package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// ============================================================================
// ПАЛИТРА OBSIDIAN NEO-BANK + ИЗУМРУДНАЯ ЖАБА + ЗОЛОТАЯ МОНЕТКА
// ============================================================================
private val ObsidianCurtainTop = Color(0xFF060C18)
private val ObsidianCurtainMid = Color(0xFF0A1426)
private val ObsidianCurtainBottom = Color(0xFF040811)

private val EmeraldGlowOuter = Color(0xFF10B981)
private val EmeraldBright = Color(0xFF6EE7B7)
private val EmeraldLight = Color(0xFF34D399)
private val EmeraldPrimary = Color(0xFF10B981)
private val EmeraldDeep = Color(0xFF059669)
private val EmeraldShadow = Color(0xFF047857)
private val EmeraldDarkOutline = Color(0xFF064E3B)

private val FrogBellyMintLight = Color(0xFFD1FAE5)
private val FrogBellyMintMid = Color(0xFFA7F3D0)
private val FrogBellyMintDeep = Color(0xFF6EE7B7)
private val FrogCheekCoral = Color(0xFFFB7185)
private val FrogMouthDark = Color(0xFF022C22)
private val FrogTongueCoral = Color(0xFFF43F5E)

private val EyeScleraLight = Color(0xFFF8FAFC)
private val EyeScleraShadow = Color(0xFFCBD5E1)
private val EyePupilDark = Color(0xFF090D16)
private val EyeIrisGold = Color(0xFFFBBF24)

private val GoldRimLight = Color(0xFFFEF08A)
private val GoldPrimary = Color(0xFFFACC15)
private val GoldMid = Color(0xFFF59E0B)
private val GoldDeep = Color(0xFFD97706)
private val GoldShadow = Color(0xFFB45309)
private val GoldEdgeDark = Color(0xFF78350F)

// ============================================================================
// ТАЙМИНГИ И ФАЗЫ АНИМАЦИИ (ОБЩАЯ ДЛИТЕЛЬНОСТЬ ~1650 МС)
// ============================================================================
private const val TOTAL_SPLASH_DURATION_MS = 1650
private const val PHASE_1_END = 0.36f // 0..600ms: монетка плотно под верхней губой -> вылет вверх и кувырок
private const val PHASE_2_END = 0.56f // 600..920ms: падение монетки с ускорением вниз до касания шторки
private const val PHASE_1_HOLD_RATIO = 0.06f // Первые 6% Фазы 1 монетка плотно зафиксирована во рту под губой
// 0.56..1.00 (920..1650ms): монетка тянет шторку вниз с прогибом Безье и открывает экран

private val CoinThrowOutEasing = CubicBezierEasing(0.18f, 0.88f, 0.28f, 1.0f)
private val CoinGravityInEasing = CubicBezierEasing(0.50f, 0.0f, 0.88f, 0.78f)
private val CurtainPullEasing = CubicBezierEasing(0.32f, 0.0f, 0.16f, 1.0f)

/**
 * Предварительно аллоцированные Path для исключения аллокаций внутри onDraw (120 FPS).
 */
private class SplashReusablePaths {
    val curtainPath = Path()
    val curtainRimPath = Path()
    val headPath = Path()
    val bellyPath = Path()
    val mouthBgPath = Path()
    val tonguePath = Path()
    val smileStrokePath = Path()
    val upperLipPath = Path()
    val upperLipHighlightPath = Path()
    val crownPath = Path()
    val rublePath = Path()
    val coinFrogHeadPath = Path()
    val coinFrogSmilePath = Path()
}

/**
 * Кэш статических кистей, зависящих только от размеров Canvas.
 * Исключает создание объектов Brush на каждом кадре анимации.
 */
private class SplashCachedBrushes(
    val width: Float,
    val height: Float,
    val frogBaseRadius: Float
) {
    val curtainBgBrush = Brush.verticalGradient(
        colors = listOf(ObsidianCurtainTop, ObsidianCurtainMid, ObsidianCurtainBottom),
        startY = 0f,
        endY = height
    )

    val ambientGlowBrush = Brush.radialGradient(
        colors = listOf(
            EmeraldGlowOuter.copy(alpha = 0.24f),
            EmeraldPrimary.copy(alpha = 0.08f),
            Color.Transparent
        ),
        center = Offset(width * 0.5f, height * 0.43f),
        radius = frogBaseRadius * 2.5f
    )
}

/**
 * Основная перегрузка сплеш-экрана.
 * Когда [isExiting] переходит в true, запускает 1650-мс анимацию:
 * 1) Монетка стартует плотно во рту под верхней губой жабы, вылетает вверх и кувыркается в 3D;
 * 2) Монетка падает вниз под действием гравитации;
 * 3) В финале падающая монетка физически цепляет верхний край тёмной шторки и тянет его вниз
 *    до самого низа экрана, открывая реальный [content] позади шторки.
 */
