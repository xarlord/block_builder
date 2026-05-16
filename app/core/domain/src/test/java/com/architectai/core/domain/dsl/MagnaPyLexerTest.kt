package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType
import org.junit.Assert.*
import org.junit.Test

class MagnaPyLexerTest {

    // ── Helper ─────────────────────────────────────────────────────────────

    private fun lex(source: String): List<Token> =
        MagnaPyLexer(source).tokenize()

    // ── 1. Tokenize a simple tile ──────────────────────────────────────────

    @Test
    fun `tokenize simple tile with at`() {
        // square(color = RED).at(0, 0)
        val tokens = lex("square(color = RED).at(0, 0)")

        val types = tokens.map { it.type }
        assertTrue(types.contains(DslTokenType.TILE_TYPE))   // square
        assertTrue(types.contains(DslTokenType.LPAREN))
        assertTrue(types.contains(DslTokenType.COLOR_PARAM)) // color
        assertTrue(types.contains(DslTokenType.EQ))
        assertTrue(types.contains(DslTokenType.COLOR))       // RED
        assertTrue(types.contains(DslTokenType.RPAREN))
        assertTrue(types.contains(DslTokenType.DOT))
        assertTrue(types.contains(DslTokenType.AT))          // at
        assertTrue(types.contains(DslTokenType.LPAREN))
        assertTrue(types.contains(DslTokenType.NUMBER))      // 0
        assertTrue(types.contains(DslTokenType.COMMA))
        assertTrue(types.contains(DslTokenType.NUMBER))      // 0
        assertTrue(types.contains(DslTokenType.RPAREN))
        assertTrue(types.last() == DslTokenType.EOF)
    }

    // ── 2. Tokenize a group with multiple tiles ────────────────────────────

    @Test
    fun `tokenize group with multiple tiles`() {
        val source = """
            group {
                square(color = RED).at(0, 0)
                triangle(color = BLUE).at(3, 0)
            }
        """.trimIndent()

        val tokens = lex(source)
        val types = tokens.map { it.type }

        // group keyword
        assertEquals(DslTokenType.GROUP, types[0])
        // two braces
        assertEquals(2, types.count { it == DslTokenType.LBRACE || it == DslTokenType.RBRACE })
        // two tile types
        assertEquals(2, types.count { it == DslTokenType.TILE_TYPE })
        // two colors
        assertEquals(2, types.count { it == DslTokenType.COLOR })
    }

    // ── 3. Tokenize repeat loop ────────────────────────────────────────────

    @Test
    fun `tokenize repeat loop with lambda`() {
        val source = "repeat(6) { i -> square(color = RED) }"
        val tokens = lex(source)
        val types = tokens.map { it.type }

        assertTrue(types.contains(DslTokenType.REPEAT))
        assertTrue(types.contains(DslTokenType.NUMBER))       // 6
        assertTrue(types.contains(DslTokenType.LBRACE))
        assertTrue(types.contains(DslTokenType.IDENTIFIER))   // i
        assertTrue(types.contains(DslTokenType.ARROW))         // ->
        assertTrue(types.contains(DslTokenType.RBRACE))
    }

    // ── 4. Tokenize snap ───────────────────────────────────────────────────

    @Test
    fun `tokenize snapTo expression`() {
        val source = ".snapTo(last, edge = TOP)"
        val tokens = lex(source)
        val types = tokens.map { it.type }

        assertTrue(types.contains(DslTokenType.DOT))
        assertTrue(types.contains(DslTokenType.SNAP_TO))
        assertTrue(types.contains(DslTokenType.LPAREN))
        assertTrue(types.contains(DslTokenType.LAST))
        assertTrue(types.contains(DslTokenType.COMMA))
        assertTrue(types.contains(DslTokenType.EDGE_PARAM)) // edge
        assertTrue(types.contains(DslTokenType.EQ))
        assertTrue(types.contains(DslTokenType.EDGE))       // TOP
        assertTrue(types.contains(DslTokenType.RPAREN))
    }

