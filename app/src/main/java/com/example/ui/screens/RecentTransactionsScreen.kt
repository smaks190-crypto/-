package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AccountEntity
import com.example.data.db.TransactionEntity
import com.example.ui.components.SwipeDirection
import com.example.ui.components.SwipeToRevealBox
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCard
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Filter mode for transactions list
 */
enum class TransactionFilterType(val label: String) {
    ALL("Все"),
    EXPENSE("Расходы"),
    INCOME("Доходы")
}

/**
 * Jetpack Compose screen that lists recent financial transactions retrieved
 * from the Room database, displaying amount, category, date, and interactive details.
 */
@Composable
fun RecentTransactionsScreen(
    transactions: List<TransactionEntity>,
    accounts: List<AccountEntity> = emptyList(),
    onDeleteTransaction: (String) -> Unit = {},
    onEditTransaction: ((TransactionEntity) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

    var selectedFilter by remember { mutableStateOf(TransactionFilterType.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isNewestFirst by remember { mutableStateOf(true) }
    var detailTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    val numberFormat = remember {
        val symbols = DecimalFormatSymbols(Locale.getDefault()).apply {
            groupingSeparator = ' '
        }
        DecimalFormat("#,##0", symbols)
    }

    // Filter and sort transactions
    val filteredTransactions by remember(transactions, selectedFilter, searchQuery, isNewestFirst) {
        derivedStateOf {
            val list = transactions.filter { tx ->
                val matchesType = when (selectedFilter) {
                    TransactionFilterType.ALL -> true
                    TransactionFilterType.EXPENSE -> tx.type == "expense"
                    TransactionFilterType.INCOME -> tx.type == "income"
                }
                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                            tx.subcategory.contains(searchQuery, ignoreCase = true) ||
                            tx.amount.toString().contains(searchQuery)
                }
                matchesType && matchesSearch
            }

            if (isNewestFirst) {
                list.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.createdAt })
            } else {
                list.sortedWith(compareBy<TransactionEntity> { it.date }.thenBy { it.createdAt })
            }
        }
    }

    // Summary calculations
    val totalExpense by remember(filteredTransactions) {
        derivedStateOf {
            filteredTransactions.filter { it.type == "expense" }.sumOf { it.amount }
        }
    }
    val totalIncome by remember(filteredTransactions) {
        derivedStateOf {
            filteredTransactions.filter { it.type == "income" }.sumOf { it.amount }
        }
    }
    val netBalance by remember(totalIncome, totalExpense) {
        derivedStateOf { totalIncome - totalExpense }
    }

    // Grouping by date
    val groupedTransactions by remember(filteredTransactions) {
        derivedStateOf {
            filteredTransactions.groupBy { it.date }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .navigationBarsPadding()
    ) {
        // --- TOP BAR ---
        Surface(
            color = Slate900,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("recent_transactions_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Indigo500.copy(alpha = 0.2f))
                        .border(1.dp, Indigo500.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Indigo500,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Недавние транзакции",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Всего записей: ${filteredTransactions.size}",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = { isNewestFirst = !isNewestFirst },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("toggle_sort_order_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = if (isNewestFirst) "Сначала новые" else "Сначала старые",
                        tint = if (isNewestFirst) Indigo500 else Slate400
                    )
                }
            }
        }

        // --- SUMMARY METRICS CARDS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryMetricCard(
                title = "Расходы",
                amount = "${numberFormat.format(totalExpense)} ₽",
                accentColor = Rose500,
                icon = Icons.Default.TrendingDown,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricCard(
                title = "Доходы",
                amount = "${numberFormat.format(totalIncome)} ₽",
                accentColor = Emerald500,
                icon = Icons.Default.TrendingUp,
                modifier = Modifier.weight(1f)
            )
            SummaryMetricCard(
                title = "Итог",
                amount = "${if (netBalance >= 0) "+" else ""}${numberFormat.format(netBalance)} ₽",
                accentColor = if (netBalance >= 0) Emerald400 else Rose400,
                icon = Icons.Default.AccountBalance,
                modifier = Modifier.weight(1f)
            )
        }

        // --- SEARCH INPUT & FILTER CHIPS ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск по категории или сумме...", color = Slate500, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить",
                                tint = Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Indigo500,
                    unfocusedBorderColor = Slate700
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("search_transactions_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionFilterType.entries.forEach { type ->
                    val isSelected = selectedFilter == type
                    val chipColor by animateColorAsState(
                        targetValue = if (isSelected) Indigo500 else Slate800,
                        animationSpec = tween(200)
                    )
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = type },
                        label = {
                            Text(
                                text = type.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Slate400
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Slate900,
                            selectedContainerColor = chipColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Indigo500 else Slate700
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("filter_chip_${type.name.lowercase()}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- TRANSACTIONS LIST ---
        if (filteredTransactions.isEmpty()) {
            EmptyTransactionsState(
                hasFilters = searchQuery.isNotEmpty() || selectedFilter != TransactionFilterType.ALL,
                onResetFilters = {
                    searchQuery = ""
                    selectedFilter = TransactionFilterType.ALL
                }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("recent_transactions_list")
            ) {
                groupedTransactions.forEach { (dateStr, txsForDate) ->
                    val dayExpense = txsForDate.filter { it.type == "expense" }.sumOf { it.amount }
                    val dayIncome = txsForDate.filter { it.type == "income" }.sumOf { it.amount }
                    val dayNet = dayIncome - dayExpense

                    item(key = "header_$dateStr") {
                        DateSectionHeader(
                            dateString = dateStr,
                            dayNet = dayNet,
                            numberFormat = numberFormat
                        )
                    }

                    items(
                        items = txsForDate,
                        key = { it.id }
                    ) { item ->
                        SwipeToRevealBox(
                            swipeDirection = SwipeDirection.EndToStart,
                            onDelete = { transactionToDelete = item },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transaction_item_${item.id}")
                        ) {
                            TransactionItemRow(
                                item = item,
                                account = accounts.firstOrNull { it.id == item.accountId },
                                numberFormat = numberFormat,
                                onClick = { detailTransaction = item },
                                onDelete = { transactionToDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- TRANSACTION DETAILS DIALOG ---
    detailTransaction?.let { tx ->
        TransactionDetailDialog(
            transaction = tx,
            account = accounts.firstOrNull { it.id == tx.accountId },
            numberFormat = numberFormat,
            onDismiss = { detailTransaction = null },
            onEdit = {
                detailTransaction = null
                onEditTransaction?.invoke(tx)
            },
            onDelete = {
                detailTransaction = null
                transactionToDelete = tx
            }
        )
    }

    // --- DELETE CONFIRMATION DIALOG ---
    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = {
                Text(
                    text = "Удалить операцию?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Вы действительно хотите удалить запись «${tx.category}» на сумму ${numberFormat.format(tx.amount)} ₽?",
                    color = Slate300
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction(tx.id)
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Отмена", color = Slate400)
                }
            },
            containerColor = Slate900,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Metric Card for Total Expenses, Incomes, or Net Balance
 */
@Composable
private fun SummaryMetricCard(
    title: String,
    amount: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = amount,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Section header displaying the date and daily net total
 */
@Composable
private fun DateSectionHeader(
    dateString: String,
    dayNet: Double,
    numberFormat: DecimalFormat
) {
    val friendlyDate = remember(dateString) {
        formatFriendlyDate(dateString)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = null,
                tint = Slate500,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = friendlyDate,
                color = Slate400,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "${if (dayNet >= 0) "+" else ""}${numberFormat.format(dayNet)} ₽",
            color = if (dayNet >= 0) Emerald400 else Slate400,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Single Transaction Item Row
 */
@Composable
private fun TransactionItemRow(
    item: TransactionEntity,
    account: AccountEntity?,
    numberFormat: DecimalFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = item.type == "expense"
    val accentColor = if (isExpense) Rose400 else Emerald400
    val icon = remember(item.category) { resolveCategoryIcon(item.category) }
    val timeFormatted = remember(item.createdAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.createdAt))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Category Icon Badge (FinTech Neon style from David-finance-main-2)
            com.example.ui.components.CategoryIconBadge(
                categoryName = item.category,
                type = item.type,
                size = 42.dp,
                iconSize = 22.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Category & Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.category,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeFormatted,
                        color = Slate500,
                        fontSize = 11.sp
                    )
                    if (item.subcategory.isNotBlank()) {
                        Text(
                            text = " • ${item.subcategory}",
                            color = Slate400,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (account != null) {
                        Text(
                            text = " • ${account.name}",
                            color = Indigo500,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            Text(
                text = "${if (isExpense) "-" else "+"}${numberFormat.format(item.amount)} ₽",
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Detailed Dialog for a Transaction
 */
@Composable
private fun TransactionDetailDialog(
    transaction: TransactionEntity,
    account: AccountEntity?,
    numberFormat: DecimalFormat,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = transaction.type == "expense"
    val accentColor = if (isExpense) Rose400 else Emerald400
    val icon = remember(transaction.category) { resolveCategoryIcon(transaction.category) }
    val fullDateFormatted = remember(transaction.createdAt, transaction.date) {
        val datePart = formatFriendlyDate(transaction.date)
        val timePart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(transaction.createdAt))
        "$datePart, $timePart"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Icon Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.5.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = transaction.category,
                        tint = accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${if (isExpense) "-" else "+"}${numberFormat.format(transaction.amount)} ₽",
                    color = accentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isExpense) "Расход" else "Доход",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Detail Attributes Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate800),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailRow(label = "Категория", value = transaction.category)
                        if (transaction.subcategory.isNotBlank()) {
                            DetailRow(label = "Подкатегория / Комментарий", value = transaction.subcategory)
                        }
                        DetailRow(label = "Дата и время", value = fullDateFormatted)
                        if (account != null) {
                            DetailRow(label = "Счет", value = account.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose400)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Удалить")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть", color = Slate400)
                    }
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Изменить")
                    }
                }
            }
        },
        containerColor = Slate900,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Slate400, fontSize = 12.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Empty State for Transactions List
 */
@Composable
private fun EmptyTransactionsState(
    hasFilters: Boolean,
    onResetFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Slate800),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (hasFilters) "Операции не найдены" else "Транзакций пока нет",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasFilters) {
                    "Попробуйте изменить параметры поиска или сбросить фильтры"
                } else {
                    "Добавьте первую операцию кнопкой «+» на главном экране"
                },
                color = Slate400,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            if (hasFilters) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onResetFilters,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Indigo500),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Indigo500)
                    )
                ) {
                    Text("Сбросить фильтры")
                }
            }
        }
    }
}

/**
 * Helper to map category name to a relevant Material icon
 */
fun resolveCategoryIcon(category: String): ImageVector {
    val lower = category.lowercase(Locale.getDefault())
    return when {
        lower.contains("продукт") || lower.contains("еда") || lower.contains("кафе") || lower.contains("ресторан") || lower.contains("супермаркет") -> Icons.Default.ShoppingCart
        lower.contains("зарплат") || lower.contains("доход") || lower.contains("подработк") || lower.contains("аванс") || lower.contains("преми") -> Icons.Default.Payments
        lower.contains("транспорт") || lower.contains("авто") || lower.contains("машин") || lower.contains("такси") || lower.contains("бензин") -> Icons.Default.DirectionsCar
        lower.contains("развлечен") || lower.contains("кино") || lower.contains("игр") || lower.contains("отдых") -> Icons.Default.Theaters
        lower.contains("книг") || lower.contains("образован") || lower.contains("курс") || lower.contains("учеб") -> Icons.Default.School
        lower.contains("здоров") || lower.contains("аптек") || lower.contains("врач") || lower.contains("медицин") -> Icons.Default.MedicalServices
        lower.contains("одежд") || lower.contains("обув") || lower.contains("покупк") || lower.contains("шоппинг") -> Icons.Default.ShoppingBag
        lower.contains("дом") || lower.contains("жкх") || lower.contains("аренд") || lower.contains("квартир") || lower.contains("обязательн") -> Icons.Default.Home
        lower.contains("сбережен") || lower.contains("вклад") || lower.contains("инвест") || lower.contains("копилк") -> Icons.Default.Savings
        lower.contains("подар") || lower.contains("праздник") -> Icons.Default.CardGiftcard
        lower.contains("долг") || lower.contains("кредит") || lower.contains("займ") -> Icons.Default.CreditCard
        else -> Icons.Default.ReceiptLong
    }
}

/**
 * Format ISO date string (YYYY-MM-DD) into user-friendly localized text
 */
fun formatFriendlyDate(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr

        val cal = Calendar.getInstance()
        val todayStr = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(cal.time)

        when (dateStr) {
            todayStr -> "Сегодня"
            yesterdayStr -> "Вчера"
            else -> {
                val outFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
                outFormat.format(date)
            }
        }
    } catch (_: Exception) {
        dateStr
    }
}
