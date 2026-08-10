package com.example.ui.components.dialogs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.CategoryEntity
import com.example.data.db.TransactionEntity
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.PeriodType
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryLimitsDialog(
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity>,
    onUpdateLimit: (categoryName: String, limit: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var limitInputText by remember { mutableStateOf("") }
    var expandedCategoryName by remember { mutableStateOf<String?>(null) }

    val expenseTransactions = remember(transactions) {
        transactions.filter { it.type == "expense" }
    }

    val categoryTotals = remember(expenseTransactions) {
        expenseTransactions.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    val allCategoryNames = remember(categories, categoryTotals) {
        (categories.filter { it.type == "expense" }.map { it.name } + categoryTotals.keys)
            .distinct()
            .sortedByDescending { categoryTotals[it] ?: 0.0 }
    }

    val totalSpent = remember(categoryTotals) { categoryTotals.values.sum() }

    SwipeToDismissDialog(
        onDismissRequest = onDismiss,
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 0.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = DarkSlate,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 16.dp, start = 20.dp, end = 20.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Slate500.copy(alpha = 0.6f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Header
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Indigo500.copy(alpha = 0.15f))
                                .border(1.dp, Indigo500.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = Indigo500,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "КАТЕГОРИИ И ЛИМИТЫ",
                                color = Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Траты: ${formatLimitCurrency(totalSpent)}",
                                color = Slate100,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Slate800.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inline editor if editing category limit
                editingCategory?.let { cat ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "ЛИМИТ ДЛЯ: ${cat.name.uppercase(Locale.getDefault())}",
                                color = Indigo500,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = limitInputText,
                                onValueChange = { limitInputText = it.replace(',', '.') },
                                label = { Text("Лимит трат (₽)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900,
                                    focusedBorderColor = Indigo500,
                                    unfocusedBorderColor = Slate800,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        onUpdateLimit(cat.name, null)
                                        editingCategory = null
                                    }
                                ) {
                                    Text("Сбросить", color = Rose500, fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                OutlinedButton(
                                    onClick = { editingCategory = null },
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                                ) {
                                    Text("Отмена", color = Slate400, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        val valLimit = limitInputText.toDoubleOrNull()
                                        onUpdateLimit(cat.name, valLimit)
                                        editingCategory = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Сохранить", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allCategoryNames) { catName ->
                        val catEntity = categories.find { it.name == catName }
                            ?: CategoryEntity(id = java.util.UUID.randomUUID().toString(), name = catName, type = "expense")
                        val spent = categoryTotals[catName] ?: 0.0
                        val limit = catEntity.monthlyLimit
                        val isExpanded = expandedCategoryName == catName
                        val categoryTxs = expenseTransactions.filter { it.category == catName }

                        CategoryLimitItemCard(
                            categoryName = catName,
                            spent = spent,
                            limit = limit,
                            isExpanded = isExpanded,
                            transactions = categoryTxs,
                            onToggleExpand = {
                                expandedCategoryName = if (isExpanded) null else catName
                            },
                            onEditLimitClick = {
                                editingCategory = catEntity
                                limitInputText = limit?.toString() ?: ""
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLimitItemCard(
    categoryName: String,
    spent: Double,
    limit: Double?,
    isExpanded: Boolean,
    transactions: List<TransactionEntity>,
    onToggleExpand: () -> Unit,
    onEditLimitClick: () -> Unit
) {
    val isOverLimit = limit != null && limit > 0 && spent > limit
    val progress = if (limit != null && limit > 0) (spent / limit).coerceIn(0.0, 1.0).toFloat() else 0f

    val categoryColor = when {
        isOverLimit -> Rose500
        progress > 0.8f -> Indigo500
        else -> Emerald400
    }

    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isOverLimit) Rose500.copy(alpha = 0.5f) else Slate800
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(categoryColor.copy(alpha = 0.15f))
                            .border(1.dp, categoryColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = categoryName,
                                color = Slate100,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isOverLimit) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Превышение",
                                    tint = Rose500,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (limit != null && limit > 0) {
                                "из ${formatLimitCurrency(limit)}"
                            } else {
                                "Лимит не задан"
                            },
                            color = if (isOverLimit) Rose500 else Slate400,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatLimitCurrency(spent),
                        color = if (isOverLimit) Rose500 else Emerald400,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onEditLimitClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Задать лимит",
                            tint = Slate300,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Развернуть",
                            tint = Slate400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (limit != null && limit > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Slate950)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barWidth = size.width * animatedProgress
                        val corner = 3.dp.toPx()

                        drawRoundRect(
                            color = categoryColor.copy(alpha = 0.4f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Emerald400, categoryColor)
                            ),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(Slate950.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Операции (${transactions.size})",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (transactions.isEmpty()) {
                        Text(
                            text = "Нет операций в выбранном периоде",
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    } else {
                        transactions.take(5).forEach { tx ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tx.subcategory.ifBlank { tx.category },
                                    color = Slate200,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatLimitCurrency(tx.amount),
                                    color = Rose500,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
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
fun SetCategoryLimitDialog(
    categoryName: String,
    currentLimit: Double?,
    onSaveLimit: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var limitInput by remember { mutableStateOf(currentLimit?.toString() ?: "") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .shadow(elevation = 20.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "ЛИМИТ: ${categoryName.uppercase(Locale.getDefault())}",
                    color = Indigo500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it.replace(',', '.') },
                    label = { Text("Сумма лимита (₽)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = Indigo500,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentLimit != null) {
                        TextButton(
                            onClick = {
                                onSaveLimit(null)
                                onDismiss()
                            }
                        ) {
                            Text("Удалить лимит", color = Rose500, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Text("Отмена", color = Slate400, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val newLim = limitInput.toDoubleOrNull()
                            if (newLim != null && newLim <= 0) {
                                Toast.makeText(context, "Укажите значение > 0", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSaveLimit(newLim)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Сохранить", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetPeriodDialog(
    currentPeriodType: PeriodType,
    onSelectPeriod: (PeriodType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "БЮДЖЕТНЫЙ ПЕРИОД",
                    color = Indigo500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                val periods = listOf(
                    PeriodType.MONTH to "Месячный период",
                    PeriodType.ALL to "За всё время",
                    PeriodType.DAY to "За выбранный день"
                )

                periods.forEach { (type, title) ->
                    val isSelected = currentPeriodType == type
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPeriod(type)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Indigo500.copy(alpha = 0.15f) else DarkBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Indigo500 else Slate800
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Indigo500 else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Indigo500,
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

private fun formatLimitCurrency(amount: Double): String {
    return String.format(Locale("ru", "RU"), "%,.0f ₽", amount).replace(',', ' ')
}
