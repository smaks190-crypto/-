package com.example.ui.viewmodel

import android.content.Context
import com.example.data.db.AccountEntity
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionEntity
import com.example.data.repository.BudgetRepository
import com.example.data.repository.ParsedVoiceOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class TransactionStateDelegate(
    private val repository: BudgetRepository,
    private val scope: CoroutineScope,
    private val selectedBudgetId: StateFlow<String?>,
    private val apiKey: StateFlow<String>,
    private val selectedDateDay: StateFlow<String>,
    private val toastMessage: MutableSharedFlow<String>,
    private val isGeneratingReaction: MutableStateFlow<Boolean>,
    private val isAnalyzingVoice: MutableStateFlow<Boolean>,
    private val voiceErrorMessage: MutableStateFlow<String?>,
    private val parsedVoiceOperations: MutableStateFlow<List<ParsedVoiceOperation>?>,
    private val completedGoalEvent: MutableStateFlow<String?>,
    private val transactions: StateFlow<List<TransactionEntity>>,
    private val accounts: StateFlow<List<AccountEntity>>,
    private val goals: StateFlow<List<GoalEntity>>,
    private val categories: StateFlow<List<CategoryEntity>>,
    private val voiceInputManager: com.example.utils.VoiceInputManager,
    private val isVoiceActive: MutableStateFlow<Boolean>,
    private val manualText: MutableStateFlow<String>,
    private val aiAuditResult: MutableStateFlow<String?>,
    private val aiAuditLoading: MutableStateFlow<Boolean>,
    private val getSavedApiKey: () -> String
) {

    private var _voiceStartTime = 0L
    val voiceStartTime: Long get() = _voiceStartTime

    private var voiceCollectionJob: kotlinx.coroutines.Job? = null

    fun startVoiceRecording(context: Context) {
        _voiceStartTime = System.currentTimeMillis()
        isVoiceActive.value = true
        manualText.value = ""
        voiceErrorMessage.value = null
        voiceInputManager.onErrorCallback = { cancelVoiceRecording() }
        voiceInputManager.onChunkRecognized = { chunkText ->
            processContinuousVoiceChunk(chunkText)
        }
        voiceInputManager.startListening(context)
    }

    fun processContinuousVoiceChunk(chunkText: String) {
        val trimmed = chunkText.trim()
        if (trimmed.isBlank() || !isVoiceActive.value) return

        scope.launch {
            isAnalyzingVoice.value = true
            voiceErrorMessage.value = null
            try {
                val expCats = categories.value.filter { it.type == "expense" }.map { it.name }
                val incCats = categories.value.filter { it.type == "income" }.map { it.name }

                val result = repository.parseVoiceOperations(
                    voiceText = trimmed,
                    apiKey = apiKey.value,
                    expenseCategories = expCats,
                    incomeCategories = incCats
                )

                if (result.isNotEmpty()) {
                    val currentList = parsedVoiceOperations.value ?: emptyList()
                    val updatedList = currentList + result
                    parsedVoiceOperations.value = updatedList
                    com.example.utils.GlobalConsoleLogger.i("UI", "Добавлены новые операции (${result.size} шт.). Всего: ${updatedList.size} шт.")
                } else {
                    com.example.utils.GlobalConsoleLogger.d("GEMINI", "В фрагменте «$trimmed» операции не найдены")
                }
            } catch (e: Exception) {
                com.example.utils.GlobalConsoleLogger.e("GEMINI", "Ошибка при обработке фрагмента «$trimmed»: ${e.localizedMessage}", e)
            } finally {
                isAnalyzingVoice.value = false
            }
        }
    }

    fun stopVoiceRecordingAndProcess() {
        voiceCollectionJob?.cancel()
        voiceCollectionJob = null
        voiceInputManager.stopListening()
        isVoiceActive.value = false
        val textToProcess = when {
            manualText.value.isNotBlank() -> manualText.value
            voiceInputManager.recognizedText.value.isNotBlank() -> voiceInputManager.recognizedText.value
            voiceInputManager.partialText.value.isNotBlank() -> voiceInputManager.partialText.value
            else -> ""
        }
        if (textToProcess.isNotBlank()) {
            processVoiceText(textToProcess)
        } else {
            cancelVoiceRecording()
        }
    }

    fun cancelVoiceRecording() {
        voiceCollectionJob?.cancel()
        voiceCollectionJob = null
        voiceInputManager.stopListening()
        isVoiceActive.value = false
        voiceErrorMessage.value = null
        clearParsedVoiceOperations()
    }

    fun setVoiceActive(active: Boolean) {
        if (active) {
            _voiceStartTime = System.currentTimeMillis()
        }
        isVoiceActive.value = active
    }

    fun setManualText(text: String) {
        manualText.value = text
    }

    fun processVoiceText(voiceText: String) {
        if (voiceText.isBlank()) return
        scope.launch {
            isAnalyzingVoice.value = true
            voiceErrorMessage.value = null
            try {
                val expCats = categories.value.filter { it.type == "expense" }.map { it.name }
                val incCats = categories.value.filter { it.type == "income" }.map { it.name }

                val result = repository.parseVoiceOperations(
                    voiceText = voiceText,
                    apiKey = apiKey.value,
                    expenseCategories = expCats,
                    incomeCategories = incCats
                )

                if (result.isEmpty()) {
                    voiceErrorMessage.value = "Не удалось распознать операции из текста. Укажите суммы и название, например: «Потратил 500 рублей на такси»"
                    parsedVoiceOperations.value = null
                } else {
                    parsedVoiceOperations.value = result
                    val finalDate = selectedDateDay.value.ifBlank {
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    }
                    confirmVoiceOperations(result, finalDate)
                }
            } catch (e: Exception) {
                voiceErrorMessage.value = "Ошибка при анализе: ${e.message}"
                parsedVoiceOperations.value = null
            } finally {
                isAnalyzingVoice.value = false
            }
        }
    }

    fun clearParsedVoiceOperations() {
        parsedVoiceOperations.value = null
        voiceErrorMessage.value = null
        isAnalyzingVoice.value = false
        isVoiceActive.value = false
        manualText.value = ""
        voiceInputManager.clear()
    }

    private suspend fun ensureCategoryExists(categoryName: String, type: String = "expense") {
        if (categoryName.isBlank() || categoryName.equals("null", ignoreCase = true)) return
        val currentBudgetId = selectedBudgetId.value ?: "default"
        val trimmed = categoryName.trim()
        val existing = categories.value.find { it.name.equals(trimmed, ignoreCase = true) }
        if (existing == null) {
            val cat = CategoryEntity(
                budgetId = currentBudgetId,
                type = type,
                name = trimmed
            )
            repository.insertCategory(cat)
        }
    }

    fun addTransaction(type: String, date: String, category: String, subcategory: String, amount: Double, accountId: String? = null) {
        val currentBudgetId = selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Добавление транзакции [$type]: $amount ₽ ($category / $subcategory), дата=$date")
        scope.launch {
            ensureCategoryExists(category, type)
            val tx = TransactionEntity(
                budgetId = currentBudgetId,
                accountId = accountId,
                type = type,
                date = date,
                category = category,
                subcategory = subcategory,
                amount = amount
            )
            repository.insertTransaction(tx)
            toastMessage.emit("Операция добавлена!")
            isGeneratingReaction.value = true

            try {
                val isFirstToday = transactions.value.none { it.date == date && it.id != tx.id }
                val userPhrase = repository.generateUserPhrase(
                    apiKey = apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    isFirstToday = isFirstToday
                )
                var extraCtx = ""
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
                        val debtTotal = debt.balance.coerceAtLeast(1.0)
                        val ratioPercent = (amount / debtTotal) * 100.0

                        extraCtx = if (remaining <= 0) {
                            "Операция относится к долгу '${debt.name}' (общая сумма долга была: ${debtTotal.toInt()} руб.). ПОЛЬЗОВАТЕЛЬ ТОЛЬКО ЧТО ПОЛНОСТЬЮ ЗАКРЫЛ/ПОГАСИЛ ЭТОТ ДОЛГ! Прокомментируй это радостное событие."
                        } else if (ratioPercent < 5.0 && debtTotal >= 1000.0) {
                            "Операция относится к долгу '${debt.name}'. ОБЩАЯ СУММА ДОЛГА: ${debtTotal.toInt()} руб., а внесено/возвращено ВСЕГО ${amount.toInt()} руб. (это лишь ${String.format(Locale.US, "%.1f", ratioPercent)}% от общей суммы долга!). Это смехотворные копейки на фоне долга в ${debtTotal.toInt()} руб.! ОБЯЗАТЕЛЬНО отреагируй на этот абсурд и смешной мизерный взнос/возврат по сравнению с огромным долгом!"
                        } else {
                            "Операция относится к долгу '${debt.name}'. Общая целевая сумма долга: ${debtTotal.toInt()} руб. Текущий внесенный взнос: ${amount.toInt()} руб. Остаток долга: ${remaining.toInt()} руб."
                        }
                    }
                }
                
                val comment = repository.generateDavidComment(
                    apiKey = apiKey.value,
                    type = type,
                    category = category,
                    subcategory = subcategory,
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx,
                    allTransactions = transactions.value
                )
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        budgetId = currentBudgetId,
                        title = if (type == "income") "Реакция Давида на доход" else "Прожарка от Давида",
                        description = "||$type|$category|$subcategory|$amount|$userPhrase||$comment",
                        icon = "david",
                        color = if (type == "income") "emerald400" else "rose500",
                        timestamp = System.currentTimeMillis(),
                        isRead = false
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isGeneratingReaction.value = false
            }
        }
    }

    fun updateTransaction(id: String, type: String, date: String, category: String, subcategory: String, amount: Double) {
        val currentBudgetId = selectedBudgetId.value ?: "default"
        scope.launch {
            ensureCategoryExists(category, type)
            val tx = TransactionEntity(
                id = id,
                budgetId = currentBudgetId,
                type = type,
                date = date,
                category = category,
                subcategory = subcategory,
                amount = amount
            )
            repository.insertTransaction(tx)
            toastMessage.emit("Операция обновлена!")
        }
    }

    fun confirmVoiceOperations(
        operations: List<ParsedVoiceOperation>,
        dateStr: String
    ) {
        val currentBudgetId = selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Подтверждение операций (${operations.size} шт.), дата: $dateStr")
        scope.launch {
            if (operations.isEmpty()) return@launch
            
            isGeneratingReaction.value = true
            
            if (operations.size == 1) {
                val op = operations[0]
                val finalDate = if (op.date.isNotBlank() && op.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) op.date else dateStr
                val finalCategory = if (op.category.isNotBlank() && !op.category.equals("null", true)) op.category else "Прочее"
                val finalSubcategory = if (op.subcategory.isNotBlank() && !op.subcategory.equals("null", true)) op.subcategory else op.title

                ensureCategoryExists(finalCategory, op.type)

                val tx = TransactionEntity(
                    budgetId = currentBudgetId,
                    type = op.type,
                    date = finalDate,
                    category = finalCategory,
                    subcategory = finalSubcategory,
                    amount = op.amount
                )
                
                if (op.items.isNotEmpty()) {
                    repository.insertReceiptTransaction(tx, op.items)
                } else {
                    repository.insertTransaction(tx)
                }
                
                com.example.utils.GlobalConsoleLogger.i("ROOM", "Сохранена транзакция в DB: ${tx.category} / ${tx.subcategory} (${tx.amount} ₽)")

                try {
                    val isFirstToday = transactions.value.none { it.date == finalDate && it.id != tx.id }
                    val userPhrase = repository.generateUserPhrase(
                        apiKey = apiKey.value,
                        type = op.type,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount,
                        isFirstToday = isFirstToday
                    )
                    val comment = repository.generateDavidComment(
                        apiKey = apiKey.value,
                        type = op.type,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = if (op.type == "income") "Реакция Давида на доход" else "Прожарка от Давида",
                            description = "||${op.type}|$finalCategory|$finalSubcategory|${op.amount}|$userPhrase||$comment",
                            icon = "david",
                            color = if (op.type == "income") "emerald400" else "rose500",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isGeneratingReaction.value = false
                }
            } else {
                val processedOps = mutableListOf<ParsedVoiceOperation>()
                for (op in operations) {
                    val finalDate = if (op.date.isNotBlank() && op.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) op.date else dateStr
                    val finalCategory = if (op.category.isNotBlank() && !op.category.equals("null", true)) op.category else "Прочее"
                    val finalSubcategory = if (op.subcategory.isNotBlank() && !op.subcategory.equals("null", true)) op.subcategory else op.title

                    ensureCategoryExists(finalCategory, op.type)

                    val tx = TransactionEntity(
                        budgetId = currentBudgetId,
                        type = op.type,
                        date = finalDate,
                        category = finalCategory,
                        subcategory = finalSubcategory,
                        amount = op.amount
                    )
                    
                    if (op.items.isNotEmpty()) {
                        repository.insertReceiptTransaction(tx, op.items)
                    } else {
                        repository.insertTransaction(tx)
                    }
                    
                    processedOps.add(op.copy(date = finalDate, category = finalCategory, subcategory = finalSubcategory))
                }

                try {
                    val userPhrase = repository.generateUserPhraseMulti(
                        apiKey = apiKey.value,
                        operations = processedOps
                    )
                    val comment = repository.generateDavidCommentMulti(
                        apiKey = apiKey.value,
                        operations = processedOps,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    )
                    
                    val opsString = processedOps.joinToString(";") { "${it.type}|${it.category}|${it.subcategory}|${it.amount}" }
                    val dominantType = if (processedOps.all { it.type == "income" }) "income" else "expense"
                    
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = if (dominantType == "income") "Реакция Давида на доходы" else "Групповая прожарка от Давида",
                            description = "||MULTI||$opsString||$userPhrase||$comment",
                            icon = "david",
                            color = if (dominantType == "income") "emerald400" else "rose500",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isGeneratingReaction.value = false
                }
            }
            
            parsedVoiceOperations.value = null
            toastMessage.emit("🔥 Успешно добавлено ${operations.size} операций!")
        }
    }

    fun deleteTransaction(id: String) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Удаление транзакции ID: $id")
        scope.launch {
            repository.deleteTransaction(id)
            toastMessage.emit("Операция удалена")
        }
    }

    fun addGoalProgress(goalId: String, amount: Double, todayIso: String) {
        val currentBudgetId = selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Взнос в финансовую цель ID=$goalId на сумму $amount ₽")
        scope.launch {
            val currentGoals = goals.value
            val goal = currentGoals.find { it.id == goalId } ?: return@launch

            val updatedCurrent = goal.currentAmount + amount

            // Automatically log contribution as expense under "Сбережения"
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
                val extraCtx = if (updatedCurrent >= goal.targetAmount) {
                    "Это взнос в цель '${goal.name}'. ПОЛЬЗОВАТЕЛЬ ТОЛЬКО ЧТО ПОЛНОСТЬЮ НАКОПИЛ И ДОСТИГ ЭТОЙ ЦЕЛИ! Прокомментируй это достижение."
                } else {
                    "Это взнос в цель '${goal.name}'. Собрано $updatedCurrent из ${goal.targetAmount} руб. Осталось: ${goal.targetAmount - updatedCurrent} руб."
                }
                val comment = repository.generateDavidComment(
                    apiKey = apiKey.value,
                    type = "expense",
                    category = "Сбережения",
                    subcategory = "Взнос в цель: ${goal.name}",
                    amount = amount,
                    recentTransactions = transactions.value.take(5),
                    activeDebts = accounts.value,
                    activeGoals = goals.value,
                    extraContext = extraCtx,
                    allTransactions = transactions.value
                )
                val userPhrase = repository.generateUserPhrase(
                    apiKey = apiKey.value,
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
            } catch (e: Exception) { e.printStackTrace() }

            if (updatedCurrent >= goal.targetAmount) {
                // Goal reached! Delete goal automatically and emit congratulation event
                repository.deleteGoal(goal.id)
                completedGoalEvent.value = goal.name
            } else {
                repository.insertGoal(goal.copy(currentAmount = updatedCurrent))
                toastMessage.emit("Взнос сохранен и учтен в расходах!")
            }
        }
    }

    fun saveNewGoal(name: String, target: Double, current: Double, todayIso: String) {
        val currentBudgetId = selectedBudgetId.value ?: "default"
        com.example.utils.GlobalConsoleLogger.i("UI", "Создание финансовой цели: «$name» (цель: $target ₽, начально: $current ₽)")
        scope.launch {
            if (current > 0) {
                val tx = TransactionEntity(
                    budgetId = currentBudgetId,
                    type = "expense",
                    date = todayIso,
                    category = "Сбережения",
                    subcategory = "Взнос в цель: $name",
                    amount = current
                )
                repository.insertTransaction(tx)
            }

            if (target > 0 && current >= target) {
                // Goal created already at or above 100% target
                completedGoalEvent.value = name
            } else {
                val goal = GoalEntity(
                    budgetId = currentBudgetId,
                    name = name,
                    targetAmount = target,
                    currentAmount = current
                )
                repository.insertGoal(goal)
                toastMessage.emit("Финансовая цель добавлена!")
                
                try {
                    val goalSubcategory = "Цель: $name (Целевая сумма: ${target.toInt()} ₽, Внесено: ${current.toInt()} ₽)"
                    val userPhrase = repository.generateUserPhrase(
                        apiKey = apiKey.value,
                        type = "expense",
                        category = "Цели",
                        subcategory = goalSubcategory,
                        amount = if (current > 0) current else target,
                        isFirstToday = false
                    )
                    val extraCtx = "Создана новая финансовая цель '$name'. Целевая сумма: ${target.toInt()} руб. Первоначальный взнос: ${current.toInt()} руб. (Осталось собрать: ${(target - current).toInt()} руб.). В комментарии ОБЯЗАТЕЛЬНО раздели и учти общую сумму цели (${target.toInt()} ₽) и сколько из неё было внесено первым взносом (${current.toInt()} ₽)!"
                    val comment = repository.generateDavidComment(
                        apiKey = apiKey.value,
                        type = "expense",
                        category = "Новая цель",
                        subcategory = goalSubcategory,
                        amount = target,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        extraContext = extraCtx,
                        allTransactions = transactions.value
                    )
                    repository.insertNotification(
                        com.example.data.db.NotificationEntity(
                            budgetId = currentBudgetId,
                            title = "Новая цель!",
                            description = "||expense|Цели|$name (Цель: ${target.toInt()} ₽, Внесено: ${current.toInt()} ₽)|${if (current > 0) current else target}|$userPhrase||$comment",
                            icon = "david",
                            color = "emerald400",
                            timestamp = System.currentTimeMillis(),
                            isRead = false
                        )
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteGoal(id: String) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Удаление финансовой цели ID: $id")
        scope.launch {
            repository.deleteGoal(id)
            toastMessage.emit("Цель удалена")
        }
    }

    fun addAccount(name: String, initialBalance: Double, type: String = "card", accountNumber: String = "**** 0000") {
        com.example.utils.GlobalConsoleLogger.i("UI", "Добавление счета/долга: «$name» (тип=$type, сумма=$initialBalance ₽)")
        scope.launch {
            val bId = selectedBudgetId.value ?: "default"
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
                    val existingActiveDebts = accounts.value.filter { (it.type == "we_owe" || it.type == "owes_us") && it.balance > 0 }
                    val existingTotalSum = existingActiveDebts.sumOf { it.balance }

                    val debtExtraCtx = if (existingActiveDebts.isNotEmpty()) {
                        "ВНИМАНИЕ! Пользователь только что ${if (type == "we_owe") "взял НОВЫЙ долг/кредит" else "дал НОВЫЙ долг"} '$name' на сумму ${initialBalance.toInt()} руб., ПРИ ТОМ ЧТО У НЕГО УЖЕ ЕСТЬ НЕПОГАШЕННЫЕ ДОЛГИ на общую сумму ${existingTotalSum.toInt()} руб.! (Существующие активные долги: ${existingActiveDebts.joinToString { "${it.name}: ${it.balance.toInt()} ₽" }}). ОБЯЗАТЕЛЬНО жестко отреагируй на это решение брать/давать новые долги при не закрытых старых!"
                    } else {
                        "Пользователь создал новый долг '$name' на сумму ${initialBalance.toInt()} руб."
                    }

                    val userPhrase = repository.generateUserPhrase(
                        apiKey = apiKey.value,
                        type = if (type == "we_owe") "income" else "expense",
                        category = "Долги/Кредиты",
                        subcategory = "$debtType: $name",
                        amount = initialBalance,
                        isFirstToday = false
                    )
                    val comment = repository.generateDavidComment(
                        apiKey = apiKey.value,
                        type = if (type == "we_owe") "expense" else "income",
                        category = "Долги/Кредиты",
                        subcategory = "$debtType: $name",
                        amount = initialBalance,
                        recentTransactions = transactions.value.take(5),
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        extraContext = debtExtraCtx,
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
            }
        }
    }

    fun deleteAccount(accountId: String) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Удаление счета/долга ID: $accountId")
        scope.launch {
            repository.deleteAccountById(accountId)
        }
    }

    fun transferBetweenAccounts(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        fromName: String,
        toName: String
    ) {
        com.example.utils.GlobalConsoleLogger.i("UI", "Перевод $amount ₽ с «$fromName» на «$toName»")
        scope.launch {
            val bId = selectedBudgetId.value ?: "default"
            repository.transferBetweenAccounts(
                budgetId = bId,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                fromName = fromName,
                toName = toName
            )
        }
    }

    fun requestAiAudit(
        currentFilteredTransactions: List<TransactionEntity>,
        isGeminiConsentGiven: Boolean,
        periodType: PeriodType,
        selectedDateDay: String,
        selectedAnnualYear: Int,
        selectedMonthIdx: Int,
        currentPeriodKey: String
    ) {
        if (!isGeminiConsentGiven) {
            aiAuditResult.value = "⚠️ **Ошибка доступа:** Для формирования ИИ-разбора требуется согласие на обработку данных. Пожалуйста, включите разрешение в Настройках приложения."
            return
        }
        val key = apiKey.value.ifBlank { getSavedApiKey() }
        if (key.isBlank()) {
            aiAuditResult.value = "ERROR_NO_CONNECTION"
            return
        }
        val bId = selectedBudgetId.value ?: "default"
        val dateDay = selectedDateDay
        val year = when (periodType) {
            PeriodType.DAY, PeriodType.WEEK -> dateDay.take(4).toIntOrNull() ?: selectedAnnualYear
            else -> selectedAnnualYear
        }
        val month = if (periodType == PeriodType.MONTH) selectedMonthIdx + 1 else 0
        val pKey = currentPeriodKey

        val allTxs = transactions.value

        // Compute previous period transactions for comparative analysis
        val previousTransactions = when (periodType) {
            PeriodType.MONTH -> {
                val prevMonthIdx = if (selectedMonthIdx > 0) selectedMonthIdx - 1 else 11
                val prevYear = if (selectedMonthIdx > 0) selectedAnnualYear else selectedAnnualYear - 1
                val prevPrefix = String.format(Locale.US, "%04d-%02d", prevYear, prevMonthIdx + 1)
                allTxs.filter { it.date.startsWith(prevPrefix) }
            }
            PeriodType.ALL -> {
                val prevYearPrefix = String.format(Locale.US, "%04d", selectedAnnualYear - 1)
                allTxs.filter { it.date.startsWith(prevYearPrefix) }
            }
            else -> emptyList()
        }

        if (aiAuditLoading.value) {
            return
        }

        aiAuditLoading.value = true
        aiAuditResult.value = ""

        scope.launch {
            try {
                val monthNames = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
                val periodName = when (periodType) {
                    PeriodType.DAY -> "День ($dateDay)"
                    PeriodType.WEEK -> "Неделя ($dateDay)"
                    PeriodType.MONTH -> "${monthNames.getOrElse(month - 1) { "Месяц" }} $year года"
                    PeriodType.ALL -> "Весь $year год"
                }

                // Save user audit request to chat history DB with unique UUID
                val reqTime = System.currentTimeMillis()
                val reqId = UUID.randomUUID().toString()
                repository.insertNotification(
                    com.example.data.db.NotificationEntity(
                        id = reqId,
                        budgetId = bId,
                        title = "Запрос аналитики",
                        description = "||audit_req||Давид, проведи аудит за $periodName",
                        icon = "david",
                        color = "indigo500",
                        timestamp = reqTime,
                        isRead = true
                    )
                )

                var fullText = ""
                var currentBlockBuffer = ""
                val baseTime = System.currentTimeMillis()
                var blockCount = 0

                aiAuditLoading.value = true

                try {
                    repository.requestAiAuditStream(
                        apiKey = key,
                        periodName = periodName,
                        year = year,
                        filteredTransactions = currentFilteredTransactions,
                        previousTransactions = previousTransactions,
                        activeDebts = accounts.value,
                        activeGoals = goals.value,
                        allTransactions = transactions.value
                    ).collect { chunk ->
                        fullText += chunk
                        currentBlockBuffer += chunk
                        aiAuditResult.value = fullText

                        // Streaming Chunk Accumulator: Slice blocks when paragraph separator (\n\n) appears
                        while (currentBlockBuffer.contains("\n\n")) {
                            val parts = currentBlockBuffer.split("\n\n", limit = 2)
                            val completedBlock = parts[0].trim()
                            currentBlockBuffer = parts.getOrElse(1) { "" }

                            if (completedBlock.isNotBlank() && completedBlock != "ERROR_NO_CONNECTION") {
                                blockCount++
                                val blockId = UUID.randomUUID().toString()
                                val blockTime = baseTime + blockCount * 100L
                                val isFirstBlock = blockCount == 1
                                val blockTitle = if (isFirstBlock) "Жабов Давид (Аналитика)" else "Аналитика"

                                repository.insertNotification(
                                    com.example.data.db.NotificationEntity(
                                        id = blockId,
                                        budgetId = bId,
                                        title = blockTitle,
                                        description = "||audit_block||$completedBlock",
                                        icon = "david",
                                        color = "emerald400",
                                        timestamp = blockTime,
                                        isRead = true
                                    )
                                )
                            }
                        }
                    }

                    // Emit remaining buffer content as the final SMS block
                    val finalBlock = currentBlockBuffer.trim()
                    if (finalBlock.isNotBlank() && finalBlock != "ERROR_NO_CONNECTION") {
                        blockCount++
                        val blockId = UUID.randomUUID().toString()
                        val blockTime = baseTime + blockCount * 100L
                        val isFirstBlock = blockCount == 1
                        val blockTitle = if (isFirstBlock) "Жабов Давид (Аналитика)" else "Аналитика"

                        repository.insertNotification(
                            com.example.data.db.NotificationEntity(
                                id = blockId,
                                budgetId = bId,
                                title = blockTitle,
                                description = "||audit_block||$finalBlock",
                                icon = "david",
                                color = "emerald400",
                                timestamp = blockTime,
                                isRead = true
                            )
                        )
                    }
                } catch (e: Exception) {
                    fullText = "ERROR_NO_CONNECTION"
                    aiAuditResult.value = fullText
                }

                if (fullText.isNotEmpty() && !fullText.contains("🏆 **Достижение: Сбой Сети**") && fullText != "ERROR_NO_CONNECTION") {
                    val currentMeme = currentFilteredTransactions.filter { tx ->
                        tx.type == "expense" && (
                            tx.category.contains("Развлечения", ignoreCase = true) ||
                            tx.category.contains("Прочее", ignoreCase = true) ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("мошеннич") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("крипт") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("казик") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("тарелоч") ||
                            tx.subcategory.lowercase(Locale.getDefault()).contains("альтуш")
                        )
                    }
                    val sillySummaryText = if (currentMeme.isNotEmpty()) {
                        currentMeme.take(3).joinToString("; ") { "${it.subcategory} (${it.amount.toInt()} ₽)" }
                    } else {
                        val topExpense = currentFilteredTransactions.filter { it.type == "expense" }.maxByOrNull { it.amount }
                        if (topExpense != null) "Крупный расход: ${topExpense.category} (${topExpense.amount.toInt()} ₽)" else "Равномерные расходы"
                    }

                    val entity = com.example.data.db.AiAuditEntity(
                        id = UUID.randomUUID().toString(),
                        budgetId = bId,
                        periodType = periodType.name,
                        periodKey = pKey,
                        year = year,
                        month = month,
                        auditText = fullText,
                        sillyExpensesSummary = sillySummaryText,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.saveAudit(entity)
                }
            } finally {
                aiAuditLoading.value = false
            }
        }
    }
}
