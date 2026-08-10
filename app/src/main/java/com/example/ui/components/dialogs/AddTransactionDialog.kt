package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.data.db.TransactionEntity
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddTransactionDialog(
    initialType: String = "expense",
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (type: String, date: String, category: String, subcategory: String, amount: Double) -> Unit,
    onSuggestCategory: (suspend (transactionName: String, type: String, categoriesList: List<String>) -> String)? = null,
    editingTransaction: TransactionEntity? = null
) {
    var type by remember { mutableStateOf(editingTransaction?.type ?: initialType) }
    var date by remember { mutableStateOf(editingTransaction?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var selectedCategory by remember {
        mutableStateOf(editingTransaction?.category ?: (categories.filter { it.type == type }.firstOrNull()?.name ?: ""))
    }
    var subcategory by remember { mutableStateOf(editingTransaction?.subcategory ?: "") }
    var amountText by remember {
        val initialAmount = editingTransaction?.amount?.let { if (it == 0.0) "" else if (it % 1 == 0.0) String.format(Locale.US, "%.0f", it) else it.toString() } ?: ""
        mutableStateOf(TextFieldValue(initialAmount))
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var aiSuggestedCategory by remember { mutableStateOf<String?>(null) }
    var isAiSuggesting by remember { mutableStateOf(false) }
    var userManuallySelectedCategory by remember { mutableStateOf(false) }

    var neonFlickerValue by remember { mutableStateOf(1f) }
    var isFlickerFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        neonFlickerValue = 1f
        kotlinx.coroutines.delay(1300)
        
        val sequence = listOf(
            0.1f to 70L,
            0.9f to 90L,
            0.0f to 120L,
            0.8f to 60L,
            0.05f to 100L,
            0.7f to 50L,
            0.0f to 180L,
            0.95f to 60L,
            0.1f to 80L,
            0.4f to 50L,
            0.0f to 200L
        )
        
        for (step in sequence) {
            neonFlickerValue = step.first
            kotlinx.coroutines.delay(step.second)
        }
        
        isFlickerFinished = true
        
        while (true) {
            kotlinx.coroutines.delay((3000..6500).random().toLong())
            val sparkSequence = listOf(
                0.15f to 40L,
                0.0f to 60L,
                0.25f to 50L,
                0.0f to 40L
            )
            for (spark in sparkSequence) {
                neonFlickerValue = spark.first
                kotlinx.coroutines.delay(spark.second)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "border_gradient_edit")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "offset"
    )

    val borderAlpha = 1f
    val dynamicGradient = Brush.linearGradient(
        colors = listOf(
            Indigo500.copy(alpha = borderAlpha),
            Emerald400.copy(alpha = borderAlpha),
            Rose500.copy(alpha = borderAlpha),
            Indigo500.copy(alpha = borderAlpha)
        ),
        start = Offset(offset, offset), end = Offset(offset + 600f, offset + 600f),
        tileMode = TileMode.Repeated
    )

    val progress = offset / 600f
    val getGradientColor = { p: Float ->
        val norm = p % 1f
        val phase = if (norm < 0f) norm + 1f else norm
        when {
            phase < 0.3333f -> {
                val t = phase / 0.3333f
                androidx.compose.ui.graphics.lerp(Indigo500, Emerald400, t)
            }
            phase < 0.6666f -> {
                val t = (phase - 0.3333f) / 0.3333f
                androidx.compose.ui.graphics.lerp(Emerald400, Rose500, t)
            }
            else -> {
                val t = (phase - 0.6666f) / 0.3334f
                androidx.compose.ui.graphics.lerp(Rose500, Indigo500, t)
            }
        }
    }
    val neonColor1 = getGradientColor(progress)
    val neonColor2 = getGradientColor(progress + 0.6666f)

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(subcategory, type) {
        val trimmed = subcategory.trim()
        if (trimmed.length >= 3 && onSuggestCategory != null) {
            kotlinx.coroutines.delay(600)
            if (subcategory.trim() == trimmed) {
                isAiSuggesting = true
                val catNames = categories.filter { it.type == type }.map { it.name }
                val suggested = onSuggestCategory(trimmed, type, catNames)
                isAiSuggesting = false
                if (suggested.isNotBlank()) {
                    aiSuggestedCategory = suggested
                    if (!userManuallySelectedCategory || selectedCategory.isBlank()) {
                        selectedCategory = suggested
                    }
                }
            }
        } else if (trimmed.isBlank()) {
            aiSuggestedCategory = null
            isAiSuggesting = false
        }
    }

    val filteredCategories = remember(categories, type) { categories.filter { it.type == type } }

    val scrollState = rememberScrollState()
    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        swipeEnabledState.value = !scrollState.isScrollInProgress
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(2.dp, dynamicGradient),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                    ambientColor = neonColor1.copy(alpha = 0.8f),
                    spotColor = neonColor2.copy(alpha = 0.8f)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Slate700)
                    )
                }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (editingTransaction != null) "Редактировать операцию" else "Добавить операцию",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Rose500.copy(alpha = 0.15f))
                            .border(1.dp, Rose500.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Rose500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkBg)
                                    .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                    .clickable { dropdownExpanded = true }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedCategory.ifEmpty { "Категория" },
                                        color = if (selectedCategory.isNotEmpty()) Color.White else Slate400,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (selectedCategory == aiSuggestedCategory && !aiSuggestedCategory.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Indigo500.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("✨ ИИ", color = Indigo500, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(Slate900)
                            ) {
                                filteredCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = Color.White) },
                                        onClick = {
                                            selectedCategory = cat.name
                                            userManuallySelectedCategory = true
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
                                userManuallySelectedCategory = false
                                selectedCategory = categories.filter { it.type == newType }.firstOrNull()?.name ?: ""
                                aiSuggestedCategory = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ОПИСАНИЕ / НАЗВАНИЕ ОПЕРАЦИИ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (onSuggestCategory != null) {
                        Row(
                            modifier = Modifier
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    clip = false,
                                    ambientColor = Indigo500,
                                    spotColor = Indigo500
                                )
                                .background(Indigo500.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, Indigo500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable {
                                    if (subcategory.isNotBlank() && !isAiSuggesting) {
                                        coroutineScope.launch {
                                            isAiSuggesting = true
                                            val catNames = categories.filter { it.type == type }.map { it.name }
                                            val suggested = onSuggestCategory(subcategory.trim(), type, catNames)
                                            isAiSuggesting = false
                                            if (suggested.isNotBlank()) {
                                                aiSuggestedCategory = suggested
                                                selectedCategory = suggested
                                                userManuallySelectedCategory = true
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Indigo500,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ИИ Категория", color = Indigo500, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = subcategory,
                    onValueChange = { subcategory = it.capitalizeFirstLetter() },
                    placeholder = { Text("Например: Пятерочка, Такси, Зарплата", color = Slate400) },
                    modifier = Modifier.fillMaxWidth().testTag("transaction_description_input"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    trailingIcon = {
                        if (isAiSuggesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Indigo500,
                                strokeWidth = 2.dp
                            )
                        } else if (onSuggestCategory != null && subcategory.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isAiSuggesting = true
                                        val catNames = categories.filter { it.type == type }.map { it.name }
                                        val suggested = onSuggestCategory(subcategory.trim(), type, catNames)
                                        isAiSuggesting = false
                                        if (suggested.isNotBlank()) {
                                            aiSuggestedCategory = suggested
                                            selectedCategory = suggested
                                            userManuallySelectedCategory = true
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("ai_suggest_category_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Определить категорию через Gemini",
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (isAiSuggesting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Indigo500, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gemini подбирает категорию...", color = Indigo500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                } else if (!aiSuggestedCategory.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(10.dp),
                                clip = false,
                                ambientColor = Indigo500,
                                spotColor = Indigo500
                            )
                            .background(Color(0xFF0F172A).copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(listOf(Indigo500, Emerald400)),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier
                                .size(14.dp)
                                .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = Emerald400, spotColor = Emerald400)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ИИ Предложение",
                                color = Indigo500,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Категория: $aiSuggestedCategory",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (selectedCategory != aiSuggestedCategory) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Emerald400.copy(alpha = 0.2f))
                                    .clickable {
                                        selectedCategory = aiSuggestedCategory!!
                                        userManuallySelectedCategory = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Применить",
                                    color = Emerald400,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("transaction_amount_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg,
                                focusedBorderColor = Emerald400,
                                unfocusedBorderColor = Slate800,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column {
                        Text("ДАТА", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        CompactDatePickerField(
                            value = date,
                            onDateSelected = { date = it }
                        )
                    }
                }

                val parsedAmount = remember(amountText.text) { parseAmountInput(amountText.text) }
                val isAmountValid = parsedAmount > 0
                val isCategoryValid = selectedCategory.trim().isNotBlank()
                val isDescriptionValid = subcategory.trim().isNotBlank()
                val isDateValid = date.trim().isNotBlank()
                val isFormValid = isAmountValid && isCategoryValid && isDescriptionValid && isDateValid

                val visualNeonLevel = if (isFormValid) {
                    1f
                } else {
                    neonFlickerValue
                }

                val currentContainerColor = androidx.compose.ui.graphics.lerp(
                    Slate800,
                    Emerald400.copy(alpha = 0.15f),
                    visualNeonLevel
                )
                val currentContentColor = androidx.compose.ui.graphics.lerp(
                    Slate500,
                    Emerald400,
                    visualNeonLevel
                )
                val currentBorderColor = androidx.compose.ui.graphics.lerp(
                    Slate700,
                    Emerald400.copy(alpha = 0.5f),
                    visualNeonLevel
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            if (isFormValid) {
                                onSave(type, date, selectedCategory.ifEmpty { "Прочее" }, subcategory.trim(), parsedAmount)
                                onDismiss()
                            }
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentContainerColor,
                            contentColor = currentContentColor,
                            disabledContainerColor = currentContainerColor,
                            disabledContentColor = currentContentColor
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = (visualNeonLevel * 14).dp,
                                shape = CircleShape,
                                ambientColor = Emerald400,
                                spotColor = Emerald400
                            )
                            .border(
                                width = 1.dp,
                                color = currentBorderColor,
                                shape = CircleShape
                            )
                            .testTag("save_transaction_button")
                    ) {
                        Text("Сохранить", color = currentContentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
