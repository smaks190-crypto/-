package com.example.data.safetospend

import com.example.data.db.RecurringExecutionLogEntity
import com.example.data.db.RecurringPaymentEntity
import java.time.LocalDate
import kotlin.math.max

enum class PaymentFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    CUSTOM_DAYS
}

enum class PaymentAmountType {
    FIXED,
    ESTIMATED_UPPER_BOUND
}

enum class ObligationPriority {
    MANDATORY,
    DISCRETIONARY_SUBSCRIPTION
}

enum class RecurringStatus {
    ACTIVE,
    PAUSED,
    ARCHIVED
}

enum class ExecutionStatus {
    PENDING,
    PAID,
    SKIPPED,
    OVERDUE,
    RESCHEDULED
}

enum class IncomeCertainty {
    GUARANTEED,
    EXPECTED,
    HYPOTHETICAL
}

enum class ExpenseStreamType {
    MANDATORY_OBLIGATION,
    ESSENTIAL_LIVING,
    DISCRETIONARY
}

enum class EssentialEstimationMethod {
    WEEKLY_TRIMMED_WINSORIZED,
    EMPIRICAL_BAYES_SHRINKAGE,
    USER_DEFINED_NORM,
    CATEGORY_LIMIT_FALLBACK,
    CONSERVATIVE_SYSTEM_FALLBACK
}

enum class EssentialConfidenceLevel {
    HIGH_HISTORICAL,
    MODERATE_BLENDED,
    USER_OR_LIMIT_BASED,
    UNVERIFIED_FALLBACK
}

enum class BottleneckReason {
    LIQUIDITY_HORIZON,
    CATEGORY_MONTHLY_BUDGET,
    INSUFFICIENT_RESERVE_FLOOR,
    CASH_GAP_DETECTED,
    OVERDUE_OBLIGATION_PRESSURE
}

enum class WhatIfVerdict {
    SAFE_WITHIN_TODAY_LIMIT,
    REDUCES_FUTURE_DAILY_BUDGET,
    RESERVE_BREACH,
    CASH_GAP_RISK
}

data class RecurringPaymentDomain(
    val id: Long = 0L,
    val title: String,
    val amount: Double,
    val estimatedMaxAmount: Double? = null,
    val amountType: PaymentAmountType = PaymentAmountType.FIXED,
    val isIncome: Boolean = false,
    val incomeCertainty: IncomeCertainty = IncomeCertainty.GUARANTEED,
    val categoryName: String,
    val subcategoryName: String = "",
    val frequency: PaymentFrequency = PaymentFrequency.MONTHLY,
    val customIntervalDays: Int? = null,
    val anchorDayOfMonth: Int? = null,
    val anchorDayOfWeek: Int? = null,
    val nextDueDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val priority: ObligationPriority = ObligationPriority.MANDATORY,
    val autoConfirm: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val status: RecurringStatus = RecurringStatus.ACTIVE
) {
    val effectiveReservedAmountRub: Double
        get() = if (amountType == PaymentAmountType.ESTIMATED_UPPER_BOUND && estimatedMaxAmount != null) {
            max(amount, estimatedMaxAmount)
        } else {
            amount
        }
}

data class RecurringExecutionLogDomain(
    val id: Long = 0L,
    val recurringId: Long,
    val scheduledEpochDay: Long,
    val executedEpochDay: Long? = null,
    val actualAmount: Double? = null,
    val status: ExecutionStatus = ExecutionStatus.PENDING,
    val linkedTransactionId: String? = null
)

data class ScheduledDebtObligation(
    val debtId: String,
    val name: String,
    val remainingAmountRub: Double,
    val isWeOwe: Boolean,
    val dueDateEpochDay: Long? = null,
    val incomeCertainty: IncomeCertainty = IncomeCertainty.HYPOTHETICAL
)

data class HistoricalExpenseSample(
    val transactionId: String,
    val epochDay: Long,
    val categoryName: String,
    val subcategoryName: String = "",
    val amountRub: Double,
    val linkedRecurringId: Long? = null
)

data class TodayClassifiedExpense(
    val transactionId: String,
    val categoryName: String,
    val amountRub: Double,
    val streamType: ExpenseStreamType,
    val linkedRecurringId: Long? = null
)

data class ScheduledObligationItem(
    val sourceId: String,
    val title: String,
    val amountRub: Double,
    val dueEpochDay: Long,
    val isOverdue: Boolean,
    val isPriorityMandatory: Boolean,
    val categoryName: String
)

