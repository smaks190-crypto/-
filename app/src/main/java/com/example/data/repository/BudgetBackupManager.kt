package com.example.data.repository

import android.content.Context
import android.util.AtomicFile
import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetProfileEntity
import com.example.davidapp.AchievementManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID

/** Database changes commit together; preferences and cache files are updated after commit. */
class BudgetBackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val clearAllData: suspend () -> Unit,
    private val initializeDefaultCategories: suspend (String) -> Unit
) {
    private val codec = BudgetBackupCodec()
    private val storageFile = File(context.filesDir, "budget_storage.json")

    fun deleteStorageFile() {
        AtomicFile(storageFile).delete()
    }

    private fun cacheBestEffort(file: File, json: String) {
        val atomic = AtomicFile(file)
        try {
            val stream = atomic.startWrite()
            try {
                stream.write(json.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                atomic.failWrite(stream)
                throw e
            }
        } catch (_: Exception) {
            // Room is authoritative; a cache failure must not report a committed import as failed.
        }
    }

    private suspend fun snapshot(budgetId: String? = null): BudgetBackup = db.withTransaction {
        val profiles = db.budgetProfileDao().getAllProfiles().first()
            .filter { budgetId == null || it.id == budgetId }
        require(profiles.isNotEmpty()) { "Budget not found" }
        BudgetBackup(
            formatVersion = 2,
            profiles = profiles,
            profileName = profiles.first().name,
            accounts = db.accountDao().getAllAccounts().first().filter { budgetId == null || it.budgetId == budgetId },
            transactions = db.transactionDao().getAllTransactions().first().filter { budgetId == null || it.budgetId == budgetId },
            goals = db.goalDao().getAllGoals().first().filter { budgetId == null || it.budgetId == budgetId },
            categories = db.categoryDao().getAllCategories().first().filter { budgetId == null || it.budgetId == budgetId },
            recurringPayments = db.recurringPaymentDao().getAllRecurringPayments().first().filter { budgetId == null || it.budgetId == budgetId },
            executionLogs = db.recurringPaymentDao().getAllExecutionLogs().first().filter { budgetId == null || it.budgetId == budgetId },
            achievements = AchievementManager.exportUnlockedIds(context)
        )
    }

    suspend fun syncToFile() = withContext(Dispatchers.IO) {
        try {
            cacheBestEffort(storageFile, codec.encode(snapshot()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Legacy cache only; normal edits persist directly in Room.
        }
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        codec.encode(snapshot()).also { cacheBestEffort(storageFile, it) }
    }

    suspend fun exportJsonForBudget(budgetId: String): String = withContext(Dispatchers.IO) {
        codec.encode(snapshot(budgetId)).also {
            cacheBestEffort(File(context.filesDir, "budget_${budgetId}.json"), it)
        }
    }

    private suspend fun deleteBudgetData(budgetId: String) {
        db.recurringPaymentDao().deleteExecutionLogsByBudgetId(budgetId)
        db.recurringPaymentDao().deleteRecurringPaymentsByBudgetId(budgetId)
        db.transactionDao().deleteTransactionsByBudgetId(budgetId)
        db.goalDao().deleteGoalsByBudgetId(budgetId)
        db.categoryDao().deleteCategoriesByBudgetId(budgetId)
        db.accountDao().deleteAccountsByBudgetId(budgetId)
        db.notificationDao().deleteNotificationsByBudgetId(budgetId)
        db.aiAuditDao().deleteAuditsByBudgetId(budgetId)
    }

    /** Runs inside the caller's Room transaction, including profile creation and recurrence ID remapping. */
    private suspend fun insertBackup(backup: BudgetBackup): BudgetBackup {
        backup.profiles.orEmpty().forEach { db.budgetProfileDao().insertProfile(it) }
        db.accountDao().insertAccounts(backup.accounts.orEmpty())
        db.categoryDao().insertCategories(backup.categories.orEmpty())
        db.goalDao().insertGoals(backup.goals.orEmpty())
        db.transactionDao().insertTransactions(backup.transactions.orEmpty())
        val paymentIds = mutableMapOf<Long, Long>()
        val payments = backup.recurringPayments.orEmpty().map { old ->
            val id = db.recurringPaymentDao().insertRecurringPayment(old.copy(id = 0L))
            paymentIds[old.id] = id
            old.copy(id = id)
        }
        val logs = backup.executionLogs.orEmpty().map { old ->
            val log = old.copy(id = 0L, recurringId = paymentIds.getValue(old.recurringId))
            val id = db.recurringPaymentDao().insertExecutionLog(log)
            log.copy(id = id)
        }
        return backup.copy(recurringPayments = payments, executionLogs = logs)
    }

    private fun restoreAchievementsBestEffort(backup: BudgetBackup) {
        try {
            backup.achievements?.let { AchievementManager.importUnlockedIds(context, it) }
        } catch (_: Exception) {
            // Supplementary preferences live outside the financial database transaction.
        }
    }

    suspend fun importBackupAsNewBudget(jsonStr: String, customName: String? = null): BudgetProfileEntity? = withContext(Dispatchers.IO) {
        try {
            val prepared = codec.prepare(codec.decode(jsonStr), UUID.randomUUID().toString(), customName)
            val restored = db.withTransaction { insertBackup(prepared) }
            restoreAchievementsBestEffort(restored)
            val profile = restored.profiles.orEmpty().single()
            cacheBestEffort(File(context.filesDir, "budget_${profile.id}.json"), codec.encode(restored))
            profile
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun importJson(jsonStr: String, targetBudgetId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val prepared = codec.prepare(codec.decode(jsonStr), targetBudgetId)
            val restored = db.withTransaction {
                if (targetBudgetId == null) clearAllData() else deleteBudgetData(targetBudgetId)
                insertBackup(prepared)
            }
            restoreAchievementsBestEffort(restored)
            if (targetBudgetId == null) cacheBestEffort(storageFile, codec.encode(restored))
            else cacheBestEffort(File(context.filesDir, "budget_${targetBudgetId}.json"), codec.encode(restored))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadDemoData(budgetId: String = "default") = withContext(Dispatchers.IO) {
        try {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val json = context.assets.open("demo_budget.json").bufferedReader().use { it.readText() }
                .replace("{YEAR}", "$year")
                .replace("{YEAR-1}", "${year - 1}")
                .replace("{YEAR-2}", "${year - 2}")
            val prepared = codec.prepare(codec.decode(json), budgetId)
            db.withTransaction {
                val existing = db.budgetProfileDao().getProfileById(budgetId)
                deleteBudgetData(budgetId)
                insertBackup(prepared.copy(profiles = listOf(existing ?: prepared.profiles.orEmpty().single())))
            }
            syncToFile()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            initializeDefaultCategories(budgetId)
        }
    }
}
