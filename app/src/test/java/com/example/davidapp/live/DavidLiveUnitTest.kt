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
                getSavedApiKey = { "${"AI"}zaSyTestKey1234567890" }
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
        assertEquals(8, provider.buildFunctionDeclarationsJson().length())

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
    fun `issue 43 - david live session controller merges spoken user turns into financial tools snapshot`() = runBlocking {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b_live",
            transactionDao = createReadOnlyTransactionDao(emptyList()),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList())
        )
        val controller = DavidLiveSessionController(
            financialToolsProvider = provider,
            externalUserMessagesProvider = { listOf("Мой текущий баланс 45к") }
        )
        // Пользователь произнёс в ходе голосового звонка David Live:
        controller.recordTranscriptTurn(
            DavidLiveSpeaker.USER,
            "У меня зарплата 120к, я должен 25к, а мне должны 10к"
        )

        val overview = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        assertEquals(45_000L, overview.getLong("availableBalanceRub"))
        assertEquals(120_000L, overview.getLong("earnedIncome30dRub"))
        assertEquals(120_000L, overview.getLong("salaryIncome30dRub"))
        assertEquals(25_000L, overview.getLong("totalWeOweRub"))
        assertEquals(10_000L, overview.getLong("totalOwesUsRub"))
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
        assertTrue(controller.validateUserApiKey("${"AI"}zaSyTestUserManualKey987654321"))

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

    @Test
    fun `DavidViewModel migrates gemini_api_key from budget_prefs to secure_prefs and removes plaintext key`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val legacyPrefs = context.getSharedPreferences(
            com.example.davidapp.DavidViewModel.LEGACY_PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        val secureBacking = context.getSharedPreferences(
            "test_simulated_encrypted_prefs",
            android.content.Context.MODE_PRIVATE
        )
        val securePrefs = com.example.davidapp.DavidViewModel.createSecurePrefsOrNull(
            context = context,
            encryptedFactory = { secureBacking }
        )!!

        legacyPrefs.edit().clear().commit()
        securePrefs.edit().clear().commit()

        val expectedLegacyKey = "${"AI"}zaSyLegacyMigratedKey123456789"
        val expectedChatKey = "${"AI"}zaSyNewChatSecretKey987654321"

        // 1. Записываем несанитизированный ключ в старый незашифрованный budget_prefs
        legacyPrefs.edit()
            .putString("gemini_api_key", "  \"$expectedLegacyKey\" \n ")
            .commit()
        assertTrue(legacyPrefs.contains("gemini_api_key"))

        // 2. Вызываем миграцию — ключ должен быть очищен через sanitizeApiKey, перенесён в secure_prefs и удалён из budget_prefs
        val migrated = com.example.davidapp.DavidViewModel.resolveAndMigrateApiKey(
            legacyPrefs = legacyPrefs,
            securePrefs = securePrefs
        )
        assertEquals(expectedLegacyKey, migrated)
        assertFalse(
            "Plaintext budget_prefs must not retain gemini_api_key after migration",
            legacyPrefs.contains("gemini_api_key")
        )
        assertNull(legacyPrefs.getString("gemini_api_key", null))
        assertEquals(
            expectedLegacyKey,
            securePrefs.getString("gemini_api_key", null)
        )

        // 3. Сохранение нового ключа из чата пишет строго в secure_prefs и не оставляет следов в budget_prefs
        val saveResult = com.example.davidapp.DavidViewModel.saveSanitizedApiKey(
            legacyPrefs = legacyPrefs,
            securePrefs = securePrefs,
            newKey = " '$expectedChatKey' "
        )
        assertTrue(saveResult is com.example.davidapp.DavidViewModel.Companion.SaveApiKeyResult.Success)
        val savedFromChat = (saveResult as com.example.davidapp.DavidViewModel.Companion.SaveApiKeyResult.Success).sanitizedKey
        assertEquals(expectedChatKey, savedFromChat)
        assertFalse(
            "saveSanitizedApiKey must never store gemini_api_key in budget_prefs",
            legacyPrefs.contains("gemini_api_key")
        )
        assertTrue(legacyPrefs.getBoolean("gemini_consent_given", false))
        assertEquals(
            expectedChatKey,
            securePrefs.getString("gemini_api_key", null)
        )
    }

    @Test
    fun `encryption failure never falls back to plaintext SharedPreferences and failed migration preserves legacy key until recovery`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val legacyPrefs = context.getSharedPreferences(
            "test_legacy_atomic_prefs",
            android.content.Context.MODE_PRIVATE
        )
        val healthyEncryptedPrefs = context.getSharedPreferences(
            "test_healthy_encrypted_prefs",
            android.content.Context.MODE_PRIVATE
        )
        legacyPrefs.edit().clear().commit()
        healthyEncryptedPrefs.edit().clear().commit()

        // 1. Проверяем, что при отказе инициализации шифрования createSecurePrefsOrNull возвращает null
        // и НИКОГДА не вызывает context.getSharedPreferences("secure_prefs", MODE_PRIVATE)
        var plaintextFallbackCalled = false
        val spyContext = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences {
                if (name == com.example.davidapp.DavidViewModel.SECURE_PREFS_NAME) {
                    plaintextFallbackCalled = true
                }
                return super.getSharedPreferences(name, mode)
            }
        }
        val failedSecurePrefs = com.example.davidapp.DavidViewModel.createSecurePrefsOrNull(
            context = spyContext,
            encryptedFactory = { throw java.security.GeneralSecurityException("Keystore unavailable") }
        )
        assertNull("createSecurePrefsOrNull must return null when encryption fails", failedSecurePrefs)
        assertFalse(
            "createSecurePrefsOrNull must NEVER fall back to unencrypted getSharedPreferences(\"secure_prefs\")",
            plaintextFallbackCalled
        )

        // 2. Попытка сохранить новый ключ при недоступном шифровании возвращает EncryptionError и не пишет в открытый XML
        val newSecretKey = "${"AI"}zaSyNeverPlaintextSecret999888777"
        val errorOnNullSave = com.example.davidapp.DavidViewModel.saveSanitizedApiKey(
            legacyPrefs = legacyPrefs,
            securePrefs = null,
            newKey = newSecretKey
        )
        assertTrue(errorOnNullSave is com.example.davidapp.DavidViewModel.Companion.SaveApiKeyResult.EncryptionError)
        assertFalse("Legacy prefs must not contain secret after failed save", legacyPrefs.contains("gemini_api_key"))

        // 3. Неуспешная миграция: при securePrefs == null или сбое commit() старый ключ в budget_prefs НЕ удаляется
        val legacySecretKey = "${"AI"}zaSyPreservedOnFailure111222333"
        legacyPrefs.edit().putString("gemini_api_key", legacySecretKey).commit()

        val resolvedWhenNull = com.example.davidapp.DavidViewModel.resolveAndMigrateApiKey(
            legacyPrefs = legacyPrefs,
            securePrefs = null
        )
        assertEquals(legacySecretKey, resolvedWhenNull)
        assertTrue(
            "Legacy key must NOT be deleted from budget_prefs when securePrefs is null",
            legacyPrefs.contains("gemini_api_key")
        )
        assertEquals(legacySecretKey, legacyPrefs.getString("gemini_api_key", null))

        // Имитируем сбой записи (commit() == false) в зашифрованное хранилище
        val failingCommitPrefs = object : android.content.SharedPreferences by healthyEncryptedPrefs {
            override fun edit(): android.content.SharedPreferences.Editor {
                return object : android.content.SharedPreferences.Editor {
                    override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor = this
                    override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
                    override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor = this
                    override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor = this
                    override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor = this
                    override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor = this
                    override fun remove(key: String?): android.content.SharedPreferences.Editor = this
                    override fun clear(): android.content.SharedPreferences.Editor = this
                    override fun commit(): Boolean = false
                    override fun apply() {}
                }
            }
        }
        val resolvedWhenCommitFails = com.example.davidapp.DavidViewModel.resolveAndMigrateApiKey(
            legacyPrefs = legacyPrefs,
            securePrefs = failingCommitPrefs
        )
        assertEquals(legacySecretKey, resolvedWhenCommitFails)
        assertTrue(
            "Legacy key must NOT be deleted from budget_prefs when encrypted commit() returns false",
            legacyPrefs.contains("gemini_api_key")
        )
        assertNull(healthyEncryptedPrefs.getString("gemini_api_key", null))

        // Попытка сохранить новый ключ при сбое commit() также возвращает EncryptionError и не удаляет старый ключ
        val errorOnFailingCommit = com.example.davidapp.DavidViewModel.saveSanitizedApiKey(
            legacyPrefs = legacyPrefs,
            securePrefs = failingCommitPrefs,
            newKey = newSecretKey
        )
        assertTrue(errorOnFailingCommit is com.example.davidapp.DavidViewModel.Companion.SaveApiKeyResult.EncryptionError)
        assertEquals(legacySecretKey, legacyPrefs.getString("gemini_api_key", null))

        // 4. Восстановление: когда зашифрованное хранилище снова исправно, миграция атомарно переносит ключ и очищает budget_prefs
        val recovered = com.example.davidapp.DavidViewModel.resolveAndMigrateApiKey(
            legacyPrefs = legacyPrefs,
            securePrefs = healthyEncryptedPrefs
        )
        assertEquals(legacySecretKey, recovered)
        assertEquals(legacySecretKey, healthyEncryptedPrefs.getString("gemini_api_key", null))
        assertFalse(
            "Legacy key must be removed from budget_prefs after verified migration recovery",
            legacyPrefs.contains("gemini_api_key")
        )
    }

    @Test
    fun `DavidLiveFinancialToolsProvider parses transaction ISO dates accurately without SimpleDateFormat allocations`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )

        // 1. Если задан createdAt > 0, возвращается точный timestamp создания
        val txWithCreatedAt = TransactionEntity(
            id = "tx_created",
            budgetId = "default",
            type = "expense",
            date = "2026-10-29",
            category = "Кафе",
            subcategory = "Кофе",
            amount = 350.0,
            createdAt = 1761740000123L
        )
        assertEquals(1761740000123L, provider.parseTransactionMillis(txWithCreatedAt))

        // 2. Если createdAt == 0, парсится ISO дата YYYY-MM-DD в полночь системной таймзоны
        val txIsoOnly = TransactionEntity(
            id = "tx_iso",
            budgetId = "default",
            type = "expense",
            date = "2026-10-29",
            category = "Продукты",
            subcategory = "Супермаркет",
            amount = 1500.0,
            createdAt = 0L
        )
        val expectedMillis = java.time.LocalDate.of(2026, 10, 29)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedMillis, provider.parseTransactionMillis(txIsoOnly))

        // 3. Проверка хронологического порядка и некорректных дат
        val earlierMillis = DavidLiveFinancialToolsProvider.parseIsoDateToMillis("2026-10-01")
        val laterMillis = DavidLiveFinancialToolsProvider.parseIsoDateToMillis("2026-10-29")
        assertTrue(laterMillis > earlierMillis)
        assertEquals(0L, DavidLiveFinancialToolsProvider.parseIsoDateToMillis(""))
        assertEquals(0L, DavidLiveFinancialToolsProvider.parseIsoDateToMillis("invalid-date"))
        assertEquals(0L, DavidLiveFinancialToolsProvider.parseIsoDateToMillis("2026-02-30"))
    }

    @Test
    fun `audio engine clearModelPlaybackQueue increments generation and suppresses tail echo while AvatarCropDialog scales non-square bitmaps`() {
        val engine = DavidLiveAudioEngine()
        val initialGen = engine.getPlaybackGenerationForTest()
        val beforeMs = System.currentTimeMillis()
        engine.clearModelPlaybackQueue()
        assertEquals(initialGen + 1, engine.getPlaybackGenerationForTest())
        assertTrue(engine.getBargeInEchoSuppressUntilMsForTest() >= beforeMs + DavidLiveAudioEngine.BARGE_IN_TAIL_ECHO_SUPPRESS_MS)
        // Тихий остаточный хвост из динамика подавляется, а громкая речь пользователя — пропускается
        assertTrue(engine.shouldSuppressTailEchoFrame(rms = 0.02f, nowMs = beforeMs + 50L))
        assertFalse(engine.shouldSuppressTailEchoFrame(rms = 0.12f, nowMs = beforeMs + 50L))

        // Проверка minScale для квадратных, узких вертикальных и панорамных изображений в AvatarCropDialog
        val squareMinScale = com.example.ui.components.calculateAvatarMinScale(
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            containerSizePx = 300f,
            viewportSizePx = 240f
        )
        assertEquals(1f, squareMinScale, 0.001f)

        // Панорамное изображение 2000x500 (соотношение 4:1): при вписывании в 300px высота равна 75px,
        // поэтому для покрытия круга 240px требуется minScale = 240 / 75 = 3.2f
        val panoramaMinScale = com.example.ui.components.calculateAvatarMinScale(
            bitmapWidth = 2000,
            bitmapHeight = 500,
            containerSizePx = 300f,
            viewportSizePx = 240f
        )
        assertEquals(3.2f, panoramaMinScale, 0.001f)

        // Узкое вертикальное изображение 500x1500 (соотношение 1:3): ширина при вписывании 100px -> minScale = 240 / 100 = 2.4f
        val narrowMinScale = com.example.ui.components.calculateAvatarMinScale(
            bitmapWidth = 500,
            bitmapHeight = 1500,
            containerSizePx = 300f,
            viewportSizePx = 240f
        )
        assertEquals(2.4f, narrowMinScale, 0.001f)
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

    @Test
    fun `ApiKeyStorage migrates legacy key atomically and refuses plaintext fallback when securePrefs is null`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val legacyPrefs = context.getSharedPreferences("test_legacy_budget_prefs", android.content.Context.MODE_PRIVATE)
        val securePrefs = context.getSharedPreferences("test_secure_prefs_mock", android.content.Context.MODE_PRIVATE)
        legacyPrefs.edit().clear().commit()
        securePrefs.edit().clear().commit()

        val validKey = "AIzaSyD-TestKeyForCentralizedStorage12345"
        legacyPrefs.edit().putString(com.example.data.preferences.ApiKeyStorage.KEY_GEMINI_API_KEY, "  $validKey  ").commit()

        // 1. При недоступности защищённого хранилища (securePrefs == null) ключ в legacy не теряется, а запись нового отклоняется
        val resolvedWhenNull = com.example.data.preferences.ApiKeyStorage.resolveAndMigrateApiKey(
            securePrefs = null,
            legacyPrefs = legacyPrefs
        )
        assertEquals(validKey, resolvedWhenNull)
        assertTrue(legacyPrefs.contains(com.example.data.preferences.ApiKeyStorage.KEY_GEMINI_API_KEY))
        assertFalse(
            com.example.data.preferences.ApiKeyStorage.saveApiKeyWithPrefs(
                rawKey = "AIzaSyD-NewSecretKey999999999999",
                securePrefs = null,
                legacyPrefs = legacyPrefs
            )
        )

        // 2. Когда защищённое хранилище доступно, миграция переносит ключ и удаляет его из legacyPrefs
        val migrated = com.example.data.preferences.ApiKeyStorage.resolveAndMigrateApiKey(
            securePrefs = securePrefs,
            legacyPrefs = legacyPrefs
        )
        assertEquals(validKey, migrated)
        assertEquals(validKey, securePrefs.getString(com.example.data.preferences.ApiKeyStorage.KEY_GEMINI_API_KEY, ""))
        assertFalse(legacyPrefs.contains(com.example.data.preferences.ApiKeyStorage.KEY_GEMINI_API_KEY))
    }

    @Test
    fun `BudgetPreferencesRepository centralizes consent, budget selection, chat flags and sound settings`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.data.preferences.BudgetPreferencesRepository.budgetPrefs(context).edit().clear().commit()
        com.example.data.preferences.BudgetPreferencesRepository.appPrefs(context).edit().clear().commit()

        assertFalse(com.example.data.preferences.BudgetPreferencesRepository.isGeminiConsentGiven(context))
        com.example.data.preferences.BudgetPreferencesRepository.setGeminiConsentGiven(context, true)
        assertTrue(com.example.data.preferences.BudgetPreferencesRepository.isGeminiConsentGiven(context))

        assertTrue(com.example.data.preferences.BudgetPreferencesRepository.isFirstLaunchSelection(context))
        assertNull(com.example.data.preferences.BudgetPreferencesRepository.getLastSelectedBudgetId(context))

        com.example.data.preferences.BudgetPreferencesRepository.setSelectedBudget(context, "budget_main")
        assertFalse(com.example.data.preferences.BudgetPreferencesRepository.isFirstLaunchSelection(context))
        assertEquals("budget_main", com.example.data.preferences.BudgetPreferencesRepository.getLastSelectedBudgetId(context))

        com.example.data.preferences.BudgetPreferencesRepository.resetBudgetSelectionOnClear(context)
        assertTrue(com.example.data.preferences.BudgetPreferencesRepository.isFirstLaunchSelection(context))
        assertNull(com.example.data.preferences.BudgetPreferencesRepository.getLastSelectedBudgetId(context))

        assertFalse(com.example.data.preferences.BudgetPreferencesRepository.hasOpenedDavidChatBefore(context, "Максим"))
        com.example.data.preferences.BudgetPreferencesRepository.markDavidChatOpened(context, "Максим")
        assertTrue(com.example.data.preferences.BudgetPreferencesRepository.hasOpenedDavidChatBefore(context, "Максим"))

        assertTrue(com.example.data.preferences.BudgetPreferencesRepository.isSoundEnabled(context))
        com.example.data.preferences.BudgetPreferencesRepository.appPrefs(context).edit().putBoolean("sound_enabled", false).commit()
        assertFalse(com.example.data.preferences.BudgetPreferencesRepository.isSoundEnabled(context))
    }

    @Test
    fun `VoiceOperationParserService and BudgetRepository companion parse and sanitize voice JSON identically`() {
        val rawMarkdownJson = """
            ```json
            [
              {"title": "Пятёрочка", "type": "expense", "amount": 1250.5, "category": "Продукты", "subcategory": "Супермаркет", "date": "2026-10-31"},
              {"title": "null", "type": "income", "amount": 50000, "category": "Зарплата", "subcategory": "undefined", "date": "invalid-date"}
            ]
            ```
        """.trimIndent()

        val extracted = com.example.data.repository.VoiceOperationParserService.extractJsonArray(rawMarkdownJson)
        assertEquals(com.example.data.repository.BudgetRepository.extractJsonArray(rawMarkdownJson), extracted)

        val parsed = com.example.data.repository.VoiceOperationParserService.parseJsonOperations(extracted, "2026-10-30")
        assertEquals(2, parsed.size)
        assertEquals("Пятёрочка", parsed[0].title)
        assertEquals("expense", parsed[0].type)
        assertEquals(1250.5, parsed[0].amount, 0.001)
        assertEquals("2026-10-31", parsed[0].date)

        assertEquals("Зарплата", parsed[1].title)
        assertEquals("income", parsed[1].type)
        assertEquals("", parsed[1].subcategory)
        assertEquals("2026-10-30", parsed[1].date)
    }

    @Test
    fun `DavidAiAuditService provides deterministic offline comments and user phrases when API key is blank`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val service = com.example.data.repository.DavidAiAuditService(context)
        val comment = service.generateDavidComment(
            apiKey = "",
            type = "expense",
            category = "Фастфуд",
            subcategory = "Бургеры",
            amount = 850.0
        )
        assertTrue(comment.isNotBlank())

        val userPhrase = service.generateUserPhrase(
            apiKey = "",
            type = "expense",
            category = "Продукты",
            subcategory = "Молоко",
            amount = 120.0,
            isFirstToday = true
        )
        assertTrue(userPhrase.isNotBlank())
    }

    @Test
    fun `core database module provides Room DAOs migrations and atomic CRUD operations`() = runBlocking {
        assertEquals(3, com.example.data.db.MIGRATION_3_4.startVersion)
        assertEquals(4, com.example.data.db.MIGRATION_3_4.endVersion)
        assertEquals(4, com.example.data.db.MIGRATION_4_5.startVersion)
        assertEquals(5, com.example.data.db.MIGRATION_4_5.endVersion)
        assertEquals(5, com.example.data.db.MIGRATION_5_6.startVersion)
        assertEquals(6, com.example.data.db.MIGRATION_5_6.endVersion)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.db.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val profile = com.example.data.db.BudgetProfileEntity(id = "test_b1", name = "Основной")
            db.budgetProfileDao().insertProfile(profile)
            assertEquals("Основной", db.budgetProfileDao().getProfileById("test_b1")?.name)

            val acc = com.example.data.db.AccountEntity(
                id = "acc_1",
                budgetId = "test_b1",
                name = "Карта",
                balance = 10000.0,
                type = "card",
                accountNumber = "**** 1234"
            )
            db.accountDao().insertAccount(acc)
            db.accountDao().updateBalance("acc_1", -1500.0)
            assertEquals(8500.0, db.accountDao().getAccountById("acc_1")?.balance ?: 0.0, 0.001)
        } finally {
            db.close()
        }
    }

    private fun createReadOnlyRecurringPaymentDao(
        payments: List<com.example.data.db.RecurringPaymentEntity>,
        logs: List<com.example.data.db.RecurringExecutionLogEntity> = emptyList()
    ) = object : com.example.data.db.RecurringPaymentDao {
        override fun getRecurringPaymentsByBudgetId(budgetId: String): Flow<List<com.example.data.db.RecurringPaymentEntity>> =
            flowOf(payments.filter { it.budgetId == budgetId })
        override fun getAllRecurringPayments(): Flow<List<com.example.data.db.RecurringPaymentEntity>> =
            flowOf(payments)
        override suspend fun getRecurringPaymentById(id: Long): com.example.data.db.RecurringPaymentEntity? =
            payments.firstOrNull { it.id == id }
        override suspend fun insertRecurringPayment(payment: com.example.data.db.RecurringPaymentEntity): Long =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun insertRecurringPayments(payments: List<com.example.data.db.RecurringPaymentEntity>) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun updateNextDueDate(id: Long, nextDueDateEpochDay: Long) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun updateStatus(id: Long, status: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteRecurringPaymentById(id: Long) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteRecurringPaymentsByBudgetId(budgetId: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteAllRecurringPayments() =
            throw UnsupportedOperationException("Read-only in David Live")
        override fun getExecutionLogsByBudgetId(budgetId: String): Flow<List<com.example.data.db.RecurringExecutionLogEntity>> =
            flowOf(logs.filter { it.budgetId == budgetId })
        override fun getAllExecutionLogs(): Flow<List<com.example.data.db.RecurringExecutionLogEntity>> =
            flowOf(logs)
        override suspend fun getExecutionLogByTransactionId(transactionId: String): com.example.data.db.RecurringExecutionLogEntity? =
            logs.firstOrNull { it.linkedTransactionId == transactionId }
        override suspend fun getExecutionLogBySchedule(recurringId: Long, scheduledEpochDay: Long): com.example.data.db.RecurringExecutionLogEntity? =
            logs.firstOrNull { it.recurringId == recurringId && it.scheduledEpochDay == scheduledEpochDay }
        override suspend fun insertExecutionLog(log: com.example.data.db.RecurringExecutionLogEntity): Long =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteExecutionLogsByBudgetId(budgetId: String) =
            throw UnsupportedOperationException("Read-only in David Live")
        override suspend fun deleteAllExecutionLogs() =
            throw UnsupportedOperationException("Read-only in David Live")
    }

    @Test
    fun `issue 60 - all 8 tool declarations match executeToolCall dispatch and return valid structured JSON`() = runBlocking {
        val d0 = java.time.LocalDate.of(2026, 11, 1).toEpochDay()
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionEntity(id = "t1", budgetId = "b1", type = "income", date = "2026-11-01", category = "Начальный баланс", subcategory = "Карта", amount = 80_000.0, createdAt = now - 50_000L),
            TransactionEntity(id = "t2", budgetId = "b1", type = "expense", date = "2026-11-01", category = "Продукты", subcategory = "Супермаркет", amount = 2_000.0, createdAt = now - 10_000L)
        )
        val cats = listOf(CategoryEntity(id = "c1", budgetId = "b1", type = "expense", name = "Продукты", monthlyLimit = 20_000.0))
        val goals = listOf(GoalEntity(id = "g1", budgetId = "b1", name = "Отпуск", targetAmount = 100_000.0, currentAmount = 25_000.0))
        val accs = listOf(AccountEntity(id = "a1", budgetId = "b1", name = "Основная", balance = 78_000.0, type = "card"))
        val recs = listOf(
            com.example.data.db.RecurringPaymentEntity(
                id = 1L,
                budgetId = "b1",
                title = "Зарплата",
                amount = 120_000.0,
                isIncome = true,
                incomeCertainty = "GUARANTEED",
                categoryName = "Зарплата",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 10L,
                status = "ACTIVE"
            )
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(txs),
            categoryDao = createReadOnlyCategoryDao(cats),
            goalDao = createReadOnlyGoalDao(goals),
            accountDao = createReadOnlyAccountDao(accs),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(recs),
            todayEpochDayProvider = { d0 }
        )

        val declarations = provider.buildFunctionDeclarationsJson()
        assertEquals(8, declarations.length())

        val declaredNames = (0 until declarations.length()).map { idx ->
            val decl = declarations.getJSONObject(idx)
            assertTrue(decl.getString("description").isNotBlank())
            assertEquals("OBJECT", decl.getJSONObject("parameters").getString("type"))
            decl.getString("name")
        }
        assertEquals(DavidLiveFinancialToolsProvider.ALL_REGISTERED_TOOLS, declaredNames)

        for (toolName in declaredNames) {
            val res = provider.executeToolCall(toolName, JSONObject())
            assertEquals("Tool $toolName must return status=ok", "ok", res.getString("status"))
            assertTrue("Tool $toolName must return dataAvailable=true", res.getBoolean("dataAvailable"))
            assertEquals("b1", res.getString("budgetId"))
            assertTrue("Tool $toolName must provide non-blank humanSummary", res.getString("humanSummary").isNotBlank())
        }

        val unsupported = provider.executeToolCall("mutate_database_secretly", JSONObject())
        assertEquals("unsupported_tool", unsupported.getString("status"))
        assertFalse(unsupported.getBoolean("dataAvailable"))
    }

    @Test
    fun `issue 60 - active recurring salary and rent schedule and safe to spend after rent before salary match SafeToSpendCalculator`() = runBlocking {
        // Сегодня: 2026-11-01 (d0). Доступный баланс B_avail = 70 000 ₽.
        // Аренда квартиры: 30 000 ₽ через 4 дня (d0 + 4, MANDATORY).
        // Гарантированная зарплата: 100 000 ₽ через 10 дней (d0 + 10, GUARANTEED).
        // Пользовательский норматив базовых расходов: 1 000 ₽/день (10 дней * 1 000 = 10 000 ₽).
        // Защитный резерв R_floor = 10 000 ₽.
        // Ожидаемый расчёт:
        // - Остаток после аренды до зарплаты (netBalanceAfterObligationsBeforeSalaryRub) = 70 000 - 30 000 = 40 000 ₽.
        // - Свободная ликвидность до зарплаты F_guar(9) = 70 000 - 10 000 (R_floor) - 30 000 (Аренда) - 10 000 (Базовая жизнь 10 дн.) = 20 000 ₽.
        // - Дневная скорость необязательных трат на 10 дней = 20 000 / 10 = 2 000 ₽/день.
        // - Суммарно можно потратить сегодня = 2 000 (свободные) + 1 000 (защищённые базовые) = 3 000 ₽.
        val d0 = java.time.LocalDate.of(2026, 11, 1).toEpochDay()
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionEntity(
                id = "tx_init",
                budgetId = "b1",
                type = "income",
                date = "2026-10-20",
                category = "Начальный баланс",
                subcategory = "Карта",
                amount = 70_000.0,
                createdAt = now - 86_400_000L * 5
            )
        )
        val recurringRules = listOf(
            com.example.data.db.RecurringPaymentEntity(
                id = 10L,
                budgetId = "b1",
                title = "Аренда квартиры",
                amount = 30_000.0,
                amountType = "FIXED",
                isIncome = false,
                incomeCertainty = "GUARANTEED",
                categoryName = "Аренда",
                frequency = "MONTHLY",
                anchorDayOfMonth = 5,
                nextDueDateEpochDay = d0 + 4L,
                priority = "MANDATORY",
                status = "ACTIVE"
            ),
            com.example.data.db.RecurringPaymentEntity(
                id = 20L,
                budgetId = "b1",
                title = "Зарплата",
                amount = 100_000.0,
                amountType = "FIXED",
                isIncome = true,
                incomeCertainty = "GUARANTEED",
                categoryName = "Зарплата",
                frequency = "MONTHLY",
                anchorDayOfMonth = 11,
                nextDueDateEpochDay = d0 + 10L,
                priority = "MANDATORY",
                status = "ACTIVE"
            )
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(txs),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList()),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(recurringRules),
            userSafetyFloorProvider = { 10_000.0 },
            userLockedReserveProvider = { 0.0 },
            userEssentialDailyNormProvider = { 1_000.0 },
            todayEpochDayProvider = { d0 }
        )

        // 1. Проверка расписания регулярных доходов и расходов (get_recurring_schedule)
        val scheduleJson = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE, JSONObject())
        assertEquals("ok", scheduleJson.getString("status"))
        assertEquals(2, scheduleJson.getInt("activeRulesCount"))
        assertEquals(1, scheduleJson.getInt("upcomingIncomesCount"))
        assertEquals(1, scheduleJson.getInt("upcomingExpensesCount"))
        assertEquals("2026-11-11", scheduleJson.getString("nextGuaranteedIncomeDateIso"))
        assertEquals("Зарплата", scheduleJson.getString("nextGuaranteedIncomeTitle"))
        assertEquals(100_000.0, scheduleJson.getDouble("nextGuaranteedIncomeAmountRub"), 0.01)
        assertEquals(10, scheduleJson.getInt("daysUntilNextGuaranteedIncome"))

        val incomeItem = scheduleJson.getJSONArray("upcomingIncomes").getJSONObject(0)
        assertEquals("Зарплата", incomeItem.getString("title"))
        assertEquals(100_000.0, incomeItem.getDouble("amountRub"), 0.01)
        assertEquals("2026-11-11", incomeItem.getString("nextDueDateIso"))
        assertEquals(10, incomeItem.getInt("daysUntilNextDue"))
        assertEquals("GUARANTEED", incomeItem.getString("incomeCertainty"))
        assertFalse("Future salary must NEVER be marked as already included in current balance", incomeItem.getBoolean("isAlreadyIncludedInCurrentBalance"))

        val expenseItem = scheduleJson.getJSONArray("upcomingExpenses").getJSONObject(0)
        assertEquals("Аренда квартиры", expenseItem.getString("title"))
        assertEquals(30_000.0, expenseItem.getDouble("effectiveReservedAmountRub"), 0.01)
        assertEquals("2026-11-05", expenseItem.getString("nextDueDateIso"))
        assertEquals(4, expenseItem.getInt("daysUntilNextDue"))
        assertEquals("MANDATORY", expenseItem.getString("priority"))

        // 2. Проверка «Сколько останется после аренды до зарплаты?» и «Сколько безопасно потратить сейчас?»
        val forecastJson = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST, JSONObject())
        assertEquals(70_000.0, forecastJson.getDouble("availableBalanceRub"), 0.01)
        assertEquals(30_000.0, forecastJson.getDouble("reservedFutureObligationsRub"), 0.01)
        assertEquals(40_000.0, forecastJson.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)
        assertEquals(10_000.0, forecastJson.getDouble("mandatorySafetyFloorRub"), 0.01)
        assertEquals(10_000.0, forecastJson.getDouble("reservedEssentialLivingHorizonRub"), 0.01)
        assertEquals(10, forecastJson.getInt("horizonDays"))
        assertEquals(2_000.0, forecastJson.getDouble("canSpendTodayDiscRub"), 0.01)
        assertEquals(1_000.0, forecastJson.getDouble("canSpendTodayEssRub"), 0.01)
        assertEquals(3_000.0, forecastJson.getDouble("canSpendTodayTotalRub"), 0.01)
        assertEquals(1, forecastJson.getJSONArray("upcomingObligations").length())
        assertEquals(1, forecastJson.getJSONArray("upcomingIncomes").length())

        // Сравнение с прямым вызовом эталонного SafeToSpendCalculator
        val directSnapshot = com.example.data.safetospend.DavidFinancialIntelligenceProvider.computeSnapshot(
            transactions = txs,
            accounts = emptyList(),
            goals = emptyList(),
            categories = emptyList(),
            recurringPayments = recurringRules,
            executionLogs = emptyList(),
            todayEpochDay = d0,
            userSafetyFloorRub = 10_000.0,
            userLockedReserveRub = 0.0,
            userEssentialDailyNormRub = 1_000.0
        )
        assertEquals(directSnapshot.safeToSpendResult.canSpendTodayDiscRub, forecastJson.getDouble("canSpendTodayDiscRub"), 0.001)
        assertEquals(directSnapshot.safeToSpendResult.netBalanceAfterObligationsBeforeSalaryRub, forecastJson.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.001)

        // 3. Проверка What-If: «Что будет, если зарплата задержится на 5 дней?»
        // Горизонт увеличивается с 10 до 15 дней. Базовые расходы на 15 дней = 15 000 ₽.
        // Свободная ликвидность на 15 дней = 70 000 - 10 000 (резерв) - 30 000 (аренда) - 15 000 (база 15 дн.) = 15 000 ₽.
        // Новый свободный дневной лимит = 15 000 / 15 = 1 000 ₽/день (снижение на -1 000 ₽/день).
        val whatIfDelayJson = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_SIMULATE_WHAT_IF,
            JSONObject().apply {
                put("scenarioType", "INCOME_DELAY")
                put("delayDays", 5)
            }
        )
        assertEquals("ok", whatIfDelayJson.getString("status"))
        assertEquals(10, whatIfDelayJson.getInt("baselineHorizonDays"))
        assertEquals(15, whatIfDelayJson.getInt("simulatedHorizonDays"))
        assertEquals("2026-11-11", whatIfDelayJson.getString("baselineNextIncomeDateIso"))
        assertEquals("2026-11-16", whatIfDelayJson.getString("simulatedNextIncomeDateIso"))
        assertEquals(2_000.0, whatIfDelayJson.getDouble("baselineCanSpendTodayRub"), 0.01)
        assertEquals(1_000.0, whatIfDelayJson.getDouble("simulatedCanSpendTodayRub"), 0.01)
        assertEquals(-1_000.0, whatIfDelayJson.getDouble("deltaDailyBudgetRub"), 0.01)
    }

    @Test
    fun `issue 60 - EXPECTED and HYPOTHETICAL recurring incomes are never mixed into guaranteed baseline or availableBalanceRub`() = runBlocking {
        val d0 = java.time.LocalDate.of(2026, 11, 1).toEpochDay()
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionEntity(
                id = "tx_init",
                budgetId = "b1",
                type = "income",
                date = "2026-10-25",
                category = "Начальный баланс",
                subcategory = "Карта",
                amount = 30_000.0,
                createdAt = now - 86_400_000L
            )
        )
        val recurringRules = listOf(
            com.example.data.db.RecurringPaymentEntity(
                id = 101L,
                budgetId = "b1",
                title = "Фриланс-проект",
                amount = 60_000.0,
                isIncome = true,
                incomeCertainty = "EXPECTED",
                categoryName = "Фриланс",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 5L,
                status = "ACTIVE"
            ),
            com.example.data.db.RecurringPaymentEntity(
                id = 102L,
                budgetId = "b1",
                title = "Возможная премия",
                amount = 40_000.0,
                isIncome = true,
                incomeCertainty = "HYPOTHETICAL",
                categoryName = "Бонус",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 7L,
                status = "ACTIVE"
            )
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(txs),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList()),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(recurringRules),
            userEssentialDailyNormProvider = { 500.0 },
            todayEpochDayProvider = { d0 }
        )

        val overview = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        // Текущий баланс остаётся 30 000 ₽, заработанный доход за 30 дней = 0 ₽ (будущие регулярные доходы не зачислены!)
        assertEquals(30_000L, overview.getLong("availableBalanceRub"))
        assertEquals(0L, overview.getLong("earnedIncome30dRub"))

        val schedule = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE,
            JSONObject().put("horizonDays", 30)
        )
        assertEquals(0.0, schedule.getDouble("totalGuaranteedFutureIncomeHorizonRub"), 0.01)
        assertEquals(60_000.0, schedule.getDouble("totalExpectedFutureIncomeHorizonRub"), 0.01)
        assertEquals(40_000.0, schedule.getDouble("totalHypotheticalFutureIncomeHorizonRub"), 0.01)
        assertFalse("No guaranteed income exists", schedule.has("nextGuaranteedIncomeDateIso"))

        val forecast = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST, null)
        // Так как GUARANTEED дохода нет, горизонт равен числу дней до конца ноября (30 дней: с 1 по 30 ноября)
        assertEquals(30, forecast.getInt("horizonDays"))
        assertFalse(forecast.has("nextIncomeDateIso"))
        // В гарантированном сценарии: 30 000 - 30 * 500 (база 15 000) = 15 000 ₽ -> 15 000 / 30 = 500 ₽/день
        assertEquals(500.0, forecast.getDouble("canSpendTodayDiscRub"), 0.01)
        // В справочном ожидаемом сценарии (с учётом EXPECTED 60 000 ₽ на 5-й день) бюджет выше, но показывается отдельно
        assertEquals(60_000.0, forecast.getDouble("expectedUnconfirmedIncomeHorizonRub"), 0.01)
        assertEquals(40_000.0, forecast.getDouble("hypotheticalDebtReceivablesRub"), 0.01)
    }

    @Test
    fun `issue 60 - PAID RESCHEDULED SKIPPED OVERDUE PAUSED ARCHIVED recurring items and linked transactions avoid double counting`() = runBlocking {
        val d0 = java.time.LocalDate.of(2026, 11, 10).toEpochDay()
        val now = System.currentTimeMillis()

        // Доступный баланс: начальный 100 000 ₽ минус уже оплаченная аренда сегодня 25 000 ₽ = 75 000 ₽
        val txs = listOf(
            TransactionEntity(id = "tx_init", budgetId = "b1", type = "income", date = "2026-11-01", category = "Начальный баланс", subcategory = "Карта", amount = 100_000.0, createdAt = now - 86_400_000L * 5),
            TransactionEntity(id = "tx_rent_paid", budgetId = "b1", type = "expense", date = "2026-11-10", category = "Аренда", subcategory = "Оплата аренды", amount = 25_000.0, createdAt = now - 1000L)
        )

        val recurringRules = listOf(
            // 1. Уже оплаченная сегодня аренда (связана через linkedTransactionId = tx_rent_paid) -> НЕ должна резервироваться повторно!
            com.example.data.db.RecurringPaymentEntity(
                id = 1L,
                budgetId = "b1",
                title = "Аренда",
                amount = 25_000.0,
                isIncome = false,
                categoryName = "Аренда",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0,
                priority = "MANDATORY",
                status = "ACTIVE"
            ),
            // 2. Пропущенная подписка (SKIPPED) -> НЕ должна резервироваться
            com.example.data.db.RecurringPaymentEntity(
                id = 2L,
                budgetId = "b1",
                title = "Подписка Кино",
                amount = 1_000.0,
                isIncome = false,
                categoryName = "Подписки",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 2L,
                priority = "DISCRETIONARY_SUBSCRIPTION",
                status = "ACTIVE"
            ),
            // 3. Перенесённый платёж (RESCHEDULED на d0 + 3) -> старая дата d0 + 3 исключена
            com.example.data.db.RecurringPaymentEntity(
                id = 3L,
                budgetId = "b1",
                title = "Страховка",
                amount = 4_000.0,
                isIncome = false,
                categoryName = "Страховка",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 3L,
                priority = "MANDATORY",
                status = "ACTIVE"
            ),
            // 4. Просроченный платёж ЖКХ (OVERDUE с d0 - 2) -> резервируется ровно 1 раз в overdueObligationsRub (5 000 ₽)
            com.example.data.db.RecurringPaymentEntity(
                id = 4L,
                budgetId = "b1",
                title = "ЖКХ",
                amount = 5_000.0,
                isIncome = false,
                categoryName = "ЖКХ",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 28L,
                priority = "MANDATORY",
                status = "ACTIVE"
            ),
            // 5. Приостановленное правило (PAUSED) -> полностью исключено
            com.example.data.db.RecurringPaymentEntity(
                id = 5L,
                budgetId = "b1",
                title = "Спортзал на паузе",
                amount = 8_000.0,
                isIncome = false,
                categoryName = "Спорт",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 4L,
                priority = "DISCRETIONARY_SUBSCRIPTION",
                status = "PAUSED"
            ),
            // 6. Архивное правило (ARCHIVED) -> полностью исключено
            com.example.data.db.RecurringPaymentEntity(
                id = 6L,
                budgetId = "b1",
                title = "Старый кредит",
                amount = 15_000.0,
                isIncome = false,
                categoryName = "Кредиты",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 5L,
                priority = "MANDATORY",
                status = "ARCHIVED"
            ),
            // 7. Гарантированная зарплата через 10 дней (d0 + 10)
            com.example.data.db.RecurringPaymentEntity(
                id = 7L,
                budgetId = "b1",
                title = "Зарплата",
                amount = 90_000.0,
                isIncome = true,
                incomeCertainty = "GUARANTEED",
                categoryName = "Зарплата",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 10L,
                status = "ACTIVE"
            )
        )

        val logs = listOf(
            // Аренда за d0 связана с реальной транзакцией tx_rent_paid
            com.example.data.db.RecurringExecutionLogEntity(
                id = 100L,
                budgetId = "b1",
                recurringId = 1L,
                scheduledEpochDay = d0,
                executedEpochDay = d0,
                actualAmount = 25_000.0,
                status = "PENDING", // Даже если статус остался PENDING, связь с существующей транзакцией делает его исполненным
                linkedTransactionId = "tx_rent_paid"
            ),
            com.example.data.db.RecurringExecutionLogEntity(
                id = 101L,
                budgetId = "b1",
                recurringId = 2L,
                scheduledEpochDay = d0 + 2L,
                status = "SKIPPED"
            ),
            com.example.data.db.RecurringExecutionLogEntity(
                id = 102L,
                budgetId = "b1",
                recurringId = 3L,
                scheduledEpochDay = d0 + 3L,
                status = "RESCHEDULED"
            ),
            com.example.data.db.RecurringExecutionLogEntity(
                id = 103L,
                budgetId = "b1",
                recurringId = 4L,
                scheduledEpochDay = d0 - 2L,
                actualAmount = 5_000.0,
                status = "OVERDUE"
            )
        )

        // Также добавим долговой счёт с таким же именем «ЖКХ», чтобы проверить дедупликацию между recurring и we_owe
        val duplicateDebtAcc = AccountEntity(
            id = "debt_jkh",
            budgetId = "b1",
            name = "Долг: ЖКХ",
            balance = 5_000.0,
            type = "we_owe",
            accountNumber = "2026-11-08"
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(txs),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(listOf(duplicateDebtAcc)),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(recurringRules, logs),
            userEssentialDailyNormProvider = { 1_000.0 },
            todayEpochDayProvider = { d0 }
        )

        val schedule = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE, JSONObject().put("horizonDays", 10))
        assertEquals(5, schedule.getInt("activeRulesCount"))
        assertEquals(2, schedule.getInt("pausedOrArchivedRulesCount"))
        assertEquals(3, schedule.getInt("excludedExecutedOrSkippedOccurrencesCount"))
        assertEquals(1, schedule.getInt("overdueExpensesCount"))
        assertEquals(5_000.0, schedule.getDouble("totalOverdueObligationsRub"), 0.01)

        val forecast = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST, null)
        // Доступный баланс = 75 000 ₽ (25 000 ₽ аренды уже списаны в tx_rent_paid)
        assertEquals(75_000.0, forecast.getDouble("availableBalanceRub"), 0.01)
        // Сегодняшние неоплаченные обязательства = 0 ₽ (аренда оплачена)
        assertEquals(0.0, forecast.getDouble("todayPendingObligationsRub"), 0.01)
        // Будущие обязательства в горизонте 10 дн. = 0 ₽ (Подписка SKIPPED, Страховка RESCHEDULED, Спортзал PAUSED, Кредит ARCHIVED)
        assertEquals(0.0, forecast.getDouble("reservedFutureObligationsRub"), 0.01)
        // Просроченные обязательства = ровно 5 000 ₽ (ЖКХ без двойного вычитания долгового счёта)
        assertEquals(5_000.0, forecast.getDouble("overdueObligationsRub"), 0.01)
        // Остаток после обязательств до зарплаты = 75 000 - 5 000 = 70 000 ₽
        assertEquals(70_000.0, forecast.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)
    }

    @Test
    fun `issue 60 - profile switching budgetId isolates recurring schedule safe to spend preferences and clears stale facts`() = runBlocking {
        val d0 = java.time.LocalDate.of(2026, 11, 1).toEpochDay()
        val now = System.currentTimeMillis()

        val txs = listOf(
            TransactionEntity(id = "t_main", budgetId = "b_main", type = "income", date = "2026-10-25", category = "Начальный баланс", subcategory = "Карта", amount = 100_000.0, createdAt = now - 10_000L),
            TransactionEntity(id = "t_family", budgetId = "b_family", type = "income", date = "2026-10-25", category = "Начальный баланс", subcategory = "Карта", amount = 40_000.0, createdAt = now - 10_000L)
        )
        val recs = listOf(
            com.example.data.db.RecurringPaymentEntity(
                id = 1L,
                budgetId = "b_main",
                title = "Аренда офиса",
                amount = 20_000.0,
                isIncome = false,
                categoryName = "Аренда",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 5L,
                priority = "MANDATORY",
                status = "ACTIVE"
            ),
            com.example.data.db.RecurringPaymentEntity(
                id = 2L,
                budgetId = "b_family",
                title = "Детский сад",
                amount = 10_000.0,
                isIncome = false,
                categoryName = "Дети",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 3L,
                priority = "MANDATORY",
                status = "ACTIVE"
            )
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b_main",
            transactionDao = createReadOnlyTransactionDao(txs),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList()),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(recs),
            userSafetyFloorProvider = { id -> if (id == "b_main") 15_000.0 else 5_000.0 },
            userEssentialDailyNormProvider = { 1_000.0 },
            todayEpochDayProvider = { d0 }
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)
        controller.recordTranscriptTurn(DavidLiveSpeaker.USER, "Проверка профиля b_main")

        // Профиль 1: b_main
        val mainSched = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE, null)
        assertEquals("b_main", mainSched.getString("budgetId"))
        assertEquals(1, mainSched.getInt("upcomingExpensesCount"))
        assertEquals("Аренда офиса", mainSched.getJSONArray("upcomingExpenses").getJSONObject(0).getString("title"))
        assertEquals(100_000.0, mainSched.getDouble("availableBalanceRub"), 0.01)
        assertEquals(1, provider.verifiedFacts.size)

        // Переключаем профиль на b_family через контроллер
        controller.onBudgetProfileChanged("b_family")
        assertEquals("b_family", provider.getBudgetId())
        assertTrue("Verified facts from previous profile must be cleared on profile switch", provider.verifiedFacts.isEmpty())
        assertTrue("Transcript turns from previous profile must be cleared on profile switch", controller.transcriptTurns.isEmpty())

        // Профиль 2: b_family
        val familySched = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE, null)
        val familyForecast = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST, null)
        assertEquals("b_family", familySched.getString("budgetId"))
        assertEquals(1, familySched.getInt("upcomingExpensesCount"))
        assertEquals("Детский сад", familySched.getJSONArray("upcomingExpenses").getJSONObject(0).getString("title"))
        assertEquals(40_000.0, familySched.getDouble("availableBalanceRub"), 0.01)
        assertEquals(5_000.0, familyForecast.getDouble("mandatorySafetyFloorRub"), 0.01)
        assertEquals(30_000.0, familyForecast.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)
    }

    @Test
    fun `issue 60 - empty database and DAO errors or null DAOs report transparent status without fabricating zero balance as confirmed fact`() = runBlocking {
        // 1. Все DAO == null (источники данных отключены)
        val disconnectedProvider = DavidLiveFinancialToolsProvider(
            budgetId = "b_disconnected",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null,
            recurringPaymentDao = null
        )
        for (toolName in DavidLiveFinancialToolsProvider.ALL_REGISTERED_TOOLS) {
            val res = disconnectedProvider.executeToolCall(toolName, JSONObject())
            assertEquals("Tool $toolName must return status=data_unavailable when DAOs are null", "data_unavailable", res.getString("status"))
            assertFalse("Tool $toolName must report dataAvailable=false", res.getBoolean("dataAvailable"))
            assertFalse("Tool $toolName must report hasData=false", res.getBoolean("hasData"))
        }
        assertTrue("Disconnected tools must never record fabricated verifiedFacts", disconnectedProvider.verifiedFacts.isEmpty())

        // 2. DAO подключены, но база данных для профиля пуста
        val emptyProvider = DavidLiveFinancialToolsProvider(
            budgetId = "b_empty",
            transactionDao = createReadOnlyTransactionDao(emptyList()),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList()),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(emptyList())
        )
        val emptyOverview = emptyProvider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        assertEquals("ok", emptyOverview.getString("status"))
        assertTrue(emptyOverview.getBoolean("dataAvailable"))
        assertFalse(emptyOverview.getBoolean("hasData"))
        assertTrue(emptyOverview.getBoolean("isDatabaseEmpty"))
        assertTrue(emptyOverview.getString("humanSummary").contains("пуста"))

        val emptySchedule = emptyProvider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE, null)
        assertEquals("ok", emptySchedule.getString("status"))
        assertFalse(emptySchedule.getBoolean("hasData"))
        assertTrue(emptySchedule.getBoolean("isDatabaseEmpty"))

        // 3. DAO выбрасывает исключение при чтении
        val failingTxDao = object : TransactionDao {
            override fun getAllTransactions(): Flow<List<TransactionEntity>> = throw IllegalStateException("SQLite disk I/O error")
            override fun getTransactionsByBudgetId(budgetId: String): Flow<List<TransactionEntity>> = throw IllegalStateException("SQLite disk I/O error")
            override suspend fun insertTransaction(transaction: TransactionEntity) {}
            override suspend fun insertTransactions(transactions: List<TransactionEntity>) {}
            override suspend fun getExpenseCountForDate(dateStr: String): Int = 0
            override suspend fun getExpenseSumForDate(dateStr: String): Double = 0.0
            override fun getExpenseCountForDateSync(dateStr: String): Int = 0
            override suspend fun deleteTransactionById(id: String) {}
            override suspend fun deleteTransactionsByBudgetId(budgetId: String) {}
            override suspend fun deleteAllTransactions() {}
        }
        val failingProvider = DavidLiveFinancialToolsProvider(
            budgetId = "b_fail",
            transactionDao = failingTxDao,
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList()),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(emptyList())
        )
        val errorResult = failingProvider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        assertEquals("error", errorResult.getString("status"))
        assertFalse(errorResult.getBoolean("dataAvailable"))
        assertEquals("DAO_READ_ERROR", errorResult.getString("errorCode"))
        assertTrue("Failed DAO call must not record verifiedFacts", failingProvider.verifiedFacts.isEmpty())
    }

    @Test
    fun `issue 60 - combined multi-domain questions invoke all required tools and enrich DavidLiveSessionController tool responses`() = runBlocking {
        val d0 = java.time.LocalDate.of(2026, 11, 1).toEpochDay()
        val now = System.currentTimeMillis()
        val txs = listOf(
            TransactionEntity(id = "tx1", budgetId = "b1", type = "income", date = "2026-10-25", category = "Начальный баланс", subcategory = "Карта", amount = 65_000.0, createdAt = now - 10_000L)
        )
        val recs = listOf(
            com.example.data.db.RecurringPaymentEntity(
                id = 1L,
                budgetId = "b1",
                title = "Аренда",
                amount = 25_000.0,
                isIncome = false,
                categoryName = "Аренда",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 4L,
                priority = "MANDATORY",
                status = "ACTIVE"
            ),
            com.example.data.db.RecurringPaymentEntity(
                id = 2L,
                budgetId = "b1",
                title = "Зарплата",
                amount = 95_000.0,
                isIncome = true,
                incomeCertainty = "GUARANTEED",
                categoryName = "Зарплата",
                frequency = "MONTHLY",
                nextDueDateEpochDay = d0 + 10L,
                status = "ACTIVE"
            )
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(txs),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList()),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(recs),
            userEssentialDailyNormProvider = { 1_000.0 },
            todayEpochDayProvider = { d0 }
        )

        // 1. Маршрутизация вопроса «Сколько останется после аренды до зарплаты?»
        val specsRentSalary = provider.resolveRequiredToolsForQuery("Сколько останется после аренды до зарплаты?")
        val toolNamesRentSalary = specsRentSalary.map { it.toolName }
        assertTrue(toolNamesRentSalary.contains(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE))
        assertTrue(toolNamesRentSalary.contains(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST))

        val executedRentSalary = provider.executeToolsForQuery("Сколько останется после аренды до зарплаты?")
        assertEquals(2, executedRentSalary.size)
        val scheduleOutput = executedRentSalary.first { it.first == DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE }.second
        val forecastOutput = executedRentSalary.first { it.first == DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST }.second
        assertEquals(40_000.0, scheduleOutput.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)
        assertEquals(40_000.0, forecastOutput.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)

        // 2. Маршрутизация вопроса «Что будет, если зарплата задержится на 5 дней?»
        val specsDelay = provider.resolveRequiredToolsForQuery("Что будет, если зарплата задержится на 5 дней?")
        val toolNamesDelay = specsDelay.map { it.toolName }
        assertTrue(toolNamesDelay.contains(DavidLiveFinancialToolsProvider.TOOL_SIMULATE_WHAT_IF))
        assertTrue(toolNamesDelay.contains(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST))
        assertTrue(toolNamesDelay.contains(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE))
        val whatIfSpec = specsDelay.first { it.toolName == DavidLiveFinancialToolsProvider.TOOL_SIMULATE_WHAT_IF }
        assertEquals("INCOME_DELAY", whatIfSpec.args.getString("scenarioType"))
        assertEquals(5, whatIfSpec.args.getInt("delayDays"))
    }

    @Test
    fun `pr82 test 1 - today scheduled income is reported as due today with 0 days remaining and never shifted 30 days ahead`() = runBlocking {
        val d0 = java.time.LocalDate.of(2026, 11, 2).toEpochDay()
        val nowMs = java.time.LocalDate.of(2026, 11, 2)
            .atTime(12, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val accounts = listOf(
            AccountEntity(id = "acc_card", budgetId = "b_today_income", name = "Основная карта", balance = 42_000.0, type = "bank")
        )
        val txs = listOf(
            TransactionEntity(
                id = "tx_coffee",
                budgetId = "b_today_income",
                type = "expense",
                date = "2026-11-02",
                category = "Кафе и Доставка",
                subcategory = "Утренний кофе",
                amount = 350.0,
                accountId = null,
                createdAt = nowMs - 3_600_000L
            )
        )
        // Зарплата назначена на СЕГОДНЯ (2026-11-02, 2-е число месяца) и ещё НЕ получена (нет PAID лога)
        val salaryRuleToday = com.example.data.db.RecurringPaymentEntity(
            id = 501L,
            budgetId = "b_today_income",
            title = "Основная зарплата",
            amount = 85_000.0,
            isIncome = true,
            incomeCertainty = "GUARANTEED",
            categoryName = "Зарплата",
            frequency = "MONTHLY",
            anchorDayOfMonth = 2,
            nextDueDateEpochDay = d0,
            priority = "MANDATORY",
            status = "ACTIVE"
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b_today_income",
            transactionDao = createReadOnlyTransactionDao(txs),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(accounts),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(listOf(salaryRuleToday), emptyList()),
            userEssentialDailyNormProvider = { 1_000.0 },
            todayEpochDayProvider = { d0 }
        )

        // 1. Проверяем get_recurring_schedule
        val schedule = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE, null)
        assertEquals("ok", schedule.getString("status"))
        assertEquals(1, schedule.getInt("upcomingIncomesCount"))
        val incomeItem = schedule.getJSONArray("upcomingIncomes").getJSONObject(0)
        assertEquals("Основная зарплата", incomeItem.getString("title"))
        assertEquals("2026-11-02", incomeItem.getString("nextDueDateIso"))
        assertEquals("2 ноября 2026", incomeItem.getString("nextDueDateHumanRu"))
        assertEquals("2 ноября 2026 года (второго ноября)", incomeItem.getString("nextDueDateSpokenRu"))
        assertEquals(2, incomeItem.getInt("dayOfMonth"))
        assertEquals("второго", incomeItem.getString("dayOrdinalGenitiveRu"))
        assertEquals("второе", incomeItem.getString("dayOrdinalNominativeRu"))
        assertEquals(0, incomeItem.getInt("daysUntilNextDue"))
        assertTrue("Salary scheduled for today must have isDueToday=true", incomeItem.getBoolean("isDueToday"))
        assertFalse("Salary not yet received must have executedToday=false", incomeItem.getBoolean("executedToday"))
        assertEquals("сегодня, 2 ноября 2026 года (второго ноября)", incomeItem.getString("whenSpokenRu"))
        assertEquals("2026-12-02", incomeItem.getString("followingCycleDueDateIso"))
        assertTrue(schedule.getString("humanSummary").contains("сегодня, 2 ноября 2026 года (второго ноября)"))

        // 2. Проверяем get_safe_to_spend_forecast
        val forecast = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST, null)
        assertEquals("ok", forecast.getString("status"))
        assertEquals("2026-11-02", forecast.getString("nextIncomeDateIso"))
        assertEquals("2 ноября 2026 года (второго ноября)", forecast.getString("nextIncomeDateSpokenRu"))
        assertEquals("сегодня, 2 ноября 2026 года (второго ноября)", forecast.getString("nextIncomeWhenSpokenRu"))
        val forecastIncomes = forecast.getJSONArray("upcomingIncomes")
        assertTrue(forecastIncomes.length() >= 1)
        val firstForecastIncome = forecastIncomes.getJSONObject(0)
        assertEquals("2026-11-02", firstForecastIncome.getString("dueDateIso"))
        assertEquals(0, firstForecastIncome.getInt("daysUntilDue"))
        assertTrue(firstForecastIncome.getBoolean("isDueToday"))

        // 3. Проверяем get_financial_overview
        val overview = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        assertEquals("ok", overview.getString("status"))
        assertTrue(overview.getInt("upcomingIncomesCount") >= 1)
        assertEquals("2026-11-02", overview.getString("nextIncomeDateIso"))
        assertEquals(0, overview.getInt("daysUntilNextIncome"))
        assertTrue(overview.getBoolean("isNextIncomeDueToday"))
        assertEquals("сегодня, 2 ноября 2026 года (второго ноября)", overview.getString("nextIncomeWhenSpokenRu"))
    }

    @Test
    fun `pr82 test 1 - RussianDateVerbalizer and DavidLiveSessionController prevent 2nd day from turning into 22nd in voice or transcript`() {
        val nov2 = java.time.LocalDate.of(2026, 11, 2).toEpochDay()
        val nov12 = java.time.LocalDate.of(2026, 11, 12).toEpochDay()
        val nov22 = java.time.LocalDate.of(2026, 11, 22).toEpochDay()

        assertEquals("второго", com.example.data.safetospend.RussianDateVerbalizer.dayOrdinalGenitiveRu(2))
        assertEquals("двенадцатого", com.example.data.safetospend.RussianDateVerbalizer.dayOrdinalGenitiveRu(12))
        assertEquals("двадцать второго", com.example.data.safetospend.RussianDateVerbalizer.dayOrdinalGenitiveRu(22))

        assertEquals("2 ноября 2026 года (второго ноября)", com.example.data.safetospend.RussianDateVerbalizer.formatSpokenDateGenitiveRu(nov2))
        assertEquals("12 ноября 2026 года (двенадцатого ноября)", com.example.data.safetospend.RussianDateVerbalizer.formatSpokenDateGenitiveRu(nov12))
        assertEquals("22 ноября 2026 года (двадцать второго ноября)", com.example.data.safetospend.RussianDateVerbalizer.formatSpokenDateGenitiveRu(nov22))
        assertEquals("сегодня, 2 ноября 2026 года (второго ноября)", com.example.data.safetospend.RussianDateVerbalizer.formatRelativeDueSpokenRu(nov2, nov2))

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)
        controller.startSession("AIzaSyTestUserManualKey987654321")
        controller.processServerMessageForTest("""{"setupComplete": {}}""")

        // Сценарий A: приходят потоковые чанки outputTranscription И одновременно дублирующий modelTurn.parts.text с числом "2"
        // Контроллер НЕ должен склеивать оба потока и превращать "2" + "2" в "22"
        controller.processServerMessageForTest(
            """{"serverContent":{"outputTranscription":{"text":"Следующая зарплата придет 2"}}}"""
        )
        controller.processServerMessageForTest(
            """{"serverContent":{"modelTurn":{"parts":[{"text":"2 ноября"}]}}}"""
        )
        controller.processServerMessageForTest(
            """{"serverContent":{"outputTranscription":{"text":" ноября (второго ноября)."}}}"""
        )
        controller.processServerMessageForTest(
            """{"serverContent":{"turnComplete":true}}"""
        )

        val davidTurns = controller.transcriptTurns.filter { it.speaker == DavidLiveSpeaker.DAVID }
        assertEquals(1, davidTurns.size)
        assertEquals("Следующая зарплата придет 2 ноября (второго ноября).", davidTurns.first().text)
        assertFalse("Must never turn '2' into '22'", davidTurns.first().text.contains("22"))
    }

    @Test
    fun `pr82 full verification - realistic budget 01_01_2026 to 02_11_2026 verifies all 8 tools WhatIf month boundaries and profile isolation`() = runBlocking {
        val dataset = com.example.data.safetospend.RealisticTestBudget2026Seeder.buildDataset(
            budgetId = com.example.data.safetospend.RealisticTestBudget2026Seeder.TEST_BUDGET_ID
        )
        val d0 = dataset.referenceTodayEpochDay // 2026-11-02 (epochDay = 20759)

        // 0. Проверка границ диапазона дат 01.01.2026 – 02.11.2026 включительно и отсутствия будущих операций
        val minDate = dataset.transactions.minOf { it.date }
        val maxDate = dataset.transactions.maxOf { it.date }
        assertEquals("2026-01-01", minDate)
        assertEquals("2026-11-02", maxDate)
        assertTrue(
            "Zero transactions may have dates after 2026-11-02",
            dataset.transactions.none { it.date > "2026-11-02" }
        )
        // Проверяем, что представлены все 11 месяцев с января по ноябрь 2026
        val monthsPresent = dataset.transactions.map { it.date.substring(0, 7) }.toSet()
        assertEquals(
            setOf(
                "2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06",
                "2026-07", "2026-08", "2026-09", "2026-10", "2026-11"
            ),
            monthsPresent
        )

        // Проверка переходов границ месяцев (31 января -> 28 февраля -> 31 марта)
        val jan31 = java.time.LocalDate.of(2026, 1, 31).toEpochDay()
        val feb28 = com.example.data.safetospend.RecurringScheduleCalculator.computeNextDueDate(
            currentDueDateEpochDay = jan31,
            frequency = com.example.data.safetospend.PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 31
        )
        assertEquals(java.time.LocalDate.of(2026, 2, 28).toEpochDay(), feb28)
        val mar31 = com.example.data.safetospend.RecurringScheduleCalculator.computeNextDueDate(
            currentDueDateEpochDay = feb28,
            frequency = com.example.data.safetospend.PaymentFrequency.MONTHLY,
            anchorDayOfMonth = 31
        )
        assertEquals(java.time.LocalDate.of(2026, 3, 31).toEpochDay(), mar31)

        // Добавим данные другого профиля ("personal_other") в общие списки DAO для проверки строгой изоляции по budgetId
        val otherAccount = AccountEntity(
            id = "acc_other",
            budgetId = "personal_other",
            name = "Чужой счёт",
            balance = 999_999.0,
            type = "bank"
        )
        val otherTx = TransactionEntity(
            id = "tx_other",
            budgetId = "personal_other",
            type = "income",
            date = "2026-11-01",
            category = "Зарплата",
            subcategory = "Чужая зарплата",
            amount = 500_000.0,
            accountId = null
        )
        val otherRecurring = com.example.data.db.RecurringPaymentEntity(
            id = 999L,
            budgetId = "personal_other",
            title = "Чужая ипотека",
            amount = 120_000.0,
            isIncome = false,
            categoryName = "Ипотека",
            frequency = "MONTHLY",
            nextDueDateEpochDay = d0 + 3L,
            priority = "MANDATORY",
            status = "ACTIVE"
        )

        val provider = DavidLiveFinancialToolsProvider(
            budgetId = com.example.data.safetospend.RealisticTestBudget2026Seeder.TEST_BUDGET_ID,
            transactionDao = createReadOnlyTransactionDao(dataset.transactions + otherTx),
            categoryDao = createReadOnlyCategoryDao(dataset.categories),
            goalDao = createReadOnlyGoalDao(dataset.goals),
            accountDao = createReadOnlyAccountDao(dataset.accounts + otherAccount),
            recurringPaymentDao = createReadOnlyRecurringPaymentDao(
                dataset.recurringPayments + otherRecurring,
                dataset.executionLogs
            ),
            userSafetyFloorProvider = { dataset.mandatorySafetyFloorRub },
            userEssentialDailyNormProvider = { dataset.essentialDailyNormRub },
            todayEpochDayProvider = { d0 }
        )

        // 1. Инструмент 1: get_financial_overview
        val overview = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        assertEquals("ok", overview.getString("status"))
        assertEquals(115_000L, overview.getLong("availableBalanceRub"))
        assertEquals(144_500L, overview.getLong("earnedIncome30dRub"))
        assertEquals(130_000L, overview.getLong("salaryIncome30dRub"))
        assertEquals(14_500L, overview.getLong("otherEarnedIncome30dRub"))
        assertEquals(5_000L, overview.getLong("debtReturned30dRub"))
        assertEquals(97_828L, overview.getLong("regularExpense30dRub"))
        assertEquals(18_500L, overview.getLong("debtRepayment30dRub"))
        assertEquals(116_328L, overview.getLong("expense30dRub"))
        assertEquals(28_172L, overview.getLong("netCashFlow30dRub"))
        assertEquals(49_000L, overview.getLong("totalWeOweRub"))
        assertEquals(20_000L, overview.getLong("totalOwesUsRub"))

        // 2. Инструмент 2: get_recurring_schedule
        val schedule = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_RECURRING_SCHEDULE,
            JSONObject().put("horizonDays", 35)
        )
        assertEquals("ok", schedule.getString("status"))
        assertEquals(7, schedule.getInt("activeRulesCount"))
        assertEquals(1, schedule.getInt("pausedOrArchivedRulesCount"))
        assertEquals(0, schedule.getInt("overdueExpensesCount"))
        // Проверяем, что оплаченный сегодня (02.11.2026) интернет НЕ висит как неоплаченный на сегодня, а переведён на 02.12.2026 ("второго декабря")
        val upcomingExpenses = schedule.getJSONArray("upcomingExpenses")
        var foundRentNov5 = false
        var foundInternetDec2 = false
        for (i in 0 until upcomingExpenses.length()) {
            val item = upcomingExpenses.getJSONObject(i)
            when (item.getString("title")) {
                "Аренда квартиры" -> {
                    assertEquals("2026-11-05", item.getString("nextDueDateIso"))
                    assertEquals(3, item.getInt("daysUntilNextDue"))
                    assertEquals(38_000.0, item.getDouble("amountRub"), 0.01)
                    foundRentNov5 = true
                }
                "Домашний интернет и мобильная связь" -> {
                    assertEquals("2026-12-02", item.getString("nextDueDateIso"))
                    assertEquals(30, item.getInt("daysUntilNextDue"))
                    assertEquals("второго", item.getString("dayOrdinalGenitiveRu"))
                    assertTrue(item.getString("nextDueDateSpokenRu").contains("второго декабря"))
                    foundInternetDec2 = true
                }
            }
        }
        assertTrue(foundRentNov5)
        assertTrue(foundInternetDec2)

        // 3. Инструмент 3: get_safe_to_spend_forecast
        val forecast = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST, null)
        assertEquals("ok", forecast.getString("status"))
        assertEquals(115_000.0, forecast.getDouble("availableBalanceRub"), 0.01)
        assertEquals(8, forecast.getInt("horizonDays"))
        assertEquals("2026-11-10", forecast.getString("nextIncomeDateIso"))
        // До зарплаты 10.11 резервируются Аренда (38 000 ₽ 05.11) + Долг брату Андрею (14 000 ₽ 08.11) = 52 000 ₽
        assertEquals(0.0, forecast.getDouble("todayPendingObligationsRub"), 0.01)
        assertEquals(52_000.0, forecast.getDouble("reservedFutureObligationsRub"), 0.01)
        assertEquals(52_000.0, forecast.getDouble("obligationsReservedTotalRub"), 0.01)
        assertEquals(63_000.0, forecast.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)
        assertEquals(15_000.0, forecast.getDouble("mandatorySafetyFloorRub"), 0.01)
        val forecastObligations = forecast.getJSONArray("upcomingObligations")
        var foundDebtNov8InForecast = false
        for (i in 0 until forecastObligations.length()) {
            val obl = forecastObligations.getJSONObject(i)
            if (obl.getString("title") == "Долг: Долг брату Андрею") {
                assertEquals("2026-11-08", obl.getString("dueDateIso"))
                assertEquals(6, obl.getInt("daysUntilDue"))
                assertEquals(14_000.0, obl.getDouble("amountRub"), 0.01)
                foundDebtNov8InForecast = true
            }
        }
        assertTrue(foundDebtNov8InForecast)
        val baseSafeToday = forecast.getDouble("canSpendTodayDiscRub")
        assertTrue("Safe-to-spend today must be positive", baseSafeToday > 0.0)

        // 4. Инструмент 4: simulate_what_if (3 сценария: разовая покупка 5 000 ₽, задержка зарплаты на 5 дн., рост аренды на +4 000 ₽)
        // 4a. Разовая трата 5 000 ₽
        val whatIfSpend5k = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_SIMULATE_WHAT_IF,
            JSONObject()
                .put("scenarioType", "HYPOTHETICAL_SPEND")
                .put("amountRub", 5_000.0)
        )
        assertEquals("ok", whatIfSpend5k.getString("status"))
        assertEquals(58_000.0, whatIfSpend5k.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)
        assertTrue(whatIfSpend5k.getDouble("simulatedCanSpendTodayRub") < baseSafeToday)

        // 4b. Задержка зарплаты на 5 дней (с 10.11 на 15.11)
        val whatIfSalaryDelay = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_SIMULATE_WHAT_IF,
            JSONObject()
                .put("scenarioType", "INCOME_DELAY")
                .put("delayDays", 5)
        )
        assertEquals("ok", whatIfSalaryDelay.getString("status"))
        assertEquals(8, whatIfSalaryDelay.getInt("baselineHorizonDays"))
        assertEquals(13, whatIfSalaryDelay.getInt("simulatedHorizonDays"))
        assertEquals(52_000.0, whatIfSalaryDelay.getDouble("baselineObligationsReservedRub"), 0.01)
        assertEquals(58_800.0, whatIfSalaryDelay.getDouble("simulatedObligationsReservedRub"), 0.01)
        assertEquals(56_200.0, whatIfSalaryDelay.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)
        assertEquals(1, whatIfSalaryDelay.getInt("newlyExposedObligationsCount"))
        assertTrue(whatIfSalaryDelay.getDouble("simulatedCanSpendTodayRub") <= baseSafeToday)

        // 4c. Увеличение обязательного платежа (Аренда квартиры +4 000 ₽ -> 42 000 ₽)
        val whatIfMandatoryIncrease = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_SIMULATE_WHAT_IF,
            JSONObject()
                .put("scenarioType", "MANDATORY_PAYMENT_CHANGE")
                .put("deltaAmountRub", 4_000.0)
                .put("targetTitle", "Аренда квартиры")
        )
        assertEquals("ok", whatIfMandatoryIncrease.getString("status"))
        assertEquals(52_000.0, whatIfMandatoryIncrease.getDouble("baselineObligationsReservedRub"), 0.01)
        assertEquals(56_000.0, whatIfMandatoryIncrease.getDouble("simulatedObligationsReservedRub"), 0.01)
        assertEquals(59_000.0, whatIfMandatoryIncrease.getDouble("netBalanceAfterObligationsBeforeSalaryRub"), 0.01)

        // 5. Инструмент 5: get_goals_and_debts
        val goalsAndDebts = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_GOALS_AND_DEBTS, null)
        assertEquals("ok", goalsAndDebts.getString("status"))
        assertEquals(2, goalsAndDebts.getInt("count"))
        assertEquals(4, goalsAndDebts.getInt("debtsCount"))
        assertEquals(49_000L, goalsAndDebts.getLong("totalWeOweRub"))
        assertEquals(20_000L, goalsAndDebts.getLong("totalOwesUsRub"))
        val goalsItems = goalsAndDebts.getJSONArray("items")
        var totalGoalsSavedRub = 0L
        for (i in 0 until goalsItems.length()) {
            totalGoalsSavedRub += goalsItems.getJSONObject(i).getLong("savedAmountRub")
        }
        assertEquals(237_000L, totalGoalsSavedRub)

        // 6. Инструмент 6: get_category_spending
        val categorySpending = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_CATEGORY_SPENDING, null)
        assertEquals("ok", categorySpending.getString("status"))
        val catsArray = categorySpending.getJSONArray("categories")
        val catMap = mutableMapOf<String, JSONObject>()
        var totalCatSpent30d = 0L
        for (i in 0 until catsArray.length()) {
            val obj = catsArray.getJSONObject(i)
            catMap[obj.getString("category")] = obj
            totalCatSpent30d += obj.getLong("spentRub")
        }
        assertEquals(97_828L, totalCatSpent30d)
        assertEquals(44_380L, catMap["ЖКХ и Аренда"]!!.getLong("spentRub"))
        assertFalse(catMap["ЖКХ и Аренда"]!!.getBoolean("isLimitExceeded"))
        assertEquals(19_840L, catMap["Продукты"]!!.getLong("spentRub"))
        assertFalse(catMap["Продукты"]!!.getBoolean("isLimitExceeded"))
        assertEquals(9_850L, catMap["Кафе и Доставка"]!!.getLong("spentRub"))
        assertTrue(catMap["Кафе и Доставка"]!!.getBoolean("isLimitExceeded"))
        assertEquals(8_400L, catMap["Развлечения и Хобби"]!!.getLong("spentRub"))
        assertTrue(catMap["Развлечения и Хобби"]!!.getBoolean("isLimitExceeded"))

        // 7. Инструмент 7: compare_spending_periods (7 дней и 30 дней)
        val compare7d = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_COMPARE_PERIODS,
            JSONObject().put("days", 7)
        )
        assertEquals("ok", compare7d.getString("status"))
        assertEquals(9_500L, compare7d.getLong("currentExpenseRub"))
        assertEquals(25_140L, compare7d.getLong("previousExpenseRub"))
        assertEquals(-62L, compare7d.getLong("expenseChangePercent"))

        val compare30d = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_COMPARE_PERIODS,
            JSONObject().put("days", 30)
        )
        assertEquals("ok", compare30d.getString("status"))
        assertEquals(116_328L, compare30d.getLong("currentExpenseRub"))
        assertEquals(94_088L, compare30d.getLong("previousExpenseRub"))
        assertEquals(144_500L, compare30d.getLong("currentIncomeRub"))
        assertEquals(130_000L, compare30d.getLong("previousIncomeRub"))
        assertEquals(24L, compare30d.getLong("expenseChangePercent"))

        // 8. Инструмент 8: get_recent_transactions
        val recentTxs = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_RECENT_TRANSACTIONS,
            JSONObject().put("limit", 5)
        )
        assertEquals("ok", recentTxs.getString("status"))
        val txArray = recentTxs.getJSONArray("transactions")
        assertEquals(5, txArray.length())
        assertEquals("2026-11-02", txArray.getJSONObject(0).getString("date"))
        assertEquals("2026-11-02", txArray.getJSONObject(1).getString("date"))
        assertEquals("2026-11-02", txArray.getJSONObject(2).getString("date"))
        assertEquals("2026-11-02", txArray.getJSONObject(3).getString("date"))
        assertEquals("2026-11-01", txArray.getJSONObject(4).getString("date"))

        // 9. Проверка строгой изоляции профилей: переключение на "personal_other" не смешивает данные с "demo_2026_test"
        provider.setBudgetId("personal_other")
        val otherOverview = provider.executeToolCall(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, null)
        assertEquals("personal_other", otherOverview.getString("budgetId"))
        assertEquals(500_000L, otherOverview.getLong("availableBalanceRub"))
        assertEquals(500_000L, otherOverview.getLong("earnedIncome30dRub"))
        assertEquals(0L, otherOverview.getLong("regularExpense30dRub"))
    }
}
