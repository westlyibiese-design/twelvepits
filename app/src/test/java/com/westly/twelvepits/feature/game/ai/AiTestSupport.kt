package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import kotlin.random.Random

/** The temporary opponent of Phase 5, kept as a test helper. It plays any pit that has seeds, at random. */
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

/** In-progress positions taken from random playouts: [count] of them, every turn of every playout. */
internal fun randomPlayoutPositions(count: Int, seed: Int): List<GameState> {
    val engine = SowingEngine()
    val positions = ArrayList<GameState>(count)
    var game = 0
    while (positions.size < count) {
        val random = Random(seed + game)
        var state = GameState.initial(if (game % 2 == 0) Player.ONE else Player.TWO)
        while (state.status == GameStatus.IN_PROGRESS && positions.size < count) {
            positions.add(state)
            val legal = state.legalMoves()
            state = engine.simulate(state, legal[random.nextInt(legal.size)])
        }
        game += 1
    }
    return positions
}
