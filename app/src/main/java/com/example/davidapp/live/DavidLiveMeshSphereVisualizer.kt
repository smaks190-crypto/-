package com.example.davidapp.live

import android.provider.Settings
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
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
private val ObsidianCoreDark = Color(0xFF060D17)
private val ObsidianCoreDeep = Color(0xFF02060C)

// Изумрудная гамма (Ожидание, Говорит Давид)
private val EmeraldPrimary = Color(0xFF00E5A8)
private val EmeraldMint = Color(0xFF00F0A0)
private val EmeraldDeep = Color(0xFF059669)

// Бирюзовая гамма (Ты говоришь)
private val TurquoiseSpeech = Color(0xFF00D9F5)
private val TurquoiseCyan = Color(0xFF06B6D4)

// Cyan / Electric Blue (Думаю, Анализирую, Подключение)
private val CyanElectric = Color(0xFF00B4FF)
private val CyanSky = Color(0xFF38BDF8)
private val CyanDeep = Color(0xFF0284C7)

// Красная гамма (Нет связи / Offline)
private val CrimsonOffline = Color(0xFFEF4444)
private val CrimsonGlow = Color(0xFFDC2626)
private val CrimsonLight = Color(0xFFF87171)

// Золотистая гамма (Нет API-ключа)
private val GoldApiAmber = Color(0xFFF59E0B)
private val GoldApiGlow = Color(0xFFFBBF24)
private val GoldApiWarm = Color(0xFFD97706)

// Фиолетовая гамма (Ошибка)
private val VioletError = Color(0xFFA855F7)
private val VioletGlow = Color(0xFF9333EA)
private val VioletLight = Color(0xFFC084FC)

// Зелёная гамма (Готово / Успех)
private val SuccessGreen = Color(0xFF10B981)
private val SuccessMint = Color(0xFF00F0A0)
private val MutedSlatePrimary = Color(0xFF94A3B8)
private val MutedSlateSecondary = Color(0xFFCBD5E1)
private val MutedSlateGlow = Color(0xFF475569)

/**
 * Палитра визуального состояния 3D mesh-сферы.
 */
private data class SpherePalette(
    val primary: Color,
    val secondary: Color,
    val haloGlow: Color,
    val coreGlow: Color,
    val waveAccent: Color
)

