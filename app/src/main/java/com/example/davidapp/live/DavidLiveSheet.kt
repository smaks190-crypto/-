package com.example.davidapp.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.vector.ImageVector
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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.example.ui.components.BrandFrogAvatar
import kotlinx.coroutines.delay
import kotlin.math.cos

private val LiveScreenBgTop = Color(0xFF060A12)
private val LiveScreenBgBottom = Color(0xFF090F1B)
private val SubtitleBubbleSurface = Color(0xFF101826)
private val SubtitleBubbleBorder = Color(0xFF1E2D42)
private val CapsuleSurfaceStart = Color(0xFF0B121E)
private val CapsuleSurfaceEnd = Color(0xFF101928)
private val CancelPillSurface = Color(0xFF111824)
private val CancelPillBorder = Color(0xFF223044)
private val EmeraldStatus = Color(0xFF00E5A8)
private val TurquoiseUser = Color(0xFF00D9F5)
private val CyanSpeaking = Color(0xFF00B4FF)
private val PeakMintWave = Color(0xFF00F0A0)
private val NeonAquaWave = Color(0xFF00D9F5)
private val IdleWaveSlate = Color(0xFF2A2F3A)
private val ErrorCrimson = Color(0xFFEF4444)
private val GoldApiMissing = Color(0xFFF59E0B)
private val PurpleCriticalError = Color(0xFFA855F7)
private val GreenSuccess = Color(0xFF22C55E)

/**
 * Resolves the human-readable Russian status label for the David Live session,
 * shared between the full-screen interface and the compact minimized capsule.
 */
fun resolveDavidLiveStatusLabel(
    connectionState: DavidLiveConnectionState,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    visualState: DavidLiveVisualState? = null,
    failureKind: DavidLiveConnectionFailureKind = DavidLiveConnectionFailureKind.NONE
): String {
    if (visualState == null && failureKind == DavidLiveConnectionFailureKind.NONE) {
        return resolveDavidLiveStatusLabel(connectionState, activeSpeaker, isMicMuted, DavidLiveVisualPhase.IDLE)
    }
    val effectiveVisualState = visualState ?: DavidLiveVisualStateResolver.resolve(
        connectionState = connectionState,
        activeSpeaker = activeSpeaker,
        overallEnergy = 0f,
        isMicMuted = isMicMuted,
        failureKind = failureKind
    )
    return when (effectiveVisualState) {
        DavidLiveVisualState.API_MISSING -> "Нет API-ключа"
        DavidLiveVisualState.OFFLINE -> "Связь прервалась"
        DavidLiveVisualState.ERROR -> {
            if (failureKind == DavidLiveConnectionFailureKind.INVALID_API_KEY) {
                "Неверный API-ключ"
            } else {
                "Ошибка соединения"
            }
        }
        DavidLiveVisualState.CONNECTING -> "Подключение..."
        DavidLiveVisualState.RECONNECTING -> "Переподключение..."
        DavidLiveVisualState.FINANCE_LOOKUP -> "Ищу финансы..."
        DavidLiveVisualState.ANALYZING -> "Анализирую данные..."
        DavidLiveVisualState.THINKING -> "Думаю..."
        DavidLiveVisualState.PREPARING_RESPONSE -> "Готовлю ответ..."
        DavidLiveVisualState.SUCCESS -> "Готово!"
        DavidLiveVisualState.DAVID_SPEAKING -> "Говорит Давид..."
        DavidLiveVisualState.USER_SPEAKING -> "Ты говоришь..."
        DavidLiveVisualState.IDLE_LISTENING -> {
            if (isMicMuted) "Микрофон выключен" else "Слушаю тебя..."
        }
    }
}

/**
 * Resolves the state icon for the top status badge and contextual cloud.
 */
private fun resolveVisualStateIcon(visualState: DavidLiveVisualState, isMicMuted: Boolean): ImageVector {
    return when (visualState) {
        DavidLiveVisualState.IDLE_LISTENING -> if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic
        DavidLiveVisualState.USER_SPEAKING -> Icons.Default.GraphicEq
        DavidLiveVisualState.THINKING -> Icons.Default.Psychology
        DavidLiveVisualState.FINANCE_LOOKUP -> Icons.Default.Search
        DavidLiveVisualState.ANALYZING -> Icons.Default.QueryStats
        DavidLiveVisualState.PREPARING_RESPONSE -> Icons.Default.Psychology
        DavidLiveVisualState.DAVID_SPEAKING -> Icons.Default.GraphicEq
        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING -> Icons.Default.Refresh
        DavidLiveVisualState.OFFLINE -> Icons.Default.WifiOff
        DavidLiveVisualState.API_MISSING -> Icons.Default.Key
        DavidLiveVisualState.ERROR -> Icons.Default.PriorityHigh
        DavidLiveVisualState.SUCCESS -> Icons.Default.Check
    }
}

