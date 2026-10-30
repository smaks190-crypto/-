package com.example.ui.components.dialogs

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
    val confidenceLevelText: String
)

fun buildFinancialStateUiModel(
    semantics: FinancialSemanticsSnapshot,
    result: SafeToSpendResult
): FinancialStateUiModel {
    val availableBalanceRub = result.availableBalanceRub
    val netWorthRub = availableBalanceRub - semantics.totalWeOweRub
    val rawFreePoolRub = result.availableBalanceRub -
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
        confidenceLevelText = localizeConfidenceLevel(result.essentialConfidenceLevel)
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
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
    var isHowCalculatedExpanded by remember { mutableStateOf(true) }
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
                        CalculationFactorRow(
                            icon = Icons.Default.EventAvailable,
                            title = breakdown.horizonLabel,
                            subtitle = result.nextIncomeEpochDay?.let { "До ${formatRussianShortDate(it)}" }
                                ?: "Окно планирования ликвидности",
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
        val subtitleRecurringText = remember(recurringPayments) {
            if (recurringPayments.isEmpty()) {
                "Аренда, связь, интернет и регулярные доходы"
            } else {
                val sampleTitles = recurringPayments.take(3).joinToString(", ") { it.title }
                "$sampleTitles (${activeRecurringCount} активн.)"
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Сделать покупку
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121A2B))
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        onOpenWhatIf(0, samplePurchaseRub.toLong().toString(), null)
                    }
                    .padding(14.dp)
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF121A2B))
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        onOpenWhatIf(1, null, "5")
                    }
                    .padding(14.dp)
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

