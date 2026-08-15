package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AccountEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionEntity
import com.example.ui.components.charts.renderChartMessage
import com.example.ui.viewmodel.PeriodType
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
fun ReportDetailsDialog(
    periodTitle: String,
    auditText: String,
    income: Double? = null,
    expense: Double? = null,
    prevIncome: Double? = null,
    prevExpense: Double? = null,
    isLoading: Boolean = false,
    isGeneratingReaction: Boolean = false,
    auditTimestamp: Long? = null,
    profileName: String = "Вы",
    notifications: List<NotificationEntity> = emptyList(),
    transactions: List<TransactionEntity> = emptyList(),
    categories: List<CategoryEntity> = emptyList(),
    accounts: List<AccountEntity> = emptyList(),
    goals: List<GoalEntity> = emptyList(),
    initialTab: Int = 0,
    onRequestAudit: () -> Any = {},
    onRequestAuditForPeriod: (PeriodType) -> Unit = {},
    onSendCustomMessage: (String) -> Unit = {},
    onClearChat: () -> Unit = {},
    onDeleteNotification: (Long) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }

    var dots by remember { mutableStateOf("") }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (true) {
                dots = ""
                delay(350)
                dots = "."
                delay(350)
                dots = ".."
                delay(350)
                dots = "..."
                delay(350)
            }
        } else {
            dots = ""
        }
    }

    LaunchedEffect(Unit) {
        onMarkAllRead()
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showPeriodCarousel by remember { mutableStateOf(true) }
    var userMessageInput by remember { mutableStateOf("") }

    val initialUnreadIds = remember { notifications.filter { !it.isRead }.map { it.id }.toSet() }

    // Parse items for the unified feed
    val chatItems = remember(notifications, isLoading, isGeneratingReaction) {
        val items = mutableListOf<ChatItem>()

        val filteredNotifications = notifications.filterNot {
            it.title == "Жабов Давид" && (
                it.description.contains("персональный фин-аналитик") ||
                it.description.contains("Я Жабов Давид")
            )
        }

        for (notif in filteredNotifications) {
            when {
                notif.description.startsWith("||audit_req|") -> {
                    val raw = notif.description.removePrefix("||audit_req|")
                    val parts = raw.split("||", limit = 2)
                    val fName = parts.getOrNull(0)?.ifBlank { "Отчет_за_период.pdf" } ?: "Отчет_за_период.pdf"
                    val reqText = parts.getOrNull(1)?.ifBlank { "Давид, проведи аудит" } ?: "Давид, проведи аудит"
                    items.add(ChatAuditRequestItem(notif.timestamp, text = reqText, fileName = fName))
                }
                notif.description.startsWith("||audit_req||") -> {
                    val reqText = notif.description.removePrefix("||audit_req||")
                    items.add(ChatAuditRequestItem(notif.timestamp, text = reqText, fileName = "Отчет_за_период.pdf"))
                }
                notif.description.startsWith("||audit_block||") -> {
                    val blockText = notif.description.removePrefix("||audit_block||")
                    val isFirst = notif.title.contains("Главный Вердикт") || notif.title.contains("Аналитика")
                    items.add(ChatAuditBlockItem(notif.timestamp, text = blockText, isFirst = isFirst))
                }
                notif.description.startsWith("||user_msg||") -> {
                    val text = notif.description.removePrefix("||user_msg||")
                    items.add(ChatUserCustomMessageItem(notif.timestamp, text))
                }
                notif.description.startsWith("||david_msg||") -> {
                    val text = notif.description.removePrefix("||david_msg||")
                    items.add(ChatDavidCustomMessageItem(notif.timestamp, text))
                }
                else -> {
                    val (ops, _, _) = extractOpsAndComment(notif)
                    if (ops.isNotEmpty()) {
                        items.add(ChatNotificationUserItem(notif))
                    }
                    items.add(ChatNotificationDavidItem(notif))
                }
            }
        }

        if (isLoading) {
            items.add(ChatTypingItem(System.currentTimeMillis(), "audit"))
        } else if (isGeneratingReaction) {
            items.add(ChatTypingItem(System.currentTimeMillis(), "reaction"))
        }

        val sorted = items.sortedBy { it.timestamp }.toMutableList()
        val unreadIdsSet = initialUnreadIds.toMutableSet()

        val firstUnreadNotifIndex = sorted.indexOfFirst { item ->
            !item.isFromUser && (
                (item is ChatNotificationDavidItem && (unreadIdsSet.contains(item.notification.id) || !item.notification.isRead)) ||
                !item.isRead
            )
        }

        if (firstUnreadNotifIndex != -1) {
            val unreadItem = sorted[firstUnreadNotifIndex]
            sorted.add(firstUnreadNotifIndex, ChatUnreadSeparatorItem(unreadItem.timestamp - 1))
        }

        sorted
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatItems.size, isLoading) {
        if (chatItems.isNotEmpty()) {
            delay(50)
            listState.animateScrollToItem(chatItems.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF070A0E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A0E))
        ) {
            // --- TOP APP BAR / HEADER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    listOf(Emerald400, Indigo500, Rose500)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🐸", fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Давид Жабов",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isLoading) Emerald400 else Emerald400.copy(alpha = 0.85f))
                            )
                        }
                        Text(
                            text = if (isLoading) "Давид Жабов печатает$dots" else "В сети",
                            color = if (isLoading) Emerald400 else Indigo500.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Очистить чат",
                            tint = Slate400
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate800)
            )

            // --- CHAT MESSAGE FEED ---
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Static Welcoming messages if feed is empty or initial
                if (chatItems.isEmpty()) {
                    item {
                        RenderInitialWelcomeBubble()
                    }
                }

                itemsIndexed(
                    items = chatItems,
                    key = { idx, item ->
                        when (item) {
                            is ChatWelcomeItem -> "welcome_${item.timestamp}_$idx"
                            is ChatChangelogItem -> "changelog_${item.timestamp}_$idx"
                            is ChatAuditOfferItem -> "offer_${item.timestamp}_$idx"
                            is ChatUnreadSeparatorItem -> "unread_sep_${item.timestamp}_$idx"
                            is ChatNotificationUserItem -> "notif_user_${item.notification.id}_$idx"
                            is ChatNotificationDavidItem -> "notif_david_${item.notification.id}_$idx"
                            is ChatAuditRequestItem -> "req_${item.timestamp}_$idx"
                            is ChatAuditSystemItem -> "sys_${item.timestamp}_$idx"
                            is ChatAuditBlockItem -> "block_${item.timestamp}_${item.text.hashCode()}_$idx"
                            is ChatAuditRetryItem -> "retry_${item.timestamp}_$idx"
                            is ChatTypingItem -> "typing_${item.type}_$idx"
                            is ChatConnectingItem -> "connecting_${item.timestamp}_$idx"
                            is ChatUserCustomMessageItem -> "user_custom_${item.timestamp}_$idx"
                            is ChatDavidCustomMessageItem -> "david_custom_${item.timestamp}_$idx"
                        }
                    }
                ) { _, item ->
                    when (item) {
                        is ChatUnreadSeparatorItem -> ChatUnreadSeparator()
                        is ChatWelcomeItem -> RenderWelcomeItem(item, profileName, periodTitle)
                        is ChatChangelogItem -> RenderChangelogItem(item)
                        is ChatAuditOfferItem -> RenderAuditOfferItem(item, periodTitle)
                        is ChatNotificationUserItem -> ChatNotificationUser(item.notification, profileName)
                        is ChatNotificationDavidItem -> ChatNotificationDavid(item.notification)
                        is ChatAuditRequestItem -> RenderAuditRequestItem(item)
                        is ChatAuditSystemItem -> RenderAuditSystemItem(item)
                        is ChatTypingItem -> RenderModernTypingIndicator()
                        is ChatAuditBlockItem -> RenderAuditBlockItem(item, transactions)
                        is ChatUserCustomMessageItem -> RenderUserCustomBubble(item.text, item.timestamp)
                        is ChatDavidCustomMessageItem -> RenderDavidCustomBubble(item.text, item.timestamp)
                        is ChatAuditRetryItem -> RenderAuditRetryItem(
                            item = item,
                            onRequestAudit = { onRequestAudit() }
                        )
                        is ChatConnectingItem -> ChatConnectingIndicator(item.isRestored)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate800)
            )

            // --- BOTTOM CONTROLS & CAROUSEL ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Period Selection Carousel
                AnimatedVisibility(
                    visible = showPeriodCarousel,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuickPeriodChip(
                            icon = "📄",
                            label = "Отчет за день",
                            fileName = "Отчет_за_день.pdf",
                            onClick = {
                                onRequestAuditForPeriod(PeriodType.DAY)
                            }
                        )

                        QuickPeriodChip(
                            icon = "📄",
                            label = "Отчет за неделю",
                            fileName = "Отчет_за_неделю.pdf",
                            onClick = {
                                onRequestAuditForPeriod(PeriodType.WEEK)
                            }
                        )

                        QuickPeriodChip(
                            icon = "📄",
                            label = "Отчет за месяц",
                            fileName = "Отчет_за_месяц.pdf",
                            onClick = {
                                onRequestAuditForPeriod(PeriodType.MONTH)
                            }
                        )

                        QuickPeriodChip(
                            icon = "📄",
                            label = "Отчет за год",
                            fileName = "Отчет_за_год.pdf",
                            onClick = {
                                onRequestAuditForPeriod(PeriodType.ALL)
                            }
                        )
                    }
                }

                // Text Input Bar with Sparkle CTA & Send Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (userMessageInput.isNotBlank()) Indigo500 else Slate700)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🐸", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = userMessageInput,
                                onValueChange = { userMessageInput = it },
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(Emerald400),
                                decorationBox = { innerTextField ->
                                    if (userMessageInput.isEmpty()) {
                                        Text(
                                            text = "Спросить Давида или дать команду...",
                                            color = Slate400,
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    // Send Button
                    val canSend = userMessageInput.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(Indigo500, Emerald400))
                                else Brush.linearGradient(listOf(Indigo500.copy(alpha = 0.5f), Slate700))
                            )
                            .clickable(enabled = canSend && !isLoading) {
                                if (canSend) {
                                    val msg = userMessageInput
                                    userMessageInput = ""
                                    onSendCustomMessage(msg)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Отправить",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing chat
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text("Очистить чат с Давидом?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Вся история сообщений и отчетов будет удалена из базы данных.",
                    color = Slate300,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearChat()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Очистить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Отмена", color = Slate400)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun QuickPeriodChip(
    icon: String,
    label: String,
    fileName: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.5f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 14.sp)
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RenderInitialWelcomeBubble() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Welcome 1
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = Color(0xFF182533),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Давид Жабов 🐸",
                    color = Emerald400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Привет! 🐸",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        // Welcome 2
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = Color(0xFF182533),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Давид Жабов 🐸",
                    color = Emerald400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Я Давид Жабов, твой аналитический ассистент. Нажми кнопку ниже, чтобы отправить отчет — я разберу доходы, структуру расходов и построю графики! Мяу! 🐸🐾",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun RenderUserCustomBubble(text: String, timestamp: Long) {
    val timeStr = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            color = Color(0xFF2B5278),
            border = BorderStroke(1.dp, Indigo500.copy(alpha = 0.6f)),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Доставлено",
                        tint = Emerald400,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderDavidCustomBubble(text: String, timestamp: Long) {
    val timeStr = remember(timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = Color(0xFF182533),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Давид Жабов 🐸",
                    color = Emerald400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                MarkdownFormattedText(
                    markdownText = text,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = timeStr,
                        color = Slate400,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderModernTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            color = Color(0xFF182533),
            border = BorderStroke(1.dp, Slate700)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🐸", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald400.copy(alpha = dot1Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald400.copy(alpha = dot2Alpha))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald400.copy(alpha = dot3Alpha))
                )
            }
        }
    }
}

@Composable
private fun RenderAuditRequestItem(item: ChatAuditRequestItem) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val fileName = item.fileName ?: "Отчет_за_период.pdf"
    val reqText = item.text.ifBlank { "Давид, сделай отчет" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            color = Color(0xFF2B5278),
            border = BorderStroke(1.dp, if (item.hasError) Rose500 else Indigo500.copy(alpha = 0.7f)),
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                // PDF Attachment Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Emerald400.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Rose500.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📄", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PDF документ аналитики",
                                color = Emerald400,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = reqText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (item.hasError) {
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "Ошибка",
                            tint = Rose500,
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Доставлено",
                            tint = Emerald400,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderAuditBlockItem(
    item: ChatAuditBlockItem,
    allTransactions: List<TransactionEntity> = emptyList()
) {
    if (item.text.isNotBlank()) {
        val timeStr = remember(item.timestamp) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        }
        val chartData = remember(item.text) {
            parseChartDataFromText(item.text)
        }
        val cleanText = remember(item.text) {
            cleanChartTagsFromText(item.text)
        }

        val categoryBreakdown = remember(allTransactions) {
            val expenses = allTransactions.filter { it.type == "expense" }
            expenses.groupBy { it.category }
                .mapValues { it.value.sumOf { tx -> tx.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
        }

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(durationMillis = 350)
            ) + fadeIn(animationSpec = tween(durationMillis = 350))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
                    color = Color(0xFF182533),
                    border = BorderStroke(1.dp, Slate700),
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (item.isFirst) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Давид Жабов 🐸 (Аналитика)",
                                    color = Emerald400,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (cleanText.isNotBlank()) {
                            MarkdownFormattedText(
                                markdownText = cleanText,
                                fontSize = 13.sp
                            )
                        }

                        // Embedded Dynamic Line/Bar Chart if present
                        if (chartData != null && chartData.points.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            renderChartMessage(
                                dataPoints = chartData.points,
                                labels = chartData.labels,
                                title = chartData.title,
                                totalAmount = chartData.total
                            )
                        }

                        // Embedded Category Breakdown Cards if it's the final verdict block
                        if (item.isFirst && categoryBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            ChatCategoryBreakdownCard(categoryBreakdown)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = timeStr,
                                color = Slate400,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatCategoryBreakdownCard(categories: List<Pair<String, Double>>) {
    val total = remember(categories) { categories.sumOf { it.second }.coerceAtLeast(1.0) }
    val colors = listOf(Emerald400, Indigo500, Rose500, Color(0xFFFBBF24), Color(0xFF38BDF8))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "📊 Топ расходов по категориям:",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            categories.forEachIndexed { idx, (cat, amount) ->
                val pct = ((amount / total) * 100).toInt()
                val color = colors[idx % colors.size]

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(cat, color = Slate300, fontSize = 11.sp)
                        Text(
                            text = "${amount.toInt()} ₽ ($pct%)",
                            color = color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Slate800)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((amount / total).toFloat().coerceIn(0.02f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderWelcomeItem(item: ChatWelcomeItem, profileName: String, periodTitle: String) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
        color = Color(0xFF182533),
        border = BorderStroke(1.dp, Slate700),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Давид Жабов 🐸",
                color = Emerald400,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Привет, $profileName! Я готов провести полный аудит твоего бюджета за $periodTitle. Мяу! 🐸🐾",
                color = Color.White,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(text = timeStr, color = Slate400, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RenderChangelogItem(item: ChatChangelogItem) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
        color = Color(0xFF182533),
        border = BorderStroke(1.dp, Slate700),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Система ⚡",
                color = Indigo400,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            MarkdownFormattedText(
                markdownText = "### 🚀 Чат с Давидом Жабовым обновлен\n- Мгновенные отчеты с интерактивными графиками.\n- Задавай любые вопросы по своим тратам и доходам напрямую.",
                fontSize = 12.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(text = timeStr, color = Slate400, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RenderAuditOfferItem(item: ChatAuditOfferItem, periodTitle: String) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
        color = Color(0xFF182533),
        border = BorderStroke(1.dp, Slate700),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Давид Жабов 🐸",
                color = Emerald400,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Проанализировать твой бюджет за $periodTitle?",
                color = Color.White,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(text = timeStr, color = Slate400, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RenderAuditSystemItem(item: ChatAuditSystemItem) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Slate800.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, Slate700)
        ) {
            Text(
                text = "Запрос на аудит бюджета отправлен • $timeStr",
                color = Slate400,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun RenderAuditRetryItem(item: ChatAuditRetryItem, onRequestAudit: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
        color = Rose500.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "⚠️ Не удалось сгенерировать отчет. Проверьте интернет или API-ключ Gemini.",
                color = Rose400,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRequestAudit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Rose500,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Повторить", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Попробовать снова", fontSize = 12.sp)
            }
        }
    }
}

data class ParsedChartMessageData(
    val points: List<Double>,
    val labels: List<String> = emptyList(),
    val title: String = "Динамика трат",
    val total: Double? = null
)

fun parseChartDataFromText(text: String): ParsedChartMessageData? {
    val regex = Regex("""\|\|chart:(.*?)\|\|""", RegexOption.DOT_MATCHES_ALL)
    val match = regex.find(text)
    if (match != null) {
        val raw = match.groupValues[1].trim()
        val parts = raw.split("|")
        var title = "Динамика трат"
        var points = listOf<Double>()
        var labels = listOf<String>()
        var total: Double? = null

        for (part in parts) {
            val trimmed = part.trim()
            when {
                trimmed.startsWith("title=") -> title = trimmed.substringAfter("title=")
                trimmed.startsWith("labels=") -> labels = trimmed.substringAfter("labels=").split(",").map { it.trim() }
                trimmed.startsWith("total=") -> total = trimmed.substringAfter("total=").toDoubleOrNull()
                trimmed.startsWith("data=") -> points = trimmed.substringAfter("data=").split(",").mapNotNull { it.trim().toDoubleOrNull() }
                else -> {
                    if (points.isEmpty()) {
                        points = trimmed.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                    }
                }
            }
        }
        if (points.isNotEmpty()) {
            return ParsedChartMessageData(points, labels, title, total)
        }
    }

    if (text.contains("динамика трат", ignoreCase = true) || text.contains("график трат", ignoreCase = true) || text.contains("расходы по дням", ignoreCase = true)) {
        val numberRegex = Regex("""(\d+[\d\s]*[.,]?\d*)\s*(?:₽|руб|rub)""", RegexOption.IGNORE_CASE)
        val extractedNums = numberRegex.findAll(text).mapNotNull {
            it.groupValues[1].replace(" ", "").replace(",", ".").toDoubleOrNull()
        }.toList()
        if (extractedNums.size >= 3) {
            return ParsedChartMessageData(
                points = extractedNums,
                title = "Динамика трат"
            )
        }
    }

    return null
}

fun cleanChartTagsFromText(text: String): String {
    return text.replace(Regex("""\|\|chart:(.*?)\|\|""", RegexOption.DOT_MATCHES_ALL), "").trim()
}
