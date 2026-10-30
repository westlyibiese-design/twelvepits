package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.SowingEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.random.Random

/**
 * Easy: 70 % of the time any legal pit at random, 30 % of the time the pit with the best immediate
 * store gain (ties at random). It blunders often.
 */
class EasyPlayer(
    private val random: Random = Random.Default,
    private val engine: SowingEngine = SowingEngine(),
) : ComputerPlayer {
    override suspend fun choosePit(state: GameState): Int {
        currentCoroutineContext().ensureActive()
        val legal = requireLegalMoves(state)
        if (random.nextDouble() < RANDOM_MOVE_CHANCE) {
            return legal[random.nextInt(legal.size)]
        }
        val me = state.currentPlayer
        return bestPitBy(legal, random) { pit -> storeDifference(engine.simulate(state, pit), me) }
    }

    companion object {
        const val RANDOM_MOVE_CHANCE = 0.70
    }
}
