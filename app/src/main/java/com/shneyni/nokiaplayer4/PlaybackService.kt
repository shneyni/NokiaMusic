package com.shneyni.nokiaplayer4

import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** נגינה ברקע + התראת מדיה + אקולייזר (פקודת "eq" מה-Activity). */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var eq: Equalizer? = null
    private var pct: IntArray? = null                 // -100..100 לכל band
    private val eqCmd = SessionCommand("eq", Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { eq?.release(); eq = null; applyEq() }
        })
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            override fun onConnect(s: MediaSession, c: MediaSession.ControllerInfo):
                MediaSession.ConnectionResult {
                val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon().add(eqCmd).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(s)
                    .setAvailableSessionCommands(cmds).build()
            }

            override fun onCustomCommand(s: MediaSession, c: MediaSession.ControllerInfo,
                                         cmd: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                if (cmd.customAction != "eq") return super.onCustomCommand(s, c, cmd, args)
                pct = args.getIntArray("levels"); applyEq()
                return Futures.immediateFuture(SessionResult(
                    if (eq != null) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
        }).build()
    }

    private fun applyEq() {
        val p = pct ?: return
        val id = player.audioSessionId
        if (id == C.AUDIO_SESSION_ID_UNSET) return
        try {
            if (eq == null) eq = Equalizer(0, id).also { it.enabled = true }
            val e = eq ?: return
            val r = e.bandLevelRange; val n = e.numberOfBands.toInt()
            for (b in 0 until n) {
                val v = p[(b * p.size / n).coerceIn(0, p.size - 1)]
                val lvl = if (v >= 0) v * r[1] / 100 else -v * r[0] / 100
                e.setBandLevel(b.toShort(), lvl.toShort())
            }
        } catch (_: Exception) { eq = null }          // המכשיר לא תומך — ה-Activity יציג זאת
    }

    override fun onGetSession(info: MediaSession.ControllerInfo) = session

    /** הסרת האפליקציה מהאחרונות: אם מתנגן — ממשיכים ברקע; אם לא — השירות נסגר. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        eq?.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
