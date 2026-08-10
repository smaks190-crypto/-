package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `accounts` (
                `id` TEXT NOT NULL,
                `budgetId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `balance` REAL NOT NULL,
                `type` TEXT NOT NULL,
                `accountNumber` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `notifications` (
                `id` TEXT NOT NULL,
                `budgetId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isRead` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        db.execSQL("ALTER TABLE categories ADD COLUMN monthlyLimit REAL DEFAULT NULL")

        db.execSQL("ALTER TABLE transactions ADD COLUMN accountId TEXT DEFAULT NULL")

        val budgetIds = mutableListOf<String>()
        val cursor = db.query("SELECT id FROM budget_profiles")
        cursor.use { c ->
            val idIndex = c.getColumnIndex("id")
            if (idIndex != -1) {
                while (c.moveToNext()) {
                    budgetIds.add(c.getString(idIndex))
                }
            }
        }

        if (budgetIds.isEmpty()) {
            val fallbackAccountId = "default_acc_default"
            val fallbackBudgetId = "default"
            db.execSQL(
                "INSERT OR IGNORE INTO accounts (id, budgetId, name, balance, type, accountNumber) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(fallbackAccountId, fallbackBudgetId, "Основной счет", 0.0, "card", "**** 0000")
            )
            db.execSQL(
                "UPDATE transactions SET accountId = ? WHERE accountId IS NULL AND budgetId = ?",
                arrayOf(fallbackAccountId, fallbackBudgetId)
            )
        } else {
            for (budgetId in budgetIds) {
                val accountId = "default_acc_" + budgetId
                db.execSQL(
                    "INSERT OR IGNORE INTO accounts (id, budgetId, name, balance, type, accountNumber) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(accountId, budgetId, "Основной счет", 0.0, "card", "**** 0000")
                )
                db.execSQL(
                    "UPDATE transactions SET accountId = ? WHERE accountId IS NULL AND budgetId = ?",
                    arrayOf(accountId, budgetId)
                )
            }
        }

        val catchAllAccountId = "default_acc_fallback"
        val catchAllBudgetId = "default"
        db.execSQL(
            "INSERT OR IGNORE INTO accounts (id, budgetId, name, balance, type, accountNumber) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(catchAllAccountId, catchAllBudgetId, "Основной счет", 0.0, "card", "**** 0000")
        )
        db.execSQL(
            "UPDATE transactions SET accountId = ? WHERE accountId IS NULL",
            arrayOf(catchAllAccountId)
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        BudgetProfileEntity::class,
        AccountEntity::class,
        NotificationEntity::class,
        TransactionEntity::class,
        GoalEntity::class,
        CategoryEntity::class,
        AiAuditEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun budgetProfileDao(): BudgetProfileDao
    abstract fun accountDao(): AccountDao
    abstract fun notificationDao(): NotificationDao
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
    abstract fun categoryDao(): CategoryDao
    abstract fun aiAuditDao(): AiAuditDao


    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "budget_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
