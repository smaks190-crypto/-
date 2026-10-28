package com.example.davidapp

import com.example.data.db.AccountEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

enum class PermanentChipType(
    val id: String,
    val periodArg: String?
) {
    ANALYZE_DAY("perm_analyze_day", "День"),
    ANALYZE_WEEK("perm_analyze_week", "Неделя"),
    ANALYZE_MONTH("perm_analyze_month", "Месяц"),
    ANALYZE_YEAR("perm_analyze_year", "Год"),
    API_CONFIG("perm_api_config", null)
}

data class PermanentChipItem(
    val type: PermanentChipType,
    val label: String,
    val testTag: String
)

enum class DynamicSuggestionTopic {
    LIMIT_ALERT,
    EXPENSE_GROWTH,
    DEBT_STRATEGY,
    GOAL_ACCELERATION,
    TOP_CATEGORY_SAVE,
    UNUSUAL_EXPENSE,
    RECURRING_LEAK,
    SAVINGS_CAPACITY
}

data class DynamicChatSuggestion(
    val id: String,
    val topic: DynamicSuggestionTopic,
    val topicKey: String,
    val priority: Int,
    val chipLabel: String,
    val userQuestion: String,
    val geminiContextFacts: String,
    val localDetailedAnswer: String
)

data class FinancialDataFingerprint(
    val budgetId: String,
    val todayIso: String,
    val txCount: Int,
    val txTotalHash: Long,
    val latestTxId: String?,
    val goalsCount: Int,
    val goalsHash: Long,
    val categoriesLimitHash: Long,
    val debtsCount: Int,
    val debtsHash: Long
)

/**
 * Контекстно-зависимый генератор быстрых подсказок (чипов) для чата с Давидом Жабовым.
 *
 * Гарантирует:
 * 1. Сохранение 5 обязательных постоянных чипов («Анализ за день», «Анализ за неделю»,
 *    «Анализ за месяц», «Анализ за год», «Сменить API / Активировать API»).
 * 2. Генерацию динамических подсказок ТОЛЬКО на основе реальных данных пользователя
 *    (никаких выдуманных целей, долгов или лимитов, если их нет в БД).
 * 3. Работу без сетевых и Gemini API запросов при формировании списка чипов.
 * 4. Дедупликацию, приоритизацию (макс. 5 динамических чипов) и кэширование по отпечатку данных.
 */
object DavidDynamicSuggestionsEngine {

    const val DEFAULT_MAX_DYNAMIC_CHIPS = 5

    @Volatile
    private var cachedFingerprint: FinancialDataFingerprint? = null

    @Volatile
    private var cachedSuggestions: List<DynamicChatSuggestion> = emptyList()

    fun getPermanentChips(hasApiKey: Boolean): List<PermanentChipItem> {
        return listOf(
            PermanentChipItem(
                type = PermanentChipType.ANALYZE_DAY,
                label = "Анализ за день",
                testTag = "perm_chip_day"
            ),
            PermanentChipItem(
                type = PermanentChipType.ANALYZE_WEEK,
                label = "Анализ за неделю",
                testTag = "perm_chip_week"
            ),
            PermanentChipItem(
                type = PermanentChipType.ANALYZE_MONTH,
                label = "Анализ за месяц",
                testTag = "perm_chip_month"
            ),
            PermanentChipItem(
                type = PermanentChipType.ANALYZE_YEAR,
                label = "Анализ за год",
                testTag = "perm_chip_year"
            ),
            PermanentChipItem(
                type = PermanentChipType.API_CONFIG,
                label = if (hasApiKey) "Сменить API" else "Активировать API",
                testTag = "perm_chip_api"
            )
        )
    }

