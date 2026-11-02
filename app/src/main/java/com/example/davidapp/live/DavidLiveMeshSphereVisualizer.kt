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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Цветовые токены премиальной дизайн-системы Obsidian Neo-Bank для David Live (Issue #56).
 */
private val ObsidianCoreDark = Color(0xFF040A14)
private val ObsidianCoreDeep = Color(0xFF010409)
private val ObsidianShadowPole = Color(0xFF010206)

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
 * Порог удержания пальца на сфере (в мс), после которого активируется
 * интерактивное 3D-притяжение световых сгустков вместо короткого нажатия (tap).
 */
internal const val SPHERE_TOUCH_HOLD_ACTIVATION_MS = 165L

/**
 * Классификация жеста пользователя на 3D-сфере David Live:
 * - [SHORT_TAP_ACTION] — короткое нажатие без перетаскивания (выполняет штатное контекстное действие сферы:
 *   переключение микрофона, открытие настроек API-ключа или переподключение).
 * - [HOLD_OR_DRAG_ATTRACTION] — удержание или перемещение пальца по поверхности сферы
 *   (запускает притяжение 3D-сгустков энергии к точке касания без вызова tap-действия).
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

    val cameraDistance = safeRadius * 3.4f
    val perspectiveScale = (cameraDistance / (cameraDistance - worldZ).coerceAtLeast(safeRadius * 0.8f))
        .coerceIn(0.82f, 1.42f)

    val projectedScreenOffset = Offset(
        x = sphereCenter.x + worldX * perspectiveScale,
        y = sphereCenter.y + worldY * perspectiveScale
    )
    val limbForeshortening = (0.42f + 0.58f * normZ).coerceIn(0.42f, 1.0f)

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
        // Быстрое, но мягкое схождение сгустков к пальцу (~180–240 мс)
        val riseRate = 6.2f
        val factor = 1f - exp(-dtSec * riseRate)
        (clampedCurrent + (1f - clampedCurrent) * factor).coerceIn(0f, 1f)
    } else {
        // Плавное органическое рассеивание сгустков после отпускания пальца (~450–550 мс)
        val decayRate = 3.6f
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
    val followRate = 32f
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
            coreGlow = EmeraldPrimary.copy(alpha = 0.28f),
            waveAccent = EmeraldMint
        )

        DavidLiveVisualState.USER_SPEAKING -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = TurquoiseCyan,
            haloGlow = TurquoiseCyan.copy(alpha = 0.46f),
            coreGlow = SkyBlueGlow.copy(alpha = 0.32f),
            waveAccent = TurquoiseSpeech
        )

        DavidLiveVisualState.THINKING -> SpherePalette(
            primary = ThinkingViolet,
            secondary = ThinkingCyan,
            haloGlow = ThinkingIndigo.copy(alpha = 0.44f),
            coreGlow = ThinkingViolet.copy(alpha = 0.30f),
            waveAccent = ThinkingCyan
        )

        DavidLiveVisualState.FINANCE_LOOKUP -> SpherePalette(
            primary = EmeraldMint,
            secondary = TurquoiseSpeech,
            haloGlow = EmeraldDeep.copy(alpha = 0.42f),
            coreGlow = GoldApiAmber.copy(alpha = 0.25f),
            waveAccent = EmeraldMint
        )

        DavidLiveVisualState.ANALYZING -> SpherePalette(
            primary = ThinkingCyan,
            secondary = ThinkingViolet,
            haloGlow = ThinkingIndigo.copy(alpha = 0.46f),
            coreGlow = ThinkingCyan.copy(alpha = 0.30f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.PREPARING_RESPONSE -> SpherePalette(
            primary = TurquoiseSpeech,
            secondary = EmeraldPrimary,
            haloGlow = EmeraldDeep.copy(alpha = 0.40f),
            coreGlow = TurquoiseSpeech.copy(alpha = 0.28f),
            waveAccent = EmeraldPrimary
        )

        DavidLiveVisualState.DAVID_SPEAKING -> SpherePalette(
            primary = DavidSpeakingTurquoise,
            secondary = DavidSpeakingEmerald,
            haloGlow = EmeraldDeep.copy(alpha = 0.52f),
            coreGlow = DavidSpeakingTurquoise.copy(alpha = 0.36f),
            waveAccent = DavidSpeakingEmerald
        )

        DavidLiveVisualState.CONNECTING,
        DavidLiveVisualState.RECONNECTING -> SpherePalette(
            primary = ThinkingCyan,
            secondary = TurquoiseSpeech,
            haloGlow = ThinkingIndigo.copy(alpha = 0.36f),
            coreGlow = ThinkingCyan.copy(alpha = 0.24f),
            waveAccent = TurquoiseSpeech
        )

        DavidLiveVisualState.OFFLINE -> SpherePalette(
            primary = CrimsonOffline,
            secondary = CrimsonLight,
            haloGlow = CrimsonGlow.copy(alpha = 0.44f),
            coreGlow = CrimsonOffline.copy(alpha = 0.30f),
            waveAccent = CrimsonLight
        )

        DavidLiveVisualState.API_MISSING -> SpherePalette(
            primary = GoldApiAmber,
            secondary = GoldApiGlow,
            haloGlow = GoldApiWarm.copy(alpha = 0.42f),
            coreGlow = GoldApiAmber.copy(alpha = 0.28f),
            waveAccent = GoldApiGlow
        )

        DavidLiveVisualState.ERROR -> SpherePalette(
            primary = CrimsonMagentaError,
            secondary = VioletError,
            haloGlow = VioletGlow.copy(alpha = 0.44f),
            coreGlow = CrimsonMagentaError.copy(alpha = 0.30f),
            waveAccent = VioletError
        )

        DavidLiveVisualState.SUCCESS -> SpherePalette(
            primary = SuccessMint,
            secondary = SuccessGreen,
            haloGlow = SuccessGreen.copy(alpha = 0.54f),
            coreGlow = SuccessMint.copy(alpha = 0.36f),
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
    // 1. Главная диагональная световая орбита
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
 * Спецификация объёмного 3D-сгустка энергии, движущегося внутри и вдоль сферических оболочек ядра.
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
    VolumetricEnergyClusterSpec(-48f, 0.0f, 0.84f, 0.92f, 1.4f, 4.2f, false),
    VolumetricEnergyClusterSpec(-34f, 1.1f, 0.68f, -0.78f, 1.8f, 3.6f, true),
    VolumetricEnergyClusterSpec(-22f, 2.3f, 0.90f, 1.12f, 1.2f, 4.8f, false),
    VolumetricEnergyClusterSpec(-14f, 3.5f, 0.56f, 0.64f, 2.1f, 3.4f, true),
    VolumetricEnergyClusterSpec(-6f, 4.7f, 0.88f, -1.04f, 1.5f, 4.4f, false),
    VolumetricEnergyClusterSpec(0f, 0.6f, 0.74f, 0.86f, 1.7f, 5.0f, true),
    VolumetricEnergyClusterSpec(8f, 1.9f, 0.92f, 1.18f, 1.3f, 4.0f, false),
    VolumetricEnergyClusterSpec(16f, 3.1f, 0.62f, -0.72f, 1.9f, 3.8f, true),
    VolumetricEnergyClusterSpec(24f, 4.2f, 0.86f, 0.96f, 1.6f, 4.6f, false),
    VolumetricEnergyClusterSpec(35f, 5.4f, 0.72f, -0.88f, 1.4f, 3.9f, true),
    VolumetricEnergyClusterSpec(46f, 0.9f, 0.82f, 0.76f, 1.5f, 4.1f, false),
    VolumetricEnergyClusterSpec(-40f, 4.0f, 0.78f, 1.05f, 1.6f, 3.7f, true),
    VolumetricEnergyClusterSpec(28f, 2.5f, 0.54f, -0.66f, 2.2f, 3.5f, false),
    VolumetricEnergyClusterSpec(-10f, 5.8f, 0.80f, 1.22f, 1.3f, 4.5f, true),
    VolumetricEnergyClusterSpec(52f, 3.7f, 0.76f, -0.94f, 1.7f, 3.6f, false),
    VolumetricEnergyClusterSpec(-28f, 1.7f, 0.89f, 0.84f, 1.4f, 4.3f, true)
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
    val latWaveDeg = 14f * sin(phaseRad * spec.verticalWaveFreq + clusterIndex * 0.7f)
    val phiRad = ((spec.baseLatitudeDeg + latWaveDeg) * PI / 180.0).toFloat()

    val radialPulse = 1f + energy * 0.08f * sin(phaseRad * 2.4f + clusterIndex)
    val shellR = safeRadius * spec.shellRadiusRatio * radialPulse

    val cosPhi = cos(phiRad)
    val sinPhi = sin(phiRad)
    val x0 = shellR * cosPhi * cos(dynamicLongitude)
    val y0 = shellR * sinPhi
    val z0 = shellR * cosPhi * sin(dynamicLongitude)

    // Наклон оси сферы (Pitch 20° и медленная прецессия Roll) для убедительного 3D-ракурса
    val pitchRad = (20.0 * PI / 180.0).toFloat()
    val rollRad = (10.0 * sin(phaseRad * 0.32f) * PI / 180.0).toFloat()
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
        // Локальное 3D-вращение сгустков вокруг точки касания, чтобы они формировали живой энергетический вихрь под пальцем
        val swirlAngle = phaseRad * 2.8f + clusterIndex * (2f * PI.toFloat() / DefaultVolumetricClusters.size)
        val swirlRadius = safeRadius * (0.06f + (clusterIndex % 4) * 0.032f)
        val targetX = touchPoint3D.worldX + swirlRadius * cos(swirlAngle) * touchPoint3D.limbForeshortening
        val targetY = touchPoint3D.worldY + swirlRadius * sin(swirlAngle) * touchPoint3D.limbForeshortening
        val targetZ = (touchPoint3D.worldZ + safeRadius * 0.05f * sin(swirlAngle * 1.5f))
            .coerceAtLeast(safeRadius * 0.14f)

        val dx = targetX - worldX
        val dy = targetY - worldY
        val dz = targetZ - worldZ
        val dist3D = sqrt(dx * dx + dy * dy + dz * dz)
        val proximityWeight = (1f - (dist3D / (safeRadius * 2.3f)).coerceIn(0f, 0.65f))
        appliedPull = (clampedTouchStrength * (0.58f + 0.36f * proximityWeight)).coerceIn(0f, 0.94f)

        // Интерполируем в 3D и проецируем на сферический объём, чтобы сгустки огибали 3D-поверхность сферы
        val mixedX = worldX + dx * appliedPull
        val mixedY = worldY + dy * appliedPull
        val mixedZ = worldZ + dz * appliedPull
        val mixedMag = sqrt(mixedX * mixedX + mixedY * mixedY + mixedZ * mixedZ).coerceAtLeast(1f)
        val desiredMag = shellR + (safeRadius * 0.88f - shellR) * appliedPull
        val magScale = desiredMag / mixedMag

        worldX = mixedX * magScale
        worldY = mixedY * magScale
        worldZ = mixedZ * magScale
    }

    val cameraDistance = safeRadius * 3.2f
    val perspectiveScale = (cameraDistance / (cameraDistance - worldZ).coerceAtLeast(safeRadius * 0.75f))
        .coerceIn(0.68f, 1.48f)

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
 * Живая 3D энергетическая сфера **David Live** со световыми орбитами, внутренней плазменной
 * динамикой и интерактивным 3D-касанием (Issue #56 Redesign + Дополнение к ТЗ).
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

    // Отслеживаем lifecycle: при сворачивании приложения останавливаем покадровый цикл и сбрасываем удержание касания
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

    // Реальные спектральные полосы из rayMagnitudes для модуляции внутренних слоёв плазмы
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

    // Состояние интерактивного 3D-касания и плавного следования за пальцем
    var targetTouchNormX by remember { mutableFloatStateOf(0f) }
    var targetTouchNormY by remember { mutableFloatStateOf(0f) }
    var smoothedTouchNormX by remember { mutableFloatStateOf(0f) }
    var smoothedTouchNormY by remember { mutableFloatStateOf(0f) }
    var touchAttractionStrength by remember { mutableFloatStateOf(0f) }
    val isTouchHoldActiveState by rememberUpdatedState(isTouchHoldActive)
    val targetTouchNormXState by rememberUpdatedState(targetTouchNormX)
    val targetTouchNormYState by rememberUpdatedState(targetTouchNormY)

    // Непрерывный аккумулятор фазы вращения — никогда не сбрасывается при смене visualState или после отпускания пальца
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

    // Плавный коэффициент яркости орбит и внутренних слоёв (приглушается при Mute и Offline)
    val targetOrbitIntensity = when {
        shouldApplyMutedVisualPalette(visualState, isMicMuted) -> 0.58f
        visualState == DavidLiveVisualState.OFFLINE -> 0.74f
        visualState == DavidLiveVisualState.API_MISSING -> 0.86f
        visualState == DavidLiveVisualState.ERROR -> 0.82f + 0.16f * breathProgress
        visualState == DavidLiveVisualState.USER_SPEAKING ||
            visualState == DavidLiveVisualState.DAVID_SPEAKING -> 0.94f + 0.06f * smoothedEnergy
        else -> 0.88f
    }
    val orbitIntensity by animateFloatAsState(
        targetValue = targetOrbitIntensity,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "sphere_orbit_intensity"
    )

    val orbitProgressNormalized = ((continuousOrbitPhaseRad / (2f * PI.toFloat())) % 1f + 1f) % 1f
    val yawDegreesContinuous = ((continuousOrbitPhaseRad * 180f / PI.toFloat()) * 0.45f) % 360f

    // Интерактивная иконка внутри ядра для состояний Mute, Offline, API Missing, Error и Success
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
                        val approxSphereRadius = (min(size.width, size.height) / 2f) * 0.62f
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
                            if (activeChange == null) {
                                break
                            }

                            if (!activeChange.pressed) {
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
            val baseRadius = (size.minDimension / 2f) * 0.62f
            val dynamicRadius = baseRadius * (
                1f +
                    0.025f * sin(breathProgress * PI.toFloat()) +
                    smoothedEnergy * 0.085f
                )

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

            // 1. Внешнее атмосферное свечение (Neon Halo) + микро-частицы в пространстве вокруг сферы
            val haloRadius = dynamicRadius * (1.48f + 0.08f * breathProgress + smoothedEnergy * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedHaloColor.copy(alpha = (animatedHaloColor.alpha * 1.18f).coerceAtMost(0.70f)),
                        animatedHaloColor.copy(alpha = animatedHaloColor.alpha * 0.44f),
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

            // 2. ЗАДНЯЯ ПОЛУСФЕРА 3D-орбит (z < 0) — проходит ЗА объёмным ядром сферы
            drawThreeDimensionalOrbits(
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

            // 3. Базовое 3D-затенение сферы и глубокие внутренние слои текучей плазмы
            drawVolumetricSphereDeepBase(
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
                visualState = visualState
            )

            // 4. ЗАДНЯЯ ПОЛУСФЕРА (z < 0) внутренних 3D-потоков и объёмных световых сгустков
            // (визуально уходят за плотное преломляющее ядро сферы и частично скрываются им)
            drawSubtleInnerEnergyMesh(
                center = center,
                radius = dynamicRadius * 0.94f,
                breathProgress = breathProgress,
                yawDegrees = yawDegreesContinuous,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                intensity = orbitIntensity,
                touchPoint3D = activeTouch3D,
                touchAttractionStrength = touchAttractionStrength,
                drawFrontHemisphere = false
            )

            drawVolumetric3DEnergyClusters(
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

            // 5. Плотное преломляющее внутреннее ядро (частично перекрывает задние сгустки z < 0, создавая настоящую 3D-глубину)
            drawRefractiveInnerCoreLayer(
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

            // 6. ПЕРЕДНЯЯ ПОЛУСФЕРА (z >= 0) внутренних 3D-потоков и объёмных световых сгустков
            // (проходят ПЕРЕД ядром в 3D-перспективе и собираются под пальцем при удержании)
            drawSubtleInnerEnergyMesh(
                center = center,
                radius = dynamicRadius * 0.96f,
                breathProgress = breathProgress,
                yawDegrees = yawDegreesContinuous,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                intensity = orbitIntensity,
                touchPoint3D = activeTouch3D,
                touchAttractionStrength = touchAttractionStrength,
                drawFrontHemisphere = true
            )

            drawVolumetric3DEnergyClusters(
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

            // 7. Выпуклая стеклянная оболочка 3D-сферы, преломления, каустика касания и Fresnel Rim
            drawConvexGlassSurfaceAndTouchCaustics(
                center = center,
                radius = dynamicRadius,
                phaseRad = continuousOrbitPhaseRad,
                energy = smoothedEnergy,
                primaryColor = animatedPrimaryColor,
                secondaryColor = animatedSecondaryColor,
                intensity = orbitIntensity,
                touchPoint3D = activeTouch3D,
                touchAttractionStrength = touchAttractionStrength
            )

            // 8. ПЕРЕДНЯЯ ПОЛУСФЕРА 3D-орбит (z >= 0) — огибает сферу СПЕРЕДИ с яркими световыми потоками
            drawThreeDimensionalOrbits(
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

            // 9. Специфические акценты состояний поверх орбит
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

        // Интерактивный значок состояния внутри ядра сферы (для Mute, Offline, API Missing, Error, Success)
        if (centerStateEmblemIcon != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .alpha((0.88f - 0.35f * touchAttractionStrength).coerceIn(0.45f, 0.92f))
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ObsidianCoreDark.copy(alpha = 0.68f),
                                animatedPrimaryColor.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = animatedPrimaryColor.copy(alpha = 0.45f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = centerStateEmblemIcon,
                    contentDescription = null,
                    tint = animatedSecondaryColor.copy(alpha = 0.92f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Базовое трёхмерное сферическое тело с теневым полюсом (terminator) и тремя независимыми
 * полупрозрачными внутренними слоями текучей плазмы, реагирующими на состояние и аудиоспектр.
 */
private fun DrawScope.drawVolumetricSphereDeepBase(
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
    visualState: DavidLiveVisualState
) {
    // 1. Объёмная сферическая тень (источник света сверху-слева, глубокий теневой терминатор снизу-справа)
    val shadowPoleCenter = Offset(center.x + radius * 0.24f, center.y + radius * 0.28f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ObsidianCoreDark.copy(alpha = 0.88f),
                ObsidianCoreDeep.copy(alpha = 0.95f),
                ObsidianShadowPole.copy(alpha = 0.99f)
            ),
            center = Offset(center.x - radius * 0.18f, center.y - radius * 0.20f),
            radius = radius * 1.18f
        ),
        radius = radius,
        center = center
    )

    // Внутреннее сферическое затенение по краям (Ambient Occlusion внутри толщи стекла)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                ObsidianCoreDeep.copy(alpha = 0.42f),
                ObsidianShadowPole.copy(alpha = 0.78f)
            ),
            center = Offset(center.x - radius * 0.10f, center.y - radius * 0.12f),
            radius = radius
        ),
        radius = radius,
        center = center
    )

    val stateFlowFactor = when (visualState) {
        DavidLiveVisualState.THINKING,
        DavidLiveVisualState.FINANCE_LOOKUP,
        DavidLiveVisualState.ANALYZING,
        DavidLiveVisualState.PREPARING_RESPONSE -> 1.25f
        DavidLiveVisualState.USER_SPEAKING,
        DavidLiveVisualState.DAVID_SPEAKING -> 1.45f
        else -> 1.0f
    }

    // 2. Слой плазмы №1 (Глубокое низкочастотное облако — движется по 3D-траектории Лиссажу)
    val cloud1Center = Offset(
        x = center.x + radius * (0.28f + 0.08f * lowBandEnergy) * cos(phaseRad * 0.58f * stateFlowFactor),
        y = center.y - radius * (0.24f + 0.08f * lowBandEnergy) * sin(phaseRad * 0.44f * stateFlowFactor)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = (0.36f + 0.22f * lowBandEnergy + 0.10f * energy) * intensity),
                primaryColor.copy(alpha = (0.24f + 0.14f * breathProgress) * intensity),
                coreGlowColor.copy(alpha = 0.12f * intensity),
                Color.Transparent
            ),
            center = cloud1Center,
            radius = radius * (0.86f + 0.08f * lowBandEnergy)
        ),
        radius = radius,
        center = center
    )

    // 3. Слой плазмы №2 (Встречное среднечастотное энергетическое течение на средней глубине)
    val cloud2Center = Offset(
        x = center.x - radius * 0.30f * sin(phaseRad * 0.72f * stateFlowFactor + 1.4f),
        y = center.y + radius * 0.26f * cos(phaseRad * 0.52f * stateFlowFactor + 0.8f)
    )
    val blendedMidColor = lerp(primaryColor, secondaryColor, 0.5f + 0.5f * sin(phaseRad * 0.8f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                blendedMidColor.copy(alpha = (0.32f + 0.22f * midBandEnergy) * intensity),
                primaryColor.copy(alpha = (0.16f + 0.10f * energy) * intensity),
                Color.Transparent
            ),
            center = cloud2Center,
            radius = radius * (0.74f + 0.10f * midBandEnergy)
        ),
        radius = radius,
        center = center
    )

    // 4. Слой плазмы №3 (Высокочастотная внутренняя волна / каустический перелив)
    val cloud3Center = Offset(
        x = shadowPoleCenter.x - radius * 0.22f * cos(phaseRad * 0.94f),
        y = shadowPoleCenter.y - radius * 0.18f * sin(phaseRad * 0.82f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                secondaryColor.copy(alpha = (0.26f + 0.24f * highBandEnergy) * intensity),
                coreGlowColor.copy(alpha = (0.15f + 0.12f * energy) * intensity),
                Color.Transparent
            ),
            center = cloud3Center,
            radius = radius * (0.64f + 0.12f * highBandEnergy)
        ),
        radius = radius,
        center = center
    )
}

