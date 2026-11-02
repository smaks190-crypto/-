package com.example.davidapp.live

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.ui.components.BrandFrogAvatar
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private val LiveScreenBgTop = Color(0xFF04070E)
private val LiveScreenBgMid = Color(0xFF070D19)
private val LiveScreenBgBottom = Color(0xFF050912)
private val SubtitleBubbleSurface = Color(0xFF0E1624)
private val SubtitleBubbleBorder = Color(0xFF1E2D42)
private val CapsuleSurfaceStart = Color(0xFF0B121E)
private val CapsuleSurfaceEnd = Color(0xFF101928)
private val ControlCircleSurface = Color(0xFF121A28)
private val ControlCircleBorder = Color(0xFF223147)
private val EmeraldStatus = Color(0xFF00E5A8)
private val TurquoiseUser = Color(0xFF00D9F5)
private val CyanSpeaking = Color(0xFF00B4FF)
private val ThinkingVioletAccent = Color(0xFF8B5CF6)
private val ThinkingCyanAccent = Color(0xFF38BDF8)
private val PeakMintWave = Color(0xFF00F0A0)
private val NeonAquaWave = Color(0xFF00D9F5)
private val IdleWaveSlate = Color(0xFF64748B)
private val ErrorCrimson = Color(0xFFEF4444)
private val GoldApiMissing = Color(0xFFF59E0B)
private val PurpleCriticalError = Color(0xFFA855F7)
private val CrimsonMagentaError = Color(0xFFF43F5E)
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
        DavidLiveVisualState.USER_SPEAKING -> {
            if (isMicMuted) "Микрофон выключен" else "Ты говоришь..."
        }
        DavidLiveVisualState.IDLE_LISTENING -> {
            if (isMicMuted) "Микрофон выключен" else "Слушаю тебя..."
        }
    }
}

/**
 * Resolves the state icon for the top status badge and under-sphere indicator.
 */
private fun resolveVisualStateIcon(visualState: DavidLiveVisualState, isMicMuted: Boolean): ImageVector {
    if (shouldApplyMutedVisualPalette(visualState, isMicMuted)) {
        return Icons.Default.MicOff
    }
    return when (visualState) {
        DavidLiveVisualState.IDLE_LISTENING -> Icons.Default.Mic
        DavidLiveVisualState.USER_SPEAKING -> Icons.Default.GraphicEq
        DavidLiveVisualState.THINKING -> Icons.Default.Psychology
        DavidLiveVisualState.FINANCE_LOOKUP -> Icons.Default.Search
        DavidLiveVisualState.ANALYZING -> Icons.Default.QueryStats
        DavidLiveVisualState.PREPARING_RESPONSE -> Icons.Default.Psychology
        DavidLiveVisualState.DAVID_SPEAKING -> Icons.Default.GraphicEq
        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING -> Icons.Default.Refresh
        DavidLiveVisualState.OFFLINE -> Icons.Default.WifiOff
        DavidLiveVisualState.API_MISSING -> Icons.Default.Warning
        DavidLiveVisualState.ERROR -> Icons.Default.PriorityHigh
        DavidLiveVisualState.SUCCESS -> Icons.Default.Check
    }
}

