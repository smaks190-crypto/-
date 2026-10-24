package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AccountEntity
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionEntity
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.components.capitalizeFirstLetter
import com.example.ui.components.formatAmountTextFieldValue
import com.example.ui.components.formatDayHeaderLabel
import com.example.ui.components.formatFullCurrency
import com.example.ui.components.formatShortCurrency
import com.example.ui.components.parseAmountInput
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DebtsScreen(
    accounts: List<AccountEntity>, // Reused for debts
    transactions: List<TransactionEntity>,
    notifications: List<NotificationEntity>,
    onAddDebt: (name: String, initialAmount: Double, type: String, comment: String) -> Unit,
    onDeleteDebt: (debtId: String) -> Unit,
    onAddDebtTransaction: (type: String, date: String, category: String, subcategory: String, amount: Double, debtId: String) -> Unit,
    onOpenAddDebtModal: (() -> Unit)? = null,
    onOpenDebtDetailsModal: ((AccountEntity) -> Unit)? = null
) {
    var showAddDebtModal by remember { mutableStateOf(false) }
    var selectedDebtForDetails by remember { mutableStateOf<AccountEntity?>(null) }
    var selectedFilter by remember { mutableStateOf("all") } // "all", "we_owe", "owes_us"

    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Compute remaining debt balance for each entity
    fun getDebtRemainingBalance(debt: AccountEntity): Double {
        val txs = transactions.filter { it.accountId == debt.id }
        val isWeOwe = debt.type != "owes_us" // Default legacy type to "we_owe"

        return if (isWeOwe) {
            val income = txs.filter { it.type == "income" }.sumOf { it.amount }
            val expense = txs.filter { it.type == "expense" }.sumOf { it.amount }
            debt.balance + income - expense
        } else {
            val expense = txs.filter { it.type == "expense" }.sumOf { it.amount }
            val income = txs.filter { it.type == "income" }.sumOf { it.amount }
            debt.balance + expense - income
        }
    }

    val weOweList = remember(accounts) { accounts.filter { it.type != "owes_us" } }
    val owesUsList = remember(accounts) { accounts.filter { it.type == "owes_us" } }

    val filteredAccounts = remember(accounts, selectedFilter) {
        when (selectedFilter) {
            "we_owe" -> weOweList
            "owes_us" -> owesUsList
            else -> accounts
        }
    }

    // Calculate total summary of we_owe and owes_us debts
    val totalWeOwe = remember(accounts, transactions) {
        weOweList.sumOf { getDebtRemainingBalance(it).coerceAtLeast(0.0) }
    }
    val totalOwesUs = remember(accounts, transactions) {
        owesUsList.sumOf { getDebtRemainingBalance(it).coerceAtLeast(0.0) }
    }

    // Priority obligation for David's Smart Snowball strategy (smallest positive "we_owe" debt to close first)
    val nextSnowballDebt = remember(accounts, transactions) {
        weOweList
            .map { it to getDebtRemainingBalance(it) }
            .filter { it.second > 0.0 }
            .minByOrNull { it.second }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. OBSIDIAN NEO-BANK HEADER CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(GlassHighlight, GlassBorder))
                ),
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ОБЯЗАТЕЛЬСТВА И ЗАЙМЫ",
                                color = Slate400,
                                fontFamily = ManropeFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Контроль долгов",
                                color = Color.White,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (onOpenAddDebtModal != null) {
                                    onOpenAddDebtModal()
                                } else {
                                    showAddDebtModal = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("add_debt_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить долг",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Долг",
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Dual Summary Bento Row ("Я должен" / "Мне должны")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card for "Я должен" (We owe)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(ObsidianElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (selectedFilter == "we_owe") Rose500 else Rose500.copy(alpha = 0.32f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = if (selectedFilter == "we_owe") "all" else "we_owe"
                                }
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Rose500.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = Rose500,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = "Я ДОЛЖЕН",
                                        color = Slate400,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                                RollingCurrencyText(
                                    text = formatFullCurrency(totalWeOwe),
                                    color = if (totalWeOwe > 0) Rose400 else Color.White,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Card for "Мне должны" (Owed to us)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(ObsidianElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (selectedFilter == "owes_us") Emerald400 else Emerald400.copy(alpha = 0.32f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = if (selectedFilter == "owes_us") "all" else "owes_us"
                                }
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(Emerald400.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = Emerald400,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = "МНЕ ДОЛЖНЫ",
                                        color = Slate400,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                                RollingCurrencyText(
                                    text = formatFullCurrency(totalOwesUs),
                                    color = if (totalOwesUs > 0) Emerald400 else Color.White,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // David AI Debt Strategy Hint (if active debt exists)
                    nextSnowballDebt?.let { (priorityDebt, remAmount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Indigo500.copy(alpha = 0.12f))
                                .border(1.dp, Indigo400.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .clickable {
                                    if (onOpenDebtDetailsModal != null) {
                                        onOpenDebtDetailsModal(priorityDebt)
                                    } else {
                                        selectedDebtForDetails = priorityDebt
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Indigo400,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "🐸 Совет Давида (Метод снежного кома): закройте «${priorityDebt.name}» (${formatFullCurrency(remAmount)}) первым для быстрой разгрузки бюджета.",
                                color = Slate300,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Segmented Filter Pills (when accounts exist)
            if (accounts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        Triple("all", "Все", accounts.size),
                        Triple("we_owe", "Я должен", weOweList.size),
                        Triple("owes_us", "Мне должны", owesUsList.size)
                    )
                    tabs.forEach { (key, title, count) ->
                        val isSelected = selectedFilter == key
                        val tabAccent = when (key) {
                            "we_owe" -> Rose500
                            "owes_us" -> Emerald400
                            else -> Indigo400
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) tabAccent.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) tabAccent.copy(alpha = 0.45f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = key
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$count",
                                    color = if (isSelected) tabAccent else Slate500,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2. DEBTS LIST
            if (filteredAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ObsidianSurface)
                                .border(1.dp, GlassBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handshake,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = if (accounts.isEmpty()) "Долгов и активных займов нет" else "В этой категории нет обязательств",
                            color = Color.White,
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Чистый баланс обязательств — лучшая база для накоплений",
                            color = Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredAccounts, key = { it.id }) { debt ->
                        val remaining = getDebtRemainingBalance(debt)
                        DebtCard(
                            debt = debt,
                            remainingBalance = remaining,
                            onDelete = { onDeleteDebt(debt.id) },
                            onClick = {
                                if (onOpenDebtDetailsModal != null) {
                                    onOpenDebtDetailsModal(debt)
                                } else {
                                    selectedDebtForDetails = debt
                                }
                            },
                            onQuickRepay = { payAmount ->
                                val isWeOwe = debt.type != "owes_us"
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                val txType = if (isWeOwe) "expense" else "income"
                                val subcat = if (isWeOwe) "Гашение: ${debt.name}" else "Возврат долга: ${debt.name}"
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAddDebtTransaction(txType, today, "Долги", subcat, payAmount, debt.id)
                                Toast.makeText(
                                    context,
                                    "🐸 Зафиксирован платеж ${formatFullCurrency(payAmount)} по «${debt.name}»",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Debt Dialog
    if (showAddDebtModal) {
        AddDebtDialog(
            onDismiss = { showAddDebtModal = false },
            onConfirm = { name, amount, type, comment ->
                onAddDebt(name, amount, type, comment)
                showAddDebtModal = false
            }
        )
    }

    // Details / Repayment Dialog
    selectedDebtForDetails?.let { debt ->
        val remaining = getDebtRemainingBalance(debt)
        DebtDetailsDialog(
            debt = debt,
            remainingBalance = remaining,
            transactions = transactions.filter { it.accountId == debt.id },
            onDismiss = { selectedDebtForDetails = null },
            onAddPayment = { type, date, amt, subcat ->
                onAddDebtTransaction(type, date, "Долги", subcat, amt, debt.id)
            }
        )
    }
}

@Composable
fun DebtCard(
    debt: AccountEntity,
    remainingBalance: Double,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onQuickRepay: ((Double) -> Unit)? = null
) {
    val isWeOwe = debt.type != "owes_us"
    val accentColor = if (isWeOwe) Rose500 else Emerald400
    val directionLabel = if (isWeOwe) "Я должен" else "Мне должны"
    val icon = if (isWeOwe) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    val safeInitial = debt.balance.coerceAtLeast(1.0)
    val safeRemaining = remainingBalance.coerceAtLeast(0.0)
    val repaidAmount = (debt.balance - safeRemaining).coerceAtLeast(0.0)
    val repaidFraction = if (debt.balance > 0.0) {
        (repaidAmount / safeInitial).toFloat().coerceIn(0f, 1f)
    } else if (safeRemaining <= 0.0) 1f else 0f
    val animatedRepaidFraction by animateFloatAsState(
        targetValue = repaidFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "debt_repaid_fraction"
    )
    val repaidPercent = (repaidFraction * 100f).roundToInt().coerceIn(0, 100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.35f), GlassBorder)
            )
        )
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            // Subtle Radial Glow inside card
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-24).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentColor.copy(alpha = 0.14f))
                                .border(1.dp, accentColor.copy(alpha = 0.32f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = debt.name,
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (debt.accountNumber.isNotBlank()) "$directionLabel • ${debt.accountNumber}" else directionLabel,
                                color = Slate400,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (remainingBalance <= 0.0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Emerald400.copy(alpha = 0.16f))
                                    .border(1.dp, Emerald400.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "ЗАКРЫТ",
                                    color = Emerald400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianElevated)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$repaidPercent% погашено",
                                    color = Slate300,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить долг",
                                tint = Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Остаток долга",
                            color = Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        RollingCurrencyText(
                            text = formatFullCurrency(safeRemaining),
                            color = if (remainingBalance <= 0.0) Slate400 else Color.White,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Из " + formatFullCurrency(debt.balance),
                        color = Slate400,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Glowing Repayment Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(DarkBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRepaidFraction)
                            .height(6.dp)
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(3.dp),
                                ambientColor = accentColor,
                                spotColor = accentColor
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (remainingBalance <= 0.0) Emerald400 else accentColor
                            )
                    )
                }

                // 1-Tap Quick Repayment Chips directly on active DebtCard
                if (safeRemaining > 0.0 && onQuickRepay != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val quickOptions = remember(safeRemaining) {
                            buildList {
                                if (safeRemaining > 1000.0) add(1000.0 to "+1 000 ₽")
                                if (safeRemaining > 5000.0) add(5000.0 to "+5 000 ₽")
                                add(safeRemaining to "Закрыть 100% (${formatShortCurrency(safeRemaining)})")
                            }
                        }
                        quickOptions.forEach { (amt, label) ->
                            val isFullClose = amt >= safeRemaining
                            Surface(
                                modifier = Modifier
                                    .weight(if (isFullClose) 1.4f else 1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onQuickRepay(amt) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isFullClose) Emerald400.copy(alpha = 0.14f) else DarkBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isFullClose) Emerald400.copy(alpha = 0.4f) else GlassBorder
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isFullClose) Emerald400 else Slate300,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

@Composable
fun AddDebtDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, type: String, comment: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedType by remember { mutableStateOf("we_owe") } // "we_owe" or "owes_us"
    var comment by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val accentColor = if (selectedType == "we_owe") Rose500 else Emerald400

    val commentPresets = remember {
        listOf("До зарплаты", "До конца месяца", "Кредитка / Грейс", "Без процентов")
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .padding(22.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(GlassHighlight)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "УЧЕТ ОБЯЗАТЕЛЬСТВ",
                        color = accentColor,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Новое обязательство",
                        color = Color.White,
                        fontFamily = ManropeFontFamily,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

                    // Segmented Type Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBg, RoundedCornerShape(14.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val types = listOf(
                            "we_owe" to "Я должен",
                            "owes_us" to "Мне должны"
                        )
                        types.forEach { (typeKey, label) ->
                            val isSelected = selectedType == typeKey
                            val activeColor = if (typeKey == "we_owe") Rose500 else Emerald400
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) activeColor else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedType = typeKey }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Name input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ИМЯ КРЕДИТОРА / ДЕБИТОРА",
                            color = Slate400,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.capitalizeFirstLetter() },
                            placeholder = { Text("например: Иван, Т-Банк", color = Slate500) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Amount input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "СУММА ДОЛГА (₽)",
                            color = Slate400,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                            placeholder = { Text("0", color = Slate500) },
                            suffix = {
                                Text(
                                    text = "₽",
                                    color = accentColor,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    // Comment / Deadline input + 1-Tap Presets
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "СРОК / КОММЕНТАРИЙ",
                            color = Slate400,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            commentPresets.forEach { preset ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { comment = preset },
                                    shape = RoundedCornerShape(10.dp),
                                    color = ObsidianElevated,
                                    border = BorderStroke(1.dp, GlassBorder)
                                ) {
                                    Text(
                                        text = preset,
                                        color = Slate300,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it.capitalizeFirstLetter() },
                            placeholder = { Text("например: до конца декабря", color = Slate500) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg,
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    val isFormValid = name.isNotBlank() && parseAmountInput(amountText.text) > 0.0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val amt = parseAmountInput(amountText.text)
                                if (isFormValid) {
                                    keyboardController?.hide()
                                    onConfirm(name.trim(), amt, selectedType, comment.trim())
                                }
                            },
                            enabled = isFormValid,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .shadow(
                                    elevation = if (isFormValid) 10.dp else 0.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = accentColor,
                                    spotColor = accentColor
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                disabledContainerColor = ObsidianElevated,
                                contentColor = if (selectedType == "owes_us") DarkBg else Color.White,
                                disabledContentColor = Slate500
                            )
                        ) {
                            Text(
                                text = "Создать обязательство",
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Slate800)
                                .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                                .clickable { onDismiss() }
                                .testTag("close_add_debt_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Slate400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
}

@Composable
fun DebtDetailsDialog(
    debt: AccountEntity,
    remainingBalance: Double,
    transactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onAddPayment: (type: String, date: String, amount: Double, subcategory: String) -> Unit
) {
    val isWeOwe = debt.type != "owes_us"
    val accentColor = if (isWeOwe) Rose500 else Emerald400

    var paymentAmountText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedOperationAction by remember { mutableStateOf(if (isWeOwe) "repay" else "they_repaid") }

    val keyboardController = LocalSoftwareKeyboardController.current
    val safeRemaining = remainingBalance.coerceAtLeast(0.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(GlassHighlight)
            )
        }

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isWeOwe) "ДЕТАЛИ ДОЛГА • Я ДОЛЖЕН" else "ДЕТАЛИ ЗАЙМА • МНЕ ДОЛЖНЫ",
                    color = accentColor,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = debt.name,
                    color = Color.White,
                    fontFamily = ManropeFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Остаток: " + formatFullCurrency(safeRemaining),
                    color = accentColor,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

                // Form: Register Transaction (Repayment / Adjustment)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianElevated),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "НОВАЯ ОПЕРАЦИЯ ПО ДОЛГУ",
                            color = Slate400,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Operation Type selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkBg, RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val actions = if (isWeOwe) {
                                listOf(
                                    "repay" to "Погасить (Расход)",
                                    "borrow_more" to "Взять еще (Доход)"
                                )
                            } else {
                                listOf(
                                    "they_repaid" to "Вернули (Доход)",
                                    "lend_more" to "Дать еще (Расход)"
                                )
                            }
                            actions.forEach { (actionKey, label) ->
                                val isSelected = selectedOperationAction == actionKey
                                val isPositiveReduction = actionKey == "repay" || actionKey == "they_repaid"
                                val localColor = if (isPositiveReduction) Emerald400 else Rose500
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) localColor.copy(alpha = 0.16f) else Color.Transparent)
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) localColor else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedOperationAction = actionKey }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) localColor else Slate400,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Quick Percentage Fill Chips (25%, 50%, 100%)
                        if (safeRemaining > 0.0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(0.25 to "25%", 0.5 to "50%", 1.0 to "100% остатка").forEach { (fraction, label) ->
                                    val calcAmt = kotlin.math.ceil(safeRemaining * fraction).toLong()
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val formatted = String.format(Locale.US, "%,d", calcAmt).replace(',', ' ')
                                                paymentAmountText = TextFieldValue(
                                                    text = formatted,
                                                    selection = TextRange(formatted.length)
                                                )
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkBg,
                                        border = BorderStroke(1.dp, GlassBorder)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = Emerald400,
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Amount field and Insert button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = paymentAmountText,
                                onValueChange = { paymentAmountText = formatAmountTextFieldValue(paymentAmountText, it) },
                                placeholder = { Text("Сумма в ₽", color = Slate500, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = GlassBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            val enteredAmount = parseAmountInput(paymentAmountText.text)
                            val isAmtValid = enteredAmount > 0.0

                            Button(
                                onClick = {
                                    if (isAmtValid) {
                                        keyboardController?.hide()
                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                                        val (txType, subcat) = when (selectedOperationAction) {
                                            "repay" -> Pair("expense", "Гашение: ${debt.name}")
                                            "borrow_more" -> Pair("income", "Взял еще в долг: ${debt.name}")
                                            "they_repaid" -> Pair("income", "Возврат долга: ${debt.name}")
                                            "lend_more" -> Pair("expense", "Дал еще в долг: ${debt.name}")
                                            else -> Pair("expense", "Гашение: ${debt.name}")
                                        }

                                        onAddPayment(txType, today, enteredAmount, subcat)
                                        paymentAmountText = TextFieldValue("")
                                    }
                                },
                                enabled = isAmtValid,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    disabledContainerColor = DarkBg
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("submit_debt_tx_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = if (isWeOwe) Color.White else DarkBg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Внести",
                                    color = if (isWeOwe) Color.White else DarkBg,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // History List Title
                Text(
                    text = "ИСТОРИЯ ПЛАТЕЖЕЙ И ИЗМЕНЕНИЙ",
                    color = Slate400,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (transactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет истории платежей по этому обязательству",
                            color = Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactions) { tx ->
                            val isIncome = tx.type == "income"
                            val outcomeColor = if (isWeOwe) {
                                if (tx.type == "expense") Emerald400 else Rose500
                            } else {
                                if (tx.type == "income") Emerald400 else Rose500
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = ObsidianElevated),
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(outcomeColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = outcomeColor,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tx.subcategory.ifBlank { tx.category },
                                                color = Color.White,
                                                fontFamily = ManropeFontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatDayHeaderLabel(tx.date),
                                                color = Slate400,
                                                fontFamily = ManropeFontFamily,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = (if (isIncome) "+ " else "- ") + formatFullCurrency(tx.amount),
                                        color = outcomeColor,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Unified bottom action row with 52.dp square ✕ close button on the right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val enteredAmtBottom = parseAmountInput(paymentAmountText.text)
                    val isBottomAmtValid = enteredAmtBottom > 0.0
                    Button(
                        onClick = {
                            if (isBottomAmtValid) {
                                keyboardController?.hide()
                                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                val (txType, subcat) = when (selectedOperationAction) {
                                    "repay" -> Pair("expense", "Гашение: ${debt.name}")
                                    "borrow_more" -> Pair("income", "Взял еще в долг: ${debt.name}")
                                    "they_repaid" -> Pair("income", "Возврат долга: ${debt.name}")
                                    "lend_more" -> Pair("expense", "Дал еще в долг: ${debt.name}")
                                    else -> Pair("expense", "Гашение: ${debt.name}")
                                }
                                onAddPayment(txType, today, enteredAmtBottom, subcat)
                                paymentAmountText = TextFieldValue("")
                                onDismiss()
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBottomAmtValid) accentColor else ObsidianElevated
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (isBottomAmtValid) "Внести платеж и закрыть" else "Готово",
                            color = if (isBottomAmtValid && !isWeOwe) DarkBg else Color.White,
                            fontFamily = ManropeFontFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Slate800)
                            .border(1.dp, Slate700, RoundedCornerShape(16.dp))
                            .clickable { onDismiss() }
                            .testTag("close_debt_details_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Slate400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
    }
}
