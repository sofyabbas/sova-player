package com.example.mediaplayerapp.ui.player

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.mediaplayerapp.service.PlayerManager
import com.example.mediaplayerapp.data.formatDuration
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    uriString: String,
    title: String,
    artistOrSubtitle: String,
    isVideo: Boolean,
    playlistUris: String = "",
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("media_player_recents", Context.MODE_PRIVATE) }
    val savedPosition = remember(uriString) {
        sharedPrefs.getLong("pos_${uriString}", 0L)
    }

    val exoPlayer = remember {
        PlayerManager.getPlayer(context).apply {
            clearMediaItems()
            if (playlistUris.isNotEmpty()) {
                val uris = playlistUris.split(",")
                uris.forEach { uriVal ->
                    val cleanUri = uriVal.trim()
                    if (cleanUri.isNotEmpty()) {
                        val file = java.io.File(cleanUri)
                        val itemTitle = file.name.substringBeforeLast(".")
                        val item = ExoMediaItem.Builder()
                            .setUri(Uri.parse(cleanUri))
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder()
                                    .setTitle(if (itemTitle.isNotEmpty() && !itemTitle.startsWith("http")) itemTitle else title)
                                    .setArtist(artistOrSubtitle)
                                    .build()
                            )
                            .build()
                        addMediaItem(item)
                    }
                }
            } else {
                val file = java.io.File(uriString)
                val itemTitle = file.name.substringBeforeLast(".")
                val item = ExoMediaItem.Builder()
                    .setUri(Uri.parse(uriString))
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle(if (itemTitle.isNotEmpty() && !itemTitle.startsWith("http")) itemTitle else title)
                            .setArtist(artistOrSubtitle)
                            .build()
                    )
                    .build()
                addMediaItem(item)
            }
            prepare()
            if (savedPosition > 0L) {
                seekTo(savedPosition)
            }
            playWhenReady = true
            PlayerManager.startService(context)
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    var areControlsVisible by remember { mutableStateOf(true) }

    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) break
            ctx = ctx.baseContext
        }
        ctx as? ComponentActivity
    }

    // Toggle orientation and status bar when entering/exiting fullscreen
    LaunchedEffect(isFullscreen) {
        if (isVideo && activity != null) {
            val window = activity.window
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // Controls visibility auto-hide timeout when playing video
    LaunchedEffect(isPlaying, areControlsVisible) {
        if (isVideo && isPlaying && areControlsVisible) {
            delay(3500)
            areControlsVisible = false
        }
    }

    // Show controls automatically if video is paused
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            areControlsVisible = true
        }
    }

    // Keep screen on during video playback
    DisposableEffect(isPlaying) {
        if (isVideo && isPlaying && activity != null) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (isVideo && activity != null) {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Reset orientation on screen exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    var currentTrackTitle by remember { mutableStateOf(title) }

    // Helper to save playback position and update recents list in SharedPreferences
    fun saveProgress(pos: Long, dur: Long) {
        if (pos > 1000L && (dur == 0L || pos < dur - 2000L)) {
            // Save position if not at the very end
            sharedPrefs.edit().putLong("pos_${uriString}", pos).apply()
        } else if (dur > 0L && pos >= dur - 2000L) {
            // If near end, reset to 0
            sharedPrefs.edit().remove("pos_${uriString}").apply()
        }
    }

    // Keep track of playback status and media changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }

            override fun onPlaybackStateChanged(state: Int) {
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (mediaItem != null) {
                    val uriStringVal = mediaItem.localConfiguration?.uri?.toString() ?: ""
                    val file = java.io.File(uriStringVal)
                    currentTrackTitle = file.name.substringBeforeLast(".")
                    if (currentTrackTitle.isEmpty() || currentTrackTitle.startsWith("http")) {
                        currentTrackTitle = mediaItem.mediaMetadata.title?.toString() ?: title
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            saveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.removeListener(listener)
        }
    }

    // Monitor progress and periodically persist position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            saveProgress(currentPosition, totalDuration)
            delay(1000)
        }
    }

    // Audio Visualizer/Disk Rotation Animation
    val infiniteTransition = rememberInfiniteTransition(label = "diskRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .clickable {
                    if (isVideo) {
                        areControlsVisible = !areControlsVisible
                    }
                }
        ) {
            if (isVideo) {
                // Video Player surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Audio Player UI
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F2027),
                                    Color(0xFF203A43),
                                    Color(0xFF2C5364)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Glassmorphic Audio Disk Visual
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .rotate(if (isPlaying) rotationAngle else 0f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF00BCD4),
                                                Color(0xFF2196F3),
                                                Color(0x002196F3)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Audio disk",
                                    modifier = Modifier.size(60.dp),
                                    colorFilter = ColorFilter.tint(Color.White)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = currentTrackTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp).basicMarquee()
                        )
                        Text(
                            text = artistOrSubtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp).basicMarquee()
                        )
                    }
                }
            }

            // Top action bar (Back button) - hides when controls hide
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = {
                            saveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
                            exoPlayer.pause()
                            PlayerManager.release()
                            onBackClick()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (isVideo) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.widthIn(max = 200.dp)) {
                            Text(
                                text = currentTrackTitle,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }
            }

            // Controls overlay - Hides on play, shows on pause or click
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Slider / Seek Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = currentPosition.toFloat(),
                            valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                            onValueChange = { newValue ->
                                currentPosition = newValue.toLong()
                                exoPlayer.seekTo(currentPosition)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = formatDuration(totalDuration),
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Control panel buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playback Speed Controller button
                        IconButton(
                            onClick = {
                                playbackSpeed = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.5f
                                    else -> 1.0f
                                }
                                exoPlayer.setPlaybackSpeed(playbackSpeed)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        // Previous track button (SkipPrevious)
                        IconButton(
                            onClick = {
                                if (exoPlayer.hasPreviousMediaItem()) {
                                    exoPlayer.seekToPreviousMediaItem()
                                } else {
                                    exoPlayer.seekTo(0L)
                                    currentPosition = 0L
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Seek backward 10 seconds
                        IconButton(
                            onClick = {
                                val seekTo = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                exoPlayer.seekTo(seekTo)
                                currentPosition = seekTo
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play/Pause button with custom rounded frame
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Seek forward 10 seconds
                        IconButton(
                            onClick = {
                                val seekTo = (exoPlayer.currentPosition + 10000).coerceAtIndexOrLimit(totalDuration)
                                exoPlayer.seekTo(seekTo)
                                currentPosition = seekTo
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Fast forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Next track button (SkipNext)
                        IconButton(
                            onClick = {
                                if (exoPlayer.hasNextMediaItem()) {
                                    exoPlayer.seekToNextMediaItem()
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Fullscreen screen layout aspect-ratio scale adjustment toggle
                        IconButton(
                            onClick = {
                                if (isVideo) {
                                    isFullscreen = !isFullscreen
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White.copy(alpha = if (isVideo) 1.0f else 0.3f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helper to restrict bounds
private fun Long.coerceAtIndexOrLimit(limit: Long): Long {
    return if (this > limit) limit else this
}
