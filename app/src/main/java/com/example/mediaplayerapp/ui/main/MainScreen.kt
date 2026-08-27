package com.example.mediaplayerapp.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.mediaplayerapp.PlayerKey
import com.example.mediaplayerapp.data.DefaultDataRepository
import com.example.mediaplayerapp.data.FolderItem
import com.example.mediaplayerapp.data.MediaItem
import com.example.mediaplayerapp.data.MediaType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
                            placeholder = { Text("بحث عن صوت أو فيديو...") },
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
                            text = if (selectedTab == 0) "Video" else if (selectedTab == 1) "Music" else "Playlist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
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
                                contentDescription = "Back"
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
                            contentDescription = "Search"
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
                    icon = { Icon(Icons.Default.Videocam, contentDescription = "Video") },
                    label = { Text("Video") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        activeAudioFolder = null
                        searchQuery = ""
                    },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music") },
                    label = { Text("Music") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        searchQuery = ""
                    },
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Playlist") },
                    label = { Text("Playlist") }
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
            // Check if global search is active and searchQuery is not empty
            if (isSearchActive && searchQuery.isNotEmpty()) {
                // Global unified Search Results for Audio & Video
                val allStorageMedia = remember(storageItems) { storageItems }
                val searchResults = allStorageMedia.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.title.lowercase().contains(searchQuery.lowercase()) ||
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
                    onPlay = { item ->
                        onItemClick(
                            PlayerKey(
                                uriString = item.uri.toString(),
                                title = item.title,
                                artistOrSubtitle = item.artistOrSubtitle,
                                isVideo = item.type == MediaType.VIDEO
                            )
                        )
                    }
                )
            } else {
                // Storage Info & Quick Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(
                        onClick = { /* Stub */ },
                        label = { Text("30.5 GB") },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Storage Status",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFFC8E6C9),
                            labelColor = Color(0xFF2E7D32)
                        )
                    )

                    InputChip(
                        selected = true,
                        onClick = { /* Stub */ },
                        label = { Text("All Videos") }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                when (selectedTab) {
                    0 -> { // Local Videos grouped by folder
                        if (!permissionGranted) {
                            StoragePermissionView(
                                onRequestPermission = {
                                    permissionLauncher.launch(viewModel.getRequiredPermissions())
                                }
                            )
                        } else if (activeVideoFolder == null) {
                            // RECENT / ALL VIDEOS Horizontal Grid layout on top
                            val allVideos = remember(videoFolders, folderContents) {
                                folderContents.filter { it.type == MediaType.VIDEO }
                            }

                            if (allVideos.isNotEmpty()) {
                                Text(
                                    text = "RECENT VIDEOS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(130.dp)
                                ) {
                                    items(allVideos.take(5)) { videoItem ->
                                        RecentVideoCard(item = videoItem, onPlay = {
                                            onItemClick(PlayerKey(videoItem.uri.toString(), videoItem.title, videoItem.artistOrSubtitle, true))
                                        })
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // Folders list
                            val filteredFolders = videoFolders.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.name.lowercase().contains(searchQuery.lowercase())
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${filteredFolders.size} FOLDERS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = { /* Sort */ }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort"
                                    )
                                }
                            }

                            FolderList(
                                folders = filteredFolders,
                                onFolderClick = { folder ->
                                    activeVideoFolder = folder
                                    viewModel.loadFolderContents(context, folder.path, MediaType.VIDEO)
                                }
                            )
                        } else {
                            val filteredContents = folderContents.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.title.lowercase().contains(searchQuery.lowercase()) ||
                                it.artistOrSubtitle.contains(searchQuery, ignoreCase = true)
                            }
                            MediaList(
                                items = filteredContents,
                                onPlay = { item ->
                                    onItemClick(PlayerKey(item.uri.toString(), item.title, item.artistOrSubtitle, true))
                                }
                            )
                        }
                    }
                    1 -> { // Local Audio grouped by folder
                        if (!permissionGranted) {
                            StoragePermissionView(
                                onRequestPermission = {
                                    permissionLauncher.launch(viewModel.getRequiredPermissions())
                                }
                            )
                        } else if (activeAudioFolder == null) {
                            val filteredFolders = audioFolders.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.name.lowercase().contains(searchQuery.lowercase())
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
                                it.title.lowercase().contains(searchQuery.lowercase()) ||
                                it.artistOrSubtitle.contains(searchQuery, ignoreCase = true)
                            }
                            MediaList(
                                items = filteredContents,
                                onPlay = { item ->
                                    onItemClick(PlayerKey(item.uri.toString(), item.title, item.artistOrSubtitle, false))
                                }
                            )
                        }
                    }
                    2 -> { // Sample Playlists view tab
                        var isCreateDialogOpen by remember { mutableStateOf(false) }
                        var newPlaylistName by remember { mutableStateOf("") }
                        
                        // Local state to store user playlists (In-memory mock database for fast operation)
                        var userPlaylists by remember { mutableStateOf(mutableListOf<Pair<String, List<MediaItem>>>()) }
                        var activePlaylistIndex by remember { mutableStateOf<Int?>(null) }
                        var isAddMediaDialogOpen by remember { mutableStateOf(false) }

                        if (activePlaylistIndex == null) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Button(
                                    onClick = { isCreateDialogOpen = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("إنشاء قائمة تشغيل جديدة +")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                if (userPlaylists.isEmpty()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("لا توجد قوائم تشغيل حالياً.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                                    Column {
                                                        Text(playlist.first, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                        Text("${playlist.second.size} ملفات", color = Color.Gray, fontSize = 12.sp)
                                                    }
                                                    Row {
                                                        // Play sequential button
                                                        IconButton(onClick = {
                                                            if (playlist.second.isNotEmpty()) {
                                                                val first = playlist.second.first()
                                                                val urisConcat = playlist.second.joinToString(",") { it.uri.toString() }
                                                                onItemClick(
                                                                    PlayerKey(
                                                                        uriString = first.uri.toString(),
                                                                        title = playlist.first,
                                                                        artistOrSubtitle = "قائمة تشغيل متتالية",
                                                                        isVideo = first.type == MediaType.VIDEO,
                                                                        playlistUris = urisConcat
                                                                    )
                                                                )
                                                            }
                                                        }) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play All", tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                        // Delete button
                                                        IconButton(onClick = {
                                                            val updated = userPlaylists.toMutableList()
                                                            updated.removeAt(index)
                                                            userPlaylists = updated
                                                        }) {
                                                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Red)
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
                            val currentPlaylist = userPlaylists[activePlaylistIndex!!]
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { activePlaylistIndex = null }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                    Text(currentPlaylist.first, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Button(onClick = { isAddMediaDialogOpen = true }) {
                                        Text("إضافة ملفات +")
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                if (currentPlaylist.second.isEmpty()) {
                                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("قائمة التشغيل فارغة. اضغط على إضافة ملفات للبدء.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(currentPlaylist.second) { item ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                        Text(item.artistOrSubtitle, fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                    IconButton(onClick = {
                                                        val updatedPlaylists = userPlaylists.toMutableList()
                                                        val updatedList = currentPlaylist.second.toMutableList()
                                                        updatedList.remove(item)
                                                        updatedPlaylists[activePlaylistIndex!!] = Pair(currentPlaylist.first, updatedList)
                                                        userPlaylists = updatedPlaylists
                                                    }) {
                                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            if (currentPlaylist.second.isNotEmpty()) {
                                                val first = currentPlaylist.second.first()
                                                val urisConcat = currentPlaylist.second.joinToString(",") { it.uri.toString() }
                                                onItemClick(
                                                    PlayerKey(
                                                        uriString = first.uri.toString(),
                                                        title = currentPlaylist.first,
                                                        artistOrSubtitle = "قائمة تشغيل متتالية",
                                                        isVideo = first.type == MediaType.VIDEO,
                                                        playlistUris = urisConcat
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("تشغيل قائمة التشغيل بالكامل التتابع")
                                    }
                                }
                            }
                        }

                        // Dialog to create a new playlist
                        if (isCreateDialogOpen) {
                            AlertDialog(
                                onDismissRequest = { isCreateDialogOpen = false },
                                title = { Text("إنشاء قائمة جديدة") },
                                text = {
                                    TextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        placeholder = { Text("اسم قائمة التشغيل...") }
                                    )
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        if (newPlaylistName.trim().isNotEmpty()) {
                                            val updated = userPlaylists.toMutableList()
                                            updated.add(Pair(newPlaylistName.trim(), emptyList()))
                                            userPlaylists = updated
                                            newPlaylistName = ""
                                            isCreateDialogOpen = false
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

                        // Dialog to add media files to the current playlist
                        if (isAddMediaDialogOpen && activePlaylistIndex != null) {
                            val allStorageMedia = remember(storageItems) { storageItems }
                            AlertDialog(
                                onDismissRequest = { isAddMediaDialogOpen = false },
                                title = { Text("اختر ملفات لإضافتها") },
                                text = {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.height(300.dp).fillMaxWidth()
                                    ) {
                                        items(allStorageMedia) { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val updatedPlaylists = userPlaylists.toMutableList()
                                                        val targetPlaylist = updatedPlaylists[activePlaylistIndex!!]
                                                        val currentList = targetPlaylist.second.toMutableList()
                                                        if (!currentList.contains(item)) {
                                                            currentList.add(item)
                                                            updatedPlaylists[activePlaylistIndex!!] = Pair(targetPlaylist.first, currentList)
                                                            userPlaylists = updatedPlaylists
                                                        }
                                                    }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (item.type == MediaType.VIDEO) Icons.Default.Videocam else Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.Gray
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(item.title, maxLines = 1, modifier = Modifier.weight(1f))
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folder.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${folder.mediaCount}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        IconButton(onClick = { /* Folder options */ }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.Gray
            )
        }
    }
}

@Composable
fun RecentVideoCard(item: MediaItem, onPlay: () -> Unit) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(110.dp)
            .clickable { onPlay() }
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dark gray placeholder simulating mockup
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2C3E50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video Thumbnail Placeholder",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(40.dp)
                )
            }

            // Duration badge at bottom right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(item.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Short title overlay at bottom left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .width(100.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MediaList(items: List<MediaItem>, onPlay: (MediaItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            MediaCard(item = item, onClick = { onPlay(item) })
        }
    }
}

@Composable
fun MediaCard(item: MediaItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        Brush.linearGradient(
                            colors = if (item.type == MediaType.VIDEO)
                                listOf(Color(0xFFE91E63), Color(0xFF9C27B0))
                            else
                                listOf(Color(0xFF2196F3), Color(0xFF00BCD4))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.artistOrSubtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatDuration(item.duration),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = durationMs / (1000 * 60 * 60)
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
