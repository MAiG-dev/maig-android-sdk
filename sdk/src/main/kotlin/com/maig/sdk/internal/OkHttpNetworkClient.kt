package com.maig.sdk.internal

import com.maig.sdk.AIGatewayError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal class OkHttpNetworkClient(private val okHttpClient: OkHttpClient) : NetworkClient {

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val request = buildRequest(url, headers, body)
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                HttpResponse(statusCode = response.code, body = responseBody)
            }
        } catch (e: AIGatewayError) {
            throw e
        } catch (e: Exception) {
            throw AIGatewayError.NetworkError(e)
        }
    }

    override fun stream(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): Flow<String> = flow {
        val request = buildRequest(url, headers, body)
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.takeIf { it.isNotBlank() }
                    if (response.code == 401) throw AIGatewayError.AuthFailure()
                    throw AIGatewayError.ServerError(response.code, errorBody)
                }
                val source = response.body?.source() ?: return@use
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    emit(line)
                }
            }
        } catch (e: AIGatewayError) {
            throw e
        } catch (e: Exception) {
            throw AIGatewayError.NetworkError(e)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildRequest(url: String, headers: Map<String, String>, body: String): Request {
        val requestBody = body.toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .post(requestBody)
            .build()
    }
}
