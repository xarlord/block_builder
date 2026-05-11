package com.architectai.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.architectai.core.designsystem.color.Accent
import com.architectai.core.designsystem.color.Background
import com.architectai.core.designsystem.color.Header
import com.architectai.core.designsystem.components.AppCard
import com.architectai.core.designsystem.components.MetadataBadge
import com.architectai.core.designsystem.components.SvgPreviewWindow
import com.architectai.core.domain.model.Composition
import com.architectai.core.domain.model.CompositionTemplate
import com.architectai.core.domain.model.Rotation
import com.architectai.core.domain.model.TileColor
import com.architectai.core.domain.model.TilePlacement
import com.architectai.core.domain.model.TileType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onUseInChat: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Library",
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
            // Tab selector
            TabSelector(
                activeTab = uiState.activeTab,
                onTabSelected = { viewModel.setActiveTab(it) }
            )

            when {
                uiState.isLoading -> {
                    LoadingState()
                }
                uiState.error != null -> {
                    ErrorState(uiState.error)
                }
                else -> {
                    when (uiState.activeTab) {
                        LibraryTab.TILES -> LibraryContent(items = uiState.items)
                        LibraryTab.TEMPLATES -> {
                            if (uiState.selectedTemplate != null) {
                                TemplatePreview(
                                    template = uiState.selectedTemplate!!,
                                    onBack = { viewModel.clearTemplateSelection() },
                                    onUseInChat = onUseInChat
                                )
                            } else {
                                TemplateGrid(
                                    templates = uiState.templates,
                                    onTemplateClick = { viewModel.selectTemplate(it) },
                                    onUseInChat = onUseInChat,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSelector(
    activeTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = activeTab == LibraryTab.TILES,
            onClick = { onTabSelected(LibraryTab.TILES) },
            label = { Text("Tiles") }
        )
        FilterChip(
            selected = activeTab == LibraryTab.TEMPLATES,
            onClick = { onTabSelected(LibraryTab.TEMPLATES) },
            label = { Text("Templates") }
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading components...",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ErrorState(error: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = error ?: "An error occurred",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun LibraryContent(items: List<LibraryItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Component Library",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Five core Magnatile components for AI-powered construction",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Component cards
        items(items) { item ->
            LibraryCard(item = item)
        }
    }
}

@Composable
private fun LibraryCard(item: LibraryItem) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            // TODO: Navigate to detail view or placement mode
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SVG preview
            SvgPreviewWindow {
                AsyncImage(
                    model = "file:///android_asset/tiles/${item.tileType.id}.svg",
                    contentDescription = item.tileType.displayName,
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.tileType.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.tileType.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item.tags.forEach { tag ->
                        MetadataBadge(text = tag)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateGrid(
    templates: List<TemplateListItem>,
    onTemplateClick: (CompositionTemplate) -> Unit,
    onUseInChat: ((String) -> Unit)?,
    viewModel: LibraryViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Template Gallery",
                style = MaterialTheme.typography.titleLarge,
                color = Header
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${templates.size} templates available. Tap to preview.",
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
            items(templates) { item ->
                TemplateCard(
                    item = item,
                    onClick = { onTemplateClick(item.template) }
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(
    item: TemplateListItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini canvas preview
            MiniTemplatePreview(
                template = item.template,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5))
            )

            Text(
                text = item.template.name,
                style = MaterialTheme.typography.titleMedium,
                color = Header
            )

            Text(
                text = "${item.tileCount} tiles",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            // Category badge
            Surface(
                shape = RoundedCornerShape(50),
                color = Accent.copy(alpha = 0.1f)
            ) {
                Text(
                    text = item.template.category,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent
                )
            }
        }
    }
}

@Composable
private fun MiniTemplatePreview(
    template: CompositionTemplate,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Simple colored tile grid preview
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val gridWidth = size.width / 30f
            val gridHeight = size.height / 30f

            template.tiles.forEach { tile ->
                val tileColor = parseTileColor(tile.color)
                drawRect(
                    color = tileColor,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        tile.x * gridWidth,
                        tile.y * gridHeight
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        tile.tileType.widthUnits * gridWidth,
                        tile.tileType.heightUnits * gridHeight
                    )
                )
            }
        }
    }
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
        TileColor.WHITE -> Color(0xFFE0E0E0) // Slightly off-white for visibility
        TileColor.TRANSLUCENT -> Color(0xCCFFFFFF) // Semi-transparent
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplatePreview(
    template: CompositionTemplate,
    onBack: () -> Unit,
    onUseInChat: ((String) -> Unit)?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Back button
        item {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(50)
            ) {
                Text("← Back to Gallery")
            }
        }

        // Large preview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Header
                    )
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )

                    // Large preview canvas
                    MiniTemplatePreview(
                        template = template,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF5F5F5))
                    )

                    // Stats
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Accent.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${template.tiles.size} tiles",
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
                                text = template.category,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Accent
                            )
                        }
                    }

                    // Tags
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        template.tags.forEach { tag ->
                            MetadataBadge(text = tag)
                        }
                    }

                    // Use in Chat button
                    Button(
                        onClick = {
                            onUseInChat?.invoke(template.name)
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use in Chat", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
