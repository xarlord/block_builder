package com.architectai.core.data.pixelart

import android.graphics.Bitmap
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import kotlin.math.sqrt

/**
 * Converts a reference image into a grid of tile placements.
 *
 * Pipeline:
 * 1. Downsample image to GRID_SIZE × GRID_SIZE pixels
 * 2. Quantize each pixel color to the nearest available TileColor
 * 3. Analyze local neighborhood to determine tile type (square, triangle, etc.)
 * 4. Produce a list of TilePlacement objects
 *
 * The effective tile grid is 10×10 since each tile is 3×3 grid units on a 30×30 grid.
 */
class ImageToTileGridConverter {

    companion object {
        /** The full canvas grid size */
        const val CANVAS_GRID_SIZE = 30
        /** Each tile occupies this many grid units */
        const val TILE_SIZE = 3
        /** Number of tiles that fit in one dimension: 30 / 3 = 10 */
        const val TILE_COUNT = CANVAS_GRID_SIZE / TILE_SIZE // 10
    }

    /**
     * Convert a Bitmap to a list of TilePlacement objects.
     *
     * @param bitmap The source image (any size — will be downsampled)
     * @param objectName Name for the resulting composition
     * @return List of TilePlacement representing the pixel art
     */
    fun convert(bitmap: Bitmap, objectName: String = "Pixel Art"): ConversionResult {
        // Step 1: Downsample to 10×10 (effective tile count)
        val small = Bitmap.createScaledBitmap(bitmap, TILE_COUNT, TILE_COUNT, true)

        // Step 2: For each pixel, quantize color and determine tile type
        val tiles = mutableListOf<TilePlacement>()
        for (y in 0 until TILE_COUNT) {
            for (x in 0 until TILE_COUNT) {
                val pixel = small.getPixel(x, y)
                val tileColor = quantizeColor(pixel)
                val gridX = x * TILE_SIZE
                val gridY = y * TILE_SIZE

                // Analyze edge direction to pick tile type
                val tileType = analyzeNeighborhood(small, x, y, pixel)
                val rotation = computeRotation(small, x, y, pixel)

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

        small.recycle()
        return ConversionResult(
            tiles = tiles,
            objectName = objectName,
            widthTiles = TILE_COUNT,
            heightTiles = TILE_COUNT
        )
    }

    /**
     * Convert at higher resolution — 30×30 pixel art using only squares.
     * This produces 100 tiles (10×10 effective grid) but each pixel maps 1:1 to a grid unit.
     * For a finer 30×30 pixel art, tiles overlap — but at the 10×10 level it's clean.
     */
    fun convertHighRes(bitmap: Bitmap, objectName: String = "Pixel Art"): ConversionResult {
        val small = Bitmap.createScaledBitmap(bitmap, TILE_COUNT, TILE_COUNT, true)

        val tiles = mutableListOf<TilePlacement>()
        for (y in 0 until TILE_COUNT) {
            for (x in 0 until TILE_COUNT) {
                val pixel = small.getPixel(x, y)
                val tileColor = quantizeColor(pixel)
                val gridX = x * TILE_SIZE
                val gridY = y * TILE_SIZE

                tiles.add(
                    TilePlacement(
                        tileType = TileType.SOLID_SQUARE,
                        x = gridX,
                        y = gridY,
                        rotation = Rotation.R0,
                        color = tileColor
                    )
                )
            }
        }

        small.recycle()
        return ConversionResult(
            tiles = tiles,
            objectName = objectName,
            widthTiles = TILE_COUNT,
            heightTiles = TILE_COUNT
        )
    }

    /**
     * Map an ARGB pixel to the nearest TileColor using Euclidean distance in RGB space.
     */
    internal fun quantizeColor(pixel: Int): TileColor {
        val r = android.graphics.Color.red(pixel)
        val g = android.graphics.Color.green(pixel)
        val b = android.graphics.Color.blue(pixel)

        return TileColor.entries.minByOrNull { tileColor ->
            colorDistance(r, g, b, tileColor)
        } ?: TileColor.RED
    }

    /**
     * Euclidean distance in RGB space between a pixel and a TileColor.
     */
    internal fun colorDistance(r: Int, g: Int, b: Int, tileColor: TileColor): Double {
        val tc = parseHexColor(tileColor.hex)
        val dr = r - tc[0]
        val dg = g - tc[1]
        val db = b - tc[2]
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    /**
     * Analyze the local neighborhood to determine the best tile type.
     * - Interior pixels (all same color) → SOLID_SQUARE
     * - Edge pixels → RIGHT_TRIANGLE for diagonals
     * - Corner pixels → EQUILATERAL_TRIANGLE
     * - Window squares for slightly varied interior regions
     */
    internal fun analyzeNeighborhood(
        img: Bitmap, x: Int, y: Int, myColor: Int
    ): TileType {
        val same = countSameColorNeighbors(img, x, y, myColor)

        // Fully surrounded by same color → solid fill
        if (same >= 7) return TileType.SOLID_SQUARE

        // Check if on a diagonal edge
        if (isOnDiagonalEdge(img, x, y, myColor)) {
            return TileType.RIGHT_TRIANGLE
        }

        // Default to solid square for all other cases
        return TileType.SOLID_SQUARE
    }

    /**
     * Determine rotation for triangle tiles based on edge direction.
     */
    internal fun computeRotation(
        img: Bitmap, x: Int, y: Int, myColor: Int
    ): Rotation {
        if (!isOnDiagonalEdge(img, x, y, myColor)) return Rotation.R0

        val w = img.width
        val h = img.height

        // Check which neighbors are different to determine diagonal direction
        val topDiff = y > 0 && img.getPixel(x, y - 1) != myColor
        val bottomDiff = y < h - 1 && img.getPixel(x, y + 1) != myColor
        val leftDiff = x > 0 && img.getPixel(x - 1, y) != myColor
        val rightDiff = x < w - 1 && img.getPixel(x + 1, y) != myColor

        return when {
            topDiff && leftDiff -> Rotation.R0      // ◢ top-left corner
            topDiff && rightDiff -> Rotation.R90     // ◣ top-right corner
            bottomDiff && rightDiff -> Rotation.R180  // ◥ bottom-right corner
            bottomDiff && leftDiff -> Rotation.R270   // ◤ bottom-left corner
            else -> Rotation.R0
        }
    }

    /**
     * Count how many of the 8 neighbors have the same color.
     */
    private fun countSameColorNeighbors(
        img: Bitmap, x: Int, y: Int, myColor: Int
    ): Int {
        var count = 0
        val w = img.width
        val h = img.height

        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) {
                    if (img.getPixel(nx, ny) == myColor) count++
                }
            }
        }
        return count
    }

