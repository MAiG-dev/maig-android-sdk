package com.maig.sdk.internal

import kotlinx.coroutines.flow.Flow

/**
 * Abstracts HTTP transport so [com.maig.sdk.AIGatewayClient] can be tested without a live server.
 */
internal interface NetworkClient {
    /**
     * Performs a synchronous POST and returns the response.
     * Throws [com.maig.sdk.AIGatewayError] on non-2xx status or I/O failure.
     */
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpResponse

    /**
     * Opens a streaming POST and emits raw response lines as they arrive.
     * Throws [com.maig.sdk.AIGatewayError] if the server returns a non-2xx status before any
     * lines are emitted.
     */
    fun stream(url: String, headers: Map<String, String>, body: String): Flow<String>
}

internal data class HttpResponse(val statusCode: Int, val body: String)
