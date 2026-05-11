package com.architectai.core.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Result of applying modifications to a template.
 */
sealed class TemplateResult {
    data class Success(val composition: Composition, val appliedModifications: List<String>) : TemplateResult()
    data class Rejected(val reason: String, val partialComposition: Composition?) : TemplateResult()
}

/**
 * JSON-serializable DTO for loading templates from assets.
 */
@Serializable
data class TemplateJson(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val tags: List<String>,
    val tiles: List<TileJson>,
    val thumbnailDescription: String = ""
)

@Serializable
data class TileJson(
    val tileType: String,
    val x: Int,
    val y: Int,
    val rotation: String,
    val color: String
)

/**
 * JSON-serializable DTO for LLM template selection response.
 */
@Serializable
data class TemplateSelectionResponse(
    val templateId: String,
    val modifications: List<ModificationJson> = emptyList()
)

@Serializable
data class ModificationJson(
    val type: String,
    val from: String? = null,
    val to: String? = null,
    val factor: Float? = null,
    val axis: String? = null,
    val dx: Int? = null,
    val dy: Int? = null,
    val templateId: String? = null,
    val offsetX: Int? = null,
    val offsetY: Int? = null
)

/**
 * Template engine that loads templates from JSON, applies modifications,
 * and validates results with constraint checking.
 *
 * The LLM never generates raw coordinates — it selects a template and suggests
 * modifications. The engine validates all modifications before rendering.
 */
class TemplateEngine {

