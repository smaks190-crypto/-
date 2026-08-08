package com.example.ui.components

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.repository.ParsedVoiceOperation
import com.example.data.db.CategoryEntity
import com.example.ui.components.capitalizeFirstLetter
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Rose500
import com.example.ui.components.parseAmountInput
import com.example.ui.components.formatAmountTextFieldValue
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import com.example.ui.viewmodel.BudgetViewModel
import com.example.ui.screens.getCategoryColorAndIcon
import com.example.utils.VoiceInputManager
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import androidx.compose.ui.text.style.TextOverflow

private const val REQUEST_CODE_RECORD_AUDIO = 100

private fun findActivity(context: android.content.Context): Activity? {
    var curr = context
    while (curr is ContextWrapper) {
        if (curr is Activity) return curr
        curr = curr.baseContext
    }
    return null
}

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
    val voiceErrorState by voiceManager.errorState.collectAsState()
    val voskStatus by voiceManager.voskStatus.collectAsState()
    val voskProgress by voiceManager.voskProgress.collectAsState()

    val isAnalyzingVoice by viewModel.isAnalyzingVoice.collectAsState()
    val voiceErrorMessage by viewModel.voiceErrorMessage.collectAsState()
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
        isVoiceActive -> (screenWidthDp - 120f).coerceAtLeast(220f)
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
                // 1. Expand horizontally first (sides)
                cardWidthAnim.animateTo(desiredWidth, animSpec)
                // 2. Expand vertically second (up & down)
                cardHeightAnim.animateTo(desiredHeight, animSpec)
            } else {
                // 1. Collapse height vertically first
                cardHeightAnim.animateTo(56f, animSpec)
                // 2. Collapse width horizontally second
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

                // 1. Collapse height vertically down towards bottom cancel button
                cardHeightAnim.animateTo(56f, collapseSpec)

                // 2. Collapse width horizontally towards cancel button
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

                // 1. Collapse height vertically down towards bottom cancel button
                cardHeightAnim.animateTo(56f, collapseSpec)

                // 2. Collapse width horizontally towards cancel button
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

    val pulseTransition = rememberInfiniteTransition(label = "fab_voice_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
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
                .clip(RoundedCornerShape(28.dp))
                .then(Modifier),
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
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF1E293B))
                                                    .border(1.dp, dynamicGradient, RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Emerald400,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Text(
                                                text = "Согласие на ИИ-обработку",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = if (showPolicyInCard) "Назад" else "Политика",
                                            color = Indigo500,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clickable { showPolicyInCard = !showPolicyInCard }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (showPolicyInCard) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(170.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1E293B))
                                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                                Text(
                                                    text = "1. Хранение данных\n" +
                                                            "Все ваши финансовые и персональные данные хранятся локально на вашем устройстве.\n\n" +
                                                            "2. Передача данных и ИИ-функции\n" +
                                                            "Для работы ИИ-ассистента, подбора категорий и распознавания голоса данные передаются в Google Gemini API напрямую с вашего устройства. Разработчик не получает доступ к вашим данным.\n\n" +
                                                            "3. Согласие\n" +
                                                            "Вы принимаете решение добровольно. Согласие можно отозвать в любой момент в настройках.",
                                                    color = Slate400,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Для распознавания голоса, ввода транзакций и ИИ-анализа требуется передача данных в Google Gemini.",
                                            color = Slate400,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.setGeminiConsentGiven(true)
                                            showConsentRequested = false
                                            showPolicyInCard = false
                                            tempApiKeyText = apiKey
                                            showApiKeyRequested = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Emerald400,
                                            contentColor = DarkBg
                                        ),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .shadow(
                                                elevation = 14.dp,
                                                shape = CircleShape,
                                                ambientColor = Emerald400,
                                                spotColor = Emerald400
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = Emerald400,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Text(
                                            text = "Принять",
                                            color = DarkBg,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(28.dp))
                                    Spacer(modifier = Modifier.size(56.dp))
                                }
                            }
                        }
                        OverlayState.API_KEY -> {
                            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                            var isPasswordVisible by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Indigo500.copy(alpha = 0.15f))
                                                .border(1.dp, Indigo500.copy(alpha = 0.3f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "ИИ-Помощник",
                                                tint = Indigo500,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Gemini API Ключ",
                                                    color = Color.White,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Emerald400.copy(alpha = 0.2f))
                                                        .border(1.dp, Emerald400.copy(alpha = 0.3f), CircleShape)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Free", color = Emerald400, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                                }
                                            }
                                            Text(
                                                text = "Интеллектуальный помощник",
                                                color = Slate400,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Instruction Box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(DarkBg)
                                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = Indigo500, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Как бесплатно получить API ключ:", color = Indigo500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "1. Перейдите на aistudio.google.com/app/apikey\n" +
                                                        "2. Войдите под своим Google-аккаунтом\n" +
                                                        "3. Нажмите «Create API key»\n" +
                                                        "4. Скопируйте ключ и вставьте ниже",
                                                color = Slate400,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))

                                            Button(
                                                onClick = {
                                                    try {
                                                        uriHandler.openUri("https://aistudio.google.com/app/apikey")
                                                    } catch (_: Throwable) {}
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Indigo500),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth().height(36.dp)
                                            ) {
                                                Text("Получить API ключ в Google AI Studio ↗", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text("ВАШ КЛЮЧ API", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = tempApiKeyText,
                                        onValueChange = { tempApiKeyText = it },
                                        placeholder = { Text("AIzaSy...", color = Slate400, fontSize = 13.sp) },
                                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                val keyToSave = tempApiKeyText.trim()
                                                if (keyToSave.isNotBlank()) {
                                                    viewModel.saveApiKey(keyToSave)
                                                    showApiKeyRequested = false
                                                    onOpenManualInput()
                                                }
                                            }
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (isPasswordVisible) Icons.Default.Info else Icons.Default.Lock,
                                                    contentDescription = "Показать/Скрыть",
                                                    tint = Slate400,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = DarkBg,
                                            unfocusedContainerColor = DarkBg,
                                            focusedBorderColor = Indigo500,
                                            unfocusedBorderColor = Color(0xFF1E293B),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            val keyToSave = tempApiKeyText.trim()
                                            if (keyToSave.isNotBlank()) {
                                                viewModel.saveApiKey(keyToSave)
                                                showApiKeyRequested = false
                                                onOpenManualInput()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Emerald400,
                                            contentColor = DarkBg
                                        ),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .shadow(
                                                elevation = 14.dp,
                                                shape = CircleShape,
                                                ambientColor = Emerald400,
                                                spotColor = Emerald400
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = Emerald400,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Text("Сохранить ключ", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    Spacer(modifier = Modifier.width(28.dp))
                                    Spacer(modifier = Modifier.size(56.dp))
                                }
                            }
                        }
                        OverlayState.MANUAL_INPUT -> {
            var type by remember { mutableStateOf(initialType) }
            var date by remember {
                mutableStateOf(if (selectedDate.isNotBlank()) selectedDate else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
            }
            var selectedCategory by remember {
                mutableStateOf(categories.filter { it.type == type }.firstOrNull()?.name ?: "")
            }
            var subcategory by remember { mutableStateOf("") }
            var amountText by remember { mutableStateOf(TextFieldValue("")) }
            var dropdownExpanded by remember { mutableStateOf(false) }

            var aiSuggestedCategory by remember { mutableStateOf<String?>(null) }
            var isAiSuggesting by remember { mutableStateOf(false) }
            var userManuallySelectedCategory by remember { mutableStateOf(false) }
            var neonFlickerValue by remember { mutableStateOf(1f) }
            var isFlickerFinished by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                // Initial bright solid glow
                neonFlickerValue = 1f
                kotlinx.coroutines.delay(1300)
                
                // Realistic rapid burnout flicker sequence (like a failing neon tube from the video reference)
                val sequence = listOf(
                    0.1f to 70L,
                    0.9f to 90L,
                    0.0f to 120L,
                    0.8f to 60L,
                    0.05f to 100L,
                    0.7f to 50L,
                    0.0f to 180L,
                    0.95f to 60L,
                    0.1f to 80L,
                    0.4f to 50L,
                    0.0f to 200L
                )
                
                for (step in sequence) {
                    neonFlickerValue = step.first
                    kotlinx.coroutines.delay(step.second)
                }
                
                isFlickerFinished = true
                
                // Continuous background loop for occasional realistic micro-sparks/buzzing of the burnt neon!
                while (true) {
                    kotlinx.coroutines.delay((3000..6500).random().toLong())
                    val sparkSequence = listOf(
                        0.15f to 40L,
                        0.0f to 60L,
                        0.25f to 50L,
                        0.0f to 40L
                    )
                    for (spark in sparkSequence) {
                        neonFlickerValue = spark.first
                        kotlinx.coroutines.delay(spark.second)
                    }
                }
            }

            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(subcategory, type) {
                val trimmed = subcategory.trim()
                if (trimmed.length >= 3) {
                    kotlinx.coroutines.delay(600)
                    if (subcategory.trim() == trimmed) {
                        isAiSuggesting = true
                        val catNames = categories.filter { it.type == type }.map { it.name }
                        val suggested = viewModel.suggestCategory(trimmed, type, catNames)
                        isAiSuggesting = false
                        if (suggested.isNotBlank()) {
                            aiSuggestedCategory = suggested
                            if (!userManuallySelectedCategory || selectedCategory.isBlank()) {
                                selectedCategory = suggested
                            }
                        }
                    }
                } else if (trimmed.isBlank()) {
                    aiSuggestedCategory = null
                    isAiSuggesting = false
                }
            }

            val filteredCategories = remember(categories, type) { categories.filter { it.type == type } }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                            .padding(start = 16.dp, top = 12.dp, end = 0.dp, bottom = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Добавить операцию",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("КАТЕГОРИЯ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(DarkBg)
                                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                                            .clickable { dropdownExpanded = true }
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = selectedCategory.ifEmpty { "Категория" },
                                                color = if (selectedCategory.isNotEmpty()) Color.White else Slate400,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (selectedCategory == aiSuggestedCategory && !aiSuggestedCategory.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Indigo500.copy(alpha = 0.2f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("✨ ИИ", color = Indigo500, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false },
                                        modifier = Modifier.background(Slate900)
                                    ) {
                                        filteredCategories.forEach { cat ->
                                            DropdownMenuItem(
                                                text = { Text(cat.name, color = Color.White) },
                                                onClick = {
                                                    selectedCategory = cat.name
                                                    userManuallySelectedCategory = true
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Column {
                                Text("ТИП", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                PlusMinusMorphToggle(
                                    type = type,
                                    onToggle = {
                                        val newType = if (type == "expense") "income" else "expense"
                                        type = newType
                                        userManuallySelectedCategory = false
                                        selectedCategory = categories.filter { it.type == newType }.firstOrNull()?.name ?: ""
                                        aiSuggestedCategory = null
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ОПИСАНИЕ / НАЗВАНИЕ ОПЕРАЦИИ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Indigo500.copy(alpha = 0.2f))
                                    .clickable {
                                        if (subcategory.isNotBlank() && !isAiSuggesting) {
                                            coroutineScope.launch {
                                                isAiSuggesting = true
                                                val catNames = categories.filter { it.type == type }.map { it.name }
                                                val suggested = viewModel.suggestCategory(subcategory.trim(), type, catNames)
                                                isAiSuggesting = false
                                                if (suggested.isNotBlank()) {
                                                    aiSuggestedCategory = suggested
                                                    selectedCategory = suggested
                                                    userManuallySelectedCategory = true
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Indigo500,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ИИ Категория", color = Indigo500, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = subcategory,
                            onValueChange = { subcategory = it.capitalizeFirstLetter() },
                            placeholder = { Text("Например: Пятерочка, Такси, Зарплата", color = Slate400, fontSize = 12.sp) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transaction_description_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkBg,
                                unfocusedContainerColor = DarkBg,
                                focusedBorderColor = Emerald400,
                                unfocusedBorderColor = Slate800,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("СУММА (₽)", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                                    placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                                    suffix = { Text("₽", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("transaction_amount_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = DarkBg,
                                        unfocusedContainerColor = DarkBg,
                                        focusedBorderColor = Emerald400,
                                        unfocusedBorderColor = Slate800,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Column {
                                Text("ДАТА", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                CompactDatePickerField(
                                    value = date,
                                    onDateSelected = { date = it }
                                )
                            }
                        }
                    }

                        // Validation logic for required fields
                        val parsedAmount = remember(amountText.text) { parseAmountInput(amountText.text) }
                        val isAmountValid = parsedAmount > 0
                        val isCategoryValid = selectedCategory.trim().isNotBlank()
                        val isDescriptionValid = subcategory.trim().isNotBlank()
                        val isDateValid = date.trim().isNotBlank()
                        val isFormValid = isAmountValid && isCategoryValid && isDescriptionValid && isDateValid

                        val visualNeonLevel = if (isFormValid) {
                            1f
                        } else {
                            neonFlickerValue
                        }

                        val currentContainerColor = androidx.compose.ui.graphics.lerp(
                            Slate800,
                            Emerald400.copy(alpha = 0.15f),
                            visualNeonLevel
                        )
                        val currentContentColor = androidx.compose.ui.graphics.lerp(
                            Slate500,
                            Emerald400,
                            visualNeonLevel
                        )
                        val currentBorderColor = androidx.compose.ui.graphics.lerp(
                            Slate700,
                            Emerald400.copy(alpha = 0.5f),
                            visualNeonLevel
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (isFormValid) {
                                        viewModel.addTransaction(
                                            type = type,
                                            date = date,
                                            category = selectedCategory.ifEmpty { "Прочее" },
                                            subcategory = subcategory.trim(),
                                            amount = parsedAmount
                                        )
                                        handleDismissManualInput()
                                    }
                                },
                                enabled = isFormValid,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = currentContainerColor,
                                    contentColor = currentContentColor,
                                    disabledContainerColor = currentContainerColor,
                                    disabledContentColor = currentContentColor
                                ),
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .shadow(
                                        elevation = (visualNeonLevel * 14).dp,
                                        shape = CircleShape,
                                        ambientColor = Emerald400,
                                        spotColor = Emerald400
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = currentBorderColor,
                                        shape = CircleShape
                                    )
                                    .testTag("save_transaction_button")
                            ) {
                                Text("Сохранить", color = currentContentColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Spacer(modifier = Modifier.width(28.dp))
                            Spacer(modifier = Modifier.size(56.dp))
                        }
                    }
                }
                        OverlayState.VOICE_OPERATIONS -> {
            if (editingIndex != null && editingIndex!! !in editableList.indices) {
                editingIndex = null
            }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha }
                            .padding(start = 16.dp, top = 12.dp, end = 0.dp, bottom = 12.dp)
                    ) {
                        if (editingIndex == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Emerald400.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Распознано: ${editableList.size}",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val totalAmount = editableList.sumOf { if (it.type == "expense") -it.amount else it.amount }
                                        Text(
                                            text = if (totalAmount >= 0) "+${String.format(Locale.US, "%.0f", totalAmount)} ₽" else "${String.format(Locale.US, "%.0f", totalAmount)} ₽",
                                            color = if (totalAmount >= 0) Emerald400 else Rose500,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                editableList.forEachIndexed { index, op ->
                                    CompactParsedOperationCard(
                                        operation = op,
                                        onClick = { editingIndex = index },
                                        onDelete = {
                                            if (index in editableList.indices) {
                                                editableList.removeAt(index)
                                                if (editableList.isEmpty()) {
                                                    handleDismissVoiceOperations()
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (editableList.isNotEmpty()) {
                                            viewModel.confirmVoiceOperations(editableList, selectedDate)
                                            viewModel.clearParsedVoiceOperations()
                                            viewModel.setVoiceActive(false)
                                        } else {
                                            Toast.makeText(context, "Нет операций для сохранения", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400.copy(alpha = 0.15f)),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                                        .testTag("confirm_voice_operations_button")
                                ) {
                                    Text("Сохранить", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }

                                Spacer(modifier = Modifier.width(28.dp))
                            Spacer(modifier = Modifier.size(56.dp))
                            }

                        } else {
                            val targetIndex = editingIndex!!

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Редактировать операцию",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FullParsedOperationFormCard(
                                    operation = editableList[targetIndex],
                                    categories = categories,
                                    viewModel = viewModel,
                                    defaultDate = selectedDate,
                                    showDeleteButton = editableList.size > 1,
                                    onUpdate = { updated: ParsedVoiceOperation ->
                                        if (targetIndex in editableList.indices) {
                                            editableList[targetIndex] = updated
                                        }
                                    },
                                    onDelete = {
                                        if (targetIndex in editableList.indices) {
                                            editableList.removeAt(targetIndex)
                                            editingIndex = null
                                            if (editableList.isEmpty()) {
                                                handleDismissVoiceOperations()
                                            }
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { editingIndex = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald400.copy(alpha = 0.15f)),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                                        .testTag("save_detail_operation_button")
                                ) {
                                    Text("Готово", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }

                                Spacer(modifier = Modifier.width(28.dp))
                            Spacer(modifier = Modifier.size(56.dp))
                        }
                    }
                }
                        }
                        OverlayState.COLLAPSED -> {
                            // Unified Voice Recording / Idle FAB Capsule
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                AnimatedVisibility(
                    visible = isVoiceActive,
                    enter = fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)) + slideInHorizontally(animationSpec = tween(250)) { -it / 4 },
                    exit = fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing)) + slideOutHorizontally(animationSpec = tween(150)) { -it / 4 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 56.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { viewModel.stopVoiceRecordingAndProcess() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isAnalyzingVoice) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = Indigo500,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Анализ ИИ...",
                                    color = Indigo500,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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

                                if (activeText.isNotBlank()) {
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

        // Single Unified FAB Button
        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center
        ) {
            Spacer(modifier = Modifier.size(56.dp))
        }
    }
}
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

@Composable
private fun NeonWaveVisualizer(
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val animatedRmsDb by animateFloatAsState(
        targetValue = rmsDb,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "animatedRmsDb"
    )

    val transition = rememberInfiniteTransition(label = "spectrometerWaves")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(30.dp)
    ) {
        val totalBars = 15
        val centerIndex = 7
        repeat(totalBars) { index ->
            val distFromCenter = kotlin.math.abs(index - centerIndex)
            val symIndex = centerIndex - distFromCenter
            val sinVal = Math.sin((phase + symIndex * 0.5f).toDouble()).toFloat()
            val baseHeight = 4f + ((sinVal + 1f) * 5f)
            val dynamicHeight = (baseHeight + (animatedRmsDb * 16f * (1.0f - distFromCenter * 0.07f))).coerceIn(4f, 26f)
            val animatedHeight by animateFloatAsState(
                targetValue = dynamicHeight,
                animationSpec = tween(durationMillis = 120),
                label = "barHeight_$index"
            )

            val barColor = if (distFromCenter % 2 == 0) Emerald400 else Indigo500

            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun CompactParsedOperationCard(
    operation: ParsedVoiceOperation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = operation.type == "expense"
    val color = if (isExpense) Rose500 else Emerald400
    val numberFormat = remember {
        val symbols = java.text.DecimalFormatSymbols(Locale("ru", "RU")).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        java.text.DecimalFormat("#,##0.##", symbols).apply {
            isGroupingUsed = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = operation.subcategory.ifBlank { operation.category },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = operation.category,
                color = Slate400,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${if (isExpense) "-" else "+"}${numberFormat.format(operation.amount)} ₽",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить",
                    tint = Rose500.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FullParsedOperationFormCard(
    operation: ParsedVoiceOperation,
    categories: List<CategoryEntity>,
    viewModel: BudgetViewModel,
    defaultDate: String,
    showDeleteButton: Boolean = true,
    onUpdate: (ParsedVoiceOperation) -> Unit,
    onDelete: () -> Unit
) {
    var type by remember(operation) { mutableStateOf(operation.type) }
    var selectedCategory by remember(operation) { mutableStateOf(operation.category) }
    var subcategory by remember(operation) { mutableStateOf(operation.subcategory) }
    var amountText by remember(operation) {
        val str = if (operation.amount == 0.0) "" else if (operation.amount % 1 == 0.0) String.format(Locale.US, "%.0f", operation.amount) else operation.amount.toString()
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(str))
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val filteredCategories = remember(categories, type) { categories.filter { it.type == type } }

    LaunchedEffect(type, selectedCategory, subcategory, amountText) {
        val amt = parseAmountInput(amountText.text)
        onUpdate(
            operation.copy(
                type = type,
                category = selectedCategory,
                subcategory = subcategory,
                amount = amt
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkBg)
            .border(1.dp, Slate800, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("КАТЕГОРИЯ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = selectedCategory.ifEmpty { "Категория" },
                            color = if (selectedCategory.isNotEmpty()) Color.White else Slate400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(Slate900)
                    ) {
                        filteredCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name, color = Color.White) },
                                onClick = {
                                    selectedCategory = cat.name
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Column {
                Text("ТИП", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                PlusMinusMorphToggle(
                    type = type,
                    onToggle = {
                        val newType = if (type == "expense") "income" else "expense"
                        type = newType
                        selectedCategory = categories.filter { it.type == newType }.firstOrNull()?.name ?: ""
                    }
                )
            }
        }

        Column {
            Text("ОПИСАНИЕ / НАЗВАНИЕ", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = subcategory,
                onValueChange = { subcategory = it.capitalizeFirstLetter() },
                placeholder = { Text("Описание", color = Slate400, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900,
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Column {
            Text("СУММА (₽)", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = formatAmountTextFieldValue(amountText, it) },
                placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                suffix = { Text("₽", color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900,
                    focusedBorderColor = Emerald400,
                    unfocusedBorderColor = Slate800,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (showDeleteButton) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Rose500.copy(alpha = 0.15f), contentColor = Rose500),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Rose500)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Удалить операцию", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
