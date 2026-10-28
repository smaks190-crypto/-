package com.example.ui.components.dialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.formatFullCurrency
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun AllTransactionsDragHandleAndHeaderBar(
    headerDragOffsetY: Float,
    onUpdateHeaderDragOffsetY: (Float) -> Unit,
    onDismiss: () -> Unit,
    shownCount: Int,
    totalCount: Int
) {
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
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Slate700)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Emerald400, Indigo500, Rose500)
                        )
                    )
                    .padding(1.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.5.dp))
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
                    fontFamily = ManropeFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Показано $shownCount из $totalCount операций",
                    color = Slate400,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("header_counter_text")
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
    dynamicMonthLabel: String = "",
    currentActiveCategoryTotals: List<Pair<String, Double>>,
    selectedCategoryFilter: String?,
    isDrilledDownToMixed: Boolean,
    remainingCategoryNames: List<String>,
    getCategoryColor: (String) -> Color,
    onExpandChart: () -> Unit = {},
    onResetToAll: () -> Unit
) {
    val isExpense = filterType == "expense"
    val accentColor = if (isExpense) Rose500 else Emerald400
    val activeAmount = if (isExpense) totalExpenseAmt else totalIncomeAmt
    val titleText = if (isExpense) "Траты" else "Доходы"
    val subtitleLabel = if (dynamicMonthLabel.isNotBlank()) "$titleText • $dynamicMonthLabel" else titleText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF11192B),
                        Slate900
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable { onExpandChart() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            CompactMiniDonutChart(
                categoryTotals = currentActiveCategoryTotals,
                getCategoryColor = getCategoryColor
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subtitleLabel,
                    color = Slate400,
                    fontFamily = ManropeFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFullCurrency(activeAmount),
                    color = Color.White,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!activeFilterText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = activeFilterText,
                        color = accentColor,
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Slate800.copy(alpha = 0.85f))
                    .testTag("compact_expand_chart_button")
                    .clickable { onExpandChart() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "График",
                    color = Slate300,
                    fontFamily = ManropeFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Развернуть график",
                    tint = Slate300,
                    modifier = Modifier.size(14.dp)
                )
            }

            IconButton(
                onClick = onResetToAll,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Slate800)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "К всем",
                    tint = Slate400,
                    modifier = Modifier.size(13.dp)
                )
            }
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
    selectedPeriod: String?,
    onSelectPeriod: (String) -> Unit,
    currentOffsetInt: Int,
    swipeMonthOffset: Int = 0,
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
    onOpenMixedCategoriesDialog: () -> Unit,
    onExpandChart: () -> Unit = {},
    onCollapseChart: () -> Unit = {}
) {
    AnimatedContent(
        targetState = showCompactHeader,
        transitionSpec = {
            fadeIn(animationSpec = tween(180))
                .togetherWith(fadeOut(animationSpec = tween(140)))
                .using(SizeTransform(clip = true))
        },
        label = "header_morph_animation"
    ) { targetCompact ->
        if (targetCompact) {
            CompactScrolledSummaryHeader(
                filterType = filterType,
                totalExpenseAmt = totalExpenseAmt,
                totalIncomeAmt = totalIncomeAmt,
                dynamicMonthLabel = dynamicMonthLabel,
                currentActiveCategoryTotals = currentActiveCategoryTotals,
                selectedCategoryFilter = selectedCategoryFilter,
                isDrilledDownToMixed = isDrilledDownToMixed,
                remainingCategoryNames = remainingCategoryNames,
                getCategoryColor = getCategoryColor,
                onExpandChart = onExpandChart,
                onResetToAll = {
                    onFilterTypeChange("all")
                    onSelectCategoryFilter(null)
                    onSetDrilledDownToMixed(false)
                }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (filterType == "all") {
                    DoubleSummaryComparisonCards(
                        totalExpenseAmt = totalExpenseAmt,
                        totalIncomeAmt = totalIncomeAmt,
                        dynamicMonthLabel = dynamicMonthLabel,
                        filterType = filterType,
                        onSelectExpense = {
                            onFilterTypeChange("expense")
                        },
                        onSelectIncome = {
                            onFilterTypeChange("income")
                        },
                        onSwipePeriodOffset = onSwipePeriodOffset,
                        isSwipeEnabled = resolveEffectivePeriod(selectedPeriod) != "custom"
                    )
                } else {
                    SingleHeroComparisonCard(
                        filterType = filterType,
                        totalExpenseAmt = totalExpenseAmt,
                        totalIncomeAmt = totalIncomeAmt,
                        selectedPeriod = selectedPeriod,
                        currentOffsetInt = currentOffsetInt,
                        swipeMonthOffset = swipeMonthOffset,
                        transactions = transactions,
                        visibleDateStr = visibleDateStr,
                        dynamicMonthLabel = dynamicMonthLabel,
                        chartViewMode = chartViewMode,
                        stableAnchorDateStr = stableAnchorDateStr,
                        getFilteredListForOffset = getFilteredListForOffset,
                        getCategoryTotalsForOffset = getCategoryTotalsForOffset,
                        getCategoryColor = getCategoryColor,
                        onResetFilterToAll = { onFilterTypeChange("all") },
                        onSwipePeriodOffset = onSwipePeriodOffset,
                        onCollapseChart = onCollapseChart
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
