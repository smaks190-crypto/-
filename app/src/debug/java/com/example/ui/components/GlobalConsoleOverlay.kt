package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.BuildConfig
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.utils.DatabaseDiagnosticsSnapshot
import com.example.utils.DbOperationEntry
import com.example.utils.GlobalConsoleLogger
import com.example.utils.LogEntry
import com.example.utils.LogLevel
import com.example.utils.NetworkCallStatus
import com.example.utils.NetworkTraceEntry
import com.example.utils.SystemDiagnosticsState
import kotlinx.coroutines.delay
import java.util.Locale

private val Cyan400 = Color(0xFF22D3EE)
private val ConsoleAmber = Color(0xFFFBBF24)
private val ConsoleFatalRed = Color(0xFFFF2E54)
private val ObsidianSurface = Color(0xFF0B0F1A)
private val ObsidianCardBg = Color(0xFF111726)
private val ObsidianTerminalBg = Color(0xFF070A12)

private enum class ConsoleTab(val title: String) {
    LOGS("LOGS"),
    ERRORS("ERRORS"),
    NETWORK_AI("NETWORK / AI"),
    DATABASE("DATABASE"),
    SYSTEM("SYSTEM")
}

@Composable
fun GlobalConsoleOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!BuildConfig.DEBUG) return

    val logs by GlobalConsoleLogger.logs.collectAsState()
    val errors by GlobalConsoleLogger.errors.collectAsState()
    val networkTraces by GlobalConsoleLogger.networkTraces.collectAsState()
    val dbOperations by GlobalConsoleLogger.dbOperations.collectAsState()
    val dbSnapshot by GlobalConsoleLogger.dbSnapshot.collectAsState()
    val systemState by GlobalConsoleLogger.systemState.collectAsState()
    val isPaused by GlobalConsoleLogger.isPaused.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(ConsoleTab.LOGS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }
    var selectedSourceFilter by remember { mutableStateOf("ALL") }
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val expandedLogIds = remember { mutableStateMapOf<Long, Boolean>() }
    val expandedErrorIds = remember { mutableStateMapOf<Long, Boolean>() }

    val logsListState = rememberLazyListState()

    LaunchedEffect(isVisible, selectedTab) {
        if (isVisible) {
            GlobalConsoleLogger.markErrorsAsRead()
        }
    }

    val filteredLogs = remember(logs, searchQuery, selectedLevelFilter, selectedSourceFilter) {
        GlobalConsoleLogger.filterLogs(
            entries = logs,
            query = searchQuery,
            levelFilter = selectedLevelFilter,
            sourceFilter = selectedSourceFilter
        )
    }

    LaunchedEffect(filteredLogs.size, autoScrollEnabled, isVisible, selectedTab) {
        if (isVisible && selectedTab == ConsoleTab.LOGS && autoScrollEnabled && filteredLogs.isNotEmpty() && !isPaused) {
            logsListState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    val availableSources = remember(logs) {
        val base = listOf("ALL", "UI", "GEMINI", "VOICE", "ROOM", "NETWORK", "DAVID", "LIFECYCLE", "CRASH")
        val dynamic = logs.map { it.source.uppercase(Locale.US) }.distinct()
        (base + dynamic).distinct()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(9999f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.84f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Emerald400.copy(alpha = 0.55f),
                                GlassBorder,
                                Indigo500.copy(alpha = 0.45f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ),
                shape = RoundedCornerShape(22.dp),
                color = ObsidianSurface,
                tonalElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // --- 1. OBSIDIAN NEO-BANK HEADER ---
                    ConsoleHeaderBar(
                        isPaused = isPaused,
                        autoScrollEnabled = autoScrollEnabled,
                        logsCount = logs.size,
                        errorsCount = errors.size,
                        onTogglePause = { GlobalConsoleLogger.setPaused(!isPaused) },
                        onToggleAutoScroll = { autoScrollEnabled = !autoScrollEnabled },
                        onDismiss = onDismiss
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 2. 5-TAB NAVIGATION BAR ---
                    ConsoleTabsRow(
                        selectedTab = selectedTab,
                        logsCount = logs.size,
                        errorsCount = errors.size,
                        networkCount = networkTraces.size,
                        dbCount = dbOperations.size,
                        onSelectTab = { tab ->
                            selectedTab = tab
                            if (tab == ConsoleTab.ERRORS) {
                                GlobalConsoleLogger.markErrorsAsRead()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 3. TAB CONTENT AREA ---
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        when (selectedTab) {
                            ConsoleTab.LOGS -> LogsTabContent(
                                logs = filteredLogs,
                                totalLogsCount = logs.size,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                selectedLevelFilter = selectedLevelFilter,
                                onSelectLevelFilter = { selectedLevelFilter = it },
                                availableSources = availableSources,
                                selectedSourceFilter = selectedSourceFilter,
                                onSelectSourceFilter = { selectedSourceFilter = it },
                                expandedLogIds = expandedLogIds,
                                listState = logsListState,
                                context = context
                            )

                            ConsoleTab.ERRORS -> ErrorsTabContent(
                                errors = errors,
                                expandedErrorIds = expandedErrorIds,
                                context = context
                            )

                            ConsoleTab.NETWORK_AI -> NetworkAiTabContent(
                                traces = networkTraces,
                                context = context
                            )

                            ConsoleTab.DATABASE -> DatabaseTabContent(
                                snapshot = dbSnapshot,
                                operations = dbOperations
                            )

                            ConsoleTab.SYSTEM -> SystemTabContent(
                                isVisible = isVisible && selectedTab == ConsoleTab.SYSTEM,
                                systemState = systemState
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // --- 4. BOTTOM DIAGNOSTIC EXPORT & CLEAR BAR ---
                    ConsoleBottomActionBar(
                        onCopyAiPrompt = {
                            val aiPrompt = GlobalConsoleLogger.buildAiDiagnosticPrompt()
                            copyTextToClipboard(
                                context = context,
                                label = "Obsidian Neo-Bank AI Diagnostic Prompt",
                                text = aiPrompt,
                                toastText = "🤖 Диагностический промпт для ИИ скопирован"
                            )
                        },
                        onCopyFullReport = {
                            val report = GlobalConsoleLogger.buildDiagnosticReport()
                            copyTextToClipboard(
                                context = context,
                                label = "Obsidian Neo-Bank Diagnostic Report",
                                text = report,
                                toastText = "Полный диагностический отчёт скопирован"
                            )
                        },
                        onShareReport = {
                            val report = GlobalConsoleLogger.buildDiagnosticReport()
                            shareDiagnosticReport(context, report)
                        },
                        onRequestClear = {
                            showClearConfirmDialog = true
                        }
                    )
                }
            }

            if (showClearConfirmDialog) {
                ClearLogsConfirmationDialog(
                    onConfirmClearAll = {
                        GlobalConsoleLogger.clear()
                        expandedLogIds.clear()
                        expandedErrorIds.clear()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Журнал диагностики очищен", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showClearConfirmDialog = false }
                )
            }
        }
    }
}

@Composable
private fun ConsoleHeaderBar(
    isPaused: Boolean,
    autoScrollEnabled: Boolean,
    logsCount: Int,
    errorsCount: Int,
    onTogglePause: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Emerald400.copy(alpha = 0.14f))
                    .border(1.dp, Emerald400.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Developer Console",
                    tint = Emerald400,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "OBSIDIAN NEO-BANK • DEBUG",
                        color = Emerald400,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isPaused) ConsoleAmber.copy(alpha = 0.18f)
                                else Emerald400.copy(alpha = 0.18f)
                            )
                            .border(
                                1.dp,
                                if (isPaused) ConsoleAmber.copy(alpha = 0.5f)
                                else Emerald400.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isPaused) "PAUSED" else "LIVE",
                            color = if (isPaused) ConsoleAmber else Emerald400,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Developer Console",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$logsCount записи • $errorsCount ошибок",
                        color = if (errorsCount > 0) Rose500 else Slate400,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause / Resume Live Stream button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isPaused) ConsoleAmber.copy(alpha = 0.2f) else ObsidianCardBg)
                    .border(
                        1.dp,
                        if (isPaused) ConsoleAmber else Slate800,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable(onClick = onTogglePause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Возобновить поток" else "Пауза потока",
                    tint = if (isPaused) ConsoleAmber else Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Auto-scroll toggle button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (autoScrollEnabled) Emerald400.copy(alpha = 0.18f) else ObsidianCardBg)
                    .border(
                        1.dp,
                        if (autoScrollEnabled) Emerald400.copy(alpha = 0.6f) else Slate800,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable(onClick = onToggleAutoScroll),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalAlignBottom,
                    contentDescription = "Автопрокрутка",
                    tint = if (autoScrollEnabled) Emerald400 else Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Close Console button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ObsidianCardBg)
                    .border(1.dp, Slate800, RoundedCornerShape(9.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть консоль",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ConsoleTabsRow(
    selectedTab: ConsoleTab,
    logsCount: Int,
    errorsCount: Int,
    networkCount: Int,
    dbCount: Int,
    onSelectTab: (ConsoleTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ConsoleTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            val badgeCount = when (tab) {
                ConsoleTab.LOGS -> logsCount
                ConsoleTab.ERRORS -> errorsCount
                ConsoleTab.NETWORK_AI -> networkCount
                ConsoleTab.DATABASE -> dbCount
                ConsoleTab.SYSTEM -> null
            }
            val activeAccent = when (tab) {
                ConsoleTab.ERRORS -> if (errorsCount > 0) Rose500 else Emerald400
                ConsoleTab.NETWORK_AI -> Indigo500
                ConsoleTab.DATABASE -> Cyan400
                else -> Emerald400
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) activeAccent.copy(alpha = 0.2f)
                        else ObsidianCardBg
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) activeAccent else Slate800,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tab.title,
                        color = if (isSelected) Color.White else Slate400,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (badgeCount != null) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (tab == ConsoleTab.ERRORS && errorsCount > 0) Rose500.copy(alpha = 0.3f)
                                    else Slate800
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badgeCount.toString(),
                                color = if (tab == ConsoleTab.ERRORS && errorsCount > 0) Rose500 else Slate400,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsTabContent(
    logs: List<LogEntry>,
    totalLogsCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedLevelFilter: LogLevel?,
    onSelectLevelFilter: (LogLevel?) -> Unit,
    availableSources: List<String>,
    selectedSourceFilter: String,
    onSelectSourceFilter: (String) -> Unit,
    expandedLogIds: MutableMap<Long, Boolean>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Slate800, RoundedCornerShape(11.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Поиск",
                tint = Slate400,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Поиск по тексту, тегу, OP-ID или стектрейсу...",
                        color = Slate400.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Emerald400),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Очистить поиск",
                    tint = Slate400,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { onSearchQueryChange("") }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Level Filter Row
        val levelOptions: List<Pair<String, LogLevel?>> = listOf(
            "ALL" to null,
            "DEBUG" to LogLevel.DEBUG,
            "INFO" to LogLevel.INFO,
            "WARNING" to LogLevel.WARNING,
            "ERROR" to LogLevel.ERROR,
            "FATAL" to LogLevel.FATAL
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            levelOptions.forEach { (label, level) ->
                val isSelected = selectedLevelFilter == level
                val chipColor = levelColor(level)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) chipColor.copy(alpha = 0.22f) else ObsidianCardBg)
                        .border(
                            1.dp,
                            if (isSelected) chipColor else Slate800,
                            RoundedCornerShape(7.dp)
                        )
                        .clickable { onSelectLevelFilter(level) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else Slate400,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Source Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            availableSources.forEach { source ->
                val isSelected = selectedSourceFilter == source
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) Indigo500.copy(alpha = 0.24f) else ObsidianCardBg)
                        .border(
                            1.dp,
                            if (isSelected) Indigo500 else Slate800,
                            RoundedCornerShape(7.dp)
                        )
                        .clickable { onSelectSourceFilter(source) }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = source,
                        color = if (isSelected) Color.White else Slate400,
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Terminal Log Stream Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(ObsidianTerminalBg)
                .border(1.dp, Slate800, RoundedCornerShape(13.dp))
                .padding(8.dp)
        ) {
            if (logs.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (totalLogsCount == 0) "Логи сессии отсутствуют" else "Нет записей по выбранному фильтру",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        val isExpanded = expandedLogIds[log.id] == true
                        LogEntryCard(
                            log = log,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedLogIds[log.id] = !isExpanded
                            },
                            onCopySingle = {
                                val singleText = buildString {
                                    append("${log.timestamp} [${log.level.canonical.label}] [${log.source}]")
                                    if (!log.operationId.isNullOrBlank()) append(" (${log.operationId})")
                                    append(": ${log.message}")
                                    if (!log.details.isNullOrBlank()) append("\nDetails: ${log.details}")
                                    if (!log.stackTrace.isNullOrBlank()) append("\nStackTrace:\n${log.stackTrace}")
                                }
                                copyTextToClipboard(
                                    context = context,
                                    label = "Log Entry",
                                    text = singleText,
                                    toastText = "Запись лога скопирована"
                                )
                            },
                            onCopyForAi = {
                                val prompt = GlobalConsoleLogger.buildAiDiagnosticPrompt(targetError = log)
                                copyTextToClipboard(
                                    context = context,
                                    label = "AI Diagnostic Prompt",
                                    text = prompt,
                                    toastText = "🤖 Промпт для ИИ по событию скопирован"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(
    log: LogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopySingle: () -> Unit,
    onCopyForAi: () -> Unit
) {
    val accent = levelColor(log.level.canonical)
    val hasExtra = !log.details.isNullOrBlank() || !log.stackTrace.isNullOrBlank() || !log.operationId.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (log.level.canonical) {
                    LogLevel.FATAL -> ConsoleFatalRed.copy(alpha = 0.14f)
                    LogLevel.ERROR -> Rose500.copy(alpha = 0.11f)
                    LogLevel.WARNING -> ConsoleAmber.copy(alpha = 0.08f)
                    else -> ObsidianCardBg.copy(alpha = 0.7f)
                }
            )
            .border(
                width = 1.dp,
                color = when (log.level.canonical) {
                    LogLevel.FATAL -> ConsoleFatalRed.copy(alpha = 0.5f)
                    LogLevel.ERROR -> Rose500.copy(alpha = 0.35f)
                    LogLevel.WARNING -> ConsoleAmber.copy(alpha = 0.25f)
                    else -> Slate800.copy(alpha = 0.7f)
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onToggleExpand() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = log.timestamp,
                    color = Slate400,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = log.level.canonical.label,
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "[${log.source}]",
                    color = sourceColor(log.source),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (!log.operationId.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "#${log.operationId}",
                        color = Slate400,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            if (hasExtra) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = log.message,
            color = if (log.level.canonical == LogLevel.ERROR || log.level.canonical == LogLevel.FATAL) {
                Color(0xFFFFD6DF)
            } else {
                Color(0xFFE2E8F0)
            },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                if (!log.details.isNullOrBlank()) {
                    Text(
                        text = "Детали: ${log.details}",
                        color = Cyan400,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (!log.stackTrace.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Slate800, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = log.stackTrace,
                            color = Color(0xFFFCA5A5),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallInlineButton(
                        text = "Копировать запись",
                        color = Slate400,
                        onClick = onCopySingle
                    )
                    if (log.level.canonical == LogLevel.ERROR || log.level.canonical == LogLevel.FATAL) {
                        SmallInlineButton(
                            text = "🤖 Скопировать для ИИ",
                            color = Emerald400,
                            onClick = onCopyForAi
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorsTabContent(
    errors: List<LogEntry>,
    expandedErrorIds: MutableMap<Long, Boolean>,
    context: Context
) {
    val reversedErrors = remember(errors) { errors.asReversed() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(13.dp))
            .background(ObsidianTerminalBg)
            .border(1.dp, Slate800, RoundedCornerShape(13.dp))
            .padding(8.dp)
    ) {
        if (reversedErrors.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = Emerald400.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Исключений и ошибок не обнаружено",
                        color = Emerald400,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Все перехваченные ошибки Room, Gemini и корутин появятся здесь",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reversedErrors, key = { it.id }) { err ->
                    val isExpanded = expandedErrorIds[err.id] ?: true
                    ErrorDiagnosticCard(
                        error = err,
                        isExpanded = isExpanded,
                        onToggleExpand = { expandedErrorIds[err.id] = !isExpanded },
                        onCopyError = {
                            val rawError = buildString {
                                appendLine("[${err.timestamp}] [${err.level.canonical.label}] [${err.source}] ${err.operationId ?: ""}")
                                appendLine("Message: ${err.message}")
                                if (!err.details.isNullOrBlank()) appendLine("Details: ${err.details}")
                                if (!err.stackTrace.isNullOrBlank()) {
                                    appendLine("StackTrace:")
                                    appendLine(err.stackTrace)
                                }
                            }
                            copyTextToClipboard(
                                context = context,
                                label = "Error StackTrace",
                                text = rawError,
                                toastText = "Ошибка и Stack Trace скопированы"
                            )
                        },
                        onCopyForAi = {
                            val prompt = GlobalConsoleLogger.buildAiDiagnosticPrompt(targetError = err)
                            copyTextToClipboard(
                                context = context,
                                label = "AI Error Prompt",
                                text = prompt,
                                toastText = "🤖 Структурированный отчёт для ИИ скопирован"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorDiagnosticCard(
    error: LogEntry,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopyError: () -> Unit,
    onCopyForAi: () -> Unit
) {
    val isFatal = error.level.canonical == LogLevel.FATAL
    val borderColor = if (isFatal) ConsoleFatalRed else Rose500

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(borderColor.copy(alpha = 0.1f))
            .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(borderColor.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (error.isPreviousSessionCrash) "CRASH • ПРОШЛАЯ СЕССИЯ" else error.level.canonical.label,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[${error.source}]",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = error.timestamp,
                    color = Slate400,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Развернуть",
                tint = Slate400,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = error.message,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp
        )

        if (!error.details.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error.details,
                color = Cyan400,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        if (isExpanded && !error.stackTrace.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = error.stackTrace,
                    color = Color(0xFFFECDD3),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(ObsidianCardBg)
                    .border(1.dp, Slate700, RoundedCornerShape(9.dp))
                    .clickable(onClick = onCopyError)
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Скопировать ошибку",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Emerald400.copy(alpha = 0.18f))
                    .border(1.dp, Emerald400.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                    .clickable(onClick = onCopyForAi)
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Скопировать для ИИ",
                        color = Emerald400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkAiTabContent(
    traces: List<NetworkTraceEntry>,
    context: Context
) {
    val reversedTraces = remember(traces) { traces.asReversed() }
    val successCount = remember(traces) { traces.count { it.status == NetworkCallStatus.SUCCESS } }
    val fallbackCount = remember(traces) { traces.count { it.status == NetworkCallStatus.FALLBACK } }
    val errorCount = remember(traces) { traces.count { it.status == NetworkCallStatus.ERROR } }
    val avgDurationMs = remember(traces) {
        if (traces.isEmpty()) 0L else traces.map { it.durationMs }.average().toLong()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // KPI Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MetricMiniBento(
                title = "ЗАПРОСЫ",
                value = traces.size.toString(),
                accent = Indigo500,
                modifier = Modifier.weight(1f)
            )
            MetricMiniBento(
                title = "SUCCESS",
                value = successCount.toString(),
                accent = Emerald400,
                modifier = Modifier.weight(1f)
            )
            MetricMiniBento(
                title = "FALLBACK",
                value = fallbackCount.toString(),
                accent = ConsoleAmber,
                modifier = Modifier.weight(1f)
            )
            MetricMiniBento(
                title = "AVG МС",
                value = "${avgDurationMs}ms",
                accent = if (errorCount > 0) Rose500 else Cyan400,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(ObsidianTerminalBg)
                .border(1.dp, Slate800, RoundedCornerShape(13.dp))
                .padding(8.dp)
        ) {
            if (reversedTraces.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = Indigo500.copy(alpha = 0.6f),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Вызовы Gemini API ещё не выполнялись",
                        color = Slate400,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Запустите голосовой ввод, аудит бюджета или добавьте операцию",
                        color = Slate400.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(reversedTraces, key = { it.id }) { trace ->
                        val statusColor = when (trace.status) {
                            NetworkCallStatus.SUCCESS -> Emerald400
                            NetworkCallStatus.FALLBACK -> ConsoleAmber
                            NetworkCallStatus.ERROR -> Rose500
                        }
                        val latencyColor = when {
                            trace.durationMs < 1500L -> Emerald400
                            trace.durationMs < 4000L -> ConsoleAmber
                            else -> Rose500
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianCardBg)
                                .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable {
                                    val summary = "[${trace.timestamp}] (${trace.operationId}) ${trace.operationType} | model=${trace.modelName} | ${trace.durationMs}ms | ${trace.status.label} ${trace.httpCode ?: ""} ${trace.errorMessage ?: ""}"
                                    copyTextToClipboard(context, "Network Trace", summary, "Сетевая метрика скопирована")
                                }
                                .padding(9.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(statusColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = trace.status.label,
                                            color = statusColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = trace.operationType,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "${trace.durationMs} ms",
                                    color = latencyColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Модель: ${trace.modelName} • Попытка ${trace.attemptNumber}/${trace.totalModelsInChain}",
                                    color = Cyan400,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${trace.timestamp} • #${trace.operationId}",
                                    color = Slate400,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (trace.httpCode != null || !trace.errorType.isNullOrBlank() || !trace.errorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = listOfNotNull(
                                        trace.httpCode?.let { "HTTP $it" },
                                        trace.errorType,
                                        trace.errorMessage
                                    ).joinToString(" • "),
                                    color = Rose500,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (!trace.details.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = trace.details,
                                    color = Slate400,
                                    fontSize = 9.5.sp,
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
private fun DatabaseTabContent(
    snapshot: DatabaseDiagnosticsSnapshot,
    operations: List<DbOperationEntry>
) {
    val reversedOps = remember(operations) { operations.asReversed() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Room Status Overview Bento
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Cyan400.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = Cyan400,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ROOM DATABASE • ${snapshot.databaseName}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (snapshot.isInitialized) Emerald400.copy(alpha = 0.2f)
                            else ConsoleAmber.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (snapshot.isInitialized) "SCHEMA v${snapshot.schemaVersion} • READY" else "INIT",
                        color = if (snapshot.isInitialized) Emerald400 else ConsoleAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Активный профиль бюджета: ${snapshot.activeBudgetId} • Обновлено: ${snapshot.lastUpdatedTimestamp}",
                color = Slate400,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            if (!snapshot.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последняя ошибка Room: ${snapshot.lastError}",
                    color = Rose500,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Table Row Counts Bento Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "КОЛИЧЕСТВО ЗАПИСЕЙ В ТАБЛИЦАХ (READ-ONLY)",
                color = Emerald400,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            val tables = listOf(
                "transactions" to (snapshot.tableCounts["transactions"] ?: 0),
                "accounts" to (snapshot.tableCounts["accounts"] ?: 0),
                "categories" to (snapshot.tableCounts["categories"] ?: 0),
                "goals" to (snapshot.tableCounts["goals"] ?: 0),
                "budget_profiles" to (snapshot.tableCounts["budget_profiles"] ?: 0),
                "notifications" to (snapshot.tableCounts["notifications"] ?: 0)
            )

            tables.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowItems.forEach { (tableName, count) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianTerminalBg)
                                .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tableName,
                                color = Slate400,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = count.toString(),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Recent DB Operations Log
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianTerminalBg)
                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "ПОСЛЕДНИЕ ОПЕРАЦИИ ROOM DB (${reversedOps.size})",
                color = Cyan400,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (reversedOps.isEmpty()) {
                Text(
                    text = "Записей об операциях чтения/записи БД пока нет",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                reversedOps.take(30).forEach { op ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianCardBg)
                            .border(
                                1.dp,
                                if (op.isSuccess) Slate800 else Rose500.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = op.operationName,
                                    color = if (op.isSuccess) Color.White else Rose500,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[${op.tableName}]",
                                    color = Cyan400,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (!op.details.isNullOrBlank()) {
                                Text(
                                    text = op.details,
                                    color = Slate400,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (!op.errorMessage.isNullOrBlank()) {
                                Text(
                                    text = op.errorMessage,
                                    color = Rose500,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${op.durationMs} ms",
                                color = if (op.durationMs < 100L) Emerald400 else ConsoleAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = op.timestamp,
                                color = Slate400,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemTabContent(
    isVisible: Boolean,
    systemState: SystemDiagnosticsState
) {
    var usedMemMb by remember { mutableLongStateOf(0L) }
    var maxMemMb by remember { mutableLongStateOf(1L) }
    var totalAllocMb by remember { mutableLongStateOf(0L) }
    var uptimeText by remember { mutableStateOf(GlobalConsoleLogger.formatUptime()) }

    // Poll dynamic JVM Heap & Uptime only while SYSTEM tab is open
    LaunchedEffect(isVisible) {
        while (isVisible) {
            val runtime = Runtime.getRuntime()
            val total = runtime.totalMemory()
            val free = runtime.freeMemory()
            val max = runtime.maxMemory().coerceAtLeast(1L)
            usedMemMb = (total - free) / (1024 * 1024)
            totalAllocMb = total / (1024 * 1024)
            maxMemMb = max / (1024 * 1024)
            uptimeText = GlobalConsoleLogger.formatUptime()
            delay(2000L)
        }
    }

    val memRatio = (usedMemMb.toFloat() / maxMemMb.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Build & Version Bento
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Emerald400.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "СБОРКА И ВЕРСИЯ ПРИЛОЖЕНИЯ",
                color = Emerald400,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            SystemKeyValueRow("Application ID", BuildConfig.APPLICATION_ID)
            SystemKeyValueRow("Version Name", "v${BuildConfig.VERSION_NAME}")
            SystemKeyValueRow("Version Code", BuildConfig.VERSION_CODE.toString())
            SystemKeyValueRow("Build Type", "${BuildConfig.BUILD_TYPE.uppercase(Locale.US)} (Debug=${BuildConfig.DEBUG})")
            SystemKeyValueRow("Время работы (Uptime)", uptimeText)
        }

        // 2. JVM Memory Heap Bento
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Indigo500.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = Indigo500,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ПАМЯТЬ JVM HEAP",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "$usedMemMb МБ / $maxMemMb МБ (${(memRatio * 100).toInt()}%)",
                    color = if (memRatio > 0.85f) Rose500 else Emerald400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Slate800)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(memRatio)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Emerald400, Indigo500, if (memRatio > 0.8f) Rose500 else Cyan400)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            SystemKeyValueRow("Выделено JVM (Allocated)", "$totalAllocMb МБ")
            SystemKeyValueRow("Свободно до лимита Heap", "${(maxMemMb - usedMemMb).coerceAtLeast(0L)} МБ")
        }

        // 3. Active Background & Lifecycle State Bento
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "ЖИЗНЕННЫЙ ЦИКЛ И ФОНОВЫЕ ЗАДАЧИ",
                color = Cyan400,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            SystemKeyValueRow("Activity Lifecycle", systemState.lifecycleState)
            SystemKeyValueRow("Активное модальное окно", systemState.activeModalName)
            SystemKeyValueRow("Активный ID бюджета", systemState.activeBudgetId)
            SystemKeyValueRow("Запись микрофона (Voice)", if (systemState.isVoiceRecording) "ACTIVE (запись)" else "IDLE")
            SystemKeyValueRow("ИИ-разбор речи (Gemini Live)", if (systemState.isAnalyzingVoice) "ANALYZING" else "IDLE")
            SystemKeyValueRow("ИИ-аудит бюджета (Stream)", if (systemState.isAiAuditRunning) "STREAMING" else "IDLE")
            SystemKeyValueRow("Генерация комментария Давида", if (systemState.isGeneratingDavidReaction) "GENERATING" else "IDLE")
        }

        // 4. Device & Android OS Info Bento
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Text(
                text = "УСТРОЙСТВО И ANDROID OS",
                color = Slate400,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))
            SystemKeyValueRow("Производитель / Бренд", "${Build.MANUFACTURER} (${Build.BRAND})")
            SystemKeyValueRow("Модель устройства", "${Build.MODEL} (${Build.DEVICE})")
            SystemKeyValueRow("Версия Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            SystemKeyValueRow("Архитектура CPU (ABI)", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        }
    }
}

@Composable
private fun SystemKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Slate400,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MetricMiniBento(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianCardBg)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Slate400,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ConsoleBottomActionBar(
    onCopyAiPrompt: () -> Unit,
    onCopyFullReport: () -> Unit,
    onShareReport: () -> Unit,
    onRequestClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Copy for AI button (Primary Emerald Accent)
        Box(
            modifier = Modifier
                .weight(1.25f)
                .clip(RoundedCornerShape(11.dp))
                .background(Emerald400.copy(alpha = 0.18f))
                .border(1.dp, Emerald400.copy(alpha = 0.65f), RoundedCornerShape(11.dp))
                .clickable(onClick = onCopyAiPrompt)
                .padding(vertical = 9.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Скопировать для ИИ",
                    tint = Emerald400,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Для ИИ",
                    color = Emerald400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        // 2. Copy Full Report
        Box(
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(11.dp))
                .background(Indigo500.copy(alpha = 0.18f))
                .border(1.dp, Indigo500.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                .clickable(onClick = onCopyFullReport)
                .padding(vertical = 9.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Скопировать отчёт",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Отчёт",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        // 3. Share Report via Android Sharesheet
        Box(
            modifier = Modifier
                .weight(1.1f)
                .clip(RoundedCornerShape(11.dp))
                .background(ObsidianCardBg)
                .border(1.dp, Cyan400.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                .clickable(onClick = onShareReport)
                .padding(vertical = 9.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Поделиться отчётом",
                    tint = Cyan400,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Share",
                    color = Cyan400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        // 4. Clear Logs (with confirmation)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(Rose500.copy(alpha = 0.15f))
                .border(1.dp, Rose500.copy(alpha = 0.5f), RoundedCornerShape(11.dp))
                .clickable(onClick = onRequestClear)
                .padding(vertical = 9.dp, horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Очистить логи",
                tint = Rose500,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ClearLogsConfirmationDialog(
    onConfirmClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianSurface)
                .border(1.dp, Rose500.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Text(
                text = "ПОДТВЕРЖДЕНИЕ ОЧИСТКИ",
                color = Rose500,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Очистить все журналы Developer Console?",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Будут удалены текущие логи, перехваченные ошибки, сетевые метрики и сохранённый краш прошлой сессии. Финансовые данные пользователя в Room не затрагиваются.",
                color = Slate400,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianCardBg)
                        .border(1.dp, Slate700, RoundedCornerShape(10.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Отмена",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Rose500.copy(alpha = 0.22f))
                        .border(1.dp, Rose500, RoundedCornerShape(10.dp))
                        .clickable(onClick = onConfirmClearAll)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Очистить",
                        color = Rose500,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallInlineButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun levelColor(level: LogLevel?): Color = when (level?.canonical) {
    LogLevel.DEBUG -> Slate400
    LogLevel.INFO -> Emerald400
    LogLevel.WARNING, LogLevel.WARN -> ConsoleAmber
    LogLevel.ERROR -> Rose500
    LogLevel.FATAL -> ConsoleFatalRed
    null -> Emerald400
}

private fun sourceColor(source: String): Color = when (source.uppercase(Locale.US)) {
    "GEMINI", "AI" -> Indigo500
    "VOICE", "VOICE_LIVE" -> Emerald400
    "ROOM", "DB" -> Cyan400
    "ERROR", "CRASH" -> Rose500
    "NETWORK" -> ConsoleAmber
    else -> Color(0xFF38BDF8)
}

private fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
    toastText: String
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
}

private fun shareDiagnosticReport(context: Context, reportText: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Obsidian Neo-Bank — Debug Diagnostic Report")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        val chooser = Intent.createChooser(sendIntent, "Поделиться диагностическим отчётом").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось открыть меню отправки: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Компактная плавающая кнопка быстрого вызова Developer Console (только для Debug APK).
 * Отображает индикатор непрочитанных ошибок и соблюдает отступы экрана.
 */
@Composable
fun DebugConsoleFloatingButton(
    onClick: () -> Unit
) {
    if (!BuildConfig.DEBUG) return

    val unreadErrors by GlobalConsoleLogger.unreadErrorCount.collectAsState()
    val hasErrors = unreadErrors > 0

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (hasErrors) Rose500.copy(alpha = 0.24f)
                else DarkBg.copy(alpha = 0.88f)
            )
            .border(
                width = 1.2.dp,
                color = if (hasErrors) Rose500 else Emerald400.copy(alpha = 0.65f),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = "Открыть Developer Console",
            tint = if (hasErrors) Rose500 else Emerald400,
            modifier = Modifier.size(20.dp)
        )

        if (hasErrors) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .clip(CircleShape)
                    .background(Rose500)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadErrors > 9) "9+" else unreadErrors.toString(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
