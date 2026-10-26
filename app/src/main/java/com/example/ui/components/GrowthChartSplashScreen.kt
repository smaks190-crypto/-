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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Статический флаг процесса: гарантирует, что стартовая анимация проигрывается ровно один раз за сессию
private var hasSplashPlayedInSession = false

private const val SPLASH_TOTAL_DURATION_MS = 1450
private const val PHASE_1_END = 520f / 1450f // 0..520 мс: Вылет монетки полностью за верхний край экрана
// 520..1450 мс: Падение монетки из-за верхнего края и захват/стягивание темной шторки вниз

private val CoinFallAndPullEasing = CubicBezierEasing(0.30f, 0.06f, 0.22f, 1.0f)

/**
 * Основной стартовый экран приложения с процедурной анимацией на Jetpack Compose Canvas:
 * 1. Фаза 1 (0..520 мс): Жаба 🐸 сидит на темном фоне (#080C14), делает пружинный squash & stretch
 *    и подбрасывает золотую монетку вертикально вверх ПОЛНОСТЬЮ за верхний край экрана (`-coinRadius * 2.2f`).
 * 2. Фаза 2 (520..1450 мс): Монетка стремительно падает сверху из-за верхнего края экрана (`-coinRadius * 1.5f` -> низ экрана).
 *    Пересекая `Y = 0f`, она физически захватывает верхний край темной шторки, прогибает её дугой Безье
 *    и стягивает вниз, открывая настоящий интерфейс `content()`.
 * 3. Жаба остается полностью видимой (`alpha = 1f`) внутри `clipPath(curtainPath)` и смотрит вверх,
 *    пока опускающаяся со шторкой монетка не дойдет до её головы, мягко сжимая и скрывая её шторкой.
 */
@Composable
fun GrowthChartSplashScreen(
    onSplashFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    var isSplashCompleted by remember { mutableStateOf(hasSplashPlayedInSession) }

    LaunchedEffect(Unit) {
        if (hasSplashPlayedInSession) {
            isSplashCompleted = true
            onSplashFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Слой 1 (Нижний): Реальный интерфейс приложения рендерится сразу под шторкой
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }

        // Слой 2 (Верхний): Canvas-шторка с жабой и падающей золотой монеткой (только если еще не проигрывалась)
        if (!isSplashCompleted && !hasSplashPlayedInSession) {
            FrogCoinCurtainOverlay(
                onSplashFinished = {
                    hasSplashPlayedInSession = true
                    isSplashCompleted = true
                    onSplashFinished()
                }
            )
        }
    }
}

/**
 * Перегрузка для вызова поверх корневого контейнера в `MainActivity.kt`.
 * Использует тот же единый флаг `hasSplashPlayedInSession`, исключая повторный запуск при рекомпозиции.
 */
@Composable
fun GrowthChartSplashScreen(
    isExiting: Boolean = false,
    onExitFinished: () -> Unit = {}
) {
    var isSplashCompleted by remember { mutableStateOf(hasSplashPlayedInSession) }

    LaunchedEffect(Unit) {
        if (hasSplashPlayedInSession) {
            isSplashCompleted = true
            onExitFinished()
        }
    }

    if (!isSplashCompleted && !hasSplashPlayedInSession) {
        FrogCoinCurtainOverlay(
            onSplashFinished = {
                hasSplashPlayedInSession = true
                isSplashCompleted = true
                onExitFinished()
            }
        )
    }
}

