# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Architect AI** is an intelligent design tool that transforms natural language prompts into structured, physical-logic-based compositions using a curated library of five core Magnatile components. The AI backend acts as a "Digital Architect," calculating spatial coordinates and rotation required to assemble complex objects.

## Core Component Set

The AI backend is strictly limited to these five tile types:

| Tile ID | Description | Dimensions |
|---------|-------------|------------|
| `solid_square` | Base structural unit | 3x3 units |
| `window_square` | Decorative/translucent variant | 3x3 units |
| `equilateral_triangle` | 60° standard for patterns/domes | base: 3, height: ~2.6 |
| `right_triangle` | 90° component for perpendicular joins | base: 3, height: 3 |
| `isosceles_triangle` | "Spire unit" for vertical emphasis | base: 3, height: 3 |

## AI Output Format

The LLM should output assemblies as JSON:

```json
{
  "object": "Object Name",
  "components": [
    {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 0, "color": "#A04523"},
    {"tile_id": "right_triangle", "x": 3, "y": 0, "rotation": 90, "color": "#F18D58"}
  ]
}
```

## Key System Constraints

1. **Snap-to-Grid Engine**: Components must align on a grid system where 1 unit = 10dp (configurable)
2. **Magnetic Snapping**: Tile edges must snap together based on geometric properties
3. **Constraint Validation**: Middleware validates AI output to prevent physically impossible overlaps
4. **Coordinate System**: All positions use x,y coordinates from a defined origin (0,0)

## Design System

- **Background**: #FFF5F2 (Warm off-white)
- **Primary Accent**: #E0542E (Vibrant orange-red)
- **Headers**: #3D1410 (Dark brown)
- **Typography**: Brand Serif for titles, UI Sans for body

## Architecture

### Frontend (Planned: Android / Kotlin)
- **View Layer**: Jetpack Compose
- **Library**: `LazyColumn` for component browser
- **Canvas**: `Box` with `GraphicLayer` for build area
- **SVG Rendering**: `AndroidSVG` or `Coil-SVG`
- **Animations**: `UpdateTransition` for snapping effects

### Backend (Planned)
- **AI Integration**: System prompts with tile dimension metadata
- **Constraint Engine**: Middleware validating physical possibility of assemblies
- **Chat Interface**: Natural language to JSON transformation

## Navigation Structure

Four main destinations:
1. **Chat**: AI construction interface
2. **Build**: Active canvas for viewing 2D/3D assemblies
3. **Library**: Component browser
4. **Gallery**: Saved user blueprints
