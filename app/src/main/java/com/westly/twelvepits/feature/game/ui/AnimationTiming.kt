package com.westly.twelvepits.feature.game.ui

/**
 * Every duration of RULES.md section 11 for one seed speed, in seconds.
 * `f = hopSeconds / 0.40`: Slow f = 1.625, Normal f = 1.0, Fast f = 0.5. All durations scale with f.
 */
class AnimationTiming(val hopSeconds: Float) {
    val f: Float = hopSeconds / BASE_HOP_SECONDS

    /** Seeds lift out of a pit. */
    val pickUp: Float = 0.30f * f

    /** One seed hops to the next pit. */
    val hop: Float = BASE_HOP_SECONDS * f

    /** Wait before a relay pick-up while the pit glows softly. */
    val relayWait: Float = 0.35f * f

    /** Emerald glow of a pit that became 4. */
    val captureGlow: Float = 0.25f * f

    /** The 4 captured seeds fly into the store. */
    val captureFly: Float = 0.60f * f

    /** The "turn skipped" banner. */
    val skippedBanner: Float = 1.5f * f

    /** The "+4" that floats up from the store. */
    val plusFloat: Float = 0.90f * f

    companion object {
        const val BASE_HOP_SECONDS = 0.40f

        /** After the last capture the final board stays still for this long. */
        const val GAME_OVER_HOLD = 1.0f

        /** Seeds still in hand at game over fade out during the first part of the hold. */
        const val HAND_FADE = 0.5f

        /** Time the invalid-move shake of a pit lasts. */
        const val SHAKE_SECONDS = 0.30f

        val NORMAL = AnimationTiming(BASE_HOP_SECONDS)
    }
}