/**
 * Полупрозрачное плотное преломляющее ядро в центре сферы, которое частично перекрывает
 * заднюю полусферу (`z < 0`) сгустков и потоков, создавая ощущение реальной оптической толщи.
 */
private fun DrawScope.drawRefractiveInnerCoreLayer(
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
    val shiftX = if (touchPoint3D != null) touchPoint3D.worldX * 0.22f * touchAttractionStrength else 0f
    val shiftY = if (touchPoint3D != null) touchPoint3D.worldY * 0.22f * touchAttractionStrength else 0f
    val nucleusCenter = Offset(
        x = center.x + radius * 0.05f * cos(phaseRad * 0.7f) + shiftX,
        y = center.y + radius * 0.05f * sin(phaseRad * 0.6f) + shiftY
    )
    val nucleusRadius = radius * (0.56f + 0.04f * breathProgress + 0.06f * energy)

    // Мягкая оптическая вуаль ядра, приглушающая элементы, проходящие позади центра сферы (z < 0)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                ObsidianCoreDark.copy(alpha = 0.54f),
                coreGlowColor.copy(alpha = (0.34f + 0.16f * energy) * intensity),
                primaryColor.copy(alpha = (0.20f + 0.12f * breathProgress) * intensity),
                Color.Transparent
            ),
            center = nucleusCenter,
            radius = nucleusRadius
        ),
        radius = nucleusRadius,
        center = nucleusCenter
    )

    // Светящийся энергетический центр ядра
    val innerPulseRadius = radius * (0.34f + 0.05f * sin(phaseRad * 1.3f) + 0.08f * energy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = (0.18f + 0.18f * energy + 0.14f * touchAttractionStrength) * intensity),
                secondaryColor.copy(alpha = (0.32f + 0.18f * energy) * intensity),
                primaryColor.copy(alpha = 0.16f * intensity),
                Color.Transparent
            ),
            center = nucleusCenter,
            radius = innerPulseRadius
        ),
        radius = innerPulseRadius,
        center = nucleusCenter
    )
}

