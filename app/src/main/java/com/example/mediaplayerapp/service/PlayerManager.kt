package com.example.mediaplayerapp.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.mediaplayerapp.MainActivity

object PlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    fun getPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            val player = ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

            exoPlayer = player

            val intent = Intent(context.applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context.applicationContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            mediaSession = MediaSession.Builder(context.applicationContext, player)
                .setSessionActivity(pendingIntent)
                .build()
        }
        return exoPlayer!!
    }

    fun getMediaSession(): MediaSession? = mediaSession

    fun startService(context: Context) {
        try {
            val intent = Intent(context.applicationContext, PlaybackService::class.java)
            context.applicationContext.startService(intent)
        } catch (_: Exception) {
            // Foreground service start fallback
        }
    }

    fun release() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        exoPlayer?.run {
            release()
            exoPlayer = null
        }
    }
}
