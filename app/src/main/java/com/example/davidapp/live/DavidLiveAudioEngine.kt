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

    internal data class CaptureFrameDecision(
        val micRms: Float,
        val playbackReferenceRms: Float,
        val adaptiveBargeInThresholdRms: Float,
        val davidCurrentlyPlaying: Boolean,
        val triggeredBargeIn: Boolean,
        val shouldForwardMicChunkToServer: Boolean,
        val suppressedAsEcho: Boolean
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
        serverTurnSpeakingActive.set(active)
        if (!active && playbackQueue.isEmpty()) {
            // Allow natural acoustic tail decay after server turn completes
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
        return playbackQueue.isNotEmpty() || nowMs <= estimatedPlayoutEndEpochMs.get()
    }

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
     * Defaults to a conservative high-volume loudspeaker assumption (`0.80f`) when [AudioManager] is unavailable.
     */
    fun getEffectiveSpeakerVolumeRatio(): Float {
        testSpeakerVolumeRatioOverride?.let { return it.coerceIn(0f, 1f) }
        if (isExternalAudioOutputHeadsetConnected()) return 0.25f
        val am = audioManager ?: return DEFAULT_SPEAKER_VOLUME_RATIO
        return try {
            val stream = getAssistantVolumeStreamType()
            val maxVol = am.getStreamMaxVolume(stream).coerceAtLeast(1)
            val curVol = am.getStreamVolume(stream).coerceIn(0, maxVol)
            (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
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
            val seededCoupling = (0.34f + 0.26f * ratio.coerceIn(0f, 1f)).coerceIn(0.18f, 0.82f)
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

    private fun updatePlaybackReferenceEnvelope(windowRms: Float) {
        val current = getPlaybackReferenceRms()
        val updated = if (windowRms >= current) {
            current * 0.45f + windowRms * 0.55f
        } else {
            current * 0.86f + windowRms * 0.14f
        }
        setPlaybackReferenceRms(updated)
    }

    internal fun getEstimatedEchoCouplingRatio(): Float = intBitsToFloat(estimatedEchoCouplingBits.get())

    private fun setEstimatedEchoCouplingRatio(value: Float) {
        estimatedEchoCouplingBits.set(floatToIntBits(value.coerceIn(0.18f, 0.82f)))
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
        lastOutputChunkTimestamp.set(0L)
        estimatedPlayoutEndEpochMs.set(0L)
        playbackBurstStartEpochMs.set(0L)
        bargeInEchoSuppressUntilMs.set(0L)
        serverTurnSpeakingActive.set(false)
        userBargedInActive.set(false)
        wasDavidPlayingPrevFrame.set(false)
        consecutiveBargeInFramesCount.set(0)
        consecutiveBargeInExcessFramesCount.set(0)
        setPlaybackReferenceRms(0f)

        startPlaybackWorker(onModelPlaybackDrained)
        startCaptureWorker(onMicPcmChunk, onUserBargeIn)
    }

    fun setMicMuted(muted: Boolean) {
        isMicMuted.set(muted)
        if (muted && _activeSpeaker.value == DavidLiveSpeaker.USER) {
            _activeSpeaker.value = DavidLiveSpeaker.NONE
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
        recordModelOutputActivity(samples, OUTPUT_SAMPLE_RATE_HZ, nowMs)
        playbackQueue.offer(samples)
    }

    private fun recordModelOutputActivity(
        samples: ShortArray,
        sampleRateHz: Int,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val prevEnd = estimatedPlayoutEndEpochMs.get()
        val wasIdle = prevEnd <= nowMs &&
            (lastOutputChunkTimestamp.get() == 0L || nowMs - lastOutputChunkTimestamp.get() > PLAYBACK_ECHO_TAIL_MS)
        if (wasIdle) {
            playbackBurstStartEpochMs.set(nowMs)
        }
        lastOutputChunkTimestamp.set(nowMs)
        val safeRate = sampleRateHz.coerceAtLeast(8_000)
        val durationMs = ((samples.size * 1000L) / safeRate).coerceAtLeast(16L)
        val baseStart = maxOf(nowMs, prevEnd)
        estimatedPlayoutEndEpochMs.set(
            (baseStart + durationMs).coerceAtMost(nowMs + MAX_BUFFERED_PLAYOUT_LOOKAHEAD_MS)
        )
        updatePlaybackReferenceEnvelope(computePcmRms(samples, samples.size))
    }

    /**
     * Immediately flushes any queued or playing model audio (used on barge-in interruption).
     * Synchronizes AudioTrack reset (`pause()` -> `flush()` -> `play()`), increments [playbackGeneration],
     * and activates a short protective tail-echo suppression window so residual speaker audio
     * does not get captured by the microphone as a new user utterance.
     */
    fun clearModelPlaybackQueue(nowMs: Long = System.currentTimeMillis()) {
        playbackGeneration.incrementAndGet()
        playbackQueue.clear()
        lastOutputChunkTimestamp.set(0L)
        estimatedPlayoutEndEpochMs.set(0L)
        playbackBurstStartEpochMs.set(0L)
        serverTurnSpeakingActive.set(false)
        consecutiveBargeInFramesCount.set(0)
        consecutiveBargeInExcessFramesCount.set(0)
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
        if (playbackQueue.isNotEmpty() || serverTurnSpeakingActive.get()) return true
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
        val refRms = getPlaybackReferenceRms()
        val volRatio = getEffectiveSpeakerVolumeRatio()
        val coupling = getEstimatedEchoCouplingRatio()
        val expectedEchoRms = refRms * coupling
        val aecMargin = if (isHardwareAecActive()) 1.38f else 1.58f
        val highVolExtraFloor = if (volRatio >= 0.65f && refRms >= 0.055f) {
            0.045f * ((volRatio - 0.50f) * 2f).coerceIn(0f, 1f)
        } else {
            0f
        }
        val dynamicThreshold = maxOf(
            BASE_BARGE_IN_RMS_THRESHOLD + highVolExtraFloor,
            expectedEchoRms * aecMargin + 0.028f
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
            setPlaybackReferenceRms(getPlaybackReferenceRms() * 0.72f)
        } else if (!wasPlaying) {
            // Reset any pre-existing voice frame counts when David starts/resumes speaking
            // so user speech before David's turn never carries over as a false barge-in.
            userBargedInActive.set(false)
            consecutiveBargeInFramesCount.set(0)
            consecutiveBargeInExcessFramesCount.set(0)
            if (playbackBurstStartEpochMs.get() <= 0L) {
                playbackBurstStartEpochMs.set(nowMs)
            }
        }

        val refRms = getPlaybackReferenceRms()
        val headset = isExternalAudioOutputHeadsetConnected()
        val volRatio = getEffectiveSpeakerVolumeRatio()

        // Self-calibrate acoustic coupling ratio when microphone signal tracks speaker playback
        if (!headset && davidCurrentlyPlaying && refRms >= 0.045f) {
            val curCoupling = getEstimatedEchoCouplingRatio()
            val burstStart = playbackBurstStartEpochMs.get()
            val inOnset = burstStart > 0L && (nowMs - burstStart in 0L..PLAYBACK_ONSET_GUARD_MS)
            val looksLikeProportionalEcho = (inOnset && rms < 0.27f) ||
                rms <= (refRms * curCoupling * 1.36f + 0.026f)
            if (looksLikeProportionalEcho && rms > 0.01f) {
                val observedRatio = (rms / refRms).coerceIn(0.18f, 0.80f)
                val alpha = if (inOnset) 0.26f else 0.14f
                setEstimatedEchoCouplingRatio(curCoupling * (1f - alpha) + observedRatio * alpha)
            }
        }

        val adaptiveThreshold = computeAdaptiveBargeInThresholdRms(nowMs)
        var triggeredBargeIn = false

        if (davidCurrentlyPlaying && !userBargedInActive.get()) {
            if (rms > adaptiveThreshold) {
                val frames = consecutiveBargeInFramesCount.incrementAndGet()
                val isSyllableGapOrStrongNearField =
                    refRms < 0.055f || rms >= adaptiveThreshold * 1.16f
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
                    consecutiveBargeInFramesCount.set(0)
                    consecutiveBargeInExcessFramesCount.set(0)
                    triggeredBargeIn = true
                    clearModelPlaybackQueue(nowMs)
                    onUserBargeIn()
                }
            } else {
                val expectedEchoRms = refRms * getEstimatedEchoCouplingRatio()
                if (rms <= expectedEchoRms * 1.15f || rms < BASE_BARGE_IN_RMS_THRESHOLD) {
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
            setPlaybackReferenceRms(getPlaybackReferenceRms() * 0.86f)
        }

        if (shouldSuppressTailEchoFrame(rms, nowMs) && !triggeredBargeIn && !userBargedInActive.get()) {
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
        val shouldForward = !davidCurrentlyPlaying || bargedIn
        if (shouldForward && rms >= BASE_BARGE_IN_RMS_THRESHOLD) {
            lastConfirmedUserSpeechEpochMs.set(nowMs)
        }

        return CaptureFrameDecision(
            micRms = rms,
            playbackReferenceRms = refRms,
            adaptiveBargeInThresholdRms = adaptiveThreshold,
            davidCurrentlyPlaying = davidCurrentlyPlaying && !triggeredBargeIn,
            triggeredBargeIn = triggeredBargeIn,
            shouldForwardMicChunkToServer = shouldForward,
            suppressedAsEcho = !shouldForward
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
        if (speaker == DavidLiveSpeaker.DAVID) {
            recordModelOutputActivity(samples, sampleRateHz, nowMs)
        }
        _activeSpeaker.value = speaker
        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(samples, samples.size, sampleRateHz)
        _overallEnergy.value = spectrumAnalyzer.overallEnergy
    }

    /**
     * Unconditionally resets speaker and visualizer state and stops active audio workers if running.
     * Safe to call even when the engine was not started or after a WebSocket failure.
     */
    fun stop() {
        val wasRunning = isRunning.getAndSet(false)
        playbackGeneration.incrementAndGet()
        playbackQueue.clear()
        lastOutputChunkTimestamp.set(0L)
        estimatedPlayoutEndEpochMs.set(0L)
        playbackBurstStartEpochMs.set(0L)
        bargeInEchoSuppressUntilMs.set(0L)
        serverTurnSpeakingActive.set(false)
        userBargedInActive.set(false)
        wasDavidPlayingPrevFrame.set(false)
        consecutiveBargeInFramesCount.set(0)
        consecutiveBargeInExcessFramesCount.set(0)
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
                val shortBuffer = ShortArray(1024)

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
                        continue
                    }

                    // Update visualizer from user's microphone when David is not currently speaking
                    if (!decision.davidCurrentlyPlaying && isRunning.get()) {
                        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(
                            pcmSamples = shortBuffer,
                            sampleCount = readCount,
                            sampleRateHz = INPUT_SAMPLE_RATE_HZ
                        )
                        _overallEnergy.value = spectrumAnalyzer.overallEnergy
                        _activeSpeaker.value = if (decision.micRms > USER_SPEECH_RMS_THRESHOLD) {
                            DavidLiveSpeaker.USER
                        } else {
                            DavidLiveSpeaker.NONE
                        }
                    }

                    if (!decision.shouldForwardMicChunkToServer) continue

                    // Convert ShortArray to little-endian PCM16 ByteArray and stream to Gemini Live
                    val pcmBytes = ByteArray(readCount * 2)
                    var bIdx = 0
                    for (i in 0 until readCount) {
                        val s = shortBuffer[i].toInt()
                        pcmBytes[bIdx] = (s and 0xFF).toByte()
                        pcmBytes[bIdx + 1] = ((s shr 8) and 0xFF).toByte()
                        bIdx += 2
                    }
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
                        val now = System.currentTimeMillis()
                        val playoutFinished = now > estimatedPlayoutEndEpochMs.get() + 120L &&
                            now - lastOutputChunkTimestamp.get() > 220L
                        if (!serverTurnSpeakingActive.get() &&
                            playoutFinished &&
                            _activeSpeaker.value == DavidLiveSpeaker.DAVID
                        ) {
                            _activeSpeaker.value = DavidLiveSpeaker.NONE
                            setPlaybackReferenceRms(0f)
                            _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                            _overallEnergy.value = spectrumAnalyzer.overallEnergy
                            onModelPlaybackDrained()
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
                        if (!isRunning.get() || generation != playbackGeneration.get()) break
                        val writeStartMs = System.currentTimeMillis()
                        lastOutputChunkTimestamp.set(writeStartMs)
                        updatePlaybackReferenceEnvelope(computePcmRms(window, window.size))
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
                        val windowDurationMs = ((window.size * 1000L) / OUTPUT_SAMPLE_RATE_HZ).coerceAtLeast(16L)
                        val writeElapsedMs = System.currentTimeMillis() - writeStartMs
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
        const val INPUT_SAMPLE_RATE_HZ = 16_000
        const val OUTPUT_SAMPLE_RATE_HZ = 24_000
        const val AUDIO_USAGE_ASSISTANT = AudioAttributes.USAGE_ASSISTANT
        const val AUDIO_CONTENT_TYPE_SPEECH = AudioAttributes.CONTENT_TYPE_SPEECH
        private const val USER_SPEECH_RMS_THRESHOLD = 0.009f
        internal const val BARGE_IN_RMS_THRESHOLD = 0.08f
        internal const val BASE_BARGE_IN_RMS_THRESHOLD = 0.08f
        internal const val MAX_ADAPTIVE_BARGE_IN_RMS_THRESHOLD = 0.32f
        internal const val ONSET_HIGH_VOLUME_GUARD_RMS = 0.23f
        internal const val BARGE_IN_MIN_FRAMES = 4
        internal const val HIGH_VOLUME_BARGE_IN_MIN_FRAMES = 5
        internal const val DEFAULT_ECHO_COUPLING_RATIO = 0.52f
        internal const val DEFAULT_SPEAKER_VOLUME_RATIO = 0.80f
        internal const val PLAYBACK_ONSET_GUARD_MS = 220L
        internal const val PLAYBACK_ECHO_TAIL_MS = 480L
        internal const val BARGE_IN_TAIL_ECHO_SUPPRESS_MS = 320L
        private const val MAX_BUFFERED_PLAYOUT_LOOKAHEAD_MS = 2_600L
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
