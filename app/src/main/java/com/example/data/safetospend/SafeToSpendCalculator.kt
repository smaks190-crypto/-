package com.example.data.safetospend

import kotlin.math.max
import kotlin.math.min

object SafeToSpendCalculator {

    private const val MAX_DYNAMIC_HORIZON_WITH_INCOME_DAYS = 45
    private const val MAX_FALLBACK_HORIZON_DAYS = 30
    private const val SAFETY_FLOOR_OBLIGATION_WINDOW_DAYS = 30
    const val SAFETY_FLOOR_OBLIGATION_RATIO = 0.05

    fun calculate(input: SafeToSpendInput): SafeToSpendResult {
        val d0 = input.todayEpochDay
        val daysRemMonth = input.daysRemainingInMonth.coerceAtLeast(1)

        // 1. Classify today's already-spent transactions into 3 disjoint streams
        val spentTodayMandatory = input.todayExpenses
            .filter { it.streamType == ExpenseStreamType.MANDATORY_OBLIGATION }
            .sumOf { max(0.0, it.amountRub) }
        val spentTodayEssential = input.todayExpenses
            .filter { it.streamType == ExpenseStreamType.ESSENTIAL_LIVING }
            .sumOf { max(0.0, it.amountRub) }
        val spentTodayDiscretionary = input.todayExpenses
            .filter { it.streamType == ExpenseStreamType.DISCRETIONARY }
            .sumOf { max(0.0, it.amountRub) }
        val spentTodayTotal = spentTodayMandatory + spentTodayEssential + spentTodayDiscretionary

        // 2. Determine dynamic horizon H
        val activePayments = input.activeRecurringPayments.filter { it.status == RecurringStatus.ACTIVE }
        val earliestGuaranteedIncomeDay = findEarliestGuaranteedIncomeEpochDay(
            activePayments = activePayments,
            executionLogs = input.executionLogs,
            afterEpochDay = d0,
            maxSearchDays = 60
        )

        val horizonDays = when {
            input.horizonDaysOverride != null -> input.horizonDaysOverride.coerceIn(1, 60)
            earliestGuaranteedIncomeDay != null -> {
                val delta = (earliestGuaranteedIncomeDay - d0).toInt()
                delta.coerceIn(1, MAX_DYNAMIC_HORIZON_WITH_INCOME_DAYS)
            }
            else -> min(daysRemMonth, MAX_FALLBACK_HORIZON_DAYS).coerceAtLeast(1)
        }

        // 3. Build unified obligation schedule (Overdue O_overdue and Horizon O_due(t))
        //    Guaranteed no double-counting between L_0, O_overdue, O_due(0), and E_mand(0).
        val maxWindowDays = max(horizonDays, SAFETY_FLOOR_OBLIGATION_WINDOW_DAYS + 1)
        val schedule = buildObligationAndIncomeSchedule(
            d0 = d0,
            maxOffsetDays = maxWindowDays - 1,
            activePayments = activePayments,
            executionLogs = input.executionLogs,
            scheduledDebts = input.scheduledDebts
        )

        val overdueObligationsRub = schedule.overdueItems.sumOf { it.amountRub }
        val todayPendingObligationsRub = schedule.dueItemsByOffset[0]?.sumOf { it.amountRub } ?: 0.0
        val reservedFutureObligationsRub = (1 until horizonDays).sumOf { t ->
            schedule.dueItemsByOffset[t]?.sumOf { it.amountRub } ?: 0.0
        }

        // 4. Safety floor R_floor (never shrunk to 10% when balance is low!)
        val mandatorySafetyFloorRub = max(0.0, input.userSafetyFloorRub)

        // 5. Compute locked reserve R_locked (never double-counting GoalEntity savings transactions)
        val lockedGoalsAndReserveRub = max(0.0, input.userLockedReserveRub) +
            max(0.0, input.unTransactedLockedGoalsRub)

        // Net liquid balance before safety floor and before future/overdue obligations
        val lNet = input.availableBalanceRub - lockedGoalsAndReserveRub
        // Starting liquidity above R_floor (Note: O_overdue is NOT subtracted here; it is inside Phi(tau))
        val l0 = lNet - mandatorySafetyFloorRub

        // 6. Essential living stream E_ess^rem(tau)
        val eEss = max(0.0, input.essentialDailyRateRub)
        val rEssToday = max(0.0, eEss - spentTodayEssential)
        val reservedEssentialHorizonRub = rEssToday + (horizonDays - 1).coerceAtLeast(0) * eEss

        // 7. Step through tau = 0 .. H - 1 and compute trajectory, F_guar(tau), S(tau), and S_exp(tau)
        val trajectory = ArrayList<DayLiquidityPoint>(horizonDays)
        var cumGuarInflows = 0.0
        var cumExpInflows = 0.0
        var cumMandatoryOutflows = overdueObligationsRub

        var minFreeLiquidity = Double.POSITIVE_INFINITY
        var maxCashGapAmount = 0.0
        var firstCashGapEpochDay: Long? = null

        var minImpliedDailySpeed = Double.POSITIVE_INFINITY
        var minMorningBaselineSpeed = Double.POSITIVE_INFINITY
        var minExpectedScenarioSpeed = Double.POSITIVE_INFINITY
        var bottleneckOffset = 0

        for (tau in 0 until horizonDays) {
            val currentEpochDay = d0 + tau.toLong()
            if (tau >= 1) {
                cumGuarInflows += schedule.guaranteedInflowByOffset[tau] ?: 0.0
                cumExpInflows += schedule.expectedInflowByOffset[tau] ?: 0.0
            }
            val dueTodayOrAtTau = schedule.dueItemsByOffset[tau]?.sumOf { it.amountRub } ?: 0.0
            cumMandatoryOutflows += dueTodayOrAtTau

            val cumEssentialReserve = rEssToday + tau.toDouble() * eEss
            val fGuarTau = l0 + cumGuarInflows - cumMandatoryOutflows - cumEssentialReserve
            val fExpTau = fGuarTau + cumExpInflows

            // Check hard cash gap (deficit even when R_floor = 0)
            val rawCashAboveZero = fGuarTau + mandatorySafetyFloorRub
            if (rawCashAboveZero < -1e-6) {
                val gapHere = -rawCashAboveZero
                if (firstCashGapEpochDay == null) {
                    firstCashGapEpochDay = currentEpochDay
                }
                if (gapHere > maxCashGapAmount) {
                    maxCashGapAmount = gapHere
                }
            }

            if (fGuarTau < minFreeLiquidity) {
                minFreeLiquidity = fGuarTau
            }

            // Morning baseline speed candidate S_A(tau) before today's discretionary spend
            val candidateMorningA = max(0.0, (fGuarTau + spentTodayDiscretionary) / (tau + 1).toDouble())
            if (candidateMorningA < minMorningBaselineSpeed) {
                minMorningBaselineSpeed = candidateMorningA
            }

            // Piecewise exact formula for S(tau) avoiding double subtraction of E_disc(0)
            val sTau: Double = when {
                fGuarTau < 0.0 -> 0.0
                tau == 0 -> fGuarTau + spentTodayDiscretionary
                else -> {
                    val candidateA = (fGuarTau + spentTodayDiscretionary) / (tau + 1).toDouble()
                    if (candidateA >= spentTodayDiscretionary - 1e-9) {
                        candidateA
                    } else {
                        fGuarTau / tau.toDouble()
                    }
                }
            }

            // Expected scenario speed S_exp(tau) including EXPECTED unconfirmed income
            val sExpTau: Double = when {
                fExpTau < 0.0 -> 0.0
                tau == 0 -> fExpTau + spentTodayDiscretionary
                else -> {
                    val candidateExpA = (fExpTau + spentTodayDiscretionary) / (tau + 1).toDouble()
                    if (candidateExpA >= spentTodayDiscretionary - 1e-9) {
                        candidateExpA
                    } else {
                        fExpTau / tau.toDouble()
                    }
                }
            }
            if (sExpTau < minExpectedScenarioSpeed) {
                minExpectedScenarioSpeed = sExpTau
            }

            if (sTau < minImpliedDailySpeed - 1e-9) {
                minImpliedDailySpeed = sTau
                bottleneckOffset = tau
            } else if (sTau <= 0.0 && minImpliedDailySpeed <= 0.0) {
                val prevBottleneckF = trajectory.getOrNull(bottleneckOffset)?.freeLiquidityGuarRub ?: 0.0
                if (fGuarTau < prevBottleneckF - 1e-6 && prevBottleneckF >= 0.0) {
                    bottleneckOffset = tau
                }
            }

            trajectory.add(
                DayLiquidityPoint(
                    offsetDays = tau,
                    epochDay = currentEpochDay,
                    cumulativeGuaranteedInflowsRub = cumGuarInflows,
                    cumulativeExpectedInflowsRub = cumExpInflows,
                    cumulativeMandatoryOutflowsRub = cumMandatoryOutflows,
                    cumulativeEssentialReserveRub = cumEssentialReserve,
                    freeLiquidityGuarRub = fGuarTau,
                    impliedDailyDiscretionarySpeedRub = sTau
                )
            )
        }

        val sLiqStar = if (minImpliedDailySpeed.isFinite()) max(0.0, minImpliedDailySpeed) else 0.0
        val sMorningStar = if (minMorningBaselineSpeed.isFinite()) max(0.0, minMorningBaselineSpeed) else 0.0
        val expectedScenarioDailyBudgetRub = if (minExpectedScenarioSpeed.isFinite()) {
            max(sLiqStar, minExpectedScenarioSpeed)
        } else {
            sLiqStar
        }

        val rDiscLiqToday = if (spentTodayDiscretionary > sMorningStar + 1e-6) {
            0.0
        } else {
            max(0.0, sLiqStar - spentTodayDiscretionary)
        }
        val todayOverspendRub = max(0.0, spentTodayDiscretionary - sMorningStar)

        // 8. Reconcile with discretionary monthly category limits (if present)
        val discCatRem = input.discretionaryCategoryRemainingRub
        val (finalDailyDiscSpeed, finalCanSpendDiscToday, isCategoryBottleneck) = if (discCatRem != null) {
            val sCatStar = max(0.0, discCatRem) / daysRemMonth.toDouble()
            val rDiscCatToday = max(0.0, sCatStar - spentTodayDiscretionary)
            val catIsTighter = rDiscCatToday < rDiscLiqToday - 1e-6 || sCatStar < sLiqStar - 1e-6
            Triple(
                min(sLiqStar, sCatStar),
                min(rDiscLiqToday, rDiscCatToday),
                catIsTighter
            )
        } else {
            Triple(sLiqStar, rDiscLiqToday, false)
        }

        // 9. Reserve requirement & Cash gap flags
        val isReserveRequirementMet = minFreeLiquidity >= -1e-6
        val reserveDeficitRub = if (minFreeLiquidity < 0.0) -minFreeLiquidity else 0.0
        val hasCashGap = maxCashGapAmount > 1e-6

        // 10. Determine bottleneck reason
        val bottleneckReason = when {
            hasCashGap -> BottleneckReason.CASH_GAP_DETECTED
            !isReserveRequirementMet && overdueObligationsRub > 0.0 &&
                (l0 - overdueObligationsRub - rEssToday) < 0.0 -> BottleneckReason.OVERDUE_OBLIGATION_PRESSURE
            !isReserveRequirementMet -> BottleneckReason.INSUFFICIENT_RESERVE_FLOOR
            isCategoryBottleneck -> BottleneckReason.CATEGORY_MONTHLY_BUDGET
            else -> BottleneckReason.LIQUIDITY_HORIZON
        }

        // 11. Today's protected essential allowance (clamped only if literal cash after today's mandatory bills is < rEssToday)
        val cashAfterTodayMandatory = lNet - overdueObligationsRub - todayPendingObligationsRub
        val canSpendTodayEssRub = if (cashAfterTodayMandatory <= 0.0) {
            0.0
        } else {
            min(rEssToday, cashAfterTodayMandatory)
        }
        val canSpendTodayTotalRub = finalCanSpendDiscToday + canSpendTodayEssRub

        val allUpcomingObligations = buildList {
            addAll(schedule.overdueItems)
            for (t in 0 until horizonDays) {
                schedule.dueItemsByOffset[t]?.let { addAll(it) }
            }
        }

        return SafeToSpendResult(
            canSpendTodayDiscRub = finalCanSpendDiscToday,
            canSpendTodayEssRub = canSpendTodayEssRub,
            canSpendTodayTotalRub = canSpendTodayTotalRub,
            dailyDiscretionarySpeedRub = finalDailyDiscSpeed,
            expectedScenarioDailyBudgetRub = expectedScenarioDailyBudgetRub,
            todayOverspendRub = todayOverspendRub,
            dailyEssentialRateRub = eEss,
            essentialEstimationMethod = input.essentialEstimationMethod,
            essentialConfidenceLevel = input.essentialConfidenceLevel,
            requiresEssentialBudgetPrompt = input.requiresEssentialBudgetPrompt,
            essentialWarningMessage = input.essentialWarningMessage,
            spentTodayMandatoryRub = spentTodayMandatory,
            spentTodayEssentialRub = spentTodayEssential,
            spentTodayDiscretionaryRub = spentTodayDiscretionary,
            spentTodayTotalRub = spentTodayTotal,
            availableBalanceRub = input.availableBalanceRub,
            lockedGoalsAndReserveRub = lockedGoalsAndReserveRub,
            mandatorySafetyFloorRub = mandatorySafetyFloorRub,
            isReserveRequirementMet = isReserveRequirementMet,
            reserveDeficitRub = reserveDeficitRub,
            overdueObligationsRub = overdueObligationsRub,
            todayPendingObligationsRub = todayPendingObligationsRub,
            reservedFutureObligationsRub = reservedFutureObligationsRub,
            reservedEssentialLivingHorizonRub = reservedEssentialHorizonRub,
            expectedUnconfirmedIncomeHorizonRub = cumExpInflows,
            hypotheticalDebtReceivablesRub = schedule.hypotheticalReceivablesRub,
            horizonDays = horizonDays,
            nextIncomeEpochDay = earliestGuaranteedIncomeDay,
            bottleneckDayOffset = bottleneckOffset,
            bottleneckEpochDay = d0 + bottleneckOffset.toLong(),
            bottleneckReason = bottleneckReason,
            hasCashGap = hasCashGap,
            cashGapEpochDay = firstCashGapEpochDay,
            cashGapAmountRub = maxCashGapAmount,
            trajectory = trajectory,
            upcomingObligations = allUpcomingObligations
        )
    }

