package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.feature.game.ai.ComputerPlayer
import com.westly.twelvepits.feature.game.ai.HardPlayer
import com.westly.twelvepits.feature.game.ai.NoLegalMoveException
import com.westly.twelvepits.feature.game.engine.GameState

/** The four places of the hidden tap code. They are places on the drawn board, not game moves. */
enum class SecretTarget {
    /** The second pit from the left in the top row. */
    TOP_SECOND,

    /** The large bowl on the left. */
    LEFT_STORE,

    /** The large bowl on the right. */
    RIGHT_STORE,

    /** The fifth pit from the left in the top row. */
    TOP_FIFTH,
}

/** Turns what a tap hit on the drawn board into a [SecretTarget] (pure Kotlin, unit tested). */
object SecretTargets {
    /** Drawn slots of the top row: slots 6 to 11 run right to left, so slot 10 is second from the left. */
    const val TOP_SECOND_SLOT = 10
    const val TOP_FIFTH_SLOT = 7

    /**
     * The target hit by a tap, or null when the tap is somewhere else. [slot] is the drawn slot of the
     * pit that was hit (or -1) and [store] the bowl that was hit (or null). Drawn places do not depend
     * on whose point of view the board is drawn from.
     */
    fun of(slot: Int, store: StoreSide?): SecretTarget? = when {
        slot == TOP_SECOND_SLOT -> SecretTarget.TOP_SECOND
        slot == TOP_FIFTH_SLOT -> SecretTarget.TOP_FIFTH
        slot < 0 && store == StoreSide.LEFT -> SecretTarget.LEFT_STORE
        slot < 0 && store == StoreSide.RIGHT -> SecretTarget.RIGHT_STORE
        else -> null
    }
}

/**
 * Recognises the hidden code: second top pit, left bowl, right bowl, fifth top pit, in that order, all
 * four taps within [WINDOW_MILLIS]. It only listens: it never decides what a tap does in the game.
 * It has no timer of its own; the time is checked when the next tap arrives, so nothing runs in the
 * background. Pure Kotlin. Main thread only.
 */
class SecretSequence(private val now: () -> Long) {
    private var matched = 0
    private var startedAt = 0L

    /** Forget any taps entered so far. */
    fun reset() {
        matched = 0
        startedAt = 0L
    }

    /**
     * Feeds one tap ([target] is null for a tap anywhere else). Returns true only when this tap
     * completes the code in time; the code then starts over.
     */
    fun onTap(target: SecretTarget?): Boolean {
        val time = now()
        // Too slow: the taps so far are forgotten, and this tap may be a new first tap.
        if (matched > 0 && time - startedAt > WINDOW_MILLIS) reset()
        if (target == null) {
            reset()
            return false
        }
        if (target == CODE[matched]) {
            if (matched == 0) startedAt = time
            matched += 1
            if (matched == CODE.size) {
                reset()
                return true
            }
            return false
        }
        // The wrong place: start again. If it happens to be the first place, it is a new first tap.
        reset()
        if (target == CODE[0]) {
            startedAt = time
            matched = 1
        }
        return false
    }

    companion object {
        const val WINDOW_MILLIS = 3_000L

        private val CODE = listOf(
            SecretTarget.TOP_SECOND,
            SecretTarget.LEFT_STORE,
            SecretTarget.RIGHT_STORE,
            SecretTarget.TOP_FIFTH,
        )
    }
}

/** Finds the strongest legal move for the player to move. It reuses the Hard computer player's search. */
object CheatHint {
    /**
     * The pit to recommend in [state], or null when there is no legal move or the answer is not a legal
     * pit of the player to move. Never changes the game.
     */
    suspend fun recommend(state: GameState, player: ComputerPlayer = HardPlayer()): Int? {
        val legal = state.legalMoves()
        if (legal.isEmpty()) return null
        val pit = try {
            player.choosePit(state)
        } catch (e: NoLegalMoveException) {
            return null
        }
        return if (pit in legal) pit else null
    }
}
