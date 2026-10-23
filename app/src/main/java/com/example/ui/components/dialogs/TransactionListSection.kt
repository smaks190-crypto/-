package com.example.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.formatDayHeaderLabel
import com.example.ui.screens.TransactionRowItem
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.components.formatFullCurrency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun TransactionListSection(
    lazyListState: LazyListState,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortedList: List<TransactionEntity>,
    groupedByDate: Map<String, List<TransactionEntity>>,
    filterType: String,
    allTransactions: List<TransactionEntity>,
    onDeleteTransaction: ((String) -> Unit)?,
    onRestoreTransaction: ((TransactionEntity) -> Unit)?,
    onEditTransaction: ((TransactionEntity) -> Unit)?,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 0.dp,
            start = 0.dp,
            end = 0.dp,
            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Unpinned Search Input at the top of scroll list
        item(key = "search_bar_unpinned") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(vertical = 2.dp),
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (searchQuery.isNotEmpty()) Indigo500.copy(alpha = 0.6f) else Slate800
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (searchQuery.isNotEmpty()) Indigo500 else Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Поиск операций...",
                                    color = Slate500,
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить",
                            tint = Slate400,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onSearchQueryChange("") }
                        )
                    }
                }
            }
        }

        // Grouped Transactions List
        if (sortedList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Операции не найдены",
                        color = Slate500,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            groupedByDate.forEach { (dateStr, itemsForDay) ->
                item(key = "header_$dateStr") {
                    val dayTotalExpense = itemsForDay.filter { it.type == "expense" }.sumOf { kotlin.math.abs(it.amount) }
                    val dayTotalIncome = itemsForDay.filter { it.type == "income" }.sumOf { kotlin.math.abs(it.amount) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDayHeaderLabel(dateStr),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (filterType == "income" && dayTotalIncome > 0) {
                            Text(
                                text = "+${formatFullCurrency(dayTotalIncome)}",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (dayTotalExpense > 0) {
                            Text(
                                text = "-${formatFullCurrency(dayTotalExpense)}",
                                color = Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                items(itemsForDay, key = { it.id }) { tx ->
                    TransactionRowItem(
                        item = tx,
                        onDelete = { txId ->
                            val targetTx = allTransactions.find { it.id == txId }
                            onDeleteTransaction?.invoke(txId)
                            if (targetTx != null) {
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Операция «${targetTx.category}» удалена",
                                        actionLabel = "ОТМЕНИТЬ",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onRestoreTransaction?.invoke(targetTx)
                                    }
                                }
                            }
                        },
                        onClick = { onEditTransaction?.invoke(tx) }
                    )
                }
            }
        }
    }
}
