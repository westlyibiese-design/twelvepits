package com.westly.twelvepits.feature.result

import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.data.TurnStats
import com.westly.twelvepits.feature.game.data.finishedGame
import com.westly.twelvepits.feature.game.data.playTurns
import com.westly.twelvepits.feature.game.data.testConfig
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import com.westly.twelvepits.feature.game.engine.randomLegalMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The result screen's numbers, and the best-capture bookkeeping behind them. */
class GameResultTest {
    @Test
    fun bestCaptureIsTheBiggestSingleTurnOfEachPlayer() {
        var stats = TurnStats()
        stats = stats.afterTurn(Player.ONE, 4)
        stats = stats.afterTurn(Player.TWO, 12)
        stats = stats.afterTurn(Player.ONE, 8)
        stats = stats.afterTurn(Player.ONE, 0)
        stats = stats.afterTurn(Player.TWO, 4)
        assertEquals(8, stats.bestCaptureOf(Player.ONE))
        assertEquals(12, stats.bestCaptureOf(Player.TWO))
    }

    @Test
    fun bestCaptureFollowsTheEnginesCapturedForMoverOverAWholeGame() {
        for (seed in 1..25) {
            val engine = SowingEngine()
            val random = Random(seed)
            var state = GameState.initial(if (seed % 2 == 0) Player.ONE else Player.TWO)
            var stats = TurnStats()
            var bestOne = 0
            var bestTwo = 0
            var capturedByMovers = 0
            while (state.status == GameStatus.IN_PROGRESS) {
                val mover = state.currentPlayer
                val result = engine.playTurn(state, randomLegalMove(state, random))
                stats = stats.afterTurn(mover, result.capturedForMover)
                if (mover == Player.ONE) bestOne = maxOf(bestOne, result.capturedForMover) else bestTwo = maxOf(bestTwo, result.capturedForMover)
                capturedByMovers += result.capturedForMover
                state = result.newState
            }
            assertEquals("seed $seed", bestOne, stats.bestCaptureOf(Player.ONE))
            assertEquals("seed $seed", bestTwo, stats.bestCaptureOf(Player.TWO))
            // A player's best single turn is never more than the seeds in their store, and is whole groups of 4.
            assertTrue(stats.bestCaptureOf(Player.ONE) <= state.storeOne)
            assertTrue(stats.bestCaptureOf(Player.TWO) <= state.storeTwo)
            assertEquals(0, stats.bestCaptureOf(Player.ONE) % 4)
            assertEquals(0, stats.bestCaptureOf(Player.TWO) % 4)
        }
    }

    @Test
    fun theResultShowsTheWinnersScoresMovesTimeAndBestCapture() {
        val (finished, stats) = finishedGame(Player.ONE, 5)
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)
        val result = GameResult.from(config, finished, stats, 402_000L)
        val winner = if (finished.storeOne > finished.storeTwo) Player.ONE else Player.TWO
        assertEquals(winner, result.winner)
        assertEquals(finished.storeOne, result.scoreOne)
        assertEquals(finished.storeTwo, result.scoreTwo)
        assertEquals(finished.storeOf(winner), result.winnerScore)
        assertEquals(finished.storeOf(winner.opponent), result.loserScore)
        assertTrue(result.winnerScore > result.loserScore)
        assertEquals(finished.moveNumber, result.moves)
        assertEquals(stats.bestCaptureOf(winner), result.bestCapture)
        assertEquals(402_000L, result.elapsedMs)
        assertEquals("06:42", formatPlayTime(result.elapsedMs))
    }

    @Test
    fun theSeedsLeftOnTheBoardAreNeverCounted() {
        val (finished, stats) = finishedGame(Player.TWO, 9)
        val result = GameResult.from(testConfig(), finished, stats, 0L)
        assertEquals(44, result.scoreOne + result.scoreTwo)
        assertEquals(4, finished.pits.sum() + finished.leftoverInHand)
    }

    @Test
    fun thereIsNeverADrawInManyGames() {
        for (seed in 1..60) {
            val (finished, stats) = finishedGame(if (seed % 2 == 0) Player.ONE else Player.TWO, seed)
            val result = GameResult.from(testConfig(), finished, stats, 1000L)
            assertNotEquals(result.scoreOne, result.scoreTwo)
            assertTrue(result.winnerScore >= 24)
            assertTrue(result.loserScore <= 20)
        }
    }

    @Test
    fun againstTheComputerTheHumanWinningIsVictoryAndTheComputerWinningIsDefeat() {
        val config = testConfig(GameMode.VS_COMPUTER)
        var sawVictory = false
        var sawDefeat = false
        for (seed in 1..80) {
            val (finished, stats) = finishedGame(Player.ONE, seed)
            val result = GameResult.from(config, finished, stats, 0L)
            if (result.winner == Player.ONE) {
                assertEquals(ResultHeadline.VICTORY, result.headline)
                assertEquals("Ada", result.winnerName)
                sawVictory = true
            } else {
                assertEquals(ResultHeadline.DEFEAT, result.headline)
                assertEquals("Computer", result.winnerName)
                sawDefeat = true
            }
        }
        assertTrue(sawVictory && sawDefeat)
    }

    @Test
    fun twoPlayersAlwaysShowTheWinnersName() {
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)
        for (seed in 1..20) {
            val (finished, stats) = finishedGame(Player.ONE, seed)
            val result = GameResult.from(config, finished, stats, 0L)
            assertEquals(ResultHeadline.WINNER_NAMED, result.headline)
            assertEquals(if (result.winner == Player.ONE) "Ada" else "Bola", result.winnerName)
        }
    }

    @Test
    fun playTimeIsShownAsMinutesAndSeconds() {
        assertEquals("00:00", formatPlayTime(0L))
        assertEquals("00:00", formatPlayTime(999L))
        assertEquals("00:01", formatPlayTime(1_000L))
        assertEquals("00:59", formatPlayTime(59_999L))
        assertEquals("01:00", formatPlayTime(60_000L))
        assertEquals("06:42", formatPlayTime(402_000L))
        assertEquals("59:59", formatPlayTime(3_599_000L))
        assertEquals("60:00", formatPlayTime(3_600_000L))
        assertEquals("125:07", formatPlayTime(7_507_000L))
        assertEquals("00:00", formatPlayTime(-5_000L))
    }

    @Test
    fun aGameInProgressHelperReallyStopsEarly() {
        val (middle, _) = playTurns(GameState.initial(Player.ONE), 6, 3)
        assertEquals(GameStatus.IN_PROGRESS, middle.status)
        assertEquals(6, middle.moveNumber)
    }
}
