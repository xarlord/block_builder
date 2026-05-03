package com.architectai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TileTypeTest {

    @Test
    fun allTileTypesHaveCorrectGridDimensions() {
        assertEquals(3, TileType.SOLID_SQUARE.widthUnits)
        assertEquals(3, TileType.SOLID_SQUARE.heightUnits)

        assertEquals(3, TileType.WINDOW_SQUARE.widthUnits)
        assertEquals(3, TileType.WINDOW_SQUARE.heightUnits)

        assertEquals(3, TileType.EQUILATERAL_TRIANGLE.widthUnits)
        assertEquals(3, TileType.EQUILATERAL_TRIANGLE.heightUnits)

        assertEquals(3, TileType.RIGHT_TRIANGLE.widthUnits)
        assertEquals(3, TileType.RIGHT_TRIANGLE.heightUnits)

        assertEquals(3, TileType.ISOSCELES_TRIANGLE.widthUnits)
        assertEquals(3, TileType.ISOSCELES_TRIANGLE.heightUnits)
    }

    @Test
    fun allTileTypesHaveUniqueId() {
        val ids = TileType.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun allTileTypesHaveNonBlankDisplayNames() {
        TileType.entries.forEach { tileType ->
            assertTrue(
                "TileType ${tileType.name} has blank displayName",
                tileType.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun allTileTypesHaveNonBlankDescriptions() {
        TileType.entries.forEach { tileType ->
            assertTrue(
                "TileType ${tileType.name} has blank description",
                tileType.description.isNotBlank()
            )
        }
    }

    @Test
    fun fiveTileTypesExist() {
        assertEquals(5, TileType.entries.size)
    }

    @Test
    fun tileTypeIdsMatchSpec() {
        assertEquals("solid_square", TileType.SOLID_SQUARE.id)
        assertEquals("window_square", TileType.WINDOW_SQUARE.id)
        assertEquals("equilateral_triangle", TileType.EQUILATERAL_TRIANGLE.id)
        assertEquals("right_triangle", TileType.RIGHT_TRIANGLE.id)
        assertEquals("isosceles_triangle", TileType.ISOSCELES_TRIANGLE.id)
    }

    @Test
    fun tileTypeCanBeFoundById() {
        TileType.entries.forEach { tileType ->
            val found = TileType.entries.firstOrNull { it.id == tileType.id }
            assertEquals(tileType, found)
        }
    }

    @Test
    fun invalidIdReturnsNull() {
        val found = TileType.entries.firstOrNull { it.id == "nonexistent" }
        assertEquals(null, found)
    }
}
