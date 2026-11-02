package com.example.davidapp.live

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Цветовые токены премиальной дизайн-системы Obsidian Neo-Bank для David Live (Issue #56).
 */
private val ObsidianCoreDark = Color(0xFF050C16)
private val ObsidianCoreDeep = Color(0xFF02050A)

// Изумрудная гамма (Ожидание — Медленное вращение)
private val EmeraldPrimary = Color(0xFF00E5A8)
private val EmeraldMint = Color(0xFF00F5B8)
private val EmeraldDeep = Color(0xFF059669)

// Бирюзово-голубая гамма (Ты говоришь — Ускоренное вращение)
private val TurquoiseSpeech = Color(0xFF00D9F5)
private val TurquoiseCyan = Color(0xFF00B4FF)
private val SkyBlueGlow = Color(0xFF38BDF8)

// Индиго-фиолетовая / электро-синяя гамма (Давид думает — Плавное вращение)
private val ThinkingIndigo = Color(0xFF6366F1)
private val ThinkingViolet = Color(0xFF8B5CF6)
private val ThinkingCyan = Color(0xFF38BDF8)

// Ярко-бирюзовая / изумрудная гамма (Давид отвечает — Активное вращение)
private val DavidSpeakingTurquoise = Color(0xFF00F0D0)
private val DavidSpeakingEmerald = Color(0xFF00E5A8)

// Приглушённая серебристо-сланцевая гамма (Микрофон выключен — Приглушённая сфера)
internal val MutedSlatePrimary = Color(0xFF94A3B8)
internal val MutedSilverSecondary = Color(0xFFCBD5E1)
private val MutedSlateGlow = Color(0xFF475569)
private val MutedSlateCore = Color(0xFF334155)

// Красная гамма (Нет интернета — Красная сфера)
private val CrimsonOffline = Color(0xFFEF4444)
private val CrimsonGlow = Color(0xFFDC2626)
private val CrimsonLight = Color(0xFFF87171)

// Золотисто-жёлтая гамма (API требуется — Жёлтая сфера)
private val GoldApiAmber = Color(0xFFF59E0B)
private val GoldApiGlow = Color(0xFFFBBF24)
private val GoldApiWarm = Color(0xFFD97706)

// Маджента-красно-фиолетовая гамма (Ошибка — Пульсирующая сфера)
private val VioletError = Color(0xFFA855F7)
private val CrimsonMagentaError = Color(0xFFF43F5E)
private val VioletGlow = Color(0xFF9333EA)

// Зелёная гамма (Готово / Успех)
private val SuccessGreen = Color(0xFF10B981)
private val SuccessMint = Color(0xFF00F0A0)

/**
 * Определяет, должна ли применяться приглушённая палитра выключенного микрофона (Mute).
 *
 * Правила приоритетов (Issue #56):
 * - Системные состояния (`OFFLINE`, `API_MISSING`, `ERROR`, `CONNECTING`, `RECONNECTING`, `SUCCESS`)
 *   всегда сохраняют собственную цветовую индикацию и действия.
 * - Исходящая речь Давида (`DAVID_SPEAKING`) НЕ маскируется состоянием пользовательского микрофона:
 *   Давид продолжает говорить со своей штатной изумрудно-бирюзовой анимацией.
 * - Во всех остальных состояниях (`IDLE_LISTENING`, `USER_SPEAKING`, `THINKING`, `FINANCE_LOOKUP`,
 *   `ANALYZING`, `PREPARING_RESPONSE`) выключение микрофона переводит сферу в заметную
 *   приглушённую серебристо-сланцевую палитру.
 */
internal fun shouldApplyMutedVisualPalette(
    visualState: DavidLiveVisualState,
    isMicMuted: Boolean
): Boolean {
    if (!isMicMuted) return false
    return when (visualState) {
        DavidLiveVisualState.OFFLINE,
        DavidLiveVisualState.API_MISSING,
        DavidLiveVisualState.ERROR,
        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING,
        DavidLiveVisualState.SUCCESS,
        DavidLiveVisualState.DAVID_SPEAKING -> false

        DavidLiveVisualState.IDLE_LISTENING,
        DavidLiveVisualState.USER_SPEAKING,
        DavidLiveVisualState.THINKING,
        DavidLiveVisualState.FINANCE_LOOKUP,
        DavidLiveVisualState.ANALYZING,
        DavidLiveVisualState.PREPARING_RESPONSE -> true
    }
}

/**
 * Вычисляет целевую угловую скорость орбитального движения (рад/с) в зависимости от
 * реального состояния [DavidLiveVisualState], флага `isMicMuted` и входящей/исходящей аудиоэнергии.
 *
 * Используется для плавной инерционной интерполяции скорости без сброса фазы вращения.
 */
internal fun resolveTargetOrbitAngularSpeed(
    visualState: DavidLiveVisualState,
    isMicMuted: Boolean = false,
    audioEnergy: Float = 0f
): Float {
    val clampedEnergy = audioEnergy.coerceIn(0f, 1f)
    if (shouldApplyMutedVisualPalette(visualState, isMicMuted)) {
        return 0.22f
    }
    return when (visualState) {
        DavidLiveVisualState.OFFLINE -> 0.14f
        DavidLiveVisualState.API_MISSING -> 0.18f
        DavidLiveVisualState.ERROR -> 0.26f
        DavidLiveVisualState.IDLE_LISTENING -> 0.44f
        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING -> 0.68f
        DavidLiveVisualState.THINKING -> 0.78f
        DavidLiveVisualState.ANALYZING -> 0.88f
        DavidLiveVisualState.FINANCE_LOOKUP -> 0.96f
        DavidLiveVisualState.PREPARING_RESPONSE -> 1.04f
        DavidLiveVisualState.SUCCESS -> 1.12f
        DavidLiveVisualState.USER_SPEAKING -> 1.24f + clampedEnergy * 0.92f
        DavidLiveVisualState.DAVID_SPEAKING -> 1.34f + clampedEnergy * 1.05f
    }
}

/**
 * Вычисляет следующее значение непрерывной фазы орбитального движения (в радианах)
 * без скачков и сбросов при переключении состояний.
 */
internal fun advanceContinuousOrbitPhase(
    currentPhaseRad: Float,
    deltaMs: Long,
    angularSpeedRadPerSec: Float,
    reduceMotion: Boolean = false
): Float {
    if (reduceMotion || deltaMs <= 0L) return currentPhaseRad
    val boundedDeltaSec = (deltaMs.coerceIn(1L, 64L)) / 1000f
    val periodWrap = (2000.0 * PI).toFloat()
    return (currentPhaseRad + boundedDeltaSec * angularSpeedRadPerSec.coerceAtLeast(0f)) % periodWrap
}

/**
 * Палитра визуального состояния живой 3D-сферы.
 */
internal data class SpherePalette(
    val primary: Color,
    val secondary: Color,
    val haloGlow: Color,
    val coreGlow: Color,
    val waveAccent: Color
)

internal fun resolveSpherePalette(
    state: DavidLiveVisualState,
    isMicMuted: Boolean = false
): SpherePalette {
    if (shouldApplyMutedVisualPalette(state, isMicMuted)) {
        return SpherePalette(
            primary = MutedSlatePrimary,
            secondary = MutedSilverSecondary,
            haloGlow = MutedSlateGlow.copy(alpha = 0.30f),
            coreGlow = MutedSlateCore.copy(alpha = 0.24f),
            waveAccent = MutedSilverSecondary
        )
    }

    return when (state) {
        DavidLiveVisualState.IDLE_LISTENING -> SpherePalette(
            primary = EmeraldPrimary,
            secondary = EmeraldMint,
            haloGlow = EmeraldDeep.copy(alpha = 0.38f),
            coreGlow = EmeraldPrimary.copy(alpha = 0.25f),
            waveAccent = EmeraldMint
        )

        DavidLiveVisualState.USER_SPEAKING -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = TurquoiseCyan,
            haloGlow = TurquoiseCyan.copy(alpha = 0.46f),
            coreGlow = SkyBlueGlow.copy(alpha = 0.30f),
            waveAccent = TurquoiseSpeech
        )

        DavidLiveVisualState.THINKING -> SpherePalette(
            primary = ThinkingViolet,
            secondary = ThinkingCyan,
            haloGlow = ThinkingIndigo.copy(alpha = 0.44f),
            coreGlow = ThinkingViolet.copy(alpha = 0.28f),
            waveAccent = ThinkingCyan
        )

        DavidLiveVisualState.FINANCE_LOOKUP -> SpherePalette(
            primary = EmeraldMint,
            secondary = TurquoiseSpeech,
            haloGlow = EmeraldDeep.copy(alpha = 0.42f),
            coreGlow = GoldApiAmber.copy(alpha = 0.22f),
            waveAccent = EmeraldMint
        )

        DavidLiveVisualState.ANALYZING -> SpherePalette(
            primary = ThinkingCyan,
            secondary = ThinkingViolet,
            haloGlow = ThinkingIndigo.copy(alpha = 0.46f),
            coreGlow = ThinkingCyan.copy(alpha = 0.28f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.PREPARING_RESPONSE -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = EmeraldPrimary,
            haloGlow = EmeraldDeep.copy(alpha = 0.40f),
            coreGlow = TurquoiseSpeech.copy(alpha = 0.26f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.DAVID_SPEAKING -> SpherePalette(
            primary = DavidSpeakingTurquoise,
            secondary = DavidSpeakingEmerald,
            haloGlow = EmeraldDeep.copy(alpha = 0.52f),
            coreGlow = DavidSpeakingTurquoise.copy(alpha = 0.34f),
            waveAccent = DavidSpeakingEmerald
        )

        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING -> SpherePalette(
            primary = ThinkingCyan,
            secondary = TurquoiseSpeech,
            haloGlow = ThinkingIndigo.copy(alpha = 0.36f),
            coreGlow = ThinkingCyan.copy(alpha = 0.22f),
            waveAccent = TurquoiseSpeech
        )

        DavidLiveVisualState.OFFLINE -> SpherePalette(
            primary = CrimsonOffline,
            secondary = CrimsonLight,
            haloGlow = CrimsonGlow.copy(alpha = 0.44f),
            coreGlow = CrimsonOffline.copy(alpha = 0.28f),
            waveAccent = CrimsonLight
        )

        DavidLiveVisualState.API_MISSING -> SpherePalette(
            primary = GoldApiAmber,
            secondary = GoldApiGlow,
            haloGlow = GoldApiWarm.copy(alpha = 0.42f),
            coreGlow = GoldApiAmber.copy(alpha = 0.26f),
            waveAccent = GoldApiGlow
        )

        DavidLiveVisualState.ERROR -> SpherePalette(
            primary = CrimsonMagentaError,
            secondary = VioletError,
            haloGlow = VioletGlow.copy(alpha = 0.44f),
            coreGlow = CrimsonMagentaError.copy(alpha = 0.28f),
            waveAccent = VioletError
        )

        DavidLiveVisualState.SUCCESS -> SpherePalette(
            primary = SuccessMint,
            secondary = SuccessGreen,
            haloGlow = SuccessGreen.copy(alpha = 0.54f),
            coreGlow = SuccessMint.copy(alpha = 0.34f),
            waveAccent = SuccessMint
        )
    }
}

/**
 * Параметры отдельной 3D-орбиты вокруг живой энергетической сферы.
 */
private class OrbitRingSpec(
    val rollDegrees: Float,
    val pitchDegrees: Float,
    val radiusRatio: Float,
    val speedMultiplier: Float,
    val phaseOffsetRad: Float,
    val ribbonWidthDp: Float,
    val useSecondaryColor: Boolean
)

private val DefaultOrbitSpecs = arrayOf(
    // 1. Главная диагональная световая орбита (как яркое кольцо на референсе)
    OrbitRingSpec(
        rollDegrees = -21f,
        pitchDegrees = 70f,
        radiusRatio = 1.36f,
        speedMultiplier = 1.0f,
        phaseOffsetRad = 0.2f,
        ribbonWidthDp = 2.6f,
        useSecondaryColor = false
    ),
    // 2. Встречная наклонная орбита, создающая объёмное перекрестие в 3D
    OrbitRingSpec(
        rollDegrees = 26f,
        pitchDegrees = 64f,
        radiusRatio = 1.26f,
        speedMultiplier = -0.76f,
        phaseOffsetRad = 2.1f,
        ribbonWidthDp = 2.0f,
        useSecondaryColor = true
    ),
    // 3. Тонкая крутая орбита для ощущения многослойного энергетического поля
    OrbitRingSpec(
        rollDegrees = -52f,
        pitchDegrees = 73f,
        radiusRatio = 1.18f,
        speedMultiplier = 1.28f,
        phaseOffsetRad = 4.2f,
        ribbonWidthDp = 1.5f,
        useSecondaryColor = false
    )
)

/**
 * Живая 3D энергетическая сфера **David Live** со световыми орбитами (Issue #56 Redesign).
 *
 * Реализует утверждённый дизайн-референс «Новый дизайн. Живая сфера. Умный Давид»:
 * - Полупрозрачное стеклянное энергетическое ядро с глубоким внутренним свечением и бликами;
 * - Трёхмерные эллиптические световые орбиты с честным разделением глубины:
 *   задняя полусфера орбит (`z < 0`) проходит за ядром, передняя (`z >= 0`) — перед ним;
 * - Непрерывное инерционное движение орбит без сброса фазы при смене состояний;
 * - Мгновенно различимая приглушённая серебристо-сланцевая палитра при выключении микрофона (`isMicMuted`),
 *   не маскирующая приоритетные состояния (`OFFLINE`, `API_MISSING`, `ERROR`) и не блокирующая речь Давида (`DAVID_SPEAKING`).
 */
@Composable
fun DavidLiveMeshSphereVisualizer(
    visualState: DavidLiveVisualState,
    overallEnergy: Float,
    rayMagnitudes: List<Float>,
    isMicMuted: Boolean,
    onSphereClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualizerSize: Dp = 276.dp,
    activeToolName: String? = null
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) <= 0.05f
        } catch (_: Exception) {
            false
        }
    }

    val targetPalette = remember(visualState, isMicMuted) {
        resolveSpherePalette(visualState, isMicMuted)
    }

    // Плавная интерполяция цветов сферы и орбит за 400 мс (требование 300–500 мс)
    val animatedPrimaryColor by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "sphere_primary_color"
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = targetPalette.secondary,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "sphere_secondary_color"
    )
    val animatedHaloColor by animateColorAsState(
        targetValue = targetPalette.haloGlow,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sphere_halo_color"
    )
    val animatedCoreGlowColor by animateColorAsState(
        targetValue = targetPalette.coreGlow,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sphere_core_color"
    )

    // Mute влияет ТОЛЬКО на входящую речь пользователя и НЕ подавляет исходящую речь Давида
    val isUserSpeechActive = visualState == DavidLiveVisualState.USER_SPEAKING && !isMicMuted
    val isDavidSpeechActive = visualState == DavidLiveVisualState.DAVID_SPEAKING
    val isSpeechActive = isUserSpeechActive || isDavidSpeechActive

    val targetAudioEnergy = if (isSpeechActive) {
        overallEnergy.coerceIn(0.08f, 1f)
    } else {
        0.03f
    }

    // Плавное затухание амплитуды (380 мс) без резкого обрыва после окончания фразы
    val smoothedEnergy by animateFloatAsState(
        targetValue = targetAudioEnergy,
        animationSpec = tween(
            durationMillis = if (isSpeechActive) 90 else 380,
            easing = FastOutSlowInEasing
        ),
        label = "sphere_energy_smoothing"
    )

    // Целевая угловая скорость орбит с плавной инерционной интерполяцией (ease-in / ease-out)
    val targetOrbitSpeed = resolveTargetOrbitAngularSpeed(
        visualState = visualState,
        isMicMuted = isMicMuted,
        audioEnergy = smoothedEnergy
    )
    val smoothedOrbitSpeed by animateFloatAsState(
        targetValue = targetOrbitSpeed,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sphere_orbit_angular_speed"
    )
    val currentOrbitSpeedState by rememberUpdatedState(smoothedOrbitSpeed)

    // Непрерывный аккумулятор фазы вращения — никогда не сбрасывается при смене visualState
    var continuousOrbitPhaseRad by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        var lastFrameMs = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { frameTimeMs ->
                if (lastFrameMs >= 0L) {
                    val deltaMs = frameTimeMs - lastFrameMs
                    continuousOrbitPhaseRad = advanceContinuousOrbitPhase(
                        currentPhaseRad = continuousOrbitPhaseRad,
                        deltaMs = deltaMs,
                        angularSpeedRadPerSec = currentOrbitSpeedState,
                        reduceMotion = false
                    )
                }
                lastFrameMs = frameTimeMs
            }
        }
    }

    // Спокойное дыхание ядра и пульсация для состояния ERROR
    val infiniteTransition = rememberInfiniteTransition(label = "david_live_sphere_breathing")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (visualState == DavidLiveVisualState.ERROR) 1600 else 3200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sphere_breathing_cycle"
    )

    // Плавный коэффициент яркости орбит (приглушается при Mute и Offline)
    val targetOrbitIntensity = when {
        shouldApplyMutedVisualPalette(visualState, isMicMuted) -> 0.56f
        visualState == DavidLiveVisualState.OFFLINE -> 0.72f
        visualState == DavidLiveVisualState.API_MISSING -> 0.85f
        visualState == DavidLiveVisualState.ERROR -> 0.80f + 0.18f * breathProgress
        visualState == DavidLiveVisualState.USER_SPEAKING ||
            visualState == DavidLiveVisualState.DAVID_SPEAKING -> 0.94f + 0.06f * smoothedEnergy
        else -> 0.86f
    }
    val orbitIntensity by animateFloatAsState(
        targetValue = targetOrbitIntensity,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "sphere_orbit_intensity"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val orbitProgressNormalized = ((continuousOrbitPhaseRad / (2f * PI.toFloat())) % 1f + 1f) % 1f
    val yawDegreesContinuous = ((continuousOrbitPhaseRad * 180f / PI.toFloat()) * 0.45f) % 360f

    Box(
        modifier = modifier
            .size(visualizerSize)
            .semantics {
                contentDescription = when (visualState) {
                    DavidLiveVisualState.OFFLINE -> "Связь потеряна. Нажмите на сферу для повторного подключения."
                    DavidLiveVisualState.API_MISSING -> "Требуется API-ключ Gemini. Нажмите для настройки."
                    DavidLiveVisualState.ERROR -> "Ошибка связи. Нажмите для повтора."
                    else -> if (isMicMuted) "Микрофон выключен. Нажмите, чтобы включить." else "Голосовой помощник David Live"
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onSphereClick
            )
            .testTag("david_live_circular_visualizer")
            .testTag("david_live_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(visualizerSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.62f
            val dynamicRadius = baseRadius * (
                1f +
                    0.025f * sin(breathProgress * PI.toFloat()) +
                    smoothedEnergy * 0.085f
                )

            // 1. Внешнее атмосферное свечение (Neon Halo) + микро-частицы в пространстве вокруг сферы
            val haloRadius = dynamicRadius * (1.46f + 0.08f * breathProgress + smoothedEnergy * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedHaloColor.copy(alpha = (animatedHaloColor.alpha * 1.15f).coerceAtMost(0.68f)),
                        animatedHaloColor.copy(alpha = animatedHaloColor.alpha * 0.42f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )

            drawAmbientSpaceParticles(
                center = center,
                sphereRadius = dynamicRadius,
                phaseRad = continuousOrbitPhaseRad,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                intensity = orbitIntensity
            )

            // 2. ЗАДНЯЯ ПОЛУСФЕРА 3D-орбит (z < 0) — проходит ЗА стеклянным ядром сферы
            drawThreeDimensionalOrbits(
                center = center,
                sphereRadius = dynamicRadius,
                phaseRad = continuousOrbitPhaseRad,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                intensity = orbitIntensity,
                drawFrontHemisphere = false
            )

            // 3. Полупрозрачное стеклянное энергетическое ядро сферы (Glass Core + Inner Plasma + Rim Light)
            drawTranslucentGlassSphereCore(
                center = center,
                radius = dynamicRadius,
                phaseRad = continuousOrbitPhaseRad,
                breathProgress = breathProgress,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                coreGlowColor = animatedCoreGlowColor,
                intensity = orbitIntensity
            )

            // 4. Тонкие внутренние энергетические потоки внутри стеклянной оболочки
            drawSubtleInnerEnergyMesh(
                center = center,
                radius = dynamicRadius * 0.96f,
                breathProgress = breathProgress,
                yawDegrees = yawDegreesContinuous,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                intensity = orbitIntensity
            )

            // 5. ПЕРЕДНЯЯ ПОЛУСФЕРА 3D-орбит (z >= 0) — огибает сферу СПЕРЕДИ с яркими световыми потоками
            drawThreeDimensionalOrbits(
                center = center,
                sphereRadius = dynamicRadius,
                phaseRad = continuousOrbitPhaseRad,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                intensity = orbitIntensity,
                drawFrontHemisphere = true
            )

            // 6. Специфические акценты состояний поверх орбит
            when (visualState) {
                DavidLiveVisualState.FINANCE_LOOKUP -> {
                    drawPlanetaryFinanceRing(
                        center = center,
                        radius = dynamicRadius,
                        orbitProgress = orbitProgressNormalized,
                        accentColor = animatedPrimaryColor,
                        goldColor = GoldApiGlow
                    )
                }

                DavidLiveVisualState.ANALYZING -> {
                    drawAnalyzingDualOrbits(
                        center = center,
                        radius = dynamicRadius,
                        orbitProgress = orbitProgressNormalized,
                        cyanColor = animatedPrimaryColor,
                        emeraldColor = animatedSecondaryColor
                    )
                }

                DavidLiveVisualState.DAVID_SPEAKING -> {
                    drawAcousticSoundwaveArcs(
                        center = center,
                        radius = dynamicRadius,
                        energy = smoothedEnergy,
                        breathProgress = breathProgress,
                        color = animatedPrimaryColor
                    )
                }

                DavidLiveVisualState.CONNECTING,
                DavidLiveVisualState.RECONNECTING -> {
                    drawConnectingHandshakeArcs(
                        center = center,
                        radius = dynamicRadius,
                        orbitProgress = orbitProgressNormalized,
                        color = animatedPrimaryColor
                    )
                }

                DavidLiveVisualState.SUCCESS -> {
                    drawSuccessExpansionRing(
                        center = center,
                        radius = dynamicRadius,
                        orbitProgress = orbitProgressNormalized,
                        color = animatedPrimaryColor
                    )
                }

                else -> Unit
            }
        }
    }
}

/**
 * Отрисовка полупрозрачного стеклянного ядра сферы с внутренним плазменным свечением,
 * краевым неоновым рефлексом (Fresnel rim) и мягким глянцевым бликом сверху-слева.
 */
private fun DrawScope.drawTranslucentGlassSphereCore(
    center: Offset,
    radius: Float,
    phaseRad: Float,
    breathProgress: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    coreGlowColor: Color,
    intensity: Float
) {
    // 1. Тёмная глубокая основа сферы
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ObsidianCoreDark.copy(alpha = 0.90f),
                ObsidianCoreDeep.copy(alpha = 0.96f)
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )

    // 2. Живое внутреннее плазменное облако (мягко смещается по фазе вращения)
    val driftX = center.x + radius * 0.16f * cos(phaseRad * 0.65f)
    val driftY = center.y - radius * 0.14f * sin(phaseRad * 0.50f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = (0.34f + 0.18f * energy) * intensity),
                primaryColor.copy(alpha = (0.24f + 0.12f * breathProgress) * intensity),
                coreGlowColor.copy(alpha = 0.14f * intensity),
                Color.Transparent
            ),
            center = Offset(driftX, driftY),
            radius = radius * 0.95f
        ),
        radius = radius,
        center = center
    )

    // 3. Вторичное нижнее внутреннее отражение для объёма
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = (0.22f + 0.14f * energy) * intensity),
                Color.Transparent
            ),
            center = Offset(center.x + radius * 0.24f, center.y + radius * 0.32f),
            radius = radius * 0.78f
        ),
        radius = radius,
        center = center
    )

    // 4. Глянцевый стеклянный блик в верхней левой части сферы
    val highlightCenter = Offset(center.x - radius * 0.28f, center.y - radius * 0.32f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f * intensity),
                secondaryColor.copy(alpha = 0.18f * intensity),
                Color.Transparent
            ),
            center = highlightCenter,
            radius = radius * 0.56f
        ),
        radius = radius,
        center = center
    )

    // 5. Светящийся стеклянный обод (Fresnel Rim Light) по контуру сферы
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.82f * intensity),
                primaryColor.copy(alpha = 0.35f * intensity),
                Color.White.copy(alpha = 0.72f * intensity),
                primaryColor.copy(alpha = 0.45f * intensity),
                secondaryColor.copy(alpha = 0.82f * intensity)
            ),
            center = center
        ),
        radius = radius,
        center = center,
        style = Stroke(width = 2.0.dp.toPx())
    )

    // Мягкий внешний ореол непосредственно вокруг стеклянной кромки
    drawCircle(
        color = primaryColor.copy(alpha = (0.22f + 0.14f * energy) * intensity),
        radius = radius * 1.02f,
        center = center,
        style = Stroke(width = 4.5.dp.toPx())
    )
}

