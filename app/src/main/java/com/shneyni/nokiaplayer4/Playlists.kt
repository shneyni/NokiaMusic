package com.shneyni.nokiaplayer4

import android.content.SharedPreferences
import androidx.compose.runtime.*

object Playlists {
    var version by mutableIntStateOf(0)
    private val m = LinkedHashMap<String, MutableList<Long>>()
    val names get() = m.keys.toList()
    fun ids(n: String) = m[n]?.toList().orEmpty()

    private fun clean(s: String) = s.replace(Regex("[\\t\\r\\n]"), " ").trim()

    /** יוצר רשימה. בלי שם (או שם ריק) – "רשימה N". שם קיים – מוסיף מספר. */
    fun create(name: String? = null): String {
        var base = clean(name ?: "")
        if (base.isEmpty()) { var i = m.size + 1; while ("רשימה $i" in m) i++; base = "רשימה $i" }
        var n = base; var k = 2
        while (n in m) { n = "$base $k"; k++ }
        m[n] = mutableListOf(); version++
        return n
    }
    fun add(n: String, id: Long) { m[n]?.let { if (id !in it) it += id }; version++ }
    fun delete(n: String) { m.remove(n); version++ }

    /** משנה שם ושומר על הסדר. מחזיר null אם השם כבר קיים. */
    fun rename(old: String, new: String): String? {
        val c = clean(new)
        if (c.isEmpty() || c == old) return old
        if (c in m) return null
        val entries = m.entries.map { (if (it.key == old) c else it.key) to it.value }
        m.clear(); entries.forEach { m[it.first] = it.second }
        version++
        return c
    }

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
