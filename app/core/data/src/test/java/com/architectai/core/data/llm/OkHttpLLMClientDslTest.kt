package com.architectai.core.data.llm

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OkHttpLLMClientDslTest {
    private lateinit var client: OkHttpLLMClient

    @Before
    fun setUp() {
        val prefs = mockk<android.content.SharedPreferences>()
        val editor = mockk<android.content.SharedPreferences.Editor>(relaxed = true)
        every { prefs.getString(any(), any()) } answers {
            // Return the default value argument
            secondArg()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            secondArg()
        }
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } answers { /* no-op */ }

        val config = LLMConfig(prefs)
        client = OkHttpLLMClient(config = config)
    }

    @Test
    fun `tryParseDsl with valid wall script`() {
        val script = """
            // Design: Wall
            group {
                repeat(4) { i -> square(color = RED).at(i * 3, 0) }
            }
        """.trimIndent()
        val result = client.tryParseDsl(script, "build a wall")
        assertNotNull("DSL should parse successfully", result)
        assertTrue("Should return Success", result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals("Wall", composition.name)
        assertEquals(4, composition.tiles.size)
    }

    @Test
    fun `tryParseDsl with simple square`() {
        val script = "square(color = RED).at(5, 5)"
        val result = client.tryParseDsl(script, "a red square")
        assertNotNull(result)
        val composition = (result as LLMResult.Success).composition
        assertEquals(1, composition.tiles.size)
        assertEquals(5, composition.tiles[0].x)
        assertEquals(5, composition.tiles[0].y)
    }

    @Test
    fun `tryParseDsl returns null for invalid script`() {
        val script = "this is not valid DSL at all"
        val result = client.tryParseDsl(script, "test")
        assertNull("Should return null for invalid DSL", result)
    }

    @Test
    fun `tryParseDsl returns null for empty script`() {
        val result = client.tryParseDsl("", "test")
        assertNull(result)
    }

    @Test
    fun `tryParseDsl extracts design name from comment`() {
        val script = """
            // Design: My Cool House
            square(color = BLUE).at(0, 0)
        """.trimIndent()
        val result = client.tryParseDsl(script, "a house")
        assertNotNull(result)
        assertEquals("My Cool House", (result as LLMResult.Success).composition.name)
    }

    @Test
    fun `tryParseDsl falls back to prompt for name`() {
        val script = "square(color = RED).at(0, 0)"
        val result = client.tryParseDsl(script, "build a tower")
        assertNotNull(result)
        // Name should come from prompt when no comment
        val name = (result as LLMResult.Success).composition.name
        assertTrue(name.isNotEmpty())
    }
}
