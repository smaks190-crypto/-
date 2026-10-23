package com.example.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.GoalEntity
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.components.formatAmountTextFieldValue
import com.example.ui.components.formatFullCurrency
import com.example.ui.components.formatShortCurrency
import com.example.ui.components.parseAmountInput
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Sky400
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    val haptic = LocalHapticFeedback.current

    val totalSaved = remember(goals) { goals.sumOf { it.currentAmount.coerceAtLeast(0.0) } }
    val totalTarget = remember(goals) { goals.sumOf { it.targetAmount.coerceAtLeast(0.0) } }
    val overallProgressFraction = remember(totalSaved, totalTarget) {
        if (totalTarget > 0.0) (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f) else 0f
    }
    val animatedOverallProgress by animateFloatAsState(
        targetValue = overallProgressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "overall_goal_progress"
    )
    val overallPercent = (overallProgressFraction * 100f).roundToInt()

    completedGoalName?.let { name ->
        SwipeToDismissDialog(onDismissRequest = onDismissCompletedGoal) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Emerald400.copy(alpha = 0.15f))
                                .border(1.dp, Emerald400.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🐸", fontSize = 20.sp)
                        }
                        Column {
                            Text(
                                text = "Цель взята!",
                                color = Color.White,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = "Давид Жабов гордится дисциплиной",
                                color = Emerald400,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Копилка «$name» закрыта на 100%!",
                            color = Color.White,
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Вы полностью собрали целевую сумму. Цель перенесена в архив достижений.",
                            color = Slate300,
                            fontFamily = ManropeFontFamily,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismissCompletedGoal,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Отлично! 🥳",
                                color = DarkBg,
                                fontFamily = ManropeFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
    ) {
        // 1. HERO OBSIDIAN PORTFOLIO CARD
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(GlassHighlight, GlassBorder)
                    )
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Radial Emerald Glow in top-right
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 50.dp, y = (-50).dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Emerald500.copy(alpha = 0.22f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "КАПИТАЛ И КОПИЛКИ",
                                    color = Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                RollingCurrencyText(
                                    text = formatFullCurrency(totalSaved),
                                    color = Color.White,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                    onOpenAddGoalModal()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("add_goal_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Новая цель",
                                    tint = DarkBg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Цель",
                                    color = DarkBg,
                                    fontFamily = ManropeFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Overall progress bar + Target summary
                        if (goals.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Из общей цели ${formatFullCurrency(totalTarget)}",
                                        color = Slate400,
                                        fontFamily = ManropeFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Emerald400.copy(alpha = 0.14f))
                                            .border(1.dp, Emerald400.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$overallPercent% выполнено",
                                            color = Emerald400,
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DarkBg)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animatedOverallProgress)
                                            .height(8.dp)
                                            .shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(4.dp),
                                                ambientColor = Emerald400,
                                                spotColor = Indigo400
                                            )
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Emerald400, Indigo400, Sky400)
                                                )
                                            )
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Пополнения целей автоматически учитываются в расходах периода и приближают вашу финансовую свободу.",
                                color = Slate400,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. EMPTY STATE OR GOALS LIST
        if (goals.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, GlassBorder),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Emerald400.copy(alpha = 0.12f))
                                .border(1.dp, Emerald400.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Emerald400,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Text(
                            text = "У вас пока нет активных копилок",
                            color = Color.White,
                            fontFamily = ManropeFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Создайте цель (отпуск, подушка безопасности или гаджет) — Давид рассчитает комфортный ежедневный шаг накоплений.",
                            color = Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
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

@Composable
fun GoalCardItem(
    goal: GoalEntity,
    onAddProgress: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    var progressInput by remember { mutableStateOf(TextFieldValue("")) }
    var isPendingDelete by remember { mutableStateOf(false) }

    val progressFraction = if (goal.targetAmount > 0.0) {
        (goal.currentAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "goal_card_progress"
    )
    val percent = (progressFraction * 100f).roundToInt().coerceIn(0, 100)
    val remainingToSave = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)

    // Forecast calculation: recommended daily & monthly contribution
    val (monthsLeftInYear, daysLeftForPace) = remember {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH) // 0..11
        val months = (12 - currentMonth).coerceAtLeast(1)
        val maxDayOfYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        val currentDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val daysLeft = (maxDayOfYear - currentDayOfYear).coerceAtLeast(30)
        months to daysLeft
    }
    val recommendedMonthly = remember(remainingToSave, monthsLeftInYear) {
        if (remainingToSave <= 0.0) 0.0 else ceil(remainingToSave / monthsLeftInYear)
    }
    val recommendedDaily = remember(remainingToSave, daysLeftForPace) {
        if (remainingToSave <= 0.0) 0.0 else ceil(remainingToSave / daysLeftForPace)
    }

    val submitAmount = {
        val amount = parseAmountInput(progressInput.text)
        if (amount > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onAddProgress(amount)
            progressInput = TextFieldValue("")
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val cardBg = if (isPendingDelete) Rose500.copy(alpha = 0.9f) else ObsidianSurface
    val cardBorder = if (isPendingDelete) {
        BorderStroke(1.5.dp, Rose500)
    } else {
        BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(GlassHighlight, GlassBorder))
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        shape = RoundedCornerShape(24.dp),
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
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!isPendingDelete) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 30.dp, y = (-30).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Emerald400.copy(alpha = 0.14f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                                text = "Нажмите на карточку для удаления цели",
                                color = Color.White,
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Emerald400.copy(alpha = 0.14f))
                                    .border(1.dp, Emerald400.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = goal.name,
                                    color = Color.White,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (remainingToSave > 0) {
                                        "Осталось: ${formatFullCurrency(remainingToSave)}"
                                    } else {
                                        "Цель достигнута!"
                                    },
                                    color = Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Emerald400.copy(alpha = 0.14f))
                                    .border(1.dp, Emerald400.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$percent%",
                                    color = Emerald400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isPendingDelete = true }
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "✕",
                                    color = Slate400,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Current vs Target Amount Row in Space Grotesk
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    RollingCurrencyText(
                        text = formatFullCurrency(goal.currentAmount),
                        color = if (isPendingDelete) Color.White else Emerald400,
                        fontFamily = SpaceGroteskFontFamily,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "из ",
                            color = if (isPendingDelete) Color.White.copy(alpha = 0.8f) else Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp
                        )
                        RollingCurrencyText(
                            text = formatFullCurrency(goal.targetAmount),
                            color = if (isPendingDelete) Color.White.copy(alpha = 0.9f) else Slate300,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Glowing Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isPendingDelete) Color.White.copy(alpha = 0.25f) else DarkBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(8.dp)
                            .shadow(
                                elevation = if (isPendingDelete) 0.dp else 6.dp,
                                shape = RoundedCornerShape(4.dp),
                                ambientColor = Emerald400,
                                spotColor = Indigo400
                            )
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isPendingDelete) {
                                    Brush.horizontalGradient(listOf(Color.White, Color.White))
                                } else {
                                    Brush.horizontalGradient(listOf(Emerald400, Indigo400, Sky400))
                                }
                            )
                    )
                }

                // 🐸 David Zhabov Daily Savings Insight & 1-Tap Daily Step
                if (!isPendingDelete && remainingToSave > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianElevated.copy(alpha = 0.85f))
                            .border(1.dp, Indigo500.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Indigo400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "🐸 ИНСАЙТ ДАВИДА • КОМФОРТНЫЙ ТЕМП",
                                    color = Indigo400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (recommendedDaily > 0) {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onAddProgress(recommendedDaily)
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Emerald400.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "+ Шаг дня (${formatShortCurrency(recommendedDaily)})",
                                        color = Emerald400,
                                        fontFamily = SpaceGroteskFontFamily,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Откладывай по ${formatFullCurrency(recommendedDaily)}/день (~${formatShortCurrency(recommendedMonthly)}/мес), чтобы закрыть копилку без нагрузки на бюджет.",
                            color = Slate300,
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp
                        )
                    }

                    // Quick Preset Chips (+500, +1 000, +5 000)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(500.0, 1000.0, 5000.0).forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkBg)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onAddProgress(preset)
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${formatShortCurrency(preset)}",
                                    color = Emerald400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Custom Amount Input + Submit Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = progressInput,
                        onValueChange = { progressInput = formatAmountTextFieldValue(progressInput, it) },
                        placeholder = {
                            Text(
                                text = "+ Своя сумма",
                                color = if (isPendingDelete) Color.White.copy(alpha = 0.6f) else Slate500,
                                fontFamily = ManropeFontFamily,
                                fontSize = 12.sp
                            )
                        },
                        suffix = {
                            Text(
                                text = "₽",
                                color = if (isPendingDelete) Color.White else Emerald400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
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
                            unfocusedBorderColor = if (isPendingDelete) Color.White.copy(alpha = 0.4f) else GlassBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Button(
                        onClick = { submitAmount() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPendingDelete) Color.White else Emerald400
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Внести",
                            color = if (isPendingDelete) Rose500 else DarkBg,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
