package com.example.davidapp.live

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Base64
import com.example.utils.GlobalConsoleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the official Gemini Live API (`BidiGenerateContent`) WebSocket session,
 * full-duplex audio streaming, live subtitles, barge-in interruption, network handoff recovery,
 * context continuation after mid-speech drops, and controlled read-only financial tool calling.
 *
 * Strictly uses ONLY the user-provided Gemini API Key entered manually inside the app.
 */
class DavidLiveSessionController(
    private val financialToolsProvider: DavidLiveFinancialToolsProvider,
    val audioEngine: DavidLiveAudioEngine = DavidLiveAudioEngine(),
    private val okHttpClient: OkHttpClient = defaultLiveHttpClient(),
    private val webSocketFactory: WebSocket.Factory = okHttpClient,
    private val maxAutoReconnectAttempts: Int = DEFAULT_MAX_AUTO_RECONNECT_ATTEMPTS,
    private val autoReconnectDelayMs: Long = DEFAULT_AUTO_RECONNECT_DELAY_MS,
    private val readyFlashDurationMs: Long = DEFAULT_READY_FLASH_DURATION_MS
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var readyFlashJob: Job? = null
    private var currentApiKey: String = ""
    private var modelAttemptIndex: Int = 0
    private var autoReconnectAttempts: Int = 0
    private var isManualReconnectInProgress: Boolean = false
    private val setupCompleted = AtomicBoolean(false)
    private val intentionalClose = AtomicBoolean(false)
    private val isBackgrounded = AtomicBoolean(false)
    private val sessionGeneration = AtomicInteger(0)

    // Session-scoped network monitor state (registered ONLY while David Live is active)
    @Volatile
    private var connectivityManager: ConnectivityManager? = null
    @Volatile
    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private val networkCallbackRegistered = AtomicBoolean(false)
    val isNetworkCallbackRegistered: Boolean
        get() = networkCallbackRegistered.get()

    // Interrupted thought & context continuation state
    @Volatile
    private var needsContinuationOnResume: Boolean = false
    @Volatile
    private var interruptedDavidPartialText: String? = null
    @Volatile
    private var lastResumedTurnSignature: String = ""
    private val executedToolCallIds = ConcurrentHashMap.newKeySet<String>()

    private val _displayMode = MutableStateFlow(DavidLiveDisplayMode.HIDDEN)
    val displayMode: StateFlow<DavidLiveDisplayMode> = _displayMode.asStateFlow()

    private val _connectionState = MutableStateFlow(DavidLiveConnectionState.IDLE)
    val connectionState: StateFlow<DavidLiveConnectionState> = _connectionState.asStateFlow()

    private val _visualPhase = MutableStateFlow(DavidLiveVisualPhase.IDLE)
    val visualPhase: StateFlow<DavidLiveVisualPhase> = _visualPhase.asStateFlow()

    private val _isReadyFlashActive = MutableStateFlow(false)
    val isReadyFlashActive: StateFlow<Boolean> = _isReadyFlashActive.asStateFlow()

    private val _isWaitingForNetwork = MutableStateFlow(false)
    val isWaitingForNetwork: StateFlow<Boolean> = _isWaitingForNetwork.asStateFlow()

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

    private var _reconnectAttemptCount = 0
    val reconnectAttemptCount: Int
        get() = _reconnectAttemptCount

    /**
     * Binds an Android application [Context] so the controller can register a lightweight
     * [ConnectivityManager.NetworkCallback] strictly while a David Live session is active.
     */
    fun bindNetworkContext(context: Context) {
        if (connectivityManager == null) {
            connectivityManager = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }
        if (_displayMode.value != DavidLiveDisplayMode.HIDDEN && !intentionalClose.get()) {
            registerNetworkCallbackIfNeeded()
        }
    }

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
        isBackgrounded.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        readyFlashJob?.cancel()
        readyFlashJob = null
        _isReadyFlashActive.value = false
        _isWaitingForNetwork.value = false
        isManualReconnectInProgress = false

        if (!validateUserApiKey(userApiKey)) {
            resetAudioAndSpeakingStateOnDisconnect(wasInterruptedMidTurn = false)
            unregisterNetworkCallbackInternal()
            _connectionState.value = DavidLiveConnectionState.NO_API_KEY
            _visualPhase.value = DavidLiveVisualPhase.IDLE
            _errorMessage.value = "Для запуска David Live укажите ваш Gemini API ключ в настройках чата (иконка ключа вверху)."
            _liveSubtitle.value = "Требуется пользовательский Gemini API ключ"
            return
        }

        _startSessionCount++
        currentApiKey = userApiKey!!.trim()
        modelAttemptIndex = 0
        autoReconnectAttempts = 0
        needsContinuationOnResume = false
        interruptedDavidPartialText = null
        lastResumedTurnSignature = ""
        executedToolCallIds.clear()
        synchronized(_transcriptTurns) {
            _transcriptTurns.clear()
        }
        pendingUserTranscript.clear()
        pendingDavidTranscript.clear()
        financialToolsProvider.clearVerifiedFacts()
        _isMicMuted.value = false
        audioEngine.setMicMuted(false)

        registerNetworkCallbackIfNeeded()
        connectWebSocket(modelIndex = 0, isAutoReconnect = false)
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
     * Retries connecting after a network or WebSocket failure while preserving session context
     * (`transcriptTurns` and `verifiedFacts` already collected in the current conversation).
     */
    fun retryConnection() {
        reconnectJob?.cancel()
        reconnectJob = null
        readyFlashJob?.cancel()
        readyFlashJob = null
        _isReadyFlashActive.value = false
        _isWaitingForNetwork.value = false

        if (!validateUserApiKey(currentApiKey)) {
            resetAudioAndSpeakingStateOnDisconnect(wasInterruptedMidTurn = false)
            _connectionState.value = DavidLiveConnectionState.NO_API_KEY
            _visualPhase.value = DavidLiveVisualPhase.IDLE
            _errorMessage.value = "Для запуска David Live укажите ваш Gemini API ключ в настройках чата."
            _liveSubtitle.value = "Требуется пользовательский Gemini API ключ"
            return
        }
        if (_displayMode.value == DavidLiveDisplayMode.HIDDEN) {
            _displayMode.value = DavidLiveDisplayMode.EXPANDED
        }
        registerNetworkCallbackIfNeeded()
        autoReconnectAttempts = 0
        modelAttemptIndex = 0
        isManualReconnectInProgress = true
        resetAudioAndSpeakingStateOnDisconnect(wasInterruptedMidTurn = false)
        GlobalConsoleLogger.i("LIVE", "Ручной повтор подключения David Live по запросу пользователя")
        connectWebSocket(modelIndex = 0, isAutoReconnect = false)
    }

    /**
     * Called when the active network is lost (e.g. Wi-Fi turned off or handoff gap to mobile data).
     * Immediately stops audio playback/capture without leaving "Говорит Давид...", preserves
     * any partial transcript, cancels in-flight sockets, and transitions to CONNECTION_LOST waiting for network.
     */
    fun onNetworkLost() {
        if (_displayMode.value == DavidLiveDisplayMode.HIDDEN ||
            intentionalClose.get() ||
            _connectionState.value == DavidLiveConnectionState.IDLE ||
            _connectionState.value == DavidLiveConnectionState.ENDED ||
            _connectionState.value == DavidLiveConnectionState.NO_API_KEY
        ) {
            return
        }

        val wasSpeakingOrThinking = _connectionState.value == DavidLiveConnectionState.DAVID_SPEAKING ||
            _connectionState.value == DavidLiveConnectionState.DAVID_THINKING ||
            pendingDavidTranscript.isNotEmpty() ||
            audioEngine.activeSpeaker.value == DavidLiveSpeaker.DAVID

        sessionGeneration.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        readyFlashJob?.cancel()
        readyFlashJob = null
        _isReadyFlashActive.value = false

        val oldSocket = webSocket
        webSocket = null
        try {
            oldSocket?.cancel()
        } catch (_: Exception) {
        }

        resetAudioAndSpeakingStateOnDisconnect(wasInterruptedMidTurn = wasSpeakingOrThinking)
        _isWaitingForNetwork.value = true
        _errorMessage.value = null
        _connectionState.value = DavidLiveConnectionState.RECONNECTING
        _visualPhase.value = DavidLiveVisualPhase.CONNECTION_LOST
        _liveSubtitle.value = "Связь с Давидом прервана"

        GlobalConsoleLogger.w(
            "LIVE",
            "Потеря сети во время сессии David Live (interruptedSpeech=$wasSpeakingOrThinking). Ожидание восстановления сети..."
        )
    }

    /**
     * Called when network connectivity becomes available again (e.g. mobile data handoff completes).
     * Does NOT assume Gemini Live is ready; triggers a single bounded reconnect with backoff
     * while guarding against duplicate callbacks and parallel sockets.
     */
    fun onNetworkAvailable() {
        if (_displayMode.value == DavidLiveDisplayMode.HIDDEN ||
            intentionalClose.get() ||
            isBackgrounded.get() ||
            !validateUserApiKey(currentApiKey)
        ) {
            return
        }

        val state = _connectionState.value
        if (state == DavidLiveConnectionState.IDLE ||
            state == DavidLiveConnectionState.ENDED ||
            state == DavidLiveConnectionState.NO_API_KEY
        ) {
            return
        }

        // Guard against duplicate network callbacks: if session is already connected or
        // an active socket / reconnect job is already in progress while not waiting for network, ignore.
        if (setupCompleted.get() && webSocket != null && !_isWaitingForNetwork.value) {
            return
        }
        if (!_isWaitingForNetwork.value &&
            state != DavidLiveConnectionState.ERROR &&
            (webSocket != null || reconnectJob?.isActive == true)
        ) {
            return
        }

        _isWaitingForNetwork.value = false
        autoReconnectAttempts = 0
        _errorMessage.value = null
        _connectionState.value = DavidLiveConnectionState.RECONNECTING
        _visualPhase.value = DavidLiveVisualPhase.RECONNECTING
        _liveSubtitle.value = "Восстанавливаем связь..."

        GlobalConsoleLogger.i("LIVE", "Сеть снова доступна — запуск восстановления сессии David Live")
        connectWebSocket(modelIndex = modelAttemptIndex, isAutoReconnect = true)
    }

    /**
     * Handles app transition to background: stops residual model speech playback, resets activeSpeaker
     * so the UI never freezes on "Говорит Давид...", and cancels pending auto-reconnect timers.
     */
    fun onAppBackgrounded() {
        isBackgrounded.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        readyFlashJob?.cancel()
        readyFlashJob = null
        _isReadyFlashActive.value = false
        if (isSessionActive()) {
            val wasSpeaking = _connectionState.value == DavidLiveConnectionState.DAVID_SPEAKING ||
                pendingDavidTranscript.isNotEmpty()
            flushPendingTranscripts(markDavidInterrupted = wasSpeaking)
            audioEngine.clearModelPlaybackQueue()
            if (_connectionState.value == DavidLiveConnectionState.DAVID_SPEAKING) {
                _connectionState.value = if (_isMicMuted.value) {
                    DavidLiveConnectionState.MUTED
                } else {
                    DavidLiveConnectionState.LISTENING
                }
                _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
            }
        }
    }

    /**
     * Handles app return to foreground: if the session was interrupted while backgrounded
     * and remained in RECONNECTING state, resumes connection cleanly.
     */
    fun onAppForegrounded() {
        val wasBackgrounded = isBackgrounded.getAndSet(false)
        if (!wasBackgrounded || !isSessionActive()) return
        if ((_connectionState.value == DavidLiveConnectionState.RECONNECTING || _isWaitingForNetwork.value) &&
            reconnectJob == null &&
            webSocket == null
        ) {
            retryConnection()
        }
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
            if (_connectionState.value == DavidLiveConnectionState.LISTENING ||
                _connectionState.value == DavidLiveConnectionState.USER_SPEAKING
            ) {
                _connectionState.value = DavidLiveConnectionState.MUTED
            }
            if (_connectionState.value != DavidLiveConnectionState.ERROR &&
                _connectionState.value != DavidLiveConnectionState.RECONNECTING
            ) {
                _liveSubtitle.value = "Микрофон отключён"
            }
        } else {
            if (_connectionState.value == DavidLiveConnectionState.MUTED) {
                _connectionState.value = DavidLiveConnectionState.LISTENING
            }
            if (_connectionState.value == DavidLiveConnectionState.LISTENING) {
                _liveSubtitle.value = "Говорите, я слушаю..."
            }
        }
    }

    /**
     * Ends the David Live session cleanly, stops audio capture/playback, unregisters network callbacks,
     * resets displayMode to HIDDEN, and returns the captured conversation turns and verified financial facts.
     */
    fun endSession(): Pair<List<DavidLiveTranscriptTurn>, List<DavidLiveToolFact>> {
        intentionalClose.set(true)
        sessionGeneration.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        readyFlashJob?.cancel()
        readyFlashJob = null
        _isReadyFlashActive.value = false
        _isWaitingForNetwork.value = false
        isManualReconnectInProgress = false
        unregisterNetworkCallbackInternal()
        flushPendingTranscripts(markDavidInterrupted = false)
        val oldSocket = webSocket
        webSocket = null
        try {
            oldSocket?.close(1000, "User ended David Live session")
        } catch (_: Exception) {
        }
        setupCompleted.set(false)
        audioEngine.stop()
        _displayMode.value = DavidLiveDisplayMode.HIDDEN
        _connectionState.value = DavidLiveConnectionState.ENDED
        _visualPhase.value = DavidLiveVisualPhase.IDLE
        _errorMessage.value = null
        return transcriptTurns to financialToolsProvider.verifiedFacts
    }

    /**
     * Appends a transcript turn (also accessible for unit/UI tests to verify summary generation).
     */
    fun recordTranscriptTurn(
        speaker: DavidLiveSpeaker,
        text: String,
        wasInterrupted: Boolean = false
    ) {
        val clean = sanitizeSpokenSubtitle(text)
        if (clean.isEmpty()) return
        synchronized(_transcriptTurns) {
            val last = _transcriptTurns.lastOrNull()
            if (last != null &&
                last.speaker == speaker &&
                !last.wasInterrupted &&
                System.currentTimeMillis() - last.timestampMs < 3500L
            ) {
                _transcriptTurns[_transcriptTurns.lastIndex] = last.copy(
                    text = "${last.text} $clean".trim(),
                    wasInterrupted = wasInterrupted
                )
            } else {
                _transcriptTurns.add(
                    DavidLiveTranscriptTurn(
                        speaker = speaker,
                        text = clean,
                        wasInterrupted = wasInterrupted
                    )
                )
            }
        }
    }

    /**
     * Processes a raw JSON message from the Gemini Live server (exposed as `internal` for unit testing).
     */
    internal fun processServerMessageForTest(rawJson: String) {
        // Detach any background real socket started by default OkHttpClient during synthetic tests
        // so asynchronous DNS/TCP failures cannot overwrite deterministic unit test state.
        if (webSocketFactory === okHttpClient && webSocket != null) {
            val oldSocket = webSocket
            webSocket = null
            val gen = sessionGeneration.incrementAndGet()
            try {
                oldSocket?.cancel()
            } catch (_: Exception) {
            }
            handleServerJsonMessage(ws = null, rawJson = rawJson, connectionGen = gen)
            return
        }
        handleServerJsonMessage(ws = webSocket, rawJson = rawJson, connectionGen = sessionGeneration.get())
    }

    private fun registerNetworkCallbackIfNeeded() {
        val cm = connectivityManager ?: return
        if (networkCallbackRegistered.get()) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = try {
                    cm.getNetworkCapabilities(network)
                } catch (_: Exception) {
                    null
                }
                if (caps == null || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    onNetworkAvailable()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    onNetworkAvailable()
                }
            }

            override fun onLost(network: Network) {
                val activeNet = try {
                    cm.activeNetwork
                } catch (_: Exception) {
                    null
                }
                val activeCaps = try {
                    if (activeNet != null && activeNet != network) cm.getNetworkCapabilities(activeNet) else null
                } catch (_: Exception) {
                    null
                }
                val stillHasInternet = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                if (!stillHasInternet) {
                    onNetworkLost()
                } else {
                    // Handoff to another already-active network (e.g. Wi-Fi lost while cellular is already active):
                    // if the current socket was bound to the old network, trigger clean reconnect.
                    onNetworkLost()
                    onNetworkAvailable()
                }
            }
        }
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            activeNetworkCallback = callback
            networkCallbackRegistered.set(true)
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkCallbackInternal() {
        val cm = connectivityManager
        val cb = activeNetworkCallback
        activeNetworkCallback = null
        if (networkCallbackRegistered.getAndSet(false) && cm != null && cb != null) {
            try {
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
    }

    private fun isStaleCallback(ws: WebSocket?, connectionGen: Int): Boolean {
        if (intentionalClose.get()) return true
        if (connectionGen != sessionGeneration.get()) return true
        val activeWs = webSocket
        if (ws != null && activeWs != null && ws !== activeWs) return true
        return false
    }

    /**
     * Immediately stops audio capture/playback, flushes queued 24 kHz model chunks,
     * commits any pending transcript fragments into [_transcriptTurns], and resets
     * `activeSpeaker` to [DavidLiveSpeaker.NONE] so the UI never remains stuck on "Говорит Давид...".
     */
    private fun resetAudioAndSpeakingStateOnDisconnect(wasInterruptedMidTurn: Boolean = false) {
        val hadPendingDavid = pendingDavidTranscript.isNotEmpty()
        val wasSpeakingOrThinking = wasInterruptedMidTurn ||
            _connectionState.value == DavidLiveConnectionState.DAVID_SPEAKING ||
            _connectionState.value == DavidLiveConnectionState.DAVID_THINKING ||
            hadPendingDavid ||
            audioEngine.activeSpeaker.value == DavidLiveSpeaker.DAVID

        if (hadPendingDavid && wasSpeakingOrThinking) {
            val partial = sanitizeSpokenSubtitle(pendingDavidTranscript.toString())
            if (partial.isNotEmpty()) {
                interruptedDavidPartialText = partial
            }
        }
        flushPendingTranscripts(markDavidInterrupted = wasSpeakingOrThinking)

        val lastTurn = synchronized(_transcriptTurns) { _transcriptTurns.lastOrNull() }
        if (wasSpeakingOrThinking || (lastTurn != null && (lastTurn.speaker == DavidLiveSpeaker.USER || lastTurn.wasInterrupted))) {
            needsContinuationOnResume = true
        }

        setupCompleted.set(false)
        readyFlashJob?.cancel()
        readyFlashJob = null
        _isReadyFlashActive.value = false
        audioEngine.clearModelPlaybackQueue()
        audioEngine.stop()
    }

    private fun handleModelPlaybackDrained(connectionGen: Int) {
        if (intentionalClose.get() || connectionGen != sessionGeneration.get() || !setupCompleted.get()) return
        if (_connectionState.value == DavidLiveConnectionState.DAVID_SPEAKING) {
            flushPendingTranscripts(markDavidInterrupted = false)
            _connectionState.value = if (_isMicMuted.value) {
                DavidLiveConnectionState.MUTED
            } else {
                DavidLiveConnectionState.LISTENING
            }
            _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
        }
    }

    private fun connectWebSocket(modelIndex: Int, isAutoReconnect: Boolean) {
        reconnectJob?.cancel()
        reconnectJob = null
        readyFlashJob?.cancel()
        readyFlashJob = null
        _isReadyFlashActive.value = false
        _isWaitingForNetwork.value = false
        intentionalClose.set(false)
        setupCompleted.set(false)

        val connectionGen = sessionGeneration.incrementAndGet()
        val oldSocket = webSocket
        webSocket = null
        try {
            oldSocket?.cancel()
        } catch (_: Exception) {
        }

        val isReconnectFlow = isAutoReconnect ||
            isManualReconnectInProgress ||
            modelIndex > 0 ||
            synchronized(_transcriptTurns) { _transcriptTurns.isNotEmpty() }

        _errorMessage.value = null
        if (isAutoReconnect) {
            _reconnectAttemptCount++
            _connectionState.value = DavidLiveConnectionState.RECONNECTING
            _visualPhase.value = DavidLiveVisualPhase.RECONNECTING
            _liveSubtitle.value = "Восстанавливаем связь..."
        } else if (modelIndex == 0) {
            _connectionState.value = DavidLiveConnectionState.CONNECTING
            if (isReconnectFlow) {
                _visualPhase.value = DavidLiveVisualPhase.RECONNECTING
                _liveSubtitle.value = "Восстанавливаем связь..."
            } else {
                _visualPhase.value = DavidLiveVisualPhase.INITIAL_CONNECTING
                _liveSubtitle.value = "Пробуждаем Давида..."
            }
        } else {
            _connectionState.value = DavidLiveConnectionState.RECONNECTING
            _visualPhase.value = DavidLiveVisualPhase.RECONNECTING
            _liveSubtitle.value = "Восстанавливаем связь..."
        }

        val modelName = LIVE_MODELS.getOrElse(modelIndex) { LIVE_MODELS.last() }
        val wsUrl = "$GEMINI_LIVE_WS_ENDPOINT?key=$currentApiKey"
        val request = Request.Builder().url(wsUrl).build()

        GlobalConsoleLogger.d(
            "LIVE",
            "Открытие Gemini Live WebSocket (model=$modelName, attempt=${modelIndex + 1}/${LIVE_MODELS.size}, autoReconnect=$isAutoReconnect)"
        )

        webSocket = webSocketFactory.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (isStaleCallback(webSocket, connectionGen)) return
                    // IMPORTANT: Do NOT mark session ready on socket open alone — wait for setupComplete!
                    val setupPayload = buildSetupMessageJson(modelName)
                    webSocket.send(setupPayload.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (isStaleCallback(webSocket, connectionGen)) return
                    handleServerJsonMessage(webSocket, text, connectionGen)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (isStaleCallback(webSocket, connectionGen)) return
                    handleServerJsonMessage(webSocket, bytes.utf8(), connectionGen)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (isStaleCallback(webSocket, connectionGen)) return
                    try {
                        webSocket.close(code, reason)
                    } catch (_: Exception) {
                    }
                    if (code == 1000 && intentionalClose.get()) return
                    handleSocketDisconnect(
                        ws = webSocket,
                        connectionGen = connectionGen,
                        throwable = null,
                        httpCode = null,
                        closingCode = code,
                        closingReason = reason
                    )
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (isStaleCallback(webSocket, connectionGen)) return
                    handleSocketDisconnect(
                        ws = webSocket,
                        connectionGen = connectionGen,
                        throwable = t,
                        httpCode = response?.code,
                        closingCode = null,
                        closingReason = response?.message
                    )
                }
            }
        )
    }

    private fun handleSocketDisconnect(
        ws: WebSocket,
        connectionGen: Int,
        throwable: Throwable?,
        httpCode: Int?,
        closingCode: Int?,
        closingReason: String?
    ) {
        if (isStaleCallback(ws, connectionGen)) return

        val wasSetupCompleted = setupCompleted.get()
        val wasSpeakingOrThinking = _connectionState.value == DavidLiveConnectionState.DAVID_SPEAKING ||
            _connectionState.value == DavidLiveConnectionState.DAVID_THINKING ||
            pendingDavidTranscript.isNotEmpty() ||
            audioEngine.activeSpeaker.value == DavidLiveSpeaker.DAVID

        resetAudioAndSpeakingStateOnDisconnect(wasInterruptedMidTurn = wasSpeakingOrThinking)
        if (webSocket === ws) {
            webSocket = null
        }

        // First mark visual phase as CONNECTION_LOST immediately upon disconnect
        _visualPhase.value = DavidLiveVisualPhase.CONNECTION_LOST
        _liveSubtitle.value = "Связь с Давидом прервана"

        val reasonSummary = classifyDisconnectReason(throwable, httpCode, closingCode, closingReason)

        // 1. If initial handshake with current model failed before setupComplete (and not in a mid-session auto-reconnect cycle),
        // try next fallback model
        if (!wasSetupCompleted &&
            autoReconnectAttempts == 0 &&
            !isManualReconnectInProgress &&
            isModelFallbackEligible(throwable, httpCode, closingCode) &&
            modelAttemptIndex + 1 < LIVE_MODELS.size
        ) {
            modelAttemptIndex++
            GlobalConsoleLogger.w(
                "LIVE",
                "Не удалось инициализировать модель ${LIVE_MODELS[modelAttemptIndex - 1]} ($reasonSummary). Переход на резервную модель ${LIVE_MODELS[modelAttemptIndex]}"
            )
            connectWebSocket(modelIndex = modelAttemptIndex, isAutoReconnect = false)
            return
        }

        // 2. If session was already established (or is in an active auto-reconnect cycle / timed out on ping/pong),
        // perform bounded automatic reconnect with backoff before surfacing a fatal error.
        if (!isBackgrounded.get() &&
            !_isWaitingForNetwork.value &&
            isAutoReconnectEligible(wasSetupCompleted || autoReconnectAttempts > 0, throwable, httpCode, closingCode) &&
            autoReconnectAttempts < maxAutoReconnectAttempts
        ) {
            val attempt = ++autoReconnectAttempts
            _connectionState.value = DavidLiveConnectionState.RECONNECTING
            _visualPhase.value = DavidLiveVisualPhase.RECONNECTING
            _errorMessage.value = null
            _liveSubtitle.value = "Восстанавливаем связь ($attempt/$maxAutoReconnectAttempts)..."

            GlobalConsoleLogger.w(
                "LIVE",
                "Обрыв активной сессии David Live ($reasonSummary). Автопереподключение $attempt/$maxAutoReconnectAttempts..."
            )

            val delayMs = (autoReconnectDelayMs * attempt).coerceAtLeast(0L)
            if (delayMs == 0L) {
                connectWebSocket(modelIndex = modelAttemptIndex, isAutoReconnect = true)
            } else {
                val scheduledGen = sessionGeneration.get()
                reconnectJob?.cancel()
                reconnectJob = controllerScope.launch {
                    delay(delayMs)
                    if (!intentionalClose.get() &&
                        !isBackgrounded.get() &&
                        !_isWaitingForNetwork.value &&
                        scheduledGen == sessionGeneration.get() &&
                        _connectionState.value == DavidLiveConnectionState.RECONNECTING
                    ) {
                        connectWebSocket(modelIndex = modelAttemptIndex, isAutoReconnect = true)
                    }
                }
            }
            return
        }

        // 3. Final Error State: static CONNECTION_LOST phase, Russian diagnostics + manual retry action.
        reconnectJob?.cancel()
        reconnectJob = null
        isManualReconnectInProgress = false
        val russianError = formatRussianLiveErrorMessage(
            t = throwable,
            httpCode = httpCode,
            closingCode = closingCode,
            closingReason = closingReason
        )
        _connectionState.value = DavidLiveConnectionState.ERROR
        _visualPhase.value = DavidLiveVisualPhase.CONNECTION_LOST
        _errorMessage.value = russianError
        _liveSubtitle.value = "Связь с Давидом прервана"

        GlobalConsoleLogger.e(
            "LIVE",
            "Сессия David Live остановлена из-за ошибки ($reasonSummary): $russianError"
        )
    }

    internal fun buildSetupMessageJson(modelName: String): JSONObject {
        val basePrompt = """
            Ты — Давид Жабов 🐸, харизматичный, ироничный и острый на ум ИИ-аудитор личных финансов в приложении David Finance AI.
            Сейчас ты работаешь в голосовом режиме реального времени David Live.
            Правила общения в David Live:
            1. Всегда говори и отвечай ТОЛЬКО на русском языке. Отвечай живо, естественно и лаконично (2–4 предложения за реплику).
            2. Для любых вопросов про баланс, расходы, доходы, категории, бюджеты, цели, сравнение периодов или прогноз остатка бюджета ОБЯЗАТЕЛЬНО вызывай предоставленные инструменты (tools).
            3. НИКОГДА не выдумывай суммы, категории, проценты или операции, которых нет в ответах инструментов. Если данных недостаточно — честно скажи об этом.
            4. Ты работаешь строго в режиме чтения: никогда не утверждай, что ты сам создал, изменил или удалил финансовую операцию без подтверждения пользователя.
        """.trimIndent()

        val savedTurns = transcriptTurns
        val savedFacts = financialToolsProvider.verifiedFacts
        val systemPrompt = if (savedTurns.isEmpty() && savedFacts.isEmpty()) {
            basePrompt
        } else {
            buildString {
                append(basePrompt)
                append("\n\n=== КОНТЕКСТ ВОССТАНОВЛЕННОГО РАЗГОВОРА (НОВАЯ СЕРВЕРНАЯ СЕССИЯ ПОСЛЕ ОБРЫВА СЕТИ) ===\n")
                append("Внимание: предыдущее WebSocket-соединение кратковременно прервалось, поэтому создана новая серверная сессия Gemini Live с передачей сохранённого контекста.\n")
                if (savedFacts.isNotEmpty()) {
                    append("Уже проверенные финансовые факты в этом разговоре (НЕ вызывай повторно инструменты для этих же данных без нового запроса пользователя):\n")
                    for (fact in savedFacts.takeLast(6)) {
                        append("- [${fact.toolName}]: ${fact.summary}\n")
                    }
                }
                if (savedTurns.isNotEmpty()) {
                    append("Недавние реплики текущего разговора:\n")
                    for (turn in savedTurns.takeLast(8)) {
                        val roleLabel = if (turn.speaker == DavidLiveSpeaker.USER) "Пользователь" else "Давид Жабов"
                        val interruptedTag = if (turn.wasInterrupted) " [ПРЕОРВАНО НА ПОЛУСЛОВЕ ИЗ-ЗА ОБРЫВА СЕТИ]" else ""
                        append("- $roleLabel$interruptedTag: ${turn.text}\n")
                    }
                }
                val partialInterrupted = interruptedDavidPartialText
                if (!partialInterrupted.isNullOrBlank()) {
                    append("Твоя последняя реплика оборвалась на фразе: «$partialInterrupted».\n")
                    append("ПРАВИЛО ПРОДОЛЖЕНИЯ: Кратко (одной фразой) обозначь, что связь восстановлена, и сразу продолжи свою последнюю незавершённую мысль строго с места обрыва, НЕ начиная ответ с нуля и НЕ повторяя уже произнесённые предложения.")
                } else {
                    append("ПРАВИЛО ПРОДОЛЖЕНИЯ: Кратко обозначь восстановление связи и продолжи диалог с учётом сохранённого контекста, не повторяя уже сказанные предложения.")
                }
            }
        }

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

    /**
     * Builds a single `clientContent` JSON payload sent right after `setupComplete` on a
     * reconnected session when the conversation was interrupted mid-turn so David continues
     * his unfinished thought without repeating already spoken sentences.
     */
    internal fun buildResumeContinuationClientContentJson(): JSONObject? {
        val turns = transcriptTurns
        if (turns.isEmpty()) return null

        val recentContext = turns.takeLast(6).joinToString("\n") { t ->
            val who = if (t.speaker == DavidLiveSpeaker.USER) "Пользователь" else "Давид"
            val tag = if (t.wasInterrupted) " (оборвалось на полуслове)" else ""
            "$who$tag: ${t.text}"
        }
        val partial = interruptedDavidPartialText
        val instructionText = if (!partial.isNullOrBlank()) {
            "[Системное уведомление о восстановлении связи после обрыва сети]\n" +
                "История перед обрывом:\n$recentContext\n\n" +
                "Ты уже успел произнести: «$partial».\n" +
                "Кратко скажи, что ты снова на связи, и продолжи свою мысль строго с того места, где остановился, НЕ начиная ответ с нуля и НЕ повторяя уже сказанные предложения."
        } else {
            "[Системное уведомление о восстановлении связи после обрыва сети]\n" +
                "История перед обрывом:\n$recentContext\n\n" +
                "Кратко обозначь, что связь восстановлена, и ответь на последнюю реплику пользователя (или продолжи незаконченную мысль), не повторяя уже завершённые предложения."
        }

        return JSONObject().apply {
            put(
                "clientContent",
                JSONObject().apply {
                    put(
                        "turns",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().apply {
                                            put("text", instructionText)
                                        }
                                    )
                                )
                            }
                        )
                    )
                    put("turnComplete", true)
                }
            )
        }
    }

    private fun buildTurnSignature(): String {
        val turns = transcriptTurns
        val last = turns.lastOrNull() ?: return ""
        return "${turns.size}:${last.speaker}:${last.text.hashCode()}:${interruptedDavidPartialText.orEmpty().hashCode()}"
    }

    private fun handleServerJsonMessage(ws: WebSocket?, rawJson: String, connectionGen: Int) {
        if (isStaleCallback(ws, connectionGen)) return

        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return
        }

        // 1. Setup Complete — ONLY here is the Gemini Live session genuinely ready!
        if (root.has("setupComplete")) {
            val hasPriorTurns = synchronized(_transcriptTurns) { _transcriptTurns.isNotEmpty() }
            val wasReconnecting = autoReconnectAttempts > 0 ||
                isManualReconnectInProgress ||
                _connectionState.value == DavidLiveConnectionState.RECONNECTING ||
                hasPriorTurns

            setupCompleted.set(true)
            autoReconnectAttempts = 0
            isManualReconnectInProgress = false
            _isWaitingForNetwork.value = false
            _errorMessage.value = null
            _connectionState.value = if (_isMicMuted.value) {
                DavidLiveConnectionState.MUTED
            } else {
                DavidLiveConnectionState.LISTENING
            }
            _visualPhase.value = DavidLiveVisualPhase.READY_FLASH
            _isReadyFlashActive.value = true
            _liveSubtitle.value = when {
                _isMicMuted.value -> "Микрофон отключён"
                wasReconnecting -> "Давид на связи. Связь восстановлена — продолжаем разговор."
                else -> "Давид на связи"
            }

            readyFlashJob?.cancel()
            if (readyFlashDurationMs > 0L) {
                readyFlashJob = controllerScope.launch {
                    delay(readyFlashDurationMs)
                    if (!intentionalClose.get() &&
                        connectionGen == sessionGeneration.get() &&
                        setupCompleted.get()
                    ) {
                        _isReadyFlashActive.value = false
                        if (isDavidLiveVoiceCapableState(_connectionState.value)) {
                            _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
                        }
                    }
                }
            }

            GlobalConsoleLogger.i("LIVE", "Сессия David Live готова (setupComplete, reconnected=$wasReconnecting)")
            if (ws != null) {
                startAudioStreaming(ws, connectionGen)
                if (wasReconnecting && needsContinuationOnResume) {
                    val signature = buildTurnSignature()
                    if (signature.isNotEmpty() && signature != lastResumedTurnSignature) {
                        val resumePayload = buildResumeContinuationClientContentJson()
                        if (resumePayload != null) {
                            lastResumedTurnSignature = signature
                            needsContinuationOnResume = false
                            interruptedDavidPartialText = null
                            ws.send(resumePayload.toString())
                            GlobalConsoleLogger.i("LIVE", "Отправлен контекст продолжения прерванной мысли после восстановления сессии")
                        }
                    }
                }
            }
            return
        }

        // 2. Tool Call (controlled on-demand read-only financial queries with deduplication)
        val toolCall = root.optJSONObject("toolCall")
        if (toolCall != null) {
            val functionCalls = toolCall.optJSONArray("functionCalls")
            if (functionCalls != null && functionCalls.length() > 0) {
                _isReadyFlashActive.value = false
                _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
                _connectionState.value = DavidLiveConnectionState.DAVID_THINKING
                _liveSubtitle.value = "Проверяю ваши финансовые данные..."
                controllerScope.launch {
                    if (isStaleCallback(ws, connectionGen)) return@launch
                    val responsesArray = JSONArray()
                    for (i in 0 until functionCalls.length()) {
                        val fc = functionCalls.optJSONObject(i) ?: continue
                        val callId = fc.optString("id")
                        val fnName = fc.optString("name")
                        val args = fc.optJSONObject("args")
                        if (callId.isNotBlank() && !executedToolCallIds.add(callId)) {
                            // Duplicate toolCall ID already processed in this session
                            continue
                        }
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
                    if (isStaleCallback(ws, connectionGen) || responsesArray.length() == 0) return@launch
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
            flushPendingTranscripts(markDavidInterrupted = false)
            _isReadyFlashActive.value = false
            _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
            _connectionState.value = if (_isMicMuted.value) {
                DavidLiveConnectionState.MUTED
            } else {
                DavidLiveConnectionState.LISTENING
            }
            _liveSubtitle.value = if (_isMicMuted.value) "Микрофон отключён" else "Слушаю вас..."
            return
        }

        // User's live speech transcription
        val inputTranscription = serverContent.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
        if (inputTranscription != null) {
            _isReadyFlashActive.value = false
            _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
            if (pendingDavidTranscript.isNotEmpty()) {
                recordTranscriptTurn(DavidLiveSpeaker.DAVID, pendingDavidTranscript.toString())
                pendingDavidTranscript.clear()
            }
            pendingUserTranscript.append(inputTranscription)
            needsContinuationOnResume = true
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
            _isReadyFlashActive.value = false
            _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
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

                val isThoughtPart = isInternalThoughtPart(part)
                val textPart = part.optString("text").takeIf { it.isNotBlank() }
                if (!isThoughtPart && textPart != null && outputTranscription == null && !part.has("inlineData")) {
                    val cleanedPart = sanitizeSpokenSubtitle(textPart)
                    if (cleanedPart.isNotBlank()) {
                        _isReadyFlashActive.value = false
                        _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
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
                            _isReadyFlashActive.value = false
                            _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
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
            flushPendingTranscripts(markDavidInterrupted = false)
            needsContinuationOnResume = false
            interruptedDavidPartialText = null
            _isReadyFlashActive.value = false
            _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
            _connectionState.value = if (_isMicMuted.value) {
                DavidLiveConnectionState.MUTED
            } else {
                DavidLiveConnectionState.LISTENING
            }
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

    private fun startAudioStreaming(ws: WebSocket, connectionGen: Int) {
        audioEngine.stop()
        audioEngine.start(
            onMicPcmChunk = { pcm16kBytes ->
                if (isStaleCallback(ws, connectionGen)) return@start
                if (!setupCompleted.get() || _isMicMuted.value) return@start
                val state = _connectionState.value
                if (state == DavidLiveConnectionState.ERROR ||
                    state == DavidLiveConnectionState.RECONNECTING ||
                    state == DavidLiveConnectionState.ENDED
                ) {
                    return@start
                }
                if (ws.queueSize() > MAX_WS_OUTBOUND_QUEUE_BYTES) {
                    return@start
                }
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
                if (isStaleCallback(ws, connectionGen)) return@start
                flushPendingTranscripts(markDavidInterrupted = false)
                _isReadyFlashActive.value = false
                _visualPhase.value = DavidLiveVisualPhase.CONVERSATION_ACTIVE
                _connectionState.value = if (_isMicMuted.value) {
                    DavidLiveConnectionState.MUTED
                } else {
                    DavidLiveConnectionState.LISTENING
                }
                _liveSubtitle.value = if (_isMicMuted.value) "Микрофон отключён" else "Слушаю вас..."
            },
            onModelPlaybackDrained = {
                handleModelPlaybackDrained(connectionGen)
            }
        )
    }

    private fun flushPendingTranscripts(markDavidInterrupted: Boolean = false) {
        if (pendingUserTranscript.isNotEmpty()) {
            recordTranscriptTurn(DavidLiveSpeaker.USER, pendingUserTranscript.toString(), wasInterrupted = false)
            pendingUserTranscript.clear()
        }
        if (pendingDavidTranscript.isNotEmpty()) {
            recordTranscriptTurn(
                speaker = DavidLiveSpeaker.DAVID,
                text = pendingDavidTranscript.toString(),
                wasInterrupted = markDavidInterrupted
            )
            pendingDavidTranscript.clear()
        } else if (markDavidInterrupted) {
            synchronized(_transcriptTurns) {
                val lastIdx = _transcriptTurns.lastIndex
                if (lastIdx >= 0 && _transcriptTurns[lastIdx].speaker == DavidLiveSpeaker.DAVID) {
                    _transcriptTurns[lastIdx] = _transcriptTurns[lastIdx].copy(wasInterrupted = true)
                    interruptedDavidPartialText = _transcriptTurns[lastIdx].text
                }
            }
        }
    }

    companion object {
        private const val GEMINI_LIVE_WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        const val DEFAULT_PING_INTERVAL_SECONDS = 40L
        const val MAX_WS_OUTBOUND_QUEUE_BYTES = 48 * 1024L
        const val DEFAULT_MAX_AUTO_RECONNECT_ATTEMPTS = 2
        const val DEFAULT_AUTO_RECONNECT_DELAY_MS = 900L
        const val DEFAULT_READY_FLASH_DURATION_MS = 900L

        val LIVE_MODELS = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.0-flash-live-001",
            "models/gemini-3.8-flash"
        )

        fun defaultLiveHttpClient(
            pingIntervalSeconds: Long = DEFAULT_PING_INTERVAL_SECONDS
        ): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .pingInterval(pingIntervalSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        internal fun isPingPongTimeout(t: Throwable?): Boolean {
            if (t == null) return false
            val msg = (t.message ?: t.localizedMessage ?: "").lowercase()
            return msg.contains("sent ping but didn't receive pong") ||
                (t is SocketTimeoutException && (msg.contains("ping") || msg.contains("pong")))
        }

        internal fun isOfflineNetworkError(t: Throwable?): Boolean {
            if (t == null) return false
            if (t is UnknownHostException || t is ConnectException || t is NoRouteToHostException) {
                return true
            }
            val msg = (t.message ?: t.localizedMessage ?: "").lowercase()
            return msg.contains("unable to resolve host") ||
                msg.contains("no address associated with hostname") ||
                msg.contains("network is unreachable") ||
                msg.contains("enetunreach") ||
                msg.contains("econnrefused")
        }

        internal fun isAuthOrKeyError(t: Throwable?, httpCode: Int?, closingReason: String?): Boolean {
            if (httpCode == 401 || httpCode == 403) return true
            val combined = "${t?.message.orEmpty()} ${closingReason.orEmpty()}".lowercase()
            return combined.contains("api_key_invalid") ||
                combined.contains("api key not valid") ||
                combined.contains("permission_denied") ||
                combined.contains("unauthenticated")
        }

        internal fun isModelFallbackEligible(
            t: Throwable?,
            httpCode: Int?,
            closingCode: Int?
        ): Boolean {
            if (isOfflineNetworkError(t)) return false
            if (isAuthOrKeyError(t, httpCode, null)) return false
            if (closingCode == 1000) return false
            return true
        }

        internal fun isAutoReconnectEligible(
            wasSetupCompleted: Boolean,
            t: Throwable?,
            httpCode: Int?,
            closingCode: Int?
        ): Boolean {
            if (isOfflineNetworkError(t)) return false
            if (isAuthOrKeyError(t, httpCode, null)) return false
            if (closingCode == 1000) return false
            if (isPingPongTimeout(t)) return true
            if (wasSetupCompleted) {
                return t is SocketTimeoutException ||
                    t is EOFException ||
                    t is SocketException ||
                    httpCode == 502 ||
                    httpCode == 503 ||
                    httpCode == 504 ||
                    closingCode != null ||
                    t != null
            }
            return false
        }

        internal fun classifyDisconnectReason(
            t: Throwable?,
            httpCode: Int?,
            closingCode: Int?,
            closingReason: String?
        ): String {
            return when {
                isPingPongTimeout(t) -> "PING_PONG_TIMEOUT"
                isOfflineNetworkError(t) -> "NETWORK_OFFLINE"
                isAuthOrKeyError(t, httpCode, closingReason) -> "AUTH_ERROR_${httpCode ?: 403}"
                httpCode != null -> "HTTP_$httpCode"
                closingCode != null -> "WS_CLOSE_$closingCode"
                t != null -> t.javaClass.simpleName
                else -> "UNKNOWN_DISCONNECT"
            }
        }

        /**
         * Translates low-level OkHttp / WebSocket / network exceptions into clear, user-friendly
         * Russian diagnostic messages without exposing API keys or internal URLs.
         */
        internal fun formatRussianLiveErrorMessage(
            t: Throwable?,
            httpCode: Int? = null,
            closingCode: Int? = null,
            closingReason: String? = null
        ): String {
            val rawMsg = "${t?.message.orEmpty()} ${t?.localizedMessage.orEmpty()} ${closingReason.orEmpty()}"
                .replace(Regex("key=[^&\\s\"']+"), "key=***")
                .trim()
            val lower = rawMsg.lowercase()

            return when {
                isPingPongTimeout(t) ->
                    "Превышено время ожидания ответа от сервера Gemini Live (тайм-аут пинга). Проверьте стабильность интернета и нажмите «Повторить подключение»."

                isOfflineNetworkError(t) ->
                    "Нет соединения с интернетом. Проверьте подключение к сети и нажмите «Повторить подключение»."

                isAuthOrKeyError(t, httpCode, closingReason) ->
                    "Ошибка авторизации Gemini API. Проверьте корректность вашего API-ключа в настройках."

                httpCode == 429 || lower.contains("429") || lower.contains("quota") || lower.contains("resource_exhausted") ->
                    "Превышен лимит запросов Gemini API (429). Подождите немного или проверьте квоту ключа, затем нажмите «Повторить подключение»."

                httpCode == 503 || httpCode == 502 || httpCode == 504 ||
                    lower.contains("503") || lower.contains("unavailable") || lower.contains("high demand") ->
                    "Сервер Gemini Live временно перегружен. Подождите несколько секунд и нажмите «Повторить подключение»."

                t is SocketTimeoutException || lower.contains("timed out") || lower.contains("timeout") ->
                    "Превышено время ожидания соединения с Gemini Live. Проверьте интернет и нажмите «Повторить подключение»."

                t is EOFException || t is SocketException ||
                    lower.contains("connection abort") || lower.contains("broken pipe") || lower.contains("connection reset") ->
                    "Соединение с Давидом неожиданно прервалось. Проверьте сеть и нажмите «Повторить подключение»."

                closingCode != null ->
                    "Соединение с Давидом закрыто (код $closingCode). Нажмите «Повторить подключение» для продолжения разговора."

                else ->
                    "Ошибка соединения с Gemini Live. Проверьте интернет и нажмите «Повторить подключение»."
            }
        }
    }
}
