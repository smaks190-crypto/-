package com.example.ui.components.charts

import com.example.data.db.TransactionEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class HomeChartPeriod(
    val code: String,
    val labelRu: String
) {
    WEEK("WEEK", "Неделя"),
    MONTH("MONTH", "Месяц"),
    YEAR("YEAR", "Год");

    companion object {
        fun fromLabelOrCode(value: String?): HomeChartPeriod {
            if (value.isNullOrBlank()) return MONTH
            return entries.firstOrNull {
                it.code.equals(value, ignoreCase = true) ||
                    it.labelRu.equals(value, ignoreCase = true)
            } ?: MONTH
        }
    }
}

data class HomeChartPoint(
    val index: Int,
    val shortAxisLabel: String,
    val fullDateTitle: String,
    val dateIsoOrPrefix: String,
    val expense: Double,
    val income: Double
) {
    val net: Double get() = income - expense
}

data class HomeAxisTick(
    val pointIndex: Int,
    val label: String,
    val isWeekendOrAccent: Boolean = false
)

data class HomeChartState(
    val period: HomeChartPeriod,
    val periodSubtitle: String,
    val points: List<HomeChartPoint>,
    val axisTicks: List<HomeAxisTick>,
    val totalExpense: Double,
    val totalIncome: Double,
    val netBalance: Double,
    val maxExpense: Double,
    val maxIncome: Double,
    val yAxisMax: Double,
    val hasExpenses: Boolean,
    val hasIncomes: Boolean,
    val hasAnyData: Boolean,
    val isDeficit: Boolean,
    val peakExpensePointIndex: Int?
)

data class CategorySliceItem(
    val category: String,
    val amount: Double,
    val ratio: Float,
    val percent: Int,
    val sweepAngleDeg: Float
)

/**
 * Чистый тестируемый движок подготовки данных и синхронизации периодов для графиков главного экрана.
 * Исключает бизнес-логику и расхождения дат внутри @Composable-функций:
 * выбранный период, подзаголовок, итоговые суммы и точки графика всегда имеют единый источник истины.
 */
object HomeChartDataEngine {

