package com.architectai.core.data.llm

import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpLLMClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpLLMClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpLLMClient(baseUrl = server.url("").toString().trimEnd('/'))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generateComposition_successResponse() = kotlinx.coroutines.test.runTest {
        val responseJson = """
        {
            "object": "House",
            "components": [
                {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 0, "color": "#A04523"},
                {"tile_id": "right_triangle", "x": 3, "y": 0, "rotation": 90, "color": "#F18D58"}
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = client.generateComposition("Build a house")

        assertTrue("Expected Success, got $result", result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals("House", composition.name)
        assertEquals(2, composition.tiles.size)
        assertEquals(Composition.Source.AI_GENERATED, composition.source)

        // Verify first tile
        val tile0 = composition.tiles[0]
        assertEquals(TileType.SOLID_SQUARE, tile0.tileType)
        assertEquals(0, tile0.x)
        assertEquals(0, tile0.y)
        assertEquals(Rotation.R0, tile0.rotation)
        assertEquals(TileColor.RED, tile0.color)
    }

    @Test
    fun generateComposition_serverError() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val result = client.generateComposition("Build something")

        assertTrue("Expected Error, got $result", result is LLMResult.Error)
        val error = result as LLMResult.Error
        assertTrue(error.message.contains("500"))
    }

    @Test
    fun generateComposition_invalidJson() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))

        val result = client.generateComposition("Build something")

        assertTrue("Expected Error for invalid JSON, got $result", result is LLMResult.Error)
    }

    @Test
    fun generateComposition_unknownTileType_skipped() = kotlinx.coroutines.test.runTest {
        val responseJson = """
        {
            "object": "Mystery",
            "components": [
                {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 0, "color": "#A04523"},
                {"tile_id": "unknown_tile", "x": 5, "y": 5, "rotation": 0, "color": "#FF0000"}
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = client.generateComposition("Mystery object")

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals(1, composition.tiles.size) // Unknown tile should be skipped
        assertEquals(TileType.SOLID_SQUARE, composition.tiles[0].tileType)
    }

    @Test
    fun generateComposition_invalidRotation_defaultsToR0() = kotlinx.coroutines.test.runTest {
        val responseJson = """
        {
            "object": "Rotated",
            "components": [
                {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 45, "color": "#A04523"}
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = client.generateComposition("Rotated tile")

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals(Rotation.R0, composition.tiles[0].rotation) // Invalid rotation defaults to R0
    }

    @Test
    fun generateComposition_unknownColor_defaultsToRed() = kotlinx.coroutines.test.runTest {
        val responseJson = """
        {
            "object": "Colored",
            "components": [
                {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 0, "color": "#UNKNOWN"}
            ]
        }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val result = client.generateComposition("Colored tile")

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals(TileColor.RED, composition.tiles[0].color) // Unknown color defaults to RED
    }

    @Test
    fun parseResponse_validJson() {
        val json = """
        {
            "object": "Tower",
            "components": [
                {"tile_id": "solid_square", "x": 5, "y": 5, "rotation": 90, "color": "#2196F3"}
            ]
        }
        """.trimIndent()

        val result = client.parseResponse(json)

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals("Tower", composition.name)
        assertEquals(TileType.SOLID_SQUARE, composition.tiles[0].tileType)
        assertEquals(5, composition.tiles[0].x)
        assertEquals(Rotation.R90, composition.tiles[0].rotation)
        assertEquals(TileColor.BLUE, composition.tiles[0].color)
    }

    @Test
    fun parseResponse_invalidJson() {
        val result = client.parseResponse("not valid json")
        assertTrue(result is LLMResult.Error)
    }

    @Test
    fun parseResponse_emptyComponents() {
        val json = """{"object": "Empty", "components": []}"""
        val result = client.parseResponse(json)
        assertTrue(result is LLMResult.Success)
        assertEquals(0, (result as LLMResult.Success).composition.tiles.size)
    }

    @Test
    fun generateComposition_sendsCorrectRequestBody() = kotlinx.coroutines.test.runTest {
        server.enqueue(MockResponse().setBody("""{"object": "Test", "components": []}""").setResponseCode(200))

        client.generateComposition("Build a castle")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("/v1/generate"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("Build a castle"))
    }
}

class LLMResponseMappingTest {

    @Test
    fun toComposition_mapsAllFields() {
        val response = LLMResponse(
            objectName = "TestObject",
            components = listOf(
                LLMComponent("solid_square", 1, 2, 90, "#4CAF50"),
                LLMComponent("right_triangle", 5, 6, 180, "#F18D58")
            )
        )

        val composition = response.toComposition()

        assertEquals("TestObject", composition.name)
        assertEquals(2, composition.tiles.size)
        assertEquals(Composition.Source.AI_GENERATED, composition.source)

        val tile1 = composition.tiles[0]
        assertEquals(TileType.SOLID_SQUARE, tile1.tileType)
        assertEquals(1, tile1.x)
        assertEquals(2, tile1.y)
        assertEquals(Rotation.R90, tile1.rotation)
        assertEquals(TileColor.GREEN, tile1.color)

        val tile2 = composition.tiles[1]
        assertEquals(TileType.RIGHT_TRIANGLE, tile2.tileType)
        assertEquals(Rotation.R180, tile2.rotation)
        assertEquals(TileColor.ORANGE, tile2.color)
    }

    @Test
    fun toComposition_skipsUnknownTileIds() {
        val response = LLMResponse(
            objectName = "Mixed",
            components = listOf(
                LLMComponent("solid_square", 0, 0, 0, "#A04523"),
                LLMComponent("nonexistent", 5, 5, 0, "#FF0000")
            )
        )

        val composition = response.toComposition()
        assertEquals(1, composition.tiles.size)
        assertEquals(TileType.SOLID_SQUARE, composition.tiles[0].tileType)
    }

    @Test
    fun toComposition_generatesUniqueIds() {
        val response = LLMResponse(
            objectName = "Test",
            components = listOf(
                LLMComponent("solid_square", 0, 0, 0, "#A04523"),
                LLMComponent("solid_square", 3, 0, 0, "#A04523")
            )
        )

        val comp1 = response.toComposition()
        val comp2 = response.toComposition()
        // Each call should generate a unique ID
        assertNotNull(comp1.id)
        assert(comp1.id != comp2.id) { "Compositions should have unique IDs" }
    }
}
