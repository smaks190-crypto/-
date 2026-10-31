package com.example.data.safetospend

import com.example.data.db.AccountEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.RecurringExecutionLogEntity
import com.example.data.db.RecurringPaymentEntity
import com.example.data.db.TransactionEntity
import com.example.data.repository.FinancialSemanticsCalculator
import com.example.data.repository.FinancialSemanticsSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

data class UnifiedFinancialIntelligenceSnapshot(
    val semantics: FinancialSemanticsSnapshot,
    val safeToSpendInput: SafeToSpendInput,
    val safeToSpendResult: SafeToSpendResult,
    val essentialForecast: EssentialForecastResult
)

object DavidFinancialIntelligenceProvider {

    private val ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /**
     * Builds the unified [UnifiedFinancialIntelligenceSnapshot] from Room entities,
     * preserving 100% compatibility with PR #58 ([FinancialSemanticsCalculator])
     * and preventing double subtraction of GoalEntity savings.
     */
    fun computeSnapshot(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        goals: List<GoalEntity>,
        categories: List<CategoryEntity> = emptyList(),
        recurringPayments: List<RecurringPaymentEntity> = emptyList(),
        executionLogs: List<RecurringExecutionLogEntity> = emptyList(),
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        userSafetyFloorRub: Double = 0.0,
        userLockedReserveRub: Double = 0.0,
        userEssentialDailyNormRub: Double? = null,
        explicitInBalanceLockedGoalsRub: Double = 0.0
    ): UnifiedFinancialIntelligenceSnapshot {
        val todayDate = LocalDate.ofEpochDay(todayEpochDay)
        val todayYearMonth = YearMonth.from(todayDate).toString() // "YYYY-MM"
        val nonTransferTransactions = transactions.filterNot { isInternalTransferTransaction(it) }
        val periodTransactions = nonTransferTransactions.filter { it.date.startsWith(todayYearMonth) }

        // 1. Single source of truth for B_avail and debts from PR #58 FinancialSemanticsCalculator
        val semantics = FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = nonTransferTransactions,
            periodTransactions = periodTransactions,
            accounts = accounts
        )

        val domainRecurring = recurringPayments.map { it.toDomain() }
        val domainLogs = executionLogs.map { it.toDomain() }
        val logByTxId = domainLogs
            .filter { !it.linkedTransactionId.isNullOrBlank() }
            .associateBy { it.linkedTransactionId!! }
        val recurringById = domainRecurring.associateBy { it.id }

        // 2. Parse historical expense samples & today's expenses (excluding debt ledger, internal transfers & savings transfers)
        val debtAccountIds = accounts.filter { FinancialSemanticsCalculator.isDebtAccount(it) }.map { it.id }.toSet()
        val historicalSamples = mutableListOf<HistoricalExpenseSample>()
        val todayExpenses = mutableListOf<TodayClassifiedExpense>()

        for (tx in nonTransferTransactions) {
            if (!tx.type.equals("expense", ignoreCase = true)) continue
            if (tx.accountId != null && tx.accountId in debtAccountIds) continue
            if (FinancialSemanticsCalculator.isDebtTransaction(tx)) continue
            if (isSavingsGoalTransaction(tx)) continue
            if (tx.amount <= 0.0) continue

            val txEpochDay = parseTransactionEpochDay(tx.date) ?: continue
            val linkedLog = logByTxId[tx.id]
            val linkedRecurring = linkedLog?.let { recurringById[it.recurringId] }

            if (txEpochDay == todayEpochDay) {
                val streamType = EssentialSpendingEstimator.classifyExpenseStream(
                    categoryName = tx.category,
                    linkedRecurringId = linkedLog?.recurringId,
                    recurringPriority = linkedRecurring?.priority
                )
                todayExpenses.add(
                    TodayClassifiedExpense(
                        transactionId = tx.id,
                        categoryName = tx.category,
                        amountRub = tx.amount,
                        streamType = streamType,
                        linkedRecurringId = linkedLog?.recurringId
                    )
                )
            } else if (txEpochDay < todayEpochDay) {
                historicalSamples.add(
                    HistoricalExpenseSample(
                        transactionId = tx.id,
                        epochDay = txEpochDay,
                        categoryName = tx.category,
                        subcategoryName = tx.subcategory,
                        amountRub = tx.amount,
                        linkedRecurringId = linkedLog?.recurringId
                    )
                )
            }
        }

