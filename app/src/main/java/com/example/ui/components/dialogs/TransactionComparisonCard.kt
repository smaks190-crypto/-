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
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.formatFullCurrency
import com.example.ui.theme.Emerald400
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun DoubleSummaryComparisonCards(
    totalExpenseAmt: Double,
    totalIncomeAmt: Double,
    dynamicMonthLabel: String,
    filterType: String,
    onSelectExpense: () -> Unit,
    onSelectIncome: () -> Unit,
    onSwipePeriodOffset: ((Int) -> Unit)? = null,
    isSwipeEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val totalFlow = (totalExpenseAmt + totalIncomeAmt).coerceAtLeast(1.0)
    val expenseShare = if (totalExpenseAmt > 0.0) {
        (totalExpenseAmt / totalFlow).toFloat().coerceIn(0.15f, 1f)
    } else 0.12f
    val incomeShare = if (totalIncomeAmt > 0.0) {
        (totalIncomeAmt / totalFlow).toFloat().coerceIn(0.15f, 1f)
    } else 0.12f

    var totalDragX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .testTag("double_summary_cards")
            .pointerInput(isSwipeEnabled, onSwipePeriodOffset) {
                if (!isSwipeEnabled || onSwipePeriodOffset == null) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        val threshold = 110f
                        val finalDrag = totalDragX
                        totalDragX = 0f
                        if (finalDrag < -threshold) {
                            onSwipePeriodOffset(1)
                        } else if (finalDrag > threshold) {
                            onSwipePeriodOffset(-1)
                        }
                    },
                    onDragCancel = { totalDragX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Expense Bento Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF131B2E),
                                Color(0xFF0C1220)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (filterType == "expense") Rose500.copy(alpha = 0.65f) else Rose500.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .testTag("summary_expense_card")
                    .clickable { onSelectExpense() }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Траты • $dynamicMonthLabel",
                            color = Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "График ›",
                            color = Rose500.copy(alpha = 0.85f),
                            fontFamily = ManropeFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = formatFullCurrency(totalExpenseAmt),
                        color = Color.White,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Slate800)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(expenseShare)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Rose500, Color(0xFFFF758F))
                                    )
                                )
                        )
                    }
                }
            }

            // Income Bento Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF101D29),
                                Color(0xFF0B131E)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (filterType == "income") Emerald400.copy(alpha = 0.65f) else Emerald400.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .testTag("summary_income_card")
                    .clickable { onSelectIncome() }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Доходы • $dynamicMonthLabel",
                            color = Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "График ›",
                            color = Emerald400.copy(alpha = 0.85f),
                            fontFamily = ManropeFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = formatFullCurrency(totalIncomeAmt),
                        color = Color.White,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Slate800)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(incomeShare)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Emerald400, Color(0xFF34D399))
                                    )
                                )
                        )
                    }
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
    selectedPeriod: String?,
    currentOffsetInt: Int,
    swipeMonthOffset: Int = 0,
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
    onCollapseChart: (() -> Unit)? = null,
    bottomContent: @Composable ColumnScope.() -> Unit
) {
    val isExpense = filterType == "expense"
    val accentColor = if (isExpense) Rose500 else Emerald400
    val activeAmount = if (filterType == "expense") totalExpenseAmt else totalIncomeAmt
    val effectivePeriod = resolveEffectivePeriod(selectedPeriod)
    val prevAmount = remember(effectivePeriod, currentOffsetInt, transactions, filterType, visibleDateStr) {
        if (effectivePeriod == "custom") {
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF11192B),
                        Slate900
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .padding(14.dp)
            .testTag("single_hero_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header inside card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFullCurrency(activeAmount),
                        color = Color.White,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("hero_active_amount")
                    )
                    if (effectivePeriod != "custom") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = diffText,
                                color = Slate300,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$titleText • $dynamicMonthLabel",
                    color = Slate400,
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("hero_period_subtitle")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onCollapseChart != null) {
                    IconButton(
                        onClick = onCollapseChart,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Slate800)
                            .testTag("hero_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Свернуть график",
                            tint = Slate300,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onResetFilterToAll,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Slate800)
                        .testTag("hero_reset_to_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "К всем",
                        tint = Slate400,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Chart area inside Swipe Gesture detector (full-width pages, zero adjacent bleed)
        var totalDragX by remember { mutableFloatStateOf(0f) }
        val isSwipeEnabled = effectivePeriod != "custom"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hero_chart_swipe_box")
                .pointerInput(effectivePeriod, isSwipeEnabled) {
                    if (!isSwipeEnabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            val threshold = 110f
                            val finalDrag = totalDragX
                            totalDragX = 0f
                            if (finalDrag < -threshold) {
                                onSwipePeriodOffset(1)
                            } else if (finalDrag > threshold) {
                                onSwipePeriodOffset(-1)
                            }
                        },
                        onDragCancel = { totalDragX = 0f },
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
                    .height(226.dp)
                    .clipToBounds()
            ) {
                val widthPx = constraints.maxWidth.coerceAtLeast(1)
                val widthDp = with(LocalDensity.current) { widthPx.toDp() }
                val dragOffsetFraction = if (isSwipeEnabled) {
                    (totalDragX / widthPx.toFloat()).coerceIn(-0.95f, 0.95f)
                } else 0f

                val animatedOffsetFloat by animateFloatAsState(
                    targetValue = currentOffsetInt.toFloat() - dragOffsetFraction,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "diagram_slide_offset"
                )

                val offsetsToRender = if (isSwipeEnabled) {
                    listOf(currentOffsetInt - 1, currentOffsetInt, currentOffsetInt + 1)
                } else {
                    listOf(currentOffsetInt)
                }

                val barChartAnchorDateStr = if (effectivePeriod == "week" || effectivePeriod == "year") {
                    shiftDateByMonths(stableAnchorDateStr, swipeMonthOffset)
                } else {
                    stableAnchorDateStr
                }

                offsetsToRender.forEach { offsetValue ->
                    val pageDelta = offsetValue - animatedOffsetFloat
                    val relativeDiff = kotlin.math.abs(pageDelta)
                    val alphaValue = (1f - relativeDiff * 1.05f).coerceIn(0f, 1f)
                    val scaleValue = (1f - relativeDiff * 0.10f).coerceIn(0.88f, 1f)
                    val translationX = widthDp * pageDelta

                    if (alphaValue > 0.01f) {
                        Box(
                            modifier = Modifier
                                .width(widthDp)
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
                                    stableAnchorDateStr = barChartAnchorDateStr,
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
        }

        bottomContent()
    }
}
