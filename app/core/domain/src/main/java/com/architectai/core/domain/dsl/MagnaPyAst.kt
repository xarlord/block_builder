package com.architectai.core.domain.dsl

import com.architectai.core.domain.model.Axis
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType

// ─────────────────────────────────────────────────────────────────────────────
// MagnaPy DSL — Abstract Syntax Tree
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed base for every node in the MagnaPy AST.
 * Each subclass represents one DSL construct.
 */
sealed class DslNode {

    /** Human-readable name of this node type (useful for error messages). */
    open val nodeName: String get() = javaClass.simpleName
}

// ── Expression base ─────────────────────────────────────────────────────────

/**
 * Sealed subset of [DslNode] that evaluates to a value
 * (integer, tile reference, etc.) rather than a side-effect.
 */
sealed class DslExpr : DslNode()

// ── Binary operators ────────────────────────────────────────────────────────

/** Arithmetic / composition operators for [BinaryExpr]. */
enum class BinaryOp {
    PLUS,
    MINUS,
    TIMES,
    MOD
}

// ── Snap edges ──────────────────────────────────────────────────────────────

/** Edges (and corners) a source tile may snap to on a target tile. */
enum class SnapEdge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    BASE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

// ── Leaf nodes ──────────────────────────────────────────────────────────────

/** Primitive tile creation: `square(RED)`, `triangle(BLUE)`, … */
data class TileLiteral(
    val type: TileType,
    val color: TileColor
) : DslNode()

/** Integer literal (e.g. loop count, coordinate). */
data class IntLiteral(
    val value: Int
) : DslExpr()

/** Standalone color reference (used in color-change operations). */
data class ColorExpr(
    val color: TileColor
) : DslNode()

/** Variable reference by name. */
data class VarRef(
    val name: String
) : DslExpr() {
    override val nodeName: String get() = "VarRef($name)"
}

/** Implicit `it` parameter inside lambda bodies. */
data object ItParam : DslExpr() {
    override val nodeName: String get() = "it"
}

/** Reference to the last placed tile (`last`). */
data object LastRef : DslNode() {
    override val nodeName: String get() = "last"
}

// ── Positioning & snapping ──────────────────────────────────────────────────

/** Position a tile at absolute grid coordinates: `tile.at(x, y)`. */
data class AtExpr(
    val target: DslNode,
    val x: DslExpr,
    val y: DslExpr
) : DslNode()

/** Snap a source tile to an edge of a target tile: `source.snapTo(target, LEFT)`. */
data class SnapToExpr(
    val source: DslNode,
    val target: DslNode,
    val edge: SnapEdge
) : DslNode()

/** Rotate a tile by the given number of degrees: `tile.rotate(90)`. */
data class RotateExpr(
    val target: DslNode,
    val degrees: DslExpr
) : DslNode()

// ── Composition ─────────────────────────────────────────────────────────────

/** Group of tiles treated as a single unit. */
data class GroupExpr(
    val children: List<DslNode>
) : DslNode()

/** Composition via `+`: combines two tile expressions into a group. */
data class PlusExpr(
    val left: DslNode,
    val right: DslNode
) : DslNode()

/** Method-chaining call: `receiver.method(arg1 = value1, …)`. */
data class DotExpr(
    val receiver: DslNode,
    val method: String,
    val args: Map<String, DslExpr>
) : DslNode()

// ── Repetition & patterns ───────────────────────────────────────────────────

/** Repeat a body N times, optionally binding the index to [indexVar]: `repeat(4) { i -> … }`. */
data class RepeatExpr(
    val count: DslExpr,
    val indexVar: String?,
    val body: DslNode
) : DslNode()

/** Arrange copies of [body] radially around an optional [center]. */
data class RadialExpr(
    val count: DslExpr,
    val center: DslNode?,
    val body: DslNode
) : DslNode()

// ── Transformations ─────────────────────────────────────────────────────────

/** Mirror a tile group along [Axis]. */
data class MirrorExpr(
    val target: DslNode,
    val axis: Axis
) : DslNode()

/** Scale a tile group by a uniform factor. */
data class ScaleExpr(
    val target: DslNode,
    val factor: Float
) : DslNode()

// ── Arithmetic ──────────────────────────────────────────────────────────────

/** Binary arithmetic expression: `left op right`. */
data class BinaryExpr(
    val left: DslExpr,
    val op: BinaryOp,
    val right: DslExpr
) : DslExpr()

// ── Variables & blocks ──────────────────────────────────────────────────────

/** Value declaration: `val name = value`. */
data class VarDecl(
    val name: String,
    val value: DslNode
) : DslNode()

/** Block of statements with an optional result expression. */
data class BlockExpr(
    val statements: List<DslNode>,
    val result: DslNode?
) : DslNode()
