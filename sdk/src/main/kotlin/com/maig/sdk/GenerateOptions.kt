package com.maig.sdk

/**
 * Optional parameters for a generation request.
 *
 * @property model  Model identifier to route to (e.g. `"gpt-4o"`, `"claude-3-5-sonnet"`).
 *                  Defaults to `"auto"` (gateway selects the best available model).
 * @property userId Stable identifier for the end user; used for per-user rate limiting and analytics.
 * @property maxTokens Maximum number of tokens to generate.
 */
data class GenerateOptions(
    val model: String? = null,
    val userId: String? = null,
    val maxTokens: Int? = null,
)
