package com.example.davidapp.live

import com.example.data.api.GeminiApiService
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiModelConfig
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generates a concise, factual post-session summary after a David Live voice dialogue ends.
 *
 * Strict Grounding Rules:
 * - If the conversation is too short or contains no meaningful exchange, returns [SummaryOutcome.SkippedTooShort].
 * - Summarizes ONLY what was actually discussed in [transcriptTurns] and verified in [verifiedFacts].
 * - Never fabricates financial figures, categories, or action items.
 * - Supports safe error recovery so a failed summary call can be retried from the chat card.
 */
class DavidLiveSummaryGenerator(
    private val geminiApiService: GeminiApiService?
) {
    sealed class SummaryOutcome {
        data object SkippedTooShort : SummaryOutcome()
        data class Success(val summary: DavidLiveSummary) : SummaryOutcome()
        data class Error(val message: String) : SummaryOutcome()
    }

    /**
     * Checks whether the conversation has enough real substance to warrant generating a summary card.
     */
    fun hasEnoughSubstanceForSummary(
        turns: List<DavidLiveTranscriptTurn>,
        verifiedFacts: List<DavidLiveToolFact>
    ): Boolean {
        val meaningfulTurns = turns.filter { it.text.trim().length >= 4 }
        val totalWords = meaningfulTurns.sumOf {
            it.text.trim().split(Regex("\\s+")).count { w -> w.isNotBlank() }
        }
        return meaningfulTurns.size >= 2 || (meaningfulTurns.size >= 1 && totalWords >= 6) ||
            (meaningfulTurns.isNotEmpty() && verifiedFacts.isNotEmpty())
    }

    suspend fun generateSummary(
        userApiKey: String,
        turns: List<DavidLiveTranscriptTurn>,
        verifiedFacts: List<DavidLiveToolFact>
    ): SummaryOutcome {
        if (!hasEnoughSubstanceForSummary(turns, verifiedFacts)) {
            return SummaryOutcome.SkippedTooShort
        }

        if (userApiKey.isBlank()) {
            return SummaryOutcome.Error("Отсутствует пользовательский Gemini API ключ для генерации резюме.")
        }

        val service = geminiApiService
            ?: return SummaryOutcome.Success(buildDeterministicGroundedSummary(turns, verifiedFacts))

        val transcriptBlock = turns.joinToString("\n") { turn ->
            val role = when (turn.speaker) {
                DavidLiveSpeaker.USER -> "Пользователь"
                DavidLiveSpeaker.DAVID -> "Давид Жабов"
                DavidLiveSpeaker.NONE -> "Система"
            }
            "$role: ${turn.text.trim()}"
        }

        val factsBlock = if (verifiedFacts.isEmpty()) {
            "Финансовые инструменты во время звонка не запрашивались."
        } else {
            verifiedFacts.joinToString("\n") { "- [${it.toolName}]: ${it.summary}" }
        }

        val prompt = """
            Ты — финансовый аудитор Давид Жабов 🐸. Только что завершился голосовой диалог в режиме David Live.
            Сформируй краткое, структурированное и на 100% достоверное резюме разговора СТРОГО на основе приведённой стенограммы и проверенных фактов.

            КРИТИЧЕСКИЕ ПРАВИЛА ДОСТОВЕРНОСТИ:
            1. НЕ выдумывай суммы, категории, лимиты, цели или советы, которых НЕ БЫЛО в стенограмме или проверенных фактах.
            2. Если какие-то цифры не звучали и не запрашивались в фактах — НЕ упоминай их.
            3. Поле "nextActions" (Договорённости и следующие действия) заполняй ТОЛЬКО если они реально обсуждались. Если не обсуждались — верни пустой массив [].
            4. Ответ верни СТРОГО в формате валидного JSON без Markdown-обёрток:
            {
              "topics": ["краткая тема 1", "краткая тема 2"],
              "keyFindings": ["вывод только по реальным данным 1"],
              "recommendations": ["рекомендация Давида из диалога 1"],
              "nextActions": ["следующий шаг, если обсуждался"]
            }

            ПРОИЗНЕСЁННЫЕ РЕПЛИКИ ДИАЛОГА:
            $transcriptBlock

            ПРОВЕРЕННЫЕ ФИНАНСОВЫЕ ФАКТЫ (ИЗ БАЗЫ ДАННЫХ):
            $factsBlock
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )

        var lastException: Exception? = null
        for (modelName in GeminiModelConfig.BUDGET_AUDIT_MODELS) {
            try {
                val response = service.generateContent(
                    model = modelName,
                    apiKey = userApiKey.trim(),
                    request = request
                )
                val rawText = response.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull()?.text
                    ?.trim()
                if (!rawText.isNullOrBlank()) {
                    val parsed = parseSummaryJson(rawText, turns, verifiedFacts)
                    if (parsed != null) {
                        return SummaryOutcome.Success(parsed)
                    }
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        return SummaryOutcome.Error(
            lastException?.localizedMessage
                ?: "Не удалось сформировать резюме голосового разговора. Вы можете повторить попытку."
        )
    }

    internal fun parseSummaryJson(
        rawResponse: String,
        turns: List<DavidLiveTranscriptTurn>,
        verifiedFacts: List<DavidLiveToolFact>
    ): DavidLiveSummary? {
        val cleaned = rawResponse
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val jsonStart = cleaned.indexOf('{')
        val jsonEnd = cleaned.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null

        return try {
            val obj = JSONObject(cleaned.substring(jsonStart, jsonEnd + 1))
            val topics = obj.optJSONArray("topics").toStringList()
            val findings = obj.optJSONArray("keyFindings").toStringList()
            val recommendations = obj.optJSONArray("recommendations").toStringList()
            val nextActions = obj.optJSONArray("nextActions").toStringList()

            if (topics.isEmpty() && findings.isEmpty() && recommendations.isEmpty()) {
                buildDeterministicGroundedSummary(turns, verifiedFacts)
            } else {
                DavidLiveSummary(
                    topics = topics.ifEmpty { listOf("Голосовая консультация в David Live") },
                    keyFindings = findings,
                    recommendations = recommendations,
                    nextActions = nextActions
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds a strictly grounded summary directly from actual conversation turns and verified facts
     * without introducing any external numbers or unverified statements.
     */
    fun buildDeterministicGroundedSummary(
        turns: List<DavidLiveTranscriptTurn>,
        verifiedFacts: List<DavidLiveToolFact>
    ): DavidLiveSummary {
        val userTurns = turns.filter { it.speaker == DavidLiveSpeaker.USER }.map { it.text.trim() }
        val davidTurns = turns.filter { it.speaker == DavidLiveSpeaker.DAVID }.map { it.text.trim() }

        val topics = userTurns.take(3).map { text ->
            if (text.length > 90) text.take(87) + "..." else text
        }.ifEmpty {
            listOf("Анализ текущего бюджета в режиме David Live")
        }

        val keyFindings = verifiedFacts.map { it.summary }.take(4).ifEmpty {
            davidTurns.take(2).map { text ->
                if (text.length > 120) text.take(117) + "..." else text
            }
        }

        val recommendations = davidTurns.drop(if (verifiedFacts.isEmpty()) 1 else 0).take(3).map { text ->
            if (text.length > 120) text.take(117) + "..." else text
        }

        return DavidLiveSummary(
            topics = topics,
            keyFindings = keyFindings,
            recommendations = recommendations,
            nextActions = emptyList()
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val item = optString(i).trim()
            if (item.isNotEmpty()) list.add(item)
        }
        return list
    }
}