internal fun shouldApplyMutedVisualPalette(
    state: DavidLiveVisualState,
    isMicMuted: Boolean
): Boolean {
    if (!isMicMuted) return false
    return when (state) {
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

/** Target angular velocity in degrees per second; it changes independently from phase. */
internal fun resolveSphereAngularSpeed(
    state: DavidLiveVisualState,
    isMicMuted: Boolean,
    audioEnergy: Float
): Float {
    if (shouldApplyMutedVisualPalette(state, isMicMuted)) return 8f
    val energy = audioEnergy.coerceIn(0f, 1f)
    return when (state) {
        DavidLiveVisualState.IDLE_LISTENING -> 10f
        DavidLiveVisualState.USER_SPEAKING -> 18f + energy * 14f
        DavidLiveVisualState.THINKING -> 14f
        DavidLiveVisualState.FINANCE_LOOKUP -> 20f
        DavidLiveVisualState.ANALYZING -> 16f
        DavidLiveVisualState.PREPARING_RESPONSE -> 18f
        DavidLiveVisualState.DAVID_SPEAKING -> 24f + energy * 16f
        DavidLiveVisualState.CONNECTING -> 12f
        DavidLiveVisualState.RECONNECTING -> 15f
        DavidLiveVisualState.OFFLINE,
        DavidLiveVisualState.API_MISSING,
        DavidLiveVisualState.ERROR -> 0f
        DavidLiveVisualState.SUCCESS -> 22f
    }
}

private fun resolveSpherePalette(
    state: DavidLiveVisualState,
    isMicMuted: Boolean = false
): SpherePalette {
    if (shouldApplyMutedVisualPalette(state, isMicMuted)) {
        return SpherePalette(
            primary = MutedSlatePrimary,
            secondary = MutedSlateSecondary,
            haloGlow = MutedSlateGlow.copy(alpha = 0.30f),
            coreGlow = MutedSlatePrimary.copy(alpha = 0.14f),
            waveAccent = MutedSlateSecondary
        )
    }
    return when (state) {
        DavidLiveVisualState.IDLE_LISTENING -> SpherePalette(
            primary = EmeraldPrimary,
            secondary = EmeraldMint,
            haloGlow = EmeraldDeep.copy(alpha = 0.35f),
            coreGlow = EmeraldPrimary.copy(alpha = 0.22f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.USER_SPEAKING -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = TurquoiseCyan,
            haloGlow = TurquoiseSpeech.copy(alpha = 0.45f),
            coreGlow = TurquoiseCyan.copy(alpha = 0.28f),
            waveAccent = TurquoiseSpeech
        )

        DavidLiveVisualState.THINKING -> SpherePalette(
            primary = CyanElectric,
            secondary = CyanSky,
            haloGlow = CyanDeep.copy(alpha = 0.42f),
            coreGlow = CyanElectric.copy(alpha = 0.25f),
            waveAccent = CyanElectric
        )

        DavidLiveVisualState.FINANCE_LOOKUP -> SpherePalette(
            primary = EmeraldMint,
            secondary = TurquoiseSpeech,
            haloGlow = EmeraldDeep.copy(alpha = 0.40f),
            coreGlow = GoldApiAmber.copy(alpha = 0.20f),
            waveAccent = EmeraldMint
        )

        DavidLiveVisualState.ANALYZING -> SpherePalette(
            primary = CyanElectric,
            secondary = EmeraldPrimary,
            haloGlow = CyanDeep.copy(alpha = 0.45f),
            coreGlow = CyanElectric.copy(alpha = 0.26f),
            waveAccent = CyanSky
        )

        DavidLiveVisualState.PREPARING_RESPONSE -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = EmeraldPrimary,
            haloGlow = EmeraldDeep.copy(alpha = 0.38f),
            coreGlow = TurquoiseSpeech.copy(alpha = 0.24f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.DAVID_SPEAKING -> SpherePalette(
            primary = EmeraldPrimary,
            secondary = TurquoiseSpeech,
            haloGlow = EmeraldDeep.copy(alpha = 0.50f),
            coreGlow = EmeraldMint.copy(alpha = 0.32f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING -> SpherePalette(
            primary = CyanElectric,
            secondary = CyanSky,
            haloGlow = CyanDeep.copy(alpha = 0.35f),
            coreGlow = CyanElectric.copy(alpha = 0.20f),
            waveAccent = CyanElectric
        )

        DavidLiveVisualState.OFFLINE -> SpherePalette(
            primary = CrimsonOffline,
            secondary = CrimsonLight,
            haloGlow = CrimsonGlow.copy(alpha = 0.42f),
            coreGlow = CrimsonOffline.copy(alpha = 0.25f),
            waveAccent = CrimsonOffline
        )

        DavidLiveVisualState.API_MISSING -> SpherePalette(
            primary = GoldApiAmber,
            secondary = GoldApiGlow,
            haloGlow = GoldApiWarm.copy(alpha = 0.40f),
            coreGlow = GoldApiAmber.copy(alpha = 0.24f),
            waveAccent = GoldApiAmber
        )

        DavidLiveVisualState.ERROR -> SpherePalette(
            primary = VioletError,
            secondary = VioletLight,
            haloGlow = VioletGlow.copy(alpha = 0.42f),
            coreGlow = VioletError.copy(alpha = 0.26f),
            waveAccent = VioletError
        )

        DavidLiveVisualState.SUCCESS -> SpherePalette(
            primary = SuccessMint,
            secondary = SuccessGreen,
            haloGlow = SuccessGreen.copy(alpha = 0.55f),
            coreGlow = SuccessMint.copy(alpha = 0.35f),
            waveAccent = SuccessMint
        )
    }
}

/**
 * 3D Mesh-сфера визуализатора **David Live** нового поколения (Issue #56).
 *
 * Воплощает визуальный язык Obsidian Neo-Bank:
 * - Прозрачная объёмная оболочка из тонких переплетённых светящихся линий (параллели, меридианы, диагонали).
 * - Глубокая тёмная обсидиановая сердцевина с мягким неоновым свечением.
 * - Плавные гармонические волны с защитой от резкого исчезновения (затухание 300–450 мс).
 * - Реакция на реальную аудио-энергию микрофона и воспроизведения Давида.
 * - Уникальные анимированные слои для каждого состояния:
 *   - `FINANCE_LOOKUP`: 3D-кольцо планетарного сканирования с финансовыми микро-акцентами (документ, график, монеты, календарь).
 *   - `ANALYZING`: перекрёстные орбитальные кольца с движущимися маяками данных.
 *   - `USER_SPEAKING`: эквалайзер входящей речи под сферой.
 *   - `DAVID_SPEAKING`: акустические дуги звукового давления по бокам сферы.
 *   - `OFFLINE`: красная mesh-сфера с «!», нажатие на сферу запускает reconnect.
 *   - `API_MISSING`: золотистая mesh-сфера с иконкой ключа, нажатие открывает настройки API.
 *   - `ERROR`: фиолетовая mesh-сфера с «!», повтор по нажатию.
 *   - `SUCCESS`: зелёная пульсация с подтверждающей галочкой.
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
    val lifecycleOwner = LocalLifecycleOwner.current
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
    var lifecycleActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleActive = when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> false
                else -> lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val ambientMotionEnabled = lifecycleActive && !reduceMotion &&
        visualState != DavidLiveVisualState.OFFLINE &&
        visualState != DavidLiveVisualState.API_MISSING &&
        visualState != DavidLiveVisualState.ERROR

    val targetAngularSpeed = resolveSphereAngularSpeed(visualState, isMicMuted, overallEnergy)
    val animatedAngularSpeed by animateFloatAsState(
        targetValue = targetAngularSpeed,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sphere_angular_speed"
    )
    val currentAngularSpeed by rememberUpdatedState(animatedAngularSpeed)
    var rotationYawDegrees by remember { mutableFloatStateOf(0f) }
    var breathProgress by remember { mutableFloatStateOf(0.5f) }
    var orbitProgress by remember { mutableFloatStateOf(0f) }

    // One frame loop drives the whole sphere. It pauses entirely when hidden, offline,
    // reduced-motion is enabled, or the lifecycle owner leaves the foreground.
    LaunchedEffect(ambientMotionEnabled) {
        if (!ambientMotionEnabled) return@LaunchedEffect
        var previousFrameMillis = 0L
        var elapsedMotionMillis = 0f
        while (isActive) {
            withFrameMillis { frameMillis ->
                if (previousFrameMillis != 0L) {
                    val deltaMillis = (frameMillis - previousFrameMillis).coerceIn(0L, 32L)
                    elapsedMotionMillis += deltaMillis
                    rotationYawDegrees = (rotationYawDegrees +
                        deltaMillis * currentAngularSpeed / 1000f) % 360f
                    orbitProgress = (orbitProgress + deltaMillis / 2400f) % 1f
                    val breathPhase = elapsedMotionMillis / 3200f * PI.toFloat()
                    breathProgress = 0.5f + 0.5f * sin(breathPhase)
                }
                previousFrameMillis = frameMillis
            }
        }
    }

    val targetPalette = resolveSpherePalette(visualState, isMicMuted)

    // Плавная интерполяция цветов при переходе между состояниями (400 мс)
    val animatedPrimaryColor by animateColorAsState(
        targetValue = targetPalette.primary,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "mesh_primary_color"
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = targetPalette.secondary,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "mesh_secondary_color"
    )
    val animatedHaloColor by animateColorAsState(
        targetValue = targetPalette.haloGlow,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "mesh_halo_color"
    )
    val animatedCoreGlowColor by animateColorAsState(
        targetValue = targetPalette.coreGlow,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "mesh_core_color"
    )

    // Плавное затухание амплитуды волн (380 мс) — предотвращает резкий обрыв анимации речи
    val isSpeechActive =
        (visualState == DavidLiveVisualState.USER_SPEAKING && !isMicMuted) ||
            visualState == DavidLiveVisualState.DAVID_SPEAKING
    val targetAudioEnergy = if (isSpeechActive) overallEnergy.coerceIn(0.08f, 1f) else 0.04f

    val smoothedEnergy by animateFloatAsState(
        targetValue = targetAudioEnergy,
        animationSpec = tween(
            durationMillis = if (isSpeechActive) 90 else 380,
            easing = FastOutSlowInEasing
        ),
        label = "mesh_energy_smoothing"
    )
    val smoothedRayMagnitudes = rayMagnitudes.mapIndexed { index, magnitude ->
        val smoothed by animateFloatAsState(
            targetValue = magnitude.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
            label = "mesh_ray_smoothing_$index"
        )
        smoothed
    }

    var touchPosition by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var isTouchActive by remember { mutableStateOf(false) }
    val touchStrength by animateFloatAsState(
        targetValue = if (isTouchActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isTouchActive) 150 else 520,
            easing = FastOutSlowInEasing
        ),
        label = "sphere_touch_strength"
    )
    LaunchedEffect(lifecycleActive) {
        if (!lifecycleActive) isTouchActive = false
    }
    val sphereActionDescription = when (visualState) {
        DavidLiveVisualState.OFFLINE -> "Связь потеряна. Нажмите на сферу для повторного подключения."
        DavidLiveVisualState.API_MISSING -> "Требуется API-ключ Gemini. Нажмите для настройки."
        DavidLiveVisualState.ERROR -> "Ошибка связи. Нажмите для повтора."
        else -> if (isMicMuted) "Микрофон выключен. Нажмите, чтобы включить." else "Голосовой помощник David Live"
    }

    Box(
        modifier = modifier
            .size(visualizerSize)
            .davidLiveSphereGesture(
                actionDescription = sphereActionDescription,
                onTap = onSphereClick,
                onLongPressStart = { point ->
                    touchPosition = point
                    isTouchActive = true
                },
                onLongPressMove = { point -> touchPosition = point },
                onLongPressEnd = { isTouchActive = false },
                onLongPressCancel = { isTouchActive = false }
            )
            .testTag("david_live_circular_visualizer")
            .testTag("david_live_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(visualizerSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.70f

            // 1. Внешнее неоновое атмосферное свечение (Halo)
            val haloRadius = baseRadius * (1.18f + 0.08f * breathProgress + smoothedEnergy * 0.15f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedHaloColor,
                        animatedHaloColor.copy(alpha = animatedHaloColor.alpha * 0.40f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )

            // 2. Тёмная сердцевина сферы (Obsidian Core)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedCoreGlowColor,
                        ObsidianCoreDark,
                        ObsidianCoreDeep
                    ),
                    center = center,
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center
            )

            // 2.5. Живые внутренние энергетические слои: объёмная динамика самого ядра.
            drawLivingCore(
                center = center,
                radius = baseRadius,
                phaseDegrees = rotationYawDegrees,
                breathProgress = breathProgress,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor
            )

            // 3. Отрисовка 3D Mesh-сферы (параллели, меридианы, узлы)
            drawWireframeMeshSphere(
                center = center,
                radius = baseRadius,
                breathProgress = breathProgress,
                yawDegrees = rotationYawDegrees,
                orbitProgress = orbitProgress,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                visualState = visualState
            )

            // Внешние орбиты, кольца и дуги отключены: оставляем чистый силуэт сферы.

            // 4.5. Локальная магнитная концентрация света под удерживаемым пальцем.
            if (touchStrength > 0.002f) {
                drawTouchConcentration(
                    center = center,
                    radius = baseRadius,
                    normalizedTouch = touchPosition,
                    strength = touchStrength,
                    phaseProgress = orbitProgress,
                    color = animatedSecondaryColor
                )
            }

            // 5. Нижний эквалайзер речи (для USER_SPEAKING и DAVID_SPEAKING)
            if (isSpeechActive) {
                drawSpeechEqualizerBars(
                    center = center,
                    sphereRadius = baseRadius,
                    energy = smoothedEnergy,
                    rayMagnitudes = smoothedRayMagnitudes,
                    color = animatedPrimaryColor
                )
            }
        }

        // 6. Центральный контекстный бейдж для состояний с явным фокусом (Offline, No API, Error, Success)
        when (visualState) {
            DavidLiveVisualState.OFFLINE -> {
                CentralStatusBadge(
                    badgeColor = CrimsonOffline,
                    glowColor = CrimsonGlow,
                    icon = Icons.Default.PriorityHigh,
                    contentDescription = "Связь потеряна"
                )
            }

            DavidLiveVisualState.API_MISSING -> {
                CentralStatusBadge(
                    badgeColor = GoldApiAmber,
                    glowColor = GoldApiWarm,
                    icon = Icons.Default.Key,
                    contentDescription = "Требуется API-ключ"
                )
            }

            DavidLiveVisualState.ERROR -> {
                CentralStatusBadge(
                    badgeColor = VioletError,
                    glowColor = VioletGlow,
                    icon = Icons.Default.PriorityHigh,
                    contentDescription = "Ошибка связи"
                )
            }

            DavidLiveVisualState.SUCCESS -> {
                CentralStatusBadge(
                    badgeColor = SuccessGreen,
                    glowColor = SuccessMint,
                    icon = Icons.Default.Check,
                    contentDescription = "Успешно"
                )
            }

            else -> {
                // A compact microphone state indicator stays inside the sphere in both modes.
                // The sphere itself remains the tap target, preserving existing mute callbacks.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 68.dp, bottom = 68.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(ObsidianCoreDeep.copy(alpha = 0.92f))
                        .border(
                            width = 1.dp,
                            color = if (isMicMuted) MutedSlatePrimary.copy(alpha = 0.86f)
                            else animatedPrimaryColor.copy(alpha = 0.78f),
                            shape = CircleShape
                        )
                        .testTag("david_live_sphere_mic_indicator"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMicMuted) {
                            "Микрофон выключен"
                        } else {
                            "Микрофон включён"
                        },
                        tint = if (isMicMuted) MutedSlateSecondary else animatedPrimaryColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/**
 * Несколько смещённых световых полей и внутренних частиц создают движение самого ядра,
 * а не только вращение внешней сетки. Все вычисления ограничены локальным кругом сферы.
 */
private fun DrawScope.drawLivingCore(
    center: Offset,
    radius: Float,
    phaseDegrees: Float,
    breathProgress: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color
) {
    val phase = phaseDegrees * (PI / 180.0)
    val driftA = Offset(
        x = (cos(phase) * radius * 0.18f).toFloat(),
        y = (sin(phase * 0.73) * radius * 0.12f).toFloat()
    )
    val driftB = Offset(
        x = (sin(phase * 0.61) * radius * 0.22f).toFloat(),
        y = (cos(phase * 0.89) * radius * 0.18f).toFloat()
    )
    val fieldRadius = radius * (0.58f + 0.025f * breathProgress)
    val fieldAlpha = (0.10f + 0.10f * energy).coerceIn(0.08f, 0.22f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = fieldAlpha),
                primaryColor.copy(alpha = fieldAlpha * 0.28f),
                Color.Transparent
            ),
            center = center + driftA,
            radius = fieldRadius
        ),
        radius = fieldRadius,
        center = center + driftA
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = fieldAlpha * 0.72f),
                secondaryColor.copy(alpha = fieldAlpha * 0.18f),
                Color.Transparent
            ),
            center = center + driftB,
            radius = fieldRadius * 0.78f
        ),
        radius = fieldRadius * 0.78f,
        center = center + driftB
    )

    for (index in 0 until 16) {
        val t = phase + index * (2.0 * PI / 16.0)
        val orbitRadius = radius * (0.22f + 0.11f * sin(t * 1.7).toFloat())
        val x = (cos(t) * orbitRadius).toFloat()
        val y = (sin(t * 1.23) * orbitRadius * 0.72f).toFloat()
        val inside = (x * x + y * y) / (radius * radius)
        if (inside < 0.46f) {
            val depth = ((sin(t * 0.83) + 1.0) * 0.5).toFloat()
            val color = if (index % 2 == 0) primaryColor else secondaryColor
            drawCircle(
                color = color.copy(alpha = (0.18f + depth * 0.30f) * (0.7f + energy * 0.3f)),
                radius = 1.2.dp.toPx() + depth * 1.2.dp.toPx(),
                center = center + Offset(x, y)
            )
        }
    }
}

