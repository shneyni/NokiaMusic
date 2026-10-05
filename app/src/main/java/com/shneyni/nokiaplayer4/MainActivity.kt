package com.shneyni.nokiaplayer4

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import android.content.BroadcastReceiver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.database.ContentObserver
import android.telephony.TelephonyManager
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaMetadata
import com.google.common.util.concurrent.ListenableFuture

// ---- פלטת הצבעים מהעיצוב ----
private val Yel = Color(0xFFFDC100)
private val Gry = Color(0xFFA6A6A6)
private val RM = listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE)

private class Screen(
    val title: String?, val items: List<String>, val notes: Int = 0, val menu: Boolean = false,
    val songs: List<Song>? = null, val small: Boolean = false, val ok: (Int) -> Unit = {},
)

@Composable
private fun Tx(t: String, size: Float, color: Color = Color.White, mod: Modifier = Modifier,
               align: TextAlign? = null, lines: Int = 1) =
    Text(t, color = color, fontSize = size.sp, fontFamily = FontFamily.SansSerif, maxLines = lines,
        overflow = TextOverflow.Ellipsis, modifier = mod, textAlign = align)

private class Art(val main: ImageBitmap, val blur: ImageBitmap)

/** אייקונים מצוירים בסגנון מודרני: bt, clock, batt, wifi, sig, shuffle, repeat, prev, next, play, pause */
@Composable
private fun Ico(kind: String, size: Dp, color: Color = Color.White, level: Float = 1f,
                off: Boolean = false, flag: Boolean = false) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val sw = w * 0.085f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val dim = color.copy(alpha = 0.3f)
        fun p(vararg v: Float, shut: Boolean = false) = Path().apply {
            moveTo(v[0] * w, v[1] * h); for (i in 2 until v.size step 2) lineTo(v[i] * w, v[i + 1] * h)
            if (shut) close()
        }
        if (off) drawLine(color, Offset(w * .08f, h * .92f), Offset(w * .92f, h * .08f), strokeWidth = w * .09f, cap = StrokeCap.Round)
        when (kind) {
            "sig" -> repeat(4) { i ->
                val bh = h * (0.32f + 0.2f * i)
                drawRoundRect(if (i < Math.round(level * 4)) color else dim,
                    Offset(w * (0.08f + i * 0.23f), h * 0.92f - bh), Size(w * 0.16f, bh), CornerRadius(w * 0.06f))
            }
            "wifi" -> {
                val cx = w * 0.5f; val cy = h * 0.88f
                val lit = Math.round(level * 3)
                repeat(3) { k ->
                    val r = w * (0.26f + 0.2f * k)
                    drawArc(if (k < lit) color else dim, -135f, 90f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r),
                        style = Stroke(width = sw * 1.2f, cap = StrokeCap.Round))
                }
                drawCircle(if (level > 0f) color else dim, w * 0.07f, Offset(cx, cy))
            }
            "bt" -> drawPath(p(.27f, .30f, .73f, .70f, .50f, .90f, .50f, .10f, .73f, .30f, .27f, .70f), color, style = st)
            "clock" -> { drawCircle(color, w * .42f, style = st); drawPath(p(.5f, .25f, .5f, .5f, .7f, .6f), color, style = st) }
            "batt" -> {
                val bw = w * 0.84f; val bh = h * 0.56f; val top = (h - bh) / 2
                drawRoundRect(color.copy(alpha = 0.55f), Offset(0f, top), Size(bw, bh), CornerRadius(bh * 0.28f),
                    style = Stroke(width = w * 0.06f))
                val inset = w * 0.07f
                val fillColor = if (!flag && level <= 0.15f) Color(0xFFFF5A52) else color
                drawRoundRect(fillColor, Offset(inset, top + inset),
                    Size(maxOf(0f, (bw - 2 * inset) * level.coerceIn(0f, 1f)), bh - 2 * inset),
                    CornerRadius((bh - 2 * inset) * 0.22f))
                drawRoundRect(color.copy(alpha = 0.55f), Offset(bw + w * 0.02f, h * 0.4f), Size(w * 0.07f, h * 0.2f), CornerRadius(w * 0.03f))
                if (flag) drawPath(p(.45f, .26f, .29f, .55f, .41f, .55f, .35f, .78f, .55f, .45f, .43f, .45f, shut = true), Color(0xFF59E27A))
            }
            "shuffle" -> {
                fun cub(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
                    Path().apply { moveTo(x0 * w, y0 * h); cubicTo(x1 * w, y1 * h, x2 * w, y2 * h, x3 * w, y3 * h) }
                drawPath(cub(.08f, .70f, .42f, .70f, .50f, .30f, .80f, .30f), color, style = st)
                drawPath(cub(.08f, .30f, .42f, .30f, .50f, .70f, .80f, .70f), color, style = st)
                drawPath(p(.70f, .18f, .84f, .30f, .70f, .42f), color, style = st)
                drawPath(p(.70f, .58f, .84f, .70f, .70f, .82f), color, style = st)
            }
            "repeat" -> {
                val top = Path().apply {
                    moveTo(.16f * w, .56f * h); lineTo(.16f * w, .44f * h); quadraticBezierTo(.16f * w, .28f * h, .32f * w, .28f * h)
                    lineTo(.68f * w, .28f * h); quadraticBezierTo(.84f * w, .28f * h, .84f * w, .44f * h); lineTo(.84f * w, .54f * h)
                }
                val bottom = Path().apply {
                    moveTo(.84f * w, .44f * h); lineTo(.84f * w, .56f * h); quadraticBezierTo(.84f * w, .72f * h, .68f * w, .72f * h)
                    lineTo(.32f * w, .72f * h); quadraticBezierTo(.16f * w, .72f * h, .16f * w, .56f * h); lineTo(.16f * w, .46f * h)
                }
                drawPath(top, color, style = st); drawPath(bottom, color, style = st)
                drawPath(p(.72f, .46f, .84f, .60f, .96f, .46f), color, style = st)
                drawPath(p(.04f, .54f, .16f, .40f, .28f, .54f), color, style = st)
                if (flag) drawPath(p(.45f, .44f, .52f, .38f, .52f, .62f), color, style = st)   // "1" = חזרה על שיר אחד
            }
            // סמלי נגן קלאסיים ללא עיגול: משולש ממולא עם פינות מעוגלות
            "play" -> {
                val tri = p(.28f, .14f, .28f, .86f, .84f, .50f, shut = true)
                drawPath(tri, color); drawPath(tri, color, style = Stroke(width = w * .09f, join = StrokeJoin.Round))
            }
            "pause" -> {
                drawRoundRect(color, Offset(w * .22f, h * .16f), Size(w * .20f, h * .68f), CornerRadius(w * .06f))
                drawRoundRect(color, Offset(w * .58f, h * .16f), Size(w * .20f, h * .68f), CornerRadius(w * .06f))
            }
            "next" -> {
                val tri = p(.16f, .20f, .16f, .80f, .62f, .50f, shut = true)
                drawPath(tri, color); drawPath(tri, color, style = Stroke(width = w * .08f, join = StrokeJoin.Round))
                drawRoundRect(color, Offset(w * .70f, h * .20f), Size(w * .14f, h * .60f), CornerRadius(w * .05f))
            }
            "prev" -> {
                val tri = p(.84f, .20f, .84f, .80f, .38f, .50f, shut = true)
                drawPath(tri, color); drawPath(tri, color, style = Stroke(width = w * .08f, join = StrokeJoin.Round))
                drawRoundRect(color, Offset(w * .16f, h * .20f), Size(w * .14f, h * .60f), CornerRadius(w * .05f))
            }
        }
        Unit
    }
}

