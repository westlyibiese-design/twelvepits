package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState

/**
 * What the board shows right now: the counts, the group of seeds in hand, the seed that is hopping,
 * the glows, the banner and the small effects. It is separate from the engine state: the animation
 * changes it step by step and, when a turn's animation is finished, it equals the engine state exactly.
 *
 * Mutable on purpose: the draw code reads it every frame and must not allocate. Pure Kotlin.
 * Only the main thread touches it.
 */
class BoardDisplayState {
    /** Seed count of every pit, indexed by engine pit 0 to 11. */
    val pits = IntArray(GameState.PIT_COUNT)

    /** Seed count of each store, indexed by `Player.ordinal`. */
    val stores = IntArray(PLAYER_COUNT)

    // Seeds held in the hand of the player who is sowing.
    var handActive = false
    var handPit = 0
    var handCount = 0

    /** 0 = just lifted out of the pit, 1 = held above the pit. */
    var handLift = 0f

    /** 1 = fully visible, falls to 0 when the leftover seeds fade out at game over. */
    var handAlpha = 1f

    // The one seed that is hopping from the hand's pit to the next pit.
    var hopActive = false
    var hopFrom = 0
    var hopTo = 0
    var hopProgress = 0f

    // Soft glow of a pit that is about to be picked up again (relay).
    var relayGlowPit = -1
    var relayGlow = 0f

    // Emerald glow of a pit that became 4, then the 4 seeds flying to the owner's store.
    var captureGlowPit = -1
    var captureGlow = 0f
    var captureFlyActive = false
    var captureFlyPit = 0
    var captureFlyOwner = 0
    var captureFlyProgress = 0f

    /** The store total before the capture, so the flying seeds land on the right store slots. */
    var captureFlyFromStore = 0

    // "<name> has no seeds. Turn skipped."
    var bannerPlayer: Player? = null
    var bannerProgress = 0f

    // Small "+4" floating up from a store. -1 = unused slot, otherwise progress from 0 to 1.
    val floaterOwner = IntArray(MAX_FLOATERS)
    val floaterProgress = FloatArray(MAX_FLOATERS) { UNUSED }

    // Tiny shake of a pit after an invalid tap.
    var shakePit = -1
    var shakeProgress = 0f

    // Green turn glow. Pit rings only while input is allowed, the store ring for the whole turn.
    var pitGlowPlayer: Player? = null
    var storeGlowPlayer: Player? = null

    /** Puts the board in exactly the position of an engine state and clears every moving part. */
    fun reset(state: GameState) {
        for (pit in 0 until GameState.PIT_COUNT) {
            pits[pit] = state.pits.getOrElse(pit) { 0 }
        }
        stores[Player.ONE.ordinal] = state.storeOne
        stores[Player.TWO.ordinal] = state.storeTwo
        clearSequence()
        clearEffects()
    }

    /** True when the counts are exactly those of the engine state. */
    fun countsMatch(state: GameState): Boolean {
        for (pit in 0 until GameState.PIT_COUNT) {
            if (pits[pit] != state.pits.getOrElse(pit) { -1 }) return false
        }
        return stores[Player.ONE.ordinal] == state.storeOne && stores[Player.TWO.ordinal] == state.storeTwo
    }

    /** True when nothing is in hand, hopping, glowing or flying. */
    val isSettled: Boolean
        get() = !handActive && !hopActive && relayGlowPit < 0 && captureGlowPit < 0 &&
            !captureFlyActive && bannerPlayer == null

    /** Clears the hand, the hop, the glows and the banner. The counts stay as they are. */
    fun clearSequence() {
        handActive = false
        handCount = 0
        handLift = 0f
        handAlpha = 1f
        hopActive = false
        hopProgress = 0f
        relayGlowPit = -1
        relayGlow = 0f
        captureGlowPit = -1
        captureGlow = 0f
        captureFlyActive = false
        captureFlyProgress = 0f
        bannerPlayer = null
        bannerProgress = 0f
    }

    /** Clears the "+4" floaters and the shake. */
    fun clearEffects() {
        for (i in 0 until MAX_FLOATERS) {
            floaterProgress[i] = UNUSED
        }
        shakePit = -1
        shakeProgress = 0f
    }

    fun addFloater(owner: Player) {
        var slot = 0
        for (i in 0 until MAX_FLOATERS) {
            if (floaterProgress[i] == UNUSED) {
                slot = i
                break
            }
        }
        floaterOwner[slot] = owner.ordinal
        floaterProgress[slot] = 0f
    }

    fun startShake(pit: Int) {
        shakePit = pit
        shakeProgress = 0f
    }

    /** True while a "+4" or a shake is still moving. */
    val hasEffects: Boolean
        get() {
            if (shakePit >= 0) return true
            for (i in 0 until MAX_FLOATERS) {
                if (floaterProgress[i] != UNUSED) return true
            }
            return false
        }

    /** Moves the "+4" floaters and the shake forward. */
    fun tickEffects(deltaSeconds: Float, timing: AnimationTiming) {
        for (i in 0 until MAX_FLOATERS) {
            if (floaterProgress[i] != UNUSED) {
                val next = floaterProgress[i] + deltaSeconds / timing.plusFloat
                floaterProgress[i] = if (next >= 1f) UNUSED else next
            }
        }
        if (shakePit >= 0) {
            shakeProgress += deltaSeconds / AnimationTiming.SHAKE_SECONDS
            if (shakeProgress >= 1f) {
                shakePit = -1
                shakeProgress = 0f
            }
        }
    }

    companion object {
        const val PLAYER_COUNT = 2
        const val MAX_FLOATERS = 4
        const val UNUSED = -1f
    }
}
