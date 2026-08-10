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
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "perimeter_progress"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val activeAmp = if (isRecording) {
        (amplitude + (sin(wavePhase.toDouble()).toFloat() * 0.15f + 0.15f)).coerceIn(0.15f, 1f)
    } else 0f

    val pathMeasure = remember { PathMeasure() }

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
                val outerStrokeWidth = 14f + activeAmp * 16f
                val midStrokeWidth = 6f + activeAmp * 8f
                val coreStrokeWidth = 2.5f + activeAmp * 2.5f

                val rectLeft = (size.width - widthDp.dp.toPx()) / 2f
                val rectTop = (size.height - heightDp.dp.toPx()) / 2f
                val rectRight = rectLeft + widthDp.dp.toPx()
                val rectBottom = rectTop + heightDp.dp.toPx()

                val capsulePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(rectLeft, rectTop, rectRight, rectBottom),
                            cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx())
                        )
                    )
                }

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
                            strokeWidth = outerStrokeWidth
                        )
                        // Medium glow
                        drawPerimeterSegment(
                            pathMeasure = pathMeasure,
                            totalLength = totalLength,
                            startDist = startDist,
                            endDist = endDist,
                            color = color.copy(alpha = 0.55f + activeAmp * 0.35f),
                            strokeWidth = midStrokeWidth
                        )
                        // Core bright line
                        drawPerimeterSegment(
                            pathMeasure = pathMeasure,
                            totalLength = totalLength,
                            startDist = startDist,
                            endDist = endDist,
                            color = color.copy(alpha = 0.95f),
                            strokeWidth = coreStrokeWidth
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
    strokeWidth: Float
) {
    if (endDist <= totalLength) {
        val segmentPath = Path()
        pathMeasure.getSegment(startDist, endDist, segmentPath, true)
        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    } else {
        // Wrap around path length
        val segmentPath1 = Path()
        pathMeasure.getSegment(startDist, totalLength, segmentPath1, true)
        drawPath(
            path = segmentPath1,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        val segmentPath2 = Path()
        pathMeasure.getSegment(0f, endDist - totalLength, segmentPath2, true)
        drawPath(
            path = segmentPath2,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
