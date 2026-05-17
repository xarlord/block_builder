package com.architectai.core.data.pixelart

import android.graphics.Bitmap
import android.graphics.Color
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import kotlin.math.sqrt

/**
 * Converts a reference image into a grid of tile placements.
 *
 * Pipeline:
 * 1. Downsample image to 10×10 pixels (effective tile grid)
 * 2. Quantize each pixel color to the nearest available TileColor
 * 3. Produce a scaled-up pixel art preview (100×100 for visibility)
 * 4. Analyze neighborhoods for tile type selection
 * 5. Produce list of TilePlacement objects
 */
class ImageToTileGridConverter {

    companion object {
        const val CANVAS_GRID_SIZE = 30
        const val TILE_SIZE = 3
        const val TILE_COUNT = CANVAS_GRID_SIZE / TILE_SIZE // 10
        /** Scale factor for the pixel art preview bitmap */
        const val PREVIEW_SCALE = 10
    }

    /**
     * Full pipeline with debug outputs — returns all intermediate results.
     */
    fun convertWithDebug(bitmap: Bitmap, objectName: String = "Pixel Art"): DebugConversionResult {
        // Step 1: Downsample to TILE_COUNT × TILE_COUNT
        val downsampled = Bitmap.createScaledBitmap(bitmap, TILE_COUNT, TILE_COUNT, true)

        // Step 2: Create quantized (pixel art) version — same size as downsampled
        val quantized = Bitmap.createBitmap(TILE_COUNT, TILE_COUNT, Bitmap.Config.ARGB_8888)
        val tiles = mutableListOf<TilePlacement>()
        val colorMap = mutableMapOf<Pair<Int, Int>, TileColor>()

        for (y in 0 until TILE_COUNT) {
            for (x in 0 until TILE_COUNT) {
                val pixel = downsampled.getPixel(x, y)
                val tileColor = quantizeColor(pixel)
                colorMap[Pair(x, y)] = tileColor

                // Paint quantized pixel
                quantized.setPixel(x, y, parseHexColorInt(tileColor.hex))

                val gridX = x * TILE_SIZE
                val gridY = y * TILE_SIZE
                val tileType = analyzeNeighborhood(downsampled, x, y, pixel)
                val rotation = computeRotation(downsampled, x, y, pixel)

                tiles.add(
                    TilePlacement(
                        tileType = tileType,
                        x = gridX,
                        y = gridY,
                        rotation = rotation,
                        color = tileColor
                    )
                )
            }
        }

        // Step 3: Create scaled-up pixel art preview for display
        val pixelArtPreview = Bitmap.createScaledBitmap(
            quantized,
            TILE_COUNT * PREVIEW_SCALE,
            TILE_COUNT * PREVIEW_SCALE,
            false // nearest neighbor — crisp pixels
        )

        // Step 4: Create a tile grid preview showing tile type icons
        val tileGridPreview = createTileGridPreview(tiles, colorMap)

        return DebugConversionResult(
            originalBitmap = bitmap,
            downsampledBitmap = downsampled,
            pixelArtBitmap = pixelArtPreview,
            tileGridBitmap = tileGridPreview,
            tiles = tiles,
            objectName = objectName,
            widthTiles = TILE_COUNT,
            heightTiles = TILE_COUNT,
            colorDistribution = tiles.groupBy { it.color }.mapValues { it.value.size }
        )
    }

