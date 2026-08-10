package com.example.ui.components

import com.example.ui.components.dialogs.*

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.key
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import com.example.ui.screens.TransactionRowItem
import com.example.ui.screens.getCategoryColorAndIcon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.db.CategoryEntity
import com.example.data.db.TransactionEntity
import com.example.ui.theme.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val LocalDialogSwipeEnabled = androidx.compose.runtime.compositionLocalOf {
    androidx.compose.runtime.mutableStateOf(true)
}

fun formatDayHeaderLabel(dateStr: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = sdf.parse(dateStr) ?: return dateStr

        val todayCal = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply { time = date }

        val isSameYear = todayCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
        val todayDayOfYear = todayCal.get(Calendar.DAY_OF_YEAR)
        val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)

        if (isSameYear && todayDayOfYear == targetDayOfYear) {
            "Сегодня"
        } else if (isSameYear && todayDayOfYear - targetDayOfYear == 1) {
            "Вчера"
        } else {
            val pattern = if (isSameYear) "d MMMM" else "d MMMM yyyy"
            val rawStr = SimpleDateFormat(pattern, Locale("ru", "RU")).format(date)
            rawStr.split(" ").mapIndexed { idx, word ->
                if (idx == 1) word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
                else word
            }.joinToString(" ")
        }
    } catch (e: Exception) {
        dateStr
    }
}

private const val REQUEST_CODE_POST_NOTIFICATIONS = 101

