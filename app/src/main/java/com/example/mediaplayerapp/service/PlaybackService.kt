package com.example.mediaplayerapp.service

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        // Ensure Player and MediaSession are initialized
        PlayerManager.getPlayer(this)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return PlayerManager.getMediaSession()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PlayerManager.getMediaSession()?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
