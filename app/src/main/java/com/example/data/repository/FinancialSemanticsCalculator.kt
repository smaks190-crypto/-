package com.example.data.repository

import com.example.data.db.AccountEntity
import com.example.data.db.TransactionEntity
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Детализированный срез одного долгового обязательства или дебиторской задолженности.
 */
data class DebtItemSnapshot(
    val id: String,
    val name: String,
    val isWeOwe: Boolean,
    val initialAmount: Double,
    val repaidAmount: Double,
    val increasedAmount: Double,
    val remainingAmount: Double
)

/**
 * Строго разделённый финансовый срез пользователя (Issue #43).
 *
 * Гарантирует отсутствие смешивания:
 * 1. Текущего доступного баланса ([availableBalanceRub]) — фактического остатка средств;
 * 2. Заработанного дохода за период ([earnedIncomeRub], включая зарплату [salaryIncomeRub]);
 * 3. Начальных/корректирующих записей баланса ([balanceAdjustmentRub]);
 * 4. Обязательств пользователя «Я должен» ([totalWeOweRub], [weOweDebts]);
 * 5. Дебиторской задолженности «Мне должны» ([totalOwesUsRub], [owesUsDebts]);
 * 6. Операций погашения/возврата долгов ([debtRepaymentExpenseRub], [debtReturnedIncomeRub]).
 */
data class FinancialSemanticsSnapshot(
    val availableBalanceRub: Double,
    val earnedIncomeRub: Double,
    val salaryIncomeRub: Double,
    val otherEarnedIncomeRub: Double,
    val balanceAdjustmentRub: Double,
    val debtReturnedIncomeRub: Double,
    val regularExpenseRub: Double,
    val debtRepaymentExpenseRub: Double,
    val totalExpenseRub: Double,
    val netRegularCashFlowRub: Double,
    val totalWeOweRub: Double,
    val totalOwesUsRub: Double,
    val weOweDebts: List<DebtItemSnapshot>,
    val owesUsDebts: List<DebtItemSnapshot>
)

/**
 * Распознанные из текста диалога финансовые сущности (раздельно по смыслу).
 */
data class ConversationalFinancialMention(
    val salaryAmount: Double? = null,
    val otherIncomeAmount: Double? = null,
    val currentBalanceAmount: Double? = null,
    val weOweAmount: Double? = null,
    val owesUsAmount: Double? = null,
    val debtRepaidByUsAmount: Double? = null,
    val debtReturnedToUsAmount: Double? = null
) {
    fun hasAnyMention(): Boolean {
        return salaryAmount != null ||
            otherIncomeAmount != null ||
            currentBalanceAmount != null ||
            weOweAmount != null ||
            owesUsAmount != null ||
            debtRepaidByUsAmount != null ||
            debtReturnedToUsAmount != null
    }
}

object FinancialSemanticsCalculator {

    private val DEBT_ACCOUNT_TYPES = setOf("we_owe", "owes_us", "debt")

    /**
     * Проверяет, является ли запись в таблице `accounts` долгом («Я должен» или «Мне должны»).
     * В David Finance таблица `accounts` используется экраном `DebtsScreen` для хранения долгов.
     */
    fun isDebtAccount(account: AccountEntity): Boolean {
        val normalized = account.type.trim().lowercase(Locale.ROOT)
        return normalized in DEBT_ACCOUNT_TYPES
    }

    /**
     * Проверяет, относится ли запись к обязательствам пользователя («Я должен»).
     */
    fun isWeOweDebt(account: AccountEntity): Boolean {
        if (!isDebtAccount(account)) return false
        return !account.type.trim().equals("owes_us", ignoreCase = true)
    }

    /**
     * Проверяет, относится ли запись к дебиторской задолженности («Мне должны»).
     */
    fun isOwesUsDebt(account: AccountEntity): Boolean {
        return account.type.trim().equals("owes_us", ignoreCase = true)
    }

