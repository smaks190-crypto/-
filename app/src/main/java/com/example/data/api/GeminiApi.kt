package com.example.data.api

import com.example.BuildConfig
import com.example.utils.GlobalConsoleLogger
import com.example.utils.NetworkCallStatus
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val HEADER_TIMEOUT_PROFILE = "X-David-Timeout-Profile"

enum class GeminiTimeoutProfile(
    val connectTimeoutMs: Long,
    val readTimeoutMs: Long,
    val writeTimeoutMs: Long
) {
    /** Короткие фоновые вызовы: реакция Давида Жабова, фраза пользователя, автоподбор категории */
    FAST_BACKGROUND(
        connectTimeoutMs = 5_000L,
        readTimeoutMs = 7_500L,
        writeTimeoutMs = 5_000L
    ),

    /** Голосовой ввод (аудио WAV чанк или распознанный текст) */
    VOICE_PARSE(
        connectTimeoutMs = 6_000L,
        readTimeoutMs = 10_000L,
        writeTimeoutMs = 8_000L
    ),

    /** Длинный аналитический аудит бюджета (SSE Stream или полный отчет) */
    STREAMING_AUDIT(
        connectTimeoutMs = 10_000L,
        readTimeoutMs = 25_000L,
        writeTimeoutMs = 10_000L
    );

    companion object {
        fun fromHeader(value: String?): GeminiTimeoutProfile? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        }
    }
}

object GeminiModelChains {
    val DAVID_REACTION_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite"
    )

    val USER_PHRASE_MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite"
    )

    val CATEGORY_SUGGEST_MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-lite"
    )

    val VOICE_TEXT_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash"
    )

    /**
     * Для голосового аудио-парсинга:
     * gemini-3.5-flash-lite и gemini-3.5-flash стоят первыми, так как gemini-2.5-flash
     * на текущем эндпоинте v1beta/models/{model}:generateContent возвращает HTTP 404.
     * gemini-2.5-flash сохранена в конце цепочки как резервная.
     */
    val VOICE_AUDIO_MODELS = listOf(
        "gemini-3.5-flash-lite",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash"
    )

    val AUDIT_MODELS = listOf(
        "gemini-3.8-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gemini-3.5-flash-lite",
        "gemini-2.5-flash"
    )
}

data class GeminiRequest(
    val contents: List<GeminiContent>,
    @field:Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiInlineData(
    @field:Json(name = "mime_type") val mimeType: String,
    val data: String
)

data class GeminiPart(
    val text: String? = null,
    @field:Json(name = "inline_data") val inlineData: GeminiInlineData? = null
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
        @Body request: GeminiRequest,
        @Header(HEADER_TIMEOUT_PROFILE) timeoutProfile: String = GeminiTimeoutProfile.FAST_BACKGROUND.name
    ): GeminiResponse

    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent")
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Query("alt") alt: String = "sse",
        @Body request: GeminiRequest,
        @Header(HEADER_TIMEOUT_PROFILE) timeoutProfile: String = GeminiTimeoutProfile.STREAMING_AUDIT.name
    ): Response<ResponseBody>
}

sealed interface GeminiChainResult<out T> {
    val operationId: String
    val chainOutcome: NetworkCallStatus
    val attemptsMade: Int

    data class Success<T>(
        val value: T,
        val modelUsed: String,
        override val operationId: String,
        override val chainOutcome: NetworkCallStatus,
        override val attemptsMade: Int
    ) : GeminiChainResult<T>

    data class Failure(
        val lastHttpCode: Int?,
        val lastErrorType: String?,
        val lastErrorMessage: String?,
        override val operationId: String,
        override val chainOutcome: NetworkCallStatus = NetworkCallStatus.FAILED,
        override val attemptsMade: Int
    ) : GeminiChainResult<Nothing>
}

data class ModelCooldownInfo(
    val modelName: String,
    val reason: String,
    val httpCode: Int?,
    val expiresAtEpochMs: Long
)

/**
 * Централизованный исполнитель цепочек fallback для Gemini API.
 * Обеспечивает:
 * - Корректную обработку HTTP 404, 429 (с учётом Retry-After), 503 и SocketTimeoutException;
 * - Немедленный проброс CancellationException без вызова следующих моделей;
 * - Сессионный кэш здоровья моделей (cooldown), чтобы недоступная/таймаутящая модель не задерживала
 *   каждый последующий запрос пользователя на десятки секунд;
 * - Прозрачную телеметрию в Developer Console (SUCCESS, SUCCESS_AFTER_FALLBACK, FAILED, CANCELLED)
 *   с единым operationId и без ложных ERROR при успешном переключении на резервную модель.
 */