/**
 * Отрисовка трёхмерных наклонных эллиптических световых орбит с честной глубиной:
 * - При `drawFrontHemisphere == false` рисуются только сегменты, находящиеся ЗА сферой (`z < 0`).
 * - При `drawFrontHemisphere == true` рисуются сегменты ПЕРЕД сферой (`z >= 0`), включая
 *   движущиеся по орбите яркие энергетические сгустки (кометные шлейфы) и узлы свечения.
 */
private fun DrawScope.drawThreeDimensionalOrbits(
    center: Offset,
    sphereRadius: Float,
    phaseRad: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float,
    drawFrontHemisphere: Boolean
) {
    val segments = 64
    val stepAngle = (2.0 * PI / segments).toFloat()
    val cameraDistance = sphereRadius * 3.8f

    for ((orbitIndex, spec) in DefaultOrbitSpecs.withIndex()) {
        val rollRad = (spec.rollDegrees * PI / 180.0).toFloat()
        val pitchRad = (spec.pitchDegrees * PI / 180.0).toFloat()
        val cosRoll = cos(rollRad)
        val sinRoll = sin(rollRad)
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)

        val orbitRadius = sphereRadius * spec.radiusRatio * (1f + energy * 0.04f)
        val baseColor = if (spec.useSecondaryColor) secondaryColor else primaryColor
        val accentColor = if (spec.useSecondaryColor) primaryColor else secondaryColor
        val orbitDynamicPhase = phaseRad * spec.speedMultiplier + spec.phaseOffsetRad

        var prevX = 0f
        var prevY = 0f
        var prevZ = 0f
        var hasPrev = false

        for (i in 0..segments) {
            val theta = i * stepAngle
            // Волновая микро-модуляция орбиты от реального голоса
            val waveOffset = if (energy > 0.06f) {
                sphereRadius * 0.028f * energy * sin(theta * 3f + orbitDynamicPhase * 2f)
            } else {
                0f
            }
            val r = orbitRadius + waveOffset

            // Координаты в плоскости орбиты
            val xLocal = r * cos(theta)
            val zLocal = r * sin(theta)

            // Наклон по Pitch (вокруг оси X): формирует эллипс и глубину Z
            val yPitch = -zLocal * cosPitch
            val zPitch = zLocal * sinPitch

            // Поворот по Roll (вокруг оси Z экрана): задаёт диагональный наклон орбиты
            val xWorld = xLocal * cosRoll - yPitch * sinRoll
            val yWorld = xLocal * sinRoll + yPitch * cosRoll
            val zWorld = zPitch

            val perspective = cameraDistance / (cameraDistance - zWorld).coerceAtLeast(sphereRadius * 0.8f)
            val screenX = center.x + xWorld * perspective
            val screenY = center.y + yWorld * perspective

            if (hasPrev) {
                val midZ = (zWorld + prevZ) * 0.5f
                val isFront = midZ >= 0f
                if (isFront == drawFrontHemisphere) {
                    val depthFactor = ((midZ / orbitRadius) + 1f) * 0.5f // 0..1
                    // Бегущая световая волна вдоль орбиты (кометный поток)
                    val midTheta = theta - stepAngle * 0.5f
                    val streamWave = ((cos(midTheta - orbitDynamicPhase) + 1f) * 0.5f)
                    val streamHighlight = streamWave * streamWave * streamWave

                    val baseAlpha = if (isFront) {
                        (0.32f + 0.52f * streamHighlight + 0.14f * depthFactor) * intensity
                    } else {
                        (0.10f + 0.16f * streamHighlight) * intensity
                    }

                    val strokePx = spec.ribbonWidthDp.dp.toPx() *
                        (if (isFront) 0.85f + 0.55f * streamHighlight else 0.62f)

                    // Мягкое неоновое свечение вокруг передней дуги орбиты
                    if (isFront && streamHighlight > 0.25f) {
                        drawLine(
                            color = baseColor.copy(alpha = (baseAlpha * 0.34f).coerceIn(0f, 0.45f)),
                            start = Offset(prevX, prevY),
                            end = Offset(screenX, screenY),
                            strokeWidth = strokePx * 3.0f,
                            cap = StrokeCap.Round
                        )
                    }

                    val segmentColor = lerp(
                        baseColor,
                        Color.White,
                        if (isFront) (streamHighlight * 0.48f).coerceIn(0f, 0.55f) else 0f
                    )

                    drawLine(
                        color = segmentColor.copy(alpha = baseAlpha.coerceIn(0.05f, 0.96f)),
                        start = Offset(prevX, prevY),
                        end = Offset(screenX, screenY),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }

            prevX = screenX
            prevY = screenY
            prevZ = zWorld
            hasPrev = true
        }

        // Светящаяся энергетическая частица (flare), бегущая по передней части орбиты
        val headTheta = orbitDynamicPhase
        val headXLocal = orbitRadius * cos(headTheta)
        val headZLocal = orbitRadius * sin(headTheta)
        val headYPitch = -headZLocal * cosPitch
        val headZWorld = headZLocal * sinPitch

        if ((headZWorld >= 0f) == drawFrontHemisphere) {
            val headXWorld = headXLocal * cosRoll - headYPitch * sinRoll
            val headYWorld = headXLocal * sinRoll + headYPitch * cosRoll
            val headPerspective = cameraDistance / (cameraDistance - headZWorld).coerceAtLeast(sphereRadius * 0.8f)
            val headPos = Offset(
                center.x + headXWorld * headPerspective,
                center.y + headYWorld * headPerspective
            )
            val flareAlpha = (if (drawFrontHemisphere) 0.90f else 0.28f) * intensity
            val flareRadius = (if (orbitIndex == 0) 3.4.dp else 2.5.dp).toPx() * headPerspective

            drawCircle(
                color = accentColor.copy(alpha = flareAlpha * 0.42f),
                radius = flareRadius * 2.8f,
                center = headPos
            )
            drawCircle(
                color = Color.White.copy(alpha = flareAlpha),
                radius = flareRadius * 0.85f,
                center = headPos
            )
        }
    }
}

/**
 * Отрисовка деликатных внутренних 3D-линий потока внутри стеклянной сферы (не перекрывающих прозрачное ядро).
 */
private fun DrawScope.drawSubtleInnerEnergyMesh(
    center: Offset,
    radius: Float,
    breathProgress: Float,
    yawDegrees: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float
) {
    val pitchRadians = (24.0 * PI / 180.0).toFloat()
    val yawRadians = (yawDegrees * PI / 180.0).toFloat()
    val cosPitch = cos(pitchRadians)
    val sinPitch = sin(pitchRadians)
    val cosYaw = cos(yawRadians)
    val sinYaw = sin(yawRadians)

    val breathingRadius = radius * (1f + 0.02f * sin(breathProgress * 2f * PI.toFloat()))
    val cameraDistance = breathingRadius * 3.5f

    val latitudeAngles = floatArrayOf(-38f, 0f, 38f)
    val stepTheta = (PI * 2.0 / 28.0).toFloat()

    for (latDeg in latitudeAngles) {
        val phi = (latDeg * PI / 180.0).toFloat()
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val latRadius = breathingRadius * cosPhi
        val y0 = breathingRadius * sinPhi

        var prevScreenX = 0f
        var prevScreenY = 0f
        var prevZ = 0f
        var isFirst = true

        for (i in 0..28) {
            val theta = i * stepTheta
            val waveDeform = 0.03f * (0.3f + energy) * sin(theta * 3f + phi * 2f + yawRadians)
            val rCurrent = latRadius * (1f + waveDeform)

            val x0 = rCurrent * cos(theta)
            val z0 = rCurrent * sin(theta)

            val x1 = x0 * cosYaw + z0 * sinYaw
            val z1 = -x0 * sinYaw + z0 * cosYaw

            val y2 = y0 * cosPitch - z1 * sinPitch
            val z2 = y0 * sinPitch + z1 * cosPitch

            val perspectiveK = cameraDistance / (cameraDistance - z2)
            val screenX = center.x + x1 * perspectiveK
            val screenY = center.y + y2 * perspectiveK

            if (!isFirst) {
                val isFront = (z2 + prevZ) / 2f >= 0f
                val strokeAlpha = (if (isFront) 0.16f else 0.06f) * intensity
                val lineColor = if (latDeg == 0f) secondaryColor else primaryColor

                drawLine(
                    color = lineColor.copy(alpha = strokeAlpha.coerceIn(0.03f, 0.28f)),
                    start = Offset(prevScreenX, prevScreenY),
                    end = Offset(screenX, screenY),
                    strokeWidth = 1.0.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            prevScreenX = screenX
            prevScreenY = screenY
            prevZ = z2
            isFirst = false
        }
    }
}

/**
 * Отрисовка фоновых микро-частиц (stardust) вокруг сферы, создающих атмосферу живого поля.
 */
private fun DrawScope.drawAmbientSpaceParticles(
    center: Offset,
    sphereRadius: Float,
    phaseRad: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float
) {
    val particleCount = 10
    for (i in 0 until particleCount) {
        val baseAngle = (i * (2.0 * PI / particleCount)).toFloat() + phaseRad * (if (i % 2 == 0) 0.22f else -0.18f)
        val distRatio = 1.16f + (i % 3) * 0.11f + 0.04f * sin(phaseRad * 0.8f + i)
        val px = center.x + sphereRadius * distRatio * cos(baseAngle)
        val py = center.y + sphereRadius * (distRatio * 0.82f) * sin(baseAngle)
        val twinkle = ((sin(phaseRad * 1.4f + i * 1.7f) + 1f) * 0.5f)
        val alpha = (0.14f + 0.36f * twinkle) * intensity
        val particleColor = if (i % 2 == 0) primaryColor else secondaryColor
        val r = (1.2.dp.toPx() + 1.0.dp.toPx() * twinkle)

        drawCircle(
            color = particleColor.copy(alpha = alpha.coerceIn(0.05f, 0.55f)),
            radius = r,
            center = Offset(px, py)
        )
    }
}

/**
 * Отрисовка планетарного сканирующего 3D-кольца с микро-акцентами финансов (FINANCE_LOOKUP).
 */
private fun DrawScope.drawPlanetaryFinanceRing(
    center: Offset,
    radius: Float,
    orbitProgress: Float,
    accentColor: Color,
    goldColor: Color
) {
    val ringRadiusX = radius * 1.38f
    val ringRadiusY = radius * 0.44f
    val tiltAngleDegrees = -18f

    rotate(degrees = tiltAngleDegrees, pivot = center) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.32f),
                    accentColor.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = center,
                radius = ringRadiusX
            ),
            topLeft = Offset(center.x - ringRadiusX, center.y - ringRadiusY),
            size = Size(ringRadiusX * 2f, ringRadiusY * 2f),
            style = Stroke(width = 2.8.dp.toPx())
        )

        val scanAngle = orbitProgress * 2f * PI.toFloat()
        val pulseX = center.x + ringRadiusX * cos(scanAngle)
        val pulseY = center.y + ringRadiusY * sin(scanAngle)

        drawCircle(
            color = goldColor.copy(alpha = 0.95f),
            radius = 4.2.dp.toPx(),
            center = Offset(pulseX, pulseY)
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.40f),
            radius = 9.5.dp.toPx(),
            center = Offset(pulseX, pulseY)
        )
    }
}

