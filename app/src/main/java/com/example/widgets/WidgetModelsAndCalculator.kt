package com.example.widgets

import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionEntity
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ZhabometerMood(
    val titleRu: String,
    val badgeText: String,
    val primaryColorHex: Int,
    val mouthOpenProgress: Float,
    val smileCurve: Float, // +1f = big smile, 0f = flat/skeptical, -1f = frown/shock
    val pupilOffsetX: Float,
    val pupilOffsetY: Float
) {
    PROSPEROUS(
        titleRu = "Царь болота",
        badgeText = "ИДЕАЛЬНЫЙ БАЛАНС",
        primaryColorHex = 0xFF34D399.toInt(),
        mouthOpenProgress = 0.65f,
        smileCurve = 1.0f,
        pupilOffsetX = 0f,
        pupilOffsetY = -0.15f
    ),
    CALM(
        titleRu = "Давид доволен",
        badgeText = "В РАМКАХ ПЛАНА",
        primaryColorHex = 0xFF10B981.toInt(),
        mouthOpenProgress = 0.25f,
        smileCurve = 0.7f,
        pupilOffsetX = 0f,
        pupilOffsetY = 0f
    ),
    ZEN(
        titleRu = "Дзен-жаба",
        badgeText = "ТИШИНА И ПОКОЙ",
        primaryColorHex = 0xFF60A5FA.toInt(),
        mouthOpenProgress = 0.10f,
        smileCurve = 0.45f,
        pupilOffsetX = 0f,
        pupilOffsetY = 0.1f
    ),
    SKEPTICAL(
        titleRu = "Прищур жабы",
        badgeText = "ТРАТЫ РАСТУТ",
        primaryColorHex = 0xFFFBBF24.toInt(),
        mouthOpenProgress = 0.15f,
        smileCurve = -0.25f,
        pupilOffsetX = 0.45f,
        pupilOffsetY = -0.1f
    ),
    ALARMED(
        titleRu = "Жаба душит!",
        badgeText = "ПЕРЕРАСХОД ЛИМИТА",
        primaryColorHex = 0xFFF43F5E.toInt(),
        mouthOpenProgress = 0.85f,
        smileCurve = -0.9f,
        pupilOffsetX = 0f,
        pupilOffsetY = 0.2f
    )
}

enum class MiniAnalyticsPeriod(val code: String, val labelRu: String, val shortLabelRu: String) {
    WEEK("WEEK", "Неделя", "Нед"),
    MONTH("MONTH", "Месяц", "Мес"),
    YEAR("YEAR", "Год", "Год");

    companion object {
        fun fromCode(code: String?): MiniAnalyticsPeriod {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: WEEK
        }
    }

    fun next(): MiniAnalyticsPeriod = when (this) {
        WEEK -> MONTH
        MONTH -> YEAR
        YEAR -> WEEK
    }
}

data class DavidTodayWidgetModel(
    val todayDateIso: String,
    val todayExpense: Double,
    val todayIncome: Double,
    val dailyBudgetLimit: Double?,
    val remainingDailyBudget: Double?,
    val isDailyBudgetExceeded: Boolean,
    val davidComment: String,
    val mood: ZhabometerMood,
    val hasAnyTransactions: Boolean,
    val isPrivacyMode: Boolean
)

data class QuickOperationWidgetModel(
    val todayExpense: Double,
    val todayOperationsCount: Int,
    val isPrivacyMode: Boolean
)

data class MyBudgetWidgetModel(
    val monthTitleRu: String,
    val monthlyBudgetLimit: Double,
    val isBudgetConfigured: Boolean,
    val monthSpent: Double,
    val monthIncome: Double,
    val remainingBudget: Double,
    val overBudgetAmount: Double,
    val usageRatio: Float, // 0f..1f+
    val isExceeded: Boolean,
    val statusText: String,
    val isPrivacyMode: Boolean
)

data class FinancialGoalWidgetModel(
    val goalId: String?,
    val goalTitle: String,
    val savedAmount: Double,
    val targetAmount: Double,
    val remainingAmount: Double,
    val progressRatio: Float,
    val progressPercent: Int,
    val isGoalFound: Boolean,
    val isGoalDeletedOrMissing: Boolean,
    val totalGoalsCount: Int,
    val statusHint: String,
    val isPrivacyMode: Boolean
)

