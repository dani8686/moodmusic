package com.daniel.mood

/** Queue rules that are independent of ExoPlayer and can be unit tested on the JVM. */
object QueueLogic {
    const val REPEAT_OFF = 0
    const val REPEAT_ONE = 1
    const val REPEAT_ALL = 2

    fun cycleRepeat(mode: Int): Int = when (mode) {
        REPEAT_OFF -> REPEAT_ONE
        REPEAT_ONE -> REPEAT_ALL
        else -> REPEAT_OFF
    }

    fun targetIndex(from: Int, to: Int, size: Int): Int {
        if (size <= 0) return 0
        return to.coerceIn(0, size - 1)
    }

    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (items.isEmpty() || from !in items.indices) return items
        val target = targetIndex(from, to, items.size)
        if (from == target) return items
        val result = items.toMutableList()
        val value = result.removeAt(from)
        result.add(target, value)
        return result
    }
}
