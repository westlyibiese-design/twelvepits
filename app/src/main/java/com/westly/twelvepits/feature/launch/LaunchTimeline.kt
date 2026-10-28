package com.westly.twelvepits.feature.launch

import kotlin.math.floor
import kotlin.math.roundToInt

/** What the launch screen prepares for real, and how much of the loading bar each step is worth. */
enum class PrepStep(val weightPercent: Int) {
    SETTINGS_READ(30),
    GEOMETRY_PARSED(20),
    FONTS_LOADED(20),
    SOUNDS_LOADED(30),
}

/** Timing and maths of the launch animation. All times are in milliseconds from the first frame. */
object LaunchTimeline {
    const val ENTER_MS = 1400f
    const val LOGO_START_MS = 700f
    const val LOGO_RISE_MS = 900f
    const val LOADER_START_MS = 900f
    const val SEEDS_START_MS = 450f
    const val SEEDS_SPAN_MS = 2300f
    const val SEED_DROP_MS = 450f
    const val SKIP_AFTER_MS = 1000f
    const val FLASH_MS = 600f
    const val SEED_COUNT = 44

    /** The flash is at its brightest at this fraction, and the cross-fade to the menu starts then. */
    const val NAVIGATE_AT_FLASH = 0.5f

    private fun ease(t: Float): Float =
        if (t < 0.5f) 2f * t * t else 1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f

    /** How far the loading bar would be at [elapsedMs] if loading were instant. Goes from 0 to 1. */
    fun animationTarget(elapsedMs: Float): Float {
        val raw = ((elapsedMs - SEEDS_START_MS) / SEEDS_SPAN_MS).coerceIn(0f, 1f)
        return 0.15f * raw + 0.85f * ease(raw)
    }

    /** Share of the real preparation that is finished, from 0 to 1. */
    fun realProgress(done: Collection<PrepStep>): Float =
        done.toSet().sumOf { it.weightPercent } / 100f

    /** The bar never runs ahead of the real preparation. */
    fun shownProgress(elapsedMs: Float, done: Collection<PrepStep>): Float =
        minOf(animationTarget(elapsedMs), realProgress(done))

    fun percent(progress: Float): Int = (progress * 100f).roundToInt().coerceIn(0, 100)

    fun seedsShown(progress: Float): Int =
        floor(progress * SEED_COUNT + 0.0001f).toInt().coerceIn(0, SEED_COUNT)

    /** 0 to 1 over the length of the flash. */
    fun flashProgress(elapsedMs: Float, flashStartMs: Float): Float =
        ((elapsedMs - flashStartMs) / FLASH_MS).coerceIn(0f, 1f)
}
