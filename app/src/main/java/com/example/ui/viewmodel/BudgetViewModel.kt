package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.data.db.AccountEntity
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionEntity
import com.example.data.repository.BudgetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class PeriodType { DAY, WEEK, MONTH, ALL }

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BudgetRepository
    private val prefs = application.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            application,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        application.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    }

    val budgetProfiles: StateFlow<List<BudgetProfileEntity>>

    private val _selectedBudgetId = MutableStateFlow<String?>(null)
    val selectedBudgetId: StateFlow<String?> = _selectedBudgetId.asStateFlow()

    val transactions: StateFlow<List<TransactionEntity>>
    val goals: StateFlow<List<GoalEntity>>
    val categories: StateFlow<List<CategoryEntity>>
    val accounts: StateFlow<List<AccountEntity>>
    val notifications: StateFlow<List<NotificationEntity>>

    private val _periodType = MutableStateFlow(PeriodType.MONTH)
    val periodType: StateFlow<PeriodType> = _periodType.asStateFlow()

    private val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private val currentYearInt = Calendar.getInstance().get(Calendar.YEAR)
    private val currentMonthIdx = Calendar.getInstance().get(Calendar.MONTH)

    private val _selectedDateDay = MutableStateFlow(todayIso)
    val selectedDateDay: StateFlow<String> = _selectedDateDay.asStateFlow()

    private val _selectedMonthIdx = MutableStateFlow(currentMonthIdx)
    val selectedMonthIdx: StateFlow<Int> = _selectedMonthIdx.asStateFlow()

    private val _selectedAnnualYear = MutableStateFlow(currentYearInt)
    val selectedAnnualYear: StateFlow<Int> = _selectedAnnualYear.asStateFlow()

    private val _allPeriodStart = MutableStateFlow("$currentYearInt-01-01")
    val allPeriodStart: StateFlow<String> = _allPeriodStart.asStateFlow()

    private val _allPeriodEnd = MutableStateFlow(todayIso)
    val allPeriodEnd: StateFlow<String> = _allPeriodEnd.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _activeSubTab = MutableStateFlow("expense")
    val activeSubTab: StateFlow<String> = _activeSubTab.asStateFlow()

    private val _expandedExpense = MutableStateFlow(false)
    val expandedExpense: StateFlow<Boolean> = _expandedExpense.asStateFlow()

    private val _expandedIncome = MutableStateFlow(false)
    val expandedIncome: StateFlow<Boolean> = _expandedIncome.asStateFlow()

    private val _isGeminiConsentGiven = MutableStateFlow(prefs.getBoolean("gemini_consent_given", false))
    val isGeminiConsentGiven: StateFlow<Boolean> = _isGeminiConsentGiven.asStateFlow()

    fun setGeminiConsentGiven(given: Boolean) {
        prefs.edit().putBoolean("gemini_consent_given", given).apply()
        _isGeminiConsentGiven.value = given
    }

    private val _apiKey = MutableStateFlow(getSavedApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _aiAuditResult = MutableStateFlow<String?>(null)
    val aiAuditResult: StateFlow<String?> = _aiAuditResult.asStateFlow()

    private val _aiAuditLoading = MutableStateFlow(false)
    val aiAuditLoading: StateFlow<Boolean> = _aiAuditLoading.asStateFlow()

    private val _isGeneratingReaction = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isGeneratingReaction: kotlinx.coroutines.flow.StateFlow<Boolean> = _isGeneratingReaction.asStateFlow()

    private val _isAnalyzingVoice = MutableStateFlow(false)
    val isAnalyzingVoice: StateFlow<Boolean> = _isAnalyzingVoice.asStateFlow()

    private val _parsedVoiceOperations = MutableStateFlow<List<com.example.data.repository.ParsedVoiceOperation>?>(null)
    val parsedVoiceOperations: StateFlow<List<com.example.data.repository.ParsedVoiceOperation>?> = _parsedVoiceOperations.asStateFlow()

    private val _voiceErrorMessage = MutableStateFlow<String?>(null)
    val voiceErrorMessage: StateFlow<String?> = _voiceErrorMessage.asStateFlow()

    val voiceInputManager by lazy { com.example.utils.VoiceInputManager(getApplication()) }

    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    private val _manualText = MutableStateFlow("")
    val manualText: StateFlow<String> = _manualText.asStateFlow()

    private lateinit var stateDelegate: TransactionStateDelegate

    val voiceStartTime: Long get() = if (::stateDelegate.isInitialized) stateDelegate.voiceStartTime else 0L

    fun startVoiceRecording(context: Context) {
        stateDelegate.startVoiceRecording(context)
    }

    fun processContinuousVoiceChunk(chunkText: String) {
        stateDelegate.processContinuousVoiceChunk(chunkText)
    }

    fun stopVoiceRecordingAndProcess() {
        stateDelegate.stopVoiceRecordingAndProcess()
    }

    fun cancelVoiceRecording() {
        stateDelegate.cancelVoiceRecording()
    }

    fun setVoiceActive(active: Boolean) {
        stateDelegate.setVoiceActive(active)
    }

    fun setManualText(text: String) {
        stateDelegate.setManualText(text)
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputManager.destroy()
    }

    val currentPeriodKey: StateFlow<String> = kotlinx.coroutines.flow.combine(
        _periodType,
        _selectedDateDay,
        _selectedMonthIdx,
        _selectedAnnualYear
    ) { type, dateDay, monthIdx, year ->
        when (type) {
            PeriodType.DAY -> "DAY_$dateDay"
            PeriodType.WEEK -> "WEEK_$dateDay"
            PeriodType.MONTH -> String.format(Locale.US, "MONTH_%04d-%02d", year, monthIdx + 1)
            PeriodType.ALL -> String.format(Locale.US, "YEAR_%04d", year)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val savedAiAudit: StateFlow<com.example.data.db.AiAuditEntity?> = kotlinx.coroutines.flow.combine(
        _selectedBudgetId,
        currentPeriodKey
    ) { budgetId, key ->
        Pair(budgetId ?: "default", key)
    }.flatMapLatest { (bId, pKey) ->
        repository.getAuditForPeriod(bId, pKey)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _completedGoalEvent = MutableStateFlow<String?>(null)
    val completedGoalEvent: StateFlow<String?> = _completedGoalEvent.asStateFlow()

    fun clearCompletedGoalEvent() {
        _completedGoalEvent.value = null
    }

    private val _showSetupModal = MutableStateFlow(false)
    val showSetupModal: StateFlow<Boolean> = _showSetupModal.asStateFlow()

    init {
        // One-time migration of legacy API key to EncryptedSharedPreferences
        val oldKey = prefs.getString("gemini_api_key", null)
        if (!oldKey.isNullOrEmpty()) {
            securePrefs.edit().putString("gemini_api_key", oldKey).apply()
            prefs.edit().remove("gemini_api_key").apply()
        }

        val database = AppDatabase.getDatabase(application)
        repository = BudgetRepository(
            application,
            database.budgetProfileDao(),
            database.transactionDao(),
            database.goalDao(),
            database.categoryDao(),
            database.aiAuditDao(),
            database.accountDao(),
            database.notificationDao(),
            database
        )

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                _selectedBudgetId,
                currentPeriodKey
            ) { bId, key -> Pair(bId, key) }.collect {
                _aiAuditResult.value = null
            }
        }


        budgetProfiles = repository.allProfiles.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        transactions = _selectedBudgetId.flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.getTransactionsForBudget(id)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        goals = _selectedBudgetId.flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.getGoalsForBudget(id)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        categories = _selectedBudgetId.flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.getCategoriesForBudget(id)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        accounts = _selectedBudgetId.flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.getAccountsForBudget(id)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        notifications = _selectedBudgetId.flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
            else repository.getNotificationsForBudget(id)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

        stateDelegate = TransactionStateDelegate(
            repository = repository,
            scope = viewModelScope,
            selectedBudgetId = _selectedBudgetId.asStateFlow(),
            apiKey = _apiKey.asStateFlow(),
            selectedDateDay = _selectedDateDay.asStateFlow(),
            toastMessage = _toastMessage,
            isGeneratingReaction = _isGeneratingReaction,
            isAnalyzingVoice = _isAnalyzingVoice,
            voiceErrorMessage = _voiceErrorMessage,
            parsedVoiceOperations = _parsedVoiceOperations,
            completedGoalEvent = _completedGoalEvent,
            transactions = transactions,
            accounts = accounts,
            goals = goals,
            categories = categories,
            voiceInputManager = voiceInputManager,
            isVoiceActive = _isVoiceActive,
            manualText = _manualText,
            aiAuditResult = _aiAuditResult,
            aiAuditLoading = _aiAuditLoading,
            getSavedApiKey = { getSavedApiKey() }
        )

        viewModelScope.launch {
            repository.ensureDefaultProfileExists()
            val profilesList = repository.allProfiles.first()
            val isFirstLaunch = prefs.getBoolean("is_first_launch_selection", true)
            val lastId = prefs.getString("last_selected_budget_id", null)

            val targetId = if (!isFirstLaunch) {
                if (lastId != null && profilesList.any { it.id == lastId }) lastId
                else profilesList.firstOrNull()?.id
            } else null

            if (targetId != null) {
                addWelcomeNotification("", targetId)
            }
            _selectedBudgetId.value = targetId
        }
    }

    private fun getSavedApiKey(): String {
        val saved = securePrefs.getString("gemini_api_key", "") ?: ""
        if (saved.isNotBlank() && saved != "your_api_key_here") {
            return saved
        }
        val defaultKey = com.example.BuildConfig.GEMINI_API_KEY
        if (defaultKey.isNotBlank() && defaultKey != "your_api_key_here") {
            return defaultKey
        }
        return ""
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        securePrefs.edit().putString("gemini_api_key", trimmed).apply()
        _apiKey.value = trimmed
        viewModelScope.launch {
            _toastMessage.emit(if (trimmed.isEmpty()) "API ключ удален" else "API ключ сохранен!")
        }
    }

    fun clearAllDataAndResetSecurity(securityManager: com.example.data.SecurityManager) {
        viewModelScope.launch {
            securityManager.removePin()
            repository.clearAllData()
            prefs.edit()
                .remove("last_selected_budget_id")
                .putBoolean("is_first_launch_selection", true)
                .apply()
            _selectedBudgetId.value = null
            repository.ensureDefaultProfileExists()
            _toastMessage.emit("Защита сброшена, данные операций удалены!")
        }
    }

    fun selectBudget(id: String?) {
        com.example.utils.GlobalConsoleLogger.i("STATE", "Selected Budget ID changed to: $id")
        if (id != null) {
            prefs.edit()
                .putString("last_selected_budget_id", id)
                .putBoolean("is_first_launch_selection", false)
                .apply()
            addWelcomeNotification("", id)
        }
        _selectedBudgetId.value = id
    }

    fun createNewBudget(name: String) {
        viewModelScope.launch {
            val profile = repository.createProfile(name.ifBlank { "Новый бюджет" })
            selectBudget(profile.id)
            _toastMessage.emit("Бюджет «${profile.name}» создан!")
        }
    }

    fun renameBudget(id: String, newName: String) {
        viewModelScope.launch {
            repository.renameProfile(id, newName.trim())
            _toastMessage.emit("Название бюджета обновлено")
        }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch {
            repository.deleteProfile(id)
            if (_selectedBudgetId.value == id) {
                _selectedBudgetId.value = null
            }
            _toastMessage.emit("Бюджет удален")
        }
    }

    fun setPeriodType(type: PeriodType) {
        com.example.utils.GlobalConsoleLogger.i("STATE", "PeriodType changed to: $type")
        _periodType.value = type
    }

    fun updateCategoryLimit(categoryName: String, type: String = "expense", newLimit: Double?) {
        viewModelScope.launch {
            val bId = _selectedBudgetId.value ?: "default"
            val existing = categories.value.find { it.name.equals(categoryName, ignoreCase = true) }
            if (existing != null) {
                repository.insertCategory(existing.copy(monthlyLimit = newLimit))
            } else {
                repository.insertCategory(
                    CategoryEntity(
                        budgetId = bId,
                        type = type,
                        name = categoryName,
                        monthlyLimit = newLimit
                    )
                )
            }
            if (newLimit != null && newLimit > 0) {
                _toastMessage.emit("Лимит $categoryName: ${newLimit.toInt()} ₽")
            } else {
                _toastMessage.emit("Лимит для $categoryName сброшен")
            }
        }
    }

    fun setSelectedDateDay(date: String) {
        _selectedDateDay.value = date
    }

    fun setSelectedMonthIdx(idx: Int) {
        _selectedMonthIdx.value = idx
    }

    fun setSelectedAnnualYear(year: Int) {
        _selectedAnnualYear.value = year
    }

    fun setAllPeriodStart(date: String) {
        _allPeriodStart.value = date
    }

    fun setAllPeriodEnd(date: String) {
        _allPeriodEnd.value = date
    }

    fun setActiveTab(index: Int) {
        val tabName = when(index) {
            0 -> "Главная (Обзор)"
            1 -> "Период (Транзакции)"
            2 -> "Долги"
            3 -> "Цели"
            4 -> "Отчет (ИИ Аудит)"
            else -> "Вкладка $index"
        }
        com.example.utils.GlobalConsoleLogger.i("UI", "Active Tab switched to: $tabName")
        _activeTab.value = index
    }

    fun setActiveSubTab(subTab: String) {
        _activeSubTab.value = subTab
    }

    fun toggleExpandExpense() {
        _expandedExpense.value = !_expandedExpense.value
    }

    fun toggleExpandIncome() {
        _expandedIncome.value = !_expandedIncome.value
    }

    fun confirmSetupMode(mode: String) {
        prefs.edit().putBoolean("is_first_run", false).apply()
        _showSetupModal.value = false
        val budgetId = _selectedBudgetId.value ?: "default"
        viewModelScope.launch {
            if (mode == "demo") {
                repository.loadDemoData(budgetId)
                repository.renameProfile(budgetId, "Казума Сато")
                _toastMessage.emit("💀 Бюджет Казумы Сато на весь год загружен!")
            } else {
                repository.initializeDefaultCategories(budgetId)
                _toastMessage.emit("Бюджет сформирован!")
            }
        }
    }

    fun loadFullYearDemoData() {
        val budgetId = _selectedBudgetId.value ?: "default"
        viewModelScope.launch {
            repository.loadDemoData(budgetId)
            repository.renameProfile(budgetId, "Казума Сато")
            _toastMessage.emit("🔥 Бюджет Казумы Сато на весь год успешно добавлен!")
        }
    }

    fun addTransaction(type: String, date: String, category: String, subcategory: String, amount: Double, accountId: String? = null) {
        stateDelegate.addTransaction(type, date, category, subcategory, amount, accountId)
    }

    fun addWelcomeNotification(profileName: String, overrideBudgetId: String? = null) {
        val currentBudgetId = overrideBudgetId ?: _selectedBudgetId.value ?: "default"
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeGreeting = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..16 -> "Добрый день"
            in 17..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val nameStr = if (profileName.isNotBlank() && profileName != "Вы") ", $profileName" else ""

        val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val currentProfileKey = currentBudgetId
        val lastGreetingDate = prefs.getString("last_greeting_date_$currentProfileKey", "")
        val isFirstLaunch = !prefs.getBoolean("has_welcomed_first_time_$currentProfileKey", false)
        val now = System.currentTimeMillis()

        viewModelScope.launch {
            val currentNotifs = try {
                repository.getNotificationsForBudget(currentBudgetId).first()
            } catch (e: Exception) {
                emptyList()
            }
            val hasUnread = currentNotifs.any { !it.isRead }

            if (isFirstLaunch) {
                prefs.edit().putBoolean("has_welcomed_first_time_$currentProfileKey", true).apply()
                prefs.edit().putString("last_greeting_date_$currentProfileKey", todayDate).apply()
                val greetingMsg = "$timeGreeting$nameStr!"
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        budgetId = currentBudgetId,
                        title = "Жабов Давид",
                        description = greetingMsg,
                        icon = "david",
                        color = "emerald400",
                        timestamp = now,
                        isRead = false
                    )
                )
            } else if (!hasUnread && lastGreetingDate != todayDate) {
                prefs.edit().putString("last_greeting_date_$currentProfileKey", todayDate).apply()
                val greetingMsg = "$timeGreeting$nameStr!"
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        budgetId = currentBudgetId,
                        title = "Жабов Давид",
                        description = greetingMsg,
                        icon = "david",
                        color = "emerald400",
                        timestamp = now,
                        isRead = false
                    )
                )
            }
        }
    }

    fun markNotificationsAsRead() {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        viewModelScope.launch {
            repository.markNotificationsAsRead(currentBudgetId)
        }
    }

    fun updateTransaction(id: String, type: String, date: String, category: String, subcategory: String, amount: Double) {
        stateDelegate.updateTransaction(id, type, date, category, subcategory, amount)
    }

    fun processVoiceText(voiceText: String) {
        stateDelegate.processVoiceText(voiceText)
    }

    fun clearParsedVoiceOperations() {
        stateDelegate.clearParsedVoiceOperations()
    }

    fun confirmVoiceOperations(
        operations: List<com.example.data.repository.ParsedVoiceOperation>,
        dateStr: String
    ) {
        stateDelegate.confirmVoiceOperations(operations, dateStr)
    }

    fun deleteTransaction(id: String) {
        stateDelegate.deleteTransaction(id)
    }

    fun addGoalProgress(goalId: String, amount: Double) {
        stateDelegate.addGoalProgress(goalId, amount, todayIso)
    }

    fun saveNewGoal(name: String, target: Double, current: Double) {
        stateDelegate.saveNewGoal(name, target, current, todayIso)
    }

    fun deleteGoal(id: String) {
        stateDelegate.deleteGoal(id)
    }

    fun addCategory(type: String, name: String) {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Добавление категории [$type]: «$name»")
        viewModelScope.launch {
            val cat = CategoryEntity(budgetId = currentBudgetId, type = type, name = name)
            repository.insertCategory(cat)
            _toastMessage.emit("Категория добавлена!")
        }
    }

    fun deleteCategory(id: String) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Удаление категории ID: $id")
        viewModelScope.launch {
            repository.deleteCategory(id)
            _toastMessage.emit("Категория удалена")
        }
    }

    fun clearAllData() {
        val currentBudgetId = _selectedBudgetId.value
        viewModelScope.launch {
            if (currentBudgetId != null) {
                repository.deleteProfile(currentBudgetId)
                val newP = repository.createProfile("Основной бюджет")
                _selectedBudgetId.value = newP.id
            } else {
                repository.clearAllData()
            }
            _toastMessage.emit("Данные текущего бюджета сброшены!")
        }
    }

    fun exportBackup(onExported: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportJsonForBudget(_selectedBudgetId.value ?: "default")
            onExported(json)
        }
    }

    fun exportBackupForBudget(budgetId: String, onExported: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportJsonForBudget(budgetId)
            onExported(json)
        }
    }

    fun importBackupAsNewBudget(json: String, onCompleted: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val newProfile = repository.importBackupAsNewBudget(json)
            if (newProfile != null) {
                selectBudget(newProfile.id)
                _toastMessage.emit("Создан новый бюджет «${newProfile.name}»!")
                onCompleted(true)
            } else {
                _toastMessage.emit("Ошибка чтения файла или формата JSON")
                onCompleted(false)
            }
        }
    }

    fun importBackup(json: String, onCompleted: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importJson(json, _selectedBudgetId.value)
            if (success) {
                _toastMessage.emit("Данные импортированы и сохранены в файл бюджета!")
            } else {
                _toastMessage.emit("Ошибка чтения файла или формата JSON")
            }
            onCompleted(success)
        }
    }

    fun requestAiAudit(currentFilteredTransactions: List<TransactionEntity>) {
        stateDelegate.requestAiAudit(
            currentFilteredTransactions = currentFilteredTransactions,
            isGeminiConsentGiven = _isGeminiConsentGiven.value,
            periodType = _periodType.value,
            selectedDateDay = _selectedDateDay.value,
            selectedAnnualYear = _selectedAnnualYear.value,
            selectedMonthIdx = _selectedMonthIdx.value,
            currentPeriodKey = currentPeriodKey.value
        )
    }

    fun requestAiAuditForPeriod(periodType: PeriodType, customPeriodName: String? = null) {
        stateDelegate.requestAiAuditForPeriod(
            periodType = periodType,
            customPeriodName = customPeriodName,
            isGeminiConsentGiven = _isGeminiConsentGiven.value
        )
    }

    fun sendChatMessageToDavid(message: String) {
        stateDelegate.sendChatMessageToDavid(
            userMessage = message,
            isGeminiConsentGiven = _isGeminiConsentGiven.value
        )
    }

    fun clearDavidChat() {
        stateDelegate.clearDavidChat()
    }

    private fun splitAuditIntoSections(auditText: String): List<String> {
        if (auditText.isBlank() || auditText == "ERROR_NO_CONNECTION") return emptyList()
        val headerRegex = Regex("(?m)^(?=#{1,6}\\s+|(?i)(?:Главный Вердикт|Цифры и Динамика|Прожарка|Ачивки|Выводы))")
        val rawBlocks = auditText.split(headerRegex)
        return rawBlocks
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "ERROR_NO_CONNECTION" }
    }

    suspend fun suggestCategory(
        transactionName: String,
        type: String,
        availableCategories: List<String>
    ): String {
        if (!_isGeminiConsentGiven.value) return ""
        val key = _apiKey.value.ifBlank { getSavedApiKey() }
        return repository.suggestCategory(key, transactionName, type, availableCategories)
    }

    fun addAccount(name: String, initialBalance: Double, type: String = "card", accountNumber: String = "**** 0000") {
        stateDelegate.addAccount(name, initialBalance, type, accountNumber)
    }

    fun deleteAccount(accountId: String) {
        stateDelegate.deleteAccount(accountId)
    }

    fun transferBetweenAccounts(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        fromName: String,
        toName: String
    ) {
        stateDelegate.transferBetweenAccounts(fromAccountId, toAccountId, amount, fromName, toName)
    }

}