object GeminiFallbackExecutor {
    const val COOLDOWN_404_MS = 15 * 60 * 1000L // 15 минут для недоступной модели (HTTP 404)
    const val COOLDOWN_503_TIMEOUT_MS = 2 * 60 * 1000L // 2 минуты для перегруженной/зависшей модели (503 / Timeout)
    const val COOLDOWN_429_DEFAULT_MS = 60 * 1000L // 1 минута по умолчанию для Rate Limit (HTTP 429)
    const val COOLDOWN_TRANSIENT_MS = 30 * 1000L

    private val modelCooldowns = ConcurrentHashMap<String, ModelCooldownInfo>()

    fun clearCooldowns() {
        modelCooldowns.clear()
    }

    fun getModelCooldown(modelName: String, nowMs: Long = System.currentTimeMillis()): ModelCooldownInfo? {
        val info = modelCooldowns[modelName] ?: return null
        if (nowMs >= info.expiresAtEpochMs) {
            modelCooldowns.remove(modelName, info)
            return null
        }
        return info
    }

    fun markModelHealthy(modelName: String) {
        modelCooldowns.remove(modelName)
    }

    fun markModelFailure(
        modelName: String,
        httpCode: Int?,
        errorType: String?,
        retryAfterMs: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cooldownDuration = when {
            httpCode == 404 -> COOLDOWN_404_MS
            httpCode == 429 -> (retryAfterMs ?: COOLDOWN_429_DEFAULT_MS).coerceIn(1_000L, 300_000L)
            httpCode == 503 || httpCode in 500..599 -> COOLDOWN_503_TIMEOUT_MS
            errorType == "SocketTimeoutException" || errorType?.contains("Timeout", ignoreCase = true) == true -> COOLDOWN_503_TIMEOUT_MS
            else -> COOLDOWN_TRANSIENT_MS
        }
        modelCooldowns[modelName] = ModelCooldownInfo(
            modelName = modelName,
            reason = errorType ?: (httpCode?.let { "HTTP_$it" } ?: "ERROR"),
            httpCode = httpCode,
            expiresAtEpochMs = nowMs + cooldownDuration
        )
    }

    /**
     * Упорядочивает модели: сначала здоровые модели в исходном порядке приоритета,
     * затем модели в состоянии cooldown (как крайний резерв, чтобы ни одна модель не пропадала навсегда).
     */
    fun orderModelsByHealth(
        models: List<String>,
        nowMs: Long = System.currentTimeMillis()
    ): List<String> {
        if (models.size <= 1) return models
        val healthy = mutableListOf<String>()
        val cooledDown = mutableListOf<Pair<String, Long>>()

        for (model in models.distinct()) {
            val cd = getModelCooldown(model, nowMs)
            if (cd == null) {
                healthy.add(model)
            } else {
                cooledDown.add(model to cd.expiresAtEpochMs)
            }
        }
        return healthy + cooledDown.sortedBy { it.second }.map { it.first }
    }

    internal fun parseRetryAfterMs(retryAfterHeader: String?): Long? {
        if (retryAfterHeader.isNullOrBlank()) return null
        val seconds = retryAfterHeader.trim().toLongOrNull()
        return if (seconds != null && seconds >= 0L) {
            (seconds * 1000L).coerceAtMost(300_000L)
        } else {
            null
        }
    }

