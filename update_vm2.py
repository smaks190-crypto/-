import re

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "r") as f:
    text = f.read()

target1 = """                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    recentTransactions = transactions.value.take(5)
                )"""
replacement1 = """                val comment = repository.generateDavidComment(
                    apiKey = _apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value
                )"""

target2 = """                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = op.type,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount,
                        recentTransactions = transactions.value.take(5)
                    )"""
replacement2 = """                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = op.type,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value
                    )"""

target_add_account = """    fun addAccount(name: String, initialBalance: Double, type: String = "card", accountNumber: String = "**** 0000") {
        viewModelScope.launch {
            val bId = _selectedBudgetId.value ?: "default"
            repository.insertAccount(
                AccountEntity(
                    budgetId = bId,
                    name = name,
                    balance = initialBalance,
                    type = type,
                    accountNumber = accountNumber
                )
            )
        }
    }"""
replacement_add_account = """    fun addAccount(name: String, initialBalance: Double, type: String = "card", accountNumber: String = "**** 0000") {
        viewModelScope.launch {
            val bId = _selectedBudgetId.value ?: "default"
            repository.insertAccount(
                AccountEntity(
                    budgetId = bId,
                    name = name,
                    balance = initialBalance,
                    type = type,
                    accountNumber = accountNumber
                )
            )
            
            if (type == "we_owe" || type == "owes_us") {
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
                        activeGoals = goals.value
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
            }
        }
    }"""

target_save_goal = """                val goal = GoalEntity(
                    budgetId = currentBudgetId,
                    name = name,
                    targetAmount = target,
                    currentAmount = current
                )
                repository.insertGoal(goal)
                _toastMessage.emit("Финансовая цель добавлена!")"""

replacement_save_goal = """                val goal = GoalEntity(
                    budgetId = currentBudgetId,
                    name = name,
                    targetAmount = target,
                    currentAmount = current
                )
                repository.insertGoal(goal)
                _toastMessage.emit("Финансовая цель добавлена!")
                
                try {
                    val comment = repository.generateDavidComment(
                        apiKey = _apiKey.value,
                        type = "expense",
                        category = "Новая цель",
                        subcategory = name,
                        amount = target,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value
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

text = text.replace(target1, replacement1)
text = text.replace(target2, replacement2)
text = text.replace(target_add_account, replacement_add_account)
text = text.replace(target_save_goal, replacement_save_goal)

with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
    f.write(text)
print("done")
