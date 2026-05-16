package com.architectai.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.data.llm.LLMClient
import com.architectai.core.data.llm.LLMConfig
import com.architectai.core.data.llm.LLMResult
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.data.template.TemplateLoader
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.MockCompositions
import com.architectai.core.domain.model.TemplateEngine
import com.architectai.core.domain.model.toComposition
import com.architectai.core.domain.model.TemplateModification
import com.architectai.core.domain.model.TemplateResult
import com.architectai.core.domain.model.TemplateSelectionResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
    val error: String? = null,
    val isLlmConfigured: Boolean = false,
    /** Debug: pixel art processing result with intermediate bitmaps */
    val pixelArtResult: com.architectai.core.data.pixelart.PixelArtResult? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmClient: LLMClient,
    private val compositionRepository: CompositionRepository,
    private val templateEngine: TemplateEngine,
    private val templateLoader: TemplateLoader,
    private val llmConfig: LLMConfig
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var templatesLoaded = false

    init {
        loadTemplates()
        updateConfigState()
    }

    private fun loadTemplates() {
        if (templatesLoaded) return
        try {
            val count = templateLoader.loadAllTemplates()
            templatesLoaded = count > 0
            // Update LLM client with template catalog
            (llmClient as? com.architectai.core.data.llm.OkHttpLLMClient)?.let {
                it.templateCatalog = templateEngine.getCatalogText()
            }
        } catch (_: Exception) {
            templatesLoaded = false
        }
    }

    /** Check if the LLM is configured. */
    fun isLlmConfigured(): Boolean = llmConfig.isConfigured

    /** Get the current config values for the settings UI. */
    fun getLlmConfig(): Triple<String, String, String> {
        return Triple(llmConfig.baseUrl, llmConfig.apiKey, llmConfig.modelName)
    }

    /** Update LLM configuration. */
    fun updateLlmConfig(baseUrl: String, apiKey: String, modelName: String) {
        llmConfig.baseUrl = baseUrl
        llmConfig.apiKey = apiKey
        llmConfig.modelName = modelName
        updateConfigState()
    }

    private fun updateConfigState() {
        _uiState.value = _uiState.value.copy(isLlmConfigured = llmConfig.isConfigured)
    }

    /**
     * Process an image through the pixel-art pipeline.
     * No LLM needed — pure local image processing.
     */
    fun generateFromImage(result: com.architectai.core.data.pixelart.PixelArtResult) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Save the composition
                compositionRepository.saveComposition(result.composition)

                // Update UI state with debug data
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    generatedComposition = result.composition,
                    pixelArtResult = result,
                    messages = _uiState.value.messages + listOf(
                        ChatMessage(
                            text = "Generated pixel art: ${result.objectName} (${result.tileCount} tiles, ${result.colorDistribution.size} colors)",
                            isUser = false
                        )
                    )
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to process image: ${e.message}"
                )
            }
        }
    }

    /**
     * Map of keywords to template IDs for fallback mode.
     * Used when LLM API is unavailable.
     */
    private val keywordMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        // Build from loaded templates
        templateEngine.getAllTemplates().forEach { template ->
            map[template.id.lowercase()] = template.id
            template.tags.forEach { tag ->
                map[tag.lowercase()] = template.id
            }
        }
        // Also include old mock composition IDs for backward compatibility
        map["lion"] = "lion"
        map["cat"] = "cat"
        map["crocodile"] = "crocodile"
        map["alligator"] = "crocodile"
        map["dog"] = "dog"
        map["puppy"] = "dog"
        map["car"] = "car"
        map["automobile"] = "car"
        map["tram"] = "tram"
        map["train"] = "tram"
        map["flower"] = "flower"
        map["rose"] = "flower"
        map["plant"] = "flower"
        map["house"] = "house"
        map["home"] = "house"
        map["tree"] = "tree"
        map["boat"] = "boat"
        map["ship"] = "boat"
        map["sail"] = "boat"
        map["rocket"] = "rocket"
        map["space"] = "rocket"
        map["castle"] = "castle"
        map["fortress"] = "castle"
        map["fish"] = "fish"
        map
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(text = text.trim(), isUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isLoading = true,
            generatedComposition = null,
            error = null
        )

        viewModelScope.launch {
            // Try real LLM API first (with template-based prompting)
            val result = llmClient.generateComposition(text.trim())

            when (result) {
                is LLMResult.Success -> {
                    val composition = result.composition
                    compositionRepository.saveComposition(composition)

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
                }
                is LLMResult.Error -> {
                    // Fall back to template engine with keyword matching
                    handleTemplateFallback(text)
                }
            }
        }
    }

    /**
     * Try to parse LLM response as template selection, otherwise keyword match.
     */
    private suspend fun handleTemplateFallback(text: String) {
        delay((400..800L).random())

        // First, try parsing the text as a template selection JSON
        val selectionResult = tryParseTemplateSelection(text)
        if (selectionResult != null) {
            val aiMessage = ChatMessage(
                text = buildModificationDescription(selectionResult),
                isUser = false
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMessage,
                generatedComposition = selectionResult.first,
                isLoading = false
            )
            return
        }

        // Fall back to keyword matching
        val lowerText = text.lowercase().trim()
        val matchedId = keywordMap.entries.firstOrNull { (keyword, _) ->
            lowerText.contains(keyword)
        }?.value

        if (matchedId != null) {
            val template = templateEngine.getTemplate(matchedId)
            if (template != null) {
                val composition = template.toComposition()
                compositionRepository.saveComposition(composition)
                val aiMessage = ChatMessage(
                    text = "I've created a ${template.name.lowercase()} composition with ${template.tiles.size} tiles. " +
                        "It uses ${template.tiles.map { it.tileType.displayName }.distinct().size} different tile types. " +
                        "Tap \"View on Canvas\" to see it!",
                    isUser = false
                )
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    generatedComposition = composition,
                    isLoading = false
                )
            } else {
                // Fall back to old mock compositions
                handleLegacyKeywordFallback(text)
            }
        } else {
            val availableNames = templateEngine.getAllTemplates().map { it.name }
            val aiMessage = ChatMessage(
                text = "I can create compositions for: ${availableNames.joinToString(", ")}. " +
                    "Try describing one of those and I'll generate the tile layout!",
                isUser = false
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + aiMessage,
                isLoading = false
            )
        }
    }

    private fun tryParseTemplateSelection(text: String): Pair<Composition, List<String>>? {
        return try {
            val result = templateEngine.parseSelectionResponse(text)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                val templateResult = templateEngine.applySelectionResponse(response)
                when (templateResult) {
                    is TemplateResult.Success -> Pair(templateResult.composition, templateResult.appliedModifications)
                    is TemplateResult.Rejected -> {
                        templateResult.partialComposition?.let { Pair(it, listOf("Fell back to unmodified template: ${templateResult.reason}")) }
                    }
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun buildModificationDescription(result: Pair<Composition, List<String>>): String {
        val (composition, modifications) = result
        val base = "I've created a ${composition.name.lowercase()} composition with ${composition.tiles.size} tiles."
        return if (modifications.isNotEmpty()) {
            "$base Modifications applied: ${modifications.joinToString(", ")}. Tap \"View on Canvas\" to see it!"
        } else {
            "$base Tap \"View on Canvas\" to see it!"
        }
    }

    private suspend fun handleLegacyKeywordFallback(text: String) {
        delay((400..800L).random())

        val lowerText = text.lowercase().trim()
        val legacyMap = mapOf(
            "lion" to "mock_lion_001",
            "cat" to "mock_lion_001",
            "crocodile" to "mock_crocodile_001",
            "alligator" to "mock_crocodile_001",
            "dog" to "mock_dog_001",
            "puppy" to "mock_dog_001",
            "car" to "mock_car_001",
            "tram" to "mock_tram_001",
            "flower" to "mock_flower_001"
        )

        val matchedId = legacyMap.entries.firstOrNull { (keyword, _) ->
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
            sendErrorMessage()
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
        val templateNames = templateEngine.getAllTemplates().map { it.name }
        return if (templateNames.isNotEmpty()) {
            templateNames.take(6)
        } else {
            listOf("Lion", "Dog", "Car", "Flower", "Crocodile", "Tram")
        }
    }
}
