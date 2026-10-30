package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.feature.game.engine.GameState
import kotlin.random.Random

/** The computer opponent. It plays Player TWO and only chooses a pit; the game plays the turn. */
interface ComputerPlayer {
    /** The pit (0 to 11) to play. Called only when it is the computer's turn and the game is not finished. */
    suspend fun choosePit(state: GameState): Int
}

// Replaced in Phase 6
/** A temporary opponent that plays any pit that has seeds, chosen at random. */
class RandomLegalMovePlayer(
    private val random: Random = Random.Default,
) : ComputerPlayer {
    override suspend fun choosePit(state: GameState): Int {
        val legal = state.legalMoves()
        return if (legal.isEmpty()) NO_PIT else legal[random.nextInt(legal.size)]
    }

    private companion object {
        const val NO_PIT = -1
    }
}
