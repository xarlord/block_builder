package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType

// ─────────────────────────────────────────────────────────────────────────────
// MagnaPy DSL — Lexer / Tokeniser
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Every token type the MagnaPy lexer can emit.
 */
enum class DslTokenType {
    // Literals
    NUMBER,
    IDENTIFIER,
    STRING,

    // Semantic categories
    COLOR,
    TILE_TYPE,
    EDGE,
    AXIS,

    // Keywords
    VAL,
    GROUP,
    REPEAT,
    RADIAL,
    MIRROR,
    SNAP_TO,
    AT,
    ROTATE,
    COLOR_PARAM,
    EDGE_PARAM,
    AXIS_PARAM,
    COUNT_PARAM,
    LAST,
    IT,

    // Operators
    PLUS,
    MINUS,
    DOT,
    ARROW,
    EQ,
    TIMES,

    // Delimiters
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    COMMA,

    // Sentinel
    EOF
}

/**
 * A single token produced by the lexer.
 *
 * @param type   The category of this token.
 * @param value  The exact text that was matched in the source.
 * @param line   1-based line number.
 * @param column 1-based column number (of the first character).
 */
data class Token(
    val type: DslTokenType,
    val value: String,
    val line: Int,
    val column: Int
)

/**
 * Production-quality lexer for the MagnaPy Block Builder DSL.
 *
 * Usage:
 * ```
 * val tokens = MagnaPyLexer(source).tokenize()
 * ```
 */
class MagnaPyLexer(private val source: String) {

    // ── Internal state ──────────────────────────────────────────────────────

    private var pos = 0          // current index into [source]
    private var line = 1         // 1-based line
    private var column = 1       // 1-based column
    private val tokens = mutableListOf<Token>()

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Tokenise the full source string and return the resulting token list
     * (including a trailing EOF).
     *
     * @throws DslError.LexError on unexpected characters.
     */
    fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            skipWhitespaceAndComments()
            if (isAtEnd()) break

            val startLine = line
            val startCol = column
            val ch = peek()

