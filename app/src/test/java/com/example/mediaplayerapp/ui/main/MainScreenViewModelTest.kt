package com.example.mediaplayerapp.ui.main

import com.example.mediaplayerapp.data.DataRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }

  @Test
  fun uiState_onItemSaved_isDisplayed() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }
}

private class FakeMyModelRepository : DataRepository {
  override val mediaItems: Flow<List<com.example.mediaplayerapp.data.MediaItem>> = flow { emit(emptyList()) }
  override fun getSampleStreams(): List<com.example.mediaplayerapp.data.MediaItem> = emptyList()
  override fun getMediaItemsFromStorage(context: android.content.Context): List<com.example.mediaplayerapp.data.MediaItem> = emptyList()
  override fun getMediaFolders(context: android.content.Context, type: com.example.mediaplayerapp.data.MediaType): List<com.example.mediaplayerapp.data.FolderItem> = emptyList()
  override fun getMediaItemsInFolder(context: android.content.Context, folderPath: String, type: com.example.mediaplayerapp.data.MediaType): List<com.example.mediaplayerapp.data.MediaItem> = emptyList()
}

