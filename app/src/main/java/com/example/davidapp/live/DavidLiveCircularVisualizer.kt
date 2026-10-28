package com.example.davidapp.live

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

// Approved color tokens from the David Live visual specification
private val IdleRayColor = Color(0xFF2A2F3A)
private val ListeningEmerald = Color(0xFF00E5A8)
private val ActiveSpeechCyan = Color(0xFF00B4FF)
private val PeakMint = Color(0xFF00F0A0)
private val DeepTealGlow = Color(0xFF059669)
private val CenterCircleSurface = Color(0xFF121824)
private val OuterRingBorder = Color(0xFF192838)
private val MutedAccent = Color(0xFFEF4444)

/**
 * Circular 48-ray spectral voice visualizer for **David Live**, built on Jetpack Compose [Canvas]
 * and driven by real FFT frequency magnitudes from [DavidLiveSpectrumAnalyzer].
 *
 * Visual Hierarchy & Reference Alignment:
 * - **Center Poles (Top 90° & Bottom 270°)**: High & upper-mid vocal frequencies extend largest (`центр, крупнее`).
 * - **Horizontal Sides (Left 180° & Right 0°)**: Low frequencies taper smoothly (`к краям, меньше`)
 *   with segmented glowing dots/capsules.
 * - **Idle / Pause (`#2A2F3A`)**: Calm, minimal ring activity with soft ambient breathing.
 * - **User Speaking (`#00E5A8` -> `#00F0A0`)**: Rich Emerald & Mint radial rays with segmented outer tips.
 * - **David Speaking (`#00B4FF` -> `#00E5A8`)**: Electric Cyan & Teal spectrum rays responding to 24 kHz output audio.
 */
