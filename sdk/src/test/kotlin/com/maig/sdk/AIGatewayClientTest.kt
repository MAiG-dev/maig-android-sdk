package com.maig.sdk

import com.maig.sdk.internal.HttpResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class AIGatewayClientTest {

    // MARK: - generateText success

    @Test
    fun `generateText returns response content`() = runTest {
        val mock = MockNetworkClient()
        mock.postResponses += HttpResponse(200, makeChatResponseJson("Hello there!"))

        val client = AIGatewayClient(apiKey = "maig_test", networkClient = mock)
        assertEquals("Hello there!", client.generateText("Hello"))
    }

    // MARK: - Auth failure (no retry)

    @Test
    fun `generateText throws AuthFailure on 401`() = runTest {
        val mock = MockNetworkClient()
        mock.postResponses += HttpResponse(401, "")

        val client = AIGatewayClient(apiKey = "maig_bad", networkClient = mock)
        try {
            client.generateText("Hello")
            fail("Expected AuthFailure")
        } catch (e: AIGatewayError.AuthFailure) {
            // expected
        }
    }

    // MARK: - Server error

    @Test
    fun `generateText throws ServerError on 500`() = runTest {
        val errorResponse = HttpResponse(500, "Internal Server Error")
        val mock = MockNetworkClient()
        // Enough responses to cover 1 attempt + 2 retries
        repeat(3) { mock.postResponses += errorResponse }

        val client = AIGatewayClient(apiKey = "maig_test", networkClient = mock)
        try {
            client.generateText("Hello")
            fail("Expected ServerError")
        } catch (e: AIGatewayError.ServerError) {
            assertEquals(500, e.statusCode)
        }
    }

    // MARK: - Retry behaviour

    @Test
    fun `generateText retries on transient network error and succeeds`() = runTest {
        val successResponse = HttpResponse(200, makeChatResponseJson("Retried!"))
        val retryMock = RetryMockNetworkClient(failTimes = 2, successResponse = successResponse)

        val client = AIGatewayClient(apiKey = "maig_test", networkClient = retryMock)
        val result = client.generateText("Hello")

        assertEquals("Retried!", result)
        assertEquals(3, retryMock.callCount) // 1 initial + 2 retries
    }

    @Test
    fun `generateText does not retry AuthFailure`() = runTest {
        val mock = MockNetworkClient()
        mock.postResponses += HttpResponse(401, "")

        val client = AIGatewayClient(apiKey = "maig_bad", networkClient = mock)
        try {
            client.generateText("Hello")
            fail("Expected AuthFailure")
        } catch (_: AIGatewayError.AuthFailure) {
            // Verify only one call was made (no retry)
        }
        // MockNetworkClient consumed exactly 1 response
        assertEquals(0, mock.postResponses.size.also {}) // all consumed
    }

    // MARK: - streamText

    @Test
    fun `streamText emits parsed token chunks`() = runTest {
        val lines = listOf(
            """data: {"choices":[{"delta":{"content":"Hel"}}]}""",
            """data: {"choices":[{"delta":{"content":"lo"}}]}""",
            "data: [DONE]",
        )
        val streamMock = StreamMockNetworkClient(lines)
        val client = AIGatewayClient(apiKey = "maig_test", networkClient = streamMock)

        val tokens = client.streamText("Hi").toList()
        assertEquals(listOf("Hel", "lo"), tokens)
    }

    @Test
    fun `streamText filters out non-content SSE lines`() = runTest {
        val lines = listOf(
            ": keep-alive",
            """data: {"choices":[{"delta":{"content":"World"}}]}""",
            "data: [DONE]",
        )
        val streamMock = StreamMockNetworkClient(lines)
        val client = AIGatewayClient(apiKey = "maig_test", networkClient = streamMock)

        val tokens = client.streamText("Hi").toList()
        assertEquals(listOf("World"), tokens)
    }
}
