package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.RollingCurrencyText
import com.example.ui.components.YearWheelPickerDialog
import com.example.ui.components.charts.MonthlyBarChart
import com.example.ui.components.formatFullCurrency
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald500
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo400
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Sky400
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlin.math.roundToInt

val ShortMonths = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")

@Composable
fun AnnualReportScreen(
    selectedYear: Int,
    allTransactions: List<TransactionEntity>,
    onChangeYear: (Int) -> Unit = {}
) {
    var reportYear by remember { mutableIntStateOf(selectedYear) }
    var showYearWheelPicker by remember { mutableStateOf(false) }
    val yearsAvailable = listOf(2026, 2025, 2024, 2023)

    val yearPrefix = "$reportYear-"
    val yearTransactions = allTransactions.filter { it.date.startsWith(yearPrefix) }

    val monthlyIncomes = DoubleArray(12) { 0.0 }
    val monthlyExpenses = DoubleArray(12) { 0.0 }

    yearTransactions.forEach { tx ->
        val monthStr = tx.date.split("-").getOrNull(1)
        val monthIdx = monthStr?.toIntOrNull()?.minus(1)
        if (monthIdx != null && monthIdx in 0..11) {
            if (tx.type == "income") {
                monthlyIncomes[monthIdx] += tx.amount
            } else if (tx.type == "expense") {
                monthlyExpenses[monthIdx] += tx.amount
            }
        }
    }

    val annualTotalIncome = monthlyIncomes.sum()
    val annualTotalExpense = monthlyExpenses.sum()
    val annualNetBalance = annualTotalIncome - annualTotalExpense
    val annualSavingsRate = if (annualTotalIncome > 0.0) {
        (((annualTotalIncome - annualTotalExpense).coerceAtLeast(0.0) / annualTotalIncome) * 100.0).roundToInt()
    } else 0

    val annualTotalSavings = yearTransactions.filter { tx ->
        tx.category.contains("Сбережен", ignoreCase = true) ||
            tx.category.contains("Накоплен", ignoreCase = true) ||
            tx.subcategory.contains("Взнос", ignoreCase = true) ||
            tx.subcategory.contains("Отложено", ignoreCase = true) ||
            tx.category.contains("Цель", ignoreCase = true) ||
            tx.category.contains("Цели", ignoreCase = true)
    }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HERO ANNUAL REPORT CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(GlassHighlight, GlassBorder))
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 50.dp, y = (-50).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Emerald500.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header + Year Stepper Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ГОДОВОЙ АУДИТ",
                                color = Slate400,
                                fontFamily = ManropeFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            RollingCurrencyText(
                                text = formatFullCurrency(annualNetBalance),
                                color = if (annualNetBalance >= 0) Color.White else Rose400,
                                fontFamily = SpaceGroteskFontFamily,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Чистое сальдо за $reportYear год • Норма сбережений $annualSavingsRate%",
                                color = Slate400,
                                fontFamily = ManropeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Year selector pill with steppers
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Предыдущий год",
                                tint = Slate300,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val nextYear = (reportYear - 1).coerceAtLeast(2020)
                                        reportYear = nextYear
                                        onChangeYear(nextYear)
                                    }
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showYearWheelPicker = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$reportYear",
                                    color = Color.White,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Следующий год",
                                tint = Slate300,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val nextYear = (reportYear + 1).coerceAtMost(2030)
                                        reportYear = nextYear
                                        onChangeYear(nextYear)
                                    }
                            )
                        }
                    }

                    // 3 Bento KPI Cards (Получено / Потрачено / В копилки)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val incFormatted = formatFullCurrency(annualTotalIncome)
                        val expFormatted = formatFullCurrency(annualTotalExpense)
                        val savFormatted = formatFullCurrency(annualTotalSavings)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianElevated)
                                .border(1.dp, Emerald400.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "ПОЛУЧЕНО",
                                    color = Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = incFormatted,
                                    color = Emerald400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = when {
                                        incFormatted.length > 13 -> 10.sp
                                        incFormatted.length > 9 -> 11.sp
                                        else -> 13.sp
                                    },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianElevated)
                                .border(1.dp, Rose500.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "ПОТРАЧЕНО",
                                    color = Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = expFormatted,
                                    color = Rose400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = when {
                                        expFormatted.length > 13 -> 10.sp
                                        expFormatted.length > 9 -> 11.sp
                                        else -> 13.sp
                                    },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianElevated)
                                .border(1.dp, Sky400.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "СБЕРЕЖЕНИЯ",
                                    color = Slate400,
                                    fontFamily = ManropeFontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = savFormatted,
                                    color = Sky400,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontSize = when {
                                        savFormatted.length > 13 -> 10.sp
                                        savFormatted.length > 9 -> 11.sp
                                        else -> 13.sp
                                    },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showYearWheelPicker) {
            YearWheelPickerDialog(
                initialYear = reportYear,
                years = yearsAvailable,
                onDismiss = { showYearWheelPicker = false },
                onConfirm = { newYear ->
                    reportYear = newYear
                    onChangeYear(newYear)
                    showYearWheelPicker = false
                }
            )
        }

        // 2. BAR CHART CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Динамика по месяцам",
                        color = Color.White,
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "$reportYear",
                        color = Slate400,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                MonthlyBarChart(
                    months = ShortMonths,
                    incomeValues = monthlyIncomes.toList(),
                    expenseValues = monthlyExpenses.toList()
                )
            }
        }

        // 3. MONTHLY BREAKDOWN TABLE
        Card(
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Помесячная матрица",
                    color = Color.White,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianElevated)
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "МЕСЯЦ",
                        color = Slate400,
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = "ДОХОД",
                        color = Emerald400,
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "РАСХОД",
                        color = Rose400,
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "САЛЬДО",
                        color = Sky400,
                        fontFamily = ManropeFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1.5f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                MonthsRu.forEachIndexed { idx, monthName ->
                    val inc = monthlyIncomes[idx]
                    val exp = monthlyExpenses[idx]
                    val net = inc - exp
                    val hasActivity = inc > 0.0 || exp > 0.0

                    val incStr = formatFullCurrency(inc)
                    val expStr = formatFullCurrency(exp)
                    val netStr = formatFullCurrency(net)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (idx % 2 == 1) ObsidianElevated.copy(alpha = 0.35f) else Color.Transparent
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = monthName,
                            color = if (hasActivity) Slate100 else Slate400,
                            fontFamily = ManropeFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = incStr,
                            color = if (inc > 0) Emerald400 else Slate400,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = when {
                                incStr.length > 12 -> 9.sp
                                incStr.length > 9 -> 10.sp
                                else -> 11.sp
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = expStr,
                            color = if (exp > 0) Slate100 else Slate400,
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = when {
                                expStr.length > 12 -> 9.sp
                                expStr.length > 9 -> 10.sp
                                else -> 11.sp
                            },
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = netStr,
                            color = when {
                                !hasActivity -> Slate400
                                net >= 0 -> Emerald400
                                else -> Rose400
                            },
                            fontFamily = SpaceGroteskFontFamily,
                            fontSize = when {
                                netStr.length > 12 -> 9.sp
                                netStr.length > 9 -> 10.sp
                                else -> 11.sp
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}