@Composable
private fun FrogCoinCurtainOverlay(
    onSplashFinished: () -> Unit
) {
    val masterTimeline = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (hasSplashPlayedInSession) {
            onSplashFinished()
            return@LaunchedEffect
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

    val t = masterTimeline.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val centerX = w / 2f
        val frogY = h * 0.56f
        val frogBaseRadius = (minOf(w, h) * 0.17f).coerceAtLeast(36f)
        val mouthOriginY = frogY + frogBaseRadius * 0.34f
        val baseCoinRadius = (minOf(w, h) * 0.088f).coerceAtLeast(24f)
        val maxDipDepth = 96.dp.toPx()

        // Фаза 1 (0..520 мс): Взлет монетки изо рта жабы полностью за верхний край экрана (-coinRadius * 2.2f)
        val p1 = (t / PHASE_1_END).coerceIn(0f, 1f)
        // Фаза 2 (520..1450 мс): Возвращение монетки сверху из-за экрана (-coinRadius * 1.5f) и стягивание шторки вниз
        val p2 = ((t - PHASE_1_END) / (1f - PHASE_1_END)).coerceIn(0f, 1f)

        val coinY: Float
        val coinFlipScaleX: Float
        val coinScale: Float
        val coinGlowIntensity: Float

        val curtainTopY: Float
        val currentDipDepth: Float
        val revealActive: Boolean

        if (t <= PHASE_1_END) {
            // --- ФАЗА 1: Вылет монетки вверх ПОЛНОСТЬЮ за верхнюю границу экрана ---
            val easedUp = FastOutSlowInEasing.transform(p1)
            val offscreenTopTargetY = -baseCoinRadius * 2.2f
            coinY = lerpFloat(mouthOriginY, offscreenTopTargetY, easedUp)

            // 2.5 оборота в воздухе по мере взлета за верхний край экрана
            val spinAngle = easedUp * 2.5f * PI.toFloat()
            coinFlipScaleX = cos(spinAngle)
            coinScale = lerpFloat(0.78f, 1.06f, easedUp)
            coinGlowIntensity = (p1 * 0.55f).coerceIn(0f, 1f)

            curtainTopY = 0f
            currentDipDepth = 0f
            revealActive = false
        } else {
            // --- ФАЗА 2: Падение сверху из-за экрана и синхронный захват темной шторки ---
            val easedFall = CoinFallAndPullEasing.transform(p2)
            val fallStartY = -baseCoinRadius * 1.5f
            val fallEndY = h * 1.15f + maxDipDepth
            coinY = lerpFloat(fallStartY, fallEndY, easedFall)

            // При падении монетка развернута анфас (с короной и символом ₽) и ярко сияет
            coinFlipScaleX = 1f

            if (coinY <= 0f) {
                // Монетка еще подлетает к верхней кромке экрана (Y: -coinRadius*1.5f .. 0f)
                curtainTopY = 0f
                currentDipDepth = 0f
                revealActive = false
                coinScale = 1.08f
                coinGlowIntensity = 0.75f
            } else {
                // Монетка пересекла Y = 0f и физически прогибает и тянет верхний край шторки вниз!
                revealActive = true
                // Плавное формирование воронки прогиба при входе монетки в шторку
                val dipRamp = (coinY / (maxDipDepth * 1.35f)).coerceIn(0f, 1f)
                val elasticWave = 1f + 0.10f * sin(p2 * PI.toFloat() * 3.5f)
                currentDipDepth = maxDipDepth * FastOutSlowInEasing.transform(dipRamp) * elasticWave

                // Нижняя точка прогиба шторки идет строго синхронно с центром монетки
                curtainTopY = (coinY - currentDipDepth).coerceAtLeast(0f)

                val impactPulse = sin((dipRamp * PI.toFloat())).coerceIn(0f, 1f)
                coinScale = 1.06f + 0.08f * impactPulse
                coinGlowIntensity = (0.85f - p2 * 0.45f).coerceIn(0.25f, 0.9f)
            }
        }

        val curtainDipBottomY = curtainTopY + currentDipDepth

        // --- Параметры жабы (НЕ исчезает рано, сидит внутри шторки и ждет монетку) ---
        val squashWindow = (t / (150f / SPLASH_TOTAL_DURATION_MS)).coerceIn(0f, 1f)
        val (tossScaleX, tossScaleY) = computeFrogSquashStretch(squashWindow)

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

        // Жаба полностью видима (alpha = 1f) все время взлета и начала падения шторки;
        // плавно растворяется только когда шторка уже проходит сквозь её тело
        val frogAlpha = if (contactProgress <= 0.30f) {
            1f
        } else {
            (1f - ((contactProgress - 0.30f) / 0.70f)).coerceIn(0f, 1f)
        }

        // Зрачки жабы следят за взлетающей монеткой вверх и остаются поднятыми в ожидании её падения
        val eyeLookUpProgress = if (t <= PHASE_1_END) {
            FastOutSlowInEasing.transform(p1)
        } else {
            1f - contactEased * 0.5f
        }

        val mouthOpenFactor = when {
            squashWindow < 0.35f -> squashWindow / 0.35f
            t <= PHASE_1_END -> (1f - ((p1 - 0.20f) / 0.55f).coerceIn(0f, 1f) * 0.65f)
            contactProgress > 0f -> (0.35f + 0.35f * sin(contactProgress * PI.toFloat())).coerceIn(0.2f, 0.7f)
            else -> 0.35f
        }.coerceIn(0.18f, 1f)

        // 1. РИСУЕМ ТЕМНУЮ ШТОРКУ И ЖАБУ ВНУТРИ clipPath(curtainPath)
        drawElasticCurtainWithFrog(
            centerX = centerX,
            curtainTopY = curtainTopY,
            dipDepth = currentDipDepth,
            revealActive = revealActive,
            frogCenter = Offset(centerX, frogY + contactPushY),
            frogBaseRadius = frogBaseRadius,
            frogScaleX = tossScaleX * contactScaleX,
            frogScaleY = tossScaleY * contactScaleY,
            frogAlpha = frogAlpha,
            eyeLookYOffset = -eyeLookUpProgress,
            mouthOpenFactor = mouthOpenFactor
        )

        // 2. РИСУЕМ СВЕТО-ШЛЕЙФ И СКОРОСТНЫЕ ЛИНИИ МОНЕТКИ
        val effectiveCoinRadius = baseCoinRadius * coinScale
        if (t <= PHASE_1_END) {
            drawUpwardTossArcs(
                centerX = centerX,
                coinY = coinY,
                mouthY = mouthOriginY,
                coinRadius = effectiveCoinRadius,
                progress = p1
            )
        } else {
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

        // 3. РИСУЕМ ЗОЛОТУЮ МОНЕТКУ
        if (coinY + effectiveCoinRadius * 2f >= 0f && coinY - effectiveCoinRadius * 2f <= h) {
            drawGoldenCoin(
                center = Offset(centerX, coinY),
                radius = effectiveCoinRadius,
                flipScaleX = coinFlipScaleX,
                glowIntensity = coinGlowIntensity,
                timeProgress = t
            )
        }
    }
}

/**
 * Вычисляет пружинный Squash & Stretch импульс жабы в первые 150 мс:
 * scaleY: 1.0f -> 0.88f -> 1.06f -> 1.0f
 * scaleX: 1.0f -> 1.08f -> 0.96f -> 1.0f
 */
private fun computeFrogSquashStretch(progress: Float): Pair<Float, Float> {
    val p = progress.coerceIn(0f, 1f)
    return when {
        p < 0.40f -> {
            val k = FastOutSlowInEasing.transform(p / 0.40f)
            Pair(
                lerpFloat(1.0f, 1.08f, k),
                lerpFloat(1.0f, 0.88f, k)
            )
        }
        p < 0.75f -> {
            val k = FastOutSlowInEasing.transform((p - 0.40f) / 0.35f)
            Pair(
                lerpFloat(1.08f, 0.96f, k),
                lerpFloat(0.88f, 1.06f, k)
            )
        }
        else -> {
            val k = LinearOutSlowInEasing.transform((p - 0.75f) / 0.25f)
            Pair(
                lerpFloat(0.96f, 1.0f, k),
                lerpFloat(1.06f, 1.0f, k)
            )
        }
    }
}

/**
 * Отрисовка эластичной темной шторки (#080C14) и самой жабы 🐸 строго внутри `clipPath(curtainPath)`.
 * Всё, что выше изогнутой кривой Безье, открывает `content()`, а всё, что ниже, содержит темный фон и жабу.
 */
private fun DrawScope.drawElasticCurtainWithFrog(
    centerX: Float,
    curtainTopY: Float,
    dipDepth: Float,
    revealActive: Boolean,
    frogCenter: Offset,
    frogBaseRadius: Float,
    frogScaleX: Float,
    frogScaleY: Float,
    frogAlpha: Float,
    eyeLookYOffset: Float,
    mouthOpenFactor: Float
) {
    val w = size.width
    val h = size.height
    if (curtainTopY >= h + 40f) return

    val dipBottomY = curtainTopY + dipDepth
    val shoulderSpan = (w * 0.38f).coerceAtLeast(80f)

    val curtainPath = Path().apply {
        if (!revealActive || (curtainTopY <= 0.5f && dipDepth <= 0.5f)) {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        } else {
            moveTo(0f, curtainTopY)
            lineTo((centerX - shoulderSpan).coerceAtLeast(0f), curtainTopY)
            cubicTo(
                centerX - shoulderSpan * 0.45f,
                curtainTopY,
                centerX - shoulderSpan * 0.28f,
                dipBottomY,
                centerX,
                dipBottomY
            )
            cubicTo(
                centerX + shoulderSpan * 0.28f,
                dipBottomY,
                centerX + shoulderSpan * 0.45f,
                curtainTopY,
                (centerX + shoulderSpan).coerceAtMost(w),
                curtainTopY
            )
            lineTo(w, curtainTopY)
            lineTo(w, h + 40f)
            lineTo(0f, h + 40f)
            close()
        }
    }

    // Обрезаем и темный фон шторки, и саму жабу по контуру шторки (clipPath)
    clipPath(curtainPath) {
        drawRect(
            color = Color(0xFF080C14),
            topLeft = Offset.Zero,
            size = Size(w, h)
        )

        if (frogAlpha > 0.01f) {
            val auraRadius = (minOf(w, h) * 0.52f).coerceAtLeast(10f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D2818).copy(alpha = 0.95f * frogAlpha),
                        Color(0xFF0A1E14).copy(alpha = 0.55f * frogAlpha),
                        Color(0xFF080C14).copy(alpha = 0f)
                    ),
                    center = frogCenter,
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = frogCenter
            )

            // Жаба рисуется внутри области шторки и остается на экране, пока шторка её не накроет
            drawEmojiFrog(
                center = frogCenter,
                baseRadius = frogBaseRadius,
                scaleX = frogScaleX,
                scaleY = frogScaleY,
                alpha = frogAlpha,
                eyeLookYOffset = eyeLookYOffset,
                mouthOpenFactor = mouthOpenFactor
            )
        }

        // Внутреннее свечение вдоль прогиба шторки под давлением золотой монетки
        if (revealActive && dipDepth > 1f) {
            val rimGlowRadius = (shoulderSpan * 0.85f).coerceAtLeast(12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFBBF24).copy(alpha = 0.26f),
                        Color(0xFF10B981).copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, dipBottomY),
                    radius = rimGlowRadius
                ),
                radius = rimGlowRadius,
                center = Offset(centerX, dipBottomY)
            )
        }
    }

    // Неоново-изумрудный и золотой кант вдоль изогнутой верхней границы шторки
    if (revealActive) {
        val topEdgePath = Path().apply {
            moveTo(0f, curtainTopY)
            lineTo((centerX - shoulderSpan).coerceAtLeast(0f), curtainTopY)
            cubicTo(
                centerX - shoulderSpan * 0.45f,
                curtainTopY,
                centerX - shoulderSpan * 0.28f,
                dipBottomY,
                centerX,
                dipBottomY
            )
            cubicTo(
                centerX + shoulderSpan * 0.28f,
                dipBottomY,
                centerX + shoulderSpan * 0.45f,
                curtainTopY,
                (centerX + shoulderSpan).coerceAtMost(w),
                curtainTopY
            )
            lineTo(w, curtainTopY)
        }

        drawPath(
            path = topEdgePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF10B981).copy(alpha = 0.25f),
                    Color(0xFF34D399).copy(alpha = 0.65f),
                    Color(0xFFFBBF24).copy(alpha = 0.90f),
                    Color(0xFF34D399).copy(alpha = 0.65f),
                    Color(0xFF10B981).copy(alpha = 0.25f)
                )
            ),
            style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawPath(
            path = topEdgePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF10B981).copy(alpha = 0.55f),
                    Color(0xFF6EE7B7),
                    Color(0xFFFEF08A),
                    Color(0xFF6EE7B7),
                    Color(0xFF10B981).copy(alpha = 0.55f)
                )
            ),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Отрисовка жабы в стиле классического эмодзи 🐸 в едином стиле с адаптивной иконкой.
 */