    fun computeFingerprint(
        budgetId: String,
        todayIso: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        goals: List<GoalEntity>,
        accounts: List<AccountEntity>
    ): FinancialDataFingerprint {
        var txHash = 17L
        transactions.forEach { tx ->
            txHash = txHash * 31L + tx.id.hashCode()
            txHash = txHash * 31L + tx.amount.toLong()
            txHash = txHash * 31L + tx.category.hashCode()
            txHash = txHash * 31L + tx.date.hashCode()
        }

        var goalsHash = 19L
        goals.forEach { g ->
            goalsHash = goalsHash * 31L + g.id.hashCode()
            goalsHash = goalsHash * 31L + g.currentAmount.toLong()
            goalsHash = goalsHash * 31L + g.targetAmount.toLong()
        }

        var catLimitHash = 23L
        categories.forEach { c ->
            catLimitHash = catLimitHash * 31L + c.name.hashCode()
            catLimitHash = catLimitHash * 31L + (c.monthlyLimit?.toLong() ?: -1L)
        }

        val debts = accounts.filter { it.type == "we_owe" || it.type == "owes_us" }
        var debtsHash = 29L
        debts.forEach { d ->
            debtsHash = debtsHash * 31L + d.id.hashCode()
            debtsHash = debtsHash * 31L + d.balance.toLong()
        }

        return FinancialDataFingerprint(
            budgetId = budgetId,
            todayIso = todayIso,
            txCount = transactions.size,
            txTotalHash = txHash,
            latestTxId = transactions.firstOrNull()?.id,
            goalsCount = goals.size,
            goalsHash = goalsHash,
            categoriesLimitHash = catLimitHash,
            debtsCount = debts.size,
            debtsHash = debtsHash
        )
    }

    fun getDynamicSuggestions(
        budgetId: String = "default",
        todayIso: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        goals: List<GoalEntity>,
        accounts: List<AccountEntity>,
        maxCount: Int = DEFAULT_MAX_DYNAMIC_CHIPS
    ): List<DynamicChatSuggestion> {
        val fingerprint = computeFingerprint(
            budgetId = budgetId,
            todayIso = todayIso,
            transactions = transactions,
            categories = categories,
            goals = goals,
            accounts = accounts
        )

        val currentCachedFp = cachedFingerprint
        if (currentCachedFp == fingerprint && maxCount == DEFAULT_MAX_DYNAMIC_CHIPS) {
            return cachedSuggestions
        }

        val computed = buildSuggestionsInternal(
            todayIso = todayIso,
            transactions = transactions,
            categories = categories,
            goals = goals,
            accounts = accounts,
            maxCount = maxCount
        )

        if (maxCount == DEFAULT_MAX_DYNAMIC_CHIPS) {
            cachedFingerprint = fingerprint
            cachedSuggestions = computed
        }
        return computed
    }

    internal fun clearCacheForTesting() {
        cachedFingerprint = null
        cachedSuggestions = emptyList()
    }

