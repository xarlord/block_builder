package com.architectai.feature.build

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.MockCompositions
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasState
import com.architectai.feature.build.canvas.CanvasTile
import com.architectai.feature.build.canvas.DragState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BuildUiState(
    val canvasState: CanvasState = CanvasState(),
    val dragState: DragState = DragState(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveConfirmation: String? = null
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val compositionRepository: CompositionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    companion object {
        /** 1 grid unit = 30dp at zoom 1.0 (matches CanvasState.bounds calculation) */
        const val GRID_UNIT_DP = 30f
    }

    fun addTile(tileType: TileType, x: Int, y: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value.canvasState
            val placement = TilePlacement(
                tileType = tileType,
                x = x,
                y = y,
                rotation = Rotation.R0,
                color = TileColor.RED
            )
            val canvasTile = CanvasTile(placement)
            _uiState.value = _uiState.value.copy(
                canvasState = currentState.withTile(canvasTile)
            )
        }
    }

    fun selectTile(tileId: String?) {
        viewModelScope.launch {
            val currentState = _uiState.value.canvasState
            _uiState.value = _uiState.value.copy(
                canvasState = currentState.withSelectedTile(tileId)
            )
        }
    }

    fun deleteSelectedTile() {
        viewModelScope.launch {
            val currentState = _uiState.value.canvasState
            currentState.selectedTile?.let { tile ->
                _uiState.value = _uiState.value.copy(
                    canvasState = currentState.withTileRemoved(tile.id)
                )
            }
        }
    }

    fun clearCanvas() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                canvasState = CanvasState()
            )
        }
    }

    fun updateCanvasTransform(scale: Float, offset: Offset) {
        viewModelScope.launch {
            val currentState = _uiState.value.canvasState
            _uiState.value = _uiState.value.copy(
                canvasState = currentState.withTransform(scale, offset)
            )
        }
    }

    fun startDragging(tileId: String, position: Offset) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                dragState = DragState(
                    isDragging = true,
                    draggedTileId = tileId,
                    dragPosition = position,
                    startPosition = position
                )
            )
        }
    }

    fun updateDrag(position: Offset) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                dragState = _uiState.value.dragState.copy(
                    dragPosition = position
                )
            )
        }
    }

    /**
     * End drag with snap-to-grid logic.
     * Snaps the tile to the nearest grid position where 1 grid unit = 10dp
     * (which is GRID_UNIT_DP / 3 since each tile is 3 units wide).
     */
    fun endDrag() {
        viewModelScope.launch {
            val dragState = _uiState.value.dragState
            if (!dragState.isTileDrag) {
                _uiState.value = _uiState.value.copy(dragState = DragState())
                return@launch
            }

            val tileId = dragState.draggedTileId ?: run {
                _uiState.value = _uiState.value.copy(dragState = DragState())
                return@launch
            }

            val canvasState = _uiState.value.canvasState
            val tile = canvasState.tiles.firstOrNull { it.id == tileId }

            if (tile != null) {
                val snapPosition = snapToGrid(dragState.dragPosition)
                val gridX = (snapPosition.x / GRID_UNIT_DP).toInt().coerceIn(0, canvasState.gridSize - tile.placement.tileType.widthUnits)
                val gridY = (snapPosition.y / GRID_UNIT_DP).toInt().coerceIn(0, canvasState.gridSize - tile.placement.tileType.heightUnits)

                val updatedPlacement = tile.placement.copy(x = gridX, y = gridY)
                val updatedTile = tile.copy(placement = updatedPlacement)
                val updatedTiles = canvasState.tiles.map {
                    if (it.id == tileId) updatedTile else it
                }

                _uiState.value = _uiState.value.copy(
                    canvasState = canvasState.copy(tiles = updatedTiles),
                    dragState = DragState()
                )
            } else {
                _uiState.value = _uiState.value.copy(dragState = DragState())
            }
        }
    }

    /**
     * Snap a pixel offset to the nearest grid position.
     * 1 grid unit = 10dp, so we snap to multiples of (GRID_UNIT_DP / 3).
     */
    internal fun snapToGrid(position: Offset): Offset {
        val snapUnit = GRID_UNIT_DP / 3f // 10dp per grid unit
        val snappedX = (position.x / snapUnit).roundToInt() * snapUnit
        val snappedY = (position.y / snapUnit).roundToInt() * snapUnit
        return Offset(snappedX, snappedY)
    }

    fun loadComposition(compositionId: String) {
        viewModelScope.launch {
            val composition = compositionRepository.getCompositionById(compositionId)
                ?: MockCompositions.getCompositionById(compositionId)
            composition?.let { comp ->
                loadCompositionDirect(comp)
            }
        }
    }

    fun loadCompositionDirect(composition: Composition) {
        viewModelScope.launch {
            val canvasTiles = composition.tiles.map { CanvasTile(it) }
            _uiState.value = _uiState.value.copy(
                canvasState = CanvasState(tiles = canvasTiles)
            )
        }
    }

    fun saveComposition(name: String) {
        viewModelScope.launch {
            val canvasState = _uiState.value.canvasState
            if (canvasState.tiles.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    error = "Cannot save an empty composition"
                )
                return@launch
            }
            val now = System.currentTimeMillis()
            val tilePlacements = canvasState.tiles.map { it.placement }
            val composition = Composition(
                id = "comp_${now}",
                name = name.ifBlank { "Untitled ${java.text.SimpleDateFormat("MMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date(now))}" },
                tiles = tilePlacements,
                createdAt = now,
                updatedAt = now,
                source = Composition.Source.MANUAL
            )
            compositionRepository.saveComposition(composition)
            _uiState.value = _uiState.value.copy(
                saveConfirmation = "Saved \"${composition.name}\" to Gallery"
            )
        }
    }

    fun updateTileRotation(tileId: String, rotation: Rotation) {
        viewModelScope.launch {
            val canvasState = _uiState.value.canvasState
            val updatedTiles = canvasState.tiles.map { tile ->
                if (tile.id == tileId) {
                    tile.copy(placement = tile.placement.copy(rotation = rotation))
                } else tile
            }
            _uiState.value = _uiState.value.copy(
                canvasState = canvasState.copy(tiles = updatedTiles)
            )
        }
    }

    fun updateTileColor(tileId: String, color: TileColor) {
        viewModelScope.launch {
            val canvasState = _uiState.value.canvasState
            val updatedTiles = canvasState.tiles.map { tile ->
                if (tile.id == tileId) {
                    tile.copy(placement = tile.placement.copy(color = color))
                } else tile
            }
            _uiState.value = _uiState.value.copy(
                canvasState = canvasState.copy(tiles = updatedTiles)
            )
        }
    }

    fun clearSaveConfirmation() {
        _uiState.value = _uiState.value.copy(saveConfirmation = null)
    }

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}
