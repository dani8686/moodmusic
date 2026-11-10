package com.daniel.mood

/** Escape LIKE wildcards so a search term is treated as literal text. */
object SearchQuery {
    const val SORT_TITLE = 0
    const val SORT_ARTIST = 1
    const val SORT_DATE = 2
    const val SORT_BPM = 3
    const val SORT_ENERGY = 4
    const val SORT_DURATION = 5
    const val SORT_COUNT = 6
    const val PAGE_SIZE = 120

    fun escapeLike(raw: String): String = buildString {
        for (c in raw) {
            when (c) {
                '!' -> append("!!")
                '%' -> append("!%")
                '_' -> append("!_")
                else -> append(c)
            }
        }
    }

    fun pattern(raw: String): String = "%${escapeLike(raw.trim())}%"
}