/**
 * Перекрёстные аналитические орбиты с обращающимися маяками (ANALYZING).
 */
private fun DrawScope.drawAnalyzingDualOrbits(
    center: Offset,
    radius: Float,
    orbitProgress: Float,
    cyanColor: Color,
    emeraldColor: Color
) {
    val orbitRadiusX = radius * 1.30f
    val orbitRadiusY = radius * 0.46f

    rotate(degrees = 34f, pivot = center) {
        val ang1 = orbitProgress * 2f * PI.toFloat()
        val beacon1X = center.x + orbitRadiusX * cos(ang1)
        val beacon1Y = center.y + orbitRadiusY * sin(ang1)
        drawCircle(
            color = cyanColor,
            radius = 3.6.dp.toPx(),
            center = Offset(beacon1X, beacon1Y)
        )
    }

    rotate(degrees = -34f, pivot = center) {
        val ang2 = (orbitProgress + 0.5f) * 2f * PI.toFloat()
        val beacon2X = center.x + orbitRadiusX * cos(ang2)
        val beacon2Y = center.y + orbitRadiusY * sin(ang2)
        drawCircle(
            color = emeraldColor,
            radius = 3.6.dp.toPx(),
            center = Offset(beacon2X, beacon2Y)
        )
    }
}

/**
 * Акустические концентрические звуковые волны по бокам сферы (DAVID_SPEAKING).
 */
