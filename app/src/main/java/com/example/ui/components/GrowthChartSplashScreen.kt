package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.davidapp.DavidSoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Статический флаг процесса: гарантирует, что стартовая анимация проигрывается ровно один раз за сессию
private var hasSplashPlayedInSession = false

private const val SPLASH_TOTAL_DURATION_MS = 1420
private const val PHASE_1_END = 0.40f          // Фаза 1 (0..568 мс): Пружинный присед (p1 <= 0.06f) и выстрел монетки вверх за край экрана
private const val PHASE_1_HOLD_RATIO = 0.12f   // Первые 12% Фазы 1: жаба приседает, монетка синхронно опускается с губами

// Ускоряющаяся кривая гравитации без торможения в конце — монетка и шторка пролетают нижний край на максимальной скорости
private val CoinFallAndPullEasing = CubicBezierEasing(0.45f, 0.0f, 0.90f, 0.85f)

// Предварительно выделенные цвета и конфигурации, чтобы не создавать списки на каждом кадре 120 FPS
private val CurtainEdgeGlowColors = listOf(
    Color(0xFF10B981).copy(alpha = 0.25f),
    Color(0xFF34D399).copy(alpha = 0.65f),
    Color(0xFFFBBF24).copy(alpha = 0.90f),
    Color(0xFF34D399).copy(alpha = 0.65f),
    Color(0xFF10B981).copy(alpha = 0.25f)
)

private val CurtainEdgeCoreColors = listOf(
    Color(0xFF10B981).copy(alpha = 0.55f),
    Color(0xFF6EE7B7),
    Color(0xFFFEF08A),
    Color(0xFF6EE7B7),
    Color(0xFF10B981).copy(alpha = 0.55f)
)

private val Coin3dRimGradientColors = listOf(
    Color(0xFF78350F),
    Color(0xFFB45309),
    Color(0xFFFDE047),
    Color(0xFFB45309),
    Color(0xFF78350F)
)

private val CoinRimGradientColors = listOf(
    Color(0xFFFDE047),
    Color(0xFFF59E0B),
    Color(0xFFD97706)
)

private val CoinCoreGradientColors = listOf(
    Color(0xFFFEF08A),
    Color(0xFFFBBF24),
    Color(0xFFF59E0B)
)

private val SpeedLineConfigs = arrayOf(
    Triple(-1.15f, 0.55f, 0.35f),
    Triple(-0.58f, 0.82f, 0.15f),
    Triple(0f, 0.95f, 0.05f),
    Triple(0.58f, 0.78f, 0.18f),
    Triple(1.15f, 0.50f, 0.38f)
)

/**
 * Переиспользуемые [Path] для отрисовки на Canvas без аллокаций в `onDraw`.
 */
private class SplashReusablePaths {
    val curtainPath = Path()
    val topEdgePath = Path()
    val mouthPath = Path()
    val smileLinePath = Path()
    val upperLipOverlapPath = Path()
    val shinePath = Path()
    val crownPath = Path()
    val rubleBowlPath = Path()
    val embossedSmilePath = Path()
}

/**
 * Кэш всех градиентных кистей [Brush].
 * Внутри блока `Canvas { ... }` и всех функций отрисовки не создается ни одного нового объекта `Brush`.
 */
private class SplashCachedBrushes {
    val curtainEdgeGlowBrush = Brush.horizontalGradient(colors = CurtainEdgeGlowColors)
    val curtainEdgeCoreBrush = Brush.horizontalGradient(colors = CurtainEdgeCoreColors)

    private var cachedFrogRadius = -1f
    private var cachedCoinRadius = -1f
    private var cachedAuraRadius = -1f

    var frogShadowBrush: Brush = Brush.linearGradient(listOf(Color.Black, Color.Transparent))
        private set
    var frogHeadBrush: Brush = Brush.linearGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A)))
        private set
    var frogJawBrush: Brush = Brush.linearGradient(listOf(Color(0xFF86EFAC), Color(0xFF4ADE80)))
        private set
    var frogAuraBrush: Brush = Brush.linearGradient(listOf(Color(0xFF0D2818), Color.Transparent))
        private set
    var coin3dRimBrush: Brush = Brush.horizontalGradient(Coin3dRimGradientColors)
        private set
    var coinRimBrush: Brush = Brush.linearGradient(CoinRimGradientColors)
        private set
    var coinCoreBrush: Brush = Brush.linearGradient(CoinCoreGradientColors)
        private set
    var coinOuterGlowBrush: Brush = Brush.linearGradient(listOf(Color(0xFFFEF08A), Color.Transparent))
        private set

    fun ensureInitialized(frogBaseRadius: Float, baseCoinRadius: Float, auraRadius: Float) {
        val r = frogBaseRadius.coerceAtLeast(16f)
        if (abs(r - cachedFrogRadius) > 0.5f) {
            cachedFrogRadius = r
            val shadowRadius = (r * 1.15f).coerceAtLeast(4f)
            frogShadowBrush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.45f),
                    Color.Transparent
                ),
                center = Offset(0f, r * 0.22f),
                radius = shadowRadius
            )
            frogHeadBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF4ADE80),
                    Color(0xFF22C55E),
                    Color(0xFF16A34A)
                ),
                startY = -r * 0.95f,
                endY = r * 0.78f
            )
            frogJawBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF86EFAC),
                    Color(0xFF4ADE80)
                ),
                startY = -r * 0.08f,
                endY = r * 0.74f
            )
        }

        val cr = baseCoinRadius.coerceAtLeast(10f)
        if (abs(cr - cachedCoinRadius) > 0.5f) {
            cachedCoinRadius = cr
            coin3dRimBrush = Brush.horizontalGradient(
                colors = Coin3dRimGradientColors,
                startX = -cr,
                endX = cr
            )
            coinRimBrush = Brush.linearGradient(
                colors = CoinRimGradientColors,
                start = Offset(-cr, -cr),
                end = Offset(cr, cr)
            )
            val coreRadius = (cr * 0.74f).coerceAtLeast(2f)
            coinCoreBrush = Brush.radialGradient(
                colors = CoinCoreGradientColors,
                center = Offset(-cr * 0.22f, -cr * 0.22f),
                radius = (coreRadius * 1.3f).coerceAtLeast(2f)
            )
            coinOuterGlowBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFEF08A).copy(alpha = 0.72f),
                    Color(0xFFF59E0B).copy(alpha = 0.36f),
                    Color.Transparent
                ),
                center = Offset.Zero,
                radius = (cr * 2.6f).coerceAtLeast(4f)
            )
        }

        val ar = auraRadius.coerceAtLeast(10f)
        if (abs(ar - cachedAuraRadius) > 1f) {
            cachedAuraRadius = ar
            frogAuraBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF0D2818).copy(alpha = 0.95f),
                    Color(0xFF0A1E14).copy(alpha = 0.55f),
                    Color(0xFF080C14).copy(alpha = 0f)
                ),
                center = Offset.Zero,
                radius = ar
            )
        }
    }
}

