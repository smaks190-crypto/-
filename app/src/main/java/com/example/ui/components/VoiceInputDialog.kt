package com.example.ui.components

import com.example.ui.components.VoiceWaveCapsule
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
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
    onOverlayActiveChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val voiceManager = viewModel.voiceInputManager

    val isConsentGiven by viewModel.isGeminiConsentGiven.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    var showConsentRequested by remember { mutableStateOf(false) }
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
    val normalizedAmplitude = remember(rmsDb) {
        (rmsDb / 12f).coerceIn(0f, 1f)
    }
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
                val longPressTimeout = 220L
                val dragLockThreshold = 60.dp.toPx()
                val startY = down.position.y

                val longPressTriggered = withTimeoutOrNull(longPressTimeout) {
                    var currentDown = down
                    while (currentDown.pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            return@withTimeoutOrNull false
                        }
                        val deltaY = startY - change.position.y
                        if (deltaY > dragLockThreshold) {
                            return@withTimeoutOrNull true
                        }
                        currentDown = change
                    }
                    false
                }

                if (longPressTriggered == false) {
                    if (showConsentRequested) {
                        showConsentRequested = false
                        if (showManualInput) onDismissManualInput()
                    } else {
                        showConsentRequested = true
                    }
                } else {
                    showConsentRequested = true
                    try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
                }
                return@awaitEachGesture
            } else if (apiKey.isBlank()) {
                down.consume()
                val longPressTimeout = 220L
                val dragLockThreshold = 60.dp.toPx()
                val startY = down.position.y

                val longPressTriggered = withTimeoutOrNull(longPressTimeout) {
                    var currentDown = down
                    while (currentDown.pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            return@withTimeoutOrNull false
                        }
                        val deltaY = startY - change.position.y
                        if (deltaY > dragLockThreshold) {
                            return@withTimeoutOrNull true
                        }
                        currentDown = change
                    }
                    false
                }

                if (longPressTriggered == false) {
                    if (showApiKeyRequested) {
                        showApiKeyRequested = false
                        if (showManualInput) onDismissManualInput()
                    } else {
                        tempApiKeyText = ""
                        showApiKeyRequested = true
                    }
                } else {
                    tempApiKeyText = ""
                    showApiKeyRequested = true
                    try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Throwable) {}
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
                    if (!change.pressed) {
                        return@withTimeoutOrNull false
                    }
                    val deltaY = startY - change.position.y
                    if (deltaY > dragLockThreshold) {
                        return@withTimeoutOrNull true
                    }
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
        isConsentNeeded || isApiKeyNeeded -> (screenWidthDp - 32f).coerceAtLeast(300f)
        showManualInput || isEditingOperations -> (screenWidthDp - 32f).coerceAtLeast(300f)
        isVoiceActive -> (screenWidthDp - 48f).coerceAtLeast(280f)
        else -> 56f
    }

    val editableList = remember(parsedVoiceOperations) {
        mutableStateListOf<ParsedVoiceOperation>().apply {
            parsedVoiceOperations?.let { addAll(it) }
        }
    }

    val desiredHeight = when {
        isConsentNeeded -> 230f.coerceAtMost(screenHeightDp - 60f)
        isApiKeyNeeded -> 430f.coerceAtMost(screenHeightDp - 60f)
        showManualInput -> 442f.coerceAtMost(screenHeightDp - 60f)
        isEditingOperations -> {
            if (editingIndex != null) {
                460f.coerceAtMost(screenHeightDp - 60f)
            } else {
                val count = editableList.size
                val baseHeight = 170f + count * 76f
                baseHeight.coerceIn(240f, screenHeightDp - 60f)
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
            val animSpec = spring<Float>(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
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

                val collapseSpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )

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

                val collapseSpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )

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
        targetValue = if (showAsExpanded) DarkBg.copy(alpha = 0.92f) else Indigo500,
        animationSpec = tween(300),
        label = "surface_color"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if ((isVoiceActive || cardHeightAnim.value > 120f) && !isClosingContentFade) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "content_alpha"
    )

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

    val progress = offset / 600f
    val getGradientColor = { p: Float ->
        val norm = p % 1f
        val phase = if (norm < 0f) norm + 1f else norm
        when {
            phase < 0.3333f -> {
                val t = phase / 0.3333f
                lerp(Indigo500, Emerald400, t)
            }
            phase < 0.6666f -> {
                val t = (phase - 0.3333f) / 0.3333f
                lerp(Emerald400, Rose500, t)
            }
            else -> {
                val t = (phase - 0.6666f) / 0.3334f
                lerp(Rose500, Indigo500, t)
            }
        }
    }

    val neonColor1 = getGradientColor(progress)
    val neonColor2 = getGradientColor(progress + 0.6666f)

    val currentOverlayState = when {
        isConsentNeeded -> OverlayState.CONSENT
        isApiKeyNeeded -> OverlayState.API_KEY
        showManualInput -> OverlayState.MANUAL_INPUT
        isEditingOperations -> OverlayState.VOICE_OPERATIONS
        else -> OverlayState.COLLAPSED
    }

    @Composable
    fun FABContainer(
        modifier: Modifier = Modifier,
        fabIcon: androidx.compose.ui.graphics.vector.ImageVector,
        fabIconRotation: Float,
        fabTint: Color,
        fabContentDescription: String?,
        surfaceColor: Color,
        isClickable: Boolean
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
                        Modifier.border(
                            width = 1.dp,
                            color = Rose500.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (isClickable) {
                        Modifier.clickable { handleFabClick() }
                    } else {
                        fabGestureModifier
                    }
                )
                .testTag(fabTestTag),
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

    Box(
        modifier = modifier
            .padding(bottom = boxBottomPadding, end = boxEndPadding),
        contentAlignment = Alignment.BottomEnd
    ) {
        MovingNeonGlow(
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
                        elevation = if (showAsExpanded) (24 * borderAlpha).dp else 24.dp,
                        shape = RoundedCornerShape(28.dp),
                        clip = false,
                        ambientColor = if (showAsExpanded) neonColor1.copy(alpha = borderAlpha) else Indigo500.copy(alpha = 0.8f),
                        spotColor = if (showAsExpanded) neonColor2.copy(alpha = borderAlpha) else Indigo500.copy(alpha = 0.8f)
                    )
                    .background(surfaceColor, RoundedCornerShape(28.dp))
                    .border(
                        width = 2.dp,
                        brush = dynamicGradient,
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    AnimatedContent(
                        targetState = currentOverlayState,
                        label = "overlay_content",
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }
                    ) { overlayState ->
                        when (overlayState) {
                            OverlayState.CONSENT -> {
                                VoiceConsentPane(
                                    onAccept = {
                                        viewModel.setGeminiConsentGiven(true)
                                        showConsentRequested = false
                                        tempApiKeyText = apiKey
                                        showApiKeyRequested = true
                                    },
                                    dynamicGradient = dynamicGradient
                                )
                            }
                            OverlayState.API_KEY -> {
                                VoiceApiKeyPane(
                                    initialApiKey = tempApiKeyText,
                                    onSaveKey = { keyToSave ->
                                        viewModel.saveApiKey(keyToSave)
                                        showApiKeyRequested = false
                                        onOpenManualInput()
                                    }
                                )
                            }
                            OverlayState.MANUAL_INPUT -> {
                                VoiceManualInputPane(
                                    viewModel = viewModel,
                                    categories = categories,
                                    selectedDate = selectedDate,
                                    initialType = initialType,
                                    contentAlpha = contentAlpha,
                                    onDismiss = { handleDismissManualInput() }
                                )
                            }
                            OverlayState.VOICE_OPERATIONS -> {
                                VoiceOperationsPane(
                                    viewModel = viewModel,
                                    editableList = editableList,
                                    categories = categories,
                                    selectedDate = selectedDate,
                                    contentAlpha = contentAlpha,
                                    editingIndex = editingIndex,
                                    onEditingIndexChanged = { editingIndex = it },
                                    onDismissVoiceOperations = { handleDismissVoiceOperations() }
                                )
                            }
                            OverlayState.COLLAPSED -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    AnimatedVisibility(
    visible = isVoiceActive || isListening,
    enter = fadeIn(),
    exit = fadeOut()
) {
    VoiceWaveCapsule(
        isVisible = isVoiceActive || isListening,
        statusTextCustom = when {
            isAnalyzingVoice -> "АНАЛИЗИРУЮ ГОЛОС..."
            isListening -> "СЛУШАЮ РЕЧЬ..."
            else -> "СЛУШАЮ..."
        },
        onClose = {
            voiceManager.stopListening()
        }
    )

                                    } else {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        if (isRecordingLocked) {
                                                            Icon(
                                                                imageVector = Icons.Default.Lock,
                                                                contentDescription = "Зафиксировано",
                                                                tint = Rose500,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                        }
                                                        val statusText = when (voskStatus) {
                                                            "DOWNLOADING" -> {
                                                                val pct = (voskProgress?.let { (it * 100).toInt() } ?: 0)
                                                                "Скачивание офлайн-модели ($pct%)"
                                                            }
                                                            "EXTRACTING" -> "Настройка модели..."
                                                            else -> "Слушаю..."
                                                        }
                                                        Text(
                                                            text = statusText,
                                                            color = if (voskStatus == "DOWNLOADING" || voskStatus == "EXTRACTING") Emerald400 else Rose500,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(vertical = 4.dp)
                                                        )
                                                    }

                                                    if (activeText.isNotBlank() && !isListening) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "«$activeText»",
                                                            color = Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        }
                    }

                    // Single Unified FAB Button
                    FABContainer(
                        modifier = Modifier.padding(bottom = fabPaddingBottom, end = fabPaddingEnd),
                        fabIcon = fabIcon,
                        fabIconRotation = fabRotationAngle,
                        fabTint = fabTint,
                        fabContentDescription = fabContentDescription,
                        surfaceColor = surfaceColor,
                        isClickable = isConsentNeeded || isApiKeyNeeded || showManualInput || isEditingOperations || isVoiceActive
                    )
                }
            }
        }
    }
}