private fun DrawScope.drawEmojiFrog(
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
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.45f * alpha),
                    Color.Transparent
                ),
                center = Offset(0f, r * 0.22f),
                radius = shadowRadius
            ),
            radius = shadowRadius,
            center = Offset(0f, r * 0.22f)
        )

        val eyeBumpRadius = r * 0.46f
        val eyeBumpOffsetX = r * 0.48f
        val eyeBumpOffsetY = -r * 0.52f

        drawCircle(
            color = Color(0xFF15803D).copy(alpha = alpha),
            radius = eyeBumpRadius * 1.04f,
            center = Offset(-eyeBumpOffsetX, eyeBumpOffsetY)
        )
        drawCircle(
            color = Color(0xFF15803D).copy(alpha = alpha),
            radius = eyeBumpRadius * 1.04f,
            center = Offset(eyeBumpOffsetX, eyeBumpOffsetY)
        )
        drawOval(
            color = Color(0xFF15803D).copy(alpha = alpha),
            topLeft = Offset(-r * 1.05f, -r * 0.54f),
            size = Size(r * 2.10f, r * 1.38f)
        )

        val headBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF4ADE80).copy(alpha = alpha),
                Color(0xFF22C55E).copy(alpha = alpha),
                Color(0xFF16A34A).copy(alpha = alpha)
            ),
            startY = -r * 0.95f,
            endY = r * 0.78f
        )

        drawCircle(
            brush = headBrush,
            radius = eyeBumpRadius,
            center = Offset(-eyeBumpOffsetX, eyeBumpOffsetY)
        )
        drawCircle(
            brush = headBrush,
            radius = eyeBumpRadius,
            center = Offset(eyeBumpOffsetX, eyeBumpOffsetY)
        )
        drawOval(
            brush = headBrush,
            topLeft = Offset(-r * 1.02f, -r * 0.52f),
            size = Size(r * 2.04f, r * 1.30f)
        )

        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF86EFAC).copy(alpha = alpha),
                    Color(0xFF4ADE80).copy(alpha = alpha)
                ),
                startY = -r * 0.08f,
                endY = r * 0.74f
            ),
            topLeft = Offset(-r * 0.92f, -r * 0.06f),
            size = Size(r * 1.84f, r * 0.80f)
        )

        drawOval(
            color = Color(0xFFBBF7D0).copy(alpha = 0.35f * alpha),
            topLeft = Offset(-r * 0.42f, -r * 0.38f),
            size = Size(r * 0.84f, r * 0.22f)
        )

        drawOval(
            color = Color(0xFFFB7185).copy(alpha = 0.30f * alpha),
            topLeft = Offset(-r * 0.82f, r * 0.10f),
            size = Size(r * 0.32f, r * 0.20f)
        )
        drawOval(
            color = Color(0xFFFB7185).copy(alpha = 0.30f * alpha),
            topLeft = Offset(r * 0.50f, r * 0.10f),
            size = Size(r * 0.32f, r * 0.20f)
        )

        drawOval(
            color = Color(0xFF064E3B).copy(alpha = alpha),
            topLeft = Offset(-r * 0.18f, -r * 0.18f),
            size = Size(r * 0.10f, r * 0.14f)
        )
        drawOval(
            color = Color(0xFF064E3B).copy(alpha = alpha),
            topLeft = Offset(r * 0.08f, -r * 0.18f),
            size = Size(r * 0.10f, r * 0.14f)
        )

        val eyeCenters = listOf(
            Offset(-eyeBumpOffsetX, eyeBumpOffsetY + r * 0.02f),
            Offset(eyeBumpOffsetX, eyeBumpOffsetY + r * 0.02f)
        )
        val scleraRadius = eyeBumpRadius * 0.62f
        val pupilRadius = scleraRadius * 0.64f
        val pupilShiftY = eyeLookYOffset * (scleraRadius * 0.28f)

        eyeCenters.forEach { eyeCenter ->
            drawCircle(
                color = Color(0xFF15803D).copy(alpha = alpha),
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

        val mouthPath = Path().apply {
            moveTo(-mouthWidth / 2f, mouthTopY)
            quadraticTo(0f, mouthTopY - r * 0.10f, mouthWidth / 2f, mouthTopY)
            quadraticTo(0f, mouthTopY + mouthHeight, -mouthWidth / 2f, mouthTopY)
            close()
        }
        drawPath(
            path = mouthPath,
            color = Color(0xFF064E3B).copy(alpha = alpha)
        )

        if (mouthOpenFactor > 0.2f) {
            drawOval(
                color = Color(0xFFF43F5E).copy(alpha = 0.75f * alpha),
                topLeft = Offset(-r * 0.24f, mouthTopY + mouthHeight * 0.18f),
                size = Size(r * 0.48f, mouthHeight * 0.36f)
            )
        }

        val smileLine = Path().apply {
            moveTo(-mouthWidth * 0.54f, mouthTopY)
            quadraticTo(0f, mouthTopY - r * 0.10f, mouthWidth * 0.54f, mouthTopY)
        }
        drawPath(
            path = smileLine,
            color = Color(0xFF064E3B).copy(alpha = alpha),
            style = Stroke(width = (r * 0.055f).coerceAtLeast(2f), cap = StrokeCap.Round)
        )
    }
}

