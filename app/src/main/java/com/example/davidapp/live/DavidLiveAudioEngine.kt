package com.example.davidapp.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Full-duplex audio streaming and real-time FFT spectrum engine for David Live.
 *
 * - Uses [AudioAttributes.USAGE_ASSISTANT] + [AudioAttributes.CONTENT_TYPE_SPEECH] so Android (including Android 17)
 *   binds hardware volume buttons directly to the AI Assistant / Neural Network volume slider
 *   instead of the phone call stream.
 * - Captures 16 kHz / 16-bit / Mono PCM microphone audio via [AudioRecord] with hardware
 *   [AcousticEchoCanceler], [NoiseSuppressor], and [AutomaticGainControl] without switching the system
 *   into phone-call mode (`MODE_IN_COMMUNICATION`).
 * - Plays 24 kHz / 16-bit / Mono PCM voice chunks from Gemini Live via [AudioTrack]
 *   while feeding [DavidLiveSpectrumAnalyzer] so the circular visualizer responds vividly to
 *   both the user's voice and David's voice.
 * - Supports instant barge-in (flushing playback queue when user interrupts David)
 *   and microphone mute/unmute without dropping the Live session.
 */
class DavidLiveAudioEngine(
    private val spectrumAnalyzer: DavidLiveSpectrumAnalyzer = DavidLiveSpectrumAnalyzer()
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioTrackLock = Any()
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var autoGainControl: AutomaticGainControl? = null

    private val playbackQueue = ConcurrentLinkedQueue<ShortArray>()
    private val isRunning = AtomicBoolean(false)
    private val isMicMuted = AtomicBoolean(false)
    private val lastOutputChunkTimestamp = AtomicLong(0L)
    private val bargeInEchoSuppressUntilMs = AtomicLong(0L)
    // Invalidate a chunk already taken by the playback worker after a queue flush.
    private val playbackGeneration = AtomicInteger(0)

    private val _rayMagnitudes = MutableStateFlow(
         defaultRestingRays()
    )
    val rayMagnitudes: StateFlow<List<Float>> = _rayMagnitudes.asStateFlow()

    private val _overallEnergy = MutableStateFlow(0.14f)
    val overallEnergy: StateFlow<Float> = _overallEnergy.asStateFlow()

    private val _activeSpeaker = MutableStateFlow(DavidLiveSpeaker.NONE)
    val activeSpeaker: StateFlow<DavidLiveSpeaker> = _activeSpeaker.asStateFlow()

    private val _isPlaybackDrained = MutableStateFlow(true)
    val isPlaybackDrained: StateFlow<Boolean> = _isPlaybackDrained.asStateFlow()

    private val _isWithinUserSpeechHold = MutableStateFlow(false)
    val isWithinUserSpeechHold: StateFlow<Boolean> = _isWithinUserSpeechHold.asStateFlow()

    private val _isWithinDavidSpeechHold = MutableStateFlow(false)
    val isWithinDavidSpeechHold: StateFlow<Boolean> = _isWithinDavidSpeechHold.asStateFlow()

    private val enqueuedDavidTurnSamplesCount = AtomicLong(0L)
    private val writtenDavidTurnSamplesCount = AtomicLong(0L)
    private val turnPlaybackStartWallClockMs = AtomicLong(0L)

    private val _enqueuedDavidTurnSamples = MutableStateFlow(0L)
    val enqueuedDavidTurnSamples: StateFlow<Long> = _enqueuedDavidTurnSamples.asStateFlow()

    private val _audibleDavidTurnSamples = MutableStateFlow(0L)
    val audibleDavidTurnSamples: StateFlow<Long> = _audibleDavidTurnSamples.asStateFlow()

    private val lastUserSpeechDetectedMs = AtomicLong(0L)
    private val lastDavidPlaybackActiveMs = AtomicLong(0L)

    fun isEngineRunning(): Boolean = isRunning.get()

    /**
     * Resets the 24 kHz turn sample counters at the start of a new David response turn or upon interruption.
     */
    fun resetDavidTurnSampleCounters() {
        enqueuedDavidTurnSamplesCount.set(0L)
        writtenDavidTurnSamplesCount.set(0L)
        turnPlaybackStartWallClockMs.set(0L)
        _enqueuedDavidTurnSamples.value = 0L
        _audibleDavidTurnSamples.value = 0L
    }

    /**
     * Builds the official Android [AudioAttributes] for AI / Voice Assistants (`USAGE_ASSISTANT`).
     * In Android 17 (and modern Android versions with dedicated Assistant volume), this routes
     * audio and hardware volume controls to the AI / Neural Network volume channel.
     */
    fun buildAssistantAudioAttributes(): AudioAttributes {
        return Companion.buildAssistantAudioAttributes()
    }

    /**
     * Starts full-duplex audio capture and playback loops.
     *
     * @param onMicPcmChunk Invoked on IO thread with raw 16 kHz 16-bit little-endian PCM bytes
     *                      whenever microphone is unmuted.
     * @param onUserBargeIn Invoked when the user starts speaking loudly while David's audio is playing.
     */
    @SuppressLint("MissingPermission")
    fun start(
        onMicPcmChunk: (ByteArray) -> Unit,
        onUserBargeIn: () -> Unit = {}
    ) {
        if (isRunning.getAndSet(true)) return
        spectrumAnalyzer.reset()
        playbackQueue.clear()
        resetDavidTurnSampleCounters()
        bargeInEchoSuppressUntilMs.set(0L)
        _isPlaybackDrained.value = true
        _isWithinUserSpeechHold.value = false
        _isWithinDavidSpeechHold.value = false

        startPlaybackWorker()
        startCaptureWorker(onMicPcmChunk, onUserBargeIn)
    }

    fun setMicMuted(muted: Boolean) {
        isMicMuted.set(muted)
        if (muted && _activeSpeaker.value == DavidLiveSpeaker.USER) {
            _activeSpeaker.value = DavidLiveSpeaker.NONE
            _isWithinUserSpeechHold.value = false
            _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
            _overallEnergy.value = spectrumAnalyzer.overallEnergy
        }
    }

    fun isMuted(): Boolean = isMicMuted.get()

    /**
     * Enqueues 24 kHz 16-bit little-endian mono PCM bytes received from Gemini Live.
     */
    fun enqueueModelAudioPcm24k(pcmBytes: ByteArray) {
        if (!isRunning.get() || pcmBytes.size < 2) return
        val sampleCount = pcmBytes.size / 2
        val samples = ShortArray(sampleCount)
        var byteIdx = 0
        for (i in 0 until sampleCount) {
            val low = pcmBytes[byteIdx].toInt() and 0xFF
            val high = pcmBytes[byteIdx + 1].toInt()
            samples[i] = ((high shl 8) or low).toShort()
            byteIdx += 2
        }
        val updatedEnqueued = enqueuedDavidTurnSamplesCount.addAndGet(sampleCount.toLong())
        _enqueuedDavidTurnSamples.value = updatedEnqueued
        _isPlaybackDrained.value = false
        playbackQueue.offer(samples)
    }

    /**
     * Immediately flushes any queued or playing model audio (used on barge-in interruption).
     * Synchronizes AudioTrack reset (`pause()` -> `flush()` -> `play()`), increments [playbackGeneration],
     * and activates a short protective tail-echo suppression window so residual speaker audio
     * does not get captured by the microphone as a new user utterance.
     */
    fun clearModelPlaybackQueue() {
        val now = System.currentTimeMillis()
        playbackGeneration.incrementAndGet()
        playbackQueue.clear()
        resetDavidTurnSampleCounters()
        lastOutputChunkTimestamp.set(0L)
        lastDavidPlaybackActiveMs.set(0L)
        _isPlaybackDrained.value = true
        _isWithinDavidSpeechHold.value = false
        bargeInEchoSuppressUntilMs.set(now + BARGE_IN_TAIL_ECHO_SUPPRESS_MS)
        synchronized(audioTrackLock) {
            val track = audioTrack
            if (track != null) {
                try {
                    track.pause()
                    track.flush()
                    if (isRunning.get()) {
                        track.play()
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (_activeSpeaker.value == DavidLiveSpeaker.DAVID) {
            _activeSpeaker.value = DavidLiveSpeaker.NONE
        }
    }

    internal fun getPlaybackGenerationForTest(): Int = playbackGeneration.get()

    internal fun getBargeInEchoSuppressUntilMsForTest(): Long = bargeInEchoSuppressUntilMs.get()

    internal fun shouldSuppressTailEchoFrame(
        rms: Float,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return nowMs < bargeInEchoSuppressUntilMs.get() && rms < BARGE_IN_RMS_THRESHOLD
    }

    /**
     * Allows unit tests to deterministically simulate buffered and audible 24 kHz audio progress
     * for verifying voice-to-subtitle synchronization without hardware AudioTrack.
     */
    internal fun simulateEnqueuedAndAudibleSamplesForTest(
        enqueuedSamples24k: Long,
        audibleSamples24k: Long,
        isDrained: Boolean = audibleSamples24k >= enqueuedSamples24k && enqueuedSamples24k > 0L
    ) {
        enqueuedDavidTurnSamplesCount.set(enqueuedSamples24k)
        writtenDavidTurnSamplesCount.set(audibleSamples24k)
        _enqueuedDavidTurnSamples.value = enqueuedSamples24k
        _audibleDavidTurnSamples.value = audibleSamples24k
        _isPlaybackDrained.value = isDrained
        _activeSpeaker.value = if (isDrained) DavidLiveSpeaker.NONE else DavidLiveSpeaker.DAVID
        _isWithinDavidSpeechHold.value = !isDrained
    }

    /**
     * Allows unit/UI tests to feed a synthetic PCM frame directly into the spectrum analyzer.
     */
    fun feedTestPcmFrame(
        samples: ShortArray,
        sampleRateHz: Int = INPUT_SAMPLE_RATE_HZ,
        speaker: DavidLiveSpeaker = DavidLiveSpeaker.USER
    ) {
        val now = System.currentTimeMillis()
        _activeSpeaker.value = speaker
        if (speaker == DavidLiveSpeaker.USER) {
            lastUserSpeechDetectedMs.set(now)
            _isWithinUserSpeechHold.value = true
        } else if (speaker == DavidLiveSpeaker.DAVID) {
            lastDavidPlaybackActiveMs.set(now)
            _isPlaybackDrained.value = false
            _isWithinDavidSpeechHold.value = true
            val totalWritten = writtenDavidTurnSamplesCount.addAndGet(samples.size.toLong())
            if (enqueuedDavidTurnSamplesCount.get() < totalWritten) {
                enqueuedDavidTurnSamplesCount.set(totalWritten)
                _enqueuedDavidTurnSamples.value = totalWritten
            }
            _audibleDavidTurnSamples.value = totalWritten
        } else {
            _isWithinUserSpeechHold.value = false
            _isWithinDavidSpeechHold.value = false
            _isPlaybackDrained.value = true
        }
        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(samples, samples.size, sampleRateHz)
        _overallEnergy.value = spectrumAnalyzer.overallEnergy
    }

    fun isPlaybackCurrentlyDrained(): Boolean = _isPlaybackDrained.value && playbackQueue.isEmpty()

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        captureJob?.cancel()
        playbackJob?.cancel()
        engineScope.coroutineContext.cancelChildren()
        playbackQueue.clear()
        resetDavidTurnSampleCounters()
        bargeInEchoSuppressUntilMs.set(0L)

        releaseAudioEffects()

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        synchronized(audioTrackLock) {
            try {
                audioTrack?.stop()
            } catch (_: Exception) {
            }
            try {
                audioTrack?.release()
            } catch (_: Exception) {
            }
            audioTrack = null
        }

        spectrumAnalyzer.reset()
        _activeSpeaker.value = DavidLiveSpeaker.NONE
        _isPlaybackDrained.value = true
        _isWithinUserSpeechHold.value = false
        _isWithinDavidSpeechHold.value = false
        lastUserSpeechDetectedMs.set(0L)
        lastDavidPlaybackActiveMs.set(0L)
        _rayMagnitudes.value = defaultRestingRays()
        _overallEnergy.value = 0.14f
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureWorker(
        onMicPcmChunk: (ByteArray) -> Unit,
        onUserBargeIn: () -> Unit
    ) {
        captureJob = engineScope.launch {
            val minBuf = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            // Use VOICE_RECOGNITION (with fallback to MIC) instead of VOICE_COMMUNICATION
            // so Android does NOT switch the volume slider into phone-call receiver mode.
            val record = tryCreateAudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, minBuf)
                ?: tryCreateAudioRecord(MediaRecorder.AudioSource.MIC, minBuf)

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                try {
                    record?.release()
                } catch (_: Exception) {
                }
                while (isActive && isRunning.get()) {
                    if (System.currentTimeMillis() - lastOutputChunkTimestamp.get() > 160L) {
                        _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                        _overallEnergy.value = spectrumAnalyzer.overallEnergy
                    }
                    delay(33L)
                }
                return@launch
            }

            audioRecord = record
            attachAudioEffects(record.audioSessionId)

            try {
                record.startRecording()

                // 512 samples @ 16 kHz = 32 ms chunk (~31.25 fps for responsive user voice visualization)
                val shortBuffer = ShortArray(512)
                val micVisualizerBuffer = ShortArray(512)
                var consecutiveVoiceFrames = 0
                var userBargedIn = false

                while (isActive && isRunning.get()) {
                    val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount <= 0) {
                        delay(12L)
                        continue
                    }

                    if (isMicMuted.get()) {
                        val davidSpeaking = System.currentTimeMillis() - lastOutputChunkTimestamp.get() <= 160L
                        if (!davidSpeaking) {
                            _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                            _overallEnergy.value = spectrumAnalyzer.overallEnergy
                            _activeSpeaker.value = DavidLiveSpeaker.NONE
                            _isWithinUserSpeechHold.value = false
                        }
                        continue
                    }

                    // Compute RMS for voice activity & barge-in detection
                    var sumSq = 0.0
                    for (i in 0 until readCount) {
                        val norm = shortBuffer[i] / 32768.0
                        sumSq += norm * norm
                    }
                    val rms = sqrt(sumSq / readCount).toFloat()

                    val nowMs = System.currentTimeMillis()
                    val davidCurrentlyPlaying =
                        nowMs - lastOutputChunkTimestamp.get() <= PLAYBACK_ECHO_TAIL_MS ||
                            playbackQueue.isNotEmpty()
                    if (!davidCurrentlyPlaying) userBargedIn = false

                    if (rms > BARGE_IN_RMS_THRESHOLD) {
                        consecutiveVoiceFrames++
                        if (davidCurrentlyPlaying && !userBargedIn && consecutiveVoiceFrames >= BARGE_IN_MIN_FRAMES) {
                            userBargedIn = true
                            clearModelPlaybackQueue()
                            onUserBargeIn()
                            consecutiveVoiceFrames = 0
                        }
                    } else {
                        consecutiveVoiceFrames = 0
                    }

                    // Protective tail-echo suppression window right after AudioTrack flush:
                    // ignore residual acoustic speaker tail unless user is actively speaking above barge-in threshold.
                    if (shouldSuppressTailEchoFrame(rms, nowMs)) {
                        _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                        _overallEnergy.value = spectrumAnalyzer.overallEnergy
                        _activeSpeaker.value = DavidLiveSpeaker.NONE
                        _isWithinUserSpeechHold.value = false
                        continue
                    }

                    // Update visualizer from user's microphone when David is not currently speaking
                    if (!davidCurrentlyPlaying) {
                        boostMicSamplesForVisualizer(
                            source = shortBuffer,
                            destination = micVisualizerBuffer,
                            count = readCount,
                            rms = rms
                        )
                        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(
                            pcmSamples = micVisualizerBuffer,
                            sampleCount = readCount,
                            sampleRateHz = INPUT_SAMPLE_RATE_HZ
                        )
                        _overallEnergy.value = computeUserVisualizerEnergy(
                            fftEnergy = spectrumAnalyzer.overallEnergy,
                            micRms = rms
                        )
                        if (rms > USER_SPEECH_RMS_THRESHOLD) {
                            lastUserSpeechDetectedMs.set(nowMs)
                            _activeSpeaker.value = DavidLiveSpeaker.USER
                            _isWithinUserSpeechHold.value = true
                        } else {
                            val withinHold =
                                lastUserSpeechDetectedMs.get() > 0L &&
                                    nowMs - lastUserSpeechDetectedMs.get() <= DavidLiveVisualStateResolver.SPEECH_PAUSE_HOLD_MS
                            _isWithinUserSpeechHold.value = withinHold
                            _activeSpeaker.value = if (withinHold) {
                                DavidLiveSpeaker.USER
                            } else {
                                DavidLiveSpeaker.NONE
                            }
                        }
                    } else {
                        _isWithinUserSpeechHold.value = false
                    }

                    // Suppress speaker echo while David is playing, except after confirmed user barge-in.
                    if (davidCurrentlyPlaying && !userBargedIn) continue

                    // Convert ShortArray to little-endian PCM16 ByteArray and stream to Gemini Live
                    val pcmBytes = ByteArray(readCount * 2)
                    var bIdx = 0
                    for (i in 0 until readCount) {
                        val s = shortBuffer[i].toInt()
                        pcmBytes[bIdx] = (s and 0xFF).toByte()
                        pcmBytes[bIdx + 1] = ((s shr 8) and 0xFF).toByte()
                        bIdx += 2
                    }
                    onMicPcmChunk(pcmBytes)
                }
            } catch (_: Exception) {
                // Capture loop interrupted or startRecording failed
            } finally {
                releaseAudioEffects()
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    record.release()
                } catch (_: Exception) {
                }
                if (audioRecord === record) {
                    audioRecord = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryCreateAudioRecord(audioSource: Int, minBuf: Int): AudioRecord? {
        return try {
            val rec = AudioRecord(
                audioSource,
                INPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
                rec.release()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        releaseAudioEffects()
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                autoGainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseAudioEffects() {
        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        }
        echoCanceler = null
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        }
        noiseSuppressor = null
        try {
            autoGainControl?.release()
        } catch (_: Exception) {
        }
        autoGainControl = null
    }

    private fun startPlaybackWorker() {
        playbackJob = engineScope.launch {
            val minOutBuf = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4800)

            val assistantAttrs = buildAssistantAudioAttributes()
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(assistantAttrs)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(OUTPUT_SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minOutBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                null
            }

            synchronized(audioTrackLock) {
                audioTrack = track
                try {
                    track?.play()
                } catch (_: Exception) {
                }
            }

            try {
                while (isActive && isRunning.get()) {
                    val generation = playbackGeneration.get()
                    val samples = playbackQueue.poll()
                    if (samples == null) {
                        val now = System.currentTimeMillis()
                        val elapsedSinceOutput = now - lastOutputChunkTimestamp.get()
                        val totalWritten = writtenDavidTurnSamplesCount.get()
                        if (totalWritten > 0L) {
                            if (elapsedSinceOutput <= AUDIO_TRACK_DRAIN_TAIL_MS) {
                                _audibleDavidTurnSamples.value = computeAudibleSamplesAt(
                                    writtenSamples24k = totalWritten,
                                    turnStartWallMs = turnPlaybackStartWallClockMs.get(),
                                    lastWriteWallMs = lastOutputChunkTimestamp.get(),
                                    nowWallMs = now,
                                    hasMoreQueuedAudio = false
                                )
                            } else {
                                _audibleDavidTurnSamples.value = totalWritten
                            }
                        }
                        if (elapsedSinceOutput > AUDIO_TRACK_DRAIN_TAIL_MS) {
                            _isPlaybackDrained.value = true
                            val withinHold = lastDavidPlaybackActiveMs.get() > 0L &&
                                now - lastDavidPlaybackActiveMs.get() <= DavidLiveVisualStateResolver.SPEECH_PAUSE_HOLD_MS
                            _isWithinDavidSpeechHold.value = withinHold
                            if (!withinHold && _activeSpeaker.value == DavidLiveSpeaker.NONE) {
                                _activeSpeaker.value = DavidLiveSpeaker.NONE
                            } else if (!withinHold && _activeSpeaker.value == DavidLiveSpeaker.DAVID) {
                                _activeSpeaker.value = DavidLiveSpeaker.NONE
                            }
                        }
                        delay(12L)
                        continue
                    }

                    // Slice into ~1024-sample windows so the circular visualizer animates smoothly at ~23 fps
                    var offset = 0
                    val step = 1024
                    while (offset < samples.size && isRunning.get() && generation == playbackGeneration.get()) {
                        val len = (samples.size - offset).coerceAtMost(step)
                        val window = samples.copyOfRange(offset, offset + len)
                        val now = System.currentTimeMillis()
                        turnPlaybackStartWallClockMs.compareAndSet(0L, now)
                        lastOutputChunkTimestamp.set(now)
                        lastDavidPlaybackActiveMs.set(now)
                        _isPlaybackDrained.value = false
                        _isWithinDavidSpeechHold.value = true
                        _activeSpeaker.value = DavidLiveSpeaker.DAVID
                        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(
                            pcmSamples = window,
                            sampleCount = window.size,
                            sampleRateHz = OUTPUT_SAMPLE_RATE_HZ
                        )
                        _overallEnergy.value = spectrumAnalyzer.overallEnergy

                        try {
                            if (generation == playbackGeneration.get() && isRunning.get()) {
                                track?.write(window, 0, window.size)
                            }
                        } catch (_: Exception) {
                        }
                        val afterWriteMs = System.currentTimeMillis()
                        val totalWritten = writtenDavidTurnSamplesCount.addAndGet(len.toLong())
                        val hasMoreQueued = (offset + len < samples.size) || playbackQueue.isNotEmpty()
                        _audibleDavidTurnSamples.value = computeAudibleSamplesAt(
                            writtenSamples24k = totalWritten,
                            turnStartWallMs = turnPlaybackStartWallClockMs.get(),
                            lastWriteWallMs = afterWriteMs,
                            nowWallMs = afterWriteMs,
                            hasMoreQueuedAudio = hasMoreQueued
                        )
                        offset += len
                    }
                }
            } finally {
                synchronized(audioTrackLock) {
                    if (audioTrack === track) {
                        try {
                            track?.stop()
                        } catch (_: Exception) {
                        }
                        try {
                            track?.release()
                        } catch (_: Exception) {
                        }
                        audioTrack = null
                    }
                }
            }
        }
    }

    companion object {
        const val INPUT_SAMPLE_RATE_HZ = 16_000
        const val OUTPUT_SAMPLE_RATE_HZ = 24_000
        const val AUDIO_USAGE_ASSISTANT = AudioAttributes.USAGE_ASSISTANT
        const val AUDIO_CONTENT_TYPE_SPEECH = AudioAttributes.CONTENT_TYPE_SPEECH
        private const val USER_SPEECH_RMS_THRESHOLD = 0.009f
        private const val BARGE_IN_RMS_THRESHOLD = 0.08f
        private const val BARGE_IN_MIN_FRAMES = 6
        private const val PLAYBACK_ECHO_TAIL_MS = 320L
        internal const val BARGE_IN_TAIL_ECHO_SUPPRESS_MS = 220L
        internal const val AUDIO_TRACK_DAC_ONSET_MS = 100L
        internal const val AUDIO_TRACK_BUFFER_LEAD_SAMPLES = 4_800L // ~200ms of 24kHz AudioTrack ring buffer
        internal const val AUDIO_TRACK_DRAIN_TAIL_MS = 220L

        /**
         * Pre-amplifies microphone PCM16 samples exclusively for the spectrum visualizer (`spectrumAnalyzer`),
         * compensating for Android `VOICE_COMMUNICATION` hardware DSP attenuation so user speech drives
         * the under-sphere waveform as vividly as David's pre-mastered TTS audio.
         * Never mutates the raw microphone stream sent to Gemini Live.
         */
        internal fun boostMicSamplesForVisualizer(
            source: ShortArray,
            destination: ShortArray,
            count: Int,
            rms: Float
        ) {
            val safeCount = count.coerceIn(0, minOf(source.size, destination.size))
            if (rms < 0.0025f) {
                for (i in 0 until safeCount) {
                    destination[i] = source[i]
                }
                return
            }
            // Adaptive gain: stronger boost (up to 4.2x) for normal conversational mic levels (rms 0.01..0.045),
            // tapering smoothly for loud speech to avoid harsh clipping.
            val adaptiveGain = when {
                rms < 0.025f -> 4.2f
                rms < 0.065f -> 3.4f
                else -> 2.4f
            }
            for (i in 0 until safeCount) {
                val boosted = (source[i].toFloat() * adaptiveGain).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                destination[i] = boosted.toShort()
            }
        }

        /**
         * Computes responsive visualizer energy `[0f..1f]` when the user is speaking into the microphone.
         */
        internal fun computeUserVisualizerEnergy(fftEnergy: Float, micRms: Float): Float {
            if (micRms <= USER_SPEECH_RMS_THRESHOLD * 0.65f) {
                return fftEnergy.coerceIn(0f, 1f)
            }
            val normalizedRms = ((micRms - USER_SPEECH_RMS_THRESHOLD * 0.65f) / 0.042f).coerceIn(0f, 1f)
            val curvedRmsEnergy = kotlin.math.sqrt(normalizedRms)
            return maxOf(fftEnergy, curvedRmsEnergy).coerceIn(0f, 1f)
        }

        /**
         * Computes the number of 24 kHz PCM samples that have actually exited the speaker DAC
         * at [nowWallMs], accounting for [AudioTrack]'s internal ring buffer lead (~200 ms)
         * and hardware DAC onset latency (~100 ms) so subtitle text never races ahead of David's voice.
         */
        internal fun computeAudibleSamplesAt(
            writtenSamples24k: Long,
            turnStartWallMs: Long,
            lastWriteWallMs: Long,
            nowWallMs: Long,
            hasMoreQueuedAudio: Boolean
        ): Long {
            if (writtenSamples24k <= 0L || turnStartWallMs <= 0L) return 0L
            val elapsedSinceStartMs = (nowWallMs - turnStartWallMs - AUDIO_TRACK_DAC_ONSET_MS).coerceAtLeast(0L)
            val wallClockPlayedSamples = (elapsedSinceStartMs * OUTPUT_SAMPLE_RATE_HZ) / 1000L
            return if (hasMoreQueuedAudio) {
                val bufferCompensated = (writtenSamples24k - AUDIO_TRACK_BUFFER_LEAD_SAMPLES).coerceAtLeast(0L)
                minOf(wallClockPlayedSamples, bufferCompensated.coerceAtLeast(minOf(writtenSamples24k, 1_200L)))
            } else {
                val drainElapsedMs = (nowWallMs - lastWriteWallMs).coerceIn(0L, AUDIO_TRACK_DRAIN_TAIL_MS)
                val remainingLead = ((AUDIO_TRACK_BUFFER_LEAD_SAMPLES * (AUDIO_TRACK_DRAIN_TAIL_MS - drainElapsedMs)) / AUDIO_TRACK_DRAIN_TAIL_MS)
                    .coerceAtLeast(0L)
                val drainedSamples = (writtenSamples24k - remainingLead).coerceIn(0L, writtenSamples24k)
                minOf(writtenSamples24k, maxOf(wallClockPlayedSamples.coerceAtMost(writtenSamples24k), drainedSamples))
            }
        }

        /**
         * Builds the [AudioAttributes] tied to the AI Assistant / Neural Network volume channel
         * (`AudioAttributes.USAGE_ASSISTANT` + `AudioAttributes.CONTENT_TYPE_SPEECH`).
         */
        fun buildAssistantAudioAttributes(): AudioAttributes {
            return AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        }

        /**
         * Resolves the Android volume control stream for the AI Assistant (`USAGE_ASSISTANT`),
         * ensuring hardware volume buttons adjust the Neural Network / Assistant volume slider on Android 17+.
         */
        fun getAssistantVolumeStreamType(): Int {
            return try {
                buildAssistantAudioAttributes().volumeControlStream
            } catch (_: Exception) {
                AudioManager.STREAM_MUSIC
            }
        }

        /**
         * Requests transient assistant audio focus so the OS routes hardware volume keys
         * to the AI Assistant stream while the David Live overlay is open.
         */
        fun requestAssistantAudioFocus(context: Context): AudioFocusRequest? {
            return try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    ?: return null
                // Ensure the device is in MODE_NORMAL so it never hijacks volume buttons to STREAM_VOICE_CALL
                if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(buildAssistantAudioAttributes())
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { }
                        .build()
                    audioManager.requestAudioFocus(focusReq)
                    focusReq
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun abandonAssistantAudioFocus(context: Context, request: AudioFocusRequest?) {
            if (request == null) return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.abandonAudioFocusRequest(request)
                }
            } catch (_: Exception) {
            }
        }

        private fun defaultRestingRays(): List<Float> {
            val env = DavidLiveSpectrumAnalyzer().getRadialSpatialEnvelope()
            return List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { idx ->
                (0.14f * env.getOrElse(idx) { 0.5f }).coerceIn(0.05f, 0.22f)
            }
        }
    }
}