/**
 * Проецирует удерживаемое касание на переднюю полусферу. Глубина точки определяет
 * масштаб и яркость частиц, поэтому притяжение ощущается как взаимодействие с объёмом.
 */
private fun DrawScope.drawTouchConcentration(
    center: Offset,
    radius: Float,
    normalizedTouch: Offset,
    strength: Float,
    phaseProgress: Float,
    color: Color
) {
    var nx = normalizedTouch.x * 2f - 1f
    var ny = normalizedTouch.y * 2f - 1f
    val distanceSquared = nx * nx + ny * ny
    if (distanceSquared > 0.82f) {
        val scale = sqrt(0.82f / distanceSquared)
        nx *= scale
        ny *= scale
    }
    val projectedRadiusSquared = (nx * nx + ny * ny).coerceIn(0f, 0.99f)
    val depth = sqrt(1f - projectedRadiusSquared)
    val touchCenter = center + Offset(nx * radius * 0.78f, ny * radius * 0.78f)
    val depthScale = 0.76f + depth * 0.32f
    val haloRadius = radius * (0.15f + depth * 0.06f) * depthScale

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.36f * strength),
                color.copy(alpha = 0.13f * strength),
                Color.Transparent
            ),
            center = touchCenter,
            radius = haloRadius
        ),
        radius = haloRadius,
        center = touchCenter
    )

    repeat(11) { index ->
        val angle = phaseProgress * (2.0 * PI) + index * (2.0 * PI / 11.0)
        val orbitRadius = radius * (0.045f + 0.095f * (1f - strength))
        val x = touchCenter.x + cos(angle).toFloat() * orbitRadius * depthScale
        val y = touchCenter.y + sin(angle * 1.13).toFloat() * orbitRadius * depthScale * 0.72f
        val frontWeight = ((sin(angle + depth) + 1.0) * 0.5).toFloat()
        drawCircle(
            color = color.copy(alpha = strength * (0.30f + frontWeight * 0.55f)),
            radius = (1.3f + frontWeight * 1.7f) * depthScale,
            center = Offset(x, y)
        )
    }
}