// ---- סמלי SVG שסופקו (מסלולי מילוי, נצבעים בצבע הרצוי) ----
private val ICON_OPTIONS = listOf(
    "M480,224H32c-17.673,0-32,14.327-32,32s14.327,32,32,32h448c17.673,0,32-14.327,32-32S497.673,224,480,224z",
    "M32,138.667h448c17.673,0,32-14.327,32-32s-14.327-32-32-32H32c-17.673,0-32,14.327-32,32S14.327,138.667,32,138.667z",
    "M480,373.333H32c-17.673,0-32,14.327-32,32s14.327,32,32,32h448c17.673,0,32-14.327,32-32S497.673,373.333,480,373.333z")          // viewBox 512 — "אפשרויות"
private val ICON_REPEAT = listOf(
    "M12,2a10.032,10.032,0,0,1,7.122,3H16a1,1,0,0,0-1,1h0a1,1,0,0,0,1,1h4.143A1.858,1.858,0,0,0,22,5.143V1a1,1,0,0,0-1-1h0a1,1,0,0,0-1,1V3.078A11.981,11.981,0,0,0,.05,10.9a1.007,1.007,0,0,0,1,1.1h0a.982.982,0,0,0,.989-.878A10.014,10.014,0,0,1,12,2Z",
    "M22.951,12a.982.982,0,0,0-.989.878A9.986,9.986,0,0,1,4.878,19H8a1,1,0,0,0,1-1H9a1,1,0,0,0-1-1H3.857A1.856,1.856,0,0,0,2,18.857V23a1,1,0,0,0,1,1H3a1,1,0,0,0,1-1V20.922A11.981,11.981,0,0,0,23.95,13.1a1.007,1.007,0,0,0-1-1.1Z")          // viewBox 24 — "חזרה על שיר"
private val ICON_BACK = listOf(
    "M23.12,9.91,19.25,6a1,1,0,0,0-1.42,0h0a1,1,0,0,0,0,1.41L21.39,11H1a1,1,0,0,0-1,1H0a1,1,0,0,0,1,1H21.45l-3.62,3.61a1,1,0,0,0,0,1.42h0a1,1,0,0,0,1.42,0l3.87-3.88A3,3,0,0,0,23.12,9.91Z")            // viewBox 24 — "אחורה"
private val ICON_CHECK = listOf("M9,16.17L4.83,12l-1.42,1.41L9,19,21,7l-1.41-1.41z")   // viewBox 24 — "בחר/פתח"
private const val NOTE_D = "M2129.49 1251.58C1894.21 1266.37 1853.45 1407.62 1841.22 1486.87 1829 1566.13 1908.89 1742.05 2056.12 1727.11 2203.36 1712.16 2259.22 1618.7 2281.7 1523.69 2300.65 1298.71 2295.72 1184.89 2285.86 760.118 2486.92 721.749 2386.36 779.281 2560 784.123"

/** מצייר סמל SVG. one=true מוסיף "1" במרכז (חזרה על שיר אחד). */
@Composable
private fun SvgIcon(paths: List<String>, vb: Float, size: Dp, color: Color = Color.White, one: Boolean = false) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(Modifier.size(size)) {
        val sc = this.size.width / vb
        withTransform({ scale(sc, sc, Offset.Zero) }) {
            parsed.forEach { drawPath(it, color) }
            if (one) drawPath(Path().apply { moveTo(10.0f, 10.3f); lineTo(12.4f, 8.4f); lineTo(12.4f, 15.8f) },
                color, style = Stroke(width = vb / 24f * 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** סמל האפליקציה (התו על רקע צהוב-כתום), מצויר ישירות, זהה לסמל המשגר */
@Composable
private fun AppIcon(size: Dp, corner: Dp) {
    val note = remember { PathParser().parsePathString(NOTE_D).toPath() }
    Canvas(Modifier.size(size).clip(RoundedCornerShape(corner))) {
        val sc = this.size.width / 108f
        drawRect(Brush.linearGradient(0f to Color(0xFFFFFF00), 0.83f to Color(0xFFFFC000), 1f to Color(0xFFFFC000),
            start = Offset(0f, this.size.height), end = Offset(this.size.width, 0f)))
        withTransform({ scale(sc, sc, Offset.Zero); translate(-54.4794f, -7.0197f); scale(0.049309f, 0.049309f, Offset.Zero) }) {
            drawPath(note, Color(0xFF042433),
                style = Stroke(width = 137.5f, cap = StrokeCap.Round, join = StrokeJoin.Miter, miter = 8f))
        }
    }
}

/** רקע כהה ומעומעם: תמונת האלבום מטושטשת ומוחשכת, ובלעדיה מעבר כהה עם הילה צהובה עדינה */
@Composable
private fun Backdrop(blur: ImageBitmap?) {
    Box(Modifier.fillMaxSize()) {
        if (blur != null) {
            Image(blur, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0xBF000000)))
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0xFF22232B), Color(0xFF0C0C10), Color(0xFF000000)))))
        }
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x24FDC100), Color.Transparent))))
    }
}

