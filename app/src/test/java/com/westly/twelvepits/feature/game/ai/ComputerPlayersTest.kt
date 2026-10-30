package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import com.westly.twelvepits.feature.game.engine.boardState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Behaviour that every computer player must have, whatever its strength. */
class ComputerPlayersTest {
    private val engine = SowingEngine()

    /** The same fast, deterministic Hard player for every test: shallow, and the clock never runs out. */
    private fun fastHard(seed: Int) = HardPlayer(Random(seed), maxDepth = 3, clock = { 0L })

    private fun allPlayers(seed: Int): Map<String, ComputerPlayer> = mapOf(
        "Easy" to EasyPlayer(Random(seed)),
        "Normal" to NormalPlayer(Random(seed)),
        "Hard" to fastHard(seed),
    )

    /** Player TWO is one capture from the end of the game: pit 6 puts its last seed in pit 7, making 4. */
    private val beforeLastCapture = GameState(
        pits = listOf(1, 1, 1, 1, 0, 0, 1, 3, 0, 0, 0, 0),
        storeOne = 20,
        storeTwo = 20,
        currentPlayer = Player.TWO,
        startingPlayer = Player.ONE,
        capturedTotal = 40,
        moveNumber = 30,
        status = GameStatus.IN_PROGRESS,
    )

    /** Player ONE has no seeds, so Player TWO plays again after most moves. */
    private val opponentHasNoSeeds = GameState(
        pits = listOf(0, 0, 0, 0, 0, 0, 2, 1, 0, 3, 0, 2),
        storeOne = 20,
        storeTwo = 20,
        currentPlayer = Player.TWO,
        startingPlayer = Player.ONE,
        capturedTotal = 40,
        moveNumber = 31,
        status = GameStatus.IN_PROGRESS,
    )

    @Test
    fun theFactoryMakesThePlayerOfEachDifficulty() {
        assertTrue(ComputerPlayers.forDifficulty(Difficulty.EASY) is EasyPlayer)
        assertTrue(ComputerPlayers.forDifficulty(Difficulty.NORMAL) is NormalPlayer)
        assertTrue(ComputerPlayers.forDifficulty(Difficulty.HARD) is HardPlayer)
    }

    @Test
    fun everyPlayerAlwaysChoosesALegalPitInFiveThousandRandomPositions() = runBlocking {
        val positions = randomPlayoutPositions(count = 5_000, seed = 100)
        assertEquals(5_000, positions.size)
        for ((name, player) in allPlayers(seed = 1)) {
            for (position in positions) {
                val pit = player.choosePit(position)
                assertTrue("$name chose pit $pit in $position", pit in position.legalMoves())
            }
        }
    }

    @Test
    fun withExactlyOneLegalMoveEveryPlayerPlaysIt() = runBlocking {
        val onlyPitNine = boardState(
            pits = listOf(2, 0, 3, 0, 1, 0, 0, 0, 0, 2, 0, 0),
            current = Player.TWO,
        )
        val onlyPitTwo = boardState(
            pits = listOf(0, 0, 5, 0, 0, 0, 1, 1, 0, 0, 0, 3),
            current = Player.ONE,
        )
        for ((name, player) in allPlayers(seed = 2)) {
            assertEquals(name, 9, player.choosePit(onlyPitNine))
            assertEquals(name, 2, player.choosePit(onlyPitTwo))
        }
    }

    @Test
    fun theComputerCanPlayForPlayerOneToo() = runBlocking {
        val position = boardState(
            pits = listOf(2, 0, 3, 0, 1, 0, 0, 5, 0, 2, 0, 0),
            current = Player.ONE,
        )
        for ((name, player) in allPlayers(seed = 3)) {
            assertTrue(name, player.choosePit(position) in setOf(0, 2, 4))
        }
    }

    @Test
    fun rightBeforeTheLastCaptureEveryPlayerChoosesALegalPit() = runBlocking {
        for ((name, player) in allPlayers(seed = 4)) {
            val pit = player.choosePit(beforeLastCapture)
            assertTrue(name, pit in beforeLastCapture.legalMoves())
        }
    }

    @Test
    fun normalAndHardTakeTheLastCaptureAndWin() = runBlocking {
        for (player in listOf(NormalPlayer(Random(5)), fastHard(5))) {
            assertEquals(6, player.choosePit(beforeLastCapture))
        }
        val after = engine.simulate(beforeLastCapture, 6)
        assertEquals(GameStatus.FINISHED, after.status)
        assertEquals(Player.TWO, after.winner)
    }

    @Test
    fun skippedTurnsNeverBreakAnyPlayer() = runBlocking {
        for ((name, player) in allPlayers(seed = 6)) {
            var state = opponentHasNoSeeds
            var turns = 0
            while (state.status == GameStatus.IN_PROGRESS) {
                val pit = player.choosePit(state)
                assertTrue("$name chose pit $pit in $state", pit in state.legalMoves())
                state = engine.simulate(state, pit)
                turns += 1
                assertTrue(name, turns < 1_000)
            }
            assertTrue(name, state.isValid())
        }
    }

    @Test
    fun aPositionWithNoLegalMoveThrowsAClearException() = runBlocking {
        val noSeeds = boardState(pits = listOf(2, 0, 3, 0, 1, 0, 0, 0, 0, 0, 0, 0), current = Player.TWO)
        val finished = engine.simulate(beforeLastCapture, 6)
        for ((name, player) in allPlayers(seed = 7)) {
            for (position in listOf(noSeeds, finished)) {
                try {
                    player.choosePit(position)
                    fail("$name should have thrown")
                } catch (expected: NoLegalMoveException) {
                    assertFalse(name, expected.message.isNullOrBlank())
                }
            }
        }
    }