@Composable
fun GrowthChartSplashScreen(
    isExiting: Boolean,
    onExitFinished: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    var splashDismissed by remember { mutableStateOf(false) }
    val timelineProgress = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "frog_idle_breathing")
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breath"
    )

    LaunchedEffect(isExiting) {
        if (isExiting && !splashDismissed) {
            timelineProgress.snapTo(0f)
            timelineProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TOTAL_SPLASH_DURATION_MS,
                    easing = LinearEasing
                )
            )
            splashDismissed = true
            onExitFinished()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Реальный интерфейс приложения рендерится позади шторки
        content?.invoke()

        if (!splashDismissed) {
            SplashCanvasOverlay(
                progress = timelineProgress.value,
                idleBreath = if (isExiting) 0f else idleBreath,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Обратная совместимость со старой сигнатурой без content-лямбды.
 */
@Composable
fun GrowthChartSplashScreen(
    isExiting: Boolean,
    onExitFinished: () -> Unit
) {
    GrowthChartSplashScreen(
        isExiting = isExiting,
        onExitFinished = onExitFinished,
        modifier = Modifier.fillMaxSize(),
        content = null
    )
}

/**
 * Вычисляет 2D-смещение зрачка (X, Y) в пикселях на основе реальных координат центра глаза
 * и текущих координат монетки, с мягким анатомическим ограничением внутри радужки.
 */
internal fun computePupilTrackingOffset(
    eyeCenterX: Float,
    eyeCenterY: Float,
    coinX: Float,
    coinY: Float,
    maxHorizontalTravel: Float,
    maxVerticalTravel: Float
): Offset {
    val safeMaxX = max(maxHorizontalTravel, 0.001f)
    val safeMaxY = max(maxVerticalTravel, 0.001f)

    val dx = coinX - eyeCenterX
    val dy = coinY - eyeCenterY

    // Нормализуем дистанцию относительно характерного масштаба смещения глаз.
    // За счёт кривой tanh взгляд плавно следует за монеткой на всём пути,
    // естественно замедляется в верхней точке (апогее) и никогда не делает резких скачков.
    val normX = tanh(dx / (safeMaxX * 6.5f))
    val normY = tanh(dy / (safeMaxY * 5.2f))

    var offsetX = normX * safeMaxX
    var offsetY = normY * safeMaxY

    // Эллиптическое ограничение гарантирует, что зрачок и блики никогда не выйдут за границы глаза
    val ellipseDistSq = (offsetX / safeMaxX) * (offsetX / safeMaxX) +
        (offsetY / safeMaxY) * (offsetY / safeMaxY)
    if (ellipseDistSq > 1f) {
        val invLen = 1f / sqrt(ellipseDistSq)
        offsetX *= invLen
        offsetY *= invLen
    }

    return Offset(offsetX, offsetY)
}

@Composable
private fun SplashCanvasOverlay(
    progress: Float,
    idleBreath: Float,
    modifier: Modifier = Modifier
) {
    val paths = remember { SplashReusablePaths() }
    var cachedBrushes by remember { mutableStateOf<SplashCachedBrushes?>(null) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDim = min(w, h)
        val centerX = w * 0.5f

        // Базовые размеры жабы и монетки
        val frogBaseRadius = minDim * 0.21f
        val frogRestCenterY = h * 0.44f
        val coinBaseRadius = frogBaseRadius * 0.48f

        val brushes = cachedBrushes?.takeIf {
            it.width == w && it.height == h && it.frogBaseRadius == frogBaseRadius
        } ?: SplashCachedBrushes(w, h, frogBaseRadius).also {
            cachedBrushes = it
        }

        val t = progress.coerceIn(0f, 1f)

        // ====================================================================
        // 1. РАСЧЕТ ПАРАМЕТРОВ ФАЗ (ЖАБА, МОНЕТКА, ШТОРКА)
        // ====================================================================
        val p1 = if (t <= PHASE_1_END) {
            (t / PHASE_1_END).coerceIn(0f, 1f)
        } else {
            1f
        }

        val p2 = when {
            t <= PHASE_1_END -> 0f
            t <= PHASE_2_END -> ((t - PHASE_1_END) / (PHASE_2_END - PHASE_1_END)).coerceIn(0f, 1f)
            else -> 1f
        }

        val p3 = if (t <= PHASE_2_END) {
            0f
        } else {
            ((t - PHASE_2_END) / (1f - PHASE_2_END)).coerceIn(0f, 1f)
        }

        // Squash & Stretch жабы в Фазе 1 + легкое дыхание в режиме ожидания
        val idleScaleX = 1f + idleBreath * 0.018f
        val idleScaleY = 1f - idleBreath * 0.018f

        val frogScaleX: Float
        val frogScaleY: Float
        val frogOffsetY: Float

        if (t <= PHASE_1_END) {
            when {
                // Подготовка к толчку (0.00..0.24 внутри Фазы 1): сжатие
                p1 < 0.24f -> {
                    val squashP = (p1 / 0.24f).coerceIn(0f, 1f)
                    val smoothSquash = sin(squashP * (PI.toFloat() * 0.5f))
                    frogScaleX = lerpFloat(idleScaleX, 1.12f, smoothSquash)
                    frogScaleY = lerpFloat(idleScaleY, 0.88f, smoothSquash)
                    frogOffsetY = frogBaseRadius * 0.10f * smoothSquash
                }
                // Выталкивание монетки вверх (0.24..0.58 внутри Фазы 1): вытягивание вверх
                p1 < 0.58f -> {
                    val stretchP = ((p1 - 0.24f) / 0.34f).coerceIn(0f, 1f)
                    val smoothStretch = sin(stretchP * (PI.toFloat() * 0.5f))
                    frogScaleX = lerpFloat(1.12f, 0.93f, smoothStretch)
                    frogScaleY = lerpFloat(0.88f, 1.09f, smoothStretch)
                    frogOffsetY = lerpFloat(frogBaseRadius * 0.10f, -frogBaseRadius * 0.11f, smoothStretch)
                }
                // Возврат в упругое положение (0.58..1.00 внутри Фазы 1)
                else -> {
                    val settleP = ((p1 - 0.58f) / 0.42f).coerceIn(0f, 1f)
                    val smoothSettle = FastOutSlowInEasing.transform(settleP)
                    frogScaleX = lerpFloat(0.93f, 1.0f, smoothSettle)
                    frogScaleY = lerpFloat(1.09f, 1.0f, smoothSettle)
                    frogOffsetY = lerpFloat(-frogBaseRadius * 0.11f, 0f, smoothSettle)
                }
            }
        } else if (t <= PHASE_2_END) {
            // В Фазе 2 жаба следит за падающей монеткой и слегка приседает перед захватом шторки
            val anticipate = CoinGravityInEasing.transform(p2)
            frogScaleX = 1.0f + 0.03f * anticipate
            frogScaleY = 1.0f - 0.03f * anticipate
            frogOffsetY = frogBaseRadius * 0.03f * anticipate
        } else {
            // В Фазе 3 жаба уходит вниз вместе со шторкой
            frogScaleX = 1.03f
            frogScaleY = 0.97f
            frogOffsetY = frogBaseRadius * 0.03f
        }

        // Расчет прогиба и движения шторки в Фазе 3
        val pullEased = CurtainPullEasing.transform(p3)
        // Боковые края шторки идут чуть позади центра, создавая живое натяжение ткани/мембраны
        val sideEdgeProgress = ((pullEased - 0.05f) / 0.95f).coerceIn(0f, 1f)
        val curtainEdgeY = lerpFloat(0f, h + coinBaseRadius * 2.2f, sideEdgeProgress)

        // Дополнительный эластичный прогиб в центре под весом монетки
        val sagEnvelope = sin(pullEased * PI.toFloat())
        val initialCatchIndent = if (t > PHASE_1_END && t <= PHASE_2_END) {
            val catchPrep = ((p2 - 0.78f) / 0.22f).coerceIn(0f, 1f)
            catchPrep * (h * 0.032f)
        } else {
            0f
        }
        val curtainSagDepth = if (t <= PHASE_2_END) {
            initialCatchIndent
        } else {
            (h * 0.032f) + (h * 0.13f) * sagEnvelope
        }
        val curtainNotchCenterY = curtainEdgeY + curtainSagDepth

        // Координаты жабы с учетом смещения всей шторки вниз
        val currentFrogCenterY = frogRestCenterY + frogOffsetY + curtainEdgeY * 0.92f
        val currentFrogScaleX = frogScaleX
        val currentFrogScaleY = frogScaleY

        // Позиция рта (стартовая точка монетки глубоко в нижней челюсти под верхней губой, как на иконке)
        val mouthOriginY = currentFrogCenterY + (frogBaseRadius * 0.68f) * currentFrogScaleY
        // Верхняя точка взлета монетки (с запасом, чтобы монетка не обрезалась)
        val peakCoinY = max(coinBaseRadius * 1.9f, h * 0.135f)
        // Точка, где падающая монетка цепляет верхний край шторки и начинает тянуть её вниз
        val catchHookY = h * 0.24f

        val flightUpProgress = if (p1 <= PHASE_1_HOLD_RATIO) {
            0f
        } else {
            ((p1 - PHASE_1_HOLD_RATIO) / (1f - PHASE_1_HOLD_RATIO)).coerceIn(0f, 1f)
        }

        // Вертикальная координата монетки по 3 фазам
        val coinY = when {
            t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO -> {
                mouthOriginY
            }
            t <= PHASE_1_END -> {
                val upEased = CoinThrowOutEasing.transform(flightUpProgress)
                lerpFloat(mouthOriginY, peakCoinY, upEased)
            }
            t <= PHASE_2_END -> {
                val downEased = CoinGravityInEasing.transform(p2)
                lerpFloat(peakCoinY, catchHookY, downEased)
            }
            else -> {
                // В Фазе 3 монетка сцеплена с центром прогиба шторки и тянет его до самого низа
                lerpFloat(catchHookY, curtainNotchCenterY, (p3 * 3.5f).coerceIn(0f, 1f))
            }
        }

        // Угол 3D-вращения монетки вокруг горизонтальной оси (кувырок при броске + вращение в падении)
        val coinSpinAngle: Float = when {
            t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO -> {
                0f
            }
            t <= PHASE_1_END -> {
                // 2 полных оборота на взлете (4 * PI): при старте (0) и в пике (4*PI) виден знак ₽,
                // а на нечетных полуоборотах (PI, 3*PI) четко видна золотая чеканка жабы на реверсе
                flightUpProgress * 4.0f * PI.toFloat()
            }
            t <= PHASE_2_END -> {
                // В падении делает ещё 3 оборота (до 10 * PI), выравниваясь к моменту зацепа шторки
                (4.0f + p2 * 6.0f) * PI.toFloat()
            }
            else -> {
                // При растягивании шторки монетка стабилизируется лицом к зрителю с легким покачиванием
                10.0f * PI.toFloat() + sin(p3 * PI.toFloat() * 2f) * 0.22f
            }
        }

        // Масштаб монетки (эффект приближения к камере на взлете)
        val coinScale = when {
            t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO -> {
                0.82f
            }
            t <= PHASE_1_END -> {
                val arc = sin(flightUpProgress * PI.toFloat())
                lerpFloat(0.82f, 1.06f, flightUpProgress) + arc * 0.12f
            }
            t <= PHASE_2_END -> {
                lerpFloat(1.06f, 1.0f, p2)
            }
            else -> {
                1.0f - 0.08f * pullEased
            }
        }

        // Легкий наклон монетки в полете
        val coinTiltDegrees = when {
            t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO -> 0f
            t <= PHASE_1_END -> sin(flightUpProgress * PI.toFloat() * 2f) * 8f
            t <= PHASE_2_END -> -sin(p2 * PI.toFloat()) * 6f
            else -> 0f
        }

        // Прозрачность и плавное исчезновение жабы, когда шторка уже почти стянута вниз
        val frogAlpha = if (t <= PHASE_2_END) {
            1f
        } else {
            (1f - ((p3 - 0.55f) / 0.40f)).coerceIn(0f, 1f)
        }

        val mouthOpenFactor = when {
            t <= PHASE_1_END && p1 <= PHASE_1_HOLD_RATIO -> {
                0.78f
            }
            t <= PHASE_1_END -> {
                val tossOpen = sin(flightUpProgress * PI.toFloat())
                (0.78f * (1f - flightUpProgress * 0.7f) + 0.25f * tossOpen).coerceIn(0.25f, 1f)
            }
            else -> 0.28f
        }

        // ====================================================================
        // 2. ОТРИСОВКА ТЕМНОЙ ШТОРКИ С ПРОГИБОМ БЕЗЬЕ
        // ====================================================================
        paths.curtainPath.reset()
        paths.curtainPath.moveTo(0f, curtainEdgeY)
        // Левая ветвь кривой к центральной ложбинке под монеткой
        paths.curtainPath.cubicTo(
            w * 0.22f,
            curtainEdgeY,
            centerX - coinBaseRadius * 2.2f,
            curtainNotchCenterY,
            centerX,
            curtainNotchCenterY
        )
        // Правая ветвь кривой от центральной ложбинки к правому краю
        paths.curtainPath.cubicTo(
            centerX + coinBaseRadius * 2.2f,
            curtainNotchCenterY,
            w * 0.78f,
            curtainEdgeY,
            w,
            curtainEdgeY
        )
        paths.curtainPath.lineTo(w, h + 4f)
        paths.curtainPath.lineTo(0f, h + 4f)
        paths.curtainPath.close()

        // Внутренность шторки (фон + мягкое свечение + жаба) обрезается строго по геометрии шторки
        clipPath(paths.curtainPath) {
            drawRect(
                brush = brushes.curtainBgBrush,
                topLeft = Offset.Zero,
                size = size
            )

            // Радиальное изумрудное свечение позади жабы
            if (frogAlpha > 0.01f) {
                drawCircle(
                    brush = brushes.ambientGlowBrush,
                    radius = frogBaseRadius * 2.5f,
                    center = Offset(centerX, currentFrogCenterY)
                )
            }

            // Отрисовка мультяшной изумрудной жабы со взглядом, привязанным к реальным координатам монетки
            if (frogAlpha > 0.01f && currentFrogCenterY - frogBaseRadius * 1.6f < h) {
                drawCartoonEmeraldFrog(
                    paths = paths,
                    centerX = centerX,
                    centerY = currentFrogCenterY,
                    baseRadius = frogBaseRadius,
                    scaleX = currentFrogScaleX,
                    scaleY = currentFrogScaleY,
                    alpha = frogAlpha,
                    coinX = centerX,
                    coinY = coinY,
                    mouthOpenFactor = mouthOpenFactor
                )
            }
        }

        // Неоновая изумрудно-золотая кромка по верхнему краю шторки (показывает физическое натяжение)
        if (t > PHASE_1_END * 0.85f && curtainEdgeY < h) {
            val rimAlpha = when {
                t <= PHASE_2_END -> ((t - PHASE_1_END * 0.85f) / (PHASE_2_END - PHASE_1_END * 0.85f)).coerceIn(0f, 1f) * 0.75f
                else -> (1f - (p3 - 0.82f).coerceAtLeast(0f) / 0.18f).coerceIn(0f, 1f)
            }
            paths.curtainRimPath.reset()
            paths.curtainRimPath.moveTo(0f, curtainEdgeY)
            paths.curtainRimPath.cubicTo(
                w * 0.22f,
                curtainEdgeY,
                centerX - coinBaseRadius * 2.2f,
                curtainNotchCenterY,
                centerX,
                curtainNotchCenterY
            )
            paths.curtainRimPath.cubicTo(
                centerX + coinBaseRadius * 2.2f,
                curtainNotchCenterY,
                w * 0.78f,
                curtainEdgeY,
                w,
                curtainEdgeY
            )

            drawPath(
                path = paths.curtainRimPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        EmeraldPrimary.copy(alpha = 0.20f * rimAlpha),
                        EmeraldLight.copy(alpha = 0.85f * rimAlpha),
                        GoldPrimary.copy(alpha = 0.95f * rimAlpha),
                        EmeraldLight.copy(alpha = 0.85f * rimAlpha),
                        EmeraldPrimary.copy(alpha = 0.20f * rimAlpha)
                    )
                ),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // ====================================================================
        // 3. ОТРИСОВКА ЗОЛОТОЙ МОНЕТКИ И ПЕРЕКРЫТИЯ ВЕРХНЕЙ ГУБОЙ НА СТАРТЕ
        // ====================================================================
        val isCoinTuckedUnderLip = t <= PHASE_1_END && p1 <= 0.10f
        if (coinY - coinBaseRadius * 1.5f < h) {
            if (isCoinTuckedUnderLip) {
                // На самом старте верхняя часть монетки гарантированно отсечена под линией верхней губы
                val mouthClipTop = currentFrogCenterY + (frogBaseRadius * 0.18f) * currentFrogScaleY
                clipRect(
                    left = 0f,
                    top = mouthClipTop,
                    right = w,
                    bottom = h
                ) {
                    drawSpinningGoldCoin(
                        paths = paths,
                        centerX = centerX,
                        centerY = coinY,
                        baseRadius = coinBaseRadius * coinScale,
                        spinAngleRad = coinSpinAngle,
                        tiltDegrees = coinTiltDegrees
                    )
                }
            } else {
                drawSpinningGoldCoin(
                    paths = paths,
                    centerX = centerX,
                    centerY = coinY,
                    baseRadius = coinBaseRadius * coinScale,
                    spinAngleRad = coinSpinAngle,
                    tiltDegrees = coinTiltDegrees
                )
            }
        }

        // На старте (пока монетка ещё во рту) рисуем высокую накладку верхней губы и ноздри поверх монетки,
        // чтобы монетка гарантированно выглядела зажатой ПОД верхней губой, как на иконке приложения.
        if (isCoinTuckedUnderLip && frogAlpha > 0.01f) {
            val lipOverlapAlpha = if (p1 <= 0.08f) {
                1f
            } else {
                ((0.10f - p1) / 0.02f).coerceIn(0f, 1f)
            }
            drawFrogUpperLipOverlap(
                paths = paths,
                centerX = centerX,
                centerY = currentFrogCenterY,
                baseRadius = frogBaseRadius,
                scaleX = currentFrogScaleX,
                scaleY = currentFrogScaleY,
                alpha = frogAlpha * lipOverlapAlpha
            )
        }
    }
}

/**
 * Отрисовка мультяшной зелёной жабы в едином стиле с адаптивной иконкой приложения.
 * Зрачки отслеживают фактическую координату монетки ([coinX], [coinY]) в реальном времени.
 */
private fun DrawScope.drawCartoonEmeraldFrog(
    paths: SplashReusablePaths,
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    scaleX: Float,
    scaleY: Float,
    alpha: Float,
    coinX: Float,
    coinY: Float,
    mouthOpenFactor: Float
) {
    // Вычисляем реальные экранные координаты центров глаз с учётом текущей трансформации головы жабы
    val eyeOffsetX = baseRadius * 0.56f
    val eyeOffsetY = -baseRadius * 0.50f
    val eyeRadius = baseRadius * 0.36f
    val scleraRadius = eyeRadius * 0.72f

    val leftEyeScreenX = centerX - eyeOffsetX * scaleX
    val rightEyeScreenX = centerX + eyeOffsetX * scaleX
    val eyeScreenY = centerY + eyeOffsetY * scaleY

    // Максимально допустимый ход зрачка внутри склеры (с запасом, чтобы ни зрачок, ни золотая радужка не выходили за глаз)
    val maxPupilShiftX = scleraRadius * 0.26f
    val maxPupilShiftY = scleraRadius * 0.36f

    val leftPupilShift = computePupilTrackingOffset(
        eyeCenterX = leftEyeScreenX,
        eyeCenterY = eyeScreenY,
        coinX = coinX,
        coinY = coinY,
        maxHorizontalTravel = maxPupilShiftX,
        maxVerticalTravel = maxPupilShiftY
    )
    val rightPupilShift = computePupilTrackingOffset(
        eyeCenterX = rightEyeScreenX,
        eyeCenterY = eyeScreenY,
        coinX = coinX,
        coinY = coinY,
        maxHorizontalTravel = maxPupilShiftX,
        maxVerticalTravel = maxPupilShiftY
    )

    withTransform({
        translate(left = centerX, top = centerY)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
    }) {
        val r = baseRadius

        // 1. Тень под жабой
        drawOval(
            color = Color.Black.copy(alpha = 0.42f * alpha),
            topLeft = Offset(-r * 1.05f, r * 0.72f),
            size = Size(r * 2.10f, r * 0.38f)
        )

        // 2. Задние лапки по бокам
        drawOval(
            color = EmeraldShadow.copy(alpha = alpha),
            topLeft = Offset(-r * 1.18f, r * 0.16f),
            size = Size(r * 0.58f, r * 0.70f)
        )
        drawOval(
            color = EmeraldShadow.copy(alpha = alpha),
            topLeft = Offset(r * 0.60f, r * 0.16f),
            size = Size(r * 0.58f, r * 0.70f)
        )

        // 3. Бугорки глаз (верхние полусферы головы)
        // Внешний тёмно-изумрудный контур для четкости силуэта
        val outlineWidth = r * 0.055f
        drawCircle(
            color = EmeraldDarkOutline.copy(alpha = alpha),
            radius = eyeRadius + outlineWidth * 0.5f,
            center = Offset(-eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            color = EmeraldDarkOutline.copy(alpha = alpha),
            radius = eyeRadius + outlineWidth * 0.5f,
            center = Offset(eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    EmeraldLight.copy(alpha = alpha),
                    EmeraldPrimary.copy(alpha = alpha),
                    EmeraldDeep.copy(alpha = alpha)
                ),
                center = Offset(-eyeOffsetX - eyeRadius * 0.2f, eyeOffsetY - eyeRadius * 0.25f),
                radius = eyeRadius * 1.25f
            ),
            radius = eyeRadius,
            center = Offset(-eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    EmeraldLight.copy(alpha = alpha),
                    EmeraldPrimary.copy(alpha = alpha),
                    EmeraldDeep.copy(alpha = alpha)
                ),
                center = Offset(eyeOffsetX - eyeRadius * 0.2f, eyeOffsetY - eyeRadius * 0.25f),
                radius = eyeRadius * 1.25f
            ),
            radius = eyeRadius,
            center = Offset(eyeOffsetX, eyeOffsetY)
        )

        // 4. Основное тело/голова жабы (пухлая мультяшная форма)
        paths.headPath.reset()
        paths.headPath.moveTo(-r * 1.06f, r * 0.10f)
        paths.headPath.cubicTo(-r * 1.12f, -r * 0.58f, -r * 0.58f, -r * 0.68f, 0f, -r * 0.68f)
        paths.headPath.cubicTo(r * 0.58f, -r * 0.68f, r * 1.12f, -r * 0.58f, r * 1.06f, r * 0.10f)
        paths.headPath.cubicTo(r * 1.14f, r * 0.72f, r * 0.68f, r * 0.94f, 0f, r * 0.94f)
        paths.headPath.cubicTo(-r * 0.68f, r * 0.94f, -r * 1.14f, r * 0.72f, -r * 1.06f, r * 0.10f)
        paths.headPath.close()

        drawPath(
            path = paths.headPath,
            color = EmeraldDarkOutline.copy(alpha = alpha),
            style = Stroke(width = outlineWidth, join = StrokeJoin.Round)
        )
        drawPath(
            path = paths.headPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    EmeraldLight.copy(alpha = alpha),
                    EmeraldPrimary.copy(alpha = alpha),
                    EmeraldDeep.copy(alpha = alpha)
                ),
                startY = -r * 0.68f,
                endY = r * 0.94f
            )
        )

        // Верхний глянцевый блик на макушке
        drawOval(
            color = EmeraldBright.copy(alpha = 0.35f * alpha),
            topLeft = Offset(-r * 0.46f, -r * 0.56f),
            size = Size(r * 0.92f, r * 0.24f)
        )

        // 5. Светлое мятное брюшко / нижняя челюсть
        paths.bellyPath.reset()
        paths.bellyPath.moveTo(-r * 0.76f, r * 0.36f)
        paths.bellyPath.cubicTo(-r * 0.52f, r * 0.18f, r * 0.52f, r * 0.18f, r * 0.76f, r * 0.36f)
        paths.bellyPath.cubicTo(r * 0.82f, r * 0.76f, r * 0.45f, r * 0.90f, 0f, r * 0.90f)
        paths.bellyPath.cubicTo(-r * 0.45f, r * 0.90f, -r * 0.82f, r * 0.76f, -r * 0.76f, r * 0.36f)
        paths.bellyPath.close()

        drawPath(
            path = paths.bellyPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    FrogBellyMintLight.copy(alpha = 0.95f * alpha),
                    FrogBellyMintMid.copy(alpha = 0.92f * alpha),
                    FrogBellyMintDeep.copy(alpha = 0.88f * alpha)
                ),
                startY = r * 0.20f,
                endY = r * 0.90f
            )
        )

        // 6. Румяные щёчки
        drawOval(
            color = FrogCheekCoral.copy(alpha = 0.38f * alpha),
            topLeft = Offset(-r * 0.92f, r * 0.04f),
            size = Size(r * 0.32f, r * 0.20f)
        )
        drawOval(
            color = FrogCheekCoral.copy(alpha = 0.38f * alpha),
            topLeft = Offset(r * 0.60f, r * 0.04f),
            size = Size(r * 0.32f, r * 0.20f)
        )

        // 7. Открытый улыбающийся рот (откуда вылетает монетка)
        val mouthHalfWidth = r * 0.56f
        val mouthTopY = r * 0.16f
        val mouthBottomY = r * (0.34f + 0.32f * mouthOpenFactor)

        paths.mouthBgPath.reset()
        paths.mouthBgPath.moveTo(-mouthHalfWidth, mouthTopY)
        paths.mouthBgPath.quadraticTo(0f, mouthTopY + r * 0.14f, mouthHalfWidth, mouthTopY)
        paths.mouthBgPath.quadraticTo(0f, mouthBottomY + r * 0.18f, -mouthHalfWidth, mouthTopY)
        paths.mouthBgPath.close()

        drawPath(
            path = paths.mouthBgPath,
            color = FrogMouthDark.copy(alpha = alpha)
        )

        // Розовый язычок в глубине рта
        clipPath(paths.mouthBgPath) {
            drawOval(
                color = FrogTongueCoral.copy(alpha = 0.88f * alpha),
                topLeft = Offset(-r * 0.30f, mouthBottomY - r * 0.16f),
                size = Size(r * 0.60f, r * 0.30f)
            )
        }

        // Контур улыбки (верхняя губа)
        paths.smileStrokePath.reset()
        paths.smileStrokePath.moveTo(-mouthHalfWidth * 1.05f, mouthTopY - r * 0.01f)
        paths.smileStrokePath.quadraticTo(0f, mouthTopY + r * 0.15f, mouthHalfWidth * 1.05f, mouthTopY - r * 0.01f)
        drawPath(
            path = paths.smileStrokePath,
            color = EmeraldDarkOutline.copy(alpha = alpha),
            style = Stroke(width = r * 0.048f, cap = StrokeCap.Round)
        )

        // 8. Ноздри
        drawOval(
            color = EmeraldDarkOutline.copy(alpha = 0.85f * alpha),
            topLeft = Offset(-r * 0.16f, -r * 0.08f),
            size = Size(r * 0.07f, r * 0.05f)
        )
        drawOval(
            color = EmeraldDarkOutline.copy(alpha = 0.85f * alpha),
            topLeft = Offset(r * 0.09f, -r * 0.08f),
            size = Size(r * 0.07f, r * 0.05f)
        )

        // 9. Глаза (склера, золотая радужка, зрачок и живые блики)
        drawFrogEye(
            centerX = -eyeOffsetX,
            centerY = eyeOffsetY,
            scleraRadius = scleraRadius,
            pupilShiftX = leftPupilShift.x,
            pupilShiftY = leftPupilShift.y,
            alpha = alpha
        )
        drawFrogEye(
            centerX = eyeOffsetX,
            centerY = eyeOffsetY,
            scleraRadius = scleraRadius,
            pupilShiftX = rightPupilShift.x,
            pupilShiftY = rightPupilShift.y,
            alpha = alpha
        )

        // 10. Передние лапки внизу (придерживают монетку на старте)
        drawRoundRect(
            color = EmeraldPrimary.copy(alpha = alpha),
            topLeft = Offset(-r * 0.64f, r * 0.72f),
            size = Size(r * 0.36f, r * 0.24f),
            cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
        )
        drawRoundRect(
            color = EmeraldPrimary.copy(alpha = alpha),
            topLeft = Offset(r * 0.28f, r * 0.72f),
            size = Size(r * 0.36f, r * 0.24f),
            cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
        )
    }
}

