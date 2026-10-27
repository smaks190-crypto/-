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
private const val PHASE_1_WINDUP_END = 0.09f // Подэтап 1А (0..~130 мс): Живой замах/приседание жабы с монеткой во рту
private const val PHASE_1_END = 0.40f        // Подэтап 1Б (~130..568 мс): Выстрел монетки вверх за край экрана с 2 вертикальными 3D-оборотами
// Фаза 2 (0.40f..1.0f): Стремительное падение монетки сверху с ускорением навылет за нижний край экрана

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
 * Кэш всех градиентных кистей [Brush] и переиспользуемых [Path].
 * Внутри блока `Canvas { ... }` и всех функций отрисовки не создается ни одного нового объекта `Brush`.
 */
private class SplashCachedBrushes {
    val curtainPath = Path()
    val topEdgePath = Path()
    val mouthPath = Path()
    val smileLinePath = Path()
    val shinePath = Path()
    val crownPath = Path()
    val rubleBowlPath = Path()

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

        // Ровно в момент t = 0.09f (~130 мс от старта таймлайна, когда жаба завершает замах и выстреливает монетку вверх)
        // синхронно включаем звон монетки
        launch {
            delay(130L)
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
        val frogY = h * 0.56f
        val frogBaseRadius = (minOf(w, h) * 0.17f).coerceAtLeast(36f)
        val mouthOriginY = frogY + frogBaseRadius * 0.34f
        val baseCoinRadius = (minOf(w, h) * 0.088f).coerceAtLeast(24f)
        val auraRadius = (minOf(w, h) * 0.52f).coerceAtLeast(10f)
        val maxDipDepth = 96.dp.toPx()

        // Инициализируем/кэшируем все Brush строго один раз для текущих размеров экрана
        cachedBrushes.ensureInitialized(
            frogBaseRadius = frogBaseRadius,
            baseCoinRadius = baseCoinRadius,
            auraRadius = auraRadius
        )

        // Прогресс Фазы 2 (0.40f..1.0f)
        val p2 = ((t - PHASE_1_END) / (1f - PHASE_1_END)).coerceIn(0f, 1f)

        val coinY: Float
        val coinSpinAngle: Float
        val coinScale: Float
        val coinGlowIntensity: Float

        val curtainTopY: Float
        val currentDipDepth: Float
        val revealActive: Boolean

        val tossScaleX: Float
        val tossScaleY: Float
        val windupSquatPushY: Float
        val flightUpProgress: Float

        if (t <= PHASE_1_WINDUP_END) {
            // --- ПОДЭТАП 1А (t от 0f до 0.09f, первые ~130 мс): Живой замах / приседание жабы с монеткой во рту ---
            val windupProgress = (t / PHASE_1_WINDUP_END).coerceIn(0f, 1f)
            val windupEased = FastOutSlowInEasing.transform(windupProgress)

            // Жаба плавно приседает, набирая пружинную инерцию для броска (scaleY: 1.0f -> 0.86f, scaleX: 1.0f -> 1.10f)
            tossScaleX = lerpFloat(1.0f, 1.10f, windupEased)
            tossScaleY = lerpFloat(1.0f, 0.86f, windupEased)
            windupSquatPushY = windupEased * (frogBaseRadius * 0.14f)
            flightUpProgress = 0f

            // Монетка сидит во рту жабы и опускается вместе с её ртом во время приседания
            coinY = mouthOriginY + windupSquatPushY * 1.15f
            coinSpinAngle = 0f
            coinScale = lerpFloat(0.86f, 0.90f, windupEased)
            coinGlowIntensity = lerpFloat(0.32f, 0.45f, windupEased)

            curtainTopY = 0f
            currentDipDepth = 0f
            revealActive = false
        } else if (t <= PHASE_1_END) {
            // --- ПОДЭТАП 1Б (t от 0.09f до PHASE_1_END): Выстрел монетки вверх за край экрана ---
            val rawFlight = ((t - PHASE_1_WINDUP_END) / (PHASE_1_END - PHASE_1_WINDUP_END)).coerceIn(0f, 1f)
            flightUpProgress = FastOutSlowInEasing.transform(rawFlight)

            // Жаба резко распрямляется вверх (scaleY: 0.86f -> 1.08f -> 1.0f, scaleX: 1.10f -> 0.95f -> 1.0f)
            if (rawFlight < 0.45f) {
                val k = FastOutSlowInEasing.transform(rawFlight / 0.45f)
                tossScaleX = lerpFloat(1.10f, 0.95f, k)
                tossScaleY = lerpFloat(0.86f, 1.08f, k)
                windupSquatPushY = lerpFloat(frogBaseRadius * 0.14f, -frogBaseRadius * 0.06f, k)
            } else {
                val k = LinearOutSlowInEasing.transform((rawFlight - 0.45f) / 0.55f)
                tossScaleX = lerpFloat(0.95f, 1.0f, k)
                tossScaleY = lerpFloat(1.08f, 1.0f, k)
                windupSquatPushY = lerpFloat(-frogBaseRadius * 0.06f, 0f, k)
            }

            val launchOriginY = mouthOriginY + frogBaseRadius * 0.14f * 1.15f
            val offscreenTopTargetY = -baseCoinRadius * 2.2f
            coinY = lerpFloat(launchOriginY, offscreenTopTargetY, flightUpProgress)

            // Монетка совершает 2 полных вертикальных 3D-оборота в воздухе
            coinSpinAngle = flightUpProgress * 4f * PI.toFloat()
            coinScale = lerpFloat(0.90f, 1.06f, flightUpProgress)
            coinGlowIntensity = (0.45f + rawFlight * 0.40f).coerceIn(0f, 1f)

            curtainTopY = 0f
            currentDipDepth = 0f
            revealActive = false
        } else {
            // --- ФАЗА 2: Падение сверху из-за экрана и синхронный захват темной шторки ---
            tossScaleX = 1.0f
            tossScaleY = 1.0f
            windupSquatPushY = 0f
            flightUpProgress = 1f

            val easedFall = CoinFallAndPullEasing.transform(p2)
            val fallStartY = -baseCoinRadius * 1.5f
            val fallEndY = h + maxDipDepth * 1.35f
            coinY = lerpFloat(fallStartY, fallEndY, easedFall)

            // В начале падения (p2 in 0f..0.18f) монетка завершает финальный полуоборот до 0f,
            // а далее тянет шторку вниз анфас с живым 3D-покачиванием, показывающим золотой гурт!
            coinSpinAngle = if (p2 <= 0.18f) {
                val finishTurn = FastOutSlowInEasing.transform((p2 / 0.18f).coerceIn(0f, 1f))
                lerpFloat(-PI.toFloat(), 0f, finishTurn)
            } else {
                sin((p2 - 0.18f) * PI.toFloat() * 3f) * 0.22f
            }

            if (coinY <= 0f) {
                curtainTopY = 0f
                currentDipDepth = 0f
                revealActive = false
                coinScale = 1.08f
                coinGlowIntensity = 0.75f
            } else {
                // Монетка пересекла Y = 0f и физически прогибает и тянет верхний край шторки вниз
                revealActive = true
                val dipRamp = (coinY / (maxDipDepth * 1.35f)).coerceIn(0f, 1f)
                val elasticWave = 1f + 0.10f * sin(p2 * PI.toFloat() * 3.5f)
                currentDipDepth = maxDipDepth * FastOutSlowInEasing.transform(dipRamp) * elasticWave

                // Нижняя точка прогиба шторки идет строго синхронно с центром монетки
                curtainTopY = (coinY - currentDipDepth).coerceAtLeast(0f)

                val impactPulse = sin(dipRamp * PI.toFloat()).coerceIn(0f, 1f)
                coinScale = 1.06f + 0.08f * impactPulse
                coinGlowIntensity = (0.85f - p2 * 0.45f).coerceIn(0.25f, 0.9f)
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
        val contactPushY = contactEased * (frogBaseRadius * 0.42f)

        val frogAlpha = if (contactProgress <= 0.30f) {
            1f
        } else {
            (1f - ((contactProgress - 0.30f) / 0.70f)).coerceIn(0f, 1f)
        }

        val eyeLookUpProgress = when {
            t <= PHASE_1_WINDUP_END -> {
                // Во время приседания взгляд чуть опускается к монетке во рту
                -0.18f * (t / PHASE_1_WINDUP_END).coerceIn(0f, 1f)
            }
            t <= PHASE_1_END -> {
                lerpFloat(-0.18f, 1f, flightUpProgress)
            }
            else -> {
                1f - contactEased * 0.5f
            }
        }

        val mouthOpenFactor = when {
            t <= PHASE_1_WINDUP_END -> {
                // Во время замаха жаба держит монетку во рту
                lerpFloat(0.38f, 0.52f, (t / PHASE_1_WINDUP_END).coerceIn(0f, 1f))
            }
            t <= PHASE_1_END -> {
                val rawFlight = ((t - PHASE_1_WINDUP_END) / (PHASE_1_END - PHASE_1_WINDUP_END)).coerceIn(0f, 1f)
                if (rawFlight < 0.28f) {
                    // Резкое раскрытие рта при выстреле монетки вверх
                    lerpFloat(0.52f, 1.0f, rawFlight / 0.28f)
                } else {
                    lerpFloat(1.0f, 0.35f, ((rawFlight - 0.28f) / 0.72f).coerceIn(0f, 1f))
                }
            }
            contactProgress > 0f -> {
                (0.35f + 0.35f * sin(contactProgress * PI.toFloat())).coerceIn(0.2f, 0.7f)
            }
            else -> 0.35f
        }.coerceIn(0.18f, 1f)

        // 1. РИСУЕМ ТЕМНУЮ ШТОРКУ И ЖАБУ (в Фазе 1 — напрямую без clipPath!)
        drawElasticCurtainWithFrog(
            brushes = cachedBrushes,
            centerX = centerX,
            curtainTopY = curtainTopY,
            dipDepth = currentDipDepth,
            revealActive = revealActive,
            frogCenter = Offset(centerX, frogY + windupSquatPushY + contactPushY),
            frogBaseRadius = frogBaseRadius,
            auraRadius = auraRadius,
            frogScaleX = tossScaleX * contactScaleX,
            frogScaleY = tossScaleY * contactScaleY,
            frogAlpha = frogAlpha,
            eyeLookYOffset = -eyeLookUpProgress,
            mouthOpenFactor = mouthOpenFactor
        )

        // 2. РИСУЕМ СВЕТО-ШЛЕЙФ И СКОРОСТНЫЕ ЛИНИИ МОНЕТКИ БЕЗ СОЗДАНИЯ BRUSH
        val effectiveCoinRadius = baseCoinRadius * coinScale
        if (t > PHASE_1_WINDUP_END && t <= PHASE_1_END) {
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

        // 3. РИСУЕМ ОБЪЕМНУЮ 3D-ЗОЛОТУЮ МОНЕТКУ С ВЕРТИКАЛЬНЫМ КУВЫРКОМ И РИФЛЕНЫМ ГУРТОМ
        if (coinY + effectiveCoinRadius * 2f >= 0f && coinY - effectiveCoinRadius * 2f <= h) {
            drawGoldenCoin(
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

/**
 * Отрисовка эластичной темной шторки (#080C14) и жабы:
 * - Когда `!revealActive` (вся Фаза 1 при взлете монетки и начало Фазы 2 до касания верха экрана),
 *   НЕ вызываем `clipPath(curtainPath)` вообще — рисуем `drawRect` и жабу напрямую без нагрузки на GPU stencil-буфер.
 * - Вызываем `clipPath(curtainPath)` ТОЛЬКО когда `revealActive == true`.
 */
private fun DrawScope.drawElasticCurtainWithFrog(
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

    val curtainPath = brushes.curtainPath
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
    val topEdgePath = brushes.topEdgePath
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
 * Отрисовка жабы с использованием кэшированных `frogHeadBrush`, `frogJawBrush`, `frogShadowBrush` и `Path` (0 аллокаций на кадр).
 */
private fun DrawScope.drawEmojiFrog(
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

        val mouthPath = brushes.mouthPath
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

        val smileLine = brushes.smileLinePath
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
 * Отрисовка объемной 3D-золотой монетки с вертикальным кувырком вокруг горизонтальной оси X,
 * массивным рифленым гуртом (9 насечек) и двухслойной 3D-штамповкой короны и знака `₽`
 * (0 аллокаций `Brush` внутри `onDraw`).
 */
private fun DrawScope.drawGoldenCoin(
    brushes: SplashCachedBrushes,
    center: Offset,
    baseRadius: Float,
    coinScale: Float,
    spinAngle: Float,
    glowIntensity: Float,
    timeProgress: Float
) {
    val r = baseRadius.coerceAtLeast(10f)
    val flipCos = cos(spinAngle)
    val flipSin = sin(spinAngle)

    // Монетка всегда сохраняет полную горизонтальную ширину (scaleX = 1f) и сжимается ТОЛЬКО по вертикали!
    val faceScaleY = abs(flipCos).coerceAtLeast(0.12f)
    val rimThickness = r * 0.34f // Толщина 3D-гурта монеты
    val rimOffsetY = flipSin * rimThickness // Вертикальный сдвиг задней стенки ребра

    withTransform({
        translate(left = center.x, top = center.y)
        scale(scaleX = coinScale, scaleY = coinScale, pivot = Offset.Zero)
    }) {
        // Внешнее золотое свечение
        drawCircle(
            brush = brushes.coinOuterGlowBrush,
            radius = r * 2.6f,
            center = Offset.Zero,
            alpha = (0.45f + 0.55f * glowIntensity).coerceIn(0f, 1f)
        )

        // Слой 1 — Массивный цилиндрический 3D-гурт (боковое золотое ребро из 7 промежуточных овалов)
        val steps = 6
        for (step in 0..steps) {
            val fraction = step.toFloat() / steps
            val sliceY = lerpFloat(rimOffsetY, -rimOffsetY * 0.25f, fraction)
            drawOval(
                brush = brushes.coin3dRimBrush,
                topLeft = Offset(-r, sliceY - r * faceScaleY),
                size = Size(r * 2f, r * 2f * faceScaleY)
            )
        }

        // Рифление ребра (9 вертикальных насечек гурта вдоль ширины монеты)
        if (abs(rimOffsetY) > 1.5f) {
            val notchStroke = (r * 0.055f).coerceAtLeast(1.5f)
            for (notch in -4..4) {
                val notchX = notch * (r * 0.20f)
                val normalizedX = (notchX / r).coerceIn(-0.95f, 0.95f)
                val curveY = sqrt((1f - normalizedX * normalizedX).coerceAtLeast(0f)) * r * faceScaleY
                val frontEdgeY = -rimOffsetY * 0.25f + if (rimOffsetY > 0f) curveY else -curveY
                val backEdgeY = rimOffsetY + if (rimOffsetY > 0f) curveY else -curveY
                drawLine(
                    color = if (notch % 2 == 0) {
                        Color(0xFF78350F).copy(alpha = 0.82f)
                    } else {
                        Color(0xFFFEF08A).copy(alpha = 0.65f)
                    },
                    start = Offset(notchX, frontEdgeY),
                    end = Offset(notchX, backEdgeY),
                    strokeWidth = notchStroke,
                    cap = StrokeCap.Round
                )
            }
        }

        // Слой 2 — Лицевая / оборотная грань монеты с 3D-фаской и рельефной эмблемой
        withTransform({
            translate(left = 0f, top = -rimOffsetY * 0.25f)
            scale(scaleX = 1f, scaleY = faceScaleY, pivot = Offset.Zero)
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
            val shinePath = brushes.shinePath
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

            // Двухслойная 3D-штамповка короны и знака рубля ₽ с верхним световым бликом
            if (abs(flipCos) > 0.20f) {
                val shadowColor = Color(0xFF78350F)
                val emblemColor = Color(0xFF92400E)
                val highlightColor = Color(0xFFFEF08A).copy(alpha = 0.45f)
                val reliefShiftY = r * 0.045f
                val highlightShiftY = -r * 0.022f

                val crownPath = brushes.crownPath
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

                val bowlPath = brushes.rubleBowlPath
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
        }
    }
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
