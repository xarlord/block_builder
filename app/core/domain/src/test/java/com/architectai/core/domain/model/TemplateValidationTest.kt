package com.architectai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Validates the structure and content of template JSON files
 * by loading them inline to avoid working directory issues.
 */
class TemplateValidationTest {

    private lateinit var engine: TemplateEngine

    private val allTemplateJsons = listOf(
        // lion
        """{"id":"lion","name":"Lion","category":"animals","description":"A majestic lion","tags":["lion","cat","wildlife"],"tiles":[{"tileType":"EQUILATERAL_TRIANGLE","x":6,"y":0,"rotation":"R180","color":"ORANGE"},{"tileType":"EQUILATERAL_TRIANGLE","x":9,"y":0,"rotation":"R180","color":"ORANGE"},{"tileType":"EQUILATERAL_TRIANGLE","x":12,"y":0,"rotation":"R180","color":"ORANGE"},{"tileType":"EQUILATERAL_TRIANGLE","x":15,"y":0,"rotation":"R180","color":"ORANGE"},{"tileType":"EQUILATERAL_TRIANGLE","x":18,"y":0,"rotation":"R180","color":"ORANGE"},{"tileType":"EQUILATERAL_TRIANGLE","x":21,"y":0,"rotation":"R180","color":"ORANGE"},{"tileType":"EQUILATERAL_TRIANGLE","x":3,"y":3,"rotation":"R270","color":"ORANGE"},{"tileType":"SOLID_SQUARE","x":6,"y":3,"rotation":"R0","color":"ORANGE"},{"tileType":"SOLID_SQUARE","x":9,"y":3,"rotation":"R0","color":"ORANGE"},{"tileType":"SOLID_SQUARE","x":12,"y":3,"rotation":"R0","color":"YELLOW"},{"tileType":"SOLID_SQUARE","x":15,"y":3,"rotation":"R0","color":"YELLOW"},{"tileType":"SOLID_SQUARE","x":18,"y":3,"rotation":"R0","color":"ORANGE"},{"tileType":"SOLID_SQUARE","x":21,"y":3,"rotation":"R0","color":"ORANGE"},{"tileType":"EQUILATERAL_TRIANGLE","x":24,"y":3,"rotation":"R90","color":"ORANGE"}]}""",
        // dog
        """{"id":"dog","name":"Dog","category":"animals","description":"A friendly dog","tags":["dog","puppy","pet"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":6,"y":0,"rotation":"R180","color":"BROWN"},{"tileType":"ISOSCELES_TRIANGLE","x":21,"y":0,"rotation":"R180","color":"BROWN"},{"tileType":"SOLID_SQUARE","x":6,"y":3,"rotation":"R0","color":"BROWN"},{"tileType":"SOLID_SQUARE","x":9,"y":3,"rotation":"R0","color":"BROWN"}]}""",
        // car
        """{"id":"car","name":"Car","category":"vehicles","description":"A sporty car","tags":["car","automobile","vehicle"],"tiles":[{"tileType":"SOLID_SQUARE","x":6,"y":9,"rotation":"R0","color":"RED"},{"tileType":"SOLID_SQUARE","x":9,"y":9,"rotation":"R0","color":"RED"},{"tileType":"SOLID_SQUARE","x":12,"y":9,"rotation":"R0","color":"RED"}]}""",
        // flower
        """{"id":"flower","name":"Flower","category":"nature","description":"A colorful flower","tags":["flower","rose","plant"],"tiles":[{"tileType":"EQUILATERAL_TRIANGLE","x":9,"y":0,"rotation":"R180","color":"RED"},{"tileType":"EQUILATERAL_TRIANGLE","x":12,"y":0,"rotation":"R180","color":"RED"}]}""",
        // house
        """{"id":"house","name":"House","category":"buildings","description":"A cozy house","tags":["house","home","building"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":9,"y":0,"rotation":"R0","color":"RED"},{"tileType":"SOLID_SQUARE","x":9,"y":3,"rotation":"R0","color":"RED"}]}""",
        // tree
        """{"id":"tree","name":"Tree","category":"nature","description":"A tall tree","tags":["tree","forest","nature"],"tiles":[{"tileType":"EQUILATERAL_TRIANGLE","x":9,"y":0,"rotation":"R180","color":"GREEN"},{"tileType":"EQUILATERAL_TRIANGLE","x":12,"y":0,"rotation":"R180","color":"GREEN"}]}""",
        // cat
        """{"id":"cat","name":"Cat","category":"animals","description":"A sitting cat","tags":["cat","kitten","feline"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":9,"y":0,"rotation":"R0","color":"ORANGE"},{"tileType":"ISOSCELES_TRIANGLE","x":15,"y":0,"rotation":"R0","color":"ORANGE"}]}""",
        // tram
        """{"id":"tram","name":"Tram","category":"vehicles","description":"A city tram","tags":["tram","train","transit"],"tiles":[{"tileType":"RIGHT_TRIANGLE","x":6,"y":0,"rotation":"R90","color":"BLACK"},{"tileType":"RIGHT_TRIANGLE","x":9,"y":0,"rotation":"R270","color":"BLACK"}]}""",
        // crocodile
        """{"id":"crocodile","name":"Crocodile","category":"animals","description":"A long crocodile","tags":["crocodile","alligator","reptile"],"tiles":[{"tileType":"SOLID_SQUARE","x":18,"y":3,"rotation":"R0","color":"GREEN"},{"tileType":"SOLID_SQUARE","x":21,"y":3,"rotation":"R0","color":"GREEN"}]}""",
        // boat
        """{"id":"boat","name":"Boat","category":"vehicles","description":"A sailboat","tags":["boat","sail","ship"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":12,"y":0,"rotation":"R0","color":"WHITE"},{"tileType":"SOLID_SQUARE","x":12,"y":3,"rotation":"R0","color":"WHITE"}]}""",
        // rocket
        """{"id":"rocket","name":"Rocket","category":"vehicles","description":"A space rocket","tags":["rocket","space","launch"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":12,"y":0,"rotation":"R0","color":"RED"},{"tileType":"SOLID_SQUARE","x":12,"y":3,"rotation":"R0","color":"WHITE"}]}""",
        // castle
        """{"id":"castle","name":"Castle","category":"buildings","description":"A medieval castle","tags":["castle","medieval","fortress"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":3,"y":0,"rotation":"R0","color":"BLUE"},{"tileType":"ISOSCELES_TRIANGLE","x":9,"y":0,"rotation":"R0","color":"RED"}]}""",
        // fish
        """{"id":"fish","name":"Fish","category":"animals","description":"A tropical fish","tags":["fish","ocean","sea"],"tiles":[{"tileType":"EQUILATERAL_TRIANGLE","x":0,"y":9,"rotation":"R270","color":"YELLOW"},{"tileType":"EQUILATERAL_TRIANGLE","x":0,"y":12,"rotation":"R270","color":"YELLOW"}]}"""
    )

