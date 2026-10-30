package com.example.davidapp.live

import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AccountDao
import com.example.data.db.AccountEntity
import com.example.data.db.CategoryDao
import com.example.data.db.CategoryEntity
import com.example.data.db.GoalDao
import com.example.data.db.GoalEntity
import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import com.example.utils.VoiceInputManager
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
    fun `audio engine binds output attributes to Android USAGE_ASSISTANT for neural network volume control`() {
        val engine = DavidLiveAudioEngine()
        val attrs = engine.buildAssistantAudioAttributes()
        assertEquals(AudioAttributes.USAGE_ASSISTANT, attrs.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attrs.contentType)
        assertEquals(AudioAttributes.USAGE_ASSISTANT, DavidLiveAudioEngine.AUDIO_USAGE_ASSISTANT)
    }

    @Test
    fun `session controller filters out English model thought blocks and displays clean Russian outputTranscription subtitles`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)

        // 1. Модель присылает внутренние размышления (Chain-of-Thought) в modelTurn.parts
        val thoughtFrameJson = """
            {
              "serverContent": {
                "modelTurn": {
                  "parts": [
                    {
                      "text": "**Crafting The Greeting**\n\nI'm working on the appropriate response, focusing on a lively and slightly playful tone.",
                      "thought": true
                    },
                    {
                      "text": "**Providing Budget Details**\n\nI need to use the 'get_financial_overview' tool. My goal is to summarize the user's balance."
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        controller.processServerMessageForTest(thoughtFrameJson)

        // Субтитры НЕ должны показывать английские мысли модели
        assertFalse(controller.liveSubtitle.value.contains("Crafting The Greeting"))
        assertFalse(controller.liveSubtitle.value.contains("Providing Budget Details"))
        assertFalse(controller.liveSubtitle.value.contains("I'm working on"))

        // 2. Следом приходит реальная русская речь Давида через outputTranscription
        val russianSpeechChunk1 = """
            {
              "serverContent": {
                "outputTranscription": {
                  "text": "Слушай, цифры интересные! "
                }
              }
            }
        """.trimIndent()
        val russianSpeechChunk2 = """
            {
              "serverContent": {
                "outputTranscription": {
                  "text": "Твой общий баланс — 217 тысяч рублей."
                },
                "turnComplete": true
              }
            }
        """.trimIndent()

        controller.processServerMessageForTest(russianSpeechChunk1)
        assertEquals("Слушай, цифры интересные!", controller.liveSubtitle.value)

        controller.processServerMessageForTest(russianSpeechChunk2)
        assertEquals(
            "Слушай, цифры интересные! Твой общий баланс — 217 тысяч рублей.",
            controller.liveSubtitle.value
        )

        // В истории реплик для итогового резюме сохранён только чистый русский ответ Давида без мыслей
        assertEquals(1, controller.transcriptTurns.size)
        assertEquals(DavidLiveSpeaker.DAVID, controller.transcriptTurns.first().speaker)
        assertEquals(
            "Слушай, цифры интересные! Твой общий баланс — 217 тысяч рублей.",
            controller.transcriptTurns.first().text
        )
    }

    @Test
    fun `voice input manager live BidiGenerateContent mode captures operations and self-corrections via toolCall`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val voiceManager = VoiceInputManager(context)
        val capturedBatches = mutableListOf<List<com.example.data.repository.ParsedVoiceOperation>>()
        val capturedCorrections = mutableListOf<com.example.utils.VoiceOperationCorrection>()

        voiceManager.onOperationsCaptured = { ops -> capturedBatches.add(ops) }
        voiceManager.onLastOperationCorrected = { corr -> capturedCorrections.add(corr) }

        assertEquals(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            VoiceInputManager.LIVE_STT_MODELS.first()
        )

        val funcDecls = VoiceInputManager.buildVoiceOperationsFunctionDeclarations()
        assertEquals(2, funcDecls.length())
        assertEquals(
            VoiceInputManager.TOOL_RECORD_VOICE_OPERATIONS,
            funcDecls.getJSONObject(0).getString("name")
        )
        assertEquals(
            VoiceInputManager.TOOL_CORRECT_LAST_VOICE_OPERATION,
            funcDecls.getJSONObject(1).getString("name")
        )

        val nativeSetup = VoiceInputManager.buildLiveTranscriptionSetupJson(
            modelName = VoiceInputManager.LIVE_STT_MODELS.first(),
            expenseCategories = listOf("Продукты", "Кафе", "Транспорт"),
            incomeCategories = listOf("Зарплата")
        )
        val nativeSetupObj = nativeSetup.getJSONObject("setup")
        assertTrue(nativeSetupObj.has("inputAudioTranscription"))
        assertTrue(nativeSetupObj.has("systemInstruction"))
        assertTrue(nativeSetupObj.has("tools"))
        assertEquals(
            "AUDIO",
            nativeSetupObj.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0)
        )

        val setup = VoiceInputManager.buildLiveTranscriptionSetupJson("models/gemini-2.0-flash-live-001")
        val setupObj = setup.getJSONObject("setup")
        assertTrue(setupObj.has("inputAudioTranscription"))
        assertFalse(setupObj.has("outputAudioTranscription"))
        assertTrue(setupObj.has("tools"))
        assertEquals(
            "TEXT",
            setupObj.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0)
        )

        // 1. Живая транскрипция речи пользователя обновляет UI-строку
        voiceManager.processLiveServerMessageForTest(
            """{"serverContent":{"inputTranscription":{"text":"Кофе 350 и булочка 200 "}}}"""
        )
        assertEquals("Кофе 350 и булочка 200", voiceManager.recognizedText.value)

        // 2. Приходит toolCall record_voice_operations — операции мгновенно захватываются без HTTP POST
        voiceManager.processLiveServerMessageForTest(
            """
            {
              "toolCall": {
                "functionCalls": [
                  {
                    "id": "call-1",
                    "name": "record_voice_operations",
                    "args": {
                      "operations": [
                        {"title": "Кофе", "amount": 350, "type": "expense", "category": "Кафе", "subcategory": "Кофе"},
                        {"title": "Булочка", "amount": 200, "type": "expense", "category": "Кафе", "subcategory": "Булочка"}
                      ]
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        assertEquals(1, capturedBatches.size)
        assertEquals(2, capturedBatches.first().size)
        assertEquals("Кофе", capturedBatches.first()[0].title)
        assertEquals(350.0, capturedBatches.first()[0].amount, 0.01)
        assertEquals("Булочка", capturedBatches.first()[1].title)
        assertEquals(200.0, capturedBatches.first()[1].amount, 0.01)

        // 3. Пользователь поправляет себя на лету («а нет, булочка 150») -> toolCall correct_last_voice_operation
        voiceManager.processLiveServerMessageForTest(
            """
            {
              "toolCall": {
                "functionCalls": [
                  {
                    "id": "call-2",
                    "name": "correct_last_voice_operation",
                    "args": {
                      "title": "Булочка",
                      "amount": 150,
                      "category": "Кафе"
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        assertEquals(1, capturedCorrections.size)
        assertEquals("Булочка", capturedCorrections.first().title)
        assertEquals(150.0, capturedCorrections.first().amount ?: 0.0, 0.01)
    }

    @Test
    fun `TransactionStateDelegate captures live operations and applies on-the-fly self-correction with ticker events`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        val delegateJob = kotlinx.coroutines.SupervisorJob()
        val delegateScope = kotlinx.coroutines.CoroutineScope(
            delegateJob + kotlinx.coroutines.Dispatchers.Default
        )
        try {
            val repo = com.example.data.repository.BudgetRepository(
                context = context,
                budgetProfileDao = db.budgetProfileDao(),
                transactionDao = db.transactionDao(),
                goalDao = db.goalDao(),
                categoryDao = db.categoryDao(),
                aiAuditDao = db.aiAuditDao(),
                accountDao = db.accountDao(),
                notificationDao = db.notificationDao(),
                db = db
            )
            val voiceManager = VoiceInputManager(context)
            val parsedOpsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.repository.ParsedVoiceOperation>?>(null)
            val isAnalyzingFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
            val isVoiceActiveFlow = kotlinx.coroutines.flow.MutableStateFlow(true)

            val delegate = com.example.ui.viewmodel.TransactionStateDelegate(
                context = context,
                repository = repo,
                scope = delegateScope,
                selectedBudgetId = kotlinx.coroutines.flow.MutableStateFlow("default"),
                apiKey = kotlinx.coroutines.flow.MutableStateFlow("AIzaSyTestKey1234567890"),
                selectedDateDay = kotlinx.coroutines.flow.MutableStateFlow("2026-10-29"),
                toastMessage = kotlinx.coroutines.flow.MutableSharedFlow(extraBufferCapacity = 8),
                isGeneratingReaction = kotlinx.coroutines.flow.MutableStateFlow(false),
                isAnalyzingVoice = isAnalyzingFlow,
                voiceErrorMessage = kotlinx.coroutines.flow.MutableStateFlow(null),
                parsedVoiceOperations = parsedOpsFlow,
                completedGoalEvent = kotlinx.coroutines.flow.MutableStateFlow(null),
                transactions = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                accounts = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                goals = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                categories = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
                voiceInputManager = voiceManager,
                isVoiceActive = isVoiceActiveFlow,
                manualText = kotlinx.coroutines.flow.MutableStateFlow(""),
                aiAuditResult = kotlinx.coroutines.flow.MutableStateFlow(null),
                aiAuditLoading = kotlinx.coroutines.flow.MutableStateFlow(false),
                getSavedApiKey = { "AIzaSyTestKey1234567890" }
            )

            delegate.appendLiveCapturedVoiceOperations(
                listOf(
                    com.example.data.repository.ParsedVoiceOperation(
                        title = "Кофе",
                        type = "expense",
                        amount = 350.0,
                        category = "Кафе",
                        subcategory = "Кофе",
                        date = "2026-10-29"
                    ),
                    com.example.data.repository.ParsedVoiceOperation(
                        title = "Булочка",
                        type = "expense",
                        amount = 200.0,
                        category = "Кафе",
                        subcategory = "Булочка",
                        date = "2026-10-29"
                    )
                )
            )

            val afterCapture = parsedOpsFlow.value
            assertNotNull(afterCapture)
            assertEquals(2, afterCapture!!.size)
            val captureTicker = delegate.latestVoiceTickerEvent.value
            assertNotNull(captureTicker)
            assertFalse(captureTicker!!.isCorrection)
            assertEquals("+ Булочка 200 ₽", captureTicker.formattedChipText)

            // Применяем live-самоисправление («а нет, булочка 150»)
            delegate.correctLastCapturedVoiceOperation(
                com.example.utils.VoiceOperationCorrection(
                    amount = 150.0,
                    title = "Булочка",
                    category = "Кафе",
                    subcategory = "Булочка",
                    type = "expense"
                )
            )

            val afterCorrection = parsedOpsFlow.value
            assertNotNull(afterCorrection)
            assertEquals(2, afterCorrection!!.size)
            assertEquals(350.0, afterCorrection[0].amount, 0.01)
            assertEquals(150.0, afterCorrection[1].amount, 0.01)

            val correctionTicker = delegate.latestVoiceTickerEvent.value
            assertNotNull(correctionTicker)
            assertTrue(correctionTicker!!.isCorrection)
            assertEquals("✎ Булочка 150 ₽", correctionTicker.formattedChipText)

            delegate.clearParsedVoiceOperations()
            assertNull(delegate.latestVoiceTickerEvent.value)
        } finally {
            delegateJob.cancel()
            db.close()
        }
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
        assertEquals(101500L, overview.getLong("availableBalanceRub"))
        assertEquals(120000L, overview.getLong("income30dRub"))
        assertEquals(120000L, overview.getLong("earnedIncome30dRub"))
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
    fun `issue 43 - financial semantics calculator and live tools strictly separate salary, balance, we_owe and owes_us`() = runBlocking {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val now = System.currentTimeMillis()

        // Сценарий 1: Отдельно только зарплата
        val salaryOnlySnap = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = listOf(
                TransactionEntity(id = "s1", budgetId = "b1", type = "income", date = today, category = "Зарплата", subcategory = "Основная работа", amount = 100_000.0, createdAt = now)
            ),
            periodTransactions = listOf(
                TransactionEntity(id = "s1", budgetId = "b1", type = "income", date = today, category = "Зарплата", subcategory = "Основная работа", amount = 100_000.0, createdAt = now)
            ),
            accounts = emptyList()
        )
        assertEquals(100_000.0, salaryOnlySnap.availableBalanceRub, 0.01)
        assertEquals(100_000.0, salaryOnlySnap.earnedIncomeRub, 0.01)
        assertEquals(100_000.0, salaryOnlySnap.salaryIncomeRub, 0.01)
        assertEquals(0.0, salaryOnlySnap.totalWeOweRub, 0.01)
        assertEquals(0.0, salaryOnlySnap.totalOwesUsRub, 0.01)

        // Сценарий 2: Отдельно только корректировка начального/текущего баланса (не должна раздувать заработанный доход)
        val balanceOnlyTxs = listOf(
            TransactionEntity(id = "b_init", budgetId = "b1", type = "income", date = today, category = "Начальный баланс", subcategory = "Остаток на карте", amount = 50_000.0, createdAt = now)
        )
        val balanceOnlySnap = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = balanceOnlyTxs,
            periodTransactions = balanceOnlyTxs,
            accounts = emptyList()
        )
        assertEquals(50_000.0, balanceOnlySnap.availableBalanceRub, 0.01)
        assertEquals("Starting balance must NOT be counted as earned period income", 0.0, balanceOnlySnap.earnedIncomeRub, 0.01)
        assertEquals(50_000.0, balanceOnlySnap.balanceAdjustmentRub, 0.01)

        // Сценарий 3: Отдельно только мой долг (we_owe) и отдельно только долг мне (owes_us)
        val weOweAcc = AccountEntity(id = "d_we", budgetId = "b1", name = "Кредитка Альфа", balance = 30_000.0, type = "we_owe")
        val owesUsAcc = AccountEntity(id = "d_they", budgetId = "b1", name = "Иван (займ)", balance = 15_000.0, type = "owes_us")

        val weOweOnlySnap = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = emptyList(),
            periodTransactions = emptyList(),
            accounts = listOf(weOweAcc)
        )
        assertEquals(0.0, weOweOnlySnap.availableBalanceRub, 0.01)
        assertEquals(0.0, weOweOnlySnap.earnedIncomeRub, 0.01)
        assertEquals(30_000.0, weOweOnlySnap.totalWeOweRub, 0.01)
        assertEquals(0.0, weOweOnlySnap.totalOwesUsRub, 0.01)

        val owesUsOnlySnap = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = emptyList(),
            periodTransactions = emptyList(),
            accounts = listOf(owesUsAcc)
        )
        assertEquals(0.0, owesUsOnlySnap.availableBalanceRub, 0.01)
        assertEquals(0.0, owesUsOnlySnap.earnedIncomeRub, 0.01)
        assertEquals(0.0, owesUsOnlySnap.totalWeOweRub, 0.01)
        assertEquals(15_000.0, owesUsOnlySnap.totalOwesUsRub, 0.01)

        // Сценарий 4: Все 4 сущности одновременно в БД и через инструменты David Live
        // Доступный баланс НЕ суммируется с AccountEntity.balance (где хранятся долги),
        // а доход НЕ включает ни начальный баланс, ни долги.
        val combinedTxs = listOf(
            TransactionEntity(id = "t_init", budgetId = "b1", type = "income", date = today, category = "Начальный баланс", subcategory = "Ввод остатка", amount = 50_000.0, createdAt = now - 40_000L),
            TransactionEntity(id = "t_sal", budgetId = "b1", type = "income", date = today, category = "Зарплата", subcategory = "Аванс и оклад", amount = 100_000.0, createdAt = now - 30_000L),
            TransactionEntity(id = "t_exp", budgetId = "b1", type = "expense", date = today, category = "Продукты", subcategory = "Супермаркет", amount = 10_000.0, createdAt = now - 20_000L)
        )
        val combinedDebts = listOf(weOweAcc, owesUsAcc)

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(combinedTxs),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(combinedDebts)
        )

        val overview1 = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        // Повторный запрос должен вернуть идентичные цифры без дублирования или мутации
        val overview2 = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)

        // Доступный остаток = 50 000 + 100 000 - 10 000 = 140 000 ₽ (НЕ +30 000 и НЕ +15 000!)
        assertEquals(140_000L, overview1.getLong("availableBalanceRub"))
        assertEquals(140_000L, overview1.getLong("totalBalanceRub"))
        assertEquals(overview1.getLong("availableBalanceRub"), overview2.getLong("availableBalanceRub"))

        // Заработанный доход = 100 000 ₽ (только зарплата, без начального баланса 50 000 ₽ и без долгов 30 000 / 15 000 ₽)
        assertEquals(100_000L, overview1.getLong("earnedIncome30dRub"))
        assertEquals(100_000L, overview1.getLong("income30dRub"))
        assertEquals(100_000L, overview1.getLong("salaryIncome30dRub"))

        // Долги строго в отдельных полях
        assertEquals(30_000L, overview1.getLong("totalWeOweRub"))
        assertEquals(15_000L, overview1.getLong("totalOwesUsRub"))

        val debtsToolResult = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_GOALS_AND_DEBTS, null)
        assertEquals(2, debtsToolResult.getInt("debtsCount"))
        assertEquals(30_000L, debtsToolResult.getLong("totalWeOweRub"))
        assertEquals(15_000L, debtsToolResult.getLong("totalOwesUsRub"))
        assertEquals(1, debtsToolResult.getJSONArray("weOweDebts").length())
        assertEquals(1, debtsToolResult.getJSONArray("owesUsDebts").length())
    }

    @Test
    fun `issue 43 - partial and full debt repayments update remaining debt and available balance without inflating earned income`() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val weOweAcc = AccountEntity(id = "d_we", budgetId = "b1", name = "Кредитка", balance = 30_000.0, type = "we_owe")
        val owesUsAcc = AccountEntity(id = "d_they", budgetId = "b1", name = "Иван", balance = 15_000.0, type = "owes_us")

        // Частичное погашение нашего долга (10 000 ₽) и частичный возврат долга нам от Ивана (5 000 ₽)
        val txsPartial = listOf(
            TransactionEntity(id = "t1", budgetId = "b1", type = "income", date = today, category = "Зарплата", subcategory = "Оклад", amount = 100_000.0),
            TransactionEntity(id = "t2", budgetId = "b1", type = "expense", date = today, category = "Долги", subcategory = "Выплата по долгу: Кредитка", amount = 10_000.0, accountId = "d_we"),
            TransactionEntity(id = "t3", budgetId = "b1", type = "income", date = today, category = "Долги", subcategory = "Возврат долга: Иван", amount = 5_000.0, accountId = "d_they")
        )

        val partialSnap = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = txsPartial,
            periodTransactions = txsPartial,
            accounts = listOf(weOweAcc, owesUsAcc)
        )

        // Заработанный доход остаётся ровно 100 000 ₽ (возврат долга 5 000 ₽ — это debtReturnedIncomeRub, а не заработанный доход!)
        assertEquals(100_000.0, partialSnap.earnedIncomeRub, 0.01)
        assertEquals(5_000.0, partialSnap.debtReturnedIncomeRub, 0.01)
        assertEquals(10_000.0, partialSnap.debtRepaymentExpenseRub, 0.01)
        // Доступный баланс на руках: 100 000 - 10 000 + 5 000 = 95 000 ₽
        assertEquals(95_000.0, partialSnap.availableBalanceRub, 0.01)
        // Остаток нашего долга: 30 000 - 10 000 = 20 000 ₽; остаток долга Ивана нам: 15 000 - 5 000 = 10 000 ₽
        assertEquals(20_000.0, partialSnap.totalWeOweRub, 0.01)
        assertEquals(10_000.0, partialSnap.totalOwesUsRub, 0.01)

        // Полное погашение обоих долгов
        val txsFull = txsPartial + listOf(
            TransactionEntity(id = "t4", budgetId = "b1", type = "expense", date = today, category = "Долги", subcategory = "Кредитка закрытие", amount = 20_000.0, accountId = "d_we"),
            TransactionEntity(id = "t5", budgetId = "b1", type = "income", date = today, category = "Долги", subcategory = "Иван вернул остаток", amount = 10_000.0, accountId = "d_they")
        )
        val fullSnap = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = txsFull,
            periodTransactions = txsFull,
            accounts = listOf(weOweAcc, owesUsAcc)
        )
        assertEquals(100_000.0, fullSnap.earnedIncomeRub, 0.01)
        assertEquals(15_000.0, fullSnap.debtReturnedIncomeRub, 0.01)
        assertEquals(30_000.0, fullSnap.debtRepaymentExpenseRub, 0.01)
        assertEquals(85_000.0, fullSnap.availableBalanceRub, 0.01)
        assertEquals(0.0, fullSnap.totalWeOweRub, 0.01)
        assertEquals(0.0, fullSnap.totalOwesUsRub, 0.01)
    }

    @Test
    fun `issue 43 - conversational mentions and heuristic voice parser do not mix salary, balance and debts`() {
        val text = "Зарплата 100к, текущий баланс 50к, я должен 30к, мне должны 15к"

        // 1. Проверка извлечения сущностей из реплики диалога
        val mention = com.example.data.repository.FinancialSemanticsCalculator.extractConversationalFinancialMention(text)
        assertEquals(100_000.0, mention.salaryAmount ?: 0.0, 0.01)
        assertEquals(50_000.0, mention.currentBalanceAmount ?: 0.0, 0.01)
        assertEquals(30_000.0, mention.weOweAmount ?: 0.0, 0.01)
        assertEquals(15_000.0, mention.owesUsAmount ?: 0.0, 0.01)

        // 2. Слияние с пустым снимком БД не складывает эти 4 числа в один доход или баланс
        val emptySnap = com.example.data.repository.FinancialSemanticsCalculator.calculateSnapshot(
            allTransactions = emptyList(),
            periodTransactions = emptyList(),
            accounts = emptyList()
        )
        val merged = com.example.data.repository.FinancialSemanticsCalculator.mergeSnapshotWithConversationalMentions(
            baseSnapshot = emptySnap,
            userMessages = listOf(text)
        )
        assertEquals(50_000.0, merged.availableBalanceRub, 0.01)
        assertEquals(100_000.0, merged.earnedIncomeRub, 0.01)
        assertEquals(100_000.0, merged.salaryIncomeRub, 0.01)
        assertEquals(30_000.0, merged.totalWeOweRub, 0.01)
        assertEquals(15_000.0, merged.totalOwesUsRub, 0.01)

        val formatted = com.example.data.repository.FinancialSemanticsCalculator.formatChatFinancialBreakdown(merged)
        assertTrue(formatted.contains("50000 ₽"))
        assertTrue(formatted.contains("100000 ₽"))
        assertTrue(formatted.contains("30000 ₽"))
        assertTrue(formatted.contains("15000 ₽"))
        assertFalse("Must never sum 100k+50k+30k+15k into 195000", formatted.contains("195000"))

        // 3. Эвристический парсер голоса/чата извлекает только реальную операцию зарплаты (100 000 ₽)
        // и НЕ превращает баланс (50к) или долги (30к, 15к) в доходы/расходы
        val parsedOps = com.example.utils.VoiceParserHelper.parseHeuristically(
            text = text,
            expenseCategories = listOf("Продукты", "Прочее"),
            incomeCategories = listOf("Зарплата", "Фриланс")
        )
        assertEquals(1, parsedOps.size)
        assertEquals("income", parsedOps.first().type)
        assertEquals("Зарплата", parsedOps.first().category)
        assertEquals(100_000.0, parsedOps.first().amount, 0.01)
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
        assertEquals(DavidLiveDisplayMode.HIDDEN, controller.displayMode.value)
    }

    @Test
    fun `minimizing and expanding David Live keeps single session alive and blocks parallel regular voice input`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)

        // До запуска сессии режим HIDDEN, обычный голосовой ввод разрешён
        assertEquals(DavidLiveDisplayMode.HIDDEN, controller.displayMode.value)
        assertFalse(controller.isSessionActive())
        assertTrue(controller.canStartRegularVoiceInput())

        // 1. Запуск сессии переводит displayMode в EXPANDED и блокирует параллельный обычный голосовой ввод
        controller.startSession("AIzaSyTestUserManualKey987654321")
        controller.processServerMessageForTest("""{"setupComplete": {}}""")
        assertEquals(1, controller.startSessionCount)
        assertEquals(DavidLiveDisplayMode.EXPANDED, controller.displayMode.value)
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertTrue(controller.isSessionActive())
        assertFalse("Regular VoiceInputManager must be blocked while David Live is EXPANDED", controller.canStartRegularVoiceInput())

        controller.recordTranscriptTurn(DavidLiveSpeaker.USER, "Привет, Давид! Проверь баланс.")

        // 2. Сворачивание (EXPANDED -> MINIMIZED) НЕ завершает сессию и НЕ очищает реплики
        controller.minimizeSession()
        assertEquals(DavidLiveDisplayMode.MINIMIZED, controller.displayMode.value)
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertTrue("Session must remain active when minimized", controller.isSessionActive())
        assertFalse("Regular VoiceInputManager must remain blocked while David Live is MINIMIZED", controller.canStartRegularVoiceInput())
        assertEquals(1, controller.transcriptTurns.size)

        // 3. Mute микрофона работает как в полноэкранном, так и в свёрнутом режиме
        assertFalse(controller.isMicMuted.value)
        controller.toggleMicMute()
        assertTrue(controller.isMicMuted.value)
        assertEquals("Микрофон выключен", resolveDavidLiveStatusLabel(controller.connectionState.value, DavidLiveSpeaker.NONE, controller.isMicMuted.value))

        // 4. Разворачивание (MINIMIZED -> EXPANDED) НЕ создаёт второе подключение
        controller.expandSession()
        assertEquals(DavidLiveDisplayMode.EXPANDED, controller.displayMode.value)
        assertEquals(1, controller.startSessionCount)
        assertTrue(controller.isMicMuted.value)

        // Повторный вызов startSession при активной свёрнутой сессии также просто разворачивает её без второго подключения
        controller.minimizeSession()
        assertEquals(DavidLiveDisplayMode.MINIMIZED, controller.displayMode.value)
        controller.startSession("AIzaSyTestUserManualKey987654321")
        assertEquals(DavidLiveDisplayMode.EXPANDED, controller.displayMode.value)
        assertEquals(1, controller.startSessionCount)

        // Включаем микрофон обратно в полноэкранном режиме
        controller.toggleMicMute()
        assertFalse(controller.isMicMuted.value)

        // 5. Завершение из мини-капсулы полностью останавливает звонок и возвращает обычный режим (HIDDEN)
        controller.minimizeSession()
        assertEquals(DavidLiveDisplayMode.MINIMIZED, controller.displayMode.value)
        val (turns, _) = controller.endSession()
        assertEquals(1, turns.size)
        assertEquals(DavidLiveDisplayMode.HIDDEN, controller.displayMode.value)
        assertEquals(DavidLiveConnectionState.ENDED, controller.connectionState.value)
        assertFalse(controller.isSessionActive())
        assertTrue("Regular voice input must be allowed again after David Live ends", controller.canStartRegularVoiceInput())
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
