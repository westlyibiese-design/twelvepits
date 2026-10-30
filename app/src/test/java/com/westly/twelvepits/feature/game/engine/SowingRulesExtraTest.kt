package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The extra engine tests of the Phase 2 brief. */
class SowingRulesExtraTest {
    private val engine = SowingEngine()

    @Test
    fun pitWithFiveOrMoreSeedsIsNeverCaptured() {
        // p6 holds 5 and the last seed of p5 makes it 6: no capture, so the whole pit is relayed.
        val state = boardState(listOf(0, 5, 5, 5, 5, 1, 5, 0, 0, 0, 0, 0))
        val result = engine.playTurn(state, 5)

        assertTrue(result.events.none { it is SowEvent.Capture })
        assertEquals(SowEvent.Drop(6, 6, 0), result.events[1])
        assertEquals(SowEvent.RelayPause(6), result.events[2])
        assertEquals(SowEvent.PickUp(6, 6), result.events[3])
        assertEquals(listOf(1, 5, 5, 5, 5, 0, 0, 1, 1, 1, 1, 1), result.newState.pits)
        assertEquals(0, result.newState.capturedTotal)
    }

    @Test
    fun pitRefilledToExactlyFourIsCapturedAgain() {
        // A real game opening: pits 0 and 7 are captured, emptied, refilled to 4 and captured again.
        var state = GameState.initial(Player.ONE)
        val captures = HashMap<Int, Int>()
        for (pit in listOf(3, 8, 5)) {
            val result = engine.playTurn(state, pit)
            for (event in result.events) {
                if (event is SowEvent.Capture) {
                    captures[event.pit] = (captures[event.pit] ?: 0) + 1
                }
            }
            state = result.newState
        }

        assertEquals(2, captures[0])
        assertEquals(2, captures[7])
        assertEquals(listOf(0, 1, 1, 1, 2, 3, 1, 0, 1, 1, 0, 1), state.pits)
        assertEquals(16, state.storeOne)
        assertEquals(20, state.storeTwo)
        assertTrue(state.isValid())
    }

    @Test
    fun eleventhCaptureEndsGameInMiddleOfSowing() {
        // p0 holds 3 and p2 holds 3. The second seed makes p2 reach 4: the 11th capture, one seed in hand.
        val state = boardState(
            listOf(3, 0, 3, 0, 0, 0, 0, 2, 0, 0, 0, 0),
            storeOne = 24,
            storeTwo = 16,
        )
        val result = engine.playTurn(state, 0)

        assertEquals(TurnOutcome.GAME_OVER, result.outcome)
        assertEquals(GameStatus.FINISHED, result.newState.status)
        assertEquals(1, result.newState.leftoverInHand)
        assertEquals(listOf(0, 1, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0), result.newState.pits)
        assertEquals(28, result.newState.storeOne)
        assertEquals(16, result.newState.storeTwo)
        assertEquals(44, result.newState.capturedTotal)
        assertEquals(Player.ONE, result.newState.winner)
        assertEquals(SowEvent.Capture(2, Player.ONE, 28), result.events[result.events.size - 2])
        assertEquals(SowEvent.GameOver(Player.ONE, 28, 16, 1), result.events.last())
        assertEquals(48, result.newState.pits.sum() + 28 + 16 + result.newState.leftoverInHand)
        assertTrue(result.newState.isValid())
    }

    @Test
    fun relayChainCrossesBothSides() {
        // p4 sows into p5, p6 and p7. p7 already holds seeds, so its seeds are relayed on to p10.
        val state = boardState(listOf(8, 8, 8, 8, 3, 1, 1, 2, 1, 1, 0, 7))
        val result = engine.playTurn(state, 4)

        assertEquals(
            listOf(
                SowEvent.PickUp(4, 3),
                SowEvent.Drop(5, 2, 2),
                SowEvent.Drop(6, 2, 1),
                SowEvent.Drop(7, 3, 0),
                SowEvent.RelayPause(7),
                SowEvent.PickUp(7, 3),
                SowEvent.Drop(8, 2, 2),
                SowEvent.Drop(9, 2, 1),
                SowEvent.Drop(10, 1, 0),
            ),
            result.events,
        )
        assertEquals(listOf(8, 8, 8, 8, 0, 2, 2, 0, 2, 2, 1, 7), result.newState.pits)
        assertEquals(Player.TWO, result.newState.currentPlayer)
    }

