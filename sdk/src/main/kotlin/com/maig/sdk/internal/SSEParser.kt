package com.maig.sdk.internal

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

private val gson = Gson()

/**
 * Parses Server-Sent Event (SSE) lines and extracts content token chunks.
 *
 * Each SSE `data:` line is expected to be a JSON object matching the OpenAI streaming format:
 * ```json
 * {"choices":[{"delta":{"content":"token"}}]}
 * ```
 */
internal object SSEParser {

    /**
     * Parse a single raw SSE line and return the content token, or `null` if the line
     * carries no content (keep-alive, `[DONE]`, empty delta, invalid JSON, etc.).
     */
    fun parseLine(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("data: ")) return null
        val payload = trimmed.removePrefix("data: ")
        if (payload == "[DONE]") return null
        return try {
            val chunk = gson.fromJson(payload, SSEChunk::class.java)
            chunk?.choices?.firstOrNull()?.delta?.content?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }
}

// Internal Gson models — mirror the iOS SSEChunk/SSEChoice/SSEDelta hierarchy

private data class SSEChunk(val choices: List<SSEChoice>?)
private data class SSEChoice(val delta: SSEDelta?)
private data class SSEDelta(val content: String?)
