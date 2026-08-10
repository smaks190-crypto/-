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
    val infiniteTransition = rememberInfiniteTransition(label = "neon_orbit")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val activeAmp = if (isRecording) {
        (amplitude + (sin(wavePhase.toDouble()).toFloat() * 0.15f + 0.15f)).coerceIn(0.15f, 1f)
    } else 0f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Canvas(
                modifier = Modifier
                    .width((widthDp + (activeAmp * 28f)).dp)
                    .height((heightDp + (activeAmp * 28f)).dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val center = Offset(canvasWidth / 2f, canvasHeight / 2f)

                val neonBrush = Brush.sweepGradient(
                    colors = listOf(
                        Emerald400,
                        Indigo500,
                        Rose500,
                        Emerald400
                    ),
                    center = center
                )

                rotate(degrees = rotationAngle, pivot = center) {
                    val outerStrokeWidth = (14f + activeAmp * 18f)
                    
                    drawRoundRect(
                        brush = neonBrush,
                        topLeft = Offset(outerStrokeWidth / 2f, outerStrokeWidth / 2f),
                        size = Size(canvasWidth - outerStrokeWidth, canvasHeight - outerStrokeWidth),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx() + outerStrokeWidth),
                        style = Stroke(width = outerStrokeWidth),
                        alpha = 0.22f + activeAmp * 0.35f
                    )

                    val midStrokeWidth = (7f + activeAmp * 9f)
                    drawRoundRect(
                        brush = neonBrush,
                        topLeft = Offset(outerStrokeWidth - midStrokeWidth / 2f, outerStrokeWidth - midStrokeWidth / 2f),
                        size = Size(canvasWidth - (outerStrokeWidth * 2 - midStrokeWidth), canvasHeight - (outerStrokeWidth * 2 - midStrokeWidth)),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                        style = Stroke(width = midStrokeWidth),
                        alpha = 0.55f + activeAmp * 0.3f
                    )

                    val innerStrokeWidth = (2.5f + activeAmp * 3f)
                    drawRoundRect(
                        brush = neonBrush,
                        topLeft = Offset(outerStrokeWidth, outerStrokeWidth),
                        size = Size(canvasWidth - outerStrokeWidth * 2, canvasHeight - outerStrokeWidth * 2),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx()),
                        style = Stroke(width = innerStrokeWidth),
                        alpha = 0.95f
                    )
                }
            }
        }

        content()
    }
}
