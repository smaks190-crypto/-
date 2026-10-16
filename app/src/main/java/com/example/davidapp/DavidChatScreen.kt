package com.example.davidapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

/**
 * Основной экран чата с кибер-консультантом Давидом Жабовым.
 * Выполнен в стиле Dark Neon / Cyberpunk Minimalist.
 * 
 * Архитектура верстки:
 * 1. Корневой Column с Modifier.fillMaxSize().
 * 2. Список сообщений (LazyColumn) расположен в контейнере с Modifier.weight(1f),
 *    что исключает выталкивание нижней панели ввода за пределы экрана при любом объеме сообщений.
 * 3. Нижняя панель действий и ввода имеет Modifier.imePadding().navigationBarsPadding(),
 *    что гарантирует корректное отображение над полосой жестов Android и клавиатурой.
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

    // Автоматическая прокрутка вниз при поступлении новых сообщений или активации ввода
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. Верхняя панель (с отступом статус-бара)
        DavidChatTopBar(
            onBack = onBack,
            onReset = { viewModel.resetSession(effectiveSoundManager) },
            onOpenAchievements = { showAchievementsDialog = true },
            isTyping = isTyping,
            modifier = Modifier.statusBarsPadding()
        )

        // 2. Список сообщений: строго weight(1f), не выталкивает нижнюю панель
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
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

        // 3. Нижняя панель действий и ввода с отступами от полосы жестов и мягкой клавиатуры
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Динамическая панель действий согласно текущему этапу ("Давид, сделай отчет" и др.)
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

    if (showAchievementsDialog) {
        AchievementsDialog(onDismiss = { showAchievementsDialog = false })
    }
}