data class DayLiquidityPoint(
    val offsetDays: Int,
    val epochDay: Long,
    val cumulativeGuaranteedInflowsRub: Double,
    val cumulativeExpectedInflowsRub: Double,
    val cumulativeMandatoryOutflowsRub: Double,
    val cumulativeEssentialReserveRub: Double,
    val freeLiquidityGuarRub: Double,
    val impliedDailyDiscretionarySpeedRub: Double
)

data class EssentialForecastResult(
    val dailyEssentialRateRub: Double,
    val weeklyBucketEstimateRub: Double,
    val winsorizedAmortizedEstimateRub: Double,
    val priorDailyRateRub: Double,
    val observedHistoryDays: Int,
    val method: EssentialEstimationMethod,
    val confidenceLevel: EssentialConfidenceLevel,
    val requiresEssentialBudgetPrompt: Boolean,
    val warningMessage: String? = null
)

data class SafeToSpendInput(
    val todayEpochDay: Long,
    val daysRemainingInMonth: Int = computeDaysRemainingInMonth(todayEpochDay),
    val availableBalanceRub: Double,
    val userSafetyFloorRub: Double = 0.0,
    val userLockedReserveRub: Double = 0.0,
    val unTransactedLockedGoalsRub: Double = 0.0,
    val activeRecurringPayments: List<RecurringPaymentDomain> = emptyList(),
    val executionLogs: List<RecurringExecutionLogDomain> = emptyList(),
    val scheduledDebts: List<ScheduledDebtObligation> = emptyList(),
    val todayExpenses: List<TodayClassifiedExpense> = emptyList(),
    val essentialDailyRateRub: Double = 0.0,
    val essentialEstimationMethod: EssentialEstimationMethod = EssentialEstimationMethod.CONSERVATIVE_SYSTEM_FALLBACK,
    val essentialConfidenceLevel: EssentialConfidenceLevel = EssentialConfidenceLevel.UNVERIFIED_FALLBACK,
    val requiresEssentialBudgetPrompt: Boolean = false,
    val essentialWarningMessage: String? = null,
    val discretionaryCategoryRemainingRub: Double? = null,
    val horizonDaysOverride: Int? = null
) {
    companion object {
        fun computeDaysRemainingInMonth(todayEpochDay: Long): Int {
            val date = LocalDate.ofEpochDay(todayEpochDay)
            return (date.lengthOfMonth() - date.dayOfMonth + 1).coerceAtLeast(1)
        }
    }
}

