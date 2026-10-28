package com.example.davidapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.davidapp.live.DavidLiveSheet
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800

/**
 * Основной экран чата с ИИ-аудитором Давидом Жабовым.
 * Выполнен в единой эстетике Obsidian Neo-Bank (David Finance AI):
 * - Шапка чата с живым статусом и быстрым переходом к ачивкам.
 * - Единая трансформирующаяся полоса чипов (DavidSmartActionStrip), в которой нажатие на
 *   «🔥 Разбор трат» плавно раскрывает инлайн-выбор периода (День, Неделя, Месяц, Год) без лишних окон и дублей.
 * - Обтекаемая нижняя капсула ввода сообщения с запуском голосового режима **David Live**
 *   по долгому удержанию (~550 мс) кнопки отправки.
 */
@Composable
fun DavidChatScreen(
    budgetId: String = "default",
    profileName: String = "Максим",
    apiKey: String = "",
    viewModel: DavidViewModel = viewModel(),
    soundManager: SoundManager? = null,
    onBack: (() -> Unit)? = null,
    isEmbeddedInCapsule: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val effectiveSoundManager = remember(soundManager) {
        soundManager ?: SoundManager(context.applicationContext)
    }

    var isSelectingPeriod by remember { mutableStateOf(false) }

    val messages by viewModel.messages.collectAsState()
    val stage by viewModel.stage.collectAsState()
    val isTyping by viewModel.isDavidTyping.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()

    // David Live states
    val isLiveOverlayVisible by viewModel.isLiveOverlayVisible.collectAsState()
    val liveConnectionState by viewModel.liveConnectionState.collectAsState()
    val liveSubtitle by viewModel.liveSubtitle.collectAsState()
    val liveRayMagnitudes by viewModel.liveRayMagnitudes.collectAsState()
    val liveOverallEnergy by viewModel.liveOverallEnergy.collectAsState()
    val liveActiveSpeaker by viewModel.liveActiveSpeaker.collectAsState()
    val isLiveMicMuted by viewModel.isLiveMicMuted.collectAsState()
    val liveErrorMessage by viewModel.liveErrorMessage.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showAchievementsDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Permission launcher for RECORD_AUDIO on first David Live launch
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startDavidLive(effectiveSoundManager)
        }
    }

    val triggerDavidLiveWithPermissionCheck = {
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPerm) {
            viewModel.startDavidLive(effectiveSoundManager)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (isLiveOverlayVisible) {
        BackHandler {
            viewModel.endDavidLiveSession(effectiveSoundManager)
        }
    } else if (onBack != null) {
        BackHandler {
            if (isSelectingPeriod) {
                isSelectingPeriod = false
            } else {
                onBack()
            }
        }
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

    LaunchedEffect(messages.size, messages.lastOrNull()?.id, isTyping, stage) {
        if (messages.isNotEmpty()) {
            val totalItems = messages.size + if (isTyping) 1 else 0
            listState.animateScrollToItem(totalItems)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg),
            containerColor = DarkBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                DavidChatTopBar(
                    onBack = onBack,
                    onReset = {
                        isSelectingPeriod = false
                        viewModel.resetSession(effectiveSoundManager)
                    },
                    onOpenAchievements = { showAchievementsDialog = true },
                    hasApiKey = hasApiKey,
                    onOpenApiKeyDialog = { showApiKeyDialog = true },
                    isTyping = isTyping,
                    modifier = if (isEmbeddedInCapsule) Modifier else Modifier.statusBarsPadding()
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg.copy(alpha = 0.96f))
                        .then(
                            if (isEmbeddedInCapsule) Modifier
                            else Modifier.navigationBarsPadding().imePadding()
                        )
                        .padding(horizontal = 14.dp)
                        .padding(top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Единая морфинг-полоса быстрых действий и выбора периода
                    DavidSmartActionStrip(
                        stage = stage,
                        hasApiKey = hasApiKey,
                        isSelectingPeriod = isSelectingPeriod,
                        onTogglePeriodSelector = { open ->
                            isSelectingPeriod = open
                            if (!open && stage == DavidStage.FILE_SELECTION) {
                                viewModel.cancelPeriodSelection()
                            }
                        },
                        onPeriodSelect = { period ->
                            isSelectingPeriod = false
                            viewModel.processFile(period, effectiveSoundManager)
                        },
                        onSendPrompt = { prompt ->
                            isSelectingPeriod = false
                            viewModel.sendTextMessage(prompt, effectiveSoundManager)
                        },
                        onActionClick = { action ->
                            isSelectingPeriod = false
                            viewModel.handleAction(action, effectiveSoundManager)
                        },
                        onExportPdf = {
                            isSelectingPeriod = false
                            viewModel.exportPdf(effectiveSoundManager)
                        },
                        onOpenApiKeyDialog = { showApiKeyDialog = true }
                    )

                    // Капсула ввода текста в стиле Obsidian Neo-Bank (долгое нажатие ~550 мс -> David Live)
                    DavidChatInputBar(
                        text = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                val textToSend = inputText
                                inputText = ""
                                isSelectingPeriod = false
                                viewModel.sendTextMessage(textToSend, effectiveSoundManager)
                            }
                        },
                        onStartDavidLive = {
                            // Важно: не очищаем и не отправляем inputText при запуске David Live
                            isSelectingPeriod = false
                            triggerDavidLiveWithPermissionCheck()
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
                        },
                        onRetryLiveSummary = { msgId ->
                            viewModel.retryLiveSummary(msgId, effectiveSoundManager)
                        }
                    )
                }

                if (isTyping) {
                    item {
                        DavidCoinTossLoader()
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // Полноэкранный режим живого голосового диалога David Live
        AnimatedVisibility(
            visible = isLiveOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DavidLiveSheet(
                connectionState = liveConnectionState,
                liveSubtitle = liveSubtitle,
                rayMagnitudes = liveRayMagnitudes,
                overallEnergy = liveOverallEnergy,
                activeSpeaker = liveActiveSpeaker,
                isMicMuted = isLiveMicMuted,
                errorMessage = liveErrorMessage,
                onToggleMicMute = { viewModel.toggleLiveMicMute() },
                onRetryConnection = { viewModel.retryLiveConnection() },
                onOpenApiKeySettings = { showApiKeyDialog = true },
                onEndSession = { viewModel.endDavidLiveSession(effectiveSoundManager) }
            )
        }
    }

    if (showAchievementsDialog) {
        AchievementsDialog(onDismiss = { showAchievementsDialog = false })
    }

    if (showApiKeyDialog) {
        var keyText by remember { mutableStateOf("") }
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
            title = {
                Text(
                    text = "🔑 Ключ Gemini API",
                    color = Color.White,
                    fontFamily = ManropeFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Вставьте пользовательский ключ из Google AI Studio для работы Давида Жабова, David Live и умного голосового ввода.",
                        color = Slate400,
                        fontFamily = ManropeFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyText,
                        onValueChange = { keyText = it },
                        placeholder = { Text("AIzaSy...", color = Slate400, fontSize = 13.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation('*'),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkBg,
                            unfocusedContainerColor = DarkBg,
                            focusedBorderColor = Emerald400,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            clipboardManager.getText()?.text?.let { pasted ->
                                if (pasted.isNotBlank()) keyText = pasted.trim()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📋 Вставить из буфера",
                            color = Emerald400,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveApiKeyFromChat(keyText, effectiveSoundManager)
                        showApiKeyDialog = false
                        if (isLiveOverlayVisible && keyText.trim().isNotBlank()) {
                            viewModel.startDavidLive(effectiveSoundManager)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald400,
                        contentColor = DarkBg
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Сохранить",
                        fontFamily = ManropeFontFamily,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text(
                        text = "Отмена",
                        color = Slate400,
                        fontFamily = ManropeFontFamily
                    )
                }
            }
        )
    }
}