@Composable
private fun RecurringPaymentsManagerSheetContent(
    recurringPayments: List<RecurringPaymentEntity>,
    todayEpochDay: Long,
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
    onToggleRecurringStatus: (Long, String) -> Unit,
    onDeleteRecurringPayment: (Long) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = Все, 1 = Платежи, 2 = Подписки, 3 = Прочее (Доходы)
    var showAddForm by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var priority by remember { mutableStateOf("MANDATORY") }
    var nextDueDateIso by remember {
        mutableStateOf(LocalDate.ofEpochDay(todayEpochDay).toString())
    }

    val filteredItems = remember(recurringPayments, selectedFilter) {
        when (selectedFilter) {
            1 -> recurringPayments.filter { !it.isIncome && it.priority == "MANDATORY" }
            2 -> recurringPayments.filter { !it.isIncome && it.priority == "DISCRETIONARY_SUBSCRIPTION" }
            3 -> recurringPayments.filter { it.isIncome }
            else -> recurringPayments
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Bar: <- Регулярные платежи
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Регулярные платежи",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400
                )
            }
        }

        // Filter pills: Все | Платежи | Подписки | Прочее
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("Все", "Платежи", "Подписки", "Прочее")
            filters.forEachIndexed { idx, label ->
                val selected = selectedFilter == idx
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) Emerald400 else Color(0xFF141D2E))
                        .border(
                            width = 1.dp,
                            color = if (selected) Emerald400 else GlassBorder,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedFilter = idx }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.Black else Slate300,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Scrollable list of recurring items + optional add form
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = showAddForm,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF121A2B))
                        .border(1.dp, Emerald400.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Новый регулярный платёж / доход",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Скрыть",
                                color = Slate400,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { showAddForm = false }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = type == "EXPENSE" && priority == "MANDATORY",
                                onClick = {
                                    type = "EXPENSE"
                                    priority = "MANDATORY"
                                },
                                label = { Text("Платёж", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = type == "EXPENSE" && priority == "DISCRETIONARY_SUBSCRIPTION",
                                onClick = {
                                    type = "EXPENSE"
                                    priority = "DISCRETIONARY_SUBSCRIPTION"
                                },
                                label = { Text("Подписка", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = type == "INCOME",
                                onClick = { type = "INCOME" },
                                label = { Text("Доход (ЗП)", fontSize = 11.sp) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Название (Аренда, Связь, ЗП)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f)
                            )
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("Сумма, ₽", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = nextDueDateIso,
                                onValueChange = { nextDueDateIso = it },
                                label = { Text("Следующая дата (ГГГГ-ММ-ДД)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val amt = amountText.toDoubleOrNull() ?: return@Button
                                    if (amt <= 0.0) return@Button
                                    val defaultCategory = when {
                                        type == "INCOME" -> "Зарплата"
                                        priority == "DISCRETIONARY_SUBSCRIPTION" -> "Подписки"
                                        else -> "ЖКХ и Аренда"
                                    }
                                    onAddRecurringPayment(
                                        title.ifBlank { defaultCategory },
                                        amt,
                                        type,
                                        defaultCategory,
                                        "MONTHLY",
                                        nextDueDateIso,
                                        priority,
                                        "GUARANTEED"
                                    )
                                    title = ""
                                    amountText = ""
                                    showAddForm = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                            ) {
                                Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "В этой категории пока нет регулярных операций.\nНажмите «+ Добавить платеж» ниже.",
                        color = Slate400,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily
                    )
                }
            } else {
                filteredItems.forEach { item ->
                    val isActive = item.status == "ACTIVE"
                    val isOverdue = !item.isIncome && isActive && item.nextDueDateEpochDay < todayEpochDay
                    val (iconBg, iconVector) = resolveRecurringVisuals(item)
                    val scheduleLabel = remember(item.nextDueDateEpochDay, item.anchorDayOfMonth, item.frequency, isOverdue) {
                        when {
                            isOverdue -> "Просрочено (${formatRussianShortDate(item.nextDueDateEpochDay)})"
                            item.anchorDayOfMonth != null && item.frequency == "MONTHLY" -> "${item.anchorDayOfMonth} числа"
                            item.frequency == "WEEKLY" -> "Каждую неделю"
                            else -> "Каждый месяц"
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF121A2B))
                            .border(
                                width = 1.dp,
                                color = if (isOverdue) Rose500.copy(alpha = 0.5f) else GlassBorder,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
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
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(iconBg.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        tint = iconBg,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (isActive) Color.White else Slate400,
                                        fontSize = 14.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (item.isIncome) "Регулярный доход" else item.categoryName,
                                        color = Slate400,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = (if (item.isIncome) "+" else "") + formatFullCurrency(item.amount),
                                        color = when {
                                            !isActive -> Slate400
                                            item.isIncome -> Emerald400
                                            else -> Color.White
                                        },
                                        fontSize = 14.sp,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = scheduleLabel,
                                        color = if (isOverdue) Rose500 else Slate400,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily
                                    )
                                }

                                Switch(
                                    checked = isActive,
                                    onCheckedChange = { onToggleRecurringStatus(item.id, item.status) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Emerald400,
                                        uncheckedThumbColor = Slate400,
                                        uncheckedTrackColor = Slate800
                                    )
                                )

                                IconButton(
                                    onClick = { onDeleteRecurringPayment(item.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = Slate500,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-width bottom emerald button: "+ Добавить платеж"
        Button(
            onClick = { showAddForm = !showAddForm },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Добавить платеж",
                color = Color.Black,
                fontSize = 15.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun resolveRecurringVisuals(item: RecurringPaymentEntity): Pair<Color, ImageVector> {
    val lower = "${item.title} ${item.categoryName}".lowercase()
    return when {
        item.isIncome -> Emerald400 to Icons.Default.Payments
        lower.contains("аренд") || lower.contains("квартир") || lower.contains("жкх") || lower.contains("жиль") ->
            Emerald400 to Icons.Default.Home
        lower.contains("связь") || lower.contains("мобиль") || lower.contains("телефон") || lower.contains("tele2") || lower.contains("мтс") ->
            Indigo400 to Icons.Default.Call
        lower.contains("интернет") || lower.contains("ростелеком") || lower.contains("wi-fi") ->
            Color(0xFF38BDF8) to Icons.Default.Security
        lower.contains("spotify") || lower.contains("музык") || lower.contains("плюс") || lower.contains("подписк") || lower.contains("кино") ->
            Emerald400 to Icons.Default.Theaters
        lower.contains("кредит") || lower.contains("банк") || lower.contains("ипотек") || lower.contains("долг") ->
            Rose500 to Icons.Default.AccountBalance
        lower.contains("продукт") || lower.contains("питани") || lower.contains("еда") ->
            Emerald400 to Icons.Default.Restaurant
        else -> Indigo400 to Icons.Default.EventRepeat
    }
}

@Composable
private fun WhatIfSimulatorSheetContent(
    safeToSpendInput: SafeToSpendInput,
    initialMode: Int = 0,
    initialPurchaseAmount: String = "3000",
    initialDelayDays: String = "5",
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var scenarioMode by remember(initialMode) { mutableIntStateOf(initialMode) } // 0 = Покупка, 1 = Задержка зарплаты, 2 = Запас хода
    var hypotheticalPurchaseText by remember(initialPurchaseAmount) { mutableStateOf(initialPurchaseAmount) }
    var isEssentialPurchase by remember { mutableStateOf(false) }
    var salaryDelayDaysText by remember(initialDelayDays) { mutableStateOf(initialDelayDays) }
    var plannedDailyText by remember { mutableStateOf("1000") }
    var isResultDetailsExpanded by remember { mutableStateOf(true) }

    val simulationResult = remember(
        safeToSpendInput,
        scenarioMode,
        hypotheticalPurchaseText,
        isEssentialPurchase,
        salaryDelayDaysText,
        plannedDailyText
    ) {
        val request: WhatIfScenarioRequest = when (scenarioMode) {
            1 -> WhatIfScenarioRequest.IncomeDelay(
                delayDays = (salaryDelayDaysText.toIntOrNull() ?: 5).coerceAtLeast(1)
            )
            2 -> WhatIfScenarioRequest.RunwayCheck(
                plannedDailyDiscretionaryRub = (plannedDailyText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
            )
            else -> WhatIfScenarioRequest.HypotheticalSpend(
                amountRub = (hypotheticalPurchaseText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                streamType = if (isEssentialPurchase) ExpenseStreamType.ESSENTIAL_LIVING else ExpenseStreamType.DISCRETIONARY,
                label = if (isEssentialPurchase) "Базовая покупка" else "Разовая покупка"
            )
        }
        WhatIfSimulator.simulate(
            baseInput = safeToSpendInput,
            request = request
        )
    }

    val verdictColor = when (simulationResult.verdict) {
        WhatIfVerdict.SAFE_WITHIN_TODAY_LIMIT -> Emerald400
        WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET -> Amber400
        WhatIfVerdict.RESERVE_BREACH, WhatIfVerdict.CASH_GAP_RISK -> Rose500
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: <- What-if: Симулятор покупок
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Text(
                    text = if (scenarioMode == 0) "What-if: Симулятор покупок" else "What-if: Проверка сценариев",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400
                )
            }
        }

        // Scenario mode selector pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = listOf(
                0 to "Покупка",
                1 to "Задержка зарплаты",
                2 to "Запас до зарплаты"
            )
            modes.forEach { (idx, label) ->
                val selected = scenarioMode == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Emerald400.copy(alpha = 0.18f) else Color(0xFF121A2B))
                        .border(
                            1.dp,
                            if (selected) Emerald400 else GlassBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { scenarioMode = idx }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) Emerald400 else Slate300,
                        fontSize = 11.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Main simulator input card matching middle-right mockup
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF121A2B))
                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Emerald400.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (scenarioMode) {
                                1 -> "Что, если зарплата задержится?"
                                2 -> "Проверка запаса до зарплаты"
                                else -> "Покупка"
                            },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (scenarioMode) {
                                1 -> "Проверьте, как задержка ближайшего дохода повлияет на лимит и платежи."
                                2 -> "Узнайте, хватит ли средств до зарплаты при выбранном темпе трат."
                                else -> "Смоделируйте расход и узнайте, как он повлияет на ваш бюджет."
                            },
                            color = Slate400,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily,
                            lineHeight = 16.sp
                        )
                    }
                }

                when (scenarioMode) {
                    1 -> {
                        OutlinedTextField(
                            value = salaryDelayDaysText,
                            onValueChange = { salaryDelayDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Задержка зарплаты, дней", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("3", "5", "7", "14").forEach { preset ->
                                FilterChip(
                                    selected = salaryDelayDaysText == preset,
                                    onClick = { salaryDelayDaysText = preset },
                                    label = { Text("$preset дн.", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    2 -> {
                        OutlinedTextField(
                            value = plannedDailyText,
                            onValueChange = { plannedDailyText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Планируемые свободные траты, ₽/день", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = hypotheticalPurchaseText,
                            onValueChange = { hypotheticalPurchaseText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Сумма покупки, ₽", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Date / stream row matching mockup ("Дата покупки: Сегодня")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0B101B))
                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                .clickable { isEssentialPurchase = !isEssentialPurchase }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Дата и тип покупки",
                                        color = Slate400,
                                        fontSize = 11.sp,
                                        fontFamily = ManropeFontFamily
                                    )
                                    Text(
                                        text = if (isEssentialPurchase) {
                                            "Сегодня • Базовые нужды (еда, транспорт, аптека)"
                                        } else {
                                            "Сегодня • Необязательная покупка"
                                        },
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = ManropeFontFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = { isResultDetailsExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                ) {
                    Text(
                        text = "Рассчитать",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Result card matching middle-right mockup ("Результат: Покупка вписывается в безопасный бюджет")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF121A2B))
                .border(1.dp, verdictColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .clickable { isResultDetailsExpanded = !isResultDetailsExpanded }
                .padding(16.dp)
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
                                .clip(CircleShape)
                                .background(verdictColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = verdictColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Результат",
                                color = Slate400,
                                fontSize = 11.sp,
                                fontFamily = ManropeFontFamily
                            )
                            Text(
                                text = localizeWhatIfVerdict(
                                    verdict = simulationResult.verdict,
                                    isPurchaseScenario = scenarioMode == 0
                                ),
                                color = verdictColor,
                                fontSize = 14.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isResultDetailsExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                        contentDescription = null,
                        tint = Slate300
                    )
                }

                AnimatedVisibility(
                    visible = isResultDetailsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = GlassBorder)
                        BreakdownRow(
                            label = "Можно потратить сегодня (до → после)",
                            value = "${formatFullCurrency(simulationResult.baseline.canSpendTodayDiscRub)} → ${formatFullCurrency(simulationResult.simulated.canSpendTodayDiscRub)}",
                            valueColor = verdictColor
                        )
                        BreakdownRow(
                            label = "Дневной бюджет следующих дней",
                            value = "${formatFullCurrency(simulationResult.baseline.dailyDiscretionarySpeedRub)}/дн. → ${formatFullCurrency(simulationResult.simulated.dailyDiscretionarySpeedRub)}/дн.",
                            valueColor = Color.White
                        )
                        BreakdownRow(
                            label = "Изменение дневного лимита",
                            value = "${if (simulationResult.deltaDailyDiscretionarySpeedRub >= 0.0) "+" else "−"}${formatFullCurrency(abs(simulationResult.deltaDailyDiscretionarySpeedRub))}/дн.",
                            valueColor = verdictColor
                        )
                        BreakdownRow(
                            label = "Остаток к концу горизонта",
                            value = formatSignedFullCurrency(simulationResult.projectedBalanceAtSalaryEveRub),
                            valueColor = if (simulationResult.projectedBalanceAtSalaryEveRub < 0.0) Rose500 else Slate200
                        )
                        BreakdownRow(
                            label = "Запас сверх защитного резерва",
                            value = formatSignedFullCurrency(simulationResult.surplusAboveReserveRub),
                            valueColor = if (simulationResult.surplusAboveReserveRub < 0.0) Rose500 else Emerald400
                        )
                        BreakdownRow(
                            label = "Автономный запас хода (без свободных трат)",
                            value = "${simulationResult.autonomousSurvivalDaysAtZeroDisc} ${formatDaysWord(simulationResult.autonomousSurvivalDaysAtZeroDisc)}",
                            valueColor = Color.White
                        )
                        if (simulationResult.newlyExposedObligations.isNotEmpty()) {
                            HorizontalDivider(color = GlassBorder)
                            Text(
                                text = "Платежи, попавшие в окно до задержанной зарплаты:",
                                color = Amber400,
                                fontSize = 12.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            simulationResult.newlyExposedObligations.forEach { item ->
                                BreakdownRow(
                                    label = "${item.title} (${formatRussianShortDate(item.dueEpochDay)})",
                                    value = "−${formatFullCurrency(item.amountRub)}",
                                    valueColor = Amber400
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastSettingsSheetContent(
    result: SafeToSpendResult,
    userSafetyFloorRub: Double,
    userEssentialDailyNormRub: Double?,
    onSetUserSafetyFloor: (Double) -> Unit,
    onSetUserEssentialDailyNorm: (Double?) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var safetyInput by remember(userSafetyFloorRub) {
        mutableStateOf(if (userSafetyFloorRub > 0.0) userSafetyFloorRub.toInt().toString() else "")
    }
    var essentialInput by remember(userEssentialDailyNormRub, result.dailyEssentialRateRub) {
        mutableStateOf(
            userEssentialDailyNormRub?.toInt()?.toString()
                ?: result.dailyEssentialRateRub.toInt().toString()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Настройки прогноза",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF121A2B))
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Защитный резерв безопасности",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Неснижаемый остаток на счетах, который алгоритм никогда не распределяет в дневной бюджет.",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = safetyInput,
                        onValueChange = { safetyInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Сумма резерва, ₽", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onSetUserSafetyFloor(safetyInput.toDoubleOrNull() ?: 0.0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                    ) {
                        Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF121A2B))
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Базовые расходы на жизнь (еда, транспорт, аптека)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Текущий режим: ${localizeEssentialMethod(result.essentialEstimationMethod)}. Вы можете задать собственную дневную норму или оставить автоматический расчёт по истории трат.",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = essentialInput,
                        onValueChange = { essentialInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Норма в день, ₽", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onSetUserEssentialDailyNorm(essentialInput.toDoubleOrNull())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                    ) {
                        Text("Обновить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                if (userEssentialDailyNormRub != null) {
                    TextButton(
                        onClick = { onSetUserEssentialDailyNorm(null) }
                    ) {
                        Text(
                            text = "Сбросить к автоматическому расчёту по истории",
                            color = Indigo400,
                            fontSize = 12.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Slate300,
            fontSize = 12.sp,
            fontFamily = ManropeFontFamily,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}
