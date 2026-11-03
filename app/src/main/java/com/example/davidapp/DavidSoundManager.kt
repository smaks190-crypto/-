package com.example.davidapp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Единый процедурный звуковой движок David Finance (Obsidian Neo-Bank).
 *
 * Все звуки синтезируются процедурно в формате 16-bit PCM (44.1 кГц, моно)
 * с пиковой нормализацией (86–95% полной шкалы Short.MAX_VALUE) и плавными косинусными
 * огибающими без стартовых/финальных щелчков, кэшируясь в памяти
 * для мгновенного воспроизведения через [AudioTrack.MODE_STATIC] с нулевой задержкой:
 * - [playTransactionCoins] — реалистичный каскадный звон монет при сохранении операций;
 * - [playVoiceOperationCaptured] — мягкий бархатный маримба-отклик при распознавании операции голосом на лету;
 * - [playReceive] / [playFrogReceive] — харизматичный двухсложный «ква-риббит» жабы Давида при входящем сообщении;
 * - [playSend] — мягкий водный «бульк» (bubble pop) при отправке сообщения пользователем;
 * - [playVoiceChatOpen] — плавный восходящий эмбиент-аккорд при раскрытии голосового чата (David Live / Голосовой ввод);
 * - [playVoiceChatClose] — мягкий нисходящий эмбиент-аккорд при сворачивании или закрытии голосового чата;
 * - [playCoinToss] — одиночный звон подброшенной золотой монетки (сплеш-экран и 3D-лоадер в чате);
 * - [playAchievement] — торжественный изумрудный арпеджио-аккорд при открытии достижения;
 * - [playClick] — деликатный тактильный микро-щелчок.
 *
 * Каждый звук строго учитывает настройку приложения `BudgetPreferencesRepository.isSoundEnabled`,
 * системный режим звонка (`AudioManager.RINGER_MODE_NORMAL`) и текущую громкость пользователя.
 */
class SoundManager(context: Context) {
    private val context: Context = context.applicationContext

    init {
        warmUpAllSounds()
    }

    /**
     * Воспроизведение мягкого водного звука отправки сообщения в чат («бульк»)
     */
    fun playSend() {
        Companion.playSend(context)
    }

    /**
     * Воспроизведение фирменного звука жабы Давида («ква-риббит») при получении сообщения в чате
     */
    fun playReceive() {
        Companion.playFrogReceive(context)
    }

    /**
     * Явный вызов фирменного звука жабы при СМС / ответе Давида
     */
    fun playFrogReceive() {
        Companion.playFrogReceive(context)
    }

    /**
     * Воспроизведение каскадного звона монет при сохранении финансовой операции
     */
    fun playTransactionCoins() {
        Companion.playTransactionCoins(context)
    }

    /**
     * Воспроизведение мягкого тёплого звука при распознавании операции в голосовом вводе
     */
    fun playVoiceOperationCaptured() {
        Companion.playVoiceOperationCaptured(context)
    }

    /**
     * Плавный восходящий звук раскрытия голосового чата (David Live / Голосовой ввод)
     */
    fun playVoiceChatOpen() {
        Companion.playVoiceChatOpen(context)
    }

    /**
     * Плавный нисходящий звук сворачивания или закрытия голосового чата
     */
    fun playVoiceChatClose() {
        Companion.playVoiceChatClose(context)
    }

    /**
     * Деликатный тактильный звук нажатия на интерактивные элементы
     */
    fun playClick() {
        Companion.playClick(context)
    }

    /**
     * Победный изумрудный аккорд при разблокировке достижения (Ачивки)
     */
    fun playAchievement() {
        Companion.playAchievement(context)
    }

    /**
     * Вызов процедурного звона подброшенной золотой монетки («Дзынь!») на экземпляре класса
     */
    fun playCoinToss(context: Context? = this.context) {
        Companion.playCoinToss(context)
    }

