package com.example.davidapp

import android.app.Application
import android.media.AudioFocusRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.db.AppDatabase
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionEntity
import com.example.data.repository.BudgetRepository
import com.example.davidapp.live.DavidLiveAudioEngine
import com.example.davidapp.live.DavidLiveConnectionState
import com.example.davidapp.live.DavidLiveDisplayMode
import com.example.davidapp.live.DavidLiveFinancialToolsProvider
import com.example.davidapp.live.DavidLiveSessionController
import com.example.davidapp.live.DavidLiveSpeaker
import com.example.davidapp.live.DavidLiveSummaryGenerator
import com.example.davidapp.live.DavidLiveToolFact
import com.example.davidapp.live.DavidLiveTranscriptTurn
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

    // --- David Live (Голосовой диалог в реальном времени) ---
    val liveFinancialToolsProvider = DavidLiveFinancialToolsProvider(
        budgetId = "default",
        transactionDao = database.transactionDao(),
        categoryDao = database.categoryDao(),
        goalDao = database.goalDao(),
        accountDao = database.accountDao(),
        recurringPaymentDao = database.recurringPaymentDao()
    )

    val liveSessionController = DavidLiveSessionController(
        financialToolsProvider = liveFinancialToolsProvider
    )

    private val liveSummaryGenerator = DavidLiveSummaryGenerator(
        geminiApiService = RetrofitClient.service
    )

    private val _isLiveOverlayVisible = MutableStateFlow(false)
    val isLiveOverlayVisible: StateFlow<Boolean> = _isLiveOverlayVisible.asStateFlow()

    private val _isLiveSessionActive = MutableStateFlow(false)
    val isLiveSessionActive: StateFlow<Boolean> = _isLiveSessionActive.asStateFlow()

    val liveDisplayMode: StateFlow<DavidLiveDisplayMode> = liveSessionController.displayMode

    private val _chatDraftText = MutableStateFlow("")
    val chatDraftText: StateFlow<String> = _chatDraftText.asStateFlow()

    private var activeAssistantAudioFocusRequest: AudioFocusRequest? = null
    private var lastDisplayModeTransitionMs: Long = 0L

    val liveConnectionState: StateFlow<DavidLiveConnectionState> = liveSessionController.connectionState
    val liveSubtitle: StateFlow<String> = liveSessionController.liveSubtitle
    val liveRayMagnitudes: StateFlow<List<Float>> = liveSessionController.audioEngine.rayMagnitudes
    val liveOverallEnergy: StateFlow<Float> = liveSessionController.audioEngine.overallEnergy
    val liveActiveSpeaker: StateFlow<DavidLiveSpeaker> = liveSessionController.audioEngine.activeSpeaker
    val isLiveMicMuted: StateFlow<Boolean> = liveSessionController.isMicMuted
    val liveErrorMessage: StateFlow<String?> = liveSessionController.errorMessage

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
        liveFinancialToolsProvider.setBudgetId(currentBudgetId)
        val prefs = getApplication<Application>().getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
        val effectiveKey = com.example.data.api.GeminiModelConfig.sanitizeApiKey(
            apiKey.ifBlank { prefs.getString("gemini_api_key", "") ?: "" }
        )
        currentApiKey = effectiveKey
        _hasApiKey.value = effectiveKey.isNotBlank()

        if (isDifferentBudget || notificationsJob == null) {
            sessionMessages.clear()
            _stage.value = DavidStage.INITIAL
            observeNotifications()
        }
    }

    fun saveApiKeyFromChat(newKey: String, soundManager: SoundManager? = null) {
        val clean = com.example.data.api.GeminiModelConfig.sanitizeApiKey(newKey)
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

        val debts = try { repository.getAccountsForBudget(currentBudgetId).first() } catch (_: Exception) { emptyList() }
        val goals = try { repository.getGoalsForBudget(currentBudgetId).first() } catch (_: Exception) { emptyList() }

        val semantics = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = allTxs,
            periodTransactions = currentTxs,
            accounts = debts
        )
        val prevSemantics = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = allTxs,
            periodTransactions = prevTxs,
            accounts = debts
        )

        val totalIncome = semantics.earnedIncomeRub
        val totalExpense = semantics.totalExpenseRub
        val net = totalIncome - totalExpense

        val prevIncome = prevSemantics.earnedIncomeRub
        val prevExpense = prevSemantics.totalExpenseRub
        val prevNet = prevIncome - prevExpense

        val deltaPercent = if (prevIncome + prevExpense > 0) {
            val rawDelta = ((net - prevNet) / (prevIncome + prevExpense).coerceAtLeast(1.0)) * 100.0
            String.format(Locale.US, "%.1f", rawDelta).toDoubleOrNull() ?: 0.0
        } else {
            if (net >= 0) 15.0 else -15.0
        }

        // Построение точек диаграммы на основе фактических транзакций
        val dataPoints = generateRealDataPoints(period, currentTxs, totalIncome, totalExpense)

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
            availableBalance = semantics.availableBalanceRub,
            weOweTotal = semantics.totalWeOweRub,
            owesUsTotal = semantics.totalOwesUsRub,
            transactions = currentTxs
        )

        val chartSummary = when {
            net >= 0 -> "Профицит периода ($period): +${net.toInt()} ₽. Заработанный доход: +${totalIncome.toInt()} ₽, доступный баланс: ${semantics.availableBalanceRub.toInt()} ₽."
            else -> "Дефицит периода ($period): ${net.toInt()} ₽. Превышение расходов над заработанным доходом!"
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
        availableBalance: Double,
        weOweTotal: Double,
        owesUsTotal: Double,
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
$currentProfileName, финансовый аудит за период **$period** сформирован! Заработанный доход составил **+${income.toInt()} ₽**, расходы — **-${expense.toInt()} ₽**. Доступный остаток (текущий баланс): **${availableBalance.toInt()} ₽**.

## Цифры и Динамика (раздельный учёт)
- Доступный остаток (текущий баланс на руках): **${availableBalance.toInt()} ₽**
- Заработанный доход за период: **+${income.toInt()} ₽**
- Совокупный расход за период: **-${expense.toInt()} ₽**
- Мои долги (я должен — обязательства, не входят в доход и баланс): **${weOweTotal.toInt()} ₽**
- Мне должны (дебиторская задолженность — не входит в доход и баланс до возврата): **${owesUsTotal.toInt()} ₽**
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
            lower.startsWith("жабий аудит") ||
            com.example.data.safetospend.WhatIfSimulator.detectWhatIfQuery(text) != null
        ) {
            return null
        }

        // Не превращаем упоминания долгов, обязательств, текущего баланса или сводных перечней в одиночную транзакцию
        val mention = com.example.data.repository.FinancialSemanticsCalculator.extractConversationalFinancialMention(text)
        val mentionedCount = listOfNotNull(
            mention.salaryAmount,
            mention.currentBalanceAmount,
            mention.weOweAmount,
            mention.owesUsAmount
        ).size
        if (mentionedCount > 1 ||
            mention.currentBalanceAmount != null ||
            mention.weOweAmount != null ||
            mention.owesUsAmount != null
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
        if (parsedList.size != 1) return null
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

    private suspend fun buildConversationFinancialSnapshot(currentQuery: String): com.example.data.repository.FinancialSemanticsSnapshot {
        val recentTxs = try { repository.getTransactionsForBudget(currentBudgetId).first() } catch (_: Exception) { emptyList() }
        val debts = try { repository.getAccountsForBudget(currentBudgetId).first() } catch (_: Exception) { emptyList() }
        val baseSnapshot = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = recentTxs,
            periodTransactions = recentTxs,
            accounts = debts
        )
        val userMessageTexts = sessionMessages
            .filter { it.isUser }
            .mapNotNull { msg -> (msg.type as? ChatMessageType.Text)?.content }
            .let { list: List<String> ->
                if (list.lastOrNull() == currentQuery) list else list + currentQuery
            }
        return com.example.data.repository.FinancialSemanticsCalculator.mergeSnapshotWithConversationalMentions(
            baseSnapshot = baseSnapshot,
            userMessages = userMessageTexts
        )
    }

    private suspend fun buildUnifiedIntelligenceSnapshot(): com.example.data.safetospend.UnifiedFinancialIntelligenceSnapshot? {
        return try {
            val recentTxs = repository.getTransactionsForBudget(currentBudgetId).first()
            val debts = repository.getAccountsForBudget(currentBudgetId).first()
            val goals = repository.getGoalsForBudget(currentBudgetId).first()
            val cats = repository.getCategoriesForBudget(currentBudgetId).first()
            val recurring = repository.getRecurringPaymentsForBudget(currentBudgetId).first()
            val logs = repository.getExecutionLogsForBudget(currentBudgetId).first()
            val prefs = getApplication<Application>().getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
            val safetyFloor = prefs.getFloat("safety_floor_rub_${currentBudgetId}", 0f).toDouble()
            val lockedReserve = prefs.getFloat("locked_reserve_rub_${currentBudgetId}", 0f).toDouble()
            val userEssNorm = prefs.getFloat("essential_daily_norm_rub_${currentBudgetId}", -1f)
                .takeIf { it > 0f }?.toDouble()

            com.example.data.safetospend.DavidFinancialIntelligenceProvider.computeSnapshot(
                transactions = recentTxs,
                accounts = debts,
                goals = goals,
                categories = cats,
                recurringPayments = recurring,
                executionLogs = logs,
                userSafetyFloorRub = safetyFloor,
                userLockedReserveRub = lockedReserve,
                userEssentialDailyNormRub = userEssNorm
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun generateGeminiReply(query: String): String {
        return try {
            val goals = repository.getGoalsForBudget(currentBudgetId).first()
            val snapshot = buildConversationFinancialSnapshot(query)
            val unified = buildUnifiedIntelligenceSnapshot()
            val whatIfReq = com.example.data.safetospend.WhatIfSimulator.detectWhatIfQuery(query)
            val whatIfResult = if (whatIfReq != null && unified != null) {
                com.example.data.safetospend.DavidFinancialIntelligenceProvider.simulateWhatIf(
                    unified.safeToSpendInput,
                    whatIfReq
                )
            } else {
                null
            }
            val historySnippet = sessionMessages
                .takeLast(8)
                .mapNotNull { msg ->
                    val txt = (msg.type as? ChatMessageType.Text)?.content ?: return@mapNotNull null
                    val role = if (msg.isUser) currentProfileName else "Давид"
                    "$role: $txt"
                }

            val answer = repository.generateDavidChatAnswer(
                apiKey = currentApiKey,
                profileName = currentProfileName,
                userMessage = query,
                snapshot = snapshot,
                activeGoals = goals,
                conversationHistory = historySnippet,
                safeToSpendResult = unified?.safeToSpendResult,
                whatIfResult = whatIfResult
            )
            answer.ifBlank { generateSmartDavidReply(query, snapshot, unified, whatIfResult) }
        } catch (_: Exception) {
            val fallbackSnapshot = try { buildConversationFinancialSnapshot(query) } catch (_: Exception) { null }
            val unified = buildUnifiedIntelligenceSnapshot()
            val whatIfReq = com.example.data.safetospend.WhatIfSimulator.detectWhatIfQuery(query)
            val whatIfResult = if (whatIfReq != null && unified != null) {
                com.example.data.safetospend.DavidFinancialIntelligenceProvider.simulateWhatIf(
                    unified.safeToSpendInput,
                    whatIfReq
                )
            } else {
                null
            }
            generateSmartDavidReply(query, fallbackSnapshot, unified, whatIfResult)
        }
    }

    private suspend fun generateSmartDavidReply(
        query: String,
        precomputedSnapshot: com.example.data.repository.FinancialSemanticsSnapshot? = null,
        precomputedUnified: com.example.data.safetospend.UnifiedFinancialIntelligenceSnapshot? = null,
        precomputedWhatIf: com.example.data.safetospend.WhatIfSimulationResult? = null
    ): String {
        val q = query.lowercase(Locale.getDefault())
        val unified = precomputedUnified ?: buildUnifiedIntelligenceSnapshot()
        val whatIfReq = com.example.data.safetospend.WhatIfSimulator.detectWhatIfQuery(query)
        val whatIfResult = precomputedWhatIf ?: if (whatIfReq != null && unified != null) {
            com.example.data.safetospend.DavidFinancialIntelligenceProvider.simulateWhatIf(
                unified.safeToSpendInput,
                whatIfReq
            )
        } else {
            null
        }

        if (whatIfResult != null) {
            return "🐸 Ква-аудит сценария «Что если» для **$currentProfileName**:\n\n${whatIfResult.structuredSummaryForDavid}"
        }

        val isSafeToSpendQuery =
            q.contains("можно потратить") ||
            q.contains("дневной лимит") ||
            q.contains("лимит на сегодня") ||
            q.contains("бюджет на сегодня") ||
            q.contains("safe to spend") ||
            q.contains("сколько тратить сегодня")

        if (isSafeToSpendQuery && unified != null) {
            return "🐸 Расчёт **Safe-to-Spend 2.0** для профиля **$currentProfileName**:\n\n${unified.safeToSpendResult.toStructuredDavidSummary()}"
        }

        val isFinancialSemanticsQuery =
            q.contains("баланс") ||
            q.contains("остаток") ||
            q.contains("доход") ||
            q.contains("зарплат") ||
            q.contains("зп") ||
            q.contains("долг") ||
            q.contains("должен") ||
            q.contains("должна") ||
            q.contains("должны") ||
            q.contains("кредит") ||
            q.contains("займ") ||
            q.contains("сколько у меня") ||
            q.contains("сколько денег") ||
            q.contains("итог")

        if (isFinancialSemanticsQuery) {
            val snapshot = precomputedSnapshot ?: try {
                buildConversationFinancialSnapshot(query)
            } catch (_: Exception) {
                null
            }
            if (snapshot != null) {
                val breakdown = com.example.data.repository.FinancialSemanticsCalculator.formatChatFinancialBreakdown(snapshot)
                return "$currentProfileName, мухи отдельно, котлеты отдельно! Я никогда не складываю зарплату, текущий баланс и чужие долги в один «общий доход»:\n\n$breakdown"
            }
        }

        return when {
            q.contains("привет") || q.contains("салют") || q.contains("здравствуй") ->
                "Салют, $currentProfileName! Хватит любезностей, давай проверим твои расходы. Нажми **«🐸 Давид, сделай отчет»**!"

            q.contains("отчет") || q.contains("выписк") || q.contains("аудит") || q.contains("трат") ->
                "Готов провести жесткий разбор трат для профиля **$currentProfileName**. Выбери интервал: **День**, **Неделя**, **Месяц** или **Год**!"

            q.contains("цел") || q.contains("накоп") || q.contains("копилк") ->
                "Финансовые цели любят дисциплину. Нажми кнопку **«🎯 Цели»** для статуса накоплений!"

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

    // --- Управление голосовым режимом David Live и пост-резюме разговора ---

    fun updateChatDraftText(text: String) {
        _chatDraftText.value = text
    }

    fun startDavidLive(soundManager: SoundManager? = null) {
        soundManager?.playClick()
        liveFinancialToolsProvider.setBudgetId(currentBudgetId)
        if (liveSessionController.displayMode.value == DavidLiveDisplayMode.MINIMIZED &&
            liveSessionController.isSessionActive()
        ) {
            expandDavidLive()
            return
        }
        if (activeAssistantAudioFocusRequest == null) {
            activeAssistantAudioFocusRequest =
                DavidLiveAudioEngine.requestAssistantAudioFocus(getApplication())
        }
        val prefs = getApplication<Application>().getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
        val userKey = currentApiKey.ifBlank { prefs.getString("gemini_api_key", "") ?: "" }
        liveSessionController.startSession(userKey)
        _isLiveOverlayVisible.value = true
        _isLiveSessionActive.value = true
    }

    fun minimizeDavidLive(soundManager: SoundManager? = null) {
        val now = System.currentTimeMillis()
        if (now - lastDisplayModeTransitionMs < 180L) return
        if (liveSessionController.displayMode.value != DavidLiveDisplayMode.EXPANDED) return
        lastDisplayModeTransitionMs = now
        soundManager?.playClick()
        liveSessionController.minimizeSession()
        _isLiveOverlayVisible.value = false
        _isLiveSessionActive.value = true
    }

    fun expandDavidLive(soundManager: SoundManager? = null) {
        val now = System.currentTimeMillis()
        if (now - lastDisplayModeTransitionMs < 180L) return
        if (liveSessionController.displayMode.value != DavidLiveDisplayMode.MINIMIZED) return
        lastDisplayModeTransitionMs = now
        soundManager?.playClick()
        liveSessionController.expandSession()
        _isLiveOverlayVisible.value = true
        _isLiveSessionActive.value = true
    }

    fun toggleLiveMicMute() {
        liveSessionController.toggleMicMute()
    }

    fun retryLiveConnection() {
        liveSessionController.retryConnection()
    }

    fun endDavidLiveSession(soundManager: SoundManager? = null) {
        soundManager?.playClick()
        val (turns, facts) = liveSessionController.endSession()
        DavidLiveAudioEngine.abandonAssistantAudioFocus(
            getApplication(),
            activeAssistantAudioFocusRequest
        )
        activeAssistantAudioFocusRequest = null
        _isLiveOverlayVisible.value = false
        _isLiveSessionActive.value = false
        processCompletedLiveSession(turns, facts, soundManager)
    }

    internal fun processCompletedLiveSession(
        turns: List<DavidLiveTranscriptTurn>,
        facts: List<DavidLiveToolFact>,
        soundManager: SoundManager? = null
    ) {
        if (!liveSummaryGenerator.hasEnoughSubstanceForSummary(turns, facts)) {
            // Слишком короткий или пустой вызов — завершаем аккуратно без засорения чата резюме
            return
        }

        val summaryMsgId = "live_summary_${System.currentTimeMillis()}"
        val loadingMsg = ChatMessage(
            id = summaryMsgId,
            sender = "Давид Жабов 🐸",
            type = ChatMessageType.LiveSessionSummary(
                summary = null,
                isGenerating = true,
                errorMessage = null,
                capturedTurns = turns,
                capturedFacts = facts
            ),
            timestamp = currentTime(),
            isUser = false,
            timestampMillis = System.currentTimeMillis()
        )
        addSessionMessage(loadingMsg)

        generateAndApplyLiveSummary(summaryMsgId, turns, facts, soundManager)
    }

    fun retryLiveSummary(messageId: String, soundManager: SoundManager? = null) {
        val index = sessionMessages.indexOfFirst { it.id == messageId }
        if (index == -1) return
        val existingMsg = sessionMessages[index]
        val existingType = existingMsg.type as? ChatMessageType.LiveSessionSummary ?: return

        soundManager?.playClick()
        sessionMessages[index] = existingMsg.copy(
            type = existingType.copy(
                isGenerating = true,
                errorMessage = null
            )
        )
        rebuildChronologicalMessages()

        generateAndApplyLiveSummary(
            messageId = messageId,
            turns = existingType.capturedTurns,
            facts = existingType.capturedFacts,
            soundManager = soundManager
        )
    }

    private fun generateAndApplyLiveSummary(
        messageId: String,
        turns: List<DavidLiveTranscriptTurn>,
        facts: List<DavidLiveToolFact>,
        soundManager: SoundManager?
    ) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
            val userKey = currentApiKey.ifBlank { prefs.getString("gemini_api_key", "") ?: "" }

            val outcome = liveSummaryGenerator.generateSummary(
                userApiKey = userKey,
                turns = turns,
                verifiedFacts = facts
            )

            val index = sessionMessages.indexOfFirst { it.id == messageId }
            if (index == -1) return@launch
            val currentMsg = sessionMessages[index]

            when (outcome) {
                is DavidLiveSummaryGenerator.SummaryOutcome.SkippedTooShort -> {
                    sessionMessages.removeAt(index)
                    rebuildChronologicalMessages()
                }
                is DavidLiveSummaryGenerator.SummaryOutcome.Success -> {
                    soundManager?.playReceive()
                    sessionMessages[index] = currentMsg.copy(
                        type = ChatMessageType.LiveSessionSummary(
                            summary = outcome.summary,
                            isGenerating = false,
                            errorMessage = null,
                            capturedTurns = turns,
                            capturedFacts = facts
                        )
                    )
                    rebuildChronologicalMessages()
                }
                is DavidLiveSummaryGenerator.SummaryOutcome.Error -> {
                    sessionMessages[index] = currentMsg.copy(
                        type = ChatMessageType.LiveSessionSummary(
                            summary = null,
                            isGenerating = false,
                            errorMessage = outcome.message,
                            capturedTurns = turns,
                            capturedFacts = facts
                        )
                    )
                    rebuildChronologicalMessages()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            DavidLiveAudioEngine.abandonAssistantAudioFocus(
                getApplication(),
                activeAssistantAudioFocusRequest
            )
            activeAssistantAudioFocusRequest = null
            liveSessionController.endSession()
        } catch (_: Exception) {
        }
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
