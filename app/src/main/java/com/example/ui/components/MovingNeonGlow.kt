package com.example.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val ObsidianSurface = Color(0xFF0A0E17)
val DeepObsidian = Color(0xFF05070B)
val NeonCyan = Color(0xFF00F2FE)
val ElectricEmerald = Color(0xFF10B981)
val CyberViolet = Color(0xFF8B5CF6)
val MagentaPulse = Color(0xFFEC4899)
valMintHighlight = Color(0xFF6EE7B7)

/**
 * 2026 Flagship Volumetric Liquid Aurora Glow.
 *
 * When paired with the 32-band `SpectralVoiceVisualizer`, `auroraIntensityScale` (default `0.55f` in recording
 * capsules) keeps the bottom-edge Aurora glow delicate and secondary so the 32 spectral bars remain the primary
 * visual focus while preserving the signature emerald/cyan bottom reflection from the reference design.
 */
@Composable
fun VolumetricLiquidAuroraGlow(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    audioLevel: Float = 0f,
    isRecording: Boolean = false,
    auroraIntensityScale: Float = 1.0f,
    backgroundColor: Color = ObsidianSurface,
    content: @Composable () -> Unit
) {
    val safeAudio = if (audioLevel.isNaN() || audioLevel.isInfinite()) 0f else audioLevel.coerceIn(0f, 1f)
    val safeScale = if (auroraIntensityScale.isNaN() || auroraIntensityScale.isInfinite()) 1f else auroraIntensityScale.coerceIn(0.15f, 1.2f)

    val animatedAudio by animateFloatAsState(
        targetValue = if (isRecording) safeAudio else 0f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "AuroraAudioLevel"
    )

    val activeMultiplier by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0.35f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "AuroraActiveState"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "LiquidAuroraPhase")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = (2f * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Phase2"
    )

    val borderSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BorderSweep"
    )

    val shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)

    androidx.compose.foundation.layout.Box(
        modifier = modifier.clip(shape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cr = cornerRadius.toPx()

            // 1. Deep Obsidian base
            drawRoundRect(
                color = backgroundColor,
                size = size,
                cornerRadius = CornerRadius(cr, cr)
            )

            // 2. Secondary bottom-edge Emerald & Cyan Aurora reflection (focused along the lower rim)
            val blobRadiusBase = maxOf(w, h) * (0.38f + animatedAudio * 0.24f)
            val bottomAnchorY = h * 0.92f

            val blob1Center = Offset(
                x = w * (0.34f + 0.18f * cos(phase1)),
                y = bottomAnchorY + h * 0.06f * sin(phase1)
            )
            val blob2Center = Offset(
                x = w * (0.52f + 0.20f * sin(phase2)),
                y = bottomAnchorY + h * 0.05f * cos(phase2)
            )
            val blob3Center = Offset(
                x = w * (0.68f + 0.16f * cos(phase1 * 0.7f + phase2 * 0.3f)),
                y = bottomAnchorY + h * 0.05f * sin(phase2)
            )

            val alphaScale = (activeMultiplier * (0.28f + animatedAudio * 0.42f) * safeScale).coerceIn(0.05f, 0.75f)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricEmerald.copy(alpha = alphaScale * 0.95f),
                        Color.Transparent
                    ),
                    center = blob1Center,
                    radius = blobRadiusBase * 0.85f
                ),
                radius = blobRadiusBase * 0.85f,
                center = blob1Center,
                blendMode = BlendMode.Plus
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeonCyan.copy(alpha = alphaScale * 0.80f),
                        Color.Transparent
                    ),
                    center = blob2Center,
                    radius = blobRadiusBase * 0.90f
                ),
                radius = blobRadiusBase * 0.90f,
                center = blob2Center,
                blendMode = BlendMode.Plus
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF06B6D4).copy(alpha = alphaScale * 0.65f),
                        Color.Transparent
                    ),
                    center = blob3Center,
                    radius = blobRadiusBase * 0.75f
                ),
                radius = blobRadiusBase * 0.75f,
                center = blob3Center,
                blendMode = BlendMode.Plus
            )

            // 3. Top dark vignette to keep upper part of capsule crisp for the 32 spectral bars
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepObsidian.copy(alpha = 0.86f),
                        ObsidianSurface.copy(alpha = 0.52f),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.88f
                ),
                size = size,
                cornerRadius = CornerRadius(cr, cr)
            )

            // 4. Delicate bottom-edge luminous horizon line matching the reference capsule
            val bottomHorizonAlpha = (0.32f + animatedAudio * 0.45f) * safeScale
            drawLine(
                brush = Brush.horizontalGradient(
                    0.00f to Color.Transparent,
                    0.22f to ElectricEmerald.copy(alpha = bottomHorizonAlpha * 0.85f),
                    0.50f to valMintHighlight.copy(alpha = bottomHorizonAlpha),
                    0.75f to NeonCyan.copy(alpha = bottomHorizonAlpha * 0.85f),
                    1.00f to Color.Transparent
                ),
                start = Offset(w * 0.12f, h - 1.5.dp.toPx()),
                end = Offset(w * 0.88f, h - 1.5.dp.toPx()),
                strokeWidth = (2.2.dp.toPx() + animatedAudio * 1.5.dp.toPx())
            )

            // 5. Crisp 1.2dp Specular Rim Stroke
            val rad = borderSweepAngle * (PI / 180.0)
            val gradStart = Offset(
                x = w * 0.5f + w * 0.5f * cos(rad).toFloat(),
                y = h * 0.5f + h * 0.5f * sin(rad).toFloat()
            )
            val gradEnd = Offset(
                x = w * 0.5f - w * 0.5f * cos(rad).toFloat(),
                y = h * 0.5f - h * 0.5f * sin(rad).toFloat()
            )

            val rimAlpha = (0.28f + animatedAudio * 0.35f) * safeScale.coerceAtLeast(0.6f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ElectricEmerald.copy(alpha = rimAlpha),
                        NeonCyan.copy(alpha = rimAlpha * 0.9f),
                        Color(0xFF1E293B).copy(alpha = 0.55f),
                        ElectricEmerald.copy(alpha = rimAlpha * 0.8f)
                    ),
                    start = gradStart,
                    end = gradEnd
                ),
                size = size,
                cornerRadius = CornerRadius(cr, cr),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        content()
    }
}

@Composable
fun MovingNeonGlow(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    glowWidth: Dp = 3.dp,
    blurRadius: Dp = 14.dp,
    durationMillis: Int = 3500,
    audioLevel: Float = 0f,
    backgroundColor: Color = ObsidianSurface,
    colors: List<Color> = listOf(NeonCyan, ElectricEmerald, CyberViolet, NeonCyan),
    content: @Composable () -> Unit
) {
    VolumetricLiquidAuroraGlow(
        modifier = modifier,
        cornerRadius = cornerRadius,
        audioLevel = audioLevel,
        isRecording = audioLevel > 0.01f,
        backgroundColor = backgroundColor,
        content = content
    )
}