/**
 * Отрисовка объёмных 3D-сгустков энергии, движущихся в трёх измерениях внутри и по поверхности сферы.
 * - При `drawFrontHemisphere == false` рисуются только сгустки задней полусферы (`z < 0`) — меньшего
 *   перспективного размера и мягкой яркости, уходящие за ядро.
 * - При `drawFrontHemisphere == true` рисуются сгустки передней полусферы (`z >= 0`) — крупнее,
 *   контрастнее и ярче, проходящие перед ядром и притягивающиеся к пальцу при удержании.
 */
private fun DrawScope.drawVolumetric3DEnergyClusters(
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
        val projected = computeVolumetricCluster3D(
            clusterIndex = index,
            spec = spec,
            center = center,
            sphereRadius = sphereRadius,
            phaseRad = phaseRad,
            energy = energy,
            touchPoint3D = touchPoint3D,
            touchAttractionStrength = touchAttractionStrength
        )

        val isFront = projected.worldZ >= 0f
        if (isFront != drawFrontHemisphere) continue

        // Плавное затухание на границе экватора глубины (z ≈ 0), чтобы переход спереди/сзади был бесшовным
        val horizonSmooth = (abs(projected.worldZ) / (sphereRadius * 0.18f)).coerceIn(0.25f, 1f)
        val baseClusterColor = if (spec.useSecondaryColor) secondaryColor else primaryColor
        val accentClusterColor = if (spec.useSecondaryColor) primaryColor else secondaryColor

        val depthAlpha = if (isFront) {
            (0.38f + 0.44f * projected.depthNormalized + 0.24f * projected.touchPullFactor + 0.14f * energy) *
                intensity * horizonSmooth
        } else {
            (0.12f + 0.18f * projected.depthNormalized) * intensity * horizonSmooth
        }

        val coreRadiusPx = spec.baseRadiusDp.dp.toPx() *
            projected.perspectiveScale *
            (if (isFront) 1.0f + 0.22f * energy + 0.28f * projected.touchPullFactor else 0.74f)
        val auraRadiusPx = coreRadiusPx * (if (isFront) 3.4f + 0.8f * projected.touchPullFactor else 2.4f)

        // Мягкое объёмное свечение вокруг сгустка
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    baseClusterColor.copy(alpha = (depthAlpha * 0.72f).coerceIn(0.04f, 0.82f)),
                    accentClusterColor.copy(alpha = (depthAlpha * 0.28f).coerceIn(0.02f, 0.42f)),
                    Color.Transparent
                ),
                center = projected.screenOffset,
                radius = auraRadiusPx
            ),
            radius = auraRadiusPx,
            center = projected.screenOffset
        )

        // Яркое ядро сгустка (на передней полусфере и при притяжении к пальцу приобретает бело-неоновый накал)
        val coreColor = lerp(
            baseClusterColor,
            Color.White,
            if (isFront) (0.38f + 0.45f * projected.touchPullFactor).coerceIn(0f, 0.85f) else 0.12f
        )
        drawCircle(
            color = coreColor.copy(alpha = (depthAlpha * 1.15f).coerceIn(0.06f, 0.96f)),
            radius = coreRadiusPx * 0.62f,
            center = projected.screenOffset
        )
    }
}

