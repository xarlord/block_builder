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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.architectai.core.designsystem.color.Accent
import com.architectai.core.designsystem.color.Header
import com.architectai.core.designsystem.components.AppButton
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

    // Color for new tiles
    var selectedColorForNewTiles by remember { mutableStateOf(TileColor.RED) }

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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveComposition(saveName)
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
                    androidx.compose.material3.TextButton(onClick = { viewModel.clearCanvas() }) {
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Total Tiles: ${canvasState.tiles.size}/200",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Rotation toolbar - show when a tile is selected
                val selectedTile = canvasState.selectedTile
                if (selectedTile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            viewModel.deleteSelectedTile()
                        },
                        text = "Delete",
                        enabled = canvasState.selectedTile != null,
                        modifier = Modifier.weight(1f)
                    )

                    AppButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val randomX = (0..20).random()
                            val randomY = (0..20).random()
                            viewModel.addTile(
                                tileType = TileType.SOLID_SQUARE,
                                x = randomX,
                                y = randomY
                            )
                        },
                        text = "Add Tile",
                        modifier = Modifier.weight(1f)
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
                    modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.size(width = 56.dp, height = 36.dp)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                )
            }
        }
    }
}
