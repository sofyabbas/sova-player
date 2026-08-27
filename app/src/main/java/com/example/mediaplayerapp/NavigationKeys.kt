package com.example.mediaplayerapp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Main : NavKey

@Serializable
data class PlayerKey(
    val uriString: String,
    val title: String,
    val artistOrSubtitle: String,
    val isVideo: Boolean,
    val playlistUris: String = "" // Comma-separated list of media URIs for sequential playback
) : NavKey
