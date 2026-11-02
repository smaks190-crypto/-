package com.example.davidapp.live

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Цветовые токены новой объёмной 3D-сферы David Live (Obsidian Quantum Plasma Orb, Issue #56).
 */
private val ObsidianVoidCore = Color(0xFF030710)
private val ObsidianDeepShadow = Color(0xFF010308)
private val ObsidianSubsurfaceDark = Color(0xFF06101E)

// Изумрудная гамма (Ожидание — Медленное медитативное 3D-вращение)
private val EmeraldPrimary = Color(0xFF00E5A8)
private val EmeraldMint = Color(0xFF34F5C5)
private val EmeraldDeep = Color(0xFF047857)

// Бирюзово-голубая гамма (Ты говоришь — Ускоренное 3D-вращение)
private val TurquoiseSpeech = Color(0xFF00D9F5)
private val TurquoiseCyan = Color(0xFF00B4FF)
private val SkyBlueGlow = Color(0xFF38BDF8)

// Индиго-фиолетовая / электро-синяя гамма (Давид думает / анализирует)
private val ThinkingIndigo = Color(0xFF6366F1)
private val ThinkingViolet = Color(0xFF8B5CF6)
private val ThinkingCyan = Color(0xFF38BDF8)

// Ярко-бирюзовая / изумрудная гамма (Давид отвечает — Активное живое вращение)
private val DavidSpeakingTurquoise = Color(0xFF00F5D4)
private val DavidSpeakingEmerald = Color(0xFF00E5A8)

// Приглушённая серебристо-сланцевая гамма (Микрофон выключен — Приглушённая 3D-сфера)
internal val MutedSlatePrimary = Color(0xFF94A3B8)
internal val MutedSilverSecondary = Color(0xFFCBD5E1)
private val MutedSlateGlow = Color(0xFF475569)
private val MutedSlateCore = Color(0xFF334155)

// Красная гамма (Нет интернета — Красная 3D-сфера)
private val CrimsonOffline = Color(0xFFEF4444)
private val CrimsonGlow = Color(0xFFDC2626)
private val CrimsonLight = Color(0xFFF87171)

// Золотисто-янтарная гамма (API требуется — Золотая 3D-сфера)
private val GoldApiAmber = Color(0xFFF59E0B)
private val GoldApiGlow = Color(0xFFFBBF24)
private val GoldApiWarm = Color(0xFFD97706)

// Маджента-фиолетовая гамма (Ошибка — Пульсирующая 3D-сфера)
private val VioletError = Color(0xFFA855F7)
private val CrimsonMagentaError = Color(0xFFF43F5E)
private val VioletGlow = Color(0xFF9333EA)

// Зелёная гамма (Готово / Успех)
private val SuccessGreen = Color(0xFF10B981)
private val SuccessMint = Color(0xFF00F0A0)

/**
 * Порог удержания пальца на сфере (в мс), после которого активируется
 * интерактивное 3D-притяжение плазменных сгустков и потоков вместо короткого нажатия (tap).
 */
internal const val SPHERE_TOUCH_HOLD_ACTIVATION_MS = 165L

/**
 * Классификация жеста пользователя на 3D-сфере David Live:
 * - [SHORT_TAP_ACTION] — короткое нажатие без перетаскивания (выполняет штатное контекстное действие сферы:
 *   переключение микрофона, открытие настроек API-ключа или переподключение).
 * - [HOLD_OR_DRAG_ATTRACTION] — удержание или перемещение пальца по поверхности сферы
 *   (запускает 3D-притяжение плазменных сгустков и потоков к точке касания без вызова tap-действия).
 * - [CANCELLED] — жест отменён системой или сворачиванием экрана.
 */
internal enum class SphereTouchGestureOutcome {
    SHORT_TAP_ACTION,
    HOLD_OR_DRAG_ATTRACTION,
    CANCELLED
}

/**
 * Детерминированный классификатор жеста на сфере, разделяющий короткий tap и удержание/drag.
 */
internal fun evaluateSphereTouchGesture(
    pressDurationMs: Long,
    maxDragDistancePx: Float,
    touchSlopPx: Float,
    wasCancelled: Boolean = false
): SphereTouchGestureOutcome {
    if (wasCancelled) {
        return SphereTouchGestureOutcome.CANCELLED
    }
    if (pressDurationMs >= SPHERE_TOUCH_HOLD_ACTIVATION_MS || maxDragDistancePx > touchSlopPx) {
        return SphereTouchGestureOutcome.HOLD_OR_DRAG_ATTRACTION
    }
    return SphereTouchGestureOutcome.SHORT_TAP_ACTION
}

/**
 * Трёхмерная проекция точки касания пальца на выпуклую переднюю полусферу (`z >= 0`).
 */
@Immutable
internal data class SphereTouchPoint3D(
    val normX: Float,
    val normY: Float,
    val normZ: Float,
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float,
    val projectedScreenOffset: Offset,
    val perspectiveScale: Float,
    val limbForeshortening: Float
)

/**
 * Проецирует экранную точку касания [touchOffset] на трёхмерную выпуклую поверхность сферы
 * с центром [sphereCenter] и радиусом [sphereRadius].
 *
 * Вычисляет честную глубину `normZ = sqrt(1 - normX^2 - normY^2)` на передней полусфере,
 * коэффициент перспективного масштаба и ракурсное сжатие (`limbForeshortening`) у краёв сферы.
 */
internal fun projectTouchTo3DSphere(
    touchOffset: Offset,
    sphereCenter: Offset,
    sphereRadius: Float
): SphereTouchPoint3D {
    val safeRadius = sphereRadius.coerceAtLeast(1f)
    val rawDx = (touchOffset.x - sphereCenter.x) / safeRadius
    val rawDy = (touchOffset.y - sphereCenter.y) / safeRadius
    val radialDist = sqrt(rawDx * rawDx + rawDy * rawDy)
    val maxNormRadius = 0.95f

    val normX: Float
    val normY: Float
    if (radialDist > maxNormRadius && radialDist > 0.0001f) {
        val clampScale = maxNormRadius / radialDist
        normX = rawDx * clampScale
        normY = rawDy * clampScale
    } else {
        normX = rawDx
        normY = rawDy
    }

    val normZ = sqrt((1f - normX * normX - normY * normY).coerceAtLeast(0.04f))
    val surfaceShellRatio = 0.88f
    val worldX = normX * safeRadius * surfaceShellRatio
    val worldY = normY * safeRadius * surfaceShellRatio
    val worldZ = normZ * safeRadius * surfaceShellRatio

    val cameraDistance = safeRadius * 3.1f
    val perspectiveScale = (cameraDistance / (cameraDistance - worldZ).coerceAtLeast(safeRadius * 0.75f))
        .coerceIn(0.82f, 1.46f)

    val projectedScreenOffset = Offset(
        x = sphereCenter.x + worldX * perspectiveScale,
        y = sphereCenter.y + worldY * perspectiveScale
    )
    val limbForeshortening = (0.40f + 0.60f * normZ).coerceIn(0.40f, 1.0f)

    return SphereTouchPoint3D(
        normX = normX,
        normY = normY,
        normZ = normZ,
        worldX = worldX,
        worldY = worldY,
        worldZ = worldZ,
        projectedScreenOffset = projectedScreenOffset,
        perspectiveScale = perspectiveScale,
        limbForeshortening = limbForeshortening
    )
}

/**
 * Плавно обновляет интенсивность притяжения световых сгустков к точке касания (`0f..1f`):
 * - при активном удержании/перетаскивании (`isTouchActive == true`) плавно нарастает к `1f`;
 * - после отпускания пальца (`isTouchActive == false`) мягко рассеивается обратно к `0f`
 *   без резких скачков и без сброса базовой фазы вращения сферы.
 */
internal fun advanceTouchAttractionStrength(
    currentStrength: Float,
    isTouchActive: Boolean,
    deltaMs: Long,
    reduceMotion: Boolean = false
): Float {
    val clampedCurrent = currentStrength.coerceIn(0f, 1f)
    if (reduceMotion) {
        return if (isTouchActive) 1f else 0f
    }
    if (deltaMs <= 0L) return clampedCurrent
    val dtSec = (deltaMs.coerceIn(1L, 64L)) / 1000f
    return if (isTouchActive) {
        val riseRate = 6.5f
        val factor = 1f - exp(-dtSec * riseRate)
        (clampedCurrent + (1f - clampedCurrent) * factor).coerceIn(0f, 1f)
    } else {
        val decayRate = 3.8f
        val next = clampedCurrent * exp(-dtSec * decayRate)
        if (next < 0.004f) 0f else next.coerceIn(0f, 1f)
    }
}

/**
 * Непрерывно и плавно интерполирует нормализованные координаты точки касания на сфере
 * при движении пальца без рывков и отставания.
 */
internal fun interpolateTouchSurfaceCoordinate(
    currentValue: Float,
    targetValue: Float,
    deltaMs: Long
): Float {
    if (deltaMs <= 0L) return targetValue
    val dtSec = (deltaMs.coerceIn(1L, 64L)) / 1000f
    val followRate = 34f
    val alpha = (1f - exp(-dtSec * followRate)).coerceIn(0.15f, 1f)
    return currentValue + (targetValue - currentValue) * alpha
}

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
 * Вычисляет целевую угловую скорость 3D-вращения сферы и её энергетических лент (рад/с)
 * в зависимости от реального состояния [DavidLiveVisualState], флага `isMicMuted` и аудиоэнергии.
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
            haloGlow = EmeraldDeep.copy(alpha = 0.42f),
            coreGlow = EmeraldPrimary.copy(alpha = 0.32f),
            waveAccent = EmeraldMint
        )

        DavidLiveVisualState.USER_SPEAKING -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = TurquoiseCyan,
            haloGlow = TurquoiseCyan.copy(alpha = 0.48f),
            coreGlow = SkyBlueGlow.copy(alpha = 0.36f),
            waveAccent = TurquoiseSpeech
        )

        DavidLiveVisualState.THINKING -> SpherePalette(
            primary = ThinkingViolet,
            secondary = ThinkingCyan,
            haloGlow = ThinkingIndigo.copy(alpha = 0.46f),
            coreGlow = ThinkingViolet.copy(alpha = 0.34f),
            waveAccent = ThinkingCyan
        )

        DavidLiveVisualState.FINANCE_LOOKUP -> SpherePalette(
            primary = EmeraldMint,
            secondary = TurquoiseSpeech,
            haloGlow = EmeraldDeep.copy(alpha = 0.44f),
            coreGlow = GoldApiAmber.copy(alpha = 0.28f),
            waveAccent = EmeraldMint
        )

        DavidLiveVisualState.ANALYZING -> SpherePalette(
            primary = ThinkingCyan,
            secondary = ThinkingViolet,
            haloGlow = ThinkingIndigo.copy(alpha = 0.48f),
            coreGlow = ThinkingCyan.copy(alpha = 0.34f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.PREPARING_RESPONSE -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = EmeraldPrimary,
            haloGlow = EmeraldDeep.copy(alpha = 0.42f),
            coreGlow = TurquoiseSpeech.copy(alpha = 0.30f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.DAVID_SPEAKING -> SpherePalette(
            primary = DavidSpeakingTurquoise,
            secondary = DavidSpeakingEmerald,
            haloGlow = EmeraldDeep.copy(alpha = 0.54f),
            coreGlow = DavidSpeakingTurquoise.copy(alpha = 0.40f),
            waveAccent = DavidSpeakingEmerald
        )

        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING -> SpherePalette(
            primary = ThinkingCyan,
            secondary = TurquoiseSpeech,
            haloGlow = ThinkingIndigo.copy(alpha = 0.38f),
            coreGlow = ThinkingCyan.copy(alpha = 0.26f),
            waveAccent = TurquoiseSpeech
        )

        DavidLiveVisualState.OFFLINE -> SpherePalette(
            primary = CrimsonOffline,
            secondary = CrimsonLight,
            haloGlow = CrimsonGlow.copy(alpha = 0.46f),
            coreGlow = CrimsonOffline.copy(alpha = 0.32f),
            waveAccent = CrimsonLight
        )

        DavidLiveVisualState.API_MISSING -> SpherePalette(
            primary = GoldApiAmber,
            secondary = GoldApiGlow,
            haloGlow = GoldApiWarm.copy(alpha = 0.44f),
            coreGlow = GoldApiAmber.copy(alpha = 0.30f),
            waveAccent = GoldApiGlow
        )

        DavidLiveVisualState.ERROR -> SpherePalette(
            primary = CrimsonMagentaError,
            secondary = VioletError,
            haloGlow = VioletGlow.copy(alpha = 0.46f),
            coreGlow = CrimsonMagentaError.copy(alpha = 0.32f),
            waveAccent = VioletError
        )

        DavidLiveVisualState.SUCCESS -> SpherePalette(
            primary = SuccessMint,
            secondary = SuccessGreen,
            haloGlow = SuccessGreen.copy(alpha = 0.56f),
            coreGlow = SuccessMint.copy(alpha = 0.38f),
            waveAccent = SuccessMint
        )
    }
}

/**
 * Спецификация объёмной 3D гироскопической энергетической ленты (Silk Plasma Ribbon),
 * огибающей сферу в трёхмерном пространстве (часть проходит позади ядра `z < 0`, часть — спереди `z >= 0`).
 */
private class GyroRibbonSpec(
    val baseTiltXDeg: Float,
    val baseTiltZDeg: Float,
    val precessionRate: Float,
    val radiusRatio: Float,
    val speedMultiplier: Float,
    val phaseOffsetRad: Float,
    val ribbonThicknessDp: Float,
    val waveHarmonic: Int,
    val useSecondaryColor: Boolean
)

private val DefaultGyroRibbonSpecs = arrayOf(
    // 1. Внутренняя диагональная 3D-лента плазмы внутри объёма сферы
    GyroRibbonSpec(
        baseTiltXDeg = 68f,
        baseTiltZDeg = -22f,
        precessionRate = 0.18f,
        radiusRatio = 0.88f,
        speedMultiplier = 1.0f,
        phaseOffsetRad = 0.15f,
        ribbonThicknessDp = 3.0f,
        waveHarmonic = 2,
        useSecondaryColor = false
    ),
    // 2. Встречная перекрёстная внутренняя 3D-лента (глубокая стерео-перспектива внутри шара)
    GyroRibbonSpec(
        baseTiltXDeg = 63f,
        baseTiltZDeg = 29f,
        precessionRate = -0.14f,
        radiusRatio = 0.80f,
        speedMultiplier = -0.82f,
        phaseOffsetRad = 2.15f,
        ribbonThicknessDp = 2.4f,
        waveHarmonic = 3,
        useSecondaryColor = true
    ),
    // 3. Меридиональная 3D-струя близко к ядру сферы
    GyroRibbonSpec(
        baseTiltXDeg = 74f,
        baseTiltZDeg = -56f,
        precessionRate = 0.22f,
        radiusRatio = 0.70f,
        speedMultiplier = 1.24f,
        phaseOffsetRad = 4.10f,
        ribbonThicknessDp = 2.0f,
        waveHarmonic = 2,
        useSecondaryColor = false
    ),
    // 4. Экваториальная внутренняя волна света под куполом сферы
    GyroRibbonSpec(
        baseTiltXDeg = 78f,
        baseTiltZDeg = 8f,
        precessionRate = -0.11f,
        radiusRatio = 0.91f,
        speedMultiplier = 0.64f,
        phaseOffsetRad = 1.05f,
        ribbonThicknessDp = 1.8f,
        waveHarmonic = 4,
        useSecondaryColor = true
    )
)

/**
 * Спецификация объёмного 3D-сгустка плазменной энергии, движущегося внутри и вдоль сферических оболочек ядра.
 */
internal class VolumetricEnergyClusterSpec(
    val baseLatitudeDeg: Float,
    val baseLongitudeRad: Float,
    val shellRadiusRatio: Float,
    val orbitSpeedMultiplier: Float,
    val verticalWaveFreq: Float,
    val baseRadiusDp: Float,
    val useSecondaryColor: Boolean
)

internal val DefaultVolumetricClusters = arrayOf(
    VolumetricEnergyClusterSpec(-50f, 0.0f, 0.82f, 0.94f, 1.4f, 5.8f, false),
    VolumetricEnergyClusterSpec(-36f, 0.9f, 0.64f, -0.80f, 1.8f, 4.9f, true),
    VolumetricEnergyClusterSpec(-24f, 1.8f, 0.88f, 1.14f, 1.2f, 6.4f, false),
    VolumetricEnergyClusterSpec(-15f, 2.7f, 0.52f, 0.66f, 2.1f, 4.6f, true),
    VolumetricEnergyClusterSpec(-6f, 3.6f, 0.86f, -1.06f, 1.5f, 6.0f, false),
    VolumetricEnergyClusterSpec(0f, 4.5f, 0.72f, 0.88f, 1.7f, 6.8f, true),
    VolumetricEnergyClusterSpec(9f, 5.4f, 0.90f, 1.20f, 1.3f, 5.4f, false),
    VolumetricEnergyClusterSpec(18f, 0.5f, 0.58f, -0.74f, 1.9f, 5.1f, true),
    VolumetricEnergyClusterSpec(27f, 1.4f, 0.84f, 0.98f, 1.6f, 6.2f, false),
    VolumetricEnergyClusterSpec(37f, 2.3f, 0.70f, -0.90f, 1.4f, 5.3f, true),
    VolumetricEnergyClusterSpec(48f, 3.2f, 0.80f, 0.78f, 1.5f, 5.6f, false),
    VolumetricEnergyClusterSpec(-42f, 4.1f, 0.76f, 1.08f, 1.6f, 5.0f, true),
    VolumetricEnergyClusterSpec(30f, 5.0f, 0.50f, -0.68f, 2.2f, 4.8f, false),
    VolumetricEnergyClusterSpec(-12f, 5.9f, 0.78f, 1.24f, 1.3f, 6.1f, true),
    VolumetricEnergyClusterSpec(54f, 1.1f, 0.74f, -0.96f, 1.7f, 4.9f, false),
    VolumetricEnergyClusterSpec(-30f, 2.9f, 0.87f, 0.86f, 1.4f, 5.9f, true)
)

/**
 * Вычисляет 3D-положение и перспективную проекцию отдельного светового сгустка внутри сферы
 * с учётом текущей непрерывной фазы [phaseRad], аудиоэнергии [energy] и интерактивного
 * 3D-притяжения к пальцу пользователя ([touchPoint3D], [touchAttractionStrength]).
 */
@Immutable
internal data class ProjectedCluster3D(
    val worldX: Float,
    val worldY: Float,
    val worldZ: Float,
    val screenOffset: Offset,
    val perspectiveScale: Float,
    val depthNormalized: Float,
    val touchPullFactor: Float
)

internal fun computeVolumetricCluster3D(
    clusterIndex: Int,
    spec: VolumetricEnergyClusterSpec,
    center: Offset,
    sphereRadius: Float,
    phaseRad: Float,
    energy: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float
): ProjectedCluster3D {
    val safeRadius = sphereRadius.coerceAtLeast(1f)
    val dynamicLongitude = spec.baseLongitudeRad + phaseRad * spec.orbitSpeedMultiplier
    val latWaveDeg = 16f * sin(phaseRad * spec.verticalWaveFreq + clusterIndex * 0.75f)
    val phiRad = ((spec.baseLatitudeDeg + latWaveDeg) * PI / 180.0).toFloat()

    val radialPulse = 1f + energy * 0.09f * sin(phaseRad * 2.3f + clusterIndex)
    val shellR = safeRadius * spec.shellRadiusRatio * radialPulse

    val cosPhi = cos(phiRad)
    val sinPhi = sin(phiRad)
    val x0 = shellR * cosPhi * cos(dynamicLongitude)
    val y0 = shellR * sinPhi
    val z0 = shellR * cosPhi * sin(dynamicLongitude)

    // Трёхмерный наклон оси сферы (Pitch 22° + плавная прецессия Roll) для глубокой 3D-перспективы
    val pitchRad = (22.0 * PI / 180.0).toFloat()
    val rollRad = (12.0 * sin(phaseRad * 0.28f) * PI / 180.0).toFloat()
    val cosPitch = cos(pitchRad)
    val sinPitch = sin(pitchRad)
    val cosRoll = cos(rollRad)
    val sinRoll = sin(rollRad)

    val yPitch = y0 * cosPitch - z0 * sinPitch
    val zPitch = y0 * sinPitch + z0 * cosPitch
    var worldX = x0 * cosRoll - yPitch * sinRoll
    var worldY = x0 * sinRoll + yPitch * cosRoll
    var worldZ = zPitch

    var appliedPull = 0f
    val clampedTouchStrength = touchAttractionStrength.coerceIn(0f, 1f)
    if (touchPoint3D != null && clampedTouchStrength > 0.004f) {
        // При удержании пальца сгустки плавно стягиваются по 3D-сфере в живой вихрь вокруг точки касания
        val swirlAngle = phaseRad * 3.1f + clusterIndex * (2f * PI.toFloat() / DefaultVolumetricClusters.size)
        val swirlRadius = safeRadius * (0.055f + (clusterIndex % 4) * 0.030f)
        val targetX = touchPoint3D.worldX + swirlRadius * cos(swirlAngle) * touchPoint3D.limbForeshortening
        val targetY = touchPoint3D.worldY + swirlRadius * sin(swirlAngle) * touchPoint3D.limbForeshortening
        val targetZ = (touchPoint3D.worldZ + safeRadius * 0.05f * sin(swirlAngle * 1.4f))
            .coerceAtLeast(safeRadius * 0.16f)

        val dx = targetX - worldX
        val dy = targetY - worldY
        val dz = targetZ - worldZ
        val dist3D = sqrt(dx * dx + dy * dy + dz * dz)
        val proximityWeight = (1f - (dist3D / (safeRadius * 2.2f)).coerceIn(0f, 0.62f))
        appliedPull = (clampedTouchStrength * (0.62f + 0.34f * proximityWeight)).coerceIn(0f, 0.95f)

        val mixedX = worldX + dx * appliedPull
        val mixedY = worldY + dy * appliedPull
        val mixedZ = worldZ + dz * appliedPull
        val mixedMag = sqrt(mixedX * mixedX + mixedY * mixedY + mixedZ * mixedZ).coerceAtLeast(1f)
        val desiredMag = shellR + (safeRadius * 0.87f - shellR) * appliedPull
        val magScale = desiredMag / mixedMag

        worldX = mixedX * magScale
        worldY = mixedY * magScale
        worldZ = mixedZ * magScale
    }

    val cameraDistance = safeRadius * 2.95f
    val perspectiveScale = (cameraDistance / (cameraDistance - worldZ).coerceAtLeast(safeRadius * 0.72f))
        .coerceIn(0.64f, 1.54f)

    val screenOffset = Offset(
        x = center.x + worldX * perspectiveScale,
        y = center.y + worldY * perspectiveScale
    )
    val depthNormalized = ((worldZ / safeRadius) + 1f).coerceIn(0f, 2f) * 0.5f

    return ProjectedCluster3D(
        worldX = worldX,
        worldY = worldY,
        worldZ = worldZ,
        screenOffset = screenOffset,
        perspectiveScale = perspectiveScale,
        depthNormalized = depthNormalized,
        touchPullFactor = appliedPull
    )
}

/**
 * Полностью переработанная живая 3D-сфера **David Live** (Obsidian 3D Quantum Plasma Sphere):
 * - Никаких плоских 2D-колец по контуру, жёстких проволочных сеток или плоских кружков.
 * - Настоящий трёхмерный объём с честным разделением задней (`z < 0`) и передней (`z >= 0`) полусфер,
 *   внутренними многослойными плазменными вихрями (с аддитивным свечением `BlendMode.Plus`),
 *   полупрозрачным преломляющим ядром, перекрывающим задний план, 3D-кометами энергии
 *   и шелковистыми гироскопическими 3D-лентами.
 * - Полная интерактивность при касании: при удержании и перемещении пальца вся 3D-плазма,
 *   внутренние потоки и световые сгустки плавно притягиваются к точке касания на выпуклой
 *   поверхности сферы и следуют за пальцем с учётом 3D-перспективы, а после отпускания
 *   мягко расходятся обратно без сброса фазы вращения.
 */
@Composable
fun DavidLiveMeshSphereVisualizer(
    visualState: DavidLiveVisualState,
    overallEnergy: Float,
    rayMagnitudes: List<Float>,
    isMicMuted: Boolean,
    onSphereClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualizerSize: Dp = 308.dp,
    activeToolName: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewConfiguration = LocalViewConfiguration.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnSphereClick by rememberUpdatedState(onSphereClick)

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

    var isLifecycleStarted by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }
    var isTouchHoldActive by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleStarted = true
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    isLifecycleStarted = false
                    isTouchHoldActive = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            isTouchHoldActive = false
        }
    }

    val targetPalette = remember(visualState, isMicMuted) {
        resolveSpherePalette(visualState, isMicMuted)
    }

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

    val isUserSpeechActive = visualState == DavidLiveVisualState.USER_SPEAKING && !isMicMuted
    val isDavidSpeechActive = visualState == DavidLiveVisualState.DAVID_SPEAKING
    val isSpeechActive = isUserSpeechActive || isDavidSpeechActive

    val targetAudioEnergy = if (isSpeechActive) {
        overallEnergy.coerceIn(0.08f, 1f)
    } else {
        0.03f
    }

    val smoothedEnergy by animateFloatAsState(
        targetValue = targetAudioEnergy,
        animationSpec = tween(
            durationMillis = if (isSpeechActive) 85 else 390,
            easing = FastOutSlowInEasing
        ),
        label = "sphere_energy_smoothing"
    )

    // Реальные частотные полосы спектра из rayMagnitudes для органической деформации 3D-плазмы
    val lowBandEnergy = remember(rayMagnitudes, smoothedEnergy) {
        if (rayMagnitudes.isEmpty()) smoothedEnergy
        else rayMagnitudes.take(8).average().toFloat().coerceIn(0f, 1f)
    }
    val midBandEnergy = remember(rayMagnitudes, smoothedEnergy) {
        if (rayMagnitudes.size < 16) smoothedEnergy
        else rayMagnitudes.subList(8, 24).average().toFloat().coerceIn(0f, 1f)
    }
    val highBandEnergy = remember(rayMagnitudes, smoothedEnergy) {
        if (rayMagnitudes.size < 32) smoothedEnergy
        else rayMagnitudes.subList(24, min(48, rayMagnitudes.size)).average().toFloat().coerceIn(0f, 1f)
    }

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

    var targetTouchNormX by remember { mutableFloatStateOf(0f) }
    var targetTouchNormY by remember { mutableFloatStateOf(0f) }
    var smoothedTouchNormX by remember { mutableFloatStateOf(0f) }
    var smoothedTouchNormY by remember { mutableFloatStateOf(0f) }
    var touchAttractionStrength by remember { mutableFloatStateOf(0f) }
    val isTouchHoldActiveState by rememberUpdatedState(isTouchHoldActive)
    val targetTouchNormXState by rememberUpdatedState(targetTouchNormX)
    val targetTouchNormYState by rememberUpdatedState(targetTouchNormY)

    var continuousOrbitPhaseRad by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(reduceMotion, isLifecycleStarted) {
        if (!isLifecycleStarted) return@LaunchedEffect
        if (reduceMotion) {
            touchAttractionStrength = if (isTouchHoldActiveState) 1f else 0f
            smoothedTouchNormX = targetTouchNormXState
            smoothedTouchNormY = targetTouchNormYState
            return@LaunchedEffect
        }
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
                    touchAttractionStrength = advanceTouchAttractionStrength(
                        currentStrength = touchAttractionStrength,
                        isTouchActive = isTouchHoldActiveState,
                        deltaMs = deltaMs,
                        reduceMotion = false
                    )
                    if (isTouchHoldActiveState || touchAttractionStrength > 0.004f) {
                        smoothedTouchNormX = interpolateTouchSurfaceCoordinate(
                            currentValue = smoothedTouchNormX,
                            targetValue = targetTouchNormXState,
                            deltaMs = deltaMs
                        )
                        smoothedTouchNormY = interpolateTouchSurfaceCoordinate(
                            currentValue = smoothedTouchNormY,
                            targetValue = targetTouchNormYState,
                            deltaMs = deltaMs
                        )
                    }
                }
                lastFrameMs = frameTimeMs
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "david_live_sphere_breathing")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (visualState == DavidLiveVisualState.ERROR) 1500 else 3100,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sphere_breathing_cycle"
    )

    val targetOrbitIntensity = when {
        shouldApplyMutedVisualPalette(visualState, isMicMuted) -> 0.62f
        visualState == DavidLiveVisualState.OFFLINE -> 0.78f
        visualState == DavidLiveVisualState.API_MISSING -> 0.88f
        visualState == DavidLiveVisualState.ERROR -> 0.84f + 0.16f * breathProgress
        visualState == DavidLiveVisualState.USER_SPEAKING ||
            visualState == DavidLiveVisualState.DAVID_SPEAKING -> 0.95f + 0.05f * smoothedEnergy
        else -> 0.90f
    }
    val orbitIntensity by animateFloatAsState(
        targetValue = targetOrbitIntensity,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "sphere_orbit_intensity"
    )

    val orbitProgressNormalized = ((continuousOrbitPhaseRad / (2f * PI.toFloat())) % 1f + 1f) % 1f

    // Интерактивная иконка внутри ядра для состояний, требующих действия пользователя
    val centerStateEmblemIcon = remember(visualState, isMicMuted) {
        when {
            shouldApplyMutedVisualPalette(visualState, isMicMuted) -> Icons.Default.MicOff
            visualState == DavidLiveVisualState.OFFLINE -> Icons.Default.WifiOff
            visualState == DavidLiveVisualState.API_MISSING -> Icons.Default.Key
            visualState == DavidLiveVisualState.ERROR -> Icons.Default.PriorityHigh
            visualState == DavidLiveVisualState.SUCCESS -> Icons.Default.Check
            else -> null
        }
    }

    val reusableSphereClipPath = remember { Path() }
    val reusablePlasmaPath = remember { Path() }
    val reusableStreamPath = remember { Path() }

    Box(
        modifier = modifier
            .size(visualizerSize)
            .semantics {
                role = Role.Button
                contentDescription = when (visualState) {
                    DavidLiveVisualState.OFFLINE -> "Связь потеряна. Нажмите на сферу для повторного подключения."
                    DavidLiveVisualState.API_MISSING -> "Требуется API-ключ Gemini. Нажмите для настройки."
                    DavidLiveVisualState.ERROR -> "Ошибка связи. Нажмите для повтора."
                    else -> if (isMicMuted) "Микрофон выключен. Нажмите, чтобы включить." else "Голосовой помощник David Live"
                }
                onClick {
                    currentOnSphereClick()
                    true
                }
            }
            .pointerInput(viewConfiguration) {
                val touchSlopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downTimeMs = System.currentTimeMillis()
                    val downPos = down.position
                    var latestPos = downPos
                    var maxDragDistPx = 0f
                    var wasCancelled = false
                    var holdActivated = false

                    fun updateTouchTargetFromScreen(pos: Offset) {
                        val boxCenter = Offset(size.width / 2f, size.height / 2f)
                        val approxSphereRadius = (min(size.width, size.height) / 2f) * 0.84f
                        val projected = projectTouchTo3DSphere(
                            touchOffset = pos,
                            sphereCenter = boxCenter,
                            sphereRadius = approxSphereRadius
                        )
                        targetTouchNormX = projected.normX
                        targetTouchNormY = projected.normY
                    }

                    updateTouchTargetFromScreen(downPos)
                    smoothedTouchNormX = targetTouchNormX
                    smoothedTouchNormY = targetTouchNormY

                    var holdTimerJob: Job? = coroutineScope.launch {
                        delay(SPHERE_TOUCH_HOLD_ACTIVATION_MS)
                        holdActivated = true
                        isTouchHoldActive = true
                    }

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChange = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (activeChange == null || !activeChange.pressed) {
                                break
                            }

                            latestPos = activeChange.position
                            val distFromDown = (latestPos - downPos).getDistance()
                            if (distFromDown > maxDragDistPx) {
                                maxDragDistPx = distFromDown
                            }

                            val elapsedMs = System.currentTimeMillis() - downTimeMs
                            if (!holdActivated &&
                                (elapsedMs >= SPHERE_TOUCH_HOLD_ACTIVATION_MS || maxDragDistPx > touchSlopPx)
                            ) {
                                holdActivated = true
                                holdTimerJob?.cancel()
                                isTouchHoldActive = true
                            }

                            updateTouchTargetFromScreen(latestPos)
                            if (holdActivated && activeChange.positionChange() != Offset.Zero) {
                                activeChange.consume()
                            }
                        }
                    } catch (_: Exception) {
                        wasCancelled = true
                    } finally {
                        holdTimerJob?.cancel()
                        isTouchHoldActive = false
                    }

                    val totalPressDurationMs = if (holdActivated) {
                        max(SPHERE_TOUCH_HOLD_ACTIVATION_MS, System.currentTimeMillis() - downTimeMs)
                    } else {
                        System.currentTimeMillis() - downTimeMs
                    }

                    val outcome = evaluateSphereTouchGesture(
                        pressDurationMs = totalPressDurationMs,
                        maxDragDistancePx = maxDragDistPx,
                        touchSlopPx = touchSlopPx,
                        wasCancelled = wasCancelled
                    )
                    if (outcome == SphereTouchGestureOutcome.SHORT_TAP_ACTION) {
                        val inBounds = latestPos.x in -touchSlopPx..(size.width + touchSlopPx) &&
                            latestPos.y in -touchSlopPx..(size.height + touchSlopPx)
                        if (inBounds) {
                            currentOnSphereClick()
                        }
                    }
                }
            }
            .testTag("david_live_circular_visualizer")
            .testTag("david_live_mic_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(visualizerSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxCanvasRadius = size.minDimension / 2f
            val baseRadius = maxCanvasRadius * 0.84f
            val dynamicRadius = (baseRadius * (
                1f +
                    0.018f * sin(breathProgress * PI.toFloat()) +
                    smoothedEnergy * 0.055f
                )).coerceAtMost(maxCanvasRadius * 0.90f)

            val activeTouch3D = if (touchAttractionStrength > 0.004f) {
                projectTouchTo3DSphere(
                    touchOffset = Offset(
                        x = center.x + smoothedTouchNormX * dynamicRadius,
                        y = center.y + smoothedTouchNormY * dynamicRadius
                    ),
                    sphereCenter = center,
                    sphereRadius = dynamicRadius
                )
            } else {
                null
            }

            // ЭТАП 1. Мягкая атмосферная корона вокруг увеличенной 3D-сферы (без внешних орбитальных дуг/колец)
            drawVolumetricOuterCoronaAndParticles(
                center = center,
                sphereRadius = dynamicRadius,
                phaseRad = continuousOrbitPhaseRad,
                breathProgress = breathProgress,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                haloColor = animatedHaloColor,
                intensity = orbitIntensity,
                touchPoint3D = activeTouch3D,
                touchAttractionStrength = touchAttractionStrength
            )

            // Подготавливаем сферическую область отсечения для всех внутренних 3D-слоёв шара
            reusableSphereClipPath.reset()
            reusableSphereClipPath.addOval(
                Rect(
                    left = center.x - dynamicRadius,
                    top = center.y - dynamicRadius,
                    right = center.x + dynamicRadius,
                    bottom = center.y + dynamicRadius
                )
            )

            clipPath(reusableSphereClipPath) {
                // ЭТАП 2. 3D-затенение шара (Lambertian + Subsurface Scattering) и 5 объёмных 3D-облаков текучей плазмы
                draw3DSphereShadedBodyAndOrganicPlasmaNebula(
                    center = center,
                    radius = dynamicRadius,
                    phaseRad = continuousOrbitPhaseRad,
                    breathProgress = breathProgress,
                    energy = smoothedEnergy,
                    lowBandEnergy = lowBandEnergy,
                    midBandEnergy = midBandEnergy,
                    highBandEnergy = highBandEnergy,
                    primaryColor = animatedPrimaryColor,
                    secondaryColor = animatedSecondaryColor,
                    coreGlowColor = animatedCoreGlowColor,
                    intensity = orbitIntensity,
                    visualState = visualState,
                    touchPoint3D = activeTouch3D,
                    touchAttractionStrength = touchAttractionStrength,
                    reusablePath = reusablePlasmaPath
                )

                // ЭТАП 3. ЗАДНЯЯ ПОЛУСФЕРА (z < 0) спиральных 3D-потоков и кометных сгустков (без орбитальных дуг)
                draw3DSpiralVortexCurrents(
                    center = center,
                    sphereRadius = dynamicRadius,
                    phaseRad = continuousOrbitPhaseRad,
                    energy = smoothedEnergy,
                    primaryColor = animatedPrimaryColor,
                    secondaryColor = animatedSecondaryColor,
                    intensity = orbitIntensity,
                    touchPoint3D = activeTouch3D,
                    touchAttractionStrength = touchAttractionStrength,
                    drawFrontHemisphere = false,
                    reusablePath = reusableStreamPath
                )

                drawVolumetric3DPlasmaCometClusters(
                    center = center,
                    sphereRadius = dynamicRadius,
                    phaseRad = continuousOrbitPhaseRad,
                    energy = smoothedEnergy,
                    primaryColor = animatedPrimaryColor,
                    secondaryColor = animatedSecondaryColor,
                    intensity = orbitIntensity,
                    touchPoint3D = activeTouch3D,
                    touchAttractionStrength = touchAttractionStrength,
                    drawFrontHemisphere = false
                )

                // ЭТАП 4. Преломляющее 3D-ядро в центре сферы (частично перекрывает задние потоки z < 0, создавая глубокий 3D-параллакс)
                draw3DRefractiveCoreNucleus(
                    center = center,
                    radius = dynamicRadius,
                    phaseRad = continuousOrbitPhaseRad,
                    breathProgress = breathProgress,
                    energy = smoothedEnergy,
                    primaryColor = animatedPrimaryColor,
                    secondaryColor = animatedSecondaryColor,
                    coreGlowColor = animatedCoreGlowColor,
                    intensity = orbitIntensity,
                    touchPoint3D = activeTouch3D,
                    touchAttractionStrength = touchAttractionStrength
                )

                // ЭТАП 5. ПЕРЕДНЯЯ ПОЛУСФЕРА (z >= 0) спиральных 3D-потоков и кометных сгустков (без орбитальных дуг)
                draw3DSpiralVortexCurrents(
                    center = center,
                    sphereRadius = dynamicRadius,
                    phaseRad = continuousOrbitPhaseRad,
                    energy = smoothedEnergy,
                    primaryColor = animatedPrimaryColor,
                    secondaryColor = animatedSecondaryColor,
                    intensity = orbitIntensity,
                    touchPoint3D = activeTouch3D,
                    touchAttractionStrength = touchAttractionStrength,
                    drawFrontHemisphere = true,
                    reusablePath = reusableStreamPath
                )

                drawVolumetric3DPlasmaCometClusters(
                    center = center,
                    sphereRadius = dynamicRadius,
                    phaseRad = continuousOrbitPhaseRad,
                    energy = smoothedEnergy,
                    primaryColor = animatedPrimaryColor,
                    secondaryColor = animatedSecondaryColor,
                    intensity = orbitIntensity,
                    touchPoint3D = activeTouch3D,
                    touchAttractionStrength = touchAttractionStrength,
                    drawFrontHemisphere = true
                )

                // ЭТАП 6. Выпуклая 3D-оптика (мягкое внутреннее свечение Френеля, купольный блик и 3D-вихрь под пальцем)
                draw3DSphericalLensAndTouchVortex(
                    center = center,
                    radius = dynamicRadius,
                    phaseRad = continuousOrbitPhaseRad,
                    breathProgress = breathProgress,
                    energy = smoothedEnergy,
                    primaryColor = animatedPrimaryColor,
                    secondaryColor = animatedSecondaryColor,
                    intensity = orbitIntensity,
                    touchPoint3D = activeTouch3D,
                    touchAttractionStrength = touchAttractionStrength
                )
            }

            // Мягкая светящаяся атмосфера вдоль сферического лимба (без внешних дуг и без плоского Stroke-кольца!)
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.Transparent,
                    0.84f to Color.Transparent,
                    0.96f to animatedPrimaryColor.copy(alpha = (0.26f + 0.14f * smoothedEnergy) * orbitIntensity),
                    0.99f to animatedSecondaryColor.copy(alpha = (0.38f + 0.16f * smoothedEnergy) * orbitIntensity),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = dynamicRadius * 1.035f
                ),
                radius = dynamicRadius * 1.035f,
                center = center,
                blendMode = BlendMode.Plus
            )
        }

        // Голографическая 3D-эмблема действия внутри ядра (для Mute, Offline, API Missing, Error, Success),
        // которая плавно смещается с 3D-параллаксом при касании сферы
        if (centerStateEmblemIcon != null) {
            val parallaxX = (smoothedTouchNormX * 8f * touchAttractionStrength).roundToInt()
            val parallaxY = (smoothedTouchNormY * 8f * touchAttractionStrength).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(parallaxX, parallaxY) }
                    .size(48.dp)
                    .alpha((0.86f - 0.30f * touchAttractionStrength).coerceIn(0.45f, 0.90f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                animatedPrimaryColor.copy(alpha = 0.26f),
                                ObsidianVoidCore.copy(alpha = 0.58f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = centerStateEmblemIcon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Отрисовка глубокой многослойной внешней короны вокруг увеличенной 3D-сферы (без внешних орбитальных колец и дуг).
 */
private fun DrawScope.drawVolumetricOuterCoronaAndParticles(
    center: Offset,
    sphereRadius: Float,
    phaseRad: Float,
    breathProgress: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    haloColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float
) {
    val outerBloomRadius = sphereRadius * (1.18f + 0.04f * breathProgress + energy * 0.08f)
    val bloomShift = if (touchPoint3D != null && touchAttractionStrength > 0.01f) {
        Offset(
            x = center.x + touchPoint3D.worldX * 0.16f * touchAttractionStrength,
            y = center.y + touchPoint3D.worldY * 0.16f * touchAttractionStrength
        )
    } else {
        center
    }

    drawCircle(
        brush = Brush.radialGradient(
            0.0f to haloColor.copy(alpha = (haloColor.alpha * 1.25f).coerceAtMost(0.68f)),
            0.68f to primaryColor.copy(alpha = (0.24f + 0.14f * energy) * intensity),
            0.88f to secondaryColor.copy(alpha = 0.10f * intensity),
            1.0f to Color.Transparent,
            center = bloomShift,
            radius = outerBloomRadius
        ),
        radius = outerBloomRadius,
        center = center
    )
}

/**
 * Отрисовка объёмного 3D-тела сферы (с освещённым куполом сверху-слева и глубоким теневым
 * терминатором снизу-справа) и 5 деформируемых 3D-облаков текучей плазмы внутри ядра,
 * реагирующих на `lowBandEnergy`, `midBandEnergy` и `highBandEnergy` спектра голоса.
 */
private fun DrawScope.draw3DSphereShadedBodyAndOrganicPlasmaNebula(
    center: Offset,
    radius: Float,
    phaseRad: Float,
    breathProgress: Float,
    energy: Float,
    lowBandEnergy: Float,
    midBandEnergy: Float,
    highBandEnergy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    coreGlowColor: Color,
    intensity: Float,
    visualState: DavidLiveVisualState,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float,
    reusablePath: Path
) {
    // 1. Трёхмерное сферическое тело с источником света в точке (-0.32R, -0.34R) и глубокой тенью в (+0.38R, +0.42R)
    val lightFocalCenter = Offset(
        x = center.x - radius * 0.30f,
        y = center.y - radius * 0.32f
    )
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to lerp(ObsidianSubsurfaceDark, primaryColor, 0.24f * intensity),
            0.48f to ObsidianVoidCore.copy(alpha = 0.94f),
            0.84f to ObsidianDeepShadow.copy(alpha = 0.98f),
            1.0f to Color.Black,
            center = lightFocalCenter,
            radius = radius * 1.38f
        ),
        radius = radius,
        center = center
    )

    val stateFlowMultiplier = when (visualState) {
        DavidLiveVisualState.THINKING,
        DavidLiveVisualState.FINANCE_LOOKUP,
        DavidLiveVisualState.ANALYZING,
        DavidLiveVisualState.PREPARING_RESPONSE -> 1.28f
        DavidLiveVisualState.USER_SPEAKING,
        DavidLiveVisualState.DAVID_SPEAKING -> 1.48f
        else -> 1.0f
    }

    // 2. Пять органических деформируемых 3D-лопастей плазмы (Plasma Nebula Lobes) на разной глубине внутри шара
    val lobeCount = 5
    val blobPoints = 24
    val stepAngle = (2.0 * PI / blobPoints).toFloat()

    for (lobeIdx in 0 until lobeCount) {
        val depthLayer = (lobeIdx - 2) / 2f // от -1.0 (дальняя глубина) до +1.0 (передний план)
        val lobeDirection = if (lobeIdx % 2 == 0) 1f else -1f
        val lobeSpeed = (0.42f + lobeIdx * 0.14f) * stateFlowMultiplier * lobeDirection
        val lobePhase = phaseRad * lobeSpeed + lobeIdx * 1.35f

        val spectralBoost = when (lobeIdx % 3) {
            0 -> lowBandEnergy
            1 -> midBandEnergy
            else -> highBandEnergy
        }

        // Трёхмерное движение центра плазменного облака внутри сферы
        val orbitR = radius * (0.22f + 0.06f * (lobeIdx % 3))
        var lobeCenterX = center.x + orbitR * cos(lobePhase) * (1f + 0.18f * spectralBoost)
        var lobeCenterY = center.y + orbitR * sin(lobePhase * 0.82f + lobeIdx) * (1f + 0.18f * spectralBoost)

        // При касании сферы внутренние плазменные облака мягко тянутся вслед за пальцем
        if (touchPoint3D != null && touchAttractionStrength > 0.005f) {
            val pullWeight = (0.34f + 0.12f * depthLayer) * touchAttractionStrength
            lobeCenterX += (touchPoint3D.projectedScreenOffset.x - lobeCenterX) * pullWeight
            lobeCenterY += (touchPoint3D.projectedScreenOffset.y - lobeCenterY) * pullWeight
        }

        val baseLobeRadius = radius * (0.52f + 0.05f * sin(breathProgress * PI.toFloat() + lobeIdx) + 0.12f * spectralBoost)
        reusablePath.reset()

        var firstX = 0f
        var firstY = 0f
        var prevX = 0f
        var prevY = 0f

        for (i in 0 until blobPoints) {
            val theta = i * stepAngle
            val wave1 = 0.14f * sin(theta * 3f + lobePhase * 1.7f)
            val wave2 = (0.09f + 0.11f * spectralBoost) * cos(theta * 4f - lobePhase * 2.1f + lobeIdx)
            val wave3 = 0.06f * energy * sin(theta * 6f + phaseRad * 3.0f)
            val rDeformed = baseLobeRadius * (1f + wave1 + wave2 + wave3)

            // Перспективное сжатие облака у краёв сферы для сохранения ощущения 3D-шара
            val px = lobeCenterX + rDeformed * cos(theta)
            val py = lobeCenterY + rDeformed * sin(theta)

            if (i == 0) {
                firstX = px
                firstY = py
                reusablePath.moveTo(px, py)
            } else {
                val midX = (prevX + px) * 0.5f
                val midY = (prevY + py) * 0.5f
                reusablePath.quadraticTo(prevX, prevY, midX, midY)
            }
            prevX = px
            prevY = py
        }
        reusablePath.quadraticTo(prevX, prevY, (prevX + firstX) * 0.5f, (prevY + firstY) * 0.5f)
        reusablePath.close()

        val lobeColorA = if (lobeIdx % 2 == 0) primaryColor else secondaryColor
        val lobeColorB = if (lobeIdx % 2 == 0) secondaryColor else coreGlowColor
        val lobeAlpha = ((0.18f + 0.14f * spectralBoost + 0.08f * energy) * intensity).coerceIn(0.05f, 0.52f)

        drawPath(
            path = reusablePath,
            brush = Brush.radialGradient(
                0.0f to Color.White.copy(alpha = lobeAlpha * 0.42f),
                0.35f to lobeColorA.copy(alpha = lobeAlpha),
                0.72f to lobeColorB.copy(alpha = lobeAlpha * 0.48f),
                1.0f to Color.Transparent,
                center = Offset(lobeCenterX, lobeCenterY),
                radius = baseLobeRadius * 1.25f
            ),
            blendMode = BlendMode.Plus
        )
    }

    // 3. Глубокое 3D-затенение вдоль теневого серпа сферы (Terminator Crescent Shadow) для рельефного 3D-объёма
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.Transparent,
            0.56f to Color.Transparent,
            0.84f to ObsidianVoidCore.copy(alpha = 0.54f),
            1.0f to ObsidianDeepShadow.copy(alpha = 0.86f),
            center = Offset(center.x - radius * 0.18f, center.y - radius * 0.20f),
            radius = radius * 1.16f
        ),
        radius = radius,
        center = center
    )
}

