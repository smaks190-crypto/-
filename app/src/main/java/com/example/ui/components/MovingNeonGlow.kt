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

// Палитра Gemini Live Volumetric Liquid Aurora
private val AuroraSpaceBg = Color(0xFF080C14)
private val AuroraRoyalBlue = Color(0xFF1D4ED8)
private val AuroraDeepIndigo = Color(0xFF4F46E5)
private val AuroraCyan = Color(0xFF00E5FF)
private val AuroraSkyBlue = Color(0xFF38BDF8)
private val AuroraViolet = Color(0xFF818CF8)
private val AuroraEmeraldHint = Color(0xFF10B981)
private val AuroraCoreIce = Color(0xFFE0F2FE)
private val AuroraCoreWhite = Color(0xFFFFFFFF)

/**
 * Объемная жидкая неоновая анимация в стиле Gemini Live (Volumetric Liquid Aurora Glow).
 * Заполняет капсулу мягкими переливающимися облаками света из 4 слоев (Deep Indigo/Royal Blue база,
 * два асимметричных дрейфующих плазменных облака Cyan/SkyBlue/Violet/Emerald и яркое бело-голубое ядро внизу)
 * с физикой дыхания и пружинным откликом на голос.
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
            0.08f
        } else if (rmsLevel <= 1.05f && rmsLevel >= 0f) {
            rmsLevel.coerceIn(0f, 1f)
        } else {
            ((rmsLevel - 1.2f) / 9.2f).coerceIn(0f, 1f)
        }
    }

    // Пружинное сглаживание уровня голоса по ТЗ: spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val smoothedVoiceLevel by animateFloatAsState(
        targetValue = rawNormalized,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "aurora_voice_spring"
    )

    // Непрерывное «дыхание» (Idle Breathing) с 4 независимыми несинхронными фазами (2100ms, 2700ms, 3300ms, 2400ms)
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
                Color.White.copy(alpha = 0.22f),
                AuroraSkyBlue.copy(alpha = 0.45f)
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

            // Ограничиваем эффективную высоту сияния, если капсула выросла вверх (например, при показе распознанных операций),
            // чтобы сияние красиво переливалось в нижней части высокой капсулы или заполняло всю компактную капсулу (84.dp)
            val effectiveAuroraH = maxAuroraHeight?.toPx()?.coerceAtMost(h) ?: h

            // В тишине сияние мягко дышит в нижних ~45% капсулы, а во время речи купол света упруго поднимается на 75–95% высоты
            val idleReach = 0.43f + breathPhase * 0.05f // ~43..48% в покое
            val activeReach = (idleReach + smoothedVoiceLevel * 0.50f).coerceIn(0.42f, 0.95f)
            val domeHeight = effectiveAuroraH * activeReach

            val orbitalX = sin(phase3300)
            val orbitalY = cos(phase3300)

            // Мягкая подложка через native BlurMaskFilter для глубокого атмосферного рассеивания
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val blurRadius = (effectiveAuroraH * (0.38f + smoothedVoiceLevel * 0.22f)).coerceAtLeast(24f)
                blurPaint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)

                // Размытый базовый ореол Royal Blue у дна капсулы
                blurPaint.color = AuroraRoyalBlue
                    .copy(alpha = (0.48f + smoothedVoiceLevel * 0.28f).coerceIn(0f, 0.85f))
                    .toArgb()
                nativeCanvas.drawOval(
                    -w * 0.15f + phase2100 * w * 0.08f,
                    h - domeHeight * 1.05f,
                    w * 1.15f + phase2700 * w * 0.08f,
                    h + effectiveAuroraH * 0.45f,
                    blurPaint
                )

                // Размытый ореол дрейфующего циана/индиго
                blurPaint.color = AuroraSkyBlue
                    .copy(alpha = (0.32f + smoothedVoiceLevel * 0.35f).coerceIn(0f, 0.8f))
                    .toArgb()
                val cyanCenterX = w * (0.5f + phase2100 * 0.22f)
                nativeCanvas.drawOval(
                    cyanCenterX - w * 0.45f,
                    h - domeHeight * (0.92f + breathPhase * 0.08f),
                    cyanCenterX + w * 0.45f,
                    h + effectiveAuroraH * 0.25f,
                    blurPaint
                )
            }

            // Слой 1 (Глубокая база): Крупное мягкое облако Deep Indigo / Royal Blue вдоль нижней границы капсулы
            val baseCenterX = w * (0.50f + phase2100 * 0.09f + orbitalX * 0.05f)
            val baseCenterY = h + effectiveAuroraH * (0.10f - smoothedVoiceLevel * 0.14f)
            val baseRadius = (w * 0.68f).coerceAtLeast(domeHeight * 1.25f)
            val baseScaleY = ((domeHeight * 1.18f) / baseRadius).coerceIn(0.28f, 1.45f)

            drawEllipticalAuroraCloud(
                center = Offset(baseCenterX, baseCenterY),
                radius = baseRadius,
                scaleX = 1.25f + breathPhase * 0.12f,
                scaleY = baseScaleY,
                colors = listOf(
                    AuroraDeepIndigo.copy(alpha = (0.78f + smoothedVoiceLevel * 0.20f).coerceAtMost(0.98f)),
                    AuroraRoyalBlue.copy(alpha = (0.62f + smoothedVoiceLevel * 0.24f).coerceAtMost(0.92f)),
                    AuroraRoyalBlue.copy(alpha = 0.18f),
                    Color.Transparent
                ),
                blendMode = BlendMode.Screen
            )

            // Слой 2 (Дрейфующая плазма — Левый/Центральный сгусток Cyan & SkyBlue с легким изумрудным оттенком)
            val plasma1CenterX = w * (0.34f + phase2100 * 0.24f + orbitalY * 0.08f)
            val plasma1CenterY = h - domeHeight * (0.12f + smoothedVoiceLevel * 0.22f + breathPhase * 0.06f)
            val plasma1Radius = (w * (0.48f + smoothedVoiceLevel * 0.16f)).coerceAtLeast(90f)
            val plasma1ScaleY = ((domeHeight * (0.95f + smoothedVoiceLevel * 0.25f)) / plasma1Radius).coerceIn(0.30f, 1.55f)

            drawEllipticalAuroraCloud(
                center = Offset(plasma1CenterX, plasma1CenterY),
                radius = plasma1Radius,
                scaleX = 1.15f + orbitalX * 0.18f,
                scaleY = plasma1ScaleY,
                colors = listOf(
                    AuroraCyan.copy(alpha = (0.64f + smoothedVoiceLevel * 0.32f).coerceAtMost(0.96f)),
                    AuroraSkyBlue.copy(alpha = (0.48f + smoothedVoiceLevel * 0.28f).coerceAtMost(0.88f)),
                    AuroraEmeraldHint.copy(alpha = (0.20f + smoothedVoiceLevel * 0.18f).coerceAtMost(0.45f)),
                    Color.Transparent
                ),
                blendMode = BlendMode.Plus
            )

            // Слой 3 (Дрейфующая плазма — Правый/Асимметричный сгусток SkyBlue & Violet/Indigo)
            val plasma2CenterX = w * (0.66f + phase2700 * 0.24f - orbitalX * 0.09f)
            val plasma2CenterY = h - domeHeight * (0.10f + smoothedVoiceLevel * 0.24f + (1f - breathPhase) * 0.07f)
            val plasma2Radius = (w * (0.46f + smoothedVoiceLevel * 0.18f)).coerceAtLeast(85f)
            val plasma2ScaleY = ((domeHeight * (0.92f + smoothedVoiceLevel * 0.28f)) / plasma2Radius).coerceIn(0.30f, 1.55f)

            drawEllipticalAuroraCloud(
                center = Offset(plasma2CenterX, plasma2CenterY),
                radius = plasma2Radius,
                scaleX = 1.18f - orbitalY * 0.16f,
                scaleY = plasma2ScaleY,
                colors = listOf(
                    AuroraSkyBlue.copy(alpha = (0.62f + smoothedVoiceLevel * 0.30f).coerceAtMost(0.95f)),
                    AuroraViolet.copy(alpha = (0.52f + smoothedVoiceLevel * 0.26f).coerceAtMost(0.86f)),
                    AuroraCyan.copy(alpha = (0.24f + smoothedVoiceLevel * 0.20f).coerceAtMost(0.55f)),
                    Color.Transparent
                ),
                blendMode = BlendMode.Plus
            )

            // Слой 4 (Световое ядро — Core Highlight): Яркое бело-голубое свечение у самого низа по центру капсулы
            val coreCenterX = w * (0.50f + (phase2100 + phase2700) * 0.06f)
            val coreCenterY = h + effectiveAuroraH * (0.04f - smoothedVoiceLevel * 0.09f)
            val coreRadius = (w * (0.42f + smoothedVoiceLevel * 0.20f)).coerceAtLeast(75f)
            val coreScaleY = ((domeHeight * (0.64f + smoothedVoiceLevel * 0.34f)) / coreRadius).coerceIn(0.24f, 1.25f)
            val coreIntensity = (0.62f + breathPhase * 0.12f + smoothedVoiceLevel * 0.38f).coerceIn(0.55f, 1f)

            drawEllipticalAuroraCloud(
                center = Offset(coreCenterX, coreCenterY),
                radius = coreRadius,
                scaleX = 1.10f + smoothedVoiceLevel * 0.20f,
                scaleY = coreScaleY,
                colors = listOf(
                    AuroraCoreWhite.copy(alpha = (0.92f * coreIntensity).coerceAtMost(1f)),
                    AuroraCoreIce.copy(alpha = (0.76f * coreIntensity).coerceAtMost(0.95f)),
                    AuroraCyan.copy(alpha = (0.42f * coreIntensity).coerceAtMost(0.75f)),
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
