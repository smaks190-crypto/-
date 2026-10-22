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

    var inputText by remember { mutableStateOf("") }
    var showAchievementsDialog by remember { mutableStateOf(false) }
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                DavidMessageItem(message = message)
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
}
