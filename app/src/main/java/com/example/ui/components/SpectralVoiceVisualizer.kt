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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Фирменная палитра 32-полосного визуализатора (по утверждённому референсу David Finance AI)
private val SpectrumCalmGreen = Color(0xFF22C55E)    // Спокойный (#22C55E)
private val SpectrumActiveEmerald = Color(0xFF10B981) // Активный (#10B981)
private val SpectrumMintCore = Color(0xFF34D399)      // Изумрудно-мятный пик
private val SpectrumMintHighlight = Color(0xFFA7F3D0) // Светлая вершина активных столбиков
private val SpectrumSpeakingCyan = Color(0xFF06B6D4)  // Говорит (#06B6D4)
private val SpectrumHighSky = Color(0xFF38BDF8)       // Высокие частоты / правое крыло
private val SpectrumHighAzure = Color(0xFF3B82F6)     // Крайние правые высокие полосы
private val SpectrumIdleDot = Color(0xFF1E3A34)       // Базовый цвет точек в тишине

/**
 * 32-полосный горизонтальный визуализатор голоса для морфинг-капсулы David Finance AI.
 *
 * Отрисовывает 32 независимых вертикальных столбика с закруглёнными концами в палитре
 * Obsidian / Emerald / Mint / Teal / Cyan с доминирующим центром, органичной незеркальной
 * формой волны и раздельным сглаживанием атаки и затухания (60 FPS).
 * Работает напрямую от существующего потока `rmsDb` (`rmsLevel`) из `VoiceInputManager`,
 * не затрагивая программную логику записи микрофона, VAD и отправки аудио в Gemini.
 */
