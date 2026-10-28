package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Semantics property exposing the current active spectral energy [0f..1f] for UI testing.
 */
val SpectralActiveEnergyKey = SemanticsPropertyKey<Float>("SpectralActiveEnergy")
var SemanticsPropertyReceiver.spectralActiveEnergy by SpectralActiveEnergyKey

/**
 * Semantics property exposing the dominant frequency region ("SILENCE", "LOW", "CENTER", "HIGH") for UI testing.
 */
val SpectralDominantRegionKey = SemanticsPropertyKey<String>("SpectralDominantRegion")
var SemanticsPropertyReceiver.spectralDominantRegion by SpectralDominantRegionKey

// Approved reference color tokens (Obsidian, Calm Green, Active Emerald, Mint, Speaking Cyan, Peak Sky)
private val CalmGreen = Color(0xFF22C55E)
private val ActiveEmerald = Color(0xFF10B981)
private val MintHighlight = Color(0xFF34D399)
private val TealBridge = Color(0xFF14B8A6)
private val SpeakingCyan = Color(0xFF06B6D4)
private val PeakSkyCyan = Color(0xFF38BDF8)
private val DeepAzureEdge = Color(0xFF3B82F6)
private val BrightMintCore = Color(0xFFA7F3D0)

/**
 * Horizontal 32-band spectral voice visualizer designed for the David Finance AI bottom capsule.
 *
 * Visual & behavioral characteristics matching the approved reference:
 * - 32 vertical rounded-cap bars (`StrokeCap.Round`) inside the dark Obsidian capsule.
 * - Central bars visually dominate via a spatial envelope while preserving real FFT frequency differences:
 *   - Low voice (left side, bands 0..10): warm Emerald/Green `#22C55E` -> `#10B981`
 *   - Normal/Mid voice (center, bands 10..21): bright Mint/Emerald/Teal `#10B981` -> `#34D399` -> `#06B6D4`
 *   - High voice / sibilants (right side, bands 21..31): Cyan/Sky `#06B6D4` -> `#38BDF8`
 * - Asymmetric frame-level interpolation: fast attack on voice onset, smooth release to idle dots on silence/stop.
 * - Delicate per-bar neon halo on active bars without heavy full-screen blur.
 * - Supports both Full mode (inside `VoiceInputDialog` 84.dp capsule) and Compact mode (`VoiceOperationsPane` 68.dp bar).
 */
