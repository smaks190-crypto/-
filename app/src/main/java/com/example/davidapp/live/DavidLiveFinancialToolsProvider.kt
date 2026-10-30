package com.example.davidapp.live

import com.example.data.db.AccountDao
import com.example.data.db.CategoryDao
import com.example.data.db.GoalDao
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
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
    private val currentTotalBalanceProvider: () -> Double = { 0.0 }
) {
    /** Strictly read-only: David Live never modifies financial records without confirmation. */
    val canMutateRecords: Boolean = false

    private val _verifiedFacts = mutableListOf<DavidLiveToolFact>()
    val verifiedFacts: List<DavidLiveToolFact>
        get() = synchronized(_verifiedFacts) { _verifiedFacts.toList() }

    private val recentToolResultsCache = mutableMapOf<String, Pair<Long, JSONObject>>()

    fun setBudgetId(newBudgetId: String) {
        val normalized = newBudgetId.ifBlank { "default" }
        if (budgetId != normalized) {
            budgetId = normalized
            synchronized(recentToolResultsCache) {
                recentToolResultsCache.clear()
            }
        }
    }

    fun clearVerifiedFacts() {
        synchronized(_verifiedFacts) {
            _verifiedFacts.clear()
        }
        synchronized(recentToolResultsCache) {
            recentToolResultsCache.clear()
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
                        "Получить реальную сводку по бюджету пользователя за последние 30 дней: " +
                            "общий баланс, доходы, расходы, чистый денежный поток, дневной темп трат и прогноз остатка до конца месяца."
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
                        "Получить список реальных финансовых целей (копилок) и счетов/долгов пользователя с текущим прогрессом."
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

        val accountsBalance = if (accounts.isNotEmpty()) {
            accounts.sumOf { it.balance }
        } else {
            val allIncomes = allTransactions.filter { it.type.equals("income", ignoreCase = true) }.sumOf { it.amount }
            val allExpenses = allTransactions.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { it.amount }
            val netAll = allIncomes - allExpenses
            if (netAll != 0.0 || allTransactions.isNotEmpty()) netAll else currentTotalBalanceProvider()
        }

        val totalIncome = transactions.filter { it.type.equals("income", ignoreCase = true) }
            .sumOf { it.amount }
        val totalExpense = transactions.filter { it.type.equals("expense", ignoreCase = true) }
            .sumOf { it.amount }
        val netFlow = totalIncome - totalExpense
        val dailyBurnRate = totalExpense / 30.0

        val cal = Calendar.getInstance()
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val remainingDaysInMonth = (maxDay - currentDay).coerceAtLeast(1)
        val projectedRemainingExpense = dailyBurnRate * remainingDaysInMonth
        val projectedEndOfMonthBalance = accountsBalance - projectedRemainingExpense

        val summary = "Баланс: ${accountsBalance.roundToLong()} ₽, " +
            "Доходы (30 дн.): ${totalIncome.roundToLong()} ₽, " +
            "Расходы (30 дн.): ${totalExpense.roundToLong()} ₽, " +
            "Средний расход в день: ${dailyBurnRate.roundToLong()} ₽/день, " +
            "Прогноз остатка к концу месяца: ${projectedEndOfMonthBalance.roundToLong()} ₽."

        return JSONObject().apply {
            put("status", "ok")
            put("hasData", transactions.isNotEmpty() || accounts.isNotEmpty() || accountsBalance != 0.0)
            put("totalBalanceRub", accountsBalance.roundToLong())
            put("income30dRub", totalIncome.roundToLong())
            put("expense30dRub", totalExpense.roundToLong())
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
        val goalsArray = JSONArray()
        val summaryParts = mutableListOf<String>()

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
            summaryParts.add(
                "${goal.name}: накоплено ${goal.currentAmount.roundToLong()} ₽ из ${goal.targetAmount.roundToLong()} ₽ ($progressPct%)"
            )
        }

        val humanSummary = if (summaryParts.isEmpty()) {
            "У пользователя пока нет активных финансовых целей."
        } else {
            "Финансовые цели: " + summaryParts.joinToString("; ")
        }

        return JSONObject().apply {
            put("status", "ok")
            put("count", goals.size)
            put("items", goalsArray)
            put("humanSummary", humanSummary)
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

        val currentExpense = currentTx.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { it.amount }
        val previousExpense = previousTx.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { it.amount }
        val currentIncome = currentTx.filter { it.type.equals("income", ignoreCase = true) }.sumOf { it.amount }
        val previousIncome = previousTx.filter { it.type.equals("income", ignoreCase = true) }.sumOf { it.amount }

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
        const val TOOL_DEDUP_WINDOW_MS = 60_000L
        const val TOOL_FINANCIAL_OVERVIEW = "get_financial_overview"
        const val TOOL_CATEGORY_SPENDING = "get_category_spending"
        const val TOOL_GOALS_AND_DEBTS = "get_goals_and_debts"
        const val TOOL_COMPARE_PERIODS = "compare_spending_periods"
        const val TOOL_RECENT_TRANSACTIONS = "get_recent_transactions"
    }
}
