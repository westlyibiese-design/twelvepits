package com.westly.twelvepits.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RampTest {
    @Test
    fun halfTheTimeMovesHalfTheRange() {
        assertEquals(0.5f, Ramp.step(0f, 1f, 200f, 400f), 0.0001f)
    }

    @Test
    fun theLevelNeverPassesTheTarget() {
        assertEquals(1f, Ramp.step(0.9f, 1f, 400f, 400f), 0f)
        assertEquals(0f, Ramp.step(0.1f, 0f, 400f, 400f), 0f)
    }

    @Test
    fun aFadeOutMovesDown() {
        assertEquals(0.75f, Ramp.step(1f, 0f, 100f, 400f), 0.0001f)
    }

    @Test
    fun aShortMoveTakesProportionallyLess() {
        // From 0.3 up to 0.4 is a tenth of the range: 40 ms of a 400 ms fade.
        assertEquals(0.4f, Ramp.step(0.3f, 0.4f, 40f, 400f), 0.0001f)
    }

    @Test
    fun noTimeOrAnEmptyRangeBehavesSafely() {
        assertEquals(0.2f, Ramp.step(0.2f, 1f, 0f, 400f), 0f)
        assertEquals(0.2f, Ramp.step(0.2f, 1f, -50f, 400f), 0f)
        assertEquals(1f, Ramp.step(0.2f, 1f, 10f, 0f), 0f)
    }

    @Test
    fun aFullFadeTakesTheGivenTime() {
        var level = 0f
        var elapsed = 0f
        while (level < 1f) {
            level = Ramp.step(level, 1f, 16f, MusicPolicy.FADE_IN_MS)
            elapsed += 16f
        }
        // 400 ms in steps of 16 ms ends on the 25th step.
        assertEquals(400f, elapsed, 20f)
    }
}
