package com.architectai.core.domain.model

/**
 * A pre-built, validated composition template stored as JSON.
 * Templates are human-designed and guaranteed valid (no overlaps, within bounds, recognizable).
 */
data class CompositionTemplate(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val tags: List<String>,
    val tiles: List<TilePlacement>,
    val thumbnailDescription: String = ""
)

/**
 * Axis used for mirror modifications.
 */
enum class Axis {
    HORIZONTAL,
    VERTICAL
}

/**
 * Sealed class representing modifications that can be applied to a template.
 * The LLM returns a list of these instead of raw coordinates.
 */
sealed class TemplateModification {
    /**
     * Swap all tiles of one color to another color.
     */
    data class ColorSwap(val fromColor: TileColor, val toColor: TileColor) : TemplateModification()

    /**
     * Scale the composition by a factor. Valid factors: 0.5, 1.0, 1.5, 2.0
     * Coordinates are multiplied by the factor and rounded to integers.
     */
    data class Scale(val factor: Float) : TemplateModification()

    /**
     * Mirror the composition along the specified axis.
     * HORIZONTAL: flips left-right (x coordinates mirrored around center)
     * VERTICAL: flips top-bottom (y coordinates mirrored around center)
     */
    data class Mirror(val axis: Axis) : TemplateModification()

    /**
     * Translate (shift) the composition by (dx, dy) grid units.
     */
    data class Translate(val dx: Int, val dy: Int) : TemplateModification()

    /**
     * Combine another template at the given offset.
     * The other template's tiles are placed offset from the current template's tiles.
     */
    data class Combine(val templateId: String, val offset: Pair<Int, Int>) : TemplateModification()
}
