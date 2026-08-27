package com.example.mediaplayerapp.data

import android.net.Uri

enum class MediaType {
    AUDIO, VIDEO
}

data class MediaItem(
    val id: Long,
    val title: String,
    val artistOrSubtitle: String,
    val uri: Uri,
    val duration: Long,
    val size: Long,
    val type: MediaType
)
