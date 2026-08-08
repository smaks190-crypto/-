package com.example.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.db.CategoryEntity
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.viewmodel.BudgetViewModel
import kotlinx.coroutines.launch

enum class OverlayState { CONSENT, API_KEY, MANUAL_INPUT, VOICE_OPERATIONS, COLLAPSED }

@Composable
fun VoiceRecordingOverlay(
    viewModel: BudgetViewModel,
    selectedDate: String,
    showManualInput: Boolean = false,
    onDismissManualInput: () -> Unit = {},
    onOpenManualInput: () -> Unit = {},
    initialType: String = "expense",
    modifier: Modifier = Modifier,
    onRequireConsent: ((String) -> Unit)? = null,
    onOverlayActiveChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val voiceManager = viewModel.voiceInputManager

    val isConsentGiven by viewModel.isGeminiConsentGiven.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    var showConsentRequested by remember { mutableStateOf(false) }
    var showPolicyInCard by remember { mutableStateOf(false) }
    var showApiKeyRequested by remember { mutableStateOf(false) }
    var tempApiKeyText by remember { mutableStateOf(apiKey) }

    var isRecordingLocked by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecordingLocked = true
            viewModel.startVoiceRecording(context)
        } else {
            viewModel.setVoiceActive(false)
        }
    }

    val isListening by voiceManager.isListening.collectAsState()
    val recognizedText by voiceManager.recognizedText.collectAsState()
    val partialText by voiceManager.partialText.collectAsState()
    val rmsDb by voiceManager.rmsDb.collectAsState()
    val voskStatus by voiceManager.voskStatus.collectAsState()
    val voskProgress by voiceManager.voskProgress.collectAsState()

    val isAnalyzingVoice by viewModel.isAnalyzingVoice.collectAsState()
    val manualText by viewModel.manualText.collectAsState()

    val parsedVoiceOperations by viewModel.parsedVoiceOperations.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val activeText = when {
        partialText.isNotBlank() -> partialText
        recognizedText.isNotBlank() -> recognizedText
        else -> manualText
    }

    val isVoiceActiveFromModel by viewModel.isVoiceActive.collectAsState()
    val isVoiceActive = isVoiceActiveFromModel || isListening || isAnalyzingVoice

    // Нормализация громкости для реактивного свечения всей капсулы
    val normalizedAmplitude = remember(rmsDb) {
        (rmsDb / 12f).coerceIn(0f, 1f)
    }

    val isConsentNeeded = !isConsentGiven && (showManualInput || showConsentRequested || isVoiceActive)
    val isApiKeyNeeded = isConsentGiven && (showApiKeyRequested || (apiKey.isBlank() && (showManualInput || isVoiceActive)))

    LaunchedEffect(isVoiceActive, isConsentGiven, apiKey) {
        if (isVoiceActive && (!isConsentGiven || apiKey.isBlank())) {
            viewModel.cancelVoiceRecording()
            if (!isConsentGiven) {
                showConsentRequested = true
            } else {
                showApiKeyRequested = true
            }
        } else if (!isVoiceActive) {
            isRecordingLocked = false
        }
    }

    val fabGestureModifier = Modifier.pointerInput(context, isConsentGiven, apiKey, showManualInput, showConsentRequested, showApiKeyRequested) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!isConsentGiven) {
                down.consume()
                if (showConsentRequested) {
                    showConsentRequested = false
                    if (showManualInput) onDismissManualInput()
                } else {
                    showConsentRequested = true
                }
                return@awaitEachGesture
            } else if (apiKey.isBlank()) {
                down.consume()
                if (showApiKeyRequested) {
                    showApiKeyRequested = false
                    if (showManualInput) onDismissManualInput()
                } else {
                    tempApiKeyText = ""
                    showApiKeyRequested = true
                }
                return@awaitEachGesture
            }

            val startY = down.position.y
            val dragLockThreshold = 60.dp.toPx()
            val longPressTimeout = 220L

            if (isRecordingLocked) {
                down.consume()
                viewModel.cancelVoiceRecording()
                isRecordingLocked = false
                return@awaitEachGesture
            }

            var isRecordingStarted = false
            var isLocked = false

            val longPressTriggered = withTimeoutOrNull(longPressTimeout) {
                var currentDown = down
                while (currentDown.pressed) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) return@withTimeoutOrNull false
                    val deltaY = startY - change.position.y
                    if (deltaY > dragLockThreshold) return@withTimeoutOrNull true
                    currentDown = change
                }
                false
            }

            if (longPressTriggered == false) {
                if (isVoiceActive) {
                    viewModel.cancelVoiceRecording()
                } else {
                    onOpenManualInput()
                }
            } else {
                val hasPerm = try {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                } catch (_: Throwable) { false }

                if (hasPerm) {
                    try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                    viewModel.startVoiceRecording(context)
                    isRecordingStarted = true
                } else {
                    viewModel.setVoiceActive(true)
                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }

                if (isRecordingStarted) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            if (!isLocked) {
                                viewModel.stopVoiceRecordingAndProcess()
                            }
                            break
                        }

                        val deltaY = startY - change.position.y
                        if (deltaY > dragLockThreshold && !isLocked) {
                            isLocked = true
                            isRecordingLocked = true
                            try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                        }
                        change.consume()
                    }
                }
            }
        }
    }

    val isEditingOperations = !parsedVoiceOperations.isNullOrEmpty()
    val isExpandedCard = isConsentNeeded || isApiKeyNeeded || showManualInput || isEditingOperations || isVoiceActive

    LaunchedEffect(isExpandedCard) {
        onOverlayActiveChanged?.invoke(isExpandedCard)
    }

    val fabRotation by animateFloatAsState(
        targetValue = if (isExpandedCard) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "fab_rotation"
    )

    var isClosingContentFade by remember { mutableStateOf(false) }
    var editingIndex by remember(parsedVoiceOperations) { mutableStateOf<Int?>(null) }

    val cardWidthAnim = remember { Animatable(56f) }
    val cardHeightAnim = remember { Animatable(56f) }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()

    val desiredWidth = when {
        isConsentNeeded || isApiKeyNeeded || showManualInput || isEditingOperations -> (screenWidthDp - 32f).coerceAtLeast(300f)
        isVoiceActive -> (screenWidthDp - 48f).coerceAtLeast(280f)
        else -> 56f
    }

    val desiredHeight = when {
        isConsentNeeded -> if (showPolicyInCard) 330f.coerceAtMost(screenHeightDp - 60f) else 230f.coerceAtMost(screenHeightDp - 60f)
        isApiKeyNeeded -> 430f.coerceAtMost(screenHeightDp - 60f)
        showManualInput -> 442f.coerceAtMost(screenHeightDp - 60f)
        isEditingOperations -> {
            if (editingIndex != null) {
                460f.coerceAtMost(screenHeightDp - 60f)
            } else {
                val count = parsedVoiceOperations?.size ?: 1
                (170f + count * 76f).coerceIn(240f, screenHeightDp - 60f)
            }
        }
        isVoiceActive -> 56f
        else -> 56f
    }

    val showAsExpanded = (cardWidthAnim.value > 57f || cardHeightAnim.value > 57f)
    val isHorizontallyExpanded = (cardWidthAnim.value > 57f)
    val isVerticallyExpanded = (cardHeightAnim.value > 57f)

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(desiredWidth, desiredHeight, isClosingContentFade, isConsentNeeded, isApiKeyNeeded, showManualInput, isEditingOperations, isVoiceActive) {
        if (!isClosingContentFade) {
            val animSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            val isTargetExpanded = isConsentNeeded || isApiKeyNeeded || showManualInput || isEditingOperations || isVoiceActive
            if (isTargetExpanded) {
                cardWidthAnim.animateTo(desiredWidth, animSpec)
                cardHeightAnim.animateTo(desiredHeight, animSpec)
            } else {
                cardHeightAnim.animateTo(56f, animSpec)
                cardWidthAnim.animateTo(56f, animSpec)
            }
        }
    }

    val handleDismissManualInput = {
        if (!isClosingContentFade) {
            coroutineScope.launch {
                isClosingContentFade = true
                kotlinx.coroutines.delay(140)
                val collapseSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                cardHeightAnim.animateTo(56f, collapseSpec)
                cardWidthAnim.animateTo(56f, collapseSpec)
                onDismissManualInput()
                isClosingContentFade = false
            }
        }
    }

    val handleDismissVoiceOperations = {
        if (!isClosingContentFade) {
            coroutineScope.launch {
                isClosingContentFade = true
                kotlinx.coroutines.delay(140)
                val collapseSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                cardHeightAnim.animateTo(56f, collapseSpec)
                cardWidthAnim.animateTo(56f, collapseSpec)
                viewModel.cancelVoiceRecording()
                viewModel.clearParsedVoiceOperations()
                viewModel.setVoiceActive(false)
                isClosingContentFade = false
            }
        }
    }

    val surfaceColor by animateColorAsState(
        targetValue = if (showAsExpanded) DarkBg.copy(alpha = 0.94f) else Indigo500,
        animationSpec = tween(300),
        label = "surface_color"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if ((isVoiceActive || cardHeightAnim.value > 120f) && !isClosingContentFade) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "content_alpha"
    )

    val editableList = remember(parsedVoiceOperations) {
        mutableStateListOf<ParsedVoiceOperation>().apply {
            parsedVoiceOperations?.let { addAll(it) }
        }
    }

    val isDetailEditing = isEditingOperations && editingIndex != null

    val fabIcon = if (isDetailEditing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Add
    val fabContentDescription = when {
        isDetailEditing -> "Назад"
        showAsExpanded -> "Отмена"
        isVoiceActive -> "Отмена записи"
        else -> "Добавить"
    }
    val fabRotationAngle = if (isDetailEditing) 0f else fabRotation
    val fabTint = when {
        isVoiceActive -> Rose500
        !showAsExpanded -> DarkBg
        isDetailEditing -> Slate400
        else -> Rose500
    }
    val fabTestTag = when {
        !showAsExpanded -> "fab_add_button"
        showManualInput -> "close_manual_input_fab"
        isEditingOperations -> "close_voice_operations_fab"
        else -> "unified_fab"
    }

    val handleFabClick = {
        when {
            isConsentNeeded -> {
                showConsentRequested = false
                showPolicyInCard = false
                if (showManualInput) handleDismissManualInput()
            }
            isApiKeyNeeded -> {
                showApiKeyRequested = false
                if (showManualInput) handleDismissManualInput()
            }
            showManualInput -> handleDismissManualInput()
            isEditingOperations -> {
                if (editingIndex != null) {
                    editingIndex = null
                } else {
                    handleDismissVoiceOperations()
                }
            }
            isVoiceActive -> viewModel.cancelVoiceRecording()
            else -> {
                if (!isConsentGiven) {
                    showConsentRequested = true
                } else if (apiKey.isBlank()) {
                    tempApiKeyText = ""
                    showApiKeyRequested = true
                } else {
                    onOpenManualInput()
                }
            }
        }
    }

    val isManualOrEditing = isConsentNeeded || isApiKeyNeeded || showManualInput || isEditingOperations

    val fabPaddingEnd by animateDpAsState(
        targetValue = if (isHorizontallyExpanded && isVerticallyExpanded && isManualOrEditing) 16.dp else 0.dp,
        animationSpec = tween(300),
        label = "fab_padding_end"
    )
    val fabPaddingBottom by animateDpAsState(
        targetValue = if (isVerticallyExpanded && isManualOrEditing) 12.dp else 0.dp,
        animationSpec = tween(300),
        label = "fab_padding_bottom"
    )
    val boxEndPadding by animateDpAsState(
        targetValue = if (isHorizontallyExpanded && isManualOrEditing) 0.dp else 16.dp,
        animationSpec = tween(300),
        label = "box_end_padding"
    )
    val boxBottomPadding by animateDpAsState(
        targetValue = if (isVerticallyExpanded && isManualOrEditing) 0.dp else 12.dp,
        animationSpec = tween(300),
        label = "box_bottom_padding"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (showAsExpanded) 1f else 0f,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "border_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "border_gradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "offset"
    )

    val dynamicGradient = Brush.linearGradient(
        colors = listOf(
            Indigo500.copy(alpha = borderAlpha),
            Emerald400.copy(alpha = borderAlpha),
            Rose500.copy(alpha = borderAlpha),
            Indigo500.copy(alpha = borderAlpha)
        ),
        start = Offset(offset, offset), end = Offset(offset + 600f, offset + 600f),
        tileMode = TileMode.Repeated
    )

    val currentOverlayState = when {
        isConsentNeeded -> OverlayState.CONSENT
        isApiKeyNeeded -> OverlayState.API_KEY
        showManualInput -> OverlayState.MANUAL_INPUT
        isEditingOperations -> OverlayState.VOICE_OPERATIONS
        else -> OverlayState.COLLAPSED
    }

    Box(
        modifier = modifier.padding(bottom = boxBottomPadding, end = boxEndPadding),
        contentAlignment = Alignment.BottomEnd
    ) {
        // ОБОЛОЧКА С ПОЛНЫМ НЕОНОВЫМ СВЕЧЕНИЕМ ВСЕЙ КАПСУЛЫ
        FullCapsuleNeonGlow(
            isRecording = isVoiceActive || isListening,
            amplitude = normalizedAmplitude,
            widthDp = cardWidthAnim.value,
            heightDp = cardHeightAnim.value
        ) {
            Box(
                modifier = Modifier
                    .width(cardWidthAnim.value.dp)
                    .height(cardHeightAnim.value.dp)
                    .shadow(
                        elevation = if (showAsExpanded) (24 * borderAlpha).dp else 12.dp,
                        shape = RoundedCornerShape(28.dp),
                        clip = false
                    )
                    .background(surfaceColor, RoundedCornerShape(28.dp))
                    .border(width = 2.dp, brush = dynamicGradient, shape = RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.BottomEnd
            ) {
                AnimatedContent(
                    targetState = currentOverlayState,
                    label = "overlay_content",
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
                ) { overlayState ->
                    when (overlayState) {
                        OverlayState.CONSENT -> {
                            ConsentOverlayContent(
                                showPolicyInCard = showPolicyInCard,
                                onTogglePolicy = { showPolicyInCard = !showPolicyInCard },
                                onAccept = {
                                    viewModel.setGeminiConsentGiven(true)
                                    showConsentRequested = false
                                    showPolicyInCard = false
                                    tempApiKeyText = apiKey
                                    showApiKeyRequested = true
                                }
                            )
                        }
                        OverlayState.API_KEY -> {
                            ApiKeyOverlayContent(
                                tempApiKeyText = tempApiKeyText,
                                onApiKeyChange = { tempApiKeyText = it },
                                onSaveKey = {
                                    val keyToSave = tempApiKeyText.trim()
                                    if (keyToSave.isNotBlank()) {
                                        viewModel.saveApiKey(keyToSave)
                                        showApiKeyRequested = false
                                        onOpenManualInput()
                                    }
                                }
                            )
                        }
                        OverlayState.MANUAL_INPUT -> {
                            ManualInputOverlayContent(
                                initialType = initialType,
                                selectedDate = selectedDate,
                                categories = categories,
                                viewModel = viewModel,
                                contentAlpha = contentAlpha,
                                onDismiss = handleDismissManualInput
                            )
                        }
                        OverlayState.VOICE_OPERATIONS -> {
                            VoiceOperationsOverlayContent(
                                editableList = editableList,
                                editingIndex = editingIndex,
                                categories = categories,
                                viewModel = viewModel,
                                selectedDate = selectedDate,
                                contentAlpha = contentAlpha,
                                onSelectEditingIndex = { editingIndex = it },
                                onDismiss = handleDismissVoiceOperations
                            )
                        }
                        OverlayState.COLLAPSED -> {
                            CollapsedVoiceBarContent(
                                isVoiceActive = isVoiceActive,
                                isAnalyzingVoice = isAnalyzingVoice,
                                isRecordingLocked = isRecordingLocked,
                                voskStatus = voskStatus,
                                voskProgress = voskProgress,
                                activeText = activeText,
                                isListening = isListening,
                                onStopClick = { viewModel.stopVoiceRecordingAndProcess() }
                            )
                        }
                    }
                }
            }
        }

        // КНОПКА УПРАВЛЕНИЯ СПРАВА
        FABContainer(
            modifier = Modifier.padding(bottom = fabPaddingBottom, end = fabPaddingEnd),
            fabIcon = fabIcon,
            fabIconRotation = fabRotationAngle,
            fabTint = fabTint,
            fabContentDescription = fabContentDescription,
            surfaceColor = surfaceColor,
            isClickable = isConsentNeeded || isApiKeyNeeded || showManualInput || isEditingOperations || isVoiceActive,
            onClick = handleFabClick,
            gestureModifier = fabGestureModifier,
            testTag = fabTestTag,
            showAsExpanded = showAsExpanded,
            isDetailEditing = isDetailEditing,
            isVoiceActive = isVoiceActive
        )
    }
}

// КОМПОНЕНТ ПОЛНОГО НЕОНОВОГО СВЕЧЕНИЯ ВСЕЙ КАПСУЛЫ
@Composable
fun FullCapsuleNeonGlow(
    isRecording: Boolean,
    amplitude: Float, // Громкость 0.0f..1.0f
    widthDp: Float,
    heightDp: Float,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CapsuleNeonRotate")
    val rotationPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CapsulePhase"
    )

    // Динамический масштаб внешнего неонового поля
    val glowScale by animateFloatAsState(
        targetValue = if (isRecording) 1.05f + (amplitude * 0.12f) else 1.0f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "CapsuleGlowScale"
    )

    val neonColors = if (isRecording) {
        listOf(
            Color(0xFFEC4899), // Pink
            Color(0xFF8B5CF6), // Purple
            Color(0xFF06B6D4), // Cyan
            Color(0xFF10B981), // Emerald
            Color(0xFFEC4899)
        )
    } else {
        listOf(Color(0xFF10B981).copy(alpha = 0.2f), Color(0xFF059669).copy(alpha = 0.1f))
    }

    Box(
        contentAlignment = Alignment.Center
    ) {
        // Внешнее широкое размытое свечение вокруг ВСЕЙ капсулы
        if (isRecording) {
            Box(
                modifier = Modifier
                    .width((widthDp + 24f).dp)
                    .height((heightDp + 24f).dp)
                    .scale(glowScale)
                    .blur(24.dp)
                    .background(
                        brush = Brush.sweepGradient(neonColors),
                        shape = RoundedCornerShape(36.dp)
                    )
            )

            // Внутренний более яркий контур свечения
            Box(
                modifier = Modifier
                    .width((widthDp + 8f).dp)
                    .height((heightDp + 8f).dp)
                    .scale(glowScale)
                    .blur(10.dp)
                    .background(
                        brush = Brush.linearGradient(neonColors),
                        shape = RoundedCornerShape(32.dp)
                    )
            )
        }

        content()
    }
}