/**
 * Resolves the primary and secondary accent colors (`Pair<Color, Color>`) for all 13
 * [DavidLiveVisualState] states across the full-screen sheet, compact capsule, and debug strip.
 */
internal fun resolveVisualStateColors(
    visualState: DavidLiveVisualState,
    isMicMuted: Boolean = false
): Pair<Color, Color> {
    if (shouldApplyMutedVisualPalette(visualState, isMicMuted)) {
        return MutedSlatePrimary to MutedSilverSecondary
    }
    return when (visualState) {
        DavidLiveVisualState.IDLE_LISTENING -> EmeraldStatus to PeakMintWave
        DavidLiveVisualState.USER_SPEAKING -> TurquoiseUser to CyanSpeaking
        DavidLiveVisualState.THINKING -> CyanSpeaking to TurquoiseUser
        DavidLiveVisualState.FINANCE_LOOKUP -> PeakMintWave to TurquoiseUser
        DavidLiveVisualState.ANALYZING -> CyanSpeaking to EmeraldStatus
        DavidLiveVisualState.PREPARING_RESPONSE -> TurquoiseUser to EmeraldStatus
        DavidLiveVisualState.DAVID_SPEAKING -> EmeraldStatus to TurquoiseUser
        DavidLiveVisualState.CONNECTING -> CyanSpeaking to TurquoiseUser
        DavidLiveVisualState.RECONNECTING -> CyanSpeaking to TurquoiseUser
        DavidLiveVisualState.OFFLINE -> ErrorCrimson to Color(0xFFF87171)
        DavidLiveVisualState.API_MISSING -> GoldApiMissing to Color(0xFFFBBF24)
        DavidLiveVisualState.ERROR -> PurpleCriticalError to Color(0xFFC084FC)
        DavidLiveVisualState.SUCCESS -> GreenSuccess to PeakMintWave
    }
}

/**
 * Horizontal multi-band spectral wave visualizer for the David Live morphing transition
 * and compact bottom voice capsule (`DavidLiveMinimizedCapsule`).
 */
