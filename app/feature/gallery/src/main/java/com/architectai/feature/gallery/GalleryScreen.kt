package com.architectai.feature.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.architectai.core.designsystem.color.Accent
import com.architectai.core.designsystem.color.Background
import com.architectai.core.designsystem.color.Header
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel = hiltViewModel(),
    onLoadToBuild: ((Composition) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedComposition by viewModel.selectedComposition.collectAsState()

    // Detail dialog
    selectedComposition?.let { composition ->
        CompositionDetailDialog(
            composition = composition,
            onDismiss = { viewModel.selectComposition(null) },
            onDelete = {
                viewModel.deleteComposition(composition.id)
                viewModel.selectComposition(null)
            },
            onRename = { newName ->
                viewModel.renameComposition(composition.id, newName)
            },
            onLoadToBuild = {
                onLoadToBuild?.invoke(composition)
                viewModel.selectComposition(null)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Gallery",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Header,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
        ) {
            when (uiState) {
                is GalleryUiState.Loading -> LoadingState()
                is GalleryUiState.Empty -> EmptyState()
                is GalleryUiState.Error -> ErrorState((uiState as GalleryUiState.Error).message)
                is GalleryUiState.Success -> {
                    val compositions = (uiState as GalleryUiState.Success).compositions
                    GalleryGrid(
                        compositions = compositions,
                        onCompositionClick = { viewModel.selectComposition(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Accent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading compositions...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustration placeholder - stylized empty frame
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF0E6E3)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🖼",
                style = MaterialTheme.typography.displayLarge
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Saved Compositions",
            style = MaterialTheme.typography.titleLarge,
            color = Header,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Compositions you save from the Build screen will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun GalleryGrid(
    compositions: List<Composition>,
    onCompositionClick: (Composition) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Saved Compositions",
                style = MaterialTheme.typography.titleLarge,
                color = Header
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${compositions.size} composition${if (compositions.size != 1) "s" else ""} saved",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(compositions, key = { it.id }) { composition ->
                CompositionCard(
                    composition = composition,
                    onClick = { onCompositionClick(composition) }
                )
            }
        }
    }
}

@Composable
private fun CompositionCard(
    composition: Composition,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Composition: ${composition.name}, ${composition.tiles.size} tiles"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini canvas preview
            MiniCompositionPreview(
                tiles = composition.tiles,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
            )

            Text(
                text = composition.name,
                style = MaterialTheme.typography.titleMedium,
                color = Header,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${composition.tiles.size} tiles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            // Source badge
            Surface(
                shape = RoundedCornerShape(50),
                color = Accent.copy(alpha = 0.1f)
            ) {
                Text(
                    text = composition.source.name.replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent
                )
            }
        }
    }
}

@Composable
private fun MiniCompositionPreview(
    tiles: List<TilePlacement>,
    modifier: Modifier = Modifier
) {
    if (tiles.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Empty",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        return
    }

    // Calculate bounds to fit all tiles
    val minX = tiles.minOf { it.x }.toFloat()
    val minY = tiles.minOf { it.y }.toFloat()
    val maxX = tiles.maxOf { it.x + it.tileType.widthUnits }.toFloat()
    val maxY = tiles.maxOf { it.y + it.tileType.heightUnits }.toFloat()
    val contentWidth = maxX - minX
    val contentHeight = maxY - minY

    Canvas(modifier = modifier) {
        val padding = 8f
        val availW = size.width - padding * 2
        val availH = size.height - padding * 2

        val scale = if (contentWidth > 0 && contentHeight > 0) {
            minOf(availW / contentWidth, availH / contentHeight)
        } else 1f

        val offsetX = padding + (availW - contentWidth * scale) / 2f
        val offsetY = padding + (availH - contentHeight * scale) / 2f

        tiles.forEach { tile ->
            val color = parseTileColor(tile.color)
            val left = offsetX + (tile.x - minX) * scale
            val top = offsetY + (tile.y - minY) * scale
            val tileW = tile.tileType.widthUnits * scale
            val tileH = tile.tileType.heightUnits * scale

            drawTileInScope(
                tileType = tile.tileType,
                rotation = tile.rotation,
                color = color,
                left = left,
                top = top,
                width = tileW,
                height = tileH
            )
        }
    }
}

private fun DrawScope.drawTileInScope(
    tileType: TileType,
    rotation: Rotation,
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float
) {
    when (tileType) {
        TileType.SOLID_SQUARE, TileType.WINDOW_SQUARE -> {
            drawRect(color = color, topLeft = Offset(left, top), size = Size(width, height))
            if (tileType == TileType.WINDOW_SQUARE) {
                val inset = width * 0.2f
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left + inset, top + inset),
                    size = Size(width - inset * 2, height - inset * 2)
                )
            }
        }
        TileType.EQUILATERAL_TRIANGLE, TileType.RIGHT_TRIANGLE, TileType.ISOSCELES_TRIANGLE -> {
            val path = Path()
            val cx = left + width / 2f
            val cy = top + height / 2f

            // Define base points relative to center, then rotate
            val points = when (tileType) {
                TileType.EQUILATERAL_TRIANGLE -> listOf(
                    Offset(cx, top),                    // top center
                    Offset(left, top + height),          // bottom left
                    Offset(left + width, top + height)   // bottom right
                )
                TileType.RIGHT_TRIANGLE -> listOf(
                    Offset(left, top),                   // top left
                    Offset(left, top + height),           // bottom left
                    Offset(left + width, top + height)    // bottom right
                )
                TileType.ISOSCELES_TRIANGLE -> listOf(
                    Offset(cx, top),
                    Offset(left, top + height),
                    Offset(left + width, top + height)
                )
            }

            // Apply rotation around center
            val rotatedPoints = when (rotation) {
                Rotation.R0 -> points
                Rotation.R90 -> points.map { rotatePoint(it, cx, cy, 90f) }
                Rotation.R180 -> points.map { rotatePoint(it, cx, cy, 180f) }
                Rotation.R270 -> points.map { rotatePoint(it, cx, cy, 270f) }
            }

            path.moveTo(rotatedPoints[0].x, rotatedPoints[0].y)
            for (i in 1 until rotatedPoints.size) {
                path.lineTo(rotatedPoints[i].x, rotatedPoints[i].y)
            }
            path.close()
            drawPath(path, color = color, style = Fill)
        }
    }
}

private fun rotatePoint(point: Offset, cx: Float, cy: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val dx = point.x - cx
    val dy = point.y - cy
    val cos = kotlin.math.cos(rad)
    val sin = kotlin.math.sin(rad)
    return Offset(
        (cx + dx * cos - dy * sin).toFloat(),
        (cy + dx * sin + dy * cos).toFloat()
    )
}

private fun parseTileColor(tileColor: TileColor): Color {
    return when (tileColor) {
        TileColor.RED -> Color(0xFFA04523)
        TileColor.ORANGE -> Color(0xFFF18D58)
        TileColor.YELLOW -> Color(0xFFF5C542)
        TileColor.GREEN -> Color(0xFF4CAF50)
        TileColor.BLUE -> Color(0xFF2196F3)
        TileColor.PURPLE -> Color(0xFF9C27B0)
        TileColor.PINK -> Color(0xFFE91E63)
        TileColor.BROWN -> Color(0xFF8D6E63)
        TileColor.BLACK -> Color(0xFF000000)
        TileColor.WHITE -> Color(0xFFE0E0E0)
        TileColor.TRANSLUCENT -> Color(0xCCFFFFFF)
    }
}

@Composable
private fun CompositionDetailDialog(
    composition: Composition,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onLoadToBuild: () -> Unit
) {
    var showRenameField by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(composition.name) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = composition.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Header
                )

                // Date
                val dateStr = try {
                    SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        .format(Date(composition.updatedAt))
                } catch (_: Exception) { "Unknown" }
                Text(
                    text = "Last modified: $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )

                // Preview
                MiniCompositionPreview(
                    tiles = composition.tiles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                )

                // Stats
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Accent.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${composition.tiles.size} tiles",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Accent
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Accent.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = composition.source.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Accent
                        )
                    }
                }

                // Rename section
                if (showRenameField) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = renameValue,
                            onValueChange = { renameValue = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        TextButton(
                            onClick = {
                                if (renameValue.isNotBlank()) {
                                    onRename(renameValue.trim())
                                    showRenameField = false
                                }
                            }
                        ) {
                            Text("Save", color = Accent)
                        }
                    }
                }

                // Delete confirmation
                if (showDeleteConfirm) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Delete this composition?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = false },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = onDelete,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }

                // Action buttons
                if (!showRenameField && !showDeleteConfirm) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRenameField = true },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Rename")
                        }
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete")
                        }
                    }

                    // Load to Build
                    Button(
                        onClick = onLoadToBuild,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open in Build", style = MaterialTheme.typography.labelLarge)
                    }

                    // Close
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
