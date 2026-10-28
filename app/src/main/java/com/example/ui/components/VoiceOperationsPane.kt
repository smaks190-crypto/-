package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.BudgetViewModel

private val VoiceBentoBg = Color(0xFF0B111E)
private val MixedExpenseAccent = Color(0xFFFF6B8B)
private val LiveAmountRegex = Regex("""(\d[\d\s]{0,9})""")

private data class LiveVoiceChipPreview(
    val categoryName: String,
    val type: String,
    val amount: Double?
)

internal data class VoiceBatchSummary(
    val count: Int,
    val totalIncome: Double,
    val totalExpense: Double,
    val netBalance: Double,
    val hasIncomes: Boolean,
    val hasExpenses: Boolean,
    val isMixed: Boolean,
    val headerSubtitleText: String,
    val saveButtonPlainText: String
)

internal fun calculateVoiceBatchSummary(operations: List<ParsedVoiceOperation>): VoiceBatchSummary {
    val totalIncome = operations.asSequence().filter { it.type == "income" }.sumOf { it.amount }
    val totalExpense = operations.asSequence().filter { it.type != "income" }.sumOf { it.amount }
    val hasIncomes = totalIncome > 0.0
    val hasExpenses = totalExpense > 0.0
    val isMixed = hasIncomes && hasExpenses
    val netBalance = totalIncome - totalExpense
    val count = operations.size

    val headerSubtitleText = when {
        isMixed -> {
            val sign = if (netBalance >= 0.0) "+" else "-"
            "Итог: $sign${formatRublesCompact(kotlin.math.abs(netBalance))} ₽"
        }
        hasIncomes -> "+${formatRublesCompact(totalIncome)} ₽"
        hasExpenses -> "-${formatRublesCompact(totalExpense)} ₽"
        else -> "0 ₽"
    }

    val saveButtonPlainText = when {
        isMixed -> "Сохранить ($count) • +${formatRublesCompact(totalIncome)} ₽ / -${formatRublesCompact(totalExpense)} ₽"
        hasIncomes -> "Сохранить ($count) • +${formatRublesCompact(totalIncome)} ₽"
        hasExpenses -> "Сохранить ($count) • -${formatRublesCompact(totalExpense)} ₽"
        else -> "Сохранить ($count) • 0 ₽"
    }

    return VoiceBatchSummary(
        count = count,
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        netBalance = netBalance,
        hasIncomes = hasIncomes,
        hasExpenses = hasExpenses,
        isMixed = isMixed,
        headerSubtitleText = headerSubtitleText,
        saveButtonPlainText = saveButtonPlainText
    )
}

internal fun formatLastAddedVoiceOperationText(operations: List<ParsedVoiceOperation>): String {
    val lastOp = operations.lastOrNull() ?: return ""
    val label = lastOp.subcategory.ifBlank { lastOp.title.ifBlank { lastOp.category } }.trim()
    return "$label ${formatRublesCompact(lastOp.amount)} ₽"
}

private fun extractLiveVoiceChipPreview(
    liveText: String,
    categories: List<CategoryEntity>
): LiveVoiceChipPreview? {
    val cleaned = liveText.trim()
    if (cleaned.length < 2 || cleaned.startsWith("Слушаю") || cleaned.startsWith("✨")) return null

    val amountMatch = LiveAmountRegex.findAll(cleaned)
        .mapNotNull { m -> m.value.replace(" ", "").toDoubleOrNull() }
        .lastOrNull { it > 0.0 }

    val matchedCat = detectCategoryAndTypeByTitle(cleaned, categories, "expense")
    if (matchedCat == null && amountMatch == null) return null

    return LiveVoiceChipPreview(
        categoryName = matchedCat?.second ?: "Расход",
        type = matchedCat?.first ?: "expense",
        amount = amountMatch
    )
}

