package com.westly.twelvepits.feature.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTimelineTest {
    @Test
    fun prepWeightsAddUpToOneHundredPercent() {
        assertEquals(100, PrepStep.entries.sumOf { it.weightPercent })
        assertEquals(1f, LaunchTimeline.realProgress(PrepStep.entries), 0.0001f)
    }

    @Test
    fun barStartsAtZeroAndEndsAtOne() {
        assertEquals(0f, LaunchTimeline.animationTarget(0f), 0.0001f)
        assertEquals(1f, LaunchTimeline.animationTarget(100000f), 0.0001f)
    }

    @Test
    fun barNeverGoesBackwards() {
        var last = 0f
        var ms = 0f
        while (ms <= 4000f) {
            val now = LaunchTimeline.animationTarget(ms)
            assertTrue(now >= last)
            last = now
            ms += 16f
        }
    }

    @Test
    fun barWaitsForRealPreparation() {
        val partial = listOf(PrepStep.SETTINGS_READ)
        assertEquals(0.3f, LaunchTimeline.shownProgress(100000f, partial), 0.0001f)
        assertEquals(1f, LaunchTimeline.shownProgress(100000f, PrepStep.entries), 0.0001f)
    }

    @Test
    fun animationFinishesInUnderFourSeconds() {
        val finishAt = LaunchTimeline.SEEDS_START_MS + LaunchTimeline.SEEDS_SPAN_MS
        val handOverAt = finishAt + LaunchTimeline.FLASH_MS * LaunchTimeline.NAVIGATE_AT_FLASH
        assertTrue(handOverAt < 4000f)
        assertTrue(finishAt + LaunchTimeline.FLASH_MS <= 4000f)
    }

    @Test
    fun seedsAppearOneByOneUpToFortyFour() {
        assertEquals(0, LaunchTimeline.seedsShown(0f))
        assertEquals(22, LaunchTimeline.seedsShown(0.5f))
        assertEquals(44, LaunchTimeline.seedsShown(1f))
    }

    @Test
    fun percentIsClampedToZeroAndHundred() {
        assertEquals(0, LaunchTimeline.percent(-1f))
        assertEquals(62, LaunchTimeline.percent(0.62f))
        assertEquals(100, LaunchTimeline.percent(2f))
    }
}
