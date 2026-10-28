package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Plays 2,000 complete games with fixed seeds and a simple random legal-move chooser.
 * If a game ever fails to finish, the test fails and prints the exact position.
 */
class RandomPlayoutTest {
    @Test
    fun twoThousandRandomGamesKeepEveryInvariant() {
        val engine = SowingEngine()
        val gameCount = 2000
        val turnLimit = 100_000
        var totalTurns = 0L
        var longestGameTurns = 0
        var longestTurnSteps = 0

        for (game in 0 until gameCount) {
            val random = Random(game)
            var state = GameState.initial(if (game % 2 == 0) Player.ONE else Player.TWO)
            var turns = 0

            while (state.status == GameStatus.IN_PROGRESS) {
                if (turns >= turnLimit) {
                    throw AssertionError("Game $game did not finish after $turns turns. Position: $state")
                }
                val before = state
                val pit = randomLegalMove(before, random)
                val result = engine.playTurn(before, pit)
                val after = result.newState
                val label = "game $game turn ${turns + 1} pit $pit from $before"

                assertEquals("seeds conserved, $label", 48, after.pits.sum() + after.storeOne + after.storeTwo + after.leftoverInHand)
                assertEquals("store one multiple of 4, $label", 0, after.storeOne % 4)
                assertEquals("store two multiple of 4, $label", 0, after.storeTwo % 4)
                assertEquals("capturedTotal, $label", after.storeOne + after.storeTwo, after.capturedTotal)
                assertTrue("isValid, $label", after.isValid())
                assertEquals("simulate equals playTurn, $label", after, engine.simulate(before, pit))
                assertFalse("step limit, $label", result.events.contains(SowEvent.StepLimitReached))
                assertTrue("outcome, $label", result.outcome != TurnOutcome.STEP_LIMIT)
                assertEquals(
                    "captures of the turn add up, $label",
                    after.capturedTotal - before.capturedTotal,
                    result.capturedForMover + result.capturedForOpponent,
                )
                assertEquals("move number, $label", before.moveNumber + 1, after.moveNumber)
                assertEquals("starter never changes, $label", before.startingPlayer, after.startingPlayer)

                val steps = result.events.count { it is SowEvent.Drop }
                if (steps > longestTurnSteps) longestTurnSteps = steps
                state = after
                turns += 1
            }

            assertEquals("game $game captured", 44, state.capturedTotal)
            assertEquals("game $game status", GameStatus.FINISHED, state.status)
            assertNotNull("game $game winner", state.winner)
            assertTrue("game $game legal moves", state.legalMoves().isEmpty())
            totalTurns += turns
            if (turns > longestGameTurns) longestGameTurns = turns
        }

        val average = totalTurns.toDouble() / gameCount
        println(
            "Random playouts: games=$gameCount, " +
                "averageTurnsPerGame=${"%.2f".format(average)}, " +
                "longestGameTurns=$longestGameTurns, " +
                "longestTurnSowingSteps=$longestTurnSteps",
        )
    }
}
