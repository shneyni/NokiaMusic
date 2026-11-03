package com.shneyni.nokiaplayer4

import android.content.SharedPreferences
import androidx.compose.runtime.*

object Cfg {
    var skipIdx by mutableIntStateOf(1)
    var resumeMin by mutableIntStateOf(10)        // שמירת מיקום: 5 / 10 / 15 דקות, 0 = כבוי
    var pauseAtZero by mutableStateOf(false)      // עצירת השמעה כשהעוצמה מגיעה ל-0
    var endAction by mutableIntStateOf(2)         // בסיום תור (במצב חזרה): 0 = עצור, 1 = המשך לבא בתור, 2 = חזור על הנוכחי
    var eqPreset by mutableIntStateOf(0)          // -1 = ידני
    var eqManual = IntArray(5)

    val skipOptions = intArrayOf(3, 5, 10, 15, 30)
    val skipSec get() = skipOptions[skipIdx]
    val dbl: Long get() = 300L                    // חלון לחיצה כפולה (קבוע)

    // קריאה ישירה מ-SharedPreferences (גם עבור השירות)
    fun resumeMinFrom(p: SharedPreferences): Int =
        if (p.contains("rmin")) p.getInt("rmin", 10) else if (p.getBoolean("rp", true)) 10 else 0
    fun endActionFrom(p: SharedPreferences) = p.getInt("end2", 2)
    fun pauseZeroFrom(p: SharedPreferences) = p.getBoolean("pz", false)

    fun load(p: SharedPreferences) {
        skipIdx = p.getInt("skip", 1).coerceIn(0, skipOptions.size - 1)
        endAction = endActionFrom(p); eqPreset = p.getInt("eqp", 0)
        resumeMin = resumeMinFrom(p); pauseAtZero = pauseZeroFrom(p)
        p.getString("eqm", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 }?.let { eqManual = it.toIntArray() }
    }

    fun save(p: SharedPreferences) {
        p.edit().putInt("skip", skipIdx).putInt("rmin", resumeMin).putBoolean("pz", pauseAtZero)
            .putInt("end2", endAction).putInt("eqp", eqPreset)
            .putString("eqm", eqManual.joinToString(",")).apply()
    }
}