    suspend fun <T> executeWithFallback(
        operationType: String,
        operationPrefix: String = "AI",
        models: List<String>,
        details: String? = null,
        operationId: String? = null,
        respectCooldownOrder: Boolean = true,
        extractResult: (GeminiResponse) -> T?,
        apiCall: suspend (model: String) -> GeminiResponse
    ): GeminiChainResult<T> {
        val opId = operationId?.takeIf { it.isNotBlank() } ?: GlobalConsoleLogger.newOperationId(operationPrefix)
        val orderedModels = if (respectCooldownOrder) {
            orderModelsByHealth(models)
        } else {
            models.distinct()
        }

        if (orderedModels.isEmpty()) {
            GlobalConsoleLogger.recordNetworkCall(
                operationType = operationType,
                modelName = "none",
                durationMs = 0L,
                status = NetworkCallStatus.FAILED,
                errorType = "EmptyModelChain",
                errorMessage = "Список моделей пуст",
                attemptNumber = 1,
                totalModelsInChain = 1,
                details = details,
                operationId = opId
            )
            return GeminiChainResult.Failure(
                lastHttpCode = null,
                lastErrorType = "EmptyModelChain",
                lastErrorMessage = "Список моделей пуст",
                operationId = opId,
                attemptsMade = 0
            )
        }

        val totalModels = orderedModels.size
        var lastHttpCode: Int? = null
        var lastErrorType: String? = null
        var lastErrorMessage: String? = null

        for ((idx, model) in orderedModels.withIndex()) {
            val attempt = idx + 1
            val isLastAttempt = attempt >= totalModels
            val startMs = System.currentTimeMillis()

            try {
                val response = apiCall(model)
                val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)

                if (response.error == null) {
                    val extracted = extractResult(response)
                    if (extracted != null) {
                        markModelHealthy(model)
                        val finalStatus = if (attempt == 1) {
                            NetworkCallStatus.SUCCESS
                        } else {
                            NetworkCallStatus.SUCCESS_AFTER_FALLBACK
                        }
                        GlobalConsoleLogger.recordNetworkCall(
                            operationType = operationType,
                            modelName = model,
                            durationMs = durationMs,
                            status = finalStatus,
                            httpCode = 200,
                            attemptNumber = attempt,
                            totalModelsInChain = totalModels,
                            details = details,
                            operationId = opId
                        )
                        return GeminiChainResult.Success(
                            value = extracted,
                            modelUsed = model,
                            operationId = opId,
                            chainOutcome = finalStatus,
                            attemptsMade = attempt
                        )
                    } else {
                        lastHttpCode = 200
                        lastErrorType = "EmptyCandidates"
                        lastErrorMessage = "Пустой или некорректный ответ от модели $model"
                        markModelFailure(model, httpCode = null, errorType = lastErrorType)
                        GlobalConsoleLogger.recordNetworkCall(
                            operationType = operationType,
                            modelName = model,
                            durationMs = durationMs,
                            status = if (!isLastAttempt) NetworkCallStatus.FALLBACK else NetworkCallStatus.FAILED,
                            httpCode = 200,
                            errorType = lastErrorType,
                            errorMessage = lastErrorMessage,
                            attemptNumber = attempt,
                            totalModelsInChain = totalModels,
                            details = details,
                            operationId = opId
                        )
                    }
                } else {
                    val errCode = response.error.code
                    val errStatus = response.error.status ?: (errCode?.let { "HTTP_$it" } ?: "GeminiError")
                    val errMsg = response.error.message ?: "Ошибка Gemini API"

                    lastHttpCode = errCode
                    lastErrorType = errStatus
                    lastErrorMessage = errMsg

                    markModelFailure(model, httpCode = errCode, errorType = errStatus)
                    GlobalConsoleLogger.recordNetworkCall(
                        operationType = operationType,
                        modelName = model,
                        durationMs = durationMs,
                        status = if (!isLastAttempt) NetworkCallStatus.FALLBACK else NetworkCallStatus.FAILED,
                        httpCode = errCode,
                        errorType = errStatus,
                        errorMessage = errMsg,
                        attemptNumber = attempt,
                        totalModelsInChain = totalModels,
                        details = details,
                        operationId = opId
                    )
                }
            } catch (ce: CancellationException) {
                val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
                GlobalConsoleLogger.recordNetworkCall(
                    operationType = operationType,
                    modelName = model,
                    durationMs = durationMs,
                    status = NetworkCallStatus.CANCELLED,
                    errorType = "CancellationException",
                    errorMessage = ce.localizedMessage ?: ce.message ?: "Запрос отменён",
                    attemptNumber = attempt,
                    totalModelsInChain = totalModels,
                    details = details,
                    operationId = opId
                )
                throw ce
            } catch (e: Exception) {
                val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
                val httpEx = e as? HttpException
                val httpCode = httpEx?.code()
                val retryAfterHeader = httpEx?.response()?.headers()?.get("Retry-After")
                val retryAfterMs = parseRetryAfterMs(retryAfterHeader)

                val errType = when {
                    httpCode != null -> "HTTP_$httpCode"
                    e is SocketTimeoutException -> "SocketTimeoutException"
                    else -> e.javaClass.simpleName
                }
                val errMsg = buildString {
                    append(e.localizedMessage ?: e.message ?: errType)
                    if (retryAfterMs != null) {
                        append(" (Retry-After: ${retryAfterMs / 1000}s)")
                    }
                }

                lastHttpCode = httpCode
                lastErrorType = errType
                lastErrorMessage = errMsg

                markModelFailure(
                    modelName = model,
                    httpCode = httpCode,
                    errorType = errType,
                    retryAfterMs = retryAfterMs
                )

                GlobalConsoleLogger.recordNetworkCall(
                    operationType = operationType,
                    modelName = model,
                    durationMs = durationMs,
                    status = if (!isLastAttempt) NetworkCallStatus.FALLBACK else NetworkCallStatus.FAILED,
                    httpCode = httpCode,
                    errorType = errType,
                    errorMessage = errMsg,
                    attemptNumber = attempt,
                    totalModelsInChain = totalModels,
                    details = details,
                    operationId = opId
                )

                // При HTTP 429 с коротким Retry-After (до 1.5 сек) выдерживаем минимальную паузу перед fallback
                if (!isLastAttempt && httpCode == 429 && retryAfterMs != null && retryAfterMs in 100L..1_500L) {
                    delay(retryAfterMs)
                }
            }
        }

