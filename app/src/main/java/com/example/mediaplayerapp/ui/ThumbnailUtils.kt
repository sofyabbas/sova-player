package com.example.mediaplayerapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory for thumbnail cache

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun get(key: String): Bitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        if (get(key) == null) {
            memoryCache.put(key, bitmap)
        }
    }
}

suspend fun loadVideoThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    val key = uri.toString()
    ThumbnailCache.get(key)?.let { return@withContext it }

    // 1. Try ContentResolver loadThumbnail for Android 10+ (API 29+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            if (uri.scheme == "content" || uri.scheme == "android.resource") {
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 384), null)
                ThumbnailCache.put(key, bitmap)
                return@withContext bitmap
            }
        } catch (_: Throwable) {
            // Fallback to MediaMetadataRetriever
        }
    }

    // 2. MediaMetadataRetriever as robust fallback
    try {
        val retriever = MediaMetadataRetriever()
        if (uri.scheme == "content" || uri.scheme == "android.resource") {
            retriever.setDataSource(context, uri)
        } else if (uri.scheme == "http" || uri.scheme == "https") {
            retriever.setDataSource(uri.toString(), HashMap())
        } else {
            val path = uri.path ?: uri.toString()
            retriever.setDataSource(path)
        }
        val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
        retriever.release()
        if (bitmap != null) {
            ThumbnailCache.put(key, bitmap)
            return@withContext bitmap
        }
    } catch (_: Throwable) {
        // Thumbnail extraction failed (e.g. unsupported stream format)
    }

    null
}

@Composable
fun VideoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = "Video Thumbnail"
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf(ThumbnailCache.get(uri.toString())) }

    LaunchedEffect(uri) {
        if (bitmap == null) {
            bitmap = loadVideoThumbnail(context, uri)
        }
    }

    Crossfade(targetState = bitmap, label = "thumbnailCrossfade") { bmp ->
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1F1C2C), Color(0xFF302B63))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
