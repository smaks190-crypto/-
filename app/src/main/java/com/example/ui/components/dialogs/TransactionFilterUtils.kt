package com.example.ui.components.dialogs

import com.example.data.db.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val RussianLocale = Locale("ru", "RU")
private val NominativeMonths = arrayOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

/**
 * Единственные 3 кнопки переключателя периода внутри графика: Нед | Мес | Год.
 */
internal val PERIOD_SWITCHER_TABS: List<Pair<String, String>> = listOf(
    "week" to "Нед",
    "month" to "Мес",
    "year" to "Год"
)

/**
 * Централизованное состояние периода в диалоге «Все операции»:
 * - По умолчанию при открытии (`selectedPeriod == null`) ни одна кнопка периода («Нед», «Мес», «Год») НЕ подсвечена,
 *   и чип месяца под поиском НЕ активирован — список транзакций позволяет свободно листать всю историю вниз
 *   (Октябрь -> Сентябрь -> Август и т.д.).
 * - При этом верхние карточки («Траты • Месяц» / «Доходы • Месяц»), круговая/столбчатая диаграмма и сам чип месяца
 *   под поиском динамически синхронизируются с тем месяцем, который сейчас виден в списке при скролле (или выбран свайпом графика).
 * - Нажатие на чип месяца или кнопку «Мес» включает/выключает строгую фиксацию (фильтрацию) списка по этому месяцу.
 * - Нажатие на уже активную кнопку («Нед», «Мес», «Год») снимает фиксацию периода (`selectedPeriod = null`).
 */
internal data class AllTransactionsPeriodState(
    val stableAnchorDateStr: String,
    val selectedPeriod: String? = null, // null (default unlocked), "week", "month", "year", "custom"
    val swipeWeekOffset: Int = 0,
    val swipeMonthOffset: Int = 0,
    val swipeYearOffset: Int = 0,
    val customStartStr: String? = null,
    val customEndStr: String? = null,
    val customLabelStr: String? = null
) {
    /**
     * Сдвинутая опорная дата для текущего выбранного/пролистанного месяца (`anchor + swipeMonthOffset`).
     * Используется как единый источник правды для месяца, даже когда кнопка «Мес» не заблокирована.
     */
    val displayedMonthDateStr: String
        get() = shiftAnchorMonth(stableAnchorDateStr, swipeMonthOffset)

    val effectivePeriod: String
        get() = selectedPeriod ?: "month"

    val isPeriodLocked: Boolean
        get() = selectedPeriod != null || !customStartStr.isNullOrBlank()

    val currentOffsetInt: Int
        get() = when (selectedPeriod) {
            "week" -> swipeWeekOffset
            "month" -> swipeMonthOffset
            "year" -> swipeYearOffset
            "custom" -> 0
            else -> swipeMonthOffset
        }

    /**
     * Индекс подсвеченной вкладки в переключателе `Нед | Мес | Год`, либо `-1`, если фиксация периода отключена.
     */
    val activeTabIndex: Int
        get() = PERIOD_SWITCHER_TABS.indexOfFirst { it.first == selectedPeriod }

    /**
     * Обработка нажатия на вкладку периода («week», «month», «year»):
     * - Повторное нажатие на уже выбранный период снимает его (`selectedPeriod = null`), оставляя текущий месяц на экране.
     * - Первое нажатие на «Мес» фиксирует текущий пролистанный месяц (`swipeMonthOffset` сохраняется).
     * - Переключение на «Нед» или «Год» сбрасывает смещение недели/года к 0 относительно текущего отображаемого месяца.
     */
    fun onTogglePeriodTab(tappedPeriod: String): AllTransactionsPeriodState {
        return if (selectedPeriod == tappedPeriod) {
            copy(
                selectedPeriod = null,
                customStartStr = null,
                customEndStr = null,
                customLabelStr = null
            )
        } else {
            copy(
                selectedPeriod = tappedPeriod,
                swipeWeekOffset = if (tappedPeriod == "week") 0 else swipeWeekOffset,
                swipeYearOffset = if (tappedPeriod == "year") 0 else swipeYearOffset,
                customStartStr = null,
                customEndStr = null,
                customLabelStr = null
            )
        }
    }

    /**
     * Свайп графика влево/вправо (`delta = -1` — прошлый период, `delta = +1` — следующий период).
     * В режиме по умолчанию (`selectedPeriod == null`) листает месяцы, НЕ блокируя кнопку «Мес».
     */
    fun onSwipePeriod(delta: Int): AllTransactionsPeriodState {
        return when (selectedPeriod) {
            "week" -> copy(swipeWeekOffset = swipeWeekOffset + delta)
            "month" -> copy(swipeMonthOffset = swipeMonthOffset + delta)
            "year" -> copy(swipeYearOffset = swipeYearOffset + delta)
            "custom" -> this
            else -> copy(
                customStartStr = null,
                customEndStr = null,
                customLabelStr = null,
                swipeMonthOffset = swipeMonthOffset + delta
            )
        }
    }

    /**
     * Вызывается при вертикальном скролле списка транзакций в режиме без фиксации периода (`selectedPeriod == null`),
     * чтобы заголовок месяца, чип под поиском и графики плавно переключались на тот месяц, до которого долистал пользователь.
     */
    fun onScrolledToDate(visibleDateStr: String): AllTransactionsPeriodState {
        if (isPeriodLocked || visibleDateStr.isBlank()) return this
        val newMonthOffset = computeMonthDiff(stableAnchorDateStr, visibleDateStr)
        return if (newMonthOffset != swipeMonthOffset) {
            copy(swipeMonthOffset = newMonthOffset)
        } else {
            this
        }
    }

    fun onCustomRangeSelected(startStr: String, endStr: String, label: String): AllTransactionsPeriodState {
        return copy(
            selectedPeriod = "custom",
            customStartStr = startStr,
            customEndStr = endStr,
            customLabelStr = label
        )
    }

    fun onClearCustomOrLockedPeriod(): AllTransactionsPeriodState {
        return copy(
            selectedPeriod = null,
            customStartStr = null,
            customEndStr = null,
            customLabelStr = null
        )
    }
}

