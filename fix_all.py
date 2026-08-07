import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

start = "                if (selectedTab == 0) {"
end = "                // Divider\n                Box("

start_idx = text.find(start)
end_idx = text.find(end)

if start_idx != -1 and end_idx != -1:
    print(f"Found at {start_idx} to {end_idx}")
    
    new_code = """
                // NEW UNIFIED CHAT LOGIC
                val chatItems = remember(notifications, displayedSections, isLoading, isGeneratingReaction, isSimulatingTyping, hasSentRequest, auditTimestamp) {
                    val items = mutableListOf<ChatItem>()
                    
                    // 1. Transaction Notifications
                    notifications.forEach { items.add(ChatNotificationItem(it)) }
                    
                    // 2. AI Audit logic
                    val baseAuditTime = auditTimestamp ?: requestTimestamp ?: if (auditText.isNotEmpty()) System.currentTimeMillis() else null
                    
                    if (baseAuditTime != null) {
                        val reqTime = if (auditTimestamp != null) baseAuditTime - 1000 else requestTimestamp ?: baseAuditTime
                        if (hasSentRequest || displayedSections.isNotEmpty() || isLoading || auditText.isNotEmpty()) {
                            items.add(ChatAuditRequestItem(reqTime))
                            if (hasSentRequest) items.add(ChatAuditSystemItem(reqTime + 100))
                        }
                        
                        // typing indicator for AI Audit
                        if (isLoading || isSimulatingTyping) {
                            items.add(ChatTypingItem(System.currentTimeMillis(), "audit"))
                        } else {
                            displayedSections.forEachIndexed { index, txt ->
                                items.add(ChatAuditBlockItem(baseAuditTime + 200 + index * 10, txt, index == 0))
                            }
                            
                            if (hasSentRequest && auditText.isBlank() && displayedSections.isEmpty() && !isLoading) {
                                items.add(ChatAuditBlockItem(baseAuditTime + 200, "Нет данных для отчета.", true))
                            }
                        }
                    }
                    
                    // 3. Generating Reaction indicator
                    if (isGeneratingReaction) {
                        items.add(ChatTypingItem(System.currentTimeMillis(), "reaction"))
                    }
                    
                    items.sortedBy { it.timestamp }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 1. Welcome Greeting from David
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Slate800, shape = CircleShape)
                                        .border(1.5.dp, Emerald400, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🐸", fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                                    color = Slate800.copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth(0.92f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Жабов Давид",
                                            color = Emerald400,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Привет! Я слежу за твоей финансовой дисциплиной. Буду комментировать каждую твою операцию прямо здесь! 🐸\\n\\nА если хочешь полную аналитику и жесткий разбор твоего бюджета за весь период — нажми кнопку внизу, и я составлю подробный ИИ-отчет!",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    chatItems.forEach { item ->
                        when (item) {
                            is ChatNotificationItem -> {
                                ChatNotification(item.notification, profileName)
                            }
                            is ChatAuditRequestItem -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Surface(
                                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp),
                                                color = Indigo500.copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Indigo500.copy(alpha = 0.5f)),
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = profileName,
                                                            color = Indigo500,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                        if (hasSentRequest) {
                                                            Text(
                                                                text = "Запрос отправлен",
                                                                color = Slate400,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(
                                                        text = "💬 Давид, сделай разбор за $periodTitle! 📉",
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is ChatAuditSystemItem -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Slate800.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Text(
                                            text = "Аудит запрашивается...",
                                            color = Slate400,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            is ChatTypingItem -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Spacer(modifier = Modifier.width(40.dp))
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Slate800.copy(alpha = 0.3f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.2f)),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.type == "audit") {
                                                com.example.ui.components.NeonCircularProgressIndicator(size = 12.dp, strokeWidth = 1.5.dp)
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Emerald400, shape = CircleShape)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (item.type == "audit") "Давид составляет отчет$dots" else "Давид печатает$dots",
                                                color = Emerald400,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            is ChatAuditBlockItem -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            if (item.isFirst) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(Slate800, shape = CircleShape)
                                                        .border(1.5.dp, Indigo500, shape = CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("🐸", fontSize = 16.sp)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(40.dp))
                                            }
                                            Surface(
                                                shape = if (item.isFirst) {
                                                    RoundedCornerShape(topStart = 2.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                                } else {
                                                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                                },
                                                color = Slate800.copy(alpha = 0.5f),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    Indigo500.copy(alpha = 0.3f)
                                                ),
                                                modifier = Modifier.fillMaxWidth(0.95f)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    if (item.isFirst) {
                                                        Text(
                                                            text = "Жабов Давид",
                                                            color = Emerald400,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                    }
                                                    MarkdownFormattedText(markdownText = item.text)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!hasSentRequest && displayedSections.isEmpty() && !isLoading) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ПОЛУЧИТЬ АУДИТ БЮДЖЕТА",
                                color = Slate500,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Emerald400.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Emerald400),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        hasSentRequest = true
                                        requestTimestamp = System.currentTimeMillis()
                                        onRequestAudit()
                                    }
                                    .padding(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "💬 Давид, сделай разбор за $periodTitle! 📉",
                                        color = Emerald400,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
"""

    text = text[:start_idx] + new_code + text[end_idx:]
    with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
        f.write(text)
    print("Replaced successfully.")
else:
    print("Not found.")

