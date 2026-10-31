package com.example.data.repository

import com.example.utils.GlobalConsoleLogger
import com.example.utils.NetworkCallStatus
import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiModelConfig
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.RetrofitClient
import androidx.room.withTransaction
import com.example.data.db.AccountDao
import com.example.data.db.AccountEntity
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetProfileDao
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.CategoryDao
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalDao
import com.example.data.db.GoalEntity
import com.example.data.db.NotificationDao
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BudgetBackup(
    val profileName: String? = null,
    val transactions: List<TransactionEntity>? = emptyList(),
    val goals: List<GoalEntity>? = emptyList(),
    val categories: List<CategoryEntity>? = emptyList(),
    val accounts: List<AccountEntity>? = emptyList(),
    val achievements: Map<String, Long>? = null
)

class BudgetRepository(
    context: android.content.Context,
    private val budgetProfileDao: BudgetProfileDao,
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val categoryDao: CategoryDao,
    private val aiAuditDao: com.example.data.db.AiAuditDao,
    private val accountDao: AccountDao,
    private val notificationDao: NotificationDao,
    private val db: AppDatabase,
    private val apiService: GeminiApiService = RetrofitClient.service,
    private val recurringPaymentDao: com.example.data.db.RecurringPaymentDao = db.recurringPaymentDao()
) {
    private val context: android.content.Context = context.applicationContext
    val allProfiles: Flow<List<BudgetProfileEntity>> = budgetProfileDao.getAllProfiles()

    fun getAccountsForBudget(budgetId: String): Flow<List<AccountEntity>> =
        accountDao.getAccountsByBudgetId(budgetId)

    fun getNotificationsForBudget(budgetId: String): Flow<List<NotificationEntity>> =
        notificationDao.getNotificationsByBudgetId(budgetId)

    fun getRecurringPaymentsForBudget(budgetId: String): Flow<List<com.example.data.db.RecurringPaymentEntity>> =
        recurringPaymentDao.getRecurringPaymentsByBudgetId(budgetId)

    fun getExecutionLogsForBudget(budgetId: String): Flow<List<com.example.data.db.RecurringExecutionLogEntity>> =
        recurringPaymentDao.getExecutionLogsByBudgetId(budgetId)

    suspend fun insertAccount(account: AccountEntity) {
        accountDao.insertAccount(account)
    }

    suspend fun deleteAccountById(id: String) {
        accountDao.deleteAccountById(id)
    }

    suspend fun transferBetweenAccounts(
        budgetId: String,
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        fromName: String,
        toName: String
    ) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            db.withTransaction {
                accountDao.updateBalance(fromAccountId, -amount)
                accountDao.updateBalance(toAccountId, amount)
                notificationDao.insertNotification(
                    NotificationEntity(
                        budgetId = budgetId,
                        title = "Перевод между счетами",
                        description = "Переведено ${String.format(Locale.US, "%.0f", amount)} ₽ со счета \"$fromName\" на счет \"$toName\"",
                        icon = "repeat",
                        color = "indigo500",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            GlobalConsoleLogger.recordDbOperation(
                operationName = "TRANSFER accounts",
                tableName = "accounts",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "budgetId=$budgetId | $fromName -> $toName",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "TRANSFER accounts",
                tableName = "accounts",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun insertNotification(notification: NotificationEntity) {
        GlobalConsoleLogger.i("NOTIFICATION", "Создано новое уведомление: [${notification.title}] ${notification.description}")
        notificationDao.insertNotification(notification)
    }

    suspend fun markNotificationsAsRead(budgetId: String) {
        GlobalConsoleLogger.i("NOTIFICATION", "Отметка всех уведомлений как прочитанных (бюджет: $budgetId)")
        notificationDao.markAllAsRead(budgetId)
    }


    private val aiAuditService = DavidAiAuditService(this.context, apiService)
    private val voiceParserService = VoiceOperationParserService(apiService)

    suspend fun generateDavidComment(
        apiKey: String,
        type: String,
        category: String,
        subcategory: String,
        amount: Double,
        recentTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<com.example.data.db.AccountEntity> = emptyList(),
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList(),
        extraContext: String = "",
        allTransactions: List<TransactionEntity> = emptyList()
    ): String = aiAuditService.generateDavidComment(
        apiKey = apiKey,
        type = type,
        category = category,
        subcategory = subcategory,
        amount = amount,
        recentTransactions = recentTransactions,
        activeDebts = activeDebts,
        activeGoals = activeGoals,
        extraContext = extraContext,
        allTransactions = allTransactions
    )

    suspend fun generateDavidChatAnswer(
        apiKey: String,
        profileName: String,
        userMessage: String,
        snapshot: FinancialSemanticsSnapshot,
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList(),
        conversationHistory: List<String> = emptyList(),
        safeToSpendResult: com.example.data.safetospend.SafeToSpendResult? = null,
        whatIfResult: com.example.data.safetospend.WhatIfSimulationResult? = null
    ): String = aiAuditService.generateDavidChatAnswer(
        apiKey = apiKey,
        profileName = profileName,
        userMessage = userMessage,
        snapshot = snapshot,
        activeGoals = activeGoals,
        conversationHistory = conversationHistory,
        safeToSpendResult = safeToSpendResult,
        whatIfResult = whatIfResult
    )

    suspend fun generateDavidCommentMulti(
        apiKey: String,
        operations: List<ParsedVoiceOperation>,
        recentTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<AccountEntity> = emptyList(),
        activeGoals: List<GoalEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList()
    ): String = aiAuditService.generateDavidCommentMulti(
        apiKey = apiKey,
        operations = operations,
        recentTransactions = recentTransactions,
        activeDebts = activeDebts,
        activeGoals = activeGoals,
        allTransactions = allTransactions
    )

    suspend fun generateUserPhraseMulti(
        apiKey: String,
        operations: List<ParsedVoiceOperation>
    ): String = aiAuditService.generateUserPhraseMulti(apiKey, operations)

    suspend fun generateUserPhrase(
        apiKey: String,
        type: String,
        category: String,
        subcategory: String,
        amount: Double,
        isFirstToday: Boolean
    ): String = aiAuditService.generateUserPhrase(
        apiKey = apiKey,
        type = type,
        category = category,
        subcategory = subcategory,
        amount = amount,
        isFirstToday = isFirstToday
    )

    fun getTransactionsForBudget(budgetId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByBudgetId(budgetId)

    fun getGoalsForBudget(budgetId: String): Flow<List<GoalEntity>> =
        goalDao.getGoalsByBudgetId(budgetId)

    fun getCategoriesForBudget(budgetId: String): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByBudgetId(budgetId)

    fun getAuditForPeriod(budgetId: String, periodKey: String): Flow<com.example.data.db.AiAuditEntity?> =
        aiAuditDao.getAuditForPeriod(budgetId, periodKey)

    suspend fun saveAudit(audit: com.example.data.db.AiAuditEntity) {
        aiAuditDao.insertAudit(audit)
    }

    suspend fun getPreviousAuditsInSameYear(budgetId: String, year: Int, currentPeriodKey: String): List<com.example.data.db.AiAuditEntity> =
        aiAuditDao.getPreviousAuditsInSameYear(budgetId, year, currentPeriodKey)

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allGoals: Flow<List<GoalEntity>> = goalDao.getAllGoals()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allRecurringPayments: Flow<List<com.example.data.db.RecurringPaymentEntity>> = recurringPaymentDao.getAllRecurringPayments()
    val allExecutionLogs: Flow<List<com.example.data.db.RecurringExecutionLogEntity>> = recurringPaymentDao.getAllExecutionLogs()


    private val backupManager = BudgetBackupManager(
        context = this.context,
        budgetProfileDao = budgetProfileDao,
        transactionDao = transactionDao,
        goalDao = goalDao,
        categoryDao = categoryDao,
        accountDao = accountDao,
        allProfiles = allProfiles,
        allTransactions = allTransactions,
        allGoals = allGoals,
        allCategories = allCategories,
        allAccounts = allAccounts,
        createProfile = ::createProfile,
        clearAllData = ::clearAllData,
        initializeDefaultCategories = ::initializeDefaultCategories
    )

    suspend fun syncToFile() = backupManager.syncToFile()

    suspend fun createProfile(name: String): BudgetProfileEntity {
        val profile = BudgetProfileEntity(name = name)
        budgetProfileDao.insertProfile(profile)
        initializeDefaultCategories(profile.id)
        return profile
    }

    suspend fun renameProfile(id: String, newName: String) {
        budgetProfileDao.renameProfile(id, newName)
    }

    suspend fun deleteProfile(id: String) {
        budgetProfileDao.deleteProfileById(id)
        transactionDao.deleteTransactionsByBudgetId(id)
        goalDao.deleteGoalsByBudgetId(id)
        aiAuditDao.deleteAuditsByBudgetId(id)
        categoryDao.deleteCategoriesByBudgetId(id)
        accountDao.deleteAccountsByBudgetId(id)
        notificationDao.deleteNotificationsByBudgetId(id)
        recurringPaymentDao.deleteExecutionLogsByBudgetId(id)
        recurringPaymentDao.deleteRecurringPaymentsByBudgetId(id)
    }

    suspend fun clearAllData() {
        transactionDao.deleteAllTransactions()
        goalDao.deleteAllGoals()
        categoryDao.deleteAllCategories()
        aiAuditDao.deleteAllAudits()
        budgetProfileDao.deleteAllProfiles()
        accountDao.deleteAllAccounts()
        notificationDao.deleteAllNotifications()
        recurringPaymentDao.deleteAllExecutionLogs()
        recurringPaymentDao.deleteAllRecurringPayments()
        backupManager.deleteStorageFile()
    }

    // budget_storage.json больше не поддерживается в актуальном состоянии на каждую запись и используется только для миграции легаси-данных
    suspend fun ensureDefaultProfileExists(): BudgetProfileEntity {
        val existing = allProfiles.first()
        if (existing.isEmpty()) {
            val default = BudgetProfileEntity(id = "default", name = "Казума Сато")
            budgetProfileDao.insertProfile(default)
            loadDemoData("default")
            return default
        } else {
            val firstProfile = existing.first()
            val currentTxs = allTransactions.first()
            if (currentTxs.isEmpty()) {
                loadDemoData(firstProfile.id)
            } else if (firstProfile.name == "Казума Сато" && currentTxs.none { it.subcategory.contains("Белуга") }) {
                // Автоматическое обновление старого неполного демо-бюджета на полный сатирический год
                loadDemoData(firstProfile.id)
            }
        }
        return existing.first()
    }


    suspend fun insertTransaction(transaction: TransactionEntity) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            db.withTransaction {
                transactionDao.insertTransaction(transaction)
                val linkedLog = recurringPaymentDao.getExecutionLogByTransactionId(transaction.id)
                val syncedLog = com.example.data.safetospend.RecurringTransactionConsistencyManager
                    .buildEditTransactionSyncTransition(transaction, linkedLog)
                if (syncedLog != null) {
                    recurringPaymentDao.insertExecutionLog(syncedLog)
                }
            }
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "type=${transaction.type.uppercase()} | cat=${transaction.category}",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
        if (transaction.type.equals("expense", ignoreCase = true) &&
            !transaction.category.equals("Сбережения", ignoreCase = true)
        ) {
            withContext(Dispatchers.Main) {
                com.example.notifications.ReminderManager.evaluateAndTriggerCroakAlertIfNeeded(
                    context = context,
                    type = transaction.type,
                    category = transaction.category,
                    subcategory = transaction.subcategory,
                    amount = transaction.amount
                )
            }
        }
    }

    suspend fun deleteTransaction(id: String) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            db.withTransaction {
                val linkedLog = recurringPaymentDao.getExecutionLogByTransactionId(id)
                if (linkedLog != null) {
                    val payment = recurringPaymentDao.getRecurringPaymentById(linkedLog.recurringId)
                    val rollback = com.example.data.safetospend.RecurringTransactionConsistencyManager
                        .buildDeleteTransactionRollbackTransition(
                            deletedTransactionId = id,
                            linkedLog = linkedLog,
                            payment = payment
                        )
                    rollback.revertedLog?.let { recurringPaymentDao.insertExecutionLog(it) }
                    rollback.updatedPayment?.let { recurringPaymentDao.insertRecurringPayment(it) }
                }
                transactionDao.deleteTransactionById(id)
            }
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "id=$id",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun insertRecurringPayment(payment: com.example.data.db.RecurringPaymentEntity): Long {
        return recurringPaymentDao.insertRecurringPayment(payment)
    }

    suspend fun deleteRecurringPayment(id: Long) {
        recurringPaymentDao.deleteRecurringPaymentById(id)
    }

    suspend fun updateRecurringPaymentStatus(id: Long, status: String) {
        recurringPaymentDao.updateStatus(id, status)
    }

    suspend fun rescheduleRecurringPayment(
        id: Long,
        oldScheduledEpochDay: Long,
        newDueDateEpochDay: Long
    ) {
        db.withTransaction {
            val payment = recurringPaymentDao.getRecurringPaymentById(id) ?: return@withTransaction
            val existingLog = recurringPaymentDao.getExecutionLogBySchedule(id, oldScheduledEpochDay)
            if (existingLog != null) {
                recurringPaymentDao.insertExecutionLog(
                    existingLog.copy(status = com.example.data.safetospend.ExecutionStatus.RESCHEDULED.name)
                )
            }
            recurringPaymentDao.updateNextDueDate(payment.id, newDueDateEpochDay)
        }
    }

    suspend fun confirmRecurringPaymentExecution(
        paymentId: Long,
        scheduledEpochDay: Long? = null,
        executedEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
        actualAmount: Double? = null
    ): TransactionEntity? {
        return db.withTransaction {
            val payment = recurringPaymentDao.getRecurringPaymentById(paymentId) ?: return@withTransaction null
            val targetScheduled = scheduledEpochDay ?: payment.nextDueDateEpochDay
            val existingLog = recurringPaymentDao.getExecutionLogBySchedule(paymentId, targetScheduled)
            val transition = com.example.data.safetospend.RecurringTransactionConsistencyManager.buildConfirmationTransition(
                payment = payment,
                scheduledEpochDay = targetScheduled,
                executedEpochDay = executedEpochDay,
                actualAmount = actualAmount ?: payment.amount,
                existingLogId = existingLog?.id ?: 0L
            )
            transactionDao.insertTransaction(transition.transaction)
            recurringPaymentDao.insertRecurringPayment(transition.updatedPayment)
            recurringPaymentDao.insertExecutionLog(transition.executionLog)
            transition.transaction
        }
    }

    suspend fun insertGoal(goal: GoalEntity) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            goalDao.insertGoal(goal)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "name=${goal.name}",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun deleteGoal(id: String) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            goalDao.deleteGoalById(id)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "id=$id",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun insertCategory(category: CategoryEntity) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            categoryDao.insertCategory(category)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "name=${category.name} (${category.type})",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun deleteCategory(id: String) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            categoryDao.deleteCategoryById(id)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "id=$id",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun initializeDefaultCategories(budgetId: String = "default") {
        val existing = getCategoriesForBudget(budgetId).first()
        if (existing.isEmpty()) {
            val defaults = listOf(
                CategoryEntity(budgetId = budgetId, type = "income", name = "Зарплата"),
                CategoryEntity(budgetId = budgetId, type = "income", name = "Подработка"),
                CategoryEntity(budgetId = budgetId, type = "income", name = "Случайные доходы"),
                CategoryEntity(budgetId = budgetId, type = "expense", name = "Обязательные"),
                CategoryEntity(budgetId = budgetId, type = "expense", name = "Продукты"),
                CategoryEntity(budgetId = budgetId, type = "expense", name = "Развлечения"),
                CategoryEntity(budgetId = budgetId, type = "expense", name = "Книги"),
                CategoryEntity(budgetId = budgetId, type = "expense", name = "Сбережения"),
                CategoryEntity(budgetId = budgetId, type = "expense", name = "Прочее")
            )
            categoryDao.insertCategories(defaults)
        } else {
            val hasBooks = existing.any { it.type == "expense" && it.name.equals("Книги", ignoreCase = true) }
            if (!hasBooks) {
                categoryDao.insertCategory(CategoryEntity(budgetId = budgetId, type = "expense", name = "Книги"))
            }
        }
    }


    suspend fun loadDemoData(budgetId: String = "default") =
        backupManager.loadDemoData(budgetId)

    suspend fun exportJson(): String =
        backupManager.exportJson()

    suspend fun exportJsonForBudget(budgetId: String): String =
        backupManager.exportJsonForBudget(budgetId)

    suspend fun importBackupAsNewBudget(jsonStr: String, customName: String? = null): BudgetProfileEntity? =
        backupManager.importBackupAsNewBudget(jsonStr, customName)

    suspend fun importJson(jsonStr: String, targetBudgetId: String? = null): Boolean =
        backupManager.importJson(jsonStr, targetBudgetId)

    fun requestAiAuditStream(
        apiKey: String,
        periodName: String,
        year: Int,
        filteredTransactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<com.example.data.db.AccountEntity> = emptyList(),
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList()
    ): Flow<String> = aiAuditService.requestAiAuditStream(
        apiKey = apiKey,
        periodName = periodName,
        year = year,
        filteredTransactions = filteredTransactions,
        previousTransactions = previousTransactions,
        activeDebts = activeDebts,
        activeGoals = activeGoals,
        allTransactions = allTransactions
    )

    suspend fun requestAiAudit(
        apiKey: String,
        periodName: String,
        year: Int,
        filteredTransactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<com.example.data.db.AccountEntity> = emptyList(),
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList()
    ): Result<String> = aiAuditService.requestAiAudit(
        apiKey = apiKey,
        periodName = periodName,
        year = year,
        filteredTransactions = filteredTransactions,
        previousTransactions = previousTransactions,
        activeDebts = activeDebts,
        activeGoals = activeGoals,
        allTransactions = allTransactions
    )

    suspend fun suggestCategory(
        apiKey: String,
        transactionName: String,
        type: String,
        categories: List<String>
    ): String = aiAuditService.suggestCategory(
        apiKey = apiKey,
        transactionName = transactionName,
        type = type,
        categories = categories
    )

    suspend fun parseVoiceOperations(
        voiceText: String,
        apiKey: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ): List<ParsedVoiceOperation> = voiceParserService.parseVoiceOperations(
        voiceText = voiceText,
        apiKey = apiKey,
        expenseCategories = expenseCategories,
        incomeCategories = incomeCategories
    )

    suspend fun parseVoiceAudioOperations(
        audioBytes: ByteArray,
        apiKey: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        mimeType: String = "audio/wav"
    ): List<ParsedVoiceOperation> = voiceParserService.parseVoiceAudioOperations(
        audioBytes = audioBytes,
        apiKey = apiKey,
        expenseCategories = expenseCategories,
        incomeCategories = incomeCategories,
        mimeType = mimeType
    )

    companion object {
        val VOICE_TEXT_MODELS: List<String> = VoiceOperationParserService.VOICE_TEXT_MODELS

        internal fun extractJsonArray(rawText: String): String =
            VoiceOperationParserService.extractJsonArray(rawText)

        internal fun sanitizeJsonStr(s: String?): String =
            VoiceOperationParserService.sanitizeJsonStr(s)

        internal fun parseJsonOperations(jsonString: String, defaultDate: String): List<ParsedVoiceOperation> =
            VoiceOperationParserService.parseJsonOperations(jsonString, defaultDate)

        internal suspend fun executeVoiceTextParsingWithFallback(
            apiService: GeminiApiService,
            cleanKey: String,
            request: GeminiRequest,
            todayStr: String,
            opId: String,
            modelsToTry: List<String> = VOICE_TEXT_MODELS
        ): List<ParsedVoiceOperation> = VoiceOperationParserService.executeVoiceTextParsingWithFallback(
            apiService = apiService,
            cleanKey = cleanKey,
            request = request,
            todayStr = todayStr,
            opId = opId,
            modelsToTry = modelsToTry
        )
    }
}
