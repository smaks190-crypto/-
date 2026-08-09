package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CategoryEntity
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
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

    val currentEditingIndex = editingIndex?.takeIf { it in editableList.indices }
    if (editingIndex != currentEditingIndex) {
        onEditingIndexChanged(currentEditingIndex)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .padding(start = 16.dp, top = 8.dp, end = 0.dp, bottom = 8.dp)
    ) {
        if (currentEditingIndex == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Emerald400.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Распознано: ${editableList.size}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val totalAmount = editableList.sumOf { if (it.type == "expense") -it.amount else it.amount }
                        Text(
                            text = if (totalAmount >= 0) "+${String.format(Locale.US, "%.0f", totalAmount)} ₽" else "${String.format(Locale.US, "%.0f", totalAmount)} ₽",
                            color = if (totalAmount >= 0) Emerald400 else Rose500,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                editableList.forEachIndexed { index, op ->
                    CompactParsedOperationCard(
                        operation = op,
                        onClick = { onEditingIndexChanged(index) },
                        onDelete = {
                            if (index in editableList.indices) {
                                editableList.removeAt(index)
                                if (editableList.isEmpty()) {
                                    onDismissVoiceOperations()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        com.example.utils.GlobalConsoleLogger.i("UI", "Подтверждение сохранения ${editableList.size} распознанных операций")
                        if (editableList.isNotEmpty()) {
                            viewModel.confirmVoiceOperations(editableList, selectedDate)
                            viewModel.clearParsedVoiceOperations()
                            viewModel.setVoiceActive(false)
                        } else {
                            Toast.makeText(context, "Нет операций для сохранения", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400.copy(alpha = 0.15f)),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                        .testTag("confirm_voice_operations_button")
                ) {
                    Text("Сохранить", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.width(28.dp))
                Spacer(modifier = Modifier.size(56.dp))
            }

        } else {
            val targetIndex = currentEditingIndex

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Редактировать операцию",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onEditingIndexChanged(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400.copy(alpha = 0.15f)),
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                        .testTag("save_detail_operation_button")
                ) {
                    Text("Готово", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.width(28.dp))
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }
}
