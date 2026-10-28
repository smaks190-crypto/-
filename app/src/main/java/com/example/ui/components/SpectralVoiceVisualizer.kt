package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.example.utils.VoiceSpectrumAnalyzer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

// Фирменная палитра спектрального визуализатора (по утверждённому референсу David Finance AI)
private val SpectrumCalmGreen = Color(0xFF22C55E)    // Спокойный (#22C55E)
private val SpectrumActiveEmerald = Color(0xFF10B981) // Активный (#10B981)
private val SpectrumMintCore = Color(0xFF34D399)      // Изумрудно-мятный пик
private val SpectrumMintHighlight = Color(0xFFA7F3D0) // Светлая вершина активных столбиков
private val SpectrumTealMid = Color(0xFF14B8A6)       // Переходный Teal
private val SpectrumSpeakingCyan = Color(0xFF06B6D4)  // Говорит (#06B6D4)
private val SpectrumHighSky = Color(0xFF38BDF8)       // Высокие частоты / Сибилянты
private val SpectrumHighAzure = Color(0xFF3B82F6)     // Крайние правые высокие частоты
private val SpectrumIdleDot = Color(0xFF1E3A34)       // Базовый цвет точек в тишине

val SpectralActiveEnergyKey = SemanticsPropertyKey<Float>("SpectralActiveEnergy")
val SpectralDominantRegionKey = SemanticsPropertyKey<String>("SpectralDominantRegion")

var SemanticsPropertyReceiver.spectralActiveEnergy by SpectralActiveEnergyKey
var SemanticsPropertyReceiver.spectralDominantRegion by SpectralDominantRegionKey

/**
 * 32-полосный горизонтальный спектральный визуализатор голоса для морфинг-капсулы David Finance AI.
 *
 * Отрисовывает 32 независимых вертикальных столбика с закруглёнными концами на основе реального
 * спектра FFT из [VoiceSpectrumAnalyzer]. Не выполняет DSP-вычислений в UI-потоке — только
 * плавную 60 FPS интерполяцию атаки/затухания и отрисовку на Compose Canvas.
 */
