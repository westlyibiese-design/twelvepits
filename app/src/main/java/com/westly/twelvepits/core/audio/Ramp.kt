package com.westly.twelvepits.core.audio

/** Moves a level toward a target at a fixed speed. Pure Kotlin, no Android imports. */
object Ramp {
    /**
     * The new level after [elapsedMs] milliseconds. A move over the whole range 0 to 1 takes
     * [fullRangeMs]; a shorter move takes proportionally less. The level never passes [target].
     */
    fun step(current: Float, target: Float, elapsedMs: Float, fullRangeMs: Float): Float {
        if (fullRangeMs <= 0f) return target
        val change = elapsedMs.coerceAtLeast(0f) / fullRangeMs
        return when {
            current < target -> minOf(target, current + change)
            current > target -> maxOf(target, current - change)
            else -> target
        }
    }
}
