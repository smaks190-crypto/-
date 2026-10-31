package com.example.davidapp

import com.example.data.db.TransactionEntity
import com.example.data.repository.BudgetRepository
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.components.detectCategoryAndTypeByTitle
import com.example.utils.VoiceParserHelper
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class AuditPeriodData(
    val income: Double,
    val expense: Double,
    val deltaPercent: Double,
    val dataPoints: List<Float>,
    val chartSummary: String,
    val sarcasticReport: String
)

internal object DavidChatAuditSupport {

    suspend fun calculateRealAuditData(
        period: String,
        allTxs: List<TransactionEntity>,
        repository: BudgetRepository,
        currentBudgetId: String,
        currentProfileName: String,
        currentApiKey: String
    ): AuditPeriodData {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val todayStr = isoFormat.format(cal.time)

        val (currentTxs, prevTxs, periodTitle) = when (period) {
            "День" -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterdayStr = isoFormat.format(cal.time)
                Triple(
                    allTxs.filter { it.date == todayStr },
                    allTxs.filter { it.date == yesterdayStr },
                    "за сегодня ($todayStr)"
                )
            }
            "Неделя" -> {
                val weekDates = (0..6).map { offset ->
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_YEAR, -offset)
                    isoFormat.format(c.time)
                }.toSet()
                val prevWeekDates = (7..13).map { offset ->
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_YEAR, -offset)
                    isoFormat.format(c.time)
                }.toSet()
                Triple(
                    allTxs.filter { it.date in weekDates },
                    allTxs.filter { it.date in prevWeekDates },
                    "за последние 7 дней"
                )
            }
            "Месяц" -> {
                val curMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                cal.add(Calendar.MONTH, -1)
                val prevMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
                Triple(
                    allTxs.filter { it.date.startsWith(curMonthPrefix) },
                    allTxs.filter { it.date.startsWith(prevMonthPrefix) },
                    "за текущий месяц"
                )
            }
            "Год" -> {
                val curYearPrefix = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
                val prevYearPrefix = (Calendar.getInstance().get(Calendar.YEAR) - 1).toString()
                Triple(
                    allTxs.filter { it.date.startsWith(curYearPrefix) },
                    allTxs.filter { it.date.startsWith(prevYearPrefix) },
                    "за текущий год"
                )
            }
            else -> Triple(allTxs, emptyList(), "за все время")
        }

        val totalIncome = currentTxs.filter { it.type == "income" }.sumOf { it.amount }
        val totalExpense = currentTxs.filter { it.type == "expense" }.sumOf { it.amount }
        val net = totalIncome - totalExpense

        val prevIncome = prevTxs.filter { it.type == "income" }.sumOf { it.amount }
        val prevExpense = prevTxs.filter { it.type == "expense" }.sumOf { it.amount }
        val prevNet = prevIncome - prevExpense

        val deltaPercent = if (prevIncome + prevExpense > 0) {
            val rawDelta = ((net - prevNet) / (prevIncome + prevExpense).coerceAtLeast(1.0)) * 100.0
            String.format(Locale.US, "%.1f", rawDelta).toDoubleOrNull() ?: 0.0
        } else {
            if (net >= 0) 15.0 else -15.0
        }

        val dataPoints = generateRealDataPoints(period, currentTxs, totalIncome, totalExpense)

        val debts = try { repository.getAccountsForBudget(currentBudgetId).first() } catch (_: Exception) { emptyList() }
        val goals = try { repository.getGoalsForBudget(currentBudgetId).first() } catch (_: Exception) { emptyList() }

        val reportResult = repository.requestAiAudit(
            apiKey = currentApiKey,
            periodName = periodTitle,
            year = cal.get(Calendar.YEAR),
            filteredTransactions = currentTxs,
            previousTransactions = prevTxs,
            activeDebts = debts,
            activeGoals = goals,
            allTransactions = allTxs
        )

        val reportText = reportResult.getOrNull() ?: buildDefaultSarcasticReport(
            currentProfileName = currentProfileName,
            period = period,
            income = totalIncome,
            expense = totalExpense,
            net = net,
            transactions = currentTxs
        )

        val chartSummary = when {
            net >= 0 -> "Профицит периода ($period): +${net.toInt()} ₽. Доходы превышают расходы на ${if (totalIncome > 0) ((net / totalIncome) * 100).toInt() else 100}%."
            else -> "Дефицит периода ($period): ${net.toInt()} ₽. Превышение расходов над доходами!"
        }

        return AuditPeriodData(
            income = totalIncome,
            expense = totalExpense,
            deltaPercent = deltaPercent,
            dataPoints = dataPoints,
            chartSummary = chartSummary,
            sarcasticReport = reportText
        )
    }

    fun generateRealDataPoints(
        period: String,
        txs: List<TransactionEntity>,
        income: Double,
        expense: Double
    ): List<Float> {
        if (txs.isEmpty()) {
            return listOf(20f, 25f, 30f, 40f, 50f, 65f, 75f)
        }

        val sorted = txs.sortedBy { it.date }
        val expenses = sorted.filter { it.type == "expense" }

        val points = mutableListOf<Float>()
        var runningExpense = 0.0
        val maxTarget = (income + expense).coerceAtLeast(1000.0)

        points.add(20f)
        for (tx in expenses.take(8)) {
            runningExpense += tx.amount
            val norm = (20f + (runningExpense / maxTarget * 75f).toFloat()).coerceIn(10f, 95f)
            points.add(norm)
        }

        while (points.size < 6) {
            points.add(points.last() + 5f)
        }

        return points.take(10)
    }

    fun buildDefaultSarcasticReport(
        currentProfileName: String,
        period: String,
        income: Double,
        expense: Double,
        net: Double,
        transactions: List<TransactionEntity>
    ): String {
        val expenses = transactions.filter { it.type == "expense" }
        val topCategory = expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .maxByOrNull { it.value }

        val topCatName = topCategory?.key ?: "Спонтанные покупки"
        val topCatAmount = topCategory?.value?.toInt() ?: 0

        val verdictTitle = if (net >= 0) "📈 УМЕРЕННЫЙ ПРОФИЦИТ" else "🚨 КАССОВЫЙ РАЗРЫВ"

        return """
# Главный Вердикт: $verdictTitle
$currentProfileName, финансовый аудит за период **$period** сформирован! Доходы составили **+${income.toInt()} ₽**, расходы — **-${expense.toInt()} ₽**. Чистый итог: **${if (net >= 0) "+" else ""}${net.toInt()} ₽**.

## Цифры и Динамика
- Совокупный доход: **+${income.toInt()} ₽**
- Совокупный расход: **-${expense.toInt()} ₽**
- Чистый остаток: **${if (net >= 0) "+" else ""}${net.toInt()} ₽**
- Главная статья трат: **$topCatName** ($topCatAmount ₽)

## Прожарка Транжиры 🔥
${if (expenses.isNotEmpty()) "Твоя главная финансовая слабость — это категория «$topCatName», куда улетело аж $topCatAmount ₽! Даже Скупой рыцарь Пушкина прослезился бы от такой щедрости к торговцам. Если продолжить в том же духе, инвестиционный портфель придется собирать из скидочных купонов." else "В этом периоде подозрительно мало расходов. Либо режим жесткой аскезы, либо ты забыл внести чек за вчерашний пир!"}

## Ачивки и Достижения 🏆
🏆 **Спонсор категории «$topCatName»** — инвестировал $topCatAmount ₽ в чужой бизнес
🥇 **${if (net >= 0) "Мастер финансового баланса" else "Герой кредитного лимита"}** — ${if (net >= 0) "удержал бюджет в зеленой зоне!" else "умудрился выйти за рамки доходов!"}

## Рекомендации Жабова 💡
Поставь строгий лимит на категорию «$topCatName» и направь как минимум 20% свободного остатка на пополнение финансовой подушки безопасности!
""".trimIndent()
    }

    fun splitReportIntoSections(report: String): List<String> {
        if (report.isBlank()) return emptyList()
        val lines = report.lines()
        val sections = mutableListOf<String>()
        val currentSection = StringBuilder()

        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("# ") || trimmed.startsWith("## ")) {
                if (currentSection.isNotBlank()) {
                    sections.add(currentSection.toString().trim())
                    currentSection.clear()
                }
            }
            currentSection.appendLine(line)
        }
        if (currentSection.isNotBlank()) {
            sections.add(currentSection.toString().trim())
        }
        return if (sections.isNotEmpty()) sections else listOf(report.trim())
    }

    suspend fun tryParseOperationFromChat(
        text: String,
        repository: BudgetRepository,
        currentBudgetId: String
    ): ParsedVoiceOperation? {
        val lower = text.lowercase(Locale.getDefault())
        if (lower.contains("?") ||
            lower.startsWith("как ") ||
            lower.startsWith("почему") ||
            lower.startsWith("сколько") ||
            lower.startsWith("что ") ||
            lower.startsWith("покажи") ||
            lower.startsWith("сделай") ||
            lower.startsWith("на чем") ||
            lower.startsWith("на чём") ||
            lower.startsWith("мой баланс") ||
            lower.startsWith("жабий аудит")
        ) {
            return null
        }

        val cats = try {
            repository.getCategoriesForBudget(currentBudgetId).first()
        } catch (_: Exception) {
            emptyList()
        }
        val expNames = cats.filter { it.type == "expense" }.map { it.name }.ifEmpty {
            listOf("Продукты", "Кафе и рестораны", "Транспорт", "ЖКХ", "Развлечения", "Покупки", "Здоровье", "Прочее")
        }
        val incNames = cats.filter { it.type == "income" }.map { it.name }.ifEmpty {
            listOf("Зарплата", "Фриланс", "Кэшбэк", "Подарки")
        }

        val parsedList = VoiceParserHelper.parseHeuristically(text, expNames, incNames)
        val firstOp = parsedList.firstOrNull { it.amount > 0.0 } ?: return null

        val semanticMatch = detectCategoryAndTypeByTitle(text, cats, firstOp.type)
        return if (semanticMatch != null) {
            firstOp.copy(
                type = semanticMatch.first,
                category = semanticMatch.second,
                subcategory = firstOp.title.ifBlank { semanticMatch.second }
            )
        } else {
            firstOp.copy(
                subcategory = firstOp.title.ifBlank { firstOp.category }
            )
        }
    }

    fun generateSmartDavidReply(currentProfileName: String, query: String): String {
        val q = query.lowercase(Locale.getDefault())
        return when {
            q.contains("привет") || q.contains("салют") || q.contains("здравствуй") ->
                "Салют, $currentProfileName! Хватит любезностей, давай проверим твои расходы. Нажми **«🐸 Давид, сделай отчет»**!"

            q.contains("отчет") || q.contains("выписк") || q.contains("аудит") || q.contains("трат") ->
                "Готов провести жесткий разбор трат для профиля **$currentProfileName**. Выбери интервал: **День**, **Неделя**, **Месяц** или **Год**!"

            q.contains("цел") || q.contains("накоп") || q.contains("копилк") ->
                "Финансовые цели любят дисциплину. Нажми кнопку **«🎯 Цели»** для статуса накоплений!"

            q.contains("долг") || q.contains("кредит") || q.contains("займ") ->
                "Долги — это аренда чужой свободы. Закрывай их в первую очередь, пока проценты не сожрали твой бюджет!"

            q.contains("кофе") || q.contains("шаурм") || q.contains("еда") || q.contains("ресторан") ->
                "Ага, опять гастрономические слабости! Каждая третья чашка навынос отдаляет тебя от финансовой независимости."

            q.contains("совет") || q.contains("как сэконом") || q.contains("что делать") ->
                "Золотое правило Жабова: сначала отложи 20% дохода на накопительный счет, а уже потом распределяй остаток по категориям."

            else ->
                "Запрос принят к сведению, $currentProfileName! Но лучший способ навести порядок в кошельке — сформировать полный PDF-аудит. Выбирай период выше!"
        }
    }
}
