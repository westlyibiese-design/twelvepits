package com.westly.twelvepits.feature.game.data

/**
 * Counts the time a player spends on the game screen: it runs only between [start] and [stop], so
 * pauses and the time in the background are left out. [now] gives milliseconds from any steady clock
 * (the game uses `SystemClock.elapsedRealtime`); tests give a fake one.
 */
class PlayClock(
    private val now: () -> Long,
    initialMs: Long = 0L,
) {
    private var accumulated: Long = initialMs.coerceAtLeast(0L)
    private var startedAt: Long = NOT_RUNNING

    val isRunning: Boolean
        get() = startedAt != NOT_RUNNING

    /** Starts counting. Does nothing if it already counts. */
    fun start() {
        if (!isRunning) startedAt = now()
    }

    /** Stops counting and keeps the time. Does nothing if it is stopped. */
    fun stop() {
        if (isRunning) {
            accumulated += sinceStart()
            startedAt = NOT_RUNNING
        }
    }

    /** Stops and starts again from [initialMs]. */
    fun reset(initialMs: Long = 0L) {
        startedAt = NOT_RUNNING
        accumulated = initialMs.coerceAtLeast(0L)
    }

    /** The counted time in milliseconds, including the part that is running now. */
    fun elapsedMs(): Long = accumulated + if (isRunning) sinceStart() else 0L

    private fun sinceStart(): Long = (now() - startedAt).coerceAtLeast(0L)

    private companion object {
        const val NOT_RUNNING = -1L
    }
}