data class SafeToSpendResult(
    val canSpendTodayDiscRub: Double,
    val canSpendTodayEssRub: Double,
    val canSpendTodayTotalRub: Double,
    val dailyDiscretionarySpeedRub: Double,
    val expectedScenarioDailyBudgetRub: Double,
    val todayOverspendRub: Double,
    val dailyEssentialRateRub: Double,
    val essentialEstimationMethod: EssentialEstimationMethod,
    val essentialConfidenceLevel: EssentialConfidenceLevel,
    val requiresEssentialBudgetPrompt: Boolean,
    val essentialWarningMessage: String?,
    val spentTodayMandatoryRub: Double,
    val spentTodayEssentialRub: Double,
    val spentTodayDiscretionaryRub: Double,
    val spentTodayTotalRub: Double,
    val availableBalanceRub: Double,
    val lockedGoalsAndReserveRub: Double,
    val mandatorySafetyFloorRub: Double,
    val isReserveRequirementMet: Boolean,
    val reserveDeficitRub: Double,
    val overdueObligationsRub: Double,
    val todayPendingObligationsRub: Double,
    val reservedFutureObligationsRub: Double,
    val reservedEssentialLivingHorizonRub: Double,
    val expectedUnconfirmedIncomeHorizonRub: Double,
    val todayPendingGuaranteedIncomeRub: Double = 0.0,
    val upcomingGuaranteedIncomeRub: Double = 0.0,
    val hypotheticalDebtReceivablesRub: Double,
    val horizonDays: Int,
    val nextIncomeEpochDay: Long?,
    val bottleneckDayOffset: Int,
    val bottleneckEpochDay: Long,
    val bottleneckReason: BottleneckReason,
    val hasCashGap: Boolean,
    val cashGapEpochDay: Long?,
    val cashGapAmountRub: Double,
    val trajectory: List<DayLiquidityPoint>,
    val upcomingObligations: List<ScheduledObligationItem>
) {
    val obligationsReservedTotalRub: Double
        get() = overdueObligationsRub + todayPendingObligationsRub + reservedFutureObligationsRub

    fun toStructuredDavidSummary(): String = buildString {
        appendLine("=== SAFE-TO-SPEND 2.0 (ДЕТЕРМИНИРОВАННЫЙ ПРОГНОЗ ЛИКВИДНОСТИ) ===")
        appendLine("• Свободный лимит на сегодня (необязательные траты): ${canSpendTodayDiscRub.toLong()} ₽")
        appendLine("• Защищённый резерв на базовые нужды сегодня (еда, транспорт, аптека): ${canSpendTodayEssRub.toLong()} ₽")
        appendLine("• Суммарно доступно сегодня без нарушения обязательств: ${canSpendTodayTotalRub.toLong()} ₽")
        appendLine("• Расчётная дневная скорость на будущие дни горизонта: ${dailyDiscretionarySpeedRub.toLong()} ₽/день")
        appendLine("• Доступный баланс (B_avail): ${availableBalanceRub.toLong()} ₽")
        appendLine("• Потрачено сегодня: всего ${spentTodayTotalRub.toLong()} ₽ (обязательные: ${spentTodayMandatoryRub.toLong()} ₽, базовые: ${spentTodayEssentialRub.toLong()} ₽, свободные: ${spentTodayDiscretionaryRub.toLong()} ₽)")
        if (todayOverspendRub > 0.0) {
            appendLine("• Перерасход дневного лимита сегодня: ${todayOverspendRub.toLong()} ₽")
        }
        appendLine("• Защитный резерв (R_floor): ${mandatorySafetyFloorRub.toLong()} ₽ (выполнен: ${if (isReserveRequirementMet) "ДА" else "НЕТ, дефицит ${reserveDeficitRub.toLong()} ₽"})")
        appendLine("• Заблокированные средства (R_locked): ${lockedGoalsAndReserveRub.toLong()} ₽")
        appendLine("• Просроченные обязательства (O_overdue): ${overdueObligationsRub.toLong()} ₽")
        appendLine("• Обязательства к оплате сегодня (O_due(0)): ${todayPendingObligationsRub.toLong()} ₽")
        appendLine("• Будущие обязательства в горизонте $horizonDays дн.: ${reservedFutureObligationsRub.toLong()} ₽")
        appendLine("• Резерв на базовую жизнь ($horizonDays дн. по ${dailyEssentialRateRub.toLong()} ₽/дн.): ${reservedEssentialLivingHorizonRub.toLong()} ₽ (метод: $essentialEstimationMethod, достоверность: $essentialConfidenceLevel)")
        if (requiresEssentialBudgetPrompt && !essentialWarningMessage.isNullOrBlank()) {
            appendLine("• ВНИМАНИЕ ПО БАЗОВЫМ РАСХОДАМ: $essentialWarningMessage")
        }
        appendLine("• Узкое место горизонта: через $bottleneckDayOffset дн. (${LocalDate.ofEpochDay(bottleneckEpochDay)}), причина: $bottleneckReason")
        if (hasCashGap) {
            val gapDate = cashGapEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "сегодня"
            appendLine("• РИСК КАССОВОГО РАЗРЫВА: дефицит ${cashGapAmountRub.toLong()} ₽ на дату $gapDate")
        } else {
            appendLine("• Кассовый разрыв: отсутствует (0 ₽)")
        }
        if (todayPendingGuaranteedIncomeRub + upcomingGuaranteedIncomeRub > 0.0) {
            appendLine("• Плановые гарантированные доходы на горизонте: ${(todayPendingGuaranteedIncomeRub + upcomingGuaranteedIncomeRub).toLong()} ₽")
        }
        if (expectedUnconfirmedIncomeHorizonRub > 0.0) {
            appendLine("• Справочно (при приходе ожидаемого неподтверждённого дохода ${expectedUnconfirmedIncomeHorizonRub.toLong()} ₽): дневной бюджет вырастет до ${expectedScenarioDailyBudgetRub.toLong()} ₽/день")
        }
        if (upcomingObligations.isNotEmpty()) {
            appendLine("• Ближайшие обязательства:")
            upcomingObligations.take(5).forEach { item ->
                val statusLabel = if (item.isOverdue) "ПРОСРОЧЕНО" else LocalDate.ofEpochDay(item.dueEpochDay).toString()
                appendLine("  - ${item.title}: ${item.amountRub.toLong()} ₽ ($statusLabel)")
            }
        }
    }
}

sealed class WhatIfScenarioRequest {
    data class HypotheticalSpend(
        val amountRub: Double,
        val streamType: ExpenseStreamType = ExpenseStreamType.DISCRETIONARY,
        val label: String = "Гипотетическая покупка"
    ) : WhatIfScenarioRequest()

