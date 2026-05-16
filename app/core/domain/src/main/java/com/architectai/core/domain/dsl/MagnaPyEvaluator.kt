package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.Axis
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType

// ─────────────────────────────────────────────────────────────────────────────
// MagnaPy DSL — AST Evaluator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Walks a MagnaPy AST ([DslNode]) and produces a [Result] containing
 * [List]<[TilePlacement]> — the final grid layout produced by the DSL program.
 *
 * ### Evaluation model
 * - Each node evaluates to a [DslValue]: either a list of placed tiles,
 *   a single placed tile, or an integer.
 * - An [EvalContext] carries variables, the last-placed tile, and loop indices.
 * - After evaluation, all tiles are validated (count ≤ 200, positions 0..29).
 *
 * ### Usage
 * ```kotlin
 * val result = MagnaPyEvaluator().evaluate(ast)
 * result.onSuccess { placements -> ... }
 * ```
 */
class MagnaPyEvaluator {

    // ── Evaluation value types ─────────────────────────────────────────────

    /**
     * Sealed hierarchy of values that AST nodes can evaluate to.
     */
    sealed class DslValue {
        /** An ordered list of placed tiles (e.g. from a group or repeat). */
        data class TileListValue(val tiles: List<PlacedTile>) : DslValue()

        /** A single placed tile. */
        data class SingleTileValue(val tile: PlacedTile) : DslValue()

        /** An integer value (loop count, coordinate, rotation degrees). */
        data class IntValue(val value: Int) : DslValue()
    }

    // ── Evaluation context ─────────────────────────────────────────────────

    /**
     * Mutable context carried through evaluation.
     */
    data class EvalContext(
        val variables: MutableMap<String, DslValue> = mutableMapOf(),
        var lastPlaced: PlacedTile? = null,
        var indexVar: Pair<String, Int>? = null
    )

    // ── Snap engine (shared across evaluations) ────────────────────────────

    private val snapEngine = SnapEngine()

    // ── Grid constraints ───────────────────────────────────────────────────

