package com.example.davidapp.live

import android.os.Build
import com.example.data.db.AccountDao
import com.example.data.db.CategoryDao
import com.example.data.db.GoalDao
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import com.example.data.repository.FinancialSemanticsCalculator
import com.example.data.safetospend.DavidFinancialIntelligenceProvider
import com.example.data.safetospend.ExecutionStatus
import com.example.data.safetospend.IncomeCertainty
import com.example.data.safetospend.ObligationPriority
import com.example.data.safetospend.RecurringPaymentDomain
import com.example.data.safetospend.RecurringScheduleCalculator
import com.example.data.safetospend.RecurringStatus
import com.example.data.safetospend.SafeToSpendCalculator
import com.example.data.safetospend.SafeToSpendInput
import com.example.data.safetospend.SafeToSpendResult
import com.example.data.safetospend.WhatIfScenarioRequest
import com.example.data.safetospend.WhatIfSimulator
import com.example.data.safetospend.toDomain
import com.example.util.GlobalConsoleLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class ToolCallSpec(
    val toolName: String,
    val args: JSONObject = JSONObject()
)

/**
 * Provides controlled, READ-ONLY access to real financial data for David Live.
 *
 * Security & Architecture Guarantees:
 * - Zero write/insert/update/delete capabilities (`canMutateRecords = false`).
 * - Does not dump the entire transaction database into the initial prompt.
 * - Executes targeted queries on demand via Gemini Live `toolCall` (`functionCalls`).
 * - Strictly isolates data by the active `budgetId` and clears verified facts on profile switch.
 * - Records every verified returned fact into [verifiedFacts] so the post-conversation summary
 *   only references verified user numbers.
 */
