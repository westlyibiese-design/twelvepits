package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.feature.game.engine.GameState

/** The computer opponent. It only chooses a pit; the game plays the turn. */
interface ComputerPlayer {
    /**
     * The pit (0 to 11) to play for the player whose turn it is in [state].
     * Called only when the game is not finished. Throws [NoLegalMoveException] when there is no legal pit.
     */
    suspend fun choosePit(state: GameState): Int
}
