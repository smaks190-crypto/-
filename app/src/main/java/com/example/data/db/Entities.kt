package com.example.data.db

import androidx.room.Entity
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
    val amount: Double
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



