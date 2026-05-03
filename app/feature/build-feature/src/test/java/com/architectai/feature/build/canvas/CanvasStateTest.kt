package com.architectai.feature.build.canvas

import androidx.compose.ui.geometry.Offset
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasTileTest {

    @Test
    fun boundsCalculation_solidSquareAtOrigin() {
        val placement = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val tile = CanvasTile(placement)

        val bounds = tile.bounds
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(90f, bounds.right, 0.01f)  // 3 * 30
        assertEquals(90f, bounds.bottom, 0.01f) // 3 * 30
    }

    @Test
    fun boundsCalculation_tileAtOffset() {
        val placement = TilePlacement(TileType.SOLID_SQUARE, 5, 10, Rotation.R0, TileColor.RED)
        val tile = CanvasTile(placement)

        val bounds = tile.bounds
        assertEquals(150f, bounds.left, 0.01f)  // 5 * 30
        assertEquals(300f, bounds.top, 0.01f)   // 10 * 30
        assertEquals(240f, bounds.right, 0.01f) // (5+3) * 30
        assertEquals(390f, bounds.bottom, 0.01f) // (10+3) * 30
    }

    @Test
    fun boundsCalculation_triangleType() {
        val placement = TilePlacement(TileType.EQUILATERAL_TRIANGLE, 0, 0, Rotation.R0, TileColor.RED)
        val tile = CanvasTile(placement)

        val bounds = tile.bounds
        assertEquals(90f, bounds.width, 0.01f) // 3 * 30
        assertEquals(90f, bounds.height, 0.01f) // 3 * 30
    }

    @Test
    fun tileIdIsUnique() {
        val placement = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val tile1 = CanvasTile(placement)
        val tile2 = CanvasTile(placement)

        assert(tile1.id != tile2.id) { "Tile IDs should be unique" }
    }
}

class CanvasStateTest {

    @Test
    fun initialState_hasNoTiles() {
        val state = CanvasState()
        assertEquals(0, state.tiles.size)
        assertNull(state.selectedTileId)
    }

    @Test
    fun withTile_addsTile() {
        val state = CanvasState()
        val placement = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val tile = CanvasTile(placement)

        val newState = state.withTile(tile)
        assertEquals(1, newState.tiles.size)
        assertEquals(tile, newState.tiles.first())
    }

    @Test
    fun withTileRemoved_removesCorrectTile() {
        val p1 = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val p2 = TilePlacement(TileType.SOLID_SQUARE, 3, 0, Rotation.R0, TileColor.BLUE)
        val t1 = CanvasTile(p1)
        val t2 = CanvasTile(p2)
        val state = CanvasState(tiles = listOf(t1, t2))

        val newState = state.withTileRemoved(t1.id)
        assertEquals(1, newState.tiles.size)
        assertEquals(t2, newState.tiles.first())
    }

    @Test
    fun withTileRemoved_clearsSelectionIfRemovedTileWasSelected() {
        val placement = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val tile = CanvasTile(placement)
        val state = CanvasState(tiles = listOf(tile), selectedTileId = tile.id)

        val newState = state.withTileRemoved(tile.id)
        assertNull(newState.selectedTileId)
    }

    @Test
    fun withTileRemoved_keepsSelectionIfDifferentTileWasSelected() {
        val p1 = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val p2 = TilePlacement(TileType.SOLID_SQUARE, 3, 0, Rotation.R0, TileColor.BLUE)
        val t1 = CanvasTile(p1)
        val t2 = CanvasTile(p2)
        val state = CanvasState(tiles = listOf(t1, t2), selectedTileId = t2.id)

        val newState = state.withTileRemoved(t1.id)
        assertEquals(t2.id, newState.selectedTileId)
    }

    @Test
    fun withSelectedTile_updatesSelection() {
        val state = CanvasState()
        val newState = state.withSelectedTile("tile_42")
        assertEquals("tile_42", newState.selectedTileId)
    }

    @Test
    fun selectedTile_returnsCorrectTile() {
        val placement = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val tile = CanvasTile(placement)
        val state = CanvasState(tiles = listOf(tile), selectedTileId = tile.id)

        assertEquals(tile, state.selectedTile)
    }

    @Test
    fun selectedTile_returnsNullWhenNoneSelected() {
        val placement = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val tile = CanvasTile(placement)
        val state = CanvasState(tiles = listOf(tile))

        assertNull(state.selectedTile)
    }

    @Test
    fun withTransform_clampsScaleToMax() {
        val state = CanvasState()
        val newState = state.withTransform(scale = 10f)
        assertEquals(3f, newState.scale, 0.01f)
    }

    @Test
    fun withTransform_clampsScaleToMin() {
        val state = CanvasState()
        val newState = state.withTransform(scale = 0.1f)
        assertEquals(0.5f, newState.scale, 0.01f)
    }

    @Test
    fun withTransform_validScale() {
        val state = CanvasState()
        val newState = state.withTransform(scale = 2f, offset = Offset(10f, 20f))
        assertEquals(2f, newState.scale, 0.01f)
        assertEquals(Offset(10f, 20f), newState.offset)
    }

    @Test
    fun defaultGridSizeIs30() {
        val state = CanvasState()
        assertEquals(30, state.gridSize)
    }
}

class DragStateTest {

    @Test
    fun initialState_noDrag() {
        val state = DragState()
        assertEquals(false, state.isDragging)
        assertNull(state.draggedTileId)
        assertEquals(Offset.Zero, state.dragPosition)
        assertEquals(Offset.Zero, state.startPosition)
    }

    @Test
    fun isTileDrag_trueWhenDraggingWithTileId() {
        val state = DragState(isDragging = true, draggedTileId = "tile_1")
        assert(state.isTileDrag)
    }

    @Test
    fun isTileDrag_falseWhenDraggingWithoutTileId() {
        val state = DragState(isDragging = true, draggedTileId = null)
        assert(!state.isTileDrag)
    }

    @Test
    fun isTileDrag_falseWhenNotDragging() {
        val state = DragState(isDragging = false, draggedTileId = "tile_1")
        assert(!state.isTileDrag)
    }
}
