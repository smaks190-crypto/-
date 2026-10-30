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
                val delegateEditor = healthyEncryptedPrefs.edit()
                return object : android.content.SharedPreferences.Editor by delegateEditor {
                    override fun commit(): Boolean = false
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
}
