package com.shneyni.nokiaplayer4

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

interface KeyActions {
    fun volumeStep(delta: Int)          // +1 / -1
    fun next()
    fun previous()
    fun restart()
    fun seekStart(dir: Int)             // +1 FF, -1 RW
    fun seekTick(dir: Int)
    fun seekEnd()
    fun ok()
    fun back()
}

/**
 * מפריד בין לחיצה קצרה / כפולה / ארוכה / Key Repeat.
 * הלחיצה הארוכה מזוהה לפי repeatCount > 0 (מערכת אנדרואיד שולחת repeat רק בהחזקה),
 * ולכן לעולם לא תתפרש כלחיצה קצרה או כפולה.
 */
class KeyGestureDetector(
    private val a: KeyActions,
    var doubleWindowMs: Long = 280L,
) {
    private val h = Handler(Looper.getMainLooper())
    private var longActive = false
    private var secondTap = false
    private var pending: Runnable? = null

    fun onKeyDown(code: Int, e: KeyEvent): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
            // לחיצה קצרה = דרגה אחת; החזקה = Key Repeat = דרגה בכל אירוע
            a.volumeStep(if (code == KeyEvent.KEYCODE_DPAD_UP) 1 else -1); true
        }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
            val dir = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
            if (e.repeatCount == 0) {
                // לחיצה שנייה בתוך החלון? מבטל את ה-Restart הממתין
                secondTap = pending != null
                pending?.let { h.removeCallbacks(it) }
                pending = null
            } else {
                if (!longActive) {
                    longActive = true; secondTap = false
                    pending?.let { h.removeCallbacks(it) }; pending = null
                    a.seekStart(dir)
                } else a.seekTick(dir)
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true
        KeyEvent.KEYCODE_BACK -> true
        else -> false
    }

    fun onKeyUp(code: Int, e: KeyEvent): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
            val dir = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
            if (longActive) {
                longActive = false; a.seekEnd()
            } else if (dir == 1) {
                a.next()                       // ימינה: מיידי
            } else if (secondTap) {
                secondTap = false; a.previous() // שמאלה x2
            } else {
                val r = Runnable { pending = null; a.restart() } // שמאלה x1 אחרי חלון
                pending = r; h.postDelayed(r, doubleWindowMs)
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { a.ok(); true }
        KeyEvent.KEYCODE_BACK -> { a.back(); true }
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
        else -> false
    }
}
