package com.example.mediaplayerapp.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class MediaDetails(
    val title: String,
    val pathOrUri: String,
    val sizeFormatted: String,
    val durationFormatted: String,
    val mimeTypeOrFormat: String,
    val resolution: String? = null
)

interface DataRepository {
    val mediaItems: Flow<List<MediaItem>>
    fun getSampleStreams(): List<MediaItem>
    fun getMediaItemsFromStorage(context: Context): List<MediaItem>
    fun getMediaFolders(context: Context, type: MediaType): List<FolderItem>
    fun getMediaItemsInFolder(context: Context, folderPath: String, type: MediaType): List<MediaItem>
    fun deleteMedia(context: Context, item: MediaItem): Boolean = false
    fun renameMedia(context: Context, item: MediaItem, newName: String): Boolean = false
    fun getMediaDetails(context: Context, item: MediaItem): MediaDetails? = null
}

class DefaultDataRepository : DataRepository {
    override val mediaItems: Flow<List<MediaItem>> = flow {
        emit(getSampleStreams())
    }.flowOn(Dispatchers.IO)

    override fun getSampleStreams(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = -1,
                title = "Big Buck Bunny (Video Stream)",
                artistOrSubtitle = "Blender Foundation",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                duration = 596000,
                size = 0,
                type = MediaType.VIDEO
            ),
            MediaItem(
                id = -2,
                title = "Sintel (Video Stream)",
                artistOrSubtitle = "Blender Foundation",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
                duration = 888000,
                size = 0,
                type = MediaType.VIDEO
            ),
            MediaItem(
                id = -3,
                title = "Synthesized Classical Piano (Audio Stream)",
                artistOrSubtitle = "Internet Archive",
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"),
                duration = 372000,
                size = 0,
                type = MediaType.AUDIO
            ),
            MediaItem(
                id = -4,
                title = "Electronic Beats (Audio Stream)",
                artistOrSubtitle = "Internet Archive",
                uri = Uri.parse("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"),
                duration = 302000,
                size = 0,
                type = MediaType.AUDIO
            )
        )
    }

    override fun getMediaItemsFromStorage(context: Context): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val contentResolver: ContentResolver = context.contentResolver

        // Load Audio Files
        val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )
        contentResolver.query(audioUri, audioProjection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown Audio"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val duration = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(audioUri, id)

                list.add(
                    MediaItem(
                        id = id,
                        title = title,
                        artistOrSubtitle = artist,
                        uri = uri,
                        duration = duration,
                        size = size,
                        type = MediaType.AUDIO
                    )
                )
            }
        }

        // Load Video Files
        val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        contentResolver.query(videoUri, videoProjection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "Unknown Video"
                val duration = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                val uri = ContentUris.withAppendedId(videoUri, id)

                list.add(
                    MediaItem(
                        id = id,
                        title = title,
                        artistOrSubtitle = "Local Video",
                        uri = uri,
                        duration = duration,
                        size = size,
                        type = MediaType.VIDEO
                    )
                )
            }
        }

        return list
    }

    override fun getMediaFolders(context: Context, type: MediaType): List<FolderItem> {
        val folderMap = mutableMapOf<String, Int>() // folder path -> media count
        val contentResolver: ContentResolver = context.contentResolver
        
        val uri = if (type == MediaType.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol)
                if (path != null) {
                    val file = java.io.File(path)
                    val parentFile = file.parentFile
                    if (parentFile != null) {
                        val parentPath = parentFile.absolutePath
                        folderMap[parentPath] = (folderMap[parentPath] ?: 0) + 1
                    }
                }
            }
        }
        
        return folderMap.map { (path, count) ->
            val name = java.io.File(path).name
            FolderItem(name = if (name.isEmpty()) "Root" else name, path = path, mediaCount = count, type = type)
        }.sortedBy { it.name }
    }

    override fun getMediaItemsInFolder(context: Context, folderPath: String, type: MediaType): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val contentResolver: ContentResolver = context.contentResolver
        
        val uri = if (type == MediaType.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = null
        val selectionArgs = null
        
        val projection = if (type == MediaType.VIDEO) {
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.MediaColumns.DATA
            )
        } else {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.MediaColumns.DATA
            )
        }
        
        contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            
            val artistOrNameCol = if (type == MediaType.VIDEO) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            } else {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            }

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: ""
                // Ensure it is in the immediate directory, not subdirectories
                val parentFile = java.io.File(path).parentFile
                if (parentFile == null || 
                    parentFile.absolutePath.trimEnd('/', '\\').equals(folderPath.trimEnd('/', '\\'), ignoreCase = true)) {
                    // Match found!
                } else {
                    continue
                }

                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown"
                val subtitle = cursor.getString(artistOrNameCol) ?: "Unknown"
                val duration = cursor.getLong(durationCol)
                val size = cursor.getLong(sizeCol)
                val itemUri = ContentUris.withAppendedId(uri, id)

                list.add(
                    MediaItem(
                        id = id,
                        title = title,
                        artistOrSubtitle = subtitle,
                        uri = itemUri,
                        duration = duration,
                        size = size,
                        type = type
                    )
                )
            }
        }
        return list
    }

    override fun deleteMedia(context: Context, item: MediaItem): Boolean {
        return try {
            if (item.uri.scheme == "content") {
                val rows = context.contentResolver.delete(item.uri, null, null)
                rows > 0
            } else if (item.uri.scheme == "file" || item.uri.path != null) {
                val file = java.io.File(item.uri.path ?: "")
                if (file.exists()) file.delete() else false
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun renameMedia(context: Context, item: MediaItem, newName: String): Boolean {
        return try {
            if (item.uri.scheme == "content") {
                val values = android.content.ContentValues().apply {
                    if (item.type == MediaType.VIDEO) {
                        put(MediaStore.Video.Media.TITLE, newName)
                        put(MediaStore.Video.Media.DISPLAY_NAME, newName)
                    } else {
                        put(MediaStore.Audio.Media.TITLE, newName)
                        put(MediaStore.Audio.Media.DISPLAY_NAME, newName)
                    }
                }
                val rows = context.contentResolver.update(item.uri, values, null, null)
                rows > 0
            } else if (item.uri.scheme == "file" || item.uri.path != null) {
                val file = java.io.File(item.uri.path ?: "")
                if (file.exists()) {
                    val newFile = java.io.File(file.parentFile, newName)
                    file.renameTo(newFile)
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun getMediaDetails(context: Context, item: MediaItem): MediaDetails {
        var formattedSize = formatFileSize(item.size)
        var path = item.uri.toString()
        var mimeType = if (item.type == MediaType.VIDEO) "video/*" else "audio/*"
        var resolution: String? = null

        try {
            if (item.uri.scheme == "content") {
                val projection = arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.SIZE
                )
                context.contentResolver.query(item.uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                        val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

                        if (dataIdx != -1) cursor.getString(dataIdx)?.let { path = it }
                        if (mimeIdx != -1) cursor.getString(mimeIdx)?.let { mimeType = it }
                        if (sizeIdx != -1) {
                            val s = cursor.getLong(sizeIdx)
                            if (s > 0) formattedSize = formatFileSize(s)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val retriever = android.media.MediaMetadataRetriever()
            if (item.uri.scheme == "content" || item.uri.scheme == "android.resource") {
                retriever.setDataSource(context, item.uri)
            } else if (item.uri.scheme == "http" || item.uri.scheme == "https") {
                retriever.setDataSource(item.uri.toString(), HashMap())
            } else {
                retriever.setDataSource(item.uri.path ?: item.uri.toString())
            }

            if (item.type == MediaType.VIDEO) {
                val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (!width.isNullOrEmpty() && !height.isNullOrEmpty()) {
                    resolution = "${width} x ${height}"
                }
            }
            val mime = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!mime.isNullOrEmpty()) mimeType = mime
            retriever.release()
        } catch (_: Exception) {}

        val durationFormatted = formatDuration(item.duration)

        return MediaDetails(
            title = item.title,
            pathOrUri = path,
            sizeFormatted = formattedSize,
            durationFormatted = durationFormatted,
            mimeTypeOrFormat = mimeType,
            resolution = resolution
        )
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
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
