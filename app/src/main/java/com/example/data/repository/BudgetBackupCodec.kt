package com.example.data.repository

import com.example.data.db.BudgetProfileEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

/** Validates the whole backup before any database mutation; accepts legacy v1 exports. */
internal class BudgetBackupCodec {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(BudgetBackup::class.java)

    fun encode(backup: BudgetBackup): String = adapter.toJson(backup)

    fun decode(json: String): BudgetBackup {
        val fields = requireNotNull(moshi.adapter(Map::class.java).fromJson(json))
        require(fields["transactions"] is List<*>) { "Not a budget backup" }
        val backup = requireNotNull(adapter.fromJson(json))
        require(backup.formatVersion in 1..2) { "Unsupported backup version" }
        if (backup.formatVersion == 2) {
            for (key in listOf("profiles", "accounts", "goals", "categories", "recurringPayments", "executionLogs")) {
                require(fields[key] is List<*>) { "Incomplete backup: $key" }
            }
            require(!backup.profiles.isNullOrEmpty()) { "Missing budget profiles" }
        }
        val accounts = backup.accounts.orEmpty()
        val transactions = backup.transactions.orEmpty()
        val payments = backup.recurringPayments.orEmpty()
        val logs = backup.executionLogs.orEmpty()
        fun uniqueIds(ids: List<String>) {
            require(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size) { "Invalid or duplicate IDs" }
        }
        uniqueIds(accounts.map { it.id })
        uniqueIds(transactions.map { it.id })
        uniqueIds(backup.goals.orEmpty().map { it.id })
        uniqueIds(backup.categories.orEmpty().map { it.id })
        uniqueIds(backup.profiles.orEmpty().map { it.id })
        require(payments.all { it.id > 0L } && payments.map { it.id }.toSet().size == payments.size)
        require(logs.all { it.id > 0L } && logs.map { it.id }.toSet().size == logs.size)
        require(logs.map { it.recurringId to it.scheduledEpochDay }.toSet().size == logs.size)
        require(accounts.all { it.balance.isFinite() })
        require(backup.goals.orEmpty().all { it.targetAmount.isFinite() && it.currentAmount.isFinite() })
        require(backup.categories.orEmpty().all { category ->
            category.monthlyLimit.let { it == null || (it.isFinite() && it >= 0.0) }
        })
        require(transactions.all {
            it.amount.isFinite() && it.amount >= 0.0 && it.type in setOf("income", "expense") &&
                it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
        }) { "Invalid transaction" }
        require(payments.all {
            it.amount.isFinite() && it.amount >= 0.0 &&
                it.estimatedMaxAmount.let { maximum -> maximum == null || (maximum.isFinite() && maximum >= 0.0) }
        })
        val accountById = accounts.associateBy { it.id }
        val transactionById = transactions.associateBy { it.id }
        val paymentById = payments.associateBy { it.id }
        transactions.forEach { tx ->
            if (tx.accountId != null) {
                require(accountById[tx.accountId]?.budgetId == tx.budgetId) { "Invalid transaction account" }
            }
        }
        logs.forEach { log ->
            require(paymentById[log.recurringId]?.budgetId == log.budgetId) { "Invalid recurring payment link" }
            if (log.linkedTransactionId != null) {
                require(transactionById[log.linkedTransactionId]?.budgetId == log.budgetId) { "Invalid execution transaction link" }
            }
            require(log.actualAmount.let { it == null || (it.isFinite() && it >= 0.0) })
        }
        if (backup.formatVersion == 2) {
            val profileIds = backup.profiles.orEmpty().map { it.id }.toSet()
            require(budgetIds(backup).all { it in profileIds }) { "Unknown budget profile" }
        }
        return backup
    }

    private fun budgetIds(backup: BudgetBackup): Set<String> = buildSet {
        backup.profiles.orEmpty().forEach { add(it.id) }
        backup.accounts.orEmpty().forEach { add(it.budgetId) }
        backup.transactions.orEmpty().forEach { add(it.budgetId) }
        backup.goals.orEmpty().forEach { add(it.budgetId) }
        backup.categories.orEmpty().forEach { add(it.budgetId) }
        backup.recurringPayments.orEmpty().forEach { add(it.budgetId) }
        backup.executionLogs.orEmpty().forEach { add(it.budgetId) }
    }

    /** Targeted imports receive fresh IDs so they cannot overwrite another budget's records. */
    fun prepare(backup: BudgetBackup, targetBudgetId: String? = null, customName: String? = null): BudgetBackup {
        val sourceIds = budgetIds(backup).ifEmpty { setOf("default") }
        if (targetBudgetId != null) {
            require(targetBudgetId.isNotBlank())
            require(sourceIds.size == 1) { "Use a single-budget backup for a targeted import" }
        }
        fun budgetId(old: String) = targetBudgetId ?: old.ifBlank { "default" }
        fun newId() = UUID.randomUUID().toString()
        val accountIds = backup.accounts.orEmpty().associate { it.id to if (targetBudgetId == null) it.id else newId() }
        val transactionIds = backup.transactions.orEmpty().associate { it.id to if (targetBudgetId == null) it.id else newId() }
        val profiles = if (targetBudgetId != null) {
            listOf(BudgetProfileEntity(
                id = targetBudgetId,
                name = customName ?: backup.profiles?.firstOrNull()?.name ?: backup.profileName ?: "Основной"
            ))
        } else backup.profiles?.takeIf { it.isNotEmpty() } ?: sourceIds.map {
            BudgetProfileEntity(id = budgetId(it), name = backup.profileName ?: "Основной")
        }.distinctBy { it.id }
        return backup.copy(
            formatVersion = 2,
            profiles = profiles,
            profileName = profiles.firstOrNull()?.name,
            accounts = backup.accounts.orEmpty().map { it.copy(id = accountIds.getValue(it.id), budgetId = budgetId(it.budgetId)) },
            transactions = backup.transactions.orEmpty().map {
                it.copy(id = transactionIds.getValue(it.id), budgetId = budgetId(it.budgetId), accountId = it.accountId?.let(accountIds::getValue))
            },
            goals = backup.goals.orEmpty().map { it.copy(id = if (targetBudgetId == null) it.id else newId(), budgetId = budgetId(it.budgetId)) },
            categories = backup.categories.orEmpty().map { it.copy(id = if (targetBudgetId == null) it.id else newId(), budgetId = budgetId(it.budgetId)) },
            recurringPayments = backup.recurringPayments.orEmpty().map { it.copy(budgetId = budgetId(it.budgetId)) },
            executionLogs = backup.executionLogs.orEmpty().map {
                it.copy(budgetId = budgetId(it.budgetId), linkedTransactionId = it.linkedTransactionId?.let(transactionIds::getValue))
            }
        ).also { decode(encode(it)) }
    }
}
