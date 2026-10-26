package com.example.davidapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Менеджер звуковых эффектов для отправки, получения, системных сигналов
 * и процедурного синтеза звона золотой монетки Давида Жабова.
 */
class SoundManager(context: Context) {
    private val context: Context = context.applicationContext

    private var soundPool: SoundPool? = null
    private var sendSoundId: Int = 0
    private var receiveSoundId: Int = 0
    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build()

            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)

            val sendResId = context.resources.getIdentifier("send_sound", "raw", context.packageName)
            if (sendResId != 0) {
                sendSoundId = soundPool?.load(context, sendResId, 1) ?: 0
            }

            val recvResId = context.resources.getIdentifier("receive_sound", "raw", context.packageName)
            if (recvResId != 0) {
                receiveSoundId = soundPool?.load(context, recvResId, 1) ?: 0
            }
        } catch (e: Exception) {
            Log.w("DavidSoundManager", "Audio init fallback mode: ${e.message}")
        }
    }

    /**
     * Воспроизведение звука отправки сообщения
     */
    fun playSend() {
        try {
            if (sendSoundId != 0) {
                soundPool?.play(sendSoundId, 0.85f, 0.85f, 0, 0, 1.0f)
            } else {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
            }
        } catch (e: Exception) {
            Log.d("DavidSoundManager", "Error playing send sound: ${e.message}")
        }
    }

    /**
     * Воспроизведение звука получения ответа от Давида
     */
    fun playReceive() {
        try {
            if (receiveSoundId != 0) {
                soundPool?.play(receiveSoundId, 1f, 1f, 0, 0, 1.0f)
            } else {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 70)
            }
        } catch (e: Exception) {
            Log.d("DavidSoundManager", "Error playing receive sound: ${e.message}")
        }
    }

    /**
     * Звук нажатия на интерактивные кнопки или переключения периодов
     */
    fun playClick() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 25)
        } catch (e: Exception) {
            Log.d("DavidSoundManager", "Error playing click sound: ${e.message}")
        }
    }

    /**
     * Победный звук при разблокировке достижения (Ачивки)
     */
    fun playAchievement() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
        } catch (e: Exception) {
            Log.d("DavidSoundManager", "Error playing achievement sound: ${e.message}")
        }
    }

    /**
     * Вызов процедурного звона подброшенной золотой монетки («Дзынь!») на экземпляре класса
     */
    fun playCoinToss(context: Context? = this.context) {
        Companion.playCoinToss(context)
    }

    /**
     * Освобождение аудио-ресурсов
     */
    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.w("DavidSoundManager", "Error releasing sound pool: ${e.message}")
        }
    }

    companion object {
        private const val COIN_SAMPLE_RATE = 44100
        private const val COIN_DURATION_MS = 320
        private val synthScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Процедурно синтезирует и воспроизводит звонкий металлический звук подбрасывания
         * золотой монетки («Дзынь!») через AudioTrack (44100 Гц, 16-bit PCM, MODE_STATIC, ~320 мс).
         */
        fun playCoinToss(context: Context? = null) {
            synthScope.launch {
                var audioTrack: AudioTrack? = null
                try {
                    val totalSamples = (COIN_SAMPLE_RATE * COIN_DURATION_MS) / 1000
                    val pcmBuffer = ShortArray(totalSamples)

                    // Частоты четырех гармоник золотой монетки
                    val f1 = 2637.0 // Базовый тон звона (нота E7)
                    val f2 = 5274.0 // Октавный обертон (нота E8)
                    val f3 = 6880.0 // Металлический блеск (inharmonic coin shimmer 1)
                    val f4 = 9320.0 // Высокий звон гурта (inharmonic coin shimmer 2)
                    val twoPi = 2.0 * PI

                    for (i in 0 until totalSamples) {
                        val t = i.toDouble() / COIN_SAMPLE_RATE

                        // Мягкая атака за первые 1.2 мс против аудио-щелчка при старте буфера
                        val attackEnv = (t / 0.0012).coerceIn(0.0, 1.0)

                        // В первые 4 мс — легкий звонкий щелчок подброса (короткий импульс с быстрым спадом)
                        val flickImpulse = if (t < 0.004) {
                            0.38 * sin(twoPi * 3650.0 * t) * exp(-t * 850.0)
                        } else {
                            0.0
                        }

                        // Микро-модуляция громкости (эффект вращения монетки в воздухе 18 Гц)
                        val spinModulation = 1.0 + 0.15 * sin(twoPi * 18.0 * t)

                        // 4 гармоники с индивидуальным экспоненциальным затуханием
                        val tone1 = 1.00 * sin(twoPi * f1 * t) * exp(-t * 10.5)
                        val tone2 = 0.45 * sin(twoPi * f2 * t) * exp(-t * 14.0)
                        val tone3 = 0.30 * sin(twoPi * f3 * t) * exp(-t * 22.0) * spinModulation
                        val tone4 = 0.18 * sin(twoPi * f4 * t) * exp(-t * 22.0) * spinModulation

                        // Нормализация суммы гармоник в диапазон [-1.0, 1.0]
                        val combined = ((tone1 + tone2 + tone3 + tone4) * 0.48 + flickImpulse) * attackEnv
                        val clamped = combined.coerceIn(-1.0, 1.0)
                        pcmBuffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                    }

                    val byteSize = pcmBuffer.size * 2
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()

                    val audioFormat = AudioFormat.Builder()
                        .setSampleRate(COIN_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()

                    audioTrack = AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(byteSize)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()

                    val written = audioTrack.write(pcmBuffer, 0, pcmBuffer.size)
                    if (written > 0 && audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                        audioTrack.play()
                        delay(COIN_DURATION_MS.toLong() + 45L)
                    }
                } catch (e: Exception) {
                    Log.d("DavidSoundManager", "Error synthesizing coin toss sound: ${e.message}")
                } finally {
                    try {
                        audioTrack?.let { track ->
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                track.stop()
                            }
                            track.release()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}

/**
 * Алиас для обращения к менеджеру звуков Давида
 */
typealias DavidSoundManager = SoundManager