@Composable
fun SpectralVoiceVisualizer(
    spectrumBands: FloatArray,
    modifier: Modifier = Modifier,
    isRecording: Boolean = true,
    rmsLevel: Float = 0f,
    barCount: Int = VoiceSpectrumAnalyzer.BAND_COUNT,
    compact: Boolean = false
) {
    val safeBarCount = barCount.coerceIn(8, 64)
    val latestBands by rememberUpdatedState(spectrumBands)
    val latestIsRecording by rememberUpdatedState(isRecording)
    val latestRms by rememberUpdatedState(rmsLevel)

    // Внутренний массив сглаженных значений полос для 60 FPS отрисовки без аллокаций в DrawScope
    val animatedBands = remember(safeBarCount) { FloatArray(safeBarCount) }
    val spatialBreathingEnvelope = remember(safeBarCount) {
        FloatArray(safeBarCount) { idx ->
            val norm = (idx - (safeBarCount - 1) * 0.5f) / ((safeBarCount - 1) * 0.5f)
            (0.32f + 0.68f * exp((-2.4f * norm * norm).toDouble()).toFloat()).coerceIn(0.32f, 1f)
        }
    }

    // Предвычисленные базовые цвета для каждой из 32 полос (от Emerald/Green слева к Mint в центре и Cyan/Azure справа)
    val barBottomColors = remember(safeBarCount) {
        Array(safeBarCount) { idx ->
            val t = idx.toFloat() / max(1, safeBarCount - 1).toFloat()
            when {
                t < 0.35f -> lerpColor(SpectrumCalmGreen, SpectrumActiveEmerald, t / 0.35f)
                t < 0.68f -> lerpColor(SpectrumActiveEmerald, SpectrumSpeakingCyan, (t - 0.35f) / 0.33f)
                else -> lerpColor(SpectrumSpeakingCyan, SpectrumHighAzure, (t - 0.68f) / 0.32f)
            }
        }
    }

    val barTopColors = remember(safeBarCount) {
        Array(safeBarCount) { idx ->
            val t = idx.toFloat() / max(1, safeBarCount - 1).toFloat()
            when {
                t < 0.35f -> lerpColor(SpectrumActiveEmerald, SpectrumMintCore, t / 0.35f)
                t < 0.68f -> lerpColor(SpectrumMintCore, SpectrumSpeakingCyan, (t - 0.35f) / 0.33f)
                else -> lerpColor(SpectrumSpeakingCyan, SpectrumHighSky, (t - 0.68f) / 0.32f)
            }
        }
    }

    var breathTimeSec by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableIntStateOf(0) }

    // Плавный 60 FPS цикл интерполяции атаки/затухания и деликатного дыхания в тишине
    LaunchedEffect(safeBarCount) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val dtSec = if (lastNanos == 0L) {
                    0.016f
                } else {
                    ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.050f)
                }
                lastNanos = nowNanos
                breathTimeSec = (breathTimeSec + dtSec) % 3600f

                val src = latestBands
                val recording = latestIsRecording

                // Быстрая атака (~45 мс) и мягкое затухание (~180 мс) с учётом реального dt кадра
                val frameRatio = (dtSec / 0.01667f).coerceIn(0.4f, 2.5f)
                val attackRate = (0.52f * frameRatio).coerceIn(0.15f, 0.85f)
                val releaseRate = (0.18f * frameRatio).coerceIn(0.06f, 0.45f)
                val stopDecayRate = (0.22f * frameRatio).coerceIn(0.08f, 0.50f)

                var maxInput = 0f
                if (recording) {
                    for (i in 0 until safeBarCount) {
                        val v = if (i < src.size) sanitizeBand(src[i]) else 0f
                        if (v > maxInput) maxInput = v
                    }
                }

                // Если в массиве полос нули, но передан ненулевой rmsLevel (например, из legacy вызова),
                // создаём деликатный отклик, пропорциональный только когда нет данных спектра
                val fallbackRms = if (recording && maxInput < 0.005f && latestRms > 1.35f) {
                    ((latestRms - 1.35f) / 9.5f).coerceIn(0f, 0.35f)
                } else {
                    0f
                }

                for (i in 0 until safeBarCount) {
                    val rawTarget = if (!recording) {
                        0f
                    } else if (i < src.size && maxInput >= 0.005f) {
                        sanitizeBand(src[i])
                    } else if (fallbackRms > 0f) {
                        val wave = 0.75f + 0.25f * sin(breathTimeSec * 4.2f + i * 0.45f)
                        (fallbackRms * spatialBreathingEnvelope[i] * wave).coerceIn(0f, 1f)
                    } else {
                        // В полной тишине — едва заметное спокойное дыхание (не имитирующее речь)
                        val idleWave = 0.5f + 0.5f * sin(breathTimeSec * 2.1f + i * 0.28f)
                        0.018f * spatialBreathingEnvelope[i] * idleWave
                    }

                    val current = animatedBands[i]
                    val rate = when {
                        !recording -> stopDecayRate
                        rawTarget > current -> attackRate
                        else -> releaseRate
                    }
                    var updated = current + (rawTarget - current) * rate
                    if (!recording && updated < 0.002f) {
                        updated = 0f
                    }
                    animatedBands[i] = sanitizeBand(updated)
                }

                frameTick = (frameTick + 1) and 0x7FFFFFFF
            }
        }
    }

    // Вычисляем семантические метрики для UI-тестов (энергия и доминирующий частотный диапазон)
    val semanticsSnapshot = remember(frameTick, spectrumBands, isRecording) {
        var totalEnergy = 0f
        var lowSum = 0f
        var midSum = 0f
        var highSum = 0f
        val third = safeBarCount / 3
        for (i in 0 until safeBarCount) {
            val v = max(
                animatedBands[i],
                if (isRecording && i < spectrumBands.size) sanitizeBand(spectrumBands[i]) else 0f
            )
            totalEnergy += v
            when {
                i < third -> lowSum += v
                i < third * 2 -> midSum += v
                else -> highSum += v
            }
        }
        val avgEnergy = totalEnergy / safeBarCount
        val region = when {
            !isRecording || avgEnergy < 0.03f -> "SILENCE"
            lowSum > midSum * 1.08f && lowSum > highSum * 1.15f -> "LOW"
            highSum > midSum * 1.05f && highSum > lowSum * 1.15f -> "HIGH"
            else -> "MID"
        }
        avgEnergy to region
    }

    Canvas(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .semantics {
                spectralActiveEnergy = semanticsSnapshot.first
                spectralDominantRegion = semanticsSnapshot.second
            }
            .testTag(if (compact) "spectral_voice_visualizer_compact" else "spectral_voice_visualizer")
    ) {
        // Читаем frameTick для подписки DrawScope на 60 FPS обновления
        val tickRead = frameTick
        if (tickRead < 0) return@Canvas

        val w = size.width
        val h = size.height
        if (w <= 4f || h <= 4f) return@Canvas

        // Геометрия 32 столбиков с закруглёнными концами
        val horizontalPad = if (compact) w * 0.02f else w * 0.03f
        val usableWidth = (w - horizontalPad * 2f).coerceAtLeast(safeBarCount * 2f)
        val slotWidth = usableWidth / safeBarCount
        val barWidth = (slotWidth * if (compact) 0.48f else 0.46f)
            .coerceIn(if (compact) 2.0f else 2.5f, if (compact) 5.2f else 6.8f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Нижняя опорная линия столбиков (как на референсе: столбики растут вверх от нижней линии,
        // а в покое выглядят как аккуратный ряд закруглённых точек/штрихов над нижним Aurora-свечением)
        val bottomAnchorY = if (compact) h * 0.86f else h * 0.84f
        val minDotHeight = barWidth.coerceAtLeast(if (compact) 2.4f else 3.0f)
        val maxGrowHeight = (bottomAnchorY - h * 0.08f).coerceAtLeast(minDotHeight + 4f)

        // 1. Деликатное фоновое свечение под активными столбиками (локальные ореолы без пересвета)
        var overallEnergy = 0f
        var weightedCenterIdx = (safeBarCount - 1) * 0.5f
        var weightSum = 0.001f
        for (i in 0 until safeBarCount) {
            val v = max(
                animatedBands[i],
                if (latestIsRecording && i < latestBands.size) sanitizeBand(latestBands[i]) else 0f
            )
            overallEnergy += v
            weightedCenterIdx += i * v
            weightSum += v
        }
        val meanEnergy = (overallEnergy / safeBarCount).coerceIn(0f, 1f)
        val dominantX = horizontalPad + ((weightedCenterIdx / weightSum) + 0.5f) * slotWidth

        if (meanEnergy > 0.025f) {
            val glowRadiusX = (usableWidth * (0.28f + meanEnergy * 0.26f)).coerceAtLeast(h * 1.2f)
            val glowCenterY = bottomAnchorY - maxGrowHeight * (0.18f + meanEnergy * 0.20f)
            val glowColor = if (meanEnergy > 0.52f) SpectrumSpeakingCyan else SpectrumActiveEmerald
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = (0.20f * meanEnergy).coerceAtMost(0.24f)),
                        SpectrumCalmGreen.copy(alpha = (0.10f * meanEnergy).coerceAtMost(0.14f)),
                        Color.Transparent
                    ),
                    center = Offset(dominantX.coerceIn(w * 0.2f, w * 0.8f), glowCenterY),
                    radius = glowRadiusX
                ),
                radius = glowRadiusX,
                center = Offset(dominantX.coerceIn(w * 0.2f, w * 0.8f), glowCenterY),
                blendMode = BlendMode.Plus
            )
        }

        // 2. Отрисовка 32 независимых вертикальных столбиков
        for (i in 0 until safeBarCount) {
            val liveImmediate = if (latestIsRecording && i < latestBands.size) sanitizeBand(latestBands[i]) else 0f
            val level = max(animatedBands[i], liveImmediate * 0.88f).coerceIn(0f, 1f)

            val centerX = horizontalPad + (i + 0.5f) * slotWidth
            val barLeft = centerX - barWidth * 0.5f
            val barHeight = (minDotHeight + level * maxGrowHeight).coerceIn(minDotHeight, bottomAnchorY)
            val barTop = (bottomAnchorY - barHeight).coerceAtLeast(0f)

            val baseBottomColor = barBottomColors[i]
            val baseTopColor = barTopColors[i]

            // Деликатный индивидуальный ореол вокруг активного столбика
            if (level > 0.14f) {
                val haloPadX = barWidth * (0.55f + level * 0.45f)
                val haloPadY = barWidth * (0.40f + level * 0.35f)
                val haloAlpha = ((level - 0.14f) * 0.26f).coerceIn(0f, 0.22f)
                drawRoundRect(
                    color = baseTopColor.copy(alpha = haloAlpha),
                    topLeft = Offset(barLeft - haloPadX * 0.5f, (barTop - haloPadY * 0.5f).coerceAtLeast(0f)),
                    size = Size(barWidth + haloPadX, barHeight + haloPadY),
                    cornerRadius = CornerRadius((barWidth + haloPadX) * 0.5f, (barWidth + haloPadX) * 0.5f),
                    blendMode = BlendMode.Plus
                )
            }

            if (level < 0.045f) {
                // Состояние тишины / покоя: аккуратная скруглённая точка-штрих с мягким изумрудно-бирюзовым оттенком
                val quietAlpha = if (latestIsRecording) {
                    (0.32f + level * 4.5f).coerceIn(0.28f, 0.58f)
                } else {
                    0.20f
                }
                val dotColor = lerpColor(SpectrumIdleDot, baseBottomColor, 0.55f).copy(alpha = quietAlpha)
                drawRoundRect(
                    color = dotColor,
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )
            } else {
                // Активный столбик с вертикальным градиентом (от насыщенной базы к яркой вершине)
                val activeAlpha = (0.68f + level * 0.32f).coerceIn(0.68f, 1.0f)
                val topColor = if (level > 0.65f) {
                    lerpColor(baseTopColor, SpectrumMintHighlight, ((level - 0.65f) / 0.35f) * 0.45f)
                } else {
                    baseTopColor
                }

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            topColor.copy(alpha = activeAlpha),
                            baseBottomColor.copy(alpha = (activeAlpha * 0.88f).coerceAtLeast(0.55f))
                        ),
                        startY = barTop,
                        endY = bottomAnchorY
                    ),
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )
            }
        }
    }
}

private fun sanitizeBand(value: Float): Float {
    if (value.isNaN() || value.isInfinite()) return 0f
    return value.coerceIn(0f, 1f)
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f
    )
}
