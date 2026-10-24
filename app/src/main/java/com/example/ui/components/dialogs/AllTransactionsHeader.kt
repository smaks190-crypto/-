package com.example.ui.components.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.data.db.TransactionEntity
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.formatFullCurrency

@Composable
fun AllTransactionsDragHandleAndHeaderBar(
    headerDragOffsetY: Float,
    onUpdateHeaderDragOffsetY: (Float) -> Unit,
    onDismiss: () -> Unit,
    shownCount: Int,
    totalCount: Int
) {
    // Vertical drag handle for swipe dismissal (identical to ExpenseSharesScreen.kt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onUpdateHeaderDragOffsetY(0f) },
                    onDragEnd = {
                        if (headerDragOffsetY > 80f) {
                            onDismiss()
                        } else {
                            onUpdateHeaderDragOffsetY(0f)
                        }
                    },
                    onDragCancel = { onUpdateHeaderDragOffsetY(0f) },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0f || headerDragOffsetY > 0f) {
                            onUpdateHeaderDragOffsetY((headerDragOffsetY + dragAmount).coerceAtLeast(0f))
                        }
                    }
                )
            }
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Slate700)
        )
    }

    // Header Bar (Cleaned without accounts & transfers)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Emerald400, Indigo500, Rose500)
                        )
                    )
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(DarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = "Все операции",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Показано $shownCount из $totalCount операций",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Slate800)
                .border(1.dp, Slate700, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = Slate400,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun CompactScrolledSummaryHeader(
    filterType: String,
    totalExpenseAmt: Double,
    totalIncomeAmt: Double,
    currentActiveCategoryTotals: List<Pair<String, Double>>,
    selectedCategoryFilter: String?,
    isDrilledDownToMixed: Boolean,
    remainingCategoryNames: List<String>,
    getCategoryColor: (String) -> Color,
    onResetToAll: () -> Unit
) {
    val isExpense = filterType == "expense"
    val accentColor = if (isExpense) Rose500 else Emerald400
    val activeAmount = if (isExpense) totalExpenseAmt else totalIncomeAmt
    val titleText = if (isExpense) "Траты" else "Доходы"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Slate900)
            .border(1.dp, Slate800, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Small doughnut/circular chart
            CompactMiniDonutChart(
                categoryTotals = currentActiveCategoryTotals,
                getCategoryColor = getCategoryColor
            )

            Column {
                Text(
                    text = titleText,
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatFullCurrency(activeAmount),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        val activeFilterText = when {
            !selectedCategoryFilter.isNullOrBlank() -> selectedCategoryFilter
            isDrilledDownToMixed -> {
                if (remainingCategoryNames.size <= 1) "✨ Прочие" else "✨ Смешанные"
            }
            else -> null
        }

        if (!activeFilterText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = activeFilterText,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        IconButton(
            onClick = onResetToAll,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Slate800)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "К всем",
                tint = Slate400,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun AllTransactionsCollapsibleHeader(
    showCompactHeader: Boolean,
    filterType: String,
    onFilterTypeChange: (String) -> Unit,
    totalExpenseAmt: Double,
    totalIncomeAmt: Double,
    currentActiveCategoryTotals: List<Pair<String, Double>>,
    selectedCategoryFilter: String?,
    onSelectCategoryFilter: (String?) -> Unit,
    isDrilledDownToMixed: Boolean,
    onSetDrilledDownToMixed: (Boolean) -> Unit,
    remainingCategoryNames: List<String>,
    selectedPeriod: String,
    onSelectPeriod: (String) -> Unit,
    currentOffsetInt: Int,
    transactions: List<TransactionEntity>,
    visibleDateStr: String,
    dynamicMonthLabel: String,
    chartViewMode: String,
    onSelectChartViewMode: (String) -> Unit,
    stableAnchorDateStr: String,
    categoryTotalsMap: List<Pair<String, Double>>,
    shouldGroup: Boolean,
    getFilteredListForOffset: (Int) -> List<TransactionEntity>,
    getCategoryTotalsForOffset: (Int) -> List<Pair<String, Double>>,
    getCategoryColor: (String) -> Color,
    onSwipePeriodOffset: (Int) -> Unit,
    onOpenMixedCategoriesDialog: () -> Unit
) {
    AnimatedContent(
        targetState = showCompactHeader,
        transitionSpec = {
            (fadeIn(animationSpec = tween(180, delayMillis = 60)) + scaleIn(initialScale = 0.96f, animationSpec = tween(180, delayMillis = 60)))
                .togetherWith(fadeOut(animationSpec = tween(100)) + scaleOut(targetScale = 0.98f, animationSpec = tween(100)))
        },
        label = "header_morph_animation"
    ) { targetCompact ->
        if (targetCompact) {
            CompactScrolledSummaryHeader(
                filterType = filterType,
                totalExpenseAmt = totalExpenseAmt,
                totalIncomeAmt = totalIncomeAmt,
                currentActiveCategoryTotals = currentActiveCategoryTotals,
                selectedCategoryFilter = selectedCategoryFilter,
                isDrilledDownToMixed = isDrilledDownToMixed,
                remainingCategoryNames = remainingCategoryNames,
                getCategoryColor = getCategoryColor,
                onResetToAll = {
                    onFilterTypeChange("all")
                    onSelectCategoryFilter(null)
                    onSetDrilledDownToMixed(false)
                }
            )
        } else {
            // Full-Size UI Header
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pinned Hero Card (Expenses / Income / Both)
                if (filterType == "all") {
                    DoubleSummaryComparisonCards(
                        totalExpenseAmt = totalExpenseAmt,
                        totalIncomeAmt = totalIncomeAmt,
                        dynamicMonthLabel = dynamicMonthLabel,
                        filterType = filterType,
                        onSelectExpense = {
                            if (filterType == "expense") {
                                onSelectPeriod("month")
                            } else {
                                onFilterTypeChange("expense")
                            }
                        },
                        onSelectIncome = {
                            if (filterType == "income") {
                                onSelectPeriod("month")
                            } else {
                                onFilterTypeChange("income")
                            }
                        }
                    )
                } else {
                    SingleHeroComparisonCard(
                        filterType = filterType,
                        totalExpenseAmt = totalExpenseAmt,
                        totalIncomeAmt = totalIncomeAmt,
                        selectedPeriod = selectedPeriod,
                        currentOffsetInt = currentOffsetInt,
                        transactions = transactions,
                        visibleDateStr = visibleDateStr,
                        dynamicMonthLabel = dynamicMonthLabel,
                        chartViewMode = chartViewMode,
                        stableAnchorDateStr = stableAnchorDateStr,
                        getFilteredListForOffset = getFilteredListForOffset,
                        getCategoryTotalsForOffset = getCategoryTotalsForOffset,
                        getCategoryColor = getCategoryColor,
                        onResetFilterToAll = { onFilterTypeChange("all") },
                        onSwipePeriodOffset = onSwipePeriodOffset
                    ) {
                        TransactionCategoryChipsSection(
                            selectedPeriod = selectedPeriod,
                            onSelectPeriod = onSelectPeriod,
                            chartViewMode = chartViewMode,
                            onSelectChartViewMode = onSelectChartViewMode,
                            categoryTotalsMap = categoryTotalsMap,
                            shouldGroup = shouldGroup,
                            isDrilledDownToMixed = isDrilledDownToMixed,
                            onSetDrilledDownToMixed = onSetDrilledDownToMixed,
                            selectedCategoryFilter = selectedCategoryFilter,
                            onSelectCategoryFilter = onSelectCategoryFilter,
                            onOpenMixedCategoriesDialog = onOpenMixedCategoriesDialog,
                            getCategoryColor = getCategoryColor
                        )
                    }
                }
            }
        }
    }
}
