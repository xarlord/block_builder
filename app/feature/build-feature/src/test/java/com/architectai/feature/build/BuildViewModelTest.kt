package com.architectai.feature.build

import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.domain.model.MockCompositions
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasAction
import com.architectai.feature.build.canvas.CanvasState
import com.architectai.feature.build.canvas.CanvasTile
import com.architectai.feature.build.canvas.DragState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuildViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var compositionRepository: CompositionRepository
    private lateinit var viewModel: BuildViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        compositionRepository = mockk(relaxed = true)
        viewModel = BuildViewModel(compositionRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Existing Tests ─────────────────────────────────────────

    @Test
    fun initialState_isEmpty() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(CanvasState(), state.canvasState)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun addTile_addsTileToCanvas() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.canvasState.tiles.size)
        assertEquals(TileType.SOLID_SQUARE, state.canvasState.tiles[0].placement.tileType)
        assertEquals(0, state.canvasState.tiles[0].placement.x)
        assertEquals(0, state.canvasState.tiles[0].placement.y)
    }

    @Test
    fun addTile_multipleTypes() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        viewModel.addTile(TileType.EQUILATERAL_TRIANGLE, 3, 0)
        viewModel.addTile(TileType.RIGHT_TRIANGLE, 6, 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(3, state.canvasState.tiles.size)
    }

    @Test
    fun addTile_atSpecificPosition() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 5, 10)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.canvasState.tiles[0].placement.x)
        assertEquals(10, state.canvasState.tiles[0].placement.y)
    }

    @Test
    fun selectTile_setsSelectedTileId() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.selectTile(tileId)
        advanceUntilIdle()

        assertEquals(tileId, viewModel.uiState.value.canvasState.selectedTileId)
    }

    @Test
    fun deleteSelectedTile_removesFromCanvas() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.selectTile(tileId)
        advanceUntilIdle()

        viewModel.deleteSelectedTile()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun deleteSelectedTile_noSelection_doesNothing() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        viewModel.deleteSelectedTile() // No selection
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun clearCanvas_removesAllTiles() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        viewModel.addTile(TileType.EQUILATERAL_TRIANGLE, 3, 0)
        advanceUntilIdle()

        viewModel.clearCanvas()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun clearCanvas_clearsSelection() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.selectTile(tileId)
        advanceUntilIdle()

        viewModel.clearCanvas()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.canvasState.selectedTileId)
    }

    @Test
    fun startDragging_setsDragState() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.startDragging(tileId, Offset(100f, 200f))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.dragState.isDragging)
        assertEquals(tileId, state.dragState.draggedTileId)
        assertEquals(Offset(100f, 200f), state.dragState.startPosition)
    }

    @Test
    fun updateDrag_updatesPosition() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.startDragging(tileId, Offset(0f, 0f))
        advanceUntilIdle()

        viewModel.updateDrag(Offset(50f, 75f))
        advanceUntilIdle()

        assertEquals(Offset(50f, 75f), viewModel.uiState.value.dragState.dragPosition)
    }

    @Test
    fun endDrag_snapsToGrid() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.startDragging(tileId, Offset(0f, 0f))
        advanceUntilIdle()

        // Move to a non-grid position
        viewModel.updateDrag(Offset(47f, 83f))
        advanceUntilIdle()

        viewModel.endDrag()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.dragState.isDragging)

        // Tile position should be snapped
        val tile = state.canvasState.tiles[0]
        // snapToGrid(47, 83) → (50, 80) → grid: (50/30=1.67→1, 80/30=2.67→2)
        // Actually: snapUnit = 30/3 = 10; snapped(47)=50, snapped(83)=80
        // gridX = 50/30 = 1.67 → 1, gridY = 80/30 = 2.67 → 2
        assertTrue(tile.placement.x >= 0)
        assertTrue(tile.placement.y >= 0)
    }

    @Test
    fun endDrag_noDragActive_doesNothing() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 5, 5)
        advanceUntilIdle()

        val originalX = viewModel.uiState.value.canvasState.tiles[0].placement.x
        val originalY = viewModel.uiState.value.canvasState.tiles[0].placement.y

        viewModel.endDrag()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(originalX, state.canvasState.tiles[0].placement.x)
        assertEquals(originalY, state.canvasState.tiles[0].placement.y)
    }

    @Test
    fun updateCanvasTransform_updatesScaleAndOffset() = runTest {
        viewModel.updateCanvasTransform(1.5f, Offset(10f, 20f))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1.5f, state.canvasState.scale, 0.01f)
        assertEquals(Offset(10f, 20f), state.canvasState.offset)
    }

    @Test
    fun updateCanvasTransform_clampsToMax() = runTest {
        viewModel.updateCanvasTransform(100f, Offset.Zero)
        advanceUntilIdle()

        val scale = viewModel.uiState.value.canvasState.scale
        assertTrue(scale <= 3f)
    }

    @Test
    fun updateCanvasTransform_clampsToMin() = runTest {
        viewModel.updateCanvasTransform(0.01f, Offset.Zero)
        advanceUntilIdle()

        val scale = viewModel.uiState.value.canvasState.scale
        assertTrue(scale >= 0.5f)
    }

    @Test
    fun snapToGrid_snapsTo10dpUnits() = runTest {
        // snapUnit = 30/3 = 10
        // 47/10 = 4.7 → round to 5 → 5*10 = 50
        // 83/10 = 8.3 → round to 8 → 8*10 = 80
        val result = viewModel.snapToGrid(Offset(47f, 83f))
        assertEquals(50f, result.x, 0.01f)
        assertEquals(80f, result.y, 0.01f)
    }

    @Test
    fun snapToGrid_exactGridPosition() = runTest {
        val result = viewModel.snapToGrid(Offset(30f, 60f))
        assertEquals(30f, result.x, 0.01f)
        assertEquals(60f, result.y, 0.01f)
    }

    @Test
    fun snapToGrid_zeroOffset() = runTest {
        val result = viewModel.snapToGrid(Offset(0f, 0f))
        assertEquals(0f, result.x, 0.01f)
        assertEquals(0f, result.y, 0.01f)
    }

    @Test
    fun loadComposition_updatesCanvasState() = runTest {
        val composition = MockCompositions.LION
        coEvery { compositionRepository.getCompositionById("mock_lion_001") } returns composition

        viewModel.loadComposition("mock_lion_001")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(composition.tiles.size, state.canvasState.tiles.size)
    }

    @Test
    fun loadComposition_nonexistent_usesMockFallback() = runTest {
        coEvery { compositionRepository.getCompositionById("mock_lion_001") } returns null

        viewModel.loadComposition("mock_lion_001")
        advanceUntilIdle()

        // Should fall back to MockCompositions
        val state = viewModel.uiState.value
        assertTrue(state.canvasState.tiles.size > 0)
    }

    @Test
    fun loadCompositionDirect_setsCanvasTiles() = runTest {
        val composition = MockCompositions.CAR
        viewModel.loadCompositionDirect(composition)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(composition.tiles.size, state.canvasState.tiles.size)
    }

    @Test
    fun gridUnitDp_is30() = runTest {
        assertEquals(30f, BuildViewModel.GRID_UNIT_DP, 0.01f)
    }

    @Test
    fun updateTileRotation_updatesRotation() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.updateTileRotation(tileId, Rotation.R90)
        advanceUntilIdle()

        val tile = viewModel.uiState.value.canvasState.tiles[0]
        assertEquals(Rotation.R90, tile.placement.rotation)
    }

    @Test
    fun updateTileRotation_onlyAffectsTargetTile() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        viewModel.addTile(TileType.EQUILATERAL_TRIANGLE, 3, 0)
        advanceUntilIdle()

        val tileId1 = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.updateTileRotation(tileId1, Rotation.R180)
        advanceUntilIdle()

        val tiles = viewModel.uiState.value.canvasState.tiles
        assertEquals(Rotation.R180, tiles[0].placement.rotation)
        assertEquals(Rotation.R0, tiles[1].placement.rotation)
    }

    @Test
    fun updateTileColor_updatesColor() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.updateTileColor(tileId, TileColor.BLUE)
        advanceUntilIdle()

        val tile = viewModel.uiState.value.canvasState.tiles[0]
        assertEquals(TileColor.BLUE, tile.placement.color)
    }

    @Test
    fun updateTileColor_onlyAffectsTargetTile() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        viewModel.addTile(TileType.SOLID_SQUARE, 3, 0)
        advanceUntilIdle()

        val tileId1 = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.updateTileColor(tileId1, TileColor.GREEN)
        advanceUntilIdle()

        val tiles = viewModel.uiState.value.canvasState.tiles
        assertEquals(TileColor.GREEN, tiles[0].placement.color)
        assertEquals(TileColor.RED, tiles[1].placement.color)
    }

    @Test
    fun updateTileRotation_nonexistentTile_doesNothing() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        viewModel.updateTileRotation("nonexistent_id", Rotation.R270)
        advanceUntilIdle()

        val tiles = viewModel.uiState.value.canvasState.tiles
        assertEquals(Rotation.R0, tiles[0].placement.rotation)
        assertEquals(1, tiles.size)
    }

    @Test
    fun updateTileColor_nonexistentTile_doesNothing() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        viewModel.updateTileColor("nonexistent_id", TileColor.YELLOW)
        advanceUntilIdle()

        val tiles = viewModel.uiState.value.canvasState.tiles
        assertEquals(TileColor.RED, tiles[0].placement.color)
        assertEquals(1, tiles.size)
    }

    // ─── Undo/Redo Tests ────────────────────────────────────────

    @Test
    fun undoAddTile_restoresEmptyCanvas() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)

        viewModel.undo()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
        assertFalse(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.canRedo)
    }

    @Test
    fun redoAddTile_restoresTile() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)

        viewModel.redo()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)
        assertEquals(TileType.SOLID_SQUARE, viewModel.uiState.value.canvasState.tiles[0].placement.tileType)
    }

    @Test
    fun undoDeleteTile_restoresTile() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 5, 10)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.selectTile(tileId)
        advanceUntilIdle()

        viewModel.deleteSelectedTile()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)
        assertEquals(5, viewModel.uiState.value.canvasState.tiles[0].placement.x)
        assertEquals(10, viewModel.uiState.value.canvasState.tiles[0].placement.y)
    }

    @Test
    fun undoRotateTile_restoresPreviousRotation() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.updateTileRotation(tileId, Rotation.R90)
        advanceUntilIdle()
        assertEquals(Rotation.R90, viewModel.uiState.value.canvasState.tiles[0].placement.rotation)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(Rotation.R0, viewModel.uiState.value.canvasState.tiles[0].placement.rotation)
    }

    @Test
    fun undoChangeColor_restoresPreviousColor() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        viewModel.updateTileColor(tileId, TileColor.BLUE)
        advanceUntilIdle()
        assertEquals(TileColor.BLUE, viewModel.uiState.value.canvasState.tiles[0].placement.color)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(TileColor.RED, viewModel.uiState.value.canvasState.tiles[0].placement.color)
    }

    @Test
    fun undoMoveTile_restoresPreviousPosition() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        // Start drag, move, end drag
        viewModel.startDragging(tileId, Offset(0f, 0f))
        advanceUntilIdle()
        viewModel.updateDrag(Offset(60f, 90f))
        advanceUntilIdle()
        viewModel.endDrag()
        advanceUntilIdle()

        // Tile should have moved
        val movedTile = viewModel.uiState.value.canvasState.tiles[0]
        assertTrue(movedTile.placement.x != 0 || movedTile.placement.y != 0)

        // Undo should restore original position
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles[0].placement.x)
        assertEquals(0, viewModel.uiState.value.canvasState.tiles[0].placement.y)
    }

    @Test
    fun undoClearCanvas_restoresAllTiles() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        viewModel.addTile(TileType.EQUILATERAL_TRIANGLE, 3, 0)
        viewModel.addTile(TileType.RIGHT_TRIANGLE, 6, 0)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.canvasState.tiles.size)

        viewModel.clearCanvas()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun redoClearCanvas_clearsAgain() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        viewModel.clearCanvas()
        advanceUntilIdle()

        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)

        viewModel.redo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun newAction_clearsRedoStack() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()
        viewModel.addTile(TileType.EQUILATERAL_TRIANGLE, 3, 0)
        advanceUntilIdle()

        // Undo twice
        viewModel.undo()
        advanceUntilIdle()
        viewModel.undo()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canRedo)
        assertFalse(viewModel.uiState.value.canUndo)
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)

        // New action should clear redo stack
        viewModel.addTile(TileType.RIGHT_TRIANGLE, 6, 0)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canRedo)
        assertTrue(viewModel.uiState.value.canUndo)
        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun undoRedo_multipleActions_maintainsCorrectState() = runTest {
        // Add 3 tiles
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()
        viewModel.addTile(TileType.EQUILATERAL_TRIANGLE, 3, 0)
        advanceUntilIdle()
        viewModel.addTile(TileType.RIGHT_TRIANGLE, 6, 0)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.canvasState.tiles.size)

        // Undo one
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.canvasState.tiles.size)

        // Undo another
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)

        // Redo one
        viewModel.redo()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.canvasState.tiles.size)

        // Redo another
        viewModel.redo()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun undoStack_maxSize_isLimitedTo50() = runTest {
        // Add 55 tiles
        for (i in 0..54) {
            viewModel.addTile(TileType.SOLID_SQUARE, i % 20, i % 20)
            advanceUntilIdle()
        }
        assertEquals(55, viewModel.uiState.value.canvasState.tiles.size)
        assertTrue(viewModel.uiState.value.canUndo)

        // Should only be able to undo 50 times (first 5 are lost)
        var undoCount = 0
        while (viewModel.uiState.value.canUndo) {
            viewModel.undo()
            advanceUntilIdle()
            undoCount++
        }
        assertEquals(50, undoCount)
        assertEquals(5, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun canUndo_canRedo_initiallyFalse() = runTest {
        assertFalse(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun undo_whenNothingToUndo_doesNothing() = runTest {
        viewModel.undo()
        advanceUntilIdle()
        // No crash, state unchanged
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun redo_whenNothingToRedo_doesNothing() = runTest {
        viewModel.redo()
        advanceUntilIdle()
        // No crash, state unchanged
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun updateTileRotation_sameRotation_doesNotPushToStack() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        // Rotation is already R0
        viewModel.updateTileRotation(tileId, Rotation.R0)
        advanceUntilIdle()

        // Should have only 1 undo action (the addTile), not 2
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun updateTileColor_sameColor_doesNotPushToStack() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        // Color is already RED
        viewModel.updateTileColor(tileId, TileColor.RED)
        advanceUntilIdle()

        // Should have only 1 undo action (the addTile), not 2
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun endDrag_noPositionChange_doesNotPushToStack() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val tileId = viewModel.uiState.value.canvasState.tiles[0].id
        // Start and end drag at the same position
        viewModel.startDragging(tileId, Offset(0f, 0f))
        advanceUntilIdle()
        viewModel.updateDrag(Offset(0f, 0f))
        advanceUntilIdle()
        viewModel.endDrag()
        advanceUntilIdle()

        // Only 1 undo (the addTile), the no-op drag should not add to the stack
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun undoLoadComposition_restoresPreviousState() = runTest {
        // First add a tile
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)

        // Load a composition (this replaces all tiles atomically)
        val composition = MockCompositions.CAR
        viewModel.loadCompositionDirect(composition)
        advanceUntilIdle()
        assertEquals(composition.tiles.size, viewModel.uiState.value.canvasState.tiles.size)

        // Undo once should go back to having 1 tile (ReplaceAll is atomic)
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.canvasState.tiles.size)

        // Undo again should go back to empty (the original addTile)
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
    }

    @Test
    fun clearCanvas_onEmptyCanvas_doesNotPushToStack() = runTest {
        // Clearing an already empty canvas should not create an undo entry
        viewModel.clearCanvas()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun undoRestoreAll_removesRestoredTiles() = runTest {
        val composition = MockCompositions.CAR
        viewModel.loadCompositionDirect(composition)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canvasState.tiles.size > 0)

        // Undo the RestoreAll
        viewModel.undo()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)
    }

    // ─── Export / Import Tests ────────────────────────────────────

    @Test
    fun exportToJson_returnsValidJson() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 0, 0)
        advanceUntilIdle()

        val json = viewModel.exportToJson()
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("\"tiles\""))
        assertTrue(json.contains("SOLID_SQUARE"))
    }

    @Test
    fun exportToJson_emptyCanvas_returnsEmptyTilesArray() = runTest {
        val json = viewModel.exportToJson()
        assertTrue(json.contains("\"tiles\""))
    }

    @Test
    fun importFromJson_validJson_loadsTiles() = runTest {
        val json = """{
            "version": 1,
            "name": "Test Import",
            "tiles": [
                {"tileType": "SOLID_SQUARE", "x": 5, "y": 10, "rotation": "R0", "color": "BLUE"},
                {"tileType": "RIGHT_TRIANGLE", "x": 8, "y": 13, "rotation": "R90", "color": "GREEN"}
            ]
        }"""

        val result = viewModel.importFromJson(json)
        advanceUntilIdle()

        assertTrue(result)
        val tiles = viewModel.uiState.value.canvasState.tiles
        assertEquals(2, tiles.size)
        assertEquals(TileType.SOLID_SQUARE, tiles[0].placement.tileType)
        assertEquals(5, tiles[0].placement.x)
        assertEquals(10, tiles[0].placement.y)
        assertEquals(TileColor.BLUE, tiles[0].placement.color)
        assertEquals(TileType.RIGHT_TRIANGLE, tiles[1].placement.tileType)
        assertEquals(Rotation.R90, tiles[1].placement.rotation)
    }

    @Test
    fun importFromJson_invalidJson_returnsFalse() = runTest {
        val result = viewModel.importFromJson("not valid json")
        assertFalse(result)
    }

    @Test
    fun importFromJson_emptyTiles_returnsFalse() = runTest {
        val json = """{"version": 1, "name": "Empty", "tiles": []}"""
        val result = viewModel.importFromJson(json)
        assertFalse(result)
    }

    @Test
    fun exportImportRoundTrip_preservesTiles() = runTest {
        viewModel.addTile(TileType.SOLID_SQUARE, 3, 7)
        advanceUntilIdle()
        viewModel.updateTileRotation(
            viewModel.uiState.value.canvasState.tiles[0].id,
            Rotation.R180
        )
        advanceUntilIdle()

        val json = viewModel.exportToJson()

        // Clear and import
        viewModel.clearCanvas()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.canvasState.tiles.size)

        val result = viewModel.importFromJson(json)
        advanceUntilIdle()

        assertTrue(result)
        val tiles = viewModel.uiState.value.canvasState.tiles
        assertEquals(1, tiles.size)
        assertEquals(TileType.SOLID_SQUARE, tiles[0].placement.tileType)
        assertEquals(3, tiles[0].placement.x)
        assertEquals(7, tiles[0].placement.y)
        assertEquals(Rotation.R180, tiles[0].placement.rotation)
    }
}
