package com.example.mediaplayerapp.ui.main

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.mediaplayerapp.PlayerKey
import com.example.mediaplayerapp.data.DefaultDataRepository
import com.example.mediaplayerapp.data.FolderItem
import com.example.mediaplayerapp.data.MediaDetails
import com.example.mediaplayerapp.data.MediaItem
import com.example.mediaplayerapp.data.MediaType
import com.example.mediaplayerapp.data.formatDuration
import com.example.mediaplayerapp.ui.VideoThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val storageItems by viewModel.storageMedia.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Video, 1 = Music, 2 = Playlist

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        viewModel.setPermissionGranted(allGranted, context)
    }

    LaunchedEffect(Unit) {
        viewModel.checkAndLoadStorage(context)
    }

    // Navigation state inside storage folders
    var activeVideoFolder by remember { mutableStateOf<FolderItem?>(null) }
    var activeAudioFolder by remember { mutableStateOf<FolderItem?>(null) }

    val videoFolders by viewModel.videoFolders.collectAsStateWithLifecycle()
    val audioFolders by viewModel.audioFolders.collectAsStateWithLifecycle()
    val folderContents by viewModel.folderContents.collectAsStateWithLifecycle()

    val recentsSharedPrefs = remember { context.getSharedPreferences("media_player_recents", Context.MODE_PRIVATE) }
    val playlistsSharedPrefs = remember { context.getSharedPreferences("media_player_playlists", Context.MODE_PRIVATE) }

    // Helpers for recents
    fun loadRecentMedia(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val dataStr = recentsSharedPrefs.getString("recent_media_list", null) ?: return list
        try {
            val jsonArr = org.json.JSONArray(dataStr)
            for (i in 0 until jsonArr.length()) {
                val itemObj = jsonArr.getJSONObject(i)
                val id = itemObj.getLong("id")
                val title = itemObj.getString("title")
                val artist = itemObj.getString("artist")
                val uriStr = itemObj.getString("uri")
                val duration = itemObj.getLong("duration")
                val size = itemObj.getLong("size")
                val type = MediaType.valueOf(itemObj.getString("type"))
                list.add(MediaItem(id, title, artist, Uri.parse(uriStr), duration, size, type))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveRecentMedia(item: MediaItem) {
        try {
            val currentList = loadRecentMedia().toMutableList()
            currentList.removeAll { it.uri.toString() == item.uri.toString() }
            currentList.add(0, item)
            val trimmed = currentList.take(20)
            val jsonArr = org.json.JSONArray()
            trimmed.forEach { itm ->
                val itemObj = org.json.JSONObject()
                itemObj.put("id", itm.id)
                itemObj.put("title", itm.title)
                itemObj.put("artist", itm.artistOrSubtitle)
                itemObj.put("uri", itm.uri.toString())
                itemObj.put("duration", itm.duration)
                itemObj.put("size", itm.size)
                itemObj.put("type", itm.type.name)
                jsonArr.put(itemObj)
            }
            recentsSharedPrefs.edit().putString("recent_media_list", jsonArr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var recentMediaItems by remember(storageItems) { mutableStateOf(loadRecentMedia()) }

    // Helpers for playlists
    fun savePlaylistsToPrefs(playlists: List<Pair<String, List<MediaItem>>>) {
        try {
            val jsonArr = org.json.JSONArray()
            playlists.forEach { (name, items) ->
                val playlistObj = org.json.JSONObject()
                playlistObj.put("name", name)
                val itemsArr = org.json.JSONArray()
                items.forEach { item ->
                    val itemObj = org.json.JSONObject()
                    itemObj.put("id", item.id)
                    itemObj.put("title", item.title)
                    itemObj.put("artist", item.artistOrSubtitle)
                    itemObj.put("uri", item.uri.toString())
                    itemObj.put("duration", item.duration)
                    itemObj.put("size", item.size)
                    itemObj.put("type", item.type.name)
                    itemsArr.put(itemObj)
                }
                playlistObj.put("items", itemsArr)
                jsonArr.put(playlistObj)
            }
            playlistsSharedPrefs.edit().putString("playlists_data", jsonArr.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadPlaylistsFromPrefs(allMedia: List<MediaItem>): List<Pair<String, List<MediaItem>>> {
        val list = mutableListOf<Pair<String, List<MediaItem>>>()
        val dataStr = playlistsSharedPrefs.getString("playlists_data", null) ?: return list
        try {
            val jsonArr = org.json.JSONArray(dataStr)
            for (i in 0 until jsonArr.length()) {
                val playlistObj = jsonArr.getJSONObject(i)
                val name = playlistObj.getString("name")
                val itemsArr = playlistObj.getJSONArray("items")
                val itemsList = mutableListOf<MediaItem>()
                for (j in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(j)
                    val id = itemObj.getLong("id")
                    val title = itemObj.getString("title")
                    val artist = itemObj.getString("artist")
                    val uriStr = itemObj.getString("uri")
                    val duration = itemObj.getLong("duration")
                    val size = itemObj.getLong("size")
                    val type = MediaType.valueOf(itemObj.getString("type"))

                    val matched = allMedia.find { it.uri.toString() == uriStr }
                    if (matched != null) {
                        itemsList.add(matched)
                    } else {
                        itemsList.add(MediaItem(id, title, artist, Uri.parse(uriStr), duration, size, type))
                    }
                }
                list.add(Pair(name, itemsList))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    var userPlaylists: List<Pair<String, List<MediaItem>>> by remember(storageItems) {
        mutableStateOf(loadPlaylistsFromPrefs(storageItems))
    }

    fun playMediaWithRecent(item: MediaItem, playlistUris: String = "") {
        saveRecentMedia(item)
        recentMediaItems = loadRecentMedia()
        onItemClick(
            PlayerKey(
                uriString = item.uri.toString(),
                title = item.title,
                artistOrSubtitle = item.artistOrSubtitle,
                isVideo = item.type == MediaType.VIDEO,
                playlistUris = playlistUris
            )
        )
    }

    // Dialog state for 3-dots actions
    var selectedMediaForPlaylist by remember { mutableStateOf<MediaItem?>(null) }
    var selectedMediaForDelete by remember { mutableStateOf<MediaItem?>(null) }
    var selectedMediaForRename by remember { mutableStateOf<MediaItem?>(null) }
    var selectedMediaForDetails by remember { mutableStateOf<MediaDetails?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث في الملفات...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = if (selectedTab == 0) "فيديوهات" else if (selectedTab == 1) "موسيقى" else "قوائم التشغيل",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    if ((selectedTab == 0 && activeVideoFolder != null) || (selectedTab == 1 && activeAudioFolder != null)) {
                        IconButton(onClick = {
                            if (selectedTab == 0) activeVideoFolder = null else activeAudioFolder = null
                            searchQuery = ""
                            isSearchActive = false
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) {
                            searchQuery = ""
                        }
                    }) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "بحث"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        activeVideoFolder = null
                        searchQuery = ""
                    },
                    icon = { Icon(Icons.Default.Videocam, contentDescription = "فيديوهات") },
                    label = { Text("فيديوهات") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        activeAudioFolder = null
                        searchQuery = ""
                    },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "موسيقى") },
                    label = { Text("موسيقى") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        searchQuery = ""
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "قوائم التشغيل") },
                    label = { Text("قوائم التشغيل") }
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Global Search View
            if (isSearchActive && searchQuery.isNotEmpty()) {
                val allStorageMedia = remember(storageItems) { storageItems }
                val searchResults = allStorageMedia.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artistOrSubtitle.contains(searchQuery, ignoreCase = true)
                }

                Text(
                    text = "نتائج البحث (${searchResults.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                MediaList(
                    items = searchResults,
                    onPlay = { item -> playMediaWithRecent(item) },
                    onAddToPlaylist = { item -> selectedMediaForPlaylist = item },
                    onDelete = { item -> selectedMediaForDelete = item },
                    onRename = { item -> selectedMediaForRename = item },
                    onShowProperties = { item -> selectedMediaForDetails = viewModel.getMediaDetails(context, item) },
                    sharedPrefs = recentsSharedPrefs
                )
            } else {
                when (selectedTab) {
                    0 -> { // Video Tab
                        if (!permissionGranted) {
                            StoragePermissionView(
                                onRequestPermission = {
                                    permissionLauncher.launch(viewModel.getRequiredPermissions())
                                }
                            )
                        } else if (activeVideoFolder == null) {
                            // RECENT VIDEOS SECTION: Clean thumbnail display without track title text
                            val recentVideos = remember(recentMediaItems, storageItems) {
                                val recentsOnlyVideos = recentMediaItems.filter { it.type == MediaType.VIDEO }
                                if (recentsOnlyVideos.isNotEmpty()) {
                                    recentsOnlyVideos
                                } else {
                                    storageItems.filter { it.type == MediaType.VIDEO }.take(6)
                                }
                            }

                            if (recentVideos.isNotEmpty()) {
                                Text(
                                    text = "الأحدث",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(105.dp)
                                ) {
                                    items(recentVideos.take(10)) { videoItem ->
                                        RecentVideoCard(
                                            item = videoItem,
                                            sharedPrefs = recentsSharedPrefs,
                                            onPlay = { playMediaWithRecent(videoItem) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Folders list
                            val filteredFolders = videoFolders.filter {
                                it.name.contains(searchQuery, ignoreCase = true)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "مجلدات الفيديو (${filteredFolders.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            FolderList(
                                folders = filteredFolders,
                                onFolderClick = { folder ->
                                    activeVideoFolder = folder
                                    viewModel.loadFolderContents(context, folder.path, MediaType.VIDEO)
                                }
                            )
                        } else {
                            // Video Folder Contents
                            val filteredContents = folderContents.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artistOrSubtitle.contains(searchQuery, ignoreCase = true)
                            }
                            MediaList(
                                items = filteredContents,
                                onPlay = { item ->
                                    val uris = filteredContents.map { it.uri.toString() }.joinToString(",")
                                    playMediaWithRecent(item, playlistUris = uris)
                                },
                                onAddToPlaylist = { item -> selectedMediaForPlaylist = item },
                                onDelete = { item -> selectedMediaForDelete = item },
                                onRename = { item -> selectedMediaForRename = item },
                                onShowProperties = { item -> selectedMediaForDetails = viewModel.getMediaDetails(context, item) },
                                sharedPrefs = recentsSharedPrefs
                            )
                        }
                    }
                    1 -> { // Audio Tab
                        if (!permissionGranted) {
                            StoragePermissionView(
                                onRequestPermission = {
                                    permissionLauncher.launch(viewModel.getRequiredPermissions())
                                }
                            )
                        } else if (activeAudioFolder == null) {
                            val filteredFolders = audioFolders.filter {
                                it.name.contains(searchQuery, ignoreCase = true)
                            }
                            FolderList(
                                folders = filteredFolders,
                                onFolderClick = { folder ->
                                    activeAudioFolder = folder
                                    viewModel.loadFolderContents(context, folder.path, MediaType.AUDIO)
                                }
                            )
                        } else {
                            val filteredContents = folderContents.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artistOrSubtitle.contains(searchQuery, ignoreCase = true)
                            }
                            MediaList(
                                items = filteredContents,
                                onPlay = { item ->
                                    val uris = filteredContents.map { it.uri.toString() }.joinToString(",")
                                    playMediaWithRecent(item, playlistUris = uris)
                                },
                                onAddToPlaylist = { item -> selectedMediaForPlaylist = item },
                                onDelete = { item -> selectedMediaForDelete = item },
                                onRename = { item -> selectedMediaForRename = item },
                                onShowProperties = { item -> selectedMediaForDetails = viewModel.getMediaDetails(context, item) },
                                sharedPrefs = recentsSharedPrefs
                            )
                        }
                    }
                    2 -> { // Playlists Tab
                        var isCreateDialogOpen by remember { mutableStateOf(false) }
                        var newPlaylistName by remember { mutableStateOf("") }
                        var activePlaylistIndex by remember { mutableStateOf<Int?>(null) }
                        var isAddMediaDialogOpen by remember { mutableStateOf(false) }
                        var isAddFolderDialogOpen by remember { mutableStateOf(false) }

                        if (activePlaylistIndex == null) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Button(
                                    onClick = { isCreateDialogOpen = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("إنشاء قائمة تشغيل جديدة")
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                if (userPlaylists.isEmpty()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("لا توجد قوائم تشغيل حالياً.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        itemsIndexed(userPlaylists) { index, playlist ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { activePlaylistIndex = index }
                                                    .clip(RoundedCornerShape(12.dp)),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(playlist.first, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                        Text("${playlist.second.size} ملفات", color = Color.Gray, fontSize = 12.sp)
                                                    }
                                                    Row {
                                                        // Play sequential button
                                                        IconButton(onClick = {
                                                            if (playlist.second.isNotEmpty()) {
                                                                val first = playlist.second.first()
                                                                val urisConcat = playlist.second.joinToString(",") { it.uri.toString() }
                                                                playMediaWithRecent(first, playlistUris = urisConcat)
                                                            }
                                                        }) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل الكل", tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                        // Delete playlist button
                                                        IconButton(onClick = {
                                                            val updated = userPlaylists.toMutableList()
                                                            updated.removeAt(index)
                                                            userPlaylists = updated
                                                            savePlaylistsToPrefs(updated)
                                                        }) {
                                                            Icon(Icons.Default.Close, contentDescription = "حذف القائمة", tint = Color.Red.copy(alpha = 0.8f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Playlist details screen
                            val currentPlaylist = userPlaylists.getOrNull(activePlaylistIndex!!)
                            if (currentPlaylist == null) {
                                activePlaylistIndex = null
                            } else {
                                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { activePlaylistIndex = null }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                                            }
                                            Text(currentPlaylist.first, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        }
                                        Row {
                                            // Sort by Episode order button
                                            IconButton(onClick = {
                                                val sortedList = sortMediaByEpisodes(currentPlaylist.second)
                                                val updatedPlaylists = userPlaylists.toMutableList()
                                                updatedPlaylists[activePlaylistIndex!!] = Pair(currentPlaylist.first, sortedList)
                                                userPlaylists = updatedPlaylists
                                                savePlaylistsToPrefs(updatedPlaylists)
                                                Toast.makeText(context, "تم ترتيب المقاطع", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "ترتيب حسب الحلقات", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            // Add entire folder button
                                            IconButton(onClick = { isAddFolderDialogOpen = true }) {
                                                Icon(Icons.Default.Folder, contentDescription = "إضافة مجلد كامل", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            // Add individual media files button
                                            IconButton(onClick = { isAddMediaDialogOpen = true }) {
                                                Icon(Icons.Default.Add, contentDescription = "إضافة ملفات", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    if (currentPlaylist.second.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                            Text("قائمة التشغيل فارغة. اضغط على إضافة ملفات للبدء.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            itemsIndexed(currentPlaylist.second) { itemIndex, item ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            // When clicking on a video clip in the playlist, it starts playing immediately
                                                            val subList = currentPlaylist.second.subList(itemIndex, currentPlaylist.second.size) + currentPlaylist.second.subList(0, itemIndex)
                                                            val urisConcat = subList.joinToString(",") { it.uri.toString() }
                                                            playMediaWithRecent(item, playlistUris = urisConcat)
                                                        }
                                                        .clip(RoundedCornerShape(10.dp)),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Media thumbnail or icon
                                                        if (item.type == MediaType.VIDEO) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(46.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                            ) {
                                                                VideoThumbnail(uri = item.uri, modifier = Modifier.fillMaxSize())
                                                            }
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(46.dp)
                                                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.MusicNote,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                                )
                                                            }
                                                        }

                                                        Spacer(modifier = Modifier.width(12.dp))

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                item.title,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                fontSize = 13.sp,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.basicMarquee(),
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Text(
                                                                formatDuration(item.duration),
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }

                                                        IconButton(onClick = {
                                                            val updatedPlaylists = userPlaylists.toMutableList()
                                                            val updatedList = currentPlaylist.second.toMutableList()
                                                            updatedList.removeAt(itemIndex)
                                                            updatedPlaylists[activePlaylistIndex!!] = Pair(currentPlaylist.first, updatedList)
                                                            userPlaylists = updatedPlaylists
                                                            savePlaylistsToPrefs(updatedPlaylists)
                                                        }) {
                                                            Icon(Icons.Default.Close, contentDescription = "إزالة", tint = Color.Gray)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                if (currentPlaylist.second.isNotEmpty()) {
                                                    val first = currentPlaylist.second.first()
                                                    val urisConcat = currentPlaylist.second.joinToString(",") { it.uri.toString() }
                                                    playMediaWithRecent(first, playlistUris = urisConcat)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("تشغيل قائمة التشغيل بالتتابع")
                                        }
                                    }
                                }
                            }
                        }

                        // Dialog to create a new playlist
                        if (isCreateDialogOpen) {
                            AlertDialog(
                                onDismissRequest = { isCreateDialogOpen = false },
                                title = { Text("إنشاء قائمة تشغيل جديدة") },
                                text = {
                                    OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        placeholder = { Text("اسم القائمة...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        if (newPlaylistName.trim().isNotEmpty()) {
                                            val updated = userPlaylists.toMutableList()
                                            updated.add(Pair(newPlaylistName.trim(), emptyList()))
                                            userPlaylists = updated
                                            savePlaylistsToPrefs(updated)
                                            newPlaylistName = ""
                                            isCreateDialogOpen = false
                                            Toast.makeText(context, "تم إنشاء القائمة", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("إنشاء")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { isCreateDialogOpen = false }) {
                                        Text("إلغاء")
                                    }
                                }
                            )
                        }

                        // Dialog to add folder to playlist
                        if (isAddFolderDialogOpen && activePlaylistIndex != null) {
                            val allFolders = remember(videoFolders, audioFolders) {
                                (videoFolders + audioFolders).distinctBy { it.path }
                            }
                            AlertDialog(
                                onDismissRequest = { isAddFolderDialogOpen = false },
                                title = { Text("اختر مجلداً لإضافته كاملاً") },
                                text = {
                                    if (allFolders.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                            Text("لا توجد مجلدات متاحة", color = Color.Gray)
                                        }
                                    } else {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.height(300.dp).fillMaxWidth()
                                        ) {
                                            items(allFolders) { folder ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val folderMedia = (
                                                                DefaultDataRepository().getMediaItemsInFolder(context, folder.path, MediaType.VIDEO) +
                                                                DefaultDataRepository().getMediaItemsInFolder(context, folder.path, MediaType.AUDIO)
                                                            ).distinctBy { it.uri.toString() }

                                                            val sortedFolderMedia = sortMediaByEpisodes(folderMedia)
                                                            val updatedPlaylists = userPlaylists.toMutableList()
                                                            val targetPlaylist = updatedPlaylists[activePlaylistIndex!!]
                                                            val currentList = targetPlaylist.second.toMutableList()

                                                            sortedFolderMedia.forEach { item ->
                                                                if (!currentList.any { it.uri.toString() == item.uri.toString() }) {
                                                                    currentList.add(item)
                                                                }
                                                            }

                                                            val finalSorted = sortMediaByEpisodes(currentList)
                                                            updatedPlaylists[activePlaylistIndex!!] = Pair(targetPlaylist.first, finalSorted)
                                                            userPlaylists = updatedPlaylists
                                                            savePlaylistsToPrefs(updatedPlaylists)
                                                            isAddFolderDialogOpen = false
                                                            Toast.makeText(context, "تمت إضافة ${folder.name}", Toast.LENGTH_SHORT).show()
                                                        },
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Folder,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(folder.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                            Text("${folder.mediaCount} ملفات", fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = { isAddFolderDialogOpen = false }) {
                                        Text("إلغاء")
                                    }
                                }
                            )
                        }

                        // Dialog to add individual media files to playlist
                        if (isAddMediaDialogOpen && activePlaylistIndex != null) {
                            val allStorageMedia = remember(storageItems) { storageItems }
                            var dialogSearchQuery by remember { mutableStateOf("") }
                            val filteredDialogMedia = remember(dialogSearchQuery, allStorageMedia) {
                                if (dialogSearchQuery.trim().isEmpty()) {
                                    allStorageMedia
                                } else {
                                    allStorageMedia.filter {
                                        it.title.contains(dialogSearchQuery, ignoreCase = true) ||
                                        it.artistOrSubtitle.contains(dialogSearchQuery, ignoreCase = true)
                                    }
                                }
                            }
                            AlertDialog(
                                onDismissRequest = { isAddMediaDialogOpen = false },
                                title = { Text("اختر ملفات لإضافتها") },
                                text = {
                                    Column {
                                        OutlinedTextField(
                                            value = dialogSearchQuery,
                                            onValueChange = { dialogSearchQuery = it },
                                            placeholder = { Text("بحث عن ملف...") },
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            singleLine = true
                                        )
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.height(300.dp).fillMaxWidth()
                                        ) {
                                            items(filteredDialogMedia) { item ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            val updatedPlaylists = userPlaylists.toMutableList()
                                                            val targetPlaylist = updatedPlaylists[activePlaylistIndex!!]
                                                            val currentList = targetPlaylist.second.toMutableList()
                                                            if (!currentList.any { it.uri.toString() == item.uri.toString() }) {
                                                                currentList.add(item)
                                                                val sorted = sortMediaByEpisodes(currentList)
                                                                updatedPlaylists[activePlaylistIndex!!] = Pair(targetPlaylist.first, sorted)
                                                                userPlaylists = updatedPlaylists
                                                                savePlaylistsToPrefs(updatedPlaylists)
                                                                Toast.makeText(context, "تمت إضافة ${item.title}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (item.type == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        item.title,
                                                        maxLines = 1,
                                                        fontSize = 13.sp,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f).basicMarquee()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = { isAddMediaDialogOpen = false }) {
                                        Text("تم")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // OVERFLOW 3-DOTS ACTION DIALOGS
    // -------------------------------------------------------------

    // 1. Add to Playlist Dialog
    if (selectedMediaForPlaylist != null) {
        val targetMedia = selectedMediaForPlaylist!!
        var isNewPlaylistDialogOpen by remember { mutableStateOf(false) }
        var newPlName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedMediaForPlaylist = null },
            title = { Text("إضافة إلى قائمة تشغيل", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "اختر قائمة لإضافة \"${targetMedia.title}\":",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (!isNewPlaylistDialogOpen) {
                        Button(
                            onClick = { isNewPlaylistDialogOpen = true },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إنشاء قائمة جديدة +")
                        }
                    } else {
                        OutlinedTextField(
                            value = newPlName,
                            onValueChange = { newPlName = it },
                            label = { Text("اسم القائمة الجديدة") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isNewPlaylistDialogOpen = false }) { Text("إلغاء") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPlName.trim().isNotEmpty()) {
                                        val updated = userPlaylists.toMutableList()
                                        updated.add(Pair(newPlName.trim(), listOf(targetMedia)))
                                        userPlaylists = updated
                                        savePlaylistsToPrefs(updated)
                                        Toast.makeText(context, "تمت الإضافة إلى ${newPlName.trim()}", Toast.LENGTH_SHORT).show()
                                        selectedMediaForPlaylist = null
                                    }
                                }
                            ) {
                                Text("إنشاء وإضافة")
                            }
                        }
                    }

                    if (userPlaylists.isEmpty() && !isNewPlaylistDialogOpen) {
                        Text("لا توجد قوائم تشغيل سابقة. أنشئ قائمة جديدة أعلاه.", fontSize = 12.sp, color = Color.Gray)
                    } else if (!isNewPlaylistDialogOpen) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(userPlaylists) { pIndex, pl ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val updated = userPlaylists.toMutableList()
                                            val curList = pl.second.toMutableList()
                                            if (!curList.any { it.uri.toString() == targetMedia.uri.toString() }) {
                                                curList.add(targetMedia)
                                                val sorted = sortMediaByEpisodes(curList)
                                                updated[pIndex] = Pair(pl.first, sorted)
                                                userPlaylists = updated
                                                savePlaylistsToPrefs(updated)
                                                Toast.makeText(context, "تمت الإضافة إلى ${pl.first}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "الملف موجود بالفعل في ${pl.first}", Toast.LENGTH_SHORT).show()
                                            }
                                            selectedMediaForPlaylist = null
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pl.first, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${pl.second.size} ملفات", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMediaForPlaylist = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // 2. Delete Confirmation Dialog
    if (selectedMediaForDelete != null) {
        val targetMedia = selectedMediaForDelete!!
        AlertDialog(
            onDismissRequest = { selectedMediaForDelete = null },
            title = { Text("تأكيد الحذف", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل أنت متأكد من رغبتك في حذف \"${targetMedia.title}\"؟\nلا يمكن التراجع عن هذا الإجراء.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMedia(context, targetMedia) { success ->
                            // Remove from recents
                            val currentRecents = loadRecentMedia().toMutableList()
                            currentRecents.removeAll { it.uri.toString() == targetMedia.uri.toString() }
                            val jsonArr = org.json.JSONArray()
                            currentRecents.forEach { itm ->
                                val itemObj = org.json.JSONObject()
                                itemObj.put("id", itm.id)
                                itemObj.put("title", itm.title)
                                itemObj.put("artist", itm.artistOrSubtitle)
                                itemObj.put("uri", itm.uri.toString())
                                itemObj.put("duration", itm.duration)
                                itemObj.put("size", itm.size)
                                itemObj.put("type", itm.type.name)
                                jsonArr.put(itemObj)
                            }
                            recentsSharedPrefs.edit().putString("recent_media_list", jsonArr.toString()).apply()
                            recentsSharedPrefs.edit().remove("pos_${targetMedia.uri}").apply()
                            recentMediaItems = loadRecentMedia()

                            // Remove from playlists
                            val updatedPlaylists = userPlaylists.map { pl ->
                                Pair(pl.first, pl.second.filter { it.uri.toString() != targetMedia.uri.toString() })
                            }
                            userPlaylists = updatedPlaylists
                            savePlaylistsToPrefs(updatedPlaylists)

                            Toast.makeText(context, if (success) "تم حذف الملف بنجاح" else "تمت إزالة الملف", Toast.LENGTH_SHORT).show()
                        }
                        selectedMediaForDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMediaForDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 3. Rename Dialog
    if (selectedMediaForRename != null) {
        val targetMedia = selectedMediaForRename!!
        var renameText by remember { mutableStateOf(targetMedia.title) }

        AlertDialog(
            onDismissRequest = { selectedMediaForRename = null },
            title = { Text("إعادة تسمية", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "أدخل الاسم الجديد للملف:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTitle = renameText.trim()
                        if (newTitle.isNotEmpty()) {
                            viewModel.renameMedia(context, targetMedia, newTitle) {
                                // Update in recents
                                val currentRecents = loadRecentMedia().toMutableList()
                                val updatedRecents = currentRecents.map {
                                    if (it.uri.toString() == targetMedia.uri.toString()) it.copy(title = newTitle) else it
                                }
                                val jsonArr = org.json.JSONArray()
                                updatedRecents.forEach { itm ->
                                    val itemObj = org.json.JSONObject()
                                    itemObj.put("id", itm.id)
                                    itemObj.put("title", itm.title)
                                    itemObj.put("artist", itm.artistOrSubtitle)
                                    itemObj.put("uri", itm.uri.toString())
                                    itemObj.put("duration", itm.duration)
                                    itemObj.put("size", itm.size)
                                    itemObj.put("type", itm.type.name)
                                    jsonArr.put(itemObj)
                                }
                                recentsSharedPrefs.edit().putString("recent_media_list", jsonArr.toString()).apply()
                                recentMediaItems = loadRecentMedia()

                                // Update in playlists
                                val updatedPlaylists = userPlaylists.map { pl ->
                                    Pair(pl.first, pl.second.map {
                                        if (it.uri.toString() == targetMedia.uri.toString()) it.copy(title = newTitle) else it
                                    })
                                }
                                userPlaylists = updatedPlaylists
                                savePlaylistsToPrefs(updatedPlaylists)

                                Toast.makeText(context, "تمت إعادة التسمية بنجاح", Toast.LENGTH_SHORT).show()
                            }
                            selectedMediaForRename = null
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMediaForRename = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 4. Properties / Details Dialog
    if (selectedMediaForDetails != null) {
        val details = selectedMediaForDetails!!
        AlertDialog(
            onDismissRequest = { selectedMediaForDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("خصائص الملف", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PropertyRow(label = "اسم الملف", value = details.title)
                    PropertyRow(label = "المسار", value = details.pathOrUri)
                    PropertyRow(label = "الحجم", value = details.sizeFormatted)
                    PropertyRow(label = "المدة", value = details.durationFormatted)
                    PropertyRow(label = "النوع / الصيغة", value = details.mimeTypeOrFormat)
                    if (details.resolution != null) {
                        PropertyRow(label = "الدقة", value = details.resolution)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedMediaForDetails = null }) {
                    Text("إغلاق")
                }
            }
        )
    }
}

@Composable
fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Natural Episode-aware sorting helper
fun sortMediaByEpisodes(items: List<MediaItem>): List<MediaItem> {
    val regexNumber = "\\d+".toRegex()
    return items.sortedWith(Comparator { a, b ->
        val titleA = a.title
        val titleB = b.title

        val numbersA = regexNumber.findAll(titleA).map { it.value.toLongOrNull() ?: 0L }.toList()
        val numbersB = regexNumber.findAll(titleB).map { it.value.toLongOrNull() ?: 0L }.toList()

        if (numbersA.isNotEmpty() && numbersB.isNotEmpty()) {
            val minLen = minOf(numbersA.size, numbersB.size)
            for (i in 0 until minLen) {
                val cmp = numbersA[i].compareTo(numbersB[i])
                if (cmp != 0) return@Comparator cmp
            }
            if (numbersA.size != numbersB.size) {
                return@Comparator numbersA.size.compareTo(numbersB.size)
            }
        }
        titleA.compareTo(titleB, ignoreCase = true)
    })
}

@Composable
fun StoragePermissionView(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "يحتاج التطبيق للوصول إلى ذاكرة التخزين لعرض ملفاتك ومجلدات الهاتف.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(onClick = onRequestPermission) {
                Text("منح الإذن")
            }
        }
    }
}

@Composable
fun FolderList(
    folders: List<FolderItem>,
    onFolderClick: (FolderItem) -> Unit
) {
    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لم يتم العثور على أي مجلدات وسائط.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(folders) { folder ->
                FolderCard(folder = folder, onClick = { onFolderClick(folder) })
            }
        }
    }
}

@Composable
fun FolderCard(folder: FolderItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${folder.mediaCount} ملفات",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * RECENT VIDEO CARD:
 * Requirement 1: Removed track/video name overlay, added real VideoThumbnail.
 * Displays duration badge and bottom playback progress line.
 */
@Composable
fun RecentVideoCard(
    item: MediaItem,
    sharedPrefs: android.content.SharedPreferences? = null,
    onPlay: () -> Unit
) {
    val savedPos = remember(item.uri) {
        sharedPrefs?.getLong("pos_${item.uri}", 0L) ?: 0L
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(105.dp)
            .clickable { onPlay() }
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Real Video Thumbnail (Requirement 1)
            VideoThumbnail(
                uri = item.uri,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                contentDescription = null
            )

            // Center play button overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "تشغيل",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Duration badge at bottom right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (savedPos > 0L && item.duration > 0L) "${formatDuration(savedPos)} / ${formatDuration(item.duration)}" else formatDuration(item.duration),
                    color = if (savedPos > 0L) MaterialTheme.colorScheme.primary else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Playback progress line at bottom if played partially
            if (savedPos > 0L && item.duration > 0L) {
                val progressFraction = (savedPos.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progressFraction)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun MediaList(
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onAddToPlaylist: ((MediaItem) -> Unit)? = null,
    onDelete: ((MediaItem) -> Unit)? = null,
    onRename: ((MediaItem) -> Unit)? = null,
    onShowProperties: ((MediaItem) -> Unit)? = null,
    sharedPrefs: android.content.SharedPreferences? = null
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لا توجد ملفات للعرض.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { item ->
                MediaCard(
                    item = item,
                    onClick = { onPlay(item) },
                    onAddToPlaylist = { onAddToPlaylist?.invoke(item) },
                    onDelete = { onDelete?.invoke(item) },
                    onRename = { onRename?.invoke(item) },
                    onShowProperties = { onShowProperties?.invoke(item) },
                    sharedPrefs = sharedPrefs
                )
            }
        }
    }
}

/**
 * MEDIA CARD:
 * Requirement 2: For videos, show ONLY the video title. If partially watched, show progress bar underneath.
 * Requirement 3: 3-dots overflow menu containing:
 *   - إضافة إلى قائمة تشغيل (Add to Playlist)
 *   - حذف (Delete)
 *   - إعادة تسمية (Rename)
 *   - خصائص (Properties)
 */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onShowProperties: (() -> Unit)? = null,
    sharedPrefs: android.content.SharedPreferences? = null
) {
    val isVideo = item.type == MediaType.VIDEO
    val savedPos = remember(item.uri) {
        sharedPrefs?.getLong("pos_${item.uri}", 0L) ?: 0L
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Media Thumbnail or Type Icon
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    VideoThumbnail(
                        uri = item.uri,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2196F3), Color(0xFF00BCD4))
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Center Content: Title and Progress Bar
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isVideo) {
                    // Requirement 2: For videos, ONLY video name is shown.
                    // If partially played, show progress bar underneath.
                    if (savedPos > 0L && item.duration > 0L) {
                        val progressFraction = (savedPos.toFloat() / item.duration.toFloat()).coerceIn(0f, 1f)
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatDuration(savedPos)} / ${formatDuration(item.duration)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(progressFraction * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatDuration(item.duration),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // For Audio: Show artist/subtitle and duration
                    Text(
                        text = item.artistOrSubtitle,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDuration(item.duration),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Requirement 3: 3-dots Menu with 4 actions (Add to playlist, Delete, Rename, Properties)
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "خيارات",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("إضافة إلى قائمة تشغيل") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("إعادة تسمية") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRename?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("خصائص") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onShowProperties?.invoke()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete?.invoke()
                        }
                    )
                }
            }
        }
    }
}