    /**
     * Освобождение ресурсов (сохранено для обратной совместимости жизненного цикла экранов)
     */
    fun release() {
        // Все PCM-буферы статичны и воспроизводятся через короткоживущие AudioTrack в фоновом пуле
    }

    companion object {
        internal const val SAMPLE_RATE = 44100
        private const val COIN_DURATION_MS = 320
        private const val TRANSACTION_COINS_DURATION_MS = 430
        private const val VOICE_CAPTURE_DURATION_MS = 160
        private const val FROG_RECEIVE_DURATION_MS = 310
        private const val SEND_BUBBLE_DURATION_MS = 95
        private const val VOICE_OPEN_DURATION_MS = 290
        private const val VOICE_CLOSE_DURATION_MS = 260
        private const val ACHIEVEMENT_DURATION_MS = 460
        private const val CLICK_DURATION_MS = 28

        private val synthScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * 1. Кэшированный PCM-буфер одиночного звона подброшенной золотой монетки (320 мс)
         */
        internal val cachedCoinPcm: ShortArray by lazy {
            synthesizeSingleCoinPcm()
        }

        /**
         * 2. Кэшированный PCM-буфер каскадного звона горсти монет при добавлении операции (430 мс)
         */
        internal val cachedTransactionCoinsPcm: ShortArray by lazy {
            synthesizeTransactionCoinsPcm()
        }

        /**
         * 3. Кэшированный PCM-буфер мягкого тёплого отклика распознанной голосовой операции (160 мс)
         */
        internal val cachedVoiceCapturePcm: ShortArray by lazy {
            synthesizeVoiceOperationCapturedPcm()
        }

        /**
         * 4. Кэшированный PCM-буфер фирменного звука жабы Давида («Ква-риббит») при СМС в чате (310 мс)
         */
        internal val cachedFrogReceivePcm: ShortArray by lazy {
            synthesizeFrogReceivePcm()
        }

        /**
         * 5. Кэшированный PCM-буфер мягкого пузырька отправки сообщения пользователем (95 мс)
         */
        internal val cachedSendBubblePcm: ShortArray by lazy {
            synthesizeSendBubblePcm()
        }

        /**
         * 6. Кэшированный PCM-буфер плавного восходящего раскрытия голосового чата (290 мс)
         */
        internal val cachedVoiceOpenPcm: ShortArray by lazy {
            synthesizeVoiceChatOpenPcm()
        }

        /**
         * 7. Кэшированный PCM-буфер плавного нисходящего закрытия голосового чата (260 мс)
         */
        internal val cachedVoiceClosePcm: ShortArray by lazy {
            synthesizeVoiceChatClosePcm()
        }

        /**
         * 8. Кэшированный PCM-буфер победного аккорда достижения (460 мс)
         */
        internal val cachedAchievementPcm: ShortArray by lazy {
            synthesizeAchievementPcm()
        }

        /**
         * 9. Кэшированный PCM-буфер мягкого тактильного микро-клика (28 мс)
         */
        internal val cachedClickPcm: ShortArray by lazy {
            synthesizeClickPcm()
        }

        /**
         * Нормализует сырой буфер сэмплов до целевого пикового уровня [targetPeakRatio] от [Short.MAX_VALUE]
         * с гарантированным плавным косинусным входом и выходом без щелчков (первый и последний сэмпл = 0).
         */
        internal fun normalizePcmPeak(
            rawSamples: DoubleArray,
            targetPeakRatio: Double = 0.92,
            fadeInSamples: Int = 48,
            fadeOutSamples: Int = 160
        ): ShortArray {
            if (rawSamples.isEmpty()) return ShortArray(0)
            val total = rawSamples.size
            val safeFadeIn = fadeInSamples.coerceIn(1, (total / 4).coerceAtLeast(1))
            val safeFadeOut = fadeOutSamples.coerceIn(1, (total / 3).coerceAtLeast(1))

            var maxAbs = 0.0
            for (i in 0 until total) {
                val a = abs(rawSamples[i])
                if (a > maxAbs) maxAbs = a
            }
            val scale = if (maxAbs > 1e-6) (targetPeakRatio.coerceIn(0.1, 0.98) / maxAbs) else 1.0
            val out = ShortArray(total)

            for (i in 0 until total) {
                val envIn = if (i < safeFadeIn) {
                    0.5 * (1.0 - cos(PI * i.toDouble() / safeFadeIn))
                } else {
                    1.0
                }
                val rem = total - 1 - i
                val envOut = if (rem < safeFadeOut) {
                    0.5 * (1.0 - cos(PI * rem.toDouble() / safeFadeOut))
                } else {
                    1.0
                }
                val normalized = (rawSamples[i] * scale * envIn * envOut).coerceIn(-1.0, 1.0)
                out[i] = (normalized * Short.MAX_VALUE).toInt().toShort()
            }
            out[0] = 0
            out[total - 1] = 0
            return out
        }

        internal fun synthesizeSingleCoinPcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * COIN_DURATION_MS) / 1000
            val pcmBuffer = ShortArray(totalSamples)

