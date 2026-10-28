package com.example.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Высокопроизводительный DSP-анализатор спектра речи в реальном времени на базе Radix-2 Cooley-Tukey FFT.
 *
 * Особенности реализации:
 * - Работает с потоком PCM 16 kHz / 16-bit / Mono (скользящее окно 1024 сэмпла с обновлением по 512 сэмплов).
 * - Применяет оконную функцию Hann для подавления спектральной утечки (spectral leakage).
 * - Не создаёт тяжёлых объектов в горячем цикле обработки аудио (все рабочие массивы и таблицы
 *   поворотных множителей предвычислены при инициализации).
 * - Преобразует спектр в 32 перцептивные частотные полосы (85 Гц – 7600 Гц), адаптированные под человеческий голос.
 * - Разделяет честные частотные данные (rawFrequencyBands) и пространственную огибающую (spatialEnvelope),
 *   которая визуально акцентирует центральную область капсулы без потери различий между низким и высоким голосом.
 * - Использует асимметричное сглаживание атаки (быстрый отклик) и затухания (плавный возврат к нулю).
 */
class VoiceSpectrumAnalyzer(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val fftSize: Int = DEFAULT_FFT_SIZE,
    val bandCount: Int = BAND_COUNT,
    private val attackAlpha: Float = DEFAULT_ATTACK_ALPHA,
    private val releaseAlpha: Float = DEFAULT_RELEASE_ALPHA
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
        const val DEFAULT_FFT_SIZE = 1024
        const val BAND_COUNT = 32

        private const val MIN_SPEECH_FREQ_HZ = 85.0
        private const val MAX_SPEECH_FREQ_HZ = 7600.0

        private const val NOISE_FLOOR_DB = -60.0f
        private const val DYNAMIC_RANGE_DB = 46.0f
        private const val SILENCE_RMS_GATE = 0.0042f

        const val DEFAULT_ATTACK_ALPHA = 0.62f
        const val DEFAULT_RELEASE_ALPHA = 0.20f
    }

    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "fftSize must be a power of 2, got $fftSize"
        }
        require(bandCount > 0) {
            "bandCount must be positive, got $bandCount"
        }
    }

    private val halfFftSize = fftSize / 2
    private val lock = Any()

    // Скользящее окно входного сигнала [-1f..1f]
    private val slidingWindow = FloatArray(fftSize)

    // Предвычисленное окно Ханна: w[n] = 0.5 * (1 - cos(2 * PI * n / (N - 1)))
    private val hannWindow = FloatArray(fftSize) { n ->
        (0.5 * (1.0 - cos((2.0 * PI * n) / (fftSize - 1)))).toFloat()
    }

    // Предвычисленная таблица бит-реверсивной перестановки
    private val bitReversedIndices = IntArray(fftSize) { i ->
        var rev = 0
        var value = i
        var bits = fftSize shr 1
        while (bits > 0) {
            rev = (rev shl 1) or (value and 1)
            value = value shr 1
            bits = bits shr 1
        }
        rev
    }

    // Предвычисленные тригонометрические таблицы для бабочек Cooley-Tukey
    private val twiddleCos = FloatArray(halfFftSize) { k ->
        cos(-2.0 * PI * k / fftSize).toFloat()
    }
    private val twiddleSin = FloatArray(halfFftSize) { k ->
        sin(-2.0 * PI * k / fftSize).toFloat()
    }

    // Рабочие буферы FFT (переиспользуются в каждом кадре без аллокаций)
    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val binMagnitudes = FloatArray(halfFftSize)

    // Границы бинов и центральные частоты для 32 полос
    private val bandStartBin = IntArray(bandCount)
    private val bandEndBin = IntArray(bandCount)
    private val bandCenterFreqHz = FloatArray(bandCount)
    private val bandPreEmphasis = FloatArray(bandCount)

    // Пространственная огибающая для визуального доминирования центра капсулы
    private val spatialEnvelope = FloatArray(bandCount)

    // Буферы выходных значений полос
    private val rawFrequencyBands = FloatArray(bandCount)
    private val targetVisualBands = FloatArray(bandCount)
    private val smoothedBands = FloatArray(bandCount)

    init {
        initFrequencyBandsAndEnvelope()
    }

    private fun initFrequencyBandsAndEnvelope() {
        val nyquist = sampleRate / 2.0
        val minFreq = MIN_SPEECH_FREQ_HZ.coerceAtMost(nyquist * 0.5)
        val maxFreq = MAX_SPEECH_FREQ_HZ.coerceAtMost(nyquist * 0.96)
        val binResolutionHz = sampleRate.toDouble() / fftSize.toDouble()

        // Используем Mel-шкалу для естественного распределения полос по человеческому голосу:
        // m = 2595 * log10(1 + f / 700)
        fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        val minMel = hzToMel(minFreq)
        val maxMel = hzToMel(maxFreq)
        val melStep = (maxMel - minMel) / bandCount

        var previousEndBin = 1
        for (b in 0 until bandCount) {
            val lowMel = minMel + b * melStep
            val highMel = minMel + (b + 1) * melStep
            val lowHz = melToHz(lowMel)
            val highHz = melToHz(highMel)
            val centerHz = melToHz((lowMel + highMel) * 0.5)

            var startBin = (lowHz / binResolutionHz).toInt().coerceIn(1, halfFftSize - 2)
            if (b > 0 && startBin < previousEndBin) {
                startBin = previousEndBin
            }
            var endBin = (highHz / binResolutionHz).toInt().coerceIn(startBin, halfFftSize - 1)
            if (endBin < startBin) {
                endBin = startBin
            }
            previousEndBin = (endBin + 1).coerceAtMost(halfFftSize - 1)

            bandStartBin[b] = startBin
            bandEndBin[b] = endBin
            bandCenterFreqHz[b] = centerHz.toFloat()

            // Мягкая перцептивная компенсация спада высоких частот речи (+3.5 дБ/октаву выше 500 Гц)
            val freqRatio = (centerHz / 450.0).coerceIn(0.4, 8.0)
            bandPreEmphasis[b] = (0.88 + 0.24 * ln(1.0 + freqRatio)).toFloat()

            // Отдельная пространственная огибающая:
            // Центр (b ≈ 15.5) имеет вес 1.0, к краям плавно снижается до ~0.38,
            // чтобы центральные столбики визуально доминировали, но реальные низкие и высокие частоты
            // оставались чётко различимыми.
            val normalizedPos = (b - (bandCount - 1) * 0.5f) / ((bandCount - 1) * 0.5f) // -1f..+1f
            val gaussianCore = exp((-2.15f * normalizedPos * normalizedPos).toDouble()).toFloat()
            spatialEnvelope[b] = (0.36f + 0.64f * gaussianCore).coerceIn(0.36f, 1.0f)
        }
    }

    /**
     * Обрабатывает кадр 16-bit Little-Endian PCM Mono и возвращает нормализованный массив 32 полос (0f..1f).
     */
    fun processPcm16LeFrame(pcmBytes: ByteArray, length: Int = pcmBytes.size): FloatArray {
        synchronized(lock) {
            val safeByteLength = min(length, pcmBytes.size) and -2
            val sampleCount = safeByteLength / 2
            if (sampleCount <= 0) {
                return decayInternal()
            }

            // Сдвигаем скользящее окно и дописываем новые сэмплы
            if (sampleCount >= fftSize) {
                val startByteOffset = (sampleCount - fftSize) * 2
                var byteIdx = startByteOffset
                for (i in 0 until fftSize) {
                    val low = pcmBytes[byteIdx].toInt() and 0xFF
                    val high = pcmBytes[byteIdx + 1].toInt()
                    val sampleShort = (high shl 8) or low
                    slidingWindow[i] = (sampleShort / 32768.0f).coerceIn(-1f, 1f)
                    byteIdx += 2
                }
            } else {
                val keepCount = fftSize - sampleCount
                System.arraycopy(slidingWindow, sampleCount, slidingWindow, 0, keepCount)
                var byteIdx = 0
                for (i in 0 until sampleCount) {
                    val low = pcmBytes[byteIdx].toInt() and 0xFF
                    val high = pcmBytes[byteIdx + 1].toInt()
                    val sampleShort = (high shl 8) or low
                    slidingWindow[keepCount + i] = (sampleShort / 32768.0f).coerceIn(-1f, 1f)
                    byteIdx += 2
                }
            }

            return computeSpectrumFromSlidingWindow()
        }
    }

    /**
     * Обрабатывает массив нормализованных float-сэмплов (-1f..1f) и возвращает массив 32 полос (0f..1f).
     */
    fun processNormalizedSamples(samples: FloatArray, length: Int = samples.size): FloatArray {
        synchronized(lock) {
            val count = min(length, samples.size)
            if (count <= 0) {
                return decayInternal()
            }

            if (count >= fftSize) {
                val startIdx = count - fftSize
                for (i in 0 until fftSize) {
                    val v = samples[startIdx + i]
                    slidingWindow[i] = if (v.isNaN() || v.isInfinite()) 0f else v.coerceIn(-1f, 1f)
                }
            } else {
                val keepCount = fftSize - count
                System.arraycopy(slidingWindow, count, slidingWindow, 0, keepCount)
                for (i in 0 until count) {
                    val v = samples[i]
                    slidingWindow[keepCount + i] = if (v.isNaN() || v.isInfinite()) 0f else v.coerceIn(-1f, 1f)
                }
            }

            return computeSpectrumFromSlidingWindow()
        }
    }

    private fun computeSpectrumFromSlidingWindow(): FloatArray {
        // 1. Вычисляем энергию окна (RMS) для отсечения фонового шума в тишине
        var sumSquares = 0.0f
        for (i in 0 until fftSize) {
            val s = slidingWindow[i]
            sumSquares += s * s
        }
        val windowRms = sqrt((sumSquares / fftSize).toDouble()).toFloat()
        if (windowRms.isNaN() || windowRms.isInfinite() || windowRms < SILENCE_RMS_GATE) {
            rawFrequencyBands.fill(0f)
            targetVisualBands.fill(0f)
            return applyAsymmetricSmoothing()
        }

        // 2. Умножаем сигнал на окно Ханна и раскладываем по бит-реверсивным индексам
        for (i in 0 until fftSize) {
            val windowedSample = slidingWindow[i] * hannWindow[i]
            val revIdx = bitReversedIndices[i]
            fftReal[revIdx] = windowedSample
            fftImag[revIdx] = 0f
        }

        // 3. In-place итеративное БПФ Кули-Тьюки по основанию 2 (Radix-2 FFT)
        var stageSize = 2
        while (stageSize <= fftSize) {
            val halfStage = stageSize shr 1
            val tableStep = fftSize / stageSize
            var groupStart = 0
            while (groupStart < fftSize) {
                var k = 0
                var twiddleIdx = 0
                while (k < halfStage) {
                    val evenIdx = groupStart + k
                    val oddIdx = evenIdx + halfStage

                    val wr = twiddleCos[twiddleIdx]
                    val wi = twiddleSin[twiddleIdx]

                    val orVal = fftReal[oddIdx]
                    val oiVal = fftImag[oddIdx]

                    val tr = wr * orVal - wi * oiVal
                    val ti = wr * oiVal + wi * orVal

                    val erVal = fftReal[evenIdx]
                    val eiVal = fftImag[evenIdx]

                    fftReal[oddIdx] = erVal - tr
                    fftImag[oddIdx] = eiVal - ti
                    fftReal[evenIdx] = erVal + tr
                    fftImag[evenIdx] = eiVal + ti

                    k++
                    twiddleIdx += tableStep
                }
                groupStart += stageSize
            }
            stageSize = stageSize shl 1
        }

        // 4. Вычисляем нормализованные амплитуды бинов спектра (с учётом когерентного усиления окна Ханна 0.5)
        val normScale = 2.0f / (fftSize * 0.5f)
        for (bin in 1 until halfFftSize) {
            val re = fftReal[bin]
            val im = fftImag[bin]
            val mag = sqrt((re * re + im * im).toDouble()).toFloat() * normScale
            binMagnitudes[bin] = if (mag.isNaN() || mag.isInfinite()) 0f else mag
        }
        binMagnitudes[0] = 0f // Исключаем постоянную составляющую (DC offset)

        // 5. Агрегируем бины в 32 частотные полосы (честный спектр без пространственного искажения)
        for (b in 0 until bandCount) {
            val start = bandStartBin[b]
            val end = bandEndBin[b]
            var energySum = 0f
            var peakMag = 0f
            var count = 0
            for (bin in start..end) {
                val m = binMagnitudes[bin]
                energySum += m * m
                if (m > peakMag) peakMag = m
                count++
            }
            val rmsMag = if (count > 0) sqrt((energySum / count).toDouble()).toFloat() else 0f
            // Комбинация RMS и пика внутри полосы сохраняет чёткость узкополосных тонов и формант
            val combinedMag = (0.55f * peakMag + 0.45f * rmsMag) * bandPreEmphasis[b]

            val db = 20.0f * log10(max(combinedMag, 1e-7f))
            val normalizedDb = ((db - NOISE_FLOOR_DB) / DYNAMIC_RANGE_DB).coerceIn(0f, 1f)

            // Мягкая нелинейная компрессия (soft-knee), чтобы громкая речь не упирала все столбики в 1.0f
            val compressed = (normalizedDb * 1.32f) / (1.0f + 0.32f * normalizedDb * normalizedDb)
            rawFrequencyBands[b] = sanitizeUnit(compressed)
        }

        // 6. Применяем лёгкую органическую связность соседних полос и отдельную пространственную огибающую
        // Важно: пространственная огибающая применяется поверх реальной энергии полосы,
        // поэтому при низком голосе максимум остаётся слева, а при высоком — справа.
        val overallGain = ((windowRms - SILENCE_RMS_GATE) / 0.16f).coerceIn(0.18f, 1.0f)
        val softGain = 0.55f + 0.45f * sqrt(overallGain.toDouble()).toFloat()

        for (b in 0 until bandCount) {
            val prev = if (b > 0) rawFrequencyBands[b - 1] else rawFrequencyBands[b]
            val curr = rawFrequencyBands[b]
            val next = if (b < bandCount - 1) rawFrequencyBands[b + 1] else rawFrequencyBands[b]

            val organicFreqValue = 0.76f * curr + 0.12f * prev + 0.12f * next
            val enveloped = organicFreqValue * spatialEnvelope[b] * softGain
            targetVisualBands[b] = sanitizeUnit(enveloped)
        }

        return applyAsymmetricSmoothing()
    }

    private fun applyAsymmetricSmoothing(): FloatArray {
        val result = FloatArray(bandCount)
        for (b in 0 until bandCount) {
            val target = sanitizeUnit(targetVisualBands[b])
            val current = sanitizeUnit(smoothedBands[b])
            val alpha = if (target > current) attackAlpha else releaseAlpha
            var nextVal = current + alpha * (target - current)
            if (nextVal < 0.002f && target == 0f) {
                nextVal = 0f
            }
            nextVal = sanitizeUnit(nextVal)
            smoothedBands[b] = nextVal
            result[b] = nextVal
        }
        return result
    }

    private fun decayInternal(steps: Int = 1): FloatArray {
        rawFrequencyBands.fill(0f)
        targetVisualBands.fill(0f)
        var latest = FloatArray(bandCount)
        val safeSteps = max(1, steps)
        repeat(safeSteps) {
            latest = applyAsymmetricSmoothing()
        }
        return latest
    }

    /**
     * Плавно уменьшает амплитуду всех полос к состоянию покоя (при тишине или остановке).
     */
    fun decayTowardSilence(steps: Int = 1): FloatArray {
        synchronized(lock) {
            return decayInternal(steps)
        }
    }

    /**
     * Возвращает копию честных (до пространственной огибающей) нормализованных частотных полос 0f..1f.
     */
    fun getRawFrequencyBands(): FloatArray {
        synchronized(lock) {
            return rawFrequencyBands.copyOf()
        }
    }

    /**
     * Возвращает копию текущих сглаженных полос для отрисовки (0f..1f).
     */
    fun getSmoothedBands(): FloatArray {
        synchronized(lock) {
            return smoothedBands.copyOf()
        }
    }

    /**
     * Возвращает копию весов пространственной огибающей (32 значения).
     */
    fun getSpatialEnvelope(): FloatArray {
        return spatialEnvelope.copyOf()
    }

    /**
     * Возвращает центральные частоты (в Гц) всех 32 полос.
     */
    fun getBandCenterFrequenciesHz(): FloatArray {
        return bandCenterFreqHz.copyOf()
    }

    /**
     * Полный сброс состояния анализатора (при завершении записи).
     */
    fun reset() {
        synchronized(lock) {
            slidingWindow.fill(0f)
            fftReal.fill(0f)
            fftImag.fill(0f)
            binMagnitudes.fill(0f)
            rawFrequencyBands.fill(0f)
            targetVisualBands.fill(0f)
            smoothedBands.fill(0f)
        }
    }

    private fun sanitizeUnit(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return 0f
        return value.coerceIn(0f, 1f)
    }
}
