package com.example.ui.components.charts

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart

import com.example.data.db.TransactionEntity
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.formatShortCurrency
import com.example.ui.components.formatFullCurrency
import com.example.ui.components.ChartColors

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

    val totalIncome = remember(transactions) {
        transactions.filter { it.type == "income" }.sumOf { it.amount }
    }
    val totalExpenses = remember(transactions) {
        transactions.filter { it.type == "expense" }.sumOf { it.amount }
    }

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
        if (max <= 0) 1000.0 else max * 1.10
    }

    val animatedMaxVal by animateFloatAsState(
        targetValue = targetMax.toFloat(),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "chart_max_val"
    )

    val maxVal = animatedMaxVal.toDouble()

    Column(
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
                    Icons.Default.ShowChart,
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
                        val rawNormalizedY = ((morphedValue / maxVal) * (h - 28.dp.toPx())).toFloat()
                        val normalizedY = if (morphedValue > 0.0) maxOf(rawNormalizedY, 4.dp.toPx()) else 0f
                        val y = h - 8.dp.toPx() - normalizedY
                        Offset(x, y)
                    }

                    // Build Area Path with sharp, localized cubic Bezier smoothing
                    val areaPath = Path().apply {
                        moveTo(points[0].x, h)
                        lineTo(points[0].x, points[0].y)

                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val dx = p2.x - p1.x
                            val cp1x = p1.x + dx * 0.35f
                            val cp1y = p1.y
                            val cp2x = p2.x - dx * 0.35f
                            val cp2y = p2.y

                            cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        }
                        if (points.isNotEmpty()) {
                            lineTo(points.last().x, points.last().y)
                        }

                        lineTo(points.last().x, h)
                        close()
                    }

                    // Build Stroke Line Path with sharp, localized cubic Bezier smoothing
                    val strokePath = Path().apply {
                        moveTo(points[0].x, points[0].y)

                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val dx = p2.x - p1.x
                            val cp1x = p1.x + dx * 0.35f
                            val cp1y = p1.y
                            val cp2x = p2.x - dx * 0.35f
                            val cp2y = p2.y

                            cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        }
                        if (points.isNotEmpty()) {
                            lineTo(points.last().x, points.last().y)
                        }
                    }

                    val isBudgetExceeded = (totalExpenses > totalIncome) || (selectedPeriod == "Неделя" && dataPoints.sum() > periodTotalIncome)

                    val strokeGradient = if (isBudgetExceeded) {
                        // Режим дефицита бюджета: вершина и пики ярко-красные
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF43F5E), // Самый верх (пик) — Красный
                                Color(0xFFF43F5E), // Верхняя часть бугорка — Красный
                                Color(0xFF6366F1), // Середина — Индиго
                                Color(0xFF34D399)  // Базовый уровень — Зеленый
                            ),
                            startY = 0f,
                            endY = h
                        )
                    } else {
                        // Режим нормы: стандартный переход
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF6366F1), // Нормальный пик — Индиго/Фиолетовый
                                Color(0xFF60A5FA), // Середина — Голубой
                                Color(0xFF34D399)  // Низ — Зеленый
                            ),
                            startY = 0f,
                            endY = h
                        )
                    }

                    // Vertical glowing gradient fill under curve matching line colors
                    val areaGradient = if (isBudgetExceeded) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x55F43F5E), // На самом верху (пик) — полупрозрачный розовато-красный (33% alpha)
                                Color(0x336366F1), // В средней части — полупрозрачный индиго (20% alpha)
                                Color(0x2234D399), // Ближе к базовой линии — полупрозрачный изумрудно-зеленый (13% alpha)
                                Color.Transparent  // В самом низу Canvas — полностью прозрачный
                            ),
                            startY = 0f,
                            endY = h
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x446366F1), // На верху (пик) — полупрозрачный индиго
                                Color(0x3360A5FA), // В средней части — полупрозрачный голубой
                                Color(0x2234D399), // Ближе к базовой линии — полупрозрачный изумрудно-зеленый
                                Color.Transparent  // В самом низу Canvas — полностью прозрачный
                            ),
                            startY = 0f,
                            endY = h
                        )
                    }

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
                    val actualPoints = points

                    points.forEachIndexed { i, pt ->
                        val amount = currentTarget.getOrNull(i) ?: 0.0
                        val isSelected = selectedPointIdx == i

                        if (isSelected) {
                            val actualPt = actualPoints.getOrElse(i) { pt }
                            val dotColor = if (amount > 0) Rose500 else Indigo500
                            
                            // Glowing Outer Ring
                            drawCircle(
                                color = dotColor.copy(alpha = 0.35f),
                                radius = 7.dp.toPx(),
                                center = actualPt
                            )
                            // Solid Node
                            drawCircle(
                                color = dotColor,
                                radius = 4.dp.toPx(),
                                center = actualPt
                            )
                            // White Center Core
                            drawCircle(
                                color = Color.White,
                                radius = 2.dp.toPx(),
                                center = actualPt
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
                            val pillX = (actualPt.x - pillWidth / 2f).coerceIn(2.dp.toPx(), w - pillWidth - 2.dp.toPx())
                            val pillY = (actualPt.y - 18.dp.toPx()).coerceIn(2.dp.toPx(), h - pillHeight - 2.dp.toPx())

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

/**
 * Функция рендеринга 'renderChartMessage' для динамических линейных графиков трат в сообщениях ассистента.
 * Реализована на базе нативного Jetpack Compose Canvas в стиле Dark Neon / Cyberpunk Minimalist.
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

    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(safeData) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }
    val progress = animProgress.value

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Indigo500.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = Indigo500,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate800,
                    border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = formatShortCurrency(computedTotal),
                        color = Emerald400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Dynamic Canvas Chart
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

                        val points = safeData.mapIndexed { index, value ->
                            val x = index * stepX
                            val rawY = ((value / maxVal) * (h - 24.dp.toPx())).toFloat() * progress
                            val y = h - 6.dp.toPx() - rawY.coerceAtLeast(2.dp.toPx())
                            Offset(x, y)
                        }

                        // Smooth Bezier path
                        val areaPath = Path().apply {
                            moveTo(points[0].x, h)
                            lineTo(points[0].x, points[0].y)

                            for (i in 0 until points.size - 1) {
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                val dx = p2.x - p1.x
                                val cp1x = p1.x + dx * 0.35f
                                val cp1y = p1.y
                                val cp2x = p2.x - dx * 0.35f
                                val cp2y = p2.y
                                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }
                            lineTo(points.last().x, points.last().y)
                            lineTo(points.last().x, h)
                            close()
                        }

                        val strokePath = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 0 until points.size - 1) {
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                val dx = p2.x - p1.x
                                val cp1x = p1.x + dx * 0.35f
                                val cp1y = p1.y
                                val cp2x = p2.x - dx * 0.35f
                                val cp2y = p2.y
                                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }
                        }

                        val neonStrokeGradient = Brush.horizontalGradient(
                            colors = listOf(Emerald400, Indigo500, Rose500)
                        )

                        val neonAreaGradient = Brush.verticalGradient(
                            colors = listOf(
                                Indigo500.copy(alpha = 0.35f),
                                Emerald400.copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h
                        )

                        // Area fill
                        drawPath(
                            path = areaPath,
                            brush = neonAreaGradient,
                            style = Fill
                        )

                        // Glowing stroke line
                        drawPath(
                            path = strokePath,
                            brush = neonStrokeGradient,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Draw Node Points
                        points.forEachIndexed { i, pt ->
                            val valAmt = safeData[i]
                            val isSelected = selectedPointIdx == i
                            val dotColor = if (valAmt > (computedTotal / n)) Rose500 else Emerald400

                            if (isSelected) {
                                drawCircle(
                                    color = dotColor.copy(alpha = 0.35f),
                                    radius = 8.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = dotColor,
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx(),
                                    center = pt
                                )
                            }
                        }

                        // Dashed guideline for selected point
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
            }

            // X-Axis Labels
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
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
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
