package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.core.model.SeedSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationTimingTest {
    private val delta = 0.0001f

    @Test
    fun theMultiplierIsHopSecondsOverZeroPointFour() {
        assertEquals(1.625f, AnimationTiming(SeedSpeed.SLOW.hopSeconds).f, delta)
        assertEquals(1.0f, AnimationTiming(SeedSpeed.NORMAL.hopSeconds).f, delta)
        assertEquals(0.5f, AnimationTiming(SeedSpeed.FAST.hopSeconds).f, delta)
    }

    @Test
    fun everyDurationOfSectionElevenScalesWithTheMultiplier() {
        for (speed in SeedSpeed.entries) {
            val timing = AnimationTiming(speed.hopSeconds)
            val f = timing.f
            assertEquals(0.30f * f, timing.pickUp, delta)
            assertEquals(0.40f * f, timing.hop, delta)
            assertEquals(speed.hopSeconds, timing.hop, delta)
            assertEquals(0.35f * f, timing.relayWait, delta)
            assertEquals(0.25f * f, timing.captureGlow, delta)
            assertEquals(0.60f * f, timing.captureFly, delta)
            assertEquals(1.5f * f, timing.skippedBanner, delta)
        }
    }

    @Test
    fun normalSpeedValuesAreTheWrittenOnes() {
        val timing = AnimationTiming.NORMAL
        assertEquals(0.30f, timing.pickUp, delta)
        assertEquals(0.40f, timing.hop, delta)
        assertEquals(0.35f, timing.relayWait, delta)
        assertEquals(0.25f, timing.captureGlow, delta)
        assertEquals(0.60f, timing.captureFly, delta)
        assertEquals(1.5f, timing.skippedBanner, delta)
        assertEquals(1.0f, AnimationTiming.GAME_OVER_HOLD, delta)
    }

    @Test
    fun aTurnOfTwentyFiveSeedsTakesAboutTenSecondsAtNormalAndFiveAtFast() {
        val normal = AnimationTiming(SeedSpeed.NORMAL.hopSeconds)
        val fast = AnimationTiming(SeedSpeed.FAST.hopSeconds)
        assertEquals(10.0f, 25 * normal.hop, 0.01f)
        assertEquals(5.0f, 25 * fast.hop, 0.01f)
    }
}
