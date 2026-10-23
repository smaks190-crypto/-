package com.example.ui.components.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.formatFullCurrency

@Composable
fun DoubleSummaryComparisonCards(
    totalExpenseAmt: Double,
    totalIncomeAmt: Double,
    dynamicMonthLabel: String,
    filterType: String,
    onSelectExpense: () -> Unit,
    onSelectIncome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Expense Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .clickable { onSelectExpense() }
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = formatFullCurrency(totalExpenseAmt),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("Траты • $dynamicMonthLabel", color = Slate400, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Rose500)
                    )
                }
            }

            // Income Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Slate900)
                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                    .clickable { onSelectIncome() }
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = formatFullCurrency(totalIncomeAmt),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text("Доходы • $dynamicMonthLabel", color = Slate400, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Emerald400)
                    )
                }
            }
        }
    }
}

@Composable
fun SingleHeroComparisonCard(
    filterType: String,
    totalExpenseAmt: Double,
    totalIncomeAmt: Double,
    selectedPeriod: String,
    currentOffsetInt: Int,
    transactions: List<TransactionEntity>,
    visibleDateStr: String,
    dynamicMonthLabel: String,
    chartViewMode: String,
    stableAnchorDateStr: String,
    getFilteredListForOffset: (Int) -> List<TransactionEntity>,
    getCategoryTotalsForOffset: (Int) -> List<Pair<String, Double>>,
    getCategoryColor: (String) -> Color,
    onResetFilterToAll: () -> Unit,
    onSwipePeriodOffset: (Int) -> Unit,
    bottomContent: @Composable ColumnScope.() -> Unit
) {
    val isExpense = filterType == "expense"
    val accentColor = if (isExpense) Rose500 else Emerald400
    val activeAmount = if (filterType == "expense") totalExpenseAmt else totalIncomeAmt
    val prevAmount = remember(selectedPeriod, currentOffsetInt, transactions, filterType, visibleDateStr) {
        if (selectedPeriod == "all" || selectedPeriod == "custom") {
            val dateParts = visibleDateStr.split("-")
            if (dateParts.size >= 2) {
                val year = dateParts[0].toIntOrNull() ?: 2026
                val month = dateParts[1].toIntOrNull() ?: 8
                val prevMonth = if (month == 1) 12 else month - 1
                val prevYear = if (month == 1) year - 1 else year
                val prefix = String.format("%04d-%02d-", prevYear, prevMonth)
                val tType = if (filterType == "expense") "expense" else "income"
                transactions.filter { it.type.equals(tType, ignoreCase = true) && it.date.startsWith(prefix) }
                    .sumOf { kotlin.math.abs(it.amount) }
            } else {
                0.0
            }
        } else {
            val prevOffset = currentOffsetInt - 1
            val prevList = getFilteredListForOffset(prevOffset)
            val tType = if (filterType == "expense") "expense" else "income"
            prevList.filter { it.type.equals(tType, ignoreCase = true) }.sumOf { kotlin.math.abs(it.amount) }
        }
    }
    val amountDiff = activeAmount - prevAmount
    val diffText = if (amountDiff >= 0) {
        "↑ ${formatFullCurrency(amountDiff)}"
    } else {
        "↓ ${formatFullCurrency(kotlin.math.abs(amountDiff))}"
    }
    val titleText = if (isExpense) "Траты" else "Доходы"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Slate900.copy(alpha = 0.9f))
            .border(1.dp, Slate800, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header inside card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFullCurrency(activeAmount),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = diffText,
                            color = Slate300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$titleText • $dynamicMonthLabel",
                    color = Slate400,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(
                onClick = onResetFilterToAll,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Slate800)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "К всем",
                    tint = Slate400,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Chart area inside Swipe Gesture detector
        var totalDragX by remember { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(selectedPeriod) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            val threshold = 120f
                            if (totalDragX < -threshold) {
                                onSwipePeriodOffset(1)
                            } else if (totalDragX > threshold) {
                                onSwipePeriodOffset(-1)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                }
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clipToBounds()
            ) {
                val widthPx = constraints.maxWidth
                val widthDp = with(LocalDensity.current) { widthPx.toDp() }

                val pageWidthDp = widthDp * 0.8f
                val centerOffsetDp = widthDp * 0.1f

                val animatedOffsetFloat by animateFloatAsState(
                    targetValue = currentOffsetInt.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "diagram_slide_offset"
                )

                listOf(currentOffsetInt - 1, currentOffsetInt, currentOffsetInt + 1).forEach { offsetValue ->
                    val translationX = centerOffsetDp + pageWidthDp * (offsetValue - animatedOffsetFloat)
                    val relativeDiff = kotlin.math.abs(offsetValue - animatedOffsetFloat)
                    val alphaValue = if (offsetValue == currentOffsetInt) {
                        1f - kotlin.math.min(1f, relativeDiff) * 0.4f
                    } else {
                        0.3f + (1f - kotlin.math.min(1f, relativeDiff)) * 0.3f
                    }

                    val scaleValue = if (offsetValue == currentOffsetInt) {
                        1f - kotlin.math.min(1f, relativeDiff) * 0.12f
                    } else {
                        0.88f + (1f - kotlin.math.min(1f, relativeDiff)) * 0.12f
                    }

                    Box(
                        modifier = Modifier
                            .width(pageWidthDp)
                            .fillMaxHeight()
                            .offset(x = translationX)
                            .graphicsLayer {
                                alpha = alphaValue
                                scaleX = scaleValue
                                scaleY = scaleValue
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val offsetTotalsMap = getCategoryTotalsForOffset(offsetValue)

                        if (chartViewMode == "donut") {
                            TransactionDonutChart(
                                offsetTotalsMap = offsetTotalsMap,
                                getCategoryColor = getCategoryColor
                            )
                        } else {
                            TransactionBarChart(
                                selectedPeriod = selectedPeriod,
                                stableAnchorDateStr = stableAnchorDateStr,
                                offsetValue = offsetValue,
                                filterType = filterType,
                                accentColor = accentColor,
                                getFilteredListForOffset = getFilteredListForOffset
                            )
                        }
                    }
                }
            }
        }

        bottomContent()
    }
}