    companion object {
        const val GRID_SIZE = 30
        const val MAX_TILES = 200
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val templates = mutableMapOf<String, CompositionTemplate>()

    /**
     * Load a template from a JSON string.
     * Validates the template has no overlaps and all tiles are within bounds.
     */
    fun loadTemplate(jsonString: String): Result<CompositionTemplate> {
        return try {
            val templateJson = json.decodeFromString<TemplateJson>(jsonString)
            val template = templateJson.toDomain()
            val validation = validateTemplate(template)
            if (validation.isNotEmpty()) {
                Result.failure(IllegalArgumentException("Template validation failed: ${validation.joinToString("; ")}"))
            } else {
                templates[template.id] = template
                Result.success(template)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load multiple templates from JSON strings.
     */
    fun loadTemplates(jsonStrings: List<String>): List<Result<CompositionTemplate>> {
        return jsonStrings.map { loadTemplate(it) }
    }

    /**
     * Get a loaded template by ID.
     */
    fun getTemplate(id: String): CompositionTemplate? = templates[id]

    /**
     * Get all loaded templates.
     */
    fun getAllTemplates(): List<CompositionTemplate> = templates.values.toList()

    /**
     * Get all loaded templates grouped by category.
     */
    fun getTemplatesByCategory(): Map<String, List<CompositionTemplate>> {
        return templates.values.groupBy { it.category }
    }

    /**
     * Search templates by tags or name.
     */
    fun searchTemplates(query: String): List<CompositionTemplate> {
        val lowerQuery = query.lowercase().trim()
        return templates.values.filter { template ->
            template.name.lowercase().contains(lowerQuery) ||
                template.tags.any { it.lowercase().contains(lowerQuery) } ||
                template.description.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Get a catalog of template names, descriptions, and tags for LLM prompting.
     */
    fun getCatalogText(): String {
        return templates.values.joinToString("\n") { template ->
            "- ${template.id}: ${template.description}. Tags: ${template.tags.joinToString(", ")}"
        }
    }

    /**
     * Apply a list of modifications to a template and return the result.
     * If any modification causes constraint violations, falls back to the
     * unmodified template with a rejection reason.
     */
    fun applyModifications(
        templateId: String,
        modifications: List<TemplateModification>
    ): TemplateResult {
        val template = templates[templateId]
            ?: return TemplateResult.Rejected("Template '$templateId' not found", null)

        var currentTiles = template.tiles.toList()
        val appliedDescriptions = mutableListOf<String>()

        for (modification in modifications) {
            val result = applySingleModification(currentTiles, modification)
            when (result) {
                is ModificationApplyResult.Success -> {
                    currentTiles = result.tiles
                    appliedDescriptions.add(result.description)
                }
                is ModificationApplyResult.Failed -> {
                    // Skip this modification but continue with others
                    appliedDescriptions.add("Skipped: ${result.reason}")
                }
            }
        }

        // Validate final result
        val validationErrors = validateTiles(currentTiles)
        if (validationErrors.isNotEmpty()) {
            // Fall back to unmodified template
            val unmodifiedComposition = template.toComposition()
            return TemplateResult.Rejected(
                "Modifications caused constraint violations: ${validationErrors.joinToString("; ")}",
                unmodifiedComposition
            )
        }

        val composition = Composition(
            id = UUID.randomUUID().toString(),
            name = template.name,
            tiles = currentTiles,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            source = Composition.Source.AI_GENERATED
        )

        return TemplateResult.Success(composition, appliedDescriptions)
    }

    /**
     * Parse LLM response JSON into a TemplateSelectionResponse.
     */
    fun parseSelectionResponse(responseJson: String): Result<TemplateSelectionResponse> {
        return try {
            val response = json.decodeFromString<TemplateSelectionResponse>(responseJson)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert a TemplateSelectionResponse into domain TemplateModifications
     * and apply them to the selected template.
     */
    fun applySelectionResponse(response: TemplateSelectionResponse): TemplateResult {
        val modifications = response.modifications.mapNotNull { modJson ->
            parseModification(modJson)
        }
        return applyModifications(response.templateId, modifications)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private sealed class ModificationApplyResult {
        data class Success(val tiles: List<TilePlacement>, val description: String) : ModificationApplyResult()
        data class Failed(val reason: String) : ModificationApplyResult()
    }

    private fun applySingleModification(
        currentTiles: List<TilePlacement>,
        modification: TemplateModification
    ): ModificationApplyResult {
        return when (modification) {
            is TemplateModification.ColorSwap -> applyColorSwap(currentTiles, modification)
            is TemplateModification.Scale -> applyScale(currentTiles, modification)
            is TemplateModification.Mirror -> applyMirror(currentTiles, modification)
            is TemplateModification.Translate -> applyTranslate(currentTiles, modification)
            is TemplateModification.Combine -> ModificationApplyResult.Failed(
                "Combine modification requires template lookup; use applyModifications with engine context"
            )
        }
    }

    private fun applyColorSwap(
        tiles: List<TilePlacement>,
        mod: TemplateModification.ColorSwap
    ): ModificationApplyResult {
        val swapped = tiles.map { tile ->
            if (tile.color == mod.fromColor) {
                tile.copy(color = mod.toColor)
            } else {
                tile
            }
        }
        val count = tiles.count { it.color == mod.fromColor }
        return ModificationApplyResult.Success(
            swapped,
            "Swapped ${mod.fromColor.displayName} to ${mod.toColor.displayName} ($count tiles)"
        )
    }

    private fun applyScale(
        tiles: List<TilePlacement>,
        mod: TemplateModification.Scale
    ): ModificationApplyResult {
        if (mod.factor <= 0f) {
            return ModificationApplyResult.Failed("Scale factor must be positive, got ${mod.factor}")
        }
        // Valid scale factors: 0.5, 1.0, 1.5, 2.0
        val validFactors = setOf(0.5f, 1.0f, 1.5f, 2.0f)
        val roundedFactor = validFactors.minByOrNull { kotlin.math.abs(it - mod.factor) } ?: 1.0f

        if (roundedFactor == 1.0f) {
            return ModificationApplyResult.Success(tiles, "No scaling applied (factor ~1.0)")
        }

        val scaled = tiles.map { tile ->
            tile.copy(
                x = (tile.x * roundedFactor).toInt(),
                y = (tile.y * roundedFactor).toInt()
            )
        }
        return ModificationApplyResult.Success(
            scaled,
            "Scaled by ${roundedFactor}x"
        )
    }

    private fun applyMirror(
        tiles: List<TilePlacement>,
        mod: TemplateModification.Mirror
    ): ModificationApplyResult {
        val mirrored = when (mod.axis) {
            Axis.HORIZONTAL -> {
                // Find rightmost edge, mirror x around center
                val maxX = tiles.maxOf { it.x + it.tileType.widthUnits }
                tiles.map { tile ->
                    val newX = maxX - tile.x - tile.tileType.widthUnits
                    tile.copy(
                        x = newX,
                        rotation = mirrorRotationHorizontal(tile.rotation)
                    )
                }
            }
            Axis.VERTICAL -> {
                // Find bottommost edge, mirror y around center
                val maxY = tiles.maxOf { it.y + it.tileType.heightUnits }
                tiles.map { tile ->
                    val newY = maxY - tile.y - tile.tileType.heightUnits
                    tile.copy(
                        y = newY,
                        rotation = mirrorRotationVertical(tile.rotation)
                    )
                }
            }
        }
        return ModificationApplyResult.Success(
            mirrored,
            "Mirrored ${mod.axis.name.lowercase()}"
        )
    }

    private fun mirrorRotationHorizontal(rotation: Rotation): Rotation {
        return when (rotation) {
            Rotation.R0 -> Rotation.R0
            Rotation.R90 -> Rotation.R270
            Rotation.R180 -> Rotation.R180
            Rotation.R270 -> Rotation.R90
        }
    }

    private fun mirrorRotationVertical(rotation: Rotation): Rotation {
        return when (rotation) {
            Rotation.R0 -> Rotation.R180
            Rotation.R90 -> Rotation.R90
            Rotation.R180 -> Rotation.R0
            Rotation.R270 -> Rotation.R270
        }
    }

    private fun applyTranslate(
        tiles: List<TilePlacement>,
        mod: TemplateModification.Translate
    ): ModificationApplyResult {
        val translated = tiles.map { tile ->
            tile.copy(
                x = tile.x + mod.dx,
                y = tile.y + mod.dy
            )
        }
        return ModificationApplyResult.Success(
            translated,
            "Translated by (${mod.dx}, ${mod.dy})"
        )
    }

    /**
     * Apply a Combine modification — brings in tiles from another template at an offset.
     */
    fun applyCombine(
        baseTemplateId: String,
        otherTemplateId: String,
        offset: Pair<Int, Int>
    ): TemplateResult {
        val baseTemplate = templates[baseTemplateId]
            ?: return TemplateResult.Rejected("Base template '$baseTemplateId' not found", null)
        val otherTemplate = templates[otherTemplateId]
            ?: return TemplateResult.Rejected("Other template '$otherTemplateId' not found", null)

        val combinedTiles = baseTemplate.tiles + otherTemplate.tiles.map { tile ->
            tile.copy(x = tile.x + offset.first, y = tile.y + offset.second)
        }

        val validationErrors = validateTiles(combinedTiles)
        if (validationErrors.isNotEmpty()) {
            val unmodifiedComposition = baseTemplate.toComposition()
            return TemplateResult.Rejected(
                "Combination caused constraint violations: ${validationErrors.joinToString("; ")}",
                unmodifiedComposition
            )
        }

        val composition = Composition(
            id = UUID.randomUUID().toString(),
            name = "${baseTemplate.name} + ${otherTemplate.name}",
            tiles = combinedTiles,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            source = Composition.Source.AI_GENERATED
        )

        return TemplateResult.Success(composition, listOf("Combined ${otherTemplate.name} at offset $offset"))
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validate a template: no overlaps, all within bounds, max tile count.
     */
    fun validateTemplate(template: CompositionTemplate): List<String> {
        return validateTiles(template.tiles)
    }

    /**
     * Validate a list of tiles: bounds checking, overlap detection, tile count.
     */
    fun validateTiles(tiles: List<TilePlacement>): List<String> {
        val errors = mutableListOf<String>()

        // Check tile count
        if (tiles.size > MAX_TILES) {
            errors.add("Too many tiles: ${tiles.size} (max $MAX_TILES)")
        }

        // Check bounds
        tiles.forEachIndexed { index, tile ->
            if (tile.x < 0 || tile.x + tile.tileType.widthUnits > GRID_SIZE) {
                errors.add("Tile $index (x=${tile.x}) out of horizontal bounds [0, $GRID_SIZE]")
            }
            if (tile.y < 0 || tile.y + tile.tileType.heightUnits > GRID_SIZE) {
                errors.add("Tile $index (y=${tile.y}) out of vertical bounds [0, $GRID_SIZE]")
            }
        }

        // Check overlaps using polygon intersection (SAT for triangles)
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                if (tilesOverlap(tiles[i], tiles[j])) {
                    errors.add("Tiles $i and $j overlap at (${tiles[i].x},${tiles[i].y}) and (${tiles[j].x},${tiles[j].y})")
                }
            }
        }

        return errors
    }

    /**
     * Check if two tiles' bounding boxes overlap.
     * Each tile occupies a widthUnits x heightUnits rectangle starting at (x, y).
     */
    fun boundingBoxesOverlap(a: TilePlacement, b: TilePlacement): Boolean {
        val aLeft = a.x
        val aRight = a.x + a.tileType.widthUnits
        val aTop = a.y
        val aBottom = a.y + a.tileType.heightUnits

        val bLeft = b.x
        val bRight = b.x + b.tileType.widthUnits
        val bTop = b.y
        val bBottom = b.y + b.tileType.heightUnits

        return !(aRight <= bLeft || bRight <= aLeft || aBottom <= bTop || bBottom <= aTop)
    }

    /**
     * Check if two tiles actually overlap using polygon intersection.
     * Squares use bounding box (already exact). Triangles use SAT polygon overlap.
     */
    fun tilesOverlap(a: TilePlacement, b: TilePlacement): Boolean {
        // Fast path: if bounding boxes don't overlap, tiles definitely don't
        if (!boundingBoxesOverlap(a, b)) return false

        // Both squares → bounding box is exact for axis-aligned squares
        if (a.tileType == TileType.SOLID_SQUARE || a.tileType == TileType.WINDOW_SQUARE) {
            if (b.tileType == TileType.SOLID_SQUARE || b.tileType == TileType.WINDOW_SQUARE) {
                return true // bounding boxes overlap and squares fill their entire box
            }
        }

        // At least one triangle → use polygon overlap
        return polygonsOverlap(getPolygon(a), getPolygon(b))
    }

    /**
     * Get the polygon vertices for a tile placement in world coordinates.
     * All tiles are 3x3 bounding boxes. Triangles fill only part of that box.
     */
    private fun getPolygon(tile: TilePlacement): List<Pair<Float, Float>> {
        val x = tile.x.toFloat()
        val y = tile.y.toFloat()
        val w = 3f  // widthUnits
        val h = 3f  // heightUnits

        // Define base polygon (R0) relative to top-left corner
        val baseVertices: List<Pair<Float, Float>> = when (tile.tileType) {
            TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
                listOf(0f to 0f, w to 0f, w to h, 0f to h)
            }
            TileType.RIGHT_TRIANGLE -> {
                // R0: fills lower-right triangle (0,h), (w,h), (w,0)
                listOf(0f to h, w to h, w to 0f)
            }
            TileType.EQUILATERAL_TRIANGLE -> {
                // R0: triangle pointing up with base at bottom
                listOf(0f to h, w / 2f to 0f, w to h)
            }
            TileType.ISOSCELES_TRIANGLE -> {
                // R0: tall triangle pointing up
                listOf(0f to h, w / 2f to 0f, w to h)
            }
        }

        // Rotate vertices around tile center
        val cx = w / 2f
        val cy = h / 2f
        val radians = Math.toRadians(tile.rotation.degrees.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()

        val rotated = baseVertices.map { (vx, vy) ->
            val dx = vx - cx
            val dy = vy - cy
            (cx + dx * cos - dy * sin) to (cy + dx * sin + dy * cos)
        }

        // Translate to world position
        return rotated.map { (vx, vy) -> (vx + x) to (vy + y) }
    }

    /**
     * Check if two convex polygons overlap using Separating Axis Theorem.
     */
    private fun polygonsOverlap(poly1: List<Pair<Float, Float>>, poly2: List<Pair<Float, Float>>): Boolean {
        val axes = getEdgeNormals(poly1) + getEdgeNormals(poly2)

        for (axis in axes) {
            val (min1, max1) = projectOntoAxis(poly1, axis)
            val (min2, max2) = projectOntoAxis(poly2, axis)

            // Check for gap
            if (max1 <= min2 || max2 <= min1) {
                return false // Separating axis found — no overlap
            }
        }

        return true // No separating axis found — polygons overlap
    }

    /**
     * Get the edge normals (perpendicular vectors) for a polygon.
     */
    private fun getEdgeNormals(polygon: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        return polygon.indices.map { i ->
            val j = (i + 1) % polygon.size
            val dx = polygon[j].first - polygon[i].first
            val dy = polygon[j].second - polygon[i].second
            // Normal is perpendicular: (-dy, dx)
            -dy to dx
        }
    }

    /**
     * Project a polygon onto an axis and return (min, max) range.
     */
    private fun projectOntoAxis(polygon: List<Pair<Float, Float>>, axis: Pair<Float, Float>): Pair<Float, Float> {
        val projections = polygon.map { (x, y) ->
            x * axis.first + y * axis.second
        }
        return projections.min() to projections.max()
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private fun parseModification(modJson: ModificationJson): TemplateModification? {
        return when (modJson.type.lowercase()) {
            "color_swap", "colorswap" -> {
                val from = TileColor.entries.firstOrNull {
                    it.name.equals(modJson.from, ignoreCase = true) ||
                    it.displayName.equals(modJson.from, ignoreCase = true)
                } ?: return null
                val to = TileColor.entries.firstOrNull {
                    it.name.equals(modJson.to, ignoreCase = true) ||
                    it.displayName.equals(modJson.to, ignoreCase = true)
                } ?: return null
                TemplateModification.ColorSwap(from, to)
            }
            "scale" -> {
                val factor = modJson.factor ?: return null
                TemplateModification.Scale(factor)
            }
            "mirror" -> {
                val axis = when (modJson.axis?.lowercase()) {
                    "horizontal", "h" -> Axis.HORIZONTAL
                    "vertical", "v" -> Axis.VERTICAL
                    else -> return null
                }
                TemplateModification.Mirror(axis)
            }
            "translate" -> {
                val dx = modJson.dx ?: return null
                val dy = modJson.dy ?: return null
                TemplateModification.Translate(dx, dy)
            }
            "combine" -> {
                val templateId = modJson.templateId ?: return null
                val offsetX = modJson.offsetX ?: 0
                val offsetY = modJson.offsetY ?: 0
                TemplateModification.Combine(templateId, Pair(offsetX, offsetY))
            }
            else -> null
        }
    }
}

// ── Mapping extensions ────────────────────────────────────────────────────────

fun TemplateJson.toDomain(): CompositionTemplate {
    val tiles = this.tiles.map { tileJson ->
        TilePlacement(
            tileType = TileType.entries.first { it.id.equals(tileJson.tileType, ignoreCase = true) || it.name.equals(tileJson.tileType, ignoreCase = true) },
            x = tileJson.x,
            y = tileJson.y,
            rotation = parseRotation(tileJson.rotation),
            color = parseColor(tileJson.color)
        )
    }
    return CompositionTemplate(
        id = this.id,
        name = this.name,
        category = this.category,
        description = this.description,
        tags = this.tags,
        tiles = tiles,
        thumbnailDescription = this.thumbnailDescription
    )
}

fun CompositionTemplate.toComposition(): Composition {
    val now = System.currentTimeMillis()
    return Composition(
        id = UUID.randomUUID().toString(),
        name = this.name,
        tiles = this.tiles,
        createdAt = now,
        updatedAt = now,
        source = Composition.Source.AI_GENERATED
    )
}

private fun parseRotation(rotationStr: String): Rotation {
    return when (rotationStr.uppercase().removePrefix("R")) {
        "0", "" -> Rotation.R0
        "90" -> Rotation.R90
        "180" -> Rotation.R180
        "270" -> Rotation.R270
        else -> Rotation.R0
    }
}

private fun parseColor(colorStr: String): TileColor {
    return TileColor.entries.firstOrNull {
        it.name.equals(colorStr, ignoreCase = true) ||
        it.displayName.equals(colorStr, ignoreCase = true) ||
        it.hex.equals(colorStr, ignoreCase = true)
    } ?: TileColor.RED
}
