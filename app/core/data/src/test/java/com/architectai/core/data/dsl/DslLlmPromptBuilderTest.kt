package com.architectai.core.data.dsl

import org.junit.Assert.*
import org.junit.Test

class DslLlmPromptBuilderTest {
    private val builder = DslLlmPromptBuilder()
    private val prompt = builder.buildSystemPrompt()

    @Test
    fun `prompt contains tile types`() {
        assertTrue(prompt.contains("square"))
        assertTrue(prompt.contains("triangle"))
        assertTrue(prompt.contains("rightTriangle"))
    }

    @Test
    fun `prompt contains colors`() {
        assertTrue(prompt.contains("RED"))
        assertTrue(prompt.contains("BLUE"))
        assertTrue(prompt.contains("YELLOW"))
    }

    @Test
    fun `prompt contains grid constraints`() {
        assertTrue(prompt.contains("30"))
        assertTrue(prompt.contains("200"))
    }

    @Test
    fun `prompt contains DSL syntax keywords`() {
        assertTrue(prompt.contains("snapTo"))
        assertTrue(prompt.contains("repeat"))
        assertTrue(prompt.contains("group"))
        assertTrue(prompt.contains(".at("))
        assertTrue(prompt.contains(".rotate("))
    }

    @Test
    fun `prompt contains kotlin code block instruction`() {
        assertTrue(prompt.contains("kotlin"))
    }

    @Test
    fun `prompt contains examples`() {
        assertTrue(prompt.contains("Wall") || prompt.contains("Flower") || prompt.contains("Bridge"))
    }

    @Test
    fun `prompt is not empty`() {
        assertTrue(prompt.isNotBlank())
        assertTrue(prompt.length > 200) // Should be substantial
    }
}
