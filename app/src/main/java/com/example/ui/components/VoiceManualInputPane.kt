package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.BudgetViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BentoFieldBg = Color(0xFF0B111E)

@Composable
fun VoiceManualInputPane(
    viewModel: BudgetViewModel,
    categories: List<CategoryEntity>,
    selectedDate: String,
    initialType: String,
    contentAlpha: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var type by remember { mutableStateOf(initialType) }
    var date by remember {
        mutableStateOf(
            if (selectedDate.isNotBlank()) selectedDate
            else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }
    var selectedCategory by remember {
        mutableStateOf(categories.filter { it.type == type }.firstOrNull()?.name ?: "")
    }
    var subcategory by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf(TextFieldValue("")) }

    var aiSuggestedCategory by remember { mutableStateOf<String?>(null) }
    var isAiSuggesting by remember { mutableStateOf(false) }
    var userManuallySelectedCategory by remember { mutableStateOf(false) }

    var isAmountFocused by remember { mutableStateOf(false) }
    var isDescriptionFocused by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Синхронизируем выбранную категорию при смене списка категорий или типа
    val filteredCategories = remember(categories, type) {
        categories.filter { it.type == type }
    }
    LaunchedEffect(filteredCategories, type) {
        if (selectedCategory.isBlank() || filteredCategories.none { it.name == selectedCategory }) {
            selectedCategory = filteredCategories.firstOrNull()?.name ?: ""
        }
    }

    // Автоматический подбор категории с дебаунсом через локальный семантический матчер и Gemini API
    LaunchedEffect(subcategory, type) {
        val trimmed = subcategory.trim()
        if (trimmed.length >= 3) {
            delay(600)
            if (subcategory.trim() == trimmed) {
                val instantMatch = detectCategoryAndTypeByTitle(trimmed, categories, type)
                if (instantMatch == null) {
                    isAiSuggesting = true
                    val catNames = categories.filter { it.type == type }.map { it.name }
                    val suggested = viewModel.suggestCategory(trimmed, type, catNames)
                    isAiSuggesting = false
                    if (suggested.isNotBlank()) {
                        aiSuggestedCategory = suggested
                        if (!userManuallySelectedCategory || selectedCategory.isBlank()) {
                            selectedCategory = suggested
                        }
                    }
                }
            }
        } else if (trimmed.isBlank()) {
            aiSuggestedCategory = null
            isAiSuggesting = false
        }
    }

    val isIncome = type == "income"
    val accentColor by animateColorAsState(
        targetValue = if (isIncome) Emerald400 else Rose500,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "manual_pane_accent"
    )

    val parsedAmount = remember(amountText.text) { parseAmountInput(amountText.text) }
    val isAmountValid = parsedAmount > 0.0
    val isCategoryValid = selectedCategory.trim().isNotBlank()
    val isDescriptionValid = subcategory.trim().isNotBlank()
    val isDateValid = date.trim().isNotBlank()
    val isFormValid = isAmountValid && isCategoryValid && isDescriptionValid && isDateValid

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        // Фоновое радиальное свечение Obsidian Neo-Bank в правом верхнем углу
        Box(
            modifier = Modifier
                .size(160.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Верхний декоративный пилл-индикатор модального листа
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

            // 1. ФИРМЕННАЯ ШАПКА + СЕГМЕНТИРОВАННЫЙ ПЕРЕКЛЮЧАТЕЛЬ «РАСХОД / ДОХОД»
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "НОВАЯ ОПЕРАЦИЯ",
                        color = accentColor,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Добавить операцию",
                        color = Color.White,
                        fontFamily = ManropeFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                TransactionTypeSegmentedToggle(
                    selectedType = type,
                    onSelectType = { newType ->
                        if (newType != type) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            type = newType
                            userManuallySelectedCategory = false
                            selectedCategory = categories.filter { it.type == newType }.firstOrNull()?.name ?: ""
                            aiSuggestedCategory = null
                        }
                    }
                )
            }

            // 2. АКЦЕНТНЫЙ БЛОК «СУММА» И «ДАТА» (НАВЕРХУ)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Слева: Акцентная Bento-карточка ввода суммы (weight 1.35f)
                val amountBorderColor by animateColorAsState(
                    targetValue = if (isAmountFocused || isAmountValid) {
                        accentColor.copy(alpha = 0.55f)
                    } else {
                        GlassBorder
                    },
                    animationSpec = tween(200),
                    label = "amount_border_color"
                )

                Box(
                    modifier = Modifier
                        .weight(1.35f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BentoFieldBg)
                        .border(1.dp, amountBorderColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "СУММА",
                            color = if (isAmountFocused) accentColor else Slate400,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (amountText.text.isEmpty()) {
                                    Text(
                                        text = "0",
                                        color = Slate600,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                BasicTextField(
                                    value = amountText,
                                    onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(accentColor),
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isAmountFocused = it.isFocused }
                                        .testTag("transaction_amount_input")
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "₽",
                                color = accentColor,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Справа: Bento-карточка выбора даты (weight 1f)
                ObsidianBentoDateCard(
                    dateIso = date,
                    accentColor = accentColor,
                    onClick = {
                        focusManager.clearFocus()
                        showDatePicker = true
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. КОМПАКТНЫЙ БЛОК «КАТЕГОРИЯ» БЕЗ ДУБЛИРОВАНИЯ
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val activeCatSpec = remember(selectedCategory, type) {
                    resolveCategoryVisualSpec(selectedCategory.ifEmpty { "Прочее" }, type)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "КАТЕГОРИЯ",
                        color = Slate400,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!aiSuggestedCategory.isNullOrBlank() && selectedCategory == aiSuggestedCategory) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Indigo500.copy(alpha = 0.18f))
                                    .border(1.dp, Indigo500.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "✨ ИИ",
                                    color = Indigo400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (selectedCategory.isNotBlank()) {
                            Text(
                                text = selectedCategory,
                                color = activeCatSpec.color,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (filteredCategories.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(filteredCategories, key = { it.id }) { cat ->
                            val isSelected = cat.name == selectedCategory
                            val spec = remember(cat.name, cat.type) {
                                resolveCategoryVisualSpec(cat.name, cat.type)
                            }
                            val catColor = spec.color

                            val chipBg by animateColorAsState(
                                targetValue = if (isSelected) catColor.copy(alpha = 0.18f) else BentoFieldBg,
                                animationSpec = tween(180),
                                label = "chip_bg"
                            )
                            val chipBorder by animateColorAsState(
                                targetValue = if (isSelected) catColor.copy(alpha = 0.7f) else GlassBorder,
                                animationSpec = tween(180),
                                label = "chip_border"
                            )
                            val chipTextColor by animateColorAsState(
                                targetValue = if (isSelected) Color.White else Slate400,
                                animationSpec = tween(180),
                                label = "chip_text"
                            )

                            Row(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(chipBg)
                                    .border(
                                        width = if (isSelected) 1.2.dp else 1.dp,
                                        color = chipBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedCategory = cat.name
                                        userManuallySelectedCategory = true
                                    }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(catColor.copy(alpha = if (isSelected) 0.24f else 0.14f))
                                        .border(
                                            width = 1.dp,
                                            color = catColor.copy(alpha = if (isSelected) 0.55f else 0.28f),
                                            shape = RoundedCornerShape(9.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = spec.icon,
                                        contentDescription = cat.name,
                                        tint = catColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                Text(
                                    text = cat.name,
                                    color = chipTextColor,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 4. ПОЛЕ «ОПИСАНИЕ ИЛИ НАЗВАНИЕ» СО ВСТРОЕННОЙ КНОПКОЙ «✨ ИИ»
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ОПИСАНИЕ ИЛИ НАЗВАНИЕ",
                    color = Slate400,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                val descBorderColor by animateColorAsState(
                    targetValue = when {
                        isDescriptionFocused -> Indigo500.copy(alpha = 0.75f)
                        subcategory.isNotBlank() -> Emerald400.copy(alpha = 0.40f)
                        else -> GlassBorder
                    },
                    animationSpec = tween(200),
                    label = "desc_border_color"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoFieldBg)
                        .border(1.dp, descBorderColor, RoundedCornerShape(16.dp))
                        .padding(start = 14.dp, end = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (subcategory.isEmpty()) {
                                Text(
                                    text = "Например: Пятерочка, Такси, Зарплата",
                                    color = Slate500,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = subcategory,
                                onValueChange = { newVal ->
                                    val cap = newVal.capitalizeFirstLetter()
                                    subcategory = cap
                                    if (!userManuallySelectedCategory) {
                                        val detected = detectCategoryAndTypeByTitle(cap, categories, type)
                                        if (detected != null) {
                                            val (detType, detCat) = detected
                                            if (detType != type) {
                                                type = detType
                                            }
                                            selectedCategory = detCat
                                            aiSuggestedCategory = detCat
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                cursorBrush = SolidColor(Emerald400),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isDescriptionFocused = it.isFocused }
                                    .testTag("transaction_description_input")
                            )
                        }

                        // Встроенная капсула «✨ ИИ» внутри правого края поля ввода
                        Row(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Indigo500.copy(alpha = 0.18f))
                                .border(1.dp, Indigo500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    if (subcategory.trim().isBlank()) {
                                        Toast.makeText(
                                            context,
                                            "Введите название операции для ИИ-подбора",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (!isAiSuggesting) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch {
                                            isAiSuggesting = true
                                            val catNames = categories.filter { it.type == type }.map { it.name }
                                            val suggested = viewModel.suggestCategory(subcategory.trim(), type, catNames)
                                            isAiSuggesting = false
                                            if (suggested.isNotBlank()) {
                                                aiSuggestedCategory = suggested
                                                selectedCategory = suggested
                                                userManuallySelectedCategory = true
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isAiSuggesting) {
                                CircularProgressIndicator(
                                    color = Indigo400,
                                    strokeWidth = 1.8.dp,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "ИИ подбор категории",
                                    tint = Indigo400,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = "ИИ",
                                color = Color.White,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. ВЫРАЗИТЕЛЬНЫЙ НИЖНИЙ РЯД: КНОПКА «СОХРАНИТЬ» + КНОПКА ЗАКРЫТИЯ «✕» СПРАВА
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val saveBorderColor by animateColorAsState(
                    targetValue = if (isAmountValid) Emerald400.copy(alpha = 0.75f) else Slate800,
                    animationSpec = tween(200),
                    label = "save_border"
                )
                val saveTextColor by animateColorAsState(
                    targetValue = if (isAmountValid) Emerald400 else Slate400,
                    animationSpec = tween(200),
                    label = "save_text"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BentoFieldBg)
                        .background(
                            brush = if (isAmountValid) {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Emerald400.copy(alpha = 0.28f),
                                        NeonCyan.copy(alpha = 0.18f)
                                    )
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        BentoFieldBg,
                                        BentoFieldBg
                                    )
                                )
                            }
                        )
                        .border(
                            width = if (isAmountValid) 1.2.dp else 1.dp,
                            color = saveBorderColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            when {
                                !isAmountValid -> {
                                    Toast.makeText(context, "Введите сумму операции", Toast.LENGTH_SHORT).show()
                                }
                                !isDescriptionValid -> {
                                    Toast.makeText(context, "Введите описание или название операции", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.addTransaction(
                                        type = type,
                                        date = date,
                                        category = selectedCategory.ifEmpty { "Прочее" },
                                        subcategory = subcategory.trim(),
                                        amount = parsedAmount
                                    )
                                    onDismiss()
                                }
                            }
                        }
                        .testTag("save_transaction_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Сохранить",
                        color = if (isFormValid) Color.White else saveTextColor,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate800.copy(alpha = 0.65f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .testTag("close_manual_add_button"),
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

    if (showDatePicker) {
        SingleDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { selected ->
                date = selected
            }
        )
    }
}

/**
 * Сегментированный переключатель типа операции («↓ Расход» / «↑ Доход») в стиле Obsidian Neo-Bank.
 */
@Composable
fun TransactionTypeSegmentedToggle(
    selectedType: String,
    onSelectType: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BentoFieldBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf(
            Triple("expense", "↓ Расход", Rose500),
            Triple("income", "↑ Доход", Emerald400)
        )

        tabs.forEach { (tabType, label, activeColor) ->
            val isSelected = selectedType == tabType

            val tabBg by animateColorAsState(
                targetValue = if (isSelected) activeColor.copy(alpha = 0.18f) else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "seg_tab_bg_$tabType"
            )
            val tabBorder by animateColorAsState(
                targetValue = if (isSelected) activeColor.copy(alpha = 0.55f) else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "seg_tab_border_$tabType"
            )
            val tabTextColor by animateColorAsState(
                targetValue = if (isSelected) {
                    if (tabType == "income") Emerald400 else Color.White
                } else {
                    Slate400
                },
                animationSpec = tween(180),
                label = "seg_tab_text_$tabType"
            )
            val tabScale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.97f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "seg_tab_scale_$tabType"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = tabScale
                        scaleY = tabScale
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(tabBg)
                    .border(1.dp, tabBorder, RoundedCornerShape(12.dp))
                    .clickable { onSelectType(tabType) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = tabTextColor,
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Bento-карточка выбора даты высотой 64.dp с месяцем/годом слева и мини-календарем дня справа.
 */
@Composable
fun ObsidianBentoDateCard(
    dateIso: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthNum = remember(dateIso) {
        try {
            if (dateIso.length >= 7) dateIso.substring(5, 7).toInt() else 1
        } catch (_: Exception) {
            1
        }
    }
    val dayStr = remember(dateIso) {
        try {
            if (dateIso.length >= 10) dateIso.substring(8, 10).toInt().toString() else "1"
        } catch (_: Exception) {
            "1"
        }
    }

    val monthNameRu = remember(monthNum) {
        listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )[(monthNum - 1).coerceIn(0, 11)]
    }

    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BentoFieldBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ДАТА",
                color = Slate400,
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = monthNameRu,
                color = Color.White,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Мини-календарь с числом выбранного дня в изумрудной/стеклянной рамке
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Emerald400.copy(alpha = 0.12f))
                .border(1.dp, Emerald400.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(Emerald400.copy(alpha = 0.7f))
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dayStr,
                    color = Color.White,
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
