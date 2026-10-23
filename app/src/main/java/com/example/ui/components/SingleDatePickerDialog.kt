package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SingleDatePickerDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (selectedDate: String) -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDate.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }) }

    val monthsList = remember {
        val list = mutableListOf<MonthInfo>()
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        for (y in (currentYear - 1)..(currentYear + 1)) {
            for (m in 0..11) {
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val firstDow = (dow + 5) % 7
                val maxD = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                list.add(MonthInfo(y, m, firstDow, maxD))
            }
        }
        list
    }

    val initialMonthIndex = remember {
        val parts = selectedDate.split("-")
        if (parts.size >= 2) {
            val y = parts[0].toIntOrNull() ?: 2026
            val m = (parts[1].toIntOrNull() ?: 7) - 1
            val cal = Calendar.getInstance()
            val startYear = cal.get(Calendar.YEAR) - 1
            ((y - startYear) * 12 + m).coerceIn(0, monthsList.size - 1)
        } else {
            12
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialMonthIndex)

    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(listState.isScrollInProgress) {
        swipeEnabledState.value = !listState.isScrollInProgress
    }

    SwipeToDismissDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBg
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top drag handle bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Slate700)
                    )
                }

                // Header Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Выберите дату",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Selected Date Header Display (e.g. "25 июля 2026 г.")
                val selectedDateFormatted = remember(selectedDate) {
                    val parts = selectedDate.split("-")
                    if (parts.size >= 3) {
                        val d = parts[2].toIntOrNull() ?: 1
                        val mIdx = (parts[1].toIntOrNull() ?: 1) - 1
                        val y = parts[0]
                        "$d ${MonthNamesGenitive.getOrElse(mIdx) { "" }} $y г."
                    } else selectedDate
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = selectedDateFormatted,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(Color(0xFF3B82F6))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Day of Week Headers (ПН ВТ СР ЧТ ПТ СБ ВС)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { dow ->
                        Text(
                            text = dow,
                            color = Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Months Calendar
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(monthsList) { monthInfo ->
                        Text(
                            text = "${MonthNamesNominative[monthInfo.monthIdx]}, ${monthInfo.year}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        )

                        val totalSlots = monthInfo.firstDayOfWeek + monthInfo.maxDays
                        val numRows = (totalSlots + 6) / 7

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (r in 0 until numRows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    for (c in 0..6) {
                                        val dayNum = r * 7 + c - monthInfo.firstDayOfWeek + 1
                                        if (dayNum in 1..monthInfo.maxDays) {
                                            val dateStr = String.format(
                                                Locale.US,
                                                "%04d-%02d-%02d",
                                                monthInfo.year,
                                                monthInfo.monthIdx + 1,
                                                dayNum
                                            )
                                            val isSelected = dateStr == selectedDate

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .then(
                                                        if (isSelected) {
                                                            Modifier
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF3B82F6))
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .clickable {
                                                        selectedDate = dateStr
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dayNum.toString(),
                                                    color = if (isSelected) Color.White else Slate300,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Confirm Button
                Surface(
                    color = Slate900,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 36.dp)
                    ) {
                        Button(
                            onClick = {
                                onConfirm(selectedDate)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Выбрать дату", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
