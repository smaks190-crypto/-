package com.example.data.safetospend

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object EssentialSpendingEstimator {

    const val DEFAULT_CONSERVATIVE_FALLBACK_RUB = 500.0
    private const val SHRINKAGE_FULL_CREDIBILITY_DAYS = 14.0

    private val ESSENTIAL_CATEGORY_KEYWORDS = listOf(
        "продукт",
        "еда",
        "супермаркет",
        "транспорт",
        "медицин",
        "аптек",
        "здоровь",
        "бытовая химия",
        "базовые нужды",
        "питани",
        "лекарств"
    )

    private val MANDATORY_CATEGORY_KEYWORDS = listOf(
        "жиль",
        "аренд",
        "жкх",
        "коммунал",
        "кредит",
        "ипотек",
        "долг",
        "налог",
        "обязательн",
        "связь",
        "интернет"
    )

    fun isEssentialCategory(categoryName: String): Boolean {
        val lower = categoryName.trim().lowercase(Locale("ru", "RU"))
        return ESSENTIAL_CATEGORY_KEYWORDS.any { lower.contains(it) }
    }

    fun isMandatoryCategory(categoryName: String): Boolean {
        val lower = categoryName.trim().lowercase(Locale("ru", "RU"))
        return MANDATORY_CATEGORY_KEYWORDS.any { lower.contains(it) }
    }

    fun classifyExpenseStream(
        categoryName: String,
        linkedRecurringId: Long? = null,
        recurringPriority: ObligationPriority? = null
    ): ExpenseStreamType {
        if (linkedRecurringId != null) {
            return if (recurringPriority == ObligationPriority.DISCRETIONARY_SUBSCRIPTION) {
                ExpenseStreamType.DISCRETIONARY
            } else {
                ExpenseStreamType.MANDATORY_OBLIGATION
            }
        }
        return when {
            isEssentialCategory(categoryName) -> ExpenseStreamType.ESSENTIAL_LIVING
            isMandatoryCategory(categoryName) -> ExpenseStreamType.MANDATORY_OBLIGATION
            else -> ExpenseStreamType.DISCRETIONARY
        }
    }

    /**
     * Step 1: Filters historical expenses in essential categories to remove any transactions
     * already covered by recurring payments (preventing double counting between O_due and E_ess).
     */
    fun filterPureEssentialHistory(
        historicalSamples: List<HistoricalExpenseSample>,
        activeRecurringPayments: List<RecurringPaymentDomain>,
        executionLogs: List<RecurringExecutionLogDomain>
    ): List<HistoricalExpenseSample> {
        val executedTxIds = executionLogs.mapNotNull { it.linkedTransactionId }.toSet()
        val activeExpenseRecurring = activeRecurringPayments.filter {
            !it.isIncome && it.status == RecurringStatus.ACTIVE
        }

        return historicalSamples.filter { sample ->
            if (!isEssentialCategory(sample.categoryName)) return@filter false
            if (sample.linkedRecurringId != null) return@filter false
            if (sample.transactionId in executedTxIds) return@filter false

            val matchesRecurringRule = activeExpenseRecurring.any { rp ->
                val sameCategory = rp.categoryName.equals(sample.categoryName, ignoreCase = true)
                val closeAmount = abs(sample.amountRub - rp.amount) / max(1.0, rp.amount) <= 0.15
                val closeToScheduledLog = executionLogs.any { log ->
                    log.recurringId == rp.id && abs(sample.epochDay - log.scheduledEpochDay) <= 3L
                }
                val closeToNextDue = abs(sample.epochDay - rp.nextDueDateEpochDay) <= 3L ||
                    abs((sample.epochDay + 30L) - rp.nextDueDateEpochDay) <= 3L
                sameCategory && closeAmount && (closeToScheduledLog || closeToNextDue)
            }
            !matchesRecurringRule
        }
    }

    /**
     * Computes the daily essential spending forecast e_ess using:
     * 1) De-duplication against recurring obligations
     * 2) 4-week bucketed Mid-50% trimmed mean (captures weekly bulk grocery shopping)
     * 3) 28/30-day winsorized amortization (captures periodic pharmacy/medicine expenses)
     * 4) Empirical Bayes shrinkage for cold-start (< 14 days of history)
     * 5) Explicit confidence level & user prompt flag when history and user norms are absent
     */
    fun estimateDailyEssentialRate(
        todayEpochDay: Long,
        historicalSamples: List<HistoricalExpenseSample>,
        activeRecurringPayments: List<RecurringPaymentDomain> = emptyList(),
        executionLogs: List<RecurringExecutionLogDomain> = emptyList(),
        userEssentialDailyNormRub: Double? = null,
        essentialCategoryMonthlyLimitsRub: Map<String, Double> = emptyMap(),
        observedHistoryDaysOverride: Int? = null
    ): EssentialForecastResult {
        val windowStart28 = todayEpochDay - 28L
        val windowStart30 = todayEpochDay - 30L
        val windowEnd = todayEpochDay - 1L

        val pureSamples30 = filterPureEssentialHistory(
            historicalSamples = historicalSamples.filter { it.epochDay in windowStart30..windowEnd && it.amountRub > 0.0 },
            activeRecurringPayments = activeRecurringPayments,
            executionLogs = executionLogs
        )
        val pureSamples28 = pureSamples30.filter { it.epochDay in windowStart28..windowEnd }

        // Determine observed history span N_obs
        val observedHistoryDays: Int = when {
            observedHistoryDaysOverride != null -> observedHistoryDaysOverride.coerceAtLeast(0)
            pureSamples30.isEmpty() -> 0
            else -> {
                val earliestEpochDay = pureSamples30.minOf { it.epochDay }
                (todayEpochDay - earliestEpochDay).toInt().coerceIn(1, 30)
            }
        }

        // Compute prior rate e_ess^prior (cleaned of recurring obligations in essential categories)
        val todayDate = LocalDate.ofEpochDay(todayEpochDay)
        val daysInMonth = todayDate.lengthOfMonth().coerceAtLeast(1)
        val cleanedCategoryLimitDaily = computeCleanedCategoryLimitDailyRate(
            essentialCategoryMonthlyLimitsRub = essentialCategoryMonthlyLimitsRub,
            activeRecurringPayments = activeRecurringPayments,
            daysInMonth = daysInMonth
        )

        val (priorDailyRateRub, priorMethod) = when {
            userEssentialDailyNormRub != null && userEssentialDailyNormRub > 0.0 ->
                userEssentialDailyNormRub to EssentialEstimationMethod.USER_DEFINED_NORM
            cleanedCategoryLimitDaily > 0.0 ->
                cleanedCategoryLimitDaily to EssentialEstimationMethod.CATEGORY_LIMIT_FALLBACK
            else ->
                DEFAULT_CONSERVATIVE_FALLBACK_RUB to EssentialEstimationMethod.CONSERVATIVE_SYSTEM_FALLBACK
        }

        // Cold-start with 0 observed days
        if (observedHistoryDays <= 0 || pureSamples30.isEmpty()) {
            val isUnverifiedFallback = priorMethod == EssentialEstimationMethod.CONSERVATIVE_SYSTEM_FALLBACK
            return EssentialForecastResult(
                dailyEssentialRateRub = priorDailyRateRub,
                weeklyBucketEstimateRub = 0.0,
                winsorizedAmortizedEstimateRub = 0.0,
                priorDailyRateRub = priorDailyRateRub,
                observedHistoryDays = 0,
                method = priorMethod,
                confidenceLevel = if (isUnverifiedFallback) {
                    EssentialConfidenceLevel.UNVERIFIED_FALLBACK
                } else {
                    EssentialConfidenceLevel.USER_OR_LIMIT_BASED
                },
                requiresEssentialBudgetPrompt = isUnverifiedFallback,
                warningMessage = if (isUnverifiedFallback) {
                    "История базовых расходов отсутствует: используется ориентировочный резерв ${priorDailyRateRub.toInt()} ₽/день (низкая достоверность). Укажите ваш дневной бюджет на базовые нужды."
                } else {
                    null
                }
            )
        }

        // If N_obs < 14, compute historical rate over the observed window [todayEpochDay - observedHistoryDays, todayEpochDay - 1]
        val weeklyBucketEstimateRub: Double
        val winsorizedAmortizedEstimateRub: Double
        val historicalRateRub: Double

        if (observedHistoryDays < 14) {
            val activeSpanDays = observedHistoryDays.coerceAtLeast(1)
            val recentStart = todayEpochDay - activeSpanDays.toLong()
            val recentSamples = pureSamples30.filter { it.epochDay in recentStart..windowEnd }
            val rawMean = recentSamples.sumOf { it.amountRub } / activeSpanDays.toDouble()
            weeklyBucketEstimateRub = rawMean
            winsorizedAmortizedEstimateRub = rawMean
            historicalRateRub = rawMean
        } else {
            // 4-week bucketed Mid-50% trimmed mean
            val bucketTotals = DoubleArray(4) { k ->
                val bucketEnd = todayEpochDay - (7L * k + 1L)
                val bucketStart = todayEpochDay - (7L * (k + 1L))
                pureSamples28
                    .filter { it.epochDay in bucketStart..bucketEnd }
                    .sumOf { it.amountRub }
            }.sortedArray()

            weeklyBucketEstimateRub = (bucketTotals[1] + bucketTotals[2]) / 14.0

            // Winsorized amortization over 28-day window to capture periodic medicine/pharmacy + weekly groceries
            val dailyTotals28 = DoubleArray(28) { idx ->
                val day = windowStart28 + idx.toLong()
                pureSamples28.filter { it.epochDay == day }.sumOf { it.amountRub }
            }
            val sortedDaily = dailyTotals28.sortedArray()
            val p90Daily = sortedDaily[(28 * 0.90).toInt().coerceIn(0, 27)]
            val medianWeekly = (bucketTotals[1] + bucketTotals[2]) / 2.0
            // Cap single-day outliers above max(2 * P90_daily, 1.5 * medianWeekly, 10000)
            val winsorCap = max(max(p90Daily * 2.0, medianWeekly * 1.5), 5_000.0)
            val winsorizedSum28 = dailyTotals28.sumOf { min(it, winsorCap) }
            winsorizedAmortizedEstimateRub = winsorizedSum28 / 28.0

            historicalRateRub = max(weeklyBucketEstimateRub, winsorizedAmortizedEstimateRub)
        }

        // Empirical Bayes shrinkage when N_obs < 14
        return if (observedHistoryDays < 14) {
            val alpha = (observedHistoryDays.toDouble() / SHRINKAGE_FULL_CREDIBILITY_DAYS).coerceIn(0.0, 1.0)
            val blendedRate = alpha * historicalRateRub + (1.0 - alpha) * priorDailyRateRub
            EssentialForecastResult(
                dailyEssentialRateRub = blendedRate,
                weeklyBucketEstimateRub = weeklyBucketEstimateRub,
                winsorizedAmortizedEstimateRub = winsorizedAmortizedEstimateRub,
                priorDailyRateRub = priorDailyRateRub,
                observedHistoryDays = observedHistoryDays,
                method = EssentialEstimationMethod.EMPIRICAL_BAYES_SHRINKAGE,
                confidenceLevel = EssentialConfidenceLevel.MODERATE_BLENDED,
                requiresEssentialBudgetPrompt = false,
                warningMessage = null
            )
        } else {
            EssentialForecastResult(
                dailyEssentialRateRub = historicalRateRub,
                weeklyBucketEstimateRub = weeklyBucketEstimateRub,
                winsorizedAmortizedEstimateRub = winsorizedAmortizedEstimateRub,
                priorDailyRateRub = priorDailyRateRub,
                observedHistoryDays = observedHistoryDays,
                method = EssentialEstimationMethod.WEEKLY_TRIMMED_WINSORIZED,
                confidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
                requiresEssentialBudgetPrompt = false,
                warningMessage = null
            )
        }
    }

    /**
     * Subtracts monthly recurring expenses in essential categories from the category limits
     * before dividing by daysInMonth so recurring obligations are never double-counted.
     */
    fun computeCleanedCategoryLimitDailyRate(
        essentialCategoryMonthlyLimitsRub: Map<String, Double>,
        activeRecurringPayments: List<RecurringPaymentDomain>,
        daysInMonth: Int
    ): Double {
        if (essentialCategoryMonthlyLimitsRub.isEmpty() || daysInMonth <= 0) return 0.0
        var totalCleanedMonthlyLimit = 0.0
        for ((category, limitRub) in essentialCategoryMonthlyLimitsRub) {
            if (!isEssentialCategory(category) || limitRub <= 0.0) continue
            val monthlyRecurringInCat = activeRecurringPayments
                .filter { !it.isIncome && it.status == RecurringStatus.ACTIVE && it.categoryName.equals(category, ignoreCase = true) }
                .sumOf { rp ->
                    when (rp.frequency) {
                        PaymentFrequency.DAILY -> rp.amount * daysInMonth
                        PaymentFrequency.WEEKLY -> rp.amount * 4.0
                        PaymentFrequency.BIWEEKLY -> rp.amount * 2.0
                        PaymentFrequency.MONTHLY -> rp.amount
                        PaymentFrequency.QUARTERLY -> rp.amount / 3.0
                        PaymentFrequency.YEARLY -> rp.amount / 12.0
                        PaymentFrequency.CUSTOM_DAYS -> rp.amount * (daysInMonth.toDouble() / (rp.customIntervalDays ?: 30).coerceAtLeast(1))
                    }
                }
            totalCleanedMonthlyLimit += max(0.0, limitRub - monthlyRecurringInCat)
        }
        return totalCleanedMonthlyLimit / daysInMonth.toDouble()
    }
}
