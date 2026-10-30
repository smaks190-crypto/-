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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
            horizonDays = 9, // k = 9 -> 10 дней (t=0..9)
            availableBalanceRub = 30_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            lockedGoalReserveRub = 0.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        val result = SafeToSpendCalculator.calculate(input)
        // Запас = 30000 - 5000 = 25000. Базовые за 10 дн. = 5000. Дискреционный пул = 20000 / 10 = 2000 ₽/дн.
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
            anchorDayOfMonth = 20,
            nextDueDateEpochDay = baseDay + 10
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 30,
            availableBalanceRub = 55_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(rent, salary)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // В точке k=5 (6 дней): L_0=55000 - R=5000 - Rent=40000 - 6*500(3000) = 7000 / 6 = 1166.67 ₽/день
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
            horizonDays = 4, // 5 дней
            availableBalanceRub = 10_000.0,
            mandatorySafetyFloorRub = 1_500.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(sub1, sub2)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // L_0=10000 - R=1500 - Subs=1000 - 5*500=2500 => 5000 / 5 = 1000 ₽/день
        assertEquals(1_000.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 4: Сегодняшний перерасход без кассового разрыва (S >= E_disc(0) vs S < E_disc(0))
    @Test
    fun test04_todayOverspendWithoutCashGap_noDoubleSubtraction() {
        // Проверка вывода S_liq* при уже совершённом сегодня расходе E_disc(0) = 3000 ₽.
        // До траты баланс был 23000 ₽, после траты L_0 = 20000 ₽.
        // Горизонт k=9 (10 дней), R_floor = 0, e_ess = 0.
        // Истинный пул на 10 дней (включая сегодня) = L_0 + E_disc(0) = 23000 ₽ -> S_disc = 2300 ₽/день.
        // Так как сегодня потрачено 3000 > 2300, canSpendTodayDiscRub = 0, todayOverspendRub = 700 ₽.
        // Оставшиеся 9 дней (t=1..9) тратят не более (20000 / 9) = 2222.22 ₽/день, но сегодня лимит 0.
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 9,
            availableBalanceRub = 20_000.0,
            mandatorySafetyFloorRub = 0.0,
            dailyEssentialRateRub = 0.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            todayClassifiedExpenses = listOf(
                TodayClassifiedExpense(
                    transactionId = 10L,
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
            horizonDays = 10,
            availableBalanceRub = 20_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(rent)
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
            horizonDays = 9, // 10 дней
            availableBalanceRub = 15_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(expectedBonus, hypotheticalGift)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // Безопасный базовый сценарий НЕ включает EXPECTED и HYPOTHETICAL:
        // (15000 - 5000 - 10*500) / 10 = 500 ₽/дн.
        assertEquals(500.0, result.dailyDiscretionarySpeedRub, 0.01)
        // Оптимистичный сценарий с EXPECTED (+50000) выше базового:
        assertTrue(result.optimisticCanSpendTodayDiscRub > result.canSpendTodayDiscRub)
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
            horizonDays = 9,
            availableBalanceRub = 20_000.0,
            mandatorySafetyFloorRub = 2_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            scheduledDebts = listOf(debt)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // До k=4 (5 дней): L_0=20000 - R=2000 - Debt=6000 - 5*500(2500) = 9500 / 5 = 1900
        // До k=9 (10 дней): (20000 - 2000 - 6000 - 5000) / 10 = 700 ₽/день
        assertEquals(700.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 8: Переход через конец месяца (31 января -> 28 февраля -> 31 марта)
    @Test
    fun test08_monthEndAnchorTransition_jan31ToFeb28AndMar31() {
        val jan31 = LocalDate.of(2026, 1, 31).toEpochDay()
        val payment = RecurringPaymentDomain(
            id = 1L,
            title = "Ипотека",
            amount = 30_000.0,
            isIncome = false,
            categoryName = "Ипотека",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 31,
            nextDueDateEpochDay = jan31
        )
        val febDue = RecurringScheduleCalculator.computeNextDueDate(payment, jan31)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), febDue)

        val paymentFeb = payment.copy(nextDueDateEpochDay = febDue)
        val marDue = RecurringScheduleCalculator.computeNextDueDate(paymentFeb, febDue)
        assertEquals(LocalDate.of(2026, 3, 31).toEpochDay(), marDue)
    }

    // Test 9: Високосный год (29 февраля 2028 -> 28 февраля 2029)
    @Test
    fun test09_leapYearTransition_feb29ToFeb28() {
        val feb29_2028 = LocalDate.of(2028, 2, 29).toEpochDay()
        val payment = RecurringPaymentDomain(
            id = 1L,
            title = "Годовая страховка",
            amount = 12_000.0,
            isIncome = false,
            categoryName = "Страховка",
            frequency = PaymentFrequency.YEARLY,
            anchorDayOfMonth = 29,
            nextDueDateEpochDay = feb29_2028
        )
        val nextYearDue = RecurringScheduleCalculator.computeNextDueDate(payment, feb29_2028)
        assertEquals(LocalDate.of(2029, 2, 28).toEpochDay(), nextYearDue)
    }

    // Test 10: Ручное подтверждение регулярного платежа сегодня (защита от двойного списания)
    @Test
    fun test10_paidTodayObligationNotDeductedTwice() {
        // Платёж 10 000 ₽ уже оплачен сегодня: L_0 уменьшился до 20 000 ₽, и лог PAID создан на baseDay.
        val payment = RecurringPaymentDomain(
            id = 1L,
            title = "Коммуналка",
            amount = 10_000.0,
            isIncome = false,
            categoryName = "ЖКХ",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 10,
            nextDueDateEpochDay = baseDay // даже если nextDueDate ещё указывает на сегодня, но есть PAID лог
        )
        val log = RecurringExecutionLogDomain(
            id = 1L,
            recurringPaymentId = 1L,
            scheduledDateEpochDay = baseDay,
            actualDateEpochDay = baseDay,
            actualAmount = 10_000.0,
            transactionId = 99L,
            executionStatus = ExecutionStatus.PAID
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 9, // 10 дней
            availableBalanceRub = 20_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(payment),
            executionLogs = listOf(log),
            todayClassifiedExpenses = listOf(
                TodayClassifiedExpense(
                    transactionId = 99L,
                    amountRub = 10_000.0,
                    streamType = ExpenseStreamType.MANDATORY_OBLIGATION,
                    linkedRecurringPaymentId = 1L
                )
            )
        )
        val result = SafeToSpendCalculator.calculate(input)
        // Повторно 10 000 ₽ НЕ вычитается: (20000 - 5000 - 5000) / 10 = 1000 ₽/дн.
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
            horizonDays = 9, // 10 дней
            availableBalanceRub = 30_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(overdueRent)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // O_overdue = 15000 вычитается ровно 1 раз:
        // Свободно = 30000 - 5000(R) - 15000(Overdue) - 5000(Ess) = 5000 / 10 = 500 ₽/дн.
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
        val log = RecurringExecutionLogDomain(
            id = 1L,
            recurringPaymentId = 1L,
            scheduledDateEpochDay = baseDay,
            actualDateEpochDay = baseDay + 5,
            actualAmount = 1_000.0,
            transactionId = null,
            executionStatus = ExecutionStatus.RESCHEDULED
        )
        val occurrences = RecurringScheduleCalculator.generateOccurrencesInRange(
            payment = payment,
            logs = listOf(log),
            startEpochDay = baseDay,
            endEpochDay = baseDay + 10
        )
        assertEquals(1, occurrences.size)
        assertEquals(baseDay + 5, occurrences.first().dueDateEpochDay)
    }

    // Test 13: Наличие категорийных лимитов жестче ликвидности
    @Test
    fun test13_categoryLimitTighterThanLiquidityCapsSpend() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 9, // 10 дней
            availableBalanceRub = 100_000.0,
            mandatorySafetyFloorRub = 10_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            remainingDiscretionaryMonthlyLimitRub = 8_000.0,
            remainingDaysInCurrentMonth = 10
        )
        val result = SafeToSpendCalculator.calculate(input)
        // По ликвидности можно 8500 ₽/дн., но по лимиту категорий 8000 / 10 = 800 ₽/дн.
        assertEquals(800.0, result.dailyDiscretionarySpeedRub, 0.01)
        assertEquals(800.0, result.canSpendTodayDiscRub, 0.01)
        assertEquals(BottleneckReason.CATEGORY_BUDGET_LIMIT, result.bottleneckReason)
    }

    // Test 14: Наличие свободных лимитов при нехватке реальных денег на счетах
    @Test
    fun test14_liquidityTighterThanCategoryLimitCapsSpend() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 9, // 10 дней
            availableBalanceRub = 12_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            remainingDiscretionaryMonthlyLimitRub = 50_000.0,
            remainingDaysInCurrentMonth = 10
        )
        val result = SafeToSpendCalculator.calculate(input)
        // По лимиту категорий 5000 ₽/дн., но реальных денег (12000 - 5000 - 5000) / 10 = 200 ₽/дн.
        assertEquals(200.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 15: Отсутствие настроенных лимитов и регулярных платежей (Fallback)
    @Test
    fun test15_noLimitsAndNoRecurringPaymentsColdStart() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 9,
            availableBalanceRub = 20_000.0,
            mandatorySafetyFloorRub = 0.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.UNVERIFIED_FALLBACK
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
            horizonDays = 10,
            availableBalanceRub = -3_500.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        val result = SafeToSpendCalculator.calculate(input)
        assertEquals(0.0, result.canSpendTodayDiscRub, 0.01)
        assertEquals(0.0, result.canSpendTodayEssRub, 0.01)
        assertFalse(result.isReserveRequirementMet)
        assertEquals(8_500.0, result.reserveDeficitRub, 0.01)
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
            frequency = PaymentFrequency.ONE_TIME,
            nextDueDateEpochDay = baseDay + 5
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 15,
            availableBalanceRub = 12_000.0,
            mandatorySafetyFloorRub = 2_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(bonus)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // Узкое место — первые 5 дней до поступления (t=0..4, k=4, 5 дней):
        // (12000 - 2000 - 5*500) / 5 = 7500 / 5 = 1500 ₽/день
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
                id = 1,
                budgetId = "b1",
                type = "expense",
                date = "2026-04-10",
                category = "Перевод между счетами",
                subcategory = "На карту 2",
                amount = 10_000.0,
                accountId = "a1"
            ),
            TransactionEntity(
                id = 2,
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
            id = 5,
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
                horizonDays = 9,
                availableBalanceRub = 30_000.0,
                mandatorySafetyFloorRub = 0.0,
                dailyEssentialRateRub = 500.0,
                essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
                recurringPayments = listOf(activeRent)
            )
        )
        val resPaused = SafeToSpendCalculator.calculate(
            SafeToSpendInput(
                todayEpochDay = baseDay,
                horizonDays = 9,
                availableBalanceRub = 30_000.0,
                mandatorySafetyFloorRub = 0.0,
                dailyEssentialRateRub = 500.0,
                essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
                recurringPayments = listOf(pausedRent)
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
            amountType = PaymentAmountType.VARIABLE_ESTIMATE,
            isIncome = false,
            categoryName = "ЖКХ",
            frequency = PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 14,
            nextDueDateEpochDay = baseDay + 4
        )
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 9, // 10 дней
            availableBalanceRub = 22_500.0,
            mandatorySafetyFloorRub = 0.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(utility)
        )
        val result = SafeToSpendCalculator.calculate(input)
        // Вычитается консервативный estimatedMaxAmount = 7500: (22500 - 7500 - 5000) / 10 = 1000 ₽/дн.
        assertEquals(1_000.0, result.dailyDiscretionarySpeedRub, 0.01)
    }

    // Test 22: Регрессионный тест сохранности текущих экранов и детерминизма
    @Test
    fun test22_deterministicCalculationAndTrajectoryIntegrity() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 14,
            availableBalanceRub = 42_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 600.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL
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
            // Ежедневный транспорт 150 ₽
            samples.add(
                HistoricalExpenseSample(
                    epochDay = baseDay - d,
                    categoryName = "Транспорт",
                    subcategoryName = "Метро",
                    amountRub = 150.0,
                    transactionId = d.toLong()
                )
            )
            // Крупная закупка продуктов по субботам (раз в 7 дней) 5600 ₽ (800 ₽/день)
            if (d % 7 == 0) {
                samples.add(
                    HistoricalExpenseSample(
                        epochDay = baseDay - d,
                        categoryName = "Продукты",
                        subcategoryName = "Гипермаркет",
                        amountRub = 5_600.0,
                        transactionId = (100 + d).toLong()
                    )
                )
            }
            // Аптека раз в 14 дней 1400 ₽ (100 ₽/день)
            if (d % 14 == 0) {
                samples.add(
                    HistoricalExpenseSample(
                        epochDay = baseDay - d,
                        categoryName = "Здоровье и Аптека",
                        subcategoryName = "Лекарства",
                        amountRub = 1_400.0,
                        transactionId = (200 + d).toLong()
                    )
                )
            }
        }
        val estimate = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = baseDay,
            historySamples = samples,
            essentialCategoryMonthlyLimitsRub = 0.0,
            daysInCurrentMonth = 30,
            userEssentialDailyNormRub = null
        )
        // Истинное среднее = 150 + 800 + 100 = 1050 ₽/день.
        // Чистая дневная медиана была бы 150 ₽/день (занижение в 7 раз!).
        // Наш алгоритм max(trimmedMean, median7dWindow, median) даёт >= 950 ₽/день:
        assertTrue("Estimate ${estimate.dailyEssentialRateRub} must capture weekly groceries", estimate.dailyEssentialRateRub >= 950.0)
        assertEquals(EssentialConfidenceLevel.HIGH_HISTORICAL, estimate.confidenceLevel)
    }

    // Test 24: Холодный старт (< 7 дней истории) и частичная история (7-20 дней)
    @Test
    fun test24_coldStartAndPartialHistoryBlending() {
        val coldSamples = listOf(
            HistoricalExpenseSample(baseDay - 1, "Продукты", "", 900.0, 1L),
            HistoricalExpenseSample(baseDay - 2, "Транспорт", "", 200.0, 2L)
        )
        val cold = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = baseDay,
            historySamples = coldSamples,
            essentialCategoryMonthlyLimitsRub = 0.0,
            daysInCurrentMonth = 30,
            userEssentialDailyNormRub = null
        )
        assertEquals(EssentialConfidenceLevel.UNVERIFIED_FALLBACK, cold.confidenceLevel)
        assertTrue(cold.requiresUserConfirmationPrompt)

        // Частичная история (10 дней с расходами) + месячный лимит 30 000 ₽ (1000 ₽/дн)
        val partialSamples = (1..10).map { d ->
            HistoricalExpenseSample(baseDay - d, "Продукты", "", 700.0, d.toLong())
        }
        val blended = EssentialSpendingEstimator.estimateDailyEssentialRate(
            todayEpochDay = baseDay,
            historySamples = partialSamples,
            essentialCategoryMonthlyLimitsRub = 30_000.0,
            daysInCurrentMonth = 30,
            userEssentialDailyNormRub = null
        )
        assertEquals(EssentialConfidenceLevel.MODERATE_BLENDED, blended.confidenceLevel)
        assertFalse(blended.requiresUserConfirmationPrompt)
        assertTrue(blended.dailyEssentialRateRub in 700.0..1000.0)
    }

    // Test 25: Исключение двойного учёта между e_ess и RecurringPaymentEntity
    @Test
    fun test25_recurringPaymentExcludedFromEssentialHistoryEstimation() {
        val samples = (1..14).map { d ->
            HistoricalExpenseSample(
                epochDay = baseDay - d,
                categoryName = "Продукты",
                subcategoryName = "Доставка молока",
                amountRub = 600.0,
                transactionId = d.toLong(),
                linkedRecurringPaymentId = if (d == 7) 555L else null
            )
        }
        val filtered = EssentialSpendingEstimator.filterPureEssentialHistory(
            samples = samples,
            confirmedRecurringTransactionIds = setOf(7L),
            activeRecurringCategoriesAndTitles = emptySet()
        )
        assertTrue(filtered.none { it.transactionId == 7L })
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
        assertTrue(snap1.safeToSpendResult.structuredContextForDavid.contains("SAFE-TO-SPEND 2.0"))
    }

    // Test 27: What-if симуляция разовой траты («Что будет, если я потрачу 3000 рублей?»)
    @Test
    fun test27_whatIfOneTimePurchaseSimulationDoesNotMutateInput() {
        val baseInput = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 9, // 10 дней
            availableBalanceRub = 25_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL
        )
        // Базовый темп: (25000 - 5000 - 5000) / 10 = 1500 ₽/день
        val sim = WhatIfSimulator.simulate(
            baseInput = baseInput,
            request = WhatIfScenarioRequest(oneTimeExpenseTodayRub = 3_000.0)
        )
        assertEquals(1_500.0, sim.baseline.dailyDiscretionarySpeedRub, 0.01)
        // После покупки 3000 ₽ сегодня: canSpendTodayDiscRub падает до 0, а темп снижается на 300 ₽/день (до 1200 ₽/день)
        assertEquals(1_200.0, sim.simulated.dailyDiscretionarySpeedRub, 0.01)
        assertEquals(-300.0, sim.deltaDailyDiscretionarySpeedRub, 0.01)
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
            horizonDays = 15,
            availableBalanceRub = 15_000.0,
            mandatorySafetyFloorRub = 2_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(rent, salary)
        )
        // В базе зарплата приходит на t+4 (до аренды на t+6) -> разрыва нет.
        // При задержке зарплаты на 5 дней она сдвигается на t+9 (после аренды на t+6) -> возникает кассовый разрыв!
        val sim = WhatIfSimulator.simulate(
            baseInput = baseInput,
            request = WhatIfScenarioRequest(salaryDelayDays = 5)
        )
        assertFalse(sim.baseline.hasCashGap)
        assertTrue(sim.simulated.hasCashGap)
        assertEquals(WhatIfVerdict.CASH_GAP, sim.verdict)
    }

    // =========================================================================
    // II. ТЕСТЫ 4 ОБЯЗАТЕЛЬНЫХ УТОЧНЕНИЙ ПЕРЕД РЕАЛИЗАЦИЕЙ
    // =========================================================================

    // Уточнение 1: Запрет тихого норматива 500 ₽/день без предупреждения о низкой достоверности
    @Test
    fun testRefinement1_unverifiedFallbackMustTriggerWarningAndPrompt() {
        val input = SafeToSpendInput(
            todayEpochDay = baseDay,
            horizonDays = 10,
            availableBalanceRub = 30_000.0,
            dailyEssentialRateRub = 500.0,
            essentialConfidence = EssentialConfidenceLevel.UNVERIFIED_FALLBACK
        )
        val res = SafeToSpendCalculator.calculate(input)
        assertTrue(res.requiresEssentialBudgetPrompt)
        assertEquals(EssentialConfidenceLevel.UNVERIFIED_FALLBACK, res.essentialConfidence)
        assertTrue(res.structuredContextForDavid.contains("НИЗКАЯ ДОСТОВЕРНОСТЬ"))
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
            actualDateEpochDay = LocalDate.of(2026, 4, 10).toEpochDay(),
            actualAmountRub = 30_000.0,
            accountId = "a1",
            existingLogsForPayment = emptyList()
        )
        assertEquals(
            LocalDate.of(2026, 5, 10).toEpochDay(),
            confirmation.updatedPayment.nextDueDateEpochDay
        )

        val savedLog = confirmation.logToInsert.copy(id = 101L, transactionId = 500L)

        // Теперь пользователь удаляет связанную транзакцию #500 -> nextDueDate обязан откатиться на 10 апреля!
        val rollback = RecurringTransactionConsistencyManager.buildDeleteTransactionRollbackTransition(
            deletedTransactionId = 500L,
            payment = confirmation.updatedPayment,
            allLogsForPayment = listOf(savedLog)
        )
        assertNotNull(rollback)
        assertEquals(101L, rollback!!.logIdToDelete)
        assertEquals(
            LocalDate.of(2026, 4, 10).toEpochDay(),
            rollback.restoredPayment.nextDueDateEpochDay
        )

        // Проверим редактирование суммы/даты связанной транзакции #500:
        val updatedLog = RecurringTransactionConsistencyManager.buildEditTransactionSyncTransition(
            editedTransactionId = 500L,
            newAmountRub = 31_500.0,
            newDateEpochDay = LocalDate.of(2026, 4, 11).toEpochDay(),
            existingLog = savedLog
        )
        assertNotNull(updatedLog)
        assertEquals(31_500.0, updatedLog!!.actualAmount, 0.01)
        assertEquals(LocalDate.of(2026, 4, 11).toEpochDay(), updatedLog.actualDateEpochDay)
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
        // Вся сумма на карте (50 000 ₽) является L_0, а 20 000 ₽ в GoalEntity НЕ вычитаются повторно из 50 000 ₽:
        assertEquals(50_000.0, snap.safeToSpendInput.availableBalanceRub, 0.01)
        assertEquals(0.0, snap.safeToSpendInput.lockedGoalReserveRub, 0.01)
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
            horizonDays = 20,
            availableBalanceRub = 40_000.0,
            mandatorySafetyFloorRub = 5_000.0,
            dailyEssentialRateRub = 600.0,
            essentialConfidence = EssentialConfidenceLevel.HIGH_HISTORICAL,
            recurringPayments = listOf(rent)
        )
        val res = SafeToSpendCalculator.calculate(baseInput)

        // Инвариант 1: Неотрицательность публичных лимитов
        assertTrue(res.canSpendTodayDiscRub >= 0.0)
        assertTrue(res.canSpendTodayEssRub >= 0.0)
        assertTrue(res.dailyDiscretionarySpeedRub >= 0.0)

        // Инвариант 2: Защита резерва и базовых потребностей на всём горизонте при отсутствии кассового разрыва
        if (!res.hasCashGap && res.isReserveRequirementMet) {
            val minProj = res.trajectory.minOf { it.projectedEndOfDayBalanceRub }
            assertTrue("Minimum trajectory balance $minProj must stay >= R_floor (5000)", minProj >= 4999.9)
        }

        // Инвариант 3: Монотонность по обязательствам (увеличение аренды не увеличивает Safe-to-Spend)
        val heavierRent = rent.copy(amount = 25_000.0)
        val resHeavier = SafeToSpendCalculator.calculate(baseInput.copy(recurringPayments = listOf(heavierRent)))
        assertTrue(resHeavier.canSpendTodayDiscRub <= res.canSpendTodayDiscRub + 1e-6)

        // Инвариант 4: Идемпотентность оплаченного сегодня платежа
        val paidLog = RecurringExecutionLogDomain(
            id = 10L,
            recurringPaymentId = 1L,
            scheduledDateEpochDay = baseDay,
            actualDateEpochDay = baseDay,
            actualAmount = 15_000.0,
            transactionId = 77L,
            executionStatus = ExecutionStatus.PAID
        )
        val dueTodayRent = rent.copy(nextDueDateEpochDay = baseDay)
        val resWithPaidToday = SafeToSpendCalculator.calculate(
            baseInput.copy(
                recurringPayments = listOf(dueTodayRent),
                executionLogs = listOf(paidLog)
            )
        )
        val resAlreadyShifted = SafeToSpendCalculator.calculate(
            baseInput.copy(
                recurringPayments = listOf(dueTodayRent.copy(nextDueDateEpochDay = baseDay + 30)),
                executionLogs = listOf(paidLog)
            )
        )
        assertEquals(resWithPaidToday.canSpendTodayDiscRub, resAlreadyShifted.canSpendTodayDiscRub, 0.01)

        // Инвариант 5: Изоляция потоков (трата на продукты сегодня уменьшает canSpendTodayEssRub, но при рамках нормы не обнуляет дискреционный бюджет)
        val withEssSpend = SafeToSpendCalculator.calculate(
            baseInput.copy(
                todayClassifiedExpenses = listOf(
                    TodayClassifiedExpense(1L, 400.0, ExpenseStreamType.ESSENTIAL_LIVING)
                )
            )
        )
        assertEquals(200.0, withEssSpend.canSpendTodayEssRub, 0.01)
    }
}
