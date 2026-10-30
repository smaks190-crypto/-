package com.example.ui.components.dialogs

import androidx.compose.ui.graphics.Color
import com.example.data.db.TransactionEntity
import com.example.ui.screens.getCategoryColorAndIcon
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val PERIOD_SWITCHER_TABS: List<Pair<String, String>> = listOf(
    "week" to "Нед",
    "month" to "Мес",
    "year" to "Год"
)

fun resolveInitialAnchorDate(
    initialDate: String?,
    fallbackDate: Date = Date()
): String {
    return if (!initialDate.isNullOrBlank()) {
        initialDate
    } else {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(fallbackDate)
    }
}

fun resolveEffectivePeriod(selectedPeriod: String?): String {
    return when (selectedPeriod) {
        "week" -> "week"
        "month" -> "month"
        "year" -> "year"
        "custom" -> "custom"
        else -> "month"
    }
}

fun toggleLockedPeriod(currentSelectedPeriod: String?, clickedPeriod: String): String? {
    return if (currentSelectedPeriod == clickedPeriod) {
        null
    } else {
        clickedPeriod
    }
}

fun resolveSelectedPeriodTabIndex(
    selectedPeriod: String?,
    tabs: List<Pair<String, String>> = PERIOD_SWITCHER_TABS
): Int {
    if (selectedPeriod.isNullOrBlank()) return -1
    return tabs.indexOfFirst { it.first == selectedPeriod }
}

fun shiftDateByMonths(anchorDateStr: String, monthOffset: Int): String {
    val cleanAnchor = anchorDateStr.ifBlank { "2026-08-01" }
    if (monthOffset == 0) return cleanAnchor
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val anchorDate = try { sdf.parse(cleanAnchor) } catch (e: Exception) { null } ?: Date()
    val cal = Calendar.getInstance().apply {
        time = anchorDate
        add(Calendar.MONTH, monthOffset)
    }
    return sdf.format(cal.time)
}

data class AllTransactionsPeriodState(
    val stableAnchorDateStr: String,
    val selectedPeriod: String? = null, // null = без фиксации (просмотр по месяцам); "week", "month", "year", "custom"
    val swipeMonthOffset: Int = 0,
    val swipeWeekOffset: Int = 0,
    val swipeYearOffset: Int = 0,
    val customStartStr: String? = null,
    val customEndStr: String? = null,
    val customLabelStr: String? = null
) {
    val effectivePeriod: String
        get() = resolveEffectivePeriod(selectedPeriod)

    val displayedMonthDateStr: String
        get() = shiftDateByMonths(stableAnchorDateStr, swipeMonthOffset)

    val isPeriodLocked: Boolean
        get() = !selectedPeriod.isNullOrBlank() || !customStartStr.isNullOrBlank()

    val activeTabIndex: Int
        get() = resolveSelectedPeriodTabIndex(selectedPeriod)

    val currentOffsetInt: Int
        get() = when (effectivePeriod) {
            "week" -> swipeWeekOffset
            "year" -> swipeYearOffset
            else -> swipeMonthOffset
        }

    fun onScrolledToDate(visibleDateStr: String): AllTransactionsPeriodState {
        if (isPeriodLocked) return this
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val anchor = try { format.parse(stableAnchorDateStr) } catch (_: Exception) { null } ?: return this
        val visible = try { format.parse(visibleDateStr) } catch (_: Exception) { null } ?: return this
        val anchorCal = Calendar.getInstance().apply { time = anchor }
        val visibleCal = Calendar.getInstance().apply { time = visible }
        val offset = (visibleCal.get(Calendar.YEAR) - anchorCal.get(Calendar.YEAR)) * 12 +
            visibleCal.get(Calendar.MONTH) - anchorCal.get(Calendar.MONTH)
        return if (offset == swipeMonthOffset) this else copy(swipeMonthOffset = offset)
    }

    fun onSwipePeriod(delta: Int): AllTransactionsPeriodState {
        return when (effectivePeriod) {
            "week" -> copy(swipeWeekOffset = swipeWeekOffset + delta)
            "year" -> copy(swipeYearOffset = swipeYearOffset + delta)
            "custom" -> this
            else -> copy(swipeMonthOffset = swipeMonthOffset + delta)
        }
    }

    fun onTogglePeriodTab(clickedPeriod: String): AllTransactionsPeriodState {
        val nextLocked = toggleLockedPeriod(selectedPeriod, clickedPeriod)
        return copy(
            selectedPeriod = nextLocked,
            swipeWeekOffset = if (nextLocked == "week" && selectedPeriod != "week") 0 else swipeWeekOffset,
            swipeYearOffset = if (nextLocked == "year" && selectedPeriod != "year") 0 else swipeYearOffset,
            customStartStr = if (nextLocked != "custom") null else customStartStr,
            customEndStr = if (nextLocked != "custom") null else customEndStr,
            customLabelStr = if (nextLocked != "custom") null else customLabelStr
        )
    }

    fun onApplyCustomDateRange(startStr: String, endStr: String, labelStr: String): AllTransactionsPeriodState {
        return copy(
            selectedPeriod = "custom",
            customStartStr = startStr,
            customEndStr = endStr,
            customLabelStr = labelStr
        )
    }

    fun onClearPeriodLock(): AllTransactionsPeriodState {
        return copy(
            selectedPeriod = null,
            customStartStr = null,
            customEndStr = null,
            customLabelStr = null
        )
    }
}

