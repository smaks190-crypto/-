package com.example.data.repository

import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiModelConfig
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.RetrofitClient
import com.example.utils.GlobalConsoleLogger
import com.example.utils.NetworkCallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Сервис разбора голосовых и аудио-операций через Gemini с детерминированной валидацией JSON (Issue #69, Этап 3).
 */
class VoiceOperationParserService(
    private val apiService: GeminiApiService = RetrofitClient.service
) {
    suspend fun parseVoiceOperations(
        voiceText: String,
        apiKey: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>
    ): List<ParsedVoiceOperation> {
        val trimmedText = voiceText.trim()
        if (trimmedText.isEmpty() || trimmedText.equals("Говорите...", ignoreCase = true)) return emptyList()

        val cleanKey = GeminiModelConfig.sanitizeApiKey(apiKey)
        if (cleanKey.isBlank()) {
            throw IllegalArgumentException("Для анализа речи с помощью ИИ укажите действующий API ключ Gemini в настройках приложения.")
        }

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayStr = dateFormat.format(java.util.Date())
        val dayOfWeekStr = java.text.SimpleDateFormat("EEEE", java.util.Locale("ru")).format(java.util.Date())

        val systemPrompt = "Вы — экспертный финансовый ассистент. Анализируйте сказанный пользователем текст и извлекайте из него ВСЕ финансовые операции (доходы и расходы).\n" +
                "Игнорируйте паузы, слова-паразиты ('эээ', 'ну', 'короче', 'так') и фоновый шум. Если во фразе нет финансовой операции или не названа сумма, верните пустой массив [].\n" +
                "Разделите сказанное на отдельные операции и для каждой выделите название, тип (income или expense), сумму (число), категорию, подкатегорию (если есть) и дату (если упомянута).\n" +
                "Текущая дата сегодня: $todayStr (день недели: $dayOfWeekStr).\n" +
                "ПРАВИЛА ДЛЯ ДАТЫ ('date'):\n" +
                "- Если пользователь говорит 'вчера', вычисли дату за день до сегодняшней ($todayStr).\n" +
                "- Если пользователь говорит 'позавчера', вычисли дату за 2 дня до сегодняшней ($todayStr).\n" +
                "- Если пользователь указывает день недели или число (например 'в прошлый вторник', '15 июля'), вычисли соответствующую дату в формате YYYY-MM-DD.\n" +
                "- Если дата не упомянута, укажи текущую дату '$todayStr'.\n" +
                "ОСОБОЕ ПРАВИЛО ДЛЯ ДЕТАЛИЗАЦИИ:\n" +
                "- Если пользователь перечисляет несколько конкретных покупок в рамках одной общей категории (например, 'в фастфуде бургер за 300 рублей и кола за 150' или 'потратил в супермаркете: молоко 100 и хлеб 50'), обязательно раздели это на ОТДЕЛЬНЫЕ операции.\n" +
                "- Каждой операции присвой одну и ту же общую подходящую категорию (например, 'Кафе/Рестораны', 'Фастфуд' или 'Продукты'), а в качестве 'title' и 'subcategory' укажи конкретную покупку ('Бургер', 'Кола', 'Молоко', 'Хлеб').\n" +
                "Подбирайте наиболее подходящие категории из предоставленных списков. Избегайте значений 'null' или пустых названий.\n" +
                "Верните СТРОГО JSON-массив объектов без какого-либо разметочного текста или комментариев."

        val userQuery = """
            Категории расходов: ${expenseCategories.joinToString(", ")}
            Категории доходов: ${incomeCategories.joinToString(", ")}

            Разбери фразу и верни JSON массив объектов формата (или [] если операций нет):
            [
              {
                "title": "Такси",
                "type": "expense",
                "amount": 450.0,
                "category": "Транспорт",
                "subcategory": "Яндекс Такси",
                "date": "$todayStr"
              }
            ]

            Текст пользователя:
            \"\"\"
            $trimmedText
            \"\"\"
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userQuery)))),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
        )

        val opId = GlobalConsoleLogger.newOperationId("VOICE-TXT")
        GlobalConsoleLogger.i("GEMINI", "Запрос к Gemini API для разбора текста (${trimmedText.length} симв.)", operationId = opId)

        return executeVoiceTextParsingWithFallback(
            apiService = apiService,
            cleanKey = cleanKey,
            request = request,
            todayStr = todayStr,
            opId = opId,
            modelsToTry = VOICE_TEXT_MODELS
        )
    }

    suspend fun parseVoiceAudioOperations(
        audioBytes: ByteArray,
        apiKey: String,
        expenseCategories: List<String>,
        incomeCategories: List<String>,
        mimeType: String = "audio/wav"
    ): List<ParsedVoiceOperation> = withContext(Dispatchers.IO) {
        if (audioBytes.isEmpty()) return@withContext emptyList()

        val cleanKey = GeminiModelConfig.sanitizeApiKey(apiKey)
        if (cleanKey.isBlank()) {
            throw IllegalArgumentException("Для анализа речи с помощью ИИ укажите действующий API ключ Gemini в настройках приложения.")
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
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.8-flash",
            "gemini-3.1-flash-lite"
        )

        val opId = GlobalConsoleLogger.newOperationId("VOICE-WAV")
        GlobalConsoleLogger.i("GEMINI", "Отправка Live-аудиофрагмента (${audioBytes.size} байт) в Gemini API...", operationId = opId)

        for ((idx, model) in modelsToTry.withIndex()) {
            val attempt = idx + 1
            val startMs = System.currentTimeMillis()
            try {
                val response = apiService.generateContent(model, cleanKey, request)
                if (response.error == null) {
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    val jsonString = if (!responseText.isNullOrEmpty()) extractJsonArray(responseText) else ""
                    if (jsonString.isNotBlank()) {
                        val parsed = parseJsonOperations(jsonString, todayStr)
                        GlobalConsoleLogger.recordNetworkCall(
                            operationType = "Gemini Live Аудио (WAV)",
                            modelName = model,
                            durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                            status = NetworkCallStatus.SUCCESS,
                            httpCode = 200,
                            attemptNumber = attempt,
                            totalModelsInChain = modelsToTry.size,
                            details = "bytes=${audioBytes.size} | распознано=${parsed.size}",
                            operationId = opId
                        )
                        return@withContext parsed
                    } else {
                        lastError = if (responseText.isNullOrEmpty()) {
                            "Пустой ответ модели"
                        } else {
                            "Ответ модели без JSON-массива"
                        }
                        GlobalConsoleLogger.recordNetworkCall(
                            operationType = "Gemini Live Аудио (WAV)",
                            modelName = model,
                            durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                            status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                            httpCode = 200,
                            errorType = "EmptyResponse",
                            errorMessage = lastError,
                            attemptNumber = attempt,
                            totalModelsInChain = modelsToTry.size,
                            details = "bytes=${audioBytes.size}",
                            operationId = opId
                        )
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
                val err = GeminiModelConfig.extractHttpErrorDetails(e)
                lastError = err.userMessage
                val isLastOrFatal = err.isFatalForAllModels || attempt >= modelsToTry.size
                GlobalConsoleLogger.recordNetworkCall(
                    operationType = "Gemini Live Аудио (WAV)",
                    modelName = model,
                    durationMs = System.currentTimeMillis() - startMs,
                    status = if (isLastOrFatal) NetworkCallStatus.ERROR else NetworkCallStatus.FALLBACK,
                    httpCode = err.httpCode,
                    errorType = err.errorType,
                    errorMessage = err.userMessage,
                    attemptNumber = attempt,
                    totalModelsInChain = modelsToTry.size,
                    details = "bytes=${audioBytes.size}",
                    operationId = opId
                )
                if (err.isFatalForAllModels) {
                    break
                }
            }
        }

        if (lastError != null) {
            GlobalConsoleLogger.e("GEMINI", "Ошибка Gemini Live аудио: $lastError")
        }
        return@withContext emptyList()
    }

    companion object {
        val VOICE_TEXT_MODELS: List<String> = listOf(
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.1-flash-lite"
        )

        internal fun extractJsonArray(rawText: String): String {
            val clean = rawText.replace("```json", "").replace("```", "").trim()
            val startIdx = clean.indexOf('[')
            val endIdx = clean.lastIndexOf(']')
            return if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                clean.substring(startIdx, endIdx + 1)
            } else ""
        }

        internal fun sanitizeJsonStr(s: String?): String {
            if (s == null) return ""
            val trimmed = s.trim()
            if (trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) return ""
            return trimmed
        }

        internal fun parseJsonOperations(jsonString: String, defaultDate: String): List<ParsedVoiceOperation> {
            val results = mutableListOf<ParsedVoiceOperation>()
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val rawTitle = sanitizeJsonStr(obj.optString("title", ""))
                    val rawType = if (obj.optString("type", "expense").lowercase().contains("inc")) "income" else "expense"
                    val amount = obj.optDouble("amount", 0.0)
                    val rawCategory = sanitizeJsonStr(obj.optString("category", ""))
                    val rawSubcategory = sanitizeJsonStr(obj.optString("subcategory", ""))
                    val rawDate = sanitizeJsonStr(obj.optString("date", ""))

                    val finalTitle = if (rawTitle.isNotBlank()) rawTitle else if (rawCategory.isNotBlank()) rawCategory else if (rawType == "income") "Доход" else "Расход"
                    val finalCategory = if (rawCategory.isNotBlank()) rawCategory else "Прочее"
                    val finalDate = if (rawDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) rawDate else defaultDate

                    if (amount > 0.0) {
                        results.add(
                            ParsedVoiceOperation(
                                title = finalTitle,
                                type = rawType,
                                amount = amount,
                                category = finalCategory,
                                subcategory = rawSubcategory,
                                date = finalDate
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
            return results
        }

        internal suspend fun executeVoiceTextParsingWithFallback(
            apiService: GeminiApiService,
            cleanKey: String,
            request: GeminiRequest,
            todayStr: String,
            opId: String,
            modelsToTry: List<String> = VOICE_TEXT_MODELS
        ): List<ParsedVoiceOperation> {
            var lastError: String? = null

            for ((idx, model) in modelsToTry.withIndex()) {
                val attempt = idx + 1
                val startMs = System.currentTimeMillis()
                try {
                    val response = apiService.generateContent(model, cleanKey, request)
                    if (response.error == null) {
                        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        val jsonString = if (!responseText.isNullOrEmpty()) extractJsonArray(responseText) else ""
                        if (jsonString.isNotBlank()) {
                            val parsed = parseJsonOperations(jsonString, todayStr)
                            GlobalConsoleLogger.recordNetworkCall(
                                operationType = "Разбор голоса (Текст)",
                                modelName = model,
                                durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                                status = NetworkCallStatus.SUCCESS,
                                httpCode = 200,
                                attemptNumber = attempt,
                                totalModelsInChain = modelsToTry.size,
                                details = "распознано=${parsed.size} шт.",
                                operationId = opId
                            )
                            return parsed
                        } else {
                            lastError = if (responseText.isNullOrEmpty()) {
                                "Пустой ответ модели"
                            } else {
                                "Ответ модели без JSON-массива"
                            }
                            GlobalConsoleLogger.recordNetworkCall(
                                operationType = "Разбор голоса (Текст)",
                                modelName = model,
                                durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                                status = if (attempt < modelsToTry.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                                httpCode = 200,
                                errorType = "EmptyResponse",
                                errorMessage = lastError,
                                attemptNumber = attempt,
                                totalModelsInChain = modelsToTry.size,
                                operationId = opId
                            )
                        }
                    } else {
                        lastError = response.error.message
                        GlobalConsoleLogger.recordNetworkCall(
                            operationType = "Разбор голоса (Текст)",
                            modelName = model,
                            durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
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
                    val err = GeminiModelConfig.extractHttpErrorDetails(e)
                    lastError = err.userMessage
                    val isLastOrFatal = err.isFatalForAllModels || attempt >= modelsToTry.size
                    GlobalConsoleLogger.recordNetworkCall(
                        operationType = "Разбор голоса (Текст)",
                        modelName = model,
                        durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                        status = if (isLastOrFatal) NetworkCallStatus.ERROR else NetworkCallStatus.FALLBACK,
                        httpCode = err.httpCode,
                        errorType = err.errorType,
                        errorMessage = err.userMessage,
                        attemptNumber = attempt,
                        totalModelsInChain = modelsToTry.size,
                        operationId = opId
                    )
                    if (err.isFatalForAllModels) {
                        break
                    }
                }
            }

            GlobalConsoleLogger.e("GEMINI", "Все Gemini модели завершились ошибкой: $lastError")
            throw IllegalStateException(lastError ?: "Не удалось разбрать операции из текста. Попробуйте сформулировать точнее.")
        }
    }
}

data class ParsedVoiceOperation(
    val title: String,
    val type: String,
    val amount: Double,
    val category: String,
    val subcategory: String = "",
    val date: String = ""
)

private fun String?.isNull_or_Empty(): Boolean = this == null || this.isEmpty()
