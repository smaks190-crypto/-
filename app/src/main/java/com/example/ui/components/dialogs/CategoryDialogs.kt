package com.example.ui.components.dialogs

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.ui.components.LocalDialogSwipeEnabled
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.components.capitalizeFirstLetter
import com.example.ui.screens.getCategoryColorAndIcon
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
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily

private data class QuickCategoryPreset(
    val emoji: String,
    val name: String,
    val type: String
)

private val DavidQuickCategoryPresets = listOf(
    // Expense presets
    QuickCategoryPreset("🛒", "Маркетплейсы", "expense"),
    QuickCategoryPreset("🎬", "Подписки", "expense"),
    QuickCategoryPreset("☕", "Кофе навынос", "expense"),
    QuickCategoryPreset("🐾", "Питомцы", "expense"),
    QuickCategoryPreset("🏋️", "Спорт", "expense"),
    QuickCategoryPreset("🚕", "Такси", "expense"),
    QuickCategoryPreset("💊", "Аптека", "expense"),
    QuickCategoryPreset("🎁", "Подарки", "expense"),
    QuickCategoryPreset("🏠", "ЖКХ и связь", "expense"),
    QuickCategoryPreset("🍔", "Фастфуд", "expense"),
    // Income presets
    QuickCategoryPreset("💼", "Фриланс", "income"),
    QuickCategoryPreset("📈", "Дивиденды", "income"),
    QuickCategoryPreset("🎁", "Кэшбэк", "income"),
    QuickCategoryPreset("🏆", "Премия", "income"),
    QuickCategoryPreset("🏠", "Аренда", "income"),
    QuickCategoryPreset("💸", "Возврат долга", "income")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAddCategory: (type: String, name: String) -> Unit,
    onDeleteCategory: (id: String) -> Unit
) {
    var newType by remember { mutableStateOf("expense") }
    var newName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val incomeCats = remember(categories) { categories.filter { it.type == "income" } }
    val expenseCats = remember(categories) { categories.filter { it.type == "expense" } }

    val existingNamesLower = remember(categories, newType) {
        categories.filter { it.type == newType }.map { it.name.trim().lowercase() }.toSet()
    }

    val availablePresets = remember(newType, existingNamesLower) {
        DavidQuickCategoryPresets.filter {
            it.type == newType && it.name.lowercase() !in existingNamesLower
        }
    }

    val activeAccent by animateColorAsState(
        targetValue = if (newType == "expense") Rose500 else Emerald400,
        animationSpec = tween(240),
        label = "category_dialog_accent"
    )

    // Obsidian Neo-Bank Delete Confirmation Modal
    categoryToDelete?.let { cat ->
        val (catColor, catIcon) = getCategoryColorAndIcon(cat.name, "")
        SwipeToDismissDialog(onDismissRequest = { categoryToDelete = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = ObsidianSurface,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(listOf(Rose500.copy(alpha = 0.45f), GlassBorder))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(catColor.copy(alpha = 0.16f))
                                .border(1.dp, catColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "УДАЛЕНИЕ КАТЕГОРИИ",
                                color = Rose400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Удалить «${cat.name}»?",
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Text(
                        text = "Категория исчезнет из списка быстрого выбора при создании новых операций. Уже созданные операции в истории сохранятся.",
                        color = Slate300,
                        fontFamily = ManropeFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = { categoryToDelete = null },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text(
                                text = "Отмена",
                                color = Slate400,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDeleteCategory(cat.id)
                                categoryToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Удалить",
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        swipeEnabledState.value = !scrollState.isScrollInProgress
    }

    val submitNewCategory = {
        val clean = newName.trim()
        if (clean.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddCategory(newType, clean)
            newName = ""
            focusManager.clearFocus()
        }
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = ObsidianSurface,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(GlassHighlight, GlassBorder))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Ambient Radial Glow in top-right corner
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 45.dp, y = (-45).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeAccent.copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Drag Handle
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

                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(activeAccent.copy(alpha = 0.15f))
                                    .border(1.dp, activeAccent.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = activeAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "СТРУКТУРА БЮДЖЕТА",
                                    color = activeAccent,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = "Категории операций",
                                    color = Color.White,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ObsidianElevated)
                                .border(1.dp, GlassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Segmented Type Selector (Расходы / Доходы)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkBg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isExpenseSelected = newType == "expense"

                        // Expense Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isExpenseSelected) Rose500.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .border(
                                    width = if (isExpenseSelected) 1.dp else 0.dp,
                                    color = if (isExpenseSelected) Rose500.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    newType = "expense"
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = if (isExpenseSelected) Rose400 else Slate400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Расход",
                                    color = if (isExpenseSelected) Color.White else Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isExpenseSelected) Rose500.copy(alpha = 0.25f) else ObsidianElevated
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${expenseCats.size}",
                                        color = if (isExpenseSelected) Rose400 else Slate400,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Income Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (!isExpenseSelected) Emerald400.copy(alpha = 0.18f) else Color.Transparent
                                )
                                .border(
                                    width = if (!isExpenseSelected) 1.dp else 0.dp,
                                    color = if (!isExpenseSelected) Emerald400.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    newType = "income"
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = if (!isExpenseSelected) Emerald400 else Slate400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Доход",
                                    color = if (!isExpenseSelected) Color.White else Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (!isExpenseSelected) Emerald400.copy(alpha = 0.25f) else ObsidianElevated
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${incomeCats.size}",
                                        color = if (!isExpenseSelected) Emerald400 else Slate400,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Input Row: Custom Category Name + Add Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.capitalizeFirstLetter() },
                            placeholder = {
                                Text(
                                    text = if (newType == "expense") "Новая категория расхода..." else "Новая категория дохода...",
                                    color = Slate500,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_category_input"),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitNewCategory() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg,
                                focusedBorderColor = activeAccent,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Button(
                            onClick = { submitNewCategory() },
                            enabled = newName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activeAccent,
                                disabledContainerColor = ObsidianElevated
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
                            modifier = Modifier.testTag("add_category_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить",
                                tint = if (newName.isNotBlank()) (if (newType == "income") DarkBg else Color.White) else Slate500,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 🐸 David Zhabov 1-Tap Quick Category Presets
                    if (availablePresets.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(ObsidianElevated.copy(alpha = 0.7f))
                                .border(1.dp, Indigo500.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Indigo400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "🐸 БЫСТРЫЕ ПРЕСЕТЫ ДАВИДА (ДОБАВИТЬ В 1 ТАП)",
                                    color = Indigo400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availablePresets.forEach { preset ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onAddCategory(preset.type, preset.name)
                                                Toast.makeText(
                                                    context,
                                                    "🐸 Добавлена категория «${preset.name}»",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = DarkBg,
                                        border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = preset.emoji, fontSize = 13.sp)
                                            Text(
                                                text = preset.name,
                                                color = Color.White,
                                                fontFamily = ManropeFontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "+",
                                                color = activeAccent,
                                                fontFamily = SpaceGroteskFontFamily,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // EXPENSE CATEGORIES SECTION
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "РАСХОДЫ",
                                color = Rose400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${expenseCats.size} шт.",
                                color = Slate400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            expenseCats.forEach { cat ->
                                key(cat.id) {
                                    ObsidianCategoryChip(
                                        category = cat,
                                        onDeleteClick = { categoryToDelete = cat }
                                    )
                                }
                            }
                        }
                    }

                    // INCOME CATEGORIES SECTION
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ДОХОДЫ",
                                color = Emerald400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${incomeCats.size} шт.",
                                color = Slate400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            incomeCats.forEach { cat ->
                                key(cat.id) {
                                    ObsidianCategoryChip(
                                        category = cat,
                                        onDeleteClick = { categoryToDelete = cat }
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
private fun ObsidianCategoryChip(
    category: CategoryEntity,
    onDeleteClick: () -> Unit
) {
    val (catColor, catIcon) = getCategoryColorAndIcon(category.name, "")
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onDeleteClick() },
        shape = RoundedCornerShape(14.dp),
        color = ObsidianElevated,
        border = BorderStroke(1.dp, catColor.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(catColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = catIcon,
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = category.name,
                color = Color.White,
                fontFamily = ManropeFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Удалить",
                tint = Slate400,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
