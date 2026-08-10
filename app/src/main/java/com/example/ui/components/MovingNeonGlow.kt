package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import kotlin.math.sin

@Composable
fun MovingNeonGlow(
    isRecording: Boolean,
    amplitude: Float,
    widthDp: Float,
    heightDp: Float,
    cornerRadiusDp: Float = 28f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_rotation_orbit")

    // Continuous 360-degree rotation angle for the sweep gradient
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    // Breathing wave phase for smooth continuous motion when silent
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Ensure active amplitude is dynamic even in silence (0.25f..0.50f breathing wave, up to 1.0f on voice)
    val idlePulse = (sin(wavePhase.toDouble()).toFloat() * 0.15f + 0.35f)
    val activeAmp = if (isRecording) {
        (amplitude * 0.7f + idlePulse * 0.3f).coerceIn(0.25f, 1f)
    } else 0f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Canvas(
                modifier = Modifier
                    .width((widthDp + 48f).dp)
                    .height((heightDp + 48f).dp)
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // Cyberpunk Neon Sweep Gradient (Emerald -> Indigo -> Rose -> Emerald)
                val neonBrush = Brush.sweepGradient(
                    colors = listOf(
                        Emerald400,
                        Indigo500,
                        Rose500,
                        Emerald400,
                        Indigo500,
                        Emerald400
                    ),
                    center = center
                )

                val capsuleWidth = widthDp.dp.toPx()
                val capsuleHeight = heightDp.dp.toPx()
                val left = (size.width - capsuleWidth) / 2f
                val top = (size.height - capsuleHeight) / 2f

                rotate(degrees = rotationAngle, pivot = center) {
                    // 1. Wide Outer Glow Aura (Soft Bloom)
                    val outerGlowWidth = 18f + activeAmp * 18f
                    drawRoundRect(
                        brush = neonBrush,
                        topLeft = Offset(left, top),
                        size = Size(capsuleWidth, capsuleHeight),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                        style = Stroke(width = outerGlowWidth),
                        alpha = 0.35f + activeAmp * 0.40f
                    )

                    // 2. Medium Intense Neon Halo
                    val midGlowWidth = 8f + activeAmp * 10f
                    drawRoundRect(
                        brush = neonBrush,
                        topLeft = Offset(left, top),
                        size = Size(capsuleWidth, capsuleHeight),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                        style = Stroke(width = midGlowWidth),
                        alpha = 0.65f + activeAmp * 0.30f
                    )

                    // 3. Crisp Core Neon Line (100% Connected Unbroken Contour)
                    val coreGlowWidth = 3f + activeAmp * 3f
                    drawRoundRect(
                        brush = neonBrush,
                        topLeft = Offset(left, top),
                        size = Size(capsuleWidth, capsuleHeight),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                        style = Stroke(width = coreGlowWidth),
                        alpha = 0.98f
                    )

                    // 4. Ultra-Bright White Accent Core
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.40f + activeAmp * 0.40f),
                        topLeft = Offset(left, top),
                        size = Size(capsuleWidth, capsuleHeight),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }

        content()
    }
}

