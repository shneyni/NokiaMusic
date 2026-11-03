package com.shneyni.nokiaplayer4

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** נגינה ברקע + התראת מדיה + אקולייזר + ניהול תור (פקודות "eq" ו-"queue" מה-Activity). */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var eq: Equalizer? = null
    private var pct: IntArray? = null                 // -100..100 לכל band
    private val eqCmd = SessionCommand("eq", Bundle.EMPTY)
    private val queueCmd = SessionCommand("queue", Bundle.EMPTY)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("state", MODE_PRIVATE) }
    private var lastVol = -1
    private val saver = object : Runnable { override fun run() { saveResume(); handler.postDelayed(this, 5000) } }

    private val volRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val v = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_MUSIC)
            if (lastVol > 0 && v == 0 && Cfg.pauseZeroFrom(prefs) && player.playWhenReady) player.pause()
            lastVol = v
        }
    }

    override fun onCreate() {
        super.onCreate()
        // מנוע שמע איכותי: פלט Float לקבצי Hi-Res (24 ביט ומעלה), שינוי מהירות דרך ה-AudioTrack,
        // ופענון חלופי אם מפענח החומרה נכשל. בקבצים רגילים אין עומס נוסף.
        val rf = DefaultRenderersFactory(this)
            .setEnableAudioFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(true)
            .setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, rf)
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
                    Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> {
                        ResumeStore.remove(this@PlaybackService, id)   // נגמר: לא לשאול שוב
                        // חזרה על הכל: הגענו לסוף התור (גם במצב אקראי) ← פועלים לפי "בסיום תור"
                        if (player.repeatMode == Player.REPEAT_MODE_ALL &&
                            player.currentTimeline.getNextWindowIndex(old.mediaItemIndex, Player.REPEAT_MODE_OFF,
                                player.shuffleModeEnabled) == C.INDEX_UNSET) onQueueWrapped()
                    }
                    Player.DISCONTINUITY_REASON_SKIP, Player.DISCONTINUITY_REASON_REMOVE ->
                        if (new.mediaItem?.mediaId != id && old.positionMs >= 15_000L && ResumeStore.has(this@PlaybackService, id))
                            ResumeStore.put(this@PlaybackService, id, old.positionMs)
                }
            }
        })
        handler.postDelayed(saver, 5000)
        lastVol = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_MUSIC)
        ContextCompat.registerReceiver(this, volRx, IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED)
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            override fun onConnect(s: MediaSession, c: MediaSession.ControllerInfo):
                MediaSession.ConnectionResult {
                val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon().add(eqCmd).add(queueCmd).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(s)
                    .setAvailableSessionCommands(cmds).build()
            }

            override fun onCustomCommand(s: MediaSession, c: MediaSession.ControllerInfo,
                                         cmd: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                return when (cmd.customAction) {
                    "eq" -> {
                        pct = args.getIntArray("levels"); applyEq()
                        Futures.immediateFuture(SessionResult(
                            if (eq != null) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    }
                    "queue" -> {
                        try { handleQueue(args) } catch (_: Exception) {}
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    else -> super.onCustomCommand(s, c, cmd, args)
                }
            }
        }).build()
    }

    // ---------- סוף תור במצב "חזרה על הכל" ----------
    private fun onQueueWrapped() {
        when (Cfg.endActionFrom(prefs)) {
            0 -> player.pause()                 // עצור
            1 -> continueToNextGroup()          // המשך לתיקייה/אלבום/אמן הבא
            else -> {}                          // 2: חזור על הנוכחי (ExoPlayer כבר חזר להתחלה)
        }
    }

    private fun continueToNextGroup() {
        val src = prefs.getString("qsrc", null) ?: return
        fun go() {
            val g = Library.nextGroup(src) ?: return
            prefs.edit().putString("qsrc", g.first).apply()
            player.setMediaItems(g.second.map { it.toMediaItem() }, 0, 0L)
            player.prepare(); player.play()
        }
        if (Library.songs.isEmpty()) {
            Thread {
                try { Library.scan(this) } catch (_: Exception) {}
                handler.post { go() }
            }.start()
        } else go()
    }

    // ---------- "השמע הבא בתור" ----------
    private fun handleQueue(a: Bundle) {
        when (a.getString("op")) {
            "addnext" -> {
                val item = mediaItemOf(a.getLong("id"), Uri.parse(a.getString("uri") ?: return),
                    a.getString("title") ?: "—", a.getString("artist") ?: "", a.getString("album") ?: "", a.getLong("albumId"))
                if (player.mediaItemCount == 0) { player.addMediaItem(item); player.prepare(); return }
                val idx = player.currentMediaItemIndex + 1
                player.addMediaItem(idx, item)
                fixShuffleNext(idx)
            }
            "movenext" -> {
                val from = a.getInt("from"); val cur = player.currentMediaItemIndex
                if (from !in 0 until player.mediaItemCount || from == cur) return
                val to = if (from < cur) cur else cur + 1
                if (from == to) return
                player.moveMediaItem(from, to)
                fixShuffleNext(to)
            }
        }
    }

    /** במצב אקראי: מציב את הפריט newIdx מיד אחרי הנוכחי בסדר הערבוב */
    private fun fixShuffleNext(newIdx: Int) {
        if (!player.shuffleModeEnabled) return
        val tl = player.currentTimeline
        val order = ArrayList<Int>()
        var i = tl.getFirstWindowIndex(true)
        while (i != C.INDEX_UNSET) { order.add(i); i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true) }
        if (order.size != tl.windowCount) return
        order.remove(newIdx)
        val pos = order.indexOf(player.currentMediaItemIndex)
        order.add(pos + 1, newIdx)
        player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
    }

    /** שירים ארוכים (לפי ההגדרה): שומרים את המיקום (כל 5 שניות ובעצירה) כדי שאפשר יהיה להמשיך משם */
    private fun saveResume() {
        val min = Cfg.resumeMinFrom(prefs)
        if (min <= 0) return
        val item = player.currentMediaItem ?: return
        val dur = player.duration
        if (dur == C.TIME_UNSET || dur < min * 60_000L) return
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
        try { unregisterReceiver(volRx) } catch (_: Exception) {}
        eq?.release()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
