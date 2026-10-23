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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.viewmodel.BudgetViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var type by remember { mutableStateOf(initialType) }
    var date by remember {
        mutableStateOf(if (selectedDate.isNotBlank()) selectedDate else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }
    var selectedCategory by remember {
        mutableStateOf(categories.filter { it.type == type }.firstOrNull()?.name ?: "")
    }
    var subcategory by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf(TextFieldValue("")) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var aiSuggestedCategory by remember { mutableStateOf<String?>(null) }
    var isAiSuggesting by remember { mutableStateOf(false) }
    var userManuallySelectedCategory by remember { mutableStateOf(false) }
    var neonFlickerValue by remember { mutableStateOf(1f) }
    var isFlickerFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Initial bright solid glow
        neonFlickerValue = 1f
        kotlinx.coroutines.delay(1300)
        
        // Realistic rapid burnout flicker sequence (like a failing neon tube from the video reference)
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
        
        // Continuous background loop for occasional realistic micro-sparks/buzzing of the burnt neon!
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

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(subcategory, type) {
        val trimmed = subcategory.trim()
        if (trimmed.length >= 3) {
            kotlinx.coroutines.delay(600)
            if (subcategory.trim() == trimmed) {
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
        } else if (trimmed.isBlank()) {
            aiSuggestedCategory = null
            isAiSuggesting = false
        }
    }

    val filteredCategories = remember(categories, type) { categories.filter { it.type == type } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .padding(start = 16.dp, top = 12.dp, end = 0.dp, bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Добавить операцию",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Indigo500.copy(alpha = 0.2f))
                        .clickable {
                            if (subcategory.isNotBlank() && !isAiSuggesting) {
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
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Indigo500,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("ИИ Категория", color = Indigo500, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = subcategory,
                onValueChange = { subcategory = it.capitalizeFirstLetter() },
                placeholder = { Text("Например: Пятерочка, Такси, Зарплата", color = Slate400, fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_description_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkBg,
                    unfocusedContainerColor = DarkBg,
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

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
        }

        // Validation logic for required fields
        val parsedAmount = remember(amountText.text) { parseAmountInput(amountText.text) }
        val isFormValid = parsedAmount > 0 && selectedCategory.trim().isNotBlank() && subcategory.trim().isNotBlank() && date.trim().isNotBlank()

        val visualNeonLevel = if (isFormValid) 1f else neonFlickerValue

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (isFormValid) {
                        viewModel.addTransaction(
                            type = type,
                            date = date,
                            category = selectedCategory.ifEmpty { "Прочее" },
                            subcategory = subcategory.trim(),
                            amount = parsedAmount
                        )
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
                    .weight(1f)
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

            Spacer(modifier = Modifier.width(28.dp))
            Spacer(modifier = Modifier.size(56.dp))
        }
    }
}