private fun DrawScope.drawAcousticSoundwaveArcs(
    center: Offset,
    radius: Float,
    energy: Float,
    breathProgress: Float,
    color: Color
) {
    val arcCount = 2
    val maxExpansion = 22.dp.toPx() * energy.coerceIn(0.1f, 1f)

    for (i in 1..arcCount) {
        val extraDist = i * (10.dp.toPx() + maxExpansion * (i / 2f))
        val currentRadius = radius + extraDist
        val alpha = (0.52f - (i * 0.16f)).coerceAtLeast(0.10f) * (0.5f + energy * 0.5f)

        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = 142f,
            sweepAngle = 76f,
            useCenter = false,
            topLeft = Offset(center.x - currentRadius, center.y - currentRadius),
            size = Size(currentRadius * 2f, currentRadius * 2f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = -38f,
            sweepAngle = 76f,
            useCenter = false,
            topLeft = Offset(center.x - currentRadius, center.y - currentRadius),
            size = Size(currentRadius * 2f, currentRadius * 2f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Анимированные дуги подключения / рукопожатия WebSocket (CONNECTING / RECONNECTING).
 */
private fun DrawScope.drawConnectingHandshakeArcs(
    center: Offset,
    radius: Float,
    orbitProgress: Float,
    color: Color
) {
    val outerRadius = radius * 1.18f
    val baseRot = orbitProgress * 360f

    rotate(degrees = baseRot, pivot = center) {
        drawArc(
            color = color.copy(alpha = 0.78f),
            startAngle = 0f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color.copy(alpha = 0.78f),
            startAngle = 180f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Кратковременное расширяющееся кольцо успеха (SUCCESS).
 */
private fun DrawScope.drawSuccessExpansionRing(
    center: Offset,
    radius: Float,
    orbitProgress: Float,
    color: Color
) {
    val ringR = radius * (1f + 0.35f * orbitProgress)
    val ringAlpha = (1f - orbitProgress).coerceIn(0f, 0.90f)

    drawCircle(
        color = color.copy(alpha = ringAlpha),
        radius = ringR,
        center = center,
        style = Stroke(width = 2.4.dp.toPx())
    )
}

/**
 * Панель отладочного ручного переключения всех 13 состояний визуализатора (Requirement 8).
 * Доступна в debug-режиме для проверки анимаций и переходов без реального Gemini Live подключения.
 */
@Composable
fun DavidLiveVisualStateDebugBar(
    activeState: DavidLiveVisualState,
    onSelectState: (DavidLiveVisualState?) -> Unit,
    modifier: Modifier = Modifier
) {
    val states = listOf(
        DavidLiveVisualState.IDLE_LISTENING to "Ожидание",
        DavidLiveVisualState.USER_SPEAKING to "Пользователь",
        DavidLiveVisualState.THINKING to "Думаю",
        DavidLiveVisualState.FINANCE_LOOKUP to "Финансы",
        DavidLiveVisualState.ANALYZING to "Анализ",
        DavidLiveVisualState.PREPARING_RESPONSE to "Ответ",
        DavidLiveVisualState.DAVID_SPEAKING to "Давид",
        DavidLiveVisualState.CONNECTING to "Подключение",
        DavidLiveVisualState.RECONNECTING to "Переподключение",
        DavidLiveVisualState.OFFLINE to "Offline",
        DavidLiveVisualState.API_MISSING to "Нет API",
        DavidLiveVisualState.ERROR to "Ошибка",
        DavidLiveVisualState.SUCCESS to "Готово"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B1422))
            .border(1.dp, Color(0xFF1E2E44), RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧪 Режим тестирования состояний визуализатора",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Сброс (Авто)",
                color = EmeraldPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectState(null) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(states.size) { idx ->
                val (st, label) = states[idx]
                val isSelected = activeState == st
                val chipPalette = resolveSpherePalette(st)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) chipPalette.primary.copy(alpha = 0.22f) else Color(0xFF111C2E)
                        )
                        .border(
                            1.dp,
                            if (isSelected) chipPalette.primary else Color(0xFF22344D),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            if (isSelected) onSelectState(null) else onSelectState(st)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) chipPalette.primary else Color(0xFFCBD5E1),
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