private fun DrawScope.drawFrogEye(
    centerX: Float,
    centerY: Float,
    scleraRadius: Float,
    pupilShiftX: Float,
    pupilShiftY: Float,
    alpha: Float
) {
    // Тень под склерой
    drawCircle(
        color = EyeScleraShadow.copy(alpha = alpha),
        radius = scleraRadius,
        center = Offset(centerX, centerY + scleraRadius * 0.06f)
    )
    // Белок глаза
    drawCircle(
        color = EyeScleraLight.copy(alpha = alpha),
        radius = scleraRadius * 0.96f,
        center = Offset(centerX, centerY)
    )

    val pupilCenter = Offset(centerX + pupilShiftX, centerY + pupilShiftY)

    // Тонкое золотое кольцо радужки (перекликается с монеткой)
    drawCircle(
        color = EyeIrisGold.copy(alpha = 0.92f * alpha),
        radius = scleraRadius * 0.58f,
        center = pupilCenter
    )
    // Глубокий темный зрачок
    drawCircle(
        color = EyePupilDark.copy(alpha = alpha),
        radius = scleraRadius * 0.46f,
        center = pupilCenter
    )
    // Крупный и малый живые блики
    drawCircle(
        color = Color.White.copy(alpha = 0.96f * alpha),
        radius = scleraRadius * 0.19f,
        center = Offset(
            pupilCenter.x - scleraRadius * 0.16f,
            pupilCenter.y - scleraRadius * 0.18f
        )
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.75f * alpha),
        radius = scleraRadius * 0.08f,
        center = Offset(
            pupilCenter.x + scleraRadius * 0.16f,
            pupilCenter.y + scleraRadius * 0.14f
        )
    )
}

