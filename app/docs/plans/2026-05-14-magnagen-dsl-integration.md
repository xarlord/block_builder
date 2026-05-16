# MagnaGen DSL Integration — Architecture Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Replace raw JSON coordinate generation with a script-first DSL ("MagnaPy") that lets the LLM write assembly recipes instead of tile coordinate lists, then execute those scripts to produce validated Compositions.

**Architecture:** The LLM generates Kotlin-style DSL scripts (not Python — this is Android). A lightweight in-app interpreter parses and executes the DSL against the existing `TemplateEngine` validation pipeline. The current LLMClient → JSON → Composition flow is preserved as a fallback; the new flow is LLMClient → DSL Script → Interpreter → Composition.

**Tech Stack:** Kotlin DSL interpreter (hand-written recursive descent parser), existing TemplateEngine for validation, existing Hilt DI, no new dependencies.

---

## Current Architecture (v1.0.0)

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  ChatScreen  │────▶│ ChatViewModel│────▶│ OkHttpLLM    │────▶│  LLM API     │
│  (UI)        │     │              │     │ Client       │     │  (GLM/GPT)   │
└─────────────┘     └──────┬───────┘     └──────┬───────┘     └──────┬───────┘
                           │                     │                     │
                    ┌──────▼───────┐     ┌───────▼───────┐     ┌──────▼───────┐
                    │  Composition │     │  JSON Parse   │◀────│  LLM returns │
                    │  Repository  │     │  (Moshi)      │     │  raw JSON    │
                    └──────────────┘     └───────┬───────┘     └──────────────┘
                                                 │
                                         ┌───────▼───────┐
                                         │ TemplateEngine │
                                         │ (validate)    │
                                         └───────────────┘
```

**Problems with current approach:**
1. LLM generates `[{tileType, x, y, rotation, color}, ...]` — breaks on 20+ tiles
2. No parametric scaling (changing size = regenerate everything)
3. No symmetry/loop constructs (each tile is a separate JSON object)
4. Hard to debug (opaque JSON blob)
5. Template modification system is limited to 5 operations

---

## Proposed Architecture (v2.0 — MagnaGen DSL)

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  ChatScreen  │────▶│ ChatViewModel│────▶│ OkHttpLLM    │────▶│  LLM API     │
│  (UI)        │     │              │     │ Client       │     │  (GLM/GPT)   │
└─────────────┘     └──────┬───────┘     └──────┬───────┘     └──────┬───────┘
                           │                     │                     │
                    ┌──────▼───────┐     ┌───────▼───────┐     ┌──────▼───────┐
                    │  Composition │     │  DSL          │◀────│  LLM returns │
                    │  Repository  │     │  Interpreter  │     │  DSL script  │
                    └──────────────┘     └───────┬───────┘     └──────────────┘
                                                 │
                    ┌────────────────────────────▼───────────────────────────┐
                    │                   MagnaPy DSL Engine                    │
                    │  ┌────────────┐  ┌─────────────┐  ┌────────────────┐  │
                    │  │  Lexer +   │  │  AST Nodes  │  │  Evaluator     │  │
                    │  │  Parser    │──▶│  (domain)   │──▶│  → List<CanvasTile>│
                    │  └────────────┘  └─────────────┘  └───────┬────────┘  │
                    └───────────────────────────────────────────┼───────────┘
                                                            │
                                                    ┌───────▼───────┐
                                                    │ TemplateEngine │
                                                    │ (validate +   │
                                                    │  SAT overlap)  │
                                                    └───────┬───────┘
                                                            │
                                                    ┌───────▼───────┐
                                                    │  Composition  │
                                                    │  (validated)  │
                                                    └───────────────┘
```

---

## The MagnaPy DSL — Language Specification

### Design Principles
1. **Familiar Kotlin-like syntax** — LLMs excel at this
2. **Snap-first, not position-first** — `snapTo()` is the primary operation, raw `pos()` is escape hatch
3. **Loops and symmetry** — `repeat()`, `radial()`, `mirror()` built in
4. **Composable** — `group()` + `+` operator for combining sub-assemblies
5. **Validated** — every output runs through existing TemplateEngine

### DSL Syntax Reference

