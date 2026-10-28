package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Initial state, validation, and test 9 (save and resume) at logic level. */
class GameStateTest {
    private val engine = SowingEngine()
    private val initial = GameState.initial(Player.ONE)

    @Test
    fun initialStateFollowsSetupRules() {
        assertEquals(List(12) { 4 }, initial.pits)
        assertEquals(0, initial.storeOne)
        assertEquals(0, initial.storeTwo)
        assertEquals(0, initial.capturedTotal)
        assertEquals(0, initial.moveNumber)
        assertEquals(0, initial.leftoverInHand)
        assertEquals(GameStatus.IN_PROGRESS, initial.status)
        assertEquals(Player.ONE, initial.currentPlayer)
        assertEquals(Player.ONE, initial.startingPlayer)
        assertTrue(initial.isValid())
    }

    @Test
    fun initialStateForPlayerTwoStartsWithPlayerTwo() {
        val state = GameState.initial(Player.TWO)
        assertEquals(Player.TWO, state.currentPlayer)
        assertEquals(Player.TWO, state.startingPlayer)
        assertEquals(listOf(6, 7, 8, 9, 10, 11), state.legalMoves())
        assertTrue(state.isValid())
    }

    @Test
    fun ownersSidesAndLegalMoves() {
        for (pit in 0..5) assertEquals(Player.ONE, initial.ownerOf(pit))
        for (pit in 6..11) assertEquals(Player.TWO, initial.ownerOf(pit))
        assertEquals(24, initial.seedsOnSide(Player.ONE))
        assertEquals(24, initial.seedsOnSide(Player.TWO))
        assertEquals(listOf(0, 1, 2, 3, 4, 5), initial.legalMoves())
        assertEquals(0, initial.storeOf(Player.ONE))
    }

    @Test
    fun nextPitAfterPitElevenIsPitZero() {
        // Anticlockwise numbering: after pit 11 comes pit 0 (RULES.md section 1).
        val state = boardState(listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1), current = Player.TWO)
        val result = engine.playTurn(state, 11)
        assertEquals(SowEvent.Drop(0, 1, 0), result.events[1])
    }

    @Test
    fun invalidStatesAreRejected() {
        val badPits = initial.pits.toMutableList()
        badPits[0] = -1
        val fivePit = initial.pits.toMutableList()
        fivePit[0] = 5

        assertFalse(initial.copy(pits = List(11) { 4 }).isValid())
        assertFalse(initial.copy(pits = badPits).isValid())
        assertFalse(initial.copy(storeOne = 2, capturedTotal = 2).isValid())
        assertFalse(initial.copy(storeOne = -4, capturedTotal = -4).isValid())
        assertFalse(initial.copy(capturedTotal = 4).isValid())
        assertFalse(initial.copy(pits = fivePit).isValid())
        assertFalse(initial.copy(storeOne = 48, capturedTotal = 48, pits = List(12) { 0 }).isValid())
        assertFalse(initial.copy(status = GameStatus.FINISHED).isValid())
        assertFalse(initial.copy(leftoverInHand = 1).isValid())
        assertFalse(initial.copy(moveNumber = -1).isValid())
    }

    @Test
    fun currentPlayerWithoutSeedsIsInvalidWhileInProgress() {
        val pits = List(12) { if (it < 6) 0 else 8 }
        val state = initial.copy(pits = pits)
        assertFalse(state.isValid())
        assertTrue(state.copy(currentPlayer = Player.TWO).isValid())
    }

    @Test
    fun finishedStateIsValidOnlyAtFortyFourCaptured() {
        val finished = boardState(
            listOf(1, 3, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0),
            storeOne = 20,
            storeTwo = 20,
        ).let { engine.playTurn(it, 0).newState }
        assertTrue(finished.isValid())
        assertFalse(finished.copy(status = GameStatus.IN_PROGRESS).isValid())
    }

    @Test
    fun test9_saveAndResumeGivesIdenticalState() {
        // Play 5 turns, save to a plain map, restore: everything must be identical.
        var state = GameState.initial(Player.TWO)
        val random = kotlin.random.Random(11)
        repeat(5) {
            state = engine.playTurn(state, randomLegalMove(state, random)).newState
        }
        assertEquals(5, state.moveNumber)

        val saved = state.toSaveMap()
        assertEquals(GameState.SCHEMA_VERSION, saved[GameState.KEY_SCHEMA_VERSION])
        val restored = GameState.fromSaveMap(saved)

        assertEquals(state, restored)
        assertEquals(state.pits, restored?.pits)
        assertEquals(state.storeOne, restored?.storeOne)
        assertEquals(state.storeTwo, restored?.storeTwo)
        assertEquals(state.currentPlayer, restored?.currentPlayer)
        assertEquals(Player.TWO, restored?.startingPlayer)
    }

    @Test
    fun test9_savedMapOfAFinishedGameRestoresToo() {
        val finished = playToEnd(engine, GameState.initial(Player.ONE), 5)
        assertEquals(finished, GameState.fromSaveMap(finished.toSaveMap()))
    }

    @Test
    fun test9_numbersStoredAsLongStillRestore() {
        val saved = initial.toSaveMap().toMutableMap()
        saved[GameState.KEY_PITS] = initial.pits.map { it.toLong() }
        saved[GameState.KEY_STORE_ONE] = 0L
        assertEquals(initial, GameState.fromSaveMap(saved))
    }

    @Test
    fun test9_damagedSavesAreRejectedWithoutCrashing() {
        val good = initial.toSaveMap()

        val oneSeedLost = good.toMutableMap()
        oneSeedLost[GameState.KEY_PITS] = List(12) { if (it == 0) 3 else 4 }
        assertNull(GameState.fromSaveMap(oneSeedLost))

        val wrongVersion = good.toMutableMap()
        wrongVersion[GameState.KEY_SCHEMA_VERSION] = 99
        assertNull(GameState.fromSaveMap(wrongVersion))

        val missingVersion = good.toMutableMap()
        missingVersion.remove(GameState.KEY_SCHEMA_VERSION)
        assertNull(GameState.fromSaveMap(missingVersion))

        val missingPits = good.toMutableMap()
        missingPits.remove(GameState.KEY_PITS)
        assertNull(GameState.fromSaveMap(missingPits))

        val wrongType = good.toMutableMap()
        wrongType[GameState.KEY_STORE_ONE] = "zero"
        assertNull(GameState.fromSaveMap(wrongType))

        val textInPits = good.toMutableMap()
        textInPits[GameState.KEY_PITS] = List(12) { "4" }
        assertNull(GameState.fromSaveMap(textInPits))

        val unknownPlayer = good.toMutableMap()
        unknownPlayer[GameState.KEY_CURRENT_PLAYER] = "THREE"
        assertNull(GameState.fromSaveMap(unknownPlayer))

        val unknownStatus = good.toMutableMap()
        unknownStatus[GameState.KEY_STATUS] = "PAUSED"
        assertNull(GameState.fromSaveMap(unknownStatus))

        val tooBig = good.toMutableMap()
        tooBig[GameState.KEY_STORE_ONE] = Long.MAX_VALUE
        assertNull(GameState.fromSaveMap(tooBig))

        assertNull(GameState.fromSaveMap(emptyMap()))
    }
}
