package com.architectai.feature.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.domain.model.Composition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class GalleryUiState {
    data object Loading : GalleryUiState()
    data class Success(val compositions: List<Composition>) : GalleryUiState()
    data object Empty : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val compositionRepository: CompositionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.Loading)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _selectedComposition = MutableStateFlow<Composition?>(null)
    val selectedComposition: StateFlow<Composition?> = _selectedComposition.asStateFlow()

    init {
        loadCompositions()
    }

    private fun loadCompositions() {
        viewModelScope.launch {
            try {
                compositionRepository.getAllCompositions().collect { compositions ->
                    _uiState.value = when {
                        compositions.isEmpty() -> GalleryUiState.Empty
                        else -> GalleryUiState.Success(compositions)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = GalleryUiState.Error(
                    e.message ?: "Failed to load compositions"
                )
            }
        }
    }

    fun deleteComposition(id: String) {
        viewModelScope.launch {
            try {
                compositionRepository.deleteComposition(id)
                if (_selectedComposition.value?.id == id) {
                    _selectedComposition.value = null
                }
            } catch (_: Exception) {
                // Silently handle — the Flow will emit updated list
            }
        }
    }

    fun renameComposition(id: String, newName: String) {
        viewModelScope.launch {
            try {
                compositionRepository.renameComposition(id, newName)
                // Update selected if it's the one being renamed
                _selectedComposition.value?.let { selected ->
                    if (selected.id == id) {
                        _selectedComposition.value = selected.copy(
                            name = newName,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
            } catch (_: Exception) {
                // Silently handle
            }
        }
    }

    fun selectComposition(composition: Composition?) {
        _selectedComposition.value = composition
    }
}
