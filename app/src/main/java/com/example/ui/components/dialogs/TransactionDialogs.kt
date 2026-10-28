package com.example.ui.components.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.db.TransactionEntity
import com.example.ui.components.DateRangePickerDialog
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllTransactionsDialog(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: ((String) -> Unit)? = null,
    onRestoreTransaction: ((TransactionEntity) -> Unit)? = null,
    onEditTransaction: ((TransactionEntity) -> Unit)? = null,
    initialFilterType: String = "all",
    initialDate: String? = null,
    onDismiss: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf("date") }
    var filterType by remember { mutableStateOf(initialFilterType) } // "all", "income", "expense"
    var chartViewMode by remember { mutableStateOf("donut") } // "donut", "bar"
    var showDatePickerModal by remember { mutableStateOf(false) }
    var isDrilledDownToMixed by remember { mutableStateOf(false) }
    var showMixedCategoriesDialog by remember { mutableStateOf(false) }
    var isChartCollapsed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Non-oscillating NestedScrollConnection:
    // Collapses the chart only on deliberate downward finger drag when the list can scroll forward,
    // and expands only when pulling down at the very top of the list (or via manual tap).
    val chartCollapseScrollConnection = remember(filterType) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && filterType != "all") {
                    if (available.y < -18f && !isChartCollapsed &&
                        (lazyListState.canScrollForward || lazyListState.firstVisibleItemIndex > 0)
                    ) {
                        isChartCollapsed = true
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag && filterType != "all") {
                    if (available.y > 18f && isChartCollapsed &&
                        lazyListState.firstVisibleItemIndex == 0 &&
                        lazyListState.firstVisibleItemScrollOffset == 0
                    ) {
                        isChartCollapsed = false
                    }
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(filterType) {
        isDrilledDownToMixed = false
        selectedCategoryFilter = null
        if (filterType == "all") {
            isChartCollapsed = false
        }
    }

    // Initial anchor date: uses initialDate if provided, otherwise current calendar date.
    val defaultAnchor = remember(initialDate) {
        resolveInitialAnchorDate(initialDate)
    }

    // Separated period state:
    // - displayedMonthDateStr / swipeMonthOffset controls the currently displayed month
    // - selectedPeriod (null by default) controls whether a period lock ("week", "month", "year", "custom") is active
    var periodState by remember(defaultAnchor) {
        mutableStateOf(AllTransactionsPeriodState(stableAnchorDateStr = defaultAnchor))
    }

    val stableAnchorDateStr = periodState.stableAnchorDateStr
    val selectedPeriod = periodState.selectedPeriod
    val swipeMonthOffset = periodState.swipeMonthOffset
    val swipeWeekOffset = periodState.swipeWeekOffset
    val swipeYearOffset = periodState.swipeYearOffset
    val customStartStr = periodState.customStartStr
    val customEndStr = periodState.customEndStr
    val customLabelStr = periodState.customLabelStr
    val currentOffsetInt = periodState.currentOffsetInt

    var headerDragOffsetY by remember { mutableFloatStateOf(0f) }

    val visibleDateStr = remember(transactions, periodState.displayedMonthDateStr) {
        derivedStateOf {
            if (transactions.isNotEmpty()) {
                val idx = (lazyListState.firstVisibleItemIndex - 2).coerceIn(0, transactions.size - 1)
                transactions.getOrNull(idx)?.date ?: periodState.displayedMonthDateStr
            } else {
                periodState.displayedMonthDateStr
            }
        }
    }

    LaunchedEffect(selectedPeriod, swipeMonthOffset, swipeWeekOffset, swipeYearOffset) {
        isDrilledDownToMixed = false
        selectedCategoryFilter = null
    }

    val dynamicMonthLabel = remember(
        stableAnchorDateStr,
        customLabelStr,
        selectedPeriod,
        swipeMonthOffset,
        swipeWeekOffset,
        swipeYearOffset
    ) {
        buildDynamicPeriodLabel(
            stableAnchorDateStr = stableAnchorDateStr,
            customLabelStr = customLabelStr,
            selectedPeriod = selectedPeriod,
            swipeMonthOffset = swipeMonthOffset,
            swipeWeekOffset = swipeWeekOffset,
            swipeYearOffset = swipeYearOffset
        )
    }

    fun getFilteredListForOffset(offset: Int): List<TransactionEntity> =
        filterTransactionsForOffset(
            transactions = transactions,
            selectedPeriod = selectedPeriod,
            customStartStr = customStartStr,
            customEndStr = customEndStr,
            stableAnchorDateStr = stableAnchorDateStr,
            currentOffsetInt = currentOffsetInt,
            offset = offset,
            swipeMonthOffset = swipeMonthOffset
        )

    // List of transactions for the currently displayed period (current/swiped month by default)
    val periodFilteredList = remember(
        transactions,
        selectedPeriod,
        customStartStr,
        customEndStr,
        stableAnchorDateStr,
        swipeWeekOffset,
        swipeMonthOffset,
        swipeYearOffset
    ) {
        getFilteredListForOffset(currentOffsetInt)
    }

    fun getCategoryTotalsForOffset(offset: Int): List<Pair<String, Double>> =
        calculateCategoryTotalsForOffset(
            rawList = getFilteredListForOffset(offset),
            filterType = filterType,
            isDrilledDownToMixed = isDrilledDownToMixed
        )

    val typeFilteredList = remember(periodFilteredList, filterType) {
        when (filterType) {
            "income" -> periodFilteredList.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> periodFilteredList.filter { it.type.equals("expense", ignoreCase = true) }
            else -> periodFilteredList
        }
    }

    val categoryTotalsMap = remember(typeFilteredList) {
        typeFilteredList
            .groupBy { it.category.ifBlank { "Прочее" } }
            .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }
    }

    val categoryThreshold = 3
    val shouldGroup = categoryTotalsMap.size > categoryThreshold

    val remainingCategoryNames = remember(categoryTotalsMap) {
        if (categoryTotalsMap.size > categoryThreshold) {
            categoryTotalsMap.drop(2).map { it.first }
        } else {
            emptyList()
        }
    }

    val categoryColorMap = remember(categoryTotalsMap, filterType) {
        buildCategoryColorMap(categoryTotalsMap, filterType)
    }

    fun getCategoryColor(name: String): Color =
        resolveCategoryColor(name, categoryColorMap, filterType)

    val currentActiveCategoryTotals = remember(categoryTotalsMap, isDrilledDownToMixed) {
        computeActiveCategoryTotals(categoryTotalsMap, isDrilledDownToMixed, categoryThreshold)
    }

    val sortedList = remember(typeFilteredList, searchQuery, selectedCategoryFilter, isDrilledDownToMixed, remainingCategoryNames, sortOption) {
        filterAndSortTransactions(
            typeFilteredList = typeFilteredList,
            searchQuery = searchQuery,
            selectedCategoryFilter = selectedCategoryFilter,
            isDrilledDownToMixed = isDrilledDownToMixed,
            remainingCategoryNames = remainingCategoryNames,
            sortOption = sortOption
        )
    }

    val groupedByDate = remember(sortedList) {
        sortedList.groupBy { it.date }
    }

    val totalExpenseAmt = remember(periodFilteredList) {
        periodFilteredList.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { kotlin.math.abs(it.amount) }
    }
    val totalIncomeAmt = remember(periodFilteredList) {
        periodFilteredList.filter { it.type.equals("income", ignoreCase = true) }.sumOf { kotlin.math.abs(it.amount) }
    }

    val animatedHeaderDragOffsetY by animateFloatAsState(
        targetValue = headerDragOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "header_drag_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = 0, y = animatedHeaderDragOffsetY.roundToInt()) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            AllTransactionsDragHandleAndHeaderBar(
                headerDragOffsetY = headerDragOffsetY,
                onUpdateHeaderDragOffsetY = { headerDragOffsetY = it },
                onDismiss = onDismiss,
                shownCount = sortedList.size,
                totalCount = periodFilteredList.size
            )

            AllTransactionsCollapsibleHeader(
                showCompactHeader = isChartCollapsed && filterType != "all",
                filterType = filterType,
                onFilterTypeChange = { filterType = it },
                totalExpenseAmt = totalExpenseAmt,
                totalIncomeAmt = totalIncomeAmt,
                currentActiveCategoryTotals = currentActiveCategoryTotals,
                selectedCategoryFilter = selectedCategoryFilter,
                onSelectCategoryFilter = { selectedCategoryFilter = it },
                isDrilledDownToMixed = isDrilledDownToMixed,
                onSetDrilledDownToMixed = { isDrilledDownToMixed = it },
                remainingCategoryNames = remainingCategoryNames,
                selectedPeriod = selectedPeriod,
                onSelectPeriod = { clickedPeriod ->
                    periodState = periodState.onTogglePeriodTab(clickedPeriod)
                },
                currentOffsetInt = currentOffsetInt,
                swipeMonthOffset = swipeMonthOffset,
                transactions = transactions,
                visibleDateStr = visibleDateStr.value,
                dynamicMonthLabel = dynamicMonthLabel,
                chartViewMode = chartViewMode,
                onSelectChartViewMode = { chartViewMode = it },
                stableAnchorDateStr = stableAnchorDateStr,
                categoryTotalsMap = categoryTotalsMap,
                shouldGroup = shouldGroup,
                getFilteredListForOffset = ::getFilteredListForOffset,
                getCategoryTotalsForOffset = ::getCategoryTotalsForOffset,
                getCategoryColor = ::getCategoryColor,
                onSwipePeriodOffset = { delta ->
                    periodState = periodState.onSwipePeriod(delta)
                },
                onOpenMixedCategoriesDialog = { showMixedCategoriesDialog = true },
                onExpandChart = { isChartCollapsed = false },
                onCollapseChart = { isChartCollapsed = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            TransactionSortAndSearchBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                focusRequester = focusRequester,
                selectedCategoryFilter = selectedCategoryFilter,
                isDrilledDownToMixed = isDrilledDownToMixed,
                remainingCategoryNames = remainingCategoryNames,
                onClearCategoryFilter = {
                    selectedCategoryFilter = null
                    isDrilledDownToMixed = false
                },
                getCategoryColor = ::getCategoryColor,
                selectedPeriod = selectedPeriod,
                customStartStr = customStartStr,
                dynamicMonthLabel = dynamicMonthLabel,
                onOpenDatePickerModal = { showDatePickerModal = true },
                onResetPeriodLock = {
                    periodState = periodState.onClearPeriodLock()
                },
                sortOption = sortOption,
                onSortOptionChange = { sortOption = it }
            )

            Spacer(modifier = Modifier.height(6.dp))

            TransactionListSection(
                lazyListState = lazyListState,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                sortedList = sortedList,
                groupedByDate = groupedByDate,
                filterType = filterType,
                allTransactions = transactions,
                onDeleteTransaction = onDeleteTransaction,
                onRestoreTransaction = onRestoreTransaction,
                onEditTransaction = onEditTransaction,
                coroutineScope = coroutineScope,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.nestedScroll(chartCollapseScrollConnection)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = Slate900,
                contentColor = Color.White,
                actionColor = Emerald400,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showDatePickerModal) {
            DateRangePickerDialog(
                initialStart = customStartStr ?: periodState.displayedMonthDateStr,
                initialEnd = customEndStr ?: periodState.displayedMonthDateStr,
                onDismiss = { showDatePickerModal = false },
                onConfirm = { s, e ->
                    val label = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val d1 = sdf.parse(s)
                        val d2 = sdf.parse(e)
                        if (d1 != null && d2 != null) {
                            val outSdf = SimpleDateFormat("d MMM", Locale("ru", "RU"))
                            "${outSdf.format(d1)} - ${outSdf.format(d2)}"
                        } else "$s - $e"
                    } catch (ex: Exception) { "$s - $e" }
                    periodState = periodState.onApplyCustomDateRange(s, e, label)
                    showDatePickerModal = false
                }
            )
        }

        if (showMixedCategoriesDialog) {
            MixedCategoriesDialog(
                categoryTotalsMap = categoryTotalsMap,
                getCategoryColor = ::getCategoryColor,
                onSelectCategory = { catName ->
                    selectedCategoryFilter = catName
                    isDrilledDownToMixed = true
                    showMixedCategoriesDialog = false
                },
                onDismiss = { showMixedCategoriesDialog = false }
            )
        }
    }
}
