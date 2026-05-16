package com.architectai.feature.build

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.architectai.core.designsystem.color.Accent
import com.architectai.core.designsystem.color.Header
import com.architectai.core.designsystem.components.AppButton
import com.architectai.core.designsystem.sound.LocalSoundEffectManager
import com.architectai.core.designsystem.sound.SoundEffectManager
import com.architectai.core.designsystem.sound.SoundEffectPlayer
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TileType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    viewModel: BuildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val canvasState = uiState.canvasState
    val snackbarHostState = remember { SnackbarHostState() }
    val view = LocalView.current
    val context = LocalContext.current

    // Sound effects manager — initialised once, released when the composable leaves composition
    val soundEffectPlayer = remember {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(PREF_KEY_SOUND_ENABLED, true)
        val manager = SoundEffectManager(context)
        manager.init()
        manager.setEnabled(enabled)
        manager
    }
    // Restore sound preference on recomposition
    var soundEnabled by remember {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        mutableStateOf(prefs.getBoolean(PREF_KEY_SOUND_ENABLED, true))
    }

    // Color for new tiles
    var selectedColorForNewTiles by remember { mutableStateOf(TileColor.RED) }

    // Max tile count — user-adjustable
    var maxTiles by remember { mutableStateOf(200) }

    // Show save confirmation as a snackbar
    uiState.saveConfirmation?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearSaveConfirmation()
        }
    }

    // Save dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save to Gallery") },
            text = {
                Column {
                    Text(
                        "Save your current composition with ${canvasState.tiles.size} tiles.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Composition name") },
                        placeholder = { Text("My Composition") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = "saveNameField" },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveComposition(saveName)
                        soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.COMPOSITION_DONE)
                        saveName = ""
                        showSaveDialog = false
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White
                    )
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showSaveDialog = false
                        saveName = ""
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build Canvas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Header,
                    titleContentColor = Color.White
                ),
                actions = {
                    // Sound toggle
                    IconButton(
                        onClick = {
                            soundEnabled = !soundEnabled
                            soundEffectPlayer.setEnabled(soundEnabled)
                            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                            prefs.edit().putBoolean(PREF_KEY_SOUND_ENABLED, soundEnabled).apply()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (soundEnabled) "Disable sound effects" else "Enable sound effects"
                            testTag = "soundToggle"
                        }
                    ) {
                        Icon(
                            imageVector = if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (soundEnabled) "Sound on" else "Sound off",
                            tint = Color.White
                        )
                    }
                    // Undo button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.BUTTON_PRESS)
                            viewModel.undo()
                        },
                        enabled = uiState.canUndo,
                        modifier = Modifier.semantics {
                            contentDescription = "Undo last action"
                            testTag = "undoButton"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.canUndo) Color.White else Color.White.copy(alpha = 0.38f)
                        )
                    }
                    // Redo button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.BUTTON_PRESS)
                            viewModel.redo()
                        },
                        enabled = uiState.canRedo,
                        modifier = Modifier.semantics {
                            contentDescription = "Redo last action"
                            testTag = "redoButton"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.canRedo) Color.White else Color.White.copy(alpha = 0.38f)
                        )
                    }
                    // Share button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.BUTTON_PRESS)
                            viewModel.shareComposition(context)
                        },
                        enabled = canvasState.tiles.isNotEmpty(),
                        modifier = Modifier.semantics {
                            contentDescription = "Share composition as image"
                            testTag = "shareButton"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = if (canvasState.tiles.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.38f)
                        )
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.BUTTON_PRESS)
                            viewModel.clearCanvas()
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Clear all tiles from canvas"
                            testTag = "clearButton"
                        }
                    ) {
                        Text("Clear", color = Color.White)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Canvas area
            BuildCanvas(
                viewModel = viewModel,
                soundEffectPlayer = soundEffectPlayer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { testTag = "buildCanvas" }
            )

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tile count with adjustable max via slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tiles: ${canvasState.tiles.size}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics {
                                contentDescription = "${canvasState.tiles.size} tiles placed"
                                testTag = "tileCount"
                            }
                        )
                        Text(
                            text = "Max: $maxTiles",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (canvasState.tiles.size >= maxTiles) Accent
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.semantics {
                                contentDescription = "Maximum $maxTiles tiles allowed"
                                testTag = "maxTileCount"
                            }
                        )
                    }
                    Slider(
                        value = maxTiles.toFloat(),
                        onValueChange = { maxTiles = it.toInt() },
                        valueRange = 10f..200f,
                        steps = (200 - 10) / 10 - 1, // steps at 20, 30, ..., 190
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = "maxTilesSlider" },
                        enabled = canvasState.tiles.isEmpty(), // only adjust when canvas is empty
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            activeTrackColor = Accent,
                            thumbColor = Accent
                        )
                    )
                    if (canvasState.tiles.isNotEmpty()) {
                        Text(
                            text = "Clear canvas to adjust max tiles",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        )
                    }
                }

                // Rotation toolbar - show when a tile is selected
                val selectedTile = canvasState.selectedTile
                if (selectedTile != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { testTag = "rotationToolbar" },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rotation:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        RotationButton("0°", Rotation.R0, selectedTile.placement.rotation, selectedTile.id, viewModel, view)
                        RotationButton("90°", Rotation.R90, selectedTile.placement.rotation, selectedTile.id, viewModel, view)
                        RotationButton("180°", Rotation.R180, selectedTile.placement.rotation, selectedTile.id, viewModel, view)
                        RotationButton("270°", Rotation.R270, selectedTile.placement.rotation, selectedTile.id, viewModel, view)
                    }
                }

                // Color picker row - show when tile is selected or always for new tiles
                ColorPickerRow(
                    selectedColor = selectedTile?.placement?.color ?: selectedColorForNewTiles,
                    onColorSelected = { color ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.COLOR_SELECT)
                        if (selectedTile != null) {
                            viewModel.updateTileColor(selectedTile.id, color)
                        } else {
                            selectedColorForNewTiles = color
                        }
                    }
                )

                // Count by tile type
                val tileTypeCounts = canvasState.tiles
                    .groupingBy { it.placement.tileType }
                    .eachCount()

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    tileTypeCounts.forEach { (tileType, count) ->
                        Text(
                            text = "• ${tileType.displayName}: $count",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppButton(
                        onClick = {
                            // --- Task 3: Enhanced haptics — CONFIRM for delete ---
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.TILE_DELETE)
                            viewModel.deleteSelectedTile()
                        },
                        text = "Delete",
                        enabled = canvasState.selectedTile != null,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Delete selected tile"
                                testTag = "deleteButton"
                            }
                    )

                    AppButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            soundEffectPlayer.play(SoundEffectPlayer.SoundEffect.BUTTON_PRESS)
                            val randomX = (0..20).random()
                            val randomY = (0..20).random()
                            viewModel.addTile(
                                tileType = TileType.SOLID_SQUARE,
                                x = randomX,
                                y = randomY
                            )
                        },
                        text = "Add Tile",
                        enabled = canvasState.tiles.size < maxTiles,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Add a new tile to the canvas"
                                testTag = "addTileButton"
                            }
                    )
                }

                // Save to Gallery button
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        showSaveDialog = true
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Save composition to gallery"
                            testTag = "saveToGalleryButton"
                        },
                    enabled = canvasState.tiles.isNotEmpty()
                ) {
                    Text("Save to Gallery", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RotationButton(
    label: String,
    rotation: Rotation,
    currentRotation: Rotation,
    tileId: String,
    viewModel: BuildViewModel,
    view: android.view.View
) {
    val isSelected = currentRotation == rotation
    val containerColor = if (isSelected) Accent else Color.Transparent
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground

    OutlinedButton(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            viewModel.updateTileRotation(tileId, rotation)
        },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .size(width = 56.dp, height = 36.dp)
            .semantics {
                contentDescription = "Set rotation to $label"
                testTag = "rotation_$label"
            }
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ColorPickerRow(
    selectedColor: TileColor,
    onColorSelected: (TileColor) -> Unit
) {
    Column {
        Text(
            text = if (selectedColor != null) "Color: ${selectedColor.displayName}" else "Color",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { testTag = "colorPicker" }
        ) {
            // Exclude TRANSLUCENT from the picker - it's implicitly for WINDOW_SQUARE
            val pickableColors = TileColor.entries.filter { it != TileColor.TRANSLUCENT }
            items(pickableColors) { tileColor ->
                val color = try {
                    Color(android.graphics.Color.parseColor(tileColor.hex))
                } catch (_: IllegalArgumentException) {
                    Color.Gray
                }
                val isSelected = tileColor == selectedColor
                // --- Task 5: Minimum touch target size 48dp ---
                Box(
                    modifier = Modifier
                        .size(48.dp) // touch target
                        .padding(6.dp) // padding so visual circle is 36dp
                        .size(36.dp) // visual circle size
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = Accent,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color.Gray.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                            }
                        )
                        .clickable { onColorSelected(tileColor) }
                        // --- Task 4: Accessibility content description ---
                        .semantics {
                            contentDescription = "Select ${tileColor.displayName} color"
                        }
                )
            }
        }
    }
}

/** SharedPreferences keys for sound settings. */
private const val PREFS_NAME = "sound_effects_prefs"
private const val PREF_KEY_SOUND_ENABLED = "sound_enabled"
