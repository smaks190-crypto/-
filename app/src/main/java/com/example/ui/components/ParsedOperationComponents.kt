package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.viewmodel.BudgetViewModel
import java.util.Locale

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.example.ui.theme.Indigo500
import com.example.ui.theme.NeonEmerald

@Composable
fun CompactParsedOperationCard(
    operation: ParsedVoiceOperation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = operation.type == "expense"
    val color = if (isExpense) Rose500 else Emerald400
    val numberFormat = remember {
        val symbols = java.text.DecimalFormatSymbols(Locale("ru", "RU")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        java.text.DecimalFormat("#,##0.##", symbols).apply {
            isGroupingUsed = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                categoryName = operation.category,
                type = operation.type,
                size = 36.dp,
                iconSize = 18.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.subcategory.ifBlank { operation.title.ifBlank { operation.category } },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${operation.category} • Нажмите для правки",
                    color = Slate400,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${if (isExpense) "-" else "+"}${numberFormat.format(operation.amount)} ₽",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить",
                    tint = Rose500.copy(alpha = 0.8f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun FullParsedOperationFormCard(
    operation: ParsedVoiceOperation,
    categories: List<CategoryEntity>,
    viewModel: BudgetViewModel,
    defaultDate: String,
    showDeleteButton: Boolean = true,
    onUpdate: (ParsedVoiceOperation) -> Unit,
    onDelete: () -> Unit
) {
    var type by remember(operation) { mutableStateOf(operation.type) }
    var selectedCategory by remember(operation) { mutableStateOf(operation.category) }
    var subcategory by remember(operation) { mutableStateOf(operation.subcategory) }
    var amountText by remember(operation) {
        val str = if (operation.amount == 0.0) "" else if (operation.amount % 1 == 0.0) String.format(Locale.US, "%.0f", operation.amount) else operation.amount.toString()
        mutableStateOf(TextFieldValue(str))
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var userManuallyPickedCategory by remember(operation) { mutableStateOf(false) }

    val filteredCategories = remember(categories, type) { categories.filter { it.type == type } }

    LaunchedEffect(type, selectedCategory, subcategory, amountText) {
        val amt = parseAmountInput(amountText.text)
        onUpdate(
            operation.copy(
                type = type,
                category = selectedCategory,
                subcategory = subcategory,
                amount = amt
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("КАТЕГОРИЯ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryIconBadge(
                                categoryName = selectedCategory.ifEmpty { "Прочее" },
                                type = type,
                                size = 28.dp,
                                iconSize = 15.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedCategory.ifEmpty { "Категория" },
                                color = if (selectedCategory.isNotEmpty()) Color.White else Slate400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Slate900)
                    ) {
                        filteredCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CategoryIconBadge(
                                            categoryName = cat.name,
                                            type = cat.type,
                                            size = 24.dp,
                                            iconSize = 13.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cat.name, color = Color.White)
                                    }
                                },
                                onClick = {
                                    selectedCategory = cat.name
                                    userManuallyPickedCategory = true
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Column {
                Text("ТИП", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                PlusMinusMorphToggle(
                    type = type,
                    onToggle = {
                        val newType = if (type == "expense") "income" else "expense"
                        type = newType
                        userManuallyPickedCategory = false
                        selectedCategory = categories.filter { it.type == newType }.firstOrNull()?.name ?: ""
                    }
                )
            }
        }

        if (filteredCategories.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filteredCategories, key = { it.id }) { cat ->
                    val isSelected = cat.name == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategory = cat.name
                            userManuallyPickedCategory = true
                        },
                        label = {
                            Text(
                                text = cat.name,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            CategoryIconBadge(
                                categoryName = cat.name,
                                type = cat.type,
                                size = 18.dp,
                                iconSize = 11.dp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Indigo500.copy(alpha = 0.25f),
                            selectedLabelColor = NeonEmerald,
                            containerColor = Slate900,
                            labelColor = Slate400
                        )
                    )
                }
            }
        }

        Column {
            Text("ОПИСАНИЕ / НАЗВАНИЕ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = subcategory,
                onValueChange = { newVal ->
                    val cap = newVal.capitalizeFirstLetter()
                    subcategory = cap
                    if (!userManuallyPickedCategory) {
                        val detected = detectCategoryAndTypeByTitle(cap, categories, type)
                        if (detected != null) {
                            val (detType, detCat) = detected
                            if (detType != type) type = detType
                            selectedCategory = detCat
                        }
                    }
                },
                placeholder = { Text("Описание", color = Slate400, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900,
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Column {
            Text("СУММА (₽)", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                suffix = { Text("₽", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900,
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (showDeleteButton) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Rose500.copy(alpha = 0.15f), contentColor = Rose500),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Rose500)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Удалить операцию", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