    /**
     * Проверяет, является ли транзакция операцией по долгу (погашение, возврат или выдача/получение займа).
     */
    fun isDebtTransaction(tx: TransactionEntity): Boolean {
        if (!tx.accountId.isNullOrBlank()) return true
        val cat = tx.category.trim().lowercase(Locale.ROOT)
        val sub = tx.subcategory.trim().lowercase(Locale.ROOT)
        if (cat == "долги" || cat == "долги/кредиты" || cat == "кредиты и долги") return true
        return sub.startsWith("выплата по долгу") ||
            sub.startsWith("погашение долга") ||
            sub.startsWith("возврат долга") ||
            sub.startsWith("взял долг") ||
            sub.startsWith("взял в долг") ||
            sub.startsWith("дал в долг") ||
            sub.startsWith("дал долг") ||
            sub.startsWith("я должен") ||
            sub.startsWith("мне должны")
    }

    /**
     * Проверяет, является ли транзакция фиксацией текущего/начального остатка (баланса),
     * а не новым заработанным доходом.
     */
    fun isBalanceAdjustmentTransaction(tx: TransactionEntity): Boolean {
        if (isDebtTransaction(tx)) return false
        val cat = tx.category.trim().lowercase(Locale.ROOT)
        val sub = tx.subcategory.trim().lowercase(Locale.ROOT)
        return cat == "баланс" ||
            cat == "текущий баланс" ||
            cat == "начальный баланс" ||
            cat == "остаток" ||
            cat == "корректировка баланса" ||
            sub == "баланс" ||
            sub == "текущий баланс" ||
            sub == "начальный баланс" ||
            sub.contains("текущий баланс") ||
            sub.contains("начальный баланс") ||
            sub.contains("начальный остаток") ||
            sub.contains("остаток на счете") ||
            sub.contains("остаток на карте")
    }

    /**
     * Проверяет, является ли доходная транзакция зарплатой/авансом/окладом.
     */
    fun isSalaryTransaction(tx: TransactionEntity): Boolean {
        if (!tx.type.equals("income", ignoreCase = true)) return false
        if (isDebtTransaction(tx) || isBalanceAdjustmentTransaction(tx)) return false
        val cat = tx.category.trim().lowercase(Locale.ROOT)
        val sub = tx.subcategory.trim().lowercase(Locale.ROOT)
        return cat.contains("зарплат") ||
            cat.contains("аванс") ||
            cat.contains("оклад") ||
            sub.contains("зарплат") ||
            sub.contains("аванс") ||
            sub.contains("оклад") ||
            sub.contains("получк")
    }

    /**
     * Вычисляет текущий остаток по конкретному долгу с учётом связанных транзакций.
     *
     * Семантика совпадает с `DebtsScreen` и `MainActivity`:
     * - Для «Я должен» (`isWeOwe = true`): `expense` уменьшает долг (выплата), `income` увеличивает долг.
     * - Для «Мне должны» (`isWeOwe = false`): `income` уменьшает долг (возврат), `expense` увеличивает долг.
     */
    fun calculateRemainingDebt(
        debt: AccountEntity,
        allTransactions: List<TransactionEntity>
    ): DebtItemSnapshot {
        val linkedTxs = allTransactions.filter { it.accountId == debt.id }
        val txIncome = linkedTxs
            .filter { it.type.equals("income", ignoreCase = true) }
            .sumOf { it.amount }
        val txExpense = linkedTxs
            .filter { it.type.equals("expense", ignoreCase = true) }
            .sumOf { it.amount }

        val isWeOwe = isWeOweDebt(debt)
        val initial = debt.balance.coerceAtLeast(0.0)
        return if (isWeOwe) {
            val remaining = (initial + txIncome - txExpense).coerceAtLeast(0.0)
            DebtItemSnapshot(
                id = debt.id,
                name = debt.name,
                isWeOwe = true,
                initialAmount = initial,
                repaidAmount = txExpense,
                increasedAmount = txIncome,
                remainingAmount = remaining
            )
        } else {
            val remaining = (initial + txExpense - txIncome).coerceAtLeast(0.0)
            DebtItemSnapshot(
                id = debt.id,
                name = debt.name,
                isWeOwe = false,
                initialAmount = initial,
                repaidAmount = txIncome,
                increasedAmount = txExpense,
                remainingAmount = remaining
            )
        }
    }

