package com.example.data.api

import com.example.BuildConfig
import com.example.utils.GlobalConsoleLogger
import com.example.utils.NetworkCallStatus
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

data class GeminiHttpErrorDetails(
    val httpCode: Int?,
    val errorType: String,
    val rawMessage: String,
    val userMessage: String,
    val isFatalForAllModels: Boolean
)

object GeminiModelConfig {
    /**
     * Основная модель по умолчанию для всех реакций Давида Жабова (одиночных и групповых).
     * По диагностическим отчётам gemini-3.5-flash-lite стабильно генерирует реакции
     * за ~0.8–1.4 секунды без частых HTTP 503 и SocketTimeoutException.
     */
    const val DEFAULT_DAVID_REACTION_MODEL = "gemini-3.5-flash-lite"

    /**
     * Цепочка моделей для реакций Давида Жабова (одиночных и групповых):
     * 1. gemini-3.5-flash-lite (основная модель по умолчанию)
     * 2. gemini-3.8-flash (резервная)
     * 3. gemini-3.6-flash (резервная)
     * 4. gemini-3.1-flash-lite (резервная)
     */
    val DAVID_REACTION_MODELS: List<String> = listOf(
        DEFAULT_DAVID_REACTION_MODEL,
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.1-flash-lite"
    )

    /**
     * Цепочка моделей для аналитического аудита бюджета (оставлена без изменений).
     */
    val BUDGET_AUDIT_MODELS: List<String> = listOf(
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-2.5-flash"
    )

    private val PLACEHOLDER_KEYS = setOf(
        "your_api_key_here",
        "my_gemini_api_key",
        "your_api_key",
        "api_key_here",
        "null",
        "undefined",
        "none",
        "placeholder"
    )

    /**
     * Очищает API-ключ от пробелов, переносов строк, управляющих символов и кавычек из .env,
     * отсекая заглушки сборки.
     */
    fun sanitizeApiKey(rawKey: String?): String {
        if (rawKey.isNullOrBlank()) return ""
        val cleaned = rawKey
            .trim()
            .trim('"', '\'', '`', ' ', '\t', '\r', '\n')
            .trim()
        if (cleaned.isEmpty()) return ""
        if (cleaned.lowercase() in PLACEHOLDER_KEYS) return ""
        return cleaned
    }

    /**
     * Проверяет, имеет ли строка формат реального ключа Google Gemini API (AIza...).
     */
    fun isValidGeminiApiKey(rawKey: String?): Boolean {
        val clean = sanitizeApiKey(rawKey)
        return clean.length >= 20 && clean.startsWith("AIza")
    }