/**
 * Вычисляет разницу в полных календарных месяцах между [anchorDateStr] и [targetDateStr] (`target - anchor`).
 * Например, если `anchor = 2026-10-15`, а `target = 2026-09-20`, возвращает `-1`.
 */
internal fun computeMonthDiff(anchorDateStr: String, targetDateStr: String): Int {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val anchorDate = try {
        sdf.parse(anchorDateStr)
    } catch (_: Exception) {
        null
    } ?: return 0
    val targetDate = try {
        sdf.parse(targetDateStr)
    } catch (_: Exception) {
        null
    } ?: return 0

    val anchorCal = Calendar.getInstance().apply { time = anchorDate }
    val targetCal = Calendar.getInstance().apply { time = targetDate }

    val yearDiff = targetCal.get(Calendar.YEAR) - anchorCal.get(Calendar.YEAR)
    val monthDiff = targetCal.get(Calendar.MONTH) - anchorCal.get(Calendar.MONTH)
    return yearDiff * 12 + monthDiff
}

internal fun shiftAnchorMonth(anchorDateStr: String, monthOffset: Int): String {
    if (monthOffset == 0) return anchorDateStr
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val baseDate = try {
        sdf.parse(anchorDateStr) ?: Date()
    } catch (_: Exception) {
        Date()
    }
    val cal = Calendar.getInstance().apply {
        time = baseDate
        set(Calendar.DAY_OF_MONTH, 15)
        add(Calendar.MONTH, monthOffset)
    }
    return sdf.format(cal.time)
}