/**
 * Накладка верхней губы жабы (рисуется поверх монетки только в первые мгновения старта),
 * чтобы монетка начинала движение непосредственно ИЗО РТА жабы под верхней губой.
 */
private fun DrawScope.drawFrogUpperLipOverlap(
    paths: SplashReusablePaths,
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    scaleX: Float,
    scaleY: Float,
    alpha: Float
) {
    withTransform({
        translate(left = centerX, top = centerY)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
    }) {
        val r = baseRadius
        val mouthHalfWidth = r * 0.58f
        val lipTopY = -r * 0.02f
        val lipCornerY = r * 0.16f
        val lipArchControlY = r * 0.30f

        paths.upperLipPath.reset()
        paths.upperLipPath.moveTo(-mouthHalfWidth, lipCornerY)
        paths.upperLipPath.cubicTo(
            -mouthHalfWidth * 0.85f,
            lipTopY,
            mouthHalfWidth * 0.85f,
            lipTopY,
            mouthHalfWidth,
            lipCornerY
        )
        paths.upperLipPath.quadraticTo(0f, lipArchControlY, -mouthHalfWidth, lipCornerY)
        paths.upperLipPath.close()

        drawPath(
            path = paths.upperLipPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    EmeraldPrimary.copy(alpha = alpha),
                    EmeraldDeep.copy(alpha = alpha)
                ),
                startY = lipTopY,
                endY = lipArchControlY
            )
        )

        // Нижняя окантовка верхней губы и блик, полностью повторяющие иконку
        paths.upperLipHighlightPath.reset()
        paths.upperLipHighlightPath.moveTo(-mouthHalfWidth * 1.02f, lipCornerY)
        paths.upperLipHighlightPath.quadraticTo(0f, lipArchControlY, mouthHalfWidth * 1.02f, lipCornerY)
        drawPath(
            path = paths.upperLipHighlightPath,
            color = EmeraldDarkOutline.copy(alpha = alpha),
            style = Stroke(width = r * 0.048f, cap = StrokeCap.Round)
        )

        // Ноздри поверх верхней губы
        drawOval(
            color = EmeraldDarkOutline.copy(alpha = 0.85f * alpha),
            topLeft = Offset(-r * 0.16f, -r * 0.08f),
            size = Size(r * 0.07f, r * 0.05f)
        )
        drawOval(
            color = EmeraldDarkOutline.copy(alpha = 0.85f * alpha),
            topLeft = Offset(r * 0.09f, -r * 0.08f),
            size = Size(r * 0.07f, r * 0.05f)
        )
    }
}

