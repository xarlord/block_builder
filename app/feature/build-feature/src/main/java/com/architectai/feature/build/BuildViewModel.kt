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
import com.architectai.feature.build.canvas.CanvasAction
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
    val saveConfirmation: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

@HiltViewModel
class BuildViewModel @Inject constructor(
    private val compositionRepository: CompositionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuildUiState())
    val uiState: StateFlow<BuildUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<CanvasAction>(MAX_UNDO_STACK_SIZE)
    private val redoStack = ArrayDeque<CanvasAction>(MAX_UNDO_STACK_SIZE)

    /** Tracks the starting grid position of a tile being dragged for undo */
    private var dragStartX: Int = 0
    private var dragStartY: Int = 0

    companion object {
        /** 1 grid unit = 30dp at zoom 1.0 (matches CanvasState.bounds calculation) */
        const val GRID_UNIT_DP = 30f
        const val MAX_UNDO_STACK_SIZE = 50
    }

    // ─── Undo / Redo ────────────────────────────────────────────

    fun undo() {
        val action = synchronized(undoStack) {
            if (undoStack.isEmpty()) return
            undoStack.removeLast()
        } ?: return
        executeAction(action.inverse)
        synchronized(redoStack) {
            redoStack.addLast(action)
            if (redoStack.size > MAX_UNDO_STACK_SIZE) redoStack.removeFirst()
        }
        updateUndoRedoState()
    }

    fun redo() {
        val action = synchronized(redoStack) {
            if (redoStack.isEmpty()) return
            redoStack.removeLast()
        } ?: return
        executeAction(action)
        synchronized(undoStack) {
            undoStack.addLast(action)
            if (undoStack.size > MAX_UNDO_STACK_SIZE) undoStack.removeFirst()
        }
        updateUndoRedoState()
    }

    /**
     * Push an action onto the undo stack, clear redo stack, and execute it.
     */
    private fun pushAndExecute(action: CanvasAction) {
        synchronized(undoStack) {
            undoStack.addLast(action)
            if (undoStack.size > MAX_UNDO_STACK_SIZE) undoStack.removeFirst()
        }
        synchronized(redoStack) {
            redoStack.clear()
        }
        executeAction(action)
        updateUndoRedoState()
    }

    /**
     * Directly apply a CanvasAction to the state (used by undo/redo).
     */
    private fun executeAction(action: CanvasAction) {
        val currentCanvasState = _uiState.value.canvasState
        val newCanvasState = when (action) {
            is CanvasAction.AddTile -> currentCanvasState.withTile(action.tile)
            is CanvasAction.RemoveTile -> currentCanvasState.withTileRemoved(action.tileId)
            is CanvasAction.MoveTile -> {
                val updatedTiles = currentCanvasState.tiles.map { tile ->
                    if (tile.id == action.tileId) {
                        tile.copy(placement = tile.placement.copy(x = action.toX, y = action.toY))
                    } else tile
                }
                currentCanvasState.copy(tiles = updatedTiles)
            }
            is CanvasAction.RotateTile -> {
                val updatedTiles = currentCanvasState.tiles.map { tile ->
                    if (tile.id == action.tileId) {
                        tile.copy(placement = tile.placement.copy(rotation = action.toRotation))
                    } else tile
                }
                currentCanvasState.copy(tiles = updatedTiles)
            }
            is CanvasAction.ChangeColor -> {
                val updatedTiles = currentCanvasState.tiles.map { tile ->
                    if (tile.id == action.tileId) {
                        tile.copy(placement = tile.placement.copy(color = action.toColor))
                    } else tile
                }
                currentCanvasState.copy(tiles = updatedTiles)
            }
            is CanvasAction.Clear -> CanvasState()
            is CanvasAction.RestoreAll -> CanvasState(tiles = action.tiles)
            is CanvasAction.ReplaceAll -> CanvasState(tiles = action.newTiles)
        }
        _uiState.value = _uiState.value.copy(canvasState = newCanvasState)
    }

    private fun updateUndoRedoState() {
        val canUndo = synchronized(undoStack) { undoStack.isNotEmpty() }
        val canRedo = synchronized(redoStack) { redoStack.isNotEmpty() }
        _uiState.value = _uiState.value.copy(canUndo = canUndo, canRedo = canRedo)
    }

    // ─── Tile Operations ────────────────────────────────────────

    fun addTile(tileType: TileType, x: Int, y: Int) {
        viewModelScope.launch {
            val placement = TilePlacement(
                tileType = tileType,
                x = x,
                y = y,
                rotation = Rotation.R0,
                color = TileColor.RED
            )
            val canvasTile = CanvasTile(placement)
            pushAndExecute(CanvasAction.AddTile(canvasTile))
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
                pushAndExecute(CanvasAction.RemoveTile(tile.id, tile))
            }
        }
    }

    fun clearCanvas() {
        viewModelScope.launch {
            val currentState = _uiState.value.canvasState
            if (currentState.tiles.isNotEmpty()) {
                pushAndExecute(CanvasAction.Clear(currentState.tiles))
            }
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
            // Record the starting grid position for undo
            val tile = _uiState.value.canvasState.tiles.firstOrNull { it.id == tileId }
            if (tile != null) {
                dragStartX = tile.placement.x
                dragStartY = tile.placement.y
            }

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

                // Only record move action if tile actually moved
                if (gridX != dragStartX || gridY != dragStartY) {
                    val moveAction = CanvasAction.MoveTile(
                        tileId = tileId,
                        fromX = dragStartX,
                        fromY = dragStartY,
                        toX = gridX,
                        toY = gridY
                    )
                    // Apply the move directly (don't use pushAndExecute since we update tile inline)
                    synchronized(undoStack) {
                        undoStack.addLast(moveAction)
                        if (undoStack.size > MAX_UNDO_STACK_SIZE) undoStack.removeFirst()
                    }
                    synchronized(redoStack) {
                        redoStack.clear()
                    }

                    val updatedPlacement = tile.placement.copy(x = gridX, y = gridY)
                    val updatedTile = tile.copy(placement = updatedPlacement)
                    val updatedTiles = canvasState.tiles.map {
                        if (it.id == tileId) updatedTile else it
                    }

                    _uiState.value = _uiState.value.copy(
                        canvasState = canvasState.copy(tiles = updatedTiles),
                        dragState = DragState()
                    )
                    updateUndoRedoState()
                } else {
                    _uiState.value = _uiState.value.copy(dragState = DragState())
                }
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
            val existingTiles = _uiState.value.canvasState.tiles
            pushAndExecute(CanvasAction.ReplaceAll(oldTiles = existingTiles, newTiles = canvasTiles))
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
            val tile = canvasState.tiles.firstOrNull { it.id == tileId } ?: return@launch
            val fromRotation = tile.placement.rotation
            if (fromRotation != rotation) {
                pushAndExecute(CanvasAction.RotateTile(tileId, fromRotation, rotation))
            }
        }
    }

    fun updateTileColor(tileId: String, color: TileColor) {
        viewModelScope.launch {
            val canvasState = _uiState.value.canvasState
            val tile = canvasState.tiles.firstOrNull { it.id == tileId } ?: return@launch
            val fromColor = tile.placement.color
            if (fromColor != color) {
                pushAndExecute(CanvasAction.ChangeColor(tileId, fromColor, color))
            }
        }
    }

    fun clearSaveConfirmation() {
        _uiState.value = _uiState.value.copy(saveConfirmation = null)
    }

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}
