package com.example.ui.components.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.formatShortCurrency
import com.example.ui.components.formatFullCurrency

@Composable
fun MonthlyBarChart(
    months: List<String>,
    incomeValues: List<Double>,
    expenseValues: List<Double>,
    modifier: Modifier = Modifier
) {
    val maxVal = (incomeValues + expenseValues).maxOrNull()?.coerceAtLeast(100.0) ?: 100.0

    var chartProgressTarget by remember(months, incomeValues, expenseValues) { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = chartProgressTarget,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "bar_chart_anim"
    )

    LaunchedEffect(months, incomeValues, expenseValues) {
        chartProgressTarget = 1f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Emerald400)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(3.dp), ambientColor = Emerald400, spotColor = Emerald400)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Доходы", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Rose500)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(3.dp), ambientColor = Rose500, spotColor = Rose500)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Расходы", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val count = months.size
                val groupWidth = canvasWidth / count
                val barWidth = (groupWidth * 0.35f).coerceAtMost(12.dp.toPx())

                // Draw horizontal background grid lines with cyberpunk Slate800 color
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = canvasHeight * (1f - i.toFloat() / gridLines)
                    drawLine(
                        color = Slate800,
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                months.forEachIndexed { i, _ ->
                    val inc = incomeValues.getOrElse(i) { 0.0 }
                    val exp = expenseValues.getOrElse(i) { 0.0 }

                    val incHeight = if (inc > 0) {
                        (((inc / maxVal) * canvasHeight).toFloat() * animatedProgress).coerceAtLeast(6.dp.toPx())
                    } else 0f

                    val expHeight = if (exp > 0) {
                        (((exp / maxVal) * canvasHeight).toFloat() * animatedProgress).coerceAtLeast(6.dp.toPx())
                    } else 0f

                    val groupX = i * groupWidth + (groupWidth - (barWidth * 2 + 2.dp.toPx())) / 2

                    // Income Bar with slight neon glow style
                    if (incHeight > 0) {
                        drawRoundRect(
                            color = Emerald400,
                            topLeft = Offset(groupX, canvasHeight - incHeight),
                            size = Size(barWidth, incHeight),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }

                    // Expense Bar with slight neon glow style
                    if (expHeight > 0) {
                        drawRoundRect(
                            color = Rose500,
                            topLeft = Offset(groupX + barWidth + 2.dp.toPx(), canvasHeight - expHeight),
                            size = Size(barWidth, expHeight),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Month Labels aligned evenly
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            months.forEach { m ->
                val shortName = m.take(3)
                Text(
                    text = shortName.uppercase(),
                    color = Slate400,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
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
    val maxVal = maxOf(income, expense).coerceAtLeast(1.0)
    val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    val df = DecimalFormat("#,##0.##", symbols).apply { isGroupingUsed = true }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkBg.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                val diff = income - expense
                val diffStr = if (diff >= 0) "+${df.format(diff)} ₽" else "${df.format(diff)} ₽"
                Text(
                    text = diffStr,
                    color = if (diff >= 0) Emerald400 else Rose500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Income bar row
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ДОХОДЫ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text("${df.format(income)} ₽", color = Emerald400, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Slate900)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (income / maxVal).toFloat().coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Emerald400.copy(alpha = 0.7f), Emerald400)
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
                    Text("РАСХОДЫ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Text("${df.format(expense)} ₽", color = Rose500, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Slate900)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (expense / maxVal).toFloat().coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Rose500.copy(alpha = 0.7f), Rose500)
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
    val formattedPct = "$diffSign${Math.round(diffPct)}%"

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
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Change point badge
                Surface(
                    color = pointColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, pointColor.copy(alpha = 0.4f)),
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
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Text(
                text = "${formatShortCurrency(prevVal)} → ${formatShortCurrency(currentVal)}",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Visual Canvas Chart with glowing point on top of current value
        var animProgress by remember { mutableFloatStateOf(0f) }
        val animatedProgress by animateFloatAsState(
            targetValue = animProgress,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "metric_anim"
        )
        LaunchedEffect(prevVal, currentVal) {
            animProgress = 1f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .background(DarkBg.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
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

                // Baseline
                drawLine(
                    color = Slate800,
                    start = Offset(0f, baselineY),
                    end = Offset(w, baselineY),
                    strokeWidth = 1.dp.toPx()
                )

                // Previous Bar
                val prevTopY = baselineY - prevH.coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = prevColor,
                    topLeft = Offset(prevX, prevTopY),
                    size = Size(barWidth, prevH.coerceAtLeast(4.dp.toPx())),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Current Bar
                val curTopY = baselineY - curH.coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = curColor,
                    topLeft = Offset(curX, curTopY),
                    size = Size(barWidth, curH.coerceAtLeast(4.dp.toPx())),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Connecting trend line
                val p1 = Offset(prevX + barWidth / 2, prevTopY)
                val p2 = Offset(curX + barWidth / 2, curTopY)

                drawLine(
                    color = pointColor.copy(alpha = 0.7f),
                    start = p1,
                    end = p2,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                )

                // Glowing Point
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
