package com.maig.sdk

/**
 * Errors thrown by [AIGatewayClient].
 */
sealed class AIGatewayError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The API key was rejected (HTTP 401). */
    class AuthFailure : AIGatewayError("Authentication failed. Check your project API key.")

    /** A lower-level network or I/O error occurred. */
    class NetworkError(cause: Throwable) : AIGatewayError("Network error: ${cause.message}", cause)

    /** The gateway returned a non-2xx status code. */
    class ServerError(
        val statusCode: Int,
        val body: String?,
    ) : AIGatewayError("Server error $statusCode${body?.let { ": $it" } ?: ""}")
}
