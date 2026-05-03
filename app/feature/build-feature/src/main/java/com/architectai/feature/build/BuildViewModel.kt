package com.architectai.feature.build

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.MockCompositions
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasState
import com.architectai.feature.build.canvas.CanvasTile
import com.architectai.feature.build.canvas.DragState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BuildUiState(
    val canvasState: CanvasState = CanvasState(),
    val dragState: DragState = DragState(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class BuildViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

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

    fun endDrag() {
        viewModelScope.launch {
            // TODO: Implement snap-to-grid logic
            _uiState.value = _uiState.value.copy(
                dragState = DragState()
            )
        }
    }

    fun loadComposition(compositionId: String) {
        viewModelScope.launch {
            val composition = MockCompositions.getCompositionById(compositionId)
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
}
