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
import androidx.compose.material.icons.filled.Shield

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
    val financialStateUi = remember(safeToSpendSnapshot.semantics, safeToSpendResult) {
        buildFinancialStateUiModel(
            semantics = safeToSpendSnapshot.semantics,
            result = safeToSpendResult
        )
    }

    val monthPeriodSemantics = remember(allTransactions, filteredTransactions, monthTransactions, accountsList) {
        val txSource = if (allTransactions.isNotEmpty()) allTransactions else filteredTransactions
        com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = txSource,
            periodTransactions = monthTransactions,
            accounts = accountsList
        )
    }
    val prevMonthPeriodSemantics = remember(allTransactions, filteredTransactions, selectedMonthIdx, selectedYear, accountsList) {
        val txSource = if (allTransactions.isNotEmpty()) allTransactions else filteredTransactions
        val prevIdx = if (selectedMonthIdx > 0) selectedMonthIdx - 1 else 11
        val prevYear = if (selectedMonthIdx > 0) selectedYear else selectedYear - 1
        val prevPrefix = String.format(Locale.US, "%04d-%02d", prevYear, prevIdx + 1)
        val prevTxs = allTransactions.filter { it.date.startsWith(prevPrefix) }
        com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = txSource,
            periodTransactions = prevTxs,
            accounts = accountsList
        )
    }
    val semanticMonthIncome = monthPeriodSemantics.earnedIncomeRub
    val semanticMonthExpense = monthPeriodSemantics.totalExpenseRub
    val incomeMoMPercent = remember(semanticMonthIncome, prevMonthPeriodSemantics.earnedIncomeRub) {
        val prevInc = prevMonthPeriodSemantics.earnedIncomeRub
        if (prevInc > 0.0) {
            Math.round(((semanticMonthIncome - prevInc) / prevInc) * 100).toInt()
        } else null
    }
    val semanticExpenseMoMPercent = remember(semanticMonthExpense, prevMonthPeriodSemantics.totalExpenseRub) {
        val prevExp = prevMonthPeriodSemantics.totalExpenseRub
        if (prevExp > 0.0) {
            Math.round(((semanticMonthExpense - prevExp) / prevExp) * 100).toInt()
        } else expenseMoMPercent
    }
    var showFinancialStateInfo by remember { mutableStateOf(false) }

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

        // --- UNIFIED "ФИНАНСОВОЕ СОСТОЯНИЕ" CARD (Issue #64) ---
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
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Card header: icon + ФИНАНСОВОЕ СОСТОЯНИЕ + (i)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Emerald400.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "ФИНАНСОВОЕ СОСТОЯНИЕ",
                            color = Slate300,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { showFinancialStateInfo = !showFinancialStateInfo },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "О показателях",
                            tint = if (showFinancialStateInfo) Emerald400 else Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showFinancialStateInfo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "• Доступный остаток — фактические ликвидные средства на счетах.",
                                color = Slate200,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily
                            )
                            Text(
                                text = "• Чистый капитал — доступный остаток с учётом ваших долговых обязательств.",
                                color = Slate200,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily
                            )
                            Text(
                                text = "• Свободные деньги — остаток после резервирования обязательных платежей, защитного резерва и базовых расходов на горизонте.",
                                color = Slate200,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily
                            )
                        }
                    }
                }

                // 1. Доступный остаток (фактические ликвидные средства)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Доступный остаток",
                        color = Slate200,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatSignedFullCurrency(financialStateUi.availableBalanceRub),
                        color = if (financialStateUi.availableBalanceRub < 0.0) Rose500 else Color.White,
                        fontSize = 34.sp,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Фактические деньги на счетах",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(GlassBorder)
                )

                // 2. Чистый капитал (с учётом долгов) | Свободные деньги (после резервирования)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Чистый капитал",
                            color = Slate300,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatSignedFullCurrency(financialStateUi.netWorthRub),
                            color = if (financialStateUi.netWorthRub < 0.0) Rose500 else Color.White,
                            fontSize = 20.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "С учётом долгов",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(GlassBorder)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Свободные деньги",
                            color = Slate300,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatFullCurrency(financialStateUi.freeMoneyAfterReservationRub),
                            color = when {
                                safeToSpendResult.hasCashGap -> Rose500
                                financialStateUi.hasExplicitDeficitWarning -> Amber400
                                else -> Emerald400
                            },
                            fontSize = 20.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "После резервирования",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                }

                // Explicit Deficit Warning when calculated free pool is negative or reserve is breached
                if (financialStateUi.hasExplicitDeficitWarning && financialStateUi.deficitWarningTitle != null) {
                    val warningColor = if (safeToSpendResult.hasCashGap) Rose500 else Amber400
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(warningColor.copy(alpha = 0.12f))
                            .border(1.dp, warningColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (viewModel != null) {
                                    viewModel.openModal(BottomCapsuleModal.SafeToSpend(0))
                                } else {
                                    safeToSpendInitialTab = 0
                                    showSafeToSpendDialog = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = financialStateUi.deficitWarningTitle,
                                color = warningColor,
                                fontSize = 12.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            financialStateUi.deficitWarningSubtitle?.let { sub ->
                                Text(
                                    text = sub,
                                    color = Slate200,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily
                                )
                            }
                        }
                    }
                }

                // 3. Выделенная интерактивная зона «Можно потратить сегодня» (открывает Bottom Sheet)
                run {
                    val isReserveDeficit = !safeToSpendResult.isReserveRequirementMet
                    val isWarningState = safeToSpendResult.hasCashGap ||
                        isReserveDeficit ||
                        safeToSpendResult.todayOverspendRub > 0.0
                    val statusColor = when {
                        safeToSpendResult.hasCashGap || isReserveDeficit || safeToSpendResult.todayOverspendRub > 0.0 -> Rose500
                        safeToSpendResult.requiresEssentialBudgetPrompt -> Amber400
                        else -> Emerald400
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isWarningState) {
                                        listOf(
                                            Rose500.copy(alpha = 0.18f),
                                            Color(0xFF2A121D)
                                        )
                                    } else {
                                        listOf(
                                            Color(0xFF0D3B2E),
                                            Color(0xFF092622)
                                        )
                                    }
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = statusColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                if (viewModel != null) {
                                    viewModel.openModal(BottomCapsuleModal.SafeToSpend(0))
                                } else {
                                    safeToSpendInitialTab = 0
                                    showSafeToSpendDialog = true
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(statusColor.copy(alpha = 0.18f))
                                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Можно потратить сегодня",
                                        color = Slate100,
                                        fontSize = 13.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = formatFullCurrency(financialStateUi.canSpendTodayRub),
                                        color = statusColor,
                                        fontSize = 24.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = financialStateUi.canSpendTodaySubtitle,
                                        color = Slate300,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Открыть подробности",
                                tint = Slate200,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- STANDALONE INCOME & EXPENSE CARDS ROW (Matching Mockup Layout) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Income Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                    .clickable {
                        if (viewModel != null) {
                            viewModel.openModal(BottomCapsuleModal.AllTransactions("income", monthStart))
                        } else {
                            initialAllTransactionsFilter = "income"
                            showAllTransactionsDialog = true
                        }
                    }
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Emerald400.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SouthWest,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Доходы",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "+${formatFullCurrency(semanticMonthIncome)}",
                            color = Emerald400,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SpaceGroteskFontFamily,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (incomeMoMPercent != null && incomeMoMPercent != 0) {
                            val arrow = if (incomeMoMPercent > 0) "↑" else "↓"
                            Text(
                                text = "$arrow ${kotlin.math.abs(incomeMoMPercent)}%",
                                color = if (incomeMoMPercent >= 0) Emerald400 else Amber400,
                                fontSize = 10.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Expense Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                    .clickable {
                        if (viewModel != null) {
                            viewModel.openModal(BottomCapsuleModal.AllTransactions("expense", monthStart))
                        } else {
                            initialAllTransactionsFilter = "expense"
                            showAllTransactionsDialog = true
                        }
                    }
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose500.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NorthEast,
                            contentDescription = null,
                            tint = Rose500,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Расходы",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "−${formatFullCurrency(semanticMonthExpense)}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SpaceGroteskFontFamily,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (semanticExpenseMoMPercent != null && semanticExpenseMoMPercent != 0) {
                            val arrow = if (semanticExpenseMoMPercent > 0) "↑" else "↓"
                            Text(
                                text = "$arrow ${kotlin.math.abs(semanticExpenseMoMPercent)}%",
                                color = if (semanticExpenseMoMPercent <= 0) Emerald400 else Rose500,
                                fontSize = 10.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
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
                onUpdateRecurringPayment = { id, title, amount, type, category, frequency, dueDate, priority, tier ->
                    viewModel?.updateRecurringPayment(id, title, amount, type, category, frequency, dueDate, priority, tier)
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

