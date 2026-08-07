package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceInputManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
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

    private var accumulatedText = ""
    private var isContinuous = false
    private var isPaused = false

    private var activeContextRef: java.lang.ref.WeakReference<Context>? = null

    var onErrorCallback: (() -> Unit)? = null

    private var silenceRunnable: Runnable? = null
    private val SILENCE_TIMEOUT_MS = 300000L // 5 minutes continuous recording timeout

    private fun resetSilenceTimer() {
        cancelSilenceTimer()
    }

    private fun cancelSilenceTimer() {
        silenceRunnable?.let { mainHandler.removeCallbacks(it) }
        silenceRunnable = null
    }

    fun startListening(callerContext: Context) {
        activeContextRef = java.lang.ref.WeakReference(callerContext)
        isContinuous = true
        isPaused = false
        startListeningInternal(clearText = true)
    }

    fun startListening() {
        activeContextRef = null
        isContinuous = true
        isPaused = false
        startListeningInternal(clearText = true)
    }

    private fun startListeningInternal(clearText: Boolean) {
        if (clearText) {
            accumulatedText = ""
            _recognizedText.value = ""
            _partialText.value = ""
            _errorState.value = null
        }

        val currentContext = activeContextRef?.get() ?: context

        val isAvailable = try {
            val intent = Intent("android.speech.RecognitionService")
            val services = currentContext.packageManager.queryIntentServices(intent, 0)
            !services.isNullOrEmpty() && SpeechRecognizer.isRecognitionAvailable(currentContext)
        } catch (_: Throwable) {
            false
        }

        if (!isAvailable) {
            _errorState.value = "Голосовая служба недоступна на этом устройстве/эмуляторе. Введите текст вручную."
            _isListening.value = false
            return
        }

        mainHandler.post {
            try {
                // Ensure any existing recognizer is cleared
                stopRecognizerOnly()

                val recognizer = try {
                    val contextWithAttribution = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) { currentContext.createAttributionContext("voice_input") } else { currentContext }; SpeechRecognizer.createSpeechRecognizer(contextWithAttribution)
                } catch (_: Throwable) {
                    null
                }

                if (recognizer == null) {
                    _errorState.value = "Голосовой ввод недоступен на этом устройстве. Введите текст вручную."
                    _isListening.value = false
                    return@post
                }
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _errorState.value = null
                        resetSilenceTimer()
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                        resetSilenceTimer()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        try {
                            val normalized = ((rmsdB + 2f) / 14f).coerceIn(0.05f, 1f)
                            _rmsDb.value = normalized
                            if (normalized > 0.15f) {
                                resetSilenceTimer()
                            }
                        } catch (_: Throwable) {}
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        if (!isContinuous || isPaused) {
                            _isListening.value = false
                        }
                        _rmsDb.value = 0f
                    }

                    override fun onError(error: Int) {
                        _rmsDb.value = 0f
                        Log.d("VoiceInputManager", "onError code: $error")

                        if (isContinuous && !isPaused) {
                            _isListening.value = true
                            mainHandler.postDelayed({
                                if (isContinuous && !isPaused) {
                                    startListeningInternal(clearText = false)
                                }
                            }, 150)
                        } else {
                            _isListening.value = false
                            if (accumulatedText.isEmpty() && _partialText.value.isEmpty()) {
                                _errorState.value = null
                                cancelSilenceTimer()
                                mainHandler.post {
                                    onErrorCallback?.invoke()
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _rmsDb.value = 0f
                        try {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                if (text.isNotBlank()) {
                                    if (accumulatedText.isBlank()) {
                                        accumulatedText = text
                                    } else {
                                        accumulatedText += " $text"
                                    }
                                    _recognizedText.value = accumulatedText
                                    _partialText.value = ""
                                    resetSilenceTimer()
                                }
                            }
                        } catch (_: Throwable) {}

                        // Restart listening if continuous mode is active and we are not paused
                        if (isContinuous && !isPaused) {
                            _isListening.value = true
                            mainHandler.post {
                                if (isContinuous && !isPaused) {
                                    startListeningInternal(clearText = false)
                                }
                            }
                        } else {
                            _isListening.value = false
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        try {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                if (text.isNotBlank()) {
                                    _partialText.value = text
                                    resetSilenceTimer()
                                }
                            }
                        } catch (_: Throwable) {}
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
                }

                try {
                    recognizer.startListening(intent)
                    _isListening.value = true
                    _errorState.value = null
                    resetSilenceTimer()
                } catch (e: Throwable) {
                    _isListening.value = false
                    _errorState.value = "Ошибка запуска микрофона: ${e.message}"
                    cancelSilenceTimer()
                }
            } catch (e: Throwable) {
                _isListening.value = false
                _errorState.value = "Голосовой ввод недоступен: ${e.message}"
            }
        }
    }

    fun stopListening() {
        isContinuous = false
        isPaused = false
        stopRecognizerOnly()
    }

    private fun stopRecognizerOnly() {
        cancelSilenceTimer()
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Throwable) {}
        speechRecognizer = null
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
            startListeningInternal(clearText = false)
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
