package com.example.data.safetospend

import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

object WhatIfSimulator {

    /**
     * Pure, deterministic read-only simulation engine for "What-If" financial questions.
     * Never mutates Room Database or SharedPreferences.
     */
    fun simulate(
        baseInput: SafeToSpendInput,
        request: WhatIfScenarioRequest
    ): WhatIfSimulationResult {
        val baseline = SafeToSpendCalculator.calculate(baseInput)

        val simulatedInput: SafeToSpendInput = when (request) {
            is WhatIfScenarioRequest.HypotheticalSpend -> {
                val spend = max(0.0, request.amountRub)
                val extraExpense = TodayClassifiedExpense(
                    transactionId = "whatif_hypothetical_spend",
                    categoryName = request.label,
                    amountRub = spend,
                    streamType = request.streamType
                )
                baseInput.copy(
                    availableBalanceRub = baseInput.availableBalanceRub - spend,
                    todayExpenses = baseInput.todayExpenses + extraExpense
                )
            }
            is WhatIfScenarioRequest.RunwayCheck -> {
                if (request.targetHorizonDays != null && request.targetHorizonDays > 0) {
                    baseInput.copy(horizonDaysOverride = request.targetHorizonDays)
                } else {
                    baseInput
                }
            }
            is WhatIfScenarioRequest.IncomeDelay -> {
                val delay = max(0, request.delayDays).toLong()
                var anyIncomeDelayed = false
                val delayedPayments = baseInput.activeRecurringPayments.map { payment ->
                    val shouldDelay = payment.isIncome &&
                        payment.status == RecurringStatus.ACTIVE &&
                        (request.targetIncomeId == null || payment.id == request.targetIncomeId)
                    if (shouldDelay) {
                        anyIncomeDelayed = true
                        val shiftedEpochDay = payment.nextDueDateEpochDay + delay
                        val shiftedAnchorDay = try {
                            java.time.LocalDate.ofEpochDay(shiftedEpochDay).dayOfMonth
                        } catch (_: Exception) {
                            payment.anchorDayOfMonth
                        }
                        payment.copy(
                            nextDueDateEpochDay = shiftedEpochDay,
                            anchorDayOfMonth = shiftedAnchorDay
                        )
                    } else {
                        payment
                    }
                }
                baseInput.copy(
                    activeRecurringPayments = delayedPayments,
                    horizonDaysOverride = if (anyIncomeDelayed) {
                        null
                    } else {
                        (baseline.horizonDays + request.delayDays.coerceAtLeast(0)).coerceIn(1, 60)
                    }
                )
            }
        }

        val simulated = SafeToSpendCalculator.calculate(simulatedInput)

        val deltaTodayDisc = simulated.canSpendTodayDiscRub - baseline.canSpendTodayDiscRub
        val deltaDailySpeed = simulated.dailyDiscretionarySpeedRub - baseline.dailyDiscretionarySpeedRub
        val causesNewCashGap = !baseline.hasCashGap && simulated.hasCashGap
        val causesNewReserveDeficit = baseline.isReserveRequirementMet && !simulated.isReserveRequirementMet

        val baselineObligationIds = baseline.upcomingObligations.map { it.sourceId }.toSet()
        val newlyExposedObligations = simulated.upcomingObligations.filter { it.sourceId !in baselineObligationIds }

        // Runway metrics at the end of the simulated horizon
        val hDays = simulated.horizonDays.coerceAtLeast(1)
        val lastPoint = simulated.trajectory.lastOrNull()
        val fLast = lastPoint?.freeLiquidityGuarRub ?: 0.0
        val wLastZeroDisc = fLast + simulated.mandatorySafetyFloorRub

        val plannedDailyDisc = when (request) {
            is WhatIfScenarioRequest.RunwayCheck -> max(0.0, request.plannedDailyDiscretionaryRub)
            else -> 0.0
        }
        val totalPlannedDiscHorizon = hDays.toDouble() * plannedDailyDisc
        val projectedBalanceAtSalaryEveRub = wLastZeroDisc - totalPlannedDiscHorizon
        val surplusAboveReserveRub = fLast - totalPlannedDiscHorizon

        val isEnoughForMandatoryAndEssentials = projectedBalanceAtSalaryEveRub >= -1e-6
        val isEnoughKeepingSafetyReserve = surplusAboveReserveRub >= -1e-6
        val runwaySufficientUntilPayday = isEnoughKeepingSafetyReserve && !simulated.hasCashGap
        val deficitUntilPaydayRub = max(0.0, -surplusAboveReserveRub)

        // Autonomous survival days at zero discretionary spend (including today/upcoming guaranteed income)
        val lNet = simulated.availableBalanceRub - simulated.lockedGoalsAndReserveRub
        val cumGuarInflows = max(
            lastPoint?.cumulativeGuaranteedInflowsRub ?: 0.0,
            simulated.todayPendingGuaranteedIncomeRub + simulated.upcomingGuaranteedIncomeRub
        )
        val cumMandOutflows = lastPoint?.cumulativeMandatoryOutflowsRub ?: 0.0
        val netPoolForEssentials = max(0.0, lNet + cumGuarInflows - cumMandOutflows)
        val autonomousSurvivalDaysAtZeroDisc = if (simulated.dailyEssentialRateRub > 0.0) {
            floor(netPoolForEssentials / simulated.dailyEssentialRateRub).toInt().coerceAtLeast(0)
        } else {
            365
        }

        val verdict = when {
            simulated.hasCashGap || !isEnoughForMandatoryAndEssentials -> WhatIfVerdict.CASH_GAP_RISK
            !simulated.isReserveRequirementMet || !isEnoughKeepingSafetyReserve -> WhatIfVerdict.RESERVE_BREACH
            request is WhatIfScenarioRequest.HypotheticalSpend &&
                request.amountRub > baseline.canSpendTodayDiscRub + 1e-6 -> WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET
            deltaDailySpeed < -1e-6 -> WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET
            else -> WhatIfVerdict.SAFE_WITHIN_TODAY_LIMIT
        }

        val summary = buildStructuredSummary(
            request = request,
            baseline = baseline,
            simulated = simulated,
            deltaTodayDisc = deltaTodayDisc,
            deltaDailySpeed = deltaDailySpeed,
            projectedBalanceAtSalaryEveRub = projectedBalanceAtSalaryEveRub,
            surplusAboveReserveRub = surplusAboveReserveRub,
            autonomousSurvivalDaysAtZeroDisc = autonomousSurvivalDaysAtZeroDisc,
            newlyExposedObligations = newlyExposedObligations,
            verdict = verdict
        )

        return WhatIfSimulationResult(
            request = request,
            baseline = baseline,
            simulated = simulated,
            deltaCanSpendTodayDiscRub = deltaTodayDisc,
            deltaDailyDiscretionarySpeedRub = deltaDailySpeed,
            causesNewCashGap = causesNewCashGap,
            causesNewReserveDeficit = causesNewReserveDeficit,
            runwaySufficientUntilPayday = runwaySufficientUntilPayday,
            isEnoughForMandatoryAndEssentials = isEnoughForMandatoryAndEssentials,
            isEnoughKeepingSafetyReserve = isEnoughKeepingSafetyReserve,
            projectedBalanceAtSalaryEveRub = projectedBalanceAtSalaryEveRub,
            surplusAboveReserveRub = surplusAboveReserveRub,
            autonomousSurvivalDaysAtZeroDisc = autonomousSurvivalDaysAtZeroDisc,
            newlyExposedObligations = newlyExposedObligations,
            deficitUntilPaydayRub = deficitUntilPaydayRub,
            verdict = verdict,
            structuredSummaryForDavid = summary
        )
    }

