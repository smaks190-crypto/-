package com.example.davidapp.live

import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the official Gemini Live API (`BidiGenerateContent`) WebSocket session,
 * full-duplex audio streaming, live subtitles, barge-in interruption, and controlled
 * read-only financial tool calling.
 *
 * Strictly uses ONLY the user-provided Gemini API Key entered manually inside the app.
 */
class DavidLiveSessionController(
    private val financialToolsProvider: DavidLiveFinancialToolsProvider,
    val audioEngine: DavidLiveAudioEngine = DavidLiveAudioEngine(),
    private val okHttpClient: OkHttpClient = defaultLiveHttpClient(),
    private val systemPromptExtrasProvider: () -> String = { "" },
    private val externalUserMessagesProvider: () -> List<String> = { emptyList() }
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var currentApiKey: String = ""
    private var modelAttemptIndex: Int = 0
    private val setupCompleted = AtomicBoolean(false)
    private val intentionalClose = AtomicBoolean(false)

    private val _displayMode = MutableStateFlow(DavidLiveDisplayMode.HIDDEN)
    val displayMode: StateFlow<DavidLiveDisplayMode> = _displayMode.asStateFlow()

    private val _connectionState = MutableStateFlow(DavidLiveConnectionState.IDLE)
    val connectionState: StateFlow<DavidLiveConnectionState> = _connectionState.asStateFlow()

    private val _liveSubtitle = MutableStateFlow("Говорите, я слушаю...")
    val liveSubtitle: StateFlow<String> = _liveSubtitle.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Детализированные реальные сигналы для единой системы визуальных состояний (Issue #56) ---
    private val _isSessionReady = MutableStateFlow(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    private val _isToolLookupActive = MutableStateFlow(false)
    val isToolLookupActive: StateFlow<Boolean> = _isToolLookupActive.asStateFlow()

    private val _activeToolName = MutableStateFlow<String?>(null)
    val activeToolName: StateFlow<String?> = _activeToolName.asStateFlow()

    private val _isAnalyzingToolData = MutableStateFlow(false)
    val isAnalyzingToolData: StateFlow<Boolean> = _isAnalyzingToolData.asStateFlow()

    private val _isPreparingResponse = MutableStateFlow(false)
    val isPreparingResponse: StateFlow<Boolean> = _isPreparingResponse.asStateFlow()

    private val _isModelThinking = MutableStateFlow(false)
    val isModelThinking: StateFlow<Boolean> = _isModelThinking.asStateFlow()

    private val _isSuccessPulseActive = MutableStateFlow(false)
    val isSuccessPulseActive: StateFlow<Boolean> = _isSuccessPulseActive.asStateFlow()

    private val _failureKind = MutableStateFlow(DavidLiveConnectionFailureKind.NONE)
    val failureKind: StateFlow<DavidLiveConnectionFailureKind> = _failureKind.asStateFlow()

    private val _debugOverrideVisualState = MutableStateFlow<DavidLiveVisualState?>(null)
    val debugOverrideVisualState: StateFlow<DavidLiveVisualState?> = _debugOverrideVisualState.asStateFlow()

    private val _visualState = MutableStateFlow(DavidLiveVisualState.IDLE_LISTENING)
    val visualState: StateFlow<DavidLiveVisualState> = _visualState.asStateFlow()

    private val _transcriptTurns = mutableListOf<DavidLiveTranscriptTurn>()
    val transcriptTurns: List<DavidLiveTranscriptTurn>
        get() = synchronized(_transcriptTurns) { _transcriptTurns.toList() }

    private val pendingUserTranscript = StringBuilder()
    private val pendingDavidTranscript = StringBuilder()

    init {
        financialToolsProvider.setConversationalUserMessagesProvider {
            buildList {
                addAll(externalUserMessagesProvider())
                addAll(
                    transcriptTurns
                        .filter { it.speaker == DavidLiveSpeaker.USER }
                        .map { it.text }
                )
                val pending = pendingUserTranscript.toString().trim()
                if (pending.isNotBlank()) {
                    add(pending)
                }
            }
        }
        controllerScope.launch {
            audioEngine.activeSpeaker.collect { recomputeVisualState() }
        }
        controllerScope.launch {
            audioEngine.isPlaybackDrained.collect { recomputeVisualState() }
        }
        controllerScope.launch {
            audioEngine.isWithinUserSpeechHold.collect { recomputeVisualState() }
        }
        controllerScope.launch {
            audioEngine.isWithinDavidSpeechHold.collect { recomputeVisualState() }
        }
    }

    /**
     * Устанавливает или сбрасывает принудительное визуальное состояние для debug-режима и UI-тестов.
     * В Release-сборках (`!BuildConfig.DEBUG`) принудительный override игнорируется, чтобы не подменять реальные состояния.
     */
    fun setDebugVisualStateOverride(overrideState: DavidLiveVisualState?) {
        _debugOverrideVisualState.value = if (com.example.BuildConfig.DEBUG) overrideState else null
        recomputeVisualState()
    }

    /**
     * Запускает кратковременный визуальный импульс успешного завершения (`SUCCESS`),
     * после чего автоматически возвращает сферу в актуальное состояние.
     */
    fun triggerSuccessPulse(durationMs: Long = DavidLiveVisualStateResolver.SUCCESS_PULSE_DURATION_MS) {
        resetProcessingFlags()
        _isSuccessPulseActive.value = true
        recomputeVisualState()
        controllerScope.launch {
            kotlinx.coroutines.delay(durationMs.coerceAtLeast(300L))
            _isSuccessPulseActive.value = false
            recomputeVisualState()
        }
    }

    internal fun resetProcessingFlags() {
        _isToolLookupActive.value = false
        _activeToolName.value = null
        _isAnalyzingToolData.value = false
        _isPreparingResponse.value = false
        _isModelThinking.value = false
        _isSuccessPulseActive.value = false
    }

    internal fun recomputeVisualState() {
        _visualState.value = DavidLiveVisualStateResolver.resolve(
            connectionState = _connectionState.value,
            activeSpeaker = audioEngine.activeSpeaker.value,
            overallEnergy = audioEngine.overallEnergy.value,
            isMicMuted = _isMicMuted.value,
            isSessionReady = _isSessionReady.value,
            isToolLookupActive = _isToolLookupActive.value,
            activeToolName = _activeToolName.value,
            isAnalyzingToolData = _isAnalyzingToolData.value,
            isPreparingResponse = _isPreparingResponse.value,
            isModelThinking = _isModelThinking.value,
            isPlaybackDrained = audioEngine.isPlaybackCurrentlyDrained(),
            isWithinUserSpeechHold = audioEngine.isWithinUserSpeechHold.value,
            isWithinDavidSpeechHold = audioEngine.isWithinDavidSpeechHold.value,
            isSuccessPulseActive = _isSuccessPulseActive.value,
            failureKind = _failureKind.value,
            debugOverrideState = if (com.example.BuildConfig.DEBUG) _debugOverrideVisualState.value else null
        )
    }

    private var _startSessionCount = 0
    val startSessionCount: Int
        get() = _startSessionCount

    /**
     * Returns true when a David Live voice session is currently active (either full-screen or minimized).
     */
    fun isSessionActive(): Boolean {
        return _displayMode.value != DavidLiveDisplayMode.HIDDEN &&
            _connectionState.value != DavidLiveConnectionState.IDLE &&
            _connectionState.value != DavidLiveConnectionState.ENDED
    }

    /**
     * Enforces strict separation between the two voice modes:
     * Regular transaction voice input (`VoiceInputManager`) must never start while David Live is active.
     */
    fun canStartRegularVoiceInput(): Boolean {
        return _displayMode.value == DavidLiveDisplayMode.HIDDEN
    }

    /**
     * Validates that the API key was manually entered by the user and is not empty or a build placeholder.
     */
    fun validateUserApiKey(rawKey: String?): Boolean {
        if (rawKey.isNullOrBlank()) return false
        val trimmed = rawKey.trim()
        if (trimmed.length < 16) return false
        if (trimmed.equals("MY_GEMINI_API_KEY", ignoreCase = true)) return false
        if (trimmed.equals("YOUR_API_KEY", ignoreCase = true)) return false
        return true
    }

    /**
     * Starts a new David Live voice session using only the user's manual API key.
     * If a session is already active (e.g. minimized), expands the existing session
     * instead of creating a duplicate WebSocket connection.
     */
    fun startSession(userApiKey: String?) {
        if (isSessionActive() &&
            _connectionState.value != DavidLiveConnectionState.ERROR &&
            _connectionState.value != DavidLiveConnectionState.OFFLINE &&
            _connectionState.value != DavidLiveConnectionState.NO_API_KEY
        ) {
            _displayMode.value = DavidLiveDisplayMode.EXPANDED
            recomputeVisualState()
            return
        }

        _displayMode.value = DavidLiveDisplayMode.EXPANDED

        if (!validateUserApiKey(userApiKey)) {
            resetProcessingFlags()
            _isSessionReady.value = false
            _failureKind.value = DavidLiveConnectionFailureKind.MISSING_API_KEY
            _connectionState.value = DavidLiveConnectionState.NO_API_KEY
            _errorMessage.value = "Для запуска David Live укажите ваш Gemini API ключ в настройках чата (иконка ключа вверху)."
            _liveSubtitle.value = "Требуется пользовательский Gemini API ключ"
            recomputeVisualState()
            return
        }

        _startSessionCount++
        currentApiKey = userApiKey!!.trim()
        modelAttemptIndex = 0
        synchronized(_transcriptTurns) {
            _transcriptTurns.clear()
        }
        pendingUserTranscript.clear()
        pendingDavidTranscript.clear()
        financialToolsProvider.clearVerifiedFacts()
        resetProcessingFlags()
        _failureKind.value = DavidLiveConnectionFailureKind.NONE
        _isMicMuted.value = false
        audioEngine.setMicMuted(false)

        connectWebSocket(modelIndex = 0)
    }

    /**
     * Collapses the full-screen David Live interface into the compact bottom voice capsule
     * without disconnecting WebSocket, stopping AudioRecord/AudioTrack, or clearing transcripts.
     */
    fun minimizeSession() {
        if (_displayMode.value == DavidLiveDisplayMode.EXPANDED) {
            _displayMode.value = DavidLiveDisplayMode.MINIMIZED
            recomputeVisualState()
        }
    }

    /**
     * Expands the compact bottom voice capsule back into the full-screen David Live interface
     * without reconnecting WebSocket or restarting the audio stream.
     */
    fun expandSession() {
        if (_displayMode.value == DavidLiveDisplayMode.MINIMIZED) {
            _displayMode.value = DavidLiveDisplayMode.EXPANDED
            recomputeVisualState()
        }
    }

    /**
     * Retries connecting after a network or WebSocket failure while preserving session context.
     */
    fun retryConnection() {
        if (!validateUserApiKey(currentApiKey)) {
            resetProcessingFlags()
            _isSessionReady.value = false
            _failureKind.value = DavidLiveConnectionFailureKind.MISSING_API_KEY
            _connectionState.value = DavidLiveConnectionState.NO_API_KEY
            recomputeVisualState()
            return
        }
        resetProcessingFlags()
        _failureKind.value = DavidLiveConnectionFailureKind.NONE
        connectWebSocket(modelIndex = 0, isExplicitReconnect = true)
    }

    /**
     * Toggles the microphone mute state without disconnecting the Gemini Live session.
     * Works identically in both EXPANDED and MINIMIZED display modes.
     */
    fun toggleMicMute() {
        val nextMuted = !_isMicMuted.value
        _isMicMuted.value = nextMuted
        audioEngine.setMicMuted(nextMuted)
        if (nextMuted) {
            _liveSubtitle.value = "Микрофон отключён"
        } else if (_connectionState.value == DavidLiveConnectionState.LISTENING) {
            _liveSubtitle.value = "Говорите, я слушаю..."
        }
        recomputeVisualState()
    }

    /**
     * Ends the David Live session cleanly, stops audio capture/playback, resets displayMode to HIDDEN,
     * and returns the captured conversation turns and verified financial facts for post-session summary generation.
     */
    fun endSession(): Pair<List<DavidLiveTranscriptTurn>, List<DavidLiveToolFact>> {
        intentionalClose.set(true)
        flushPendingTranscripts()
        try {
            webSocket?.close(1000, "User ended David Live session")
        } catch (_: Exception) {
        }
        webSocket = null
        audioEngine.stop()
        resetProcessingFlags()
        _isSessionReady.value = false
        _failureKind.value = DavidLiveConnectionFailureKind.NONE
        _displayMode.value = DavidLiveDisplayMode.HIDDEN
        _connectionState.value = DavidLiveConnectionState.ENDED
        _errorMessage.value = null
        recomputeVisualState()
        return transcriptTurns to financialToolsProvider.verifiedFacts
    }

    /**
     * Appends a transcript turn (also accessible for unit/UI tests to verify summary generation).
     */
    fun recordTranscriptTurn(speaker: DavidLiveSpeaker, text: String) {
        val clean = sanitizeSpokenSubtitle(text)
        if (clean.isEmpty()) return
        synchronized(_transcriptTurns) {
            val last = _transcriptTurns.lastOrNull()
            if (last != null && last.speaker == speaker && System.currentTimeMillis() - last.timestampMs < 3500L) {
                _transcriptTurns[_transcriptTurns.lastIndex] = last.copy(
                    text = "${last.text} $clean".trim()
                )
            } else {
                _transcriptTurns.add(
                    DavidLiveTranscriptTurn(
                        speaker = speaker,
                        text = clean
                    )
                )
            }
        }
    }

    /**
     * Processes a raw JSON message from the Gemini Live server (exposed as `internal` for unit testing).
     */
    internal fun processServerMessageForTest(rawJson: String) {
        setupCompleted.set(true)
        _isSessionReady.value = true
        handleServerJsonMessage(ws = null, rawJson = rawJson)
    }

    /**
     * Классифицирует причину разрыва или сбоя соединения для визуального разделения Offline и Error.
     */
    internal fun classifyConnectionFailure(
        throwable: Throwable? = null,
        httpOrWsCode: Int? = null,
        reason: String? = null
    ): DavidLiveConnectionFailureKind {
        val msg = buildString {
            if (!reason.isNullOrBlank()) append(reason).append(' ')
            if (throwable != null) {
                append(throwable.javaClass.simpleName).append(' ')
                append(throwable.localizedMessage ?: throwable.message ?: "")
            }
        }.lowercase()

        if (httpOrWsCode == 401 || httpOrWsCode == 403 ||
            msg.contains("api_key_invalid") ||
            msg.contains("api key not valid") ||
            msg.contains("permission_denied") ||
            msg.contains("unauthenticated") ||
            msg.contains("invalid api key")
        ) {
            return DavidLiveConnectionFailureKind.INVALID_API_KEY
        }

        if (throwable is java.net.UnknownHostException ||
            throwable is java.net.ConnectException ||
            throwable is java.net.SocketTimeoutException ||
            throwable is java.net.NoRouteToHostException ||
            throwable is java.io.EOFException ||
            (httpOrWsCode != null && httpOrWsCode in listOf(1001, 1006, 1011, 1012, 1013)) ||
            msg.contains("unable to resolve host") ||
            msg.contains("network is unreachable") ||
            msg.contains("software caused connection abort") ||
            msg.contains("connection reset") ||
            msg.contains("broken pipe") ||
            msg.contains("failed to connect") ||
            msg.contains("timed out")
        ) {
            return DavidLiveConnectionFailureKind.NETWORK_OFFLINE
        }

        return DavidLiveConnectionFailureKind.PROTOCOL_OR_SERVER_ERROR
    }

    /**
     * Позволяет в тестах и при сетевых событиях переводить контроллер в состояние потери соединения или ошибки.
     */
    internal fun simulateFailureForTest(
        failureKind: DavidLiveConnectionFailureKind,
        customMessage: String? = null
    ) {
        audioEngine.stop()
        resetProcessingFlags()
        _isSessionReady.value = false
        _failureKind.value = failureKind
        _connectionState.value = DavidLiveConnectionState.ERROR
        _errorMessage.value = customMessage ?: when (failureKind) {
            DavidLiveConnectionFailureKind.NETWORK_OFFLINE ->
                "Соединение прервано. Проверьте интернет и нажмите на сферу для переподключения."
            DavidLiveConnectionFailureKind.INVALID_API_KEY ->
                "Недействительный API-ключ Gemini. Проверьте или обновите ключ в настройках."
            DavidLiveConnectionFailureKind.MISSING_API_KEY ->
                "Для запуска David Live укажите ваш Gemini API ключ."
            else ->
                "Что-то пошло не так. Попробуйте ещё раз."
        }
        _liveSubtitle.value = if (failureKind == DavidLiveConnectionFailureKind.NETWORK_OFFLINE) {
            "Связь прервалась"
        } else {
            "Ошибка подключения"
        }
        recomputeVisualState()
    }

    private fun connectWebSocket(modelIndex: Int, isExplicitReconnect: Boolean = false) {
        intentionalClose.set(false)
        setupCompleted.set(false)
        _isSessionReady.value = false
        resetProcessingFlags()
        _failureKind.value = DavidLiveConnectionFailureKind.NONE
        _errorMessage.value = null
        _connectionState.value = if (modelIndex == 0 && !isExplicitReconnect) {
            DavidLiveConnectionState.CONNECTING
        } else {
            DavidLiveConnectionState.RECONNECTING
        }
        _liveSubtitle.value = if (_connectionState.value == DavidLiveConnectionState.RECONNECTING) {
            "Восстанавливаю соединение с David Live..."
        } else {
            "Подключаюсь к David Live..."
        }
        recomputeVisualState()

        try {
            webSocket?.cancel()
        } catch (_: Exception) {
        }

        val modelName = LIVE_MODELS.getOrElse(modelIndex) { LIVE_MODELS.last() }
        val wsUrl = "$GEMINI_LIVE_WS_ENDPOINT?key=$currentApiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Ready НЕ устанавливается по onOpen — ждём подтверждённого setupComplete от сервера
                    val setupPayload = buildSetupMessageJson(modelName)
                    webSocket.send(setupPayload.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerJsonMessage(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleServerJsonMessage(webSocket, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (!intentionalClose.get() && !setupCompleted.get() && modelAttemptIndex + 1 < LIVE_MODELS.size) {
                        modelAttemptIndex++
                        connectWebSocket(modelAttemptIndex, isExplicitReconnect = true)
                        return
                    }
                    if (!intentionalClose.get()) {
                        audioEngine.stop()
                        resetProcessingFlags()
                        _isSessionReady.value = false
                        val kind = classifyConnectionFailure(httpOrWsCode = code, reason = reason)
                        _failureKind.value = kind
                        _connectionState.value = DavidLiveConnectionState.ERROR
                        _errorMessage.value = when (kind) {
                            DavidLiveConnectionFailureKind.INVALID_API_KEY ->
                                "Недействительный API-ключ Gemini ($code). Проверьте настройки API."
                            DavidLiveConnectionFailureKind.NETWORK_OFFLINE ->
                                "Соединение прервано ($code). Проверьте сеть или нажмите на сферу."
                            else ->
                                "Соединение прервано ($code). Проверьте сеть или попробуйте снова."
                        }
                        _liveSubtitle.value = if (kind == DavidLiveConnectionFailureKind.NETWORK_OFFLINE) {
                            "Связь прервалась"
                        } else {
                            "Связь потеряна"
                        }
                        recomputeVisualState()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (intentionalClose.get()) return
                    val httpCode = response?.code
                    val kind = classifyConnectionFailure(
                        throwable = t,
                        httpOrWsCode = httpCode,
                        reason = response?.message
                    )
                    // Если ключ недействителен, не перебираем остальные модели впустую
                    if (kind != DavidLiveConnectionFailureKind.INVALID_API_KEY &&
                        !setupCompleted.get() &&
                        modelAttemptIndex + 1 < LIVE_MODELS.size
                    ) {
                        modelAttemptIndex++
                        connectWebSocket(modelAttemptIndex, isExplicitReconnect = true)
                        return
                    }
                    audioEngine.stop()
                    resetProcessingFlags()
                    _isSessionReady.value = false
                    _failureKind.value = kind
                    _connectionState.value = DavidLiveConnectionState.ERROR
                    _errorMessage.value = when (kind) {
                        DavidLiveConnectionFailureKind.INVALID_API_KEY ->
                            "Недействительный или неподдерживаемый API-ключ Gemini. Проверьте ключ в настройках."
                        DavidLiveConnectionFailureKind.NETWORK_OFFLINE ->
                            "Нет связи с сервером Gemini Live. Проверьте интернет и нажмите на сферу."
                        else ->
                            "Ошибка подключения: ${t.localizedMessage ?: "Проверьте интернет и API ключ"}"
                    }
                    _liveSubtitle.value = if (kind == DavidLiveConnectionFailureKind.NETWORK_OFFLINE) {
                        "Связь прервалась"
                    } else {
                        "Не удалось подключиться"
                    }
                    recomputeVisualState()
                }
            }
        )
    }

    private fun buildSetupMessageJson(modelName: String): JSONObject {
        val extras = systemPromptExtrasProvider().trim()
        val extrasBlock = if (extras.isNotBlank()) "\n$extras" else ""
        val systemPrompt = """
            Ты — Давид Жабов 🐸, харизматичный, ироничный и острый на ум ИИ-аудитор личных финансов в приложении David Finance AI.$extrasBlock
            Сейчас ты работаешь в голосовом режиме реального времени David Live.
            Правила общения в David Live:
            1. Всегда говори и отвечай ТОЛЬКО на русском языке. Отвечай живо, естественно и лаконично (2–4 предложения за реплику).
            2. Для любых вопросов про баланс, расходы, доходы, категории, бюджеты, цели, долги, сравнение периодов или прогноз остатка бюджета ОБЯЗАТЕЛЬНО вызывай предоставленные инструменты (tools).
            3. НИКОГДА не выдумывай суммы, категории, проценты или операции, которых нет в ответах инструментов. Если данных недостаточно — честно скажи об этом.
            4. Ты работаешь строго в режиме чтения: никогда не утверждай, что ты сам создал, изменил или удалил финансовую операцию без подтверждения пользователя.
            5. СТРОГОЕ РАЗДЕЛЕНИЕ ФИНАНСОВЫХ СУЩНОСТЕЙ (ЗАПРЕТ СМЕШИВАНИЯ):
               - Доступный остаток (текущий баланс `availableBalanceRub` / `totalBalanceRub`) — это фактические деньги на руках с учётом уже полученных доходов и совершённых расходов. НИКОГДА не складывай текущий баланс с зарплатой или доходом повторно!
               - Заработанный доход за период (`earnedIncome30dRub` / `income30dRub`, включая зарплату `salaryIncome30dRub`) — это только поступления дохода за период.
               - Мои долги («Я должен», `totalWeOweRub`) — это обязательства (пассив) пользователя. НИКОГДА не прибавляй их к доходу или к балансу!
               - Должны мне («Мне должны», `totalOwesUsRub`) — это дебиторская задолженность (ожидаемый возврат). НИКОГДА не прибавляй её к текущему заработанному доходу или к доступному остатку!
               - При ответе всегда называй эти статьи раздельно и своими именами: «Доступный остаток», «Доход за период (зарплата)», «Я должен» и «Мне должны».
        """.trimIndent()

        return JSONObject().apply {
            put(
                "setup",
                JSONObject().apply {
                    put("model", modelName)
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("responseModalities", JSONArray().put("AUDIO"))
                            put(
                                "speechConfig",
                                JSONObject().apply {
                                    put(
                                        "voiceConfig",
                                        JSONObject().apply {
                                            put(
                                                "prebuiltVoiceConfig",
                                                JSONObject().apply {
                                                    put("voiceName", "Aoede")
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                    put(
                        "systemInstruction",
                        JSONObject().apply {
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("text", systemPrompt)
                                    }
                                )
                            )
                        }
                    )
                    put(
                        "tools",
                        JSONArray().put(
                            JSONObject().apply {
                                put("functionDeclarations", financialToolsProvider.buildFunctionDeclarationsJson())
                            }
                        )
                    )
                    put("inputAudioTranscription", JSONObject())
                    put("outputAudioTranscription", JSONObject())
                }
            )
        }
    }

    private fun handleServerJsonMessage(ws: WebSocket?, rawJson: String) {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return
        }

        // 1. Setup Complete (подтверждённая готовность сессии и аудиодвижка)
        if (root.has("setupComplete")) {
            setupCompleted.set(true)
            if (ws != null) {
                startAudioStreaming(ws)
            }
            _isSessionReady.value = true
            _failureKind.value = DavidLiveConnectionFailureKind.NONE
            resetProcessingFlags()
            _connectionState.value = DavidLiveConnectionState.LISTENING
            _liveSubtitle.value = if (_isMicMuted.value) "Микрофон отключён" else "Говорите, я слушаю..."
            recomputeVisualState()
            return
        }

        // 2. Tool Call (controlled on-demand financial queries -> FINANCE_LOOKUP -> ANALYZING -> PREPARING_RESPONSE)
        val toolCall = root.optJSONObject("toolCall")
        if (toolCall != null) {
            val functionCalls = toolCall.optJSONArray("functionCalls")
            if (functionCalls != null && functionCalls.length() > 0) {
                val firstToolName = functionCalls.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
                _isModelThinking.value = false
                _isAnalyzingToolData.value = false
                _isPreparingResponse.value = false
                _isToolLookupActive.value = true
                _activeToolName.value = firstToolName
                _liveSubtitle.value = "Проверяю ваши финансовые данные..."
                recomputeVisualState()

                if (ws != null) {
                    controllerScope.launch {
                        val responsesArray = JSONArray()
                        for (i in 0 until functionCalls.length()) {
                            val fc = functionCalls.optJSONObject(i) ?: continue
                            val callId = fc.optString("id")
                            val fnName = fc.optString("name")
                            if (fnName.isNotBlank()) {
                                _activeToolName.value = fnName
                                recomputeVisualState()
                            }
                            val args = fc.optJSONObject("args")
                            val toolResult = financialToolsProvider.executeToolCall(fnName, args)
                            responsesArray.put(
                                JSONObject().apply {
                                    if (callId.isNotBlank()) put("id", callId)
                                    put("name", fnName)
                                    put(
                                        "response",
                                        JSONObject().apply {
                                            put("output", toolResult)
                                        }
                                    )
                                }
                            )
                        }
                        // Поиск в БД завершён — переходим в фазу аналитического сопоставления (ANALYZING)
                        _isToolLookupActive.value = false
                        _activeToolName.value = null
                        if (_connectionState.value != DavidLiveConnectionState.ERROR &&
                            _connectionState.value != DavidLiveConnectionState.OFFLINE &&
                            _connectionState.value != DavidLiveConnectionState.ENDED
                        ) {
                            _isAnalyzingToolData.value = true
                            _liveSubtitle.value = "Проверяю цифры и составляю выводы..."
                            recomputeVisualState()
                        }

                        val toolResponseMsg = JSONObject().apply {
                            put(
                                "toolResponse",
                                JSONObject().apply {
                                    put("functionResponses", responsesArray)
                                }
                            )
                        }
                        ws.send(toolResponseMsg.toString())
                    }
                }
            }
            return
        }

        // 3. Server Content (Audio, Transcriptions, Interruption, Turn Complete)
        val serverContent = root.optJSONObject("serverContent") ?: return

        if (serverContent.optBoolean("interrupted", false)) {
            audioEngine.clearModelPlaybackQueue()
            flushPendingTranscripts()
            resetProcessingFlags()
            _connectionState.value = DavidLiveConnectionState.LISTENING
            _liveSubtitle.value = "Слушаю вас..."
            recomputeVisualState()
            return
        }

        // User's live speech transcription
        val inputTranscription = serverContent.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
        if (inputTranscription != null) {
            if (pendingDavidTranscript.isNotEmpty()) {
                recordTranscriptTurn(DavidLiveSpeaker.DAVID, pendingDavidTranscript.toString())
                pendingDavidTranscript.clear()
            }
            pendingUserTranscript.append(inputTranscription)
            val cleanedUserText = sanitizeSpokenSubtitle(pendingUserTranscript.toString())
            if (cleanedUserText.isNotBlank()) {
                _liveSubtitle.value = cleanedUserText
            }
            // Как только пользователь произнёс фразу, если не идёт поиск/речь Давида — активируем готовность к размышлению
            if (!_isToolLookupActive.value && !_isAnalyzingToolData.value) {
                _isModelThinking.value = true
            }
            recomputeVisualState()
        }

        // David's actual spoken voice transcription (outputTranscription)
        val outputTranscription = serverContent.optJSONObject("outputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
        if (outputTranscription != null) {
            if (pendingUserTranscript.isNotEmpty()) {
                recordTranscriptTurn(DavidLiveSpeaker.USER, pendingUserTranscript.toString())
                pendingUserTranscript.clear()
            }
            pendingDavidTranscript.append(outputTranscription)
            _isToolLookupActive.value = false
            _activeToolName.value = null
            _isAnalyzingToolData.value = false
            _isPreparingResponse.value = false
            _isModelThinking.value = false
            _connectionState.value = DavidLiveConnectionState.DAVID_SPEAKING
            val cleanedDavidText = sanitizeSpokenSubtitle(pendingDavidTranscript.toString())
            if (cleanedDavidText.isNotBlank()) {
                _liveSubtitle.value = cleanedDavidText
            }
            recomputeVisualState()
        }

        val modelTurn = serverContent.optJSONObject("modelTurn")
        val parts = modelTurn?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue

                // IMPORTANT: Native audio thinking models (like gemini-2.5-flash-native-audio-preview)
                // send internal Chain-of-Thought reasoning in `modelTurn.parts[i].text` (often with `"thought": true`
                // or English markdown headers like `**Crafting The Greeting**\nI'm working on...`),
                // while the real Russian spoken response arrives via `serverContent.outputTranscription`.
                // We must NEVER display internal thought parts in the user's subtitle bubble!
                val isThoughtPart = isInternalThoughtPart(part)
                if (isThoughtPart) {
                    // Внутренний Chain-of-Thought модели:
                    // если уже были получены данные финансового инструмента — переводим Analyzing -> Preparing Response,
                    // иначе показываем состояние Thinking!
                    if (_isAnalyzingToolData.value) {
                        _isAnalyzingToolData.value = false
                        _isPreparingResponse.value = true
                    } else if (!_isToolLookupActive.value &&
                        !_isPreparingResponse.value &&
                        _connectionState.value != DavidLiveConnectionState.DAVID_SPEAKING
                    ) {
                        _isModelThinking.value = true
                    }
                    recomputeVisualState()
                }

                val textPart = part.optString("text").takeIf { it.isNotBlank() }
                if (!isThoughtPart && textPart != null && outputTranscription == null && ! part.has("inlineData")) {
                    val cleanedPart = sanitizeSpokenSubtitle(textPart)
                    if (cleanedPart.isNotBlank()) {
                        pendingDavidTranscript.append(cleanedPart)
                        _liveSubtitle.value = sanitizeSpokenSubtitle(pendingDavidTranscript.toString())
                    }
                }

                val inlineData = part.optJSONObject("inlineData")
                if (inlineData != null) {
                    val mimeType = inlineData.optString("mimeType")
                    val b64Data = inlineData.optString("data")
                    if (mimeType.startsWith("audio/pcm") && b64Data.isNotBlank()) {
                        val pcmBytes = try {
                            Base64.decode(b64Data, Base64.DEFAULT)
                        } catch (_: Exception) {
                            null
                        }
                        if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                            if (pendingUserTranscript.isNotEmpty()) {
                                recordTranscriptTurn(DavidLiveSpeaker.USER, pendingUserTranscript.toString())
                                pendingUserTranscript.clear()
                            }
                            _isToolLookupActive.value = false
                            _activeToolName.value = null
                            _isAnalyzingToolData.value = false
                            _isPreparingResponse.value = false
                            _isModelThinking.value = false
                            _connectionState.value = DavidLiveConnectionState.DAVID_SPEAKING
                            if (pendingDavidTranscript.isEmpty()) {
                                _liveSubtitle.value = "Давид отвечает..."
                            }
                            audioEngine.enqueueModelAudioPcm24k(pcmBytes)
                            recomputeVisualState()
                        }
                    }
                }
            }
        }

        if (serverContent.optBoolean("turnComplete", false)) {
            flushPendingTranscripts()
            resetProcessingFlags()
            _connectionState.value = DavidLiveConnectionState.LISTENING
            if (!_isMicMuted.value && _liveSubtitle.value.isBlank()) {
                _liveSubtitle.value = "Говорите, я слушаю..."
            }
            recomputeVisualState()
        }
    }

    /**
     * Detects whether a `modelTurn` part represents internal model reasoning (`thought`)
     * rather than spoken dialogue.
     */
    internal fun isInternalThoughtPart(part: JSONObject): Boolean {
        if (part.optBoolean("thought", false)) return true
        if (part.has("thoughtSignature")) return true
        val rawText = part.optString("text", "").trim()
        if (rawText.isEmpty()) return false
        return isInternalThoughtText(rawText)
    }

    /**
     * Filters out internal English Chain-of-Thought blocks (e.g. `**Crafting The Greeting**\nI'm working on...`,
     * `**Providing Budget Details**\nI need to use the 'get_financial_overview' tool...`).
     */
    internal fun isInternalThoughtText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // Markdown bold title at the start of a reasoning block: `**Title**`
        if (trimmed.startsWith("**") && trimmed.indexOf("**", startIndex = 2) in 3..80) {
            return true
        }
        val lower = trimmed.lowercase()
        val thoughtMarkers = listOf(
            "i'm working on",
            "i am working on",
            "i need to use",
            "i realize my",
            "my goal is",
            "i'll start by",
            "i will start by",
            "i should respond",
            "let's formulate",
            "crafting the",
            "de-escalating the",
            "providing budget",
            "analyzing the user"
        )
        if (thoughtMarkers.any { lower.contains(it) }) {
            return true
        }
        // In David Live, David always speaks Russian. If a modelTurn.part text has no Cyrillic characters
        // at all and contains multiple Latin words, it is internal English reasoning/metadata.
        val hasCyrillic = trimmed.any { it in '\u0400'..'\u04FF' }
        val latinCount = trimmed.count { it in 'A'..'Z' || it in 'a'..'z' }
        if (!hasCyrillic && latinCount > 12) {
            return true
        }
        return false
    }

    /**
     * Strips any accidental markdown thought headers or control tags from spoken subtitle text.
     */
    internal fun sanitizeSpokenSubtitle(raw: String): String {
        val withoutBoldHeaders = raw
            .replace(Regex("\\*\\*[^*]+\\*\\*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (isInternalThoughtText(withoutBoldHeaders)) return ""
        return withoutBoldHeaders
    }

    private fun startAudioStreaming(ws: WebSocket) {
        audioEngine.start(
            onMicPcmChunk = { pcm16kBytes ->
                if (!setupCompleted.get() || _isMicMuted.value) return@start
                val b64 = Base64.encodeToString(pcm16kBytes, Base64.NO_WRAP)
                val msg = JSONObject().apply {
                    put(
                        "realtimeInput",
                        JSONObject().apply {
                            put(
                                "mediaChunks",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", b64)
                                    }
                                )
                            )
                        }
                    )
                }
                ws.send(msg.toString())
            },
            onUserBargeIn = {
                flushPendingTranscripts()
                resetProcessingFlags()
                _connectionState.value = DavidLiveConnectionState.LISTENING
                _liveSubtitle.value = "Слушаю вас..."
                recomputeVisualState()
            }
        )
    }

    private fun flushPendingTranscripts() {
        if (pendingUserTranscript.isNotEmpty()) {
            recordTranscriptTurn(DavidLiveSpeaker.USER, pendingUserTranscript.toString())
            pendingUserTranscript.clear()
        }
        if (pendingDavidTranscript.isNotEmpty()) {
            recordTranscriptTurn(DavidLiveSpeaker.DAVID, pendingDavidTranscript.toString())
            pendingDavidTranscript.clear()
        }
    }

    companion object {
        private const val GEMINI_LIVE_WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        val LIVE_MODELS = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.0-flash-live-001",
            "models/gemini-3.8-flash"
        )

        private fun defaultLiveHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
