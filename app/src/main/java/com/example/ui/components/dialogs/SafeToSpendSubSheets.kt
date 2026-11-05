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


@Composable
internal fun RecurringPaymentsManagerSheetContent(
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
    ) -> Unit,
    onToggleRecurringStatus: (Long, String) -> Unit,
    onDeleteRecurringPayment: (Long) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFilter by remember { mutableIntStateOf(0) } // 0 = Все, 1 = Платежи, 2 = Подписки, 3 = Доходы
    var showAddForm by remember { mutableStateOf(false) }
    var editingPaymentId by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

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
                                text = if (editingPaymentId != null) {
                                    "Редактировать регулярную операцию"
                                } else {
                                    "Новый регулярный платёж / доход"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Скрыть",
                                color = Slate400,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    editingPaymentId = null
                                    showAddForm = false
                                }
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
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ObsidianBentoDateCard(
                                dateIso = nextDueDateIso,
                                accentColor = if (type == "INCOME") Emerald400 else Rose500,
                                onClick = { showDatePicker = true },
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
                                    val targetId = editingPaymentId
                                    if (targetId != null) {
                                        onUpdateRecurringPayment(
                                            targetId,
                                            title.ifBlank { defaultCategory },
                                            amt,
                                            type,
                                            defaultCategory,
                                            "MONTHLY",
                                            nextDueDateIso,
                                            priority,
                                            "GUARANTEED"
                                        )
                                    } else {
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
                                    }
                                    // Ensure newly added/edited item is immediately visible if a mismatched filter tab was selected
                                    val matchesCurrentFilter = when (selectedFilter) {
                                        1 -> type != "INCOME" && priority == "MANDATORY"
                                        2 -> type != "INCOME" && priority == "DISCRETIONARY_SUBSCRIPTION"
                                        3 -> type == "INCOME"
                                        else -> true
                                    }
                                    if (!matchesCurrentFilter) {
                                        selectedFilter = 0
                                    }
                                    editingPaymentId = null
                                    title = ""
                                    amountText = ""
                                    showAddForm = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(64.dp)
                            ) {
                                Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    val openEditForItem = {
                        editingPaymentId = item.id
                        title = item.title
                        amountText = if (item.amount % 1.0 == 0.0) {
                            item.amount.toLong().toString()
                        } else {
                            item.amount.toString()
                        }
                        type = if (item.isIncome) "INCOME" else "EXPENSE"
                        priority = item.priority
                        nextDueDateIso = runCatching {
                            LocalDate.ofEpochDay(item.nextDueDateEpochDay).toString()
                        }.getOrDefault(LocalDate.ofEpochDay(todayEpochDay).toString())
                        showAddForm = true
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF121A2B))
                            .border(
                                width = 1.dp,
                                color = when {
                                    editingPaymentId == item.id -> Emerald400.copy(alpha = 0.65f)
                                    isOverdue -> Rose500.copy(alpha = 0.5f)
                                    else -> GlassBorder
                                },
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { openEditForItem() }
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
                                        text = if (item.isIncome) "Регулярный доход • нажмите, чтобы изменить" else "${item.categoryName} • нажмите, чтобы изменить",
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    onClick = { openEditForItem() },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Изменить",
                                        tint = Slate400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (editingPaymentId == item.id) {
                                            editingPaymentId = null
                                            showAddForm = false
                                        }
                                        onDeleteRecurringPayment(item.id)
                                    },
                                    modifier = Modifier.size(26.dp)
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
            onClick = {
                if (showAddForm && editingPaymentId != null) {
                    editingPaymentId = null
                    title = ""
                    amountText = ""
                } else {
                    editingPaymentId = null
                    title = ""
                    amountText = ""
                    showAddForm = !showAddForm
                }
            },
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

    if (showDatePicker) {
        SingleDatePickerDialog(
            initialDate = nextDueDateIso,
            onDismiss = { showDatePicker = false },
            onConfirm = { selected ->
                nextDueDateIso = selected
            }
        )
    }
}

internal fun resolveRecurringVisuals(item: RecurringPaymentEntity): Pair<Color, ImageVector> {
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
internal fun WhatIfSimulatorSheetContent(
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
internal fun ForecastSettingsSheetContent(
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
internal fun BreakdownRow(label: String, value: String, valueColor: Color) {
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
