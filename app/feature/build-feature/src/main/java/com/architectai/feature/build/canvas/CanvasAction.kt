package com.architectai.feature.build.canvas

import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor

/**
 * Sealed class representing every mutable canvas operation for undo/redo support.
 * Each action knows its inverse, enabling the command pattern.
 */
sealed class CanvasAction {
    abstract val inverse: CanvasAction

    data class AddTile(val tile: CanvasTile) : CanvasAction() {
        override val inverse: CanvasAction get() = RemoveTile(tile.id, tile)
    }

    data class RemoveTile(val tileId: String, val tile: CanvasTile) : CanvasAction() {
        override val inverse: CanvasAction get() = AddTile(tile)
    }

    data class MoveTile(
        val tileId: String,
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int
    ) : CanvasAction() {
        override val inverse: CanvasAction get() = MoveTile(tileId, toX, toY, fromX, fromY)
    }

    data class RotateTile(
        val tileId: String,
        val fromRotation: Rotation,
        val toRotation: Rotation
    ) : CanvasAction() {
        override val inverse: CanvasAction get() = RotateTile(tileId, toRotation, fromRotation)
    }

    data class ChangeColor(
        val tileId: String,
        val fromColor: TileColor,
        val toColor: TileColor
    ) : CanvasAction() {
        override val inverse: CanvasAction get() = ChangeColor(tileId, toColor, fromColor)
    }

    data class Clear(val tiles: List<CanvasTile>) : CanvasAction() {
        override val inverse: CanvasAction get() = RestoreAll(tiles)
    }

    data class RestoreAll(val tiles: List<CanvasTile>) : CanvasAction() {
        override val inverse: CanvasAction get() = Clear(tiles)
    }

    /** Atomically replace all tiles — used for loading compositions. */
    data class ReplaceAll(
        val oldTiles: List<CanvasTile>,
        val newTiles: List<CanvasTile>
    ) : CanvasAction() {
        override val inverse: CanvasAction get() = ReplaceAll(newTiles, oldTiles)
    }
}