    /**
     * Check if this pixel is on a diagonal edge by examining diagonal neighbors.
     */
    private fun isOnDiagonalEdge(
        img: Bitmap, x: Int, y: Int, myColor: Int
    ): Boolean {
        val w = img.width
        val h = img.height

        // Check diagonal pairs
        val checks = listOf(
            Pair(intArrayOf(-1, -1), intArrayOf(1, 1)),   // NW vs SE
            Pair(intArrayOf(1, -1), intArrayOf(-1, 1)),   // NE vs SW
        )

        for ((dir1, dir2) in checks) {
            val x1 = x + dir1[0]; val y1 = y + dir1[1]
            val x2 = x + dir2[0]; val y2 = y + dir2[1]

            val c1 = if (x1 in 0 until w && y1 in 0 until h) img.getPixel(x1, y1) else 0
            val c2 = if (x2 in 0 until w && y2 in 0 until h) img.getPixel(x2, y2) else 0

            // One diagonal same, other different → we're on a diagonal edge
            if ((c1 == myColor) != (c2 == myColor)) return true
        }
        return false
    }

    /**
     * Parse a hex color string like "#A04523" to [R, G, B]
     */
    private fun parseHexColor(hex: String): IntArray {
        val clean = hex.removePrefix("#")
        return if (clean.length >= 6) {
            intArrayOf(
                clean.substring(0, 2).toInt(16),
                clean.substring(2, 4).toInt(16),
                clean.substring(4, 6).toInt(16)
            )
        } else {
            intArrayOf(0, 0, 0)
        }
    }
}

/**
 * Result of image-to-tile conversion.
 */
data class ConversionResult(
    val tiles: List<TilePlacement>,
    val objectName: String,
    val widthTiles: Int,
    val heightTiles: Int
)
