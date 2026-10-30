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
import com.example.utils.VoiceParserHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    fun `ping pong timeout during David speech immediately resets activeSpeaker to NONE performs bounded auto-reconnect and shows Russian error`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val fakeFactory = FakeWebSocketFactory()
        val controller = DavidLiveSessionController(
            financialToolsProvider = provider,
            webSocketFactory = fakeFactory,
            maxAutoReconnectAttempts = 2,
            autoReconnectDelayMs = 0L
        )

        val testKey = "AIzaSyTestUserManualKey987654321"
        controller.startSession(testKey)
        assertEquals(1, fakeFactory.entries.size)
        val firstSocket = fakeFactory.entries[0]

        // 1. Setup complete -> LISTENING
        firstSocket.simulateOpen()
        firstSocket.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // 2. David starts speaking (outputTranscription + active audio visualizer frame)
        firstSocket.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"Привет! Твой баланс в полном порядке."}}}"""
        )
        val davidSpeechSamples = ShortArray(1024) { i ->
            (sin(2.0 * PI * 440.0 * i / 24_000.0) * 16000).toInt().toShort()
        }
        controller.audioEngine.feedTestPcmFrame(
            samples = davidSpeechSamples,
            sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
            speaker = DavidLiveSpeaker.DAVID
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        assertEquals(DavidLiveSpeaker.DAVID, controller.audioEngine.activeSpeaker.value)
        assertEquals(
            "Говорит Давид...",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value
            )
        )

        // 3. Ping/pong timeout occurs mid-speech:
        // "sent ping but didn't receive pong within 20000ms (after 0 successful ping/pongs)"
        val pingTimeout = SocketTimeoutException(
            "sent ping but didn't receive pong within 20000ms (after 0 successful ping/pongs)"
        )
        firstSocket.simulateFailure(pingTimeout)

        // Immediately after failure: activeSpeaker MUST be reset to NONE, transcript preserved,
        // and bounded auto-reconnect attempt #1 opens socket #2
        assertEquals(DavidLiveSpeaker.NONE, controller.audioEngine.activeSpeaker.value)
        assertEquals(1, controller.transcriptTurns.size)
        assertEquals("Привет! Твой баланс в полном порядке.", controller.transcriptTurns.first().text)
        assertEquals(2, fakeFactory.entries.size)
        assertEquals(DavidLiveConnectionState.RECONNECTING, controller.connectionState.value)
        assertEquals(
            "Восстанавливаем связь...",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value
            )
        )

        // 4. Auto-reconnect #1 also fails with ping timeout -> triggers auto-reconnect #2 (socket #3)
        fakeFactory.entries[1].simulateFailure(pingTimeout)
        assertEquals(3, fakeFactory.entries.size)
        assertEquals(DavidLiveConnectionState.RECONNECTING, controller.connectionState.value)
        assertEquals(DavidLiveSpeaker.NONE, controller.audioEngine.activeSpeaker.value)

        // 5. Auto-reconnect #2 fails (retries exhausted) -> transitions to ERROR with Russian message
        fakeFactory.entries[2].simulateFailure(pingTimeout)
        assertEquals(3, fakeFactory.entries.size)
        assertEquals(DavidLiveConnectionState.ERROR, controller.connectionState.value)
        assertEquals(DavidLiveSpeaker.NONE, controller.audioEngine.activeSpeaker.value)
        assertEquals(DavidLiveVisualPhase.CONNECTION_LOST, controller.visualPhase.value)

        val errMsg = controller.errorMessage.value
        assertNotNull(errMsg)
        assertTrue(errMsg!!.contains("тайм-аут пинга"))
        assertTrue(errMsg.contains("Повторить подключение"))
        assertFalse("API key must never appear in error messages", errMsg.contains(testKey))

        // Status label and visualizer capability must NEVER say "Говорит Давид..." in ERROR state,
        // even if a caller passes a stale DavidLiveSpeaker.DAVID
        assertEquals(
            "Связь с Давидом прервана",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value
            )
        )
        assertEquals(
            "Связь с Давидом прервана",
            resolveDavidLiveStatusLabel(
                DavidLiveConnectionState.ERROR,
                DavidLiveSpeaker.DAVID,
                false
            )
        )
        assertFalse(isDavidLiveVoiceCapableState(DavidLiveConnectionState.ERROR))
        assertFalse(isDavidLiveVoiceCapableState(DavidLiveConnectionState.RECONNECTING))
        assertFalse(isDavidLiveVoiceCapableState(DavidLiveConnectionState.CONNECTING))
    }

    @Test
    fun `auto reconnect after unexpected onClosing restores LISTENING state and manual retry preserves conversation turns`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val fakeFactory = FakeWebSocketFactory()
        val controller = DavidLiveSessionController(
            financialToolsProvider = provider,
            webSocketFactory = fakeFactory,
            maxAutoReconnectAttempts = 1,
            autoReconnectDelayMs = 0L
        )

        controller.startSession("AIzaSyTestUserManualKey987654321")
        val socket1 = fakeFactory.entries[0]
        socket1.simulateOpen()
        socket1.simulateServerText("""{"setupComplete": {}}""")
        socket1.simulateServerText("""{"serverContent":{"inputTranscription":{"text":"Сколько я потратил за неделю?"}}}""")
        socket1.simulateServerText("""{"serverContent":{"outputTranscription":{"text":"За неделю расходы составили 12 400 ₽."},"turnComplete":true}}""")
        assertEquals(2, controller.transcriptTurns.size)

        // 1. Server closes unexpectedly with code 1006 -> auto-reconnects on socket #2
        socket1.simulateClosing(1006, "Abnormal closure")
        assertEquals(2, fakeFactory.entries.size)
        assertEquals(DavidLiveConnectionState.RECONNECTING, controller.connectionState.value)
        assertEquals(DavidLiveSpeaker.NONE, controller.audioEngine.activeSpeaker.value)

        // 2. Socket #2 completes setup -> session resumes in LISTENING with previous turns intact
        val socket2 = fakeFactory.entries[1]
        socket2.simulateOpen()
        socket2.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertTrue(controller.liveSubtitle.value.contains("Связь восстановлена"))
        assertEquals(2, controller.transcriptTurns.size)

        // 3. Now simulate disconnect that exhausts auto-reconnect (attempt #1 fails on socket #3)
        socket2.simulateClosing(1011, "Internal server error")
        assertEquals(3, fakeFactory.entries.size)
        fakeFactory.entries[2].simulateFailure(SocketTimeoutException("Read timed out"))
        assertEquals(DavidLiveConnectionState.ERROR, controller.connectionState.value)

        // 4. User taps "Повторить подключение" (retryConnection) -> opens socket #4 and preserves transcriptTurns!
        controller.retryConnection()
        assertEquals(4, fakeFactory.entries.size)
        assertEquals(DavidLiveConnectionState.CONNECTING, controller.connectionState.value)
        assertEquals(DavidLiveVisualPhase.RECONNECTING, controller.visualPhase.value)
        fakeFactory.entries[3].simulateOpen()
        fakeFactory.entries[3].simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertEquals("Previous conversation turns must be preserved across manual retry", 2, controller.transcriptTurns.size)
    }

    @Test
    fun `stale WebSocket callbacks from previous socket are ignored after reconnect or session close`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val fakeFactory = FakeWebSocketFactory()
        val controller = DavidLiveSessionController(
            financialToolsProvider = provider,
            webSocketFactory = fakeFactory,
            maxAutoReconnectAttempts = 1,
            autoReconnectDelayMs = 0L
        )

        controller.startSession("AIzaSyTestUserManualKey987654321")
        val staleSocket = fakeFactory.entries[0]
        staleSocket.simulateOpen()
        staleSocket.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // Fast manual retry replaces staleSocket with activeSocket
        controller.retryConnection()
        assertEquals(2, fakeFactory.entries.size)
        val activeSocket = fakeFactory.entries[1]
        activeSocket.simulateOpen()
        activeSocket.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // Stale callbacks arriving late from staleSocket must be completely ignored
        staleSocket.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"Устаревшая реплика из закрытого сокета"}}}"""
        )
        staleSocket.simulateClosing(1006, "Late close from old socket")
        staleSocket.simulateFailure(
            SocketTimeoutException("sent ping but didn't receive pong within 20000ms (after 0 successful ping/pongs)")
        )

        assertEquals(
            "Stale socket failure must not overwrite active session state",
            DavidLiveConnectionState.LISTENING,
            controller.connectionState.value
        )
        assertNull(controller.errorMessage.value)
        assertTrue(controller.transcriptTurns.none { it.text.contains("Устаревшая") })
    }

    @Test
    fun `offline network error skips futile model fallback and background transition resets speaking state`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val fakeFactory = FakeWebSocketFactory()
        val controller = DavidLiveSessionController(
            financialToolsProvider = provider,
            webSocketFactory = fakeFactory,
            maxAutoReconnectAttempts = 2,
            autoReconnectDelayMs = 0L
        )

        // 1. Offline DNS failure -> immediately transitions to ERROR without cycling through 3 models
        controller.startSession("AIzaSyTestUserManualKey987654321")
        assertEquals(1, fakeFactory.entries.size)
        fakeFactory.entries[0].simulateFailure(
            UnknownHostException("Unable to resolve host \"generativelanguage.googleapis.com\": No address associated with hostname")
        )
        assertEquals("Offline error must not spin through fallback models", 1, fakeFactory.entries.size)
        assertEquals(DavidLiveConnectionState.ERROR, controller.connectionState.value)
        assertTrue(controller.errorMessage.value!!.contains("Нет соединения с интернетом"))

        // 2. Retry succeeds, David starts speaking, then app goes to background -> speaking state resets immediately
        controller.retryConnection()
        val socket2 = fakeFactory.entries[1]
        socket2.simulateOpen()
        socket2.simulateServerText("""{"setupComplete": {}}""")
        socket2.simulateServerText("""{"serverContent":{"outputTranscription":{"text":"Проверяю твои лимиты..."}}}""")
        controller.audioEngine.feedTestPcmFrame(
            samples = ShortArray(512) { 4000 },
            sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
            speaker = DavidLiveSpeaker.DAVID
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        assertEquals(DavidLiveSpeaker.DAVID, controller.audioEngine.activeSpeaker.value)

        controller.onAppBackgrounded()
        assertEquals(DavidLiveSpeaker.NONE, controller.audioEngine.activeSpeaker.value)
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        controller.onAppForegrounded()
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
    }

    @Test
    fun `visual connection phases and Russian labels strictly reflect setupComplete and never show ready on socket onOpen alone`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val fakeFactory = FakeWebSocketFactory()
        val controller = DavidLiveSessionController(
            financialToolsProvider = provider,
            webSocketFactory = fakeFactory,
            maxAutoReconnectAttempts = 1,
            autoReconnectDelayMs = 0L,
            readyFlashDurationMs = 0L
        )

        // 1. Первое подключение -> INITIAL_CONNECTING, подпись «Пробуждаем Давида...»
        controller.startSession("AIzaSyTestUserManualKey987654321")
        assertEquals(DavidLiveConnectionState.CONNECTING, controller.connectionState.value)
        assertEquals(DavidLiveVisualPhase.INITIAL_CONNECTING, controller.visualPhase.value)
        assertEquals(
            "Пробуждаем Давида...",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value,
                controller.visualPhase.value
            )
        )

        // 2. Только открытие сокета (onOpen без setupComplete) НЕ должно показывать «Давид на связи»
        val socket1 = fakeFactory.entries[0]
        socket1.simulateOpen()
        assertEquals(DavidLiveConnectionState.CONNECTING, controller.connectionState.value)
        assertEquals(DavidLiveVisualPhase.INITIAL_CONNECTING, controller.visualPhase.value)
        assertFalse(controller.isReadyFlashActive.value)
        assertEquals(
            "Пробуждаем Давида...",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value,
                controller.visualPhase.value
            )
        )

        // 3. Проверка READY_FLASH и подписи «Давид на связи» строго после setupComplete
        assertEquals(
            "Давид на связи",
            resolveDavidLiveStatusLabel(
                DavidLiveConnectionState.LISTENING,
                DavidLiveSpeaker.NONE,
                false,
                DavidLiveVisualPhase.READY_FLASH
            )
        )

        socket1.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // 4. Обрыв соединения -> RECONNECTING («Восстанавливаем связь...»), а после исчерпания попыток -> CONNECTION_LOST («Связь с Давидом прервана»)
        socket1.simulateFailure(SocketTimeoutException("sent ping but didn't receive pong within 20000ms"))
        assertEquals(DavidLiveConnectionState.RECONNECTING, controller.connectionState.value)
        assertEquals(DavidLiveVisualPhase.RECONNECTING, controller.visualPhase.value)
        assertEquals(
            "Восстанавливаем связь...",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value,
                controller.visualPhase.value
            )
        )

        fakeFactory.entries[1].simulateFailure(SocketTimeoutException("sent ping but didn't receive pong within 20000ms"))
        assertEquals(DavidLiveConnectionState.ERROR, controller.connectionState.value)
        assertEquals(DavidLiveVisualPhase.CONNECTION_LOST, controller.visualPhase.value)
        assertEquals(
            "Связь с Давидом прервана",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value,
                controller.visualPhase.value
            )
        )
    }

    @Test
    fun `network switch during David speech transitions to CONNECTION_LOST avoids parallel reconnects and continues interrupted thought on new socket`() = runBlocking {
        val now = System.currentTimeMillis()
        val fakeTransactions = listOf(
            TransactionEntity(
                id = "tx1",
                budgetId = "b1",
                type = "expense",
                date = "2026-10-26",
                category = "Продукты",
                subcategory = "Супермаркет",
                amount = 15_000.0,
                createdAt = now - 10_000L
            )
        )
        val fakeCategories = listOf(
            CategoryEntity(id = "c1", budgetId = "b1", type = "expense", name = "Продукты", monthlyLimit = 25_000.0)
        )
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(fakeTransactions),
            categoryDao = createReadOnlyCategoryDao(fakeCategories),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(emptyList())
        )

        // Выполняем tool call до обрыва и проверяем дедупликацию повторного вызова после переподключения
        val firstToolRes = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_CATEGORY_SPENDING,
            JSONObject().put("categoryQuery", "Продукты")
        )
        assertEquals("ok", firstToolRes.getString("status"))
        assertEquals(1, provider.verifiedFacts.size)

        val duplicateToolRes = provider.executeToolCall(
            DavidLiveFinancialToolsProvider.TOOL_CATEGORY_SPENDING,
            JSONObject().put("categoryQuery", "Продукты")
        )
        assertEquals("ok", duplicateToolRes.getString("status"))
        assertEquals("Duplicate tool call after reconnect must not duplicate verifiedFacts", 1, provider.verifiedFacts.size)

        val fakeFactory = FakeWebSocketFactory()
        val controller = DavidLiveSessionController(
            financialToolsProvider = provider,
            webSocketFactory = fakeFactory,
            maxAutoReconnectAttempts = 2,
            autoReconnectDelayMs = 0L,
            readyFlashDurationMs = 0L
        )

        controller.startSession("AIzaSyTestUserManualKey987654321")
        val socket1 = fakeFactory.entries[0]
        socket1.simulateOpen()
        socket1.simulateServerText("""{"setupComplete": {}}""")

        // Пользователь задаёт вопрос, Давид начинает длинный ответ (без turnComplete)
        socket1.simulateServerText(
            """{"serverContent":{"inputTranscription":{"text":"Давид, разбери мои траты на продукты и посоветуй как сэкономить"}}}"""
        )
        socket1.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"Смотри, по категории Продукты ты уже потратил 15 000 рублей из 25 000. Во-первых, стоит сократить спонтанные покупки, а во-вторых"}}}"""
        )
        controller.audioEngine.feedTestPcmFrame(
            samples = ShortArray(512) { 6000 },
            sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
            speaker = DavidLiveSpeaker.DAVID
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)

        // 1. Происходит потеря сети (например, отключение Wi-Fi перед переключением на LTE)
        controller.onNetworkLost()
        assertEquals(DavidLiveConnectionState.ERROR, controller.connectionState.value)
        assertEquals(DavidLiveVisualPhase.CONNECTION_LOST, controller.visualPhase.value)
        assertEquals(DavidLiveSpeaker.NONE, controller.audioEngine.activeSpeaker.value)
        assertTrue(controller.isWaitingForNetwork.value)
        assertEquals(
            "Связь с Давидом прервана",
            resolveDavidLiveStatusLabel(
                controller.connectionState.value,
                controller.audioEngine.activeSpeaker.value,
                controller.isMicMuted.value,
                controller.visualPhase.value
            )
        )

        // Частичная реплика Давида сохранена и помечена как прерванная (wasInterrupted = true)
        val lastDavidTurn = controller.transcriptTurns.last { it.speaker == DavidLiveSpeaker.DAVID }
        assertTrue("Interrupted turn must be flagged with wasInterrupted = true", lastDavidTurn.wasInterrupted)
        assertTrue(lastDavidTurn.text.contains("а во-вторых"))

        // 2. Появляется мобильная сеть (и дублирующий колбэк onNetworkAvailable подряд)
        controller.onNetworkAvailable()
        controller.onNetworkAvailable() // Дубль не должен открывать параллельный сокет!
        assertEquals("Duplicate onNetworkAvailable must not open parallel sockets", 2, fakeFactory.entries.size)
        assertEquals(DavidLiveConnectionState.RECONNECTING, controller.connectionState.value)
        assertEquals(DavidLiveVisualPhase.RECONNECTING, controller.visualPhase.value)
        assertFalse(controller.isWaitingForNetwork.value)

        // 3. Новая сессия на сокете #2 открывается: проверяем, что setup содержит контекст и прерванную реплику
        val socket2 = fakeFactory.entries[1]
        socket2.simulateOpen()
        assertEquals(1, socket2.sentMessages.size)
        val setupPayload = socket2.sentMessages[0]
        assertTrue("Setup message on reconnect must include conversation context", setupPayload.contains("КОНТЕКСТ ТЕКУЩЕГО РАЗГОВОРА"))
        assertTrue("Setup message on reconnect must include verified financial facts", setupPayload.contains("15000"))
        assertTrue("Setup message on reconnect must include interrupted partial thought", setupPayload.contains("а во-вторых"))

        // 4. После setupComplete контроллер автоматически отправляет одно сообщение clientContent для продолжения оборванной мысли
        socket2.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertEquals("After setupComplete on interrupted turn, continuation clientContent must be sent", 2, socket2.sentMessages.size)
        val continuationPayload = socket2.sentMessages[1]
        assertTrue(continuationPayload.contains("clientContent"))
        assertTrue(continuationPayload.contains("Связь прервалась"))
        assertTrue(continuationPayload.contains("а во-вторых"))

        // 5. Завершение сессии гарантированно снимает сетевую подписку и флаги ожидания
        controller.endSession()
        assertFalse(controller.isNetworkCallbackRegistered)
        assertFalse(controller.isWaitingForNetwork.value)
        assertEquals(DavidLiveDisplayMode.HIDDEN, controller.displayMode.value)
    }

    @Test
    fun `live HTTP client keep-alive configuration and regular voice input parser remain intact`() {
        val client = DavidLiveSessionController.defaultLiveHttpClient()
        assertEquals(40_000, client.pingIntervalMillis)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(0, client.readTimeoutMillis)
        assertTrue(client.retryOnConnectionFailure)

        // Regression check for regular voice input heuristic parser used on the main screen
        val ops = VoiceParserHelper.parseHeuristically(
            text = "Кофе 350 и такси 600",
            expenseCategories = listOf("Кафе и рестораны", "Транспорт", "Продукты", "Прочее"),
            incomeCategories = listOf("Зарплата")
        )
        assertEquals(2, ops.size)
        assertEquals(350.0, ops[0].amount, 0.01)
        assertEquals(600.0, ops[1].amount, 0.01)
    }

    private class FakeWebSocketEntry(
        val request: Request,
        val listener: WebSocketListener
    ) : WebSocket {
        val sentMessages = mutableListOf<String>()
        var simulatedQueueSize: Long = 0L
        var isClosedOrCancelled: Boolean = false

        override fun request(): Request = request
        override fun queueSize(): Long = simulatedQueueSize
        override fun send(text: String): Boolean {
            if (isClosedOrCancelled) return false
            sentMessages.add(text)
            return true
        }
        override fun send(bytes: ByteString): Boolean = !isClosedOrCancelled
        override fun close(code: Int, reason: String?): Boolean {
            isClosedOrCancelled = true
            return true
        }
        override fun cancel() {
            isClosedOrCancelled = true
        }

        fun simulateOpen() {
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("Switching Protocols")
                .build()
            listener.onOpen(this, response)
        }

        fun simulateServerText(json: String) {
            listener.onMessage(this, json)
        }

        fun simulateClosing(code: Int, reason: String) {
            listener.onClosing(this, code, reason)
        }

        fun simulateFailure(t: Throwable, response: Response? = null) {
            listener.onFailure(this, t, response)
        }
    }

    private class FakeWebSocketFactory : WebSocket.Factory {
        val entries = mutableListOf<FakeWebSocketEntry>()

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            val entry = FakeWebSocketEntry(request, listener)
            entries.add(entry)
            return entry
        }
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
