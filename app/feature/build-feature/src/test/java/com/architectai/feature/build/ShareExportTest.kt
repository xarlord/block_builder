package com.architectai.feature.build

import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompositionJsonCodecTest {

    @Test
    fun exportToJson_producesValidStructure() {
        val tiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, 3, 5, Rotation.R90, TileColor.BLUE)
        )
        val json = CompositionJsonCodec.exportToJson("Test Composition", tiles)
        assert(json.contains("\"version\": 1"))
        assert(json.contains("\"name\": \"Test Composition\""))
        assert(json.contains("\"SOLID_SQUARE\""))
        assert(json.contains("\"EQUILATERAL_TRIANGLE\""))
        assert(json.contains("\"R90\""))
        assert(json.contains("\"BLUE\""))
    }

    @Test
    fun exportToJson_containsCorrectTileData() {
        val tiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 10, 20, Rotation.R270, TileColor.GREEN)
        )
        val json = CompositionJsonCodec.exportToJson("Test", tiles)
        assert(json.contains("\"tileType\": \"SOLID_SQUARE\""))
        assert(json.contains("\"x\": 10"))
        assert(json.contains("\"y\": 20"))
        assert(json.contains("\"rotation\": \"R270\""))
        assert(json.contains("\"color\": \"GREEN\""))
    }

    @Test
    fun importFromJson_roundTrip_preservesData() {
        val originalTiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.WINDOW_SQUARE, 3, 3, Rotation.R90, TileColor.BLUE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, 6, 6, Rotation.R180, TileColor.GREEN),
            TilePlacement(TileType.RIGHT_TRIANGLE, 9, 9, Rotation.R270, TileColor.ORANGE),
            TilePlacement(TileType.ISOSCELES_TRIANGLE, 12, 12, Rotation.R0, TileColor.PURPLE)
        )
        val json = CompositionJsonCodec.exportToJson("Round Trip Test", originalTiles)
        val result = CompositionJsonCodec.importFromJson(json)

        assertNotNull(result)
        val (name, importedTiles) = result!!
        assertEquals("Round Trip Test", name)
        assertEquals(originalTiles.size, importedTiles.size)

        originalTiles.zip(importedTiles).forEach { (original, imported) ->
            assertEquals(original.tileType, imported.tileType)
            assertEquals(original.x, imported.x)
            assertEquals(original.y, imported.y)
            assertEquals(original.rotation, imported.rotation)
            assertEquals(original.color, imported.color)
        }
    }

    @Test
    fun importFromJson_invalidJson_returnsNull() {
        assertNull(CompositionJsonCodec.importFromJson("not json"))
    }

    @Test
    fun importFromJson_wrongVersion_returnsNull() {
        val json = """{"version": 99, "name": "Test", "tiles": []}"""
        assertNull(CompositionJsonCodec.importFromJson(json))
    }

    @Test
    fun importFromJson_emptyTiles_returnsNull() {
        val json = """{"version": 1, "name": "Test", "tiles": []}"""
        assertNull(CompositionJsonCodec.importFromJson(json))
    }

    @Test
    fun importFromJson_skipsUnknownTileTypes() {
        val json = """{
            "version": 1,
            "name": "Test",
            "tiles": [
                {"tileType": "SOLID_SQUARE", "x": 0, "y": 0, "rotation": "R0", "color": "RED"},
                {"tileType": "UNKNOWN_TYPE", "x": 3, "y": 3, "rotation": "R0", "color": "BLUE"},
                {"tileType": "EQUILATERAL_TRIANGLE", "x": 6, "y": 6, "rotation": "R90", "color": "GREEN"}
            ]
        }"""
        val result = CompositionJsonCodec.importFromJson(json)
        assertNotNull(result)
        val (_, tiles) = result!!
        assertEquals(2, tiles.size)
        assertEquals(TileType.SOLID_SQUARE, tiles[0].tileType)
        assertEquals(TileType.EQUILATERAL_TRIANGLE, tiles[1].tileType)
    }

    @Test
    fun importFromJson_defaultsUnknownRotationAndColor() {
        val json = """{
            "version": 1,
            "name": "Test",
            "tiles": [
                {"tileType": "SOLID_SQUARE", "x": 0, "y": 0, "rotation": "INVALID", "color": "INVALID_COLOR"}
            ]
        }"""
        val result = CompositionJsonCodec.importFromJson(json)
        assertNotNull(result)
        val (_, tiles) = result!!
        assertEquals(Rotation.R0, tiles[0].rotation)
        assertEquals(TileColor.RED, tiles[0].color)
    }
}
