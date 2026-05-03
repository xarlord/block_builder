# Tile Geometry & Snap System Design

## Grid System
- 30x30 grid, origin (0,0) at top-left
- Each tile occupies a 3x3 bounding box regardless of rotation
- Position (x,y) = top-left corner of bounding box
- Bounding box spans (x,y) to (x+3, y+3)

---

## Primitive ASCII Art (each `#` = 1 unit, `.` = empty)

### Square (S) — all rotations identical
```
###    Vertices: TL(0,0) TR(3,0)
###              BL(0,3) BR(3,3)
###    Flat edges: TOP, RIGHT, BOTTOM, LEFT (all 3 units)
```

### Equilateral Triangle (T) — rotation changes shape
```
R0 apex-UP:       R90 apex-RIGHT:   R180 apex-DOWN:   R270 apex-LEFT:
.#.               ...               ###               ##.
###               .##               ###               #..
###               ###               .#.               ...

Flat: BOTTOM      Flat: LEFT        Flat: TOP         Flat: RIGHT
```

### Right Triangle (R) — rotation changes shape
```
R0 right-TOP-R:   R90 right-BOT-R:  R180 right-BOT-L: R270 right-TOP-L:
###               ..#               ..#               ###
.##               .##               ##.               ##.
..#               ###               ###               #..

Flat: TOP+RIGHT   Flat: RIGHT+BOT   Flat: BOT+LEFT    Flat: LEFT+TOP
```

### Isosceles Triangle (I) — same as Equilateral for rendering
```
Identical to Equilateral Triangle in all rotations.
```

---

## Edge Definitions (snap surfaces)

Every tile has 4 potential edge positions. Only FLAT edges can snap.
Each flat edge is exactly 3 units long (matches grid tile width).

### Square — 4 flat edges (always)
| Edge   | Start → End        | Coord              |
|--------|--------------------|--------------------|
| TOP    | (x, y) → (x+3, y) | row = y            |
| RIGHT  | (x+3, y) → (x+3, y+3) | col = x+3      |
| BOTTOM | (x, y+3) → (x+3, y+3) | row = y+3      |
| LEFT   | (x, y) → (x, y+3) | col = x            |

### Equilateral/Isosceles Triangle — 1 flat edge per rotation
| Rotation | Flat Edge | Coord   |
|----------|-----------|---------|
| R0       | BOTTOM    | row=y+3 |
| R90      | LEFT      | col=x   |
| R180     | TOP       | row=y   |
| R270     | RIGHT     | col=x+3 |

### Right Triangle — 2 flat edges per rotation
| Rotation | Flat Edges       | Coords           |
|----------|------------------|------------------|
| R0       | TOP + RIGHT      | row=y, col=x+3   |
| R90      | RIGHT + BOTTOM   | col=x+3, row=y+3 |
| R180     | BOTTOM + LEFT    | row=y+3, col=x   |
| R270     | LEFT + TOP       | col=x, row=y     |

---

## Snap Rules

### Rule 1: No Overlap
Two tiles overlap if their bounding boxes intersect AND they are not the same tile.
Bounding box collision check: NOT (A.right <= B.left OR A.left >= B.right OR A.bottom <= B.top OR A.top >= B.bottom)

### Rule 2: At Least One Shared Edge
Tile A at (ax, ay) snaps to Tile B at (bx, by) when:
- A has a flat edge at coordinate C1
- B has a flat edge at coordinate C2
- C1 == C2 (same row or column)
- The edge spans overlap

Snap alignment cases (for square-to-square):
```
A.BOTTOM (row=ay+3) = B.TOP (row=by)     → by = ay+3  (B below A)
A.TOP    (row=ay)   = B.BOTTOM (row=by+3) → by = ay-3  (B above A)
A.RIGHT  (col=ax+3) = B.LEFT  (col=bx)    → bx = ax+3  (B right of A)
A.LEFT   (col=ax)   = B.RIGHT  (col=bx+3) → bx = ax-3  (B left of A)
```

### Rule 3: Build From Center
First tile placed at center of composition area.
Center = (12, 12) on 30x30 grid (leaves room in all directions).

### Rule 4: Center Piece Is Square
First tile is always SOLID_SQUARE at center position.

### Rule 5: Track Exterior Cartesian Positions
Maintain a set of all exposed flat edge positions after each placement.
A flat edge is "exposed" if no other tile's flat edge shares that coordinate.

### Rule 6: Relative Positioning
New tile N is positioned relative to the exposed edge of a previously placed tile.
```
newTile.x = previousTile.x + offset_x
newTile.y = previousTile.y + offset_y
```
Where offset depends on which edge of previousTile we snap to:
- Snap to TOP:    offset = (0, -3)
- Snap to RIGHT:  offset = (+3, 0)
- Snap to BOTTOM: offset = (0, +3)
- Snap to LEFT:   offset = (-3, 0)