class MainActivity : ComponentActivity(), KeyActions {
    private var ctl by mutableStateOf<MediaController?>(null)
    private var tick by mutableIntStateOf(0)
    private val stack = mutableStateListOf<String>()
    private val selStack = mutableListOf<Int>()
    private var sel by mutableIntStateOf(0)
    private val cur: String? get() = stack.lastOrNull()
    private var notice by mutableStateOf("")
    private var crashLines: List<String> = emptyList()
    private var artMain by mutableStateOf<ImageBitmap?>(null)
    private var artBlur by mutableStateOf<ImageBitmap?>(null)
    private var optSong: Song? = null
    private var sleepOn by mutableStateOf(false)
    private var sleepJob: Runnable? = null
    private var lastSeek = 0L
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audio: AudioManager
    private lateinit var prefs: SharedPreferences
    private val keys = KeyGestureDetector(this)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        refreshVol()
        prefs = getSharedPreferences("state", MODE_PRIVATE)
        Cfg.load(prefs); Playlists.load(prefs); keys.doubleWindowMs = Cfg.dbl
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(filesDir, "crash.txt").writeText(e.stackTraceToString()) } catch (_: Throwable) {}
            oldHandler?.uncaughtException(t, e)
        }
        File(filesDir, "crash.txt").takeIf { it.exists() }?.let { cf ->
            crashLines = cf.readLines().map { it.trim() }.filter { it.isNotEmpty() }.take(40)
            stack.add("crash"); selStack.add(0)
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        controllerFuture = f
        f.addListener({
            val c = try { f.get() } catch (e: Exception) { showNotice("חיבור לשירות הנגינה נכשל"); return@addListener }
            ctl = c
            c.addListener(object : Player.Listener {
                override fun onEvents(p: Player, e: Player.Events) {
                    tick++
                    if (e.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) && p.playbackState == Player.STATE_ENDED &&
                        Cfg.endAction == 1 && Library.songs.isNotEmpty()) loadQueue(Library.songs, 0)
                }
            })
            tryRestore(); playPending(); sendEq()
        }, MoreExecutors.directExecutor())
        volumeControlStream = AudioManager.STREAM_MUSIC          // מקשי עוצמה = עוצמת המדיה של המערכת
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        hideSystemBars()
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, volObserver)
        ContextCompat.registerReceiver(this, volReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED)
        handleIntent(intent)
        ensureLibrary()
        setContent { Root() }
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var vol by mutableIntStateOf(0)
    private fun refreshVol() { vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC) }
    private val volReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { refreshVol() }      // שינוי עוצמה מיידי מהמערכת
    }
    private val volObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshVol(); tick++ }    // עוצמת המערכת השתנתה (מקשים, Bluetooth...)
    }

    private fun hideSystemBars() {
        val w = WindowInsetsControllerCompat(window, window.decorView)
        w.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        w.hide(WindowInsetsCompat.Type.statusBars())
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); handleIntent(intent); playPending()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(volObserver)
        try { unregisterReceiver(volReceiver) } catch (_: Exception) {}
        controllerFuture?.let { MediaController.releaseFuture(it) }   // השירות והנגינה ממשיכים
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        Cfg.save(prefs); Playlists.save(prefs)
        val c = ctl ?: return
        prefs.edit().putString("mediaId", c.currentMediaItem?.mediaId)
            .putString("queueIds", (0 until c.mediaItemCount).joinToString(",") { c.getMediaItemAt(it).mediaId })
            .putLong("pos", c.currentPosition).putBoolean("playing", c.isPlaying)
            .putFloat("speed", c.playbackParameters.speed).putBoolean("shuffle", c.shuffleModeEnabled)
            .putInt("repeat", c.repeatMode).apply()
    }

    // ================= מקשים =================
    private fun isSoft(k: Int) = k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SOFT_RIGHT || k == KeyEvent.KEYCODE_SOFT_LEFT
    private fun isLR(k: Int) = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (isSoft(k)) return true
        if (cur == "search" && handleSearchKey(k, e)) return true
        if (cur != "search" && (k == KeyEvent.KEYCODE_POUND || k == KeyEvent.KEYCODE_STAR)) {
            if (e.repeatCount == 0) { if (k == KeyEvent.KEYCODE_POUND) cycleRepeat() else toggleShuffle() }
            return true
        }
        if (cur == "eqmanual" && isLR(k)) { adjustBand(if (k == KeyEvent.KEYCODE_DPAD_RIGHT) 10 else -10); return true }
        return keys.onKeyDown(k, e) || super.onKeyDown(k, e)
    }

    override fun onKeyUp(k: Int, e: KeyEvent): Boolean {
        if (cur == "eqmanual" && isLR(k)) return true
        if (cur != "search" && (k == KeyEvent.KEYCODE_POUND || k == KeyEvent.KEYCODE_STAR)) return true
        when (k) {
            KeyEvent.KEYCODE_SOFT_LEFT -> { leftAction(); return true }       // אפשרויות / בחר
            KeyEvent.KEYCODE_MENU -> { leftAction(); return true }              // "open menu"
            KeyEvent.KEYCODE_SOFT_RIGHT -> { back(); return true }            // אחורה
        }
        return keys.onKeyUp(k, e) || super.onKeyUp(k, e)
    }

    private fun leftAction() {
        val k = cur
        if (k == null) { optSong = null; push("options"); return }
        val sc = screenFor(k)
        if (sc.menu) sc.ok(sel) else { optSong = sc.songs?.getOrNull(sel); push("options") }
    }

    override fun volumeStep(delta: Int) {
        cur?.let { k -> sel = (sel - delta).mod(maxOf(1, screenFor(k).items.size)); return }
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        refreshVol(); tick++
    }
    override fun next() { ctl?.seekToNextMediaItem() }
    override fun previous() { ctl?.seekToPreviousMediaItem() }
    override fun restart() { ctl?.seekTo(0) }
    override fun seekStart(dir: Int) { lastSeek = 0; seekTick(dir) }
    override fun seekTick(dir: Int) {
        val c = ctl ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSeek < 250) return
        lastSeek = now
        c.seekTo((c.currentPosition + dir * Cfg.skipSec * 1000L).coerceIn(0L, maxOf(0L, c.duration)))
    }
    override fun seekEnd() {}
    override fun ok() { val k = cur; if (k == null) togglePlay() else screenFor(k).ok(sel) }
    override fun back() {
        if (stack.isNotEmpty()) {
            if (stack.last() == "crash") File(filesDir, "crash.txt").delete()
            stack.removeAt(stack.lastIndex); sel = selStack.removeLastOrNull() ?: 0
        }
    }
    /** # : חזרה על הכל (כל התיקייה/הרשימה) ← חזרה על שיר אחד ← ללא חזרה */
    private fun cycleRepeat() {
        val c = ctl ?: return
        val order = listOf(Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_OFF)
        val next = order[(order.indexOf(c.repeatMode) + 1).mod(order.size)]
        c.repeatMode = next
        showNotice(when (next) { Player.REPEAT_MODE_ALL -> "חזרה על הכל"; Player.REPEAT_MODE_ONE -> "חזרה על שיר אחד"; else -> "ללא חזרה" })
    }

    /** * : הפעלה/כיבוי של ערבוב */
    private fun toggleShuffle() {
        val c = ctl ?: return
        val on = !c.shuffleModeEnabled
        c.shuffleModeEnabled = on
        showNotice(if (on) "ערבוב פעיל" else "ערבוב כבוי")
    }

    private fun togglePlay() { ctl?.let { if (it.isPlaying) it.pause() else it.play() } }

    private fun push(k: String) {
        selStack.add(sel); stack.add(k); sel = startIdx(k)
        if (k == "search") resetSearch()
    }
    private fun clearStack() { stack.clear(); selStack.clear(); sel = 0 }

    private fun showNotice(m: String) {
        notice = m; handler.removeCallbacksAndMessages("n")
        handler.postAtTime({ notice = "" }, "n", SystemClock.uptimeMillis() + 2500)
    }

    // ================= מסכים =================
    private fun startIdx(k: String): Int = when (k) {
        "set:repeat" -> RM.indexOf(ctl?.repeatMode ?: 0).coerceAtLeast(0)
        "set:end" -> Cfg.endAction
        "set:shuffle" -> if (ctl?.shuffleModeEnabled == true) 1 else 0
        "set:speed" -> Menu.speeds.indexOf(ctl?.playbackParameters?.speed ?: 1f).coerceAtLeast(0)
        "set:skip" -> Cfg.skipIdx
        "set:input" -> if (Cfg.t9) 0 else 1
        "set:lang" -> if (Cfg.inputHe) 0 else 1
        "set:dbl" -> Cfg.dblIdx
        "eq" -> if (Cfg.eqPreset < 0) presets.size else Cfg.eqPreset
        "nowplaying" -> ctl?.currentMediaItemIndex ?: 0
        else -> 0
    }

    private fun songScreen(title: String?, list: List<Song>) = Screen(title,
        list.map { it.title }.ifEmpty { listOf("ריק") }, notes = list.size, songs = list,
        ok = { i -> if (i < list.size) playQueue(list, i) })

    private fun menuScreen(title: String, items: List<Pair<String, () -> Unit>>) =
        Screen(title, items.map { it.first }, menu = true, ok = { i -> items.getOrNull(i)?.second?.invoke() })

    private fun pick(key: String, title: String, labels: List<String>, onPick: (Int) -> Unit): Screen {
        val now = startIdx(key)
        return Screen(title, labels.mapIndexed { i, l -> (if (i == now) "● " else "") + l }, menu = true,
            ok = { i -> onPick(i); Cfg.save(prefs); back() })
    }

    private fun screenFor(k: String): Screen = when {
        k == "options" -> menuScreen("אפשרויות", listOf(
            "מושמע כעת" to { push("nowplaying") },
            "רשימות השמעה" to { push("playlists") },
            "חיפוש" to { push("search") },
            "הוספה לרשימה" to { push("addto") },
            "הגדרה כצלצול" to { setRingtone() },
            "הפעלת טיימר שינה" to { push("sleep") },
            "הספרייה" to { push("library") },
            "פרטי השיר" to { push("details") },
            "הגדרות" to { push("settings") }))
        k == "library" -> menuScreen("הספרייה", listOf(
            "כל השירים" to { push("songs") }, "אמנים" to { push("artists") },
            "אלבומים" to { push("albums") }, "תיקיות" to { push("folders") }))
        k == "songs" -> songScreen("כל השירים", Library.songs)
        k == "artists" -> Screen("אמנים", Library.artists.ifEmpty { listOf("ריק") }, menu = true,
            ok = { i -> Library.artists.getOrNull(i)?.let { push("artist:$it") } })
        k == "albums" -> Screen("אלבומים", Library.albums.map { it.second }.ifEmpty { listOf("ריק") }, menu = true,
            ok = { i -> Library.albums.getOrNull(i)?.let { push("album:${it.first}") } })
        k == "folders" -> Screen("תיקיות",
            Library.folders.map { it.split('/').takeLast(2).joinToString("/") }.ifEmpty { listOf("ריק") }, menu = true,
            ok = { i -> Library.folders.getOrNull(i)?.let { push("folder:$it") } })
        k.startsWith("artist:") -> songScreen(k.removePrefix("artist:"),
            Library.songs.filter { it.artist == k.removePrefix("artist:") })
        k.startsWith("album:") -> songScreen(Library.albumLabel(k.removePrefix("album:").toLongOrNull() ?: 0),
            Library.songs.filter { it.albumId == k.removePrefix("album:").toLongOrNull() }.sortedBy { it.track })
        k.startsWith("folder:") -> songScreen(k.substringAfterLast('/'),
            Library.songs.filter { it.folder == k.removePrefix("folder:") })
        k == "nowplaying" -> {
            val c = ctl; val n = c?.mediaItemCount ?: 0
            val t = (0 until n).map { c!!.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "—" }
            Screen("מושמע כעת", t.ifEmpty { listOf("ריק") }, notes = n,
                ok = { i -> if (i < n) { c?.seekTo(i, 0L); c?.play(); clearStack() } })
        }
        k == "playlists" -> {
            val names = Playlists.names
            Screen("רשימות השמעה", names + "＋ רשימה חדשה", notes = names.size, ok = { i ->
                if (i < names.size) push("pl:${names[i]}")
                else { showNotice("נוצרה ${Playlists.create()}"); Playlists.save(prefs) } })
        }
        k.startsWith("pl:") -> {
            val byId = Library.songs.associateBy { it.id }
            songScreen(k.removePrefix("pl:"), Playlists.ids(k.removePrefix("pl:")).mapNotNull { byId[it] })
        }
        k == "addto" -> {
            val names = Playlists.names
            Screen("הוספה לרשימה", names + "＋ רשימה חדשה", notes = names.size, menu = true, ok = { i ->
                val song = optSong ?: currentSong()
                if (song == null) showNotice("אין שיר") else {
                    val n = if (i < names.size) names[i] else Playlists.create()
                    Playlists.add(n, song.id); Playlists.save(prefs); showNotice("נוסף ל$n")
                }
                clearStack() })
        }
        k == "search" -> Screen("חיפוש  ${if (searchT9) "T9" else "ABC"}  $query",
            results.map { it.title }.ifEmpty { listOf(if (query.isEmpty()) "הקלד לחיפוש" else "אין תוצאות") },
            notes = results.size, songs = results,
            ok = { i -> if (i < results.size) playQueue(results, i) })
        k == "details" -> Screen("פרטי השיר", detailsLines())
        k == "sleep" -> menuScreen("טיימר שינה",
            Menu.sleepMinutes.map { m -> "$m דקות" to { setSleep(m); clearStack(); showNotice("טיימר: $m דקות") } } +
                ("ביטול טיימר" to { setSleep(null); clearStack() }))
        k == "settings" -> menuScreen("הגדרות", listOf(
            "סוג חזרה" to { push("set:repeat") }, "בסיום תור" to { push("set:end") },
            "אקראי" to { push("set:shuffle") }, "מהירות השמעה" to { push("set:speed") },
            "אקוליייזר" to { push("eq") }, "הגדרת שניות לדילוג" to { push("set:skip") },
            "שיטת קלט בחיפוש" to { push("set:input") }, "שפת קלט בחיפוש" to { push("set:lang") },
            "חלון לחיצה כפולה" to { push("set:dbl") }))
        k == "set:repeat" -> pick(k, "סוג חזרה", listOf("ללא", "הכל", "שיר אחד")) { ctl?.repeatMode = RM[it] }
        k == "set:end" -> pick(k, "בסיום תור", listOf("עצור", "המשך בכל הספרייה")) { Cfg.endAction = it }
        k == "set:shuffle" -> pick(k, "אקראי", listOf("כבוי", "פעיל")) { ctl?.shuffleModeEnabled = it == 1 }
        k == "set:speed" -> pick(k, "מהירות השמעה", Menu.speeds.map { "${it}x" }) { ctl?.setPlaybackSpeed(Menu.speeds[it]) }
        k == "set:skip" -> pick(k, "שניות לדילוג", Cfg.skipOptions.map { "$it שניות" }) { Cfg.skipIdx = it }
        k == "set:input" -> pick(k, "קלט חיפוש", listOf("T9", "Multi-Tap")) { Cfg.t9 = it == 0 }
        k == "set:lang" -> pick(k, "שפת קלט", listOf("עברית", "English")) { Cfg.inputHe = it == 0 }
        k == "set:dbl" -> pick(k, "חלון לחיצה כפולה", listOf("200ms", "280ms", "400ms")) { Cfg.dblIdx = it; keys.doubleWindowMs = Cfg.dbl }
        k == "eq" -> Screen("אקוליייזר", eqItems(), menu = true, ok = { selectEq(it) })
        k == "eqmanual" -> Screen("אקוליייזר – ידני", manualItems(), menu = true)
        k == "crash" -> Screen("קריסה קודמת", crashLines, small = true)
        else -> Screen(null, listOf("—"))
    }

    // ================= UI =================
    @Composable
    private fun Root() {
        tick; libLoaded; Playlists.version
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                val s = maxHeight.value / 815f
                val mid = ctl?.currentMediaItem?.mediaId
                LaunchedEffect(ctl, mid) {
                    val item = ctl?.currentMediaItem
                    val a = withContext(Dispatchers.IO) { loadArt(item) }
                    artMain = a?.main; artBlur = a?.blur
                }
                Backdrop(artBlur)
                val sc = cur?.let { screenFor(it) }
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    StatusBar(s)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (sc == null) PlayerView(s) else ListView(sc, s)
                        if (notice.isNotEmpty()) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .background(Gry).padding((14 * s).dp)) { Tx(notice, 38 * s, align = TextAlign.Center, mod = Modifier.fillMaxWidth()) }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
                    Row(Modifier.fillMaxWidth().height((68 * s).dp).padding(horizontal = (16 * s).dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        SvgIcon(ICON_BACK, 24f, (40 * s).dp)
                        Spacer(Modifier.weight(1f))
                        if (sc == null || !sc.menu) SvgIcon(ICON_OPTIONS, 512f, (38 * s).dp) else SvgIcon(ICON_CHECK, 24f, (40 * s).dp)
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusBar(s: Float) {
        var time by remember { mutableStateOf("") }
        var batt by remember { mutableFloatStateOf(1f) }
        var charging by remember { mutableStateOf(false) }
        var btState by remember { mutableIntStateOf(0) }       // 0 כבוי, 1 דלוק, 2 מחובר
        var wifi by remember { mutableIntStateOf(0) }          // 0 כבוי, 1 דלוק, 2 מחובר
        var wifiLvl by remember { mutableIntStateOf(0) }
        var bars by remember { mutableIntStateOf(0) }
        var noSig by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (true) {
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                batt = if (l < 0) 1f else l / (i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).toFloat()
                charging = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
                btState = bluetoothState()
                val (ws, wl) = wifiState(); wifi = ws; wifiLvl = wl
                val (b, off) = signalState(); bars = b; noSig = off
                delay(3000)
            }
        }
        Row(Modifier.fillMaxWidth().height((46 * s).dp).padding(horizontal = (12 * s).dp),
            verticalAlignment = Alignment.CenterVertically) {
            Tx(time, 36 * s); Spacer(Modifier.width((10 * s).dp))
            Ico("batt", (44 * s).dp, level = batt, flag = charging)
            Spacer(Modifier.weight(1f))
            if (sleepOn) { Ico("clock", (34 * s).dp); Spacer(Modifier.width((10 * s).dp)) }
            Ico("bt", (34 * s).dp, color = when (btState) { 2 -> Yel; 1 -> Color.White; else -> Color(0xFF777777) }, off = btState == 0)
            Spacer(Modifier.width((10 * s).dp))
            Ico("wifi", (36 * s).dp, color = if (wifi == 0) Color(0xFF777777) else Color.White,
                level = if (wifi == 2) minOf(1f, (wifiLvl + 1) / 3f) else 0f, off = wifi == 0)
            Spacer(Modifier.width((10 * s).dp))
            Ico("sig", (34 * s).dp, level = bars / 4f, off = noSig)      // קליטה בקצה השמאלי
        }
    }

    @Composable
    private fun ListView(sc: Screen, s: Float) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowH = 100 * s
            val rows = ((maxHeight.value - (if (sc.title != null) rowH else 0f)) / rowH).toInt().coerceAtLeast(1)
            val n = sc.items.size
            val start = (sel - rows / 2).coerceIn(0, maxOf(0, n - rows))
            Column {
                sc.title?.let {
                    Row(Modifier.fillMaxWidth().height(rowH.dp).background(Gry).padding(horizontal = (24 * s).dp),
                        verticalAlignment = Alignment.CenterVertically) { Tx(it, 52 * s) }
                }
                for (i in start until minOf(n, start + rows)) {
                    Row(Modifier.fillMaxWidth().height(rowH.dp).background(if (i == sel) Yel else Color.Transparent)
                        .padding(horizontal = (24 * s).dp), verticalAlignment = Alignment.CenterVertically) {
                        if (i < sc.notes) { Tx("♪", 54 * s); Spacer(Modifier.width((14 * s).dp)) }
                        Tx(sc.items[i], (if (sc.small) 30 else 46) * s, lines = if (sc.small) 2 else 1)
                    }
                }
            }
        }
    }

    @Composable
    private fun PlayerView(s: Float) {
        tick
        val c = ctl
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(c) { while (true) { pos = c?.currentPosition ?: 0L; delay(400) } }
        LaunchedEffect(Unit) { while (true) { refreshVol(); delay(150) } }   // עוצמה אמיתית של המכשיר
        val md = c?.mediaMetadata
        val dur = (c?.duration ?: 0L).coerceAtLeast(0L)
        val vmax = maxOf(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        val vv = vol
        val rep = c?.repeatMode ?: Player.REPEAT_MODE_OFF
        val art = artMain
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(bottom = (204 * s).dp, start = (16 * s).dp, end = (16 * s).dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (art != null) Image(art, null, Modifier.size((230 * s).dp).clip(RoundedCornerShape((18 * s).dp)),
                    contentScale = ContentScale.Crop)
                else AppIcon((230 * s).dp, (18 * s).dp)
                Spacer(Modifier.height((8 * s).dp))
                Tx(md?.artist?.toString() ?: "", 30 * s, Color(0xFFCCCCCC), align = TextAlign.Center, mod = Modifier.fillMaxWidth())
                Tx(md?.albumTitle?.toString() ?: "", 24 * s, Color(0xFF9A9AA0), align = TextAlign.Center, mod = Modifier.fillMaxWidth())
                Tx(md?.title?.toString() ?: "—", 41 * s, align = TextAlign.Center, mod = Modifier.fillMaxWidth())
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val shape = RoundedCornerShape(topStart = (34 * s).dp, topEnd = (34 * s).dp)
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE67884A0), Color(0xE62E364D))), shape)
                    .padding(horizontal = (30 * s).dp, vertical = (14 * s).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tx("−", 40 * s); Spacer(Modifier.width((6 * s).dp))
                        Canvas(Modifier.size((190 * s).dp, (24 * s).dp)) {
                            // קובייה לכל דרגת עוצמה אמיתית של המערכת
                            val n = minOf(vmax, 30); val gap = size.width / n
                            val on = if (vmax <= 30) vv else Math.round(vv * n / vmax.toFloat())
                            for (i in 0 until n) drawRoundRect(if (i < on) Yel else Color(0x59FFFFFF),
                                Offset(i * gap + gap * 0.12f, 0f), Size(gap * 0.62f, size.height), CornerRadius(gap * 0.15f))
                        }
                        Spacer(Modifier.width((6 * s).dp)); Tx("+", 40 * s)
                        Spacer(Modifier.weight(1f))
                        Ico("shuffle", (40 * s).dp, if (c?.shuffleModeEnabled == true) Yel else Color.White)
                        Spacer(Modifier.width((12 * s).dp))
                        SvgIcon(ICON_REPEAT, 24f, (40 * s).dp, if (rep != Player.REPEAT_MODE_OFF) Yel else Color.White, one = rep == Player.REPEAT_MODE_ONE)
                    }
                    Spacer(Modifier.height((8 * s).dp))
                    Box(Modifier.fillMaxWidth().height((3 * s).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(50))
                        .background(Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f)
                            .fillMaxHeight().background(Yel))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Tx(fmt(pos), 26 * s); Tx(fmt(dur), 26 * s)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        Ico("prev", (62 * s).dp)
                        Ico(if (c?.isPlaying == true) "pause" else "play", (84 * s).dp)
                        Ico("next", (62 * s).dp)
                    }
                }
            }
        }
    }

    private fun fmt(ms: Long) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)
    private fun currentSong() = Library.songs.firstOrNull { it.id.toString() == ctl?.currentMediaItem?.mediaId }

    private fun detailsLines(): List<String> {
        val c = ctl ?: return listOf("—")
        val md = c.mediaMetadata
        val uri = c.currentMediaItem?.localConfiguration?.uri
        var size = "?"
        if (uri != null) try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) size = "%.1f MB".format(it.getLong(0) / 1048576.0)
            }
        } catch (_: Exception) {}
        return listOf("שם: ${md.title ?: "—"}", "אמן: ${md.artist ?: "—"}", "אלבום: ${md.albumTitle ?: "—"}",
            "קובץ: ${uri?.lastPathSegment ?: "—"}", "גודל: $size",
            "פורמט: ${uri?.let { contentResolver.getType(it) } ?: "?"}",
            "משך: ${fmt(c.duration.coerceAtLeast(0))}", "נתיב: ${currentSong()?.path ?: uri ?: "—"}")
    }

    // ================= פעולות =================
    private fun setSleep(min: Int?) {
        sleepJob?.let { handler.removeCallbacks(it) }; sleepJob = null; sleepOn = min != null
        if (min != null) {
            val r = Runnable { ctl?.pause(); sleepJob = null; sleepOn = false }  // עצירת השמעה בלבד
            sleepJob = r; handler.postDelayed(r, min * 60_000L)
        }
    }

    private fun setRingtone() {
        val song = optSong ?: currentSong() ?: return showNotice("אין שיר")
        if (!Settings.System.canWrite(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            showNotice("אשר הרשאה ונסה שוב"); return
        }
        try {
            RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, song.uri)
            showNotice("הוגדר כצלצול"); clearStack()
        } catch (_: Exception) { showNotice("ההגדרה נכשלה") }
    }

    private fun loadQueue(list: List<Song>, index: Int, pos: Long = 0L) {
        val c = ctl ?: return
        c.setMediaItems(list.map { it.toMediaItem() }, index, pos); c.prepare(); c.play()
    }
    private fun playQueue(list: List<Song>, index: Int) { loadQueue(list, index); clearStack() }

    private fun loadArt(item: MediaItem?): Art? {
        val uri = item?.localConfiguration?.uri ?: return null
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        var bmp: Bitmap? = null
        try {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(this, uri); r.embeddedPicture?.let { bmp = BitmapFactory.decodeByteArray(it, 0, it.size, opts) } }
            finally { r.release() }
        } catch (_: Exception) {}
        if (bmp == null) item.mediaMetadata.artworkUri?.let { art ->
            try { contentResolver.openInputStream(art)?.use { bmp = BitmapFactory.decodeStream(it, null, opts) } } catch (_: Exception) {}
        }
        val b = bmp ?: return null
        val small = Bitmap.createScaledBitmap(b, 16, 16, true)      // הקטנה קיצונית + הגדלה חלקה = טשטוש
        return Art(b.asImageBitmap(), small.asImageBitmap())
    }

    // ================= חיפוש (T9 / Multi-Tap) =================
    private var query by mutableStateOf("")
    private var results by mutableStateOf<List<Song>>(emptyList())
    private var searchT9 by mutableStateOf(true)
    private var searchHe by mutableStateOf(true)
    private var lastDigit = ' '
    private var lastTap = 0L
    private var tapIdx = 0

    private fun resetSearch() { query = ""; results = emptyList(); lastDigit = ' '; searchT9 = Cfg.t9; searchHe = Cfg.inputHe }

    /** 0-9 מקלידים, * מחליף שפה, # מחליף T9/Multi-Tap, C/Del מוחק */
    private fun handleSearchKey(k: Int, e: KeyEvent): Boolean {
        if (k in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            if (e.repeatCount == 0) typeDigit('0' + (k - KeyEvent.KEYCODE_0)); return true
        }
        val first = e.repeatCount == 0
        when (k) {
            KeyEvent.KEYCODE_STAR -> if (first) searchHe = !searchHe
            KeyEvent.KEYCODE_POUND -> if (first) { searchT9 = !searchT9; query = ""; lastDigit = ' '; updateSearch() }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_CLEAR -> if (first) { query = query.dropLast(1); lastDigit = ' '; updateSearch() }
            else -> return false
        }
        return true
    }

    private fun typeDigit(d: Char) {
        if (searchT9) query += d else {
            val chars = KeypadMap.layout(searchHe, d); if (chars.isEmpty()) return
            val now = System.currentTimeMillis()
            if (d == lastDigit && now - lastTap < 900 && chars.length > 1 && query.isNotEmpty()) {
                tapIdx = (tapIdx + 1) % chars.length; query = query.dropLast(1) + chars[tapIdx]
            } else { tapIdx = 0; query += chars[0] }
            lastDigit = d; lastTap = now
        }
        updateSearch()
    }

    private fun updateSearch() {
        val q = if (searchT9) query else KeypadMap.norm(query.trim())
        results = if (q.isEmpty()) emptyList() else {
            val idx = if (searchT9) Library.titleDigits else Library.titleNorm
            Library.songs.filterIndexed { i, _ -> idx[i].contains(q) }
        }
        sel = 0
    }

    // ================= אקוליייזר =================
    private val presets = listOf(
        "Normal" to intArrayOf(0, 0, 0, 0, 0), "Bass" to intArrayOf(70, 45, 0, 0, 0),
        "Treble" to intArrayOf(0, 0, 0, 45, 70), "Vocal" to intArrayOf(-30, 0, 50, 40, -10),
        "Rock" to intArrayOf(50, 25, -20, 30, 55), "Classical" to intArrayOf(35, 20, -10, 25, 40))
    private val bandNames = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    private var eqOk by mutableStateOf<Boolean?>(null)

    private fun sendEq() {
        val c = ctl ?: return
        val lv = presets.getOrNull(Cfg.eqPreset)?.second ?: Cfg.eqManual
        val f = c.sendCustomCommand(SessionCommand("eq", Bundle.EMPTY), Bundle().apply { putIntArray("levels", lv) })
        f.addListener({ eqOk = runCatching { f.get().resultCode == SessionResult.RESULT_SUCCESS }.getOrDefault(false) },
            MoreExecutors.directExecutor())
    }

    private fun eqItems(): List<String> =
        presets.mapIndexed { i, p -> (if (i == Cfg.eqPreset) "● " else "") + p.first } +
            ((if (Cfg.eqPreset < 0) "● " else "") + "ידני") +
            (if (eqOk == false) listOf("לא נתמך במכשיר זה") else emptyList())

    private fun manualItems() = bandNames.mapIndexed { i, n ->
        val v = Cfg.eqManual[i]; val f = (v + 100) / 25
        "$n ${"▮".repeat(f)}${"▯".repeat(8 - f)} ${v / 10}"
    }

    private fun selectEq(i: Int) {
        if (i > presets.size) return
        Cfg.eqPreset = if (i == presets.size) -1 else i
        Cfg.save(prefs); sendEq()
        if (i == presets.size) push("eqmanual")
    }

    private fun adjustBand(d: Int) {
        val b = sel.coerceIn(0, 4)
        Cfg.eqManual[b] = (Cfg.eqManual[b] + d).coerceIn(-100, 100)
        Cfg.save(prefs); sendEq(); tick++
    }

    // ================= סטטוס: קליטה ו-Bluetooth =================
    /** (פסי קליטה 0..4, אין שירות/אין SIM/מצב טיסה) */
    private fun signalState(): Pair<Int, Boolean> {
        val airplane = Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (airplane || tm == null || tm.simState != TelephonyManager.SIM_STATE_READY) return 0 to true
        val lvl = try { if (Build.VERSION.SDK_INT >= 28) tm.signalStrength?.level ?: 0 else 0 } catch (_: Exception) { 0 }
        return lvl to false
    }

    /** 0 כבוי, 1 דלוק ולא מחובר, 2 מחובר */
    private fun bluetoothState(): Int = try {
        val ad = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (ad == null || !ad.isEnabled) 0 else {
            var connected = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (!connected) try {
                connected = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
                    .any { ad.getProfileConnectionState(it) == BluetoothProfile.STATE_CONNECTED }
            } catch (_: SecurityException) {}
            if (connected) 2 else 1
        }
    } catch (_: Exception) { 0 }

    /** (מצב: 0 כבוי, 1 דלוק ולא מחובר, 2 מחובר ; רמת קליטה 0..3) */
    private fun wifiState(): Pair<Int, Int> = try {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wm.isWifiEnabled) 0 to 0 else {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) 1 to 0 else {
                @Suppress("DEPRECATION")
                val rssi = wm.connectionInfo?.rssi ?: -100
                @Suppress("DEPRECATION")
                val lvl = WifiManager.calculateSignalLevel(rssi, 4)
                2 to lvl.coerceIn(0, 3)
            }
        }
    } catch (_: Exception) { 0 to 0 }

    // ================= פתיחת קבצי שמע מבחוץ =================
    private var pendingUri: Uri? = null
    private var pendingPos = 0L

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_VIEW && i.data != null) { pendingUri = i.data; pendingPos = i.getLongExtra("pos", 0L); i.action = null }
    }

    private fun displayName(u: Uri): String? = try {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }

    private fun sizeOf(u: Uri): Long = try {
        contentResolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
    } catch (_: Exception) { -1L }

    /** מנגן את הקובץ שנפתח: אם הוא בספרייה — כחלק ממנה (קודם/הבא עובדים), אחרת כקובץ בודד. */
    private fun playPending() {
        val c = ctl ?: return
        val u = pendingUri ?: return
        if (!libLoaded && granted(audioPerm)) return           // ממתין לסיום הסריקה
        val startPos = pendingPos; pendingPos = 0L
        pendingUri = null
        val name = if (u.scheme == "file") u.lastPathSegment else displayName(u)
        val sz = if (u.scheme == "file") -1L else sizeOf(u)
        val songs = Library.songs
        var idx = songs.indexOfFirst { if (u.scheme == "file") it.path == u.path else it.uri == u }
        if (idx < 0 && name != null)
            idx = songs.indexOfFirst { File(it.path).name == name && (sz < 0 || it.size == sz) }
        if (idx >= 0) { loadQueue(songs, idx, startPos); clearStack(); return }
        try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val item = MediaItem.Builder().setUri(u).setMediaId(u.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(name?.substringBeforeLast('.') ?: "—").build()).build()
        c.setMediaItem(item, startPos); c.prepare(); c.play(); clearStack()
    }

    // ================= ספרייה והרשאות =================
    private var libLoaded by mutableStateOf(false)
    private var restored = false
    private var scanning = false
    private val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { if (!libLoaded) scanAsync() }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun ensureLibrary() {
        val need = mutableListOf<String>()
        if (!granted(audioPerm)) need += audioPerm
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
            need += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT))
            need += Manifest.permission.BLUETOOTH_CONNECT
        if (granted(audioPerm)) scanAsync()
        if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
    }

    private fun scanAsync() {
        if (!granted(audioPerm) || scanning) return
        scanning = true
        Thread {
            try { Library.scan(this) } catch (e: Exception) { runOnUiThread { showNotice("סריקת הספרייה נכשלה") } }
            runOnUiThread { scanning = false; libLoaded = true; tryRestore(); playPending() }
        }.start()
    }

    /** שחזור: התור האחרון שנבחר אם קיים, אחרת כל הספרייה */
    private fun tryRestore() {
        val c = ctl ?: return
        if (pendingUri != null) return
        if (restored || !libLoaded) return
        restored = true
        if (c.mediaItemCount > 0) return
        val songs = Library.songs; if (songs.isEmpty()) return
        val byId = songs.associateBy { it.id.toString() }
        val saved = prefs.getString("queueIds", null)?.split(',')?.mapNotNull { byId[it] }.orEmpty()
        val queue = saved.ifEmpty { songs }
        val id = prefs.getString("mediaId", null)
        val idx = queue.indexOfFirst { it.id.toString() == id }.coerceAtLeast(0)
        c.setMediaItems(queue.map { it.toMediaItem() }, idx, prefs.getLong("pos", 0L))
        c.setPlaybackSpeed(prefs.getFloat("speed", 1f))
        c.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
        c.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
        c.prepare()
        if (prefs.getBoolean("playing", false)) c.play()
    }
}


