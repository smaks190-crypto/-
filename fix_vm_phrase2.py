import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

# 1. Modify addTransaction extraCtx for debts
target_debt_ext = """                        val remaining = if (isWeOwe) {
                            debt.balance + income - expense - (if (type == "expense") amount else -amount)
                        } else {
                            debt.balance + expense - income - (if (type == "income") amount else -amount)
                        }
                        extraCtx = "Операция относится к долгу '${debt.name}'. После этой операции остаток долга: $remaining руб.\""""

replacement_debt_ext = """                        val remaining = if (isWeOwe) {
                            debt.balance + income - expense - (if (type == "expense") amount else -amount)
                        } else {
                            debt.balance + expense - income - (if (type == "income") amount else -amount)
                        }
                        extraCtx = if (remaining <= 0) {
                            "Операция относится к долгу '${debt.name}'. ПОЛЬЗОВАТЕЛЬ ТОЛЬКО ЧТО ПОЛНОСТЬЮ ЗАКРЫЛ/ПОГАСИЛ ЭТОТ ДОЛГ! Прокомментируй это событие."
                        } else {
                            "Операция относится к долгу '${debt.name}'. После этой операции остаток долга: $remaining руб."
                        }"""

# 2. Modify addGoalProgress extraCtx for goals
target_goal_ext = """                val extraCtx = "Это взнос в цель '${goal.name}'. Собрано $updatedCurrent из ${goal.targetAmount} руб. Осталось: ${goal.targetAmount - updatedCurrent} руб.\""""

replacement_goal_ext = """                val extraCtx = if (updatedCurrent >= goal.targetAmount) {
                    "Это взнос в цель '${goal.name}'. ПОЛЬЗОВАТЕЛЬ ТОЛЬКО ЧТО ПОЛНОСТЬЮ НАКОПИЛ И ДОСТИГ ЭТОЙ ЦЕЛИ! Прокомментируй это достижение."
                } else {
                    "Это взнос в цель '${goal.name}'. Собрано $updatedCurrent из ${goal.targetAmount} руб. Осталось: ${goal.targetAmount - updatedCurrent} руб."
                }"""

# 3. Modify saveNewGoal to generate user phrase
target_save_goal = """                try {
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = "expense",
                        category = "Новая цель",
                        subcategory = name,
                        amount = target,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = "Новая цель!",
                            description = "||expense|Цели|$name|$target|Открыта новая цель||$comment",
                            icon = "david",
                            color = "emerald400",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }"""

replacement_save_goal = """                try {
                    val userPhrase = repository.generateUserPhrase(
                        apiKey = _apiKey.value,
                        type = "expense",
                        category = "Цели",
                        subcategory = "Открыл цель: $name",
                        amount = target,
                        isFirstToday = false
                    )
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = "expense",
                        category = "Новая цель",
                        subcategory = name,
                        amount = target,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = "Новая цель!",
                            description = "||expense|Цели|$name|$target|$userPhrase||$comment",
                            icon = "david",
                            color = "emerald400",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }"""

# 4. Modify addAccount to generate user phrase for debts
target_add_account = """            if (type == "we_owe" || type == "owes_us") {
                try {
                    val debtType = if (type == "we_owe") "Взял долг/кредит" else "Дал в долг"
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = if (type == "we_owe") "expense" else "income",
                        category = "Долги/Кредиты",
                        subcategory = name,
                        amount = initialBalance,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    val displayType = if (type == "we_owe") "expense" else "income"
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = bId,
                            title = "Новый долг!",
                            description = "||$displayType|Долги|$name|$initialBalance|Открыт долг/кредит||$comment",
                            icon = "david",
                            color = if (type == "owes_us") "emerald400" else "rose500",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }"""

replacement_add_account = """            if (type == "we_owe" || type == "owes_us") {
                try {
                    val debtType = if (type == "we_owe") "Взял долг/кредит" else "Дал в долг"
                    val userPhrase = repository.generateUserPhrase(
                        apiKey = _apiKey.value,
                        type = if (type == "we_owe") "income" else "expense",
                        category = "Долги/Кредиты",
                        subcategory = "$debtType: $name",
                        amount = initialBalance,
                        isFirstToday = false
                    )
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = if (type == "we_owe") "expense" else "income",
                        category = "Долги/Кредиты",
                        subcategory = "$debtType: $name",
                        amount = initialBalance,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    val displayType = if (type == "we_owe") "expense" else "income"
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = bId,
                            title = if (type == "we_owe") "Взяли долг!" else "Дали в долг!",
                            description = "||$displayType|Долги|$name|$initialBalance|$userPhrase||$comment",
                            icon = "david",
                            color = if (type == "owes_us") "emerald400" else "rose500",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }"""

if target_debt_ext in text: text = text.replace(target_debt_ext, replacement_debt_ext)
else: print("target_debt_ext missing")

if target_goal_ext in text: text = text.replace(target_goal_ext, replacement_goal_ext)
else: print("target_goal_ext missing")

if target_save_goal in text: text = text.replace(target_save_goal, replacement_save_goal)
else: print("target_save_goal missing")

if target_add_account in text: text = text.replace(target_add_account, replacement_add_account)
else: print("target_add_account missing")

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
    f.write(text)
print("Done ViewModel modifications.")
