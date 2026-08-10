package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DatePickerField(
    value: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val initialYear = try {
        if (value.length >= 10) value.substring(0, 4).toInt() else calendar.get(Calendar.YEAR)
    } catch (e: Exception) {
        calendar.get(Calendar.YEAR)
    }

    val initialMonth = try {
        if (value.length >= 10) value.substring(5, 7).toInt() - 1 else calendar.get(Calendar.MONTH)
    } catch (e: Exception) {
        calendar.get(Calendar.MONTH)
    }

    val initialDay = try {
        if (value.length >= 10) value.substring(8, 10).toInt() else calendar.get(Calendar.DAY_OF_MONTH)
    } catch (e: Exception) {
        calendar.get(Calendar.DAY_OF_MONTH)
    }

    val datePickerDialog = remember(value) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                onDateSelected(formatted)
            },
            initialYear,
            initialMonth,
            initialDay
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            .clickable { datePickerDialog.show() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                if (!label.isNullOrEmpty()) {
                    Text(label, color = Slate400, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = value.ifBlank { "Выберите дату" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Календарь",
                tint = Emerald400,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PlusMinusMorphToggle(
    type: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = type == "income"
    val morphProgress by animateFloatAsState(
        targetValue = if (isIncome) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "plus_minus_morph"
    )

    val color by animateColorAsState(
        targetValue = if (isIncome) Emerald400 else Rose500,
        animationSpec = tween(220),
        label = "plus_minus_color"
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .width(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                clip = false,
                ambientColor = color,
                spotColor = color
            )
            .background(DarkBg, RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val strokeWidthPx = 3.dp.toPx()
            val widthPx = size.width
            val heightPx = size.height
            val centerPxX = widthPx / 2f
            val centerPxY = heightPx / 2f
            val halfLen = widthPx * 0.4f

            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(centerPxX - halfLen, centerPxY),
                end = Offset(centerPxX + halfLen, centerPxY),
                strokeWidth = strokeWidthPx * 2.8f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(centerPxX - halfLen, centerPxY),
                end = Offset(centerPxX + halfLen, centerPxY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            val vertHalfLen = halfLen * morphProgress
            if (vertHalfLen > 0.2f) {
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = Offset(centerPxX, centerPxY - vertHalfLen),
                    end = Offset(centerPxX, centerPxY + vertHalfLen),
                    strokeWidth = strokeWidthPx * 2.8f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(centerPxX, centerPxY - vertHalfLen),
                    end = Offset(centerPxX, centerPxY + vertHalfLen),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun CalendarDayIcon(
    dayStr: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Slate300
) {
    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = 1.8.dp.toPx()
            val cornerRadiusPx = 6.dp.toPx()
            val topSpacePx = 4.dp.toPx()

            val widthPx = size.width
            val heightPx = size.height

            val rectLeft = strokePx / 2f
            val rectTop = topSpacePx + strokePx / 2f
            val rectWidth = widthPx - strokePx
            val rectHeight = heightPx - topSpacePx - strokePx

            drawRoundRect(
                color = tintColor,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = strokePx)
            )

            val ringWidthPx = 2.5.dp.toPx()
            val ringHeightPx = 5.dp.toPx()
            val ringRadiusPx = 1.2.dp.toPx()

            val ring1X = widthPx * 0.32f - ringWidthPx / 2f
            val ring2X = widthPx * 0.68f - ringWidthPx / 2f

            drawRoundRect(
                color = tintColor,
                topLeft = Offset(ring1X, 0f),
                size = Size(ringWidthPx, ringHeightPx),
                cornerRadius = CornerRadius(ringRadiusPx, ringRadiusPx),
                style = Stroke(width = strokePx)
            )

            drawRoundRect(
                color = tintColor,
                topLeft = Offset(ring2X, 0f),
                size = Size(ringWidthPx, ringHeightPx),
                cornerRadius = CornerRadius(ringRadiusPx, ringRadiusPx),
                style = Stroke(width = strokePx)
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayStr,
                color = tintColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CompactDatePickerField(
    value: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSingleDatePicker by remember { mutableStateOf(false) }

    val yearStr = try { if (value.length >= 4) value.substring(0, 4) else "" } catch (e: Exception) { "" }
    val monthNum = try { if (value.length >= 7) value.substring(5, 7).toInt() else 1 } catch (e: Exception) { 1 }
    val dayStr = try { if (value.length >= 10) value.substring(8, 10).toInt().toString() else "1" } catch (e: Exception) { "1" }

    val monthShortRu = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
    )[(monthNum - 1).coerceIn(0, 11)]

    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            .clickable { showSingleDatePicker = true }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$monthShortRu $yearStr",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        CalendarDayIcon(
            dayStr = dayStr,
            tintColor = Slate300
        )
    }

    if (showSingleDatePicker) {
        SingleDatePickerDialog(
            initialDate = value,
            onDismiss = { showSingleDatePicker = false },
            onConfirm = { selected ->
                onDateSelected(selected)
            }
        )
    }
}

private val MonthNamesGenitive = listOf(
    "Января", "Февраля", "Марта", "Апреля", "Мая", "Июня",
    "Июля", "Августа", "Сентября", "Октября", "Ноября", "Декабря"
)
private val MonthNamesNominative = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

private fun formatRussianDateShort(dateStr: String, prefix: String): String {
    val parts = dateStr.split("-")
    if (parts.size < 3) return "$prefix $dateStr"
    val day = parts[2].toIntOrNull() ?: 1
    val monthIdx = (parts[1].toIntOrNull() ?: 1) - 1
    val monthGenitive = MonthNamesGenitive.getOrElse(monthIdx) { "" }
    return "$prefix $day $monthGenitive"
}

data class MonthInfo(
    val year: Int,
    val monthIdx: Int,
    val firstDayOfWeek: Int,
    val maxDays: Int
)

@Composable
fun DateRangePickerDialog(
    initialStart: String,
    initialEnd: String,
    onDismiss: () -> Unit,
    onConfirm: (start: String, end: String) -> Unit
) {
    var start by remember { mutableStateOf(initialStart.ifBlank { "2026-07-01" }) }
    var end by remember { mutableStateOf(initialEnd.ifBlank { "2026-07-23" }) }
    var selectingStart by remember { mutableStateOf(true) }

    val monthsList = remember {
        val list = mutableListOf<MonthInfo>()
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        for (y in currentYear..currentYear + 1) {
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
        val parts = start.split("-")
        if (parts.size >= 2) {
            val m = (parts[1].toIntOrNull() ?: 7) - 1
            val y = parts[0].toIntOrNull() ?: 2026
            val cal = Calendar.getInstance()
            val startYear = cal.get(Calendar.YEAR)
            ((y - startYear) * 12 + m).coerceIn(0, monthsList.size - 1)
        } else {
            0
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
                        text = "Выберите период",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectingStart = true }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = formatRussianDateShort(start, "с"),
                            color = if (selectingStart) Color.White else Slate400,
                            fontSize = 16.sp,
                            fontWeight = if (selectingStart) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(if (selectingStart) Color(0xFF3B82F6) else Slate800)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectingStart = false }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = formatRussianDateShort(end, "до"),
                            color = if (!selectingStart) Color.White else Slate400,
                            fontSize = 16.sp,
                            fontWeight = if (!selectingStart) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(if (!selectingStart) Color(0xFF3B82F6) else Slate800)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                                            val isStart = dateStr == start
                                            val isEnd = dateStr == end
                                            val isInRange = dateStr > start && dateStr < end

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .then(
                                                        if (isStart || isEnd) {
                                                            Modifier
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF3B82F6))
                                                        } else if (isInRange) {
                                                            Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Slate800)
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                                    .clickable {
                                                        if (selectingStart) {
                                                            start = dateStr
                                                            if (end < start) end = start
                                                            selectingStart = false
                                                        } else {
                                                            if (dateStr < start) {
                                                                start = dateStr
                                                                selectingStart = false
                                                            } else {
                                                                end = dateStr
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$dayNum",
                                                    color = if (isStart || isEnd || isInRange) Color.White else Slate300,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isStart || isEnd) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f).height(40.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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
                                onConfirm(start, end)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Применить выбранный период", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

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
