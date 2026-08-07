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

target_save_goal = """    fun saveNewGoal(name: String, target: Double, current: Double) {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        viewModelScope.launch {
            repository.insertGoal(
                GoalEntity(
                    budgetId = currentBudgetId,
                    name = name,
                    targetAmount = target,
                    currentAmount = current
                )
            )
            _toastMessage.emit("Цель добавлена!")
        }
    }"""
replacement_save_goal = """    fun saveNewGoal(name: String, target: Double, current: Double) {
        val currentBudgetId = _selectedBudgetId.value ?: "default"
        viewModelScope.launch {
            repository.insertGoal(
                GoalEntity(
                    budgetId = currentBudgetId,
                    name = name,
                    targetAmount = target,
                    currentAmount = current
                )
            )
            _toastMessage.emit("Цель добавлена!")
            
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
            } catch (e: Exception) { e.printStackTrace() }
        }
    }"""


if target1 in text and target2 in text and target_add_account in text and target_save_goal in text:
    text = text.replace(target1, replacement1)
    text = text.replace(target2, replacement2)
    text = text.replace(target_add_account, replacement_add_account)
    text = text.replace(target_save_goal, replacement_save_goal)
    with open("app/src/main/java/com/example/ui/viewmodel/BudgetViewModel.kt", "w") as f:
        f.write(text)
    print("ViewModel modified.")
else:
    if target1 not in text: print("target1 not found")
    if target2 not in text: print("target2 not found")
    if target_add_account not in text: print("target_add_account not found")
    if target_save_goal not in text: print("target_save_goal not found")

