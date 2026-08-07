import re

with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "r") as f:
    text = f.read()

target = """    suspend fun generateDavidComment(
        apiKey: String,
        type: String,
        category: String,
        subcategory: String,
        amount: Double,
        recentTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<com.example.data.db.AccountEntity> = emptyList(),
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList(),
        extraContext: String = ""
    ): String {"""

replacement = """    suspend fun generateDavidComment(
        apiKey: String,
        type: String,
        category: String,
        subcategory: String,
        amount: Double,
        recentTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<com.example.data.db.AccountEntity> = emptyList(),
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList(),
        extraContext: String = "",
        allTransactions: List<TransactionEntity> = emptyList()
    ): String {"""

target2 = """        val debtsContext = if (activeDebts.isNotEmpty()) {
            "\\nТекущие долги пользователя:\\n" +
            activeDebts.filter { it.type == "we_owe" || it.type == "owes_us" }.joinToString("\\n") { d ->
                "- ${if (d.type == "we_owe") "Взял в долг/Кредит" else "Дал в долг"}: '${d.name}', осталось: ${d.balance} руб."
            } + "\\n"
        } else \"\""""

replacement2 = """        val debtsContext = if (activeDebts.isNotEmpty()) {
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
                "- ${if (isWeOwe) "Взял в долг/Кредит" else "Дал в долг"}: '${d.name}', изначальная сумма: ${d.balance} руб., осталось выплатить/вернуть: $remaining руб."
            } + "\\n"
        } else \"\""""

target3 = """        val systemPrompt = "Ты — Жабов Давид, саркастичный и безжалостный финансовый аудитор с циничным чувством юмора. Твоя цель — высмеять глупые траты или иронично прокомментировать доходы пользователя, замечая связи между его покупками, долгами и целями. Твой ответ должен быть СТРОГО на русском языке, содержать не более 15 слов и быть максимально ярким, мемным или саркастичным.\""""

replacement3 = """        val systemPrompt = "Ты — Жабов Давид, саркастичный и безжалостный финансовый аудитор с циничным чувством юмора. Твоя цель — высмеять глупые траты или иронично прокомментировать доходы пользователя. КРИТИЧЕСКИ ВАЖНО: всегда помни о текущих долгах и целях пользователя. Если у него огромный долг, а он тратит на ерунду (например, покупает фигурки аниме или кофе), обязательно жестко упрекни его в этом! Замечай связи между его покупками, долгами и целями. Твой ответ должен быть СТРОГО на русском языке, содержать не более 15-20 слов и быть максимально ярким, мемным или саркастичным.\""""


if target in text and target2 in text and target3 in text:
    text = text.replace(target, replacement)
    text = text.replace(target2, replacement2)
    text = text.replace(target3, replacement3)
    with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "w") as f:
        f.write(text)
    print("Modified generateDavidComment")
else:
    if target not in text: print("target not found")
    if target2 not in text: print("target2 not found")
    if target3 not in text: print("target3 not found")

