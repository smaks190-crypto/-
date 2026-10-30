package com.example.davidapp.live

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private val LiveScreenBgTop = Color(0xFF070B12)
private val LiveScreenBgBottom = Color(0xFF0A101B)
private val SubtitleBubbleSurface = Color(0xFF121A28)
private val SubtitleBubbleBorder = Color(0xFF1E2B3E)
private val CapsuleSurfaceStart = Color(0xFF0B121E)
private val CapsuleSurfaceEnd = Color(0xFF101928)
private val CancelPillSurface = Color(0xFF111824)
private val CancelPillBorder = Color(0xFF223044)
private val EmeraldStatus = Color(0xFF00E5A8)
private val CyanSpeaking = Color(0xFF00B4FF)
private val PeakMintWave = Color(0xFF00F0A0)
private val NeonAquaWave = Color(0xFF00D9F5)
private val IdleWaveSlate = Color(0xFF2A2F3A)
private val ErrorCrimson = Color(0xFFEF4444)

/**
 * Resolves the human-readable Russian status label for the David Live session,
 * shared between the full-screen interface and the compact minimized capsule.
 */
fun resolveDavidLiveStatusLabel(
    connectionState: DavidLiveConnectionState,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean
): String {
    return when {
        connectionState == DavidLiveConnectionState.NO_API_KEY -> "Требуется API-ключ"
        connectionState == DavidLiveConnectionState.ERROR -> "Ошибка связи"
        connectionState == DavidLiveConnectionState.CONNECTING -> "Подключение..."
        connectionState == DavidLiveConnectionState.RECONNECTING -> "Переподключение..."
        isMicMuted && activeSpeaker != DavidLiveSpeaker.DAVID &&
            connectionState != DavidLiveConnectionState.DAVID_SPEAKING -> "Микрофон выключен"
        connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
            activeSpeaker == DavidLiveSpeaker.DAVID -> "Говорит Давид..."
        else -> "Слушаю тебя..."
    }
}

/**
 * Horizontal multi-band spectral wave visualizer for the David Live morphing transition
 * and compact bottom voice capsule (`DavidLiveMinimizedCapsule`).
 *
 * Driven by the exact same real-time FFT `rayMagnitudes` and `overallEnergy` from
 * [DavidLiveSpectrumAnalyzer] / [DavidLiveAudioEngine]:
 * - Green/Emerald (`#00E5A8` / `#00F0A0`) when the user is speaking / listening;
 * - Cyan/Blue (`#00B4FF` / `#00D9F5`) when David is speaking;
 * - Muted slate (`#2A2F3A`) when muted or silent, with smooth decay and zero chaotic jitter.
 */
