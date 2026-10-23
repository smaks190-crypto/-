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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val infiniteTransition = rememberInfiniteTransition(label = "neon_perimeter_orbit")
    
    // Smooth progress 0f..1f travelling around the capsule perimeter
    val progressState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "perimeter_progress"
    )

    val wavePhaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val pathMeasure = remember { PathMeasure() }
    val capsulePath = remember { Path() }
    val segmentPath1 = remember { Path() }
    val segmentPath2 = remember { Path() }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Canvas(
                modifier = Modifier
                    .width((widthDp + 36f).dp)
                    .height((heightDp + 36f).dp)
            ) {
                // Read animation state inside DRAW scope only, preventing full composable recomposition!
                val progress = progressState.value
                val wavePhase = wavePhaseState.value
                val activeAmp = (amplitude + (sin(wavePhase.toDouble()).toFloat() * 0.15f + 0.15f)).coerceIn(0.15f, 1f)

                val outerStrokeWidth = 14f + activeAmp * 16f
                val midStrokeWidth = 6f + activeAmp * 8f
                val coreStrokeWidth = 2.5f + activeAmp * 2.5f

                val rectLeft = (size.width - widthDp.dp.toPx()) / 2f
                val rectTop = (size.height - heightDp.dp.toPx()) / 2f
                val rectRight = rectLeft + widthDp.dp.toPx()
                val rectBottom = rectTop + heightDp.dp.toPx()

                capsulePath.rewind()
                capsulePath.addRoundRect(
                    RoundRect(
                        rect = Rect(rectLeft, rectTop, rectRight, rectBottom),
                        cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx())
                    )
                )

                pathMeasure.setPath(capsulePath, false)
                val totalLength = pathMeasure.length

                if (totalLength > 0f) {
                    // Base subtle outline so the capsule is softly outlined in Indigo
                    drawPath(
                        path = capsulePath,
                        color = Indigo500.copy(alpha = 0.25f + activeAmp * 0.2f),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // 3 traveling neon light comets along the perimeter (Emerald, Indigo, Rose)
                    val cometColors = listOf(Emerald400, Indigo500, Rose500)
                    val cometSegmentLen = totalLength * 0.30f // 30% of perimeter

                    cometColors.forEachIndexed { index, color ->
                        val offsetFraction = (progress + index * 0.333f) % 1f
                        val startDist = offsetFraction * totalLength
                        val endDist = startDist + cometSegmentLen

                        // Outer soft glow
                        drawPerimeterSegment(
                            pathMeasure = pathMeasure,
                            totalLength = totalLength,
                            startDist = startDist,
                            endDist = endDist,
                            color = color.copy(alpha = 0.22f + activeAmp * 0.28f),
                            strokeWidth = outerStrokeWidth,
                            path1 = segmentPath1,
                            path2 = segmentPath2
                        )
                        // Medium glow
                        drawPerimeterSegment(
                            pathMeasure = pathMeasure,
                            totalLength = totalLength,
                            startDist = startDist,
                            endDist = endDist,
                            color = color.copy(alpha = 0.55f + activeAmp * 0.35f),
                            strokeWidth = midStrokeWidth,
                            path1 = segmentPath1,
                            path2 = segmentPath2
                        )
                        // Core bright line
                        drawPerimeterSegment(
                            pathMeasure = pathMeasure,
                            totalLength = totalLength,
                            startDist = startDist,
                            endDist = endDist,
                            color = color.copy(alpha = 0.95f),
                            strokeWidth = coreStrokeWidth,
                            path1 = segmentPath1,
                            path2 = segmentPath2
                        )
                    }
                }
            }
        }

        content()
    }
}

private fun DrawScope.drawPerimeterSegment(
    pathMeasure: PathMeasure,
    totalLength: Float,
    startDist: Float,
    endDist: Float,
    color: Color,
    strokeWidth: Float,
    path1: Path,
    path2: Path
) {
    if (endDist <= totalLength) {
        path1.rewind()
        pathMeasure.getSegment(startDist, endDist, path1, true)
        drawPath(
            path = path1,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    } else {
        // Wrap around path length
        path1.rewind()
        pathMeasure.getSegment(startDist, totalLength, path1, true)
        drawPath(
            path = path1,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        path2.rewind()
        pathMeasure.getSegment(0f, endDist - totalLength, path2, true)
        drawPath(
            path = path2,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/**
 * Ультратонкий неоновый световой луч в стиле Gemini Live / David-finance-main-2.
 * Рисует бегущий многоцветный градиентный луч и встречный блик, реагирующий на голос.
 */
@Composable
fun MovingNeonGlowVisualizer(
    isRecording: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "neon_beam_transition")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neon_phase"
    )

    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neon_shimmer"
    )

    val neonColors = listOf(
        com.example.ui.theme.NeonEmerald,
        com.example.ui.theme.NeonIndigo,
        com.example.ui.theme.NeonCyan,
        com.example.ui.theme.NeonRose,
        com.example.ui.theme.NeonEmerald
    )

    Canvas(
        modifier = modifier
            .height(6.dp)
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        val boost = if (isRecording) (rmsLevel / 10f).coerceIn(0.15f, 1f) else 0.45f
        val barHeight = (h * (0.55f + 0.45f * boost)).coerceAtMost(h)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - barHeight / 2f),
            size = androidx.compose.ui.geometry.Size(w, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
        )

        val beamWidth = w * (0.48f + 0.25f * boost)
        val startX = -beamWidth + (w + beamWidth * 2f) * phase

        val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                neonColors[0].copy(alpha = 0.85f * shimmer),
                neonColors[1],
                neonColors[2],
                neonColors[3].copy(alpha = 0.85f * shimmer),
                Color.Transparent
            ),
            startX = startX,
            endX = startX + beamWidth
        )

        drawRoundRect(
            brush = brush,
            topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - barHeight / 2f),
            size = androidx.compose.ui.geometry.Size(w, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
        )

        val reverseStartX = w - (w + beamWidth) * phase
        val secondaryBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                com.example.ui.theme.NeonCyan.copy(alpha = 0.55f * boost),
                com.example.ui.theme.NeonEmerald.copy(alpha = 0.75f * boost),
                Color.Transparent
            ),
            startX = reverseStartX,
            endX = reverseStartX + beamWidth * 0.65f
        )

        drawRoundRect(
            brush = secondaryBrush,
            topLeft = androidx.compose.ui.geometry.Offset(0f, centerY - barHeight / 2f),
            size = androidx.compose.ui.geometry.Size(w, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
        )
    }
}