    data class RunwayCheck(
        val plannedDailyDiscretionaryRub: Double = 0.0,
        val targetHorizonDays: Int? = null
    ) : WhatIfScenarioRequest()

    data class IncomeDelay(
        val delayDays: Int,
        val targetIncomeId: Long? = null
    ) : WhatIfScenarioRequest()
}

data class WhatIfSimulationResult(
    val request: WhatIfScenarioRequest,
    val baseline: SafeToSpendResult,
    val simulated: SafeToSpendResult,
    val deltaCanSpendTodayDiscRub: Double,
    val deltaDailyDiscretionarySpeedRub: Double,
    val causesNewCashGap: Boolean,
    val causesNewReserveDeficit: Boolean,
    val runwaySufficientUntilPayday: Boolean,
    val isEnoughForMandatoryAndEssentials: Boolean,
    val isEnoughKeepingSafetyReserve: Boolean,
    val projectedBalanceAtSalaryEveRub: Double,
    val surplusAboveReserveRub: Double,
    val autonomousSurvivalDaysAtZeroDisc: Int,
    val newlyExposedObligations: List<ScheduledObligationItem>,
    val deficitUntilPaydayRub: Double,
    val verdict: WhatIfVerdict,
    val structuredSummaryForDavid: String
)

data class RecurringPaymentDraft(
    val title: String,
    val amountRub: Double,
    val isIncome: Boolean,
    val frequency: PaymentFrequency,
    val anchorDayOfMonth: Int?,
    val anchorDayOfWeek: Int?,
    val nextDueDateEpochDay: Long,
    val categoryName: String,
    val priority: ObligationPriority = ObligationPriority.MANDATORY,
    val requiresUserConfirmation: Boolean = true
)

fun RecurringPaymentEntity.toDomain(): RecurringPaymentDomain = RecurringPaymentDomain(
    id = id,
    title = title,
    amount = amount,
    estimatedMaxAmount = estimatedMaxAmount,
    amountType = runCatching { PaymentAmountType.valueOf(amountType) }.getOrDefault(PaymentAmountType.FIXED),
    isIncome = isIncome,
    incomeCertainty = runCatching { IncomeCertainty.valueOf(incomeCertainty) }.getOrDefault(
        if (isIncome) IncomeCertainty.GUARANTEED else IncomeCertainty.GUARANTEED
    ),
    categoryName = categoryName,
    subcategoryName = subcategoryName,
    frequency = runCatching { PaymentFrequency.valueOf(frequency) }.getOrDefault(PaymentFrequency.MONTHLY),
    customIntervalDays = customIntervalDays,
    anchorDayOfMonth = anchorDayOfMonth,
    anchorDayOfWeek = anchorDayOfWeek,
    nextDueDateEpochDay = nextDueDateEpochDay,
    endDateEpochDay = endDateEpochDay,
    priority = runCatching { ObligationPriority.valueOf(priority) }.getOrDefault(ObligationPriority.MANDATORY),
    autoConfirm = autoConfirm,
    reminderDaysBefore = reminderDaysBefore,
    status = runCatching { RecurringStatus.valueOf(status) }.getOrDefault(RecurringStatus.ACTIVE)
)

fun RecurringPaymentDomain.toEntity(createdAtMillis: Long = System.currentTimeMillis()): RecurringPaymentEntity =
    RecurringPaymentEntity(
        id = id,
        title = title,
        amount = amount,
        estimatedMaxAmount = estimatedMaxAmount,
        amountType = amountType.name,
        isIncome = isIncome,
        incomeCertainty = incomeCertainty.name,
        categoryName = categoryName,
        subcategoryName = subcategoryName,
        frequency = frequency.name,
        customIntervalDays = customIntervalDays,
        anchorDayOfMonth = anchorDayOfMonth,
        anchorDayOfWeek = anchorDayOfWeek,
        nextDueDateEpochDay = nextDueDateEpochDay,
        endDateEpochDay = endDateEpochDay,
        priority = priority.name,
        autoConfirm = autoConfirm,
        reminderDaysBefore = reminderDaysBefore,
        status = status.name,
        createdAtMillis = createdAtMillis
    )

fun RecurringExecutionLogEntity.toDomain(): RecurringExecutionLogDomain = RecurringExecutionLogDomain(
    id = id,
    recurringId = recurringId,
    scheduledEpochDay = scheduledEpochDay,
    executedEpochDay = executedEpochDay,
    actualAmount = actualAmount,
    status = runCatching { ExecutionStatus.valueOf(status) }.getOrDefault(ExecutionStatus.PENDING),
    linkedTransactionId = linkedTransactionId
)