    val MonthsNominativeRu = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    private val MonthsGenitiveShortRu = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек"
    )

    private val MonthsShortUpperRu = listOf(
        "ЯНВ", "ФЕВ", "МАР", "АПР", "МАЙ", "ИЮН",
        "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК"
    )

    private val WeekDaysShortRu = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    private val WeekDaysFullRu = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    fun formatCompactChartAmount(value: Double): String {
        val safe = if (value.isNaN() || value.isInfinite()) 0.0 else value
        val absVal = abs(safe)
        val sign = if (safe < 0.0) "−" else ""
        return when {
            absVal >= 1_000_000_000.0 -> String.format(Locale("ru", "RU"), "%s%.1fB\u00A0₽", sign, absVal / 1_000_000_000.0)
            absVal >= 1_000_000.0 -> String.format(Locale("ru", "RU"), "%s%.1fM\u00A0₽", sign, absVal / 1_000_000.0)
            absVal >= 100_000.0 -> "$sign${(absVal / 1_000.0).roundToInt()}к\u00A0₽"
            absVal >= 10_000.0 -> String.format(Locale("ru", "RU"), "%s%.1fк\u00A0₽", sign, absVal / 1_000.0)
            absVal > 0.0 && absVal < 1.0 -> {
                val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
                    groupingSeparator = ' '
                    decimalSeparator = ','
                }
                "$sign${DecimalFormat("0.##", symbols).format(absVal)}\u00A0₽"
            }
            else -> {
                val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
                    groupingSeparator = ' '
                    decimalSeparator = ','
                }
                "$sign${DecimalFormat("#,##0", symbols).format(absVal)}\u00A0₽"
            }
        }
    }

    /**
     * Строит полностью синхронизированное состояние графика для выбранного периода (WEEK / MONTH / YEAR),
     * привязанного к выбранному пользователем месяцу и году на главном экране.
     */
    fun buildHomeChartState(
        transactions: List<TransactionEntity>,
        period: HomeChartPeriod,
        selectedMonthIdx: Int,
        selectedYear: Int,
        referenceDateIso: String? = null
    ): HomeChartState {
        val safeMonthIdx = selectedMonthIdx.coerceIn(0, 11)
        val safeYear = selectedYear.coerceIn(1970, 2100)

        return when (period) {
            HomeChartPeriod.WEEK -> buildWeekState(
                transactions = transactions,
                selectedMonthIdx = safeMonthIdx,
                selectedYear = safeYear,
                referenceDateIso = referenceDateIso
            )
            HomeChartPeriod.MONTH -> buildMonthState(
                transactions = transactions,
                selectedMonthIdx = safeMonthIdx,
                selectedYear = safeYear
            )
            HomeChartPeriod.YEAR -> buildYearState(
                transactions = transactions,
                selectedYear = safeYear
            )
        }
    }

    private fun buildWeekState(
        transactions: List<TransactionEntity>,
        selectedMonthIdx: Int,
        selectedYear: Int,
        referenceDateIso: String?
    ): HomeChartState {
        val targetMonthPrefix = String.format(Locale.US, "%04d-%02d", selectedYear, selectedMonthIdx + 1)

        // Определяем опорную дату недели внутри выбранного пользователем месяца:
        // 1) если referenceDateIso принадлежит выбранному месяцу — используем её;
        // 2) иначе если в выбранном месяце есть операции — берём дату последней операции в этом месяце;
        // 3) иначе если выбран текущий календарный месяц — берём сегодняшний день;
        // 4) иначе — 1-е число выбранного месяца.
        val anchorDateIso = when {
            !referenceDateIso.isNullOrBlank() && referenceDateIso.startsWith(targetMonthPrefix) -> referenceDateIso
            else -> {
                val latestTxInMonth = transactions
                    .asSequence()
                    .filter { it.date.startsWith(targetMonthPrefix) }
                    .maxByOrNull { it.date }
                    ?.date
                if (latestTxInMonth != null) {
                    latestTxInMonth
                } else {
                    val now = Calendar.getInstance()
                    if (now.get(Calendar.YEAR) == selectedYear && now.get(Calendar.MONTH) == selectedMonthIdx) {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
                    } else {
                        "$targetMonthPrefix-01"
                    }
                }
            }
        }

        val anchorCal = parseDateIso(anchorDateIso, selectedYear, selectedMonthIdx)
        val mondayCal = (anchorCal.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            val dow = get(Calendar.DAY_OF_WEEK)
            val diffToMonday = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
            add(Calendar.DAY_OF_YEAR, diffToMonday)
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val weekDates = (0..6).map { offset ->
            val d = (mondayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            sdf.format(d.time)
        }

        val expByDate = mutableMapOf<String, Double>()
        val incByDate = mutableMapOf<String, Double>()

        transactions.forEach { tx ->
            if (tx.date in weekDates) {
                val cleanAmt = tx.amount.coerceAtLeast(0.0)
                if (tx.type == "expense") {
                    expByDate[tx.date] = (expByDate[tx.date] ?: 0.0) + cleanAmt
                } else if (tx.type == "income") {
                    incByDate[tx.date] = (incByDate[tx.date] ?: 0.0) + cleanAmt
                }
            }
        }

        val points = weekDates.mapIndexed { idx, dateIso ->
            val humanDay = formatDayShortMonth(dateIso)
            HomeChartPoint(
                index = idx,
                shortAxisLabel = WeekDaysShortRu[idx],
                fullDateTitle = "${WeekDaysFullRu[idx]}, $humanDay",
                dateIsoOrPrefix = dateIso,
                expense = expByDate[dateIso] ?: 0.0,
                income = incByDate[dateIso] ?: 0.0
            )
        }

        val axisTicks = points.map { pt ->
            HomeAxisTick(
                pointIndex = pt.index,
                label = pt.shortAxisLabel,
                isWeekendOrAccent = pt.index >= 5
            )
        }

        val subtitle = "${formatDayShortMonth(weekDates.first())} – ${formatDayShortMonth(weekDates.last())} $selectedYear"

        return finalizeState(
            period = HomeChartPeriod.WEEK,
            subtitle = subtitle,
            points = points,
            axisTicks = axisTicks
        )
    }

    private fun buildMonthState(
        transactions: List<TransactionEntity>,
        selectedMonthIdx: Int,
        selectedYear: Int
    ): HomeChartState {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonthIdx)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceIn(28, 31)
        val monthPrefix = String.format(Locale.US, "%04d-%02d", selectedYear, selectedMonthIdx + 1)

        val expByDay = DoubleArray(daysInMonth + 1)
        val incByDay = DoubleArray(daysInMonth + 1)

        transactions.forEach { tx ->
            if (tx.date.startsWith(monthPrefix)) {
                val day = tx.date.takeLast(2).toIntOrNull()
                if (day != null && day in 1..daysInMonth) {
                    val cleanAmt = tx.amount.coerceAtLeast(0.0)
                    if (tx.type == "expense") {
                        expByDay[day] += cleanAmt
                    } else if (tx.type == "income") {
                        incByDay[day] += cleanAmt
                    }
                }
            }
        }

        val genitiveMonth = MonthsGenitiveShortRu[selectedMonthIdx]
        val points = (1..daysInMonth).map { day ->
            val dateIso = String.format(Locale.US, "%s-%02d", monthPrefix, day)
            HomeChartPoint(
                index = day - 1,
                shortAxisLabel = day.toString(),
                fullDateTitle = "$day $genitiveMonth $selectedYear",
                dateIsoOrPrefix = dateIso,
                expense = expByDay[day],
                income = incByDay[day]
            )
        }

        val tickDays = listOf(1, 5, 10, 15, 20, 25, daysInMonth).distinct()
        val axisTicks = tickDays.map { d ->
            HomeAxisTick(
                pointIndex = (d - 1).coerceIn(0, points.lastIndex),
                label = d.toString(),
                isWeekendOrAccent = false
            )
        }

        val subtitle = "${MonthsNominativeRu[selectedMonthIdx]} $selectedYear"

        return finalizeState(
            period = HomeChartPeriod.MONTH,
            subtitle = subtitle,
            points = points,
            axisTicks = axisTicks
        )
    }

    private fun buildYearState(
        transactions: List<TransactionEntity>,
        selectedYear: Int
    ): HomeChartState {
        val yearPrefix = String.format(Locale.US, "%04d-", selectedYear)
        val expByMonth = DoubleArray(12)
        val incByMonth = DoubleArray(12)

        transactions.forEach { tx ->
            if (tx.date.startsWith(yearPrefix) && tx.date.length >= 7) {
                val monthNum = tx.date.substring(5, 7).toIntOrNull()
                if (monthNum != null && monthNum in 1..12) {
                    val cleanAmt = tx.amount.coerceAtLeast(0.0)
                    if (tx.type == "expense") {
                        expByMonth[monthNum - 1] += cleanAmt
                    } else if (tx.type == "income") {
                        incByMonth[monthNum - 1] += cleanAmt
                    }
                }
            }
        }

        val points = (0 until 12).map { mIdx ->
            val ymPrefix = String.format(Locale.US, "%04d-%02d", selectedYear, mIdx + 1)
            HomeChartPoint(
                index = mIdx,
                shortAxisLabel = MonthsShortUpperRu[mIdx],
                fullDateTitle = "${MonthsNominativeRu[mIdx]} $selectedYear",
                dateIsoOrPrefix = ymPrefix,
                expense = expByMonth[mIdx],
                income = incByMonth[mIdx]
            )
        }

        val axisTicks = points.map { pt ->
            HomeAxisTick(
                pointIndex = pt.index,
                label = pt.shortAxisLabel.take(1),
                isWeekendOrAccent = false
            )
        }

        return finalizeState(
            period = HomeChartPeriod.YEAR,
            subtitle = "$selectedYear год",
            points = points,
            axisTicks = axisTicks
        )
    }

    private fun finalizeState(
        period: HomeChartPeriod,
        subtitle: String,
        points: List<HomeChartPoint>,
        axisTicks: List<HomeAxisTick>
    ): HomeChartState {
        val totalExp = points.sumOf { it.expense }
        val totalInc = points.sumOf { it.income }
        val maxExp = points.maxOfOrNull { it.expense } ?: 0.0
        val maxInc = points.maxOfOrNull { it.income } ?: 0.0
        val rawMax = max(maxExp, maxInc)
        val yAxisMax = when {
            rawMax <= 0.0 -> 1_000.0
            rawMax < 10.0 -> rawMax * 1.25
            else -> rawMax * 1.12
        }

        val peakIdx = if (maxExp > 0.0) {
            points.indexOfFirst { it.expense >= maxExp }.takeIf { it >= 0 }
        } else null

        return HomeChartState(
            period = period,
            periodSubtitle = subtitle,
            points = points,
            axisTicks = axisTicks,
            totalExpense = totalExp,
            totalIncome = totalInc,
            netBalance = totalInc - totalExp,
            maxExpense = maxExp,
            maxIncome = maxInc,
            yAxisMax = yAxisMax,
            hasExpenses = totalExp > 0.0,
            hasIncomes = totalInc > 0.0,
            hasAnyData = totalExp > 0.0 || totalInc > 0.0,
            isDeficit = totalExp > totalInc && totalInc > 0.0,
            peakExpensePointIndex = peakIdx
        )
    }

    /**
     * Подготовка долей категорий для круговых/кольцевых графиков с защитой от нулевых,
     * отрицательных и экстремально малых значений.
     */
    fun buildCategorySlices(categoryAmounts: Map<String, Double>): List<CategorySliceItem> {
        val positiveEntries = categoryAmounts.entries
            .filter { it.key.isNotBlank() && it.value > 0.0 && !it.value.isNaN() && !it.value.isInfinite() }
            .sortedByDescending { it.value }

        val total = positiveEntries.sumOf { it.value }
        if (total <= 0.0) return emptyList()

        return positiveEntries.map { entry ->
            val ratio = (entry.value / total).toFloat().coerceIn(0f, 1f)
            val pct = (ratio * 100f).roundToInt().coerceIn(0, 100)
            CategorySliceItem(
                category = entry.key,
                amount = entry.value,
                ratio = ratio,
                percent = pct,
                sweepAngleDeg = (ratio * 360f).coerceIn(0f, 360f)
            )
        }
    }

    private fun parseDateIso(dateIso: String, fallbackYear: Int, fallbackMonthIdx: Int): Calendar {
        val cal = Calendar.getInstance()
        try {
            val parts = dateIso.split("-")
            if (parts.size == 3) {
                cal.set(Calendar.YEAR, parts[0].toInt())
                cal.set(Calendar.MONTH, (parts[1].toInt() - 1).coerceIn(0, 11))
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt().coerceIn(1, maxDay))
                return cal
            }
        } catch (_: Exception) {
        }
        cal.set(Calendar.YEAR, fallbackYear)
        cal.set(Calendar.MONTH, fallbackMonthIdx.coerceIn(0, 11))
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal
    }

    private fun formatDayShortMonth(dateIso: String): String {
        val parts = dateIso.split("-")
        if (parts.size == 3) {
            val m = (parts[1].toIntOrNull() ?: 1).coerceIn(1, 12)
            val d = parts[2].toIntOrNull() ?: 1
            return "$d ${MonthsGenitiveShortRu[m - 1]}"
        }
        return dateIso
    }
}
