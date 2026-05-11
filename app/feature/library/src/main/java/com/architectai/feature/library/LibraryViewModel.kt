package com.architectai.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.data.template.TemplateLoader
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.CompositionTemplate
import com.architectai.core.domain.model.TemplateEngine
import com.architectai.core.domain.model.TileType
import com.architectai.core.domain.model.toComposition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryItem(
    val tileType: TileType,
    val tags: List<String>
)

data class TemplateListItem(
    val template: CompositionTemplate,
    val tileCount: Int,
    val colorSummary: String
)

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val templates: List<TemplateListItem> = emptyList(),
    val selectedTemplate: CompositionTemplate? = null,
    val previewComposition: Composition? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeTab: LibraryTab = LibraryTab.TILES
)

enum class LibraryTab {
    TILES,
    TEMPLATES
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val templateEngine: TemplateEngine,
    private val templateLoader: TemplateLoader
) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibraryItems()
    }

    private fun loadLibraryItems() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true)

            try {
                // Load tile items
                val items = TileType.entries.map { tileType ->
                    LibraryItem(
                        tileType = tileType,
                        tags = getTagsForTile(tileType)
                    )
                }

                // Load templates
                val templateCount = templateLoader.loadAllTemplates()
                val templates = templateEngine.getAllTemplates().map { template ->
                    val colors = template.tiles.map { it.color.displayName }.distinct()
                    TemplateListItem(
                        template = template,
                        tileCount = template.tiles.size,
                        colorSummary = colors.joinToString(", ")
                    )
                }

                _uiState.value = LibraryUiState(
                    items = items,
                    templates = templates,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = LibraryUiState(
                    error = "Failed to load library: ${e.message}"
                )
            }
        }
    }

    fun selectTemplate(template: CompositionTemplate?) {
        val preview = template?.toComposition()
        _uiState.value = _uiState.value.copy(
            selectedTemplate = template,
            previewComposition = preview
        )
    }

    fun clearTemplateSelection() {
        _uiState.value = _uiState.value.copy(
            selectedTemplate = null,
            previewComposition = null
        )
    }

    fun setActiveTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun getTemplateChatText(template: CompositionTemplate): String {
        return "Build a ${template.name.lowercase()}"
    }

    private fun getTagsForTile(tileType: TileType): List<String> {
        return when (tileType) {
            TileType.SOLID_SQUARE -> listOf("Structural", "Base")
            TileType.WINDOW_SQUARE -> listOf("Decorative", "Translucent")
            TileType.EQUILATERAL_TRIANGLE -> listOf("60°", "Pattern")
            TileType.RIGHT_TRIANGLE -> listOf("90°", "Corner")
            TileType.ISOSCELES_TRIANGLE -> listOf("Spire", "Rooftop")
        }
    }
}