/**
 * Отрисовка трёхмерных спиральных геодезических энергетических потоков внутри сферы,
 * которые струятся в 3D-пространстве по передней (`z >= 0`) и задней (`z < 0`) полусферам
 * и изгибаются к пальцу при касании.
 */
private fun DrawScope.draw3DSpiralVortexCurrents(
    center: Offset,
    sphereRadius: Float,
    phaseRad: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float,
    drawFrontHemisphere: Boolean,
    reusablePath: Path
) {
    val streamCount = 6
    val stepsPerStream = 28
    val cameraDistance = sphereRadius * 3.0f
    val pitchRad = (20.0 * PI / 180.0).toFloat()
    val cosPitch = cos(pitchRad)
    val sinPitch = sin(pitchRad)

    for (streamIdx in 0 until streamCount) {
        val baseAzimuth = streamIdx * (2f * PI.toFloat() / streamCount) + phaseRad * 0.55f
        val shellRadius = sphereRadius * (0.70f + (streamIdx % 3) * 0.08f)
        val streamColor = if (streamIdx % 2 == 0) secondaryColor else primaryColor

        reusablePath.reset()
        var isDrawingSegment = false
        var prevScreenX = 0f
        var prevScreenY = 0f

        for (step in 0..stepsPerStream) {
            val t = step.toFloat() / stepsPerStream.toFloat() // 0..1 от южного полюса к северному
            val latRad = ((t - 0.5f) * PI * 0.82).toFloat()
            val spiralTwist = (t - 0.5f) * 2.8f + 0.22f * (0.4f + energy) * sin(t * 6f + phaseRad * 1.8f + streamIdx)
            val lonRad = baseAzimuth + spiralTwist

            val cosLat = cos(latRad)
            val sinLat = sin(latRad)
            val x0 = shellRadius * cosLat * cos(lonRad)
            val y0 = shellRadius * sinLat
            val z0 = shellRadius * cosLat * sin(lonRad)

            var worldX = x0
            var worldY = y0 * cosPitch - z0 * sinPitch
            var worldZ = y0 * sinPitch + z0 * cosPitch

            if (touchPoint3D != null && touchAttractionStrength > 0.01f && worldZ > -sphereRadius * 0.25f) {
                val dx = touchPoint3D.worldX - worldX
                val dy = touchPoint3D.worldY - worldY
                val dz = touchPoint3D.worldZ - worldZ
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                val pull = (1f - (dist / (sphereRadius * 1.65f)).coerceIn(0f, 1f)) *
                    0.54f * touchAttractionStrength
                worldX += dx * pull
                worldY += dy * pull
                worldZ += dz * pull
            }

            val isFront = worldZ >= 0f
            val perspective = cameraDistance / (cameraDistance - worldZ).coerceAtLeast(sphereRadius * 0.75f)
            val sx = center.x + worldX * perspective
            val sy = center.y + worldY * perspective

            if (isFront == drawFrontHemisphere) {
                if (!isDrawingSegment) {
                    reusablePath.moveTo(sx, sy)
                    isDrawingSegment = true
                } else {
                    reusablePath.quadraticTo(prevScreenX, prevScreenY, (prevScreenX + sx) * 0.5f, (prevScreenY + sy) * 0.5f)
                }
            } else {
                isDrawingSegment = false
            }
            prevScreenX = sx
            prevScreenY = sy
        }

        val baseAlpha = if (drawFrontHemisphere) {
            (0.20f + 0.16f * energy + 0.14f * touchAttractionStrength) * intensity
        } else {
            (0.07f + 0.05f * energy) * intensity
        }

        // Широкий мягкий ореол потока + тонкая яркая сердцевина
        drawPath(
            path = reusablePath,
            color = streamColor.copy(alpha = (baseAlpha * 0.45f).coerceIn(0.02f, 0.36f)),
            style = Stroke(
                width = (if (drawFrontHemisphere) 4.2.dp else 2.6.dp).toPx(),
                cap = StrokeCap.Round
            ),
            blendMode = BlendMode.Plus
        )
        drawPath(
            path = reusablePath,
            color = lerp(streamColor, Color.White, if (drawFrontHemisphere) 0.35f else 0.10f)
                .copy(alpha = baseAlpha.coerceIn(0.03f, 0.58f)),
            style = Stroke(
                width = (if (drawFrontHemisphere) 1.4.dp else 0.9.dp).toPx(),
                cap = StrokeCap.Round
            ),
            blendMode = BlendMode.Plus
        )
    }
}