    private fun buildStructuredSummary(
        request: WhatIfScenarioRequest,
        baseline: SafeToSpendResult,
        simulated: SafeToSpendResult,
        deltaTodayDisc: Double,
        deltaDailySpeed: Double,
        projectedBalanceAtSalaryEveRub: Double,
        surplusAboveReserveRub: Double,
        autonomousSurvivalDaysAtZeroDisc: Int,
        newlyExposedObligations: List<ScheduledObligationItem>,
        verdict: WhatIfVerdict
    ): String = buildString {
        appendLine("=== WHAT-IF СИМУЛЯЦИЯ (БЕЗ ИЗМЕНЕНИЯ БАЗЫ ДАННЫХ) ===")
        when (request) {
            is WhatIfScenarioRequest.HypotheticalSpend -> {
                appendLine("• Сценарий: Гипотетическая трата ${request.amountRub.toLong()} ₽ (${request.label})")
                appendLine("• Свободный лимит сегодня: было ${baseline.canSpendTodayDiscRub.toLong()} ₽ -> станет ${simulated.canSpendTodayDiscRub.toLong()} ₽ (Δ ${deltaTodayDisc.toLong()} ₽)")
                appendLine("• Дневной бюджет на следующие дни: было ${baseline.dailyDiscretionarySpeedRub.toLong()} ₽/день -> станет ${simulated.dailyDiscretionarySpeedRub.toLong()} ₽/день (Δ ${deltaDailySpeed.toLong()} ₽/день)")
            }
            is WhatIfScenarioRequest.RunwayCheck -> {
                appendLine("• Сценарий: Проверка запаса хода на ${simulated.horizonDays} дн. (при тратах ${request.plannedDailyDiscretionaryRub.toLong()} ₽/день сверх базы)")
                appendLine("• Остаток к концу горизонта (накануне зарплаты): ${projectedBalanceAtSalaryEveRub.toLong()} ₽")
                appendLine("• Запас сверх защитного резерва (${simulated.mandatorySafetyFloorRub.toLong()} ₽): ${surplusAboveReserveRub.toLong()} ₽")
                appendLine("• Автономный запас хода (при 0 ₽ необязательных трат): $autonomousSurvivalDaysAtZeroDisc дн.")
            }
            is WhatIfScenarioRequest.IncomeDelay -> {
                appendLine("• Сценарий: Задержка дохода на ${request.delayDays} дн. (горизонт: ${baseline.horizonDays} дн. -> ${simulated.horizonDays} дн.)")
                appendLine("• Свободный лимит сегодня: было ${baseline.canSpendTodayDiscRub.toLong()} ₽ -> станет %.2f ₽ (Δ %.2f ₽)".format(Locale.US, simulated.canSpendTodayDiscRub, deltaTodayDisc))
                if (newlyExposedObligations.isNotEmpty()) {
                    appendLine("• Новые обязательства, попавшие в окно до зарплаты (${newlyExposedObligations.size} шт.):")
                    newlyExposedObligations.forEach { item ->
                        appendLine("  - ${item.title}: ${item.amountRub.toLong()} ₽")
                    }
                }
            }
        }
        appendLine("• Вердикт симуляции: $verdict")
    }

