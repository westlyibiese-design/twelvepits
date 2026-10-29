package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.SowEvent

/** The sounds the animation asks for. The game screen plays them; tests only count them. */
interface AnimationSounds {
    /** Seeds lifted out of a pit (the first pick-up and every relay pick-up). */
    fun pickUp()

    /** One seed lands in a pit. The player picks one of the three drop sounds at random. */
    fun drop()

    /** Four seeds are captured into a store. */
    fun capture()

    /** A turn is skipped. */
    fun turnSkipped()
}

/** Used to jump to the end of a turn without replaying any sound. */
object SilentSounds : AnimationSounds {
    override fun pickUp() {
    }

    override fun drop() {
    }

    override fun capture() {
    }

    override fun turnSkipped() {
    }
}

/**
 * Plays the engine's `List<SowEvent>` of one turn as the movements of RULES.md section 11, by
 * changing a [BoardDisplayState]. Pure Kotlin: it never waits. The caller gives it the time that
 * passed ([advance]), so tests use a fake clock and the game screen uses the frame clock.
 *
 * When the list is finished, the display counts equal the engine's new state exactly.
 * The duration of every step is read when that step begins, so a new seed speed applies from the next step.
 */
class SowAnimator(
    private val display: BoardDisplayState,
    private val events: List<SowEvent>,
    private val timingProvider: () -> AnimationTiming,
    private val sounds: AnimationSounds,
) {
    private var index = 0
    private var started = false
    private var elapsed = 0f
    private var duration = 0f
    private var timing: AnimationTiming = timingProvider()
    private var captureFlyBegun = false
    private var silent = false
    private var cleaned = false

    /** True when every event has been played. */
    val isFinished: Boolean
        get() = index >= events.size

    /** True once the game-over event (and its 1 s hold) has been played. */
    var gameOverReached: Boolean = false
        private set

    /** Moves the animation forward by [deltaSeconds]. A large value plays several steps at once. */
    fun advance(deltaSeconds: Float) {
        var remaining = if (deltaSeconds.isNaN() || deltaSeconds < 0f) 0f else deltaSeconds
        while (index < events.size) {
            val event = events[index]
            if (!started) {
                begin(event)
                started = true
                elapsed = 0f
            }
            val needed = duration - elapsed
            if (remaining >= needed) {
                remaining -= needed
                elapsed = duration
                update(event)
                complete(event)
                index += 1
                started = false
            } else {
                elapsed += remaining
                update(event)
                return
            }
        }
        if (!cleaned) {
            cleaned = true
            display.clearSequence()
        }
    }

    /** Jumps to the final state of the turn at once, with no sound (the app went to the background). */
    fun finishInstantly() {
        silent = true
        advance(Float.MAX_VALUE)
    }

    private val audio: AnimationSounds
        get() = if (silent) SilentSounds else sounds

    private fun begin(event: SowEvent) {
        timing = timingProvider()
        captureFlyBegun = false
        when (event) {
            is SowEvent.PickUp -> {
                display.relayGlowPit = -1
                display.relayGlow = 0f
                display.pits[event.pit] = 0
                display.handActive = true
                display.handPit = event.pit
                display.handCount = event.count
                display.handLift = 0f
                display.handAlpha = 1f
                audio.pickUp()
                duration = timing.pickUp
            }
            is SowEvent.Drop -> {
                if (!display.handActive) {
                    // Cannot happen with the engine's events; keeps the display safe if it ever does.
                    display.handActive = true
                    display.handPit = (event.pit + GameState.PIT_COUNT - 1) % GameState.PIT_COUNT
                    display.handCount = event.inHandAfter + 1
                    display.handLift = 1f
                    display.handAlpha = 1f
                }
                display.hopActive = true
                display.hopFrom = display.handPit
                display.hopTo = event.pit
                display.hopProgress = 0f
                duration = timing.hop
            }
            is SowEvent.RelayPause -> {
                display.relayGlowPit = event.pit
                display.relayGlow = 0f
                duration = timing.relayWait
            }
            is SowEvent.Capture -> {
                display.captureGlowPit = event.pit
                display.captureGlow = 0f
                duration = timing.captureGlow + timing.captureFly
            }
            is SowEvent.TurnSkipped -> {
                display.bannerPlayer = event.player
                display.bannerProgress = 0f
                audio.turnSkipped()
                duration = timing.skippedBanner
            }
            is SowEvent.GameOver -> {
                duration = AnimationTiming.GAME_OVER_HOLD
            }
            is SowEvent.LoopStopped -> {
                duration = 0f
            }
            is SowEvent.StepLimitReached -> {
                duration = 0f
            }
        }
    }

    private fun update(event: SowEvent) {
        val progress = if (duration <= 0f) 1f else (elapsed / duration).coerceIn(0f, 1f)
        when (event) {
            is SowEvent.PickUp -> display.handLift = progress
            is SowEvent.Drop -> display.hopProgress = progress
            is SowEvent.RelayPause -> display.relayGlow = progress
            is SowEvent.Capture -> updateCapture(event)
            is SowEvent.TurnSkipped -> display.bannerProgress = progress
            is SowEvent.GameOver -> {
                if (display.handActive) {
                    display.handAlpha = 1f - (elapsed / AnimationTiming.HAND_FADE).coerceIn(0f, 1f)
                }
            }
            is SowEvent.LoopStopped -> Unit
            is SowEvent.StepLimitReached -> Unit
        }
    }

    private fun updateCapture(event: SowEvent.Capture) {
        val glowEnd = timing.captureGlow
        if (elapsed < glowEnd) {
            display.captureGlow = if (glowEnd <= 0f) 1f else (elapsed / glowEnd).coerceIn(0f, 1f)
            return
        }
        display.captureGlow = 1f
        if (!captureFlyBegun) {
            captureFlyBegun = true
            display.pits[event.pit] = 0
            display.captureGlowPit = -1
            display.captureFlyActive = true
            display.captureFlyPit = event.pit
            display.captureFlyOwner = event.owner.ordinal
            display.captureFlyFromStore = display.stores[event.owner.ordinal]
            display.captureFlyProgress = 0f
            audio.capture()
        }
        val flyTime = timing.captureFly
        display.captureFlyProgress = if (flyTime <= 0f) 1f else ((elapsed - glowEnd) / flyTime).coerceIn(0f, 1f)
    }

    private fun complete(event: SowEvent) {
        when (event) {
            is SowEvent.PickUp -> display.handLift = 1f
            is SowEvent.Drop -> {
                display.hopActive = false
                display.hopProgress = 0f
                display.pits[event.pit] = event.newCount
                display.handPit = event.pit
                display.handCount = event.inHandAfter
                if (event.inHandAfter <= 0) {
                    display.handActive = false
                    display.handCount = 0
                }
                audio.drop()
            }
            is SowEvent.RelayPause -> Unit
            is SowEvent.Capture -> {
                display.captureFlyActive = false
                display.captureFlyProgress = 0f
                display.captureGlowPit = -1
                display.captureGlow = 0f
                display.stores[event.owner.ordinal] = event.newStoreTotal
                display.addFloater(event.owner)
            }
            is SowEvent.TurnSkipped -> {
                display.bannerPlayer = null
                display.bannerProgress = 0f
            }
            is SowEvent.GameOver -> {
                display.handActive = false
                display.handCount = 0
                display.handAlpha = 1f
                gameOverReached = true
            }
            is SowEvent.LoopStopped -> Unit
            is SowEvent.StepLimitReached -> {
                // The engine put the seeds still in hand back on the board.
                if (display.handActive) {
                    display.pits[display.handPit] += display.handCount
                    display.handActive = false
                    display.handCount = 0
                }
            }
        }
    }
}