    @Test
    fun relayThroughPitWithTwelveOrMoreSeeds() {
        // p6 holds 12. The seed from p5 makes it 13, all 13 are relayed and the lap gives p6 a seed too.
        val state = boardState(listOf(1, 1, 1, 1, 1, 1, 12, 0, 1, 1, 1, 1))
        val result = engine.playTurn(state, 5)
        val events = result.events

        val relayIndex = events.indexOf(SowEvent.RelayPause(6))
        assertTrue(relayIndex > 0)
        assertEquals(SowEvent.Drop(6, 13, 0), events[relayIndex - 1])
        assertEquals(SowEvent.PickUp(6, 13), events[relayIndex + 1])
        // The start pit of the relay receives a seed on the full lap, like every other pit.
        assertTrue(events.contains(SowEvent.Drop(6, 1, 1)))

        assertEquals(listOf(0, 3, 3, 0, 3, 2, 0, 1, 0, 0, 3, 3), result.newState.pits)
        // The last seed of the relay makes p8 (opponent's pit) 4: the sower captures it.
        assertEquals(4, result.newState.storeOne)
        assertEquals(0, result.newState.storeTwo)
        assertEquals(4, result.capturedForMover)
        assertEquals(0, result.capturedForOpponent)
        assertEquals(SowEvent.Capture(8, Player.ONE, 4), events.last())
        assertEquals(TurnOutcome.TURN_ENDED, result.outcome)
        assertEquals(22, result.newState.pits.sum() + result.newState.storeOne)
    }

    @Test
    fun emptyPitIsIllegal() {
        val state = boardState(listOf(0, 5, 5, 5, 5, 1, 5, 0, 0, 0, 0, 0))
        assertThrows(IllegalMoveException::class.java) { engine.playTurn(state, 0) }
        assertThrows(IllegalMoveException::class.java) { engine.simulate(state, 0) }
    }

    @Test
    fun opponentPitIsIllegal() {
        val state = GameState.initial(Player.ONE)
        assertThrows(IllegalMoveException::class.java) { engine.playTurn(state, 6) }
        assertThrows(IllegalMoveException::class.java) { engine.playTurn(state, 11) }
        assertThrows(IllegalMoveException::class.java) { engine.simulate(state, 6) }
    }

    @Test
    fun pitThatDoesNotExistIsIllegal() {
        val state = GameState.initial(Player.ONE)
        assertThrows(IllegalMoveException::class.java) { engine.playTurn(state, -1) }
        assertThrows(IllegalMoveException::class.java) { engine.playTurn(state, 12) }
    }

    @Test
    fun finishedGameIsIllegal() {
        val finished = boardState(
            listOf(1, 3, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0),
            storeOne = 20,
            storeTwo = 20,
        ).let { engine.playTurn(it, 0).newState }
        assertEquals(GameStatus.FINISHED, finished.status)
        assertThrows(IllegalMoveException::class.java) { engine.playTurn(finished, 6) }
        assertThrows(IllegalMoveException::class.java) { engine.simulate(finished, 6) }
    }

    @Test
    fun simulateReturnsTheSameStateAsPlayTurn() {
        var state = GameState.initial(Player.TWO)
        val random = kotlin.random.Random(7)
        var turns = 0
        while (state.status == GameStatus.IN_PROGRESS && turns < 200) {
            val pit = randomLegalMove(state, random)
            val played = engine.playTurn(state, pit).newState
            val simulated = engine.simulate(state, pit)
            assertEquals(played, simulated)
            state = played
            turns += 1
        }
    }

    @Test
    fun stepLimitStopsTheTurnAndKeepsAllSeeds() {
        val logger = RecordingLogger()
        val limited = SowingEngine(maxSteps = 3, logger = logger)
        val result = limited.playTurn(GameState.initial(Player.ONE), 0)

        assertEquals(TurnOutcome.STEP_LIMIT, result.outcome)
        assertEquals(
            listOf(
                SowEvent.PickUp(0, 4),
                SowEvent.Drop(1, 5, 3),
                SowEvent.Drop(2, 5, 2),
                SowEvent.Drop(3, 5, 1),
                SowEvent.StepLimitReached,
            ),
            result.events,
        )
        // The seed still in hand went back into the last pit sown into (p3: 5 + 1).
        assertEquals(listOf(0, 5, 5, 6, 4, 4, 4, 4, 4, 4, 4, 4), result.newState.pits)
        assertEquals(48, result.newState.pits.sum())
        assertEquals(Player.TWO, result.newState.currentPlayer)
        assertEquals(GameStatus.IN_PROGRESS, result.newState.status)
        assertEquals(1, logger.messages.size)
        assertTrue(result.newState.isValid())
        assertEquals(result.newState, limited.simulate(GameState.initial(Player.ONE), 0))
    }

    @Test
    fun bothSidesEmptyIsTreatedLikeTheStepLimit() {
        // Impossible in a real game (4 seeds always stay on the board), so the position is built by hand.
        val logger = RecordingLogger()
        val guarded = SowingEngine(logger = logger)
        val state = boardState(listOf(1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        val result = guarded.playTurn(state, 0)

        assertEquals(TurnOutcome.STEP_LIMIT, result.outcome)
        assertEquals(SowEvent.StepLimitReached, result.events.last())
        assertEquals(1, logger.messages.size)
    }
}
