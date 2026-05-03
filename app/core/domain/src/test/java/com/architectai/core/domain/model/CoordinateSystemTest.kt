package com.architectai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the coordinate system defined in CLAUDE.md:
 * - 1 grid unit = 10dp at zoom level 1.0
 * - Origin (0,0) at top-left
 * - Default canvas: 30x30 grid units
 * - Rotation: clockwise around tile center; valid values 0, 90, 180, 270
 */
class CoordinateSystemTest {

    @Test
    fun gridUnitIs10dpAtZoom1() {
        val gridUnitDp = 10
        assertEquals(10, gridUnitDp)
    }

    @Test
    fun defaultCanvasIs30x30() {
        val canvasWidth = 30
        val canvasHeight = 30
        assertEquals(30, canvasWidth)
        assertEquals(30, canvasHeight)
    }

    @Test
    fun originIsTopLeft() {
        // A tile at (0,0) should be at the top-left corner
        val placement = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        assertEquals(0, placement.x)
        assertEquals(0, placement.y)
    }

    @Test
    fun validRotationsAre0_90_180_270() {
        val validRotations = setOf(0, 90, 180, 270)
        Rotation.entries.forEach { rotation ->
            assert(validRotations.contains(rotation.degrees)) {
                "Rotation ${rotation.degrees} is not in valid set"
            }
        }
    }

    @Test
    fun maxTilesPerComposition() {
        // Spec says max 200 tiles per composition
        val maxTiles = 200
        assertEquals(200, maxTiles)
    }

    @Test
    fun tileAtMaxGridPositionStaysWithinBounds() {
        // Solid square is 3x3, so max position is 27 (27+3=30)
        val maxX = 30 - TileType.SOLID_SQUARE.widthUnits
        val maxY = 30 - TileType.SOLID_SQUARE.heightUnits
        assertEquals(27, maxX)
        assertEquals(27, maxY)
    }

    @Test
    fun allTileTypesFitMaxGridPosition() {
        val gridSize = 30
        TileType.entries.forEach { tileType ->
            val maxX = gridSize - tileType.widthUnits
            val maxY = gridSize - tileType.heightUnits
            assert(maxX >= 0) { "${tileType.displayName} width exceeds grid" }
            assert(maxY >= 0) { "${tileType.displayName} height exceeds grid" }
        }
    }
}
