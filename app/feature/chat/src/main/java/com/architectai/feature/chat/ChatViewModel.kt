package com.architectai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.MockCompositions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val generatedComposition: Composition? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * Map of keywords to mock composition IDs for prompt matching.
     * This will be replaced by actual LLM API integration.
     */
    private val keywordMap: Map<String, String> = mapOf(
        "lion" to "mock_lion_001",
        "cat" to "mock_lion_001", // similar to lion
        "crocodile" to "mock_crocodile_001",
        "alligator" to "mock_crocodile_001",
        "dog" to "mock_dog_001",
        "puppy" to "mock_dog_001",
        "car" to "mock_car_001",
        "automobile" to "mock_car_001",
        "tram" to "mock_tram_001",
        "train" to "mock_tram_001",
        "flower" to "mock_flower_001",
        "rose" to "mock_flower_001",
        "plant" to "mock_flower_001"
    )

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message
        val userMessage = ChatMessage(text = text.trim(), isUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true,
            generatedComposition = null,
            error = null
        )

        viewModelScope.launch {
            // Simulate AI processing delay
            delay(800..1500L)

            // Match prompt to mock composition
            val lowerText = text.lowercase().trim()
            val matchedId = keywordMap.entries.firstOrNull { (keyword, _) ->
                lowerText.contains(keyword)
            }?.value

            if (matchedId != null) {
                val composition = MockCompositions.getCompositionById(matchedId)
                if (composition != null) {
                    val aiMessage = ChatMessage(
                        text = "I've created a ${composition.name.lowercase()} composition with ${composition.tiles.size} tiles. " +
                            "It uses ${composition.tiles.map { it.tileType.displayName }.distinct().size} different tile types. " +
                            "Tap \"View on Canvas\" to see it!",
                        isUser = false
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + aiMessage,
                        generatedComposition = composition,
                        isLoading = false
                    )
                } else {
                    sendErrorMessage()
                }
            } else {
                // No match found — helpful response
                val aiMessage = ChatMessage(
                    text = "I can create compositions for: lion, crocodile, dog, car, tram, or flower. " +
                        "Try describing one of those and I'll generate the tile layout!",
                    isUser = false
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isLoading = false
                )
            }
        }
    }

    private fun sendErrorMessage() {
        val errorMessage = ChatMessage(
            text = "Something went wrong generating that composition. Please try again.",
            isUser = false
        )
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + errorMessage,
            isLoading = false,
            error = "Composition not found"
        )
    }

    fun clearComposition() {
        _uiState.value = _uiState.value.copy(generatedComposition = null)
    }

    fun getQuickSuggestions(): List<String> {
        return listOf("Lion", "Dog", "Car", "Flower", "Crocodile", "Tram")
    }

    private suspend fun delay(range: LongRange) {
        delay(range.random())
    }
}