@Composable
fun DavidLiveCircularVisualizer(
    rayMagnitudes: List<Float>,
    overallEnergy: Float,
    connectionState: DavidLiveConnectionState,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    onToggleMicMute: () -> Unit,
    modifier: Modifier = Modifier,
    visualizerSize: Dp = 296.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "david_live_halo")
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_pulse"
    )

    val energyClamped = overallEnergy.coerceIn(0f, 1f)
    val isDavidSpeaking = connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
        activeSpeaker == DavidLiveSpeaker.DAVID

    Box(
        modifier = modifier
            .size(visualizerSize)
            .testTag("david_live_circular_visualizer")
            .semantics {
                contentDescription = "Круговой спектральный визуализатор голоса David Live"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRingRadius = size.minDimension * 0.485f
            val innerCoreRadius = size.minDimension * 0.165f
            val rayStartRadius = innerCoreRadius + size.minDimension * 0.032f
            val maxRaySpan = outerRingRadius - rayStartRadius - size.minDimension * 0.025f

            // 1. Subtle dark circular plate & outer ring border (matching reference)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                         (if (isDavidSpeaking) ActiveSpeechCyan else ListeningEmerald)
                            .copy(alpha = 0.06f + 0.16f * energyClamped),
                        Color(0xFF0B111B).copy(alpha = 0.72f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRingRadius
                ),
                radius = outerRingRadius,
                center = center
            )

            drawCircle(
                color = OuterRingBorder.copy(alpha = 0.75f + 0.2f * energyClamped),
                radius = outerRingRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.3.dp.toPx())
            )

            // 2. Inner ambient halo around the central microphone circle
            val haloRadius = innerCoreRadius * (1.28f + 0.06f * ambientPulse + 0.22f * energyClamped)
            val haloPrimary = when {
                isMicMuted -> IdleRayColor.copy(alpha = 0.18f)
                isDavidSpeaking -> ActiveSpeechCyan.copy(alpha = 0.22f + 0.24f * energyClamped)
                else -> ListeningEmerald.copy(alpha = 0.20f + 0.25f * energyClamped)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        haloPrimary,
                        DeepTealGlow.copy(alpha = 0.08f + 0.12f * energyClamped),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )

            // 3. 48 radial frequency rays + segmented outer capsules
            val rayCount = DavidLiveSpectrumAnalyzer.RAY_COUNT
            val baseStrokeWidth = (size.minDimension * 0.0135f).coerceAtLeast(3.2f)

            for (i in 0 until rayCount) {
                val rawMag = rayMagnitudes.getOrElse(i) { 0.04f }.coerceIn(0.03f, 1f)
                val angleDeg = (i.toFloat() / rayCount.toFloat()) * 360f
                val angleRad = (angleDeg * PI / 180.0).toFloat()
                val dirX = cos(angleRad)
                val dirY = sin(angleRad)

                // Verticality factor: 1.0 at top/bottom poles (90°/270° - "Высокие частоты, центр крупнее"),
                // 0.0 at horizontal sides (0°/180° - "Низкие частоты, к краям меньше")
                val verticalFocus = abs(sin(angleRad)).pow(1.25f)

                val effectiveMag = if (isMicMuted && !isDavidSpeaking) {
                    0.04f + 0.02f * verticalFocus * ambientPulse
                } else {
                    rawMag
                }

                // Determine color blend per ray from reference specification
                val activeBaseColor = if (isDavidSpeaking) {
                    lerp(ListeningEmerald, ActiveSpeechCyan, 0.45f + 0.45f * (1f - verticalFocus))
                } else {
                    lerp(
                        ActiveSpeechCyan,
                        lerp(ListeningEmerald, PeakMint, effectiveMag),
                        0.38f + 0.62f * verticalFocus
                    )
                }

                val rayColor = lerp(
                    IdleRayColor,
                    activeBaseColor,
                    ((effectiveMag - 0.04f) / 0.85f).coerceIn(0f, 1f)
                )

                // Polar envelope scales max span so center poles extend furthest and sides stay compact
                val polarScale = 0.46f + 0.54f * verticalFocus
                val totalSpan = maxRaySpan * polarScale * effectiveMag.coerceIn(0.06f, 1f)

                // Primary inner continuous bar segment (62% of active ray length)
                val primaryBarLen = (totalSpan * 0.62f).coerceAtLeast(baseStrokeWidth * 0.9f)
                val barStart = Offset(
                    x = center.x + dirX * rayStartRadius,
                    y = center.y + dirY * rayStartRadius
                )
                val barEnd = Offset(
                    x = center.x + dirX * (rayStartRadius + primaryBarLen),
                    y = center.y + dirY * (rayStartRadius + primaryBarLen)
                )

                // Soft glow under active rays
                if (effectiveMag > 0.22f) {
                    drawLine(
                        color = rayColor.copy(alpha = 0.24f * effectiveMag),
                        start = barStart,
                        end = Offset(
                            x = center.x + dirX * (rayStartRadius + totalSpan),
                            y = center.y + dirY * (rayStartRadius + totalSpan)
                        ),
                        strokeWidth = baseStrokeWidth * 2.2f,
                        cap = StrokeCap.Round
                    )
                }

                // Draw primary inner radial bar
                drawLine(
                    color = rayColor.copy(alpha = (0.45f + 0.55f * effectiveMag).coerceIn(0.35f, 1f)),
                    start = barStart,
                    end = barEnd,
                    strokeWidth = baseStrokeWidth,
                    cap = StrokeCap.Round
                )

                // Segmented outer capsules / dots (matching the reference artwork's beaded radial tails)
                val dotGap = baseStrokeWidth * 1.65f
                val firstBeadDist = rayStartRadius + primaryBarLen + dotGap
                if (effectiveMag > 0.10f && firstBeadDist < outerRingRadius - baseStrokeWidth) {
                    val bead1Len = (totalSpan * 0.16f).coerceIn(baseStrokeWidth * 0.35f, baseStrokeWidth * 1.7f)
                    val b1Start = Offset(
                        x = center.x + dirX * firstBeadDist,
                        y = center.y + dirY * firstBeadDist
                    )
                    val b1End = Offset(
                        x = center.x + dirX * (firstBeadDist + bead1Len),
                        y = center.y + dirY * (firstBeadDist + bead1Len)
                    )
                    drawLine(
                        color = rayColor.copy(alpha = (0.35f + 0.55f * effectiveMag).coerceIn(0.25f, 0.92f)),
                        start = b1Start,
                        end = b1End,
                        strokeWidth = baseStrokeWidth * 0.92f,
                        cap = StrokeCap.Round
                    )

                    // Second outer bead dot for medium/high energy rays
                    val secondBeadDist = firstBeadDist + bead1Len + dotGap * 0.95f
                    if (effectiveMag > 0.26f && secondBeadDist < outerRingRadius - baseStrokeWidth) {
                        val bead2Len = (baseStrokeWidth * 0.45f).coerceAtLeast(2f)
                        val b2Start = Offset(
                            x = center.x + dirX * secondBeadDist,
                            y = center.y + dirY * secondBeadDist
                        )
                        val b2End = Offset(
                            x = center.x + dirX * (secondBeadDist + bead2Len),
                            y = center.y + dirY * (secondBeadDist + bead2Len)
                        )
                        drawLine(
                            color = rayColor.copy(alpha = (0.22f + 0.48f * effectiveMag).coerceIn(0.18f, 0.78f)),
                            start = b2Start,
                            end = b2End,
                            strokeWidth = baseStrokeWidth * 0.82f,
                            cap = StrokeCap.Round
                        )
                    }
                } else {
                    // Subtle resting outer dot in quiet/idle state
                    val idleDotDist = rayStartRadius + primaryBarLen + dotGap * 0.85f
                    val dotPos = Offset(
                        x = center.x + dirX * idleDotDist,
                        y = center.y + dirY * idleDotDist
                    )
                    drawCircle(
                        color = IdleRayColor.copy(alpha = 0.55f),
                        radius = baseStrokeWidth * 0.38f,
                        center = dotPos
                    )
                }
            }
        }

        // 4. Central interactive microphone button (matching the dark circular core in the reference)
        val coreButtonSize = visualizerSize * 0.31f
        val borderTint = when {
            isMicMuted -> MutedAccent.copy(alpha = 0.65f)
            isDavidSpeaking -> ActiveSpeechCyan.copy(alpha = 0.55f + 0.35f * energyClamped)
            else -> ListeningEmerald.copy(alpha = 0.45f + 0.40f * energyClamped)
        }

        Box(
            modifier = Modifier
                .size(coreButtonSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A2333),
                            CenterCircleSurface
                        )
                    )
                )
                .border(width = 1.5.dp, color = borderTint, shape = CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onToggleMicMute
                )
                .testTag("david_live_mic_button")
                .semantics {
                    contentDescription = if (isMicMuted) "Включить микрофон" else "Отключить микрофон"
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = null,
                tint = when {
                    isMicMuted -> MutedAccent
                    isDavidSpeaking -> ActiveSpeechCyan
                    energyClamped > 0.25f -> PeakMint
                    else -> Color(0xFF93C5FD)
                },
                modifier = Modifier.size(coreButtonSize * 0.38f)
            )
        }
    }
}
