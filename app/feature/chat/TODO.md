# Chat Screen Implementation TODO

## Overview
This document tracks the implementation of the Chat screen feature.

## Tasks

### UI Components
- [ ] Chat message list (LazyColumn with reversed layout)
- [ ] Message bubbles (user vs AI styling)
- [ ] Text input field with send button
- [ ] Loading indicator during AI generation
- [ ] Error message display

### ViewModel & State
- [ ] ChatViewModel with StateFlow<ChatUiState>
- [ ] Message data class (content, sender, timestamp)
- [ ] UiEvent sealed class (SendMessage, Retry, etc.)
- [ ] Integration with SharedCompositionRepository

### AI Integration
- [ ] LLM client service (OkHttp + Moshi)
- [ ] Prompt engineering template
- [ ] Response parsing to TilePlacement array
- [ ] Constraint validation of AI output
- [ ] Error handling and retry logic

### Navigation
- [ ] Navigate to Build screen with composition
- [ ] Pass generated composition via navigation arguments
- [ ] Handle back navigation from Build screen

### Testing
- [ ] Unit tests for ChatViewModel
- [ ] Unit tests for LLM response parsing
- [ ] UI tests for Chat screen interactions
