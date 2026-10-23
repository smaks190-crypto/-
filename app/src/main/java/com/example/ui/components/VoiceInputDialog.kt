package com.example.ui.components

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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import kotlin.math.PI
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
import androidx.compose.material.icons.filled.Mic
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
                when {
                    !parsedVoiceOperations.isNullOrEmpty() -> {
                        viewModel.cancelVoiceRecording()
                        viewModel.clearParsedVoiceOperations()
                        viewModel.setVoiceActive(false)
                    }
                    isVoiceActive -> {
                        viewModel.cancelVoiceRecording()
                    }
                    else -> {
                        onOpenManualInput()
                    }
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
        showManualInput -> 490f.coerceAtMost(screenHeightDp - 60f)
        isEditingOperations -> {
            if (editingIndex != null) {
                505f.coerceAtMost(screenHeightDp - 60f)
            } else {
                val count = editableList.size
                val liveExtra = if (isListening || isAnalyzingVoice) 72f else 0f
                val baseHeight = 175f + count * 78f + liveExtra
                baseHeight.coerceIn(245f, screenHeightDp - 60f)
            }
        }
        isVoiceActive -> 64f
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
                                        .height(64.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    AnimatedVisibility(
                                        visible = isVoiceActive,
                                        enter = fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) + slideInHorizontally(animationSpec = tween(250)) { -it / 4 },
                                        exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) + slideOutHorizontally(animationSpec = tween(150)) { -it / 4 },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp, end = 62.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .clickable { viewModel.stopVoiceRecordingAndProcess() },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Left pulsing radial neon Mic button / AI spinner (from David-finance-main-2)
                                            Box(
                                                modifier = Modifier.size(44.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isListening) {
                                                    val pulseScale by animateFloatAsState(
                                                        targetValue = 1f + (rmsDb / 14f).coerceIn(0f, 0.45f),
                                                        animationSpec = tween(100),
                                                        label = "mic_pulse"
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size((36 * pulseScale).dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                Brush.radialGradient(
                                                                    colors = listOf(
                                                                        com.example.ui.theme.NeonCyan.copy(alpha = 0.55f),
                                                                        com.example.ui.theme.NeonEmerald.copy(alpha = 0.25f),
                                                                        Color.Transparent
                                                                    )
                                                                )
                                                            )
                                                    )
                                                }

                                                if (isAnalyzingVoice && !isListening) {
                                                    CircularProgressIndicator(
                                                        color = com.example.ui.theme.NeonEmerald,
                                                        strokeWidth = 2.5.dp,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (isListening) com.example.ui.theme.NeonCyan else com.example.ui.theme.EmeraldPrimary
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isRecordingLocked) Icons.Default.Lock else Icons.Default.Mic,
                                                            contentDescription = "Голосовой ввод",
                                                            tint = DarkBg,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                val displayLine = when {
                                                    isAnalyzingVoice && !isListening -> "✨ Давид разбирает операции через Gemini..."
                                                    activeText.isNotBlank() -> activeText
                                                    isListening -> "Слушаю вас... (нажмите для отправки)"
                                                    else -> "Нажмите для записи"
                                                }
                                                Text(
                                                    text = displayLine,
                                                    color = if (isAnalyzingVoice) com.example.ui.theme.NeonEmerald else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(3.dp))

                                                Neon32BandVoiceVisualizer(
                                                    amplitude = normalizedAmplitude,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(20.dp)
                                                )

                                                Spacer(modifier = Modifier.height(2.dp))

                                                MovingNeonGlowVisualizer(
                                                    isRecording = isListening,
                                                    rmsLevel = rmsDb,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
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
                        isClickable = isConsentNeeded || isApiKeyNeeded || showManualInput || isDetailEditing
                    )
                }
            }
        }
    }

/**
 * 32-полосный непрерывный визуализатор голоса с неоновым свечением.
 * Отрисовывает плавную синусоидально-гармоническую световую волну по 32 спектральным узлам
 * без дискретных столбцов (seamless neon audio wave / glow).
 */
@Composable
fun Neon32BandVoiceVisualizer(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_visualizer_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (-2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryPhase"
    )

    val animatedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0.08f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "animated_amplitude"
    )

    Canvas(
        modifier = modifier
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val maxWaveHeight = (height * 0.42f) * animatedAmp.coerceIn(0.15f, 1f)

        val bandCount = 32
        val stepX = width / (bandCount - 1).toFloat()

        val points = FloatArray(bandCount)
        for (i in 0 until bandCount) {
            val t = i.toFloat() / (bandCount - 1)
            // Hann window function to smoothly fade ends to the baseline
            val window = sin(PI * t).toFloat()
            val h1 = sin(phase + i * 0.42f).toFloat()
            val h2 = sin(secondaryPhase + i * 0.88f).toFloat() * 0.45f
            val h3 = sin(phase * 1.6f + i * 1.3f).toFloat() * 0.25f

            val displacement = (h1 + h2 + h3) * maxWaveHeight * window
            points[i] = displacement
        }

        val wavePath = Path()
        val glowFillPath = Path()
        val mirrorPath = Path()

        wavePath.moveTo(0f, centerY + points[0])
        glowFillPath.moveTo(0f, centerY)
        glowFillPath.lineTo(0f, centerY + points[0])

        for (i in 0 until bandCount - 1) {
            val x0 = i * stepX
            val y0 = centerY + points[i]
            val x1 = (i + 1) * stepX
            val y1 = centerY + points[i + 1]

            val cx = (x0 + x1) / 2f
            wavePath.cubicTo(cx, y0, cx, y1, x1, y1)
            glowFillPath.cubicTo(cx, y0, cx, y1, x1, y1)
        }
        glowFillPath.lineTo(width, centerY)
        glowFillPath.close()

        mirrorPath.moveTo(0f, centerY - points[0] * 0.65f)
        for (i in 0 until bandCount - 1) {
            val x0 = i * stepX
            val y0 = centerY - points[i] * 0.65f
            val x1 = (i + 1) * stepX
            val y1 = centerY - points[i + 1] * 0.65f

            val cx = (x0 + x1) / 2f
            mirrorPath.cubicTo(cx, y0, cx, y1, x1, y1)
        }

        val neonGradient = Brush.horizontalGradient(
            colors = listOf(
                Emerald400,
                Indigo500,
                Rose500,
                Indigo500,
                Emerald400
            )
        )

        // 1. Soft glowing fill under the wave
        drawPath(
            path = glowFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Indigo500.copy(alpha = 0.22f * animatedAmp),
                    Color.Transparent
                ),
                startY = centerY - maxWaveHeight,
                endY = centerY + maxWaveHeight
            )
        )

        // 2. Wide outer neon glow
        drawPath(
            path = wavePath,
            brush = neonGradient,
            style = Stroke(
                width = 6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            alpha = 0.35f * animatedAmp.coerceIn(0.5f, 1f)
        )

        // 3. Mid neon glow aura
        drawPath(
            path = wavePath,
            brush = neonGradient,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            alpha = 0.85f
        )

        // 4. White-hot core filament
        drawPath(
            path = wavePath,
            color = Color.White.copy(alpha = 0.95f),
            style = Stroke(
                width = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 5. Mirrored secondary subtle wave
        drawPath(
            path = mirrorPath,
            brush = neonGradient,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            ),
            alpha = 0.4f
        )
    }
}

