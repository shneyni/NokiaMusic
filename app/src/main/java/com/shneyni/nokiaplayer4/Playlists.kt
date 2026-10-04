package com.shneyni.nokiaplayer4

import android.content.SharedPreferences
import androidx.compose.runtime.*

object Playlists {
    var version by mutableIntStateOf(0)
    private val m = LinkedHashMap<String, MutableList<Long>>()
    val names get() = m.keys.toList()
    fun ids(n: String) = m[n]?.toList().orEmpty()

    fun create(): String {
        var i = m.size + 1
        while ("רשימה $i" in m) i++
        return "רשימה $i".also { m[it] = mutableListOf(); version++ }
    }
    fun add(n: String, id: Long) { m[n]?.let { if (id !in it) it += id }; version++ }

    fun load(p: SharedPreferences) {
        m.clear()
        p.getString("pl", null)?.split('\n')?.forEach {
            val x = it.split('\t')
            if (x.size == 2) m[x[0]] = x[1].split(',').mapNotNull { s -> s.toLongOrNull() }.toMutableList()
        }
        version++
    }
    fun save(p: SharedPreferences) {
        p.edit().putString("pl", m.entries.joinToString("\n") { it.key + "\t" + it.value.joinToString(",") }).apply()
    }
}
