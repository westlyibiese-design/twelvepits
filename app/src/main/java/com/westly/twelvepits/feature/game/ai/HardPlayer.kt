package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.random.Random

/**
 * Hard: minimax with alpha-beta pruning over whole turns, deepening one level at a time up to
 * [maxDepth] and stopping when [timeBudgetMillis] is used (or [nodeLimit] nodes are searched). The
 * answer is the best pit of the last depth that finished completely. The search checks that its
 * coroutine is still active at every node, so cancelling it stops the work at once.
 *
 * [clock] returns milliseconds and, like [random], [maxDepth] and [nodeLimit], can be replaced in
 * tests so they never depend on the speed of the machine.
 */
class HardPlayer(
    private val random: Random = Random.Default,
    private val engine: SowingEngine = SowingEngine(),
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val timeBudgetMillis: Long = DEFAULT_TIME_BUDGET_MILLIS,
    private val nodeLimit: Long = Long.MAX_VALUE,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) : ComputerPlayer {
    init {
        require(maxDepth >= 1) { "maxDepth must be at least 1" }
        require(timeBudgetMillis >= 0) { "timeBudgetMillis must not be negative" }
        require(nodeLimit >= 1) { "nodeLimit must be at least 1" }
    }

    override suspend fun choosePit(state: GameState): Int {
        val job = currentCoroutineContext()[Job]
        job?.ensureActive()
        val legal = requireLegalMoves(state)
        if (legal.size == 1) return legal[0]
        return Search(state.currentPlayer, job).run(state, legal)
    }

    private class Move(val pit: Int, val state: GameState, val gain: Int)

    private class RootResult(val move: Move, val score: Int)

    /** Used to leave the search quickly when the time or the node limit is used. Carries no stack trace. */
    private class SearchStopped : RuntimeException(null, null, false, false)

    private inner class Search(private val me: Player, private val job: Job?) {
        private val deadline = clock() + timeBudgetMillis
        private var nodes = 0L

        fun run(root: GameState, legal: List<Int>): Int {
            // The shuffle makes equal moves come in a random order, so equal scores are chosen at random.
            var rootMoves = orderedMoves(root, legal.shuffled(random))
            var best = rootMoves[0]
            for (depth in 1..maxDepth) {
                if (depth > 1 && clock() >= deadline) break
                val result = searchRoot(rootMoves, depth) ?: break
                best = result.move
                rootMoves = listOf(best) + rootMoves.filter { it !== best }
                if (result.score >= PROVEN_SCORE || result.score <= -PROVEN_SCORE) break
            }
            return best.pit
        }

        /** Null when the search was stopped before this depth finished. */
        private fun searchRoot(moves: List<Move>, depth: Int): RootResult? {
            try {
                var alpha = -INFINITY
                var bestScore = -INFINITY
                var bestMove = moves[0]
                for (move in moves) {
                    val score = score(move.state, depth - 1, alpha, INFINITY)
                    if (score > bestScore) {
                        bestScore = score
                        bestMove = move
                    }
                    if (score > alpha) alpha = score
                }
                return RootResult(bestMove, bestScore)
            } catch (stopped: SearchStopped) {
                return null
            }
        }

        private fun score(state: GameState, depth: Int, alpha: Int, beta: Int): Int {
            countNode()
            if (state.status == GameStatus.FINISHED) return finishedScore(state)
            if (depth == 0) return evaluate(state)
            val moves = orderedMoves(state, state.legalMoves())
            if (moves.isEmpty()) return evaluate(state)

            var low = alpha
            var high = beta
            if (state.currentPlayer == me) {
                var value = -INFINITY
                for (move in moves) {
                    val childScore = score(move.state, depth - 1, low, high)
                    if (childScore > value) value = childScore
                    if (value > low) low = value
                    if (low >= high) break
                }
                return value
            }
            var value = INFINITY
            for (move in moves) {
                val childScore = score(move.state, depth - 1, low, high)
                if (childScore < value) value = childScore
                if (value < high) high = value
                if (low >= high) break
            }
            return value
        }

        private fun countNode() {
            job?.ensureActive()
            nodes += 1
            if (nodes > nodeLimit) throw SearchStopped()
            if ((nodes and CLOCK_CHECK_MASK) == 0L && clock() >= deadline) throw SearchStopped()
        }

        /** The legal pits of [state] played out, the best immediate store gain for the mover first. */
        private fun orderedMoves(state: GameState, legal: List<Int>): List<Move> {
            val mover = state.currentPlayer
            val moves = ArrayList<Move>(legal.size)
            for (pit in legal) {
                val after = engine.simulate(state, pit)
                moves.add(Move(pit, after, storeDifference(after, mover)))
            }
            moves.sortByDescending { it.gain }
            return moves
        }

        private fun finishedScore(state: GameState): Int {
            val difference = storeDifference(state, me)
            return when {
                difference > 0 -> WIN_SCORE + difference
                difference < 0 -> -WIN_SCORE + difference
                else -> 0
            }
        }

        private fun evaluate(state: GameState): Int {
            val opponent = me.opponent
            return STORE_WEIGHT * storeDifference(state, me) +
                SEED_WEIGHT * (state.seedsOnSide(me) - state.seedsOnSide(opponent)) +
                (playablePits(state, me) - playablePits(state, opponent))
        }

        private fun playablePits(state: GameState, player: Player): Int {
            var count = 0
            for (pit in GameState.pitsOf(player)) {
                if (state.pits[pit] > 0) count += 1
            }
            return count
        }
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 8
        const val DEFAULT_TIME_BUDGET_MILLIS = 1_200L

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val CLOCK_CHECK_MASK = 63L
        private const val INFINITY = 1_000_000
        private const val WIN_SCORE = 100_000
        private const val PROVEN_SCORE = 90_000
        private const val STORE_WEIGHT = 100
        private const val SEED_WEIGHT = 2
    }
}
