import re

with open("app/src/main/java/com/example/ui/components/Charts.kt", "r") as f:
    content = f.read()

# I will replace the entire ExpenseDynamicsAreaChartCard function.
# Let's find the start and end of it.

start_str = "fun ExpenseDynamicsAreaChartCard("
end_str = "@Composable\nfun ComparativeMetricRow("

start_idx = content.find(start_str)
end_idx = content.find(end_str)

if start_idx != -1 and end_idx != -1:
    new_func = """fun ExpenseDynamicsAreaChartCard(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
    title: String = "Динамика расходов",
    onClick: (() -> Unit)? = null
) {
    var selectedPeriod by remember { mutableStateOf("Неделя") } // "Неделя" or "Месяц"

    val expenseTx = remember(transactions) {
        transactions.filter { it.type == "expense" }
    }

    val weekDays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

    // Filter/Group data depending on selected tab
    val (dataPoints, xLabels) = remember(expenseTx, selectedPeriod) {
        if (selectedPeriod == "Неделя") {
            // Group by day of week (0=Mon .. 6=Sun)
            val cal = Calendar.getInstance()
            val map = DoubleArray(7) { 0.0 }
            expenseTx.forEach { tx ->
                val parts = tx.date.split("-")
                if (parts.size == 3) {
                    val y = parts[0].toIntOrNull() ?: 2026
                    val m = (parts[1].toIntOrNull() ?: 1) - 1
                    val d = parts[2].toIntOrNull() ?: 1
                    cal.set(y, m, d)
                    val dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // 0=Mon .. 6=Sun
                    map[dayOfWeek] += tx.amount
                }
            }
            // For visual variety if empty
            if (map.all { it == 0.0 }) {
                map[1] = 50.0
                map[2] = 20.0
                map[3] = 60.0
                map[4] = 30.0
                map[5] = 50.0
            }
            Pair(map.toList(), weekDays)
        } else {
            // Group by day of month (1..maxDay)
            val cal = Calendar.getInstance()
            val maxInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(15)
            val map = mutableMapOf<Int, Double>()
            expenseTx.forEach { tx ->
                val day = tx.date.split("-").lastOrNull()?.toIntOrNull() ?: 1
                map[day] = (map[day] ?: 0.0) + tx.amount
            }
            val points = (1..maxInMonth).map { day -> map[day] ?: 0.0 }
            val labels = listOf("1", "5", "10", "15", "20", "25", "$maxInMonth")
            Pair(points, labels)
        }
    }

    val maxVal = remember(dataPoints) {
        val max = dataPoints.maxOrNull() ?: 0.0
        if (max <= 0) 1000.0 else max * 1.15
    }

    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "chart_area_anim"
    )

    LaunchedEffect(transactions, selectedPeriod) {
        animProgress = 0f
        animProgress = 1f
    }

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
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            // Week / Month Toggle Pill
            Row(
                modifier = Modifier
                    .background(Slate950, RoundedCornerShape(8.dp))
                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf("Неделя", "Месяц").forEach { period ->
                    val isSelected = selectedPeriod == period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Indigo500 else Color.Transparent)
                            .clickable { selectedPeriod = period }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = period,
                            color = if (isSelected) Color.White else Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Smooth Neon Area Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val n = dataPoints.size
                val stepX = if (n > 1) w / (n - 1) else w
                
                if (n > 1) {
                    val points = dataPoints.mapIndexed { index, value ->
                        val x = index * stepX
                        val normalizedY = ((value / maxVal) * (h - 20.dp.toPx())).toFloat() * animatedProgress
                        val y = h - 10.dp.toPx() - normalizedY
                        Offset(x, y)
                    }

                    // Build Area Path
                    val areaPath = Path().apply {
                        moveTo(points[0].x, h)
                        lineTo(points[0].x, points[0].y)

                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val controlX1 = p1.x + (p2.x - p1.x) / 2f
                            val controlY1 = p1.y
                            val controlX2 = p1.x + (p2.x - p1.x) / 2f
                            val controlY2 = p2.y
                            cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                        }

                        lineTo(points.last().x, h)
                        close()
                    }

                    // Build Stroke Line Path
                    val strokePath = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val controlX1 = p1.x + (p2.x - p1.x) / 2f
                            val controlY1 = p1.y
                            val controlX2 = p1.x + (p2.x - p1.x) / 2f
                            val controlY2 = p2.y
                            cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                        }
                    }

                    // Draw Gradient Area Fill
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Indigo500.copy(alpha = 0.3f),
                                Indigo500.copy(alpha = 0.0f)
                            )
                        ),
                        style = Fill
                    )
                    
                    // Draw glowing stroke behind the main stroke
                    drawPath(
                        path = strokePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Emerald400, Indigo500, Rose500)
                        ),
                        style = Stroke(width = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        alpha = 0.4f // Glow alpha
                    )

                    // Draw Stroke Line
                    drawPath(
                        path = strokePath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Emerald400, Indigo500, Rose500)
                        ),
                        style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )

                    // Draw Dots (like in HTML)
                    val pointsToDraw = listOf(
                        points.getOrNull(points.size / 3),
                        points.getOrNull(2 * points.size / 3)
                    ).filterNotNull()
                    
                    pointsToDraw.forEachIndexed { i, pt ->
                        val fillColor = if (i == 0) Indigo500 else Rose500
                        drawCircle(
                            color = fillColor,
                            radius = 4.dp.toPx() * animatedProgress,
                            center = pt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4.dp.toPx() * animatedProgress,
                            center = pt,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }
        
        // X Axis Labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            xLabels.forEach { label ->
                Text(
                    text = label,
                    color = Slate500,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

"""
    new_content = content[:start_idx] + new_func + content[end_idx:]
    with open("app/src/main/java/com/example/ui/components/Charts.kt", "w") as f:
        f.write(new_content)
    print("Replaced successfully")
else:
    print("Failed to find boundaries")