```kotlin
// === Primitives ===
square(color = RED)                           // Single square tile at (0,0)
triangle(color = BLUE)                        // Equilateral triangle at (0,0)
rightTriangle(color = GREEN)                  // Right triangle at (0,0)
isoscelesTriangle(color = YELLOW)             // Isosceles triangle at (0,0)
windowSquare(color = TRANSLUCENT)             // Window square at (0,0)

// === Positioning ===
square(color = RED).at(5, 10)                 // Place at grid (5, 10)
square(color = RED).at(x = 5, y = 10)

// === Snapping (PRIMARY OPERATION) ===
val body = square(color = RED)
val head = square(color = ORANGE).snapTo(body, edge = TOP)     // Snap above
val leg1 = rightTriangle(color = BROWN).snapTo(body, edge = BOTTOM_LEFT)
val leg2 = rightTriangle(color = BROWN).snapTo(body, edge = BOTTOM_RIGHT)

// === Edge identifiers ===
// Square edges: TOP, BOTTOM, LEFT, RIGHT
// Triangle edges: LEFT, RIGHT, BASE

// === Rotation ===
triangle(color = BLUE).rotate(90)             // 0/90/180/270
rightTriangle(color = GREEN).rotate(180).at(3, 4)

// === Groups ===
val face = group {
    square(color = YELLOW).at(0, 0)
    square(color = YELLOW).at(0, 1)
}

val mane = group {
    repeat(6) { i ->
        triangle(color = ORANGE)
            .at(0, 2)
            .rotate(i * 60)
    }
}

val lion = face + mane

// === Radial pattern ===
radial(count = 8, center = square(color = BLUE)) {
    triangle(color = RED)
}

// === Mirror ===
val leftWing = group {
    triangle(color = BLUE).at(0, 0)
    triangle(color = BLUE).at(1, 0).rotate(90)
}
val butterfly = leftWing + leftWing.mirror(axis = HORIZONTAL)

// === Scale (parametric) ===
val wall = repeat(4) { i ->
    square(color = RED).at(i * 3, 0)
}
// wall.scale(2) → repeats pattern with double spacing

// === Built-in functions ===
repeat(n) { i -> ... }                        // Loop with index
radial(count, center) { i -> ... }            // Radial placement
group { ... }                                 // Group tiles
mirror(axis = HORIZONTAL | VERTICAL)          // Mirror group
```

### What the LLM Generates

**Before (current — fragile JSON):**
```json
[
  {"tileType": "SOLID_SQUARE", "x": 0, "y": 0, "rotation": "R0", "color": "YELLOW"},
  {"tileType": "SOLID_SQUARE", "x": 0, "y": 3, "rotation": "R0", "color": "YELLOW"},
  {"tileType": "EQUILATERAL_TRIANGLE", "x": 0, "y": 6, "rotation": "R0", "color": "ORANGE"},
  {"tileType": "EQUILATERAL_TRIANGLE", "x": 0, "y": 6, "rotation": "R60", "color": "ORANGE"},
  ... (18 more entries with coordinate errors)
]
```

**After (MagnaPy DSL — robust):**
```kotlin
val face = group {
    square(color = YELLOW).at(0, 0)
    square(color = YELLOW).snapTo(last, edge = TOP)
}

val mane = group {
    repeat(6) { i ->
        triangle(color = ORANGE).snapTo(face, edge = TOP).rotate(i * 60)
    }
}

val body = group {
    square(color = YELLOW).at(0, 5)
    square(color = YELLOW).snapTo(last, edge = BOTTOM)
}

val legs = group {
    rightTriangle(color = BROWN).snapTo(body, edge = BOTTOM_LEFT)
    rightTriangle(color = BROWN).snapTo(body, edge = BOTTOM_RIGHT)
}

val tail = triangle(color = ORANGE).snapTo(body, edge = RIGHT).rotate(90)

face + mane + body + legs + tail
```

---

## Module Changes

### New Files to Create

```
core/domain/
├── dsl/
│   ├── MagnaPyLexer.kt          # Tokenizer — produces token stream
│   ├── MagnaPyParser.kt         # Recursive descent parser → AST
│   ├── MagnaPyAst.kt            # AST node definitions (sealed classes)
│   ├── MagnaPyEvaluator.kt      # AST evaluator → List<TilePlacement>
│   ├── MagnaPyErrorCode.kt      # Error types + user-friendly messages
│   └── SnapEngine.kt            # Edge snapping geometry calculator
└── (existing files unchanged)

core/data/
├── dsl/
│   ├── DslLlmPromptBuilder.kt   # System prompt for DSL generation
│   └── DslResponseParser.kt     # Extract DSL from LLM markdown response
└── (modify OkHttpLLMClient.kt — add DSL mode)

feature/chat/
├── (modify ChatViewModel.kt — add DSL execution path)
└── (modify ChatScreen.kt — show DSL script in result card, optional "View Code" toggle)
```

### Files to Modify

