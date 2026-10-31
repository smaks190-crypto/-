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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.Amber400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo400
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.SpaceGroteskFontFamily

import com.example.ui.components.RollingCurrencyText
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import com.example.ui.viewmodel.PeriodType
import com.example.ui.viewmodel.BudgetViewModel
import com.example.ui.viewmodel.BottomCapsuleModal
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
    onEditTransaction: ((TransactionEntity) -> Unit)? = null,
    onOpenRecentTransactionsScreen: (() -> Unit)? = null
) {
    val monthStart = remember(selectedMonthIdx, selectedYear) {
        val monthFormatted = String.format(Locale.US, "%02d", selectedMonthIdx + 1)
        "$selectedYear-$monthFormatted-01"
    }

    val daysInSelectedMonth = remember(selectedMonthIdx, selectedYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, selectedMonthIdx)
            set(Calendar.DAY_OF_MONTH, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val monthEnd = remember(selectedMonthIdx, selectedYear, daysInSelectedMonth) {
        val monthFormatted = String.format(Locale.US, "%02d", selectedMonthIdx + 1)
        val dayFormatted = String.format(Locale.US, "%02d", daysInSelectedMonth)
        "$selectedYear-$monthFormatted-$dayFormatted"
    }

    val monthTransactions = remember(allTransactions, monthStart, monthEnd) {
        allTransactions.filter { it.date >= monthStart && it.date <= monthEnd }
    }

    val monthIncomes = remember(monthTransactions) { monthTransactions.filter { it.type == "income" } }
    val monthExpenses = remember(monthTransactions) { monthTransactions.filter { it.type == "expense" } }

    val monthTotalIncome = remember(monthIncomes) { monthIncomes.sumOf { it.amount } }
    val monthTotalExpense = remember(monthExpenses) { monthExpenses.sumOf { it.amount } }
    val monthNetBalance = monthTotalIncome - monthTotalExpense

    // Previous month comparison (MoM %)
    val (prevMonthIncome, prevMonthExpense) = remember(allTransactions, selectedMonthIdx, selectedYear) {
        val prevIdx = if (selectedMonthIdx > 0) selectedMonthIdx - 1 else 11
        val prevYear = if (selectedMonthIdx > 0) selectedYear else selectedYear - 1
        val prevPrefix = String.format(Locale.US, "%04d-%02d", prevYear, prevIdx + 1)
        val prevTxs = allTransactions.filter { it.date.startsWith(prevPrefix) }
        val pInc = prevTxs.filter { it.type == "income" }.sumOf { it.amount }
        val pExp = prevTxs.filter { it.type == "expense" }.sumOf { it.amount }
        Pair(pInc, pExp)
    }

    val expenseMoMPercent = remember(monthTotalExpense, prevMonthExpense) {
        if (prevMonthExpense > 0) {
            Math.round(((monthTotalExpense - prevMonthExpense) / prevMonthExpense) * 100).toInt()
        } else null
    }

    val previousCarryover = remember(allTransactions, monthStart) {
        val prevIncomes = allTransactions
            .filter { it.type == "income" && it.date < monthStart }
            .sumOf { it.amount }
        val prevExpenses = allTransactions
            .filter { it.type == "expense" && it.date < monthStart }
            .sumOf { it.amount }

        prevIncomes - prevExpenses
    }

    val monthTotalAccumulatedBalance = previousCarryover + monthNetBalance
    val monthSavingsRate = if (monthTotalIncome > 0) Math.max(0, Math.round((monthNetBalance / monthTotalIncome) * 100)) else 0

    val expenses = remember(filteredTransactions) { filteredTransactions.filter { it.type == "expense" } }

    val categoryExpenseTotals = remember(expenses) {
        expenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    var showSharesDialog by remember { mutableStateOf(false) }
    var showCategoryLimitsDialog by remember { mutableStateOf(false) }
    var showIncomeExpenseSummaryDialog by remember { mutableStateOf(false) }
    var showAllTransactionsDialog by remember { mutableStateOf(false) }
    var initialAllTransactionsFilter by remember { mutableStateOf("all") }

    val categoriesListState = viewModel?.categories?.collectAsStateWithLifecycle()
    val categoriesList = categoriesListState?.value ?: remember { emptyList() }

    // Safe-to-Spend Daily Limit calculation
    val totalMonthlyLimit = remember(categoriesList, monthTotalIncome) {
        val sumLimits = categoriesList.filter { it.type == "expense" }.mapNotNull { it.monthlyLimit }.sum()
        if (sumLimits > 0) sumLimits else if (monthTotalIncome > 0) monthTotalIncome * 0.8 else 0.0
    }

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    val todaySpent = remember(monthExpenses, todayIso) {
        monthExpenses.filter { it.date == todayIso }.sumOf { it.amount }
    }

    val remainingDaysInMonth = remember(selectedMonthIdx, selectedYear, daysInSelectedMonth) {
        val now = Calendar.getInstance()
        val isCurrentMonth = now.get(Calendar.YEAR) == selectedYear && now.get(Calendar.MONTH) == selectedMonthIdx
        if (isCurrentMonth) {
            (daysInSelectedMonth - now.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
        } else {
            daysInSelectedMonth.coerceAtLeast(1)
        }
    }

    val safeDailyBudget = remember(totalMonthlyLimit, monthTotalExpense, todaySpent, remainingDaysInMonth) {
        val spentBeforeToday = (monthTotalExpense - todaySpent).coerceAtLeast(0.0)
        val remainingPool = (totalMonthlyLimit - spentBeforeToday).coerceAtLeast(0.0)
        remainingPool / remainingDaysInMonth
    }

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
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- OBSIDIAN NEO-BANK PERIOD SELECTOR & QUICK AUDIT BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            val newIdx = if (selectedMonthIdx > 0) selectedMonthIdx - 1 else 11
                            if (selectedMonthIdx == 0) onChangeSelectedAnnualYear(selectedYear - 1)
                            onSetPeriodType(PeriodType.MONTH)
                            onChangeSelectedMonthIdx(newIdx)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Предыдущий месяц",
                        tint = Slate300,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "${MonthsRu.getOrElse(selectedMonthIdx) { "Месяц" }} $selectedYear",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            val newIdx = if (selectedMonthIdx < 11) selectedMonthIdx + 1 else 0
                            if (selectedMonthIdx == 11) onChangeSelectedAnnualYear(selectedYear + 1)
                            onSetPeriodType(PeriodType.MONTH)
                            onChangeSelectedMonthIdx(newIdx)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Следующий месяц",
                        tint = Slate300,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Quick Limits & Analytics Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        if (viewModel != null) {
                            viewModel.openModal(BottomCapsuleModal.CategoryLimits)
                        } else {
                            showCategoryLimitsDialog = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = Emerald400,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Лимиты",
                    color = Slate200,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // --- HERO OBSIDIAN NEO-BANK BALANCE CARD ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF111827),
                            Color(0xFF0A0E17)
                        )
                    )
                )
                .drawBehind {
                    // Ambient Obsidian-Emerald Radial Aura in top-right
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Emerald400.copy(alpha = 0.14f),
                                Indigo500.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.15f),
                            radius = size.width * 0.65f
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.65f
                    )
                }
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            GlassHighlight,
                            GlassBorder,
                            Color.White.copy(alpha = 0.03f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ЧИСТЫЙ КАПИТАЛ",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (monthSavingsRate >= 15) Emerald400.copy(alpha = 0.12f) else Indigo500.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (monthSavingsRate >= 15) Emerald400.copy(alpha = 0.3f) else Indigo500.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = if (monthSavingsRate >= 15) Emerald400 else Indigo400,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Сбережения ${monthSavingsRate}%",
                                color = if (monthSavingsRate >= 15) Emerald400 else Indigo400,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Total Balance Amount in Space Grotesk (modern geometric fintech typography)
                Text(
                    text = formatFullCurrency(monthTotalAccumulatedBalance),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                // Safe-to-Spend Daily Limit Bar ("Можно потратить сегодня")
                if (safeDailyBudget > 0) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val safeRemainingToday = (safeDailyBudget - todaySpent).coerceAtLeast(0.0)
                    val isOverDaily = todaySpent > safeDailyBudget
                    val dailyProgress = (todaySpent / safeDailyBudget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    val statusColor = when {
                        isOverDaily -> Rose500
                        dailyProgress > 0.8f -> Amber400
                        else -> Emerald400
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isOverDaily) "Дневной лимит превышен" else "Можно потратить сегодня",
                                    color = Slate300,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (isOverDaily) {
                                        "Перерасход ${formatShortCurrency(todaySpent - safeDailyBudget)}"
                                    } else {
                                        "${formatShortCurrency(safeRemainingToday)} из ${formatShortCurrency(safeDailyBudget)}"
                                    },
                                    color = statusColor,
                                    fontSize = 12.sp,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = dailyProgress.coerceAtLeast(0.04f))
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Income & Expense Bento Sub-Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Income Glass Card
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                if (viewModel != null) {
                                    viewModel.openModal(BottomCapsuleModal.AllTransactions("income", monthStart))
                                } else {
                                    initialAllTransactionsFilter = "income"
                                    showAllTransactionsDialog = true
                                }
                            }
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Emerald400.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SouthWest,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Доходы",
                                color = Slate400,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "+${formatShortCurrency(monthTotalIncome)}",
                                color = Emerald400,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGroteskFontFamily,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Expense Glass Card with MoM % Delta
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                if (viewModel != null) {
                                    viewModel.openModal(BottomCapsuleModal.AllTransactions("expense", monthStart))
                                } else {
                                    initialAllTransactionsFilter = "expense"
                                    showAllTransactionsDialog = true
                                }
                            }
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Rose500.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NorthEast,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Расходы",
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                                if (expenseMoMPercent != null && expenseMoMPercent != 0) {
                                    val sign = if (expenseMoMPercent > 0) "+" else ""
                                    Text(
                                        text = "$sign$expenseMoMPercent%",
                                        color = if (expenseMoMPercent <= 0) Emerald400 else Rose500,
                                        fontSize = 9.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "-${formatShortCurrency(monthTotalExpense)}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SpaceGroteskFontFamily,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // --- EXPENSE DYNAMICS AREA CHART ---
        com.example.ui.components.charts.ExpenseDynamicsAreaChartCard(
            transactions = monthTransactions,
            title = "ДИНАМИКА РАСХОДОВ",
            onClick = null
        )

        // --- STEP 3: OBSIDIAN BENTO CATEGORIES SECTION (2-Column Readable Cards with Real Limits) ---
        ObsidianBentoCategoriesSection(
            categoryExpenseTotals = categoryExpenseTotals,
            categoriesList = categoriesList,
            onOpenCategoryLimits = {
                if (viewModel != null) {
                    viewModel.openModal(BottomCapsuleModal.CategoryLimits)
                } else {
                    showCategoryLimitsDialog = true
                }
            }
        )

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

        if (showSharesDialog && !isAppLocked) {
            ExpenseSharesDialog(
                filteredTransactions = filteredTransactions,
                allTransactions = allTransactions,
                onDeleteTransaction = onDeleteTransaction,
                onEditTransaction = onEditTransaction,
                selectedDateDay = selectedDateDay,
                onDateSelected = onChangeSelectedDay,
                aiAuditResult = aiAuditResult,
                aiAuditLoading = aiAuditLoading,
                savedAiAudit = savedAiAudit?.auditText,
                onRequestAiAudit = onRequestAiAudit,
                periodType = periodType,
                onSetPeriodType = onSetPeriodType,
                selectedMonthIdx = selectedMonthIdx,
                onChangeSelectedMonthIdx = onChangeSelectedMonthIdx,
                allPeriodStart = allPeriodStart,
                allPeriodEnd = allPeriodEnd,
                onChangeAllPeriodStart = onChangeAllPeriodStart,
                onChangeAllPeriodEnd = onChangeAllPeriodEnd,
                monthsRu = MonthsRu,
                viewModel = viewModel,
                onDismiss = { showSharesDialog = false }
            )
        }

        if (showIncomeExpenseSummaryDialog && !isAppLocked) {
            IncomeExpenseSummaryDialog(
                transactions = filteredTransactions,
                onDismiss = { showIncomeExpenseSummaryDialog = false }
            )
        }

        if (showAllTransactionsDialog && !isAppLocked) {
            val currentPeriodInitialDate = if (initialAllTransactionsFilter != "all") {
                monthStart
            } else if (periodType == PeriodType.DAY || periodType == PeriodType.WEEK) {
                selectedDateDay
            } else {
                monthStart
            }
            AllTransactionsDialog(
                transactions = if (allTransactions.isNotEmpty()) allTransactions else filteredTransactions,
                onDeleteTransaction = onDeleteTransaction,
                onRestoreTransaction = { restoredTx ->
                    viewModel?.addTransaction(
                        type = restoredTx.type,
                        date = restoredTx.date,
                        category = restoredTx.category,
                        subcategory = restoredTx.subcategory,
                        amount = restoredTx.amount,
                        accountId = restoredTx.accountId
                    )
                },
                onEditTransaction = onEditTransaction,
                initialFilterType = initialAllTransactionsFilter,
                initialDate = currentPeriodInitialDate,
                onDismiss = { showAllTransactionsDialog = false }
            )
        }

        // --- STEP 4: RECENT TRANSACTIONS SECTION WITH DATE GROUPING ---
        RecentTransactionsFeedCard(
            filteredTransactions = filteredTransactions,
            allTransactions = allTransactions,
            todayIso = todayIso,
            onOpenAllTransactions = {
                val targetDate = if (periodType == PeriodType.DAY || periodType == PeriodType.WEEK) {
                    selectedDateDay
                } else {
                    monthStart
                }
                if (viewModel != null) {
                    viewModel.openModal(BottomCapsuleModal.AllTransactions("all", targetDate))
                } else {
                    initialAllTransactionsFilter = "all"
                    showAllTransactionsDialog = true
                }
            },
            onDeleteTransaction = onDeleteTransaction,
            onEditTransaction = onEditTransaction
        )

        // Extra bottom padding so the floating Obsidian Bottom Dock & Voice FAB never cover the last card
        Spacer(modifier = Modifier.height(88.dp))
    }
}
