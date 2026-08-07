import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

target1 = """                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value
                )"""

replacement1 = """                var extraCtx = ""
                if (accountId != null) {
                    val debt = accounts.value.find { it.id == accountId }
                    if (debt != null && (debt.type == "we_owe" || debt.type == "owes_us")) {
                        val txs = transactions.value.filter { it.accountId == debt.id }
                        val income = txs.filter { it.type == "income" }.sumOf { it.amount }
                        val expense = txs.filter { it.type == "expense" }.sumOf { it.amount }
                        
                        val isWeOwe = debt.type != "owes_us"
                        val remaining = if (isWeOwe) {
                            debt.balance + income - expense - (if (type == "expense") amount else -amount)
                        } else {
                            debt.balance + expense - income - (if (type == "income") amount else -amount)
                        }
                        extraCtx = "Операция относится к долгу '${debt.name}'. После этой операции остаток долга: $remaining руб."
                    }
                }
                
                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx
                )"""

if target1 in text:
    text = text.replace(target1, replacement1)
    with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
        f.write(text)
    print("Modified BudgetViewModel addTransaction")
else:
    print("Target not found")

