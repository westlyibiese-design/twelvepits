package com.westly.twelvepits.core.model

import kotlin.math.abs

/** How fast one seed hops from pit to pit. Stored as the hop time in seconds. */
enum class SeedSpeed(val hopSeconds: Float) {
    SLOW(0.65f),
    NORMAL(0.40f),
    FAST(0.20f);

    companion object {
        val DEFAULT: SeedSpeed = NORMAL

        /** The speed closest to a stored hop time. Bad or missing data gives [DEFAULT]. */
        fun fromHopSeconds(seconds: Float?): SeedSpeed {
            if (seconds == null || seconds.isNaN()) return DEFAULT
            return entries.minByOrNull { abs(it.hopSeconds - seconds) } ?: DEFAULT
        }
    }
}
