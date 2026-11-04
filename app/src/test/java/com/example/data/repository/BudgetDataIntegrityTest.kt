package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AccountEntity
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.RecurringExecutionLogEntity
import com.example.data.db.RecurringPaymentEntity
import com.example.data.db.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BudgetDataIntegrityTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: BudgetRepository
    private val codec = BudgetBackupCodec()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = BudgetRepository(
            context, db.budgetProfileDao(), db.transactionDao(), db.goalDao(), db.categoryDao(),
            db.aiAuditDao(), db.accountDao(), db.notificationDao(), db
        )
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun seed(budgetId: String = "default"): TransactionEntity {
        db.budgetProfileDao().insertProfile(BudgetProfileEntity(id = budgetId, name = "Budget $budgetId"))
        db.accountDao().insertAccount(AccountEntity(id = "account_$budgetId", budgetId = budgetId, name = "Card", balance = 1000.0))
        val tx = TransactionEntity(
            id = "transaction_$budgetId", budgetId = budgetId, accountId = "account_$budgetId",
            type = "expense", date = "2026-11-04", category = "Food", subcategory = "Lunch",
            amount = 100.0, createdAt = 123456L
        )
        db.transactionDao().insertTransaction(tx)
        return tx
    }

    private fun legacyBackup(subcategory: String = "Imported"): BudgetBackup = BudgetBackup(
        profileName = "Imported budget",
        accounts = listOf(AccountEntity(id = "import_account", name = "Cash")),
        transactions = listOf(TransactionEntity(
            id = "import_tx", accountId = "import_account", type = "expense", date = "2026-11-04",
            category = "Food", subcategory = subcategory, amount = 50.0, createdAt = 777L
        ))
    )

    private fun failImportedTransaction() {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_import BEFORE INSERT ON transactions
            WHEN NEW.subcategory = 'FAIL_IMPORT'
            BEGIN SELECT RAISE(ABORT, 'Injected write failure'); END
        """.trimIndent())
    }

    @Test
    fun `editing preserves account and creation time and cannot move another budget's transaction`() = runBlocking {
        val original = seed()
        assertFalse(repository.updateTransactionDetails(original.id, "another", "expense", original.date, "Food", "Edit", 120.0))
        assertTrue(repository.updateTransactionDetails(original.id, original.budgetId, "expense", original.date, "Food", "Edit", 120.0))
        val updated = db.transactionDao().getAllTransactions().first().single()
        assertEquals(original.copy(subcategory = "Edit", amount = 120.0), updated)
    }

    @Test
    fun `empty malformed unrelated and unsupported JSON leave existing records intact`() = runBlocking {
        val original = seed()
        val invalid = listOf("{}", "null", "[]", "{", "{\"message\":\"hello\"}", "{\"transactions\":null}",
            "{\"formatVersion\":99,\"transactions\":[]}", "{\"formatVersion\":2,\"transactions\":[]}")
        for (json in invalid) {
            assertFalse(json, repository.importJson(json))
            assertEquals(listOf(original), db.transactionDao().getAllTransactions().first())
        }
    }

    @Test
    fun `write failure rolls back full import and preserves the previous cache`() = runBlocking {
        val original = seed()
        val cache = File(context.filesDir, "budget_storage.json").apply { writeText("previous backup") }
        failImportedTransaction()
        assertFalse(repository.importJson(codec.encode(legacyBackup("FAIL_IMPORT"))))
        assertEquals(listOf(original), db.transactionDao().getAllTransactions().first())
        assertNotNull(db.accountDao().getAccountById(original.accountId!!))
        assertEquals("Budget default", db.budgetProfileDao().getProfileById("default")?.name)
        assertEquals("previous backup", cache.readText())
    }

    @Test
    fun `new budget import rolls back its profile when a write fails`() = runBlocking {
        val original = seed()
        failImportedTransaction()
        assertNull(repository.importBackupAsNewBudget(codec.encode(legacyBackup("FAIL_IMPORT"))))
        assertEquals(listOf("default"), db.budgetProfileDao().getAllProfiles().first().map { it.id })
        assertEquals(listOf(original), db.transactionDao().getAllTransactions().first())
    }

    @Test
    fun `targeted import replaces only the selected budget and remaps links`() = runBlocking {
        seed()
        val other = seed("other")
        assertTrue(repository.importJson(codec.encode(legacyBackup()), "default"))
        assertEquals(listOf(other), db.transactionDao().getTransactionsByBudgetId("other").first())
        val imported = db.transactionDao().getTransactionsByBudgetId("default").first().single()
        assertNotEquals("import_tx", imported.id)
        assertEquals("default", db.accountDao().getAccountById(imported.accountId!!)?.budgetId)
        assertEquals(777L, imported.createdAt)
    }

    @Test
    fun `targeted write failure restores selected and unrelated budgets`() = runBlocking {
        val original = seed()
        val other = seed("other")
        failImportedTransaction()
        assertFalse(repository.importJson(codec.encode(legacyBackup("FAIL_IMPORT")), "default"))
        assertEquals(listOf(original), db.transactionDao().getTransactionsByBudgetId("default").first())
        assertEquals(listOf(other), db.transactionDao().getTransactionsByBudgetId("other").first())
    }

    @Test
    fun `backup round trip preserves multiple profiles recurring payments and execution links`() = runBlocking {
        val original = seed()
        seed("other")
        val paymentId = db.recurringPaymentDao().insertRecurringPayment(RecurringPaymentEntity(
            title = "Lunch", amount = 100.0, categoryName = "Food", nextDueDateEpochDay = 21000L
        ))
        db.recurringPaymentDao().insertExecutionLog(RecurringExecutionLogEntity(
            recurringId = paymentId, scheduledEpochDay = 20970L, executedEpochDay = 20970L,
            actualAmount = 100.0, status = "PAID", linkedTransactionId = original.id
        ))
        val json = repository.exportJson()
        assertEquals(2, codec.decode(json).formatVersion)
        assertTrue(repository.importJson(json))
        assertEquals(setOf("default", "other"), db.budgetProfileDao().getAllProfiles().first().map { it.id }.toSet())
        val payment = db.recurringPaymentDao().getAllRecurringPayments().first().single()
        val log = db.recurringPaymentDao().getAllExecutionLogs().first().single()
        assertEquals(payment.id, log.recurringId)
        assertEquals(original.id, log.linkedTransactionId)

        val singleBudget = repository.exportJsonForBudget("default")
        val newProfile = repository.importBackupAsNewBudget(singleBudget, "Restored")!!
        val newTx = db.transactionDao().getTransactionsByBudgetId(newProfile.id).first().single()
        val newPayment = db.recurringPaymentDao().getRecurringPaymentsByBudgetId(newProfile.id).first().single()
        val newLog = db.recurringPaymentDao().getExecutionLogsByBudgetId(newProfile.id).first().single()
        assertNotEquals(payment.id, newPayment.id)
        assertEquals(newPayment.id, newLog.recurringId)
        assertEquals(newTx.id, newLog.linkedTransactionId)
        assertEquals(newProfile.id, db.accountDao().getAccountById(newTx.accountId!!)?.budgetId)
        assertEquals(listOf(original), db.transactionDao().getTransactionsByBudgetId("default").first())
    }

    @Test
    fun `invalid account links negative amounts and duplicate IDs are rejected before clearing`() = runBlocking {
        val original = seed()
        val valid = legacyBackup()
        val tx = valid.transactions!!.single()
        for (invalid in listOf(
            valid.copy(transactions = listOf(tx.copy(accountId = "missing"))),
            valid.copy(transactions = listOf(tx.copy(amount = -1.0))),
            valid.copy(transactions = listOf(tx, tx))
        )) {
            assertFalse(repository.importJson(codec.encode(invalid)))
            assertEquals(listOf(original), db.transactionDao().getAllTransactions().first())
        }
    }

    @Test
    fun `existing empty budget is not replaced with demo data on startup`() = runBlocking {
        db.budgetProfileDao().insertProfile(BudgetProfileEntity(id = "default", name = "Казума Сато"))
        repository.ensureDefaultProfileExists()
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
    }

    @Test
    fun `demo asset still imports with valid account links`() = runBlocking {
        seed()
        repository.loadDemoData("default")
        assertEquals(287, db.transactionDao().getTransactionsByBudgetId("default").first().size)
        codec.decode(repository.exportJsonForBudget("default"))
        Unit
    }
}
