package com.example.data.safetospend

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.RecurringExecutionLogEntity
import com.example.data.db.RecurringPaymentEntity
import com.example.data.db.TransactionEntity
import com.example.data.repository.BudgetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecurringPaymentEditTest {
    @Test fun editingRuleKeepsPaidHistoryAndRejectsAnotherBudget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.budgetProfileDao().insertProfile(BudgetProfileEntity(id = "b1", name = "Личный"))
            db.budgetProfileDao().insertProfile(BudgetProfileEntity(id = "b2", name = "Другой"))
            val dao = db.recurringPaymentDao()
            val rule = RecurringPaymentEntity(id = 41, budgetId = "b1", title = "Связь", amount = 500.0,
                categoryName = "Связь", nextDueDateEpochDay = 20_600)
            dao.insertRecurringPayment(rule)
            db.transactionDao().insertTransaction(TransactionEntity(id = "paid", budgetId = "b1", type = "expense",
                date = "2026-05-01", category = "Связь", subcategory = "Связь", amount = 500.0))
            val log = RecurringExecutionLogEntity(id = 51, budgetId = "b1", recurringId = 41,
                scheduledEpochDay = 20_574, executedEpochDay = 20_574, actualAmount = 500.0,
                status = "PAID", linkedTransactionId = "paid")
            dao.insertExecutionLog(log)
            val repository = BudgetRepository(context, db.budgetProfileDao(), db.transactionDao(), db.goalDao(),
                db.categoryDao(), db.aiAuditDao(), db.accountDao(), db.notificationDao(), db)

            assertTrue(repository.updateRecurringPaymentDetails(41, "b1") { it.copy(amount = 650.0) })
            assertEquals(650.0, dao.getRecurringPaymentById(41)!!.amount, 0.01)
            assertEquals(log, dao.getExecutionLogBySchedule(41, 20_574))
            assertFalse(repository.updateRecurringPaymentDetails(41, "b2") { it.copy(amount = 1.0) })
            assertEquals(650.0, dao.getRecurringPaymentById(41)!!.amount, 0.01)
            assertEquals(log, dao.getExecutionLogBySchedule(41, 20_574))
        } finally {
            db.close()
        }
    }
}