    private fun buildSuggestionsInternal(
        todayIso: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        goals: List<GoalEntity>,
        accounts: List<AccountEntity>,
        maxCount: Int
    ): List<DynamicChatSuggestion> {
        val candidates = mutableListOf<DynamicChatSuggestion>()
        val yearMonth = todayIso.take(7) // yyyy-MM
        val prevYearMonth = computePreviousYearMonth(yearMonth)

        val monthTxs = transactions.filter { it.date.startsWith(yearMonth) }
        val prevMonthTxs = transactions.filter { it.date.startsWith(prevYearMonth) }

        val activeExpenses = if (monthTxs.any { it.type == "expense" }) {
            monthTxs.filter { it.type == "expense" }
        } else {
            transactions.filter { it.type == "expense" }
        }

        val monthIncome = monthTxs.filter { it.type == "income" }.sumOf { it.amount }
        val monthExpense = monthTxs.filter { it.type == "expense" }.sumOf { it.amount }
        val prevMonthExpense = prevMonthTxs.filter { it.type == "expense" }.sumOf { it.amount }

        // 1. Проверка превышения или приближения к лимиту категории (ТОЛЬКО если лимиты реально заданы)
        val expenseLimits = categories.filter {
            it.type == "expense" && it.monthlyLimit != null && it.monthlyLimit > 0.0
        }
        if (expenseLimits.isNotEmpty() && activeExpenses.isNotEmpty()) {
            val spendByCat = activeExpenses.groupBy { it.category.trim().lowercase(Locale.ROOT) }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val mostCriticalLimit = expenseLimits.mapNotNull { cat ->
                val spent = spendByCat[cat.name.trim().lowercase(Locale.ROOT)] ?: 0.0
                val limit = cat.monthlyLimit ?: 0.0
                if (limit > 0.0 && spent >= limit * 0.75) {
                    Triple(cat, spent, (spent / limit) * 100.0)
                } else null
            }.maxByOrNull { it.third }

            if (mostCriticalLimit != null) {
                val (cat, spent, pctDouble) = mostCriticalLimit
                val limit = cat.monthlyLimit ?: 1.0
                val pct = pctDouble.roundToInt()
                val isOver = spent > limit
                val diff = kotlin.math.abs(spent - limit)
                val shortCat = shortenTitle(cat.name, 16)

                candidates.add(
                    DynamicChatSuggestion(
                        id = "dyn_limit_${cat.id}",
                        topic = DynamicSuggestionTopic.LIMIT_ALERT,
                        topicKey = "limit_${cat.name.lowercase(Locale.ROOT)}",
                        priority = if (isOver) 100 else 88,
                        chipLabel = if (isOver) {
                            "⚠️ Лимит «$shortCat» пробит ($pct%)"
                        } else {
                            "⚡ Лимит «$shortCat»: уже $pct%"
                        },
                        userQuestion = if (isOver) {
                            "Я превысил лимит по категории «${cat.name}» (${formatRub(spent)} из ${formatRub(limit)}). Как скорректировать бюджет до конца месяца?"
                        } else {
                            "По категории «${cat.name}» уже израсходовано $pct% лимита (${formatRub(spent)} из ${formatRub(limit)}). Как уложиться в остаток?"
                        },
                        geminiContextFacts = "Категория '${cat.name}': месячный лимит ${formatRub(limit)}, потрачено ${formatRub(spent)} ($pct%). " +
                            if (isOver) "Перерасход составляет ${formatRub(diff)}." else "Остаток лимита ${formatRub(diff)}.",
                        localDetailedAnswer = if (isOver) {
                            "🐸 **Тревога в болоте!** По категории **«${cat.name}»** ты потратил **${formatRub(spent)}** при лимите **${formatRub(limit)}** ($pct%, перерасход **+${formatRub(diff)}**).\n\n" +
                                "**План спасения от Давида:**\n" +
                                "1. Заморозь необязательные траты в категории «${cat.name}» минимум на 5 дней.\n" +
                                "2. Компенсируй перерасход **${formatRub(diff)}** за счёт резерва других категорий, чтобы общий баланс месяца не ушёл в минус."
                        } else {
                            "🐸 **Жабий радар предупреждает:** по категории **«${cat.name}»** освоено уже **$pct%** лимита (**${formatRub(spent)}** из **${formatRub(limit)}**).\n\n" +
                                "В запасе осталось **${formatRub(diff)}**. Рекомендую ограничить чеки в этой категории до конца месяца, чтобы сохранить зелёный статус бюджета!"
                        }
                    )
                )
            }
        }

        // 2. Рост расходов относительно прошлого месяца (ТОЛЬКО если есть реальные траты за оба месяца и рост >= 15%)
        if (monthExpense > 0.0 && prevMonthExpense > 0.0) {
            val growthPct = (((monthExpense - prevMonthExpense) / prevMonthExpense) * 100.0).roundToInt()
            if (growthPct >= 15) {
                val topDriver = monthTxs.filter { it.type == "expense" }
                    .groupBy { it.category }
                    .mapValues { e -> e.value.sumOf { it.amount } }
                    .maxByOrNull { it.value }
                val driverInfo = topDriver?.let { "Главный драйвер: «${it.key}» (${formatRub(it.value)})." } ?: ""

                candidates.add(
                    DynamicChatSuggestion(
                        id = "dyn_expense_growth_$yearMonth",
                        topic = DynamicSuggestionTopic.EXPENSE_GROWTH,
                        topicKey = "expense_growth_mom",
                        priority = 95,
                        chipLabel = "📈 Почему выросли расходы (+$growthPct%)?",
                        userQuestion = "Почему у меня выросли расходы в этом месяце на +$growthPct% (${formatRub(monthExpense)} против ${formatRub(prevMonthExpense)})?",
                        geminiContextFacts = "Расходы за $yearMonth составили ${formatRub(monthExpense)}, за прошлый месяц ($prevYearMonth) было ${formatRub(prevMonthExpense)} (рост +$growthPct%). $driverInfo",
                        localDetailedAnswer = "🐸 **Разбор скачка расходов (+$growthPct%):**\n" +
                            "В текущем месяце потрачено **${formatRub(monthExpense)}** против **${formatRub(prevMonthExpense)}** месяцем ранее (разница **+${formatRub(monthExpense - prevMonthExpense)}**).\n\n" +
                            (if (topDriver != null) "Основной удар по кошельку нанесла категория **«${topDriver.key}»** — туда ушло **${formatRub(topDriver.value)}**.\n\n" else "") +
                            "**Вердикт Жабова:** срежь импульсивные покупки в лидерской категории на 20%, чтобы вернуть динамику в норму."
                    )
                )
            }
        }

        // 3. Активные долги и обязательства (ТОЛЬКО если в БД есть реальные записи we_owe или owes_us с остатком > 0)
        val activeDebts = accounts.mapNotNull { acc ->
            if (acc.type != "we_owe" && acc.type != "owes_us") return@mapNotNull null
            val linkedTxs = transactions.filter { it.accountId == acc.id }
            val inc = linkedTxs.filter { it.type == "income" }.sumOf { it.amount }
            val exp = linkedTxs.filter { it.type == "expense" }.sumOf { it.amount }
            val remaining = if (acc.type == "we_owe") {
                acc.balance + inc - exp
            } else {
                acc.balance + exp - inc
            }
            if (remaining > 0.0) Pair(acc, remaining) else null
        }.sortedByDescending { it.second }

        if (activeDebts.isNotEmpty()) {
            val (topDebt, remainingDebt) = activeDebts.first()
            val isWeOwe = topDebt.type == "we_owe"
            val shortDebtName = shortenTitle(topDebt.name, 16)

            candidates.add(
                DynamicChatSuggestion(
                    id = "dyn_debt_${topDebt.id}",
                    topic = DynamicSuggestionTopic.DEBT_STRATEGY,
                    topicKey = "debt_${topDebt.id}",
                    priority = 90,
                    chipLabel = if (isWeOwe) {
                        "💳 Как закрыть долг «$shortDebtName»?"
                    } else {
                        "🤝 Возврат долга «$shortDebtName» (${formatRub(remainingDebt)})"
                    },
                    userQuestion = if (isWeOwe) {
                        "У меня есть обязательство «${topDebt.name}» с остатком ${formatRub(remainingDebt)}. Составь оптимальный план досрочного погашения."
                    } else {
                        "Мне должны по записи «${topDebt.name}» ещё ${formatRub(remainingDebt)}. Как грамотно спланировать возврат этих средств в бюджет?"
                    },
                    geminiContextFacts = "Долговое обязательство '${topDebt.name}' (${if (isWeOwe) "мы должны" else "нам должны"}): начальная сумма ${formatRub(topDebt.balance)}, текущий непогашенный остаток ${formatRub(remainingDebt)}.",
                    localDetailedAnswer = if (isWeOwe) {
                        val comfortableMonthly = (remainingDebt / 4.0).coerceAtLeast(1000.0)
                        "🐸 **Стратегия ликвидации долга «${topDebt.name}»:**\n" +
                            "Текущий остаток долга: **${formatRub(remainingDebt)}**.\n\n" +
                            "1. Если направлять по **${formatRub(comfortableMonthly)}** в месяц (или откладывать по **${formatRub(comfortableMonthly / 4.0)}** в неделю), ты полностью закроешь этот хвост за 4 месяца.\n" +
                            "2. Любой незапланированный доход или кэшбэк сразу отправляй в счёт погашения «${topDebt.name}»."
                    } else {
                        "🐸 **Контроль дебиторки по «${topDebt.name}»:**\n" +
                            "Тебе осталось получить **${formatRub(remainingDebt)}** (из начальных **${formatRub(topDebt.balance)}**).\n\n" +
                            "**Совет Давида:** предложи должнику комфортный график возврата частями (например, по **${formatRub(remainingDebt / 2.0)}**), а после возврата сразу переведи эту сумму в накопления."
                    }
                )
            )
        }

        // 4. Активные финансовые цели (ТОЛЬКО если у пользователя реально созданы незакрытые цели)
        val activeGoals = goals.filter { it.targetAmount > 0.0 && it.currentAmount < it.targetAmount }
            .sortedByDescending { (it.currentAmount / it.targetAmount) }

        if (activeGoals.isNotEmpty()) {
            val topGoal = activeGoals.first()
            val remainingGoal = (topGoal.targetAmount - topGoal.currentAmount).coerceAtLeast(0.0)
            val pct = ((topGoal.currentAmount / topGoal.targetAmount) * 100.0).roundToInt()
            val shortGoalName = shortenTitle(topGoal.name, 16)

            candidates.add(
                DynamicChatSuggestion(
                    id = "dyn_goal_${topGoal.id}",
                    topic = DynamicSuggestionTopic.GOAL_ACCELERATION,
                    topicKey = "goal_${topGoal.id}",
                    priority = 85,
                    chipLabel = "🎯 Как быстрее накопить на «$shortGoalName»?",
                    userQuestion = "Как мне быстрее накопить на цель «${topGoal.name}»? Сейчас накоплено ${formatRub(topGoal.currentAmount)} из ${formatRub(topGoal.targetAmount)} ($pct%).",
                    geminiContextFacts = "Финансовая цель '${topGoal.name}': цель ${formatRub(topGoal.targetAmount)}, уже накоплено ${formatRub(topGoal.currentAmount)} ($pct%), осталось накопить ${formatRub(remainingGoal)}.",
                    localDetailedAnswer = "🐸 **План ускорения цели «${topGoal.name}» ($pct%):**\n" +
                        "Уже в копилке: **${formatRub(topGoal.currentAmount)}** из **${formatRub(topGoal.targetAmount)}**. Осталось собрать: **${formatRub(remainingGoal)}**.\n\n" +
                        "• Чтобы закрыть цель за **3 месяца**, откладывай по **${formatRub(remainingGoal / 3.0)}/мес.** (или **${formatRub(remainingGoal / 90.0)}/день**).\n" +
                        "• Переводи 15% от каждого поступления дохода в «${topGoal.name}» в день получения."
                )
            )
        }

        // 5. Главная категория расходов — на чём можно сэкономить (ТОЛЬКО если есть реальные расходы)
        if (activeExpenses.isNotEmpty()) {
            val totalExp = activeExpenses.sumOf { it.amount }.coerceAtLeast(1.0)
            val topCategoryEntry = activeExpenses
                .groupBy { it.category.ifBlank { "Прочее" } }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .maxByOrNull { it.value }

            if (topCategoryEntry != null && topCategoryEntry.value > 0.0) {
                val catName = topCategoryEntry.key
                val catSum = topCategoryEntry.value
                val sharePct = ((catSum / totalExp) * 100.0).roundToInt()
                val potentialSave = catSum * 0.20
                val shortCat = shortenTitle(catName, 15)

                candidates.add(
                    DynamicChatSuggestion(
                        id = "dyn_top_cat_${catName.hashCode()}",
                        topic = DynamicSuggestionTopic.TOP_CATEGORY_SAVE,
                        topicKey = "top_cat_${catName.lowercase(Locale.ROOT)}",
                        priority = 80,
                        chipLabel = "💡 Как снизить траты на «$shortCat» ($sharePct%)?",
                        userQuestion = "Моя самая затратная категория — «$catName» (${formatRub(catSum)}, $sharePct% всех расходов). На чём здесь можно разумно сэкономить?",
                        geminiContextFacts = "Топ-1 категория расходов пользователя: '$catName' с суммой ${formatRub(catSum)} ($sharePct% от общих трат ${formatRub(totalExp)}). Оптимизация на 20% сохранит ${formatRub(potentialSave)}.",
                        localDetailedAnswer = "🐸 **Аудит главной статьи расходов — «$catName»:**\n" +
                            "На эту категорию ушло **${formatRub(catSum)}** — это **$sharePct%** от всех твоих трат за период!\n\n" +
                            "Если оптимизировать чеки в «$catName» всего на **20%**, ты освободишь **+${formatRub(potentialSave)}** чистыми без потери качества жизни. Установи на неё лимит **${formatRub(catSum * 0.8)}**."
                    )
                )
            }
        }

        // 6. Необычно крупная разовая трата (если транзакций >= 3 и максимальная >= 2.2x от средней)
        if (activeExpenses.size >= 3) {
            val avgExp = activeExpenses.map { it.amount }.average()
            val maxTx = activeExpenses.maxByOrNull { it.amount }
            if (maxTx != null && maxTx.amount >= avgExp * 2.2 && maxTx.amount >= 1000.0) {
                val itemTitle = maxTx.subcategory.ifBlank { maxTx.category }
                val shortItem = shortenTitle(itemTitle, 14)

                candidates.add(
                    DynamicChatSuggestion(
                        id = "dyn_spike_${maxTx.id}",
                        topic = DynamicSuggestionTopic.UNUSUAL_EXPENSE,
                        topicKey = "spike_${itemTitle.lowercase(Locale.ROOT)}",
                        priority = 74,
                        chipLabel = "🔍 Крупная трата: «$shortItem» (${formatRub(maxTx.amount)})",
                        userQuestion = "Оцени влияние крупной покупки «$itemTitle» на ${formatRub(maxTx.amount)} (${maxTx.date}) на мой бюджет.",
                        geminiContextFacts = "Крупнейшая разовая покупка: '$itemTitle' (категория '${maxTx.category}') на сумму ${formatRub(maxTx.amount)} от ${maxTx.date}, что в ${String.format(Locale.US, "%.1f", maxTx.amount / avgExp)} раза выше среднего чека (${formatRub(avgExp)}).",
                        localDetailedAnswer = "🐸 **Разбор крупного чека «$itemTitle» (${formatRub(maxTx.amount)}):**\n" +
                            "Эта операция в **${String.format(Locale.US, "%.1f", maxTx.amount / avgExp)} раза** превышает твой средний расход (**${formatRub(avgExp)}**).\n\n" +
                            "Чтобы такая покупка не выбила месячный баланс из колеи, на ближайшую неделю включи режим умеренных дневных трат."
                    )
                )
            }
        }

        // 7. Частые повторяющиеся мелкие расходы (>= 3 раз за период)
        if (activeExpenses.size >= 3) {
            val frequentGroup = activeExpenses
                .groupBy { it.subcategory.ifBlank { it.category }.trim() }
                .filter { it.key.isNotBlank() && it.value.size >= 3 }
                .maxByOrNull { it.value.size }

            if (frequentGroup != null) {
                val habitName = frequentGroup.key
                val count = frequentGroup.value.size
                val totalHabitSum = frequentGroup.value.sumOf { it.amount }
                val shortHabit = shortenTitle(habitName, 14)

                candidates.add(
                    DynamicChatSuggestion(
                        id = "dyn_habit_${habitName.hashCode()}",
                        topic = DynamicSuggestionTopic.RECURRING_LEAK,
                        topicKey = "habit_${habitName.lowercase(Locale.ROOT)}",
                        priority = 70,
                        chipLabel = "☕ Частые траты: «$shortHabit» ($count раз)",
                        userQuestion = "Я совершил трату «$habitName» уже $count раз(а) на общую сумму ${formatRub(totalHabitSum)}. Как взять эту привычку под контроль?",
                        geminiContextFacts = "Повторяющийся расход '$habitName': совершён $count раз(а) за период на общую сумму ${formatRub(totalHabitSum)} (средний чек ${formatRub(totalHabitSum / count)}).",
                        localDetailedAnswer = "🐸 **Детектор регулярных утечек: «$habitName»**\n" +
                            "Зафиксировано **$count операций** на общую сумму **${formatRub(totalHabitSum)}** (в среднем по **${formatRub(totalHabitSum / count)}**).\n\n" +
                            "Сократив частоту всего в 2 раза, ты сэкономишь **+${formatRub(totalHabitSum / 2.0)}** за месяц и **+${formatRub(totalHabitSum * 6.0)}** за год!"
                    )
                )
            }
        }

        // 8. Сколько можно безопасно отложить в этом месяце (ТОЛЬКО если есть реальный доход > 0)
        if (monthIncome > 0.0) {
            val net = monthIncome - monthExpense
            val recommendedSave = if (net > 0.0) {
                (net * 0.50).coerceAtMost(monthIncome * 0.30).coerceAtLeast(monthIncome * 0.10)
            } else {
                monthIncome * 0.10
            }

            candidates.add(
                DynamicChatSuggestion(
                    id = "dyn_savings_capacity_$yearMonth",
                    topic = DynamicSuggestionTopic.SAVINGS_CAPACITY,
                    topicKey = "savings_capacity_$yearMonth",
                    priority = 65,
                    chipLabel = "💰 Сколько отложить (${formatRub(recommendedSave)})?",
                    userQuestion = "При моём доходе ${formatRub(monthIncome)} и расходах ${formatRub(monthExpense)} сколько я могу безопасно отложить в копилку в этом месяце?",
                    geminiContextFacts = "За $yearMonth доход составил ${formatRub(monthIncome)}, расход ${formatRub(monthExpense)}, чистый остаток ${formatRub(net)}. Рекомендуемая сумма сбережений: ${formatRub(recommendedSave)}.",
                    localDetailedAnswer = "🐸 **Калькулятор безопасных сбережений от Давида:**\n" +
                        "• Доходы за месяц: **+${formatRub(monthIncome)}**\n" +
                        "• Расходы за месяц: **−${formatRub(monthExpense)}**\n" +
                        "• Свободный баланс: **${formatRub(net)}**\n\n" +
                        "Без ущерба для текущих нужд ты можешь прямо сейчас перевести в копилку **${formatRub(recommendedSave)}** (${((recommendedSave / monthIncome) * 100.0).roundToInt()}% от дохода)."
                )
            )
        }

        // Дедупликация по topicKey и нормализованному тексту чипа + сортировка по приоритету
        val seenKeys = mutableSetOf<String>()
        val seenLabels = mutableSetOf<String>()

        return candidates
            .sortedByDescending { it.priority }
            .filter { item ->
                val normLabel = item.chipLabel.lowercase(Locale.ROOT).trim()
                val isNew = seenKeys.add(item.topicKey) && seenLabels.add(normLabel)
                isNew
            }
            .take(maxCount.coerceAtLeast(1))
    }

    private fun computePreviousYearMonth(yearMonth: String): String {
        val parts = yearMonth.split("-")
        if (parts.size < 2) return "2026-01"
        val y = parts[0].toIntOrNull() ?: 2026
        val m = parts[1].toIntOrNull() ?: 1
        return if (m > 1) {
            String.format(Locale.US, "%04d-%02d", y, m - 1)
        } else {
            String.format(Locale.US, "%04d-12", y - 1)
        }
    }

    private fun shortenTitle(raw: String, maxLen: Int): String {
        val clean = raw.trim()
        return if (clean.length <= maxLen) clean else clean.take(maxLen - 1).trimEnd() + "…"
    }

    private fun formatRub(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val df = DecimalFormat("#,##0", symbols).apply {
            isGroupingUsed = true
        }
        return "${df.format(amount)}\u00A0₽"
    }
}