@Composable
fun SwipeToDismissDialog(
    onDismissRequest: () -> Unit,
    isAtTop: () -> Boolean = { true },
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false
    ),
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    content: @Composable () -> Unit
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragStartedAtTop by remember { mutableStateOf(false) }
    val swipeEnabledState = remember { mutableStateOf(true) }

    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dialog_swipe_offset"
    )

    val nestedScrollConnection = remember(isAtTop, swipeEnabledState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!swipeEnabledState.value) return Offset.Zero

                if (source == NestedScrollSource.Drag && offsetY == 0f) {
                    dragStartedAtTop = isAtTop()
                }

                if (offsetY > 0f && available.y < 0f) {
                    val newOffset = (offsetY + available.y).coerceAtLeast(0f)
                    val consumedY = newOffset - offsetY
                    offsetY = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!swipeEnabledState.value) return Offset.Zero

                if (source == NestedScrollSource.Drag && dragStartedAtTop && isAtTop() && available.y > 0f) {
                    offsetY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!swipeEnabledState.value) return Velocity.Zero
                dragStartedAtTop = false
                if (offsetY > 120f) {
                    offsetY = 0f
                    onDismissRequest()
                } else {
                    offsetY = 0f
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!swipeEnabledState.value) return Velocity.Zero
                dragStartedAtTop = false
                if (offsetY > 120f) {
                    offsetY = 0f
                    onDismissRequest()
                } else {
                    offsetY = 0f
                }
                return Velocity.Zero
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        androidx.activity.compose.BackHandler(onBack = onDismissRequest)

        androidx.compose.runtime.CompositionLocalProvider(LocalDialogSwipeEnabled provides swipeEnabledState) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismissRequest() }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .imePadding()
                    .padding(contentPadding)
                    .nestedScroll(nestedScrollConnection),
                contentAlignment = Alignment.BottomCenter
            ) {
                val maxAllowedHeight = maxHeight - 8.dp
                androidx.compose.animation.AnimatedVisibility(
                    visible = isVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    enter = androidx.compose.animation.slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                    ) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
                    ) + androidx.compose.animation.fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxAllowedHeight)
                            .animateContentSize(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioNoBouncy
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { 
                                focusManager.clearFocus()
                                com.example.ui.components.SwipeToRevealController.requestCollapseAll()
                            }
                            .offset { IntOffset(0, animatedOffsetY.roundToInt().coerceAtLeast(0)) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

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

private data class MonthInfo(
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
                        text = "Выберите период",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Range Tabs ("с 1 июля" / "до 23 июля")
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

                // Bottom apply button
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




@Composable
fun ApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var apiKeyText by remember { mutableStateOf(currentKey) }

    val scrollState = rememberScrollState()
    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        swipeEnabledState.value = !scrollState.isScrollInProgress
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Indigo500.copy(alpha = 0.15f))
                            .border(1.dp, Indigo500.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "ИИ-Помощник",
                            tint = Indigo500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Gemini API Ключ",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Emerald400.copy(alpha = 0.2f))
                                    .border(1.dp, Emerald400.copy(alpha = 0.3f), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Free", color = Emerald400, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        Text(
                            text = "Интеллектуальный помощник",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBg)
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Indigo500, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Как бесплатно получить API ключ:", color = Indigo500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "1. Перейдите на aistudio.google.com/app/apikey\n" +
                                    "2. Войдите под своим Google-аккаунтом\n" +
                                    "3. Нажмите «Create API key»\n" +
                                    "4. Скопируйте ключ и вставьте ниже",
                            color = Slate400,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val context = LocalContext.current
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Получить API ключ в Google AI Studio ↗", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("ВАШ КЛЮЧ API", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    placeholder = { Text("AIzaSy...", color = Slate400) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = Indigo500,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onSave(apiKeyText)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("save_api_key_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Сохранить ключ", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)


@Composable
fun AddGoalDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, target: Double, current: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf(TextFieldValue("")) }
    var currentText by remember { mutableStateOf(TextFieldValue("")) }

    val scrollState = rememberScrollState()
    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        swipeEnabledState.value = !scrollState.isScrollInProgress
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Новая финансовая цель", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("НАЗВАНИЕ ЦЕЛИ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.capitalizeFirstLetter() },
                    placeholder = { Text("Например: Новый ноутбук", color = Slate400) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("ЦЕЛЕВАЯ СУММА (₽)", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = formatAmountTextFieldValue(targetText, it) },
                    placeholder = { Text("130 000", color = Slate400) },
                    suffix = { Text("₽", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("УЖЕ НАКОПЛЕНО (₽)", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = formatAmountTextFieldValue(currentText, it) },
                    placeholder = { Text("0", color = Slate400) },
                    suffix = { Text("₽", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = Emerald400,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            val target = parseAmountInput(targetText.text)
                            val current = parseAmountInput(currentText.text)
                            if (name.isNotBlank() && target > 0) {
                                onSave(name.trim(), target, current)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Создать", color = DarkBg, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun SetupDialog(
    onDismiss: (() -> Unit)?,
    onSelectMode: (mode: String) -> Unit
) {
    SwipeToDismissDialog(onDismissRequest = { onDismiss?.invoke() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
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

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Emerald400.copy(alpha = 0.1f))
                        .border(1.dp, Emerald400.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💰", fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Составить бюджет", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("Выберите режим инициализации:", color = Slate400, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(20.dp))

                // Blank Mode Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMode("blank") },
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("✨ С чистого листа", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Пустой кошелек и цели", color = Slate400, fontSize = 11.sp)
                        }
                        Text("→", color = Emerald400, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Trash Demo Mode Button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMode("demo") },
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("📊 Казума Сато (Демо)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Загрузить угарный пример расходов", color = Slate400, fontSize = 11.sp)
                        }
                        Text("→", color = Emerald400, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Rose500.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(message, color = Slate400, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена", color = Slate400, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("Да, выполнить", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}



@Composable
fun ReminderSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(com.example.notifications.ReminderManager.isReminderEnabled(context)) }
    val (savedHour, savedMinute) = remember { com.example.notifications.ReminderManager.getReminderTime(context) }
    var selectedHour by remember { mutableStateOf(savedHour) }
    var selectedMinute by remember { mutableStateOf(savedMinute) }

    var hasPermission by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    fun checkAndRequestPermission(onGranted: () -> Unit = {}) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                var curr = context
                var activity: android.app.Activity? = null
                while (curr is android.content.ContextWrapper) {
                    if (curr is android.app.Activity) {
                        activity = curr
                        break
                    }
                    curr = curr.baseContext
                }
                if (activity != null) {
                    try {
                        androidx.core.app.ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            REQUEST_CODE_POST_NOTIFICATIONS
                        )
                    } catch (_: Throwable) {}
                }
            } else {
                hasPermission = true
                onGranted()
            }
        } else {
            hasPermission = true
            onGranted()
        }
    }

    LaunchedEffect(Unit) {
        if (isEnabled && !hasPermission) {
            checkAndRequestPermission()
        }
    }

    val scrollState = rememberScrollState()
    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        swipeEnabledState.value = !scrollState.isScrollInProgress
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
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

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💡 Напоминания о бюджете", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Ежедневное напоминание о необходимости внести расходы и проверить бюджет.",
                    color = Slate400,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBg)
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                        .clickable {
                            val nextState = !isEnabled
                            isEnabled = nextState
                            if (nextState && !hasPermission) {
                                checkAndRequestPermission()
                            }
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Включить уведомления", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (isEnabled) {
                                if (hasPermission) "Напоминание активно" else "Требуется разрешение на уведомления"
                            } else "Напоминания отключены",
                            color = if (isEnabled) (if (hasPermission) Emerald400 else Rose500) else Slate400,
                            fontSize = 11.sp
                        )
                    }

                    androidx.compose.material3.Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            if (checked && !hasPermission) {
                                checkAndRequestPermission()
                            }
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = DarkBg,
                            checkedTrackColor = Emerald400,
                            uncheckedThumbColor = Slate400,
                            uncheckedTrackColor = Slate800
                        )
                    )
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Rose500.copy(alpha = 0.15f))
                            .border(1.dp, Rose500.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { checkAndRequestPermission() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Rose500, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Разрешение не предоставлено", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Нажмите, чтобы разрешить приложения отправку уведомлений", color = Rose500, fontSize = 11.sp)
                        }
                    }
                }

                if (isEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Время напоминания (24ч):", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelPicker(
                            items = (0..23).map { it.toString().padStart(2, '0') },
                            initialIndex = selectedHour.coerceIn(0, 23),
                            onItemSelected = { hourIdx -> selectedHour = hourIdx },
                            modifier = Modifier.width(96.dp),
                            visibleItemsCount = 3,
                            itemHeight = 46.dp
                        )

                        Text(
                            text = " : ",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        WheelPicker(
                            items = (0..59).map { it.toString().padStart(2, '0') },
                            initialIndex = selectedMinute.coerceIn(0, 59),
                            onItemSelected = { minIdx -> selectedMinute = minIdx },
                            modifier = Modifier.width(96.dp),
                            visibleItemsCount = 3,
                            itemHeight = 46.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (!hasPermission) {
                                checkAndRequestPermission {
                                    com.example.notifications.ReminderManager.showNotification(context)
                                }
                            } else {
                                com.example.notifications.ReminderManager.showNotification(context)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Тест уведомления", color = Indigo500, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (isEnabled && !hasPermission) {
                                checkAndRequestPermission {
                                    com.example.notifications.ReminderManager.setReminderEnabled(context, isEnabled, selectedHour, selectedMinute)
                                    Toast.makeText(context, "Напоминание сохранено на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } else {
                                com.example.notifications.ReminderManager.setReminderEnabled(context, isEnabled, selectedHour, selectedMinute)
                                Toast.makeText(context, if (isEnabled) "Напоминание сохранено на ${selectedHour.toString().padStart(2, '0')}:${selectedMinute.toString().padStart(2, '0')}" else "Напоминания отключены", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сохранить", color = DarkBg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeExpenseSummaryDialog(
    transactions: List<TransactionEntity>,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Calculate totals and category breakdown
    val incomeTransactions = transactions.filter { it.type.lowercase() == "income" }
    val expenseTransactions = transactions.filter { it.type.lowercase() == "expense" }

    val totalIncome = incomeTransactions.sumOf { kotlin.math.abs(it.amount) }
    val totalExpense = expenseTransactions.sumOf { kotlin.math.abs(it.amount) }
    val netBalance = totalIncome - totalExpense

    // Categories Breakdown
    val expenseCategoryTotals = expenseTransactions
        .groupBy { it.category.ifBlank { "Прочее" } }
        .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
        .filterValues { it > 0 }

    val incomeCategoryTotals = incomeTransactions
        .groupBy { it.category.ifBlank { "Доходы" } }
        .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
        .filterValues { it > 0 }

    val sliceColors = listOf(
        Rose500, Emerald400, Indigo500, Amber400, Sky400,
        Rose500, Emerald400, Indigo500, Slate400, Color(0xFFC084FC)
    )

    data class DoughnutSegment(
        val name: String,
        val amount: Double,
        val isIncome: Boolean,
        val color: Color,
        val percentage: Float
    )

    val grandTotal = totalIncome + totalExpense
    val segments = mutableListOf<DoughnutSegment>()

    var colorIdx = 0
    expenseCategoryTotals.toList().sortedByDescending { it.second }.forEach { (cat, amt) ->
        val pct = if (grandTotal > 0) (amt / grandTotal).toFloat() else 0f
        val color = if (colorIdx == 0) Rose500 else sliceColors[colorIdx % sliceColors.size]
        segments.add(DoughnutSegment(cat, amt, isIncome = false, color = color, percentage = pct))
        colorIdx++
    }

    incomeCategoryTotals.toList().sortedByDescending { it.second }.forEach { (cat, amt) ->
        val pct = if (grandTotal > 0) (amt / grandTotal).toFloat() else 0f
        val color = if (colorIdx == 1) Emerald400 else sliceColors[(colorIdx + 2) % sliceColors.size]
        segments.add(DoughnutSegment(cat, amt, isIncome = true, color = color, percentage = pct))
        colorIdx++
    }

    SwipeToDismissDialog(
        onDismissRequest = onDismiss,
        isAtTop = { scrollState.value == 0 }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 12.dp),
                color = DarkBg,
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Indigo500.copy(alpha = 0.15f))
                                    .border(1.dp, Indigo500.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Indigo500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Сводка доходов и расходов",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Доли категорий и общий расчёт",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Slate900)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Summary Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Slate900.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Доходы", color = Slate400, fontSize = 11.sp)
                                    Text(
                                        "+ ${formatFullCurrency(totalIncome)}",
                                        color = Emerald400,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(1.dp)
                                        .background(Slate800)
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Баланс", color = Slate400, fontSize = 11.sp)
                                    Text(
                                        "${if (netBalance >= 0) "+" else ""}${formatFullCurrency(netBalance)}",
                                        color = if (netBalance >= 0) Emerald400 else Rose500,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .width(1.dp)
                                        .background(Slate800)
                                )

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Расходы", color = Slate400, fontSize = 11.sp)
                                    Text(
                                        "- ${formatFullCurrency(totalExpense)}",
                                        color = Rose500,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Doughnut Chart Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Slate900.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "КРУГОВАЯ ДИАГРАММА ДОЛЕЙ",
                                    color = Slate300,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.align(Alignment.Start)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Box(
                                    modifier = Modifier.size(190.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        if (segments.isEmpty() || grandTotal <= 0) {
                                            drawArc(
                                                color = Slate800,
                                                startAngle = 0f,
                                                sweepAngle = 360f,
                                                useCenter = false,
                                                style = Stroke(width = 28.dp.toPx())
                                            )
                                        } else {
                                            var currentAngle = -90f
                                            val strokeWidthPx = 28.dp.toPx()
                                            val gapAngle = if (segments.size > 1) 2.5f else 0f

                                            segments.forEach { seg ->
                                                val sweep = (seg.percentage * 360f) - gapAngle
                                                if (sweep > 0f) {
                                                    drawArc(
                                                        color = seg.color,
                                                        startAngle = currentAngle,
                                                        sweepAngle = sweep,
                                                        useCenter = false,
                                                        style = Stroke(
                                                            width = strokeWidthPx,
                                                            cap = StrokeCap.Round
                                                        )
                                                    )
                                                    currentAngle += sweep + gapAngle
                                                }
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Всего средств",
                                            color = Slate400,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = formatFullCurrency(grandTotal),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Chart Legend
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    segments.forEach { seg ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .background(DarkBg, RoundedCornerShape(8.dp))
                                                .border(1.dp, seg.color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(seg.color)
                                            )
                                            Text(
                                                text = "${seg.name} (${(seg.percentage * 100).toInt()}%)",
                                                color = Slate200,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Detailed Category Lists
                        if (expenseCategoryTotals.isNotEmpty()) {
                            Text(
                                text = "РАСХОДЫ ПО КАТЕГОРИЯМ",
                                color = Rose500,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            expenseCategoryTotals.toList().sortedByDescending { it.second }.forEach { (cat, amt) ->
                                val pct = if (totalExpense > 0) (amt / totalExpense * 100).toInt() else 0
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Slate900.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Rose500)
                                            )
                                            Text(
                                                text = cat,
                                                color = Slate200,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "$pct%",
                                                color = Slate500,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "- ${formatFullCurrency(amt)}",
                                                color = Rose500,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (incomeCategoryTotals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ДОХОДЫ ПО КАТЕГОРИЯМ",
                                color = Emerald400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            incomeCategoryTotals.toList().sortedByDescending { it.second }.forEach { (cat, amt) ->
                                val pct = if (totalIncome > 0) (amt / totalIncome * 100).toInt() else 0
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Slate900.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Emerald400)
                                            )
                                            Text(
                                                text = cat,
                                                color = Slate200,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "$pct%",
                                                color = Slate500,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "+ ${formatFullCurrency(amt)}",
                                                color = Emerald400,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
