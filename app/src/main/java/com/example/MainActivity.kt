package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Rose500
import com.example.ui.theme.Slate700
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Star
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import com.example.ui.components.VoiceRecordingOverlay
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TransactionEntity
import com.example.ui.components.AddGoalDialog
import com.example.ui.components.AddTransactionDialog
import com.example.ui.components.GeminiConsentDialog
import com.example.ui.components.ApiKeyDialog
import com.example.ui.components.CategoriesDialog
import com.example.ui.components.ExportImportDialog
import com.example.ui.components.GrowthChartSplashScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.AnnualReportScreen
import com.example.ui.screens.BudgetSelectionScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.PeriodBudgetScreen
import com.example.ui.components.ReportDetailsDialog
import com.example.ui.theme.BudgetTheme
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Emerald400
import com.example.ui.theme.Indigo500
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.DarkBg
import com.example.ui.viewmodel.BudgetViewModel
import com.example.ui.viewmodel.PeriodType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import java.io.File
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Add

private const val REQUEST_CODE_POST_NOTIFICATIONS = 101

class MainActivity : FragmentActivity() {

    private val viewModel: BudgetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        com.example.utils.GlobalConsoleLogger.setupUncaughtExceptionHandler()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BudgetTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: BudgetViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val budgetProfiles by viewModel.budgetProfiles.collectAsState()
    val selectedBudgetId by viewModel.selectedBudgetId.collectAsState()

    val currentProfile = remember(budgetProfiles, selectedBudgetId) {
        budgetProfiles.find { it.id == selectedBudgetId }
    }

    var avatarUpdateKey by remember { mutableStateOf(0) }

    val avatarBitmap = remember(currentProfile?.id, avatarUpdateKey) {
        val profileId = currentProfile?.id ?: "default"
        val file = File(context.filesDir, "avatar_$profileId.jpg")
        if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    val transactions by viewModel.transactions.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val completedGoalName by viewModel.completedGoalEvent.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadNotificationsCount = remember(notifications) {
        notifications.count { !it.isRead }
    }

    val periodType by viewModel.periodType.collectAsState()
    val selectedDateDay by viewModel.selectedDateDay.collectAsState()
    val selectedMonthIdx by viewModel.selectedMonthIdx.collectAsState()
    val selectedAnnualYear by viewModel.selectedAnnualYear.collectAsState()
    val allPeriodStart by viewModel.allPeriodStart.collectAsState()
    val allPeriodEnd by viewModel.allPeriodEnd.collectAsState()

    val activeTab by viewModel.activeTab.collectAsState()
    val activeSubTab by viewModel.activeSubTab.collectAsState()

    val expandedExpense by viewModel.expandedExpense.collectAsState()
    val expandedIncome by viewModel.expandedIncome.collectAsState()

    val apiKey by viewModel.apiKey.collectAsState()
    val isGeminiConsentGiven by viewModel.isGeminiConsentGiven.collectAsState()
    val isVoiceActive by viewModel.isVoiceActive.collectAsState()
    val isAnalyzingVoice by viewModel.isAnalyzingVoice.collectAsState()
    val aiAuditResult by viewModel.aiAuditResult.collectAsState()
    val aiAuditLoading by viewModel.aiAuditLoading.collectAsState()
    val isGeneratingReaction by viewModel.isGeneratingReaction.collectAsState()
    val savedAiAudit by viewModel.savedAiAudit.collectAsState()

    // Dialog state handlers
    var showConsentDialog by remember { mutableStateOf(false) }
    var consentDialogMessage by remember { mutableStateOf<String?>(null) }
    var showAddTxModal by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var triggerAuditAfterKeySave by remember { mutableStateOf(false) }
    var showCategoriesModal by remember { mutableStateOf(false) }
    var showAddGoalModal by remember { mutableStateOf(false) }
    var showExportImportModal by remember { mutableStateOf(false) }
    var showSettingsHubModal by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportDialogTab by remember { mutableStateOf(0) }
    var settingsInitialScreen by remember { mutableStateOf(com.example.ui.components.SettingsScreen.HUB) }

    LaunchedEffect(showSettingsHubModal, showCategoriesModal, showAddGoalModal, showAddTxModal, showReportDialog) {
        if (showSettingsHubModal) com.example.utils.GlobalConsoleLogger.i("UI", "Dialog: SettingsHub opened")
        if (showCategoriesModal) com.example.utils.GlobalConsoleLogger.i("UI", "Dialog: Categories opened")
        if (showAddGoalModal) com.example.utils.GlobalConsoleLogger.i("UI", "Dialog: AddGoal opened")
        if (showAddTxModal) com.example.utils.GlobalConsoleLogger.i("UI", "Dialog: AddTransaction opened")
        if (showReportDialog) com.example.utils.GlobalConsoleLogger.i("UI", "Dialog: DavidChat/Report opened")
    }

    val securityManager = remember { com.example.data.SecurityManager(context) }
    var isAppLocked by remember { mutableStateOf(securityManager.isPinEnabled()) }
    var splashStage by remember { mutableStateOf("loading") }

    var showWelcomeBubble by remember { mutableStateOf(false) }
    var showDebugConsole by remember { mutableStateOf(false) }
    var avatarTapCount by remember { mutableStateOf(0) }
    var lastAvatarTapTime by remember { mutableStateOf(0L) }

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveRotation by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveRotation"
    )

