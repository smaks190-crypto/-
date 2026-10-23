package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassHighlight
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.NeonRose
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.viewmodel.BudgetViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

enum class BottomBarMode {
    NAV_TABS,
    MANUAL_ADD,
    VOICE_INPUT
}

private enum class VoiceSubState {
    RECORDING,
    CONSENT,
    API_KEY,
    OPERATIONS
}

/**
 * Единая трансформирующаяся нижняя капсула (Morphing Bottom Bar):
 * - В режиме `BottomBarMode.NAV_TABS`: отображает 4 вкладки (`Период`, `Долги`, `Цели`, `Отчет`)
 *   и встроенную внутрь рамки кнопку `+` справа (только на вкладке `Период`).
 * - Короткое нажатие на `+` (`onTap`) -> переводит капсулу в `BottomBarMode.MANUAL_ADD`
 *   и плавно раскрывает рамку вверх в форму «Добавить операцию».
 * - Долгое нажатие на `+` (`onLongPress`) -> вызывает `HapticFeedbackType.LongPress`,
 *   переводит капсулу в `BottomBarMode.VOICE_INPUT`, показывает живой голосовой бар с микрофоном,
 *   распознаваемым текстом («Слушаю вас...»), `MovingNeonGlow` и крестиком `✕`, а по мере
 *   распознавания операций плавно вырастает вверх со списком операций и кнопкой «Сохранить».
 */
