package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.db.CategoryEntity
import com.example.data.db.TransactionEntity
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSlate
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.util.Locale

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

    // Group expense transactions by category
    val expenseTransactions = remember(transactions) {
        transactions.filter { it.type == "expense" }
    }
    
    val categoryTotals = remember(expenseTransactions) {
        expenseTransactions.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    // Combine defined categories with any categories present in transactions
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
                // Drag handle / swipe indicator
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
                    horizontalArrangement = Arrangement.Start,
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
                                text = "Траты: ${formatCurrency(totalSpent)}",
                                color = Slate100,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    contentPadding = PaddingValues(
                        bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(allCategoryNames) { catName ->
                        val catEntity = categories.find { it.name.equals(catName, ignoreCase = true) }
                        val spent = categoryTotals[catName] ?: 0.0
                        val limit = catEntity?.monthlyLimit
                        val isExpanded = expandedCategoryName == catName
                        val catTxs = expenseTransactions.filter { it.category.equals(catName, ignoreCase = true) }

                        CategoryLimitItemCard(
                            categoryName = catName,
                            spent = spent,
                            limit = limit,
                            isExpanded = isExpanded,
                            transactions = catTxs,
                            onToggleExpand = {
                                expandedCategoryName = if (isExpanded) null else catName
                            },
                            onEditLimitClick = {
                                editingCategory = catEntity ?: CategoryEntity(
                                    type = "expense",
                                    name = catName,
                                    monthlyLimit = limit
                                )
                                limitInputText = limit?.let { String.format(Locale.US, "%.0f", it) } ?: ""
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog to edit limit
    if (editingCategory != null) {
        Dialog(
            onDismissRequest = { editingCategory = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = DarkBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Лимит категории: ${editingCategory?.name}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Установите максимальную сумму расходов в месяц. При превышении шкала загорится неоновым красным цветом.",
                        color = Slate400,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = limitInputText,
                        onValueChange = { limitInputText = it },
                        label = { Text("Лимит (₽)", color = Slate400) },
                        placeholder = { Text("например: 25000", color = Slate500) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Indigo500,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Slate900,
                            unfocusedContainerColor = Slate900
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editingCategory?.monthlyLimit != null) {
                            TextButton(
                                onClick = {
                                    editingCategory?.let { onUpdateLimit(it.name, null) }
                                    editingCategory = null
                                }
                            ) {
                                Text("Сбросить", color = Rose500, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Button(
                            onClick = {
                                val parsed = limitInputText.toDoubleOrNull()
                                editingCategory?.let { onUpdateLimit(it.name, parsed) }
                                editingCategory = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Сохранить", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
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
    val progressRatio = remember(spent, limit) {
        if (limit == null || limit <= 0.0) 0f
        else (spent / limit).coerceIn(0.0, 1.5).toFloat()
    }

    val isOverLimit = remember(spent, limit) {
        limit != null && limit > 0 && spent > limit
    }

    // Morphing progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = progressRatio.coerceAtMost(1.0f),
        animationSpec = spring(stiffness = 300f),
        label = "neon_progress_morph"
    )

    // Dynamic Neon Progress Bar Color Blend
    val neonProgressColor by animateColorAsState(
        targetValue = when {
            isOverLimit -> Rose500
            progressRatio > 0.8f -> Indigo500
            else -> Emerald400
        },
        animationSpec = tween(400),
        label = "neon_progress_color"
    )

    val categoryColor = remember(categoryName) {
        val hash = categoryName.hashCode()
        val palette = listOf(Emerald400, Indigo500, Rose500, Color(0xFFF59E0B), Color(0xFF06B6D4), Color(0xFFA855F7))
        palette[kotlin.math.abs(hash) % palette.size]
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 400f)),
        colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.6f)),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isOverLimit) 1.5.dp else 1.dp,
            color = if (isOverLimit) Rose500.copy(alpha = 0.7f) else Slate800
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
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
                                "из ${formatCurrency(limit)}"
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
                        text = formatCurrency(spent),
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

            // Neon Progress Bar (If limit is set)
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
                        
                        // Draw Glow
                        drawRoundRect(
                            color = neonProgressColor.copy(alpha = 0.4f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(corner, corner)
                        )

                        // Draw Inner Neon Bar
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Emerald400, neonProgressColor)
                            ),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    }
                }
            }

            // Expanded Transactions breakdown
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
                            text = "Нет операцией в выбранном периоде",
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
                                    text = "${formatCurrency(tx.amount)}",
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

private fun formatCurrency(amount: Double): String {
    return String.format(Locale("ru", "RU"), "%,.0f ₽", amount).replace(',', ' ')
}
