# Block Builder Sprint 2 Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Complete the core app experience — real AI chat, interactive canvas, and saved gallery.

**Architecture:** Extend existing multi-module structure. Each feature is self-contained in its feature module with ViewModel + Screen. Cross-cutting concerns (LLM client, Room DB) stay in core/data.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, OkHttp+Moshi, Navigation 3

---

## Phase 1: Foundation (Issues #6, #10)

### Task 1: Polygon-based overlap detection (#6)

**Objective:** Replace bounding-box overlap with SAT polygon intersection for triangles.

**Files:**
- Modify: `app/core/domain/src/main/java/com/architectai/core/domain/model/TemplateEngine.kt`
- Modify: `app/core/domain/src/test/java/com/architectai/core/domain/model/TemplateValidationTest.kt`

**Step 1: Write failing tests**

```kotlin
@Test
fun `complementary right triangles in same bounding box do not overlap`() {
    val t1 = TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R0, TileColor.RED)
    val t2 = TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R180, TileColor.BLUE)
    // R0 fills lower-left triangle, R180 fills upper-right triangle — no overlap
    assertFalse(TemplateEngine.tilesOverlap(t1, t2))
}

@Test
fun `overlapping right triangles at same position same rotation do overlap`() {
    val t1 = TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R0, TileColor.RED)
    val t2 = TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R0, TileColor.BLUE)
    assertTrue(TemplateEngine.tilesOverlap(t1, t2))
}

@Test
fun `equilateral triangle and square overlapping regions detected`() {
    val square = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
    val tri = TilePlacement(TileType.EQUILATERAL_TRIANGLE, 1, 1, Rotation.R180, TileColor.BLUE)
    // Should detect if polygon regions overlap
    val result = TemplateEngine.tilesOverlap(square, tri)
    // Depends on exact coordinates — verify against grid
}
```

**Step 2: Implement polygon overlap in TemplateEngine**

```kotlin
// Add to TemplateEngine.kt

private fun getTrianglePolygon(tile: TilePlacement): List<Pair<Float, Float>> {
    val vertices = when (tile.tileType) {
        TileType.EQUILATERAL_TRIANGLE -> {
            val w = 3f * GRID_UNIT
            val h = 2.6f * GRID_UNIT
            listOf(0f to h, w/2 to 0f, w to h)
        }
        TileType.RIGHT_TRIANGLE -> {
            val s = 3f * GRID_UNIT
            listOf(0f to 0f, s to 0f, 0f to s)
        }
        TileType.ISOSCELES_TRIANGLE -> {
            val w = 3f * GRID_UNIT
            val h = 3f * GRID_UNIT
            listOf(0f to h, w/2 to 0f, w to h)
        }
        else -> emptyList()
    }
    return rotatePolygon(vertices, tile.rotation, getTileCenter(tile))
        .map { (it.first + tile.x * GRID_UNIT) to (it.second + tile.y * GRID_UNIT) }
}

private fun polygonsOverlap(p1: List<Pair<Float, Float>>, p2: List<Pair<Float, Float>>): Boolean {
    val axes = getAxes(p1) + getAxes(p2)
    for (axis in axes) {
        val proj1 = project(p1, axis)
        val proj2 = project(p2, axis)
        if (proj1.second < proj2.first || proj2.second < proj1.first) return false
    }
    return true
}

// SAT helper methods: getAxes(), project(), rotatePolygon()
```

**Step 3: Update tilesOverlap() to use polygon check for triangles**

```kotlin
fun tilesOverlap(t1: TilePlacement, t2: TilePlacement): Boolean {
    // Fast path: bounding box check
    if (!boundingBoxesOverlap(t1, t2)) return false
    // Both are squares → bounding box is sufficient
    if (t1.tileType.isSquare && t2.tileType.isSquare) return true
    // At least one triangle → polygon check
    val p1 = getPolygon(t1)
    val p2 = getPolygon(t2)
    return polygonsOverlap(p1, p2)
}
```

**Step 4: Run tests, commit**

```bash
cd app && ./gradlew :core:domain:test
git add -A && git commit -m "feat: implement polygon-based overlap detection for triangles (#6)"
```

---

### Task 2: Verify CI pipeline (#10)

**Objective:** Ensure PR #11 CI passes on merge.

**Verification:**
```bash
# After merging PR #11, check CI on master
gh pr checks 11
# Merge if green
gh pr merge 11 --squash
```

---

## Phase 2: Interactive Canvas (Issue #9)

### Task 3: Add drag gesture to BuildCanvas

**Objective:** Users can drag tiles to new positions on the canvas.

**Files:**
- Modify: `app/feature/build-feature/src/main/java/com/architectai/feature/build/BuildCanvas.kt`

**Implementation:**
```kotlin
// In BuildCanvas.kt, replace detectTapGestures with combined gesture detector
Modifier.pointerInput(canvasState.tiles) {
    detectDragGestures(
        onDragStart = { offset ->
            val tile = findTileAt(offset, canvasState)
            if (tile != null) {
                viewModel.startDragging(tile.id, offset)
            }
        },
        onDrag = { change, dragAmount ->
            viewModel.updateDrag(dragAmount)
        },
        onDragEnd = {
            viewModel.endDragging()
        },
        onDragCancel = {
            viewModel.endDragging()
        }
    )
}
```

### Task 4: Add zoom/pan to BuildCanvas

