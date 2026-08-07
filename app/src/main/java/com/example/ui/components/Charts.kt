package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.PathEffect
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import com.example.data.db.TransactionEntity
import java.util.Calendar
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.material.icons.filled.ShowChart

val ChartColors = listOf(
    Color(0xFFF43F5E), // Rose
    Color(0xFF10B981), // Emerald
    Color(0xFF3B82F6), // Blue
    Color(0xFFF59E0B), // Amber
    Color(0xFF8B5CF6), // Purple
    Color(0xFF14B8A6), // Teal
    Color(0xFFEC4899), // Pink
    Color(0xFF64748B)  // Slate
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDoughnutChart(
    categoryAmounts: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    if (categoryAmounts.isEmpty() || categoryAmounts.values.sum() <= 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нет данных за период",
                color = Slate400,
                fontSize = 13.sp
            )
        }
        return
    }

    val total = categoryAmounts.values.sum()
    val entries = categoryAmounts.entries.toList()

    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(categoryAmounts) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }
    val animatedProgress = animProgress.value

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(160.dp).clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(150.dp).clipToBounds()) {
                var startAngle = -90f
                val strokeWidth = 32.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )
                val arcSize = Size(radius * 2, radius * 2)

                entries.forEachIndexed { index, entry ->
                    val fullSweep = ((entry.value / total) * 360f).toFloat()
                    val sweepAngle = Math.max(0f, (fullSweep * animatedProgress) - 2f)
                    val color = ChartColors[index % ChartColors.size]

                    if (sweepAngle > 0f) {
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                    }

                    startAngle += fullSweep * animatedProgress
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Всего",
                    color = Slate400,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                RollingCurrencyText(
                    text = formatShortCurrency(total),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Color Legend
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            entries.forEachIndexed { index, entry ->
                val color = ChartColors[index % ChartColors.size]
                val percent = Math.round((entry.value / total) * 100)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${entry.key} ($percent%)",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

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
                    .clip(RoundedCornerShape(2.dp))
                    .background(Emerald400)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Доходы", color = Slate400, fontSize = 11.sp)

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Rose500)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Расходы", color = Slate400, fontSize = 11.sp)
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

                // Draw horizontal background grid lines
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

                    // Income Bar
                    if (incHeight > 0) {
                        drawRoundRect(
                            color = Emerald400,
                            topLeft = Offset(groupX, canvasHeight - incHeight),
                            size = Size(barWidth, incHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }

                    // Expense Bar
                    if (expHeight > 0) {
                        drawRoundRect(
                            color = Rose500,
                            topLeft = Offset(groupX + barWidth + 2.dp.toPx(), canvasHeight - expHeight),
                            size = Size(barWidth, expHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Month Labels aligned evenly with weight(1f)
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            months.forEach { m ->
                val shortName = m.take(3)
                Text(
                    text = shortName,
                    color = Slate400,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
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

    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = com.example.ui.theme.DarkBg),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.Slate800),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                val diff = income - expense
                val diffStr = if (diff >= 0) "+${df.format(diff)} ₽" else "${df.format(diff)} ₽"
                Text(
                    text = diffStr,
                    color = if (diff >= 0) Emerald400 else Rose500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Income bar row
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Доходы", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("${df.format(income)} ₽", color = Emerald400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(com.example.ui.theme.Slate900)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (income / maxVal).toFloat().coerceIn(0.02f, 1f))
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Emerald400)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expense bar row
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Расходы", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text("${df.format(expense)} ₽", color = Rose500, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(com.example.ui.theme.Slate900)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (expense / maxVal).toFloat().coerceIn(0.02f, 1f))
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Rose500)
                    )
                }
            }
        }
    }
}

fun formatShortCurrency(value: Double): String {
    val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    val df = DecimalFormat("#,##0", symbols).apply {
        isGroupingUsed = true
    }
    return "${df.format(value)}\u00A0₽"
}

fun formatFullCurrency(value: Double): String {
    val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    val df = DecimalFormat("#,##0.##", symbols).apply {
        isGroupingUsed = true
    }
    return "${df.format(value)}\u00A0₽"
}

