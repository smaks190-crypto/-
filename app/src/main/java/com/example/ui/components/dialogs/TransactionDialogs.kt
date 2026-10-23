package com.example.ui.components.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.db.TransactionEntity
import com.example.ui.components.DateRangePickerDialog
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.Date
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
    val isScrolled by remember {
        androidx.compose.runtime.derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 40
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf("date") }
    var filterType by remember { mutableStateOf(initialFilterType) } // "all", "income", "expense"
    var selectedPeriod by remember { mutableStateOf("month") } // "all", "week", "month", "year", "custom"
    var chartViewMode by remember { mutableStateOf("donut") } // "donut", "bar"
    var selectedAccountFilter by remember { mutableStateOf<String?>(null) } // null = all, "Black" etc.
    var excludeTransfers by remember { mutableStateOf(false) }
    var showDatePickerModal by remember { mutableStateOf(false) }
    var isCategoriesExpanded by remember { mutableStateOf(false) }
    var isDrilledDownToMixed by remember { mutableStateOf(false) }
    var isSearchExpandedInPlace by remember { mutableStateOf(false) }
    var showMixedCategoriesDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isScrolled) {
        if (!isScrolled) {
            isSearchExpandedInPlace = false
        }
    }

    LaunchedEffect(isSearchExpandedInPlace) {
        if (isSearchExpandedInPlace) {
            focusRequester.requestFocus()
        }
    }

    // Custom date range bounds
    var customStartStr by remember { mutableStateOf<String?>(null) }
    var customEndStr by remember { mutableStateOf<String?>(null) }
    var customLabelStr by remember { mutableStateOf<String?>(null) }

    // Synchronized current month name from first visible item on scroll
    val visibleDateStr = remember(transactions) {
        androidx.compose.runtime.derivedStateOf {
            if (transactions.isNotEmpty()) {
                val idx = (lazyListState.firstVisibleItemIndex - 4).coerceIn(0, transactions.size - 1)
                transactions.getOrNull(idx)?.date ?: transactions.first().date
            } else "2026-08-01"
        }
    }

    val defaultAnchor = remember(initialDate, transactions) {
        if (!initialDate.isNullOrBlank()) {
            initialDate
        } else if (transactions.isNotEmpty()) {
            transactions.maxByOrNull { it.date }?.date ?: transactions.first().date
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
    }

    var stableAnchorDateStr by remember(defaultAnchor) { mutableStateOf(defaultAnchor) }
    var swipeWeekOffset by remember { mutableStateOf(0) }
    var swipeMonthOffset by remember { mutableStateOf(0) }
    var swipeYearOffset by remember { mutableStateOf(0) }
    var headerDragOffsetY by remember { mutableFloatStateOf(0f) }

    val currentOffsetInt = when (selectedPeriod) {
        "week" -> swipeWeekOffset
        "month" -> swipeMonthOffset
        "year" -> swipeYearOffset
        else -> swipeMonthOffset
    }

    LaunchedEffect(filterType) {
        isDrilledDownToMixed = false
        selectedCategoryFilter = null
    }

    LaunchedEffect(selectedPeriod) {
        if (stableAnchorDateStr.isBlank()) {
            stableAnchorDateStr = defaultAnchor
        }
        isDrilledDownToMixed = false
        selectedCategoryFilter = null
    }

    val dynamicMonthLabel = remember(stableAnchorDateStr, customLabelStr, selectedPeriod, swipeMonthOffset, swipeWeekOffset, swipeYearOffset) {
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
            offset = offset
        )

    // Filter transactions by period ("week", "month", "year", "custom", "all") synchronized with chart & header
    val periodFilteredList = remember(transactions, selectedPeriod, customStartStr, customEndStr, stableAnchorDateStr, swipeWeekOffset, swipeMonthOffset, swipeYearOffset) {
        getFilteredListForOffset(currentOffsetInt)
    }

    fun getCategoryTotalsForOffset(offset: Int): List<Pair<String, Double>> =
        calculateCategoryTotalsForOffset(
            rawList = getFilteredListForOffset(offset),
            filterType = filterType,
            isDrilledDownToMixed = isDrilledDownToMixed
        )

    // Filtered by type ("expense", "income", "all")
    val typeFilteredList = remember(periodFilteredList, filterType) {
        when (filterType) {
            "income" -> periodFilteredList.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> periodFilteredList.filter { it.type.equals("expense", ignoreCase = true) }
            else -> periodFilteredList
        }
    }

    // Category sums map for current filter (strictly synchronized with typeFilteredList)
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

    // Search, category filtered and sorted
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

    SwipeToDismissDialog(
        onDismissRequest = onDismiss,
        isAtTop = { lazyListState.firstVisibleItemIndex == 0 },
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.96f)
                    .offset { IntOffset(x = 0, y = animatedHeaderDragOffsetY.roundToInt()) },
                color = DarkBg,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    AllTransactionsDragHandleAndHeaderBar(
                        headerDragOffsetY = headerDragOffsetY,
                        onUpdateHeaderDragOffsetY = { headerDragOffsetY = it },
                        onDismiss = onDismiss,
                        shownCount = sortedList.size,
                        totalCount = transactions.size
                    )

                    // Pinned Collapsible / Morphing Header or Full-Size Components
                    AllTransactionsCollapsibleHeader(
                        showCompactHeader = isScrolled && filterType != "all",
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
                        onSelectPeriod = { selectedPeriod = it },
                        currentOffsetInt = currentOffsetInt,
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
                            when (selectedPeriod) {
                                "week" -> swipeWeekOffset += delta
                                "month" -> swipeMonthOffset += delta
                                "year" -> swipeYearOffset += delta
                                else -> {
                                    customStartStr = null
                                    customEndStr = null
                                    customLabelStr = null
                                    selectedPeriod = "month"
                                    swipeMonthOffset += delta
                                }
                            }
                        },
                        onOpenMixedCategoriesDialog = { showMixedCategoriesDialog = true }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Pinned Sticky Sorting Options Row (outside LazyColumn)
                    TransactionSortAndSearchBar(
                        isSearchExpandedInPlace = isSearchExpandedInPlace,
                        onSetSearchExpandedInPlace = { isSearchExpandedInPlace = it },
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
                        onResetPeriodToAll = {
                            selectedPeriod = "all"
                            customStartStr = null
                            customEndStr = null
                            customLabelStr = null
                        },
                        sortOption = sortOption,
                        onSortOptionChange = { sortOption = it },
                        isScrolled = isScrolled
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
                        snackbarHostState = snackbarHostState
                    )
                }
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
        }

        // Date Range Selection Dialog Modal
        if (showDatePickerModal) {
            DateRangePickerDialog(
                initialStart = customStartStr ?: visibleDateStr.value.ifBlank { "2026-07-01" },
                initialEnd = customEndStr ?: "2026-08-31",
                onDismiss = { showDatePickerModal = false },
                onConfirm = { s, e ->
                    customStartStr = s
                    customEndStr = e
                    selectedPeriod = "custom"
                    customLabelStr = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val d1 = sdf.parse(s)
                        val d2 = sdf.parse(e)
                        if (d1 != null && d2 != null) {
                            val outSdf = SimpleDateFormat("d MMM", Locale("ru", "RU"))
                            "${outSdf.format(d1)} - ${outSdf.format(d2)}"
                        } else "$s - $e"
                    } catch (ex: Exception) { "$s - $e" }
                    showDatePickerModal = false
                }
            )
        }

        // Mixed Categories Pop-Up Window/Dialog
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
