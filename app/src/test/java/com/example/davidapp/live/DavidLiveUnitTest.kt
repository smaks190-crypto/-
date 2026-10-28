package com.example.davidapp.live

import com.example.data.db.AccountDao
import com.example.data.db.AccountEntity
import com.example.data.db.CategoryDao
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalDao
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidLiveUnitTest {

    @Test
    fun `send button gesture helper distinguishes short tap and 550ms long press without clearing draft`() {
        // 1. Короткое нажатие (< 550 мс) при непустом тексте отправляет сообщение
        val shortTap = DavidSendButtonGestureHelper.evaluatePress(
            pressDurationMs = 180L,
            currentDraftText = "Такси 450"
        )
        assertEquals(SendButtonGestureAction.SHORT_TAP_SEND, shortTap.action)
        assertFalse(shortTap.shouldTriggerHaptic)
        assertEquals("", shortTap.resultingDraftText)
        assertEquals("Такси 450", shortTap.messageSent)

        // 2. Короткое нажатие при пустом поле ничего не делает
        val emptyTap = DavidSendButtonGestureHelper.evaluatePress(
            pressDurationMs = 150L,
            currentDraftText = "   "
        )
        assertEquals(SendButtonGestureAction.NONE, emptyTap.action)
        assertNull(emptyTap.messageSent)

        // 3. Долгое нажатие (>= 550 мс) запускает David Live, включает виброотклик,
        // НЕ удаляет и НЕ отправляет уже набранный текст
        val longPressWithDraft = DavidSendButtonGestureHelper.evaluatePress(
            pressDurationMs = 560L,
            currentDraftText = "Черновик вопроса про бюджет"
        )
        assertEquals(SendButtonGestureAction.LONG_PRESS_START_LIVE, longPressWithDraft.action)
        assertTrue(longPressWithDraft.shouldTriggerHaptic)
        assertEquals("Черновик вопроса про бюджет", longPressWithDraft.resultingDraftText)
        assertNull(longPressWithDraft.messageSent)
    }

    @Test
    fun `spectrum analyzer produces 48 bounded radial rays with center pole emphasis and FFT frequency separation`() {
        val analyzer = DavidLiveSpectrumAnalyzer(sampleRate = 16_000, fftSize = 1024)
        assertEquals(48, DavidLiveSpectrumAnalyzer.RAY_COUNT)

        // Пространственная огибающая: верхний (0) и нижний (24) полюса крупнее, чем боковые края (12 и 36)
        val envelope = analyzer.getRadialSpatialEnvelope()
        assertEquals(48, envelope.size)
        assertTrue(
            "Top pole ray should dominate right side ray",
            envelope[0] > envelope[12]
        )
        assertTrue(
            "Bottom pole ray should dominate left side ray",
            envelope[24] > envelope[36]
        )

        // Тишина плавно затухает к 0
        val silenceFrame = ShortArray(1024) { 0 }
        val quietRays = analyzer.analyzePcmFrame(silenceFrame, silenceFrame.size, 16_000)
        assertEquals(48, quietRays.size)
        assertTrue(quietRays.all { it in 0f..0.05f })

        // Высокочастотный сигнал (2400 Гц) возбуждает верхние/центральные полосы сильнее низких
        val highTone = ShortArray(1024) { idx ->
            (sin(2.0 * PI * 2400.0 * idx / 16_000.0) * 18000).toInt().toShort()
        }
        repeat(4) { analyzer.analyzePcmFrame(highTone, highTone.size, 16_000) }
        val highBands = analyzer.getRawFrequencyBands()
        val lowBandAvg = highBands.sliceArray(0..5).average()
        val upperBandAvg = highBands.sliceArray(18..28).average()
        assertTrue(
            "High vocal frequency should activate upper FFT bands ($upperBandAvg) more than low bands ($lowBandAvg)",
            upperBandAvg > lowBandAvg
        )
        assertTrue("Overall energy should be positive during speech", analyzer.overallEnergy > 0.05f)
    }

    @Test
    fun `financial tools provider is strictly read-only and returns real verified data on demand`() = runBlocking {
        val now = System.currentTimeMillis()
        val fakeTransactions = listOf(
            TransactionEntity(
                id = "tx1",
                budgetId = "b1",
                type = "income",
                date = "2026-10-25",
                category = "Зарплата",
                subcategory = "Аванс",
                amount = 120_000.0,
                createdAt = now - 86_400_000L
            ),
            TransactionEntity(
                id = "tx2",
                budgetId = "b1",
                type = "expense",
                date = "2026-10-26",
                category = "Продукты",
                subcategory = "Супермаркет",
                amount = 15_000.0,
                createdAt = now - 43_200_000L
            ),
            TransactionEntity(
                id = "tx3",
                budgetId = "b1",
                type = "expense",
                date = "2026-10-27",
                category = "Кафе",
                subcategory = "Кофе",
                amount = 3_500.0,
                createdAt = now - 10_000L
            )
        )
        val fakeCategories = listOf(
            CategoryEntity(id = "c1", budgetId = "b1", type = "expense", name = "Продукты", monthlyLimit = 25_000.0),
            CategoryEntity(id = "c2", budgetId = "b1", type = "expense", name = "Кафе", monthlyLimit = 5_000.0)
        )
        val fakeGoals = listOf(
            GoalEntity(id = "g1", budgetId = "b1", name = "Подушка безопасности", targetAmount = 300_000.0, currentAmount = 150_000.0)
        )
        val fakeAccounts = listOf(
            AccountEntity(id = "a1", budgetId = "b1", name = "Основная карта", balance = 101_500.0, type = "card")
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(fakeTransactions),
            categoryDao = createReadOnlyCategoryDao(fakeCategories),
            goalDao = createReadOnlyGoalDao(fakeGoals),
            accountDao = createReadOnlyAccountDao(fakeAccounts)
        )

        // Гарантия режима только для чтения
        assertFalse("David Live must never mutate financial records without confirmation", provider.canMutateRecords)
        assertEquals(5, provider.buildFunctionDeclarationsJson().length())

        // 1. Сводка бюджета
        val overview = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        assertEquals("ok", overview.getString("status"))
        assertEquals(101500L, overview.getLong("totalBalanceRub"))
        assertEquals(120000L, overview.getLong("income30dRub"))
        assertEquals(18500L, overview.getLong("expense30dRub"))

        // 2. Категории и лимиты
        val catResult = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_CATEGORY_SPENDING,
            JSONObject().put("categoryQuery", "Продукты")
        )
        val catsArray = catResult.getJSONArray("categories")
        assertEquals(1, catsArray.length())
        assertEquals(15000L, catsArray.getJSONObject(0).getLong("spentRub"))
        assertEquals(60L, catsArray.getJSONObject(0).getLong("limitUsagePercent"))

        // 3. Цели
        val goalsResult = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_GOALS_AND_DEBTS, null)
        assertEquals(1, goalsResult.getInt("count"))

        // Все проверенные факты сохранены для пост-резюме
        assertEquals(3, provider.verifiedFacts.size)
        assertTrue(provider.verifiedFacts.any { it.summary.contains("101500") })
    }

    @Test
    fun `summary generator skips short calls and grounds summary strictly in real transcript and facts`() = runBlocking {
        val generator = DavidLiveSummaryGenerator(geminiApiService = null)

        // 1. Слишком короткий звонок пропускается без создания карточки резюме
        val shortTurns = listOf(
            DavidLiveTranscriptTurn(speaker = DavidLiveSpeaker.USER, text = "Привет")
        )
        val skipped = generator.generateSummary(
            userApiKey = "AIzaSyValidUserKey123456789",
            turns = shortTurns,
            verifiedFacts = emptyList()
        )
        assertTrue(skipped is DavidLiveSummaryGenerator.SummaryOutcome.SkippedTooShort)

        // 2. Содержательный диалог с проверенными фактами формирует достоверное резюме без выдуманных цифр
        val turns = listOf(
            DavidLiveTranscriptTurn(
                speaker = DavidLiveSpeaker.USER,
                text = "Давид, сколько я потратил на продукты и хватит ли мне бюджета до конца месяца?"
            ),
            DavidLiveTranscriptTurn(
                speaker = DavidLiveSpeaker.DAVID,
                text = "На продукты ушло 15 000 ₽ из лимита 25 000 ₽ (60%). Прогноз остатка к концу месяца положительный."
            )
        )
        val facts = listOf(
            DavidLiveToolFact(
                toolName = DavidLiveFinancialToolsProvider.TOOL_CATEGORY_SPENDING,
                summary = "Продукты: 15000 ₽ (лимит 25000 ₽, исчерпан на 60%)"
            )
        )

        val outcome = generator.generateSummary(
            userApiKey = "AIzaSyValidUserKey123456789",
            turns = turns,
            verifiedFacts = facts
        )
        assertTrue(outcome is DavidLiveSummaryGenerator.SummaryOutcome.Success)
        val summary = (outcome as DavidLiveSummaryGenerator.SummaryOutcome.Success).summary
        assertNotNull(summary)
        assertTrue(summary.topics.first().contains("продукты", ignoreCase = true))
        assertTrue(summary.keyFindings.any { it.contains("15000") })
        assertTrue("No unagreed next actions should be fabricated", summary.nextActions.isEmpty())
    }

    @Test
    fun `session controller rejects missing or placeholder API keys and manages mic mute and transcripts`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)

        assertFalse(controller.validateUserApiKey(null))
        assertFalse(controller.validateUserApiKey(""))
        assertFalse(controller.validateUserApiKey("MY_GEMINI_API_KEY"))
        assertTrue(controller.validateUserApiKey("AIzaSyTestUserManualKey987654321"))

        controller.startSession("")
        assertEquals(DavidLiveConnectionState.NO_API_KEY, controller.connectionState.value)

        // Проверка mute/unmute
        assertFalse(controller.isMicMuted.value)
        controller.toggleMicMute()
        assertTrue(controller.isMicMuted.value)
        controller.toggleMicMute()
        assertFalse(controller.isMicMuted.value)

        // Запись реплик и завершение сессии
        controller.recordTranscriptTurn(DavidLiveSpeaker.USER, "Сколько осталось в категории кафе?")
        controller.recordTranscriptTurn(DavidLiveSpeaker.DAVID, "В категории кафе израсходовано 3500 ₽ из 5000 ₽.")
        val (capturedTurns, _) = controller.endSession()
        assertEquals(2, capturedTurns.size)
        assertEquals(DavidLiveConnectionState.ENDED, controller.connectionState.value)
    }

    private fun createReadOnlyTransactionDao(items: List<TransactionEntity>) = object : TransactionDao {
        override fun getAllTransactions(): Flow<List<TransactionEntity>> = flowOf(items)
        override fun getTransactionsByBudgetId(budgetId: String): Flow<List<TransactionEntity>> =
            flowOf(items.filter { it.budgetId == budgetId })
        override suspend fun insertTransaction(transaction: TransactionEntity) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun insertTransactions(transactions: List<TransactionEntity>) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun getExpenseCountForDate(dateStr: String): Int = 0
        override suspend fun getExpenseSumForDate(dateStr: String): Double = 0.0
        override fun getExpenseCountForDateSync(dateStr: String): Int = 0
        override suspend fun deleteTransactionById(id: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteTransactionsByBudgetId(budgetId: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteAllTransactions() =
            throw UnsupportedOperationException("Read-only in David Live")
    }

    private fun createReadOnlyCategoryDao(items: List<CategoryEntity>) = object : CategoryDao {
        override fun getAllCategories(): Flow<List<CategoryEntity>> = flowOf(items)
        override fun getCategoriesByBudgetId(budgetId: String): Flow<List<CategoryEntity>> =
            flowOf(items.filter { it.budgetId == budgetId })
        override suspend fun insertCategory(category: CategoryEntity) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun insertCategories(categories: List<CategoryEntity>) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteCategoryById(id: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteCategoriesByBudgetId(budgetId: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteAllCategories() =
            throw UnsupportedOperationException("Read-only in David Live")
    }

    private fun createReadOnlyGoalDao(items: List<GoalEntity>) = object : GoalDao {
        override fun getAllGoals(): Flow<List<GoalEntity>> = flowOf(items)
        override fun getGoalsByBudgetId(budgetId: String): Flow<List<GoalEntity>> =
            flowOf(items.filter { it.budgetId == budgetId })
        override suspend fun insertGoal(goal: GoalEntity) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun insertGoals(goals: List<GoalEntity>) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteGoalById(id: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteGoalsByBudgetId(budgetId: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteAllGoals() =
            throw UnsupportedOperationException("Read-only in David Live")
    }

    private fun createReadOnlyAccountDao(items: List<AccountEntity>) = object : AccountDao {
        override fun getAccountsByBudgetId(budgetId: String): Flow<List<AccountEntity>> =
            flowOf(items.filter { it.budgetId == budgetId })
        override suspend fun getAccountById(id: String): AccountEntity? = items.firstOrNull { it.id == id }
        override suspend fun updateBalance(id: String, delta: Double) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun insertAccount(account: AccountEntity) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun insertAccounts(accounts: List<AccountEntity>) =
            throw UnsupportedOperationException("Read-only in David Live")
        override fun getAllAccounts(): Flow<List<AccountEntity>> = flowOf(items)
        override suspend fun deleteAllAccounts() =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteAccountById(id: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteAccountsByBudgetId(budgetId: String) =
            throw UnsupportedOperationException("Read-only in David Live")
    }
}
