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
    var showSafeToSpendDialog by remember { mutableStateOf(false) }
    var safeToSpendInitialTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var initialAllTransactionsFilter by remember { mutableStateOf("all") }

    val categoriesListState = viewModel?.categories?.collectAsStateWithLifecycle()
    val categoriesList = categoriesListState?.value ?: remember { emptyList() }

    val accountsListState = viewModel?.accounts?.collectAsStateWithLifecycle()
    val accountsList = accountsListState?.value ?: remember { emptyList() }

    val goalsListState = viewModel?.goals?.collectAsStateWithLifecycle()
    val goalsList = goalsListState?.value ?: remember { emptyList() }

    val recurringPaymentsState = viewModel?.recurringPayments?.collectAsStateWithLifecycle()
    val recurringPaymentsList = recurringPaymentsState?.value ?: remember { emptyList() }

    val recurringLogsState = viewModel?.recurringExecutionLogs?.collectAsStateWithLifecycle()
    val recurringLogsList = recurringLogsState?.value ?: remember { emptyList() }

    val userSafetyFloorState = viewModel?.userSafetyFloorRub?.collectAsStateWithLifecycle()
    val userSafetyFloorRub = userSafetyFloorState?.value ?: 0.0

    val userEssentialDailyNormState = viewModel?.userEssentialDailyNormRub?.collectAsStateWithLifecycle()
    val userEssentialDailyNormRub = userEssentialDailyNormState?.value

    val todayIso = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    val todayEpochDay = remember(todayIso) {
        com.example.data.safetospend.DavidFinancialIntelligenceProvider.parseTransactionEpochDay(todayIso)
            ?: java.time.LocalDate.now().toEpochDay()
    }

    val safeToSpendSnapshot = remember(
        allTransactions,
        filteredTransactions,
        accountsList,
        categoriesList,
        goalsList,
        recurringPaymentsList,
        recurringLogsList,
        userSafetyFloorRub,
        userEssentialDailyNormRub,
        todayEpochDay
    ) {
        val txSource = if (allTransactions.isNotEmpty()) allTransactions else filteredTransactions
        com.example.data.safetospend.DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = txSource,
            accounts = accountsList,
            goals = goalsList,
            categories = categoriesList,
            recurringPayments = recurringPaymentsList,
            executionLogs = recurringLogsList,
            todayEpochDay = todayEpochDay,
            userSafetyFloorRub = userSafetyFloorRub,
            userEssentialDailyNormRub = userEssentialDailyNormRub
        )
    }

    val safeToSpendResult = safeToSpendSnapshot.safeToSpendResult
    val safeDailyBudget = safeToSpendResult.dailyDiscretionarySpeedRub
    val todaySpent = safeToSpendResult.spentTodayDiscretionaryRub

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

                // Adaptive Safe-to-Spend 2.0 Card ("Можно потратить сегодня")
                Spacer(modifier = Modifier.height(14.dp))
                run {
                    val safeRemainingToday = safeToSpendResult.canSpendTodayDiscRub
                    val isReserveDeficit = !safeToSpendResult.isReserveRequirementMet
                    val isOverDaily = safeToSpendResult.todayOverspendRub > 0.0 ||
                        safeToSpendResult.hasCashGap ||
                        isReserveDeficit
                    val dailyProgress = if (safeDailyBudget > 0.0) {
                        (todaySpent / safeDailyBudget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    } else if (isOverDaily) 1f else 0.08f
                    val statusColor = when {
                        safeToSpendResult.hasCashGap || isReserveDeficit || safeToSpendResult.todayOverspendRub > 0.0 -> Rose500
                        dailyProgress > 0.8f || safeToSpendResult.requiresEssentialBudgetPrompt -> Amber400
                        else -> Emerald400
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                safeToSpendInitialTab = 0
                                showSafeToSpendDialog = true
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        safeToSpendResult.hasCashGap -> "Риск кассового разрыва"
                                        isReserveDeficit -> "Дефицит резерва безопасности"
                                        safeToSpendResult.todayOverspendRub > 0.0 -> "Дневной лимит превышен"
                                        else -> "Можно потратить сегодня"
                                    },
                                    color = Slate300,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when {
                                        safeToSpendResult.todayOverspendRub > 0.0 ->
                                            "Перерасход ${formatShortCurrency(safeToSpendResult.todayOverspendRub)}"
                                        else ->
                                            "${formatShortCurrency(safeRemainingToday)} (+${formatShortCurrency(safeToSpendResult.canSpendTodayEssRub)} база)"
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        safeToSpendResult.requiresEssentialBudgetPrompt ->
                                            "⚠️ Ориентир ${safeToSpendResult.dailyEssentialRateRub.toInt()} ₽/дн. (нажмите, чтобы уточнить)"
                                        safeToSpendResult.overdueObligationsRub > 0.0 ->
                                            "Бронь просрочки: ${formatShortCurrency(safeToSpendResult.overdueObligationsRub)}"
                                        else ->
                                            "Горизонт: ${safeToSpendResult.bottleneckDayOffset} дн. • Регулярных: ${recurringPaymentsList.size}"
                                    },
                                    color = if (safeToSpendResult.requiresEssentialBudgetPrompt) Amber400 else Slate400,
                                    fontSize = 10.sp,
                                    fontFamily = ManropeFontFamily
                                )
                                Text(
                                    text = "What-if / Платежи ›",
                                    color = Emerald400,
                                    fontSize = 10.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        safeToSpendInitialTab = 1
                                        showSafeToSpendDialog = true
                                    }
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
                    text = "КАТЕГОРИИ И ЛИМИТЫ",
                    color = Slate300,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Настроить ›",
                    color = Emerald400,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (viewModel != null) {
                                viewModel.openModal(BottomCapsuleModal.CategoryLimits)
                            } else {
                                showCategoryLimitsDialog = true
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                val colors = listOf(Emerald400, Indigo500, Rose500, Color(0xFFF59E0B), Color(0xFF06B6D4))
                if (categoryExpenseTotals.isNotEmpty()) {
                    val realSorted = categoryExpenseTotals.entries.sortedByDescending { it.value }.take(5)
                    val realItems = realSorted.mapIndexed { idx, entry ->
                        val (col, ic) = getCategoryColorAndIcon(entry.key, "")
                        val finalCol = if (col == Slate400) colors[idx % colors.size] else col
                        Triple(entry.key, entry.value, finalCol to ic)
                    }

                    if (realItems.size < 5) {
                        val remainingDb = categoriesList
                            .filter { it.type == "expense" && realItems.none { item -> item.first.equals(it.name, true) } }
                            .mapIndexed { index, cat ->
                                val (col, ic) = getCategoryColorAndIcon(cat.name, "")
                                val finalCol = if (col == Slate400) colors[(realItems.size + index) % colors.size] else col
                                Triple(cat.name, 0.0, finalCol to ic)
                            }
                        (realItems + remainingDb).take(5)
                    } else {
                        realItems
                    }
                } else {
                    val expenseDbCats = categoriesList.filter { it.type == "expense" }
                    if (expenseDbCats.isNotEmpty()) {
                        expenseDbCats.mapIndexed { idx, cat ->
                            val (col, ic) = getCategoryColorAndIcon(cat.name, "")
                            val finalCol = if (col == Slate400) colors[idx % colors.size] else col
                            Triple(cat.name, 0.0, finalCol to ic)
                        }.take(5)
                    } else {
                        defaultCategories.map { Triple(it.first, 0.0, it.third) }
                    }
                }
            }

            val maxCategoryVal = remember(displayCategories) {
                displayCategories.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
            }

            // 2-Column Bento Grid (3 rows x 2 columns: 5 top categories + "+ Лимит / Категория" card)
            val rows = displayCategories.chunked(2)
            rows.forEachIndexed { rowIdx, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { (name, amount, colorAndIcon) ->
                        val (baseColor, icon) = colorAndIcon
                        val limitObj = categoriesList.find { it.name.equals(name, ignoreCase = true) && it.type == "expense" }
                        val catLimit = limitObj?.monthlyLimit
                        val ratio = if (catLimit != null && catLimit > 0) {
                            (amount / catLimit).toFloat().coerceIn(0.06f, 1f)
                        } else {
                            (amount / maxCategoryVal).toFloat().coerceIn(0.08f, 1f)
                        }
                        val barColor = when {
                            catLimit != null && catLimit > 0 && amount > catLimit -> Rose500
                            catLimit != null && catLimit > 0 && amount >= catLimit * 0.8 -> Amber400
                            else -> baseColor
                        }

                        Card(
                            onClick = {
                                if (viewModel != null) {
                                    viewModel.openModal(BottomCapsuleModal.CategoryLimits)
                                } else {
                                    showCategoryLimitsDialog = true
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(baseColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = baseColor,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            color = Slate200,
                                            fontSize = 12.sp,
                                            fontFamily = ManropeFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatShortCurrency(amount),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (catLimit != null && catLimit > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Лимит ${formatShortCurrency(catLimit)}",
                                                color = Slate500,
                                                fontSize = 9.sp,
                                                fontFamily = ManropeFontFamily
                                            )
                                            Text(
                                                text = "${Math.round((amount / catLimit) * 100)}%",
                                                color = barColor,
                                                fontSize = 9.sp,
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.07f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = ratio)
                                                .height(4.dp)
                                                .clip(CircleShape)
                                                .background(barColor)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Fill 2nd slot of last row with "+ Все категории / Лимиты" card
                    if (rowItems.size == 1) {
                        Card(
                            onClick = {
                                if (viewModel != null) {
                                    viewModel.openModal(BottomCapsuleModal.CategoryLimits)
                                } else {
                                    showCategoryLimitsDialog = true
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface.copy(alpha = 0.6f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(88.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Emerald400.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Все лимиты",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${categoriesList.count { it.type == "expense" }} категорий",
                                        color = Emerald400,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
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

        if (showSafeToSpendDialog && !isAppLocked) {
            SafeToSpendAndRecurringDialog(
                safeToSpendResult = safeToSpendResult,
                safeToSpendInput = safeToSpendSnapshot.safeToSpendInput,
                recurringPayments = recurringPaymentsList,
                userSafetyFloorRub = userSafetyFloorRub,
                userEssentialDailyNormRub = userEssentialDailyNormRub,
                initialTab = safeToSpendInitialTab,
                onSetUserSafetyFloor = { viewModel?.setUserSafetyFloor(it) },
                onSetUserEssentialDailyNorm = { viewModel?.setUserEssentialDailyNorm(it) },
                onAddRecurringPayment = { title, amount, type, category, frequency, nextDueDateIso, priority, incomeTier ->
                    viewModel?.addRecurringPayment(
                        title = title,
                        amount = amount,
                        type = type,
                        category = category,
                        frequency = frequency,
                        nextDueDateIso = nextDueDateIso,
                        priority = priority,
                        incomeTier = incomeTier
                    )
                },
                onConfirmRecurringPayment = { viewModel?.confirmRecurringPayment(it) },
                onToggleRecurringStatus = { id, status -> viewModel?.toggleRecurringPaymentStatus(id, status) },
                onDeleteRecurringPayment = { viewModel?.deleteRecurringPayment(it) },
                onDismiss = { showSafeToSpendDialog = false }
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
        val recentTxList = remember(filteredTransactions) {
            filteredTransactions.take(7)
        }

        val groupedRecentTx = remember(recentTxList) {
            recentTxList.groupBy { it.date }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ИСТОРИЯ ОПЕРАЦИЙ",
                            color = Slate300,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Всего ${filteredTransactions.size} записей за период",
                            color = Slate500,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }

                    if (filteredTransactions.isNotEmpty() || allTransactions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier.clickable {
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
                            }
                        ) {
                            Text(
                                text = "Все операции ›",
                                color = Emerald400,
                                fontSize = 12.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (recentTxList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет операций за выбранный период",
                            color = Slate500,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groupedRecentTx.forEach { (dateStr, dayTxs) ->
                            val dayLabel = remember(dateStr, todayIso) {
                                formatHumanDateHeader(dateStr, todayIso)
                            }
                            val dayNet = remember(dayTxs) {
                                dayTxs.sumOf { if (it.type == "income") it.amount else -it.amount }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dayLabel,
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (dayNet >= 0) "+${formatShortCurrency(dayNet)}" else formatShortCurrency(dayNet),
                                    color = Slate500,
                                    fontSize = 11.sp,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            dayTxs.forEach { tx ->
                                TransactionRowItem(
                                    item = tx,
                                    onDelete = onDeleteTransaction,
                                    onClick = if (onEditTransaction != null) {
                                        { onEditTransaction(tx) }
                                    } else null,
                                    canDelete = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Extra bottom padding so the floating Obsidian Bottom Dock & Voice FAB never cover the last card
        Spacer(modifier = Modifier.height(88.dp))
    }
}

private fun formatHumanDateHeader(dateStr: String, todayIso: String): String {
    if (dateStr == todayIso) return "Сегодня"
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(todayIso) ?: return dateStr
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayIso = sdf.format(cal.time)
        if (dateStr == yesterdayIso) return "Вчера"

        val parts = dateStr.split("-")
        if (parts.size == 3) {
            val monthNum = parts[1].toIntOrNull() ?: 1
            val dayNum = parts[2].toIntOrNull() ?: 1
            val monthsGenitive = listOf(
                "января", "февраля", "марта", "апреля", "мая", "июня",
                "июля", "августа", "сентября", "октября", "ноября", "декабря"
            )
            return "$dayNum ${monthsGenitive.getOrElse(monthNum - 1) { "" }}"
        }
    } catch (_: Exception) {}
    return dateStr
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Emerald400.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) Emerald400.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Emerald400 else Slate400,
            fontSize = 11.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

fun getCategoryColorAndIcon(category: String, subcategory: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.vector.ImageVector> {
    val text = "$category $subcategory".lowercase()
    return when {
        text.contains("игра") || text.contains("гейм") || text.contains("аниме") || text.contains("кибер") -> Pair(Emerald400, Icons.Default.SportsEsports)
        text.contains("бар") || text.contains("тусов") || text.contains("алко") || text.contains("вечерин") -> Pair(Indigo500, Icons.Default.LocalBar)
        text.contains("форс") || text.contains("авар") || text.contains("штраф") -> Pair(Amber400, Icons.Default.Warning)
        text.contains("кредит") || text.contains("займ") || text.contains("ипотек") || text.contains("долг") || text.contains("банк") -> Pair(Rose500, Icons.Default.AccountBalance)
        text.contains("аптек") || text.contains("лекарст") || text.contains("здоровье") || text.contains("больниц") || text.contains("врач") || text.contains("медицин") -> Pair(Rose500, Icons.Default.MedicalServices)
        text.contains("сбережен") || text.contains("копилк") || text.contains("накоплен") || text.contains("вклад") || text.contains("инвест") || text.contains("фонд") || text.contains("цель") -> Pair(Indigo500, Icons.Default.Savings)
        text.contains("яндекс") || text.contains("yandex") -> Pair(androidx.compose.ui.graphics.Color(0xFFFC3F1D), Icons.Default.ShoppingBag)
        text.contains("сбер") || text.contains("перевод") || text.contains("тинькофф") || text.contains("карта") || text.contains("спб") -> Pair(Emerald400, Icons.Default.Refresh)
        text.contains("продукт") || text.contains("супермаркет") || text.contains("еда") || text.contains("магнит") || text.contains("пятерочк") || text.contains("ашан") || text.contains("магазин") -> Pair(androidx.compose.ui.graphics.Color(0xFFF59E0B), Icons.Default.Restaurant)
        text.contains("кафе") || text.contains("ресторан") || text.contains("фастфуд") || text.contains("доставк") || text.contains("кофе") || text.contains("столовая") -> Pair(androidx.compose.ui.graphics.Color(0xFFEC4899), Icons.Default.Restaurant)
        text.contains("транспорт") || text.contains("такси") || text.contains("авто") || text.contains("бензин") || text.contains("заправк") || text.contains("метро") || text.contains("автобус") -> Pair(Indigo400, Icons.Default.DirectionsCar)
        text.contains("развлечени") || text.contains("кино") || text.contains("подписк") || text.contains("музык") || text.contains("театр") || text.contains("спорт") -> Pair(androidx.compose.ui.graphics.Color(0xFFA855F7), Icons.Default.Theaters)
        text.contains("жилье") || text.contains("коммунал") || text.contains("дом") || text.contains("жкх") || text.contains("аренд") || text.contains("квартир") -> Pair(androidx.compose.ui.graphics.Color(0xFF06B6D4), Icons.Default.Home)
        text.contains("связь") || text.contains("интернет") || text.contains("телефон") || text.contains("мобильн") || text.contains("техник") -> Pair(androidx.compose.ui.graphics.Color(0xFF3B82F6), Icons.Default.Call)
        text.contains("зарплат") || text.contains("доход") || text.contains("преми") || text.contains("аванс") || text.contains("кэшбэк") || text.contains("халтур") -> Pair(Emerald400, Icons.Default.Payments)
        text.contains("одежд") || text.contains("обувь") || text.contains("гардероб") || text.contains("сумка") || text.contains("красот") || text.contains("салон") -> Pair(androidx.compose.ui.graphics.Color(0xFFEC4899), Icons.Default.Checkroom)
        text.contains("подарок") || text.contains("подарк") || text.contains("праздник") || text.contains("цветы") -> Pair(Rose500, Icons.Default.CardGiftcard)
        text.contains("книг") || text.contains("литератур") || text.contains("чтени") || text.contains("литрес") -> Pair(androidx.compose.ui.graphics.Color(0xFF8B5CF6), Icons.Default.List)
        text.contains("обучени") || text.contains("образовани") || text.contains("курсы") || text.contains("школа") -> Pair(androidx.compose.ui.graphics.Color(0xFF60A5FA), Icons.Default.School)
        else -> Pair(Slate400, Icons.Default.ShoppingBag)
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
    val bottomTitle = if (hasSubcategory) item.category else if (isExpense) "Расход" else "Доход"

    val cardContent: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(
            modifier = rowModifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0E1525))
                .border(
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color.White.copy(alpha = 0.07f)
                    ),
                    RoundedCornerShape(18.dp)
                )
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
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(catColor.copy(alpha = 0.15f))
                        .border(1.dp, catColor.copy(alpha = 0.32f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = item.category,
                        tint = catColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bottomTitle,
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "$prefix${numberFormat.format(item.amount)} ₽",
                color = if (isExpense) Color.White else Emerald400,
                fontSize = 15.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    if (canDelete) {
        SwipeToRevealBox(
            swipeDirection = SwipeDirection.Both,
            onDelete = { onDelete(item.id) },
            shape = RoundedCornerShape(18.dp),
            modifier = modifier
        ) {
            cardContent(Modifier)
        }
    } else {
        cardContent(modifier)
    }
}

