package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import com.example.ui.theme.Rose500
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.GoalEntity
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.formatAmountInput
import com.example.ui.components.formatAmountTextFieldValue
import com.example.ui.components.formatFullCurrency
import com.example.ui.components.parseAmountInput
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg

@Composable
fun GoalsScreen(
    goals: List<GoalEntity>,
    onOpenAddGoalModal: () -> Unit,
    onAddGoalProgress: (goalId: String, amount: Double) -> Unit,
    onDeleteGoal: (goalId: String) -> Unit,
    completedGoalName: String? = null,
    onDismissCompletedGoal: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    completedGoalName?.let { name ->
        com.example.ui.components.SwipeToDismissDialog(onDismissRequest = onDismissCompletedGoal) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(24.dp),
                color = Slate900,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("🎉 Поздравляем!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Цель «$name» успешно достигнута!", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Вы полностью накопили нужную сумму. Цель выполнена и автоматически удалена из списка активных целей.", color = Emerald400, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismissCompletedGoal,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ура! 🥳", color = DarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.8f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Управление целями", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Внесенные пополнения автоматически списываются в расходы.", color = Slate400, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onOpenAddGoalModal()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_goal_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DarkBg)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Цель", color = DarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        if (goals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("У вас нет активных целей. Добавьте новую!", color = Slate400, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    }
            ) {
                items(goals, key = { it.id }) { goal ->
                    GoalCardItem(
                        goal = goal,
                        onAddProgress = { amount -> onAddGoalProgress(goal.id, amount) },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun GoalCardItem(
    goal: GoalEntity,
    onAddProgress: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var progressInput by remember { mutableStateOf(TextFieldValue("")) }
    var isPendingDelete by remember { mutableStateOf(false) }

    val percent = Math.min(100, Math.round((goal.currentAmount / goal.targetAmount) * 100))

    val submitAmount = {
        val amount = parseAmountInput(progressInput.text)
        if (amount > 0) {
            onAddProgress(amount)
            progressInput = TextFieldValue("")
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val cardBg = if (isPendingDelete) Rose500.copy(alpha = 0.9f) else Slate900.copy(alpha = 0.8f)
    val cardBorder = if (isPendingDelete) androidx.compose.foundation.BorderStroke(1.5.dp, Rose500) else androidx.compose.foundation.BorderStroke(1.dp, Slate800)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                focusManager.clearFocus()
                keyboardController?.hide()
                if (isPendingDelete) {
                    onDelete()
                }
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isPendingDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "⚠️ Нажмите для подтверждения",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    IconButton(
                        onClick = { isPendingDelete = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Отмена",
                            tint = Color.White
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = goal.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isPendingDelete = true }
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = Slate400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RollingCurrencyText(
                    text = formatFullCurrency(goal.currentAmount),
                    color = if (isPendingDelete) Color.White else Emerald400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("из ", color = if (isPendingDelete) Color.White.copy(alpha = 0.8f) else Slate400, fontSize = 12.sp)
                    RollingCurrencyText(
                        text = formatFullCurrency(goal.targetAmount),
                        color = if (isPendingDelete) Color.White.copy(alpha = 0.9f) else Slate400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(" ($percent%)", color = if (isPendingDelete) Color.White.copy(alpha = 0.9f) else Slate400, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isPendingDelete) Color.White.copy(alpha = 0.25f) else DarkBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent / 100f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPendingDelete) Color.White else Emerald400)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = progressInput,
                    onValueChange = { progressInput = formatAmountTextFieldValue(progressInput, it) },
                    placeholder = { Text("+ Сумма", color = if (isPendingDelete) Color.White.copy(alpha = 0.6f) else Slate400, fontSize = 11.sp) },
                    suffix = { Text("₽", color = if (isPendingDelete) Color.White else Emerald400, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submitAmount() }
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isPendingDelete) Color.White.copy(alpha = 0.15f) else DarkBg,
                        unfocusedContainerColor = if (isPendingDelete) Color.White.copy(alpha = 0.15f) else DarkBg,
                        focusedBorderColor = if (isPendingDelete) Color.White else Emerald400,
                        unfocusedBorderColor = if (isPendingDelete) Color.White.copy(alpha = 0.4f) else Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = { submitAmount() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isPendingDelete) Color.White else Emerald400),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Внести", color = if (isPendingDelete) Rose500 else DarkBg, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}
