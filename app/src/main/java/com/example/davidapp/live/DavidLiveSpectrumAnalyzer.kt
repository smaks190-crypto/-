package com.example.davidapp.live

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Реальный спектральный FFT-анализатор для кругового визуализатора David Live.
 *
 * Особенности:
 * - Использует оконную функцию Ханна (Hann window) и in-place Radix-2 Cooley-Tukey FFT (1024 отсчёта)
 *   без создания объектов в горячем цикле обработки аудио.
 * - Поддерживает как входной поток микрофона (16 кГц PCM), так и выходной голосовой поток Давида (24 кГц PCM).
 * - Рассчитывает 32 базовые частотные полосы речи (85 Гц .. 7600 Гц) и отображает их
 *   на 48 радиальных лучей кругового визуализатора в соответствии со схемой «Детали» референса:
 *   - Индекс 0 (12 часов, верхний полюс) и индекс 24 (6 часов, нижний полюс) акцентируют
 *     средние и высокие частоты речи («Высокие частоты (центр, крупнее)»);
 *   - Индекс 12 (3 часа, правый край) и индекс 36 (9 часов, левый край) отражают
 *     низкие частоты с плавным затуханием («Низкие частоты (к краям, меньше)»).
 * - Использует раздельное сглаживание атаки (быстрый отклик) и затухания (плавный возврат в покой).
 */
