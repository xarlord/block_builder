package com.architectai.feature.chat

import android.content.SharedPreferences
import app.cash.turbine.test
import com.architectai.core.data.llm.LLMClient
import com.architectai.core.data.llm.LLMConfig
import com.architectai.core.data.llm.LLMResult
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.data.template.TemplateLoader
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TemplateEngine
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var llmClient: LLMClient
    private lateinit var compositionRepository: CompositionRepository
    private lateinit var templateEngine: TemplateEngine
    private lateinit var templateLoader: TemplateLoader
    private lateinit var llmConfig: LLMConfig
    private lateinit var viewModel: ChatViewModel

    /** Minimal valid template JSONs for testing keyword matching */
    private val testTemplates = listOf(
        """{"id":"lion","name":"Lion","category":"animals","description":"A lion","tags":["lion","cat","wildlife","mane"],"tiles":[{"tileType":"SOLID_SQUARE","x":6,"y":3,"rotation":"R0","color":"ORANGE"}]}""",
        """{"id":"dog","name":"Dog","category":"animals","description":"A dog","tags":["dog","puppy","pet"],"tiles":[{"tileType":"SOLID_SQUARE","x":6,"y":3,"rotation":"R0","color":"BROWN"}]}""",
        """{"id":"car","name":"Car","category":"vehicles","description":"A car","tags":["car","automobile","vehicle"],"tiles":[{"tileType":"SOLID_SQUARE","x":6,"y":9,"rotation":"R0","color":"RED"}]}""",
        """{"id":"flower","name":"Flower","category":"nature","description":"A flower","tags":["flower","rose","plant"],"tiles":[{"tileType":"EQUILATERAL_TRIANGLE","x":9,"y":0,"rotation":"R180","color":"RED"}]}""",
        """{"id":"crocodile","name":"Crocodile","category":"animals","description":"A crocodile","tags":["crocodile","alligator","reptile"],"tiles":[{"tileType":"SOLID_SQUARE","x":18,"y":3,"rotation":"R0","color":"GREEN"}]}""",
        """{"id":"tram","name":"Tram","category":"vehicles","description":"A tram","tags":["tram","train","transit"],"tiles":[{"tileType":"RIGHT_TRIANGLE","x":6,"y":0,"rotation":"R90","color":"BLACK"}]}""",
        """{"id":"cat","name":"Cat","category":"animals","description":"A cat","tags":["cat","kitten","feline"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":9,"y":0,"rotation":"R0","color":"ORANGE"}]}""",
        """{"id":"house","name":"House","category":"buildings","description":"A house","tags":["house","home","building"],"tiles":[{"tileType":"ISOSCELES_TRIANGLE","x":9,"y":0,"rotation":"R0","color":"RED"}]}"""
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        llmClient = mockk()
        compositionRepository = mockk(relaxed = true)
        templateEngine = TemplateEngine()
        templateLoader = mockk()
        every { templateLoader.loadAllTemplates() } returns 0

        // Create mock LLM config backed by a mutable map for realistic get/set behavior
        val prefsMap = mutableMapOf<String, String>(
            "llm_base_url" to "",
            "llm_api_key" to "",
            "llm_model_name" to "glm-4-flash"
        )
        val prefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { prefsEditor.putString(any(), any()) } answers {
            prefsMap[firstArg()] = secondArg()
            prefsEditor
        }
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString("llm_base_url", "") } answers { prefsMap["llm_base_url"] ?: "" }
        every { prefs.getString("llm_api_key", "") } answers { prefsMap["llm_api_key"] ?: "" }
        every { prefs.getString("llm_model_name", "glm-4-flash") } answers { prefsMap["llm_model_name"] ?: "glm-4-flash" }
        every { prefs.edit() } returns prefsEditor
        llmConfig = LLMConfig(prefs)

        // Load test templates into engine directly
        testTemplates.forEach { templateEngine.loadTemplate(it) }

        viewModel = ChatViewModel(llmClient, compositionRepository, templateEngine, templateLoader, llmConfig)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isEmpty() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<ChatMessage>(), state.messages)
            assertNull(state.generatedComposition)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun initialState_showsNotConfigured() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse("Should show not configured when API key is empty", state.isLlmConfigured)
        }
    }

    @Test
    fun sendMessage_blankText_doesNothing() = runTest {
        viewModel.sendMessage("")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(emptyList<ChatMessage>(), state.messages)
    }

    @Test
    fun sendMessage_addsUserMessage() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("Build a lion")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isNotEmpty())
        val userMsg = state.messages.first { it.isUser }
        assertEquals("Build a lion", userMsg.text)
        assertTrue(userMsg.isUser)
    }

    @Test
    fun sendMessage_setsLoadingState_thenClears() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("Test")
        // Loading is set synchronously before async processing
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun sendMessage_llmSuccess_returnsComposition() = runTest {
        val testComposition = Composition(
            id = "test_1",
            name = "Test Object",
            tiles = listOf(
                TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED)
            ),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            source = Composition.Source.AI_GENERATED
        )
        coEvery { llmClient.generateCompositionWithRetry("Build a house") } returns LLMResult.Success(testComposition)

        viewModel.sendMessage("Build a house")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.generatedComposition)
        assertEquals("Test Object", state.generatedComposition!!.name)
        assertFalse(state.isLoading)
    }

    @Test
    fun sendMessage_llmSuccess_savesToRepository() = runTest {
        val testComposition = Composition(
            id = "test_2",
            name = "House",
            tiles = emptyList(),
            createdAt = 0,
            updatedAt = 0,
            source = Composition.Source.AI_GENERATED
        )
        coEvery { llmClient.generateCompositionWithRetry("house") } returns LLMResult.Success(testComposition)

        viewModel.sendMessage("house")
        advanceUntilIdle()

        coVerify { compositionRepository.saveComposition(testComposition) }
    }

    @Test
    fun sendMessage_llmSuccess_addsAiMessage() = runTest {
        val testComposition = Composition(
            id = "test_3",
            name = "Castle",
            tiles = listOf(
                TilePlacement(TileType.SOLID_SQUARE, 0, 0, Rotation.R0, TileColor.RED),
                TilePlacement(TileType.SOLID_SQUARE, 3, 0, Rotation.R0, TileColor.BLUE)
            ),
            createdAt = 0,
            updatedAt = 0,
            source = Composition.Source.AI_GENERATED
        )
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Success(testComposition)

        viewModel.sendMessage("castle")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val aiMessages = state.messages.filter { !it.isUser }
        assertEquals(1, aiMessages.size)
        assertTrue(aiMessages[0].text.contains("castle"))
    }

    @Test
    fun sendMessage_llmError_fallsBackToTemplateKeywordMatching() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("I want a lion")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Should fall back to template keyword matching for 'lion'", state.generatedComposition)
        assertEquals("Lion", state.generatedComposition!!.name)
        assertFalse(state.isLoading)
    }

    @Test
    fun sendMessage_llmError_noKeywordMatch_showsSuggestion() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("I want a zebra")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.generatedComposition)
        val lastMessage = state.messages.last()
        // Should suggest available template names
        assertTrue("Should suggest available templates: ${lastMessage.text}",
            lastMessage.text.contains("Lion") || lastMessage.text.contains("Dog")
                || lastMessage.text.contains("Car") || lastMessage.text.contains("Flower"))
        assertFalse(state.isLoading)
    }

    @Test
    fun clearComposition_clearsGeneratedComposition() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("lion")
        advanceUntilIdle()

        viewModel.clearComposition()

        assertNull(viewModel.uiState.value.generatedComposition)
    }

    @Test
    fun getQuickSuggestions_returnsTemplateNames() {
        val suggestions = viewModel.getQuickSuggestions()
        // Should return template names from loaded templates
        assertTrue("Should have suggestions", suggestions.isNotEmpty())
        assertTrue("Should contain Lion", suggestions.contains("Lion"))
        assertTrue("Should contain Dog", suggestions.contains("Dog"))
    }

    @Test
    fun sendMessage_multipleMessages_maintainsOrder() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("lion")
        advanceUntilIdle()
        viewModel.sendMessage("dog")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Should have at least 4 messages", state.messages.size >= 4)
        assertEquals("lion", state.messages[0].text)
        assertTrue(state.messages[0].isUser)
        assertEquals("dog", state.messages[2].text)
        assertTrue(state.messages[2].isUser)
    }

    @Test
    fun sendMessage_keywordMapCoversTemplates() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        // Test keyword paths via template engine
        val testCases = mapOf(
            "lion" to "Lion",
            "crocodile" to "Crocodile",
            "alligator" to "Crocodile",
            "dog" to "Dog",
            "puppy" to "Dog",
            "car" to "Car",
            "automobile" to "Car",
            "tram" to "Tram",
            "train" to "Tram",
            "flower" to "Flower",
            "rose" to "Flower",
            "plant" to "Flower",
            "cat" to "Cat",
            "house" to "House"
        )

        for ((keyword, expectedName) in testCases) {
            val engine = TemplateEngine()
            testTemplates.forEach { engine.loadTemplate(it) }
            val vm = ChatViewModel(llmClient, compositionRepository, engine, templateLoader, llmConfig)
            vm.sendMessage(keyword)
            advanceUntilIdle()

            assertNotNull("Keyword '$keyword' should match composition", vm.uiState.value.generatedComposition)
            assertEquals("Keyword '$keyword' should map to $expectedName", expectedName, vm.uiState.value.generatedComposition!!.name)
        }
    }

    @Test
    fun sendMessage_templateKeywordMatchingUsesTemplateEngine() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("cat")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("'cat' should match the Cat template", state.generatedComposition)
        assertEquals("Cat", state.generatedComposition!!.name)
    }

    @Test
    fun sendMessage_fallbackReturnsTemplateComposition() = runTest {
        coEvery { llmClient.generateCompositionWithRetry(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("lion")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.generatedComposition)
        // Should have tiles from the template
        assertTrue(state.generatedComposition!!.tiles.isNotEmpty())
    }

    @Test
    fun updateLlmConfig_updatesConfiguredState() = runTest {
        // Initially not configured
        assertFalse(viewModel.uiState.value.isLlmConfigured)

        // Update with valid config
        viewModel.updateLlmConfig("https://api.example.com", "test-key", "test-model")

        // Should now show as configured
        assertTrue(viewModel.uiState.value.isLlmConfigured)
    }

    @Test
    fun getLlmConfig_returnsCurrentValues() {
        val (baseUrl, apiKey, model) = viewModel.getLlmConfig()
        assertEquals("", baseUrl)
        assertEquals("", apiKey)
        assertEquals("glm-4-flash", model)
    }

    @Test
    fun isLlmConfigured_reflectsConfigState() {
        assertFalse(viewModel.isLlmConfigured())
        viewModel.updateLlmConfig("https://api.example.com", "key", "model")
        assertTrue(viewModel.isLlmConfigured())
    }
}