/**
 * Полупрозрачное 3D-преломляющее ядро в центре сферы, которое частично перекрывает
 * заднюю полусферу (`z < 0`) потоков и сгустков, создавая явный оптический параллакс глубины.
 */
private fun DrawScope.draw3DRefractiveCoreNucleus(
    center: Offset,
    radius: Float,
    phaseRad: Float,
    breathProgress: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    coreGlowColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float
) {
    val shiftX = if (touchPoint3D != null) touchPoint3D.worldX * 0.26f * touchAttractionStrength else 0f
    val shiftY = if (touchPoint3D != null) touchPoint3D.worldY * 0.26f * touchAttractionStrength else 0f
    val nucleusCenter = Offset(
        x = center.x + radius * 0.04f * cos(phaseRad * 0.65f) + shiftX,
        y = center.y + radius * 0.04f * sin(phaseRad * 0.55f) + shiftY
    )
    val veilRadius = radius * (0.62f + 0.04f * breathProgress + 0.05f * energy)

    // 1. Оптическая поглощающая вуаль ядра, приглушающая заднюю полусферу (z < 0) для 3D-разделения планов
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to ObsidianVoidCore.copy(alpha = 0.62f),
            0.55f to ObsidianVoidCore.copy(alpha = 0.38f),
            0.85f to coreGlowColor.copy(alpha = 0.18f * intensity),
            1.0f to Color.Transparent,
            center = nucleusCenter,
            radius = veilRadius
        ),
        radius = veilRadius,
        center = nucleusCenter
    )

    // 2. Внутреннее пульсирующее энергетическое сердце Давида
    val heartRadius = radius * (0.38f + 0.05f * sin(phaseRad * 1.4f) + 0.10f * energy)
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.White.copy(alpha = (0.28f + 0.24f * energy + 0.18f * touchAttractionStrength) * intensity),
            0.32f to secondaryColor.copy(alpha = (0.38f + 0.20f * energy) * intensity),
            0.68f to primaryColor.copy(alpha = (0.22f + 0.12f * breathProgress) * intensity),
            1.0f to Color.Transparent,
            center = nucleusCenter,
            radius = heartRadius
        ),
        radius = heartRadius,
        center = nucleusCenter,
        blendMode = BlendMode.Plus
    )
}

