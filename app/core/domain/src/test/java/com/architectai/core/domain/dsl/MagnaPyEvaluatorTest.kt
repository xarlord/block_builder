package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.Axis
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import org.junit.Assert.*
import org.junit.Test

class MagnaPyEvaluatorTest {

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun evaluate(node: DslNode): List<TilePlacement> =
        MagnaPyEvaluator().evaluate(node).getOrElse { throw it }

    private fun evaluateWith(source: String): List<TilePlacement> {
        val tokens = MagnaPyLexer(source).tokenize()
        val ast = MagnaPyParser(tokens).parse().getOrElse { throw it }
        return MagnaPyEvaluator().evaluate(ast).getOrElse { throw it }
    }

    // ── 1. Evaluate single tile ────────────────────────────────────────────

    @Test
    fun `evaluate single tile literal`() {
        val tile = TileLiteral(TileType.SOLID_SQUARE, TileColor.RED)
        val placements = evaluate(tile)
        assertEquals(1, placements.size)
        val p = placements[0]
        assertEquals(TileType.SOLID_SQUARE, p.tileType)
        assertEquals(0, p.x)
        assertEquals(0, p.y)
        assertEquals(Rotation.R0, p.rotation)
        assertEquals(TileColor.RED, p.color)
    }

    // ── 2. Evaluate at ─────────────────────────────────────────────────────

