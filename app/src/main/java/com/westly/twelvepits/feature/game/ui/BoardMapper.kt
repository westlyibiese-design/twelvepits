package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState

/** The two store bowls at the ends of the drawn board. */
enum class StoreSide { LEFT, RIGHT }

/**
 * Maps engine pits and stores to the drawn board (pure Kotlin, no Android imports).
 *
 * A drawn slot is a number from 0 to 11 in the order of `board_geometry.json`: slots 0 to 5 are the
 * bottom row left to right, slots 6 to 11 are the top row right to left. Seeds always travel
 * anticlockwise on screen, so the next engine pit is always the next slot.
 *
 * Perspective ONE (the default) is the identity: Player ONE is at the bottom with the right store.
 * Perspective TWO draws Player TWO at the bottom with the right store (used by Phase 9).
 */
class BoardMapper(val perspective: Player = Player.ONE) {

    /** The drawn slot (0 to 11) of an engine pit. */
    fun slotOfPit(pit: Int): Int {
        require(pit in 0 until GameState.PIT_COUNT) { "Pit index out of range: $pit" }
        return if (perspective == Player.ONE) pit else (pit + GameState.PITS_PER_SIDE) % GameState.PIT_COUNT
    }

    /** The engine pit drawn in a slot. The exact reverse of [slotOfPit]. */
    fun pitOfSlot(slot: Int): Int {
        require(slot in 0 until GameState.PIT_COUNT) { "Slot index out of range: $slot" }
        return if (perspective == Player.ONE) slot else (slot + GameState.PITS_PER_SIDE) % GameState.PIT_COUNT
    }

    /** Which store bowl belongs to a player in this view. The bottom player owns the right store. */
    fun storeSide(owner: Player): StoreSide =
        if (owner == perspective) StoreSide.RIGHT else StoreSide.LEFT

    /** True when a slot is in the bottom row of the drawn board. */
    fun isBottomRow(slot: Int): Boolean = slot < GameState.PITS_PER_SIDE
}
