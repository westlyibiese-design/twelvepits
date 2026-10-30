package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.EngineLogger
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.boardState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.random.Random

/** A clock the test moves by hand. A waiting coroutine continues when enough fake time has passed. */
private class FakeClock {
    private class Waiter(val deadline: Long, val resume: () -> Unit)

    private val waiters = ArrayList<Waiter>()
    private var now = 0L

    val waiting: Int
        get() = waiters.size

    suspend fun pause(millis: Long) {
        suspendCancellableCoroutine { continuation ->
            val waiter = Waiter(now + millis) { continuation.resume(Unit) }
            waiters.add(waiter)
            continuation.invokeOnCancellation { waiters.remove(waiter) }
        }
    }

    fun advanceBy(millis: Long) {
        now += millis
        for (waiter in waiters.toList()) {
            if (waiter.deadline <= now && waiters.remove(waiter)) waiter.resume()
        }
    }
}

private class RecordingLog : EngineLogger {
    val messages = ArrayList<String>()

    override fun error(message: String) {
        messages.add(message)
    }
}

/** The computer's turn with a fake player and a fake clock. */
class ComputerTurnSchedulerTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val clock = FakeClock()
    private val log = RecordingLog()
    private val chosen = ArrayList<Pair<GameState, Int>>()

    /** Player TWO owns pits 6 to 11 and has seeds in pits 7 and 9 only. */
    private val computersTurn: GameState = boardState(
        pits = listOf(2, 0, 3, 0, 1, 0, 0, 5, 0, 2, 0, 0),
        current = Player.TWO,
    )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun scheduler(player: ComputerPlayer) = ComputerTurnScheduler(
        player = player,
        scope = scope,
        onPitChosen = { state, pit -> chosen.add(state to pit) },
        logger = log,
        computeContext = Dispatchers.Unconfined,
        pause = clock::pause,
    )

    private fun fixedPlayer(pit: Int) = object : ComputerPlayer {
        override suspend fun choosePit(state: GameState): Int = pit
    }

    @Test
    fun theComputerWaitsThenPlaysTheChosenPit() {
        val scheduler = scheduler(fixedPlayer(9))
        scheduler.start(computersTurn)
        assertTrue(scheduler.isThinking)
        clock.advanceBy(799)
        assertTrue(chosen.isEmpty())
        assertTrue(scheduler.isThinking)
        clock.advanceBy(1)
        assertEquals(listOf(computersTurn to 9), chosen)
        assertFalse(scheduler.isThinking)
    }

    @Test
    fun theThinkingTimeIsEightHundredMilliseconds() {
        assertEquals(800L, ComputerTurnScheduler.THINK_MILLIS)
    }

    @Test
    fun startingWhileThinkingDoesNotStartASecondTurn() {
        val scheduler = scheduler(fixedPlayer(7))
        scheduler.start(computersTurn)
        scheduler.start(computersTurn)
        assertEquals(1, clock.waiting)
        clock.advanceBy(800)
        assertEquals(1, chosen.size)
    }

    @Test
    fun cancelStopsThinkingAndNothingIsPlayed() {
        val scheduler = scheduler(fixedPlayer(7))
        scheduler.start(computersTurn)
        clock.advanceBy(500)
        scheduler.cancel()
        assertFalse(scheduler.isThinking)
        clock.advanceBy(10_000)
        assertTrue(chosen.isEmpty())
    }

    @Test
    fun afterACancelThinkingStartsAgainWithTheFullWait() {
        val scheduler = scheduler(fixedPlayer(7))
        scheduler.start(computersTurn)
        clock.advanceBy(700)
        scheduler.cancel()
        // The game was paused and resumed: the computer thinks again from the start.
        scheduler.start(computersTurn)
        clock.advanceBy(799)
        assertTrue(chosen.isEmpty())
        clock.advanceBy(1)
        assertEquals(listOf(computersTurn to 7), chosen)
    }

    @Test
    fun cancelWhileTheComputerIsStillWorkingOutItsMovePlaysNothing() = runBlocking {
        val gate = CompletableDeferred<Int>()
        val slowPlayer = object : ComputerPlayer {
            override suspend fun choosePit(state: GameState): Int = gate.await()
        }
        val scheduler = scheduler(slowPlayer)
        scheduler.start(computersTurn)
        clock.advanceBy(800)
        assertTrue(scheduler.isThinking)
        scheduler.cancel()
        gate.complete(7)
        assertTrue(chosen.isEmpty())
        assertFalse(scheduler.isThinking)
    }

    @Test
    fun aPitThatIsNotALegalMoveIsReplacedByTheFirstLegalPitAndLogged() {
        for (illegal in listOf(0, 2, 8, 11, 12, -1, 99)) {
            chosen.clear()
            log.messages.clear()
            val scheduler = scheduler(fixedPlayer(illegal))
            scheduler.start(computersTurn)
            clock.advanceBy(800)
            assertEquals("pit $illegal", listOf(computersTurn to 7), chosen)
            assertEquals("pit $illegal", 1, log.messages.size)
        }
    }

    @Test
    fun aLegalPitIsNotLogged() {
        val scheduler = scheduler(fixedPlayer(9))
        scheduler.start(computersTurn)
        clock.advanceBy(800)
        assertTrue(log.messages.isEmpty())
    }

    @Test
    fun noLegalPitMeansNothingIsPlayedAndAnErrorIsLogged() {
        val stuck = boardState(
            pits = listOf(2, 0, 3, 0, 1, 0, 0, 0, 0, 0, 0, 0),
            current = Player.TWO,
        )
        val scheduler = scheduler(fixedPlayer(7))
        scheduler.start(stuck)
        clock.advanceBy(800)
        assertTrue(chosen.isEmpty())
        assertEquals(1, log.messages.size)
        assertFalse(scheduler.isThinking)
    }

    @Test
    fun theThinkingTimeCountsInsideTheWait() = runBlocking {
        val gate = CompletableDeferred<Int>()
        val player = object : ComputerPlayer {
            override suspend fun choosePit(state: GameState): Int = gate.await()
        }
        scheduler(player).start(computersTurn)
        clock.advanceBy(300)
        // The player answers after 0.3 s: the computer still plays only after the full 0.8 s.
        gate.complete(9)
        clock.advanceBy(499)
        assertTrue(chosen.isEmpty())
        clock.advanceBy(1)
        assertEquals(listOf(computersTurn to 9), chosen)
    }

    @Test
    fun aSlowPlayerIsWaitedForAndPlaysAsSoonAsItAnswers() = runBlocking {
        val gate = CompletableDeferred<Int>()
        val player = object : ComputerPlayer {
            override suspend fun choosePit(state: GameState): Int = gate.await()
        }
        val scheduler = scheduler(player)
        scheduler.start(computersTurn)
        clock.advanceBy(2_000)
        assertTrue(chosen.isEmpty())
        assertTrue(scheduler.isThinking)
        gate.complete(7)
        assertEquals(listOf(computersTurn to 7), chosen)
        assertFalse(scheduler.isThinking)
    }

    @Test
    fun aPlayerThatFailsIsReplacedByTheFirstLegalPitAndLogged() {
        val broken = object : ComputerPlayer {
            override suspend fun choosePit(state: GameState): Int = throw IllegalStateException("broken")
        }
        val scheduler = scheduler(broken)
        scheduler.start(computersTurn)
        clock.advanceBy(800)
        assertEquals(listOf(computersTurn to 7), chosen)
        assertEquals(1, log.messages.size)
        assertFalse(scheduler.isThinking)
    }

    @Test
    fun theTemporaryPlayerAlwaysPicksALegalPitAndSometimesDifferentOnes() = runBlocking {
        val player = RandomLegalMovePlayer(Random(5))
        val picks = HashSet<Int>()
        repeat(60) { picks.add(player.choosePit(computersTurn)) }
        assertEquals(setOf(7, 9), picks)
    }

    @Test
    fun theTemporaryPlayerReturnsNoPitWhenThereIsNoMove() = runBlocking {
        val empty = boardState(pits = List(12) { 0 }, current = Player.TWO)
        assertEquals(-1, RandomLegalMovePlayer().choosePit(empty))
    }

    @Test
    fun aWholeGameAgainstTheTemporaryPlayerCanBePlayedToTheEnd() = runBlocking {
        val engine = com.westly.twelvepits.feature.game.engine.SowingEngine()
        val player = RandomLegalMovePlayer(Random(2))
        val human = Random(3)
        var state = GameState.initial(Player.ONE)
        var turns = 0
        while (state.status == com.westly.twelvepits.feature.game.engine.GameStatus.IN_PROGRESS) {
            val pit = if (state.currentPlayer == Player.TWO) player.choosePit(state) else state.legalMoves().let { it[human.nextInt(it.size)] }
            assertTrue(pit in state.legalMoves())
            state = engine.playTurn(state, pit).newState
            turns += 1
            assertTrue(turns < 100_000)
        }
        assertTrue(state.isValid())
    }
}
