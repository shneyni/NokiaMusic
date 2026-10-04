package com.shneyni.nokiaplayer4

/** מיפוי מקשי מספרים → אותיות (עברית + אנגלית), לשימוש ב-T9 וב-Multi-Tap. */
object KeypadMap {
    private val en = mapOf('2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz")
    private val he = mapOf('2' to "אבג", '3' to "דהו", '4' to "זחט", '5' to "יכל",
        '6' to "מנס", '7' to "עפצ", '8' to "קרש", '9' to "ת")
    private val digitOf: Map<Char, Char> = buildMap {
        en.forEach { (d, s) -> s.forEach { put(it, d) } }
        he.forEach { (d, s) -> s.forEach { put(it, d) } }
    }

    fun layout(hebrew: Boolean, d: Char): String = when (d) {
        '0' -> " "
        '1' -> ".,'-1"
        else -> (if (hebrew) he else en)[d] ?: ""
    }

    /** אותיות קטנות + אותיות סופיות לרגילות, כדי שהתאמה תעבוד גם על ך/ם/ן/ף/ץ */
    fun norm(s: String) = s.lowercase().map {
        when (it) { 'ך' -> 'כ'; 'ם' -> 'מ'; 'ן' -> 'נ'; 'ף' -> 'פ'; 'ץ' -> 'צ'; else -> it }
    }.joinToString("")

    /** כותרת שיר → רצף ספרות כפי שהיה מוקלד ב-T9 (רווח=0, סימן=1) */
    fun toDigits(s: String) = norm(s).map {
        digitOf[it] ?: if (it.isDigit()) it else if (it == ' ') '0' else '1'
    }.joinToString("")
}