/**
 * Отрисовка золотой монетки с 3D-эффектом переворота в воздухе (ребро, ободок, блик,
 * знак рубля с короной на аверсе и чеканная золотая жаба Давида на реверсе).
 */
private fun DrawScope.drawSpinningGoldCoin(
    paths: SplashReusablePaths,
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    spinAngleRad: Float,
    tiltDegrees: Float
) {
    // Проекция 3D-переворота: cos(spinAngleRad) сжимает вертикальный радиус монетки
    val flipCos = cos(spinAngleRad)
    val flipSin = sin(spinAngleRad)
    val isFrontFace = flipCos >= 0f
    val verticalScale = max(abs(flipCos), 0.13f)
    val edgeThickness = baseRadius * 0.19f * (1f - abs(flipCos))

    withTransform({
        translate(left = centerX, top = centerY)
        rotate(degrees = tiltDegrees, pivot = Offset.Zero)
    }) {
        val rx = baseRadius
        val ry = baseRadius * verticalScale

        // 1. Мягкое золотое свечение вокруг монетки
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GoldPrimary.copy(alpha = 0.34f),
                    GoldMid.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset.Zero,
                radius = baseRadius * 1.65f
            ),
            radius = baseRadius * 1.65f,
            center = Offset.Zero
        )

        // 2. Объемное золотое ребро (гурт) монетки при 3D-перевороте
        val edgeShiftY = if (flipSin >= 0f) edgeThickness else -edgeThickness
        if (abs(edgeShiftY) > 0.5f) {
            drawOval(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        GoldEdgeDark,
                        GoldShadow,
                        GoldMid,
                        GoldShadow,
                        GoldEdgeDark
                    ),
                    startX = -rx,
                    endX = rx
                ),
                topLeft = Offset(-rx, -ry + edgeShiftY),
                size = Size(rx * 2f, ry * 2f)
            )
        }

        // 3. Внешний темный контур монетки
        drawOval(
            color = GoldEdgeDark,
            topLeft = Offset(-rx, -ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = baseRadius * 0.06f)
        )

        // 4. Основной золотой диск (внешний ободок)
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(GoldRimLight, GoldPrimary, GoldMid, GoldDeep),
                start = Offset(-rx * 0.7f, -ry * 0.7f),
                end = Offset(rx * 0.7f, ry * 0.7f)
            ),
            topLeft = Offset(-rx, -ry),
            size = Size(rx * 2f, ry * 2f)
        )

        // 5. Внутреннее поле монетки (чеканка)
        val innerRx = rx * 0.78f
        val innerRy = ry * 0.78f
        drawOval(
            brush = Brush.linearGradient(
                colors = listOf(GoldDeep, GoldMid, GoldPrimary, GoldRimLight),
                start = Offset(-innerRx * 0.8f, -innerRy * 0.8f),
                end = Offset(innerRx * 0.8f, innerRy * 0.8f)
            ),
            topLeft = Offset(-innerRx, -innerRy),
            size = Size(innerRx * 2f, innerRy * 2f)
        )
        drawOval(
            color = GoldEdgeDark.copy(alpha = 0.55f),
            topLeft = Offset(-innerRx, -innerRy),
            size = Size(innerRx * 2f, innerRy * 2f),
            style = Stroke(width = baseRadius * 0.035f)
        )

        // 6. Чеканная эмблема на грани (видна, когда монетка не повернута совсем ребром)
        if (verticalScale > 0.24f) {
            withTransform({
                scale(scaleX = 1f, scaleY = verticalScale, pivot = Offset.Zero)
            }) {
                if (isFrontFace) {
                    // АВЕРС: Корона и символ рубля (₽)
                    drawEmbossedRubleAndCrown(paths = paths, r = baseRadius)
                } else {
                    // РЕВЕРС: Чеканная золотая жаба Давид (барельеф в золоте)
                    drawEmbossedFrogEmblem(paths = paths, r = baseRadius)
                }
            }
        }

        // 7. Диагональный глянцевый блик по монетке
        drawOval(
            color = Color.White.copy(alpha = 0.38f),
            topLeft = Offset(-rx * 0.55f, -ry * 0.72f),
            size = Size(rx * 0.62f, ry * 0.36f)
        )
    }
}

