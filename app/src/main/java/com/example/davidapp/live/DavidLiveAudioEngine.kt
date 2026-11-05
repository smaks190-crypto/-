package com.example.davidapp.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
import java.lang.Float.floatToIntBits
import java.lang.Float.intBitsToFloat
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
 *   [AcousticEchoCanceler] and [NoiseSuppressor] (while keeping [AutomaticGainControl] disabled on
 *   speakerphone so loudspeaker bleed is never amplified during user silence) without switching the system
 *   into phone-call mode (`MODE_IN_COMMUNICATION`).
 * - Tracks real-time 24 kHz model playback envelope, hardware buffer playout completion time,
 *   and speaker-to-microphone acoustic coupling to prevent false self-interruption (echo barge-in)
 *   at high loudspeaker volumes while preserving responsive interruption when the user actually speaks.
 */
class DavidLiveAudioEngine(
    private val spectrumAnalyzer: DavidLiveSpectrumAnalyzer = DavidLiveSpectrumAnalyzer()
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    @Volatile
    private var audioManager: AudioManager? = null

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
    private val estimatedPlayoutEndEpochMs = AtomicLong(0L)
    private val playbackBurstStartEpochMs = AtomicLong(0L)
    private val bargeInEchoSuppressUntilMs = AtomicLong(0L)
    private val serverTurnSpeakingActive = AtomicBoolean(false)
    private val userBargedInActive = AtomicBoolean(false)
    private val wasDavidPlayingPrevFrame = AtomicBoolean(false)
    private val consecutiveBargeInFramesCount = AtomicInteger(0)
    private val consecutiveBargeInExcessFramesCount = AtomicInteger(0)
    private val lastConfirmedUserSpeechEpochMs = AtomicLong(0L)
    private val playbackReferenceRmsBits = AtomicInteger(floatToIntBits(0f))
    private val recentLoudPeakRmsBits = AtomicInteger(floatToIntBits(0f))
    private val lastLoudPlaybackOutputTimestamp = AtomicLong(0L)
    private val activePlaybackWindowInProgress = AtomicBoolean(false)
    private val queuedPlaybackSamples = AtomicLong(0L)
    private val postBargeInForwardUntilEpochMs = AtomicLong(0L)
    private val preBargeInRingBuffer = mutableListOf<ShortArray>()
    private val preBargeInRingLock = Any()
    private val estimatedEchoCouplingBits = AtomicInteger(floatToIntBits(DEFAULT_ECHO_COUPLING_RATIO))

    // Test overrides for deterministic unit verification of volume, headset routing, and AEC state
    @Volatile
    private var testSpeakerVolumeRatioOverride: Float? = null
    @Volatile
    private var testExternalHeadsetConnectedOverride: Boolean? = null
    @Volatile
    private var testHardwareAecEnabledOverride: Boolean? = null

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

    private val enqueuedDavidTurnSamplesCount = AtomicLong(0L)
    private val writtenDavidTurnSamplesCount = AtomicLong(0L)
    private val turnPlaybackStartWallClockMs = AtomicLong(0L)

    private val _enqueuedDavidTurnSamples = MutableStateFlow(0L)
    val enqueuedDavidTurnSamples: StateFlow<Long> = _enqueuedDavidTurnSamples.asStateFlow()

    private val _audibleDavidTurnSamples = MutableStateFlow(0L)
    val audibleDavidTurnSamples: StateFlow<Long> = _audibleDavidTurnSamples.asStateFlow()




    private val _isPlaybackDrained = MutableStateFlow(true)
    val isPlaybackDrained: StateFlow<Boolean> = _isPlaybackDrained.asStateFlow()

    private val _isWithinUserSpeechHold = MutableStateFlow(false)
    val isWithinUserSpeechHold: StateFlow<Boolean> = _isWithinUserSpeechHold.asStateFlow()

    private val _isWithinDavidSpeechHold = MutableStateFlow(false)
    val isWithinDavidSpeechHold: StateFlow<Boolean> = _isWithinDavidSpeechHold.asStateFlow()

    private val lastUserSpeechDetectedMs = AtomicLong(0L)
    private val lastDavidPlaybackActiveMs = AtomicLong(0L)

    internal data class CaptureFrameDecision(
        val micRms: Float,
        val playbackReferenceRms: Float,
        val adaptiveBargeInThresholdRms: Float,
        val davidCurrentlyPlaying: Boolean,
        val triggeredBargeIn: Boolean,
        val shouldForwardMicChunkToServer: Boolean,
        val suppressedAsEcho: Boolean,
        val preRollPcmFramesToForward: List<ShortArray> = emptyList()
    )

    /**
     * Binds the application [Context] so the engine can inspect real-time speaker volume levels
     * and audio output routing (built-in loudspeaker vs wired/Bluetooth headset).
     */
    fun bindAudioContext(context: Context) {
        if (audioManager == null) {
            audioManager = context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
    }

    /**
     * Notifies the audio engine whether the Gemini Live server is actively streaming a model turn
     * (before `turnComplete` or `interrupted`), preventing mid-turn network jitter gaps from
     * prematurely opening the microphone to speaker echo.
     */
    fun setServerTurnSpeakingActive(active: Boolean) {
        val wasActive = serverTurnSpeakingActive.getAndSet(active)
        if (active && !wasActive && !userBargedInActive.get()) {
            // Clear pre-turn user speech timestamp when David starts speaking so the user's
            // prior question is never mistaken for a mid-turn barge-in.
            lastConfirmedUserSpeechEpochMs.set(0L)
        }
        if (!active &&
            playbackQueue.isEmpty() &&
            !activePlaybackWindowInProgress.get() &&
            queuedPlaybackSamples.get() <= 0L
        ) {
            // Allow natural acoustic tail decay after server turn and local playback both complete
            val now = System.currentTimeMillis()
            val currentEnd = estimatedPlayoutEndEpochMs.get()
            if (currentEnd > now + MAX_POST_TURN_BUFFER_DRAIN_MS) {
                estimatedPlayoutEndEpochMs.set(now + MAX_POST_TURN_BUFFER_DRAIN_MS)
            }
        }
    }

    /**
     * Returns true if the user was confirmed speaking or barging in recently.
     */
    fun hasRecentConfirmedUserSpeech(
        nowMs: Long = System.currentTimeMillis(),
        windowMs: Long = 1800L
    ): Boolean {
        if (userBargedInActive.get()) return true
        val lastSpeech = lastConfirmedUserSpeechEpochMs.get()
        return lastSpeech > 0L && nowMs - lastSpeech <= windowMs
    }

    /**
     * Returns true when model audio chunks are still queued or actively playing through the speaker.
     */
    fun hasPendingOrActiveModelPlayback(nowMs: Long = System.currentTimeMillis()): Boolean {
        return playbackQueue.isNotEmpty() ||
            activePlaybackWindowInProgress.get() ||
            queuedPlaybackSamples.get() > 0L ||
            nowMs <= estimatedPlayoutEndEpochMs.get()
    }

    fun hasQueuedPlaybackChunks(): Boolean {
        return playbackQueue.isNotEmpty() ||
            activePlaybackWindowInProgress.get() ||
            queuedPlaybackSamples.get() > 0L
    }

    fun isEngineRunning(): Boolean = isRunning.get()

    /**
     * Returns true when an external wired, USB, or Bluetooth headset/earpiece is connected for output,
     * meaning loudspeaker-to-microphone acoustic coupling is negligible.
     */
    fun isExternalAudioOutputHeadsetConnected(): Boolean {
        testExternalHeadsetConnectedOverride?.let { return it }
        val am = audioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                outputs.any { dev ->
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_USB_DEVICE,
                        AudioDeviceInfo.TYPE_HEARING_AID -> true
                        else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                    }
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves the current output volume ratio (`0.0f..1.0f`) for the Assistant / media stream.
     * Queries both the Assistant stream (`USAGE_ASSISTANT`) and `STREAM_MUSIC`, returning the higher ratio
     * because on many Android devices `USAGE_ASSISTANT` maps to a separate stream index while actual
     * loudspeaker amplification is controlled by `STREAM_MUSIC` (or vice versa).
     * Defaults to a conservative high-volume loudspeaker assumption (`0.80f`) when [AudioManager] is unavailable.
     */
    fun getEffectiveSpeakerVolumeRatio(): Float {
        testSpeakerVolumeRatioOverride?.let { return it.coerceIn(0f, 1f) }
        if (isExternalAudioOutputHeadsetConnected()) return 0.25f
        val am = audioManager ?: return DEFAULT_SPEAKER_VOLUME_RATIO
        return try {
            val assistantStream = getAssistantVolumeStreamType()
            val assistantMax = am.getStreamMaxVolume(assistantStream).coerceAtLeast(1)
            val assistantCur = am.getStreamVolume(assistantStream).coerceIn(0, assistantMax)
            val assistantRatio = (assistantCur.toFloat() / assistantMax.toFloat()).coerceIn(0f, 1f)

            val musicMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val musicCur = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, musicMax)
            val musicRatio = (musicCur.toFloat() / musicMax.toFloat()).coerceIn(0f, 1f)

            maxOf(assistantRatio, musicRatio)
        } catch (_: Exception) {
            DEFAULT_SPEAKER_VOLUME_RATIO
        }
    }

    fun isHardwareAecActive(): Boolean {
        testHardwareAecEnabledOverride?.let { return it }
        return try {
            echoCanceler?.enabled == true
        } catch (_: Exception) {
            false
        }
    }

    fun isAutoGainControlActive(): Boolean {
        return try {
            autoGainControl?.enabled == true
        } catch (_: Exception) {
            false
        }
    }

    internal fun setSpeakerVolumeRatioForTest(ratio: Float?) {
        testSpeakerVolumeRatioOverride = ratio
        if (ratio != null) {
            val seededCoupling = (0.34f + 0.26f * ratio.coerceIn(0f, 1f)).coerceIn(0.18f, 1.05f)
            setEstimatedEchoCouplingRatio(seededCoupling)
        } else {
            setEstimatedEchoCouplingRatio(DEFAULT_ECHO_COUPLING_RATIO)
        }
    }

    internal fun setExternalHeadsetConnectedForTest(connected: Boolean?) {
        testExternalHeadsetConnectedOverride = connected
    }

    internal fun setHardwareAecEnabledForTest(enabled: Boolean?) {
        testHardwareAecEnabledOverride = enabled
    }

    internal fun getPlaybackReferenceRms(): Float = intBitsToFloat(playbackReferenceRmsBits.get())

    private fun setPlaybackReferenceRms(value: Float) {
        playbackReferenceRmsBits.set(floatToIntBits(value.coerceIn(0f, 1f)))
    }

    private fun getRecentLoudPeakRms(): Float = intBitsToFloat(recentLoudPeakRmsBits.get())

    private fun setRecentLoudPeakRms(value: Float) {
        recentLoudPeakRmsBits.set(floatToIntBits(value.coerceIn(0f, 1f)))
    }

    /**
     * Returns the effective playback reference RMS accounting for the `AudioTrack` hardware buffer
     * latency (~300–450 ms) and room reverberation tail (~250–300 ms) via [AUDIO_TRACK_ECHO_HOLD_MS].
     * Used consistently across both [computeAdaptiveBargeInThresholdRms] and [evaluateCaptureFrame]
     * so inter-word pauses written into the tail of `AudioTrack`'s buffer do not prematurely drop
     * `refRms` while the previous loud word is still exiting the physical loudspeaker.
     */
    internal fun getEffectiveEchoReferenceRms(nowMs: Long = System.currentTimeMillis()): Float {
        val rawRefRms = getPlaybackReferenceRms()
        if (isExternalAudioOutputHeadsetConnected()) return rawRefRms
        val volRatio = getEffectiveSpeakerVolumeRatio()
        val inLoudEchoHoldWindow = isDavidPlaybackOrEchoTailActive(nowMs) &&
            volRatio >= 0.65f &&
            lastLoudPlaybackOutputTimestamp.get() > 0L &&
            (nowMs - lastLoudPlaybackOutputTimestamp.get() in 0L..AUDIO_TRACK_ECHO_HOLD_MS)
        return if (inLoudEchoHoldWindow) {
            maxOf(rawRefRms, getRecentLoudPeakRms() * 0.74f)
        } else {
            rawRefRms
        }
    }

    private fun updatePlaybackReferenceEnvelope(
        windowRms: Float,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val current = getPlaybackReferenceRms()
        val updated = if (windowRms >= current) {
            current * 0.45f + windowRms * 0.55f
        } else {
            current * 0.91f + windowRms * 0.09f
        }
        setPlaybackReferenceRms(updated)
        if (windowRms >= 0.075f) {
            lastLoudPlaybackOutputTimestamp.set(nowMs)
            val prevPeak = getRecentLoudPeakRms()
            val nextPeak = if (windowRms >= prevPeak) {
                windowRms
            } else {
                prevPeak * 0.85f + windowRms * 0.15f
            }
            setRecentLoudPeakRms(nextPeak)
        } else if (nowMs - lastLoudPlaybackOutputTimestamp.get() > AUDIO_TRACK_ECHO_HOLD_MS) {
            setRecentLoudPeakRms(getRecentLoudPeakRms() * 0.88f)
        }
    }

    internal fun getEstimatedEchoCouplingRatio(): Float = intBitsToFloat(estimatedEchoCouplingBits.get())

    private fun setEstimatedEchoCouplingRatio(value: Float) {
        estimatedEchoCouplingBits.set(floatToIntBits(value.coerceIn(0.18f, 1.05f)))
    }

    private fun clearPreBargeInRingBuffer() {
        synchronized(preBargeInRingLock) {
            preBargeInRingBuffer.clear()
        }
    }

    private fun recordPreBargeInFrame(pcmSamples: ShortArray, sampleCount: Int) {
        val count = sampleCount.coerceIn(0, pcmSamples.size)
        if (count <= 0) return
        val copy = pcmSamples.copyOf(count)
        synchronized(preBargeInRingLock) {
            while (preBargeInRingBuffer.size >= PRE_BARGE_IN_RING_MAX_FRAMES) {
                preBargeInRingBuffer.removeAt(0)
            }
            preBargeInRingBuffer.add(copy)
        }
    }

    /**
     * Drains the rolling pre-barge-in onset frames (excluding the current frame, which is sent
     * immediately after) and prepends 2 frames (128 ms) of clean digital silence so Gemini Live's
     * server-side VAD detects a crisp speech onset and transcribes the very first word of the user's
     * interruption without clipping.
     */
    private fun drainPreBargeInFramesForServer(currentFrameSampleCount: Int): List<ShortArray> {
        synchronized(preBargeInRingLock) {
            if (preBargeInRingBuffer.isEmpty()) return emptyList()
            val buffered = preBargeInRingBuffer.toList()
            preBargeInRingBuffer.clear()
            val priorFrames = if (buffered.size > 1) buffered.dropLast(1) else emptyList()
            if (priorFrames.isEmpty()) return emptyList()
            val silenceLen = currentFrameSampleCount.coerceAtLeast(1024)
            val result = ArrayList<ShortArray>(priorFrames.size + 2)
            result.add(ShortArray(silenceLen))
            result.add(ShortArray(silenceLen))
            result.addAll(priorFrames)
            return result
        }
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
        onUserBargeIn: () -> Unit = {},
        onModelPlaybackDrained: () -> Unit = {}
    ) {
        if (isRunning.getAndSet(true)) return
        playbackGeneration.incrementAndGet()
        spectrumAnalyzer.reset()
        playbackQueue.clear()
        queuedPlaybackSamples.set(0L)
        postBargeInForwardUntilEpochMs.set(0L)
        clearPreBargeInRingBuffer()
        lastOutputChunkTimestamp.set(0L)
        estimatedPlayoutEndEpochMs.set(0L)
        playbackBurstStartEpochMs.set(0L)
        bargeInEchoSuppressUntilMs.set(0L)
        resetDavidTurnSampleCounters()
        _isPlaybackDrained.value = true
        _isWithinUserSpeechHold.value = false
        _isWithinDavidSpeechHold.value = false
        serverTurnSpeakingActive.set(false)
        userBargedInActive.set(false)
        wasDavidPlayingPrevFrame.set(false)
        consecutiveBargeInFramesCount.set(0)
        consecutiveBargeInExcessFramesCount.set(0)
        activePlaybackWindowInProgress.set(false)
        lastLoudPlaybackOutputTimestamp.set(0L)
        setRecentLoudPeakRms(0f)
        setPlaybackReferenceRms(0f)

        startPlaybackWorker(onModelPlaybackDrained)
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
    fun enqueueModelAudioPcm24k(pcmBytes: ByteArray, nowMs: Long = System.currentTimeMillis()) {
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
        val sampleTotal = enqueuedDavidTurnSamplesCount.addAndGet(sampleCount.toLong())
        _enqueuedDavidTurnSamples.value = sampleTotal
        _isPlaybackDrained.value = false
        queuedPlaybackSamples.addAndGet(sampleCount.toLong())
        // Seed the reference envelope immediately only when no playback reference is active yet;
        // once startPlaybackWorker is actively writing windows to AudioTrack, the playback worker
        // updates the reference envelope in real time so network pre-buffering never desynchronizes it.
        val seedEnvelopeImmediate = !isRunning.get() || getPlaybackReferenceRms() <= 0.001f
        recordModelOutputActivity(
            samples = samples,
            sampleRateHz = OUTPUT_SAMPLE_RATE_HZ,
            nowMs = nowMs,
            updateEnvelope = seedEnvelopeImmediate
        )
        playbackQueue.offer(samples)
    }

    private fun recordModelOutputActivity(
        samples: ShortArray,
        sampleRateHz: Int,
        nowMs: Long = System.currentTimeMillis(),
        updateEnvelope: Boolean = true
    ) {
        val prevEnd = estimatedPlayoutEndEpochMs.get()
        val wasIdle = prevEnd <= nowMs &&
            (lastOutputChunkTimestamp.get() == 0L || nowMs - lastOutputChunkTimestamp.get() > PLAYBACK_ECHO_TAIL_MS)
        if (wasIdle) {
            playbackBurstStartEpochMs.set(nowMs)
            if (!userBargedInActive.get()) {
                lastConfirmedUserSpeechEpochMs.set(0L)
            }
        }
        lastOutputChunkTimestamp.set(nowMs)
        val safeRate = sampleRateHz.coerceAtLeast(8_000)
        val durationMs = ((samples.size * 1000L) / safeRate).coerceAtLeast(16L)
        val baseStart = maxOf(nowMs, prevEnd)
        estimatedPlayoutEndEpochMs.set(
            (baseStart + durationMs).coerceAtMost(nowMs + MAX_BUFFERED_PLAYOUT_LOOKAHEAD_MS)
        )
        if (updateEnvelope) {
            updatePlaybackReferenceEnvelope(computePcmRms(samples, samples.size), nowMs)
        }
    }

    /**
     * Immediately flushes any queued or playing model audio (used on barge-in interruption).
     * Synchronizes AudioTrack reset (`pause()` -> `flush()` -> `play()`), increments [playbackGeneration],
     * and activates a short protective tail-echo suppression window so residual speaker audio
     * does not get captured by the microphone as a new user utterance.
     */
    fun clearModelPlaybackQueue(nowMs: Long = System.currentTimeMillis()) {
        resetDavidTurnSampleCounters()
        playbackGeneration.incrementAndGet()
        playbackQueue.clear()
        queuedPlaybackSamples.set(0L)
        lastOutputChunkTimestamp.set(0L)
        lastDavidPlaybackActiveMs.set(0L)
        _isPlaybackDrained.value = true
        _isWithinDavidSpeechHold.value = false
        estimatedPlayoutEndEpochMs.set(0L)
        playbackBurstStartEpochMs.set(0L)
        serverTurnSpeakingActive.set(false)
        consecutiveBargeInFramesCount.set(0)
        consecutiveBargeInExcessFramesCount.set(0)
        activePlaybackWindowInProgress.set(false)
        lastLoudPlaybackOutputTimestamp.set(0L)
        setRecentLoudPeakRms(0f)
        setPlaybackReferenceRms(0f)
        bargeInEchoSuppressUntilMs.set(nowMs + BARGE_IN_TAIL_ECHO_SUPPRESS_MS)
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
        _rayMagnitudes.value = defaultRestingRays()
        _overallEnergy.value = 0.14f
    }

    internal fun getPlaybackGenerationForTest(): Int = playbackGeneration.get()

    internal fun getBargeInEchoSuppressUntilMsForTest(): Long = bargeInEchoSuppressUntilMs.get()

    internal fun getEstimatedPlayoutEndEpochMsForTest(): Long = estimatedPlayoutEndEpochMs.get()

    internal fun isDavidPlaybackOrEchoTailActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (playbackQueue.isNotEmpty() ||
            activePlaybackWindowInProgress.get() ||
            queuedPlaybackSamples.get() > 0L ||
            serverTurnSpeakingActive.get()
        ) {
            return true
        }
        val playoutEnd = estimatedPlayoutEndEpochMs.get()
        if (playoutEnd > 0L && nowMs <= playoutEnd + PLAYBACK_ECHO_TAIL_MS) return true
        val lastChunk = lastOutputChunkTimestamp.get()
        return lastChunk > 0L && nowMs - lastChunk <= PLAYBACK_ECHO_TAIL_MS
    }

    /**
     * Computes the dynamic barge-in RMS threshold for the current playback reference level,
     * speaker volume ratio, headset routing, and hardware AEC availability.
     */
    internal fun computeAdaptiveBargeInThresholdRms(nowMs: Long = System.currentTimeMillis()): Float {
        if (isExternalAudioOutputHeadsetConnected()) {
            return BASE_BARGE_IN_RMS_THRESHOLD
        }
        val volRatio = getEffectiveSpeakerVolumeRatio()
        val refRms = getEffectiveEchoReferenceRms(nowMs)
        val coupling = getEstimatedEchoCouplingRatio()
        val expectedEchoRms = refRms * coupling
        // On built-in loudspeaker at >=70% volume in MODE_NORMAL (USAGE_ASSISTANT), Android HAL AEC
        // does not cancel full-scale loudspeaker bleed even when AcousticEchoCanceler reports enabled.
        val aecMargin = if (isHardwareAecActive() && volRatio < 0.70f) 1.38f else 1.58f
        val highVolExtraFloor = if (volRatio >= 0.65f && refRms >= 0.055f) {
            0.055f * ((volRatio - 0.50f) * 2f).coerceIn(0f, 1f)
        } else {
            0f
        }
        val dynamicThreshold = maxOf(
            BASE_BARGE_IN_RMS_THRESHOLD + highVolExtraFloor,
            expectedEchoRms * aecMargin + 0.032f
        )
        val burstStart = playbackBurstStartEpochMs.get()
        val inOnsetGuard = isDavidPlaybackOrEchoTailActive(nowMs) &&
            volRatio >= 0.65f &&
            burstStart > 0L &&
            (nowMs - burstStart in 0L..PLAYBACK_ONSET_GUARD_MS)
        val withOnsetGuard = if (inOnsetGuard) {
            maxOf(dynamicThreshold, ONSET_HIGH_VOLUME_GUARD_RMS)
        } else {
            dynamicThreshold
        }
        return withOnsetGuard.coerceIn(BASE_BARGE_IN_RMS_THRESHOLD, MAX_ADAPTIVE_BARGE_IN_RMS_THRESHOLD)
    }

    internal fun shouldSuppressTailEchoFrame(
        rms: Float,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val threshold = computeAdaptiveBargeInThresholdRms(nowMs).coerceAtLeast(BARGE_IN_RMS_THRESHOLD)
        return nowMs < bargeInEchoSuppressUntilMs.get() && rms < threshold
    }

    /**
     * Evaluates a single captured 16 kHz microphone PCM frame for voice activity, acoustic echo suppression,
     * and user barge-in detection. Extracted for deterministic unit testing and real-time capture use.
     */
    internal fun evaluateCaptureFrame(
        pcmSamples: ShortArray,
        sampleCount: Int = pcmSamples.size,
        nowMs: Long = System.currentTimeMillis(),
        onUserBargeIn: () -> Unit = {}
    ): CaptureFrameDecision {
        val rms = computePcmRms(pcmSamples, sampleCount)
        val davidCurrentlyPlaying = isDavidPlaybackOrEchoTailActive(nowMs)
        val wasPlaying = wasDavidPlayingPrevFrame.getAndSet(davidCurrentlyPlaying)

        if (!davidCurrentlyPlaying) {
            userBargedInActive.set(false)
            consecutiveBargeInFramesCount.set(0)
            consecutiveBargeInExcessFramesCount.set(0)
            clearPreBargeInRingBuffer()
            setPlaybackReferenceRms(getPlaybackReferenceRms() * 0.72f)
        } else if (!wasPlaying) {
            // Reset any pre-existing voice frame counts and pre-turn speech timestamps when David
            // starts/resumes speaking so user speech before David's turn never carries over as a false barge-in.
            userBargedInActive.set(false)
            lastConfirmedUserSpeechEpochMs.set(0L)
            postBargeInForwardUntilEpochMs.set(0L)
            consecutiveBargeInFramesCount.set(0)
            consecutiveBargeInExcessFramesCount.set(0)
            clearPreBargeInRingBuffer()
            if (playbackBurstStartEpochMs.get() <= 0L) {
                playbackBurstStartEpochMs.set(nowMs)
            }
        }

        val refRms = getEffectiveEchoReferenceRms(nowMs)
        val headset = isExternalAudioOutputHeadsetConnected()
        val volRatio = getEffectiveSpeakerVolumeRatio()

        // Self-calibrate acoustic coupling ratio when microphone signal tracks speaker playback
        if (!headset && davidCurrentlyPlaying && refRms >= 0.045f) {
            val curCoupling = getEstimatedEchoCouplingRatio()
            val burstStart = playbackBurstStartEpochMs.get()
            val inOnset = burstStart > 0L && (nowMs - burstStart in 0L..PLAYBACK_ONSET_GUARD_MS)
            val looksLikeProportionalEcho = (inOnset && rms < 0.29f) ||
                rms <= (refRms * curCoupling * 1.65f + 0.055f)
            if (looksLikeProportionalEcho && rms > 0.01f) {
                val observedRatio = (rms / refRms).coerceIn(0.18f, 1.05f)
                val alpha = if (inOnset) 0.28f else 0.16f
                setEstimatedEchoCouplingRatio(curCoupling * (1f - alpha) + observedRatio * alpha)
            }
        }

        val adaptiveThreshold = computeAdaptiveBargeInThresholdRms(nowMs)
        var triggeredBargeIn = false
        var preRollFrames: List<ShortArray> = emptyList()

        if (davidCurrentlyPlaying && !userBargedInActive.get()) {
            recordPreBargeInFrame(pcmSamples, sampleCount)
            if (rms > adaptiveThreshold) {
                val frames = consecutiveBargeInFramesCount.incrementAndGet()
                val isLowRefSyllableGapAllowed = if (!headset && volRatio >= 0.65f) {
                    refRms < 0.055f && rms >= 0.16f
                } else {
                    refRms < 0.055f
                }
                val isSyllableGapOrStrongNearField =
                    isLowRefSyllableGapAllowed || rms >= adaptiveThreshold * STRONG_NEAR_FIELD_EXCESS_MULTIPLIER
                val excessFrames = if (isSyllableGapOrStrongNearField) {
                    consecutiveBargeInExcessFramesCount.incrementAndGet()
                } else {
                    consecutiveBargeInExcessFramesCount.get()
                }
                val requiredFrames = when {
                    headset || volRatio < 0.65f -> BARGE_IN_MIN_FRAMES
                    excessFrames >= 2 -> BARGE_IN_MIN_FRAMES
                    else -> HIGH_VOLUME_BARGE_IN_MIN_FRAMES
                }
                if (frames >= requiredFrames) {
                    userBargedInActive.set(true)
                    lastConfirmedUserSpeechEpochMs.set(nowMs)
                    postBargeInForwardUntilEpochMs.set(nowMs + POST_BARGE_IN_FORWARD_HOLD_MS)
                    consecutiveBargeInFramesCount.set(0)
                    consecutiveBargeInExcessFramesCount.set(0)
                    triggeredBargeIn = true
                    preRollFrames = drainPreBargeInFramesForServer(sampleCount)
                    clearModelPlaybackQueue(nowMs)
                    onUserBargeIn()
                }
            } else {
                val expectedEchoRms = refRms * getEstimatedEchoCouplingRatio()
                if (volRatio >= 0.65f || rms <= expectedEchoRms * 1.15f || rms < BASE_BARGE_IN_RMS_THRESHOLD) {
                    // At high loudspeaker volume (>=65%), any frame that drops back below adaptiveThreshold
                    // immediately resets the consecutive barge-in counters so isolated syllable peaks across
                    // a 20–40 second answer can never ratchet up and self-interrupt David.
                    consecutiveBargeInFramesCount.set(0)
                    consecutiveBargeInExcessFramesCount.set(0)
                } else {
                    consecutiveBargeInFramesCount.updateAndGet { maxOf(0, it - 1) }
                }
            }
        }

        // Smoothly decay the playback reference envelope across capture frames (~64 ms)
        // when no new playback window was written in the last 45 ms.
        if (nowMs - lastOutputChunkTimestamp.get() > 45L && getPlaybackReferenceRms() > 0f) {
            setPlaybackReferenceRms(getPlaybackReferenceRms() * 0.91f)
        }

        val lastSpeechMs = lastConfirmedUserSpeechEpochMs.get()
        val inPostBargeInForwardWindow = nowMs <= postBargeInForwardUntilEpochMs.get()
        val isContinuingUserBargeInSpeech = userBargedInActive.get() ||
            triggeredBargeIn ||
            inPostBargeInForwardWindow ||
            (lastSpeechMs > 0L && nowMs - lastSpeechMs in 0L..900L && rms >= USER_SPEECH_RMS_THRESHOLD)

        if (shouldSuppressTailEchoFrame(rms, nowMs) && !isContinuingUserBargeInSpeech) {
            return CaptureFrameDecision(
                micRms = rms,
                playbackReferenceRms = refRms,
                adaptiveBargeInThresholdRms = adaptiveThreshold,
                davidCurrentlyPlaying = davidCurrentlyPlaying,
                triggeredBargeIn = false,
                shouldForwardMicChunkToServer = false,
                suppressedAsEcho = true
            )
        }

        val bargedIn = userBargedInActive.get() || triggeredBargeIn
        val shouldForward = !davidCurrentlyPlaying || bargedIn || inPostBargeInForwardWindow
        if (shouldForward && rms >= BASE_BARGE_IN_RMS_THRESHOLD) {
            lastConfirmedUserSpeechEpochMs.set(nowMs)
            if (inPostBargeInForwardWindow || bargedIn) {
                postBargeInForwardUntilEpochMs.set(
                    maxOf(postBargeInForwardUntilEpochMs.get(), nowMs + POST_BARGE_IN_SPEECH_EXTENSION_MS)
                )
            }
        } else if (shouldForward && inPostBargeInForwardWindow && rms >= USER_SPEECH_RMS_THRESHOLD) {
            postBargeInForwardUntilEpochMs.set(
                maxOf(postBargeInForwardUntilEpochMs.get(), nowMs + POST_BARGE_IN_SPEECH_EXTENSION_MS)
            )
        }

        return CaptureFrameDecision(
            micRms = rms,
            playbackReferenceRms = refRms,
            adaptiveBargeInThresholdRms = adaptiveThreshold,
            davidCurrentlyPlaying = davidCurrentlyPlaying && !triggeredBargeIn,
            triggeredBargeIn = triggeredBargeIn,
            shouldForwardMicChunkToServer = shouldForward,
            suppressedAsEcho = !shouldForward,
            preRollPcmFramesToForward = preRollFrames
        )
    }

    /**
     * Allows unit/UI tests to feed a synthetic PCM frame directly into the spectrum analyzer.
     */
    fun feedTestPcmFrame(
        samples: ShortArray,
        sampleRateHz: Int = INPUT_SAMPLE_RATE_HZ,
        speaker: DavidLiveSpeaker = DavidLiveSpeaker.USER,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val now = System.currentTimeMillis()
        if (speaker == DavidLiveSpeaker.DAVID) {
            val totalWritten = writtenDavidTurnSamplesCount.addAndGet(samples.size.toLong())
            enqueuedDavidTurnSamplesCount.updateAndGet { maxOf(it, totalWritten) }
            _enqueuedDavidTurnSamples.value = enqueuedDavidTurnSamplesCount.get()
            _audibleDavidTurnSamples.value = totalWritten
            recordModelOutputActivity(samples, sampleRateHz, nowMs, updateEnvelope = true)
        }
        _activeSpeaker.value = speaker
        if (speaker == DavidLiveSpeaker.USER) {
            lastUserSpeechDetectedMs.set(now)
            _isWithinUserSpeechHold.value = true
        } else if (speaker == DavidLiveSpeaker.DAVID) {
            lastDavidPlaybackActiveMs.set(now)
            _isPlaybackDrained.value = false
            _isWithinDavidSpeechHold.value = true
        } else {
            _isWithinUserSpeechHold.value = false
            _isWithinDavidSpeechHold.value = false
            _isPlaybackDrained.value = true
        }
        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(samples, samples.size, sampleRateHz)
        _overallEnergy.value = spectrumAnalyzer.overallEnergy
    }

    fun resetDavidTurnSampleCounters() {
        enqueuedDavidTurnSamplesCount.set(0L)
        writtenDavidTurnSamplesCount.set(0L)
        turnPlaybackStartWallClockMs.set(0L)
        _enqueuedDavidTurnSamples.value = 0L
        _audibleDavidTurnSamples.value = 0L
    }

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

    fun isPlaybackCurrentlyDrained(): Boolean = _isPlaybackDrained.value && playbackQueue.isEmpty()

    /**
     * Unconditionally resets speaker and visualizer state and stops active audio workers if running.
     * Safe to call even when the engine was not started or after a WebSocket failure.
     */
    fun stop() {
        val wasRunning = isRunning.getAndSet(false)
        playbackGeneration.incrementAndGet()
        playbackQueue.clear()
        queuedPlaybackSamples.set(0L)
        postBargeInForwardUntilEpochMs.set(0L)
        clearPreBargeInRingBuffer()
        lastOutputChunkTimestamp.set(0L)
        estimatedPlayoutEndEpochMs.set(0L)
        playbackBurstStartEpochMs.set(0L)
        bargeInEchoSuppressUntilMs.set(0L)
        serverTurnSpeakingActive.set(false)
        userBargedInActive.set(false)
        wasDavidPlayingPrevFrame.set(false)
        consecutiveBargeInFramesCount.set(0)
        consecutiveBargeInExcessFramesCount.set(0)
        activePlaybackWindowInProgress.set(false)
        lastLoudPlaybackOutputTimestamp.set(0L)
        setRecentLoudPeakRms(0f)
        setPlaybackReferenceRms(0f)

        if (wasRunning) {
            captureJob?.cancel()
            playbackJob?.cancel()
            engineScope.coroutineContext.cancelChildren()

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

    private fun encodeShortArrayToPcm16LeBytes(samples: ShortArray, sampleCount: Int = samples.size): ByteArray {
        val count = sampleCount.coerceIn(0, samples.size)
        val pcmBytes = ByteArray(count * 2)
        var bIdx = 0
        for (i in 0 until count) {
            val s = samples[i].toInt()
            pcmBytes[bIdx] = (s and 0xFF).toByte()
            pcmBytes[bIdx + 1] = ((s shr 8) and 0xFF).toByte()
            bIdx += 2
        }
        return pcmBytes
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
                    if (!isDavidPlaybackOrEchoTailActive(System.currentTimeMillis())) {
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

                // 1024 samples @ 16 kHz = 64 ms chunk
                val shortBuffer = ShortArray(512)
                val micVisualizerBuffer = ShortArray(512)

                while (isActive && isRunning.get()) {
                    val readStartMs = System.currentTimeMillis()
                    val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount <= 0) {
                        delay(15L)
                        continue
                    }

                    if (isMicMuted.get()) {
                        val davidSpeaking = isDavidPlaybackOrEchoTailActive(System.currentTimeMillis())
                        if (!davidSpeaking) {
                            _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                            _overallEnergy.value = spectrumAnalyzer.overallEnergy
                            _activeSpeaker.value = DavidLiveSpeaker.NONE
                            _isWithinUserSpeechHold.value = false
                        }
                        if (System.currentTimeMillis() - readStartMs < 10L) {
                            delay(32L)
                        }
                        continue
                    }

                    val rawRms = computePcmRms(shortBuffer, readCount)
                    val nowMs = System.currentTimeMillis()
                    // Under Robolectric unit tests, ShadowAudioRecord.read() returns 1024 zero samples
                    // in 0ms without blocking. Throttle synthetic 0ms zero reads to prevent CPU/heap spin
                    // and avoid overwriting synthetic test speaker states.
                    if (nowMs - readStartMs < 10L && rawRms == 0f) {
                        delay(32L)
                        continue
                    }

                    val decision = evaluateCaptureFrame(
                        pcmSamples = shortBuffer,
                        sampleCount = readCount,
                        nowMs = nowMs,
                        onUserBargeIn = onUserBargeIn
                    )

                    if (decision.suppressedAsEcho && !decision.davidCurrentlyPlaying) {
                        _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                        _overallEnergy.value = spectrumAnalyzer.overallEnergy
                        _activeSpeaker.value = DavidLiveSpeaker.NONE
                        _isWithinUserSpeechHold.value = false
                        continue
                    }

                    // Update visualizer from user's microphone when David is not currently speaking
                    if (!decision.davidCurrentlyPlaying && isRunning.get()) {
                        boostMicSamplesForVisualizer(shortBuffer, micVisualizerBuffer, readCount, decision.micRms)
                        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(
                            pcmSamples = micVisualizerBuffer,
                            sampleCount = readCount,
                            sampleRateHz = INPUT_SAMPLE_RATE_HZ
                        )
                        _overallEnergy.value = computeUserVisualizerEnergy(spectrumAnalyzer.overallEnergy, decision.micRms)
                        if (decision.micRms > USER_SPEECH_RMS_THRESHOLD) {
                            lastUserSpeechDetectedMs.set(nowMs)
                            _isWithinUserSpeechHold.value = true
                        }
                        _activeSpeaker.value = if (decision.micRms > USER_SPEECH_RMS_THRESHOLD) {
                            DavidLiveSpeaker.USER
                        } else {
                            val withinHold =
                                lastUserSpeechDetectedMs.get() > 0L &&
                                    nowMs - lastUserSpeechDetectedMs.get() <= DavidLiveVisualStateResolver.SPEECH_PAUSE_HOLD_MS
                            _isWithinUserSpeechHold.value = withinHold
                            if (withinHold) DavidLiveSpeaker.USER else DavidLiveSpeaker.NONE
                        }
                    } else {
                        _isWithinUserSpeechHold.value = false
                    }

                    if (!decision.shouldForwardMicChunkToServer) continue

                    // If this frame confirmed a user barge-in, flush the pre-roll onset frames first
                    // so the beginning of the user's interrupting utterance is preserved on the server.
                    if (decision.preRollPcmFramesToForward.isNotEmpty() && isRunning.get()) {
                        for (preFrame in decision.preRollPcmFramesToForward) {
                            if (!isRunning.get()) break
                            onMicPcmChunk(encodeShortArrayToPcm16LeBytes(preFrame, preFrame.size))
                        }
                    }

                    // Convert ShortArray to little-endian PCM16 ByteArray and stream to Gemini Live
                    val pcmBytes = encodeShortArrayToPcm16LeBytes(shortBuffer, readCount)
                    if (isRunning.get()) {
                        onMicPcmChunk(pcmBytes)
                    }
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
                if (!isRunning.get()) {
                    _activeSpeaker.value = DavidLiveSpeaker.NONE
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
            // Explicitly disable AutomaticGainControl (AGC) if created by the HAL:
            // on speakerphone (USAGE_ASSISTANT in MODE_NORMAL), AGC boosts microphone gain during
            // user silence, amplifying David's loudspeaker output into false barge-in triggers.
            if (AutomaticGainControl.isAvailable()) {
                autoGainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = false }
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

    private fun startPlaybackWorker(onModelPlaybackDrained: () -> Unit = {}) {
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
                        activePlaybackWindowInProgress.set(false)
                        queuedPlaybackSamples.set(0L)
                        val now = System.currentTimeMillis()
                        val written = writtenDavidTurnSamplesCount.get()
                        if (written > 0L) {
                            _audibleDavidTurnSamples.value = computeAudibleSamplesAt(
                                written, turnPlaybackStartWallClockMs.get(), lastOutputChunkTimestamp.get(), now, false
                            )
                        }
                        val playoutFinished = now > estimatedPlayoutEndEpochMs.get() + 120L &&
                            now - lastOutputChunkTimestamp.get() > 220L
                        if (!serverTurnSpeakingActive.get() &&
                            playoutFinished &&
                            _activeSpeaker.value == DavidLiveSpeaker.DAVID
                        ) {
                            _audibleDavidTurnSamples.value = writtenDavidTurnSamplesCount.get()
                            _isPlaybackDrained.value = true
                            _isWithinDavidSpeechHold.value = false
                            _activeSpeaker.value = DavidLiveSpeaker.NONE
                            setPlaybackReferenceRms(0f)
                            setRecentLoudPeakRms(0f)
                            _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                            _overallEnergy.value = spectrumAnalyzer.overallEnergy
                            onModelPlaybackDrained()
                        }
                        delay(12L)
                        continue
                    }

                    activePlaybackWindowInProgress.set(true)
                    // Slice into ~1024-sample windows so the circular visualizer animates smoothly at ~23 fps
                    var offset = 0
                    val step = 1024
                    while (offset < samples.size && isRunning.get() && generation == playbackGeneration.get()) {
                        val len = (samples.size - offset).coerceAtMost(step)
                        val window = samples.copyOfRange(offset, offset + len)
                        if (!isRunning.get() || generation != playbackGeneration.get()) break
                        val wallStartMs = System.currentTimeMillis()
                        turnPlaybackStartWallClockMs.compareAndSet(0L, wallStartMs)
                        val writeStartMs = maxOf(wallStartMs, lastOutputChunkTimestamp.get())
                        val remainingInChunk = (samples.size - offset).coerceAtLeast(window.size).toLong()
                        val totalRemainingSamples = maxOf(remainingInChunk, queuedPlaybackSamples.get())
                        val remainingChunkMs = ((totalRemainingSamples * 1000L) / OUTPUT_SAMPLE_RATE_HZ).coerceAtLeast(16L)
                        val minPlayoutEnd = writeStartMs + remainingChunkMs + 260L
                        estimatedPlayoutEndEpochMs.updateAndGet { maxOf(it, minPlayoutEnd) }
                        lastOutputChunkTimestamp.set(writeStartMs)
                        updatePlaybackReferenceEnvelope(computePcmRms(window, window.size), writeStartMs)
                        lastDavidPlaybackActiveMs.set(writeStartMs)
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
                        val totalWritten = writtenDavidTurnSamplesCount.addAndGet(len.toLong())
                        val afterWriteMs = System.currentTimeMillis()
                        _audibleDavidTurnSamples.value = computeAudibleSamplesAt(
                            totalWritten, turnPlaybackStartWallClockMs.get(), afterWriteMs, afterWriteMs,
                            (offset + len < samples.size) || playbackQueue.isNotEmpty()
                        )
                        queuedPlaybackSamples.updateAndGet { maxOf(0L, it - len.toLong()) }
                        val windowDurationMs = ((window.size * 1000L) / OUTPUT_SAMPLE_RATE_HZ).coerceAtLeast(16L)
                        val writeElapsedMs = System.currentTimeMillis() - wallStartMs
                        if (writeElapsedMs + 6L < windowDurationMs &&
                            generation == playbackGeneration.get() &&
                            isRunning.get()
                        ) {
                            delay((windowDurationMs - writeElapsedMs - 4L).coerceIn(4L, 36L))
                        }
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
                if (!isRunning.get() || playbackQueue.isEmpty()) {
                    _activeSpeaker.value = DavidLiveSpeaker.NONE
                }
            }
        }
    }

    companion object {
        internal const val AUDIO_TRACK_DAC_ONSET_MS = 100L
        internal const val AUDIO_TRACK_BUFFER_LEAD_SAMPLES = 4_800L
        internal const val AUDIO_TRACK_DRAIN_TAIL_MS = 220L
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

        internal fun computeUserVisualizerEnergy(fftEnergy: Float, micRms: Float): Float {
            if (micRms <= USER_SPEECH_RMS_THRESHOLD * 0.65f) {
                return fftEnergy.coerceIn(0f, 1f)
            }
            val normalizedRms = ((micRms - USER_SPEECH_RMS_THRESHOLD * 0.65f) / 0.042f).coerceIn(0f, 1f)
            val curvedRmsEnergy = kotlin.math.sqrt(normalizedRms)
            return maxOf(fftEnergy, curvedRmsEnergy).coerceIn(0f, 1f)
        }

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


        const val INPUT_SAMPLE_RATE_HZ = 16_000
        const val OUTPUT_SAMPLE_RATE_HZ = 24_000
        const val AUDIO_USAGE_ASSISTANT = AudioAttributes.USAGE_ASSISTANT
        const val AUDIO_CONTENT_TYPE_SPEECH = AudioAttributes.CONTENT_TYPE_SPEECH
        private const val USER_SPEECH_RMS_THRESHOLD = 0.009f
        internal const val BARGE_IN_RMS_THRESHOLD = 0.08f
        internal const val BASE_BARGE_IN_RMS_THRESHOLD = 0.08f
        internal const val MAX_ADAPTIVE_BARGE_IN_RMS_THRESHOLD = 0.30f
        internal const val STRONG_NEAR_FIELD_EXCESS_MULTIPLIER = 1.12f
        internal const val ONSET_HIGH_VOLUME_GUARD_RMS = 0.23f
        internal const val BARGE_IN_MIN_FRAMES = 4
        internal const val HIGH_VOLUME_BARGE_IN_MIN_FRAMES = 5
        internal const val PRE_BARGE_IN_RING_MAX_FRAMES = 6
        internal const val DEFAULT_ECHO_COUPLING_RATIO = 0.52f
        internal const val DEFAULT_SPEAKER_VOLUME_RATIO = 0.80f
        internal const val PLAYBACK_ONSET_GUARD_MS = 220L
        internal const val AUDIO_TRACK_ECHO_HOLD_MS = 780L
        internal const val PLAYBACK_ECHO_TAIL_MS = 480L
        internal const val BARGE_IN_TAIL_ECHO_SUPPRESS_MS = 320L
        private const val POST_BARGE_IN_FORWARD_HOLD_MS = 4_500L
        private const val POST_BARGE_IN_SPEECH_EXTENSION_MS = 2_200L
        private const val MAX_BUFFERED_PLAYOUT_LOOKAHEAD_MS = 60_000L
        private const val MAX_POST_TURN_BUFFER_DRAIN_MS = 650L

        internal fun computePcmRms(pcmSamples: ShortArray, sampleCount: Int = pcmSamples.size): Float {
            val count = sampleCount.coerceIn(0, pcmSamples.size)
            if (count <= 0) return 0f
            var sumSq = 0.0
            for (i in 0 until count) {
                val norm = pcmSamples[i] / 32768.0
                sumSq += norm * norm
            }
            return sqrt(sumSq / count).toFloat()
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