/**
 * Отрисовка выпуклой стеклянной поверхности 3D-сферы, краевого преломления (Fresnel Rim Light),
 * скользящих поверхностных бликов и локальной 3D-концентрации энергии под пальцем при касании.
 */
private fun DrawScope.drawConvexGlassSurfaceAndTouchCaustics(
    center: Offset,
    radius: Float,
    phaseRad: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float
) {
    // 1. Если активно удержание/перемещение пальца по сфере — рисуем 3D-концентрацию энергии на выпуклой поверхности
    if (touchPoint3D != null && touchAttractionStrength > 0.005f) {
        val touchSpot = touchPoint3D.projectedScreenOffset
        val spotRadius = radius * (0.34f + 0.08f * sin(phaseRad * 3f)) * (0.65f + 0.35f * touchAttractionStrength)

        // Объёмное подповерхностное свечение вокруг точки касания
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.46f * touchAttractionStrength * intensity),
                    secondaryColor.copy(alpha = 0.52f * touchAttractionStrength * intensity),
                    primaryColor.copy(alpha = 0.28f * touchAttractionStrength * intensity),
                    Color.Transparent
                ),
                center = touchSpot,
                radius = spotRadius * 1.35f
            ),
            radius = radius,
            center = center
        )

        // Перспективно-сжатое 3D-кольцо преломления на поверхности сферы вокруг пальца
        val normalAngleDeg = (atan2(touchPoint3D.normY, touchPoint3D.normX) * 180.0 / PI).toFloat()
        val ringRx = radius * 0.22f * (0.75f + 0.25f * touchAttractionStrength)
        val ringRy = ringRx * touchPoint3D.limbForeshortening
        rotate(degrees = normalAngleDeg + 90f, pivot = touchSpot) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.72f * touchAttractionStrength * intensity),
                        secondaryColor.copy(alpha = 0.45f * touchAttractionStrength * intensity),
                        Color.Transparent
                    ),
                    center = touchSpot,
                    radius = ringRx * 1.2f
                ),
                topLeft = Offset(touchSpot.x - ringRx, touchSpot.y - ringRy),
                size = Size(ringRx * 2f, ringRy * 2f),
                style = Stroke(width = 2.0.dp.toPx())
            )
        }
    }

    // 2. Главный выпуклый стеклянный блик в верхней левой части сферы (плавно дышит и смещается по 3D-поверхности)
    val specularDriftX = radius * 0.04f * cos(phaseRad * 0.45f)
    val specularDriftY = radius * 0.04f * sin(phaseRad * 0.45f)
    val highlightCenter = Offset(
        x = center.x - radius * 0.32f + specularDriftX,
        y = center.y - radius * 0.34f + specularDriftY
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f * intensity),
                secondaryColor.copy(alpha = 0.20f * intensity),
                Color.Transparent
            ),
            center = highlightCenter,
            radius = radius * 0.54f
        ),
        radius = radius,
        center = center
    )

    // Изогнутая дуга блика (caustic arc) вдоль выпуклой верхней левой грани 3D-сферы
    val innerRimRadius = radius * 0.91f
    drawArc(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.36f * intensity),
                secondaryColor.copy(alpha = 0.22f * intensity),
                Color.Transparent
            ),
            center = center
        ),
        startAngle = 196f,
        sweepAngle = 68f,
        useCenter = false,
        topLeft = Offset(center.x - innerRimRadius, center.y - innerRimRadius),
        size = Size(innerRimRadius * 2f, innerRimRadius * 2f),
        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
    )

    // 3. Нижний правый контровой рефлекс (Sub-surface Rim Bounce) для подчёркивания 3D-шарообразности
    val bounceCenter = Offset(center.x + radius * 0.36f, center.y + radius * 0.38f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = (0.28f + 0.14f * energy) * intensity),
                secondaryColor.copy(alpha = 0.12f * intensity),
                Color.Transparent
            ),
            center = bounceCenter,
            radius = radius * 0.62f
        ),
        radius = radius,
        center = center
    )

    // 4. Двухслойный стеклянный обод (Fresnel Rim Light) с ощущением толщины стенки сферы
    val innerWallRadius = radius * 0.972f
    drawCircle(
        color = secondaryColor.copy(alpha = 0.20f * intensity),
        radius = innerWallRadius,
        center = center,
        style = Stroke(width = 1.2.dp.toPx())
    )

    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                secondaryColor.copy(alpha = 0.86f * intensity),
                primaryColor.copy(alpha = 0.36f * intensity),
                Color.White.copy(alpha = 0.78f * intensity),
                primaryColor.copy(alpha = 0.48f * intensity),
                secondaryColor.copy(alpha = 0.86f * intensity)
            ),
            center = center
        ),
        radius = radius,
        center = center,
        style = Stroke(width = 2.2.dp.toPx())
    )

    // Мягкий внешний ореол непосредственно вокруг стеклянной кромки
    drawCircle(
        color = primaryColor.copy(alpha = (0.24f + 0.14f * energy) * intensity),
        radius = radius * 1.02f,
        center = center,
        style = Stroke(width = 4.6.dp.toPx())
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
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float,
    drawFrontHemisphere: Boolean
) {
    val segments = 64
    val stepAngle = (2.0 * PI / segments).toFloat()
    val cameraDistance = sphereRadius * 3.6f

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
            val waveOffset = if (energy > 0.06f) {
                sphereRadius * 0.028f * energy * sin(theta * 3f + orbitDynamicPhase * 2f)
            } else {
                0f
            }
            val r = orbitRadius + waveOffset

            val xLocal = r * cos(theta)
            val zLocal = r * sin(theta)

            val yPitch = -zLocal * cosPitch
            val zPitch = zLocal * sinPitch

            var xWorld = xLocal * cosRoll - yPitch * sinRoll
            var yWorld = xLocal * sinRoll + yPitch * cosRoll
            var zWorld = zPitch

            // Мягкое 3D-притяжение передней дуги орбиты к пальцу при удержании
            if (touchPoint3D != null && touchAttractionStrength > 0.01f && zWorld > 0f) {
                val dx = touchPoint3D.worldX - xWorld
                val dy = touchPoint3D.worldY - yWorld
                val dz = touchPoint3D.worldZ - zWorld
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                val pull = (1f - (dist / (sphereRadius * 1.8f)).coerceIn(0f, 1f)) *
                    0.32f * touchAttractionStrength
                xWorld += dx * pull
                yWorld += dy * pull
                zWorld += dz * pull
            }

            val perspective = cameraDistance / (cameraDistance - zWorld).coerceAtLeast(sphereRadius * 0.8f)
            val screenX = center.x + xWorld * perspective
            val screenY = center.y + yWorld * perspective

            if (hasPrev) {
                val midZ = (zWorld + prevZ) * 0.5f
                val isFront = midZ >= 0f
                if (isFront == drawFrontHemisphere) {
                    val depthFactor = ((midZ / orbitRadius) + 1f) * 0.5f
                    val midTheta = theta - stepAngle * 0.5f
                    val streamWave = ((cos(midTheta - orbitDynamicPhase) + 1f) * 0.5f)
                    val streamHighlight = streamWave * streamWave * streamWave

                    val baseAlpha = if (isFront) {
                        (0.34f + 0.52f * streamHighlight + 0.14f * depthFactor) * intensity
                    } else {
                        (0.08f + 0.14f * streamHighlight) * intensity
                    }

                    val strokePx = spec.ribbonWidthDp.dp.toPx() *
                        (if (isFront) 0.88f + 0.58f * streamHighlight else 0.58f)

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
                        color = segmentColor.copy(alpha = baseAlpha.coerceIn(0.04f, 0.96f)),
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

        // Светящаяся энергетическая частица (flare), бегущая по орбите в 3D
        val headTheta = orbitDynamicPhase
        val headXLocal = orbitRadius * cos(headTheta)
        val headZLocal = orbitRadius * sin(headTheta)
        val headYPitch = -headZLocal * cosPitch
        val headZWorld = headZLocal * sinPitch

        if ((headZWorld >= 0f) == drawFrontHemisphere) {
            var headXWorld = headXLocal * cosRoll - headYPitch * sinRoll
            var headYWorld = headXLocal * sinRoll + headYPitch * cosRoll
            var effectiveHeadZ = headZWorld

            if (touchPoint3D != null && touchAttractionStrength > 0.01f && effectiveHeadZ > 0f) {
                val pull = 0.38f * touchAttractionStrength
                headXWorld += (touchPoint3D.worldX - headXWorld) * pull
                headYWorld += (touchPoint3D.worldY - headYWorld) * pull
                effectiveHeadZ += (touchPoint3D.worldZ - effectiveHeadZ) * pull
            }

            val headPerspective = cameraDistance / (cameraDistance - effectiveHeadZ).coerceAtLeast(sphereRadius * 0.8f)
            val headPos = Offset(
                center.x + headXWorld * headPerspective,
                center.y + headYWorld * headPerspective
            )
            val flareAlpha = (if (drawFrontHemisphere) 0.92f else 0.24f) * intensity
            val flareRadius = (if (orbitIndex == 0) 3.5.dp else 2.6.dp).toPx() * headPerspective

            drawCircle(
                color = accentColor.copy(alpha = flareAlpha * 0.44f),
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
 * Отрисовка внутренних 3D-линий энергетического потока по сферической поверхности с честным
 * разделением задней (`z < 0`) и передней (`z >= 0`) полусфер и реакцией на 3D-касание.
 */
private fun DrawScope.drawSubtleInnerEnergyMesh(
    center: Offset,
    radius: Float,
    breathProgress: Float,
    yawDegrees: Float,
    energy: Float,
    primaryColor: Color,
    secondaryColor: Color,
    intensity: Float,
    touchPoint3D: SphereTouchPoint3D?,
    touchAttractionStrength: Float,
    drawFrontHemisphere: Boolean
) {
    val pitchRadians = (22.0 * PI / 180.0).toFloat()
    val yawRadians = (yawDegrees * PI / 180.0).toFloat()
    val cosPitch = cos(pitchRadians)
    val sinPitch = sin(pitchRadians)
    val cosYaw = cos(yawRadians)
    val sinYaw = sin(yawRadians)

    val breathingRadius = radius * (1f + 0.02f * sin(breathProgress * 2f * PI.toFloat()))
    val cameraDistance = breathingRadius * 3.3f

    val latitudeAngles = floatArrayOf(-42f, -14f, 14f, 42f)
    val stepTheta = (PI * 2.0 / 32.0).toFloat()

    for ((latIdx, latDeg) in latitudeAngles.withIndex()) {
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
            val waveDeform = 0.036f * (0.35f + energy) * sin(theta * 3f + phi * 2f + yawRadians)
            val rCurrent = latRadius * (1f + waveDeform)

            val x0 = rCurrent * cos(theta)
            val z0 = rCurrent * sin(theta)

            val x1 = x0 * cosYaw + z0 * sinYaw
            val z1 = -x0 * sinYaw + z0 * cosYaw

            var x2 = x1
            var y2 = y0 * cosPitch - z1 * sinPitch
            var z2 = y0 * sinPitch + z1 * cosPitch

            // При удержании пальца внутренние энергетические нити плавно изгибаются к 3D-точке касания
            if (touchPoint3D != null && touchAttractionStrength > 0.01f && z2 > -breathingRadius * 0.2f) {
                val dx = touchPoint3D.worldX - x2
                val dy = touchPoint3D.worldY - y2
                val dz = touchPoint3D.worldZ - z2
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                val pull = (1f - (dist / (breathingRadius * 1.6f)).coerceIn(0f, 1f)) *
                    0.46f * touchAttractionStrength
                x2 += dx * pull
                y2 += dy * pull
                z2 += dz * pull
            }

            val perspectiveK = cameraDistance / (cameraDistance - z2).coerceAtLeast(breathingRadius * 0.75f)
            val screenX = center.x + x2 * perspectiveK
            val screenY = center.y + y2 * perspectiveK

            if (!isFirst) {
                val midZ = (z2 + prevZ) * 0.5f
                val isFront = midZ >= 0f
                if (isFront == drawFrontHemisphere) {
                    val flowPulse = ((cos(theta * 2f - yawRadians * 1.6f + latIdx) + 1f) * 0.5f)
                    val strokeAlpha = if (isFront) {
                        (0.12f + 0.18f * flowPulse + 0.12f * touchAttractionStrength) * intensity
                    } else {
                        (0.04f + 0.06f * flowPulse) * intensity
                    }
                    val lineColor = if (latIdx % 2 == 0) secondaryColor else primaryColor

                    drawLine(
                        color = lineColor.copy(alpha = strokeAlpha.coerceIn(0.03f, 0.38f)),
                        start = Offset(prevScreenX, prevScreenY),
                        end = Offset(screenX, screenY),
                        strokeWidth = (if (isFront) 1.25.dp else 0.9.dp).toPx(),
                        cap = StrokeCap.Round
                    )
                }
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