@Composable
fun DavidLiveHorizontalWaveVisualizer(
    rayMagnitudes: List<Float>,
    overallEnergy: Float,
    connectionState: DavidLiveConnectionState,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24
) {
    val energyClamped = overallEnergy.coerceIn(0f, 1f)
    val isDavidSpeaking = connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
        activeSpeaker == DavidLiveSpeaker.DAVID
    val isUserSpeaking = activeSpeaker == DavidLiveSpeaker.USER || (!isMicMuted && energyClamped > 0.16f)
    val isVoiceActive = (isDavidSpeaking || isUserSpeaking) &&
        connectionState != DavidLiveConnectionState.ERROR &&
        connectionState != DavidLiveConnectionState.NO_API_KEY

    val waveActivity by animateFloatAsState(
        targetValue = if (isVoiceActive) 1f else 0.12f,
        animationSpec = tween(
            durationMillis = if (isVoiceActive) 90 else 260,
            easing = LinearEasing
        ),
        label = "horizontal_wave_activity"
    )

    val radialEnvelope = remember { DavidLiveSpectrumAnalyzer().getRadialSpatialEnvelope() }

    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "Горизонтальная звуковая волна David Live"
            }
    ) {
        val totalBars = barCount.coerceIn(12, 36)
        val widthPx = size.width
        val heightPx = size.height
        val centerY = heightPx / 2f
        val stepX = widthPx / (totalBars + 1).toFloat()
        val strokeWidth = (stepX * 0.52f).coerceIn(2.4.dp.toPx(), 4.5.dp.toPx())
        val minBarHalfHeight = (heightPx * 0.07f).coerceAtLeast(1.8.dp.toPx())
        val maxBarHalfHeight = (heightPx * 0.46f).coerceAtLeast(minBarHalfHeight + 2f)

        // Subtle ambient glow under the wave when voice is active
        if (waveActivity > 0.2f && (!isMicMuted || isDavidSpeaking)) {
            val glowColor = if (isDavidSpeaking) CyanSpeaking else EmeraldStatus
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = (0.18f + 0.22f * energyClamped) * waveActivity),
                        Color.Transparent
                    ),
                    center = Offset(widthPx / 2f, centerY),
                    radius = (widthPx * 0.48f).coerceAtLeast(16f)
                ),
                topLeft = Offset.Zero,
                size = Size(widthPx, heightPx),
                cornerRadius = CornerRadius(heightPx / 2f, heightPx / 2f)
            )
        }

        val rayCount = DavidLiveSpectrumAnalyzer.RAY_COUNT
        for (i in 0 until totalBars) {
            val normPos = (i + 0.5f) / totalBars.toFloat() // 0..1 left-to-right
            // Bell-shaped central emphasis: tallest in the center, tapering smoothly to the edges
            val centerEnvelope = sin(PI * normPos.toDouble()).toFloat().pow(1.28f)

            // Map center bars to pole indices (0 / 24 = vocal formant peak) and outer bars to side indices (12 / 36)
            val distFromCenter = kotlin.math.abs(normPos - 0.5f) * 2f // 0 at center, 1 at edges
            val rayIdx = ((distFromCenter * 12f).roundToInt() + (if (normPos >= 0.5f) 0 else 24)) % rayCount
            val env = radialEnvelope.getOrElse(rayIdx) { 0.7f }.coerceAtLeast(0.38f)
            val rawRay = (rayMagnitudes.getOrElse(rayIdx) { 0.08f } / env).coerceIn(0f, 1f)

            val activeMagnitude = if (isMicMuted && !isDavidSpeaking) {
                0.05f
            } else {
                (rawRay * (0.35f + 0.85f * centerEnvelope) * waveActivity).coerceIn(0.05f, 1f)
            }

            val halfBar = minBarHalfHeight + (maxBarHalfHeight - minBarHalfHeight) * activeMagnitude
            val x = stepX * (i + 1)

            val barColor = when {
                isMicMuted && !isDavidSpeaking -> IdleWaveSlate.copy(alpha = 0.65f)
                !isVoiceActive -> IdleWaveSlate.copy(alpha = 0.72f)
                isDavidSpeaking -> lerp(
                    CyanSpeaking,
                    NeonAquaWave,
                    (centerEnvelope * 0.65f + activeMagnitude * 0.35f).coerceIn(0f, 1f)
                )
                else -> lerp(
                    EmeraldStatus,
                    PeakMintWave,
                    (centerEnvelope * 0.6f + activeMagnitude * 0.4f).coerceIn(0f, 1f)
                )
            }

            drawLine(
                color = barColor,
                start = Offset(x, centerY - halfBar),
                end = Offset(x, centerY + halfBar),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Compact bottom voice capsule for **David Live** (`DavidLiveDisplayMode.MINIMIZED`),
 * matching Frame 5 & Frame 10 of the Obsidian Neo-Bank visual specification.
 *
 * - **Left**: Frog avatar (`🐸`), title `"Давид"` + `"● LIVE"` badge, and current status subtitle.
 * - **Center**: Real-time horizontal spectral wave ([DavidLiveHorizontalWaveVisualizer]) reactive
 *   to the user's voice (emerald `#00E5A8`) and David's voice (cyan `#00B4FF`).
 * - **Right**: Independent Microphone Mute/Unmute button and Red End Call button (`#EF4444`).
 * - **Tap on capsule body**: Expands back to full-screen David Live (`onExpand`).
 */
@Composable
fun DavidLiveMinimizedCapsule(
    connectionState: DavidLiveConnectionState,
    rayMagnitudes: List<Float>,
    overallEnergy: Float,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    onExpand: () -> Unit,
    onToggleMicMute: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier,
    embeddedInBottomBar: Boolean = false
) {
    val energyClamped = overallEnergy.coerceIn(0f, 1f)
    val isDavidSpeaking = connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
        activeSpeaker == DavidLiveSpeaker.DAVID
    val statusText = resolveDavidLiveStatusLabel(
        connectionState = connectionState,
        activeSpeaker = activeSpeaker,
        isMicMuted = isMicMuted
    )

    val capsuleCornerRadius = if (embeddedInBottomBar) 22.dp else 26.dp

    val capsuleBorderColor = when {
        connectionState == DavidLiveConnectionState.ERROR ||
            connectionState == DavidLiveConnectionState.NO_API_KEY -> ErrorCrimson.copy(alpha = 0.65f)
        isMicMuted && !isDavidSpeaking -> Color(0xFF283548)
        isDavidSpeaking -> CyanSpeaking.copy(alpha = 0.55f + 0.30f * energyClamped)
        else -> EmeraldStatus.copy(alpha = 0.48f + 0.32f * energyClamped)
    }

    val capsuleGlowColor = when {
        isDavidSpeaking -> CyanSpeaking
        isMicMuted -> Color(0xFF1E293B)
        else -> EmeraldStatus
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(if (embeddedInBottomBar) 68.dp else 66.dp)
            .then(
                if (embeddedInBottomBar) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(capsuleCornerRadius),
                        ambientColor = capsuleGlowColor.copy(alpha = 0.45f),
                        spotColor = capsuleGlowColor.copy(alpha = 0.55f)
                    )
                }
            )
            .clip(RoundedCornerShape(capsuleCornerRadius))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        CapsuleSurfaceStart,
                        CapsuleSurfaceEnd,
                        CapsuleSurfaceStart
                    )
                )
            )
            .border(
                width = if (embeddedInBottomBar) 1.dp else 1.2.dp,
                color = capsuleBorderColor,
                shape = RoundedCornerShape(capsuleCornerRadius)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onExpand
            )
            .testTag("david_live_minimized_capsule")
            .semantics {
                contentDescription = "Компактная панель David Live. Нажмите, чтобы развернуть."
            },
        color = Color.Transparent,
        shape = RoundedCornerShape(capsuleCornerRadius)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Left: Frog avatar + "Давид • LIVE" + Status subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1.15f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isDavidSpeaking) CyanSpeaking.copy(alpha = 0.28f) else EmeraldStatus.copy(alpha = 0.28f),
                                    Color(0xFF0D2329),
                                    Color(0xFF09131C)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            color = if (isDavidSpeaking) {
                                CyanSpeaking.copy(alpha = 0.72f)
                            } else {
                                EmeraldStatus.copy(alpha = 0.72f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🐸",
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Давид",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        DavidLiveConnectionState.ERROR,
                                        DavidLiveConnectionState.NO_API_KEY -> ErrorCrimson
                                        DavidLiveConnectionState.DAVID_SPEAKING -> CyanSpeaking
                                        else -> EmeraldStatus
                                    }
                                )
                        )
                        Text(
                            text = "LIVE",
                            color = when (connectionState) {
                                DavidLiveConnectionState.ERROR,
                                DavidLiveConnectionState.NO_API_KEY -> ErrorCrimson
                                DavidLiveConnectionState.DAVID_SPEAKING -> CyanSpeaking
                                else -> EmeraldStatus
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = statusText,
                        color = if (isMicMuted && !isDavidSpeaking) {
                            Color(0xFFFCA5A5)
                        } else {
                            Color(0xFF94A3B8)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("david_live_capsule_status")
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 2. Center: Real-time horizontal spectral waveform
            DavidLiveHorizontalWaveVisualizer(
                rayMagnitudes = rayMagnitudes,
                overallEnergy = overallEnergy,
                connectionState = connectionState,
                activeSpeaker = activeSpeaker,
                isMicMuted = isMicMuted,
                barCount = 20,
                modifier = Modifier
                    .weight(0.95f)
                    .height(34.dp)
                    .testTag("david_live_capsule_waveform")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Right: Independent Microphone Mute & Red End Call buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mic Mute/Unmute circular button
                val micBgColor = when {
                    isMicMuted -> ErrorCrimson.copy(alpha = 0.20f)
                    isDavidSpeaking -> CyanSpeaking.copy(alpha = 0.20f)
                    else -> CyanSpeaking.copy(alpha = 0.16f)
                }
                val micBorderColor = when {
                    isMicMuted -> ErrorCrimson.copy(alpha = 0.75f)
                    isDavidSpeaking -> CyanSpeaking.copy(alpha = 0.72f)
                    else -> EmeraldStatus.copy(alpha = 0.68f)
                }
                val micIconTint = when {
                    isMicMuted -> ErrorCrimson
                    isDavidSpeaking -> CyanSpeaking
                    else -> NeonAquaWave
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(micBgColor)
                        .border(1.1.dp, micBorderColor, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onToggleMicMute
                        )
                        .testTag("david_live_capsule_mute_button")
                        .semantics {
                            contentDescription = if (isMicMuted) {
                                "Включить микрофон в компактном режиме"
                            } else {
                                "Выключить микрофон в компактном режиме"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        tint = micIconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Red End Call circular button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = ErrorCrimson,
                            spotColor = ErrorCrimson
                        )
                        .clip(CircleShape)
                        .background(ErrorCrimson)
                        .border(1.dp, Color(0xFFF87171).copy(alpha = 0.55f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onEndSession
                        )
                        .testTag("david_live_capsule_end_button")
                        .semantics {
                            contentDescription = "Завершить разговор David Live"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full-screen voice conversation screen for **David Live** with a smooth 350–500 ms
 * multi-stage morphing transition into the compact bottom voice capsule ([DavidLiveMinimizedCapsule]).
 *
 * Implements the 10-step visual storyboard:
 * 1. Active full-screen David Live with 48-ray circular spectral visualizer.
 * 2. Start of minimize: circular visualizer compresses (`scale`), backdrop dims.
 * 3. Visualizer transformation: circular 48-ray spectrum morphs into horizontal spectral wave.
 * 4. Text & controls transition downward toward the bottom capsule.
 * 5. Final minimized view: compact voice capsule at the bottom of the screen while keeping the
 *    rest of the app interactive.
 * 6–10. Reverse expansion (`MINIMIZED -> EXPANDED`) and clean session termination (`ENDED / HIDDEN`).
 */
@Composable
fun DavidLiveSheet(
    connectionState: DavidLiveConnectionState,
    liveSubtitle: String,
    rayMagnitudes: List<Float>,
    overallEnergy: Float,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    errorMessage: String?,
    onToggleMicMute: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenApiKeySettings: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier,
    displayMode: DavidLiveDisplayMode = DavidLiveDisplayMode.EXPANDED,
    onMinimize: () -> Unit = {},
    onMinimizeToChat: (() -> Unit)? = null,
    onExpand: () -> Unit = {},
    isEmbeddedInModalCard: Boolean = false,
    renderEmbeddedMinimizedCapsule: Boolean = true,
    minimizedBottomOffsetDp: Dp = 14.dp
) {
    val context = LocalContext.current

    // Bind hardware volume buttons to the Android 17 AI Assistant / Neural Network volume channel
    // across the entire active David Live session (both EXPANDED and MINIMIZED).
    DisposableEffect(context, displayMode != DavidLiveDisplayMode.HIDDEN) {
        val activity = context as? Activity
        val previousVolumeStream = activity?.volumeControlStream ?: AudioManager.USE_DEFAULT_STREAM_TYPE
        if (displayMode != DavidLiveDisplayMode.HIDDEN) {
            activity?.volumeControlStream = DavidLiveAudioEngine.getAssistantVolumeStreamType()
        }
        onDispose {
            if (displayMode == DavidLiveDisplayMode.HIDDEN) {
                activity?.volumeControlStream = previousVolumeStream
            }
        }
    }

    // In EXPANDED mode, system Back minimizes if a minimize handler is active, or ends session
    BackHandler(enabled = displayMode == DavidLiveDisplayMode.EXPANDED) {
        onMinimize()
    }

    // Respect Android system "Remove animations" / reduced motion accessibility setting
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
    val morphDurationMs = if (animatorDurationScale <= 0.05f) 0 else 430

    val transition = updateTransition(
        targetState = displayMode,
        label = "david_live_display_mode_transition"
    )

    // 1f = Full-screen EXPANDED (Step 1), 0f = Compact MINIMIZED (Step 5)
    val expandProgress by transition.animateFloat(
        transitionSpec = {
            tween(durationMillis = morphDurationMs, easing = FastOutSlowInEasing)
        },
        label = "expand_progress"
    ) { mode ->
        when (mode) {
            DavidLiveDisplayMode.EXPANDED -> 1f
            DavidLiveDisplayMode.MINIMIZED -> 0f
            DavidLiveDisplayMode.HIDDEN -> 0f
        }
    }

    if (displayMode == DavidLiveDisplayMode.HIDDEN && expandProgress <= 0.01f) {
        return
    }

    // Stage progress helpers matching the 10-step storyboard:
    // Step 1 -> 2 (1.0 .. 0.62): Circular visualizer compresses, top bar & subtitle bubble fade
    // Step 2 -> 3 (0.68 .. 0.32): Circular visualizer cross-fades into horizontal wave visualizer
    // Step 3 -> 4 (0.55 .. 0.12): Intermediate morph panel shifts downward toward the bottom capsule
    // Step 4 -> 5 (0.28 .. 0.0): Compact bottom capsule locks into place
    val backdropAlpha = ((expandProgress - 0.08f) / 0.92f).coerceIn(0f, 1f)
    val topHeaderAlpha = ((expandProgress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val circularAlpha = ((expandProgress - 0.42f) / 0.42f).coerceIn(0f, 1f)
    val circularScale = 0.54f + (1f - 0.54f) * ((expandProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)

    // Intermediate horizontal wave stage (Steps 3, 4, 9) visible while morphing between circle and capsule
    val intermediateWaveAlpha = when {
        expandProgress > 0.72f -> ((1f - expandProgress) / 0.28f).coerceIn(0f, 1f)
        expandProgress >= 0.22f -> 1f
        else -> ((expandProgress - 0.05f) / 0.17f).coerceIn(0f, 1f)
    }
    val downwardShiftDp = ((1f - expandProgress) * 190f)
    val bottomControlsAlpha = ((expandProgress - 0.25f) / 0.55f).coerceIn(0f, 1f)
    val minimizedCapsuleAlpha = ((0.32f - expandProgress) / 0.32f).coerceIn(0f, 1f)

    val statusText = resolveDavidLiveStatusLabel(
        connectionState = connectionState,
        activeSpeaker = activeSpeaker,
        isMicMuted = isMicMuted
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Full-screen & intermediate morphing layer (active when expandProgress > 0.02f)
        if (expandProgress > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backdropAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                LiveScreenBgTop,
                                LiveScreenBgBottom
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .then(
                        if (isEmbeddedInModalCard) {
                            Modifier
                        } else {
                            Modifier
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        }
                    )
                    .testTag("david_live_sheet")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = if (isEmbeddedInModalCard) 18.dp else 22.dp,
                            vertical = if (isEmbeddedInModalCard) 14.dp else 16.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top Navigation & Identity Header (Step 1: `<` minimize, David Live • LIVE, settings)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(topHeaderAlpha)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Minimize `<` button (collapses to compact bottom capsule)
                            IconButton(
                                onClick = onMinimize,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF111927))
                                    .border(1.dp, Color(0xFF1F2D42), CircleShape)
                                    .testTag("david_live_minimize_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Свернуть David Live в компактный режим",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Center "David Live • LIVE" pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0E1624))
                                    .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "David Live",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (connectionState) {
                                                DavidLiveConnectionState.ERROR,
                                                DavidLiveConnectionState.NO_API_KEY -> ErrorCrimson
                                                DavidLiveConnectionState.DAVID_SPEAKING -> CyanSpeaking
                                                else -> EmeraldStatus
                                            }
                                        )
                                )
                                Text(
                                    text = "LIVE",
                                    color = when (connectionState) {
                                        DavidLiveConnectionState.ERROR,
                                        DavidLiveConnectionState.NO_API_KEY -> ErrorCrimson
                                        DavidLiveConnectionState.DAVID_SPEAKING -> CyanSpeaking
                                        else -> EmeraldStatus
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            IconButton(
                                onClick = onOpenApiKeySettings,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF111927))
                                    .border(1.dp, Color(0xFF1F2D42), CircleShape)
                                    .testTag("david_live_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Настройки API-ключа и голоса",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isEmbeddedInModalCard) 10.dp else 14.dp))

                        // David Avatar + "Давид Жабов" / "AI-помощник по финансам" header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF10B981).copy(alpha = 0.35f),
                                                Color(0xFF0B1522)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = when (connectionState) {
                                            DavidLiveConnectionState.DAVID_SPEAKING -> CyanSpeaking
                                            DavidLiveConnectionState.ERROR,
                                            DavidLiveConnectionState.NO_API_KEY -> ErrorCrimson
                                            else -> EmeraldStatus.copy(alpha = 0.72f)
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🐸",
                                    fontSize = 24.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Давид Жабов",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "AI-помощник по финансам",
                                    color = Color(0xFF7E92AC),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isEmbeddedInModalCard) 10.dp else 14.dp))

                        // Live speech bubble with pointer tail ("Говорите, я слушаю...")
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = if (isEmbeddedInModalCard) 48.dp else 54.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(SubtitleBubbleSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (connectionState == DavidLiveConnectionState.ERROR ||
                                            connectionState == DavidLiveConnectionState.NO_API_KEY
                                        ) {
                                            ErrorCrimson.copy(alpha = 0.45f)
                                        } else {
                                            SubtitleBubbleBorder
                                        },
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .padding(horizontal = 18.dp, vertical = if (isEmbeddedInModalCard) 11.dp else 14.dp)
                                    .testTag("david_live_subtitle_bubble")
                            ) {
                                Column {
                                    Text(
                                        text = errorMessage ?: liveSubtitle,
                                        color = if (errorMessage != null) Color(0xFFFCA5A5) else Color(0xFFF1F5F9),
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 21.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    AnimatedVisibility(
                                        visible = connectionState == DavidLiveConnectionState.ERROR ||
                                            connectionState == DavidLiveConnectionState.NO_API_KEY,
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(top = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (connectionState == DavidLiveConnectionState.ERROR) {
                                                Button(
                                                    onClick = onRetryConnection,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF1E293B),
                                                        contentColor = EmeraldStatus
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Повторить", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            if (connectionState == DavidLiveConnectionState.NO_API_KEY) {
                                                Button(
                                                    onClick = onOpenApiKeySettings,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = EmeraldStatus,
                                                        contentColor = Color(0xFF061019)
                                                    ),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text("Указать API ключ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Speech bubble pointer tail
                            Canvas(
                                modifier = Modifier
                                    .padding(start = 26.dp)
                                    .size(width = 16.dp, height = 9.dp)
                            ) {
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(size.width, 0f)
                                    lineTo(size.width * 0.35f, size.height)
                                    close()
                                }
                                drawPath(path = path, color = SubtitleBubbleSurface)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 2. Center Morphing Visualizer Area (Steps 1..4 & 7..9)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, downwardShiftDp.dp.roundToPx()) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Stage A: 48-Ray Circular Visualizer (Steps 1, 2, 7, 8)
                        if (circularAlpha > 0.01f) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .alpha(circularAlpha)
                                    .scale(circularScale)
                            ) {
                                DavidLiveCircularVisualizer(
                                    rayMagnitudes = rayMagnitudes,
                                    overallEnergy = overallEnergy,
                                    connectionState = connectionState,
                                    activeSpeaker = activeSpeaker,
                                    isMicMuted = isMicMuted,
                                    onToggleMicMute = onToggleMicMute,
                                    visualizerSize = if (isEmbeddedInModalCard) 244.dp else 278.dp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = statusText,
                                    color = if (isMicMuted) Color(0xFFFCA5A5) else Color(0xFF94A3B8),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Stage B: Morphing Horizontal Spectral Wave + Frog Badge (Steps 3, 4, 9)
                        if (intermediateWaveAlpha > 0.02f && expandProgress < 0.85f) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(intermediateWaveAlpha)
                                    .padding(horizontal = 16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0D2329))
                                        .border(1.5.dp, EmeraldStatus.copy(alpha = 0.75f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🐸", fontSize = 26.sp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                DavidLiveHorizontalWaveVisualizer(
                                    rayMagnitudes = rayMagnitudes,
                                    overallEnergy = overallEnergy,
                                    connectionState = connectionState,
                                    activeSpeaker = activeSpeaker,
                                    isMicMuted = isMicMuted,
                                    barCount = 28,
                                    modifier = Modifier
                                        .fillMaxWidth(0.88f)
                                        .height(64.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Давид Жабов",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = statusText,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 3. Bottom Control Bar (Mute, Red End Call / Отменить, Minimize to Chat)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(bottomControlsAlpha)
                            .padding(bottom = if (isEmbeddedInModalCard) 4.dp else 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Mic Mute Toggle Button
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isMicMuted) ErrorCrimson.copy(alpha = 0.18f) else CancelPillSurface
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isMicMuted) ErrorCrimson.copy(alpha = 0.72f) else CancelPillBorder,
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = onToggleMicMute
                                    )
                                    .testTag("david_live_bottom_mute_button")
                                    .semantics {
                                        contentDescription = if (isMicMuted) "Включить микрофон" else "Выключить микрофон"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (isMicMuted) ErrorCrimson else Color(0xFFCBD5E1),
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Centered end-call button without the redundant close icon
                            Box(
                                modifier = Modifier
                                    .height(54.dp)
                                    .width(160.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(27.dp),
                                        ambientColor = ErrorCrimson,
                                        spotColor = ErrorCrimson
                                    )
                                    .clip(RoundedCornerShape(27.dp))
                                    .background(ErrorCrimson)
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFF87171).copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(27.dp)
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = onEndSession
                                    )
                                    .testTag("david_live_cancel_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Отменить",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Right: Minimize to Chat / Compact Capsule Button
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(CancelPillSurface)
                                    .border(1.dp, CancelPillBorder, CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = { (onMinimizeToChat ?: onMinimize)() }
                                    )
                                    .testTag("david_live_chat_minimize_button")
                                    .semantics {
                                        contentDescription = "Свернуть в чат"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onMinimize
                                )
                        )
                    }
                }
            }
        }

        // Embedded Minimized Capsule at the bottom of the screen when in MINIMIZED state
        if (renderEmbeddedMinimizedCapsule &&
            (displayMode == DavidLiveDisplayMode.MINIMIZED || (expandProgress < 0.32f && displayMode != DavidLiveDisplayMode.HIDDEN))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = minimizedBottomOffsetDp)
                    .alpha(minimizedCapsuleAlpha),
                contentAlignment = Alignment.BottomCenter
            ) {
                DavidLiveMinimizedCapsule(
                    connectionState = connectionState,
                    rayMagnitudes = rayMagnitudes,
                    overallEnergy = overallEnergy,
                    activeSpeaker = activeSpeaker,
                    isMicMuted = isMicMuted,
                    onExpand = onExpand,
                    onToggleMicMute = onToggleMicMute,
                    onEndSession = onEndSession
                )
            }
        }
    }
}
