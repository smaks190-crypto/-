import re

with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "r") as f:
    text = f.read()

target1 = """    fun requestAiAuditStream(
        apiKey: String,
        periodName: String,
        year: Int,
        filteredTransactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity> = emptyList()
    ): Flow<String> = flow {"""

replacement1 = """    fun requestAiAuditStream(
        apiKey: String,
        periodName: String,
        year: Int,
        filteredTransactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<com.example.data.db.AccountEntity> = emptyList(),
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList()
    ): Flow<String> = flow {"""

target2 = "                \"Также выдай ачивки (достижения) за этот период (хорошие или плохие), основанные на моих тратах.\""

replacement2 = """                debtsContext +
                goalsContext +
                "Детализация расходов по категориям:\\n" +
                expenseByCategory.entries.joinToString("\\n") { "- ${it.key}: ${it.value} руб." } + "\\n\\n" +
                "Детализация доходов по категориям:\\n" +
                incomeByCategory.entries.joinToString("\\n") { "- ${it.key}: ${it.value} руб." } + "\\n\\n" +
                "ТОП-5 самых крупных покупок:\\n" +
                topExpenses.joinToString("\\n") { "- ${it.category}/${it.subcategory}: ${it.amount} руб. (${it.date})" } + "\\n\\n" +
                "Проведи жесточайший разбор, укажи на нелепые или глупые траты, дай саркастичные советы, похвали (если есть за что) с долей иронии.\\n" +
                "ОБЯЗАТЕЛЬНО: Если у пользователя есть непогашенные долги, и при этом он тратит деньги на ерунду или совершает крупные ненужные покупки, жестоко высмей это! Покажи ему абсурдность ситуации, когда при долгах он позволяет себе такие траты.\\n" +
                "Также выдай ачивки (достижения) за этот период (хорошие или плохие), основанные на моих тратах.\""""

target2_find = """        val userQuery = "Я хочу получить полный финансовый аудит за $periodName $year года.\\n" +
                "Сводка:\\n" +
                "- Доходы: $totalIncome руб. (динамика: ${if(incomeDiff > 0) "+$incomeDiff" else incomeDiff} руб. к прошлому периоду)\\n" +
                "- Расходы: $totalExpense руб. (динамика: ${if(expenseDiff > 0) "+$expenseDiff" else expenseDiff} руб. к прошлому периоду)\\n" +
                "- Сальдо: $net руб. (динамика: ${if(netDiff > 0) "+$netDiff" else netDiff} руб.)\\n\\n" +
                "Детализация расходов по категориям:\\n" +
                expenseByCategory.entries.joinToString("\\n") { "- ${it.key}: ${it.value} руб." } + "\\n\\n" +
                "Детализация доходов по категориям:\\n" +
                incomeByCategory.entries.joinToString("\\n") { "- ${it.key}: ${it.value} руб." } + "\\n\\n" +
                "ТОП-5 самых крупных покупок:\\n" +
                topExpenses.joinToString("\\n") { "- ${it.category}/${it.subcategory}: ${it.amount} руб. (${it.date})" } + "\\n\\n" +
                "Проведи жесточайший разбор, укажи на нелепые или глупые траты, дай саркастичные советы, похвали (если есть за что) с долей иронии.\\n" +
                "Также выдай ачивки (достижения) за этот период (хорошие или плохие), основанные на моих тратах.\""""

replacement_final = """        val debtsContext = if (activeDebts.isNotEmpty()) {
            "\\nТекущие долги пользователя:\\n" +
            activeDebts.filter { it.type == "we_owe" || it.type == "owes_us" }.joinToString("\\n") { d ->
                val txs = allTransactions.filter { it.accountId == d.id }
                val isWeOwe = d.type != "owes_us"
                val remaining = if (isWeOwe) {
                    val inc = txs.filter { it.type == "income" }.sumOf { it.amount }
                    val exp = txs.filter { it.type == "expense" }.sumOf { it.amount }
                    d.balance + inc - exp
                } else {
                    val exp = txs.filter { it.type == "expense" }.sumOf { it.amount }
                    val inc = txs.filter { it.type == "income" }.sumOf { it.amount }
                    d.balance + exp - inc
                }
                "- ${if (isWeOwe) "Взял в долг/Кредит" else "Дал в долг"}: '${d.name}', осталось выплатить/вернуть: $remaining руб."
            } + "\\n"
        } else ""

        val goalsContext = if (activeGoals.isNotEmpty()) {
            "\\nТекущие цели пользователя:\\n" +
            activeGoals.joinToString("\\n") { g ->
                "- Цель '${g.name}': собрано ${g.currentAmount} из ${g.targetAmount} руб."
            } + "\\n"
        } else ""

        val userQuery = "Я хочу получить полный финансовый аудит за $periodName $year года.\\n" +
                "Сводка:\\n" +
                "- Доходы: $totalIncome руб. (динамика: ${if(incomeDiff > 0) "+$incomeDiff" else incomeDiff} руб. к прошлому периоду)\\n" +
                "- Расходы: $totalExpense руб. (динамика: ${if(expenseDiff > 0) "+$expenseDiff" else expenseDiff} руб. к прошлому периоду)\\n" +
                "- Сальдо: $net руб. (динамика: ${if(netDiff > 0) "+$netDiff" else netDiff} руб.)\\n\\n" +
                debtsContext +
                goalsContext +
                "Детализация расходов по категориям:\\n" +
                expenseByCategory.entries.joinToString("\\n") { "- ${it.key}: ${it.value} руб." } + "\\n\\n" +
                "Детализация доходов по категориям:\\n" +
                incomeByCategory.entries.joinToString("\\n") { "- ${it.key}: ${it.value} руб." } + "\\n\\n" +
                "ТОП-5 самых крупных покупок:\\n" +
                topExpenses.joinToString("\\n") { "- ${it.category}/${it.subcategory}: ${it.amount} руб. (${it.date})" } + "\\n\\n" +
                "Проведи жесточайший разбор, укажи на нелепые или глупые траты, дай саркастичные советы, похвали (если есть за что) с долей иронии.\\n" +
                "ОБЯЗАТЕЛЬНО: Если у пользователя есть непогашенные долги, и при этом он тратит деньги на ерунду или совершает крупные ненужные покупки, жестоко высмей это! Покажи ему абсурдность ситуации, когда при долгах он позволяет себе такие траты.\\n" +
                "Также выдай ачивки (достижения) за этот период (хорошие или плохие), основанные на моих тратах.\""""

if target1 in text:
    text = text.replace(target1, replacement1)
    text = text.replace(target2_find, replacement_final)
    with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "w") as f:
        f.write(text)
    print("Modified audit stream repo.")
else:
    print("Target 1 missing")

