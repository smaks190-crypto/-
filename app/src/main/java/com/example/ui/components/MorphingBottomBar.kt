package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.CardBg
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate400
import com.example.ui.viewmodel.BottomCapsuleModal
import com.example.ui.viewmodel.BudgetViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class VoiceCardState {
    IDLE,
    CONSENT,
    API_KEY,
    LISTENING,
    PROCESSING,
    RESULT,
    ERROR
}

private enum class ReleaseAction {
    NONE,
    CONFIRM,
    CANCEL
}

/**
 * Restored flagship **MorphingBottomBar** component for Obsidian Neo-Bank (David Finance).
 *
 * Responsibilities:
 * 1. Bottom navigation tabs (`Период`, `Долги`, `Цели`, `Отчет`) with smooth active indicator.
 * 2. Embedded `+` action button and dual-stage morphing into full-screen/card modals (`ManualAddTransaction`,
 *    `EditTransaction`, `AllTransactions`, `SettingsHub`, `CategoryLimits`, `Categories`, `AddGoal`,
 *    `AddDebt`, `DebtDetails`, `GeminiConsent`, `DavidChat`).
 * 3. Hold-to-speak / tap-to-speak regular voice transaction entry (`VoiceInputManager`) with slide-to-cancel
 *    red track and multi-operation confirmation card.
 * 4. Hosting the minimized **David Live** compact voice capsule (`davidLiveMinimizedCapsule`) directly above
 *    the bottom navigation capsule while preventing regular `VoiceInputManager` from starting in parallel
 *    with an active David Live session (`isDavidLiveActive`).
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
    initialType: String,
    showNavigationTabs: Boolean = true,
    onOverlayActiveChanged: (Boolean) -> Unit = {},
    isDavidLiveActive: Boolean = false,
    davidLiveMinimizedCapsule: (@Composable () -> Unit)? = null,
    onDavidLiveBlocksVoiceInput: () -> Unit = {},
    modalContent: @Composable (BottomCapsuleModal) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val isAnalyzing by viewModel.isAnalyzingVoice.collectAsState()
    val parsedOperations by viewModel.parsedVoiceOperations.collectAsState()
    val voiceError by viewModel.voiceErrorMessage.collectAsState()
    val isVoiceActive by viewModel.isVoiceActive.collectAsState()
    val manualText by viewModel.manualText.collectAsState()
    val isGeminiConsentGiven by viewModel.isGeminiConsentGiven.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    val voiceInputManager = viewModel.voiceInputManager
    val isListening by voiceInputManager.isListening.collectAsState()
    val recognizedText by voiceInputManager.recognizedText.collectAsState()
    val partialText by voiceInputManager.partialText.collectAsState()
    val audioLevel by voiceInputManager.audioLevel.collectAsState()
    val frequencyBands by voiceInputManager.frequencyBands.collectAsState()

    var editableOperations by remember(parsedOperations) {
        mutableStateOf(parsedOperations ?: emptyList())
    }
    var editingIndex by remember(parsedOperations) { mutableStateOf<Int?>(null) }

    var showConsentCard by remember { mutableStateOf(false) }
    var showApiKeyCard by remember { mutableStateOf(false) }

    val currentDavidLiveActive by rememberUpdatedState(isDavidLiveActive)
    val currentOnDavidLiveBlocks by rememberUpdatedState(onDavidLiveBlocksVoiceInput)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (currentDavidLiveActive) {
                currentOnDavidLiveBlocks()
            } else {
                viewModel.startVoiceRecording(context)
            }
        } else {
            viewModel.setVoiceActive(false)
        }
    }

    val startVoiceCaptureWithChecks: () -> Unit = {
        if (currentDavidLiveActive) {
            // Strict separation of the two voice modes:
            // Do NOT start regular transaction voice input while David Live is active!
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnDavidLiveBlocks()
        } else {
            val prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
            val currentConsent = prefs.getBoolean("gemini_consent_given", false)
            if (currentConsent && !isGeminiConsentGiven) {
                viewModel.setGeminiConsentGiven(true)
            }
            if (!currentConsent && !isGeminiConsentGiven) {
                showConsentCard = true
            } else if (apiKey.isBlank()) {
                showApiKeyCard = true
            } else {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    viewModel.startVoiceRecording(context)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val currentStartVoiceCapture by rememberUpdatedState(startVoiceCaptureWithChecks)

    val cardState = when {
        showConsentCard -> VoiceCardState.CONSENT
        showApiKeyCard -> VoiceCardState.API_KEY
        showManualInput -> VoiceCardState.IDLE
        parsedOperations != null && parsedOperations!!.isNotEmpty() -> VoiceCardState.RESULT
        isAnalyzing -> VoiceCardState.PROCESSING
        voiceError != null -> VoiceCardState.ERROR
        isVoiceActive || isListening -> VoiceCardState.LISTENING
        else -> VoiceCardState.IDLE
    }

    val activeModalState by viewModel.activeModal.collectAsState()

    // Guard: If BottomCapsuleModal.VoiceInput is requested while David Live is active, block it cleanly
    LaunchedEffect(activeModalState, isDavidLiveActive) {
        if (activeModalState is BottomCapsuleModal.VoiceInput && isDavidLiveActive) {
            viewModel.closeModal()
            onDavidLiveBlocksVoiceInput()
        }
    }

    val activeModal = when {
        activeModalState !is BottomCapsuleModal.None &&
            !(activeModalState is BottomCapsuleModal.VoiceInput && isDavidLiveActive) -> activeModalState
        showManualInput -> BottomCapsuleModal.ManualAddTransaction(initialType)
        cardState != VoiceCardState.IDLE -> BottomCapsuleModal.VoiceInput
        else -> BottomCapsuleModal.None
    }

    val isVoiceMode = activeModal is BottomCapsuleModal.VoiceInput
    val isGenericModal = activeModal !is BottomCapsuleModal.None && !isVoiceMode
    val isExpanded = activeModal !is BottomCapsuleModal.None

    val closeAllModals: () -> Unit = {
        showConsentCard = false
        showApiKeyCard = false
        if (isVoiceMode) {
            viewModel.cancelVoiceRecording()
        }
        if (showManualInput) {
            onDismissManualInput()
        }
        viewModel.closeModal()
    }

    LaunchedEffect(isExpanded) {
        onOverlayActiveChanged(isExpanded)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isVoiceActive || isListening) {
                viewModel.cancelVoiceRecording()
            }
        }
    }

    BackHandler(enabled = isExpanded) {
        closeAllModals()
    }

    val dynamicGradient = rememberMovingNeonBrush()

    val isFullScreenModal = activeModal is BottomCapsuleModal.AllTransactions ||
        activeModal is BottomCapsuleModal.SettingsHub ||
        activeModal is BottomCapsuleModal.Categories ||
        activeModal is BottomCapsuleModal.CategoryLimits ||
        activeModal is BottomCapsuleModal.DebtDetails ||
        activeModal is BottomCapsuleModal.DavidChat

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val statusBarsTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val fullScreenTargetHeight = (screenHeightDp - statusBarsTop - navBarsBottom - 16.dp).coerceAtLeast(360.dp)

    val targetHeightValue = when {
        !isExpanded -> 68f
        isFullScreenModal -> fullScreenTargetHeight.value
        activeModal is BottomCapsuleModal.ManualAddTransaction ||
            activeModal is BottomCapsuleModal.EditTransaction -> 580f
        activeModal is BottomCapsuleModal.AddGoal ||
            activeModal is BottomCapsuleModal.AddDebt -> 470f
        activeModal is BottomCapsuleModal.GeminiConsent -> 440f
        cardState == VoiceCardState.CONSENT -> 300f
        cardState == VoiceCardState.API_KEY -> 400f
        cardState == VoiceCardState.LISTENING -> 80f
        cardState == VoiceCardState.PROCESSING -> 80f
        cardState == VoiceCardState.ERROR -> 170f
        cardState == VoiceCardState.RESULT -> {
            val baseHeight = 200 + (editableOperations.size.coerceAtMost(3) * 62)
            if (editingIndex != null) (baseHeight + 170).toFloat() else baseHeight.toFloat()
        }
        else -> 68f
    }

    val targetWidthValue = when {
        !isExpanded -> (screenWidthDp - 32.dp).coerceAtMost(420.dp).value
        isFullScreenModal -> (screenWidthDp - 16.dp).value
        else -> (screenWidthDp - 24.dp).coerceAtMost(460.dp).value
    }

    val animWidth = remember { Animatable(targetWidthValue) }
    val animHeight = remember { Animatable(68f) }

    LaunchedEffect(targetWidthValue, targetHeightValue, isExpanded) {
        if (isExpanded) {
            // Smooth 2-stage expansion: width expands first, then height
            animWidth.animateTo(
                targetValue = targetWidthValue,
                animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
            )
            animHeight.animateTo(
                targetValue = targetHeightValue,
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            // Smooth 2-stage collapse: height shrinks first, then width returns to pill
            animHeight.animateTo(
                targetValue = 68f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            )
            animWidth.animateTo(
                targetValue = targetWidthValue,
                animationSpec = spring(
                    dampingRatio = 0.86f,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val cardHeight = animHeight.value.dp
    val cardWidth = animWidth.value.dp

    val cornerRadius by animateDpAsState(
        targetValue = when {
            !isExpanded -> 34.dp
            isFullScreenModal -> 28.dp
            cardState == VoiceCardState.LISTENING || cardState == VoiceCardState.PROCESSING -> 40.dp
            else -> 28.dp
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "capsule_corner_radius"
    )

    val bottomPadding by animateDpAsState(
        targetValue = if (isFullScreenModal) 8.dp else 12.dp,
        animationSpec = tween(durationMillis = 200),
        label = "capsule_bottom_padding"
    )

    val rawDisplayTranscript = when {
        manualText.isNotBlank() -> manualText
        partialText.isNotBlank() -> partialText
        recognizedText.isNotBlank() -> recognizedText
        else -> ""
    }

    val isSlideCancelling = remember { mutableStateOf(false) }
    val slideCancelProgressState = remember { mutableStateOf(0f) }

    // Scrim backdrop when expanded
    AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = Modifier.zIndex(9f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    closeAllModals()
                }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isExpanded) 10f else 1f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = navBarsBottom + bottomPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // Minimized David Live Compact Voice Capsule (Frame 5 & Frame 10 of visual spec)
            // Docked right above the bottom navigation capsule so the user can freely switch tabs
            // and interact with the app while continuing their live voice conversation with David.
            AnimatedVisibility(
                visible = !isExpanded && davidLiveMinimizedCapsule != null,
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 }
            ) {
                Box(
                    modifier = Modifier
                        .width((screenWidthDp - 32.dp).coerceAtMost(420.dp))
                        .padding(bottom = 10.dp)
                ) {
                    davidLiveMinimizedCapsule?.invoke()
                }
            }

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Main Morphing Capsule Surface
                val isListeningOrProcessing = isExpanded && isVoiceMode &&
                    (cardState == VoiceCardState.LISTENING || cardState == VoiceCardState.PROCESSING)

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = if (isExpanded) 28.dp else 18.dp,
                            shape = RoundedCornerShape(cornerRadius),
                            ambientColor = if (isExpanded) Indigo500 else Color.Black,
                            spotColor = if (isExpanded) Emerald400 else Color.Black
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                        .then(
                            if (isListeningOrProcessing) {
                                Modifier
                                    .volumetricLiquidAuroraGlow(
                                        audioLevel = if (cardState == VoiceCardState.LISTENING) audioLevel else 0.28f,
                                        cornerRadius = cornerRadius
                                    )
                                    .border(
                                        width = if (isSlideCancelling.value) 1.6.dp else 1.2.dp,
                                        brush = if (isSlideCancelling.value) {
                                            Brush.horizontalGradient(listOf(Rose500, Color(0xFFFF6B6B)))
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFF22C55E).copy(alpha = 0.72f),
                                                    Color(0xFF06B6D4).copy(alpha = 0.72f)
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(cornerRadius)
                                    )
                            } else {
                                Modifier.border(
                                    width = if (isExpanded) 1.5.dp else 1.dp,
                                    brush = dynamicGradient,
                                    shape = RoundedCornerShape(cornerRadius)
                                )
                            }
                        ),
                    color = if (isListeningOrProcessing) {
                        Color(0xFF060D17).copy(alpha = 0.94f)
                    } else {
                        CardBg.copy(alpha = if (isExpanded) 0.99f else 0.96f)
                    },
                    shape = RoundedCornerShape(cornerRadius)
                ) {
                    if (isGenericModal) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            modalContent(activeModal)
                        }
                    } else if (isExpanded && isVoiceMode) {
                        AnimatedContent(
                            targetState = cardState,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(150))
                            },
                            label = "voice_card_content"
                        ) { state ->
                            when (state) {
                                VoiceCardState.IDLE -> {
                                    Spacer(modifier = Modifier.fillMaxSize())
                                }

                                VoiceCardState.CONSENT -> {
                                    VoiceConsentPane(
                                        onAccept = {
                                            viewModel.setGeminiConsentGiven(true)
                                            showConsentCard = false
                                            if (apiKey.isBlank()) {
                                                showApiKeyCard = true
                                            } else {
                                                currentStartVoiceCapture()
                                            }
                                        },
                                        dynamicGradient = dynamicGradient
                                    )
                                }

                                VoiceCardState.API_KEY -> {
                                    VoiceApiKeyPane(
                                        initialApiKey = apiKey,
                                        onSaveKey = { keyToSave ->
                                            viewModel.saveApiKey(keyToSave)
                                            showApiKeyCard = false
                                            currentStartVoiceCapture()
                                        }
                                    )
                                }

                                VoiceCardState.LISTENING -> {
                                    val cancelAlpha = slideCancelProgressState.value.coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Rose500.copy(alpha = 0.38f * cancelAlpha),
                                                        Rose500.copy(alpha = 0.14f * cancelAlpha),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                            .padding(start = 16.dp, end = 88.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        // Red slide-to-cancel progress bar at the bottom of the capsule
                                        if (cancelAlpha > 0.02f) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(bottom = 4.dp)
                                                    .fillMaxWidth(cancelAlpha.coerceIn(0.08f, 1f))
                                                    .height(3.5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            colors = listOf(
                                                                Rose500,
                                                                Color(0xFFFF6B6B)
                                                            )
                                                        )
                                                    )
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            if (isSlideCancelling.value || cancelAlpha > 0.2f) {
                                                Text(
                                                    text = if (cancelAlpha >= 0.85f) {
                                                        "Отпустите для отмены записи"
                                                    } else {
                                                        "← Сдвиньте влево для отмены (${(cancelAlpha * 100).toInt()}%)"
                                                    },
                                                    color = Rose500,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(bottom = 2.dp)
                                                )
                                            } else if (rawDisplayTranscript.isNotBlank()) {
                                                Text(
                                                    text = rawDisplayTranscript,
                                                    color = Color(0xFF86EFAC),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(bottom = 2.dp)
                                                )
                                            }

                                            Neon32BandVoiceVisualizer(
                                                frequencyBands = frequencyBands,
                                                audioLevel = audioLevel,
                                                isCancelling = isSlideCancelling.value,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp)
                                            )
                                        }
                                    }
                                }

                                VoiceCardState.PROCESSING -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 20.dp, end = 88.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF10B981),
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Распознаю операции...",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = rawDisplayTranscript.ifBlank { "Анализ спектра речи Gemini AI" },
                                                color = Color(0xFF06B6D4),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                VoiceCardState.ERROR -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(Rose500.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Rose500,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = voiceError ?: "Не удалось распознать операцию",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = { viewModel.cancelVoiceRecording() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF1E293B),
                                                    contentColor = Color.White
                                                ),
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(56.dp)
                                            ) {
                                                Text("Закрыть", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }

                                            Spacer(modifier = Modifier.width(28.dp))
                                            Spacer(modifier = Modifier.size(56.dp))
                                        }
                                    }
                                }

                                VoiceCardState.RESULT -> {
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
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Распознано операций: ${editableOperations.size}",
                                                    color = Emerald400,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Нажмите для правки",
                                                    color = Slate400,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            LazyColumn(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 260.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                itemsIndexed(editableOperations) { idx, op ->
                                                    VoiceOperationResultRow(
                                                        operation = op,
                                                        isEditing = editingIndex == idx,
                                                        onToggleEdit = {
                                                            editingIndex = if (editingIndex == idx) null else idx
                                                        },
                                                        onUpdate = { updated ->
                                                            editableOperations = editableOperations.toMutableList().also {
                                                                it[idx] = updated
                                                            }
                                                        },
                                                        onDelete = {
                                                            val next = editableOperations.toMutableList().also {
                                                                it.removeAt(idx)
                                                            }
                                                            editableOperations = next
                                                            if (next.isEmpty()) {
                                                                viewModel.cancelVoiceRecording()
                                                            }
                                                        }
                                                    )
                                                }
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
                                                    if (editableOperations.isNotEmpty()) {
                                                        viewModel.confirmVoiceOperations(
                                                            operations = editableOperations,
                                                            dateStr = selectedDate
                                                        )
                                                    } else {
                                                        viewModel.cancelVoiceRecording()
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
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = DarkBg,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Добавить (${editableOperations.size})",
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
                            }
                        }
                    }
                }

                // Bottom Navigation Tabs + Embedded '+' / Voice / Cancel Button
                if (!isGenericModal) {
                    val isIdle = !isExpanded
                    var dragOffsetX by remember { mutableStateOf(0f) }
                    var dragOffsetY by remember { mutableStateOf(0f) }

                    LaunchedEffect(cardState) {
                        if (cardState != VoiceCardState.LISTENING) {
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                            isSlideCancelling.value = false
                            slideCancelProgressState.value = 0f
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (showNavigationTabs && isIdle) {
                            Arrangement.SpaceBetween
                        } else {
                            Arrangement.End
                        }
                    ) {
                        if (showNavigationTabs && isIdle) {
                            // Left 2 Tabs: Период (0), Долги (1)
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MorphNavTabButton(
                                    label = "Период",
                                    icon = Icons.Default.DateRange,
                                    selected = currentMainTab == 0,
                                    onClick = { onSelectTab(0) }
                                )
                                MorphNavTabButton(
                                    label = "Долги",
                                    icon = Icons.Default.Warning,
                                    selected = currentMainTab == 1,
                                    onClick = { onSelectTab(1) }
                                )
                            }

                            Spacer(modifier = Modifier.width(68.dp))

                            // Right 2 Tabs: Цели (2), Отчет (3)
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MorphNavTabButton(
                                    label = "Цели",
                                    icon = Icons.Default.Star,
                                    selected = currentMainTab == 2,
                                    onClick = { onSelectTab(2) }
                                )
                                MorphNavTabButton(
                                    label = "Отчет",
                                    icon = Icons.Default.List,
                                    selected = currentMainTab == 3,
                                    onClick = { onSelectTab(3) }
                                )
                            }
                        }
                    }

                    // Center/Right Action Button ('+' in Idle, Mic/Cancel in Voice Mode)
                    val buttonHorizontalBias by animateFloatAsState(
                        targetValue = if (isIdle && showNavigationTabs) 0f else 0.88f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
                        label = "action_btn_bias"
                    )

                    val maxButtonOffset = ((cardWidth - 68.dp) / 2f).coerceAtLeast(0.dp)
                    val buttonOffsetX = (maxButtonOffset.value * buttonHorizontalBias).dp

                    val currentCardState by rememberUpdatedState(cardState)
                    val currentOnOpenManualInput by rememberUpdatedState(onOpenManualInput)

                    Box(
                        modifier = Modifier
                            .offset(x = buttonOffsetX)
                            .padding(bottom = if (isIdle) 6.dp else 12.dp)
                            .offset {
                                IntOffset(
                                    x = dragOffsetX.roundToInt(),
                                    y = dragOffsetY.roundToInt()
                                )
                            }
                            .size(56.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                ambientColor = when {
                                    isSlideCancelling.value -> Rose500
                                    currentCardState == VoiceCardState.LISTENING -> Color(0xFF06B6D4)
                                    else -> Emerald400
                                },
                                spotColor = when {
                                    isSlideCancelling.value -> Rose500
                                    currentCardState == VoiceCardState.LISTENING -> Color(0xFF22C55E)
                                    else -> Emerald400
                                }
                            )
                            .clip(CircleShape)
                            .background(
                                brush = when {
                                    isSlideCancelling.value -> Brush.linearGradient(
                                        listOf(Rose500, Color(0xFFDC2626))
                                    )
                                    currentCardState == VoiceCardState.LISTENING -> Brush.linearGradient(
                                        listOf(Color(0xFF10B981), Color(0xFF06B6D4))
                                    )
                                    currentCardState == VoiceCardState.RESULT ||
                                        currentCardState == VoiceCardState.CONSENT ||
                                        currentCardState == VoiceCardState.API_KEY ||
                                        currentCardState == VoiceCardState.ERROR -> Brush.linearGradient(
                                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                    )
                                    else -> Brush.linearGradient(
                                        listOf(Emerald400, Color(0xFF10B981))
                                    )
                                }
                            )
                            .border(
                                width = 1.5.dp,
                                color = when {
                                    isSlideCancelling.value -> Rose500
                                    currentCardState == VoiceCardState.LISTENING -> Color(0xFF67E8F9)
                                    else -> Color.White.copy(alpha = 0.28f)
                                },
                                shape = CircleShape
                            )
                            .testTag("morphing_bottom_bar_action_button")
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val downTime = System.currentTimeMillis()
                                    var isLongPressActive = false
                                    var pointerUp = false
                                    var totalDx = 0f

                                    val longPressJob = scope.launch {
                                        delay(320L)
                                        if (!pointerUp && currentCardState == VoiceCardState.IDLE) {
                                            isLongPressActive = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            currentStartVoiceCapture()
                                        }
                                    }

                                    while (!pointerUp) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: event.changes.firstOrNull()
                                        if (change == null || change.changedToUpIgnoreConsumed() || !change.pressed) {
                                            pointerUp = true
                                        } else if (isLongPressActive || currentCardState == VoiceCardState.LISTENING) {
                                            val delta = change.position - change.previousPosition
                                            totalDx = (totalDx + delta.x).coerceIn(-220f, 20f)
                                            dragOffsetX = totalDx
                                            val cancelProgress = (-totalDx / 130f).coerceIn(0f, 1f)
                                            slideCancelProgressState.value = cancelProgress
                                            isSlideCancelling.value = cancelProgress > 0.25f
                                        }
                                    }

                                    longPressJob.cancel()
                                    val pressDuration = System.currentTimeMillis() - downTime
                                    val shouldCancelBySlide = slideCancelProgressState.value >= 0.72f

                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    isSlideCancelling.value = false
                                    slideCancelProgressState.value = 0f

                                    if (isLongPressActive) {
                                        if (shouldCancelBySlide) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.cancelVoiceRecording()
                                        } else {
                                            viewModel.stopVoiceRecordingAndProcess()
                                        }
                                    } else if (pressDuration < 320L) {
                                        when (currentCardState) {
                                            VoiceCardState.IDLE -> {
                                                currentOnOpenManualInput()
                                            }
                                            VoiceCardState.LISTENING -> {
                                                viewModel.stopVoiceRecordingAndProcess()
                                            }
                                            VoiceCardState.CONSENT,
                                            VoiceCardState.API_KEY,
                                            VoiceCardState.RESULT,
                                            VoiceCardState.ERROR,
                                            VoiceCardState.PROCESSING -> {
                                                closeAllModals()
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val iconVector = when (cardState) {
                            VoiceCardState.IDLE -> Icons.Default.Add
                            VoiceCardState.LISTENING -> if (isSlideCancelling.value) Icons.Default.Close else Icons.Default.Check
                            else -> Icons.Default.Close
                        }
                        val iconTint = when (cardState) {
                            VoiceCardState.IDLE -> DarkBg
                            VoiceCardState.LISTENING -> Color.White
                            else -> Slate400
                        }
                        Icon(
                            imageVector = iconVector,
                            contentDescription = "Добавить операцию или голосовой ввод",
                            tint = iconTint,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MorphNavTabButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) Emerald400 else Slate400
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "nav_tab_scale"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
