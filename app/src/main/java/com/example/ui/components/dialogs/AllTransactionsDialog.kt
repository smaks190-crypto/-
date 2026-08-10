package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.screens.TransactionRowItem
import com.example.ui.screens.getCategoryColorAndIcon
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllTransactionsDialog(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: ((String) -> Unit)? = null,
    onEditTransaction: ((TransactionEntity) -> Unit)? = null,
    initialFilterType: String = "all",
    initialDate: String? = null,
    onDismiss: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isScrolled by remember {
        androidx.compose.runtime.derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 40
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf("date") }
    var filterType by remember { mutableStateOf(initialFilterType) } // "all", "income", "expense"
    var selectedPeriod by remember { mutableStateOf("all") } // "all", "week", "month", "year", "custom"
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
            transactions.first().date
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

    LaunchedEffect(selectedPeriod, filterType) {
        if (stableAnchorDateStr.isBlank()) {
            stableAnchorDateStr = defaultAnchor
        }
        swipeWeekOffset = 0
        swipeMonthOffset = 0
        swipeYearOffset = 0
        isDrilledDownToMixed = false
        selectedCategoryFilter = null
    }

    val dynamicMonthLabel = remember(visibleDateStr.value, stableAnchorDateStr, customLabelStr, selectedPeriod, swipeMonthOffset, swipeWeekOffset, swipeYearOffset) {
        if (!customLabelStr.isNullOrBlank()) {
            customLabelStr!!
        } else {
            val baseDateStr = if (selectedPeriod == "all" || selectedPeriod == "custom") visibleDateStr.value else stableAnchorDateStr
            val anchorDateStr = baseDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch (e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }

            if (selectedPeriod == "week") {
                cal.add(Calendar.WEEK_OF_YEAR, swipeWeekOffset)
                val startCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    add(Calendar.DAY_OF_WEEK, 6)
                }
                val df = SimpleDateFormat("dd.MM", Locale.getDefault())
                "Неделя ${df.format(startCal.time)} - ${df.format(endCal.time)}"
            } else if (selectedPeriod == "year") {
                cal.add(Calendar.YEAR, swipeYearOffset)
                "${cal.get(Calendar.YEAR)} год"
            } else if (selectedPeriod == "month") {
                cal.add(Calendar.MONTH, swipeMonthOffset)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val monthYear = cal.get(Calendar.YEAR)
                val formatPattern = if (monthYear != currentYear) "LLLL yyyy" else "LLLL"
                val rawStr = SimpleDateFormat(formatPattern, Locale("ru", "RU")).format(cal.time)
                rawStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
            } else {
                try {
                    val d = sdf.parse(anchorDateStr)
                    if (d != null) {
                        val parsedCal = Calendar.getInstance().apply { time = d }
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        val parsedYear = parsedCal.get(Calendar.YEAR)
                        val formatPattern = if (parsedYear != currentYear) "LLLL yyyy" else "LLLL"
                        val rawStr = SimpleDateFormat(formatPattern, Locale("ru", "RU")).format(d)
                        rawStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
                    } else "Август"
                } catch (e: Exception) {
                    "Август"
                }
            }
        }
    }

    // Filter transactions by period ("week", "month", "year", "custom") relative to visible/selected month
    val periodFilteredList = remember(transactions, selectedPeriod, customStartStr, customEndStr, visibleDateStr.value, stableAnchorDateStr, swipeWeekOffset, swipeMonthOffset, swipeYearOffset) {
        if (selectedPeriod == "custom" && !customStartStr.isNullOrBlank() && !customEndStr.isNullOrBlank()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDate = try { sdf.parse(customStartStr!!) } catch (e: Exception) { null }
            val endDate = try { sdf.parse(customEndStr!!) } catch (e: Exception) { null }
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch (e: Exception) { null }
                if (txDate == null) true
                else if (startDate != null && endDate != null) {
                    !txDate.before(startDate) && !txDate.after(endDate)
                } else true
            }
        } else if (selectedPeriod == "week") {
            val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }
            cal.add(Calendar.WEEK_OF_YEAR, swipeWeekOffset)
            val adjustedDate = cal.time
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                if (txDate == null) true
                else {
                    val diffMs = kotlin.math.abs(adjustedDate.time - txDate.time)
                    val diffDays = diffMs / (1000 * 60 * 60 * 24)
                    diffDays <= 7
                }
            }
        } else if (selectedPeriod == "month") {
            val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }
            cal.add(Calendar.MONTH, swipeMonthOffset)
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH)
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                if (txDate == null) true
                else {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth
                }
            }
        } else if (selectedPeriod == "year") {
            val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }
            cal.add(Calendar.YEAR, swipeYearOffset)
            val currentYear = cal.get(Calendar.YEAR)
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                if (txDate == null) true
                else {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    txCal.get(Calendar.YEAR) == currentYear
                }
            }
        } else {
            transactions
        }
    }

    fun getFilteredListForOffset(offset: Int): List<TransactionEntity> {
        val effectivePeriod = if (selectedPeriod == "all" || selectedPeriod == "custom") "month" else selectedPeriod

        val anchorDateStr = if (selectedPeriod == "all" || selectedPeriod == "custom") {
            visibleDateStr.value.ifBlank { "2026-08-01" }
        } else {
            stableAnchorDateStr.ifBlank { "2026-08-01" }
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
        val cal = Calendar.getInstance().apply { time = anchorDate }

        val baseList = if (selectedPeriod == "custom") periodFilteredList else transactions

        return when (effectivePeriod) {
            "week" -> {
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                val adjustedDate = cal.time
                baseList.filter { tx ->
                    val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                    if (txDate == null) true
                    else {
                        val diffMs = kotlin.math.abs(adjustedDate.time - txDate.time)
                        val diffDays = diffMs / (1000 * 60 * 60 * 24)
                        diffDays <= 7
                    }
                }
            }
            "month" -> {
                cal.add(Calendar.MONTH, offset)
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH)
                baseList.filter { tx ->
                    val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                    if (txDate == null) true
                    else {
                        val txCal = Calendar.getInstance().apply { time = txDate }
                        txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth
                    }
                }
            }
            "year" -> {
                cal.add(Calendar.YEAR, offset)
                val currentYear = cal.get(Calendar.YEAR)
                baseList.filter { tx ->
                    val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                    if (txDate == null) true
                    else {
                        val txCal = Calendar.getInstance().apply { time = txDate }
                        txCal.get(Calendar.YEAR) == currentYear
                    }
                }
            }
            else -> baseList
        }
    }

    fun getCategoryTotalsForOffset(offset: Int): List<Pair<String, Double>> {
        val rawList = getFilteredListForOffset(offset)
        val typeFiltered = when (filterType) {
            "income" -> rawList.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> rawList.filter { it.type.equals("expense", ignoreCase = true) }
            else -> rawList
        }
        val fullMap = typeFiltered
            .groupBy { it.category.ifBlank { "Прочее" } }
            .map { (cat, list) -> cat to list.sumOf { kotlin.math.abs(it.amount) } }
            .sortedByDescending { it.second }

        val threshold = 3
        return if (fullMap.size > threshold) {
            if (isDrilledDownToMixed) {
                fullMap.drop(2)
            } else {
                val top2 = fullMap.take(2)
                val remaining = fullMap.drop(2)
                val remainingSum = remaining.sumOf { it.second }
                val remainingCount = remaining.size
                val mixedName = if (remainingCount <= 1) "✨ Прочие" else "✨ Смешанные (+$remainingCount)"
                (top2 + (mixedName to remainingSum)).sortedByDescending { it.second }
            }
        } else {
            fullMap
        }
    }

    val typeFilteredList = remember(periodFilteredList, filterType) {
        when (filterType) {
            "income" -> periodFilteredList.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> periodFilteredList.filter { it.type.equals("expense", ignoreCase = true) }
            else -> periodFilteredList
        }
    }

    val categoryTotalsMap = remember(typeFilteredList, visibleDateStr.value, selectedPeriod) {
        val listForCategory = if (selectedPeriod == "all" || selectedPeriod == "custom") {
            val dateParts = visibleDateStr.value.split("-")
            if (dateParts.size >= 2) {
                val prefix = "${dateParts[0]}-${dateParts[1]}-"
                typeFilteredList.filter { it.date.startsWith(prefix) }
            } else {
                typeFilteredList
            }
        } else {
            typeFilteredList
        }
        listForCategory
            .groupBy { it.category.ifBlank { "Прочее" } }
            .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }
    }

    val categoryThreshold = 3
    val shouldGroup = categoryTotalsMap.size > categoryThreshold

    val accentColor = remember(filterType) {
        if (filterType == "expense") Rose500 else Emerald400
    }

    val remainingCategoryNames = remember(categoryTotalsMap) {
        if (categoryTotalsMap.size > categoryThreshold) {
            categoryTotalsMap.drop(2).map { it.first }
        } else {
            emptyList()
        }
    }

    val categoryColorMap = remember(categoryTotalsMap, filterType) {
        val map = mutableMapOf<String, Color>()
        categoryTotalsMap.forEachIndexed { index, (catName, _) ->
            val (systemColor, _) = getCategoryColorAndIcon(catName, "")
            if (systemColor == Slate400) {
                val baseColors = if (filterType == "income") {
                    listOf(
                        Color(0xFF10B981), Color(0xFF6366F1), Color(0xFF3B82F6),
                        Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFF14B8A6)
                    )
                } else {
                    listOf(
                        Color(0xFFF43F5E), Color(0xFF3B82F6), Color(0xFFF59E0B),
                        Color(0xFF8B5CF6), Color(0xFF10B981), Color(0xFF14B8A6)
                    )
                }
                map[catName] = baseColors.getOrElse(index % baseColors.size) { if (filterType == "expense") Rose500 else Emerald400 }
            } else {
                map[catName] = systemColor
            }
        }
        map["✨ Смешанные"] = Indigo500
        map["✨ Прочие"] = Indigo500
        map
    }

    fun getCategoryColor(name: String): Color {
        val cleanName = if (name.startsWith("✨")) {
            if (name.contains("Прочие")) "✨ Прочие" else "✨ Смешанные"
        } else {
            name
        }
        return categoryColorMap.get(cleanName) ?: run {
            val (systemColor, _) = getCategoryColorAndIcon(cleanName, "")
            if (systemColor == Slate400) {
                if (filterType == "expense") Rose500 else Emerald400
            } else {
                systemColor
            }
        }
    }

    val currentActiveCategoryTotals = remember(categoryTotalsMap, isDrilledDownToMixed) {
        if (categoryTotalsMap.size > categoryThreshold) {
            if (isDrilledDownToMixed) {
                categoryTotalsMap.drop(2)
            } else {
                val top2 = categoryTotalsMap.take(2)
                val remaining = categoryTotalsMap.drop(2)
                val remainingSum = remaining.sumOf { it.second }
                val remainingCount = remaining.size
                val mixedName = if (remainingCount <= 1) "✨ Прочие" else "✨ Смешанные (+$remainingCount)"
                (top2 + (mixedName to remainingSum)).sortedByDescending { it.second }
            }
        } else {
            categoryTotalsMap
        }
    }

    val searchFilteredList = remember(typeFilteredList, searchQuery, selectedCategoryFilter, isDrilledDownToMixed, remainingCategoryNames) {
        typeFilteredList.filter { tx ->
            val txCat = tx.category.ifBlank { "Прочее" }
            val matchesCategory = when {
                selectedCategoryFilter == "✨ Смешанные" || selectedCategoryFilter == "✨ Прочие" -> {
                    remainingCategoryNames.any { it.equals(txCat, ignoreCase = true) }
                }
                isDrilledDownToMixed && selectedCategoryFilter == null -> {
                    remainingCategoryNames.any { it.equals(txCat, ignoreCase = true) }
                }
                selectedCategoryFilter.isNullOrBlank() -> true
                else -> txCat.equals(selectedCategoryFilter, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                    tx.subcategory.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val sortedList = remember(searchFilteredList, sortOption) {
        when (sortOption) {
            "desc" -> searchFilteredList.sortedWith(compareByDescending<TransactionEntity> { kotlin.math.abs(it.amount) }.thenByDescending { it.createdAt })
            "asc" -> searchFilteredList.sortedWith(compareBy<TransactionEntity> { kotlin.math.abs(it.amount) }.thenByDescending { it.createdAt })
            "name" -> searchFilteredList.sortedWith(compareBy<TransactionEntity> { it.category.ifBlank { it.subcategory } }.thenByDescending { it.createdAt })
            "name_desc" -> searchFilteredList.sortedWith(compareByDescending<TransactionEntity> { it.category.ifBlank { it.subcategory } }.thenByDescending { it.createdAt })
            else -> searchFilteredList.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt })
        }
    }

    val groupedByDate = remember(sortedList) {
        sortedList.groupBy { it.date }
    }

    val totalExpenseAmt = remember(periodFilteredList, visibleDateStr.value, selectedPeriod) {
        if (selectedPeriod == "all" || selectedPeriod == "custom") {
            val dateParts = visibleDateStr.value.split("-")
            if (dateParts.size >= 2) {
                val prefix = "${dateParts[0]}-${dateParts[1]}-"
                periodFilteredList.filter { it.type == "expense" && it.date.startsWith(prefix) }
                    .sumOf { kotlin.math.abs(it.amount) }
            } else {
                periodFilteredList.filter { it.type == "expense" }.sumOf { kotlin.math.abs(it.amount) }
            }
        } else {
            periodFilteredList.filter { it.type == "expense" }.sumOf { kotlin.math.abs(it.amount) }
        }
    }
    val totalIncomeAmt = remember(periodFilteredList, visibleDateStr.value, selectedPeriod) {
        if (selectedPeriod == "all" || selectedPeriod == "custom") {
            val dateParts = visibleDateStr.value.split("-")
            if (dateParts.size >= 2) {
                val prefix = "${dateParts[0]}-${dateParts[1]}-"
                periodFilteredList.filter { it.type == "income" && it.date.startsWith(prefix) }
                    .sumOf { kotlin.math.abs(it.amount) }
            } else {
                periodFilteredList.filter { it.type == "income" }.sumOf { kotlin.math.abs(it.amount) }
            }
        } else {
            periodFilteredList.filter { it.type == "income" }.sumOf { kotlin.math.abs(it.amount) }
        }
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { headerDragOffsetY = 0f },
                                    onDragEnd = {
                                        if (headerDragOffsetY > 80f) {
                                            onDismiss()
                                        } else {
                                            headerDragOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = { headerDragOffsetY = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        if (dragAmount > 0f || headerDragOffsetY > 0f) {
                                            headerDragOffsetY = (headerDragOffsetY + dragAmount).coerceAtLeast(0f)
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Start,
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
                                    text = "Показано ${sortedList.size} из ${transactions.size} операций",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 0.dp,
                            start = 0.dp,
                            end = 0.dp,
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. FILTER TYPE & PERIOD SELECTOR
                        item(key = "controls_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Filter type: Все | Расходы | Доходы
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Slate900)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        "all" to "Все",
                                        "expense" to "Расходы",
                                        "income" to "Доходы"
                                    ).forEach { (typeKey, typeLabel) ->
                                        val isSelected = filterType == typeKey
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) {
                                                        when (typeKey) {
                                                            "expense" -> Rose500.copy(alpha = 0.25f)
                                                            "income" -> Emerald400.copy(alpha = 0.25f)
                                                            else -> Indigo500.copy(alpha = 0.25f)
                                                        }
                                                    } else Color.Transparent
                                                )
                                                .border(
                                                    width = if (isSelected) 1.dp else 0.dp,
                                                    color = if (isSelected) {
                                                        when (typeKey) {
                                                            "expense" -> Rose500
                                                            "income" -> Emerald400
                                                            else -> Indigo500
                                                        }
                                                    } else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    filterType = typeKey
                                                    selectedCategoryFilter = null
                                                    isDrilledDownToMixed = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = typeLabel,
                                                color = if (isSelected) Color.White else Slate400,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Period tabs: Всё время | Месяц | Неделя | Год | Выбрать
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "all" to "Всё время",
                                        "month" to "Месяц",
                                        "week" to "Неделя",
                                        "year" to "Год",
                                        "custom" to "Выбрать"
                                    ).forEach { (pKey, pLabel) ->
                                        val isSelected = selectedPeriod == pKey
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) Indigo500.copy(alpha = 0.2f) else Slate900.copy(alpha = 0.6f))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Indigo500 else Slate800,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    if (pKey == "custom") {
                                                        showDatePickerModal = true
                                                    } else {
                                                        selectedPeriod = pKey
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = pLabel,
                                                color = if (isSelected) Indigo500 else Slate400,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Swipe/Arrow Month Navigation bar if specific period selected
                                if (selectedPeriod != "all") {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(selectedPeriod) {
                                                detectHorizontalDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    if (dragAmount < -30) {
                                                        when (selectedPeriod) {
                                                            "week" -> swipeWeekOffset += 1
                                                            "month" -> swipeMonthOffset += 1
                                                            "year" -> swipeYearOffset += 1
                                                        }
                                                    } else if (dragAmount > 30) {
                                                        when (selectedPeriod) {
                                                            "week" -> swipeWeekOffset -= 1
                                                            "month" -> swipeMonthOffset -= 1
                                                            "year" -> swipeYearOffset -= 1
                                                        }
                                                    }
                                                }
                                            },
                                        color = Slate900.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    when (selectedPeriod) {
                                                        "week" -> swipeWeekOffset -= 1
                                                        "month" -> swipeMonthOffset -= 1
                                                        "year" -> swipeYearOffset -= 1
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                    contentDescription = "Назад",
                                                    tint = Slate400,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Text(
                                                text = dynamicMonthLabel,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            IconButton(
                                                onClick = {
                                                    when (selectedPeriod) {
                                                        "week" -> swipeWeekOffset += 1
                                                        "month" -> swipeMonthOffset += 1
                                                        "year" -> swipeYearOffset += 1
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = "Вперед",
                                                    tint = Slate400,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. DIAGRAMS & CHARTS SECTION
                        item(key = "diagrams_section") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Slate900.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (filterType == "income") "Структура доходов" else if (filterType == "expense") "Структура расходов" else "Структура операций",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val periodSum = currentActiveCategoryTotals.sumOf { it.second }
                                            Text(
                                                text = "Итого: ${formatFullCurrency(periodSum)}",
                                                color = accentColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DarkBg)
                                                .padding(2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            IconButton(
                                                onClick = { chartViewMode = "donut" },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (chartViewMode == "donut") Indigo500.copy(alpha = 0.3f) else Color.Transparent)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PieChart,
                                                    contentDescription = "Кольцевая",
                                                    tint = if (chartViewMode == "donut") Indigo500 else Slate400,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { chartViewMode = "bar" },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (chartViewMode == "bar") Indigo500.copy(alpha = 0.3f) else Color.Transparent)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.BarChart,
                                                    contentDescription = "Столбчатая",
                                                    tint = if (chartViewMode == "bar") Indigo500 else Slate400,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (currentActiveCategoryTotals.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Нет данных для отображения диаграммы",
                                                color = Slate500,
                                                fontSize = 12.sp
                                            )
                                        }
                                    } else {
                                        val grandTotal = currentActiveCategoryTotals.sumOf { it.second }.coerceAtLeast(1.0)

                                        if (chartViewMode == "donut") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(110.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        val strokeWidth = 14.dp.toPx()
                                                        val diameter = size.minDimension - strokeWidth
                                                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                                                        val arcSize = Size(diameter, diameter)

                                                        var startAngle = -90f
                                                        currentActiveCategoryTotals.forEach { (catName, amount) ->
                                                            val sweepAngle = ((amount / grandTotal) * 360f).toFloat()
                                                            val color = getCategoryColor(catName)
                                                            drawArc(
                                                                color = color,
                                                                startAngle = startAngle,
                                                                sweepAngle = sweepAngle - 2f,
                                                                useCenter = false,
                                                                topLeft = topLeft,
                                                                size = arcSize,
                                                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                                            )
                                                            startAngle += sweepAngle
                                                        }
                                                    }

                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = "${currentActiveCategoryTotals.size}",
                                                            color = Color.White,
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "кат.",
                                                            color = Slate400,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }

                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    currentActiveCategoryTotals.take(4).forEach { (catName, amount) ->
                                                        val pct = (amount / grandTotal * 100).toInt()
                                                        val catColor = getCategoryColor(catName)
                                                        val isSelected = selectedCategoryFilter == catName

                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .clickable {
                                                                    if (catName.startsWith("✨")) {
                                                                        isDrilledDownToMixed = true
                                                                        selectedCategoryFilter = null
                                                                    } else {
                                                                        selectedCategoryFilter = if (isSelected) null else catName
                                                                    }
                                                                }
                                                                .padding(vertical = 2.dp, horizontal = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.weight(1f)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(catColor)
                                                                )
                                                                Text(
                                                                    text = catName,
                                                                    color = if (isSelected) catColor else Color.White,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }

                                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Text(
                                                                    text = "$pct%",
                                                                    color = Slate400,
                                                                    fontSize = 10.sp
                                                                )
                                                                Text(
                                                                    text = formatFullCurrency(amount),
                                                                    color = Color.White,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                val maxAmount = currentActiveCategoryTotals.maxOfOrNull { it.second } ?: 1.0
                                                currentActiveCategoryTotals.take(5).forEach { (catName, amount) ->
                                                    val ratio = (amount / maxAmount).toFloat().coerceIn(0.05f, 1f)
                                                    val catColor = getCategoryColor(catName)
                                                    val isSelected = selectedCategoryFilter == catName

                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                if (catName.startsWith("✨")) {
                                                                    isDrilledDownToMixed = true
                                                                    selectedCategoryFilter = null
                                                                } else {
                                                                    selectedCategoryFilter = if (isSelected) null else catName
                                                                }
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = catName,
                                                                color = if (isSelected) catColor else Color.White,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            Text(
                                                                text = formatFullCurrency(amount),
                                                                color = catColor,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(3.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(8.dp)
                                                                .clip(CircleShape)
                                                                .background(Slate800)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxHeight()
                                                                    .fillMaxWidth(ratio)
                                                                    .clip(CircleShape)
                                                                    .background(
                                                                        Brush.horizontalGradient(
                                                                            listOf(catColor.copy(alpha = 0.7f), catColor)
                                                                        )
                                                                    )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. SEARCH BAR, SORT DROPDOWN & CATEGORY CHIPS
                        item(key = "search_sort_chips") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Search Bar
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = Slate900,
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Поиск",
                                                tint = Slate400,
                                                modifier = Modifier.size(16.dp)
                                            )

                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .focusRequester(focusRequester),
                                                textStyle = TextStyle(
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                singleLine = true,
                                                decorationBox = { innerTextField ->
                                                    if (searchQuery.isEmpty()) {
                                                        Text(
                                                            text = "Поиск по категории...",
                                                            color = Slate500,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )

                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { searchQuery = "" },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Очистить",
                                                        tint = Slate400,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Sort Menu Dropdown
                                    var isSortMenuExpanded by remember { mutableStateOf(false) }

                                    Box {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Slate900)
                                                .border(1.dp, if (sortOption != "date") Indigo500 else Slate800, RoundedCornerShape(12.dp))
                                                .clickable { isSortMenuExpanded = true }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Sort,
                                                    contentDescription = "Сортировка",
                                                    tint = if (sortOption != "date") Indigo500 else Slate400,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = when (sortOption) {
                                                        "desc" -> "Сумма ↓"
                                                        "asc" -> "Сумма ↑"
                                                        "name" -> "A-Z"
                                                        "name_desc" -> "Z-A"
                                                        else -> "Дата"
                                                    },
                                                    color = if (sortOption != "date") Indigo500 else Slate300,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = isSortMenuExpanded,
                                            onDismissRequest = { isSortMenuExpanded = false },
                                            modifier = Modifier
                                                .background(DarkBg)
                                                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                        ) {
                                            listOf(
                                                "date" to "По дате (сначала новые)",
                                                "desc" to "По убыванию суммы",
                                                "asc" to "По возрастанию суммы",
                                                "name" to "По названию (A-Z)",
                                                "name_desc" to "По названию (Z-A)"
                                            ).forEach { (optKey, optLabel) ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = optLabel,
                                                            color = if (sortOption == optKey) Indigo500 else Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (sortOption == optKey) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        sortOption = optKey
                                                        isSortMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Category Chips
                                if (categoryTotalsMap.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val isAllSelected = selectedCategoryFilter == null && !isDrilledDownToMixed
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isAllSelected) Emerald400.copy(alpha = 0.2f) else Slate900)
                                                .border(1.dp, if (isAllSelected) Emerald400 else Slate800, RoundedCornerShape(20.dp))
                                                .clickable {
                                                    selectedCategoryFilter = null
                                                    isDrilledDownToMixed = false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Все категории",
                                                color = if (isAllSelected) Emerald400 else Slate400,
                                                fontSize = 11.sp,
                                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }

                                        currentActiveCategoryTotals.forEach { (catName, _) ->
                                            val isSelected = selectedCategoryFilter == catName
                                            val catColor = getCategoryColor(catName)

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(if (isSelected) catColor.copy(alpha = 0.25f) else Slate900)
                                                    .border(1.dp, if (isSelected) catColor else Slate800, RoundedCornerShape(20.dp))
                                                    .clickable {
                                                        if (catName.startsWith("✨")) {
                                                            isDrilledDownToMixed = true
                                                            selectedCategoryFilter = null
                                                        } else {
                                                            selectedCategoryFilter = if (isSelected) null else catName
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(catColor)
                                                    )
                                                    Text(
                                                        text = catName,
                                                        color = if (isSelected) Color.White else Slate300,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (sortedList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Операции не найдены",
                                        color = Slate500,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            groupedByDate.forEach { (dateStr, itemsForDay) ->
                                item(key = "header_$dateStr") {
                                    val dayTotalExpense = itemsForDay.filter { it.type == "expense" }.sumOf { kotlin.math.abs(it.amount) }
                                    val dayTotalIncome = itemsForDay.filter { it.type == "income" }.sumOf { kotlin.math.abs(it.amount) }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatDayHeaderLabel(dateStr),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (filterType == "income" && dayTotalIncome > 0) {
                                            Text(
                                                text = "+${formatFullCurrency(dayTotalIncome)}",
                                                color = Emerald400,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else if (dayTotalExpense > 0) {
                                            Text(
                                                text = "-${formatFullCurrency(dayTotalExpense)}",
                                                color = Slate400,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                items(itemsForDay, key = { it.id }) { tx ->
                                    TransactionRowItem(
                                        item = tx,
                                        onDelete = { txId -> onDeleteTransaction?.invoke(txId) },
                                        onClick = { onEditTransaction?.invoke(tx) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePickerModal) {
        var tempStart by remember { mutableStateOf(customStartStr ?: "2026-08-01") }
        var tempEnd by remember { mutableStateOf(customEndStr ?: "2026-08-31") }

        androidx.compose.ui.window.Dialog(onDismissRequest = { showDatePickerModal = false }) {
            Surface(
                color = DarkBg,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Выберите период",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    DatePickerField(
                        value = tempStart,
                        onDateSelected = { tempStart = it },
                        label = "Начальная дата"
                    )

                    DatePickerField(
                        value = tempEnd,
                        onDateSelected = { tempEnd = it },
                        label = "Конечная дата"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.TextButton(onClick = { showDatePickerModal = false }) {
                            Text("Отмена", color = Slate400)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                customStartStr = tempStart
                                customEndStr = tempEnd
                                customLabelStr = "$tempStart - $tempEnd"
                                selectedPeriod = "custom"
                                showDatePickerModal = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Indigo500)
                        ) {
                            Text("Применить", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