    /**
     * Create a visual preview showing each tile with its type indicator.
     * Solid squares are filled, triangles show directional indicators.
     */
    private fun createTileGridPreview(
        tiles: List<TilePlacement>,
        colorMap: Map<Pair<Int, Int>, TileColor>
    ): Bitmap {
        val cellSize = PREVIEW_SCALE
        val size = TILE_COUNT * cellSize
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        for (tile in tiles) {
            val tx = tile.x / TILE_SIZE
            val ty = tile.y / TILE_SIZE
            val left = tx * cellSize.toFloat()
            val top = ty * cellSize.toFloat()
            val right = left + cellSize
            val bottom = top + cellSize

            val color = parseHexColorInt(tile.color.hex)
            val paint = android.graphics.Paint().apply {
                this.color = color
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }

            when (tile.tileType) {
                TileType.SOLID_SQUARE -> {
                    canvas.drawRect(left, top, right, bottom, paint)
                }
                TileType.RIGHT_TRIANGLE -> {
                    val path = android.graphics.Path()
                    when (tile.rotation) {
                        Rotation.R0 -> { path.moveTo(left, top); path.lineTo(right, bottom); path.lineTo(left, bottom) }
                        Rotation.R90 -> { path.moveTo(left, top); path.lineTo(right, top); path.lineTo(right, bottom) }
                        Rotation.R180 -> { path.moveTo(right, top); path.lineTo(left, bottom); path.lineTo(right, bottom) }
                        Rotation.R270 -> { path.moveTo(right, bottom); path.lineTo(left, top); path.lineTo(left, bottom) }
                    }
                    path.close()
                    canvas.drawPath(path, paint)
                }
                else -> canvas.drawRect(left, top, right, bottom, paint)
            }

            // Draw grid border
            val borderPaint = android.graphics.Paint().apply {
                this.color = Color.DKGRAY
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRect(left, top, right, bottom, borderPaint)
        }

        return bitmap
    }

    internal fun quantizeColor(pixel: Int): TileColor {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return TileColor.entries.minByOrNull { colorDistance(r, g, b, it) } ?: TileColor.RED
    }

    internal fun colorDistance(r: Int, g: Int, b: Int, tileColor: TileColor): Double {
        val tc = parseHexColor(tileColor.hex)
        val dr = r - tc[0]
        val dg = g - tc[1]
        val db = b - tc[2]
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    internal fun analyzeNeighborhood(img: Bitmap, x: Int, y: Int, myColor: Int): TileType {
        val same = countSameColorNeighbors(img, x, y, myColor)
        if (same >= 7) return TileType.SOLID_SQUARE
        if (isOnDiagonalEdge(img, x, y, myColor)) return TileType.RIGHT_TRIANGLE
        return TileType.SOLID_SQUARE
    }

    internal fun computeRotation(img: Bitmap, x: Int, y: Int, myColor: Int): Rotation {
        if (!isOnDiagonalEdge(img, x, y, myColor)) return Rotation.R0
        val w = img.width
        val h = img.height
        val topDiff = y > 0 && img.getPixel(x, y - 1) != myColor
        val bottomDiff = y < h - 1 && img.getPixel(x, y + 1) != myColor
        val leftDiff = x > 0 && img.getPixel(x - 1, y) != myColor
        val rightDiff = x < w - 1 && img.getPixel(x + 1, y) != myColor

        return when {
            topDiff && leftDiff -> Rotation.R0
            topDiff && rightDiff -> Rotation.R90
            bottomDiff && rightDiff -> Rotation.R180
            bottomDiff && leftDiff -> Rotation.R270
            else -> Rotation.R0
        }
    }

    private fun countSameColorNeighbors(img: Bitmap, x: Int, y: Int, myColor: Int): Int {
        var count = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until img.width && ny in 0 until img.height) {
                    if (img.getPixel(nx, ny) == myColor) count++
                }
            }
        }
        return count
    }

    private fun isOnDiagonalEdge(img: Bitmap, x: Int, y: Int, myColor: Int): Boolean {
        val checks = listOf(
            Pair(intArrayOf(-1, -1), intArrayOf(1, 1)),
            Pair(intArrayOf(1, -1), intArrayOf(-1, 1)),
        )
        for ((dir1, dir2) in checks) {
            val x1 = x + dir1[0]; val y1 = y + dir1[1]
            val x2 = x + dir2[0]; val y2 = y + dir2[1]
            val c1 = if (x1 in 0 until img.width && y1 in 0 until img.height) img.getPixel(x1, y1) else 0
            val c2 = if (x2 in 0 until img.width && y2 in 0 until img.height) img.getPixel(x2, y2) else 0
            if ((c1 == myColor) != (c2 == myColor)) return true
        }
        return false
    }

    private fun parseHexColor(hex: String): IntArray {
        val clean = hex.removePrefix("#")
        return if (clean.length >= 6) {
            intArrayOf(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16)
            )
        } else intArrayOf(0, 0, 0)
    }

    private fun parseHexColorInt(hex: String): Int {
        val c = parseHexColor(hex)
        return Color.rgb(c[0], c[1], c[2])
    }
}

/**
 * Full debug result with all intermediate bitmaps.
 */
data class DebugConversionResult(
    /** The original image as provided by the user/API */
    val originalBitmap: Bitmap,
    /** Downsampled to TILE_COUNT × TILE_COUNT (10×10) */
    val downsampledBitmap: Bitmap,
    /** Quantized pixel art scaled up for preview (100×100) */
    val pixelArtBitmap: Bitmap,
    /** Tile grid with type indicators (triangles shown as shapes) */
    val tileGridBitmap: Bitmap,
    /** Final tile placements */
    val tiles: List<TilePlacement>,
    val objectName: String,
    val widthTiles: Int,
    val heightTiles: Int,
    /** How many tiles of each color */
    val colorDistribution: Map<TileColor, Int>
)
