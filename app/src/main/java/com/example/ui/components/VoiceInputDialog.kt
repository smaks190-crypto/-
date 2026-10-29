package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Rose500
import com.example.utils.VoiceInputManager
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// Approved reference color states for the 32-band voice visualizer
private val CalmGreen = Color(0xFF22C55E)      // Спокойный (#22C55E)
private val ActiveEmerald = Color(0xFF10B981)  // Активный (#10B981)
private val SpeakingCyan = Color(0xFF06B6D4)   // Говорит (#06B6D4)
private val HighFreqBlue = Color(0xFF2563EB)   // Высокие частоты (правая часть)
private val DeepCapsuleBg = Color(0xFF050D14)

/**
 * 32-Band Real-Time Spectral Voice Visualizer for transaction voice input (`VoiceInputManager`).
 *
 * - **Left part**: Low frequencies (deep voice, 80–350 Hz) -> `#22C55E` / `#10B981`
 * - **Center part**: Core vocal frequencies (speech & formants, 350–2200 Hz) with central boost -> `#10B981` / `#06B6D4`
 * - **Right part**: High frequencies (sibilants & overtones, 2200–7200 Hz) -> `#06B6D4` / `#2563EB`
 */
@Composable
fun Neon32BandVoiceVisualizer(
    frequencyBands: List<Float>,
    audioLevel: Float,
    isCancelling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_32band_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_shift"
    )

    val levelClamped = audioLevel.coerceIn(0f, 1f)
    val borderAlpha = (0.38f + levelClamped * 0.45f).coerceIn(0.35f, 0.88f)
    val borderBrush = if (isCancelling) {
        Brush.horizontalGradient(
            colors = listOf(
                Rose500.copy(alpha = 0.75f),
                Color(0xFFFF6B6B).copy(alpha = 0.85f),
                Rose500.copy(alpha = 0.75f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                CalmGreen.copy(alpha = borderAlpha * 0.85f),
                ActiveEmerald.copy(alpha = borderAlpha),
                SpeakingCyan.copy(alpha = borderAlpha),
                HighFreqBlue.copy(alpha = borderAlpha * 0.8f)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07131D).copy(alpha = 0.94f),
                        DeepCapsuleBg.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Left Circular Microphone Badge
            val micRingColor = if (isCancelling) {
                Rose500
            } else {
                lerp(CalmGreen, SpeakingCyan, (levelClamped * 1.1f).coerceIn(0f, 1f))
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                micRingColor.copy(alpha = 0.24f + levelClamped * 0.22f),
                                Color(0xFF061621)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = micRingColor.copy(alpha = 0.55f + levelClamped * 0.35f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val w = size.width
                    val h = size.height
                    val iconColor = if (isCancelling) Rose500 else lerp(ActiveEmerald, SpeakingCyan, 0.35f)
                    val strokeW = 1.6.dp.toPx()

                    // Microphone capsule head
                    val capsuleW = w * 0.42f
                    val capsuleH = h * 0.56f
                    drawRoundRect(
                        color = iconColor,
                        topLeft = Offset((w - capsuleW) / 2f, h * 0.04f),
                        size = Size(capsuleW, capsuleH),
                        cornerRadius = CornerRadius(capsuleW / 2f, capsuleW / 2f)
                    )

                    // Microphone U-cradle arc
                    val arcWidth = w * 0.72f
                    val arcHeight = h * 0.50f
                    val arcPath = Path().apply {
                        moveTo((w - arcWidth) / 2f, h * 0.40f)
                        cubicTo(
                            (w - arcWidth) / 2f, h * 0.80f,
                            (w + arcWidth) / 2f, h * 0.80f,
                            (w + arcWidth) / 2f, h * 0.40f
                        )
                    }
                    drawPath(
                        path = arcPath,
                        color = iconColor,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Stem & base
                    drawLine(
                        color = iconColor,
                        start = Offset(w / 2f, h * 0.74f),
                        end = Offset(w / 2f, h * 0.94f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(w * 0.32f, h * 0.94f),
                        end = Offset(w * 0.68f, h * 0.94f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 32-Band Real-Time Spectral Canvas
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val bandCount = VoiceInputManager.BAND_COUNT
                val canvasW = size.width
                val canvasH = size.height
                val bottomY = canvasH * 0.88f
                val maxBarH = canvasH * 0.78f
                val minDotH = 2.8.dp.toPx()

                // Bottom dual-tone neon horizon glow
                val glowRadiusX = canvasW * (0.42f + 0.16f * levelClamped)
                val glowAlpha = (0.16f + 0.38f * levelClamped + 0.05f * glowPulse).coerceIn(0.14f, 0.58f)

                if (isCancelling) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Rose500.copy(alpha = glowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(canvasW * 0.48f, canvasH * 0.95f),
                            radius = glowRadiusX
                        ),
                        topLeft = Offset(canvasW * 0.08f, canvasH * 0.45f),
                        size = Size(canvasW * 0.84f, canvasH * 0.60f)
                    )
                } else {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CalmGreen.copy(alpha = glowAlpha * 0.90f),
                                ActiveEmerald.copy(alpha = glowAlpha * 0.45f),
                                Color.Transparent
                            ),
                            center = Offset(canvasW * 0.36f, canvasH * 0.96f),
                            radius = glowRadiusX * 0.75f
                        ),
                        topLeft = Offset(canvasW * 0.04f, canvasH * 0.48f),
                        size = Size(canvasW * 0.64f, canvasH * 0.56f)
                    )
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SpeakingCyan.copy(alpha = glowAlpha * 0.95f),
                                HighFreqBlue.copy(alpha = glowAlpha * 0.40f),
                                Color.Transparent
                            ),
                            center = Offset(canvasW * 0.62f, canvasH * 0.96f),
                            radius = glowRadiusX * 0.75f
                        ),
                        topLeft = Offset(canvasW * 0.30f, canvasH * 0.48f),
                        size = Size(canvasW * 0.64f, canvasH * 0.56f)
                    )
                }

                val slotW = canvasW / bandCount.toFloat()
                val barW = (slotW * 0.54f).coerceIn(2.2.dp.toPx(), 4.6.dp.toPx())

                for (i in 0 until bandCount) {
                    val normPos = i.toFloat() / (bandCount - 1).coerceAtLeast(1)
                    val rawBand = frequencyBands.getOrElse(i) { 0f }.coerceIn(0f, 1f)

                    val bellEnvelope = sin(PI * normPos).toFloat().pow(1.45f)
                    val idleBreathing = if (levelClamped < 0.04f) {
                        0.025f * bellEnvelope * (0.5f + 0.5f * sin(phaseShift + i * 0.28f))
                    } else {
                        0f
                    }

                    val shapedMag = (rawBand * (0.34f + 0.66f * bellEnvelope) + idleBreathing).coerceIn(0f, 1f)
                    val barHeight = (minDotH + shapedMag * (maxBarH - minDotH)).coerceAtLeast(minDotH)

                    val centerX = slotW * i + slotW / 2f
                    val topY = (bottomY - barHeight).coerceAtLeast(canvasH * 0.06f)

                    val baseColor = if (isCancelling) {
                        lerp(Rose500, Color(0xFFFF8585), normPos)
                    } else {
                        when {
                            normPos < 0.35f -> lerp(CalmGreen, ActiveEmerald, normPos / 0.35f)
                            normPos < 0.72f -> lerp(ActiveEmerald, SpeakingCyan, (normPos - 0.35f) / 0.37f)
                            else -> lerp(SpeakingCyan, HighFreqBlue, (normPos - 0.72f) / 0.28f)
                        }
                    }

                    val barTopColor = if (isCancelling) {
                        Color(0xFFFFB4B4)
                    } else {
                        lerp(baseColor, Color(0xFF67E8F9), (shapedMag * 0.55f).coerceIn(0f, 0.65f))
                    }

                    val barAlpha = (0.38f + shapedMag * 0.62f).coerceIn(0.38f, 1f)

                    if (shapedMag > 0.14f) {
                        drawRoundRect(
                            color = baseColor.copy(alpha = (shapedMag * 0.28f).coerceIn(0.05f, 0.32f)),
                            topLeft = Offset(centerX - barW * 0.95f, topY - 1.5.dp.toPx()),
                            size = Size(barW * 1.9f, barHeight + 3.dp.toPx()),
                            cornerRadius = CornerRadius(barW, barW)
                        )
                    }

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                barTopColor.copy(alpha = barAlpha),
                                baseColor.copy(alpha = barAlpha * 0.88f)
                            ),
                            startY = topY,
                            endY = bottomY
                        ),
                        topLeft = Offset(centerX - barW / 2f, topY),
                        size = Size(barW, barHeight),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }
    }
}
