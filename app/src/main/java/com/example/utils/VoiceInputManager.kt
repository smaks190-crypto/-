package com.example.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10

class VoiceInputManager(context: Context) {
    private val context: Context = context.applicationContext
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var recordingStartTimestamp: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

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
    var onChunkRecognized: ((String) -> Unit)? = null

    init {
        // Очистка старой тяжелой офлайн-модели VOSK (~50 МБ), если она осталась от прошлых версий
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val legacyVoskDir = File(this@VoiceInputManager.context.filesDir, "vosk-model-small-ru-0.22")
                if (legacyVoskDir.exists()) {
                    legacyVoskDir.deleteRecursively()
                    GlobalConsoleLogger.i("VOICE", "Удалена устаревшая офлайн-модель VOSK из памяти устройства")
                }
                val legacyZip = File(this@VoiceInputManager.context.cacheDir, "vosk_model.zip")
                if (legacyZip.exists()) {
                    legacyZip.delete()
                }
            } catch (_: Throwable) {}
        }
    }

    private val amplitudePollRunnable = object : Runnable {
        override fun run() {
            val recorder = mediaRecorder
            if (recorder != null && _isListening.value) {
                try {
                    val maxAmp = recorder.maxAmplitude // 0 .. 32767
                    val normalizedRms = if (maxAmp > 80) {
                        val db = 20f * log10(maxAmp.toFloat() / 100f)
                        ((db - 8f) / 3.2f).coerceIn(1.2f, 12f)
                    } else {
                        1.0f
                    }
                    _rmsDb.value = normalizedRms
                } catch (_: Throwable) {
                    _rmsDb.value = 1.0f
                }
                mainHandler.postDelayed(this, 40L)
            }
        }
    }

    fun startListening(callerContext: Context = context) {
        GlobalConsoleLogger.i("VOICE", "Запуск прямой аудиозаписи для Gemini...")
        stopRecorderSafely(deleteFile = true)

        _recognizedText.value = ""
        _partialText.value = ""
        _errorState.value = null

        try {
            val outputFile = File(context.cacheDir, "gemini_voice_input.m4a")
            if (outputFile.exists()) {
                outputFile.delete()
            }
            currentAudioFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(callerContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(16000)
            recorder.setAudioEncodingBitRate(32000)
            recorder.setOutputFile(outputFile.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            recordingStartTimestamp = System.currentTimeMillis()
            _isListening.value = true
            _rmsDb.value = 1.5f

            mainHandler.removeCallbacks(amplitudePollRunnable)
            mainHandler.post(amplitudePollRunnable)

            GlobalConsoleLogger.i("VOICE", "Запись аудио в файл ${outputFile.name} успешно начата")
        } catch (e: Throwable) {
            Log.e("VoiceInputManager", "MediaRecorder start error", e)
            GlobalConsoleLogger.e("VOICE", "Ошибка запуска записи микрофона: ${e.localizedMessage}", e)
            _errorState.value = "Ошибка запуска микрофона: ${e.localizedMessage}"
            _isListening.value = false
            stopRecorderSafely(deleteFile = true)
            onErrorCallback?.invoke()
        }
    }

    fun stopListeningAndGetAudioFile(): File? {
        GlobalConsoleLogger.i("VOICE", "Завершение записи аудио и подготовка файла для Gemini")
        mainHandler.removeCallbacks(amplitudePollRunnable)
        val durationMs = System.currentTimeMillis() - recordingStartTimestamp
        val recorder = mediaRecorder
        mediaRecorder = null
        _isListening.value = false
        _rmsDb.value = 0f

        if (recorder != null) {
            try {
                recorder.stop()
            } catch (e: Throwable) {
                Log.w("VoiceInputManager", "MediaRecorder stop warning (too short or interrupted): ${e.message}")
            } finally {
                try {
                    recorder.release()
                } catch (_: Throwable) {}
            }
        }

        val file = currentAudioFile
        return if (file != null && file.exists() && file.length() > 256L && durationMs >= 300L) {
            GlobalConsoleLogger.i("VOICE", "Аудиофайл готов: ${file.length()} байт (${durationMs} мс)")
            file
        } else {
            GlobalConsoleLogger.w("VOICE", "Запись слишком короткая (${durationMs} мс), файл отброшен")
            try { file?.delete() } catch (_: Throwable) {}
            currentAudioFile = null
            null
        }
    }

    fun stopListening() {
        GlobalConsoleLogger.i("VOICE", "Отмена/остановка записи речи")
        stopRecorderSafely(deleteFile = true)
    }

    private fun stopRecorderSafely(deleteFile: Boolean) {
        mainHandler.removeCallbacks(amplitudePollRunnable)
        val recorder = mediaRecorder
        mediaRecorder = null
        _isListening.value = false
        _rmsDb.value = 0f

        if (recorder != null) {
            try {
                recorder.stop()
            } catch (_: Throwable) {
            } finally {
                try {
                    recorder.release()
                } catch (_: Throwable) {}
            }
        }

        if (deleteFile) {
            try {
                currentAudioFile?.delete()
            } catch (_: Throwable) {}
            currentAudioFile = null
        }
    }

    fun clear() {
        _recognizedText.value = ""
        _partialText.value = ""
        _errorState.value = null
    }

    fun setRecognizedTextManually(text: String) {
        _recognizedText.value = text
        _partialText.value = ""
    }

    fun destroy() {
        stopRecorderSafely(deleteFile = true)
    }
}
