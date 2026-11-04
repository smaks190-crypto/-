package com.example.data.safetospend

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReleaseFinancialSafetyTest {
    private val today = LocalDate.of(2026, 11, 4).toEpochDay()
    private fun income() = RecurringPaymentDomain(
        id = 1, title = "Income", amount = 100.0, estimatedMaxAmount = 10000.0,
        amountType = PaymentAmountType.ESTIMATED_UPPER_BOUND, isIncome = true,
        categoryName = "Income", nextDueDateEpochDay = today + 1,
        frequency = PaymentFrequency.MONTHLY
    )

    @Test fun `upper estimate of income never inflates guaranteed liquidity`() {
        val result = SafeToSpendCalculator.calculate(SafeToSpendInput(
            todayEpochDay = today, horizonDaysOverride = 3, availableBalanceRub = 100.0,
            activeRecurringPayments = listOf(income())
        ))
        assertEquals(100.0, result.trajectory.last().cumulativeGuaranteedInflowsRub, 0.001)
    }

    @Test fun `rescheduled income does not fund spending on the old date`() {
        val result = SafeToSpendCalculator.calculate(SafeToSpendInput(
            todayEpochDay = today, horizonDaysOverride = 3, availableBalanceRub = 100.0,
            activeRecurringPayments = listOf(income()),
            executionLogs = listOf(RecurringExecutionLogDomain(
                recurringId = 1, scheduledEpochDay = today + 1, status = ExecutionStatus.RESCHEDULED
            ))
        ))
        assertEquals(0.0, result.trajectory.last().cumulativeGuaranteedInflowsRub, 0.001)
        assertNotEquals(today + 1, result.nextIncomeEpochDay)
    }

    @Test fun `essential spending cannot consume the protected reserve`() {
        val result = SafeToSpendCalculator.calculate(SafeToSpendInput(
            todayEpochDay = today, horizonDaysOverride = 1, availableBalanceRub = 1000.0,
            userSafetyFloorRub = 900.0, essentialDailyRateRub = 500.0
        ))
        assertEquals(100.0, result.canSpendTodayEssRub, 0.001)
        assertEquals(100.0, result.canSpendTodayTotalRub, 0.001)
        assertFalse(result.isReserveRequirementMet)
    }

    @Test fun `no daily spending is available when the balance equals the protected reserve`() {
        val result = SafeToSpendCalculator.calculate(SafeToSpendInput(
            todayEpochDay = today, horizonDaysOverride = 5, availableBalanceRub = 1000.0,
            userSafetyFloorRub = 1000.0, essentialDailyRateRub = 100.0
        ))
        assertEquals(0.0, result.canSpendTodayTotalRub, 0.001)
    }

    @Test fun `larger reserve never increases available spending`() {
        for (reserve in 0..10000 step 250) {
            val input = SafeToSpendInput(todayEpochDay = today, horizonDaysOverride = 10,
                availableBalanceRub = 10000.0, userSafetyFloorRub = reserve.toDouble(), essentialDailyRateRub = 200.0)
            val base = SafeToSpendCalculator.calculate(input)
            val stricter = SafeToSpendCalculator.calculate(input.copy(userSafetyFloorRub = reserve + 100.0))
            assertTrue(stricter.canSpendTodayTotalRub <= base.canSpendTodayTotalRub + 0.001)
            assertTrue(stricter.canSpendTodayTotalRub >= 0.0)
        }
    }
}
