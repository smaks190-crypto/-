import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

# We want to remove displayedSections and the LaunchedEffect that feeds it
target_remove_logic = """    val parsedSections by androidx.compose.runtime.produceState(initialValue = emptyList<String>(), key1 = auditText) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            splitIntoSections(auditText)
        }
    }
    val displayedSections = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var isSimulatingTyping by remember { mutableStateOf(false) }
    var hasPlayedNotification by remember { mutableStateOf(auditText.isNotEmpty()) }

    val currentParsedSections by androidx.compose.runtime.rememberUpdatedState(parsedSections)
    val currentIsLoading by androidx.compose.runtime.rememberUpdatedState(isLoading)
    val currentHasSentRequest by androidx.compose.runtime.rememberUpdatedState(hasSentRequest)

    LaunchedEffect(Unit) {
        // Cached case
        if (displayedSections.isEmpty() && currentParsedSections.isNotEmpty() && !currentIsLoading && !currentHasSentRequest) {
            displayedSections.addAll(currentParsedSections)
            isSimulatingTyping = false
        }

        while (true) {
            if (displayedSections.size < currentParsedSections.size) {
                val nextIndex = displayedSections.size
                val isLastSectionAndLoading = (nextIndex == currentParsedSections.size - 1) && currentIsLoading
                
                if (isLastSectionAndLoading) {
                    isSimulatingTyping = true
                    kotlinx.coroutines.delay(200)
                } else {
                    isSimulatingTyping = true
                    val sectionText = currentParsedSections[nextIndex]
                    val delayTime = (sectionText.length * 25L).coerceIn(800L, 3500L)
                    kotlinx.coroutines.delay(delayTime)
                    
                    if (nextIndex < currentParsedSections.size) {
                        displayedSections.add(currentParsedSections[nextIndex])
                    }
                }
            } else {
                if (currentIsLoading) {
                    isSimulatingTyping = true
                } else {
                    isSimulatingTyping = false
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    LaunchedEffect(displayedSections.size, isLoading) {
        if (!isLoading && displayedSections.isNotEmpty() && !hasPlayedNotification) {
            hasPlayedNotification = true
            try {
                val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(context, notificationUri)
                    prepare()
                }
                mediaPlayer.start()
                mediaPlayer.setOnCompletionListener { it.release() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }"""

replacement_logic = """    val parsedSections = remember(auditText) { splitIntoSections(auditText) }
    var hasPlayedNotification by remember { mutableStateOf(auditText.isNotEmpty()) }

    LaunchedEffect(isLoading) {
        if (!isLoading && auditText.isNotEmpty() && !hasPlayedNotification) {
            hasPlayedNotification = true
            try {
                val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(context, notificationUri)
                    prepare()
                }
                mediaPlayer.start()
                mediaPlayer.setOnCompletionListener { it.release() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }"""

target_ui = """                        // typing indicator for AI Audit
                        displayedSections.forEachIndexed { index, txt ->
                            items.add(ChatAuditBlockItem(baseAuditTime + 200 + index * 10, txt, index == 0))
                        }
                        if (isLoading || isSimulatingTyping) {
                            items.add(ChatTypingItem(System.currentTimeMillis() + 1000, "audit"))
                        } else if (hasSentRequest && auditText.isBlank() && displayedSections.isEmpty() && !isLoading) {
                            items.add(ChatAuditBlockItem(baseAuditTime + 200, "Нет данных для отчета.", true))
                        }"""

replacement_ui = """                        // typing indicator for AI Audit
                        parsedSections.forEachIndexed { index, txt ->
                            items.add(ChatAuditBlockItem(baseAuditTime + 200 + index * 10, txt, index == 0))
                        }
                        if (isLoading) {
                            items.add(ChatTypingItem(System.currentTimeMillis() + 1000, "audit"))
                        } else if (hasSentRequest && auditText.isBlank() && parsedSections.isEmpty() && !isLoading) {
                            items.add(ChatAuditBlockItem(baseAuditTime + 200, "Нет данных для отчета.", true))
                        }"""

if target_remove_logic in text and target_ui in text:
    text = text.replace(target_remove_logic, replacement_logic)
    text = text.replace(target_ui, replacement_ui)
    
    # Also fix the previous reference to displayedSections.isNotEmpty()
    text = text.replace("if (hasSentRequest || displayedSections.isNotEmpty() || isLoading || auditText.isNotEmpty())",
                        "if (hasSentRequest || parsedSections.isNotEmpty() || isLoading || auditText.isNotEmpty())")

    with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
        f.write(text)
    print("Logic successfully replaced.")
else:
    print("Target missing!")
