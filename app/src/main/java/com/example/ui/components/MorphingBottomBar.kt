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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import com.example.ui.theme.DarkCard
import com.example.ui.viewmodel.BottomCapsuleModal

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
 * - Когда `activeModal == BottomCapsuleModal.None`: отображает 4 вкладки (`Период`, `Долги`, `Цели`, `Отчет`)
 *   и встроенную внутрь рамки кнопку `+ Добавить` справа (на вкладке `Период`).
 * - Когда открыто любое модальное окно (`BottomCapsuleModal`):
 *   фоновый экран плавно затемняется полупрозрачным скримом (`Color.Black.copy(alpha = 0.65f)` с `animateFloatAsState`),
 *   иконки нижней навигации растворяются строго на месте внизу (`fadeOut(tween(120))`), а карточка нижней капсулы
 *   (`RoundedCornerShape(28.dp)`, темный фон `DarkCard`/`Slate900`, неоновая обводка `MovingNeonGlow`) с единой пружинной физикой
 *   `spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)` вырастает снизу вверх от стартовой высоты 68.dp:
 *   - Компактные формы (`wrapContentHeight()`)
 *   - Большие экраны со списками (`fillMaxHeight(0.88f)`)
 */
@Composable
fun MorphingBottomBar(
    viewModel: BudgetViewModel,
    currentMainTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedDate: String,
    showManualInput: Boolean = false,
    onDismissManualInput: () -> Unit = {},
    onOpenManualInput: () -> Unit = {},
    initialType: String = "expense",
    showNavigationTabs: Boolean = true,
    onOverlayActiveChanged: (Boolean) -> Unit = {},
    isDavidLiveActive: Boolean = false,
    davidLiveMinimizedCapsule: (@Composable () -> Unit)? = null,
    onDavidLiveBlocksVoiceInput: () -> Unit = {},
    modalContent: @Composable (BottomCapsuleModal) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val voiceManager = viewModel.voiceInputManager
    val isListening by voiceManager.isListening.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val partialText by voiceManager.partialText.collectAsState()
    val rmsDb by voiceManager.rmsDb.collectAsState()
    val errorState by voiceManager.errorState.collectAsState()

    val isVoiceActive by viewModel.isVoiceActive.collectAsState()
    val isAnalyzingVoice by viewModel.isAnalyzingVoice.collectAsState()
    val parsedOperations by viewModel.parsedVoiceOperations.collectAsState()
    val latestVoiceTickerEvent by viewModel.latestVoiceTickerEvent.collectAsState()
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val isConsentGiven by viewModel.isGeminiConsentGiven.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val activeModal by viewModel.activeModal.collectAsState()

    var showInlineConsent by remember { mutableStateOf(false) }
    var showInlineApiKey by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val editableList = remember { mutableStateListOf<ParsedVoiceOperation>() }

    // Сохраняем последнее открытое окно на время пружинной анимации сворачивания вниз
    var renderedModal by remember { mutableStateOf<BottomCapsuleModal>(BottomCapsuleModal.None) }
    if (activeModal != BottomCapsuleModal.None) {
        renderedModal = activeModal
    }
    val effectiveModal = if (activeModal != BottomCapsuleModal.None) activeModal else renderedModal

    // Мгновенный тактильный отклик в руку на каждый пойманный или скорректированный голосом расход/доход (toolCall)
    LaunchedEffect(latestVoiceTickerEvent?.id) {
        if (latestVoiceTickerEvent != null && activeModal is BottomCapsuleModal.VoiceInput) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(parsedOperations) {
        val ops = parsedOperations
        if (ops != null) {
            if (editableList.isEmpty()) {
                editableList.addAll(ops)
            } else if (ops.size > editableList.size) {
                for (i in editableList.indices) {
                    if (editableList[i] != ops[i] && editingIndex != i) {
                        editableList[i] = ops[i]
                    }
                }
                val newItems = ops.subList(editableList.size, ops.size)
                editableList.addAll(newItems)
            } else if (ops.size == editableList.size) {
                for (i in ops.indices) {
                    if (editableList[i] != ops[i] && editingIndex != i) {
                        editableList[i] = ops[i]
                    }
                }
            }
            if (ops.isNotEmpty()) {
                viewModel.openModal(BottomCapsuleModal.VoiceInput)
            }
        } else {
            editableList.clear()
            editingIndex = null
        }
    }

    // Синхронизация внешнего флага showManualInput с единым состоянием капсулы
    LaunchedEffect(showManualInput) {
        if (showManualInput && activeModal == BottomCapsuleModal.None) {
            viewModel.openModal(BottomCapsuleModal.ManualAddTransaction(initialType))
        } else if (!showManualInput && activeModal is BottomCapsuleModal.ManualAddTransaction) {
            viewModel.closeModal()
        }
    }

    // Если голосовой ввод завершился без распознанных операций и без открытых под-окон — возвращаемся к вкладкам
    LaunchedEffect(
        isListening,
        isAnalyzingVoice,
        isVoiceActive,
        parsedOperations,
        editableList.size,
        showInlineConsent,
        showInlineApiKey,
        activeModal
    ) {
        if (activeModal is BottomCapsuleModal.VoiceInput &&
            !isListening &&
            !isAnalyzingVoice &&
            !isVoiceActive &&
            parsedOperations.isNullOrEmpty() &&
            editableList.isEmpty() &&
            !showInlineConsent &&
            !showInlineApiKey
        ) {
            viewModel.closeModal()
            viewModel.setVoiceActive(false)
        }
    }

    val isExpandedOrVoice = activeModal != BottomCapsuleModal.None
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
        viewModel.closeModal()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.openModal(BottomCapsuleModal.VoiceInput)
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

    fun triggerVoiceMode(overrideConsentGiven: Boolean? = null, overrideApiKey: String? = null) {
        if (isDavidLiveActive) {
            Toast.makeText(
                context,
                "Сейчас активен разговор David Live — сначала завершите его или разверните нижнюю панель",
                Toast.LENGTH_SHORT
            ).show()
            onDavidLiveBlocksVoiceInput()
            return
        }
        if (showManualInput) {
            onDismissManualInput()
        }
        viewModel.openModal(BottomCapsuleModal.VoiceInput)
        val effectiveConsent = overrideConsentGiven ?: isConsentGiven
        if (!effectiveConsent) {
            showInlineConsent = true
            return
        }
        val effectiveKey = (overrideApiKey ?: apiKey).trim()
        if (effectiveKey.isBlank()) {
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
                voiceManager.onOperationsCaptured = null
                voiceManager.onLastOperationCorrected = null
                voiceManager.onChunkRecognized = null
                voiceManager.stopListening()
            }
        }
    }

    // Системная кнопка «Назад» плавно складывает морфинг-капсулу обратно в режим вкладок
    BackHandler(enabled = activeModal != BottomCapsuleModal.None) {
        if (activeModal is BottomCapsuleModal.VoiceInput && editingIndex != null) {
            editingIndex = null
        } else {
            cancelAndCollapseToNav()
        }
    }

    val isFullHeightModal = when (effectiveModal) {
        is BottomCapsuleModal.AllTransactions,
        is BottomCapsuleModal.SettingsHub,
        is BottomCapsuleModal.CategoryLimits,
        is BottomCapsuleModal.DavidChat -> true
        else -> false
    }

    val isCompactVoiceBar = activeModal is BottomCapsuleModal.VoiceInput &&
        editableList.isEmpty() && !showInlineConsent && !showInlineApiKey

    val isVoiceModal = effectiveModal is BottomCapsuleModal.VoiceInput

    val cornerRadius by animateDpAsState(
        targetValue = when {
            activeModal == BottomCapsuleModal.None -> 22.dp
            activeModal is BottomCapsuleModal.VoiceInput -> 36.dp
            else -> 28.dp
        },
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "morph_bar_corner"
    )

    // Неоновое переливающееся свечение рамки: фаза читается строго в фазе отрисовки (drawWithContent),
    // чтобы покадровое изменение градиента не вызывало рекомпозиций дочерних вкладок и текста.
    val needsAnimatedNeonBorder =
        activeModal != BottomCapsuleModal.None && activeModal !is BottomCapsuleModal.VoiceInput
    val glowPhaseState = if (needsAnimatedNeonBorder) {
        val glowTransition = rememberInfiniteTransition(label = "capsule_neon_glow")
        glowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "capsule_neon_phase"
        )
    } else {
        null
    }

    val staticLiveMinBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Emerald400.copy(alpha = 0.58f),
                NeonCyan.copy(alpha = 0.48f),
                Emerald400.copy(alpha = 0.58f)
            )
        )
    }
    val staticDefaultBrush = remember {
        Brush.horizontalGradient(listOf(GlassHighlight, GlassHighlight))
    }
    val staticVoiceBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Emerald400.copy(alpha = 0.45f),
                Color(0xFF10B981).copy(alpha = 0.38f),
                Color(0xFF06B6D4).copy(alpha = 0.45f)
            )
        )
    }
    val animatedGlowColors = remember {
        listOf(
            Emerald400.copy(alpha = 0.65f),
            NeonCyan.copy(alpha = 0.45f),
            Indigo500.copy(alpha = 0.55f)
        )
    }
    val borderWidth = when {
        activeModal == BottomCapsuleModal.None -> 1.dp
        activeModal is BottomCapsuleModal.VoiceInput -> 1.dp
        else -> 1.4.dp
    }

    // Плавное затемнение фонового экрана (Color.Black.copy(alpha = 0.65f) с animateFloatAsState)
    val showScrim = activeModal != BottomCapsuleModal.None && !isCompactVoiceBar
    val scrimAlpha by animateFloatAsState(
        targetValue = if (showScrim) 0.65f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "morph_scrim_alpha"
    )

    if (!showNavigationTabs && activeModal == BottomCapsuleModal.None && davidLiveMinimizedCapsule == null) {
        return
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val fullModalHeight = ((maxHeight - 24.dp) * 0.88f).coerceAtLeast(320.dp)
        val navBarHeightPx = with(density) { 68.dp.roundToPx() }

        if (scrimAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        cancelAndCollapseToNav()
                    }
            )
        }

        // Сама нижняя морфинг-капсула, жестко привязанная к низу экрана (Alignment.BottomCenter)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .shadow(
                    elevation = if (activeModal == BottomCapsuleModal.None) 18.dp else 28.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    ambientColor = when {
                        activeModal is BottomCapsuleModal.VoiceInput -> Color(0xFF00E5FF)
                        activeModal != BottomCapsuleModal.None -> NeonEmerald
                        davidLiveMinimizedCapsule != null -> NeonEmerald.copy(alpha = 0.65f)
                        else -> Color.Black
                    },
                    spotColor = when {
                        activeModal is BottomCapsuleModal.VoiceInput -> Color(0xFF38BDF8)
                        activeModal != BottomCapsuleModal.None -> NeonIndigo
                        davidLiveMinimizedCapsule != null -> NeonCyan.copy(alpha = 0.65f)
                        else -> Color.Black
                    }
                )
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    when {
                        activeModal == BottomCapsuleModal.None -> ObsidianSurface.copy(alpha = 0.96f)
                        isVoiceModal -> Color(0xFF080C14)
                        else -> DarkCard.copy(alpha = 0.99f)
                    }
                )
                .drawWithContent {
                    drawContent()
                    val strokeWidthPx = borderWidth.toPx()
                    val halfStroke = strokeWidthPx / 2f
                    val cornerPx = (cornerRadius.toPx() - halfStroke).coerceAtLeast(0f)
                    val phase = glowPhaseState?.value ?: 0f
                    val activeBrush = when {
                        activeModal == BottomCapsuleModal.None && davidLiveMinimizedCapsule != null -> staticLiveMinBrush
                        activeModal == BottomCapsuleModal.None -> staticDefaultBrush
                        activeModal is BottomCapsuleModal.VoiceInput -> staticVoiceBrush
                        else -> Brush.linearGradient(
                            colors = animatedGlowColors,
                            start = Offset.Zero,
                            end = Offset(800f * (0.35f + phase * 0.65f), 800f)
                        )
                    }
                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size(
                            width = (size.width - strokeWidthPx).coerceAtLeast(0f),
                            height = (size.height - strokeWidthPx).coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = strokeWidthPx)
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // 1. Нижняя капсула (height = 68.dp), строго зафиксированная внизу (Alignment.BottomCenter).
            // При свернутом David Live отображает компактную голосовую капсулу Давида прямо внутри нижней панели
            // вместо вкладок навигации; при завершении сессии плавно возвращает 4 вкладки + кнопку «+ Добавить».
            AnimatedVisibility(
                visible = activeModal == BottomCapsuleModal.None,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(68.dp),
                enter = fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 100)),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                label = "bottom_nav_icons_fade"
            ) {
                AnimatedContent(
                    targetState = davidLiveMinimizedCapsule != null,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "bottom_capsule_live_vs_tabs"
                ) { isLiveMinimizedInBar ->
                    if (isLiveMinimizedInBar && davidLiveMinimizedCapsule != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            davidLiveMinimizedCapsule.invoke()
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .padding(horizontal = 6.dp, vertical = 5.dp),
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

                            // Встроенная симметричная кнопка `+ Добавить` внутри капсулы навигации (ТОЛЬКО на вкладке Период)
                            AnimatedVisibility(
                                visible = currentMainTab == 0,
                                enter = expandHorizontally(
                                    expandFrom = Alignment.End,
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkHorizontally(
                                    shrinkTowards = Alignment.End,
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
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
                                            .pointerInput(initialType) {
                                                detectTapGestures(
                                                    onTap = {
                                                        onOpenManualInput()
                                                        viewModel.openModal(
                                                            BottomCapsuleModal.ManualAddTransaction(initialType)
                                                        )
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
                }
            }

            // 2. Контент модального окна: плавно вырастает снизу вверх от стартовой высоты панели (68.dp)
            // до целевой высоты окна (wrapContentHeight или 0.88f экрана для больших окон) с единой пружинной физикой.
            AnimatedVisibility(
                visible = activeModal != BottomCapsuleModal.None,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    initialHeight = { navBarHeightPx },
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 60)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    targetHeight = { navBarHeightPx },
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeOut(animationSpec = tween(durationMillis = 120)),
                label = "bottom_capsule_modal_expand"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.82f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        .then(
                            if (isFullHeightModal) {
                                Modifier.height(fullModalHeight)
                            } else {
                                Modifier
                                    .heightIn(max = fullModalHeight)
                                    .wrapContentHeight(align = Alignment.Bottom)
                            }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedContent(
                        targetState = effectiveModal,
                        contentAlignment = Alignment.BottomCenter,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220, delayMillis = 40)) togetherWith
                                fadeOut(animationSpec = tween(120)))
                                .using(
                                    SizeTransform(
                                        clip = false,
                                        sizeAnimationSpec = { _, _ ->
                                            spring(
                                                dampingRatio = 0.82f,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        }
                                    )
                                )
                        },
                        label = "bottom_capsule_modal_switch"
                    ) { modal ->
                        when (modal) {
                            BottomCapsuleModal.None -> {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                )
                            }

                            is BottomCapsuleModal.ManualAddTransaction -> {
                                val scrollState = rememberScrollState()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 540.dp)
                                        .verticalScroll(scrollState)
                                ) {
                                    VoiceManualInputPane(
                                        viewModel = viewModel,
                                        categories = categories,
                                        selectedDate = selectedDate,
                                        initialType = modal.initialType,
                                        contentAlpha = 1f,
                                        onDismiss = {
                                            onDismissManualInput()
                                            viewModel.closeModal()
                                        }
                                    )
                                }
                            }

                            is BottomCapsuleModal.VoiceInput -> {
                                val voiceSubState = when {
                                    showInlineConsent -> VoiceSubState.CONSENT
                                    showInlineApiKey -> VoiceSubState.API_KEY
                                    editableList.isNotEmpty() -> VoiceSubState.OPERATIONS
                                    else -> VoiceSubState.RECORDING
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF080C14)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    // Мягкое второстепенное северное сияние вдоль нижней кромки капсулы (Volumetric Liquid Aurora Glow)
                                    VolumetricLiquidAuroraGlow(
                                        isRecording = isListening || isAnalyzingVoice,
                                        rmsLevel = if (isListening) rmsDb else if (isAnalyzingVoice) 4.2f else 1.8f,
                                        cornerRadius = 36.dp,
                                        maxAuroraHeight = if (voiceSubState == VoiceSubState.RECORDING) null else 190.dp,
                                        showGlassBorder = false,
                                        fillBackground = false,
                                        auroraIntensityScale = 0.52f,
                                        modifier = Modifier.matchParentSize()
                                    )

                                    AnimatedContent(
                                        targetState = voiceSubState,
                                        contentAlignment = Alignment.BottomCenter,
                                        transitionSpec = {
                                            (fadeIn(tween(200)) togetherWith fadeOut(tween(140)))
                                                .using(
                                                    SizeTransform(
                                                        clip = false,
                                                        sizeAnimationSpec = { _, _ ->
                                                            spring(
                                                                dampingRatio = 0.82f,
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
                                                            triggerVoiceMode(overrideConsentGiven = true)
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
                                                        val sanitized = com.example.data.api.GeminiModelConfig.sanitizeApiKey(newKey)
                                                        if (sanitized.isBlank()) {
                                                            Toast.makeText(
                                                                context,
                                                                "Введите корректный API ключ Gemini",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        } else {
                                                            viewModel.saveApiKey(sanitized)
                                                            showInlineApiKey = false
                                                            triggerVoiceMode(
                                                                overrideConsentGiven = true,
                                                                overrideApiKey = sanitized
                                                            )
                                                        }
                                                    },
                                                    onCancel = {
                                                        cancelAndCollapseToNav()
                                                    }
                                                )
                                            }

                                            VoiceSubState.RECORDING -> {
                                                val liveStatusText = when {
                                                    isAnalyzingVoice && !isListening -> "✨ Давид разбирает операции..."
                                                    recognizedText.isNotBlank() -> recognizedText
                                                    partialText.isNotBlank() && partialText != "Говорите..." -> partialText
                                                    else -> "Слушаю..."
                                                }

                                                val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
                                                val micPulse by infiniteTransition.animateFloat(
                                                    initialValue = 0.96f,
                                                    targetValue = 1.05f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(900, easing = FastOutSlowInEasing),
                                                        repeatMode = RepeatMode.Reverse
                                                    ),
                                                    label = "mic_pulse_scale"
                                                )

                                                // Капсула голосового ввода (height = 84.dp, скругление 36.dp) с 32-полосным спектральным визуализатором
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(84.dp)
                                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(46.dp)
                                                            .graphicsLayer {
                                                                val s = if (isListening) micPulse else 1f
                                                                scaleX = s
                                                                scaleY = s
                                                            }
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF10B981).copy(alpha = 0.16f))
                                                            .border(
                                                                width = 1.2.dp,
                                                                brush = Brush.verticalGradient(
                                                                    colors = listOf(
                                                                        Emerald400.copy(alpha = 0.65f),
                                                                        Color(0xFF06B6D4).copy(alpha = 0.55f)
                                                                    )
                                                                ),
                                                                shape = CircleShape
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
                                                                color = Emerald400,
                                                                strokeWidth = 2.2.dp,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.Mic,
                                                                contentDescription = "Остановить запись",
                                                                tint = Emerald400,
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(12.dp))

                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                            .clickable {
                                                                if (isListening) {
                                                                    viewModel.stopVoiceRecordingAndProcess()
                                                                }
                                                            },
                                                        verticalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = liveStatusText,
                                                                color = Color.White.copy(alpha = 0.94f),
                                                                fontSize = 12.sp,
                                                                fontFamily = ManropeFontFamily,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false),
                                                                style = TextStyle(
                                                                    shadow = Shadow(
                                                                        color = Color.Black.copy(alpha = 0.75f),
                                                                        offset = Offset(0f, 1.5f),
                                                                        blurRadius = 6f
                                                                    )
                                                                )
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            val tickerEvent = latestVoiceTickerEvent
                                                            if (tickerEvent != null) {
                                                                val tickerAccent = if (tickerEvent.isCorrection) {
                                                                    Color(0xFF06B6D4)
                                                                } else {
                                                                    Emerald400
                                                                }
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(10.dp))
                                                                        .background(tickerAccent.copy(alpha = 0.16f))
                                                                        .border(
                                                                            width = 1.dp,
                                                                            color = tickerAccent.copy(alpha = 0.55f),
                                                                            shape = RoundedCornerShape(10.dp)
                                                                        )
                                                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = tickerEvent.formattedChipText,
                                                                        color = tickerAccent,
                                                                        fontSize = 10.sp,
                                                                        fontFamily = ManropeFontFamily,
                                                                        fontWeight = FontWeight.ExtraBold,
                                                                        maxLines = 1
                                                                    )
                                                                }
                                                            } else {
                                                                Text(
                                                                    text = if (isAnalyzingVoice) "ИИ разбор" else "Нажмите для стопа",
                                                                    color = Color(0xFF94A3B8).copy(alpha = 0.78f),
                                                                    fontSize = 10.sp,
                                                                    fontFamily = ManropeFontFamily,
                                                                    fontWeight = FontWeight.Medium,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }

                                                        // 32-полосный горизонтальный спектральный визуализатор реального голоса (FFT + Hann)
                                                        SpectralVoiceVisualizer(
                                                            isRecording = isListening || isAnalyzingVoice,
                                                            rmsLevel = rmsDb,
                                                            barCount = 32,
                                                            compact = false,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(40.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    // Аккуратная круглая кнопка закрытия ✕ справа
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF080C14).copy(alpha = 0.55f))
                                                            .border(
                                                                width = 1.dp,
                                                                color = Color.White.copy(alpha = 0.20f),
                                                                shape = CircleShape
                                                            )
                                                            .clickable { cancelAndCollapseToNav() }
                                                            .testTag("voice_bar_cancel_button"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Отменить голосовой ввод",
                                                            tint = Color.White.copy(alpha = 0.90f),
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            VoiceSubState.OPERATIONS -> {
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

                            else -> {
                                modalContent(modal)
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
    var isKeyVisible by remember { mutableStateOf(false) }

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
            visualTransformation = if (isKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation('•')
            },
            trailingIcon = {
                IconButton(
                    onClick = { isKeyVisible = !isKeyVisible },
                    modifier = Modifier.testTag("toggle_voice_api_key_visibility")
                ) {
                    Icon(
                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isKeyVisible) "Скрыть ключ" else "Показать ключ",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
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