    private data class ScheduleProjection(
        val overdueItems: List<ScheduledObligationItem>,
        val dueItemsByOffset: Map<Int, List<ScheduledObligationItem>>,
        val guaranteedInflowByOffset: Map<Int, Double>,
        val expectedInflowByOffset: Map<Int, Double>,
        val hypotheticalReceivablesRub: Double
    )

    private fun findEarliestGuaranteedIncomeEpochDay(
        activePayments: List<RecurringPaymentDomain>,
        executionLogs: List<RecurringExecutionLogDomain>,
        afterEpochDay: Long,
        maxSearchDays: Int
    ): Long? {
        val searchEnd = afterEpochDay + maxSearchDays.toLong()
        var earliest: Long? = null

        val incomeRules = activePayments.filter {
            it.isIncome && it.status == RecurringStatus.ACTIVE && it.incomeCertainty == IncomeCertainty.GUARANTEED
        }

        for (rule in incomeRules) {
            val occurrences = RecurringScheduleCalculator.generateOccurrencesInRange(
                payment = rule,
                rangeStartEpochDay = afterEpochDay + 1L,
                rangeEndEpochDay = searchEnd
            )
            for (occ in occurrences) {
                val isSkippedOrPaid = executionLogs.any { log ->
                    log.recurringId == rule.id &&
                        log.scheduledEpochDay == occ &&
                        (log.status == ExecutionStatus.PAID || log.status == ExecutionStatus.SKIPPED)
                }
                if (!isSkippedOrPaid) {
                    if (earliest == null || occ < earliest) {
                        earliest = occ
                    }
                    break
                }
            }
        }
        return earliest
    }

