package com.example.davidapp.live

import com.example.utils.VoiceInputManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DavidLiveUnitTest {

    @Test
    fun `isInternalThoughtPart detects explicit thought flag and signature`() {
        val explicitThought = JSONObject("""{"text": "Thinking about user budget...", "thought": true}""")
        assertTrue(DavidLiveSessionController.isInternalThoughtPart(explicitThought))

        val withSignature = JSONObject("""{"text": "Какой-то текст", "thoughtSignature": "sig_123"}""")
        assertTrue(DavidLiveSessionController.isInternalThoughtPart(withSignature))
    }

    @Test
    fun `isLikelyEnglishThoughtText filters English CoT headers and reasoning paragraphs`() {
        val cotHeader = "**Crafting The Greeting**\n\nI'm working on the appropriate response, focusing on a lively and slightly playful tone."
        assertTrue(DavidLiveSessionController.isLikelyEnglishThoughtText(cotHeader))

        val cotReasoning = "I need to call get_financial_overview first to check the user's current balance before answering."
        assertTrue(DavidLiveSessionController.isLikelyEnglishThoughtText(cotReasoning))
    }

    @Test
    fun `isLikelyEnglishThoughtText preserves Russian spoken responses`() {
        val russianResponse = "Привет! Твой текущий баланс составляет 45 200 рублей, а главная категория расходов — Еда."
        assertFalse(DavidLiveSessionController.isLikelyEnglishThoughtText(russianResponse))

        val russianWithEnglishBrand = "Твоя подписка на YouTube Premium и Spotify обошлась в 1 200 рублей за этот месяц."
        assertFalse(DavidLiveSessionController.isLikelyEnglishThoughtText(russianWithEnglishBrand))
    }

    @Test
    fun `summary generator builds structured fallback summary from turns and facts`() {
        val turns = listOf(
            DavidLiveTranscriptTurn(DavidLiveSpeaker.USER, "Сколько я потратил на еду в этом месяце?"),
            DavidLiveTranscriptTurn(DavidLiveSpeaker.DAVID, "На категорию Еда ушло 18 500 рублей, это 34% всех твоих расходов.")
        )
        val facts = listOf(
            DavidLiveFactCard(
                title = "Категория: Еда",
                value = "18500 ₽",
                subtitle = "Лимит: 20000 ₽",
                isPositive = true
            )
        )

        assertTrue(DavidLiveSummaryGenerator.shouldGenerateSummary(turns, facts))
        val summary = DavidLiveSummaryGenerator.buildLocalFallbackSummary(turns, facts)

        assertTrue(summary.contains("Итоги голосового сеанса David Live"))
        assertTrue(summary.contains("Категория: Еда"))
        assertTrue(summary.contains("18500 ₽"))
        assertTrue(summary.contains("Сколько я потратил на еду"))
    }

    @Test
    fun `session controller filters out English model thought blocks and displays clean Russian outputTranscription subtitles`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryLimitDao = null,
            savingGoalDao = null,
            categoryDao = null
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
    fun `voice input live transcription setup requests text-only inputAudioTranscription without tools or voice output`() {
        val setup = VoiceInputManager.buildLiveTranscriptionSetupJson("models/gemini-2.0-flash-live-001")
        val setupObj = setup.getJSONObject("setup")
        assertEquals("models/gemini-2.0-flash-live-001", setupObj.getString("model"))
        assertTrue(setupObj.has("inputAudioTranscription"))
        assertFalse(setupObj.has("outputAudioTranscription"))
        assertFalse(setupObj.has("tools"))
        val modalities = setupObj.getJSONObject("generationConfig").getJSONArray("responseModalities")
        assertEquals(1, modalities.length())
        assertEquals("TEXT", modalities.getString(0))
    }

    @Test
    fun `endSession returns non-empty transcript turns and triggers DavidViewModel summary flow condition`() {
        val provider = DavidLiveFinancialToolsProvider(
            budgetId = "default",
            transactionDao = null,
            categoryLimitDao = null,
            savingGoalDao = null,
            categoryDao = null
        )
        val controller = DavidLiveSessionController(financialToolsProvider = provider)

        controller.processServerMessageForTest(
            """{"serverContent":{"inputTranscription":{"text":"Привет, Давид! Сколько я потратил за неделю?"}}}"""
        )
        controller.processServerMessageForTest(
            """{"serverContent":{"outputTranscription":{"text":"За последнюю неделю ты потратил 14 200 рублей."},"turnComplete":true}}"""
        )

        val (turns, facts) = controller.endSession()
        assertEquals(2, turns.size)
        assertEquals(DavidLiveSpeaker.USER, turns[0].speaker)
        assertEquals("Привет, Давид! Сколько я потратил за неделю?", turns[0].text)
        assertEquals(DavidLiveSpeaker.DAVID, turns[1].speaker)
        assertEquals("За последнюю неделю ты потратил 14 200 рублей.", turns[1].text)
        assertTrue(DavidLiveSummaryGenerator.shouldGenerateSummary(turns, facts))
    }
}
