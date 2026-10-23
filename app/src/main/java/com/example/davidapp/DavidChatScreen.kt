package com.example.davidapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.DarkBg

/**
 * Основной экран чата с кибер-консультантом Давидом Жабовым.
 * Выполнен в стиле Dark Neon / Cyberpunk Minimalist.
 *
 * Архитектура верстки через Scaffold:
 * 1. topBar: шапка чата Давида с Modifier.statusBarsPadding().
 * 2. bottomBar: единый Column(modifier = Modifier.fillMaxWidth().background(DarkBg).navigationBarsPadding().imePadding()),
 *    внутри которого расположены DavidQuickActionsBar и DavidChatInputBar с отступом padding(bottom = 8.dp).
 * 3. content (innerPadding): LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)),
 *    чтобы список сообщений никогда не перекрывал и не выталкивал bottomBar.
 */
@Composable
fun DavidChatScreen(
    budgetId: String = "default",
    profileName: String = "Максим",
    apiKey: String = "",
    viewModel: DavidViewModel = viewModel(),
    soundManager: SoundManager? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveSoundManager = remember(soundManager) {
        soundManager ?: SoundManager(context.applicationContext)
    }

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    LaunchedEffect(budgetId, profileName, apiKey) {
        viewModel.bindProfile(budgetId, profileName, apiKey)
    }

    DisposableEffect(effectiveSoundManager) {
        onDispose {
            if (soundManager == null) {
                effectiveSoundManager.release()
            }
        }
    }

    val messages by viewModel.messages.collectAsState()
    val stage by viewModel.stage.collectAsState()
    val isTyping by viewModel.isDavidTyping.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showAchievementsDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Автоматическая плавная прокрутка к последнему элементу внизу списка
    LaunchedEffect(messages.size, messages.lastOrNull()?.id, isTyping, stage) {
        if (messages.isNotEmpty()) {
            val totalItems = messages.size + if (isTyping) 1 else 0
            listState.animateScrollToItem(totalItems)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DavidChatTopBar(
                onBack = onBack,
                onReset = { viewModel.resetSession(effectiveSoundManager) },
                onOpenAchievements = { showAchievementsDialog = true },
                hasApiKey = hasApiKey,
                onOpenApiKeyDialog = { showApiKeyDialog = true },
                isTyping = isTyping,
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp)
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Динамическая панель действий согласно текущему этапу ("Давид, сделай отчет", выбор периода PDF и др.)
                DavidQuickActionsBar(
                    stage = stage,
                    onActionClick = { action ->
                        viewModel.handleAction(action, effectiveSoundManager)
                    },
                    onPeriodSelect = { period ->
                        viewModel.processFile(period, effectiveSoundManager)
                    },
                    onExportPdf = {
                        viewModel.exportPdf(effectiveSoundManager)
                    }
                )

                // Горизонтальная лента быстрых подсказок (из David-finance-main-2)
                DavidSuggestionChipsRow(
                    hasApiKey = hasApiKey,
                    onSendPrompt = { prompt ->
                        viewModel.sendTextMessage(prompt, effectiveSoundManager)
                    },
                    onActionClick = { action ->
                        viewModel.handleAction(action, effectiveSoundManager)
                    },
                    onOpenApiKeyDialog = { showApiKeyDialog = true }
                )

                // Поле ввода текста со скрепкой и кнопкой отправки
                DavidChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendTextMessage(textToSend, effectiveSoundManager)
                        }
                    },
                    onAttach = {
                        viewModel.handleAction("START", effectiveSoundManager)
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }

            items(
                items = messages,
                key = { it.id }
            ) { message ->
                DavidMessageItem(
                    message = message,
                    onConfirmProposal = { msgId ->
                        viewModel.confirmChatProposedOperation(msgId, effectiveSoundManager)
                    },
                    onDismissProposal = { msgId ->
                        viewModel.dismissChatProposedOperation(msgId)
                    }
                )
            }

            if (isTyping) {
                item {
                    DavidTypingBubble()
                }
            }

            item {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showAchievementsDialog) {
        AchievementsDialog(onDismiss = { showAchievementsDialog = false })
    }

    if (showApiKeyDialog) {
        var keyText by remember { mutableStateOf("") }
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = com.example.ui.theme.SurfaceCard,
            title = {
                androidx.compose.material3.Text(
                    "🔑 Gemini API Ключ",
                    color = TextLight,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column {
                    androidx.compose.material3.Text(
                        "Введите ключ от Google AI Studio для активации Давида Жабова (Gemini 3.8) и голосового ввода.",
                        color = TextMuted,
                        fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        label = { androidx.compose.material3.Text("API Key") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation('*'),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { pasted ->
                                if (pasted.isNotBlank()) keyText = pasted.trim()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.material3.Text("📋 Вставить из буфера")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        viewModel.saveApiKeyFromChat(keyText, effectiveSoundManager)
                        showApiKeyDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.example.ui.theme.EmeraldPrimary,
                        contentColor = DarkBackground
                    )
                ) {
                    androidx.compose.material3.Text("Сохранить", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showApiKeyDialog = false }) {
                    androidx.compose.material3.Text("Отмена", color = TextMuted)
                }
            }
        )
    }
}

