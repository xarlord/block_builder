package com.example.architectai

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.architectai.core.domain.model.Composition
import com.architectai.feature.build.BuildScreen
import com.architectai.feature.build.BuildViewModel
import com.architectai.feature.chat.ChatScreen
import com.architectai.feature.chat.ChatViewModel
import com.architectai.feature.gallery.GalleryScreen
import com.architectai.feature.library.LibraryScreen
import com.example.architectai.onboarding.OnboardingScreen
import com.example.architectai.onboarding.OnboardingViewModel

enum class TabDestination(
    val label: String
) {
    CHAT("Chat"),
    BUILD("Build"),
    LIBRARY("Library"),
    GALLERY("Gallery")
}

@Composable
fun MainNavigation() {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    var showOnboarding by rememberSaveable {
        mutableStateOf(!onboardingViewModel.hasCompletedOnboarding)
    }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = {
                onboardingViewModel.markOnboardingComplete()
                showOnboarding = false
            }
        )
    } else {
        MainAppContent(
            onboardingViewModel = onboardingViewModel
        )
    }
}

@Composable
private fun MainAppContent(
    onboardingViewModel: OnboardingViewModel
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingComposition by rememberSaveable { mutableStateOf<Composition?>(null) }
    var pendingChatMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val sharedBuildViewModel: BuildViewModel = hiltViewModel()
    val chatViewModel: ChatViewModel = hiltViewModel()
    var showHelpDialog by rememberSaveable { mutableStateOf(false) }

    // Handle composition loading when tab switches to BUILD
    androidx.compose.runtime.LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == TabDestination.BUILD.ordinal && pendingComposition != null) {
            val compositionToLoad = pendingComposition!!
            sharedBuildViewModel.loadCompositionDirect(compositionToLoad)
            pendingComposition = null
        }
        if (selectedTabIndex == TabDestination.CHAT.ordinal && pendingChatMessage != null) {
            val message = pendingChatMessage!!
            chatViewModel.sendMessage(message)
            pendingChatMessage = null
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabDestination.entries.forEachIndexed { index, destination ->
                    val isSelected = selectedTabIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Text(destination.label.first().toString())
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { _ ->
        when (TabDestination.entries[selectedTabIndex]) {
            TabDestination.CHAT -> ChatScreen(
                viewModel = chatViewModel,
                onNavigateToBuild = { composition ->
                    pendingComposition = composition
                    selectedTabIndex = TabDestination.BUILD.ordinal
                },
                onShowHelp = { showHelpDialog = true }
            )
            TabDestination.BUILD -> BuildScreen(
                viewModel = sharedBuildViewModel
            )
            TabDestination.LIBRARY -> LibraryScreen(
                onUseInChat = { templateName ->
                    pendingChatMessage = "Build a $templateName"
                    selectedTabIndex = TabDestination.CHAT.ordinal
                }
            )
            TabDestination.GALLERY -> GalleryScreen(
                onLoadToBuild = { composition ->
                    pendingComposition = composition
                    selectedTabIndex = TabDestination.BUILD.ordinal
                }
            )
        }
    }

    // Help dialog to re-trigger onboarding
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help") },
            text = { Text("Would you like to see the tutorial again?") },
            confirmButton = {
                TextButton(onClick = {
                    showHelpDialog = false
                    onboardingViewModel.resetOnboarding()
                }) {
                    Text("Show Tutorial")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
