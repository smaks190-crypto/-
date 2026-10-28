package com.example.ui.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.formatDayHeaderLabel
import com.example.ui.components.formatFullCurrency
import com.example.ui.screens.TransactionRowItem
import com.example.ui.theme.Emerald400
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class DayHeaderTotalsUi(
    val incomeBadgeText: String?,
    val expenseBadgeText: String?
)

internal fun computeDayHeaderTotals(
    filterType: String,
    dayTotalIncome: Double,
    dayTotalExpense: Double
): DayHeaderTotalsUi {
    val incomeText = if (dayTotalIncome > 0.0 && (filterType == "all" || filterType == "income")) {
        "+${formatFullCurrency(dayTotalIncome)}"
    } else null

    val expenseText = if (dayTotalExpense > 0.0 && (filterType == "all" || filterType == "expense")) {
        "-${formatFullCurrency(dayTotalExpense)}"
    } else null

    return DayHeaderTotalsUi(
        incomeBadgeText = incomeText,
        expenseBadgeText = expenseText
    )
}

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
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_lazy_list"),
        contentPadding = PaddingValues(
            top = 4.dp,
            start = 0.dp,
            end = 0.dp,
            bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (sortedList.isEmpty()) {
            item(key = "empty_transactions_state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            "По запросу «$searchQuery» операции не найдены"
                        } else {
                            "Операции за выбранный период не найдены"
                        },
                        color = Slate500,
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            groupedByDate.forEach { (dateStr, itemsForDay) ->
                item(key = "header_$dateStr") {
                    val dayTotalExpense = itemsForDay
                        .filter { it.type.equals("expense", ignoreCase = true) }
                        .sumOf { kotlin.math.abs(it.amount) }
                    val dayTotalIncome = itemsForDay
                        .filter { it.type.equals("income", ignoreCase = true) }
                        .sumOf { kotlin.math.abs(it.amount) }

                    val dayTotalsUi = computeDayHeaderTotals(
                        filterType = filterType,
                        dayTotalIncome = dayTotalIncome,
                        dayTotalExpense = dayTotalExpense
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDayHeaderLabel(dateStr),
                            color = Color.White,
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (dayTotalsUi.incomeBadgeText != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Emerald400.copy(alpha = 0.12f))
                                        .border(1.dp, Emerald400.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = dayTotalsUi.incomeBadgeText,
                                        color = Emerald400,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (dayTotalsUi.expenseBadgeText != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Slate900)
                                        .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = dayTotalsUi.expenseBadgeText,
                                        color = if (filterType == "expense") Rose500 else Slate300,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
