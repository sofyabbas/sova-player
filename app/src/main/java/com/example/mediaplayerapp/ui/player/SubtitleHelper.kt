package com.example.mediaplayerapp.ui.player

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.io.File

data class SubtitleItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val language: String = "ar",
    val isAutoDetected: Boolean = false
)

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

@OptIn(UnstableApi::class)
object SubtitleHelper {

    fun getMimeTypeForExtension(extension: String): String {
        return when (extension.lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP // default .srt
        }
    }

    fun getMimeTypeForUri(uri: Uri): String {
        val path = uri.path ?: uri.toString()
        val ext = path.substringAfterLast(".", "srt")
        return getMimeTypeForExtension(ext)
    }

    /**
     * Searches for subtitle files in the same directory as the video file or in a "Subs"/"subtitles" subfolder.
     */
    fun findLocalSubtitles(context: Context, videoUri: Uri): List<SubtitleItem> {
        val list = mutableListOf<SubtitleItem>()
        val supportedExtensions = setOf("srt", "vtt", "ass", "ssa", "ttml")

        var filePath: String? = null
        if (videoUri.scheme == "file") {
            filePath = videoUri.path
        } else if (videoUri.scheme == "content") {
            try {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(videoUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (colIdx != -1) {
                            filePath = cursor.getString(colIdx)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (filePath != null) {
            val videoFile = File(filePath)
            val parentDir = videoFile.parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                val videoBaseName = videoFile.nameWithoutExtension.lowercase()

                // Check in same folder
                val files = parentDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (supportedExtensions.contains(ext)) {
                            val fileNameNoExt = file.nameWithoutExtension.lowercase()
                            // Check if matches video name or contains video name
                            if (fileNameNoExt.startsWith(videoBaseName) || fileNameNoExt == videoBaseName) {
                                val lang = detectLanguage(file.name)
                                list.add(
                                    SubtitleItem(
                                        uri = Uri.fromFile(file),
                                        name = file.name,
                                        mimeType = getMimeTypeForExtension(ext),
                                        language = lang,
                                        isAutoDetected = true
                                    )
                                )
                            }
                        }
                    }
                }

                // Check in "Subs" or "subtitles" subfolders
                val subsDirs = listOf(File(parentDir, "Subs"), File(parentDir, "subs"), File(parentDir, "subtitles"), File(parentDir, "Subtitles"))
                for (subDir in subsDirs) {
                    if (subDir.exists() && subDir.isDirectory) {
                        val subFiles = subDir.listFiles() ?: emptyArray()
                        for (file in subFiles) {
                            val ext = file.extension.lowercase()
                            if (supportedExtensions.contains(ext)) {
                                val lang = detectLanguage(file.name)
                                list.add(
                                    SubtitleItem(
                                        uri = Uri.fromFile(file),
                                        name = file.name,
                                        mimeType = getMimeTypeForExtension(ext),
                                        language = lang,
                                        isAutoDetected = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return list.distinctBy { it.uri.toString() }
    }

    private fun detectLanguage(filename: String): String {
        val lower = filename.lowercase()
        return when {
            lower.contains(".ar.") || lower.contains(".ara.") || lower.contains("arabic") || lower.contains("عرب") -> "ar"
            lower.contains(".en.") || lower.contains(".eng.") || lower.contains("english") -> "en"
            lower.contains(".fr.") || lower.contains("french") -> "fr"
            lower.contains(".es.") || lower.contains("spanish") -> "es"
            else -> "ar"
        }
    }

    fun buildExoMediaItem(
        uri: Uri,
        title: String,
        artist: String,
        subtitles: List<SubtitleItem>
    ): ExoMediaItem {
        val builder = ExoMediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )

        if (subtitles.isNotEmpty()) {
            val subtitleConfigs = subtitles.mapIndexed { index, sub ->
                ExoMediaItem.SubtitleConfiguration.Builder(sub.uri)
                    .setMimeType(sub.mimeType)
                    .setLanguage(sub.language)
                    .setLabel(sub.name)
                    .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigs)
        }

        return builder.build()
    }
}
