package com.example.davidapp.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Full-duplex audio streaming and real-time FFT spectrum engine for David Live.
 *
 * - Captures 16 kHz / 16-bit / Mono PCM microphone audio via [AudioRecord] (VOICE_COMMUNICATION)
 *   and streams chunks to Gemini Live while feeding [DavidLiveSpectrumAnalyzer].
 * - Plays 24 kHz / 16-bit / Mono PCM voice chunks from Gemini Live via [AudioTrack]
 *   while feeding [DavidLiveSpectrumAnalyzer] so the circular visualizer responds to
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

    private val playbackQueue = ConcurrentLinkedQueue<ShortArray>()
    private val isRunning = AtomicBoolean(false)
    private val isMicMuted = AtomicBoolean(false)
    private val lastOutputChunkTimestamp = AtomicLong(0L)

    private val _rayMagnitudes = MutableStateFlow(
        List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.04f }
    )
    val rayMagnitudes: StateFlow<List<Float>> = _rayMagnitudes.asStateFlow()

    private val _overallEnergy = MutableStateFlow(0f)
    val overallEnergy: StateFlow<Float> = _overallEnergy.asStateFlow()

    private val _activeSpeaker = MutableStateFlow(DavidLiveSpeaker.NONE)
    val activeSpeaker: StateFlow<DavidLiveSpeaker> = _activeSpeaker.asStateFlow()

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

        startPlaybackWorker()
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
        playbackQueue.offer(samples)
    }

    /**
     * Immediately flushes any queued or playing model audio (used on barge-in interruption).
     */
    fun clearModelPlaybackQueue() {
        playbackQueue.clear()
        lastOutputChunkTimestamp.set(0L)
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (_: Exception) {
        }
        if (_activeSpeaker.value == DavidLiveSpeaker.DAVID) {
            _activeSpeaker.value = DavidLiveSpeaker.NONE
        }
    }

    /**
     * Allows unit/UI tests to feed a synthetic PCM frame directly into the spectrum analyzer.
     */
    fun feedTestPcmFrame(
        samples: ShortArray,
        sampleRateHz: Int = INPUT_SAMPLE_RATE_HZ,
        speaker: DavidLiveSpeaker = DavidLiveSpeaker.USER
    ) {
        _activeSpeaker.value = speaker
        _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(samples, samples.size, sampleRateHz)
        _overallEnergy.value = spectrumAnalyzer.overallEnergy
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        captureJob?.cancel()
        playbackJob?.cancel()
        engineScope.coroutineContext.cancelChildren()
        playbackQueue.clear()

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null

        spectrumAnalyzer.reset()
        _activeSpeaker.value = DavidLiveSpeaker.NONE
        _rayMagnitudes.value = List(DavidLiveSpectrumAnalyzer.RAY_COUNT) { 0.04f }
        _overallEnergy.value = 0f
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

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    INPUT_SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
            } catch (_: Exception) {
                null
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                try {
                    record?.release()
                } catch (_: Exception) {
                }
                // Keep decay loop active even on emulator without hardware mic
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
            try {
                record.startRecording()
            } catch (_: Exception) {
                return@launch
            }

            // 1024 samples @ 16 kHz = 64 ms chunk
            val shortBuffer = ShortArray(1024)
            var consecutiveVoiceFrames = 0

            while (isActive && isRunning.get()) {
                val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                if (readCount <= 0) {
                    delay(15L)
                    continue
                }

                if (isMicMuted.get()) {
                    val davidSpeaking = System.currentTimeMillis() - lastOutputChunkTimestamp.get() <= 160L
                    if (!davidSpeaking) {
                        _rayMagnitudes.value = spectrumAnalyzer.decayFrame()
                        _overallEnergy.value = spectrumAnalyzer.overallEnergy
                        _activeSpeaker.value = DavidLiveSpeaker.NONE
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

                val davidCurrentlyPlaying =
                    System.currentTimeMillis() - lastOutputChunkTimestamp.get() <= 180L ||
                        playbackQueue.isNotEmpty()

                if (rms > BARGE_IN_RMS_THRESHOLD) {
                    consecutiveVoiceFrames++
                    if (davidCurrentlyPlaying && consecutiveVoiceFrames >= 3) {
                        clearModelPlaybackQueue()
                        onUserBargeIn()
                        consecutiveVoiceFrames = 0
                    }
                } else {
                    consecutiveVoiceFrames = 0
                }

                // Update visualizer from user's microphone when David is not currently speaking
                if (!davidCurrentlyPlaying) {
                    _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(
                        pcmSamples = shortBuffer,
                        sampleCount = readCount,
                        sampleRateHz = INPUT_SAMPLE_RATE_HZ
                    )
                    _overallEnergy.value = spectrumAnalyzer.overallEnergy
                    _activeSpeaker.value = if (rms > USER_SPEECH_RMS_THRESHOLD) {
                        DavidLiveSpeaker.USER
                    } else {
                        DavidLiveSpeaker.NONE
                    }
                }

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
        }
    }

    private fun startPlaybackWorker() {
        playbackJob = engineScope.launch {
            val minOutBuf = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4800)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
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

            audioTrack = track
            try {
                track?.play()
            } catch (_: Exception) {
            }

            while (isActive && isRunning.get()) {
                val samples = playbackQueue.poll()
                if (samples == null) {
                    delay(12L)
                    continue
                }

                // Slice into ~1024-sample windows so the circular visualizer animates smoothly at ~23 fps
                var offset = 0
                val step = 1024
                while (offset < samples.size && isRunning.get()) {
                    val len = (samples.size - offset).coerceAtMost(step)
                    val window = samples.copyOfRange(offset, offset + len)
                    lastOutputChunkTimestamp.set(System.currentTimeMillis())
                    _activeSpeaker.value = DavidLiveSpeaker.DAVID
                    _rayMagnitudes.value = spectrumAnalyzer.analyzePcmFrame(
                        pcmSamples = window,
                        sampleCount = window.size,
                        sampleRateHz = OUTPUT_SAMPLE_RATE_HZ
                    )
                    _overallEnergy.value = spectrumAnalyzer.overallEnergy

                    try {
                        track?.write(window, 0, window.size)
                    } catch (_: Exception) {
                    }
                    offset += len
                }
            }
        }
    }

    companion object {
        const val INPUT_SAMPLE_RATE_HZ = 16_000
        const val OUTPUT_SAMPLE_RATE_HZ = 24_000
        private const val USER_SPEECH_RMS_THRESHOLD = 0.015f
        private const val BARGE_IN_RMS_THRESHOLD = 0.055f
    }
}
