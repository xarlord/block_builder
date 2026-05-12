package com.architectai.feature.build

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileType
import com.architectai.feature.build.canvas.CanvasTile
import java.io.ByteArrayOutputStream

/**
 * Utility to render a list of CanvasTile objects to a Bitmap using native Android Canvas.
 * This replicates the same drawing logic as BuildCanvas composable but can be called
 * from a ViewModel or Activity context (no Compose required).
 */
object CanvasCapture {

    private const val GRID_UNIT = 30f
    private const val GRID_SIZE = 30

    /**
     * Render tiles to a 900x900 ARGB_8888 Bitmap.
     * Draws background, grid lines, and each tile with its fill color + border.
     */
    fun renderToBitmap(tiles: List<CanvasTile>): Bitmap {
        val canvasSize = (GRID_SIZE * GRID_UNIT).toInt() // 900x900
        val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Background color: #FFF5F2
        canvas.drawColor(0xFFFFF5F2.toInt())

        // Grid lines
        val gridPaint = Paint().apply {
            color = 0xFFE0D8CF.toInt()
            alpha = 128 // ~0.5 alpha
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        for (x in 0..GRID_SIZE) {
            canvas.drawLine(
                x * GRID_UNIT, 0f,
                x * GRID_UNIT, GRID_SIZE * GRID_UNIT,
                gridPaint
            )
        }
        for (y in 0..GRID_SIZE) {
            canvas.drawLine(
                0f, y * GRID_UNIT,
                GRID_SIZE * GRID_UNIT, y * GRID_UNIT,
                gridPaint
            )
        }

        // Draw each tile
        val fillPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = 0xFF3D1410.toInt() // Header color for borders
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        for (tile in tiles) {
            val placement = tile.placement
            val bounds = tile.bounds

            val tileWidth = placement.tileType.widthUnits * GRID_UNIT
            val tileHeight = placement.tileType.heightUnits * GRID_UNIT

            val left = bounds.left
            val top = bounds.top

            // Parse tile color
            val fillColor = try {
                android.graphics.Color.parseColor(placement.color.hex)
            } catch (_: IllegalArgumentException) {
                android.graphics.Color.GRAY
            }

            when (placement.tileType) {
                TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
                    val isTranslucent = placement.tileType == TileType.WINDOW_SQUARE
                    val rect = RectF(left, top, left + tileWidth, top + tileHeight)

                    if (isTranslucent) {
                        fillPaint.color = fillColor
                        fillPaint.alpha = 153 // ~0.6 * 255
                        canvas.drawRoundRect(rect, 4f, 4f, fillPaint)
                        fillPaint.alpha = 255

                        borderPaint.style = Paint.Style.STROKE
                        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)

                        // Inner window highlight
                        val innerPaint = Paint().apply {
                            color = 0xFFFFFFFF.toInt()
                            alpha = 51 // ~0.2 * 255
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val innerRect = RectF(left + 8f, top + 8f, left + tileWidth - 8f, top + tileHeight - 8f)
                        canvas.drawRoundRect(innerRect, 2f, 2f, innerPaint)
                    } else {
                        fillPaint.color = fillColor
                        fillPaint.alpha = 255
                        canvas.drawRoundRect(rect, 4f, 4f, fillPaint)
                        canvas.drawRoundRect(rect, 4f, 4f, borderPaint)
                    }
                }

                TileType.EQUILATERAL_TRIANGLE, TileType.ISOSCELES_TRIANGLE -> {
                    val path = equilateralTrianglePath(left, top, tileWidth, tileHeight, placement.rotation)
                    fillPaint.color = fillColor
                    fillPaint.alpha = 255
                    canvas.drawPath(path, fillPaint)
                    canvas.drawPath(path, borderPaint)
                }

                TileType.RIGHT_TRIANGLE -> {
                    val path = rightTrianglePath(left, top, tileWidth, tileHeight, placement.rotation)
                    fillPaint.color = fillColor
                    fillPaint.alpha = 255
                    canvas.drawPath(path, fillPaint)
                    canvas.drawPath(path, borderPaint)
                }
            }
        }

        return bitmap
    }

    /**
     * Convert a Bitmap to PNG byte array.
     */
    fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * Equilateral/Isosceles triangle path with rotation support.
     * Matches BuildCanvas logic exactly.
     */
    private fun equilateralTrianglePath(
        x: Float, y: Float,
        width: Float, height: Float,
        rotation: Rotation
    ): Path = Path().apply {
        when (rotation) {
            Rotation.R0 -> {
                moveTo(x + width / 2, y)
                lineTo(x + width, y + height)
                lineTo(x, y + height)
            }
            Rotation.R90 -> {
                moveTo(x, y)
                lineTo(x + width, y + height / 2)
                lineTo(x, y + height)
            }
            Rotation.R180 -> {
                moveTo(x, y)
                lineTo(x + width, y)
                lineTo(x + width / 2, y + height)
            }
            Rotation.R270 -> {
                moveTo(x + width, y)
                lineTo(x, y + height / 2)
                lineTo(x + width, y + height)
            }
        }
        close()
    }

    /**
     * Right triangle path with rotation support.
     * Matches BuildCanvas logic exactly.
     */
    private fun rightTrianglePath(
        x: Float, y: Float,
        width: Float, height: Float,
        rotation: Rotation
    ): Path = Path().apply {
        when (rotation) {
            Rotation.R0 -> {
                moveTo(x, y)
                lineTo(x + width, y)
                lineTo(x + width, y + height)
            }
            Rotation.R90 -> {
                moveTo(x + width, y)
                lineTo(x + width, y + height)
                lineTo(x, y + height)
            }
            Rotation.R180 -> {
                moveTo(x + width, y + height)
                lineTo(x, y + height)
                lineTo(x, y)
            }
            Rotation.R270 -> {
                moveTo(x, y + height)
                lineTo(x, y)
                lineTo(x + width, y)
            }
        }
        close()
    }
}