@Composable
fun DavidLiveHorizontalWaveVisualizer(
    rayMagnitudes: List<Float>,
    overallEnergy: Float,
    connectionState: DavidLiveConnectionState,
    activeSpeaker: DavidLiveSpeaker,
    isMicMuted: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    visualState: DavidLiveVisualState? = null
) {
    val energyClamped = overallEnergy.coerceIn(0f, 1f)
    val effectiveVisualState = visualState ?: DavidLiveVisualStateResolver.resolve(
        connectionState = connectionState,
        activeSpeaker = activeSpeaker,
        overallEnergy = energyClamped,
        isMicMuted = isMicMuted
    )
    val isDavidSpeaking = effectiveVisualState == DavidLiveVisualState.DAVID_SPEAKING ||
        connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
        activeSpeaker == DavidLiveSpeaker.DAVID
    val isUserSpeaking = !isMicMuted && (
        effectiveVisualState == DavidLiveVisualState.USER_SPEAKING ||
            activeSpeaker == DavidLiveSpeaker.USER ||
            energyClamped > 0.08f
        )
    val isProcessing = effectiveVisualState == DavidLiveVisualState.THINKING ||
        effectiveVisualState == DavidLiveVisualState.FINANCE_LOOKUP ||
        effectiveVisualState == DavidLiveVisualState.ANALYZING ||
        effectiveVisualState == DavidLiveVisualState.PREPARING_RESPONSE
    val isIdleListeningUnmuted = effectiveVisualState == DavidLiveVisualState.IDLE_LISTENING && !isMicMuted
    val isVoiceOrProcessActive = (isDavidSpeaking || isUserSpeaking || isProcessing || isIdleListeningUnmuted) &&
        effectiveVisualState != DavidLiveVisualState.OFFLINE &&
        effectiveVisualState != DavidLiveVisualState.ERROR &&
        effectiveVisualState != DavidLiveVisualState.API_MISSING

    // Smooth 380ms decay so wave bars never vanish abruptly
    val waveActivity by animateFloatAsState(
        targetValue = when {
            isDavidSpeaking || isUserSpeaking -> 1f
            isProcessing -> 0.52f
            isIdleListeningUnmuted -> 0.50f
            else -> 0.12f
        },
        animationSpec = tween(
            durationMillis = if (isDavidSpeaking || isUserSpeaking) 75 else 380,
            easing = FastOutSlowInEasing
        ),
        label = "horizontal_wave_activity"
    )

    // Continuous 60fps wave phase so the horizontal visualizer animates fluidly during speech
    val infiniteTransition = rememberInfiniteTransition(label = "horizontal_wave_phase_transition")
    val wavePhaseRad by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "horizontal_wave_phase_rad"
    )

    val radialEnvelope = remember { DavidLiveSpectrumAnalyzer().getRadialSpatialEnvelope() }
    val (targetPrimaryColor, targetSecondaryColor) = remember(effectiveVisualState, isMicMuted) {
        resolveVisualStateColors(effectiveVisualState, isMicMuted)
    }
    val statePrimaryColor by animateColorAsState(
        targetValue = targetPrimaryColor,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "wave_primary_color"
    )
    val stateSecondaryColor by animateColorAsState(
        targetValue = targetSecondaryColor,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "wave_secondary_color"
    )

    val totalBars = barCount.coerceIn(12, 36)
    val smoothedBarMagnitudes = remember(totalBars) { FloatArray(totalBars) { 0.08f } }

    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "Горизонтальная звуковая волна David Live"
            }
    ) {
        val widthPx = size.width
        val heightPx = size.height
        val centerY = heightPx / 2f
        val stepX = widthPx / (totalBars + 1).toFloat()
        val strokeWidth = (stepX * 0.48f).coerceIn(2.0.dp.toPx(), 4.0.dp.toPx())
        val minBarHalfHeight = (heightPx * 0.08f).coerceAtLeast(1.8.dp.toPx())
        val maxBarHalfHeight = (heightPx * 0.47f).coerceAtLeast(minBarHalfHeight + 2f)

        for (i in 0 until totalBars) {
            val normPos = (i + 0.5f) / totalBars.toFloat()
            val centerEnvelope = sin(PI * normPos.toDouble()).toFloat().pow(1.22f)

            val targetMagnitude = computeHorizontalWaveBarTargetMagnitude(
                barIndex = i,
                totalBars = totalBars,
                rayMagnitudes = rayMagnitudes,
                radialEnvelope = radialEnvelope,
                overallEnergy = energyClamped,
                wavePhaseRad = wavePhaseRad,
                waveActivity = waveActivity,
                isMicMuted = isMicMuted,
                isUserSpeaking = isUserSpeaking,
                isDavidSpeaking = isDavidSpeaking,
                isProcessing = isProcessing,
                isIdleListeningUnmuted = isIdleListeningUnmuted
            )

            // Fast elastic attack (0.58f) when speaking, smooth organic decay (0.24f)
            val prevMag = smoothedBarMagnitudes[i]
            val smoothingFactor = if (targetMagnitude > prevMag) 0.58f else 0.24f
            val activeMagnitude = (prevMag + (targetMagnitude - prevMag) * smoothingFactor).coerceIn(0.06f, 1f)
            smoothedBarMagnitudes[i] = activeMagnitude

            val halfBar = minBarHalfHeight + (maxBarHalfHeight - minBarHalfHeight) * activeMagnitude
            val x = stepX * (i + 1)

            val barColor = when {
                effectiveVisualState == DavidLiveVisualState.OFFLINE -> ErrorCrimson.copy(alpha = 0.65f)
                effectiveVisualState == DavidLiveVisualState.API_MISSING -> GoldApiMissing.copy(alpha = 0.68f)
                effectiveVisualState == DavidLiveVisualState.ERROR -> ErrorCrimson.copy(alpha = 0.68f)
                isMicMuted && !isDavidSpeaking -> IdleWaveSlate.copy(alpha = 0.58f)
                !isVoiceOrProcessActive -> statePrimaryColor.copy(alpha = 0.46f)
                else -> lerp(
                    statePrimaryColor,
                    stateSecondaryColor,
                    (centerEnvelope * 0.65f + activeMagnitude * 0.35f).coerceIn(0f, 1f)
                )
            }

            val topY = centerY - halfBar
            val bottomY = centerY + halfBar

            // Soft diffused per-bar aura instead of a rectangular background glow
            if (waveActivity > 0.18f && (!isMicMuted || isDavidSpeaking || isProcessing)) {
                val haloHalfBar = (halfBar * 1.32f).coerceAtMost(heightPx * 0.49f)
                val haloAlpha = (0.08f + 0.18f * activeMagnitude * centerEnvelope) * waveActivity
                drawLine(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.28f to statePrimaryColor.copy(alpha = haloAlpha * 0.65f),
                        0.50f to barColor.copy(alpha = haloAlpha),
                        0.72f to statePrimaryColor.copy(alpha = haloAlpha * 0.65f),
                        1.0f to Color.Transparent,
                        startY = centerY - haloHalfBar,
                        endY = centerY + haloHalfBar
                    ),
                    start = Offset(x, centerY - haloHalfBar),
                    end = Offset(x, centerY + haloHalfBar),
                    strokeWidth = strokeWidth * 2.35f,
                    cap = StrokeCap.Round
                )
            }

            // Soft vertical gradient bar with tapered luminous tips
            drawLine(
                brush = Brush.verticalGradient(
                    0.0f to barColor.copy(alpha = barColor.alpha * 0.42f),
                    0.22f to barColor.copy(alpha = barColor.alpha * 0.88f),
                    0.50f to lerp(barColor, Color.White, 0.22f * activeMagnitude).copy(alpha = barColor.alpha),
                    0.78f to barColor.copy(alpha = barColor.alpha * 0.88f),
                    1.0f to barColor.copy(alpha = barColor.alpha * 0.42f),
                    startY = topY,
                    endY = bottomY
                ),
                start = Offset(x, topY),
                end = Offset(x, bottomY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Compact bottom voice capsule for **David Live** (`DavidLiveDisplayMode.MINIMIZED`).
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
    embeddedInBottomBar: Boolean = false,
    visualPhase: DavidLiveVisualPhase? = null,
    visualState: DavidLiveVisualState? = null,
    failureKind: DavidLiveConnectionFailureKind = DavidLiveConnectionFailureKind.NONE
) {
    val energyClamped = overallEnergy.coerceIn(0f, 1f)
    val effectiveVisualState = remember(
        visualState,
        connectionState,
        activeSpeaker,
        energyClamped,
        isMicMuted,
        failureKind
    ) {
        visualState ?: DavidLiveVisualStateResolver.resolve(
            connectionState = connectionState,
            activeSpeaker = activeSpeaker,
            overallEnergy = energyClamped,
            isMicMuted = isMicMuted,
            failureKind = failureKind
        )
    }

    val isDavidSpeaking = effectiveVisualState == DavidLiveVisualState.DAVID_SPEAKING ||
        connectionState == DavidLiveConnectionState.DAVID_SPEAKING ||
        activeSpeaker == DavidLiveSpeaker.DAVID

    val statusText = resolveDavidLiveStatusLabel(
        connectionState = connectionState,
        activeSpeaker = activeSpeaker,
        isMicMuted = isMicMuted,
        visualState = effectiveVisualState,
        failureKind = failureKind
    )

    val (statePrimaryColor, _) = remember(effectiveVisualState, isMicMuted) {
        resolveVisualStateColors(effectiveVisualState, isMicMuted)
    }

    val capsuleCornerRadius = if (embeddedInBottomBar) 22.dp else 26.dp

    val capsuleBorderColor = when (effectiveVisualState) {
        DavidLiveVisualState.OFFLINE -> ErrorCrimson.copy(alpha = 0.72f)
        DavidLiveVisualState.API_MISSING -> GoldApiMissing.copy(alpha = 0.72f)
        DavidLiveVisualState.ERROR -> PurpleCriticalError.copy(alpha = 0.72f)
        else -> if (isMicMuted && !isDavidSpeaking) {
            Color(0xFF283548)
        } else {
            statePrimaryColor.copy(alpha = 0.50f + 0.30f * energyClamped)
        }
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
                        ambientColor = statePrimaryColor.copy(alpha = 0.45f),
                        spotColor = statePrimaryColor.copy(alpha = 0.55f)
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
                                    statePrimaryColor.copy(alpha = 0.28f),
                                    Color(0xFF0D2329),
                                    Color(0xFF09131C)
                                )
                            )
                        )
                        .border(
                            width = 1.2.dp,
                            color = statePrimaryColor.copy(alpha = 0.72f),
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
                                .background(statePrimaryColor)
                        )
                        Text(
                            text = "LIVE",
                            color = statePrimaryColor,
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
                visualState = effectiveVisualState,
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
                val micBgColor = when {
                    isMicMuted -> ErrorCrimson.copy(alpha = 0.20f)
                    isDavidSpeaking -> CyanSpeaking.copy(alpha = 0.20f)
                    else -> statePrimaryColor.copy(alpha = 0.16f)
                }
                val micBorderColor = when {
                    isMicMuted -> ErrorCrimson.copy(alpha = 0.75f)
                    else -> statePrimaryColor.copy(alpha = 0.72f)
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
 * Full-screen voice conversation screen for **David Live** (Issue #56 Redesign):
 * - Centralized 13-state visual model ([DavidLiveVisualState] + [DavidLiveVisualMetadata]);
 * - Interactive 3D glowing Mesh Sphere ([DavidLiveCircularVisualizer]) with direct tap-to-reconnect
 *   in `OFFLINE` and tap-to-configure-API in `API_MISSING` / `INVALID_API_KEY`;
 * - Contextual status cloud (`david_live_subtitle_bubble`) with state badge, short title,
 *   contextual explanation, and action pill (`"Повторить"`, `"Добавить API"`, `"Настроить API"`);
 * - Debug/Test state switcher (`enableDebugStateSwitcher`) to manually inspect all 13 visual states
 *   and transitions without a live Gemini connection.
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
    minimizedBottomOffsetDp: Dp = 14.dp,
    visualPhase: DavidLiveVisualPhase? = null,
    visualState: DavidLiveVisualState? = null,
    failureKind: DavidLiveConnectionFailureKind = DavidLiveConnectionFailureKind.NONE,
    activeToolName: String? = null,
    debugOverrideVisualState: DavidLiveVisualState? = null,
    onSelectDebugVisualState: ((DavidLiveVisualState?) -> Unit)? = null,
    enableDebugStateSwitcher: Boolean = false
) {
    val context = LocalContext.current

    // Local fallback override when tested standalone in Compose UI tests without a controller callback
    var localDebugOverride by remember { mutableStateOf<DavidLiveVisualState?>(null) }
    val activeDebugOverride = if (enableDebugStateSwitcher) {
        debugOverrideVisualState ?: localDebugOverride
    } else {
        null
    }
    var isDebugBarExpanded by remember { mutableStateOf(false) }

    val effectiveVisualState = remember(
        activeDebugOverride,
        visualState,
        connectionState,
        activeSpeaker,
        overallEnergy,
        isMicMuted,
        failureKind
    ) {
        activeDebugOverride
            ?: visualState
            ?: DavidLiveVisualStateResolver.resolve(
                connectionState = connectionState,
                activeSpeaker = activeSpeaker,
                overallEnergy = overallEnergy,
                isMicMuted = isMicMuted,
                failureKind = failureKind
            )
    }

    val effectiveFailureKind = remember(effectiveVisualState, failureKind) {
        when {
            failureKind != DavidLiveConnectionFailureKind.NONE -> failureKind
            effectiveVisualState == DavidLiveVisualState.OFFLINE -> DavidLiveConnectionFailureKind.NETWORK_OFFLINE
            effectiveVisualState == DavidLiveVisualState.API_MISSING -> DavidLiveConnectionFailureKind.MISSING_API_KEY
            else -> DavidLiveConnectionFailureKind.NONE
        }
    }

    val visualMetadata = remember(
        effectiveVisualState,
        effectiveFailureKind,
        isMicMuted,
        liveSubtitle,
        errorMessage,
        activeToolName
    ) {
        DavidLiveVisualStateResolver.resolveMetadata(
            visualState = effectiveVisualState,
            failureKind = effectiveFailureKind,
            isMicMuted = isMicMuted,
            liveSubtitle = liveSubtitle,
            errorMessage = errorMessage,
            activeToolName = activeToolName
        )
    }

    val (targetStatePrimaryColor, targetStateSecondaryColor) = remember(effectiveVisualState, isMicMuted) {
        resolveVisualStateColors(effectiveVisualState, isMicMuted)
    }
    val statePrimaryColor by animateColorAsState(
        targetValue = targetStatePrimaryColor,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "sheet_state_primary_color"
    )
    val stateSecondaryColor by animateColorAsState(
        targetValue = targetStateSecondaryColor,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "sheet_state_secondary_color"
    )

    // Bind hardware volume buttons to the Android 17 AI Assistant / Neural Network volume channel
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

    BackHandler(enabled = displayMode == DavidLiveDisplayMode.EXPANDED) {
        onMinimize()
    }

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

    val backdropAlpha = ((expandProgress - 0.08f) / 0.92f).coerceIn(0f, 1f)
    val topHeaderAlpha = ((expandProgress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val circularAlpha = ((expandProgress - 0.42f) / 0.42f).coerceIn(0f, 1f)
    val circularScale = 0.54f + (1f - 0.54f) * ((expandProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)

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
        isMicMuted = isMicMuted,
        visualState = effectiveVisualState,
        failureKind = effectiveFailureKind
    )

    // Contextual synthetic energy for debug preview of USER_SPEAKING / DAVID_SPEAKING when no real mic input is active
    val effectiveEnergy = if (activeDebugOverride == DavidLiveVisualState.USER_SPEAKING ||
        activeDebugOverride == DavidLiveVisualState.DAVID_SPEAKING
    ) {
        overallEnergy.coerceAtLeast(0.62f)
    } else {
        overallEnergy
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
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
                    // Block background taps without merging the whole screen into one accessibility button.
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
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
                            vertical = if (isEmbeddedInModalCard) 12.dp else 14.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top Navigation & Identity Header + Contextual Cloud
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
                            // Minimize `<` button
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

                            // Center "David Live • LIVE" pill with live state indicator dot
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF0E1624))
                                    .border(
                                        width = 1.dp,
                                        color = statePrimaryColor.copy(alpha = 0.42f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
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
                                        .background(statePrimaryColor)
                                )
                                Text(
                                    text = "LIVE",
                                    color = statePrimaryColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (enableDebugStateSwitcher) {
                                    IconButton(
                                        onClick = { isDebugBarExpanded = !isDebugBarExpanded },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (activeDebugOverride != null || isDebugBarExpanded) {
                                                    statePrimaryColor.copy(alpha = 0.22f)
                                                } else {
                                                    Color(0xFF111927)
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (activeDebugOverride != null) {
                                                    statePrimaryColor
                                                } else {
                                                    Color(0xFF1F2D42)
                                                },
                                                shape = CircleShape
                                            )
                                            .testTag("david_live_debug_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BugReport,
                                            contentDescription = "Тестовый переключатель состояний визуализатора",
                                            tint = if (activeDebugOverride != null) statePrimaryColor else Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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
                        }

                        // Optional Debug / Preview State Switcher Strip (only in Debug/Test mode)
                        AnimatedVisibility(
                            visible = enableDebugStateSwitcher && isDebugBarExpanded,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            DavidLiveDebugStateStrip(
                                selectedState = activeDebugOverride,
                                onSelectState = { newState ->
                                    localDebugOverride = newState
                                    onSelectDebugVisualState?.invoke(newState)
                                },
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .testTag("david_live_debug_state_switcher")
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isEmbeddedInModalCard) 8.dp else 12.dp))

                        // David Avatar + "Давид Жабов" / "AI-помощник по финансам" + State Badge Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    statePrimaryColor.copy(alpha = 0.35f),
                                                    Color(0xFF0B1522)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = statePrimaryColor.copy(alpha = 0.78f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🐸",
                                        fontSize = 23.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(11.dp))

                                Column {
                                    Text(
                                        text = "Давид Жабов",
                                        color = Color.White,
                                        fontSize = 17.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "AI-помощник по финансам",
                                        color = Color(0xFF7E92AC),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // State Header Pill matching the top-of-card badge in the reference design
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(statePrimaryColor.copy(alpha = 0.14f))
                                    .border(
                                        width = 1.dp,
                                        color = statePrimaryColor.copy(alpha = 0.48f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("david_live_state_badge")
                            ) {
                                Icon(
                                    imageVector = resolveVisualStateIcon(effectiveVisualState, isMicMuted),
                                    contentDescription = null,
                                    tint = statePrimaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = visualMetadata.title,
                                    color = statePrimaryColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isEmbeddedInModalCard) 8.dp else 12.dp))

                        // Connection problems have a single quiet notice. The speech bubble is for dialogue only.
                        val hasConnectionProblem = effectiveVisualState == DavidLiveVisualState.OFFLINE ||
                            effectiveVisualState == DavidLiveVisualState.API_MISSING ||
                            effectiveVisualState == DavidLiveVisualState.ERROR
                        if (hasConnectionProblem) {
                            Surface(
                                color = com.example.ui.theme.ObsidianSurface,
                                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorder),
                                modifier = Modifier.fillMaxWidth().testTag("david_live_connection_notice")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = when (effectiveVisualState) {
                                            DavidLiveVisualState.API_MISSING -> "Для голосового диалога добавьте личный ключ в настройках."
                                            DavidLiveVisualState.OFFLINE -> "Нет соединения. Проверьте интернет."
                                            else -> "Голосовой диалог недоступен. Попробуйте подключиться снова."
                                        },
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        color = com.example.ui.theme.Slate300,
                                        modifier = Modifier.weight(1f)
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            if (visualMetadata.sphereTapOpensApiSettings) onOpenApiKeySettings()
                                            else onRetryConnection()
                                        },
                                        modifier = Modifier.testTag("david_live_contextual_action_button")
                                    ) {
                                        Text(if (visualMetadata.sphereTapOpensApiSettings) "Настройки" else "Повторить")
                                    }
                                }
                            }
                        }
                        val speech = resolveDavidLiveSpeech(effectiveVisualState, liveSubtitle)
                        if (speech != null) {
                            Surface(
                                color = com.example.ui.theme.ObsidianSurface,
                                shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, com.example.ui.theme.GlassBorder),
                                modifier = Modifier.fillMaxWidth().testTag("david_live_subtitle_bubble")
                            ) {
                                Text(
                                    text = extractActiveSpeechBubbleSegment(speech).activeText,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                    color = com.example.ui.theme.Slate100,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 2. Center Morphing 3D Mesh Sphere Visualizer Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, downwardShiftDp.dp.roundToPx()) },
                        contentAlignment = Alignment.Center
                    ) {
                        // Stage A: 3D Mesh Sphere Visualizer
                        if (circularAlpha > 0.01f) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .alpha(circularAlpha)
                                    .scale(circularScale)
                            ) {
                                DavidLiveCircularVisualizer(
                                    rayMagnitudes = rayMagnitudes,
                                    overallEnergy = effectiveEnergy,
                                    connectionState = connectionState,
                                    activeSpeaker = activeSpeaker,
                                    isMicMuted = isMicMuted,
                                    onToggleMicMute = onToggleMicMute,
                                    visualizerSize = if (isEmbeddedInModalCard) 244.dp else 278.dp,
                                    visualState = effectiveVisualState,
                                    failureKind = effectiveFailureKind,
                                    onRetryConnection = onRetryConnection,
                                    onOpenApiKeySettings = onOpenApiKeySettings
                                )


                            }
                        }

                        // Stage B: Morphing Horizontal Spectral Wave + Frog Badge (during minimize/expand transition)
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
                                        .border(1.5.dp, statePrimaryColor.copy(alpha = 0.75f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "🐸", fontSize = 26.sp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                DavidLiveHorizontalWaveVisualizer(
                                    rayMagnitudes = rayMagnitudes,
                                    overallEnergy = effectiveEnergy,
                                    connectionState = connectionState,
                                    activeSpeaker = activeSpeaker,
                                    isMicMuted = isMicMuted,
                                    barCount = 28,
                                    visualState = effectiveVisualState,
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
                                        if (isMicMuted) Color(0xFF94A3B8).copy(alpha = 0.16f) else CancelPillSurface
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isMicMuted) Color(0xFF94A3B8).copy(alpha = 0.64f) else CancelPillBorder,
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

                            // Centered end-call button
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
                    overallEnergy = effectiveEnergy,
                    activeSpeaker = activeSpeaker,
                    isMicMuted = isMicMuted,
                    onExpand = onExpand,
                    onToggleMicMute = onToggleMicMute,
                    onEndSession = onEndSession,
                    visualState = effectiveVisualState,
                    failureKind = effectiveFailureKind
                )
            }
        }
    }
}

/**
 * Horizontal scrollable debug/preview strip allowing developers and QA to manually inspect
 * all 13 [DavidLiveVisualState] states and verify smooth transitions without a live Gemini WebSocket.
 */
@Composable
fun DavidLiveDebugStateStrip(
    selectedState: DavidLiveVisualState?,
    onSelectState: (DavidLiveVisualState?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val allStates = remember { DavidLiveVisualState.values().toList() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B1320))
            .border(1.dp, Color(0xFF1E2D42), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "AUTO (Live)" chip
        val isAuto = selectedState == null
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isAuto) EmeraldStatus.copy(alpha = 0.22f) else Color(0xFF131E30))
                .border(
                    width = 1.dp,
                    color = if (isAuto) EmeraldStatus else Color(0xFF25354D),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onSelectState(null) }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("david_live_debug_chip_AUTO")
        ) {
            Text(
                text = "АВТО",
                color = if (isAuto) EmeraldStatus else Color(0xFF94A3B8),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        allStates.forEach { state ->
            val isSelected = selectedState == state
            val (chipColor, _) = resolveVisualStateColors(state, isMicMuted = false)
            val meta = DavidLiveVisualStateResolver.resolveMetadata(state)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) chipColor.copy(alpha = 0.24f) else Color(0xFF131E30))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) chipColor else Color(0xFF25354D),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelectState(state) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("david_live_debug_chip_${state.name}")
            ) {
                Text(
                    text = meta.title,
                    color = if (isSelected) chipColor else Color(0xFFCBD5E1),
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

internal fun isDefaultStatusSubtitlePlaceholder(text: String?): Boolean {
    val trimmed = text?.trim().orEmpty()
    return trimmed.isEmpty() ||
        trimmed == "Я здесь, слушаю тебя" ||
        trimmed == "Слушаю..." ||
        trimmed == "Говорю..." ||
        trimmed == "Вот что я думаю..." ||
        trimmed == "Говорите, я слушаю..." ||
        trimmed == "Давид отвечает..." ||
        trimmed == "Слушаю вас" ||
        trimmed == "Слушаю вас..." ||
        trimmed == "Слушаю тебя..." ||
        trimmed == "Микрофон отключён" ||
        trimmed == "Подключаюсь к Давиду..."
}
internal fun computeHorizontalWaveBarTargetMagnitude(
    barIndex: Int,
    totalBars: Int,
    rayMagnitudes: List<Float>,
    radialEnvelope: FloatArray,
    overallEnergy: Float,
    wavePhaseRad: Float,
    waveActivity: Float,
    isMicMuted: Boolean,
    isUserSpeaking: Boolean,
    isDavidSpeaking: Boolean,
    isProcessing: Boolean,
    isIdleListeningUnmuted: Boolean
): Float {
    if (isMicMuted && !isDavidSpeaking && !isProcessing) {
        return 0.06f
    }

    val normPos = (barIndex + 0.5f) / totalBars.coerceAtLeast(1).toFloat()
    val centerEnvelope = sin(PI * normPos.toDouble()).toFloat().pow(1.22f)
    val distFromCenter = kotlin.math.abs(normPos - 0.5f) * 2f

    val rayCount = DavidLiveSpectrumAnalyzer.RAY_COUNT
    // Направляем центральные столбики на голосовые форманты (индексы 6..20 и 30..44 соответствуют 140–2800 Гц),
    // где сосредоточена основная энергия живого голоса пользователя
    val vocalRayIdx = (8 + (distFromCenter * 12f).roundToInt() + (if (normPos >= 0.5f) 0 else 24)) % rayCount
    val neighborRayIdx = (vocalRayIdx + 2) % rayCount
    val highRayIdx = ((distFromCenter * 12f).roundToInt() + (if (normPos >= 0.5f) 0 else 24)) % rayCount

    val envVocal = radialEnvelope.getOrElse(vocalRayIdx) { 0.65f }.coerceAtLeast(0.38f)
    val envNeighbor = radialEnvelope.getOrElse(neighborRayIdx) { 0.65f }.coerceAtLeast(0.38f)
    val envHigh = radialEnvelope.getOrElse(highRayIdx) { 0.70f }.coerceAtLeast(0.38f)
    val vocalRay = maxOf(
        (rayMagnitudes.getOrElse(vocalRayIdx) { 0.06f } / envVocal).coerceIn(0f, 1f),
        (rayMagnitudes.getOrElse(neighborRayIdx) { 0.06f } / envNeighbor).coerceIn(0f, 1f) * 0.92f
    )
    val highRay = (rayMagnitudes.getOrElse(highRayIdx) { 0.06f } / envHigh).coerceIn(0f, 1f)
    val rawRay = (0.76f * vocalRay + 0.24f * highRay).coerceIn(0f, 1f)

    var peakRay = 0f
    var sumRay = 0f
    for (r in rayMagnitudes) {
        if (r > peakRay) peakRay = r
        sumRay += r
    }
    val meanRay = if (rayMagnitudes.isNotEmpty()) sumRay / rayMagnitudes.size else 0f
    val rawVoiceEnergy = maxOf(overallEnergy.coerceIn(0f, 1f), peakRay * 0.90f, meanRay * 1.55f).coerceIn(0f, 1f)

    val isSpeakingActive = isUserSpeaking || isDavidSpeaking || (isIdleListeningUnmuted && rawVoiceEnergy > 0.045f)
    if (isSpeakingActive) {
        // Нелинейное усиление чувствительности для живого голоса (нормальная речь даёт 62%–96% амплитуды в центре)
        val curvedEnergy = rawVoiceEnergy.coerceAtLeast(0.08f).pow(0.50f).coerceIn(0.24f, 1f)
        val harmonicFloor = maxOf(rawRay, peakRay * 0.78f).coerceIn(0.12f, 1f)
        val combinedSignal = maxOf(
            curvedEnergy * 0.88f,
            0.44f * harmonicFloor.pow(0.65f) + 0.62f * curvedEnergy
        ).coerceIn(0.24f, 1f)

        // Бегущая артикуляционная волна создаёт живое движение соседних столбиков в такт слогам речи
        val waveA = ((sin(wavePhaseRad * 3.0 + barIndex * 0.78) + 1.0) * 0.5).toFloat()
        val waveB = ((cos(wavePhaseRad * 5.0 - barIndex * 1.24) + 1.0) * 0.5).toFloat()
        val dynamicArticulation = 0.74f + 0.52f * (0.62f * waveA + 0.38f * waveB)

        val shaped = combinedSignal * dynamicArticulation * (0.36f + 0.78f * centerEnvelope) * waveActivity
        return shaped.coerceIn(0.10f, 1f)
    }

    // Спокойное плавное дыхание волн в режиме ожидания (IDLE_LISTENING)
    val idleBreathing = 0.55f + 0.45f * ((sin(wavePhaseRad * 1.0 + barIndex * 0.65) + 1.0) * 0.5).toFloat()
    val idleContour = 0.10f + 0.24f * centerEnvelope * idleBreathing
    val baseSignal = maxOf(rawRay * 0.65f, if (isIdleListeningUnmuted) idleContour else 0.08f)
    return (baseSignal * (0.32f + 0.76f * centerEnvelope) * waveActivity).coerceIn(0.06f, 1f)
}
