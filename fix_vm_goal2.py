import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

target = """                val updatedCurrent = goal.currentAmount + amount
                val extraCtx = "Это взнос в цель '${goal.name}'. Собрано $updatedCurrent из ${goal.targetAmount} руб. Осталось: ${goal.targetAmount - updatedCurrent} руб."
                val comment = repository.generateDavidComment("""

replacement = """                val extraCtx = "Это взнос в цель '${goal.name}'. Собрано $updatedCurrent из ${goal.targetAmount} руб. Осталось: ${goal.targetAmount - updatedCurrent} руб."
                val comment = repository.generateDavidComment("""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
        f.write(text)
    print("Fixed updatedCurrent duplicate")
else:
    print("Target not found")
