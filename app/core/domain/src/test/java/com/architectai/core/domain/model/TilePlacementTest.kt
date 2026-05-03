package com.architectai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {

    @Test
    fun allRotationsHaveCorrectDegrees() {
        assertEquals(0, Rotation.R0.degrees)
        assertEquals(90, Rotation.R90.degrees)
        assertEquals(180, Rotation.R180.degrees)
        assertEquals(270, Rotation.R270.degrees)
    }

    @Test
    fun fourRotationsExist() {
        assertEquals(4, Rotation.entries.size)
    }

    @Test
    fun rotationCanBeFoundByDegrees() {
        assertEquals(Rotation.R0, Rotation.entries.first { it.degrees == 0 })
        assertEquals(Rotation.R90, Rotation.entries.first { it.degrees == 90 })
        assertEquals(Rotation.R180, Rotation.entries.first { it.degrees == 180 })
        assertEquals(Rotation.R270, Rotation.entries.first { it.degrees == 270 })
    }

    @Test
    fun invalidDegreesReturnsNull() {
        val found = Rotation.entries.firstOrNull { it.degrees == 45 }
        assertNull(found)
    }
}

class TileColorTest {

    @Test
    fun allColorsHaveHexValues() {
        TileColor.entries.forEach { color ->
            assertTrue(
                "TileColor ${color.name} hex doesn't start with #",
                color.hex.startsWith("#")
            )
        }
    }

    @Test
    fun allColorsHaveDisplayNames() {
        TileColor.entries.forEach { color ->
            assertTrue(
                "TileColor ${color.name} has blank displayName",
                color.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun specificColorHexValues() {
        assertEquals("#A04523", TileColor.RED.hex)
        assertEquals("#F18D58", TileColor.ORANGE.hex)
        assertEquals("#F5C542", TileColor.YELLOW.hex)
        assertEquals("#4CAF50", TileColor.GREEN.hex)
        assertEquals("#2196F3", TileColor.BLUE.hex)
        assertEquals("#9C27B0", TileColor.PURPLE.hex)
        assertEquals("#8D6E63", TileColor.BROWN.hex)
        assertEquals("#000000", TileColor.BLACK.hex)
        assertEquals("#FFFFFF", TileColor.WHITE.hex)
    }
}

class TilePlacementTest {

    @Test
    fun tilePlacementCreation() {
        val placement = TilePlacement(
            tileType = TileType.SOLID_SQUARE,
            x = 5,
            y = 10,
            rotation = Rotation.R90,
            color = TileColor.RED
        )
        assertEquals(TileType.SOLID_SQUARE, placement.tileType)
        assertEquals(5, placement.x)
        assertEquals(10, placement.y)
        assertEquals(Rotation.R90, placement.rotation)
        assertEquals(TileColor.RED, placement.color)
    }

    @Test
    fun tilePlacementDataClassEquality() {
        val p1 = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val p2 = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        assertEquals(p1, p2)
    }

    @Test
    fun tilePlacementDataClassInequality() {
        val p1 = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val p2 = TilePlacement(TileType.SOLID_SQUARE, 1, 0, Rotation.R0, TileColor.RED)
        assert(p1 != p2)
    }

    @Test
    fun tilePlacementCopy() {
        val original = TilePlacement(TileType.SOLID_SQUARE, 5, 10, Rotation.R0, TileColor.RED)
        val modified = original.copy(x = 15, rotation = Rotation.R180)
        assertEquals(15, modified.x)
        assertEquals(Rotation.R180, modified.rotation)
        assertEquals(10, modified.y) // unchanged
    }
}
