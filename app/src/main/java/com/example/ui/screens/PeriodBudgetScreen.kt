package com.example.ui.screens

import com.example.ui.components.dialogs.*

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.ReportDetailsDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.dialogs.CategoryLimitsDialog
import com.example.ui.components.SwipeDirection
import com.example.ui.components.SwipeToRevealBox
import com.example.ui.components.IncomeExpenseSummaryDialog
import com.example.ui.components.dialogs.AllTransactionsDialog
import com.example.ui.components.DatePickerField
import com.example.ui.components.MarkdownFormattedText
import com.example.ui.components.formatFullCurrency
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700

import com.example.ui.components.RollingCurrencyText
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import com.example.ui.viewmodel.PeriodType
import com.example.ui.viewmodel.BudgetViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning

val MonthsRu = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

@Composable
fun PeriodBudgetScreen(
    periodType: PeriodType,
    selectedDateDay: String,
    selectedMonthIdx: Int,
    selectedYear: Int = 2026,
    allPeriodStart: String,
    allPeriodEnd: String,
    filteredTransactions: List<TransactionEntity>,
    allTransactions: List<TransactionEntity> = emptyList(),
    activeSubTab: String,
    expandedExpense: Boolean,
    expandedIncome: Boolean,
    aiAuditResult: String?,
    aiAuditLoading: Boolean,
    savedAiAudit: com.example.data.db.AiAuditEntity? = null,
    isAppLocked: Boolean = false,
    viewModel: BudgetViewModel? = null,
    onSetPeriodType: (PeriodType) -> Unit,

    onChangeSelectedDay: (String) -> Unit,
    onChangeSelectedMonthIdx: (Int) -> Unit,
    onChangeSelectedAnnualYear: (Int) -> Unit = {},
    onChangeAllPeriodStart: (String) -> Unit,
    onChangeAllPeriodEnd: (String) -> Unit,
    onChangeActiveSubTab: (String) -> Unit,
    onToggleExpandExpense: () -> Unit,
    onToggleExpandIncome: () -> Unit,
    onRequestAiAudit: () -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onEditTransaction: ((TransactionEntity) -> Unit)? = null
) {
    // Исключаем дочерние позиции чека из расчетов и отображения
    val mainFilteredTransactions = remember(filteredTransactions) {
        filteredTransactions.filter { it.parentId == null }
    }

    val mainAllTransactions = remember(allTransactions) {
        allTransactions.filter { it.parentId == null }
    }

    // Состояние для открытия диалога просмотра состава чека
    var selectedReceiptTransaction by remember { mutableStateOf<TransactionEntity?>(null) }

    // Monthly Calculations
    val monthStart = remember(selectedMonthIdx, selectedYear) {
        val monthFormatted = String.format(Locale.US, "%02d", selectedMonthIdx + 1)
        "$selectedYear-$monthFormatted-01"
    }

    val monthEnd = remember(selectedMonthIdx, selectedYear) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonthIdx)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    val monthTransactions = remember(mainAllTransactions, monthStart, monthEnd) {
        mainAllTransactions.filter { it.date >= monthStart && it.date <= monthEnd }
    }

    val monthIncomes = remember(monthTransactions) { monthTransactions.filter { it.type == "income" } }
    val monthExpenses = remember(monthTransactions) { monthTransactions.filter { it.type == "expense" } }

    val monthTotalIncome = remember(monthIncomes) { monthIncomes.sumOf { it.amount } }
    val monthTotalExpense = remember(monthExpenses) { monthExpenses.sumOf { it.amount } }
    val monthNetBalance = monthTotalIncome - monthTotalExpense

    val previousCarryover = remember(mainAllTransactions, monthStart) {
        val prevIncomes = mainAllTransactions
            .filter { it.type == "income" && it.date < monthStart }
            .sumOf { it.amount }
        val prevExpenses = mainAllTransactions
            .filter { it.type == "expense" && it.date < monthStart }
            .sumOf { it.amount }

        prevIncomes - prevExpenses
    }

    val monthTotalAccumulatedBalance = previousCarryover + monthNetBalance
    val monthSavingsRate = if (monthTotalIncome > 0) Math.max(0, Math.round((monthNetBalance / monthTotalIncome) * 100)) else 0

    val incomes = remember(mainFilteredTransactions) { mainFilteredTransactions.filter { it.type == "income" } }
    val expenses = remember(mainFilteredTransactions) { mainFilteredTransactions.filter { it.type == "expense" } }

    val categoryExpenseTotals = remember(expenses) {
        expenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    var showCategoryLimitsDialog by remember { mutableStateOf(false) }
    var showAllTransactionsDialog by remember { mutableStateOf(false) }
    var initialAllTransactionsFilter by remember { mutableStateOf("all") }

    val categoriesListState = viewModel?.categories?.collectAsStateWithLifecycle()
    val categoriesList = categoriesListState?.value ?: remember { emptyList() }

    val pagerState = rememberPagerState(initialPage = if (activeSubTab == "expense") 0 else 1, pageCount = { 2 })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val targetSubTab = if (page == 0) "expense" else "income"
            if (targetSubTab != activeSubTab) {
                onChangeActiveSubTab(targetSubTab)
            }
        }
    }

    LaunchedEffect(activeSubTab) {
        val targetPage = if (activeSubTab == "expense") 0 else 1
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                com.example.ui.components.SwipeToRevealController.requestCollapseAll()
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- MAIN NEON BALANCE CARD ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Slate900)
                .border(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(
                            Emerald400.copy(alpha = 0.4f),
                            Indigo500.copy(alpha = 0.4f),
                            Rose500.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ТЕКУЩИЙ БАЛАНС",
                        color = Slate400,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        shape = CircleShape,
                        color = Emerald400.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Норма ${monthSavingsRate}%",
                                color = Emerald400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatFullCurrency(monthTotalAccumulatedBalance),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                initialAllTransactionsFilter = "income"
                                showAllTransactionsDialog = true
                            }
                            .background(Emerald400.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .border(1.dp, Emerald400.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    ambientColor = Emerald400,
                                    spotColor = Emerald400
                                )
                                .clip(CircleShape)
                                .background(Emerald400.copy(alpha = 0.12f))
                                .border(1.dp, Emerald400.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.SouthWest,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Доходы", color = Slate400, fontSize = 11.sp)
                            Text(
                                text = "+\u00A0${formatFullCurrency(monthTotalIncome)}",
                                color = Emerald400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                initialAllTransactionsFilter = "expense"
                                showAllTransactionsDialog = true
                            }
                            .background(Rose500.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .border(1.dp, Rose500.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    ambientColor = Rose500,
                                    spotColor = Rose500
                                )
                                .clip(CircleShape)
                                .background(Rose500.copy(alpha = 0.12f))
                                .border(1.dp, Rose500.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.NorthEast,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Расходы", color = Slate400, fontSize = 11.sp)
                            Text(
                                text = "-\u00A0${formatFullCurrency(monthTotalExpense)}",
                                color = Rose500,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        

        com.example.ui.components.charts.ExpenseDynamicsAreaChartCard(
            transactions = monthTransactions,
            title = "ДИНАМИКА РАСХОДОВ",
            onClick = null
        )

        // --- CATEGORIES GRID ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "КАТЕГОРИИ",
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Все категории",
                    color = Indigo500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showCategoryLimitsDialog = true }
                )
            }

            val defaultCategories = listOf(
                Triple("Гейминг и Аниме", 22000.0, Emerald400 to Icons.Default.SportsEsports),
                Triple("Бары и Тусовки", 4800.0, Indigo500 to Icons.Default.LocalBar),
                Triple("Такси и Транспорт", 1420.0, Rose500 to Icons.Default.DirectionsCar),
                Triple("Продукты и Еда", 1150.0, Emerald400 to Icons.Default.Restaurant),
                Triple("Форс-мажоры", 300.0, Indigo500 to Icons.Default.Warning)
            )

            val displayCategories = remember(categoryExpenseTotals, categoriesList) {
                if (categoryExpenseTotals.isNotEmpty()) {
                    val colors = listOf(Emerald400, Indigo500, Rose500, Color(0xFFF59E0B), Color(0xFF06B6D4))
                    val realSorted = categoryExpenseTotals.entries.sortedByDescending { it.value }.take(5)
                    val realItems = realSorted.mapIndexed { idx, entry ->
                        val (col, ic) = when {
                            entry.key.contains("игра", true) || entry.key.contains("гейм", true) || entry.key.contains("аниме", true) -> Emerald400 to Icons.Default.SportsEsports
                            entry.key.contains("бар", true) || entry.key.contains("тусов", true) || entry.key.contains("алко", true) -> Indigo500 to Icons.Default.LocalBar
                            entry.key.contains("такси", true) || entry.key.contains("транс", true) || entry.key.contains("авто", true) -> Rose500 to Icons.Default.DirectionsCar
                            entry.key.contains("продукт", true) || entry.key.contains("еда", true) || entry.key.contains("кафе", true) -> Emerald400 to Icons.Default.Restaurant
                            entry.key.contains("форс", true) || entry.key.contains("авар", true) -> Indigo500 to Icons.Default.Warning
                            else -> colors[idx % colors.size] to Icons.Default.ShoppingBag
                        }
                        Triple(entry.key, entry.value, col to ic)
                    }

                    if (realItems.size < 5) {
                        val remainingDb = categoriesList
                            .filter { it.type == "expense" && realItems.none { item -> item.first.equals(it.name, true) } }
                            .mapIndexed { index, cat ->
                                val (col, ic) = when {
                                    cat.name.contains("игра", true) || cat.name.contains("гейм", true) || cat.name.contains("аниме", true) -> Emerald400 to Icons.Default.SportsEsports
                                    cat.name.contains("бар", true) || cat.name.contains("тусов", true) || cat.name.contains("алко", true) -> Indigo500 to Icons.Default.LocalBar
                                    cat.name.contains("такси", true) || cat.name.contains("транс", true) || cat.name.contains("авто", true) -> Rose500 to Icons.Default.DirectionsCar
                                    cat.name.contains("продукт", true) || cat.name.contains("еда", true) || cat.name.contains("кафе", true) -> Emerald400 to Icons.Default.Restaurant
                                    cat.name.contains("форс", true) || cat.name.contains("авар", true) -> Indigo500 to Icons.Default.Warning
                                    else -> colors[(realItems.size + index) % colors.size] to Icons.Default.ShoppingBag
                                }
                                Triple(cat.name, 0.0, col to ic)
                            }
                        (realItems + remainingDb).take(5)
                    } else {
                        realItems
                    }
                } else {
                    val colors = listOf(Emerald400, Indigo500, Rose500, Color(0xFFF59E0B), Color(0xFF06B6D4))
                    val expenseDbCats = categoriesList.filter { it.type == "expense" }
                    if (expenseDbCats.isNotEmpty()) {
                        expenseDbCats.mapIndexed { idx, cat ->
                            val (col, ic) = when {
                                cat.name.contains("игра", true) || cat.name.contains("гейм", true) || cat.name.contains("аниме", true) -> Emerald400 to Icons.Default.SportsEsports
                                cat.name.contains("бар", true) || cat.name.contains("тусов", true) || cat.name.contains("алко", true) -> Indigo500 to Icons.Default.LocalBar
                                cat.name.contains("такси", true) || cat.name.contains("транс", true) || cat.name.contains("авто", true) -> Rose500 to Icons.Default.DirectionsCar
                                cat.name.contains("продукт", true) || cat.name.contains("еда", true) || cat.name.contains("кафе", true) -> Emerald400 to Icons.Default.Restaurant
                                cat.name.contains("форс", true) || cat.name.contains("авар", true) -> Indigo500 to Icons.Default.Warning
                                else -> colors[idx % colors.size] to Icons.Default.ShoppingBag
                            }
                            Triple(cat.name, 0.0, col to ic)
                        }.take(5)
                    } else {
                        defaultCategories.map { Triple(it.first, 0.0, it.third) }
                    }
                }
            }

            val maxCategoryVal = remember(displayCategories) {
                displayCategories.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                displayCategories.take(3).forEach { (name, amount, colorAndIcon) ->
                    val (color, icon) = colorAndIcon
                    val ratio = (amount / maxCategoryVal).toFloat().coerceIn(0.12f, 1f)

                    Card(
                        onClick = { showCategoryLimitsDialog = true },
                        colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(118.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color.copy(alpha = 0.12f))
                                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = name,
                                color = Slate300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 12.sp,
                                modifier = Modifier.height(26.dp)
                            )

                            Text(
                                text = formatFullCurrency(amount),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF020617))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = ratio)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                displayCategories.drop(3).take(2).forEach { (name, amount, colorAndIcon) ->
                    val (color, icon) = colorAndIcon
                    val ratio = (amount / maxCategoryVal).toFloat().coerceIn(0.12f, 1f)

                    Card(
                        onClick = { showCategoryLimitsDialog = true },
                        colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(118.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color.copy(alpha = 0.12f))
                                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = name,
                                color = Slate300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 12.sp,
                                modifier = Modifier.height(26.dp)
                            )

                            Text(
                                text = formatFullCurrency(amount),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF020617))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = ratio)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                }

                Card(
                    onClick = { showCategoryLimitsDialog = true },
                    colors = CardDefaults.cardColors(containerColor = Indigo500.copy(alpha = 0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(118.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Indigo500),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Добавить",
                            color = Indigo500,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (showCategoryLimitsDialog && !isAppLocked) {
            CategoryLimitsDialog(
                categories = categoriesList,
                transactions = monthExpenses,
                onUpdateLimit = { catName, limit ->
                    viewModel?.updateCategoryLimit(catName, "expense", limit)
                },
                onDismiss = { showCategoryLimitsDialog = false }
            )
        }

        if (showAllTransactionsDialog && !isAppLocked) {
            val currentPeriodInitialDate = if (periodType == PeriodType.DAY || periodType == PeriodType.WEEK) selectedDateDay else monthStart
            AllTransactionsDialog(
                transactions = if (mainAllTransactions.isNotEmpty()) mainAllTransactions else mainFilteredTransactions,
                onDeleteTransaction = onDeleteTransaction,
                onEditTransaction = onEditTransaction,
                initialFilterType = initialAllTransactionsFilter,
                initialDate = currentPeriodInitialDate,
                onDismiss = { showAllTransactionsDialog = false }
            )
        }

        // --- RECENT TRANSACTIONS SECTION ---
        val recentTxList = remember(mainFilteredTransactions) {
            mainFilteredTransactions.take(10)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.6f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ПОСЛЕДНИЕ ОПЕРАЦИИ",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Всего ${mainFilteredTransactions.size} транзакций",
                            color = Slate500,
                            fontSize = 10.sp
                        )
                    }

                    if (mainFilteredTransactions.isNotEmpty()) {
                        Text(
                            text = "Все ›",
                            color = Indigo500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    initialAllTransactionsFilter = "all"
                                    showAllTransactionsDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (recentTxList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет операций за выбранный период",
                            color = Slate500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentTxList.forEach { tx ->
                            // Находим дочерние товары чека, если они есть
                            val receiptItems = remember(allTransactions, tx.id) {
                                allTransactions.filter { it.parentId == tx.id }
                            }

                            TransactionRowItem(
                                item = tx,
                                onDelete = onDeleteTransaction,
                                onClick = {
                                    if (receiptItems.isNotEmpty()) {
                                        selectedReceiptTransaction = tx
                                    } else if (onEditTransaction != null) {
                                        onEditTransaction(tx)
                                    }
                                },
                                canDelete = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

        // Всплывающее окно состава чека при нажатии на карточку чека
    selectedReceiptTransaction?.let { parentTx ->
        val childItems = remember(allTransactions, parentTx.id) {
            allTransactions.filter { it.parentId == parentTx.id }.map { childTx ->
                com.example.data.repository.ParsedReceiptItem(
                    title = childTx.subcategory.ifBlank { childTx.category },
                    amount = childTx.amount
                )
            }
        }
        ReceiptDetailsDialog(
            parentTransaction = parentTx,
            receiptItems = childItems,
            onDismiss = { selectedReceiptTransaction = null }
        )
    }
}

@Composable
fun PeriodButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = Indigo500,
                        spotColor = Indigo500
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Indigo500 else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Indigo500.copy(alpha = 0.8f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else Slate400,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

fun getCategoryColorAndIcon(category: String, subcategory: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.vector.ImageVector> {
    val text = "$category $subcategory".lowercase()
    return when {
        text.contains("кредит") || text.contains("займ") || text.contains("ипотек") || text.contains("долг") || text.contains("банк") -> Pair(Rose500, Icons.Default.AccountBalance)
        text.contains("аптек") || text.contains("лекарст") || text.contains("здоровье") || text.contains("больниц") || text.contains("врач") || text.contains("медицин") -> Pair(Rose500, Icons.Default.MedicalServices)
        text.contains("сбережен") || text.contains("копилк") || text.contains("накоплен") || text.contains("вклад") || text.contains("инвест") || text.contains("фонд") || text.contains("цель") -> Pair(Indigo500, Icons.Default.Savings)
        text.contains("янндекс") || text.contains("yandex") -> Pair(androidx.compose.ui.graphics.Color(0xFFFC3F1D), Icons.Default.ShoppingBag)
        text.contains("сбер") || text.contains("перевод") || text.contains("тинькофф") || text.contains("карта") || text.contains("спб") -> Pair(Emerald400, Icons.Default.Refresh)
        text.contains("продукт") || text.contains("супермаркет") || text.contains("еда") || text.contains("магнит") || text.contains("пятерочк") || text.contains("ашан") || text.contains("магазин") -> Pair(androidx.compose.ui.graphics.Color(0xFFF59E0B), Icons.Default.ShoppingBag)
        text.contains("кафе") || text.contains("ресторан") || text.contains("фастфуд") || text.contains("доставк") || text.contains("кофе") || text.contains("столовая") -> Pair(androidx.compose.ui.graphics.Color(0xFFEC4899), Icons.Default.ShoppingBag)
        text.contains("транспорт") || text.contains("такси") || text.contains("авто") || text.contains("бензин") || text.contains("заправк") || text.contains("метро") || text.contains("автобус") -> Pair(Indigo500, Icons.Default.DirectionsCar)
        text.contains("развлечени") || text.contains("кино") || text.contains("игры") || text.contains("подписк") || text.contains("музык") || text.contains("театр") || text.contains("спорт") -> Pair(androidx.compose.ui.graphics.Color(0xFFA855F7), Icons.Default.Theaters)
        text.contains("жилье") || text.contains("коммунал") || text.contains("дом") || text.contains("жкх") || text.contains("аренд") || text.contains("квартир") -> Pair(androidx.compose.ui.graphics.Color(0xFF06B6D4), Icons.Default.Home)
        text.contains("связь") || text.contains("интернет") || text.contains("телефон") || text.contains("мобильн") || text.contains("техник") -> Pair(androidx.compose.ui.graphics.Color(0xFF3B82F6), Icons.Default.Call)
        text.contains("зарплат") || text.contains("доход") || text.contains("преми") || text.contains("аванс") || text.contains("кэшбэк") -> Pair(Emerald400, Icons.Default.Payments)
        text.contains("одежд") || text.contains("обувь") || text.contains("гардероб") || text.contains("сумка") || text.contains("красот") || text.contains("салон") -> Pair(androidx.compose.ui.graphics.Color(0xFFEC4899), Icons.Default.Checkroom)
        text.contains("подарок") || text.contains("подарк") || text.contains("праздник") || text.contains("цветы") -> Pair(Rose500, Icons.Default.CardGiftcard)
        text.contains("книг") || text.contains("литератур") || text.contains("чтени") || text.contains("литрес") -> Pair(androidx.compose.ui.graphics.Color(0xFF8B5CF6), Icons.Default.List)
        text.contains("обучени") || text.contains("образовани") || text.contains("курсы") || text.contains("школа") -> Pair(androidx.compose.ui.graphics.Color(0xFF60A5FA), Icons.Default.School)
        else -> Pair(Slate400, Icons.Default.List)
    }
}

@Composable
fun TransactionRowItem(
    item: TransactionEntity,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    canDelete: Boolean = true
) {
    val numberFormat = remember {
        val symbols = java.text.DecimalFormatSymbols(Locale("ru", "RU")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        java.text.DecimalFormat("#,##0.##", symbols).apply {
            isGroupingUsed = true
        }
    }
    val isExpense = item.type == "expense"
    val prefix = if (isExpense) "-" else "+"
    val (catColor, catIcon) = getCategoryColorAndIcon(item.category, item.subcategory)
    val hasSubcategory = item.subcategory.isNotBlank() && !item.subcategory.equals(item.category, ignoreCase = true)
    val topTitle = if (hasSubcategory) item.subcategory else item.category
    val bottomTitle = if (hasSubcategory) item.category else ""

    val content = @Composable {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkBg.copy(alpha = 0.6f))
                .border(androidx.compose.foundation.BorderStroke(1.dp, Slate800.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.1f))
                        .border(0.5.dp, catColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = item.category,
                        tint = catColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (hasSubcategory) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (bottomTitle.isNotEmpty()) {
                        Text(
                            text = bottomTitle,
                            color = Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$prefix${numberFormat.format(item.amount)} ₽",
                color = if (isExpense) Color.White else Emerald400,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (canDelete) {
        SwipeToRevealBox(
            swipeDirection = SwipeDirection.Both,
            onDelete = { onDelete(item.id) },
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
        ) {
            content()
        }
    } else {
        content()
    }
}