@Composable
fun SpectralVoiceVisualizer(
    modifier: Modifier = Modifier,
    isRecording: Boolean = true,
    rmsLevel: Float = 0f,
    barCount: Int = 32,
    compact: Boolean = false,
    spectrumBands: FloatArray? = null
) {
    val safeBarCount = barCount.coerceIn(8, 64)
    val latestBands by rememberUpdatedState(spectrumBands)
    val latestIsRecording by rememberUpdatedState(isRecording)
    val latestRms by rememberUpdatedState(rmsLevel)

    // Внутренний массив сглаженных значений полос для 60 FPS отрисовки без аллокаций в DrawScope
    val animatedBands = remember(safeBarCount) { FloatArray(safeBarCount) }

    // Отдельная пространственная огибающая: центральные столбики визуально доминируют,
    // к краям амплитуда плавно уменьшается (как на утверждённом референсе)
    val spatialEnvelope = remember(safeBarCount) {
        FloatArray(safeBarCount) { idx ->
            val center = (safeBarCount - 1) * 0.5f
            val normDist = abs(idx - center) / center.coerceAtLeast(1f)
            val gaussian = exp((-2.25f * normDist * normDist).toDouble()).toFloat()
            val cosineBell = (0.5 * (1.0 + cos(PI * normDist))).toFloat()
            (0.28f + 0.72f * (0.65f * gaussian + 0.35f * cosineBell)).coerceIn(0.28f, 1.0f)
        }
    }

    // Независимые фазовые и частотные коэффициенты для каждого из 32 столбиков,
    // чтобы форма волны была живой и органичной, а не зеркальной или синхронной
    val barFreqMultiplierA = remember(safeBarCount) {
        FloatArray(safeBarCount) { idx ->
            5.4f + (idx % 7) * 0.83f + (idx * 0.19f)
        }
    }
    val barFreqMultiplierB = remember(safeBarCount) {
        FloatArray(safeBarCount) { idx ->
            9.1f + ((idx * 3) % 11) * 0.67f - (idx * 0.11f)
        }
    }
    val barPhaseOffsetA = remember(safeBarCount) {
        FloatArray(safeBarCount) { idx ->
            idx * 0.71f + ((idx * idx) % 13) * 0.29f
        }
    }
    val barPhaseOffsetB = remember(safeBarCount) {
        FloatArray(safeBarCount) { idx ->
            idx * 1.17f + ((idx * 5) % 17) * 0.23f
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

    var phaseTimeSec by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableIntStateOf(0) }

    // Плавный 60 FPS цикл с раздельным сглаживанием атаки (быстрый отклик) и затухания (мягкий возврат к нулю)
    LaunchedEffect(safeBarCount) {
        var lastNanos = 0L
        var prevRms = 1.0f
        var smoothedVelocity = 0f

        while (true) {
            withFrameNanos { nowNanos ->
                val dtSec = if (lastNanos == 0L) {
                    0.016f
                } else {
                    ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.050f)
                }
                lastNanos = nowNanos
                phaseTimeSec = (phaseTimeSec + dtSec) % 3600f

                val src = latestBands
                val recording = latestIsRecording
                val rawRms = if (latestRms.isNaN() || latestRms.isInfinite()) 0f else latestRms

                // Отслеживаем динамику интонации (скорость изменения громкости голоса)
                val instantDelta = abs(rawRms - prevRms)
                prevRms = rawRms
                smoothedVelocity = (smoothedVelocity * 0.78f + instantDelta * 0.22f).coerceIn(0f, 3.5f)

                // Быстрая атака (~40 мс) и мягкое затухание (~185 мс)
                val frameRatio = (dtSec / 0.01667f).coerceIn(0.4f, 2.5f)
                val attackRate = (0.54f * frameRatio).coerceIn(0.18f, 0.85f)
                val releaseRate = (0.18f * frameRatio).coerceIn(0.06f, 0.42f)
                val stopDecayRate = (0.22f * frameRatio).coerceIn(0.08f, 0.48f)

                var maxExplicitBand = 0f
                if (recording && src != null) {
                    for (i in 0 until minOf(safeBarCount, src.size)) {
                        val v = sanitizeBand(src[i])
                        if (v > maxExplicitBand) maxExplicitBand = v
                    }
                }

                // Нормализация rmsDb из VoiceInputManager (в тишине 1.0..2.2, речь 2.4..12.0)
                // С мягкой компрессией (soft-knee), чтобы на громкой речи столбики не упирались в потолок одновременно
                val speechGate = 2.15f
                val normalizedVoice = if (recording && rawRms > speechGate) {
                    val linear = ((rawRms - speechGate) / (11.2f - speechGate)).coerceIn(0f, 1f)
                    val curved = 0.65f * sqrt(linear) + 0.35f * linear
                    ((curved * 1.24f) / (1.0f + 0.24f * curved * curved)).coerceIn(0f, 0.96f)
                } else {
                    0f
                }

                val velocityBoost = (smoothedVelocity / 2.2f).coerceIn(0f, 0.28f)

                for (i in 0 until safeBarCount) {
                    val rawTarget = if (!recording) {
                        0f
                    } else if (src != null && i < src.size && maxExplicitBand >= 0.005f) {
                        sanitizeBand(src[i])
                    } else if (normalizedVoice > 0.005f) {
                        // Независимая многочастотная модуляция каждого столбика с учётом темпа речи
                        val oscA = 0.5f + 0.5f * sin(phaseTimeSec * barFreqMultiplierA[i] + barPhaseOffsetA[i])
                        val oscB = 0.5f + 0.5f * cos(phaseTimeSec * barFreqMultiplierB[i] + barPhaseOffsetB[i])
                        val barTexture = (0.34f + 0.42f * oscA + 0.24f * oscB).coerceIn(0.28f, 1.0f)

                        // На тихой речи активен преимущественно центр, на громкой и эмоциональной речи подключаются края
                        val normPos = abs(i - (safeBarCount - 1) * 0.5f) / ((safeBarCount - 1) * 0.5f).coerceAtLeast(1f)
                        val quietCenterFocus = if (normalizedVoice < 0.35f) {
                            (1.0f - normPos * (0.45f - normalizedVoice)).coerceIn(0.35f, 1.0f)
                        } else {
                            1.0f
                        }

                        // Смещение акцента влево/вправо в зависимости от динамики атаки голоса (незеркальная волна)
                        val horizRatio = i.toFloat() / max(1, safeBarCount - 1).toFloat()
                        val inflectionTilt = if (velocityBoost > 0.08f) {
                            0.88f + 0.26f * horizRatio * (velocityBoost / 0.28f)
                        } else {
                            1.04f - 0.10f * horizRatio
                        }

                        val combined = (normalizedVoice + velocityBoost * 0.35f) *
                            spatialEnvelope[i] *
                            quietCenterFocus *
                            barTexture *
                            inflectionTilt

                        sanitizeBand(combined)
                    } else {
                        // В тишине столбики остаются короткими с едва заметным плавным дыханием без ложной речи
                        val idleWave = 0.5f + 0.5f * sin(phaseTimeSec * 2.1f + i * 0.28f)
                        0.018f * spatialEnvelope[i] * idleWave
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

    Canvas(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .testTag(if (compact) "spectral_voice_visualizer_compact" else "spectral_voice_visualizer")
    ) {
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
            val v = animatedBands[i]
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
            val level = animatedBands[i].coerceIn(0f, 1f)

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
