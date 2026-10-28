package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Test 8 (alternating starter) at logic level. */
class StarterPolicyTest {
    private val engine = SowingEngine()

    @Test
    fun everyModeStartsWithPlayerOne() {
        val policy = StarterPolicy()
        for (mode in GameMode.entries) {
            assertEquals(Player.ONE, policy.starterFor(mode))
        }
    }

    @Test
    fun test8_starterAlternatesEveryFinishedGame() {
        val mode = GameMode.TWO_PLAYERS_ONE_PHONE
        var policy = StarterPolicy()

        // Game 1 starts with Player ONE and is played to the end.
        val game1 = GameState.initial(policy.starterFor(mode))
        assertEquals(Player.ONE, game1.startingPlayer)
        assertEquals(Player.ONE, game1.currentPlayer)
        val end1 = playToEnd(engine, game1, 1)
        assertEquals(GameStatus.FINISHED, end1.status)
        policy = policy.afterGameFinished(mode, end1.startingPlayer)

        // Game 2 starts with Player TWO.
        val game2 = GameState.initial(policy.starterFor(mode))
        assertEquals(Player.TWO, game2.startingPlayer)
        assertEquals(Player.TWO, game2.currentPlayer)
        val end2 = playToEnd(engine, game2, 2)
        assertEquals(Player.TWO, end2.startingPlayer)
        policy = policy.afterGameFinished(mode, end2.startingPlayer)

        // Game 3 starts with Player ONE again.
        assertEquals(Player.ONE, policy.starterFor(mode))
    }

    @Test
    fun test8_recordIsKeptSeparatelyForEachMode() {
        val policy = StarterPolicy().afterGameFinished(GameMode.VS_COMPUTER, Player.ONE)
        assertEquals(Player.TWO, policy.starterFor(GameMode.VS_COMPUTER))
        assertEquals(Player.ONE, policy.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE))
        assertEquals(Player.ONE, policy.starterFor(GameMode.TWO_PHONES))
    }

    @Test
    fun test8_alternationContinuesAfterTheAppIsClosed() {
        // Phase 5 stores the plain map. Restoring it must give the same next starter.
        val before = StarterPolicy().afterGameFinished(GameMode.VS_COMPUTER, Player.ONE)
        val stored: Map<GameMode, Player> = before.toMap()
        val after = StarterPolicy.fromMap(stored)
        assertEquals(before, after)
        assertEquals(Player.TWO, after.starterFor(GameMode.VS_COMPUTER))
    }

    @Test
    fun missingModesInAStoredMapFallBackToPlayerOne() {
        val policy = StarterPolicy.fromMap(mapOf(GameMode.VS_COMPUTER to Player.TWO))
        assertEquals(Player.TWO, policy.starterFor(GameMode.VS_COMPUTER))
        assertEquals(Player.ONE, policy.starterFor(GameMode.TWO_PHONES))
        assertEquals(GameMode.entries.size, policy.toMap().size)
    }

    @Test
    fun restartAndUnfinishedGamesDoNotChangeTheStarter() {
        // The policy has no function for restart or abandoning: only afterGameFinished changes it.
        // A new game over an unfinished one asks the policy again and gets the same starter.
        val policy = StarterPolicy().afterGameFinished(GameMode.VS_COMPUTER, Player.ONE)
        val unfinished = GameState.initial(policy.starterFor(GameMode.VS_COMPUTER))
        val afterOneTurn = engine.playTurn(unfinished, unfinished.legalMoves().first()).newState
        assertEquals(Player.TWO, afterOneTurn.startingPlayer)

        val restarted = GameState.initial(policy.starterFor(GameMode.VS_COMPUTER))
        assertEquals(afterOneTurn.startingPlayer, restarted.startingPlayer)
        assertEquals(Player.TWO, policy.starterFor(GameMode.VS_COMPUTER))
    }

    @Test
    fun resumedGameKeepsItsOwnStartingPlayer() {
        val started = GameState.initial(Player.TWO)
        val played = engine.playTurn(started, started.legalMoves().first()).newState
        val resumed = GameState.fromSaveMap(played.toSaveMap())
        assertEquals(Player.TWO, resumed?.startingPlayer)
    }

    @Test
    fun resetPutsEveryModeBackToPlayerOne() {
        var policy = StarterPolicy()
        for (mode in GameMode.entries) {
            policy = policy.afterGameFinished(mode, Player.ONE)
        }
        assertTrue(GameMode.entries.all { policy.starterFor(it) == Player.TWO })

        val cleared = policy.reset()
        assertTrue(GameMode.entries.all { cleared.starterFor(it) == Player.ONE })
        assertEquals(StarterPolicy(), cleared)
    }
}
