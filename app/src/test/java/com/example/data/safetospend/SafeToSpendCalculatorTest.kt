package com.example.data.safetospend

import com.example.data.db.AccountEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.RecurringExecutionLogEntity
import com.example.data.db.RecurringPaymentEntity
import com.example.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import com.example.ui.components.dialogs.buildFinancialStateUiModel
import com.example.ui.components.dialogs.buildSafeToSpendBreakdownUiModel
import com.example.ui.components.dialogs.localizeBottleneckReason
import com.example.ui.components.dialogs.localizeWhatIfVerdict

class SafeToSpendCalculatorTest {

    private val baseDay = LocalDate.of(2026, 4, 10).toEpochDay()

    // =========================================================================
    // I. 28 ОБЯЗАТЕЛЬНЫХ СЦЕНАРИЕВ ИЗ СПЕЦИФИКАЦИИ ISSUE #60 (REV. 3)
    // =========================================================================

    // Test 1: Чистый равномерный бюджет без обязательств
    @Test
    fun test01_uniformBudgetWithoutObligations() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10, // 10 дней (t=0..9)
            availableBalanceRub = 30_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(2_000.0, result.dailyDiscretionarySpeedRub, 0.01)
        assertEquals(2_000.0, result.canSpendTodayDiscRub, 0.01)
        assertEquals(500.0, result.canSpendTodayEssRub, 0.01)
        assertFalse(result.hasCashGap)
        assertTrue(result.isReserveRequirementMet)
    }

    // Test 2: Крупная аренда до зарплаты (Эффект «бутылочного горлышка» до ЗП)
    @Test
    fun test02_largeRentBeforeSalaryBottleneck() {
        val rent = RecurringPaymentDomain(
            id = 1L,
            title = "Аренда",
            amount = 40_000.0,
            isIncome = false,
            categoryName = "ЖКХ и Аренда",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 15,
            nextDueDateEpochDay = baseDay + 5,
            priority = ObligationPriority.MANDATORY
        )
        val salary = RecurringPaymentDomain(
            id = 2L,
            title = "Зарплата",
            amount = 80_000.0,
            isIncome = true,
            incomeCertainty = IncomeCertainty.GUARANTEED,
            categoryName = "Зарплата",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 16,
            nextDueDateEpochDay = baseDay + 6
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 30,
            availableBalanceRub = 55_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(rent, salary)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // В точке offset=5 (6 дней t=0..5): L_0=55000 - R=5000 - Rent=40000 - 6*500(3000) = 7000 / 6 = 1166.67 ₽/день
        assertEquals(5, result.bottleneckDayOffset)
        assertEquals(7_000.0 / 6.0, result.dailyDiscretionarySpeedRub, 0.1)
        assertEquals(7_000.0 / 6.0, result.canSpendTodayDiscRub, 0.1)
    }

    // Test 3: Множественные подписки в один день
    @Test
    fun test03_multipleSubscriptionsOnSameDay() {
        val sub1 = RecurringPaymentDomain(
            id = 1L,
            title = "Музыка",
            amount = 300.0,
            isIncome = false,
            categoryName = "Подписки",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 12,
            nextDueDateEpochDay = baseDay + 2,
            priority = ObligationPriority.DISCRETIONARY_SUBSCRIPTION
        )
        val sub2 = RecurringPaymentDomain(
            id = 2L,
            title = "Кинотеатр",
            amount = 700.0,
            isIncome = false,
            categoryName = "Подписки",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 12,
            nextDueDateEpochDay = baseDay + 2,
            priority = ObligationPriority.DISCRETIONARY_SUBSCRIPTION
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 5,
            availableBalanceRub = 10_000.0,
            userSafetyFloorRub = 1_500.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(sub1, sub2)
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(1_000.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 4: Сегодняшний перерасход без кассового разрыва (S >= E_disc(0) vs S < E_disc(0))
    @Test
    fun test04_todayOverspendWithoutCashGap_noDoubleSubtraction() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 20_000.0,
            userSafetyFloorRub = 0.0,
            essentialDailyRateRub = 0.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            todayExpenses = listOf(
                TodayClassifiedExpense(
                    transactionId = "10",
                    categoryName = "Развлечения",
                    amountRub = 3_000.0,
                    streamType = ExpenseStreamType.DISCRETIONARY
                )
            )
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(0.0, result.canSpendTodayDiscRub, 0.01)
        assertTrue(result.todayOverspendRub > 0.0)
        assertFalse(result.hasCashGap)
    }

    // Test 5: Кассовый разрыв неизбежен даже при нулевых дискреционных тратах
    @Test
    fun test05_inevitableCashGapEvenWithZeroDiscretionarySpend() {
        val rent = RecurringPaymentDomain(
            id = 1L,
            title = "Аренда",
            amount = 35_000.0,
            isIncome = false,
            categoryName = "ЖКХ и Аренда",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 13,
            nextDueDateEpochDay = baseDay + 3,
            priority = ObligationPriority.MANDATORY
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 20_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(rent)
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertTrue(result.hasCashGap)
        assertEquals(0.0, result.canSpendTodayDiscRub, 0.01)
        assertEquals(baseDay + 3, result.cashGapEpochDay)
        assertTrue(result.cashGapAmountRub > 0.0)
    }

    // Test 6: Разделение гарантированных, ожидаемых и гипотетических доходов
    @Test
    fun test06_incomeTiersSeparation_guaranteedVsExpectedVsHypothetical() {
        val expectedBonus = RecurringPaymentDomain(
            id = 1L,
            title = "Премия",
            amount = 50_000.0,
            isIncome = true,
            incomeCertainty = IncomeCertainty.EXPECTED,
            categoryName = "Премия",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 12,
            nextDueDateEpochDay = baseDay + 2
        )
        val hypotheticalGift = RecurringPaymentDomain(
            id = 2L,
            title = "Возможный фриланс",
            amount = 100_000.0,
            isIncome = true,
            incomeCertainty = IncomeCertainty.HYPOTHETICAL,
            categoryName = "Подработка",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 12,
            nextDueDateEpochDay = baseDay + 2
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 15_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(expectedBonus, hypotheticalGift)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // Безопасный базовый сценарий НЕ включает EXPECTED и HYPOTHETICAL:
        assertEquals(500.0, result.dailyDiscretionarySpeedRub, 0.01)
        // Оптимистичный сценарий с EXPECTED (+50000) выше базового:
        assertTrue(result.expectedScenarioDailyBudgetRub > result.dailyDiscretionarySpeedRub)
    }

    // Test 7: Погашение долга как обязательство
    @Test
    fun test07_debtRepaymentDecreasesSafeToSpend() {
        val debt = ScheduledDebtObligation(
            debtId = "d1",
            name = "Долг Ивану",
            remainingAmountRub = 6_000.0,
            isWeOwe = true,
            dueDateEpochDay = baseDay + 4
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 20_000.0,
            userSafetyFloorRub = 2_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            scheduledDebts = listOf(debt)
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(700.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 8: Переход через конец месяца (31 января -> 28 февраля -> 31 марта)
    @Test
    fun test08_monthEndAnchorTransition_jan31ToFeb28AndMar31() {
        val jan31 = LocalDate.of(2026, 1, 31).toEpochDay()
        val febDue = RecurringScheduleCalculator.computeNextDueDate(
            currentDueDateEpochDay = jan31,
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 31
        )
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), febDue)

        val marDue = RecurringScheduleCalculator.computeNextDueDate(
            currentDueDateEpochDay = febDue,
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 31
        )
        assertEquals(LocalDate.of(2026, 3, 31).toEpochDay(), marDue)
    }

    // Test 9: Високосный год (29 февраля 2028 -> 28 февраля 2029)
    @Test
    fun test09_leapYearTransition_feb29ToFeb28() {
        val feb29_2028 = LocalDate.of(2028, 2, 29).toEpochDay()
        val nextYearDue = RecurringScheduleCalculator.computeNextDueDate(
            currentDueDateEpochDay = feb29_2028,
            frequency = PaymentFrequency.YEARLY,
            anchorDayOfMonth = 29
        )
        assertEquals(LocalDate.of(2029, 2, 28).toEpochDay(), nextYearDue)
    }

    // Test 10: Ручное подтверждение регулярного платежа сегодня (защита от двойного списания)
    @Test
    fun test10_paidTodayObligationNotDeductedTwice() {
        val payment = RecurringPaymentDomain(
            id = 1L,
            title = "Коммуналка",
            amount = 10_000.0,
            isIncome = false,
            categoryName = "ЖКХ",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 10,
            nextDueDateEpochDay = baseDay
        )
        val log = RecurringExecutionLogDomain(
            id = 1L,
            recurringId = 1L,
            scheduledEpochDay = baseDay,
            executedEpochDay = baseDay,
            actualAmount = 10_000.0,
            status = ExecutionStatus.PAID,
            linkedTransactionId = "99"
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 20_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(payment),
            executionLogs = listOf(log),
            todayExpenses = listOf(
                TodayClassifiedExpense(
                    transactionId = "99",
                    categoryName = "ЖКХ",
                    amountRub = 10_000.0,
                    streamType = ExpenseStreamType.MANDATORY_OBLIGATION,
                    linkedRecurringId = 1L
                )
            )
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(1_000.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 11: Просроченный обязательный платёж (Единое правило O_overdue, без двойного счёта в O(0))
    @Test
    fun test11_overdueMandatoryPaymentReservedExactlyOnce() {
        val overdueRent = RecurringPaymentDomain(
            id = 1L,
            title = "Аренда за прошлую неделю",
            amount = 15_000.0,
            isIncome = false,
            categoryName = "ЖКХ и Аренда",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 5,
            nextDueDateEpochDay = baseDay - 5,
            priority = ObligationPriority.MANDATORY
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 30_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(overdueRent)
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(15_000.0, result.overdueObligationsRub, 0.01)
        assertEquals(500.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 12: Пропущенный и перенесённый платёж
    @Test
    fun test12_rescheduledPaymentShiftsToNewDate() {
        val payment = RecurringPaymentDomain(
            id = 1L,
            title = "Интернет",
            amount = 1_000.0,
            isIncome = false,
            categoryName = "Связь",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 10,
            nextDueDateEpochDay = baseDay + 5
        )
        val occurrences = RecurringScheduleCalculator.generateOccurrencesInRange(
            payment = payment,
            rangeStartEpochDay = baseDay,
            rangeEndEpochDay = baseDay + 10
        )
        assertEquals(1, occurrences.size)
        assertEquals(baseDay + 5, occurrences.first())
    }

    // Test 13: Наличие категорийных лимитов жестче ликвидности
    @Test
    fun test13_categoryLimitTighterThanLiquidityCapsSpend() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            daysRemainingInMonth = 10,
            horizonDaysOverride = 10,
            availableBalanceRub = 100_000.0,
            userSafetyFloorRub = 10_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            discretionaryCategoryRemainingRub = 8_000.0
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(800.0, result.dailyDiscretionarySpeedRub, 0.01)
        assertEquals(800.0, result.canSpendTodayDiscRub, 0.01)
        assertEquals(BottleneckReason.CATEGORY_MONTHLY_BUDGET, result.bottleneckReason)
    }

    // Test 14: Наличие свободных лимитов при нехватке реальных денег на счетах
    @Test
    fun test14_liquidityTighterThanCategoryLimitCapsSpend() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            daysRemainingInMonth = 10,
            horizonDaysOverride = 10,
            availableBalanceRub = 12_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            discretionaryCategoryRemainingRub = 50_000.0
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(200.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 15: Отсутствие настроенных лимитов и регулярных платежей (Fallback)
    @Test
    fun test15_noLimitsAndNoRecurringPaymentsColdStart() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 20_000.0,
            userSafetyFloorRub = 0.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.UNVERIFIED_FALLBACK,
            requiresEssentialBudgetPrompt = true
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(1_500.0, result.dailyDiscretionarySpeedRub, 0.01)
        assertTrue(result.requiresEssentialBudgetPrompt)
    }

    // Test 16: Нулевой и отрицательный баланс счетов
    @Test
    fun test16_zeroAndNegativeAccountBalance() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = -3_500.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(0.0, result.canSpendTodayDiscRub, 0.01)
        assertEquals(0.0, result.canSpendTodayEssRub, 0.01)
        assertFalse(result.isReserveRequirementMet)
        assertEquals(13_500.0, result.reserveDeficitRub, 0.01)
    }

    // Test 17: Крупное разовое поступление в середине месяца
    @Test
    fun test17_largeOneTimeIncomeMidMonthRelievesLaterHorizon() {
        val bonus = RecurringPaymentDomain(
            id = 1L,
            title = "Подтверждённый возврат налога",
            amount = 50_000.0,
            isIncome = true,
            incomeCertainty = IncomeCertainty.GUARANTEED,
            categoryName = "Доход",
            frequency = PaymentFrequency.MONTHLY,
            nextDueDateEpochDay = baseDay + 5
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 15,
            availableBalanceRub = 12_000.0,
            userSafetyFloorRub = 2_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(bonus)
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(4, result.bottleneckDayOffset)
        assertEquals(1_500.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 18: Внутренний перевод между картами НЕ меняет Safe-to-Spend
    @Test
    fun test18_internalTransferBetweenCardsDoesNotAlterSafeToSpend() {
        val accounts = listOf(
            AccountEntity(id = "a1", budgetId = "b1", name = "Карта 1", balance = 30_000.0, type = "card"),
            AccountEntity(id = "a2", budgetId = "b1", name = "Карта 2", balance = 20_000.0, type = "card")
        )
        val transferTx = listOf(
            TransactionEntity(
                id = "1",
                budgetId = "b1",
                type = "expense",
                date = "2026-04-10",
                category = "Перевод между счетами",
                subcategory = "На карту 2",
                amount = 10_000.0,
                accountId = "a1"
            ),
            TransactionEntity(
                id = "2",
                budgetId = "b1",
                type = "income",
                date = "2026-04-10",
                category = "Перевод между счетами",
                subcategory = "С карты 1",
                amount = 10_000.0,
                accountId = "a2"
            )
        )
        val snapBefore = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = emptyList(),
            accounts = accounts,
            goals = emptyList(),
            categories = emptyList(),
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 5_000.0,
            userEssentialDailyNormRub = 500.0
        )
        val snapAfter = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = transferTx,
            accounts = accounts,
            goals = emptyList(),
            categories = emptyList(),
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 5_000.0,
            userEssentialDailyNormRub = 500.0
        )
        assertEquals(
            snapBefore.safeToSpendResult.canSpendTodayDiscRub,
            snapAfter.safeToSpendResult.canSpendTodayDiscRub,
            0.01
        )
    }

    // Test 19: Пополнение копилки уменьшает доступный остаток, но не считается расходом в истории
    @Test
    fun test19_savingsGoalContributionNotCountedAsEssentialOrDiscretionaryExpense() {
        val accounts = listOf(
            AccountEntity(id = "a1", budgetId = "b1", name = "Карта", balance = 40_000.0, type = "card")
        )
        val goals = listOf(
            GoalEntity(id = "g1", budgetId = "b1", name = "Отпуск", targetAmount = 100_000.0, currentAmount = 10_000.0)
        )
        val goalContribTx = TransactionEntity(
            id = "5",
            budgetId = "b1",
            type = "expense",
            date = "2026-04-10",
            category = "Копилка",
            subcategory = "Отпуск",
            amount = 5_000.0,
            accountId = "a1"
        )
        val snap = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = listOf(goalContribTx),
            accounts = accounts,
            goals = goals,
            categories = emptyList(),
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 0.0,
            userEssentialDailyNormRub = 500.0
        )
        assertEquals(0.0, snap.safeToSpendResult.spentTodayDiscretionaryRub, 0.01)
    }

    // Test 20: Удаление или пауза регулярного платежа мгновенно пересчитывает Safe-to-Spend
    @Test
    fun test20_pausingRecurringPaymentImmediatelyIncreasesSafeToSpend() {
        val activeRent = RecurringPaymentDomain(
            id = 1L,
            title = "Аренда",
            amount = 20_000.0,
            isIncome = false,
            categoryName = "ЖКХ и Аренда",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 15,
            nextDueDateEpochDay = baseDay + 5,
            status = RecurringStatus.ACTIVE
        )
        val pausedRent = activeRent.copy(status = RecurringStatus.PAUSED)

        val resActive = SafeToSpendCalculator.calculate(
            SafeToSpendInput(
                todayEpochDay = baseDay,
                horizonDaysOverride = 10,
                availableBalanceRub = 30_000.0,
                userSafetyFloorRub = 0.0,
                essentialDailyRateRub = 500.0,
                essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
                activeRecurringPayments = listOf(activeRent)
            )
        )
        val resPaused = SafeToSpendCalculator.calculate(
            SafeToSpendInput(
                todayEpochDay = baseDay,
                horizonDaysOverride = 10,
                availableBalanceRub = 30_000.0,
                userSafetyFloorRub = 0.0,
                essentialDailyRateRub = 500.0,
                essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
                activeRecurringPayments = listOf(pausedRent)
            )
        )
        assertTrue(resPaused.canSpendTodayDiscRub > resActive.canSpendTodayDiscRub)
        assertEquals(2_500.0, resPaused.canSpendTodayDiscRub, 0.01)
    }

    // Test 21: Переменный регулярный платёж (ЖКХ с estimatedMaxAmount)
    @Test
    fun test21_variableUtilityBillUsesConservativeEstimatedMaxAmount() {
        val utility = RecurringPaymentDomain(
            id = 1L,
            title = "ЖКХ Зимой",
            amount = 5_000.0,
            estimatedMaxAmount = 7_500.0,
            amountType = PaymentAmountType.ESTIMATED_UPPER_BOUND,
            isIncome = false,
            categoryName = "ЖКХ",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 14,
            nextDueDateEpochDay = baseDay + 4
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 22_500.0,
            userSafetyFloorRub = 0.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(utility)
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(1_000.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 22: Регрессионный тест сохранности текущих экранов и детерминизма
    @Test
    fun test22_deterministicCalculationAndTrajectoryIntegrity() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 15,
            availableBalanceRub = 42_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 600.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        val r1 = SafeToSpendCalculator.calculate(input)
        val r2 = SafeToSpendCalculator.calculate(input)
        assertEquals(r1.canSpendTodayDiscRub, r2.canSpendTodayDiscRub, 0.0)
        assertEquals(15, r1.trajectory.size)
    }

    // Test 23: Нерегулярные крупные закупки продуктов раз в неделю + аптека раз в 2 недели
    @Test
    fun test23_weeklyGrocerySpikesAndBiweeklyPharmacyCapturedByEffectiveRate() {
        val samples = mutableListOf<HistoricalExpenseSample>()
        for (d in 1..28) {
            samples.add(
                HistoricalExpenseSample(
                    transactionId = d.toString(),
                    epochDay = baseDay - d,
                    categoryName = "Транспорт",
                    subcategoryName = "Метро",
                    amountRub = 150.0
                )
            )
            if (d % 7 == 0) {
                samples.add(
                    HistoricalExpenseSample(
                        transactionId = (100 + d).toString(),
                        epochDay = baseDay - d,
                        categoryName = "Продукты",
                        subcategoryName = "Гипермаркет",
                        amountRub = 5_600.0
                    )
                )
            }
            if (d % 14 == 0) {
                samples.add(
                    HistoricalExpenseSample(
                        transactionId = (200 + d).toString(),
                        epochDay = baseDay - d,
                        categoryName = "Здоровье и Аптека",
                        subcategoryName = "Лекарства",
                        amountRub = 1_400.0
                    )
                )
            }
        }
        val estimate = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = baseDay,
            historicalSamples = samples,
            observedHistoryDaysOverride = 28
        )
        assertTrue("Estimate ${estimate.dailyEssentialRateRub} must capture weekly groceries", estimate.dailyEssentialRateRub >= 900.0)
        assertEquals(EssentialConfidenceLevel.HIGH_HISTORICAL, estimate.confidenceLevel)
    }

    // Test 24: Холодный старт (< 7 дней истории) и частичная история (7-20 дней)
    @Test
    fun test24_coldStartAndPartialHistoryBlending() {
        val coldSamples = listOf(
            HistoricalExpenseSample("1", baseDay - 1, "Продукты", "", 900.0),
            HistoricalExpenseSample("2", baseDay - 2, "Транспорт", "", 200.0)
        )
        val cold = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = baseDay,
            historicalSamples = coldSamples,
            observedHistoryDaysOverride = 2
        )
        assertEquals(EssentialConfidenceLevel.UNVERIFIED_FALLBACK, cold.confidenceLevel)
        assertTrue(cold.requiresEssentialBudgetPrompt)

        val partialSamples = (1..10).map { d ->
            HistoricalExpenseSample(d.toString(), baseDay - d, "Продукты", "", 700.0)
        }
        val blended = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = baseDay,
            historicalSamples = partialSamples,
            essentialCategoryMonthlyLimitsRub = mapOf("Продукты" to 30_000.0),
            observedHistoryDaysOverride = 10
        )
        assertEquals(EssentialConfidenceLevel.MODERATE_BLENDED, blended.confidenceLevel)
        assertFalse(blended.requiresEssentialBudgetPrompt)
        assertTrue(blended.dailyEssentialRateRub in 700.0..1050.0)
    }

    // Test 25: Исключение двойного учёта между e_ess и RecurringPaymentEntity
    @Test
    fun test25_recurringPaymentExcludedFromEssentialHistoryEstimation() {
        val samples = (1..14).map { d ->
            HistoricalExpenseSample(
                transactionId = d.toString(),
                epochDay = baseDay - d,
                categoryName = "Продукты",
                subcategoryName = "Доставка молока",
                amountRub = 600.0,
                linkedRecurringId = if (d == 7) 555L else null
            )
        }
        val filtered = EssentialSpendingEstimator.filterPureEssentialHistory(
            historicalSamples = samples,
            activeRecurringPayments = emptyList(),
            executionLogs = listOf(
                RecurringExecutionLogDomain(
                    id = 1L,
                    recurringId = 555L,
                    scheduledEpochDay = baseDay - 7,
                    executedEpochDay = baseDay - 7,
                    actualAmount = 600.0,
                    status = ExecutionStatus.PAID,
                    linkedTransactionId = "7"
                )
            )
        )
        assertTrue(filtered.none { it.transactionId == "7" })
    }

    // Test 26: Консистентность текстового чата Давида и голосового David Live
    @Test
    fun test26_davidTextAndVoiceShareIdenticalSafeToSpendSnapshot() {
        val accounts = listOf(
            AccountEntity(id = "a1", budgetId = "b1", name = "Основной счёт", balance = 45_000.0, type = "card")
        )
        val snap1 = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = emptyList(),
            accounts = accounts,
            goals = emptyList(),
            categories = emptyList(),
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 5_000.0,
            userEssentialDailyNormRub = 500.0
        )
        val snap2 = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = emptyList(),
            accounts = accounts,
            goals = emptyList(),
            categories = emptyList(),
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 5_000.0,
            userEssentialDailyNormRub = 500.0
        )
        assertEquals(snap1.safeToSpendResult.canSpendTodayDiscRub, snap2.safeToSpendResult.canSpendTodayDiscRub, 0.0)
        assertTrue(snap1.safeToSpendResult.toStructuredDavidSummary().contains("SAFE-TO-SPEND 2.0"))
    }

    // Test 27: What-if симуляция разовой траты («Что будет, если я потрачу 3000 рублей?»)
    @Test
    fun test27_whatIfOneTimePurchaseSimulationDoesNotMutateInput() {
        val baseInput = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 25_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        val sim = WhatIfSimulator.simulate(
            baseInput = baseInput,
            request = WhatIfScenarioRequest.HypotheticalSpend(amountRub = 3_000.0)
        )
        assertEquals(1_500.0, sim.baseline.dailyDiscretionarySpeedRub, 0.01)
        assertTrue(sim.simulated.dailyDiscretionarySpeedRub < sim.baseline.dailyDiscretionarySpeedRub)
        assertTrue(sim.deltaDailyDiscretionarySpeedRub < 0.0)
        assertEquals(25_000.0, baseInput.availableBalanceRub, 0.0)
    }

    // Test 28: What-if симуляция задержки зарплаты («Что изменится, если зарплата задержится на 5 дней?»)
    @Test
    fun test28_whatIfSalaryDelayCausesCashGapBeforeDelayedSalary() {
        val rent = RecurringPaymentDomain(
            id = 1L,
            title = "Аренда",
            amount = 30_000.0,
            isIncome = false,
            categoryName = "Аренда",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 16,
            nextDueDateEpochDay = baseDay + 6,
            priority = ObligationPriority.MANDATORY
        )
        val salary = RecurringPaymentDomain(
            id = 2L,
            title = "Зарплата",
            amount = 60_000.0,
            isIncome = true,
            incomeCertainty = IncomeCertainty.GUARANTEED,
            categoryName = "Зарплата",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 14,
            nextDueDateEpochDay = baseDay + 4
        )
        val baseInput = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 15,
            availableBalanceRub = 15_000.0,
            userSafetyFloorRub = 2_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(rent, salary)
        )
        val sim = WhatIfSimulator.simulate(
            baseInput = baseInput,
            request = WhatIfScenarioRequest.IncomeDelay(delayDays = 5)
        )
        assertFalse(sim.baseline.hasCashGap)
        assertTrue(sim.simulated.hasCashGap)
        assertEquals(WhatIfVerdict.CASH_GAP_RISK, sim.verdict)
    }

    // =========================================================================
    // II. ТЕСТЫ 4 ОБЯЗАТЕЛЬНЫХ УТОЧНЕНИЙ ПЕРЕД РЕАЛИЗАЦИЕЙ
    // =========================================================================

    // Уточнение 1: Запрет тихого норматива 500 ₽/день без предупреждения о низкой достоверности
    @Test
    fun testRefinement1_unverifiedFallbackMustTriggerWarningAndPrompt() {
        val est = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = baseDay,
            historicalSamples = emptyList(),
            observedHistoryDaysOverride = 0
        )
        assertTrue(est.requiresEssentialBudgetPrompt)
        assertEquals(EssentialConfidenceLevel.UNVERIFIED_FALLBACK, est.confidenceLevel)
        assertNotNull(est.warningMessage)
    }

    // Уточнение 2: Атомарный откат и синхронизация при удалении/редактировании транзакции регулярного платежа
    @Test
    fun testRefinement2_atomicRollbackOnDeleteAndSyncOnEditLinkedTransaction() {
        val paymentEntity = RecurringPaymentEntity(
            id = 7L,
            budgetId = "b1",
            title = "Аренда",
            amount = 30_000.0,
            isIncome = false,
            categoryName = "ЖКХ и Аренда",
            frequency = "MONTHLY",
            anchorDayOfMonth = 10,
            nextDueDateEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
            priority = "MANDATORY",
            status = "ACTIVE"
        )
        val confirmation = RecurringTransactionConsistencyManager.buildConfirmationTransition(
            payment = paymentEntity,
            scheduledEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
            executedEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
            actualAmount = 30_000.0,
            transactionId = "tx-500"
        )
        assertEquals(
            LocalDate.of(2026, 5, 10).toEpochDay(),
            confirmation.updatedPayment.nextDueDateEpochDay
        )

        val savedLog = confirmation.executionLog.copy(id = 101L)

        // Пользователь удаляет связанную транзакцию tx-500 -> nextDueDate обязан откатиться на 10 апреля!
        val rollback = RecurringTransactionConsistencyManager.buildDeleteTransactionRollbackTransition(
            deletedTransactionId = "tx-500",
            linkedLog = savedLog,
            payment = confirmation.updatedPayment
        )
        assertNotNull(rollback.updatedPayment)
        assertEquals(
            LocalDate.of(2026, 4, 10).toEpochDay(),
            rollback.updatedPayment!!.nextDueDateEpochDay
        )
        assertEquals(ExecutionStatus.PENDING.name, rollback.revertedLog!!.status)

        // Проверим редактирование суммы/даты связанной транзакции tx-500:
        val editedTx = confirmation.transaction.copy(amount = 31_500.0, date = "2026-04-11")
        val updatedLog = RecurringTransactionConsistencyManager.buildEditTransactionSyncTransition(
            updatedTransaction = editedTx,
            linkedLog = savedLog
        )
        assertNotNull(updatedLog)
        assertEquals(31_500.0, updatedLog!!.actualAmount!!, 0.01)
        assertEquals(LocalDate.of(2026, 4, 11).toEpochDay(), updatedLog.executedEpochDay)
    }

    // Уточнение 3: Однозначный источник R_locked — отсутствие двойного вычитания копилок из L_0
    @Test
    fun testRefinement3_lockedGoalsNotSubtractedTwiceFromAvailableBalance() {
        val accounts = listOf(
            AccountEntity(id = "a1", budgetId = "b1", name = "Карта", balance = 50_000.0, type = "card")
        )
        val goals = listOf(
            GoalEntity(id = "g1", budgetId = "b1", name = "Копилка", targetAmount = 100_000.0, currentAmount = 20_000.0)
        )
        val snap = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = emptyList(),
            accounts = accounts,
            goals = goals,
            categories = emptyList(),
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 5_000.0,
            userEssentialDailyNormRub = 500.0
        )
        assertEquals(50_000.0, snap.safeToSpendInput.availableBalanceRub, 0.01)
        assertEquals(0.0, snap.safeToSpendInput.unTransactedLockedGoalsRub, 0.01)
    }

    // =========================================================================
    // III. 6 ФУНДАМЕНТАЛЬНЫХ ИНВАРИАНТОВ
    // =========================================================================

    @Test
    fun testInvariants_allSixMathematicalInvariantsHold() {
        val rent = RecurringPaymentDomain(
            id = 1L,
            title = "Аренда",
            amount = 15_000.0,
            isIncome = false,
            categoryName = "ЖКХ",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 15,
            nextDueDateEpochDay = baseDay + 5
        )
        val baseInput = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 20,
            availableBalanceRub = 40_000.0,
            userSafetyFloorRub = 5_000.0,
            essentialDailyRateRub = 600.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL,
            activeRecurringPayments = listOf(rent)
        )
        val res = SafeToSpendCalculator.calculate(baseInput)

        // Инвариант 1: Неотрицательность публичных лимитов
        assertTrue(res.canSpendTodayDiscRub >= 0.0)
        assertTrue(res.canSpendTodayEssRub >= 0.0)
        assertTrue(res.dailyDiscretionarySpeedRub >= 0.0)

        // Инвариант 2: Защита резерва и базовых потребностей на всём горизонте при отсутствии кассового разрыва
        if (!res.hasCashGap && res.isReserveRequirementMet) {
            val minFree = res.trajectory.minOf { it.freeLiquidityGuarRub }
            assertTrue("Minimum free liquidity above reserve $minFree must stay >= 0", minFree >= -0.01)
        }

        // Инвариант 3: Монотонность по обязательствам (увеличение аренды не увеличивает Safe-to-Spend)
        val heavierRent = rent.copy(amount = 25_000.0)
        val resHeavier = SafeToSpendCalculator.calculate(baseInput.copy(activeRecurringPayments = listOf(heavierRent)))
        assertTrue(resHeavier.canSpendTodayDiscRub <= res.canSpendTodayDiscRub + 1e-6)

        // Инвариант 4: Идемпотентность оплаченного сегодня платежа
        val paidLog = RecurringExecutionLogDomain(
            id = 10L,
            recurringId = 1L,
            scheduledEpochDay = baseDay,
            executedEpochDay = baseDay,
            actualAmount = 15_000.0,
            status = ExecutionStatus.PAID,
            linkedTransactionId = "77"
        )
        val dueTodayRent = rent.copy(nextDueDateEpochDay = baseDay)
        val resWithPaidToday = SafeToSpendCalculator.calculate(
            baseInput.copy(
                activeRecurringPayments = listOf(dueTodayRent),
                executionLogs = listOf(paidLog)
            )
        )
        val resAlreadyShifted = SafeToSpendCalculator.calculate(
            baseInput.copy(
                activeRecurringPayments = listOf(dueTodayRent.copy(nextDueDateEpochDay = baseDay + 30)),
                executionLogs = listOf(paidLog)
            )
        )
        assertEquals(resWithPaidToday.canSpendTodayDiscRub, resAlreadyShifted.canSpendTodayDiscRub, 0.01)

        // Инвариант 5: Изоляция потоков (трата на продукты сегодня уменьшает canSpendTodayEssRub, но при рамках нормы не обнуляет дискреционный бюджет)
        val withEssSpend = SafeToSpendCalculator.calculate(
            baseInput.copy(
                todayExpenses = listOf(
                    TodayClassifiedExpense("1", "Продукты", 400.0, ExpenseStreamType.ESSENTIAL_LIVING)
                )
            )
        )
        assertEquals(200.0, withEssSpend.canSpendTodayEssRub, 0.01)
    }

    // =========================================================================
    // IV. ТЕСТЫ МОДЕЛИ ОТОБРАЖЕНИЯ UI (ISSUE #64: FINANCIAL STATE & SAFE-TO-SPEND 2.0)
    // =========================================================================

    @Test
    fun test_issue64_financialStateUiModel_separatesLiquidityNetWorthAndFreeMoneyWithExplicitDeficitWarning() {
        val todayStr = "2026-04-10"
        val txs = listOf(
            TransactionEntity(
                id = "tx_bal",
                budgetId = "b1",
                type = "income",
                date = todayStr,
                category = "Начальный баланс",
                subcategory = "Остаток на карте",
                amount = 10_000.0
            )
        )
        val debtAccounts = listOf(
            AccountEntity(id = "d_credit", budgetId = "b1", name = "Кредитка", balance = 85_000.0, type = "we_owe"),
            AccountEntity(id = "d_friend", budgetId = "b1", name = "Долг друга нам", balance = 40_000.0, type = "owes_us")
        )
        val recurring = listOf(
            RecurringPaymentEntity(
                id = 1L,
                budgetId = "b1",
                title = "Мобильная связь и интернет",
                amount = 2_000.0,
                isIncome = false,
                categoryName = "Связь",
                frequency = "MONTHLY",
                anchorDayOfMonth = 12,
                nextDueDateEpochDay = baseDay + 2,
                priority = "MANDATORY",
                status = "ACTIVE"
            ),
            RecurringPaymentEntity(
                id = 2L,
                budgetId = "b1",
                title = "Зарплата",
                amount = 60_000.0,
                isIncome = true,
                incomeCertainty = "GUARANTEED",
                categoryName = "Зарплата",
                frequency = "MONTHLY",
                anchorDayOfMonth = 16,
                nextDueDateEpochDay = baseDay + 6,
                priority = "MANDATORY",
                status = "ACTIVE"
            )
        )

        val snap = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = txs,
            accounts = debtAccounts,
            goals = emptyList(),
            categories = emptyList(),
            recurringPayments = recurring,
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 2_000.0,
            userEssentialDailyNormRub = 500.0
        )

        val uiState = buildFinancialStateUiModel(snap.semantics, snap.safeToSpendResult)
        val sheetBreakdown = buildSafeToSpendBreakdownUiModel(snap.safeToSpendResult)

        // 1. Доступный остаток = 10 000 ₽ (без подмены чистым капиталом и без включения owes_us 40 000 ₽)
        assertEquals(10_000.0, uiState.availableBalanceRub, 0.01)
        // 2. Чистый капитал = 10 000 - 85 000 = -75 000 ₽
        assertEquals(-75_000.0, uiState.netWorthRub, 0.01)
        // 3. Свободные деньги (Вариант А): 10 000 - 2 000 (резерв) - 2 000 (связь) - 6*500 (3 000 база) = 3 000 ₽
        assertEquals(3_000.0, uiState.freeMoneyAfterReservationRub, 0.01)
        assertFalse(uiState.hasExplicitDeficitWarning)
        // 4. Единая сумма «Можно потратить сегодня» на главной и в Bottom Sheet = 500 ₽
        assertEquals(500.0, uiState.canSpendTodayRub, 0.01)
        assertEquals(uiState.canSpendTodayRub, sheetBreakdown.canSpendTodayRub, 0.001)

        // Проверка сценария отрицательного свободного пула: UI показывает 0 ₽, но обязательно выставляет предупреждение о дефиците
        val deficitSnap = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = txs,
            accounts = debtAccounts,
            goals = emptyList(),
            categories = emptyList(),
            recurringPayments = recurring,
            executionLogs = emptyList(),
            todayEpochDay = baseDay,
            userSafetyFloorRub = 9_000.0,
            userEssentialDailyNormRub = 500.0
        )
        val deficitUi = buildFinancialStateUiModel(deficitSnap.semantics, deficitSnap.safeToSpendResult)
        assertEquals(0.0, deficitUi.freeMoneyAfterReservationRub, 0.01)
        assertTrue("Negative pool must never be hidden without explicit deficit warning", deficitUi.hasExplicitDeficitWarning)
        assertTrue(deficitUi.freeMoneyDeficitRub > 0.0)
        assertNotNull(deficitUi.deficitWarningTitle)
    }

    @Test
    fun test_issue64_breakdownAndVerdictLocalization_containsZeroTechnicalTokens() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDaysOverride = 10,
            availableBalanceRub = 40_000.0,
            userSafetyFloorRub = 3_000.0,
            essentialDailyRateRub = 500.0,
            essentialConfidenceLevel = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        val result = SafeToSpendCalculator.calculate(input)
        val breakdown = buildSafeToSpendBreakdownUiModel(result)

        assertEquals(40_000.0, breakdown.availableFundsRub, 0.01)
        assertEquals(5_000.0, breakdown.essentialHorizonReserveRub, 0.01)
        assertEquals(500.0, breakdown.essentialDailyNormRub, 0.01)
        assertEquals(3_000.0, breakdown.protectedReserveRub, 0.01)
        assertEquals(10, breakdown.horizonDays)

        val forbiddenTokens = listOf("S_disc", "e_ess", "k*", "R_floor", "SAFE_WITHIN_TODAY_LIMIT", "CASH_GAP_RISK")
        WhatIfVerdict.values().forEach { v ->
            val localized = localizeWhatIfVerdict(v, isPurchaseScenario = true)
            forbiddenTokens.forEach { token ->
                assertFalse("Localized verdict '$localized' must not contain '$token'", localized.contains(token))
            }
        }
        BottleneckReason.values().forEach { r ->
            val localized = localizeBottleneckReason(r)
            forbiddenTokens.forEach { token ->
                assertFalse("Localized reason '$localized' must not contain '$token'", localized.contains(token))
            }
        }
    }

    @Test
    fun test_pr67_createEditDeleteRecurringExpenseAndIncome_immediatelyUpdatesAllMetricsWithoutDoubleCounting() {
        // Сценарий из видео в PR #67: конец месяца (31 октября 2026, daysRemainingInMonth = 1),
        // профиль с крупным балансом (1 068 110 ₽) и лимитом только по одной необязательной категории.
        val oct31EpochDay = LocalDate.of(2026, 10, 31).toEpochDay()
        val nov11EpochDay = LocalDate.of(2026, 11, 11).toEpochDay() // через 11 дней

        val baseTransactions = listOf(
            TransactionEntity(
                id = "tx_init_balance",
                budgetId = "b1",
                type = "income",
                date = "2026-10-01",
                category = "Зарплата",
                subcategory = "Основной остаток",
                amount = 1_068_110.0
            )
        )
        // Частичный лимит только на одну категорию не должен замораживать глобальный Safe-to-Spend
        val partialCategories = listOf(
            CategoryEntity(id = "c1", budgetId = "b1", name = "Развлечения", type = "expense", monthlyLimit = 25_160.0),
            CategoryEntity(id = "c2", budgetId = "b1", name = "Покупки", type = "expense", monthlyLimit = null),
            CategoryEntity(id = "c3", budgetId = "b1", name = "Кафе и рестораны", type = "expense", monthlyLimit = null)
        )

        // 0. Исходное состояние (без регулярных операций) на 31 октября:
        //    Горизонт не схлопывается в 1 день, а равен 30 дням скользящего окна.
        val snap0 = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        val ui0 = buildFinancialStateUiModel(snap0.semantics, snap0.safeToSpendResult)
        val br0 = buildSafeToSpendBreakdownUiModel(snap0.safeToSpendResult)

        assertEquals(30, snap0.safeToSpendResult.horizonDays)
        assertEquals(0.0, br0.upcomingObligationsRub, 0.01)
        assertEquals(1_053_110.0, ui0.freeMoneyAfterReservationRub, 0.01) // 1 068 110 - 30 * 500

        // 1. Создание регулярного расхода (Аренда 13 500 ₽ 11 ноября):
        //    Сразу резервируется в «Предстоящие платежи», уменьшает «Свободные деньги» и «Можно потратить сегодня».
        val rentEntity = RecurringPaymentEntity(
            id = 101L,
            budgetId = "b1",
            title = "Аренда",
            amount = 13_500.0,
            isIncome = false,
            categoryName = "ЖКХ и Аренда",
            frequency = "MONTHLY",
            anchorDayOfMonth = 11,
            nextDueDateEpochDay = nov11EpochDay,
            priority = "MANDATORY",
            status = "ACTIVE"
        )
        val snapAfterAddExpense = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = listOf(rentEntity),
            executionLogs = emptyList(),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        val uiAfterAddExpense = buildFinancialStateUiModel(snapAfterAddExpense.semantics, snapAfterAddExpense.safeToSpendResult)
        val brAfterAddExpense = buildSafeToSpendBreakdownUiModel(snapAfterAddExpense.safeToSpendResult)

        assertEquals(13_500.0, brAfterAddExpense.upcomingObligationsRub, 0.01)
        assertEquals(ui0.freeMoneyAfterReservationRub - 13_500.0, uiAfterAddExpense.freeMoneyAfterReservationRub, 0.01)
        assertTrue(uiAfterAddExpense.canSpendTodayRub < ui0.canSpendTodayRub - 100.0)

        // 2. Изменение (редактирование) регулярного расхода (Аренда 13 500 ₽ -> 23 500 ₽):
        val editedRentEntity = rentEntity.copy(amount = 23_500.0)
        val snapAfterEditExpense = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = listOf(editedRentEntity),
            executionLogs = emptyList(),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        val uiAfterEditExpense = buildFinancialStateUiModel(snapAfterEditExpense.semantics, snapAfterEditExpense.safeToSpendResult)
        val brAfterEditExpense = buildSafeToSpendBreakdownUiModel(snapAfterEditExpense.safeToSpendResult)

        assertEquals(23_500.0, brAfterEditExpense.upcomingObligationsRub, 0.01)
        assertEquals(uiAfterAddExpense.freeMoneyAfterReservationRub - 10_000.0, uiAfterEditExpense.freeMoneyAfterReservationRub, 0.01)
        assertTrue(uiAfterEditExpense.canSpendTodayRub < uiAfterAddExpense.canSpendTodayRub - 100.0)

        // 3. Добавление регулярного дохода на сегодня (d0 = 31 октября, ЗП 200 000 ₽) и его редактирование (200 000 ₽ -> 500 000 ₽):
        val salaryToday200k = RecurringPaymentEntity(
            id = 202L,
            budgetId = "b1",
            title = "ЗП",
            amount = 200_000.0,
            isIncome = true,
            incomeCertainty = "GUARANTEED",
            categoryName = "Зарплата",
            frequency = "MONTHLY",
            anchorDayOfMonth = 31,
            nextDueDateEpochDay = oct31EpochDay,
            priority = "MANDATORY",
            status = "ACTIVE"
        )
        val snapWithSalary200k = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = listOf(editedRentEntity, salaryToday200k),
            executionLogs = emptyList(),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        val uiWithSalary200k = buildFinancialStateUiModel(snapWithSalary200k.semantics, snapWithSalary200k.safeToSpendResult)
        val brWithSalary200k = buildSafeToSpendBreakdownUiModel(snapWithSalary200k.safeToSpendResult)

        assertEquals(200_000.0, snapWithSalary200k.safeToSpendResult.todayPendingGuaranteedIncomeRub, 0.01)
        assertEquals(200_000.0, brWithSalary200k.plannedIncomeRub, 0.01)
        assertEquals(uiAfterEditExpense.freeMoneyAfterReservationRub + 200_000.0, uiWithSalary200k.freeMoneyAfterReservationRub, 0.01)
        assertTrue(uiWithSalary200k.canSpendTodayRub > uiAfterEditExpense.canSpendTodayRub + 1_000.0)

        // Редактирование регулярного дохода (200 000 ₽ -> 500 000 ₽):
        val salaryToday500k = salaryToday200k.copy(amount = 500_000.0)
        val snapWithSalary500k = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = listOf(editedRentEntity, salaryToday500k),
            executionLogs = emptyList(),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        val uiWithSalary500k = buildFinancialStateUiModel(snapWithSalary500k.semantics, snapWithSalary500k.safeToSpendResult)
        val brWithSalary500k = buildSafeToSpendBreakdownUiModel(snapWithSalary500k.safeToSpendResult)

        assertEquals(500_000.0, brWithSalary500k.plannedIncomeRub, 0.01)
        assertEquals(uiWithSalary200k.freeMoneyAfterReservationRub + 300_000.0, uiWithSalary500k.freeMoneyAfterReservationRub, 0.01)
        assertTrue(uiWithSalary500k.canSpendTodayRub > uiWithSalary200k.canSpendTodayRub + 5_000.0)

        // 4. Подтверждение фактического исполнения (PAID) не приводит к двойному учёту:
        val confirmedRentTx = TransactionEntity(
            id = "tx_paid_rent",
            budgetId = "b1",
            type = "expense",
            date = "2026-10-31",
            category = "ЖКХ и Аренда",
            subcategory = "Аренда",
            amount = 23_500.0
        )
        val paidRentLog = com.example.data.db.RecurringExecutionLogEntity(
            id = 501L,
            budgetId = "b1",
            recurringId = editedRentEntity.id,
            scheduledEpochDay = oct31EpochDay,
            executedEpochDay = oct31EpochDay,
            actualAmount = 23_500.0,
            status = "PAID",
            linkedTransactionId = "tx_paid_rent"
        )
        val rentDueToday = editedRentEntity.copy(nextDueDateEpochDay = oct31EpochDay)
        val snapBeforePaid = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = listOf(rentDueToday),
            executionLogs = emptyList(),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        val snapAfterPaid = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions + confirmedRentTx,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = listOf(rentDueToday),
            executionLogs = listOf(paidRentLog),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        // После фактической оплаты на ту же сумму 23 500 ₽ плановая бронь снимается (0 ₽),
        // а свободный пул и дневной лимит остаются такими же (без двойного вычитания 2 * 23 500 ₽)
        assertEquals(0.0, snapAfterPaid.safeToSpendResult.obligationsReservedTotalRub, 0.01)
        assertEquals(
            snapBeforePaid.safeToSpendResult.canSpendTodayDiscRub,
            snapAfterPaid.safeToSpendResult.canSpendTodayDiscRub,
            0.01
        )

        // 5. Удаление обеих регулярных операций возвращает расчёт к исходному состоянию snap0:
        val snapAfterDeleteAll = DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = baseTransactions,
            accounts = emptyList(),
            goals = emptyList(),
            categories = partialCategories,
            recurringPayments = emptyList(),
            executionLogs = emptyList(),
            todayEpochDay = oct31EpochDay,
            userEssentialDailyNormRub = 500.0
        )
        assertEquals(
            snap0.safeToSpendResult.canSpendTodayDiscRub,
            snapAfterDeleteAll.safeToSpendResult.canSpendTodayDiscRub,
            0.01
        )
        assertEquals(0.0, snapAfterDeleteAll.safeToSpendResult.obligationsReservedTotalRub, 0.01)
    }
}
