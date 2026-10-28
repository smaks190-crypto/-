package com.example.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Фирменная изумрудно-мятная палитра приложения David Finance (Obsidian Neo-Bank Emerald)
private val AuroraSpaceBg = Color(0xFF080C14)
private val AuroraDeepEmerald = Color(0xFF065F46)
private val AuroraEmerald = Color(0xFF10B981)
private val AuroraNeonMint = Color(0xFF34D399)
private val AuroraTealCyan = Color(0xFF14B8A6)
private val AuroraSubtleIndigo = Color(0xFF6366F1)
private val AuroraCoreMint = Color(0xFFA7F3D0)

/**
 * Компактная жидкая неоновая анимация в фирменном изумрудном цвете приложения (Obsidian Emerald Edge Glow).
 * Формирует деликатное второстепенное многослойное Aurora-свечение строго вдоль нижней кромки капсулы микрофона,
 * подчёркивая 32-полосный спектральный визуализатор без засветки столбиков, текста и кнопок действий.
 */
@Composable
fun VolumetricLiquidAuroraGlow(
    isRecording: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 36.dp,
    maxAuroraHeight: Dp? = null,
    showGlassBorder: Boolean = true,
    fillBackground: Boolean = true,
    auroraIntensityScale: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    // Приводим rmsDb (обычно 1..12) или уже нормализованный уровень (0..1) к единому диапазону 0f..1f
    val rawNormalized = remember(isRecording, rmsLevel) {
        if (!isRecording) {
            0.04f
        } else if (rmsLevel <= 1.05f && rmsLevel >= 0f) {
            rmsLevel.coerceIn(0f, 1f)
        } else {
            ((rmsLevel - 1.2f) / 9.2f).coerceIn(0f, 1f)
        }
    }

    val safeIntensity = auroraIntensityScale.coerceIn(0.20f, 1.0f)

    // Пружинное сглаживание уровня голоса
    val smoothedVoiceLevel by animateFloatAsState(
        targetValue = rawNormalized,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "aurora_voice_spring"
    )

    // Непрерывное «дыхание» (Idle Breathing) с 4 независимыми несинхронными фазами
    val infiniteTransition = rememberInfiniteTransition(label = "gemini_live_liquid_aurora")

    val phase2100 by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_phase_2100"
    )

    val phase2700 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_phase_2700"
    )

    val phase3300 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase_3300"
    )

    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_idle_breath"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val glassBorderBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                AuroraEmerald.copy(alpha = 0.24f),
                AuroraTealCyan.copy(alpha = 0.44f)
            )
        )
    }

    val blurPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (fillBackground) Modifier.background(AuroraSpaceBg) else Modifier
            )
            .then(
                if (showGlassBorder) Modifier.border(width = 1.dp, brush = glassBorderBrush, shape = shape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Обязательный Offscreen-буфер для корректной работы BlendMode.Plus и BlurMaskFilter
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Когда открыты под-экраны CONSENT / API_KEY / OPERATIONS (maxAuroraHeight != null и !fillBackground),
            // не рисуем дублирующее свечение под нижними кнопками действий («Сохранить», «Разрешить ИИ»),
            // так как в режиме OPERATIONS уже светится сама 64.dp капсула прослушивания микрофона.
            if (maxAuroraHeight != null && !fillBackground) return@Canvas

            val maxCapPx = maxAuroraHeight?.toPx()?.coerceAtMost(h) ?: h

            // Деликатная высота второстепенного краевого свечения строго у нижней границы капсулы:
            // В тишине: ~9..11.dp у самой кромки; при речи: плавно поднимается до ~18.dp (не выше 24% высоты капсулы).
            val targetEdgeDp = if (isRecording) {
                (9.5f + breathPhase * 1.6f + smoothedVoiceLevel * 8.0f).dp
            } else {
                (7.0f + breathPhase * 1.4f).dp
            }
            val domeHeight = targetEdgeDp.toPx()
                .coerceAtMost(maxCapPx * 0.24f)
                .coerceAtMost(h * 0.24f)

            val orbitalX = sin(phase3300)
            val orbitalY = cos(phase3300)

            // Мягкая компактная подложка через native BlurMaskFilter в фирменном изумрудном цвете
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val blurRadius = (domeHeight * 0.48f).coerceIn(4.dp.toPx(), 11.dp.toPx())
                blurPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

                // Размытый базовый ореол Deep Emerald у самого дна капсулы
                val deepAlpha = (if (isRecording) (0.32f + smoothedVoiceLevel * 0.18f) else 0.20f) * safeIntensity
                blurPaint.color = AuroraDeepEmerald
                    .copy(alpha = deepAlpha.coerceAtMost(0.48f))
                    .toArgb()
                nativeCanvas.drawOval(
                    -w * 0.06f + phase2100 * w * 0.04f,
                    h - domeHeight * 0.45f,
                    w * 1.06f + phase2700 * w * 0.04f,
                    h + domeHeight * 0.88f,
                    blurPaint
                )

                // Тонкий размытый ореол дрейфующего изумруда вдоль нижней линии
                val midAlpha = (if (isRecording) (0.20f + smoothedVoiceLevel * 0.18f) else 0.12f) * safeIntensity
                blurPaint.color = AuroraEmerald
                    .copy(alpha = midAlpha.coerceAtMost(0.38f))
                    .toArgb()
                val emeraldCenterX = w * (0.5f + phase2100 * 0.18f)
                nativeCanvas.drawOval(
                    emeraldCenterX - w * 0.36f,
                    h - domeHeight * (0.34f + breathPhase * 0.06f),
                    emeraldCenterX + w * 0.36f,
                    h + domeHeight * 0.70f,
                    blurPaint
                )
            }

            // Слой 1 (Глубокая база): Мягкая лента Deep Emerald / Emerald вдоль нижней границы капсулы
            val baseCenterX = w * (0.50f + phase2100 * 0.07f + orbitalX * 0.03f)
            val baseCenterY = h + domeHeight * 0.36f
            val baseRadius = (w * 0.56f).coerceAtLeast(domeHeight * 2.2f)
            val baseScaleY = ((domeHeight * 0.88f) / baseRadius).coerceIn(0.04f, 0.34f)

            drawEllipticalAuroraCloud(
                center = Offset(baseCenterX, baseCenterY),
                radius = baseRadius,
                scaleX = 1.14f + breathPhase * 0.06f,
                scaleY = baseScaleY,
                colors = listOf(
                    AuroraDeepEmerald.copy(
                        alpha = ((if (isRecording) (0.46f + smoothedVoiceLevel * 0.18f) else 0.28f) * safeIntensity).coerceAtMost(0.56f)
                    ),
                    AuroraEmerald.copy(
                        alpha = ((if (isRecording) (0.30f + smoothedVoiceLevel * 0.14f) else 0.18f) * safeIntensity).coerceAtMost(0.42f)
                    ),
                    AuroraTealCyan.copy(alpha = 0.06f * safeIntensity),
                    Color.Transparent
                ),
                blendMode = BlendMode.Screen
            )

            // Слой 2 (Левый/Центральный краевой сгусток Emerald & NeonMint вдоль нижнего края)
            val plasma1CenterX = w * (0.34f + phase2100 * 0.18f + orbitalY * 0.05f)
            val plasma1CenterY = h + domeHeight * (0.28f - smoothedVoiceLevel * 0.05f)
            val plasma1Radius = (w * (0.38f + smoothedVoiceLevel * 0.08f)).coerceAtLeast(64f)
            val plasma1ScaleY = ((domeHeight * (0.72f + smoothedVoiceLevel * 0.12f)) / plasma1Radius).coerceIn(0.04f, 0.32f)

            drawEllipticalAuroraCloud(
                center = Offset(plasma1CenterX, plasma1CenterY),
                radius = plasma1Radius,
                scaleX = 1.10f + orbitalX * 0.10f,
                scaleY = plasma1ScaleY,
                colors = listOf(
                    AuroraEmerald.copy(
                        alpha = ((if (isRecording) (0.36f + smoothedVoiceLevel * 0.20f) else 0.20f) * safeIntensity).coerceAtMost(0.50f)
                    ),
                    AuroraNeonMint.copy(
                        alpha = ((if (isRecording) (0.22f + smoothedVoiceLevel * 0.16f) else 0.12f) * safeIntensity).coerceAtMost(0.36f)
                    ),
                    AuroraTealCyan.copy(
                        alpha = ((if (isRecording) (0.12f + smoothedVoiceLevel * 0.10f) else 0.06f) * safeIntensity).coerceAtMost(0.22f)
                    ),
                    Color.Transparent
                ),
                blendMode = BlendMode.Plus
            )

            // Слой 3 (Правый/Асимметричный краевой сгусток TealCyan вдоль нижнего края)
            val plasma2CenterX = w * (0.66f + phase2700 * 0.18f - orbitalX * 0.05f)
            val plasma2CenterY = h + domeHeight * (0.30f - smoothedVoiceLevel * 0.05f)
            val plasma2Radius = (w * (0.36f + smoothedVoiceLevel * 0.09f)).coerceAtLeast(60f)
            val plasma2ScaleY = ((domeHeight * (0.68f + smoothedVoiceLevel * 0.12f)) / plasma2Radius).coerceIn(0.04f, 0.32f)

            drawEllipticalAuroraCloud(
                center = Offset(plasma2CenterX, plasma2CenterY),
                radius = plasma2Radius,
                scaleX = 1.10f - orbitalY * 0.10f,
                scaleY = plasma2ScaleY,
                colors = listOf(
                    AuroraTealCyan.copy(
                        alpha = ((if (isRecording) (0.34f + smoothedVoiceLevel * 0.18f) else 0.18f) * safeIntensity).coerceAtMost(0.46f)
                    ),
                    AuroraEmerald.copy(
                        alpha = ((if (isRecording) (0.24f + smoothedVoiceLevel * 0.14f) else 0.12f) * safeIntensity).coerceAtMost(0.36f)
                    ),
                    AuroraSubtleIndigo.copy(
                        alpha = ((if (isRecording) (0.08f + smoothedVoiceLevel * 0.08f) else 0.04f) * safeIntensity).coerceAtMost(0.18f)
                    ),
                    Color.Transparent
                ),
                blendMode = BlendMode.Plus
            )

            // Слой 4 (Тонкая изумрудно-мятная кромка по самому краю снизу)
            val coreCenterX = w * (0.50f + (phase2100 + phase2700) * 0.06f)
            val coreCenterY = h + domeHeight * 0.32f
            val coreRadius = (w * (0.34f + smoothedVoiceLevel * 0.10f)).coerceAtLeast(56f)
            val coreScaleY = ((domeHeight * (0.40f + smoothedVoiceLevel * 0.10f)) / coreRadius).coerceIn(0.04f, 0.22f)
            val coreIntensity = (if (isRecording) {
                (0.30f + breathPhase * 0.06f + smoothedVoiceLevel * 0.26f).coerceIn(0.26f, 0.62f)
            } else {
                0.18f + breathPhase * 0.05f
            }) * safeIntensity

            drawEllipticalAuroraCloud(
                center = Offset(coreCenterX, coreCenterY),
                radius = coreRadius,
                scaleX = 1.06f + smoothedVoiceLevel * 0.12f,
                scaleY = coreScaleY,
                colors = listOf(
                    AuroraCoreMint.copy(alpha = (0.44f * coreIntensity).coerceAtMost(0.45f)),
                    AuroraNeonMint.copy(alpha = (0.34f * coreIntensity).coerceAtMost(0.36f)),
                    AuroraDeepEmerald.copy(alpha = (0.14f * coreIntensity).coerceAtMost(0.20f)),
                    Color.Transparent
                ),
                blendMode = BlendMode.Plus
            )
        }

        content()
    }
}

