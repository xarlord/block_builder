package com.architectai.core.domain.model

/**
 * Mock compositions for testing without AI integration.
 * These represent simple objects created from the 5 tile types.
 *
 * NOTE: createdAt/updatedAt are set to 0 as sentinel values.
 * Real compositions will use actual timestamps.
 */
object MockCompositions {

    val LION = Composition(
        id = "mock_lion_001",
        name = "Lion",
        tiles = listOf(
            // Mane - triangles radiating outward from head
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 9, y = 2, Rotation.R180, TileColor.ORANGE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 9, y = 5, Rotation.R180, TileColor.ORANGE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 21, y = 2, Rotation.R0, TileColor.ORANGE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 21, y = 5, Rotation.R0, TileColor.ORANGE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 12, y = 0, Rotation.R180, TileColor.ORANGE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 15, y = 0, Rotation.R180, TileColor.ORANGE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 12, y = 8, Rotation.R0, TileColor.ORANGE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 15, y = 8, Rotation.R0, TileColor.ORANGE),
            // Head outline (2x2)
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 2, Rotation.R0, TileColor.ORANGE),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 2, Rotation.R0, TileColor.ORANGE),
            // Body outline (2x2)
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 11, Rotation.R0, TileColor.YELLOW),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 11, Rotation.R0, TileColor.YELLOW),
            // Legs (4 corners)
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 14, Rotation.R0, TileColor.YELLOW),
            TilePlacement(TileType.SOLID_SQUARE, x = 18, y = 14, Rotation.R0, TileColor.YELLOW),
            // Tail - curves upward with right triangle
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 21, y = 11, Rotation.R0, TileColor.ORANGE),
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 21, y = 8, Rotation.R90, TileColor.ORANGE),
            // Face details
            TilePlacement(TileType.WINDOW_SQUARE, x = 12, y = 3, Rotation.R0, TileColor.WHITE),
            TilePlacement(TileType.WINDOW_SQUARE, x = 15, y = 3, Rotation.R0, TileColor.WHITE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 15, y = 5, Rotation.R180, TileColor.ORANGE)
        ),
        createdAt = 0L,
        updatedAt = 0L,
        source = Composition.Source.MANUAL
    )

    val CROCODILE = Composition(
        id = "mock_crocodile_001",
        name = "Crocodile",
        tiles = listOf(
            // Long snout with tapered ends
            TilePlacement(TileType.SOLID_SQUARE, x = 18, y = 5, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 21, y = 5, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 24, y = 5, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 27, y = 5, Rotation.R270, TileColor.GREEN),
            // Head behind snout
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 8, Rotation.R0, TileColor.GREEN),
            // Long body (4x2)
            TilePlacement(TileType.SOLID_SQUARE, x = 6, y = 11, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 11, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 11, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 6, y = 14, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 14, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 14, Rotation.R0, TileColor.GREEN),
            // Legs
            TilePlacement(TileType.SOLID_SQUARE, x = 6, y = 17, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 17, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 6, y = 20, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 20, Rotation.R0, TileColor.GREEN),
            // Tapered tail
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 3, y = 11, Rotation.R180, TileColor.GREEN),
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 0, y = 14, Rotation.R180, TileColor.GREEN),
            // Eye
            TilePlacement(TileType.WINDOW_SQUARE, x = 15, y = 8, Rotation.R0, TileColor.WHITE),
            // Teeth
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 21, y = 8, Rotation.R0, TileColor.WHITE),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 27, y = 8, Rotation.R0, TileColor.WHITE),
            // Belly
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 17, Rotation.R0, TileColor.YELLOW)
        ),
        createdAt = 0L,
        updatedAt = 0L,
        source = Composition.Source.MANUAL
    )

    val DOG = Composition(
        id = "mock_dog_001",
        name = "Dog",
        tiles = listOf(
            // Floppy ears hanging down
            TilePlacement(TileType.ISOSCELES_TRIANGLE, x = 6, y = 5, Rotation.R180, TileColor.BROWN),
            TilePlacement(TileType.ISOSCELES_TRIANGLE, x = 21, y = 5, Rotation.R180, TileColor.BROWN),
            // Head (2x2)
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 5, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 5, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 8, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 8, Rotation.R0, TileColor.BROWN),
            // Body (2x2)
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 12, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 12, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 15, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 15, Rotation.R0, TileColor.BROWN),
            // Legs
            TilePlacement(TileType.SOLID_SQUARE, x = 6, y = 18, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 18, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 6, y = 21, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 21, Rotation.R0, TileColor.BROWN),
            // Tail
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 18, y = 12, Rotation.R0, TileColor.BROWN),
            // Face details
            TilePlacement(TileType.SOLID_SQUARE, x = 10, y = 6, Rotation.R0, TileColor.YELLOW),
            TilePlacement(TileType.WINDOW_SQUARE, x = 10, y = 6, Rotation.R0, TileColor.BLACK),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 11, y = 8, Rotation.R180, TileColor.BLACK),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 11, y = 9, Rotation.R0, TileColor.RED)
        ),
        createdAt = 0L,
        updatedAt = 0L,
        source = Composition.Source.MANUAL
    )

    val CAR = Composition(
        id = "mock_car_001",
        name = "Car",
        tiles = listOf(
            // Body (2x3 red rectangle)
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 8, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 8, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 11, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 11, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 14, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 14, Rotation.R0, TileColor.RED),
            // Roof (2x1 blue)
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 5, Rotation.R0, TileColor.BLUE),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 5, Rotation.R0, TileColor.BLUE),
            // Wheels
            TilePlacement(TileType.SOLID_SQUARE, x = 6, y = 14, Rotation.R0, TileColor.BLACK),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 14, Rotation.R0, TileColor.BLACK),
            // Windows
            TilePlacement(TileType.WINDOW_SQUARE, x = 9, y = 6, Rotation.R0, TileColor.WHITE),
            TilePlacement(TileType.WINDOW_SQUARE, x = 12, y = 6, Rotation.R0, TileColor.WHITE),
            // Headlights & taillights
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 15, y = 11, Rotation.R0, TileColor.YELLOW),
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 6, y = 11, Rotation.R180, TileColor.RED)
        ),
        createdAt = 0L,
        updatedAt = 0L,
        source = Composition.Source.MANUAL
    )

    val TRAM = Composition(
        id = "mock_tram_001",
        name = "Tram",
        tiles = listOf(
            // Long body (3x2 blue horizontal)
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 8, Rotation.R0, TileColor.BLUE),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 8, Rotation.R0, TileColor.BLUE),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 8, Rotation.R0, TileColor.BLUE),
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 11, Rotation.R0, TileColor.BLUE),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 11, Rotation.R0, TileColor.BLUE),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 11, Rotation.R0, TileColor.BLUE),
            // Wheels
            TilePlacement(TileType.SOLID_SQUARE, x = 9, y = 14, Rotation.R0, TileColor.BLACK),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 14, Rotation.R0, TileColor.BLACK),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 14, Rotation.R0, TileColor.BLACK),
            // Pantograph
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 9, y = 5, Rotation.R90, TileColor.BLACK),
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 12, y = 5, Rotation.R270, TileColor.BLACK),
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 15, y = 5, Rotation.R90, TileColor.BLACK),
            // Windows
            TilePlacement(TileType.WINDOW_SQUARE, x = 9, y = 9, Rotation.R0, TileColor.WHITE),
            TilePlacement(TileType.WINDOW_SQUARE, x = 12, y = 9, Rotation.R0, TileColor.WHITE),
            TilePlacement(TileType.WINDOW_SQUARE, x = 15, y = 9, Rotation.R0, TileColor.WHITE),
            // Front lights
            TilePlacement(TileType.RIGHT_TRIANGLE, x = 18, y = 11, Rotation.R0, TileColor.YELLOW)
        ),
        createdAt = 0L,
        updatedAt = 0L,
        source = Composition.Source.MANUAL
    )

    val FLOWER = Composition(
        id = "mock_flower_001",
        name = "Flower",
        tiles = listOf(
            // Petals radiating outward
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 12, y = 7, Rotation.R180, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 15, y = 7, Rotation.R180, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 12, y = 16, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 15, y = 16, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 9, y = 10, Rotation.R270, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 9, y = 13, Rotation.R270, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 18, y = 10, Rotation.R90, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 18, y = 13, Rotation.R90, TileColor.RED),
            // Stem
            TilePlacement(TileType.SOLID_SQUARE, x = 13, y = 16, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 13, y = 19, Rotation.R0, TileColor.GREEN),
            TilePlacement(TileType.SOLID_SQUARE, x = 13, y = 22, Rotation.R0, TileColor.GREEN),
            // Pot
            TilePlacement(TileType.SOLID_SQUARE, x = 10, y = 25, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 13, y = 25, Rotation.R0, TileColor.BROWN),
            TilePlacement(TileType.SOLID_SQUARE, x = 16, y = 25, Rotation.R0, TileColor.BROWN),
            // Flower center
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 10, Rotation.R0, TileColor.YELLOW),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 10, Rotation.R0, TileColor.YELLOW),
            TilePlacement(TileType.SOLID_SQUARE, x = 12, y = 13, Rotation.R0, TileColor.YELLOW),
            TilePlacement(TileType.SOLID_SQUARE, x = 15, y = 13, Rotation.R0, TileColor.YELLOW),
            // Leaves
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 10, y = 19, Rotation.R270, TileColor.GREEN),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, x = 16, y = 22, Rotation.R90, TileColor.GREEN)
        ),
        createdAt = 0L,
        updatedAt = 0L,
        source = Composition.Source.MANUAL
    )

    fun getAllMockCompositions(): List<Composition> =
        listOf(LION, CROCODILE, DOG, CAR, TRAM, FLOWER)

    fun getCompositionById(id: String): Composition? =
        getAllMockCompositions().find { it.id == id }

    fun getCompositionByName(name: String): Composition? =
        getAllMockCompositions().find { it.name.equals(name, ignoreCase = true) }
}
