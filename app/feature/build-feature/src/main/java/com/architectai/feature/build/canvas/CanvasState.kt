package com.architectai.feature.build.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.architectai.core.domain.model.TilePlacement
import java.util.concurrent.atomic.AtomicLong

private val tileIdCounter = AtomicLong(0)

data class CanvasTile(
    val placement: TilePlacement,
    val id: String = "tile_${tileIdCounter.incrementAndGet()}"
) {
    val bounds: Rect
        get() {
            val gridUnit = 30f
            return Rect(
                left = placement.x * gridUnit,
                top = placement.y * gridUnit,
                right = (placement.x + placement.tileType.widthUnits) * gridUnit,
                bottom = (placement.y + placement.tileType.heightUnits) * gridUnit
            )
        }
}

data class CanvasState(
    val tiles: List<CanvasTile> = emptyList(),
    val selectedTileId: String? = null,
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
    val gridSize: Int = 30 // 30x30 grid units
) {
    val selectedTile: CanvasTile?
        get() = tiles.firstOrNull { it.id == selectedTileId }

    fun withTile(tile: CanvasTile): CanvasState {
        return copy(tiles = tiles + tile)
    }

    fun withTileRemoved(tileId: String): CanvasState {
        return copy(
            tiles = tiles.filterNot { it.id == tileId },
            selectedTileId = if (selectedTileId == tileId) null else selectedTileId
        )
    }

    fun withSelectedTile(tileId: String?): CanvasState {
        return copy(selectedTileId = tileId)
    }

    fun withTransform(scale: Float = this.scale, offset: Offset = this.offset): CanvasState {
        return copy(scale = scale.coerceIn(0.5f, 3f), offset = offset)
    }
}

data class DragState(
    val isDragging: Boolean = false,
    val draggedTileId: String? = null,
    val dragPosition: Offset = Offset.Zero,
    val startPosition: Offset = Offset.Zero
) {
    val isTileDrag: Boolean
        get() = isDragging && draggedTileId != null
}
