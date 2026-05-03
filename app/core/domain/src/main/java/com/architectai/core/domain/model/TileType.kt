package com.architectai.core.domain.model

enum class TileType(
    val id: String,
    val displayName: String,
    val description: String,
    val widthUnits: Int,
    val heightUnits: Int
) {
    SOLID_SQUARE(
        id = "solid_square",
        displayName = "Solid Square",
        description = "Base structural unit (3x3 units)",
        widthUnits = 3,
        heightUnits = 3
    ),
    WINDOW_SQUARE(
        id = "window_square",
        displayName = "Window Square",
        description = "Decorative/translucent variant (3x3 units)",
        widthUnits = 3,
        heightUnits = 3
    ),
    EQUILATERAL_TRIANGLE(
        id = "equilateral_triangle",
        displayName = "Equilateral Triangle",
        description = "60° standard for patterns and domes",
        widthUnits = 3,
        heightUnits = 3
    ),
    RIGHT_TRIANGLE(
        id = "right_triangle",
        displayName = "Right Triangle",
        description = "90° component for perpendicular joins and stairs",
        widthUnits = 3,
        heightUnits = 3
    ),
    ISOSCELES_TRIANGLE(
        id = "isosceles_triangle",
        displayName = "Isosceles Triangle",
        description = "Spire unit for vertical emphasis and rooftops",
        widthUnits = 3,
        heightUnits = 3
    )
}

enum class TileColor(
    val hex: String,
    val displayName: String
) {
    RED("#A04523", "Red"),
    ORANGE("#F18D58", "Orange"),
    YELLOW("#F5C542", "Yellow"),
    GREEN("#4CAF50", "Green"),
    BLUE("#2196F3", "Blue"),
    PURPLE("#9C27B0", "Purple"),
    BROWN("#8D6E63", "Brown"),
    BLACK("#000000", "Black"),
    WHITE("#FFFFFF", "White"),
    TRANSLUCENT("#FFFFFF80", "Translucent")
}

enum class Rotation(val degrees: Int) {
    R0(0),
    R90(90),
    R180(180),
    R270(270)
}
