package com.example.ui.components.dialogs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.RecurringPaymentEntity
import com.example.data.repository.FinancialSemanticsSnapshot
import com.example.data.safetospend.*
import com.example.ui.components.ObsidianBentoDateCard
import com.example.ui.components.SingleDatePickerDialog
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.components.formatFullCurrency
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.*
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

data class FinancialStateUiModel(
    val availableBalanceRub: Double,
    val netWorthRub: Double,
    val freeMoneyAfterReservationRub: Double,
    val rawFreeDiscretionaryPoolRub: Double,
    val freeMoneyDeficitRub: Double,
    val hasExplicitDeficitWarning: Boolean,
    val deficitWarningTitle: String?,
    val deficitWarningSubtitle: String?,
    val canSpendTodayRub: Double,
    val canSpendTodaySubtitle: String
)

data class SafeToSpendBreakdownUiModel(
    val canSpendTodayRub: Double,
    val availableFundsRub: Double,
    val upcomingObligationsRub: Double,
    val essentialHorizonReserveRub: Double,
    val essentialDailyNormRub: Double,
    val protectedReserveRub: Double,
    val horizonDays: Int,
    val hasGuaranteedIncomeAnchor: Boolean,
    val horizonLabel: String,
    val horizonValueText: String,
    val bottleneckReasonText: String,
    val bottleneckDateText: String,
    val estimationMethodText: String,
    val confidenceLevelText: String,
    val plannedIncomeRub: Double = 0.0
)

fun buildFinancialStateUiModel(
    semantics: FinancialSemanticsSnapshot,
    result: SafeToSpendResult
): FinancialStateUiModel {
    val availableBalanceRub = result.availableBalanceRub
    val netWorthRub = availableBalanceRub - semantics.totalWeOweRub
    val rawFreePoolRub = result.availableBalanceRub +
        result.todayPendingGuaranteedIncomeRub -
        result.lockedGoalsAndReserveRub -
        result.mandatorySafetyFloorRub -
        result.obligationsReservedTotalRub -
        result.reservedEssentialLivingHorizonRub

    val freeMoneyAfterReservationRub = max(0.0, rawFreePoolRub)
    val freeMoneyDeficitRub = max(
        result.reserveDeficitRub,
        max(0.0, -rawFreePoolRub)
    )
    val hasExplicitDeficitWarning = result.hasCashGap ||
        !result.isReserveRequirementMet ||
        rawFreePoolRub < -0.01

    val (warningTitle, warningSubtitle) = when {
        result.hasCashGap -> {
            "Риск кассового разрыва: −${formatFullCurrency(result.cashGapAmountRub)}" to
                "Доступных средств и гарантированных доходов не хватает на обязательные платежи и базовые расходы."
        }
        hasExplicitDeficitWarning -> {
            "Дефицит после резервирования: −${formatFullCurrency(freeMoneyDeficitRub)}" to
                "Обязательные платежи, базовые нужды и защитный резерв превышают текущий остаток."
        }
        else -> null to null
    }

    val canSpendSubtitle = when {
        result.hasCashGap -> "Внимание: дефицит средств на обязательные платежи"
        !result.isReserveRequirementMet -> "Защитный резерв не покрыт • дефицит ${formatShortCurrency(result.reserveDeficitRub)}"
        result.todayOverspendRub > 0.0 -> "Дневной лимит превышен на ${formatFullCurrency(result.todayOverspendRub)}"
        result.requiresEssentialBudgetPrompt -> "С учётом платежей • уточните норму базовых трат (${result.dailyEssentialRateRub.toInt()} ₽/дн.)"
        else -> "С учётом предстоящих платежей"
    }

    return FinancialStateUiModel(
        availableBalanceRub = availableBalanceRub,
        netWorthRub = netWorthRub,
        freeMoneyAfterReservationRub = freeMoneyAfterReservationRub,
        rawFreeDiscretionaryPoolRub = rawFreePoolRub,
        freeMoneyDeficitRub = freeMoneyDeficitRub,
        hasExplicitDeficitWarning = hasExplicitDeficitWarning,
        deficitWarningTitle = warningTitle,
        deficitWarningSubtitle = warningSubtitle,
        canSpendTodayRub = result.canSpendTodayDiscRub,
        canSpendTodaySubtitle = canSpendSubtitle
    )
}