| File | Change |
|------|--------|
| `core/domain/.../TileType.kt` | Add `edgeCount` and `edgeLength` properties for snap geometry |
| `core/data/.../OkHttpLLMClient.kt` | Add `mode` parameter (JSON vs DSL), change system prompt accordingly |
| `core/data/.../LLMClient.kt` | Add `generationMode` to interface |
| `feature/chat/.../ChatViewModel.kt` | Branch: if DSL mode → parse+execute script, else existing JSON path |
| `feature/chat/.../ChatScreen.kt` | Optional: show DSL source, "View Code" toggle in result card |
| `core/data/.../DataModule.kt` | Provide new DSL dependencies |

### No Changes Needed

| Module | Why |
|--------|-----|
| `core/designsystem` | UI components unchanged |
| `core/svg` | SVG assets unchanged |
| `feature/build-feature` | Consumes Composition — doesn't care how it was created |
| `feature/library` | Template browsing unchanged |
| `feature/gallery` | Composition storage unchanged |
| `app/` (shell) | Navigation unchanged |

---

## Detailed Component Design

### 1. MagnaPyLexer

**Input:** DSL script string  
**Output:** `List<DslToken>`

```kotlin
data class DslToken(
    val type: DslTokenType,    // KEYWORD, IDENTIFIER, NUMBER, STRING, LPAREN, etc.
    val value: String,
    val line: Int,
    val column: Int
)

enum class DslTokenType {
    // Literals
    NUMBER, STRING, COLOR_LITERAL,    // RED, BLUE, etc.
    // Keywords
    VAL, SQUARE, TRIANGLE, RIGHT_TRIANGLE, ISOSCELES_TRIANGLE, WINDOW_SQUARE,
    AT, SNAP_TO, ROTATE, GROUP, REPEAT, RADIAL, MIRROR, LAST,
    COLOR, EDGE, AXIS, COUNT, CENTER,
    // Edge/axis literals
    TOP, BOTTOM, LEFT, RIGHT, BASE,
    HORIZONTAL, VERTICAL,
    // Operators
    PLUS,      // group composition
    DOT,       // method chain
    // Delimiters
    LPAREN, RPAREN, LBRACE, RBRACE, COMMA, ARROW, EQ,
    // Special
    IDENTIFIER, EOF
}
```

### 2. MagnaPyAst

```kotlin
sealed class DslNode {
    // Primitives
    data class TileLiteral(val type: TileType, val color: TileColor) : DslNode()
    data class At(val target: DslNode, val x: Int, val y: Int) : DslNode()
    data class SnapTo(val target: DslNode, val source: DslNode, val edge: Edge) : DslNode()
    data class Rotate(val target: DslNode, val degrees: Int) : DslNode()
    data class Group(val children: List<DslNode>) : DslNode()
    data class Repeat(val count: DslNode, val indexVar: String?, val body: DslNode) : DslNode()
    data class Radial(val count: DslNode, val center: DslNode?, val body: DslNode) : DslNode()
    data class Mirror(val target: DslNode, val axis: Axis) : DslNode()
    data class Scale(val target: DslNode, val factor: Float) : DslNode()
    data class Chain(val receiver: DslNode, val method: DslNode) : DslNode()
    data class Plus(val left: DslNode, val right: DslNode) : DslNode()
    data class VarDecl(val name: String, val value: DslNode) : DslNode()
    data class VarRef(val name: String) : DslNode()
    data class NumberLiteral(val value: Int) : DslNode()
    data class BinaryOp(val left: DslNode, val op: String, val right: DslNode) : DslNode()
    data class Block(val statements: List<DslNode>) : DslNode()
    data class LastRef(val scope: DslNode) : DslNode()    // `last` keyword
}

enum class Edge { TOP, BOTTOM, LEFT, RIGHT, BASE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
enum class Axis { HORIZONTAL, VERTICAL }
```

### 3. SnapEngine

The most critical new component. Calculates actual grid positions from snap operations.

```kotlin
class SnapEngine {
    /**
     * Calculate the position for [source] to snap to [target]'s [edge].
     * Returns (x, y) grid position for the source tile.
     */
    fun calculateSnapPosition(
        target: PlacedTile,          // Already placed tile with known (x, y)
        source: TileType,            // The tile being placed
        edge: Edge,                  // Which edge of target to snap to
        sourceRotation: Rotation     // Rotation of the source tile
    ): Pair<Int, Int>
    
    /**
     * Get all valid edges for a tile type.
     * Squares: TOP, BOTTOM, LEFT, RIGHT
     * EquilateralTriangle: LEFT, RIGHT, BASE
     * RightTriangle: LEFT, RIGHT, BASE (hypotenuse)
     * IsoscelesTriangle: LEFT, RIGHT, BASE
     */
    fun edgesForTile(tileType: TileType): List<Edge>
}
```

