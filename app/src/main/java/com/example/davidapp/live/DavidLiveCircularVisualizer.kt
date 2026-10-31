package com.example.davidapp.live

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
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

// Approved color tokens from the Obsidian Neo-Bank David Live visual specification
private val IdleRayColor = Color(0xFF2A2F3A)
private val ListeningEmerald = Color(0xFF00E5A8)
private val ActiveSpeechCyan = Color(0xFF00B4FF)
private val PeakMint = Color(0xFF00F0A0)
private val NeonAqua = Color(0xFF00D9F5)
private val DeepEmeraldGlow = Color(0xFF059669)
private val DeepCyanGlow = Color(0xFF0284C7)
private val CenterCircleSurface = Color(0xFF101722)
private val MutedAccent = Color(0xFFEF4444)
private val AmberReconnectAccent = Color(0xFFF59E0B)

private fun normalizedRay(rays: List<Float>, envelope: FloatArray, index: Int): Float {
    val count = DavidLiveSpectrumAnalyzer.RAY_COUNT
    val k = ((index % count) + count) % count
    val env = envelope.getOrElse(k) { 1f }.coerceAtLeast(0.38f)
    return (rays.getOrElse(k) { 0f } / env).coerceIn(0f, 1f)
}

/**
 * Circular 48-ray spectral voice visualizer for **David Live**, built on Jetpack Compose [Canvas],
 * driven by real FFT frequency magnitudes from [DavidLiveSpectrumAnalyzer] and synchronized with
 * the connection state machine ([DavidLiveVisualPhase]):
 *
 * - **[DavidLiveVisualPhase.INITIAL_CONNECTING]** («Пробуждаем Давида...»): softly pulsing emerald core
 *   and expanding thin concentric rings.
 * - **[DavidLiveVisualPhase.CONNECTION_LOST]** («Связь с Давидом прервана»): pulsation stops, outer ring
 *   breaks into 12 discrete segments with zero false speaking indication.
 * - **[DavidLiveVisualPhase.RECONNECTING]** («Восстанавливаем связь...»): segments progressively
 *   reassemble into a continuous ring.
 * - **[DavidLiveVisualPhase.READY_FLASH]** («Давид на связи»): brief emerald confirmation flash strictly
 *   after `setupComplete` before transitioning smoothly into the active spectral visualizer.
 * - **[DavidLiveVisualPhase.CONVERSATION_ACTIVE]**: 48-ray spectral voice visualizer.
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
    visualizerSize: Dp = 296.dp,
    visualPhase: DavidLiveVisualPhase? = null
) {
    val context = LocalContext.current
    val animatorDurationScale = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) {
            1f
        }
    }
    val animationsEnabled = animatorDurationScale > 0.05f

    val effectivePhase = remember(connectionState, visualPhase) {
        resolveDavidLiveVisualPhase(
            connectionState = connectionState,
            explicitVisualPhase = visualPhase
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "david_live_halo")
    val rawAmbientPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_pulse"
    )
    val rawRingWaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_wave_progress"
    )

    val ambientPulse = when {
        effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST -> 0f
        !animationsEnabled -> 0.5f
        else -> rawAmbientPulse
    }
    val ringWaveProgress = if (animationsEnabled) rawRingWaveProgress else 0.45f

    // Smooth transition for ring fragmentation (0f = intact ring, 1f = broken segments)
    val ringFragmentation by animateFloatAsState(
        targetValue = when (effectivePhase) {
            DavidLiveVisualPhase.CONNECTION_LOST -> 1f
            DavidLiveVisualPhase.RECONNECTING -> if (animationsEnabled) (0.75f - 0.55f * ambientPulse) else 0.45f
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (animationsEnabled) 320 else 0, easing = FastOutSlowInEasing),
        label = "ring_fragmentation"
    )

    // Brief green confirmation flash when session reaches setupComplete (READY_FLASH)
    val readyFlashIntensity by animateFloatAsState(
        targetValue = if (effectivePhase == DavidLiveVisualPhase.READY_FLASH) 1f else 0f,
        animationSpec = tween(durationMillis = if (animationsEnabled) 380 else 0, easing = FastOutSlowInEasing),
        label = "ready_flash_intensity"
    )

    val energyClamped = overallEnergy.coerceIn(0f, 1f)
    val voiceCapableState = isDavidLiveVoiceCapableState(connectionState) &&
        effectivePhase != DavidLiveVisualPhase.CONNECTION_LOST &&
        effectivePhase != DavidLiveVisualPhase.RECONNECTING &&
        effectivePhase != DavidLiveVisualPhase.INITIAL_CONNECTING
    val isDavidSpeaking = voiceCapableState &&
        (connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
            activeSpeaker == DavidLiveSpeaker.DAVID)
    val isUserSpeaking = voiceCapableState &&
        (activeSpeaker == DavidLiveSpeaker.USER || (!isMicMuted && energyClamped > 0.18f))

    val voiceActive = voiceCapableState && activeSpeaker != DavidLiveSpeaker.NONE
    val rayVisibility by animateFloatAsState(
        targetValue = if (voiceActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (!animationsEnabled) 0 else if (voiceActive) 90 else 280,
            easing = LinearEasing
        ),
        label = "ray_visibility"
    )
    val radialEnvelope = remember { DavidLiveSpectrumAnalyzer().getRadialSpatialEnvelope() }

    Box(
        modifier = modifier
            .size(visualizerSize)
            .testTag("david_live_circular_visualizer")
            .semantics {
                contentDescription = when (effectivePhase) {
                    DavidLiveVisualPhase.INITIAL_CONNECTING ->
                        "Визуализатор David Live: пробуждаем Давида"
                    DavidLiveVisualPhase.CONNECTION_LOST ->
                        "Визуализатор David Live: связь с Давидом прервана"
                    DavidLiveVisualPhase.RECONNECTING ->
                        "Визуализатор David Live: восстанавливаем связь"
                    DavidLiveVisualPhase.READY_FLASH ->
                        "Визуализатор David Live: Давид на связи"
                    else ->
                        "Круговой спектральный визуализатор голоса David Live"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRingRadius = size.minDimension * 0.488f
            val innerCoreRadius = size.minDimension * 0.162f
            val rayStartRadius = innerCoreRadius + size.minDimension * 0.028f
            val maxRaySpan = outerRingRadius - rayStartRadius - size.minDimension * 0.020f

            // 1. Deep atmospheric neon aura behind the circular visualizer
            val auraPrimary = when {
                effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST ->
                    AmberReconnectAccent.copy(alpha = 0.14f)
                effectivePhase == DavidLiveVisualPhase.RECONNECTING ->
                    lerp(AmberReconnectAccent, ListeningEmerald, 0.55f).copy(alpha = 0.18f + 0.08f * ambientPulse)
                effectivePhase == DavidLiveVisualPhase.READY_FLASH ->
                    PeakMint.copy(alpha = 0.34f + 0.22f * readyFlashIntensity)
                isMicMuted && !isDavidSpeaking -> IdleRayColor.copy(alpha = 0.15f)
                isDavidSpeaking -> ActiveSpeechCyan.copy(alpha = 0.22f + 0.24f * energyClamped)
                else -> ListeningEmerald.copy(alpha = 0.20f + 0.24f * energyClamped)
            }
            val auraSecondary = when {
                effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST ->
                    Color(0xFF1A1215).copy(alpha = 0.72f)
                effectivePhase == DavidLiveVisualPhase.READY_FLASH ->
                    ListeningEmerald.copy(alpha = 0.28f * readyFlashIntensity)
                isMicMuted && !isDavidSpeaking -> Color(0xFF0B111B).copy(alpha = 0.70f)
                isDavidSpeaking -> PeakMint.copy(alpha = 0.12f + 0.16f * energyClamped)
                else -> ActiveSpeechCyan.copy(alpha = 0.10f + 0.16f * energyClamped)
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraPrimary,
                        auraSecondary,
                        Color(0xFF0A121E).copy(alpha = 0.78f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRingRadius * 1.04f
                ),
                radius = outerRingRadius * 1.04f,
                center = center
            )

            // 2. State-driven Outer Ring / Expanding Rings / Fragmented & Reassembling Segments
            when {
                effectivePhase == DavidLiveVisualPhase.INITIAL_CONNECTING -> {
                    // Expanding thin concentric emerald rings from core to outer boundary
                    val ringCount = 3
                    for (r in 0 until ringCount) {
                        val phaseOffset = (ringWaveProgress + r / ringCount.toFloat()) % 1f
                        val ringRadius = innerCoreRadius * 1.12f +
                            (outerRingRadius - innerCoreRadius * 1.12f) * phaseOffset
                        val ringAlpha = ((1f - phaseOffset) * 0.62f).coerceIn(0.06f, 0.62f)
                        drawCircle(
                            color = lerp(PeakMint, ListeningEmerald, phaseOffset).copy(alpha = ringAlpha),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    // Soft outer boundary guide ring
                    drawCircle(
                        color = ListeningEmerald.copy(alpha = 0.26f + 0.14f * ambientPulse),
                        radius = outerRingRadius,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }

                ringFragmentation > 0.04f -> {
                    // CONNECTION_LOST (static fragmented segments) or RECONNECTING (segments reassembling into a ring)
                    val segmentCount = 12
                    val stepDeg = 360f / segmentCount
                    val gapDeg = (15f * ringFragmentation).coerceIn(2f, 16f)
                    val sweepDeg = (stepDeg - gapDeg).coerceAtLeast(8f)
                    val segmentColor = if (effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST) {
                        lerp(AmberReconnectAccent, MutedAccent, 0.35f).copy(alpha = 0.68f)
                    } else {
                        lerp(ListeningEmerald, AmberReconnectAccent, ringFragmentation * 0.55f)
                            .copy(alpha = 0.55f + 0.25f * (1f - ringFragmentation))
                    }

                    for (s in 0 until segmentCount) {
                        // Alternating radial offset when broken; converges to 0 as ring reassembles
                        val radialJitter = if (s % 2 == 0) 1f else -0.65f
                        val radiusOffset = size.minDimension * 0.022f * ringFragmentation * radialJitter
                        val segRadius = (outerRingRadius + radiusOffset).coerceAtLeast(innerCoreRadius * 1.3f)
                        val arcTopLeft = Offset(center.x - segRadius, center.y - segRadius)
                        val arcSize = Size(segRadius * 2f, segRadius * 2f)
                        val startAngle = -90f + s * stepDeg + gapDeg / 2f
                        drawArc(
                            color = segmentColor,
                            startAngle = startAngle,
                            sweepAngle = sweepDeg,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                else -> {
                    // Intact outer ring (READY_FLASH or CONVERSATION_ACTIVE)
                    val outerRingColor = when {
                        readyFlashIntensity > 0.05f ->
                            lerp(ListeningEmerald, PeakMint, readyFlashIntensity)
                                .copy(alpha = (0.48f + 0.42f * readyFlashIntensity).coerceIn(0f, 1f))
                        isMicMuted && !isDavidSpeaking -> IdleRayColor.copy(alpha = 0.55f)
                        isDavidSpeaking -> lerp(ListeningEmerald, ActiveSpeechCyan, 0.55f)
                            .copy(alpha = 0.38f + 0.32f * energyClamped)
                        else -> lerp(ListeningEmerald, NeonAqua, 0.35f)
                            .copy(alpha = 0.34f + 0.28f * energyClamped)
                    }
                    val ringStrokeWidth = (1.4f + 1.4f * readyFlashIntensity).dp.toPx()
                    drawCircle(
                        color = outerRingColor,
                        radius = outerRingRadius,
                        center = center,
                        style = Stroke(width = ringStrokeWidth)
                    )
                }
            }

            // Short emerald confirmation flash ring when setupComplete is confirmed (READY_FLASH)
            if (readyFlashIntensity > 0.02f) {
                val flashRadius = innerCoreRadius + (outerRingRadius - innerCoreRadius) * (1f - 0.25f * readyFlashIntensity)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PeakMint.copy(alpha = 0.36f * readyFlashIntensity),
                            ListeningEmerald.copy(alpha = 0.20f * readyFlashIntensity),
                            Color.Transparent
                        ),
                        center = center,
                        radius = flashRadius
                    ),
                    radius = flashRadius,
                    center = center
                )
            }

            // 3. Inner neon halo around the central microphone core
            val haloPulseScale = when (effectivePhase) {
                DavidLiveVisualPhase.CONNECTION_LOST -> 1.18f
                DavidLiveVisualPhase.INITIAL_CONNECTING -> 1.28f + 0.20f * ambientPulse
                DavidLiveVisualPhase.RECONNECTING -> 1.24f + 0.12f * ambientPulse
                else -> 1.36f + 0.08f * ambientPulse + 0.28f * energyClamped + 0.14f * readyFlashIntensity
            }
            val haloRadius = innerCoreRadius * haloPulseScale
            val haloCoreColor = when {
                effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST ->
                    AmberReconnectAccent.copy(alpha = 0.18f)
                effectivePhase == DavidLiveVisualPhase.RECONNECTING ->
                    lerp(AmberReconnectAccent, ListeningEmerald, 0.6f).copy(alpha = 0.26f + 0.12f * ambientPulse)
                effectivePhase == DavidLiveVisualPhase.INITIAL_CONNECTING ->
                    ListeningEmerald.copy(alpha = 0.32f + 0.22f * ambientPulse)
                isMicMuted && !isDavidSpeaking -> IdleRayColor.copy(alpha = 0.20f)
                isDavidSpeaking -> ActiveSpeechCyan.copy(alpha = 0.34f + 0.28f * energyClamped)
                else -> PeakMint.copy(alpha = 0.30f + 0.28f * energyClamped + 0.20f * readyFlashIntensity)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        haloCoreColor,
                        (if (isDavidSpeaking) DeepCyanGlow else DeepEmeraldGlow)
                            .copy(alpha = 0.16f + 0.18f * energyClamped),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )

            // 4. 48 radial frequency rays + 3 concentric rings of glowing capsule segments
            val rayCount = DavidLiveSpectrumAnalyzer.RAY_COUNT
            val baseStrokeWidth = (size.minDimension * 0.0138f).coerceAtLeast(3.4f)

            val raysToDraw = if (rayVisibility > 0.02f) rayCount else 0

            for (i in 0 until raysToDraw) {
                val fftMag = 0.5f * normalizedRay(rayMagnitudes, radialEnvelope, i) +
                    0.25f * normalizedRay(rayMagnitudes, radialEnvelope, i - 1) +
                    0.25f * normalizedRay(rayMagnitudes, radialEnvelope, i + 1)

                val angleFromTopRad = (2.0 * PI * i.toDouble() / rayCount.toDouble()).toFloat()
                val canvasAngleRad = angleFromTopRad - (PI / 2.0).toFloat()
                val dirX = cos(canvasAngleRad)
                val dirY = sin(canvasAngleRad)

                val verticalFocus = abs(cos(angleFromTopRad)).pow(1.15f)
                val effectiveMag = (fftMag * 1.12f * rayVisibility).coerceIn(0.05f, 1.0f)

                val bottomHemisphere = (0.5f - 0.5f * cos(angleFromTopRad)).coerceIn(0f, 1f)
                val cyanMix = when {
                    isDavidSpeaking -> (0.38f + 0.48f * bottomHemisphere + 0.14f * (1f - verticalFocus)).coerceIn(0.25f, 0.92f)
                    isUserSpeaking -> (0.18f + 0.46f * bottomHemisphere + 0.22f * effectiveMag).coerceIn(0.15f, 0.82f)
                    else -> (0.14f + 0.34f * bottomHemisphere).coerceIn(0.10f, 0.55f)
                }

                val innerNeonColor = if (isMicMuted && !isDavidSpeaking) {
                    IdleRayColor
                } else {
                    lerp(PeakMint, ListeningEmerald, 0.35f + 0.35f * (1f - verticalFocus))
                }

                val outerNeonColor = if (isMicMuted && !isDavidSpeaking) {
                    IdleRayColor
                } else {
                    lerp(ListeningEmerald, ActiveSpeechCyan, cyanMix)
                }

                val tipNeonColor = if (isMicMuted && !isDavidSpeaking) {
                    IdleRayColor
                } else {
                    lerp(NeonAqua, ActiveSpeechCyan, (cyanMix + 0.2f).coerceIn(0f, 1f))
                }

                val totalSpan = maxRaySpan * effectiveMag.coerceIn(0.05f, 1f)
                val primaryBarLen = (totalSpan * 0.56f).coerceAtLeast(baseStrokeWidth * 1.15f)
                val barStart = Offset(
                    x = center.x + dirX * rayStartRadius,
                    y = center.y + dirY * rayStartRadius
                )
                val barEnd = Offset(
                    x = center.x + dirX * (rayStartRadius + primaryBarLen),
                    y = center.y + dirY * (rayStartRadius + primaryBarLen)
                )

                if (!isMicMuted || isDavidSpeaking) {
                    val bloomEndDist = (rayStartRadius + totalSpan * 0.95f).coerceAtMost(outerRingRadius - baseStrokeWidth)
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                innerNeonColor.copy(alpha = (0.20f + 0.28f * effectiveMag).coerceIn(0.15f, 0.48f)),
                                outerNeonColor.copy(alpha = (0.12f + 0.22f * effectiveMag).coerceIn(0.08f, 0.34f)),
                                Color.Transparent
                            ),
                            start = barStart,
                            end = Offset(
                                x = center.x + dirX * bloomEndDist,
                                y = center.y + dirY * bloomEndDist
                            )
                        ),
                        start = barStart,
                        end = Offset(
                            x = center.x + dirX * bloomEndDist,
                            y = center.y + dirY * bloomEndDist
                        ),
                        strokeWidth = baseStrokeWidth * 2.55f,
                        cap = StrokeCap.Round
                    )
                }

                val barAlpha = if (isMicMuted && !isDavidSpeaking) {
                    0.45f
                } else {
                    (0.74f + 0.26f * effectiveMag).coerceIn(0.74f, 1f)
                }
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            innerNeonColor.copy(alpha = barAlpha),
                            outerNeonColor.copy(alpha = barAlpha)
                        ),
                        start = barStart,
                        end = barEnd
                    ),
                    start = barStart,
                    end = barEnd,
                    strokeWidth = baseStrokeWidth,
                    cap = StrokeCap.Round
                )

                val segmentGap = baseStrokeWidth * 1.48f
                val bead1Dist = rayStartRadius + primaryBarLen + segmentGap
                if (bead1Dist < outerRingRadius - baseStrokeWidth) {
                    val bead1Len = (totalSpan * 0.17f).coerceIn(baseStrokeWidth * 0.55f, baseStrokeWidth * 2.0f)
                    val b1Start = Offset(
                        x = center.x + dirX * bead1Dist,
                        y = center.y + dirY * bead1Dist
                    )
                    val b1End = Offset(
                        x = center.x + dirX * (bead1Dist + bead1Len).coerceAtMost(outerRingRadius - baseStrokeWidth),
                        y = center.y + dirY * (bead1Dist + bead1Len).coerceAtMost(outerRingRadius - baseStrokeWidth)
                    )
                    val bead1Alpha = if (isMicMuted && !isDavidSpeaking) {
                        0.30f
                    } else {
                        (0.58f + 0.38f * effectiveMag).coerceIn(0.55f, 0.96f)
                    }
                    drawLine(
                        color = outerNeonColor.copy(alpha = bead1Alpha),
                        start = b1Start,
                        end = b1End,
                        strokeWidth = baseStrokeWidth * 0.92f,
                        cap = StrokeCap.Round
                    )

                    val bead2Dist = bead1Dist + bead1Len + segmentGap * 0.92f
                    if (bead2Dist < outerRingRadius - baseStrokeWidth) {
                        val bead2Len = (totalSpan * 0.11f).coerceIn(baseStrokeWidth * 0.42f, baseStrokeWidth * 1.35f)
                        val b2Start = Offset(
                            x = center.x + dirX * bead2Dist,
                            y = center.y + dirY * bead2Dist
                        )
                        val b2End = Offset(
                            x = center.x + dirX * (bead2Dist + bead2Len).coerceAtMost(outerRingRadius - baseStrokeWidth),
                            y = center.y + dirY * (bead2Dist + bead2Len).coerceAtMost(outerRingRadius - baseStrokeWidth)
                        )
                        val bead2Alpha = if (isMicMuted && !isDavidSpeaking) {
                            0.20f
                        } else {
                            (0.40f + 0.45f * effectiveMag).coerceIn(0.38f, 0.88f)
                        }
                        drawLine(
                            color = tipNeonColor.copy(alpha = bead2Alpha),
                            start = b2Start,
                            end = b2End,
                            strokeWidth = baseStrokeWidth * 0.84f,
                            cap = StrokeCap.Round
                        )

                        val bead3Dist = bead2Dist + bead2Len + segmentGap * 0.88f
                        if (bead3Dist < outerRingRadius - baseStrokeWidth * 0.8f) {
                            val bead3Len = (baseStrokeWidth * 0.48f).coerceAtLeast(2.2f)
                            val b3Start = Offset(
                                x = center.x + dirX * bead3Dist,
                                y = center.y + dirY * bead3Dist
                            )
                            val b3End = Offset(
                                x = center.x + dirX * (bead3Dist + bead3Len),
                                y = center.y + dirY * (bead3Dist + bead3Len)
                            )
                            val bead3Alpha = if (isMicMuted && !isDavidSpeaking) {
                                0.14f
                            } else {
                                (0.26f + 0.44f * effectiveMag).coerceIn(0.24f, 0.76f)
                            }
                            drawLine(
                                color = tipNeonColor.copy(alpha = bead3Alpha),
                                start = b3Start,
                                end = b3End,
                                strokeWidth = baseStrokeWidth * 0.76f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        // 5. Central interactive microphone button
        val coreButtonSize = visualizerSize * 0.31f
        val borderTint = when {
            effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST ->
                AmberReconnectAccent.copy(alpha = 0.75f)
            effectivePhase == DavidLiveVisualPhase.RECONNECTING ->
                lerp(AmberReconnectAccent, ListeningEmerald, 0.55f).copy(alpha = 0.78f)
            effectivePhase == DavidLiveVisualPhase.READY_FLASH ->
                PeakMint
            isMicMuted -> MutedAccent.copy(alpha = 0.72f)
            isDavidSpeaking -> ActiveSpeechCyan.copy(alpha = 0.68f + 0.30f * energyClamped)
            else -> ListeningEmerald.copy(alpha = 0.58f + 0.36f * energyClamped)
        }

        Box(
            modifier = Modifier
                .size(coreButtonSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (effectivePhase == DavidLiveVisualPhase.READY_FLASH) {
                                Color(0xFF14352E)
                            } else {
                                Color(0xFF1A2636)
                            },
                            CenterCircleSurface
                        )
                    )
                )
                .border(width = 1.6.dp, color = borderTint, shape = CircleShape)
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
                    effectivePhase == DavidLiveVisualPhase.CONNECTION_LOST -> AmberReconnectAccent
                    effectivePhase == DavidLiveVisualPhase.READY_FLASH -> PeakMint
                    isMicMuted -> MutedAccent
                    isDavidSpeaking -> ActiveSpeechCyan
                    energyClamped > 0.22f -> PeakMint
                    else -> Color(0xFF93C5FD)
                },
                modifier = Modifier.size(coreButtonSize * 0.38f)
            )
        }
    }
}