fun buildSafeToSpendBreakdownUiModel(result: SafeToSpendResult): SafeToSpendBreakdownUiModel {
    val hasIncomeAnchor = result.nextIncomeEpochDay != null
    val protectedReserveRub = result.mandatorySafetyFloorRub + result.lockedGoalsAndReserveRub
    val horizonDays = result.horizonDays.coerceAtLeast(1)
    val plannedIncomeRub = result.todayPendingGuaranteedIncomeRub + result.upcomingGuaranteedIncomeRub

    return SafeToSpendBreakdownUiModel(
        canSpendTodayRub = result.canSpendTodayDiscRub,
        availableFundsRub = result.availableBalanceRub,
        upcomingObligationsRub = result.obligationsReservedTotalRub,
        essentialHorizonReserveRub = result.reservedEssentialLivingHorizonRub,
        essentialDailyNormRub = result.dailyEssentialRateRub,
        protectedReserveRub = protectedReserveRub,
        horizonDays = horizonDays,
        hasGuaranteedIncomeAnchor = hasIncomeAnchor,
        horizonLabel = if (hasIncomeAnchor) "Дней до зарплаты" else "Горизонт прогноза",
        horizonValueText = "$horizonDays ${formatDaysWord(horizonDays)}",
        bottleneckReasonText = localizeBottleneckReason(result.bottleneckReason),
        bottleneckDateText = "${formatRussianShortDate(result.bottleneckEpochDay)} (через ${result.bottleneckDayOffset} ${formatDaysWord(result.bottleneckDayOffset)})",
        estimationMethodText = localizeEssentialMethod(result.essentialEstimationMethod),
        confidenceLevelText = localizeConfidenceLevel(result.essentialConfidenceLevel),
        plannedIncomeRub = plannedIncomeRub
    )
}

fun localizeBottleneckReason(reason: BottleneckReason): String = when (reason) {
    BottleneckReason.LIQUIDITY_HORIZON -> "Равномерное распределение до конца горизонта"
    BottleneckReason.CATEGORY_MONTHLY_BUDGET -> "Ограничено месячным лимитом по категориям"
    BottleneckReason.INSUFFICIENT_RESERVE_FLOOR -> "Свободных средств меньше защитного резерва"
    BottleneckReason.CASH_GAP_DETECTED -> "Кассовый разрыв по обязательным платежам и базовым расходам"
    BottleneckReason.OVERDUE_OBLIGATION_PRESSURE -> "Давление неоплаченных просроченных обязательств"
}

fun localizeWhatIfVerdict(verdict: WhatIfVerdict, isPurchaseScenario: Boolean = true): String = when (verdict) {
    WhatIfVerdict.SAFE_WITHIN_TODAY_LIMIT ->
        if (isPurchaseScenario) "Покупка вписывается в безопасный бюджет"
        else "Сценарий укладывается в безопасный бюджет"
    WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET ->
        if (isPurchaseScenario) "Покупка снизит дневной бюджет следующих дней"
        else "Дневной бюджет на оставшиеся дни уменьшится"
    WhatIfVerdict.RESERVE_BREACH ->
        if (isPurchaseScenario) "Покупка затронет защитный резерв"
        else "Сценарий затрагивает защитный резерв"
    WhatIfVerdict.CASH_GAP_RISK ->
        "Риск кассового разрыва — не хватит на обязательные платежи"
}

fun localizeEssentialMethod(method: EssentialEstimationMethod): String = when (method) {
    EssentialEstimationMethod.WEEKLY_TRIMMED_WINSORIZED -> "По фактической истории покупок (недельные корзины и амортизация)"
    EssentialEstimationMethod.EMPIRICAL_BAYES_SHRINKAGE -> "Сглаженная оценка по неполной истории трат"
    EssentialEstimationMethod.USER_DEFINED_NORM -> "Задано вами вручную"
    EssentialEstimationMethod.CATEGORY_LIMIT_FALLBACK -> "По месячным лимитам базовых категорий"
    EssentialEstimationMethod.CONSERVATIVE_SYSTEM_FALLBACK -> "Базовый защитный ориентир (мало истории)"
}

fun localizeConfidenceLevel(level: EssentialConfidenceLevel): String = when (level) {
    EssentialConfidenceLevel.HIGH_HISTORICAL -> "Высокая (достаточно истории)"
    EssentialConfidenceLevel.MODERATE_BLENDED -> "Средняя (накапливается история)"
    EssentialConfidenceLevel.USER_OR_LIMIT_BASED -> "По вашим настройкам и лимитам"
    EssentialConfidenceLevel.UNVERIFIED_FALLBACK -> "Требует уточнения"
}

fun formatDaysWord(days: Int): String {
    val n = abs(days) % 100
    val n1 = n % 10
    return when {
        n in 11..19 -> "дней"
        n1 == 1 -> "день"
        n1 in 2..4 -> "дня"
        else -> "дней"
    }
}

fun formatRussianShortDate(epochDay: Long): String {
    return try {
        val date = LocalDate.ofEpochDay(epochDay)
        val months = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
        )
        "${date.dayOfMonth} ${months.getOrElse(date.monthValue - 1) { "" }}"
    } catch (_: Exception) {
        LocalDate.ofEpochDay(epochDay).toString()
    }
}