/**
 * Отрисовка золотой монетки с 3D-вращением, объемным гуртом, бликами и короной / символом ₽ в центре.
 */
private fun DrawScope.drawGoldenCoin(
    center: Offset,
    radius: Float,
    flipScaleX: Float,
    glowIntensity: Float,
    timeProgress: Float
) {
    val r = radius.coerceAtLeast(10f)

    val baseAuraRadius = (r * (1.8f + 1.4f * glowIntensity)).coerceAtLeast(4f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFEF08A).copy(alpha = (0.38f + 0.45f * glowIntensity).coerceIn(0f, 1f)),
                Color(0xFFF59E0B).copy(alpha = (0.18f + 0.28f * glowIntensity).coerceIn(0f, 1f)),
                Color.Transparent
            ),
            center = center,
            radius = baseAuraRadius
        ),
        radius = baseAuraRadius,
        center = center
    )

    val absFlip = abs(flipScaleX).coerceIn(0.14f, 1f)
    val edgeThickness = (r * 0.22f * (1f - absFlip)).coerceAtLeast(2.dp.toPx())

    withTransform({
        translate(left = center.x, top = center.y)
    }) {
        drawOval(
            color = Color(0xFFB45309),
            topLeft = Offset(-r * absFlip - edgeThickness, -r),
            size = Size(r * 2f * absFlip + edgeThickness * 2f, r * 2f)
        )

        withTransform({
            scale(scaleX = absFlip, scaleY = 1f, pivot = Offset.Zero)
        }) {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFDE047),
                        Color(0xFFF59E0B),
                        Color(0xFFD97706)
                    ),
                    start = Offset(-r, -r),
                    end = Offset(r, r)
                ),
                radius = r,
                center = Offset.Zero
            )

            drawCircle(
                color = Color(0xFFD97706),
                radius = r * 0.80f,
                center = Offset.Zero
            )

            val coreRadius = (r * 0.74f).coerceAtLeast(2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFEF08A),
                        Color(0xFFFBBF24),
                        Color(0xFFF59E0B)
                    ),
                    center = Offset(-r * 0.22f, -r * 0.22f),
                    radius = coreRadius * 1.3f
                ),
                radius = coreRadius,
                center = Offset.Zero
            )

            val shineShift = sin(timeProgress * PI.toFloat() * 4f) * r * 0.12f
            val shinePath = Path().apply {
                moveTo(-r * 0.48f + shineShift, -r * 0.56f)
                lineTo(-r * 0.16f + shineShift, -r * 0.68f)
                lineTo(r * 0.48f + shineShift, r * 0.52f)
                lineTo(r * 0.16f + shineShift, r * 0.66f)
                close()
            }
            drawPath(
                path = shinePath,
                color = Color(0xFFFEF08A).copy(alpha = 0.45f)
            )

            if (absFlip > 0.28f) {
                val emblemColor = Color(0xFF92400E)

                val crownPath = Path().apply {
                    val cw = r * 0.42f
                    val topY = -r * 0.52f
                    val baseY = -r * 0.30f
                    moveTo(-cw * 0.75f, baseY)
                    lineTo(-cw * 0.90f, topY + r * 0.04f)
                    lineTo(-cw * 0.38f, baseY - r * 0.07f)
                    lineTo(0f, topY - r * 0.03f)
                    lineTo(cw * 0.38f, baseY - r * 0.07f)
                    lineTo(cw * 0.90f, topY + r * 0.04f)
                    lineTo(cw * 0.75f, baseY)
                    close()
                }
                drawPath(path = crownPath, color = emblemColor)

                val strokeW = (r * 0.13f).coerceAtLeast(2f)
                val stemX = -r * 0.14f
                val topR = -r * 0.16f
                val bottomR = r * 0.48f
                val midR = r * 0.16f

                drawLine(
                    color = emblemColor,
                    start = Offset(stemX, topR),
                    end = Offset(stemX, bottomR),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                val bowlPath = Path().apply {
                    moveTo(stemX, topR)
                    lineTo(r * 0.10f, topR)
                    arcTo(
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
                    lineTo(stemX - r * 0.12f, midR)
                }
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
            }
        }
    }
}

