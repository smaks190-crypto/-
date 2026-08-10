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

    private var _voiceStartTime = 0L
    val voiceStartTime: Long get() = _voiceStartTime

    private val _manualText = MutableStateFlow("")
    val manualText: StateFlow<String> = _manualText.asStateFlow()

    private var voiceCollectionJob: kotlinx.coroutines.Job? = null

    fun startVoiceRecording(context: Context) {
        _voiceStartTime = System.currentTimeMillis()
        _isVoiceActive.value = true
        _manualText.value = ""
        _voiceErrorMessage.value = null
        voiceInputManager.onErrorCallback = { cancelVoiceRecording() }
        voiceInputManager.onChunkRecognized = { chunkText ->
            processContinuousVoiceChunk(chunkText)
        }
        voiceInputManager.startListening(context)
    }

    fun processContinuousVoiceChunk(chunkText: String) {
        val trimmed = chunkText.trim()
        if (trimmed.isBlank() || !_isVoiceActive.value) return

        viewModelScope.launch {
            _isAnalyzingVoice.value = true
            _voiceErrorMessage.value = null
            try {
                val expCats = categories.value.filter { it.type == "expense" }.map { it.name }
                val incCats = categories.value.filter { it.type == "income" }.map { it.name }

                val result = repository.parseVoiceOperations(
                    voiceText = trimmed,
                    apiKey = _apiKey.value,
                    expenseCategories = expCats,
                    incomeCategories = incCats
                )

                if (result.isNotEmpty()) {
                    val currentList = _parsedVoiceOperations.value ?: emptyList()
                    val updatedList = currentList + result
                    _parsedVoiceOperations.value = updatedList
                    com.example.utils.GlobalConsoleLogger.i("UI", "Добавлены новые операции (${result.size} шт.). Всего: ${updatedList.size} шт.")
                } else {
                    com.example.utils.GlobalConsoleLogger.d("GEMINI", "В фрагменте «$trimmed» операции не найдены")
                }
            } catch (e: Exception) {
                com.example.utils.GlobalConsoleLogger.e("GEMINI", "Ошибка при обработке фрагмента «$trimmed»: ${e.localizedMessage}", e)
            } finally {
                _isAnalyzingVoice.value = false
            }
        }
    }

    fun stopVoiceRecordingAndProcess() {
        voiceCollectionJob?.cancel()
        voiceCollectionJob = null
        voiceInputManager.stopListening()
        _isVoiceActive.value = false
        val textToProcess = when {
            _manualText.value.isNotBlank() -> _manualText.value
            voiceInputManager.recognizedText.value.isNotBlank() -> voiceInputManager.recognizedText.value
            voiceInputManager.partialText.value.isNotBlank() -> voiceInputManager.partialText.value
            else -> ""
        }
        if (textToProcess.isNotBlank()) {
            processVoiceText(textToProcess)
        } else {
            cancelVoiceRecording()
        }
    }

    fun cancelVoiceRecording() {
        voiceCollectionJob?.cancel()
        voiceCollectionJob = null
        voiceInputManager.stopListening()
        _isVoiceActive.value = false
        _voiceErrorMessage.value = null
        clearParsedVoiceOperations()
    }

    fun setVoiceActive(active: Boolean) {
        if (active) {
            _voiceStartTime = System.currentTimeMillis()
        }
        _isVoiceActive.value = active
    }

    fun setManualText(text: String) {
        _manualText.value = text
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

    private suspend fun ensureCategoryExists(categoryName: String, type: String = "expense") {
        if (categoryName.isBlank() || categoryName.equals("null", ignoreCase = true)) return
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        val trimmed = categoryName.trim()
        val existing = categories.value.find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing == null) {
            val cat = CategoryEntity(
                budgetId = currentBudgetId,
                type = type,
                name = trimmed
            )
            repository.insertCategory(cat)
        }
    }

    fun addTransaction(type: String, date: String, category: String, subcategory: String, amount: Double, accountId: String? = null) {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Добавление транзакции [$type]: $amount ₽ ($category / $subcategory), дата=$date")
        viewModelScope.launch {
            ensureCategoryExists(category, type)
            val tx = TransactionEntity(
                budgetId = currentBudgetId,
                accountId = accountId,
                type = type,
                date = date,
                category = category,
                subcategory = subcategory,
                amount = amount
            )
            repository.insertTransaction(tx)
            _toastMessage.emit("Операция добавлена!")
            _isGeneratingReaction.value = true

            try {
                val isFirstToday = transactions.value.none { it.date == date && it.id != tx.id }
                val userPhrase = repository.generateUserPhrase(
                    apiKey = _apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    isFirstToday = isFirstToday
                )
                var extraCtx = ""
                if (accountId != null) {
                    val debt = accounts.value.find { it.id == accountId }
                    if (debt != null && (debt.type == "we_owe" || debt.type == "owes_us")) {
                        val txs = transactions.value.filter { it.accountId == debt.id }
                        val income = txs.filter { it.type == "income" }.sumOf { it.amount }
                        val expense = txs.filter { it.type == "expense" }.sumOf { it.amount }
                        
                        val isWeOwe = debt.type != "owes_us"
                        val remaining = if (isWeOwe) {
                            debt.balance + income - expense - (if (type == "expense") amount else -amount)
                        } else {
                            debt.balance + expense - income - (if (type == "income") amount else -amount)
                        }
                        val debtTotal = debt.balance.coerceAtLeast(1.0)
                        val ratioPercent = (amount / debtTotal) * 100.0

                        extraCtx = if (remaining <= 0) {
                            "Операция относится к долгу '${debt.name}' (общая сумма долга была: ${debtTotal.toInt()} руб.). ПОЛЬЗОВАТЕЛЬ ТОЛЬКО ЧТО ПОЛНОСТЬЮ ЗАКРЫЛ/ПОГАСИЛ ЭТОТ ДОЛГ! Прокомментируй это радостное событие."
                        } else if (ratioPercent < 5.0 && debtTotal >= 1000.0) {
                            "Операция относится к долгу '${debt.name}'. ОБЩАЯ СУММА ДОЛГА: ${debtTotal.toInt()} руб., а внесено/возвращено ВСЕГО ${amount.toInt()} руб. (это лишь ${String.format(java.util.Locale.US, "%.1f", ratioPercent)}% от общей суммы долга!). Это смехотворные копейки на фоне долга в ${debtTotal.toInt()} руб.! ОБЯЗАТЕЛЬНО отреагируй на этот абсурд и смешной мизерный взнос/возврат по сравнению с огромным долгом!"
                        } else {
                            "Операция относится к долгу '${debt.name}'. Общая целевая сумма долга: ${debtTotal.toInt()} руб. Текущий внесенный взнос: ${amount.toInt()} руб. Остаток долга: ${remaining.toInt()} руб."
                        }
                    }
                }
                
                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx,
                    allTransactions = transactions.value
                )
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        budgetId = currentBudgetId,
                        title = if (type == "income") "Реакция Давида на доход" else "Прожарка от Давида",
                        description = "||$type|$category|$subcategory|$amount|$userPhrase||$comment",
                        icon = "david",
                        color = if (type == "income") "emerald400" else "rose500",
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isGeneratingReaction.value = false
            }
        }
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
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        viewModelScope.launch {
            ensureCategoryExists(category, type)
            val existingTx = transactions.value.find { it.id == id }
            val tx = TransactionEntity(
                id = id,
                budgetId = currentBudgetId,
                accountId = existingTx?.accountId,
                type = type,
                date = date,
                category = category,
                subcategory = subcategory,
                amount = amount,
                createdAt = existingTx?.createdAt ?: System.currentTimeMillis()
            )
            repository.insertTransaction(tx)
            _toastMessage.emit("Операция обновлена!")
        }
    }

    fun processVoiceText(voiceText: String) {
        if (voiceText.isBlank()) return
        viewModelScope.launch {
            _isAnalyzingVoice.value = true
            _voiceErrorMessage.value = null
            try {
                val expCats = categories.value.filter { it.type == "expense" }.map { it.name }
                val incCats = categories.value.filter { it.type == "income" }.map { it.name }

                val result = repository.parseVoiceOperations(
                    voiceText = voiceText,
                    apiKey = _apiKey.value,
                    expenseCategories = expCats,
                    incomeCategories = incCats
                )

                if (result.isEmpty()) {
                    _voiceErrorMessage.value = "Не удалось распознать операции из текста. Укажите суммы и название, например: «Потратил 500 рублей на такси»"
                    _parsedVoiceOperations.value = null
                } else {
                    _parsedVoiceOperations.value = result
                    val finalDate = _selectedDateDay.value.ifBlank {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                    }
                    confirmVoiceOperations(result, finalDate)
                }
            } catch (e: Exception) {
                _voiceErrorMessage.value = "Ошибка при анализе: ${e.message}"
                _parsedVoiceOperations.value = null
            } finally {
                _isAnalyzingVoice.value = false
            }
        }
    }

    fun clearParsedVoiceOperations() {
        _parsedVoiceOperations.value = null
        _voiceErrorMessage.value = null
        _isAnalyzingVoice.value = false
        _isVoiceActive.value = false
        _manualText.value = ""
        voiceInputManager.clear()
    }

    fun confirmVoiceOperations(
        operations: List<com.example.data.repository.ParsedVoiceOperation>,
        dateStr: String
    ) {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Подтверждение операций (${operations.size} шт.), дата: $dateStr")
        viewModelScope.launch {
            if (operations.isEmpty()) return@launch
            
            _isGeneratingReaction.value = true
            
            if (operations.size == 1) {
                val op = operations[0]
                val finalDate = if (op.date.isNotBlank() && op.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) op.date else dateStr
                val finalCategory = if (op.category.isNotBlank() && !op.category.equals("null", true)) op.category else "Прочее"
                val finalSubcategory = if (op.subcategory.isNotBlank() && !op.subcategory.equals("null", true)) op.subcategory else op.title

                ensureCategoryExists(finalCategory, op.type)

                val tx = TransactionEntity(
                    budgetId = currentBudgetId,
                    type = op.type,
                    date = finalDate,
                    category = finalCategory,
                    subcategory = finalSubcategory,
                    amount = op.amount
                )
                repository.insertTransaction(tx)
                com.example.utils.GlobalConsoleLogger.i("ROOM", "Сохранена транзакция в DB: ${tx.category} / ${tx.subcategory} (${tx.amount} ₽)")

                try {
                    val isFirstToday = transactions.value.none { it.date == finalDate && it.id != tx.id }
                    val userPhrase = repository.generateUserPhrase(
                        apiKey = _apiKey.value,
                        type = op.type,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount,
                        isFirstToday = isFirstToday
                    )
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = op.type,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = if (op.type == "income") "Реакция Давида на доход" else "Прожарка от Давида",
                            description = "||${op.type}|$finalCategory|$finalSubcategory|${op.amount}|$userPhrase||$comment",
                            icon = "david",
                            color = if (op.type == "income") "emerald400" else "rose500",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isGeneratingReaction.value = false
                }
            } else {
                val processedOps = mutableListOf<com.example.data.repository.ParsedVoiceOperation>()
                for (op in operations) {
                    val finalDate = if (op.date.isNotBlank() && op.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) op.date else dateStr
                    val finalCategory = if (op.category.isNotBlank() && !op.category.equals("null", true)) op.category else "Прочее"
                    val finalSubcategory = if (op.subcategory.isNotBlank() && !op.subcategory.equals("null", true)) op.subcategory else op.title

                    ensureCategoryExists(finalCategory, op.type)

                    val tx = TransactionEntity(
                        budgetId = currentBudgetId,
                        type = op.type,
                        date = finalDate,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount
                    )
                    repository.insertTransaction(tx)
                    
                    processedOps.add(op.copy(date = finalDate, category = finalCategory, subcategory = finalSubcategory))
                }

                try {
                    val userPhrase = repository.generateUserPhraseMulti(
                        apiKey = _apiKey.value,
                        operations = processedOps
                    )
                    val comment = repository.generateDavidCommentMulti(
                        apiKey = _apiKey.value,
                        operations = processedOps,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    
                    val opsString = processedOps.joinToString(";") { "${it.type}|${it.category}|${it.subcategory}|${it.amount}" }
                    val dominantType = if (processedOps.all { it.type == "income" }) "income" else "expense"
                    
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = if (dominantType == "income") "Реакция Давида на доходы" else "Групповая прожарка от Давида",
                            description = "||MULTI||$opsString||$userPhrase||$comment",
                            icon = "david",
                            color = if (dominantType == "income") "emerald400" else "rose500",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isGeneratingReaction.value = false
                }
            }
            
            _parsedVoiceOperations.value = null
            _toastMessage.emit("🔥 Успешно добавлено ${operations.size} операций!")
        }
    }

    fun deleteTransaction(id: String) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Удаление транзакции ID: $id")
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _toastMessage.emit("Операция удалена")
        }
    }

    fun addGoalProgress(goalId: String, amount: Double) {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Взнос в финансовую цель ID=$goalId на сумму $amount ₽")
        viewModelScope.launch {
            val currentGoals = goals.value
            val goal = currentGoals.find { it.id == goalId } ?: return@launch

            val updatedCurrent = goal.currentAmount + amount

            // Automatically log contribution as expense under "Сбережения"
            val tx = TransactionEntity(
                budgetId = currentBudgetId,
                type = "expense",
                date = todayIso,
                category = "Сбережения",
                subcategory = "Взнос в цель: ${goal.name}",
                amount = amount
            )
            repository.insertTransaction(tx)
            
            try {
                val extraCtx = if (updatedCurrent >= goal.targetAmount) {
                    "Это взнос в цель '${goal.name}'. ПОЛЬЗОВАТЕЛЬ ТОЛЬКО ЧТО ПОЛНОСТЬЮ НАКОПИЛ И ДОСТИГ ЭТОЙ ЦЕЛИ! Прокомментируй это достижение."
                } else {
                    "Это взнос в цель '${goal.name}'. Собрано $updatedCurrent из ${goal.targetAmount} руб. Осталось: ${goal.targetAmount - updatedCurrent} руб."
                }
                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = "expense",
                    category = "Сбережения",
                    subcategory = "Взнос в цель: ${goal.name}",
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx,
                    allTransactions = transactions.value
                )
                val userPhrase = repository.generateUserPhrase(
                    apiKey = _apiKey.value,
                    type = "expense",
                    category = "Сбережения",
                    subcategory = "Взнос в цель: ${goal.name}",
                    amount = amount,
                    isFirstToday = transactions.value.none { it.date == todayIso && it.id != tx.id }
                )
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        budgetId = currentBudgetId,
                        title = "Взнос в цель",
                        description = "||expense|Сбережения|Взнос в цель: ${goal.name}|$amount|$userPhrase||$comment",
                        icon = "david",
                        color = "emerald400",
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )
            } catch (e: Exception) { e.printStackTrace() }

            if (updatedCurrent >= goal.targetAmount) {
                // Goal reached! Delete goal automatically and emit congratulation event
                repository.deleteGoal(goal.id)
                _completedGoalEvent.value = goal.name
            } else {
                repository.insertGoal(goal.copy(currentAmount = updatedCurrent))
                _toastMessage.emit("Взнос сохранен и учтен в расходах!")
            }
        }
    }

    fun saveNewGoal(name: String, target: Double, current: Double) {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Создание финансовой цели: «$name» (цель: $target ₽, начально: $current ₽)")
        viewModelScope.launch {
            if (current > 0) {
                val tx = TransactionEntity(
                    budgetId = currentBudgetId,
                    type = "expense",
                    date = todayIso,
                    category = "Сбережения",
                    subcategory = "Взнос в цель: $name",
                    amount = current
                )
                repository.insertTransaction(tx)
            }

            if (target > 0 && current >= target) {
                // Goal created already at or above 100% target
                _completedGoalEvent.value = name
            } else {
                val goal = GoalEntity(
                    budgetId = currentBudgetId,
                    name = name,
                    targetAmount = target,
                    currentAmount = current
                )
                repository.insertGoal(goal)
                _toastMessage.emit("Финансовая цель добавлена!")
                
                try {
                    val goalSubcategory = "Цель: $name (Целевая сумма: ${target.toInt()} ₽, Внесено: ${current.toInt()} ₽)"
                    val userPhrase = repository.generateUserPhrase(
                        apiKey = _apiKey.value,
                        type = "expense",
                        category = "Цели",
                        subcategory = goalSubcategory,
                        amount = if (current > 0) current else target,
                        isFirstToday = false
                    )
                    val extraCtx = "Создана новая финансовая цель '$name'. Целевая сумма: ${target.toInt()} руб. Первоначальный взнос: ${current.toInt()} руб. (Осталось собрать: ${(target - current).toInt()} руб.). В комментарии ОБЯЗАТЕЛЬНО раздели и учти общую сумму цели (${target.toInt()} ₽) и сколько из неё было внесено первым взносом (${current.toInt()} ₽)!"
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = "expense",
                        category = "Новая цель",
                        subcategory = goalSubcategory,
                        amount = target,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        extraContext = extraCtx,
                        allTransactions = transactions.value
                    )
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = "Новая цель!",
                            description = "||expense|Цели|$name (Цель: ${target.toInt()} ₽, Внесено: ${current.toInt()} ₽)|${if (current > 0) current else target}|$userPhrase||$comment",
                            icon = "david",
                            color = "emerald400",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteGoal(id: String) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Удаление финансовой цели ID: $id")
        viewModelScope.launch {
            repository.deleteGoal(id)
            _toastMessage.emit("Цель удалена")
        }
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
        if (!_isGeminiConsentGiven.value) {
            _aiAuditResult.value = "⚠️ **Ошибка доступа:** Для формирования ИИ-разбора требуется согласие на обработку данных. Пожалуйста, включите разрешение в Настройках приложения."
            return
        }
        val key = _apiKey.value.ifBlank { getSavedApiKey() }
        if (key.isBlank()) {
            _aiAuditResult.value = "ERROR_NO_CONNECTION"
            return
        }
        val bId = _selectedBudgetId.value ?: "default"
        val dateDay = _selectedDateDay.value
        val year = when (_periodType.value) {
            PeriodType.DAY, PeriodType.WEEK -> dateDay.take(4).toIntOrNull() ?: _selectedAnnualYear.value
            else -> _selectedAnnualYear.value
        }
        val month = if (_periodType.value == PeriodType.MONTH) _selectedMonthIdx.value + 1 else 0
        val pKey = currentPeriodKey.value

        val allTxs = transactions.value

        // Compute previous period transactions for comparative analysis
        val previousTransactions = when (_periodType.value) {
            PeriodType.MONTH -> {
                val prevMonthIdx = if (_selectedMonthIdx.value > 0) _selectedMonthIdx.value - 1 else 11
                val prevYear = if (_selectedMonthIdx.value > 0) _selectedAnnualYear.value else _selectedAnnualYear.value - 1
                val prevPrefix = String.format(java.util.Locale.US, "%04d-%02d", prevYear, prevMonthIdx + 1)
                allTxs.filter { it.date.startsWith(prevPrefix) }
            }
            PeriodType.ALL -> {
                val prevYearPrefix = String.format(java.util.Locale.US, "%04d", _selectedAnnualYear.value - 1)
                allTxs.filter { it.date.startsWith(prevYearPrefix) }
            }
            else -> emptyList()
        }

        viewModelScope.launch {
            if (_aiAuditLoading.value) {
                return@launch
            }

            _aiAuditLoading.value = true
            _aiAuditResult.value = ""

            try {
                val monthNames = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
                val periodName = when (_periodType.value) {
                    PeriodType.DAY -> "День ($dateDay)"
                    PeriodType.WEEK -> "Неделя ($dateDay)"
                    PeriodType.MONTH -> "${monthNames.getOrElse(month - 1) { "Месяц" }} $year года"
                    PeriodType.ALL -> "Весь $year год"
                }

                // Save user audit request to chat history DB with unique UUID
                val reqTime = System.currentTimeMillis()
                val reqId = java.util.UUID.randomUUID().toString()
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        id = reqId,
                        budgetId = bId,
                        title = "Запрос аналитики",
                        description = "||audit_req||Давид, проведи аудит за $periodName",
                        icon = "david",
                        color = "indigo500",
                        timestamp = reqTime,
                        isRead = true
                    )
                )

                var fullText = ""
                var currentBlockBuffer = ""
                val baseTime = System.currentTimeMillis()
                var blockCount = 0

                _aiAuditLoading.value = true

                try {
                    repository.requestAiAuditStream(
                        apiKey = key,
                        periodName = periodName,
                        year = year,
                        filteredTransactions = currentFilteredTransactions,
                        previousTransactions = previousTransactions,
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    ).collect { chunk ->
                        fullText += chunk
                        currentBlockBuffer += chunk
                        _aiAuditResult.value = fullText

                        // Streaming Chunk Accumulator: Slice blocks when paragraph separator (\n\n) appears
                        while (currentBlockBuffer.contains("\n\n")) {
                            val parts = currentBlockBuffer.split("\n\n", limit = 2)
                            val completedBlock = parts[0].trim()
                            currentBlockBuffer = parts.getOrElse(1) { "" }

                            if (completedBlock.isNotBlank() && completedBlock != "ERROR_NO_CONNECTION") {
                                blockCount++
                                val blockId = java.util.UUID.randomUUID().toString()
                                val blockTime = baseTime + blockCount * 100L
                                val isFirstBlock = blockCount == 1
                                val blockTitle = if (isFirstBlock) "Жабов Давид (Аналитика)" else "Аналитика"

                                repository.insertNotification(
                                    com.example.data.db.NotificationEntity(
                                        id = blockId,
                                        budgetId = bId,
                                        title = blockTitle,
                                        description = "||audit_block||$completedBlock",
                                        icon = "david",
                                        color = "emerald400",
                                        timestamp = blockTime,
                                        isRead = true
                                    )
                                )
                            }
                        }
                    }

                    // Emit remaining buffer content as the final SMS block
                    val finalBlock = currentBlockBuffer.trim()
                    if (finalBlock.isNotBlank() && finalBlock != "ERROR_NO_CONNECTION") {
                        blockCount++
                        val blockId = java.util.UUID.randomUUID().toString()
                        val blockTime = baseTime + blockCount * 100L
                        val isFirstBlock = blockCount == 1
                        val blockTitle = if (isFirstBlock) "Жабов Давид (Аналитика)" else "Аналитика"

                        repository.insertNotification(
                            com.example.data.db.NotificationEntity(
                                id = blockId,
                                budgetId = bId,
                                title = blockTitle,
                                description = "||audit_block||$finalBlock",
                                icon = "david",
                                color = "emerald400",
                                timestamp = blockTime,
                                isRead = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    fullText = "ERROR_NO_CONNECTION"
                    _aiAuditResult.value = fullText
                }

                if (fullText.isNotEmpty() && !fullText.contains("🏆 **Достижение: Сбой Сети**") && fullText != "ERROR_NO_CONNECTION") {
                    val currentMeme = currentFilteredTransactions.filter { tx ->
                        tx.type == "expense" && (
                            tx.category.contains("Развлечения", ignoreCase = true) ||
                            tx.category.contains("Прочее", ignoreCase = true) ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("мошеннич") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("крипт") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("казик") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("тарелоч") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("альтуш")
                        )
                    }
                    val sillySummaryText = if (currentMeme.isNotEmpty()) {
                        currentMeme.take(3).joinToString("; ") { "${it.subcategory} (${it.amount.toInt()} ₽)" }
                    } else {
                        val topExpense = currentFilteredTransactions.filter { it.type == "expense" }.maxByOrNull { it.amount }
                        if (topExpense != null) "Крупный расход: ${topExpense.category} (${topExpense.amount.toInt()} ₽)" else "Равномерные расходы"
                    }

                    val entity = com.example.data.db.AiAuditEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        budgetId = bId,
                        periodType = _periodType.value.name,
                        periodKey = pKey,
                        year = year,
                        month = month,
                        auditText = fullText,
                        sillyExpensesSummary = sillySummaryText,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.saveAudit(entity)
                }
            } finally {
                _aiAuditLoading.value = false
            }
        }
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
        com.example.utils.GlobalConsoleLogger.i("UI", "Добавление счета/долга: «$name» (тип=$type, сумма=$initialBalance ₽)")
        viewModelScope.launch {
            val bId = _selectedBudgetId.value ?: "default"
            repository.insertAccount(
                AccountEntity(
                    budgetId = bId,
                    name = name,
                    balance = initialBalance,
                    type = type,
                    accountNumber = accountNumber
                )
            )
            
            if (type == "we_owe" || type == "owes_us") {
                try {
                    val debtType = if (type == "we_owe") "Взял долг/кредит" else "Дал в долг"
                    val existingActiveDebts = accounts.value.filter { (it.type == "we_owe" || it.type == "owes_us") && it.balance > 0 }
                    val existingTotalSum = existingActiveDebts.sumOf { it.balance }

                    val debtExtraCtx = if (existingActiveDebts.isNotEmpty()) {
                        "ВНИМАНИЕ! Пользователь только что ${if (type == "we_owe") "взял НОВЫЙ долг/кредит" else "дал НОВЫЙ долг"} '$name' на сумму ${initialBalance.toInt()} руб., ПРИ ТОМ ЧТО У НЕГО УЖЕ ЕСТЬ НЕПОГАШЕННЫЕ ДОЛГИ на общую сумму ${existingTotalSum.toInt()} руб.! (Существующие активные долги: ${existingActiveDebts.joinToString { "${it.name}: ${it.balance.toInt()} ₽" }}). ОБЯЗАТЕЛЬНО жестко отреагируй на это решение брать/давать новые долги при не закрытых старых!"
                    } else {
                        "Пользователь создал новый долг '$name' на сумму ${initialBalance.toInt()} руб."
                    }

                    val userPhrase = repository.generateUserPhrase(
                        apiKey = _apiKey.value,
                        type = if (type == "we_owe") "income" else "expense",
                        category = "Долги/Кредиты",
                        subcategory = "$debtType: $name",
                        amount = initialBalance,
                        isFirstToday = false
                    )
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = if (type == "we_owe") "expense" else "income",
                        category = "Долги/Кредиты",
                        subcategory = "$debtType: $name",
                        amount = initialBalance,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        extraContext = debtExtraCtx,
                        allTransactions = transactions.value
                    )
                    val displayType = if (type == "we_owe") "expense" else "income"
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = bId,
                            title = if (type == "we_owe") "Взяли долг!" else "Дали в долг!",
                            description = "||$displayType|Долги|$name|$initialBalance|$userPhrase||$comment",
                            icon = "david",
                            color = if (type == "owes_us") "emerald400" else "rose500",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteAccount(accountId: String) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Удаление счета/долга ID: $accountId")
        viewModelScope.launch {
            repository.deleteAccountById(accountId)
        }
    }

    fun transferBetweenAccounts(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        fromName: String,
        toName: String
    ) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Перевод $amount ₽ с «$fromName» на «$toName»")
        viewModelScope.launch {
            val bId = _selectedBudgetId.value ?: "default"
            repository.transferBetweenAccounts(
                budgetId = bId,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                fromName = fromName,
                toName = toName
            )
        }
    }

}