    /**
     * Detects natural-language "What-If" questions in Russian text chat and extracts the structured scenario request.
     */
    fun detectWhatIfQuery(rawMessage: String): WhatIfScenarioRequest? {
        val lower = rawMessage.trim().lowercase(Locale("ru", "RU"))
        if (lower.isEmpty()) return null

        // 1. Income delay: "задержится на 4 дня", "зарплата опоздает на 3 дня", "задержка зарплаты на 5 дней"
        if ((lower.contains("задерж") || lower.contains("опозда") || lower.contains("сдвинет") || lower.contains("позже")) &&
            (lower.contains("зарплат") || lower.contains("доход") || lower.contains("аванс") || lower.contains("зп"))
        ) {
            val daysMatch = Regex("""на\s+(\d{1,2})\s*(?:дн|день|дня|дней)""").find(lower)
                ?: Regex("""(\d{1,2})\s*(?:дн|день|дня|дней)""").find(lower)
            val delayDays = daysMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 45) ?: 3
            return WhatIfScenarioRequest.IncomeDelay(delayDays = delayDays)
        }

        // 2. Hypothetical spend: "что будет, если я потрачу 3000", "если куплю за 5500", "могу ли потратить 4000"
        if ((lower.contains("если") || lower.contains("что будет") || lower.contains("хватит ли если") || lower.contains("могу ли я потратить")) &&
            (lower.contains("потрач") || lower.contains("потратить") || lower.contains("купл") || lower.contains("купить") || lower.contains("возьму"))
        ) {
            val amountMatch = Regex("""(\d+(?:[\s.,]\d+)?)\s*(тыс(?:яч)?|к|k)?\s*(?:руб(?:лей|ля|ль|\.)?|₽|р\b)?""").find(lower)
            if (amountMatch != null) {
                val baseVal = amountMatch.groupValues[1].replace(" ", "").replace(",", ".").toDoubleOrNull()
                val mult = if (amountMatch.groupValues[2].isNotEmpty()) 1000.0 else 1.0
                if (baseVal != null && baseVal * mult > 0.0) {
                    return WhatIfScenarioRequest.HypotheticalSpend(amountRub = baseVal * mult)
                }
            }
        }

        // 3. Runway check: "хватит ли до зарплаты", "доживу ли до зарплаты", "дотяну ли до зп"
        if (lower.contains("хватит ли до зарплат") ||
            lower.contains("хватит ли мне до зарплат") ||
            lower.contains("хватит до зарплат") ||
            lower.contains("доживу ли до зарплат") ||
            lower.contains("дотяну ли до зарплат") ||
            lower.contains("хватит ли до зп")
        ) {
            val dailyMatch = Regex("""по\s+(\d+(?:[\s.,]\d+)?)\s*(тыс(?:яч)?|к|k)?""").find(lower)
            val dailySpend = if (dailyMatch != null) {
                val baseVal = dailyMatch.groupValues[1].replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                val mult = if (dailyMatch.groupValues[2].isNotEmpty()) 1000.0 else 1.0
                baseVal * mult
            } else {
                0.0
            }
            return WhatIfScenarioRequest.RunwayCheck(plannedDailyDiscretionaryRub = dailySpend)
        }

        return null
    }
}
