package com.architectai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CompositionTest {

    @Test
    fun compositionCreation() {
        val now = System.currentTimeMillis()
        val composition = Composition(
            id = "test_001",
            name = "Test Object",
            tiles = listOf(
                TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
            ),
            createdAt = now,
            updatedAt = now,
            source = Composition.Source.AI_GENERATED
        )
        assertEquals("test_001", composition.id)
        assertEquals("Test Object", composition.name)
        assertEquals(1, composition.tiles.size)
        assertEquals(Composition.Source.AI_GENERATED, composition.source)
    }

    @Test
    fun compositionSourceValues() {
        assertEquals(2, Composition.Source.entries.size)
        assertNotNull(Composition.Source.valueOf("AI_GENERATED"))
        assertNotNull(Composition.Source.valueOf("MANUAL"))
    }

    @Test
    fun compositionCopy() {
        val original = Composition(
            id = "1",
            name = "Original",
            tiles = emptyList(),
            createdAt = 0,
            updatedAt = 0,
            source = Composition.Source.MANUAL
        )
        val copied = original.copy(name = "Modified")
        assertEquals("Modified", copied.name)
        assertEquals("1", copied.id) // unchanged
    }
}

class MockCompositionsTest {

    @Test
    fun allMockCompositionsAreNonNull() {
        val all = MockCompositions.getAllMockCompositions()
        all.forEach { composition ->
            assertNotNull("Composition ${composition.id} is null", composition)
        }
    }

    @Test
    fun allMockCompositionsHaveNonEmptyTiles() {
        MockCompositions.getAllMockCompositions().forEach { composition ->
            assert(composition.tiles.isNotEmpty()) {
                "Composition ${composition.name} has no tiles"
            }
        }
    }

    @Test
    fun getCompositionByIdReturnsCorrectComposition() {
        val lion = MockCompositions.getCompositionById("mock_lion_001")
        assertNotNull(lion)
        assertEquals("Lion", lion!!.name)
    }

    @Test
    fun getCompositionByIdReturnsNullForUnknownId() {
        val result = MockCompositions.getCompositionById("nonexistent")
        assertNull(result)
    }

    @Test
    fun getCompositionByNameReturnsCorrectComposition() {
        val lion = MockCompositions.getCompositionByName("lion")
        assertNotNull(lion)
        assertEquals("mock_lion_001", lion!!.id)
    }

    @Test
    fun getCompositionByNameIsCaseInsensitive() {
        val lion = MockCompositions.getCompositionByName("LION")
        assertNotNull(lion)
        assertEquals("mock_lion_001", lion!!.id)
    }

    @Test
    fun getCompositionByNameReturnsNullForUnknown() {
        val result = MockCompositions.getCompositionByName("nonexistent")
        assertNull(result)
    }

    @Test
    fun sixMockCompositionsExist() {
        assertEquals(6, MockCompositions.getAllMockCompositions().size)
    }

    @Test
    fun allMockCompositionsHaveUniqueIds() {
        val ids = MockCompositions.getAllMockCompositions().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun allMockTilesUseValidTileTypes() {
        MockCompositions.getAllMockCompositions().forEach { composition ->
            composition.tiles.forEach { tile ->
                assert(TileType.entries.any { it == tile.tileType }) {
                    "Unknown tile type in ${composition.name}: ${tile.tileType}"
                }
            }
        }
    }

    @Test
    fun allMockTilesUseValidRotations() {
        MockCompositions.getAllMockCompositions().forEach { composition ->
            composition.tiles.forEach { tile ->
                assert(Rotation.entries.any { it == tile.rotation }) {
                    "Invalid rotation in ${composition.name}: ${tile.rotation}"
                }
            }
        }
    }

    @Test
    fun allMockTilesAreWithinGridBounds() {
        val maxGrid = 30
        MockCompositions.getAllMockCompositions().forEach { composition ->
            composition.tiles.forEach { tile ->
                assert(tile.x >= 0 && tile.x + tile.tileType.widthUnits <= maxGrid) {
                    "Tile x=${tile.x} out of bounds in ${composition.name}"
                }
                assert(tile.y >= 0 && tile.y + tile.tileType.heightUnits <= maxGrid) {
                    "Tile y=${tile.y} out of bounds in ${composition.name}"
                }
            }
        }
    }
}