/**
 * Основной стартовый экран приложения с процедурной анимацией на Jetpack Compose Canvas:
 * - Неизменный корневой `Box(modifier = Modifier.fillMaxSize())`, внутри которого первым слоем
 *   ВСЕГДА лежит `content()`, а вторым слоем поверх него рендерится `SplashCanvasOverlay`.
 * - После завершения анимации размонтируется только `Canvas`, а `content()` остается на месте без рекомпозиции.
 */
@Composable
fun GrowthChartSplashScreen(
    onSplashFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    var isSplashFinished by remember { mutableStateOf(hasSplashPlayedInSession) }

    LaunchedEffect(Unit) {
        if (hasSplashPlayedInSession) {
            isSplashFinished = true
            onSplashFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Слой 1 (Нижний): Неизменный контент приложения рендерится сразу под шторкой
        content()

        // Слой 2 (Верхний): Аппаратный Canvas-оверлей со шторкой, жабой и золотой монеткой
        if (!isSplashFinished) {
            SplashCanvasOverlay(
                onSplashFinished = {
                    hasSplashPlayedInSession = true
                    isSplashFinished = true
                    onSplashFinished()
                }
            )
        }
    }
}

/**
 * Перегрузка для вызова поверх корневого `Box` в `MainActivity.kt`.
 */
@Composable
fun GrowthChartSplashScreen(
    isExiting: Boolean = false,
    onExitFinished: () -> Unit = {}
) {
    var isSplashFinished by remember { mutableStateOf(hasSplashPlayedInSession) }

    LaunchedEffect(Unit) {
        if (hasSplashPlayedInSession) {
            isSplashFinished = true
            onExitFinished()
        }
    }

    if (!isSplashFinished) {
        SplashCanvasOverlay(
            onSplashFinished = {
                hasSplashPlayedInSession = true
                isSplashFinished = true
                onExitFinished()
            }
        )
    }
}

@Composable
private fun SplashCanvasOverlay(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val masterTimeline = remember { Animatable(0f) }
    val cachedBrushes = remember { SplashCachedBrushes() }
    val reusablePaths = remember { SplashReusablePaths() }

    LaunchedEffect(Unit) {
        if (hasSplashPlayedInSession) {
            onSplashFinished()
            return@LaunchedEffect
        }

        // Предварительный прогрев PCM-буфера звона монетки в фоновом потоке
        DavidSoundManager.warmUpCoinToss()

        // Ждем ровно 2 кадра и минимальную паузу 50 мс — и сразу начинаем живой замах жабы!
        repeat(2) {
            withFrameNanos { }
        }
        delay(50L)

        // Звон монетки совпадает с завершением удлинённого замаха (12% фазы 1 ≈ 68 мс от старта таймлайна)
        launch {
            delay(68L)
            DavidSoundManager.playCoinToss(context)
        }

        masterTimeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SPLASH_TOTAL_DURATION_MS,
                easing = LinearEasing
            )
        )
        hasSplashPlayedInSession = true
        onSplashFinished()
    }

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        // Читаем значение таймлайна строго внутри DrawScope, чтобы на каждом кадре 120 FPS
        // перезапускалась только фаза отрисовки без единой рекомпозиции!
        val t = masterTimeline.value

        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val centerX = w / 2f
        val frogY = h * 0.50f
        val frogBaseRadius = (minOf(w, h) * 0.17f).coerceAtLeast(36f)
        val baseCoinRadius = (minOf(w, h) * 0.088f).coerceAtLeast(24f)
        val auraRadius = (minOf(w, h) * 0.52f).coerceAtLeast(10f)
        val maxDipDepth = 96.dp.toPx()

        // Инициализируем/кэшируем все Brush строго один раз для текущих размеров экрана
        cachedBrushes.ensureInitialized(
            frogBaseRadius = frogBaseRadius,
            baseCoinRadius = baseCoinRadius,
            auraRadius = auraRadius
        )

        // Прогресс Фазы 1 (0f..0.40f) и Фазы 2 (0.40f..1.0f)
        val p1 = (t / PHASE_1_END).coerceIn(0f, 1f)
        val p2 = ((t - PHASE_1_END) / (1f - PHASE_1_END)).coerceIn(0f, 1f)

        val tossScaleX: Float
        val tossScaleY: Float
        val windupSquatPushY: Float
        val flightUpProgress: Float

        if (t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO) {
            val windupProgress = (p1 / PHASE_1_HOLD_RATIO).coerceIn(0f, 1f)
            val windupEased = FastOutSlowInEasing.transform(windupProgress)
            tossScaleX = lerpFloat(1.0f, 1.10f, windupEased)
            tossScaleY = lerpFloat(1.0f, 0.86f, windupEased)
            windupSquatPushY = windupEased * (frogBaseRadius * 0.14f)
            flightUpProgress = 0f
        } else if (t <= PHASE_1_END) {
            val launchProgress = ((p1 - PHASE_1_HOLD_RATIO) / (1f - PHASE_1_HOLD_RATIO)).coerceIn(0f, 1f)
            flightUpProgress = FastOutSlowInEasing.transform(launchProgress)
            if (launchProgress < 0.45f) {
                val k = FastOutSlowInEasing.transform(launchProgress / 0.45f)
                tossScaleX = lerpFloat(1.10f, 0.95f, k)
                tossScaleY = lerpFloat(0.86f, 1.08f, k)
                windupSquatPushY = lerpFloat(frogBaseRadius * 0.14f, -frogBaseRadius * 0.06f, k)
            } else {
                val k = LinearOutSlowInEasing.transform((launchProgress - 0.45f) / 0.55f)
                tossScaleX = lerpFloat(0.95f, 1.0f, k)
                tossScaleY = lerpFloat(1.08f, 1.0f, k)
                windupSquatPushY = lerpFloat(-frogBaseRadius * 0.06f, 0f, k)
            }
        } else {
            tossScaleX = 1.0f
            tossScaleY = 1.0f
            windupSquatPushY = 0f
            flightUpProgress = 1f
        }

        val coinY: Float
        val coinSpinAngle: Float
        val coinScale: Float
        val coinGlowIntensity: Float

        val curtainTopY: Float
        val currentDipDepth: Float
        val revealActive: Boolean

        // Вычисляем положение шторки для Фазы 2, чтобы знать contactProgress жабы
        if (t <= PHASE_1_END) {
            curtainTopY = 0f
            currentDipDepth = 0f
            revealActive = false
        } else {
            val easedFall = CoinFallAndPullEasing.transform(p2)
            val fallStartY = -baseCoinRadius * 1.5f
            val fallEndY = h + maxDipDepth * 1.35f
            val fallingCoinY = lerpFloat(fallStartY, fallEndY, easedFall)
            if (fallingCoinY <= 0f) {
                curtainTopY = 0f
                currentDipDepth = 0f
                revealActive = false
            } else {
                revealActive = true
                val dipRamp = (fallingCoinY / (maxDipDepth * 1.35f)).coerceIn(0f, 1f)
                val elasticWave = 1f + 0.10f * sin(p2 * PI.toFloat() * 3.5f)
                currentDipDepth = maxDipDepth * FastOutSlowInEasing.transform(dipRamp) * elasticWave
                curtainTopY = (fallingCoinY - currentDipDepth).coerceAtLeast(0f)
            }
        }

        val curtainDipBottomY = curtainTopY + currentDipDepth

        // Когда прогиб шторки с монеткой доходит до макушки жабы (curtainDipBottomY >= frogTopY),
        // жаба упруго сжимается вниз и плавно растворяется по мере прохождения шторки сквозь неё
        val frogTopY = frogY - frogBaseRadius * 0.95f
        val frogBottomY = frogY + frogBaseRadius * 0.85f
        val contactProgress = if (revealActive && curtainDipBottomY >= frogTopY) {
            ((curtainDipBottomY - frogTopY) / (frogBottomY - frogTopY).coerceAtLeast(1f)).coerceIn(0f, 1f)
        } else {
            0f
        }

        val contactEased = FastOutSlowInEasing.transform(contactProgress)
        val contactScaleX = lerpFloat(1.0f, 1.14f, contactEased)
        val contactScaleY = lerpFloat(1.0f, 0.76f, contactEased)
        val contactPushY = contactEased * (frogBaseRadius * 0.42f) + windupSquatPushY

        val currentFrogCenterY = frogY + contactPushY
        val currentFrogScaleX = tossScaleX * contactScaleX
        val currentFrogScaleY = tossScaleY * contactScaleY

        // Точная посадка монетки в нижней челюсти жабы (как на системной иконке ic_launcher_foreground.xml)
        val mouthOriginY = currentFrogCenterY + (frogBaseRadius * 0.43f) * currentFrogScaleY

        if (t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO) {
            // --- Первые 12% Фазы 1: жаба опускает губы, монетка следует за ними и остаётся зажатой ---
            coinY = mouthOriginY
            coinSpinAngle = 0f
            coinScale = 0.82f
            coinGlowIntensity = 0.30f
        } else if (t <= PHASE_1_END) {
            // --- После синхронного опускания губ начинается поворот и затем бросок монетки ---
            val launchProgress = ((p1 - PHASE_1_HOLD_RATIO) / (1f - PHASE_1_HOLD_RATIO)).coerceIn(0f, 1f)
            // The lips grip the coin while it rotates to its first edge-on pose.
            // The coin does NOT travel upwards until that quarter turn is complete.
            val gripRatio = 0.12f
            val gripProgress = (launchProgress / gripRatio).coerceIn(0f, 1f)
            val freeFlight = ((launchProgress - gripRatio) / (1f - gripRatio)).coerceIn(0f, 1f)
            val easedUp = FastOutSlowInEasing.transform(freeFlight)
            val offscreenTopTargetY = -baseCoinRadius * 2.4f
            coinY = lerpFloat(mouthOriginY, offscreenTopTargetY, easedUp)
            // 0 -> PI/4 while gripped, then PI/4 -> 2PI during free flight.
            // No restart or reversal at release.
            coinSpinAngle = if (launchProgress < gripRatio) {
                gripProgress * (PI.toFloat() * 0.25f)
            } else {
                PI.toFloat() * (0.25f + freeFlight * 1.75f)
            }
            coinScale = lerpFloat(0.82f, 1.06f, easedUp)
            coinGlowIntensity = (0.35f + freeFlight * 0.50f).coerceIn(0f, 1f)
        } else {
            // --- ФАЗА 2: Падение сверху из-за экрана и синхронный захват темной шторки ---
            val easedFall = CoinFallAndPullEasing.transform(p2)
            val fallStartY = -baseCoinRadius * 1.5f
            val fallEndY = h + maxDipDepth * 1.35f
            coinY = lerpFloat(fallStartY, fallEndY, easedFall)

            // Два оборота в обратном направлении при падении; продолжение от +2*PI
            coinSpinAngle = (2.0f + p2 * 4.0f) * PI.toFloat()

            if (coinY <= 0f) {
                coinScale = 1.08f
                coinGlowIntensity = 0.75f
            } else {
                val dipRamp = (coinY / (maxDipDepth * 1.35f)).coerceIn(0f, 1f)
                val impactPulse = sin(dipRamp * PI.toFloat()).coerceIn(0f, 1f)
                coinScale = 1.06f + 0.08f * impactPulse
                coinGlowIntensity = (0.85f - p2 * 0.45f).coerceIn(0.25f, 0.9f)
            }
        }

        val frogAlpha = if (contactProgress <= 0.30f) {
            1f
        } else {
            (1f - ((contactProgress - 0.30f) / 0.70f)).coerceIn(0f, 1f)
        }

        // Зрачки следят за фактической координатой монетки на всём пути:
        // вниз к губам перед броском, вверх во время полёта и вниз при падении.
        // Монетка движется по центральной вертикальной траектории, поэтому горизонтальный сдвиг не нужен.
        val eyeCenterY = currentFrogCenterY - frogBaseRadius * 0.50f
        val eyeLookYOffset = (
            (coinY - eyeCenterY) / (frogBaseRadius * 2.2f)
        ).coerceIn(-1f, 1f)

        val mouthOpenFactor = when {
            t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO -> {
                // В фазе замаха рот приоткрыт, чтобы монетка лежала в нижней челюсти под верхней губой
                0.65f
            }
            t <= PHASE_1_END -> {
                val launchProgress = ((p1 - PHASE_1_HOLD_RATIO) / (1f - PHASE_1_HOLD_RATIO)).coerceIn(0f, 1f)
                if (launchProgress < 0.28f) {
                    lerpFloat(0.65f, 1.0f, launchProgress / 0.28f)
                } else {
                    lerpFloat(1.0f, 0.35f, ((launchProgress - 0.28f) / 0.72f).coerceIn(0f, 1f))
                }
            }
            contactProgress > 0f -> {
                (0.35f + 0.35f * sin(contactProgress * PI.toFloat())).coerceIn(0.2f, 0.7f)
            }
            else -> 0.35f
        }.coerceIn(0.18f, 1f)

        // 1. РИСУЕМ ТЕМНУЮ ШТОРКУ И ЖАБУ (в Фазе 1 — напрямую без clipPath!)
        drawElasticCurtainWithFrog(
            paths = reusablePaths,
            brushes = cachedBrushes,
            centerX = centerX,
            curtainTopY = curtainTopY,
            dipDepth = currentDipDepth,
            revealActive = revealActive,
            frogCenter = Offset(centerX, currentFrogCenterY),
            frogBaseRadius = frogBaseRadius,
            auraRadius = auraRadius,
            frogScaleX = currentFrogScaleX,
            frogScaleY = currentFrogScaleY,
            frogAlpha = frogAlpha,
            eyeLookYOffset = eyeLookYOffset,
            mouthOpenFactor = mouthOpenFactor
        )

        // 2. РИСУЕМ СВЕТО-ШЛЕЙФ И СКОРОСТНЫЕ ЛИНИИ МОНЕТКИ БЕЗ СОЗДАНИЯ BRUSH
        val effectiveCoinRadius = baseCoinRadius * coinScale
        if (t <= PHASE_1_END && p1 > PHASE_1_HOLD_RATIO &&
            ((p1 - PHASE_1_HOLD_RATIO) / (1f - PHASE_1_HOLD_RATIO)) > 0.12f) {
            drawUpwardTossArcs(
                centerX = centerX,
                coinY = coinY,
                mouthY = mouthOriginY,
                coinRadius = effectiveCoinRadius,
                progress = flightUpProgress
            )
        } else if (t > PHASE_1_END) {
            val trailStrength = (1f - (p2 / 0.88f)).coerceIn(0f, 1f)
            if (trailStrength > 0.01f && coinY > -effectiveCoinRadius) {
                drawSpeedTrails(
                    centerX = centerX,
                    coinY = coinY,
                    originTopY = (coinY - h * 0.28f).coerceAtLeast(0f),
                    coinRadius = effectiveCoinRadius,
                    intensity = trailStrength
                )
            }
        }

        // 3. РИСУЕМ ДВУСТОРОННЮЮ ОБЪЕМНУЮ 3D-ЗОЛОТУЮ МОНЕТКУ (Аверс: ₽ с короной / Реверс: золотая жаба Давид)
        if (coinY + effectiveCoinRadius * 2f >= 0f && coinY - effectiveCoinRadius * 2f <= h) {
            if (t <= PHASE_1_END &&
                (p1 <= PHASE_1_HOLD_RATIO ||
                    ((p1 - PHASE_1_HOLD_RATIO) / (1f - PHASE_1_HOLD_RATIO)) < 0.12f)) {
                // Keep the coin gripped by the lip through its first quarter turn.
                // Hide only the upper part behind the real upper lip. The lower half
                // remains visible BELOW the mouth, as if the frog is gripping the coin.
                // Never clip to the entire mouth path: that hides the protruding half.
                val lipEdgeY = currentFrogCenterY +
                    frogBaseRadius * 0.07f * currentFrogScaleY
                clipRect(top = lipEdgeY) {
                    drawGoldenCoin(
                        paths = reusablePaths,
                        brushes = cachedBrushes,
                        center = Offset(centerX, coinY),
                        baseRadius = baseCoinRadius,
                        coinScale = coinScale,
                        spinAngle = coinSpinAngle,
                        glowIntensity = coinGlowIntensity,
                        timeProgress = t
                    )
                }
            } else {
                drawGoldenCoin(
                    paths = reusablePaths,
                    brushes = cachedBrushes,
                    center = Offset(centerX, coinY),
                    baseRadius = baseCoinRadius,
                    coinScale = coinScale,
                    spinAngle = coinSpinAngle,
                    glowIntensity = coinGlowIntensity,
                    timeProgress = t
                )
            }
        }


    }
}

