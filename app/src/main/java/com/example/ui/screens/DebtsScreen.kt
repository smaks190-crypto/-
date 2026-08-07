package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import com.example.ui.components.capitalizeFirstLetter
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AccountEntity
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionEntity
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.components.formatAmountTextFieldValue
import com.example.ui.components.formatFullCurrency
import com.example.ui.components.formatDayHeaderLabel
import com.example.ui.components.parseAmountInput
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebtsScreen(
    accounts: List<AccountEntity>, // Reused for debts
    transactions: List<TransactionEntity>,
    notifications: List<NotificationEntity>,
    onAddDebt: (name: String, initialAmount: Double, type: String, comment: String) -> Unit,
    onDeleteDebt: (debtId: String) -> Unit,
    onAddDebtTransaction: (type: String, date: String, category: String, subcategory: String, amount: Double, debtId: String) -> Unit
) {
    var showAddDebtModal by remember { mutableStateOf(false) }
    var selectedDebtForDetails by remember { mutableStateOf<AccountEntity?>(null) }

    val focusManager = LocalFocusManager.current

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

    // Calculate total summary of we_owe and owes_us debts
    val totalWeOwe = remember(accounts, transactions) {
        accounts.filter { it.type != "owes_us" }.sumOf { getDebtRemainingBalance(it).coerceAtLeast(0.0) }
    }
    val totalOwesUs = remember(accounts, transactions) {
        accounts.filter { it.type == "owes_us" }.sumOf { getDebtRemainingBalance(it).coerceAtLeast(0.0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ДОЛГИ И ЗАЙМЫ",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Контроль задолженностей",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { showAddDebtModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .border(1.dp, Indigo500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .testTag("add_debt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить долг",
                        tint = Indigo500,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Долг", color = Indigo500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Dual Summary Card (Ya Dolzhen / Mne Dolzhny)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card for "Я должен" (We owe)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            ambientColor = Rose500,
                            spotColor = Rose500
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Я ДОЛЖЕН",
                                color = Slate400,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        RollingCurrencyText(
                            text = formatFullCurrency(totalWeOwe),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Card for "Мне должны" (Owed to us)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            ambientColor = Emerald400,
                            spotColor = Emerald400
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "МНЕ ДОЛЖНЫ",
                                color = Slate400,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        RollingCurrencyText(
                            text = formatFullCurrency(totalOwesUs),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Debts List
            if (accounts.isEmpty()) {
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
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Rose500.copy(alpha = 0.1f))
                                .border(1.dp, Rose500.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "•︵•",
                                color = Rose500,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "У вас отсутствуют долги",
                            color = Slate400,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(accounts) { debt ->
                        val remaining = getDebtRemainingBalance(debt)
                        DebtCard(
                            debt = debt,
                            remainingBalance = remaining,
                            onDelete = { onDeleteDebt(debt.id) },
                            onClick = { selectedDebtForDetails = debt }
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
    onClick: () -> Unit
) {
    val isWeOwe = debt.type != "owes_us"
    val accentColor = if (isWeOwe) Rose500 else Emerald400
    val directionLabel = if (isWeOwe) "Я должен" else "Мне должны"
    val icon = if (isWeOwe) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
                ambientColor = accentColor,
                spotColor = accentColor
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.85f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            // Subtle Neon Glow inside card
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.15f),
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = debt.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (debt.accountNumber.isNotBlank()) "$directionLabel • ${debt.accountNumber}" else directionLabel,
                                color = Slate400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (remainingBalance <= 0.0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Emerald400.copy(alpha = 0.2f))
                                    .border(1.dp, Emerald400.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ПОГАШЕН",
                                    color = Emerald400,
                                    fontSize = 9.sp,
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
                            text = "Оставшийся долг",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        RollingCurrencyText(
                            text = formatFullCurrency(remainingBalance.coerceAtLeast(0.0)),
                            color = if (remainingBalance <= 0.0) Slate400 else Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Начальный: " + formatFullCurrency(debt.balance),
                        color = Slate400.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
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

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Новое обязательство",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = Slate400)
                    }
                }

                // Segmented Type Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) activeColor.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) activeColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedType = typeKey }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) activeColor else Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Name input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Имя кредитора / дебитора", color = Slate400, fontSize = 11.sp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.capitalizeFirstLetter() },
                        placeholder = { Text("например: Иван, Сбербанк", color = Slate700) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg,
                            focusedBorderColor = if (selectedType == "we_owe") Rose500 else Emerald400,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Amount input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Сумма долга (₽)", color = Slate400, fontSize = 11.sp)
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                        placeholder = { Text("0", color = Slate700) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg,
                            focusedBorderColor = if (selectedType == "we_owe") Rose500 else Emerald400,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Comment / Deadline input
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Срок / Комментарий", color = Slate400, fontSize = 11.sp)
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it.capitalizeFirstLetter() },
                        placeholder = { Text("например: до конца декабря", color = Slate700) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg,
                            focusedBorderColor = if (selectedType == "we_owe") Rose500 else Emerald400,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                val isFormValid = name.isNotBlank() && parseAmountInput(amountText.text) > 0.0
                val accentColor = if (selectedType == "we_owe") Rose500 else Emerald400

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
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(
                            elevation = if (isFormValid) 10.dp else 0.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = accentColor,
                            spotColor = accentColor
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = Slate800,
                        contentColor = Color.White,
                        disabledContentColor = Slate400
                    )
                ) {
                    Text("Создать обязательство", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
    var selectedOperationAction by remember { mutableStateOf(if (isWeOwe) "repay" else "they_repaid") } // "repay"/"borrow_more" or "they_repaid"/"lend_more"

    val keyboardController = LocalSoftwareKeyboardController.current

    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")) }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(debt.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isWeOwe) "Я должен: " + formatFullCurrency(remainingBalance.coerceAtLeast(0.0))
                                   else "Мне должны: " + formatFullCurrency(remainingBalance.coerceAtLeast(0.0)),
                            color = accentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть", tint = Slate400)
                    }
                }

                // Form: Register Transaction (Repayment / Adjustment)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "НОВАЯ ОПЕРАЦИЯ ПО ДОЛГУ",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        // Operation Type selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate900, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isWeOwe) {
                                val actions = listOf(
                                    "repay" to "Погасить (Расход)",
                                    "borrow_more" to "Взять еще (Доход)"
                                )
                                actions.forEach { (actionKey, label) ->
                                    val isSelected = selectedOperationAction == actionKey
                                    val localColor = if (actionKey == "repay") Emerald400 else Rose500
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) localColor.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.dp else 0.dp,
                                                color = if (isSelected) localColor else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedOperationAction = actionKey }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) localColor else Slate400,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                val actions = listOf(
                                    "they_repaid" to "Вернули (Доход)",
                                    "lend_more" to "Дать еще (Расход)"
                                )
                                actions.forEach { (actionKey, label) ->
                                    val isSelected = selectedOperationAction == actionKey
                                    val localColor = if (actionKey == "they_repaid") Emerald400 else Rose500
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) localColor.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                width = if (isSelected) 1.dp else 0.dp,
                                                color = if (isSelected) localColor else Color.Transparent,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedOperationAction = actionKey }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) localColor else Slate400,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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
                                placeholder = { Text("Сумма в ₽", color = Slate700, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900,
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = Slate800,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            val enteredAmount = parseAmountInput(paymentAmountText.text)
                            val isAmtValid = enteredAmount > 0.0

                            Button(
                                onClick = {
                                    if (isAmtValid) {
                                        keyboardController?.hide()
                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        
                                        // Determine transaction type and subcategory text
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
                                    disabledContainerColor = Slate800
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("submit_debt_tx_button")
                            ) {
                                Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Внести", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // History List Title
                Text(
                    text = "ИСТОРИЯ ПЛАТЕЖЕЙ И ИЗМЕНЕНИЙ",
                    color = Slate400,
                    fontSize = 11.sp,
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
                        Text("Нет истории платежей по этому обязательству", color = Slate400, fontSize = 13.sp)
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
                            
                            // Color representation: 
                            // - If we owe: reducing it (expense) is positive outcome (Emerald), borrowing more (income) is negative outcome (Rose)
                            // - If they owe us: paying back to us (income) is positive outcome (Emerald), lending more (expense) is negative (Rose)
                            val outcomeColor = if (isWeOwe) {
                                if (tx.type == "expense") Emerald400 else Rose500
                            } else {
                                if (tx.type == "income") Emerald400 else Rose500
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
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
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(outcomeColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = outcomeColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = tx.subcategory.ifBlank { tx.category },
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = formatDayHeaderLabel(tx.date),
                                                color = Slate400,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = (if (isIncome) "+ " else "- ") + formatFullCurrency(tx.amount),
                                        color = if (isIncome) Emerald400 else Rose500,
                                        fontSize = 12.sp,
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
