package com.example.davidapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Основной экран чата с кибер-консультантом Давидом Жабовым.
 * Выполнен в стиле Dark Neon / Cyberpunk Minimalist.
 */
@Composable
fun DavidChatScreen(
    viewModel: DavidViewModel = viewModel(),
    soundManager: SoundManager? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveSoundManager = remember(soundManager) {
        soundManager ?: SoundManager(context)
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
    val listState = rememberLazyListState()

    // Автоматическая прокрутка вниз при поступлении новых сообщений или активации ввода
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            DavidChatTopBar(
                onBack = onBack,
                onReset = { viewModel.resetSession(effectiveSoundManager) },
                isTyping = isTyping,
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Динамическая панель действий согласно текущему этапу
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

                // Поле ввода текста
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
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBackground)
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
    }
}
