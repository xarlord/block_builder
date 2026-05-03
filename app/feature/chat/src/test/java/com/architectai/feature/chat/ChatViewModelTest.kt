package com.architectai.feature.chat

import app.cash.turbine.test
import com.architectai.core.data.llm.LLMClient
import com.architectai.core.data.llm.LLMResult
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        llmClient = mockk()
        compositionRepository = mockk(relaxed = true)
        viewModel = ChatViewModel(llmClient, compositionRepository)
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
    fun sendMessage_blankText_doesNothing() = runTest {
        viewModel.sendMessage("")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(emptyList<ChatMessage>(), state.messages)
    }

    @Test
    fun sendMessage_addsUserMessage() = runTest {
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Error("No API")

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
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Error("No API")

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
        coEvery { llmClient.generateComposition("Build a house") } returns LLMResult.Success(testComposition)

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
        coEvery { llmClient.generateComposition("house") } returns LLMResult.Success(testComposition)

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
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Success(testComposition)

        viewModel.sendMessage("castle")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val aiMessages = state.messages.filter { !it.isUser }
        assertEquals(1, aiMessages.size)
        assertTrue(aiMessages[0].text.contains("castle"))
    }

    @Test
    fun sendMessage_llmError_fallsBackToKeywordMatching() = runTest {
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("I want a lion")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull("Should fall back to keyword matching for 'lion'", state.generatedComposition)
        assertEquals("Lion", state.generatedComposition!!.name)
        assertFalse(state.isLoading)
    }

    @Test
    fun sendMessage_llmError_noKeywordMatch_showsSuggestion() = runTest {
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("I want a spaceship")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.generatedComposition)
        val lastMessage = state.messages.last()
        assertTrue("Should contain suggestion keywords",
            lastMessage.text.contains("lion") || lastMessage.text.contains("crocodile"))
        assertFalse(state.isLoading)
    }

    @Test
    fun clearComposition_clearsGeneratedComposition() = runTest {
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Error("No API")

        viewModel.sendMessage("lion")
        advanceUntilIdle()

        viewModel.clearComposition()

        assertNull(viewModel.uiState.value.generatedComposition)
    }

    @Test
    fun getQuickSuggestions_returnsExpectedList() {
        val suggestions = viewModel.getQuickSuggestions()
        assertEquals(listOf("Lion", "Dog", "Car", "Flower", "Crocodile", "Tram"), suggestions)
    }

    @Test
    fun sendMessage_multipleMessages_maintainsOrder() = runTest {
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Error("No API")

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
    fun sendMessage_keywordMapCoversAllMocks() = runTest {
        coEvery { llmClient.generateComposition(any()) } returns LLMResult.Error("No API")

        // Test all keyword paths
        val testCases = mapOf(
            "lion" to "Lion",
            "cat" to "Lion",
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
            "plant" to "Flower"
        )

        for ((keyword, expectedName) in testCases) {
            val vm = ChatViewModel(llmClient, compositionRepository)
            vm.sendMessage(keyword)
            advanceUntilIdle()

            assertNotNull("Keyword '$keyword' should match composition", vm.uiState.value.generatedComposition)
            assertEquals("Keyword '$keyword' should map to $expectedName", expectedName, vm.uiState.value.generatedComposition!!.name)
        }
    }
}
