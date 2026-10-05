package com.shneyni.nokiaplayer4

import android.content.Context

/** מיקום השמעה אחרון של שירים ארוכים (10 דקות ומעלה), לפי מזהה השיר. */
object ResumeStore {
    private fun sp(c: Context) = c.getSharedPreferences("resume", Context.MODE_PRIVATE)
    fun get(c: Context, id: String): Long? { val v = sp(c).getLong(id, -1L); return if (v >= 0) v else null }
    fun has(c: Context, id: String) = sp(c).contains(id)
    fun put(c: Context, id: String, pos: Long) { sp(c).edit().putLong(id, pos).apply() }
    fun remove(c: Context, id: String) { sp(c).edit().remove(id).apply() }
}
