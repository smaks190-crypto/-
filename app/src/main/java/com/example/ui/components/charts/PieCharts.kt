package com.example.ui.components.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.formatShortCurrency
import com.example.ui.components.ChartColors

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
fun DonutChart(
    categoryAmounts: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    CategoryDoughnutChart(categoryAmounts, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPieChart(
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
            Canvas(modifier = Modifier.size(140.dp).clipToBounds()) {
                var startAngle = -90f
                val radius = size.minDimension / 2
                val topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )
                val arcSize = Size(radius * 2, radius * 2)

                entries.forEachIndexed { index, entry ->
                    val fullSweep = ((entry.value / total) * 360f).toFloat()
                    val sweepAngle = Math.max(0f, (fullSweep * animatedProgress) - 1.5f)
                    val color = ChartColors[index % ChartColors.size]

                    if (sweepAngle > 0f) {
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Fill
                        )
                    }

                    startAngle += fullSweep * animatedProgress
                }
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
