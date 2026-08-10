package com.example.ui.screens

import com.example.ui.components.dialogs.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.charts.CategoryDoughnutChart
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.ChartColors
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.theme.Slate700
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate300
import com.example.ui.viewmodel.PeriodType
import com.example.ui.viewmodel.BudgetViewModel

private fun formatDayHeaderLabel(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr

        val todayCal = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply { time = date }

        val isSameYear = todayCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)
        val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)

        if (isSameYear && todayDayOfYear == targetDayOfYear) {
            "Сегодня"
        } else if (isSameYear && todayDayOfYear - targetDayOfYear == 1) {
            "Вчера"
        } else {
            val pattern = if (isSameYear) "d MMMM" else "d MMMM yyyy"
            val rawStr = SimpleDateFormat(pattern, Locale("ru", "RU")).format(date)
            rawStr.split(" ").mapIndexed { idx, word ->
                if (idx == 1) word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
                else word
            }.joinToString(" ")
        }
    } catch (e: Exception) {
        dateStr
    }
}

private val Indigo950Bg = Color(0xFF1E1B4B)
private val Purple950Bg = Color(0xFF2E1065)

@Composable
fun ExpenseSharesScreen(
    filteredTransactions: List<TransactionEntity>,
    allTransactions: List<TransactionEntity> = emptyList(),
    onDeleteTransaction: ((String) -> Unit)? = null,
    onEditTransaction: ((TransactionEntity) -> Unit)? = null,
    selectedDateDay: String = "",
    onDateSelected: ((String) -> Unit)? = null,
    aiAuditResult: String? = null,
    aiAuditLoading: Boolean = false,
    savedAiAudit: String? = null,
    onRequestAiAudit: (() -> Unit)? = null,
    periodType: PeriodType = PeriodType.MONTH,
    onSetPeriodType: ((PeriodType) -> Unit)? = null,
    selectedMonthIdx: Int = 0,
    onChangeSelectedMonthIdx: ((Int) -> Unit)? = null,
    allPeriodStart: String = "",
    allPeriodEnd: String = "",
    onChangeAllPeriodStart: ((String) -> Unit)? = null,
    onChangeAllPeriodEnd: ((String) -> Unit)? = null,
    monthsRu: List<String> = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"),
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 1 })
    val coroutineScope = rememberCoroutineScope()

    var sortOption by remember { mutableStateOf("date") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedDetailGroup by remember { mutableStateOf<Pair<String, List<TransactionEntity>>?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var hideStatusBadge by remember { mutableStateOf(false) }
    var showMonthWheelPicker by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    val numberFormat = remember {
        val symbols = java.text.DecimalFormatSymbols(Locale("ru", "RU")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        java.text.DecimalFormat("#,##0.##", symbols).apply {
            isGroupingUsed = true
        }
    }

    val showScrollToTop by remember { derivedStateOf { scrollState.value > 500 } }

    val activeAuditText = aiAuditResult ?: savedAiAudit
    val isReportReady = !activeAuditText.isNullOrBlank()

    LaunchedEffect(aiAuditLoading) {
        if (!aiAuditLoading && isReportReady) {
            hideStatusBadge = false
        }
    }

    LaunchedEffect(pagerState.targetPage) {
        selectedCategoryFilter = null
    }

    val selectedTargetPage = pagerState.targetPage
    val expenseTabBg by animateColorAsState(
        targetValue = if (selectedTargetPage == 0) Rose500 else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "exp_tab_bg"
    )
    val incomeTabBg by animateColorAsState(
        targetValue = if (selectedTargetPage == 1) Emerald400 else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "inc_tab_bg"
    )
    val expenseTextColor by animateColorAsState(
        targetValue = if (selectedTargetPage == 0) Color.White else Slate400,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "exp_text_color"
    )
    val incomeTextColor by animateColorAsState(
        targetValue = if (selectedTargetPage == 1) DarkBg else Slate400,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "inc_text_color"
    )

    val expenses = remember(filteredTransactions) { filteredTransactions.filter { it.type == "expense" } }
    val incomes = remember(filteredTransactions) { filteredTransactions.filter { it.type == "income" } }

    val totalExpense = remember(expenses) { expenses.sumOf { it.amount } }
    val totalIncome = remember(incomes) { incomes.sumOf { it.amount } }

    val previousTransactions = remember(allTransactions, periodType, selectedMonthIdx, selectedDateDay) {
        if (allTransactions.isEmpty()) return@remember emptyList()
        when (periodType) {
            PeriodType.MONTH -> {
                val prevMonthIdx = if (selectedMonthIdx > 0) selectedMonthIdx - 1 else 11
                val cal = Calendar.getInstance()
                val currentYear = cal.get(Calendar.YEAR)
                val prevYear = if (selectedMonthIdx > 0) currentYear else currentYear - 1
                val prevPrefix = String.format(Locale.US, "%04d-%02d", prevYear, prevMonthIdx + 1)
                allTransactions.filter { it.date.startsWith(prevPrefix) }
            }
            else -> emptyList()
        }
    }

    val prevIncome = remember(previousTransactions) { previousTransactions.filter { it.type == "income" }.sumOf { it.amount } }
    val prevExpense = remember(previousTransactions) { previousTransactions.filter { it.type == "expense" }.sumOf { it.amount } }

    val rawExpenseTotals = remember(expenses) {
        expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val rawIncomeTotals = remember(incomes) {
        incomes.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val expenseCategoryTotals = rawExpenseTotals
    val incomeCategoryTotals = rawIncomeTotals

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- PERIOD SELECTOR INSIDE EXPENSE SHARES ---
            if (onSetPeriodType != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        val barWidth = maxWidth
                        val tabCount = 3
                        val tabWidth = barWidth / tabCount

                        val targetIndex = when (periodType) {
                            PeriodType.WEEK -> 0
                            PeriodType.MONTH -> 1
                            else -> 2
                        }

                        val animatedFraction by animateFloatAsState(
                            targetValue = targetIndex.toFloat(),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "shares_period_fraction"
                        )

                        // Moving selection indicator pill
                        Box(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .offset(x = tabWidth * animatedFraction)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Indigo500,
                                    spotColor = Indigo500
                                )
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Indigo500, Indigo500.copy(alpha = 0.8f))
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Indigo500.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                        )

                        // Clickable options
                        Row(modifier = Modifier.fillMaxSize()) {
                            listOf(
                                Triple("Неделя", PeriodType.WEEK, 0),
                                Triple("Месяц", PeriodType.MONTH, 1),
                                Triple("Период", PeriodType.ALL, 2)
                            ).forEach { (label, type, index) ->
                                val isSelected = targetIndex == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSetPeriodType(type) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else Slate400,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (periodType) {
                            PeriodType.DAY, PeriodType.MONTH -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Месяц:",
                                        color = Slate400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Slate900)
                                                .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                                                .clickable { showMonthWheelPicker = true }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = monthsRu.getOrElse(selectedMonthIdx) { "Январь" },
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (showMonthWheelPicker && onChangeSelectedMonthIdx != null) {
                                            com.example.ui.components.MonthWheelPickerDialog(
                                                initialMonthIdx = selectedMonthIdx,
                                                months = monthsRu,
                                                onDismiss = { showMonthWheelPicker = false },
                                                onConfirm = { newMonthIdx ->
                                                    onChangeSelectedMonthIdx(newMonthIdx)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            PeriodType.WEEK -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Неделя:", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Slate900)
                                            .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text("Последние 7 дней", color = Emerald400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            PeriodType.ALL -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Период:",
                                        color = Slate400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Box {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Slate900)
                                                .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                                                .clickable { showDateRangePicker = true }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "с $allPeriodStart по $allPeriodEnd 📅",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (showDateRangePicker && onChangeAllPeriodStart != null && onChangeAllPeriodEnd != null) {
                                            com.example.ui.components.DateRangePickerDialog(
                                                initialStart = allPeriodStart,
                                                initialEnd = allPeriodEnd,
                                                onDismiss = { showDateRangePicker = false },
                                                onConfirm = { start, end ->
                                                    onChangeAllPeriodStart(start)
                                                    onChangeAllPeriodEnd(end)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    )
            ) { page ->
                val isExpense = page == 0
                val categoryTotals = if (isExpense) expenseCategoryTotals else incomeCategoryTotals
                val grandTotal = if (isExpense) totalExpense else totalIncome


                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {


                    // --- DETAILED CATEGORY BREAKDOWN LIST ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (isExpense) "Детализация расходов" else "Детализация доходов",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Нажмите для фильтрации",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (categoryTotals.isEmpty() || grandTotal <= 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isExpense) "За выбранный период расходов нет" else "За выбранный период доходов нет",
                                        color = Slate400,
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    categoryTotals.forEachIndexed { index, (catName, sumAmount) ->
                                        val percent = if (grandTotal > 0) (sumAmount / grandTotal) else 0.0
                                        val color = ChartColors[index % ChartColors.size]
                                        val isSelected = selectedCategoryFilter == catName

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) Indigo500.copy(alpha = 0.2f) else Color.Transparent)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Indigo500 else Color.Transparent,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    selectedCategoryFilter = if (isSelected) null else catName
                                                }
                                                .padding(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(12.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = catName,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "${numberFormat.format(sumAmount)} ₽",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "${(percent * 100).toInt()}%",
                                                        color = Slate400,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            val animatedCategoryProgress by animateFloatAsState(
                                                targetValue = percent.toFloat().coerceIn(0f, 1f),
                                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                                label = "cat_bar_$index"
                                            )

                                            LinearProgressIndicator(
                                                progress = { animatedCategoryProgress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(CircleShape),
                                                color = color,
                                                trackColor = Slate800,
                                                strokeCap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- SORTING BAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Сортировка операций:",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val isAmountActive = sortOption == "desc" || sortOption == "asc"
                            val arrowRotation by animateFloatAsState(
                                targetValue = if (sortOption == "asc") 180f else 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "arrowRotation"
                            )
                            val amountBgColor by animateColorAsState(
                                targetValue = if (isAmountActive) Indigo500 else Slate900,
                                label = "amountBg"
                            )
                            val amountBorderColor by animateColorAsState(
                                targetValue = if (isAmountActive) Indigo500 else Slate800,
                                label = "amountBorder"
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(amountBgColor)
                                    .border(1.dp, amountBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        sortOption = when (sortOption) {
                                            "desc" -> "asc"
                                            "asc" -> "date"
                                            else -> "desc"
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "↓",
                                        color = if (isAmountActive) Color.White else Slate400,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.graphicsLayer {
                                            rotationZ = arrowRotation
                                        }
                                    )
                                    Text(
                                        text = "Сумма",
                                        color = if (isAmountActive) Color.White else Slate400,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val isNameActive = sortOption == "name" || sortOption == "name_desc"
                            val nameText = when (sortOption) {
                                "name" -> "A-Z"
                                "name_desc" -> "Z-A"
                                else -> "A-Z"
                            }
                            val nameBgColor by animateColorAsState(
                                targetValue = if (isNameActive) Indigo500 else Slate900,
                                label = "nameBg"
                            )
                            val nameBorderColor by animateColorAsState(
                                targetValue = if (isNameActive) Indigo500 else Slate800,
                                label = "nameBorder"
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(nameBgColor)
                                    .border(1.dp, nameBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        sortOption = when (sortOption) {
                                            "name" -> "name_desc"
                                            "name_desc" -> "date"
                                            else -> "name"
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                AnimatedContent(
                                    targetState = nameText,
                                    transitionSpec = {
                                        if (targetState == "Z-A") {
                                            (slideInVertically { height -> height } + fadeIn())
                                                .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                                        } else {
                                            (slideInVertically { height -> -height } + fadeIn())
                                                .togetherWith(slideOutVertically { height -> height } + fadeOut())
                                        }
                                    },
                                    label = "NameSortAnim"
                                ) { targetText ->
                                    Text(
                                        text = targetText,
                                        color = if (isNameActive) Color.White else Slate400,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Surface(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(0)
                    }
                },
                color = Slate800,
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.6f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Наверх",
                        tint = Indigo500,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Вверх",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        selectedDetailGroup?.let { (category, items) ->
            TransactionDetailsDialog(
                category = category,
                dateStr = items.firstOrNull()?.date ?: "",
                items = items,
                onDeleteItem = { idToDelete ->
                    onDeleteTransaction?.invoke(idToDelete)
                    val updatedItems = items.filter { it.id != idToDelete }
                    if (updatedItems.isEmpty()) {
                        selectedDetailGroup = null
                    } else {
                        selectedDetailGroup = Pair(category, updatedItems)
                    }
                },
                onEditItem = { item ->
                    onEditTransaction?.invoke(item)
                    selectedDetailGroup = null
                },
                onDismiss = { selectedDetailGroup = null }
            )
        }
    }
}