    // ── 5. Tokenize val declaration ────────────────────────────────────────

    @Test
    fun `tokenize val declaration`() {
        val source = "val face = group { square(color = RED) }"
        val tokens = lex(source)
        val types = tokens.map { it.type }

        assertEquals(DslTokenType.VAL, types[0])
        // "face" is classified as IDENTIFIER
        assertEquals(DslTokenType.IDENTIFIER, types[1])
        assertEquals("face", tokens[1].value)
        assertEquals(DslTokenType.EQ, types[2])
        assertTrue(types.contains(DslTokenType.GROUP))
    }

    // ── 6. Tokenize plus ───────────────────────────────────────────────────

    @Test
    fun `tokenize plus expression`() {
        val source = "face + mane"
        val tokens = lex(source)
        val types = tokens.map { it.type }

        assertEquals(DslTokenType.IDENTIFIER, types[0])
        assertEquals("face", tokens[0].value)
        assertEquals(DslTokenType.PLUS, types[1])
        assertEquals(DslTokenType.IDENTIFIER, types[2])
        assertEquals("mane", tokens[2].value)
    }

    // ── 7. Tokenize comments (should be skipped) ───────────────────────────

    @Test
    fun `comments are skipped`() {
        val source = "square(color = RED) // this is a comment"
        val tokens = lex(source)

        // No token should contain comment text
        assertFalse(tokens.any { it.value.contains("comment") })
        // The tile tokens should still be present
        assertTrue(tokens.any { it.type == DslTokenType.TILE_TYPE })
        assertTrue(tokens.any { it.type == DslTokenType.COLOR })
    }

    @Test
    fun `multiline comments are skipped`() {
        val source = """
            // first line comment
            square(color = RED)
            // second line comment
        """.trimIndent()
        val tokens = lex(source)

        assertFalse(tokens.any { it.value.contains("comment") })
        assertFalse(tokens.any { it.value.contains("first") })
        assertFalse(tokens.any { it.value.contains("second") })
        assertTrue(tokens.any { it.type == DslTokenType.TILE_TYPE })
    }

    // ── 8. Tokenize all color names ────────────────────────────────────────

    @Test
    fun `tokenize all color names`() {
        val colorNames = TileColor.entries.map { it.name }
        for (name in colorNames) {
            val tokens = lex(name)
            assertEquals(
                "Expected COLOR token for '$name'",
                DslTokenType.COLOR,
                tokens.first().type
            )
            assertEquals(name, tokens.first().value)
        }
    }

    // ── 9. Tokenize all tile types ─────────────────────────────────────────

    @Test
    fun `tokenize all tile type keywords`() {
        val tileKeywords = mapOf(
            "square" to TileType.SOLID_SQUARE,
            "triangle" to TileType.EQUILATERAL_TRIANGLE,
            "rightTriangle" to TileType.RIGHT_TRIANGLE,
            "isoscelesTriangle" to TileType.ISOSCELES_TRIANGLE,
            "windowSquare" to TileType.WINDOW_SQUARE
        )
        for ((keyword, _) in tileKeywords) {
            val tokens = lex(keyword)
            assertEquals(
                "Expected TILE_TYPE token for '$keyword'",
                DslTokenType.TILE_TYPE,
                tokens.first().type
            )
            assertEquals(keyword, tokens.first().value)
        }
    }

    // ── 10. Tokenize all edge names ────────────────────────────────────────

    @Test
    fun `tokenize all edge names`() {
        val edgeNames = SnapEdge.entries.map { it.name }
        for (name in edgeNames) {
            val tokens = lex(name)
            assertEquals(
                "Expected EDGE token for '$name'",
                DslTokenType.EDGE,
                tokens.first().type
            )
            assertEquals(name, tokens.first().value)
        }
    }

    // ── 11. Error on unexpected character ───────────────────────────────────

    @Test(expected = DslError.LexError::class)
    fun `error on unexpected character`() {
        lex("@")
    }

