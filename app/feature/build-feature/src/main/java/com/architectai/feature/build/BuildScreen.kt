package com.architectai.feature.build

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.architectai.core.designsystem.components.AppButton
import com.architectai.core.domain.model.TileType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    viewModel: BuildViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val canvasState = uiState.canvasState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build Canvas") },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.clearCanvas() }) {
                        Text("Clear", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Total Tiles: ${canvasState.tiles.size}/200",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Count by tile type
                val tileTypeCounts = canvasState.tiles
                    .groupingBy { it.placement.tileType }
                    .eachCount()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        onClick = { viewModel.deleteSelectedTile() },
                        text = "Delete",
                        enabled = canvasState.selectedTile != null,
                        modifier = Modifier.weight(1f)
                    )

                    AppButton(
                        onClick = {
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

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
