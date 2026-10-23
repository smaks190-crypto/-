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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
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
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import java.util.Calendar
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

            // Glow line behind horizontal line
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(centerPxX - halfLen, centerPxY),
                end = Offset(centerPxX + halfLen, centerPxY),
                strokeWidth = strokeWidthPx * 2.8f,
                cap = StrokeCap.Round
            )
            // Sharp main horizontal line
            drawLine(
                color = color,
                start = Offset(centerPxX - halfLen, centerPxY),
                end = Offset(centerPxX + halfLen, centerPxY),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            // Vertical line (animates length to morph '-' into '+')
            val vertHalfLen = halfLen * morphProgress
            if (vertHalfLen > 0.2f) {
                // Glow line behind vertical line
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = Offset(centerPxX, centerPxY - vertHalfLen),
                    end = Offset(centerPxX, centerPxY + vertHalfLen),
                    strokeWidth = strokeWidthPx * 2.8f,
                    cap = StrokeCap.Round
                )
                // Sharp main vertical line
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

            // Rounded calendar body outline
            drawRoundRect(
                color = tintColor,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, rectHeight),
                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                style = Stroke(width = strokePx)
            )

            // Top binder rings
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

        // Day Number
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
        // Month and Year (Left)
        Text(
            text = "$monthShortRu $yearStr",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Calendar Icon with Day Number (Right)
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