fun buildDynamicPeriodLabel(
    stableAnchorDateStr: String,
    customLabelStr: String?,
    selectedPeriod: String?,
    swipeMonthOffset: Int,
    swipeWeekOffset: Int,
    swipeYearOffset: Int
): String {
    if (!customLabelStr.isNullOrBlank()) return customLabelStr
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayedMonthAnchorStr = shiftAnchorMonth(stableAnchorDateStr, swipeMonthOffset)
    val monthBaseDate = try {
        sdf.parse(displayedMonthAnchorStr) ?: Date()
    } catch (_: Exception) {
        Date()
    }
    val cal = Calendar.getInstance().apply { time = monthBaseDate }

    return when (selectedPeriod) {
        null, "month" -> {
            val mIdx = cal.get(Calendar.MONTH)
            val yr = cal.get(Calendar.YEAR)
            val currentYr = Calendar.getInstance().get(Calendar.YEAR)
            if (yr != currentYr) "${NominativeMonths[mIdx]} $yr" else NominativeMonths[mIdx]
        }
        "week" -> {
            val anchorCal = cal.clone() as Calendar
            anchorCal.add(Calendar.WEEK_OF_YEAR, swipeWeekOffset)
            val startCal = anchorCal.clone() as Calendar
            val dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK)
            val diffToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
            startCal.add(Calendar.DAY_OF_MONTH, diffToMonday)
            val endCal = startCal.clone() as Calendar
            endCal.add(Calendar.DAY_OF_MONTH, 6)
            val shortFmt = SimpleDateFormat("d MMM", RussianLocale)
            "${shortFmt.format(startCal.time)} – ${shortFmt.format(endCal.time)}"
        }
        "year" -> {
            cal.add(Calendar.YEAR, swipeYearOffset)
            "${cal.get(Calendar.YEAR)} год"
        }
        else -> {
            val mIdx = cal.get(Calendar.MONTH)
            val yr = cal.get(Calendar.YEAR)
            val currentYr = Calendar.getInstance().get(Calendar.YEAR)
            if (yr != currentYr) "${NominativeMonths[mIdx]} $yr" else NominativeMonths[mIdx]
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
    if (transactions.isEmpty()) return emptyList()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    if (selectedPeriod == "custom" && offset == currentOffsetInt && !customStartStr.isNullOrBlank() && !customEndStr.isNullOrBlank()) {
        return transactions.filter { it.date >= customStartStr && it.date <= customEndStr }
    }

    val effectivePeriod = selectedPeriod ?: "month"

    return when (effectivePeriod) {
        "month" -> {
            val baseDate = try {
                sdf.parse(stableAnchorDateStr) ?: Date()
            } catch (_: Exception) {
                Date()
            }
            val cal = Calendar.getInstance().apply {
                time = baseDate
                set(Calendar.DAY_OF_MONTH, 15)
                add(Calendar.MONTH, offset)
            }
            val targetMonthPrefix = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
            transactions.filter { it.date.startsWith(targetMonthPrefix) }
        }
        "week" -> {
            val displayedMonthStr = shiftAnchorMonth(stableAnchorDateStr, swipeMonthOffset)
            val baseDate = try {
                sdf.parse(displayedMonthStr) ?: Date()
            } catch (_: Exception) {
                Date()
            }
            val anchorCal = Calendar.getInstance().apply {
                time = baseDate
                add(Calendar.WEEK_OF_YEAR, offset)
            }
            val startCal = anchorCal.clone() as Calendar
            val dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK)
            val diffToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
            startCal.add(Calendar.DAY_OF_MONTH, diffToMonday)

            val endCal = startCal.clone() as Calendar
            endCal.add(Calendar.DAY_OF_MONTH, 6)

            val startStr = sdf.format(startCal.time)
            val endStr = sdf.format(endCal.time)
            transactions.filter { it.date in startStr..endStr }
        }
        "year" -> {
            val displayedMonthStr = shiftAnchorMonth(stableAnchorDateStr, swipeMonthOffset)
            val baseDate = try {
                sdf.parse(displayedMonthStr) ?: Date()
            } catch (_: Exception) {
                Date()
            }
            val cal = Calendar.getInstance().apply {
                time = baseDate
                add(Calendar.YEAR, offset)
            }
            val targetYearPrefix = SimpleDateFormat("yyyy", Locale.US).format(cal.time)
            transactions.filter { it.date.startsWith(targetYearPrefix) }
        }
        else -> transactions
    }
}

