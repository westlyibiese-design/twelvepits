package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import kotlin.random.Random

/** Thrown when a computer player is asked to choose a pit in a position with no legal move. */
class NoLegalMoveException(message: String) : IllegalStateException(message)

/** The pits the current player may play. Throws [NoLegalMoveException] when there are none. */
internal fun requireLegalMoves(state: GameState): List<Int> {
    val legal = state.legalMoves()
    if (legal.isEmpty()) {
        throw NoLegalMoveException("There is no legal pit to play (status ${state.status}, player ${state.currentPlayer}).")
    }
    return legal
}

/** Seeds in [player]'s store minus seeds in the other store. */
internal fun storeDifference(state: GameState, player: Player): Int =
    state.storeOf(player) - state.storeOf(player.opponent)

/** The pit of [legal] with the highest [score]. Pits with the same best score are chosen at random. */
internal inline fun bestPitBy(legal: List<Int>, random: Random, score: (Int) -> Int): Int {
    var bestScore = Int.MIN_VALUE
    val best = ArrayList<Int>(legal.size)
    for (pit in legal) {
        val value = score(pit)
        if (value > bestScore) {
            bestScore = value
            best.clear()
            best.add(pit)
        } else if (value == bestScore) {
            best.add(pit)
        }
    }
    return best[random.nextInt(best.size)]
}
