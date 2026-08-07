package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val NeonRose = Color(0xFFF43F5E)
val NeonPurple = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF00F2FE)
val NeonEmerald = Color(0xFF10B981)

@Composable
fun NeonCircularProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 5.dp,
    speedMultiplier: Float = 3.0f,
    glowRadius: Dp = 2.dp
) {
    val outerDurationMillis = (2200 / speedMultiplier).toInt() // 733 ms
    val innerDurationMillis = (1600 / speedMultiplier).toInt() // 533 ms

    val infiniteTransition = rememberInfiniteTransition(label = "loader_transition")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(outerDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_rotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(innerDurationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_rotation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Мягкий неоновый ореол (Glow Backplane)
        if (glowRadius > 0.dp) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(glowRadius)
                    .alpha(0.75f)
            ) {
                val outerStrokePx = strokeWidth.toPx() * 1.5f
                val innerStrokePx = strokeWidth.toPx() * 1.2f
                val diameter = size.toPx()

                rotate(outerRotation) {
                    drawArc(
                        brush = Brush.sweepGradient(listOf(NeonRose, NeonPurple, NeonCyan, NeonEmerald, NeonRose)),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = outerStrokePx, cap = StrokeCap.Round)
                    )
                }

                rotate(innerRotation) {
                    val inset = strokeWidth.toPx() * 2.8f
                    drawArc(
                        brush = Brush.sweepGradient(listOf(NeonEmerald, NeonCyan, NeonPurple, NeonRose, NeonEmerald)),
                        startAngle = 0f,
                        sweepAngle = 210f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(diameter - inset * 2, diameter - inset * 2),
                        style = Stroke(width = innerStrokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Четкие кольца на переднем плане
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerStrokePx = strokeWidth.toPx()
            val innerStrokePx = strokeWidth.toPx() * 0.8f
            val diameter = size.toPx()

            rotate(outerRotation) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(NeonRose, NeonPurple, NeonCyan, NeonEmerald, NeonRose)),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = outerStrokePx, cap = StrokeCap.Round)
                )
            }

            rotate(innerRotation) {
                val inset = strokeWidth.toPx() * 2.8f
                drawArc(
                    brush = Brush.sweepGradient(listOf(NeonEmerald, NeonCyan, NeonPurple, NeonRose, NeonEmerald)),
                    startAngle = 0f,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter - inset * 2, diameter - inset * 2),
                    style = Stroke(width = innerStrokePx, cap = StrokeCap.Round)
                )
            }
        }
    }
}