    @Test
    fun `evaluate at expression`() {
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            IntLiteral(5),
            IntLiteral(10)
        )
        val placements = evaluate(node)
        assertEquals(1, placements.size)
        assertEquals(5, placements[0].x)
        assertEquals(10, placements[0].y)
    }

    // ── 3. Evaluate rotate ─────────────────────────────────────────────────

    @Test
    fun `evaluate rotate expression`() {
        val node = RotateExpr(
            TileLiteral(TileType.EQUILATERAL_TRIANGLE, TileColor.BLUE),
            IntLiteral(90)
        )
        val placements = evaluate(node)
        assertEquals(1, placements.size)
        assertEquals(Rotation.R90, placements[0].rotation)
        assertEquals(TileType.EQUILATERAL_TRIANGLE, placements[0].tileType)
        assertEquals(TileColor.BLUE, placements[0].color)
    }

    // ── 4. Evaluate group ──────────────────────────────────────────────────

    @Test
    fun `evaluate group expression`() {
        val node = GroupExpr(
            listOf(
                AtExpr(TileLiteral(TileType.SOLID_SQUARE, TileColor.RED), IntLiteral(0), IntLiteral(0)),
                AtExpr(TileLiteral(TileType.EQUILATERAL_TRIANGLE, TileColor.BLUE), IntLiteral(3), IntLiteral(0))
            )
        )
        val placements = evaluate(node)
        assertEquals(2, placements.size)
        assertEquals(0, placements[0].x)
        assertEquals(3, placements[1].x)
        assertEquals(TileType.SOLID_SQUARE, placements[0].tileType)
        assertEquals(TileType.EQUILATERAL_TRIANGLE, placements[1].tileType)
    }

    // ── 5. Evaluate repeat ─────────────────────────────────────────────────

    @Test
    fun `evaluate repeat expression`() {
        val node = RepeatExpr(
            count = IntLiteral(3),
            indexVar = null,
            body = TileLiteral(TileType.SOLID_SQUARE, TileColor.RED)
        )
        val placements = evaluate(node)
        assertEquals(3, placements.size)
        // All tiles should be at (0,0) since no index-dependent positioning
        placements.forEach { p ->
            assertEquals(TileType.SOLID_SQUARE, p.tileType)
        }
    }

    // ── 6. Evaluate repeat with index variable ─────────────────────────────

    @Test
    fun `evaluate repeat with index variable for positioning`() {
        // Use source-level DSL so the parser correctly handles 'i' as a VarRef
        // and the evaluator's indexVar mechanism matches it via VarRef -> evalVarRef
        // Note: The evaluator stores index as ctx.variables[varName] 
        // through the RepeatExpr path
        val source = "repeat(3) { i -> square(color = RED).at(i * 3, 0) }"
        val placements = evaluateWith(source)
        assertEquals(3, placements.size)
        // i=0 → x=0, i=1 → x=3, i=2 → x=6
        assertEquals(0, placements[0].x)
        assertEquals(3, placements[1].x)
        assertEquals(6, placements[2].x)
    }

    // ── 7. Evaluate plus ───────────────────────────────────────────────────

    @Test
    fun `evaluate plus expression`() {
        val group1 = AtExpr(TileLiteral(TileType.SOLID_SQUARE, TileColor.RED), IntLiteral(0), IntLiteral(0))
        val group2 = AtExpr(TileLiteral(TileType.EQUILATERAL_TRIANGLE, TileColor.BLUE), IntLiteral(3), IntLiteral(3))
        val node = PlusExpr(group1, group2)
        val placements = evaluate(node)
        assertEquals(2, placements.size)
        assertEquals(TileType.SOLID_SQUARE, placements[0].tileType)
        assertEquals(TileType.EQUILATERAL_TRIANGLE, placements[1].tileType)
    }

    // ── 8. Evaluate var decl + var ref ─────────────────────────────────────

    @Test
    fun `evaluate variable declaration and reference`() {
        // val face = square(color = RED); face
        val program = BlockExpr(
            statements = listOf(
                VarDecl("face", TileLiteral(TileType.SOLID_SQUARE, TileColor.RED)),
                VarRef("face")
            ),
            result = null
        )
        val placements = evaluate(program)
        assertEquals(1, placements.size)
        assertEquals(TileType.SOLID_SQUARE, placements[0].tileType)
        assertEquals(TileColor.RED, placements[0].color)
    }

    // ── 9. Evaluate full program ───────────────────────────────────────────

    @Test
    fun `evaluate full program with val and group`() {
        val source = """
            val face = group { square(color = RED).at(0, 0) }
            face
        """.trimIndent()
        val placements = evaluateWith(source)
        assertEquals(1, placements.size)
        assertEquals(TileType.SOLID_SQUARE, placements[0].tileType)
        assertEquals(0, placements[0].x)
        assertEquals(0, placements[0].y)
        assertEquals(TileColor.RED, placements[0].color)
    }

    @Test
    fun `evaluate full program with two tiles`() {
        val source = """
            val row = group {
                square(color = RED).at(0, 0)
                triangle(color = BLUE).at(3, 0)
            }
            row
        """.trimIndent()
        val placements = evaluateWith(source)
        assertEquals(2, placements.size)
        assertEquals(TileType.SOLID_SQUARE, placements[0].tileType)
        assertEquals(TileType.EQUILATERAL_TRIANGLE, placements[1].tileType)
    }

    // ── 10. Grid bounds validation ─────────────────────────────────────────

    @Test
    fun `tile outside grid bounds fails validation`() {
        // Tile at x=30 should fail (grid is 0..29)
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            IntLiteral(30),
            IntLiteral(0)
        )
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected failure for out-of-bounds tile", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "Expected EvalError for grid bounds, got ${error?.let { it::class.simpleName }}",
            error is DslError.EvalError
        )
        assertTrue(error!!.message!!.contains("grid bounds"))
    }

    @Test
    fun `tile at negative position fails validation`() {
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            IntLiteral(-1),
            IntLiteral(0)
        )
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected failure for negative position", result.isFailure)
    }

    @Test
    fun `tile at max valid position succeeds`() {
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            IntLiteral(29),
            IntLiteral(29)
        )
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected success for tile at (29,29)", result.isSuccess)
        assertEquals(29, result.getOrDefault(emptyList())[0].x)
        assertEquals(29, result.getOrDefault(emptyList())[0].y)
    }

    // ── 11. Max tiles validation ────────────────────────────────────────────

    @Test
    fun `exceeding max tiles fails validation`() {
        // repeat(201) { square(color = RED) }  → 201 tiles > 200 max
        val node = RepeatExpr(
            count = IntLiteral(201),
            indexVar = null,
            body = TileLiteral(TileType.SOLID_SQUARE, TileColor.RED)
        )
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected failure for too many tiles", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "Expected EvalError for too many tiles, got ${error?.let { it::class.simpleName }}",
            error is DslError.EvalError
        )
        assertTrue(error!!.message!!.contains("Too many tiles"))
    }

    @Test
    fun `exactly 200 tiles succeeds`() {
        val node = RepeatExpr(
            count = IntLiteral(200),
            indexVar = null,
            body = TileLiteral(TileType.SOLID_SQUARE, TileColor.RED)
        )
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected success for exactly 200 tiles", result.isSuccess)
        assertEquals(200, result.getOrDefault(emptyList()).size)
    }

    // ── 12. Evaluate arithmetic ────────────────────────────────────────────

    @Test
    fun `evaluate binary multiplication`() {
        // BinaryExpr(IntLiteral(3), TIMES, IntLiteral(4)) → IntValue(12)
        // Use it in an at() expression
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            BinaryExpr(IntLiteral(3), BinaryOp.TIMES, IntLiteral(4)),
            IntLiteral(0)
        )
        val placements = evaluate(node)
        assertEquals(1, placements.size)
        assertEquals(12, placements[0].x)
    }

    @Test
    fun `evaluate binary addition`() {
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            BinaryExpr(IntLiteral(5), BinaryOp.PLUS, IntLiteral(7)),
            IntLiteral(0)
        )
        val placements = evaluate(node)
        assertEquals(12, placements[0].x)
    }

    @Test
    fun `evaluate binary subtraction`() {
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            BinaryExpr(IntLiteral(10), BinaryOp.MINUS, IntLiteral(3)),
            IntLiteral(0)
        )
        val placements = evaluate(node)
        assertEquals(7, placements[0].x)
    }

    @Test
    fun `evaluate binary modulo`() {
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            BinaryExpr(IntLiteral(10), BinaryOp.MOD, IntLiteral(3)),
            IntLiteral(0)
        )
        val placements = evaluate(node)
        assertEquals(1, placements[0].x)
    }

    // ── Additional tests ───────────────────────────────────────────────────

    @Test
    fun `evaluate undefined variable throws error`() {
        val node = VarRef("undefinedVar")
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected failure for undefined var", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is DslError.EvalError)
        assertTrue(error!!.message!!.contains("Undefined variable"))
    }

    @Test
    fun `evaluate division by zero throws error`() {
        val node = AtExpr(
            TileLiteral(TileType.SOLID_SQUARE, TileColor.RED),
            BinaryExpr(IntLiteral(10), BinaryOp.MOD, IntLiteral(0)),
            IntLiteral(0)
        )
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected failure for division by zero", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is DslError.EvalError)
        assertTrue(error!!.message!!.contains("Division by zero"))
    }

    @Test
    fun `evaluate chained rotate and at`() {
        val source = "square(color = RED).at(5, 10).rotate(90)"
        val placements = evaluateWith(source)
        assertEquals(1, placements.size)
        assertEquals(5, placements[0].x)
        assertEquals(10, placements[0].y)
        assertEquals(Rotation.R90, placements[0].rotation)
    }

    @Test
    fun `evaluate var decl with plus`() {
        val source = """
            val a = square(color = RED)
            val b = triangle(color = BLUE)
            a + b
        """.trimIndent()
        val placements = evaluateWith(source)
        assertEquals(2, placements.size)
        assertEquals(TileType.SOLID_SQUARE, placements[0].tileType)
        assertEquals(TileType.EQUILATERAL_TRIANGLE, placements[1].tileType)
    }

    @Test
    fun `evaluate repeat in group`() {
        val source = """
            group {
                repeat(3) { i ->
                    square(color = RED).at(i * 3, 0)
                }
            }
        """.trimIndent()
        val placements = evaluateWith(source)
        assertEquals(3, placements.size)
        assertEquals(0, placements[0].x)
        assertEquals(3, placements[1].x)
        assertEquals(6, placements[2].x)
    }

    @Test
    fun `evaluate negative repeat count throws error`() {
        val node = RepeatExpr(
            count = IntLiteral(-1),
            indexVar = null,
            body = TileLiteral(TileType.SOLID_SQUARE, TileColor.RED)
        )
        val result = MagnaPyEvaluator().evaluate(node)
        assertTrue("Expected failure for negative repeat count", result.isFailure)
    }
}
