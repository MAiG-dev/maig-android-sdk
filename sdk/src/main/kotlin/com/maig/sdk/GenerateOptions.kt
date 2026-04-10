package com.maig.sdk

data class GenerateOptions(
    val model: String? = null,
    val userId: String? = null,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val stop: List<String>? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val seed: Int? = null,
    val responseFormat: ResponseFormat? = null,
)

enum class ResponseFormat { TEXT, JSON_OBJECT }