---

## Calculation Algorithm

```
function buildComposition(blueprint):
  placed = []
  exposedEdges = []

  // Rule 3 & 4: Start with center square
  center = Square at (12, 12)
  placed.add(center)
  exposedEdges = getFlatEdges(center)  // [TOP, RIGHT, BOTTOM, LEFT]

  // For each subsequent tile in blueprint:
  for tile in blueprint.tiles:
    // Rule 6: Pick which exposed edge to snap to
    targetEdge = selectTargetEdge(tile, exposedEdges)

    // Rule 6: Calculate position relative to target
    tile.x = targetEdge.owner.x + targetEdge.offsetX
    tile.y = targetEdge.owner.y + targetEdge.offsetY

    // Rule 1: Verify no overlap
    if overlapsAny(tile, placed):
      REJECT — try different edge or rotation

    // Rule 2: Verify snap (at least one shared edge)
    if not hasSharedEdge(tile, placed):
      REJECT — must snap to existing tile

    // Place tile
    placed.add(tile)
    // Rule 5: Update exposed edges
    exposedEdges.remove(targetEdge)  // covered by new tile
    exposedEdges.addAll(getFlatEdges(tile))  // add new exposed edges
    removeNewlyCovered(exposedEdges, tile)
```

---

## ASCII Art Object Construction Test

### Test 1: Cross Shape (5 squares)

Step 1: Center square at (0,0) in local coords
```
###
###
###
```
Exposed edges: TOP(y=0), RIGHT(x=3), BOTTOM(y=3), LEFT(x=0)

Step 2: Square snapped to BOTTOM edge → offset (0,+3)
```
###
###
###
###
###
###
```
Wait — this gives a 3x6 rectangle. Let me show it properly with positioning:

```
  ###          <- Square at (0,0)
  ###          Exposed: TOP, LEFT, RIGHT
  ###
###.###        <- Square at (-3,3) snapped LEFT, Square at (0,0) center, Square at (3,3) snapped RIGHT
###.###        Square at (0,3) snapped BOTTOM of center
###.###
  ###
  ###          <- Square at (0,6) snapped BOTTOM of (0,3)
  ###
```

Hmm, ASCII art positioning is tricky. Let me use a coordinate grid instead:

### Test 2: Simple House (Square + Triangle Roof)

Using local coordinates starting at (0,0):

**Step 1: Center piece — Square at (0,3)**
```
Positions (0,3) to (3,6):
Row 3: ###
Row 4: ###
Row 5: ###
```
Exposed edges: TOP(y=3), RIGHT(x=3), BOTTOM(y=6), LEFT(x=0)

**Step 2: Triangle R0 (apex UP) snapped to TOP edge → at (0,0)**
- Target: TOP of square at y=3
- New tile: needs BOTTOM flat edge at y=3
- Triangle R0 has BOTTOM flat edge at row = tile.y + 3
- So tile.y + 3 = 3 → tile.y = 0
- Position: (0, 0) to (3, 3)

```
Row 0: .#.      <- Triangle apex
Row 1: ###
Row 2: ###
Row 3: ###      <- Square top edge = Triangle bottom edge ✓
Row 4: ###
Row 5: ###
```

Result: House shape with triangular roof! Triangle BOTTOM edge (y=3) snaps to Square TOP edge (y=3).

**Verification:**
- Rule 1 ✓: No overlap (triangle occupies (0,0)-(3,3), square occupies (0,3)-(3,6))
- Rule 2 ✓: Shared edge at y=3
- Rule 3 ✓: Started from center square
- Rule 4 ✓: Center is square
- Rule 5 ✓: Exposed edges tracked
- Rule 6 ✓: Triangle positioned relative to square's top edge

### Test 3: Dog Head (Square + 2 Triangles for Ears)

**Step 1: Center square at (3,3)**
```
###    <- (3,3) to (6,6)
###
###
```

**Step 2: Right Triangle R270 (flat LEFT+TOP) snapped LEFT of square → at (0,3)**
- Target: LEFT of square at x=3
- R270 has LEFT flat edge at col = tile.x
- tile.x = 3 → but we need LEFT edge at x=3, so tile.x = 0 (tile spans 0 to 3, LEFT edge at x=0)

Wait, for R270, the LEFT flat edge is at col = tile.x. For it to snap to square's LEFT edge at x=3:
- New tile's RIGHT edge would need to be at x=3
- R270's flat edges are LEFT(col=x) and TOP(row=y)
- Neither is RIGHT!

Let me reconsider. For a left ear, I need a triangle with its RIGHT edge flat, snapping to the square's LEFT edge.

