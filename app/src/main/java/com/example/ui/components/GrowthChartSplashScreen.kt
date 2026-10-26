package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Amber400
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val SPLASH_TOTAL_DURATION_MS = 1650
private const val PHASE_1_END = 520f / 1650f   // 0..520 ms: Frog squash & stretch + Coin toss to apexY
private const val PHASE_2_END = 880f / 1650f   // 520..880 ms: Frog fades, Coin flashes at apex & dives to catchY
private const val PHASE_3_END = 1500f / 1650f  // 880..1500 ms: Coin bends & pulls dark curtain down with Bezier dip
// 1500..1650 ms: Curtain & coin clear bottom of screen -> onSplashFinished()

private val CurtainPullEasing = CubicBezierEasing(0.32f, 0.08f, 0.24f, 1.0f)

/**
 * Основной стартовый экран приложения с процедурной анимацией на Jetpack Compose Canvas:
 * 1. Жаба 🐸 с пружинным импульсом (squash & stretch) подбрасывает золотую монетку из открытого рта вверх.
 * 2. В верхней точке монетка вспыхивает золотым сиянием, разворачивается анфас (с короной и символом ₽) и стремительно пикирует вниз.
 * 3. Падающая монетка физически прогибает и стягивает темную шторку (#080C14) вниз по кривой Безье, открывая реальный интерфейс `content()`.
 */
@Composable
fun GrowthChartSplashScreen(
    onSplashFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    var isSplashCompleted by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Слой 1 (Нижний): Реальный интерфейс приложения рендерится сразу под шторкой
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }

        // Слой 2 (Верхний): Canvas-шторка с жабой и падающей золотой монеткой
        if (!isSplashCompleted) {
            FrogCoinCurtainOverlay(
                onSplashFinished = {
                    if (!isSplashCompleted) {
                        isSplashCompleted = true
                        onSplashFinished()
                    }
                }
            )
        }
    }
}

/**
 * Перегрузка для обратной совместимости с вызовами поверх существующего контейнера Box в MainActivity.
 * Запускает ту же 4-фазную процедурную анимацию жабы и монетки, стягивающей темную шторку вниз.
 */
