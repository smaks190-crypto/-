import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

target = """                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = "expense",
                    category = "Сбережения",
                    subcategory = "Взнос в цель: ${goal.name}",
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value
                )"""

replacement = """                val updatedCurrent = goal.currentAmount + amount
                val extraCtx = "Это взнос в цель '${goal.name}'. Собрано $updatedCurrent из ${goal.targetAmount} руб. Осталось: ${goal.targetAmount - updatedCurrent} руб."
                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = "expense",
                    category = "Сбережения",
                    subcategory = "Взнос в цель: ${goal.name}",
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx
                )"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
        f.write(text)
    print("Modified BudgetViewModel addGoalProgress")
else:
    print("Target not found")

