package com.architectai.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.architectai.core.designsystem.color.Accent
import com.architectai.core.designsystem.color.Background
import com.architectai.core.designsystem.color.Header
import com.architectai.core.domain.model.Composition

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToBuild: (Composition) -> Unit = {},
    onShowHelp: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val listState = rememberLazyListState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pixelArtComposer = remember { com.architectai.core.data.pixelart.PixelArtComposer() }
    val coroutineScope = rememberCoroutineScope()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            android.util.Log.d("PixelArt", "Image picked: $pickedUri")
            // Show loading immediately
            viewModel.setImageProcessing(true)
            // Process bitmap on IO thread to avoid blocking main thread
            coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        pixelArtComposer.processFromUri(context, pickedUri, "Pixel Art")
                    }
                    if (result != null) {
                        android.util.Log.d("PixelArt", "Pipeline success: ${result.tileCount} tiles")
                        viewModel.generateFromImage(result)
                    } else {
                        android.util.Log.e("PixelArt", "processFromUri returned null — bitmap decode failed")
                        viewModel.setImageProcessingError("Failed to load image. Try a different photo.")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PixelArt", "Pipeline error", e)
                    viewModel.setImageProcessingError("Image processing failed: ${e.message}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI Architect",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    IconButton(onClick = onShowHelp) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_info_details),
                            contentDescription = "Help",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_preferences),
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Header,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        // Auto-scroll to bottom when new content arrives (pixel art result, composition, or messages)
        LaunchedEffect(uiState.pixelArtResult, uiState.generatedComposition, uiState.messages.size) {
            if (uiState.pixelArtResult != null || uiState.generatedComposition != null) {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
                .imePadding()
        ) {
            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Welcome message
                item {
                    ChatBubble(
                        text = "Hi! I'm your AI Architect. Describe something you'd like to build — like \"a lion\", \"a car\", or \"a flower\" — and I'll create a tile composition for you.",
                        isUser = false
                    )
                }

                // API setup notice (when not configured)
                item {
                    if (!uiState.isLlmConfigured) {
                        ApiSetupCard(
                            onOpenSettings = { showSettingsDialog = true }
                        )
                    }
                }

                // Chat messages
                items(uiState.messages, key = { it.id }) { message ->
                    ChatBubble(
                        text = message.text,
                        isUser = message.isUser
                    )
                }

                // Loading indicator
                item {
                    AnimatedVisibility(
                        visible = uiState.isLoading,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Accent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Generating composition...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Image processing loading indicator
                item {
                    AnimatedVisibility(
                        visible = uiState.isImageProcessing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Accent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Processing image...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Error display
                item {
                    uiState.error?.let { errorMsg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            )
                        ) {
                            Text(
                                text = "⚠️ $errorMsg",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }

                // Pixel Art Debug Card (3-panel pipeline view)
                item {
                    uiState.pixelArtResult?.let { result ->
                        PixelArtDebugCard(
                            result = result,
                            onViewCanvas = { onNavigateToBuild(result.composition) }
                        )
                    }
                }

                // Generated composition card
                item {
                    uiState.generatedComposition?.let { composition ->
                        CompositionResultCard(
                            composition = composition,
                            onViewCanvas = { onNavigateToBuild(composition) }
                        )
                    }
                }

                // Quick suggestions (shown when no active composition)
                item {
                    if (uiState.generatedComposition == null && uiState.messages.isEmpty()) {
                        QuickSuggestions(
                            suggestions = viewModel.getQuickSuggestions(),
                            onSuggestionClick = { suggestion ->
                                inputText = TextFieldValue(suggestion)
                                viewModel.sendMessage(suggestion)
                                inputText = TextFieldValue("")
                            }
                        )
                    }
                }
            }

            // Input bar
            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    val text = inputText.text.trim()
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(text)
                        inputText = TextFieldValue("")
                    }
                },
                onPickImage = {
                    imagePickerLauncher.launch("image/*")
                },
                enabled = !uiState.isLoading
            )
        }
    }

    // Settings dialog
    if (showSettingsDialog) {
        LlmSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun ApiSetupCard(
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Accent.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚙️ AI Backend Not Configured",
                style = MaterialTheme.typography.titleSmall,
                color = Header
            )
            Text(
                text = "Connect an OpenAI-compatible API to enable AI-generated compositions. " +
                    "For now, template-based matching is used as fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Accent
                )
            ) {
                Text("Configure API")
            }
        }
    }
}

