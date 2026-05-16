package com.architectai.core.data.llm

import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType
import io.mockk.every
import io.mockk.mockk
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
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var config: LLMConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        // Create a mock SharedPreferences that returns the server URL
        prefs = mockk(relaxed = true)
        val serverUrl = server.url("").toString().trimEnd('/')
        every { prefs.getString("llm_base_url", any()) } returns serverUrl
        every { prefs.getString("llm_api_key", any()) } returns "test-api-key"
        every { prefs.getString("llm_model_name", any()) } returns "test-model"

        config = LLMConfig(prefs)
        client = OkHttpLLMClient(config = config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generateComposition_successResponse() = kotlinx.coroutines.test.runTest {
        val openAiResponse = """{"id":"chatcmpl-test","choices":[{"index":0,"message":{"role":"assistant","content":"{\"object\":\"House\",\"components\":[{\"tile_id\":\"solid_square\",\"x\":0,\"y\":0,\"rotation\":0,\"color\":\"#A04523\"},{\"tile_id\":\"right_triangle\",\"x\":3,\"y\":0,\"rotation\":90,\"color\":\"#F18D58\"}]}"},"finish_reason":"stop"}],"model":"glm-4-flash"}"""

        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))

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
    fun generateComposition_successWithMarkdownCodeBlocks() = kotlinx.coroutines.test.runTest {
        // LLM may wrap JSON in markdown code blocks
        val responseWithCodeBlocks = """{"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"```json\n{\"object\":\"Car\",\"components\":[{\"tile_id\":\"solid_square\",\"x\":0,\"y\":0,\"rotation\":0,\"color\":\"#2196F3\"}]}\n```"},"finish_reason":"stop"}],"model":"test"}"""

        server.enqueue(MockResponse().setBody(responseWithCodeBlocks).setResponseCode(200))

        val result = client.generateComposition("Build a car")

        assertTrue("Expected Success, got $result", result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals("Car", composition.name)
        assertEquals(1, composition.tiles.size)
        assertEquals(TileColor.BLUE, composition.tiles[0].color)
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
        val openAiResponse = """{"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"not json at all"},"finish_reason":"stop"}]}"""

        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))

        val result = client.generateComposition("Build something")

        assertTrue("Expected Error for invalid JSON, got $result", result is LLMResult.Error)
    }

    @Test
    fun generateComposition_unknownTileType_skipped() = kotlinx.coroutines.test.runTest {
        val openAiResponse = """{"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"{\"object\":\"Mystery\",\"components\":[{\"tile_id\":\"solid_square\",\"x\":0,\"y\":0,\"rotation\":0,\"color\":\"#A04523\"},{\"tile_id\":\"unknown_tile\",\"x\":5,\"y\":5,\"rotation\":0,\"color\":\"#FF0000\"}]}"},"finish_reason":"stop"}]}"""

        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))

        val result = client.generateComposition("Mystery object")

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals(1, composition.tiles.size) // Unknown tile should be skipped
        assertEquals(TileType.SOLID_SQUARE, composition.tiles[0].tileType)
    }

    @Test
    fun generateComposition_invalidRotation_defaultsToR0() = kotlinx.coroutines.test.runTest {
        val openAiResponse = """{"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"{\"object\":\"Rotated\",\"components\":[{\"tile_id\":\"solid_square\",\"x\":0,\"y\":0,\"rotation\":45,\"color\":\"#A04523\"}]}"},"finish_reason":"stop"}]}"""

        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))

        val result = client.generateComposition("Rotated tile")

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals(Rotation.R0, composition.tiles[0].rotation) // Invalid rotation defaults to R0
    }

    @Test
    fun generateComposition_unknownColor_defaultsToRed() = kotlinx.coroutines.test.runTest {
        val openAiResponse = """{"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"{\"object\":\"Colored\",\"components\":[{\"tile_id\":\"solid_square\",\"x\":0,\"y\":0,\"rotation\":0,\"color\":\"#UNKNOWN\"}]}"},"finish_reason":"stop"}]}"""

        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))

        val result = client.generateComposition("Colored tile")

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals(TileColor.RED, composition.tiles[0].color) // Unknown color defaults to RED
    }

    @Test
    fun generateComposition_colorNameLookup() = kotlinx.coroutines.test.runTest {
        val openAiResponse = """{"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"{\"object\":\"Named\",\"components\":[{\"tile_id\":\"solid_square\",\"x\":0,\"y\":0,\"rotation\":0,\"color\":\"BLUE\"},{\"tile_id\":\"solid_square\",\"x\":3,\"y\":0,\"rotation\":0,\"color\":\"GREEN\"}]}"},"finish_reason":"stop"}]}"""

        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))

        val result = client.generateComposition("Color test")

        assertTrue(result is LLMResult.Success)
        val composition = (result as LLMResult.Success).composition
        assertEquals(TileColor.BLUE, composition.tiles[0].color)
        assertEquals(TileColor.GREEN, composition.tiles[1].color)
    }

    @Test
    fun generateComposition_notConfigured_returnsError() = kotlinx.coroutines.test.runTest {
        // Create config with empty values
        val emptyPrefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { emptyPrefs.getString("llm_base_url", any()) } returns ""
        every { emptyPrefs.getString("llm_api_key", any()) } returns ""
        every { emptyPrefs.getString("llm_model_name", any()) } returns "test-model"
        val emptyConfig = LLMConfig(emptyPrefs)
        val unconfiguredClient = OkHttpLLMClient(config = emptyConfig)

        val result = unconfiguredClient.generateComposition("Build something")

        assertTrue("Expected Error when not configured", result is LLMResult.Error)
        assertTrue("Error should mention configuration", (result as LLMResult.Error).message.contains("not configured"))
    }

    @Test
    fun generateComposition_emptyChoices_returnsError() = kotlinx.coroutines.test.runTest {
        val openAiResponse = """{"id":"test","choices":[],"model":"test"}"""

        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))

        val result = client.generateComposition("Build something")

        assertTrue("Expected Error for empty choices", result is LLMResult.Error)
        assertTrue("Error should mention no choices", (result as LLMResult.Error).message.contains("No choices"))
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
        server.enqueue(MockResponse().setBody("""{"id":"test","choices":[{"index":0,"message":{"role":"assistant","content":"{\"object\":\"Test\",\"components\":[]}"}}]}""").setResponseCode(200))

        client.generateComposition("Build a castle")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue("Path should contain /chat/completions, got ${request.path}", request.path!!.contains("/chat/completions"))

        // Verify Authorization header
        val authHeader = request.getHeader("Authorization")
        assertTrue("Should have Bearer token", authHeader?.startsWith("Bearer ") == true)

        // Verify request body is in OpenAI chat format
        val body = request.body.readUtf8()
        assertTrue("Body should contain model field", body.contains("\"model\""))
        assertTrue("Body should contain messages field", body.contains("\"messages\""))
        assertTrue("Body should contain system message", body.contains("\"system\""))
        assertTrue("Body should contain user message", body.contains("\"user\""))
        assertTrue("Body should contain the prompt", body.contains("Build a castle"))
    }

    @Test
    fun extractJsonFromContent_plainJson() {
        val content = """{"object":"House","components":[]}"""
        assertEquals(content, client.extractJsonFromContent(content))
    }

    @Test
    fun extractJsonFromContent_markdownCodeBlock() {
        val content = "```json\n{\"object\":\"House\",\"components\":[]}\n```"
        assertEquals("""{"object":"House","components":[]}""", client.extractJsonFromContent(content))
    }

    @Test
    fun extractJsonFromContent_markdownCodeBlockNoLanguage() {
        val content = "```\n{\"object\":\"House\",\"components\":[]}\n```"
        assertEquals("""{"object":"House","components":[]}""", client.extractJsonFromContent(content))
    }

    @Test
    fun extractJsonFromContent_surroundedByText() {
        val content = "Here's the composition:\n{\"object\":\"House\",\"components\":[]}\nHope you like it!"
        assertEquals("""{"object":"House","components":[]}""", client.extractJsonFromContent(content))
    }

    @Test
    fun buildSystemPrompt_includesTileTypes() {
        val prompt = client.buildSystemPrompt()
        assertTrue("Should mention solid_square", prompt.contains("solid_square"))
        assertTrue("Should mention window_square", prompt.contains("window_square"))
        assertTrue("Should mention equilateral_triangle", prompt.contains("equilateral_triangle"))
        assertTrue("Should mention right_triangle", prompt.contains("right_triangle"))
        assertTrue("Should mention isosceles_triangle", prompt.contains("isosceles_triangle"))
    }

    @Test
    fun buildSystemPrompt_includesColors() {
        val prompt = client.buildSystemPrompt()
        assertTrue("Should mention RED", prompt.contains("RED"))
        assertTrue("Should mention BLUE", prompt.contains("BLUE"))
        assertTrue("Should mention TRANSLUCENT", prompt.contains("TRANSLUCENT"))
    }

    @Test
    fun buildSystemPrompt_includesGridSize() {
        val prompt = client.buildSystemPrompt()
        assertTrue("Should mention 30", prompt.contains("30"))
    }

    @Test
    fun buildSystemPrompt_includesOutputFormat() {
        val prompt = client.buildSystemPrompt()
        assertTrue("Should mention object field", prompt.contains("\"object\""))
        assertTrue("Should mention components field", prompt.contains("\"components\""))
        assertTrue("Should mention tile_id field", prompt.contains("\"tile_id\""))
    }

    @Test
    fun buildSystemPrompt_includesTemplateCatalog() {
        client.templateCatalog = "- test: A test template. Tags: test"
        val prompt = client.buildSystemPrompt()
        assertTrue("Should include template catalog", prompt.contains("test: A test template"))
        assertTrue("Should include Available templates header", prompt.contains("Available templates"))
    }

    @Test
    fun buildSystemPrompt_includesRotationValues() {
        val prompt = client.buildSystemPrompt()
        assertTrue("Should mention rotation 0", prompt.contains("0") && prompt.contains("90") && prompt.contains("180") && prompt.contains("270"))
    }
}

