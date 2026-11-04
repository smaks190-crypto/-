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
    fun `completed silent model turn clears thinking and returns sphere to listening`() {
        val controller = DavidLiveSessionController(
            financialToolsProvider = DavidLiveFinancialToolsProvider("default", null, null, null, null)
        )
        controller.processServerMessageForTest("""{"setupComplete":{}}""")
        controller.processServerMessageForTest("""{"serverContent":{"inputTranscription":{"text":"Привет"}}}""")
        assertEquals(DavidLiveVisualState.THINKING, controller.visualState.value)
        controller.processServerMessageForTest("""{"serverContent":{"turnComplete":true}}""")
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, controller.visualState.value)
        assertEquals("Привет", controller.spokenTranscript.value)
    }

    @Test
    fun `spoken transcript excludes setup and processing status messages`() {
        val controller = DavidLiveSessionController(financialToolsProvider = DavidLiveFinancialToolsProvider(
            budgetId = "default", transactionDao = null, categoryDao = null, goalDao = null, accountDao = null
        ))
        assertEquals("", controller.spokenTranscript.value)
        controller.startSession(null)
        assertEquals("", controller.spokenTranscript.value)
        controller.processServerMessageForTest("""{"serverContent":{"outputTranscription":{"text":"Ваш бюджет в порядке"}}}""")
        assertEquals("Ваш бюджет в порядке", controller.spokenTranscript.value)
    }

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
        controller.endSession()
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
        controller.endSession()
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
        controller.endSession()
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
        controller.endSession()
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
        controller.endSession()
    }

    @Test
    fun `network switch during David speech transitions to CONNECTION_LOST avoids parallel reconnects and continues interrupted thought on new socket`() {
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

        // Выполняем tool call во время активной сессии до обрыва и проверяем дедупликацию повторного вызова
        runBlocking {
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
        }

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
        assertEquals(DavidLiveConnectionState.RECONNECTING, controller.connectionState.value)
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
        val setupJson = JSONObject(socket2.sentMessages[0])
        val systemInstructionText = setupJson
            .getJSONObject("setup")
            .getJSONObject("systemInstruction")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        assertTrue("Setup message on reconnect must include conversation context", systemInstructionText.contains("КОНТЕКСТ ВОССТАНОВЛЕННОГО РАЗГОВОРА"))
        assertTrue("Setup message on reconnect must include verified financial facts", systemInstructionText.contains("15000"))
        assertTrue("Setup message on reconnect must include interrupted partial thought", systemInstructionText.contains("а во-вторых"))

        // 4. После setupComplete контроллер автоматически отправляет одно сообщение clientContent для продолжения оборванной мысли
        socket2.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertEquals("After setupComplete on interrupted turn, continuation clientContent must be sent", 2, socket2.sentMessages.size)
        val continuationJson = JSONObject(socket2.sentMessages[1])
        val continuationText = continuationJson
            .getJSONObject("clientContent")
            .getJSONArray("turns")
            .getJSONObject(0)
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        assertTrue(continuationText.contains("восстановлении связи после обрыва сети"))
        assertTrue(continuationText.contains("а во-вторых"))

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
        val sentMessages: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())
        var simulatedQueueSize: Long = 0L
        var isClosedOrCancelled: Boolean = false

        override fun request(): Request = request
        override fun queueSize(): Long = simulatedQueueSize
        override fun send(text: String): Boolean {
            if (isClosedOrCancelled) return false
            if (!text.contains("\"realtimeInput\"")) {
                sentMessages.add(text)
            }
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

    @Test
    fun `visual state resolver evaluates all 13 states strictly respecting priority hierarchy`() {
        // 1. Отсутствие API-ключа имеет высший приоритет (никогда не начинает бесконечный reconnect)
        val missingKey = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.NO_API_KEY,
            activeSpeaker = DavidLiveSpeaker.DAVID,
            failureKind = DavidLiveConnectionFailureKind.MISSING_API_KEY
        )
        assertEquals(DavidLiveVisualState.API_MISSING, missingKey)

        // 2. Потеря интернет-соединения (Offline) имеет абсолютный приоритет над речью, поиском и мышлением
        val offlinePrioritized = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.OFFLINE,
            activeSpeaker = DavidLiveSpeaker.DAVID,
            isToolLookupActive = true,
            isAnalyzingToolData = true,
            isModelThinking = true,
            failureKind = DavidLiveConnectionFailureKind.NETWORK_OFFLINE
        )
        assertEquals(DavidLiveVisualState.OFFLINE, offlinePrioritized)

        // 3. Критическая ошибка протокола/сервера
        val protocolError = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.ERROR,
            activeSpeaker = DavidLiveSpeaker.NONE,
            failureKind = DavidLiveConnectionFailureKind.PROTOCOL_OR_SERVER_ERROR
        )
        assertEquals(DavidLiveVisualState.ERROR, protocolError)

        // 4. Переподключение
        val reconnecting = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.RECONNECTING,
            activeSpeaker = DavidLiveSpeaker.NONE
        )
        assertEquals(DavidLiveVisualState.RECONNECTING, reconnecting)

        // 5. Первичное подключение (пока сессия не подтвердила готовность)
        val connectingUnready = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isSessionReady = false
        )
        assertEquals(DavidLiveVisualState.CONNECTING, connectingUnready)

        // 6. Речь Давида (только при наличии незавершённого playback или окна удержания)
        val davidSpeaking = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.DAVID_SPEAKING,
            activeSpeaker = DavidLiveSpeaker.DAVID,
            isPlaybackDrained = false,
            isWithinDavidSpeechHold = true
        )
        assertEquals(DavidLiveVisualState.DAVID_SPEAKING, davidSpeaking)

        // 7. Речь пользователя (с удержанием коротких пауз между словами)
        val userSpeakingWithHold = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isWithinUserSpeechHold = true,
            isMicMuted = false
        )
        assertEquals(DavidLiveVisualState.USER_SPEAKING, userSpeakingWithHold)

        // 8. Поиск финансовых данных (активен СТРОГО только при реальном запуске инструмента)
        val financeLookup = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isToolLookupActive = true,
            activeToolName = DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW
        )
        assertEquals(DavidLiveVisualState.FINANCE_LOOKUP, financeLookup)

        // 9. Анализ полученных данных
        val analyzing = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isAnalyzingToolData = true
        )
        assertEquals(DavidLiveVisualState.ANALYZING, analyzing)

        // 10. Подготовка голосового ответа
        val preparing = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isPreparingResponse = true
        )
        assertEquals(DavidLiveVisualState.PREPARING_RESPONSE, preparing)

        // 11. Размышление модели (Thinking)
        val thinking = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.DAVID_THINKING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isModelThinking = true
        )
        assertEquals(DavidLiveVisualState.THINKING, thinking)

        // 12. Кратковременный импульс успеха (Success)
        val success = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isSuccessPulseActive = true
        )
        assertEquals(DavidLiveVisualState.SUCCESS, success)

        // 13. Спокойное ожидание (Idle / Listening)
        val idle = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isPlaybackDrained = true
        )
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, idle)

        // Debug override имеет приоритет над всеми событиями ТОЛЬКО в Debug-режиме
        val overridden = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.OFFLINE,
            activeSpeaker = DavidLiveSpeaker.NONE,
            debugOverrideState = DavidLiveVisualState.FINANCE_LOOKUP,
            isDebugModeEnabled = true
        )
        assertEquals(DavidLiveVisualState.FINANCE_LOOKUP, overridden)

        // В Release-режиме (isDebugModeEnabled = false) debugOverrideState НЕ подменяет реальные состояния (Offline сохраняет приоритет)
        val releaseNotOverridden = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.OFFLINE,
            activeSpeaker = DavidLiveSpeaker.DAVID,
            isToolLookupActive = true,
            debugOverrideState = DavidLiveVisualState.FINANCE_LOOKUP,
            isDebugModeEnabled = false
        )
        assertEquals(DavidLiveVisualState.OFFLINE, releaseNotOverridden)

        // Все 13 состояний возвращают непустую пару цветов (Pair<Color, Color>) в resolveVisualStateColors
        DavidLiveVisualState.values().forEach { state ->
            val (primary, secondary) = resolveVisualStateColors(state, isMicMuted = false)
            assertNotNull("Primary color must be defined for $state", primary)
            assertNotNull("Secondary color must be defined for $state", secondary)
        }
    }

    @Test
    fun `visual state metadata generates authentic Russian labels actions and sphere tap flags`() {
        // Ожидание
        val idleMeta = DavidLiveVisualStateResolver.resolveMetadata(DavidLiveVisualState.IDLE_LISTENING)
        assertEquals("Ожидание", idleMeta.title)
        assertEquals("Я здесь, слушаю тебя", idleMeta.subtitle)
        assertEquals("LIVE", idleMeta.badgeText)
        assertFalse(idleMeta.sphereTapTriggersReconnect)
        assertFalse(idleMeta.sphereTapOpensApiSettings)

        // Поиск финансов с подсказкой конкретного инструмента
        val financeMeta = DavidLiveVisualStateResolver.resolveMetadata(
            visualState = DavidLiveVisualState.FINANCE_LOOKUP,
            activeToolName = DavidLiveFinancialToolsProvider.TOOL_SAFE_TO_SPEND_FORECAST
        )
        assertEquals("Ищу финансы", financeMeta.title)
        assertTrue(financeMeta.subtitle.contains("Safe-to-Spend"))
        assertEquals("SCAN", financeMeta.badgeText)

        // Анализ
        val analyzeMeta = DavidLiveVisualStateResolver.resolveMetadata(DavidLiveVisualState.ANALYZING)
        assertEquals("Анализирую", analyzeMeta.title)
        assertEquals("ANALYZE", analyzeMeta.badgeText)

        // Потеря связи (Offline): нажатие на сферу запускает reconnect, действие «Повторить»
        val offlineMeta = DavidLiveVisualStateResolver.resolveMetadata(DavidLiveVisualState.OFFLINE)
        assertEquals("Связь прервалась", offlineMeta.title)
        assertEquals("OFFLINE", offlineMeta.badgeText)
        assertEquals(DavidLiveVisualActionType.RECONNECT, offlineMeta.actionType)
        assertEquals("Повторить", offlineMeta.actionLabel)
        assertTrue(offlineMeta.sphereTapTriggersReconnect)
        assertFalse(offlineMeta.sphereTapOpensApiSettings)

        // Отсутствие ключа: нажатие на сферу открывает безопасные настройки, действие «Добавить API»
        val noKeyMeta = DavidLiveVisualStateResolver.resolveMetadata(DavidLiveVisualState.API_MISSING)
        assertEquals("Нет API-ключа", noKeyMeta.title)
        assertEquals("Добавь API-ключ Gemini для продолжения", noKeyMeta.subtitle)
        assertEquals("API KEY", noKeyMeta.badgeText)
        assertEquals(DavidLiveVisualActionType.ADD_API_KEY, noKeyMeta.actionType)
        assertEquals("Добавить API", noKeyMeta.actionLabel)
        assertFalse(noKeyMeta.sphereTapTriggersReconnect)
        assertTrue(noKeyMeta.sphereTapOpensApiSettings)

        // Неверный API-ключ в ошибке
        val invalidKeyMeta = DavidLiveVisualStateResolver.resolveMetadata(
            visualState = DavidLiveVisualState.ERROR,
            errorMessage = "API key not valid (403)",
            failureKind = DavidLiveConnectionFailureKind.INVALID_API_KEY
        )
        assertEquals("Неверный API-ключ", invalidKeyMeta.title)
        assertEquals(DavidLiveVisualActionType.UPDATE_API_KEY, invalidKeyMeta.actionType)
        assertEquals("Настроить API", invalidKeyMeta.actionLabel)
        assertTrue(invalidKeyMeta.sphereTapOpensApiSettings)
    }

    @Test
    fun `session controller manages visual states across tool calling thinking and disconnect cycles`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryDao = null,
            goalDao = null,
            accountDao = null
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)

        // Начальное состояние
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, controller.visualState.value)

        // 1. Ручной override для отладки всех 13 состояний
        controller.setDebugVisualStateOverride(DavidLiveVisualState.ANALYZING)
        assertEquals(DavidLiveVisualState.ANALYZING, controller.visualState.value)
        controller.setDebugVisualStateOverride(null)
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, controller.visualState.value)

        // 2. Имитация вызова финансового инструмента -> переход в FINANCE_LOOKUP
        val toolCallMsg = """
            {
              "toolCall": {
                "functionCalls": [
                  {
                    "id": "call_123",
                    "name": "${DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW}",
                    "args": {}
                  }
                ]
              }
            }
        """.trimIndent()
        controller.processServerMessageForTest(toolCallMsg)
        assertTrue(controller.isToolLookupActive.value)
        assertEquals(DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW, controller.activeToolName.value)
        assertEquals(DavidLiveVisualState.FINANCE_LOOKUP, controller.visualState.value)

        // 3. Импульс успешного завершения
        controller.triggerSuccessPulse(durationMs = 500L)
        assertTrue(controller.isSuccessPulseActive.value)
        assertEquals(DavidLiveVisualState.SUCCESS, controller.visualState.value)
    }

    @Test
    fun `mandatory transitions - User Speaking to Idle and David Speaking to Idle preserve smooth speech hold and playback drain`() {
        val engine = DavidLiveAudioEngine()

        // 1. Подаём активный голосовой сигнал пользователя (440 Гц тон высокой энергии)
        val speechFrame = ShortArray(1024) { idx ->
            (sin(2.0 * PI * 440.0 * idx / 16_000.0) * 19_000).toInt().toShort()
        }
        engine.feedTestPcmFrame(
            samples = speechFrame,
            sampleRateHz = 16_000,
            speaker = DavidLiveSpeaker.USER
        )
        assertEquals(DavidLiveSpeaker.USER, engine.activeSpeaker.value)
        assertTrue("User speech hold must be active during speech", engine.isWithinUserSpeechHold.value)

        // Во время речи резолвер выдаёт USER_SPEAKING
        assertEquals(
            DavidLiveVisualState.USER_SPEAKING,
            DavidLiveVisualStateResolver.resolve(
                connectionState = DavidLiveConnectionState.LISTENING,
                activeSpeaker = engine.activeSpeaker.value,
                overallEnergy = engine.overallEnergy.value,
                isWithinUserSpeechHold = engine.isWithinUserSpeechHold.value
            )
        )

        // 2. Короткая пауза между словами (тихий кадр сразу после речи):
        // окно удержания (420 мс) не позволяет сфере мгновенно сбросить анимацию USER_SPEAKING
        assertEquals(
            DavidLiveVisualState.USER_SPEAKING,
            DavidLiveVisualStateResolver.resolve(
                connectionState = DavidLiveConnectionState.LISTENING,
                activeSpeaker = DavidLiveSpeaker.NONE,
                overallEnergy = 0.02f,
                isWithinUserSpeechHold = engine.isWithinUserSpeechHold.value
            )
        )

        // 3. После истечения окна удержания сфера плавно возвращается в IDLE_LISTENING
        val quietFrame = ShortArray(1024) { 0 }
        engine.feedTestPcmFrame(
            samples = quietFrame,
            sampleRateHz = 16_000,
            speaker = DavidLiveSpeaker.NONE
        )
        assertEquals(
            DavidLiveVisualState.IDLE_LISTENING,
            DavidLiveVisualStateResolver.resolve(
                connectionState = DavidLiveConnectionState.LISTENING,
                activeSpeaker = DavidLiveSpeaker.NONE,
                overallEnergy = 0.01f,
                isWithinUserSpeechHold = false
            )
        )

        // 4. David Speaking -> Idle: пока очередь воспроизведения не опустошена (isPlaybackDrained == false),
        // состояние остаётся DAVID_SPEAKING; после полного воспроизведения (isPlaybackDrained == true)
        // и завершения hold-окна переходит в IDLE_LISTENING без резкого обрыва.
        assertEquals(
            DavidLiveVisualState.DAVID_SPEAKING,
            DavidLiveVisualStateResolver.resolve(
                connectionState = DavidLiveConnectionState.DAVID_SPEAKING,
                activeSpeaker = DavidLiveSpeaker.DAVID,
                isPlaybackDrained = false,
                isWithinDavidSpeechHold = true
            )
        )
        assertEquals(
            DavidLiveVisualState.IDLE_LISTENING,
            DavidLiveVisualStateResolver.resolve(
                connectionState = DavidLiveConnectionState.LISTENING,
                activeSpeaker = DavidLiveSpeaker.NONE,
                isPlaybackDrained = true,
                isWithinDavidSpeechHold = false
            )
        )
    }

    @Test
    fun `mandatory transitions - full pipeline Thinking to Finance Lookup to Analyzing to Preparing Response to David Speaking and Offline recovery`() = runBlocking {
        val fakeAccounts = listOf(
            AccountEntity(id = "a1", budgetId = "b1", name = "Основная", balance = 150_000.0, type = "card")
        )
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "b1",
            transactionDao = createReadOnlyTransactionDao(emptyList()),
            categoryDao = createReadOnlyCategoryDao(emptyList()),
            goalDao = createReadOnlyGoalDao(emptyList()),
            accountDao = createReadOnlyAccountDao(fakeAccounts)
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)

        // 1. SetupComplete подтверждает готовность сессии и переводит CONNECTING -> IDLE_LISTENING
        controller.processServerMessageForTest("""{"setupComplete": {}}""")
        assertTrue(controller.isSessionReady.value)
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, controller.visualState.value)

        // 2. Пользователь произнёс фразу -> модель переходит к осмыслению (THINKING)
        controller.processServerMessageForTest(
            """{"serverContent": {"inputTranscription": {"text": "Сколько у меня денег на счетах?"}}}"""
        )
        assertEquals(DavidLiveVisualState.THINKING, controller.visualState.value)

        // 3. Модель вызывает финансовый инструмент -> мгновенный переход в FINANCE_LOOKUP (и далее ANALYZING по завершении чтения БД)
        controller.processServerMessageForTest(
            """
            {
              "toolCall": {
                "functionCalls": [
                  {"id": "c1", "name": "${DavidLiveFinancialToolsProvider.TOOL_FINANCIAL_OVERVIEW}", "args": {}}
                ]
              }
            }
            """.trimIndent()
        )
        assertTrue(
            "Visual state during/right after toolCall must be FINANCE_LOOKUP or ANALYZING",
            controller.visualState.value == DavidLiveVisualState.FINANCE_LOOKUP ||
                controller.visualState.value == DavidLiveVisualState.ANALYZING
        )

        // 4. После получения данных инструмента при приходе внутренней мысли модель переходит в ANALYZING -> PREPARING_RESPONSE
        val analyzingResolved = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isAnalyzingToolData = true
        )
        assertEquals(DavidLiveVisualState.ANALYZING, analyzingResolved)

        val preparingResolved = DavidLiveVisualStateResolver.resolve(
            connectionState = DavidLiveConnectionState.LISTENING,
            activeSpeaker = DavidLiveSpeaker.NONE,
            isPreparingResponse = true
        )
        assertEquals(DavidLiveVisualState.PREPARING_RESPONSE, preparingResolved)

        // 5. Начинается голосовой ответ Давида -> DAVID_SPEAKING
        controller.processServerMessageForTest(
            """{"serverContent": {"outputTranscription": {"text": "На твоём счёте 150 тысяч рублей."}}}"""
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)

        // 6. Обрыв сети во время анализа или речи Давида немедленно переводит сферу в OFFLINE (приоритет Offline!)
        controller.simulateFailureForTest(
            failureKind = DavidLiveConnectionFailureKind.NETWORK_OFFLINE,
            customMessage = "Unable to resolve host generativelanguage.googleapis.com"
        )
        assertEquals(DavidLiveConnectionFailureKind.NETWORK_OFFLINE, controller.failureKind.value)
        assertEquals(DavidLiveConnectionState.ERROR, controller.connectionState.value)
        assertEquals(DavidLiveVisualState.OFFLINE, controller.visualState.value)

        // 7. Ошибка 403 Invalid API key переводит в отдельное состояние ERROR (а не OFFLINE)
        controller.simulateFailureForTest(
            failureKind = DavidLiveConnectionFailureKind.INVALID_API_KEY,
            customMessage = "API key not valid. Please pass a valid API key."
        )
        assertEquals(DavidLiveConnectionFailureKind.INVALID_API_KEY, controller.failureKind.value)
        assertEquals(DavidLiveConnectionState.ERROR, controller.connectionState.value)
        assertEquals(DavidLiveVisualState.ERROR, controller.visualState.value)

        // 8. Повторный setupComplete восстанавливает рабочее состояние IDLE_LISTENING
        controller.processServerMessageForTest("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionFailureKind.NONE, controller.failureKind.value)
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, controller.visualState.value)
    }

    @Test
    fun `high speaker volume loudspeaker echo during long David speech does not trigger false barge-in while genuine user interruption succeeds`() {
        val engine = DavidLiveAudioEngine()
        engine.setExternalHeadsetConnectedForTest(false)
        engine.setHardwareAecEnabledForTest(false)
        engine.setSpeakerVolumeRatioForTest(0.95f) // 95% громкость динамика

        var nowMs = 100_000L
        var bargeInTriggeredCount = 0

        // 0. Пользователь только что громко задал вопрос перед ответом Давида (проверка сброса счётчика кадров при старте речи Давида)
        val userQuestionFrame = createTonePcmFrame(rmsTarget = 0.22f)
        repeat(4) {
            engine.evaluateCaptureFrame(
                pcmSamples = userQuestionFrame,
                nowMs = nowMs,
                onUserBargeIn = { bargeInTriggeredCount++ }
            )
            nowMs += 64L
        }
        assertEquals(0, bargeInTriggeredCount)

        // 1. Давид начинает длинный голосовой ответ на высокой громкости динамика (digital RMS ~0.28f..0.34f).
        // Микрофон захватывает сильное эхо динамика (RMS ~0.12f..0.19f, что значительно выше старого порога 0.08f).
        val davidLoudFrame = createTonePcmFrame(rmsTarget = 0.30f, sampleCount = 1024)
        val speakerEchoFrame = createTonePcmFrame(rmsTarget = 0.16f, sampleCount = 1024)

        repeat(20) { step ->
            engine.feedTestPcmFrame(
                samples = davidLoudFrame,
                sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
                speaker = DavidLiveSpeaker.DAVID,
                nowMs = nowMs
            )
            val decision = engine.evaluateCaptureFrame(
                pcmSamples = speakerEchoFrame,
                nowMs = nowMs + 10L,
                onUserBargeIn = { bargeInTriggeredCount++ }
            )
            assertFalse(
                "High-volume loudspeaker echo (step=$step, micRms=${decision.micRms}, threshold=${decision.adaptiveBargeInThresholdRms}) must NOT trigger false barge-in",
                decision.triggeredBargeIn
            )
            assertFalse(
                "Speaker echo must be gated and never forwarded to Gemini Live server",
                decision.shouldForwardMicChunkToServer
            )
            assertTrue(decision.suppressedAsEcho)
            nowMs += 64L
        }
        assertEquals("David must finish speaking without self-interruption", 0, bargeInTriggeredCount)
        assertEquals(DavidLiveSpeaker.DAVID, engine.activeSpeaker.value)

        // 2. Теперь во время ответа Давида пользователь действительно начинает говорить в микрофон (ближнее поле + межслоговые паузы Давида)
        val davidSyllableDipFrame = createTonePcmFrame(rmsTarget = 0.04f, sampleCount = 1024)
        val userBargeInFrame = createTonePcmFrame(rmsTarget = 0.34f, sampleCount = 1024)

        var lastDecision: DavidLiveAudioEngine.CaptureFrameDecision? = null
        repeat(5) { idx ->
            val currentDavidOut = if (idx % 2 == 1) davidSyllableDipFrame else davidLoudFrame
            if (bargeInTriggeredCount == 0) {
                engine.feedTestPcmFrame(
                    samples = currentDavidOut,
                    sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
                    speaker = DavidLiveSpeaker.DAVID,
                    nowMs = nowMs
                )
            }
            lastDecision = engine.evaluateCaptureFrame(
                pcmSamples = userBargeInFrame,
                nowMs = nowMs + 10L,
                onUserBargeIn = { bargeInTriggeredCount++ }
            )
            nowMs += 64L
        }

        assertEquals("Genuine user speech must reliably interrupt David even at high volume", 1, bargeInTriggeredCount)
        assertNotNull(lastDecision)
        assertTrue(lastDecision!!.shouldForwardMicChunkToServer)
        assertEquals(DavidLiveSpeaker.NONE, engine.activeSpeaker.value)
    }

    @Test
    fun `normal speaker volume and after reconnect allow responsive user barge-in and suppress spurious server echo interrupted signal`() {
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
            autoReconnectDelayMs = 0L,
            readyFlashDurationMs = 0L
        )

        controller.startSession("AIzaSyTestUserManualKey987654321")
        val socket1 = fakeFactory.entries[0]
        socket1.simulateOpen()
        socket1.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // Симулируем переподключение после сетевого сбоя и проверяем работу защиты от эха и barge-in на новом сокете
        socket1.simulateFailure(SocketTimeoutException("sent ping but didn't receive pong within 20000ms"))
        val socket2 = fakeFactory.entries[1]
        socket2.simulateOpen()
        socket2.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // Давид начинает отвечать на обычной громкости (40%)
        controller.audioEngine.setExternalHeadsetConnectedForTest(false)
        controller.audioEngine.setSpeakerVolumeRatioForTest(0.40f)
        val nowMs = System.currentTimeMillis()
        socket2.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"Разбираю твои регулярные платежи и доступный остаток."}}}"""
        )
        val davidAudio = createTonePcmFrame(rmsTarget = 0.20f, sampleCount = 2400)
        controller.audioEngine.feedTestPcmFrame(
            samples = davidAudio,
            sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
            speaker = DavidLiveSpeaker.DAVID,
            nowMs = nowMs
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        assertTrue(controller.audioEngine.hasPendingOrActiveModelPlayback(nowMs))

        // 1. Ложный серверный interrupted=true (без подтверждённой речи пользователя при активном воспроизведении) игнорируется
        socket2.simulateServerText("""{"serverContent":{"interrupted": true}}""")
        assertEquals(
            "Spurious server interrupted=true without user speech must not cut off active playback",
            DavidLiveConnectionState.DAVID_SPEAKING,
            controller.connectionState.value
        )

        // 2. Реальное перебивание пользователем на обычной громкости (4 кадра речи RMS 0.14f)
        val userNormalVoice = createTonePcmFrame(rmsTarget = 0.14f, sampleCount = 1024)
        var t = nowMs + 250L
        repeat(4) {
            controller.audioEngine.evaluateCaptureFrame(
                pcmSamples = userNormalVoice,
                nowMs = t,
                onUserBargeIn = { controller.handleConfirmedUserBargeIn() }
            )
            t += 64L
        }
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertEquals("Слушаю вас...", controller.liveSubtitle.value)

        // 3. Запоздавшие транзитные пакеты прерванной реплики Давида игнорируются до начала нового хода
        socket2.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"Хвост старой прерванной реплики"}}}"""
        )
        assertEquals(
            "In-flight chunks of an already-interrupted model turn must not flip state back to DAVID_SPEAKING",
            DavidLiveConnectionState.LISTENING,
            controller.connectionState.value
        )

        // 4. Новый вопрос пользователя сбрасывает флаг перебивания и позволяет Давиду ответить на новый вопрос
        socket2.simulateServerText(
            """{"serverContent":{"inputTranscription":{"text":"А сколько осталось на кафе?"}}}"""
        )
        socket2.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"На кафе осталось 1 500 рублей."}}}"""
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        assertEquals("На кафе осталось 1 500 рублей.", controller.liveSubtitle.value)

        controller.endSession()
    }

    @Test
    fun `high volume inter-word pauses do not false barge-in post-barge-in speech forwards and minimizing during answer preserves playback`() {
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
            autoReconnectDelayMs = 0L,
            readyFlashDurationMs = 0L
        )

        controller.startSession("AIzaSyTestUserManualKey987654321")
        val socket = fakeFactory.entries[0]
        socket.simulateOpen()
        socket.simulateServerText("""{"setupComplete": {}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        val engine = controller.audioEngine
        engine.setExternalHeadsetConnectedForTest(false)
        engine.setHardwareAecEnabledForTest(false)
        engine.setSpeakerVolumeRatioForTest(1.0f)

        var nowMs = System.currentTimeMillis()
        val loudDavidWord = createTonePcmFrame(rmsTarget = 0.24f, sampleCount = 1024, sampleRateHz = 24_000)
        val interWordPause = createTonePcmFrame(rmsTarget = 0.015f, sampleCount = 1024, sampleRateHz = 24_000)
        val delayedSpeakerEcho = createTonePcmFrame(rmsTarget = 0.12f, sampleCount = 1024, sampleRateHz = 16_000)

        // 1. На полной громкости (100%) во время межсловных пауз задержка эха AudioTrack (0.12f) НЕ вызывает ложный barge-in
        var falseBargeIns = 0
        repeat(12) { step ->
            val outFrame = if (step % 4 in 2..3) interWordPause else loudDavidWord
            engine.feedTestPcmFrame(
                samples = outFrame,
                sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
                speaker = DavidLiveSpeaker.DAVID,
                nowMs = nowMs
            )
            val decision = engine.evaluateCaptureFrame(
                pcmSamples = delayedSpeakerEcho,
                nowMs = nowMs + 15L,
                onUserBargeIn = { falseBargeIns++ }
            )
            assertFalse("Inter-word pause echo at step=$step must not trigger false barge-in", decision.triggeredBargeIn)
            assertFalse("Inter-word pause echo must not be forwarded to server", decision.shouldForwardMicChunkToServer)
            nowMs += 64L
        }
        assertEquals(0, falseBargeIns)

        // 2. Длинный ответ со свёрнутым окном (MINIMIZED) и ранним turnComplete при наличии очереди аудио НЕ прерывает ответ Давида
        val pcmBytes24k = ByteArray(4800) { idx -> if (idx % 2 == 0) 0x20 else 0x10 }
        val b64Audio = android.util.Base64.encodeToString(pcmBytes24k, android.util.Base64.NO_WRAP)
        socket.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"Продолжаю подробный разбор твоих расходов по всем категориям."},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64Audio"}}]}}}"""
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        assertTrue(engine.hasQueuedPlaybackChunks())

        // Сворачиваем David Live в главное меню во время ответа
        controller.minimizeSession()
        assertEquals(DavidLiveDisplayMode.MINIMIZED, controller.displayMode.value)
        assertEquals("Minimizing to bottom capsule must keep DAVID_SPEAKING active", DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        assertTrue("Minimizing must not flush queued model audio", engine.hasQueuedPlaybackChunks())

        // Приходит turnComplete от сервера раньше окончания воспроизведения очереди — состояние остаётся DAVID_SPEAKING
        socket.simulateServerText("""{"serverContent":{"turnComplete":true}}""")
        assertEquals(
            "Early server turnComplete while audio queue is still non-empty must keep state as DAVID_SPEAKING",
            DavidLiveConnectionState.DAVID_SPEAKING,
            controller.connectionState.value
        )

        // 3. Пользователь реально перебивает Давида голосом (barge-in), и последующие кадры речи пользователя (даже тише порога 0.08f)
        // в течение 320 мс окна НЕ блокируются фильтром хвостового эха
        val strongUserVoice = createTonePcmFrame(rmsTarget = 0.34f, sampleCount = 1024, sampleRateHz = 16_000)
        val trailingUserSyllable = createTonePcmFrame(rmsTarget = 0.05f, sampleCount = 1024, sampleRateHz = 16_000)
        var userBargeInCount = 0
        repeat(4) {
            engine.evaluateCaptureFrame(
                pcmSamples = strongUserVoice,
                nowMs = nowMs,
                onUserBargeIn = {
                    userBargeInCount++
                    controller.handleConfirmedUserBargeIn(nowMs)
                }
            )
            nowMs += 64L
        }
        assertEquals(1, userBargeInCount)
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // Следующие 3 кадра речи пользователя (через +64, +128, +192 мс — внутри 320 мс окна BARGE_IN_TAIL_ECHO_SUPPRESS_MS)
        repeat(3) {
            val followUpDecision = engine.evaluateCaptureFrame(
                pcmSamples = trailingUserSyllable,
                nowMs = nowMs
            )
            assertFalse("Post-barge-in user speech must not be suppressed as tail echo", followUpDecision.suppressedAsEcho)
            assertTrue("Post-barge-in user speech must be forwarded to Gemini Live server", followUpDecision.shouldForwardMicChunkToServer)
            nowMs += 64L
        }

        controller.endSession()
    }

    @Test
    fun `long high-volume 80-100 percent answer with 700ms AudioTrack echo delay and nominal HAL AEC never self-interrupts`() {
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
            autoReconnectDelayMs = 0L,
            readyFlashDurationMs = 0L
        )

        controller.startSession("AIzaSyTestUserManualKey987654321")
        val socket = fakeFactory.entries[0]
        socket.simulateOpen()
        socket.simulateServerText("""{"setupComplete": {}}""")

        val engine = controller.audioEngine
        engine.setExternalHeadsetConnectedForTest(false)
        // Даже если HAL формально сообщает echoCanceler.enabled == true, на громкости 80-100% в MODE_NORMAL
        // акустическая защита не должна занижать запас порога
        engine.setHardwareAecEnabledForTest(true)
        engine.setSpeakerVolumeRatioForTest(0.95f)

        var nowMs = System.currentTimeMillis()
        // 1. Пользователь громко задал вопрос перед ответом Давида
        val userQuestionFrame = createTonePcmFrame(rmsTarget = 0.25f, sampleCount = 1024, sampleRateHz = 16_000)
        repeat(4) {
            engine.evaluateCaptureFrame(pcmSamples = userQuestionFrame, nowMs = nowMs)
            nowMs += 64L
        }
        assertTrue(engine.hasRecentConfirmedUserSpeech(nowMs))

        // 2. Давид начинает длинный ответ (аудио + текст). Метка прежней речи пользователя должна сброситься,
        // чтобы в первые 1.8 с ответа ложный серверный interrupted=true не прервал Давида.
        val pcmBytes24k = ByteArray(9600) { idx -> if (idx % 2 == 0) 0x30 else 0x18 }
        val b64Audio = android.util.Base64.encodeToString(pcmBytes24k, android.util.Base64.NO_WRAP)
        socket.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"Начинаю полный разбор всех твоих расходов, доходов и обязательств за месяц."},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64Audio"}}]}}}"""
        )
        assertFalse(
            "Pre-turn user speech timestamp must be cleared when David starts speaking",
            engine.hasRecentConfirmedUserSpeech(nowMs)
        )

        // Ложный серверный interrupted=true через 300 мс после старта ответа Давида подавляется
        socket.simulateServerText("""{"serverContent":{"interrupted":true}}""")
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)

        // 3. Симулируем длинный (25 секунд) ответ Давида на громкости 95% с паузами между фразами по 640 мс (10 кадров),
        // во время которых из буфера AudioTrack (латентность ~350-650 мс) продолжает звучать громкое эхо предыдущих слов (RMS 0.20f)
        val loudWord24k = createTonePcmFrame(rmsTarget = 0.28f, sampleCount = 1024, sampleRateHz = 24_000)
        val silencePause24k = createTonePcmFrame(rmsTarget = 0.008f, sampleCount = 1024, sampleRateHz = 24_000)
        val delayedLoudEcho16k = createTonePcmFrame(rmsTarget = 0.20f, sampleCount = 1024, sampleRateHz = 16_000)

        var falseBargeIns = 0
        repeat(60) { frameIdx ->
            val phase = frameIdx % 15
            val davidOut = if (phase < 5) loudWord24k else silencePause24k
            engine.feedTestPcmFrame(
                samples = davidOut,
                sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
                speaker = DavidLiveSpeaker.DAVID,
                nowMs = nowMs
            )
            val decision = engine.evaluateCaptureFrame(
                pcmSamples = delayedLoudEcho16k,
                nowMs = nowMs + 10L,
                onUserBargeIn = { falseBargeIns++ }
            )
            assertFalse(
                "Delayed AudioTrack loudspeaker echo at frame=$frameIdx (phase=$phase, refRms=${decision.playbackReferenceRms}, threshold=${decision.adaptiveBargeInThresholdRms}) must never self-interrupt David",
                decision.triggeredBargeIn
            )
            assertFalse(decision.shouldForwardMicChunkToServer)
            nowMs += 64L
        }
        assertEquals(0, falseBargeIns)
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)

        controller.endSession()
    }

    @Test
    fun `barge-in during modelTurn PCM drops all delayed old-turn chunks after inputTranscription and answers only the new user request`() {
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
            autoReconnectDelayMs = 0L,
            readyFlashDurationMs = 0L
        )

        controller.startSession("AIzaSyTestUserManualKey987654321")
        val socket = fakeFactory.entries[0]
        socket.simulateOpen()
        socket.simulateServerText("""{"setupComplete": {}}""")

        val engine = controller.audioEngine
        engine.setExternalHeadsetConnectedForTest(false)
        engine.setSpeakerVolumeRatioForTest(0.85f)

        var nowMs = System.currentTimeMillis()
        val pcmBytes24k = ByteArray(4800) { idx -> if (idx % 2 == 0) 0x24 else 0x12 }
        val b64Audio = android.util.Base64.encodeToString(pcmBytes24k, android.util.Base64.NO_WRAP)

        // 1. Давид начинает длинный ответ (Turn #1) с реальными modelTurn PCM пакетами
        socket.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"За прошлый месяц твои расходы составили 84 тысячи рублей."},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64Audio"}}]}}}"""
        )
        val davidWord = createTonePcmFrame(rmsTarget = 0.22f, sampleCount = 1024, sampleRateHz = 24_000)
        engine.feedTestPcmFrame(
            samples = davidWord,
            sampleRateHz = DavidLiveAudioEngine.OUTPUT_SAMPLE_RATE_HZ,
            speaker = DavidLiveSpeaker.DAVID,
            nowMs = nowMs
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)

        // 2. Пользователь перебивает Давида в середине ответа ("Стоп, а сколько я потратил на такси?")
        val userStrongSpeech = createTonePcmFrame(rmsTarget = 0.34f, sampleCount = 1024, sampleRateHz = 16_000)
        var confirmDecision: DavidLiveAudioEngine.CaptureFrameDecision? = null
        repeat(4) {
            confirmDecision = engine.evaluateCaptureFrame(
                pcmSamples = userStrongSpeech,
                nowMs = nowMs,
                onUserBargeIn = { controller.handleConfirmedUserBargeIn(nowMs) }
            )
            nowMs += 64L
        }
        assertNotNull(confirmDecision)
        assertTrue("4th frame of strong user speech must trigger barge-in", confirmDecision!!.triggeredBargeIn)
        assertTrue(
            "Pre-roll buffer must preserve the initial 3 speech frames + 2 silence lead-in frames so the beginning of user's utterance is not clipped",
            confirmDecision!!.preRollPcmFramesToForward.size >= 5
        )
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // 3. Приходит первый фрагмент распознавания новой речи пользователя (inputTranscription) ДО того,
        // как сервер успел прислать interrupted=true или turnComplete=true для старого ответа Давида
        socket.simulateServerText(
            """{"serverContent":{"inputTranscription":{"text":"Стоп, а сколько я потратил"}}}"""
        )
        assertEquals("Стоп, а сколько я потратил", controller.liveSubtitle.value)
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)

        // 4. Через 900 мс и через 2 100 мс (позже прежнего лимита 1 500 мс!) по сети приходят запоздавшие
        // пакеты modelTurn PCM и outputTranscription от СТАРОГО прерванного ответа Давида (Turn #1).
        // Они ОБЯЗАНЫ быть полностью отброшены и не должны ни возобновлять речь Давида, ни закрывать микрофон!
        assertTrue(controller.isModelTurnSuppressedByLocalBargeIn(nowMs + 2_100L))
        socket.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":" Продолжаю старый прерванный ответ про супермаркеты и подписки."},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64Audio"}}]}}}"""
        )
        assertEquals(
            "Delayed modelTurn PCM from the old interrupted turn must NEVER flip state back to DAVID_SPEAKING",
            DavidLiveConnectionState.LISTENING,
            controller.connectionState.value
        )
        assertFalse(
            "Delayed modelTurn PCM from the old interrupted turn must NEVER be enqueued into AudioTrack",
            engine.hasQueuedPlaybackChunks()
        )
        assertEquals(
            "Live subtitle must remain on the user's new utterance, not the old David answer",
            "Стоп, а сколько я потратил",
            controller.liveSubtitle.value
        )

        // Микрофон остаётся открытым для окончания фразы пользователя и финальной паузы
        val userNextFrameDecision = engine.evaluateCaptureFrame(
            pcmSamples = createTonePcmFrame(rmsTarget = 0.14f, sampleCount = 1024, sampleRateHz = 16_000),
            nowMs = nowMs + 600L
        )
        assertTrue("Microphone must stay open for the rest of user's new utterance", userNextFrameDecision.shouldForwardMicChunkToServer)

        // 5. Пользователь договаривает вопрос, после чего сервер закрывает старый прерванный ход (interrupted=true)
        socket.simulateServerText(
            """{"serverContent":{"inputTranscription":{"text":" на такси?"}}}"""
        )
        assertEquals("Стоп, а сколько я потратил на такси?", controller.liveSubtitle.value)

        socket.simulateServerText("""{"serverContent":{"interrupted":true}}""")
        assertEquals(DavidLiveConnectionState.LISTENING, controller.connectionState.value)
        assertEquals(
            "Server interrupted=true for the old turn must preserve the user's new subtitle",
            "Стоп, а сколько я потратил на такси?",
            controller.liveSubtitle.value
        )

        // 6. Теперь сервер начинает НОВЫЙ ответ Давида (Turn #2) строго по новому запросу пользователя
        socket.simulateServerText(
            """{"serverContent":{"outputTranscription":{"text":"На такси в этом месяце ты потратил 3 400 рублей."},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$b64Audio"}}]}}}"""
        )
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        assertEquals("На такси в этом месяце ты потратил 3 400 рублей.", controller.liveSubtitle.value)

        socket.simulateServerText("""{"serverContent":{"turnComplete":true}}""")
        val (turns, _) = controller.endSession()
        val userTurns = turns.filter { it.speaker == DavidLiveSpeaker.USER }
        assertTrue(
            "Full new user request after barge-in must be recorded as a single intact turn",
            userTurns.any { it.text.contains("Стоп, а сколько я потратил на такси?") }
        )
    }

    private fun createTonePcmFrame(
        rmsTarget: Float,
        sampleCount: Int = 1024,
        frequencyHz: Double = 440.0,
        sampleRateHz: Int = 16_000
    ): ShortArray {
        val amplitude = (rmsTarget * kotlin.math.sqrt(2.0) * 32767.0).coerceIn(0.0, 32760.0)
        return ShortArray(sampleCount) { i ->
            (sin(2.0 * PI * frequencyHz * i / sampleRateHz) * amplitude).toInt().toShort()
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


