package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.charts.MonthlyBarChart
import com.example.ui.components.formatFullCurrency
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Sky400
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg

val ShortMonths = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")

@Composable
fun AnnualReportScreen(
    selectedYear: Int,
    allTransactions: List<TransactionEntity>,
    onChangeYear: (Int) -> Unit = {}
) {
    var reportYear by remember { androidx.compose.runtime.mutableIntStateOf(selectedYear) }
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Year Picker Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.8f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Годовой отчет", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Сравнительный анализ за $reportYear год", color = Slate400, fontSize = 11.sp)
                }

                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBg)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .clickable { showYearWheelPicker = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("$reportYear", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        if (showYearWheelPicker) {
            com.example.ui.components.YearWheelPickerDialog(
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

        // Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val incFormatted = formatFullCurrency(annualTotalIncome)
            val expFormatted = formatFullCurrency(annualTotalExpense)
            val savFormatted = formatFullCurrency(annualTotalSavings)

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.7f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ПОЛУЧЕНО", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = incFormatted,
                        color = Emerald400,
                        fontSize = when {
                            incFormatted.length > 13 -> 10.sp
                            incFormatted.length > 9 -> 11.sp
                            else -> 13.sp
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.7f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("ПОТРАЧЕНО", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = expFormatted,
                        color = Rose500,
                        fontSize = when {
                            expFormatted.length > 13 -> 10.sp
                            expFormatted.length > 9 -> 11.sp
                            else -> 13.sp
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.7f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("СБЕРЕЖЕНИЯ", color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = savFormatted,
                        color = Sky400,
                        fontSize = when {
                            savFormatted.length > 13 -> 10.sp
                            savFormatted.length > 9 -> 11.sp
                            else -> 13.sp
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Bar Chart
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.8f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Динамика доходов и расходов", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))
                MonthlyBarChart(
                    months = ShortMonths,
                    incomeValues = monthlyIncomes.toList(),
                    expenseValues = monthlyExpenses.toList()
                )
            }
        }

        // Monthly Breakdown Table
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.8f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Детализация по месяцах", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkBg)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("МЕСЯЦ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                    Text("ДОХОД", color = Emerald400, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                    Text("РАСХОД", color = Rose500, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                    Text("САЛЬДО", color = Sky400, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                }

                Spacer(modifier = Modifier.height(6.dp))

                MonthsRu.forEachIndexed { idx, monthName ->
                    val inc = monthlyIncomes[idx]
                    val exp = monthlyExpenses[idx]
                    val net = inc - exp

                    val incStr = formatFullCurrency(inc)
                    val expStr = formatFullCurrency(exp)
                    val netStr = formatFullCurrency(net)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(monthName, color = Slate100, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        Text(
                            text = incStr,
                            color = Slate100,
                            fontSize = when {
                                incStr.length > 12 -> 8.sp
                                incStr.length > 9 -> 9.sp
                                else -> 11.sp
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = expStr,
                            color = Slate100,
                            fontSize = when {
                                expStr.length > 12 -> 8.sp
                                expStr.length > 9 -> 9.sp
                                else -> 11.sp
                            },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = netStr,
                            color = if (net >= 0) Emerald400 else Rose500,
                            fontSize = when {
                                netStr.length > 12 -> 8.sp
                                netStr.length > 9 -> 9.sp
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
    }
}