/**
 * Световые дуги и золотые искры при подбрасывании монетки вверх за пределы экрана в Фазе 1.
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

    val trailTop = (coinY + coinRadius * 0.5f).coerceAtLeast(0f)
    val trailBottom = lerpFloat(mouthY, (coinY + coinRadius * 3.5f).coerceAtLeast(0f), progress * 0.65f)
    if (trailBottom <= trailTop + 4f) return

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFDE047).copy(alpha = 0.42f * alpha),
                Color(0xFF10B981).copy(alpha = 0.16f * alpha),
                Color.Transparent
            ),
            startY = trailTop,
            endY = trailBottom
        ),
        topLeft = Offset(centerX - coinRadius * 0.45f, trailTop),
        size = Size(coinRadius * 0.90f, (trailBottom - trailTop).coerceAtLeast(2f))
    )

    val sparkOffsets = listOf(
        Offset(-coinRadius * 1.25f, coinRadius * 0.4f),
        Offset(coinRadius * 1.30f, coinRadius * 0.8f),
        Offset(-coinRadius * 0.85f, coinRadius * 1.6f),
        Offset(coinRadius * 0.95f, coinRadius * 1.9f)
    )
    sparkOffsets.forEachIndexed { idx, offset ->
        val sparkY = coinY + offset.y + (idx * 6.dp.toPx() * progress)
        if (sparkY >= 0f) {
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = alpha * (0.85f - idx * 0.15f)),
                radius = (3.5f - idx * 0.5f).dp.toPx(),
                center = Offset(centerX + offset.x, sparkY)
            )
        }
    }
}

/**
 * Вертикальный золотой световой шлейф и скоростные линии позади падающей монетки.
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

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFF59E0B).copy(alpha = 0.22f * intensity),
                Color(0xFFFEF08A).copy(alpha = 0.55f * intensity)
            ),
            startY = trailStartY,
            endY = trailEndY
        ),
        topLeft = Offset(centerX - coinRadius * 0.68f, trailStartY),
        size = Size(coinRadius * 1.36f, trailHeight)
    )

    val speedLineConfigs = listOf(
        Triple(-coinRadius * 1.15f, 0.55f, 0.35f),
        Triple(-coinRadius * 0.58f, 0.82f, 0.15f),
        Triple(0f, 0.95f, 0.05f),
        Triple(coinRadius * 0.58f, 0.78f, 0.18f),
        Triple(coinRadius * 1.15f, 0.50f, 0.38f)
    )

    speedLineConfigs.forEach { (offsetX, lengthFactor, topDelayFactor) ->
        val lineTop = trailStartY + trailHeight * topDelayFactor
        val lineBottom = (lineTop + trailHeight * lengthFactor).coerceAtMost(coinY - coinRadius * 0.15f)
        if (lineBottom > lineTop + 4f) {
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFFEF08A).copy(alpha = 0.80f * intensity)
                    ),
                    startY = lineTop,
                    endY = lineBottom
                ),
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

/**
 * Демо-превью стартового экрана `GrowthChartSplashScreen`:
 * показывает, как падающая из-за верхнего края золотая монетка стягивает темную шторку с жабой вниз
 * и открывает интерфейс приложения с карточкой «Привет, Жабик! Баланс: 1 250 ₽».
 */
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
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF064E3B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🐸", fontSize = 28.sp)
                    }
                    Column {
                        Text(
                            text = "Привет, Жабик!",
                            color = Color(0xFF6EE7B7),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Баланс: 1 250 ₽",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