class DavidLiveSpectrumAnalyzer(
    val sampleRate: Int = 16_000,
    val fftSize: Int = 1024,
    val radialRayCount: Int = DEFAULT_RADIAL_RAY_COUNT,
    private val attackAlpha: Float = 0.72f,
    private val releaseAlpha: Float = 0.22f
) {
    companion object {
        const val DEFAULT_RADIAL_RAY_COUNT = 48
        const val RAY_COUNT = DEFAULT_RADIAL_RAY_COUNT
        const val INTERNAL_FREQ_BANDS = 32

        private const val MIN_FREQ_HZ = 85.0f
        private const val MAX_FREQ_HZ = 7600.0f
        private const val NOISE_FLOOR_DB = -62.0f
        private const val CEILING_DB = -12.0f
        private const val DYNAMIC_RANGE_DB = CEILING_DB - NOISE_FLOOR_DB
        private const val SILENCE_RMS_GATE = 0.0020f
    }

    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "fftSize must be a power of 2"
        }
        require(radialRayCount >= 16 && radialRayCount % 4 == 0) {
            "radialRayCount must be a multiple of 4 (got $radialRayCount)"
        }
    }

    @Volatile
    var overallEnergy: Float = 0f
        private set

    private val halfFftSize = fftSize shr 1
    private val slidingWindow = FloatArray(fftSize)
    private val hannWindow = FloatArray(fftSize)
    private val bitReversedIndices = IntArray(fftSize)
    private val twiddleCos = FloatArray(halfFftSize)
    private val twiddleSin = FloatArray(halfFftSize)

    private val fftReal = FloatArray(fftSize)
    private val fftImag = FloatArray(fftSize)
    private val binMagnitudes = FloatArray(halfFftSize)

    private val bandStartBin = IntArray(INTERNAL_FREQ_BANDS)
    private val bandEndBin = IntArray(INTERNAL_FREQ_BANDS)
    private val bandPreEmphasis = FloatArray(INTERNAL_FREQ_BANDS)
    private val rawFreqBands = FloatArray(INTERNAL_FREQ_BANDS)

    // Пространственная огибающая для каждого из 48 радиальных лучей круга:
    // максимум на вертикальной оси (индекс 0 = 12 часов, индекс 24 = 6 часов),
    // плавное уменьшение к горизонтальным краям (индекс 12 = 3 часа, индекс 36 = 9 часов)
    private val radialSpatialEnvelope = FloatArray(radialRayCount)
    // Индекс частотной полосы [0..31] для каждого радиального луча
    private val radialFreqBandMap = IntArray(radialRayCount)

    private val targetRays = FloatArray(radialRayCount)
    private val smoothedRays = FloatArray(radialRayCount)
    private val outputSnapshot = FloatArray(radialRayCount)
    private val rawSnapshot = FloatArray(INTERNAL_FREQ_BANDS)

    private val lock = Any()

    init {
        initHannWindow()
        initBitReversalAndTwiddles()
        initMelBands(sampleRate)
        initRadialTopology()
    }

    private fun initHannWindow() {
        val denom = (fftSize - 1).coerceAtLeast(1).toDouble()
        for (i in 0 until fftSize) {
            hannWindow[i] = (0.5 * (1.0 - cos(2.0 * PI * i / denom))).toFloat()
        }
    }

    private fun initBitReversalAndTwiddles() {
        var bits = 0
        var temp = fftSize
        while (temp > 1) {
            bits++
            temp = temp shr 1
        }
        for (i in 0 until fftSize) {
            var rev = 0
            var x = i
            for (b in 0 until bits) {
                rev = (rev shl 1) or (x and 1)
                x = x shr 1
            }
            bitReversedIndices[i] = rev
        }
        for (k in 0 until halfFftSize) {
            val angle = -2.0 * PI * k.toDouble() / fftSize.toDouble()
            twiddleCos[k] = cos(angle).toFloat()
            twiddleSin[k] = sin(angle).toFloat()
        }
    }

    private fun initMelBands(rateHz: Int) {
        fun hzToMel(hz: Float): Float = (2595.0 * log10(1.0 + hz / 700.0)).toFloat()
        fun melToHz(mel: Float): Float = (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()

        val nyquist = rateHz / 2.0f
        val maxHz = min(MAX_FREQ_HZ, nyquist * 0.95f)
        val minMel = hzToMel(MIN_FREQ_HZ)
        val maxMel = hzToMel(maxHz)
        val binResolutionHz = rateHz.toFloat() / fftSize.toFloat()

        var prevEnd = 0
        for (b in 0 until INTERNAL_FREQ_BANDS) {
            val melL = minMel + (maxMel - minMel) * (b.toFloat() / INTERNAL_FREQ_BANDS.toFloat())
            val melH = minMel + (maxMel - minMel) * ((b + 1).toFloat() / INTERNAL_FREQ_BANDS.toFloat())
            val hzL = melToHz(melL)
            val hzH = melToHz(melH)
            val hzCenter = 0.5f * (hzL + hzH)

            var sBin = (hzL / binResolutionHz).roundToInt().coerceIn(1, halfFftSize - 1)
            var eBin = (hzH / binResolutionHz).roundToInt().coerceIn(sBin, halfFftSize - 1)
            if (sBin <= prevEnd && prevEnd < halfFftSize - 1) {
                sBin = prevEnd + 1
            }
            if (eBin < sBin) eBin = sBin
            prevEnd = eBin

            bandStartBin[b] = sBin
            bandEndBin[b] = eBin

            val normFreq = (hzCenter / 1000.0f).coerceIn(0.08f, 8.0f)
            bandPreEmphasis[b] = (0.96f + 0.34f * ln(1.0f + normFreq)).coerceIn(0.92f, 1.68f)
        }
    }

    /**
     * Строит круговую топологию лучей (индекс 0 = 12 часов / верхний центр, обход по часовой стрелке):
     * - Около 12 часов (индекс 0) и 6 часов (индекс radialRayCount/2 = 24) расположены высокие и центральные
     *   речевые частоты с максимальной пространственной огибающей (1.0).
     * - По направлению к 3 часам (индекс 12) и 9 часам (индекс 36) расположены
     *   средние и низкие частоты с плавным затуханием огибающей (до 0.38).
     */
    private fun initRadialTopology() {
        for (i in 0 until radialRayCount) {
            val angleFromTopRad = 2.0 * PI * i.toDouble() / radialRayCount.toDouble()
            val verticalness = abs(cos(angleFromTopRad)).toFloat()

            val gaussianPole = exp(-1.55 * (1.0 - verticalness) * (1.0 - verticalness)).toFloat()
            radialSpatialEnvelope[i] = (0.38f + 0.62f * (0.58f * verticalness + 0.42f * gaussianPole)).coerceIn(0.38f, 1.0f)

            val hemisphereOffset = if (sin(angleFromTopRad) >= 0.0) 0 else 1
            val mappedBand = ((verticalness * (INTERNAL_FREQ_BANDS - 2)).roundToInt() + hemisphereOffset)
                .coerceIn(0, INTERNAL_FREQ_BANDS - 1)
            radialFreqBandMap[i] = mappedBand
        }
    }

    /**
     * Удобный метод для [DavidLiveAudioEngine]: принимает PCM16 кадр и частоту дискретизации,
     * возвращает неизменяемый список из 48 нормализованных значений лучей.
     */
    fun analyzePcmFrame(
        pcmSamples: ShortArray,
        sampleCount: Int = pcmSamples.size,
        sampleRateHz: Int = sampleRate
    ): List<Float> {
        synchronized(lock) {
            if (sampleRateHz > 0 && sampleRateHz != sampleRate) {
                initMelBands(sampleRateHz)
            } else {
                initMelBands(sampleRate)
            }
            return processPcm16Frame(pcmSamples, sampleCount).toList()
        }
    }

    /**
     * Выполняет один шаг плавного затухания лучей к состоянию покоя и возвращает `List<Float>`.
     */
    fun decayFrame(): List<Float> {
        return decayTowardSilence(steps = 1).toList()
    }

    /**
     * Обрабатывает 16-bit PCM кадр (`ShortArray`) и возвращает 48 нормализованных радиальных полос `[0.0f, 1.0f]`.
     */
    fun processPcm16Frame(pcm: ShortArray, readCount: Int): FloatArray {
        synchronized(lock) {
            val valid = readCount.coerceIn(0, pcm.size)
            if (valid <= 0) return decayToIdleInternal()

            if (valid >= fftSize) {
                val offset = valid - fftSize
                for (i in 0 until fftSize) {
                    slidingWindow[i] = sanitizeSample(pcm[offset + i].toFloat() / 32768.0f)
                }
            } else {
                val keep = fftSize - valid
                System.arraycopy(slidingWindow, valid, slidingWindow, 0, keep)
                for (i in 0 until valid) {
                    slidingWindow[keep + i] = sanitizeSample(pcm[i].toFloat() / 32768.0f)
                }
            }
            return computeRadialSpectrum()
        }
    }

    /**
     * Обрабатывает массив Little-Endian 16-bit PCM байтов (например, из ответа Gemini Live 24 кГц).
     */
    fun processPcm16Bytes(bytes: ByteArray, byteLength: Int = bytes.size): FloatArray {
        synchronized(lock) {
            val safeBytes = byteLength.coerceIn(0, bytes.size)
            val sampleCount = safeBytes / 2
            if (sampleCount <= 0) return decayToIdleInternal()

            if (sampleCount >= fftSize) {
                val startSample = sampleCount - fftSize
                for (i in 0 until fftSize) {
                    val bIdx = (startSample + i) * 2
                    val low = bytes[bIdx].toInt() and 0xFF
                    val high = bytes[bIdx + 1].toInt()
                    val sampleShort = ((high shl 8) or low).toShort()
                    slidingWindow[i] = sanitizeSample(sampleShort.toFloat() / 32768.0f)
                }
            } else {
                val keep = fftSize - sampleCount
                System.arraycopy(slidingWindow, sampleCount, slidingWindow, 0, keep)
                for (i in 0 until sampleCount) {
                    val bIdx = i * 2
                    val low = bytes[bIdx].toInt() and 0xFF
                    val high = bytes[bIdx + 1].toInt()
                    val sampleShort = ((high shl 8) or low).toShort()
                    slidingWindow[keep + i] = sanitizeSample(sampleShort.toFloat() / 32768.0f)
                }
            }
            return computeRadialSpectrum()
        }
    }

    /**
     * Обрабатывает нормализованные float-отсчёты `[-1f, 1f]` (для unit-тестов и генераторов).
     */
    fun processNormalizedSamples(samples: FloatArray, count: Int = samples.size): FloatArray {
        synchronized(lock) {
            val valid = count.coerceIn(0, samples.size)
            if (valid <= 0) return decayToIdleInternal()

            if (valid >= fftSize) {
                val offset = valid - fftSize
                for (i in 0 until fftSize) {
                    slidingWindow[i] = sanitizeSample(samples[offset + i])
                }
            } else {
                val keep = fftSize - valid
                System.arraycopy(slidingWindow, valid, slidingWindow, 0, keep)
                for (i in 0 until valid) {
                    slidingWindow[keep + i] = sanitizeSample(samples[i])
                }
            }
            return computeRadialSpectrum()
        }
    }

    private fun computeRadialSpectrum(): FloatArray {
        var sumSq = 0.0f
        for (i in 0 until fftSize) {
            val s = slidingWindow[i]
            sumSq += s * s
        }
        val rms = sqrt(sumSq / fftSize.toFloat())
        if (!rms.isFinite() || rms < SILENCE_RMS_GATE) {
            rawFreqBands.fill(0f)
            targetRays.fill(0f)
            return applySmoothing()
        }

        for (i in 0 until fftSize) {
            val wSample = slidingWindow[i] * hannWindow[i]
            val rev = bitReversedIndices[i]
            fftReal[rev] = wSample
            fftImag[rev] = 0.0f
        }

        var stageSize = 2
        while (stageSize <= fftSize) {
            val halfStage = stageSize shr 1
            val step = fftSize / stageSize
            var groupStart = 0
            while (groupStart < fftSize) {
                var twiddleIdx = 0
                for (k in 0 until halfStage) {
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
                    twiddleIdx += step
                }
                groupStart += stageSize
            }
            stageSize = stageSize shl 1
        }

        val normScale = 2.0f / (fftSize.toFloat() * 0.5f)
        binMagnitudes[0] = 0f
        var maxBandEnergy = 0f
        for (bin in 1 until halfFftSize) {
            val re = fftReal[bin]
            val im = fftImag[bin]
            val mag = sqrt(re * re + im * im) * normScale
            binMagnitudes[bin] = if (mag.isFinite()) mag else 0f
        }

        for (b in 0 until INTERNAL_FREQ_BANDS) {
            val start = bandStartBin[b]
            val end = bandEndBin[b]
            var energySum = 0.0f
            var peak = 0.0f
            var count = 0
            for (bin in start..end) {
                val m = binMagnitudes[bin]
                energySum += m * m
                if (m > peak) peak = m
                count++
            }
            val rmsMag = if (count > 0) sqrt(energySum / count.toFloat()) else 0.0f
            val combined = (0.62f * peak + 0.38f * rmsMag) * bandPreEmphasis[b]
            val db = 20.0f * log10(max(combined, 1e-7f))
            val normDb = ((db - NOISE_FLOOR_DB) / DYNAMIC_RANGE_DB).coerceIn(0.0f, 1.0f)
            val compressed = (normDb * 1.42f) / (1.0f + 0.42f * normDb * normDb)
            val unitVal = sanitizeUnit(compressed)
            rawFreqBands[b] = unitVal
            if (unitVal > maxBandEnergy) maxBandEnergy = unitVal
        }

        val overallGain = ((rms - SILENCE_RMS_GATE) / 0.065f).coerceIn(0.38f, 1.0f)
        val softGain = 0.72f + 0.28f * sqrt(overallGain)

        // Отображаем 32 частотные полосы на 48 радиальных лучей с учётом круговой пространственной огибающей
        // и гармонической поддержки, чтобы вся корона лучей жила при речи, а полюса доминировали
        for (r in 0 until radialRayCount) {
            val bandIdx = radialFreqBandMap[r]
            val prevBand = rawFreqBands[(bandIdx - 1).coerceAtLeast(0)]
            val currBand = rawFreqBands[bandIdx]
            val nextBand = rawFreqBands[(bandIdx + 1).coerceAtMost(INTERNAL_FREQ_BANDS - 1)]

            val midSpeechSupport = if (bandIdx >= 14) {
                val midIdx = (bandIdx - 7).coerceIn(6, 20)
                0.38f * rawFreqBands[midIdx]
            } else {
                0.22f * maxBandEnergy
            }

            val blendedFreq = (0.68f * currBand + 0.16f * prevBand + 0.16f * nextBand + midSpeechSupport)
                .coerceIn(0f, 1f)
            targetRays[r] = sanitizeUnit(blendedFreq * radialSpatialEnvelope[r] * softGain)
        }

        return applySmoothing()
    }

    private fun applySmoothing(): FloatArray {
        var energyAcc = 0f
        var peakRay = 0f
        for (i in 0 until radialRayCount) {
            val target = sanitizeUnit(targetRays[i])
            val current = sanitizeUnit(smoothedRays[i])
            val alpha = if (target > current) attackAlpha else releaseAlpha
            var next = current + alpha * (target - current)
            if (next < 0.002f && target == 0f) next = 0f
            next = sanitizeUnit(next)
            smoothedRays[i] = next
            outputSnapshot[i] = next
            energyAcc += next
            if (next > peakRay) peakRay = next
        }
        val meanRay = energyAcc / radialRayCount.toFloat()
        overallEnergy = sanitizeUnit(0.55f * meanRay + 0.45f * peakRay)
        return outputSnapshot.copyOf()
    }

    private fun decayToIdleInternal(): FloatArray {
        rawFreqBands.fill(0f)
        targetRays.fill(0f)
        return applySmoothing()
    }

    fun decayTowardSilence(steps: Int = 1): FloatArray {
        synchronized(lock) {
            var res = outputSnapshot
            for (s in 0 until steps.coerceAtLeast(1)) {
                res = decayToIdleInternal()
            }
            return res
        }
    }

    fun getRawFrequencyBands(): FloatArray {
        synchronized(lock) {
            System.arraycopy(rawFreqBands, 0, rawSnapshot, 0, INTERNAL_FREQ_BANDS)
            return rawSnapshot.copyOf()
        }
    }

    fun getRadialSpatialEnvelope(): FloatArray = radialSpatialEnvelope.copyOf()

    fun reset() {
        synchronized(lock) {
            slidingWindow.fill(0f)
            fftReal.fill(0f)
            fftImag.fill(0f)
            binMagnitudes.fill(0f)
            rawFreqBands.fill(0f)
            targetRays.fill(0f)
            smoothedRays.fill(0f)
            outputSnapshot.fill(0f)
            rawSnapshot.fill(0f)
            overallEnergy = 0f
        }
    }

    private fun sanitizeSample(v: Float): Float = if (v.isFinite()) v.coerceIn(-1f, 1f) else 0f
    private fun sanitizeUnit(v: Float): Float = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f
}
