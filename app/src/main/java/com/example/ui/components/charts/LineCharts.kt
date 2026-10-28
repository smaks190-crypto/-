package com.example.ui.components.charts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Полностью отрефакторенная карточка графика динамики доходов и расходов на главной странице
 * в дизайн-системе Obsidian Neo-Bank.
 *
 * Все вычисления вынесены в [HomeChartDataEngine]:
 * - Единый источник истины для выбранного периода (Неделя / Месяц / Год), подзаголовка, сумм и точек.
 * - Полная синхронизация с выбранным месяцем и годом главного экрана (отсутствие рассинхронизации).
 * - Отображение как расходов, так и доходов с плавной морфинг-анимацией и интерактивным выбором точки.
 */
@Composable
fun ExpenseDynamicsAreaChartCard(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
    title: String = "Динамика бюджета",
    selectedMonthIdx: Int = Calendar.getInstance().get(Calendar.MONTH),
    selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    referenceDateIso: String? = null,
    initialPeriod: HomeChartPeriod = HomeChartPeriod.MONTH,
    onClick: (() -> Unit)? = null
) {
    var selectedPeriodCode by rememberSaveable { mutableStateOf(initialPeriod.code) }
    val activePeriod = remember(selectedPeriodCode) {
        HomeChartPeriod.fromLabelOrCode(selectedPeriodCode)
    }

    var selectedPointIdx by rememberSaveable(activePeriod.code, selectedMonthIdx, selectedYear) {
        mutableStateOf<Int?>(null)
    }

    val chartState = remember(transactions, activePeriod, selectedMonthIdx, selectedYear, referenceDateIso) {
        HomeChartDataEngine.buildHomeChartState(
            transactions = transactions,
            period = activePeriod,
            selectedMonthIdx = selectedMonthIdx,
            selectedYear = selectedYear,
            referenceDateIso = referenceDateIso
        )
    }

    val targetExpensePoints = remember(chartState) {
        chartState.points.map { it.expense }
    }
    val targetIncomePoints = remember(chartState) {
        chartState.points.map { it.income }
    }

    // Clamp or clear selectedPointIdx if point count changed
    val activeSelectedPoint: HomeChartPoint? = remember(selectedPointIdx, chartState.points) {
        val idx = selectedPointIdx
        if (idx != null && idx in chartState.points.indices) chartState.points[idx] else null
    }

    var oldExpensePoints by remember { mutableStateOf<List<Double>>(emptyList()) }
    var currentExpensePoints by remember { mutableStateOf<List<Double>>(emptyList()) }
    val morphAnim = remember { Animatable(1f) }

    LaunchedEffect(targetExpensePoints) {
        oldExpensePoints = if (currentExpensePoints.isNotEmpty()) {
            currentExpensePoints
        } else {
            List(targetExpensePoints.size) { 0.0 }
        }
        currentExpensePoints = targetExpensePoints
        morphAnim.snapTo(0f)
        morphAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
    }

    val animatedYMax by animateFloatAsState(
        targetValue = chartState.yAxisMax.toFloat(),
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "home_chart_y_max"
    )
    val effectiveMaxVal = animatedYMax.toDouble().coerceAtLeast(1.0)

    val displayedExpense = activeSelectedPoint?.expense ?: chartState.totalExpense
    val displayedIncome = activeSelectedPoint?.income ?: chartState.totalIncome
    val displayedSubtitle = activeSelectedPoint?.fullDateTitle ?: chartState.periodSubtitle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ObsidianSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(18.dp)
            .testTag("home_dynamics_chart_card")
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. ВЕРХНЯЯ ПАНЕЛЬ: Заголовок, синхронизированный подзаголовок периода и переключатель Неделя / Месяц / Год
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo500.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = Indigo400,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        text = title.uppercase(),
                        color = Slate300,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayedSubtitle,
                        color = if (activeSelectedPoint != null) Emerald400 else Slate400,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("home_chart_period_subtitle")
                    )
                }
            }

            // 3-позиционный переключатель Неделя / Месяц / Год в стиле Obsidian Neo-Bank
            val periodIndex = HomeChartPeriod.entries.indexOf(activePeriod).coerceAtLeast(0)
            val animatedIndex by animateFloatAsState(
                targetValue = periodIndex.toFloat(),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "home_chart_period_pill"
            )

            BoxWithConstraints(
                modifier = Modifier
                    .width(168.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                val pillWidth = maxWidth / 3f

                Box(
                    modifier = Modifier
                        .offset(x = pillWidth * animatedIndex)
                        .width(pillWidth)
                        .fillMaxHeight()
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(9.dp),
                            clip = false,
                            ambientColor = Emerald400,
                            spotColor = Emerald400
                        )
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Emerald400.copy(alpha = 0.24f),
                                    Indigo500.copy(alpha = 0.24f)
                                )
                            ),
                            shape = RoundedCornerShape(9.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Emerald400.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(9.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeChartPeriod.entries.forEach { periodItem ->
                        val isSelected = periodItem == activePeriod
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Slate400,
                            animationSpec = tween(180),
                            label = "period_tab_text"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedPeriodCode = periodItem.code
                                    selectedPointIdx = null
                                }
                                .testTag("home_chart_period_${periodItem.code.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = periodItem.labelRu,
                                color = textColor,
                                fontSize = 10.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 2. СИНХРОНИЗИРОВАННАЯ СТРОКА ИТОГОВ ПЕРИОДА (ИЛИ ВЫБРАННОЙ ТОЧКИ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkBg.copy(alpha = 0.65f))
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Rose500)
                )
                Text(
                    text = "Расходы:",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily
                )
                Text(
                    text = HomeChartDataEngine.formatCompactChartAmount(displayedExpense),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("home_chart_expense_total")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald400)
                )
                Text(
                    text = "Доходы:",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily
                )
                Text(
                    text = "+${HomeChartDataEngine.formatCompactChartAmount(displayedIncome)}",
                    color = Emerald400,
                    fontSize = 13.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("home_chart_income_total")
                )
            }
        }

        // 3. CANVAS ГРАФИКА С ВЕКТОРНЫМ МОРФИНГОМ И ОТОБРАЖЕНИЕМ ДОХОДОВ/РАСХОДОВ
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .testTag("home_chart_canvas_box")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(chartState.points) {
                        detectTapGestures { offset ->
                            val n = chartState.points.size
                            if (n > 1) {
                                val stepX = size.width / (n - 1).toFloat()
                                val idx = (offset.x / stepX).roundToInt().coerceIn(0, n - 1)
                                selectedPointIdx = if (selectedPointIdx == idx) null else idx
                            } else if (n == 1) {
                                selectedPointIdx = if (selectedPointIdx == 0) null else 0
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val topPad = 20.dp.toPx()
                val bottomPad = 10.dp.toPx()
                val usableH = (h - topPad - bottomPad).coerceAtLeast(20f)
                val baselineY = h - bottomPad

                // Горизонтальные направляющие сетки (0%, 50%, 100%)
                val dashGrid = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                for (level in 0..2) {
                    val gy = topPad + usableH * (level / 2f)
                    drawLine(
                        color = Slate800.copy(alpha = 0.7f),
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (level == 2) null else dashGrid
                    )
                }

                val n = currentExpensePoints.size
                if (n > 1) {
                    val stepX = w / (n - 1).toFloat()
                    val morphProgress = morphAnim.value

                    // Интерполированные точки расходов
                    val expOffsets = currentExpensePoints.mapIndexed { index, targetVal ->
                        val x = index * stepX
                        val prevVal = if (oldExpensePoints.size > 1) {
                            val normPos = index.toFloat() / (n - 1).toFloat()
                            val exactIdx = normPos * (oldExpensePoints.size - 1)
                            val i0 = exactIdx.toInt().coerceIn(0, oldExpensePoints.lastIndex)
                            val i1 = (i0 + 1).coerceIn(0, oldExpensePoints.lastIndex)
                            val frac = exactIdx - i0
                            oldExpensePoints[i0] + frac * (oldExpensePoints[i1] - oldExpensePoints[i0])
                        } else 0.0

                        val morphed = prevVal + morphProgress * (targetVal - prevVal)
                        val ratioY = (morphed / effectiveMaxVal).toFloat().coerceIn(0f, 1f)
                        val pxH = if (morphed > 0.0) maxOf(ratioY * usableH, 3.dp.toPx()) else 0f
                        Offset(x, baselineY - pxH)
                    }

                    // Точки доходов (если есть доходы за период)
                    val incOffsets = targetIncomePoints.mapIndexed { index, incVal ->
                        val x = index * stepX
                        val ratioY = ((incVal * morphProgress) / effectiveMaxVal).toFloat().coerceIn(0f, 1f)
                        val pxH = if (incVal > 0.0) maxOf(ratioY * usableH, 3.dp.toPx()) else 0f
                        Offset(x, baselineY - pxH)
                    }

                    // Если есть доходы — рисуем плавную изумрудную кривую доходов
                    if (chartState.hasIncomes && incOffsets.size > 1) {
                        val incStrokePath = buildSmoothBezierLinePath(incOffsets)
                        val incAreaPath = buildSmoothBezierAreaPath(incOffsets, baselineY)

                        drawPath(
                            path = incAreaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Emerald400.copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                startY = topPad,
                                endY = baselineY
                            ),
                            style = Fill
                        )
                        drawPath(
                            path = incStrokePath,
                            color = Emerald400.copy(alpha = 0.72f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            )
                        )
                    }

                    // Основная кривая и заливка расходов
                    val expAreaPath = buildSmoothBezierAreaPath(expOffsets, baselineY)
                    val expStrokePath = buildSmoothBezierLinePath(expOffsets)

                    val strokeBrush = if (chartState.isDeficit) {
                        Brush.verticalGradient(
                            colors = listOf(Rose500, Indigo500, Emerald400),
                            startY = topPad,
                            endY = baselineY
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(Indigo400, Color(0xFF38BDF8), Emerald400),
                            startY = topPad,
                            endY = baselineY
                        )
                    }

                    val areaBrush = if (chartState.isDeficit) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Rose500.copy(alpha = 0.30f),
                                Indigo500.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            startY = topPad,
                            endY = baselineY
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Indigo500.copy(alpha = 0.28f),
                                Emerald400.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startY = topPad,
                            endY = baselineY
                        )
                    }

                    drawPath(path = expAreaPath, brush = areaBrush, style = Fill)
                    drawPath(
                        path = expStrokePath,
                        brush = strokeBrush,
                        style = Stroke(
                            width = 3.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Выделенная пользователем точка или пиковая точка периода
                    val highlightIdx = selectedPointIdx ?: chartState.peakExpensePointIndex
                    if (highlightIdx != null && highlightIdx in expOffsets.indices) {
                        val pt = expOffsets[highlightIdx]
                        val pointData = chartState.points[highlightIdx]
                        val isUserSelected = selectedPointIdx == highlightIdx
                        val dotColor = if (pointData.expense >= pointData.income && pointData.expense > 0.0) {
                            Rose500
                        } else {
                            Emerald400
                        }

                        if (isUserSelected) {
                            drawLine(
                                color = Indigo400.copy(alpha = 0.65f),
                                start = Offset(pt.x, topPad * 0.3f),
                                end = Offset(pt.x, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }

                        drawCircle(
                            color = dotColor.copy(alpha = 0.32f),
                            radius = (if (isUserSelected) 8.dp else 6.dp).toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = dotColor,
                            radius = (if (isUserSelected) 4.5.dp else 3.5.dp).toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.8.dp.toPx(),
                            center = pt
                        )

                        // Всплывающий бейдж суммы над выбранной или пиковой точкой
                        if (isUserSelected || pointData.expense > 0.0) {
                            val badgeText = if (pointData.expense > 0.0) {
                                HomeChartDataEngine.formatCompactChartAmount(pointData.expense)
                            } else {
                                "+${HomeChartDataEngine.formatCompactChartAmount(pointData.income)}"
                            }
                            val textPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 9.5.dp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.create(
                                    android.graphics.Typeface.MONOSPACE,
                                    android.graphics.Typeface.BOLD
                                )
                            }
                            val textW = textPaint.measureText(badgeText)
                            val pillW = textW + 10.dp.toPx()
                            val pillH = 16.dp.toPx()
                            val pillX = (pt.x - pillW / 2f).coerceIn(2.dp.toPx(), w - pillW - 2.dp.toPx())
                            val pillY = (pt.y - pillH - 6.dp.toPx()).coerceIn(1.dp.toPx(), baselineY - pillH - 2.dp.toPx())

                            drawRoundRect(
                                color = Color(0xFF0B1120),
                                topLeft = Offset(pillX, pillY),
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(5.dp.toPx())
                            )
                            drawRoundRect(
                                color = dotColor.copy(alpha = 0.85f),
                                topLeft = Offset(pillX, pillY),
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(5.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                badgeText,
                                pillX + pillW / 2f,
                                pillY + pillH / 2f + 3.2.dp.toPx(),
                                textPaint
                            )
                        }
                    }
                }
            }

            if (!chartState.hasAnyData) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBg.copy(alpha = 0.85f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("home_chart_empty_state")
                ) {
                    Text(
                        text = "Нет операций за ${chartState.periodSubtitle.lowercase()}",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 4. ПОДПИСИ ОСИ X (Синхронизированные с выбранным периодом и количеством дней в месяце)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            chartState.axisTicks.forEach { tick ->
                val isSelectedTick = selectedPointIdx == tick.pointIndex
                val labelColor = when {
                    isSelectedTick -> Emerald400
                    tick.isWeekendOrAccent -> Rose500.copy(alpha = 0.85f)
                    else -> Slate500
                }
                Text(
                    text = tick.label,
                    color = labelColor,
                    fontSize = 10.sp,
                    fontWeight = if (isSelectedTick || tick.isWeekendOrAccent) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = SpaceGroteskFontFamily
                )
            }
        }
    }
}

private fun buildSmoothBezierLinePath(points: List<Offset>): Path {
    return Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val dx = p2.x - p1.x
            cubicTo(
                p1.x + dx * 0.35f,
                p1.y,
                p2.x - dx * 0.35f,
                p2.y,
                p2.x,
                p2.y
            )
        }
    }
}

