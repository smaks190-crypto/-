package com.example.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.example.data.repository.ParsedVoiceOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Описание точечного исправления последней распознанной голосовой операции на лету
 * (инструмент `correct_last_voice_operation` в потоке `BidiGenerateContent`).
 */
data class VoiceOperationCorrection(
    val amount: Double? = null,
    val title: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val type: String? = null,
    val date: String? = null
) {
    fun hasAnyField(): Boolean {
        return (amount != null && amount > 0.0) ||
            !title.isNullOrBlank() ||
            !category.isNullOrBlank() ||
            !subcategory.isNullOrBlank() ||
            !type.isNullOrBlank() ||
            !date.isNullOrBlank()
    }
}

/**
 * Потоковый менеджер голосового ввода в стиле Gemini Live (`BidiGenerateContent`).
 *
 * Работает как тихий потоковый рекордер-транскрибатор в едином WebSocket-соединении:
 * - Непрерывно считывает PCM-аудио (16 kHz, 16-bit, Mono) через AudioRecord без системных звуков
 *   и без создания временных WAV-файлов.
 * - Стримит PCM-фреймы напрямую по WebSocket в `BidiGenerateContent` и в реальном времени получает:
 *   1) `inputTranscription` — живой текст речи пользователя (`partialText` / `recognizedText`);
 *   2) `toolCall` (`record_voice_operations`) — структурированные операции (сумма, категория,
 *      подкатегория, тип, дата), выделенные моделью прямо на лету во время речи без единого REST POST запроса;
 *   3) `toolCall` (`correct_last_voice_operation`) — мгновенное исправление последней карточки,
 *      если пользователь оговорился и поправил себя на лету («а нет, булочка 150»).
 * - Не воспроизводит голос ассистента и не отправляет WAV/текстовые чанки через `generateContent`.
 */
class VoiceInputManager(context: Context) {
    private val context: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    var onErrorCallback: (() -> Unit)? = null
    var onChunkRecognized: ((String) -> Unit)? = null
    var onOperationsCaptured: ((List<ParsedVoiceOperation>) -> Unit)? = null
    var onLastOperationCorrected: ((VoiceOperationCorrection) -> Unit)? = null

    // --- Gemini Live WebSocket BidiGenerateContent State ---
    private val liveHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var liveWebSocket: WebSocket? = null
    private val liveSetupCompleted = AtomicBoolean(false)
    private val liveIntentionalClose = AtomicBoolean(false)
    private var liveModelAttemptIndex = 0
    private var currentLiveApiKey: String = ""
    private var currentExpenseCategories: List<String> = emptyList()
    private var currentIncomeCategories: List<String> = emptyList()
    private val pendingLiveTurnTranscript = StringBuilder()
    private val fullSessionTranscript = StringBuilder()
    private val preSetupPcmQueue = ArrayDeque<String>(64)

    @Volatile
    private var liveTurnOrToolLatch: CountDownLatch? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SAMPLES = 512 // 32 мс при 16000 Гц
        private const val FRAME_BYTES = FRAME_SAMPLES * 2 // 1024 байта (16-bit PCM)
        private const val SPEECH_RMS_THRESHOLD = 360.0 // Порог чувствительности голоса (16-bit PCM)

        private const val GEMINI_LIVE_WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        const val LISTENING_PLACEHOLDER = "Говорите..."
        const val TOOL_RECORD_VOICE_OPERATIONS = "record_voice_operations"
        const val TOOL_CORRECT_LAST_VOICE_OPERATION = "correct_last_voice_operation"

        val LIVE_STT_MODELS = listOf(
            "models/gemini-2.5-flash-native-audio-preview-12-2025",
            "models/gemini-2.0-flash-live-001",
            "models/gemini-2.0-flash-exp"
        )