/**
 * Отрисовка объёмных 3D-кометных сгустков плазмы (`DefaultVolumetricClusters`) с шлейфами движения:
 * - На задней полусфере (`z < 0`) сгустки меньше и мягче (в глубине за ядром).
 * - На передней полусфере (`z >= 0`) сгустки крупнее и ярче за счёт 3D-перспективы,
 *   а при касании пальцем стягиваются в яркий вихрь вокруг точки касания.
 */
private fun DrawScope.drawVolumetric3DPlasmaCometClusters(
    center: Offset,
    sphereRadius: Float,
    phaseRad: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float,
    drawFrontHemisphere: Boolean
) {
    for ((index, spec) in DefaultVolumetricClusters.withIndex()) {
        val current = computeVolumetricCluster3D(
            clusterIndex = index,
            spec = spec,
            center = center,
            sphereRadius = sphereRadius,
            phaseRad = phaseRad,
            energy = energy,
            touchPoint3D = touchPoint3D,
            touchAttractionStrength = touchAttractionStrength
        )

        val isFront = current.worldZ >= 0f
        if (isFront != drawFrontHemisphere) continue

        // Вычисляем предыдущее положение сгустка по фазе для ориентации 3D-шлейфа кометы
        val trailCluster = computeVolumetricCluster3D(
            clusterIndex = index,
            spec = spec,
            center = center,
            sphereRadius = sphereRadius,
            phaseRad = phaseRad - 0.14f,
            energy = energy,
            touchPoint3D = touchPoint3D,
            touchAttractionStrength = touchAttractionStrength
        )

        val horizonFade = (abs(current.worldZ) / (sphereRadius * 0.16f)).coerceIn(0.22f, 1f)
        val baseColor = if (spec.useSecondaryColor) secondaryColor else primaryColor
        val accentColor = if (spec.useSecondaryColor) primaryColor else secondaryColor

        val depthAlpha = if (isFront) {
            (0.34f + 0.42f * current.depthNormalized + 0.26f * current.touchPullFactor + 0.14f * energy) *
                intensity * horizonFade
        } else {
            (0.10f + 0.15f * current.depthNormalized) * intensity * horizonFade
        }

        val coreRadiusPx = spec.baseRadiusDp.dp.toPx() *
            current.perspectiveScale *
            (if (isFront) 1.0f + 0.24f * energy + 0.30f * current.touchPullFactor else 0.70f)
        val nebulaRadiusPx = coreRadiusPx * (if (isFront) 3.8f + 0.9f * current.touchPullFactor else 2.6f)

        // 1. Мягкий кометный шлейф за 3D-сгустком
        val trailVec = current.screenOffset - trailCluster.screenOffset
        if (trailVec.getDistanceSquared() > 1f) {
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        baseColor.copy(alpha = (depthAlpha * 0.55f).coerceIn(0.02f, 0.65f)),
                        Color.White.copy(alpha = (depthAlpha * 0.75f).coerceIn(0.04f, 0.85f))
                    ),
                    start = trailCluster.screenOffset,
                    end = current.screenOffset
                ),
                start = trailCluster.screenOffset,
                end = current.screenOffset,
                strokeWidth = coreRadiusPx * 1.15f,
                cap = StrokeCap.Round,
                blendMode = BlendMode.Plus
            )
        }

        // 2. Широкое туманное свечение плазменного сгустка
        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.White.copy(alpha = (depthAlpha * 0.82f).coerceIn(0.04f, 0.92f)),
                0.28f to baseColor.copy(alpha = (depthAlpha * 0.68f).coerceIn(0.03f, 0.78f)),
                0.65f to accentColor.copy(alpha = (depthAlpha * 0.26f).coerceIn(0.01f, 0.38f)),
                1.0f to Color.Transparent,
                center = current.screenOffset,
                radius = nebulaRadiusPx
            ),
            radius = nebulaRadiusPx,
            center = current.screenOffset,
            blendMode = BlendMode.Plus
        )
    }
}