            val f1 = 2637.0 // Базовый тон звона (нота E7)
            val f2 = 5274.0 // Октавный обертон (нота E8)
            val f3 = 6880.0 // Мягкий металлический блеск (inharmonic coin shimmer 1)
            val f4 = 9320.0 // Верхний обертон гурта (inharmonic coin shimmer 2)
            val twoPi = 2.0 * PI

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                // Мягкая атака за первые 1.5 мс против стартового щелчка
                val attackEnv = (t / 0.0015).coerceIn(0.0, 1.0)
                val tailFade = ((COIN_DURATION_MS / 1000.0 - t) / 0.012).coerceIn(0.0, 1.0)

                // В первые 4 мс — деликатный импульс подброса монетки
                val flickImpulse = if (t < 0.004) {
                    0.18 * sin(twoPi * 3650.0 * t) * exp(-t * 900.0)
                } else {
                    0.0
                }

                // Микро-модуляция громкости (эффект вращения монетки в воздухе 18 Гц)
                val spinModulation = 1.0 + 0.15 * sin(twoPi * 18.0 * t)
                val tone1 = 1.00 * sin(twoPi * f1 * t) * exp(-t * 10.5)
                val tone2 = 0.45 * sin(twoPi * f2 * t) * exp(-t * 14.0)
                val tone3 = 0.14 * sin(twoPi * f3 * t) * exp(-t * 22.0) * spinModulation
                val tone4 = 0.08 * sin(twoPi * f4 * t) * exp(-t * 22.0) * spinModulation

