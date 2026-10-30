package com.westly.twelvepits.feature.game.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Elapsed time counts only while the game screen is open and not paused. */
class PlayClockTest {
    private var time = 1_000L
    private val clock = PlayClock(now = { time })

    @Test
    fun aNewClockShowsZeroAndDoesNotRun() {
        assertEquals(0L, clock.elapsedMs())
        assertFalse(clock.isRunning)
        time += 5_000
        assertEquals(0L, clock.elapsedMs())
    }

    @Test
    fun itCountsWhileRunning() {
        clock.start()
        time += 2_500
        assertEquals(2_500L, clock.elapsedMs())
        time += 500
        assertEquals(3_000L, clock.elapsedMs())
        assertTrue(clock.isRunning)
    }

    @Test
    fun timeInAPauseOrTheBackgroundIsNotCounted() {
        clock.start()
        time += 10_000
        clock.stop()
        time += 60_000
        assertEquals(10_000L, clock.elapsedMs())
        clock.start()
        time += 3_000
        clock.stop()
        assertEquals(13_000L, clock.elapsedMs())
    }

    @Test
    fun startingTwiceOrStoppingTwiceChangesNothing() {
        clock.start()
        time += 1_000
        clock.start()
        time += 1_000
        clock.stop()
        clock.stop()
        assertEquals(2_000L, clock.elapsedMs())
    }

    @Test
    fun aResumedGameContinuesFromItsSavedTime() {
        val resumed = PlayClock(now = { time }, initialMs = 400_000L)
        assertEquals(400_000L, resumed.elapsedMs())
        resumed.start()
        time += 1_500
        assertEquals(401_500L, resumed.elapsedMs())
    }

    @Test
    fun resetStopsAndStartsAgain() {
        clock.start()
        time += 9_000
        clock.reset()
        assertFalse(clock.isRunning)
        assertEquals(0L, clock.elapsedMs())
        clock.reset(2_000L)
        assertEquals(2_000L, clock.elapsedMs())
    }

    @Test
    fun aClockThatGoesBackwardsNeverGivesNegativeTime() {
        clock.start()
        time -= 5_000
        assertEquals(0L, clock.elapsedMs())
        clock.stop()
        assertEquals(0L, clock.elapsedMs())
        assertEquals(0L, PlayClock(now = { time }, initialMs = -5L).elapsedMs())
    }
}
