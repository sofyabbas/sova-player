package com.example.mediaplayerapp.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediaplayerapp.data.DataRepository
import com.example.mediaplayerapp.data.FolderItem
import com.example.mediaplayerapp.data.MediaItem
import com.example.mediaplayerapp.data.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {
    
    private val _storageMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val storageMedia = _storageMedia.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted = _permissionGranted.asStateFlow()

    private val _videoFolders = MutableStateFlow<List<FolderItem>>(emptyList())
    val videoFolders = _videoFolders.asStateFlow()

    private val _audioFolders = MutableStateFlow<List<FolderItem>>(emptyList())
    val audioFolders = _audioFolders.asStateFlow()

    private val _folderContents = MutableStateFlow<List<MediaItem>>(emptyList())
    val folderContents = _folderContents.asStateFlow()

    val uiState: StateFlow<MainScreenUiState> =
        dataRepository.mediaItems
            .map<List<MediaItem>, MainScreenUiState> { MainScreenUiState.Success(it) }
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

    fun checkAndLoadStorage(context: Context) {
        val permissions = getRequiredPermissions()
        val allGranted = permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        _permissionGranted.value = allGranted
        if (allGranted) {
            loadStorageItems(context)
        }
    }

    fun setPermissionGranted(granted: Boolean, context: Context) {
        _permissionGranted.value = granted
        if (granted) {
            loadStorageItems(context)
        }
    }

    fun loadStorageItems(context: Context) {
        viewModelScope.launch {
            _storageMedia.value = dataRepository.getMediaItemsFromStorage(context)
            _videoFolders.value = dataRepository.getMediaFolders(context, MediaType.VIDEO)
            _audioFolders.value = dataRepository.getMediaFolders(context, MediaType.AUDIO)
        }
    }

    fun loadFolderContents(context: Context, folderPath: String, type: MediaType) {
        viewModelScope.launch {
            _folderContents.value = dataRepository.getMediaItemsInFolder(context, folderPath, type)
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: List<MediaItem>) : MainScreenUiState
}
