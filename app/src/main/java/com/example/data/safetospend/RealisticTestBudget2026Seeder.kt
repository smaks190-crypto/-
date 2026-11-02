package com.example.data.safetospend

import com.example.data.db.AccountEntity
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.RecurringExecutionLogEntity
import com.example.data.db.RecurringPaymentEntity
import com.example.data.db.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class RealisticTestBudgetDataset(
    val profile: BudgetProfileEntity,
    val referenceTodayEpochDay: Long,
    val referenceTodayIso: String,
    val mandatorySafetyFloorRub: Double = 15_000.0,
    val essentialDailyNormRub: Double = 1_200.0,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val goals: List<GoalEntity>,
    val recurringPayments: List<RecurringPaymentEntity>,
    val executionLogs: List<RecurringExecutionLogEntity>,
    val transactions: List<TransactionEntity>
) {
    val recurringExecutionLogs: List<RecurringExecutionLogEntity>
        get() = executionLogs
}

/**
 * Generates an isolated, realistic, deterministic personal finance dataset covering
 * **01.01.2026 – 02.11.2026 inclusive** (zero future transactions after 2026-11-02).
 *
 * Designed for end-to-end verification of David Live and Safe-to-Spend 2.0 tools
 * without modifying or touching any real user profile.
 */
object RealisticTestBudget2026Seeder {

    const val TEST_BUDGET_ID = "demo_2026_test"
    const val TEST_PROFILE_NAME = "Тест 2026 (01.01–02.11)"
    const val TEST_BUDGET_NAME = TEST_PROFILE_NAME
    val REFERENCE_TODAY_DATE: LocalDate = LocalDate.of(2026, 11, 2)
    val REFERENCE_TODAY_EPOCH_DAY: Long = REFERENCE_TODAY_DATE.toEpochDay()
    const val REFERENCE_TODAY_ISO: String = "2026-11-02"

    fun buildDataset(budgetId: String = TEST_BUDGET_ID): RealisticTestBudgetDataset {
        val profile = BudgetProfileEntity(
            id = budgetId,
            name = if (budgetId == TEST_BUDGET_ID) TEST_PROFILE_NAME else "Демо-бюджет 2026"
        )

        val accounts = buildAccounts(budgetId)
        val categories = buildCategories(budgetId)
        val goals = buildGoals(budgetId)
        val recurringPayments = buildRecurringPayments(budgetId)
        val executionLogs = buildRecurringExecutionLogs(budgetId)
        val transactions = buildTransactions(budgetId)

        return RealisticTestBudgetDataset(
            profile = profile,
            referenceTodayEpochDay = REFERENCE_TODAY_EPOCH_DAY,
            referenceTodayIso = REFERENCE_TODAY_ISO,
            accounts = accounts,
            categories = categories,
            goals = goals,
            recurringPayments = recurringPayments,
            executionLogs = executionLogs,
            transactions = transactions
        )
    }