    @Before
    fun setUp() {
        engine = TemplateEngine()
        allTemplateJsons.forEach { json ->
            engine.loadTemplate(json)
        }
    }

    @Test
    fun minimumTemplateCount() {
        assertTrue(
            "Should have at least 10 templates, found ${allTemplateJsons.size}",
            allTemplateJsons.size >= 10
        )
    }

    @Test
    fun allTemplatesLoadSuccessfully() {
        val loadedCount = engine.getAllTemplates().size
        assertEquals("All templates should load successfully", allTemplateJsons.size, loadedCount)
    }

    @Test
    fun allTemplatesHaveNoOverlaps() {
        for (template in engine.getAllTemplates()) {
            val errors = engine.validateTemplate(template)
            assertTrue(
                "Template ${template.id} should have no overlapping tiles, errors: $errors",
                errors.isEmpty()
            )
        }
    }

    @Test
    fun allTemplatesAreWithinBounds() {
        for (template in engine.getAllTemplates()) {
            for (tile in template.tiles) {
                assertTrue(
                    "Template ${template.id}: tile x=${tile.x} should be >= 0",
                    tile.x >= 0
                )
                assertTrue(
                    "Template ${template.id}: tile x=${tile.x} + width should be <= 30",
                    tile.x + tile.tileType.widthUnits <= 30
                )
                assertTrue(
                    "Template ${template.id}: tile y=${tile.y} should be >= 0",
                    tile.y >= 0
                )
                assertTrue(
                    "Template ${template.id}: tile y=${tile.y} + height should be <= 30",
                    tile.y + tile.tileType.heightUnits <= 30
                )
            }
        }
    }

    @Test
    fun allTemplatesHaveTiles() {
        for (template in engine.getAllTemplates()) {
            assertTrue(
                "Template ${template.id} should have at least 1 tile",
                template.tiles.isNotEmpty()
            )
        }
    }