@Composable
fun GrowthChartSplashScreen(
    isExiting: Boolean = false,
    onExitFinished: () -> Unit = {}
) {
    var isSplashCompleted by remember { mutableStateOf(false) }

    if (!isSplashCompleted) {
        FrogCoinCurtainOverlay(
            onSplashFinished = {
                if (!isSplashCompleted) {
                    isSplashCompleted = true
                    onExitFinished()
                }
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
        masterTimeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SPLASH_TOTAL_DURATION_MS,
                easing = LinearEasing
            )
        )
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
        val apexY = h * 0.18f
        val catchY = h * 0.28f
        val endCurtainY = h * 1.15f
        val maxDipDepth = 96.dp.toPx()

        // --- Расчет параметров по фазам ---

        // Фаза 1 (0..520 мс): Прыжок жабы и подбрасывание монетки вверх
        val p1 = (t / PHASE_1_END).coerceIn(0f, 1f)
        // Фаза 2 (520..880 мс): Вспышка в верхней точке и пикирование монетки вниз
        val p2 = ((t - PHASE_1_END) / (PHASE_2_END - PHASE_1_END)).coerceIn(0f, 1f)
        // Фаза 3..4 (880..1650 мс): Прогиб и стягивание шторки вниз до полного ухода за экран
        val p3 = ((t - PHASE_2_END) / (1f - PHASE_2_END)).coerceIn(0f, 1f)

        // Вычисляем положение верхнего края шторки и глубину прогиба под монеткой
        val curtainPullProgress = CurtainPullEasing.transform(p3)
        val curtainTopY = if (t < PHASE_2_END) {
            0f
        } else {
            lerpFloat(0f, endCurtainY, curtainPullProgress)
        }

        // Эластичный прогиб шторки: быстро нарастает при захвате и пружинит по мере движения вниз
        val dipEnvelope = if (t < PHASE_2_END) {
            0f
        } else {
            val rampIn = (p3 / 0.16f).coerceIn(0f, 1f)
            val tailFade = (1f - ((p3 - 0.85f) / 0.15f).coerceIn(0f, 1f))
            val oscillation = 1f + 0.12f * sin(p3 * PI.toFloat() * 3.5f)
            FastOutSlowInEasing.transform(rampIn) * oscillation * tailFade
        }
        val currentDipDepth = maxDipDepth * dipEnvelope

        // 1. РИСУЕМ ЭЛАСТИЧНУЮ ТЕМНУЮ ШТОРКУ (#080C14) С ИЗУМРУДНЫМ СВЕЧЕНИЕМ И НЕОНОВЫМ КАНТОМ
        drawElasticCurtain(
            centerX = centerX,
            curtainTopY = curtainTopY,
            dipDepth = currentDipDepth,
            revealActive = t >= PHASE_2_END,
            frogCenterY = frogY,
            frogAuraAlpha = if (t < PHASE_1_END) 1f else (1f - p2).coerceIn(0f, 1f)
        )

        // 2. РИСУЕМ ЖАБУ 🐸 (Фаза 1 и плавное растворение в Фазе 2)
        val frogAlpha = when {
            t <= PHASE_1_END -> 1f
            t <= PHASE_2_END -> (1f - FastOutSlowInEasing.transform(p2)).coerceIn(0f, 1f)
            else -> 0f
        }

        if (frogAlpha > 0.01f) {
            // Squash & Stretch в первые 150 мс (t in 0 .. 150/1650)
            val squashWindow = (t / (150f / SPLASH_TOTAL_DURATION_MS)).coerceIn(0f, 1f)
            val (frogScaleX, frogScaleY) = computeFrogSquashStretch(squashWindow)

            // В Фазе 2 жаба плавно уменьшается (1.0 -> 0.82)
            val exitScale = if (t <= PHASE_1_END) 1f else lerpFloat(1f, 0.82f, FastOutSlowInEasing.transform(p2))

            // Зрачки жабы следят за взлетающей монеткой вверх (-1f = взгляд вверх, 0f = по центру)
            val eyeLookUpProgress = FastOutSlowInEasing.transform(p1)

            // Открытие рта при выбросе монетки
            val mouthOpenFactor = when {
                squashWindow < 0.35f -> squashWindow / 0.35f
                p1 < 0.65f -> 1f - ((p1 - 0.2f) / 0.45f).coerceIn(0f, 1f) * 0.65f
                else -> 0.35f * (1f - p2)
            }.coerceIn(0.15f, 1f)

            drawEmojiFrog(
                center = Offset(centerX, frogY),
                baseRadius = frogBaseRadius,
                scaleX = frogScaleX * exitScale,
                scaleY = frogScaleY * exitScale,
                alpha = frogAlpha,
                eyeLookYOffset = -eyeLookUpProgress,
                mouthOpenFactor = mouthOpenFactor
            )
        }

        // 3. ВЫЧИСЛЯЕМ КООРДИНАТУ И 3D-ВРАЩЕНИЕ ЗОЛОТОЙ МОНЕТКИ
        val baseCoinRadius = (minOf(w, h) * 0.088f).coerceAtLeast(24f)

        val coinY: Float
        val coinFlipScaleX: Float
        val coinScale: Float
        val apexFlashIntensity: Float

        when {
            t <= PHASE_1_END -> {
                // Подъем от рта жабы до верхней точки apexY с физическим замедлением
                val easedUp = FastOutSlowInEasing.transform(p1)
                coinY = lerpFloat(mouthOriginY, apexY, easedUp)
                // 2 полных оборота в воздухе с выходом в анфас (cos(2*PI) = 1f) к вершине
                val spinAngle = easedUp * 2f * PI.toFloat()
                coinFlipScaleX = cos(spinAngle)
                coinScale = lerpFloat(0.76f, 1.08f, easedUp)
                apexFlashIntensity = ((p1 - 0.82f) / 0.18f).coerceIn(0f, 1f) * 0.6f
            }
            t <= PHASE_2_END -> {
                // В первые 25% Фазы 2 монетка вспыхивает в верхней точке, затем стремительно пикирует к catchY
                val holdRatio = 0.22f
                if (p2 < holdRatio) {
                    val localHold = p2 / holdRatio
                    coinY = apexY - sin(localHold * PI.toFloat()) * 6.dp.toPx()
                    coinFlipScaleX = 1f
                    coinScale = lerpFloat(1.08f, 1.18f, sin(localHold * PI.toFloat()))
                    apexFlashIntensity = 0.6f + 0.4f * sin(localHold * PI.toFloat())
                } else {
                    val diveProgress = ((p2 - holdRatio) / (1f - holdRatio)).coerceIn(0f, 1f)
                    val easedDive = FastOutLinearInEasing.transform(diveProgress)
                    coinY = lerpFloat(apexY, catchY, easedDive)
                    coinFlipScaleX = 1f
                    coinScale = lerpFloat(1.14f, 1.04f, easedDive)
                    apexFlashIntensity = (1f - diveProgress * 1.4f).coerceIn(0f, 1f)
                }
            }
            else -> {
                // Фаза 3..4: Монетка сидит в самой нижней точке прогиба шторки и тянет её вниз
                val targetRestY = curtainTopY + currentDipDepth
                val catchToCurtainBlend = (p3 / 0.14f).coerceIn(0f, 1f)
                coinY = lerpFloat(catchY, targetRestY, FastOutSlowInEasing.transform(catchToCurtainBlend))
                coinFlipScaleX = 1f
                // Легкое сплющивание в момент удара о шторку и упругое восстановление
                val impactPulse = sin((p3 / 0.22f).coerceIn(0f, 1f) * PI.toFloat())
                coinScale = 1.04f + 0.08f * impactPulse
                apexFlashIntensity = (1f - (p3 / 0.25f)).coerceIn(0f, 1f) * 0.45f
            }
        }

        // 4. РИСУЕМ СКОРОСТНЫЕ ЛИНИИ И ЗОЛОТОЙ ШЛЕЙФ (Фаза 1 взлет + Фаза 2..3 пикирование и тяга)
        if (t <= PHASE_1_END) {
            drawUpwardTossArcs(
                centerX = centerX,
                coinY = coinY,
                mouthY = mouthOriginY,
                coinRadius = baseCoinRadius * coinScale,
                progress = p1
            )
        } else {
            val trailStrength = when {
                t <= PHASE_2_END -> ((p2 - 0.15f) / 0.85f).coerceIn(0f, 1f)
                else -> (1f - (p3 / 0.78f)).coerceIn(0f, 1f)
            }
            if (trailStrength > 0.01f) {
                drawSpeedTrails(
                    centerX = centerX,
                    coinY = coinY,
                    originTopY = apexY,
                    coinRadius = baseCoinRadius * coinScale,
                    intensity = trailStrength
                )
            }
        }

        // 5. РИСУЕМ ЗОЛОТУЮ МОНЕТКУ С КОРОНОЙ И СИМВОЛОМ ₽
        if (coinY - baseCoinRadius * 2f < h) {
            drawGoldenCoin(
                center = Offset(centerX, coinY),
                radius = baseCoinRadius * coinScale,
                flipScaleX = coinFlipScaleX,
                glowIntensity = apexFlashIntensity,
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
 * Отрисовка эластичной темной шторки (#080C14), которую падающая монетка физически прогибает
 * и стягивает вниз по кривым Безье (`cubicTo`), открывая интерфейс приложения сверху.
 */
private fun DrawScope.drawElasticCurtain(
    centerX: Float,
    curtainTopY: Float,
    dipDepth: Float,
    revealActive: Boolean,
    frogCenterY: Float,
    frogAuraAlpha: Float
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
            // Левое плечо шторки
            lineTo((centerX - shoulderSpan).coerceAtLeast(0f), curtainTopY)
            // Левая кубическая кривая Безье к центру прогиба под монеткой
            cubicTo(
                centerX - shoulderSpan * 0.45f,
                curtainTopY,
                centerX - shoulderSpan * 0.28f,
                dipBottomY,
                centerX,
                dipBottomY
            )
            // Правая кубическая кривая Безье от центра прогиба к правому плечу
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

    // Основное полотно шторки (#080C14) + мягкое изумрудное свечение вокруг жабы
    clipPath(curtainPath) {
        drawRect(
            color = Color(0xFF080C14),
            topLeft = Offset.Zero,
            size = Size(w, h)
        )

        if (frogAuraAlpha > 0.01f) {
            val auraRadius = (minOf(w, h) * 0.52f).coerceAtLeast(10f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0D2818).copy(alpha = 0.95f * frogAuraAlpha),
                        Color(0xFF0A1E14).copy(alpha = 0.55f * frogAuraAlpha),
                        Color(0xFF080C14).copy(alpha = 0f)
                    ),
                    center = Offset(centerX, frogCenterY),
                    radius = auraRadius
                ),
                radius = auraRadius,
                center = Offset(centerX, frogCenterY)
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

    // Неоново-изумрудный / золотой кант вдоль изогнутой верхней кромки шторки
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

        // Мягкий внешний ореол канта
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

        // Яркое ядро канта
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
 * Отрисовка харизматичной жабы в стиле классического эмодзи 🐸 в едином стиле с адаптивной иконкой.
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
        // Тень под головой жабы
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

        // Два крупных полукруглых надбровных выступа сверху (левый и правый)
        val eyeBumpRadius = r * 0.46f
        val eyeBumpOffsetX = r * 0.48f
        val eyeBumpOffsetY = -r * 0.52f

        // Нижний теневой контур головы (#15803D)
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

        // Основная изумрудная форма головы (#4ADE80 -> #22C55E -> #15803D)
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

        // Светло-зеленая нижняя часть мордочки и щек (#86EFAC -> #4ADE80)
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

        // Объемный блик на лбу
        drawOval(
            color = Color(0xFFBBF7D0).copy(alpha = 0.35f * alpha),
            topLeft = Offset(-r * 0.42f, -r * 0.38f),
            size = Size(r * 0.84f, r * 0.22f)
        )

        // Мягкий румянец по бокам щек
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

        // Две аккуратные овальные ноздри (#064E3B)
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

        // Глаза в верхних полукруглых выступах со следящими за монеткой зрачками
        val eyeCenters = listOf(
            Offset(-eyeBumpOffsetX, eyeBumpOffsetY + r * 0.02f),
            Offset(eyeBumpOffsetX, eyeBumpOffsetY + r * 0.02f)
        )
        val scleraRadius = eyeBumpRadius * 0.62f
        val pupilRadius = scleraRadius * 0.64f
        val pupilShiftY = eyeLookYOffset * (scleraRadius * 0.28f)

        eyeCenters.forEach { eyeCenter ->
            // Изумрудный ободок века
            drawCircle(
                color = Color(0xFF15803D).copy(alpha = alpha),
                radius = scleraRadius * 1.12f,
                center = eyeCenter
            )
            // Светло-желтая радужка (#FEF9C3)
            drawCircle(
                color = Color(0xFFFEF9C3).copy(alpha = alpha),
                radius = scleraRadius,
                center = eyeCenter
            )
            // Выразительный темный зрачок (#0F172A), следящий за монеткой
            val pupilCenter = Offset(eyeCenter.x, eyeCenter.y + pupilShiftY)
            drawCircle(
                color = Color(0xFF0F172A).copy(alpha = alpha),
                radius = pupilRadius,
                center = pupilCenter
            )
            // Главный яркий белый блик (#FFFFFF)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = pupilRadius * 0.38f,
                center = Offset(
                    pupilCenter.x - pupilRadius * 0.34f,
                    pupilCenter.y - pupilRadius * 0.34f
                )
            )
            // Вторичный малый блик
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * alpha),
                radius = pupilRadius * 0.18f,
                center = Offset(
                    pupilCenter.x + pupilRadius * 0.32f,
                    pupilCenter.y + pupilRadius * 0.30f
                )
            )
        }

        // Широкий дружелюбный рот жабы (приоткрывается при подбрасывании монетки)
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

        // Розовый язычок внутри открытого рта
        if (mouthOpenFactor > 0.2f) {
            drawOval(
                color = Color(0xFFF43F5E).copy(alpha = 0.75f * alpha),
                topLeft = Offset(-r * 0.24f, mouthTopY + mouthHeight * 0.18f),
                size = Size(r * 0.48f, mouthHeight * 0.36f)
            )
        }

        // Четкая изумрудная линия улыбки сверху рта
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

    // Внешняя золотая вспышка / ореол вокруг монетки
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

    // Кольцо вспышки в верхней точке (Apex Flash)
    if (glowIntensity > 0.15f) {
        val starSpan = r * (1.35f + glowIntensity * 0.85f)
        drawLine(
            color = Color(0xFFFEF9C3).copy(alpha = glowIntensity * 0.85f),
            start = Offset(center.x - starSpan, center.y),
            end = Offset(center.x + starSpan, center.y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFFEF9C3).copy(alpha = glowIntensity * 0.85f),
            start = Offset(center.x, center.y - starSpan),
            end = Offset(center.x, center.y + starSpan),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    val absFlip = abs(flipScaleX).coerceIn(0.14f, 1f)
    val edgeThickness = (r * 0.22f * (1f - absFlip)).coerceAtLeast(2.dp.toPx())

    withTransform({
        translate(left = center.x, top = center.y)
    }) {
        // 3D-гурт (толщина ребра монетки при повороте)
        drawOval(
            color = Color(0xFFB45309),
            topLeft = Offset(-r * absFlip - edgeThickness, -r),
            size = Size(r * 2f * absFlip + edgeThickness * 2f, r * 2f)
        )

        // Лицевая сторона монетки со сжатием по оси X для 3D-оборота
        withTransform({
            scale(scaleX = absFlip, scaleY = 1f, pivot = Offset.Zero)
        }) {
            // Внешний золотой обод (#FDE047 -> #F59E0B -> #D97706)
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

            // Внутреннее углубленное кольцо (#B45309)
            drawCircle(
                color = Color(0xFFD97706),
                radius = r * 0.80f,
                center = Offset.Zero
            )

            // Яркое золотое ядро (#FEF08A -> #FBBF24 -> #F59E0B)
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

            // Диагональный зеркальный блик (#FEF08A)
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

            // Силуэт короны сверху и четкого знака рубля ₽ в центре монетки
            if (absFlip > 0.28f) {
                val emblemColor = Color(0xFF92400E)

                // Мини-корона в верхней части монетки
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

                // Символ ₽ в центре монетки
                val strokeW = (r * 0.13f).coerceAtLeast(2f)
                val stemX = -r * 0.14f
                val topR = -r * 0.16f
                val bottomR = r * 0.48f
                val midR = r * 0.16f

                // Вертикальная ножка ₽
                drawLine(
                    color = emblemColor,
                    start = Offset(stemX, topR),
                    end = Offset(stemX, bottomR),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // Верхняя дуга ₽
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
                // Нижняя горизонтальная перекладина ₽
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
 * Световые дуги и золотые искры при подбрасывании монетки вверх в Фазе 1.
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

    val trailTop = coinY + coinRadius * 0.5f
    val trailBottom = lerpFloat(mouthY, coinY + coinRadius * 3.2f, progress * 0.65f)
    if (trailBottom <= trailTop + 4f) return

    // Центральный восходящий золотой луч
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

    // 4 золотые искры вокруг взлетающей монетки
    val sparkOffsets = listOf(
        Offset(-coinRadius * 1.25f, coinRadius * 0.4f),
        Offset(coinRadius * 1.30f, coinRadius * 0.8f),
        Offset(-coinRadius * 0.85f, coinRadius * 1.6f),
        Offset(coinRadius * 0.95f, coinRadius * 1.9f)
    )
    sparkOffsets.forEachIndexed { idx, offset ->
        val sparkY = coinY + offset.y + (idx * 6.dp.toPx() * progress)
        drawCircle(
            color = Color(0xFFFEF08A).copy(alpha = alpha * (0.85f - idx * 0.15f)),
            radius = (3.5f - idx * 0.5f).dp.toPx(),
            center = Offset(centerX + offset.x, sparkY)
        )
    }
}

/**
 * Вертикальный золотой световой шлейф (`Brush.verticalGradient`) и 5 скоростных линий
 * позади стремительно падающей монетки во 2-й и 3-й фазах.
 */
private fun DrawScope.drawSpeedTrails(
    centerX: Float,
    coinY: Float,
    originTopY: Float,
    coinRadius: Float,
    intensity: Float
) {
    val trailStartY = originTopY.coerceAtMost(coinY - coinRadius * 0.5f)
    val trailEndY = coinY
    val trailHeight = (trailEndY - trailStartY).coerceAtLeast(4f)

    // Широкий золотой градиентный шлейф позади монетки
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

    // 5 быстрых вертикальных скоростных линий (speed lines)
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

// =========================================================================
// Вспомогательные иконки для экранов ИИ-анализа и долей расходов
// =========================================================================

@Composable
fun WalletSplashIcon(
    size: Dp = 140.dp,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val entryAlpha = remember { Animatable(if (animated) 0f else 1f) }
    val bill1Anim = remember { Animatable(0f) }
    val bill2Anim = remember { Animatable(0f) }
    val bill3Anim = remember { Animatable(0f) }
    val flyOffAnim = remember { Animatable(0f) }

    if (animated) {
        LaunchedEffect(Unit) {
            launch {
                entryAlpha.animateTo(1f, tween(250, easing = LinearOutSlowInEasing))
            }
            delay(200)
            launch {
                bill1Anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            delay(160)
            launch {
                bill2Anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            delay(160)
            launch {
                bill3Anim.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            delay(350)
            launch {
                flyOffAnim.animateTo(1f, tween(450, easing = FastOutLinearInEasing))
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val flyOffVal = flyOffAnim.value
                val alphaVal = entryAlpha.value * (if (flyOffVal > 0.5f) 1f - ((flyOffVal - 0.5f) / 0.5f) else 1f)
                alpha = alphaVal.coerceIn(0f, 1f)
                translationY = flyOffVal * size.toPx() * 3.5f
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val walletW = w * 0.65f
            val walletH = h * 0.34f
            val walletX = (w - walletW) / 2f
            val walletY = h * 0.52f

            drawRoundRect(
                color = DarkBg,
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, walletH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            val b3Progress = bill3Anim.value
            val b3W = w * 0.48f
            val b3H = h * 0.22f
            val b3StartY = walletY - b3H * 0.85f
            val b3EndY = walletY + b3H * 0.60f
            val b3Y = if (animated) b3StartY + b3Progress * (b3EndY - b3StartY) else b3StartY
            val b3Rot = if (animated) 8f * (1f - b3Progress) else 8f

            withTransform({
                translate(left = w / 2f, top = b3Y)
                rotate(degrees = b3Rot, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = Color(0xFF0284C7),
                    topLeft = Offset(-b3W / 2f, -b3H / 2f),
                    size = Size(b3W, b3H),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFBAE6FD),
                    topLeft = Offset(-b3W * 0.28f, -b3H / 2f),
                    size = Size(b3W * 0.12f, b3H)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(-b3W * 0.08f, -b3H * 0.32f),
                    size = Size(b3W * 0.35f, b3H * 0.42f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            val b2Progress = bill2Anim.value
            val b2W = w * 0.52f
            val b2H = h * 0.22f
            val b2StartY = walletY - b2H * 0.58f
            val b2EndY = walletY + b2H * 0.60f
            val b2Y = if (animated) b2StartY + b2Progress * (b2EndY - b2StartY) else b2StartY
            val b2Rot = if (animated) -8f * (1f - b2Progress) else -8f

            withTransform({
                translate(left = w / 2f, top = b2Y)
                rotate(degrees = b2Rot, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = Color(0xFF7C3AED),
                    topLeft = Offset(-b2W / 2f, -b2H / 2f),
                    size = Size(b2W, b2H),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFC7D2FE),
                    topLeft = Offset(b2W * 0.22f, -b2H / 2f),
                    size = Size(b2W * 0.14f, b2H)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(-b2W * 0.30f, -b2H * 0.32f),
                    size = Size(b2W * 0.38f, b2H * 0.42f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            val b1Progress = bill1Anim.value
            val b1W = w * 0.58f
            val b1H = h * 0.22f
            val b1StartY = walletY - b1H * 0.30f
            val b1EndY = walletY + b1H * 0.60f
            val b1Y = if (animated) b1StartY + b1Progress * (b1EndY - b1StartY) else b1StartY

            withTransform({
                translate(left = w / 2f, top = b1Y)
            }) {
                drawRoundRect(
                    color = Rose500,
                    topLeft = Offset(-b1W / 2f, -b1H / 2f),
                    size = Size(b1W, b1H),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = Amber400,
                    topLeft = Offset(-b1W * 0.35f, -b1H / 2f),
                    size = Size(b1W * 0.14f, b1H)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(-b1W * 0.10f, -b1H * 0.32f),
                    size = Size(b1W * 0.42f, b1H * 0.42f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            drawRoundRect(
                color = Color(0xFF1E1B4B),
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, walletH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFF312E81),
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, 4.dp.toPx())
            )
            drawRoundRect(
                color = Indigo500,
                topLeft = Offset(walletX, walletY),
                size = Size(walletW, walletH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

@Composable
fun AiWalletLoadingIcon(
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_wallet_fan")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val fold3Progress = ((phase - 0.00f) / 0.22f).coerceIn(0f, 1f)
    val fold1Progress = ((phase - 0.18f) / 0.22f).coerceIn(0f, 1f)
    val fold2Progress = ((phase - 0.36f) / 0.22f).coerceIn(0f, 1f)

    val isSnapping = phase >= 0.72f
    val rawSnap = if (phase < 0.72f) 0f else if (phase > 0.86f) 1f else (phase - 0.72f) / 0.14f
    val snapFactor = FastOutSlowInEasing.transform(rawSnap)

    val b3Fold = if (isSnapping) (1f - snapFactor) else fold3Progress
    val b1Fold = if (isSnapping) (1f - snapFactor) else fold1Progress
    val b2Fold = if (isSnapping) (1f - snapFactor) else fold2Progress

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val walletW = w * 0.70f
        val walletH = h * 0.38f
        val walletX = (w - walletW) / 2f
        val walletY = h * 0.52f

        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(walletX, walletY),
            size = Size(walletW, walletH),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
        )

        val b1Rot = -22f * (1f - b1Fold)
        val b1W = w * 0.50f
        val b1H = h * 0.24f
        val b1OffsetX = (-w * 0.12f) * (1f - b1Fold)
        val b1Y = walletY - b1H * 0.55f + (b1H * 0.45f * b1Fold)

        withTransform({
            translate(left = w / 2f + b1OffsetX, top = b1Y)
            rotate(degrees = b1Rot, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                color = Color(0xFF0284C7),
                topLeft = Offset(-b1W / 2f, -b1H / 2f),
                size = Size(b1W, b1H),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFBAE6FD),
                topLeft = Offset(-b1W * 0.30f, -b1H / 2f),
                size = Size(b1W * 0.12f, b1H)
            )
        }

        val b3Rot = 22f * (1f - b3Fold)
        val b3W = w * 0.52f
        val b3H = h * 0.24f
        val b3OffsetX = (w * 0.12f) * (1f - b3Fold)
        val b3Y = walletY - b3H * 0.55f + (b3H * 0.45f * b3Fold)

        withTransform({
            translate(left = w / 2f + b3OffsetX, top = b3Y)
            rotate(degrees = b3Rot, pivot = Offset.Zero)
        }) {
            drawRoundRect(
                color = Rose500,
                topLeft = Offset(-b3W / 2f, -b3H / 2f),
                size = Size(b3W, b3H),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFFDE68A),
                topLeft = Offset(-b3W * 0.30f, -b3H / 2f),
                size = Size(b3W * 0.12f, b3H)
            )
        }

        val b2W = w * 0.56f
        val b2H = h * 0.24f
        val b2Y = walletY - b2H * 0.70f + (b2H * 0.45f * b2Fold)

        withTransform({
            translate(left = w / 2f, top = b2Y)
        }) {
            drawRoundRect(
                color = Color(0xFF7C3AED),
                topLeft = Offset(-b2W / 2f, -b2H / 2f),
                size = Size(b2W, b2H),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }

        drawRoundRect(
            color = Color(0xFF1E1B4B),
            topLeft = Offset(walletX, walletY),
            size = Size(walletW, walletH),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        drawRoundRect(
            color = Color(0xFF818CF8),
            topLeft = Offset(walletX, walletY),
            size = Size(walletW, walletH),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
fun GrowthChartIcon(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier
) {
    AiWalletLoadingIcon(size = size, modifier = modifier)
}

/**
 * Демо-превью стартового экрана `GrowthChartSplashScreen`:
 * показывает, как падающая золотая монетка стягивает темную шторку вниз
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
