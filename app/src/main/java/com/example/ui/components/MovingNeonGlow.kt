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

// Палитра Gemini Live Volumetric Liquid Aurora (компактное краевое свечение по низу капсулы)
private val AuroraSpaceBg = Color(0xFF080C14)
private val AuroraRoyalBlue = Color(0xFF1D4ED8)
private val AuroraDeepIndigo = Color(0xFF4F46E5)
private val AuroraCyan = Color(0xFF00E5FF)
private val AuroraSkyBlue = Color(0xFF38BDF8)
private val AuroraViolet = Color(0xFF818CF8)
private val AuroraEmeraldHint = Color(0xFF10B981)
private val AuroraCoreIce = Color(0xFFE0F2FE)

/**
 * Компактная жидкая неоновая анимация в стиле Gemini Live (Volumetric Liquid Aurora Edge Glow).
 * Формирует аккуратное многослойное свечение строго вдоль нижней кромки капсулы без засветки
 * текста и кнопок, плавно откликаясь на голос увеличением высоты и яркости нижнего ореола.
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

    // Пружинное сглаживание уровня голоса
    val smoothedVoiceLevel by animateFloatAsState(
        targetValue = rawNormalized,
        animationSpec = spring(
            dampingRatio = 0.55f,
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
                Color.White.copy(alpha = 0.16f),
                AuroraSkyBlue.copy(alpha = 0.42f)
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

            val maxCapPx = maxAuroraHeight?.toPx()?.coerceAtMost(h) ?: h

            // Компактная высота краевого свечения строго у нижней границы:
            // В покое (диалоги согласия / ключа / пауза): 10..13.dp у самой кромки.
            // При записи голоса: от 15.dp в тишине до 28.dp на пике речи (не выше 34% высоты капсулы).
            val targetEdgeDp = if (isRecording) {
                (14f + breathPhase * 2f + smoothedVoiceLevel * 14f).dp
            } else {
                (9.5f + breathPhase * 2.5f).dp
            }
            val domeHeight = targetEdgeDp.toPx()
                .coerceAtMost(maxCapPx * 0.34f)
                .coerceAtMost(h * 0.34f)

            val orbitalX = sin(phase3300)
            val orbitalY = cos(phase3300)

            // Мягкая компактная подложка через native BlurMaskFilter строго вдоль нижней кромки
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val blurRadius = (domeHeight * 0.55f).coerceIn(6.dp.toPx(), 15.dp.toPx())
                blurPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

                // Размытый базовый ореол Royal Blue у самого дна капсулы
                blurPaint.color = AuroraRoyalBlue
                    .copy(alpha = if (isRecording) (0.34f + smoothedVoiceLevel * 0.22f).coerceAtMost(0.60f) else 0.24f)
                    .toArgb()
                nativeCanvas.drawOval(
                    -w * 0.08f + phase2100 * w * 0.05f,
                    h - domeHeight * 0.55f,
                    w * 1.08f + phase2700 * w * 0.05f,
                    h + domeHeight * 0.90f,
                    blurPaint
                )

                // Тонкий размытый ореол дрейфующего циана вдоль нижней линии
                blurPaint.color = AuroraSkyBlue
                    .copy(alpha = if (isRecording) (0.22f + smoothedVoiceLevel * 0.24f).coerceAtMost(0.50f) else 0.16f)
                    .toArgb()
                val cyanCenterX = w * (0.5f + phase2100 * 0.20f)
                nativeCanvas.drawOval(
                    cyanCenterX - w * 0.38f,
                    h - domeHeight * (0.42f + breathPhase * 0.08f),
                    cyanCenterX + w * 0.38f,
                    h + domeHeight * 0.70f,
                    blurPaint
                )
            }

            // Слой 1 (Глубокая база): Мягкая лента Deep Indigo / Royal Blue вдоль нижней границы капсулы
            val baseCenterX = w * (0.50f + phase2100 * 0.08f + orbitalX * 0.04f)
            val baseCenterY = h + domeHeight * 0.32f
            val baseRadius = (w * 0.58f).coerceAtLeast(domeHeight * 2.2f)
            val baseScaleY = ((domeHeight * 0.95f) / baseRadius).coerceIn(0.06f, 0.45f)

            drawEllipticalAuroraCloud(
                center = Offset(baseCenterX, baseCenterY),
                radius = baseRadius,
                scaleX = 1.18f + breathPhase * 0.08f,
                scaleY = baseScaleY,
                colors = listOf(
                    AuroraDeepIndigo.copy(alpha = if (isRecording) (0.52f + smoothedVoiceLevel * 0.22f).coerceAtMost(0.78f) else 0.36f),
                    AuroraRoyalBlue.copy(alpha = if (isRecording) (0.38f + smoothedVoiceLevel * 0.18f).coerceAtMost(0.62f) else 0.24f),
                    AuroraRoyalBlue.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                blendMode = BlendMode.Screen
            )

            // Слой 2 (Левый/Центральный краевой сгусток Cyan & Emerald вдоль нижнего края)
            val plasma1CenterX = w * (0.32f + phase2100 * 0.22f + orbitalY * 0.06f)
            val plasma1CenterY = h + domeHeight * (0.24f - smoothedVoiceLevel * 0.08f)
            val plasma1Radius = (w * (0.40f + smoothedVoiceLevel * 0.10f)).coerceAtLeast(70f)
            val plasma1ScaleY = ((domeHeight * (0.82f + smoothedVoiceLevel * 0.18f)) / plasma1Radius).coerceIn(0.06f, 0.42f)

            drawEllipticalAuroraCloud(
                center = Offset(plasma1CenterX, plasma1CenterY),
                radius = plasma1Radius,
                scaleX = 1.12f + orbitalX * 0.12f,
                scaleY = plasma1ScaleY,
                colors = listOf(
                    AuroraCyan.copy(alpha = if (isRecording) (0.42f + smoothedVoiceLevel * 0.26f).coerceAtMost(0.72f) else 0.26f),
                    AuroraSkyBlue.copy(alpha = if (isRecording) (0.28f + smoothedVoiceLevel * 0.20f).coerceAtMost(0.54f) else 0.16f),
                    AuroraEmeraldHint.copy(alpha = if (isRecording) (0.14f + smoothedVoiceLevel * 0.14f).coerceAtMost(0.32f) else 0.08f),
                    Color.Transparent
                ),
                blendMode = BlendMode.Plus
            )

            // Слой 3 (Правый/Асимметричный краевой сгусток SkyBlue & Violet вдоль нижнего края)
            val plasma2CenterX = w * (0.68f + phase2700 * 0.22f - orbitalX * 0.07f)
            val plasma2CenterY = h + domeHeight * (0.26f - smoothedVoiceLevel * 0.08f)
            val plasma2Radius = (w * (0.38f + smoothedVoiceLevel * 0.12f)).coerceAtLeast(65f)
            val plasma2ScaleY = ((domeHeight * (0.78f + smoothedVoiceLevel * 0.18f)) / plasma2Radius).coerceIn(0.06f, 0.42f)

            drawEllipticalAuroraCloud(
                center = Offset(plasma2CenterX, plasma2CenterY),
                radius = plasma2Radius,
                scaleX = 1.14f - orbitalY * 0.12f,
                scaleY = plasma2ScaleY,
                colors = listOf(
                    AuroraSkyBlue.copy(alpha = if (isRecording) (0.40f + smoothedVoiceLevel * 0.24f).coerceAtMost(0.68f) else 0.24f),
                    AuroraViolet.copy(alpha = if (isRecording) (0.32f + smoothedVoiceLevel * 0.20f).coerceAtMost(0.56f) else 0.18f),
                    AuroraCyan.copy(alpha = if (isRecording) (0.14f + smoothedVoiceLevel * 0.14f).coerceAtMost(0.32f) else 0.08f),
                    Color.Transparent
                ),
                blendMode = BlendMode.Plus
            )

            // Слой 4 (Тонкая неоновая кромка по самому краю снизу без белой засветки контента)
            val coreCenterX = w * (0.50f + (phase2100 + phase2700) * 0.08f)
            val coreCenterY = h + domeHeight * 0.28f
            val coreRadius = (w * (0.36f + smoothedVoiceLevel * 0.14f)).coerceAtLeast(60f)
            val coreScaleY = ((domeHeight * (0.48f + smoothedVoiceLevel * 0.16f)) / coreRadius).coerceIn(0.04f, 0.26f)
            val coreIntensity = if (isRecording) {
                (0.38f + breathPhase * 0.08f + smoothedVoiceLevel * 0.34f).coerceIn(0.35f, 0.80f)
            } else {
                0.24f + breathPhase * 0.06f
            }

            drawEllipticalAuroraCloud(
                center = Offset(coreCenterX, coreCenterY),
                radius = coreRadius,
                scaleX = 1.08f + smoothedVoiceLevel * 0.15f,
                scaleY = coreScaleY,
                colors = listOf(
                    AuroraCoreIce.copy(alpha = (0.58f * coreIntensity).coerceAtMost(0.65f)),
                    AuroraCyan.copy(alpha = (0.42f * coreIntensity).coerceAtMost(0.52f)),
                    AuroraRoyalBlue.copy(alpha = (0.18f * coreIntensity).coerceAtMost(0.28f)),
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