Equilateral Triangle R270 has flat edge RIGHT at col = x+3.
Square LEFT edge is at x=3.
So: x+3 = 3 → x = 0. Triangle at (0, 3).

```
##.    <- Triangle R270 (apex left, flat RIGHT edge)
#..    <- RIGHT edge at col 3 = square LEFT edge ✓
...    <- Apex visible at left

###    <- Square at (3,3)
###
###

...    <- Triangle R90 (apex right, flat LEFT edge)
..#    <- LEFT edge at col 3+3=6... wait
.##
```

Hmm, let me redo this more carefully.

Square at (3,3): occupies (3,3) to (6,6)
- LEFT edge at x=3
- RIGHT edge at x=6

Left ear: Equilateral Triangle R270 at (0,3)
- Occupies (0,3) to (3,3)
- R270 has flat RIGHT edge at col = 0+3 = 3
- Square has flat LEFT edge at col = 3
- They snap! ✓

Right ear: Equilateral Triangle R90 at (6,3)
- Occupies (6,3) to (9,3)
- R90 has flat LEFT edge at col = 6
- Square has flat RIGHT edge at col = 6
- They snap! ✓

ASCII grid (9 columns, 6 rows):
```
Col:  0123456789
R0:   .#.....#..    <- Left ear apex, Right ear apex
R1:   ###...###.    <- Left ear body, Right ear body
R2:   ###...###.    <- Left ear flat-RIGHT, Right ear flat-LEFT
R3:   ...#####..    <- Square top row (both ears connect)
R4:   ...#####..    <- Square middle
R5:   ...#####..    <- Square bottom
```

Wait, the ears at (0,3) occupy rows 3-6, not 0-3. Let me recalculate.

Left ear (T R270) at (0,3): occupies (0,3) to (3,6)
Right ear (T R90) at (6,3): occupies (6,3) to (9,6)
Square at (3,3): occupies (3,3) to (6,6)

All three occupy rows 3-6. The ears are at the same vertical position as the square.

```
Col:  012345678
R3:   #..###..#    <- T-R270 row3, Sq row3, T-R90 row3
R4:   ##.###.##    <- T-R270 row4, Sq row4, T-R90 row4
R5:   ##.###.##    <- T-R270 row5, Sq row5, T-R90 row5
R6:   ##.###.##    <- T-R270 row6, Sq row6, T-R90 row6
```

Hmm, the R270 triangle should have apex pointing LEFT. Let me reconsider the shapes:

T-R270 at (0,3):
- Apex at (0, 4.5) — left center
- Base at right side: (3,3) to (3,6)
- Shape: fills from right edge toward left apex

```
R3: ##.
R4: #..
R5: #..
R6: ##.
```

Wait no. For R270 (apex LEFT), the vertices are:
- Apex: (x, y+1.5) = (0, 4.5)
- Base top: (x+3, y) = (3, 3)
- Base bottom: (x+3, y+3) = (3, 6)

So the flat RIGHT edge goes from (3,3) to (3,6) — this is correct for snapping.

The shape fills the area between the left apex and the right edge:
```
R3: ##.    <- wide at top (near base top)
R4: #..    <- narrow toward apex
R5: #..
R6: ##.    <- wide at bottom (near base bottom)
```

And T-R90 at (6,3):
- Apex at (9, 4.5) — right center
- Base at left: (6,3) to (6,6)
- Flat LEFT edge at x=6

```
R3: .##
R4: ..#
R5: ..#
R6: .##
```

Combined with square:
```
Col:  012345678
R3:   ##.###.##    <- ear top, sq top, ear top
R4:   #..###..#    <- ear mid, sq mid, ear mid
R5:   #..###..#    <- ear mid, sq mid, ear mid
R6:   ##.###.##    <- ear bot, sq bot, ear bot
```

This creates a head shape with ears sticking out to the sides! But the ears are triangles with narrow points facing outward.

This looks more like a dog head with pointed ears. For floppy ears (hanging down), we'd want different placement.

---

## Summary of Flat Edge Snap Compatibility

| Tile A Edge | Tile B must have | B position offset |
|-------------|-----------------|-------------------|
| TOP (row=y) | BOTTOM flat edge | (Bx, Ay-3)       |
| RIGHT (col=x+3) | LEFT flat edge | (Ax+3, By)    |
| BOTTOM (row=y+3) | TOP flat edge | (Bx, Ay+3)     |
| LEFT (col=x) | RIGHT flat edge | (Ax-3, By)      |

This means:
- Square → Square: Always works (4 flat edges each)
- Square → Triangle: Only works at the triangle's flat edge
- Triangle → Triangle: Only works when both have matching flat edges facing each other

## Next Step
Test the algorithm by constructing all 6 objects (Lion, Crocodile, Dog, Car, Tram, Flower) as ASCII art compositions, following Rules 1-6 strictly.
