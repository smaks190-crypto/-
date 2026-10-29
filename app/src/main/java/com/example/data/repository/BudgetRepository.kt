package com.example.data.repository

import com.example.utils.GlobalConsoleLogger
import com.example.utils.NetworkCallStatus
import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiModelConfig
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.RetrofitClient
import com.example.data.db.AccountDao
import com.example.data.db.AccountEntity
import com.example.data.db.AiAuditDao
import com.example.data.db.AiAuditEntity
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetProfileDao
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.CategoryDao
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalDao
import com.example.data.db.GoalEntity
import com.example.data.db.NotificationDao
import com.example.data.db.NotificationEntity
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BudgetBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val profileName: String? = null,
    val profiles: List<BudgetProfileEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList()
)

class BudgetRepository(
    context: android.content.Context,
    private val budgetProfileDao: BudgetProfileDao,
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val categoryDao: CategoryDao,
    private val aiAuditDao: AiAuditDao,
    private val accountDao: AccountDao,
    private val notificationDao: NotificationDao,
    private val db: AppDatabase,
    private val apiService: GeminiApiService = RetrofitClient.service
) {
    private val context: android.content.Context = context.applicationContext
    val allProfiles: Flow<List<BudgetProfileEntity>> = budgetProfileDao.getAllProfiles()

    fun getAccountsForBudget(budgetId: String): Flow<List<AccountEntity>> =
        accountDao.getAccountsByBudgetId(budgetId).flowOn(Dispatchers.IO)

    fun getCategoriesForBudget(budgetId: String): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByBudgetId(budgetId).flowOn(Dispatchers.IO)

    fun getGoalsForBudget(budgetId: String): Flow<List<GoalEntity>> =
        goalDao.getGoalsByBudgetId(budgetId).flowOn(Dispatchers.IO)

    fun getTransactionsForBudget(budgetId: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsByBudgetId(budgetId).flowOn(Dispatchers.IO)

    fun getNotificationsForBudget(budgetId: String): Flow<List<NotificationEntity>> =
        notificationDao.getNotificationsByBudgetId(budgetId).flowOn(Dispatchers.IO)

    suspend fun transferBetweenAccounts(
        budgetId: String,
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        fromName: String,
        toName: String
    ) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            db.withTransaction {
                accountDao.updateBalance(fromAccountId, -amount)
                accountDao.updateBalance(toAccountId, amount)
                notificationDao.insertNotification(
                    NotificationEntity(
                        budgetId = budgetId,
                        title = "Перевод между счетами",
                        description = "Переведено ${String.format(Locale.US, "%.0f", amount)} ₽ со счета \"$fromName\" на счет \"$toName\"",
                        icon = "repeat",
                        color = "indigo500",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            GlobalConsoleLogger.recordDbOperation(
                operationName = "TRANSFER accounts",
                tableName = "accounts",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "budgetId=$budgetId | $fromName -> $toName",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "TRANSFER accounts",
                tableName = "accounts",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun generateDavidComment(
        apiKey: String,
        type: String,
        category: String,
        subcategory: String,
        amount: Double,
        recentTransactions: List<TransactionEntity>,
        activeDebts: List<AccountEntity> = emptyList(),
        activeGoals: List<GoalEntity> = emptyList(),
        extraContext: String? = null,
        allTransactions: List<TransactionEntity> = emptyList()
    ): String {
        if (apiKey.isBlank()) {
            return generateLocalDavidComment(type, category, amount)
        }

        val debtsTotal = activeDebts.filter { it.type == "debt" && it.balance > 0 }.sumOf { it.balance }
        val goalsTotal = activeGoals.sumOf { it.targetAmount - it.currentAmount }

        val debtsContext = if (debtsTotal > 0) {
            "Пользователь имеет непогашенные долги/кредиты на общую сумму: ${debtsTotal.toInt()} ₽ (${activeDebts.filter { it.type == "debt" && it.balance > 0 }.joinToString { "${it.name}: ${it.balance.toInt()} ₽" }})."
        } else "У пользователя нет активных долгов."

        val goalsContext = if (goalsTotal > 0) {
            "Пользователь копит на цели, осталось собрать: ${goalsTotal.toInt()} ₽ (${activeGoals.filter { it.currentAmount < it.targetAmount }.joinToString { "${it.name}: осталось ${(it.targetAmount - it.currentAmount).toInt()} ₽" }})."
        } else "У пользователя нет активных целей накопления."

        val isDirectDebtOrGoalAction = category.contains("Долг", ignoreCase = true) ||
                category.contains("Кредит", ignoreCase = true) ||
                category.contains("Цель", ignoreCase = true) ||
                category.contains("Копилка", ignoreCase = true)

        val totalExpensesMonth = allTransactions.filter { it.type == "expense" }.sumOf { it.amount }
        val categoryExpensesMonth = allTransactions.filter { it.type == "expense" && it.category.equals(category, ignoreCase = true) }.sumOf { it.amount }

        val userQuery = """
            Пользователь только что добавил операцию в свой бюджет:
            Тип: ${if (type == "expense") "Расход" else "Доход"}
            Категория: $category
            Подкатегория/Название: $subcategory
            Сумма: $amount ₽
            
            Контекст бюджета пользователя:
            - Общие расходы за текущий месяц: ${totalExpensesMonth.toInt()} ₽
            - Расходы конкретно по категории "$category" за месяц: ${categoryExpensesMonth.toInt()} ₽
            - $debtsContext
            - $goalsContext
            ${extraContext?.let { "- Дополнительный контекст: $it" } ?: ""}
            
            Недавние операции пользователя:
            ${recentTransactions.take(5).joinToString("\n") { "- ${it.date}: ${if (it.type == "expense") "Расход" else "Доход"} ${it.amount.toInt()} ₽ (${it.category} / ${it.subcategory})" }}
            
            Напиши ОДИН короткий, саркастичный, ироничный комментарий от лица финансового аудитора Жабова Давида (1-2 предложения, до 15-20 слов, на русском языке, сочный юмор, мемы). 
            Оцени разумность этой конкретной траты или дохода. 
            Если это очевидно глупая или импульсивная трата — высмей её. Если полезный доход — похвали сквозь зубы.
        """.trimIndent()

        val strictness = com.example.notifications.ReminderManager.getAuditorStrictness(context)
        val croakThreshold = com.example.notifications.ReminderManager.getCroakThresholdAmount(context)
        val isCroakEnabled = com.example.notifications.ReminderManager.isCroakAlertEnabled(context)
        val isImpulseTriggered = com.example.notifications.ReminderManager.isImpulseCategoriesWatchEnabled(context) &&
            type == "expense" &&
            com.example.notifications.ReminderManager.isImpulseCategory(category, subcategory)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isNightTriggered = com.example.notifications.ReminderManager.isNightWatchEnabled(context) &&
            type == "expense" &&
            (currentHour >= 23 || currentHour < 5)

        val systemPrompt = "Ты — Жабов Давид 🐸, харизматичный финансовый аудитор.\n" +
                "${strictness.systemPromptDirective}\n" +
                (if (isCroakEnabled && type == "expense" && amount >= croakThreshold) {
                    "ВНИМАНИЕ: Сумма траты ($amount ₽) ПРОБИЛА установленный пользователем «Порог кваканья» ($croakThreshold ₽)! Обязательно отреагируй на превышение порога в рамках своего режима жёсткости!\n"
                } else "") +
                (if (isImpulseTriggered) {
                    "ТРИГГЕР ИМПУЛЬСИВНЫХ КАТЕГОРИЙ: Покупка входит в список дофаминовых/импульсивных трат ($category / $subcategory). Обрати на это особое внимание!\n"
                } else "") +
                (if (isNightTriggered) {
                    "ТРИГГЕР «НОЧНОЙ ДОЗОР»: Операция совершена глубокой ночью (между 23:00 и 05:00). Обязательно упомяни ночной дожор или ночной шопинг!\n"
                } else "") +
                "ВАЖНОЕ ПРАВИЛО: Не упоминай долги и цели при каждой обычной бытовой операции! " +
                "Упоминай текущие долги или цели ТОЛЬКО при необоснованно больших тратах на ненужное, при слишком частых тратах на одну категорию (фастфуд, кофе и т.д.), либо при прямой работе с целями и долгами. " +
                "При создании цели ОБЯЗАТЕЛЬНО разделяй целевую сумму и первый взнос. " +
                "ПРАВИЛО ПРИ ПРЯМОЙ РАБОТЕ С ДОЛГАМИ/ЦЕЛЯМИ (когда операция касается долгов или целей):\n" +
                "- Если пользователь ГАСИТ долг: обязательно похвали его за правильное движение к финансовой свободе!\n" +
                "- Если пользователь БЕРЁТ новый долг/кредит: \n" +
                "     - Если это первый долг — язвительно предупреди о кабале;\n" +
                "     - При взятии/выдаче нового долга при наличии не закрытых старых обязательно выкажи возмущение этим безумием!\n" +
                "- В остальных обычных текущих операциях НЕ упоминай долги и цели, а саркастично комментируй саму покупку или доход."

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userQuery)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        val modelsToTry = GeminiModelConfig.DAVID_REACTION_MODELS
        return GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = apiService,
            apiKey = apiKey,
            request = request,
            operationType = "Реакция Давида Жабова",
            operationPrefix = "DAVID",
            details = "cat=$category",
            models = modelsToTry
        ) ?: generateLocalDavidComment(type, category, amount)
    }

    suspend fun generateDavidCommentMulti(
        apiKey: String,
        operations: List<ParsedVoiceOperation>,
        activeDebts: List<AccountEntity> = emptyList(),
        activeGoals: List<GoalEntity> = emptyList()
    ): String {
        if (operations.isEmpty()) return "Ква! Ничего не добавлено."
        if (operations.size == 1) {
            val op = operations.first()
            return generateDavidComment(
                apiKey = apiKey,
                type = op.type,
                category = op.category,
                subcategory = op.subcategory,
                amount = op.amount,
                recentTransactions = emptyList(),
                activeDebts = activeDebts,
                activeGoals = activeGoals
            )
        }

        if (apiKey.isBlank()) {
            return "Ого, сразу ${operations.size} операций на общую сумму ${String.format("%.0f", operations.sumOf { it.amount })} ₽! Кошелек в шоке, но записать надо было."
        }

        val debtsTotal = activeDebts.filter { it.type == "debt" && it.balance > 0 }.sumOf { it.balance }
        val goalsTotal = activeGoals.sumOf { it.targetAmount - it.currentAmount }

        val debtsContext = if (debtsTotal > 0) {
            "У пользователя есть непогашенные долги: ${debtsTotal.toInt()} ₽."
        } else ""

        val goalsContext = if (goalsTotal > 0) {
            "Пользователь копит на цели, осталось: ${goalsTotal.toInt()} ₽."
        } else ""

        val opsListString = operations.joinToString("\n") { op ->
            "- ${if (op.type == "expense") "Расход" else "Доход"}: ${op.amount.toInt()} ₽ (${op.category} / ${op.subcategory})"
        }

        val userQuery = "Пользователь за один раз добавил сразу несколько операций:\n" +
                opsListString + "\n" +
                debtsContext + "\n" +
                goalsContext +
                "Проанализируй весь этот набор операций в совокупности. Напиши ОДИН общий, короткий, саркастичный, ироничный комментарий от лица циничного финансового аудитора Жабова Давида (до 20-25 слов, на русском языке, сочный юмор, мемы), высмеивающий или комментирующий эту общую картину его трат/доходов за раз. Относись к пользователю с иронией."

        val strictness = com.example.notifications.ReminderManager.getAuditorStrictness(context)
        val systemPrompt = "Ты — Жабов Давид 🐸, харизматичный финансовый аудитор. ${strictness.systemPromptDirective}"

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userQuery)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        val modelsToTry = GeminiModelConfig.DAVID_REACTION_MODELS
        return GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = apiService,
            apiKey = apiKey,
            request = request,
            operationType = "Групповая реакция Давида Жабова",
            operationPrefix = "DAVID-M",
            details = "ops=${operations.size}",
            models = modelsToTry
        ) ?: "Добавлено ${operations.size} операций на общую сумму ${String.format("%.0f", operations.sumOf { it.amount })} ₽."
    }

    suspend fun generateUserPhraseMulti(
        apiKey: String,
        operations: List<ParsedVoiceOperation>
    ): String {
        if (operations.isEmpty()) return ""
        if (operations.size == 1) {
            val op = operations.first()
            return if (op.title.isNotBlank()) op.title else "${op.category} ${String.format("%.0f", op.amount)} ₽"
        }

        val totalAmount = operations.sumOf { it.amount }
        val opsListString = operations.joinToString(", ") { op ->
            val titleOrSub = if (op.subcategory.isNotBlank()) op.subcategory else op.title
            "$titleOrSub ${String.format("%.0f", op.amount)} ₽"
        }

        if (apiKey.isBlank()) {
            return "Добавлено ${operations.size} операций на ${String.format("%.0f", totalAmount)} ₽: $opsListString"
        }

        val prompt = "Пользователь добавил пачку операций:\n$opsListString\n" +
                "Сформируй ОДНУ короткую, естественную человеческую фразу на русском языке (от первого лица, до 10-12 слов), описывающую эти действия вместе. Например: «Закупился в супермаркете и оплатил такси до дома» или «Оплатил коммуналку и перевел на накопительный счет»."

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )

        try {
            val response = apiService.generateContent("gemini-2.5-flash", apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?.trim()
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                ?.removePrefix("«")
                ?.removeSuffix("»")
                ?.trim()

            if (!text.isNullOrEmpty()) {
                return text
            }
        } catch (_: Exception) {}

        return "Добавлено ${operations.size} операций: $opsListString"
    }

    suspend fun autoDetectCategoryWithAi(
        title: String,
        apiKey: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        type: String
    ): String? {
        if (apiKey.isBlank() || title.isBlank()) return null

        val availableCategories = if (type == "income") incomeCategories else expenseCategories
        if (availableCategories.isEmpty()) return null

        val prompt = """
            Определи наиболее подходящую категорию для финансовой операции: "$title".
            Тип операции: ${if (type == "income") "Доход" else "Расход"}.
            
            Доступные категории:
            ${availableCategories.joinToString(", ")}
            
            Правила:
            1. Выбери ТОЧНО ОДНО название категории из списка выше.
            2. Не придумывай новые категории, верни строго точное совпадение из списка.
            3. Если ни одна категория не подходит идеально, выбери наиболее близкую по смыслу или "Прочее" / "Другое" (если есть в списке).
            4. В ответе напиши ТОЛЬКО название выбранной категории и ничего больше. Без кавычек, точек и пояснений.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )

        val modelsToTry = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash"
        )
        val opId = GlobalConsoleLogger.newOperationId("CAT-AI")

        for ((idx, model) in modelsToTry.withIndex()) {
            val attempt = idx + 1
            val startMs = System.currentTimeMillis()
            try {
                val response = apiService.generateContent(model, apiKey, request)
                if (response.error == null) {
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?.trim()
                        ?.removePrefix("\"")
                        ?.removeSuffix("\"")
                        ?.removePrefix("«")
                        ?.removeSuffix("»")
                        ?.trim()
                    if (!responseText.isNullOrEmpty()) {
                        GlobalConsoleLogger.recordNetworkCall(
                            operationType = "Автоподбор категории (ИИ)",
                            modelName = model,
                            durationMs = System.currentTimeMillis() - startMs,
                            status = NetworkCallStatus.SUCCESS,
                            httpCode = 200,
                            attemptNumber = attempt,
                            totalModelsInChain = modelsToTry.size,
                            details = "result=$responseText",
                            operationId = opId
                        )
                        return responseText
                    }
                } else {
                    GlobalConsoleLogger.recordNetworkCall(
                        operationType = "Автоподбор категории (ИИ)",
                        modelName = model,
                        durationMs = System.currentTimeMillis() - startMs,
                        status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                        httpCode = response.error.code,
                        errorType = response.error.status ?: "GeminiError",
                        errorMessage = response.error.message,
                        attemptNumber = attempt,
                        totalModelsInChain = modelsToTry.size,
                        operationId = opId
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                GlobalConsoleLogger.recordNetworkCall(
                    operationType = "Автоподбор категории (ИИ)",
                    modelName = model,
                    durationMs = System.currentTimeMillis() - startMs,
                    status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                    errorType = e.javaClass.simpleName,
                    errorMessage = e.localizedMessage ?: e.message,
                    attemptNumber = attempt,
                    totalModelsInChain = modelsToTry.size,
                    operationId = opId
                )
            }
        }

        return null
    }

    private fun generateLocalDavidComment(type: String, category: String, amount: Double): String {
        return if (type == "expense") {
            when {
                amount > 10000 -> "Ого, минус ${amount.toInt()} ₽ на $category! Надеюсь, это окупится, а не уйдёт в пустоту."
                amount > 3000 -> "Минус ${amount.toInt()} ₽ на $category. Твой баланс слегка похудел, держи руку на пульсе!"
                category.contains("Кафе", ignoreCase = true) || category.contains("Еда", ignoreCase = true) ->
                    "Снова вкусняшки за ${amount.toInt()} ₽? Желудок рад, а кошелёк грустит."
                category.contains("Такси", ignoreCase = true) ->
                    "Пешком ходить полезно, но ладно — ${amount.toInt()} ₽ на такси списано."
                else -> "${amount.toInt()} ₽ на $category учтены. Финансовая дисциплина требует жертв!"
            }
        } else {
            when {
                amount > 50000 -> "Плюс ${amount.toInt()} ₽! Вот это я понимаю улов. Главное — не спусти всё в первый же день!"
                amount > 10000 -> "Отличное пополнение: +${amount.toInt()} ₽. Положи часть в копилку, не пожалеешь!"
                else -> "+${amount.toInt()} ₽ в копилку бюджета. Копейка рубль бережёт, ква!"
            }
        }
    }

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    val allGoals: Flow<List<GoalEntity>> = goalDao.getAllGoals()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    suspend fun getActiveBudgetProfile(): BudgetProfileEntity? {
        return budgetProfileDao.getActiveProfile()
    }

    suspend fun setActiveBudgetProfile(id: String) {
        db.withTransaction {
            budgetProfileDao.clearActiveProfile()
            budgetProfileDao.setActiveProfile(id)
        }
    }

    suspend fun createProfile(name: String): BudgetProfileEntity {
        val newProfile = BudgetProfileEntity(name = name)
        budgetProfileDao.insertProfile(newProfile)
        initializeDefaultCategories(newProfile.id)
        initializeDefaultAccounts(newProfile.id)
        return newProfile
    }

    suspend fun renameProfile(id: String, newName: String) {
        budgetProfileDao.renameProfile(id, newName)
    }

    suspend fun deleteProfile(id: String) {
        db.withTransaction {
            transactionDao.deleteTransactionsByBudgetId(id)
            categoryDao.deleteCategoriesByBudgetId(id)
            goalDao.deleteGoalsByBudgetId(id)
            accountDao.deleteAccountsByBudgetId(id)
            budgetProfileDao.deleteProfileById(id)
        }
    }

    suspend fun ensureDefaultProfileExists() {
        val profiles = budgetProfileDao.getAllProfilesSync()
        if (profiles.isEmpty()) {
            val defaultProfile = BudgetProfileEntity(
                id = "default",
                name = "Основной бюджет",
                isActive = true
            )
            budgetProfileDao.insertProfile(defaultProfile)
            initializeDefaultCategories("default")
            initializeDefaultAccounts("default")
        }
    }


    suspend fun insertTransaction(transaction: TransactionEntity) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            transactionDao.insertTransaction(transaction)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "type=${transaction.type.uppercase()} | cat=${transaction.category}",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
        if (transaction.type.equals("expense", ignoreCase = true) &&
            !transaction.category.equals("Сбережения", ignoreCase = true)
        ) {
            withContext(Dispatchers.Main) {
                com.example.notifications.ReminderManager.evaluateAndTriggerCroakAlertIfNeeded(
                    context = context,
                    type = transaction.type,
                    category = transaction.category,
                    subcategory = transaction.subcategory,
                    amount = transaction.amount
                )
            }
        }
    }

    suspend fun deleteTransaction(id: String) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            transactionDao.deleteTransactionById(id)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "id=$id",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE transaction",
                tableName = "transactions",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun insertGoal(goal: GoalEntity) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            goalDao.insertGoal(goal)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "name=${goal.name}",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun deleteGoal(id: String) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            goalDao.deleteGoalById(id)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "id=$id",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE goal",
                tableName = "goals",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun insertCategory(category: CategoryEntity) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            categoryDao.insertCategory(category)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "name=${category.name} (${category.type})",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "INSERT category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun deleteCategory(id: String) {
        val opId = GlobalConsoleLogger.newOperationId("DB")
        val startMs = System.currentTimeMillis()
        try {
            categoryDao.deleteCategoryById(id)
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = true,
                details = "id=$id",
                operationId = opId
            )
        } catch (e: Exception) {
            GlobalConsoleLogger.recordDbOperation(
                operationName = "DELETE category",
                tableName = "categories",
                durationMs = System.currentTimeMillis() - startMs,
                isSuccess = false,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                operationId = opId
            )
            throw e
        }
    }

    suspend fun initializeDefaultCategories(budgetId: String = "default") {
        val existing = categoryDao.getCategoriesByBudgetId(budgetId).first()
        if (existing.isEmpty()) {
            val defaults = listOf(
                CategoryEntity(budgetId = budgetId, name = "Продукты и Еда", iconName = "fastfood", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Транспорт и Такси", iconName = "directions_car", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Жилье и ЖКХ", iconName = "home", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Развлечения", iconName = "sports_esports", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Покупки и Одежда", iconName = "shopping_bag", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Здоровье и Аптека", iconName = "medical_services", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Кафе и Рестораны", iconName = "restaurant", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Связь и Интернет", iconName = "wifi", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Подписки и Сервисы", iconName = "subscriptions", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Красота и Уход", iconName = "spa", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Спорт и Фитнес", iconName = "fitness_center", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Образование и Книги", iconName = "school", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Подарки и Праздники", iconName = "card_giftcard", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Домашние животные", iconName = "pets", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Путешествия", iconName = "flight", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Ремонт и Мебель", iconName = "build", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Благотворительность", iconName = "volunteer_activism", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Сбережения и Инвестиции", iconName = "savings", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Кредиты и Долги", iconName = "credit_card", type = "expense"),
                CategoryEntity(budgetId = budgetId, name = "Прочие расходы", iconName = "more_horiz", type = "expense"),

                CategoryEntity(budgetId = budgetId, name = "Зарплата", iconName = "payments", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Подработка и Фриланс", iconName = "laptop", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Пассивный доход", iconName = "trending_up", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Подарки", iconName = "redeem", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Продажа вещей", iconName = "storefront", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Возврат долгов", iconName = "assignment_return", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Кэшбэк и Проценты", iconName = "percent", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Стипендия или Пособие", iconName = "account_balance", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Инвестиционный доход", iconName = "show_chart", type = "income"),
                CategoryEntity(budgetId = budgetId, name = "Прочие доходы", iconName = "attach_money", type = "income")
            )
            categoryDao.insertCategories(defaults)
        }
    }

    suspend fun initializeDefaultAccounts(budgetId: String = "default") {
        val existing = accountDao.getAccountsByBudgetId(budgetId).first()
        if (existing.isEmpty()) {
            val defaults = listOf(
                AccountEntity(budgetId = budgetId, name = "Наличные", balance = 0.0, iconName = "account_balance_wallet", type = "cash"),
                AccountEntity(budgetId = budgetId, name = "Основная карта", balance = 0.0, iconName = "credit_card", type = "card"),
                AccountEntity(budgetId = budgetId, name = "Накопительный счет", balance = 0.0, iconName = "savings", type = "savings")
            )
            accountDao.insertAccounts(defaults)
        }
    }

    suspend fun loadDemoData(budgetId: String = "default") = withContext(Dispatchers.IO) {
        initializeDefaultCategories(budgetId)

        try {
            val demoTransactions = listOf(
                TransactionEntity(budgetId = budgetId, type = "expense", date = "2026-08-01", category = "Продукты и Еда", subcategory = "Супермаркет", amount = 1250.0),
                TransactionEntity(budgetId = budgetId, type = "expense", date = "2026-08-02", category = "Транспорт и Такси", subcategory = "Метро", amount = 150.0),
                TransactionEntity(budgetId = budgetId, type = "expense", date = "2026-08-03", category = "Развлечения", subcategory = "Кино", amount = 600.0),
                TransactionEntity(budgetId = budgetId, type = "income", date = "2026-08-05", category = "Зарплата", subcategory = "Аванс", amount = 45000.0),
                TransactionEntity(budgetId = budgetId, type = "expense", date = "2026-08-07", category = "Жилье и ЖКХ", subcategory = "Аренда", amount = 25000.0),
                TransactionEntity(budgetId = budgetId, type = "expense", date = "2026-08-10", category = "Кафе и Рестораны", subcategory = "Обед с коллегами", amount = 850.0),
                TransactionEntity(budgetId = budgetId, type = "income", date = "2026-08-12", category = "Подработка и Фриланс", subcategory = "Верстка лендинга", amount = 8000.0),
                TransactionEntity(budgetId = budgetId, type = "expense", date = "2026-08-14", category = "Покупки и Одежда", subcategory = "Кроссовки", amount = 4500.0),
                TransactionEntity(budgetId = budgetId, type = "expense", date = "2026-08-15", category = "Связь и Интернет", subcategory = "Тариф и Wi-Fi", amount = 900.0)
            )
            transactionDao.insertTransactions(demoTransactions)
        } catch (_: Exception) {}
    }


    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val currentProfiles = allProfiles.first()
        val profile = currentProfiles.firstOrNull()
        val txs = allTransactions.first()
        val cats = allCategories.first()
        val gls = allGoals.first()
        val accs = allAccounts.first()

        val backup = BudgetBackup(
            profileName = profile?.name ?: "Мой Бюджет",
            profiles = currentProfiles,
            accounts = accs,
            categories = cats,
            goals = gls,
            transactions = txs
        )
        val json = backupAdapter.toJson(backup)
        try {
            storageFile.writeText(json)
        } catch (_: Exception) {}
        json
    }

    suspend fun exportJsonForBudget(budgetId: String): String = withContext(Dispatchers.IO) {
        val profile = allProfiles.first().firstOrNull { it.id == budgetId }
        val profileName = profile?.name
        val txs = transactionDao.getTransactionsByBudgetId(budgetId).first()
        val cats = categoryDao.getCategoriesByBudgetId(budgetId).first()
        val gls = goalDao.getGoalsByBudgetId(budgetId).first()
        val accs = accountDao.getAccountsByBudgetId(budgetId).first()

        val backup = BudgetBackup(
            profileName = profileName,
            profiles = if (profile != null) listOf(profile) else emptyList(),
            accounts = accs,
            categories = cats,
            goals = gls,
            transactions = txs
        )
        val json = backupAdapter.toJson(backup)
        try {
            val file = java.io.File(context.filesDir, "budget_${budgetId}.json")
            file.writeText(json)
        } catch (_: Exception) {}
        json
    }

    suspend fun importBackupAsNewBudget(jsonStr: String, customName: String? = null): BudgetProfileEntity? = withContext(Dispatchers.IO) {
        try {
            val backup = backupAdapter.fromJson(jsonStr) ?: return@withContext null
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            val name = customName ?: backup.profileName?.takeIf { it.isNotBlank() } ?: "Бюджет из копии ($dateStr)"
            val profile = createProfile(name)

            if (backup.categories.isNotEmpty()) {
                val newCategories = backup.categories.map { it.copy(id = UUID.randomUUID().toString(), budgetId = profile.id) }
                categoryDao.deleteCategoriesByBudgetId(profile.id)
                categoryDao.insertCategories(newCategories)
            }

            if (backup.accounts.isNotEmpty()) {
                val newAccounts = backup.accounts.map { it.copy(id = UUID.randomUUID().toString(), budgetId = profile.id) }
                accountDao.deleteAccountsByBudgetId(profile.id)
                accountDao.insertAccounts(newAccounts)
            }

            if (backup.goals.isNotEmpty()) {
                val newGoals = backup.goals.map { it.copy(id = UUID.randomUUID().toString(), budgetId = profile.id) }
                goalDao.insertGoals(newGoals)
            }

            if (backup.transactions.isNotEmpty()) {
                val newTransactions = backup.transactions.map { it.copy(id = UUID.randomUUID().toString(), budgetId = profile.id) }
                transactionDao.insertTransactions(newTransactions)
            }

            profile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun importJson(jsonStr: String, targetBudgetId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = backupAdapter.fromJson(jsonStr) ?: return@withContext false
            clearAllData()

            val bId = targetBudgetId ?: "default"

            if (backup.profiles.isNotEmpty()) {
                budgetProfileDao.insertProfiles(backup.profiles)
            } else {
                ensureDefaultProfileExists()
            }

            if (backup.categories.isNotEmpty()) {
                val cats = if (targetBudgetId != null) backup.categories.map { it.copy(budgetId = bId) } else backup.categories
                categoryDao.insertCategories(cats)
            }
            if (backup.goals.isNotEmpty()) {
                val gls = if (targetBudgetId != null) backup.goals.map { it.copy(budgetId = bId) } else backup.goals
                goalDao.insertGoals(gls)
            }
            if (backup.accounts.isNotEmpty()) {
                val accs = if (targetBudgetId != null) backup.accounts.map { it.copy(budgetId = bId) } else backup.accounts
                accountDao.insertAccounts(accs)
            }
            if (backup.transactions.isNotEmpty()) {
                val txs = if (targetBudgetId != null) backup.transactions.map { it.copy(budgetId = bId) } else backup.transactions
                transactionDao.insertTransactions(txs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearAllData() {
        val currentBudget = getActiveBudgetProfile()?.id ?: "default"
        db.withTransaction {
            transactionDao.deleteTransactionsByBudgetId(currentBudget)
            categoryDao.deleteCategoriesByBudgetId(currentBudget)
            goalDao.deleteGoalsByBudgetId(currentBudget)
            accountDao.deleteAccountsByBudgetId(currentBudget)
            aiAuditDao.deleteAllAudits()
        }
        initializeDefaultCategories(currentBudget)
        initializeDefaultAccounts(currentBudget)
    }

    suspend fun clearOnlyTransactions() {
        val currentBudget = getActiveBudgetProfile()?.id ?: "default"
        db.withTransaction {
            transactionDao.deleteTransactionsByBudgetId(currentBudget)
            aiAuditDao.deleteAllAudits()
        }
    }

    fun requestAiAuditStream(
        apiKey: String,
        periodName: String,
        year: Int,
        filteredTransactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<AccountEntity> = emptyList(),
        activeGoals: List<GoalEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList()
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Укажите API ключ Gemini в настройках приложения для получения аудита.")
        }

        val totalIncome = filteredTransactions.filter { it.type == "income" }.sumOf { it.amount }
        val totalExpense = filteredTransactions.filter { it.type == "expense" }.sumOf { it.amount }
        val prevIncome = previousTransactions.filter { it.type == "income" }.sumOf { it.amount }
        val prevExpense = previousTransactions.filter { it.type == "expense" }.sumOf { it.amount }

        val expensesByCategory = filteredTransactions.filter { it.type == "expense" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val topCategory = expensesByCategory.maxByOrNull { it.value }

        val debtsTotal = activeDebts.filter { it.type == "debt" && it.balance > 0 }.sumOf { it.balance }
        val goalsTotal = activeGoals.sumOf { it.targetAmount - it.currentAmount }

        val debtsInfo = if (debtsTotal > 0) {
            "Активные долги/кредиты пользователя на сумму ${debtsTotal.toInt()} ₽: " +
                    activeDebts.filter { it.type == "debt" && it.balance > 0 }.joinToString { "${it.name}: ${it.balance.toInt()} ₽" }
        } else "Долгов нет."

        val goalsInfo = if (goalsTotal > 0) {
            "Финансовые цели пользователя (осталось собрать ${goalsTotal.toInt()} ₽): " +
                    activeGoals.filter { it.currentAmount < it.targetAmount }.joinToString { "${it.name}: осталось ${(it.targetAmount - it.currentAmount).toInt()} ₽" }
        } else "Активных целей накопления нет."

        val prompt = """
            Проведи подробный, язвительный, остроумный финансовый аудит расходов и доходов пользователя за период: $periodName ($year год).
            
            Показатели за период:
            - Доход: $totalIncome ₽ (в предыдущем аналогичном периоде: $prevIncome ₽)
            - Расход: $totalExpense ₽ (в предыдущем аналогичном периоде: $prevExpense ₽)
            - Чистый итог (баланс): ${totalIncome - totalExpense} ₽
            - Самая затратная категория: ${topCategory?.key ?: "нет"} (${topCategory?.value ?: 0.0} ₽)
            - Структура расходов по категориям:
            ${expensesByCategory.entries.joinToString("\n") { "  * ${it.key}: ${it.value} ₽" }}
            
            Дополнительный финансовый контекст:
            - $debtsInfo
            - $goalsInfo
            
            Сформируй структурированный отчет в формате Markdown со следующими обязательными разделами:
            # Главный Вердикт
            Краткий, хлесткий диагноз финансовому поведению.
            
            ## Цифры и Динамика
            Анализ доходов, расходов, разницы с прошлым периодом, сравнение долей.
            
            ## Прожарка Транжиры
            Высмей самые нелепые, импульсивные и раздутые статьи расходов. Если есть долги при больших тратах на развлечения/кафе — непременно укажи на это безумие.
            
            ## Ачивки и Достижения
            Придумай 2-3 ироничных или хвалебных «достижения» за этот период (например, «Спонсор кофейной мафии», «Мастер выживания»).
            
            ## Выводы и Рекомендации
            Краткий финальный совет, как перестать банкротить себя.
        """.trimIndent()

        val strictness = com.example.notifications.ReminderManager.getAuditorStrictness(context)
        val croakThreshold = com.example.notifications.ReminderManager.getCroakThresholdAmount(context)
        val systemPrompt = "Ты — Давид Жабов 🐸, высокоэрудированный финансовый аудитор.\n" +
                "${strictness.systemPromptDirective}\n" +
                "Порог крупной разовой траты («Порог кваканья» пользователя): $croakThreshold ₽. Обращай особое внимание на операции выше этой суммы.\n\n" +
                "Дели свой ответ на короткие, законченные по смыслу блоки (как отдельные СМС-сообщения). Разделяй каждый блок двумя переносами строк (\\n\\n). Не пиши всё одним монолитным текстом.\n\n" +
                "РОЛЬ И СТИЛЬ:\n" +
                "- Виртуозно миксуй 3 элемента:\n" +
                "  а) популярные классические и самые актуальные интернет-мемы, тренды и вирусы поп-культуры (от культовых мемов прошлых лет до свежих трендов и забавных ситуаций),\n" +
                "  б) изящные отсылки к классической литературе (Гоголь, Чехов, Достоевский, Булгаков, Ильф и Петров, Шекспир и др.) и историческим анекдотам,\n" +
                "  в) конкретные финансовые цифры из реального бюджета пользователя.\n" +
                "- Вставляй точные и остроумные цитаты великих авторов или переиначивай классические сюжеты под реалии трат пользователя.\n" +
                "- Сохраняй глубокую финансовую экспертизу: за едким юмором должны стоять безупречный математический расчет, выявление скрытых утечек бюджета и дельные советы по инвестициям и накоплениям.\n" +
                "- Отвечай исключительно на русском языке, живо, сочно, интеллигентно и с непревзойденной иронией."

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        var lastExceptionMessage = ""
        val modelsToTry = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash"
        )

        val opId = GlobalConsoleLogger.newOperationId("AUDIT")
        var success = false
        for ((idx, model) in modelsToTry.withIndex()) {
            val attempt = idx + 1
            val startMs = System.currentTimeMillis()
            try {
                val response = apiService.streamGenerateContent(model, apiKey, "sse", request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        body.byteStream().bufferedReader().useLines { lines ->
                            for (line in lines) {
                                if (line.startsWith("data:")) {
                                    val json = line.removePrefix("data:").trim()
                                    if (json == "[DONE]") break
                                    try {
                                        val chunk = moshi.adapter(GeminiResponse::class.java).fromJson(json)
                                        val text = chunk?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                        if (!text.isNullOrEmpty()) {
                                            emit(text)
                                            success = true
                                        }
                                    } catch (_: Exception) {
                                        // Ignore JSON parsing exceptions for non-json lines or incomplete data
                                    }
                                }
                            }
                        }
                        if (success) {
                            GlobalConsoleLogger.recordNetworkCall(
                                operationType = "Аудит бюджета (SSE Stream)",
                                modelName = model,
                                durationMs = System.currentTimeMillis() - startMs,
                                status = NetworkCallStatus.SUCCESS,
                                httpCode = response.code(),
                                attemptNumber = attempt,
                                totalModelsInChain = modelsToTry.size,
                                details = "period=$periodName",
                                operationId = opId
                            )
                            return@flow
                        }
                    }
                } else {
                    lastExceptionMessage = "HTTP ${response.code()}: ${response.message()}"
                    GlobalConsoleLogger.recordNetworkCall(
                        operationType = "Аудит бюджета (SSE Stream)",
                        modelName = model,
                        durationMs = System.currentTimeMillis() - startMs,
                        status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                        httpCode = response.code(),
                        errorType = "HTTP_${response.code()}",
                        errorMessage = response.message(),
                        attemptNumber = attempt,
                        totalModelsInChain = modelsToTry.size,
                        details = "period=$periodName",
                        operationId = opId
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastExceptionMessage = e.message ?: e.toString()
                GlobalConsoleLogger.recordNetworkCall(
                    operationType = "Аудит бюджета (SSE Stream)",
                    modelName = model,
                    durationMs = System.currentTimeMillis() - startMs,
                    status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                    errorType = e.javaClass.simpleName,
                    errorMessage = e.localizedMessage ?: e.message,
                    attemptNumber = attempt,
                    totalModelsInChain = modelsToTry.size,
                    details = "period=$periodName",
                    operationId = opId
                )
            }
        }

        throw IllegalStateException("Не удалось получить аудит от моделей Gemini: $lastExceptionMessage")
    }.flowOn(Dispatchers.IO)

    suspend fun requestAiAudit(
        apiKey: String,
        periodName: String,
        year: Int,
        filteredTransactions: List<TransactionEntity>,
        previousTransactions: List<TransactionEntity> = emptyList(),
        activeDebts: List<AccountEntity> = emptyList(),
        activeGoals: List<GoalEntity> = emptyList(),
        allTransactions: List<TransactionEntity> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Укажите API ключ Gemini в настройках приложения для получения аудита."))
        }

        val totalIncome = filteredTransactions.filter { it.type == "income" }.sumOf { it.amount }
        val totalExpense = filteredTransactions.filter { it.type == "expense" }.sumOf { it.amount }
        val prevIncome = previousTransactions.filter { it.type == "income" }.sumOf { it.amount }
        val prevExpense = previousTransactions.filter { it.type == "expense" }.sumOf { it.amount }

        val expensesByCategory = filteredTransactions.filter { it.type == "expense" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val topCategory = expensesByCategory.maxByOrNull { it.value }

        val debtsTotal = activeDebts.filter { it.type == "debt" && it.balance > 0 }.sumOf { it.balance }
        val goalsTotal = activeGoals.sumOf { it.targetAmount - it.currentAmount }

        val debtsInfo = if (debtsTotal > 0) {
            "Активные долги/кредиты пользователя на сумму ${debtsTotal.toInt()} ₽: " +
                    activeDebts.filter { it.type == "debt" && it.balance > 0 }.joinToString { "${it.name}: ${it.balance.toInt()} ₽" }
        } else "Долгов нет."

        val goalsInfo = if (goalsTotal > 0) {
            "Финансовые цели пользователя (осталось собрать ${goalsTotal.toInt()} ₽): " +
                    activeGoals.filter { it.currentAmount < it.targetAmount }.joinToString { "${it.name}: осталось ${(it.targetAmount - it.currentAmount).toInt()} ₽" }
        } else "Активных целей накопления нет."

        val prompt = """
            Проведи подробный, язвительный, остроумный финансовый аудит расходов и доходов пользователя за период: $periodName ($year год).
            
            Показатели за период:
            - Доход: $totalIncome ₽ (в предыдущем аналогичном периоде: $prevIncome ₽)
            - Расход: $totalExpense ₽ (в предыдущем аналогичном периоде: $prevExpense ₽)
            - Чистый итог (баланс): ${totalIncome - totalExpense} ₽
            - Самая затратная категория: ${topCategory?.key ?: "нет"} (${topCategory?.value ?: 0.0} ₽)
            - Структура расходов по категориям:
            ${expensesByCategory.entries.joinToString("\n") { "  * ${it.key}: ${it.value} ₽" }}
            
            Дополнительный финансовый контекст:
            - $debtsInfo
            - $goalsInfo
            
            Сформируй структурированный отчет в формате Markdown со следующими обязательными разделами:
            # Главный Вердикт
            Краткий, хлесткий диагноз финансовому поведению.
            
            ## Цифры и Динамика
            Анализ доходов, расходов, разницы с прошлым периодом, сравнение долей.
            
            ## Прожарка Транжиры
            Высмей самые нелепые, импульсивные и раздутые статьи расходов. Если есть долги при больших тратах на развлечения/кафе — непременно укажи на это безумие.
            
            ## Ачивки и Достижения
            Придумай 2-3 ироничных или хвалебных «достижения» за этот период (например, «Спонсор кофейной мафии», «Мастер выживания»).
            
            ## Выводы и Рекомендации
            Краткий финальный совет, как перестать банкротить себя.
        """.trimIndent()

        val strictness = com.example.notifications.ReminderManager.getAuditorStrictness(context)
        val croakThreshold = com.example.notifications.ReminderManager.getCroakThresholdAmount(context)
        val systemPrompt = "Ты — Давид Жабов 🐸, высокоэрудированный финансовый аудитор.\n" +
                "${strictness.systemPromptDirective}\n" +
                "Порог крупной разовой траты («Порог кваканья» пользователя): $croakThreshold ₽. Обращай особое внимание на операции выше этой суммы.\n\n" +
                "Дели свой ответ на короткие, законченные по смыслу блоки (как отдельные СМС-сообщения). Разделяй каждый блок двумя переносами строк (\\n\\n). Не пиши всё одним монолитным текстом.\n\n" +
                "РОЛЬ И СТИЛЬ:\n" +
                "- Виртуозно миксуй 3 элемента:\n" +
                "  а) популярные классические и самые актуальные интернет-мемы, тренды и вирусы поп-культуры (от культовых мемов прошлых лет до свежих трендов и забавных ситуаций),\n" +
                "  б) изящные отсылки к классической литературе (Гоголь, Чехов, Достоевский, Булгаков, Ильф и Петров, Шекспир и др.) и историческим анекдотам,\n" +
                "  в) конкретные финансовые цифры из реального бюджета пользователя.\n" +
                "- Вставляй точные и остроумные цитаты великих авторов или переиначивай классические сюжеты под реалии трат пользователя.\n" +
                "- Сохраняй глубокую финансовую экспертизу: за едким юмором должны стоять безупречный математический расчет, выявление скрытых утечек бюджета и дельные советы по инвестициям и накоплениям.\n" +
                "- Отвечай исключительно на русском языке, живо, сочно, интеллигентно и с непревзойденной иронией."

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        var lastExceptionMessage = ""

        val modelsToTry = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash"
        )

        for (model in modelsToTry) {
            try {
                val response = apiService.generateContent(model, apiKey, request)
                if (response.error == null) {
                    val fullText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!fullText.isNullOrEmpty()) {
                        saveAuditToDb(periodName, year, fullText)
                        return@withContext Result.success(fullText)
                    }
                } else {
                    lastExceptionMessage = response.error.message ?: "Unknown error"
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastExceptionMessage = e.message ?: e.toString()
            }
        }

        Result.failure(IllegalStateException("Не удалось получить аудит от моделей Gemini: $lastExceptionMessage"))
    }

    private suspend fun saveAuditToDb(period: String, year: Int, text: String) {
        val auditEntity = AiAuditEntity(
            period = period,
            year = year,
            reportText = text
        )
        aiAuditDao.insertAudit(auditEntity)
    }

    fun getSavedAudit(period: String, year: Int): Flow<AiAuditEntity?> {
        return aiAuditDao.getAuditByPeriodAndYear(period, year)
    }

    suspend fun parseVoiceOperations(
        voiceText: String,
        apiKey: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ): List<ParsedVoiceOperation> = withContext(Dispatchers.IO) {
        val trimmedText = voiceText.trim()
        if (trimmedText.isEmpty()) return@withContext emptyList()

        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Для анализа текста с помощью ИИ укажите API ключ Gemini в настройках приложения.")
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        val dayOfWeekStr = SimpleDateFormat("EEEE", Locale("ru")).format(Date())

        val systemPrompt = "Вы — экспертный финансовый ассистент. Проанализируйте текст пользователя на русском языке и извлеките из него ВСЕ упомянутые финансовые операции (доходы и расходы).\n" +
                "Разделите текст на отдельные операции и для каждой выделите название, тип (income или expense), сумму (число), категорию, подкатегорию (конкретное название покупки/источника) и дату (если упомянута).\n" +
                "Текущая дата сегодня: $todayStr (день недели: $dayOfWeekStr).\n" +
                "ПРАВИЛА ДЛЯ ДАТЫ ('date'):\n" +
                "- Если пользователь говорит 'вчера', вычисли дату за день до сегодняшней ($todayStr).\n" +
                "- Если пользователь говорит 'позавчера', вычисли дату за 2 дня до сегодняшней ($todayStr).\n" +
                "- Если пользователь указывает день недели или число (например 'в прошлый вторник', '15 июля'), вычисли соответствующую дату в формате YYYY-MM-DD.\n" +
                "- Если дата не упомянута, укажи текущую дату '$todayStr'.\n" +
                "ОСОБОЕ ПРАВИЛО ДЛЯ ДЕТАЛИЗАЦИИ:\n" +
                "- Если пользователь перечисляет несколько конкретных покупок в рамках одной общей категории (например, 'в фастфуде бургер за 300 рублей и кола за 150' или 'потратил в супермаркете: молоко 100 и хлеб 50'), обязательно раздели это на ОТДЕЛЬНЫЕ операции.\n" +
                "- Каждой операции присвой одну и ту же общую подходящую категорию из списка имеющихся категорий, а в качестве 'title' и 'subcategory' укажи конкретную покупку ('Бургер', 'Кола', 'Молоко', 'Хлеб').\n" +
                "Подбирайте наиболее подходящие категории из предоставленных списков. Избегайте значений 'null' или пустых названий.\n" +
                "Верните СТРОГО JSON-массив объектов без какого-либо разметочного текста или комментариев."

        val userQuery = """
            Категории расходов: ${expenseCategories.joinToString(", ")}
            Категории доходов: ${incomeCategories.joinToString(", ")}

            Текст пользователя: "$trimmedText"

            Верни ТОЛЬКО JSON массив объектов формата:
            [
              {
                "title": "Такси",
                "type": "expense",
                "amount": 450.0,
                "category": "Такси и Транспорт",
                "subcategory": "Яндекс Такси",
                "date": "$todayStr"
              }
            ]
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userQuery)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        var lastError: String? = null
        val modelsToTry = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash"
        )

        val opId = GlobalConsoleLogger.newOperationId("VOICE-TXT")
        GlobalConsoleLogger.i("GEMINI", "Запрос к Gemini API для разбора текста (${trimmedText.length} симв.)", operationId = opId)

        for ((idx, model) in modelsToTry.withIndex()) {
            val attempt = idx + 1
            val startMs = System.currentTimeMillis()
            try {
                val response = apiService.generateContent(model, apiKey, request)
                if (response.error == null) {
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!responseText.isNullOrEmpty()) {
                        val jsonString = extractJsonArray(responseText)
                        if (jsonString.isNotBlank()) {
                            val parsed = parseJsonOperations(jsonString, todayStr)
                            if (parsed.isNotEmpty()) {
                                GlobalConsoleLogger.recordNetworkCall(
                                    operationType = "Разбор голоса (Текст)",
                                    modelName = model,
                                    durationMs = System.currentTimeMillis() - startMs,
                                    status = NetworkCallStatus.SUCCESS,
                                    httpCode = 200,
                                    attemptNumber = attempt,
                                    totalModelsInChain = modelsToTry.size,
                                    details = "распознано=${parsed.size} шт.",
                                    operationId = opId
                                )
                                return parsed
                            }
                        }
                    }
                } else {
                    lastError = response.error.message
                    GlobalConsoleLogger.recordNetworkCall(
                        operationType = "Разбор голоса (Текст)",
                        modelName = model,
                        durationMs = System.currentTimeMillis() - startMs,
                        status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                        httpCode = response.error.code,
                        errorType = response.error.status ?: "GeminiError",
                        errorMessage = response.error.message,
                        attemptNumber = attempt,
                        totalModelsInChain = modelsToTry.size,
                        operationId = opId
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e.message
                GlobalConsoleLogger.recordNetworkCall(
                    operationType = "Разбор голоса (Текст)",
                    modelName = model,
                    durationMs = System.currentTimeMillis() - startMs,
                    status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                    errorType = e.javaClass.simpleName,
                    errorMessage = e.localizedMessage ?: e.message,
                    attemptNumber = attempt,
                    totalModelsInChain = modelsToTry.size,
                    operationId = opId
                )
            }
        }

        GlobalConsoleLogger.e("GEMINI", "Все Gemini модели завершились ошибкой: $lastError", operationId = opId)
        throw IllegalStateException(lastError ?: "Не удалось разобрать операции из текста. Попробуйте сформулировать точнее.")
    }

    suspend fun parseVoiceAudioOperations(
        audioBytes: ByteArray,
        apiKey: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        mimeType: String = "audio/wav"
    ): List<ParsedVoiceOperation> = withContext(Dispatchers.IO) {
        if (audioBytes.isEmpty()) return@withContext emptyList()

        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Для анализа речи с помощью ИИ укажите API ключ Gemini в настройках приложения.")
        }

        val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayStr = dateFormat.format(java.util.Date())
        val dayOfWeekStr = java.text.SimpleDateFormat("EEEE", java.util.Locale("ru")).format(java.util.Date())

        val systemPrompt = "Вы — экспертный финансовый ассистент в режиме реального времени (Gemini Live). Прослушайте аудиофрагмент пользователя на русском языке и извлеките из него ВСЕ упомянутые финансовые операции (доходы и расходы).\n" +
                "Игнорируйте паузы, слова-паразиты ('эээ', 'ну', 'короче', 'так') и фоновый шум. Если в фрагменте нет финансовой операции (только вздох или шум), верните пустой массив [].\n" +
                "Разделите сказанное на отдельные операции и для каждой выделите название, тип (income или expense), сумму (число), категорию, подкатегорию (конкретное название покупки/источника) и дату (если упомянута).\n" +
                "Текущая дата сегодня: $todayStr (день недели: $dayOfWeekStr).\n" +
                "ПРАВИЛА ДЛЯ ДАТЫ ('date'):\n" +
                "- Если пользователь говорит 'вчера', вычисли дату за день до сегодняшней ($todayStr).\n" +
                "- Если пользователь говорит 'позавчера', вычисли дату за 2 дня до сегодняшней ($todayStr).\n" +
                "- Если пользователь указывает день недели или число (например 'в прошлый вторник', '15 июля'), вычисли соответствующую дату в формате YYYY-MM-DD.\n" +
                "- Если дата не упомянута, укажи текущую дату '$todayStr'.\n" +
                "ОСОБОЕ ПРАВИЛО ДЛЯ ДЕТАЛИЗАЦИИ:\n" +
                "- Если пользователь перечисляет несколько конкретных покупок в рамках одной общей категории (например, 'в фастфуде бургер за 300 рублей и кола за 150' или 'потратил в супермаркете: молоко 100 и хлеб 50'), обязательно раздели это на ОТДЕЛЬНЫЕ операции.\n" +
                "- Каждой операции присвой одну и ту же общую подходящую категорию из списка имеющихся категорий, а в качестве 'title' и 'subcategory' укажи конкретную покупку ('Бургер', 'Кола', 'Молоко', 'Хлеб').\n" +
                "Подбирайте наиболее подходящие категории из предоставленных списков. Избегайте значений 'null' или пустых названий.\n" +
                "Верните СТРОГО JSON-массив объектов без какого-либо разметочного текста или комментариев."

        val userQuery = """
            Категории расходов: ${expenseCategories.joinToString(", ")}
            Категории доходов: ${incomeCategories.joinToString(", ")}

            Прослушай прикрепленный аудиофрагмент и верни ТОЛЬКО JSON массив объектов формата (или [] если операций нет):
            [
              {
                "title": "Такси",
                "type": "expense",
                "amount": 450.0,
                "category": "Такси и Транспорт",
                "subcategory": "Яндекс Такси",
                "date": "$todayStr"
              }
            ]
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            inlineData = com.example.data.api.GeminiInlineData(
                                mimeType = mimeType,
                                data = base64Audio
                            )
                        ),
                        GeminiPart(text = userQuery)
                    )
                )
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        var lastError: String? = null
        val modelsToTry = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash"
        )

        val opId = GlobalConsoleLogger.newOperationId("VOICE-WAV")
        GlobalConsoleLogger.i("GEMINI", "Отправка Live-аудиофрагмента (${audioBytes.size} байт) в Gemini API...", operationId = opId)

        for ((idx, model) in modelsToTry.withIndex()) {
            val attempt = idx + 1
            val startMs = System.currentTimeMillis()
            try {
                val response = apiService.generateContent(model, apiKey, request)
                if (response.error == null) {
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!responseText.isNullOrEmpty()) {
                        val jsonString = extractJsonArray(responseText)
                        if (jsonString.isNotBlank()) {
                            val parsed = parseJsonOperations(jsonString, todayStr)
                            GlobalConsoleLogger.recordNetworkCall(
                                operationType = "Gemini Live Аудио (WAV)",
                                modelName = model,
                                durationMs = System.currentTimeMillis() - startMs,
                                status = NetworkCallStatus.SUCCESS,
                                httpCode = 200,
                                attemptNumber = attempt,
                                totalModelsInChain = modelsToTry.size,
                                details = "bytes=${audioBytes.size} | распознано=${parsed.size}",
                                operationId = opId
                            )
                            return@withContext parsed
                        }
                    }
                } else {
                    lastError = response.error.message
                    GlobalConsoleLogger.recordNetworkCall(
                        operationType = "Gemini Live Аудио (WAV)",
                        modelName = model,
                        durationMs = System.currentTimeMillis() - startMs,
                        status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                        httpCode = response.error.code,
                        errorType = response.error.status ?: "GeminiError",
                        errorMessage = response.error.message,
                        attemptNumber = attempt,
                        totalModelsInChain = modelsToTry.size,
                        details = "bytes=${audioBytes.size}",
                        operationId = opId
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e.message
                GlobalConsoleLogger.recordNetworkCall(
                    operationType = "Gemini Live Аудио (WAV)",
                    modelName = model,
                    durationMs = System.currentTimeMillis() - startMs,
                    status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                    errorType = e.javaClass.simpleName,
                    errorMessage = e.localizedMessage ?: e.message,
                    attemptNumber = attempt,
                    totalModelsInChain = modelsToTry.size,
                    details = "bytes=${audioBytes.size}",
                    operationId = opId
                )
            }
        }

        if (lastError != null) {
            GlobalConsoleLogger.e("GEMINI", "Ошибка Gemini Live аудио: $lastError")
        }
        return@withContext emptyList()
    }

    private fun extractJsonArray(rawText: String): String {
        val clean = rawText.replace("```json", "").replace("```", "").trim()
        val startIdx = clean.indexOf('[')
        val endIdx = clean.lastIndexOf(']')
        return if (startIdx != -1 && endIdx != -1 && endIdx >= startIdx) {
            clean.substring(startIdx, endIdx + 1)
        } else {
            ""
        }
    }

    private fun parseJsonOperations(jsonString: String, fallbackDate: String): List<ParsedVoiceOperation> {
        val resultList = mutableListOf<ParsedVoiceOperation>()
        try {
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title", "").trim()
                val type = obj.optString("type", "expense").lowercase(Locale.getDefault())
                val amount = obj.optDouble("amount", 0.0)
                val category = obj.optString("category", "").trim()
                val subcategory = obj.optString("subcategory", "").trim()
                val date = obj.optString("date", fallbackDate).trim()

                if (amount > 0) {
                    val finalCategory = if (category.isNotBlank() && !category.equals("null", ignoreCase = true)) category else "Прочее"
                    val finalSubcategory = if (subcategory.isNotBlank() && !subcategory.equals("null", ignoreCase = true)) subcategory else title
                    val finalTitle = if (title.isNotBlank()) title else finalSubcategory

                    resultList.add(
                        ParsedVoiceOperation(
                            title = finalTitle,
                            type = if (type == "income") "income" else "expense",
                            amount = amount,
                            category = finalCategory,
                            subcategory = finalSubcategory,
                            date = if (date.isNotBlank()) date else fallbackDate
                        )
                    )
                }
            }
        } catch (e: Exception) {
            GlobalConsoleLogger.e("GEMINI", "Ошибка парсинга JSON массива операций: ${e.localizedMessage}")
        }
        return resultList
    }

    private val storageFile = java.io.File(context.filesDir, "budget_storage.json")
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val backupAdapter = moshi.adapter(BudgetBackup::class.java)
}