/**
 * Отрисовка эластичной темной шторки (#080C14) и жабы:
 * - Когда `!revealActive` (вся Фаза 1 при взлете монетки и начало Фазы 2 до касания верха экрана),
 *   НЕ вызываем `clipPath(curtainPath)` вообще — рисуем `drawRect` и жабу напрямую без нагрузки на GPU stencil-буфер.
 * - Вызываем `clipPath(curtainPath)` ТОЛЬКО когда `revealActive == true`.
 */
private fun DrawScope.drawElasticCurtainWithFrog(
    paths: SplashReusablePaths,
    brushes: SplashCachedBrushes,
    centerX: Float,
    curtainTopY: Float,
    dipDepth: Float,
    revealActive: Boolean,
    frogCenter: Offset,
    frogBaseRadius: Float,
    auraRadius: Float,
    frogScaleX: Float,
    frogScaleY: Float,
    frogAlpha: Float,
    eyeLookYOffset: Float,
    mouthOpenFactor: Float
) {
    val w = size.width
    val h = size.height
    if (curtainTopY >= h + 40f) return

    if (!revealActive) {
        // БЫСТРЫЙ ПУТЬ ФАЗЫ 1: без clipPath!
        drawRect(
            color = Color(0xFF080C14),
            topLeft = Offset.Zero,
            size = Size(w, h)
        )

        if (frogAlpha > 0.01f) {
            withTransform({
                translate(left = frogCenter.x, top = frogCenter.y)
            }) {
                drawCircle(
                    brush = brushes.frogAuraBrush,
                    radius = auraRadius,
                    center = Offset.Zero,
                    alpha = frogAlpha
                )
            }

            drawEmojiFrog(
                paths = paths,
                brushes = brushes,
                center = frogCenter,
                baseRadius = frogBaseRadius,
                scaleX = frogScaleX,
                scaleY = frogScaleY,
                alpha = frogAlpha,
                eyeLookYOffset = eyeLookYOffset,
                mouthOpenFactor = mouthOpenFactor
            )
        }
        return
    }

    // ФАЗА 2 (revealActive == true): монетка прогибает и тянет шторку сверху вниз
    val dipBottomY = curtainTopY + dipDepth
    val shoulderSpan = (w * 0.38f).coerceAtLeast(80f)

    val curtainPath = paths.curtainPath
    curtainPath.reset()
    curtainPath.moveTo(0f, curtainTopY)
    curtainPath.lineTo((centerX - shoulderSpan).coerceAtLeast(0f), curtainTopY)
    curtainPath.cubicTo(
        centerX - shoulderSpan * 0.45f,
        curtainTopY,
        centerX - shoulderSpan * 0.28f,
        dipBottomY,
        centerX,
        dipBottomY
    )
    curtainPath.cubicTo(
        centerX + shoulderSpan * 0.28f,
        dipBottomY,
        centerX + shoulderSpan * 0.45f,
        curtainTopY,
        (centerX + shoulderSpan).coerceAtMost(w),
        curtainTopY
    )
    curtainPath.lineTo(w, curtainTopY)
    curtainPath.lineTo(w, h + 40f)
    curtainPath.lineTo(0f, h + 40f)
    curtainPath.close()

    clipPath(curtainPath) {
        drawRect(
            color = Color(0xFF080C14),
            topLeft = Offset.Zero,
            size = Size(w, h)
        )

        if (frogAlpha > 0.01f) {
            withTransform({
                translate(left = frogCenter.x, top = frogCenter.y)
            }) {
                drawCircle(
                    brush = brushes.frogAuraBrush,
                    radius = auraRadius,
                    center = Offset.Zero,
                    alpha = frogAlpha
                )
            }

            drawEmojiFrog(
                paths = paths,
                brushes = brushes,
                center = frogCenter,
                baseRadius = frogBaseRadius,
                scaleX = frogScaleX,
                scaleY = frogScaleY,
                alpha = frogAlpha,
                eyeLookYOffset = eyeLookYOffset,
                mouthOpenFactor = mouthOpenFactor
            )
        }
    }

    // Неоново-изумрудный и золотой кант вдоль изогнутой верхней границы шторки
    val topEdgePath = paths.topEdgePath
    topEdgePath.reset()
    topEdgePath.moveTo(0f, curtainTopY)
    topEdgePath.lineTo((centerX - shoulderSpan).coerceAtLeast(0f), curtainTopY)
    topEdgePath.cubicTo(
        centerX - shoulderSpan * 0.45f,
        curtainTopY,
        centerX - shoulderSpan * 0.28f,
        dipBottomY,
        centerX,
        dipBottomY
    )
    topEdgePath.cubicTo(
        centerX + shoulderSpan * 0.28f,
        dipBottomY,
        centerX + shoulderSpan * 0.45f,
        curtainTopY,
        (centerX + shoulderSpan).coerceAtMost(w),
        curtainTopY
    )
    topEdgePath.lineTo(w, curtainTopY)

    drawPath(
        path = topEdgePath,
        brush = brushes.curtainEdgeGlowBrush,
        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    drawPath(
        path = topEdgePath,
        brush = brushes.curtainEdgeCoreBrush,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * Отрисовка жабы с использованием кэшированных `frogHeadBrush`, `frogJawBrush`, `frogShadowBrush` и `SplashReusablePaths` (0 аллокаций на кадр).
 */
private fun DrawScope.drawEmojiFrog(
    paths: SplashReusablePaths,
    brushes: SplashCachedBrushes,
    center: Offset,
    baseRadius: Float,
    scaleX: Float,
    scaleY: Float,
    alpha: Float,
    eyeLookYOffset: Float,
    mouthOpenFactor: Float
) {
    val r = baseRadius.coerceAtLeast(16f)

    withTransform({
        translate(left = center.x, top = center.y)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
    }) {
        val shadowRadius = (r * 1.15f).coerceAtLeast(4f)
        drawCircle(
            brush = brushes.frogShadowBrush,
            radius = shadowRadius,
            center = Offset(0f, r * 0.22f),
            alpha = alpha
        )

        val eyeBumpRadius = r * 0.46f
        val eyeBumpOffsetX = r * 0.48f
        val eyeBumpOffsetY = -r * 0.52f

        val outlineColor = Color(0xFF15803D).copy(alpha = alpha)
        drawCircle(
            color = outlineColor,
            radius = eyeBumpRadius * 1.04f,
            center = Offset(-eyeBumpOffsetX, eyeBumpOffsetY)
        )
        drawCircle(
            color = outlineColor,
            radius = eyeBumpRadius * 1.04f,
            center = Offset(eyeBumpOffsetX, eyeBumpOffsetY)
        )
        drawOval(
            color = outlineColor,
            topLeft = Offset(-r * 1.05f, -r * 0.54f),
            size = Size(r * 2.10f, r * 1.38f)
        )

        drawCircle(
            brush = brushes.frogHeadBrush,
            radius = eyeBumpRadius,
            center = Offset(-eyeBumpOffsetX, eyeBumpOffsetY),
            alpha = alpha
        )
        drawCircle(
            brush = brushes.frogHeadBrush,
            radius = eyeBumpRadius,
            center = Offset(eyeBumpOffsetX, eyeBumpOffsetY),
            alpha = alpha
        )
        drawOval(
            brush = brushes.frogHeadBrush,
            topLeft = Offset(-r * 1.02f, -r * 0.52f),
            size = Size(r * 2.04f, r * 1.30f),
            alpha = alpha
        )

        drawOval(
            brush = brushes.frogJawBrush,
            topLeft = Offset(-r * 0.92f, -r * 0.06f),
            size = Size(r * 1.84f, r * 0.80f),
            alpha = alpha
        )

        drawOval(
            color = Color(0xFFBBF7D0).copy(alpha = 0.35f * alpha),
            topLeft = Offset(-r * 0.42f, -r * 0.38f),
            size = Size(r * 0.84f, r * 0.22f)
        )

        val cheekColor = Color(0xFFFB7185).copy(alpha = 0.30f * alpha)
        drawOval(
            color = cheekColor,
            topLeft = Offset(-r * 0.82f, r * 0.10f),
            size = Size(r * 0.32f, r * 0.20f)
        )
        drawOval(
            color = cheekColor,
            topLeft = Offset(r * 0.50f, r * 0.10f),
            size = Size(r * 0.32f, r * 0.20f)
        )

        val darkEmerald = Color(0xFF064E3B).copy(alpha = alpha)
        drawOval(
            color = darkEmerald,
            topLeft = Offset(-r * 0.18f, -r * 0.18f),
            size = Size(r * 0.10f, r * 0.14f)
        )
        drawOval(
            color = darkEmerald,
            topLeft = Offset(r * 0.08f, -r * 0.18f),
            size = Size(r * 0.10f, r * 0.14f)
        )

        val scleraRadius = eyeBumpRadius * 0.62f
        val pupilRadius = scleraRadius * 0.64f
        val pupilShiftY = eyeLookYOffset * (scleraRadius * 0.28f)
        val eyeY = eyeBumpOffsetY + r * 0.02f

        for (side in -1..1 step 2) {
            val eyeCenter = Offset(side * eyeBumpOffsetX, eyeY)
            drawCircle(
                color = outlineColor,
                radius = scleraRadius * 1.12f,
                center = eyeCenter
            )
            drawCircle(
                color = Color(0xFFFEF9C3).copy(alpha = alpha),
                radius = scleraRadius,
                center = eyeCenter
            )
            val pupilCenter = Offset(eyeCenter.x, eyeCenter.y + pupilShiftY)
            drawCircle(
                color = Color(0xFF0F172A).copy(alpha = alpha),
                radius = pupilRadius,
                center = pupilCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = pupilRadius * 0.38f,
                center = Offset(
                    pupilCenter.x - pupilRadius * 0.34f,
                    pupilCenter.y - pupilRadius * 0.34f
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * alpha),
                radius = pupilRadius * 0.18f,
                center = Offset(
                    pupilCenter.x + pupilRadius * 0.32f,
                    pupilCenter.y + pupilRadius * 0.30f
                )
            )
        }

        val mouthWidth = r * 1.32f
        val mouthHeight = r * (0.18f + 0.30f * mouthOpenFactor)
        val mouthTopY = r * 0.16f

        val mouthPath = paths.mouthPath
        mouthPath.reset()
        mouthPath.moveTo(-mouthWidth / 2f, mouthTopY)
        mouthPath.quadraticTo(0f, mouthTopY - r * 0.10f, mouthWidth / 2f, mouthTopY)
        mouthPath.quadraticTo(0f, mouthTopY + mouthHeight, -mouthWidth / 2f, mouthTopY)
        mouthPath.close()

        drawPath(
            path = mouthPath,
            color = darkEmerald
        )

        if (mouthOpenFactor > 0.2f) {
            drawOval(
                color = Color(0xFFF43F5E).copy(alpha = 0.75f * alpha),
                topLeft = Offset(-r * 0.24f, mouthTopY + mouthHeight * 0.18f),
                size = Size(r * 0.48f, mouthHeight * 0.36f)
            )
        }

        val smileLine = paths.smileLinePath
        smileLine.reset()
        smileLine.moveTo(-mouthWidth * 0.54f, mouthTopY)
        smileLine.quadraticTo(0f, mouthTopY - r * 0.10f, mouthWidth * 0.54f, mouthTopY)

        drawPath(
            path = smileLine,
            color = darkEmerald,
            style = Stroke(width = (r * 0.055f).coerceAtLeast(2f), cap = StrokeCap.Round)
        )
    }
}

/**
 * Отрисовка двусторонней объемной 3D-золотой монетки вокруг горизонтальной оси X:
 * - Аверс (`flipCos >= 0f`): рельефный знак рубля `₽` с короной.
 * - Реверс (`flipCos < 0f`): чеканный золотой барельеф головы жабы Давида (`drawEmbossedFrogEmblem`).
 * (0 аллокаций `Brush` внутри `onDraw`).
 */
private fun DrawScope.drawGoldenCoin(
    paths: SplashReusablePaths,
    brushes: SplashCachedBrushes,
    center: Offset,
    baseRadius: Float,
    coinScale: Float,
    spinAngle: Float,
    glowIntensity: Float,
    timeProgress: Float
) {
    val r = baseRadius.coerceAtLeast(10f)
    // Orthographic projection of a real, thick cylinder rotating about X.
    // The angular timeline is monotonically POSITIVE: reverse the previous
    // downward tumble. Never use abs(cos) for the face transform: it would mirror
    // each quarter-turn and look like the coin rocks back and forth.
    val flipCos = cos(spinAngle)
    val flipSin = sin(spinAngle)
    val projectedFaceY = if (abs(flipCos) < 0.015f) {
        if (flipCos < 0f) -0.015f else 0.015f
    } else flipCos
    val faceHeight = abs(projectedFaceY)
    val halfDepth = r * 0.24f
    val depthShift = flipSin * halfDepth
    // The frontmost visible cap changes at each half-turn.
    val capY = if (flipCos >= 0f) -depthShift else depthShift

    withTransform({
        translate(left = center.x, top = center.y)
        scale(scaleX = coinScale, scaleY = coinScale, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = brushes.coinOuterGlowBrush,
            radius = r * 2.6f,
            center = Offset.Zero,
            alpha = (0.45f + 0.55f * glowIntensity).coerceIn(0f, 1f)
        )

        // Project both faces at +/- depthShift, and connect them with
        // eight slices of the metal edge. At 90/270 degrees the edge has
        // actual thickness instead of collapsing into an infinitesimal line.
        val steps = 8
        for (step in 0..steps) {
            val fraction = step.toFloat() / steps
            val sliceY = lerpFloat(-depthShift, depthShift, fraction)
            drawOval(
                brush = brushes.coin3dRimBrush,
                topLeft = Offset(-r, sliceY - r * faceHeight),
                size = Size(r * 2f, r * 2f * faceHeight)
            )
        }
        // Alternating top/bottom lighting makes successive edge-on positions
        // distinguishable; a symmetric band looks identical in both directions.
        val edgeVisibility = abs(flipSin)
        if (edgeVisibility > 0.08f) {
            val edgeY = if (flipSin < 0f) -abs(depthShift) else abs(depthShift)
            drawLine(
                color = if (flipSin < 0f) Color(0xFFFFE88B)
                        else Color(0xFF78350F),
                start = Offset(-r * 0.88f, edgeY),
                end = Offset(r * 0.88f, edgeY),
                strokeWidth = (r * 0.09f).coerceAtLeast(2f),
                alpha = edgeVisibility * 0.85f,
                cap = StrokeCap.Round
            )
            for (notch in -4..4) {
                val x = notch * r * 0.19f
                drawLine(
                    color = Color(0xFF92400E).copy(alpha = edgeVisibility * 0.7f),
                    start = Offset(x, -abs(depthShift) * 0.7f),
                    end = Offset(x, abs(depthShift) * 0.7f),
                    strokeWidth = (r * 0.025f).coerceAtLeast(1f),
                    cap = StrokeCap.Round
                )
            }
        }

        // Signed face projection: its orientation continues THROUGH the
        // edge; the reverse emblem is drawn on the far side of the turn.
        withTransform({
            translate(left = 0f, top = capY)
            scale(scaleX = 1f, scaleY = projectedFaceY, pivot = Offset.Zero)
        }) {
            // Внешний золотой обод
            drawCircle(
                brush = brushes.coinRimBrush,
                radius = r,
                center = Offset.Zero
            )

            // Внутреннее утопленное кольцо со смещенным световым бликом фаски
            drawCircle(
                color = Color(0xFFB45309),
                radius = r * 0.82f,
                center = Offset.Zero
            )
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = 0.55f),
                radius = r * 0.80f,
                center = Offset(-r * 0.02f, -r * 0.03f)
            )

            // Центральное золотое ядро
            val coreRadius = (r * 0.74f).coerceAtLeast(2f)
            drawCircle(
                brush = brushes.coinCoreBrush,
                radius = coreRadius,
                center = Offset.Zero
            )

            // Диагональный солнечный блик
            val shineShift = sin(timeProgress * PI.toFloat() * 4f) * r * 0.12f
            val shinePath = paths.shinePath
            shinePath.reset()
            shinePath.moveTo(-r * 0.48f + shineShift, -r * 0.56f)
            shinePath.lineTo(-r * 0.16f + shineShift, -r * 0.68f)
            shinePath.lineTo(r * 0.48f + shineShift, r * 0.52f)
            shinePath.lineTo(r * 0.16f + shineShift, r * 0.66f)
            shinePath.close()

            drawPath(
                path = shinePath,
                color = Color(0xFFFEF08A).copy(alpha = 0.45f)
            )

            // Двусторонняя чеканка: Аверс (flipCos > 0f) — ₽ с короной, Реверс (flipCos < 0f) — золотая жаба Давид
            if (abs(flipCos) > 0.14f) {
                if (flipCos > 0f) {
                    drawEmbossedRubleAndCrownEmblem(paths = paths, r = r)
                } else {
                    drawEmbossedFrogEmblem(paths = paths, r = r)
                }
            }
        }

        // Foreground half of the cylindrical edge MUST be painted after the face.
        // Previously the face covered the rim at oblique angles, leaving grooves
        // visible only when the coin was exactly edge-on.
        val visibleRim = abs(flipSin)
        if (visibleRim > 0.025f) {
            val rimSide = if (flipSin >= 0f) 1f else -1f
            val rimNearY = rimSide * abs(depthShift)
            val faceEdgeY = capY + rimSide * r * faceHeight
            val rimOuterY = rimNearY + rimSide * r * faceHeight
            val rimBand = paths.upperLipOverlapPath
            rimBand.reset()
            rimBand.moveTo(-r, faceEdgeY)
            rimBand.quadraticTo(0f, faceEdgeY + rimSide * r * faceHeight * 0.10f, r, faceEdgeY)
            rimBand.lineTo(r, rimOuterY)
            rimBand.quadraticTo(0f, rimOuterY + rimSide * r * faceHeight * 0.10f, -r, rimOuterY)
            rimBand.close()
            drawPath(
                path = rimBand,
                brush = brushes.coin3dRimBrush,
                alpha = visibleRim.coerceIn(0f, 1f)
            )
            val grooveTop = minOf(faceEdgeY, rimOuterY)
            val grooveBottom = maxOf(faceEdgeY, rimOuterY)
            if (grooveBottom - grooveTop > 0.8f) {
                for (notch in -5..5) {
                    val x = notch * r * 0.16f
                    val roundness = sqrt((1f - (x / r) * (x / r)).coerceAtLeast(0f))
                    val y1 = lerpFloat(faceEdgeY, rimOuterY, 0.12f) * roundness
                    val y2 = lerpFloat(faceEdgeY, rimOuterY, 0.88f) * roundness
                    drawLine(
                        color = if (notch % 2 == 0) Color(0xFF78350F) else Color(0xFFFFE88B),
                        start = Offset(x, y1),
                        end = Offset(x, y2),
                        strokeWidth = (r * 0.026f).coerceAtLeast(1f),
                        alpha = visibleRim * 0.8f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Аверс монеты (`flipCos > 0f`): рельефная штамповка короны и знака рубля `₽`
 * (`#92400E` с верхним бликом `#FEF08A` и тенью `#78350F`).
 */
private fun DrawScope.drawEmbossedRubleAndCrownEmblem(
    paths: SplashReusablePaths,
    r: Float
) {
    val shadowColor = Color(0xFF78350F)
    val emblemColor = Color(0xFF92400E)
    val highlightColor = Color(0xFFFEF08A).copy(alpha = 0.55f)
    val reliefShiftY = r * 0.045f
    val highlightShiftY = -r * 0.022f

    val crownPath = paths.crownPath
    crownPath.reset()
    val cw = r * 0.42f
    val topY = -r * 0.52f
    val baseY = -r * 0.30f
    crownPath.moveTo(-cw * 0.75f, baseY)
    crownPath.lineTo(-cw * 0.90f, topY + r * 0.04f)
    crownPath.lineTo(-cw * 0.38f, baseY - r * 0.07f)
    crownPath.lineTo(0f, topY - r * 0.03f)
    crownPath.lineTo(cw * 0.38f, baseY - r * 0.07f)
    crownPath.lineTo(cw * 0.90f, topY + r * 0.04f)
    crownPath.lineTo(cw * 0.75f, baseY)
    crownPath.close()

    val strokeW = (r * 0.13f).coerceAtLeast(2f)
    val stemX = -r * 0.14f
    val topR = -r * 0.16f
    val bottomR = r * 0.48f
    val midR = r * 0.16f

    val bowlPath = paths.rubleBowlPath
    bowlPath.reset()
    bowlPath.moveTo(stemX, topR)
    bowlPath.lineTo(r * 0.10f, topR)
    bowlPath.arcTo(
        rect = Rect(
            left = -r * 0.05f,
            top = topR,
            right = r * 0.30f,
            bottom = midR
        ),
        startAngleDegrees = -90f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false
    )
    bowlPath.lineTo(stemX - r * 0.12f, midR)

    // 1) Нижний слой штамповки — глубокая тень рельефа (+reliefShiftY)
    withTransform({
        translate(left = 0f, top = reliefShiftY)
    }) {
        drawPath(path = crownPath, color = shadowColor)
        drawLine(
            color = shadowColor,
            start = Offset(stemX, topR),
            end = Offset(stemX, bottomR),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawPath(
            path = bowlPath,
            color = shadowColor,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            color = shadowColor,
            start = Offset(stemX - r * 0.12f, r * 0.31f),
            end = Offset(r * 0.20f, r * 0.31f),
            strokeWidth = strokeW * 0.88f,
            cap = StrokeCap.Round
        )
    }

    // 2) Основной слой штамповки (Offset.Zero)
    drawPath(path = crownPath, color = emblemColor)
    drawLine(
        color = emblemColor,
        start = Offset(stemX, topR),
        end = Offset(stemX, bottomR),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    drawPath(
        path = bowlPath,
        color = emblemColor,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawLine(
        color = emblemColor,
        start = Offset(stemX - r * 0.12f, r * 0.31f),
        end = Offset(r * 0.20f, r * 0.31f),
        strokeWidth = strokeW * 0.88f,
        cap = StrokeCap.Round
    )

    // 3) Тонкий верхний блик чеканки
    withTransform({
        translate(left = 0f, top = highlightShiftY)
    }) {
        drawPath(path = crownPath, color = highlightColor)
        drawLine(
            color = highlightColor,
            start = Offset(stemX, topR),
            end = Offset(stemX, bottomR),
            strokeWidth = strokeW * 0.45f,
            cap = StrokeCap.Round
        )
        drawPath(
            path = bowlPath,
            color = highlightColor,
            style = Stroke(width = strokeW * 0.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Реверс монеты (`flipCos < 0f`): чеканный золотой барельеф головы жабы Давида (внутри золотого ядра монетки):
 * 1) Светло-золотой блик барельефа со сдвигом `(-r * 0.02f, -r * 0.02f)` цветом `#FEF08A.copy(alpha = 0.7f)`
 *    и основной штамп цветом `#92400E` / `#B45309`.
 * 2) Два круглых бугорка глаз сверху (`Offset(-r * 0.24f, -r * 0.22f)` и `Offset(r * 0.24f, -r * 0.22f)`, `radius = r * 0.20f`)
 *    и широкий овал головы (`topLeft = Offset(-r * 0.48f, -r * 0.20f)`, `size = Size(r * 0.96f, r * 0.62f)`).
 * 3) Внутри глазниц — светло-золотые радужки (`#FDE047`, `radius = r * 0.12f`) и темные чеканные зрачки (`#78350F`, `radius = r * 0.07f`).
 * 4) Две ноздри по центру (`Offset(-r * 0.08f, -r * 0.04f)` и `Offset(r * 0.08f, -r * 0.04f)`) и широкая довольная улыбка Давида
 *    (`quadraticTo(0f, r * 0.30f, ...)`), прорисованная `#78350F` (`Stroke(width = r * 0.065f, cap = StrokeCap.Round)`).
 */
private fun DrawScope.drawEmbossedFrogEmblem(
    paths: SplashReusablePaths,
    r: Float
) {
    val highlightColor = Color(0xFFFEF08A).copy(alpha = 0.7f)
    val shadowStampColor = Color(0xFF78350F)
    val mainStampColor = Color(0xFF92400E)
    val innerStampColor = Color(0xFFB45309)

    val highlightOffset = Offset(-r * 0.02f, -r * 0.02f)
    val shadowOffset = Offset(r * 0.022f, r * 0.028f)

    val leftEyeCenter = Offset(-r * 0.24f, -r * 0.22f)
    val rightEyeCenter = Offset(r * 0.24f, -r * 0.22f)
    val eyeBumpRadius = r * 0.20f

    val headTopLeft = Offset(-r * 0.48f, -r * 0.20f)
    val headSize = Size(r * 0.96f, r * 0.62f)

    // 1) Нижняя тень барельефа (#78350F)
    drawCircle(
        color = shadowStampColor.copy(alpha = 0.75f),
        radius = eyeBumpRadius,
        center = leftEyeCenter + shadowOffset
    )
    drawCircle(
        color = shadowStampColor.copy(alpha = 0.75f),
        radius = eyeBumpRadius,
        center = rightEyeCenter + shadowOffset
    )
    drawOval(
        color = shadowStampColor.copy(alpha = 0.75f),
        topLeft = headTopLeft + shadowOffset,
        size = headSize
    )

    // 2) Светло-золотой блик барельефа со сдвигом (-r * 0.02f, -r * 0.02f)
    drawCircle(
        color = highlightColor,
        radius = eyeBumpRadius,
        center = leftEyeCenter + highlightOffset
    )
    drawCircle(
        color = highlightColor,
        radius = eyeBumpRadius,
        center = rightEyeCenter + highlightOffset
    )
    drawOval(
        color = highlightColor,
        topLeft = headTopLeft + highlightOffset,
        size = headSize
    )

    // 3) Основной штамп головы жабы (#92400E и внутренний объем #B45309)
    drawCircle(
        color = mainStampColor,
        radius = eyeBumpRadius,
        center = leftEyeCenter
    )
    drawCircle(
        color = mainStampColor,
        radius = eyeBumpRadius,
        center = rightEyeCenter
    )
    drawOval(
        color = mainStampColor,
        topLeft = headTopLeft,
        size = headSize
    )
    drawOval(
        color = innerStampColor,
        topLeft = Offset(-r * 0.43f, -r * 0.16f),
        size = Size(r * 0.86f, r * 0.54f)
    )

    // 4) Внутри глазниц — светло-золотые радужки (#FDE047, radius = r * 0.12f) и темные чеканные зрачки (#78350F, radius = r * 0.07f)
    val irisRadius = r * 0.12f
    val pupilRadius = r * 0.07f
    drawCircle(
        color = Color(0xFFFDE047),
        radius = irisRadius,
        center = leftEyeCenter
    )
    drawCircle(
        color = Color(0xFFFDE047),
        radius = irisRadius,
        center = rightEyeCenter
    )
    drawCircle(
        color = shadowStampColor,
        radius = pupilRadius,
        center = leftEyeCenter
    )
    drawCircle(
        color = shadowStampColor,
        radius = pupilRadius,
        center = rightEyeCenter
    )

    // 5) Две ноздри по центру (Offset(-r * 0.08f, -r * 0.04f) и Offset(r * 0.08f, -r * 0.04f))
    val nostrilRadius = (r * 0.035f).coerceAtLeast(1f)
    drawCircle(
        color = shadowStampColor,
        radius = nostrilRadius,
        center = Offset(-r * 0.08f, -r * 0.04f)
    )
    drawCircle(
        color = shadowStampColor,
        radius = nostrilRadius,
        center = Offset(r * 0.08f, -r * 0.04f)
    )

    // 6) Широкая довольная улыбка Давида (quadraticTo(0f, r * 0.30f, ...))
    val smilePath = paths.embossedSmilePath
    smilePath.reset()
    smilePath.moveTo(-r * 0.30f, r * 0.12f)
    smilePath.quadraticTo(0f, r * 0.30f, r * 0.30f, r * 0.12f)

    // Светлая подложка фаски под линией улыбки
    withTransform({
        translate(left = 0f, top = r * 0.02f)
    }) {
        drawPath(
            path = smilePath,
            color = highlightColor,
            style = Stroke(width = (r * 0.055f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
        )
    }

    drawPath(
        path = smilePath,
        color = shadowStampColor,
        style = Stroke(width = (r * 0.065f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
    )
}

/**
 * Световые дуги и золотые искры при подбрасывании монетки вверх за пределы экрана в Фазе 1
 * (рисуются чистыми примитивами без создания Brush).
 */
private fun DrawScope.drawUpwardTossArcs(
    centerX: Float,
    coinY: Float,
    mouthY: Float,
    coinRadius: Float,
    progress: Float
) {
    val alpha = sin(progress * PI.toFloat()).coerceIn(0f, 1f)
    if (alpha <= 0.02f) return

    val trailTop = (coinY + coinRadius * 0.6f).coerceAtLeast(0f)
    val trailBottom = lerpFloat(mouthY, (coinY + coinRadius * 3.2f).coerceAtLeast(0f), progress * 0.65f)
    if (trailBottom > trailTop + 4f) {
        drawLine(
            color = Color(0xFFFDE047).copy(alpha = 0.26f * alpha),
            start = Offset(centerX, trailTop),
            end = Offset(centerX, trailBottom),
            strokeWidth = coinRadius * 0.48f,
            cap = StrokeCap.Round
        )
    }

    val stepPx = 6.dp.toPx() * progress
    for (idx in 0 until 4) {
        val normX = when (idx) {
            0 -> -1.25f
            1 -> 1.30f
            2 -> -0.85f
            else -> 0.95f
        }
        val normY = when (idx) {
            0 -> 0.4f
            1 -> 0.8f
            2 -> 1.6f
            else -> 1.9f
        }
        val sparkY = coinY + coinRadius * normY + idx * stepPx
        if (sparkY >= 0f) {
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = alpha * (0.85f - idx * 0.15f)),
                radius = (3.5f - idx * 0.5f).dp.toPx(),
                center = Offset(centerX + coinRadius * normX, sparkY)
            )
        }
    }
}

/**
 * Вертикальный золотой световой шлейф и скоростные линии позади падающей монетки
 * (рисуются чистыми примитивами без создания Brush).
 */
private fun DrawScope.drawSpeedTrails(
    centerX: Float,
    coinY: Float,
    originTopY: Float,
    coinRadius: Float,
    intensity: Float
) {
    val trailStartY = originTopY.coerceAtMost(coinY - coinRadius * 0.4f)
    val trailEndY = coinY
    val trailHeight = (trailEndY - trailStartY).coerceAtLeast(4f)
    if (trailEndY <= 0f) return

    for (config in SpeedLineConfigs) {
        val offsetX = coinRadius * config.first
        val lengthFactor = config.second
        val topDelayFactor = config.third
        val lineTop = trailStartY + trailHeight * topDelayFactor
        val lineBottom = (lineTop + trailHeight * lengthFactor).coerceAtMost(coinY - coinRadius * 0.15f)
        if (lineBottom > lineTop + 4f) {
            drawLine(
                color = Color(0xFFFEF08A).copy(alpha = 0.65f * intensity),
                start = Offset(centerX + offsetX, lineTop),
                end = Offset(centerX + offsetX, lineBottom),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction.coerceIn(0f, 1f)
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun SplashDemoPreview() {
    GrowthChartSplashScreen(
        onSplashFinished = {}
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B1120)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF111C2D))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF10B981).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "DAVID FINANCE",
                            color = Color(0xFF34D399),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "1 480 500 ₽",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        BrandFrogAvatar(size = 32.dp)
                    }
                }
            }
        }
    }
}
