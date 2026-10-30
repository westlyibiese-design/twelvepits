package com.westly.twelvepits.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPatternsTest {
    @Test
    fun validTapIsTenMilliseconds() {
        assertArrayEquals(longArrayOf(0, 10), HapticPatterns.timings(HapticEffect.PIT_TAP))
    }

    @Test
    fun captureIsThirtyFiveMilliseconds() {
        assertArrayEquals(longArrayOf(0, 35), HapticPatterns.timings(HapticEffect.CAPTURE))
    }

    @Test
    fun skippedTurnIsTwoTwentyMillisecondPulsesSixtyApart() {
        assertArrayEquals(longArrayOf(0, 20, 60, 20), HapticPatterns.timings(HapticEffect.TURN_SKIPPED))
    }

    @Test
    fun invalidMoveIsFifteenMilliseconds() {
        assertArrayEquals(longArrayOf(0, 15), HapticPatterns.timings(HapticEffect.INVALID_MOVE))
    }

    @Test
    fun defeatIsOneEightyMillisecondPulse() {
        assertArrayEquals(longArrayOf(0, 80), HapticPatterns.timings(HapticEffect.DEFEAT))
    }

    @Test
    fun victoryRisesInThreePulses() {
        val timings = HapticPatterns.timings(HapticEffect.VICTORY)
        val pulses = listOf(timings[1], timings[3], timings[5])
        assertEquals(pulses.sorted(), pulses)
        assertTrue(pulses.first() < pulses.last())
        val amplitudes = requireNotNull(HapticPatterns.amplitudes(HapticEffect.VICTORY))
        assertEquals(timings.size, amplitudes.size)
        assertTrue(amplitudes[1] < amplitudes[3] && amplitudes[3] < amplitudes[5])
    }

    @Test
    fun everyPatternIsValidForTheSystem() {
        for (effect in HapticEffect.entries) {
            val timings = HapticPatterns.timings(effect)
            assertEquals("$effect starts with a wait of zero", 0L, timings[0])
            assertTrue("$effect has at least one pulse", timings.any { it > 0L })
            assertTrue("$effect has no negative time", timings.all { it >= 0L })
            val amplitudes = HapticPatterns.amplitudes(effect)
            if (amplitudes != null) {
                assertEquals(timings.size, amplitudes.size)
                assertTrue(amplitudes.all { it in 0..255 })
            }
        }
    }

    @Test
    fun onlyTheVictoryVariesItsStrength() {
        for (effect in HapticEffect.entries) {
            if (effect != HapticEffect.VICTORY) assertNull(HapticPatterns.amplitudes(effect))
        }
    }
}