@Composable
fun SpectralVoiceVisualizer(
    spectrumBands: FloatArray,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    barCount: Int = 32,
    height: Dp = if (compact) 36.dp else 48.dp,
    testTagName: String = "SpectralVoiceVisualizer"
) {
    // Frame-interpolated display values so 60/120 Hz screen rendering is silky-smooth
    // between 32 ms AudioRecord FFT hops
    val displayedBands = remember(barCount) { FloatArray(barCount) }
    val latestTargetBands = remember(barCount) { FloatArray(barCount) }

    // Precompute spatial resting envelope so idle breathing has a subtle central contour
    val idleCenterEnvelope = remember(barCount) {
        FloatArray(barCount) { i ->
            val normDist = abs(i - (barCount - 1) * 0.5f) / ((barCount - 1) * 0.5f).coerceAtLeast(1f)
            (0.45f + 0.55f * exp(-2.2f * normDist * normDist)).coerceIn(0.45f, 1.0f)
        }
    }

    // Precompute per-bar base and top colors across the 32 horizontal positions
    // matching the reference gradient: Emerald Green (left) -> Mint/Teal (center) -> Cyan/Azure (right)
    val barBaseColors = remember(barCount) {
        Array(barCount) { i ->
            val t = i.toFloat() / (barCount - 1).coerceAtLeast(1).toFloat()
            when {
                t < 0.35f -> lerpColor(CalmGreen, ActiveEmerald, t / 0.35f)
                t < 0.68f -> lerpColor(ActiveEmerald, SpeakingCyan, (t - 0.35f) / 0.33f)
                else -> lerpColor(SpeakingCyan, DeepAzureEdge, (t - 0.68f) / 0.32f)
            }
        }
    }

    val barTopColors = remember(barCount) {
        Array(barCount) { i ->
            val t = i.toFloat() / (barCount - 1).coerceAtLeast(1).toFloat()
            when {
                t < 0.35f -> lerpColor(ActiveEmerald, MintHighlight, t / 0.35f)
                t < 0.68f -> lerpColor(MintHighlight, SpeakingCyan, (t - 0.35f) / 0.33f)
                else -> lerpColor(SpeakingCyan, PeakSkyCyan, (t - 0.68f) / 0.32f)
            }
        }
    }

    // Copy incoming FFT bands safely into target array
    LaunchedEffect(spectrumBands, isRecording) {
        if (!isRecording) {
            latestTargetBands.fill(0f)
        } else {
            for (i in 0 until barCount) {
                val raw = if (i < spectrumBands.size) spectrumBands[i] else 0f
                latestTargetBands[i] = if (raw.isFinite()) raw.coerceIn(0f, 1f) else 0f
            }
        }
    }

    var breathingPhase by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableLongStateOf(0L) }

    // Continuous 60fps animation loop for organic attack/release interpolation & subtle idle breathing
    LaunchedEffect(isRecording, barCount) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dtSeconds = if (lastNanos == 0L) {
                    0.016f
                } else {
                    ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.064f)
                }
                lastNanos = nanos

                breathingPhase = (breathingPhase + dtSeconds * 2.2f) % (2f * PI.toFloat())

                // Fast attack (~28ms time constant), gentle release (~160ms time constant)
                val attackStep = (dtSeconds * 24.0f).coerceIn(0.18f, 0.78f)
                val releaseStep = (dtSeconds * 7.5f).coerceIn(0.06f, 0.32f)

                for (i in 0 until barCount) {
                    val target = if (isRecording) latestTargetBands[i] else 0f
                    val current = displayedBands[i]
                    val factor = if (target > current) attackStep else releaseStep
                    val updated = current + (target - current) * factor
                    displayedBands[i] = if (updated < 0.001f && target == 0f) 0f else updated.coerceIn(0f, 1f)
                }

                frameTick = nanos
            }
        }
    }

    // Calculate semantic metrics for accessibility and automated UI tests
    val _tick = frameTick
    var totalEnergy = 0f
    var lowEnergy = 0f
    var centerEnergy = 0f
    var highEnergy = 0f
    for (i in 0 until barCount) {
        val v = max(displayedBands[i], if (isRecording && i < spectrumBands.size) spectrumBands[i].coerceIn(0f, 1f) else 0f)
        totalEnergy += v
        when {
            i < 10 -> lowEnergy += v
            i < 22 -> centerEnergy += v
            else -> highEnergy += v
        }
    }
    val meanEnergy = (totalEnergy / barCount.toFloat()).coerceIn(0f, 1f)
    val dominantRegion = when {
        meanEnergy < 0.025f -> "SILENCE"
        lowEnergy / 10f > centerEnergy / 12f * 1.15f && lowEnergy / 10f > highEnergy / 10f * 1.25f -> "LOW"
        highEnergy / 10f > centerEnergy / 12f * 1.15f && highEnergy / 10f > lowEnergy / 10f * 1.25f -> "HIGH"
        else -> "CENTER"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag(testTagName)
            .semantics {
                spectralActiveEnergy = meanEnergy
                spectralDominantRegion = dominantRegion
            }
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        val horizontalPadding = if (compact) 4.dp.toPx() else 6.dp.toPx()
        val usableWidth = (canvasWidth - horizontalPadding * 2f).coerceAtLeast(barCount.toFloat())
        val slotWidth = usableWidth / barCount.toFloat()

        // Pill thickness proportional to slot width with clean gaps matching the reference image
        val barThickness = (slotWidth * if (compact) 0.48f else 0.52f)
            .coerceIn(2.2.dp.toPx(), if (compact) 4.2.dp.toPx() else 5.4.dp.toPx())
        val glowThickness = barThickness * 2.15f

        // Baseline sits slightly below vertical center so bars grow upward with a grounded reflection Anchor
        // matching the reference capsule where bars rise from a lower baseline
        val baselineY = canvasHeight * 0.78f
        val maxBarGrowHeight = (canvasHeight * 0.68f).coerceAtLeast(barThickness)
        val minDotHeight = barThickness * 0.35f

        for (i in 0 until barCount) {
            val centerX = horizontalPadding + slotWidth * (i + 0.5f)
            val signal = displayedBands[i].coerceIn(0f, 1f)

            // Subtle idle breathing only when recording in silence (never fakes speech)
            val idleBreath = if (isRecording) {
                val wave = 0.5f + 0.5f * sin(breathingPhase + i * 0.28f)
                0.022f * wave * idleCenterEnvelope[i]
            } else {
                0f
            }

            val effectiveAmplitude = max(signal, idleBreath).coerceIn(0f, 1f)
            val activeHeight = minDotHeight + effectiveAmplitude * maxBarGrowHeight

            // Bars grow primarily upward from the grounded baseline and slightly downward (asymmetric 82% up / 18% down)
            // just like the approved reference graphic
            val topY = (baselineY - activeHeight * 0.82f).coerceIn(barThickness * 0.6f, canvasHeight - barThickness * 0.6f)
            val bottomY = (baselineY + activeHeight * 0.18f).coerceIn(topY + 0.5f, canvasHeight - barThickness * 0.5f)

            val baseColor = barBaseColors[i]
            val topColor = if (effectiveAmplitude > 0.62f) {
                lerpColor(barTopColors[i], BrightMintCore, ((effectiveAmplitude - 0.62f) / 0.38f).coerceIn(0f, 0.65f))
            } else {
                barTopColors[i]
            }

            // 1. Delicate outer neon glow for active bars (only when amplitude exceeds quiet threshold)
            if (effectiveAmplitude > 0.08f) {
                val glowAlpha = ((effectiveAmplitude - 0.08f) / 0.92f).coerceIn(0f, 1f) * (if (compact) 0.24f else 0.32f)
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            topColor.copy(alpha = glowAlpha),
                            baseColor.copy(alpha = glowAlpha * 0.65f)
                        ),
                        startY = topY - barThickness * 0.3f,
                        endY = bottomY + barThickness * 0.3f
                    ),
                    start = Offset(centerX, topY),
                    end = Offset(centerX, bottomY),
                    strokeWidth = glowThickness,
                    cap = StrokeCap.Round
                )
            }

            // 2. Main rounded-cap bar body (`StrokeCap.Round`)
            val barAlpha = (0.34f + 0.66f * effectiveAmplitude.coerceIn(0f, 1f)).coerceIn(0.34f, 1.0f)
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topColor.copy(alpha = barAlpha),
                        baseColor.copy(alpha = (barAlpha * 0.92f).coerceIn(0.30f, 1.0f))
                    ),
                    startY = topY,
                    endY = bottomY
                ),
                start = Offset(centerX, topY),
                end = Offset(centerX, bottomY),
                strokeWidth = barThickness,
                cap = StrokeCap.Round
            )

            // 3. Subtle specular highlight core on strongly active bars
            if (effectiveAmplitude > 0.35f) {
                val coreAlpha = ((effectiveAmplitude - 0.35f) / 0.65f).coerceIn(0f, 1f) * 0.45f
                drawLine(
                    color = BrightMintCore.copy(alpha = coreAlpha),
                    start = Offset(centerX, topY + barThickness * 0.15f),
                    end = Offset(centerX, topY + (bottomY - topY) * 0.45f),
                    strokeWidth = barThickness * 0.38f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * t,
        green = start.green + (stop.green - start.green) * t,
        blue = start.blue + (stop.blue - start.blue) * t,
        alpha = start.alpha + (stop.alpha - start.alpha) * t
    )
}