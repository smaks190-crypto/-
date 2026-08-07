import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

target1 = """                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx
                )"""

replacement1 = """                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx,
                    allTransactions = transactions.value
                )"""

target2 = """                        activeDebts = accounts.value,
                        activeGoals = goals.value
                    )"""

replacement2 = """                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )"""

target3 = """                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        extraContext = extraCtx
                    )"""
replacement3 = """                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        extraContext = extraCtx,
                        allTransactions = transactions.value
                    )"""

text = text.replace(target1, replacement1)
text = text.replace(target2, replacement2)
text = text.replace(target3, replacement3)

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
    f.write(text)
print("done")