/**
 * חלון קטן שנפתח כשפותחים קובץ שמע ממנהל הקבצים: מנגן מיד.
 * ←/→ ארוך = הרצה אחורה/קדימה, אישור (או "פתח") = ממשיך באפליקציה מאותו מקום, אחורה = עוצר לצמיתות וסוגר.
 */
class QuickPlayActivity : ComponentActivity(), KeyActions {
    private var player: ExoPlayer? = null
    private var uri: Uri? = null
    private var closing = false
    private var handedOff = false
    private var tick by mutableIntStateOf(0)
    private var failed by mutableStateOf(false)
    private var fileName = "—"
    private var lastSeek = 0L
    private lateinit var audio: AudioManager
    private val keys = KeyGestureDetector(this)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC
        Cfg.load(getSharedPreferences("state", MODE_PRIVATE)); keys.doubleWindowMs = Cfg.dbl
        val u = intent?.data
        if (u == null) { finish(); return }
        uri = u
        val raw = if (u.scheme == "file") u.lastPathSegment else queryName(u)
        fileName = raw?.substringBeforeLast('.') ?: "—"
        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true).build()
        p.addListener(object : Player.Listener {
            override fun onEvents(pl: Player, e: Player.Events) { tick++ }
            override fun onPlayerError(error: PlaybackException) { failed = true; tick++ }
        })
        p.setMediaItem(MediaItem.fromUri(u)); p.prepare(); p.play()
        player = p
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { stopAndClose() }
        })
        setContent { QuickView() }
    }

    private fun queryName(u: Uri): String? = try {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }

    /** יציאה חזרה למנהל הקבצים: השיר נעצר לצמיתות */
    private fun stopAndClose() {
        if (closing) return
        closing = true
        player?.stop(); player?.release(); player = null
        finish()
    }

    /** אישור: מעבירים לאפליקציה הראשית ממש מהמקום שבו עצרנו */
    private fun openInApp() {
        val p = player ?: return
        val u = uri ?: return
        if (closing) return
        closing = true; handedOff = true
        val pos = p.currentPosition
        p.release(); player = null
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW; data = u; putExtra("pos", pos)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }

    override fun onStop() { super.onStop(); if (!handedOff && !closing) stopAndClose() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }

    // ---- מקשים ----
    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (k == KeyEvent.KEYCODE_SOFT_LEFT || k == KeyEvent.KEYCODE_SOFT_RIGHT || k == KeyEvent.KEYCODE_MENU) return true
        return keys.onKeyDown(k, e) || super.onKeyDown(k, e)
    }
    override fun onKeyUp(k: Int, e: KeyEvent): Boolean {
        when (k) {
            KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU -> { openInApp(); return true }
            KeyEvent.KEYCODE_SOFT_RIGHT -> { stopAndClose(); return true }
        }
        return keys.onKeyUp(k, e) || super.onKeyUp(k, e)
    }
    override fun volumeStep(delta: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }
    override fun next() {}
    override fun previous() {}
    override fun restart() { player?.seekTo(0) }
    override fun seekStart(dir: Int) { lastSeek = 0; seekTick(dir) }
    override fun seekTick(dir: Int) {
        val p = player ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSeek < 250) return
        lastSeek = now
        p.seekTo((p.currentPosition + dir * Cfg.skipSec * 1000L).coerceIn(0L, maxOf(0L, p.duration)))
    }
    override fun seekEnd() {}
    override fun ok() { openInApp() }
    override fun back() { stopAndClose() }

    @Composable
    private fun QuickView() {
        tick
        val p = player
        var pos by remember { mutableLongStateOf(0L) }
        LaunchedEffect(p) { while (true) { pos = p?.currentPosition ?: 0L; delay(400) } }
        val md = p?.mediaMetadata
        val dur = (p?.duration ?: 0L).coerceAtLeast(0L)
        val title = md?.title?.toString() ?: fileName
        val artist = md?.artist?.toString().orEmpty()
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                val s = maxHeight.value / 815f
                Column(Modifier.fillMaxWidth(0.92f)
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A4562), Color(0xFF151A2A))), RoundedCornerShape((30 * s).dp))
                    .padding(horizontal = (28 * s).dp, vertical = (24 * s).dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Tx(title, 40 * s, align = TextAlign.Center, mod = Modifier.fillMaxWidth())
                    if (artist.isNotBlank()) Tx(artist, 28 * s, Color(0xFFCCCCCC), align = TextAlign.Center, mod = Modifier.fillMaxWidth())
                    if (failed) Tx("לא ניתן לנגן את הקובץ", 28 * s, Color(0xFFFF8A80), align = TextAlign.Center, mod = Modifier.fillMaxWidth())
                    Spacer(Modifier.height((16 * s).dp))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().height((3 * s).dp.coerceAtLeast(2.dp)).clip(RoundedCornerShape(50))
                                .background(Color(0x66000000))) {
                                Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f)
                                    .fillMaxHeight().background(Yel))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Tx("%02d:%02d".format(pos / 60000, pos / 1000 % 60), 26 * s)
                                Tx("%02d:%02d".format(dur / 60000, dur / 1000 % 60), 26 * s)
                            }
                        }
                    }
                    Spacer(Modifier.height((8 * s).dp))
                    AppIcon((86 * s).dp, (20 * s).dp)
                    Spacer(Modifier.height((14 * s).dp))
                    Row(Modifier.fillMaxWidth()) {
                        SvgIcon(ICON_BACK, 24f, (38 * s).dp)
                        Spacer(Modifier.weight(1f))
                        SvgIcon(ICON_CHECK, 24f, (38 * s).dp)
                    }
                }
            }
        }
    }
}
