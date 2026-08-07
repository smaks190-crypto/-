import re

with open("app/src/main/java/com/example/ui/components/Charts.kt", "r") as f:
    content = f.read()

# Remove the total balance row inside ExpenseDynamicsAreaChartCard
old_total_row = """                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatFullCurrency(totalExpense),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Surface(
                    shape = CircleShape,
                    color = Rose500.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "${expenseTx.size} транзакций",
                        color = Rose500,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Smooth Neon Area Chart Canvas"""
new_total_row = """                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Smooth Neon Area Chart Canvas"""
content = content.replace(old_total_row, new_total_row)

# Change Canvas Background to transparent (remove .background(DarkBg))
old_canvas_box = """            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkBg)
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {"""
new_canvas_box = """            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(vertical = 4.dp)
            ) {"""
content = content.replace(old_canvas_box, new_canvas_box)

# Fix Area Fill Gradient
old_fill = """                        // Draw Gradient Area Fill
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Rose500.copy(alpha = 0.35f),
                                    Rose500.copy(alpha = 0.1f),
                                    Color.Transparent
                                )
                            ),
                            style = Fill
                        )"""
new_fill = """                        // Draw Gradient Area Fill
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Indigo500.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            ),
                            style = Fill
                        )"""
content = content.replace(old_fill, new_fill)

# Fix Stroke Gradient
old_stroke = """                        // Draw Glowing Stroke Line
                        drawPath(
                            path = strokePath,
                            color = Rose500,
                            style = Stroke(width = 2.5.dp.toPx())
                        )"""
new_stroke = """                        // Draw Glowing Stroke Line
                        drawPath(
                            path = strokePath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Emerald400,
                                    Indigo500,
                                    Rose500
                                )
                            ),
                            style = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )"""
content = content.replace(old_stroke, new_stroke)

# Fix dots
old_dots = """                        // Highlight Peak Points with Glowing Dots
                        val maxPoint = points.maxByOrNull { h - it.y }
                        maxPoint?.let { pt ->
                            if (h - pt.y > 10.dp.toPx()) {
                                drawCircle(
                                    color = Rose500.copy(alpha = 0.3f),
                                    radius = 10.dp.toPx() * animatedProgress,
                                    center = pt
                                )
                                drawCircle(
                                    color = Rose500,
                                    radius = 4.dp.toPx() * animatedProgress,
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx() * animatedProgress,
                                    center = pt
                                )
                            }
                        }"""
new_dots = """                        // Highlight Peak Points with Glowing Dots
                        val maxPoint = points.maxByOrNull { h - it.y }
                        maxPoint?.let { pt ->
                            if (h - pt.y > 10.dp.toPx()) {
                                drawCircle(
                                    color = Indigo500.copy(alpha = 0.5f),
                                    radius = 8.dp.toPx() * animatedProgress,
                                    center = pt
                                )
                                drawCircle(
                                    color = Indigo500,
                                    radius = 4.dp.toPx() * animatedProgress,
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.dp.toPx() * animatedProgress,
                                    center = pt,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }"""
content = content.replace(old_dots, new_dots)

with open("app/src/main/java/com/example/ui/components/Charts.kt", "w") as f:
    f.write(content)

