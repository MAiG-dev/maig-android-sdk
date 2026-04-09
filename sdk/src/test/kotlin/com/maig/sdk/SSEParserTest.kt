package com.maig.sdk

import com.maig.sdk.internal.SSEParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SSEParserTest {

    @Test
    fun `parseLine extracts content from a valid data line`() {
        val line = """data: {"choices":[{"delta":{"content":"Hel"}}]}"""
        assertEquals("Hel", SSEParser.parseLine(line))
    }

    @Test
    fun `parseLine returns null for DONE marker`() {
        assertNull(SSEParser.parseLine("data: [DONE]"))
    }

    @Test
    fun `parseLine returns null for non-data lines`() {
        assertNull(SSEParser.parseLine(": keep-alive"))
        assertNull(SSEParser.parseLine("event: message"))
        assertNull(SSEParser.parseLine(""))
    }

    @Test
    fun `parseLine returns null for empty content`() {
        val line = """data: {"choices":[{"delta":{"content":""}}]}"""
        assertNull(SSEParser.parseLine(line))
    }

    @Test
    fun `parseLine returns null for null content field`() {
        val line = """data: {"choices":[{"delta":{}}]}"""
        assertNull(SSEParser.parseLine(line))
    }

    @Test
    fun `parseLine returns null for invalid JSON`() {
        assertNull(SSEParser.parseLine("data: not-json"))
    }

    @Test
    fun `parseLine handles leading whitespace`() {
        val line = """  data: {"choices":[{"delta":{"content":"Hi"}}]}"""
        assertEquals("Hi", SSEParser.parseLine(line))
    }
}
