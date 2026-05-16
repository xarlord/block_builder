package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileType
import org.junit.Assert.*
import org.junit.Test

class SnapEngineTest {

    private val engine = SnapEngine()

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Create a PlacedTile for a square at the given position. */
    private fun squareAt(x: Int, y: Int, rotation: Rotation = Rotation.R0): PlacedTile =
        PlacedTile(TileType.SOLID_SQUARE, x, y, rotation)

    /** Create a PlacedTile for a triangle at the given position. */
    private fun triangleAt(x: Int, y: Int, rotation: Rotation = Rotation.R0): PlacedTile =
        PlacedTile(TileType.EQUILATERAL_TRIANGLE, x, y, rotation)

    // ── 1. Square snap TOP ─────────────────────────────────────────────────

    @Test
    fun `square snap TOP places source above target`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.TOP
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // Source should be placed directly above: dx=0, dy=-3 → (3, 0)
        assertEquals(3, x)
        assertEquals(0, y)
    }

    // ── 2. Square snap BOTTOM ──────────────────────────────────────────────

    @Test
    fun `square snap BOTTOM places source below target`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BOTTOM
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // Source should be placed directly below: dx=0, dy=+3 → (3, 6)
        assertEquals(3, x)
        assertEquals(6, y)
    }

    // ── 3. Square snap LEFT ────────────────────────────────────────────────

    @Test
    fun `square snap LEFT places source left of target`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.LEFT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // Source should be placed to the left: dx=-3, dy=0 → (0, 3)
        assertEquals(0, x)
        assertEquals(3, y)
    }

    // ── 4. Square snap RIGHT ───────────────────────────────────────────────

    @Test
    fun `square snap RIGHT places source right of target`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.RIGHT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // Source should be placed to the right: dx=+3, dy=0 → (6, 3)
        assertEquals(6, x)
        assertEquals(3, y)
    }

    // ── 5. Corner snap ─────────────────────────────────────────────────────

    @Test
    fun `square snap TOP_LEFT places source at top-left diagonal`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.TOP_LEFT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // TOP_LEFT: dx=-3, dy=-3 → (0, 0)
        assertEquals(0, x)
        assertEquals(0, y)
    }

    @Test
    fun `square snap TOP_RIGHT places source at top-right diagonal`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.TOP_RIGHT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // TOP_RIGHT: dx=+3, dy=-3 → (6, 0)
        assertEquals(6, x)
        assertEquals(0, y)
    }

    @Test
    fun `square snap BOTTOM_LEFT places source at bottom-left diagonal`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BOTTOM_LEFT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // BOTTOM_LEFT: dx=-3, dy=+3 → (0, 6)
        assertEquals(0, x)
        assertEquals(6, y)
    }

    @Test
    fun `square snap BOTTOM_RIGHT places source at bottom-right diagonal`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BOTTOM_RIGHT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // BOTTOM_RIGHT: dx=+3, dy=+3 → (6, 6)
        assertEquals(6, x)
        assertEquals(6, y)
    }

    // ── 6. Triangle snap BASE ──────────────────────────────────────────────

    @Test
    fun `triangle snap BASE at R0 places source below`() {
        val target = triangleAt(3, 3, Rotation.R0)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.EQUILATERAL_TRIANGLE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BASE
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // R0: base at bottom → dy=+3 → (3, 6)
        assertEquals(3, x)
        assertEquals(6, y)
    }

    @Test
    fun `triangle snap BASE at R90 accounts for rotation`() {
        val target = triangleAt(3, 3, Rotation.R90)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.EQUILATERAL_TRIANGLE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BASE
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // R90: base at right → dx=+3 → (6, 3)
        assertEquals(6, x)
        assertEquals(3, y)
    }

    @Test
    fun `triangle snap BASE at R180 accounts for rotation`() {
        val target = triangleAt(3, 3, Rotation.R180)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.EQUILATERAL_TRIANGLE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BASE
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // R180: base at top → dy=-3 → (3, 0)
        assertEquals(3, x)
        assertEquals(0, y)
    }

    @Test
    fun `triangle snap BASE at R270 accounts for rotation`() {
        val target = triangleAt(3, 3, Rotation.R270)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.EQUILATERAL_TRIANGLE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BASE
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // R270: base at left → dx=-3 → (0, 3)
        assertEquals(0, x)
        assertEquals(3, y)
    }

    @Test
    fun `triangle snap LEFT at R0`() {
        val target = triangleAt(3, 3, Rotation.R0)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.EQUILATERAL_TRIANGLE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.LEFT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // R0 LEFT: (-3, -1) → (0, 2)
        assertEquals(0, x)
        assertEquals(2, y)
    }

    @Test
    fun `triangle snap RIGHT at R0`() {
        val target = triangleAt(3, 3, Rotation.R0)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.EQUILATERAL_TRIANGLE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.RIGHT
        )
        assertTrue("Expected successful snap", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // R0 RIGHT: (+3, -1) → (6, 2)
        assertEquals(6, x)
        assertEquals(2, y)
    }

    // ── 7. edgesForTile SOLID_SQUARE ────────────────────────────────────────

    @Test
    fun `edgesForTile SOLID_SQUARE returns cardinal and corner edges`() {
        val edges = engine.edgesForTile(TileType.SOLID_SQUARE)
        assertEquals(8, edges.size)
        assertTrue(edges.contains(SnapEdge.TOP))
        assertTrue(edges.contains(SnapEdge.BOTTOM))
        assertTrue(edges.contains(SnapEdge.LEFT))
        assertTrue(edges.contains(SnapEdge.RIGHT))
        assertTrue(edges.contains(SnapEdge.TOP_LEFT))
        assertTrue(edges.contains(SnapEdge.TOP_RIGHT))
        assertTrue(edges.contains(SnapEdge.BOTTOM_LEFT))
        assertTrue(edges.contains(SnapEdge.BOTTOM_RIGHT))
        // BASE should not be in the list for squares
        assertFalse(edges.contains(SnapEdge.BASE))
    }

    // ── 8. edgesForTile EQUILATERAL_TRIANGLE ────────────────────────────────

    @Test
    fun `edgesForTile EQUILATERAL_TRIANGLE returns base sides and corner edges`() {
        val edges = engine.edgesForTile(TileType.EQUILATERAL_TRIANGLE)
        assertEquals(7, edges.size)
        assertTrue(edges.contains(SnapEdge.BASE))
        assertTrue(edges.contains(SnapEdge.LEFT))
        assertTrue(edges.contains(SnapEdge.RIGHT))
        assertTrue(edges.contains(SnapEdge.TOP_LEFT))
        assertTrue(edges.contains(SnapEdge.TOP_RIGHT))
        assertTrue(edges.contains(SnapEdge.BOTTOM_LEFT))
        assertTrue(edges.contains(SnapEdge.BOTTOM_RIGHT))
        // TOP and BOTTOM should not be in the list for triangles
        assertFalse(edges.contains(SnapEdge.TOP))
        assertFalse(edges.contains(SnapEdge.BOTTOM))
    }

    // ── Additional edge tests ──────────────────────────────────────────────

    @Test
    fun `edgesForTile WINDOW_SQUARE same as SOLID_SQUARE`() {
        val solidEdges = engine.edgesForTile(TileType.SOLID_SQUARE)
        val windowEdges = engine.edgesForTile(TileType.WINDOW_SQUARE)
        assertEquals(solidEdges, windowEdges)
    }

    @Test
    fun `edgesForTile RIGHT_TRIANGLE same as EQUILATERAL_TRIANGLE`() {
        val eqEdges = engine.edgesForTile(TileType.EQUILATERAL_TRIANGLE)
        val rightEdges = engine.edgesForTile(TileType.RIGHT_TRIANGLE)
        assertEquals(eqEdges, rightEdges)
    }

    @Test
    fun `BASE edge on square target returns failure`() {
        val target = squareAt(3, 3)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.BASE
        )
        // BASE is not valid for square targets
        assertTrue("Expected failure for BASE on square", result.isFailure)
    }

    @Test
    fun `TOP edge on triangle target returns cardinal offset`() {
        val target = triangleAt(3, 3, Rotation.R0)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.EQUILATERAL_TRIANGLE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.TOP
        )
        // TOP is valid for triangle targets (treated as cardinal)
        assertTrue("Expected successful snap for TOP on triangle", result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // TOP: dx=0, dy=-3 → (3, 0)
        assertEquals(3, x)
        assertEquals(0, y)
    }

    @Test
    fun `snap position is relative to target position`() {
        // Test with a different target position
        val target = squareAt(10, 20)
        val result = engine.calculateSnapPosition(
            target = target,
            sourceType = TileType.SOLID_SQUARE,
            sourceRotation = Rotation.R0,
            edge = SnapEdge.RIGHT
        )
        assertTrue(result.isSuccess)
        val (x, y) = result.getOrDefault(0 to 0)
        // RIGHT: dx=+3 → (13, 20)
        assertEquals(13, x)
        assertEquals(20, y)
    }
}