        return GeminiChainResult.Failure(
            lastHttpCode = lastHttpCode,
            lastErrorType = lastErrorType,
            lastErrorMessage = lastErrorMessage,
            operationId = opId,
            chainOutcome = NetworkCallStatus.FAILED,
            attemptsMade = totalModels
        )
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val diagnosticTelemetryInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val profileHeader = originalRequest.header(HEADER_TIMEOUT_PROFILE)
        val path = originalRequest.url.encodedPath
        val actionSegment = path.substringAfter(":", "request")

        val profile = GeminiTimeoutProfile.fromHeader(profileHeader)
            ?: if (actionSegment.contains("stream", ignoreCase = true)) {
                GeminiTimeoutProfile.STREAMING_AUDIT
            } else {
                GeminiTimeoutProfile.FAST_BACKGROUND
            }

        val cleanRequest = if (profileHeader != null) {
            originalRequest.newBuilder().removeHeader(HEADER_TIMEOUT_PROFILE).build()
        } else {
            originalRequest
        }

        val timedChain = chain
            .withConnectTimeout(profile.connectTimeoutMs.toInt(), TimeUnit.MILLISECONDS)
            .withReadTimeout(profile.readTimeoutMs.toInt(), TimeUnit.MILLISECONDS)
            .withWriteTimeout(profile.writeTimeoutMs.toInt(), TimeUnit.MILLISECONDS)

        if (!BuildConfig.DEBUG) {
            return@Interceptor timedChain.proceed(cleanRequest)
        }

        val startNs = System.nanoTime()
        val modelSegment = path.substringAfter("/models/", "gemini")
            .substringBefore(":")

        try {
            val response = timedChain.proceed(cleanRequest)
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            val code = response.code
            if (response.isSuccessful) {
                GlobalConsoleLogger.d(
                    tag = "NETWORK",
                    message = "HTTP $code ${cleanRequest.method} $actionSegment ($modelSegment) за ${durationMs}ms",
                    details = "profile=${profile.name} | host=${cleanRequest.url.host} | protocol=${response.protocol}"
                )
            } else {
                GlobalConsoleLogger.w(
                    tag = "NETWORK",
                    message = "HTTP $code ${cleanRequest.method} $actionSegment ($modelSegment) за ${durationMs}ms",
                    details = "profile=${profile.name} | message=${response.message.ifBlank { "HTTP Error" }}"
                )
            }
            response
        } catch (e: Exception) {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000L
            // Промежуточные сетевые сбои на уровне OkHttp логируем как WARNING, а не ERROR,
            // чтобы не засорять вкладку ERRORS при успешном срабатывании резервной модели в GeminiFallbackExecutor.
            GlobalConsoleLogger.w(
                tag = "NETWORK",
                message = "Сбой попытки HTTP ${cleanRequest.method} $actionSegment ($modelSegment) через ${durationMs}ms: ${e.javaClass.simpleName}",
                details = "profile=${profile.name} (readTimeout=${profile.readTimeoutMs}ms) | ${e.localizedMessage ?: e.message ?: ""}"
            )
            throw e
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(diagnosticTelemetryInterceptor)
        .addInterceptor(HttpLoggingInterceptor { message ->
            GlobalConsoleLogger.d("HTTP", message)
        }.apply {
            redactHeader("x-goog-api-key")
            redactHeader("Authorization")
            redactHeader(HEADER_TIMEOUT_PROFILE)
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