    private fun isoToEpochMillis(isoDate: String, hour: Int = 12, minute: Int = 0): Long {
        return LocalDate.parse(isoDate)
            .atTime(hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun buildAccounts(budgetId: String): List<AccountEntity> {
        val p = "${budgetId}_"
        return listOf(
            // Liquid accounts: 68,450 + 42,300 + 4,250 = 115,000 ₽
            AccountEntity(
                id = "${p}acc_main_card",
                budgetId = budgetId,
                name = "Основная карта Т-Банк",
                balance = 68450.0,
                type = "card",
                accountNumber = "•• 4821"
            ),
            AccountEntity(
                id = "${p}acc_reserve_savings",
                budgetId = budgetId,
                name = "Накопительный счёт Сбер",
                balance = 42300.0,
                type = "savings",
                accountNumber = "•• 9104"
            ),
            AccountEntity(
                id = "${p}acc_cash_wallet",
                budgetId = budgetId,
                name = "Наличные в кошельке",
                balance = 4250.0,
                type = "cash",
                accountNumber = ""
            ),
            // Debts — «Я должен» (we_owe): remaining 35,000 + 14,000 = 49,000 ₽
            // FinancialSemanticsCalculator computes remaining = balance - repaid(txs linked by accountId)
            AccountEntity(
                id = "${p}debt_we_owe_installment",
                budgetId = budgetId,
                name = "Рассрочка за ноутбук",
                balance = 60000.0, // 2 repayments of 12,500 ₽ -> remaining 35,000 ₽
                type = "we_owe",
                accountNumber = "2026-11-20"
            ),
            AccountEntity(
                id = "${p}debt_we_owe_brother",
                budgetId = budgetId,
                name = "Долг брату Андрею",
                balance = 20000.0, // 1 repayment of 6,000 ₽ -> remaining 14,000 ₽ due 2026-11-08
                type = "we_owe",
                accountNumber = "2026-11-08"
            ),
            // Debts — «Мне должны» (owes_us): remaining 13,000 + 7,000 = 20,000 ₽
            AccountEntity(
                id = "${p}debt_owes_us_alexey",
                budgetId = budgetId,
                name = "Алексей (коллега)",
                balance = 18000.0, // 1 return of 5,000 ₽ -> remaining 13,000 ₽
                type = "owes_us",
                accountNumber = "2026-11-15"
            ),
            AccountEntity(
                id = "${p}debt_owes_us_dmitry",
                budgetId = budgetId,
                name = "Дмитрий (сосед)",
                balance = 9500.0, // 1 return of 2,500 ₽ -> remaining 7,000 ₽
                type = "owes_us",
                accountNumber = "2026-11-28"
            )
        )
    }

    private fun buildCategories(budgetId: String): List<CategoryEntity> {
        return listOf(
            // Income categories
            CategoryEntity(
                budgetId = budgetId,
                name = "Зарплата",
                type = "income",
                monthlyLimit = 0.0
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Подработка",
                type = "income",
                monthlyLimit = 0.0
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Возврат долга",
                type = "income",
                monthlyLimit = 0.0
            ),
            // Expense categories with limits (some with remaining buffer, some exceeded)
            CategoryEntity(
                budgetId = budgetId,
                name = "Продукты",
                type = "expense",
                monthlyLimit = 28000.0 // 30d spent: 19,840 ₽ (remaining: +8,160 ₽, 71%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "ЖКХ и Аренда",
                type = "expense",
                monthlyLimit = 46000.0 // 30d spent: 44,380 ₽ (remaining: +1,620 ₽, 96%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Транспорт",
                type = "expense",
                monthlyLimit = 6000.0 // 30d spent: 3,920 ₽ (remaining: +2,080 ₽, 65%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Здоровье и Аптека",
                type = "expense",
                monthlyLimit = 5000.0 // 30d spent: 2,860 ₽ (remaining: +2,140 ₽, 57%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Связь и Интернет",
                type = "expense",
                monthlyLimit = 2500.0 // 30d spent: 1,390 ₽ (remaining: +1,110 ₽, 56%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Подписки и Сервисы",
                type = "expense",
                monthlyLimit = 1500.0 // 30d spent: 698 ₽ (remaining: +802 ₽, 47%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Кафе и Доставка",
                type = "expense",
                monthlyLimit = 8000.0 // 30d spent: 9,850 ₽ (EXCEEDED by 1,850 ₽, 123%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Развлечения и Хобби",
                type = "expense",
                monthlyLimit = 7000.0 // 30d spent: 8,400 ₽ (EXCEEDED by 1,400 ₽, 120%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Одежда и Обувь",
                type = "expense",
                monthlyLimit = 10000.0 // 30d spent: 6,490 ₽ (remaining: +3,510 ₽, 65%)
            ),
            CategoryEntity(
                budgetId = budgetId,
                name = "Погашение долга",
                type = "expense",
                monthlyLimit = 0.0
            )
        )
    }

    private fun buildGoals(budgetId: String): List<GoalEntity> {
        val p = "${budgetId}_"
        return listOf(
            GoalEntity(
                id = "${p}goal_safety_cushion",
                budgetId = budgetId,
                name = "Финансовая подушка",
                targetAmount = 300000.0,
                currentAmount = 165000.0
            ),
            GoalEntity(
                id = "${p}goal_altai_vacation",
                budgetId = budgetId,
                name = "Отпуск на Алтае",
                targetAmount = 180000.0,
                currentAmount = 72000.0
            )
        )
    }

    private fun buildRecurringPayments(budgetId: String): List<RecurringPaymentEntity> {
        val createdAt = isoToEpochMillis("2026-01-01", 9, 0)
        return listOf(
            // 1. Guaranteed Salary (10th of month, next: 2026-11-10)
            RecurringPaymentEntity(
                id = 101L,
                budgetId = budgetId,
                title = "Основная зарплата",
                amount = 85000.0,
                amountType = PaymentAmountType.FIXED.name,
                isIncome = true,
                incomeCertainty = IncomeCertainty.GUARANTEED.name,
                categoryName = "Зарплата",
                subcategoryName = "Основная работа",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 10,
                nextDueDateEpochDay = LocalDate.of(2026, 11, 10).toEpochDay(),
                priority = ObligationPriority.MANDATORY.name,
                autoConfirm = false,
                reminderDaysBefore = 1,
                status = RecurringStatus.ACTIVE.name,
                createdAtMillis = createdAt
            ),
            // 2. Guaranteed Advance (25th of month, next: 2026-11-25)
            RecurringPaymentEntity(
                id = 102L,
                budgetId = budgetId,
                title = "Аванс",
                amount = 45000.0,
                amountType = PaymentAmountType.FIXED.name,
                isIncome = true,
                incomeCertainty = IncomeCertainty.GUARANTEED.name,
                categoryName = "Зарплата",
                subcategoryName = "Аванс",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 25,
                nextDueDateEpochDay = LocalDate.of(2026, 11, 25).toEpochDay(),
                priority = ObligationPriority.MANDATORY.name,
                autoConfirm = false,
                reminderDaysBefore = 1,
                status = RecurringStatus.ACTIVE.name,
                createdAtMillis = createdAt
            ),
            // 3. Expected Consulting Bonus (22nd of month — tests day 22 verbalization! next: 2026-11-22)
            RecurringPaymentEntity(
                id = 103L,
                budgetId = budgetId,
                title = "Премия по проекту (консалтинг)",
                amount = 18500.0,
                amountType = PaymentAmountType.FIXED.name,
                isIncome = true,
                incomeCertainty = IncomeCertainty.EXPECTED.name,
                categoryName = "Подработка",
                subcategoryName = "Консалтинг",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 22,
                nextDueDateEpochDay = LocalDate.of(2026, 11, 22).toEpochDay(),
                priority = ObligationPriority.MANDATORY.name,
                autoConfirm = false,
                reminderDaysBefore = 2,
                status = RecurringStatus.ACTIVE.name,
                createdAtMillis = createdAt
            ),
            // 4. Mandatory Rent (5th of month, next: 2026-11-05 — before Nov 10 salary!)
            RecurringPaymentEntity(
                id = 201L,
                budgetId = budgetId,
                title = "Аренда квартиры",
                amount = 38000.0,
                amountType = PaymentAmountType.FIXED.name,
                isIncome = false,
                incomeCertainty = IncomeCertainty.GUARANTEED.name,
                categoryName = "ЖКХ и Аренда",
                subcategoryName = "Аренда квартиры",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 5,
                nextDueDateEpochDay = LocalDate.of(2026, 11, 5).toEpochDay(),
                priority = ObligationPriority.MANDATORY.name,
                autoConfirm = false,
                reminderDaysBefore = 2,
                status = RecurringStatus.ACTIVE.name,
                createdAtMillis = createdAt
            ),
            // 5. Mandatory Utilities / ЖКХ (12th of month — tests day 12 verbalization! next: 2026-11-12)
            RecurringPaymentEntity(
                id = 202L,
                budgetId = budgetId,
                title = "Коммунальные услуги (ЖКХ)",
                amount = 6380.0,
                estimatedMaxAmount = 6800.0,
                amountType = PaymentAmountType.ESTIMATED_UPPER_BOUND.name,
                isIncome = false,
                incomeCertainty = IncomeCertainty.GUARANTEED.name,
                categoryName = "ЖКХ и Аренда",
                subcategoryName = "Квитанция ЖКХ",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 12,
                nextDueDateEpochDay = LocalDate.of(2026, 11, 12).toEpochDay(),
                priority = ObligationPriority.MANDATORY.name,
                autoConfirm = false,
                reminderDaysBefore = 2,
                status = RecurringStatus.ACTIVE.name,
                createdAtMillis = createdAt
            ),
            // 6. Mandatory Internet & Telecom (2nd of month — paid today on 2026-11-02, next: 2026-12-02, tests day 2!)
            RecurringPaymentEntity(
                id = 203L,
                budgetId = budgetId,
                title = "Домашний интернет и мобильная связь",
                amount = 1390.0,
                amountType = PaymentAmountType.FIXED.name,
                isIncome = false,
                incomeCertainty = IncomeCertainty.GUARANTEED.name,
                categoryName = "Связь и Интернет",
                subcategoryName = "Тариф Семейный",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 2,
                nextDueDateEpochDay = LocalDate.of(2026, 12, 2).toEpochDay(),
                priority = ObligationPriority.MANDATORY.name,
                autoConfirm = true,
                reminderDaysBefore = 1,
                status = RecurringStatus.ACTIVE.name,
                createdAtMillis = createdAt
            ),
            // 7. Discretionary Subscription (18th of month, next: 2026-11-18)
            RecurringPaymentEntity(
                id = 204L,
                budgetId = budgetId,
                title = "Облако и Музыка (подписки)",
                amount = 698.0,
                amountType = PaymentAmountType.FIXED.name,
                isIncome = false,
                incomeCertainty = IncomeCertainty.GUARANTEED.name,
                categoryName = "Подписки и Сервисы",
                subcategoryName = "Яндекс Плюс и Облако",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 18,
                nextDueDateEpochDay = LocalDate.of(2026, 11, 18).toEpochDay(),
                priority = ObligationPriority.DISCRETIONARY_SUBSCRIPTION.name,
                autoConfirm = true,
                reminderDaysBefore = 1,
                status = RecurringStatus.ACTIVE.name,
                createdAtMillis = createdAt
            ),
            // 8. Paused rule (verifies pausedOrArchivedRulesCount == 1)
            RecurringPaymentEntity(
                id = 205L,
                budgetId = budgetId,
                title = "Абонемент в бассейн (на паузе)",
                amount = 3200.0,
                amountType = PaymentAmountType.FIXED.name,
                isIncome = false,
                incomeCertainty = IncomeCertainty.GUARANTEED.name,
                categoryName = "Развлечения и Хобби",
                subcategoryName = "Спорт",
                frequency = PaymentFrequency.MONTHLY.name,
                anchorDayOfMonth = 15,
                nextDueDateEpochDay = LocalDate.of(2026, 11, 15).toEpochDay(),
                priority = ObligationPriority.DISCRETIONARY_SUBSCRIPTION.name,
                autoConfirm = false,
                reminderDaysBefore = 2,
                status = RecurringStatus.PAUSED.name,
                createdAtMillis = createdAt
            )
        )
    }

    private fun buildRecurringExecutionLogs(budgetId: String): List<RecurringExecutionLogEntity> {
        val p = "${budgetId}_"
        val logs = mutableListOf<RecurringExecutionLogEntity>()
        var logId = 1000L

        // Logs for Jan..Oct 2026 for Salary (10th), Advance (25th), Rent (5th), Utilities (12th), Subscriptions (18th)
        for (month in 1..10) {
            val mStr = String.format(Locale.US, "%02d", month)
            val rentDay = LocalDate.of(2026, month, 5).toEpochDay()
            logs.add(
                RecurringExecutionLogEntity(
                    id = logId++,
                    budgetId = budgetId,
                    recurringId = 201L,
                    scheduledEpochDay = rentDay,
                    executedEpochDay = rentDay,
                    actualAmount = 38000.0,
                    status = ExecutionStatus.PAID.name,
                    linkedTransactionId = "${p}tx_2026_${mStr}_05_rent"
                )
            )
            val salDay = LocalDate.of(2026, month, 10).toEpochDay()
            logs.add(
                RecurringExecutionLogEntity(
                    id = logId++,
                    budgetId = budgetId,
                    recurringId = 101L,
                    scheduledEpochDay = salDay,
                    executedEpochDay = salDay,
                    actualAmount = 85000.0,
                    status = ExecutionStatus.PAID.name,
                    linkedTransactionId = "${p}tx_2026_${mStr}_10_salary"
                )
            )
            val utilDay = LocalDate.of(2026, month, 12).toEpochDay()
            val utilAmt = if (month in 1..3 || month == 10) 6380.0 else 5940.0
            logs.add(
                RecurringExecutionLogEntity(
                    id = logId++,
                    budgetId = budgetId,
                    recurringId = 202L,
                    scheduledEpochDay = utilDay,
                    executedEpochDay = utilDay,
                    actualAmount = utilAmt,
                    status = ExecutionStatus.PAID.name,
                    linkedTransactionId = "${p}tx_2026_${mStr}_12_jkh"
                )
            )
            val subDay = LocalDate.of(2026, month, 18).toEpochDay()
            logs.add(
                RecurringExecutionLogEntity(
                    id = logId++,
                    budgetId = budgetId,
                    recurringId = 204L,
                    scheduledEpochDay = subDay,
                    executedEpochDay = subDay,
                    actualAmount = 698.0,
                    status = ExecutionStatus.PAID.name,
                    linkedTransactionId = "${p}tx_2026_${mStr}_18_sub"
                )
            )
            val advDay = LocalDate.of(2026, month, 25).toEpochDay()
            logs.add(
                RecurringExecutionLogEntity(
                    id = logId++,
                    budgetId = budgetId,
                    recurringId = 102L,
                    scheduledEpochDay = advDay,
                    executedEpochDay = advDay,
                    actualAmount = 45000.0,
                    status = ExecutionStatus.PAID.name,
                    linkedTransactionId = "${p}tx_2026_${mStr}_25_advance"
                )
            )
        }

        // Logs for Internet & Mobile (2nd of month) for Jan..Nov 2026 (including 2026-11-02 paid today!)
        for (month in 1..11) {
            val mStr = String.format(Locale.US, "%02d", month)
            val netDay = LocalDate.of(2026, month, 2).toEpochDay()
            logs.add(
                RecurringExecutionLogEntity(
                    id = logId++,
                    budgetId = budgetId,
                    recurringId = 203L,
                    scheduledEpochDay = netDay,
                    executedEpochDay = netDay,
                    actualAmount = 1390.0,
                    status = ExecutionStatus.PAID.name,
                    linkedTransactionId = "${p}tx_2026_${mStr}_02_internet"
                )
            )
        }

        return logs
    }

    private fun buildTransactions(budgetId: String): List<TransactionEntity> {
        val p = "${budgetId}_"
        val list = mutableListOf<TransactionEntity>()

        /**
         * Note: In FinancialSemanticsCalculator, `isDebtTransaction(tx)` returns true whenever
         * `!tx.accountId.isNullOrBlank()`. Thus, regular transactions MUST have `accountId = null`,
         * and only debt repayment/return transactions set `accountId = debtAccountId`.
         */
        fun addTx(
            idSuffix: String,
            type: String,
            date: String,
            category: String,
            subcategory: String,
            amount: Double,
            accountId: String? = null,
            @Suppress("UNUSED_PARAMETER") note: String = "",
            hour: Int = 13
        ) {
            list.add(
                TransactionEntity(
                    id = "${p}$idSuffix",
                    budgetId = budgetId,
                    accountId = accountId,
                    type = type,
                    date = date,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    createdAt = isoToEpochMillis(date, hour, 15)
                )
            )
        }

        // =====================================================================
        // PART A: Historical Months Jan 2026 – Sep 2026 (01.01.2026 – 30.09.2026)
        // =====================================================================
        val monthlyGrocerySets = listOf(
            listOf(4250.0, 4890.0, 5120.0, 4460.0),
            listOf(4680.0, 4390.0, 5240.0, 4510.0),
            listOf(4920.0, 4470.0, 5310.0, 4630.0),
            listOf(4380.0, 4790.0, 5050.0, 4490.0),
            listOf(4610.0, 4850.0, 5190.0, 4720.0),
            listOf(4490.0, 4930.0, 5280.0, 4560.0),
            listOf(4750.0, 4620.0, 5140.0, 4680.0),
            listOf(4530.0, 4880.0, 5360.0, 4710.0),
            listOf(4690.0, 4910.0, 5210.0, 4640.0)
        )

        for (month in 1..9) {
            val mStr = String.format(Locale.US, "%02d", month)
            // Jan 1 holiday grocery / coffee on 2026-01-01
            if (month == 1) {
                addTx(
                    idSuffix = "tx_2026_01_01_newyear",
                    type = "expense",
                    date = "2026-01-01",
                    category = "Продукты",
                    subcategory = "Супермаркет",
                    amount = 2450.0,
                    note = "Новогодний стол и фрукты"
                )
            }

            // 2nd: Internet & Mobile
            addTx(
                idSuffix = "tx_2026_${mStr}_02_internet",
                type = "expense",
                date = "2026-$mStr-02",
                category = "Связь и Интернет",
                subcategory = "Тариф Семейный",
                amount = 1390.0,
                note = "Домашний интернет и связь"
            )
            // 5th: Rent
            addTx(
                idSuffix = "tx_2026_${mStr}_05_rent",
                type = "expense",
                date = "2026-$mStr-05",
                category = "ЖКХ и Аренда",
                subcategory = "Аренда квартиры",
                amount = 38000.0,
                note = "Оплата аренды квартиры"
            )
            // 10th: Salary
            addTx(
                idSuffix = "tx_2026_${mStr}_10_salary",
                type = "income",
                date = "2026-$mStr-10",
                category = "Зарплата",
                subcategory = "Основная работа",
                amount = 85000.0,
                note = "Основная часть зарплаты"
            )
            // 12th: Utilities (ЖКХ)
            val utilAmt = if (month in 1..3) 6380.0 else 5940.0
            addTx(
                idSuffix = "tx_2026_${mStr}_12_jkh",
                type = "expense",
                date = "2026-$mStr-12",
                category = "ЖКХ и Аренда",
                subcategory = "Квитанция ЖКХ",
                amount = utilAmt,
                note = "Коммунальные услуги по счётчикам"
            )
            // 18th: Subscriptions
            addTx(
                idSuffix = "tx_2026_${mStr}_18_sub",
                type = "expense",
                date = "2026-$mStr-18",
                category = "Подписки и Сервисы",
                subcategory = "Яндекс Плюс и Облако",
                amount = 698.0,
                note = "Ежемесячные подписки"
            )
            // 25th: Advance
            addTx(
                idSuffix = "tx_2026_${mStr}_25_advance",
                type = "income",
                date = "2026-$mStr-25",
                category = "Зарплата",
                subcategory = "Аванс",
                amount = 45000.0,
                note = "Аванс за месяц"
            )

            // Weekly groceries (4 times per month)
            val gSet = monthlyGrocerySets[month - 1]
            val gDays = listOf("03", "09", "16", "24")
            for (i in gDays.indices) {
                addTx(
                    idSuffix = "tx_2026_${mStr}_${gDays[i]}_groceries",
                    type = "expense",
                    date = "2026-$mStr-${gDays[i]}",
                    category = "Продукты",
                    subcategory = if (i % 2 == 0) "Гипермаркет" else "Супермаркет",
                    amount = gSet[i],
                    note = "Закупка продуктов на неделю"
                )
            }

            // Transport & Pharmacy & Cafes each month
            addTx(
                idSuffix = "tx_2026_${mStr}_07_transport",
                type = "expense",
                date = "2026-$mStr-07",
                category = "Транспорт",
                subcategory = "Метро и Тройка",
                amount = 2850.0,
                note = "Проездной и поездки по городу"
            )
            addTx(
                idSuffix = "tx_2026_${mStr}_14_pharmacy",
                type = "expense",
                date = "2026-$mStr-14",
                category = "Здоровье и Аптека",
                subcategory = "Лекарства",
                amount = 1950.0,
                note = "Домашняя аптечка и витамины"
            )
            addTx(
                idSuffix = "tx_2026_${mStr}_21_cafe",
                type = "expense",
                date = "2026-$mStr-21",
                category = "Кафе и Доставка",
                subcategory = "Рестораны",
                amount = 4800.0,
                note = "Ужин и доставка на выходных"
            )
        }

        // Irregular major expenses & extra incomes in Jan–Sep 2026:
        addTx("tx_2026_02_19_freelance", "income", "2026-02-19", "Подработка", "Консалтинг", 16500.0, note = "Проект по автоматизации таблиц")
        addTx("tx_2026_03_22_appliance", "expense", "2026-03-22", "Одежда и Обувь", "Повседневная одежда", 7800.0, note = "Весенняя куртка и кроссовки")
        addTx("tx_2026_05_15_freelance", "income", "2026-05-15", "Подработка", "Аудит отчётности", 22000.0, note = "Квартальный консалтинг")
        addTx("tx_2026_06_11_dental", "expense", "2026-06-11", "Здоровье и Аптека", "Стоматология", 14500.0, note = "Профгигиена и пломба")
        addTx("tx_2026_08_20_freelance", "income", "2026-08-20", "Подработка", "Консалтинг", 19000.0, note = "Аналитический отчёт")
        addTx("tx_2026_09_15_autumn_coat", "expense", "2026-09-15", "Одежда и Обувь", "Повседневная одежда", 11200.0, note = "Осеннее пальто")
        // September debt operations (prior 30d window: 2026-09-03 .. 2026-10-02):
        addTx(
            idSuffix = "tx_2026_09_20_debt_installment_1",
            type = "expense",
            date = "2026-09-20",
            category = "Погашение долга",
            subcategory = "Рассрочка",
            amount = 12500.0,
            accountId = "${p}debt_we_owe_installment",
            note = "Первый взнос по рассрочке за ноутбук"
        )
        addTx(
            idSuffix = "tx_2026_09_28_debt_dmitry_1",
            type = "income",
            date = "2026-09-28",
            category = "Возврат долга",
            subcategory = "Дмитрий (сосед)",
            amount = 2500.0,
            accountId = "${p}debt_owes_us_dmitry",
            note = "Частичный возврат долга от соседа Дмитрия"
        )
        // Oct 2 Internet payment (just before the 30d window [2026-10-03 .. 2026-11-02]):
        addTx(
            idSuffix = "tx_2026_10_02_internet",
            type = "expense",
            date = "2026-10-02",
            category = "Связь и Интернет",
            subcategory = "Тариф Семейный",
            amount = 1390.0,
            note = "Домашний интернет и связь за октябрь"
        )

        // =====================================================================
        // PART B: Active 30-Day Window (03.10.2026 – 02.11.2026 inclusive)
        // Exact verifiable totals:
        // - Earned Income 30d: 85,000 + 14,500 + 45,000 = 144,500 ₽ (Salary: 130,000 ₽, Freelance: 14,500 ₽)
        // - Debt Returned 30d: 5,000 ₽ (Alexey on 2026-10-15, excluded from earnedIncome30dRub)
        // - Regular Expense 30d: 97,828 ₽
        // - Debt Repayment 30d: 18,500 ₽ (6,000 ₽ Brother + 12,500 ₽ Installment)
        // - Total Expense 30d: 116,328 ₽
        // =====================================================================

        // 1. Incomes in 30d window:
        addTx("tx_2026_10_10_salary", "income", "2026-10-10", "Зарплата", "Основная работа", 85000.0, note = "Основная зарплата за октябрь")
        addTx("tx_2026_10_15_debt_alexey", "income", "2026-10-15", "Возврат долга", "Алексей (коллега)", 5000.0, accountId = "${p}debt_owes_us_alexey", note = "Частичный возврат долга от Алексея")
        addTx("tx_2026_10_19_freelance", "income", "2026-10-19", "Подработка", "Аудит отчётности", 14500.0, note = "Разовый аудит управленческой отчётности")
        addTx("tx_2026_10_25_advance", "income", "2026-10-25", "Зарплата", "Аванс", 45000.0, note = "Аванс за октябрь")

        // 2. ЖКХ и Аренда in 30d window (44,380 ₽ / limit 46,000 ₽):
        addTx("tx_2026_10_05_rent", "expense", "2026-10-05", "ЖКХ и Аренда", "Аренда квартиры", 38000.0, note = "Аренда квартиры за октябрь")
        addTx("tx_2026_10_12_jkh", "expense", "2026-10-12", "ЖКХ и Аренда", "Квитанция ЖКХ", 6380.0, note = "Квитанция ЖКХ за октябрь")

        // 3. Продукты in 30d window (19,840 ₽ / limit 28,000 ₽):
        addTx("tx_2026_10_04_groc1", "expense", "2026-10-04", "Продукты", "Гипермаркет", 4620.0, note = "Гипермаркет Лента (закупка на неделю)")
        addTx("tx_2026_10_07_groc2", "expense", "2026-10-07", "Продукты", "Супермаркет", 1180.0, note = "ВкусВилл (молоко, овощи, хлеб)")
        addTx("tx_2026_10_11_groc3", "expense", "2026-10-11", "Продукты", "Супермаркет", 4390.0, note = "Перекрёсток (продукты на неделю)")
        addTx("tx_2026_10_16_groc4", "expense", "2026-10-16", "Продукты", "Супермаркет", 1450.0, note = "Пятёрочка (фрукты, сыр, кофе)")
        addTx("tx_2026_10_18_groc5", "expense", "2026-10-18", "Продукты", "Гипермаркет", 4850.0, note = "Гипермаркет Окей (мясо, рыба, бакалея)")
        addTx("tx_2026_10_26_groc6", "expense", "2026-10-26", "Продукты", "Супермаркет", 1620.0, note = "ВкусВилл (фермерские продукты)")
        addTx("tx_2026_11_01_groc7", "expense", "2026-11-01", "Продукты", "Супермаркет", 1250.0, note = "Перекрёсток (продукты на выходные)", hour = 14)
        addTx("tx_2026_11_02_groc8", "expense", "2026-11-02", "Продукты", "Пекарня и молоко", 480.0, note = "Пекарня и фермерское молоко у дома", hour = 9)

        // 4. Транспорт in 30d window (3,920 ₽ / limit 6,000 ₽):
        addTx("tx_2026_10_06_tr1", "expense", "2026-10-06", "Транспорт", "Метро и Тройка", 1500.0, note = "Пополнение карты Тройка")
        addTx("tx_2026_10_14_tr2", "expense", "2026-10-14", "Транспорт", "Такси", 890.0, note = "Яндекс Такси (поездка с вокзала)")
        addTx("tx_2026_10_22_tr3", "expense", "2026-10-22", "Транспорт", "Метро и Тройка", 1000.0, note = "Пополнение карты Тройка")
        addTx("tx_2026_10_29_tr4", "expense", "2026-10-29", "Транспорт", "Каршеринг", 370.0, note = "Каршеринг Ситидрайв")
        addTx("tx_2026_11_02_tr5", "expense", "2026-11-02", "Транспорт", "Метро и Тройка", 160.0, note = "Метро и МЦД утром", hour = 10)

        // 5. Здоровье и Аптека in 30d window (2,860 ₽ / limit 5,000 ₽):
        addTx("tx_2026_10_09_med1", "expense", "2026-10-09", "Здоровье и Аптека", "Витамины", 1680.0, note = "Аптека Ригла (витамины D3 и Омега-3)")
        addTx("tx_2026_10_24_med2", "expense", "2026-10-24", "Здоровье и Аптека", "Лекарства", 1180.0, note = "Аптека Горздрав (сезонные средства)")

        // 6. Связь и Интернет in 30d window (1,390 ₽ / limit 2,500 ₽ — executed today 2026-11-02):
        addTx("tx_2026_11_02_internet", "expense", "2026-11-02", "Связь и Интернет", "Тариф Семейный", 1390.0, note = "Абонентская плата за домашний интернет и связь (2 ноября)", hour = 11)

        // 7. Подписки и Сервисы in 30d window (698 ₽ / limit 1,500 ₽):
        addTx("tx_2026_10_18_sub", "expense", "2026-10-18", "Подписки и Сервисы", "Яндекс Плюс и Облако", 698.0, note = "Подписка Яндекс Плюс и облачное хранилище")

        // 8. Кафе и Доставка in 30d window (9,850 ₽ / limit 8,000 ₽ — EXCEEDED by 1,850 ₽ [123%]!):
        addTx("tx_2026_10_08_cafe1", "expense", "2026-10-08", "Кафе и Доставка", "Кофейня", 1450.0, note = "Surf Coffee и сэндвич")
        addTx("tx_2026_10_13_cafe2", "expense", "2026-10-13", "Кафе и Доставка", "Доставка еды", 3200.0, note = "Яндекс Еда (ужин с друзьями)")
        addTx("tx_2026_10_21_cafe3", "expense", "2026-10-21", "Кафе и Доставка", "Доставка еды", 2350.0, note = "Пиццерия Папа Джонс")
        addTx("tx_2026_10_27_cafe4", "expense", "2026-10-27", "Кафе и Доставка", "Рестораны", 1900.0, note = "Бизнес-ланч и десерт")
        addTx("tx_2026_11_01_cafe5", "expense", "2026-11-01", "Кафе и Доставка", "Рестораны", 600.0, note = "Воскресный бранч в кафе", hour = 16)
        addTx("tx_2026_11_02_cafe6", "expense", "2026-11-02", "Кафе и Доставка", "Кофейня", 350.0, note = "Утренний капучино по дороге в офис", hour = 12)

        // 9. Развлечения и Хобби in 30d window (8,400 ₽ / limit 7,000 ₽ — EXCEEDED by 1,400 ₽ [120%]!):
        addTx("tx_2026_10_17_ent1", "expense", "2026-10-17", "Развлечения и Хобби", "Театр и Концерты", 5400.0, note = "Билеты в театр на двоих")
        addTx("tx_2026_10_30_ent2", "expense", "2026-10-30", "Развлечения и Хобби", "Кино и Выставки", 3000.0, note = "Выставка и концерт")

        // 10. Одежда и Обувь in 30d window (6,490 ₽ / limit 10,000 ₽):
        addTx("tx_2026_10_23_cloth1", "expense", "2026-10-23", "Одежда и Обувь", "Повседневная одежда", 6490.0, note = "Шерстяной свитер и перчатки")

        // 11. Debt repayments in 30d window (18,500 ₽ total):
        addTx(
            idSuffix = "tx_2026_10_08_debt_brother",
            type = "expense",
            date = "2026-10-08",
            category = "Погашение долга",
            subcategory = "Личный долг",
            amount = 6000.0,
            accountId = "${p}debt_we_owe_brother",
            note = "Частичный возврат долга брату Андрею"
        )
        addTx(
            idSuffix = "tx_2026_10_20_debt_installment_2",
            type = "expense",
            date = "2026-10-20",
            category = "Погашение долга",
            subcategory = "Рассрочка",
            amount = 12500.0,
            accountId = "${p}debt_we_owe_installment",
            note = "Второй взнос по рассрочке за ноутбук"
        )

        return list
    }
}
