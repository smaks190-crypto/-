import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

target = """                repository.requestAiAuditStream(
                    apiKey = key,
                    periodName = periodName,
                    year = year,
                    filteredTransactions = currentFilteredTransactions,
                    previousTransactions = previousTransactions
                )"""

replacement = """                repository.requestAiAuditStream(
                    apiKey = key,
                    periodName = periodName,
                    year = year,
                    filteredTransactions = currentFilteredTransactions,
                    previousTransactions = previousTransactions,
                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    allTransactions = transactions.value
                )"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
        f.write(text)
    print("Modified vm audit stream.")
else:
    print("Target missing")

