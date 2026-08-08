package com.example.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
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

    private var isSystemMuted = false

    private fun muteSystemSounds() {
        if (isSystemMuted) return
        try {
            val currentContext = activeContextRef?.get() ?: context
            val audioManager = currentContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_ALARM, true)
                }
                isSystemMuted = true
            }
        } catch (e: Throwable) {
            Log.d("VoiceInputManager", "Failed to mute system sounds: ${e.message}")
        }
    }

    private fun restoreSystemSounds() {
        if (!isSystemMuted) return
        try {
            val currentContext = activeContextRef?.get() ?: context
            val audioManager = currentContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_ALARM, false)
                }
                isSystemMuted = false
            }
        } catch (e: Throwable) {
            Log.d("VoiceInputManager", "Failed to restore system sounds: ${e.message}")
        }
    }

    private fun resetSilenceTimer() {
        cancelSilenceTimer()
    }

    private fun cancelSilenceTimer() {
        silenceRunnable?.let { mainHandler.removeCallbacks(it) }
        silenceRunnable = null
    }

    private val _isHotwordActive = MutableStateFlow(false)
    val isHotwordActive: StateFlow<Boolean> = _isHotwordActive.asStateFlow()

    var onHotwordDetected: ((initialPhrase: String) -> Unit)? = null

    private fun triggerHapticVibration() {
        try {
            val currentContext = activeContextRef?.get() ?: context
            val vibrator = currentContext.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(200)
            }
        } catch (e: Throwable) {
            Log.d("VoiceInputManager", "Vibration failed: ${e.message}")
        }
    }

    fun startHotwordListening(callerContext: Context) {
        activeContextRef = java.lang.ref.WeakReference(callerContext)
        _isHotwordActive.value = true
        isContinuous = true
        isPaused = false
        startHotwordInternal()
    }

    fun stopHotwordListening() {
        _isHotwordActive.value = false
        stopListening()
    }

    private fun startHotwordInternal() {
        val currentContext = activeContextRef?.get() ?: context

        val isAvailable = try {
            val intent = Intent("android.speech.RecognitionService")
            val services = currentContext.packageManager.queryIntentServices(intent, 0)
            !services.isNullOrEmpty() && SpeechRecognizer.isRecognitionAvailable(currentContext)
        } catch (_: Throwable) {
            false
        }

        if (!isAvailable || !_isHotwordActive.value) return

        mainHandler.post {
            try {
                stopRecognizerOnly()

                val recognizer = try {
                    val contextWithAttribution = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { currentContext.createAttributionContext("hotword_input") } else { currentContext }
                    SpeechRecognizer.createSpeechRecognizer(contextWithAttribution)
                } catch (_: Throwable) {
                    null
                }

                if (recognizer == null) return@post
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        try {
                            val normalized = ((rmsdB + 2f) / 14f).coerceIn(0.05f, 1f)
                            _rmsDb.value = normalized
                        } catch (_: Throwable) {}
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { _rmsDb.value = 0f }

                    override fun onError(error: Int) {
                        _rmsDb.value = 0f
                        if (_isHotwordActive.value) {
                            mainHandler.postDelayed({
                                if (_isHotwordActive.value) startHotwordInternal()
                            }, 50)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _rmsDb.value = 0f
                        try {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty() && checkAndProcessHotword(matches)) {
                                return
                            }
                        } catch (_: Throwable) {}

                        if (_isHotwordActive.value) {
                            mainHandler.post {
                                if (_isHotwordActive.value) startHotwordInternal()
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        try {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty() && checkAndProcessHotword(matches)) {
                                return
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
                }

                try {
                    muteSystemSounds()
                    recognizer.startListening(intent)
                } catch (_: Throwable) {
                    if (_isHotwordActive.value) {
                        mainHandler.postDelayed({ if (_isHotwordActive.value) startHotwordInternal() }, 200)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun checkAndProcessHotword(matches: List<String>): Boolean {
        val triggers = listOf("давид", "дэвид", "david")
        for (match in matches) {
            val lower = match.lowercase(java.util.Locale.getDefault()).trim()
            for (trigger in triggers) {
                if (lower.contains(trigger)) {
                    val index = lower.indexOf(trigger)
                    var command = lower.substring(index + trigger.length).trim()
                    command = command.removePrefix(",").removePrefix(".").trim()

                    triggerHapticVibration()

                    _isHotwordActive.value = false
                    stopRecognizerOnly()

                    val ctx = activeContextRef?.get() ?: context
                    startListening(ctx)
                    if (command.isNotBlank()) {
                        setRecognizedTextManually(command)
                    }

                    mainHandler.post {
                        onHotwordDetected?.invoke(command)
                    }
                    return true
                }
            }
        }
        return false
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
            restoreSystemSounds()
            return
        }

        mainHandler.post {
            try {
                // Ensure any existing recognizer is cleared
                stopRecognizerOnly()

                val recognizer = try {
                    val contextWithAttribution = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { currentContext.createAttributionContext("voice_input") } else { currentContext }
                    SpeechRecognizer.createSpeechRecognizer(contextWithAttribution)
                } catch (_: Throwable) {
                    null
                }

                if (recognizer == null) {
                    _errorState.value = "Голосовой ввод недоступен на этом устройстве. Введите текст вручную."
                    _isListening.value = false
                    restoreSystemSounds()
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
                            }, 50)
                        } else {
                            _isListening.value = false
                            if (accumulatedText.isEmpty() && _partialText.value.isEmpty()) {
                                _errorState.value = null
                                cancelSilenceTimer()
                                restoreSystemSounds()
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
                            restoreSystemSounds()
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
                    muteSystemSounds()
                    recognizer.startListening(intent)
                    _isListening.value = true
                    _errorState.value = null
                    resetSilenceTimer()
                } catch (e: Throwable) {
                    _isListening.value = false
                    _errorState.value = "Ошибка запуска микрофона: ${e.message}"
                    cancelSilenceTimer()
                    restoreSystemSounds()
                }
            } catch (e: Throwable) {
                _isListening.value = false
                _errorState.value = "Голосовой ввод недоступен: ${e.message}"
                restoreSystemSounds()
            }
        }
    }

    fun stopListening() {
        isContinuous = false
        isPaused = false
        _isHotwordActive.value = false
        stopRecognizerOnly()
        restoreSystemSounds()
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

        if (!isContinuous && !_isHotwordActive.value) {
            restoreSystemSounds()
        }
    }

    fun pauseListening() {
        if (isContinuous) {
            isPaused = true
            stopRecognizerOnly()
            restoreSystemSounds()
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
        restoreSystemSounds()
    }
}