        // 3. Extract essential & discretionary category monthly limits
        val essentialCategoryLimits = mutableMapOf<String, Double>()
        var totalDiscretionaryBudgetLimit = 0.0
        var totalDiscretionaryCategoriesCount = 0
        val discretionaryLimitedCategories = mutableSetOf<String>()

        for (cat in categories) {
            if (!cat.type.equals("expense", ignoreCase = true)) continue
            val isEss = EssentialSpendingEstimator.isEssentialCategory(cat.name)
            val isMand = EssentialSpendingEstimator.isMandatoryCategory(cat.name)
            if (!isEss && !isMand) {
                totalDiscretionaryCategoriesCount++
            }
            val limit = cat.monthlyLimit ?: 0.0
            if (limit <= 0.0) continue
            if (isEss) {
                essentialCategoryLimits[cat.name] = limit
            } else if (!isMand) {
                totalDiscretionaryBudgetLimit += limit
                discretionaryLimitedCategories.add(cat.name.lowercase(Locale("ru", "RU")))
            }
        }

        // 4. Compute Essential Spending Forecast e_ess
        val essentialForecast = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = todayEpochDay,
            historicalSamples = historicalSamples,
            activeRecurringPayments = domainRecurring,
            executionLogs = domainLogs,
            userEssentialDailyNormRub = userEssentialDailyNormRub,
            essentialCategoryMonthlyLimitsRub = essentialCategoryLimits
        )

        // 5. Compute discretionary category remaining budget ONLY when all discretionary expense categories
        //    have an explicit monthly limit configured (a partial limit on 1-2 categories is a category cap,
        //    not a global cap on the user's entire discretionary liquidity pool).
        val hasComprehensiveDiscretionaryBudget = totalDiscretionaryCategoriesCount > 0 &&
            discretionaryLimitedCategories.size == totalDiscretionaryCategoriesCount
        val discretionaryCategoryRemainingRub: Double? = if (hasComprehensiveDiscretionaryBudget && totalDiscretionaryBudgetLimit > 0.0) {
            val spentBeforeTodayInDiscCats = transactions
                .filter { tx ->
                    tx.type.equals("expense", ignoreCase = true) &&
                        (tx.accountId == null || tx.accountId !in debtAccountIds) &&
                        !FinancialSemanticsCalculator.isDebtTransaction(tx) &&
                        tx.date.startsWith(todayYearMonth) &&
                        parseTransactionEpochDay(tx.date)?.let { it < todayEpochDay } == true &&
                        tx.category.lowercase(Locale("ru", "RU")) in discretionaryLimitedCategories &&
                        logByTxId[tx.id] == null
                }
                .sumOf { it.amount }

            val recurringInDiscCats = domainRecurring
                .filter {
                    !it.isIncome &&
                        it.status == RecurringStatus.ACTIVE &&
                        it.categoryName.lowercase(Locale("ru", "RU")) in discretionaryLimitedCategories
                }
                .sumOf { it.effectiveReservedAmountRub }

            max(0.0, totalDiscretionaryBudgetLimit - spentBeforeTodayInDiscCats - recurringInDiscCats)
        } else {
            null
        }

        // 6. Map scheduled debts from accounts (compatible with PR #58 FinancialSemanticsCalculator)
        val scheduledDebts = accounts
            .filter { FinancialSemanticsCalculator.isDebtAccount(it) }
            .mapNotNull { debtAccount ->
                val snapshot = FinancialSemanticsCalculator.calculateRemainingDebt(debtAccount, transactions)
                if (snapshot.remainingAmount <= 0.0) return@mapNotNull null
                val parsedDueDate = parseDueDateFromComment(debtAccount.accountNumber)
                ScheduledDebtObligation(
                    debtId = debtAccount.id,
                    name = debtAccount.name,
                    remainingAmountRub = snapshot.remainingAmount,
                    isWeOwe = snapshot.isWeOwe,
                    dueDateEpochDay = parsedDueDate,
                    incomeCertainty = IncomeCertainty.HYPOTHETICAL
                )
            }

        // 7. Unambiguous R_locked calculation:
        //    In David Finance, GoalEntity contributions are recorded as "expense" transactions under category "Сбережения",
        //    which have ALREADY been subtracted from semantics.availableBalanceRub!
        //    Therefore, unTransactedLockedGoalsRub only includes explicitly marked in-balance locked amounts
        //    that were NOT deducted via "Сбережения" expense transactions.
        val unTransactedLockedGoalsRub = max(0.0, explicitInBalanceLockedGoalsRub)

        val daysRemainingInMonth = SafeToSpendInput.computeDaysRemainingInMonth(todayEpochDay)

        val input = SafeToSpendInput(
            todayEpochDay = todayEpochDay,
            daysRemainingInMonth = daysRemainingInMonth,
            availableBalanceRub = semantics.availableBalanceRub,
            userSafetyFloorRub = userSafetyFloorRub,
            userLockedReserveRub = userLockedReserveRub,
            unTransactedLockedGoalsRub = unTransactedLockedGoalsRub,
            activeRecurringPayments = domainRecurring,
            executionLogs = domainLogs,
            scheduledDebts = scheduledDebts,
            todayExpenses = todayExpenses,
            essentialDailyRateRub = essentialForecast.dailyEssentialRateRub,
            essentialEstimationMethod = essentialForecast.method,
            essentialConfidenceLevel = essentialForecast.confidenceLevel,
            requiresEssentialBudgetPrompt = essentialForecast.requiresEssentialBudgetPrompt,
            essentialWarningMessage = essentialForecast.warningMessage,
            discretionaryCategoryRemainingRub = discretionaryCategoryRemainingRub
        )

        val result = SafeToSpendCalculator.calculate(input)

        return UnifiedFinancialIntelligenceSnapshot(
            semantics = semantics,
            safeToSpendInput = input,
            safeToSpendResult = result,
            essentialForecast = essentialForecast
        )
    }

    fun simulateWhatIf(
        input: SafeToSpendInput,
        request: WhatIfScenarioRequest
    ): WhatIfSimulationResult {
        return WhatIfSimulator.simulate(input, request)
    }

    fun isInternalTransferTransaction(tx: TransactionEntity): Boolean {
        val cat = tx.category.trim().lowercase(Locale("ru", "RU"))
        val sub = tx.subcategory.trim().lowercase(Locale("ru", "RU"))
        return cat.contains("перевод между счетами") ||
            cat == "перевод" ||
            cat == "внутренний перевод" ||
            cat == "между своими счетами" ||
            sub.contains("перевод между счетами")
    }

    fun isSavingsGoalTransaction(tx: TransactionEntity): Boolean {
        val cat = tx.category.trim().lowercase(Locale("ru", "RU"))
        return cat == "сбережения" || cat == "копилка" || cat == "цели"
    }

    fun parseTransactionEpochDay(rawDate: String): Long? {
        val clean = rawDate.trim().take(10)
        if (clean.length < 10) return null
        return runCatching {
            LocalDate.parse(clean, ISO_DATE_FORMATTER).toEpochDay()
        }.getOrNull()
    }

    private fun parseDueDateFromComment(rawComment: String): Long? {
        if (rawComment.isBlank()) return null
        val isoMatch = Regex("""\b(\d{4}-\d{2}-\d{2})\b""").find(rawComment)
        if (isoMatch != null) {
            return parseTransactionEpochDay(isoMatch.groupValues[1])
        }
        val ruMatch = Regex("""\b(\d{2})\.(\d{2})\.(\d{4})\b""").find(rawComment)
        if (ruMatch != null) {
            val day = ruMatch.groupValues[1]
            val month = ruMatch.groupValues[2]
            val year = ruMatch.groupValues[3]
            return parseTransactionEpochDay("$year-$month-$day")
        }
        return null
    }
}
