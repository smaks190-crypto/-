package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.viewmodel.BudgetViewModel
import java.util.Locale

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
    val voiceManager = viewModel.voiceInputManager
    val isListening by voiceManager.isListening.collectAsState()
    val rmsDb by voiceManager.rmsDb.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val partialText by voiceManager.partialText.collectAsState()
    val isAnalyzingVoice by viewModel.isAnalyzingVoice.collectAsState()
    val normalizedAmplitude = remember(rmsDb) {
        (rmsDb / 12f).coerceIn(0f, 1f)
    }

    val currentEditingIndex = editingIndex?.takeIf { it in editableList.indices }
    if (editingIndex != currentEditingIndex) {
        onEditingIndexChanged(currentEditingIndex)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Emerald400.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Распознано: ${editableList.size}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isListening || isAnalyzingVoice) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Indigo500.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAnalyzingVoice) "LIVE • ИИ..." else "LIVE",
                                        color = Indigo500,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        val totalAmount = editableList.sumOf { if (it.type == "expense") -it.amount else it.amount }
                        Text(
                            text = if (totalAmount >= 0) "+${String.format(Locale.US, "%.0f", totalAmount)} ₽" else "${String.format(Locale.US, "%.0f", totalAmount)} ₽",
                            color = if (totalAmount >= 0) Emerald400 else Rose500,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
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

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
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

            AnimatedVisibility(visible = isListening || isAnalyzingVoice) {
                VolumetricLiquidAuroraGlow(
                    isRecording = isListening || isAnalyzingVoice,
                    rmsLevel = if (isListening) rmsDb else 4.8f,
                    cornerRadius = 24.dp,
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
                    val liveHint = when {
                        isAnalyzingVoice && !isListening -> "✨ Давид разбирает операции через Gemini..."
                        recognizedText.isNotBlank() -> recognizedText
                        partialText.isNotBlank() && partialText != "Говорите..." -> partialText
                        else -> "Слушаю вас... Говорите ещё"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAnalyzingVoice && !isListening) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Микрофон",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = liveHint,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(
                                    shadow = androidx.compose.ui.graphics.Shadow(
                                        color = Color.Black.copy(alpha = 0.75f),
                                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                        blurRadius = 8f
                                    )
                                )
                            )
                            Text(
                                text = if (isAnalyzingVoice) "Gemini Live • анализ" else "Gemini Live • слушаю в реальном времени",
                                color = Color(0xFFE0F2FE).copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        if (isAnalyzingVoice && isListening) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val totalAbsSum = editableList.sumOf { it.amount }

            Button(
                onClick = {
                    com.example.utils.GlobalConsoleLogger.i("UI", "Подтверждение сохранения ${editableList.size} распознанных операций")
                    if (isListening) {
                        voiceManager.onAudioChunkReady = null
                        voiceManager.stopListening()
                    }
                    if (editableList.isNotEmpty()) {
                        com.example.davidapp.AchievementManager.unlock(context, "voice_master")
                        viewModel.confirmVoiceOperations(editableList.toList(), selectedDate)
                        viewModel.clearParsedVoiceOperations()
                        viewModel.setVoiceActive(false)
                        onDismissVoiceOperations()
                    } else {
                        Toast.makeText(context, "Нет операций для сохранения", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(NeonEmerald, NeonIndigo)
                        ),
                        shape = CircleShape
                    )
                    .testTag("confirm_voice_operations_button")
            ) {
                Text(
                    text = "Сохранить (${editableList.size}) • ${formatRublesCompact(totalAbsSum)} ₽",
                    color = DarkBg,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
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

            Button(
                onClick = { onEditingIndexChanged(null) },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald400.copy(alpha = 0.15f)),
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                    .testTag("save_detail_operation_button")
            ) {
                Text("Готово", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