**Files:**
- Modify: `app/feature/build-feature/src/main/java/com/architectai/feature/build/BuildCanvas.kt`
- Modify: `app/feature/build-feature/src/main/java/com/architectai/feature/build/canvas/CanvasState.kt`

**Implementation:**
```kotlin
// Wrap canvas in a transformable modifier
var scale by remember { mutableFloatStateOf(1f) }
var offset by remember { mutableStateOf(Offset.Zero) }

Modifier.pointerInput(Unit) {
    detectTransformGestures { _, pan, zoom, _ ->
        scale = (scale * zoom).coerceIn(0.5f, 3f)
        offset += pan
        viewModel.updateCanvasTransform(scale, offset)
    }
}
```

### Task 5: Add rotation control and color picker

**Files:**
- Modify: `app/feature/build-feature/src/main/java/com/architectai/feature/build/BuildScreen.kt`
- Modify: `app/feature/build-feature/src/main/java/com/architectai/feature/build/BuildViewModel.kt`

**Implementation:**
- Add a bottom action bar that appears when a tile is selected
- Rotation buttons: 0°/90°/180°/270°
- Color picker: Row of `TileColor` swatches
- Wire to existing ViewModel methods (`updateTileRotation`, etc.)

### Task 6: Add snap animations and haptics

**Files:**
- Modify: `app/feature/build-feature/src/main/java/com/architectai/feature/build/BuildCanvas.kt`

**Implementation:**
```kotlin
// Use UpdateTransition for snap states
val transition = updateTransition(tile.snapState, label = "snap")
val scale by transition.animateFloat(
    transitionSpec = { spring(dampingRatio = 0.4f) },
    label = "scale"
) { state ->
    when (state) { SnapState.Dragging -> 1.1f; else -> 1f }
}
// Haptics on snap
view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
```

---

## Phase 3: Gallery (Issue #7)

### Task 7: Create GalleryViewModel

**Files:**
- Create: `app/feature/gallery/src/main/java/com/architectai/feature/gallery/GalleryViewModel.kt`

**Implementation:**
```kotlin
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: CompositionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState

    init { loadCompositions() }

    fun loadCompositions() { /* Flow from Room */ }
    fun deleteComposition(id: Long) { /* Delete via repository */ }
    fun renameComposition(id: Long, newName: String) { /* Update via repository */ }
}
```

### Task 8: Create GalleryScreen

**Files:**
- Create: `app/feature/gallery/src/main/java/com/architectai/feature/gallery/GalleryScreen.kt`

**Implementation:**
- LazyVerticalGrid with composition cards
- Each card: mini canvas preview + name + date
- Tap → full-screen detail view
- Empty state illustration
- Delete/rename from detail view

### Task 9: Wire Gallery into Navigation

**Files:**
- Modify: `app/app/src/main/java/com/example/architectai/Navigation.kt`

Replace `GalleryPlaceholderScreen()` with real `GalleryScreen()`.

### Task 10: Add "Save to Gallery" from Build canvas

**Files:**
- Modify: `app/feature/build-feature/src/main/java/com/architectai/feature/build/BuildViewModel.kt`

```kotlin
fun saveComposition(name: String) {
    val composition = Composition(
        name = name,
        tiles = canvasState.value.tiles.map { it.toTilePlacement() },
        createdAt = System.currentTimeMillis()
    )
    viewModelScope.launch { repository.saveComposition(composition) }
}
```

---

## Phase 4: Real LLM Integration (Issue #8)

### Task 11: Make LLM endpoint configurable

**Files:**
- Modify: `app/core/data/src/main/java/com/architectai/core/data/llm/OkHttpLLMClient.kt`
- Modify: `app/core/data/src/main/java/com/architectai/core/data/di/DataModule.kt`

### Task 12: Implement real API call flow in ChatViewModel

**Files:**
- Modify: `app/feature/chat/src/main/java/com/architectai/feature/chat/ChatViewModel.kt`

**Flow:**
1. User sends message → show loading
2. Build system prompt with tile format specification
3. POST to LLM API with user message + system prompt
4. Parse JSON response into `List<TilePlacement>`
5. On failure → fall back to template matching
6. Display result composition card

### Task 13: Add error handling and retry

**Files:**
- Modify: `app/feature/chat/src/main/java/com/architectai/feature/chat/ChatViewModel.kt`

---

## Phase 5: Polish

### Task 14: Edge-to-edge + IME padding for Chat

### Task 15: Staggered animations for AI composition placement

### Task 16: Accessibility — content descriptions, touch targets

---

## Estimated Effort

| Phase | Tasks | Est. Time |
|-------|-------|-----------|
| Phase 1: Foundation | 2 | 2 hours |
| Phase 2: Interactive Canvas | 4 | 4 hours |
| Phase 3: Gallery | 4 | 3 hours |
| Phase 4: Real LLM | 3 | 3 hours |
| Phase 5: Polish | 3 | 2 hours |
| **Total** | **16** | **~14 hours** |

## Recommended Execution Order

1. ✅ CI pipeline (PR #11) — merge first
2. Polygon overlap (#6) — unblocks richer templates
3. Gallery (#7) — persistence layer needed before polish
4. Drag/zoom/rotate (#9) — biggest UX improvement
5. Real LLM (#8) — last, depends on stable canvas
6. Polish — final pass