/**
 * Resolves the primary and secondary accent colors (`Pair<Color, Color>`) for all 13
 * [DavidLiveVisualState] states across the full-screen sheet, compact capsule, and debug strip.
 *
 * When `isMicMuted == true`, states governed by user microphone input (`IDLE_LISTENING`,
 * `USER_SPEAKING`, `THINKING`, `FINANCE_LOOKUP`, `ANALYZING`, `PREPARING_RESPONSE`)
 * transition to a distinct muted silver-slate palette (`MutedSlatePrimary` to `MutedSilverSecondary`),
 * while priority system states (`OFFLINE`, `API_MISSING`, `ERROR`, `CONNECTING`, `RECONNECTING`, `SUCCESS`)
 * and outgoing speech (`DAVID_SPEAKING`) preserve their own colors.
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
        DavidLiveVisualState.THINKING -> ThinkingVioletAccent to ThinkingCyanAccent
        DavidLiveVisualState.FINANCE_LOOKUP -> PeakMintWave to TurquoiseUser
        DavidLiveVisualState.ANALYZING -> ThinkingCyanAccent to ThinkingVioletAccent
        DavidLiveVisualState.PREPARING_RESPONSE -> TurquoiseUser to EmeraldStatus
        DavidLiveVisualState.DAVID_SPEAKING -> EmeraldStatus to TurquoiseUser
        DavidLiveVisualState.CONNECTING -> CyanSpeaking to TurquoiseUser
        DavidLiveVisualState.RECONNECTING -> CyanSpeaking to TurquoiseUser
        DavidLiveVisualState.OFFLINE -> ErrorCrimson to Color(0xFFF87171)
        DavidLiveVisualState.API_MISSING -> GoldApiMissing to Color(0xFFFBBF24)
        DavidLiveVisualState.ERROR -> CrimsonMagentaError to PurpleCriticalError
        DavidLiveVisualState.SUCCESS -> GreenSuccess to PeakMintWave
    }
}

/**
 * Horizontal multi-band spectral wave visualizer for the full-screen under-sphere audio wave,
 * morphing transition, and compact bottom voice capsule (`DavidLiveMinimizedCapsule`).
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
            energyClamped > 0.16f
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
            isProcessing -> 0.48f
            isIdleListeningUnmuted -> 0.42f
            else -> 0.12f
        },
        animationSpec = tween(
            durationMillis = if (isDavidSpeaking || isUserSpeaking) 95 else 380,
            easing = FastOutSlowInEasing
        ),
        label = "horizontal_wave_activity"
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
        val maxBarHalfHeight = (heightPx * 0.45f).coerceAtLeast(minBarHalfHeight + 2f)

        val rayCount = DavidLiveSpectrumAnalyzer.RAY_COUNT
        for (i in 0 until totalBars) {
            val normPos = (i + 0.5f) / totalBars.toFloat()
            val centerEnvelope = sin(PI * normPos.toDouble()).toFloat().pow(1.36f)

            val distFromCenter = kotlin.math.abs(normPos - 0.5f) * 2f
            val rayIdx = ((distFromCenter * 12f).roundToInt() + (if (normPos >= 0.5f) 0 else 24)) % rayCount
            val env = radialEnvelope.getOrElse(rayIdx) { 0.7f }.coerceAtLeast(0.38f)
            val rawRay = (rayMagnitudes.getOrElse(rayIdx) { 0.08f } / env).coerceIn(0f, 1f)

            // Gentle harmonic contour in idle listening so the waveform under the sphere looks alive
            val idleContour = 0.14f + 0.42f * centerEnvelope * (0.55f + 0.45f * kotlin.math.abs(sin((i * 0.85f).toDouble())).toFloat())
            val targetMagnitude = if (isMicMuted && !isDavidSpeaking && !isProcessing) {
                0.06f
            } else {
                val baseSignal = rawRay.coerceAtLeast(if (isIdleListeningUnmuted) idleContour else 0.09f)
                (baseSignal * (0.32f + 0.86f * centerEnvelope) * waveActivity).coerceIn(0.06f, 1f)
            }

            // Smooth per-bar temporal interpolation (fast attack, smooth decay)
            val prevMag = smoothedBarMagnitudes[i]
            val smoothingFactor = if (targetMagnitude > prevMag) 0.42f else 0.22f
            val activeMagnitude = (prevMag + (targetMagnitude - prevMag) * smoothingFactor).coerceIn(0.06f, 1f)
            smoothedBarMagnitudes[i] = activeMagnitude

            val halfBar = minBarHalfHeight + (maxBarHalfHeight - minBarHalfHeight) * activeMagnitude
            val x = stepX * (i + 1)

            val barColor = when {
                effectiveVisualState == DavidLiveVisualState.OFFLINE -> ErrorCrimson.copy(alpha = 0.65f)
                effectiveVisualState == DavidLiveVisualState.API_MISSING -> GoldApiMissing.copy(alpha = 0.68f)
                effectiveVisualState == DavidLiveVisualState.ERROR -> CrimsonMagentaError.copy(alpha = 0.68f)
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

    val (targetPrimaryColor, _) = remember(effectiveVisualState, isMicMuted) {
        resolveVisualStateColors(effectiveVisualState, isMicMuted)
    }
    val statePrimaryColor by animateColorAsState(
        targetValue = targetPrimaryColor,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "capsule_state_primary_color"
    )

    val capsuleCornerRadius = if (embeddedInBottomBar) 22.dp else 26.dp

    val capsuleBorderColor = when (effectiveVisualState) {
        DavidLiveVisualState.OFFLINE -> ErrorCrimson.copy(alpha = 0.72f)
        DavidLiveVisualState.API_MISSING -> GoldApiMissing.copy(alpha = 0.72f)
        DavidLiveVisualState.ERROR -> CrimsonMagentaError.copy(alpha = 0.72f)
        else -> if (isMicMuted && !isDavidSpeaking) {
            MutedSlatePrimary.copy(alpha = 0.55f)
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
                    BrandFrogAvatar(size = 28.dp)
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
                            MutedSilverSecondary
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
                    isMicMuted -> Color(0xFF263244)
                    isDavidSpeaking -> CyanSpeaking.copy(alpha = 0.20f)
                    else -> statePrimaryColor.copy(alpha = 0.16f)
                }
                val micBorderColor = when {
                    isMicMuted -> MutedSlatePrimary.copy(alpha = 0.78f)
                    else -> statePrimaryColor.copy(alpha = 0.72f)
                }
                val micIconTint = when {
                    isMicMuted -> MutedSilverSecondary
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
 * - Matches the approved Obsidian Neo-Bank "David Live — Живая сфера" design reference:
 *   1. Top Header with David Frog Avatar + Gold Coin badge, verified title, live status badge, and Settings gear;
 *   2. Central Translucent 3D Energy Sphere with multi-depth continuous orbital ribbons ([DavidLiveCircularVisualizer]);
 *   3. Under-Sphere Indicator (Audio Waveform / Orbital Spinner / State Icon), Status Label, and Contextual Cloud (`david_live_subtitle_bubble`);
 *   4. Bottom 3-Button Control Bar: Chat (`💬`), Large Focal Microphone Toggle (`🎤`/`🔇`), and End Call (`📞`).
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
    visualState: DavidLiveVisualState? = null,
    failureKind: DavidLiveConnectionFailureKind = DavidLiveConnectionFailureKind.NONE,
    activeToolName: String? = null,
    debugOverrideVisualState: DavidLiveVisualState? = null,
    onSelectDebugVisualState: ((DavidLiveVisualState?) -> Unit)? = null,
    enableDebugStateSwitcher: Boolean = com.example.BuildConfig.DEBUG
) {
    val context = LocalContext.current

    var localDebugOverride by remember { mutableStateOf<DavidLiveVisualState?>(null) }
    val activeDebugOverride = if (enableDebugStateSwitcher) {
        debugOverrideVisualState ?: localDebugOverride
    } else {
        null
    }
    var isDebugBarExpanded by remember { mutableStateOf(false) }
    var apiCloudHighlightCount by remember { mutableIntStateOf(0) }

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

    // Синхронизация смены фраз в облачке речи с реальными сегментами и завершением озвучки Давида/пользователя
    var previousVisualStateForSpeech by remember { mutableStateOf(effectiveVisualState) }
    var lastSpokenSubtitleWhileSpeaking by remember { mutableStateOf<String?>(null) }
    var lastCompletedSpokenSubtitle by remember { mutableStateOf<String?>(null) }
    var speechTurnEpoch by remember { mutableIntStateOf(0) }

    val isCurrentlySpeakingState = effectiveVisualState == DavidLiveVisualState.DAVID_SPEAKING ||
        effectiveVisualState == DavidLiveVisualState.USER_SPEAKING
    val wasPreviouslySpeakingState = previousVisualStateForSpeech == DavidLiveVisualState.DAVID_SPEAKING ||
        previousVisualStateForSpeech == DavidLiveVisualState.USER_SPEAKING

    if (isCurrentlySpeakingState) {
        if (!wasPreviouslySpeakingState || previousVisualStateForSpeech != effectiveVisualState) {
            speechTurnEpoch++
        }
        val trimmedLive = liveSubtitle.trim()
        if (trimmedLive.isNotEmpty()) {
            lastSpokenSubtitleWhileSpeaking = trimmedLive
        }
    } else if (wasPreviouslySpeakingState) {
        // Речь завершилась: фиксируем отзвучавшую реплику, чтобы она плавно ушла вверх и не оставалась в облачке
        lastCompletedSpokenSubtitle = lastSpokenSubtitleWhileSpeaking ?: liveSubtitle.trim()
        speechTurnEpoch++
    }
    if (previousVisualStateForSpeech != effectiveVisualState) {
        previousVisualStateForSpeech = effectiveVisualState
    }

    val effectiveLiveSubtitleForBubble = remember(
        effectiveVisualState,
        liveSubtitle,
        lastCompletedSpokenSubtitle
    ) {
        if (shouldDismissCompletedSpokenSubtitle(
                visualState = effectiveVisualState,
                currentLiveSubtitle = liveSubtitle,
                lastCompletedSpokenSubtitle = lastCompletedSpokenSubtitle
            )
        ) {
            null
        } else {
            liveSubtitle
        }
    }

    val activeSpeechSegment = remember(effectiveLiveSubtitleForBubble) {
        extractActiveSpeechBubbleSegment(effectiveLiveSubtitleForBubble.orEmpty())
    }

    val visualMetadata = remember(
        effectiveVisualState,
        effectiveFailureKind,
        isMicMuted,
        effectiveLiveSubtitleForBubble,
        errorMessage,
        activeToolName
    ) {
        DavidLiveVisualStateResolver.resolveMetadata(
            visualState = effectiveVisualState,
            failureKind = effectiveFailureKind,
            isMicMuted = isMicMuted,
            liveSubtitle = effectiveLiveSubtitleForBubble,
            errorMessage = errorMessage,
            activeToolName = activeToolName
        )
    }

    val speechPhraseSlotKey = remember(
        speechTurnEpoch,
        effectiveVisualState,
        isMicMuted,
        activeSpeechSegment.segmentIndex
    ) {
        "$speechTurnEpoch:${effectiveVisualState.name}:$isMicMuted:${activeSpeechSegment.segmentIndex}"
    }
    val phraseSlotTexts = remember { mutableStateMapOf<String, String>() }
    phraseSlotTexts[speechPhraseSlotKey] = visualMetadata.subtitle

    val (targetStatePrimaryColor, targetStateSecondaryColor) = remember(effectiveVisualState, isMicMuted) {
        resolveVisualStateColors(effectiveVisualState, isMicMuted)
    }
    val statePrimaryColor by animateColorAsState(
        targetValue = targetStatePrimaryColor,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "sheet_state_primary_color"
    )
    val stateSecondaryColor by animateColorAsState(
        targetValue = targetStateSecondaryColor,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
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

    val effectiveEnergy = if (activeDebugOverride == DavidLiveVisualState.USER_SPEAKING ||
        activeDebugOverride == DavidLiveVisualState.DAVID_SPEAKING
    ) {
        overallEnergy.coerceAtLeast(0.62f)
    } else {
        overallEnergy
    }

    val isMutedVisual = shouldApplyMutedVisualPalette(effectiveVisualState, isMicMuted)
    val isVoiceWaveUnderSphere = !isMutedVisual && (
        effectiveVisualState == DavidLiveVisualState.IDLE_LISTENING ||
            effectiveVisualState == DavidLiveVisualState.USER_SPEAKING ||
            effectiveVisualState == DavidLiveVisualState.DAVID_SPEAKING
        )
    val isSpinnerUnderSphere = !isMutedVisual && (
        effectiveVisualState == DavidLiveVisualState.THINKING ||
            effectiveVisualState == DavidLiveVisualState.FINANCE_LOOKUP ||
            effectiveVisualState == DavidLiveVisualState.ANALYZING ||
            effectiveVisualState == DavidLiveVisualState.PREPARING_RESPONSE ||
            effectiveVisualState == DavidLiveVisualState.CONNECTING ||
            effectiveVisualState == DavidLiveVisualState.RECONNECTING
        )

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
                                LiveScreenBgMid,
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
                            vertical = if (isEmbeddedInModalCard) 10.dp else 14.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top Header Bar (matches reference mockup: Avatar + Gold Coin + "Давид Жабов" ✓ + Status Badge + Settings ⚙)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                // Minimize `<` button
                                IconButton(
                                    onClick = onMinimize,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF111927))
                                        .border(1.dp, Color(0xFF1F2D42), CircleShape)
                                        .testTag("david_live_minimize_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Свернуть David Live в компактный режим",
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Чистый аватар Давида без декоративной монетки (Дополнение к ТЗ п. 2)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    statePrimaryColor.copy(alpha = 0.32f),
                                                    Color(0xFF0A1622)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.6.dp,
                                            color = statePrimaryColor.copy(alpha = 0.82f),
                                            shape = CircleShape
                                        )
                                        .testTag("david_live_header_avatar"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BrandFrogAvatar(size = 33.dp)
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = "Давид Жабов",
                                            color = Color.White,
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        // Verified green check badge next to David's name (as in reference)
                                        Box(
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldStatus),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF022C22),
                                                modifier = Modifier.size(10.5.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "AI-помощник по финансам",
                                        color = Color(0xFF7E92AC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Connection / State pill directly under name (matching reference top status)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(statePrimaryColor.copy(alpha = 0.12f))
                                            .border(
                                                width = 0.8.dp,
                                                color = statePrimaryColor.copy(alpha = 0.42f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                            .testTag("david_live_state_badge")
                                    ) {
                                        Icon(
                                            imageVector = resolveVisualStateIcon(effectiveVisualState, isMicMuted),
                                            contentDescription = null,
                                            tint = statePrimaryColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = visualMetadata.title,
                                            color = statePrimaryColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
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
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Настройки API-ключа и голоса",
                                        tint = Color(0xFFCBD5E1),
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
                    }

                    Spacer(modifier = Modifier.weight(0.85f))

                    // 2. Center Living 3D Energy Sphere + Under-Sphere Indicator + Contextual Cloud
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, downwardShiftDp.dp.roundToPx()) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (circularAlpha > 0.01f) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                    visualizerSize = if (isEmbeddedInModalCard) 268.dp else 308.dp,
                                    visualState = effectiveVisualState,
                                    failureKind = effectiveFailureKind,
                                    activeToolName = activeToolName,
                                    onRetryConnection = onRetryConnection,
                                    onOpenApiKeySettings = {
                                        apiCloudHighlightCount++
                                        onOpenApiKeySettings()
                                    }
                                )

                                Spacer(modifier = Modifier.height(if (isEmbeddedInModalCard) 6.dp else 10.dp))

                                // Under-Sphere Indicator (matches the 8 states in the reference design):
                                // - Audio waveform for IDLE_LISTENING (unmuted), USER_SPEAKING, DAVID_SPEAKING
                                // - Glowing orbital spinner for THINKING, FINANCE_LOOKUP, ANALYZING, PREPARING_RESPONSE, CONNECTING
                                // - State icon for MUTE (🔇), OFFLINE (📡), API_MISSING (⚠), ERROR (!)
                                Box(
                                    modifier = Modifier.height(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isVoiceWaveUnderSphere -> {
                                            DavidLiveHorizontalWaveVisualizer(
                                                rayMagnitudes = rayMagnitudes,
                                                overallEnergy = effectiveEnergy,
                                                connectionState = connectionState,
                                                activeSpeaker = activeSpeaker,
                                                isMicMuted = isMicMuted,
                                                barCount = 24,
                                                visualState = effectiveVisualState,
                                                modifier = Modifier
                                                    .width(176.dp)
                                                    .height(28.dp)
                                            )
                                        }

                                        isSpinnerUnderSphere -> {
                                            DavidLiveThinkingOrbitalSpinner(
                                                primaryColor = statePrimaryColor,
                                                secondaryColor = stateSecondaryColor,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        else -> {
                                            Icon(
                                                imageVector = resolveVisualStateIcon(effectiveVisualState, isMicMuted),
                                                contentDescription = null,
                                                tint = statePrimaryColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Primary status caption directly under the indicator
                                Text(
                                    text = statusText,
                                    color = when (effectiveVisualState) {
                                        DavidLiveVisualState.OFFLINE -> Color(0xFFFCA5A5)
                                        DavidLiveVisualState.API_MISSING -> Color(0xFFFDE68A)
                                        DavidLiveVisualState.ERROR -> Color(0xFFFBCFE8)
                                        DavidLiveVisualState.SUCCESS -> Color(0xFF86EFAC)
                                        else -> if (isMutedVisual) MutedSilverSecondary else Color.White
                                    },
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("david_live_sphere_status_caption")
                                )

                                Spacer(modifier = Modifier.height(if (isEmbeddedInModalCard) 8.dp else 10.dp))

                                // Compact Contextual Status / Subtitle Cloud (`david_live_subtitle_bubble`)
                                val isAlertState = effectiveVisualState == DavidLiveVisualState.OFFLINE ||
                                    effectiveVisualState == DavidLiveVisualState.API_MISSING ||
                                    effectiveVisualState == DavidLiveVisualState.ERROR

                                val cloudHighlightScale by animateFloatAsState(
                                    targetValue = if (apiCloudHighlightCount % 2 == 1) 1.03f else 1f,
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                                    label = "api_cloud_highlight_scale"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .scale(cloudHighlightScale)
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    SubtitleBubbleSurface.copy(alpha = 0.88f),
                                                    lerp(
                                                        SubtitleBubbleSurface,
                                                        statePrimaryColor,
                                                        if (isAlertState) 0.14f else 0.06f
                                                    )
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isAlertState || apiCloudHighlightCount > 0) {
                                                statePrimaryColor.copy(alpha = 0.62f)
                                            } else {
                                                lerp(SubtitleBubbleBorder, statePrimaryColor, 0.28f)
                                            },
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                        .testTag("david_live_subtitle_bubble"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AnimatedContent(
                                            targetState = speechPhraseSlotKey,
                                            transitionSpec = {
                                                (slideInVertically(
                                                    animationSpec = tween(
                                                        durationMillis = 320,
                                                        easing = FastOutSlowInEasing
                                                    ),
                                                    initialOffsetY = { fullHeight -> fullHeight / 2 }
                                                ) + fadeIn(
                                                    animationSpec = tween(
                                                        durationMillis = 260,
                                                        easing = FastOutSlowInEasing
                                                    )
                                                )).togetherWith(
                                                    slideOutVertically(
                                                        animationSpec = tween(
                                                            durationMillis = 320,
                                                            easing = FastOutSlowInEasing
                                                        ),
                                                        targetOffsetY = { fullHeight -> -fullHeight }
                                                    ) + fadeOut(
                                                        animationSpec = tween(
                                                            durationMillis = 220,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                )
                                            },
                                            label = "david_live_speech_phrase_transition"
                                        ) { slotKey ->
                                            val displayedPhrase = phraseSlotTexts[slotKey] ?: visualMetadata.subtitle
                                            Text(
                                                text = displayedPhrase,
                                                color = when (effectiveVisualState) {
                                                    DavidLiveVisualState.OFFLINE -> Color(0xFFFECACA)
                                                    DavidLiveVisualState.API_MISSING -> Color(0xFFFDE68A)
                                                    DavidLiveVisualState.ERROR -> Color(0xFFFBCFE8)
                                                    DavidLiveVisualState.SUCCESS -> Color(0xFFBBF7D0)
                                                    DavidLiveVisualState.DAVID_SPEAKING,
                                                    DavidLiveVisualState.USER_SPEAKING -> Color(0xFFF8FAFC)
                                                    else -> Color(0xFFE2E8F0)
                                                },
                                                fontSize = 15.sp,
                                                fontWeight = if (isCurrentlySpeakingState || isAlertState) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.SemiBold
                                                },
                                                lineHeight = 20.5.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.testTag("david_live_subtitle_text")
                                            )
                                        }

                                        AnimatedVisibility(
                                            visible = visualMetadata.actionType != DavidLiveVisualActionType.NONE &&
                                                visualMetadata.actionLabel != null,
                                            enter = fadeIn(),
                                            exit = fadeOut()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val actionLabel = visualMetadata.actionLabel ?: ""
                                                val isApiAction = visualMetadata.actionType == DavidLiveVisualActionType.OPEN_API_SETTINGS ||
                                                    visualMetadata.actionType == DavidLiveVisualActionType.ADD_API_KEY ||
                                                    visualMetadata.actionType == DavidLiveVisualActionType.UPDATE_API_KEY
                                                Button(
                                                    onClick = {
                                                        if (isApiAction) {
                                                            onOpenApiKeySettings()
                                                        } else {
                                                            onRetryConnection()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = statePrimaryColor.copy(alpha = 0.20f),
                                                        contentColor = statePrimaryColor
                                                    ),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier
                                                        .height(34.dp)
                                                        .border(
                                                            width = 1.dp,
                                                            color = statePrimaryColor.copy(alpha = 0.65f),
                                                            shape = RoundedCornerShape(14.dp)
                                                        )
                                                        .testTag("david_live_contextual_action_button")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isApiAction) Icons.Default.Key else Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = actionLabel,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
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
                                    BrandFrogAvatar(size = 36.dp)
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

                    // 3. Bottom Control Bar (matches reference mockup: Left Chat 💬, Center Large Mic 🎤/🔇, Right Red End Call 📞)
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
                            // 1. Left: Chat Button (`💬`)
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(ControlCircleSurface)
                                    .border(1.dp, ControlCircleBorder, CircleShape)
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
                                    modifier = Modifier.size(21.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // 2. Center: Large Focal Microphone Toggle Button (`🎤` / `🔇`)
                            val isBrightActiveMic = !isMicMuted && (
                                effectiveVisualState == DavidLiveVisualState.IDLE_LISTENING ||
                                    effectiveVisualState == DavidLiveVisualState.USER_SPEAKING ||
                                    effectiveVisualState == DavidLiveVisualState.DAVID_SPEAKING
                                )
                            val centerMicBgBrush = when {
                                isMicMuted -> Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF334155),
                                        Color(0xFF1E293B)
                                    )
                                )
                                isBrightActiveMic -> Brush.radialGradient(
                                    colors = listOf(
                                        stateSecondaryColor,
                                        statePrimaryColor
                                    )
                                )
                                else -> Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF1A2436),
                                        Color(0xFF111826)
                                    )
                                )
                            }
                            val centerMicBorderColor = when {
                                isMicMuted -> MutedSlatePrimary.copy(alpha = 0.78f)
                                isBrightActiveMic -> Color.White.copy(alpha = 0.55f)
                                else -> ControlCircleBorder
                            }
                            val centerMicIconTint = when {
                                isMicMuted -> MutedSilverSecondary
                                isBrightActiveMic -> Color(0xFF022C22)
                                else -> Color(0xFFCBD5E1)
                            }

                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .then(
                                        if (isBrightActiveMic) {
                                            Modifier.shadow(
                                                elevation = 18.dp,
                                                shape = CircleShape,
                                                ambientColor = statePrimaryColor,
                                                spotColor = statePrimaryColor
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clip(CircleShape)
                                    .background(centerMicBgBrush)
                                    .border(
                                        width = 1.4.dp,
                                        color = centerMicBorderColor,
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
                                    tint = centerMicIconTint,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // 3. Right: End Call Button (`📞` red hangup icon in dark crimson circle)
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .shadow(
                                        elevation = 10.dp,
                                        shape = CircleShape,
                                        ambientColor = ErrorCrimson.copy(alpha = 0.55f),
                                        spotColor = ErrorCrimson.copy(alpha = 0.65f)
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF2B1219),
                                                Color(0xFF170B10)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        color = ErrorCrimson.copy(alpha = 0.68f),
                                        shape = CircleShape
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = onEndSession
                                    )
                                    .testTag("david_live_cancel_button")
                                    .semantics {
                                        contentDescription = "Завершить разговор"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = null,
                                    tint = ErrorCrimson,
                                    modifier = Modifier.size(22.dp)
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
 * Glowing orbital spinner displayed below the sphere in `THINKING`, `FINANCE_LOOKUP`,
 * `ANALYZING`, `PREPARING_RESPONSE`, and `CONNECTING` states (matching screen 3 of the reference mockup).
 */
@Composable
private fun DavidLiveThinkingOrbitalSpinner(
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_orbital_spinner")
    val spinDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_spinner_angle"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 2.6.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        val arcSize = Size(diameter, diameter)

        drawArc(
            color = primaryColor.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    primaryColor.copy(alpha = 0.75f),
                    secondaryColor
                )
            ),
            startAngle = spinDegrees,
            sweepAngle = 230f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
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
