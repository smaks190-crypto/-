package com.example.ui.components.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.components.formatFullCurrency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionBarChart(
    selectedPeriod: String,
    stableAnchorDateStr: String,
    offsetValue: Int,
    filterType: String,
    accentColor: Color,
    getFilteredListForOffset: (Int) -> List<TransactionEntity>,
    modifier: Modifier = Modifier
) {
    val effectivePeriod = if (selectedPeriod == "all") "month" else selectedPeriod
    val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val anchorDate = try { sdf.parse(anchorDateStr) } catch (e: Exception) { null } ?: Date()
    val cal = Calendar.getInstance().apply { time = anchorDate }

    val (barValues, barLabels, currentHighlightIdx) = when (effectivePeriod) {
        "year" -> {
            cal.add(Calendar.YEAR, offsetValue)
            val currentYear = cal.get(Calendar.YEAR)

            val monthNamesList = listOf("Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек")
            val monthValues = DoubleArray(12) { 0.0 }

            val offsetTransactions = getFilteredListForOffset(offsetValue)
            val typeFiltered = offsetTransactions.filter { it.type.equals(filterType, ignoreCase = true) }

            typeFiltered.forEach { tx ->
                val txDate = try { sdf.parse(tx.date) } catch (e: Exception) { null }
                if (txDate != null) {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    if (txCal.get(Calendar.YEAR) == currentYear) {
                        val monthIdx = txCal.get(Calendar.MONTH)
                        if (monthIdx in 0..11) {
                            monthValues[monthIdx] += kotlin.math.abs(tx.amount)
                        }
                    }
                }
            }

            val highlightIdx = if (currentYear == Calendar.getInstance().get(Calendar.YEAR)) {
                Calendar.getInstance().get(Calendar.MONTH)
            } else {
                -1
            }
            Triple(monthValues.toList(), monthNamesList, highlightIdx)
        }
        "week" -> {
            cal.add(Calendar.WEEK_OF_YEAR, offsetValue)
            val firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)

            val weekDayLabels = mutableListOf<String>()
            val weekDayValues = DoubleArray(7) { 0.0 }
            val dayFormats = SimpleDateFormat("dd", Locale.getDefault())

            val weekDates = (0..6).map { i ->
                val dCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_WEEK, i) }
                val dayNum = dayFormats.format(dCal.time).toIntOrNull()?.toString() ?: dayFormats.format(dCal.time)
                weekDayLabels.add(dayNum)
                dCal.time
            }

            val offsetTransactions = getFilteredListForOffset(offsetValue)
            val typeFiltered = offsetTransactions.filter { it.type.equals(filterType, ignoreCase = true) }

            typeFiltered.forEach { tx ->
                val txDate = try { sdf.parse(tx.date) } catch (e: Exception) { null }
                if (txDate != null) {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    weekDates.forEachIndexed { idx, wDate ->
                        val wCal = Calendar.getInstance().apply { time = wDate }
                        if (txCal.get(Calendar.YEAR) == wCal.get(Calendar.YEAR) &&
                            txCal.get(Calendar.DAY_OF_YEAR) == wCal.get(Calendar.DAY_OF_YEAR)
                        ) {
                            weekDayValues[idx] += kotlin.math.abs(tx.amount)
                        }
                    }
                }
            }

            val nowCal = Calendar.getInstance()
            val highlightIdx = weekDates.indexOfFirst { wDate ->
                val wCal = Calendar.getInstance().apply { time = wDate }
                nowCal.get(Calendar.YEAR) == wCal.get(Calendar.YEAR) &&
                        nowCal.get(Calendar.DAY_OF_YEAR) == wCal.get(Calendar.DAY_OF_YEAR)
            }
            Triple(weekDayValues.toList(), weekDayLabels, highlightIdx)
        }
        else -> { // "month" or "all"
            cal.add(Calendar.MONTH, offsetValue)
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH)

            val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val weekRanges = mutableListOf<Pair<Int, Int>>()
            var startDay = 1
            while (startDay <= maxDays) {
                val tempCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth)
                    set(Calendar.DAY_OF_MONTH, startDay)
                }
                val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
                val daysToSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else (8 - dayOfWeek)
                val endDay = kotlin.math.min(startDay + daysToSunday, maxDays)
                weekRanges.add(startDay to endDay)
                startDay = endDay + 1
            }

            val weekLabels = weekRanges.map { (s, e) ->
                if (s == e) "$s" else "$s-$e"
            }
            val weekValues = DoubleArray(weekRanges.size) { 0.0 }

            val offsetTransactions = getFilteredListForOffset(offsetValue)
            val typeFiltered = offsetTransactions.filter { it.type.equals(filterType, ignoreCase = true) }

            typeFiltered.forEach { tx ->
                val txDate = try { sdf.parse(tx.date) } catch (e: Exception) { null }
                if (txDate != null) {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    if (txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth) {
                        val day = txCal.get(Calendar.DAY_OF_MONTH)
                        val weekIdx = weekRanges.indexOfFirst { day in it.first..it.second }
                        if (weekIdx != -1) {
                            weekValues[weekIdx] += kotlin.math.abs(tx.amount)
                        }
                    }
                }
            }

            val nowCal = Calendar.getInstance()
            val highlightIdx = if (nowCal.get(Calendar.YEAR) == currentYear && nowCal.get(Calendar.MONTH) == currentMonth) {
                val day = nowCal.get(Calendar.DAY_OF_MONTH)
                weekRanges.indexOfFirst { day in it.first..it.second }
            } else {
                -1
            }
            Triple(weekValues.toList(), weekLabels, highlightIdx)
        }
    }

    val maxVal = barValues.maxOrNull() ?: 0.0
    val averageVal = if (barValues.any { it > 0.0 }) barValues.filter { it > 0.0 }.average() else 0.0
    val averageRatio = if (maxVal > 0.0) (averageVal / maxVal).toFloat() else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        // 1. Dotted Average Line
        if (averageVal > 0.0) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .align(Alignment.TopCenter)
            ) {
                val y = size.height - (size.height * averageRatio)
                drawLine(
                    color = Slate500.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            }

            // Average text label placed right above the line
            Text(
                text = "Ср: ${formatFullCurrency(averageVal)}",
                color = Slate400,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 4.dp, y = (90 * (1 - averageRatio) - 12).coerceAtLeast(0f).dp)
            )
        }

        // 2. Bar Rows
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            barLabels.forEachIndexed { i, labelText ->
                val amountVal = barValues.getOrElse(i) { 0.0 }
                val barRatio = if (maxVal > 0.0) (amountVal / maxVal).toFloat() else 0f

                // Coerce to have a tiny minimal height if there are transactions but very small
                val displayRatio = if (amountVal > 0.0) barRatio.coerceAtLeast(0.06f) else 0f
                val isHighlighted = (i == currentHighlightIdx) || (currentHighlightIdx == -1 && barRatio == 1f && amountVal > 0.0)

                val barWidth = when (barLabels.size) {
                    in 1..5 -> 18.dp
                    in 6..8 -> 14.dp
                    else -> 10.dp
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height((90 * displayRatio).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (isHighlighted) accentColor
                                else Indigo500.copy(alpha = 0.5f)
                            )
                    )
                    Text(
                        text = labelText,
                        color = if (isHighlighted) Color.White else Slate400,
                        fontSize = 8.sp,
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
