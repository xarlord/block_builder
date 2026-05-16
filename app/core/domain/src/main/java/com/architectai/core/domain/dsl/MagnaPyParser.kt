package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.Axis
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType

// ─────────────────────────────────────────────────────────────────────────────
// MagnaPy DSL — Recursive Descent Parser
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Production-quality recursive descent parser for the MagnaPy DSL.
 *
 * Takes a [List]<[Token]> from the [MagnaPyLexer] and produces a [BlockExpr]
 * AST representing the full program.
 *
 * ### Grammar (informal)
 * ```
 * program      := statement*
 * statement    := varDecl | expr
 * varDecl      := 'val' IDENTIFIER '=' expr
 * expr         := methodOrPlus
 * methodOrPlus := tileOrPrimary (('.' methodChain) | ('+' tileOrPrimary))*
 * methodChain  := 'at' '(' expr ',' expr ')'
 *               | 'snapTo' '(' expr ',' 'edge' '=' EDGE ')'
 *               | 'rotate' '(' expr ')'
 * tileOrPrimary := tileLiteral | groupExpr | repeatExpr | radialExpr
 *                | mirrorExpr | 'last' | 'it' | IDENTIFIER | '(' expr ')'
 * ```
 *
 * Operator precedence (low → high):
 * 1. `+` (composition / addition)
 * 2. `*`, `%` (multiplicative)
 * 3. Unary `-`
 * 4. Primary (numbers, identifiers, parenthesised expressions)
 *
 * ### Usage
 * ```kotlin
 * val tokens = MagnaPyLexer(source).tokenize()
 * val ast = MagnaPyParser(tokens).parse().getOrDefault(BlockExpr(emptyList(), null))
 * ```
 */
class MagnaPyParser(private val tokens: List<Token>) {

    // ── Internal state ──────────────────────────────────────────────────────

