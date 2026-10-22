package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Потоковый менеджер голосового ввода в стиле Gemini Live.
 * Непрерывно считывает PCM-аудио (16 kHz, 16-bit, Mono) через AudioRecord без системных звуков,
 * вычисляет живую амплитуду для 32-полосной неоновой волны и с помощью умного детектора речи (VAD)
 * отправляет каждую законченную фразу (по паузе ~0.85 сек) в Gemini в реальном времени,
 * не прерывая запись микрофона.
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

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    var onErrorCallback: (() -> Unit)? = null
    var onAudioChunkReady: ((ByteArray) -> Unit)? = null

    private val chunkLock = Any()
    private var activeSpeechBuffer = ByteArrayOutputStream()
    private var activeSpeechFrames = 0

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SAMPLES = 512 // 32 мс при 16000 Гц
        private const val FRAME_BYTES = FRAME_SAMPLES * 2 // 1024 байта (16-bit PCM)
        private const val PRE_ROLL_FRAMES = 8 // ~256 мс предзаписи до начала фразы
        private const val SILENCE_FRAMES_TO_SPLIT = 26 // ~830 мс паузы для отправки готовой фразы в Gemini Live
        private const val MIN_SPEECH_FRAMES = 11 // ~350 мс минимальной длительности речи во фразе
        private const val MAX_CHUNK_FRAMES = 375 // ~12 секунд максимум на один непрерывный чанк
        private const val SPEECH_RMS_THRESHOLD = 360.0 // Порог чувствительности голоса (16-bit PCM)
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

    @SuppressLint("MissingPermission")
    fun startListening(callerContext: Context = context) {
        GlobalConsoleLogger.i("VOICE", "Запуск потокового режима Gemini Live...")
        stopInternal()

        _recognizedText.value = ""
        _partialText.value = "Говорите..."
        _errorState.value = null

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

                try {
                    while (isActive && _isListening.value) {
                        val readBytes = record.read(frameBuf, 0, FRAME_BYTES)
                        if (readBytes <= 0) continue

                        val frameCopy = frameBuf.copyOf(readBytes)
                        val rms = calculatePcmRms(frameCopy, readBytes)

                        // Плавная нормализация амплитуды (1..12) для 32-полосной неоновой волны
                        val visualDb = if (rms > 80.0) {
                            val db = 20f * log10((rms / 80.0).toFloat())
                            (db * 0.35f).coerceIn(1.5f, 12f)
                        } else {
                            1.0f
                        }
                        _rmsDb.value = visualDb

                        val isSpeechFrame = rms >= SPEECH_RMS_THRESHOLD

                        synchronized(chunkLock) {
                            if (isSpeechFrame) {
                                if (!isInSpeech) {
                                    isInSpeech = true
                                    silenceFramesCount = 0
                                    // Дописываем pre-roll буфер, чтобы не съедалось начало первого слова
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

                                    // Если пользователь сделал паузу (~0.83 сек) или фраза длится > 12 сек —
                                    // мгновенно отправляем готовый фрагмент в Gemini Live, не выключая микрофон!
                                    if (silenceFramesCount >= SILENCE_FRAMES_TO_SPLIT || totalFramesInChunk >= MAX_CHUNK_FRAMES) {
                                        if (activeSpeechFrames >= MIN_SPEECH_FRAMES) {
                                            val pcmData = activeSpeechBuffer.toByteArray()
                                            val wavBytes = pcmToWavBytes(pcmData, SAMPLE_RATE)
                                            GlobalConsoleLogger.i("VOICE_LIVE", "Фраза завершена по паузе (${pcmData.size} байт), отправка в Gemini Live...")
                                            onAudioChunkReady?.invoke(wavBytes)
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

    /**
     * Останавливает запись и возвращает последний оставшийся фрагмент речи (если пользователь говорил
     * прямо перед нажатием кнопки завершения), чтобы ни одно слово в конце не потерялось.
     */
    fun stopListeningAndFlushRemaining(): ByteArray? {
        GlobalConsoleLogger.i("VOICE", "Завершение сессии Gemini Live и досылка остатка буфера...")
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

        synchronized(chunkLock) {
            val remainingPcm = activeSpeechBuffer.toByteArray()
            val frames = activeSpeechFrames
            activeSpeechBuffer.reset()
            activeSpeechFrames = 0

            return if (frames >= 8 && remainingPcm.size > FRAME_BYTES * 6) {
                GlobalConsoleLogger.i("VOICE_LIVE", "Отправка финального фрагмента речи (${remainingPcm.size} байт)")
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

    private fun stopInternal() {
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

        synchronized(chunkLock) {
            activeSpeechBuffer.reset()
            activeSpeechFrames = 0
        }
        _rmsDb.value = 0f
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
