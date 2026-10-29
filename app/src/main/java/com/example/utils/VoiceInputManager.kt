package com.example.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.data.ParsedOperation
import com.example.data.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

enum class VoiceRecordingState {
    IDLE,
    REQUESTING_CONSENT,
    RECORDING,
    PROCESSING,
    REVIEW,
    MANUAL_INPUT,
    SUCCESS,
    ERROR,
    MISSING_API_KEY
}

/**
 * Manages hold-to-record voice input on the "+" button using Gemini Multimodal Live WebSocket
 * in text-only input transcription mode (`inputAudioTranscription`).
 *
 * Instead of recording to a temporary `.m4a` file and uploading it afterwards, this manager
 * streams 16kHz PCM audio directly over WebSocket while the user holds/records, receiving
 * real-time speech transcription without any spoken assistant response, and then parses the
 * recognized text into structured financial operations.
 */
class VoiceInputManager(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val onOperationsConfirmed: (List<ParsedOperation>) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(VoiceRecordingState.IDLE)
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _spectrumBands = MutableStateFlow(List(24) { 0f })
    val spectrumBands: StateFlow<List<Float>> = _spectrumBands.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _parsedOperations = MutableStateFlow<List<ParsedOperation>>(emptyList())
    val parsedOperations: StateFlow<List<ParsedOperation>> = _parsedOperations.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var startTime: Long = 0
    private var currentExpenseCategories: List<String> = emptyList()
    private var currentIncomeCategories: List<String> = emptyList()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private val isLiveCapturing = AtomicBoolean(false)
    private val setupCompleted = AtomicBoolean(false)
    private var modelFallbackIndex = 0
    private var activeApiKey: String = ""

    private val finalizedTranscriptBuilder = StringBuilder()
    private val currentTurnTranscriptBuilder = StringBuilder()
    private val preSetupAudioQueue = ArrayDeque<String>()

    companion object {
        private const val INPUT_SAMPLE_RATE = 16000
        private const val INPUT_FRAME_SAMPLES = 1600 // 100ms at 16kHz
        private const val SPECTRUM_BANDS_COUNT = 24
        private const val MAX_PRE_SETUP_CHUNKS = 150 // Up to 15s buffered while WS handshakes

        val LIVE_STT_MODEL_CANDIDATES = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.5-flash-native-audio-preview-09-2025",
            "models/gemini-2.0-flash-live-001",
            "models/gemini-2.0-flash-exp"
        )

        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        internal fun buildLiveTranscriptionSetupJson(modelName: String): JSONObject {
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

        internal fun sanitizeTranscriptText(raw: String): String {
            return raw
                .replace(Regex("\\*\\*[^*]+\\*\\*"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    fun updateCategories(expenseCategories: List<String>, incomeCategories: List<String>) {
        currentExpenseCategories = expenseCategories
        currentIncomeCategories = incomeCategories
    }

    fun startRecording() {
        if (_state.value == VoiceRecordingState.RECORDING ||
            _state.value == VoiceRecordingState.PROCESSING ||
            _state.value == VoiceRecordingState.REQUESTING_CONSENT
        ) return

        scope.launch {
            val hasConsent = settingsManager.geminiConsentFlow.first()
            if (!hasConsent) {
                _state.value = VoiceRecordingState.REQUESTING_CONSENT
                return@launch
            }

            val apiKey = settingsManager.geminiApiKeyFlow.first().trim()
            if (apiKey.isEmpty()) {
                _state.value = VoiceRecordingState.MISSING_API_KEY
                return@launch
            }

            startRecordingInternal(apiKey)
        }
    }

    fun acceptConsentAndStartRecording() {
        scope.launch {
            settingsManager.saveGeminiConsent(true)
            val apiKey = settingsManager.geminiApiKeyFlow.first().trim()
            if (apiKey.isEmpty()) {
                _state.value = VoiceRecordingState.MISSING_API_KEY
            } else {
                startRecordingInternal(apiKey)
            }
        }
    }

    fun declineConsent() {
        _state.value = VoiceRecordingState.IDLE
    }

    private fun startRecordingInternal(apiKey: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _errorMessage.value = "Нет разрешения на запись звука"
            _state.value = VoiceRecordingState.ERROR
            return
        }

        stopLiveCaptureInternal()

        activeApiKey = apiKey
        modelFallbackIndex = 0
        synchronized(finalizedTranscriptBuilder) {
            finalizedTranscriptBuilder.clear()
            currentTurnTranscriptBuilder.clear()
        }
        synchronized(preSetupAudioQueue) {
            preSetupAudioQueue.clear()
        }

        _recognizedText.value = ""
        _errorMessage.value = null
        startTime = System.currentTimeMillis()
        isLiveCapturing.set(true)
        setupCompleted.set(false)
        _state.value = VoiceRecordingState.RECORDING

        startMicrophoneCaptureLoop()
        connectLiveWebSocket(LIVE_STT_MODEL_CANDIDATES[modelFallbackIndex])
    }

    private fun connectLiveWebSocket(modelName: String) {
        if (!isLiveCapturing.get()) return

        setupCompleted.set(false)
        val wsUrl = "$WS_BASE_URL?key=$activeApiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!isLiveCapturing.get()) {
                        webSocket.close(1000, "Recording stopped")
                        return
                    }
                    val setupJson = buildLiveTranscriptionSetupJson(modelName)
                    webSocket.send(setupJson.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleLiveServerMessage(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    handleLiveServerMessage(webSocket, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!isLiveCapturing.get()) return
                    if (!setupCompleted.get() && modelFallbackIndex + 1 < LIVE_STT_MODEL_CANDIDATES.size) {
                        modelFallbackIndex++
                        connectLiveWebSocket(LIVE_STT_MODEL_CANDIDATES[modelFallbackIndex])
                    } else if (code != 1000) {
                        scope.launch {
                            _errorMessage.value = "Ошибка Live-транскрипции ($code): $reason"
                            _state.value = VoiceRecordingState.ERROR
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!isLiveCapturing.get()) return
                    if (!setupCompleted.get() && modelFallbackIndex + 1 < LIVE_STT_MODEL_CANDIDATES.size) {
                        modelFallbackIndex++
                        connectLiveWebSocket(LIVE_STT_MODEL_CANDIDATES[modelFallbackIndex])
                    } else {
                        scope.launch {
                            _errorMessage.value = "Ошибка соединения Live: ${t.localizedMessage ?: "проверьте интернет"}"
                            _state.value = VoiceRecordingState.ERROR
                        }
                    }
                }
            }
        )
    }

    internal fun handleLiveServerMessage(ws: WebSocket?, rawJson: String) {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return
        }

        if (root.has("setupComplete")) {
            setupCompleted.set(true)
            flushPreSetupAudioQueue(ws)
            return
        }

        // Ignore tool calls and any assistant responses — this session is purely speech-to-text.
        if (root.has("toolCall")) return

        val serverContent = root.optJSONObject("serverContent") ?: return

        val inputTranscription = serverContent.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }

        if (inputTranscription != null) {
            val combined = synchronized(finalizedTranscriptBuilder) {
                currentTurnTranscriptBuilder.append(inputTranscription)
                buildCombinedTranscriptLocked()
            }
            if (combined.isNotBlank()) {
                _recognizedText.value = combined
            }
        }

        if (serverContent.optBoolean("turnComplete", false) || serverContent.optBoolean("interrupted", false)) {
            val combined = synchronized(finalizedTranscriptBuilder) {
                val turnText = sanitizeTranscriptText(currentTurnTranscriptBuilder.toString())
                if (turnText.isNotBlank()) {
                    if (finalizedTranscriptBuilder.isNotEmpty()) {
                        finalizedTranscriptBuilder.append(' ')
                    }
                    finalizedTranscriptBuilder.append(turnText)
                }
                currentTurnTranscriptBuilder.clear()
                buildCombinedTranscriptLocked()
            }
            if (combined.isNotBlank()) {
                _recognizedText.value = combined
            }
        }
    }

    private fun buildCombinedTranscriptLocked(): String {
        val finalized = finalizedTranscriptBuilder.toString().trim()
        val current = sanitizeTranscriptText(currentTurnTranscriptBuilder.toString())
        return when {
            finalized.isEmpty() -> current
            current.isEmpty() -> finalized
            else -> "$finalized $current".replace(Regex("\\s+"), " ").trim()
        }
    }

    private fun flushPreSetupAudioQueue(ws: WebSocket?) {
        if (ws == null) return
        val pendingChunks = mutableListOf<String>()
        synchronized(preSetupAudioQueue) {
            while (preSetupAudioQueue.isNotEmpty()) {
                pendingChunks.add(preSetupAudioQueue.removeFirst())
            }
        }
        for (b64 in pendingChunks) {
            sendPcmChunkOverWebSocket(ws, b64)
        }
    }

    private fun sendPcmChunkOverWebSocket(ws: WebSocket, base64Audio: String) {
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
    }

    @SuppressLint("MissingPermission")
    private fun startMicrophoneCaptureLoop() {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val minBufSize = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(INPUT_FRAME_SAMPLES * 2)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufSize * 2
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Не удалось инициализировать микрофон: ${e.localizedMessage}"
                    _state.value = VoiceRecordingState.ERROR
                }
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Микрофон недоступен или занят другим приложением"
                    _state.value = VoiceRecordingState.ERROR
                }
                return@launch
            }

            audioRecord = record
            attachAudioEffects(record.audioSessionId)

            val shortBuffer = ShortArray(INPUT_FRAME_SAMPLES)
            val byteBuffer = ByteArray(INPUT_FRAME_SAMPLES * 2)
            val smoothedBands = FloatArray(SPECTRUM_BANDS_COUNT)

            try {
                record.startRecording()
                while (isActive && isLiveCapturing.get()) {
                    val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        updateAmplitudeAndSpectrum(shortBuffer, readCount, smoothedBands)

                        for (i in 0 until readCount) {
                            val s = shortBuffer[i].toInt()
                            byteBuffer[i * 2] = (s and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                        }
                        val payloadBytes = if (readCount == shortBuffer.size) {
                            byteBuffer
                        } else {
                            byteBuffer.copyOf(readCount * 2)
                        }
                        val b64 = Base64.encodeToString(payloadBytes, Base64.NO_WRAP)

                        val ws = webSocket
                        if (setupCompleted.get() && ws != null) {
                            sendPcmChunkOverWebSocket(ws, b64)
                        } else {
                            synchronized(preSetupAudioQueue) {
                                if (preSetupAudioQueue.size >= MAX_PRE_SETUP_CHUNKS) {
                                    preSetupAudioQueue.removeFirst()
                                }
                                preSetupAudioQueue.addLast(b64)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                releaseAudioRecordInternal()
            }
        }
    }

    private fun updateAmplitudeAndSpectrum(
        samples: ShortArray,
        length: Int,
        smoothedBands: FloatArray
    ) {
        if (length <= 0) return
        var sumSq = 0.0
        for (i in 0 until length) {
            val norm = samples[i] / 32768.0
            sumSq += norm * norm
        }
        val rms = sqrt(sumSq / length).toFloat()
        val normalizedAmp = (rms * 4.5f).coerceIn(0f, 1f)
        _amplitude.value = _amplitude.value * 0.35f + normalizedAmp * 0.65f

        val samplesPerBand = (length / SPECTRUM_BANDS_COUNT).coerceAtLeast(1)
        val bandList = MutableList(SPECTRUM_BANDS_COUNT) { 0f }
        for (b in 0 until SPECTRUM_BANDS_COUNT) {
            val startIdx = b * samplesPerBand
            val endIdx = (startIdx + samplesPerBand).coerceAtMost(length)
            var bandEnergy = 0f
            var zeroCrossings = 0
            var prev = 0
            for (i in startIdx until endIdx) {
                val v = samples[i].toInt()
                bandEnergy += abs(v) / 32768f
                if ((v >= 0 && prev < 0) || (v < 0 && prev >= 0)) {
                    zeroCrossings++
                }
                prev = v
            }
            val count = (endIdx - startIdx).coerceAtLeast(1)
            val avgEnergy = (bandEnergy / count) * (3.2f + (zeroCrossings.toFloat() / count) * 1.8f)
            val target = ( normalizedAmp * 0.4f + avgEnergy * 0.6f ).coerceIn(0.05f, 1f)
            val factor = if (target > smoothedBands[b]) 0.6f else 0.3f
            smoothedBands[b] = (smoothedBands[b] * (1f - factor) + target * factor).coerceIn(0.05f, 1f)
            bandList[b] = smoothedBands[b]
        }
        _spectrumBands.value = bandList
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {}
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {}
        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {}
    }

    private fun releaseAudioRecordInternal() {
        try { echoCanceler?.release() } catch (_: Exception) {}
        echoCanceler = null
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        noiseSuppressor = null
        try { gainControl?.release() } catch (_: Exception) {}
        gainControl = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    fun stopRecordingAndProcess() {
        if (_state.value != VoiceRecordingState.RECORDING) return

        val duration = System.currentTimeMillis() - startTime
        if (duration < 500) {
            stopLiveCaptureInternal()
            _errorMessage.value = "Запись слишком короткая. Удерживайте кнопку и проговорите операцию."
            _state.value = VoiceRecordingState.ERROR
            return
        }

        _state.value = VoiceRecordingState.PROCESSING
        _amplitude.value = 0f
        _spectrumBands.value = List(SPECTRUM_BANDS_COUNT) { 0f }

        scope.launch {
            // Stop capturing new mic frames, send end-of-turn signal to flush final live transcription chunks
            isLiveCapturing.set(false)
            captureJob?.cancelAndJoin()
            releaseAudioRecordInternal()

            val ws = webSocket
            if (setupCompleted.get() && ws != null) {
                flushPreSetupAudioQueue(ws)
                try {
                    val turnCompleteMsg = JSONObject().apply {
                        put("clientContent", JSONObject().apply { put("turnComplete", true) })
                    }
                    ws.send(turnCompleteMsg.toString())
                } catch (_: Exception) {}

                // Wait briefly (up to 900ms) for Gemini Live to deliver trailing inputTranscription chunks
                var waitedMs = 0
                while (waitedMs < 900) {
                    delay(150)
                    waitedMs += 150
                    if (_recognizedText.value.isNotBlank() && waitedMs >= 450) break
                }
            }

            closeWebSocketInternal()

            val finalTranscript = synchronized(finalizedTranscriptBuilder) {
                buildCombinedTranscriptLocked()
            }.ifBlank { _recognizedText.value.trim() }

            if (finalTranscript.isBlank()) {
                _errorMessage.value = "Не удалось расслышать речь. Попробуйте сказать чуть громче или введите текстом."
                _state.value = VoiceRecordingState.ERROR
            } else {
                _recognizedText.value = finalTranscript
                parseRecognizedTextWithGemini(finalTranscript)
            }
        }
    }

    fun cancelRecording() {
        stopLiveCaptureInternal()
        _state.value = VoiceRecordingState.IDLE
        _amplitude.value = 0f
        _spectrumBands.value = List(SPECTRUM_BANDS_COUNT) { 0f }
        _recognizedText.value = ""
        _parsedOperations.value = emptyList()
        _errorMessage.value = null
    }

    private fun stopLiveCaptureInternal() {
        isLiveCapturing.set(false)
        setupCompleted.set(false)
        captureJob?.cancel()
        captureJob = null
        releaseAudioRecordInternal()
        closeWebSocketInternal()
        synchronized(preSetupAudioQueue) {
            preSetupAudioQueue.clear()
        }
    }

    private fun closeWebSocketInternal() {
        try {
            webSocket?.close(1000, "Voice input completed")
        } catch (_: Exception) {}
        webSocket = null
    }

    fun switchToManualInput() {
        stopLiveCaptureInternal()
        _amplitude.value = 0f
        _spectrumBands.value = List(SPECTRUM_BANDS_COUNT) { 0f }
        _errorMessage.value = null
        _state.value = VoiceRecordingState.MANUAL_INPUT
    }

    fun processManualText(text: String) {
        if (text.isBlank()) {
            _errorMessage.value = "Введите текст операции"
            _state.value = VoiceRecordingState.ERROR
            return
        }
        _recognizedText.value = text.trim()
        _state.value = VoiceRecordingState.PROCESSING
        scope.launch {
            val hasConsent = settingsManager.geminiConsentFlow.first()
            if (!hasConsent) {
                _state.value = VoiceRecordingState.REQUESTING_CONSENT
                return@launch
            }
            parseRecognizedTextWithGemini(text.trim())
        }
    }

    fun saveApiKeyAndRetry(apiKey: String) {
        scope.launch {
            settingsManager.saveGeminiApiKey(apiKey.trim())
            _state.value = VoiceRecordingState.IDLE
        }
    }

    private suspend fun parseRecognizedTextWithGemini(transcriptText: String) {
        withContext(Dispatchers.IO) {
            try {
                val apiKey = settingsManager.geminiApiKeyFlow.first().trim()
                if (apiKey.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _state.value = VoiceRecordingState.MISSING_API_KEY
                    }
                    return@withContext
                }

                val expenseCatsStr = if (currentExpenseCategories.isNotEmpty()) {
                    currentExpenseCategories.joinToString(", ")
                } else {
                    "Еда, Транспорт, Жилье, Развлечения, Здоровье, Одежда, Связь, Подарки, Другое"
                }

                val incomeCatsStr = if (currentIncomeCategories.isNotEmpty()) {
                    currentIncomeCategories.joinToString(", ")
                } else {
                    "Зарплата, Фриланс, Подарки, Инвестиции, Возврат, Другое"
                }

                val prompt = VoiceParserHelper.buildTextParsingPrompt(transcriptText, expenseCatsStr, incomeCatsStr)

                val requestJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("responseMimeType", "application/json")
                    })
                }

                val responseText = executeGeminiGenerateContentWithFallback(apiKey, requestJson.toString())

                val jsonResponse = JSONObject(responseText)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    throw Exception("Пустой ответ от ИИ")
                }

                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val rawJsonText = parts?.getJSONObject(0)?.optString("text") ?: ""

                val parseResult = VoiceParserHelper.parseGeminiJsonResponse(
                    rawJsonText = rawJsonText,
                    expenseCategories = currentExpenseCategories,
                    incomeCategories = currentIncomeCategories,
                    fallbackTranscript = transcriptText
                )

                withContext(Dispatchers.Main) {
                    _recognizedText.value = parseResult.transcript.ifBlank { transcriptText }
                    if (parseResult.operations.isEmpty()) {
                        _errorMessage.value = "Не удалось распознать финансовые операции в фразе: «$transcriptText»"
                        _state.value = VoiceRecordingState.ERROR
                    } else {
                        _parsedOperations.value = parseResult.operations
                        _state.value = VoiceRecordingState.REVIEW
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Ошибка обработки: ${e.localizedMessage ?: "Неизвестная ошибка"}"
                    _state.value = VoiceRecordingState.ERROR
                }
            }
        }
    }

    private fun executeGeminiGenerateContentWithFallback(apiKey: String, jsonBody: String): String {
        val models = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
        var lastException: Exception? = null

        for (model in models) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody)
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val bodyString = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    return bodyString
                } else {
                    lastException = Exception("HTTP ${response.code}: $bodyString")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("Не удалось подключиться к Gemini API")
    }

    fun updateOperation(index: Int, updated: ParsedOperation) {
        val current = _parsedOperations.value.toMutableList()
        if (index in current.indices) {
            current[index] = updated
            _parsedOperations.value = current
        }
    }

    fun removeOperation(index: Int) {
        val current = _parsedOperations.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            if (current.isEmpty()) {
                cancelRecording()
            } else {
                _parsedOperations.value = current
            }
        }
    }

    fun addBlankOperation() {
        val current = _parsedOperations.value.toMutableList()
        val defaultCategory = currentExpenseCategories.firstOrNull() ?: "Другое"
        current.add(
            ParsedOperation(
                amount = 0.0,
                category = defaultCategory,
                note = "Новая запись",
                isIncome = false,
                confidence = 1.0f
            )
        )
        _parsedOperations.value = current
    }

    fun confirmOperations() {
        val ops = _parsedOperations.value.filter { it.amount > 0 }
        if (ops.isEmpty()) {
            _errorMessage.value = "Укажите сумму больше 0 хотя бы для одной операции"
            return
        }
        onOperationsConfirmed(ops)
        _state.value = VoiceRecordingState.SUCCESS
        scope.launch {
            delay(1200)
            cancelRecording()
        }
    }

    private fun String.toMediaTypeOrNull(): MediaType? = try {
        MediaType.get(this)
    } catch (_: Exception) {
        null
    }
}