    /**
     * Формирует полный, детерминированный и семантически разделённый финансовый срез.
     *
     * Ключевые инварианты:
     * 1. Записи долгов (`AccountEntity` с `type` in `we_owe`, `owes_us`, `debt`) НИКОГДА не суммируются
     *    в `availableBalanceRub` и НИКОГДА не включаются в `earnedIncomeRub`.
     * 2. Зарплата (`salaryIncomeRub`) входит в `earnedIncomeRub` один раз, а `availableBalanceRub`
     *    отражает чистый остаток средств (`allIncomes - allExpenses`) и не складывается повторно с зарплатой.
     * 3. Начальный/текущий баланс, занесённый отдельной корректирующей транзакцией (`isBalanceAdjustmentTransaction`),
     *    учитывается в `availableBalanceRub`, но НЕ считается заработанным доходом (`earnedIncomeRub`).
     * 4. Возврат долга мне (`debtReturnedIncomeRub`) увеличивает доступный остаток денег (`availableBalanceRub`)
     *    и уменьшает остаток дебиторской задолженности (`totalOwesUsRub`), но НЕ завышает `earnedIncomeRub`.
     */
    fun calculateSnapshot(
        allTransactions: List<TransactionEntity>,
        periodTransactions: List<TransactionEntity> = allTransactions,
        accounts: List<AccountEntity> = emptyList(),
        fallbackBalance: Double = 0.0
    ): FinancialSemanticsSnapshot {
        val debtAccounts = accounts.filter { isDebtAccount(it) }
        val nonDebtAccounts = accounts.filter { !isDebtAccount(it) }

        val debtSnapshots = debtAccounts.map { calculateRemainingDebt(it, allTransactions) }
        val weOweDebts = debtSnapshots.filter { it.isWeOwe }
        val owesUsDebts = debtSnapshots.filter { !it.isWeOwe }

        val totalWeOweRub = weOweDebts.sumOf { it.remainingAmount }
        val totalOwesUsRub = owesUsDebts.sumOf { it.remainingAmount }

        // Доступный остаток средств (текущий баланс):
        // Рассчитывается строго по реальным денежным потокам транзакций.
        // Если транзакций нет, используются только недолговые счета (card/cash) или fallbackBalance.
        val availableBalanceRub = if (allTransactions.isNotEmpty()) {
            val allIncomes = allTransactions
                .filter { it.type.equals("income", ignoreCase = true) }
                .sumOf { it.amount }
            val allExpenses = allTransactions
                .filter { it.type.equals("expense", ignoreCase = true) }
                .sumOf { it.amount }
            allIncomes - allExpenses
        } else if (nonDebtAccounts.isNotEmpty()) {
            nonDebtAccounts.sumOf { it.balance }
        } else {
            fallbackBalance
        }

        // Разделение доходных операций периода по смыслу
        val periodIncomes = periodTransactions.filter { it.type.equals("income", ignoreCase = true) }
        val debtReturnedIncomeRub = periodIncomes
            .filter { isDebtTransaction(it) }
            .sumOf { it.amount }
        val balanceAdjustmentRub = periodIncomes
            .filter { isBalanceAdjustmentTransaction(it) }
            .sumOf { it.amount }
        val earnedIncomeTxs = periodIncomes.filter {
            !isDebtTransaction(it) && !isBalanceAdjustmentTransaction(it)
        }
        val earnedIncomeRub = earnedIncomeTxs.sumOf { it.amount }
        val salaryIncomeRub = earnedIncomeTxs
            .filter { isSalaryTransaction(it) }
            .sumOf { it.amount }
        val otherEarnedIncomeRub = (earnedIncomeRub - salaryIncomeRub).coerceAtLeast(0.0)

        // Разделение расходных операций периода по смыслу
        val periodExpenses = periodTransactions.filter { it.type.equals("expense", ignoreCase = true) }
        val debtRepaymentExpenseRub = periodExpenses
            .filter { isDebtTransaction(it) }
            .sumOf { it.amount }
        val regularExpenseRub = periodExpenses
            .filter { !isDebtTransaction(it) }
            .sumOf { it.amount }
        val totalExpenseRub = regularExpenseRub + debtRepaymentExpenseRub

        val netRegularCashFlowRub = earnedIncomeRub - regularExpenseRub

        return FinancialSemanticsSnapshot(
            availableBalanceRub = availableBalanceRub,
            earnedIncomeRub = earnedIncomeRub,
            salaryIncomeRub = salaryIncomeRub,
            otherEarnedIncomeRub = otherEarnedIncomeRub,
            balanceAdjustmentRub = balanceAdjustmentRub,
            debtReturnedIncomeRub = debtReturnedIncomeRub,
            regularExpenseRub = regularExpenseRub,
            debtRepaymentExpenseRub = debtRepaymentExpenseRub,
            totalExpenseRub = totalExpenseRub,
            netRegularCashFlowRub = netRegularCashFlowRub,
            totalWeOweRub = totalWeOweRub,
            totalOwesUsRub = totalOwesUsRub,
            weOweDebts = weOweDebts,
            owesUsDebts = owesUsDebts
        )
    }