                // Оригинальный смягчённый базовый множитель амплитуды сплеш-экрана (0.24)
                val combined = ((tone1 + tone2 + tone3 + tone4) * 0.24 + flickImpulse) * attackEnv * tailFade
                val clamped = combined.coerceIn(-1.0, 1.0)
                pcmBuffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
            }
            pcmBuffer[0] = 0
            pcmBuffer[totalSamples - 1] = 0
            return pcmBuffer
        }

        internal fun synthesizeTransactionCoinsPcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * TRANSACTION_COINS_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalDurationSec = TRANSACTION_COINS_DURATION_MS / 1000.0

            // Три каскадных удара монет (золотая -> серебряная -> изумрудная крона), образующие мажорное трезвучие
            val coinOnsets = doubleArrayOf(0.000, 0.068, 0.142)
            val baseFreqs = doubleArrayOf(2489.0, 2960.0, 3520.0) // D#7, F#7, A7 звон
            val coinGains = doubleArrayOf(0.88, 0.94, 1.00)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val masterTail = ((totalDurationSec - t) / 0.018).coerceIn(0.0, 1.0)
                var sampleSum = 0.0

                for (c in coinOnsets.indices) {
                    val dt = t - coinOnsets[c]
                    if (dt >= 0.0) {
                        val f1 = baseFreqs[c]
                        val f2 = f1 * 2.0
                        val f3 = f1 * 2.62 // Негармонический обертон звона гурта монеты
                        val f4 = f1 * 3.48

                        val attack = (dt / 0.0016).coerceIn(0.0, 1.0)
                        val clinkTransient = if (dt < 0.0045) {
                            0.24 * sin(twoPi * (f1 * 1.45) * dt) * exp(-dt * 850.0)
                        } else {
                            0.0
                        }
                        val spinShimmer = 1.0 + 0.16 * sin(twoPi * (19.0 + c * 4.0) * dt)

                        val partial1 = 1.00 * sin(twoPi * f1 * dt) * exp(-dt * 11.5)
                        val partial2 = 0.48 * sin(twoPi * f2 * dt) * exp(-dt * 15.5)
                        val partial3 = 0.20 * sin(twoPi * f3 * dt) * exp(-dt * 24.0) * spinShimmer
                        val partial4 = 0.10 * sin(twoPi * f4 * dt) * exp(-dt * 30.0) * spinShimmer

                        sampleSum += ((partial1 + partial2 + partial3 + partial4) * 0.22 + clinkTransient) *
                            attack * coinGains[c]
                    }
                }

                raw[i] = sampleSum * masterTail
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.94, fadeInSamples = 32, fadeOutSamples = 200)
        }

        /**
         * Мягкий, тёплый бархатный отклик при распознавании операции в голосовом вводе (160 мс):
         * двухнотный деревянно-стеклянный маримба-интервал (G4 392 Гц -> D5 587.33 Гц)
         * с округлым косинусным вступлением и полным отсутствием резких высоких металлических гармоник,
         * чтобы звук приятно подтверждал захват операции и не перебивал речь пользователя.
         */
        internal fun synthesizeVoiceOperationCapturedPcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * VOICE_CAPTURE_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalSec = VOICE_CAPTURE_DURATION_MS / 1000.0

            val note1Freq = 392.00 // G4 — тёплая бархатная основа
            val note2Freq = 587.33 // D5 — мягкая чистая квинта подтверждения
            val note2Onset = 0.038 // Вторая нота мягко вступает через 38 мс

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val masterTail = ((totalSec - t) / 0.024).coerceIn(0.0, 1.0)

                // Первая нота с плавной косинусной атакой 12 мс (без щелчка/удара)
                val attack1 = if (t < 0.012) 0.5 * (1.0 - cos(PI * (t / 0.012))) else 1.0
                val decay1 = exp(-t * 18.0)
                val subWarmth1 = 0.28 * sin(twoPi * (note1Freq * 0.5) * t) * exp(-t * 22.0)
                val fund1 = 0.85 * sin(twoPi * note1Freq * t)
                val warmHarm1 = 0.18 * sin(twoPi * (note1Freq * 2.0) * t) * exp(-t * 34.0)
                var signal = (subWarmth1 + fund1 + warmHarm1) * attack1 * decay1

                // Вторая нота — мягкое просветление на квинту вверх
                if (t >= note2Onset) {
                    val dt = t - note2Onset
                    val attack2 = if (dt < 0.014) 0.5 * (1.0 - cos(PI * (dt / 0.014))) else 1.0
                    val decay2 = exp(-dt * 15.5)
                    val fund2 = 0.92 * sin(twoPi * note2Freq * dt)
                    val warmHarm2 = 0.14 * sin(twoPi * (note2Freq * 2.0) * dt) * exp(-dt * 32.0)
                    signal += (fund2 + warmHarm2) * attack2 * decay2
                }

                raw[i] = signal * masterTail
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.88, fadeInSamples = 80, fadeOutSamples = 240)
        }

        internal fun synthesizeFrogReceivePcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * FROG_RECEIVE_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalSec = FROG_RECEIVE_DURATION_MS / 1000.0

            // Двухсложный музыкальный «ква-риббит» Давида Жабова:
            // Слог 1 (0..102 мс): короткий упругий «кв-» с подъёмом питча 175 -> 295 Гц и горловой пульсацией 48 Гц
            // Слог 2 (114..310 мс): выразительный «ква-а!» 205 -> 385 -> 325 Гц с изумрудным резонансом
            var phase1 = 0.0
            var phase2 = 0.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val masterTail = ((totalSec - t) / 0.016).coerceIn(0.0, 1.0)
                var signal = 0.0

                if (t < 0.102) {
                    val progress = (t / 0.102).coerceIn(0.0, 1.0)
                    val env = sin(PI * progress) * exp(-progress * 0.9)
                    val instFreq = 175.0 + 120.0 * sin(0.5 * PI * progress)
                    phase1 += twoPi * instFreq / SAMPLE_RATE

                    // Горловая пульсация резонатора лягушки (vocal sac trill ~48 Hz)
                    val throatTrill = 0.72 + 0.28 * sin(twoPi * 48.0 * t)
                    val fundamental = 0.55 * sin(phase1)
                    val formant2 = 0.85 * sin(phase1 * 2.0)
                    val formant3 = 0.45 * sin(phase1 * 3.0)
                    val woodyResonance = 0.22 * sin(twoPi * 720.0 * t) * exp(-t * 28.0)

                    signal += (fundamental + formant2 + formant3 + woodyResonance) * throatTrill * env * 0.34
                }

                if (t >= 0.114) {
                    val dt = t - 0.114
                    val dur2 = totalSec - 0.114
                    val progress = (dt / dur2).coerceIn(0.0, 1.0)
                    val attack = (dt / 0.008).coerceIn(0.0, 1.0)
                    val decay = exp(-dt * 11.5)
                    // Дуга частоты: 205 Гц -> пик 385 Гц -> мягкий спад к 320 Гц
                    val pitchArc = sin(PI * progress.coerceAtMost(0.78) / 0.78)
                    val instFreq = 205.0 + 180.0 * pitchArc
                    phase2 += twoPi * instFreq / SAMPLE_RATE

                    val throatTrill = 0.76 + 0.24 * sin(twoPi * 52.0 * dt)
                    val fundamental = 0.50 * sin(phase2)
                    val formant2 = 0.90 * sin(phase2 * 2.0)
                    val formant3 = 0.48 * sin(phase2 * 3.0)
                    // Изумрудный колокольчик в хвосте второго слога для премиального звучания
                    val emeraldTail = 0.18 * sin(twoPi * 1140.0 * dt) * exp(-dt * 16.0)

                    signal += ((fundamental + formant2 + formant3) * throatTrill * decay + emeraldTail) * attack * 0.36
                }

                raw[i] = signal * masterTail
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.95, fadeInSamples = 40, fadeOutSamples = 180)
        }

        internal fun synthesizeSendBubblePcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * SEND_BUBBLE_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalSec = SEND_BUBBLE_DURATION_MS / 1000.0
            var phase = 0.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = (t / totalSec).coerceIn(0.0, 1.0)
                val attack = (t / 0.003).coerceIn(0.0, 1.0)
                val tail = ((totalSec - t) / 0.010).coerceIn(0.0, 1.0)
                val env = attack * exp(-t * 24.0) * tail

                // Быстрый восходящий «бульк» капли воды (360 Гц -> 890 Гц)
                val instFreq = 360.0 + 530.0 * (progress * progress)
                phase += twoPi * instFreq / SAMPLE_RATE

                val tone1 = 0.85 * sin(phase)
                val tone2 = 0.28 * sin(phase * 2.0) * exp(-t * 35.0)
                raw[i] = (tone1 + tone2) * env
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.90, fadeInSamples = 36, fadeOutSamples = 120)
        }

        internal fun synthesizeVoiceChatOpenPcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * VOICE_OPEN_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalSec = VOICE_OPEN_DURATION_MS / 1000.0
            var sweepPhase = 0.0

            // Мягкий восходящий нео-банковский портал: глиссандо 280 -> 580 Гц + тёплый аккорд Dmaj9 (D4 - A4 - E5 - F#5)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = (t / totalSec).coerceIn(0.0, 1.0)

                // Плавное S-образное нарастание (42 мс) и бархатное затухание
                val fadeIn = if (t < 0.042) 0.5 * (1.0 - cos(PI * (t / 0.042))) else 1.0
                val fadeOut = if (t > totalSec - 0.055) {
                    0.5 * (1.0 + cos(PI * ((t - (totalSec - 0.055)) / 0.055)))
                } else {
                    1.0
                }
                val env = fadeIn * fadeOut * exp(-t * 4.8)

                val sweepFreq = 280.0 + 300.0 * sin(0.5 * PI * progress)
                sweepPhase += twoPi * sweepFreq / SAMPLE_RATE

                val padGlide = 0.45 * sin(sweepPhase)
                val note1 = 0.35 * sin(twoPi * 293.66 * t) * exp(-t * 5.5) // D4
                val note2 = 0.32 * sin(twoPi * 440.00 * t) * exp(-t * 5.0) // A4
                // Верхние ноты вступают с мягкой задержкой для эффекта «раскрытия сферы»
                val shimmerDelay = ((t - 0.035) / 0.045).coerceIn(0.0, 1.0)
                val note3 = 0.26 * sin(twoPi * 659.25 * t) * shimmerDelay * exp(-t * 6.2) // E5
                val note4 = 0.20 * sin(twoPi * 739.99 * t) * shimmerDelay * exp(-t * 7.0) // F#5

                raw[i] = (padGlide + note1 + note2 + note3 + note4) * env
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.92, fadeInSamples = 96, fadeOutSamples = 240)
        }

        internal fun synthesizeVoiceChatClosePcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * VOICE_CLOSE_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalSec = VOICE_CLOSE_DURATION_MS / 1000.0
            var sweepPhase = 0.0

            // Плавный нисходящий эмбиент-аккорд при сворачивании/закрытии голосового чата (560 -> 240 Гц)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = (t / totalSec).coerceIn(0.0, 1.0)

                val fadeIn = if (t < 0.028) 0.5 * (1.0 - cos(PI * (t / 0.028))) else 1.0
                val fadeOut = if (t > totalSec - 0.060) {
                    0.5 * (1.0 + cos(PI * ((t - (totalSec - 0.060)) / 0.060)))
                } else {
                    1.0
                }
                val env = fadeIn * fadeOut * exp(-t * 6.0)

                val sweepFreq = 560.0 - 320.0 * sin(0.5 * PI * progress)
                sweepPhase += twoPi * sweepFreq / SAMPLE_RATE

                val padGlide = 0.48 * sin(sweepPhase)
                val highNote = 0.28 * sin(twoPi * 587.33 * t) * exp(-t * 11.0) // D5 быстро уходит
                val midNote = 0.34 * sin(twoPi * 440.00 * t) * exp(-t * 7.5) // A4
                val lowWarmth = 0.40 * sin(twoPi * 293.66 * t) * exp(-t * 5.2) // D4 мягкая опора

                raw[i] = (padGlide + highNote + midNote + lowWarmth) * env
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.90, fadeInSamples = 80, fadeOutSamples = 240)
        }

        internal fun synthesizeAchievementPcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * ACHIEVEMENT_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalSec = ACHIEVEMENT_DURATION_MS / 1000.0

            // Изумрудный 4-нотный фанфарный арпеджио: D5 -> F#5 -> A5 -> D6
            val onsets = doubleArrayOf(0.000, 0.065, 0.130, 0.195)
            val freqs = doubleArrayOf(587.33, 739.99, 880.00, 1174.66)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val masterTail = ((totalSec - t) / 0.020).coerceIn(0.0, 1.0)
                var sum = 0.0

                for (n in onsets.indices) {
                    val dt = t - onsets[n]
                    if (dt >= 0.0) {
                        val f = freqs[n]
                        val attack = (dt / 0.004).coerceIn(0.0, 1.0)
                        val decayRate = if (n == onsets.lastIndex) 6.5 else 11.0
                        val env = attack * exp(-dt * decayRate)
                        val fundamental = 0.75 * sin(twoPi * f * dt)
                        val overtone = 0.25 * sin(twoPi * (f * 2.0) * dt) * exp(-dt * 16.0)
                        sum += (fundamental + overtone) * env
                    }
                }

                raw[i] = sum * masterTail
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.93, fadeInSamples = 40, fadeOutSamples = 200)
        }

        internal fun synthesizeClickPcm(): ShortArray {
            val totalSamples = (SAMPLE_RATE * CLICK_DURATION_MS) / 1000
            val raw = DoubleArray(totalSamples)
            val twoPi = 2.0 * PI
            val totalSec = CLICK_DURATION_MS / 1000.0
            var phase = 0.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val attack = (t / 0.0012).coerceIn(0.0, 1.0)
                val tail = ((totalSec - t) / 0.004).coerceIn(0.0, 1.0)
                val instFreq = 580.0 * exp(-t * 55.0) + 180.0
                phase += twoPi * instFreq / SAMPLE_RATE

                raw[i] = sin(phase) * exp(-t * 115.0) * attack * tail
            }
            return normalizePcmPeak(raw, targetPeakRatio = 0.82, fadeInSamples = 20, fadeOutSamples = 80)
        }

        /**
         * Предварительный прогрев всех PCM-буферов в фоновом потоке,
         * чтобы воспроизведение любого звукового эффекта происходило с нулевой задержкой.
         */
        fun warmUpAllSounds() {
            synthScope.launch {
                cachedCoinPcm.size
                cachedTransactionCoinsPcm.size
                cachedVoiceCapturePcm.size
                cachedFrogReceivePcm.size
                cachedSendBubblePcm.size
                cachedVoiceOpenPcm.size
                cachedVoiceClosePcm.size
                cachedAchievementPcm.size
                cachedClickPcm.size
            }
        }

        /**
         * Обратная совместимость со сплеш-экраном (`GrowthChartSplashScreen`).
         */
        fun warmUpCoinToss() {
            warmUpAllSounds()
        }

        /**
         * Проверяет разрешение звука в настройках приложения, режим звонка и системную громкость.
         * Не применяет двойного затухания поверх системного микшера STREAM_MUSIC, чтобы звуки
         * звучали насыщенно и отчётливо на реальных Android-устройствах.
         */
        internal fun resolvePlaybackGain(
            context: Context?,
            baseGain: Float,
            maxGain: Float
        ): Float {
            var userVolumeRatio = 0.85f

            if (context != null) {
                try {
                    if (!com.example.data.preferences.BudgetPreferencesRepository.isSoundEnabled(context)) {
                        return 0f
                    }

                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                            return 0f
                        }

                        val musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val notifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                        val notifMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1)
                        userVolumeRatio = maxOf(
                            musicVol.toFloat() / musicMax,
                            notifVol.toFloat() / notifMax
                        ).coerceIn(0f, 1f)
                        if (userVolumeRatio <= 0.01f) {
                            return 0f
                        }
                    }
                } catch (e: Exception) {
                    Log.d("DavidSoundManager", "Volume check fallback: ${e.message}")
                }
            }

            // Аппаратный микшер Android уже масштабирует громкость потока, поэтому сохраняем высокий
            // базовый коэффициент (78%..100% от baseGain), чтобы избежать двойного ослабления сигнала.
            val perceptualScale = 0.78f + 0.22f * userVolumeRatio
            return (baseGain * perceptualScale).coerceIn(0f, maxGain)
        }

        private fun playPcmBufferAsync(
            context: Context?,
            pcmProvider: () -> ShortArray,
            durationMs: Int,
            baseGain: Float = 0.88f,
            maxGain: Float = 0.98f,
            debugTag: String = "sound"
        ) {
            val finalGain = resolvePlaybackGain(context, baseGain, maxGain)
            if (finalGain <= 0.002f) return

            synthScope.launch {
                var audioTrack: AudioTrack? = null
                try {
                    val pcmBuffer = pcmProvider()
                    val byteSize = pcmBuffer.size * 2

                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()

                    val audioFormat = AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
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
                        delay(durationMs.toLong() + 45L)
                    }
                } catch (e: Exception) {
                    Log.d("DavidSoundManager", "Error playing $debugTag: ${e.message}")
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

        /**
         * Одиночный звон золотой монетки для сплеш-экрана (`GrowthChartSplashScreen`) —
         * сохранён с оригинальной деликатной громкостью `(0.22f * userVolumeRatio).coerceIn(0f, 0.25f)`
         * и профилем `USAGE_GAME`, как на ветке `main`.
         */
        fun playCoinToss(context: Context? = null) {
            var userVolumeRatio = 0.5f

            if (context != null) {
                try {
                    if (!com.example.data.preferences.BudgetPreferencesRepository.isSoundEnabled(context)) {
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
                        .setSampleRate(SAMPLE_RATE)
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

        /**
         * Каскадный звон монет при сохранении финансовой операции (дохода, расхода, взноса в цель)
         */
        fun playTransactionCoins(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedTransactionCoinsPcm },
                durationMs = TRANSACTION_COINS_DURATION_MS,
                baseGain = 0.94f,
                maxGain = 1.00f,
                debugTag = "transaction_coins"
            )
        }

        /**
         * Мягкий бархатный маримба-отклик при распознавании операции в голосовом вводе на лету
         */
        fun playVoiceOperationCaptured(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedVoiceCapturePcm },
                durationMs = VOICE_CAPTURE_DURATION_MS,
                baseGain = 0.78f,
                maxGain = 0.88f,
                debugTag = "voice_operation_captured"
            )
        }

        /**
         * Харизматичный звук жабы Давида («Ква-риббит») при входящем СМС/ответе в чате
         */
        fun playFrogReceive(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedFrogReceivePcm },
                durationMs = FROG_RECEIVE_DURATION_MS,
                baseGain = 0.96f,
                maxGain = 1.00f,
                debugTag = "frog_receive"
            )
        }

        /**
         * Мягкий звук пузырька при отправке сообщения пользователем
         */
        fun playSend(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedSendBubblePcm },
                durationMs = SEND_BUBBLE_DURATION_MS,
                baseGain = 0.86f,
                maxGain = 0.96f,
                debugTag = "send_bubble"
            )
        }

        /**
         * Плавный восходящий звук раскрытия голосового чата (David Live / Голосовой ввод)
         */
        fun playVoiceChatOpen(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedVoiceOpenPcm },
                durationMs = VOICE_OPEN_DURATION_MS,
                baseGain = 0.90f,
                maxGain = 0.98f,
                debugTag = "voice_chat_open"
            )
        }

        /**
         * Плавный нисходящий звук закрытия / сворачивания голосового чата
         */
        fun playVoiceChatClose(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedVoiceClosePcm },
                durationMs = VOICE_CLOSE_DURATION_MS,
                baseGain = 0.88f,
                maxGain = 0.96f,
                debugTag = "voice_chat_close"
            )
        }

        /**
         * Торжественный изумрудный аккорд при разблокировке достижения
         */
        fun playAchievement(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedAchievementPcm },
                durationMs = ACHIEVEMENT_DURATION_MS,
                baseGain = 0.94f,
                maxGain = 1.00f,
                debugTag = "achievement"
            )
        }

        /**
         * Деликатный тактильный микро-отклик при нажатии кнопок
         */
        fun playClick(context: Context? = null) {
            playPcmBufferAsync(
                context = context,
                pcmProvider = { cachedClickPcm },
                durationMs = CLICK_DURATION_MS,
                baseGain = 0.68f,
                maxGain = 0.80f,
                debugTag = "click"
            )
        }
    }
}

/**
 * Алиас для обращения к менеджеру звуков Давида
 */
typealias DavidSoundManager = SoundManager