/**
 * Отрисовка объёмной 3D-сетки mesh-сферы с изометрическим наклоном и перспективой.
 */
private fun DrawScope.drawWireframeMeshSphere(
    center: Offset,
    radius: Float,
    breathProgress: Float,
    yawDegrees: Float,
    orbitProgress: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    visualState: DavidLiveVisualState
) {
    val pitchRadians = (28.0 * PI / 180.0).toFloat() // Фиксированный угол наклона 3D (28°)
    val yawRadians = (yawDegrees * PI / 180.0).toFloat()
    val cosPitch = cos(pitchRadians)
    val sinPitch = sin(pitchRadians)
    val cosYaw = cos(yawRadians)
    val sinYaw = sin(yawRadians)

    // Динамический радиус с учётом дыхания и звуковой энергии
    val breathingRadius = radius * (1f + 0.035f * sin(breathProgress * 2f * PI.toFloat()) + energy * 0.12f)
    val cameraDistance = breathingRadius * 3.4f

    // 1. Отрисовка параллелей (широтных колец)
    val latitudeAngles = floatArrayOf(-60f, -40f, -20f, 0f, 20f, 40f, 60f)
    val stepTheta = (PI * 2.0 / 32.0).toFloat()

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

        for (i in 0..32) {
            val theta = i * stepTheta
            // Гармоническая деформация поверхности от аудиосигнала
            val waveDeform = if (energy > 0.08f) {
                0.045f * energy * sin(theta * 4f + phi * 3f + yawRadians * 2f)
            } else {
                0f
            }
            val rCurrent = latRadius * (1f + waveDeform)

            val x0 = rCurrent * cos(theta)
            val z0 = rCurrent * sin(theta)

            // Вращение по Yaw (ось Y)
            val x1 = x0 * cosYaw + z0 * sinYaw
            val z1 = -x0 * sinYaw + z0 * cosYaw

            // Вращение по Pitch (ось X)
            val y2 = y0 * cosPitch - z1 * sinPitch
            val z2 = y0 * sinPitch + z1 * cosPitch

            // Перспективная проекция на плоскость экрана
            val perspectiveK = cameraDistance / (cameraDistance - z2)
            val screenX = center.x + x1 * perspectiveK
            val screenY = center.y + y2 * perspectiveK

            if (!isFirst) {
                val isFront = (z2 + prevZ) / 2f >= -breathingRadius * 0.15f
                val strokeAlpha = if (isFront) {
                    0.85f.coerceAtMost(0.40f + (z2 / breathingRadius) * 0.50f)
                } else {
                    0.18f
                }
                val strokeWidth = if (isFront) 1.9.dp.toPx() else 1.0.dp.toPx()
                val lineColor = if (latDeg == 0f) secondaryColor else primaryColor

                drawLine(
                    color = lineColor.copy(alpha = strokeAlpha.coerceIn(0.08f, 0.95f)),
                    start = Offset(prevScreenX, prevScreenY),
                    end = Offset(screenX, screenY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            prevScreenX = screenX
            prevScreenY = screenY
            prevZ = z2
            isFirst = false
        }
    }

    // 2. Отрисовка меридианов (продольных дуг)
    val meridianCount = 8
    val stepPhi = (PI / 20.0).toFloat()

    for (m in 0 until meridianCount) {
        val baseTheta = (m * (PI * 2.0 / meridianCount)).toFloat()
        var prevScreenX = 0f
        var prevScreenY = 0f
        var prevZ = 0f
        var isFirst = true

        for (j in -10..10) {
            val phi = j * stepPhi
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)

            val x0 = breathingRadius * cosPhi * cos(baseTheta)
            val z0 = breathingRadius * cosPhi * sin(baseTheta)
            val y0 = breathingRadius * sinPhi

            val x1 = x0 * cosYaw + z0 * sinYaw
            val z1 = -x0 * sinYaw + z0 * cosYaw

            val y2 = y0 * cosPitch - z1 * sinPitch
            val z2 = y0 * sinPitch + z1 * cosPitch

            val perspectiveK = cameraDistance / (cameraDistance - z2)
            val screenX = center.x + x1 * perspectiveK
            val screenY = center.y + y2 * perspectiveK

            if (!isFirst) {
                val isFront = (z2 + prevZ) / 2f >= 0f
                val strokeAlpha = if (isFront) 0.60f else 0.14f
                val strokeWidth = if (isFront) 1.5.dp.toPx() else 0.9.dp.toPx()

                drawLine(
                    color = primaryColor.copy(alpha = strokeAlpha),
                    start = Offset(prevScreenX, prevScreenY),
                    end = Offset(screenX, screenY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }

            prevScreenX = screenX
            prevScreenY = screenY
            prevZ = z2
            isFirst = false
        }
    }

    // 3. Светящиеся узлы (частицы) на пересечениях сетки
    val nodePoints = floatArrayOf(
        0f, 0f,
        PI.toFloat() / 2f, 20f,
        PI.toFloat(), -20f,
        3f * PI.toFloat() / 2f, 40f,
        PI.toFloat() / 4f, -40f,
        3f * PI.toFloat() / 4f, 20f,
        5f * PI.toFloat() / 4f, 0f,
        7f * PI.toFloat() / 4f, -20f
    )

    var idx = 0
    while (idx < nodePoints.size) {
        val theta = nodePoints[idx]
        val phi = (nodePoints[idx + 1] * PI / 180.0).toFloat()
        idx += 2

        val x0 = breathingRadius * cos(phi) * cos(theta)
        val z0 = breathingRadius * cos(phi) * sin(theta)
        val y0 = breathingRadius * sin(phi)

        val x1 = x0 * cosYaw + z0 * sinYaw
        val z1 = -x0 * sinYaw + z0 * cosYaw

        val y2 = y0 * cosPitch - z1 * sinPitch
        val z2 = y0 * sinPitch + z1 * cosPitch

        if (z2 >= -breathingRadius * 0.2f) {
            val perspectiveK = cameraDistance / (cameraDistance - z2)
            val screenX = center.x + x1 * perspectiveK
            val screenY = center.y + y2 * perspectiveK
            val nodeRadius = (2.4.dp.toPx() * perspectiveK).coerceIn(1.8.dp.toPx(), 4.5.dp.toPx())

            drawCircle(
                color = secondaryColor.copy(alpha = 0.85f),
                radius = nodeRadius,
                center = Offset(screenX, screenY)
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = nodeRadius * 2.2f,
                center = Offset(screenX, screenY)
            )
        }
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
    val ringRadiusX = radius * 1.34f
    val ringRadiusY = radius * 0.44f
    val tiltAngleDegrees = -18f

    rotate(degrees = tiltAngleDegrees, pivot = center) {
        // Фоновое свечение экваториального кольца
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
            style = Stroke(width = 3.2.dp.toPx())
        )

        // Бегущий импульс сканирования по периметру кольца
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

        // 4 финансовых микро-акцента по квадрантам кольца
        val angles = floatArrayOf(
            0.15f * PI.toFloat(),
            0.65f * PI.toFloat(),
            1.15f * PI.toFloat(),
            1.65f * PI.toFloat()
        )
        for ((iconIdx, ang) in angles.withIndex()) {
            val posX = center.x + ringRadiusX * cos(ang)
            val posY = center.y + ringRadiusY * sin(ang)

            drawCircle(
                color = ObsidianCoreDeep,
                radius = 7.5.dp.toPx(),
                center = Offset(posX, posY)
            )
            drawCircle(
                color = goldColor.copy(alpha = 0.85f),
                radius = 7.5.dp.toPx(),
                center = Offset(posX, posY),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Микро-иконки в зависимости от квадранта:
            // 0 -> Документ/чек, 1 -> График/аналитика, 2 -> Монета, 3 -> Календарь
            when (iconIdx) {
                0 -> { // Документ
                    drawLine(
                        color = goldColor,
                        start = Offset(posX - 3.dp.toPx(), posY - 2.dp.toPx()),
                        end = Offset(posX + 3.dp.toPx(), posY - 2.dp.toPx()),
                        strokeWidth = 1.1.dp.toPx()
                    )
                    drawLine(
                        color = goldColor,
                        start = Offset(posX - 3.dp.toPx(), posY + 1.dp.toPx()),
                        end = Offset(posX + 1.dp.toPx(), posY + 1.dp.toPx()),
                        strokeWidth = 1.1.dp.toPx()
                    )
                }

                1 -> { // Столбцы графика
                    drawLine(
                        color = accentColor,
                        start = Offset(posX - 3.dp.toPx(), posY + 3.dp.toPx()),
                        end = Offset(posX - 3.dp.toPx(), posY - 1.dp.toPx()),
                        strokeWidth = 1.3.dp.toPx()
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(posX, posY + 3.dp.toPx()),
                        end = Offset(posX, posY - 3.dp.toPx()),
                        strokeWidth = 1.3.dp.toPx()
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(posX + 3.dp.toPx(), posY + 3.dp.toPx()),
                        end = Offset(posX + 3.dp.toPx(), posY + 1.dp.toPx()),
                        strokeWidth = 1.3.dp.toPx()
                    )
                }

                2 -> { // Монета
                    drawCircle(
                        color = goldColor,
                        radius = 2.4.dp.toPx(),
                        center = Offset(posX, posY)
                    )
                }

                3 -> { // Календарь
                    drawRect(
                        color = accentColor,
                        topLeft = Offset(posX - 2.5.dp.toPx(), posY - 2.5.dp.toPx()),
                        size = Size(5.dp.toPx(), 5.dp.toPx()),
                        style = Stroke(width = 1.0.dp.toPx())
                    )
                }
            }
        }
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
    val orbitRadiusX = radius * 1.28f
    val orbitRadiusY = radius * 0.46f

    // 1-я орбита (+34 градуса)
    rotate(degrees = 34f, pivot = center) {
        drawOval(
            color = cyanColor.copy(alpha = 0.35f),
            topLeft = Offset(center.x - orbitRadiusX, center.y - orbitRadiusY),
            size = Size(orbitRadiusX * 2f, orbitRadiusY * 2f),
            style = Stroke(width = 1.6.dp.toPx())
        )
        val ang1 = orbitProgress * 2f * PI.toFloat()
        val beacon1X = center.x + orbitRadiusX * cos(ang1)
        val beacon1Y = center.y + orbitRadiusY * sin(ang1)
        drawCircle(
            color = cyanColor,
            radius = 3.8.dp.toPx(),
            center = Offset(beacon1X, beacon1Y)
        )
    }

    // 2-я орбита (-34 градуса)
    rotate(degrees = -34f, pivot = center) {
        drawOval(
            color = emeraldColor.copy(alpha = 0.35f),
            topLeft = Offset(center.x - orbitRadiusX, center.y - orbitRadiusY),
            size = Size(orbitRadiusX * 2f, orbitRadiusY * 2f),
            style = Stroke(width = 1.6.dp.toPx())
        )
        val ang2 = (orbitProgress + 0.5f) * 2f * PI.toFloat()
        val beacon2X = center.x + orbitRadiusX * cos(ang2)
        val beacon2Y = center.y + orbitRadiusY * sin(ang2)
        drawCircle(
            color = emeraldColor,
            radius = 3.8.dp.toPx(),
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
    val arcCount = 3
    val maxExpansion = 28.dp.toPx() * energy.coerceIn(0.1f, 1f)

    for (i in 1..arcCount) {
        val extraDist = i * (8.dp.toPx() + maxExpansion * (i / 3f))
        val currentRadius = radius + extraDist
        val alpha = (0.75f - (i * 0.20f)).coerceAtLeast(0.12f) * (0.6f + energy * 0.4f)

        // Левая акустическая дуга
        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - currentRadius, center.y - currentRadius),
            size = Size(currentRadius * 2f, currentRadius * 2f),
            style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
        )

        // Правая акустическая дуга
        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - currentRadius, center.y - currentRadius),
            size = Size(currentRadius * 2f, currentRadius * 2f),
            style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
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
            color = color.copy(alpha = 0.85f),
            startAngle = 0f,
            sweepAngle = 75f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color.copy(alpha = 0.85f),
            startAngle = 180f,
            sweepAngle = 75f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
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
    val ringAlpha = (1f - orbitProgress).coerceIn(0f, 0.95f)

    drawCircle(
        color = color.copy(alpha = ringAlpha),
        radius = ringR,
        center = center,
        style = Stroke(width = 2.6.dp.toPx())
    )
}

/**
 * Горизонтальный эквалайзер речи под сферой (как на дизайн-макете "Ты говоришь").
 */
private fun DrawScope.drawSpeechEqualizerBars(
    center: Offset,
    sphereRadius: Float,
    energy: Float,
    rayMagnitudes: List<Float>,
    color: Color
) {
    val barCount = 20
    val totalWidth = sphereRadius * 1.6f
    val startX = center.x - totalWidth / 2f
    val barY = center.y + sphereRadius * 1.16f
    val barSpacing = totalWidth / (barCount - 1)
    val maxBarHeight = 18.dp.toPx()

    for (b in 0 until barCount) {
        val rayIdx = (b * (DavidLiveSpectrumAnalyzer.RAY_COUNT / barCount)).coerceIn(0, rayMagnitudes.lastIndex)
        val rawMag = rayMagnitudes.getOrElse(rayIdx) { 0f }
        val centerFalloff = 1f - abs((b - barCount / 2f) / (barCount / 2f)) * 0.45f
        val h = (maxBarHeight * rawMag * centerFalloff * (0.4f + energy * 0.6f)).coerceIn(2.5.dp.toPx(), maxBarHeight)

        val bx = startX + b * barSpacing
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = Offset(bx, barY - h / 2f),
            end = Offset(bx, barY + h / 2f),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * Центральный светящийся бейдж с иконкой (восклицательный знак, ключ, галочка) для фокусных состояний.
 */
@Composable
private fun CentralStatusBadge(
    badgeColor: Color,
    glowColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        badgeColor.copy(alpha = 0.35f),
                        ObsidianCoreDeep.copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.5.dp, badgeColor.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = badgeColor,
            modifier = Modifier.size(26.dp)
        )
    }
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
