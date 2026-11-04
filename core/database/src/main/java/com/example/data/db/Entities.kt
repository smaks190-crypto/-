package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "budget_profiles")
data class BudgetProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String = "default",
    val name: String,
    val balance: Double = 0.0,
    val type: String = "card", // "card", "cash", "vault"
    val accountNumber: String = ""
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String = "default",
    val title: String,
    val description: String,
    val icon: String = "bell",
    val color: String = "indigo500",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String = "default",
    val accountId: String? = null,
    val type: String, // "income" or "expense"
    val date: String, // "YYYY-MM-DD"
    val category: String,
    val subcategory: String,
    val amount: Double,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String = "default",
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String = "default",
    val type: String, // "income" or "expense"
    val name: String,
    val monthlyLimit: Double? = null
)

@Entity(tableName = "ai_audits")
data class AiAuditEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String = "default",
    val periodType: String, // "MONTH" or "YEAR"
    val periodKey: String, // e.g. "2026-07" or "2026"
    val year: Int,
    val month: Int, // 1..12 or 0 for year
    val auditText: String,
    val sillyExpensesSummary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recurring_payments",
    indices = [
        Index(value = ["budgetId", "status", "nextDueDateEpochDay"]),
        Index(value = ["isIncome", "status"])
    ]
)
data class RecurringPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val budgetId: String = "default",
    val title: String,
    val amount: Double,
    val estimatedMaxAmount: Double? = null,
    val amountType: String = "FIXED",
    val isIncome: Boolean = false,
    val incomeCertainty: String = "GUARANTEED",
    val categoryName: String,
    val subcategoryName: String = "",
    val frequency: String = "MONTHLY",
    val customIntervalDays: Int? = null,
    val anchorDayOfMonth: Int? = null,
    val anchorDayOfWeek: Int? = null,
    val nextDueDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val priority: String = "MANDATORY",
    val autoConfirm: Boolean = false,
    val reminderDaysBefore: Int = 2,
    val status: String = "ACTIVE",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recurring_execution_logs",
    foreignKeys = [
        ForeignKey(
            entity = RecurringPaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recurringId", "scheduledEpochDay"], unique = true),
        Index(value = ["status"]),
        Index(value = ["linkedTransactionId"])
    ]
)
data class RecurringExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val budgetId: String = "default",
    val recurringId: Long,
    val scheduledEpochDay: Long,
    val executedEpochDay: Long? = null,
    val actualAmount: Double? = null,
    val status: String = "PENDING",
    val linkedTransactionId: String? = null
)
