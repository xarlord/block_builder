package com.architectai.feature.build

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasState
import com.architectai.feature.build.canvas.CanvasTile

@Composable
fun BuildCanvas(
    viewModel: BuildViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val canvasState = uiState.canvasState
    val dragState = uiState.dragState

    val gridUnit = 30f // 1 grid unit = 30dp at zoom 1.0

    // Local transform state for smooth zoom/pan
    var currentScale by remember { mutableFloatStateOf(canvasState.scale) }
    var currentOffset by remember { mutableStateOf(canvasState.offset) }

    // Track if a transform gesture is in progress (to suppress tap/drag)
    var isTransforming by remember { mutableStateOf(false) }

    // Haptic feedback
    val view = LocalView.current

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Zoom/pan via pinch gesture (two-finger)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    isTransforming = true
                    val newScale = (currentScale * zoom).coerceIn(0.5f, 3f)
                    val newOffset = currentOffset + pan
                    currentScale = newScale
                    currentOffset = newOffset
                    viewModel.updateCanvasTransform(newScale, newOffset)
                }
            }
            // Drag gesture for tile dragging (long press to start, or tap to select)
            .pointerInput(canvasState.tiles) {
                var dragTileId by mutableStateOf<String?>(null)
                var isLongPressDrag by mutableStateOf(false)

                detectDragGestures(
                    onDragStart = { offset ->
                        if (isTransforming) return@detectDragGestures
                        val hitTile = hitTestTile(offset, canvasState.tiles, currentScale, currentOffset)
                        if (hitTile != null) {
                            dragTileId = hitTile.id
                            isLongPressDrag = true
                            val transformedOffset = screenToCanvas(offset, currentScale, currentOffset)
                            viewModel.startDragging(hitTile.id, transformedOffset)
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragTileId != null) {
                            change.consume()
                            val transformedOffset = screenToCanvas(change.position, currentScale, currentOffset)
                            viewModel.updateDrag(transformedOffset)
                        }
                    },
                    onDragEnd = {
                        if (dragTileId != null) {
                            viewModel.endDrag()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        dragTileId = null
                        isLongPressDrag = false
                        isTransforming = false
                    },
                    onDragCancel = {
                        dragTileId = null
                        isLongPressDrag = false
                        isTransforming = false
                    }
                )
            }
            // Tap gestures for select/delete
            .pointerInput(canvasState.tiles) {
                detectTapGestures(
                    onTap = { offset ->
                        if (isTransforming) {
                            isTransforming = false
                            return@detectTapGestures
                        }
                        val hitTile = hitTestTile(offset, canvasState.tiles, currentScale, currentOffset)
                        viewModel.selectTile(hitTile?.id)
                    }
                )
            }
    ) {
        clipRect {
            translate(left = currentOffset.x, top = currentOffset.y) {
                scale(scale = currentScale, pivot = Offset.Zero) {
                    // Draw grid lines
                    val gridColor = Color(0xFFE0D8CF).copy(alpha = 0.5f)
                    for (x in 0..30) {
                        drawLine(
                            color = gridColor,
                            start = Offset(x * gridUnit, 0f),
                            end = Offset(x * gridUnit, 30 * gridUnit),
                            strokeWidth = 1f
                        )
                    }
                    for (y in 0..30) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y * gridUnit),
                            end = Offset(30 * gridUnit, y * gridUnit),
                            strokeWidth = 1f
                        )
                    }

                    // Draw each tile
                    canvasState.tiles.forEach { tile ->
                        val isBeingDragged = dragState.isTileDrag && dragState.draggedTileId == tile.id
                        val placement = tile.placement
                        val bounds = tile.bounds
                        val fillColor = try {
                            Color(android.graphics.Color.parseColor(placement.color.hex))
                        } catch (_: IllegalArgumentException) {
                            Color.Gray
                        }
                        val borderColor = Color(0xFF3D1410)
                        val tileWidth = placement.tileType.widthUnits * gridUnit
                        val tileHeight = placement.tileType.heightUnits * gridUnit

                        // When dragging, offset tile to follow drag position and add visual lift
                        val drawOffset = if (isBeingDragged && dragState.isDragging) {
                            val dragPos = dragState.dragPosition
                            val startBounds = tile.bounds
                            Offset(
                                dragPos.x - startBounds.width / 2,
                                dragPos.y - startBounds.height / 2
                            )
                        } else {
                            Offset(bounds.left, bounds.top)
                        }

                        // Draw shadow under dragged tile for lift effect
                        if (isBeingDragged && dragState.isDragging) {
                            drawTileShadow(
                                x = drawOffset.x + 4f,
                                y = drawOffset.y + 4f,
                                width = tileWidth,
                                height = tileHeight,
                                tileType = placement.tileType,
                                rotation = placement.rotation
                            )
                        }

                        when (placement.tileType) {
                            TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
                                val isTranslucent = placement.tileType == TileType.WINDOW_SQUARE
                                val alpha = if (isBeingDragged) 0.85f else 1f

                                drawRoundRect(
                                    color = if (isTranslucent) fillColor.copy(alpha = 0.6f * alpha) else fillColor.copy(alpha = alpha),
                                    topLeft = drawOffset,
                                    size = Size(bounds.width, bounds.height),
                                    cornerRadius = CornerRadius(4f)
                                )
                                drawRoundRect(
                                    color = borderColor,
                                    style = Stroke(width = 3f),
                                    cornerRadius = CornerRadius(4f),
                                    topLeft = drawOffset,
                                    size = Size(bounds.width, bounds.height)
                                )
                                if (isTranslucent) {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.2f),
                                        topLeft = Offset(drawOffset.x + 8, drawOffset.y + 8),
                                        size = Size(bounds.width - 16, bounds.height - 16),
                                        cornerRadius = CornerRadius(2f)
                                    )
                                }
                            }

                            TileType.EQUILATERAL_TRIANGLE, TileType.ISOSCELES_TRIANGLE -> {
                                val path = equilateralTrianglePath(
                                    x = drawOffset.x,
                                    y = drawOffset.y,
                                    width = tileWidth,
                                    height = tileHeight,
                                    rotation = placement.rotation
                                )
                                drawPath(path = path, color = fillColor)
                                drawPath(path = path, color = borderColor, style = Stroke(width = 3f))
                            }

                            TileType.RIGHT_TRIANGLE -> {
                                val path = rightTrianglePath(
                                    x = drawOffset.x,
                                    y = drawOffset.y,
                                    width = tileWidth,
                                    height = tileHeight,
                                    rotation = placement.rotation
                                )
                                drawPath(path = path, color = fillColor)
                                drawPath(path = path, color = borderColor, style = Stroke(width = 3f))
                            }
                        }

                        // Selection highlight
                        if (canvasState.selectedTileId == tile.id && !isBeingDragged) {
                            drawRoundRect(
                                color = Color(0xFFE0542E),
                                style = Stroke(width = 4f),
                                cornerRadius = CornerRadius(4f),
                                topLeft = Offset(bounds.left - 4, bounds.top - 4),
                                size = Size(bounds.width + 8, bounds.height + 8)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hit-test a screen-space offset against the tiles, accounting for canvas scale/offset.
 */
private fun hitTestTile(
    screenOffset: Offset,
    tiles: List<CanvasTile>,
    scale: Float,
    offset: Offset
): CanvasTile? {
    val canvasOffset = screenToCanvas(screenOffset, scale, offset)
    return tiles.firstOrNull { tile ->
        val bounds = tile.bounds
        canvasOffset.x >= bounds.left &&
            canvasOffset.x <= bounds.right &&
            canvasOffset.y >= bounds.top &&
            canvasOffset.y <= bounds.bottom
    }
}

/**
 * Convert screen coordinates to canvas coordinates.
 */
private fun screenToCanvas(screenOffset: Offset, scale: Float, offset: Offset): Offset {
    return Offset(
        (screenOffset.x - offset.x) / scale,
        (screenOffset.y - offset.y) / scale
    )
}

/**
 * Draw a shadow rect/triangle to simulate lift when dragging.
 */
private fun DrawScope.drawTileShadow(
    x: Float, y: Float,
    width: Float, height: Float,
    tileType: TileType,
    rotation: Rotation
) {
    val shadowColor = Color.Black.copy(alpha = 0.25f)
    when (tileType) {
        TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
            drawRoundRect(
                color = shadowColor,
                topLeft = Offset(x, y),
                size = Size(width, height),
                cornerRadius = CornerRadius(4f)
            )
        }
        TileType.EQUILATERAL_TRIANGLE, TileType.ISOSCELES_TRIANGLE -> {
            val path = equilateralTrianglePath(x, y, width, height, rotation)
            drawPath(path = path, color = shadowColor)
        }
        TileType.RIGHT_TRIANGLE -> {
            val path = rightTrianglePath(x, y, width, height, rotation)
            drawPath(path = path, color = shadowColor)
        }
    }
}

/**
 * Equilateral/Isosceles triangle path with rotation support.
 *
 * R0:   apex UP    (flat bottom)
 * R90:  apex RIGHT (flat left)
 * R180: apex DOWN  (flat top)
 * R270: apex LEFT  (flat right)
 */
private fun equilateralTrianglePath(
    x: Float, y: Float,
    width: Float, height: Float,
    rotation: Rotation
): Path = Path().apply {
    when (rotation) {
        Rotation.R0 -> {
            // Apex up, flat bottom
            moveTo(x + width / 2, y)
            lineTo(x + width, y + height)
            lineTo(x, y + height)
        }
        Rotation.R90 -> {
            // Apex right, flat left
            moveTo(x, y)
            lineTo(x + width, y + height / 2)
            lineTo(x, y + height)
        }
        Rotation.R180 -> {
            // Apex down, flat top
            moveTo(x, y)
            lineTo(x + width, y)
            lineTo(x + width / 2, y + height)
        }
        Rotation.R270 -> {
            // Apex left, flat right
            moveTo(x + width, y)
            lineTo(x, y + height / 2)
            lineTo(x + width, y + height)
        }
    }
    close()
}

/**
 * Right triangle path with rotation support.
 *
 * R0:   right angle at TOP-LEFT    (flat TOP + RIGHT)
 * R90:  right angle at TOP-RIGHT   (flat RIGHT + BOTTOM)
 * R180: right angle at BOTTOM-RIGHT (flat BOTTOM + LEFT)
 * R270: right angle at BOTTOM-LEFT (flat LEFT + TOP)
 */
private fun rightTrianglePath(
    x: Float, y: Float,
    width: Float, height: Float,
    rotation: Rotation
): Path = Path().apply {
    when (rotation) {
        Rotation.R0 -> {
            // Right angle at top-left
            moveTo(x, y)
            lineTo(x + width, y)
            lineTo(x + width, y + height)
        }
        Rotation.R90 -> {
            // Right angle at top-right
            moveTo(x + width, y)
            lineTo(x + width, y + height)
            lineTo(x, y + height)
        }
        Rotation.R180 -> {
            // Right angle at bottom-right
            moveTo(x + width, y + height)
            lineTo(x, y + height)
            lineTo(x, y)
        }
        Rotation.R270 -> {
            // Right angle at bottom-left
            moveTo(x, y + height)
            lineTo(x, y)
            lineTo(x + width, y)
        }
    }
    close()
}
