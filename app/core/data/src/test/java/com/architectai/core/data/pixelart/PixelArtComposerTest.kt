package com.architectai.core.data.pixelart

import android.graphics.Bitmap
import android.graphics.Color
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.TileColor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for PixelArtComposer — the orchestrator that converts bitmaps to Compositions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PixelArtComposerTest {

    private lateinit var composer: PixelArtComposer

    @Before
    fun setup() {
        composer = PixelArtComposer()
    }

    @Test
    fun `processImage returns non-null result`() {
        val img = createTestBitmap(100, 100, Color.RED)
        val result = composer.processImage(img, "Test")
        assertNotNull(result)
        img.recycle()
    }

    @Test
    fun `processImage preserves object name`() {
        val img = createTestBitmap(50, 50, Color.BLUE)
        val result = composer.processImage(img, "My House")
        assertEquals("My House", result.objectName)
        img.recycle()
    }

    @Test
    fun `processImage returns valid composition`() {
        val img = createTestBitmap(100, 100, Color.RED)
        val result = composer.processImage(img, "Test")
        val comp = result.composition
        assertNotNull(comp)
        assertNotNull(comp.id)
        assertTrue(comp.id.isNotBlank())
        assertEquals("Test", comp.name)
        assertTrue(comp.tiles.isNotEmpty())
        assertEquals(Composition.Source.AI_GENERATED, comp.source)
        assertTrue(comp.createdAt > 0)
        assertTrue(comp.updatedAt > 0)
        img.recycle()
    }

    @Test
    fun `processImage returns all debug bitmaps`() {
        val img = createTestBitmap(100, 100, Color.GREEN)
        val result = composer.processImage(img, "Test")
        assertNotNull(result.originalBitmap)
        assertNotNull(result.pixelArtBitmap)
        assertNotNull(result.tileGridBitmap)
        assertFalse(result.originalBitmap.isRecycled)
        assertFalse(result.pixelArtBitmap.isRecycled)
        assertFalse(result.tileGridBitmap.isRecycled)
        img.recycle()
    }

    @Test
    fun `processImage returns correct tile count`() {
        val img = createTestBitmap(200, 200, Color.RED)
        val result = composer.processImage(img, "Test")
        assertEquals(100, result.tileCount) // 10×10
        assertEquals(100, result.composition.tiles.size)
        img.recycle()
    }

    @Test
    fun `processImage color distribution matches tiles`() {
        val img = createTestBitmap(100, 100, Color.rgb(160, 69, 35))
        val result = composer.processImage(img, "Test")
        val totalFromDist = result.colorDistribution.values.sum()
        assertEquals(result.tileCount, totalFromDist)
        img.recycle()
    }

    @Test
    fun `processImage with two-tone image has multiple colors`() {
        // Use 10x10 to match TILE_COUNT — avoids scaling artifacts
        val img = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                img.setPixel(x, y, if (y < 5) Color.RED else Color.BLUE)
            }
        }
        val result = composer.processImage(img, "Two Tone")
        assertTrue(result.colorDistribution.size >= 2)
        img.recycle()
    }

    @Test
    fun `processImage handles square image`() {
        val img = createTestBitmap(300, 300, Color.BLUE)
        val result = composer.processImage(img, "Square")
        assertEquals(100, result.tileCount)
        img.recycle()
    }

    @Test
    fun `processImage handles wide image`() {
        val img = createTestBitmap(500, 100, Color.GREEN)
        val result = composer.processImage(img, "Wide")
        assertEquals(100, result.tileCount)
        img.recycle()
    }

    @Test
    fun `processImage handles tall image`() {
        val img = createTestBitmap(100, 500, Color.YELLOW)
        val result = composer.processImage(img, "Tall")
        assertEquals(100, result.tileCount)
        img.recycle()
    }

    @Test
    fun `processImage handles tiny image`() {
        val img = createTestBitmap(1, 1, Color.RED)
        val result = composer.processImage(img, "Tiny")
        assertEquals(100, result.tileCount)
        img.recycle()
    }

    @Test
    fun `processImage composition has unique ID`() {
        val img = createTestBitmap(50, 50, Color.RED)
        val r1 = composer.processImage(img, "A")
        val r2 = composer.processImage(img, "B")
        assertNotEquals(r1.composition.id, r2.composition.id)
        img.recycle()
    }

    @Test
    fun `processImage timestamps are recent`() {
        val before = System.currentTimeMillis()
        val img = createTestBitmap(50, 50, Color.RED)
        val result = composer.processImage(img, "Test")
        val after = System.currentTimeMillis()
        assertTrue(result.composition.createdAt in before..after)
        assertTrue(result.composition.updatedAt in before..after)
        img.recycle()
    }

    @Test
    fun `processImage all tiles have valid types`() {
        val img = createTestBitmap(100, 100, Color.RED)
        val result = composer.processImage(img, "Test")
        for (tile in result.composition.tiles) {
            assertNotNull(tile.tileType)
            assertNotNull(tile.color)
            assertNotNull(tile.rotation)
        }
        img.recycle()
    }

    @Test
    fun `processImage all tile positions are multiples of TILE_SIZE`() {
        val img = createTestBitmap(100, 100, Color.RED)
        val result = composer.processImage(img, "Test")
        for (tile in result.composition.tiles) {
            assertEquals(0, tile.x % ImageToTileGridConverter.TILE_SIZE)
            assertEquals(0, tile.y % ImageToTileGridConverter.TILE_SIZE)
        }
        img.recycle()
    }

    // ═══════════════════════════════════════
    // DebugConversionResult extension tests
    // ═══════════════════════════════════════

    @Test
    fun `toComposition creates valid Composition`() {
        val converter = ImageToTileGridConverter()
        val img = createTestBitmap(50, 50, Color.BLUE)
        val debug = converter.convertWithDebug(img, "Ext Test")
        val comp = debug.toComposition()

        assertNotNull(comp.id)
        assertEquals("Ext Test", comp.name)
        assertEquals(debug.tiles, comp.tiles)
        assertEquals(Composition.Source.AI_GENERATED, comp.source)
        img.recycle()
    }

    // ═══════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════

    private fun createTestBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
}
