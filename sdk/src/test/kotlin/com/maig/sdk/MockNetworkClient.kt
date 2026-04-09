package com.maig.sdk

import com.maig.sdk.internal.HttpResponse
import com.maig.sdk.internal.NetworkClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/** Simple mock that returns pre-configured responses in order. */
class MockNetworkClient : NetworkClient {
    val postResponses: MutableList<HttpResponse> = mutableListOf()
    private var callCount = 0

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): HttpResponse {
        val index = callCount.coerceAtMost(postResponses.lastIndex)
        callCount++
        return postResponses[index]
    }

    override fun stream(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): Flow<String> = error("Use a dedicated stream mock for streaming tests")
}

/** Mock whose [post] fails [failTimes] times before returning [successResponse]. */
class RetryMockNetworkClient(
    private val failTimes: Int,
    private val successResponse: HttpResponse,
    private val failureException: Exception = AIGatewayError.NetworkError(Exception("transient")),
) : NetworkClient {
    var callCount = 0
        private set

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): HttpResponse {
        callCount++
        if (callCount <= failTimes) throw failureException
        return successResponse
    }

    override fun stream(url: String, headers: Map<String, String>, body: String): Flow<String> =
        error("Not used in retry tests")
}

/** Mock whose [stream] emits the supplied lines then completes. */
class StreamMockNetworkClient(private val lines: List<String>) : NetworkClient {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse =
        error("Not used in stream tests")

    override fun stream(url: String, headers: Map<String, String>, body: String): Flow<String> =
        lines.asFlow()
}

// Helpers

fun makeChatResponseJson(content: String): String =
    """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""
