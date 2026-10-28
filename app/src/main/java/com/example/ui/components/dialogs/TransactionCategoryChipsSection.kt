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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
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
import com.example.ui.components.formatFullCurrency
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionCategoryChipsSection(
    selectedPeriod: String?,
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
        // Period selector (Нед, Мес, Год) with toggle semantics and no artificial default highlight
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
            val periodsList = listOf(
                "week" to "Нед",
                "month" to "Мес",
                "year" to "Год"
            )
            val tabCount = periodsList.size
            val tabWidth = barWidth / tabCount

            val selectedIndex = resolveSelectedPeriodTabIndex(selectedPeriod, periodsList)
            val hasActivePeriodSelection = selectedIndex >= 0

            val animatedFraction by animateFloatAsState(
                targetValue = if (hasActivePeriodSelection) selectedIndex.toFloat() else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "period_tab_fraction"
            )

            // Moving selection indicator pill — shown ONLY when a period is explicitly locked
            if (hasActivePeriodSelection) {
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
            }

            // Clickable labels (toggle behavior handled by onSelectPeriod)
            Row(modifier = Modifier.fillMaxSize()) {
                periodsList.forEach { (pKey, pText) ->
                    val isSelected = selectedPeriod == pKey
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
                            fontFamily = ManropeFontFamily,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Chart Mode buttons (Donut vs Bar) with crisp Vector Icons
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkBg)
                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isDonut = chartViewMode == "donut"
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDonut) Indigo500.copy(alpha = 0.25f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isDonut) Indigo500.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectChartViewMode("donut") }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PieChart,
                    contentDescription = "Круговая диаграмма",
                    tint = if (isDonut) Color.White else Slate400,
                    modifier = Modifier.size(15.dp)
                )
            }
            val isBar = chartViewMode == "bar"
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isBar) Indigo500.copy(alpha = 0.25f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isBar) Indigo500.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectChartViewMode("bar") }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Гистограмма",
                    tint = if (isBar) Color.White else Slate400,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }

    // Category Chips with percentages and amounts
    if (categoryTotalsMap.isNotEmpty()) {
        val totalCategorySum = categoryTotalsMap.sumOf { it.second }.coerceAtLeast(1.0)
        val categoriesScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 112.dp)
                .verticalScroll(categoriesScrollState)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDrilledDownToMixed) {
                    // Back button to top-level categories
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkBg)
                            .border(1.dp, Indigo500, RoundedCornerShape(20.dp))
                            .clickable {
                                onSetDrilledDownToMixed(false)
                                onSelectCategoryFilter(null)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "« Назад",
                            color = Indigo500,
                            fontFamily = ManropeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val remainingCategories = categoryTotalsMap.drop(2)
                    val remainingTotalSum = remainingCategories.sumOf { it.second }.coerceAtLeast(1.0)

                    remainingCategories.forEach { (catName, remainingSumAmt) ->
                        val isCatSelected = selectedCategoryFilter == catName
                        val pillColor = getCategoryColor(catName)
                        val pct = if (remainingTotalSum > 0) {
                            ((remainingSumAmt / remainingTotalSum) * 100).let { kotlin.math.round(it).toInt() }
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
                                    .background(if (isCatSelected) Color.White else pillColor)
                            )
                            Text(
                                text = if (pct > 0) "$catName $pct%" else catName,
                                color = if (isCatSelected) Color.White else Slate300,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatFullCurrency(remainingSumAmt),
                                color = if (isCatSelected) Color.White else Slate400,
                                fontFamily = SpaceGroteskFontFamily,
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
                                    .background(if (isCatSelected) Color.White else pillColor)
                            )
                            Text(
                                text = if (pct > 0) "$catName $pct%" else catName,
                                color = if (isCatSelected) Color.White else Slate300,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatFullCurrency(sumAmt),
                                color = if (isCatSelected) Color.White else Slate400,
                                fontFamily = SpaceGroteskFontFamily,
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
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatFullCurrency(remainingSum),
                                color = Slate400,
                                fontFamily = SpaceGroteskFontFamily,
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
