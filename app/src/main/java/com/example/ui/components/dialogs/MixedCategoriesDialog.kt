package com.example.ui.components.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SwipeToDismissDialog
import com.example.ui.components.formatFullCurrency
import com.example.ui.theme.DarkCard
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun MixedCategoriesDialog(
    categoryTotalsMap: List<Pair<String, Double>>,
    getCategoryColor: (String) -> Color,
    onSelectCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    SwipeToDismissDialog(
        onDismissRequest = onDismiss,
        isAtTop = { scrollState.value == 0 }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .shadow(
                    elevation = 26.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Indigo500.copy(alpha = 0.5f),
                    spotColor = Emerald400.copy(alpha = 0.5f)
                ),
            color = DarkCard,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(
                width = 1.4.dp,
                brush = Brush.linearGradient(
                    listOf(Emerald400.copy(alpha = 0.65f), Indigo500.copy(alpha = 0.55f), Rose500.copy(alpha = 0.55f))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Верхний индикатор-пилюля
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

                // Заголовок без верхнего крестика
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✨ Смешанные категории",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Indigo500.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "+${categoryTotalsMap.drop(2).size}",
                                color = Indigo500,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Список смешанных категорий
                val remainingCategories = categoryTotalsMap.drop(2)
                val totalSum = categoryTotalsMap.sumOf { it.second }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    remainingCategories.forEach { (catName, sumAmt) ->
                        val pillColor = getCategoryColor(catName)
                        val pct = if (totalSum > 0) {
                            ((sumAmt / totalSum) * 100).let { kotlin.math.round(it).toInt() }
                        } else 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Slate900.copy(alpha = 0.55f))
                                .border(1.dp, Slate800, RoundedCornerShape(14.dp))
                                .clickable { onSelectCategory(catName) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(pillColor)
                                )
                                Text(
                                    text = if (pct > 0) "$catName $pct%" else catName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = formatFullCurrency(sumAmt),
                                color = Slate300,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Единая нижняя панель управления с квадратной кнопкой закрытия ✕ справа
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Slate800.copy(alpha = 0.9f))
                            .border(1.dp, Slate700, RoundedCornerShape(16.dp))
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
    }
}
