package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.SowingEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.random.Random

/**
 * Normal: looks two turns ahead. For every legal pit it plays the turn, then assumes the player who
 * moves next answers with the pit that is best for them by immediate store difference, and it keeps
 * the pit with the best resulting store difference (ties at random). When the same player moves
 * again because the other side has no seeds, that follow-up is judged the same way.
 */
class NormalPlayer(
    private val random: Random = Random.Default,
    private val engine: SowingEngine = SowingEngine(),
) : ComputerPlayer {
    override suspend fun choosePit(state: GameState): Int {
        currentCoroutineContext().ensureActive()
        val legal = requireLegalMoves(state)
        val me = state.currentPlayer
        return bestPitBy(legal, random) { pit -> valueAfterMyTurn(engine.simulate(state, pit), me) }
    }

    /** Store difference from [me]'s point of view after the best reply to [after]. */
    private fun valueAfterMyTurn(after: GameState, me: Player): Int {
        val replies = after.legalMoves()
        if (replies.isEmpty()) return storeDifference(after, me)
        val replier = after.currentPlayer
        var bestForReplier = Int.MIN_VALUE
        for (reply in replies) {
            val difference = storeDifference(engine.simulate(after, reply), replier)
            if (difference > bestForReplier) bestForReplier = difference
        }
        return if (replier == me) bestForReplier else -bestForReplier
    }
}