    /**
     * Извлекает из текста сообщения пользователя раздельные упоминания:
     * - зарплаты / заработка;
     * - текущего баланса / остатка;
     * - долгов пользователя («я должен», «мой долг»);
     * - долгов перед пользователем («мне должны», «должны мне», «дал в долг»);
     * - погашения или возврата долга.
     */
    fun extractConversationalFinancialMention(text: String): ConversationalFinancialMention {
        val normalized = text.lowercase(Locale.ROOT).trim()
        if (normalized.isEmpty()) return ConversationalFinancialMention()

        val clauses = normalized
            .split(Regex("(?:[,;\\n]+|\\s+и\\s+|\\s+а\\s+также\\s+|\\s+плюс\\s+|\\s+ещ[её]\\s+)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var salary: Double? = null
        var otherIncome: Double? = null
        var currentBalance: Double? = null
        var weOwe: Double? = null
        var owesUs: Double? = null
        var debtRepaidByUs: Double? = null
        var debtReturnedToUs: Double? = null

        val amountRegex = Regex("(\\d+(?:[\\s\\u00A0]\\d{3})*(?:[.,]\\d+)?)\\s*(?:тыс(?:яч[аи]?)?|к|k|р(?:уб(?:л(?:ей|я|ь)?)?)?|₽)?")

        for (clause in clauses) {
            val match = amountRegex.find(clause) ?: continue
            val rawNum = match.groupValues[1].replace(Regex("[\\s\\u00A0]"), "").replace(',', '.')
            var value = rawNum.toDoubleOrNull() ?: continue
            val fullMatchText = match.value.lowercase(Locale.ROOT)
            if (fullMatchText.contains("тыс") || fullMatchText.endsWith("к") || fullMatchText.endsWith("k")) {
                if (value < 1000.0) value *= 1000.0
            }
            if (value <= 0.0) continue

            when {
                clause.contains("мне вернул") || clause.contains("вернули долг") || clause.contains("отдали долг") -> {
                    debtReturnedToUs = (debtReturnedToUs ?: 0.0) + value
                }
                clause.contains("погасил долг") || clause.contains("выплатил долг") || clause.contains("отдал долг") || clause.contains("вернул долг") -> {
                    debtRepaidByUs = (debtRepaidByUs ?: 0.0) + value
                }
                clause.contains("мне должн") || clause.contains("должны мне") || clause.contains("должен мне") ||
                    clause.contains("должна мне") || clause.contains("дал в долг") || clause.contains("одолжил ") -> {
                    owesUs = (owesUs ?: 0.0) + value
                }
                clause.contains("я долж") || clause.contains("мой долг") || clause.contains("мои долги") ||
                    clause.contains("взял в долг") || clause.contains("торчу ") || clause.contains("кредит ") -> {
                    weOwe = (weOwe ?: 0.0) + value
                }
                clause.contains("баланс") || clause.contains("остаток") || clause.contains("на счете") ||
                    clause.contains("на счету") || clause.contains("на карте") || clause.contains("в кошельке") -> {
                    currentBalance = value
                }
                clause.contains("зарплат") || clause.contains("зп ") || clause.startsWith("зп") ||
                    clause.contains("аванс") || clause.contains("оклад") || clause.contains("получк") -> {
                    salary = (salary ?: 0.0) + value
                }
                clause.contains("доход") || clause.contains("заработ") || clause.contains("фриланс") || clause.contains("подработ") -> {
                    otherIncome = (otherIncome ?: 0.0) + value
                }
            }
        }

        return ConversationalFinancialMention(
            salaryAmount = salary,
            otherIncomeAmount = otherIncome,
            currentBalanceAmount = currentBalance,
            weOweAmount = weOwe,
            owesUsAmount = owesUs,
            debtRepaidByUsAmount = debtRepaidByUs,
            debtReturnedToUsAmount = debtReturnedToUs
        )
    }

    /**
     * Объединяет срез из БД с устными/текстовыми уточнениями пользователя из текущей сессии чата,
     * сохраняя строгое разделение сущностей и исключая их суммирование в один «общий доход».
     */
    fun mergeSnapshotWithConversationalMentions(
        baseSnapshot: FinancialSemanticsSnapshot,
        userMessages: List<String>
    ): FinancialSemanticsSnapshot {
        var result = baseSnapshot
        for (msg in userMessages) {
            val mention = extractConversationalFinancialMention(msg)
            if (!mention.hasAnyMention()) continue

            val newSalary = mention.salaryAmount ?: 0.0
            val newOtherIncome = mention.otherIncomeAmount ?: 0.0
            val newWeOwe = mention.weOweAmount ?: 0.0
            val newOwesUs = mention.owesUsAmount ?: 0.0
            val repaidByUs = mention.debtRepaidByUsAmount ?: 0.0
            val returnedToUs = mention.debtReturnedToUsAmount ?: 0.0

            val updatedSalary = if (newSalary > 0.0 && result.salaryIncomeRub == 0.0) {
                newSalary
            } else {
                result.salaryIncomeRub
            }
            val updatedOtherIncome = if (newOtherIncome > 0.0 && result.otherEarnedIncomeRub == 0.0) {
                newOtherIncome
            } else {
                result.otherEarnedIncomeRub
            }
            val updatedEarnedIncome = updatedSalary + updatedOtherIncome

            val baseBalance = mention.currentBalanceAmount ?: result.availableBalanceRub
            val updatedAvailableBalance = baseBalance - repaidByUs + returnedToUs

            val updatedWeOwe = ((if (newWeOwe > 0.0 && result.totalWeOweRub == 0.0) newWeOwe else result.totalWeOweRub) - repaidByUs)
                .coerceAtLeast(0.0)
            val updatedOwesUs = ((if (newOwesUs > 0.0 && result.totalOwesUsRub == 0.0) newOwesUs else result.totalOwesUsRub) - returnedToUs)
                .coerceAtLeast(0.0)

            result = result.copy(
                availableBalanceRub = updatedAvailableBalance,
                earnedIncomeRub = updatedEarnedIncome,
                salaryIncomeRub = updatedSalary,
                otherEarnedIncomeRub = updatedOtherIncome,
                debtRepaymentExpenseRub = result.debtRepaymentExpenseRub + repaidByUs,
                debtReturnedIncomeRub = result.debtReturnedIncomeRub + returnedToUs,
                totalExpenseRub = result.regularExpenseRub + result.debtRepaymentExpenseRub + repaidByUs,
                totalWeOweRub = updatedWeOwe,
                totalOwesUsRub = updatedOwesUs
            )
        }
        return result
    }

    /**
     * Формирует текстовый блок финансового контекста для системного/пользовательского промпта Gemini
     * с жёстким разграничением баланса, дохода (зарплаты) и взаимных долгов.
     */
    fun formatPromptFinancialContext(snapshot: FinancialSemanticsSnapshot): String {
        val weOweDetails = if (snapshot.weOweDebts.isNotEmpty()) {
            snapshot.weOweDebts.joinToString("; ") {
                "'${it.name}': осталось ${it.remainingAmount.roundToLong()} ₽ (изначально ${it.initialAmount.roundToLong()} ₽, выплачено ${it.repaidAmount.roundToLong()} ₽)"
            }
        } else if (snapshot.totalWeOweRub > 0.0) {
            "остаток обязательств ${snapshot.totalWeOweRub.roundToLong()} ₽"
        } else {
            "нет активных обязательств (0 ₽)"
        }

        val owesUsDetails = if (snapshot.owesUsDebts.isNotEmpty()) {
            snapshot.owesUsDebts.joinToString("; ") {
                "'${it.name}': должны вернуть ${it.remainingAmount.roundToLong()} ₽ (изначально ${it.initialAmount.roundToLong()} ₽, возвращено ${it.repaidAmount.roundToLong()} ₽)"
            }
        } else if (snapshot.totalOwesUsRub > 0.0) {
            "ожидается к возврату ${snapshot.totalOwesUsRub.roundToLong()} ₽"
        } else {
            "никто не должен (0 ₽)"
        }

        return buildString {
            appendLine("=== СТРОГО РАЗДЕЛЁННЫЕ ФИНАНСОВЫЕ ПОКАЗАТЕЛИ ПОЛЬЗОВАТЕЛЯ ===")
            appendLine("1. ДОСТУПНЫЙ ОСТАТОК (Текущий баланс на руках): ${snapshot.availableBalanceRub.roundToLong()} ₽")
            appendLine("   (Это фактический остаток денег с учётом уже полученных доходов и совершённых расходов. НЕ складывай его с зарплатой!)")
            appendLine("2. ЗАРАБОТАННЫЙ ДОХОД ЗА ПЕРИОД: ${snapshot.earnedIncomeRub.roundToLong()} ₽ (в т.ч. Зарплата: ${snapshot.salaryIncomeRub.roundToLong()} ₽, прочий заработок: ${snapshot.otherEarnedIncomeRub.roundToLong()} ₽)")
            if (snapshot.balanceAdjustmentRub > 0.0) {
                appendLine("   Начальный/скорректированный остаток баланса (НЕ является доходом): ${snapshot.balanceAdjustmentRub.roundToLong()} ₽")
            }
            if (snapshot.debtReturnedIncomeRub > 0.0) {
                appendLine("   Возвраты долгов от должников за период (НЕ являются новым заработанным доходом): ${snapshot.debtReturnedIncomeRub.roundToLong()} ₽")
            }
            appendLine("3. РАСХОДЫ ЗА ПЕРИОД: всего ${snapshot.totalExpenseRub.roundToLong()} ₽ (обычные траты: ${snapshot.regularExpenseRub.roundToLong()} ₽, выплаты по своим долгам: ${snapshot.debtRepaymentExpenseRub.roundToLong()} ₽)")
            appendLine("4. МОИ ДОЛГИ («Я должен» — обязательства пользователя, пассив): ${snapshot.totalWeOweRub.roundToLong()} ₽ [$weOweDetails]")
            appendLine("   (КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО прибавлять «Я должен» к доходу или к доступному балансу!)")
            appendLine("5. ДОЛЖНЫ МНЕ («Мне должны» — дебиторская задолженность, ожидаемый возврат): ${snapshot.totalOwesUsRub.roundToLong()} ₽ [$owesUsDetails]")
            appendLine("   (КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО прибавлять «Мне должны» к заработанному доходу или к текущему доступному остатку до фактического возврата!)")
        }
    }

    /**
     * Формирует понятную пользователю карточку-разбивку без смешивания категорий (для офлайн-ответов и резюме).
     */
    fun formatChatFinancialBreakdown(snapshot: FinancialSemanticsSnapshot): String {
        return buildString {
            appendLine("📊 **Раздельный финансовый расклад (без смешивания статей):**")
            appendLine("• 💰 **Доступный остаток (текущий баланс):** **${snapshot.availableBalanceRub.roundToLong()} ₽**")
            appendLine("• 📈 **Заработанный доход за период:** **${snapshot.earnedIncomeRub.roundToLong()} ₽** (в т.ч. зарплата: **${snapshot.salaryIncomeRub.roundToLong()} ₽**)")
            if (snapshot.debtReturnedIncomeRub > 0.0) {
                appendLine("• 🔄 **Возвращено долгов мне (возврат основного долга, не доход):** **${snapshot.debtReturnedIncomeRub.roundToLong()} ₽**")
            }
            appendLine("• 📉 **Расходы за период:** **${snapshot.totalExpenseRub.roundToLong()} ₽** (обычные траты: **${snapshot.regularExpenseRub.roundToLong()} ₽**, погашение долгов: **${snapshot.debtRepaymentExpenseRub.roundToLong()} ₽**)")
            appendLine("• 🔴 **Я должен (мои обязательства):** **${snapshot.totalWeOweRub.roundToLong()} ₽**")
            append("• 🟢 **Мне должны (дебиторская задолженность):** **${snapshot.totalOwesUsRub.roundToLong()} ₽**")
        }
    }
}
