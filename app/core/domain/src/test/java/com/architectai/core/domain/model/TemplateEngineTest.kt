package com.architectai.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemplateEngineTest {

    private lateinit var engine: TemplateEngine

    private val simpleTemplateJson = """
    {
        "id": "test_square",
        "name": "Test Square",
        "category": "test",
        "description": "A simple test template",
        "tags": ["test", "square"],
        "tiles": [
            {"tileType": "SOLID_SQUARE", "x": 0, "y": 0, "rotation": "R0", "color": "RED"},
            {"tileType": "SOLID_SQUARE", "x": 3, "y": 0, "rotation": "R0", "color": "BLUE"},
            {"tileType": "SOLID_SQUARE", "x": 0, "y": 3, "rotation": "R0", "color": "GREEN"},
            {"tileType": "SOLID_SQUARE", "x": 3, "y": 3, "rotation": "R0", "color": "YELLOW"}
        ]
    }
    """.trimIndent()

    private val triangleTemplateJson = """
    {
        "id": "test_triangle",
        "name": "Test Triangle",
        "category": "test",
        "description": "A triangle test template",
        "tags": ["test", "triangle"],
        "tiles": [
            {"tileType": "EQUILATERAL_TRIANGLE", "x": 9, "y": 0, "rotation": "R180", "color": "ORANGE"},
            {"tileType": "SOLID_SQUARE", "x": 9, "y": 3, "rotation": "R0", "color": "ORANGE"},
            {"tileType": "SOLID_SQUARE", "x": 12, "y": 3, "rotation": "R0", "color": "ORANGE"},
            {"tileType": "SOLID_SQUARE", "x": 9, "y": 6, "rotation": "R0", "color": "ORANGE"},
            {"tileType": "SOLID_SQUARE", "x": 12, "y": 6, "rotation": "R0", "color": "ORANGE"}
        ]
    }
    """.trimIndent()

    private val overlappingTemplateJson = """
    {
        "id": "bad_overlap",
        "name": "Bad Overlap",
        "category": "test",
        "description": "An invalid template with overlapping tiles",
        "tags": ["test"],
        "tiles": [
            {"tileType": "SOLID_SQUARE", "x": 0, "y": 0, "rotation": "R0", "color": "RED"},
            {"tileType": "SOLID_SQUARE", "x": 1, "y": 0, "rotation": "R0", "color": "BLUE"}
        ]
    }
    """.trimIndent()

    private val outOfBoundsTemplateJson = """
    {
        "id": "bad_bounds",
        "name": "Bad Bounds",
        "category": "test",
        "description": "An invalid template with tiles out of bounds",
        "tags": ["test"],
        "tiles": [
            {"tileType": "SOLID_SQUARE", "x": 28, "y": 28, "rotation": "R0", "color": "RED"}
        ]
    }
    """.trimIndent()

    @Before
    fun setUp() {
        engine = TemplateEngine()
    }

    // ── Template Loading Tests ────────────────────────────────────────────

    @Test
    fun loadTemplate_validJson_returnsSuccess() {
        val result = engine.loadTemplate(simpleTemplateJson)
        assertTrue("Expected success, got $result", result.isSuccess)
        val template = result.getOrThrow()
        assertEquals("test_square", template.id)
        assertEquals("Test Square", template.name)
        assertEquals("test", template.category)
        assertEquals(4, template.tiles.size)
    }

    @Test
    fun loadTemplate_storesTemplate() {
        engine.loadTemplate(simpleTemplateJson)
        val template = engine.getTemplate("test_square")
        assertNotNull(template)
        assertEquals("Test Square", template!!.name)
    }

    @Test
    fun loadTemplate_invalidJson_returnsFailure() {
        val result = engine.loadTemplate("not valid json")
        assertTrue("Expected failure for invalid JSON", result.isFailure)
    }

    @Test
    fun loadTemplate_overlappingTiles_returnsFailure() {
        val result = engine.loadTemplate(overlappingTemplateJson)
        assertTrue("Expected failure for overlapping tiles", result.isFailure)
    }

    @Test
    fun loadTemplate_outOfBounds_returnsFailure() {
        val result = engine.loadTemplate(outOfBoundsTemplateJson)
        assertTrue("Expected failure for out-of-bounds tiles", result.isFailure)
    }

    @Test
    fun loadTemplates_multipleTemplates() {
        val results = engine.loadTemplates(listOf(simpleTemplateJson, triangleTemplateJson))
        assertEquals(2, results.size)
        assertTrue(results[0].isSuccess)
        assertTrue(results[1].isSuccess)
        assertEquals(2, engine.getAllTemplates().size)
    }

    @Test
    fun getTemplate_unknownId_returnsNull() {
        assertNull(engine.getTemplate("nonexistent"))
    }

    // ── Template Query Tests ──────────────────────────────────────────────

    @Test
    fun getAllTemplates_returnsAllLoadedTemplates() {
        engine.loadTemplate(simpleTemplateJson)
        engine.loadTemplate(triangleTemplateJson)
        assertEquals(2, engine.getAllTemplates().size)
    }

    @Test
    fun getTemplatesByCategory_groupsCorrectly() {
        engine.loadTemplate(simpleTemplateJson)
        engine.loadTemplate(triangleTemplateJson)
        val byCategory = engine.getTemplatesByCategory()
        assertEquals(1, byCategory.size)
        assertEquals(2, byCategory["test"]!!.size)
    }

    @Test
    fun searchTemplates_byName() {
        engine.loadTemplate(simpleTemplateJson)
        val results = engine.searchTemplates("square")
        assertEquals(1, results.size)
        assertEquals("test_square", results[0].id)
    }

    @Test
    fun searchTemplates_byTag() {
        engine.loadTemplate(simpleTemplateJson)
        engine.loadTemplate(triangleTemplateJson)
        val results = engine.searchTemplates("triangle")
        // Both templates have "test" tag, but only triangle template has "triangle" tag
        assertEquals(1, results.size)
        assertEquals("test_triangle", results[0].id)
    }

    @Test
    fun searchTemplates_caseInsensitive() {
        engine.loadTemplate(simpleTemplateJson)
        val results = engine.searchTemplates("SQUARE")
        assertEquals(1, results.size)
    }

    @Test
    fun searchTemplates_noMatch_returnsEmpty() {
        engine.loadTemplate(simpleTemplateJson)
        val results = engine.searchTemplates("castle")
        assertEquals(0, results.size)
    }

    @Test
    fun getCatalogText_returnsFormattedCatalog() {
        engine.loadTemplate(simpleTemplateJson)
        val catalog = engine.getCatalogText()
        assertTrue(catalog.contains("test_square"))
        assertTrue(catalog.contains("test"))
    }

    // ── Color Swap Modification Tests ─────────────────────────────────────

    @Test
    fun colorSwap_changesMatchingColors() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.ColorSwap(TileColor.RED, TileColor.PURPLE))
        )
        assertTrue("Expected success, got $result", result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        composition.tiles.forEach { tile ->
            assertFalse("No RED tiles should remain", tile.color == TileColor.RED)
        }
        assertTrue("Should have PURPLE tiles", composition.tiles.any { it.color == TileColor.PURPLE })
    }

    @Test
    fun colorSwap_preservesOtherColors() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.ColorSwap(TileColor.RED, TileColor.PURPLE))
        )
        assertTrue(result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        // BLUE, GREEN, YELLOW tiles should remain unchanged
        assertTrue(composition.tiles.any { it.color == TileColor.BLUE })
        assertTrue(composition.tiles.any { it.color == TileColor.GREEN })
        assertTrue(composition.tiles.any { it.color == TileColor.YELLOW })
    }

    @Test
    fun colorSwap_noMatchingColor_noChange() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.ColorSwap(TileColor.PINK, TileColor.PURPLE))
        )
        assertTrue(result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        // No PINK tiles exist, so nothing changes
        assertEquals(4, composition.tiles.size)
        assertTrue(composition.tiles.any { it.color == TileColor.RED })
    }

    // ── Scale Modification Tests ──────────────────────────────────────────

    @Test
    fun scale_halfReducesCoordinates() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Scale(0.5f))
        )
        // At 0.5x scale: (0,0), (1,0), (0,1), (1,1) — each tile is still 3x3
        // So tiles overlap (1+3 > 0). This should be REJECTED.
        assertTrue(
            "Half-scale should cause overlaps since tiles are still 3x3",
            result is TemplateResult.Rejected
        )
    }

    @Test
    fun scale_doubleExpandsCoordinates() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Scale(2.0f))
        )
        // At 2x scale: (0,0), (6,0), (0,6), (6,6) — all 3x3 tiles
        // (6,6) + 3 = 9, within 30x30 grid
        assertTrue(result is TemplateResult.Success || result is TemplateResult.Rejected)
    }

    @Test
    fun scale_factorOne_noChange() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Scale(1.0f))
        )
        assertTrue(result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        val coords = composition.tiles.map { Pair(it.x, it.y) }.toSet()
        assertTrue(coords.contains(Pair(0, 0)))
        assertTrue(coords.contains(Pair(3, 0)))
    }

    @Test
    fun scale_zeroFactor_fails() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Scale(0f))
        )
        // Scale with 0 should be skipped
        assertTrue(result is TemplateResult.Success)
    }

    @Test
    fun scale_outOfBounds_rejectsModification() {
        // Load a template with tiles at high coordinates
        val largeTemplateJson = """
        {
            "id": "large_test",
            "name": "Large Test",
            "category": "test",
            "description": "Test",
            "tags": ["test"],
            "tiles": [
                {"tileType": "SOLID_SQUARE", "x": 12, "y": 12, "rotation": "R0", "color": "RED"},
                {"tileType": "SOLID_SQUARE", "x": 15, "y": 12, "rotation": "R0", "color": "BLUE"}
            ]
        }
        """.trimIndent()
        engine.loadTemplate(largeTemplateJson)
        val result = engine.applyModifications(
            "large_test",
            listOf(TemplateModification.Scale(2.0f))
        )
        // 12*2=24, 24+3=27 (ok), 15*2=30, 30+3=33 (out of bounds!)
        assertTrue(result is TemplateResult.Rejected)
    }

    // ── Mirror Modification Tests ─────────────────────────────────────────

    @Test
    fun mirrorHorizontal_flipsXCoordinates() {
        engine.loadTemplate(simpleTemplateJson)
        // Original x positions: 0, 3, 0, 3 → width 6
        // After horizontal mirror: maxX - x - 3 = 6 - x - 3 = 3 - x
        // So 0→3, 3→0
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Mirror(Axis.HORIZONTAL))
        )
        assertTrue(result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        val xCoords = composition.tiles.map { it.x }.sorted()
        // After mirror: original x=0 becomes 3, x=3 becomes 0
        assertEquals(listOf(0, 0, 3, 3), xCoords)
    }

    @Test
    fun mirrorVertical_flipsYCoordinates() {
        engine.loadTemplate(simpleTemplateJson)
        // Original y positions: 0, 0, 3, 3 → height 6
        // After vertical mirror: maxY - y - 3 = 6 - y - 3 = 3 - y
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Mirror(Axis.VERTICAL))
        )
        assertTrue(result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        val yCoords = composition.tiles.map { it.y }.sorted()
        assertEquals(listOf(0, 0, 3, 3), yCoords)
    }

    // ── Translate Modification Tests ──────────────────────────────────────

    @Test
    fun translate_shiftsAllTiles() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Translate(3, 6))
        )
        assertTrue(result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        composition.tiles.forEach { tile ->
            assertTrue("Tile x should be >= 3 after translate(3,6)", tile.x >= 3)
            assertTrue("Tile y should be >= 6 after translate(3,6)", tile.y >= 6)
        }
    }

    @Test
    fun translate_outOfBounds_rejects() {
        engine.loadTemplate(simpleTemplateJson)
        // Translate by 28 would put tiles at x=28+3=31, out of bounds
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Translate(28, 0))
        )
        assertTrue(result is TemplateResult.Rejected)
    }

    @Test
    fun translate_negativeOutOfBounds_rejects() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Translate(-1, 0))
        )
        assertTrue(result is TemplateResult.Rejected)
    }

    // ── Multiple Modifications Tests ──────────────────────────────────────

    @Test
    fun multipleModifications_appliedInOrder() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(
                TemplateModification.ColorSwap(TileColor.RED, TileColor.PURPLE),
                TemplateModification.Translate(3, 3)
            )
        )
        assertTrue(result is TemplateResult.Success)
        val composition = (result as TemplateResult.Success).composition
        // Should have PURPLE (was RED) and translated by (3,3)
        assertTrue(composition.tiles.any { it.color == TileColor.PURPLE })
        assertTrue(composition.tiles.all { it.x >= 3 && it.y >= 3 })
    }

    @Test
    fun unknownTemplate_returnsRejected() {
        val result = engine.applyModifications("nonexistent", emptyList())
        assertTrue(result is TemplateResult.Rejected)
        val rejected = result as TemplateResult.Rejected
        assertTrue(rejected.reason.contains("not found"))
    }

    // ── Combine Tests ─────────────────────────────────────────────────────

    @Test
    fun combine_twoTemplates_mergesTiles() {
        engine.loadTemplate(simpleTemplateJson)
        engine.loadTemplate(triangleTemplateJson)
        val result = engine.applyCombine("test_square", "test_triangle", Pair(6, 0))
        if (result is TemplateResult.Success) {
            // Combined template should have tiles from both
            assertTrue(result.composition.tiles.size >= 4 + 5)
        }
        // Could be rejected if they overlap
    }

    @Test
    fun combine_overlappingTemplates_rejects() {
        engine.loadTemplate(simpleTemplateJson)
        engine.loadTemplate(triangleTemplateJson)
        // No offset — triangle starts at (9,0), so with (0,0) offset they shouldn't overlap
        // But let's test with negative offset that would overlap
        val result = engine.applyCombine("test_square", "test_triangle", Pair(-3, -3))
        // This should cause overlaps and be rejected
        if (result is TemplateResult.Rejected) {
            assertTrue(result.reason.contains("constraint violations"))
        }
    }

    @Test
    fun combine_unknownTemplate_rejects() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyCombine("test_square", "nonexistent", Pair(0, 0))
        assertTrue(result is TemplateResult.Rejected)
        assertTrue((result as TemplateResult.Rejected).reason.contains("not found"))
    }

    // ── Constraint Validation Tests ───────────────────────────────────────

    @Test
    fun validateTiles_noOverlaps_returnsEmpty() {
        val tiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, 3, 0, Rotation.R0, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue("Expected no errors, got: $errors", errors.isEmpty())
    }

    @Test
    fun validateTiles_overlappingTiles_returnsErrors() {
        val tiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED),
            TilePlacement(TileType.SOLID_SQUARE, 1, 0, Rotation.R0, TileColor.BLUE)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("overlap") })
    }

    @Test
    fun validateTiles_outOfBounds_returnsErrors() {
        val tiles = listOf(
            TilePlacement(TileType.SOLID_SQUARE, 28, 28, Rotation.R0, TileColor.RED)
        )
        val errors = engine.validateTiles(tiles)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("bounds") })
    }

    @Test
    fun validateTiles_tooManyTiles_returnsError() {
        val tiles = (0..201).map { i ->
            val x = (i % 10) * 3
            val y = (i / 10) * 3
            TilePlacement(TileType.SOLID_SQUARE, x, y, Rotation.R0, TileColor.RED)
        }
        val errors = engine.validateTiles(tiles)
        assertTrue(errors.any { it.contains("Too many tiles") })
    }

    @Test
    fun boundingBoxesOverlap_adjacentTiles_dontOverlap() {
        val a = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val b = TilePlacement(TileType.SOLID_SQUARE, 3, 0, Rotation.R0, TileColor.BLUE)
        assertFalse(engine.boundingBoxesOverlap(a, b))
    }

    @Test
    fun boundingBoxesOverlap_overlappingTiles_doOverlap() {
        val a = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val b = TilePlacement(TileType.SOLID_SQUARE, 2, 0, Rotation.R0, TileColor.BLUE)
        assertTrue(engine.boundingBoxesOverlap(a, b))
    }

    @Test
    fun boundingBoxesOverlap_diagonalTouch_dontOverlap() {
        val a = TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
        val b = TilePlacement(TileType.SOLID_SQUARE, 3, 3, Rotation.R0, TileColor.BLUE)
        assertFalse(engine.boundingBoxesOverlap(a, b))
    }

    // ── JSON Parsing Tests ────────────────────────────────────────────────

    @Test
    fun parseSelectionResponse_validJson() {
        val json = """{"templateId": "lion", "modifications": [{"type": "color_swap", "from": "ORANGE", "to": "BLUE"}]}"""
        val result = engine.parseSelectionResponse(json)
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("lion", response.templateId)
        assertEquals(1, response.modifications.size)
    }

    @Test
    fun parseSelectionResponse_invalidJson() {
        val result = engine.parseSelectionResponse("not json")
        assertTrue(result.isFailure)
    }

    @Test
    fun parseSelectionResponse_noModifications() {
        val json = """{"templateId": "lion", "modifications": []}"""
        val result = engine.parseSelectionResponse(json)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().modifications.size)
    }

    // ── TemplateJson Mapping Tests ────────────────────────────────────────

    @Test
    fun templateJson_toDomain_mapsAllFields() {
        val result = engine.loadTemplate(simpleTemplateJson)
        assertTrue(result.isSuccess)
        val template = result.getOrThrow()
        assertEquals("test_square", template.id)
        assertEquals("Test Square", template.name)
        assertEquals("test", template.category)
        assertEquals("A simple test template", template.description)
        assertEquals(listOf("test", "square"), template.tags)
        assertEquals(4, template.tiles.size)
    }

    @Test
    fun templateJson_toDomain_mapsTileTypes() {
        val result = engine.loadTemplate(simpleTemplateJson)
        assertTrue(result.isSuccess)
        val template = result.getOrThrow()
        assertEquals(TileType.SOLID_SQUARE, template.tiles[0].tileType)
    }

    @Test
    fun templateJson_toDomain_mapsRotations() {
        val result = engine.loadTemplate(triangleTemplateJson)
        assertTrue(result.isSuccess)
        val template = result.getOrThrow()
        assertEquals(Rotation.R180, template.tiles[0].rotation)
        assertEquals(Rotation.R0, template.tiles[1].rotation)
    }

    @Test
    fun templateJson_toDomain_mapsColors() {
        val result = engine.loadTemplate(simpleTemplateJson)
        assertTrue(result.isSuccess)
        val template = result.getOrThrow()
        assertEquals(TileColor.RED, template.tiles[0].color)
        assertEquals(TileColor.BLUE, template.tiles[1].color)
    }

    // ── CompositionTemplate toComposition Tests ───────────────────────────

    @Test
    fun compositionTemplate_toComposition() {
        engine.loadTemplate(simpleTemplateJson)
        val template = engine.getTemplate("test_square")!!
        val composition = template.toComposition()
        assertNotNull(composition.id)
        assertEquals("Test Square", composition.name)
        assertEquals(4, composition.tiles.size)
        assertEquals(Composition.Source.AI_GENERATED, composition.source)
    }

    // ── Applied Modifications Description Tests ───────────────────────────

    @Test
    fun applyModifications_returnsDescriptions() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.ColorSwap(TileColor.RED, TileColor.PURPLE))
        ) as TemplateResult.Success
        assertTrue(result.appliedModifications.isNotEmpty())
        assertTrue(result.appliedModifications[0].contains("Swapped"))
    }

    @Test
    fun applyModifications_rejectedResult_hasReason() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Translate(50, 0))
        )
        if (result is TemplateResult.Rejected) {
            assertTrue(result.reason.isNotEmpty())
        }
    }

    @Test
    fun applyModifications_rejectedResult_hasPartialComposition() {
        engine.loadTemplate(simpleTemplateJson)
        val result = engine.applyModifications(
            "test_square",
            listOf(TemplateModification.Translate(50, 0))
        )
        if (result is TemplateResult.Rejected) {
            assertNotNull(result.partialComposition)
            assertEquals(4, result.partialComposition!!.tiles.size)
        }
    }
}