**Snap geometry rules (grid-based):**

| Target Tile | Target Edge | Source snaps to... |
|-------------|------------|-------------------|
| Square (3×3) | TOP | source.x = target.x, source.y = target.y - source.heightUnits |
| Square (3×3) | BOTTOM | source.x = target.x, source.y = target.y + target.heightUnits |
| Square (3×3) | LEFT | source.x = target.x - source.widthUnits, source.y = target.y |
| Square (3×3) | RIGHT | source.x = target.x + target.widthUnits, source.y = target.y |
| Triangle | BASE | Aligned below base edge |
| Triangle | LEFT | Aligned to left edge |
| Triangle | RIGHT | Aligned to right edge |

Corner snaps (TOP_LEFT, TOP_RIGHT, etc.) place the source diagonally.

### 4. MagnaPyEvaluator

**Input:** `DslNode` (AST root)  
**Output:** `List<TilePlacement>` (raw, unvalidated)

```kotlin
class MagnaPyEvaluator(private val snapEngine: SnapEngine) {
    // Evaluation context — variable bindings
    data class EvalContext(
        val variables: Map<String, DslValue>,
        val lastPlaced: PlacedTile?        // `last` reference
    )
    
    sealed class DslValue {
        data class TileList(val tiles: List<PlacedTile>) : DslValue()
        data class SingleTile(val tile: PlacedTile) : DslValue()
        data class IntVal(val value: Int) : DslValue()
    }
    
    fun evaluate(ast: DslNode): Result<List<TilePlacement>>
}
```

### 5. DslLlmPromptBuilder

Replaces the current JSON-focused system prompt with DSL-focused one.

```kotlin
class DslLlmPromptBuilder {
    fun buildSystemPrompt(): String {
        // Includes:
        // 1. DSL syntax reference (concise)
        // 2. Available tile types + colors
        // 3. Grid constraints (30×30, max 200 tiles)
        // 4. Snap semantics (edge alignment rules)
        // 5. 2-3 example scripts (simple, medium, complex)
        // 6. Instruction: "Output ONLY the DSL script in a ```kotlin code block"
    }
}
```

### 6. Integration Flow in ChatViewModel

```kotlin
// In ChatViewModel.sendMessage():

// 1. Call LLM (DSL mode)
val result = llmClient.generateComposition(prompt)

// 2. Parse response
val dslScript = DslResponseParser.extractScript(result.content)

// 3. Lex → Parse → Evaluate
val tokens = MagnaPyLexer(dslScript).tokenize()
val ast = MagnaPyParser(tokens).parse()
val rawTiles = MagnaPyEvaluator(snapEngine).evaluate(ast)
    .getOrElse { error ->
        // Fallback: try legacy JSON parsing
        return tryLegacyJsonParse(result.content)
    }

// 4. Validate via existing TemplateEngine
val validationErrors = templateEngine.validateTiles(rawTiles)
if (validationErrors.isNotEmpty()) {
    // Return partial result + errors to UI
    // Optionally: ask LLM to fix the script
}

// 5. Create Composition
val composition = Composition(
    id = uuid, name = extractName(prompt),
    tiles = rawTiles, source = AI_GENERATED,
    createdAt = now, updatedAt = now
)
```

---

## Edge Snapping — The Hard Problem

This is where 80% of the new complexity lives. Here's the detailed design:

### Grid Geometry

All tiles occupy a 3×3 grid cell at base. After rotation:

```
Square at (x,y):                Triangle at (x,y):
┌───┬───┬───┐                   │╲          ╱│
│   │   │   │                   │  ╲      ╱  │
│   │   │   │                   │    ╲  ╱    │
│   │   │   │                   │  3 rows     │
└───┴───┴───┘                   └─────────────┘
 3 cols × 3 rows                  3 cols × 3 rows