@Composable
fun ExpenseDynamicsAreaChartCard(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
    title: String = "Динамика расходов",
    onClick: (() -> Unit)? = null
) {
    var selectedPeriod by remember { mutableStateOf("Неделя") } // "Неделя" or "Месяц"
    var selectedPointIdx by remember { mutableStateOf<Int?>(null) }

    val expenseTx = remember(transactions) {
        transactions.filter { it.type == "expense" }
    }

    val weekDays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

    // Filter/Group data depending on selected tab
    val (dataPoints, xLabels) = remember(expenseTx, selectedPeriod) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

        if (selectedPeriod == "Неделя") {
            val map = DoubleArray(7) { 0.0 }
            expenseTx.forEach { tx ->
                try {
                    val txDate = sdf.parse(tx.date)
                    if (txDate != null) {
                        val cal = java.util.Calendar.getInstance().apply { time = txDate }
                        val dayOfWeek = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 // 0=Mon .. 6=Sun
                        map[dayOfWeek] += tx.amount
                    }
                } catch (e: Exception) {
                    val day = tx.date.split("-").lastOrNull()?.toIntOrNull() ?: 1
                    val dayOfWeek = (day - 1) % 7
                    map[dayOfWeek] += tx.amount
                }
            }
            Pair(map.toList(), weekDays)
        } else {
            // Group by day of month for the transactions passed (1..maxDay)
            val map = mutableMapOf<Int, Double>()
            var maxDayInTx = 28
            expenseTx.forEach { tx ->
                val day = tx.date.split("-").lastOrNull()?.toIntOrNull() ?: 1
                map[day] = (map[day] ?: 0.0) + tx.amount
                if (day > maxDayInTx) maxDayInTx = day
            }
            val totalDays = maxOf(maxDayInTx, 30)
            val points = (1..totalDays).map { day -> map[day] ?: 0.0 }
            val labels = listOf("1", "5", "10", "15", "20", "25", "$totalDays")
            Pair(points, labels)
        }
    }

    val hasRealExpenses = remember(expenseTx) { expenseTx.isNotEmpty() }

    // Group income data depending on selected tab to compute corresponding period income reference
    val periodTotalIncome = remember(transactions, selectedPeriod) {
        val incomeTxs = transactions.filter { it.type == "income" }
        val sum = incomeTxs.sumOf { it.amount }
        if (selectedPeriod == "Неделя") {
            if (sum > 0.0) sum / 4.3 else 15000.0 // Default fallback weekly
        } else {
            if (sum > 0.0) sum else 60000.0 // Default fallback monthly
        }
    }

    val dailyIncomeLimit = remember(periodTotalIncome, selectedPeriod) {
        periodTotalIncome / (if (selectedPeriod == "Неделя") 7.0 else 30.0)
    }

    // Vector Morphing state setup for tab / data changes
    var oldPoints by remember { mutableStateOf<List<Double>>(emptyList()) }
    var targetPoints by remember { mutableStateOf<List<Double>>(emptyList()) }
    
    val morphAnim = remember { androidx.compose.animation.core.Animatable(1f) }

    LaunchedEffect(dataPoints) {
        selectedPointIdx = null
        if (targetPoints.isNotEmpty()) {
            oldPoints = targetPoints
        } else {
            oldPoints = List(dataPoints.size) { 0.0 }
        }
        targetPoints = dataPoints
        morphAnim.snapTo(0f)
        morphAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    val targetMax = remember(targetPoints) {
        val max = targetPoints.maxOrNull() ?: 0.0
        if (max <= 0) 1000.0 else max * 1.30
    }

    val animatedMaxVal by animateFloatAsState(
        targetValue = targetMax.toFloat(),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "chart_max_val"
    )

    val maxVal = animatedMaxVal.toDouble()

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Slate900.copy(alpha = 0.6f))
            .border(1.dp, Slate800.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title & Tab Pill Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = Indigo500,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title.uppercase(),
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            // Week / Month Toggle Pill with Smooth sliding animation and neon styling
            val selectedIndex by animateFloatAsState(
                targetValue = if (selectedPeriod == "Неделя") 0f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "period_toggle_offset"
            )

            BoxWithConstraints(
                modifier = Modifier
                    .width(130.dp)
                    .height(30.dp)
                    .background(Color(0xFF020617), RoundedCornerShape(8.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                val totalWidth = maxWidth
                val pillWidth = totalWidth / 2

                // Sliding neon selection pill
                Box(
                    modifier = Modifier
                        .offset(x = pillWidth * selectedIndex)
                        .width(pillWidth)
                        .fillMaxHeight()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(6.dp),
                            clip = false,
                            ambientColor = Indigo500,
                            spotColor = Indigo500
                        )
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Indigo500, Indigo500.copy(alpha = 0.8f))
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Indigo500.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Неделя", "Месяц").forEach { period ->
                        val isSelected = selectedPeriod == period
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Slate400,
                            animationSpec = tween(200),
                            label = "period_text_color"
                        )
                        val textScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            animationSpec = tween(200),
                            label = "period_text_scale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { selectedPeriod = period },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                color = textColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = textScale
                                    scaleY = textScale
                                }
                            )
                        }
                    }
                }
            }
        }

        // Smooth Neon Area Chart Canvas with Vector Morphing & Dynamic Peak Bump Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(targetPoints) {
                        detectTapGestures { offset ->
                            val n = targetPoints.size
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
                val currentTarget = targetPoints
                val n = currentTarget.size
                
                if (n > 1) {
                    val stepX = w / (n - 1)
                    val morphProgress = morphAnim.value

                    // Compute interpolated points for smooth vector morphing
                    val points = currentTarget.mapIndexed { index, targetValue ->
                        val x = index * stepX
                        
                        val oldValInterpolated = if (oldPoints.size > 1) {
                            val normPos = index.toFloat() / (n - 1)
                            val oldIndexExact = normPos * (oldPoints.size - 1)
                            val i0 = oldIndexExact.toInt().coerceIn(0, oldPoints.size - 1)
                            val i1 = (i0 + 1).coerceIn(0, oldPoints.size - 1)
                            val frac = oldIndexExact - i0
                            oldPoints[i0] + frac * (oldPoints[i1] - oldPoints[i0])
                        } else 0.0

                        val morphedValue = oldValInterpolated + morphProgress * (targetValue - oldValInterpolated)
                        val normalizedY = ((morphedValue / maxVal) * (h - 28.dp.toPx())).toFloat()
                        val y = h - 8.dp.toPx() - normalizedY
                        Offset(x, y)
                    }

                    // Build Area Path with safe quadratic midpoint smoothing
                    val areaPath = Path().apply {
                        moveTo(points[0].x, h)
                        lineTo(points[0].x, points[0].y)

                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val midX = (p1.x + p2.x) / 2f
                            val midY = (p1.y + p2.y) / 2f

                            if (i == 0) {
                                lineTo(midX, midY)
                            } else {
                                quadraticTo(p1.x, p1.y, midX, midY)
                            }
                        }
                        if (points.isNotEmpty()) {
                            lineTo(points.last().x, points.last().y)
                        }

                        lineTo(points.last().x, h)
                        close()
                    }

                    // Build Stroke Line Path with safe quadratic midpoint smoothing
                    val strokePath = Path().apply {
                        moveTo(points[0].x, points[0].y)

                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val midX = (p1.x + p2.x) / 2f
                            val midY = (p1.y + p2.y) / 2f

                            if (i == 0) {
                                lineTo(midX, midY)
                            } else {
                                quadraticTo(p1.x, p1.y, midX, midY)
                            }
                        }
                        if (points.isNotEmpty()) {
                            lineTo(points.last().x, points.last().y)
                        }
                    }

                    val minY = points.minOf { it.y }

                    // Vertical gradient for neon line based on expense height (Rose at top -> Indigo -> Blue -> Emerald at bottom)
                    val strokeGradient = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF43F5E), // Верх (максимальные траты) — Красный
                            Color(0xFF6366F1), // Середина — Фиолетовый/Индиго
                            Color(0xFF60A5FA), // Чуть ниже — Голубой
                            Color(0xFF34D399)  // Самый низ (минимальные траты) — Зеленый
                        ),
                        startY = 0f,
                        endY = h
                    )

                    // Vertical glowing gradient fill under curve
                    val areaGradient = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x66A855F7), // Soft purple/indigo neon glow
                            Color.Transparent
                        ),
                        startY = minY.coerceAtLeast(0f),
                        endY = h
                    )

                    // Draw Gradient Area Fill under curve
                    drawPath(
                        path = areaPath,
                        brush = areaGradient,
                        style = Fill
                    )
                    
                    // Draw Main Neon Line (3.5dp thickness with round caps and joins)
                    drawPath(
                        path = strokePath,
                        brush = strokeGradient,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Text Paint for Amount Badges
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 9.dp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    }

                    // Highlight / Draw Neon Dots & Sums
                    points.forEachIndexed { i, pt ->
                        val amount = currentTarget.getOrNull(i) ?: 0.0
                        val isSelected = selectedPointIdx == i

                        if (isSelected) {
                            val dotColor = if (amount > 0) Rose500 else Indigo500
                            
                            // Glowing Outer Ring
                            drawCircle(
                                color = dotColor.copy(alpha = 0.35f),
                                radius = 7.dp.toPx(),
                                center = pt
                            )
                            // Solid Node
                            drawCircle(
                                color = dotColor,
                                radius = 4.dp.toPx(),
                                center = pt
                            )
                            // White Center Core
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = pt
                            )

                            // Formatted Sum above the point
                            val valInt = amount.toInt()
                            val textStr = when {
                                valInt >= 1_000_000 -> "%.1fM ₽".format(amount / 1_000_000.0)
                                valInt >= 100_000 -> "${valInt / 1000}k ₽"
                                valInt >= 10_000 -> "%.1fk ₽".format(amount / 1000.0)
                                else -> "$valInt ₽"
                            }

                            val textWidth = textPaint.measureText(textStr)
                            val pillHeight = 15.dp.toPx()
                            val pillWidth = textWidth + 8.dp.toPx()
                            val pillX = (pt.x - pillWidth / 2f).coerceIn(2.dp.toPx(), w - pillWidth - 2.dp.toPx())
                            val pillY = (pt.y - 18.dp.toPx()).coerceIn(2.dp.toPx(), h - pillHeight - 2.dp.toPx())

                            // Draw Pill Background
                            drawRoundRect(
                                color = Color(0xFF0F172A),
                                topLeft = Offset(pillX, pillY),
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                            // Draw Neon Border
                            drawRoundRect(
                                color = dotColor.copy(alpha = 0.85f),
                                topLeft = Offset(pillX, pillY),
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(4.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            // Draw Sum Text
                            drawContext.canvas.nativeCanvas.drawText(
                                textStr,
                                pillX + pillWidth / 2f,
                                pillY + pillHeight / 2f + 3.dp.toPx(),
                                textPaint
                            )
                        }
                    }

                    // Vertical dashed guideline for selected index
                    selectedPointIdx?.let { sIdx ->
                        if (sIdx in points.indices) {
                            val selPt = points[sIdx]
                            drawLine(
                                color = Indigo500.copy(alpha = 0.6f),
                                start = Offset(selPt.x, 0f),
                                end = Offset(selPt.x, h),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }
                    }
                }
            }
            if (!hasRealExpenses) {
                Text(
                    text = "Нет расходов за выбранный период",
                    color = Slate500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        // X Axis Labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xLabels.forEach { label ->
                val isWeekend = label.equals("СБ", ignoreCase = true) || 
                                label.equals("ВС", ignoreCase = true) ||
                                label.equals("СБ.", ignoreCase = true) ||
                                label.equals("ВС.", ignoreCase = true)
                Text(
                    text = label,
                    color = if (isWeekend) Rose500 else Slate500,
                    fontSize = 10.sp,
                    fontWeight = if (isWeekend) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
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

    // Point color logic:
    // Income: diff >= 0 is good (Emerald), diff < 0 is bad (Rose)
    // Expense: diff > 0 is bad (Rose - expenses grew), diff <= 0 is good (Emerald - expenses dropped)
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
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Change point badge (Точка изменения)
                Surface(
                    color = pointColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, pointColor.copy(alpha = 0.5f)),
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Text(
                text = "${formatShortCurrency(prevVal)} → ${formatShortCurrency(currentVal)}",
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
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
                .background(DarkBg, RoundedCornerShape(12.dp))
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
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )

                // Current Bar
                val curTopY = baselineY - curH.coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = curColor,
                    topLeft = Offset(curX, curTopY),
                    size = Size(barWidth, curH.coerceAtLeast(4.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
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

                // Glowing Point (Точка на графике)
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
