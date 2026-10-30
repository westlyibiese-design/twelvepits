package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plays whole games between the levels. Everything is seeded, and the starting player alternates every
 * game, so the numbers are the same on every machine and neither side gets an advantage from moving first.
 * Hard uses a fixed, lower depth here so the test never depends on how fast the machine is.
 */
class ComputerStrengthTest {
    private val engine = SowingEngine()

    /** Plays one game. Player ONE is [weak] and Player TWO is [strong]. Returns the winner. */
    private suspend fun playGame(weak: ComputerPlayer, strong: ComputerPlayer, starter: Player): Player {
        var state = GameState.initial(starter)
        var turns = 0
        while (state.status == GameStatus.IN_PROGRESS) {
            val player = if (state.currentPlayer == Player.ONE) weak else strong
            val pit = player.choosePit(state)
            assertTrue("pit $pit is not legal in $state", pit in state.legalMoves())
            state = engine.simulate(state, pit)
            turns += 1
            assertTrue("The game did not finish: $state", turns < 10_000)
        }
        return state.winner ?: error("A finished game always has a winner")
    }

    /** How many of [games] games the strong player wins. */
    private fun strongWins(weak: ComputerPlayer, strong: ComputerPlayer, games: Int): Int = runBlocking {
        var wins = 0
        for (game in 0 until games) {
            val starter = if (game % 2 == 0) Player.ONE else Player.TWO
            if (playGame(weak, strong, starter) == Player.TWO) wins += 1
        }
        wins
    }

    @Test
    fun normalBeatsEasyInAtLeastSixtyFiveOfOneHundredGames() {
        val wins = strongWins(weak = EasyPlayer(Random(21)), strong = NormalPlayer(Random(22)), games = 100)
        println("STRENGTH: Normal beat Easy in $wins of 100 games")
        assertTrue("Normal won only $wins of 100 games against Easy", wins >= 65)
    }

    @Test
    fun hardBeatsNormalInAtLeastSixtyOfOneHundredGames() {
        val hard = HardPlayer(Random(32), maxDepth = TEST_HARD_DEPTH, clock = { 0L })
        val wins = strongWins(weak = NormalPlayer(Random(31)), strong = hard, games = 100)
        println("STRENGTH: Hard (depth $TEST_HARD_DEPTH) beat Normal in $wins of 100 games")
        assertTrue("Hard won only $wins of 100 games against Normal", wins >= 60)
    }

    @Test
    fun hardBeatsEasyInAtLeastThirtyOfFortyGames() {
        val hard = HardPlayer(Random(42), maxDepth = TEST_HARD_DEPTH, clock = { 0L })
        val wins = strongWins(weak = EasyPlayer(Random(41)), strong = hard, games = 40)
        println("STRENGTH: Hard (depth $TEST_HARD_DEPTH) beat Easy in $wins of 40 games")
        assertTrue("Hard won only $wins of 40 games against Easy", wins >= 30)
    }

    private companion object {
        const val TEST_HARD_DEPTH = 4
    }
}
