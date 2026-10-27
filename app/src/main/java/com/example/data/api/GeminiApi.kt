package com.example.data.api

import com.example.BuildConfig
import com.example.utils.GlobalConsoleLogger
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
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
                GlobalConsoleLogger.w(
                    tag = "NETWORK",
                    message = "HTTP $code ${request.method} $actionSegment ($modelSegment) за ${durationMs}ms",
                    details = "message=${response.message.ifBlank { "HTTP Error" }}"
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