fun buildDynamicPeriodLabel(
    stableAnchorDateStr: String,
    customLabelStr: String?,
    selectedPeriod: String?,
    swipeMonthOffset: Int,
    swipeWeekOffset: Int,
    swipeYearOffset: Int
): String {
    val effectivePeriod = resolveEffectivePeriod(selectedPeriod)
    return if (effectivePeriod == "custom" && !customLabelStr.isNullOrBlank()) {
        customLabelStr
    } else {
        val baseDateStr = if (effectivePeriod == "week" || effectivePeriod == "year") {
            shiftDateByMonths(stableAnchorDateStr, swipeMonthOffset)
        } else {
            stableAnchorDateStr.ifBlank { "2026-08-01" }
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val anchorDate = try { sdf.parse(baseDateStr) } catch (e: Exception) { null } ?: Date()
        val cal = Calendar.getInstance().apply { time = anchorDate }

        when (effectivePeriod) {
            "week" -> {
                cal.add(Calendar.WEEK_OF_YEAR, swipeWeekOffset)
                cal.firstDayOfWeek = Calendar.MONDAY
                val startCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }
                val endCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 6)
                }
                val df = SimpleDateFormat("dd.MM", Locale.getDefault())
                "Неделя ${df.format(startCal.time)} - ${df.format(endCal.time)}"
            }
            "year" -> {
                cal.add(Calendar.YEAR, swipeYearOffset)
                "${cal.get(Calendar.YEAR)} год"
            }
            else -> {
                cal.add(Calendar.MONTH, swipeMonthOffset)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val monthYear = cal.get(Calendar.YEAR)
                val formatPattern = if (monthYear != currentYear) "LLLL yyyy" else "LLLL"
                val rawStr = SimpleDateFormat(formatPattern, Locale("ru", "RU")).format(cal.time)
                rawStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
            }
        }
    }
}

fun filterTransactionsForOffset(
    transactions: List<TransactionEntity>,
    selectedPeriod: String?,
    customStartStr: String?,
    customEndStr: String?,
    stableAnchorDateStr: String,
    currentOffsetInt: Int,
    offset: Int,
    swipeMonthOffset: Int = 0
): List<TransactionEntity> {
    val effectivePeriod = resolveEffectivePeriod(selectedPeriod)
    if (effectivePeriod == "custom" && !customStartStr.isNullOrBlank() && !customEndStr.isNullOrBlank() && offset == currentOffsetInt) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = try { sdf.parse(customStartStr) } catch (e: Exception) { null }
        val endDate = try { sdf.parse(customEndStr) } catch (e: Exception) { null }
        return transactions.filter { tx ->
            val txDate = try { sdf.parse(tx.date) } catch (e: Exception) { null }
            if (txDate == null) true
            else if (startDate != null && endDate != null) {
                !txDate.before(startDate) && !txDate.after(endDate)
            } else true
        }
    }

    val baseAnchorDateStr = if (effectivePeriod == "week" || effectivePeriod == "year") {
        shiftDateByMonths(stableAnchorDateStr, swipeMonthOffset)
    } else {
        stableAnchorDateStr.ifBlank { "2026-08-01" }
    }
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val anchorDate = try { sdf.parse(baseAnchorDateStr) } catch (e: Exception) { null } ?: Date()
    val cal = Calendar.getInstance().apply { time = anchorDate }

    return when (effectivePeriod) {
        "week" -> {
            cal.add(Calendar.WEEK_OF_YEAR, offset)
            cal.firstDayOfWeek = Calendar.MONDAY
            val startCal = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = (startCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 6)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val startStr = sdf.format(startCal.time)
            val endStr = sdf.format(endCal.time)
            transactions.filter { tx ->
                tx.date >= startStr && tx.date <= endStr
            }
        }
        "year" -> {
            cal.add(Calendar.YEAR, offset)
            val currentYear = cal.get(Calendar.YEAR)
            val yearPrefix = String.format(Locale.US, "%04d-", currentYear)
            transactions.filter { tx ->
                tx.date.startsWith(yearPrefix)
            }
        }
        else -> {
            cal.add(Calendar.MONTH, offset)
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1
            val monthPrefix = String.format(Locale.US, "%04d-%02d-", currentYear, currentMonth)
            transactions.filter { tx ->
                tx.date.startsWith(monthPrefix)
            }
        }
    }
}