    /**
     * Извлекает структурированную причину ошибки из ответа Gemini REST API (включая тело ошибки при HttpException).
     */
    fun parseErrorBodyJson(rawBody: String?, httpCode: Int): GeminiHttpErrorDetails {
        val bodyText = rawBody?.trim().orEmpty()
        var status = ""
        var message = ""
        var reason = ""

        if (bodyText.isNotEmpty()) {
            try {
                val parsed = RetrofitClient.moshi.adapter(GeminiResponse::class.java).fromJson(bodyText)
                val err = parsed?.error
                if (err != null) {
                    status = err.status?.trim().orEmpty()
                    message = err.message?.trim().orEmpty()
                }
            } catch (_: Exception) {
            }
            if (status.isEmpty()) {
                status = Regex(""""status"\s*:\s*"([^"]+)"""").find(bodyText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            }
            if (message.isEmpty()) {
                message = Regex(""""message"\s*:\s*"([^"]+)"""").find(bodyText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            }
            reason = Regex(""""reason"\s*:\s*"([^"]+)"""").find(bodyText)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (message.isEmpty() && !bodyText.startsWith("{")) {
                message = bodyText.take(240)
            }
        }

        val effectiveErrorType = when {
            reason.isNotEmpty() -> reason
            status.isNotEmpty() -> status
            else -> "HTTP_$httpCode"
        }

        val isInvalidKey = reason.equals("API_KEY_INVALID", ignoreCase = true) ||
            reason.equals("API_KEY_SERVICE_BLOCKED", ignoreCase = true) ||
            message.contains("API key not valid", ignoreCase = true) ||
            message.contains("API_KEY_INVALID", ignoreCase = true)

        val isUnsupportedLocation = (status.equals("FAILED_PRECONDITION", ignoreCase = true) || httpCode == 400) &&
            message.contains("location is not supported", ignoreCase = true)

        val isPermissionDenied = httpCode == 403 || status.equals("PERMISSION_DENIED", ignoreCase = true)

        val isFatalForAllModels = isInvalidKey || isUnsupportedLocation || isPermissionDenied

        val userMessage = when {
            isInvalidKey ->
                "Недействительный API-ключ Gemini (HTTP $httpCode: API_KEY_INVALID). Проверьте или обновите ключ в настройках ИИ."
            isUnsupportedLocation ->
                "Gemini API недоступен в вашем регионе без VPN (HTTP $httpCode: FAILED_PRECONDITION — User location is not supported)."
            isPermissionDenied ->
                "Доступ к Gemini API отклонён (HTTP $httpCode: ${message.ifBlank { "PERMISSION_DENIED" }}). Проверьте права API-ключа."
            message.isNotBlank() && status.isNotBlank() ->
                "HTTP $httpCode ($status): $message"
            message.isNotBlank() ->
                "HTTP $httpCode: $message"
            else ->
                "HTTP $httpCode"
        }

        return GeminiHttpErrorDetails(
            httpCode = httpCode,
            errorType = effectiveErrorType,
            rawMessage = message.ifBlank { "HTTP $httpCode" },
            userMessage = userMessage,
            isFatalForAllModels = isFatalForAllModels
        )
    }

    fun extractHttpErrorDetails(throwable: Throwable): GeminiHttpErrorDetails {
        if (throwable is HttpException) {
            val code = throwable.code()
            val errorBody = try {
                throwable.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            return parseErrorBodyJson(errorBody, code)
        }
        val msg = (throwable.localizedMessage ?: throwable.message ?: throwable.javaClass.simpleName).trim()
        return GeminiHttpErrorDetails(
            httpCode = null,
            errorType = throwable.javaClass.simpleName,
            rawMessage = msg,
            userMessage = msg,
            isFatalForAllModels = false
        )
    }

    /**
     * Выполняет генерацию реакции Давида Жабова (одиночной или групповой) с использованием
     * основной модели по умолчанию [DEFAULT_DAVID_REACTION_MODEL] и автоматического fallback-переключения
     * по цепочке [models], фиксируя телеметрию в [GlobalConsoleLogger].
     */
    suspend fun executeDavidReactionWithFallback(
        apiService: GeminiApiService,
        apiKey: String,
        request: GeminiRequest,
        operationType: String,
        operationPrefix: String,
        details: String,
        models: List<String> = DAVID_REACTION_MODELS
    ): String? {
        val cleanKey = sanitizeApiKey(apiKey).ifEmpty { apiKey.trim() }
        val opId = GlobalConsoleLogger.newOperationId(operationPrefix)

        for ((idx, model) in models.withIndex()) {
            val attempt = idx + 1
            val startMs = System.currentTimeMillis()
            try {
                val response = apiService.generateContent(model, cleanKey, request)
                if (response.error == null) {
                    val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?.trim()
                        ?.removePrefix("\"")
                        ?.removeSuffix("\"")
                        ?.removePrefix("«")
                        ?.removeSuffix("»")
                        ?.trim()
                    if (!responseText.isNullOrEmpty()) {
                        GlobalConsoleLogger.recordNetworkCall(
                            operationType = operationType,
                            modelName = model,
                            durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                            status = NetworkCallStatus.SUCCESS,
                            httpCode = 200,
                            attemptNumber = attempt,
                            totalModelsInChain = models.size,
                            details = details,
                            operationId = opId
                        )
                        return responseText
                    }
                } else {
                    GlobalConsoleLogger.recordNetworkCall(
                        operationType = operationType,
                        modelName = model,
                        durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                        status = if (attempt < models.size) NetworkCallStatus.FALLBACK else NetworkCallStatus.ERROR,
                        httpCode = response.error.code,
                        errorType = response.error.status ?: "GeminiError",
                        errorMessage = response.error.message,
                        attemptNumber = attempt,
                        totalModelsInChain = models.size,
                        details = details,
                        operationId = opId
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val err = extractHttpErrorDetails(e)
                val isLastOrFatal = err.isFatalForAllModels || attempt >= models.size
                GlobalConsoleLogger.recordNetworkCall(
                    operationType = operationType,
                    modelName = model,
                    durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
                    status = if (isLastOrFatal) NetworkCallStatus.ERROR else NetworkCallStatus.FALLBACK,
                    httpCode = err.httpCode,
                    errorType = err.errorType,
                    errorMessage = err.userMessage,
                    attemptNumber = attempt,
                    totalModelsInChain = models.size,
                    details = details,
                    operationId = opId
                )
                if (err.isFatalForAllModels) {
                    break
                }
            }
        }
        return null
    }
}

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @field:Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null
)

data class GeminiInlineData(
    @field:Json(name = "mimeType") val mimeType: String,
    @field:Json(name = "data") val data: String
)

data class GeminiPart(
    val text: String? = null,
    @field:Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent")
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Query("alt") alt: String = "sse",
        @Body request: GeminiRequest
    ): Response<ResponseBody>
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val diagnosticTelemetryInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (!BuildConfig.DEBUG) {
            return@Interceptor chain.proceed(request)
        }

        val startNs = System.nanoTime()
        val path = request.url.encodedPath
        val modelSegment = path.substringAfter("/models/", "gemini")
            .substringBefore(":")
        val actionSegment = path.substringAfter(":", "request")

        try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            val code = response.code
            if (response.isSuccessful) {
                GlobalConsoleLogger.d(
                    tag = "NETWORK",
                    message = "HTTP $code ${request.method} $actionSegment ($modelSegment) за ${durationMs}ms",
                    details = "host=${request.url.host} | protocol=${response.protocol}"
                )
            } else {
                val peekedError = try {
                    val raw = response.peekBody(4096).string()
                    GeminiModelConfig.parseErrorBodyJson(raw, code).userMessage
                } catch (_: Exception) {
                    response.message.ifBlank { "HTTP Error" }
                }
                GlobalConsoleLogger.w(
                    tag = "NETWORK",
                    message = "HTTP $code ${request.method} $actionSegment ($modelSegment) за ${durationMs}ms",
                    details = "message=$peekedError"
                )
            }
            response
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            GlobalConsoleLogger.e(
                tag = "NETWORK",
                message = "Сбой HTTP ${request.method} $actionSegment ($modelSegment) через ${durationMs}ms: ${e.javaClass.simpleName}",
                throwable = e
            )
            throw e
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(diagnosticTelemetryInterceptor)
        .addInterceptor(HttpLoggingInterceptor { message ->
            GlobalConsoleLogger.d("HTTP", message)
        }.apply {
            redactHeader("x-goog-api-key")
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}
