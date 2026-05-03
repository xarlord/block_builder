package com.example.architectai

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.architectai.core.domain.model.Composition
import com.architectai.feature.build.BuildScreen
import com.architectai.feature.build.BuildViewModel
import com.architectai.feature.chat.ChatScreen
import com.example.architectai.screens.GalleryPlaceholderScreen
import com.example.architectai.screens.LibraryPlaceholderScreen

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
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var pendingComposition by rememberSaveable { mutableStateOf<Composition?>(null) }
    val sharedBuildViewModel = viewModel<BuildViewModel>()

    // Handle composition loading when tab switches to BUILD
    androidx.compose.runtime.LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == TabDestination.BUILD.ordinal && pendingComposition != null) {
            val compositionToLoad = pendingComposition!!
            sharedBuildViewModel.loadCompositionDirect(compositionToLoad)
            pendingComposition = null
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
                onNavigateToBuild = { composition ->
                    pendingComposition = composition
                    selectedTabIndex = TabDestination.BUILD.ordinal
                }
            )
            TabDestination.BUILD -> BuildScreen(
                viewModel = sharedBuildViewModel
            )
            TabDestination.LIBRARY -> LibraryPlaceholderScreen()
            TabDestination.GALLERY -> GalleryPlaceholderScreen()
        }
    }
}
