package com.maig.sdk

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.maig.sdk.internal.NetworkClient
import com.maig.sdk.internal.OkHttpNetworkClient
import com.maig.sdk.internal.SSEParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Entry point for the MAIG (Mobile AI Gateway) Android SDK.
 *
 * ```kotlin
 * val client = AIGatewayClient(apiKey = "maig_...")
 *
 * // One-shot
 * val text = client.generateText("Summarise this article: ...")
 *
 * // Streaming
 * client.streamText("Tell me a story").collect { token -> print(token) }
 * ```
 *
 * All methods are safe to call from any coroutine context; blocking I/O is dispatched internally
 * to [kotlinx.coroutines.Dispatchers.IO].
 *
 * @param apiKey   Your project API key (starts with `maig_`).
 * @param baseUrl  Override the default gateway URL (`https://api.maig.dev`) — useful for testing against a local instance.
 */
class AIGatewayClient @JvmOverloads constructor(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    networkClient: NetworkClient? = null,
) {
    private val gson = Gson()

    private val httpClient: NetworkClient = networkClient ?: OkHttpNetworkClient(
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    )

    // MARK: - Public API

    /**
     * Makes a non-streaming chat completion request with retry + exponential backoff.
     *
     * @param prompt  The user message to send.
     * @param options Optional generation parameters.
     * @return The model's response text.
     * @throws AIGatewayError on authentication failure, server error, or network error.
     */
    suspend fun generateText(prompt: String, options: GenerateOptions? = null): String {
        val body = buildRequestBody(prompt, options, stream = false)
        val headers = buildHeaders()
        return withRetry {
            val response = httpClient.post("$baseUrl/v1/chat/completions", headers, body)
            validateResponse(response.statusCode, response.body)
            parseTextResponse(response.body)
        }
    }

    /**
     * Opens a streaming chat completion and returns a [Flow] that emits token chunks as they
     * arrive via Server-Sent Events (SSE).
     *
     * The flow completes when the server sends `[DONE]` or the connection closes. Errors
     * (auth failure, server error, network error) are delivered as flow exceptions.
     *
     * ```kotlin
     * client.streamText("Write a haiku").collect { token ->
     *     print(token) // prints each token as it arrives
     * }
     * ```
     *
     * @param prompt  The user message to send.
     * @param options Optional generation parameters.
     */
    fun streamText(prompt: String, options: GenerateOptions? = null): Flow<String> {
        val body = buildRequestBody(prompt, options, stream = true)
        val headers = buildHeaders()
        return httpClient.stream("$baseUrl/v1/chat/completions", headers, body)
            .mapNotNull { line -> SSEParser.parseLine(line) }
    }

    // MARK: - Private helpers

    private fun buildHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json",
    )

    private fun buildRequestBody(prompt: String, options: GenerateOptions?, stream: Boolean): String {
        val request = ChatRequest(
            model = options?.model ?: "auto",
            messages = listOf(Message(role = "user", content = prompt)),
            user = options?.userId,
            maxTokens = options?.maxTokens,
            stream = stream,
        )
        return gson.toJson(request)
    }

    private fun validateResponse(statusCode: Int, body: String) {
        when (statusCode) {
            in 200..299 -> return
            401 -> throw AIGatewayError.AuthFailure()
            else -> throw AIGatewayError.ServerError(statusCode, body.takeIf { it.isNotBlank() })
        }
    }

    private fun parseTextResponse(body: String): String {
        val response = gson.fromJson(body, ChatResponse::class.java)
        return response?.choices?.firstOrNull()?.message?.content ?: ""
    }

    /** Retries [block] up to [MAX_RETRIES] times with exponential backoff (1 s, 2 s). */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Throwable? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                return block()
            } catch (e: AIGatewayError.AuthFailure) {
                throw e // never retry auth failures
            } catch (e: AIGatewayError) {
                lastError = e
            } catch (e: Exception) {
                lastError = AIGatewayError.NetworkError(e)
            }
            if (attempt < MAX_RETRIES) {
                delay((1L shl attempt) * 1_000L) // 1 s, 2 s
            }
        }
        throw lastError!!
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.maig.dev"
        private const val TIMEOUT_SECONDS = 30L
        private const val MAX_RETRIES = 2
    }
}

// MARK: - Gson models

private data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val user: String?,
    @SerializedName("max_tokens") val maxTokens: Int?,
    val stream: Boolean,
)

private data class Message(val role: String, val content: String)

private data class ChatResponse(val choices: List<Choice>?)
private data class Choice(val message: Message?)
