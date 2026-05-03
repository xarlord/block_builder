package com.architectai.feature.build

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasState

@Composable
fun BuildCanvas(
    viewModel: BuildViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val canvasState = uiState.canvasState

    val gridUnit = 30f // 1 grid unit = 30dp at zoom 1.0

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        viewModel.selectTile(
                            canvasState.tiles.firstOrNull { tile ->
                                val bounds = tile.bounds
                                offset.x >= bounds.left &&
                                    offset.x <= bounds.right &&
                                    offset.y >= bounds.top &&
                                    offset.y <= bounds.bottom
                            }?.id
                        )
                    }
                )
            }
    ) {
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

            when (placement.tileType) {
                TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
                    val isTranslucent = placement.tileType == TileType.WINDOW_SQUARE

                    drawRoundRect(
                        color = if (isTranslucent) fillColor.copy(alpha = 0.6f) else fillColor,
                        topLeft = Offset(bounds.left, bounds.top),
                        size = Size(bounds.width, bounds.height),
                        cornerRadius = CornerRadius(4f)
                    )
                    drawRoundRect(
                        color = borderColor,
                        style = Stroke(width = 3f),
                        cornerRadius = CornerRadius(4f),
                        topLeft = Offset(bounds.left, bounds.top),
                        size = Size(bounds.width, bounds.height)
                    )
                    if (isTranslucent) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.2f),
                            topLeft = Offset(bounds.left + 8, bounds.top + 8),
                            size = Size(bounds.width - 16, bounds.height - 16),
                            cornerRadius = CornerRadius(2f)
                        )
                    }
                }

                TileType.EQUILATERAL_TRIANGLE, TileType.ISOSCELES_TRIANGLE -> {
                    val path = equilateralTrianglePath(
                        x = bounds.left,
                        y = bounds.top,
                        width = tileWidth,
                        height = tileHeight,
                        rotation = placement.rotation
                    )
                    drawPath(path = path, color = fillColor)
                    drawPath(path = path, color = borderColor, style = Stroke(width = 3f))
                }

                TileType.RIGHT_TRIANGLE -> {
                    val path = rightTrianglePath(
                        x = bounds.left,
                        y = bounds.top,
                        width = tileWidth,
                        height = tileHeight,
                        rotation = placement.rotation
                    )
                    drawPath(path = path, color = fillColor)
                    drawPath(path = path, color = borderColor, style = Stroke(width = 3f))
                }
            }

            // Selection highlight
            if (canvasState.selectedTileId == tile.id) {
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
