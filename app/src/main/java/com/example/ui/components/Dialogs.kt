package com.example.ui.components

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
fun AddTransactionDialog(
    initialType: String = "expense",
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (type: String, date: String, category: String, subcategory: String, amount: Double) -> Unit,
    onSuggestCategory: (suspend (transactionName: String, type: String, categoriesList: List<String>) -> String)? = null,
    editingTransaction: TransactionEntity? = null
) {
    var type by remember { mutableStateOf(editingTransaction?.type ?: initialType) }
    var date by remember { mutableStateOf(editingTransaction?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var selectedCategory by remember {
        mutableStateOf(editingTransaction?.category ?: (categories.filter { it.type == type }.firstOrNull()?.name ?: ""))
    }
    var subcategory by remember { mutableStateOf(editingTransaction?.subcategory ?: "") }
    var amountText by remember {
        val initialAmount = editingTransaction?.amount?.let { if (it == 0.0) "" else if (it % 1 == 0.0) String.format(Locale.US, "%.0f", it) else it.toString() } ?: ""
        mutableStateOf(TextFieldValue(initialAmount))
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var aiSuggestedCategory by remember { mutableStateOf<String?>(null) }
    var isAiSuggesting by remember { mutableStateOf(false) }
    var userManuallySelectedCategory by remember { mutableStateOf(false) }

    var neonFlickerValue by remember { mutableStateOf(1f) }
    var isFlickerFinished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        neonFlickerValue = 1f
        kotlinx.coroutines.delay(1300)
        
        val sequence = listOf(
            0.1f to 70L,
            0.9f to 90L,
            0.0f to 120L,
            0.8f to 60L,
            0.05f to 100L,
            0.7f to 50L,
            0.0f to 180L,
            0.95f to 60L,
            0.1f to 80L,
            0.4f to 50L,
            0.0f to 200L
        )
        
        for (step in sequence) {
            neonFlickerValue = step.first
            kotlinx.coroutines.delay(step.second)
        }
        
        isFlickerFinished = true
        
        while (true) {
            kotlinx.coroutines.delay((3000..6500).random().toLong())
            val sparkSequence = listOf(
                0.15f to 40L,
                0.0f to 60L,
                0.25f to 50L,
                0.0f to 40L
            )
            for (spark in sparkSequence) {
                neonFlickerValue = spark.first
                kotlinx.coroutines.delay(spark.second)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "border_gradient_edit")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "offset"
    )

    val borderAlpha = 1f
    val dynamicGradient = Brush.linearGradient(
        colors = listOf(
            Indigo500.copy(alpha = borderAlpha),
            Emerald400.copy(alpha = borderAlpha),
            Rose500.copy(alpha = borderAlpha),
            Indigo500.copy(alpha = borderAlpha)
        ),
        start = Offset(offset, offset), end = Offset(offset + 600f, offset + 600f),
        tileMode = TileMode.Repeated
    )

    val progress = offset / 600f
    val getGradientColor = { p: Float ->
        val norm = p % 1f
        val phase = if (norm < 0f) norm + 1f else norm
        when {
            phase < 0.3333f -> {
                val t = phase / 0.3333f
                androidx.compose.ui.graphics.lerp(Indigo500, Emerald400, t)
            }
            phase < 0.6666f -> {
                val t = (phase - 0.3333f) / 0.3333f
                androidx.compose.ui.graphics.lerp(Emerald400, Rose500, t)
            }
            else -> {
                val t = (phase - 0.6666f) / 0.3334f
                androidx.compose.ui.graphics.lerp(Rose500, Indigo500, t)
            }
        }
    }
    val neonColor1 = getGradientColor(progress)
    val neonColor2 = getGradientColor(progress + 0.6666f)

    val coroutineScope = rememberCoroutineScope()

    // Debounced automatic category suggestion via Gemini
    LaunchedEffect(subcategory, type) {
        val trimmed = subcategory.trim()
        if (trimmed.length >= 3 && onSuggestCategory != null) {
            kotlinx.coroutines.delay(600)
            if (subcategory.trim() == trimmed) {
                isAiSuggesting = true
                val catNames = categories.filter { it.type == type }.map { it.name }
                val suggested = onSuggestCategory(trimmed, type, catNames)
                isAiSuggesting = false
                if (suggested.isNotBlank()) {
                    aiSuggestedCategory = suggested
                    if (!userManuallySelectedCategory || selectedCategory.isBlank()) {
                        selectedCategory = suggested
                    }
                }
            }
        } else if (trimmed.isBlank()) {
            aiSuggestedCategory = null
            isAiSuggesting = false
        }
    }

    val filteredCategories = remember(categories, type) { categories.filter { it.type == type } }

    val scrollState = rememberScrollState()
    val swipeEnabledState = LocalDialogSwipeEnabled.current
    LaunchedEffect(scrollState.isScrollInProgress) {
        swipeEnabledState.value = !scrollState.isScrollInProgress
    }

    SwipeToDismissDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(2.dp, dynamicGradient),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                    ambientColor = neonColor1.copy(alpha = 0.8f),
                    spotColor = neonColor2.copy(alpha = 0.8f)
                )
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
                    Text(
                        text = if (editingTransaction != null) "Редактировать операцию" else "Добавить операцию",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Rose500.copy(alpha = 0.15f))
                            .border(1.dp, Rose500.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Rose500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row 1: Category (Left) and Type toggle (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Category
                    Column(modifier = Modifier.weight(1f)) {
                        Text("КАТЕГОРИЯ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkBg)
                                    .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                    .clickable { dropdownExpanded = true }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedCategory.ifEmpty { "Категория" },
                                        color = if (selectedCategory.isNotEmpty()) Color.White else Slate400,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (selectedCategory == aiSuggestedCategory && !aiSuggestedCategory.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Indigo500.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("✨ ИИ", color = Indigo500, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(Slate900)
                            ) {
                                filteredCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, color = Color.White) },
                                        onClick = {
                                            selectedCategory = cat.name
                                            userManuallySelectedCategory = true
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Type Toggle (Rightmost: Morphing Plus/Minus button)
                    Column {
                        Text("ТИП", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        PlusMinusMorphToggle(
                            type = type,
                            onToggle = {
                                val newType = if (type == "expense") "income" else "expense"
                                type = newType
                                userManuallySelectedCategory = false
                                selectedCategory = categories.filter { it.type == newType }.firstOrNull()?.name ?: ""
                                aiSuggestedCategory = null
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Subcategory / Description
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ОПИСАНИЕ / НАЗВАНИЕ ОПЕРАЦИИ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (onSuggestCategory != null) {
                        Row(
                            modifier = Modifier
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    clip = false,
                                    ambientColor = Indigo500,
                                    spotColor = Indigo500
                                )
                                .background(Indigo500.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, Indigo500.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable {
                                    if (subcategory.isNotBlank() && !isAiSuggesting) {
                                        coroutineScope.launch {
                                            isAiSuggesting = true
                                            val catNames = categories.filter { it.type == type }.map { it.name }
                                            val suggested = onSuggestCategory(subcategory.trim(), type, catNames)
                                            isAiSuggesting = false
                                            if (suggested.isNotBlank()) {
                                                aiSuggestedCategory = suggested
                                                selectedCategory = suggested
                                                userManuallySelectedCategory = true
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Indigo500,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ИИ Категория", color = Indigo500, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = subcategory,
                    onValueChange = { subcategory = it.capitalizeFirstLetter() },
                    placeholder = { Text("Например: Пятерочка, Такси, Зарплата", color = Slate400) },
                    modifier = Modifier.fillMaxWidth().testTag("transaction_description_input"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    trailingIcon = {
                        if (isAiSuggesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Indigo500,
                                strokeWidth = 2.dp
                            )
                        } else if (onSuggestCategory != null && subcategory.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isAiSuggesting = true
                                        val catNames = categories.filter { it.type == type }.map { it.name }
                                        val suggested = onSuggestCategory(subcategory.trim(), type, catNames)
                                        isAiSuggesting = false
                                        if (suggested.isNotBlank()) {
                                            aiSuggestedCategory = suggested
                                            selectedCategory = suggested
                                            userManuallySelectedCategory = true
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("ai_suggest_category_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Определить категорию через Gemini",
                                    tint = Emerald400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
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

                if (isAiSuggesting) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Indigo500, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gemini подбирает категорию...", color = Indigo500, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                } else if (!aiSuggestedCategory.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(10.dp),
                                clip = false,
                                ambientColor = Indigo500,
                                spotColor = Indigo500
                            )
                            .background(Color(0xFF0F172A).copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(listOf(Indigo500, Emerald400)),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier
                                .size(14.dp)
                                .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = Emerald400, spotColor = Emerald400)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ИИ Предложение",
                                color = Indigo500,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Категория: $aiSuggestedCategory",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (selectedCategory != aiSuggestedCategory) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Emerald400.copy(alpha = 0.2f))
                                    .clickable {
                                        selectedCategory = aiSuggestedCategory!!
                                        userManuallySelectedCategory = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Применить",
                                    color = Emerald400,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row combining Amount (Left) and Date (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Amount (Left - shortened, takes available space)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("СУММА (₽)", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                            placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                            suffix = { Text("₽", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("transaction_amount_input"),
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
                    }

                    // Date (Right)
                    Column {
                        Text("ДАТА", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        CompactDatePickerField(
                            value = date,
                            onDateSelected = { date = it }
                        )
                    }
                }

                // Validation logic for required fields
                val parsedAmount = remember(amountText.text) { parseAmountInput(amountText.text) }
                val isAmountValid = parsedAmount > 0
                val isCategoryValid = selectedCategory.trim().isNotBlank()
                val isDescriptionValid = subcategory.trim().isNotBlank()
                val isDateValid = date.trim().isNotBlank()
                val isFormValid = isAmountValid && isCategoryValid && isDescriptionValid && isDateValid



                val visualNeonLevel = if (isFormValid) {
                    1f
                } else {
                    neonFlickerValue
                }

                val currentContainerColor = androidx.compose.ui.graphics.lerp(
                    Slate800,
                    Emerald400.copy(alpha = 0.15f),
                    visualNeonLevel
                )
                val currentContentColor = androidx.compose.ui.graphics.lerp(
                    Slate500,
                    Emerald400,
                    visualNeonLevel
                )
                val currentBorderColor = androidx.compose.ui.graphics.lerp(
                    Slate700,
                    Emerald400.copy(alpha = 0.5f),
                    visualNeonLevel
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Emerald400 glowing save button
                    Button(
                        onClick = {
                            if (isFormValid) {
                                onSave(type, date, selectedCategory.ifEmpty { "Прочее" }, subcategory.trim(), parsedAmount)
                                onDismiss()
                            }
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentContainerColor,
                            contentColor = currentContentColor,
                            disabledContainerColor = currentContainerColor,
                            disabledContentColor = currentContentColor
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(
                                elevation = (visualNeonLevel * 14).dp,
                                shape = CircleShape,
                                ambientColor = Emerald400,
                                spotColor = Emerald400
                            )
                            .border(
                                width = 1.dp,
                                color = currentBorderColor,
                                shape = CircleShape
                            )
                            .testTag("save_transaction_button")
                    ) {
                        Text("Сохранить", color = currentContentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
fun CategoriesDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onAddCategory: (type: String, name: String) -> Unit,
    onDeleteCategory: (id: String) -> Unit
) {
    var newType by remember { mutableStateOf("expense") }
    var newName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Удалить категорию?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Вы уверены, что хотите удалить категорию «${cat.name}»?", color = Slate300) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCategory(cat.id)
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Отмена", color = Slate400)
                }
            },
            containerColor = Slate900,
            shape = RoundedCornerShape(20.dp)
        )
    }

    val incomeCats = categories.filter { it.type == "income" }
    val expenseCats = categories.filter { it.type == "expense" }

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
                    Text("Категории операций", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBg)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .clickable {
                                newType = if (newType == "expense") "income" else "expense"
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = if (newType == "expense") "Расход" else "Доход",
                            color = if (newType == "expense") Rose500 else Emerald400,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.capitalizeFirstLetter() },
                        placeholder = { Text("Категория", color = Slate400, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
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

                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddCategory(newType, newName.trim())
                                newName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+", color = DarkBg, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("ДОХОДЫ", color = Emerald400, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    incomeCats.forEach { cat ->
                        key(cat.id) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate800)
                                    .clickable { categoryToDelete = cat }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Удалить",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("РАСХОДЫ", color = Rose500, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    expenseCats.forEach { cat ->
                        key(cat.id) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate800)
                                    .clickable { categoryToDelete = cat }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = cat.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Удалить",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllTransactionsDialog(
    transactions: List<TransactionEntity>,
    onDeleteTransaction: ((String) -> Unit)? = null,
    onEditTransaction: ((TransactionEntity) -> Unit)? = null,
    initialFilterType: String = "all",
    initialDate: String? = null,
    onDismiss: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isScrolled by remember {
        androidx.compose.runtime.derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 40
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf("date") }
    var filterType by remember { mutableStateOf(initialFilterType) } // "all", "income", "expense"
    var selectedPeriod by remember { mutableStateOf("all") } // "all", "week", "month", "year", "custom"
    var chartViewMode by remember { mutableStateOf("donut") } // "donut", "bar"
    var selectedAccountFilter by remember { mutableStateOf<String?>(null) } // null = all, "Black" etc.
    var excludeTransfers by remember { mutableStateOf(false) }
    var showDatePickerModal by remember { mutableStateOf(false) }
    var isCategoriesExpanded by remember { mutableStateOf(false) }
    var isDrilledDownToMixed by remember { mutableStateOf(false) }
    var isSearchExpandedInPlace by remember { mutableStateOf(false) }
    var showMixedCategoriesDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isScrolled) {
        if (!isScrolled) {
            isSearchExpandedInPlace = false
        }
    }

    LaunchedEffect(isSearchExpandedInPlace) {
        if (isSearchExpandedInPlace) {
            focusRequester.requestFocus()
        }
    }

    // Custom date range bounds
    var customStartStr by remember { mutableStateOf<String?>(null) }
    var customEndStr by remember { mutableStateOf<String?>(null) }
    var customLabelStr by remember { mutableStateOf<String?>(null) }

    // Synchronized current month name from first visible item on scroll
    val visibleDateStr = remember(transactions) {
        androidx.compose.runtime.derivedStateOf {
            if (transactions.isNotEmpty()) {
                val idx = (lazyListState.firstVisibleItemIndex - 4).coerceIn(0, transactions.size - 1)
                transactions.getOrNull(idx)?.date ?: transactions.first().date
            } else "2026-08-01"
        }
    }

    val defaultAnchor = remember(initialDate, transactions) {
        if (!initialDate.isNullOrBlank()) {
            initialDate
        } else if (transactions.isNotEmpty()) {
            transactions.first().date
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }
    }

    var stableAnchorDateStr by remember(defaultAnchor) { mutableStateOf(defaultAnchor) }
    var swipeWeekOffset by remember { mutableStateOf(0) }
    var swipeMonthOffset by remember { mutableStateOf(0) }
    var swipeYearOffset by remember { mutableStateOf(0) }
    var headerDragOffsetY by remember { mutableFloatStateOf(0f) }

    val currentOffsetInt = when (selectedPeriod) {
        "week" -> swipeWeekOffset
        "month" -> swipeMonthOffset
        "year" -> swipeYearOffset
        else -> swipeMonthOffset
    }

    LaunchedEffect(selectedPeriod, filterType) {
        if (stableAnchorDateStr.isBlank()) {
            stableAnchorDateStr = defaultAnchor
        }
        swipeWeekOffset = 0
        swipeMonthOffset = 0
        swipeYearOffset = 0
        isDrilledDownToMixed = false
        selectedCategoryFilter = null
    }

    val dynamicMonthLabel = remember(visibleDateStr.value, stableAnchorDateStr, customLabelStr, selectedPeriod, swipeMonthOffset, swipeWeekOffset, swipeYearOffset) {
        if (!customLabelStr.isNullOrBlank()) {
            customLabelStr!!
        } else {
            val baseDateStr = if (selectedPeriod == "all" || selectedPeriod == "custom") visibleDateStr.value else stableAnchorDateStr
            val anchorDateStr = baseDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch (e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }

            if (selectedPeriod == "week") {
                cal.add(Calendar.WEEK_OF_YEAR, swipeWeekOffset)
                val startCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    add(Calendar.DAY_OF_WEEK, 6)
                }
                val df = SimpleDateFormat("dd.MM", Locale.getDefault())
                "Неделя ${df.format(startCal.time)} - ${df.format(endCal.time)}"
            } else if (selectedPeriod == "year") {
                cal.add(Calendar.YEAR, swipeYearOffset)
                "${cal.get(Calendar.YEAR)} год"
            } else if (selectedPeriod == "month") {
                cal.add(Calendar.MONTH, swipeMonthOffset)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val monthYear = cal.get(Calendar.YEAR)
                val formatPattern = if (monthYear != currentYear) "LLLL yyyy" else "LLLL"
                val rawStr = SimpleDateFormat(formatPattern, Locale("ru", "RU")).format(cal.time)
                rawStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
            } else {
                try {
                    val d = sdf.parse(anchorDateStr)
                    if (d != null) {
                        val parsedCal = Calendar.getInstance().apply { time = d }
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        val parsedYear = parsedCal.get(Calendar.YEAR)
                        val formatPattern = if (parsedYear != currentYear) "LLLL yyyy" else "LLLL"
                        val rawStr = SimpleDateFormat(formatPattern, Locale("ru", "RU")).format(d)
                        rawStr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString() }
                    } else "Август"
                } catch (e: Exception) {
                    "Август"
                }
            }
        }
    }

    // Filter transactions by period ("week", "month", "year", "custom") relative to visible/selected month
    val periodFilteredList = remember(transactions, selectedPeriod, customStartStr, customEndStr, visibleDateStr.value, stableAnchorDateStr, swipeWeekOffset, swipeMonthOffset, swipeYearOffset) {
        if (selectedPeriod == "custom" && !customStartStr.isNullOrBlank() && !customEndStr.isNullOrBlank()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startDate = try { sdf.parse(customStartStr!!) } catch (e: Exception) { null }
            val endDate = try { sdf.parse(customEndStr!!) } catch (e: Exception) { null }
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch (e: Exception) { null }
                if (txDate == null) true
                else if (startDate != null && endDate != null) {
                    !txDate.before(startDate) && !txDate.after(endDate)
                } else true
            }
        } else if (selectedPeriod == "week") {
            val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }
            cal.add(Calendar.WEEK_OF_YEAR, swipeWeekOffset)
            val adjustedDate = cal.time
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                if (txDate == null) true
                else {
                    val diffMs = kotlin.math.abs(adjustedDate.time - txDate.time)
                    val diffDays = diffMs / (1000 * 60 * 60 * 24)
                    diffDays <= 7
                }
            }
        } else if (selectedPeriod == "month") {
            val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }
            cal.add(Calendar.MONTH, swipeMonthOffset)
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH)
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                if (txDate == null) true
                else {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth
                }
            }
        } else if (selectedPeriod == "year") {
            val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
            val cal = Calendar.getInstance().apply { time = anchorDate }
            cal.add(Calendar.YEAR, swipeYearOffset)
            val currentYear = cal.get(Calendar.YEAR)
            transactions.filter { tx ->
                val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                if (txDate == null) true
                else {
                    val txCal = Calendar.getInstance().apply { time = txDate }
                    txCal.get(Calendar.YEAR) == currentYear
                }
            }
        } else {
            // "all" - show ALL transactions!
            transactions
        }
    }

    fun getFilteredListForOffset(offset: Int): List<TransactionEntity> {
        val effectivePeriod = if (selectedPeriod == "all" || selectedPeriod == "custom") "month" else selectedPeriod

        val anchorDateStr = if (selectedPeriod == "all" || selectedPeriod == "custom") {
            visibleDateStr.value.ifBlank { "2026-08-01" }
        } else {
            stableAnchorDateStr.ifBlank { "2026-08-01" }
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
        val cal = Calendar.getInstance().apply { time = anchorDate }

        val baseList = if (selectedPeriod == "custom") periodFilteredList else transactions

        return when (effectivePeriod) {
            "week" -> {
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                val adjustedDate = cal.time
                baseList.filter { tx ->
                    val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                    if (txDate == null) true
                    else {
                        val diffMs = kotlin.math.abs(adjustedDate.time - txDate.time)
                        val diffDays = diffMs / (1000 * 60 * 60 * 24)
                        diffDays <= 7
                    }
                }
            }
            "month" -> {
                cal.add(Calendar.MONTH, offset)
                val currentYear = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH)
                baseList.filter { tx ->
                    val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                    if (txDate == null) true
                    else {
                        val txCal = Calendar.getInstance().apply { time = txDate }
                        txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth
                    }
                }
            }
            "year" -> {
                cal.add(Calendar.YEAR, offset)
                val currentYear = cal.get(Calendar.YEAR)
                baseList.filter { tx ->
                    val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                    if (txDate == null) true
                    else {
                        val txCal = Calendar.getInstance().apply { time = txDate }
                        txCal.get(Calendar.YEAR) == currentYear
                    }
                }
            }
            else -> baseList
        }
    }

    fun getCategoryTotalsForOffset(offset: Int): List<Pair<String, Double>> {
        val rawList = getFilteredListForOffset(offset)
        val typeFiltered = when (filterType) {
            "income" -> rawList.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> rawList.filter { it.type.equals("expense", ignoreCase = true) }
            else -> rawList
        }
        val fullMap = typeFiltered
            .groupBy { it.category.ifBlank { "Прочее" } }
            .map { (cat, list) -> cat to list.sumOf { kotlin.math.abs(it.amount) } }
            .sortedByDescending { it.second }

        val threshold = 3
        return if (fullMap.size > threshold) {
            if (isDrilledDownToMixed) {
                fullMap.drop(2)
            } else {
                val top2 = fullMap.take(2)
                val remaining = fullMap.drop(2)
                val remainingSum = remaining.sumOf { it.second }
                val remainingCount = remaining.size
                val mixedName = if (remainingCount <= 1) "✨ Прочие" else "✨ Смешанные (+$remainingCount)"
                (top2 + (mixedName to remainingSum)).sortedByDescending { it.second }
            }
        } else {
            fullMap
        }
    }

    // Filtered by type ("expense", "income", "all")
    val typeFilteredList = remember(periodFilteredList, filterType) {
        when (filterType) {
            "income" -> periodFilteredList.filter { it.type.equals("income", ignoreCase = true) }
            "expense" -> periodFilteredList.filter { it.type.equals("expense", ignoreCase = true) }
            else -> periodFilteredList
        }
    }

    // Category sums map for current filter
    val categoryTotalsMap = remember(typeFilteredList, visibleDateStr.value, selectedPeriod) {
        val listForCategory = if (selectedPeriod == "all" || selectedPeriod == "custom") {
            val dateParts = visibleDateStr.value.split("-")
            if (dateParts.size >= 2) {
                val prefix = "${dateParts[0]}-${dateParts[1]}-"
                typeFilteredList.filter { it.date.startsWith(prefix) }
            } else {
                typeFilteredList
            }
        } else {
            typeFilteredList
        }
        listForCategory
            .groupBy { it.category.ifBlank { "Прочее" } }
            .mapValues { entry -> entry.value.sumOf { kotlin.math.abs(it.amount) } }
            .filterValues { it > 0 }
            .toList()
            .sortedByDescending { it.second }
    }

    val categoryThreshold = 3
    val shouldGroup = categoryTotalsMap.size > categoryThreshold

    val accentColor = remember(filterType) {
        if (filterType == "expense") Rose500 else Emerald400
    }

    val remainingCategoryNames = remember(categoryTotalsMap) {
        if (categoryTotalsMap.size > categoryThreshold) {
            categoryTotalsMap.drop(2).map { it.first }
        } else {
            emptyList()
        }
    }

    val categoryColorMap = remember(categoryTotalsMap, filterType) {
        val map = mutableMapOf<String, Color>()
        categoryTotalsMap.forEachIndexed { index, (catName, _) ->
            val (systemColor, _) = getCategoryColorAndIcon(catName, "")
            if (systemColor == Slate400) {
                val baseColors = if (filterType == "income") {
                    listOf(
                        Color(0xFF10B981), // Emerald
                        Color(0xFF6366F1), // Indigo
                        Color(0xFF3B82F6), // Blue
                        Color(0xFFF59E0B), // Amber
                        Color(0xFF8B5CF6), // Purple
                        Color(0xFF14B8A6)  // Teal
                    )
                } else {
                    listOf(
                        Color(0xFFF43F5E), // Rose
                        Color(0xFF3B82F6), // Blue
                        Color(0xFFF59E0B), // Amber
                        Color(0xFF8B5CF6), // Purple
                        Color(0xFF10B981), // Emerald
                        Color(0xFF14B8A6)  // Teal
                    )
                }
                map[catName] = baseColors.getOrElse(index % baseColors.size) { if (filterType == "expense") Rose500 else Emerald400 }
            } else {
                map[catName] = systemColor
            }
        }
        map["✨ Смешанные"] = Indigo500
        map["✨ Прочие"] = Indigo500
        map
    }

    fun getCategoryColor(name: String): Color {
        val cleanName = if (name.startsWith("✨")) {
            if (name.contains("Прочие")) "✨ Прочие" else "✨ Смешанные"
        } else {
            name
        }
        return categoryColorMap.get(cleanName) ?: run {
            val (systemColor, _) = getCategoryColorAndIcon(cleanName, "")
            if (systemColor == Slate400) {
                if (filterType == "expense") Rose500 else Emerald400
            } else {
                systemColor
            }
        }
    }

    val currentActiveCategoryTotals = remember(categoryTotalsMap, isDrilledDownToMixed) {
        if (categoryTotalsMap.size > categoryThreshold) {
            if (isDrilledDownToMixed) {
                categoryTotalsMap.drop(2)
            } else {
                val top2 = categoryTotalsMap.take(2)
                val remaining = categoryTotalsMap.drop(2)
                val remainingSum = remaining.sumOf { it.second }
                val remainingCount = remaining.size
                val mixedName = if (remainingCount <= 1) "✨ Прочие" else "✨ Смешанные (+$remainingCount)"
                (top2 + (mixedName to remainingSum)).sortedByDescending { it.second }
            }
        } else {
            categoryTotalsMap
        }
    }

    // Search and category filtered
    val searchFilteredList = remember(typeFilteredList, searchQuery, selectedCategoryFilter, isDrilledDownToMixed, remainingCategoryNames) {
        typeFilteredList.filter { tx ->
            val txCat = tx.category.ifBlank { "Прочее" }
            val matchesCategory = when {
                selectedCategoryFilter == "✨ Смешанные" || selectedCategoryFilter == "✨ Прочие" -> {
                    remainingCategoryNames.any { it.equals(txCat, ignoreCase = true) }
                }
                isDrilledDownToMixed && selectedCategoryFilter == null -> {
                    remainingCategoryNames.any { it.equals(txCat, ignoreCase = true) }
                }
                selectedCategoryFilter.isNullOrBlank() -> true
                else -> txCat.equals(selectedCategoryFilter, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                    tx.subcategory.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Sorted
    val sortedList = remember(searchFilteredList, sortOption) {
        when (sortOption) {
            "desc" -> searchFilteredList.sortedByDescending { kotlin.math.abs(it.amount) }
            "asc" -> searchFilteredList.sortedBy { kotlin.math.abs(it.amount) }
            "name" -> searchFilteredList.sortedBy { it.category.ifBlank { it.subcategory } }
            "name_desc" -> searchFilteredList.sortedByDescending { it.category.ifBlank { it.subcategory } }
            else -> searchFilteredList.sortedByDescending { it.date }
        }
    }

    val groupedByDate = remember(sortedList) {
        sortedList.groupBy { it.date }
    }

    val totalExpenseAmt = remember(periodFilteredList, visibleDateStr.value, selectedPeriod) {
        if (selectedPeriod == "all" || selectedPeriod == "custom") {
            val dateParts = visibleDateStr.value.split("-")
            if (dateParts.size >= 2) {
                val prefix = "${dateParts[0]}-${dateParts[1]}-"
                periodFilteredList.filter { it.type == "expense" && it.date.startsWith(prefix) }
                    .sumOf { kotlin.math.abs(it.amount) }
            } else {
                periodFilteredList.filter { it.type == "expense" }.sumOf { kotlin.math.abs(it.amount) }
            }
        } else {
            periodFilteredList.filter { it.type == "expense" }.sumOf { kotlin.math.abs(it.amount) }
        }
    }
    val totalIncomeAmt = remember(periodFilteredList, visibleDateStr.value, selectedPeriod) {
        if (selectedPeriod == "all" || selectedPeriod == "custom") {
            val dateParts = visibleDateStr.value.split("-")
            if (dateParts.size >= 2) {
                val prefix = "${dateParts[0]}-${dateParts[1]}-"
                periodFilteredList.filter { it.type == "income" && it.date.startsWith(prefix) }
                    .sumOf { kotlin.math.abs(it.amount) }
            } else {
                periodFilteredList.filter { it.type == "income" }.sumOf { kotlin.math.abs(it.amount) }
            }
        } else {
            periodFilteredList.filter { it.type == "income" }.sumOf { kotlin.math.abs(it.amount) }
        }
    }

    val animatedHeaderDragOffsetY by animateFloatAsState(
        targetValue = headerDragOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "header_drag_offset"
    )

    SwipeToDismissDialog(
        onDismissRequest = onDismiss,
        isAtTop = { lazyListState.firstVisibleItemIndex == 0 },
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 12.dp, bottom = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.96f)
                    .offset { IntOffset(x = 0, y = animatedHeaderDragOffsetY.roundToInt()) },
                color = DarkBg,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Vertical drag handle for swipe dismissal (identical to ExpenseSharesScreen.kt)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { headerDragOffsetY = 0f },
                                    onDragEnd = {
                                        if (headerDragOffsetY > 80f) {
                                            onDismiss()
                                        } else {
                                            headerDragOffsetY = 0f
                                        }
                                    },
                                    onDragCancel = { headerDragOffsetY = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        if (dragAmount > 0f || headerDragOffsetY > 0f) {
                                            headerDragOffsetY = (headerDragOffsetY + dragAmount).coerceAtLeast(0f)
                                        }
                                    }
                                )
                            }
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Slate700)
                        )
                    }

                    // Header Bar (Cleaned without accounts & transfers)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Start,
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
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Emerald400, Indigo500, Rose500)
                                        )
                                    )
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(DarkBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Все операции",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Показано ${sortedList.size} из ${transactions.size} операций",
                                    color = Slate400,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Pinned Collapsible / Morphing Header or Full-Size Components
                    val showCompactHeader = isScrolled && filterType != "all"

                    AnimatedContent(
                        targetState = showCompactHeader,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(180, delayMillis = 60)) + androidx.compose.animation.scaleIn(initialScale = 0.96f, animationSpec = tween(180, delayMillis = 60)))
                                .togetherWith(fadeOut(animationSpec = tween(100)) + androidx.compose.animation.scaleOut(targetScale = 0.98f, animationSpec = tween(100)))
                        },
                        label = "header_morph_animation"
                    ) { targetCompact ->
                        if (targetCompact) {
                            val isExpense = filterType == "expense"
                            val accentColor = if (isExpense) Rose500 else Emerald400
                            val activeAmount = if (isExpense) totalExpenseAmt else totalIncomeAmt
                            val titleText = if (isExpense) "Траты" else "Доходы"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Slate900)
                                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Small doughnut/circular chart
                                    Canvas(modifier = Modifier.size(36.dp)) {
                                        val strokeWidth = 5.dp.toPx()
                                        val radius = (size.minDimension - strokeWidth) / 2
                                        val centerOffset = Offset(size.width / 2, size.height / 2)

                                        if (currentActiveCategoryTotals.isEmpty() || currentActiveCategoryTotals.sumOf { it.second } <= 0) {
                                            drawCircle(
                                                color = Slate800,
                                                radius = radius,
                                                center = centerOffset,
                                                style = Stroke(width = strokeWidth)
                                            )
                                        } else {
                                            val sumAll = currentActiveCategoryTotals.sumOf { it.second }
                                            var startAngle = -90f

                                            currentActiveCategoryTotals.forEachIndexed { _, (catName, amt) ->
                                                val sweepAngle = ((amt / sumAll) * 360f).toFloat()
                                                val col = getCategoryColor(catName)
                                                drawArc(
                                                    color = col,
                                                    startAngle = startAngle,
                                                    sweepAngle = sweepAngle - 2f,
                                                    useCenter = false,
                                                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                                )
                                                startAngle += sweepAngle
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = titleText,
                                            color = Slate400,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = formatFullCurrency(activeAmount),
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                val activeFilterText = when {
                                    !selectedCategoryFilter.isNullOrBlank() -> selectedCategoryFilter
                                    isDrilledDownToMixed -> {
                                        if (remainingCategoryNames.size <= 1) "✨ Прочие" else "✨ Смешанные"
                                    }
                                    else -> null
                                }

                                if (!activeFilterText.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accentColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = activeFilterText,
                                            color = accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { 
                                        filterType = "all"
                                        selectedCategoryFilter = null
                                        isDrilledDownToMixed = false
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Slate800)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "К всем",
                                        tint = Slate400,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        } else {
                            // Full-Size UI Header
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Pinned Hero Card (Expenses / Income / Both)
                                if (filterType == "all") {
                                    // Double Summary Cards
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Expense Card
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(Slate900)
                                                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                                                    .clickable { 
                                                        if (filterType == "expense") {
                                                            selectedPeriod = "month"
                                                        } else {
                                                            filterType = "expense"
                                                        }
                                                    }
                                                    .padding(14.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = formatFullCurrency(totalExpenseAmt),
                                                        color = Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                    Text("Траты", color = Slate400, fontSize = 11.sp)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp)
                                                            .clip(CircleShape)
                                                            .background(Rose500)
                                                    )
                                                }
                                            }

                                            // Income Card
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(Slate900)
                                                    .border(1.dp, Slate800, RoundedCornerShape(20.dp))
                                                    .clickable { 
                                                        if (filterType == "income") {
                                                            selectedPeriod = "month"
                                                        } else {
                                                            filterType = "income"
                                                        }
                                                    }
                                                    .padding(14.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = formatFullCurrency(totalIncomeAmt),
                                                        color = Color.White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                    Text("Доходы", color = Slate400, fontSize = 11.sp)
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp)
                                                            .clip(CircleShape)
                                                            .background(Emerald400)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Single Hero Card (Expense or Income)
                                    val isExpense = filterType == "expense"
                                    val accentColor = if (isExpense) Rose500 else Emerald400
                                    val activeAmount = if (filterType == "expense") totalExpenseAmt else totalIncomeAmt
                                    val prevAmount = remember(selectedPeriod, currentOffsetInt, transactions, filterType, visibleDateStr.value) {
                                        if (selectedPeriod == "all" || selectedPeriod == "custom") {
                                            val dateParts = visibleDateStr.value.split("-")
                                            if (dateParts.size >= 2) {
                                                val year = dateParts[0].toIntOrNull() ?: 2026
                                                val month = dateParts[1].toIntOrNull() ?: 8
                                                val prevMonth = if (month == 1) 12 else month - 1
                                                val prevYear = if (month == 1) year - 1 else year
                                                val prefix = String.format("%04d-%02d-", prevYear, prevMonth)
                                                val tType = if (filterType == "expense") "expense" else "income"
                                                transactions.filter { it.type.equals(tType, ignoreCase = true) && it.date.startsWith(prefix) }
                                                    .sumOf { kotlin.math.abs(it.amount) }
                                            } else {
                                                0.0
                                            }
                                        } else {
                                            val prevOffset = currentOffsetInt - 1
                                            val prevList = getFilteredListForOffset(prevOffset)
                                            val tType = if (filterType == "expense") "expense" else "income"
                                            prevList.filter { it.type.equals(tType, ignoreCase = true) }.sumOf { kotlin.math.abs(it.amount) }
                                        }
                                    }
                                    val amountDiff = activeAmount - prevAmount
                                    val diffText = if (amountDiff >= 0) {
                                        "↑ ${formatFullCurrency(amountDiff)}"
                                    } else {
                                        "↓ ${formatFullCurrency(kotlin.math.abs(amountDiff))}"
                                    }
                                    val titleText = if (isExpense) "Траты" else "Доходы"

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(Slate900.copy(alpha = 0.9f))
                                            .border(1.dp, Slate800, RoundedCornerShape(24.dp))
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Header inside card
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = formatFullCurrency(activeAmount),
                                                        color = Color.White,
                                                        fontSize = 32.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(Color.White.copy(alpha = 0.08f))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = diffText,
                                                            color = Slate300,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = titleText,
                                                    color = Slate400,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            }

                                            IconButton(
                                                onClick = { filterType = "all" },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Slate800)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "К всем",
                                                    tint = Slate400,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        // Chart area inside Swipe Gesture detector
                                        var totalDragX by remember { mutableFloatStateOf(0f) }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pointerInput(selectedPeriod) {
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { totalDragX = 0f },
                                                        onDragEnd = {
                                                            val threshold = 120f
                                                            if (totalDragX < -threshold) {
                                                                // Swipe Left -> Next
                                                                when (selectedPeriod) {
                                                                    "week" -> swipeWeekOffset += 1
                                                                    "month" -> swipeMonthOffset += 1
                                                                    "year" -> swipeYearOffset += 1
                                                                    else -> swipeMonthOffset += 1
                                                                }
                                                            } else if (totalDragX > threshold) {
                                                                // Swipe Right -> Prev
                                                                when (selectedPeriod) {
                                                                    "week" -> swipeWeekOffset -= 1
                                                                    "month" -> swipeMonthOffset -= 1
                                                                    "year" -> swipeYearOffset -= 1
                                                                    else -> swipeMonthOffset -= 1
                                                                }
                                                            }
                                                        },
                                                        onHorizontalDrag = { change, dragAmount ->
                                                            change.consume()
                                                            totalDragX += dragAmount
                                                        }
                                                    )
                                                }
                                        ) {
                                            BoxWithConstraints(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(250.dp)
                                                    .clipToBounds()
                                            ) {
                                                val widthPx = constraints.maxWidth
                                                val widthDp = with(androidx.compose.ui.platform.LocalDensity.current) { widthPx.toDp() }
                                                
                                                val pageWidthDp = widthDp * 0.8f
                                                val centerOffsetDp = widthDp * 0.1f
                                                
                                                val animatedOffsetFloat by animateFloatAsState(
                                                    targetValue = currentOffsetInt.toFloat(),
                                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                                                    label = "diagram_slide_offset"
                                                )
                                                
                                                listOf(currentOffsetInt - 1, currentOffsetInt, currentOffsetInt + 1).forEach { offsetValue ->
                                                    val translationX = centerOffsetDp + pageWidthDp * (offsetValue - animatedOffsetFloat)
                                                    val relativeDiff = kotlin.math.abs(offsetValue - animatedOffsetFloat)
                                                    val alphaValue = if (offsetValue == currentOffsetInt) {
                                                        1f - kotlin.math.min(1f, relativeDiff) * 0.4f
                                                    } else {
                                                        0.3f + (1f - kotlin.math.min(1f, relativeDiff)) * 0.3f
                                                    }
                                                    
                                                    val scaleValue = if (offsetValue == currentOffsetInt) {
                                                        1f - kotlin.math.min(1f, relativeDiff) * 0.12f
                                                    } else {
                                                        0.88f + (1f - kotlin.math.min(1f, relativeDiff)) * 0.12f
                                                    }

                                                    Box(
                                                        modifier = Modifier
                                                            .width(pageWidthDp)
                                                            .fillMaxHeight()
                                                            .offset(x = translationX)
                                                            .graphicsLayer {
                                                                alpha = alphaValue
                                                                scaleX = scaleValue
                                                                scaleY = scaleValue
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        val offsetTotalsMap = getCategoryTotalsForOffset(offsetValue)
                                                        
                                                        if (chartViewMode == "donut") {
                                                            Box(
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                val sumAll = offsetTotalsMap.sumOf { it.second }
                                                                
                                                                 Box(
                                                                     modifier = Modifier.size(240.dp),
                                                                     contentAlignment = Alignment.Center
                                                                 ) {
                                                                     Canvas(modifier = Modifier.size(170.dp)) {
                                                                     val strokeWidth = 20.dp.toPx()
                                                                     val radius = (size.minDimension - strokeWidth) / 2
                                                                     val centerOffset = Offset(size.width / 2, size.height / 2)
                                                                     
                                                                     if (offsetTotalsMap.isEmpty() || sumAll <= 0) {
                                                                         drawCircle(
                                                                             color = Slate800,
                                                                             radius = radius,
                                                                             center = centerOffset,
                                                                             style = Stroke(width = strokeWidth)
                                                                         )
                                                                     } else {
                                                                         var startAngle = -90f
                                                                         
                                                                         offsetTotalsMap.forEach { (catName, amt) ->
                                                                             val sweepAngle = ((amt / sumAll) * 360f).toFloat()
                                                                             val col = getCategoryColor(catName)
                                                                             
                                                                             if (sweepAngle > 0f) {
                                                                                 // Glow effect backdrop arc
                                                                                 drawArc(
                                                                                     color = col.copy(alpha = 0.18f),
                                                                                     startAngle = startAngle,
                                                                                     sweepAngle = sweepAngle - 3f,
                                                                                     useCenter = false,
                                                                                     topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                                                                     size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                                                                     style = Stroke(width = strokeWidth * 1.4f, cap = StrokeCap.Round)
                                                                                 )
                                                                                 
                                                                                 // Main crisp arc
                                                                                 drawArc(
                                                                                     color = col,
                                                                                     startAngle = startAngle,
                                                                                     sweepAngle = sweepAngle - 3f,
                                                                                     useCenter = false,
                                                                                     topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                                                                     size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                                                                     style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                                                                 )
                                                                             }
                                                                             startAngle += sweepAngle
                                                                         }
                                                                     }
                                                                 }
                                                                 
                                                                 // Overlays removed
                                                                 if (false) {
                                                                     var startAngle = -90f
                                                                     offsetTotalsMap.forEach { (catName, amt) ->
                                                                         val sweepAngle = ((amt / sumAll) * 360f).toFloat()
                                                                         val midAngle = startAngle + sweepAngle / 2f
                                                                         val angleRad = Math.toRadians(midAngle.toDouble())
                                                                         
                                                                         val centerOffsetVal = 120.dp
                                                                         
                                                                         // 1. Position and draw category icon inside track if segment is large enough
                                                                         if (sweepAngle > 12f) {
                                                                             val arcRadius = 75.dp
                                                                             val iconX = centerOffsetVal + arcRadius * Math.cos(angleRad).toFloat()
                                                                             val iconY = centerOffsetVal + arcRadius * Math.sin(angleRad).toFloat()
                                                                             
                                                                             val col = getCategoryColor(catName)
                                              val icon = if (catName.startsWith("✨")) {
                                                  Icons.Default.List
                                              } else {
                                                  getCategoryColorAndIcon(catName, "").second
                                              }
                                                                             
                                                                             Box(
                                                                                 modifier = Modifier
                                                                                     .size(26.dp)
                                                                                     .offset(x = iconX - 13.dp, y = iconY - 13.dp)
                                                                                     .shadow(
                                                                                         elevation = 6.dp,
                                                                                         shape = CircleShape,
                                                                                         ambientColor = col,
                                                                                         spotColor = col
                                                                                     )
                                                                                     .clip(CircleShape)
                                                                                     .background(Color.Black.copy(alpha = 0.5f))
                                                                                     .border(1.dp, col.copy(alpha = 0.8f), CircleShape),
                                                                                 contentAlignment = Alignment.Center
                                                                             ) {
                                                                                 Icon(
                                                                                     imageVector = icon,
                                                                                     contentDescription = null,
                                                                                     tint = Color.White,
                                                                                     modifier = Modifier.size(13.dp)
                                                                                 )
                                                                             }
                                                                         }
                                                                         
                                                                         // 2. Position and draw percentage text outside the track
                                                                         val pct = ((amt / sumAll) * 100).let { kotlin.math.round(it).toInt() }
                                                                         if (pct > 0) {
                                                                             val textRadius = 100.dp
                                                                             val textX = centerOffsetVal + textRadius * Math.cos(angleRad).toFloat()
                                                                             val textY = centerOffsetVal + textRadius * Math.sin(angleRad).toFloat()
                                                                             
                                                                             Box(
                                                                                 modifier = Modifier
                                                                                     .offset(x = textX - 25.dp, y = textY - 8.dp)
                                                                                     .width(50.dp),
                                                                                 contentAlignment = Alignment.Center
                                                                             ) {
                                                                                 Text(
                                                                                     text = "$pct%",
                                                                                     color = Slate300,
                                                                                     fontSize = 12.sp,
                                                                                     fontWeight = FontWeight.Bold,
                                                                                     fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                                                 )
                                                                             }
                                                                         }
                                                                         
                                                                         startAngle += sweepAngle
                                                                     }
                                                                 }
                                                             }
                                                        }
                                                        } else {
                                                            val effectivePeriod = if (selectedPeriod == "all") "month" else selectedPeriod
                                                            val anchorDateStr = stableAnchorDateStr.ifBlank { "2026-08-01" }
                                                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                            val anchorDate = try { sdf.parse(anchorDateStr) } catch(e: Exception) { null } ?: Date()
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
                                                                        val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
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
                                                                        val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
                                                                        if (txDate != null) {
                                                                            val txCal = Calendar.getInstance().apply { time = txDate }
                                                                            weekDates.forEachIndexed { idx, wDate ->
                                                                                val wCal = Calendar.getInstance().apply { time = wDate }
                                                                                if (txCal.get(Calendar.YEAR) == wCal.get(Calendar.YEAR) &&
                                                                                    txCal.get(Calendar.DAY_OF_YEAR) == wCal.get(Calendar.DAY_OF_YEAR)) {
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
                                                                        val txDate = try { sdf.parse(tx.date) } catch(e: Exception) { null }
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
                                                                modifier = Modifier
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
                                                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
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
                                                    }
                                                }
                                            }
                                        }

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
                                                            .clickable { selectedPeriod = pKey },
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
                                                    .clickable { chartViewMode = "donut" }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text("🔄", fontSize = 11.sp)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (chartViewMode == "bar") Slate800 else Color.Transparent)
                                                    .clickable { chartViewMode = "bar" }
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
                                                                isDrilledDownToMixed = false
                                                                selectedCategoryFilter = null
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
                                                    remainingCategories.forEachIndexed { idx, (catName, remainingSumAmt) ->
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
                                                                    selectedCategoryFilter = if (isCatSelected) null else catName
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

                                                    visibleCategories.forEachIndexed { idx, (catName, sumAmt) ->
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
                                                                    selectedCategoryFilter = if (isCatSelected) null else catName
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
                                                                    showMixedCategoriesDialog = true
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
                            }
                        }
                    }
                }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Pinned Sticky Sorting Options Row (outside LazyColumn)
                    androidx.compose.animation.AnimatedContent(
                        targetState = isSearchExpandedInPlace,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(150)) + androidx.compose.animation.scaleIn(initialScale = 0.98f))
                                .togetherWith(fadeOut(animationSpec = tween(100)) + androidx.compose.animation.scaleOut(targetScale = 0.98f))
                        },
                        label = "search_row_in_sorting_morph"
                    ) { expanded ->
                        if (expanded) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .padding(vertical = 2.dp),
                                color = Slate900,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (searchQuery.isNotEmpty()) Indigo500 else Slate800
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = if (searchQuery.isNotEmpty()) Indigo500 else Slate400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Быстрый поиск...",
                                                    color = Slate500,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Скрыть поиск",
                                        tint = Slate400,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { 
                                                isSearchExpandedInPlace = false
                                            }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Сортировка:",
                                        color = Slate400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    val isCatActive = !selectedCategoryFilter.isNullOrBlank() || isDrilledDownToMixed
                                    if (isCatActive) {
                                        val catLabel = selectedCategoryFilter ?: if (remainingCategoryNames.size <= 1) "Прочие" else "Смешанные"
                                        val catColor = if (!selectedCategoryFilter.isNullOrBlank()) getCategoryColor(selectedCategoryFilter!!) else Indigo500
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(catColor.copy(alpha = 0.2f))
                                                .border(1.dp, catColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .clickable {
                                                    selectedCategoryFilter = null
                                                    isDrilledDownToMixed = false
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = catLabel,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "✕",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isPeriodActive = selectedPeriod != "all" || !customStartStr.isNullOrBlank()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isPeriodActive) Indigo500 else Slate900)
                                            .border(1.dp, if (isPeriodActive) Indigo500 else Slate800, RoundedCornerShape(8.dp))
                                            .clickable { showDatePickerModal = true }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DateRange,
                                                contentDescription = null,
                                                tint = if (isPeriodActive) Color.White else Slate400,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = dynamicMonthLabel,
                                                color = if (isPeriodActive) Color.White else Slate400,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isPeriodActive) {
                                                Text(
                                                    text = "✕",
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.clickable {
                                                        selectedPeriod = "all"
                                                        customStartStr = null
                                                        customEndStr = null
                                                        customLabelStr = null
                                                    }
                                                )
                                            } else {
                                                Text("▼", color = Slate400, fontSize = 8.sp)
                                            }
                                        }
                                    }

                                    val isAmountActive = sortOption == "desc" || sortOption == "asc"
                                    val arrowRotation by animateFloatAsState(
                                        targetValue = if (sortOption == "asc") 180f else 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "arrowRotation"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isAmountActive) Indigo500 else Slate900)
                                            .border(1.dp, if (isAmountActive) Indigo500 else Slate800, RoundedCornerShape(8.dp))
                                            .clickable {
                                                sortOption = when (sortOption) {
                                                    "desc" -> "asc"
                                                    "asc" -> "date"
                                                    else -> "desc"
                                                 }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "↓",
                                                color = if (isAmountActive) Color.White else Slate400,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                                            )
                                            Text(
                                                text = "Сумма",
                                                color = if (isAmountActive) Color.White else Slate400,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    val isNameActive = sortOption == "name" || sortOption == "name_desc"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isNameActive) Indigo500 else Slate900)
                                            .border(1.dp, if (isNameActive) Indigo500 else Slate800, RoundedCornerShape(8.dp))
                                            .clickable {
                                                sortOption = when (sortOption) {
                                                    "name" -> "name_desc"
                                                    "name_desc" -> "date"
                                                    else -> "name"
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = if (sortOption == "name_desc") "Z-A" else "A-Z",
                                            color = if (isNameActive) Color.White else Slate400,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (isScrolled) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (searchQuery.isNotEmpty()) Indigo500.copy(alpha = 0.2f) else Slate900)
                                                .border(
                                                    1.dp,
                                                    if (searchQuery.isNotEmpty()) Indigo500 else Slate800,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    isSearchExpandedInPlace = true
                                                }
                                                .size(26.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "Поиск",
                                                tint = if (searchQuery.isNotEmpty()) Indigo500 else Slate400,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // LazyColumn containing transactions
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 0.dp,
                            start = 0.dp,
                            end = 0.dp,
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Unpinned Search Input at the top of scroll list
                        item(key = "search_bar_unpinned") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(vertical = 2.dp),
                                color = Slate900,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (searchQuery.isNotEmpty()) Indigo500.copy(alpha = 0.6f) else Slate800
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = if (searchQuery.isNotEmpty()) Indigo500 else Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Поиск операций...",
                                                    color = Slate500,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Очистить",
                                            tint = Slate400,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { searchQuery = "" }
                                        )
                                    }
                                }
                            }
                        }

                        // Grouped Transactions List
                        if (sortedList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Операции не найдены",
                                        color = Slate500,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            groupedByDate.forEach { (dateStr, itemsForDay) ->
                                item(key = "header_$dateStr") {
                                    val dayTotalExpense = itemsForDay.filter { it.type == "expense" }.sumOf { kotlin.math.abs(it.amount) }
                                    val dayTotalIncome = itemsForDay.filter { it.type == "income" }.sumOf { kotlin.math.abs(it.amount) }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatDayHeaderLabel(dateStr),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (filterType == "income" && dayTotalIncome > 0) {
                                            Text(
                                                text = "+${formatFullCurrency(dayTotalIncome)}",
                                                color = Emerald400,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else if (dayTotalExpense > 0) {
                                            Text(
                                                text = "-${formatFullCurrency(dayTotalExpense)}",
                                                color = Slate400,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                items(itemsForDay, key = { it.id }) { tx ->
                                    TransactionRowItem(
                                        item = tx,
                                        onDelete = { txId -> onDeleteTransaction?.invoke(txId) },
                                        onClick = { onEditTransaction?.invoke(tx) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Date Range Selection Dialog Modal
        if (showDatePickerModal) {
            DateRangePickerDialog(
                initialStart = customStartStr ?: visibleDateStr.value.ifBlank { "2026-07-01" },
                initialEnd = customEndStr ?: "2026-08-31",
                onDismiss = { showDatePickerModal = false },
                onConfirm = { s, e ->
                    customStartStr = s
                    customEndStr = e
                    selectedPeriod = "custom"
                    customLabelStr = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val d1 = sdf.parse(s)
                        val d2 = sdf.parse(e)
                        if (d1 != null && d2 != null) {
                            val outSdf = SimpleDateFormat("d MMM", Locale("ru", "RU"))
                            "${outSdf.format(d1)} - ${outSdf.format(d2)}"
                        } else "$s - $e"
                    } catch (ex: Exception) { "$s - $e" }
                    showDatePickerModal = false
                }
            )
        }

        // Mixed Categories Pop-Up Window/Dialog
        if (showMixedCategoriesDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showMixedCategoriesDialog = false }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Indigo500.copy(alpha = 0.5f),
                            spotColor = Indigo500.copy(alpha = 0.5f)
                        ),
                    color = DarkBg,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(Emerald400, Indigo500, Rose500)
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header with close button
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
                                    text = "✨ Смешанные",
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

                            IconButton(
                                onClick = { showMixedCategoriesDialog = false },
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Slate800)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Закрыть",
                                    tint = Slate400,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // Categories Scrollable List
                        val remainingCategories = categoryTotalsMap.drop(2)
                        val totalSum = categoryTotalsMap.sumOf { it.second }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
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
                                        .background(Slate900.copy(alpha = 0.4f))
                                        .border(1.dp, Slate800, RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedCategoryFilter = catName
                                            isDrilledDownToMixed = true
                                            showMixedCategoriesDialog = false
                                        }
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
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
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


