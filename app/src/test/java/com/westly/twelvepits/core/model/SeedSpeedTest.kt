package com.westly.twelvepits.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeedSpeedTest {
    @Test
    fun hopTimesMatchTheSettingsScreen() {
        assertEquals(0.65f, SeedSpeed.SLOW.hopSeconds, 0f)
        assertEquals(0.40f, SeedSpeed.NORMAL.hopSeconds, 0f)
        assertEquals(0.20f, SeedSpeed.FAST.hopSeconds, 0f)
    }

    @Test
    fun savedHopTimeComesBackAsTheSameSpeed() {
        for (speed in SeedSpeed.entries) {
            assertEquals(speed, SeedSpeed.fromHopSeconds(speed.hopSeconds))
        }
    }

    @Test
    fun missingOrBadDataGivesNormal() {
        assertEquals(SeedSpeed.NORMAL, SeedSpeed.fromHopSeconds(null))
        assertEquals(SeedSpeed.NORMAL, SeedSpeed.fromHopSeconds(Float.NaN))
    }
}