data class MiniAnalyticsWidgetModel(
    val period: MiniAnalyticsPeriod,
    val periodSubtitle: String,
    val totalExpense: Double,
    val totalIncome: Double,
    val netBalance: Double,
    val previousPeriodExpense: Double,
    val expenseDeltaPercent: Int?, // null if no previous data
    val chartExpensePoints: List<Double>,
    val chartIncomePoints: List<Double>,
    val chartLabels: List<String>,
    val hasData: Boolean,
    val isPrivacyMode: Boolean
)

data class ZhabometerWidgetModel(
    val score: Int, // 0..100 (100 = richest/calmest frog, 0 = maximum toad choke)
    val mood: ZhabometerMood,
    val headline: String,
    val tip: String,
    val budgetUsagePercent: Int?,
    val goalProgressPercent: Int?,
    val expenseTrendPercent: Int?,
    val isPrivacyMode: Boolean
)

object DavidWidgetStateCalculator {

    private val MonthsNominativeRu = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )

    fun formatWidgetCurrency(amount: Double, isPrivacyMode: Boolean, showSign: String = ""): String {
        if (isPrivacyMode) {
            return "${showSign}••••\u00A0₽"
        }
        val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val df = DecimalFormat("#,##0", symbols).apply {
            isGroupingUsed = true
        }
        return "$showSign${df.format(amount)}\u00A0₽"
    }

    fun formatCompactCurrency(amount: Double, isPrivacyMode: Boolean): String {
        if (isPrivacyMode) return "••••\u00A0₽"
        val absVal = abs(amount)
        return when {
            absVal >= 1_000_000 -> String.format(Locale("ru", "RU"), "%.1fM\u00A0₽", amount / 1_000_000.0)
            absVal >= 100_000 -> "${(amount / 1_000.0).roundToInt()}к\u00A0₽"
            else -> formatWidgetCurrency(amount, isPrivacyMode = false)
        }
    }

    fun computeMonthlyBudgetLimit(
        categories: List<CategoryEntity>,
        monthIncome: Double,
        explicitMonthlyLimit: Double? = null
    ): Pair<Double, Boolean> {
        if (explicitMonthlyLimit != null && explicitMonthlyLimit > 0.0) {
            return explicitMonthlyLimit to true
        }
        val sumCategoryLimits = categories
            .filter { it.type == "expense" }
            .mapNotNull { it.monthlyLimit }
            .filter { it > 0.0 }
            .sum()
        if (sumCategoryLimits > 0.0) {
            return sumCategoryLimits to true
        }
        if (monthIncome > 0.0) {
            return (monthIncome * 0.80) to true
        }
        return 0.0 to false
    }

    fun computeTodayState(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        todayIso: String,
        explicitMonthlyLimit: Double? = null,
        isPrivacyMode: Boolean = false
    ): DavidTodayWidgetModel {
        val yearMonthPrefix = todayIso.take(7) // yyyy-MM
        val monthTxs = transactions.filter { it.date.startsWith(yearMonthPrefix) }
        val monthIncome = monthTxs.filter { it.type == "income" }.sumOf { it.amount }
        val monthExpense = monthTxs.filter { it.type == "expense" }.sumOf { it.amount }

        val todayTxs = monthTxs.filter { it.date == todayIso }
        val todayExpense = todayTxs.filter { it.type == "expense" }.sumOf { it.amount }
        val todayIncome = todayTxs.filter { it.type == "income" }.sumOf { it.amount }

        val (monthlyLimit, hasLimit) = computeMonthlyBudgetLimit(
            categories = categories,
            monthIncome = monthIncome,
            explicitMonthlyLimit = explicitMonthlyLimit
        )

        val cal = parseIsoToCalendar(todayIso)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
        val remainingDaysInMonth = (daysInMonth - currentDay + 1).coerceAtLeast(1)

        val dailyLimit: Double?
        val remainingDaily: Double?
        val isExceeded: Boolean

        if (hasLimit && monthlyLimit > 0.0) {
            val spentBeforeToday = (monthExpense - todayExpense).coerceAtLeast(0.0)
            val poolForRemainingDays = (monthlyLimit - spentBeforeToday).coerceAtLeast(0.0)
            val safePerDay = poolForRemainingDays / remainingDaysInMonth.toDouble()
            dailyLimit = safePerDay
            remainingDaily = safePerDay - todayExpense
            isExceeded = todayExpense > safePerDay || monthExpense > monthlyLimit
        } else {
            dailyLimit = null
            remainingDaily = null
            isExceeded = false
        }

        val (mood, comment) = when {
            transactions.isEmpty() -> ZhabometerMood.ZEN to "В болоте штиль! Добавь первую операцию, чтобы Давид начал аудит."
            isExceeded && dailyLimit != null -> {
                val over = (todayExpense - dailyLimit).coerceAtLeast(0.0)
                ZhabometerMood.ALARMED to "Ква-раул! Дневной лимит пробит на ${formatCompactCurrency(over, isPrivacyMode)}. Притормози траты!"
            }
            todayExpense == 0.0 && todayIncome > 0.0 -> {
                ZhabometerMood.PROSPEROUS to "Ни рубля расходов и плюс в казну! Сегодня жаба мурлычет от счастья."
            }
            todayExpense == 0.0 -> {
                ZhabometerMood.PROSPEROUS to "Сегодня 0 ₽ расходов — идеальный день для копилки!"
            }
            dailyLimit != null && todayExpense <= dailyLimit * 0.5 -> {
                ZhabometerMood.CALM to "Идём красиво: потрачено меньше половины дневного лимита."
            }
            dailyLimit != null && todayExpense <= dailyLimit -> {
                ZhabometerMood.SKEPTICAL to "Лимит ещё держится, но Давид уже внимательно следит за картой."
            }
            else -> {
                ZhabometerMood.CALM to "За сегодня записано ${todayTxs.size} опер. Загляни в аналитику или задай лимит!"
            }
        }

        return DavidTodayWidgetModel(
            todayDateIso = todayIso,
            todayExpense = todayExpense,
            todayIncome = todayIncome,
            dailyBudgetLimit = dailyLimit,
            remainingDailyBudget = remainingDaily,
            isDailyBudgetExceeded = isExceeded,
            davidComment = comment,
            mood = mood,
            hasAnyTransactions = transactions.isNotEmpty(),
            isPrivacyMode = isPrivacyMode
        )
    }

    fun computeQuickOperationState(
        transactions: List<TransactionEntity>,
        todayIso: String,
        isPrivacyMode: Boolean = false
    ): QuickOperationWidgetModel {
        val todayTxs = transactions.filter { it.date == todayIso }
        val todayExpense = todayTxs.filter { it.type == "expense" }.sumOf { it.amount }
        return QuickOperationWidgetModel(
            todayExpense = todayExpense,
            todayOperationsCount = todayTxs.size,
            isPrivacyMode = isPrivacyMode
        )
    }

    fun computeBudgetState(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        todayIso: String,
        explicitMonthlyLimit: Double? = null,
        isPrivacyMode: Boolean = false
    ): MyBudgetWidgetModel {
        val cal = parseIsoToCalendar(todayIso)
        val monthIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
        val year = cal.get(Calendar.YEAR)
        val monthTitle = "${MonthsNominativeRu[monthIdx]} $year"

        val yearMonthPrefix = todayIso.take(7)
        val monthTxs = transactions.filter { it.date.startsWith(yearMonthPrefix) }
        val monthSpent = monthTxs.filter { it.type == "expense" }.sumOf { it.amount }
        val monthIncome = monthTxs.filter { it.type == "income" }.sumOf { it.amount }

        val (limit, isConfigured) = computeMonthlyBudgetLimit(
            categories = categories,
            monthIncome = monthIncome,
            explicitMonthlyLimit = explicitMonthlyLimit
        )

        if (!isConfigured || limit <= 0.0) {
            return MyBudgetWidgetModel(
                monthTitleRu = monthTitle,
                monthlyBudgetLimit = 0.0,
                isBudgetConfigured = false,
                monthSpent = monthSpent,
                monthIncome = monthIncome,
                remainingBudget = 0.0,
                overBudgetAmount = 0.0,
                usageRatio = if (monthSpent > 0) 0.35f else 0f,
                isExceeded = false,
                statusText = "Лимит не задан • Нажмите для настройки",
                isPrivacyMode = isPrivacyMode
            )
        }

        val remaining = (limit - monthSpent).coerceAtLeast(0.0)
        val overAmount = (monthSpent - limit).coerceAtLeast(0.0)
        val isExceeded = monthSpent > limit
        val ratio = (monthSpent / limit).toFloat().coerceAtLeast(0f)
        val pct = (ratio * 100f).roundToInt()

        val statusText = when {
            isExceeded -> "⚠️ Лимит превышен на ${formatCompactCurrency(overAmount, isPrivacyMode)} ($pct%)"
            pct >= 85 -> "Внимание: израсходовано $pct% бюджета"
            else -> "Использовано $pct% • В запасе ${formatCompactCurrency(remaining, isPrivacyMode)}"
        }

        return MyBudgetWidgetModel(
            monthTitleRu = monthTitle,
            monthlyBudgetLimit = limit,
            isBudgetConfigured = true,
            monthSpent = monthSpent,
            monthIncome = monthIncome,
            remainingBudget = remaining,
            overBudgetAmount = overAmount,
            usageRatio = ratio,
            isExceeded = isExceeded,
            statusText = statusText,
            isPrivacyMode = isPrivacyMode
        )
    }

    fun computeGoalState(
        goals: List<GoalEntity>,
        selectedGoalId: String?,
        isPrivacyMode: Boolean = false
    ): FinancialGoalWidgetModel {
        if (goals.isEmpty()) {
            return FinancialGoalWidgetModel(
                goalId = null,
                goalTitle = "Финансовая цель не создана",
                savedAmount = 0.0,
                targetAmount = 0.0,
                remainingAmount = 0.0,
                progressRatio = 0f,
                progressPercent = 0,
                isGoalFound = false,
                isGoalDeletedOrMissing = false,
                totalGoalsCount = 0,
                statusHint = "Нажмите, чтобы добавить первую копилку с Давидом",
                isPrivacyMode = isPrivacyMode
            )
        }

        val exactMatch = if (!selectedGoalId.isNullOrBlank()) {
            goals.firstOrNull { it.id == selectedGoalId }
        } else null

        val wasDeleted = !selectedGoalId.isNullOrBlank() && exactMatch == null
        val targetGoal = exactMatch ?: goals.maxByOrNull { goal ->
            val t = goal.targetAmount.coerceAtLeast(1.0)
            (goal.currentAmount / t).coerceAtMost(0.999)
        } ?: goals.first()

        val target = targetGoal.targetAmount.coerceAtLeast(1.0)
        val saved = targetGoal.currentAmount.coerceAtLeast(0.0)
        val remaining = (target - saved).coerceAtLeast(0.0)
        val ratio = (saved / target).toFloat().coerceIn(0f, 1f)
        val percent = ((saved / target) * 100.0).roundToInt().coerceIn(0, 100)

        val statusHint = when {
            saved >= target -> "🎉 Цель достигнута! Давид гордится тобой!"
            wasDeleted -> "Прежняя цель удалена • Показана: «${targetGoal.name}»"
            percent >= 75 -> "Финишная прямая! Осталось всего ${100 - percent}%"
            percent >= 35 -> "Отличный темп накоплений ($percent%)"
            else -> "Копим шаг за шагом • Нажмите для пополнения"
        }

        return FinancialGoalWidgetModel(
            goalId = targetGoal.id,
            goalTitle = targetGoal.name,
            savedAmount = saved,
            targetAmount = target,
            remainingAmount = remaining,
            progressRatio = ratio,
            progressPercent = percent,
            isGoalFound = true,
            isGoalDeletedOrMissing = wasDeleted,
            totalGoalsCount = goals.size,
            statusHint = statusHint,
            isPrivacyMode = isPrivacyMode
        )
    }

    fun computeMiniAnalyticsState(
        transactions: List<TransactionEntity>,
        period: MiniAnalyticsPeriod,
        todayIso: String,
        isPrivacyMode: Boolean = false
    ): MiniAnalyticsWidgetModel {
        val cal = parseIsoToCalendar(todayIso)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        when (period) {
            MiniAnalyticsPeriod.WEEK -> {
                // Current week Monday..Sunday containing todayIso
                val mondayCal = (cal.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    val dow = get(Calendar.DAY_OF_WEEK)
                    val offsetToMonday = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
                    add(Calendar.DAY_OF_YEAR, offsetToMonday)
                }
                val currentWeekDates = (0..6).map { offset ->
                    val d = (mondayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                    sdf.format(d.time)
                }
                val prevWeekDates = (0..6).map { offset ->
                    val d = (mondayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset - 7) }
                    sdf.format(d.time)
                }.toSet()

                val expPoints = currentWeekDates.map { dateStr ->
                    transactions.filter { it.type == "expense" && it.date == dateStr }.sumOf { it.amount }
                }
                val incPoints = currentWeekDates.map { dateStr ->
                    transactions.filter { it.type == "income" && it.date == dateStr }.sumOf { it.amount }
                }
                val prevExp = transactions
                    .filter { it.type == "expense" && it.date in prevWeekDates }
                    .sumOf { it.amount }

                val totalExp = expPoints.sum()
                val totalInc = incPoints.sum()
                val deltaPct = if (prevExp > 0.0) {
                    (((totalExp - prevExp) / prevExp) * 100.0).roundToInt()
                } else null

                return MiniAnalyticsWidgetModel(
                    period = period,
                    periodSubtitle = "Текущая неделя",
                    totalExpense = totalExp,
                    totalIncome = totalInc,
                    netBalance = totalInc - totalExp,
                    previousPeriodExpense = prevExp,
                    expenseDeltaPercent = deltaPct,
                    chartExpensePoints = expPoints,
                    chartIncomePoints = incPoints,
                    chartLabels = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"),
                    hasData = totalExp > 0.0 || totalInc > 0.0,
                    isPrivacyMode = isPrivacyMode
                )
            }

            MiniAnalyticsPeriod.MONTH -> {
                val year = cal.get(Calendar.YEAR)
                val monthIdx = cal.get(Calendar.MONTH)
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)
                val currentPrefix = String.format(Locale.US, "%04d-%02d", year, monthIdx + 1)

                val prevMonthCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                val prevPrefix = String.format(
                    Locale.US,
                    "%04d-%02d",
                    prevMonthCal.get(Calendar.YEAR),
                    prevMonthCal.get(Calendar.MONTH) + 1
                )

                val monthTxs = transactions.filter { it.date.startsWith(currentPrefix) }
                val prevMonthExp = transactions
                    .filter { it.type == "expense" && it.date.startsWith(prevPrefix) }
                    .sumOf { it.amount }

                // Group into 6 compact 5-day buckets for clean 4x2 widget visualization
                val bucketRanges = listOf(
                    1..5,
                    6..10,
                    11..15,
                    16..20,
                    21..25,
                    26..daysInMonth
                )
                val expBuckets = DoubleArray(bucketRanges.size)
                val incBuckets = DoubleArray(bucketRanges.size)

                monthTxs.forEach { tx ->
                    val day = tx.date.takeLast(2).toIntOrNull() ?: return@forEach
                    val bIdx = bucketRanges.indexOfFirst { day in it }
                    if (bIdx >= 0) {
                        if (tx.type == "expense") expBuckets[bIdx] += tx.amount
                        else if (tx.type == "income") incBuckets[bIdx] += tx.amount
                    }
                }

                val totalExp = monthTxs.filter { it.type == "expense" }.sumOf { it.amount }
                val totalInc = monthTxs.filter { it.type == "income" }.sumOf { it.amount }
                val deltaPct = if (prevMonthExp > 0.0) {
                    (((totalExp - prevMonthExp) / prevMonthExp) * 100.0).roundToInt()
                } else null

                return MiniAnalyticsWidgetModel(
                    period = period,
                    periodSubtitle = "${MonthsNominativeRu[monthIdx]} $year",
                    totalExpense = totalExp,
                    totalIncome = totalInc,
                    netBalance = totalInc - totalExp,
                    previousPeriodExpense = prevMonthExp,
                    expenseDeltaPercent = deltaPct,
                    chartExpensePoints = expBuckets.toList(),
                    chartIncomePoints = incBuckets.toList(),
                    chartLabels = listOf("1-5", "6-10", "11-15", "16-20", "21-25", "26-$daysInMonth"),
                    hasData = totalExp > 0.0 || totalInc > 0.0,
                    isPrivacyMode = isPrivacyMode
                )
            }

            MiniAnalyticsPeriod.YEAR -> {
                val year = cal.get(Calendar.YEAR)
                val yearPrefix = String.format(Locale.US, "%04d-", year)
                val prevYearPrefix = String.format(Locale.US, "%04d-", year - 1)

                val yearTxs = transactions.filter { it.date.startsWith(yearPrefix) }
                val prevYearExp = transactions
                    .filter { it.type == "expense" && it.date.startsWith(prevYearPrefix) }
                    .sumOf { it.amount }

                val expByMonth = DoubleArray(12)
                val incByMonth = DoubleArray(12)
                yearTxs.forEach { tx ->
                    val m = tx.date.drop(5).take(2).toIntOrNull() ?: return@forEach
                    if (m in 1..12) {
                        if (tx.type == "expense") expByMonth[m - 1] += tx.amount
                        else if (tx.type == "income") incByMonth[m - 1] += tx.amount
                    }
                }

                val totalExp = yearTxs.filter { it.type == "expense" }.sumOf { it.amount }
                val totalInc = yearTxs.filter { it.type == "income" }.sumOf { it.amount }
                val deltaPct = if (prevYearExp > 0.0) {
                    (((totalExp - prevYearExp) / prevYearExp) * 100.0).roundToInt()
                } else null

                val shortMonths = listOf("Я", "Ф", "М", "А", "М", "И", "И", "А", "С", "О", "Н", "Д")

                return MiniAnalyticsWidgetModel(
                    period = period,
                    periodSubtitle = "$year год",
                    totalExpense = totalExp,
                    totalIncome = totalInc,
                    netBalance = totalInc - totalExp,
                    previousPeriodExpense = prevYearExp,
                    expenseDeltaPercent = deltaPct,
                    chartExpensePoints = expByMonth.toList(),
                    chartIncomePoints = incByMonth.toList(),
                    chartLabels = shortMonths,
                    hasData = totalExp > 0.0 || totalInc > 0.0,
                    isPrivacyMode = isPrivacyMode
                )
            }
        }
    }

    fun computeZhabometerState(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        goals: List<GoalEntity>,
        todayIso: String,
        explicitMonthlyLimit: Double? = null,
        isPrivacyMode: Boolean = false
    ): ZhabometerWidgetModel {
        if (transactions.isEmpty() && goals.isEmpty()) {
            return ZhabometerWidgetModel(
                score = 75,
                mood = ZhabometerMood.ZEN,
                headline = "Дзен-жаба ждёт данных",
                tip = "Добавь первые доходы и расходы — Давид рассчитает градус финансового дзена.",
                budgetUsagePercent = null,
                goalProgressPercent = null,
                expenseTrendPercent = null,
                isPrivacyMode = isPrivacyMode
            )
        }

        val budgetState = computeBudgetState(
            transactions = transactions,
            categories = categories,
            todayIso = todayIso,
            explicitMonthlyLimit = explicitMonthlyLimit,
            isPrivacyMode = isPrivacyMode
        )
        val monthAnalytics = computeMiniAnalyticsState(
            transactions = transactions,
            period = MiniAnalyticsPeriod.MONTH,
            todayIso = todayIso,
            isPrivacyMode = isPrivacyMode
        )

        val budgetUsagePct = if (budgetState.isBudgetConfigured && budgetState.monthlyBudgetLimit > 0.0) {
            ((budgetState.monthSpent / budgetState.monthlyBudgetLimit) * 100.0).roundToInt()
        } else null

        val avgGoalPct = if (goals.isNotEmpty()) {
            goals.map { g ->
                val t = g.targetAmount.coerceAtLeast(1.0)
                ((g.currentAmount / t) * 100.0).coerceIn(0.0, 100.0)
            }.average().roundToInt()
        } else null

        val trendPct = monthAnalytics.expenseDeltaPercent

        // Score calculation (0..100)
        var score = 72
        if (budgetUsagePct != null) {
            score += when {
                budgetUsagePct > 110 -> -42
                budgetUsagePct > 100 -> -30
                budgetUsagePct in 85..100 -> -14
                budgetUsagePct in 50..84 -> +8
                else -> +18
            }
        } else if (budgetState.monthIncome > 0.0) {
            val expToInc = (budgetState.monthSpent / budgetState.monthIncome) * 100.0
            score += when {
                expToInc > 100.0 -> -32
                expToInc > 85.0 -> -12
                else -> +14
            }
        }

        if (trendPct != null) {
            score += when {
                trendPct >= 30 -> -15
                trendPct >= 15 -> -8
                trendPct <= -10 -> +10
                else -> 0
            }
        }

        if (avgGoalPct != null) {
            score += when {
                avgGoalPct >= 75 -> +10
                avgGoalPct >= 35 -> +5
                else -> 0
            }
        }

        val finalScore = score.coerceIn(5, 99)

        val mood = when {
            budgetState.isExceeded || finalScore < 38 -> ZhabometerMood.ALARMED
            finalScore < 58 || (trendPct != null && trendPct >= 25) -> ZhabometerMood.SKEPTICAL
            finalScore >= 84 -> ZhabometerMood.PROSPEROUS
            else -> ZhabometerMood.CALM
        }

        val tip = when (mood) {
            ZhabometerMood.PROSPEROUS -> {
                if (avgGoalPct != null && avgGoalPct < 100) {
                    "Бюджет в профите! Самое время закинуть свободный остаток в цель (сейчас $avgGoalPct%)."
                } else {
                    "Образцовая дисциплина! Доходы опережают траты, кувшинки цветут."
                }
            }
            ZhabometerMood.CALM -> {
                if (budgetUsagePct != null) {
                    "Израсходовано $budgetUsagePct% бюджета — идёшь ровно по графику без лишних импульсов."
                } else {
                    "Финансовый пульс ровный. Продолжай фиксировать чеки голосом или в 1 тап."
                }
            }
            ZhabometerMood.SKEPTICAL -> {
                if (trendPct != null && trendPct > 0) {
                    "Траты выросли на +$trendPct% к прошлому месяцу. Проверь топ категорий у Давида!"
                } else {
                    "Лимит уже близко (${budgetUsagePct ?: 85}%). Отложи необязательные покупки на пару дней."
                }
            }
            ZhabometerMood.ALARMED -> {
                val overStr = formatCompactCurrency(budgetState.overBudgetAmount, isPrivacyMode)
                if (budgetState.overBudgetAmount > 0) {
                    "Перерасход $overStr! Включаем режим анти-шопинга до конца месяца."
                } else {
                    "Расходы превысили доходы! Нажми, чтобы разобрать утечки бюджета с Давидом."
                }
            }
            ZhabometerMood.ZEN -> "Тишина в кошельке. Запиши операцию, чтобы разбудить жабий радар!"
        }

        return ZhabometerWidgetModel(
            score = finalScore,
            mood = mood,
            headline = "${mood.titleRu} • $finalScore/100",
            tip = tip,
            budgetUsagePercent = budgetUsagePct,
            goalProgressPercent = avgGoalPct,
            expenseTrendPercent = trendPct,
            isPrivacyMode = isPrivacyMode
        )
    }

    private fun parseIsoToCalendar(dateIso: String): Calendar {
        val cal = Calendar.getInstance()
        try {
            val parts = dateIso.split("-")
            if (parts.size == 3) {
                cal.set(Calendar.YEAR, parts[0].toInt())
                cal.set(Calendar.MONTH, (parts[1].toInt() - 1).coerceIn(0, 11))
                cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt().coerceAtLeast(1))
            }
        } catch (_: Exception) {
        }
        return cal
    }
}
