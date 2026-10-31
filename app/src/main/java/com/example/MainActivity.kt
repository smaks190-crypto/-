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
import androidx.compose.ui.zIndex
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
        com.example.utils.GlobalConsoleLogger.setupUncaughtExceptionHandler(this)
        lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            com.example.utils.GlobalConsoleLogger.onLifecycleEvent(event.name)
        })
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

    val avatarBitmap = androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = currentProfile?.id,
        key2 = avatarUpdateKey
    ) {
        val profileId = currentProfile?.id ?: "default"
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
    }.value

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
                            com.example.ui.components.ObsidianTopAppBar(
                                currentProfile = currentProfile,
                                avatarBitmap = avatarBitmap,
                                isDavidChatOpen = activeModal is com.example.ui.viewmodel.BottomCapsuleModal.DavidChat,
                                unreadNotificationsCount = unreadNotificationsCount,
                                splashStage = splashStage,
                                showWelcomeBubble = showWelcomeBubble,
                                onDismissWelcomeBubble = { showWelcomeBubble = false },
                                onOpenSettingsHub = {
                                    viewModel.openModal(
                                        com.example.ui.viewmodel.BottomCapsuleModal.SettingsHub(
                                            com.example.ui.components.SettingsScreen.HUB
                                        )
                                    )
                                },
                                onOpenDebugConsole = { showDebugConsole = true },
                                onOpenDavidChat = {
                                    viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.DavidChat)
                                }
                            )

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

            // --- SHARED DAVID LIVE SESSION & UNIFIED MORPHING BOTTOM BAR ---
            val davidViewModel: com.example.davidapp.DavidViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val liveDisplayMode by davidViewModel.liveDisplayMode.collectAsState()
            val liveConnectionState by davidViewModel.liveConnectionState.collectAsState()
            val liveSubtitle by davidViewModel.liveSubtitle.collectAsState()
            val liveRayMagnitudes by davidViewModel.liveRayMagnitudes.collectAsState()
            val liveOverallEnergy by davidViewModel.liveOverallEnergy.collectAsState()
            val liveActiveSpeaker by davidViewModel.liveActiveSpeaker.collectAsState()
            val isLiveMicMuted by davidViewModel.isLiveMicMuted.collectAsState()
            val liveErrorMessage by davidViewModel.liveErrorMessage.collectAsState()

            val currentBudgetProfile = budgetProfiles.find { it.id == selectedBudgetId }
            LaunchedEffect(selectedBudgetId, currentBudgetProfile?.name, apiKey) {
                davidViewModel.bindProfile(
                    budgetId = currentBudgetProfile?.id ?: "default",
                    profileName = currentBudgetProfile?.name ?: "Максим",
                    apiKey = apiKey
                )
            }

            // Enforce strict separation between David Live and regular transaction voice input
            LaunchedEffect(liveDisplayMode) {
                viewModel.setDavidLiveSessionActive(
                    liveDisplayMode != com.example.davidapp.live.DavidLiveDisplayMode.HIDDEN
                )
            }

            // If the user dismisses the DavidChat modal card (e.g. via scrim tap) while David Live is expanded,
            // smoothly transition David Live into the minimized bottom capsule inside MorphingBottomBar.
            LaunchedEffect(activeModal) {
                if (activeModal != com.example.ui.viewmodel.BottomCapsuleModal.DavidChat &&
                    liveDisplayMode == com.example.davidapp.live.DavidLiveDisplayMode.EXPANDED
                ) {
                    davidViewModel.minimizeDavidLive()
                }
            }

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
                        isDavidLiveActive = liveDisplayMode != com.example.davidapp.live.DavidLiveDisplayMode.HIDDEN,
                        davidLiveMinimizedCapsule = if (liveDisplayMode != com.example.davidapp.live.DavidLiveDisplayMode.HIDDEN) {
                            {
                                com.example.davidapp.live.DavidLiveMinimizedCapsule(
                                    connectionState = liveConnectionState,
                                    rayMagnitudes = liveRayMagnitudes,
                                    overallEnergy = liveOverallEnergy,
                                    activeSpeaker = liveActiveSpeaker,
                                    isMicMuted = isLiveMicMuted,
                                    onExpand = {
                                        davidViewModel.expandDavidLive()
                                        viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.DavidChat)
                                    },
                                    onToggleMicMute = { davidViewModel.toggleLiveMicMute() },
                                    onEndSession = { davidViewModel.endDavidLiveSession() },
                                    embeddedInBottomBar = true
                                )
                            }
                        } else {
                            null
                        },
                        onDavidLiveBlocksVoiceInput = {
                            davidViewModel.expandDavidLive()
                            viewModel.openModal(com.example.ui.viewmodel.BottomCapsuleModal.DavidChat)
                        },
                        modalContent = { modal ->
                            com.example.ui.components.BottomCapsuleModalHost(
                                modal = modal,
                                context = context,
                                viewModel = viewModel,
                                davidViewModel = davidViewModel,
                                securityManager = securityManager,
                                currentProfile = currentProfile,
                                apiKey = apiKey,
                                categories = categories,
                                transactions = transactions,
                                filteredTransactions = filteredTransactions,
                                accounts = accounts,
                                triggerAuditAfterKeySave = triggerAuditAfterKeySave,
                                onSetTriggerAuditAfterKeySave = { triggerAuditAfterKeySave = it },
                                onAvatarChanged = { avatarUpdateKey++ },
                                onUnlockAppIfPinDisabled = {
                                    if (!securityManager.isPinEnabled()) {
                                        isAppLocked = false
                                    }
                                }
                            )
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

            if (!showDebugConsole &&
                activeModal == com.example.ui.viewmodel.BottomCapsuleModal.None &&
                splashStage == "done" &&
                !isAppLocked
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 86.dp, end = 12.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    com.example.ui.components.DebugConsoleFloatingButton(onClick = { showDebugConsole = true })
                }
            }
        }

        SplashHost(
            onSplashDone = { splashStage = "done" }
        )

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
private fun SplashHost(
    onSplashDone: () -> Unit
) {
    var localDone by remember { mutableStateOf(false) }

    LaunchedEffect(localDone) {
        if (localDone) {
            // Уведомляем родительский экран в фоне после того, как шторка уже полностью скрылась без рекомпозиции MainAppScreen
            kotlinx.coroutines.delay(750)
            onSplashDone()
        }
    }

    if (!localDone) {
        GrowthChartSplashScreen(
            isExiting = false,
            onExitFinished = { localDone = true }
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

internal fun filterTransactionsForPeriod(
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