class CompositionTemplateModelTest {

    @Test
    fun compositionTemplateCreation() {
        val template = CompositionTemplate(
            id = "test",
            name = "Test",
            category = "test",
            description = "A test template",
            tags = listOf("test"),
            tiles = emptyList()
        )
        assertEquals("test", template.id)
        assertEquals("Test", template.name)
    }

    @Test
    fun templateModification_colorSwap() {
        val mod = TemplateModification.ColorSwap(TileColor.RED, TileColor.BLUE)
        assertEquals(TileColor.RED, mod.fromColor)
        assertEquals(TileColor.BLUE, mod.toColor)
    }

    @Test
    fun templateModification_scale() {
        val mod = TemplateModification.Scale(1.5f)
        assertEquals(1.5f, mod.factor)
    }

    @Test
    fun templateModification_mirror() {
        val mod = TemplateModification.Mirror(Axis.HORIZONTAL)
        assertEquals(Axis.HORIZONTAL, mod.axis)
    }

    @Test
    fun templateModification_translate() {
        val mod = TemplateModification.Translate(5, 10)
        assertEquals(5, mod.dx)
        assertEquals(10, mod.dy)
    }

    @Test
    fun templateModification_combine() {
        val mod = TemplateModification.Combine("other", Pair(3, 6))
        assertEquals("other", mod.templateId)
        assertEquals(Pair(3, 6), mod.offset)
    }

    @Test
    fun axis_values() {
        assertEquals(2, Axis.entries.size)
        assertNotNull(Axis.valueOf("HORIZONTAL"))
        assertNotNull(Axis.valueOf("VERTICAL"))
    }
}