@Composable
private fun LlmSettingsDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val (currentBaseUrl, currentApiKey, currentModel) = viewModel.getLlmConfig()
    var baseUrl by remember { mutableStateOf(currentBaseUrl) }
    var apiKey by remember { mutableStateOf(currentApiKey) }
    var modelName by remember { mutableStateOf(currentModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("AI API Settings")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Configure an OpenAI-compatible API endpoint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.z.ai/api/openai") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name") },
                    placeholder = { Text("glm-4-flash") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateLlmConfig(baseUrl.trim(), apiKey.trim(), modelName.trim())
                    onDismiss()
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ChatBubble(
    text: String,
    isUser: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) Accent.copy(alpha = 0.15f) else Color.White
                )
                .padding(12.dp)
                .width(maxOf(180.dp, 200.dp))
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun CompositionResultCard(
    composition: Composition,
    onViewCanvas: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = composition.name,
                style = MaterialTheme.typography.titleMedium,
                color = Header
            )
            Text(
                text = "${composition.tiles.size} tiles • ${composition.tiles.map { it.tileType }.distinct().size} types",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            // Tile type breakdown
            val typeCounts = composition.tiles.groupingBy { it.tileType.displayName }.eachCount()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                typeCounts.forEach { (name, count) ->
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(50),
                        color = Accent.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "$name ×$count",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent
                        )
                    }
                }
            }

            // View on Canvas button — pill-shaped per design tokens
            androidx.compose.material3.Button(
                onClick = onViewCanvas,
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "View on Canvas",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSuggestions(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Try asking for:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                androidx.compose.material3.OutlinedButton(
                    onClick = { onSuggestionClick(suggestion) },
                    shape = RoundedCornerShape(50),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Header
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.semantics {
                        contentDescription = "Suggestion: $suggestion"
                    }
                ) {
                    Text(suggestion, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit = {},
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Image picker button
        IconButton(
            onClick = onPickImage,
            enabled = enabled,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_gallery),
                contentDescription = "Pick Image",
                tint = if (enabled) Accent
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "Describe what to build...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            },
            shape = RoundedCornerShape(24.dp),
            maxLines = 3,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyMedium
        )

        IconButton(
            onClick = onSend,
            enabled = enabled && value.text.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_send),
                contentDescription = "Send",
                tint = if (enabled && value.text.isNotBlank()) Accent
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * 3-panel debug card showing the pixel art pipeline:
 * 1. Original image (fetched/selected)
 * 2. Pixel art after downsampling + color quantization
 * 3. Tile grid with type indicators
 * + "View on Canvas" button to place tiles
 */
@Composable
private fun PixelArtDebugCard(
    result: com.architectai.core.data.pixelart.PixelArtResult,
    onViewCanvas: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text(
                text = "🎨 Pixel Art Pipeline — ${result.objectName}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            // Stats bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${result.tileCount} tiles",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00D4FF)
                )
                Text(
                    text = "${result.colorDistribution.size} colors",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00D4FF)
                )
            }

            HorizontalDivider(color = Color(0xFF333355))

            // Panel 1: Original Image
            Text(
                text = "① Original Image",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFAAAAFF)
            )
            Image(
                bitmap = result.originalBitmap.asImageBitmap(),
                contentDescription = "Original image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A3E)),
                contentScale = ContentScale.Fit
            )

            HorizontalDivider(color = Color(0xFF333355))

            // Panel 2: Pixel Art (quantized)
            Text(
                text = "② Pixel Art (10×10 quantized)",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFAAAAFF)
            )
            Image(
                bitmap = result.pixelArtBitmap.asImageBitmap(),
                contentDescription = "Pixel art preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A3E)),
                contentScale = ContentScale.Fit
            )

            HorizontalDivider(color = Color(0xFF333355))

            // Panel 3: Tile Grid
            Text(
                text = "③ Tile Grid (shapes + colors)",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFAAAAFF)
            )
            Image(
                bitmap = result.tileGridBitmap.asImageBitmap(),
                contentDescription = "Tile grid preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A3E)),
                contentScale = ContentScale.Fit
            )

            HorizontalDivider(color = Color(0xFF333355))

            // Color distribution
            Text(
                text = "Color Distribution:",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFAAAAFF)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                result.colorDistribution.forEach { (color, count) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(Color(0xFF2A2A3E), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(parseTileColor(color), RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "${color.displayName}: $count",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            // View on Canvas button
            Button(
                onClick = onViewCanvas,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_view),
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("View on Canvas", color = Color.White)
            }
        }
    }
}

/** Parse TileColor hex to Compose Color */
private fun parseTileColor(tileColor: com.architectai.core.domain.model.TileColor): Color {
    val hex = tileColor.hex.removePrefix("#")
    return if (hex.length >= 6) {
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f
        )
    } else Color.White
}