class CompositionResponseMappingTest {

    @Test
    fun toComposition_mapsAllFields() {
        val response = CompositionResponse(
            objectName = "TestObject",
            components = listOf(
                CompositionComponent("solid_square", 1, 2, 90, "#4CAF50"),
                CompositionComponent("right_triangle", 5, 6, 180, "#F18D58")
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
        val response = CompositionResponse(
            objectName = "Mixed",
            components = listOf(
                CompositionComponent("solid_square", 0, 0, 0, "#A04523"),
                CompositionComponent("nonexistent", 5, 5, 0, "#FF0000")
            )
        )

        val composition = response.toComposition()
        assertEquals(1, composition.tiles.size)
        assertEquals(TileType.SOLID_SQUARE, composition.tiles[0].tileType)
    }

    @Test
    fun toComposition_generatesUniqueIds() {
        val response = CompositionResponse(
            objectName = "Test",
            components = listOf(
                CompositionComponent("solid_square", 0, 0, 0, "#A04523"),
                CompositionComponent("solid_square", 3, 0, 0, "#A04523")
            )
        )

        val comp1 = response.toComposition()
        val comp2 = response.toComposition()
        // Each call should generate a unique ID
        assertNotNull(comp1.id)
        assert(comp1.id != comp2.id) { "Compositions should have unique IDs" }
    }

    @Test
    fun toComposition_colorNameLookup() {
        val response = CompositionResponse(
            objectName = "Named",
            components = listOf(
                CompositionComponent("solid_square", 0, 0, 0, "RED"),
                CompositionComponent("solid_square", 3, 0, 0, "BLUE")
            )
        )

        val composition = response.toComposition()
        assertEquals(TileColor.RED, composition.tiles[0].color)
        assertEquals(TileColor.BLUE, composition.tiles[1].color)
    }
}