    LaunchedEffect(selectedBudgetId, splashStage) {
        if (splashStage == "done" && selectedBudgetId != null) {
            kotlinx.coroutines.delay(600)
            showWelcomeBubble = true
            viewModel.addWelcomeNotification(currentProfile?.name ?: "")
            kotlinx.coroutines.delay(4800)
            showWelcomeBubble = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, securityManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (securityManager.isPinEnabled()) {
                    isAppLocked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activity = context as? FragmentActivity
    LaunchedEffect(isAppLocked, splashStage) {
        if (splashStage == "done" && isAppLocked && securityManager.isBiometricEnabled() && activity != null) {
            com.example.ui.security.BiometricPromptHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = { isAppLocked = false }
            )
        }
    }

    // Consent dialog is now triggered on demand when pressing the FAB (+) button on the Period tab or using AI features.
    var exportJsonStr by remember { mutableStateOf("") }
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var showExportFolderPicker by remember { mutableStateOf(false) }
    var exportFolderPickerJson by remember { mutableStateOf("") }
    var exportFolderPickerFileName by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri != null && !pendingExportJson.isNullOrEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(pendingExportJson!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Файл бюджета успешно сохранен", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportJson = null
    }

    val triggerDirectExport = { budgetId: String? ->
        val idToExport = budgetId ?: selectedBudgetId
        if (idToExport != null) {
            viewModel.exportBackupForBudget(idToExport) { json ->
                pendingExportJson = json
                val targetProfile = budgetProfiles.firstOrNull { it.id == idToExport } ?: currentProfile
                val rawName = targetProfile?.name ?: "budget"
                val safeName = rawName.replace(Regex("[^a-zA-Z0-9А-Яа-я_\\-]"), "_")
                val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(java.util.Date())
                val fileName = "${safeName}_$dateSuffix.json"
                
                exportFolderPickerJson = json
                exportFolderPickerFileName = fileName
                showExportFolderPicker = true
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        splashStage = "exiting"

        if (com.example.notifications.ReminderManager.isReminderEnabled(context)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    val act = context as? android.app.Activity
                    if (act != null) {
                        try {
                            androidx.core.app.ActivityCompat.requestPermissions(
                                act,
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                REQUEST_CODE_POST_NOTIFICATIONS
                            )
                        } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val filteredTransactions = remember(
        transactions, periodType, selectedDateDay, selectedMonthIdx, allPeriodStart, allPeriodEnd, selectedAnnualYear
    ) {
        filterTransactionsForPeriod(
            transactions, periodType, selectedDateDay, selectedMonthIdx, selectedAnnualYear, allPeriodStart, allPeriodEnd
        )
    }

    val mainPagerState = rememberPagerState(initialPage = activeTab, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(mainPagerState) {
        snapshotFlow { mainPagerState.settledPage }.collect { page ->
            if (page != activeTab) {
                viewModel.setActiveTab(page)
            }
        }
    }

    LaunchedEffect(activeTab) {
        if (mainPagerState.currentPage != activeTab && !mainPagerState.isScrollInProgress) {
            mainPagerState.animateScrollToPage(activeTab)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isAppLocked) {
            com.example.ui.screens.PinLockScreen(
                isBiometricAvailable = securityManager.isBiometricEnabled() && activity != null,
                onVerifyPin = { pin -> securityManager.verifyPin(pin) },
                onBiometricClick = {
                    if (activity != null) {
                        com.example.ui.security.BiometricPromptHelper.showBiometricPrompt(
                            activity = activity,
                            onSuccess = { isAppLocked = false }
                        )
                    }
                },
                onResetSecurity = {
                    viewModel.clearAllDataAndResetSecurity(securityManager)
                    isAppLocked = false
                },
                onSuccess = {
                    isAppLocked = false
                },
                onForgotPinClick = {
                    if (activity != null) {
                        com.example.ui.security.BiometricPromptHelper.showBiometricOrDevicePrompt(
                            activity = activity,
                            onSuccess = {
                                viewModel.clearAllDataAndResetSecurity(securityManager)
                                isAppLocked = false
                            },
                            onError = { err ->
                                Toast.makeText(
                                    activity,
                                    "Аутентификация не удалась. Введите «СБРОС» вручную.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = DarkBg,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = DarkBg
                ) {
                    if (selectedBudgetId == null) {
                        BudgetSelectionScreen(
                            profiles = budgetProfiles,
                            onSelectBudget = { viewModel.selectBudget(it) },
                            onCreateBudget = { viewModel.createNewBudget(it) },
                            onRenameBudget = { id, name -> viewModel.renameBudget(id, name) },
                            onDeleteBudget = { id -> viewModel.deleteBudget(id) },
                            onExportBudget = { budgetId -> triggerDirectExport(budgetId) },
                            onImportFromBackup = { json -> viewModel.importBackupAsNewBudget(json) },
                            onOpenApiKeyModal = {
                                settingsInitialScreen = com.example.ui.components.SettingsScreen.API_KEY
                                showSettingsHubModal = true
                            },
                            onOpenCategoriesModal = { showCategoriesModal = true },
                            onOpenReminderModal = {
                                settingsInitialScreen = com.example.ui.components.SettingsScreen.REMINDERS
                                showSettingsHubModal = true
                            },
                            onOpenSecurityModal = {
                                settingsInitialScreen = com.example.ui.components.SettingsScreen.SECURITY
                                showSettingsHubModal = true
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // --- TOP APP BAR ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(contentAlignment = Alignment.CenterStart) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            val now = System.currentTimeMillis()
                                            if (now - lastAvatarTapTime < 500) {
                                                avatarTapCount++
                                                if (avatarTapCount >= 3) {
                                                    showDebugConsole = true
                                                    avatarTapCount = 0
                                                }
                                            } else {
                                                avatarTapCount = 1
                                            }
                                            lastAvatarTapTime = now

                                            settingsInitialScreen = com.example.ui.components.SettingsScreen.HUB
                                            showSettingsHubModal = true
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Neon Glow Background underneath
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        Brush.radialGradient(
                                                            colors = listOf(
                                                                Indigo500.copy(alpha = 0.6f),
                                                                Indigo500.copy(alpha = 0.2f),
                                                                Color.Transparent
                                                            )
                                                        ),
                                                        CircleShape
                                                    )
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .background(
                                                        Brush.sweepGradient(listOf(Emerald400, Indigo500, Rose500, Emerald400)),
                                                        CircleShape
                                                    )
                                                    .padding(2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(DarkBg, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (avatarBitmap != null) {
                                                        Image(
                                                            bitmap = avatarBitmap,
                                                            contentDescription = "Аватар",
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Text(
                                                            text = getProfileInitials(currentProfile?.name ?: ""),
                                                            color = Emerald400,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // --- EXPANDING NEON CHAT BUTTON CONTAINER ---
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val pName = currentProfile?.name ?: "default"
                                    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                                    val hasOpenedChatBefore = remember(showReportDialog, pName) { prefs.getBoolean("has_opened_david_chat_before_$pName", false) }
                                    val hasUnread = unreadNotificationsCount > 0 || !hasOpenedChatBefore
                                    val shouldShowMessage = hasUnread || showWelcomeBubble
                                    val iconNeonColor = Indigo500

                                    var isExpandedByNeon by remember { mutableStateOf(false) }
                                    var isNeonFlickering by remember { mutableStateOf(false) }
                                    val haptic = LocalHapticFeedback.current
                                    val neonAlpha = remember { androidx.compose.animation.core.Animatable(1f) }

                                    LaunchedEffect(shouldShowMessage, unreadNotificationsCount) {
                                        if (shouldShowMessage) {
                                            com.example.utils.GlobalConsoleLogger.i("ANIM", "Запуск анимации плашки уведомлений (непрочитанных: $unreadNotificationsCount, приветствие: $showWelcomeBubble)")
                                            // Step 1: Collapse box & start neon flickering first
                                            isExpandedByNeon = false
                                            isNeonFlickering = true
                                            com.example.utils.GlobalConsoleLogger.d("ANIM", "Эффект неонового мерцания [Старт]: мигание лампы 0.15f -> 1.0f")

                                            // Neon lamp turning-on flickering animation sequence
                                            neonAlpha.snapTo(0.15f)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            kotlinx.coroutines.delay(70)
                                            neonAlpha.snapTo(1f)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            kotlinx.coroutines.delay(50)
                                            neonAlpha.snapTo(0.2f)
                                            kotlinx.coroutines.delay(90)
                                            neonAlpha.snapTo(0.9f)
                                            kotlinx.coroutines.delay(40)
                                            neonAlpha.snapTo(0.1f)
                                            kotlinx.coroutines.delay(80)
                                            neonAlpha.snapTo(1f)

                                            isNeonFlickering = false
                                            com.example.utils.GlobalConsoleLogger.d("ANIM", "Эффект неонового мерцания [Завершен]")

                                            // Step 2: Expand container after flickering completes
                                            isExpandedByNeon = true
                                            com.example.utils.GlobalConsoleLogger.i("ANIM", "Раскрытие плашки уведомлений: isExpandedByNeon = true")
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                            // Step 3: Show message for 4 seconds, then auto-collapse back to glowing icon
                                            kotlinx.coroutines.delay(4000)
                                            isExpandedByNeon = false
                                            com.example.utils.GlobalConsoleLogger.i("ANIM", "Авто-сворачивание плашки уведомлений по таймауту (4 сек)")
                                        } else {
                                            isNeonFlickering = false
                                            isExpandedByNeon = false
                                            neonAlpha.snapTo(1f)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(38.dp)
                                            .shadow(
                                                elevation = if (shouldShowMessage || hasUnread) 12.dp else 2.dp,
                                                shape = RoundedCornerShape(14.dp),
                                                ambientColor = iconNeonColor,
                                                spotColor = iconNeonColor
                                            )
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Slate900.copy(alpha = 0.9f))
                                            .border(
                                                width = if (shouldShowMessage || hasUnread) 1.5.dp else 1.dp,
                                                color = if (isNeonFlickering) {
                                                    iconNeonColor.copy(alpha = neonAlpha.value.coerceIn(0.1f, 1f))
                                                } else if (shouldShowMessage || hasUnread) {
                                                    iconNeonColor.copy(alpha = 0.85f)
                                                } else {
                                                    Slate800
                                                },
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .animateContentSize(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioLowBouncy
                                                )
                                            )
                                            .clickable {
                                                com.example.utils.GlobalConsoleLogger.i("UI", "Нажатие на плашку уведомлений/чата (непрочитанных: $hasUnread), открытие диалога")
                                                showWelcomeBubble = false
                                                isExpandedByNeon = false
                                                if (hasUnread) {
                                                    reportDialogTab = 1
                                                } else {
                                                    reportDialogTab = 0
                                                }
                                                showReportDialog = true
                                            }
                                            .padding(horizontal = 9.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Box(contentAlignment = Alignment.TopEnd) {
                                                Icon(
                                                    imageVector = Icons.Default.Chat,
                                                    contentDescription = "Чат с Давидом",
                                                    tint = iconNeonColor.copy(
                                                        alpha = if (isNeonFlickering) neonAlpha.value.coerceIn(0.15f, 1f) else 1f
                                                    ),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                if (hasUnread) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .offset(x = 2.dp, y = (-2).dp)
                                                            .background(Color.Transparent, CircleShape)
                                                            .border(0.dp, Color.Transparent, CircleShape)
                                                    )
                                                }
                                            }

                                            AnimatedVisibility(
                                                visible = isExpandedByNeon && shouldShowMessage,
                                                enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(
                                                    expandFrom = Alignment.Start,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                ),
                                                exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally(
                                                    shrinkTowards = Alignment.Start,
                                                    animationSpec = tween(200)
                                                )
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 8.dp, end = 2.dp)
                                                ) {
                                                    if (hasUnread) {
                                                        Column {
                                                            Text(
                                                                text = "У вас есть непрочитанные сообщения",
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1
                                                            )
                                                            Text(
                                                                text = "У вас комментарии ($unreadNotificationsCount)",
                                                                color = Indigo500,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    } else if (showWelcomeBubble) {
                                                        val welcomeGreeting = remember(currentProfile?.id, currentProfile?.name) {
                                                            val prefs = context.getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
                                                            val profileKey = currentProfile?.id ?: currentProfile?.name ?: "default"
                                                            val hasOpened = prefs.getBoolean("has_opened_profile_$profileKey", false)
                                                            if (!hasOpened) {
                                                                prefs.edit().putBoolean("has_opened_profile_$profileKey", true).apply()
                                                                "Добро пожаловать"
                                                            } else {
                                                                "С возвращением"
                                                            }
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "$welcomeGreeting, ",
                                                                color = Color.White,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            Text(
                                                                text = "${currentProfile?.name ?: "Друг"}",
                                                                color = Emerald400,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = "👋",
                                                                fontSize = 11.sp,
                                                                modifier = Modifier.graphicsLayer {
                                                                    rotationZ = waveRotation
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



                            Spacer(modifier = Modifier.height(12.dp))

                            // --- NAVIGATION TABS ---
                            val currentMainTab = mainPagerState.currentPage
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(vertical = 2.dp)
                                    .clip(CircleShape)
                                    .background(Slate900.copy(alpha = 0.6f))
                                    .border(1.dp, Slate800, CircleShape)
                                    .padding(4.dp)
                            ) {
                                val barWidth = maxWidth
                                val tabCount = 4
                                val tabWidth = barWidth / tabCount

                                val animatedFraction by animateFloatAsState(
                                    targetValue = currentMainTab.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )

                                Box(
                                    modifier = Modifier
                                        .width(tabWidth)
                                        .fillMaxHeight()
                                        .offset(x = tabWidth * animatedFraction)
                                        .shadow(
                                            elevation = 14.dp,
                                            shape = CircleShape,
                                            ambientColor = Emerald400,
                                            spotColor = Emerald400
                                        )
                                        .background(Emerald400.copy(alpha = 0.18f), CircleShape)
                                        .border(1.dp, Emerald400.copy(alpha = 0.5f), CircleShape)
                                )

                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    NavTabButton(
                                        text = "Период",
                                        icon = androidx.compose.material.icons.Icons.Default.GridView,
                                        isSelected = currentMainTab == 0,
                                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("tab_period"),
                                        onClick = { coroutineScope.launch { mainPagerState.animateScrollToPage(0) } }
                                    )

                                    NavTabButton(
                                        text = "Долги",
                                        icon = androidx.compose.material.icons.Icons.Default.CreditCard,
                                        isSelected = currentMainTab == 1,
                                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("tab_debts"),
                                        onClick = { coroutineScope.launch { mainPagerState.animateScrollToPage(1) } }
                                    )

                                    NavTabButton(
                                        text = "Цели",
                                        icon = androidx.compose.material.icons.Icons.Default.EmojiEvents,
                                        isSelected = currentMainTab == 2,
                                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("tab_goals"),
                                        onClick = { coroutineScope.launch { mainPagerState.animateScrollToPage(2) } }
                                    )

                                    NavTabButton(
                                        text = "Отчет",
                                        icon = androidx.compose.material.icons.Icons.Default.PieChart,
                                        isSelected = currentMainTab == 3,
                                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("tab_annual"),
                                        onClick = { coroutineScope.launch { mainPagerState.animateScrollToPage(3) } }
                                    )
                                }
                            }

                            // --- HORIZONTAL PAGER MAIN TAB CONTENT SCREEN ---
                            HorizontalPager(
                                state = mainPagerState,
                                modifier = Modifier.weight(1f)
                            ) { page ->
                                when (page) {
                                    0 -> PeriodBudgetScreen(
                                        periodType = periodType,
                                        selectedDateDay = selectedDateDay,
                                        selectedMonthIdx = selectedMonthIdx,
                                        selectedYear = selectedAnnualYear,
                                        allPeriodStart = allPeriodStart,
                                        allPeriodEnd = allPeriodEnd,
                                        filteredTransactions = filteredTransactions,
                                        allTransactions = transactions,
                                        activeSubTab = activeSubTab,
                                        expandedExpense = expandedExpense,
                                        expandedIncome = expandedIncome,
                                        aiAuditResult = aiAuditResult,
                                        aiAuditLoading = aiAuditLoading,
                                        savedAiAudit = savedAiAudit,
                                        isAppLocked = isAppLocked,
                                        viewModel = viewModel,
                                        onSetPeriodType = { viewModel.setPeriodType(it) },
                                        onChangeSelectedDay = { viewModel.setSelectedDateDay(it) },
                                        onChangeSelectedMonthIdx = { viewModel.setSelectedMonthIdx(it) },
                                        onChangeSelectedAnnualYear = { viewModel.setSelectedAnnualYear(it) },
                                        onChangeAllPeriodStart = { viewModel.setAllPeriodStart(it) },
                                        onChangeAllPeriodEnd = { viewModel.setAllPeriodEnd(it) },
                                        onChangeActiveSubTab = { viewModel.setActiveSubTab(it) },
                                        onToggleExpandExpense = { viewModel.toggleExpandExpense() },
                                        onToggleExpandIncome = { viewModel.toggleExpandIncome() },
                                        onRequestAiAudit = {
                                            viewModel.requestAiAudit(filteredTransactions)
                                        },
                                        onDeleteTransaction = { viewModel.deleteTransaction(it) },
                                        onEditTransaction = null
                                    )

                                    1 -> DebtsScreen(
                                        accounts = accounts,
                                        transactions = transactions,
                                        notifications = notifications,
                                        onAddDebt = { name, initialAmount, type, comment ->
                                            viewModel.addAccount(name, initialAmount, type, comment)
                                        },
                                        onDeleteDebt = { debtId ->
                                            viewModel.deleteAccount(debtId)
                                        },
                                        onAddDebtTransaction = { type, date, category, subcategory, amount, debtId ->
                                            viewModel.addTransaction(type, date, category, subcategory, amount, debtId)
                                        }
                                    )

                                    2 -> GoalsScreen(
                                        goals = goals,
                                        onOpenAddGoalModal = { showAddGoalModal = true },
                                        onAddGoalProgress = { goalId, amount -> viewModel.addGoalProgress(goalId, amount) },
                                        onDeleteGoal = { viewModel.deleteGoal(it) },
                                        completedGoalName = completedGoalName,
                                        onDismissCompletedGoal = { viewModel.clearCompletedGoalEvent() }
                                    )

                                    3 -> AnnualReportScreen(
                                        selectedYear = selectedAnnualYear,
                                        allTransactions = transactions,
                                        onChangeYear = { viewModel.setSelectedAnnualYear(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- MODALS AND DIALOGS ---

            if (editingTransaction != null) {
                AddTransactionDialog(
                    initialType = if (activeSubTab == "income") "income" else "expense",
                    categories = categories,
                    onDismiss = { editingTransaction = null },
                    onSave = { type, date, category, subcategory, amount ->
                        val txToEdit = editingTransaction
                        if (txToEdit != null) {
                            viewModel.updateTransaction(txToEdit.id, type, date, category, subcategory, amount)
                        }
                        editingTransaction = null
                    },
                    onSuggestCategory = { txName, type, catList ->
                        viewModel.suggestCategory(txName, type, catList)
                    },
                    editingTransaction = editingTransaction
                )
            }

            if (showCategoriesModal) {
                CategoriesDialog(
                    categories = categories,
                    onDismiss = { showCategoriesModal = false },
                    onAddCategory = { type, name -> viewModel.addCategory(type, name) },
                    onDeleteCategory = { id -> viewModel.deleteCategory(id) }
                )
            }

            if (showAddGoalModal) {
                AddGoalDialog(
                    onDismiss = { showAddGoalModal = false },
                    onSave = { name, target, current -> viewModel.saveNewGoal(name, target, current) }
                )
            }

            if (showExportFolderPicker) {
                com.example.ui.components.ExportFolderPickerDialog(
                    defaultFileName = exportFolderPickerFileName,
                    jsonContent = exportFolderPickerJson,
                    onDismiss = { showExportFolderPicker = false },
                    onSaveSuccess = { showExportFolderPicker = false }
                )
            }

            if (showExportImportModal) {
                ExportImportDialog(
                    initialJson = exportJsonStr,
                    onDismiss = { showExportImportModal = false },
                    onImport = { json -> viewModel.importBackup(json) {} }
                )
            }

            if (showSettingsHubModal) {
                com.example.ui.components.SettingsHubDialog(
                    securityManager = securityManager,
                    apiKey = apiKey,
                    currentProfileName = currentProfile?.name ?: "",
                    profileId = currentProfile?.id ?: "default",
                    onAvatarChanged = { avatarUpdateKey++ },
                    onRenameProfile = { newName ->
                        currentProfile?.let { viewModel.renameBudget(it.id, newName) }
                    },
                    onResetAllData = {
                        viewModel.clearAllDataAndResetSecurity(securityManager)
                        showSettingsHubModal = false
                    },
                    initialScreen = settingsInitialScreen,
                    onDismiss = {
                        showSettingsHubModal = false
                        triggerAuditAfterKeySave = false
                    },
                    onSaveApiKey = { newKey ->
                        viewModel.saveApiKey(newKey)
                        if (triggerAuditAfterKeySave && newKey.isNotBlank()) {
                            triggerAuditAfterKeySave = false
                            viewModel.requestAiAudit(filteredTransactions)
                        }
                    },
                    onSecurityUpdated = {
                        if (!securityManager.isPinEnabled()) {
                            isAppLocked = false
                        }
                    },
                    onOpenSecurity = {},
                    onOpenReminders = {},
                    onOpenApiKey = {},
                    onOpenCategories = { showCategoriesModal = true },
                    onExitBudget = {
                        viewModel.selectBudget(null)
                        showSettingsHubModal = false
                    }
                )
            }

            if (showConsentDialog) {
                GeminiConsentDialog(
                    messageOverride = consentDialogMessage ?: "Для использования ИИ-функций требуется ваше согласие на обработку данных.",
                    currentApiKey = apiKey,
                    onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
                    onAccept = {
                        viewModel.setGeminiConsentGiven(true)
                        showConsentDialog = false
                        if (apiKey.isBlank()) {
                            triggerAuditAfterKeySave = true
                            settingsInitialScreen = com.example.ui.components.SettingsScreen.API_KEY
                            showSettingsHubModal = true
                        } else {
                            viewModel.requestAiAudit(filteredTransactions)
                        }
                    },
                    onDecline = {
                        viewModel.setGeminiConsentGiven(false)
                        showConsentDialog = false
                    }
                )
            }

            if (showReportDialog) {
                val activeAuditText = if (aiAuditLoading) {
                    aiAuditResult ?: ""
                } else {
                    savedAiAudit?.auditText ?: aiAuditResult ?: ""
                }
                val periodTitleName = when (periodType) {
                    PeriodType.DAY -> "День ($selectedDateDay)"
                    PeriodType.WEEK -> "Неделя ($selectedDateDay)"
                    PeriodType.MONTH -> "${com.example.ui.screens.MonthsRu.getOrElse(selectedMonthIdx) { "Месяц" }}"
                    PeriodType.ALL -> "Период (с $allPeriodStart по $allPeriodEnd)"
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                val profileKey = currentProfile?.name ?: "default"
                val appPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                ReportDetailsDialog(
                    periodTitle = periodTitleName,
                    auditText = activeAuditText,
                    isLoading = aiAuditLoading,
                    isGeneratingReaction = isGeneratingReaction,
                    auditTimestamp = savedAiAudit?.timestamp,
                    profileName = currentProfile?.name ?: "Вы",
                    notifications = notifications,
                    initialTab = reportDialogTab,
                    onRequestAudit = {
                        viewModel.requestAiAudit(filteredTransactions)
                        true
                    },
                    onMarkAllRead = {
                        appPrefs.edit().putBoolean("has_opened_david_chat_before_$profileKey", true).apply()
                        viewModel.markNotificationsAsRead()
                    },
                    onDismiss = {
                        showReportDialog = false
                        appPrefs.edit().putBoolean("has_opened_david_chat_before_$profileKey", true).apply()
                        viewModel.markNotificationsAsRead()
                    }
                )
            }

            // --- NATIVE OVERLAY FOR VOICE & MANUAL FAB ---
            val parsedVoiceOperations by viewModel.parsedVoiceOperations.collectAsState()
            var isVoiceOverlayActive by remember { mutableStateOf(false) }
            val isVoicePipelineActive = isVoiceActive || isAnalyzingVoice || !parsedVoiceOperations.isNullOrEmpty() || showAddTxModal || isVoiceOverlayActive

            val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
            val isAnyModalOpen = showSettingsHubModal || showCategoriesModal || showExportImportModal || showExportFolderPicker || showAddGoalModal || showConsentDialog || showReportDialog

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 16.dp, end = 16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                AnimatedVisibility(
                    visible = (isVoicePipelineActive || (!isImeVisible && selectedBudgetId != null && mainPagerState.currentPage == 0)) && !isAnyModalOpen,
                    enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow), transformOrigin = TransformOrigin(1f, 0.5f)),
                    exit = fadeOut(animationSpec = tween(200)) + scaleOut(animationSpec = tween(200), transformOrigin = TransformOrigin(1f, 0.5f))
                ) {
                    VoiceRecordingOverlay(
                        viewModel = viewModel,
                        selectedDate = selectedDateDay,
                        showManualInput = showAddTxModal,
                        onDismissManualInput = { showAddTxModal = false },
                        onOpenManualInput = { showAddTxModal = true },
                        initialType = if (activeSubTab == "income") "income" else "expense",
                        onRequireConsent = { msg ->
                            consentDialogMessage = msg
                            showConsentDialog = true
                        },
                        onOverlayActiveChanged = { active ->
                            isVoiceOverlayActive = active
                        }
                    )
                }
            }
        }

        }
        if (com.example.BuildConfig.DEBUG) {
            com.example.ui.components.GlobalConsoleOverlay(
                isVisible = showDebugConsole,
                onDismiss = { showDebugConsole = false }
            )

            if (!showDebugConsole) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp, end = 12.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    com.example.ui.components.DebugConsoleFloatingButton(onClick = { showDebugConsole = true })
                }
            }
        }

        if (splashStage != "done") {
            GrowthChartSplashScreen(
                isExiting = splashStage == "exiting",
                onExitFinished = { splashStage = "done" }
            )
        }
}

@Composable
fun NavTabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Emerald400 else Slate400,
        animationSpec = tween(250)
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    )
    val targetRotation = when {
        !isSelected -> 0f
        text == "Период" -> 90f
        text == "Долги" -> -10f
        text == "Цели" -> 15f
        text == "Отчет" -> 360f
        else -> 15f
    }
    val iconRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    )
    val iconTranslationY by animateDpAsState(
        targetValue = if (isSelected) (-2).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        rotationZ = iconRotation
                        translationY = iconTranslationY.toPx()
                    }
            )
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

private fun filterTransactionsForPeriod(
    list: List<TransactionEntity>,
    type: PeriodType,
    selectedDay: String,
    monthIdx: Int,
    year: Int,
    allStart: String,
    allEnd: String
): List<TransactionEntity> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    return when (type) {
        PeriodType.DAY -> list.filter { it.date == selectedDay }
        PeriodType.MONTH -> {
            val monthFormatted = String.format(Locale.getDefault(), "%02d", monthIdx + 1)
            val prefix = "$year-$monthFormatted"
            list.filter { it.date.startsWith(prefix) }
        }
        PeriodType.WEEK -> {
            val now = Calendar.getInstance()
            val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }

            list.filter {
                try {
                    val d = sdf.parse(it.date) ?: return@filter false
                    d.after(weekAgo.time) && d.before(now.time) || it.date == sdf.format(now.time)
                } catch (e: Exception) {
                    false
                }
            }
        }
        PeriodType.ALL -> {
            list.filter { it.date >= allStart && it.date <= allEnd }
        }
    }
}

private fun getProfileInitials(name: String): String {
    val clean = name.trim().uppercase()
    if (clean.isEmpty()) return "Б"
    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        "${parts[0].first()}${parts[1].first()}"
    } else if (parts.isNotEmpty()) {
        "${parts[0].first()}"
    } else {
        "Б"
    }
}
