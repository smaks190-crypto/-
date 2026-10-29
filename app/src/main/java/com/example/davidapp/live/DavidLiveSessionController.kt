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
    private val okHttpClient: OkHttpClient = defaultLiveHttpClient()
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

    private val _transcriptTurns = mutableListOf<DavidLiveTranscriptTurn>()
    val transcriptTurns: List<DavidLiveTranscriptTurn>
        get() = synchronized(_transcriptTurns) { _transcriptTurns.toList() }

    private val pendingUserTranscript = StringBuilder()
    private val pendingDavidTranscript = StringBuilder()

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
            _connectionState.value != DavidLiveConnectionState.NO_API_KEY
        ) {
            _displayMode.value = DavidLiveDisplayMode.EXPANDED
            return
        }

        _displayMode.value = DavidLiveDisplayMode.EXPANDED

        if (!validateUserApiKey(userApiKey)) {
            _connectionState.value = DavidLiveConnectionState.NO_API_KEY
            _errorMessage.value = "Для запуска David Live укажите ваш Gemini API ключ в настройках чата (иконка ключа вверху)."
            _liveSubtitle.value = "Требуется пользовательский Gemini API ключ"
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
        }
    }

    /**
     * Expands the compact bottom voice capsule back into the full-screen David Live interface
     * without reconnecting WebSocket or restarting the audio stream.
     */
    fun expandSession() {
        if (_displayMode.value == DavidLiveDisplayMode.MINIMIZED) {
            _displayMode.value = DavidLiveDisplayMode.EXPANDED
        }
    }

    /**
     * Retries connecting after a network or WebSocket failure while preserving session context.
     */
    fun retryConnection() {
        if (!validateUserApiKey(currentApiKey)) {
            _connectionState.value = DavidLiveConnectionState.NO_API_KEY
            return
        }
        connectWebSocket(modelIndex = 0)
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
        _displayMode.value = DavidLiveDisplayMode.HIDDEN
        _connectionState.value = DavidLiveConnectionState.ENDED
        _errorMessage.value = null
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
        handleServerJsonMessage(ws = null, rawJson = rawJson)
    }

    private fun connectWebSocket(modelIndex: Int) {
        intentionalClose.set(false)
        setupCompleted.set(false)
        _errorMessage.value = null
        _connectionState.value = if (modelIndex == 0) {
            DavidLiveConnectionState.CONNECTING
        } else {
            DavidLiveConnectionState.RECONNECTING
        }
        _liveSubtitle.value = "Подключаюсь к David Live..."

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
                        connectWebSocket(modelAttemptIndex)
                        return
                    }
                    if (!intentionalClose.get()) {
                        audioEngine.stop()
                        _connectionState.value = DavidLiveConnectionState.ERROR
                        _errorMessage.value = "Соединение прервано ($code). Проверьте сеть или попробуйте снова."
                        _liveSubtitle.value = "Связь потеряна"
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (intentionalClose.get()) return
                    if (!setupCompleted.get() && modelAttemptIndex + 1 < LIVE_MODELS.size) {
                        modelAttemptIndex++
                        connectWebSocket(modelAttemptIndex)
                        return
                    }
                    audioEngine.stop()
                    _connectionState.value = DavidLiveConnectionState.ERROR
                    _errorMessage.value = "Ошибка подключения: ${t.localizedMessage ?: "Проверьте интернет и API ключ"}"
                    _liveSubtitle.value = "Не удалось подключиться"
                }
            }
        )
    }

    /**
     * Text-only transcription mode: stream microphone audio to Gemini Live for input
     * transcription, but do not request or play spoken assistant responses.
     */
    private fun buildSetupMessageJson(modelName: String): JSONObject {
        return JSONObject().apply {
            put(
                "setup",
                JSONObject().apply {
                    put("model", modelName)
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("responseModalities", JSONArray().put("TEXT"))
                        }
                    )
                    put("inputAudioTranscription", JSONObject())
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

        // Text-only mode: ignore model tool calls; this session is for transcription only.
        if (root.has("toolCall")) return

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

        // Text-only mode: never display or play model output. Preserve only the recognized user text.
        if (serverContent.optBoolean("turnComplete", false)) {
            if (pendingUserTranscript.isNotEmpty()) {
                recordTranscriptTurn(DavidLiveSpeaker.USER, pendingUserTranscript.toString())
                pendingUserTranscript.clear()
            }
            _connectionState.value = DavidLiveConnectionState.LISTENING
            if (!_isMicMuted.value) _liveSubtitle.value = "Говорите, я слушаю..."
        }
        return

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
                            _connectionState.value = DavidLiveConnectionState.DAVID_SPEAKING
                            if (pendingDavidTranscript.isEmpty()) {
                                _liveSubtitle.value = "Давид отвечает..."
                            }
                            audioEngine.enqueueModelAudioPcm24k(pcmBytes)
                        }
                    }
                }
            }
        }

        if (serverContent.optBoolean("turnComplete", false)) {
            flushPendingTranscripts()
            _connectionState.value = DavidLiveConnectionState.LISTENING
            if (!_isMicMuted.value && _liveSubtitle.value.isBlank()) {
                _liveSubtitle.value = "Говорите, я слушаю..."
            }
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
                _connectionState.value = DavidLiveConnectionState.LISTENING
                _liveSubtitle.value = "Слушаю вас..."
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
