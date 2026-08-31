package com.example.mediaplayerapp.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
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
            val appContext = context.applicationContext
            val player = ExoPlayer.Builder(appContext)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            exoPlayer = player

            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            mediaSession = MediaSession.Builder(appContext, player)
                .setSessionActivity(pendingIntent)
                .build()
        }
        return exoPlayer!!
    }

    fun getMediaSession(context: Context? = null): MediaSession? {
        if (mediaSession == null && context != null) {
            getPlayer(context)
        }
        return mediaSession
    }

    fun startService(context: Context) {
        val appContext = context.applicationContext
        try {
            val intent = Intent(appContext, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: Exception) {
            try {
                val intent = Intent(appContext, PlaybackService::class.java)
                appContext.startService(intent)
            } catch (_: Exception) {
                // Fallback catch
            }
        }
    }

    fun stopService(context: Context) {
        try {
            val intent = Intent(context.applicationContext, PlaybackService::class.java)
            context.applicationContext.stopService(intent)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            mediaSession?.run {
                release()
                mediaSession = null
            }
            exoPlayer?.run {
                release()
                exoPlayer = null
            }
        } catch (_: Exception) {}
    }
}