/**
 * Аверс монетки: чеканная корона и знак рубля ₽.
 */
private fun DrawScope.drawEmbossedRubleAndCrown(
    paths: SplashReusablePaths,
    r: Float
) {
    // 1. Маленькая корона в верхней части внутреннего поля
    paths.crownPath.reset()
    paths.crownPath.moveTo(-r * 0.24f, -r * 0.34f)
    paths.crownPath.lineTo(-r * 0.29f, -r * 0.52f)
    paths.crownPath.lineTo(-r * 0.11f, -r * 0.42f)
    paths.crownPath.lineTo(0f, -r * 0.58f)
    paths.crownPath.lineTo(r * 0.11f, -r * 0.42f)
    paths.crownPath.lineTo(r * 0.29f, -r * 0.52f)
    paths.crownPath.lineTo(r * 0.24f, -r * 0.34f)
    paths.crownPath.close()

    drawPath(
        path = paths.crownPath,
        color = GoldEdgeDark.copy(alpha = 0.65f),
        style = Stroke(width = r * 0.04f, join = StrokeJoin.Round)
    )
    drawPath(
        path = paths.crownPath,
        color = GoldRimLight
    )

    // 2. Рельефный знак рубля ₽ в центре
    val strokeW = r * 0.11f
    paths.rublePath.reset()
    // Вертикальная мачта
    paths.rublePath.moveTo(-r * 0.12f, -r * 0.22f)
    paths.rublePath.lineTo(-r * 0.12f, r * 0.44f)
    // Полукруг головы "Р"
    paths.rublePath.moveTo(-r * 0.24f, -r * 0.22f)
    paths.rublePath.lineTo(r * 0.06f, -r * 0.22f)
    paths.rublePath.cubicTo(
        r * 0.28f,
        -r * 0.22f,
        r * 0.28f,
        r * 0.08f,
        r * 0.06f,
        r * 0.08f
    )
    paths.rublePath.lineTo(-r * 0.24f, r * 0.08f)
    // Нижняя горизонтальная перекладина
    paths.rublePath.moveTo(-r * 0.26f, r * 0.23f)
    paths.rublePath.lineTo(r * 0.14f, r * 0.23f)

    // Тень чеканки
    withTransform({
        translate(left = r * 0.025f, top = r * 0.03f)
    }) {
        drawPath(
            path = paths.rublePath,
            color = GoldEdgeDark.copy(alpha = 0.72f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    // Светлое золото чеканки
    drawPath(
        path = paths.rublePath,
        color = GoldRimLight,
        style = Stroke(width = strokeW * 0.88f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * Реверс монетки: чеканный золотой барельеф жабы Давида (виден при перевороте монетки).
 */
private fun DrawScope.drawEmbossedFrogEmblem(
    paths: SplashReusablePaths,
    r: Float
) {
    val frogScale = r * 0.56f
    val eyeOffsetX = frogScale * 0.54f
    val eyeOffsetY = -frogScale * 0.42f
    val eyeRadius = frogScale * 0.34f

    // Силуэт головы жабы для чеканки
    paths.coinFrogHeadPath.reset()
    paths.coinFrogHeadPath.moveTo(-frogScale * 0.96f, frogScale * 0.14f)
    paths.coinFrogHeadPath.cubicTo(
        -frogScale * 1.02f,
        -frogScale * 0.46f,
        -frogScale * 0.52f,
        -frogScale * 0.56f,
        0f,
        -frogScale * 0.56f
    )
    paths.coinFrogHeadPath.cubicTo(
        frogScale * 0.52f,
        -frogScale * 0.56f,
        frogScale * 1.02f,
        -frogScale * 0.46f,
        frogScale * 0.96f,
        frogScale * 0.14f
    )
    paths.coinFrogHeadPath.cubicTo(
        frogScale * 1.02f,
        frogScale * 0.68f,
        frogScale * 0.58f,
        frogScale * 0.84f,
        0f,
        frogScale * 0.84f
    )
    paths.coinFrogHeadPath.cubicTo(
        -frogScale * 0.58f,
        frogScale * 0.84f,
        -frogScale * 1.02f,
        frogScale * 0.68f,
        -frogScale * 0.96f,
        frogScale * 0.14f
    )
    paths.coinFrogHeadPath.close()

    // 1. Глубокая тень барельефа (смещена вправо-вниз)
    withTransform({
        translate(left = r * 0.03f, top = r * 0.035f)
    }) {
        drawCircle(
            color = GoldEdgeDark.copy(alpha = 0.78f),
            radius = eyeRadius,
            center = Offset(-eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            color = GoldEdgeDark.copy(alpha = 0.78f),
            radius = eyeRadius,
            center = Offset(eyeOffsetX, eyeOffsetY)
        )
        drawPath(
            path = paths.coinFrogHeadPath,
            color = GoldEdgeDark.copy(alpha = 0.78f)
        )
    }

    // 2. Светлый верхний блик барельефа (смещен влево-вверх)
    withTransform({
        translate(left = -r * 0.02f, top = -r * 0.025f)
    }) {
        drawCircle(
            color = GoldRimLight,
            radius = eyeRadius,
            center = Offset(-eyeOffsetX, eyeOffsetY)
        )
        drawCircle(
            color = GoldRimLight,
            radius = eyeRadius,
            center = Offset(eyeOffsetX, eyeOffsetY)
        )
        drawPath(
            path = paths.coinFrogHeadPath,
            color = GoldRimLight
        )
    }

    // 3. Основной золотой барельеф головы жабы
    val reliefBrush = Brush.linearGradient(
        colors = listOf(GoldRimLight, GoldPrimary, GoldMid),
        start = Offset(-frogScale, -frogScale),
        end = Offset(frogScale, frogScale)
    )
    drawCircle(
        brush = reliefBrush,
        radius = eyeRadius,
        center = Offset(-eyeOffsetX, eyeOffsetY)
    )
    drawCircle(
        brush = reliefBrush,
        radius = eyeRadius,
        center = Offset(eyeOffsetX, eyeOffsetY)
    )
    drawPath(
        path = paths.coinFrogHeadPath,
        brush = reliefBrush
    )

    // Контур чеканки головы
    drawPath(
        path = paths.coinFrogHeadPath,
        color = GoldEdgeDark.copy(alpha = 0.68f),
        style = Stroke(width = r * 0.035f, join = StrokeJoin.Round)
    )

    // 4. Чеканные глаза (ободок + зрачок + золотой блик)
    val irisR = eyeRadius * 0.64f
    val pupilR = eyeRadius * 0.34f
    // Левый глаз
    drawCircle(
        color = GoldEdgeDark.copy(alpha = 0.75f),
        radius = irisR,
        center = Offset(-eyeOffsetX, eyeOffsetY),
        style = Stroke(width = r * 0.032f)
    )
    drawCircle(
        color = GoldEdgeDark.copy(alpha = 0.85f),
        radius = pupilR,
        center = Offset(-eyeOffsetX, eyeOffsetY)
    )
    drawCircle(
        color = GoldRimLight,
        radius = pupilR * 0.42f,
        center = Offset(-eyeOffsetX - pupilR * 0.3f, eyeOffsetY - pupilR * 0.3f)
    )

    // Правый глаз
    drawCircle(
        color = GoldEdgeDark.copy(alpha = 0.75f),
        radius = irisR,
        center = Offset(eyeOffsetX, eyeOffsetY),
        style = Stroke(width = r * 0.032f)
    )
    drawCircle(
        color = GoldEdgeDark.copy(alpha = 0.85f),
        radius = pupilR,
        center = Offset(eyeOffsetX, eyeOffsetY)
    )
    drawCircle(
        color = GoldRimLight,
        radius = pupilR * 0.42f,
        center = Offset(eyeOffsetX - pupilR * 0.3f, eyeOffsetY - pupilR * 0.3f)
    )

    // 5. Чеканные ноздри
    drawCircle(
        color = GoldEdgeDark.copy(alpha = 0.78f),
        radius = r * 0.026f,
        center = Offset(-frogScale * 0.14f, -frogScale * 0.02f)
    )
    drawCircle(
        color = GoldEdgeDark.copy(alpha = 0.78f),
        radius = r * 0.026f,
        center = Offset(frogScale * 0.14f, -frogScale * 0.02f)
    )

    // 6. Фирменная широкая улыбка Давида (вдавленная линия с нижним золотым бликом)
    paths.coinFrogSmilePath.reset()
    paths.coinFrogSmilePath.moveTo(-frogScale * 0.56f, frogScale * 0.22f)
    paths.coinFrogSmilePath.quadraticTo(0f, frogScale * 0.56f, frogScale * 0.56f, frogScale * 0.22f)

    withTransform({
        translate(left = 0f, top = r * 0.022f)
    }) {
        drawPath(
            path = paths.coinFrogSmilePath,
            color = GoldRimLight.copy(alpha = 0.85f),
            style = Stroke(width = r * 0.042f, cap = StrokeCap.Round)
        )
    }
    drawPath(
        path = paths.coinFrogSmilePath,
        color = GoldEdgeDark.copy(alpha = 0.82f),
        style = Stroke(width = r * 0.045f, cap = StrokeCap.Round)
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

// ============================================================================
// ДЕМО-ПРЕВЬЮ ДЛЯ ANDROID STUDIO
// ============================================================================
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun SplashDemoPreview() {
    var triggerKey by remember { mutableIntStateOf(0) }
    var isExiting by remember(triggerKey) { mutableStateOf(true) }

    GrowthChartSplashScreen(
        isExiting = isExiting,
        onExitFinished = { isExiting = false }
    ) {
        // Имитация основного экрана приложения под шторкой
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070D19))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DAVID FINANCE AI",
                color = EmeraldLight,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Основной экран открыт падающей монеткой",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFF111C30), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "Общий баланс",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "248 500 ₽",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = { triggerKey++ },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(width = 220.dp, height = 50.dp)
            ) {
                Text(
                    text = "Повторить анимацию",
                    color = Color(0xFF04130E),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
