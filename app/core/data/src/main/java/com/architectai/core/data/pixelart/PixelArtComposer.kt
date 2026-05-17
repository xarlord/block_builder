package com.architectai.core.data.pixelart

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.architectai.core.domain.model.Composition
import java.util.UUID

/**
 * Orchestrates the image-to-pixel-art pipeline.
 * Returns all intermediate debug data for visual inspection.
 */
class PixelArtComposer(
    private val converter: ImageToTileGridConverter = ImageToTileGridConverter()
) {

    /**
     * Full pipeline with debug outputs.
     * Returns [PixelArtResult] with original, pixel art preview, tile grid, and composition.
     */
    fun processImage(bitmap: Bitmap, name: String = "Pixel Art"): PixelArtResult {
        val debug = converter.convertWithDebug(bitmap, name)
        val composition = debug.toComposition()

        return PixelArtResult(
            originalBitmap = debug.originalBitmap,
            pixelArtBitmap = debug.pixelArtBitmap,
            tileGridBitmap = debug.tileGridBitmap,
            composition = composition,
            colorDistribution = debug.colorDistribution,
            tileCount = debug.tiles.size,
            objectName = debug.objectName
        )
    }

    /**
     * Process from a content Uri.
     */
    fun processFromUri(context: Context, uri: Uri, name: String = "Pixel Art"): PixelArtResult? {
        val bitmap = loadBitmapFromUri(context, uri) ?: return null
        val result = processImage(bitmap, name)
        // Don't recycle original — we need it for debug display
        return result
    }
}

/**
 * Complete result of pixel art processing — includes all debug visuals.
 */
data class PixelArtResult(
    /** Step 1: Original image */
    val originalBitmap: Bitmap,
    /** Step 2: Pixel art after downsampling + color quantization */
    val pixelArtBitmap: Bitmap,
    /** Step 3: Tile grid with type indicators */
    val tileGridBitmap: Bitmap,
    /** Final composition ready for canvas */
    val composition: Composition,
    /** Color distribution: which TileColor → count */
    val colorDistribution: Map<com.architectai.core.domain.model.TileColor, Int>,
    /** Total tile count */
    val tileCount: Int,
    /** Name of the object */
    val objectName: String
)

/**
 * Extension to convert a DebugConversionResult to a domain Composition.
 */
fun DebugConversionResult.toComposition(): Composition {
    val now = System.currentTimeMillis()
    return Composition(
        id = UUID.randomUUID().toString(),
        name = objectName,
        tiles = tiles,
        createdAt = now,
        updatedAt = now,
        source = Composition.Source.AI_GENERATED
    )
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        bitmap
    } catch (e: Exception) {
        null
    }
}
