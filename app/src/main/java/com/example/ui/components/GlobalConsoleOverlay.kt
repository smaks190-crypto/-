package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.example.BuildConfig
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.utils.GlobalConsoleLogger
import com.example.utils.LogEntry
import com.example.utils.LogLevel
import kotlin.math.roundToInt

@Composable
fun GlobalConsoleOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return

    val logs by GlobalConsoleLogger.logs.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var selectedTagFilter by remember { mutableStateOf("ВСЕ") }
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val tags = listOf("ВСЕ", "UI_ACTION", "UI_DIALOG", "CALCULATION", "ROOM_QUERY", "VOSK_STREAM", "GEMINI", "ERROR")

    val filteredLogs = remember(logs, selectedTagFilter) {
        if (selectedTagFilter == "ВСЕ") {
            logs
        } else if (selectedTagFilter == "ERROR") {
            logs.filter {
                it.level == LogLevel.ERROR ||
                it.tag.contains("ERROR", ignoreCase = true) ||
                it.tag.contains("CRASH", ignoreCase = true) ||
                it.message.contains("[ERROR]", ignoreCase = true)
            }
        } else {
            logs.filter {
                it.tag.contains(selectedTagFilter, ignoreCase = true) ||
                it.message.contains("[$selectedTagFilter]", ignoreCase = true) ||
                it.message.contains(selectedTagFilter, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size, autoScrollEnabled) {
        if (autoScrollEnabled && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .zIndex(Float.MAX_VALUE)
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(top = 36.dp, bottom = 16.dp, start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.5.dp, Indigo500.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Indigo500),
                color = DarkBg.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // --- HEADER ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Debug Console",
                                tint = Emerald400,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DEBUG CONSOLE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(${filteredLogs.size}/${logs.size})",
                                color = Slate400,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Закрыть",
                                tint = Slate400
                            )
                        }
                    }

                    // --- FILTER TAGS ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            val isSelected = selectedTagFilter == tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) {
                                            when (tag) {
                                                "ERROR" -> Rose500.copy(alpha = 0.3f)
                                                "VOSK_STREAM" -> Emerald400.copy(alpha = 0.3f)
                                                "GEMINI" -> Indigo500.copy(alpha = 0.3f)
                                                else -> Slate700
                                            }
                                        } else Slate900
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) {
                                            when (tag) {
                                                "ERROR" -> Rose500
                                                "VOSK_STREAM" -> Emerald400
                                                "GEMINI" -> Indigo500
                                                else -> Emerald400
                                            }
                                        } else Slate800,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedTagFilter = tag }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) Color.White else Slate400
                                )
                            }
                        }
                    }

                    // --- TERMINAL LOGS LIST ---
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        if (filteredLogs.isEmpty()) {
                            Text(
                                text = "Логи отсутствуют...",
                                color = Slate400,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredLogs, key = { it.id }) { log ->
                                    LogItemRow(log = log)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- ACTION BUTTONS BOTTOM BAR ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Clear Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Rose500.copy(alpha = 0.15f))
                                    .border(1.dp, Rose500.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .clickable { GlobalConsoleLogger.clear() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Очистить",
                                        tint = Rose500,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Очистить",
                                        color = Rose500,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Copy Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Indigo500.copy(alpha = 0.15f))
                                    .border(1.dp, Indigo500.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        val textToCopy = filteredLogs.joinToString("\n") {
                                            "[${it.timestamp}] [${it.level}] [${it.tag}] ${it.message}"
                                        }
                                        clipboardManager.setText(AnnotatedString(textToCopy))
                                        Toast.makeText(context, "Логи скопированы (${filteredLogs.size})", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Скопировать",
                                        tint = Indigo500,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Копировать",
                                        color = Indigo500,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Auto-scroll toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (autoScrollEnabled) Emerald400.copy(alpha = 0.15f) else Slate800)
                                .border(
                                    1.dp,
                                    if (autoScrollEnabled) Emerald400.copy(alpha = 0.5f) else Slate700,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { autoScrollEnabled = !autoScrollEnabled }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (autoScrollEnabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Автоскролл",
                                    tint = if (autoScrollEnabled) Emerald400 else Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (autoScrollEnabled) "Пауза" else "Автоскролл",
                                    color = if (autoScrollEnabled) Emerald400 else Slate400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
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
private fun LogItemRow(log: LogEntry) {
    val levelColor = when (log.level) {
        LogLevel.ERROR -> Rose500
        LogLevel.WARN -> Color(0xFFF59E0B) // Amber
        LogLevel.INFO -> Emerald400
        LogLevel.DEBUG -> Indigo500
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = log.timestamp,
                color = Slate400,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor.copy(alpha = 0.2f))
                    .border(0.5.dp, levelColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = log.tag,
                    color = levelColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(
            text = log.message,
            color = if (log.level == LogLevel.ERROR) Rose500 else Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
        )
    }
}

@Composable
fun DebugConsoleFloatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Popup(
        alignment = Alignment.BottomEnd,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = modifier
                .zIndex(Float.MAX_VALUE)
                .padding(bottom = 80.dp, end = 16.dp)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    var totalDragDistance = 0f
                    detectDragGestures(
                        onDragStart = { totalDragDistance = 0f },
                        onDragEnd = {
                            if (totalDragDistance < 12f) {
                                onClick()
                            }
                        },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragDistance += dragAmount.getDistance()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    )
                }
                .size(48.dp)
                .shadow(10.dp, CircleShape, spotColor = Emerald400)
                .clip(CircleShape)
                .background(DarkBg)
                .border(1.5.dp, Emerald400, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Debug Console Trigger",
                tint = Emerald400,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