    /** Current read position in the [tokens] list. */
    private var pos = 0

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Parse the full token list and return a [Result] containing the root
     * [BlockExpr] (which wraps all top-level statements), or a failure
     * containing a [DslError.ParseError].
     */
    fun parse(): Result<BlockExpr> = runCatching {
        val statements = mutableListOf<DslNode>()
        var lastError: Exception? = null
        while (!isAtEnd()) {
            try {
                val stmt = parseStatement()
                statements.add(stmt)
            } catch (e: Exception) {
                // Skip to next statement on error (skip to next line or next 'val')
                lastError = e
                skipToNextStatement()
            }
        }
        // If we recovered errors but produced zero statements, re-throw
        if (statements.isEmpty() && lastError != null) {
            throw lastError
        }
        BlockExpr(statements.toList(), result = null)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Statement-level parsing
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ```
     * statement := varDecl | expr
     * varDecl   := 'val' IDENTIFIER '=' expr
     * ```
     */
    private fun parseStatement(): DslNode {
        // ── Variable declaration ────────────────────────────────────────
        if (peekType() == DslTokenType.VAL) {
            return parseVarDecl()
        }
        // ── Lookahead: IDENTIFIER followed by EQ = implicit varDecl ────
        // Handles LLM output like: b1 = square(color = RED).at(0, 0)
        if (peekType() == DslTokenType.IDENTIFIER && lookaheadType(1) == DslTokenType.EQ) {
            return parseImplicitVarDecl()
        }
        // ── Otherwise it's a bare expression ────────────────────────────
        return parseExpression()
    }

    /**
     * Parse `val IDENTIFIER '=' expr` and return a [VarDecl].
     */
    private fun parseVarDecl(): VarDecl {
        consume(DslTokenType.VAL, "Expected 'val'")
        val name = consume(DslTokenType.IDENTIFIER, "Expected identifier after 'val'").value
        consume(DslTokenType.EQ, "Expected '=' after variable name")
        val value = parseExpression()
        return VarDecl(name, value)
    }

    /**
     * Parse an implicit variable declaration without the `val` keyword:
     * `IDENTIFIER '=' expr` (e.g. `b1 = square(color = RED).at(0, 0)`).
     *
     * This handles LLM output that omits the `val` keyword.
     */
    private fun parseImplicitVarDecl(): VarDecl {
        val name = advance().value  // consume IDENTIFIER
        advance() // consume '='
        val value = parseExpression()
        return VarDecl(name, value)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Expression-level parsing (precedence climbing)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Top-level expression: handles method chaining (`.`) and the `+`
     * composition operator (left-associative).
     *
     * The `+` operator has the *lowest* precedence. Method chains bind
     * tighter, so `square(RED).at(0, 0) + triangle(BLUE)` parses as
     * `PlusExpr(AtExpr(...), TileLiteral(...))`.
     */
    private fun parseExpression(): DslNode {
        var node = parseMethodChainOrTile()

        while (peekType() == DslTokenType.PLUS) {
            advance() // consume '+'
            val right = parseMethodChainOrTile()
            node = PlusExpr(node, right)
        }

        return node
    }

    /**
     * Parse a tile-or-primary followed by zero or more `.methodCall(...)` chains.
     *
     * For example:
     * ```
     * square(color=RED).at(0,0).rotate(90)
     * ```
     * produces:
     * ```
     * RotateExpr(AtExpr(TileLiteral(SOLID_SQUARE, RED), IntLiteral(0), IntLiteral(0)), IntLiteral(90))
     * ```
     */
    private fun parseMethodChainOrTile(): DslNode {
        var receiver = parseTileOrPrimary()

        while (peekType() == DslTokenType.DOT) {
            advance() // consume '.'
            receiver = parseMethodCall(receiver)
        }

        return receiver
    }

    /**
     * Parse a single method call after the dot:
     * - `.at(expr, expr)`     → [AtExpr]
     * - `.snapTo(expr, edge=EDGE)` → [SnapToExpr]
     * - `.rotate(expr)`       → [RotateExpr]
     * - any other method      → [DotExpr] (generic)
     */
    private fun parseMethodCall(receiver: DslNode): DslNode {
        return when (peekType()) {
            DslTokenType.AT -> parseAtCall(receiver)
            DslTokenType.SNAP_TO -> parseSnapToCall(receiver)
            DslTokenType.ROTATE -> parseRotateCall(receiver)
            else -> parseGenericDotCall(receiver)
        }
    }

    /**
     * `.at(expr, expr)` → [AtExpr]
     */
    private fun parseAtCall(receiver: DslNode): AtExpr {
        advance() // consume 'at'
        consume(DslTokenType.LPAREN, "Expected '(' after 'at'")
        val x = parseArithmetic()
        consume(DslTokenType.COMMA, "Expected ',' between x and y in .at()")
        allowTrailingComma() // tolerate trailing comma
        val y = parseArithmetic()
        allowTrailingComma()
        consume(DslTokenType.RPAREN, "Expected ')' after .at() arguments")
        return AtExpr(receiver, x, y)
    }

    /**
     * `.snapTo(expr, edge=EDGE)` → [SnapToExpr]
     */
    private fun parseSnapToCall(receiver: DslNode): SnapToExpr {
        advance() // consume 'snapTo'
        consume(DslTokenType.LPAREN, "Expected '(' after 'snapTo'")
        val target = parseExpression()
        consume(DslTokenType.COMMA, "Expected ',' after snapTo target")
        allowTrailingComma()
        consume(DslTokenType.EDGE_PARAM, "Expected 'edge' parameter")
        consume(DslTokenType.EQ, "Expected '=' after 'edge'")
        val edgeToken = consume(DslTokenType.EDGE, "Expected edge name (e.g. TOP, LEFT)")
        val edge = SnapEdge.valueOf(edgeToken.value)
        allowTrailingComma()
        consume(DslTokenType.RPAREN, "Expected ')' after .snapTo() arguments")
        return SnapToExpr(receiver, target, edge)
    }

    /**
     * `.rotate(expr)` → [RotateExpr]
     */
    private fun parseRotateCall(receiver: DslNode): RotateExpr {
        advance() // consume 'rotate'
        consume(DslTokenType.LPAREN, "Expected '(' after 'rotate'")
        val degrees = parseArithmetic()
        allowTrailingComma()
        consume(DslTokenType.RPAREN, "Expected ')' after .rotate() argument")
        return RotateExpr(receiver, degrees)
    }

    /**
     * Generic `.method(namedArgs)` → [DotExpr].
     * Handles any method name not recognised as a built-in.
     */
    private fun parseGenericDotCall(receiver: DslNode): DotExpr {
        val methodToken = consume(DslTokenType.IDENTIFIER, "Expected method name after '.'")
        val args = mutableMapOf<String, DslExpr>()

        if (peekType() == DslTokenType.LPAREN) {
            advance() // consume '('
            if (peekType() != DslTokenType.RPAREN) {
                // Parse named arguments: name=expr, ...
                do {
                    allowTrailingComma()
                    if (peekType() == DslTokenType.RPAREN) break
                    val paramName = consume(DslTokenType.IDENTIFIER, "Expected parameter name").value
                    consume(DslTokenType.EQ, "Expected '=' after parameter name")
                    args[paramName] = parseArithmetic()
                } while (peekType() == DslTokenType.COMMA && also { advance() }.run { true })
                allowTrailingComma()
            }
            consume(DslTokenType.RPAREN, "Expected ')' after arguments")
        }

        return DotExpr(receiver, methodToken.value, args)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Tile / primary-level parsing
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * ```
     * tileOrPrimary := tileLiteral
     *               | groupExpr
     *               | repeatExpr
     *               | radialExpr
     *               | mirrorExpr
     *               | 'last'
     *               | 'it'
     *               | IDENTIFIER
     *               | '(' expr ')'
     * ```
     */
    private fun parseTileOrPrimary(): DslNode {
        return when (peekType()) {
            DslTokenType.TILE_TYPE  -> parseTileLiteral()
            DslTokenType.GROUP      -> parseGroup()
            DslTokenType.REPEAT     -> parseRepeat()
            DslTokenType.RADIAL     -> parseRadial()
            DslTokenType.MIRROR     -> parseMirror()
            DslTokenType.LAST       -> { advance(); LastRef }
            DslTokenType.IT         -> { advance(); ItParam }
            DslTokenType.IDENTIFIER -> { val t = advance(); VarRef(t.value) }
            DslTokenType.LPAREN     -> parseParenthesized()
            else                    -> throw parseError(
                "Expected expression but found '${peek().value}'",
                expected = "expression"
            )
        }
    }

    /**
     * Parse a tile literal: `square`, `triangle`, `square(color=RED)`, etc.
     */
    private fun parseTileLiteral(): TileLiteral {
        val tileToken = advance() // consume TILE_TYPE
        val tileType = MagnaPyLexer.tileTypeForName(tileToken.value)
            ?: throw parseError("Unknown tile type '${tileToken.value}'")

        // Default colour
        var color = TileColor.RED

        // Optional parenthesised named args: (color=X)
        if (peekType() == DslTokenType.LPAREN) {
            advance() // consume '('
            // Parse named arguments
            while (peekType() != DslTokenType.RPAREN && !isAtEnd()) {
                allowTrailingComma()
                if (peekType() == DslTokenType.RPAREN) break
                when (peekType()) {
                    DslTokenType.COLOR_PARAM -> {
                        advance() // consume 'color'
                        consume(DslTokenType.EQ, "Expected '=' after 'color'")
                        color = when (peekType()) {
                            DslTokenType.COLOR -> {
                                val colorToken = advance()
                                TileColor.valueOf(colorToken.value)
                            }
                            DslTokenType.STRING -> {
                                val strToken = advance()
                                // Try to match by name (case-insensitive) or hex
                                resolveColor(strToken.value) ?: TileColor.RED
                            }
                            else -> throw parseError(
                                "Expected colour name",
                                expected = "color=<COLOUR>"
                            )
                        }
                    }
                    else -> throw parseError(
                        "Unexpected token '${peek().value}' in tile literal arguments",
                        expected = "color=<COLOUR>"
                    )
                }
                // Consume comma if present
                if (peekType() == DslTokenType.COMMA) advance()
                allowTrailingComma()
            }
            consume(DslTokenType.RPAREN, "Expected ')' after tile arguments")
        }

        return TileLiteral(tileType, color)
    }

    /**
     * Parse a group expression: `group { statements }`.
     * Handles empty groups gracefully: `group {}` → `GroupExpr(emptyList())`.
     */
    private fun parseGroup(): GroupExpr {
        advance() // consume 'group'
        consume(DslTokenType.LBRACE, "Expected '{' after 'group'")
        val children = parseBlockBody()
        consume(DslTokenType.RBRACE, "Expected '}' to close group")
        return GroupExpr(children)
    }

    /**
     * Parse a repeat expression: `repeat(N) { i -> statements }`
     * or `repeat(N) { statements }` (anonymous index).
     */
    private fun parseRepeat(): RepeatExpr {
        advance() // consume 'repeat'
        consume(DslTokenType.LPAREN, "Expected '(' after 'repeat'")
        val count = parseArithmetic()
        allowTrailingComma()
        consume(DslTokenType.RPAREN, "Expected ')' after repeat count")

        consume(DslTokenType.LBRACE, "Expected '{' after repeat(...)")

        // Optional lambda parameter: `i ->`
        val indexVar = parseOptionalLambdaParam()

        val bodyStatements = parseBlockBody()
        consume(DslTokenType.RBRACE, "Expected '}' to close repeat body")

        val body = if (bodyStatements.size == 1) bodyStatements[0]
        else BlockExpr(bodyStatements, result = null)

        return RepeatExpr(count, indexVar, body)
    }

    /**
     * Parse a radial expression:
     * `radial(count=N, center=expr) { i -> statements }`
     * or `radial(count=N) { i -> statements }`.
     */
    private fun parseRadial(): RadialExpr {
        advance() // consume 'radial'
        consume(DslTokenType.LPAREN, "Expected '(' after 'radial'")

        var count: DslExpr = IntLiteral(0)
        var center: DslNode? = null

        // Parse named arguments: count=expr, center=expr
        while (peekType() != DslTokenType.RPAREN && !isAtEnd()) {
            allowTrailingComma()
            if (peekType() == DslTokenType.RPAREN) break
            when (peekType()) {
                DslTokenType.COUNT_PARAM -> {
                    advance() // consume 'count'
                    consume(DslTokenType.EQ, "Expected '=' after 'count'")
                    count = parseArithmetic()
                }
                DslTokenType.IDENTIFIER -> {
                    // Could be 'center' as a positional-named arg
                    if (peek().value == "center") {
                        advance()
                        consume(DslTokenType.EQ, "Expected '=' after 'center'")
                        center = parseExpression()
                    } else {
                        throw parseError(
                            "Unexpected identifier '${peek().value}' in radial()",
                            expected = "count= or center="
                        )
                    }
                }
                else -> throw parseError(
                    "Unexpected token '${peek().value}' in radial() arguments",
                    expected = "count= or center="
                )
            }
            if (peekType() == DslTokenType.COMMA) advance()
            allowTrailingComma()
        }

        consume(DslTokenType.RPAREN, "Expected ')' after radial arguments")
        consume(DslTokenType.LBRACE, "Expected '{' after radial(...)")

        val indexVar = parseOptionalLambdaParam()
        val bodyStatements = parseBlockBody()
        consume(DslTokenType.RBRACE, "Expected '}' to close radial body")

        val body = if (bodyStatements.size == 1) bodyStatements[0]
        else BlockExpr(bodyStatements, result = null)

        return RadialExpr(count, center, body)
    }

    /**
     * Parse a mirror expression: `mirror(axis=X)`.
     * This produces a MirrorExpr but requires a target to be set later
     * via method chaining (e.g. `mirror(axis=HORIZONTAL).target(...)`).
     *
     * If mirror appears as a standalone expression (no receiver), it's parsed
     * as a MirrorExpr with target = LastRef as a placeholder.
     */
    private fun parseMirror(): DslNode {
        advance() // consume 'mirror'
        consume(DslTokenType.LPAREN, "Expected '(' after 'mirror'")

        var axis = Axis.HORIZONTAL // default

        // Parse: axis=AXIS
        while (peekType() != DslTokenType.RPAREN && !isAtEnd()) {
            allowTrailingComma()
            if (peekType() == DslTokenType.RPAREN) break
            when (peekType()) {
                DslTokenType.AXIS_PARAM -> {
                    advance() // consume 'axis'
                    consume(DslTokenType.EQ, "Expected '=' after 'axis'")
                    val axisToken = consume(DslTokenType.AXIS, "Expected axis name (HORIZONTAL or VERTICAL)")
                    axis = Axis.valueOf(axisToken.value)
                }
                DslTokenType.AXIS -> {
                    // Shorthand: mirror(HORIZONTAL) without 'axis='
                    val axisToken = advance()
                    axis = Axis.valueOf(axisToken.value)
                }
                else -> throw parseError(
                    "Unexpected token '${peek().value}' in mirror()",
                    expected = "axis="
                )
            }
            if (peekType() == DslTokenType.COMMA) advance()
            allowTrailingComma()
        }

        consume(DslTokenType.RPAREN, "Expected ')' after mirror arguments")

        // Mirror needs a target — if used standalone we wrap LastRef.
        // In practice, mirror is typically chained: tile.mirror(axis=X)
        return MirrorExpr(LastRef, axis)
    }

    /**
     * Parse a parenthesised expression: `( expr )`.
     */
    private fun parseParenthesized(): DslNode {
        advance() // consume '('
        val expr = parseExpression()
        consume(DslTokenType.RPAREN, "Expected ')'")
        return expr
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Arithmetic parsing (expression precedence levels)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Parse arithmetic expressions with correct precedence:
     *
     * 1. `+`, `-` (lowest)
     * 2. `*`, `%` (higher)
     * 3. Unary `-`
     * 4. Primary (numbers, identifiers, parenthesised)
     *
     * Returns a [DslExpr] (may be [IntLiteral], [BinaryExpr], [VarRef], etc.).
     */
    private fun parseArithmetic(): DslExpr {
        return parseAdditive()
    }

    /**
     * Additive: `left ((+ | -) right)*`
     */
    private fun parseAdditive(): DslExpr {
        var left = parseMultiplicative()

        while (peekType() == DslTokenType.PLUS || peekType() == DslTokenType.MINUS) {
            val opToken = advance()
            val op = when (opToken.type) {
                DslTokenType.PLUS  -> BinaryOp.PLUS
                DslTokenType.MINUS -> BinaryOp.MINUS
                else -> error("unreachable")
            }
            val right = parseMultiplicative()
            left = BinaryExpr(left, op, right)
        }

        return left
    }

    /**
     * Multiplicative: `left ('*' right)*`
     *
     * Note: The `%` (modulo) operator is defined in [BinaryOp] but the
     * current lexer does not emit a dedicated `%` token. This method
     * is ready for it if/when added.
     */
    private fun parseMultiplicative(): DslExpr {
        var left = parseUnary()

        while (peekType() == DslTokenType.TIMES) {
            advance() // consume '*'
            val right = parseUnary()
            left = BinaryExpr(left, BinaryOp.TIMES, right)
        }

        return left
    }

    /**
     * Unary: `-primary` | `primary`
     */
    private fun parseUnary(): DslExpr {
        if (peekType() == DslTokenType.MINUS) {
            advance() // consume '-'
            val operand = parseUnary()
            // Represent as 0 - operand
            return BinaryExpr(IntLiteral(0), BinaryOp.MINUS, operand)
        }
        return parseArithmeticPrimary()
    }

    /**
     * Primary: NUMBER | IDENTIFIER | 'it' | 'last' | '(' arithmetic ')'
     */
    private fun parseArithmeticPrimary(): DslExpr {
        return when (peekType()) {
            DslTokenType.NUMBER -> {
                val token = advance()
                IntLiteral(token.value.toIntOrNull()
                    ?: throw parseError("Invalid number '${token.value}'"))
            }
            DslTokenType.IDENTIFIER -> {
                val token = advance()
                VarRef(token.value)
            }
            DslTokenType.IT -> {
                advance()
                ItParam
            }
            DslTokenType.LAST -> {
                // In arithmetic context, treat last as a reference
                advance()
                // LastRef is a DslNode but not DslExpr; wrap in VarRef-like
                ItParam // Using ItParam as placeholder; LastRef isn't a DslExpr
            }
            DslTokenType.LPAREN -> {
                advance() // consume '('
                val expr = parseArithmetic()
                consume(DslTokenType.RPAREN, "Expected ')'")
                expr
            }
            else -> throw parseError(
                "Expected number or identifier but found '${peek().value}'",
                expected = "number or identifier"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Block body & lambda helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Parse zero or more statements until we hit `}`.
     * Used for the body of `group {}`, `repeat(N) {}`, `radial(...) {}`.
     */
    private fun parseBlockBody(): List<DslNode> {
        val statements = mutableListOf<DslNode>()
        while (peekType() != DslTokenType.RBRACE && !isAtEnd()) {
            statements.add(parseStatement())
        }
        return statements.toList()
    }

    /**
     * Try to parse an optional lambda parameter: `identifier '->'`.
     * Returns the parameter name if found, or `null` if the next tokens
     * don't form a lambda parameter.
     */
    private fun parseOptionalLambdaParam(): String? {
        // Look ahead: IDENTIFIER ARROW → lambda param
        if (peekType() == DslTokenType.IDENTIFIER && peekType(1) == DslTokenType.ARROW) {
            val name = advance().value
            advance() // consume '->'
            return name
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Token manipulation helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The current token without advancing.
     */
    private fun peek(): Token =
        if (pos < tokens.size) tokens[pos] else tokens.last()

    /**
     * Look ahead by [offset] tokens (0 = current).
     */
    private fun peek(offset: Int): Token {
        val idx = pos + offset
        return if (idx < tokens.size) tokens[idx] else tokens.last()
    }

    /**
     * The [DslTokenType] of the current token.
     */
    private fun peekType(): DslTokenType = peek().type

    /**
     * The [DslTokenType] of the token at [offset] ahead.
     */
    private fun peekType(offset: Int): DslTokenType = peek(offset).type

    /**
     * The string value of the current token.
     */
    private fun peekValue(): String = peek().value

    /**
     * Advance the position by one and return the consumed token.
     */
    private fun advance(): Token {
        val token = peek()
        if (!isAtEnd()) pos++
        return token
    }

    /**
     * Consume the current token, asserting it matches [expected].
     * Throws [DslError.ParseError] if the token doesn't match.
     */
    private fun consume(expected: DslTokenType, message: String): Token {
        if (peekType() == expected) return advance()
        throw parseError(
            "$message, got '${peek().value}'",
            expected = expected.name
        )
    }

    /**
     * Are we at the end-of-input token?
     */
    private fun isAtEnd(): Boolean = peekType() == DslTokenType.EOF

    /**
     * If the current token is a comma, consume it.
     * This gracefully handles trailing commas in argument lists.
     */
    private fun allowTrailingComma() {
        if (peekType() == DslTokenType.COMMA) {
            // Only consume if next is ')' or '}' — i.e. truly trailing
            if (peekType(1) == DslTokenType.RPAREN ||
                peekType(1) == DslTokenType.RBRACE) {
                advance() // consume trailing comma
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Error construction
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build a [DslError.ParseError] at the current token position.
     */
    private fun parseError(
        message: String,
        expected: String? = null
    ): DslError.ParseError {
        val current = peek()
        val fullMessage = if (expected != null) {
            "$message (expected $expected)"
        } else {
            message
        }
        return DslError.ParseError(
            detail = fullMessage,
            line = current.line,
            unexpectedToken = current.value
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lookahead & recovery helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Return the [DslTokenType] of the token at `pos + [offset]`.
     * Returns [DslTokenType.EOF] if the offset is out of range.
     */
    private fun lookaheadType(offset: Int): DslTokenType {
        val idx = pos + offset
        return if (idx < tokens.size) tokens[idx].type else DslTokenType.EOF
    }

    /**
     * Try to resolve a colour name from a string token value.
     * Handles:
     * - Named colours (case-insensitive): `"red"` → `TileColor.RED`
     * - Hex colour codes: `"#A04523"` → `TileColor.RED`
     * Returns `null` if no match is found.
     */
    private fun resolveColor(name: String): TileColor? {
        // ── Hex colour code ──────────────────────────────────────────
        if (name.startsWith("#")) {
            val hexKey = name.removePrefix("#").uppercase()
            val colorName = MagnaPyLexer.COLOR_HEX_MAP[hexKey] ?: return null
            return TileColor.valueOf(colorName)
        }
        // ── Named colour (case-insensitive) ──────────────────────────
        return try {
            TileColor.valueOf(name.uppercase())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Advance past tokens until we find what looks like the start of a
     * new statement (VAL, TILE_TYPE, IDENTIFIER followed by EQ, or EOF).
     *
     * Used for error recovery: when a single statement fails to parse,
     * we skip to the next one rather than failing the entire script.
     */
    private fun skipToNextStatement() {
        while (!isAtEnd()) {
            advance()
            when (peekType()) {
                DslTokenType.VAL,
                DslTokenType.TILE_TYPE,
                DslTokenType.GROUP,
                DslTokenType.REPEAT,
                DslTokenType.RADIAL,
                DslTokenType.MIRROR,
                DslTokenType.LBRACE,
                DslTokenType.RBRACE -> return
                DslTokenType.IDENTIFIER -> {
                    // IDENTIFIER followed by EQ looks like an implicit varDecl
                    if (lookaheadType(1) == DslTokenType.EQ) return
                }
                else -> { /* keep skipping */ }
            }
        }
    }
}
