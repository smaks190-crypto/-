package com.example.davidapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.util.Log

/**
 * Менеджер звуковых эффектов для отправки, получения и системных сигналов.
 * Поддерживает кастомные SoundPool ресурсы и встроенный аппаратный ToneGenerator.
 */
class SoundManager(private val context: Context) {

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

            // Пробуем загрузить кастомные аудио-файлы из res/raw если они добавлены
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
}
