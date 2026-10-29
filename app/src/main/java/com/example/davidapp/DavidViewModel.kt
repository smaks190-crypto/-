package com.example.davidapp

import android.app.Application
import android.content.Context
import android.media.AudioFocusRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.davidapp.live.DavidLiveAudioEngine
import com.example.davidapp.live.DavidLiveConnectionState
import com.example.davidapp.live.DavidLiveFactCard
import com.example.davidapp.live.DavidLiveFinancialToolsProvider
import com.example.davidapp.live.DavidLiveSessionController
import com.example.davidapp.live.DavidLiveSpeaker
import com.example.davidapp.live.DavidLiveSummaryGenerator
import com.example.davidapp.live.DavidLiveTranscriptTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DavidViewModel(
    application: Application,
    private val transactionDao: TransactionDao,
    private val categoryLimitDao: CategoryLimitDao,
    private val savingGoalDao: SavingGoalDao,
    private val categoryDao: CategoryDao,
    private val davidMessageDao: DavidMessageDao,
    private val settingsManager: SettingsManager,
    private val achievementManager: AchievementManager
) : AndroidViewModel(application) {

    private val currentBudgetIdFlow = MutableStateFlow("default")

    fun setBudgetId(budgetId: String) {
        if (currentBudgetIdFlow.value != budgetId) {
            currentBudgetIdFlow.value = budgetId
            rebuildLiveSessionController(budgetId)
        }
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _mood = MutableStateFlow(DavidMood.NEUTRAL)
    val mood: StateFlow<DavidMood> = _mood.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _hasGeminiConsent = MutableStateFlow(false)
    val hasGeminiConsent: StateFlow<Boolean> = _hasGeminiConsent.asStateFlow()

    private val _showConsentDialog = MutableStateFlow(false)
    val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

    private var pendingMessageAfterConsent: String? = null
    private var pendingSoundManagerAfterConsent: SoundManager? = null
    private var pendingLiveStartAfterConsent: Boolean = false

    private val _unlockedAchievements = MutableStateFlow<Set<String>>(emptySet())
    val unlockedAchievements: StateFlow<Set<String>> = _unlockedAchievements.asStateFlow()

    private val _newlyUnlockedAchievement = MutableStateFlow<Achievement?>(null)
    val newlyUnlockedAchievement: StateFlow<Achievement?> = _newlyUnlockedAchievement.asStateFlow()

    private val _proactiveNotification = MutableStateFlow<String?>(null)
    val proactiveNotification: StateFlow<String?> = _proactiveNotification.asStateFlow()

    // =========================================================================
    // DAVID LIVE (REALTIME MULTIMODAL VOICE SESSION) STATE
    // =========================================================================

    private val _isLiveOverlayVisible = MutableStateFlow(false)
    val isLiveOverlayVisible: StateFlow<Boolean> = _isLiveOverlayVisible.asStateFlow()

    private val _isLiveSessionActive = MutableStateFlow(false)
    val isLiveSessionActive: StateFlow<Boolean> = _isLiveSessionActive.asStateFlow()

    private val _isGeneratingLiveSummary = MutableStateFlow(false)
    val isGeneratingLiveSummary: StateFlow<Boolean> = _isGeneratingLiveSummary.asStateFlow()

    private var activeAssistantAudioFocusRequest: AudioFocusRequest? = null

    private var liveSessionController: DavidLiveSessionController = createLiveController("default")

    private val _liveConnectionState = MutableStateFlow(DavidLiveConnectionState.IDLE)
    val liveConnectionState: StateFlow<DavidLiveConnectionState> = _liveConnectionState.asStateFlow()

    private val _liveSubtitle = MutableStateFlow("")
    val liveSubtitle: StateFlow<String> = _liveSubtitle.asStateFlow()

    private val _liveErrorMessage = MutableStateFlow<String?>(null)
    val liveErrorMessage: StateFlow<String?> = _liveErrorMessage.asStateFlow()

    private val _liveIsMicMuted = MutableStateFlow(false)
    val liveIsMicMuted: StateFlow<Boolean> = _liveIsMicMuted.asStateFlow()

    private val _liveFactCards = MutableStateFlow<List<DavidLiveFactCard>>(emptyList())
    val liveFactCards: StateFlow<List<DavidLiveFactCard>> = _liveFactCards.asStateFlow()

    private val _liveUserAudioLevel = MutableStateFlow(0f)
    val liveUserAudioLevel: StateFlow<Float> = _liveUserAudioLevel.asStateFlow()

    private val _liveModelAudioLevel = MutableStateFlow(0f)
    val liveModelAudioLevel: StateFlow<Float> = _liveModelAudioLevel.asStateFlow()

    private val _liveUserSpectrum = MutableStateFlow(FloatArray(24))
    val liveUserSpectrum: StateFlow<FloatArray> = _liveUserSpectrum.asStateFlow()

    private val _liveModelSpectrum = MutableStateFlow(FloatArray(24))
    val liveModelSpectrum: StateFlow<FloatArray> = _liveModelSpectrum.asStateFlow()

    private var liveCollectorJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonFormat = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        viewModelScope.launch {
            settingsManager.geminiApiKeyFlow.collect { key ->
                _apiKey.value = key
            }
        }
        viewModelScope.launch {
            settingsManager.geminiConsentFlow.collect { consent ->
                _hasGeminiConsent.value = consent
            }
        }
        viewModelScope.launch {
            achievementManager.unlockedAchievementsFlow.collect { unlocked ->
                _unlockedAchievements.value = unlocked
            }
        }
        viewModelScope.launch {
            achievementManager.newAchievementEvent.collect { achievement ->
                _newlyUnlockedAchievement.value = achievement
            }
        }

        bindLiveControllerFlows(liveSessionController)

        viewModelScope.launch {
            currentBudgetIdFlow.flatMapLatest { budgetId ->
                davidMessageDao.getMessagesForBudget(budgetId)
            }.collect { entities ->
                if (entities.isEmpty()) {
                    val welcomeMsg = createWelcomeMessage()
                    _messages.value = listOf(welcomeMsg)
                    saveMessageToDb(welcomeMsg)
                } else {
                    _messages.value = entities.map { it.toChatMessage() }
                }
            }
        }
    }

    private fun createLiveController(budgetId: String): DavidLiveSessionController {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = budgetId,
            transactionDao = transactionDao,
            categoryLimitDao = categoryLimitDao,
            savingGoalDao = savingGoalDao,
            categoryDao = categoryDao
        )
        return DavidLiveSessionController(
            financialToolsProvider = provider,
            httpClient = httpClient
        )
    }

    private fun rebuildLiveSessionController(budgetId: String) {
        if (_isLiveSessionActive.value) {
            liveSessionController.endSession()
            _isLiveSessionActive.value = false
            _isLiveOverlayVisible.value = false
        }
        liveSessionController.release()
        liveSessionController = createLiveController(budgetId)
        bindLiveControllerFlows(liveSessionController)
    }

    private fun bindLiveControllerFlows(controller: DavidLiveSessionController) {
        liveCollectorJob?.cancel()
        liveCollectorJob = viewModelScope.launch {
            launch { controller.connectionState.collect { _liveConnectionState.value = it } }
            launch { controller.liveSubtitle.collect { _liveSubtitle.value = it } }
            launch { controller.errorMessage.collect { _liveErrorMessage.value = it } }
            launch { controller.isMicMuted.collect { _liveIsMicMuted.value = it } }
            launch { controller.factCards.collect { _liveFactCards.value = it } }
            launch { controller.audioEngine.userAudioLevel.collect { _liveUserAudioLevel.value = it } }
            launch { controller.audioEngine.modelAudioLevel.collect { _liveModelAudioLevel.value = it } }
            launch { controller.audioEngine.userSpectrum.collect { _liveUserSpectrum.value = it } }
            launch { controller.audioEngine.modelSpectrum.collect { _liveModelSpectrum.value = it } }
        }
    }

    private fun createWelcomeMessage(): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = "Привет! Я Давид Жабов 🐸 — твой персональный ИИ-аудитор и финансовый консультант.\n\n" +
                    "Я не просто считаю циферки, а помогаю находить дыры в бюджете, строить реальные планы накоплений и не сливать зарплату в первую неделю.\n\n" +
                    "Нажми на одну из быстрых кнопок ниже, напиши вопрос в чат или запусти голосовой режим **David Live** (иконка волны сверху), чтобы поговорить со мной вживую!",
            isUser = false,
            timestamp = getCurrentTimeFormatted(),
            mood = DavidMood.HAPPY,
            suggestions = listOf(
                "📊 Полный разбор бюджета",
                "🔥 Где я теряю деньги?",
                "🎯 Как быстрее накопить на цель?",
                "💡 Совет дня от Жабова"
            )
        )
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsManager.saveGeminiApiKey(key.trim())
        }
    }

    fun requestGeminiConsentIfNeeded(
        messageText: String,
        soundManager: SoundManager? = null
    ): Boolean {
        if (_hasGeminiConsent.value) return true
        pendingMessageAfterConsent = messageText
        pendingSoundManagerAfterConsent = soundManager
        pendingLiveStartAfterConsent = false
        _showConsentDialog.value = true
        return false
    }

    fun onGeminiConsentAccepted() {
        viewModelScope.launch {
            settingsManager.saveGeminiConsent(true)
            _showConsentDialog.value = false
            if (pendingLiveStartAfterConsent) {
                pendingLiveStartAfterConsent = false
                val sm = pendingSoundManagerAfterConsent
                pendingSoundManagerAfterConsent = null
                startDavidLiveSession(sm)
                return@launch
            }
            val msg = pendingMessageAfterConsent
            val sm = pendingSoundManagerAfterConsent
            pendingMessageAfterConsent = null
            pendingSoundManagerAfterConsent = null
            if (!msg.isNullOrBlank()) {
                sendMessage(msg, sm)
            }
        }
    }

    fun onGeminiConsentDeclined() {
        pendingMessageAfterConsent = null
        pendingSoundManagerAfterConsent = null
        pendingLiveStartAfterConsent = false
        _showConsentDialog.value = false
    }

    fun dismissAchievementToast() {
        _newlyUnlockedAchievement.value = null
    }

    fun clearProactiveNotification() {
        _proactiveNotification.value = null
    }

    fun clearHistory(soundManager: SoundManager? = null) {
        viewModelScope.launch {
            val budgetId = currentBudgetIdFlow.value
            davidMessageDao.clearMessagesForBudget(budgetId)
            val welcome = createWelcomeMessage()
            _messages.value = listOf(welcome)
            saveMessageToDb(welcome)
            _mood.value = DavidMood.NEUTRAL
            soundManager?.playClick()
        }
    }

    fun sendMessage(
        userText: String = _inputText.value,
        soundManager: SoundManager? = null
    ) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isTyping.value) return

        if (!_hasGeminiConsent.value) {
            requestGeminiConsentIfNeeded(trimmed, soundManager)
            return
        }

        _inputText.value = ""
        soundManager?.playSend()

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            isUser = true,
            timestamp = getCurrentTimeFormatted()
        )

        val updatedList = _messages.value + userMsg
        _messages.value = updatedList

        viewModelScope.launch {
            saveMessageToDb(userMsg)
            achievementManager.unlockAchievement("first_chat")

            val userMsgCount = updatedList.count { it.isUser }
            if (userMsgCount >= 10) {
                achievementManager.unlockAchievement("chat_master")
            }
            if (trimmed.contains("инвест", ignoreCase = true) ||
                trimmed.contains("акци", ignoreCase = true) ||
                trimmed.contains("вклад", ignoreCase = true) ||
                trimmed.contains("капитал", ignoreCase = true)
            ) {
                achievementManager.unlockAchievement("investor_mindset")
            }

            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..4) {
                achievementManager.unlockAchievement("night_owl")
            }

            _isTyping.value = true
            _mood.value = DavidMood.THINKING

            try {
                val financialContext = buildFinancialContext()
                val currentKey = _apiKey.value.trim()

                val responseResult = if (currentKey.isNotEmpty()) {
                    callGeminiApi(trimmed, financialContext, currentKey, updatedList)
                } else {
                    generateLocalSmartResponse(trimmed, financialContext)
                }

                _isTyping.value = false
                _mood.value = responseResult.mood
                soundManager?.playReceive()

                val botMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = responseResult.text,
                    isUser = false,
                    timestamp = getCurrentTimeFormatted(),
                    mood = responseResult.mood,
                    chartData = responseResult.chartData,
                    actionButton = responseResult.actionButton,
                    suggestions = responseResult.suggestions,
                    financialInsights = responseResult.insights
                )

                _messages.value = _messages.value + botMsg
                saveMessageToDb(botMsg)

            } catch (e: Exception) {
                _isTyping.value = false
                _mood.value = DavidMood.SHOCKED
                val fallback = generateLocalSmartResponse(trimmed, buildFinancialContext())
                val errorNote = if (_apiKey.value.isNotBlank()) {
                    "\n\n*(Примечание: не удалось связаться с облачным сервером Gemini — ${e.localizedMessage ?: "ошибка сети"}. Переключился на локальный аналитический модуль Жабова)*"
                } else ""

                val botMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = fallback.text + errorNote,
                    isUser = false,
                    timestamp = getCurrentTimeFormatted(),
                    mood = fallback.mood,
                    chartData = fallback.chartData,
                    suggestions = fallback.suggestions,
                    financialInsights = fallback.insights
                )
                _messages.value = _messages.value + botMsg
                saveMessageToDb(botMsg)
            }
        }
    }

    private suspend fun buildFinancialContext(): FinancialContextData = withContext(Dispatchers.IO) {
        val budgetId = currentBudgetIdFlow.value
        val transactions = transactionDao.getTransactionsByBudgetSync(budgetId)
        val limits = categoryLimitDao.getLimitsForBudgetSync(budgetId)
        val goals = savingGoalDao.getGoalsByBudgetSync(budgetId)
        val customCategories = categoryDao.getCategoriesByBudgetSync(budgetId)

        val totalIncome = transactions.filter { it.isIncome }.sumOf { it.amount }
        val totalExpense = transactions.filter { !it.isIncome }.sumOf { it.amount }
        val balance = totalIncome - totalExpense

        val expensesByCategory = transactions
            .filter { !it.isIncome }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val incomeByCategory = transactions
            .filter { it.isIncome }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val recentTransactions = transactions
            .sortedByDescending { it.timestamp }
            .take(15)

        FinancialContextData(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            balance = balance,
            expensesByCategory = expensesByCategory,
            incomeByCategory = incomeByCategory,
            limits = limits,
            goals = goals,
            recentTransactions = recentTransactions,
            totalTransactionsCount = transactions.size,
            customCategoriesCount = customCategories.size
        )
    }

    private suspend fun callGeminiApi(
        userQuery: String,
        context: FinancialContextData,
        apiKey: String,
        chatHistory: List<ChatMessage>
    ): BotResponseResult = withContext(Dispatchers.IO) {

        val systemInstructionText = buildSystemPrompt(context)

        val contentsArray = JSONArray()
        val recentHistory = chatHistory.takeLast(8)
        for (msg in recentHistory) {
            val role = if (msg.isUser) "user" else "model"
            val partObj = JSONObject().put("text", msg.text)
            val partsArr = JSONArray().put(partObj)
            val contentObj = JSONObject()
                .put("role", role)
                .put("parts", partsArr)
            contentsArray.put(contentObj)
        }

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstructionText)))
            })
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.75)
                put("maxOutputTokens", 1200)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${responseBody.take(200)}")
        }

        val rootJson = JSONObject(responseBody)
        val candidates = rootJson.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val contentObj = firstCandidate?.optJSONObject("content")
        val parts = contentObj?.optJSONArray("parts")
        val rawText = parts?.optJSONObject(0)?.optString("text")?.trim()
            ?: throw IllegalStateException("Пустой ответ от модели Gemini")

        parseStructuredBotResponse(rawText, userQuery, context)
    }

    private fun buildSystemPrompt(ctx: FinancialContextData): String {
        val topExpensesStr = if (ctx.expensesByCategory.isEmpty()) {
            "Расходов пока нет"
        } else {
            ctx.expensesByCategory.take(6).joinToString("; ") { "${it.first}: ${it.second.toInt()} ₽" }
        }

        val limitsStr = if (ctx.limits.isEmpty()) {
            "Лимиты не установлены"
        } else {
            ctx.limits.joinToString("; ") { lim ->
                val spent = ctx.expensesByCategory.find { it.first == lim.category }?.second ?: 0.0
                "${lim.category}: потрачено ${spent.toInt()} из ${lim.limitAmount.toInt()} ₽"
            }
        }

        val goalsStr = if (ctx.goals.isEmpty()) {
            "Целей накоплений пока нет"
        } else {
            ctx.goals.joinToString("; ") { g ->
                "${g.name}: накоплено ${g.currentAmount.toInt()} из ${g.targetAmount.toInt()} ₽"
            }
        }

        val recentStr = if (ctx.recentTransactions.isEmpty()) {
            "Нет недавних операций"
        } else {
            ctx.recentTransactions.take(8).joinToString(" | ") { tx ->
                val sign = if (tx.isIncome) "+" else "-"
                "$sign${tx.amount.toInt()}₽ (${tx.category}${if (tx.note.isNotBlank()) ": ${tx.note}" else ""})"
            }
        }

        return """
Ты — Давид Жабов 🐸, харизматичный, ироничный, но очень профессиональный и заботливый финансовый аудитор внутри мобильного приложения учёта финансов.
Твой стиль: живой русский язык, лёгкий дружеский юмор, метафоры про жаб/болото/кувшинки и финансовую дисциплину, но всегда конкретные цифры, чёткие расчёты и практическая польза.

ТЕКУЩИЕ ФИНАНСОВЫЕ ДАННЫЕ ПОЛЬЗОВАТЕЛЯ:
- Общий доход: ${ctx.totalIncome.toInt()} ₽
- Общий расход: ${ctx.totalExpense.toInt()} ₽
- Текущий баланс: ${ctx.balance.toInt()} ₽
- Всего операций: ${ctx.totalTransactionsCount}
- Топ категорий расходов: $topExpensesStr
- Установленные лимиты по категориям: $limitsStr
- Финансовые цели: $goalsStr
- Последние операции: $recentStr

ПРАВИЛА ОТВЕТА:
1. Опирайся на реальные цифры пользователя выше. Не выдумывай транзакции, которых нет.
2. Структурируй ответ с помощью списков и жирного шрифта, чтобы его было легко читать с экрана телефона.
3. В самом начале ответа можешь указать тег настроения в квадратных скобках: [HAPPY], [PROUD], [THINKING], [SHOCKED], [ANGRY] или [COOL].
""".trimIndent()
    }

    private fun parseStructuredBotResponse(
        rawText: String,
        userQuery: String,
        context: FinancialContextData
    ): BotResponseResult {
        var cleanedText = rawText
        var detectedMood = DavidMood.HAPPY

        val moodTags = mapOf(
            "[HAPPY]" to DavidMood.HAPPY,
            "[PROUD]" to DavidMood.PROUD,
            "[THINKING]" to DavidMood.THINKING,
            "[SHOCKED]" to DavidMood.SHOCKED,
            "[ANGRY]" to DavidMood.ANGRY,
            "[COOL]" to DavidMood.COOL,
            "[NEUTRAL]" to DavidMood.NEUTRAL
        )

        for ((tag, moodEnum) in moodTags) {
            if (cleanedText.contains(tag, ignoreCase = true)) {
                detectedMood = moodEnum
                cleanedText = cleanedText.replace(tag, "", ignoreCase = true).trim()
                break
            }
        }

        val chartData = if (userQuery.contains("разбор", ignoreCase = true) ||
            userQuery.contains("категор", ignoreCase = true) ||
            userQuery.contains("трат", ignoreCase = true) ||
            userQuery.contains("расход", ignoreCase = true)
        ) {
            buildExpenseChartData(context)
        } else null

        val insights = buildAutomatedInsights(context)

        val suggestions = listOf(
            "📉 Как сократить топ-расход?",
            "💰 План сбережений на месяц",
            "🐸 Оцени мою дисциплину"
        )

        return BotResponseResult(
            text = cleanedText,
            mood = detectedMood,
            chartData = chartData,
            suggestions = suggestions,
            insights = insights
        )
    }

    private fun generateLocalSmartResponse(
        query: String,
        ctx: FinancialContextData
    ): BotResponseResult {
        val q = query.lowercase(Locale.getDefault())
        val savingsRate = if (ctx.totalIncome > 0) {
            ((ctx.totalIncome - ctx.totalExpense) / ctx.totalIncome * 100).toInt()
        } else 0

        val topExpense = ctx.expensesByCategory.firstOrNull()
        val insights = buildAutomatedInsights(ctx)

        return when {
            ctx.totalTransactionsCount == 0 -> {
                BotResponseResult(
                    text = "Ква-ква! 🐸 В твоём болоте пока тишина — ни одной записи о доходах или расходах.\n\n" +
                            "Добавь пару операций через кнопку **«+»** или голосовой ввод на главном экране, и я сразу разложу твой бюджет по полочкам!",
                    mood = DavidMood.HAPPY,
                    suggestions = listOf(
                        "💡 С чего начать учёт финансов?",
                        "🎯 Как правильно ставить цели?",
                        "📊 Зачем нужны лимиты категорий?"
                    )
                )
            }

            q.contains("разбор") || q.contains("анализ") || q.contains("бюджет") || q.contains("баланс") -> {
                val mood = when {
                    ctx.balance < 0 -> DavidMood.SHOCKED
                    savingsRate >= 20 -> DavidMood.PROUD
                    else -> DavidMood.HAPPY
                }

                val topCatText = if (topExpense != null) {
                    val share = if (ctx.totalExpense > 0) (topExpense.second / ctx.totalExpense * 100).toInt() else 0
                    "Главная статья расходов — **${topExpense.first}** (${topExpense.second.toInt()} ₽, или **$share%** всех трат)."
                } else {
                    "Расходов пока не зафиксировано."
                }

                val verdict = when {
                    ctx.balance < 0 -> "⚠️ Внимание: расходы превысили доходы на **${(-ctx.balance).toInt()} ₽**. Пора притормозить импульсивные покупки!"
                    savingsRate >= 25 -> "🏆 Отличная жабья выдержка! Ты сохраняешь **$savingsRate%** от дохода."
                    savingsRate in 10..24 -> "👍 Неплохой темп: норма сбережений составляет **$savingsRate%**. Если дотянуть до 20–25%, капитал будет расти вдвое быстрее."
                    else -> "🧐 Запас прочности тонковат (сохранено всего **$savingsRate%**). Давай проверим, какие подписки или спонтанные траты съедают остаток."
                }

                BotResponseResult(
                    text = "### 📊 Финансовый аудит от Давида Жабова\n\n" +
                            "• **Доходы:** ${ctx.totalIncome.toInt()} ₽\n" +
                            "• **Расходы:** ${ctx.totalExpense.toInt()} ₽\n" +
                            "• **Чистый остаток:** ${ctx.balance.toInt()} ₽\n\n" +
                            "$topCatText\n\n$verdict",
                    mood = mood,
                    chartData = buildExpenseChartData(ctx),
                    insights = insights,
                    suggestions = listOf(
                        "🔥 Где я теряю деньги?",
                        "🎯 Проверить мои цели",
                        "💡 Как откладывать 20% без боли?"
                    )
                )
            }

            q.contains("теряю") || q.contains("дыр") || q.contains("сократ") || q.contains("экономи") -> {
                if (ctx.expensesByCategory.isEmpty()) {
                    BotResponseResult(
                        text = "Трат пока нет — твоя кувшинка в полной безопасности! 🐸 Добавь расходы, чтобы я нашёл точки оптимизации.",
                        mood = DavidMood.COOL
                    )
                } else {
                    val top3 = ctx.expensesByCategory.take(3)
                    val listText = top3.mapIndexed { idx, pair ->
                        val pct = if (ctx.totalExpense > 0) (pair.second / ctx.totalExpense * 100).toInt() else 0
                        "${idx + 1}. **${pair.first}** — ${pair.second.toInt()} ₽ ($pct%)"
                    }.joinToString("\n")

                    val potentialSave = (ctx.totalExpense * 0.15).toInt()

                    BotResponseResult(
                        text = "### 🔍 Поиск утечек в бюджете\n\n" +
                                "Вот топ категорий, куда уходит больше всего денег:\n$listText\n\n" +
                                "**Рекомендация Жабова:** если урезать топ-категорию **${top3.first().first}** хотя бы на 15% и поставить на неё жёсткий лимит в приложении, ты освободишь около **$potentialSave ₽** уже в этом месяце!",
                        mood = DavidMood.THINKING,
                        chartData = buildExpenseChartData(ctx),
                        insights = insights,
                        suggestions = listOf(
                            "📊 Полный разбор бюджета",
                            "🎯 Как быстрее накопить на цель?",
                            "💡 Правило 50/30/20"
                        )
                    )
                }
            }

            q.contains("цел") || q.contains("накоп") -> {
                if (ctx.goals.isEmpty()) {
                    val recommendedMonthly = if (ctx.totalIncome > 0) (ctx.totalIncome * 0.2).toInt() else 10000
                    BotResponseResult(
                        text = "У тебя пока нет активных целей накоплений в разделе **Цели** 🎯.\n\n" +
                                "При твоём текущем потоке оптимально откладывать по **$recommendedMonthly ₽** (20% дохода) сразу в день зарплаты. Создай цель на главном экране, и я помогу рассчитать точный срок её достижения!",
                        mood = DavidMood.HAPPY,
                        suggestions = listOf(
                            "📊 Полный разбор бюджета",
                            "💡 Финансовая подушка безопасности"
                        )
                    )
                } else {
                    val goalsReport = ctx.goals.joinToString("\n\n") { g ->
                        val pct = if (g.targetAmount > 0) ((g.currentAmount / g.targetAmount) * 100).toInt().coerceIn(0, 100) else 0
                        val remaining = (g.targetAmount - g.currentAmount).coerceAtLeast(0.0).toInt()
                        "• **${g.name}**: накоплено **${g.currentAmount.toInt()} ₽** из ${g.targetAmount.toInt()} ₽ ($pct%). Осталось: **$remaining ₽**."
                    }
                    BotResponseResult(
                        text = "### 🎯 Разбор твоих финансовых целей\n\n$goalsReport\n\n" +
                                "Чтобы закрыть цели быстрее, направляй туда любой неплановый остаток в конце недели — даже по 500–1000 ₽ дают мощный эффект сложного ускорения!",
                        mood = DavidMood.PROUD,
                        insights = insights,
                        suggestions = listOf(
                            "📊 Полный разбор бюджета",
                            "🔥 Где я теряю деньги?"
                        )
                    )
                }
            }

            else -> {
                val topCatInfo = topExpense?.let { "Лидер по расходам сейчас — **${it.first}** (${it.second.toInt()} ₽)." } ?: ""
                BotResponseResult(
                    text = "Я внимательно изучил твой запрос! 🐸\n\n" +
                            "Сейчас твой баланс составляет **${ctx.balance.toInt()} ₽** (доходы: ${ctx.totalIncome.toInt()} ₽, расходы: ${ctx.totalExpense.toInt()} ₽). $topCatInfo\n\n" +
                            "Золотое правило болотной бухгалтерии: сначала заплати себе (отложи минимум 10–20% в накопления), зафиксируй лимиты на импульсивные категории и проверяй динамику раз в 3 дня.\n\n" +
                            "Хочешь, чтобы я подключал глубокий генеративный ИИ Gemini к каждому твоему вопросу? Добавь свой Gemini API-ключ в настройках чата (иконка шестерёнки сверху)!",
                    mood = DavidMood.COOL,
                    insights = insights,
                    suggestions = listOf(
                        "📊 Полный разбор бюджета",
                        "🔥 Где я теряю деньги?",
                        "🎯 Как быстрее накопить на цель?"
                    )
                )
            }
        }
    }

    private fun buildExpenseChartData(ctx: FinancialContextData): List<ChartDataPoint>? {
        if (ctx.expensesByCategory.isEmpty() || ctx.totalExpense <= 0) return null
        val palette = listOf(
            0xFF10B981,
            0xFF6366F1,
            0xFFF43F5E,
            0xFFF59E0B,
            0xFF06B6D4,
            0xFF8B5CF6
        )
        return ctx.expensesByCategory.take(5).mapIndexed { index, (cat, amount) ->
            ChartDataPoint(
                label = cat,
                value = amount.toFloat(),
                percentage = (amount / ctx.totalExpense * 100).toFloat(),
                colorHex = palette[index % palette.size]
            )
        }
    }

    private fun buildAutomatedInsights(ctx: FinancialContextData): List<FinancialInsight> {
        val list = mutableListOf<FinancialInsight>()
        if (ctx.totalIncome > 0) {
            val rate = ((ctx.totalIncome - ctx.totalExpense) / ctx.totalIncome * 100).toInt()
            list.add(
                FinancialInsight(
                    title = "Норма сбережений",
                    value = "$rate%",
                    isPositive = rate >= 15
                )
            )
        }
        ctx.expensesByCategory.firstOrNull()?.let { top ->
            list.add(
                FinancialInsight(
                    title = "Топ расход",
                    value = "${top.first} (${top.second.toInt()} ₽)",
                    isPositive = false
                )
            )
        }
        return list
    }

    private suspend fun saveMessageToDb(msg: ChatMessage) = withContext(Dispatchers.IO) {
        val entity = msg.toEntity(currentBudgetIdFlow.value)
        davidMessageDao.insertMessage(entity)
    }

    private fun ChatMessage.toEntity(budgetId: String): DavidMessageEntity {
        val extras = MessageExtrasPayload(
            chartData = chartData,
            actionButton = actionButton,
            suggestions = suggestions,
            financialInsights = financialInsights
        )
        return DavidMessageEntity(
            id = id,
            budgetId = budgetId,
            text = text,
            isUser = isUser,
            timestamp = timestamp,
            mood = mood.name,
            extrasJson = jsonFormat.encodeToString(extras),
            createdAtMillis = System.currentTimeMillis()
        )
    }

    private fun DavidMessageEntity.toChatMessage(): ChatMessage {
        val extras = try {
            if (extrasJson.isNotBlank()) {
                jsonFormat.decodeFromString<MessageExtrasPayload>(extrasJson)
            } else null
        } catch (_: Exception) {
            null
        }
        val parsedMood = try {
            DavidMood.valueOf(mood)
        } catch (_: Exception) {
            DavidMood.NEUTRAL
        }
        return ChatMessage(
            id = id,
            text = text,
            isUser = isUser,
            timestamp = timestamp,
            mood = parsedMood,
            chartData = extras?.chartData,
            actionButton = extras?.actionButton,
            suggestions = extras?.suggestions ?: emptyList(),
            financialInsights = extras?.financialInsights ?: emptyList()
        )
    }

    private fun getCurrentTimeFormatted(): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Generates a proactive financial greeting or warning if there are overspent limits.
     */
    fun checkAndTriggerProactiveGreeting() {
        viewModelScope.launch {
            val ctx = buildFinancialContext()
            for (limit in ctx.limits) {
                val spent = ctx.expensesByCategory.find { it.first == limit.category }?.second ?: 0.0
                if (spent > limit.limitAmount) {
                    _proactiveNotification.value = "🐸 Ква! По категории «${limit.category}» превышен лимит на ${(spent - limit.limitAmount).toInt()} ₽!"
                    return@launch
                }
            }
        }
    }

    /**
     * Builds a dynamic system prompt string used by external callers or tests.
     */
    fun generateDynamicSystemPrompt(
        totalIncome: Double,
        totalExpense: Double,
        expenses: Map<String, Double>
    ): String {
        val topCat = expenses.maxByOrNull { it.value }
        return """
Ты — Давид Жабов, финансовый помощник.
Доходы: ${totalIncome.toInt()} ₽, Расходы: ${totalExpense.toInt()} ₽.
${if (expenses.isNotEmpty()) "Твоя главная финансовая слабость сейчас: ${topCat?.key} (${topCat?.value?.toInt()} ₽)." else "Расходов пока нет."}
        """.trimIndent()
    }

    // =========================================================================
    // DAVID LIVE PUBLIC API
    // =========================================================================

    fun startDavidLiveSession(soundManager: SoundManager? = null) {
        if (!_hasGeminiConsent.value) {
            pendingLiveStartAfterConsent = true
            pendingSoundManagerAfterConsent = soundManager
            _showConsentDialog.value = true
            return
        }

        val key = _apiKey.value.trim()
        _isLiveOverlayVisible.value = true
        _isLiveSessionActive.value = true
        soundManager?.playClick()

        val appContext: Context = getApplication()
        activeAssistantAudioFocusRequest = DavidLiveAudioEngine.requestAssistantAudioFocus(
            context = appContext,
            onFocusLost = {
                liveSessionController.setMicMuted(true)
            }
        )

        liveSessionController.startSession(
            context = appContext,
            apiKey = key
        )

        viewModelScope.launch {
            achievementManager.unlockAchievement("first_chat")
        }
    }

    fun toggleDavidLiveMicMute(soundManager: SoundManager? = null) {
        soundManager?.playClick()
        liveSessionController.toggleMicMute()
    }

    fun interruptDavidLiveSpeech() {
        liveSessionController.interruptModelSpeech()
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
        facts: List<DavidLiveFactCard>,
        soundManager: SoundManager? = null
    ) {
        if (!DavidLiveSummaryGenerator.shouldGenerateSummary(turns, facts)) {
            return
        }

        val currentKey = _apiKey.value.trim()
        val userSpokenSummary = turns
            .filter { it.speaker == DavidLiveSpeaker.USER }
            .joinToString(" ") { it.text }
            .trim()

        if (userSpokenSummary.isNotBlank()) {
            val voiceUserMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "🎙️ *Голосовой сеанс David Live:*\n«${userSpokenSummary.take(280)}${if (userSpokenSummary.length > 280) "…" else ""}»",
                isUser = true,
                timestamp = getCurrentTimeFormatted()
            )
            _messages.value = _messages.value + voiceUserMsg
            viewModelScope.launch { saveMessageToDb(voiceUserMsg) }
        }

        viewModelScope.launch {
            _isGeneratingLiveSummary.value = true
            _isTyping.value = true
            _mood.value = DavidMood.THINKING

            val summaryText = try {
                if (currentKey.isNotBlank()) {
                    callGeminiForLiveSummary(turns, facts, currentKey)
                } else {
                    DavidLiveSummaryGenerator.buildLocalFallbackSummary(turns, facts)
                }
            } catch (_: Exception) {
                DavidLiveSummaryGenerator.buildLocalFallbackSummary(turns, facts)
            }

            _isGeneratingLiveSummary.value = false
            _isTyping.value = false
            _mood.value = DavidMood.PROUD
            soundManager?.playReceive()

            val insights = facts.take(4).map {
                FinancialInsight(
                    title = it.title,
                    value = it.value,
                    isPositive = it.isPositive
                )
            }

            val summaryBotMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                text = summaryText,
                isUser = false,
                timestamp = getCurrentTimeFormatted(),
                mood = DavidMood.PROUD,
                financialInsights = insights,
                suggestions = listOf(
                    "📊 Полный разбор бюджета",
                    "🔥 Где я теряю деньги?",
                    "🎯 Как быстрее накопить на цель?"
                )
            )
            _messages.value = _messages.value + summaryBotMsg
            saveMessageToDb(summaryBotMsg)
        }
    }

    private suspend fun callGeminiForLiveSummary(
        turns: List<DavidLiveTranscriptTurn>,
        facts: List<DavidLiveFactCard>,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = DavidLiveSummaryGenerator.buildGeminiSummaryPrompt(turns, facts)
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.5)
                    put("maxOutputTokens", 700)
                }
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IllegalStateException("Summary HTTP ${response.code}")
        }

        val rootJson = JSONObject(responseBody)
        val text = rootJson.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()

        if (text.isNullOrBlank()) {
            DavidLiveSummaryGenerator.buildLocalFallbackSummary(turns, facts)
        } else {
            text
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveCollectorJob?.cancel()
        liveSessionController.release()
        DavidLiveAudioEngine.abandonAssistantAudioFocus(
            getApplication(),
            activeAssistantAudioFocusRequest
        )
    }
}