fun formatSignedFullCurrency(amount: Double): String {
    return when {
        amount < -0.005 -> "−${formatFullCurrency(abs(amount))}"
        else -> formatFullCurrency(amount)
    }
}

private enum class SafeToSpendSheetPage {
    OVERVIEW,
    RECURRING_MANAGER,
    WHAT_IF_SIMULATOR,
    FORECAST_SETTINGS
}

@Composable
fun SafeToSpendAndRecurringDialog(
    safeToSpendResult: SafeToSpendResult,
    safeToSpendInput: SafeToSpendInput,
    recurringPayments: List<RecurringPaymentEntity>,
    userSafetyFloorRub: Double,
    userEssentialDailyNormRub: Double?,
    initialTab: Int = 0,
    isEmbeddedInCapsule: Boolean = false,
    onSetUserSafetyFloor: (Double) -> Unit,
    onSetUserEssentialDailyNorm: (Double?) -> Unit,
    onAddRecurringPayment: (
        title: String,
        amount: Double,
        type: String,
        category: String,
        frequency: String,
        nextDueDateIso: String,
        priority: String,
        incomeTier: String
    ) -> Unit,
    onUpdateRecurringPayment: (
        id: Long,
        title: String,
        amount: Double,
        type: String,
        category: String,
        frequency: String,
        nextDueDateIso: String,
        priority: String,
        incomeTier: String
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onConfirmRecurringPayment: (Long) -> Unit,
    onToggleRecurringStatus: (Long, String) -> Unit,
    onDeleteRecurringPayment: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialPage = remember(initialTab) {
        when (initialTab) {
            1 -> SafeToSpendSheetPage.RECURRING_MANAGER
            2 -> SafeToSpendSheetPage.WHAT_IF_SIMULATOR
            3 -> SafeToSpendSheetPage.FORECAST_SETTINGS
            else -> SafeToSpendSheetPage.OVERVIEW
        }
    }
    var currentPage by remember { mutableStateOf(initialPage) }
    var whatIfInitialMode by remember { mutableIntStateOf(0) } // 0 = Покупка, 1 = Задержка зарплаты, 2 = Запас хода
    var whatIfInitialAmount by remember { mutableStateOf("3000") }
    var whatIfInitialDelayDays by remember { mutableStateOf("5") }

    BackHandler(enabled = currentPage != SafeToSpendSheetPage.OVERVIEW) {
        currentPage = SafeToSpendSheetPage.OVERVIEW
    }

    val sheetContent: @Composable () -> Unit = {
        // Drag handle pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Slate700)
            )
        }

        when (currentPage) {
            SafeToSpendSheetPage.OVERVIEW -> SafeToSpendOverviewSheetContent(
                result = safeToSpendResult,
                safeToSpendInput = safeToSpendInput,
                recurringPayments = recurringPayments,
                onSetUserEssentialDailyNorm = onSetUserEssentialDailyNorm,
                onOpenRecurringManager = { currentPage = SafeToSpendSheetPage.RECURRING_MANAGER },
                onOpenWhatIf = { mode, amount, delayDays ->
                    whatIfInitialMode = mode
                    if (amount != null) whatIfInitialAmount = amount
                    if (delayDays != null) whatIfInitialDelayDays = delayDays
                    currentPage = SafeToSpendSheetPage.WHAT_IF_SIMULATOR
                },
                onOpenSettings = { currentPage = SafeToSpendSheetPage.FORECAST_SETTINGS },
                onDismiss = onDismiss
            )
            SafeToSpendSheetPage.RECURRING_MANAGER -> RecurringPaymentsManagerSheetContent(
                recurringPayments = recurringPayments,
                todayEpochDay = safeToSpendInput.todayEpochDay,
                onAddRecurringPayment = onAddRecurringPayment,
                onUpdateRecurringPayment = onUpdateRecurringPayment,
                onToggleRecurringStatus = onToggleRecurringStatus,
                onDeleteRecurringPayment = onDeleteRecurringPayment,
                onBack = { currentPage = SafeToSpendSheetPage.OVERVIEW },
                onDismiss = onDismiss
            )
            SafeToSpendSheetPage.WHAT_IF_SIMULATOR -> WhatIfSimulatorSheetContent(
                safeToSpendInput = safeToSpendInput,
                initialMode = whatIfInitialMode,
                initialPurchaseAmount = whatIfInitialAmount,
                initialDelayDays = whatIfInitialDelayDays,
                onBack = { currentPage = SafeToSpendSheetPage.OVERVIEW },
                onDismiss = onDismiss
            )
            SafeToSpendSheetPage.FORECAST_SETTINGS -> ForecastSettingsSheetContent(
                result = safeToSpendResult,
                userSafetyFloorRub = userSafetyFloorRub,
                userEssentialDailyNormRub = userEssentialDailyNormRub,
                onSetUserSafetyFloor = onSetUserSafetyFloor,
                onSetUserEssentialDailyNorm = onSetUserEssentialDailyNorm,
                onBack = { currentPage = SafeToSpendSheetPage.OVERVIEW },
                onDismiss = onDismiss
            )
        }
    }

    if (isEmbeddedInCapsule) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            sheetContent()
        }
    } else {
        SwipeToDismissDialog(
            onDismissRequest = onDismiss,
            contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 0.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                GlassHighlight,
                                GlassBorder,
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {},
                color = Color(0xFF0B101B)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    sheetContent()
                }
            }
        }
    }
}

