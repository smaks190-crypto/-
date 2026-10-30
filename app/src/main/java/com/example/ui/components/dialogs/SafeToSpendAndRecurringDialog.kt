package com.example.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.RecurringPaymentEntity
import com.example.data.safetospend.*
import com.example.ui.components.formatFullCurrency
import com.example.ui.components.formatShortCurrency
import com.example.ui.theme.*
import java.time.LocalDate

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
    var activeTab by remember { mutableIntStateOf(initialTab) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp)),
            color = DarkBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Adaptive Safe-to-Spend 2.0",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Прогноз ликвидности • Регулярные платежи • What-if",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontFamily = ManropeFontFamily
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Slate300
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("Прогноз", "Платежи (${recurringPayments.size})", "What-if")
                    tabs.forEachIndexed { idx, label ->
                        val selected = activeTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Emerald400.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { activeTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Emerald400 else Slate300,
                                fontSize = 12.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        0 -> ForecastBreakdownTab(
                            result = safeToSpendResult,
                            userSafetyFloorRub = userSafetyFloorRub,
                            userEssentialDailyNormRub = userEssentialDailyNormRub,
                            onSetUserSafetyFloor = onSetUserSafetyFloor,
                            onSetUserEssentialDailyNorm = onSetUserEssentialDailyNorm
                        )
                        1 -> RecurringPaymentsTab(
                            recurringPayments = recurringPayments,
                            todayEpochDay = safeToSpendInput.todayEpochDay,
                            onAddRecurringPayment = onAddRecurringPayment,
                            onConfirmRecurringPayment = onConfirmRecurringPayment,
                            onToggleRecurringStatus = onToggleRecurringStatus,
                            onDeleteRecurringPayment = onDeleteRecurringPayment
                        )
                        2 -> WhatIfSimulatorTab(
                            safeToSpendInput = safeToSpendInput
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastBreakdownTab(
    result: SafeToSpendResult,
    userSafetyFloorRub: Double,
    userEssentialDailyNormRub: Double?,
    onSetUserSafetyFloor: (Double) -> Unit,
    onSetUserEssentialDailyNorm: (Double?) -> Unit
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

    val isReserveDeficit = !result.isReserveRequirementMet

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        text = "⚠️ Низкая достоверность прогноза базовых расходов",
                        color = Amber400,
                        fontSize = 13.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "История трат на продукты, транспорт и аптеку менее 7 дней, а месячные лимиты категорий не заданы. Временно используется ориентир ${result.dailyEssentialRateRub.toInt()} ₽/день. Укажите свой ориентир базовых расходов на день:",
                        color = Slate200,
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
                            label = { Text("База ₽/день", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val parsed = essentialInput.toDoubleOrNull()
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "МОЖНО ПОТРАТИТЬ СЕГОДНЯ (ДИСКРЕЦИОННО)",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatFullCurrency(result.canSpendTodayDiscRub),
                    color = if (result.hasCashGap || isReserveDeficit || result.todayOverspendRub > 0) Rose500 else Emerald400,
                    fontSize = 28.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
                if (result.todayOverspendRub > 0.0) {
                    Text(
                        text = "Перерасход сегодня: ${formatFullCurrency(result.todayOverspendRub)}",
                        color = Rose500,
                        fontSize = 12.sp,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                HorizontalDivider(color = GlassBorder)
                BreakdownRow("Остаток на базовые нужды сегодня", formatFullCurrency(result.canSpendTodayEssRub), Emerald400)
                BreakdownRow("Безопасный дневной темп (S_disc)", "${formatShortCurrency(result.dailyDiscretionarySpeedRub)}/дн.", Color.White)
                BreakdownRow("Базовые расходы (e_ess)", "${formatShortCurrency(result.dailyEssentialRateRub)}/дн.", Slate200)
                BreakdownRow("Горизонт бутылочного горлышка (k*)", "${result.bottleneckDayOffset} дн.", Indigo400)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "СТРУКТУРА ЛИКВИДНОСТИ И ОБЯЗАТЕЛЬСТВ",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                BreakdownRow("Доступная ликвидность (L_0)", formatFullCurrency(result.availableBalanceRub), Color.White)
                BreakdownRow("Неснижаемый резерв (R_floor)", formatFullCurrency(result.mandatorySafetyFloorRub), Slate300)
                if (isReserveDeficit) {
                    BreakdownRow(
                        "Дефицит резерва (D_reserve)",
                        formatFullCurrency(result.reserveDeficitRub),
                        Rose500
                    )
                }
                if (result.overdueObligationsRub > 0.0) {
                    BreakdownRow(
                        "Бронь просроченных платежей (O_overdue)",
                        formatFullCurrency(result.overdueObligationsRub),
                        Amber400
                    )
                }
                if (result.hasCashGap) {
                    val gapDate = result.cashGapEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "в горизонте"
                    BreakdownRow(
                        "Кассовый разрыв ($gapDate)",
                        formatFullCurrency(result.cashGapAmountRub),
                        Rose500
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "НАСТРОЙКИ ПРОГНОЗА",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = safetyInput,
                        onValueChange = { safetyInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Мин. резерв (R_floor), ₽", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onSetUserSafetyFloor(safetyInput.toDoubleOrNull() ?: 0.0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                    ) {
                        Text("Задать", color = Color.White, fontSize = 12.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = essentialInput,
                        onValueChange = { essentialInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Базовые расходы ₽/день", fontSize = 11.sp) },
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
            }
        }
    }
}

@Composable
private fun RecurringPaymentsTab(
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
    onConfirmRecurringPayment: (Long) -> Unit,
    onToggleRecurringStatus: (Long, String) -> Unit,
    onDeleteRecurringPayment: (Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var priority by remember { mutableStateOf("MANDATORY") }
    var nextDueDateIso by remember {
        mutableStateOf(LocalDate.ofEpochDay(todayEpochDay).toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    text = "ДОБАВИТЬ РЕГУЛЯРНЫЙ ПЛАТЁЖ / ДОХОД",
                    color = Slate300,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        label = { Text("Расход", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME" },
                        label = { Text("Доход (ЗП/Аванс)", fontSize = 11.sp) }
                    )
                    if (type == "EXPENSE") {
                        FilterChip(
                            selected = priority == "MANDATORY",
                            onClick = {
                                priority = if (priority == "MANDATORY") "DISCRETIONARY_SUBSCRIPTION" else "MANDATORY"
                            },
                            label = {
                                Text(
                                    if (priority == "MANDATORY") "Обязательный" else "Подписка",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Название (Аренда, ЗП)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Сумма ₽", fontSize = 11.sp) },
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
                        label = { Text("След. дата (ГГГГ-ММ-ДД)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: return@Button
                            if (amt <= 0.0) return@Button
                            val defaultCategory = if (type == "INCOME") "Зарплата" else "ЖКХ и Аренда"
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
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Добавить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (recurringPayments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет активных регулярных платежей. Добавьте аренду, ЖКХ, подписки или зарплату.",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily
                )
            }
        } else {
            recurringPayments.forEach { item ->
                val dueIso = LocalDate.ofEpochDay(item.nextDueDateEpochDay).toString()
                val isOverdue = !item.isIncome &&
                    item.status == "ACTIVE" &&
                    item.nextDueDateEpochDay < todayEpochDay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ObsidianSurface)
                        .border(
                            1.dp,
                            if (isOverdue) Rose500.copy(alpha = 0.5f) else GlassBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = buildString {
                                        append(if (item.isIncome) "Доход" else "Расход")
                                        append(" • След. дата: $dueIso")
                                        if (isOverdue) append(" • ПРОСРОЧЕНО")
                                        if (item.status != "ACTIVE") append(" • ПАУЗА")
                                    },
                                    color = if (isOverdue) Rose500 else Slate400,
                                    fontSize = 11.sp,
                                    fontFamily = ManropeFontFamily
                                )
                            }
                            Text(
                                text = (if (item.isIncome) "+" else "-") + formatFullCurrency(item.amount),
                                color = if (item.isIncome) Emerald400 else Color.White,
                                fontSize = 14.sp,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onConfirmRecurringPayment(item.id) }) {
                                Text("Подтвердить факт", color = Emerald400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { onToggleRecurringStatus(item.id, item.status) }) {
                                Text(
                                    if (item.status == "ACTIVE") "Пауза" else "Включить",
                                    color = Slate300,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = { onDeleteRecurringPayment(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = Rose500,
                                    modifier = Modifier.size(18.dp)
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
private fun WhatIfSimulatorTab(
    safeToSpendInput: SafeToSpendInput
) {
    var scenarioMode by remember { mutableIntStateOf(0) } // 0 = Покупка, 1 = Задержка дохода, 2 = До зарплаты
    var hypotheticalPurchaseText by remember { mutableStateOf("3000") }
    var salaryDelayDaysText by remember { mutableStateOf("5") }
    var plannedDailyText by remember { mutableStateOf("1000") }

    val simulationResult = remember(
        safeToSpendInput,
        scenarioMode,
        hypotheticalPurchaseText,
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
                streamType = ExpenseStreamType.DISCRETIONARY,
                label = "Разовая покупка"
            )
        }
        WhatIfSimulator.simulate(
            baseInput = safeToSpendInput,
            request = request
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    text = "СИМУЛЯТОР СЦЕНАРИЕВ WHAT-IF (БЕЗ ЗАПИСИ В БД)",
                    color = Slate300,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = scenarioMode == 0,
                        onClick = { scenarioMode = 0 },
                        label = { Text("Покупка ₽", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = scenarioMode == 1,
                        onClick = { scenarioMode = 1 },
                        label = { Text("Задержка ЗП", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = scenarioMode == 2,
                        onClick = { scenarioMode = 2 },
                        label = { Text("До зарплаты", fontSize = 11.sp) }
                    )
                }
                when (scenarioMode) {
                    1 -> OutlinedTextField(
                        value = salaryDelayDaysText,
                        onValueChange = { salaryDelayDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Задержка дохода, дней", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    2 -> OutlinedTextField(
                        value = plannedDailyText,
                        onValueChange = { plannedDailyText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Планируемый дневной расход, ₽/день", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> OutlinedTextField(
                        value = hypotheticalPurchaseText,
                        onValueChange = { hypotheticalPurchaseText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Разовая покупка сегодня, ₽", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        val verdictColor = when (simulationResult.verdict) {
            WhatIfVerdict.SAFE_WITHIN_TODAY_LIMIT -> Emerald400
            WhatIfVerdict.REDUCES_FUTURE_DAILY_BUDGET -> Amber400
            WhatIfVerdict.RESERVE_BREACH, WhatIfVerdict.CASH_GAP_RISK -> Rose500
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, verdictColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ВЕРДИКТ ДАВИДА: ${simulationResult.verdict.name}",
                    color = verdictColor,
                    fontSize = 13.sp,
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = simulationResult.structuredSummaryForDavid,
                    color = Slate200,
                    fontSize = 12.sp,
                    fontFamily = ManropeFontFamily
                )
                HorizontalDivider(color = GlassBorder)
                BreakdownRow(
                    "Можно потратить сегодня (до → после)",
                    "${formatShortCurrency(simulationResult.baseline.canSpendTodayDiscRub)} → ${formatShortCurrency(simulationResult.simulated.canSpendTodayDiscRub)}",
                    verdictColor
                )
                BreakdownRow(
                    "Дневной темп (до → после)",
                    "${formatShortCurrency(simulationResult.baseline.dailyDiscretionarySpeedRub)}/дн. → ${formatShortCurrency(simulationResult.simulated.dailyDiscretionarySpeedRub)}/дн.",
                    Color.White
                )
                BreakdownRow(
                    "Изменение дневного лимита",
                    "${if (simulationResult.deltaDailyDiscretionarySpeedRub >= 0) "+" else ""}${simulationResult.deltaDailyDiscretionarySpeedRub.toInt()} ₽/дн.",
                    verdictColor
                )
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
            fontFamily = ManropeFontFamily
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}