// Вспомогательный UI-компонент FAB
@Composable
private fun FABContainer(
    modifier: Modifier = Modifier,
    fabIcon: androidx.compose.ui.graphics.vector.ImageVector,
    fabIconRotation: Float,
    fabTint: Color,
    fabContentDescription: String?,
    surfaceColor: Color,
    isClickable: Boolean,
    onClick: () -> Unit,
    gestureModifier: Modifier,
    testTag: String,
    showAsExpanded: Boolean,
    isDetailEditing: Boolean,
    isVoiceActive: Boolean
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(surfaceColor)
            .then(
                if (showAsExpanded) {
                    Modifier.border(
                        width = 1.dp,
                        color = if (isDetailEditing) Slate400.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                } else if (isVoiceActive) {
                    Modifier.border(width = 1.dp, color = Rose500.copy(alpha = 0.4f), shape = CircleShape)
                } else {
                    Modifier
                }
            )
            .then(
                if (isClickable) {
                    Modifier.clickable { onClick() }
                } else {
                    gestureModifier
                }
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = fabIcon,
            contentDescription = fabContentDescription,
            tint = fabTint,
            modifier = Modifier.rotate(fabIconRotation)
        )
    }
}

// Содержимое плашки при прослушивании
@Composable
private fun CollapsedVoiceBarContent(
    isVoiceActive: Boolean,
    isAnalyzingVoice: Boolean,
    isRecordingLocked: Boolean,
    voskStatus: String?,
    voskProgress: Float?,
    activeText: String,
    isListening: Boolean,
    onStopClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedVisibility(
            visible = isVoiceActive,
            enter = fadeIn(animationSpec = tween(250)) + slideInHorizontally { -it / 4 },
            exit = fadeOut(animationSpec = tween(150)) + slideOutHorizontally { -it / 4 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 68.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onStopClick() },
                contentAlignment = Alignment.CenterStart
            ) {
                if (isAnalyzingVoice) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Indigo500,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Анализ ИИ...", color = Indigo500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecordingLocked) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Rose500, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        val statusText = when (voskStatus) {
                            "DOWNLOADING" -> "Загрузка модели (${((voskProgress ?: 0f) * 100).toInt()}%)"
                            "EXTRACTING" -> "Настройка VOSK..."
                            else -> "Слушаю..."
                        }
                        Text(
                            text = statusText,
                            color = if (voskStatus == "DOWNLOADING" || voskStatus == "EXTRACTING") Emerald400 else Rose500,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        if (activeText.isNotBlank() && !isListening) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "«$activeText»",
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// Содержимое карточек поверхностей (Согласие, Ключ, Форма)
@Composable
private fun ConsentOverlayContent(
    showPolicyInCard: Boolean,
    onTogglePolicy: () -> Unit,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Согласие на ИИ-обработку", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (showPolicyInCard) "Назад" else "Политика",
                    color = Indigo500,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onTogglePolicy() }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Для распознавания голоса и ввода транзакций данные обрабатываются локально и передаются в Gemini API.",
                color = Slate400,
                fontSize = 12.sp
            )
        }

        Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = DarkBg),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Принять", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ApiKeyOverlayContent(
    tempApiKeyText: String,
    onApiKeyChange: (String) -> Unit,
    onSaveKey: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = "Gemini API Ключ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = "Введите ваш API ключ без кириллицы (ASCII)", color = Slate400, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = tempApiKeyText,
                onValueChange = onApiKeyChange,
                placeholder = { Text("AIzaSy...", color = Slate400, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Button(
            onClick = onSaveKey,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = DarkBg),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Сохранить ключ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ManualInputOverlayContent(
    initialType: String,
    selectedDate: String,
    categories: List<CategoryEntity>,
    viewModel: BudgetViewModel,
    contentAlpha: Float,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull { it.type == type }?.name ?: "") }
    var subcategory by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf(TextFieldValue("")) }

    val parsedAmount = remember(amountText.text) { parseAmountInput(amountText.text) }
    val isFormValid = parsedAmount > 0 && selectedCategory.isNotBlank() && subcategory.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Добавить операцию", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = subcategory,
                onValueChange = { subcategory = it },
                placeholder = { Text("Описание (Пятерочка, Кофе)", color = Slate400, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                placeholder = { Text("Сумма (₽)", color = Slate400, fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        Button(
            onClick = {
                if (isFormValid) {
                    viewModel.addTransaction(
                        type = type,
                        date = selectedDate,
                        category = selectedCategory.ifEmpty { "Прочее" },
                        subcategory = subcategory.trim(),
                        amount = parsedAmount
                    )
                    onDismiss()
                }
            },
            enabled = isFormValid,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = DarkBg),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Сохранить", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VoiceOperationsOverlayContent(
    editableList: MutableList<ParsedVoiceOperation>,
    editingIndex: Int?,
    categories: List<CategoryEntity>,
    viewModel: BudgetViewModel,
    selectedDate: String,
    contentAlpha: Float,
    onSelectEditingIndex: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = "Распознанные операции (${editableList.size})", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            editableList.forEachIndexed { index, op ->
                CompactParsedOperationCard(
                    operation = op,
                    onClick = { onSelectEditingIndex(index) },
                    onDelete = {
                        editableList.removeAt(index)
                        if (editableList.isEmpty()) onDismiss()
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Button(
            onClick = {
                if (editableList.isNotEmpty()) {
                    viewModel.confirmVoiceOperations(editableList, selectedDate)
                    viewModel.clearParsedVoiceOperations()
                    viewModel.setVoiceActive(false)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Emerald400, contentColor = DarkBg),
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Сохранить все", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CompactParsedOperationCard(
    operation: ParsedVoiceOperation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = operation.subcategory.ifBlank { operation.category },
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = operation.category, color = Slate400, fontSize = 10.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${if (operation.type == "expense") "-" else "+"}${operation.amount} ₽",
                color = if (operation.type == "expense") Rose500 else Emerald400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Удалить", tint = Rose500, modifier = Modifier.size(14.dp))
            }
        }
    }
}

fun parseAmountInput(input: String): Double {
    return input.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
}

fun formatAmountTextFieldValue(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    val clean = newValue.text.filter { it.isDigit() || it == '.' || it == ',' }
    return newValue.copy(text = clean)
}

