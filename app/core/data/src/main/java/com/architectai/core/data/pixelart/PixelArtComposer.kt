package com.architectai.core.data.pixelart

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.TilePlacement
import java.util.UUID

/**
 * Orchestrates the image-to-pixel-art pipeline.
 *
 * Usage:
 *   val composer = PixelArtComposer(converter)
 *   val result = composer.composeFromImage(bitmap, "My House")
 *   // result is a Composition ready for the canvas
 */
class PixelArtComposer(
    private val converter: ImageToTileGridConverter = ImageToTileGridConverter()
) {

    /**
     * Convert a Bitmap to a Composition suitable for the Build canvas.
     *
     * @param bitmap Source image (any size)
     * @param name Name for the composition
     * @return A Composition with tiles placed on the 30×30 grid
     */
    fun composeFromImage(bitmap: Bitmap, name: String = "Pixel Art"): Composition {
        val result = converter.convertHighRes(bitmap, name)
        return result.toComposition()
    }

    /**
     * Convert a Uri to a Composition (loads bitmap from content resolver).
     */
    fun composeFromUri(context: Context, uri: Uri, name: String = "Pixel Art"): Composition? {
        val bitmap = loadBitmapFromUri(context, uri) ?: return null
        val composition = composeFromImage(bitmap, name)
        bitmap.recycle()
        return composition
    }
}

/**
 * Extension to convert a ConversionResult to a domain Composition.
 */
fun ConversionResult.toComposition(): Composition {
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

/**
 * Load a Bitmap from a content Uri.
 */
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
