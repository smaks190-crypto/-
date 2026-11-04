package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetProfileDao {
    @Query("SELECT * FROM budget_profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<BudgetProfileEntity>>

    @Query("SELECT * FROM budget_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): BudgetProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BudgetProfileEntity)

    @Query("UPDATE budget_profiles SET name = :newName WHERE id = :id")
    suspend fun renameProfile(id: String, newName: String)

    @Query("DELETE FROM budget_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: String)

    @Query("DELETE FROM budget_profiles")
    suspend fun deleteAllProfiles()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE budgetId = :budgetId ORDER BY date DESC, createdAt DESC, id DESC")
    fun getTransactionsByBudgetId(budgetId: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("SELECT COUNT(*) FROM transactions WHERE date = :dateStr AND type = 'expense'")
    suspend fun getExpenseCountForDate(dateStr: String): Int

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE date = :dateStr AND type = 'expense'")
    suspend fun getExpenseSumForDate(dateStr: String): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE date = :dateStr AND type = 'expense'")
    fun getExpenseCountForDateSync(dateStr: String): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM transactions WHERE budgetId = :budgetId")
    suspend fun deleteTransactionsByBudgetId(budgetId: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id ASC")
    fun getAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE budgetId = :budgetId ORDER BY id ASC")
    fun getGoalsByBudgetId(budgetId: String): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoalById(id: String)

    @Query("DELETE FROM goals WHERE budgetId = :budgetId")
    suspend fun deleteGoalsByBudgetId(budgetId: String)

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE budgetId = :budgetId")
    fun getCategoriesByBudgetId(budgetId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: String)

    @Query("DELETE FROM categories WHERE budgetId = :budgetId")
    suspend fun deleteCategoriesByBudgetId(budgetId: String)

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}

@Dao
interface AiAuditDao {
    @Query("SELECT * FROM ai_audits WHERE budgetId = :budgetId AND periodKey = :periodKey ORDER BY timestamp DESC LIMIT 1")
    fun getAuditForPeriod(budgetId: String, periodKey: String): Flow<AiAuditEntity?>

    @Query("SELECT * FROM ai_audits WHERE budgetId = :budgetId AND year = :year AND periodType = 'MONTH' AND periodKey < :currentPeriodKey ORDER BY month ASC")
    suspend fun getPreviousAuditsInSameYear(budgetId: String, year: Int, currentPeriodKey: String): List<AiAuditEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: AiAuditEntity)

    @Query("DELETE FROM ai_audits WHERE budgetId = :budgetId")
    suspend fun deleteAuditsByBudgetId(budgetId: String)

    @Query("DELETE FROM ai_audits")
    suspend fun deleteAllAudits()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE budgetId = :budgetId")
    fun getAccountsByBudgetId(budgetId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :id")
    suspend fun updateBalance(id: String, delta: Double)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("DELETE FROM accounts WHERE budgetId = :budgetId")
    suspend fun deleteAccountsByBudgetId(budgetId: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE budgetId = :budgetId ORDER BY timestamp ASC, id ASC")
    fun getNotificationsByBudgetId(budgetId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE budgetId = :budgetId")
    suspend fun markAllAsRead(budgetId: String)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: String)

    @Query("DELETE FROM notifications WHERE budgetId = :budgetId")
    suspend fun deleteNotificationsByBudgetId(budgetId: String)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()
}

@Dao
interface RecurringPaymentDao {
    @Query("SELECT * FROM recurring_payments WHERE budgetId = :budgetId ORDER BY nextDueDateEpochDay ASC, id ASC")
    fun getRecurringPaymentsByBudgetId(budgetId: String): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments ORDER BY nextDueDateEpochDay ASC, id ASC")
    fun getAllRecurringPayments(): Flow<List<RecurringPaymentEntity>>

    @Query("SELECT * FROM recurring_payments WHERE id = :id LIMIT 1")
    suspend fun getRecurringPaymentById(id: Long): RecurringPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringPayment(payment: RecurringPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringPayments(payments: List<RecurringPaymentEntity>)

    @Query("UPDATE recurring_payments SET nextDueDateEpochDay = :nextDueDateEpochDay WHERE id = :id")
    suspend fun updateNextDueDate(id: Long, nextDueDateEpochDay: Long)

    @Query("UPDATE recurring_payments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM recurring_payments WHERE id = :id")
    suspend fun deleteRecurringPaymentById(id: Long)

    @Query("DELETE FROM recurring_payments WHERE budgetId = :budgetId")
    suspend fun deleteRecurringPaymentsByBudgetId(budgetId: String)

    @Query("DELETE FROM recurring_payments")
    suspend fun deleteAllRecurringPayments()

    @Query("SELECT * FROM recurring_execution_logs WHERE budgetId = :budgetId ORDER BY scheduledEpochDay DESC, id DESC")
    fun getExecutionLogsByBudgetId(budgetId: String): Flow<List<RecurringExecutionLogEntity>>

    @Query("SELECT * FROM recurring_execution_logs ORDER BY scheduledEpochDay DESC, id DESC")
    fun getAllExecutionLogs(): Flow<List<RecurringExecutionLogEntity>>

    @Query("SELECT * FROM recurring_execution_logs WHERE linkedTransactionId = :transactionId LIMIT 1")
    suspend fun getExecutionLogByTransactionId(transactionId: String): RecurringExecutionLogEntity?

    @Query("SELECT * FROM recurring_execution_logs WHERE recurringId = :recurringId AND scheduledEpochDay = :scheduledEpochDay LIMIT 1")
    suspend fun getExecutionLogBySchedule(recurringId: Long, scheduledEpochDay: Long): RecurringExecutionLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutionLog(log: RecurringExecutionLogEntity): Long

    @Query("DELETE FROM recurring_execution_logs WHERE budgetId = :budgetId")
    suspend fun deleteExecutionLogsByBudgetId(budgetId: String)

    @Query("DELETE FROM recurring_execution_logs")
    suspend fun deleteAllExecutionLogs()
}