        /**
         * Декларация инструментов `record_voice_operations` и `correct_last_voice_operation` для `BidiGenerateContent`:
         * позволяет модели в реальном времени фиксировать каждую названную трату/доход и исправлять
         * последнюю операцию на лету при самоисправлении пользователя без HTTP POST запросов.
         */
        internal fun buildVoiceOperationsFunctionDeclarations(): JSONArray {
            val itemProperties = JSONObject().apply {
                put(
                    "title",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "Краткое название операции или покупки на русском языке (например: Кофе, Такси, Зарплата).")
                )
                put(
                    "type",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "Тип операции: строго 'expense' (расход) или 'income' (доход).")
                )
                put(
                    "amount",
                    JSONObject()
                        .put("type", "NUMBER")
                        .put("description", "Числовая сумма операции в рублях (> 0).")
                )
                put(
                    "category",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "Точное название наиболее подходящей категории из списка пользователя.")
                )
                put(
                    "subcategory",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "Конкретное уточнение/подкатегория (например: Капучино, Яндекс Такси, Аванс).")
                )
                put(
                    "date",
                    JSONObject()
                        .put("type", "STRING")
                        .put("description", "Дата операции в формате YYYY-MM-DD.")
                )
            }

            val recordParametersSchema = JSONObject().apply {
                put("type", "OBJECT")
                put(
                    "properties",
                    JSONObject().put(
                        "operations",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("description", "Список распознанных финансовых операций из произнесённой фразы.")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put("properties", itemProperties)
                                    put(
                                        "required",
                                        JSONArray()
                                            .put("title")
                                            .put("type")
                                            .put("amount")
                                            .put("category")
                                            .put("subcategory")
                                    )
                                }
                            )
                        }
                    )
                )
                put("required", JSONArray().put("operations"))
            }

            val correctParametersSchema = JSONObject().apply {
                put("type", "OBJECT")
                put(
                    "properties",
                    JSONObject().apply {
                        put(
                            "amount",
                            JSONObject()
                                .put("type", "NUMBER")
                                .put("description", "Исправленная сумма последней операции в рублях (> 0).")
                        )
                        put(
                            "title",
                            JSONObject()
                                .put("type", "STRING")
                                .put("description", "Исправленное название последней операции.")
                        )
                        put(
                            "category",
                            JSONObject()
                                .put("type", "STRING")
                                .put("description", "Исправленная категория последней операции.")
                        )
                        put(
                            "subcategory",
                            JSONObject()
                                .put("type", "STRING")
                                .put("description", "Исправленная подкатегория последней операции.")
                        )
                        put(
                            "type",
                            JSONObject()
                                .put("type", "STRING")
                                .put("description", "Исправленный тип последней операции: 'expense' или 'income'.")
                        )
                        put(
                            "date",
                            JSONObject()
                                .put("type", "STRING")
                                .put("description", "Исправленная дата последней операции в формате YYYY-MM-DD.")
                        )
                    }
                )
            }

            return JSONArray()
                .put(
                    JSONObject().apply {
                        put("name", TOOL_RECORD_VOICE_OPERATIONS)
                        put(
                            "description",
                            "Записывает распознанные из речи пользователя финансовые операции (расходы и доходы). " +
                                "Вызывай этот инструмент сразу, как только пользователь назвал сумму и назначение траты или дохода."
                        )
                        put("parameters", recordParametersSchema)
                    }
                )
                .put(
                    JSONObject().apply {
                        put("name", TOOL_CORRECT_LAST_VOICE_OPERATION)
                        put(
                            "description",
                            "Исправляет последнюю ранее записанную финансовую операцию, если пользователь оговорился " +
                                "или поправил себя на лету (например: 'а нет, булочка 150', 'ой, не 300, а 250', 'точнее категория Продукты')."
                        )
                        put("parameters", correctParametersSchema)
                    }
                )
        }

        /**
         * Конфигурация сессии Gemini Live (`BidiGenerateContent`) для живого распознавания речи
         * и потокового выделения финансовых операций через `toolCall` без голосовых ответов ИИ.
         */
        internal fun buildLiveTranscriptionSetupJson(
            modelName: String,
            expenseCategories: List<String> = emptyList(),
            incomeCategories: List<String> = emptyList()
        ): JSONObject {
            val isNativeAudio = modelName.contains("native-audio", ignoreCase = true)
            val modality = if (isNativeAudio) "AUDIO" else "TEXT"
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val expCatsStr = if (expenseCategories.isNotEmpty()) {
                expenseCategories.joinToString(", ")
            } else {
                "Продукты, Кафе, Фастфуд, Транспорт, Такси, Жилье и ЖКУ, Аптека и здоровье, Развлечения, Одежда, Маркетплейсы, Прочее"
            }
            val incCatsStr = if (incomeCategories.isNotEmpty()) {
                incomeCategories.joinToString(", ")
            } else {
                "Зарплата, Подработка, Фриланс, Подарки, Переводы, Доходы"
            }

            val systemPrompt = """
                Ты — беззвучный потоковый регистратор финансовых операций в приложении David Finance.
                Сегодняшняя дата: $todayStr.
                Доступные категории РАСХОДОВ (type="expense"): [$expCatsStr].
                Доступные категории ДОХОДОВ (type="income"): [$incCatsStr].
                Твоя задача:
                1. Слушай речь пользователя в реальном времени.
                2. Как только пользователь произнёс финансовую операцию с суммой (например: «кофе 300», «такси 500 и шаурма 400», «пришла зарплата 80 тысяч»), НЕМЕДЛЕННО вызови инструмент `$TOOL_RECORD_VOICE_OPERATIONS` и передай распознанные операции.
                3. Если пользователь поправил себя по поводу только что названной операции (например: «кофе 350, булочка 200... а нет, булочка 150», «ой, не 300, а 250 рублей», «точнее это категория Продукты»), НЕМЕДЛЕННО вызови инструмент `$TOOL_CORRECT_LAST_VOICE_OPERATION` с обновлёнными полями, НЕ создавая дублирующую операцию.
                4. Для поля `category` выбирай наиболее подходящую категорию из списка выше, а в `subcategory` и `title` указывай конкретное название покупки или дохода.
                5. Если пользователь назвал несколько покупок подряд, передай их все в массиве `operations`.
                6. НЕ произноси вслух никаких ответов и НЕ веди диалог — только вызывай инструменты `$TOOL_RECORD_VOICE_OPERATIONS` или `$TOOL_CORRECT_LAST_VOICE_OPERATION`.
            """.trimIndent()

            return JSONObject().apply {
                put(
                    "setup",
                    JSONObject().apply {
                        put("model", modelName)
                        put(
                            "generationConfig",
                            JSONObject().apply {
                                put("responseModalities", JSONArray().put(modality))
                            }
                        )
                        put(
                            "systemInstruction",
                            JSONObject().apply {
                                put(
                                    "parts",
                                    JSONArray().put(
                                        JSONObject().apply {
                                            put("text", systemPrompt)
                                        }
                                    )
                                )
                            }
                        )
                        put(
                            "tools",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("functionDeclarations", buildVoiceOperationsFunctionDeclarations())
                                }
                            )
                        )
                        put("inputAudioTranscription", JSONObject())
                    }
                )
            }
        }

        /**
         * Извлекает список [ParsedVoiceOperation] из серверного сообщения `toolCall` от `BidiGenerateContent`.
         */
        internal fun parseToolCallOperations(
            toolCall: JSONObject,
            defaultDateStr: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        ): List<ParsedVoiceOperation> {
            val result = mutableListOf<ParsedVoiceOperation>()
            val functionCalls = toolCall.optJSONArray("functionCalls") ?: return result
            for (i in 0 until functionCalls.length()) {
                val fc = functionCalls.optJSONObject(i) ?: continue
                val fnName = fc.optString("name")
                if (fnName.isNotBlank() && fnName != TOOL_RECORD_VOICE_OPERATIONS) continue
                val args = fc.optJSONObject("args") ?: continue
                val opsArray = args.optJSONArray("operations") ?: continue
                for (j in 0 until opsArray.length()) {
                    val item = opsArray.optJSONObject(j) ?: continue
                    val amount = item.optDouble("amount", 0.0)
                    if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) continue
                    val rawType = item.optString("type", "expense").trim().lowercase(Locale.ROOT)
                    val type = if (rawType == "income") "income" else "expense"
                    val title = item.optString("title", "").trim()
                    val category = item.optString("category", "").trim()
                    val subcategory = item.optString("subcategory", "").trim()
                    val rawDate = item.optString("date", "").trim()
                    val date = if (rawDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) rawDate else defaultDateStr

                    val effectiveTitle = title.ifBlank { subcategory.ifBlank { category.ifBlank { "Операция" } } }
                    val effectiveCategory = category.ifBlank { if (type == "income") "Доходы" else "Прочее" }
                    val effectiveSubcategory = subcategory.ifBlank { effectiveTitle }

                    result.add(
                        ParsedVoiceOperation(
                            title = effectiveTitle,
                            type = type,
                            amount = amount,
                            category = effectiveCategory,
                            subcategory = effectiveSubcategory,
                            date = date
                        )
                    )
                }
            }
            return result
        }

        /**
         * Извлекает список корректировок [VoiceOperationCorrection] из серверного сообщения `toolCall`
         * (`correct_last_voice_operation`) от `BidiGenerateContent`.
         */
        internal fun parseToolCallCorrections(toolCall: JSONObject): List<VoiceOperationCorrection> {
            val corrections = mutableListOf<VoiceOperationCorrection>()
            val functionCalls = toolCall.optJSONArray("functionCalls") ?: return corrections
            for (i in 0 until functionCalls.length()) {
                val fc = functionCalls.optJSONObject(i) ?: continue
                val fnName = fc.optString("name")
                if (fnName != TOOL_CORRECT_LAST_VOICE_OPERATION) continue
                val args = fc.optJSONObject("args") ?: continue

                val rawAmount = if (args.has("amount") && !args.isNull("amount")) {
                    args.optDouble("amount", -1.0)
                } else {
                    -1.0
                }
                val validAmount = rawAmount.takeIf { it > 0.0 && !it.isNaN() && !it.isInfinite() }
                val title = args.optString("title", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
                val category = args.optString("category", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
                val subcategory = args.optString("subcategory", "").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
                val rawType = args.optString("type", "").trim().lowercase(Locale.ROOT)
                val type = when (rawType) {
                    "income" -> "income"
                    "expense" -> "expense"
                    else -> null
                }
                val rawDate = args.optString("date", "").trim()
                val date = rawDate.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }

                val correction = VoiceOperationCorrection(
                    amount = validAmount,
                    title = title,
                    category = category,
                    subcategory = subcategory,
                    type = type,
                    date = date
                )
                if (correction.hasAnyField()) {
                    corrections.add(correction)
                }
            }
            return corrections
        }

        internal fun sanitizeTranscriptText(raw: String): String {
            return raw
                .replace(Regex("\\*\\*[^*]+\\*\\*"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }

    init {
        // Очистка старой тяжелой офлайн-модели VOSK (~50 МБ), если она осталась от прошлых версий
        scope.launch {
            try {
                val legacyVoskDir = File(this@VoiceInputManager.context.filesDir, "vosk-model-small-ru-0.22")
                if (legacyVoskDir.exists()) {
                    legacyVoskDir.deleteRecursively()
                }
                val legacyZip = File(this@VoiceInputManager.context.cacheDir, "vosk_model.zip")
                if (legacyZip.exists()) {
                    legacyZip.delete()
                }
            } catch (_: Throwable) {}
        }
    }

    private fun resolveUserApiKey(callerContext: Context, explicitKey: String = ""): String {
        return try {
            com.example.data.preferences.ApiKeyStorage.getSavedApiKey(callerContext, explicitKey)
        } catch (_: Throwable) {
            ""
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(
        callerContext: Context = context,
        apiKeyOverride: String = "",
        expenseCategories: List<String> = emptyList(),
        incomeCategories: List<String> = emptyList()
    ) {
        GlobalConsoleLogger.i("VOICE", "Запуск потокового режима Gemini Live BidiGenerateContent...")
        stopInternal()

        _recognizedText.value = ""
        _partialText.value = LISTENING_PLACEHOLDER
        _errorState.value = null
        currentExpenseCategories = expenseCategories
        currentIncomeCategories = incomeCategories
        synchronized(pendingLiveTurnTranscript) {
            pendingLiveTurnTranscript.clear()
        }
        synchronized(fullSessionTranscript) {
            fullSessionTranscript.clear()
        }
        synchronized(preSetupPcmQueue) {
            preSetupPcmQueue.clear()
        }

        currentLiveApiKey = resolveUserApiKey(callerContext, apiKeyOverride)
        if (currentLiveApiKey.isNotBlank()) {
            liveIntentionalClose.set(false)
            liveModelAttemptIndex = 0
            connectLiveSttWebSocket(modelIndex = 0)
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufferSize * 2, FRAME_BYTES * 8)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw IllegalStateException("Микрофон не удалось инициализировать")
            }

            record.startRecording()
            audioRecord = record
            _isListening.value = true
            _rmsDb.value = 1.5f
            _voiceLevel.value = 0.08f

            recordingJob = scope.launch {
                val frameBuf = ByteArray(FRAME_BYTES)
                val wsBatchBuffer = ByteArrayOutputStream(FRAME_BYTES * 3)
                var wsBatchCount = 0

                try {
                    while (isActive && _isListening.value) {
                        val readBytes = record.read(frameBuf, 0, FRAME_BYTES)
                        if (readBytes <= 0) continue

                        val frameCopy = frameBuf.copyOf(readBytes)
                        val rms = calculatePcmRms(frameCopy, readBytes)

                        // Плавная нормализация амплитуды (1..12) и уровня голоса (0f..1f) для Volumetric Liquid Aurora
                        val visualDb = if (rms > 80.0) {
                            val db = 20f * log10((rms / 80.0).toFloat())
                            (db * 0.35f).coerceIn(1.5f, 12f)
                        } else {
                            1.0f
                        }
                        _rmsDb.value = visualDb
                        _voiceLevel.value = ((visualDb - 1.0f) / 9.5f).coerceIn(0f, 1f)

                        if (rms >= SPEECH_RMS_THRESHOLD &&
                            (_partialText.value.isBlank() || _partialText.value == LISTENING_PLACEHOLDER)
                        ) {
                            _partialText.value = "Слушаю..."
                        }

                        // Потоковая отправка PCM-аудио в Gemini Live WebSocket (пакетами по ~96 мс)
                        wsBatchBuffer.write(frameCopy)
                        wsBatchCount++
                        if (wsBatchCount >= 3) {
                            streamPcmFrameToLiveWebSocket(wsBatchBuffer.toByteArray())
                            wsBatchBuffer.reset()
                            wsBatchCount = 0
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("VoiceInputManager", "AudioRecord loop error", e)
                } finally {
                    if (wsBatchBuffer.size() > 0) {
                        streamPcmFrameToLiveWebSocket(wsBatchBuffer.toByteArray())
                        wsBatchBuffer.reset()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("VoiceInputManager", "Failed to start AudioRecord", e)
            GlobalConsoleLogger.e("VOICE", "Ошибка запуска Gemini Live микрофона: ${e.localizedMessage}", e)
            _errorState.value = "Ошибка микрофона: ${e.localizedMessage}"
            _isListening.value = false
            stopInternal()
            onErrorCallback?.invoke()
        }
    }

    private fun connectLiveSttWebSocket(modelIndex: Int) {
        if (currentLiveApiKey.isBlank()) return
        liveSetupCompleted.set(false)
        try {
            liveWebSocket?.cancel()
        } catch (_: Throwable) {}

        val modelName = LIVE_STT_MODELS.getOrElse(modelIndex) { LIVE_STT_MODELS.last() }
        val wsUrl = "$GEMINI_LIVE_WS_ENDPOINT?key=$currentLiveApiKey"
        val request = Request.Builder().url(wsUrl).build()

        liveWebSocket = liveHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (liveIntentionalClose.get()) {
                        webSocket.close(1000, "Stopped")
                        return
                    }
                    val setupMsg = buildLiveTranscriptionSetupJson(
                        modelName = modelName,
                        expenseCategories = currentExpenseCategories,
                        incomeCategories = currentIncomeCategories
                    )
                    webSocket.send(setupMsg.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleLiveServerJsonMessage(webSocket, text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleLiveServerJsonMessage(webSocket, bytes.utf8())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    if (!liveIntentionalClose.get() && !liveSetupCompleted.get() && liveModelAttemptIndex + 1 < LIVE_STT_MODELS.size) {
                        liveModelAttemptIndex++
                        connectLiveSttWebSocket(liveModelAttemptIndex)
                        return
                    }
                    liveTurnOrToolLatch?.countDown()
                    try {
                        webSocket.close(code, reason)
                    } catch (_: Throwable) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (liveIntentionalClose.get()) {
                        liveTurnOrToolLatch?.countDown()
                        return
                    }
                    if (!liveSetupCompleted.get() && liveModelAttemptIndex + 1 < LIVE_STT_MODELS.size) {
                        liveModelAttemptIndex++
                        connectLiveSttWebSocket(liveModelAttemptIndex)
                        return
                    }
                    liveTurnOrToolLatch?.countDown()
                }
            }
        )
    }

    private fun streamPcmFrameToLiveWebSocket(pcm16kBytes: ByteArray) {
        if (currentLiveApiKey.isBlank() || pcm16kBytes.isEmpty()) return
        val b64 = Base64.encodeToString(pcm16kBytes, Base64.NO_WRAP)
        val ws = liveWebSocket
        if (liveSetupCompleted.get() && ws != null) {
            sendPcmBase64ToWebSocket(ws, b64)
        } else {
            synchronized(preSetupPcmQueue) {
                if (preSetupPcmQueue.size >= 60) {
                    preSetupPcmQueue.removeFirst()
                }
                preSetupPcmQueue.addLast(b64)
            }
        }
    }

    private fun flushPreSetupPcmQueue(ws: WebSocket?) {
        if (ws == null) return
        val buffered = mutableListOf<String>()
        synchronized(preSetupPcmQueue) {
            while (preSetupPcmQueue.isNotEmpty()) {
                buffered.add(preSetupPcmQueue.removeFirst())
            }
        }
        for (b64 in buffered) {
            sendPcmBase64ToWebSocket(ws, b64)
        }
    }

    private fun sendPcmBase64ToWebSocket(ws: WebSocket, base64Pcm: String) {
        val msg = JSONObject().apply {
            put(
                "realtimeInput",
                JSONObject().apply {
                    put(
                        "mediaChunks",
                        JSONArray().put(
                            JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=16000")
                                put("data", base64Pcm)
                            }
                        )
                    )
                }
            )
        }
        ws.send(msg.toString())
    }

    private fun sendAudioStreamEndToWebSocket(ws: WebSocket) {
        val streamEndMsg = JSONObject().apply {
            put(
                "realtimeInput",
                JSONObject().apply {
                    put("audioStreamEnd", true)
                }
            )
        }
        ws.send(streamEndMsg.toString())

        val turnCompleteMsg = JSONObject().apply {
            put(
                "clientContent",
                JSONObject().apply {
                    put("turnComplete", true)
                }
            )
        }
        ws.send(turnCompleteMsg.toString())
    }

    private fun sendToolResponseToWebSocket(ws: WebSocket?, functionCalls: List<Pair<String, String>>) {
        if (ws == null || functionCalls.isEmpty()) return
        val responsesArray = JSONArray()
        for ((callId, fnName) in functionCalls) {
            responsesArray.put(
                JSONObject().apply {
                    if (callId.isNotBlank()) put("id", callId)
                    put("name", fnName.ifBlank { TOOL_RECORD_VOICE_OPERATIONS })
                    put(
                        "response",
                        JSONObject().apply {
                            put("output", JSONObject().put("status", "recorded"))
                        }
                    )
                }
            )
        }
        val toolResponseMsg = JSONObject().apply {
            put(
                "toolResponse",
                JSONObject().apply {
                    put("functionResponses", responsesArray)
                }
            )
        }
        ws.send(toolResponseMsg.toString())
    }

    /**
     * Обрабатывает сообщения от Gemini Live в потоковом режиме (доступен для unit-тестов).
     */
    internal fun processLiveServerMessageForTest(rawJson: String) {
        handleLiveServerJsonMessage(ws = null, rawJson = rawJson)
    }

    private fun handleLiveServerJsonMessage(ws: WebSocket?, rawJson: String) {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return
        }

        if (root.has("setupComplete")) {
            liveSetupCompleted.set(true)
            flushPreSetupPcmQueue(ws)
            return
        }

        // Обработка потокового вызова инструментов (`record_voice_operations` и `correct_last_voice_operation`) прямо из BidiGenerateContent
        val toolCall = root.optJSONObject("toolCall")
        if (toolCall != null) {
            val functionCallsArray = toolCall.optJSONArray("functionCalls")
            val callDescriptors = mutableListOf<Pair<String, String>>()
            if (functionCallsArray != null) {
                for (i in 0 until functionCallsArray.length()) {
                    val fc = functionCallsArray.optJSONObject(i) ?: continue
                    val id = fc.optString("id")
                    val name = fc.optString("name", TOOL_RECORD_VOICE_OPERATIONS)
                    callDescriptors.add(id to name)
                }
            }
            sendToolResponseToWebSocket(ws, callDescriptors)

            val capturedOps = parseToolCallOperations(toolCall)
            if (capturedOps.isNotEmpty()) {
                val lastOp = capturedOps.last()
                val label = lastOp.subcategory.ifBlank { lastOp.title.ifBlank { lastOp.category } }
                val summary = "$label ${lastOp.amount.toInt()} ₽"
                _recognizedText.value = summary
                _partialText.value = summary
                GlobalConsoleLogger.i(
                    "VOICE_LIVE",
                    "BidiGenerateContent toolCall: захвачено ${capturedOps.size} оп. (последняя: $summary)"
                )
                onOperationsCaptured?.invoke(capturedOps)
            }

            val corrections = parseToolCallCorrections(toolCall)
            for (correction in corrections) {
                val corrLabel = correction.subcategory
                    ?: correction.title
                    ?: correction.category
                    ?: "Операция"
                val corrAmountPart = correction.amount?.let { " ${it.toInt()} ₽" } ?: ""
                val corrSummary = "✎ $corrLabel$corrAmountPart"
                _recognizedText.value = corrSummary
                _partialText.value = corrSummary
                GlobalConsoleLogger.i(
                    "VOICE_LIVE",
                    "BidiGenerateContent toolCall: исправлена последняя операция ($corrSummary)"
                )
                onLastOperationCorrected?.invoke(correction)
            }

            liveTurnOrToolLatch?.countDown()
            return
        }

        val serverContent = root.optJSONObject("serverContent") ?: return

        val inputTranscription = serverContent.optJSONObject("inputTranscription")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }

        if (inputTranscription != null) {
            val cleaned = synchronized(pendingLiveTurnTranscript) {
                pendingLiveTurnTranscript.append(inputTranscription)
                sanitizeTranscriptText(pendingLiveTurnTranscript.toString())
            }
            if (cleaned.isNotBlank()) {
                _partialText.value = cleaned
                _recognizedText.value = cleaned
            }
        }

        val isTurnComplete = serverContent.optBoolean("turnComplete", false) ||
            serverContent.has("modelTurn") ||
            serverContent.has("outputTranscription")

        if (isTurnComplete) {
            val completedTurnText = synchronized(pendingLiveTurnTranscript) {
                val text = sanitizeTranscriptText(pendingLiveTurnTranscript.toString())
                pendingLiveTurnTranscript.clear()
                text
            }
            if (completedTurnText.isNotBlank()) {
                synchronized(fullSessionTranscript) {
                    if (fullSessionTranscript.isNotEmpty()) fullSessionTranscript.append(' ')
                    fullSessionTranscript.append(completedTurnText)
                }
                _recognizedText.value = completedTurnText
                _partialText.value = completedTurnText
                GlobalConsoleLogger.i("VOICE_LIVE", "Фраза распознана в Live STT: «$completedTurnText»")
                onChunkRecognized?.invoke(completedTurnText)
            }
            liveTurnOrToolLatch?.countDown()
        }
    }

    /**
     * Завершает захват микрофона, отправляет сигнал окончания аудиопотока в активный WebSocket
     * `BidiGenerateContent` и кратко дожидается финального `toolCall` / `turnComplete`,
     * после чего закрывает соединение. Не создаёт WAV-файлов и не выполняет HTTP POST.
     */
    suspend fun finishListeningAndAwaitLiveFlush(timeoutMs: Long = 1600L): String {
        GlobalConsoleLogger.i("VOICE", "Завершение потоковой сессии Gemini Live BidiGenerateContent...")
        _isListening.value = false
        val jobToJoin = recordingJob
        recordingJob = null
        try {
            jobToJoin?.cancel()
        } catch (_: Throwable) {}

        stopAudioRecordOnly()
        _rmsDb.value = 0f
        _voiceLevel.value = 0f

        val ws = liveWebSocket
        val isLiveReady = liveSetupCompleted.get() && ws != null
        if (isLiveReady && ws != null) {
            val latch = CountDownLatch(1)
            liveTurnOrToolLatch = latch
            try {
                sendAudioStreamEndToWebSocket(ws)
                withContext(Dispatchers.IO) {
                    latch.await(timeoutMs, TimeUnit.MILLISECONDS)
                }
            } catch (_: Throwable) {
            } finally {
                liveTurnOrToolLatch = null
            }
        }

        val remainingTurnText = synchronized(pendingLiveTurnTranscript) {
            val t = sanitizeTranscriptText(pendingLiveTurnTranscript.toString())
            pendingLiveTurnTranscript.clear()
            t
        }
        if (remainingTurnText.isNotBlank()) {
            synchronized(fullSessionTranscript) {
                if (fullSessionTranscript.isNotEmpty()) fullSessionTranscript.append(' ')
                fullSessionTranscript.append(remainingTurnText)
            }
            _recognizedText.value = remainingTurnText
            _partialText.value = remainingTurnText
        } else if (_partialText.value == LISTENING_PLACEHOLDER || _partialText.value == "Слушаю...") {
            _partialText.value = _recognizedText.value
        }

        closeLiveWebSocket()

        val fullText = synchronized(fullSessionTranscript) {
            sanitizeTranscriptText(fullSessionTranscript.toString())
        }
        return fullText.ifBlank { _recognizedText.value }
    }

    fun stopListening() {
        GlobalConsoleLogger.i("VOICE", "Полная остановка записи речи")
        stopInternal()
    }

    private fun stopAudioRecordOnly() {
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    record.release()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun closeLiveWebSocket() {
        liveIntentionalClose.set(true)
        liveSetupCompleted.set(false)
        liveTurnOrToolLatch?.countDown()
        liveTurnOrToolLatch = null
        try {
            liveWebSocket?.close(1000, "Voice input finished")
        } catch (_: Throwable) {}
        liveWebSocket = null
        synchronized(preSetupPcmQueue) {
            preSetupPcmQueue.clear()
        }
    }

    private fun stopInternal() {
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null
        closeLiveWebSocket()
        stopAudioRecordOnly()
        _rmsDb.value = 0f
        _voiceLevel.value = 0f
    }

    private fun calculatePcmRms(buffer: ByteArray, length: Int): Double {
        var sumSquares = 0.0
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0.0
        var i = 0
        while (i < length - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = (high shl 8) or low
            sumSquares += sample.toDouble() * sample.toDouble()
            i += 2
        }
        return sqrt(sumSquares / sampleCount)
    }

    fun clear() {
        synchronized(pendingLiveTurnTranscript) {
            pendingLiveTurnTranscript.clear()
        }
        synchronized(fullSessionTranscript) {
            fullSessionTranscript.clear()
        }
        _recognizedText.value = ""
        _partialText.value = ""
        _errorState.value = null
    }

    fun setRecognizedTextManually(text: String) {
        _recognizedText.value = text
        _partialText.value = text
    }

    fun destroy() {
        stopInternal()
    }
}
