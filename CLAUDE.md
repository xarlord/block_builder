# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Architect AI** is an Android app that transforms natural language prompts into
structured 2D tile compositions using 5 Magnatile components. The AI backend acts
as a "Digital Architect," calculating spatial coordinates and rotation to assemble
complex objects from these 5 tiles.

## Build & Run Commands

All commands should be run from the `app/` directory:

```bash
cd app/
```

- Build: `./gradlew assembleDebug`
- Run on device/emulator: `android run` (from project root) or `adb install app/build/outputs/apk/debug/app-debug.apk`
- Run tests: `./gradlew test`
- Run instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`

## Android CLI Commands

The `android` CLI is installed at `C:/ProgramData/AndroidCLI/android.exe`.

- View layout tree: `android layout`
- Capture screenshot: `android screen capture -o screen.png`
- List emulators: `android emulator list`
- Start emulator: `android emulator start --profile=medium_phone`
- Search Android docs: `android docs search "<query>"`

## Architecture

### Module Layout

```
app/                        # Application shell, MainActivity, DI
feature/chat/               # Chat screen + ChatViewModel
feature/build/              # Build canvas + BuildViewModel + TileRenderer
feature/library/            # Component library + LibraryViewModel
feature/gallery/            # Saved compositions + GalleryViewModel
core/domain/                # Tile models, ConstraintEngine, UseCases
core/data/                  # Repository impls, LLM client, Room DAOs
core/designsystem/          # Theme, typography, color tokens, components
core/svg/                   # SVG loading utilities
```

### Navigation

Uses **Navigation 3** (`navigation3-runtime`, `navigation3-ui`).
Top-level destinations: ChatRoute, BuildRoute, LibraryRoute, GalleryRoute.
Each tab maintains its own back stack via TopLevelBackStack.

### State Management

- Each screen has a ViewModel exposing `StateFlow<UiState>`.
- UI events are dispatched as sealed class `UiEvent` via ViewModel methods.
- Cross-screen shared state uses `SharedCompositionRepository` (a StateFlow-based store).
- Room for persistence. SavedStateHandle for process death recovery.

## Core Component Set

| Tile ID | Display Name | Grid Dimensions |
|---------|-------------|-----------------|
| `solid_square` | Solid Square | 3x3 |
| `window_square` | Window Square | 3x3 |
| `equilateral_triangle` | Equilateral Triangle | base: 3, height: 2.6 |
| `right_triangle` | Right Triangle | base: 3, height: 3 |
| `isosceles_triangle` | Isosceles Triangle | base: 3, height: 3 |

## Coordinate System

- 1 grid unit = 10dp at zoom level 1.0
- Origin (0,0) at top-left
- Default canvas: 30x30 grid units
- Rotation: clockwise around tile center; valid values 0, 90, 180, 270

## AI Output Format

```json
{
  "object": "Object Name",
  "components": [
    {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 0, "color": "#A04523"},
    {"tile_id": "right_triangle", "x": 3, "y": 0, "rotation": 90, "color": "#F18D58"}
  ]
}
```

## Design Tokens

| Token | Value | Usage |
|-------|-------|-------|
| Background | `#FFF5F2` | Screen backgrounds |
| Accent | `#E0542E` | Buttons, highlights, active states |
| Header | `#3D1410` | Titles, navigation labels |
| Button shape | Pill (RoundedCornerShape(50%)) | All primary buttons |
| Header font | Brand Serif | All headings |
| Body font | UI Sans | Body text, descriptions |

## Key Technical Decisions

1. **SVG vs Canvas**: SVG (via Coil-SVG) for Library card previews. Compose `DrawScope` (Canvas API) for Build canvas rendering. This avoids SVG parsing overhead during animation.
2. **Constraint Engine**: Pure Kotlin functions in `core:domain` with no Android dependencies. Uses Separating Axis Theorem for rotated triangle overlap detection.
3. **Edge-to-edge**: All activities call `enableEdgeToEdge()`. Chat screen uses IME padding via `Modifier.fitInside(WindowInsetsRulers.Ime.current)`.
4. **Animations**: `UpdateTransition` for tile snap states (Idle/Dragging/Snapping/Settled). Staggered `fadeIn + scaleIn` for AI-generated composition placement.
5. **Haptics**: `view.performHapticFeedback()` for tile snap, rejection, and button presses.

## Constraint Validation Rules

1. Tiles must be within 30x30 grid bounds
2. No two tiles may overlap (polygon intersection via SAT)
3. Maximum 200 tiles per composition
4. Only valid TileType IDs, rotations (0/90/180/270), and recognized colors

## File Naming Conventions

- Screens: `<Feature>Screen.kt` (e.g., `ChatScreen.kt`)
- ViewModels: `<Feature>ViewModel.kt`
- Use cases: `<Verb><Noun>UseCase.kt`
- Domain models: Plain names (e.g., `TilePlacement.kt`, `Composition.kt`)
- DAOs: `<Entity>Dao.kt`
- Repository impls: `<Entity>RepositoryImpl.kt`

## Dependencies (Key)

- Jetpack Compose BOM (latest stable)
- Navigation 3 (alpha)
- Material 3
- Coil 3 + coil-svg
- Room + KSP
- OkHttp + Moshi
- Hilt (DI)
- AndroidX Lifecycle (ViewModel, StateFlow)

## Project Structure

The Android project is in the `app/` subdirectory. Root contains:
- `initial_specs.md` — Original product specification
- `CLAUDE.md` — This file
- `.gitignore` — Root gitignore (app/ has its own from template)

## Emulator

A medium_phone AVD has been created for testing. Start with:
```bash
android emulator start --profile=medium_phone
```
