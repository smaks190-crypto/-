package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener as SystemRecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as SystemSpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class VoiceInputManager(private val context: Context) {
    private var systemSpeechRecognizer: SystemSpeechRecognizer? = null
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var voskSpeechService: SpeechService? = null
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

    // VOSK specific states
    private val _voskStatus = MutableStateFlow("") // "", "DOWNLOADING", "EXTRACTING", "READY", "ERROR"
    val voskStatus: StateFlow<String> = _voskStatus.asStateFlow()

    private val _voskProgress = MutableStateFlow<Float?>(null)
    val voskProgress: StateFlow<Float?> = _voskProgress.asStateFlow()

    private var accumulatedText = ""
    private var isContinuous = false
    private var isPaused = false
    @Volatile private var isProcessingAllowed = true
    private var lastProcessedChunk = ""

    private var activeContextRef: java.lang.ref.WeakReference<Context>? = null

    var onErrorCallback: (() -> Unit)? = null
    var onChunkRecognized: ((String) -> Unit)? = null

    private fun parsePartialHypothesis(json: String): String {
        return try {
            val regex = "\"partial\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            regex.find(json)?.groupValues?.get(1) ?: ""
        } catch (_: Throwable) { "" }
    }

    private fun parseResultHypothesis(json: String): String {
        return try {
            val regex = "\"text\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            regex.find(json)?.groupValues?.get(1) ?: ""
        } catch (_: Throwable) { "" }
    }

    fun startListening(callerContext: Context) {
        GlobalConsoleLogger.i("VOICE", "Запуск непрерывного прослушивания микрофона...")
        activeContextRef = java.lang.ref.WeakReference(callerContext)
        isContinuous = true
        isPaused = false
        isProcessingAllowed = true
        lastProcessedChunk = ""

        accumulatedText = ""
        _recognizedText.value = ""
        _partialText.value = ""
        _errorState.value = null

        val targetDir = File(context.filesDir, "vosk-model-small-ru-0.22")
        if (targetDir.exists() && targetDir.isDirectory && targetDir.list()?.isNotEmpty() == true) {
            _voskStatus.value = "READY"
            GlobalConsoleLogger.i("VOSK", "Найдена локальная офлайн-модель VOSK")
            initVoskAndStart(callerContext)
        } else {
            GlobalConsoleLogger.i("VOSK", "Модель VOSK не найдена локально, запускаем загрузку")
            downloadAndInitModel(callerContext)
        }
    }

    fun startListening() {
        activeContextRef = null
        isContinuous = true
        isPaused = false
        startListening(context)
    }

    private fun downloadAndInitModel(callerContext: Context) {
        _voskStatus.value = "DOWNLOADING"
        _voskProgress.value = 0f
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 20000
                connection.readTimeout = 20000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Ошибка загрузки модели с сервера VOSK code: ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val inputStream = BufferedInputStream(connection.inputStream)
                val tempZip = File(context.cacheDir, "vosk_model.zip")
                val outputStream = FileOutputStream(tempZip)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                while (inputStream.read(data).also { count = it } != -1) {
                    if (isPaused || !isContinuous) {
                        outputStream.close()
                        inputStream.close()
                        tempZip.delete()
                        _voskStatus.value = ""
                        _voskProgress.value = null
                        return@launch
                    }
                    total += count
                    if (fileLength > 0) {
                        _voskProgress.value = total.toFloat() / fileLength
                    }
                    outputStream.write(data, 0, count)
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                _voskStatus.value = "EXTRACTING"
                _voskProgress.value = null

                val buffer = ByteArray(8192)
                ZipInputStream(BufferedInputStream(tempZip.inputStream())).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(context.filesDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                tempZip.delete()

                _voskStatus.value = "READY"
                withContext(Dispatchers.Main) {
                    initVoskAndStart(callerContext)
                }
            } catch (e: Exception) {
                _voskStatus.value = "ERROR"
                _voskProgress.value = null
                _errorState.value = "Не удалось загрузить офлайн-модель: ${e.localizedMessage}"
                Log.e("VoiceInputManager", "Vosk download error", e)
                withContext(Dispatchers.Main) {
                    // Fallback to system speech recognition
                    startSystemSpeechRecognizer(callerContext)
                }
            }
        }
    }

    private fun initVoskAndStart(callerContext: Context) {
        try {
            GlobalConsoleLogger.i("VOSK", "Инициализация офлайн-модели VOSK...")
            val targetDir = File(context.filesDir, "vosk-model-small-ru-0.22")
            if (voskModel == null) {
                voskModel = Model(targetDir.absolutePath)
            }
            if (voskRecognizer == null) {
                voskRecognizer = Recognizer(voskModel, 16000f)
            }

            voskSpeechService?.stop()
            val service = SpeechService(voskRecognizer, 16000f)
            voskSpeechService = service

            service.startListening(object : VoskRecognitionListener {
                override fun onResult(hypothesis: String) {
                    if (!isProcessingAllowed) return
                    val text = parseResultHypothesis(hypothesis).trim()
                    if (text.isNotBlank() && text != lastProcessedChunk) {
                        lastProcessedChunk = text
                        GlobalConsoleLogger.d("VOSK", "Распознан фрагмент: «$text»")
                        _recognizedText.value = text
                        _partialText.value = ""
                        onChunkRecognized?.invoke(text)
                    }
                }

                override fun onPartialResult(hypothesis: String) {
                    if (!isProcessingAllowed) return
                    val partial = parsePartialHypothesis(hypothesis).trim()
                    if (partial.isNotBlank()) {
                        _partialText.value = partial
                    }
                }

                override fun onFinalResult(hypothesis: String) {
                    if (!isProcessingAllowed) return
                    val text = parseResultHypothesis(hypothesis).trim()
                    if (text.isNotBlank() && text != lastProcessedChunk) {
                        lastProcessedChunk = text
                        GlobalConsoleLogger.i("VOSK", "Финальный результат VOSK: «$text»")
                        _recognizedText.value = text
                        _partialText.value = ""
                        onChunkRecognized?.invoke(text)
                    }
                }

                override fun onError(exception: Exception) {
                    if (!isProcessingAllowed) return
                    GlobalConsoleLogger.e("VOSK", "Ошибка VOSK слушателя: ${exception.localizedMessage}", exception)
                    Log.e("VoiceInputManager", "Vosk listener error", exception)
                    _errorState.value = exception.localizedMessage
                }

                override fun onTimeout() {
                    // Continuous listening, do not stop automatically
                }
            })

            _isListening.value = true
            _errorState.value = null
            GlobalConsoleLogger.i("VOSK", "VOSK успешно запущен и слушатель готов (offline)")
            Log.d("VoiceInputManager", "Vosk successfully started listening offline!")
        } catch (e: Throwable) {
            GlobalConsoleLogger.e("VOSK", "Ошибка JNI VOSK, переход на системный SpeechRecognizer: ${e.localizedMessage}", e)
            Log.e("VoiceInputManager", "Vosk JNI error, falling back to system SpeechRecognizer", e)
            startSystemSpeechRecognizer(callerContext)
        }
    }

    private fun startSystemSpeechRecognizer(callerContext: Context) {
        val currentContext = activeContextRef?.get() ?: callerContext
        val isAvailable = try {
            val intent = Intent("android.speech.RecognitionService")
            val services = currentContext.packageManager.queryIntentServices(intent, 0)
            !services.isNullOrEmpty() && SystemSpeechRecognizer.isRecognitionAvailable(currentContext)
        } catch (_: Throwable) {
            false
        }

        if (!isAvailable) {
            _errorState.value = "Голосовая служба недоступна. Пожалуйста, введите текст вручную."
            _isListening.value = false
            return
        }

        mainHandler.post {
            try {
                stopRecognizerOnly()

                val recognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    SystemSpeechRecognizer.createSpeechRecognizer(currentContext.createAttributionContext("voice_input"))
                } else {
                    SystemSpeechRecognizer.createSpeechRecognizer(currentContext)
                }

                systemSpeechRecognizer = recognizer
                recognizer.setRecognitionListener(object : SystemRecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        GlobalConsoleLogger.d("VOICE", "Системный распознаватель готов к приему речи")
                        _isListening.value = true
                        _errorState.value = null
                    }

                    override fun onBeginningOfSpeech() {
                        GlobalConsoleLogger.d("VOICE", "Обнаружено начало речи")
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 14f).coerceIn(0.05f, 1f)
                        _rmsDb.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        GlobalConsoleLogger.d("VOICE", "Завершение речевого фрагмента")
                        if (!isContinuous || isPaused) {
                            _isListening.value = false
                        }
                    }

                    override fun onError(error: Int) {
                        GlobalConsoleLogger.w("VOICE", "Системный распознаватель вернул ошибку code: $error")
                        Log.d("VoiceInputManager", "System recognizer onError code: $error")
                        if (isContinuous && !isPaused) {
                            _isListening.value = true
                            mainHandler.postDelayed({
                                if (isContinuous && !isPaused) {
                                    startSystemSpeechRecognizer(currentContext)
                                }
                            }, 200)
                        } else {
                            _isListening.value = false
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        if (!isProcessingAllowed) return
                        val matches = results?.getStringArrayList(SystemSpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].trim()
                            if (text.isNotBlank() && text != lastProcessedChunk) {
                                lastProcessedChunk = text
                                GlobalConsoleLogger.i("VOICE", "Системный движок распознал: «$text»")
                                _recognizedText.value = text
                                _partialText.value = ""
                                onChunkRecognized?.invoke(text)
                            }
                        }

                        if (isContinuous && !isPaused) {
                            _isListening.value = true
                            mainHandler.post {
                                if (isContinuous && !isPaused) {
                                    startSystemSpeechRecognizer(currentContext)
                                }
                            }
                        } else {
                            _isListening.value = false
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SystemSpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            if (text.isNotBlank()) {
                                _partialText.value = text
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                recognizer.startListening(intent)
                _isListening.value = true
                _errorState.value = null
            } catch (e: Throwable) {
                _isListening.value = false
                _errorState.value = "Ошибка запуска микрофона: ${e.message}"
            }
        }
    }

    fun stopListening() {
        GlobalConsoleLogger.i("VOICE", "Остановка распознавания речи")
        isProcessingAllowed = false
        isContinuous = false
        isPaused = false
        stopRecognizerOnly()
    }

    private fun stopRecognizerOnly() {
        try {
            systemSpeechRecognizer?.stopListening()
            systemSpeechRecognizer?.destroy()
        } catch (_: Throwable) {}
        systemSpeechRecognizer = null

        try {
            voskSpeechService?.stop()
        } catch (_: Throwable) {}
        voskSpeechService = null

        activeContextRef = null
        _isListening.value = false
        _rmsDb.value = 0f
    }

    fun pauseListening() {
        if (isContinuous) {
            isPaused = true
            stopRecognizerOnly()
        }
    }

    fun resumeListening() {
        if (isContinuous && isPaused) {
            isPaused = false
            val currentContext = activeContextRef?.get() ?: context
            startListening(currentContext)
        }
    }

    fun clear() {
        accumulatedText = ""
        _recognizedText.value = ""
        _partialText.value = ""
        _errorState.value = null
    }

    fun setRecognizedTextManually(text: String) {
        accumulatedText = text
        _recognizedText.value = text
        _partialText.value = ""
    }

    fun destroy() {
        stopListening()
    }
}
