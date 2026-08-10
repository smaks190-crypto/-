package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.NotificationEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

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
    profileName: String = "Максим",
    notifications: List<NotificationEntity> = emptyList(),
    initialTab: Int = 0,
    onRequestAudit: () -> Any = {},
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
                delay(400)
                dots = "."
                delay(400)
                dots = ".."
                delay(400)
                dots = "..."
                delay(400)
            }
        } else {
            dots = ""
        }
    }

    val initialUnreadIds = remember { notifications.filter { !it.isRead }.map { it.id }.toSet() }
    
    LaunchedEffect(Unit) {
        onMarkAllRead()
    }
    var requestTimestamp by remember { mutableStateOf<Long?>(null) }

    var hadNoConnection by remember { mutableStateOf(false) }
    var isConnectionRestored by remember { mutableStateOf(false) }
    var showConnectingNeon by remember { mutableStateOf(false) }

    LaunchedEffect(auditText, isLoading) {
        if (auditText == "ERROR_NO_CONNECTION") {
            showConnectingNeon = true
            hadNoConnection = true
            isConnectionRestored = false
        } else if (hadNoConnection && (isLoading || (auditText.isNotEmpty() && auditText != "ERROR_NO_CONNECTION"))) {
            isConnectionRestored = true
            hadNoConnection = false
            delay(1500)
            showConnectingNeon = false
            isConnectionRestored = false
        }
    }

    val parsedSections by produceState(initialValue = emptyList<String>(), key1 = auditText) {
        value = withContext(kotlinx.coroutines.Dispatchers.Default) {
            splitIntoSections(auditText)
        }
    }

    val hasAuditInHistory = remember(notifications, auditText) {
        notifications.any { it.description.startsWith("||audit_req||") || it.description.startsWith("||audit_block||") } ||
        (auditText.isNotEmpty() && auditText != "ERROR_NO_CONNECTION")
    }

    val displayedSections = remember { mutableStateListOf<String>() }
    var isSimulatingTyping by remember { mutableStateOf(false) }
    var hasSentRequest by remember { mutableStateOf(false) }

    var userMessageText by remember(hasAuditInHistory) {
        mutableStateOf(if (!hasAuditInHistory && !isLoading) "Давид, проведи аудит за $periodTitle" else "")
    }
    var attachedFileName by remember { mutableStateOf("Выписка_$periodTitle.csv") }
    var isFileAttached by remember(hasAuditInHistory) {
        mutableStateOf(!hasAuditInHistory && !isLoading)
    }

    LaunchedEffect(periodTitle, auditText, notifications.size, isLoading, hasSentRequest) {
        val isErr = auditText == "ERROR_NO_CONNECTION" || auditText.contains("⚠️") || auditText.lowercase().contains("ошибка") || auditText.contains("Сбой")
        if (isErr) {
            hasSentRequest = false
            if (userMessageText.isEmpty()) {
                userMessageText = "Давид, проведи аудит за $periodTitle"
                isFileAttached = true
            }
        } else if (hasSentRequest || hasAuditInHistory || isLoading) {
            userMessageText = ""
            isFileAttached = false
        } else if (userMessageText.isEmpty() && !hasSentRequest && !hasAuditInHistory && !isLoading) {
            userMessageText = "Давид, проведи аудит за $periodTitle"
            isFileAttached = true
        }
    }

    val welcomeTimestamp = remember(profileName) {
        val profileKey = profileName.ifBlank { "default" }
        val key = "chat_welcome_timestamp_$profileKey"
        val saved = prefs.getLong(key, 0L)
        if (saved != 0L) saved else {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(key, now).apply()
            now
        }
    }

    val changelogTimestamp = remember(profileName, welcomeTimestamp) {
        val profileKey = profileName.ifBlank { "default" }
        val key = "chat_changelog_timestamp_$profileKey"
        val saved = prefs.getLong(key, 0L)
        if (saved != 0L) saved else {
            val now = welcomeTimestamp + 500L
            prefs.edit().putLong(key, now).apply()
            now
        }
    }

    val auditOfferTimestamp = remember(profileName, welcomeTimestamp) {
        val profileKey = profileName.ifBlank { "default" }
        val key = "audit_offer_timestamp_$profileKey"
        val saved = prefs.getLong(key, 0L)
        if (saved != 0L) saved else {
            val now = welcomeTimestamp + 1000L
            prefs.edit().putLong(key, now).apply()
            now
        }
    }

    LaunchedEffect(auditText, hasSentRequest) {
        if (hasSentRequest && auditText.isNotEmpty() && auditText != "ERROR_NO_CONNECTION") {
            prefs.edit().putLong("audit_last_success_timestamp", System.currentTimeMillis()).apply()
        }
    }

    LaunchedEffect(parsedSections) {
        if (parsedSections.isNotEmpty()) {
            displayedSections.clear()
            if (hasSentRequest) {
                isSimulatingTyping = true
                for (section in parsedSections) {
                    displayedSections.add(section)
                    delay(600)
                }
                isSimulatingTyping = false
            } else {
                displayedSections.addAll(parsedSections)
            }
        } else {
            displayedSections.clear()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate950
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Slate950)
        ) {
            // Header (Telegram Style Top Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                Brush.linearGradient(listOf(Indigo500, Emerald400)),
                                shape = CircleShape
                            )
                            .border(1.5.dp, if (isLoading) Emerald400 else Indigo500, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🐸", fontSize = 22.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Давид Жабов",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isLoading) "печатает$dots" else "в сети",
                            color = if (isLoading) Emerald400 else Indigo500.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Call, contentDescription = "Звонок", tint = Slate300)
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = Slate300)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate800)
            )

            // Unified Chat Feed
            val listState = rememberLazyListState()

            val chatItems = remember(notifications, displayedSections.toList(), isLoading, isGeneratingReaction, isSimulatingTyping, hasSentRequest, auditTimestamp, auditText, requestTimestamp, showConnectingNeon, isConnectionRestored, welcomeTimestamp, changelogTimestamp, auditOfferTimestamp) {
                val items = mutableListOf<ChatItem>()
                
                items.add(ChatWelcomeItem(welcomeTimestamp))
                items.add(ChatChangelogItem(changelogTimestamp))

                val filteredNotifications = notifications.filterNot {
                    it.title == "Жабов Давид" ||
                    it.description.contains("персональный фин-аналитик") ||
                    it.description.contains("Я Жабов Давид")
                }

                val notificationsToProcess = mutableListOf<NotificationEntity>()
                val validAuditOfferTime = auditOfferTimestamp
                
                for (notif in filteredNotifications) {
                    if (notif.timestamp < validAuditOfferTime) {
                        notificationsToProcess.add(notif)
                    }
                }
                
                val processNotifToItems: (NotificationEntity) -> List<ChatItem> = { notif ->
                    val res = mutableListOf<ChatItem>()
                    if (notif.description.startsWith("||audit_req||")) {
                        val reqText = notif.description.removePrefix("||audit_req||")
                        res.add(ChatAuditRequestItem(notif.timestamp, text = reqText, fileName = "Выписка_.csv"))
                    } else if (notif.description.startsWith("||audit_block||")) {
                        val blockText = notif.description.removePrefix("||audit_block||")
                        val isFirst = notif.title.contains("Главный Вердикт") || notif.title.contains("Аналитика")
                        res.add(ChatAuditBlockItem(notif.timestamp, text = blockText, isFirst = isFirst))
                    } else {
                        val (ops, _, _) = extractOpsAndComment(notif)
                        if (ops.isNotEmpty()) {
                            res.add(ChatNotificationUserItem(notif))
                        }
                        res.add(ChatNotificationDavidItem(notif))
                    }
                    res
                }

                for (notif in notificationsToProcess) {
                    items.addAll(processNotifToItems(notif))
                }

                items.add(ChatAuditOfferItem(validAuditOfferTime))

                val notifsAfterOffer = filteredNotifications.filter { it.timestamp >= validAuditOfferTime }
                for (notif in notifsAfterOffer) {
                    items.addAll(processNotifToItems(notif))
                }

                if ((isLoading || isSimulatingTyping) && !showConnectingNeon) {
                    items.add(ChatTypingItem(System.currentTimeMillis(), "audit"))
                }

                if (showConnectingNeon) {
                    items.add(ChatConnectingItem(Long.MAX_VALUE - 100L, isConnectionRestored))
                }

                if (isGeneratingReaction) {
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

            val unreadSeparatorIndex = remember(chatItems) {
                chatItems.indexOfFirst { it is ChatUnreadSeparatorItem }
            }

            var hasInitialScrolled by remember { mutableStateOf(false) }

            LaunchedEffect(unreadSeparatorIndex, chatItems.size, displayedSections.size) {
                if (chatItems.isNotEmpty()) {
                    snapshotFlow { listState.layoutInfo.totalItemsCount }
                        .filter { it >= chatItems.size }
                        .first()

                    if (!hasInitialScrolled) {
                        if (unreadSeparatorIndex != -1) {
                            listState.scrollToItem(unreadSeparatorIndex)
                        } else {
                            listState.scrollToItem(chatItems.size - 1)
                        }
                        hasInitialScrolled = true
                    } else if (hasSentRequest || isLoading || isSimulatingTyping) {
                        listState.animateScrollToItem(chatItems.size - 1)
                    } else {
                        listState.scrollToItem(chatItems.size - 1)
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = chatItems,
                    key = { _, item ->
                        when (item) {
                            is ChatWelcomeItem -> "welcome_${item.timestamp}"
                            is ChatChangelogItem -> "changelog_${item.timestamp}"
                            is ChatAuditOfferItem -> "offer_${item.timestamp}"
                            is ChatUnreadSeparatorItem -> "unread_sep_${item.timestamp}"
                            is ChatNotificationUserItem -> "notif_user_${item.notification.id}"
                            is ChatNotificationDavidItem -> "notif_david_${item.notification.id}"
                            is ChatAuditRequestItem -> "req_${item.timestamp}"
                            is ChatAuditSystemItem -> "sys_${item.timestamp}"
                            is ChatAuditBlockItem -> "block_${item.timestamp}_${item.text.hashCode()}"
                            is ChatAuditRetryItem -> "retry_${item.timestamp}"
                            is ChatTypingItem -> "typing_${item.type}"
                            is ChatConnectingItem -> "connecting_${item.timestamp}"
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
                        is ChatTypingItem -> RenderTypingItem(item)
                        is ChatAuditBlockItem -> RenderAuditBlockItem(item)
                        is ChatAuditRetryItem -> RenderAuditRetryItem(
                            item = item,
                            onRequestAudit = {
                                requestTimestamp = System.currentTimeMillis()
                                hasSentRequest = true
                                onRequestAudit()
                            }
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

            // Footer (Telegram style input bar with attached file)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate900)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                AnimatedVisibility(visible = isFileAttached) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp, start = 4.dp, end = 4.dp)
                            .background(Slate800, RoundedCornerShape(12.dp))
                            .border(1.dp, Indigo500.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Файл",
                                tint = Emerald400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = attachedFileName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить файл",
                            tint = Slate400,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { isFileAttached = false }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        color = Slate800,
                        border = BorderStroke(1.dp, if (userMessageText.isNotEmpty() || isFileAttached) Indigo500 else Slate700)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SentimentSatisfied,
                                contentDescription = "Смайлы",
                                tint = Slate400,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = userMessageText,
                                onValueChange = { userMessageText = it },
                                readOnly = false,
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(Emerald400),
                                decorationBox = { innerTextField ->
                                    if (userMessageText.isEmpty()) {
                                        Text(
                                            text = "Сообщение",
                                            color = Slate400,
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Прикрепить",
                                tint = if (isFileAttached) Emerald400 else Slate400,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        isFileAttached = !isFileAttached
                                        if (isFileAttached && attachedFileName.isEmpty()) {
                                            attachedFileName = "Выписка_$periodTitle.csv"
                                        }
                                    }
                            )
                        }
                    }

                    val canSend = userMessageText.isNotBlank() || isFileAttached
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(Indigo500, Color(0xFF3B82F6)))
                                else Brush.linearGradient(listOf(Indigo500.copy(alpha = 0.5f), Slate700)),
                                CircleShape
                            )
                            .clip(CircleShape)
                            .clickable(enabled = !isLoading && !isSimulatingTyping) {
                                if (canSend) {
                                    requestTimestamp = System.currentTimeMillis()
                                    hasSentRequest = true
                                    onRequestAudit()
                                    userMessageText = ""
                                    isFileAttached = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (canSend) Icons.Default.Send else Icons.Default.Mic,
                            contentDescription = if (canSend) "Отправить" else "Голос",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderWelcomeItem(item: ChatWelcomeItem, profileName: String, periodTitle: String) {
    val context = LocalContext.current
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val (greetingText, introText) = remember(profileName, periodTitle, item.timestamp) {
        val prefsInner = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val profileKey = profileName.ifBlank { "default" }
        val greetingKey = "chat_welcome_greeting_${profileKey}_${item.timestamp}"
        val savedGreeting = prefsInner.getString(greetingKey, null)
        val nameStr = if (profileName.isNotBlank() && profileName != "Вы") ", $profileName" else ""

        val finalGreeting = if (!savedGreeting.isNullOrBlank()) {
            savedGreeting
        } else {
            val hasOpenedChat = prefsInner.getBoolean("has_opened_david_chat_before_$profileKey", false)
            val isFirstEver = !hasOpenedChat
            if (isFirstEver) {
                prefsInner.edit().putBoolean("has_opened_david_chat_before_$profileKey", true).apply()
            }
            val cal = Calendar.getInstance().apply { timeInMillis = item.timestamp }
            val hourNow = cal.get(Calendar.HOUR_OF_DAY)
            val timeOfDayGreeting = when (hourNow) {
                in 5..11 -> "Доброе утро"
                in 12..16 -> "Добрый день"
                in 17..22 -> "Добрый вечер"
                else -> "Доброй ночи"
            }
            val greetingWord = if (isFirstEver) "Добро пожаловать" else timeOfDayGreeting
            val computed = "$greetingWord$nameStr!"
            prefsInner.edit().putString(greetingKey, computed).apply()
            computed
        }

        val msg2 = "Я — Жабов Давид 🐸, твой персональный фин-аналитик. Готов помочь разобрать твои финансы за $periodTitle."
        Pair(finalGreeting, msg2)
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Slate800.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Жабов Давид",
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = timeStr,
                        color = Slate400,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = greetingText,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Slate800.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Жабов Давид",
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = timeStr,
                        color = Slate400,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = introText,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun RenderChangelogItem(item: ChatChangelogItem) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Column(horizontalAlignment = Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Slate800.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Система",
                    color = Indigo400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                MarkdownFormattedText(
                    markdownText = "### 🚀 Что нового в версии 1.2\n- **Обновленный чат с Давидом**: полный стиль и комфорт мессенджера Telegram.\n- **Группировка операций**: повторные транзакции автоматически объединяются.\n- **Умная аналитика**: рекомендации и фин-советы прямо в диалоге.",
                    fontSize = 12.5.sp
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
private fun RenderAuditOfferItem(item: ChatAuditOfferItem, periodTitle: String) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Column(horizontalAlignment = Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Slate800.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Slate700),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Жабов Давид",
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
private fun RenderAuditRequestItem(item: ChatAuditRequestItem) {
    val timeStr = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    val fileName = item.fileName ?: "Выписка_.csv"
    val reqText = item.text.ifBlank { "Давид, проведи аудит за " }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Indigo500.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, if (item.hasError) Rose500 else Indigo400)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (fileName.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .background(Slate900.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                            .border(1.dp, Emerald400.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Emerald400.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Прикрепленный файл",
                                tint = Emerald400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = fileName,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "CSV • Прикреплен",
                                color = Emerald400.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.widthIn(min = 120.dp)
                ) {
                    Text(
                        text = reqText,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Bottom)
                    ) {
                        Text(
                            text = timeStr,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        if (item.hasError) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Ошибка отправки",
                                tint = Rose500,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (item.isRead) Icons.Default.DoneAll else Icons.Default.Check,
                                contentDescription = if (item.isRead) "Прочитано" else "Отправлено",
                                tint = if (item.isRead) Emerald400 else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
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
private fun RenderTypingItem(item: ChatTypingItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Slate800.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Slate700)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🐸", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (item.type == "audit") "Давид анализирует ваш бюджет..." else "Давид печатает...",
                    color = Emerald400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RenderAuditBlockItem(item: ChatAuditBlockItem) {
    if (item.text.isNotBlank()) {
        val timeStr = remember(item.timestamp) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        }
        val isChartBlock = remember(item.text) {
            item.text.contains("||chart||") || item.text.contains("Динамика трат") || item.text.contains("График трат")
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
                if (isChartBlock) {
                    val cleanText = remember(item.text) { item.text.replace("||chart||", "").trim() }
                    if (cleanText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = Slate800.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                MarkdownFormattedText(
                                    markdownText = cleanText,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    renderChartMessage(
                        title = "Динамика трат по дням",
                        time = timeStr
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        color = Slate800.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Slate700),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (item.isFirst) {
                                Text(
                                    text = "Жабов Давид (Аналитика)",
                                    color = Emerald400,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            MarkdownFormattedText(
                                markdownText = item.text,
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
        }
    }
}

@Composable
private fun RenderAuditRetryItem(item: ChatAuditRetryItem, onRequestAudit: () -> Unit) {
    Column(horizontalAlignment = Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Rose500.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "⚠️ Не удалось сгенерировать полный отчет. Проверьте подключение к интернету.",
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
}
