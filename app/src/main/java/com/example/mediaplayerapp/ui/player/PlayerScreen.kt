package com.example.mediaplayerapp.ui.player

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.example.mediaplayerapp.data.formatDuration
import com.example.mediaplayerapp.service.PlayerManager
import kotlinx.coroutines.delay
import java.io.File

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

    var autoDetectedSubtitles by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
    var externalSubtitles by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var isSubtitlesEnabled by remember { mutableStateOf(true) }
    var isSubtitleDialogOpen by remember { mutableStateOf(false) }
    var subtitleFontScale by remember { mutableFloatStateOf(1.0f) }

    // Auto-detect local subtitles in video directory on startup
    LaunchedEffect(uriString) {
        if (isVideo) {
            val detected = SubtitleHelper.findLocalSubtitles(context, Uri.parse(uriString))
            autoDetectedSubtitles = detected
        }
    }

    val exoPlayer = remember {
        PlayerManager.getPlayer(context).apply {
            clearMediaItems()
            val initialSubs = if (isVideo) SubtitleHelper.findLocalSubtitles(context, Uri.parse(uriString)) else emptyList()

            if (playlistUris.isNotEmpty()) {
                val uris = playlistUris.split(",")
                var startIndex = 0
                var foundIndex = 0
                uris.forEach { uriVal ->
                    val cleanUri = uriVal.trim()
                    if (cleanUri.isNotEmpty()) {
                        if (cleanUri == uriString) {
                            startIndex = foundIndex
                        }
                        foundIndex++
                        val file = File(cleanUri)
                        val itemTitle = file.name.substringBeforeLast(".")
                        val subs = if (cleanUri == uriString && isVideo) initialSubs else emptyList()
                        val item = SubtitleHelper.buildExoMediaItem(
                            Uri.parse(cleanUri),
                            if (itemTitle.isNotEmpty() && !itemTitle.startsWith("http")) itemTitle else title,
                            artistOrSubtitle,
                            subs
                        )
                        addMediaItem(item)
                    }
                }
                prepare()
                if (savedPosition > 0L) {
                    seekTo(startIndex, savedPosition)
                } else {
                    seekTo(startIndex, 0L)
                }
            } else {
                val file = File(uriString)
                val itemTitle = file.name.substringBeforeLast(".")
                val item = SubtitleHelper.buildExoMediaItem(
                    Uri.parse(uriString),
                    if (itemTitle.isNotEmpty() && !itemTitle.startsWith("http")) itemTitle else title,
                    artistOrSubtitle,
                    initialSubs
                )
                addMediaItem(item)
                prepare()
                if (savedPosition > 0L) {
                    seekTo(savedPosition)
                }
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
    var currentTrackTitle by remember { mutableStateOf(title) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            PlayerManager.startService(context)
        }
    }

    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) break
            ctx = ctx.baseContext
        }
        ctx as? ComponentActivity
    }

    // Refresh subtitle tracks from current player state
    fun refreshSubtitleTracks() {
        val tracks = exoPlayer.currentTracks
        val list = mutableListOf<TrackOption>()
        for (gIdx in 0 until tracks.groups.size) {
            val group = tracks.groups[gIdx]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val isSelected = group.isTrackSelected(tIdx)
                    val label = format.label ?: format.language ?: "ترجمة ${list.size + 1}"
                    list.add(TrackOption(gIdx, tIdx, label, format.language, isSelected))
                }
            }
        }
        subtitleTracks = list
        isSubtitlesEnabled = !exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    }

    fun selectSubtitleTrack(track: TrackOption?) {
        if (track == null) {
            // Disable subtitles
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            isSubtitlesEnabled = false
        } else {
            val group = exoPlayer.currentTracks.groups.getOrNull(track.groupIndex)
            if (group != null) {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(track.trackIndex))
                    )
                    .build()
                isSubtitlesEnabled = true
            }
        }
        refreshSubtitleTracks()
    }

    // Launcher for picking custom external subtitle file (.srt, .vtt, .ass, etc.)
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val fileName = getFileNameFromUri(context, selectedUri) ?: "ملف ترجمة مخصص"
            val subItem = SubtitleItem(
                uri = selectedUri,
                name = fileName,
                mimeType = SubtitleHelper.getMimeTypeForUri(selectedUri),
                language = "ar"
            )

            val currentPos = exoPlayer.currentPosition
            val isCurrentlyPlaying = exoPlayer.isPlaying
            val allSubs = (autoDetectedSubtitles + externalSubtitles + subItem).distinctBy { it.uri.toString() }
            externalSubtitles = externalSubtitles + subItem

            val newMediaItem = SubtitleHelper.buildExoMediaItem(
                Uri.parse(uriString),
                currentTrackTitle,
                artistOrSubtitle,
                allSubs
            )

            exoPlayer.setMediaItem(newMediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(currentPos)
            exoPlayer.playWhenReady = isCurrentlyPlaying

            // Enable subtitle track
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()

            Toast.makeText(context, "تمت إضافة ملف الترجمة: $fileName", Toast.LENGTH_SHORT).show()
            refreshSubtitleTracks()
        }
    }

    // Orientation and status bar
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

    // Controls timeout
    LaunchedEffect(isPlaying, areControlsVisible) {
        if (isVideo && isPlaying && areControlsVisible && !isSubtitleDialogOpen) {
            delay(3500)
            areControlsVisible = false
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            areControlsVisible = true
        }
    }

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

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    fun saveProgress(pos: Long, dur: Long) {
        if (pos > 1000L && (dur == 0L || pos < dur - 2000L)) {
            sharedPrefs.edit().putLong("pos_${uriString}", pos).apply()
        } else if (dur > 0L && pos >= dur - 2000L) {
            sharedPrefs.edit().remove("pos_${uriString}").apply()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }

            override fun onPlaybackStateChanged(state: Int) {
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                refreshSubtitleTracks()
            }

            override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (mediaItem != null) {
                    val uriStringVal = mediaItem.localConfiguration?.uri?.toString() ?: ""
                    val file = File(uriStringVal)
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

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            saveProgress(currentPosition, totalDuration)
            delay(1000)
        }
    }

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
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val transparentCaptionStyle = CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                            subtitleView?.apply {
                                setStyle(transparentCaptionStyle)
                                setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleFontScale)
                            }
                        }
                    },
                    update = { playerView ->
                        val transparentCaptionStyle = CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                        playerView.subtitleView?.apply {
                            setStyle(transparentCaptionStyle)
                            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleFontScale)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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

            // Top Action Bar with Title, Back Button, and Subtitles Button
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
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
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (isVideo) {
                            Spacer(modifier = Modifier.width(10.dp))
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

                    if (isVideo) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Subtitle Toggle & Settings Button
                            IconButton(
                                onClick = { isSubtitleDialogOpen = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ClosedCaption,
                                    contentDescription = "الترجمة",
                                    tint = if (isSubtitlesEnabled && subtitleTracks.any { it.isSelected }) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Controls Overlay
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

            // -------------------------------------------------------------
            // SUBTITLE SETTINGS DIALOG
            // -------------------------------------------------------------
            if (isSubtitleDialogOpen) {
                AlertDialog(
                    onDismissRequest = { isSubtitleDialogOpen = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إعدادات الترجمة (Subtitles)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Enable / Disable Subtitles toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSubtitlesEnabled) {
                                            selectSubtitleTrack(null)
                                        } else {
                                            val firstTrack = subtitleTracks.firstOrNull()
                                            selectSubtitleTrack(firstTrack)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تفعيل الترجمة", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Switch(
                                    checked = isSubtitlesEnabled && subtitleTracks.any { it.isSelected },
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            val firstTrack = subtitleTracks.firstOrNull()
                                            selectSubtitleTrack(firstTrack)
                                        } else {
                                            selectSubtitleTrack(null)
                                        }
                                    }
                                )
                            }

                            HorizontalDivider()

                            // Subtitle Tracks List
                            Text("مسارات الترجمة المتاحة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)

                            if (subtitleTracks.isEmpty()) {
                                Text("لم يتم العثور على مسارات ترجمة مدمجة أو محلية.", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .heightIn(max = 160.dp)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(subtitleTracks) { track ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectSubtitleTrack(track) }
                                                .background(
                                                    if (track.isSelected && isSubtitlesEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = track.isSelected && isSubtitlesEnabled,
                                                onClick = { selectSubtitleTrack(track) }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = track.label,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (track.language != null) {
                                                    Text(text = "اللغة: ${track.language}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Button to pick custom subtitle file from storage
                            Button(
                                onClick = {
                                    subtitlePickerLauncher.launch(arrayOf("*/*", "application/x-subrip", "text/vtt", "text/plain"))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اختيار ملف ترجمة من الهاتف...", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp)
                            }

                            HorizontalDivider()

                            // Subtitle Font Size scaling
                            Text("حجم خط الترجمة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = subtitleFontScale == 0.8f,
                                    onClick = { subtitleFontScale = 0.8f },
                                    label = { Text("صغير") }
                                )
                                FilterChip(
                                    selected = subtitleFontScale == 1.0f,
                                    onClick = { subtitleFontScale = 1.0f },
                                    label = { Text("متوسط") }
                                )
                                FilterChip(
                                    selected = subtitleFontScale == 1.3f,
                                    onClick = { subtitleFontScale = 1.3f },
                                    label = { Text("كبير") }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { isSubtitleDialogOpen = false }) {
                            Text("تم")
                        }
                    }
                )
            }
        }
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) return cursor.getString(nameIdx)
                }
            }
        } catch (_: Exception) {}
    }
    return uri.lastPathSegment?.substringAfterLast("/")
}

private fun Long.coerceAtIndexOrLimit(limit: Long): Long {
    return if (this > limit) limit else this
}
