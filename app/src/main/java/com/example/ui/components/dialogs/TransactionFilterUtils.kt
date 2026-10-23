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

fun buildDynamicPeriodLabel(
    stableAnchorDateStr: String,
    customLabelStr: String?,
    selectedPeriod: String,
    swipeMonthOffset: Int,
    swipeWeekOffset: Int,
    swipeYearOffset: Int
): String {
    return if (selectedPeriod == "custom" && !customLabelStr.isNullOrBlank()) {
        customLabelStr
    } else if (selectedPeriod == "all") {
        "За всё время"
    } else {
        val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val anchorDate = try { sdf.parse(anchorDateStr) } catch (e: Exception) { null } ?: Date()
        val cal = Calendar.getInstance().apply { time = anchorDate }

        if (selectedPeriod == "week") {
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
        } else if (selectedPeriod == "year") {
            cal.add(Calendar.YEAR, swipeYearOffset)
            "${cal.get(Calendar.YEAR)} год"
        } else {
            cal.add(Calendar.MONTH, swipeMonthOffset)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val monthYear = cal.get(Calendar.YEAR)
            val formatPattern = if (monthYear != currentYear) "LLLL yyyy" else "LLLL"
            val rawStr = SimpleDateFormat(formatPattern, Locale("ru", "RU")).format(cal.time)
            rawStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
        }
    }
}

fun filterTransactionsForOffset(
    transactions: List<TransactionEntity>,
    selectedPeriod: String,
    customStartStr: String?,
    customEndStr: String?,
    stableAnchorDateStr: String,
    currentOffsetInt: Int,
    offset: Int
): List<TransactionEntity> {
    if (selectedPeriod == "custom" && !customStartStr.isNullOrBlank() && !customEndStr.isNullOrBlank() && offset == currentOffsetInt) {
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
    if (selectedPeriod == "all" && offset == currentOffsetInt) {
        return transactions
    }

    val effectivePeriod = if (selectedPeriod == "all" || selectedPeriod == "custom") "month" else selectedPeriod
    val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val anchorDate = try { sdf.parse(anchorDateStr) } catch (e: Exception) { null } ?: Date()
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
        "month" -> {
            cal.add(Calendar.MONTH, offset)
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1
            val monthPrefix = String.format(Locale.US, "%04d-%02d-", currentYear, currentMonth)
            transactions.filter { tx ->
                tx.date.startsWith(monthPrefix)
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
        else -> transactions
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

