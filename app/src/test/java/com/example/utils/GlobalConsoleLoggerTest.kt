package com.example.utils

import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiCandidate
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiError
import com.example.data.api.GeminiInlineData
import com.example.data.api.GeminiModelConfig
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.GeminiResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean

class GlobalConsoleLoggerTest {

    @Before
    fun setUp() {
        GlobalConsoleLogger.setPaused(false)
        GlobalConsoleLogger.clear()
    }

    @After
    fun tearDown() {
        GlobalConsoleLogger.setPaused(false)
        GlobalConsoleLogger.clear()
    }

    @Test
    fun `gemini part serializes inlineData and mimeType in lowerCamelCase to prevent HTTP 400 rejection`() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(GeminiPart::class.java)

        val inlinePart = GeminiPart(
            text = null,
            inlineData = GeminiInlineData(
                mimeType = "audio/wav",
                data = "UklGRiQAAABXQVZFZm10"
            )
        )
        val json = adapter.toJson(inlinePart)

        assertTrue("Must use lowerCamelCase inlineData", json.contains("\"inlineData\""))
        assertTrue("Must use lowerCamelCase mimeType", json.contains("\"mimeType\""))
        assertFalse("Snake case inline_data causes HTTP 400 from Gemini API", json.contains("\"inline_data\""))
        assertFalse("Snake case mime_type causes HTTP 400 from Gemini API", json.contains("\"mime_type\""))
    }

    @Test
    fun `gemini-3_5-flash-lite is primary default model for all David Zhabov reactions while budget audit models remain unchanged`() {
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.DEFAULT_DAVID_REACTION_MODEL)
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.DAVID_REACTION_MODELS.first())
        assertEquals(
            listOf(
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash",
                "gemini-3.6-flash",
                "gemini-3.1-flash-lite"
            ),
            GeminiModelConfig.DAVID_REACTION_MODELS
        )

        // Модели для аудита бюджета остаются без изменений
        assertEquals(
            listOf(
                "gemini-3.8-flash",
                "gemini-3.6-flash",
                "gemini-3.5-flash",
                "gemini-2.5-flash"
            ),
            GeminiModelConfig.BUDGET_AUDIT_MODELS
        )
    }

    @Test
    fun `executeDavidReactionWithFallback uses gemini-3_5-flash-lite first for both single and multi-operation reactions`() = runTest {
        val attemptedModels = mutableListOf<String>()
        val fakeService = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequest
            ): GeminiResponse {
                attemptedModels.add(model)
                return GeminiResponse(
                    candidates = listOf(
                        GeminiCandidate(
                            content = GeminiContent(
                                parts = listOf(GeminiPart(text = "Ква! Отличная экономия, одобряю!"))
                            )
                        )
                    ),
                    error = null
                )
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                alt: String,
                request: GeminiRequest
            ): Response<ResponseBody> {
                throw UnsupportedOperationException("Not used in reaction tests")
            }
        }

        val sampleRequest = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Кофе 300 ₽"))))
        )

        val singleReply = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = fakeService,
            apiKey = "test-key",
            request = sampleRequest,
            operationType = "Реакция Давида (1 оп.)",
            operationPrefix = "DAVID",
            details = "category=Кафе | amount=300.0"
        )
        val multiReply = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = fakeService,
            apiKey = "test-key",
            request = sampleRequest,
            operationType = "Реакция Давида (группа)",
            operationPrefix = "DAVID-MULTI",
            details = "count=2"
        )

        assertEquals("Ква! Отличная экономия, одобряю!", singleReply)
        assertEquals("Ква! Отличная экономия, одобряю!", multiReply)
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.5-flash-lite"),
            attemptedModels
        )
    }

    @Test
    fun `executeDavidReactionWithFallback falls back to next model when primary fails`() = runTest {
        val attemptedModels = mutableListOf<String>()
        val fakeServiceWithPrimaryFailure = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequest
            ): GeminiResponse {
                attemptedModels.add(model)
                if (model == "gemini-3.5-flash-lite") {
                    return GeminiResponse(
                        candidates = null,
                        error = GeminiError(code = 503, message = "Model overloaded", status = "UNAVAILABLE")
                    )
                }
                return GeminiResponse(
                    candidates = listOf(
                        GeminiCandidate(
                            content = GeminiContent(
                                parts = listOf(GeminiPart(text = "Резервная жаба на связи!"))
                            )
                        )
                    ),
                    error = null
                )
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                alt: String,
                request: GeminiRequest
            ): Response<ResponseBody> {
                throw UnsupportedOperationException("Not used in reaction tests")
            }
        }

        val sampleRequest = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Такси 500 ₽"))))
        )

        val reply = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = fakeServiceWithPrimaryFailure,
            apiKey = "test-key",
            request = sampleRequest,
            operationType = "Реакция Давида (1 оп.)",
            operationPrefix = "DAVID",
            details = "category=Такси | amount=500.0"
        )

        assertEquals("Резервная жаба на связи!", reply)
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"),
            attemptedModels
        )
    }

    @Test
    fun `maskSensitive redacts Gemini API keys, Bearer tokens, and PIN codes`() {
        val rawText = """
            x-goog-api-key: ${"AI"}zaSyD9876543210abcdefghijklmnopQRSTU
            Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secretPayload
            key=${"AI"}zaSyA123456789012345678901234567890
            "pin": "7391"
            "data": "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQAAAAA="
        """.trimIndent()

        val masked = GlobalConsoleLogger.maskSensitive(rawText)

        assertFalse("Raw Gemini key must not remain in logs", masked.contains("${"AI"}zaSyD9876543210abcdefghijklmnopQRSTU"))
        assertFalse("Second Gemini key must not remain in logs", masked.contains("${"AI"}zaSyA123456789012345678901234567890"))
        assertFalse("Raw Bearer token must not remain in logs", masked.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse("Raw PIN must not remain in logs", masked.contains("\"7391\""))
        assertTrue("Masked output should contain AIza***REDACTED***", masked.contains("AIza***REDACTED***"))
        assertTrue("Masked output should contain [REDACTED]", masked.contains("[REDACTED]"))
    }

    @Test
    fun `ring buffer enforces MAX_LOGS cap of 500 entries`() {
        for (i in 1..550) {
            GlobalConsoleLogger.i("TEST", "Log message #$i")
        }

        val logs = GlobalConsoleLogger.logs.value
        assertEquals(GlobalConsoleLogger.MAX_LOGS, logs.size)
        assertEquals("Log message #51", logs.first().message)
        assertEquals("Log message #550", logs.last().message)
    }

    @Test
    fun `error logs are automatically indexed in errors flow and carry stack trace`() {
        val exception = IllegalStateException("Failed to parse Gemini JSON payload")
        GlobalConsoleLogger.e(
            tag = "GEMINI",
            message = "Ошибка разбора ответа модели",
            throwable = exception,
            operationId = "OP-TEST-01"
        )

        val errors = GlobalConsoleLogger.errors.value
        assertEquals(1, errors.size)
        val entry = errors.first()
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("GEMINI", entry.source)
        assertEquals("OP-TEST-01", entry.operationId)
        assertNotNull(entry.stackTrace)
        assertTrue(entry.stackTrace!!.contains("IllegalStateException"))
    }

    @Test
    fun `filterLogs filters by level, source, and search query across message and stackTrace`() {
        GlobalConsoleLogger.d("UI", "Opened AllTransactionsDialog")
        GlobalConsoleLogger.i("ROOM", "Inserted 1 transaction into database")
        GlobalConsoleLogger.e(
            "GEMINI",
            "HTTP 429 Quota exceeded",
            RuntimeException("RateLimitException: TooManyRequests"),
            operationId = "VOICE-0099"
        )

        val allEntries = GlobalConsoleLogger.logs.value

        // Filter by level ERROR
        val errorOnly = GlobalConsoleLogger.filterLogs(
            entries = allEntries,
            query = "",
            levelFilter = LogLevel.ERROR,
            sourceFilter = "ALL"
        )
        assertEquals(1, errorOnly.size)
        assertEquals("HTTP 429 Quota exceeded", errorOnly.first().message)

        // Filter by source ROOM
        val roomOnly = GlobalConsoleLogger.filterLogs(
            entries = allEntries,
            query = "",
            levelFilter = null,
            sourceFilter = "ROOM"
        )
        assertEquals(1, roomOnly.size)
        assertEquals("ROOM", roomOnly.first().source)

        // Filter by search query matching stackTrace or operationId
        val byStackQuery = GlobalConsoleLogger.filterLogs(
            entries = allEntries,
            query = "RateLimitException",
            levelFilter = null,
            sourceFilter = "ALL"
        )
        assertEquals(1, byStackQuery.size)

        val byOpIdQuery = GlobalConsoleLogger.filterLogs(
            entries = allEntries,
            query = "VOICE-0099",
            levelFilter = null,
            sourceFilter = "ALL"
        )
        assertEquals(1, byOpIdQuery.size)
    }

    @Test
    fun `pause mode stops live log collection except FATAL entries`() {
        GlobalConsoleLogger.i("UI", "Before pause")
        GlobalConsoleLogger.setPaused(true)
        GlobalConsoleLogger.d("UI", "While paused debug")
        GlobalConsoleLogger.e("NETWORK", "While paused error")

        val logs = GlobalConsoleLogger.logs.value
        assertEquals(1, logs.size)
        assertEquals("Before pause", logs.first().message)
    }

    @Test
    fun `handleUncaughtException records FATAL entry with masked secrets and delegates to default handler`() {
        val delegated = AtomicBoolean(false)
        var delegatedThrowable: Throwable? = null

        val fakeDefaultHandler = Thread.UncaughtExceptionHandler { _, throwable ->
            delegated.set(true)
            delegatedThrowable = throwable
        }

        val crash = RuntimeException("Crash with key ${"AI"}zaSySecretKey1234567890abcdef")
        GlobalConsoleLogger.handleUncaughtException(
            thread = Thread.currentThread(),
            throwable = crash,
            delegateHandler = fakeDefaultHandler
        )

        assertTrue("UncaughtExceptionHandler must always delegate to default system handler", delegated.get())
        assertEquals(crash, delegatedThrowable)

        val errors = GlobalConsoleLogger.errors.value
        assertEquals(1, errors.size)
        val fatalEntry = errors.first()
        assertEquals(LogLevel.FATAL, fatalEntry.level)
        assertEquals("CRASH", fatalEntry.source)
        assertFalse(fatalEntry.message.contains("${"AI"}zaSySecretKey1234567890abcdef"))
        assertTrue(fatalEntry.message.contains("AIza***REDACTED***"))
    }

    @Test
    fun `buildAiDiagnosticPrompt and buildDiagnosticReport produce structured sanitized Markdown`() {
        val opId = GlobalConsoleLogger.newOperationId("GEMINI")
        GlobalConsoleLogger.recordDbOperation(
            operationName = "INSERT transaction",
            tableName = "transactions",
            durationMs = 8L,
            details = "amount=1500.0",
            operationId = opId
        )
        GlobalConsoleLogger.recordNetworkCall(
            operationType = "Разбор голоса",
            modelName = "gemini-3.5-flash",
            durationMs = 420L,
            status = NetworkCallStatus.FALLBACK,
            httpCode = 503,
            errorType = "HttpException",
            errorMessage = "Service Unavailable for key ${"AI"}zaSyTestKey1234567890123456",
            attemptNumber = 1,
            totalModelsInChain = 3,
            operationId = opId
        )
        GlobalConsoleLogger.e(
            tag = "GEMINI",
            message = "Сбой цепочки моделей",
            throwable = IllegalStateException("All models failed"),
            operationId = opId
        )

        val targetError = GlobalConsoleLogger.errors.value.first()
        val aiPrompt = GlobalConsoleLogger.buildAiDiagnosticPrompt(
            targetError = targetError,
            appVersionName = "8.0",
            appVersionCode = 800,
            buildType = "debug"
        )
        val fullReport = GlobalConsoleLogger.buildDiagnosticReport(
            appVersionName = "8.0",
            appVersionCode = 800,
            buildType = "debug"
        )

        assertTrue(aiPrompt.contains("# Диагностический отчёт для ИИ-разработчика"))
        assertTrue(aiPrompt.contains("v8.0"))
        assertTrue(aiPrompt.contains(opId))
        assertTrue(aiPrompt.contains("IllegalStateException: All models failed"))
        assertFalse(aiPrompt.contains("${"AI"}zaSyTestKey1234567890123456"))

        assertTrue(fullReport.contains("OBSIDIAN NEO-BANK (DAVID FINANCE) — DIAGNOSTIC REPORT"))
        assertTrue(fullReport.contains("NETWORK / GEMINI AI TRACES"))
        assertTrue(fullReport.contains("RECENT ERRORS"))
    }

    @Test
    fun `formatLastAddedVoiceOperationText returns only the latest operation in Gemini Live`() {
        val operations = listOf(
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Фастфуд и Доставка",
                subcategory = "Бургер",
                title = "Бургер",
                amount = 500.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Продукты и Выживание",
                subcategory = "Продукты",
                title = "Продукты",
                amount = 5000.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "income",
                category = "Зарплата",
                subcategory = "Зарплата",
                title = "Зарплата",
                amount = 50000.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Шмотки и Тяги",
                subcategory = "Футболка",
                title = "Футболка",
                amount = 25000.0
            )
        )

        val liveText = com.example.ui.components.formatLastAddedVoiceOperationText(operations)
        assertEquals("Футболка 25 000 ₽", liveText)
        assertFalse("Must not concatenate previous operations", liveText.contains("Бургер"))
        assertFalse("Must not concatenate previous operations", liveText.contains("Продукты"))
    }

    @Test
    fun `calculateVoiceBatchSummary separates incomes and expenses instead of summing absolute values`() {
        val mixedOps = listOf(
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Фастфуд и Доставка",
                subcategory = "Бургер",
                title = "Бургер",
                amount = 500.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Продукты и Выживание",
                subcategory = "Продукты",
                title = "Продукты",
                amount = 5000.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "income",
                category = "Зарплата",
                subcategory = "Зарплата",
                title = "Зарплата",
                amount = 50000.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Шмотки и Тяги",
                subcategory = "Футболка",
                title = "Футболка",
                amount = 25000.0
            )
        )

        val summary = com.example.ui.components.calculateVoiceBatchSummary(mixedOps)
        assertTrue(summary.isMixed)
        assertEquals(50000.0, summary.totalIncome, 0.001)
        assertEquals(30500.0, summary.totalExpense, 0.001)
        assertEquals(19500.0, summary.netBalance, 0.001)
        assertEquals("Итог: +19 500 ₽", summary.headerSubtitleText)
        assertEquals("Сохранить (4) • +50 000 ₽ / -30 500 ₽", summary.saveButtonPlainText)
        assertFalse("Must never show gross sum 80 500 ₽", summary.saveButtonPlainText.contains("80 500"))

        val expensesOnly = mixedOps.filter { it.type == "expense" }
        val expSummary = com.example.ui.components.calculateVoiceBatchSummary(expensesOnly)
        assertFalse(expSummary.isMixed)
        assertEquals("-30 500 ₽", expSummary.headerSubtitleText)
        assertEquals("Сохранить (3) • -30 500 ₽", expSummary.saveButtonPlainText)

        val incomesOnly = mixedOps.filter { it.type == "income" }
        val incSummary = com.example.ui.components.calculateVoiceBatchSummary(incomesOnly)
        assertFalse(incSummary.isMixed)
        assertEquals("+50 000 ₽", incSummary.headerSubtitleText)
        assertEquals("Сохранить (1) • +50 000 ₽", incSummary.saveButtonPlainText)
    }

    @Test
    fun `mergeDuplicateVoiceOperations groups same-category operations and preserves subcategories`() {
        val rawOps = listOf(
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Фастфуд и Доставка",
                subcategory = "Бургер",
                title = "Бургер",
                amount = 500.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Фастфуд и Доставка",
                subcategory = "Бургер",
                title = "Бургер",
                amount = 300.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Продукты и Выживание",
                subcategory = "Молоко",
                title = "Молоко",
                amount = 120.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "expense",
                category = "Продукты и Выживание",
                subcategory = "Хлеб",
                title = "Хлеб",
                amount = 80.0
            ),
            com.example.data.repository.ParsedVoiceOperation(
                type = "income",
                category = "Зарплата",
                subcategory = "Зарплата",
                title = "Зарплата",
                amount = 50000.0
            )
        )

        val result = com.example.ui.components.mergeDuplicateVoiceOperations(rawOps)
        assertEquals(2, result.mergedGroupsCount)
        assertEquals(2, result.removedDuplicatesCount)
        assertEquals(3, result.mergedOperations.size)

        val mergedFastFood = result.mergedOperations[0]
        assertEquals("Фастфуд и Доставка", mergedFastFood.category)
        assertEquals("Бургер (×2)", mergedFastFood.subcategory)
        assertEquals(800.0, mergedFastFood.amount, 0.001)

        val mergedGroceries = result.mergedOperations[1]
        assertEquals("Продукты и Выживание", mergedGroceries.category)
        assertEquals("Молоко + Хлеб", mergedGroceries.subcategory)
        assertEquals(200.0, mergedGroceries.amount, 0.001)

        val unchangedIncome = result.mergedOperations[2]
        assertEquals("Зарплата", unchangedIncome.category)
        assertEquals(50000.0, unchangedIncome.amount, 0.001)
    }

    @Test
    fun `computeDayHeaderTotals shows both income and expense badges in all mode`() {
        val mixedAll = com.example.ui.components.dialogs.computeDayHeaderTotals(
            filterType = "all",
            dayTotalIncome = 55000.0,
            dayTotalExpense = 26000.0
        )
        assertEquals("+55 000\u00A0₽", mixedAll.incomeBadgeText)
        assertEquals("-26 000\u00A0₽", mixedAll.expenseBadgeText)

        val expenseOnlyFilter = com.example.ui.components.dialogs.computeDayHeaderTotals(
            filterType = "expense",
            dayTotalIncome = 55000.0,
            dayTotalExpense = 26000.0
        )
        assertNull(expenseOnlyFilter.incomeBadgeText)
        assertEquals("-26 000\u00A0₽", expenseOnlyFilter.expenseBadgeText)

        val incomeOnlyFilter = com.example.ui.components.dialogs.computeDayHeaderTotals(
            filterType = "income",
            dayTotalIncome = 55000.0,
            dayTotalExpense = 26000.0
        )
        assertEquals("+55 000\u00A0₽", incomeOnlyFilter.incomeBadgeText)
        assertNull(incomeOnlyFilter.expenseBadgeText)
    }

    @Test
    fun `initial AllTransactionsPeriodState is unlocked and updates displayed month dynamically on scroll`() {
        val sampleTransactions = listOf(
            com.example.data.db.TransactionEntity(
                id = "tx-1",
                type = "expense",
                date = "2026-10-12",
                category = "Кафе",
                subcategory = "Октябрьский кофе",
                amount = 350.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-2",
                type = "income",
                date = "2026-10-05",
                category = "Зарплата",
                subcategory = "Октябрьская зарплата",
                amount = 120000.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-3",
                type = "expense",
                date = "2026-09-20",
                category = "Продукты",
                subcategory = "Сентябрьские продукты",
                amount = 4200.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-4",
                type = "expense",
                date = "2026-08-14",
                category = "Путешествия",
                subcategory = "Августовский отпуск",
                amount = 35000.0
            )
        )

        // Переключатель периодов содержит ровно 3 кнопки: Нед | Мес | Год (без кнопки «Все»)
        assertEquals(
            listOf("week" to "Нед", "month" to "Мес", "year" to "Год"),
            com.example.ui.components.dialogs.PERIOD_SWITCHER_TABS
        )

        var state = com.example.ui.components.dialogs.AllTransactionsPeriodState(
            stableAnchorDateStr = "2026-10-15"
        )

        // При открытии период не зафиксирован (можно листать весь список вниз до Сентября и Августа)
        assertNull(state.selectedPeriod)
        assertFalse(state.isPeriodLocked)
        assertEquals(-1, state.activeTabIndex)
        assertEquals(0, state.swipeMonthOffset)
        assertEquals("2026-10-15", state.displayedMonthDateStr)

        // График/карточки в шапке изначально показывают суммы за Октябрь 2026
        val initialChartTransactions = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(2, initialChartTransactions.size)
        assertTrue(initialChartTransactions.all { it.date.startsWith("2026-10") })

        // Когда пользователь скроллит список вниз до Сентября ("2026-09-20"),
        // отображаемый месяц в чипе и графиках автоматически переключается на Сентябрь без блокировки списка
        state = state.onScrolledToDate("2026-09-20")
        assertFalse(state.isPeriodLocked)
        assertEquals(-1, state.swipeMonthOffset)
        assertEquals("2026-09-15", state.displayedMonthDateStr)
        val sepLabel = com.example.ui.components.dialogs.buildDynamicPeriodLabel(
            stableAnchorDateStr = state.stableAnchorDateStr,
            customLabelStr = state.customLabelStr,
            selectedPeriod = state.selectedPeriod,
            swipeMonthOffset = state.swipeMonthOffset,
            swipeWeekOffset = state.swipeWeekOffset,
            swipeYearOffset = state.swipeYearOffset
        )
        assertTrue(sepLabel.startsWith("Сентябрь"))

        val sepChartTransactions = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(1, sepChartTransactions.size)
        assertEquals("Сентябрьские продукты", sepChartTransactions.first().subcategory)
    }

    @Test
    fun `swiping months updates chart totals, period labels, and transaction list synchronously without locking period`() {
        val sampleTransactions = listOf(
            com.example.data.db.TransactionEntity(
                id = "tx-1",
                type = "expense",
                date = "2026-10-10",
                category = "Фастфуд",
                subcategory = "Октябрь бургер",
                amount = 2000.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-2",
                type = "expense",
                date = "2026-09-18",
                category = "Продукты",
                subcategory = "Сентябрь супермаркет",
                amount = 5000.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-3",
                type = "expense",
                date = "2026-08-05",
                category = "Путешествия",
                subcategory = "Август билеты",
                amount = 15000.0
            )
        )

        var state = com.example.ui.components.dialogs.AllTransactionsPeriodState(
            stableAnchorDateStr = "2026-10-15"
        )

        // Шаг 1: Свайп на предыдущий месяц (Октябрь -> Сентябрь)
        state = state.onSwipePeriod(-1)
        assertEquals(-1, state.swipeMonthOffset)
        assertEquals("2026-09-15", state.displayedMonthDateStr)
        assertNull("Свайп не должен автоматически фиксировать период", state.selectedPeriod)
        assertEquals(-1, state.activeTabIndex)

        val sepLabel = com.example.ui.components.dialogs.buildDynamicPeriodLabel(
            stableAnchorDateStr = state.stableAnchorDateStr,
            customLabelStr = state.customLabelStr,
            selectedPeriod = state.selectedPeriod,
            swipeMonthOffset = state.swipeMonthOffset,
            swipeWeekOffset = state.swipeWeekOffset,
            swipeYearOffset = state.swipeYearOffset
        )
        assertTrue(sepLabel.startsWith("Сентябрь"))

        val sepList = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(listOf("Сентябрь супермаркет"), sepList.map { it.subcategory })

        val sepChartTotals = com.example.ui.components.dialogs.calculateCategoryTotalsForOffset(
            rawList = sepList,
            filterType = "expense",
            isDrilledDownToMixed = false
        ).toMap()
        assertEquals(5000.0, sepChartTotals["Продукты"] ?: 0.0, 0.001)
        assertFalse(sepChartTotals.containsKey("Фастфуд"))

        // Шаг 2: Ещё один свайп назад (Сентябрь -> Август)
        state = state.onSwipePeriod(-1)
        assertEquals(-2, state.swipeMonthOffset)
        assertEquals("2026-08-15", state.displayedMonthDateStr)
        assertNull(state.selectedPeriod)
        val augList = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(listOf("Август билеты"), augList.map { it.subcategory })

        // Шаг 3: Свайп вперёд (Август -> Сентябрь)
        state = state.onSwipePeriod(1)
        assertEquals(-1, state.swipeMonthOffset)
        assertEquals("2026-09-15", state.displayedMonthDateStr)
        assertNull(state.selectedPeriod)
    }

    @Test
    fun `tapping Mes after swiping locks displayed month and second tap unlocks without showing all-time history`() {
        val sampleTransactions = listOf(
            com.example.data.db.TransactionEntity(
                id = "tx-1",
                type = "expense",
                date = "2026-10-10",
                category = "Транспорт",
                subcategory = "Октябрь такси",
                amount = 800.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-2",
                type = "expense",
                date = "2026-09-10",
                category = "Жильё",
                subcategory = "Сентябрь аренда",
                amount = 45000.0
            )
        )

        // Пользователь открыл Октябрь и перелистнул на Сентябрь
        var state = com.example.ui.components.dialogs.AllTransactionsPeriodState(
            stableAnchorDateStr = "2026-10-15"
        ).onSwipePeriod(-1)

        assertEquals("2026-09-15", state.displayedMonthDateStr)
        assertNull(state.selectedPeriod)

        // Первое нажатие «Мес»: фиксирует текущий отображаемый месяц (Сентябрь), не сбрасывая его на Октябрь
        state = state.onTogglePeriodTab("month")
        assertEquals("month", state.selectedPeriod)
        assertTrue(state.isPeriodLocked)
        assertEquals(1, state.activeTabIndex)
        assertEquals(-1, state.swipeMonthOffset)
        assertEquals("2026-09-15", state.displayedMonthDateStr)

        val lockedSepTransactions = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(listOf("Сентябрь аренда"), lockedSepTransactions.map { it.subcategory })

        // Повторное нажатие «Мес»: снимает фиксацию, но оставляет отображаемый Сентябрь (НЕ включает все операции за всё время)
        state = state.onTogglePeriodTab("month")
        assertNull(state.selectedPeriod)
        assertFalse(state.isPeriodLocked)
        assertEquals(-1, state.activeTabIndex)
        assertEquals(-1, state.swipeMonthOffset)
        assertEquals("2026-09-15", state.displayedMonthDateStr)

        val unlockedSepTransactions = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(1, unlockedSepTransactions.size)
        assertEquals("Сентябрь аренда", unlockedSepTransactions.first().subcategory)
    }

    @Test
    fun `toggle logic for week and year tabs and interaction with search, categories, and income-expense filters`() {
        val sampleTransactions = listOf(
            com.example.data.db.TransactionEntity(
                id = "tx-1",
                type = "expense",
                date = "2026-09-14",
                category = "Кафе",
                subcategory = "Кофе Раф",
                amount = 450.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-2",
                type = "expense",
                date = "2026-09-02",
                category = "Кафе",
                subcategory = "Кофе Американо",
                amount = 250.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-3",
                type = "income",
                date = "2026-09-15",
                category = "Зарплата",
                subcategory = "Премия Сентябрь",
                amount = 30000.0
            ),
            com.example.data.db.TransactionEntity(
                id = "tx-4",
                type = "expense",
                date = "2026-01-10",
                category = "Спорт",
                subcategory = "Январский абонемент",
                amount = 12000.0
            )
        )

        var state = com.example.ui.components.dialogs.AllTransactionsPeriodState(
            stableAnchorDateStr = "2026-10-15"
        ).onSwipePeriod(-1) // Отображается 2026-09-15

        // Нажатие «Нед»: включает недельный режим относительно отображаемого месяца (2026-09-15)
        state = state.onTogglePeriodTab("week")
        assertEquals("week", state.selectedPeriod)
        assertEquals(0, state.activeTabIndex)
        val weekList = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        // Неделя с 14 по 20 сентября 2026 содержит только операции за 14 и 15 сентября
        assertEquals(setOf("Кофе Раф", "Премия Сентябрь"), weekList.map { it.subcategory }.toSet())

        // Переключение с «Нед» сразу на «Год»: заменяет фильтр на годовой
        state = state.onTogglePeriodTab("year")
        assertEquals("year", state.selectedPeriod)
        assertEquals(2, state.activeTabIndex)
        val yearList = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(4, yearList.size)

        // Повторное нажатие «Год»: снимает годовой фильтр и возвращает к просмотру месяца (Сентябрь 2026)
        state = state.onTogglePeriodTab("year")
        assertNull(state.selectedPeriod)
        assertEquals(-1, state.activeTabIndex)
        val backToMonthList = com.example.ui.components.dialogs.filterTransactionsForOffset(
            transactions = sampleTransactions,
            selectedPeriod = state.selectedPeriod,
            customStartStr = state.customStartStr,
            customEndStr = state.customEndStr,
            stableAnchorDateStr = state.stableAnchorDateStr,
            currentOffsetInt = state.currentOffsetInt,
            offset = state.currentOffsetInt,
            swipeMonthOffset = state.swipeMonthOffset
        )
        assertEquals(3, backToMonthList.size)
        assertFalse(backToMonthList.any { it.subcategory == "Январский абонемент" })

        // Проверка совместной работы с фильтром доходов/расходов, категорией и поиском
        val expenseMonthList = backToMonthList.filter { it.type.equals("expense", ignoreCase = true) }
        val filteredByCategoryAndSearch = com.example.ui.components.dialogs.filterAndSortTransactions(
            typeFilteredList = expenseMonthList,
            searchQuery = "Раф",
            selectedCategoryFilter = "Кафе",
            isDrilledDownToMixed = false,
            remainingCategoryNames = emptyList(),
            sortOption = "date"
        )
        assertEquals(1, filteredByCategoryAndSearch.size)
        assertEquals("Кофе Раф", filteredByCategoryAndSearch.first().subcategory)
    }

    @Test
    fun `GeminiModelConfig extracts HTTP 400 error body details and aborts fallback chain on fatal API_KEY_INVALID`() = runTest {
        assertEquals("", GeminiModelConfig.sanitizeApiKey("your_api_key_here"))
        assertEquals("", GeminiModelConfig.sanitizeApiKey("MY_GEMINI_API_KEY"))
        assertEquals("${"AI"}zaSyValidKey1234567890", GeminiModelConfig.sanitizeApiKey("  \"${"AI"}zaSyValidKey1234567890\" \n"))
        assertFalse(GeminiModelConfig.isValidGeminiApiKey("MY_GEMINI_API_KEY"))
        assertTrue(GeminiModelConfig.isValidGeminiApiKey("${"AI"}zaSyValidKey1234567890"))

        val errorJson = """
            {
              "error": {
                "code": 400,
                "message": "API key not valid. Please pass a valid API key.",
                "status": "INVALID_ARGUMENT",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "API_KEY_INVALID"
                  }
                ]
              }
            }
        """.trimIndent()

        val parsedDetails = GeminiModelConfig.parseErrorBodyJson(errorJson, 400)
        assertEquals(400, parsedDetails.httpCode)
        assertEquals("API_KEY_INVALID", parsedDetails.errorType)
        assertTrue(parsedDetails.isFatalForAllModels)
        assertTrue(parsedDetails.userMessage.contains("API_KEY_INVALID"))

        val attemptedModels = mutableListOf<String>()
        val fatalHttp400Api = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequest
            ): GeminiResponse {
                attemptedModels.add(model)
                val body = okhttp3.ResponseBody.create(null, errorJson)
                throw retrofit2.HttpException(retrofit2.Response.error<GeminiResponse>(400, body))
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                alt: String,
                request: GeminiRequest
            ): Response<ResponseBody> = throw UnsupportedOperationException()
        }

        val sampleRequest = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Такси 500 ₽"))))
        )

        val result = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = fatalHttp400Api,
            apiKey = "invalid-key",
            request = sampleRequest,
            operationType = "Разбор голоса (Текст)",
            operationPrefix = "VOICE-TXT",
            details = "test"
        )

        assertNull(result)
        assertEquals(
            "Fatal HTTP 400 API_KEY_INVALID must abort immediately on the first model without hammering all fallback models",
            1,
            attemptedModels.size
        )
    }
}