/**
 * Отрисовка выпуклой 3D-линзы сферы (без плоской обводки!), внутреннего краевого свечения Френеля,
 * купольного блика и локального 3D-энергетического вихря под пальцем при интерактивном касании.
 */
private fun DrawScope.draw3DSphericalLensAndTouchVortex(
    center: Offset,
    radius: Float,
    phaseRad: Float,
    breathProgress: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float
) {
    // 1. Внутреннее объёмное свечение Френеля (Fresnel Rim Glow), подчёркивающее кривизну 3D-шара у краёв
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.Transparent,
            0.68f to Color.Transparent,
            0.88f to primaryColor.copy(alpha = (0.18f + 0.10f * energy) * intensity),
            0.96f to secondaryColor.copy(alpha = (0.38f + 0.14f * energy) * intensity),
            1.0f to Color.White.copy(alpha = (0.28f + 0.12f * breathProgress) * intensity),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Plus
    )

    // 2. Мягкий объёмный купольный блик (3D Dome Specular Sheen) в верхней левой зоне передней полусферы
    val domeDriftX = radius * 0.04f * cos(phaseRad * 0.42f)
    val domeDriftY = radius * 0.04f * sin(phaseRad * 0.42f)
    val domeHighlightCenter = Offset(
        x = center.x - radius * 0.34f + domeDriftX,
        y = center.y - radius * 0.36f + domeDriftY
    )
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color.White.copy(alpha = 0.34f * intensity),
            0.38f to secondaryColor.copy(alpha = 0.18f * intensity),
            0.75f to primaryColor.copy(alpha = 0.06f * intensity),
            1.0f to Color.Transparent,
            center = domeHighlightCenter,
            radius = radius * 0.58f
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Plus
    )

    // 3. Контровой подповерхностный рефлекс (Subsurface Rim Bounce) снизу-справа
    val bounceCenter = Offset(center.x + radius * 0.38f, center.y + radius * 0.40f)
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to primaryColor.copy(alpha = (0.26f + 0.14f * energy) * intensity),
            0.55f to secondaryColor.copy(alpha = 0.10f * intensity),
            1.0f to Color.Transparent,
            center = bounceCenter,
            radius = radius * 0.56f
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Plus
    )

    // 4. Интерактивный 3D-вихрь концентрации энергии под пальцем (при удержании или ведении пальцем по сфере)
    if (touchPoint3D != null && touchAttractionStrength > 0.005f) {
        val spot = touchPoint3D.projectedScreenOffset
        val spotRadius = radius * (0.38f + 0.06f * sin(phaseRad * 3.4f)) * (0.60f + 0.40f * touchAttractionStrength)

        drawCircle(
            brush = Brush.radialGradient(
                0.0f to Color.White.copy(alpha = 0.62f * touchAttractionStrength * intensity),
                0.28f to secondaryColor.copy(alpha = 0.54f * touchAttractionStrength * intensity),
                0.64f to primaryColor.copy(alpha = 0.26f * touchAttractionStrength * intensity),
                1.0f to Color.Transparent,
                center = spot,
                radius = spotRadius * 1.3f
            ),
            radius = radius,
            center = center,
            blendMode = BlendMode.Plus
        )

        // Перспективно-сжатые 3D-волны на выпуклой поверхности сферы вокруг точки касания
        val normalAngleDeg = (atan2(touchPoint3D.normY, touchPoint3D.normX) * 180.0 / PI).toFloat()
        for (waveIdx in 0..1) {
            val wavePulse = ((phaseRad * 1.4f + waveIdx * 0.5f) % 1f + 1f) % 1f
            val rx = radius * (0.12f + 0.22f * wavePulse) * (0.7f + 0.3f * touchAttractionStrength)
            val ry = rx * touchPoint3D.limbForeshortening
            val waveAlpha = (1f - wavePulse) * 0.58f * touchAttractionStrength * intensity

            rotate(degrees = normalAngleDeg + 90f, pivot = spot) {
                drawOval(
                    color = lerp(secondaryColor, Color.White, 0.5f).copy(alpha = waveAlpha.coerceIn(0f, 0.75f)),
                    topLeft = Offset(spot.x - rx, spot.y - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = (2.2f - waveIdx * 0.6f).dp.toPx()),
                    blendMode = BlendMode.Plus
                )
            }
        }
    }
}