    private companion object {
        const val MAX_TILES = 200
        const val GRID_MIN = 0
        const val GRID_MAX = 29
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Evaluate the given [ast] root node and produce the final list of
     * [TilePlacement] results, or a failure containing a [DslError].
     */
    fun evaluate(ast: DslNode): Result<List<TilePlacement>> = runCatching {
        val ctx = EvalContext()
        val value = evalNode(ast, ctx)
        val placedTiles = extractTiles(value)

        // ── Validate constraints ────────────────────────────────────────
        if (placedTiles.size > MAX_TILES) {
            throw DslError.EvalError(
                "Too many tiles: ${placedTiles.size} (max $MAX_TILES)"
            )
        }

        placedTiles.forEach { tile ->
            if (tile.x < GRID_MIN || tile.x > GRID_MAX ||
                tile.y < GRID_MIN || tile.y > GRID_MAX
            ) {
                throw DslError.EvalError(
                    "Tile at (${tile.x}, ${tile.y}) is outside the " +
                        "grid bounds ($GRID_MIN..$GRID_MAX)"
                )
            }
        }

        // ── Convert PlacedTile → TilePlacement (with color lookup) ──────
        placedTiles.toTilePlacements()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Core dispatch
    // ═══════════════════════════════════════════════════════════════════════

    private fun evalNode(node: DslNode, ctx: EvalContext): DslValue = when (node) {
        is TileLiteral    -> evalTileLiteral(node, ctx)
        is IntLiteral     -> DslValue.IntValue(node.value)
        is ColorExpr      -> DslValue.IntValue(0) // colors don't produce tiles directly
        is VarRef         -> evalVarRef(node, ctx)
        is ItParam        -> evalItParam(node, ctx)
        is LastRef        -> evalLastRef(node, ctx)
        is AtExpr         -> evalAtExpr(node, ctx)
        is SnapToExpr     -> evalSnapToExpr(node, ctx)
        is RotateExpr     -> evalRotateExpr(node, ctx)
        is GroupExpr      -> evalGroupExpr(node, ctx)
        is PlusExpr       -> evalPlusExpr(node, ctx)
        is DotExpr        -> evalDotExpr(node, ctx)
        is RepeatExpr     -> evalRepeatExpr(node, ctx)
        is RadialExpr     -> evalRadialExpr(node, ctx)
        is MirrorExpr     -> evalMirrorExpr(node, ctx)
        is ScaleExpr      -> evalScaleExpr(node, ctx)
        is BinaryExpr     -> evalBinaryExpr(node, ctx)
        is VarDecl        -> evalVarDecl(node, ctx)
        is BlockExpr      -> evalBlockExpr(node, ctx)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Leaf nodes
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * `square(RED)` → SingleTileValue at (0, 0) with R0.
     * We store the color in the variable context so it can be retrieved
     * when converting to TilePlacement at the end.
     */
    private fun evalTileLiteral(node: TileLiteral, ctx: EvalContext): DslValue {
        val tile = PlacedTile(
            tileType = node.type,
            x = 0,
            y = 0,
            rotation = Rotation.R0
        )
        ctx.lastPlaced = tile
        // Store color mapping for later conversion
        tileColorMap[tile] = node.color
        return DslValue.SingleTileValue(tile)
    }

    /**
     * Variable lookup by name.
     */
    private fun evalVarRef(node: VarRef, ctx: EvalContext): DslValue {
        return ctx.variables[node.name]
            ?: throw DslError.EvalError(
                "Undefined variable '${node.name}'",
                node.nodeName
            )
    }

    /**
     * Implicit `it` parameter → lookup the current loop index.
     */
    private fun evalItParam(node: ItParam, ctx: EvalContext): DslValue {
        val (name, value) = ctx.indexVar
            ?: throw DslError.EvalError(
                "'it' is not available outside of a loop body",
                node.nodeName
            )
        return DslValue.IntValue(value)
    }

    /**
     * `last` → returns the last placed tile as a SingleTileValue.
     * If no tile has been placed yet, throws a clear error.
     */
    private fun evalLastRef(node: LastRef, ctx: EvalContext): DslValue {
        val last = ctx.lastPlaced
            ?: throw DslError.EvalError(
                "No tile has been placed yet — 'last' is undefined. " +
                    "Use .at(x, y) to position the first tile before using .snapTo(last, ...).",
                node.nodeName
            )
        return DslValue.SingleTileValue(last)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Positioning & snapping
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * `tile.at(x, y)` → evaluate target, override its x,y coordinates.
     */
    private fun evalAtExpr(node: AtExpr, ctx: EvalContext): DslValue {
        val targetVal = evalNode(node.target, ctx)
        val x = evalInt(node.x, ctx)
        val y = evalInt(node.y, ctx)

        return repositionTiles(targetVal, x, y, ctx)
    }

    /**
     * `source.snapTo(target, EDGE)` → use SnapEngine to calculate snap position.
     */
    private fun evalSnapToExpr(node: SnapToExpr, ctx: EvalContext): DslValue {
        val sourceVal = evalNode(node.source, ctx)
        val targetVal = evalNode(node.target, ctx)

        val targetTile = extractSingleTile(targetVal, "snapTo target")

        // For each source tile, calculate snap position
        val sourceTiles = extractTiles(sourceVal)
        val snapped = mutableListOf<PlacedTile>()

        for (src in sourceTiles) {
            val result = snapEngine.calculateSnapPosition(
                target = targetTile,
                sourceType = src.tileType,
                sourceRotation = src.rotation,
                edge = node.edge
            )
            val (snapX, snapY) = result.getOrElse { throwable ->
                throw DslError.SnapError(
                    detail = throwable.message ?: "Unknown snap error",
                    sourceTile = src.tileType.name,
                    targetTile = targetTile.tileType.name,
                    edge = node.edge.name
                )
            }
            val placed = src.copy(x = snapX, y = snapY)
            ctx.lastPlaced = placed
            transferColor(src, placed)
            snapped.add(placed)
        }

        return if (snapped.size == 1) DslValue.SingleTileValue(snapped.first())
        else DslValue.TileListValue(snapped)
    }

    /**
     * `tile.rotate(degrees)` → apply rotation (snap to nearest 90°).
     */
    private fun evalRotateExpr(node: RotateExpr, ctx: EvalContext): DslValue {
        val targetVal = evalNode(node.target, ctx)
        val degreesRaw = evalInt(node.degrees, ctx)
        val rotation = degreesToRotation(degreesRaw)

        val tiles = extractTiles(targetVal)
        val rotated = tiles.map { tile ->
            // Combine existing rotation with new rotation
            val combinedDegrees = (tile.rotation.degrees + rotation.degrees) % 360
            val newRotation = degreesToRotation(combinedDegrees)
            val placed = tile.copy(rotation = newRotation)
            ctx.lastPlaced = placed
            transferColor(tile, placed)
            placed
        }

        return if (rotated.size == 1) DslValue.SingleTileValue(rotated.first())
        else DslValue.TileListValue(rotated)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Composition
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * `group { ... }` → evaluate all children, collect into TileListValue.
     */
    private fun evalGroupExpr(node: GroupExpr, ctx: EvalContext): DslValue {
        val allTiles = mutableListOf<PlacedTile>()
        for (child in node.children) {
            val childVal = evalNode(child, ctx)
            allTiles.addAll(extractTiles(childVal))
        }
        return DslValue.TileListValue(allTiles)
    }

    /**
     * `left + right` → concatenate tile lists.
     */
    private fun evalPlusExpr(node: PlusExpr, ctx: EvalContext): DslValue {
        val leftVal = evalNode(node.left, ctx)
        val rightVal = evalNode(node.right, ctx)

        val leftTiles = extractTiles(leftVal)
        val rightTiles = extractTiles(rightVal)

        return DslValue.TileListValue(leftTiles + rightTiles)
    }

    /**
     * Generic dot expression: `receiver.method(args)`.
     * Handles any unrecognized method call.
     */
    private fun evalDotExpr(node: DotExpr, ctx: EvalContext): DslValue {
        val receiverVal = evalNode(node.receiver, ctx)

        // Handle known generic methods
        return when (node.method) {
            "target" -> {
                // mirror().target(expr) — the arg is the actual target
                val targetArg = node.args["target"] ?: node.args.entries.firstOrNull()?.value
                if (targetArg != null) {
                    evalNode(targetArg, ctx)
                } else {
                    receiverVal
                }
            }
            else -> {
                // Unknown method — return receiver unchanged
                receiverVal
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Repetition & patterns
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * `repeat(count) { i -> body }` → evaluate body N times, each iteration
     * updates the index variable.
     */
    private fun evalRepeatExpr(node: RepeatExpr, ctx: EvalContext): DslValue {
        val count = evalInt(node.count, ctx)
        if (count < 0) {
            throw DslError.EvalError(
                "Repeat count cannot be negative: $count",
                node.nodeName
            )
        }

        val allTiles = mutableListOf<PlacedTile>()
        val prevIndexVar = ctx.indexVar

        for (i in 0 until count) {
            // Set index variable in both indexVar (for ItParam) and variables map (for VarRef)
            val varName = node.indexVar ?: "it"
            ctx.indexVar = varName to i
            ctx.variables[varName] = DslValue.IntValue(i)

            val bodyVal = evalNode(node.body, ctx)
            allTiles.addAll(extractTiles(bodyVal))
        }

        // Restore previous index var
        ctx.indexVar = prevIndexVar

        return DslValue.TileListValue(allTiles)
    }

    /**
     * `radial(count=N) { body }` → evaluate body N times with angular offsets.
     * Each copy gets rotation = (360/count * i) degrees.
     */
    private fun evalRadialExpr(node: RadialExpr, ctx: EvalContext): DslValue {
        val count = evalInt(node.count, ctx)
        if (count <= 0) {
            throw DslError.EvalError(
                "Radial count must be positive: $count",
                node.nodeName
            )
        }

        val allTiles = mutableListOf<PlacedTile>()
        val prevIndexVar = ctx.indexVar

        // Evaluate center if provided (to set lastPlaced context)
        if (node.center != null) {
            evalNode(node.center, ctx)
        }

        val angleStep = 360f / count

        for (i in 0 until count) {
            val varName = "it"
            ctx.indexVar = varName to i

            val degreesOffset = (angleStep * i).toInt()
            val rotation = degreesToRotation(degreesOffset)

            val bodyVal = evalNode(node.body, ctx)
            val tiles = extractTiles(bodyVal)

            // Apply the radial rotation to each tile
            for (tile in tiles) {
                val combinedDegrees = (tile.rotation.degrees + rotation.degrees) % 360
                val newRotation = degreesToRotation(combinedDegrees)
                val placed = tile.copy(rotation = newRotation)
                ctx.lastPlaced = placed
                transferColor(tile, placed)
                allTiles.add(placed)
            }
        }

        ctx.indexVar = prevIndexVar

        return DslValue.TileListValue(allTiles)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Transformations
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * `mirror(axis=X)` → create a mirrored copy.
     * HORIZONTAL: flip x around the group center.
     * VERTICAL: flip y around the group center.
     */
    private fun evalMirrorExpr(node: MirrorExpr, ctx: EvalContext): DslValue {
        val targetVal = evalNode(node.target, ctx)
        val tiles = extractTiles(targetVal)

        if (tiles.isEmpty()) return DslValue.TileListValue(emptyList())

        // Calculate the center of the tile group
        val minX = tiles.minOf { it.x }
        val maxX = tiles.maxOf { it.x }
        val minY = tiles.minOf { it.y }
        val maxY = tiles.maxOf { it.y }
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0

        val mirrored = tiles.map { tile ->
            val newX: Int
            val newY: Int
            when (node.axis) {
                Axis.HORIZONTAL -> {
                    // Flip around horizontal center line (flip y)
                    newX = tile.x
                    newY = (2 * centerY - tile.y).roundToInt()
                }
                Axis.VERTICAL -> {
                    // Flip around vertical center line (flip x)
                    newX = (2 * centerX - tile.x).roundToInt()
                    newY = tile.y
                }
            }
            // Also flip rotation for the mirrored tile
            val mirroredRotation = mirrorRotation(tile.rotation, node.axis)
            val placed = tile.copy(x = newX, y = newY, rotation = mirroredRotation)
            ctx.lastPlaced = placed
            transferColor(tile, placed)
            placed
        }

        // Return both original and mirrored tiles
        return DslValue.TileListValue(tiles + mirrored)
    }

    /**
     * `scale(factor)` → multiply all tile positions by factor.
     */
    private fun evalScaleExpr(node: ScaleExpr, ctx: EvalContext): DslValue {
        val targetVal = evalNode(node.target, ctx)
        val tiles = extractTiles(targetVal)

        val scaled = tiles.map { tile ->
            val newX = (tile.x * node.factor).roundToInt()
            val newY = (tile.y * node.factor).roundToInt()
            val placed = tile.copy(x = newX, y = newY)
            ctx.lastPlaced = placed
            transferColor(tile, placed)
            placed
        }

        return if (scaled.size == 1) DslValue.SingleTileValue(scaled.first())
        else DslValue.TileListValue(scaled)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Arithmetic
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Binary arithmetic: `left op right`.
     */
    private fun evalBinaryExpr(node: BinaryExpr, ctx: EvalContext): DslValue {
        val left = evalInt(node.left, ctx)
        val right = evalInt(node.right, ctx)

        val result = when (node.op) {
            BinaryOp.PLUS  -> left + right
            BinaryOp.MINUS -> left - right
            BinaryOp.TIMES -> left * right
            BinaryOp.MOD   -> if (right == 0) {
                throw DslError.EvalError("Division by zero", node.nodeName)
            } else {
                left % right
            }
        }

        return DslValue.IntValue(result)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Variables & blocks
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * `val name = value` → evaluate value, store in context variables.
     */
    private fun evalVarDecl(node: VarDecl, ctx: EvalContext): DslValue {
        val value = evalNode(node.value, ctx)
        ctx.variables[node.name] = value
        return value
    }

    /**
     * Block of statements → evaluate sequentially, return last value.
     *
     * All statements (including VarDecls) contribute their tiles to the
     * result. This is necessary because LLM-generated scripts often consist
     * entirely of `val x = tile.at(...)` declarations without a final
     * expression that references the variables.
     */
    private fun evalBlockExpr(node: BlockExpr, ctx: EvalContext): DslValue {
        val allTiles = mutableListOf<PlacedTile>()
        for (stmt in node.statements) {
            val value = evalNode(stmt, ctx)
            // Collect tiles from ALL statements, including VarDecls.
            // VarDecl stores the value in variables but also produces tiles.
            extractTiles(value).let { allTiles.addAll(it) }
        }
        // If there's a result expression, evaluate it and collect its tiles
        if (node.result != null) {
            val resultValue = evalNode(node.result, ctx)
            extractTiles(resultValue).let { allTiles.addAll(it) }
        }
        // If multiple statements produced tiles, return them as a group
        return if (allTiles.size > 1) {
            DslValue.TileListValue(allTiles)
        } else if (allTiles.size == 1) {
            DslValue.SingleTileValue(allTiles.first())
        } else {
            DslValue.TileListValue(emptyList())
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Evaluate a [DslExpr] and extract its integer value.
     */
    private fun evalInt(expr: DslExpr, ctx: EvalContext): Int {
        val value = evalNode(expr, ctx)
        return when (value) {
            is DslValue.IntValue -> value.value
            else -> throw DslError.EvalError(
                "Expected integer value but got ${value::class.simpleName}",
                expr.nodeName
            )
        }
    }

    /**
     * Extract all [PlacedTile] from a [DslValue].
     */
    private fun extractTiles(value: DslValue): List<PlacedTile> = when (value) {
        is DslValue.TileListValue   -> value.tiles
        is DslValue.SingleTileValue -> listOf(value.tile)
        is DslValue.IntValue        -> emptyList()
    }

    /**
     * Extract a single tile from a value, or throw if none/multiple.
     */
    private fun extractSingleTile(value: DslValue, context: String): PlacedTile {
        return when (value) {
            is DslValue.SingleTileValue -> value.tile
            is DslValue.TileListValue   -> {
                if (value.tiles.size == 1) value.tiles.first()
                else throw DslError.EvalError(
                    "Expected a single tile for $context but got ${value.tiles.size} tiles"
                )
            }
            is DslValue.IntValue -> throw DslError.EvalError(
                "Expected a tile for $context but got an integer value"
            )
        }
    }

    /**
     * Reposition all tiles in a value to the given (x, y) coordinates.
     * For single tiles, this is straightforward.
     * For tile lists, the first tile is placed at (x, y) and others
     * maintain their relative offsets.
     */
    private fun repositionTiles(
        value: DslValue,
        x: Int,
        y: Int,
        ctx: EvalContext
    ): DslValue {
        val tiles = extractTiles(value)
        if (tiles.isEmpty()) {
            return DslValue.TileListValue(emptyList())
        }

        // Calculate offset from first tile's original position
        val first = tiles.first()
        val dx = x - first.x
        val dy = y - first.y

        val repositioned = tiles.map { tile ->
            val placed = tile.copy(
                x = tile.x + dx,
                y = tile.y + dy
            )
            ctx.lastPlaced = placed
            transferColor(tile, placed)
            placed
        }

        return if (repositioned.size == 1) {
            DslValue.SingleTileValue(repositioned.first())
        } else {
            DslValue.TileListValue(repositioned)
        }
    }

    /**
     * Convert an integer degrees value to the nearest [Rotation] enum.
     * Snaps to the nearest 90° increment.
     */
    private fun degreesToRotation(degrees: Int): Rotation {
        val normalized = ((degrees % 360) + 360) % 360
        val snapped = when {
            normalized < 45  -> 0
            normalized < 135 -> 90
            normalized < 225 -> 180
            normalized < 315 -> 270
            else             -> 0
        }
        return when (snapped) {
            0   -> Rotation.R0
            90  -> Rotation.R90
            180 -> Rotation.R180
            270 -> Rotation.R270
            else -> Rotation.R0
        }
    }

    /**
     * Mirror a rotation around the given axis.
     */
    private fun mirrorRotation(rotation: Rotation, axis: Axis): Rotation {
        // When mirroring horizontally (flip y), the rotation mirrors around the horizontal axis
        // When mirroring vertically (flip x), the rotation mirrors around the vertical axis
        return when (axis) {
            Axis.HORIZONTAL -> when (rotation) {
                Rotation.R0   -> Rotation.R0
                Rotation.R90  -> Rotation.R270
                Rotation.R180 -> Rotation.R180
                Rotation.R270 -> Rotation.R90
            }
            Axis.VERTICAL -> when (rotation) {
                Rotation.R0   -> Rotation.R0
                Rotation.R90  -> Rotation.R270
                Rotation.R180 -> Rotation.R180
                Rotation.R270 -> Rotation.R90
            }
        }
    }

    /**
     * Round a float to the nearest Int.
     */
    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()

    /**
     * Round a double to the nearest Int.
     */
    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()

    // ── Tile color tracking ────────────────────────────────────────────────
    // PlacedTile doesn't carry color, so we track it separately and apply
    // it during the final conversion to TilePlacement.

    private val tileColorMap = mutableMapOf<PlacedTile, TileColor>()

    /**
     * Transfer the color mapping when a tile is copied/transformed.
     */
    private fun transferColor(original: PlacedTile, newTile: PlacedTile) {
        tileColorMap[original]?.let { color ->
            tileColorMap[newTile] = color
        }
    }

    /**
     * After evaluation, convert [PlacedTile] to [TilePlacement],
     * looking up the associated color.
     */
    private fun List<PlacedTile>.toTilePlacements(): List<TilePlacement> {
        return this.map { pt ->
            val color = tileColorMap[pt] ?: TileColor.RED
            TilePlacement(
                tileType = pt.tileType,
                x = pt.x,
                y = pt.y,
                rotation = pt.rotation,
                color = color
            )
        }
    }
}
