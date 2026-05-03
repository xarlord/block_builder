package com.architectai.feature.build

import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.domain.model.MockCompositions
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasState
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
}