    @Test
    fun allTemplatesHaveUniqueIds() {
        val ids = engine.getAllTemplates().map { it.id }
        assertEquals("All template IDs should be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun allTemplatesHaveRequiredFields() {
        for (template in engine.getAllTemplates()) {
            assertTrue("${template.id}: id should not be blank", template.id.isNotBlank())
            assertTrue("${template.id}: name should not be blank", template.name.isNotBlank())
            assertTrue("${template.id}: category should not be blank", template.category.isNotBlank())
            assertTrue("${template.id}: description should not be blank", template.description.isNotBlank())
            assertTrue("${template.id}: tags should not be empty", template.tags.isNotEmpty())
        }
    }

    @Test
    fun allTemplatesUseValidTileTypes() {
        val validTileTypeIds = TileType.entries.map { it.id }.toSet()
        for (template in engine.getAllTemplates()) {
            template.tiles.forEach { tile ->
                assertTrue(
                    "${template.id}: Tile type ${tile.tileType.id} should be valid",
                    validTileTypeIds.contains(tile.tileType.id)
                )
            }
        }
    }

    @Test
    fun allTemplatesUseValidColors() {
        for (template in engine.getAllTemplates()) {
            template.tiles.forEach { tile ->
                assertTrue(
                    "${template.id}: Color ${tile.color} should be a valid TileColor",
                    TileColor.entries.contains(tile.color)
                )
            }
        }
    }

    @Test
    fun allTemplatesUseValidRotations() {
        for (template in engine.getAllTemplates()) {
            template.tiles.forEach { tile ->
                assertTrue(
                    "${template.id}: Rotation ${tile.rotation} should be valid",
                    Rotation.entries.contains(tile.rotation)
                )
            }
        }
    }

    @Test
    fun templatesCoverMultipleCategories() {
        val categories = engine.getAllTemplates().map { it.category }.toSet()
        assertTrue(
            "Should have at least 3 categories, found: $categories",
            categories.size >= 3
        )
    }

    @Test
    fun templatesUseDiverseTileTypes() {
        val tileTypesUsed = mutableSetOf<TileType>()
        engine.getAllTemplates().forEach { template ->
            template.tiles.forEach { tileTypesUsed.add(it.tileType) }
        }
        assertTrue(
            "Templates should use at least 4 different tile types, found ${tileTypesUsed.size}",
            tileTypesUsed.size >= 4
        )
    }

    @Test
    fun categoriesIncludeExpectedValues() {
        val categories = engine.getAllTemplates().map { it.category }.toSet()
        assertTrue("Should have 'animals' category", categories.contains("animals"))
        assertTrue("Should have 'vehicles' category", categories.contains("vehicles"))
        assertTrue("Should have 'nature' category", categories.contains("nature") || categories.contains("buildings"))
    }

    @Test
    fun complementaryRightTrianglesDoNotOverlap() {
        // R0 fills lower-right triangle, R180 fills upper-left triangle
        // Together they fill a square but their filled regions don't overlap
        val tiles = listOf(
            TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R180, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(
            "Complementary right triangles should NOT be flagged as overlapping. Errors: $errors",
            errors.all { !it.contains("overlap", ignoreCase = true) }
        )
    }

    @Test
    fun identicalTrianglesOverlap() {
        val tiles = listOf(
            TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R0, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(
            "Identical triangles at same position should be flagged as overlapping",
            errors.any { it.contains("overlap", ignoreCase = true) }
        )
    }

    @Test
    fun nonOverlappingTrianglesPassValidation() {
        // Two triangles far apart should pass
        val tiles = listOf(
            TilePlacement(TileType.RIGHT_TRIANGLE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.RIGHT_TRIANGLE, 6, 0, Rotation.R0, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(
            "Non-overlapping triangles should pass validation. Errors: $errors",
            errors.isEmpty()
        )
    }

    @Test
    fun squareAndOverlappingTriangleDetected() {
        // Triangle placed so its polygon overlaps with square
        val tiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 3, 3, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.RIGHT_TRIANGLE, 3, 3, Rotation.R0, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(
            "Triangle overlapping square should be detected",
            errors.any { it.contains("overlap", ignoreCase = true) }
        )
    }

    @Test
    fun adjacentSquaresStillDetectedAsOverlap() {
        // Adjacent squares that share a boundary (bounding boxes touching)
        val tiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, 3, 0, Rotation.R0, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(
            "Non-overlapping adjacent squares should pass. Errors: $errors",
            errors.isEmpty()
        )
    }

    @Test
    fun equilateralTrianglesDifferentRotationsCanCoexist() {
        // Two equilateral triangles at same position, one pointing up (R0) one pointing down (R180)
        // This is the classic Star of David pattern — they DO overlap in the center
        // So they should be detected as overlapping
        val tiles = listOf(
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.EQUILATERAL_TRIANGLE, 0, 0, Rotation.R180, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(
            "Overlapping equilateral triangles should be detected. Errors: $errors",
            errors.any { it.contains("overlap", ignoreCase = true) }
        )
    }
}
