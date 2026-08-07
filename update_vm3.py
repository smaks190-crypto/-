import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

target = """            // Automatically log contribution as expense under "Сбережения"
            val tx = TransactionEntity(
                budgetId = currentBudgetId,
                type = "expense",
                date = todayIso,
                category = "Сбережения",
                subcategory = "Взнос в цель: ${goal.name}",
                amount = amount
            )
            repository.insertTransaction(tx)"""

replacement = """            // Automatically log contribution as expense under "Сбережения"
            val tx = TransactionEntity(
                budgetId = currentBudgetId,
                type = "expense",
                date = todayIso,
                category = "Сбережения",
                subcategory = "Взнос в цель: ${goal.name}",
                amount = amount
            )
            repository.insertTransaction(tx)
            
            try {
                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = "expense",
                    category = "Сбережения",
                    subcategory = "Взнос в цель: ${goal.name}",
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value
                )
                val userPhrase = repository.generateUserPhrase(
                    apiKey = _apiKey.value,
                    type = "expense",
                    category = "Сбережения",
                    subcategory = "Взнос в цель: ${goal.name}",
                    amount = amount,
                    isFirstToday = transactions.value.none { it.date == todayIso && it.id != tx.id }
                )
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        budgetId = currentBudgetId,
                        title = "Взнос в цель",
                        description = "||expense|Сбережения|Взнос в цель: ${goal.name}|$amount|$userPhrase||$comment",
                        icon = "david",
                        color = "emerald400",
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )
            } catch (e: Exception) { e.printStackTrace() }"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
        f.write(text)
    print("Done update_vm3")
else:
    print("Not found target")

