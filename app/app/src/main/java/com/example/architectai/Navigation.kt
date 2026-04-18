package com.example.architectai

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.architectai.screens.BuildPlaceholderScreen
import com.example.architectai.screens.ChatPlaceholderScreen
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabDestination.entries.forEachIndexed { index, destination ->
                    val isSelected = selectedTabIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTabIndex = index
                        },
                        icon = {
                            Text(destination.label.first().toString())
                        },
                        label = {
                            Text(destination.label)
                        }
                    )
                }
            }
        }
    ) { _ ->
        when (TabDestination.entries[selectedTabIndex]) {
            TabDestination.CHAT -> ChatPlaceholderScreen()
            TabDestination.BUILD -> BuildPlaceholderScreen()
            TabDestination.LIBRARY -> LibraryPlaceholderScreen()
            TabDestination.GALLERY -> GalleryPlaceholderScreen()
        }
    }
}