class DavidLiveFinancialToolsProvider(
    @Volatile private var budgetId: String = "default",
    private val transactionDao: TransactionDao?,
    private val categoryDao: CategoryDao?,
    private val goalDao: GoalDao?,
    private val accountDao: AccountDao?,
    private val currentTotalBalanceProvider: () -> Double = { 0.0 },
    private val recurringPaymentDao: com.example.data.db.RecurringPaymentDao? = null,
    private val safeToSpendInputOverrideProvider: (() -> SafeToSpendInput?)? = null,
    private var conversationalUserMessagesProvider: () -> List<String> = { emptyList() },
    private val userSafetyFloorProvider: (String) -> Double = { 0.0 },
    private val userLockedReserveProvider: (String) -> Double = { 0.0 },
    private val userEssentialDailyNormProvider: (String) -> Double? = { null },
    private val todayEpochDayProvider: () -> Long = { LocalDate.now().toEpochDay() }
) {
    /** Strictly read-only: David Live never modifies financial records without confirmation. */
    val canMutateRecords: Boolean = false

    fun setConversationalUserMessagesProvider(provider: () -> List<String>) {
        conversationalUserMessagesProvider = provider
    }

    private val _verifiedFacts = mutableListOf<DavidLiveToolFact>()
    val verifiedFacts: List<DavidLiveToolFact>
        get() = synchronized(_verifiedFacts) { _verifiedFacts.toList() }

    fun getBudgetId(): String = budgetId

    fun setBudgetId(newBudgetId: String) {
        val normalized = newBudgetId.ifBlank { "default" }
        val changed = normalized != budgetId
        budgetId = normalized
        if (changed) {
            clearVerifiedFacts()
        }
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
                        "Получить реальную сводку по бюджету активного профиля за последние 30 дней со строгим разделением: " +
                            "доступный остаток (текущий баланс), заработанный доход (в т.ч. полученная зарплата), расходы, " +
                            "мои обязательства («Я должен»), дебиторская задолженность («Мне должны»), безопасный лимит трат на сегодня (Safe-to-Spend 2.0) " +
                            "и ближайшие регулярные обязательства/доходы."
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
                    put("name", TOOL_SAFE_TO_SPEND_FORECAST)
                    put(
                        "description",
                        "Получить полную математическую расшифровку прогноза ликвидности Safe-to-Spend 2.0: " +
                            "свободный лимит на сегодня, защищённый резерв на базовые нужды, остаток после обязательств до зарплаты, " +
                            "список конкретных будущих обязательств (аренда, ЖКХ, кредиты) и плановых доходов (зарплата, аванс), " +
                            "защитный резерв и риск кассового разрыва."
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
                    put("name", TOOL_RECURRING_SCHEDULE)
                    put(
                        "description",
                        "Получить расписание активных регулярных доходов (зарплата, аванс и др.) и регулярных расходов/обязательств " +
                            "(аренда, ЖКХ, подписки, кредиты): названия, точные суммы, даты следующих поступлений и списаний, " +
                            "количество дней до события, периодичность, статус достоверности дохода (GUARANTEED / EXPECTED / HYPOTHETICAL), " +
                            "приоритет обязательства (MANDATORY / DISCRETIONARY_SUBSCRIPTION) и статус исполнения (PENDING / OVERDUE / PAID / SKIPPED / RESCHEDULED)."
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put(
                                        "horizonDays",
                                        JSONObject().apply {
                                            put("type", "INTEGER")
                                            put("description", "Горизонт расписания в днях вперёд (от 1 до 90, по умолчанию 45)")
                                        }
                                    )
                                    put(
                                        "filterType",
                                        JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Фильтр по типу: ALL (все), INCOME (только доходы/зарплата) или EXPENSE (только расходы/обязательства)")
                                        }
                                    )
                                    put(
                                        "searchQuery",
                                        JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Необязательный фильтр по названию или категории (например, 'зарплата', 'аренда', 'ЖКХ')")
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
                    put("name", TOOL_SIMULATE_WHAT_IF)
                    put(
                        "description",
                        "Выполнить детерминированную What-If симуляцию без изменения БД: " +
                            "'Что будет, если я потрачу X рублей?' (HYPOTHETICAL_SPEND), " +
                            "'Хватит ли до зарплаты?' (RUNWAY_CHECK), " +
                            "'Что изменится, если зарплата задержится на N дней?' (INCOME_DELAY)."
                    )
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put(
                                        "scenarioType",
                                        JSONObject().apply {
                                            put("type", "STRING")
                                            put("description", "Тип сценария: HYPOTHETICAL_SPEND, RUNWAY_CHECK или INCOME_DELAY")
                                        }
                                    )
                                    put(
                                        "amountRub",
                                        JSONObject().apply {
                                            put("type", "NUMBER")
                                            put("description", "Сумма гипотетической покупки в рублях (для HYPOTHETICAL_SPEND)")
                                        }
                                    )
                                    put(
                                        "delayDays",
                                        JSONObject().apply {
                                            put("type", "INTEGER")
                                            put("description", "На сколько дней задержится доход/зарплата (для INCOME_DELAY)")
                                        }
                                    )
                                    put(
                                        "plannedDailyDiscretionaryRub",
                                        JSONObject().apply {
                                            put("type", "NUMBER")
                                            put("description", "Планируемые ежедневные необязательные траты в рублях (для RUNWAY_CHECK)")
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
     * Transparently reports `error` or `data_unavailable` without fabricating `0 ₽` as a confirmed fact.
     */
    suspend fun executeToolCall(toolName: String, args: JSONObject?): JSONObject {
        val result = try {
            when (toolName) {
                TOOL_FINANCIAL_OVERVIEW -> queryFinancialOverview()
                TOOL_SAFE_TO_SPEND_FORECAST -> querySafeToSpendForecast()
                TOOL_RECURRING_SCHEDULE -> queryRecurringSchedule(args)
                TOOL_SIMULATE_WHAT_IF -> querySimulateWhatIf(args)
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
                    put("dataAvailable", false)
                    put("message", "Инструмент $toolName не поддерживается или запрещён в режиме только для чтения.")
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            GlobalConsoleLogger.log(
                "WARN",
                "DavidLiveTools",
                "Read-only tool '$toolName' failed (${e::class.java.simpleName})"
            )
            return JSONObject().apply {
                put("status", "error")
                put("dataAvailable", false)
                put("hasData", false)
                put("errorCode", "DAO_READ_ERROR")
                put("budgetId", budgetId)
                put(
                    "humanSummary",
                    "Не удалось прочитать финансовые данные из локальной базы ($toolName). " +
                        "Сообщи пользователю, что данные временно недоступны, и не называй вымышленные суммы."
                )
            }
        }

        val status = result.optString("status", "ok")
        val dataAvailable = result.optBoolean("dataAvailable", status == "ok")
        if (status == "ok" && dataAvailable) {
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
        }
        return result
    }

    /**
     * Deterministically resolves which read-only tools must be invoked for a user's Russian query,
     * including multi-tool combinations (e.g., «сколько останется после аренды до зарплаты» ->
     * `get_recurring_schedule` + `get_safe_to_spend_forecast`).
     */
    fun resolveRequiredToolsForQuery(userQuery: String): List<ToolCallSpec> {
        val lower = userQuery.trim().lowercase(Locale("ru", "RU")).replace("ё", "е")
        if (lower.isEmpty()) return listOf(ToolCallSpec(TOOL_FINANCIAL_OVERVIEW))

        val specs = LinkedHashMap<String, ToolCallSpec>()

        val mentionsRecurringIncome = lower.contains("зарплат") ||
            lower.contains("зп") ||
            lower.contains("аванс") ||
            lower.contains("стипенди") ||
            lower.contains("пенси") ||
            lower.contains("поступлен") ||
            lower.contains("получу") ||
            lower.contains("придет")

        val mentionsRecurringExpense = lower.contains("аренд") ||
            lower.contains("квартплат") ||
            lower.contains("жкх") ||
            lower.contains("коммунал") ||
            lower.contains("подписк") ||
            lower.contains("регулярн") ||
            lower.contains("обязательн") ||
            lower.contains("платеж") ||
            lower.contains("списан") ||
            lower.contains("график") ||
            lower.contains("расписан") ||
            lower.contains("предстоящ") ||
            lower.contains("планов")

        val mentionsSafeToSpend = lower.contains("можно потратить") ||
            lower.contains("безопасно потратить") ||
            lower.contains("свободный лимит") ||
            lower.contains("лимит на сегодня") ||
            lower.contains("дневной лимит") ||
            lower.contains("дневной бюджет") ||
            lower.contains("сколько останется") ||
            lower.contains("останется после") ||
            lower.contains("до зарплат") ||
            lower.contains("до зп") ||
            lower.contains("кассовый разрыв") ||
            lower.contains("защитный резерв") ||
            lower.contains("резерв") ||
            lower.contains("прогноз") ||
            lower.contains("safe-to-spend") ||
            lower.contains("safe to spend")

        val whatIfRequest = WhatIfSimulator.detectWhatIfQuery(userQuery)
        val mentionsWhatIf = whatIfRequest != null ||
            lower.contains("что будет если") ||
            lower.contains("что изменится если") ||
            lower.contains("хватит ли") ||
            lower.contains("доживу ли") ||
            lower.contains("дотяну ли")

        if (mentionsRecurringIncome || mentionsRecurringExpense) {
            val filterType = when {
                mentionsRecurringIncome && !mentionsRecurringExpense && !mentionsSafeToSpend -> "INCOME"
                mentionsRecurringExpense && !mentionsRecurringIncome && !mentionsSafeToSpend -> "EXPENSE"
                else -> "ALL"
            }
            specs[TOOL_RECURRING_SCHEDULE] = ToolCallSpec(
                toolName = TOOL_RECURRING_SCHEDULE,
                args = JSONObject().apply {
                    put("horizonDays", 45)
                    put("filterType", filterType)
                }
            )
        }

        if (mentionsSafeToSpend || (mentionsRecurringIncome && mentionsRecurringExpense)) {
            specs[TOOL_SAFE_TO_SPEND_FORECAST] = ToolCallSpec(TOOL_SAFE_TO_SPEND_FORECAST)
            if (mentionsRecurringIncome || mentionsRecurringExpense) {
                specs.putIfAbsent(
                    TOOL_RECURRING_SCHEDULE,
                    ToolCallSpec(
                        toolName = TOOL_RECURRING_SCHEDULE,
                        args = JSONObject().apply {
                            put("horizonDays", 45)
                            put("filterType", "ALL")
                        }
                    )
                )
            }
        }

        if (mentionsWhatIf) {
            val whatIfArgs = JSONObject()
            when (whatIfRequest) {
                is WhatIfScenarioRequest.IncomeDelay -> {
                    whatIfArgs.put("scenarioType", "INCOME_DELAY")
                    whatIfArgs.put("delayDays", whatIfRequest.delayDays)
                }
                is WhatIfScenarioRequest.RunwayCheck -> {
                    whatIfArgs.put("scenarioType", "RUNWAY_CHECK")
                    whatIfArgs.put("plannedDailyDiscretionaryRub", whatIfRequest.plannedDailyDiscretionaryRub)
                }
                is WhatIfScenarioRequest.HypotheticalSpend -> {
                    whatIfArgs.put("scenarioType", "HYPOTHETICAL_SPEND")
                    whatIfArgs.put("amountRub", whatIfRequest.amountRub)
                }
                null -> {
                    whatIfArgs.put("scenarioType", "RUNWAY_CHECK")
                }
            }
            specs[TOOL_SIMULATE_WHAT_IF] = ToolCallSpec(TOOL_SIMULATE_WHAT_IF, whatIfArgs)
            specs.putIfAbsent(TOOL_SAFE_TO_SPEND_FORECAST, ToolCallSpec(TOOL_SAFE_TO_SPEND_FORECAST))
            if (mentionsRecurringIncome || mentionsRecurringExpense) {
                specs.putIfAbsent(
                    TOOL_RECURRING_SCHEDULE,
                    ToolCallSpec(
                        toolName = TOOL_RECURRING_SCHEDULE,
                        args = JSONObject().apply {
                            put("horizonDays", 45)
                            put("filterType", "ALL")
                        }
                    )
                )
            }
        }

        if (lower.contains("категори") || lower.contains("лимит по") || lower.contains("бюджет на ") || lower.contains("потратил на ")) {
            specs[TOOL_CATEGORY_SPENDING] = ToolCallSpec(TOOL_CATEGORY_SPENDING)
        }

        if (lower.contains("цел") || lower.contains("копилк") || lower.contains("долг") ||
            lower.contains("я должен") || lower.contains("мне должны") || lower.contains("кредит")
        ) {
            specs[TOOL_GOALS_AND_DEBTS] = ToolCallSpec(TOOL_GOALS_AND_DEBTS)
        }

        if (lower.contains("динамик") || lower.contains("сравн") || lower.contains("по сравнению") ||
            lower.contains("прошлый период") || lower.contains("прошлую неделю")
        ) {
            val days = if (lower.contains("недел") || lower.contains("7")) 7 else 30
            specs[TOOL_COMPARE_PERIODS] = ToolCallSpec(
                toolName = TOOL_COMPARE_PERIODS,
                args = JSONObject().apply { put("days", days) }
            )
        }

        if (lower.contains("последние операц") || lower.contains("последние трат") ||
            lower.contains("недавние покуп") || lower.contains("история операц")
        ) {
            specs[TOOL_RECENT_TRANSACTIONS] = ToolCallSpec(
                toolName = TOOL_RECENT_TRANSACTIONS,
                args = JSONObject().apply { put("limit", 5) }
            )
        }

        if (specs.isEmpty() || lower.contains("баланс") || lower.contains("сводк") || lower.contains("обзор") || lower.contains("сколько у меня денег")) {
            specs.putIfAbsent(TOOL_FINANCIAL_OVERVIEW, ToolCallSpec(TOOL_FINANCIAL_OVERVIEW))
        }

        return specs.values.toList()
    }

    /**
     * Executes all required read-only tools for a natural-language query in sequence.
     */
    suspend fun executeToolsForQuery(userQuery: String): List<Pair<String, JSONObject>> {
        val specs = resolveRequiredToolsForQuery(userQuery)
        return specs.map { spec ->
            spec.toolName to executeToolCall(spec.toolName, spec.args)
        }
    }

    internal fun parseTransactionMillis(tx: TransactionEntity): Long {
        if (tx.createdAt > 0L) return tx.createdAt
        return parseIsoDateToMillis(tx.date)
    }

    private fun isAllCoreDataSourcesUnavailable(): Boolean {
        return safeToSpendInputOverrideProvider?.invoke() == null &&
            transactionDao == null &&
            accountDao == null &&
            goalDao == null &&
            categoryDao == null &&
            recurringPaymentDao == null &&
            conversationalUserMessagesProvider().isEmpty() &&
            currentTotalBalanceProvider() == 0.0
    }

    private suspend fun resolveSafeToSpendInputAndResult(): Pair<SafeToSpendInput, SafeToSpendResult> {
        val overrideInput = safeToSpendInputOverrideProvider?.invoke()
        if (overrideInput != null) {
            return overrideInput to SafeToSpendCalculator.calculate(overrideInput)
        }
        val activeProfileId = budgetId
        val allTransactions = transactionDao?.getTransactionsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val accounts = accountDao?.getAccountsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val goals = goalDao?.getGoalsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val categories = categoryDao?.getCategoriesByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val recurring = recurringPaymentDao?.getRecurringPaymentsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val logs = recurringPaymentDao?.getExecutionLogsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()

        val unified = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = allTransactions,
            accounts = accounts,
            goals = goals,
            categories = categories,
            recurringPayments = recurring,
            executionLogs = logs,
            todayEpochDay = todayEpochDayProvider(),
            userSafetyFloorRub = userSafetyFloorProvider(activeProfileId),
            userLockedReserveRub = userLockedReserveProvider(activeProfileId),
            userEssentialDailyNormRub = userEssentialDailyNormProvider(activeProfileId),
            conversationalUserMessages = conversationalUserMessagesProvider(),
            fallbackBalance = currentTotalBalanceProvider()
        )
        return unified.safeToSpendInput to unified.safeToSpendResult
    }

    private fun buildUpcomingObligationsJsonArray(sts: SafeToSpendResult, todayEpochDay: Long): JSONArray {
        return JSONArray().apply {
            sts.upcomingObligations.forEach { item ->
                put(
                    JSONObject().apply {
                        put("sourceId", item.sourceId)
                        put("title", item.title)
                        put("amountRub", item.amountRub)
                        put("dueEpochDay", item.dueEpochDay)
                        put("dueDateIso", LocalDate.ofEpochDay(item.dueEpochDay).toString())
                        put("daysUntilDue", (item.dueEpochDay - todayEpochDay).toInt())
                        put("isOverdue", item.isOverdue)
                        put("isPriorityMandatory", item.isPriorityMandatory)
                        put("priority", if (item.isPriorityMandatory) "MANDATORY" else "DISCRETIONARY_SUBSCRIPTION")
                        put("categoryName", item.categoryName)
                    }
                )
            }
        }
    }

    private fun buildUpcomingIncomesJsonArray(sts: SafeToSpendResult, todayEpochDay: Long): JSONArray {
        return JSONArray().apply {
            sts.upcomingIncomes.forEach { item ->
                put(
                    JSONObject().apply {
                        put("sourceId", item.sourceId)
                        if (item.recurringId != null) put("recurringId", item.recurringId)
                        put("title", item.title)
                        put("amountRub", item.amountRub)
                        put("dueEpochDay", item.dueEpochDay)
                        put("dueDateIso", LocalDate.ofEpochDay(item.dueEpochDay).toString())
                        put("daysUntilDue", (item.dueEpochDay - todayEpochDay).toInt())
                        put("incomeCertainty", item.incomeCertainty.name)
                        put("categoryName", item.categoryName)
                        put("frequency", item.frequency.name)
                        put("isIncludedInCurrentBalance", false)
                    }
                )
            }
        }
    }

    private suspend fun querySafeToSpendForecast(): JSONObject {
        if (isAllCoreDataSourcesUnavailable()) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put(
                    "humanSummary",
                    "Источники финансовых данных для расчёта Safe-to-Spend 2.0 недоступны. Не выдумывай суммы."
                )
            }
        }

        val (input, sts) = resolveSafeToSpendInputAndResult()
        val activeProfileId = budgetId
        val hasAnyRecords = safeToSpendInputOverrideProvider?.invoke() != null ||
            input.availableBalanceRub != 0.0 ||
            input.activeRecurringPayments.isNotEmpty() ||
            input.scheduledDebts.isNotEmpty() ||
            input.todayExpenses.isNotEmpty() ||
            (transactionDao?.getTransactionsByBudgetId(activeProfileId)?.firstOrNull()?.isNotEmpty() == true) ||
            (accountDao?.getAccountsByBudgetId(activeProfileId)?.firstOrNull()?.isNotEmpty() == true)

        val upcomingObligationsJson = buildUpcomingObligationsJsonArray(sts, input.todayEpochDay)
        val upcomingIncomesJson = buildUpcomingIncomesJsonArray(sts, input.todayEpochDay)
        val nextIncomeDateIso = sts.nextIncomeEpochDay?.let { LocalDate.ofEpochDay(it).toString() }

        val summary = if (!hasAnyRecords) {
            "В текущем профиле бюджета ($activeProfileId) пока нет операций, счетов и регулярных платежей. " +
                "Добавьте текущий баланс, регулярные доходы (зарплату) и обязательства (аренду, ЖКХ), чтобы рассчитать точный Safe-to-Spend 2.0."
        } else {
            sts.toStructuredDavidSummary()
        }

        return JSONObject().apply {
            put("status", "ok")
            put("dataAvailable", true)
            put("hasData", hasAnyRecords)
            put("isDatabaseEmpty", !hasAnyRecords)
            put("budgetId", activeProfileId)
            put("canSpendTodayDiscRub", sts.canSpendTodayDiscRub)
            put("canSpendTodayEssRub", sts.canSpendTodayEssRub)
            put("canSpendTodayTotalRub", sts.canSpendTodayTotalRub)
            put("dailyDiscretionarySpeedRub", sts.dailyDiscretionarySpeedRub)
            put("expectedScenarioDailyBudgetRub", sts.expectedScenarioDailyBudgetRub)
            put("dailyEssentialRateRub", sts.dailyEssentialRateRub)
            put("essentialEstimationMethod", sts.essentialEstimationMethod.name)
            put("essentialConfidenceLevel", sts.essentialConfidenceLevel.name)
            put("requiresEssentialBudgetPrompt", sts.requiresEssentialBudgetPrompt)
            put("availableBalanceRub", sts.availableBalanceRub)
            put("lockedGoalsAndReserveRub", sts.lockedGoalsAndReserveRub)
            put("mandatorySafetyFloorRub", sts.mandatorySafetyFloorRub)
            put("isReserveRequirementMet", sts.isReserveRequirementMet)
            put("reserveDeficitRub", sts.reserveDeficitRub)
            put("overdueObligationsRub", sts.overdueObligationsRub)
            put("todayPendingObligationsRub", sts.todayPendingObligationsRub)
            put("reservedFutureObligationsRub", sts.reservedFutureObligationsRub)
            put("obligationsReservedTotalRub", sts.obligationsReservedTotalRub)
            put("netBalanceAfterObligationsBeforeSalaryRub", sts.netBalanceAfterObligationsBeforeSalaryRub)
            put("reservedEssentialLivingHorizonRub", sts.reservedEssentialLivingHorizonRub)
            put("expectedUnconfirmedIncomeHorizonRub", sts.expectedUnconfirmedIncomeHorizonRub)
            put("hypotheticalDebtReceivablesRub", sts.hypotheticalDebtReceivablesRub)
            put("horizonDays", sts.horizonDays)
            if (sts.nextIncomeEpochDay != null) {
                put("nextIncomeEpochDay", sts.nextIncomeEpochDay)
                put("nextIncomeDateIso", nextIncomeDateIso)
            }
            put("bottleneckDayOffset", sts.bottleneckDayOffset)
            put("bottleneckReason", sts.bottleneckReason.name)
            put("hasCashGap", sts.hasCashGap)
            put("cashGapAmountRub", sts.cashGapAmountRub)
            put("upcomingObligationsCount", sts.upcomingObligations.size)
            put("upcomingObligations", upcomingObligationsJson)
            put("upcomingIncomesCount", sts.upcomingIncomes.size)
            put("upcomingIncomes", upcomingIncomesJson)
            put("humanSummary", summary)
        }
    }

    private suspend fun queryRecurringSchedule(args: JSONObject?): JSONObject {
        val overrideInput = safeToSpendInputOverrideProvider?.invoke()
        if (overrideInput == null && recurringPaymentDao == null) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put(
                    "humanSummary",
                    "Источник регулярных платежей и доходов недоступен. Я не могу выдумывать расписание зарплаты или обязательств."
                )
            }
        }

        val activeProfileId = budgetId
        val horizonDays = args?.optInt("horizonDays", 45)?.coerceIn(1, 90) ?: 45
        val filterType = args?.optString("filterType", "ALL")?.trim()?.uppercase(Locale.ROOT) ?: "ALL"
        val searchQuery = args?.optString("searchQuery", "")?.trim()?.takeIf { it.isNotEmpty() }

        val (stsInput, stsResult) = resolveSafeToSpendInputAndResult()
        val d0 = stsInput.todayEpochDay
        val rangeEnd = d0 + horizonDays.toLong()

        val allRules: List<RecurringPaymentDomain> = if (overrideInput != null) {
            overrideInput.activeRecurringPayments
        } else {
            recurringPaymentDao?.getRecurringPaymentsByBudgetId(activeProfileId)
                ?.firstOrNull()
                .orEmpty()
                .map { it.toDomain() }
        }

        val existingTxIds: Set<String> = if (overrideInput != null) {
            overrideInput.todayExpenses.map { it.transactionId }.toSet()
        } else {
            transactionDao?.getTransactionsByBudgetId(activeProfileId)
                ?.firstOrNull()
                .orEmpty()
                .map { it.id }
                .toSet()
        }

        val rawLogs = if (overrideInput != null) {
            overrideInput.executionLogs
        } else {
            recurringPaymentDao?.getExecutionLogsByBudgetId(activeProfileId)
                ?.firstOrNull()
                .orEmpty()
                .map { it.toDomain() }
        }

        val reconciledLogs = rawLogs.map { log ->
            if (!log.linkedTransactionId.isNullOrBlank() && log.linkedTransactionId in existingTxIds && log.status != ExecutionStatus.PAID) {
                log.copy(status = ExecutionStatus.PAID)
            } else {
                log
            }
        }
        val logsByRecurringAndDate = reconciledLogs.associateBy { it.recurringId to it.scheduledEpochDay }

        val activeRules = allRules.filter { it.status == RecurringStatus.ACTIVE }
        val pausedOrArchivedCount = allRules.count { it.status != RecurringStatus.ACTIVE }

        val filteredRules = activeRules.filter { rule ->
            val matchesType = when (filterType) {
                "INCOME" -> rule.isIncome
                "EXPENSE" -> !rule.isIncome
                else -> true
            }
            val matchesSearch = if (searchQuery == null) {
                true
            } else {
                rule.title.contains(searchQuery, ignoreCase = true) ||
                    rule.categoryName.contains(searchQuery, ignoreCase = true) ||
                    rule.subcategoryName.contains(searchQuery, ignoreCase = true)
            }
            matchesType && matchesSearch
        }

        val upcomingIncomesArray = JSONArray()
        val upcomingExpensesArray = JSONArray()
        val overdueExpensesArray = JSONArray()
        val allItemsArray = JSONArray()

        var totalGuaranteedFutureIncomeRub = 0.0
        var totalExpectedFutureIncomeRub = 0.0
        var totalHypotheticalFutureIncomeRub = 0.0
        var totalMandatoryObligationsRub = 0.0
        var totalDiscretionarySubscriptionsRub = 0.0
        var totalOverdueObligationsRub = 0.0
        var excludedOccurrencesCount = 0

        var nextGuaranteedIncomeEpochDay: Long? = null
        var nextGuaranteedIncomeTitle: String? = null
        var nextGuaranteedIncomeAmountRub: Double = 0.0

        val incomeSummaryLines = mutableListOf<String>()
        val expenseSummaryLines = mutableListOf<String>()
        val overdueSummaryLines = mutableListOf<String>()

        for (rule in filteredRules) {
            val effectiveAmount = rule.effectiveReservedAmountRub
            if (effectiveAmount <= 0.0) continue

            // Check explicit overdue logs (< d0) for expense rules
            val ruleOverdueDays = mutableSetOf<Long>()
            if (!rule.isIncome) {
                reconciledLogs
                    .filter {
                        it.recurringId == rule.id &&
                            it.scheduledEpochDay < d0 &&
                            it.linkedTransactionId.isNullOrBlank() &&
                            (it.status == ExecutionStatus.PENDING || it.status == ExecutionStatus.OVERDUE)
                    }
                    .forEach { log ->
                        ruleOverdueDays.add(log.scheduledEpochDay)
                    }
            }

            val startRange = if (rule.isIncome) d0 + 1L else min(rule.nextDueDateEpochDay, d0)
            val generatedOccurrences = RecurringScheduleCalculator.generateOccurrencesInRange(
                payment = rule,
                rangeStartEpochDay = startRange,
                rangeEndEpochDay = rangeEnd
            )

            val activeFutureOccurrences = mutableListOf<Long>()
            for (occDay in generatedOccurrences) {
                val log = logsByRecurringAndDate[rule.id to occDay]
                val isExcluded = log != null && (
                    log.status == ExecutionStatus.PAID ||
                        log.status == ExecutionStatus.SKIPPED ||
                        log.status == ExecutionStatus.RESCHEDULED ||
                        !log.linkedTransactionId.isNullOrBlank()
                    )
                if (isExcluded) {
                    excludedOccurrencesCount++
                    continue
                }
                if (!rule.isIncome && occDay < d0) {
                    ruleOverdueDays.add(occDay)
                } else if ((rule.isIncome && occDay > d0) || (!rule.isIncome && occDay >= d0)) {
                    activeFutureOccurrences.add(occDay)
                }
            }

            // Record overdue occurrences for expense rules
            for (overdueDay in ruleOverdueDays.sorted()) {
                val log = logsByRecurringAndDate[rule.id to overdueDay]
                val overdueAmount = log?.actualAmount ?: effectiveAmount
                totalOverdueObligationsRub += overdueAmount
                val overdueIso = LocalDate.ofEpochDay(overdueDay).toString()
                val overdueJson = JSONObject().apply {
                    put("id", rule.id)
                    put("title", rule.title)
                    put("amountRub", rule.amount)
                    put("effectiveReservedAmountRub", overdueAmount)
                    put("amountType", rule.amountType.name)
                    put("currency", "RUB")
                    put("type", "EXPENSE")
                    put("isIncome", false)
                    put("categoryName", rule.categoryName)
                    put("subcategoryName", rule.subcategoryName)
                    put("frequency", rule.frequency.name)
                    put("nextDueDateEpochDay", overdueDay)
                    put("nextDueDateIso", overdueIso)
                    put("daysUntilNextDue", (overdueDay - d0).toInt())
                    put("priority", rule.priority.name)
                    put("scheduleStatus", rule.status.name)
                    put("executionStatus", ExecutionStatus.OVERDUE.name)
                    put("isOverdue", true)
                    put("isAlreadyIncludedInCurrentBalance", false)
                }
                overdueExpensesArray.put(overdueJson)
                allItemsArray.put(overdueJson)
                overdueSummaryLines.add(
                    "${rule.title}: ${overdueAmount.roundToLong()} ₽ (просрочено с $overdueIso, категория: ${rule.categoryName})"
                )
            }

            if (activeFutureOccurrences.isEmpty()) continue

            val firstNextOcc = activeFutureOccurrences.first()
            val firstLog = logsByRecurringAndDate[rule.id to firstNextOcc]
            val firstAmount = firstLog?.actualAmount ?: effectiveAmount
            val nextIso = LocalDate.ofEpochDay(firstNextOcc).toString()
            val daysUntilNext = (firstNextOcc - d0).toInt()
            val occIsoArray = JSONArray().apply {
                activeFutureOccurrences.forEach { occ -> put(LocalDate.ofEpochDay(occ).toString()) }
            }
            val totalOccurrencesAmountInHorizon = activeFutureOccurrences.sumOf { occ ->
                logsByRecurringAndDate[rule.id to occ]?.actualAmount ?: effectiveAmount
            }

            val itemJson = JSONObject().apply {
                put("id", rule.id)
                put("title", rule.title)
                put("amountRub", rule.amount)
                put("effectiveReservedAmountRub", firstAmount)
                if (rule.estimatedMaxAmount != null) {
                    put("estimatedMaxAmountRub", rule.estimatedMaxAmount)
                }
                put("amountType", rule.amountType.name)
                put("currency", "RUB")
                put("type", if (rule.isIncome) "INCOME" else "EXPENSE")
                put("isIncome", rule.isIncome)
                put("categoryName", rule.categoryName)
                put("subcategoryName", rule.subcategoryName)
                put("frequency", rule.frequency.name)
                if (rule.anchorDayOfMonth != null) put("anchorDayOfMonth", rule.anchorDayOfMonth)
                if (rule.anchorDayOfWeek != null) put("anchorDayOfWeek", rule.anchorDayOfWeek)
                put("nextDueDateEpochDay", firstNextOcc)
                put("nextDueDateIso", nextIso)
                put("daysUntilNextDue", daysUntilNext)
                put("occurrencesCountInHorizon", activeFutureOccurrences.size)
                put("occurrencesInHorizonDates", occIsoArray)
                put("totalAmountInHorizonRub", totalOccurrencesAmountInHorizon)
                put("incomeCertainty", if (rule.isIncome) rule.incomeCertainty.name else "N/A")
                put("priority", if (!rule.isIncome) rule.priority.name else "N/A")
                put("scheduleStatus", rule.status.name)
                put("executionStatus", (firstLog?.status ?: ExecutionStatus.PENDING).name)
                put("isOverdue", false)
                put("isAlreadyIncludedInCurrentBalance", false)
            }

            allItemsArray.put(itemJson)

            if (rule.isIncome) {
                upcomingIncomesArray.put(itemJson)
                when (rule.incomeCertainty) {
                    IncomeCertainty.GUARANTEED -> {
                        totalGuaranteedFutureIncomeRub += totalOccurrencesAmountInHorizon
                        if (nextGuaranteedIncomeEpochDay == null || firstNextOcc < nextGuaranteedIncomeEpochDay!!) {
                            nextGuaranteedIncomeEpochDay = firstNextOcc
                            nextGuaranteedIncomeTitle = rule.title
                            nextGuaranteedIncomeAmountRub = firstAmount
                        }
                    }
                    IncomeCertainty.EXPECTED -> {
                        totalExpectedFutureIncomeRub += totalOccurrencesAmountInHorizon
                    }
                    IncomeCertainty.HYPOTHETICAL -> {
                        totalHypotheticalFutureIncomeRub += totalOccurrencesAmountInHorizon
                    }
                }
                val certaintyRu = when (rule.incomeCertainty) {
                    IncomeCertainty.GUARANTEED -> "гарантированный (GUARANTEED)"
                    IncomeCertainty.EXPECTED -> "ожидаемый неподтверждённый (EXPECTED, не входит в гарантированный базовый лимит)"
                    IncomeCertainty.HYPOTHETICAL -> "гипотетический (HYPOTHETICAL, не входит в прогноз ликвидности)"
                }
                incomeSummaryLines.add(
                    "${rule.title}: ${firstAmount.roundToLong()} ₽, дата: $nextIso (через $daysUntilNext дн.), статус: $certaintyRu"
                )
            } else {
                upcomingExpensesArray.put(itemJson)
                if (rule.priority == ObligationPriority.MANDATORY) {
                    totalMandatoryObligationsRub += totalOccurrencesAmountInHorizon
                } else {
                    totalDiscretionarySubscriptionsRub += totalOccurrencesAmountInHorizon
                }
                val priorityRu = if (rule.priority == ObligationPriority.MANDATORY) {
                    "обязательный платёж (MANDATORY)"
                } else {
                    "подписка/регулярный расход (DISCRETIONARY_SUBSCRIPTION)"
                }
                val whenLabel = if (daysUntilNext == 0) "сегодня ($nextIso)" else "$nextIso (через $daysUntilNext дн.)"
                expenseSummaryLines.add(
                    "${rule.title}: ${firstAmount.roundToLong()} ₽, дата: $whenLabel, тип: $priorityRu, категория: ${rule.categoryName}"
                )
            }
        }

        val hasData = activeRules.isNotEmpty()
        val humanSummary = buildString {
            if (allRules.isEmpty()) {
                append("В профиле бюджета ($activeProfileId) пока нет настроенных регулярных доходов (зарплаты) и регулярных платежей (аренды, ЖКХ, подписок).")
            } else {
                appendLine("=== РАСПИСАНИЕ РЕГУЛЯРНЫХ ДОХОДОВ И ОБЯЗАТЕЛЬСТВ (ГОРИЗОНТ $horizonDays ДН.) ===")
                if (incomeSummaryLines.isEmpty()) {
                    appendLine("• Плановые будущие доходы: активных записей по фильтру не найдено.")
                } else {
                    appendLine("• Плановые будущие доходы (НЕ входят в текущий доступный баланс ${stsResult.availableBalanceRub.roundToLong()} ₽):")
                    incomeSummaryLines.forEach { appendLine("  - $it") }
                }
                if (overdueSummaryLines.isNotEmpty()) {
                    appendLine("• ПРОСРОЧЕННЫЕ обязательства (${totalOverdueObligationsRub.roundToLong()} ₽):")
                    overdueSummaryLines.forEach { appendLine("  - $it") }
                }
                if (expenseSummaryLines.isEmpty()) {
                    appendLine("• Предстоящие регулярные платежи/обязательства: активных записей по фильтру не найдено.")
                } else {
                    appendLine("• Предстоящие регулярные платежи и обязательства:")
                    expenseSummaryLines.forEach { appendLine("  - $it") }
                }
                appendLine("• Сводка с учётом текущего баланса (${stsResult.availableBalanceRub.roundToLong()} ₽):")
                appendLine("  - Зарезервировано под обязательства до ближайшей гарантированной зарплаты (${stsResult.horizonDays} дн.): ${stsResult.obligationsReservedTotalRub.roundToLong()} ₽")
                appendLine("  - Остаток после обязательств до зарплаты (до вычета базовой жизни и резерва): ${stsResult.netBalanceAfterObligationsBeforeSalaryRub.roundToLong()} ₽")
                appendLine("  - Безопасно потратить сегодня (свободный лимит Safe-to-Spend 2.0): ${stsResult.canSpendTodayDiscRub.roundToLong()} ₽ (+ ${stsResult.canSpendTodayEssRub.roundToLong()} ₽ защищено на базовые нужды)")
            }
        }.trim()

        return JSONObject().apply {
            put("status", "ok")
            put("dataAvailable", true)
            put("hasData", hasData)
            put("isDatabaseEmpty", allRules.isEmpty())
            put("budgetId", activeProfileId)
            put("todayEpochDay", d0)
            put("todayIso", LocalDate.ofEpochDay(d0).toString())
            put("horizonDays", horizonDays)
            put("filterType", filterType)
            put("activeRulesCount", activeRules.size)
            put("pausedOrArchivedRulesCount", pausedOrArchivedCount)
            put("excludedExecutedOrSkippedOccurrencesCount", excludedOccurrencesCount)
            put("upcomingIncomesCount", upcomingIncomesArray.length())
            put("upcomingExpensesCount", upcomingExpensesArray.length())
            put("overdueExpensesCount", overdueExpensesArray.length())
            put("upcomingIncomes", upcomingIncomesArray)
            put("upcomingExpenses", upcomingExpensesArray)
            put("overdueExpenses", overdueExpensesArray)
            put("items", allItemsArray)
            put("totalGuaranteedFutureIncomeHorizonRub", totalGuaranteedFutureIncomeRub)
            put("totalExpectedFutureIncomeHorizonRub", totalExpectedFutureIncomeRub)
            put("totalHypotheticalFutureIncomeHorizonRub", totalHypotheticalFutureIncomeRub)
            put("totalMandatoryObligationsHorizonRub", totalMandatoryObligationsRub)
            put("totalDiscretionarySubscriptionsHorizonRub", totalDiscretionarySubscriptionsRub)
            put("totalOverdueObligationsRub", totalOverdueObligationsRub)
            if (nextGuaranteedIncomeEpochDay != null) {
                put("nextGuaranteedIncomeEpochDay", nextGuaranteedIncomeEpochDay)
                put("nextGuaranteedIncomeDateIso", LocalDate.ofEpochDay(nextGuaranteedIncomeEpochDay!!).toString())
                put("nextGuaranteedIncomeTitle", nextGuaranteedIncomeTitle)
                put("nextGuaranteedIncomeAmountRub", nextGuaranteedIncomeAmountRub)
                put("daysUntilNextGuaranteedIncome", (nextGuaranteedIncomeEpochDay!! - d0).toInt())
            }
            put("availableBalanceRub", stsResult.availableBalanceRub)
            put("obligationsReservedUntilSalaryRub", stsResult.obligationsReservedTotalRub)
            put("netBalanceAfterObligationsBeforeSalaryRub", stsResult.netBalanceAfterObligationsBeforeSalaryRub)
            put("canSpendTodayDiscRub", stsResult.canSpendTodayDiscRub)
            put("canSpendTodayEssRub", stsResult.canSpendTodayEssRub)
            put("canSpendTodayTotalRub", stsResult.canSpendTodayTotalRub)
            put("humanSummary", humanSummary)
        }
    }

    private suspend fun querySimulateWhatIf(args: JSONObject?): JSONObject {
        if (isAllCoreDataSourcesUnavailable()) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put(
                    "humanSummary",
                    "Источники финансовых данных для What-If симуляции недоступны. Не выдумывай результаты сценария."
                )
            }
        }

        val (baseInput, _) = resolveSafeToSpendInputAndResult()
        val rawType = args?.optString("scenarioType", "HYPOTHETICAL_SPEND")?.uppercase(Locale.ROOT)
            ?: "HYPOTHETICAL_SPEND"
        val request = when (rawType) {
            "INCOME_DELAY" -> {
                val delayDays = args?.optInt("delayDays", 3)?.coerceAtLeast(1) ?: 3
                val targetId = args?.optLong("targetIncomeId", -1L)?.takeIf { it > 0L }
                WhatIfScenarioRequest.IncomeDelay(delayDays = delayDays, targetIncomeId = targetId)
            }
            "RUNWAY_CHECK" -> {
                val plannedDaily = args?.optDouble("plannedDailyDiscretionaryRub", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
                val targetHorizon = args?.optInt("targetHorizonDays", -1)?.takeIf { it > 0 }
                WhatIfScenarioRequest.RunwayCheck(
                    plannedDailyDiscretionaryRub = plannedDaily,
                    targetHorizonDays = targetHorizon
                )
            }
            else -> {
                val amount = args?.optDouble("amountRub", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
                WhatIfScenarioRequest.HypotheticalSpend(amountRub = amount)
            }
        }

        val sim = DavidFinancialIntelligenceProvider.simulateWhatIf(baseInput, request)
        val exposedArray = JSONArray()
        sim.newlyExposedObligations.forEach { item ->
            exposedArray.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("amountRub", item.amountRub)
                    put("dueEpochDay", item.dueEpochDay)
                    put("dueDateIso", LocalDate.ofEpochDay(item.dueEpochDay).toString())
                }
            )
        }

        val upcomingObligationsJson = buildUpcomingObligationsJsonArray(sim.simulated, baseInput.todayEpochDay)
        val upcomingIncomesJson = buildUpcomingIncomesJsonArray(sim.simulated, baseInput.todayEpochDay)

        return JSONObject().apply {
            put("status", "ok")
            put("dataAvailable", true)
            put("budgetId", budgetId)
            put("scenarioType", rawType)
            put("baselineHorizonDays", sim.baseline.horizonDays)
            put("simulatedHorizonDays", sim.simulated.horizonDays)
            if (sim.baseline.nextIncomeEpochDay != null) {
                put("baselineNextIncomeDateIso", LocalDate.ofEpochDay(sim.baseline.nextIncomeEpochDay).toString())
            }
            if (sim.simulated.nextIncomeEpochDay != null) {
                put("simulatedNextIncomeDateIso", LocalDate.ofEpochDay(sim.simulated.nextIncomeEpochDay).toString())
            }
            put("baselineCanSpendTodayRub", String.format(Locale.US, "%.2f", sim.baseline.canSpendTodayDiscRub).toDouble())
            put("simulatedCanSpendTodayRub", String.format(Locale.US, "%.2f", sim.simulated.canSpendTodayDiscRub).toDouble())
            put("deltaDailyBudgetRub", String.format(Locale.US, "%.2f", sim.deltaCanSpendTodayDiscRub).toDouble())
            put("simulatedFutureDailyRub", String.format(Locale.US, "%.2f", sim.simulated.dailyDiscretionarySpeedRub).toDouble())
            put("deltaFutureDailyRub", String.format(Locale.US, "%.2f", sim.deltaDailyDiscretionarySpeedRub).toDouble())
            put("netBalanceAfterObligationsBeforeSalaryRub", sim.simulated.netBalanceAfterObligationsBeforeSalaryRub)
            put("newlyExposedObligationsCount", sim.newlyExposedObligations.size)
            put("newlyExposedObligations", exposedArray)
            put("upcomingObligations", upcomingObligationsJson)
            put("upcomingIncomes", upcomingIncomesJson)
            put("isCashGap", sim.simulated.hasCashGap)
            put("isReserveDeficit", !sim.simulated.isReserveRequirementMet)
            put("projectedBalanceAtSalaryEveRub", sim.projectedBalanceAtSalaryEveRub)
            put("surplusAboveReserveRub", sim.surplusAboveReserveRub)
            put("autonomousSurvivalDaysAtZeroDisc", sim.autonomousSurvivalDaysAtZeroDisc)
            put("verdict", sim.verdict.name)
            put("humanSummary", sim.structuredSummaryForDavid)
        }
    }

    private suspend fun queryFinancialOverview(): JSONObject {
        if (isAllCoreDataSourcesUnavailable()) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put(
                    "humanSummary",
                    "Финансовые данные недоступны (источник базы данных не подключён). Я не могу назвать баланс или доходы без реальных данных."
                )
            }
        }

        val activeProfileId = budgetId
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24L * 3600L * 1000L
        val allTransactions = transactionDao?.getTransactionsByBudgetId(activeProfileId)
            ?.firstOrNull()
            .orEmpty()
        val transactions = allTransactions.filter { parseTransactionMillis(it) >= thirtyDaysAgo }
        val accounts = accountDao?.getAccountsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val recurring = recurringPaymentDao?.getRecurringPaymentsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()

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
        val (stsInput, sts) = resolveSafeToSpendInputAndResult()

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

        val hasData = allTransactions.isNotEmpty() ||
            accounts.isNotEmpty() ||
            recurring.isNotEmpty() ||
            stsInput.activeRecurringPayments.isNotEmpty() ||
            availableBalance != 0.0 ||
            conversationalUserMessagesProvider().isNotEmpty()

        val upcomingObligationsJson = buildUpcomingObligationsJsonArray(sts, stsInput.todayEpochDay)
        val upcomingIncomesJson = buildUpcomingIncomesJsonArray(sts, stsInput.todayEpochDay)
        val nextIncomeDateIso = sts.nextIncomeEpochDay?.let { LocalDate.ofEpochDay(it).toString() }

        val summary = if (!hasData) {
            "В текущем профиле бюджета ($activeProfileId) база данных пуста: операций, счетов и регулярных платежей пока нет."
        } else {
            buildString {
                append("Доступный остаток (баланс): ${availableBalance.roundToLong()} ₽ (НЕ суммировать с зарплатой и долгами), ")
                append("Можно потратить сегодня (свободный лимит Safe-to-Spend 2.0): ${sts.canSpendTodayDiscRub.roundToLong()} ₽ (+ ${sts.canSpendTodayEssRub.roundToLong()} ₽ защищено на базовые нужды), ")
                append("Остаток после обязательств до зарплаты: ${sts.netBalanceAfterObligationsBeforeSalaryRub.roundToLong()} ₽, ")
                append("Заработанный доход (получено за 30 дн.): ${earnedIncome.roundToLong()} ₽ (в т.ч. зарплата: ${snapshot.salaryIncomeRub.roundToLong()} ₽), ")
                if (snapshot.debtReturnedIncomeRub > 0.0) {
                    append("Возврат долгов мне (30 дн.): ${snapshot.debtReturnedIncomeRub.roundToLong()} ₽ (не является доходом), ")
                }
                append("Расходы (30 дн.): ${totalExpense.roundToLong()} ₽, ")
                append("Я должен (обязательства): ${snapshot.totalWeOweRub.roundToLong()} ₽, ")
                append("Мне должны (дебиторская задолженность): ${snapshot.totalOwesUsRub.roundToLong()} ₽, ")
                if (sts.upcomingObligations.isNotEmpty()) {
                    val obls = sts.upcomingObligations.take(3).joinToString("; ") {
                        "${it.title}: ${it.amountRub.roundToLong()} ₽ (${LocalDate.ofEpochDay(it.dueEpochDay)})"
                    }
                    append("Ближайшие обязательства в горизонте ${sts.horizonDays} дн. (${sts.obligationsReservedTotalRub.roundToLong()} ₽): $obls, ")
                }
                if (sts.upcomingIncomes.isNotEmpty()) {
                    val incs = sts.upcomingIncomes.take(2).joinToString("; ") {
                        "${it.title}: ${it.amountRub.roundToLong()} ₽ (${LocalDate.ofEpochDay(it.dueEpochDay)}, ${it.incomeCertainty})"
                    }
                    append("Плановые будущие доходы (не в балансе): $incs, ")
                }
                append("Средний расход в день: ${dailyBurnRate.roundToLong()} ₽/день, ")
                append("Прогноз остатка к концу месяца: ${projectedEndOfMonthBalance.roundToLong()} ₽.")
            }
        }

        return JSONObject().apply {
            put("status", "ok")
            put("dataAvailable", true)
            put("hasData", hasData)
            put("isDatabaseEmpty", !hasData)
            put("budgetId", activeProfileId)
            put("totalBalanceRub", availableBalance.roundToLong())
            put("availableBalanceRub", availableBalance.roundToLong())
            put("canSpendTodayDiscRub", sts.canSpendTodayDiscRub)
            put("canSpendTodayEssRub", sts.canSpendTodayEssRub)
            put("canSpendTodayTotalRub", sts.canSpendTodayTotalRub)
            put("safeToSpendHorizonDays", sts.horizonDays)
            put("reservedFutureObligationsRub", sts.reservedFutureObligationsRub)
            put("overdueObligationsRub", sts.overdueObligationsRub)
            put("todayPendingObligationsRub", sts.todayPendingObligationsRub)
            put("obligationsReservedTotalRub", sts.obligationsReservedTotalRub)
            put("netBalanceAfterObligationsBeforeSalaryRub", sts.netBalanceAfterObligationsBeforeSalaryRub)
            put("mandatorySafetyFloorRub", sts.mandatorySafetyFloorRub)
            if (nextIncomeDateIso != null) {
                put("nextIncomeDateIso", nextIncomeDateIso)
            }
            put("upcomingObligationsCount", sts.upcomingObligations.size)
            put("upcomingObligations", upcomingObligationsJson)
            put("upcomingIncomesCount", sts.upcomingIncomes.size)
            put("upcomingIncomes", upcomingIncomesJson)
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
        if (transactionDao == null && categoryDao == null) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put("categories", JSONArray())
                put("humanSummary", "Источник данных по категориям и расходам недоступен. Не выдумывай суммы.")
            }
        }

        val activeProfileId = budgetId
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24L * 3600L * 1000L
        val allTransactions = transactionDao?.getTransactionsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val transactions = allTransactions.filter { parseTransactionMillis(it) >= thirtyDaysAgo }
        val categories = categoryDao?.getCategoriesByBudgetId(activeProfileId)?.firstOrNull().orEmpty()

        val expensesByCat = transactions
            .filter {
                it.type.equals("expense", ignoreCase = true) &&
                    !DavidFinancialIntelligenceProvider.isInternalTransferTransaction(it) &&
                    !FinancialSemanticsCalculator.isDebtTransaction(it)
            }
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

        val isDatabaseEmpty = allTransactions.isEmpty() && categories.isEmpty()
        val humanSummary = if (summaryParts.isEmpty()) {
            "По выбранным категориям расходов за последние 30 дней не найдено."
        } else {
            "Расходы по категориям: " + summaryParts.take(6).joinToString("; ")
        }

        return JSONObject().apply {
            put("status", "ok")
            put("dataAvailable", true)
            put("hasData", categoryArray.length() > 0)
            put("isDatabaseEmpty", isDatabaseEmpty)
            put("budgetId", activeProfileId)
            put("categories", categoryArray)
            put("humanSummary", humanSummary)
        }
    }

    private suspend fun queryGoalsAndDebts(): JSONObject {
        if (goalDao == null && accountDao == null && transactionDao == null && conversationalUserMessagesProvider().isEmpty()) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put("humanSummary", "Источник данных по целям и долгам недоступен. Не выдумывай суммы.")
            }
        }

        val activeProfileId = budgetId
        val goals = goalDao?.getGoalsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val accounts = accountDao?.getAccountsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()
        val allTransactions = transactionDao?.getTransactionsByBudgetId(activeProfileId)?.firstOrNull().orEmpty()

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

        val hasData = goals.isNotEmpty() || snapshot.weOweDebts.isNotEmpty() || snapshot.owesUsDebts.isNotEmpty()

        return JSONObject().apply {
            put("status", "ok")
            put("dataAvailable", true)
            put("hasData", hasData)
            put("isDatabaseEmpty", !hasData && allTransactions.isEmpty() && accounts.isEmpty())
            put("budgetId", activeProfileId)
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
        if (transactionDao == null) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put("humanSummary", "Источник данных операций для сравнения периодов недоступен.")
            }
        }

        val activeProfileId = budgetId
        val now = System.currentTimeMillis()
        val periodMs = days.toLong() * 24L * 3600L * 1000L
        val currentStart = now - periodMs
        val previousStart = currentStart - periodMs

        val allTx = transactionDao.getTransactionsByBudgetId(activeProfileId)
            .firstOrNull()
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
            allTx.isEmpty() -> "Операций в базе данных пока нет, сравнение периодов за $days дн. недоступно."
            expenseDiffPercent == null -> "В предыдущем периоде ($days дн.) расходов не было, сейчас: ${currentExpense.roundToLong()} ₽."
            expenseDiffPercent > 0 -> "Расходы за $days дн. выросли на $expenseDiffPercent% (${currentExpense.roundToLong()} ₽ против ${previousExpense.roundToLong()} ₽)."
            expenseDiffPercent < 0 -> "Расходы за $days дн. снизились на ${abs(expenseDiffPercent)}% (${currentExpense.roundToLong()} ₽ против ${previousExpense.roundToLong()} ₽)."
            else -> "Расходы за $days дн. не изменились (${currentExpense.roundToLong()} ₽)."
        }

        return JSONObject().apply {
            put("status", "ok")
            put("dataAvailable", true)
            put("hasData", currentTx.isNotEmpty() || previousTx.isNotEmpty())
            put("isDatabaseEmpty", allTx.isEmpty())
            put("budgetId", activeProfileId)
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
        if (transactionDao == null) {
            return JSONObject().apply {
                put("status", "data_unavailable")
                put("dataAvailable", false)
                put("hasData", false)
                put("isDatabaseEmpty", true)
                put("budgetId", budgetId)
                put("transactions", JSONArray())
                put("humanSummary", "Источник данных последних операций недоступен.")
            }
        }

        val activeProfileId = budgetId
        val allTx = transactionDao.getTransactionsByBudgetId(activeProfileId).firstOrNull().orEmpty()
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
            put("dataAvailable", true)
            put("hasData", recent.isNotEmpty())
            put("isDatabaseEmpty", allTx.isEmpty())
            put("budgetId", activeProfileId)
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
        const val TOOL_SAFE_TO_SPEND_FORECAST = "get_safe_to_spend_forecast"
        const val TOOL_RECURRING_SCHEDULE = "get_recurring_schedule"
        const val TOOL_SIMULATE_WHAT_IF = "simulate_what_if"
        const val TOOL_CATEGORY_SPENDING = "get_category_spending"
        const val TOOL_GOALS_AND_DEBTS = "get_goals_and_debts"
        const val TOOL_COMPARE_PERIODS = "compare_spending_periods"
        const val TOOL_RECENT_TRANSACTIONS = "get_recent_transactions"

        val ALL_REGISTERED_TOOLS = listOf(
            TOOL_FINANCIAL_OVERVIEW,
            TOOL_SAFE_TO_SPEND_FORECAST,
            TOOL_RECURRING_SCHEDULE,
            TOOL_SIMULATE_WHAT_IF,
            TOOL_CATEGORY_SPENDING,
            TOOL_GOALS_AND_DEBTS,
            TOOL_COMPARE_PERIODS,
            TOOL_RECENT_TRANSACTIONS
        )

        /**
         * Потокобезопасный статический парсер дат формата `YYYY-MM-DD` без аллокаций `SimpleDateFormat` в циклах.
         */
        @JvmStatic
        internal fun parseIsoDateToMillis(dateStr: String): Long {
            val trimmed = dateStr.trim()
            if (trimmed.length < 10) return 0L
            val isoPart = if (trimmed.length == 10) trimmed else trimmed.substring(0, 10)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDate.parse(isoPart, DateTimeFormatter.ISO_LOCAL_DATE)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } else {
                    parseYyyyMmDdStrictFallback(isoPart)
                }
            } catch (_: Exception) {
                parseYyyyMmDdStrictFallback(isoPart)
            }
        }

        private fun parseYyyyMmDdStrictFallback(isoPart: String): Long {
            if (isoPart.length != 10 || isoPart[4] != '-' || isoPart[7] != '-') return 0L
            return try {
                val year = isoPart.substring(0, 4).toInt()
                val month = isoPart.substring(5, 7).toInt()
                val day = isoPart.substring(8, 10).toInt()
                if (year !in 1970..2100 || month !in 1..12 || day !in 1..31) return 0L
                Calendar.getInstance().apply {
                    isLenient = false
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } catch (_: Exception) {
                0L
            }
        }
    }
}
