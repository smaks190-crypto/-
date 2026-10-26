package com.example

import com.example.ui.components.dialogs.*

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
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.example.ui.components.MorphingBottomBar
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
import com.example.ui.components.dialogs.AddTransactionDialog
import com.example.ui.components.GeminiConsentDialog
import com.example.ui.components.dialogs.CategoriesDialog
import com.example.ui.components.GrowthChartSplashScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.AnnualReportScreen
import com.example.ui.screens.BudgetSelectionScreen
import com.example.ui.screens.GoalsScreen
import com.example.ui.screens.PeriodBudgetScreen
import com.example.ui.screens.RecentTransactionsScreen
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
        com.example.davidapp.AchievementManager.initIfNeeded(this)
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
    val activeModal by viewModel.activeModal.collectAsState()

    var triggerAuditAfterKeySave by remember { mutableStateOf(false) }

    LaunchedEffect(activeModal) {
        when (activeModal) {
            is com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: SettingsHub opened")
            is com.example.ui.viewmodel.BottomCapsuleModal.Categories ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: Categories opened")
            is com.example.ui.viewmodel.BottomCapsuleModal.AddGoal ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: AddGoal opened")
            is com.example.ui.viewmodel.BottomCapsuleModal.ManualAddTransaction,
            is com.example.ui.viewmodel.BottomCapsuleModal.EditTransaction ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: Add/EditTransaction opened")
            is com.example.ui.viewmodel.BottomCapsuleModal.DavidChat ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: DavidChat opened")
            is com.example.ui.viewmodel.BottomCapsuleModal.AllTransactions ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: AllTransactions opened")
            is com.example.ui.viewmodel.BottomCapsuleModal.CategoryLimits ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: CategoryLimits opened")
            is com.example.ui.viewmodel.BottomCapsuleModal.AddDebt,
            is com.example.ui.viewmodel.BottomCapsuleModal.DebtDetails ->
                com.example.utils.GlobalConsoleLogger.i("UI", "CapsuleModal: Debt dialog opened")
            else -> {}
        }
    }

    val securityManager = remember { com.example.data.SecurityManager(context) }
    var isAppLocked by remember { mutableStateOf(securityManager.isPinEnabled()) }
    var splashStage by remember { mutableStateOf("loading") }

    var showWelcomeBubble by remember { mutableStateOf(false) }
    var showDebugConsole by remember { mutableStateOf(false) }
    var avatarTapCount by remember { mutableStateOf(0) }
    var lastAvatarTapTime by remember { mutableStateOf(0L) }

    LaunchedEffect(selectedBudgetId, splashStage) {
        if (splashStage == "done" && selectedBudgetId != null) {
            kotlinx.coroutines.delay(650)
            viewModel.addWelcomeNotification(currentProfile?.name ?: "")
            showWelcomeBubble = true
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

    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri != null && !pendingExportJson.isNullOrEmpty()) {
            try {
                val jsonWithAchievements = com.example.utils.ExportImportHelper.injectAchievements(context, pendingExportJson!!)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonWithAchievements.toByteArray(Charsets.UTF_8))
                }
                com.example.davidapp.AchievementManager.unlock(context, "security_backup")
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
                
                try {
                    createDocumentLauncher.launch(fileName)
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось открыть системный проводник: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2200)

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
                            onImportFromBackup = { json, nameHint -> viewModel.importBackupAsNewBudget(json, nameHint) },
                            onOpenApiKeyModal = {
                                viewModel.openModal(
                                    com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub(
                                        com.example.ui.components.SettingsScreen.API_KEY
                                    )
                                )
                            },
                            onOpenCategoriesModal = {
                                viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.Categories)
                            },
                            onOpenReminderModal = {
                                viewModel.openModal(
                                    com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub(
                                        com.example.ui.components.SettingsScreen.REMINDERS
                                    )
                                )
                            },
                            onOpenSecurityModal = {
                                viewModel.openModal(
                                    com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub(
                                        com.example.ui.components.SettingsScreen.SECURITY
                                    )
                                )
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            // --- OBSIDIAN NEO-BANK TOP APP BAR ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable(
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

                                            viewModel.openModal(
                                                com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub(
                                                    com.example.ui.components.SettingsScreen.HUB
                                                )
                                            )
                                        }
                                        .padding(end = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(42.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    com.example.ui.theme.ObsidianSurface,
                                                    CircleShape
                                                )
                                                .border(
                                                    width = 1.5.dp,
                                                    brush = Brush.linearGradient(
                                                        listOf(
                                                            Emerald400.copy(alpha = 0.7f),
                                                            com.example.ui.theme.GlassBorder
                                                        )
                                                    ),
                                                    shape = CircleShape
                                                )
                                                .padding(2.dp),
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
                                                    fontFamily = com.example.ui.theme.ManropeFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = currentProfile?.name ?: "Личный бюджет",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontFamily = com.example.ui.theme.ManropeFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Профиль и настройки ›",
                                            color = Slate400,
                                            fontSize = 11.sp,
                                            fontFamily = com.example.ui.theme.ManropeFontFamily,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // --- OBSIDIAN GLASS DAVID AI CHAT PILL ---
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val pName = currentProfile?.name ?: "default"
                                    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                                    val isDavidChatOpen = activeModal is com.example.ui.viewmodel.BottomCapsuleModal.DavidChat
                                    val hasOpenedChatBefore = remember(isDavidChatOpen, pName) { prefs.getBoolean("has_opened_david_chat_before_$pName", false) }
                                    val hasUnread = unreadNotificationsCount > 0 || !hasOpenedChatBefore
                                    val shouldShowMessage = (splashStage == "done") && (showWelcomeBubble || (hasUnread && unreadNotificationsCount > 0))
                                    val iconNeonColor = Emerald400

                                    var isExpandedByNeon by remember { mutableStateOf(false) }
                                    var isNeonFlickering by remember { mutableStateOf(false) }
                                    val haptic = LocalHapticFeedback.current
                                    val neonAlpha = remember { androidx.compose.animation.core.Animatable(1f) }

                                    LaunchedEffect(shouldShowMessage, unreadNotificationsCount, splashStage) {
                                        if (shouldShowMessage) {
                                            if (isExpandedByNeon) {
                                                kotlinx.coroutines.delay(4000)
                                                isExpandedByNeon = false
                                                return@LaunchedEffect
                                            }

                                            kotlinx.coroutines.delay(500)
                                            isExpandedByNeon = false
                                            isNeonFlickering = true

                                            neonAlpha.snapTo(0.25f)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            kotlinx.coroutines.delay(70)
                                            neonAlpha.snapTo(1f)
                                            kotlinx.coroutines.delay(50)
                                            neonAlpha.snapTo(0.35f)
                                            kotlinx.coroutines.delay(80)
                                            neonAlpha.snapTo(1f)

                                            isNeonFlickering = false
                                            isExpandedByNeon = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                            kotlinx.coroutines.delay(4000)
                                            isExpandedByNeon = false
                                        } else {
                                            isNeonFlickering = false
                                            isExpandedByNeon = false
                                            neonAlpha.snapTo(1f)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(com.example.ui.theme.ObsidianSurface)
                                            .drawBehind {
                                                val borderColor = if (isNeonFlickering) {
                                                    iconNeonColor.copy(alpha = neonAlpha.value.coerceIn(0.15f, 1f))
                                                } else if (shouldShowMessage || hasUnread) {
                                                    iconNeonColor.copy(alpha = 0.6f)
                                                } else {
                                                    com.example.ui.theme.GlassBorder
                                                }
                                                drawRoundRect(
                                                    color = borderColor,
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                                )
                                            }
                                            .animateContentSize(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow,
                                                    dampingRatio = Spring.DampingRatioLowBouncy
                                                )
                                            )
                                            .clickable {
                                                showWelcomeBubble = false
                                                isExpandedByNeon = false
                                                viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.DavidChat)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "🐸",
                                                fontSize = 15.sp
                                            )
                                            if (!isExpandedByNeon || !shouldShowMessage) {
                                                Text(
                                                    text = "Давид AI",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontFamily = com.example.ui.theme.ManropeFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (unreadNotificationsCount > 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(7.dp)
                                                            .clip(CircleShape)
                                                            .background(Emerald400)
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
                                                    modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                                                ) {
                                                    val welcomeGreeting = remember(currentProfile?.id, currentProfile?.name) {
                                                        val prefs = context.getSharedPreferences("budget_prefs", android.content.Context.MODE_PRIVATE)
                                                        val profileKey = currentProfile?.id ?: currentProfile?.name ?: "default"
                                                        val hasOpened = prefs.getBoolean("has_opened_profile_$profileKey", false)
                                                        val hourNow = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                                                        val timeOfDayGreeting = when (hourNow) {
                                                            in 5..11 -> "Доброе утро"
                                                            in 12..16 -> "Добрый день"
                                                            in 17..22 -> "Добрый вечер"
                                                            else -> "Доброй ночи"
                                                        }
                                                        if (!hasOpened) {
                                                            prefs.edit().putBoolean("has_opened_profile_$profileKey", true).apply()
                                                            "Добро пожаловать"
                                                        } else {
                                                            timeOfDayGreeting
                                                        }
                                                    }
                                                    if (hasUnread && unreadNotificationsCount > 0) {
                                                        Text(
                                                            text = "Новое сообщение от Давида",
                                                            color = Emerald400,
                                                            fontSize = 11.sp,
                                                            fontFamily = com.example.ui.theme.ManropeFontFamily,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    } else {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = "$welcomeGreeting, ",
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                fontFamily = com.example.ui.theme.ManropeFontFamily,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                            Text(
                                                                text = "${currentProfile?.name ?: "Друг"}!",
                                                                color = Emerald400,
                                                                fontSize = 11.sp,
                                                                fontFamily = com.example.ui.theme.ManropeFontFamily,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                                        onEditTransaction = { tx ->
                                            viewModel.openModal(
                                                com.example.ui.viewmodel.BottomCapsuleModal.EditTransaction(tx)
                                            )
                                        }
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
                                        },
                                        onOpenAddDebtModal = {
                                            viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.AddDebt)
                                        },
                                        onOpenDebtDetailsModal = { debt ->
                                            viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.DebtDetails(debt))
                                        }
                                    )

                                    2 -> GoalsScreen(
                                        goals = goals,
                                        onOpenAddGoalModal = {
                                            viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.AddGoal)
                                        },
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

            // --- UNIFIED MORPHING BOTTOM BAR (ALL MODALS + NAV TABS + EMBEDDED '+' + VOICE MORPH) ---
            val parsedVoiceOperations by viewModel.parsedVoiceOperations.collectAsState()
            var isVoiceOverlayActive by remember { mutableStateOf(false) }
            val isVoicePipelineActive = isVoiceActive || isAnalyzingVoice || !parsedVoiceOperations.isNullOrEmpty() || isVoiceOverlayActive || activeModal != com.example.ui.viewmodel.BottomCapsuleModal.None

            val isImeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
            val currentMainTab = mainPagerState.currentPage

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = selectedBudgetId != null || activeModal != com.example.ui.viewmodel.BottomCapsuleModal.None,
                    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.95f, animationSpec = tween(220)),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(150))
                ) {
                    MorphingBottomBar(
                        viewModel = viewModel,
                        currentMainTab = currentMainTab,
                        onSelectTab = { targetPage ->
                            coroutineScope.launch {
                                mainPagerState.animateScrollToPage(targetPage)
                            }
                        },
                        selectedDate = selectedDateDay,
                        showManualInput = activeModal is com.example.ui.viewmodel.BottomCapsuleModal.ManualAddTransaction,
                        onDismissManualInput = { viewModel.closeModal() },
                        onOpenManualInput = {
                            val type = if (activeSubTab == "income") "income" else "expense"
                            viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.ManualAddTransaction(type))
                        },
                        initialType = if (activeSubTab == "income") "income" else "expense",
                        showNavigationTabs = selectedBudgetId != null,
                        onOverlayActiveChanged = { active ->
                            isVoiceOverlayActive = active
                        },
                        modalContent = { modal ->
                            when (modal) {
                                is com.example.ui.viewmodel.BottomCapsuleModal.EditTransaction -> {
                                    AddTransactionDialog(
                                        initialType = modal.transaction.type,
                                        categories = categories,
                                        onDismiss = { viewModel.closeModal() },
                                        onSave = { type, date, category, subcategory, amount ->
                                            viewModel.updateTransaction(
                                                modal.transaction.id,
                                                type,
                                                date,
                                                category,
                                                subcategory,
                                                amount
                                            )
                                            viewModel.closeModal()
                                        },
                                        onSuggestCategory = { txName, type, catList ->
                                            viewModel.suggestCategory(txName, type, catList)
                                        },
                                        editingTransaction = modal.transaction
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.AllTransactions -> {
                                    AllTransactionsDialog(
                                        transactions = transactions,
                                        initialFilterType = modal.initialFilterType,
                                        initialDate = modal.initialDate,
                                        onDismiss = { viewModel.closeModal() },
                                        onEditTransaction = { tx ->
                                            viewModel.openModal(
                                                com.example.ui.viewmodel.BottomCapsuleModal.EditTransaction(tx)
                                            )
                                        },
                                        onDeleteTransaction = { txId ->
                                            viewModel.deleteTransaction(txId)
                                        },
                                        onRestoreTransaction = { tx ->
                                            viewModel.restoreTransaction(tx)
                                        }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub -> {
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
                                            viewModel.closeModal()
                                        },
                                        initialScreen = modal.initialScreen,
                                        onDismiss = {
                                            viewModel.closeModal()
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
                                        onOpenCategories = {
                                            viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.Categories)
                                        },
                                        onExitBudget = {
                                            viewModel.selectBudget(null)
                                            viewModel.closeModal()
                                        }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.CategoryLimits -> {
                                    CategoryLimitsDialog(
                                        categories = categories,
                                        transactions = filteredTransactions,
                                        onUpdateLimit = { catName, limit ->
                                            viewModel.updateCategoryLimit(
                                                categoryName = catName,
                                                type = "expense",
                                                newLimit = limit
                                            )
                                        },
                                        onDismiss = { viewModel.closeModal() }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.Categories -> {
                                    CategoriesDialog(
                                        categories = categories,
                                        onDismiss = { viewModel.closeModal() },
                                        onAddCategory = { type, name -> viewModel.addCategory(type, name) },
                                        onDeleteCategory = { id -> viewModel.deleteCategory(id) }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.AddGoal -> {
                                    AddGoalDialog(
                                        onDismiss = { viewModel.closeModal() },
                                        onSave = { name, target, current ->
                                            viewModel.saveNewGoal(name, target, current)
                                            viewModel.closeModal()
                                        }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.AddDebt -> {
                                    com.example.ui.screens.AddDebtDialog(
                                        onDismiss = { viewModel.closeModal() },
                                        onConfirm = { name, amount, type, comment ->
                                            viewModel.addAccount(name, amount, type, comment)
                                            viewModel.closeModal()
                                        }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.DebtDetails -> {
                                    val liveDebt = accounts.find { it.id == modal.debt.id } ?: modal.debt
                                    val debtTransactions = transactions.filter { it.accountId == liveDebt.id }
                                    val isWeOwe = liveDebt.type != "owes_us"
                                    val paidSoFar = debtTransactions.sumOf { tx ->
                                        val matchesRepayment = if (isWeOwe) tx.type == "expense" else tx.type == "income"
                                        if (matchesRepayment) tx.amount else -tx.amount
                                    }
                                    val remaining = (liveDebt.balance - paidSoFar).coerceAtLeast(0.0)
                                    com.example.ui.screens.DebtDetailsDialog(
                                        debt = liveDebt,
                                        remainingBalance = remaining,
                                        transactions = debtTransactions,
                                        onDismiss = { viewModel.closeModal() },
                                        onAddPayment = { type, date, amt, subcat ->
                                            viewModel.addTransaction(type, date, "Долги", subcat, amt, liveDebt.id)
                                        }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.GeminiConsent -> {
                                    GeminiConsentDialog(
                                        messageOverride = modal.messageOverride ?: "Для использования ИИ-функций требуется ваше согласие на обработку данных.",
                                        currentApiKey = apiKey,
                                        onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
                                        onAccept = {
                                            viewModel.setGeminiConsentGiven(true)
                                            if (apiKey.isBlank()) {
                                                triggerAuditAfterKeySave = modal.triggerAuditAfterAccept
                                                viewModel.openModal(
                                                    com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub(
                                                        com.example.ui.components.SettingsScreen.API_KEY
                                                    )
                                                )
                                            } else {
                                                viewModel.closeModal()
                                                if (modal.triggerAuditAfterAccept) {
                                                    viewModel.requestAiAudit(filteredTransactions)
                                                }
                                            }
                                        },
                                        onDecline = {
                                            viewModel.setGeminiConsentGiven(false)
                                            viewModel.closeModal()
                                        }
                                    )
                                }

                                is com.example.ui.viewmodel.BottomCapsuleModal.DavidChat -> {
                                    val profileKey = currentProfile?.name ?: "default"
                                    val appPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
                                    com.example.davidapp.DavidChatScreen(
                                        budgetId = currentProfile?.id ?: "default",
                                        profileName = currentProfile?.name ?: "Максим",
                                        apiKey = apiKey,
                                        isEmbeddedInCapsule = true,
                                        onBack = {
                                            viewModel.closeModal()
                                            appPrefs.edit().putBoolean("has_opened_david_chat_before_$profileKey", true).apply()
                                            viewModel.markNotificationsAsRead()
                                        }
                                    )
                                }

                                else -> {}
                            }
                        }
                    )
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
                        .padding(bottom = 86.dp, end = 12.dp),
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

        // Всплывающее неоновое уведомление о получении достижения от Давида Жабова
        com.example.davidapp.AchievementUnlockedBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
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
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.Icon(
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
                fontFamily = com.example.ui.theme.ManropeFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
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