/**
 * Отрисовка шелковистых трёхмерных гироскопических энергетических лент (`DefaultGyroRibbonSpecs`)
 * с перспективным изменением толщины, мягким неоновым свечением и разделением на заднюю (`z < 0`)
 * и переднюю (`z >= 0`) полусферы.
 */
private fun DrawScope.drawGyroscopic3DSilkRibbons(
    center: Offset,
    sphereRadius: Float,
    phaseRad: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float,
    drawFrontHemisphere: Boolean
) {
    val segments = 72
    val stepAngle = (2.0 * PI / segments).toFloat()
    val cameraDistance = sphereRadius * 3.3f

    for ((ribbonIndex, spec) in DefaultGyroRibbonSpecs.withIndex()) {
        // Живая 3D-прецессия плоскости орбиты, чтобы ленты плавно покачивались в 3D-пространстве
        val dynamicTiltXDeg = spec.baseTiltXDeg + 5.5f * sin(phaseRad * spec.precessionRate + ribbonIndex)
        val dynamicTiltZDeg = spec.baseTiltZDeg + 6.5f * cos(phaseRad * spec.precessionRate * 0.85f + ribbonIndex)

        val pitchRad = (dynamicTiltXDeg * PI / 180.0).toFloat()
        val rollRad = (dynamicTiltZDeg * PI / 180.0).toFloat()
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        val cosRoll = cos(rollRad)
        val sinRoll = sin(rollRad)

        val baseOrbitRadius = sphereRadius * spec.radiusRatio * (1f + energy * 0.045f)
        val baseColor = if (spec.useSecondaryColor) secondaryColor else primaryColor
        val accentColor = if (spec.useSecondaryColor) primaryColor else secondaryColor
        val ribbonPhase = phaseRad * spec.speedMultiplier + spec.phaseOffsetRad

        var prevX = 0f
        var prevY = 0f
        var prevZ = 0f
        var hasPrev = false

        for (i in 0..segments) {
            val theta = i * stepAngle
            val harmonicWave = sphereRadius * (0.018f + 0.026f * energy) *
                sin(theta * spec.waveHarmonic + ribbonPhase * 1.5f)
            val r = baseOrbitRadius + harmonicWave

            val xLocal = r * cos(theta)
            val zLocal = r * sin(theta)

            val yPitch = -zLocal * cosPitch + harmonicWave * 0.45f * cos(theta * 2f - ribbonPhase)
            val zPitch = zLocal * sinPitch

            var xWorld = xLocal * cosRoll - yPitch * sinRoll
            var yWorld = xLocal * sinRoll + yPitch * cosRoll
            var zWorld = zPitch

            // При удержании пальца передние дуги энергетических лент эластично изгибаются к точке касания
            if (touchPoint3D != null && touchAttractionStrength > 0.01f && zWorld > 0f) {
                val dx = touchPoint3D.worldX - xWorld
                val dy = touchPoint3D.worldY - yWorld
                val dz = touchPoint3D.worldZ - zWorld
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                val pull = (1f - (dist / (sphereRadius * 1.85f)).coerceIn(0f, 1f)) *
                    0.36f * touchAttractionStrength
                xWorld += dx * pull
                yWorld += dy * pull
                zWorld += dz * pull
            }

            val perspective = cameraDistance / (cameraDistance - zWorld).coerceAtLeast(sphereRadius * 0.78f)
            val screenX = center.x + xWorld * perspective
            val screenY = center.y + yWorld * perspective

            if (hasPrev) {
                val midZ = (zWorld + prevZ) * 0.5f
                val isFront = midZ >= 0f
                if (isFront == drawFrontHemisphere) {
                    val depthFactor = ((midZ / baseOrbitRadius) + 1f).coerceIn(0f, 2f) * 0.5f
                    val midTheta = theta - stepAngle * 0.5f

                    // Две бегущие световые волны вдоль каждой 3D-ленты
                    val waveA = ((cos(midTheta - ribbonPhase) + 1f) * 0.5f)
                    val waveB = ((cos(midTheta * 2f + ribbonPhase * 0.7f) + 1f) * 0.5f)
                    val crestHighlight = (waveA * waveA * waveA * 0.78f + waveB * waveB * 0.22f).coerceIn(0f, 1f)

                    val alpha = if (isFront) {
                        (0.24f + 0.58f * crestHighlight + 0.16f * depthFactor) * intensity
                    } else {
                        (0.06f + 0.14f * crestHighlight) * intensity
                    }

                    val baseThicknessPx = spec.ribbonThicknessDp.dp.toPx() * perspective
                    val coreStrokePx = baseThicknessPx *
                        (if (isFront) 0.72f + 0.68f * crestHighlight else 0.48f)

                    // 1. Широкое мягкое свечение шелковистой ленты (Outer Silk Glow)
                    if (isFront || crestHighlight > 0.35f) {
                        drawLine(
                            color = baseColor.copy(alpha = (alpha * (if (isFront) 0.32f else 0.18f)).coerceIn(0.01f, 0.42f)),
                            start = Offset(prevX, prevY),
                            end = Offset(screenX, screenY),
                            strokeWidth = coreStrokePx * 3.4f,
                            cap = StrokeCap.Round,
                            blendMode = BlendMode.Plus
                        )
                    }

                    // 2. Яркая плазменная сердцевина ленты
                    val coreColor = lerp(
                        baseColor,
                        Color.White,
                        if (isFront) (crestHighlight * 0.56f).coerceIn(0f, 0.68f) else 0.08f
                    )
                    drawLine(
                        color = coreColor.copy(alpha = alpha.coerceIn(0.03f, 0.94f)),
                        start = Offset(prevX, prevY),
                        end = Offset(screenX, screenY),
                        strokeWidth = coreStrokePx,
                        cap = StrokeCap.Round,
                        blendMode = BlendMode.Plus
                    )
                }
            }

            prevX = screenX
            prevY = screenY
            prevZ = zWorld
            hasPrev = true
        }

        // Светящийся энергетический импульс (photon node), скользящий вдоль 3D-ленты
        val headTheta = ribbonPhase
        val headXLocal = baseOrbitRadius * cos(headTheta)
        val headZLocal = baseOrbitRadius * sin(headTheta)
        val headYPitch = -headZLocal * cosPitch
        val headZWorld = headZLocal * sinPitch

        if ((headZWorld >= 0f) == drawFrontHemisphere) {
            var headXWorld = headXLocal * cosRoll - headYPitch * sinRoll
            var headYWorld = headXLocal * sinRoll + headYPitch * cosRoll
            var effectiveHeadZ = headZWorld

            if (touchPoint3D != null && touchAttractionStrength > 0.01f && effectiveHeadZ > 0f) {
                val pull = 0.40f * touchAttractionStrength
                headXWorld += (touchPoint3D.worldX - headXWorld) * pull
                headYWorld += (touchPoint3D.worldY - headYWorld) * pull
                effectiveHeadZ += (touchPoint3D.worldZ - effectiveHeadZ) * pull
            }

            val headPerspective = cameraDistance / (cameraDistance - effectiveHeadZ).coerceAtLeast(sphereRadius * 0.78f)
            val headPos = Offset(
                center.x + headXWorld * headPerspective,
                center.y + headYWorld * headPerspective
            )
            val flareAlpha = (if (drawFrontHemisphere) 0.92f else 0.22f) * intensity
            val flareRadius = (if (ribbonIndex == 0) 3.8.dp else 2.8.dp).toPx() * headPerspective

            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = flareAlpha),
                    0.35f to accentColor.copy(alpha = flareAlpha * 0.65f),
                    1.0f to Color.Transparent,
                    center = headPos,
                    radius = flareRadius * 3.2f
                ),
                radius = flareRadius * 3.2f,
                center = headPos,
                blendMode = BlendMode.Plus
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
    val ringRadiusX = radius * 1.38f
    val ringRadiusY = radius * 0.44f
    val tiltAngleDegrees = -18f

    rotate(degrees = tiltAngleDegrees, pivot = center) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.34f),
                    accentColor.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = center,
                radius = ringRadiusX
            ),
            topLeft = Offset(center.x - ringRadiusX, center.y - ringRadiusY),
            size = Size(ringRadiusX * 2f, ringRadiusY * 2f),
            style = Stroke(width = 2.6.dp.toPx()),
            blendMode = BlendMode.Plus
        )

        val scanAngle = orbitProgress * 2f * PI.toFloat()
        val pulseX = center.x + ringRadiusX * cos(scanAngle)
        val pulseY = center.y + ringRadiusY * sin(scanAngle)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    goldColor.copy(alpha = 0.92f),
                    accentColor.copy(alpha = 0.36f),
                    Color.Transparent
                ),
                center = Offset(pulseX, pulseY),
                radius = 11.dp.toPx()
            ),
            radius = 11.dp.toPx(),
            center = Offset(pulseX, pulseY),
            blendMode = BlendMode.Plus
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
            radius = 3.8.dp.toPx(),
            center = Offset(beacon1X, beacon1Y),
            blendMode = BlendMode.Plus
        )
    }

    rotate(degrees = -34f, pivot = center) {
        val ang2 = (orbitProgress + 0.5f) * 2f * PI.toFloat()
        val beacon2X = center.x + orbitRadiusX * cos(ang2)
        val beacon2Y = center.y + orbitRadiusY * sin(ang2)
        drawCircle(
            color = emeraldColor,
            radius = 3.8.dp.toPx(),
            center = Offset(beacon2X, beacon2Y),
            blendMode = BlendMode.Plus
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
        val extraDist = i * (10.dp.toPx() + maxExpansion * (i / 2f)) + 2.dp.toPx() * breathProgress
        val currentRadius = radius + extraDist
        val alpha = (0.50f - (i * 0.15f)).coerceAtLeast(0.10f) * (0.5f + energy * 0.5f)

        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = 144f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = Offset(center.x - currentRadius, center.y - currentRadius),
            size = Size(currentRadius * 2f, currentRadius * 2f),
            style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round),
            blendMode = BlendMode.Plus
        )

        drawArc(
            color = color.copy(alpha = alpha),
            startAngle = -36f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = Offset(center.x - currentRadius, center.y - currentRadius),
            size = Size(currentRadius * 2f, currentRadius * 2f),
            style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round),
            blendMode = BlendMode.Plus
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
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            blendMode = BlendMode.Plus
        )
        drawArc(
            color = color.copy(alpha = 0.78f),
            startAngle = 180f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2f, outerRadius * 2f),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            blendMode = BlendMode.Plus
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
        style = Stroke(width = 2.4.dp.toPx()),
        blendMode = BlendMode.Plus
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
