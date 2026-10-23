package com.example.ui.components.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Slate800

@Composable
fun CompactMiniDonutChart(
    categoryTotals: List<Pair<String, Double>>,
    getCategoryColor: (String) -> Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(36.dp)) {
        val strokeWidth = 5.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val centerOffset = Offset(size.width / 2, size.height / 2)

        if (categoryTotals.isEmpty() || categoryTotals.sumOf { it.second } <= 0) {
            drawCircle(
                color = Slate800,
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokeWidth)
            )
        } else {
            val sumAll = categoryTotals.sumOf { it.second }
            var startAngle = -90f

            categoryTotals.forEachIndexed { _, (catName, amt) ->
                val sweepAngle = ((amt / sumAll) * 360f).toFloat()
                val col = getCategoryColor(catName)
                drawArc(
                    color = col,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle - 2f,
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                startAngle += sweepAngle
            }
        }
    }
}

@Composable
fun TransactionDonutChart(
    offsetTotalsMap: List<Pair<String, Double>>,
    getCategoryColor: (String) -> Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val sumAll = offsetTotalsMap.sumOf { it.second }

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(170.dp)) {
                val strokeWidth = 20.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val centerOffset = Offset(size.width / 2, size.height / 2)

                if (offsetTotalsMap.isEmpty() || sumAll <= 0) {
                    drawCircle(
                        color = Slate800,
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    var startAngle = -90f

                    offsetTotalsMap.forEach { (catName, amt) ->
                        val sweepAngle = ((amt / sumAll) * 360f).toFloat()
                        val col = getCategoryColor(catName)

                        if (sweepAngle > 0f) {
                            // Glow effect backdrop arc
                            drawArc(
                                color = col.copy(alpha = 0.18f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle - 3f,
                                useCenter = false,
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                style = Stroke(width = strokeWidth * 1.4f, cap = StrokeCap.Round)
                            )

                            // Main crisp arc
                            drawArc(
                                color = col,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle - 3f,
                                useCenter = false,
                                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        startAngle += sweepAngle
                    }
                }
            }
        }
    }
}
