package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileType

// ─────────────────────────────────────────────────────────────────────────────
// MagnaPy DSL — Snap Geometry Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A tile that has been placed on the canvas with a known position and rotation.
 */
data class PlacedTile(
    val tileType: TileType,
    val x: Int,
    val y: Int,
    val rotation: Rotation
)

/**
 * Composite key for the pre-computed snap-offset lookup table.
 *
 * The offset (dx, dy) required to place a source tile adjacent to a target
 * tile depends on:
 *   - the target tile's type & rotation (determines its shape in grid space)
 *   - the source tile's type & rotation
 *   - the snap edge being targeted
 */
data class SnapKey(
    val targetType: TileType,
    val targetRotation: Rotation,
    val sourceType: TileType,
    val sourceRotation: Rotation,
    val edge: SnapEdge
)

/**
 * Pre-computes and stores snap offsets for every valid combination of
 * target/source tile type, rotation, and snap edge.
 *
 * All tiles occupy a 3×3 bounding box in grid units. Squares are trivial;
 * triangles change their exposed edges depending on rotation.
 */
class SnapEngine {

    /** Lookup table: SnapKey → (dx, dy) offset from the target tile's origin. */
    private val snapTable: Map<SnapKey, Pair<Int, Int>>

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Calculate the grid position (x, y) for placing a source tile such that
     * it snaps to the given [edge] of the [target] tile.
     *
     * @return [Result.success] with the position, or [Result.failure] if no
     *   valid snap offset exists for this combination.
     */
    fun calculateSnapPosition(
        target: PlacedTile,
        sourceType: TileType,
        sourceRotation: Rotation,
        edge: SnapEdge
    ): Result<Pair<Int, Int>> {
        val key = SnapKey(
            targetType = target.tileType,
            targetRotation = target.rotation,
            sourceType = sourceType,
            sourceRotation = sourceRotation,
            edge = edge
        )
        val (dx, dy) = snapTable[key]
            ?: return Result.failure(
                IllegalStateException(
                    "No snap offset for target=${target.tileType}/${target.rotation}, " +
                        "source=$sourceType/$sourceRotation, edge=$edge"
                )
            )
        return Result.success(target.x + dx to target.y + dy)
    }

    /**
     * Return the list of semantically valid snap edges for a given tile type.
     * Squares expose four cardinal edges plus four corners.
     * Triangles expose BASE plus two sides and four corners.
     */
    fun edgesForTile(tileType: TileType): List<SnapEdge> = when (tileType) {
        TileType.SOLID_SQUARE,
        TileType.WINDOW_SQUARE -> listOf(
            SnapEdge.TOP, SnapEdge.BOTTOM, SnapEdge.LEFT, SnapEdge.RIGHT,
            SnapEdge.TOP_LEFT, SnapEdge.TOP_RIGHT,
            SnapEdge.BOTTOM_LEFT, SnapEdge.BOTTOM_RIGHT
        )
        TileType.EQUILATERAL_TRIANGLE,
        TileType.RIGHT_TRIANGLE,
        TileType.ISOSCELES_TRIANGLE -> listOf(
            SnapEdge.BASE, SnapEdge.LEFT, SnapEdge.RIGHT,
            SnapEdge.TOP_LEFT, SnapEdge.TOP_RIGHT,
            SnapEdge.BOTTOM_LEFT, SnapEdge.BOTTOM_RIGHT
        )
    }

    // ── Snap-table generation ────────────────────────────────────────────

    init {
        snapTable = buildMap {
            for (targetType in TileType.entries) {
                for (targetRotation in Rotation.entries) {
                    for (sourceType in TileType.entries) {
                        for (sourceRotation in Rotation.entries) {
                            for (edge in SnapEdge.entries) {
                                computeOffset(
                                    targetType, targetRotation,
                                    sourceType, sourceRotation,
                                    edge
                                )?.let { (dx, dy) ->
                                    put(
                                        SnapKey(
                                            targetType, targetRotation,
                                            sourceType, sourceRotation,
                                            edge
                                        ),
                                        dx to dy
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Offset computation helpers ───────────────────────────────────────

    private val TileType.isSquare: Boolean
        get() = this == TileType.SOLID_SQUARE || this == TileType.WINDOW_SQUARE

    private val TileType.isTriangle: Boolean
        get() = !isSquare

    /**
     * Compute the (dx, dy) offset for a single combination.
     * Returns `null` if the edge is not applicable (e.g. BASE on a square).
     *
     * Coordinate system: origin at top-left, x grows right, y grows down.
     * All tiles have a 3×3 bounding box.
     */
    private fun computeOffset(
        targetType: TileType,
        targetRotation: Rotation,
        sourceType: TileType,
        sourceRotation: Rotation,
        edge: SnapEdge
    ): Pair<Int, Int>? {
        val S = 3 // tile size in grid units

        // ── Corner snaps (same for all tile shapes) ──────────────────────
        // Corners are always relative to the bounding-box corners.
        return when (edge) {
            SnapEdge.TOP_LEFT     -> (-S) to (-S)
            SnapEdge.TOP_RIGHT    -> ( S) to (-S)
            SnapEdge.BOTTOM_LEFT  -> (-S) to ( S)
            SnapEdge.BOTTOM_RIGHT -> ( S) to ( S)

            // ── Cardinal edges (TOP/BOTTOM apply to all; LEFT/RIGHT differ by shape) ──
            SnapEdge.TOP -> {
                // Place source directly above target
                0 to (-S)
            }
            SnapEdge.BOTTOM -> {
                // Place source directly below target
                0 to S
            }
            SnapEdge.LEFT -> {
                if (targetType.isSquare) {
                    // Square: place source directly to the left
                    (-S) to 0
                } else {
                    // Triangle LEFT side edge — offset depends on rotation
                    when (targetRotation) {
                        Rotation.R0   -> (-S) to (-S / 2)   // (-3, -1)
                        Rotation.R90  -> (-S / 2) to S       // (-1, +3)
                        Rotation.R180 -> S to (S / 2)        // (+3, +1)
                        Rotation.R270 -> (S / 2) to (-S)     // (+1, -3)
                    }
                }
            }
            SnapEdge.RIGHT -> {
                if (targetType.isSquare) {
                    // Square: place source directly to the right
                    S to 0
                } else {
                    // Triangle RIGHT side edge — offset depends on rotation
                    when (targetRotation) {
                        Rotation.R0   -> S to (-S / 2)      // (+3, -1)
                        Rotation.R90  -> (S / 2) to (-S)     // (+1, -3)
                        Rotation.R180 -> (-S) to (S / 2)     // (-3, +1)
                        Rotation.R270 -> (-S / 2) to S       // (-1, +3)
                    }
                }
            }

            // ── Triangle BASE edge (invalid for squares) ──
            SnapEdge.BASE -> {
                if (targetType.isSquare) {
                    // BASE is not a valid edge for squares
                    null
                } else {
                    when (targetRotation) {
                        Rotation.R0   -> 0 to S       // base at bottom
                        Rotation.R90  -> S to 0       // base at right
                        Rotation.R180 -> 0 to (-S)    // base at top
                        Rotation.R270 -> (-S) to 0    // base at left
                    }
                }
            }
        }
    }
}
