package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests 1 to 7 of RULES.md section 9, with the numbers given there. */
class RulesTestCasesTest {
    private val engine = SowingEngine()

    @Test
    fun test1_firstMove() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 0)
        val end = result.newState

        assertEquals(listOf(2, 7, 1, 6, 1, 6, 6, 6, 0, 1, 6, 6), end.pits)
        assertEquals(0, end.storeOne)
        assertEquals(0, end.storeTwo)
        assertEquals(48, end.pits.sum())
        assertEquals(TurnOutcome.TURN_ENDED, result.outcome)
        assertEquals(0, result.capturedForMover)
        assertEquals(0, result.capturedForOpponent)
        assertEquals(Player.TWO, end.currentPlayer)
        assertEquals(Player.ONE, end.startingPlayer)
        assertEquals(1, end.moveNumber)
        assertTrue(end.isValid())

        assertEquals(SowEvent.PickUp(0, 4), result.events.first())
        val drops = result.events.count { it is SowEvent.Drop }
        val seedsSown = result.events.filterIsInstance<SowEvent.PickUp>().sumOf { it.count }
        assertEquals(26, drops)
        assertEquals(seedsSown, drops)
        assertEquals(4, result.events.count { it is SowEvent.RelayPause })
    }

    @Test
    fun test2_midSowingCapture() {
        // p1 holds 3, p3 holds 3, p4 is empty. Sowing: p2 +1, p3 becomes 4 (captured), p4 becomes 1.
        val state = boardState(listOf(5, 3, 0, 3, 0, 5, 5, 5, 5, 5, 6, 6))
        val result = engine.playTurn(state, 1)

        assertEquals(listOf(5, 0, 1, 0, 1, 5, 5, 5, 5, 5, 6, 6), result.newState.pits)
        assertEquals(4, result.newState.storeOne)
        assertEquals(0, result.newState.storeTwo)
        assertEquals(4, result.capturedForMover)
        assertEquals(0, result.capturedForOpponent)
        assertEquals(TurnOutcome.TURN_ENDED, result.outcome)
        assertEquals(Player.TWO, result.newState.currentPlayer)
        assertTrue(result.events.contains(SowEvent.Capture(3, Player.ONE, 4)))
        // The sowing continued after the capture: the seed after it still landed in p4.
        assertTrue(result.events.contains(SowEvent.Drop(4, 1, 0)))
    }

    @Test
    fun test3_lastSeedOnOwnPit() {
        val state = boardState(listOf(3, 0, 0, 3, 5, 5, 5, 5, 5, 5, 6, 6))
        val result = engine.playTurn(state, 0)

        assertEquals(listOf(0, 1, 1, 0, 5, 5, 5, 5, 5, 5, 6, 6), result.newState.pits)
        assertEquals(4, result.newState.storeOne)
        assertEquals(4, result.capturedForMover)
        assertEquals(TurnOutcome.TURN_ENDED, result.outcome)
        assertEquals(Player.TWO, result.newState.currentPlayer)
        assertEquals(SowEvent.Capture(3, Player.ONE, 4), result.events.last())
    }

    @Test
    fun test4_lastSeedOnOpponentPit() {
        val state = boardState(listOf(5, 5, 5, 5, 5, 1, 3, 4, 4, 4, 4, 3))
        val result = engine.playTurn(state, 5)

        assertEquals(listOf(5, 5, 5, 5, 5, 0, 0, 4, 4, 4, 4, 3), result.newState.pits)
        assertEquals(0, result.newState.storeOne)
        assertEquals(4, result.newState.storeTwo)
        assertEquals(0, result.capturedForMover)
        assertEquals(4, result.capturedForOpponent)
        assertEquals(TurnOutcome.TURN_ENDED, result.outcome)
        assertEquals(Player.TWO, result.newState.currentPlayer)
        assertEquals(SowEvent.Capture(6, Player.TWO, 4), result.events.last())
    }

    @Test
    fun test5_fullLap() {
        val pits = MutableList(12) { 1 }
        pits[3] = 12
        val result = engine.playTurn(boardState(pits), 3)

        assertEquals(listOf(2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 2, 2), result.newState.pits)
        assertEquals(0, result.newState.capturedTotal)
        assertEquals(TurnOutcome.TURN_ENDED, result.outcome)
        assertEquals(12, result.events.count { it is SowEvent.Drop })
        assertTrue(result.events.none { it is SowEvent.Capture })
        assertTrue(result.events.none { it is SowEvent.RelayPause })
        // The starting pit received its seed like any other pit, as the last seed of the lap.
        assertEquals(SowEvent.Drop(3, 1, 0), result.events.last())
    }

    @Test
    fun test5b_fullLapWithMidSowingCapture() {
        // p3 holds 12, p6 holds 3, every other pit holds 1. The lap makes p6 reach 4 in the middle.
        val pits = MutableList(12) { 1 }
        pits[3] = 12
        pits[6] = 3
        val result = engine.playTurn(boardState(pits), 3)

        assertEquals(listOf(2, 2, 2, 1, 2, 2, 0, 2, 2, 2, 2, 2), result.newState.pits)
        assertEquals(0, result.newState.storeOne)
        assertEquals(4, result.newState.storeTwo)
        assertEquals(4, result.capturedForOpponent)
        assertEquals(TurnOutcome.TURN_ENDED, result.outcome)
        assertEquals(12, result.events.count { it is SowEvent.Drop })
        assertTrue(result.events.contains(SowEvent.Capture(6, Player.TWO, 4)))
        assertEquals(SowEvent.Drop(3, 1, 0), result.events.last())
    }

    @Test
    fun test6_skippedTurn() {
        // After p0 moves its single seed, Player TWO has no seeds on p6 to p11: TWO is skipped.
        val state = boardState(listOf(1, 0, 10, 11, 12, 14, 0, 0, 0, 0, 0, 0))
        val result = engine.playTurn(state, 0)

        assertEquals(Player.ONE, result.newState.currentPlayer)
        assertEquals(listOf(0, 1, 10, 11, 12, 14, 0, 0, 0, 0, 0, 0), result.newState.pits)
        assertEquals(SowEvent.TurnSkipped(Player.TWO), result.events.last())
        assertEquals(1, result.events.count { it is SowEvent.TurnSkipped })
        assertEquals(1, result.newState.moveNumber)
        assertTrue(result.newState.isValid())
        assertEquals(listOf(1, 2, 3, 4, 5), result.newState.legalMoves())
    }

    @Test
    fun test6b_skippedPlayerGetsTurnBackWhenSeedsArrive() {
        // Player TWO is empty. Player ONE's last seed from p5 lands in p6, so TWO has seeds again.
        val state = boardState(listOf(9, 9, 9, 10, 10, 1, 0, 0, 0, 0, 0, 0))
        val result = engine.playTurn(state, 5)

        assertEquals(listOf(9, 9, 9, 10, 10, 0, 1, 0, 0, 0, 0, 0), result.newState.pits)
        assertEquals(Player.TWO, result.newState.currentPlayer)
        assertTrue(result.events.none { it is SowEvent.TurnSkipped })
        assertEquals(listOf(6), result.newState.legalMoves())
    }

    @Test
    fun test7_gameEnd_playerOneWins() {
        // The 11th capture: the stores hold 40 seeds, p1 reaches 4 and the game ends at once.
        val state = boardState(
            listOf(1, 3, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0),
            storeOne = 20,
            storeTwo = 20,
        )
        val result = engine.playTurn(state, 0)

        assertEquals(TurnOutcome.GAME_OVER, result.outcome)
        assertEquals(GameStatus.FINISHED, result.newState.status)
        assertEquals(44, result.newState.capturedTotal)
        assertEquals(24, result.newState.storeOne)
        assertEquals(20, result.newState.storeTwo)
        assertEquals(Player.ONE, result.newState.winner)
        assertEquals(SowEvent.GameOver(Player.ONE, 24, 20, 0), result.events.last())
        // The last 4 seeds stay on the board.
        assertEquals(4, result.newState.pits.sum())
        assertTrue(result.newState.legalMoves().isEmpty())
        assertTrue(result.newState.isValid())
    }

    @Test
    fun test7_gameEnd_playerTwoWins() {
        val state = boardState(
            listOf(4, 0, 0, 0, 0, 0, 1, 3, 0, 0, 0, 0),
            current = Player.TWO,
            storeOne = 20,
            storeTwo = 20,
        )
        val result = engine.playTurn(state, 6)

        assertEquals(TurnOutcome.GAME_OVER, result.outcome)
        assertEquals(20, result.newState.storeOne)
        assertEquals(24, result.newState.storeTwo)
        assertEquals(Player.TWO, result.newState.winner)
        assertEquals(SowEvent.GameOver(Player.TWO, 20, 24, 0), result.events.last())
        assertTrue(result.newState.isValid())
    }

    @Test
    fun test7_winnerIsNullWhileGameIsRunning() {
        assertNull(GameState.initial(Player.ONE).winner)
    }
}
