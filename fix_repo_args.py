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
        activeGoals: List<com.example.data.db.GoalEntity> = emptyList()
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
        extraContext: String = ""
    ): String {"""

target2 = """        val historyContext = if (recentTransactions.isNotEmpty()) {
            "\\nНедавние операции пользователя (для контекста):\\n" +
            recentTransactions.joinToString("\\n") { tx -> 
                "- ${if (tx.type == "income") "Доход" else "Расход"}: ${tx.category} / ${tx.subcategory}, ${tx.amount} руб." 
            } + "\\nУчитывай этот контекст, возможно новая операция связана с текущими долгами, целями или предыдущими тратами (например, купил шаурму, а потом таблетки; или вносит копейки за кредит, но покупает айфон).\\n"
        } else {
            "\\nУчитывай контекст текущих долгов и целей, если они есть.\\n"
        }"""

replacement2 = """        val historyContext = if (recentTransactions.isNotEmpty()) {
            "\\nНедавние операции пользователя (для контекста):\\n" +
            recentTransactions.joinToString("\\n") { tx -> 
                "- ${if (tx.type == "income") "Доход" else "Расход"}: ${tx.category} / ${tx.subcategory}, ${tx.amount} руб." 
            } + "\\nУчитывай этот контекст, возможно новая операция связана с текущими долгами, целями или предыдущими тратами (например, купил шаурму, а потом таблетки; или вносит копейки за кредит, но покупает айфон).\\n"
        } else {
            "\\nУчитывай контекст текущих долгов и целей, если они есть.\\n"
        }
        val fullExtra = if (extraContext.isNotBlank()) "\\nДетали:\\n$extraContext\\n" else ""
"""

target3 = """                debtsContext +
                goalsContext +
                historyContext +
                "Напиши один короткий, саркастичный, ироничный, жесткий комментарий от лица циничного финансового аудитора Жабова Давида (не длиннее 15 слов, без лишней вежливости, используя сочный черный юмор, мемы или литературно-исторические сравнения).\""""

replacement3 = """                debtsContext +
                goalsContext +
                fullExtra +
                historyContext +
                "Напиши один короткий, саркастичный, ироничный, жесткий комментарий от лица циничного финансового аудитора Жабова Давида (не длиннее 15 слов, без лишней вежливости, используя сочный черный юмор, мемы или литературно-исторические сравнения).\""""

if target in text and target2 in text and target3 in text:
    text = text.replace(target, replacement)
    text = text.replace(target2, replacement2)
    text = text.replace(target3, replacement3)
    with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "w") as f:
        f.write(text)
    print("Modified BudgetRepository")
else:
    print("Targets not found")

