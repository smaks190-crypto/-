package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Потоковый менеджер голосового ввода в стиле Gemini Live (`BidiGenerateContent`).
 *
 * Работает в режиме только текстовой транскрипции речи пользователя (`responseModalities: ["TEXT"]`,
 * `inputAudioTranscription: {}`):
 * - Непрерывно считывает PCM-аудио (16 kHz, 16-bit, Mono) через AudioRecord без системных звуков
 *   и без сохранения во временные файлы.
 * - Стримит аудиочанки по WebSocket в Gemini Live и в реальном времени получает `inputTranscription`,
 *   обновляя `partialText` и `recognizedText` прямо во время речи.
 * - Полностью игнорирует любые ответы ассистента (`outputTranscription`, `modelTurn`, `toolCall`),
 *   чтобы сессия работала строго как живой рекордер-транскрибатор для добавления операций.
 * - По завершении фразы (`turnComplete` или паузе речи) передаёт распознанный текст в `onChunkRecognized`.
 */
class VoiceInputManager(context: Context) {
    private val context: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    var onErrorCallback: (() -> Unit)? = null
    var onAudioChunkReady: ((ByteArray) -> Unit)? = null
    var onChunkRecognized: ((String) -> Unit)? = null

    private val chunkLock = Any()
    private var activeSpeechBuffer = ByteArrayOutputStream()
    private var activeSpeechFrames = 0

    // --- Gemini Live WebSocket Text-Only STT State ---
    private val liveHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var liveWebSocket: WebSocket? = null
    private val liveSetupCompleted = AtomicBoolean(false)
    private val liveIntentionalClose = AtomicBoolean(false)
    private var liveModelAttemptIndex = 0
    private var currentLiveApiKey: String = ""
    private val pendingLiveTurnTranscript = StringBuilder()
    private val preSetupPcmQueue = ArrayDeque<String>(64)

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SAMPLES = 512 // 32 мс при 16000 Гц
        private const val FRAME_BYTES = FRAME_SAMPLES * 2 // 1024 байта (16-bit PCM)
        private const val PRE_ROLL_FRAMES = 8 // ~256 мс предзаписи до начала фразы
        private const val SILENCE_FRAMES_TO_SPLIT = 26 // ~830 мс паузы для отправки готовой фразы в Gemini Live
        private const val MIN_SPEECH_FRAMES = 11 // ~350 мс минимальной длительности речи во фразе
        private const val MAX_CHUNK_FRAMES = 375 // ~12 секунд максимум на один непрерывный чанк
        private const val SPEECH_RMS_THRESHOLD = 360.0 // Порог чувствительности голоса (16-bit PCM)

