package com.daniel.mood

/**
 * Mood vocabulary and small formatting helpers.
 *
 * Pure Kotlin (no Android imports) so it can be unit tested on the JVM.
 * The classifier works only on decoded audio samples; this object only holds labels.
 */
object MoodCatalog {
    const val ALL = -1
    const val HAPPY = 0
    const val SAD = 1
    const val CALM = 2
    const val RAINY = 3
    const val NOSTALGIC = 4
    const val ROMANTIC = 5
    const val SPORT = 6
    const val ENERGETIC = 7
    const val EMOTIONAL = 8
    const val INSTRUMENTAL = 9
    const val FOCUS = 10
    const val NIGHT = 11
    const val REVIEW = 12
    const val COUNT = 13

    /** Moods a user may choose manually (REVIEW is a system state, not a real mood). */
    val selectable: List<Int> = (HAPPY..NIGHT).toList()

    private val namesFa = listOf(
        "شاد", "غمگین", "آرام", "بارونی", "نوستالژیک", "عاشقانه",
        "ورزشی", "پرانرژی", "احساسی", "بی\u200cکلام", "تمرکز و مطالعه",
        "شبانه", "نیازمند بررسی"
    )
    private val namesEn = listOf(
        "Happy", "Sad", "Calm", "Rainy", "Nostalgic", "Romantic",
        "Sport", "Energetic", "Emotional", "Instrumental", "Focus & Study",
        "Night & Dreamy", "Needs review"
    )
    private val glyphs = listOf("☀", "☔", "🌿", "🌧", "📻", "♥", "🏃", "⚡", "💫", "🎼", "✎", "☾", "?")

    /** ARGB accent for each mood; used by the theme, cards and the player. */
    private val accents = intArrayOf(
        0xFFFFC24D.toInt(), // Happy
        0xFF7C8CF8.toInt(), // Sad
        0xFF4FD1B0.toInt(), // Calm
        0xFF5AA9E6.toInt(), // Rainy
        0xFFC89B6A.toInt(), // Nostalgic
        0xFFF27BA9.toInt(), // Romantic
        0xFFFF7043.toInt(), // Sport
        0xFFFF5C8A.toInt(), // Energetic
        0xFF9D8CFF.toInt(), // Emotional
        0xFF49C6E5.toInt(), // Instrumental
        0xFF8BC34A.toInt(), // Focus
        0xFF6C63C7.toInt(), // Night
        0xFF8A90A8.toInt()  // Needs review
    )

    fun name(id: Int, persian: Boolean): String =
        (if (persian) namesFa else namesEn).getOrElse(id) { if (persian) "?" else "?" }

    fun glyph(id: Int): String = glyphs.getOrElse(id) { "♫" }
    fun color(id: Int): Int = accents.getOrElse(id) { accents[REVIEW] }

    /** Subjective labels need more evidence before being presented as certain. */
    fun isSubjective(id: Int): Boolean =
        id == RAINY || id == NOSTALGIC || id == ROMANTIC ||
            id == EMOTIONAL || id == INSTRUMENTAL || id == NIGHT

    fun tr(persian: Boolean, english: String, persianText: String): String =
        if (persian) persianText else english

    fun digits(value: Any, persian: Boolean): String {
        val text = value.toString()
        if (!persian) return text
        return text.map { c -> if (c in '0'..'9') ('۰'.code + c.code - '0'.code).toChar() else c }
            .joinToString("")
    }

    fun clock(ms: Long, persian: Boolean): String {
        val seconds = (ms.coerceAtLeast(0L)) / 1000L
        val text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        return digits(text, persian)
    }
}
