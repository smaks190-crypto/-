package com.example.ui.components.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.components.formatFullCurrency

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionCategoryChipsSection(
    selectedPeriod: String,
    onSelectPeriod: (String) -> Unit,
    chartViewMode: String,
    onSelectChartViewMode: (String) -> Unit,
    categoryTotalsMap: List<Pair<String, Double>>,
    shouldGroup: Boolean,
    isDrilledDownToMixed: Boolean,
    onSetDrilledDownToMixed: (Boolean) -> Unit,
    selectedCategoryFilter: String?,
    onSelectCategoryFilter: (String?) -> Unit,
    onOpenMixedCategoriesDialog: () -> Unit,
    getCategoryColor: (String) -> Color
) {
    // Period selector bar & Chart View Toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Period selector (Нед, Мес, Год)
        BoxWithConstraints(
            modifier = Modifier
                .width(180.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkBg)
                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                .padding(2.dp)
        ) {
            val barWidth = maxWidth
            val tabCount = 3
            val tabWidth = barWidth / tabCount

            val periodsList = listOf("week", "month", "year")
            val selectedIndex = periodsList.indexOf(
                if (selectedPeriod == "all") "month" else selectedPeriod
            ).coerceAtLeast(0)

            val animatedFraction by animateFloatAsState(
                targetValue = selectedIndex.toFloat(),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "period_tab_fraction"
            )

            // Moving selection indicator pill
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .offset(x = tabWidth * animatedFraction)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(10.dp),
                        ambientColor = Indigo500,
                        spotColor = Indigo500
                    )
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Indigo500, Indigo500.copy(alpha = 0.8f))
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = Indigo500.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    )
            )

            // Clickable labels
            Row(modifier = Modifier.fillMaxSize()) {
                listOf("week" to "Нед", "month" to "Мес", "year" to "Год").forEach { (pKey, pText) ->
                    val isSelected = selectedPeriod == pKey || (selectedPeriod == "all" && pKey == "month")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectPeriod(pKey) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pText,
                            color = if (isSelected) Color.White else Slate400,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Chart Mode buttons (Donut vs Bar)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DarkBg)
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (chartViewMode == "donut") Slate800 else Color.Transparent)
                    .clickable { onSelectChartViewMode("donut") }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text("🔄", fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (chartViewMode == "bar") Slate800 else Color.Transparent)
                    .clickable { onSelectChartViewMode("bar") }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text("📊", fontSize = 11.sp)
            }
        }
    }

    // Scrollable Category Flow Layout with grouping
    if (categoryTotalsMap.isNotEmpty()) {
        val totalCategorySum = categoryTotalsMap.sumOf { it.second }
        val categoriesScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .verticalScroll(categoriesScrollState)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDrilledDownToMixed) {
                    // Back button pill to go up to top-level
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkBg)
                            .border(1.dp, Indigo500.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .clickable {
                                onSetDrilledDownToMixed(false)
                                onSelectCategoryFilter(null)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "← Назад",
                            color = Indigo500,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Remaining child categories
                    val remainingCategories = categoryTotalsMap.drop(2)
                    remainingCategories.forEach { (catName, remainingSumAmt) ->
                        val isCatSelected = selectedCategoryFilter == catName
                        val pillColor = getCategoryColor(catName)
                        val pct = if (totalCategorySum > 0) {
                            ((remainingSumAmt / totalCategorySum) * 100).let { kotlin.math.round(it).toInt() }
                        } else 0

                        Row(
                            modifier = Modifier
                                .then(
                                    if (isCatSelected) {
                                        Modifier.shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(20.dp),
                                            ambientColor = pillColor,
                                            spotColor = pillColor
                                        )
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isCatSelected) pillColor else DarkBg)
                                .border(
                                    1.dp,
                                    if (isCatSelected) pillColor else Slate800,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    onSelectCategoryFilter(if (isCatSelected) null else catName)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(pillColor)
                            )
                            Text(
                                text = if (pct > 0) "$catName $pct%" else catName,
                                color = if (isCatSelected) Color.White else Slate300,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatFullCurrency(remainingSumAmt),
                                color = if (isCatSelected) Color.White else Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Top-level categories
                    val visibleCategories = if (shouldGroup) {
                        categoryTotalsMap.take(2)
                    } else {
                        categoryTotalsMap
                    }

                    visibleCategories.forEach { (catName, sumAmt) ->
                        val isCatSelected = selectedCategoryFilter == catName
                        val pillColor = getCategoryColor(catName)
                        val pct = if (totalCategorySum > 0) {
                            ((sumAmt / totalCategorySum) * 100).let { kotlin.math.round(it).toInt() }
                        } else 0

                        Row(
                            modifier = Modifier
                                .then(
                                    if (isCatSelected) {
                                        Modifier.shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(20.dp),
                                            ambientColor = pillColor,
                                            spotColor = pillColor
                                        )
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isCatSelected) pillColor else DarkBg)
                                .border(
                                    1.dp,
                                    if (isCatSelected) pillColor else Slate800,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    onSelectCategoryFilter(if (isCatSelected) null else catName)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(pillColor)
                            )
                            Text(
                                text = if (pct > 0) "$catName $pct%" else catName,
                                color = if (isCatSelected) Color.White else Slate300,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatFullCurrency(sumAmt),
                                color = if (isCatSelected) Color.White else Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (shouldGroup) {
                        val remainingCategories = categoryTotalsMap.drop(2)
                        val remainingSum = remainingCategories.sumOf { it.second }
                        val remainingCount = remainingCategories.size
                        val remainingPct = if (totalCategorySum > 0) {
                            ((remainingSum / totalCategorySum) * 100).let { kotlin.math.round(it).toInt() }
                        } else 0

                        val mixedLabel = if (remainingCount <= 1) {
                            "✨ Прочие ${if (remainingPct > 0) "$remainingPct%" else ""}"
                        } else {
                            "✨ Смешанные (+$remainingCount) ${if (remainingPct > 0) "$remainingPct%" else ""}"
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DarkBg)
                                .border(
                                    1.dp,
                                    Indigo500.copy(alpha = 0.6f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    onOpenMixedCategoriesDialog()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = mixedLabel,
                                color = Indigo500,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatFullCurrency(remainingSum),
                                color = Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "»",
                                color = Indigo500,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