@Composable
private fun SafeToSpendOverviewSheetContent(
    result: SafeToSpendResult,
    safeToSpendInput: SafeToSpendInput,
    recurringPayments: List<RecurringPaymentEntity>,
    onSetUserEssentialDailyNorm: (Double?) -> Unit,
    onOpenRecurringManager: () -> Unit,
    onOpenWhatIf: (mode: Int, amount: String?, delayDays: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val breakdown = remember(result) { buildSafeToSpendBreakdownUiModel(result) }
    val isReserveDeficit = !result.isReserveRequirementMet
    val statusColor = when {
        result.hasCashGap || isReserveDeficit || result.todayOverspendRub > 0.0 -> Rose500
        result.requiresEssentialBudgetPrompt -> Amber400
        else -> Emerald400
    }

    var showHeaderInfo by remember { mutableStateOf(false) }
    var isHowCalculatedExpanded by remember { mutableStateOf(false) }
    var isDetailedMathExpanded by remember { mutableStateOf(false) }
    var isTrajectoryExpanded by remember { mutableStateOf(false) }
    var isUpcomingRecurringExpanded by remember { mutableStateOf(false) }

    var essentialPromptInput by remember(result.dailyEssentialRateRub) {
        mutableStateOf(result.dailyEssentialRateRub.toInt().toString())
    }

    // Dynamic What-If quick scenarios computed strictly via real WhatIfSimulator
    val samplePurchaseRub = remember(result.canSpendTodayDiscRub, result.dailyDiscretionarySpeedRub) {
        val base = when {
            result.canSpendTodayDiscRub >= 1000.0 -> (result.canSpendTodayDiscRub * 1.5).roundToLong()
            result.dailyDiscretionarySpeedRub >= 1000.0 -> (result.dailyDiscretionarySpeedRub * 1.5).roundToLong()
            else -> 1500L
        }
        val rounded = ((base + 249L) / 500L) * 500L
        rounded.coerceAtLeast(500L).toDouble()
    }
    val quickPurchaseSim = remember(safeToSpendInput, samplePurchaseRub) {
        WhatIfSimulator.simulate(
            baseInput = safeToSpendInput,
            request = WhatIfScenarioRequest.HypotheticalSpend(
                amountRub = samplePurchaseRub,
                streamType = ExpenseStreamType.DISCRETIONARY,
                label = "Разовая покупка"
            )
        )
    }
    val quickDelaySim = remember(safeToSpendInput) {
        WhatIfSimulator.simulate(
            baseInput = safeToSpendInput,
            request = WhatIfScenarioRequest.IncomeDelay(delayDays = 5)
        )
    }
    val quickRunwaySim = remember(safeToSpendInput) {
        WhatIfSimulator.simulate(
            baseInput = safeToSpendInput,
            request = WhatIfScenarioRequest.RunwayCheck(plannedDailyDiscretionaryRub = 0.0)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header row: "Ваш безопасный дневной бюджет" + (i) + Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ваш безопасный дневной бюджет",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showHeaderInfo = !showHeaderInfo },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "О расчёте",
                        tint = if (showHeaderInfo) Emerald400 else Slate400,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Slate300,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Text(
            text = when {
                result.hasCashGap ->
                    "Внимание: на горизонте прогноза средств не хватает на обязательные платежи и базовые нужды."
                isReserveDeficit ->
                    "Свободный лимит равен 0 ₽, так как текущий остаток ниже защитного резерва и предстоящих обязательств."
                result.todayOverspendRub > 0.0 ->
                    "Сегодняшний лимит превышен на ${formatFullCurrency(result.todayOverspendRub)}. Бюджет на следующие дни уже пересчитан."
                else ->
                    "Сумма, которую можно потратить сегодня без риска для обязательных платежей."
            },
            color = Slate300,
            fontSize = 13.sp,
            fontFamily = ManropeFontFamily,
            lineHeight = 18.sp
        )

        AnimatedVisibility(
            visible = showHeaderInfo,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF131C2E))
                    .border(1.dp, Indigo500.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Безопасный дневной бюджет защищает ваши регулярные счета, выплаты по долгам, неснижаемый резерв и ежедневную норму на еду, транспорт и аптеку на всём горизонте до следующего гарантированного дохода.",
                    color = Slate200,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 17.sp
                )
            }
        }

        // Giant dynamic safe-to-spend number
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = formatFullCurrency(result.canSpendTodayDiscRub),
                color = statusColor,
                fontSize = 40.sp,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp
            )
            if (result.canSpendTodayEssRub > 0.0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Дополнительно защищено на базовые нужды сегодня: ${formatFullCurrency(result.canSpendTodayEssRub)}",
                        color = Slate300,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Explicit deficit or cash gap alert card
        if (result.hasCashGap || isReserveDeficit) {
            val alertColor = if (result.hasCashGap) Rose500 else Amber400
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(alertColor.copy(alpha = 0.12f))
                    .border(1.dp, alertColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (result.hasCashGap) {
                            val gapDateText = result.cashGapEpochDay?.let { formatRussianShortDate(it) } ?: "в горизонте"
                            "Кассовый разрыв ($gapDateText): −${formatFullCurrency(result.cashGapAmountRub)}"
                        } else {
                            "Дефицит защитного резерва: −${formatFullCurrency(result.reserveDeficitRub)}"
                        },
                        color = alertColor,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (result.hasCashGap) {
                            "Чтобы избежать нехватки денег на обязательные счета, сократите необязательные расходы или проверьте расписание платежей."
                        } else {
                            "Свободных средств после вычета предстоящих платежей и базовых расходов меньше установленного защитного резерва."
                        },
                        color = Slate200,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily
                    )
                }
            }
        }

        // Cold-start prompt banner if history < 7 days and no user norm / category limit
        if (result.requiresEssentialBudgetPrompt) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Amber400.copy(alpha = 0.12f))
                    .border(1.dp, Amber400.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Уточните дневной бюджет на базовые нужды",
                        color = Amber400,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "История трат на продукты, транспорт и аптеку пока меньше 7 дней. Сейчас резервируется ориентир ${result.dailyEssentialRateRub.toInt()} ₽/день. Укажите комфортную для вас сумму:",
                        color = Slate200,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = essentialPromptInput,
                            onValueChange = { essentialPromptInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Базовые расходы, ₽/день", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val parsed = essentialPromptInput.toDoubleOrNull()
                                if (parsed != null && parsed > 0.0) {
                                    onSetUserEssentialDailyNorm(parsed)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                        ) {
                            Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = GlassBorder)

        // Section: "Как рассчитана сумма"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { isHowCalculatedExpanded = !isHowCalculatedExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Как рассчитана сумма",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = if (isHowCalculatedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isHowCalculatedExpanded) "Свернуть" else "Развернуть",
                tint = Slate300
            )
        }

        AnimatedVisibility(
            visible = isHowCalculatedExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF121A2B))
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CalculationFactorRow(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "Доступные средства",
                            subtitle = "Фактические деньги на счетах",
                            valueText = formatSignedFullCurrency(breakdown.availableFundsRub),
                            valueColor = if (breakdown.availableFundsRub < 0.0) Rose500 else Color.White
                        )
                        CalculationFactorRow(
                            icon = Icons.Default.DateRange,
                            title = "Предстоящие платежи",
                            subtitle = when {
                                result.overdueObligationsRub > 0.0 ->
                                    "Включая просроченные ${formatShortCurrency(result.overdueObligationsRub)}"
                                else -> "Обязательства на горизонте расчёта"
                            },
                            valueText = if (breakdown.upcomingObligationsRub > 0.0) {
                                "−${formatFullCurrency(breakdown.upcomingObligationsRub)}"
                            } else {
                                "0 ₽"
                            },
                            valueColor = if (breakdown.upcomingObligationsRub > 0.0) Slate200 else Slate400
                        )
                        CalculationFactorRow(
                            icon = Icons.Default.Restaurant,
                            title = "Базовые расходы (еда, транспорт, аптека)",
                            subtitle = "Норма ${formatFullCurrency(breakdown.essentialDailyNormRub)}/день × ${breakdown.horizonDays} дн.",
                            valueText = if (breakdown.essentialHorizonReserveRub > 0.0) {
                                "−${formatFullCurrency(breakdown.essentialHorizonReserveRub)}"
                            } else {
                                "0 ₽"
                            },
                            valueColor = if (breakdown.essentialHorizonReserveRub > 0.0) Slate200 else Slate400
                        )
                        CalculationFactorRow(
                            icon = Icons.Default.Shield,
                            title = "Защитный резерв",
                            subtitle = if (result.lockedGoalsAndReserveRub > 0.0) {
                                "Неснижаемый запас и целевые резервы"
                            } else {
                                "Неснижаемый подушечный остаток"
                            },
                            valueText = if (breakdown.protectedReserveRub > 0.0) {
                                "−${formatFullCurrency(breakdown.protectedReserveRub)}"
                            } else {
                                "0 ₽"
                            },
                            valueColor = if (breakdown.protectedReserveRub > 0.0) Slate200 else Slate400
                        )
                        if (breakdown.plannedIncomeRub > 0.0) {
                            CalculationFactorRow(
                                icon = Icons.Default.Payments,
                                title = "Ожидаемые регулярные доходы",
                                subtitle = if (result.todayPendingGuaranteedIncomeRub > 0.0) {
                                    "Запланировано к поступлению сегодня"
                                } else {
                                    result.nextIncomeEpochDay?.let { "Поступление ${formatRussianShortDate(it)}" }
                                        ?: "Регулярный доход на горизонте"
                                },
                                valueText = "+${formatFullCurrency(breakdown.plannedIncomeRub)}",
                                valueColor = Emerald400
                            )
                        }
                        CalculationFactorRow(
                            icon = Icons.Default.EventAvailable,
                            title = breakdown.horizonLabel,
                            subtitle = result.nextIncomeEpochDay?.let {
                                val incStr = if (breakdown.plannedIncomeRub > 0.0) " (+${formatFullCurrency(breakdown.plannedIncomeRub)})" else ""
                                "До ${formatRussianShortDate(it)}$incStr"
                            } ?: "Окно планирования ликвидности",
                            valueText = breakdown.horizonValueText,
                            valueColor = Color.White
                        )
                    }
                }

                Text(
                    text = "Алгоритм анализирует ваши доходы, регулярные платежи, переменные расходы и проверяет баланс на каждый день горизонта. Итоговый дневной бюджет определяется самой напряжённой точкой горизонта и лимитами категорий.",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily,
                    lineHeight = 17.sp
                )

                Text(
                    text = if (isDetailedMathExpanded) "Скрыть подробности расчёта ▲" else "Подробнее о расчёте",
                    color = Emerald400,
                    fontSize = 13.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isDetailedMathExpanded = !isDetailedMathExpanded }
                        .padding(vertical = 2.dp)
                )

                AnimatedVisibility(
                    visible = isDetailedMathExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(ObsidianSurface)
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Детализация ограничений и прогноза",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            BreakdownRow(
                                label = "Причина ограничения бюджета",
                                value = breakdown.bottleneckReasonText,
                                valueColor = Emerald400
                            )
                            BreakdownRow(
                                label = "Дата финансового узкого места",
                                value = breakdown.bottleneckDateText,
                                valueColor = Color.White
                            )
                            BreakdownRow(
                                label = "Расчётный темп на будущие дни",
                                value = "${formatFullCurrency(result.dailyDiscretionarySpeedRub)}/день",
                                valueColor = Color.White
                            )
                            BreakdownRow(
                                label = "Потрачено сегодня (всего)",
                                value = formatFullCurrency(result.spentTodayTotalRub),
                                valueColor = Slate200
                            )
                            if (result.spentTodayTotalRub > 0.0) {
                                Text(
                                    text = "Из них сегодня: необязательные ${formatFullCurrency(result.spentTodayDiscretionaryRub)}, базовые ${formatFullCurrency(result.spentTodayEssentialRub)}, обязательные ${formatFullCurrency(result.spentTodayMandatoryRub)}",
                                    color = Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily
                                )
                            }
                            BreakdownRow(
                                label = "Метод оценки базовых расходов",
                                value = breakdown.estimationMethodText,
                                valueColor = Slate300
                            )
                            BreakdownRow(
                                label = "Достоверность оценки базы",
                                value = breakdown.confidenceLevelText,
                                valueColor = if (result.requiresEssentialBudgetPrompt) Amber400 else Emerald400
                            )
                            if (result.expectedUnconfirmedIncomeHorizonRub > 0.0) {
                                BreakdownRow(
                                    label = "При приходе ожидаемых доходов (+${formatShortCurrency(result.expectedUnconfirmedIncomeHorizonRub)})",
                                    value = "${formatFullCurrency(result.expectedScenarioDailyBudgetRub)}/день",
                                    valueColor = Emerald400
                                )
                            }

                            if (result.trajectory.isNotEmpty()) {
                                HorizontalDivider(color = GlassBorder)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isTrajectoryExpanded = !isTrajectoryExpanded }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Прогноз ликвидности по дням (${result.trajectory.size} дн.)",
                                        color = Indigo400,
                                        fontSize = 12.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isTrajectoryExpanded) "Свернуть ▲" else "Показать ▼",
                                        color = Indigo400,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                AnimatedVisibility(visible = isTrajectoryExpanded) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        result.trajectory.take(14).forEach { point ->
                                            val isBottleneck = point.offsetDays == result.bottleneckDayOffset
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isBottleneck) Indigo500.copy(alpha = 0.14f)
                                                        else Color.White.copy(alpha = 0.03f)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (point.offsetDays == 0) {
                                                            "Сегодня (${formatRussianShortDate(point.epochDay)})"
                                                        } else {
                                                            "${formatRussianShortDate(point.epochDay)} (+${point.offsetDays} дн.)"
                                                        } + if (isBottleneck) " • узкое место" else "",
                                                        color = if (isBottleneck) Indigo400 else Color.White,
                                                        fontSize = 11.sp,
                                                        fontFamily = ManropeFontFamily,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Свободный пул: ${formatSignedFullCurrency(point.freeLiquidityGuarRub)}",
                                                        color = if (point.freeLiquidityGuarRub < 0.0) Rose500 else Slate400,
                                                        fontSize = 10.sp,
                                                        fontFamily = ManropeFontFamily
                                                    )
                                                }
                                                Text(
                                                    text = "${formatFullCurrency(point.impliedDailyDiscretionarySpeedRub)}/дн.",
                                                    color = if (point.impliedDailyDiscretionarySpeedRub <= 0.0) Rose500 else Emerald400,
                                                    fontSize = 11.sp,
                                                    fontFamily = SpaceGroteskFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Expandable "Регулярные платежи" + "Управление платежами" button
        val activeRecurringCount = remember(recurringPayments) {
            recurringPayments.count { it.status == "ACTIVE" }
        }
        val activeExpenseTotal = remember(recurringPayments) {
            recurringPayments.filter { it.status == "ACTIVE" && !it.isIncome }.sumOf { it.amount }
        }
        val activeIncomeTotal = remember(recurringPayments) {
            recurringPayments.filter { it.status == "ACTIVE" && it.isIncome }.sumOf { it.amount }
        }
        val subtitleRecurringText = remember(recurringPayments, activeRecurringCount, activeExpenseTotal, activeIncomeTotal) {
            if (recurringPayments.isEmpty()) {
                "Аренда, связь, интернет и регулярные доходы"
            } else {
                val sampleTitles = recurringPayments.take(2).joinToString(", ") {
                    "${it.title} (${if (it.isIncome) "+" else "−"}${formatShortCurrency(it.amount)})"
                }
                "$sampleTitles • ${activeRecurringCount} активн."
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF121A2B))
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .clickable { isUpcomingRecurringExpanded = !isUpcomingRecurringExpanded }
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Emerald400.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventRepeat,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Регулярные платежи",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = subtitleRecurringText,
                                color = Slate400,
                                fontSize = 12.sp,
                                fontFamily = ManropeFontFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isUpcomingRecurringExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                        contentDescription = null,
                        tint = Slate300
                    )
                }

                AnimatedVisibility(
                    visible = isUpcomingRecurringExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = GlassBorder)
                        if (recurringPayments.isEmpty() && result.upcomingObligations.isEmpty()) {
                            Text(
                                text = "Нет запланированных регулярных платежей или зарплат. Нажмите «Управление платежами», чтобы добавить их.",
                                color = Slate400,
                                fontSize = 12.sp,
                                fontFamily = ManropeFontFamily
                            )
                        } else {
                            val sortedPreview = remember(recurringPayments, safeToSpendInput.todayEpochDay) {
                                recurringPayments.sortedBy { it.nextDueDateEpochDay }.take(6)
                            }
                            sortedPreview.forEach { item ->
                                val isOverdue = !item.isIncome &&
                                    item.status == "ACTIVE" &&
                                    item.nextDueDateEpochDay < safeToSpendInput.todayEpochDay
                                val statusText = when {
                                    item.status != "ACTIVE" -> "На паузе"
                                    isOverdue -> "Просрочено • ${formatRussianShortDate(item.nextDueDateEpochDay)}"
                                    item.nextDueDateEpochDay == safeToSpendInput.todayEpochDay -> "Сегодня"
                                    else -> formatRussianShortDate(item.nextDueDateEpochDay)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.03f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontFamily = ManropeFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${if (item.isIncome) "Доход" else item.categoryName} • $statusText",
                                            color = if (isOverdue) Rose500 else Slate400,
                                            fontSize = 11.sp,
                                            fontFamily = ManropeFontFamily
                                        )
                                    }
                                    Text(
                                        text = (if (item.isIncome) "+" else "−") + formatFullCurrency(item.amount),
                                        color = if (item.isIncome) Emerald400 else Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Indigo full-width "Управление платежами" button matching mockup
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF4338CA),
                            Color(0xFF3730A3)
                        )
                    )
                )
                .border(1.dp, Indigo400.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .clickable { onOpenRecurringManager() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Управление платежами",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        // Section: "Что будет, если..." (What-if)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Что будет, если...",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Indigo500.copy(alpha = 0.18f))
                    .border(1.dp, Indigo500.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "What-if",
                    color = Indigo400,
                    fontSize = 11.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Two side-by-side What-if action cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Сделать покупку
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121A2B))
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        onOpenWhatIf(0, samplePurchaseRub.toLong().toString(), null)
                    }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Indigo500.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = Indigo400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Сделать покупку",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Смоделировать расход и увидеть последствия",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        lineHeight = 15.sp
                    )
                }
            }

            // Card 2: Что, если зарплата задержится? (supported IncomeDelay & RunwayCheck)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121A2B))
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        onOpenWhatIf(1, null, "5")
                    }
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Emerald400.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Что, если зарплата задержится?",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Проверить сдвиг дохода и запас до зарплаты",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Dynamic calculated What-If quick scenarios card (0% hardcoded results!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF121A2B))
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickScenarioCalculatedRow(
                    title = "Покупка сегодня на ${formatFullCurrency(samplePurchaseRub)}",
                    badgeText = when (quickPurchaseSim.verdict) {
                        WhatIfVerdict.SAFE_WITHIN_TODAY_LIMIT ->
                            "Лимит сегодня: ${formatFullCurrency(quickPurchaseSim.simulated.canSpendTodayDiscRub)}"
                        WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET ->
                            "Темп: ${formatFullCurrency(quickPurchaseSim.simulated.dailyDiscretionarySpeedRub)}/дн."
                        WhatIfVerdict.RESERVE_BREACH -> "Затронет резерв"
                        WhatIfVerdict.CASH_GAP_RISK -> "Кассовый разрыв"
                    },
                    badgeColor = when (quickPurchaseSim.verdict) {
                        WhatIfVerdict.SAFE_WITHIN_TODAY_LIMIT -> Emerald400
                        WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET -> Amber400
                        WhatIfVerdict.RESERVE_BREACH, WhatIfVerdict.CASH_GAP_RISK -> Rose500
                    },
                    onClick = { onOpenWhatIf(0, samplePurchaseRub.toLong().toString(), null) }
                )
                HorizontalDivider(color = GlassBorder)
                QuickScenarioCalculatedRow(
                    title = "Задержка дохода на 5 дней",
                    badgeText = "Лимит: ${formatFullCurrency(quickDelaySim.simulated.canSpendTodayDiscRub)}",
                    badgeColor = when (quickDelaySim.verdict) {
                        WhatIfVerdict.SAFE_WITHIN_TODAY_LIMIT -> Emerald400
                        WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET -> Amber400
                        WhatIfVerdict.RESERVE_BREACH, WhatIfVerdict.CASH_GAP_RISK -> Rose500
                    },
                    onClick = { onOpenWhatIf(1, null, "5") }
                )
                HorizontalDivider(color = GlassBorder)
                QuickScenarioCalculatedRow(
                    title = "Автономный запас без необязательных трат",
                    badgeText = "${quickRunwaySim.autonomousSurvivalDaysAtZeroDisc} ${formatDaysWord(quickRunwaySim.autonomousSurvivalDaysAtZeroDisc)}",
                    badgeColor = if (quickRunwaySim.isEnoughKeepingSafetyReserve) Emerald400 else Amber400,
                    onClick = { onOpenWhatIf(2, null, null) }
                )
            }
        }

        // Settings row: "Настройки прогноза"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF121A2B))
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .clickable { onOpenSettings() }
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Slate300,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Настройки прогноза",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Защитный резерв и норма базовых расходов",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Slate400
                )
            }
        }

        Text(
            text = "Суммы рассчитываются автоматически в зависимости от актуальных данных и настроек прогноза.",
            color = Slate500,
            fontSize = 11.sp,
            fontFamily = ManropeFontFamily,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun CalculationFactorRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueText: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Slate300,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Slate200,
                    fontSize = 13.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = valueText,
            color = valueColor,
            fontSize = 15.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickScenarioCalculatedRow(
    title: String,
    badgeText: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Slate200,
            fontSize = 13.sp,
            fontFamily = ManropeFontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = badgeText,
            color = badgeColor,
            fontSize = 13.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}
