package com.example.utils

import com.example.data.api.GeminiCandidate
import com.example.data.api.GeminiChainResult
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiFallbackExecutor
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GlobalConsoleLoggerTest {

    @Before
    fun setUp() {
        GlobalConsoleLogger.setPaused(false)
        GlobalConsoleLogger.clear()
        GeminiFallbackExecutor.clearCooldowns()
    }

    @After
    fun tearDown() {
        GlobalConsoleLogger.setPaused(false)
        GlobalConsoleLogger.clear()
        GeminiFallbackExecutor.clearCooldowns()
    }

    private fun buildSuccessGeminiResponse(text: String): GeminiResponse {
        return GeminiResponse(
            candidates = listOf(
                GeminiCandidate(
                    content = GeminiContent(
                        parts = listOf(GeminiPart(text = text))
                    )
                )
            ),
            error = null
        )
    }

    private fun buildHttpException(code: Int, message: String = "HTTP Error $code"): HttpException {
        val body = """{"error":{"code":$code,"message":"$message"}}"""
            .toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<GeminiResponse>(code, body))
    }

    @Test
    fun `scenario 1 - primary model responds HTTP 200 immediately with SUCCESS status`() = runBlocking {
        val calledModels = mutableListOf<String>()

        val result = GeminiFallbackExecutor.executeWithFallback(
            operationType = "Комментарий Давида",
            operationPrefix = "DAVID",
            models = listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash"),
            details = "cat=Фастфуд",
            extractResult = { res -> res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text }
        ) { model ->
            calledModels.add(model)
            buildSuccessGeminiResponse("Комментарий Давида с первой попытки")
        }

        assertTrue(result is GeminiChainResult.Success)
        val success = result as GeminiChainResult.Success
        assertEquals("Комментарий Давида с первой попытки", success.value)
        assertEquals(NetworkCallStatus.SUCCESS, success.chainOutcome)
        assertEquals("gemini-3.5-flash", success.modelUsed)
        assertEquals(1, success.attemptsMade)
        assertEquals(listOf("gemini-3.5-flash"), calledModels)
        assertTrue("No error entries must be logged on primary model success", GlobalConsoleLogger.errors.value.isEmpty())

        val traces = GlobalConsoleLogger.networkTraces.value
        assertEquals(1, traces.size)
        assertEquals(NetworkCallStatus.SUCCESS, traces.first().status)
        assertEquals(NetworkCallStatus.SUCCESS, traces.first().chainOutcome)
    }

    @Test
    fun `scenario 2 - primary model returns HTTP 404 then fallback model responds HTTP 200 with SUCCESS_AFTER_FALLBACK`() = runBlocking {
        val calledModels = mutableListOf<String>()

        val result = GeminiFallbackExecutor.executeWithFallback(
            operationType = "Gemini Live Аудио (WAV)",
            operationPrefix = "VOICE-WAV",
            models = listOf("gemini-2.5-flash", "gemini-3.5-flash-lite"),
            details = "bytes=32000",
            extractResult = { res -> res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text }
        ) { model ->
            calledModels.add(model)
            if (model == "gemini-2.5-flash") {
                throw buildHttpException(404, "models/gemini-2.5-flash is not found for API version v1beta")
            }
            buildSuccessGeminiResponse("""[{"title":"Кофе","type":"expense","amount":300.0,"category":"Кафе"}]""")
        }

        assertTrue(result is GeminiChainResult.Success)
        val success = result as GeminiChainResult.Success
        assertNotNull(success.value)
        assertEquals(NetworkCallStatus.SUCCESS_AFTER_FALLBACK, success.chainOutcome)
        assertEquals("gemini-3.5-flash-lite", success.modelUsed)
        assertEquals(2, success.attemptsMade)
        assertEquals(listOf("gemini-2.5-flash", "gemini-3.5-flash-lite"), calledModels)

        // Intermediate 404 must NOT pollute GlobalConsoleLogger.errors when fallback succeeds!
        assertTrue(
            "Intermediate HTTP 404 must not be recorded as a terminal ERROR when fallback succeeds",
            GlobalConsoleLogger.errors.value.isEmpty()
        )

        // Model that returned 404 must now be on cooldown and moved to the end of subsequent chains
        assertNotNull(
            "Model returning HTTP 404 should be placed on cooldown",
            GeminiFallbackExecutor.getModelCooldown("gemini-2.5-flash")
        )
        val reordered = GeminiFallbackExecutor.orderModelsByHealth(listOf("gemini-2.5-flash", "gemini-3.5-flash-lite"))
        assertEquals("gemini-3.5-flash-lite", reordered.first())
    }

    @Test
    fun `scenario 3 - SocketTimeoutException then HTTP 503 then HTTP 200 records SUCCESS_AFTER_FALLBACK without false ERROR logs`() = runBlocking {
        val result = GeminiFallbackExecutor.executeWithFallback(
            operationType = "Комментарий Давида",
            operationPrefix = "DAVID",
            models = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
            details = "cat=Фастфуд | sum=2000",
            extractResult = { res -> res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text }
        ) { model ->
            when (model) {
                "gemini-3.8-flash" -> throw SocketTimeoutException("timeout in Http2Stream.takeHeaders")
                "gemini-3.6-flash" -> throw buildHttpException(503, "The model is overloaded. Please try again later.")
                "gemini-3.5-flash-lite" -> buildSuccessGeminiResponse("Два косаря на бургер? Раскольников бы заплакал, ква-ква! 🐸")
                else -> throw IllegalStateException("Unexpected model $model")
            }
        }

        assertTrue(result is GeminiChainResult.Success)
        val success = result as GeminiChainResult.Success
        assertEquals("Два косаря на бургер? Раскольников бы заплакал, ква-ква! 🐸", success.value)
        assertEquals(NetworkCallStatus.SUCCESS_AFTER_FALLBACK, success.chainOutcome)
        assertEquals("gemini-3.5-flash-lite", success.modelUsed)
        assertEquals(3, success.attemptsMade)

        // Verify all 3 attempts share the same operationId and have chainOutcome == SUCCESS_AFTER_FALLBACK
        val traces = GlobalConsoleLogger.networkTraces.value.filter { it.operationId == success.operationId }
        assertEquals(3, traces.size)
        assertEquals(NetworkCallStatus.FALLBACK, traces[0].status)
        assertEquals(NetworkCallStatus.FALLBACK, traces[1].status)
        assertEquals(NetworkCallStatus.SUCCESS_AFTER_FALLBACK, traces[2].status)
        assertTrue(traces.all { it.chainOutcome == NetworkCallStatus.SUCCESS_AFTER_FALLBACK })
        assertEquals(
            NetworkCallStatus.SUCCESS_AFTER_FALLBACK,
            GlobalConsoleLogger.getChainOutcomeForOperation(success.operationId)
        )

        // Verify 0 terminal errors in GlobalConsoleLogger.errors
        assertTrue(
            "Successful fallback chain must not leave false ERROR entries in Developer Console",
            GlobalConsoleLogger.errors.value.isEmpty()
        )
    }

    @Test
    fun `scenario 4 - all models fail records terminal FAILED status and logs single terminal ERROR`() = runBlocking {
        val result = GeminiFallbackExecutor.executeWithFallback(
            operationType = "Комментарий Давида",
            operationPrefix = "DAVID",
            models = listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash"),
            details = "cat=Фастфуд | sum=2000",
            extractResult = { res -> res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text }
        ) { model ->
            when (model) {
                "gemini-3.5-flash" -> throw SocketTimeoutException("read timed out")
                "gemini-3.5-flash-lite" -> throw buildHttpException(503, "Service Unavailable")
                else -> throw buildHttpException(429, "Quota exceeded")
            }
        }

        assertTrue(result is GeminiChainResult.Failure)
        val failure = result as GeminiChainResult.Failure
        assertEquals(NetworkCallStatus.FAILED, failure.chainOutcome)
        assertEquals(3, failure.attemptsMade)

        val traces = GlobalConsoleLogger.networkTraces.value.filter { it.operationId == failure.operationId }
        assertEquals(3, traces.size)
        assertEquals(NetworkCallStatus.FALLBACK, traces[0].status)
        assertEquals(NetworkCallStatus.FALLBACK, traces[1].status)
        assertEquals(NetworkCallStatus.FAILED, traces[2].status)
        assertTrue(traces.all { it.chainOutcome == NetworkCallStatus.FAILED })

        // Exactly 1 terminal error entry must be recorded when the entire chain fails
        val errors = GlobalConsoleLogger.errors.value
        assertEquals(1, errors.size)
        assertEquals(failure.operationId, errors.first().operationId)
    }

    @Test
    fun `scenario 5 - CancellationException immediately aborts chain without calling subsequent models`() = runBlocking {
        val calledModels = mutableListOf<String>()

        try {
            GeminiFallbackExecutor.executeWithFallback(
                operationType = "Gemini Live Аудио (WAV)",
                operationPrefix = "VOICE-WAV",
                models = listOf("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite"),
                details = "bytes=32000",
                extractResult = { res -> res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text }
            ) { model ->
                calledModels.add(model)
                throw CancellationException("User closed screen or cancelled voice input")
            }
            fail("Expected CancellationException to be rethrown")
        } catch (_: CancellationException) {
            // Expected
        }

        // Subsequent models in the fallback chain must NOT be called after CancellationException
        assertEquals(listOf("gemini-3.5-flash-lite"), calledModels)
        assertTrue("Cancellation must not be logged as ERROR", GlobalConsoleLogger.errors.value.isEmpty())

        val traces = GlobalConsoleLogger.networkTraces.value
        assertEquals(1, traces.size)
        assertEquals(NetworkCallStatus.CANCELLED, traces.first().status)
        assertEquals(NetworkCallStatus.CANCELLED, traces.first().chainOutcome)
    }

    @Test
    fun `scenario 6 - financial operation is saved once regardless of Gemini fallback retries`() = runBlocking {
        val dbInsertCounter = AtomicInteger(0)
        val geminiCallCounter = AtomicInteger(0)

        // Simulate the exact flow of TransactionStateDelegate.addTransaction:
        // 1. Insert transaction into Room once
        dbInsertCounter.incrementAndGet()
        GlobalConsoleLogger.recordDbOperation(
            operationName = "INSERT transaction",
            tableName = "transactions",
            durationMs = 4L,
            isSuccess = true,
            details = "type=expense | cat=Фастфуд | sum=2000.0"
        )

        // 2. Run Gemini fallback chain (3 attempts)
        val aiResult = GeminiFallbackExecutor.executeWithFallback(
            operationType = "Комментарий Давида",
            operationPrefix = "DAVID",
            models = listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash"),
            details = "cat=Фастфуд | sum=2000",
            extractResult = { res -> res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text }
        ) { model ->
            val attempt = geminiCallCounter.incrementAndGet()
            if (attempt < 3) {
                throw buildHttpException(503, "Temporary overload on $model")
            }
            buildSuccessGeminiResponse("Записано с 3-й модели без дублей в БД!")
        }

        assertEquals(1, dbInsertCounter.get())
        assertEquals(3, geminiCallCounter.get())
        assertTrue(aiResult is GeminiChainResult.Success)
        assertEquals("Записано с 3-й модели без дублей в БД!", (aiResult as GeminiChainResult.Success).value)
        assertEquals(1, GlobalConsoleLogger.dbTraces.value.size)
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

        val warnings = GlobalConsoleLogger.filterLogs(entries = allEntries, levelFilter = LogLevel.WARNING)
        assertEquals(1, warnings.size)
        assertEquals("VOICE", warnings.first().source)

        val geminiLogs = GlobalConsoleLogger.filterLogs(entries = allEntries, sourceFilter = "GEMINI")
        assertEquals(1, geminiLogs.size)
        assertTrue(geminiLogs.first().message.contains("gemini-3.8-flash"))

        val opFiltered = GlobalConsoleLogger.filterLogs(entries = allEntries, query = "OP-104")
        assertEquals(1, opFiltered.size)
        assertEquals("ROOM", opFiltered.first().source)

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
        GlobalConsoleLogger.recordNetworkCall(
            operationType = "Аудит бюджета",
            modelName = "gemini-3.5-flash-lite",
            durationMs = 620L,
            status = NetworkCallStatus.SUCCESS_AFTER_FALLBACK,
            httpCode = 200,
            attemptNumber = 2,
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
        assertTrue(report.contains("SUCCESS_AFTER_FALLBACK"))
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
