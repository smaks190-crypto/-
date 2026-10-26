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
 * и процедурного синтеза мягкого звона золотой монетки Давида Жабова.
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

        // Кэшированный PCM-буфер мягкого, бархатного звона золотой монетки
        private val cachedCoinPcm: ShortArray by lazy {
            val totalSamples = (COIN_SAMPLE_RATE * COIN_DURATION_MS) / 1000
            val pcmBuffer = ShortArray(totalSamples)

            val f1 = 2637.0 // Базовый тон звона (нота E7)
            val f2 = 5274.0 // Октавный обертон (нота E8)
            val f3 = 6880.0 // Мягкий металлический блеск (inharmonic coin shimmer 1)
            val f4 = 9320.0 // Верхний обертон гурта (inharmonic coin shimmer 2)
            val twoPi = 2.0 * PI

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / COIN_SAMPLE_RATE

                // Мягкая атака за первые 1.5 мс против стартового щелчка
                val attackEnv = (t / 0.0015).coerceIn(0.0, 1.0)

                // В первые 4 мс — деликатный импульс подброса монетки
                val flickImpulse = if (t < 0.004) {
                    0.18 * sin(twoPi * 3650.0 * t) * exp(-t * 900.0)
                } else {
                    0.0
                }

                // Микро-модуляция громкости (эффект вращения монетки в воздухе 18 Гц)
                val spinModulation = 1.0 + 0.15 * sin(twoPi * 18.0 * t)

                // 4 гармоники со смягченными верхними частотами (0.14 и 0.08)
                val tone1 = 1.00 * sin(twoPi * f1 * t) * exp(-t * 10.5)
                val tone2 = 0.45 * sin(twoPi * f2 * t) * exp(-t * 14.0)
                val tone3 = 0.14 * sin(twoPi * f3 * t) * exp(-t * 22.0) * spinModulation
                val tone4 = 0.08 * sin(twoPi * f4 * t) * exp(-t * 22.0) * spinModulation

                // Смягченный базовый множитель амплитуды (0.24 вместо 0.48)
                val combined = ((tone1 + tone2 + tone3 + tone4) * 0.24 + flickImpulse) * attackEnv
                val clamped = combined.coerceIn(-1.0, 1.0)
                pcmBuffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
            }
            pcmBuffer
        }

        /**
         * Воспроизводит мягкий металлический звук подбрасывания золотой монетки («Дзынь!»)
         * с учетом настроек приложения, режима звонка (Без звука / Вибрация) и системной громкости.
         */
        fun playCoinToss(context: Context? = null) {
            var userVolumeRatio = 0.5f

            if (context != null) {
                try {
                    val appPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val budgetPrefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)

                    if (!appPrefs.getBoolean("sound_enabled", true) ||
                        !appPrefs.getBoolean("is_sound_enabled", true) ||
                        !budgetPrefs.getBoolean("sound_enabled", true) ||
                        !budgetPrefs.getBoolean("is_sound_enabled", true)
                    ) {
                        return
                    }

                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                        return
                    }

                    val musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val notifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                    val notifMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1)
                    userVolumeRatio = minOf(musicVol.toFloat() / musicMax, notifVol.toFloat() / notifMax)
                    if (userVolumeRatio <= 0.01f) {
                        return
                    }
                } catch (e: Exception) {
                    Log.d("DavidSoundManager", "Volume check fallback: ${e.message}")
                }
            }

            val finalGain = (0.22f * userVolumeRatio).coerceIn(0f, 0.25f)
            if (finalGain <= 0.002f) return

            synthScope.launch {
                var audioTrack: AudioTrack? = null
                try {
                    val pcmBuffer = cachedCoinPcm
                    val byteSize = pcmBuffer.size * 2

                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
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
                        audioTrack.setVolume(finalGain)
                        audioTrack.play()
                        delay(COIN_DURATION_MS.toLong() + 45L)
                    }
                } catch (e: Exception) {
                    Log.d("DavidSoundManager", "Error playing coin toss sound: ${e.message}")
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
