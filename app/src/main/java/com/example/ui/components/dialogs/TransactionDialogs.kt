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
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllTransactionsDialog(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: (String) -> Unit,
    onDismiss: () -> Unit,
    initialFilterType: String = "all",
    initialDate: String? = null,
    onRestoreTransaction: ((TransactionEntity) -> Unit)? = null,
    onEditTransaction: ((TransactionEntity) -> Unit)? = null
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 40
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("date") } // "date", "desc", "asc", "name", "name_desc"
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var filterType by remember { mutableStateOf(initialFilterType) } // "all", "income", "expense"
    var chartViewMode by remember { mutableStateOf("donut") } // "donut", "bar"
    var showDatePickerModal by remember { mutableStateOf(false) }
    var isDrilledDownToMixed by remember { mutableStateOf(false) }
    var isSearchExpandedInPlace by remember { mutableStateOf(false) }
    var showMixedCategoriesDialog by remember { mutableStateOf(false) }
    var isChartCollapsed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Неосциллирующий NestedScrollConnection:
    // Сворачивает график только при явном скролле вниз пальцем, и разворачивает при скролле в самом верху списка.
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

    val stableAnchorDateStr = remember(transactions, initialDate) {
        initialDate?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            ?: transactions.firstOrNull()?.date
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // Единое состояние периода:
    // По умолчанию (selectedPeriod == null) период НЕ зафиксирован: список отображает всю историю операций
    // (можно свободно листать из Октября в Сентябрь и ниже), а чип месяца и графики автоматически
    // подстраиваются под текущий видимый месяц при прокрутке.
    var periodState by remember(stableAnchorDateStr) {
        mutableStateOf(AllTransactionsPeriodState(stableAnchorDateStr = stableAnchorDateStr))
    }

    LaunchedEffect(periodState.selectedPeriod) {
        isDrilledDownToMixed = false
        selectedCategoryFilter = null
    }

    val dynamicMonthLabel = remember(
        stableAnchorDateStr,
        periodState
    ) {
        buildDynamicPeriodLabel(
            stableAnchorDateStr = periodState.stableAnchorDateStr,
            customLabelStr = periodState.customLabelStr,
            selectedPeriod = periodState.selectedPeriod,
            swipeMonthOffset = periodState.swipeMonthOffset,
            swipeWeekOffset = periodState.swipeWeekOffset,
            swipeYearOffset = periodState.swipeYearOffset
        )
    }

    val getFilteredListForOffset: (Int) -> List<TransactionEntity> = { offset ->
        filterTransactionsForOffset(
            transactions = transactions,
            selectedPeriod = periodState.selectedPeriod,
            customStartStr = periodState.customStartStr,
            customEndStr = periodState.customEndStr,
            stableAnchorDateStr = periodState.stableAnchorDateStr,
            currentOffsetInt = periodState.currentOffsetInt,
            offset = offset,
            swipeMonthOffset = periodState.swipeMonthOffset
        )
    }

    // Транзакции для верхних карточек («Траты • Месяц» / «Доходы • Месяц») и диаграмм —
    // всегда соответствуют текущему отображаемому периоду/месяцу.
    val chartPeriodFilteredList = remember(transactions, periodState) {
        getFilteredListForOffset(periodState.currentOffsetInt)
    }

    // Транзакции для самого списка:
    // - Если период НЕ зафиксирован (periodState.isPeriodLocked == false), показываем все транзакции,
    //   чтобы пользователь мог бесшовно скроллить из Октября в Сентябрь, Август и т.д.
    // - Если период зафиксирован (нажат чип месяца, выбрана вкладка Нед/Мес/Год или кастомные даты),
    //   фильтруем список строго по выбранному периоду.
    val listPeriodFilteredTransactions = remember(transactions, periodState) {
        if (periodState.isPeriodLocked) {
            chartPeriodFilteredList
        } else {
            transactions
        }
    }

    val getCategoryTotalsForOffset: (Int) -> List<Pair<String, Double>> = { offset ->
        calculateCategoryTotalsForOffset(
            rawList = getFilteredListForOffset(offset),
            filterType = filterType,
            isDrilledDownToMixed = isDrilledDownToMixed
        )
    }

    val chartTypeFilteredList = remember(chartPeriodFilteredList, filterType) {
        when (filterType) {
            "income" -> chartPeriodFilteredList.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> chartPeriodFilteredList.filter { it.type.equals("expense", ignoreCase = true) }
            else -> chartPeriodFilteredList
        }
    }

    val listTypeFilteredTransactions = remember(listPeriodFilteredTransactions, filterType) {
        when (filterType) {
            "income" -> listPeriodFilteredTransactions.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> listPeriodFilteredTransactions.filter { it.type.equals("expense", ignoreCase = true) }
            else -> listPeriodFilteredTransactions
        }
    }

    val categoryTotalsMap = remember(chartTypeFilteredList) {
        chartTypeFilteredList
            .groupBy { it.category.ifBlank { "Прочее" } }
            .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
            .toList()
            .sortedByDescending { it.second }
    }

    val totalCategorySum = remember(categoryTotalsMap) {
        categoryTotalsMap.sumOf { it.second }
    }
    val categoryThreshold = totalCategorySum * 0.07

    val remainingCategoryNames = remember(categoryTotalsMap, categoryThreshold) {
        val smallCats = categoryTotalsMap.filter { it.second < categoryThreshold }
        val largeCats = categoryTotalsMap.filter { it.second >= categoryThreshold }
        if (smallCats.size >= 2 && largeCats.isNotEmpty()) {
            smallCats.map { it.first }
        } else if (categoryTotalsMap.size > 3) {
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

    val currentActiveCategoryTotals = remember(categoryTotalsMap, isDrilledDownToMixed, categoryThreshold) {
        computeActiveCategoryTotals(categoryTotalsMap, isDrilledDownToMixed, categoryThreshold)
    }

    val sortedList = remember(listTypeFilteredTransactions, searchQuery, selectedCategoryFilter, isDrilledDownToMixed, remainingCategoryNames, sortOption) {
        filterAndSortTransactions(
            typeFilteredList = listTypeFilteredTransactions,
            searchQuery = searchQuery,
            selectedCategoryFilter = selectedCategoryFilter,
            isDrilledDownToMixed = isDrilledDownToMixed,
            remainingCategoryNames = remainingCategoryNames,
            sortOption = sortOption
        )
    }

    // Group by date when sorting by date
    val groupedByDate = remember(sortedList, sortOption) {
        if (sortOption == "date") {
            sortedList.groupBy { it.date }
        } else {
            mapOf("Все подходящие операции" to sortedList)
        }
    }

    // Плоский список дат для каждого элемента LazyColumn (header + items),
    // чтобы при скролле точно знать, какой месяц сейчас находится в верхней части списка.
    val lazyItemDates = remember(groupedByDate) {
        buildList {
            groupedByDate.forEach { (dateKey, dayItems) ->
                val validDate = dayItems.firstOrNull()?.date ?: dateKey
                add(validDate) // Заголовок дня
                dayItems.forEach { tx ->
                    add(tx.date)
                }
            }
        }
    }

    // Синхронизация месяца при вертикальном скролле списка (когда фильтр периода не заблокирован):
    // как только пользователь долистывает до операций Сентября, чип «Октябрь» меняется на «Сентябрь»,
    // и суммы/графики в шапке автоматически показывают данные за Сентябрь.
    LaunchedEffect(lazyListState, lazyItemDates, periodState.isPeriodLocked, sortOption) {
        if (!periodState.isPeriodLocked && sortOption == "date" && lazyItemDates.isNotEmpty()) {
            snapshotFlow { lazyListState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstIdx ->
                    if (lazyListState.isScrollInProgress) {
                        val visibleDate = lazyItemDates.getOrNull(firstIdx.coerceIn(0, lazyItemDates.lastIndex))
                        if (!visibleDate.isNullOrBlank() && visibleDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                            periodState = periodState.onScrolledToDate(visibleDate)
                        }
                    }
                }
        }
    }

    val visibleDateStr = remember(lazyItemDates, periodState.displayedMonthDateStr) {
        derivedStateOf {
            val idx = lazyListState.firstVisibleItemIndex
            lazyItemDates.getOrNull(idx)?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                ?: periodState.displayedMonthDateStr
        }
    }

    // При свайпе графика влево/вправо в незаблокированном режиме плавно прокручиваем список к началу выбранного месяца
    val scrollToDisplayedMonth: (String) -> Unit = { targetMonthDateStr ->
        val targetMonthPrefix = targetMonthDateStr.take(7) // "yyyy-MM"
        val targetLazyIndex = lazyItemDates.indexOfFirst { it.startsWith(targetMonthPrefix) }
        if (targetLazyIndex >= 0) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(targetLazyIndex)
            }
        }
    }

    // Суммы в карточках шапки («Траты • Месяц» и «Доходы • Месяц») рассчитываются по текущему отображаемому месяцу/периоду
    val totalExpenseAmt = remember(chartPeriodFilteredList) {
        chartPeriodFilteredList.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { kotlin.math.abs(it.amount) }
    }
    val totalIncomeAmt = remember(chartPeriodFilteredList) {
        chartPeriodFilteredList.filter { it.type.equals("income", ignoreCase = true) }.sumOf { kotlin.math.abs(it.amount) }
    }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "dialog_swipe_offset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AllTransactionsDragHandleAndHeaderBar(
                headerDragOffsetY = dragOffsetY,
                onUpdateHeaderDragOffsetY = { dragOffsetY = it },
                onDismiss = onDismiss,
                shownCount = sortedList.size,
                totalCount = transactions.size
            )

            AllTransactionsCollapsibleHeader(
                showCompactHeader = isChartCollapsed && filterType != "all",
                filterType = filterType,
                onFilterTypeChange = { filterType = it },
                totalExpenseAmt = totalExpenseAmt,
                totalIncomeAmt = totalIncomeAmt,
                dynamicMonthLabel = dynamicMonthLabel,
                currentActiveCategoryTotals = currentActiveCategoryTotals,
                selectedCategoryFilter = selectedCategoryFilter,
                onSelectCategoryFilter = { selectedCategoryFilter = it },
                isDrilledDownToMixed = isDrilledDownToMixed,
                onSetDrilledDownToMixed = { isDrilledDownToMixed = it },
                remainingCategoryNames = remainingCategoryNames,
                selectedPeriod = periodState.selectedPeriod,
                onSelectPeriod = { tappedPeriod ->
                    periodState = periodState.onTogglePeriodTab(tappedPeriod)
                },
                currentOffsetInt = periodState.currentOffsetInt,
                transactions = transactions,
                visibleDateStr = visibleDateStr.value,
                stableAnchorDateStr = periodState.displayedMonthDateStr,
                chartViewMode = chartViewMode,
                onSelectChartViewMode = { chartViewMode = it },
                categoryTotalsMap = categoryTotalsMap,
                shouldGroup = categoryTotalsMap.size > 3,
                swipeMonthOffset = periodState.swipeMonthOffset,
                getFilteredListForOffset = getFilteredListForOffset,
                getCategoryTotalsForOffset = getCategoryTotalsForOffset,
                getCategoryColor = ::getCategoryColor,
                onSwipePeriodOffset = { delta ->
                    val nextState = periodState.onSwipePeriod(delta)
                    periodState = nextState
                    if (!nextState.isPeriodLocked) {
                        scrollToDisplayedMonth(nextState.displayedMonthDateStr)
                    }
                },
                onOpenMixedCategoriesDialog = { showMixedCategoriesDialog = true },
                onExpandChart = { isChartCollapsed = false },
                onCollapseChart = { isChartCollapsed = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            TransactionSortAndSearchBar(
                isSearchExpandedInPlace = isSearchExpandedInPlace,
                onSetSearchExpandedInPlace = { isSearchExpandedInPlace = it },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                focusRequester = focusRequester,
                selectedCategoryFilter = selectedCategoryFilter,
                isDrilledDownToMixed = isDrilledDownToMixed,
                remainingCategoryNames = remainingCategoryNames,
                getCategoryColor = ::getCategoryColor,
                onClearCategoryFilter = {
                    selectedCategoryFilter = null
                    isDrilledDownToMixed = false
                },
                selectedPeriod = periodState.selectedPeriod,
                customStartStr = periodState.customStartStr,
                dynamicMonthLabel = dynamicMonthLabel,
                onToggleMonthLock = {
                    periodState = periodState.onTogglePeriodTab("month")
                },
                onOpenDatePickerModal = { showDatePickerModal = true },
                onResetPeriodToAll = {
                    periodState = periodState.onClearPeriodLock()
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
                snackbarHostState = snackbarHostState,
                modifier = Modifier.nestedScroll(chartCollapseScrollConnection)
            )
        }

        // Undo Delete Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Slate900,
                contentColor = Color.White,
                actionColor = Emerald400,
                shape = RoundedCornerShape(14.dp)
            )
        }

        if (showDatePickerModal) {
            DateRangePickerDialog(
                initialStart = periodState.customStartStr ?: visibleDateStr.value.ifBlank { "2026-07-01" },
                initialEnd = periodState.customEndStr ?: visibleDateStr.value.ifBlank { "2026-08-31" },
                onDismiss = { showDatePickerModal = false },
                onConfirm = { s, e ->
                    val label = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val outFmt = SimpleDateFormat("d MMM", Locale("ru", "RU"))
                        val d1 = sdf.parse(s)
                        val d2 = sdf.parse(e)
                        if (d1 != null && d2 != null) "${outFmt.format(d1)} - ${outFmt.format(d2)}" else "$s .. $e"
                    } catch (_: Exception) {
                        "$s .. $e"
                    }
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
                    selectedCategoryFilter = if (selectedCategoryFilter == catName) null else catName
                    showMixedCategoriesDialog = false
                },
                onDismiss = { showMixedCategoriesDialog = false }
            )
        }
    }
}
