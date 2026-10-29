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
import java.net.SocketTimeoutException
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
        assertEquals(
            listOf(
                "gemini-3.5-flash-lite",
                "gemini-3.8-flash",
                "gemini-3.6-flash",
                "gemini-3.1-flash-lite"
            ),
            GeminiModelConfig.DAVID_REACTION_MODELS
        )
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.DAVID_REACTION_MODELS.first())

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
    fun `single and group David reactions use gemini-3_5-flash-lite by default and preserve fallback mechanism`() = runTest {
        val sampleRequest = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = "Бургер 2000 ₽"))))
        )

        // 1. Одиночная реакция Давида Жабова — успешный ответ с первой попытки от gemini-3.5-flash-lite
        val singleAttemptedModels = mutableListOf<String>()
        val singleApi = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequest
            ): GeminiResponse {
                singleAttemptedModels.add(model)
                return GeminiResponse(
                    candidates = listOf(
                        GeminiCandidate(
                            content = GeminiContent(
                                parts = listOf(GeminiPart(text = "«Два косаря за бургер? Жаба в обмороке! 🐸»"))
                            )
                        )
                    )
                )
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                alt: String,
                request: GeminiRequest
            ): Response<ResponseBody> = throw UnsupportedOperationException()
        }

        val singleComment = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = singleApi,
            apiKey = "test-key",
            request = sampleRequest,
            operationType = "Реакция Давида Жабова",
            operationPrefix = "DAVID",
            details = "cat=Фастфуд"
        )

        assertEquals("Два косаря за бургер? Жаба в обмороке! 🐸", singleComment)
        assertEquals(listOf("gemini-3.5-flash-lite"), singleAttemptedModels)

        // 2. Групповая реакция Давида Жабова — успешный ответ с первой попытки от gemini-3.5-flash-lite
        val multiAttemptedModels = mutableListOf<String>()
        val multiApi = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequest
            ): GeminiResponse {
                multiAttemptedModels.add(model)
                return GeminiResponse(
                    candidates = listOf(
                        GeminiCandidate(
                            content = GeminiContent(
                                parts = listOf(GeminiPart(text = "\"Бургер и такси — классический набор финансового гусара! 🐸\""))
                            )
                        )
                    )
                )
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                alt: String,
                request: GeminiRequest
            ): Response<ResponseBody> = throw UnsupportedOperationException()
        }

        val multiComment = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = multiApi,
            apiKey = "test-key",
            request = sampleRequest,
            operationType = "Групповая реакция Давида Жабова",
            operationPrefix = "DAVID-M",
            details = "ops=2"
        )

        assertEquals("Бургер и такси — классический набор финансового гусара! 🐸", multiComment)
        assertEquals(listOf("gemini-3.5-flash-lite"), multiAttemptedModels)

        // 3. Проверка fallback-механизма при сбое первой модели
        val fallbackAttemptedModels = mutableListOf<String>()
        val fallbackApi = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequest
            ): GeminiResponse {
                fallbackAttemptedModels.add(model)
                return when (model) {
                    "gemini-3.5-flash-lite" -> GeminiResponse(
                        error = GeminiError(code = 503, message = "Overloaded", status = "UNAVAILABLE")
                    )
                    "gemini-3.8-flash" -> throw SocketTimeoutException("Read timed out")
                    else -> GeminiResponse(
                        candidates = listOf(
                            GeminiCandidate(
                                content = GeminiContent(
                                    parts = listOf(GeminiPart(text = "Резервная модель сработала штатно! 🐸"))
                                )
                            )
                        )
                    )
                }
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                alt: String,
                request: GeminiRequest
            ): Response<ResponseBody> = throw UnsupportedOperationException()
        }

        val fallbackComment = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = fallbackApi,
            apiKey = "test-key",
            request = sampleRequest,
            operationType = "Реакция Давида Жабова",
            operationPrefix = "DAVID",
            details = "cat=Фастфуд"
        )

        assertEquals("Резервная модель сработала штатно! 🐸", fallbackComment)
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.6-flash"),
            fallbackAttemptedModels
        )

        // 4. При отказе всех моделей возвращается null для активации локального комментария Давида
        val allFailApi = object : GeminiApiService {
            override suspend fun generateContent(
                model: String,
                apiKey: String,
                request: GeminiRequest
            ): GeminiResponse {
                throw SocketTimeoutException("Timeout on $model")
            }

            override suspend fun streamGenerateContent(
                model: String,
                apiKey: String,
                alt: String,
                request: GeminiRequest
            ): Response<ResponseBody> = throw UnsupportedOperationException()
        }

        val nullWhenAllFail = GeminiModelConfig.executeDavidReactionWithFallback(
            apiService = allFailApi,
            apiKey = "test-key",
            request = sampleRequest,
            operationType = "Групповая реакция Давида Жабова",
            operationPrefix = "DAVID-M",
            details = "ops=3"
        )
        assertNull(nullWhenAllFail)
    }

    @Test
    fun `sensitive data masking hides Gemini API keys, Bearer tokens, PINs, and card numbers`() {
        val rawText = """
            Request failed with x-goog-api-key: AIzaSyD9abcDEF1234567890_secretKeyValue
            Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.secretPayload
            User credentials: pin=4829, password="mySuperSecretPassword", card=4276 3800 1234 9876
        """.trimIndent()

        val masked = GlobalConsoleLogger.maskSensitive(rawText)

        assertFalse("Gemini API key must be masked", masked.contains("AIzaSyD9abcDEF1234567890_secretKeyValue"))
        assertTrue("Masked API key placeholder expected", masked.contains("[MASKED_API_KEY]"))
        assertFalse("Bearer token must be masked", masked.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertTrue("Masked Bearer placeholder expected", masked.contains("Bearer [MASKED_TOKEN]"))
        assertFalse("PIN code must be masked", masked.contains("pin=4829"))
        assertFalse("Password must be masked", masked.contains("mySuperSecretPassword"))
        assertTrue("Masked secret placeholder expected", masked.contains("[MASKED_SECRET]"))
        assertFalse("Full card number must be masked", masked.contains("4276 3800 1234 9876"))
        assertTrue("Card last 4 digits should remain", masked.contains("**** **** **** 9876"))
    }

    @Test
    fun `ring buffer enforces MAX_LOGS limit and drops oldest entries`() {
        val totalToInsert = GlobalConsoleLogger.MAX_LOGS + 45
        for (idx in 1..totalToInsert) {
            GlobalConsoleLogger.i("TEST", "Log entry number #$idx")
        }

        val currentLogs = GlobalConsoleLogger.logs.value
        assertEquals(GlobalConsoleLogger.MAX_LOGS, currentLogs.size)
        assertEquals("Log entry number #46", currentLogs.first().message)
        assertEquals("Log entry number #$totalToInsert", currentLogs.last().message)
    }

    @Test
    fun `filterLogs filters accurately by level, source tag, query, and operationId`() {
        GlobalConsoleLogger.d("UI", "ModalAddTransaction opened", operationId = "OP-101")
        GlobalConsoleLogger.i("GEMINI", "Model gemini-3.8-flash responded in 420ms", operationId = "OP-102")
        GlobalConsoleLogger.w("VOICE", "Silence timeout reached", operationId = "OP-103")
        GlobalConsoleLogger.e(
            tag = "ROOM",
            message = "SQLiteConstraintException on transactions table",
            throwable = IllegalStateException("Unique constraint failed"),
            operationId = "OP-104"
        )

        val allEntries = GlobalConsoleLogger.logs.value
        assertEquals(4, allEntries.size)

        // Filter by level WARNING
        val warnings = GlobalConsoleLogger.filterLogs(entries = allEntries, levelFilter = LogLevel.WARNING)
        assertEquals(1, warnings.size)
        assertEquals("VOICE", warnings.first().source)

        // Filter by source GEMINI
        val geminiLogs = GlobalConsoleLogger.filterLogs(entries = allEntries, sourceFilter = "GEMINI")
        assertEquals(1, geminiLogs.size)
        assertTrue(geminiLogs.first().message.contains("gemini-3.8-flash"))

        // Filter by operationId query
        val opFiltered = GlobalConsoleLogger.filterLogs(entries = allEntries, query = "OP-104")
        assertEquals(1, opFiltered.size)
        assertEquals("ROOM", opFiltered.first().source)

        // Filter by text inside stackTrace
        val stackFiltered = GlobalConsoleLogger.filterLogs(entries = allEntries, query = "Unique constraint failed")
        assertEquals(1, stackFiltered.size)
        assertEquals(LogLevel.ERROR, stackFiltered.first().level)
    }

    @Test
    fun `pause mode suppresses non-critical logs while still capturing ERROR and FATAL`() {
        GlobalConsoleLogger.setPaused(true)

        GlobalConsoleLogger.d("UI", "Ignored debug event while paused")
        GlobalConsoleLogger.i("UI", "Ignored info event while paused")
        GlobalConsoleLogger.e("GEMINI", "Critical API failure must still be recorded while paused")

        val logs = GlobalConsoleLogger.logs.value
        assertEquals(1, logs.size)
        assertEquals(LogLevel.ERROR, logs.first().level)
        assertEquals(1, GlobalConsoleLogger.errors.value.size)
    }

    @Test
    fun `handleUncaughtException records FATAL entry with masked secrets and delegates to default handler`() {
        val delegated = AtomicBoolean(false)
        var delegatedThrowable: Throwable? = null

        val fakeDefaultHandler = Thread.UncaughtExceptionHandler { _, throwable ->
            delegated.set(true)
            delegatedThrowable = throwable
        }

        val crashException = RuntimeException("Crash with key AIzaSySecretKey999888777666555444")
        GlobalConsoleLogger.handleUncaughtException(
            thread = Thread.currentThread(),
            throwable = crashException,
            delegateHandler = fakeDefaultHandler
        )

        assertTrue("Must always delegate to Android default UncaughtExceptionHandler", delegated.get())
        assertEquals(crashException, delegatedThrowable)

        val errors = GlobalConsoleLogger.errors.value
        assertEquals(1, errors.size)
        val fatalEntry = errors.first()
        assertEquals(LogLevel.FATAL, fatalEntry.level)
        assertEquals("CRASH", fatalEntry.source)
        assertFalse("Crash message must not leak raw API key", fatalEntry.message.contains("AIzaSySecretKey999888777666555444"))
        assertTrue("Crash message must contain masked placeholder", fatalEntry.message.contains("[MASKED_API_KEY]"))
        assertNotNull(fatalEntry.stackTrace)
    }

    @Test
    fun `buildDiagnosticReport and buildAiDiagnosticPrompt generate structured sanitized outputs`() {
        val opId = GlobalConsoleLogger.newOperationId("AI")
        GlobalConsoleLogger.recordNetworkCall(
            operationType = "Аудит бюджета",
            modelName = "gemini-3.8-flash",
            durationMs = 840L,
            status = NetworkCallStatus.FALLBACK,
            httpCode = 503,
            errorType = "HTTP_503",
            errorMessage = "Model overloaded with key AIzaSyTestKey1234567890123456",
            attemptNumber = 1,
            totalModelsInChain = 4,
            operationId = opId
        )
        GlobalConsoleLogger.recordDbOperation(
            operationName = "INSERT transaction",
            tableName = "transactions",
            durationMs = 14L,
            isSuccess = true,
            details = "cat=Продукты",
            operationId = "DB-0001"
        )
        GlobalConsoleLogger.e(
            tag = "GEMINI",
            message = "Timeout waiting for stream response with key AIzaSyTestKey1234567890123456",
            throwable = IllegalStateException("SocketTimeoutException"),
            operationId = opId
        )

        val report = GlobalConsoleLogger.buildDiagnosticReport(
            appVersionName = "1.42",
            appVersionCode = 42,
            buildType = "debug"
        )
        assertTrue(report.contains("OBSIDIAN NEO-BANK (DAVID FINANCE) — DIAGNOSTIC REPORT"))
        assertTrue(report.contains("v1.42 (code 42) [debug]"))
        assertTrue(report.contains("INSERT transaction"))
        assertTrue(report.contains("gemini-3.8-flash"))
        assertFalse("Report must never contain raw API key", report.contains("AIzaSyTestKey1234567890123456"))

        val aiPrompt = GlobalConsoleLogger.buildAiDiagnosticPrompt(
            targetError = GlobalConsoleLogger.errors.value.last(),
            appVersionName = "1.42",
            appVersionCode = 42,
            buildType = "debug"
        )
        assertTrue(aiPrompt.contains("# Диагностический отчёт для ИИ-разработчика"))
        assertTrue(aiPrompt.contains("https://github.com/smaks190-crypto/-.git"))
        assertTrue(aiPrompt.contains("SocketTimeoutException"))
        assertTrue(aiPrompt.contains("## 5. Задача для ИИ"))
        assertFalse("AI prompt must never contain raw API key", aiPrompt.contains("AIzaSyTestKey1234567890123456"))
    }
}