fun calculateCategoryTotalsForOffset(
    rawList: List<TransactionEntity>,
    filterType: String,
    isDrilledDownToMixed: Boolean,
    threshold: Int = 3
): List<Pair<String, Double>> {
    val typeFiltered = when (filterType) {
        "income" -> rawList.filter { it.type.equals("income", ignoreCase = true) }
        "expense" -> rawList.filter { it.type.equals("expense", ignoreCase = true) }
        else -> rawList
    }
    val fullMap = typeFiltered
        .groupBy { it.category.ifBlank { "Прочее" } }
        .map { (cat, list) -> cat to list.sumOf { kotlin.math.abs(it.amount) } }
        .sortedByDescending { it.second }

    return computeActiveCategoryTotals(fullMap, isDrilledDownToMixed, threshold)
}

fun computeActiveCategoryTotals(
    categoryTotalsMap: List<Pair<String, Double>>,
    isDrilledDownToMixed: Boolean,
    categoryThreshold: Int = 3
): List<Pair<String, Double>> {
    return if (categoryTotalsMap.size > categoryThreshold) {
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

fun buildCategoryColorMap(
    categoryTotalsMap: List<Pair<String, Double>>,
    filterType: String
): Map<String, Color> {
    val map = mutableMapOf<String, Color>()
    categoryTotalsMap.forEachIndexed { index, (catName, _) ->
        val (systemColor, _) = getCategoryColorAndIcon(catName, "")
        if (systemColor == Slate400) {
            val baseColors = if (filterType == "income") {
                listOf(
                    Color(0xFF10B981), // Emerald
                    Color(0xFF6366F1), // Indigo
                    Color(0xFF3B82F6), // Blue
                    Color(0xFFF59E0B), // Amber
                    Color(0xFF8B5CF6), // Purple
                    Color(0xFF14B8A6)  // Teal
                )
            } else {
                listOf(
                    Color(0xFFF43F5E), // Rose
                    Color(0xFF3B82F6), // Blue
                    Color(0xFFF59E0B), // Amber
                    Color(0xFF8B5CF6), // Purple
                    Color(0xFF10B981), // Emerald
                    Color(0xFF14B8A6)  // Teal
                )
            }
            map[catName] = baseColors.getOrElse(index % baseColors.size) { if (filterType == "expense") Rose500 else Emerald400 }
        } else {
            map[catName] = systemColor
        }
    }
    map["✨ Смешанные"] = Indigo500
    map["✨ Прочие"] = Indigo500
    return map
}

fun resolveCategoryColor(
    name: String,
    categoryColorMap: Map<String, Color>,
    filterType: String
): Color {
    val cleanName = if (name.startsWith("✨")) {
        if (name.contains("Прочие")) "✨ Прочие" else "✨ Смешанные"
    } else {
        name
    }
    return categoryColorMap[cleanName] ?: run {
        val (systemColor, _) = getCategoryColorAndIcon(cleanName, "")
        if (systemColor == Slate400) {
            if (filterType == "expense") Rose500 else Emerald400
        } else {
            systemColor
        }
    }
}

fun filterAndSortTransactions(
    typeFilteredList: List<TransactionEntity>,
    searchQuery: String,
    selectedCategoryFilter: String?,
    isDrilledDownToMixed: Boolean,
    remainingCategoryNames: List<String>,
    sortOption: String
): List<TransactionEntity> {
    val searchFilteredList = typeFilteredList.filter { tx ->
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

    return when (sortOption) {
        "desc" -> searchFilteredList.sortedByDescending { kotlin.math.abs(it.amount) }
        "asc" -> searchFilteredList.sortedBy { kotlin.math.abs(it.amount) }
        "name" -> searchFilteredList.sortedBy { it.category.ifBlank { it.subcategory } }
        "name_desc" -> searchFilteredList.sortedByDescending { it.category.ifBlank { it.subcategory } }
        else -> searchFilteredList.sortedWith(
            compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt }
        )
    }
}
