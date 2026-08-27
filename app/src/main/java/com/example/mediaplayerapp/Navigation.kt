package com.example.mediaplayerapp

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.mediaplayerapp.ui.main.MainScreen
import com.example.mediaplayerapp.ui.player.PlayerScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.safeDrawingPadding())
        }
        entry<PlayerKey> { key ->
          PlayerScreen(
            uriString = key.uriString,
            title = key.title,
            artistOrSubtitle = key.artistOrSubtitle,
            isVideo = key.isVideo,
            onBackClick = { backStack.removeLastOrNull() }
          )
        }
      },
  )
}
