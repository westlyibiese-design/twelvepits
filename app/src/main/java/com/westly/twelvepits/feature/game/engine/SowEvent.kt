package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player

/** The exact script of a turn, in order, for the Phase 4 animation. */
sealed class SowEvent {
    /** All seeds lifted from a pit (the first pick-up and every relay pick-up). */
    data class PickUp(val pit: Int, val count: Int) : SowEvent()

    /** One seed lands. `newCount` is the pit count right after landing, before any capture. */
    data class Drop(val pit: Int, val newCount: Int, val inHandAfter: Int) : SowEvent()

    /** The last seed landed in a pit that already had seeds. Always followed by a PickUp of that pit. */
    data class RelayPause(val pit: Int) : SowEvent()

    /** The pit became exactly 4 and its 4 seeds went to the owner's store. */
    data class Capture(val pit: Int, val owner: Player, val newStoreTotal: Int) : SowEvent()

    /** That player has no seeds, so their turn was skipped. */
    data class TurnSkipped(val player: Player) : SowEvent()

    /** Emitted right after the 11th capture, even in the middle of a sowing. */
    data class GameOver(
        val winner: Player,
        val scoreOne: Int,
        val scoreTwo: Int,
        val leftoverInHand: Int,
    ) : SowEvent()

    /**
     * The endless relay rule (RULES.md section 4): the whole board and the landing pit are exactly
     * the same as at an earlier landing in this turn, so the turn ends here. It replaces the
     * RelayPause and PickUp that would have come next. No seeds are in hand.
     */
    data object LoopStopped : SowEvent()

    /** The engineering safety net fired. Never expected in normal play. */
    data object StepLimitReached : SowEvent()
}