@Composable
fun VoiceOperationsPane(
    viewModel: BudgetViewModel,
    editableList: SnapshotStateList<ParsedVoiceOperation>,
    categories: List<CategoryEntity>,
    selectedDate: String,
    contentAlpha: Float,
    editingIndex: Int?,
    onEditingIndexChanged: (Int?) -> Unit,
    onDismissVoiceOperations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val voiceManager = viewModel.voiceInputManager
    val isListening by voiceManager.isListening.collectAsState()
    val rmsDb by voiceManager.rmsDb.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val partialText by voiceManager.partialText.collectAsState()
    val isAnalyzingVoice by viewModel.isAnalyzingVoice.collectAsState()

    val currentEditingIndex = editingIndex?.takeIf { it in editableList.indices }
    if (editingIndex != currentEditingIndex) {
        onEditingIndexChanged(currentEditingIndex)
    }

    val batchSummary = calculateVoiceBatchSummary(editableList)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (currentEditingIndex == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Emerald400.copy(alpha = 0.16f))
                            .border(1.dp, Emerald400.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Распознано: ${editableList.size}",
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (isListening || isAnalyzingVoice) {
                                Spacer(modifier = Modifier.width(8.dp))
                                // Стабильный фирменный изумрудный бейдж без прыжков ширины каждые 2 секунды
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Emerald400.copy(alpha = 0.16f))
                                        .border(1.dp, Emerald400.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (!isListening && isAnalyzingVoice) "ИИ АНАЛИЗ" else "● LIVE",
                                        color = Emerald400,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.6.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = batchSummary.headerSubtitleText,
                            color = if (batchSummary.isMixed) {
                                if (batchSummary.netBalance >= 0.0) Emerald400 else Rose500
                            } else if (batchSummary.hasIncomes) {
                                Emerald400
                            } else {
                                Rose500
                            },
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Slate800.copy(alpha = 0.85f))
                        .border(1.dp, Slate700, CircleShape)
                        .clickable { onDismissVoiceOperations() }
                        .testTag("close_voice_operations_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                editableList.forEachIndexed { index, op ->
                    CompactParsedOperationCard(
                        operation = op,
                        onClick = { onEditingIndexChanged(index) },
                        onDelete = {
                            if (index in editableList.indices) {
                                editableList.removeAt(index)
                                if (editableList.isEmpty() && !isListening && !isAnalyzingVoice) {
                                    onDismissVoiceOperations()
                                }
                            }
                        }
                    )
                }
            }

            // Быстрые 1-тап пресеты для корректировки последней распознанной суммы без клавиатуры (Пункт 3)
            if (editableList.isNotEmpty()) {
                val lastIdx = editableList.lastIndex
                val lastOp = editableList[lastIdx]
                val quickPresets = listOf(
                    "+100 ₽" to { (lastOp.amount + 100.0) },
                    "+500 ₽" to { (lastOp.amount + 500.0) },
                    "-100 ₽" to { (lastOp.amount - 100.0).coerceAtLeast(10.0) },
                    "÷2 пополам" to { (lastOp.amount / 2.0).coerceAtLeast(1.0) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1-ТАП:",
                        color = Slate400,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    quickPresets.forEach { (label, calcNewAmount) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(VoiceBentoBg)
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val updatedAmount = kotlin.math.round(calcNewAmount())
                                    editableList[lastIdx] = lastOp.copy(amount = updatedAmount)
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Emerald400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isListening || isAnalyzingVoice) {
                val lastAddedOp = editableList.lastOrNull()
                val lastAddedOpText = formatLastAddedVoiceOperationText(editableList)
                val liveSpeechText = when {
                    lastAddedOpText.isNotBlank() -> lastAddedOpText
                    partialText.isNotBlank() && partialText != "Говорите..." -> partialText
                    recognizedText.isNotBlank() -> recognizedText
                    else -> ""
                }
                val liveHint = when {
                    isAnalyzingVoice && !isListening -> "✨ Давид разбирает операции через Gemini..."
                    liveSpeechText.isNotBlank() -> liveSpeechText
                    else -> "Слушаю вас... Говорите ещё"
                }

                // В капсуле Gemini Live всегда отображаем чип именно последней добавленной операции
                val liveChipPreview = remember(lastAddedOp, liveSpeechText, categories) {
                    if (lastAddedOp != null) {
                        LiveVoiceChipPreview(
                            categoryName = lastAddedOp.category,
                            type = lastAddedOp.type,
                            amount = lastAddedOp.amount
                        )
                    } else {
                        extractLiveVoiceChipPreview(liveSpeechText, categories)
                    }
                }

                VolumetricLiquidAuroraGlow(
                    isRecording = isListening || isAnalyzingVoice,
                    rmsLevel = if (isListening) rmsDb else 3.2f,
                    cornerRadius = 20.dp,
                    showGlassBorder = true,
                    fillBackground = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(64.dp)
                        .clickable {
                            if (isListening) {
                                viewModel.stopVoiceRecordingAndProcess()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Emerald400.copy(alpha = 0.16f))
                                .border(1.dp, Emerald400.copy(alpha = 0.45f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAnalyzingVoice && !isListening) {
                                CircularProgressIndicator(
                                    color = Emerald400,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Микрофон",
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = liveHint,
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                )
                            )
                            Text(
                                text = if (!isListening && isAnalyzingVoice) {
                                    "Gemini Live • финальный разбор"
                                } else {
                                    "Gemini Live • слушаю в реальном времени"
                                },
                                color = Emerald400.copy(alpha = 0.85f),
                                fontFamily = ManropeFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        // Справа: чип категории и суммы последней распознанной операции
                        if (liveChipPreview != null) {
                            val spec = remember(liveChipPreview.categoryName, liveChipPreview.type) {
                                resolveCategoryVisualSpec(liveChipPreview.categoryName, liveChipPreview.type)
                            }
                            val chipSign = if (liveChipPreview.type == "income") "+" else "-"
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(VoiceBentoBg.copy(alpha = 0.90f))
                                    .border(1.dp, spec.color.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = spec.icon,
                                    contentDescription = null,
                                    tint = spec.color,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = liveChipPreview.amount?.let { "$chipSign${formatRublesCompact(it)} ₽" }
                                        ?: liveChipPreview.categoryName,
                                    color = if (liveChipPreview.type == "income") Emerald400 else Color.White,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val saveButtonAnnotatedText = buildAnnotatedString {
                if (batchSummary.isMixed) {
                    withStyle(SpanStyle(color = Color.White)) {
                        append("Сохранить (${batchSummary.count}) • ")
                    }
                    withStyle(SpanStyle(color = Emerald400)) {
                        append("+${formatRublesCompact(batchSummary.totalIncome)} ₽")
                    }
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.55f))) {
                        append(" / ")
                    }
                    withStyle(SpanStyle(color = MixedExpenseAccent)) {
                        append("-${formatRublesCompact(batchSummary.totalExpense)} ₽")
                    }
                } else {
                    withStyle(SpanStyle(color = Color.White)) {
                        append(batchSummary.saveButtonPlainText)
                    }
                }
            }

            // Фирменная кнопка «Сохранить» в едином стиле Obsidian Neo-Bank (с раздельным отображением доходов и расходов)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VoiceBentoBg)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Emerald400.copy(alpha = 0.28f),
                                NeonCyan.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        color = Emerald400.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        com.example.utils.GlobalConsoleLogger.i("UI", "Подтверждение сохранения ${editableList.size} распознанных операций")
                        if (isListening) {
                            voiceManager.onAudioChunkReady = null
                            voiceManager.stopListening()
                        }
                        if (editableList.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            com.example.davidapp.AchievementManager.unlock(context, "voice_master")
                            viewModel.confirmVoiceOperations(editableList.toList(), selectedDate)
                            viewModel.clearParsedVoiceOperations()
                            viewModel.setVoiceActive(false)
                            onDismissVoiceOperations()
                        } else {
                            Toast.makeText(context, "Нет операций для сохранения", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 12.dp)
                    .testTag("confirm_voice_operations_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = saveButtonAnnotatedText,
                    color = Color.White,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (batchSummary.isMixed) 14.sp else 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            val targetIndex = currentEditingIndex

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Редактировать операцию",
                    color = Color.White,
                    fontFamily = ManropeFontFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Slate800.copy(alpha = 0.85f))
                        .border(1.dp, Slate700, CircleShape)
                        .clickable { onEditingIndexChanged(null) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Назад к списку",
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                FullParsedOperationFormCard(
                    operation = editableList[targetIndex],
                    categories = categories,
                    viewModel = viewModel,
                    defaultDate = selectedDate,
                    showDeleteButton = editableList.size > 1,
                    onUpdate = { updated: ParsedVoiceOperation ->
                        if (targetIndex in editableList.indices) {
                            editableList[targetIndex] = updated
                        }
                    },
                    onDelete = {
                        if (targetIndex in editableList.indices) {
                            editableList.removeAt(targetIndex)
                            onEditingIndexChanged(null)
                            if (editableList.isEmpty()) {
                                onDismissVoiceOperations()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VoiceBentoBg)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Emerald400.copy(alpha = 0.28f),
                                NeonCyan.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .border(1.2.dp, Emerald400.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .clickable { onEditingIndexChanged(null) }
                    .testTag("save_detail_operation_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Готово",
                    color = Color.White,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