fun calculateCategoryTotalsForOffset(
    rawList: List<TransactionEntity>,
    filterType: String,
    isDrilledDownToMixed: Boolean
): List<Pair<String, Double>> {
    val tList = when (filterType) {
        "income" -> rawList.filter { it.type.equals("income", ignoreCase = true) }
        "expense" -> rawList.filter { it.type.equals("expense", ignoreCase = true) }
        else -> rawList
    }
    val map = tList.groupBy { it.category.ifBlank { "Прочее" } }
        .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
        .toList()
        .sortedByDescending { it.second }

    if (map.isEmpty()) return emptyList()
    val totSum = map.sumOf { it.second }
    val thresh = totSum * 0.07
    val smCats = map.filter { it.second < thresh }
    val lgCats = map.filter { it.second >= thresh }
    val canGrp = smCats.size >= 2 && lgCats.isNotEmpty()

    return when {
        isDrilledDownToMixed && canGrp -> smCats
        isDrilledDownToMixed && map.size > 3 -> map.drop(2)
        canGrp -> lgCats + Pair("Смешанные траты", smCats.sumOf { it.second })
        map.size <= 3 -> map
        else -> {
            val top2 = map.take(2)
            val rem = map.drop(2)
            top2 + Pair("Смешанные траты", rem.sumOf { it.second })
        }
    }
}

fun computeActiveCategoryTotals(
    categoryTotalsMap: List<Pair<String, Double>>,
    isDrilledDownToMixed: Boolean,
    categoryThreshold: Double
): List<Pair<String, Double>> {
    if (categoryTotalsMap.isEmpty()) return emptyList()
    val smallCats = categoryTotalsMap.filter { it.second < categoryThreshold }
    val largeCats = categoryTotalsMap.filter { it.second >= categoryThreshold }
    val canGroupByPercent = smallCats.size >= 2 && largeCats.isNotEmpty()

    return when {
        isDrilledDownToMixed && canGroupByPercent -> smallCats
        isDrilledDownToMixed && categoryTotalsMap.size > 3 -> categoryTotalsMap.drop(2)
        canGroupByPercent -> {
            val mixedSum = smallCats.sumOf { it.second }
            largeCats + Pair("Смешанные траты", mixedSum)
        }
        categoryTotalsMap.size <= 3 -> categoryTotalsMap
        else -> {
            val top2 = categoryTotalsMap.take(2)
            val remaining = categoryTotalsMap.drop(2)
            val mixedSum = remaining.sumOf { it.second }
            top2 + Pair("Смешанные траты", mixedSum)
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
    val q = searchQuery.trim().lowercase(Locale.getDefault())
    val searched = typeFilteredList.filter { tx ->
        val matchesCategory = when {
            selectedCategoryFilter != null -> tx.category.ifBlank { "Прочее" } == selectedCategoryFilter
            isDrilledDownToMixed -> tx.category.ifBlank { "Прочее" } in remainingCategoryNames
            else -> true
        }
        val matchesQuery = if (q.isEmpty()) {
            true
        } else {
            tx.category.lowercase(Locale.getDefault()).contains(q) ||
                tx.subcategory.lowercase(Locale.getDefault()).contains(q) ||
                tx.amount.toInt().toString().contains(q)
        }
        matchesCategory && matchesQuery
    }

    return when (sortOption) {
        "desc" -> searched.sortedByDescending { kotlin.math.abs(it.amount) }
        "asc" -> searched.sortedBy { kotlin.math.abs(it.amount) }
        "name" -> searched.sortedBy { (if (it.subcategory.isNotBlank()) it.subcategory else it.category).lowercase(Locale.getDefault()) }
        "name_desc" -> searched.sortedByDescending { (if (it.subcategory.isNotBlank()) it.subcategory else it.category).lowercase(Locale.getDefault()) }
        else -> searched.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt })
    }
}
