package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType
import org.junit.Assert.*
import org.junit.Test

class MagnaPyParserTest {

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun parse(source: String): BlockExpr {
        val tokens = MagnaPyLexer(source).tokenize()
        return MagnaPyParser(tokens).parse()
            .getOrElse { throw it }
    }

    private fun parseFirst(source: String): DslNode {
        val block = parse(source)
        assertTrue("Expected at least one statement", block.statements.isNotEmpty())
        return block.statements.first()
    }

    // ── 1. Parse single tile ───────────────────────────────────────────────

    @Test
    fun `parse single tile literal`() {
        val node = parseFirst("square(color = RED)")
        assertTrue(node is TileLiteral)
        val tile = node as TileLiteral
        assertEquals(TileType.SOLID_SQUARE, tile.type)
        assertEquals(TileColor.RED, tile.color)
    }

    @Test
    fun `parse single tile with default color`() {
        // square without arguments → defaults to RED
        val node = parseFirst("square")
        assertTrue(node is TileLiteral)
        val tile = node as TileLiteral
        assertEquals(TileType.SOLID_SQUARE, tile.type)
        assertEquals(TileColor.RED, tile.color)
    }

    // ── 2. Parse tile with at() ────────────────────────────────────────────

    @Test
    fun `parse tile with at`() {
        val node = parseFirst("square(color = RED).at(0, 0)")
        assertTrue(node is AtExpr)
        val at = node as AtExpr
        assertTrue(at.target is TileLiteral)
        assertTrue(at.x is IntLiteral)
        assertTrue(at.y is IntLiteral)
        assertEquals(0, (at.x as IntLiteral).value)
        assertEquals(0, (at.y as IntLiteral).value)
    }

    // ── 3. Parse tile with rotate() ────────────────────────────────────────

    @Test
    fun `parse tile with rotate`() {
        val node = parseFirst("triangle(color = BLUE).rotate(90)")
        assertTrue(node is RotateExpr)
        val rotate = node as RotateExpr
        assertTrue(rotate.target is TileLiteral)
        val target = rotate.target as TileLiteral
        assertEquals(TileType.EQUILATERAL_TRIANGLE, target.type)
        assertEquals(TileColor.BLUE, target.color)
        assertTrue(rotate.degrees is IntLiteral)
        assertEquals(90, (rotate.degrees as IntLiteral).value)
    }

    // ── 4. Parse chain - at then rotate ────────────────────────────────────

    @Test
    fun `parse chained at and rotate`() {
        val node = parseFirst("square(color = RED).at(0, 0).rotate(90)")
        // Should be RotateExpr(AtExpr(TileLiteral, 0, 0), 90)
        assertTrue(node is RotateExpr)
        val rotate = node as RotateExpr
        assertTrue(rotate.target is AtExpr)

        val at = rotate.target as AtExpr
        assertTrue(at.target is TileLiteral)
        assertEquals(0, (at.x as IntLiteral).value)
        assertEquals(0, (at.y as IntLiteral).value)
        assertEquals(90, (rotate.degrees as IntLiteral).value)
    }

    // ── 5. Parse val declaration ───────────────────────────────────────────

    @Test
    fun `parse val declaration`() {
        val node = parseFirst("val face = square(color = RED)")
        assertTrue(node is VarDecl)
        val decl = node as VarDecl
        assertEquals("face", decl.name)
        assertTrue(decl.value is TileLiteral)
    }

    // ── 6. Parse group ─────────────────────────────────────────────────────

    @Test
    fun `parse group expression`() {
        val node = parseFirst("group { square(color = RED).at(0, 0) }")
        assertTrue(node is GroupExpr)
        val group = node as GroupExpr
        assertEquals(1, group.children.size)
        assertTrue(group.children[0] is AtExpr)
    }

    @Test
    fun `parse empty group`() {
        val node = parseFirst("group { }")
        assertTrue(node is GroupExpr)
        assertTrue((node as GroupExpr).children.isEmpty())
    }

    @Test
    fun `parse group with multiple children`() {
        val source = """
            group {
                square(color = RED).at(0, 0)
                triangle(color = BLUE).at(3, 0)
            }
        """.trimIndent()
        val node = parseFirst(source)
        assertTrue(node is GroupExpr)
        assertEquals(2, (node as GroupExpr).children.size)
    }

    // ── 7. Parse repeat ────────────────────────────────────────────────────

    @Test
    fun `parse repeat with index variable`() {
        val source = "repeat(4) { i -> square(color = RED).at(i * 3, 0) }"
        val node = parseFirst(source)
        assertTrue(node is RepeatExpr)
        val repeat = node as RepeatExpr
        assertTrue(repeat.count is IntLiteral)
        assertEquals(4, (repeat.count as IntLiteral).value)
        assertEquals("i", repeat.indexVar)
        // Body should contain an AtExpr
        assertTrue(repeat.body is AtExpr)
    }

    @Test
    fun `parse repeat without index variable`() {
        val source = "repeat(3) { square(color = RED) }"
        val node = parseFirst(source)
        assertTrue(node is RepeatExpr)
        val repeat = node as RepeatExpr
        assertNull(repeat.indexVar)
    }

