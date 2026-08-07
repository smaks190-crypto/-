import re

with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "r") as f:
    text = f.read()

target = """    suspend fun generateDavidComment(
        apiKey: String,
        type: String,
        category: String,
        subcategory: String,
        amount: Double,
        recentTransactions: List<TransactionEntity> = emptyList()
    ): String {"""

replacement = """    suspend fun generateDavidComment(
        apiKey: String,
        type: String,
        category: String,
        subcategory: String,
        amount: Double,
        recentTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<com.example.data.db.AccountEntity> = emptyList(),
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList()
    ): String {"""

target2 = """        val historyContext = if (recentTransactions.isNotEmpty()) {
            "\\nНедавние операции пользователя (для контекста):\\n" +
            recentTransactions.joinToString("\\n") { tx -> 
                "- ${if (tx.type == "income") "Доход" else "Расход"}: ${tx.category} / ${tx.subcategory}, ${tx.amount} руб." 
            } + "\\nУчитывай этот контекст, возможно новая операция как-то смешно или иронично связана с предыдущими (например, купил шаурму по акции за 100₽, а потом таблетки от диареи).\\n"
        } else {
            ""
        }"""

replacement2 = """        val debtsContext = if (activeDebts.isNotEmpty()) {
            "\\nТекущие долги пользователя:\\n" +
            activeDebts.filter { it.type == "we_owe" || it.type == "owes_us" }.joinToString("\\n") { d ->
                "- ${if (d.type == "we_owe") "Взял в долг/Кредит" else "Дал в долг"}: '${d.name}', осталось: ${d.balance} руб."
            } + "\\n"
        } else ""

        val goalsContext = if (activeGoals.isNotEmpty()) {
            "\\nТекущие цели пользователя:\\n" +
            activeGoals.joinToString("\\n") { g ->
                "- Цель '${g.name}': собрано ${g.currentAmount} из ${g.targetAmount} руб."
            } + "\\n"
        } else ""

        val historyContext = if (recentTransactions.isNotEmpty()) {
            "\\nНедавние операции пользователя (для контекста):\\n" +
            recentTransactions.joinToString("\\n") { tx -> 
                "- ${if (tx.type == "income") "Доход" else "Расход"}: ${tx.category} / ${tx.subcategory}, ${tx.amount} руб." 
            } + "\\nУчитывай этот контекст, возможно новая операция связана с текущими долгами, целями или предыдущими тратами (например, купил шаурму, а потом таблетки; или вносит копейки за кредит, но покупает айфон).\\n"
        } else {
            "\\nУчитывай контекст текущих долгов и целей, если они есть.\\n"
        }"""

target3 = """                historyContext +
                "Напиши один короткий, саркастичный, ироничный, жесткий комментарий от лица циничного финансового аудитора Жабова Давида (не длиннее 15 слов, без лишней вежливости, используя сочный черный юмор, мемы или литературно-исторические сравнения).\""""

replacement3 = """                debtsContext +
                goalsContext +
                historyContext +
                "Напиши один короткий, саркастичный, ироничный, жесткий комментарий от лица циничного финансового аудитора Жабова Давида (не длиннее 15 слов, без лишней вежливости, используя сочный черный юмор, мемы или литературно-исторические сравнения).\""""


target4 = """        val systemPrompt = "Ты — Жабов Давид, саркастичный и безжалостный финансовый аудитор с циничным чувством юмора. Твоя цель — высмеять глупые траты или иронично прокомментировать доходы пользователя, замечая связи между его покупками. Твой ответ должен быть СТРОГО на русском языке, содержать не более 15 слов и быть максимально ярким, мемным или саркастичным.\""""
replacement4 = """        val systemPrompt = "Ты — Жабов Давид, саркастичный и безжалостный финансовый аудитор с циничным чувством юмора. Твоя цель — высмеять глупые траты или иронично прокомментировать доходы пользователя, замечая связи между его покупками, долгами и целями. Твой ответ должен быть СТРОГО на русском языке, содержать не более 15 слов и быть максимально ярким, мемным или саркастичным.\""""

if target in text and target2 in text and target3 in text and target4 in text:
    text = text.replace(target, replacement)
    text = text.replace(target2, replacement2)
    text = text.replace(target3, replacement3)
    text = text.replace(target4, replacement4)
    with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "w") as f:
        f.write(text)
    print("Repository modified.")
else:
    print("Targets not found.")

