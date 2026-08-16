package com.example.davidapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionEntity
import com.example.data.repository.BudgetRepository
import com.example.ui.components.extractOpsAndComment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel для управления состоянием чата и аудита с Давидом Жабовым.
 *
 * Полная интеграция:
 * 1. Привязка к активному профилю бюджета (budgetId, profileName).
 * 2. Синхронизация и отображение реакций Давида на добавленные транзакции (из NotificationEntity).
 * 3. Расчёт реальных финансовых показателей и генерация персонального аудита (День, Неделя, Месяц, Год).
 * 4. Интерактивная диаграмма динамики баланса на основе фактических транзакций.
 */
class DavidViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = BudgetRepository(
        application,
        database.budgetProfileDao(),
        database.transactionDao(),
        database.goalDao(),
        database.categoryDao(),
        database.aiAuditDao(),
        database.accountDao(),
        database.notificationDao(),
        database
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _stage = MutableStateFlow(DavidStage.INITIAL)
    val stage: StateFlow<DavidStage> = _stage.asStateFlow()

    private val _isDavidTyping = MutableStateFlow(false)
    val isDavidTyping: StateFlow<Boolean> = _isDavidTyping.asStateFlow()

    private var currentBudgetId: String = "default"
    private var currentProfileName: String = "Максим"
    private var currentApiKey: String = ""

    private var notificationsJob: Job? = null
    private val sessionMessages = mutableListOf<ChatMessage>()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun bindProfile(budgetId: String, profileName: String, apiKey: String) {
        val isDifferentBudget = budgetId != currentBudgetId || profileName != currentProfileName
        currentBudgetId = budgetId
        currentProfileName = if (profileName.isNotBlank()) profileName else "Пользователь"
        currentApiKey = apiKey

        if (isDifferentBudget || notificationsJob == null) {
            sessionMessages.clear()
            _stage.value = DavidStage.INITIAL
            observeNotifications()
        }
    }

    fun setProfileName(name: String) {
        if (name.isNotBlank() && name != currentProfileName) {
            currentProfileName = name
            rebuildMessages(emptyList())
        }
    }

    private fun observeNotifications() {
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            repository.getNotificationsForBudget(currentBudgetId).collect { notifs ->
                rebuildMessages(notifs)
            }
        }
    }

    private fun rebuildMessages(notifications: List<NotificationEntity>) {
        val result = mutableListOf<ChatMessage>()

        // 1. Начальное приветствие от Давида
        result.add(
            ChatMessage(
                id = "greeting_${currentBudgetId}",
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(
                    "Салют, $currentProfileName! Я **Давид Жабов** — твой персональный финансовый аудитор и безжалостный критик транжирства.\n\n" +
                    "Готов провести жесткий разбор трат, прожарить нелепые расходы и выдать сочные ачивки. Выбери период или нажми **«🐸 Давид, сделай отчет»**!"
                ),
                timestamp = "09:00",
                isUser = false
            )
        )

        // 2. Добавление реакций на транзакции и действия из базы данных
        for (notif in notifications) {
            val (ops, userPhrase, comment) = extractOpsAndComment(notif)
            val notifTime = timeFormat.format(Date(notif.timestamp))

            // Пропускаем простые системные уведомления-приветствия, чтобы не дублировать стартовое приветствие Давида
            if (ops.isEmpty() && userPhrase.isBlank() && (comment.startsWith("Добр") || comment.startsWith("Салют"))) {
                continue
            }

            // Сообщение пользователя (добавленная операция или фраза)
            if (userPhrase.isNotBlank() || ops.isNotEmpty()) {
                val firstOp = ops.firstOrNull()
                val opType = firstOp?.type ?: "expense"
                val opCategory = firstOp?.category ?: "Операция"
                val opSub = firstOp?.subcategory ?: ""
                val opAmount = if (ops.size > 1) ops.sumOf { it.amount } else (firstOp?.amount ?: 0.0)

                val phraseText = if (userPhrase.isNotBlank()) {
                    userPhrase
                } else if (ops.size > 1) {
                    "Добавлено ${ops.size} операций на сумму ${opAmount.toInt()} ₽"
                } else {
                    "$opCategory ($opSub) — ${opAmount.toInt()} ₽"
                }

                result.add(
                    ChatMessage(
                        id = "user_notif_${notif.id}",
                        sender = currentProfileName,
                        type = ChatMessageType.Operation(
                            type = opType,
                            category = opCategory,
                            subcategory = opSub,
                            amount = opAmount,
                            userPhrase = phraseText,
                            isRead = notif.isRead
                        ),
                        timestamp = notifTime,
                        isUser = true
                    )
                )
            }

            // Ответ Давида (реакция / прожарка)
            if (comment.isNotBlank()) {
                result.add(
                    ChatMessage(
                        id = "david_notif_${notif.id}",
                        sender = "Давид Жабов 🐸",
                        type = ChatMessageType.Text(comment),
                        timestamp = notifTime,
                        isUser = false
                    )
                )
            }
        }

        // 3. Добавление сообщений текущей интерактивной сессии (запросы аудита, графики, ответы)
        result.addAll(sessionMessages)

        _messages.value = result
    }

    private fun currentTime(): String = timeFormat.format(Date())

    /**
     * Обработка быстрых действий
     */
    fun handleAction(action: String, soundManager: SoundManager? = null) {
        viewModelScope.launch {
            soundManager?.playClick()
            when (action) {
                "START" -> {
                    val userMsg = ChatMessage(
                        sender = currentProfileName,
                        type = ChatMessageType.Text("🐸 Давид, сделай отчет"),
                        timestamp = currentTime(),
                        isUser = true
                    )
                    sessionMessages.add(userMsg)
                    _messages.value = _messages.value + userMsg

                    _stage.value = DavidStage.PROCESSING
                    _isDavidTyping.value = true
                    delay(500)
                    _isDavidTyping.value = false
                    soundManager?.playReceive()
                    _stage.value = DavidStage.FILE_SELECTION

                    val botMsg = ChatMessage(
                        sender = "Давид Жабов 🐸",
                        type = ChatMessageType.Text(
                            "Отлично, $currentProfileName! Выбери временной интервал для выписки. Я соберу все твои фактические траты и выдам сочный финансовый вердикт."
                        ),
                        timestamp = currentTime(),
                        isUser = false
                    )
                    sessionMessages.add(botMsg)
                    _messages.value = _messages.value + botMsg
                }
                "AUDIT" -> {
                    val userMsg = ChatMessage(
                        sender = currentProfileName,
                        type = ChatMessageType.Text("⚡ Провести экспресс-аудит за текущий месяц"),
                        timestamp = currentTime(),
                        isUser = true
                    )
                    sessionMessages.add(userMsg)
                    _messages.value = _messages.value + userMsg
                    processFile("Месяц", soundManager)
                }
                "GOALS" -> {
                    val userMsg = ChatMessage(
                        sender = currentProfileName,
                        type = ChatMessageType.Text("🎯 Покажи статус моих финансовых целей"),
                        timestamp = currentTime(),
                        isUser = true
                    )
                    sessionMessages.add(userMsg)
                    _messages.value = _messages.value + userMsg

                    _isDavidTyping.value = true
                    delay(600)
                    _isDavidTyping.value = false
                    soundManager?.playReceive()

                    val goalsList = try {
                        repository.getGoalsForBudget(currentBudgetId).first()
                    } catch (_: Exception) { emptyList() }

                    val goalsText = if (goalsList.isNotEmpty()) {
                        "🎯 **Прогресс по финансовым целям ($currentProfileName):**\n\n" +
                        goalsList.joinToString("\n") { g ->
                            val percent = if (g.targetAmount > 0) ((g.currentAmount / g.targetAmount) * 100).toInt() else 0
                            "- 📌 **${g.name}:** $percent% (собрано ${g.currentAmount.toInt()} ₽ из ${g.targetAmount.toInt()} ₽)"
                        } + "\n\n💡 *Совет Жабова:* Регулярные автопополнения приближают цель в 2.5 раза быстрее спонтанных взносов!"
                    } else {
                        "🎯 У тебя пока нет активных целей в профиле **$currentProfileName**. Создай цель на главный экран, чтобы мне было за что тебя хвалить!"
                    }

                    val botMsg = ChatMessage(
                        sender = "Давид Жабов 🐸",
                        type = ChatMessageType.Text(goalsText),
                        timestamp = currentTime(),
                        isUser = false
                    )
                    sessionMessages.add(botMsg)
                    _messages.value = _messages.value + botMsg
                }
                else -> {
                    processFile("Месяц", soundManager)
                }
            }
        }
    }

    /**
     * Обработка выбора периода: рассчитывает реальные данные профиля, формирует отчет и интерактивный график
     */
    fun processFile(period: String, soundManager: SoundManager? = null) {
        viewModelScope.launch {
            soundManager?.playSend()

            val periodFormatted = when (period.lowercase()) {
                "день", "day" -> "День"
                "неделя", "week" -> "Неделя"
                "месяц", "month" -> "Месяц"
                "год", "year" -> "Год"
                else -> period.replaceFirstChar { it.uppercase() }
            }

            val fileName = "Выписка_${currentProfileName}_${periodFormatted}.pdf"
            val fileSize = when (periodFormatted) {
                "День" -> "1.2 MB"
                "Неделя" -> "2.4 MB"
                "Месяц" -> "4.8 MB"
                "Год" -> "12.3 MB"
                else -> "3.1 MB"
            }

            // Добавляем сообщение-запрос с файлом от пользователя
            val fileMsg = ChatMessage(
                sender = currentProfileName,
                type = ChatMessageType.File(
                    name = fileName,
                    size = fileSize,
                    extension = "PDF"
                ),
                timestamp = currentTime(),
                isUser = true
            )
            sessionMessages.add(fileMsg)
            _messages.value = _messages.value + fileMsg

            _stage.value = DavidStage.PROCESSING

            // Получаем реальные транзакции для этого профиля
            val allTxs = try {
                repository.getTransactionsForBudget(currentBudgetId).first()
            } catch (_: Exception) {
                emptyList()
            }

            val auditData = calculateRealAuditData(periodFormatted, allTxs)
            val sections = splitReportIntoSections(auditData.sarcasticReport)

            // 1. Отправляем каждую секцию отчета отдельным баблом с эффектом печати
            for (section in sections) {
                _isDavidTyping.value = true
                delay(600)
                _isDavidTyping.value = false
                soundManager?.playReceive()

                val textMsg = ChatMessage(
                    sender = "Давид Жабов 🐸",
                    type = ChatMessageType.Text(section),
                    timestamp = currentTime(),
                    isUser = false
                )
                sessionMessages.add(textMsg)
                _messages.value = _messages.value + textMsg
                delay(150)
            }

            // 2. В конце отправляем карточку интерактивного графика
            _isDavidTyping.value = true
            delay(700)
            _isDavidTyping.value = false
            soundManager?.playReceive()

            val chartMsg = ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Chart(
                    title = "Динамика баланса: $periodFormatted",
                    summary = auditData.chartSummary,
                    income = auditData.income,
                    expense = auditData.expense,
                    deltaPercent = auditData.deltaPercent,
                    dataPoints = auditData.dataPoints
                ),
                timestamp = currentTime(),
                isUser = false
            )
            sessionMessages.add(chartMsg)
            _messages.value = _messages.value + chartMsg

            _stage.value = DavidStage.FOLLOW_UP
        }
    }

    /**
     * Расчет реальных показателей транзакций для выбранного периода
     */
    private suspend fun calculateRealAuditData(
        period: String,
        allTxs: List<TransactionEntity>
    ): AuditPeriodData {
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

        // Построение точек диаграммы на основе фактических транзакций
        val dataPoints = generateRealDataPoints(period, currentTxs, totalIncome, totalExpense)

        // Генерация отчета через репозиторий с учетом реальных данных и имени профиля
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

    /**
     * Построение точек для диаграммы Безье на основе фактических транзакций
     */
    private fun generateRealDataPoints(
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

    /**
     * Генератор саркастического отчета по умолчанию на основе фактических цифр профиля
     */
    private fun buildDefaultSarcasticReport(
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

    /**
     * Разделение отчета по смысловым блокам/заголовкам Markdown для раздельных баблов
     */
    private fun splitReportIntoSections(report: String): List<String> {
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

    /**
     * Отправка произвольного сообщения от пользователя
     */
    fun sendUserMessage(text: String, soundManager: SoundManager? = null) {
        if (text.isBlank()) return

        viewModelScope.launch {
            soundManager?.playSend()

            val userMsg = ChatMessage(
                sender = currentProfileName,
                type = ChatMessageType.Text(text.trim()),
                timestamp = currentTime(),
                isUser = true
            )
            sessionMessages.add(userMsg)
            _messages.value = _messages.value + userMsg

            val prevStage = _stage.value
            _isDavidTyping.value = true
            _stage.value = DavidStage.PROCESSING

            val reply = if (currentApiKey.isNotBlank()) {
                generateGeminiReply(text.trim())
            } else {
                generateSmartDavidReply(text.trim())
            }

            delay(650)
            _isDavidTyping.value = false
            soundManager?.playReceive()

            val botMsg = ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(reply),
                timestamp = currentTime(),
                isUser = false
            )
            sessionMessages.add(botMsg)
            _messages.value = _messages.value + botMsg

            _stage.value = if (prevStage == DavidStage.INITIAL) DavidStage.FILE_SELECTION else prevStage
        }
    }

    private suspend fun generateGeminiReply(query: String): String {
        return try {
            val recentTxs = repository.getTransactionsForBudget(currentBudgetId).first()
            val debts = repository.getAccountsForBudget(currentBudgetId).first()
            val goals = repository.getGoalsForBudget(currentBudgetId).first()

            val comment = repository.generateDavidComment(
                apiKey = currentApiKey,
                type = "expense",
                category = "Вопрос аудитору",
                subcategory = query,
                amount = 0.0,
                recentTransactions = recentTxs.take(5),
                activeDebts = debts,
                activeGoals = goals,
                extraContext = "Пользователь $currentProfileName задает вопрос в чате: \"$query\". Ответь остроумно и по существу его финансов.",
                allTransactions = recentTxs
            )
            comment.ifBlank { generateSmartDavidReply(query) }
        } catch (_: Exception) {
            generateSmartDavidReply(query)
        }
    }

    private fun generateSmartDavidReply(query: String): String {
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

    fun sendTextMessage(text: String, soundManager: SoundManager? = null) {
        sendUserMessage(text, soundManager)
    }

    /**
     * Экспорт отчета в PDF
     */
    fun exportPdf(soundManager: SoundManager? = null) {
        viewModelScope.launch {
            soundManager?.playClick()

            val userMsg = ChatMessage(
                sender = currentProfileName,
                type = ChatMessageType.Text("📄 Экспортировать аудит в файл PDF"),
                timestamp = currentTime(),
                isUser = true
            )
            sessionMessages.add(userMsg)
            _messages.value = _messages.value + userMsg

            _isDavidTyping.value = true
            delay(500)
            _isDavidTyping.value = false
            soundManager?.playReceive()

            val pdfMsg = ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.File(
                    name = "Финансовый_Аудит_${currentProfileName}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.pdf",
                    size = "3.8 MB",
                    extension = "PDF"
                ),
                timestamp = currentTime(),
                isUser = false
            )
            sessionMessages.add(pdfMsg)
            _messages.value = _messages.value + pdfMsg
        }
    }

    fun resetSession(soundManager: SoundManager? = null) {
        soundManager?.playClick()
        sessionMessages.clear()
        _stage.value = DavidStage.INITIAL
        observeNotifications()
    }
}

private data class AuditPeriodData(
    val income: Double,
    val expense: Double,
    val deltaPercent: Double,
    val dataPoints: List<Float>,
    val chartSummary: String,
    val sarcasticReport: String
)
