package com.shneyni.nokiaplayer4

import android.content.Intent
import android.os.Handler
import android.os.Looper
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
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) saveResume() }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) player.currentMediaItem?.let { ResumeStore.remove(this@PlaybackService, it.mediaId) }
            }
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                val id = old.mediaItem?.mediaId ?: return
                when (reason) {
                    Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> ResumeStore.remove(this@PlaybackService, id)   // נגמר: לא לשאול שוב
                    Player.DISCONTINUITY_REASON_SKIP, Player.DISCONTINUITY_REASON_REMOVE ->
                        if (new.mediaItem?.mediaId != id && old.positionMs >= 15_000L && ResumeStore.has(this@PlaybackService, id))
                            ResumeStore.put(this@PlaybackService, id, old.positionMs)
                }
            }
        })
        handler.postDelayed(saver, 5000)
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

    private val handler = Handler(Looper.getMainLooper())
    private val saver = object : Runnable { override fun run() { saveResume(); handler.postDelayed(this, 5000) } }

    /** שירים של 10 דקות ומעלה: שומרים את המיקום (כל 5 שניות ובעצירה) כדי שאפשר יהיה להמשיך משם */
    private fun saveResume() {
        val item = player.currentMediaItem ?: return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur < 600_000L) return
        val pos = player.currentPosition
        if (pos > dur - 20_000L) ResumeStore.remove(this, item.mediaId)     // כמעט נגמר: נחשב כסיים
        else if (pos >= 15_000L) ResumeStore.put(this, item.mediaId, pos)
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
        saveResume()
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(saver); saveResume()
        eq?.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
