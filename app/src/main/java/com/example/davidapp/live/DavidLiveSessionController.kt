package com.example.davidapp.live

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrates a full-duplex Gemini Multimodal Live WebSocket session (`BidiGenerateContent`).
 *
 * Key responsibilities:
 * - Connects to Gemini Live WebSocket endpoint with automatic model fallback.
 * - Sends initial `setup` frame with Russian financial assistant persona + read-only DB tools.
 * - Streams 16kHz PCM audio chunks from [DavidLiveAudioEngine] in real time.
 * - Plays back 24kHz PCM audio chunks from Gemini Live with barge-in (interruption) support.
 * - Executes `toolCall` requests from Gemini against local Room DAOs via [DavidLiveFinancialToolsProvider]
 *   and returns `toolResponse` frames + updates live UI fact cards.
 * - Collects clean conversation turns for the post-session text summary in chat.
 */
class DavidLiveSessionController(
    private val financialToolsProvider: DavidLiveFinancialToolsProvider,
    private val audioEngine: DavidLiveAudioEngine = DavidLiveAudioEngine(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket keep-alive
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(DavidLiveConnectionState.IDLE)
    val connectionState: StateFlow<DavidLiveConnectionState> = _connectionState.asStateFlow()

    private val _liveSubtitle = MutableStateFlow("")
    val liveSubtitle: StateFlow<String> = _liveSubtitle.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _factCards = MutableStateFlow<List<DavidLiveFactCard>>(emptyList())
    val factCards: StateFlow<List<DavidLiveFactCard>> = _factCards.asStateFlow()

    private val _transcriptTurns = mutableListOf<DavidLiveTranscriptTurn>()
    val transcriptTurns: List<DavidLiveTranscriptTurn>
        get() = synchronized(_transcriptTurns) { _transcriptTurns.toList() }

    private var webSocket: WebSocket? = null
    private var activeApiKey: String = ""
    private var appContext: Context? = null
    private var modelFallbackIndex = 0
    private val isSessionActive = AtomicBoolean(false)
    private val setupCompleted = AtomicBoolean(false)

    private var pendingUserTranscript = StringBuilder()
    private var pendingDavidTranscript = StringBuilder()
    private var speakingStateWatchdogJob: Job? = null

    companion object {
        /**
         * Ordered list of Gemini Multimodal Live models to try.
         * In 2025–2026 Google's BidiGenerateContent API supports native audio preview models
         * and flash-live models; if the user's API key/tier rejects the primary model on close,
         * we automatically retry the next candidate.
         */
        val LIVE_MODEL_CANDIDATES = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.5-flash-native-audio-preview-09-2025",
            "models/gemini-2.0-flash-live-001",
            "models/gemini-2.0-flash-exp"
        )

        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        private val CYRILLIC_REGEX = Regex("[А-Яа-яЁё]")
        private val LATIN_WORD_REGEX = Regex("[A-Za-z]{3,}")
        private val MARKDOWN_HEADER_THOUGHT_REGEX = Regex("^\\s*\\*\\*[A-Za-z0-9 ,.'\\-:!?]+\\*\\*[\\s\\S]*")
        private val ENGLISH_REASONING_PREFIX_REGEX = Regex(
            "^\\s*(I'm |I am |I need to |I will |I should |Let's |Let me |My goal |The user |Thinking| Crafting |Providing |Analyzing |Checking |Formulating |Responding )[\\s\\S]*",
            RegexOption.IGNORE_CASE
        )

        /**
         * Detects whether a `modelTurn.parts[i]` JSON object is an internal Gemini thinking / reasoning trace
         * rather than user-facing Russian assistant speech.
         */
        internal fun isInternalThoughtPart(part: JSONObject): Boolean {
            if (part.optBoolean("thought", false)) return true
            if (part.has("thoughtSignature")) return true
            val text = part.optString("text", "").trim()
            if (text.isEmpty()) return false
            return isLikelyEnglishThoughtText(text)
        }

        /**
         * Heuristic filter for Chain-of-Thought strings that native-audio thinking models
         * sometimes emit without `"thought": true` (e.g., `**Crafting The Greeting**\n\nI'm working on...`).
         */
        internal fun isLikelyEnglishThoughtText(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            if (MARKDOWN_HEADER_THOUGHT_REGEX.matches(trimmed)) return true
            if (ENGLISH_REASONING_PREFIX_REGEX.matches(trimmed)) return true

            val hasCyrillic = CYRILLIC_REGEX.containsMatchIn(trimmed)
            val latinWordsCount = LATIN_WORD_REGEX.findAll(trimmed).count()
            // David always speaks Russian. Purely English multi-word paragraphs in modelTurn are internal thoughts.
            if (!hasCyrillic && latinWordsCount >= 3) return true
            return false
        }

        /**
         * Cleans raw transcription chunks into readable subtitle text.
         */
        internal fun sanitizeSpokenSubtitle(raw: String): String {
            return raw
                .replace(Regex("\\*\\*[^*]+\\*\\*"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    val audioEngineInstance: DavidLiveAudioEngine
        get() = audioEngine

    /**
     * Starts a new Gemini Live session.
     */
    fun startSession(
        context: Context,
        apiKey: String
    ) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            _connectionState.value = DavidLiveConnectionState.ERROR
            _errorMessage.value = "Для голосового режима David Live укажите Gemini API-ключ в настройках чата."
            return
        }

        if (!audioEngine.hasRecordAudioPermission(context)) {
            _connectionState.value = DavidLiveConnectionState.ERROR
            _errorMessage.value = "Нет разрешения на использование микрофона."
            return
        }

        stopInternal(clearTranscript = true)

        this.appContext = context.applicationContext
        this.activeApiKey = trimmedKey
        this.modelFallbackIndex = 0
        this.isSessionActive.set(true)
        this._errorMessage.value = null
        this._isMicMuted.value = false
        this._factCards.value = emptyList()
        this._liveSubtitle.value = "Подключаюсь к David Live..."

        audioEngine.setMuted(false)
        audioEngine.startPlaybackLoop()

        connectWebSocketWithModel(LIVE_MODEL_CANDIDATES[modelFallbackIndex])
    }

    private fun connectWebSocketWithModel(modelName: String) {
        if (!isSessionActive.get()) return

        _connectionState.value = if (modelFallbackIndex == 0) {
            DavidLiveConnectionState.CONNECTING
        } else {
            DavidLiveConnectionState.RECONNECTING
        }
        setupCompleted.set(false)

        val wsUrl = "$WS_BASE_URL?key=$activeApiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isSessionActive.get()) {
                        webSocket.close(1000, "Session cancelled")
                        return
                    }
                    val setupJson = buildSetupMessageJson(modelName)
                    webSocket.send(setupJson.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerMessage(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    handleServerMessage(webSocket, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isSessionActive.get()) return
                    if (!setupCompleted.get() && modelFallbackIndex + 1 < LIVE_MODEL_CANDIDATES.size) {
                        modelFallbackIndex++
                        val nextModel = LIVE_MODEL_CANDIDATES[modelFallbackIndex]
                        _liveSubtitle.value = "Переключаю аудио-модель (${modelFallbackIndex + 1}/${LIVE_MODEL_CANDIDATES.size})..."
                        connectWebSocketWithModel(nextModel)
                    } else if (code != 1000) {
                        _connectionState.value = DavidLiveConnectionState.ERROR
                        _errorMessage.value = buildFriendlyCloseError(code, reason)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!isSessionActive.get()) return
                    if (!setupCompleted.get() && modelFallbackIndex + 1 < LIVE_MODEL_CANDIDATES.size) {
                        modelFallbackIndex++
                        val nextModel = LIVE_MODEL_CANDIDATES[modelFallbackIndex]
                        connectWebSocketWithModel(nextModel)
                    } else {
                        _connectionState.value = DavidLiveConnectionState.ERROR
                        _errorMessage.value = "Сбой соединения David Live: ${t.localizedMessage ?: "проверьте интернет или API-ключ"}"
                    }
                }
            }
        )
    }

    private fun buildFriendlyCloseError(code: Int, reason: String): String {
        val cleanReason = reason.takeIf { it.isNotBlank() } ?: "код $code"
        return when {
            reason.contains("API_KEY", ignoreCase = true) || code == 1008 ->
                "API-ключ отклонён или модель недоступна ($cleanReason)."
            reason.contains("quota", ignoreCase = true) || code == 1013 ->
                "Превышен лимит запросов Gemini API ($cleanReason)."
            else ->
                "Сессия завершена сервером ($cleanReason)."
        }
    }

    private fun startAudioStreaming(ws: WebSocket) {
        val ctx = appContext ?: return
        audioEngine.startCapture(
            context = ctx,
            onPcmChunk = { pcm16kBytes ->
                if (!isSessionActive.get() || !setupCompleted.get()) return@startCapture

                // Client-side barge-in: if user starts speaking loudly while David is talking,
                // immediately duck/clear local buffer so user feels instant responsiveness.
                if (audioEngine.isModelCurrentlyPlaying() && audioEngine.userAudioLevel.value > 0.42f) {
                    audioEngine.clearModelPlaybackQueue()
                }

                val base64Audio = Base64.encodeToString(pcm16kBytes, Base64.NO_WRAP)
                val realtimeInputJson = JSONObject().apply {
                    put(
                        "realtimeInput",
                        JSONObject().apply {
                            put(
                                "mediaChunks",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("mimeType", "audio/pcm;rate=16000")
                                        put("data", base64Audio)
                                    }
                                )
                            )
                        }
                    )
                }
                ws.send(realtimeInputJson.toString())
            },
            onError = { errMsg ->
                _connectionState.value = DavidLiveConnectionState.ERROR
                _errorMessage.value = errMsg
            }
        )
    }

    private fun buildSetupMessageJson(modelName: String): JSONObject {
        val systemPrompt = """
            Ты — Давид Жабов 🐸, харизматичный, ироничный и острый на ум ИИ-аудитор личных финансов в приложении David Finance AI.
            Сейчас ты работаешь в голосовом режиме реального времени David Live.
            Правила общения в David Live:
            1. Всегда говори и отвечай ТОЛЬКО на русском языке. Отвечай живо, естественно и лаконично (2–4 предложения за реплику).
            2. Для любых вопросов про баланс, расходы, доходы, категории, бюджеты, цели, сравнение периодов или прогноз остатка бюджета ОБЯЗАТЕЛЬНО вызывай предоставленные инструменты (tools).
            3. НИКОГДА не выдумывай суммы, категории, проценты или операции, которых нет в ответах инструментов. Если данных недостаточно — честно скажи об этом.
            4. Ты работаешь строго в режиме чтения: никогда не утверждай, что ты сам создал, изменил или удалил финансовую операцию без подтверждения пользователя.
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

    private fun handleServerMessage(ws: WebSocket?, rawJson: String) {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return
        }

        // 1. Setup Complete
        if (root.has("setupComplete")) {
            setupCompleted.set(true)
            _connectionState.value = DavidLiveConnectionState.LISTENING
            _liveSubtitle.value = if (_isMicMuted.value) "Микрофон отключён" else "Говорите, я слушаю..."
            if (ws != null) {
                startAudioStreaming(ws)
            }
            return
        }

        // 2. Tool Call (controlled on-demand financial queries)
        val toolCall = root.optJSONObject("toolCall")
        if (toolCall != null) {
            val functionCalls = toolCall.optJSONArray("functionCalls")
            if (functionCalls != null && functionCalls.length() > 0) {
                _liveSubtitle.value = "Проверяю ваши финансовые данные..."
                controllerScope.launch {
                    val responsesArray = JSONArray()
                    for (i in 0 until functionCalls.length()) {
                        val fc = functionCalls.optJSONObject(i) ?: continue
                        val callId = fc.optString("id")
                        val fnName = fc.optString("name")
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
                    _factCards.value = financialToolsProvider.collectedFacts
                    val toolResponseMsg = JSONObject().apply {
                        put(
                            "toolResponse",
                            JSONObject().apply {
                                put("functionResponses", responsesArray)
                            }
                        )
                    }
                    ws?.send(toolResponseMsg.toString())
                }
            }
            return
        }

        // 3. Server Content (Audio, Transcriptions, Interruption, Turn Complete)
        val serverContent = root.optJSONObject("serverContent") ?: return

        if (serverContent.optBoolean("interrupted", false)) {
            audioEngine.clearModelPlaybackQueue()
            flushPendingTranscripts()
            _connectionState.value = DavidLiveConnectionState.LISTENING
            _liveSubtitle.value = "Слушаю вас..."
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
            _connectionState.value = DavidLiveConnectionState.DAVID_SPEAKING
            val cleanedDavidText = sanitizeSpokenSubtitle(pendingDavidTranscript.toString())
            if (cleanedDavidText.isNotBlank()) {
                _liveSubtitle.value = cleanedDavidText
            }
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
                val textPart = part.optString("text").takeIf { it.isNotBlank() }
                if (!isThoughtPart && textPart != null && outputTranscription == null && !part.has("inlineData")) {
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
                            _connectionState.value = DavidLiveConnectionState.DAVID_SPEAKING
                            if (pendingDavidTranscript.isEmpty()) {
                                _liveSubtitle.value = "Давид отвечает..."
                            }
                            audioEngine.enqueueModelAudioPcm24k(pcmBytes)
                            scheduleSpeakingWatchdog()
                        }
                    }
                }
            }
        }

        if (serverContent.optBoolean("turnComplete", false)) {
            flushPendingTranscripts()
            scheduleReturnToListeningWhenPlaybackEnds()
        }
    }

    /**
     * Test-only entry point to feed synthetic server JSON messages without opening a real socket.
     */
    internal fun processServerMessageForTest(rawJson: String) {
        handleServerMessage(ws = null, rawJson = rawJson)
    }

    private fun scheduleSpeakingWatchdog() {
        speakingStateWatchdogJob?.cancel()
        speakingStateWatchdogJob = controllerScope.launch {
            while (isActive && audioEngine.isModelCurrentlyPlaying()) {
                delay(120)
            }
            if (isSessionActive.get() && _connectionState.value == DavidLiveConnectionState.DAVID_SPEAKING) {
                _connectionState.value = DavidLiveConnectionState.LISTENING
            }
        }
    }

    private fun scheduleReturnToListeningWhenPlaybackEnds() {
        speakingStateWatchdogJob?.cancel()
        speakingStateWatchdogJob = controllerScope.launch {
            while (isActive && audioEngine.isModelCurrentlyPlaying()) {
                delay(100)
            }
            if (isSessionActive.get()) {
                _connectionState.value = DavidLiveConnectionState.LISTENING
                if (!_isMicMuted.value && _liveSubtitle.value.isBlank()) {
                    _liveSubtitle.value = "Говорите, я слушаю..."
                }
            }
        }
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

    private fun recordTranscriptTurn(speaker: DavidLiveSpeaker, rawText: String) {
        val cleaned = sanitizeSpokenSubtitle(rawText)
        if (cleaned.isBlank()) return
        synchronized(_transcriptTurns) {
            val last = _transcriptTurns.lastOrNull()
            if (last != null && last.speaker == speaker) {
                _transcriptTurns[_transcriptTurns.lastIndex] =
                    last.copy(text = "${last.text} $cleaned".replace(Regex("\\s+"), " ").trim())
            } else {
                _transcriptTurns.add(DavidLiveTranscriptTurn(speaker = speaker, text = cleaned))
            }
        }
    }

    fun toggleMicMute(): Boolean {
        val newMuted = !_isMicMuted.value
        setMicMuted(newMuted)
        return newMuted
    }

    fun setMicMuted(muted: Boolean) {
        _isMicMuted.value = muted
        audioEngine.setMuted(muted)
        if (muted) {
            _liveSubtitle.value = "Микрофон на паузе"
        } else if (_connectionState.value == DavidLiveConnectionState.LISTENING) {
            _liveSubtitle.value = "Говорите, я слушаю..."
        }
    }

    /**
     * Allows user to manually tap to interrupt David's current voice output.
     */
    fun interruptModelSpeech() {
        audioEngine.clearModelPlaybackQueue()
        flushPendingTranscripts()
        if (isSessionActive.get()) {
            _connectionState.value = DavidLiveConnectionState.LISTENING
            _liveSubtitle.value = "Слушаю вас..."
        }
    }

    /**
     * Ends the live session and returns collected transcript turns + facts for summary generation.
     */
    fun endSession(): Pair<List<DavidLiveTranscriptTurn>, List<DavidLiveFactCard>> {
        flushPendingTranscripts()
        val turnsSnapshot = transcriptTurns
        val factsSnapshot = _factCards.value
        stopInternal(clearTranscript = false)
        return turnsSnapshot to factsSnapshot
    }

    private fun stopInternal(clearTranscript: Boolean) {
        isSessionActive.set(false)
        setupCompleted.set(false)
        speakingStateWatchdogJob?.cancel()
        audioEngine.stopAll()
        try {
            webSocket?.close(1000, "User ended David Live")
        } catch (_: Exception) {
        }
        webSocket = null
        pendingUserTranscript.clear()
        pendingDavidTranscript.clear()
        if (clearTranscript) {
            synchronized(_transcriptTurns) {
                _transcriptTurns.clear()
            }
            financialToolsProvider.clearCollectedFacts()
        }
        _connectionState.value = DavidLiveConnectionState.IDLE
    }

    fun release() {
        stopInternal(clearTranscript = true)
        controllerScope.cancel()
    }
}