    @Test
    fun easyMixesRandomMovesWithTheBestImmediateGain() = runBlocking {
        // Pit 6 captures 4 seeds at once. Pits 7, 8 and 11 capture nothing.
        val position = boardState(
            pits = listOf(1, 1, 1, 1, 0, 0, 1, 3, 2, 0, 0, 2),
            current = Player.TWO,
        )
        val player = EasyPlayer(Random(8))
        val counts = HashMap<Int, Int>()
        repeat(2_000) {
            val pit = player.choosePit(position)
            counts[pit] = (counts[pit] ?: 0) + 1
        }
        assertEquals(setOf(6, 7, 8, 11), counts.keys)
        // Expected: 0.30 + 0.70 / 4 = 0.475 of the games.
        val share = (counts[6] ?: 0) / 2_000.0
        assertTrue("share of the best pit was $share", share > 0.40 && share < 0.55)
    }

    @Test
    fun normalTakesACaptureWhenItCan() = runBlocking {
        val position = boardState(
            pits = listOf(1, 1, 1, 1, 0, 0, 1, 3, 2, 0, 0, 2),
            current = Player.TWO,
        )
        repeat(20) { seed ->
            assertEquals(6, NormalPlayer(Random(seed)).choosePit(position))
        }
    }

    /** Store difference for [me] after [pit], when the player who moves next answers as well as they can. */
    private fun twoTurnValue(state: GameState, pit: Int, me: Player): Int {
        val after = engine.simulate(state, pit)
        val replies = after.legalMoves()
        if (replies.isEmpty()) return after.storeOf(me) - after.storeOf(me.opponent)
        val values = replies.map {
            val result = engine.simulate(after, it)
            result.storeOf(me) - result.storeOf(me.opponent)
        }
        return if (after.currentPlayer == me) values.max() else values.min()
    }

    @Test
    fun normalAlwaysPlaysAPitWithTheBestTwoTurnValue() = runBlocking {
        val player = NormalPlayer(Random(16))
        for (position in randomPlayoutPositions(count = 400, seed = 600)) {
            val me = position.currentPlayer
            val values = position.legalMoves().associateWith { twoTurnValue(position, it, me) }
            val chosen = player.choosePit(position)
            assertEquals("position $position", values.values.max(), values[chosen])
        }
    }

    @Test
    fun hardGivesTheSameAnswerForTheSameSeedAndDepth() = runBlocking {
        for (position in randomPlayoutPositions(count = 60, seed = 300)) {
            val first = fastHard(9).choosePit(position)
            val second = fastHard(9).choosePit(position)
            assertEquals(first, second)
        }
    }

    @Test
    fun hardStopsWhenItsTimeBudgetIsUsed() = runBlocking {
        var now = 0L
        var calls = 0
        val player = HardPlayer(
            random = Random(10),
            maxDepth = 8,
            timeBudgetMillis = 1_200L,
            clock = {
                calls += 1
                now += 100L
                now
            },
        )
        val start = GameState.initial(Player.TWO)
        val pit = player.choosePit(start)
        assertTrue(pit in start.legalMoves())
        // A full depth 8 search needs hundreds of thousands of nodes and clock checks.
        assertTrue("the clock was read $calls times", calls < 60)
    }

    @Test
    fun hardStopsWhenItsNodeLimitIsUsedAndStillPlaysALegalPit() = runBlocking {
        for (limit in listOf(1L, 2L, 10L, 500L)) {
            val player = HardPlayer(Random(11), maxDepth = 8, nodeLimit = limit, clock = { 0L })
            for (position in randomPlayoutPositions(count = 40, seed = 400)) {
                assertTrue(player.choosePit(position) in position.legalMoves())
            }
        }
    }

    @Test
    fun hardWithAHigherDepthStillPlaysLegalPits() = runBlocking {
        val player = HardPlayer(Random(12), maxDepth = 6, nodeLimit = 5_000, clock = { 0L })
        for (position in randomPlayoutPositions(count = 100, seed = 500)) {
            assertTrue(player.choosePit(position) in position.legalMoves())
        }
    }

    @Test
    fun cancellingARunningHardSearchStopsItAtOnce() = runBlocking {
        val started = CountDownLatch(1)
        // Reading the clock is slowed down on purpose, so the search is still busy when it is cancelled.
        val player = HardPlayer(
            random = Random(13),
            maxDepth = 12,
            timeBudgetMillis = Long.MAX_VALUE / 4,
            clock = {
                started.countDown()
                Thread.sleep(1)
                0L
            },
        )
        val search = async(Dispatchers.Default) { player.choosePit(GameState.initial(Player.TWO)) }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        search.cancel()
        withTimeout(5_000) { search.join() }
        assertTrue(search.isCancelled)
    }

    @Test
    fun aFullGameBetweenTwoHardPlayersEndsWithAValidState() = runBlocking {
        val one = HardPlayer(Random(14), maxDepth = 2, clock = { 0L })
        val two = HardPlayer(Random(15), maxDepth = 2, clock = { 0L })
        var state = GameState.initial(Player.ONE)
        var turns = 0
        while (state.status == GameStatus.IN_PROGRESS) {
            val player = if (state.currentPlayer == Player.ONE) one else two
            state = engine.simulate(state, player.choosePit(state))
            turns += 1
            assertTrue(turns < 10_000)
        }
        assertTrue(state.isValid())
    }
}