            when {
                // ── Multi-char symbols ────────────────────────────────────
                matchMultiCharSymbol(startLine, startCol) -> { /* handled */ }

                // ── Single-char symbols ──────────────────────────────────
                ch == '+'  -> { addToken(DslTokenType.PLUS,  ch.toString(), startLine, startCol) }
                ch == '-'  -> { addToken(DslTokenType.MINUS, ch.toString(), startLine, startCol) }
                ch == '*'  -> { addToken(DslTokenType.TIMES, ch.toString(), startLine, startCol) }
                ch == '.'  -> { addToken(DslTokenType.DOT,   ch.toString(), startLine, startCol) }
                ch == '='  -> { addToken(DslTokenType.EQ,    ch.toString(), startLine, startCol) }
                ch == '('  -> { addToken(DslTokenType.LPAREN, ch.toString(), startLine, startCol) }
                ch == ')'  -> { addToken(DslTokenType.RPAREN, ch.toString(), startLine, startCol) }
                ch == '{'  -> { addToken(DslTokenType.LBRACE, ch.toString(), startLine, startCol) }
                ch == '}'  -> { addToken(DslTokenType.RBRACE, ch.toString(), startLine, startCol) }
                ch == ','  -> { addToken(DslTokenType.COMMA,  ch.toString(), startLine, startCol) }

                // ── String literals ──────────────────────────────────────
                ch == '"'  -> readString(startLine, startCol)

                // ── Numbers ──────────────────────────────────────────────
                ch.isDigit() -> readNumber(startLine, startCol)

                // ── Identifiers / keywords ───────────────────────────────
                ch.isIdentifierStart() -> readIdentifierOrKeyword(startLine, startCol)

                // ── Unknown character ────────────────────────────────────
                else -> throw DslError.LexError(
                    "Unexpected character '$ch'",
                    startLine,
                    startCol
                )
            }
        }

        // ── Post-processing: convert quoted color strings and #hex literals ──
        normalizeColorStrings(tokens)

        tokens.add(Token(DslTokenType.EOF, "", line, column))
        return tokens.toList()
    }

    // ── Whitespace & comments ───────────────────────────────────────────────

    private fun skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            val ch = peek()
            when {
                ch == '\n'      -> { advance(); line++; column = 1 }
                ch.isWhitespace() -> advance()
                ch == '/' && peekNext() == '/' -> skipLineComment()
                else -> break
            }
        }
    }

    private fun skipLineComment() {
        // Consume the '//'
        advance() // first '/'
        advance() // second '/'
        // Consume everything until end-of-line or end-of-input
        while (!isAtEnd() && peek() != '\n') {
            advance()
        }
    }

    // ── Multi-char symbol matching ──────────────────────────────────────────

    /**
     * Try to match any of the multi-char symbols (e.g. "->").
     * Returns `true` if a symbol was matched and a token emitted.
     */
    private fun matchMultiCharSymbol(startLine: Int, startCol: Int): Boolean {
        for ((lexeme, type) in MULTI_CHAR_SYMBOLS) {
            if (source.regionMatches(pos, lexeme, 0, lexeme.length)) {
                val text = source.substring(pos, pos + lexeme.length)
                repeat(lexeme.length) { advance() }
                tokens.add(Token(type, text, startLine, startCol))
                return true
            }
        }
        return false
    }

    // ── Number literal ──────────────────────────────────────────────────────

    private fun readNumber(startLine: Int, startCol: Int) {
        val start = pos
        while (!isAtEnd() && peek().isDigit()) advance()
        val text = source.substring(start, pos)
        tokens.add(Token(DslTokenType.NUMBER, text, startLine, startCol))
    }

    // ── String literal ──────────────────────────────────────────────────────

    private fun readString(startLine: Int, startCol: Int) {
        val sb = StringBuilder()
        advance() // opening quote
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\\') {
                advance() // skip backslash
                if (!isAtEnd()) sb.append(peek())
                advance()
            } else {
                sb.append(peek())
                advance()
            }
        }
        if (isAtEnd()) {
            throw DslError.LexError("Unterminated string literal", startLine, startCol)
        }
        advance() // closing quote
        tokens.add(Token(DslTokenType.STRING, sb.toString(), startLine, startCol))
    }

    // ── Identifiers & keywords ──────────────────────────────────────────────

    private fun readIdentifierOrKeyword(startLine: Int, startCol: Int) {
        val start = pos
        while (!isAtEnd() && peek().isIdentifierPart()) advance()
        val text = source.substring(start, pos)

        val tokenType = classifyWord(text)
        // Normalize color names to UPPERCASE so TileColor.valueOf(token.value) works
        val normalizedValue = if (tokenType == DslTokenType.COLOR) text.uppercase() else text
        tokens.add(Token(tokenType, normalizedValue, startLine, startCol))
    }

    /**
     * Map a word to the most specific [DslTokenType].
     */
    private fun classifyWord(word: String): DslTokenType {
        // ── Tile-type keywords (lowercase) ───────────────────────────────
        if (word in TILE_TYPE_KEYWORDS) return DslTokenType.TILE_TYPE

        // ── Colour names (case-insensitive) ────────────────────────────
        if (word in COLOR_NAMES || word.uppercase() in COLOR_NAMES) return DslTokenType.COLOR

        // ── Edge names (UPPERCASE) ──────────────────────────────────────
        if (word in EDGE_NAMES) return DslTokenType.EDGE

        // ── Axis names (UPPERCASE) ──────────────────────────────────────
        if (word in AXIS_NAMES) return DslTokenType.AXIS

        // ── Other keywords (lowercase) ──────────────────────────────────
        return when (word) {
            "val"   -> DslTokenType.VAL
            "group" -> DslTokenType.GROUP
            "repeat" -> DslTokenType.REPEAT
            "radial" -> DslTokenType.RADIAL
            "mirror" -> DslTokenType.MIRROR
            "snapTo" -> DslTokenType.SNAP_TO
            "at"     -> DslTokenType.AT
            "rotate" -> DslTokenType.ROTATE
            "color"  -> DslTokenType.COLOR_PARAM
            "edge"   -> DslTokenType.EDGE_PARAM
            "axis"   -> DslTokenType.AXIS_PARAM
            "count"  -> DslTokenType.COUNT_PARAM
            "last"   -> DslTokenType.LAST
            "it"     -> DslTokenType.IT
            else     -> DslTokenType.IDENTIFIER
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun isAtEnd(): Boolean = pos >= source.length

    private fun peek(): Char = source[pos]

    private fun peekNext(): Char = if (pos + 1 < source.length) source[pos + 1] else '\u0000'

    private fun advance() {
        pos++
        column++
    }

    private fun addToken(type: DslTokenType, text: String, line: Int, col: Int) {
        advance()
        tokens.add(Token(type, text, line, col))
    }

    // ── Character classification ────────────────────────────────────────────

    private fun Char.isIdentifierStart(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this == '_'

    private fun Char.isIdentifierPart(): Boolean =
        isIdentifierStart() || this in '0'..'9'

    // ── Post-processing helpers ────────────────────────────────────────────

    /**
     * Convert STRING tokens to COLOR tokens when their value is a recognised
     * colour name (case-insensitive) or a `#hex` colour literal.
     *
     * This handles LLM quirks where colours are emitted as quoted strings
     * like `"red"` or `"#A04523"` instead of bare identifiers.
     */
    private fun normalizeColorStrings(tokens: MutableList<Token>) {
        for (i in tokens.indices) {
            val tok = tokens[i]
            if (tok.type != DslTokenType.STRING) continue

            val value = tok.value

            // ── #hex colour literal ──────────────────────────────────────
            if (value.startsWith("#")) {
                val hexKey = value.removePrefix("#").uppercase()
                COLOR_HEX_MAP[hexKey]?.let { colorName ->
                    tokens[i] = tok.copy(type = DslTokenType.COLOR, value = colorName)
                }
                continue
            }

            // ── Named colour (case-insensitive) ─────────────────────────
            val upper = value.uppercase()
            if (upper in COLOR_NAMES) {
                tokens[i] = tok.copy(type = DslTokenType.COLOR, value = upper)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Companion — lookup tables + public helpers
    // ═══════════════════════════════════════════════════════════════════════

    companion object {

        // ── Lookup tables ────────────────────────────────────────────────

        /** Tile-shape keywords → TileType. */
        internal val TILE_TYPE_KEYWORDS: Map<String, TileType> = mapOf(
            "square"            to TileType.SOLID_SQUARE,
            "triangle"          to TileType.EQUILATERAL_TRIANGLE,
            "rightTriangle"     to TileType.RIGHT_TRIANGLE,
            "isoscelesTriangle" to TileType.ISOSCELES_TRIANGLE,
            "windowSquare"      to TileType.WINDOW_SQUARE
        )

        /** Uppercase colour names → COLOR token. */
        internal val COLOR_NAMES: Map<String, TileColor> =
            TileColor.entries.associateBy { it.name }

        /** Hex colour code (without #) → UPPERCASE colour name. */
        internal val COLOR_HEX_MAP: Map<String, String> =
            TileColor.entries.associate { it.hex.removePrefix("#").uppercase() to it.name }

        /** Uppercase edge names → EDGE token. */
        internal val EDGE_NAMES: Set<String> =
            SnapEdge.entries.map { it.name }.toSet()

        /** Uppercase axis names → AXIS token. */
        internal val AXIS_NAMES: Set<String> = setOf("HORIZONTAL", "VERTICAL")

        /** All multi-char operator / punctuation sequences (longest first). */
        internal val MULTI_CHAR_SYMBOLS: List<Pair<String, DslTokenType>> = listOf(
            "->" to DslTokenType.ARROW
        )

        // ── Public lookup helpers ────────────────────────────────────────

        /**
         * Look up a [TileColor] by its DSL name (e.g. `"RED" → TileColor.RED`).
         * Returns `null` if [name] is not a recognised colour.
         */
        fun colorForName(name: String): TileColor? =
            TileColor.entries.find { it.name == name }

        /**
         * Look up a [TileType] by its DSL keyword
         * (e.g. `"square" → TileType.SOLID_SQUARE`).
         * Returns `null` if [name] is not a recognised tile-type keyword.
         */
        fun tileTypeForName(name: String): TileType? =
            TILE_TYPE_KEYWORDS[name]

        /**
         * Look up a [SnapEdge] by its DSL name (e.g. `"TOP_LEFT" → SnapEdge.TOP_LEFT`).
         * Returns `null` if [name] is not a recognised edge.
         */
        fun edgeForName(name: String): SnapEdge? =
            SnapEdge.entries.find { it.name == name }
    }
}
