import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    content = f.read()

# Replace Step 1 Total Balance Card
start_marker = "// --- STEP 1: TOTAL BALANCE CARD"
end_marker = "// --- STEP 2: DYNAMICS CHART SECTION"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

new_card = """// --- STEP 1: TOTAL BALANCE CARD ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Slate900, Slate900, Slate950),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
        ) {
            // Neon Background Blur Glare 1 (Top-Right Indigo)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-30).dp)
                    .size(130.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Indigo500.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // Neon Background Blur Glare 2 (Bottom-Left Emerald)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-30).dp, y = 30.dp)
                    .size(130.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Emerald400.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Row: Title + Trend Badge
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "ОБЩИЙ БАЛАНС",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        shape = CircleShape,
                        color = Emerald400.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Норма $monthSavingsRate%",
                                color = Emerald400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Total Balance Amount
                RollingCurrencyText(
                    text = formatFullCurrency(monthTotalAccumulatedBalance),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Grid: Incomes & Expenses
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = Slate800.copy(alpha = 0.8f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Income Item
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(
                                    elevation = 15.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = Emerald400,
                                    spotColor = Emerald400
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(Emerald400.copy(alpha = 0.1f))
                                .border(1.dp, Emerald400.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.SouthWest,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "Доходы",
                                color = Slate400,
                                fontSize = 10.sp
                            )
                            RollingCurrencyText(
                                text = "+ ${formatShortCurrency(monthTotalIncome)}",
                                color = Emerald400,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Expense Item
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(
                                    elevation = 15.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = Rose500,
                                    spotColor = Rose500
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(Rose500.copy(alpha = 0.1f))
                                .border(1.dp, Rose500.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.NorthEast,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "Расходы",
                                color = Slate400,
                                fontSize = 10.sp
                            )
                            RollingCurrencyText(
                                text = "- ${formatShortCurrency(monthTotalExpense)}",
                                color = Rose500,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))

        // """

content = content[:start_idx] + new_card + content[end_idx:]

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
    f.write(content)
