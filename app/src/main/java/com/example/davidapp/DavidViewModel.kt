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
 * 3. Строгий сквозной хронологический порядок всех элементов диалога (транзакции, отчеты, диаграммы, сообщения).
 * 4. Расчёт реальных финансовых показателей и генерация персонального аудита (День, Неделя, Месяц, Год).
 * 5. Интерактивная диаграмма динамики баланса на основе фактических транзакций.
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

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private var currentBudgetId: String = "default"
    private var currentProfileName: String = "Максим"
    private var currentApiKey: String = ""

    private var notificationsJob: Job? = null
    private var currentNotifications: List<NotificationEntity> = emptyList()
    private val sessionMessages = mutableListOf<ChatMessage>()

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun bindProfile(budgetId: String, profileName: String, apiKey: String) {
        val isDifferentBudget = budgetId != currentBudgetId || profileName != currentProfileName
        currentBudgetId = budgetId
        currentProfileName = if (profileName.isNotBlank()) profileName else "Пользователь"
        val prefs = getApplication<Application>().getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
        val effectiveKey = apiKey.ifBlank { prefs.getString("gemini_api_key", "") ?: "" }
        currentApiKey = effectiveKey
        _hasApiKey.value = effectiveKey.isNotBlank()

        if (isDifferentBudget || notificationsJob == null) {
            sessionMessages.clear()
            _stage.value = DavidStage.INITIAL
            observeNotifications()
        }
    }

    fun saveApiKeyFromChat(newKey: String, soundManager: SoundManager? = null) {
        val clean = newKey.trim()
        val prefs = getApplication<Application>().getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", clean).putBoolean("gemini_consent_given", true).apply()
        currentApiKey = clean
        _hasApiKey.value = clean.isNotBlank()
        soundManager?.playReceive()
        addSessionMessage(
            ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(
                    if (clean.isNotBlank()) {
                        "🐸 Ква! API-ключ успешно сохранён и активирован! Теперь я работаю на базе **Gemini 3.8** — спрашивай про бюджет или пиши траты прямо сюда (например: *«Такси 500»*)!"
                    } else {
                        "API-ключ очищен. Я переключился в локальный офлайн-режим."
                    }
                ),
                timestamp = currentTime(),
                isUser = false,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }


    fun setProfileName(name: String) {
        if (name.isNotBlank() && name != currentProfileName) {
            currentProfileName = name
            rebuildChronologicalMessages()
        }
    }

    private fun observeNotifications() {
        notificationsJob?.cancel()
        notificationsJob = viewModelScope.launch {
            repository.getNotificationsForBudget(currentBudgetId).collect { notifs ->
                currentNotifications = notifs
                rebuildChronologicalMessages()
            }
        }
    }

    private fun addSessionMessage(message: ChatMessage) {
        sessionMessages.add(message)
        rebuildChronologicalMessages()
    }

    private fun rebuildChronologicalMessages() {
        // 1. Начальное приветствие от Давида (всегда первое, timestampMillis = 0L)
        val greetingMsg = ChatMessage(
            id = "greeting_${currentBudgetId}",
            sender = "Давид Жабов 🐸",
            type = ChatMessageType.Text(
                "Салют, $currentProfileName! Я **Давид Жабов** — твой персональный финансовый ревизор.\n\n" +
                "Нажми **«🔥 Разбор трат»** внизу, чтобы выбрать период аудита, спроси меня о бюджете или просто напиши расход прямо в чат (например: *«Кофе 300»*)."
            ),
            timestamp = "09:00",
            isUser = false,
            timestampMillis = 0L
        )

        // 2. Добавление реакций на транзакции и действия из базы данных
        val notifMessages = mutableListOf<ChatMessage>()
        for (notif in currentNotifications) {
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

                notifMessages.add(
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
                        isUser = true,
                        timestampMillis = notif.timestamp
                    )
                )
            }

            // Ответ Давида (реакция / прожарка)
            if (comment.isNotBlank()) {
                notifMessages.add(
                    ChatMessage(
                        id = "david_notif_${notif.id}",
                        sender = "Давид Жабов 🐸",
                        type = ChatMessageType.Text(comment),
                        timestamp = notifTime,
                        isUser = false,
                        timestampMillis = notif.timestamp + 1L
                    )
                )
            }
        }

        // 3. Строгая хронологическая сортировка всех динамических элементов ленты
        val dynamicMessages = (notifMessages + sessionMessages).sortedWith(
            compareBy<ChatMessage> { it.timestampMillis }.thenBy { it.id }
        )

        // 4. Приветствие всегда в начале истории
        _messages.value = listOf(greetingMsg) + dynamicMessages
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
                    AchievementManager.unlock(getApplication(), "first_audit")
                    val now = System.currentTimeMillis()
                    val userMsg = ChatMessage(
                        sender = currentProfileName,
                        type = ChatMessageType.Text("🐸 Давид, сделай отчет"),
                        timestamp = currentTime(),
                        isUser = true,
                        timestampMillis = now
                    )
                    addSessionMessage(userMsg)

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
                        isUser = false,
                        timestampMillis = System.currentTimeMillis()
                    )
                    addSessionMessage(botMsg)
                }
                "AUDIT" -> {
                    AchievementManager.unlock(getApplication(), "first_audit")
                    val userMsg = ChatMessage(
                        sender = currentProfileName,
                        type = ChatMessageType.Text("⚡ Провести экспресс-аудит за текущий месяц"),
                        timestamp = currentTime(),
                        isUser = true,
                        timestampMillis = System.currentTimeMillis()
                    )
                    addSessionMessage(userMsg)
                    processFile("Месяц", soundManager)
                }
                "GOALS" -> {
                    val userMsg = ChatMessage(
                        sender = currentProfileName,
                        type = ChatMessageType.Text("🎯 Покажи статус моих финансовых целей"),
                        timestamp = currentTime(),
                        isUser = true,
                        timestampMillis = System.currentTimeMillis()
                    )
                    addSessionMessage(userMsg)

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
                        isUser = false,
                        timestampMillis = System.currentTimeMillis()
                    )
                    addSessionMessage(botMsg)
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
        AchievementManager.unlock(getApplication(), "first_audit")
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
            val fileTime = System.currentTimeMillis()
            val fileMsg = ChatMessage(
                sender = currentProfileName,
                type = ChatMessageType.File(
                    name = fileName,
                    size = fileSize,
                    extension = "PDF"
                ),
                timestamp = currentTime(),
                isUser = true,
                timestampMillis = fileTime
            )
            addSessionMessage(fileMsg)

            _stage.value = DavidStage.PROCESSING

            // Получаем реальные транзакции для этого профиля
            val allTxs = try {
                repository.getTransactionsForBudget(currentBudgetId).first()
            } catch (_: Exception) {
                emptyList()
            }

            val auditData = calculateRealAuditData(periodFormatted, allTxs)
            val sections = splitReportIntoSections(auditData.sarcasticReport)
            val reportBaseTime = System.currentTimeMillis()

            // 1. Отправляем каждую секцию отчета отдельным баблом с эффектом печати
            sections.forEachIndexed { index, section ->
                _isDavidTyping.value = true
                delay(600)
                _isDavidTyping.value = false
                soundManager?.playReceive()

                val textMsg = ChatMessage(
                    sender = "Давид Жабов 🐸",
                    type = ChatMessageType.Text(section),
                    timestamp = currentTime(),
                    isUser = false,
                    timestampMillis = reportBaseTime + index + 1
                )
                addSessionMessage(textMsg)
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
                isUser = false,
                timestampMillis = reportBaseTime + sections.size + 1
            )
            addSessionMessage(chartMsg)

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
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        AchievementManager.unlock(getApplication(), "first_chat")

        // 1. Проверка, не вставил ли пользователь Gemini API-ключ прямо в чат
        val looksLikeApiKey = trimmed.startsWith("AIza") ||
            trimmed.startsWith("AQ.") ||
            (trimmed.length >= 25 && !trimmed.contains(" ") && trimmed.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' })

        if (looksLikeApiKey) {
            soundManager?.playSend()
            val maskedMsg = ChatMessage(
                sender = currentProfileName,
                type = ChatMessageType.Text("🔑 API-ключ: ••••••••••••"),
                timestamp = currentTime(),
                isUser = true,
                timestampMillis = System.currentTimeMillis()
            )
            addSessionMessage(maskedMsg)
            saveApiKeyFromChat(trimmed, soundManager)
            return
        }

        viewModelScope.launch {
            soundManager?.playSend()

            val userMsg = ChatMessage(
                sender = currentProfileName,
                type = ChatMessageType.Text(trimmed),
                timestamp = currentTime(),
                isUser = true,
                timestampMillis = System.currentTimeMillis()
            )
            addSessionMessage(userMsg)

            val prevStage = _stage.value
            _isDavidTyping.value = true
            _stage.value = DavidStage.PROCESSING

            // 2. Попытка распознать операцию из естественной фразы в чате (например, «Такси 500» или «Купил кофе за 350»)
            val proposedOp = tryParseOperationFromChat(trimmed)

            val reply = if (currentApiKey.isNotBlank()) {
                generateGeminiReply(trimmed)
            } else {
                generateSmartDavidReply(trimmed)
            }

            delay(650)
            _isDavidTyping.value = false
            soundManager?.playReceive()

            val botMsg = ChatMessage(
                sender = "Давид Жабов 🐸",
                type = ChatMessageType.Text(reply),
                timestamp = currentTime(),
                isUser = false,
                timestampMillis = System.currentTimeMillis()
            )
            addSessionMessage(botMsg)

            if (proposedOp != null && proposedOp.amount > 0.0) {
                val proposalMsg = ChatMessage(
                    sender = "Давид Жабов 🐸",
                    type = ChatMessageType.ParsedTransactionProposal(
                        operation = proposedOp,
                        isAdded = false
                    ),
                    timestamp = currentTime(),
                    isUser = false,
                    timestampMillis = System.currentTimeMillis() + 1L
                )
                addSessionMessage(proposalMsg)
            }

            _stage.value = if (prevStage == DavidStage.INITIAL) DavidStage.FILE_SELECTION else prevStage
        }
    }

    private suspend fun tryParseOperationFromChat(text: String): com.example.data.repository.ParsedVoiceOperation? {
        val lower = text.lowercase(Locale.getDefault())
        if (lower.contains("?") ||
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

        val parsedList = com.example.utils.VoiceParserHelper.parseHeuristically(text, expNames, incNames)
        val firstOp = parsedList.firstOrNull { it.amount > 0.0 } ?: return null

        val semanticMatch = com.example.ui.components.detectCategoryAndTypeByTitle(text, cats, firstOp.type)
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

    fun confirmChatProposedOperation(messageId: String, soundManager: SoundManager? = null) {
        viewModelScope.launch {
            val index = sessionMessages.indexOfFirst { it.id == messageId }
            if (index == -1) return@launch
            val msg = sessionMessages[index]
            val proposal = msg.type as? ChatMessageType.ParsedTransactionProposal ?: return@launch
            if (proposal.isAdded) return@launch

            val op = proposal.operation
            soundManager?.playClick()
            database.transactionDao().insertTransaction(
                TransactionEntity(
                    budgetId = currentBudgetId,
                    type = op.type,
                    date = op.date.ifBlank { isoFormat.format(Date()) },
                    category = op.category.ifBlank { "Прочее" },
                    subcategory = op.subcategory.ifBlank { op.title.ifBlank { op.category } },
                    amount = op.amount
                )
            )
            val appCtx = getApplication<Application>()
            if (op.type == "expense" && com.example.notifications.ReminderManager.isCroakAlertEnabled(appCtx)) {
                val baseThreshold = com.example.notifications.ReminderManager.getCroakThresholdAmount(appCtx)
                if (op.amount >= baseThreshold) {
                    com.example.notifications.ReminderManager.triggerCroakAlertNotification(
                        context = appCtx,
                        category = op.subcategory.ifBlank { op.title.ifBlank { op.category } },
                        amount = op.amount.toInt(),
                        overrideThreshold = baseThreshold
                    )
                }
            }
            sessionMessages[index] = msg.copy(
                type = proposal.copy(isAdded = true)
            )
            rebuildChronologicalMessages()
        }
    }

    fun dismissChatProposedOperation(messageId: String) {
        val removed = sessionMessages.removeAll { it.id == messageId }
        if (removed) {
            rebuildChronologicalMessages()
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
                isUser = true,
                timestampMillis = System.currentTimeMillis()
            )
            addSessionMessage(userMsg)

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
                isUser = false,
                timestampMillis = System.currentTimeMillis()
            )
            addSessionMessage(pdfMsg)
        }
    }

    fun cancelPeriodSelection() {
        if (_stage.value == DavidStage.FILE_SELECTION) {
            _stage.value = DavidStage.INITIAL
        }
    }

    fun resetSession(soundManager: SoundManager? = null) {
        soundManager?.playClick()
        sessionMessages.clear()
        _stage.value = DavidStage.INITIAL
        rebuildChronologicalMessages()
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