```

### Snap Position Calculation

For squares, it's straightforward grid arithmetic:
- `snapTo(target, TOP)`: source.y = target.y - 3
- `snapTo(target, BOTTOM)`: source.y = target.y + 3
- `snapTo(target, LEFT)`: source.x = target.x - 3
- `snapTo(target, RIGHT)`: source.x = target.x + 3

For triangles, it depends on rotation AND the source/target combination:
- Equilateral triangle base = 3 units, height ≈ 2.6 units
- Snap alignment must account for rotation (a 180° triangle has its base on top)

### Resolution Strategy

1. **Pre-compute a snap table** for all (targetTile × targetEdge × sourceTile × sourceRotation) = 5 × 4 × 5 × 4 = 400 entries
2. Store as `Map<SnapKey, Pair<Int, Int>>` — O(1) lookup at evaluation time
3. Entries that produce invalid snaps (e.g., triangle-to-triangle-right-edge) return error
4. This table is generated once at app start, used by `SnapEngine`

---

## Fallback Strategy

```
LLM Response
    │
    ├─ Extract DSL from ```kotlin block
    │   ├─ Parse OK → Evaluate → Validate → ✓ Composition
    │   └─ Parse FAIL ─┐
    │                   │
    ├─ Extract JSON from ```json block (legacy)
    │   ├─ Parse OK → Validate → ✓ Composition
    │   └─ Parse FAIL ─┐
    │                   │
    └─ Template keyword match ──→ Existing template fallback ──→ ✓ Composition
```

Three-tier fallback: DSL → JSON → Template. The user never sees a failure.

---

## LLM Prompt Strategy

The system prompt instructs the LLM to:
1. Always output DSL in a ` ```kotlin ` code block
2. Use `snapTo()` as the primary positioning method (not raw `at()`)
3. Use `group` + `repeat` for symmetric patterns
4. Keep total tile count under 200
5. Include a comment at the top with the design name

**Example prompt (abridged):**
```
You are MagnaPy, a CAD-like DSL for Magna-Tile assembly.
Output a Kotlin code block using the MagnaPy DSL.

Available tiles: square, triangle, rightTriangle, isoscelesTriangle, windowSquare
Colors: RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK, BROWN, BLACK, WHITE, TRANSLUCENT

Primary positioning: .snapTo(target, edge=TOP|BOTTOM|LEFT|RIGHT)
Escape hatch: .at(x, y)

[full syntax reference]
[3 examples]
```

---

## Implementation Phases

### Phase 1: DSL Core (core:domain/dsl/) — ~800 lines
1. `MagnaPyAst.kt` — AST node definitions
2. `MagnaPyErrorCode.kt` — Error types
3. `SnapEngine.kt` — Snap geometry + pre-computed table
4. `MagnaPyLexer.kt` — Tokenizer
5. `MagnaPyParser.kt` — Recursive descent parser
6. `MagnaPyEvaluator.kt` — AST → List<TilePlacement>
7. Unit tests for each component

### Phase 2: LLM Integration (core:data/dsl/) — ~300 lines
1. `DslLlmPromptBuilder.kt` — System prompt generator
2. `DslResponseParser.kt` — Extract DSL from LLM response
3. Modify `OkHttpLLMClient` — DSL mode
4. Modify `LLMClient` interface — add mode
5. Unit tests

### Phase 3: Chat Feature Integration — ~200 lines
1. Modify `ChatViewModel` — DSL execution path with 3-tier fallback
2. Modify `ChatScreen` — "View Code" toggle in result card
3. Integration tests

### Phase 4: Polish & Validation — ~150 lines
1. DSL error reporting (show script errors to user with line numbers)
2. Auto-retry: if DSL fails, send error back to LLM for fix
3. Performance: benchmark DSL evaluation (target <50ms for 200 tiles)
4. Update onboarding to mention DSL capability

---

## Risk Analysis

| Risk | Mitigation |
|------|-----------|
| LLM generates invalid DSL syntax | 3-tier fallback (DSL → JSON → Template) |
| Snap geometry edge cases | Pre-computed snap table + exhaustive unit tests |
| DSL evaluation too slow | Hand-written parser (not ANTLR), no reflection |
| LLM confused by new DSL prompt | Extensive examples in prompt, temperature=0.3 for DSL mode |
| Breaking existing chat functionality | Feature flag: `dsl_enabled` in LLMConfig, default false |
| Triangle snap positions incorrect | Visual validation: render snapped tiles, compare with expected |

---

## What's NOT in Scope (v2.0)

- **Physics simulation** (gravity/stability) — future v3.0
- **Multi-agent validation pipeline** — future v3.0
- **User-written DSL** (script editor) — future v2.1
- **DSL → animation export** — future v2.1
- **Python MagnaPy** — Kotlin DSL is sufficient for Android

---

## Success Metrics

1. **Reliability:** DSL mode produces valid compositions ≥90% of the time (vs ~60% with raw JSON for 20+ tiles)
2. **Symmetry:** Complex radial/loop patterns work correctly (gears, bridges, castles)
3. **Parametric:** "Make it bigger" succeeds via variable change (not full regeneration)
4. **Fallback:** Zero user-visible failures (3-tier fallback always produces something)
5. **Performance:** DSL parse + evaluate < 50ms for 200-tile composition
