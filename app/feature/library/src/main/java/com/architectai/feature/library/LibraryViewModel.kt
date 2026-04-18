package com.architectai.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.domain.model.TileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryItem(
    val tileType: TileType,
    val tags: List<String>
)

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class LibraryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibraryItems()
    }

    private fun loadLibraryItems() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true)

            try {
                val items = TileType.entries.map { tileType ->
                    LibraryItem(
                        tileType = tileType,
                        tags = getTagsForTile(tileType)
                    )
                }

                _uiState.value = LibraryUiState(items = items)
            } catch (e: Exception) {
                _uiState.value = LibraryUiState(
                    error = "Failed to load library: ${e.message}"
                )
            }
        }
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
