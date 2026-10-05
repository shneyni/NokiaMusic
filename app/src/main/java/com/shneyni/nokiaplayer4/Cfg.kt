package com.shneyni.nokiaplayer4

import android.content.SharedPreferences
import androidx.compose.runtime.*

object Cfg {
    var t9 by mutableStateOf(true)
    var inputHe by mutableStateOf(true)
    var dblIdx by mutableIntStateOf(1)
    var skipIdx by mutableIntStateOf(1)
    var resumePrompt by mutableStateOf(true)      // לשאול "להמשיך מהמקום שעצרת?" בשירים של 10 דקות ומעלה
    var endAction by mutableIntStateOf(0)        // 0 = עצור, 1 = המשך בכל הספרייה
    var eqPreset by mutableIntStateOf(0)         // -1 = ידני
    var eqManual = IntArray(5)

    val skipOptions = intArrayOf(3, 5, 10, 15, 30)
    val skipSec get() = skipOptions[skipIdx]
    val dbl get() = longArrayOf(200L, 280L, 400L)[dblIdx]

    fun load(p: SharedPreferences) {
        t9 = p.getBoolean("t9", true); inputHe = p.getBoolean("he", true)
        dblIdx = p.getInt("dbl", 1); skipIdx = p.getInt("skip", 1)
        endAction = p.getInt("end", 0); eqPreset = p.getInt("eqp", 0)
        resumePrompt = p.getBoolean("rp", true)
        p.getString("eqm", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 }?.let { eqManual = it.toIntArray() }
    }

    fun save(p: SharedPreferences) {
        p.edit().putBoolean("t9", t9).putBoolean("he", inputHe).putInt("dbl", dblIdx)
            .putInt("skip", skipIdx).putBoolean("rp", resumePrompt).putInt("end", endAction).putInt("eqp", eqPreset)
            .putString("eqm", eqManual.joinToString(",")).apply()
    }
}