private fun buildSmoothBezierAreaPath(points: List<Offset>, baselineY: Float): Path {
    return Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points.first().x, baselineY)
        lineTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val dx = p2.x - p1.x
            cubicTo(
                p1.x + dx * 0.35f,
                p1.y,
                p2.x - dx * 0.35f,
                p2.y,
                p2.x,
                p2.y
            )
        }
        lineTo(points.last().x, baselineY)
        close()
    }
}

/**
 * Функция рендеринга 'renderChartMessage' для динамических линейных графиков трат в сообщениях ассистента.
 * Выполнена в единой дизайн-системе Obsidian Neo-Bank.
 */
@Composable
fun renderChartMessage(
    dataPoints: List<Double>,
    labels: List<String> = emptyList(),
    title: String = "Динамика трат",
    totalAmount: Double? = null,
    modifier: Modifier = Modifier
) {
    var selectedPointIdx by remember { mutableStateOf<Int?>(null) }

    val safeData = if (dataPoints.isEmpty()) listOf(0.0, 0.0) else dataPoints
    val safeLabels = if (labels.isEmpty()) {
        val days = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
        if (safeData.size == 7) days else List(safeData.size) { "${it + 1}" }
    } else labels

    val computedTotal = totalAmount ?: safeData.sum()
    val maxVal = (safeData.maxOrNull() ?: 1.0).coerceAtLeast(10.0) * 1.15

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(safeData) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }
    val progress = animProgress.value

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, GlassBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Indigo500.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = Indigo400,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkBg,
                    border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.35f))
                ) {
                    val activeAmount = selectedPointIdx?.let { safeData.getOrNull(it) } ?: computedTotal
                    Text(
                        text = formatShortCurrency(activeAmount),
                        color = Emerald400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGroteskFontFamily,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(safeData) {
                            detectTapGestures { offset ->
                                val n = safeData.size
                                if (n > 1) {
                                    val stepX = size.width / (n - 1)
                                    val idx = (offset.x / stepX).roundToInt().coerceIn(0, n - 1)
                                    selectedPointIdx = if (selectedPointIdx == idx) null else idx
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val n = safeData.size

                    if (n > 1) {
                        val stepX = w / (n - 1)
                        val baselineY = h - 6.dp.toPx()

                        val points = safeData.mapIndexed { index, value ->
                            val x = index * stepX
                            val rawY = ((value / maxVal) * (h - 24.dp.toPx())).toFloat() * progress
                            val y = baselineY - rawY.coerceAtLeast(2.dp.toPx())
                            Offset(x, y)
                        }

                        val areaPath = buildSmoothBezierAreaPath(points, baselineY)
                        val strokePath = buildSmoothBezierLinePath(points)

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Indigo500.copy(alpha = 0.32f),
                                    Emerald400.copy(alpha = 0.12f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = h
                            ),
                            style = Fill
                        )

                        drawPath(
                            path = strokePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(Emerald400, Indigo500, Rose500)
                            ),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        selectedPointIdx?.let { sIdx ->
                            if (sIdx in points.indices) {
                                val selPt = points[sIdx]
                                val valAmt = safeData[sIdx]
                                val dotColor = if (valAmt > (computedTotal / n)) Rose500 else Emerald400

                                drawLine(
                                    color = Indigo400.copy(alpha = 0.6f),
                                    start = Offset(selPt.x, 0f),
                                    end = Offset(selPt.x, h),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                                )
                                drawCircle(
                                    color = dotColor.copy(alpha = 0.35f),
                                    radius = 8.dp.toPx(),
                                    center = selPt
                                )
                                drawCircle(
                                    color = dotColor,
                                    radius = 4.dp.toPx(),
                                    center = selPt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = selPt
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                safeLabels.forEachIndexed { idx, label ->
                    val isSelected = selectedPointIdx == idx
                    Text(
                        text = label,
                        color = if (isSelected) Emerald400 else Slate400,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = SpaceGroteskFontFamily
                    )
                }
            }
        }
    }
}

@Composable
fun RenderChartMessage(
    dataPoints: List<Double>,
    labels: List<String> = emptyList(),
    title: String = "Динамика трат",
    totalAmount: Double? = null,
    modifier: Modifier = Modifier
) {
    renderChartMessage(
        dataPoints = dataPoints,
        labels = labels,
        title = title,
        totalAmount = totalAmount,
        modifier = modifier
    )
}
