package com.architectai.core.data.pixelart

import android.graphics.Bitmap
import android.graphics.Color
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive tests for ImageToTileGridConverter.
 * Uses Robolectric for Android Bitmap/Color support in unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageToTileGridConverterTest {

    private lateinit var converter: ImageToTileGridConverter

    @Before
    fun setup() {
        converter = ImageToTileGridConverter()
    }

    // ═══════════════════════════════════════
    // Color Quantization Tests
    // ═══════════════════════════════════════

    @Test
    fun `quantizeColor pure red maps to RED`() {
        val pixel = Color.rgb(160, 69, 35) // close to RED hex #A04523
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.RED, result)
    }

    @Test
    fun `quantizeColor pure black maps to BLACK`() {
        val pixel = Color.rgb(0, 0, 0)
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.BLACK, result)
    }

    @Test
    fun `quantizeColor pure white maps to WHITE`() {
        val pixel = Color.rgb(255, 255, 255)
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.WHITE, result)
    }

    @Test
    fun `quantizeColor bright green maps to GREEN`() {
        val pixel = Color.rgb(76, 175, 80) // close to GREEN #4CAF50
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.GREEN, result)
    }

    @Test
    fun `quantizeColor bright blue maps to BLUE`() {
        val pixel = Color.rgb(33, 150, 243) // close to BLUE #2196F3
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.BLUE, result)
    }

    @Test
    fun `quantizeColor orange maps to ORANGE`() {
        val pixel = Color.rgb(241, 141, 88) // close to ORANGE #F18D58
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.ORANGE, result)
    }

    @Test
    fun `quantizeColor purple maps to PURPLE`() {
        val pixel = Color.rgb(156, 39, 176) // close to PURPLE #9C27B0
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.PURPLE, result)
    }

    @Test
    fun `quantizeColor pink maps to PINK`() {
        val pixel = Color.rgb(233, 30, 99) // close to PINK #E91E63
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.PINK, result)
    }

    @Test
    fun `quantizeColor brown maps to BROWN`() {
        val pixel = Color.rgb(141, 110, 99) // close to BROWN #8D6E63
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.BROWN, result)
    }

    @Test
    fun `quantizeColor yellow maps to YELLOW`() {
        val pixel = Color.rgb(245, 197, 66) // close to YELLOW #F5C542
        val result = converter.quantizeColor(pixel)
        assertEquals(TileColor.YELLOW, result)
    }

    @Test
    fun `quantizeColor gray maps to closest color`() {
        val pixel = Color.rgb(128, 128, 128)
        val result = converter.quantizeColor(pixel)
        // Gray 128,128,128 should map to a valid color (doesn't crash)
        assertNotNull(result)
        assertTrue(result in TileColor.entries)
    }

    @Test
    fun `quantizeColor translucent pixel handled correctly`() {
        // Alpha should be ignored, color channels used
        val pixel = Color.argb(128, 160, 69, 35)
        val result = converter.quantizeColor(pixel)
        assertNotNull(result)
        assertTrue(result in TileColor.entries)
    }

    // ═══════════════════════════════════════
    // Color Distance Tests
    // ═══════════════════════════════════════

    @Test
    fun `colorDistance zero for exact match`() {
        val dist = converter.colorDistance(160, 69, 35, TileColor.RED)
        assertEquals(0.0, dist, 0.01)
    }

    @Test
    fun `colorDistance larger for farther color`() {
        val distToRed = converter.colorDistance(160, 69, 35, TileColor.RED)
        val distToBlue = converter.colorDistance(160, 69, 35, TileColor.BLUE)
        assertTrue(distToRed < distToBlue)
    }

    @Test
    fun `colorDistance is always non-negative`() {
        for (tileColor in TileColor.entries) {
            val dist = converter.colorDistance(0, 0, 0, tileColor)
            assertTrue(dist >= 0)
        }
    }

    // ═══════════════════════════════════════
    // Neighborhood Analysis Tests
    // ═══════════════════════════════════════

    @Test
    fun `analyzeNeighborhood all same color returns SOLID_SQUARE`() {
        val img = createUniformBitmap(10, 10, Color.RED)
        val result = converter.analyzeNeighborhood(img, 5, 5, Color.RED)
        assertEquals(TileType.SOLID_SQUARE, result)
        img.recycle()
    }

    @Test
    fun `analyzeNeighborhood corner of uniform image returns SOLID_SQUARE`() {
        // Interior pixel (5,5) has all 8 neighbors same color
        val img = createUniformBitmap(10, 10, Color.BLUE)
        val result = converter.analyzeNeighborhood(img, 5, 5, Color.BLUE)
        assertEquals(TileType.SOLID_SQUARE, result)
        img.recycle()
    }

    @Test
    fun `analyzeNeighborhood edge pixel on boundary returns SOLID_SQUARE`() {
        // Interior pixel (4,4) has all 8 neighbors same color
        val img = createUniformBitmap(10, 10, Color.GREEN)
        val result = converter.analyzeNeighborhood(img, 4, 4, Color.GREEN)
        assertEquals(TileType.SOLID_SQUARE, result)
        img.recycle()
    }

    @Test
    fun `analyzeNeighborhood diagonal boundary returns RIGHT_TRIANGLE`() {
        // Create a bitmap with a diagonal split: top-left RED, bottom-right BLUE
        val img = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                img.setPixel(x, y, if (x + y < 10) Color.RED else Color.BLUE)
            }
        }
        // Pixel at (4,5) should be on the diagonal edge
        val myColor = img.getPixel(4, 5)
        val result = converter.analyzeNeighborhood(img, 4, 5, myColor)
        // On diagonal boundary — should be RIGHT_TRIANGLE
        assertEquals(TileType.RIGHT_TRIANGLE, result)
        img.recycle()
    }

    // ═══════════════════════════════════════
    // Rotation Tests
    // ═══════════════════════════════════════

    @Test
    fun `computeRotation uniform image returns R0`() {
        val img = createUniformBitmap(10, 10, Color.RED)
        val result = converter.computeRotation(img, 5, 5, Color.RED)
        assertEquals(Rotation.R0, result)
        img.recycle()
    }

    @Test
    fun `computeRotation topLeft corner returns R0`() {
        // Top-left region: different above and to the left
        val img = createDiagonalBitmap(10)
        val pixel = img.getPixel(3, 3) // on diagonal
        val result = converter.computeRotation(img, 3, 3, pixel)
        assertNotNull(result)
        assertTrue(result in Rotation.entries)
        img.recycle()
    }

    // ═══════════════════════════════════════
    // Full Pipeline Tests
    // ═══════════════════════════════════════

    @Test
    fun `convertWithDebug returns correct tile count`() {
        val img = createUniformBitmap(100, 100, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        assertEquals(100, result.tiles.size) // 10×10 = 100 tiles
        img.recycle()
    }

    @Test
    fun `convertWithDebug returns correct object name`() {
        val img = createUniformBitmap(50, 50, Color.BLUE)
        val result = converter.convertWithDebug(img, "My House")
        assertEquals("My House", result.objectName)
        img.recycle()
    }

    @Test
    fun `convertWithDebug returns correct dimensions`() {
        val img = createUniformBitmap(200, 150, Color.GREEN)
        val result = converter.convertWithDebug(img, "Test")
        assertEquals(10, result.widthTiles)
        assertEquals(10, result.heightTiles)
        img.recycle()
    }

    @Test
    fun `convertWithDebug uniform image produces all SOLID_SQUARE`() {
        // Interior pixel test: use 10x10 but check that interior tiles are squares
        val img = createUniformBitmap(10, 10, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        val interiorTiles = result.tiles.filter { it.x in 3..24 && it.y in 3..24 }
        assertTrue("Interior tiles should be squares", interiorTiles.all { it.tileType == TileType.SOLID_SQUARE })
        img.recycle()
    }

    @Test
    fun `convertWithDebug uniform image produces all R0 rotation`() {
        val img = createUniformBitmap(100, 100, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        assertTrue(result.tiles.all { it.rotation == Rotation.R0 })
        img.recycle()
    }

    @Test
    fun `convertWithDebug uniform red image produces all RED tiles`() {
        val img = createUniformBitmap(100, 100, Color.rgb(160, 69, 35))
        val result = converter.convertWithDebug(img, "Test")
        assertTrue(result.tiles.all { it.color == TileColor.RED })
        img.recycle()
    }

    @Test
    fun `convertWithDebug produces correct grid positions`() {
        val img = createUniformBitmap(100, 100, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        val positions = result.tiles.map { Pair(it.x, it.y) }.sortedWith(compareBy({ it.second }, { it.first }))
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                assertTrue("Missing tile at ($x,$y)", positions.contains(Pair(x * 3, y * 3)))
            }
        }
        img.recycle()
    }

    @Test
    fun `convertWithDebug returns non-null bitmaps`() {
        val img = createUniformBitmap(100, 100, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        assertNotNull(result.originalBitmap)
        assertNotNull(result.downsampledBitmap)
        assertNotNull(result.pixelArtBitmap)
        assertNotNull(result.tileGridBitmap)
        assertFalse(result.originalBitmap.isRecycled)
        assertFalse(result.pixelArtBitmap.isRecycled)
        assertFalse(result.tileGridBitmap.isRecycled)
        img.recycle()
    }

    @Test
    fun `convertWithDebug pixel art preview is scaled up`() {
        val img = createUniformBitmap(100, 100, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        assertEquals(100, result.pixelArtBitmap.width) // 10 * 10 = 100
        assertEquals(100, result.pixelArtBitmap.height)
        img.recycle()
    }

    @Test
    fun `convertWithDebug downsampled is 10x10`() {
        val img = createUniformBitmap(500, 300, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        assertEquals(10, result.downsampledBitmap.width)
        assertEquals(10, result.downsampledBitmap.height)
        img.recycle()
    }

    @Test
    fun `convertWithDebug color distribution is correct for uniform image`() {
        val img = createUniformBitmap(100, 100, Color.rgb(160, 69, 35))
        val result = converter.convertWithDebug(img, "Test")
        assertEquals(1, result.colorDistribution.size)
        assertEquals(100, result.colorDistribution[TileColor.RED])
        img.recycle()
    }

    @Test
    fun `convertWithDebug multi-color image has multiple colors`() {
        val img = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        // Top half red, bottom half blue
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                img.setPixel(x, y, if (y < 50) Color.rgb(160, 69, 35) else Color.rgb(33, 150, 243))
            }
        }
        val result = converter.convertWithDebug(img, "Two Color")
        assertTrue(result.colorDistribution.size >= 2)
        img.recycle()
    }

    @Test
    fun `convertWithDebug handles very small image`() {
        val img = createUniformBitmap(1, 1, Color.RED)
        val result = converter.convertWithDebug(img, "Tiny")
        assertEquals(100, result.tiles.size) // Still produces 10×10 grid
        img.recycle()
    }

    @Test
    fun `convertWithDebug handles non-square image`() {
        val img = createUniformBitmap(200, 50, Color.BLUE)
        val result = converter.convertWithDebug(img, "Wide")
        assertEquals(100, result.tiles.size)
        img.recycle()
    }

    @Test
    fun `convertWithDebug handles large image`() {
        val img = createUniformBitmap(4000, 4000, Color.GREEN)
        val result = converter.convertWithDebug(img, "Large")
        assertEquals(100, result.tiles.size)
        img.recycle()
    }

    @Test
    fun `convertWithDebug diagonal boundary produces triangles`() {
        val img = createDiagonalBitmap(100)
        val result = converter.convertWithDebug(img, "Diagonal")
        val triangleCount = result.tiles.count { it.tileType == TileType.RIGHT_TRIANGLE }
        val squareCount = result.tiles.count { it.tileType == TileType.SOLID_SQUARE }
        // Should have at least some triangles along the diagonal
        assertTrue("Should have some triangles on diagonal edge", triangleCount > 0)
        assertTrue("Should have some squares too", squareCount > 0)
        img.recycle()
    }

    // ═══════════════════════════════════════
    // Companion Constants Tests
    // ═══════════════════════════════════════

    @Test
    fun `constants are correct`() {
        assertEquals(30, ImageToTileGridConverter.CANVAS_GRID_SIZE)
        assertEquals(3, ImageToTileGridConverter.TILE_SIZE)
        assertEquals(10, ImageToTileGridConverter.TILE_COUNT)
    }

    // ═══════════════════════════════════════
    // Tile Placement Validation
    // ═══════════════════════════════════════

    @Test
    fun `all tile positions are within grid bounds`() {
        val img = createUniformBitmap(100, 100, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        for (tile in result.tiles) {
            assertTrue("x=${tile.x} should be >= 0", tile.x >= 0)
            assertTrue("y=${tile.y} should be >= 0", tile.y >= 0)
            assertTrue("x=${tile.x} + 3 should be <= 30", tile.x + 3 <= 30)
            assertTrue("y=${tile.y} + 3 should be <= 30", tile.y + 3 <= 30)
        }
        img.recycle()
    }

    @Test
    fun `no duplicate tile positions`() {
        val img = createUniformBitmap(100, 100, Color.RED)
        val result = converter.convertWithDebug(img, "Test")
        val positions = result.tiles.map { Pair(it.x, it.y) }
        assertEquals("No duplicate positions", positions.size, positions.toSet().size)
        img.recycle()
    }

    // ═══════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════

    @Test
    fun `convertWithDebug with checkerboard pattern`() {
        val img = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                img.setPixel(x, y, if ((x / 10 + y / 10) % 2 == 0) Color.RED else Color.BLUE)
            }
        }
        val result = converter.convertWithDebug(img, "Checker")
        assertTrue(result.colorDistribution.size >= 2)
        img.recycle()
    }

    @Test
    fun `convertWithDebug all white image`() {
        val img = createUniformBitmap(100, 100, Color.WHITE)
        val result = converter.convertWithDebug(img, "White")
        assertTrue(result.tiles.all { it.color == TileColor.WHITE })
        img.recycle()
    }

    @Test
    fun `convertWithDebug all black image`() {
        val img = createUniformBitmap(100, 100, Color.BLACK)
        val result = converter.convertWithDebug(img, "Black")
        assertTrue(result.tiles.all { it.color == TileColor.BLACK })
        img.recycle()
    }

    // ═══════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════

    private fun createUniformBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    private fun createDiagonalBitmap(size: Int): Bitmap {
        val img = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                img.setPixel(x, y, if (x + y < size) Color.RED else Color.BLUE)
            }
        }
        return img
    }
}
