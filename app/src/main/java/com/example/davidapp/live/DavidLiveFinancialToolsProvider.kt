package com.example.davidapp.live

import com.example.data.db.AccountDao
import com.example.data.db.CategoryDao
import com.example.data.db.GoalDao
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import com.example.data.repository.FinancialSemanticsCalculator
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Provides controlled, READ-ONLY access to real financial data for David Live.
 *
 * Security & Architecture Guarantees:
 * - Zero write/insert/update/delete capabilities (`canMutateRecords = false`).
 * - Does not dump the entire transaction database into the initial prompt.
 * - Executes targeted queries on demand via Gemini Live `toolCall` (`functionCalls`).
 * - Records every returned fact into [verifiedFacts] so the post-conversation summary
 *   only references verified user numbers.
 */
class DavidLiveFinancialToolsProvider(
    private var budgetId: String = "default",
    private val transactionDao: TransactionDao?,
    private val categoryDao: CategoryDao?,
    private val goalDao: GoalDao?,
    private val accountDao: AccountDao?,
    private val currentTotalBalanceProvider: () -> Double = { 0.0 },
    private var conversationalUserMessagesProvider: () -> List<String> = { emptyList() }
) {
    /** Strictly read-only: David Live never modifies financial records without confirmation. */
    val canMutateRecords: Boolean = false

    fun setConversationalUserMessagesProvider(provider: () -> List<String>) {
        conversationalUserMessagesProvider = provider
    }

    private val _verifiedFacts = mutableListOf<DavidLiveToolFact>()
    val verifiedFacts: List<DavidLiveToolFact>
        get() = synchronized(_verifiedFacts) { _verifiedFacts.toList() }

    fun setBudgetId(newBudgetId: String) {
        budgetId = newBudgetId.ifBlank { "default" }
    }

    fun clearVerifiedFacts() {
        synchronized(_verifiedFacts) {
            _verifiedFacts.clear()
        }
    }

    /**
     * Builds the `functionDeclarations` JSON array for the Gemini Live `setup` message.
     */
    fun buildFunctionDeclarationsJson(): JSONArray {
        return JSONArray().apply {
            put(
                JSONObject().apply {
                    put("name", TOOL_FINANCIAL_OVERVIEW)
                    put(
                        "description",
                        "Получить реальную сводку по бюджету пользователя за последние 30 дней со строгим разделением: " +
                            "доступный остаток (текущий баланс), заработанный доход (в т.ч. зарплата), расходы, " +
                            "мои обязательства («Я должен») и дебиторская задолженность («Мне должны»)."
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        }
                    )
                }
            )
            put(
                JSONObject().apply {
                    put("name", TOOL_CATEGORY_SPENDING)
                    put(
                        "description",
                        "Получить реальные расходы по категориям, установленные лимиты (бюджеты категорий) и процент их использования."
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put(
                                        "categoryQuery",
                                        JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Необязательный фильтр по названию категории")
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            put(
                JSONObject().apply {
                    put("name", TOOL_GOALS_AND_DEBTS)
                    put(
                        "description",
                        "Получить список реальных финансовых целей (копилок) и отдельно долгов пользователя " +
                            "(«Я должен» — обязательства и «Мне должны» — дебиторская задолженность) с остатками."
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        }
                    )
                }
            )
            put(
                JSONObject().apply {
                    put("name", TOOL_COMPARE_PERIODS)
                    put(
                        "description",
                        "Сравнить динамику расходов и доходов за последние 7 или 30 дней с предыдущим аналогичным периодом."
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put(
                                        "days",
                                        JSONObject().apply {
                                            put("type", "INTEGER")
                                            put("description", "Количество дней для сравнения (7 или 30)")
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
            put(
                JSONObject().apply {
                    put("name", TOOL_RECENT_TRANSACTIONS)
                    put(
                        "description",
                        "Получить последние реальные операции пользователя (до 10 записей) для ответа на конкретный вопрос."
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put(
                                        "limit",
                                        JSONObject().apply {
                                            put("type", "INTEGER")
                                            put("description", "Количество последних операций (от 1 до 10)")
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }
    }

    /**
     * Executes a read-only tool call requested by Gemini Live and records verified facts.
     */
    suspend fun executeToolCall(toolName: String, args: JSONObject?): JSONObject {
        val result = when (toolName) {
            TOOL_FINANCIAL_OVERVIEW -> queryFinancialOverview()
            TOOL_CATEGORY_SPENDING -> {
                val filter = args?.optString("categoryQuery")?.takeIf { it.isNotBlank() }
                queryCategorySpending(filter)
            }
            TOOL_GOALS_AND_DEBTS -> queryGoalsAndDebts()
            TOOL_COMPARE_PERIODS -> {
                val days = args?.optInt("days", 30)?.coerceIn(7, 90) ?: 30
                queryPeriodComparison(days)
            }
            TOOL_RECENT_TRANSACTIONS -> {
                val limit = args?.optInt("limit", 5)?.coerceIn(1, 10) ?: 5
                queryRecentTransactions(limit)
            }
            else -> JSONObject().apply {
                put("status", "unsupported_tool")
                put("message", "Инструмент $toolName не поддерживается или запрещён в режиме только для чтения.")
            }
        }

        val summaryText = result.optString("humanSummary").takeIf { it.isNotBlank() }
            ?: result.toString().take(240)
        synchronized(_verifiedFacts) {
            _verifiedFacts.add(
                DavidLiveToolFact(
                    toolName = toolName,
                    summary = summaryText
                )
            )
        }
        return result
    }

    private fun parseTransactionMillis(tx: TransactionEntity): Long {
        if (tx.createdAt > 0L) return tx.createdAt
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(tx.date)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun queryFinancialOverview(): JSONObject {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24L * 3600L * 1000L
        val allTransactions = transactionDao?.getTransactionsByBudgetId(budgetId)
            ?.firstOrNull()
            .orEmpty()
        val transactions = allTransactions.filter { parseTransactionMillis(it) >= thirtyDaysAgo }
        val accounts = accountDao?.getAccountsByBudgetId(budgetId)?.firstOrNull().orEmpty()

        val baseSnapshot = FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = allTransactions,
            periodTransactions = transactions,
            accounts = accounts,
            fallbackBalance = currentTotalBalanceProvider()
        )
        val snapshot = FinancialSemanticsCalculator.mergeSnapshotWithConversationalMentions(
            baseSnapshot = baseSnapshot,
            userMessages = conversationalUserMessagesProvider()
        )

        val availableBalance = snapshot.availableBalanceRub
        val earnedIncome = snapshot.earnedIncomeRub
        val totalExpense = snapshot.totalExpenseRub
        val netFlow = earnedIncome - totalExpense
        val dailyBurnRate = snapshot.regularExpenseRub / 30.0

        val cal = Calendar.getInstance()
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val remainingDaysInMonth = (maxDay - currentDay).coerceAtLeast(1)
        val projectedRemainingExpense = dailyBurnRate * remainingDaysInMonth
        val projectedEndOfMonthBalance = availableBalance - projectedRemainingExpense

        val summary = buildString {
            append("Доступный остаток (баланс): ${availableBalance.roundToLong()} ₽ (НЕ суммировать с зарплатой и долгами), ")
            append("Заработанный доход (30 дн.): ${earnedIncome.roundToLong()} ₽ (в т.ч. зарплата: ${snapshot.salaryIncomeRub.roundToLong()} ₽), ")
            if (snapshot.debtReturnedIncomeRub > 0.0) {
                append("Возврат долгов мне (30 дн.): ${snapshot.debtReturnedIncomeRub.roundToLong()} ₽ (не является доходом), ")
            }
            append("Расходы (30 дн.): ${totalExpense.roundToLong()} ₽, ")
            append("Я должен (обязательства): ${snapshot.totalWeOweRub.roundToLong()} ₽, ")
            append("Мне должны (дебиторская задолженность): ${snapshot.totalOwesUsRub.roundToLong()} ₽, ")
            append("Средний расход в день: ${dailyBurnRate.roundToLong()} ₽/день, ")
            append("Прогноз остатка к концу месяца: ${projectedEndOfMonthBalance.roundToLong()} ₽.")
        }

        return JSONObject().apply {
            put("status", "ok")
            put("hasData", transactions.isNotEmpty() || accounts.isNotEmpty() || availableBalance != 0.0)
            put("totalBalanceRub", availableBalance.roundToLong())
            put("availableBalanceRub", availableBalance.roundToLong())
            put("income30dRub", earnedIncome.roundToLong())
            put("earnedIncome30dRub", earnedIncome.roundToLong())
            put("salaryIncome30dRub", snapshot.salaryIncomeRub.roundToLong())
            put("otherEarnedIncome30dRub", snapshot.otherEarnedIncomeRub.roundToLong())
            put("balanceAdjustment30dRub", snapshot.balanceAdjustmentRub.roundToLong())
            put("debtReturned30dRub", snapshot.debtReturnedIncomeRub.roundToLong())
            put("expense30dRub", totalExpense.roundToLong())
            put("regularExpense30dRub", snapshot.regularExpenseRub.roundToLong())
            put("debtRepayment30dRub", snapshot.debtRepaymentExpenseRub.roundToLong())
            put("totalWeOweRub", snapshot.totalWeOweRub.roundToLong())
            put("totalOwesUsRub", snapshot.totalOwesUsRub.roundToLong())
            put("netCashFlow30dRub", netFlow.roundToLong())
            put("dailyBurnRateRub", dailyBurnRate.roundToLong())
            put("remainingDaysInMonth", remainingDaysInMonth)
            put("projectedEndOfMonthBalanceRub", projectedEndOfMonthBalance.roundToLong())
            put("transactionsCount30d", transactions.size)
            put("humanSummary", summary)
        }
    }

    private suspend fun queryCategorySpending(categoryQuery: String?): JSONObject {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24L * 3600L * 1000L
        val transactions = transactionDao?.getTransactionsByBudgetId(budgetId)
            ?.firstOrNull()
            .orEmpty()
            .filter { parseTransactionMillis(it) >= thirtyDaysAgo }
        val categories = categoryDao?.getCategoriesByBudgetId(budgetId)?.firstOrNull().orEmpty()

        val expensesByCat = transactions
            .filter { it.type.equals("expense", ignoreCase = true) }
            .groupBy { it.category.trim() }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val categoryArray = JSONArray()
        val summaryParts = mutableListOf<String>()

        val allCatNames = (categories.map { it.name } + expensesByCat.keys)
            .distinct()
            .filter { name ->
                categoryQuery.isNullOrBlank() || name.contains(categoryQuery, ignoreCase = true)
            }

        for (catName in allCatNames) {
            val catEntity = categories.firstOrNull { it.name.equals(catName, ignoreCase = true) }
            val spent = expensesByCat[catName] ?: 0.0
            val limit = catEntity?.monthlyLimit ?: 0.0
            if (spent <= 0.0 && limit <= 0.0) continue

            val usagePercent = if (limit > 0.0) ((spent / limit) * 100.0).roundToLong() else null
            categoryArray.put(
                JSONObject().apply {
                    put("category", catName)
                    put("spentRub", spent.roundToLong())
                    put("budgetLimitRub", limit.roundToLong())
                    if (usagePercent != null) {
                        put("limitUsagePercent", usagePercent)
                        put("remainingLimitRub", (limit - spent).roundToLong())
                    }
                }
            )
            val limitNote = if (limit > 0.0) {
                " (лимит ${limit.roundToLong()} ₽, исчерпан на ${usagePercent}%)"
            } else {
                ""
            }
            summaryParts.add("$catName: ${spent.roundToLong()} ₽$limitNote")
        }

        val humanSummary = if (summaryParts.isEmpty()) {
            "По выбранным категориям расходов за последние 30 дней не найдено."
        } else {
            "Расходы по категориям: " + summaryParts.take(6).joinToString("; ")
        }

        return JSONObject().apply {
            put("status", "ok")
            put("categories", categoryArray)
            put("humanSummary", humanSummary)
        }
    }

    private suspend fun queryGoalsAndDebts(): JSONObject {
        val goals = goalDao?.getGoalsByBudgetId(budgetId)?.firstOrNull().orEmpty()
        val accounts = accountDao?.getAccountsByBudgetId(budgetId)?.firstOrNull().orEmpty()
        val allTransactions = transactionDao?.getTransactionsByBudgetId(budgetId)?.firstOrNull().orEmpty()

        val baseSnapshot = FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = allTransactions,
            periodTransactions = allTransactions,
            accounts = accounts
        )
        val snapshot = FinancialSemanticsCalculator.mergeSnapshotWithConversationalMentions(
            baseSnapshot = baseSnapshot,
            userMessages = conversationalUserMessagesProvider()
        )

        val goalsArray = JSONArray()
        val goalSummaryParts = mutableListOf<String>()

        for (goal in goals) {
            val progressPct = if (goal.targetAmount > 0.0) {
                ((goal.currentAmount / goal.targetAmount) * 100.0).roundToLong()
            } else {
                0L
            }
            goalsArray.put(
                JSONObject().apply {
                    put("name", goal.name)
                    put("savedAmountRub", goal.currentAmount.roundToLong())
                    put("targetAmountRub", goal.targetAmount.roundToLong())
                    put("remainingRub", (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0).roundToLong())
                    put("progressPercent", progressPct)
                }
            )
            goalSummaryParts.add(
                "${goal.name}: накоплено ${goal.currentAmount.roundToLong()} ₽ из ${goal.targetAmount.roundToLong()} ₽ ($progressPct%)"
            )
        }

        val weOweArray = JSONArray()
        val owesUsArray = JSONArray()
        val debtsArray = JSONArray()
        val debtSummaryParts = mutableListOf<String>()

        for (debt in snapshot.weOweDebts) {
            val obj = JSONObject().apply {
                put("id", debt.id)
                put("name", debt.name)
                put("direction", "we_owe")
                put("directionLabel", "Я должен (обязательство)")
                put("initialAmountRub", debt.initialAmount.roundToLong())
                put("repaidAmountRub", debt.repaidAmount.roundToLong())
                put("remainingAmountRub", debt.remainingAmount.roundToLong())
            }
            weOweArray.put(obj)
            debtsArray.put(obj)
            debtSummaryParts.add(
                "Я должен '${debt.name}': остаток ${debt.remainingAmount.roundToLong()} ₽ (из ${debt.initialAmount.roundToLong()} ₽)"
            )
        }

        for (debt in snapshot.owesUsDebts) {
            val obj = JSONObject().apply {
                put("id", debt.id)
                put("name", debt.name)
                put("direction", "owes_us")
                put("directionLabel", "Мне должны (дебиторская задолженность)")
                put("initialAmountRub", debt.initialAmount.roundToLong())
                put("returnedAmountRub", debt.repaidAmount.roundToLong())
                put("remainingAmountRub", debt.remainingAmount.roundToLong())
            }
            owesUsArray.put(obj)
            debtsArray.put(obj)
            debtSummaryParts.add(
                "Мне должны '${debt.name}': остаток ${debt.remainingAmount.roundToLong()} ₽ (из ${debt.initialAmount.roundToLong()} ₽)"
            )
        }

        val goalsSection = if (goalSummaryParts.isEmpty()) {
            "Активных финансовых целей нет."
        } else {
            "Финансовые цели: " + goalSummaryParts.joinToString("; ")
        }
        val debtsSection = if (debtSummaryParts.isEmpty()) {
            "Активных долгов нет (Я должен: 0 ₽, Мне должны: 0 ₽)."
        } else {
            "Долги (НЕ входят в баланс и доход): Я должен всего ${snapshot.totalWeOweRub.roundToLong()} ₽, Мне должны всего ${snapshot.totalOwesUsRub.roundToLong()} ₽ (" +
                debtSummaryParts.joinToString("; ") + ")."
        }

        return JSONObject().apply {
            put("status", "ok")
            put("count", goals.size)
            put("items", goalsArray)
            put("debtsCount", snapshot.weOweDebts.size + snapshot.owesUsDebts.size)
            put("debts", debtsArray)
            put("weOweDebts", weOweArray)
            put("owesUsDebts", owesUsArray)
            put("totalWeOweRub", snapshot.totalWeOweRub.roundToLong())
            put("totalOwesUsRub", snapshot.totalOwesUsRub.roundToLong())
            put("humanSummary", "$goalsSection $debtsSection")
        }
    }

    private suspend fun queryPeriodComparison(days: Int): JSONObject {
        val now = System.currentTimeMillis()
        val periodMs = days.toLong() * 24L * 3600L * 1000L
        val currentStart = now - periodMs
        val previousStart = currentStart - periodMs

        val allTx = transactionDao?.getTransactionsByBudgetId(budgetId)
            ?.firstOrNull()
            .orEmpty()

        val currentTx = allTx.filter {
            val ts = parseTransactionMillis(it)
            ts in currentStart..now
        }
        val previousTx = allTx.filter {
            val ts = parseTransactionMillis(it)
            ts in previousStart until currentStart
        }

        val currentSnapshot = FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = allTx,
            periodTransactions = currentTx
        )
        val previousSnapshot = FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = allTx,
            periodTransactions = previousTx
        )

        val currentExpense = currentSnapshot.totalExpenseRub
        val previousExpense = previousSnapshot.totalExpenseRub
        val currentIncome = currentSnapshot.earnedIncomeRub
        val previousIncome = previousSnapshot.earnedIncomeRub

        val expenseDiffPercent = if (previousExpense > 0.0) {
            (((currentExpense - previousExpense) / previousExpense) * 100.0).roundToLong()
        } else {
            null
        }

        val trendText = when {
            expenseDiffPercent == null -> "В предыдущем периоде ($days дн.) расходов не было, сейчас: ${currentExpense.roundToLong()} ₽."
            expenseDiffPercent > 0 -> "Расходы за $days дн. выросли на $expenseDiffPercent% (${currentExpense.roundToLong()} ₽ против ${previousExpense.roundToLong()} ₽)."
            expenseDiffPercent < 0 -> "Расходы за $days дн. снизились на ${abs(expenseDiffPercent)}% (${currentExpense.roundToLong()} ₽ против ${previousExpense.roundToLong()} ₽)."
            else -> "Расходы за $days дн. не изменились (${currentExpense.roundToLong()} ₽)."
        }

        return JSONObject().apply {
            put("status", "ok")
            put("periodDays", days)
            put("currentExpenseRub", currentExpense.roundToLong())
            put("previousExpenseRub", previousExpense.roundToLong())
            put("currentIncomeRub", currentIncome.roundToLong())
            put("previousIncomeRub", previousIncome.roundToLong())
            if (expenseDiffPercent != null) {
                put("expenseChangePercent", expenseDiffPercent)
            }
            put("humanSummary", trendText)
        }
    }

    private suspend fun queryRecentTransactions(limit: Int): JSONObject {
        val allTx = transactionDao?.getTransactionsByBudgetId(budgetId)?.firstOrNull().orEmpty()
        val recent = allTx.sortedByDescending { parseTransactionMillis(it) }.take(limit)
        val items = JSONArray()
        val summaryParts = mutableListOf<String>()

        for (tx in recent) {
            items.put(
                JSONObject().apply {
                    put("type", tx.type)
                    put("category", tx.category)
                    put("subcategory", tx.subcategory)
                    put("amountRub", tx.amount.roundToLong())
                    put("date", tx.date)
                }
            )
            val sub = if (tx.subcategory.isNotBlank() && tx.subcategory != tx.category) " (${tx.subcategory})" else ""
            summaryParts.add("${tx.category}$sub: ${tx.amount.roundToLong()} ₽")
        }

        return JSONObject().apply {
            put("status", "ok")
            put("transactions", items)
            put(
                "humanSummary",
                if (summaryParts.isEmpty()) {
                    "Операций в базе данных пока нет."
                } else {
                    "Последние операции: " + summaryParts.joinToString(", ")
                }
            )
        }
    }

    companion object {
        const val TOOL_FINANCIAL_OVERVIEW = "get_financial_overview"
        const val TOOL_CATEGORY_SPENDING = "get_category_spending"
        const val TOOL_GOALS_AND_DEBTS = "get_goals_and_debts"
        const val TOOL_COMPARE_PERIODS = "compare_spending_periods"
        const val TOOL_RECENT_TRANSACTIONS = "get_recent_transactions"
    }
}
