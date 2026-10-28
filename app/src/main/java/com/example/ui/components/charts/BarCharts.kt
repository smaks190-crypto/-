package com.example.ui.components.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.roundToInt

@Composable
fun MonthlyBarChart(
    months: List<String>,
    incomeValues: List<Double>,
    expenseValues: List<Double>,
    modifier: Modifier = Modifier
) {
    val safeMonths = if (months.isEmpty()) HomeChartDataEngine.MonthsNominativeRu else months
    val maxVal = remember(incomeValues, expenseValues) {
        val rawMax = (incomeValues + expenseValues).filter { !it.isNaN() && !it.isInfinite() }.maxOrNull() ?: 0.0
        if (rawMax <= 0.0) 100.0 else rawMax * 1.12
    }

    var selectedBarIdx by remember(safeMonths, incomeValues, expenseValues) { mutableStateOf<Int?>(null) }
    var chartProgressTarget by remember(safeMonths, incomeValues, expenseValues) { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = chartProgressTarget,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bar_chart_anim"
    )

    LaunchedEffect(safeMonths, incomeValues, expenseValues) {
        chartProgressTarget = 1f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeIdx = selectedBarIdx
            if (activeIdx != null && activeIdx in safeMonths.indices) {
                val mName = safeMonths[activeIdx]
                val inc = incomeValues.getOrElse(activeIdx) { 0.0 }
                val exp = expenseValues.getOrElse(activeIdx) { 0.0 }
                Text(
                    text = "$mName: +${HomeChartDataEngine.formatCompactChartAmount(inc)} / −${HomeChartDataEngine.formatCompactChartAmount(exp)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "Нажмите на месяц для деталей",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald400)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Доходы",
                    color = Slate300,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Rose500)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Расходы",
                    color = Slate300,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(safeMonths) {
                        detectTapGestures { offset ->
                            val count = safeMonths.size.coerceAtLeast(1)
                            val groupW = size.width / count.toFloat()
                            val idx = (offset.x / groupW).toInt().coerceIn(0, count - 1)
                            selectedBarIdx = if (selectedBarIdx == idx) null else idx
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val count = safeMonths.size.coerceAtLeast(1)
                val groupWidth = canvasWidth / count
                val barWidth = (groupWidth * 0.34f).coerceAtMost(12.dp.toPx()).coerceAtLeast(3.dp.toPx())
                val gap = 2.dp.toPx()

                val gridLines = 3
                for (i in 0..gridLines) {
                    val y = canvasHeight * (1f - i.toFloat() / gridLines)
                    drawLine(
                        color = Slate800.copy(alpha = 0.75f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                safeMonths.forEachIndexed { i, _ ->
                    val inc = incomeValues.getOrElse(i) { 0.0 }.coerceAtLeast(0.0)
                    val exp = expenseValues.getOrElse(i) { 0.0 }.coerceAtLeast(0.0)
                    val isSelected = selectedBarIdx == i

                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.05f),
                            topLeft = Offset(i * groupWidth + 1.dp.toPx(), 0f),
                            size = Size((groupWidth - 2.dp.toPx()).coerceAtLeast(2f), canvasHeight),
                            cornerRadius = CornerRadius(6.dp.toPx())
                        )
                    }

                    val incHeight = if (inc > 0.0) {
                        (((inc / maxVal) * canvasHeight).toFloat() * animatedProgress).coerceAtLeast(5.dp.toPx())
                    } else 0f

                    val expHeight = if (exp > 0.0) {
                        (((exp / maxVal) * canvasHeight).toFloat() * animatedProgress).coerceAtLeast(5.dp.toPx())
                    } else 0f

                    val groupX = i * groupWidth + (groupWidth - (barWidth * 2 + gap)) / 2f

                    if (incHeight > 0f) {
                        drawRoundRect(
                            color = if (isSelected || selectedBarIdx == null) Emerald400 else Emerald400.copy(alpha = 0.55f),
                            topLeft = Offset(groupX, canvasHeight - incHeight),
                            size = Size(barWidth, incHeight),
                            cornerRadius = CornerRadius(3.5.dp.toPx())
                        )
                    }

                    if (expHeight > 0f) {
                        drawRoundRect(
                            color = if (isSelected || selectedBarIdx == null) Rose500 else Rose500.copy(alpha = 0.55f),
                            topLeft = Offset(groupX + barWidth + gap, canvasHeight - expHeight),
                            size = Size(barWidth, expHeight),
                            cornerRadius = CornerRadius(3.5.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            safeMonths.forEachIndexed { idx, m ->
                val isSelected = selectedBarIdx == idx
                val shortName = m.take(3).uppercase()
                Text(
                    text = shortName,
                    color = if (isSelected) Emerald400 else Slate400,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontFamily = SpaceGroteskFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun IncomeExpenseComparisonCard(
    income: Double,
    expense: Double,
    title: String = "Сравнение доходов и расходов",
    modifier: Modifier = Modifier
) {
    val safeIncome = income.coerceAtLeast(0.0)
    val safeExpense = expense.coerceAtLeast(0.0)
    val maxVal = maxOf(safeIncome, safeExpense).coerceAtLeast(1.0)
    val diff = safeIncome - safeExpense

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    color = Slate300,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                val diffPrefix = if (diff >= 0.0) "+" else "−"
                Text(
                    text = "$diffPrefix${HomeChartDataEngine.formatCompactChartAmount(kotlin.math.abs(diff))}",
                    color = if (diff >= 0.0) Emerald400 else Rose500,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SpaceGroteskFontFamily
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Income bar row
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ДОХОДЫ",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "+${formatShortCurrency(safeIncome)}",
                        color = Emerald400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGroteskFontFamily
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                fraction = if (safeIncome > 0.0) (safeIncome / maxVal).toFloat().coerceIn(0.04f, 1f) else 0f
                            )
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Emerald400.copy(alpha = 0.75f), Emerald400)
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expense bar row
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "РАСХОДЫ",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "−${formatShortCurrency(safeExpense)}",
                        color = Rose500,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGroteskFontFamily
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                fraction = if (safeExpense > 0.0) (safeExpense / maxVal).toFloat().coerceIn(0.04f, 1f) else 0f
                            )
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Rose500.copy(alpha = 0.75f), Rose500)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ComparativeMetricRow(
    label: String,
    prevVal: Double,
    currentVal: Double,
    diff: Double,
    diffPct: Double,
    isIncome: Boolean
) {
    val maxVal = maxOf(prevVal, currentVal).coerceAtLeast(1.0)
    val curColor = if (isIncome) Emerald400 else Rose500
    val prevColor = Slate600

    val isGoodChange = if (isIncome) diff >= 0 else diff <= 0
    val pointColor = if (isGoodChange) Emerald400 else Rose500

    val diffSign = if (diff > 0) "+" else ""
    val formattedDiff = "$diffSign${formatShortCurrency(diff)}"
    val formattedPct = "$diffSign${diffPct.roundToInt()}%"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = pointColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, pointColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(pointColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$formattedDiff ($formattedPct)",
                            color = pointColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SpaceGroteskFontFamily
                        )
                    }
                }
            }

            Text(
                text = "${formatShortCurrency(prevVal)} → ${formatShortCurrency(currentVal)}",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = SpaceGroteskFontFamily
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        var animProgress by remember { mutableFloatStateOf(0f) }
        val animatedProgress by animateFloatAsState(
            targetValue = animProgress,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "metric_anim"
        )
        LaunchedEffect(prevVal, currentVal) {
            animProgress = 1f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .background(ObsidianSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val barWidth = 28.dp.toPx()

                val prevH = ((prevVal / maxVal) * (h - 18.dp.toPx())).toFloat() * animatedProgress
                val curH = ((currentVal / maxVal) * (h - 18.dp.toPx())).toFloat() * animatedProgress

                val prevX = w * 0.25f - barWidth / 2
                val curX = w * 0.75f - barWidth / 2
                val baselineY = h - 4.dp.toPx()

                drawLine(
                    color = Slate800,
                    start = Offset(0f, baselineY),
                    end = Offset(w, baselineY),
                    strokeWidth = 1.dp.toPx()
                )

                val prevTopY = baselineY - prevH.coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = prevColor,
                    topLeft = Offset(prevX, prevTopY),
                    size = Size(barWidth, prevH.coerceAtLeast(4.dp.toPx())),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                val curTopY = baselineY - curH.coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = curColor,
                    topLeft = Offset(curX, curTopY),
                    size = Size(barWidth, curH.coerceAtLeast(4.dp.toPx())),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                val p1 = Offset(prevX + barWidth / 2, prevTopY)
                val p2 = Offset(curX + barWidth / 2, curTopY)

                drawLine(
                    color = pointColor.copy(alpha = 0.7f),
                    start = p1,
                    end = p2,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                )

                drawCircle(
                    color = pointColor.copy(alpha = 0.35f),
                    radius = 9.dp.toPx() * animatedProgress,
                    center = p2
                )
                drawCircle(
                    color = pointColor,
                    radius = 5.dp.toPx() * animatedProgress,
                    center = p2
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx() * animatedProgress,
                    center = p2
                )
            }
        }
    }
}
