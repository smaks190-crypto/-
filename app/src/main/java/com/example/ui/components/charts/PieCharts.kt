package com.example.ui.components.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ChartColors
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.SpaceGroteskFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDoughnutChart(
    categoryAmounts: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    val slices = remember(categoryAmounts) {
        HomeChartDataEngine.buildCategorySlices(categoryAmounts)
    }

    if (slices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нет данных за период",
                color = Slate400,
                fontSize = 13.sp,
                fontFamily = ManropeFontFamily
            )
        }
        return
    }

    val total = remember(slices) { slices.sumOf { it.amount } }
    var selectedCategory by remember(slices) { mutableStateOf<String?>(null) }
    val activeSlice = remember(selectedCategory, slices) {
        slices.firstOrNull { it.category == selectedCategory }
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }
    val animatedProgress = animProgress.value

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(152.dp)
                    .clipToBounds()
            ) {
                var startAngle = -90f
                val baseStrokeWidth = 28.dp.toPx()
                val radius = (size.minDimension - baseStrokeWidth * 1.2f) / 2f
                val topLeft = Offset(
                    (size.width - radius * 2f) / 2f,
                    (size.height - radius * 2f) / 2f
                )
                val arcSize = Size(radius * 2f, radius * 2f)

                slices.forEachIndexed { index, slice ->
                    val isSelected = selectedCategory == slice.category
                    val fullSweep = slice.sweepAngleDeg * animatedProgress
                    val gapDeg = if (slices.size > 1) 2.5f else 0f
                    val sweepAngle = (fullSweep - gapDeg).coerceAtLeast(0.8f)
                    val baseColor = ChartColors[index % ChartColors.size]
                    val drawColor = if (selectedCategory == null || isSelected) {
                        baseColor
                    } else {
                        baseColor.copy(alpha = 0.38f)
                    }
                    val strokeW = if (isSelected) baseStrokeWidth * 1.14f else baseStrokeWidth

                    if (sweepAngle > 0f) {
                        drawArc(
                            color = drawColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += fullSweep
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = activeSlice?.category ?: "Всего",
                    color = Slate400,
                    fontSize = 10.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RollingCurrencyText(
                    text = formatShortCurrency(activeSlice?.amount ?: total),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (activeSlice != null) {
                    Text(
                        text = "${activeSlice.percent}%",
                        color = Slate300,
                        fontSize = 10.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEachIndexed { index, slice ->
                val color = ChartColors[index % ChartColors.size]
                val isSelected = selectedCategory == slice.category

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) color.copy(alpha = 0.16f) else ObsidianSurface.copy(alpha = 0.6f))
                        .border(1.dp, if (isSelected) color else GlassBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            selectedCategory = if (isSelected) null else slice.category
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "${slice.category} (${slice.percent}%)",
                        color = if (isSelected) Color.White else Slate300,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
    val slices = remember(categoryAmounts) {
        HomeChartDataEngine.buildCategorySlices(categoryAmounts)
    }

    if (slices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Нет данных за период",
                color = Slate400,
                fontSize = 13.sp,
                fontFamily = ManropeFontFamily
            )
        }
        return
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }
    val animatedProgress = animProgress.value

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(140.dp)
                    .clipToBounds()
            ) {
                var startAngle = -90f
                val radius = size.minDimension / 2f
                val topLeft = Offset(
                    (size.width - radius * 2f) / 2f,
                    (size.height - radius * 2f) / 2f
                )
                val arcSize = Size(radius * 2f, radius * 2f)

                slices.forEachIndexed { index, slice ->
                    val fullSweep = slice.sweepAngleDeg * animatedProgress
                    val sweepAngle = (fullSweep - if (slices.size > 1) 1.5f else 0f).coerceAtLeast(0.5f)
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
                    startAngle += fullSweep
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEachIndexed { index, slice ->
                val color = ChartColors[index % ChartColors.size]

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
                        text = "${slice.category} (${slice.percent}%)",
                        color = Slate300,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