    private fun buildObligationAndIncomeSchedule(
        d0: Long,
        maxOffsetDays: Int,
        activePayments: List<RecurringPaymentDomain>,
        executionLogs: List<RecurringExecutionLogDomain>,
        scheduledDebts: List<ScheduledDebtObligation>
    ): ScheduleProjection {
        val rangeEnd = d0 + maxOffsetDays.toLong()
        val overdueItems = mutableListOf<ScheduledObligationItem>()
        val dueItemsByOffset = mutableMapOf<Int, MutableList<ScheduledObligationItem>>()
        val guaranteedInflowByOffset = mutableMapOf<Int, Double>()
        val expectedInflowByOffset = mutableMapOf<Int, Double>()
        var hypotheticalReceivablesRub = 0.0

        val logsByRecurringAndDate = executionLogs.associateBy { it.recurringId to it.scheduledEpochDay }
        val handledOverdueKeys = mutableSetOf<Pair<Long, Long>>()

        for (rule in activePayments) {
            if (rule.status != RecurringStatus.ACTIVE) continue
            val effectiveAmount = rule.effectiveReservedAmountRub
            if (effectiveAmount <= 0.0) continue

            if (!rule.isIncome) {
                // 1. Check any explicit past logs that are PENDING or OVERDUE
                executionLogs
                    .filter {
                        it.recurringId == rule.id &&
                            it.scheduledEpochDay < d0 &&
                            (it.status == ExecutionStatus.PENDING || it.status == ExecutionStatus.OVERDUE)
                    }
                    .forEach { log ->
                        if (handledOverdueKeys.add(rule.id to log.scheduledEpochDay)) {
                            overdueItems.add(
                                ScheduledObligationItem(
                                    sourceId = "rec_${rule.id}_${log.scheduledEpochDay}",
                                    title = rule.title,
                                    amountRub = log.actualAmount ?: effectiveAmount,
                                    dueEpochDay = log.scheduledEpochDay,
                                    isOverdue = true,
                                    isPriorityMandatory = rule.priority == ObligationPriority.MANDATORY,
                                    categoryName = rule.categoryName
                                )
                            )
                        }
                    }

                // 2. Step through occurrences starting from rule.nextDueDateEpochDay up to rangeEnd
                val occurrences = RecurringScheduleCalculator.generateOccurrencesInRange(
                    payment = rule,
                    rangeStartEpochDay = min(rule.nextDueDateEpochDay, d0),
                    rangeEndEpochDay = rangeEnd
                )

                for (occDay in occurrences) {
                    val log = logsByRecurringAndDate[rule.id to occDay]
                    // If already PAID or SKIPPED, do not reserve again (money already left B_avail or skipped)
                    if (log != null && (log.status == ExecutionStatus.PAID || log.status == ExecutionStatus.SKIPPED || log.status == ExecutionStatus.RESCHEDULED)) {
                        continue
                    }

                    if (occDay < d0) {
                        if (handledOverdueKeys.add(rule.id to occDay)) {
                            overdueItems.add(
                                ScheduledObligationItem(
                                    sourceId = "rec_${rule.id}_$occDay",
                                    title = rule.title,
                                    amountRub = log?.actualAmount ?: effectiveAmount,
                                    dueEpochDay = occDay,
                                    isOverdue = true,
                                    isPriorityMandatory = rule.priority == ObligationPriority.MANDATORY,
                                    categoryName = rule.categoryName
                                )
                            )
                        }
                    } else {
                        val offset = (occDay - d0).toInt()
                        if (offset in 0..maxOffsetDays) {
                            dueItemsByOffset.getOrPut(offset) { mutableListOf() }.add(
                                ScheduledObligationItem(
                                    sourceId = "rec_${rule.id}_$occDay",
                                    title = rule.title,
                                    amountRub = log?.actualAmount ?: effectiveAmount,
                                    dueEpochDay = occDay,
                                    isOverdue = false,
                                    isPriorityMandatory = rule.priority == ObligationPriority.MANDATORY,
                                    categoryName = rule.categoryName
                                )
                            )
                        }
                    }
                }
            } else {
                // Recurring Income (only future offsets t >= 1; today's income is only added when actually received into B_avail)
                val occurrences = RecurringScheduleCalculator.generateOccurrencesInRange(
                    payment = rule,
                    rangeStartEpochDay = d0 + 1L,
                    rangeEndEpochDay = rangeEnd
                )
                for (occDay in occurrences) {
                    val log = logsByRecurringAndDate[rule.id to occDay]
                    if (log != null && (log.status == ExecutionStatus.PAID || log.status == ExecutionStatus.SKIPPED)) {
                        continue
                    }
                    val offset = (occDay - d0).toInt()
                    if (offset in 1..maxOffsetDays) {
                        when (rule.incomeCertainty) {
                            IncomeCertainty.GUARANTEED -> {
                                guaranteedInflowByOffset[offset] =
                                    (guaranteedInflowByOffset[offset] ?: 0.0) + effectiveAmount
                            }
                            IncomeCertainty.EXPECTED -> {
                                expectedInflowByOffset[offset] =
                                    (expectedInflowByOffset[offset] ?: 0.0) + effectiveAmount
                            }
                            IncomeCertainty.HYPOTHETICAL -> {
                                hypotheticalReceivablesRub += effectiveAmount
                            }
                        }
                    }
                }
            }
        }

        // 3. Process scheduled debt obligations (we_owe vs owes_us)
        for (debt in scheduledDebts) {
            val rem = debt.remainingAmountRub
            if (rem <= 0.0) continue

            if (debt.isWeOwe) {
                val dueDay = debt.dueDateEpochDay ?: continue
                if (dueDay < d0) {
                    overdueItems.add(
                        ScheduledObligationItem(
                            sourceId = "debt_${debt.debtId}",
                            title = "Долг: ${debt.name}",
                            amountRub = rem,
                            dueEpochDay = dueDay,
                            isOverdue = true,
                            isPriorityMandatory = true,
                            categoryName = "Долги"
                        )
                    )
                } else {
                    val offset = (dueDay - d0).toInt()
                    if (offset in 0..maxOffsetDays) {
                        dueItemsByOffset.getOrPut(offset) { mutableListOf() }.add(
                            ScheduledObligationItem(
                                sourceId = "debt_${debt.debtId}",
                                title = "Долг: ${debt.name}",
                                amountRub = rem,
                                dueEpochDay = dueDay,
                                isOverdue = false,
                                isPriorityMandatory = true,
                                categoryName = "Долги"
                            )
                        )
                    }
                }
            } else {
                // owes_us: never included in guaranteed liquidity
                val dueDay = debt.dueDateEpochDay
                if (dueDay != null && dueDay > d0 && (dueDay - d0).toInt() in 1..maxOffsetDays && debt.incomeCertainty == IncomeCertainty.EXPECTED) {
                    val offset = (dueDay - d0).toInt()
                    expectedInflowByOffset[offset] = (expectedInflowByOffset[offset] ?: 0.0) + rem
                } else {
                    hypotheticalReceivablesRub += rem
                }
            }
        }

        overdueItems.sortBy { it.dueEpochDay }

        return ScheduleProjection(
            overdueItems = overdueItems,
            dueItemsByOffset = dueItemsByOffset,
            guaranteedInflowByOffset = guaranteedInflowByOffset,
            expectedInflowByOffset = expectedInflowByOffset,
            hypotheticalReceivablesRub = hypotheticalReceivablesRub
        )
    }
}
