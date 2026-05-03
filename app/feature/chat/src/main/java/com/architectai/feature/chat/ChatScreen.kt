package com.architectai.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    onNavigateToBuild: (Composition) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI Architect",
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
                enabled = !uiState.isLoading
            )
        }
    }
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
