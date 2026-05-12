package com.architectai.feature.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.architectai.core.data.repository.CompositionRepository
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.TilePlacement
import com.architectai.feature.build.CanvasCapture
import com.architectai.feature.build.CompositionJsonCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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

    // ─── Share / Export ──────────────────────────────────────────────

    /**
     * Share a composition as a PNG image via Intent.
     */
    fun shareCompositionAsImage(context: Context, composition: Composition) {
        if (composition.tiles.isEmpty()) return

        val canvasTiles = composition.tiles.map { placement ->
            com.architectai.feature.build.canvas.CanvasTile(placement)
        }
        val bitmap = CanvasCapture.renderToBitmap(canvasTiles)

        val compositionsDir = File(context.cacheDir, "compositions")
        compositionsDir.mkdirs()
        val imageFile = File(compositionsDir, "${composition.name.replace(" ", "_")}_${System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${composition.name}"))
    }

    /**
     * Share a composition as a JSON file via Intent.
     */
    fun shareCompositionAsJson(context: Context, composition: Composition) {
        if (composition.tiles.isEmpty()) return

        val json = CompositionJsonCodec.exportToJson(composition.name, composition.tiles)
        val compositionsDir = File(context.cacheDir, "compositions")
        compositionsDir.mkdirs()
        val jsonFile = File(compositionsDir, "${composition.name.replace(" ", "_")}_${System.currentTimeMillis()}.json")
        jsonFile.writeText(json)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            jsonFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export ${composition.name} as JSON"))
    }

    /**
     * Parse JSON string and return parsed composition, or null on failure.
     */
    fun parseImportJson(json: String): Pair<String, List<TilePlacement>>? {
        return CompositionJsonCodec.importFromJson(json)
    }
}
