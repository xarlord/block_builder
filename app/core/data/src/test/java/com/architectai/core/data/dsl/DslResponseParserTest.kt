package com.architectai.core.data.dsl

import org.junit.Assert.*
import org.junit.Test

class DslResponseParserTest {

    @Test
    fun `extract DSL from kotlin code block`() {
        val response = """
            Here's your design:
            ```kotlin
            // Design: Wall
            group {
                repeat(4) { i -> square(color = RED).at(i * 3, 0) }
            }
            ```
        """.trimIndent()
        val script = DslResponseParser.extractDslScript(response)
        assertNotNull(script)
        assertTrue(script!!.contains("square"))
        assertTrue(script.contains("repeat"))
    }

    @Test
    fun `extract DSL from generic code block`() {
        val response = """
            ```
            square(color = RED).at(0, 0)
            ```
        """.trimIndent()
        val script = DslResponseParser.extractDslScript(response)
        assertNotNull(script)
        assertTrue(script!!.contains("square"))
    }

    @Test
    fun `extract DSL from raw content`() {
        val response = "square(color = RED).at(0, 0)"
        val script = DslResponseParser.extractDslScript(response)
        assertNotNull(script)
        assertEquals("square(color = RED).at(0, 0)", script)
    }

    @Test
    fun `returns null for blank content`() {
        assertNull(DslResponseParser.extractDslScript("   "))
        assertNull(DslResponseParser.extractDslScript(""))
    }

    @Test
    fun `looksLikeDsl detects kotlin code block`() {
        val response = "```kotlin\nsquare(color = RED)\n```"
        assertTrue(DslResponseParser.looksLikeDsl(response))
    }

    @Test
    fun `looksLikeDsl detects DSL keywords`() {
        val response = "Use square(color = RED).at(0, 0) and triangle(color = BLUE)"
        assertTrue(DslResponseParser.looksLikeDsl(response))
    }

    @Test
    fun `looksLikeDsl returns false for JSON`() {
        val response = """{"object": "house", "components": [{"tile_id": "solid_square"}]}"""
        assertFalse(DslResponseParser.looksLikeDsl(response))
    }

    @Test
    fun `looksLikeDsl returns false for plain text`() {
        assertFalse(DslResponseParser.looksLikeDsl("Hello, how are you?"))
    }
}
