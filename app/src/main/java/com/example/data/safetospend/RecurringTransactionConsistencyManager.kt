package com.example.data.safetospend

import com.example.data.db.RecurringExecutionLogEntity
import com.example.data.db.RecurringPaymentEntity
import com.example.data.db.TransactionEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.min

data class RecurringExecutionConfirmationOutcome(
    val transaction: TransactionEntity,
    val updatedPayment: RecurringPaymentEntity,
    val executionLog: RecurringExecutionLogEntity
)

data class RecurringTransactionRollbackOutcome(
    val updatedPayment: RecurringPaymentEntity?,
    val revertedLog: RecurringExecutionLogEntity?
)

object RecurringTransactionConsistencyManager {

    private val ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /**
     * Produces the atomic state transition when confirming payment/receipt of a [RecurringPaymentEntity]
     * for [scheduledEpochDay] on [executedEpochDay] with [actualAmount].
     */
    fun buildConfirmationTransition(
        payment: RecurringPaymentEntity,
        scheduledEpochDay: Long = payment.nextDueDateEpochDay,
        executedEpochDay: Long = scheduledEpochDay,
        actualAmount: Double = payment.amount,
        existingLogId: Long = 0L,
        transactionId: String = UUID.randomUUID().toString()
    ): RecurringExecutionConfirmationOutcome {
        val executedDateStr = LocalDate.ofEpochDay(executedEpochDay).format(ISO_FORMATTER)
        val transaction = TransactionEntity(
            id = transactionId,
            budgetId = payment.budgetId,
            accountId = null,
            type = if (payment.isIncome) "income" else "expense",
            date = executedDateStr,
            category = payment.categoryName,
            subcategory = payment.subcategoryName.ifBlank { payment.title },
            amount = actualAmount
        )

        val freq = runCatching { PaymentFrequency.valueOf(payment.frequency) }
            .getOrDefault(PaymentFrequency.MONTHLY)
        val nextDue = RecurringScheduleCalculator.computeNextDueDate(
            currentDueDateEpochDay = scheduledEpochDay,
            frequency = freq,
            anchorDayOfMonth = payment.anchorDayOfMonth,
            anchorDayOfWeek = payment.anchorDayOfWeek,
            customIntervalDays = payment.customIntervalDays
        )

        val updatedPayment = payment.copy(
            nextDueDateEpochDay = if (nextDue > payment.nextDueDateEpochDay) nextDue else payment.nextDueDateEpochDay
        )

        val log = RecurringExecutionLogEntity(
            id = existingLogId,
            budgetId = payment.budgetId,
            recurringId = payment.id,
            scheduledEpochDay = scheduledEpochDay,
            executedEpochDay = executedEpochDay,
            actualAmount = actualAmount,
            status = ExecutionStatus.PAID.name,
            linkedTransactionId = transaction.id
        )

        return RecurringExecutionConfirmationOutcome(
            transaction = transaction,
            updatedPayment = updatedPayment,
            executionLog = log
        )
    }

    /**
     * Produces the atomic rollback transition when a transaction [deletedTransactionId]
     * that was linked to a paid [RecurringExecutionLogEntity] is deleted.
     *
     * Reverts the log back to PENDING (clearing linkedTransactionId, executedEpochDay, actualAmount)
     * and rolls back [RecurringPaymentEntity.nextDueDateEpochDay] to min(nextDueDateEpochDay, scheduledEpochDay)
     * so the unpaid obligation immediately re-enters O_due / O_overdue.
     */
    fun buildDeleteTransactionRollbackTransition(
        deletedTransactionId: String,
        linkedLog: RecurringExecutionLogEntity?,
        payment: RecurringPaymentEntity?
    ): RecurringTransactionRollbackOutcome {
        if (linkedLog == null || linkedLog.linkedTransactionId != deletedTransactionId) {
            return RecurringTransactionRollbackOutcome(null, null)
        }
        val revertedLog = linkedLog.copy(
            executedEpochDay = null,
            actualAmount = null,
            status = ExecutionStatus.PENDING.name,
            linkedTransactionId = null
        )
        val rolledBackPayment = payment?.copy(
            nextDueDateEpochDay = min(payment.nextDueDateEpochDay, linkedLog.scheduledEpochDay)
        )
        return RecurringTransactionRollbackOutcome(
            updatedPayment = rolledBackPayment,
            revertedLog = revertedLog
        )
    }

    /**
     * Produces the atomic synchronization transition when a transaction linked to a paid
     * [RecurringExecutionLogEntity] is edited (amount or date changed).
     */
    fun buildEditTransactionSyncTransition(
        updatedTransaction: TransactionEntity,
        linkedLog: RecurringExecutionLogEntity?
    ): RecurringExecutionLogEntity? {
        if (linkedLog == null || linkedLog.linkedTransactionId != updatedTransaction.id) {
            return null
        }
        val newExecutedEpochDay = DavidFinancialIntelligenceProvider.parseTransactionEpochDay(updatedTransaction.date)
            ?: linkedLog.executedEpochDay
        return linkedLog.copy(
            actualAmount = updatedTransaction.amount,
            executedEpochDay = newExecutedEpochDay
        )
    }
}