@Composable
fun MorphingBottomBar(
    viewModel: BudgetViewModel,
    currentMainTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedDate: String,
    showManualInput: Boolean,
    onDismissManualInput: () -> Unit,
    onOpenManualInput: () -> Unit,
    initialType: String = "expense",
    onOverlayActiveChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val voiceManager = viewModel.voiceInputManager
    val isListening by voiceManager.isListening.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val partialText by voiceManager.partialText.collectAsState()
    val rmsDb by voiceManager.rmsDb.collectAsState()
    val errorState by voiceManager.errorState.collectAsState()

    val isAnalyzingVoice by viewModel.isAnalyzingVoice.collectAsState()
    val parsedOperations by viewModel.parsedVoiceOperations.collectAsState()
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val isConsentGiven by viewModel.isGeminiConsentGiven.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    var bottomBarMode by remember { mutableStateOf(BottomBarMode.NAV_TABS) }
    var showInlineConsent by remember { mutableStateOf(false) }
    var showInlineApiKey by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val editableList = remember { mutableStateListOf<ParsedVoiceOperation>() }

    LaunchedEffect(parsedOperations) {
        val ops = parsedOperations
        if (ops != null) {
            if (editableList.isEmpty()) {
                editableList.addAll(ops)
            } else if (ops.size > editableList.size) {
                val newItems = ops.subList(editableList.size, ops.size)
                editableList.addAll(newItems)
            }
            if (ops.isNotEmpty()) {
                bottomBarMode = BottomBarMode.VOICE_INPUT
            }
        } else {
            editableList.clear()
            editingIndex = null
        }
    }

    // Синхронизация внешнего флага showManualInput с режимом капсулы
    LaunchedEffect(showManualInput) {
        if (showManualInput) {
            bottomBarMode = BottomBarMode.MANUAL_ADD
        } else if (bottomBarMode == BottomBarMode.MANUAL_ADD) {
            bottomBarMode = BottomBarMode.NAV_TABS
        }
    }

    // Если голосовой ввод завершился без распознанных операций и без открытых под-окон — возвращаемся к вкладкам
    LaunchedEffect(isListening, isAnalyzingVoice, editableList.size, showInlineConsent, showInlineApiKey) {
        if (bottomBarMode == BottomBarMode.VOICE_INPUT &&
            !isListening &&
            !isAnalyzingVoice &&
            editableList.isEmpty() &&
            !showInlineConsent &&
            !showInlineApiKey
        ) {
            bottomBarMode = BottomBarMode.NAV_TABS
            viewModel.setVoiceActive(false)
        }
    }

    val isExpandedOrVoice = bottomBarMode != BottomBarMode.NAV_TABS
    LaunchedEffect(isExpandedOrVoice) {
        onOverlayActiveChanged(isExpandedOrVoice)
    }

    fun startLiveRecordingSession() {
        viewModel.startVoiceRecording(context)
    }

    fun cancelAndCollapseToNav() {
        viewModel.cancelVoiceRecording()
        showInlineConsent = false
        showInlineApiKey = false
        editingIndex = null
        editableList.clear()
        if (showManualInput) {
            onDismissManualInput()
        }
        bottomBarMode = BottomBarMode.NAV_TABS
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            bottomBarMode = BottomBarMode.VOICE_INPUT
            startLiveRecordingSession()
        } else {
            Toast.makeText(
                context,
                "Для голосового ввода необходим доступ к микрофону",
                Toast.LENGTH_SHORT
            ).show()
            cancelAndCollapseToNav()
        }
    }

    fun triggerVoiceMode() {
        if (showManualInput) {
            onDismissManualInput()
        }
        bottomBarMode = BottomBarMode.VOICE_INPUT
        if (!isConsentGiven) {
            showInlineConsent = true
            return
        }
        if (apiKey.isBlank()) {
            showInlineApiKey = true
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startLiveRecordingSession()
        }
    }

    LaunchedEffect(errorState) {
        errorState?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            viewModel.setVoiceActive(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (voiceManager.isListening.value) {
                voiceManager.onAudioChunkReady = null
                voiceManager.stopListening()
            }
        }
    }

    // Системная кнопка «Назад» плавно складывает морфинг-капсулу обратно в режим вкладок
    BackHandler(enabled = bottomBarMode != BottomBarMode.NAV_TABS) {
        if (bottomBarMode == BottomBarMode.VOICE_INPUT && editingIndex != null) {
            editingIndex = null
        } else {
            cancelAndCollapseToNav()
        }
    }

    val cornerRadius by animateDpAsState(
        targetValue = when (bottomBarMode) {
            BottomBarMode.NAV_TABS -> 22.dp
            BottomBarMode.MANUAL_ADD -> 24.dp
            BottomBarMode.VOICE_INPUT -> if (editableList.isNotEmpty() || showInlineConsent || showInlineApiKey) 24.dp else 22.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "morph_bar_corner"
    )

    val borderBrush = when (bottomBarMode) {
        BottomBarMode.NAV_TABS -> Brush.horizontalGradient(listOf(GlassHighlight, GlassHighlight))
        BottomBarMode.MANUAL_ADD -> Brush.linearGradient(
            listOf(Emerald400.copy(alpha = 0.55f), Indigo500.copy(alpha = 0.45f))
        )
        BottomBarMode.VOICE_INPUT -> Brush.linearGradient(
            listOf(NeonEmerald.copy(alpha = 0.7f), NeonIndigo.copy(alpha = 0.7f))
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Полупрозрачный фон-затемнение при раскрытом окне ручного ввода или списка распознанных операций
        val showScrim = bottomBarMode == BottomBarMode.MANUAL_ADD ||
            (bottomBarMode == BottomBarMode.VOICE_INPUT && (editableList.isNotEmpty() || showInlineConsent || showInlineApiKey))

        AnimatedVisibility(
            visible = showScrim,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        cancelAndCollapseToNav()
                    }
            )
        }

        // Сама нижняя морфинг-рамка (остается на своем месте внизу экрана с теми же боковыми отступами 16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = if (bottomBarMode == BottomBarMode.VOICE_INPUT) NeonEmerald else Color.Black,
                    spotColor = if (bottomBarMode == BottomBarMode.VOICE_INPUT) NeonIndigo else Color.Black
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    if (bottomBarMode == BottomBarMode.NAV_TABS) ObsidianSurface.copy(alpha = 0.96f)
                    else Slate900.copy(alpha = 0.98f)
                )
                .border(
                    width = 1.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            AnimatedContent(
                targetState = bottomBarMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(160)))
                        .using(
                            SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ ->
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                }
                            )
                        )
                },
                label = "bottom_bar_mode_morph"
            ) { mode ->
                when (mode) {
                    BottomBarMode.NAV_TABS -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 4 вкладки навигации, которые плавно занимают всю доступную ширину
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val barWidth = maxWidth
                                val tabCount = 4
                                val tabWidth = barWidth / tabCount

                                val animatedFraction by animateFloatAsState(
                                    targetValue = currentMainTab.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "bottom_nav_pill"
                                )

                                Box(
                                    modifier = Modifier
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .offset(x = tabWidth * animatedFraction)
                                        .background(Emerald400.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                        .border(1.dp, Emerald400.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                )

                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MorphNavTabButton(
                                        text = "Период",
                                        icon = Icons.Default.GridView,
                                        isSelected = currentMainTab == 0,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("tab_period"),
                                        onClick = { onSelectTab(0) }
                                    )

                                    MorphNavTabButton(
                                        text = "Долги",
                                        icon = Icons.Default.CreditCard,
                                        isSelected = currentMainTab == 1,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("tab_debts"),
                                        onClick = { onSelectTab(1) }
                                    )

                                    MorphNavTabButton(
                                        text = "Цели",
                                        icon = Icons.Default.EmojiEvents,
                                        isSelected = currentMainTab == 2,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("tab_goals"),
                                        onClick = { onSelectTab(2) }
                                    )

                                    MorphNavTabButton(
                                        text = "Отчет",
                                        icon = Icons.Default.PieChart,
                                        isSelected = currentMainTab == 3,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .testTag("tab_annual"),
                                        onClick = { onSelectTab(3) }
                                    )
                                }
                            }

                            // Встроенная симметричная кнопка `+` внутри капсулы навигации (ТОЛЬКО на вкладке Период)
                            AnimatedVisibility(
                                visible = currentMainTab == 0,
                                enter = expandHorizontally(
                                    expandFrom = Alignment.End,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkHorizontally(
                                    shrinkTowards = Alignment.End,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeOut(animationSpec = tween(150))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(start = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(68.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                color = NeonEmerald.copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = NeonEmerald.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = {
                                                        bottomBarMode = BottomBarMode.MANUAL_ADD
                                                        onOpenManualInput()
                                                    },
                                                    onLongPress = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        triggerVoiceMode()
                                                    }
                                                )
                                            }
                                            .testTag("embedded_add_operation_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Добавить операцию (удерживайте для голосового ввода)",
                                                tint = NeonEmerald,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Добавить",
                                                color = NeonEmerald,
                                                fontSize = 10.sp,
                                                fontFamily = ManropeFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    BottomBarMode.MANUAL_ADD -> {
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp)
                                .verticalScroll(scrollState)
                        ) {
                            VoiceManualInputPane(
                                viewModel = viewModel,
                                categories = categories,
                                selectedDate = selectedDate,
                                initialType = initialType,
                                contentAlpha = 1f,
                                onDismiss = {
                                    onDismissManualInput()
                                    bottomBarMode = BottomBarMode.NAV_TABS
                                }
                            )
                        }
                    }

                    BottomBarMode.VOICE_INPUT -> {
                        val voiceSubState = when {
                            showInlineConsent -> VoiceSubState.CONSENT
                            showInlineApiKey -> VoiceSubState.API_KEY
                            editableList.isNotEmpty() -> VoiceSubState.OPERATIONS
                            else -> VoiceSubState.RECORDING
                        }

                        AnimatedContent(
                            targetState = voiceSubState,
                            transitionSpec = {
                                (fadeIn(tween(200)) togetherWith fadeOut(tween(140)))
                                    .using(
                                        SizeTransform(
                                            clip = false,
                                            sizeAnimationSpec = { _, _ ->
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            }
                                        )
                                    )
                            },
                            label = "voice_substate_morph"
                        ) { subState ->
                            when (subState) {
                                VoiceSubState.CONSENT -> {
                                    VoiceConsentPane(
                                        contentAlpha = 1f,
                                        onAccept = {
                                            viewModel.setGeminiConsentGiven(true)
                                            showInlineConsent = false
                                            if (apiKey.isBlank()) {
                                                showInlineApiKey = true
                                            } else {
                                                triggerVoiceMode()
                                            }
                                        },
                                        onDecline = {
                                            viewModel.setGeminiConsentGiven(false)
                                            cancelAndCollapseToNav()
                                        }
                                    )
                                }

                                VoiceSubState.API_KEY -> {
                                    VoiceApiKeyPane(
                                        currentKey = apiKey,
                                        contentAlpha = 1f,
                                        onSaveKey = { newKey ->
                                            viewModel.saveApiKey(newKey)
                                            showInlineApiKey = false
                                            triggerVoiceMode()
                                        },
                                        onCancel = {
                                            cancelAndCollapseToNav()
                                        }
                                    )
                                }

                                VoiceSubState.RECORDING -> {
                                    // Компактный интерфейс записи голоса внутри нижней капсулы:
                                    // Слева — микрофон, по центру — живой текст («Слушаю вас...») + MovingNeonGlow, справа — ✕
                                    val liveStatusText = when {
                                        isAnalyzingVoice && !isListening -> "✨ Давид разбирает операции..."
                                        recognizedText.isNotBlank() -> recognizedText
                                        partialText.isNotBlank() && partialText != "Говорите..." -> partialText
                                        else -> "Слушаю вас..."
                                    }

                                    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
                                    val micPulse by infiniteTransition.animateFloat(
                                        initialValue = 0.94f,
                                        targetValue = 1.08f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(750, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "mic_pulse_scale"
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Слева — иконка микрофона (нажатие завершает запись и отправляет на разбор)
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .graphicsLayer {
                                                    val s = if (isListening) micPulse else 1f
                                                    scaleX = s
                                                    scaleY = s
                                                }
                                                .shadow(
                                                    elevation = 10.dp,
                                                    shape = CircleShape,
                                                    ambientColor = NeonEmerald,
                                                    spotColor = NeonEmerald
                                                )
                                                .clip(CircleShape)
                                                .background(
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(NeonEmerald, NeonIndigo)
                                                    )
                                                )
                                                .clickable {
                                                    if (isListening) {
                                                        viewModel.stopVoiceRecordingAndProcess()
                                                    }
                                                }
                                                .testTag("voice_bar_mic_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isAnalyzingVoice && !isListening) {
                                                CircularProgressIndicator(
                                                    color = DarkBg,
                                                    strokeWidth = 2.5.dp,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Остановить запись",
                                                    tint = DarkBg,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // По центру — живой распознаваемый текст («Слушаю вас...») и переливающееся неоновое свечение (MovingNeonGlow)
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    if (isListening) {
                                                        viewModel.stopVoiceRecordingAndProcess()
                                                    }
                                                },
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = liveStatusText,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isAnalyzingVoice) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "ИИ...",
                                                        color = NeonEmerald,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            MovingNeonGlowVisualizer(
                                                isRecording = isListening || isAnalyzingVoice,
                                                rmsLevel = if (isListening) rmsDb else 4f,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Справа — крестик ✕ для отмены/завершения
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Slate800.copy(alpha = 0.9f))
                                                .border(1.dp, Slate700, CircleShape)
                                                .clickable { cancelAndCollapseToNav() }
                                                .testTag("voice_bar_cancel_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Отменить голосовой ввод",
                                                tint = Slate400,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                VoiceSubState.OPERATIONS -> {
                                    // Когда речь распознана и разобрана — эта же нижняя рамка плавно вырастает вверх,
                                    // отображая список распознанных операций и кнопку «Сохранить»
                                    val scrollState = rememberScrollState()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 520.dp)
                                            .verticalScroll(scrollState)
                                    ) {
                                        VoiceOperationsPane(
                                            viewModel = viewModel,
                                            editableList = editableList,
                                            categories = categories,
                                            selectedDate = selectedDate,
                                            contentAlpha = 1f,
                                            editingIndex = editingIndex,
                                            onEditingIndexChanged = { editingIndex = it },
                                            onDismissVoiceOperations = {
                                                cancelAndCollapseToNav()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MorphNavTabButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Emerald400 else Slate400,
        animationSpec = tween(220),
        label = "nav_tab_color"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "nav_tab_scale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 10.sp,
                fontFamily = ManropeFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VoiceConsentPane(
    contentAlpha: Float,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Indigo500.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Indigo500, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ИИ-ассистент Gemini",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Slate800.copy(alpha = 0.85f))
                    .border(1.dp, Slate700, CircleShape)
                    .clickable(onClick = onDecline),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Slate400,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Для автоматического распознавания категорий и сумм из вашего голоса данные отправляются в Google Gemini API.",
            color = Slate400,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400.copy(alpha = 0.15f)),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                .testTag("accept_voice_consent_button")
        ) {
            Text("Разрешить ИИ", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun VoiceApiKeyPane(
    currentKey: String,
    contentAlpha: Float,
    onSaveKey: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var keyText by remember(currentKey) { mutableStateOf(currentKey) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ключ Gemini API",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Получить ключ ↗",
                    color = Indigo500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        context.startActivity(intent)
                    }
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Slate800.copy(alpha = 0.85f))
                        .border(1.dp, Slate700, CircleShape)
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Для работы голосового ввода вставьте ваш бесплатный ключ из Google AI Studio:",
            color = Slate400,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = keyText,
            onValueChange = { keyText = it },
            placeholder = { Text("AIzaSy...", color = Slate400, fontSize = 13.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_api_key_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = Indigo500,
                unfocusedBorderColor = Slate800,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = {
                if (keyText.isNotBlank()) {
                    onSaveKey(keyText.trim())
                } else {
                    Toast.makeText(context, "Введите API ключ", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_voice_api_key_button")
        ) {
            Text("Сохранить и начать", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun Neon32BandVoiceVisualizer(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_32_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val smoothedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0.05f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "smoothed_amp"
    )

    val barBrush = remember {
        Brush.verticalGradient(
            colors = listOf(NeonEmerald, NeonCyan, NeonIndigo)
        )
    }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val spacing = (totalWidth / (barCount * 2.4f)).coerceAtLeast(1.5f)
        val barWidth = ((totalWidth - spacing * (barCount - 1)) / barCount).coerceAtLeast(2f)
        val centerIndex = (barCount - 1) / 2f

        for (i in 0 until barCount) {
            val normalizedDist = abs(i - centerIndex) / centerIndex
            val bellEnvelope = exp(-2.4f * normalizedDist * normalizedDist)
            val harmonic = 0.55f + 0.45f * sin(phase + i * 0.55f)
            val heightFraction = (0.14f + smoothedAmp * bellEnvelope * harmonic * 0.86f).coerceIn(0.12f, 1f)
            val barHeight = totalHeight * heightFraction
            val x = i * (barWidth + spacing)
            val y = (totalHeight - barHeight) / 2f

            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