    // ── 8. Parse plus ──────────────────────────────────────────────────────

    @Test
    fun `parse plus expression`() {
        // Need to define the vars first, but parsing alone doesn't require eval
        val node = parseFirst("face + body")
        assertTrue(node is PlusExpr)
        val plus = node as PlusExpr
        assertTrue(plus.left is VarRef)
        assertEquals("face", (plus.left as VarRef).name)
        assertTrue(plus.right is VarRef)
        assertEquals("body", (plus.right as VarRef).name)
    }

    // ── 9. Parse arithmetic ────────────────────────────────────────────────

    @Test
    fun `parse arithmetic multiplication`() {
        // We test arithmetic via a repeat body that uses i * 3
        val source = "repeat(4) { i -> square(color = RED).at(i * 3, 0) }"
        val node = parseFirst(source) as RepeatExpr
        val body = node.body as AtExpr
        val x = body.x
        assertTrue("Expected BinaryExpr for x coordinate", x is BinaryExpr)
        val bin = x as BinaryExpr
        assertEquals(BinaryOp.TIMES, bin.op)
        assertTrue(bin.left is VarRef)
        assertEquals("i", (bin.left as VarRef).name)
        assertTrue(bin.right is IntLiteral)
        assertEquals(3, (bin.right as IntLiteral).value)
    }

    // ── 10. Parse full program with multiple statements ────────────────────

    @Test
    fun `parse full program with multiple statements`() {
        val source = """
            val face = group { square(color = RED).at(0, 0) }
            val body = group { square(color = BLUE).at(0, 3) }
            face + body
        """.trimIndent()
        val block = parse(source)
        assertEquals(3, block.statements.size)
        assertTrue(block.statements[0] is VarDecl)
        assertEquals("face", (block.statements[0] as VarDecl).name)
        assertTrue(block.statements[1] is VarDecl)
        assertEquals("body", (block.statements[1] as VarDecl).name)
        assertTrue(block.statements[2] is PlusExpr)
    }

    // ── 11. Parse error on unexpected token ────────────────────────────────

    @Test
    fun `parse error on unexpected token`() {
        val source = "= ="
        val result = MagnaPyLexer(source).tokenize().let { tokens ->
            MagnaPyParser(tokens).parse()
        }
        assertTrue("Expected parse failure", result.isFailure)
        assertTrue(
            "Expected ParseError, got ${result.exceptionOrNull()!!::class.simpleName}",
            result.exceptionOrNull() is DslError.ParseError
        )
    }

    @Test
    fun `parse error on missing closing brace`() {
        val source = "group { square(color = RED)"
        val result = MagnaPyLexer(source).tokenize().let { tokens ->
            MagnaPyParser(tokens).parse()
        }
        assertTrue("Expected parse failure", result.isFailure)
        assertTrue(
            result.exceptionOrNull() is DslError.ParseError
        )
    }

    // ── 12. Parse mirror ───────────────────────────────────────────────────

    @Test
    fun `parse mirror expression`() {
        val node = parseFirst("mirror(axis = HORIZONTAL)")
        assertTrue(node is MirrorExpr)
        val mirror = node as MirrorExpr
        assertEquals(com.architectai.core.domain.model.Axis.HORIZONTAL, mirror.axis)
        // Default target is LastRef when standalone
        assertTrue(mirror.target is LastRef)
    }

    @Test
    fun `parse mirror with shorthand axis`() {
        val node = parseFirst("mirror(VERTICAL)")
        assertTrue(node is MirrorExpr)
        val mirror = node as MirrorExpr
        assertEquals(com.architectai.core.domain.model.Axis.VERTICAL, mirror.axis)
    }

    // ── Additional coverage ────────────────────────────────────────────────

    @Test
    fun `parse snapTo expression`() {
        val source = "square(color = RED).snapTo(last, edge = TOP)"
        val node = parseFirst(source)
        assertTrue(node is SnapToExpr)
        val snap = node as SnapToExpr
        assertTrue(snap.source is TileLiteral)
        assertTrue(snap.target is LastRef)
        assertEquals(SnapEdge.TOP, snap.edge)
    }

    @Test
    fun `parse nested groups`() {
        val source = "group { group { square(color = RED) } }"
        val node = parseFirst(source)
        assertTrue(node is GroupExpr)
        val outer = node as GroupExpr
        assertEquals(1, outer.children.size)
        assertTrue(outer.children[0] is GroupExpr)
    }

    @Test
    fun `parse val decl with group value`() {
        val source = "val face = group { square(color = RED).at(0, 0) }"
        val node = parseFirst(source)
        assertTrue(node is VarDecl)
        val decl = node as VarDecl
        assertEquals("face", decl.name)
        assertTrue(decl.value is GroupExpr)
    }

    @Test
    fun `parse tile with different colors`() {
        val node = parseFirst("square(color = BLUE)")
        assertTrue(node is TileLiteral)
        assertEquals(TileColor.BLUE, (node as TileLiteral).color)
    }
}
