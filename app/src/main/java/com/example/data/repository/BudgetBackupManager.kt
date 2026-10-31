package com.example.data.repository

import android.content.Context
import com.example.data.db.AccountDao
import com.example.data.db.AccountEntity
import com.example.data.db.BudgetProfileDao
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.CategoryDao
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalDao
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Менеджер резервного копирования (JSON Export/Import), файловой синхронизации и демо-данных (Issue #69, Этап 3).
 */
class BudgetBackupManager(
    private val context: Context,
    private val budgetProfileDao: BudgetProfileDao,
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val allProfiles: Flow<List<BudgetProfileEntity>>,
    private val allTransactions: Flow<List<TransactionEntity>>,
    private val allGoals: Flow<List<GoalEntity>>,
    private val allCategories: Flow<List<CategoryEntity>>,
    private val allAccounts: Flow<List<AccountEntity>>,
    private val createProfile: suspend (String) -> BudgetProfileEntity,
    private val clearAllData: suspend () -> Unit,
    private val initializeDefaultCategories: suspend (String) -> Unit
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val backupAdapter = moshi.adapter(BudgetBackup::class.java)
    private val storageFile = File(context.filesDir, "budget_storage.json")

    fun deleteStorageFile() {
        if (storageFile.exists()) {
            try { storageFile.delete() } catch (_: Exception) {}
        }
    }

    suspend fun syncToFile() {
        try {
            val profileName = allProfiles.first().firstOrNull()?.name
            val txs = allTransactions.first()
            val goals = allGoals.first()
            val cats = allCategories.first()
            val accs = allAccounts.first()
            val backup = BudgetBackup(
                profileName = profileName,
                transactions = txs,
                goals = goals,
                categories = cats,
                accounts = accs
            )
            val json = backupAdapter.toJson(backup)
            storageFile.writeText(json)
        } catch (_: Exception) {}
    }

    suspend fun loadDemoData(budgetId: String = "default") = withContext(Dispatchers.IO) {
        try {
            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val assetJson = context.assets.open("demo_budget.json").bufferedReader().use { it.readText() }
            val jsonWithYear = assetJson
                .replace("{YEAR}", "$currentYear")
                .replace("{YEAR-1}", "${currentYear - 1}")
                .replace("{YEAR-2}", "${currentYear - 2}")
            val backup = backupAdapter.fromJson(jsonWithYear)
            if (backup != null) {
                transactionDao.deleteTransactionsByBudgetId(budgetId)
                goalDao.deleteGoalsByBudgetId(budgetId)
                categoryDao.deleteCategoriesByBudgetId(budgetId)
                accountDao.deleteAccountsByBudgetId(budgetId)

                val accountIdMap = mutableMapOf<String, String>()
                val accs = (backup.accounts ?: emptyList()).map { oldAcc ->
                    val newId = if (budgetId == "default") oldAcc.id else "${oldAcc.id}_$budgetId"
                    accountIdMap[oldAcc.id] = newId
                    oldAcc.copy(id = newId, budgetId = budgetId)
                }
                val txs = (backup.transactions ?: emptyList()).map { oldTx ->
                    val mappedAccId = oldTx.accountId?.let { accountIdMap[it] ?: it }
                    oldTx.copy(budgetId = budgetId, accountId = mappedAccId)
                }
                val goals = (backup.goals ?: emptyList()).map { it.copy(budgetId = budgetId) }
                val cats = (backup.categories ?: emptyList()).map { it.copy(budgetId = budgetId) }

                if (accs.isNotEmpty()) accountDao.insertAccounts(accs)
                if (cats.isNotEmpty()) categoryDao.insertCategories(cats)
                if (goals.isNotEmpty()) goalDao.insertGoals(goals)
                if (txs.isNotEmpty()) transactionDao.insertTransactions(txs)
                syncToFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            initializeDefaultCategories(budgetId)
        }
    }


    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val currentProfiles = allProfiles.first()
        val profile = currentProfiles.firstOrNull()
        val txs = allTransactions.first()
        val goals = allGoals.first()
        val cats = allCategories.first()
        val accs = allAccounts.first()
        val backup = BudgetBackup(
            profileName = profile?.name,
            transactions = txs,
            goals = goals,
            categories = cats,
            accounts = accs,
            achievements = com.example.davidapp.AchievementManager.exportUnlockedIds(context)
        )
        val json = backupAdapter.toJson(backup)
        try {
            storageFile.writeText(json)
        } catch (_: Exception) {}
        json
    }

    suspend fun exportJsonForBudget(budgetId: String): String = withContext(Dispatchers.IO) {
        val profile = allProfiles.first().firstOrNull { it.id == budgetId }
        val profileName = profile?.name
        val txs = transactionDao.getTransactionsByBudgetId(budgetId).first()
        val goals = goalDao.getGoalsByBudgetId(budgetId).first()
        val cats = categoryDao.getCategoriesByBudgetId(budgetId).first()
        val accs = accountDao.getAccountsByBudgetId(budgetId).first()
        val backup = BudgetBackup(
            profileName = profileName,
            transactions = txs,
            goals = goals,
            categories = cats,
            accounts = accs,
            achievements = com.example.davidapp.AchievementManager.exportUnlockedIds(context)
        )
        val json = backupAdapter.toJson(backup)
        try {
            val file = java.io.File(context.filesDir, "budget_${budgetId}.json")
            file.writeText(json)
        } catch (_: Exception) {}
        json
    }

    suspend fun importBackupAsNewBudget(jsonStr: String, customName: String? = null): BudgetProfileEntity? = withContext(Dispatchers.IO) {
        try {
            val backup = backupAdapter.fromJson(jsonStr) ?: return@withContext null
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            val name = customName ?: backup.profileName?.takeIf { it.isNotBlank() } ?: "Бюджет из копии ($dateStr)"
            val profile = createProfile(name)
            val newBudgetId = profile.id

            val rawTxs = backup.transactions ?: emptyList()
            val rawGoals = backup.goals ?: emptyList()
            val rawCats = backup.categories ?: emptyList()
            val rawAccs = backup.accounts ?: emptyList()

            // Map old account IDs to new UUIDs to prevent collision and preserve foreign key relationships in transactions
            val accountIdMap = mutableMapOf<String, String>()
            val accs = rawAccs.map { oldAcc ->
                val newId = java.util.UUID.randomUUID().toString()
                accountIdMap[oldAcc.id] = newId
                oldAcc.copy(id = newId, budgetId = newBudgetId)
            }

            val txs = rawTxs.map { oldTx ->
                val mappedAccountId = oldTx.accountId?.let { accountIdMap[it] } ?: oldTx.accountId
                oldTx.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    budgetId = newBudgetId,
                    accountId = mappedAccountId
                )
            }
            val goals = rawGoals.map { it.copy(id = java.util.UUID.randomUUID().toString(), budgetId = newBudgetId) }
            val cats = rawCats.map { it.copy(id = java.util.UUID.randomUUID().toString(), budgetId = newBudgetId) }

            if (accs.isNotEmpty()) accountDao.insertAccounts(accs)
            if (txs.isNotEmpty()) transactionDao.insertTransactions(txs)
            if (goals.isNotEmpty()) goalDao.insertGoals(goals)
            if (cats.isNotEmpty()) categoryDao.insertCategories(cats)

            // Восстановление достижений с обратной совместимостью
            if (backup.achievements != null && backup.achievements.isNotEmpty()) {
                com.example.davidapp.AchievementManager.importUnlockedIds(context, backup.achievements)
            } else {
                com.example.utils.ExportImportHelper.extractAndImportAchievements(context, jsonStr)
            }

            val file = java.io.File(context.filesDir, "budget_${newBudgetId}.json")
            val updatedBackup = BudgetBackup(
                profileName = name,
                transactions = txs,
                goals = goals,
                categories = cats,
                accounts = accs,
                achievements = com.example.davidapp.AchievementManager.exportUnlockedIds(context)
            )
            file.writeText(backupAdapter.toJson(updatedBackup))
            profile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun importJson(jsonStr: String, targetBudgetId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = backupAdapter.fromJson(jsonStr) ?: return@withContext false
            clearAllData()

            val bId = targetBudgetId ?: "default"
            val profileName = backup.profileName?.takeIf { it.isNotBlank() } ?: "Основной"
            budgetProfileDao.insertProfile(BudgetProfileEntity(id = bId, name = profileName))

            val rawTxs = backup.transactions ?: emptyList()
            val rawGoals = backup.goals ?: emptyList()
            val rawCats = backup.categories ?: emptyList()
            val rawAccs = backup.accounts ?: emptyList()

            val accs = rawAccs.map { if (it.budgetId.isBlank()) it.copy(budgetId = bId) else it }
            val txs = rawTxs.map { if (it.budgetId.isBlank()) it.copy(budgetId = bId) else it }
            val goals = rawGoals.map { if (it.budgetId.isBlank()) it.copy(budgetId = bId) else it }
            val cats = rawCats.map { if (it.budgetId.isBlank()) it.copy(budgetId = bId) else it }

            if (accs.isNotEmpty()) accountDao.insertAccounts(accs)
            if (txs.isNotEmpty()) transactionDao.insertTransactions(txs)
            if (goals.isNotEmpty()) goalDao.insertGoals(goals)
            if (cats.isNotEmpty()) categoryDao.insertCategories(cats)

            // Восстановление достижений с обратной совместимостью
            if (backup.achievements != null && backup.achievements.isNotEmpty()) {
                com.example.davidapp.AchievementManager.importUnlockedIds(context, backup.achievements)
            } else {
                com.example.utils.ExportImportHelper.extractAndImportAchievements(context, jsonStr)
            }

            val updatedBackup = BudgetBackup(
                profileName = profileName,
                transactions = txs,
                goals = goals,
                categories = cats,
                accounts = accs,
                achievements = com.example.davidapp.AchievementManager.exportUnlockedIds(context)
            )
            val updatedJson = backupAdapter.toJson(updatedBackup)
            storageFile.writeText(updatedJson)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