private fun DrawScope.drawEllipticalAuroraCloud(
    center: Offset,
    radius: Float,
    scaleX: Float,
    scaleY: Float,
    colors: List<Color>,
    blendMode: BlendMode
) {
    if (radius <= 1f) return
    withTransform({
        scale(scaleX = scaleX, scaleY = scaleY, pivot = center)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = colors,
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center,
            blendMode = blendMode
        )
    }
}

/**
 * Обертка обратной совместимости для капсул с объемным неоновым свечением Gemini Live.
 */
@Composable
fun MovingNeonGlow(
    isRecording: Boolean,
    amplitude: Float,
    widthDp: Float,
    heightDp: Float,
    cornerRadiusDp: Float = 36f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    VolumetricLiquidAuroraGlow(
        isRecording = isRecording,
        rmsLevel = amplitude,
        cornerRadius = cornerRadiusDp.dp,
        modifier = modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
    ) {
        content()
    }
}

/**
 * Компактный визуализатор объемного северного сияния Gemini Live (Volumetric Liquid Aurora),
 * используемый внутри карточек и панелей распознавания речи.
 */
@Composable
fun MovingNeonGlowVisualizer(
    isRecording: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    VolumetricLiquidAuroraGlow(
        isRecording = isRecording,
        rmsLevel = rmsLevel,
        cornerRadius = 24.dp,
        showGlassBorder = true,
        fillBackground = true,
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    )
}