        private const val GEMINI_LIVE_WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        val LIVE_STT_MODELS = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.0-flash-live-001",
            "models/gemini-2.0-flash-exp"
        )

        /**
         * Конфигурация сессии Gemini Live строго для распознавания речи пользователя без ответов ИИ.
         */
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

    init {
        // Очистка старой тяжелой офлайн-модели VOSK (~50 МБ), если она осталась от прошлых версий
        scope.launch {
            try {
                val legacyVoskDir = File(this@VoiceInputManager.context.filesDir, "vosk-model-small-ru-0.22")
                if (legacyVoskDir.exists()) {
                    legacyVoskDir.deleteRecursively()
                }
                val legacyZip = File(this@VoiceInputManager.context.cacheDir, "vosk_model.zip")
                if (legacyZip.exists()) {
                    legacyZip.delete()
                }
            } catch (_: Throwable) {}
        }
    }

    private fun resolveUserApiKey(callerContext: Context): String {
        return try {
            val prefs = callerContext.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
            (prefs.getString("gemini_api_key", "") ?: "").trim()
        } catch (_: Throwable) {
            ""
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(callerContext: Context = context) {
        GlobalConsoleLogger.i("VOICE", "Запуск потокового режима Gemini Live STT (text-only)...")
        stopInternal()

        _recognizedText.value = ""
        _partialText.value = "Говорите..."
        _errorState.value = null
        synchronized(pendingLiveTurnTranscript) {
            pendingLiveTurnTranscript.clear()
        }
        synchronized(preSetupPcmQueue) {
            preSetupPcmQueue.clear()
        }

        currentLiveApiKey = resolveUserApiKey(callerContext)
        if (currentLiveApiKey.isNotBlank()) {
            liveIntentionalClose.set(false)
            liveModelAttemptIndex = 0
            connectLiveSttWebSocket(modelIndex = 0)
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 2, FRAME_BYTES * 8)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("Микрофон не удалось инициализировать")
            }

            record.startRecording()
            audioRecord = record
            _isListening.value = true
            _rmsDb.value = 1.5f
            _voiceLevel.value = 0.08f

            synchronized(chunkLock) {
                activeSpeechBuffer.reset()
                activeSpeechFrames = 0
            }

            recordingJob = scope.launch {
                val frameBuf = ByteArray(FRAME_BYTES)
                val preRollQueue = ArrayDeque<ByteArray>(PRE_ROLL_FRAMES + 2)
                var isInSpeech = false
                var silenceFramesCount = 0
                var totalFramesInChunk = 0
                val wsBatchBuffer = ByteArrayOutputStream(FRAME_BYTES * 3)
                var wsBatchCount = 0

                try {
                    while (isActive && _isListening.value) {
                        val readBytes = record.read(frameBuf, 0, FRAME_BYTES)
                        if (readBytes <= 0) continue

                        val frameCopy = frameBuf.copyOf(readBytes)
                        val rms = calculatePcmRms(frameCopy, readBytes)

                        // Плавная нормализация амплитуды (1..12) и уровня голоса (0f..1f) для Volumetric Liquid Aurora
                        val visualDb = if (rms > 80.0) {
                            val db = 20f * log10((rms / 80.0).toFloat())
                            (db * 0.35f).coerceIn(1.5f, 12f)
                        } else {
                            1.0f
                        }
                        _rmsDb.value = visualDb
                        _voiceLevel.value = ((visualDb - 1.0f) / 9.5f).coerceIn(0f, 1f)

                        // Потоковая отправка PCM-аудио в Gemini Live WebSocket (пакетами по ~96 мс)
                        wsBatchBuffer.write(frameCopy)
                        wsBatchCount++
                        if (wsBatchCount >= 3) {
                            streamPcmFrameToLiveWebSocket(wsBatchBuffer.toByteArray())
                            wsBatchBuffer.reset()
                            wsBatchCount = 0
                        }

                        val isSpeechFrame = rms >= SPEECH_RMS_THRESHOLD

                        synchronized(chunkLock) {
                            if (isSpeechFrame) {
                                if (!isInSpeech) {
                                    isInSpeech = true
                                    silenceFramesCount = 0
                                    for (preFrame in preRollQueue) {
                                        activeSpeechBuffer.write(preFrame)
                                    }
                                    preRollQueue.clear()
                                    if (_partialText.value.isBlank() || _partialText.value == "Говорите...") {
                                        _partialText.value = "Слушаю..."
                                    }
                                }
                                activeSpeechBuffer.write(frameCopy)
                                activeSpeechFrames++
                                totalFramesInChunk++
                                silenceFramesCount = 0
                            } else {
                                if (isInSpeech) {
                                    activeSpeechBuffer.write(frameCopy)
                                    silenceFramesCount++
                                    totalFramesInChunk++

                                    // Если пользователь сделал паузу (~0.83 сек) или фраза длится > 12 сек:
                                    if (silenceFramesCount >= SILENCE_FRAMES_TO_SPLIT || totalFramesInChunk >= MAX_CHUNK_FRAMES) {
                                        if (activeSpeechFrames >= MIN_SPEECH_FRAMES) {
                                            val liveTextSoFar = synchronized(pendingLiveTurnTranscript) {
                                                sanitizeTranscriptText(pendingLiveTurnTranscript.toString())
                                            }
                                            if (liveSetupCompleted.get() && liveTextSoFar.isNotBlank()) {
                                                // В режиме Live STT отправляем распознанный текст без создания WAV-файла!
                                                synchronized(pendingLiveTurnTranscript) {
                                                    pendingLiveTurnTranscript.clear()
                                                }
                                                GlobalConsoleLogger.i("VOICE_LIVE", "Фраза распознана в Live STT по паузе: «$liveTextSoFar»")
                                                onChunkRecognized?.invoke(liveTextSoFar)
                                            } else if (!liveSetupCompleted.get()) {
                                                // Резервный fallback на случай, если WebSocket еще не установил соединение
                                                val pcmData = activeSpeechBuffer.toByteArray()
                                                val wavBytes = pcmToWavBytes(pcmData, SAMPLE_RATE)
                                                onAudioChunkReady?.invoke(wavBytes)
                                            }
                                        }
                                        activeSpeechBuffer.reset()
                                        activeSpeechFrames = 0
                                        totalFramesInChunk = 0
                                        silenceFramesCount = 0
                                        isInSpeech = false
                                    }
                                } else {
                                    if (preRollQueue.size >= PRE_ROLL_FRAMES) {
                                        preRollQueue.removeFirst()
                                    }
                                    preRollQueue.addLast(frameCopy)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("VoiceInputManager", "AudioRecord loop error", e)
                }
            }
        } catch (e: Throwable) {
            Log.e("VoiceInputManager", "Failed to start AudioRecord", e)
            GlobalConsoleLogger.e("VOICE", "Ошибка запуска Gemini Live микрофона: ${e.localizedMessage}", e)
            _errorState.value = "Ошибка микрофона: ${e.localizedMessage}"
            _isListening.value = false
            stopInternal()
            onErrorCallback?.invoke()
        }
    }

    private fun connectLiveSttWebSocket(modelIndex: Int) {
        if (currentLiveApiKey.isBlank()) return
        liveSetupCompleted.set(false)
        try {
            liveWebSocket?.cancel()
        } catch (_: Throwable) {}

        val modelName = LIVE_STT_MODELS.getOrElse(modelIndex) { LIVE_STT_MODELS.last() }
        val wsUrl = "$GEMINI_LIVE_WS_ENDPOINT?key=$currentLiveApiKey"
        val request = Request.Builder().url(wsUrl).build()

        liveWebSocket = liveHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (liveIntentionalClose.get()) {
                        webSocket.close(1000, "Stopped")
                        return
                    }
                    val setupMsg = buildLiveTranscriptionSetupJson(modelName)
                    webSocket.send(setupMsg.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleLiveServerJsonMessage(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleLiveServerJsonMessage(webSocket, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (!liveIntentionalClose.get() && !liveSetupCompleted.get() && liveModelAttemptIndex + 1 < LIVE_STT_MODELS.size) {
                        liveModelAttemptIndex++
                        connectLiveSttWebSocket(liveModelAttemptIndex)
                        return
                    }
                    try {
                        webSocket.close(code, reason)
                    } catch (_: Throwable) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (liveIntentionalClose.get()) return
                    if (!liveSetupCompleted.get() && liveModelAttemptIndex + 1 < LIVE_STT_MODELS.size) {
                        liveModelAttemptIndex++
                        connectLiveSttWebSocket(liveModelAttemptIndex)
                    }
                }
            }
        )
    }

    private fun streamPcmFrameToLiveWebSocket(pcm16kBytes: ByteArray) {
        if (currentLiveApiKey.isBlank() || pcm16kBytes.isEmpty()) return
        val b64 = Base64.encodeToString(pcm16kBytes, Base64.NO_WRAP)
        val ws = liveWebSocket
        if (liveSetupCompleted.get() && ws != null) {
            sendPcmBase64ToWebSocket(ws, b64)
        } else {
            synchronized(preSetupPcmQueue) {
                if (preSetupPcmQueue.size >= 50) {
                    preSetupPcmQueue.removeFirst()
                }
                preSetupPcmQueue.addLast(b64)
            }
        }
    }

    private fun flushPreSetupPcmQueue(ws: WebSocket?) {
        if (ws == null) return
        val buffered = mutableListOf<String>()
        synchronized(preSetupPcmQueue) {
            while (preSetupPcmQueue.isNotEmpty()) {
                buffered.add(preSetupPcmQueue.removeFirst())
            }
        }
        for (b64 in buffered) {
            sendPcmBase64ToWebSocket(ws, b64)
        }
    }

    private fun sendPcmBase64ToWebSocket(ws: WebSocket, base64Pcm: String) {
        val msg = JSONObject().apply {
            put(
                "realtimeInput",
                JSONObject().apply {
                    put(
                        "mediaChunks",
                        JSONArray().put(
                            JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=16000")
                                put("data", base64Pcm)
                            }
                        )
                    )
                }
            )
        }
        ws.send(msg.toString())
    }

    /**
     * Обрабатывает сообщения от Gemini Live в режиме текстовой транскрипции (доступен для unit-тестов).
     */
    internal fun processLiveServerMessageForTest(rawJson: String) {
        handleLiveServerJsonMessage(ws = null, rawJson = rawJson)
    }

    private fun handleLiveServerJsonMessage(ws: WebSocket?, rawJson: String) {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return
        }

        if (root.has("setupComplete")) {
            liveSetupCompleted.set(true)
            flushPreSetupPcmQueue(ws)
            return
        }

        // Игнорируем любые вызовы инструментов и ответы модели — этот режим только записывает речь пользователя.
        if (root.has("toolCall")) return

        val serverContent = root.optJSONObject("serverContent") ?: return

        val inputTranscription = serverContent.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }

        if (inputTranscription != null) {
            val cleaned = synchronized(pendingLiveTurnTranscript) {
                pendingLiveTurnTranscript.append(inputTranscription)
                sanitizeTranscriptText(pendingLiveTurnTranscript.toString())
            }
            if (cleaned.isNotBlank()) {
                _partialText.value = cleaned
                _recognizedText.value = cleaned
            }
        }

        if (serverContent.optBoolean("turnComplete", false)) {
            val completedTurnText = synchronized(pendingLiveTurnTranscript) {
                val text = sanitizeTranscriptText(pendingLiveTurnTranscript.toString())
                pendingLiveTurnTranscript.clear()
                text
            }
            if (completedTurnText.isNotBlank()) {
                _recognizedText.value = completedTurnText
                _partialText.value = completedTurnText
                synchronized(chunkLock) {
                    activeSpeechBuffer.reset()
                    activeSpeechFrames = 0
                }
                onChunkRecognized?.invoke(completedTurnText)
            }
        }
    }

    /**
     * Останавливает запись и возвращает последний оставшийся фрагмент речи (только если Live STT
     * ещё не успел распознать его в текст), чтобы ни одно слово в конце не потерялось.
     */
    fun stopListeningAndFlushRemaining(): ByteArray? {
        GlobalConsoleLogger.i("VOICE", "Завершение сессии Gemini Live STT...")
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null

        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    record.release()
                } catch (_: Throwable) {}
            }
        }
        _rmsDb.value = 0f
        _voiceLevel.value = 0f

        val pendingText = synchronized(pendingLiveTurnTranscript) {
            val t = sanitizeTranscriptText(pendingLiveTurnTranscript.toString())
            pendingLiveTurnTranscript.clear()
            t
        }
        if (pendingText.isNotBlank()) {
            _recognizedText.value = pendingText
            _partialText.value = pendingText
        }

        val hadLiveSetup = liveSetupCompleted.get()
        closeLiveWebSocket()

        // Если Live WebSocket уже распознал текст в реальном времени, возвращаем null,
        // чтобы TransactionStateDelegate сразу разобрал готовый текст без отправки WAV-файла!
        if (hadLiveSetup && (_recognizedText.value.isNotBlank() || pendingText.isNotBlank())) {
            synchronized(chunkLock) {
                activeSpeechBuffer.reset()
                activeSpeechFrames = 0
            }
            return null
        }

        synchronized(chunkLock) {
            val remainingPcm = activeSpeechBuffer.toByteArray()
            val frames = activeSpeechFrames
            activeSpeechBuffer.reset()
            activeSpeechFrames = 0

            return if (frames >= 8 && remainingPcm.size > FRAME_BYTES * 6) {
                GlobalConsoleLogger.i("VOICE_LIVE", "Fallback-отправка финального фрагмента речи (${remainingPcm.size} байт)")
                pcmToWavBytes(remainingPcm, SAMPLE_RATE)
            } else {
                null
            }
        }
    }

    fun stopListening() {
        GlobalConsoleLogger.i("VOICE", "Полная остановка записи речи")
        stopInternal()
    }

    private fun closeLiveWebSocket() {
        liveIntentionalClose.set(true)
        liveSetupCompleted.set(false)
        try {
            liveWebSocket?.close(1000, "Voice input finished")
        } catch (_: Throwable) {}
        liveWebSocket = null
        synchronized(preSetupPcmQueue) {
            preSetupPcmQueue.clear()
        }
    }

    private fun stopInternal() {
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null
        closeLiveWebSocket()

        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    record.release()
                } catch (_: Throwable) {}
            }
        }

        synchronized(chunkLock) {
            activeSpeechBuffer.reset()
            activeSpeechFrames = 0
        }
        _rmsDb.value = 0f
        _voiceLevel.value = 0f
    }

    private fun calculatePcmRms(buffer: ByteArray, length: Int): Double {
        var sumSquares = 0.0
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0.0
        var i = 0
        while (i < length - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
            i += 2
        }
        return sqrt(sumSquares / sampleCount)
    }

    private fun pcmToWavBytes(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono = 2 bytes per sample
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt subchunk
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1) // AudioFormat = 1 (PCM)
        header.putShort(1) // NumChannels = 1 (Mono)
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2) // BlockAlign = 2
        header.putShort(16) // BitsPerSample = 16

        // data subchunk
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        val wavOutput = ByteArray(44 + pcmData.size)
        System.arraycopy(header.array(), 0, wavOutput, 0, 44)
        System.arraycopy(pcmData, 0, wavOutput, 44, pcmData.size)
        return wavOutput
    }

    fun clear() {
        synchronized(pendingLiveTurnTranscript) {
            pendingLiveTurnTranscript.clear()
        }
        _recognizedText.value = ""
        _partialText.value = ""
        _errorState.value = null
    }

    fun setRecognizedTextManually(text: String) {
        _recognizedText.value = text
        _partialText.value = text
    }

    fun destroy() {
        stopInternal()
    }
}
