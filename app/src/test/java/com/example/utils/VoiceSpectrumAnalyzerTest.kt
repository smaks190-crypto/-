package com.example.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class VoiceSpectrumAnalyzerTest {

    private fun generatePcm16LeTone(
        frequenciesHz: List<Pair<Double, Double>>,
        sampleCount: Int = 1024,
        sampleRate: Int = 16000
    ): ByteArray {
        val buf = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (n in 0 until sampleCount) {
            var sampleVal = 0.0
            for ((freq, amp) in frequenciesHz) {
                sampleVal += amp * sin(2.0 * PI * freq * n / sampleRate)
            }
            val clamped = sampleVal.coerceIn(-1.0, 1.0)
            val pcmShort = (clamped * 32767.0).toInt().toShort()
            buf.putShort(pcmShort)
        }
        return buf.array()
    }

    @Test
    fun test1_bandCountAndCenterFrequenciesAreMonotonicWithinSpeechRange() {
        val analyzer = VoiceSpectrumAnalyzer()
        val centers = analyzer.getBandCenterFrequenciesHz()
        val envelope = analyzer.getSpatialEnvelope()

        assertEquals(32, centers.size)
        assertEquals(32, envelope.size)

        for (i in centers.indices) {
            assertTrue("Center frequency must be >= 80 Hz, got ${centers[i]}", centers[i] >= 80f)
            assertTrue("Center frequency must be <= 7800 Hz, got ${centers[i]}", centers[i] <= 7800f)
            if (i > 0) {
                assertTrue(
                    "Band center frequencies must strictly increase: band[$i]=${centers[i]} vs band[${i - 1}]=${centers[i - 1]}",
                    centers[i] > centers[i - 1]
                )
            }
        }

        // Центральная область пространственной огибающей доминирует над краями
        assertTrue(envelope[15] > envelope[0] * 1.8f)
        assertTrue(envelope[16] > envelope[31] * 1.8f)
    }

    @Test
    fun test2_silenceAndSubThresholdNoiseStayZero() {
        val analyzer = VoiceSpectrumAnalyzer()
        val silencePcm = ByteArray(1024)
        val bands = analyzer.processPcm16LeFrame(silencePcm)

        assertEquals(32, bands.size)
        for (b in bands) {
            assertEquals(0f, b, 1e-5f)
        }

        // Очень тихий тепловой шум ниже порога гейта также не должен вызывать ложных колебаний
        val tinyNoise = generatePcm16LeTone(listOf(440.0 to 0.001))
        val gatedBands = analyzer.processPcm16LeFrame(tinyNoise)
        for (b in gatedBands) {
            assertEquals(0f, b, 1e-4f)
        }
    }

    @Test
    fun test3_lowVoiceSignalDominatesLeftLowFrequencyBands() {
        val analyzer = VoiceSpectrumAnalyzer()
        // Мужской бас/баритон: основной тон 140 Гц + гармоника 240 Гц
        val lowVoicePcm = generatePcm16LeTone(
            listOf(140.0 to 0.55, 240.0 to 0.35),
            sampleCount = 1024
        )
        repeat(4) {
            analyzer.processPcm16LeFrame(lowVoicePcm)
        }

        val raw = analyzer.getRawFrequencyBands()
        val visual = analyzer.getSmoothedBands()

        val lowRawSum = (0..9).sumOf { raw[it].toDouble() }
        val highRawSum = (22..31).sumOf { raw[it].toDouble() }
        assertTrue("Low frequency raw bands ($lowRawSum) must dominate high bands ($highRawSum)", lowRawSum > highRawSum * 4.0)

        val lowVisualSum = (0..10).sumOf { visual[it].toDouble() }
        val highVisualSum = (21..31).sumOf { visual[it].toDouble() }
        assertTrue("Low voice visual bands ($lowVisualSum) must exceed high bands ($highVisualSum)", lowVisualSum > highVisualSum * 3.0)
    }

    @Test
    fun test4_normalMidSpeechSignalPeaksInCenterBands() {
        val analyzer = VoiceSpectrumAnalyzer()
        // Основные речевые форманты (гласные звуки ~650 Гц и ~1150 Гц)
        val midSpeechPcm = generatePcm16LeTone(
            listOf(650.0 to 0.45, 1150.0 to 0.40),
            sampleCount = 1024
        )
        repeat(4) {
            analyzer.processPcm16LeFrame(midSpeechPcm)
        }

        val visual = analyzer.getSmoothedBands()
        val centerSum = (10..21).sumOf { visual[it].toDouble() }
        val leftEdgeSum = (0..5).sumOf { visual[it].toDouble() }
        val rightEdgeSum = (26..31).sumOf { visual[it].toDouble() }

        assertTrue("Center bands ($centerSum) must dominate left edge ($leftEdgeSum)", centerSum > leftEdgeSum * 3.0)
        assertTrue("Center bands ($centerSum) must dominate right edge ($rightEdgeSum)", centerSum > rightEdgeSum * 3.0)
    }

    @Test
    fun test5_highVoiceAndSibilantSignalDominatesRightHighFrequencyBands() {
        val analyzer = VoiceSpectrumAnalyzer()
        // Высокий голос и звонкие согласные / сибилянты (3200 Гц и 4800 Гц)
        val highVoicePcm = generatePcm16LeTone(
            listOf(3200.0 to 0.45, 4800.0 to 0.35),
            sampleCount = 1024
        )
        repeat(4) {
            analyzer.processPcm16LeFrame(highVoicePcm)
        }

        val raw = analyzer.getRawFrequencyBands()
        val visual = analyzer.getSmoothedBands()

        val highRawSum = (22..31).sumOf { raw[it].toDouble() }
        val lowRawSum = (0..9).sumOf { raw[it].toDouble() }
        assertTrue("High frequency raw bands ($highRawSum) must dominate low bands ($lowRawSum)", highRawSum > lowRawSum * 4.0)

        val highVisualSum = (21..31).sumOf { visual[it].toDouble() }
        val lowVisualSum = (0..10).sumOf { visual[it].toDouble() }
        assertTrue("High voice visual bands ($highVisualSum) must exceed low bands ($lowVisualSum)", highVisualSum > lowVisualSum * 3.0)
    }

    @Test
    fun test6_quietNormalAndLoudSpeechScaleMonotonicallyWithoutHardClippingAllBars() {
        val quietAnalyzer = VoiceSpectrumAnalyzer()
        val normalAnalyzer = VoiceSpectrumAnalyzer()
        val loudAnalyzer = VoiceSpectrumAnalyzer()

        val quietPcm = generatePcm16LeTone(listOf(500.0 to 0.03, 950.0 to 0.02, 1600.0 to 0.015))
        val normalPcm = generatePcm16LeTone(listOf(500.0 to 0.16, 950.0 to 0.12, 1600.0 to 0.08))
        val loudPcm = generatePcm16LeTone(listOf(500.0 to 0.55, 950.0 to 0.35, 1600.0 to 0.25))

        repeat(5) {
            quietAnalyzer.processPcm16LeFrame(quietPcm)
            normalAnalyzer.processPcm16LeFrame(normalPcm)
            loudAnalyzer.processPcm16LeFrame(loudPcm)
        }

        val quietEnergy = quietAnalyzer.getSmoothedBands().sum()
        val normalEnergy = normalAnalyzer.getSmoothedBands().sum()
        val loudBands = loudAnalyzer.getSmoothedBands()
        val loudEnergy = loudBands.sum()

        assertTrue("Normal speech ($normalEnergy) must be louder than quiet speech ($quietEnergy)", normalEnergy > quietEnergy * 1.25f)
        assertTrue("Loud speech ($loudEnergy) must be louder than normal speech ($normalEnergy)", loudEnergy > normalEnergy * 1.10f)

        // При громкой речи не все столбики должны упираться в максимум 1.0f
        val maxedCount = loudBands.count { it >= 0.98f }
        assertTrue("Loud speech should not clip all 32 bars to 1.0f (maxed=$maxedCount)", maxedCount < 6)
    }

    @Test
    fun test7_asymmetricAttackAndReleaseSmoothing() {
        val analyzer = VoiceSpectrumAnalyzer(attackAlpha = 0.65f, releaseAlpha = 0.20f)
        val speechPcm = generatePcm16LeTone(listOf(800.0 to 0.50), sampleCount = 1024)

        // Одиночный кадр атаки сразу дает заметный отклик (> 50% от установившегося уровня)
        val firstFrameBands = analyzer.processPcm16LeFrame(speechPcm)
        val firstFramePeak = firstFrameBands.maxOrNull() ?: 0f
        assertTrue("Fast attack must produce immediate visible response, got $firstFramePeak", firstFramePeak > 0.30f)

        repeat(4) { analyzer.processPcm16LeFrame(speechPcm) }
        val steadyPeak = analyzer.getSmoothedBands().maxOrNull() ?: 0f

        // При наступлении тишины спад идет плавно: после 1 кадра тишины остается ~80% амплитуды
        val afterOneSilenceFrame = analyzer.decayTowardSilence(steps = 1).maxOrNull() ?: 0f
        assertTrue(
            "Release must decay smoothly (expected > 65% of steadyPeak=$steadyPeak after 1 step, got $afterOneSilenceFrame)",
            afterOneSilenceFrame > steadyPeak * 0.65f && afterOneSilenceFrame < steadyPeak
        )

        // После серии кадров тишины плавно затухает к нулю
        val afterLongSilence = analyzer.decayTowardSilence(steps = 40).maxOrNull() ?: 1f
        assertTrue("After long silence all bars must approach 0, got $afterLongSilence", afterLongSilence < 0.01f)
    }

    @Test
    fun test8_nanInfinityAndExtremePcmInputsNeverProduceInvalidValues() {
        val analyzer = VoiceSpectrumAnalyzer()

        val corruptedSamples = FloatArray(1024) { idx ->
            when (idx % 4) {
                0 -> Float.NaN
                1 -> Float.POSITIVE_INFINITY
                2 -> Float.NEGATIVE_INFINITY
                else -> 50.0f
            }
        }
        val outFromCorrupted = analyzer.processNormalizedSamples(corruptedSamples)
        for (v in outFromCorrupted) {
            assertFalse("Band value must not be NaN", v.isNaN())
            assertFalse("Band value must not be Infinite", v.isInfinite())
            assertTrue("Band value must be in 0f..1f, got $v", v in 0f..1f)
        }

        // Полный размах 16-bit PCM (Short.MIN_VALUE / Short.MAX_VALUE) и нечетная длина буфера
        val extremePcm = ByteArray(1025) { idx -> if (idx % 2 == 0) 0xFF.toByte() else 0x7F.toByte() }
        val outExtreme = analyzer.processPcm16LeFrame(extremePcm, extremePcm.size)
        for (v in outExtreme) {
            assertFalse(v.isNaN())
            assertFalse(v.isInfinite())
            assertTrue(v in 0f..1f)
        }
    }

    @Test
    fun test9_resetClearsAllStateImmediately() {
        val analyzer = VoiceSpectrumAnalyzer()
        val speechPcm = generatePcm16LeTone(listOf(700.0 to 0.5, 1400.0 to 0.4))
        repeat(3) { analyzer.processPcm16LeFrame(speechPcm) }
        assertTrue((analyzer.getSmoothedBands().maxOrNull() ?: 0f) > 0.3f)

        analyzer.reset()
        for (b in analyzer.getSmoothedBands()) {
            assertEquals(0f, b, 1e-6f)
        }
        for (b in analyzer.getRawFrequencyBands()) {
            assertEquals(0f, b, 1e-6f)
        }
    }
}
