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

interface DataRepository {
    val mediaItems: Flow<List<MediaItem>>
    fun getSampleStreams(): List<MediaItem>
    fun getMediaItemsFromStorage(context: Context): List<MediaItem>
    fun getMediaFolders(context: Context, type: MediaType): List<FolderItem>
    fun getMediaItemsInFolder(context: Context, folderPath: String, type: MediaType): List<MediaItem>
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
}