    @Test
    fun `lex error contains position info`() {
        try {
            lex("square(color = RED) @")
            fail("Expected LexError")
        } catch (e: DslError.LexError) {
            assertTrue(e.message!!.contains("Unexpected character '@'"))
        }
    }

    // ── 12. Error on unterminated string ────────────────────────────────────

    @Test(expected = DslError.LexError::class)
    fun `error on unterminated string`() {
        lex("\"unterminated")
    }

    @Test
    fun `unterminated string error message`() {
        try {
            lex("\"hello")
            fail("Expected LexError")
        } catch (e: DslError.LexError) {
            assertTrue(e.message!!.contains("Unterminated string"))
        }
    }

    // ── 13. Companion object lookups ────────────────────────────────────────

    @Test
    fun `colorForName returns correct TileColor`() {
        for (color in TileColor.entries) {
            assertEquals(color, MagnaPyLexer.colorForName(color.name))
        }
    }

    @Test
    fun `colorForName returns null for unknown name`() {
        assertNull(MagnaPyLexer.colorForName("NOT_A_COLOR"))
    }

    @Test
    fun `tileTypeForName returns correct TileType`() {
        assertEquals(TileType.SOLID_SQUARE, MagnaPyLexer.tileTypeForName("square"))
        assertEquals(TileType.EQUILATERAL_TRIANGLE, MagnaPyLexer.tileTypeForName("triangle"))
        assertEquals(TileType.RIGHT_TRIANGLE, MagnaPyLexer.tileTypeForName("rightTriangle"))
        assertEquals(TileType.ISOSCELES_TRIANGLE, MagnaPyLexer.tileTypeForName("isoscelesTriangle"))
        assertEquals(TileType.WINDOW_SQUARE, MagnaPyLexer.tileTypeForName("windowSquare"))
    }

    @Test
    fun `tileTypeForName returns null for unknown name`() {
        assertNull(MagnaPyLexer.tileTypeForName("hexagon"))
        assertNull(MagnaPyLexer.tileTypeForName("SQUARE"))  // case-sensitive
    }

    @Test
    fun `edgeForName returns correct SnapEdge`() {
        for (edge in SnapEdge.entries) {
            assertEquals(edge, MagnaPyLexer.edgeForName(edge.name))
        }
    }

    @Test
    fun `edgeForName returns null for unknown name`() {
        assertNull(MagnaPyLexer.edgeForName("DIAGONAL"))
    }

    // ── Token position tracking ────────────────────────────────────────────

    @Test
    fun `tokens have correct positions`() {
        val tokens = lex("a + b")
        // Token "a" at line 1, column 1
        assertEquals(1, tokens[0].line)
        assertEquals(1, tokens[0].column)
        // Token "+" at line 1, column 3
        assertEquals(1, tokens[1].line)
        assertEquals(3, tokens[1].column)
        // Token "b" at line 1, column 5
        assertEquals(1, tokens[2].line)
        assertEquals(5, tokens[2].column)
    }

    @Test
    fun `EOF token is always last`() {
        val tokens = lex("square(color = RED)")
        assertEquals(DslTokenType.EOF, tokens.last().type)
        assertEquals("", tokens.last().value)
    }

    @Test
    fun `tokenize axis names`() {
        val tokens = lex("HORIZONTAL VERTICAL")
        assertEquals(DslTokenType.AXIS, tokens[0].type)
        assertEquals("HORIZONTAL", tokens[0].value)
        assertEquals(DslTokenType.AXIS, tokens[1].type)
        assertEquals("VERTICAL", tokens[1].value)
    }

    @Test
    fun `tokenize string literal`() {
        val tokens = lex("\"hello world\"")
        assertEquals(DslTokenType.STRING, tokens[0].type)
        assertEquals("hello world", tokens[0].value)
    }

    @Test
    fun `tokenize string with escaped quote`() {
        val tokens = lex("\"hello \\\"world\\\"\"")
        assertEquals(DslTokenType.STRING, tokens[0].type)
        assertEquals("hello \"world\"", tokens[0].value)
    }
}
