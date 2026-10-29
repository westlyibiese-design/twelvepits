package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.core.model.SeedSpeed
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowEvent
import com.westly.twelvepits.feature.game.engine.SowingEngine
import com.westly.twelvepits.feature.game.engine.randomLegalMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The animation player runs on a fake clock here: no test ever waits. */
class SowAnimatorTest {
    private val engine = SowingEngine()

    private class CountingSounds : AnimationSounds {
        var pickUps = 0
        var drops = 0
        var captures = 0
        var skips = 0

        override fun pickUp() {
            pickUps += 1
        }

        override fun drop() {
            drops += 1
        }

        override fun capture() {
            captures += 1
        }

        override fun turnSkipped() {
            skips += 1
        }

        val total: Int get() = pickUps + drops + captures + skips
    }

    private fun timingOf(speed: SeedSpeed): () -> AnimationTiming {
        val timing = AnimationTiming(speed.hopSeconds)
        return { timing }
    }

    /** Seeds that are somewhere on the board, in hand or in flight. */
    private fun seedsShown(display: BoardDisplayState): Int {
        var total = display.stores[Player.ONE.ordinal] + display.stores[Player.TWO.ordinal]
        for (count in display.pits) {
            total += count
        }
        if (display.handActive) total += display.handCount
        if (display.captureFlyActive) total += GameState.CAPTURE_SIZE
        return total
    }

    /** Runs the animator on the fake clock until it is finished and returns the seconds that passed. */
    private fun runToEnd(animator: SowAnimator, step: Float, afterEachStep: () -> Unit = {}): Float {
        var time = 0f
        var guard = 0
        while (!animator.isFinished) {
            animator.advance(step)
            time += step
            afterEachStep()
            guard += 1
            assertTrue("The animation never finished", guard < 5_000_000)
        }
        return time
    }

    private fun expectedSeconds(events: List<SowEvent>, timing: AnimationTiming): Float {
        var total = 0f
        for (event in events) {
            total += when (event) {
                is SowEvent.PickUp -> timing.pickUp
                is SowEvent.Drop -> timing.hop
                is SowEvent.RelayPause -> timing.relayWait
                is SowEvent.Capture -> timing.captureGlow + timing.captureFly
                is SowEvent.TurnSkipped -> timing.skippedBanner
                is SowEvent.GameOver -> AnimationTiming.GAME_OVER_HOLD
                is SowEvent.LoopStopped -> 0f
                is SowEvent.StepLimitReached -> 0f
            }
        }
        return total
    }

    @Test
    fun theFirstMoveOnPitZeroEndsExactlyAsTestOneOfRulesMd() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 0)
        val display = BoardDisplayState()
        display.reset(start)
        val sounds = CountingSounds()
        val animator = SowAnimator(display, result.events, timingOf(SeedSpeed.NORMAL), sounds)
        runToEnd(animator, 0.016f)
        assertEquals(listOf(2, 7, 1, 6, 1, 6, 6, 6, 0, 1, 6, 6), display.pits.toList())
        assertEquals(0, display.stores[Player.ONE.ordinal])
        assertEquals(0, display.stores[Player.TWO.ordinal])
        assertTrue(display.countsMatch(result.newState))
        assertTrue(display.isSettled)
    }

    @Test
    fun soundsAreAskedForOncePerEvent() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 0)
        val display = BoardDisplayState()
        display.reset(start)
        val sounds = CountingSounds()
        runToEnd(SowAnimator(display, result.events, timingOf(SeedSpeed.NORMAL), sounds), 0.05f)
        assertEquals(result.events.count { it is SowEvent.PickUp }, sounds.pickUps)
        assertEquals(result.events.count { it is SowEvent.Drop }, sounds.drops)
        assertEquals(result.events.count { it is SowEvent.Capture }, sounds.captures)
        assertEquals(result.events.count { it is SowEvent.TurnSkipped }, sounds.skips)
    }

    @Test
    fun aTurnTakesExactlyTheWrittenTimeAtSlowNormalAndFast() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 0)
        for (speed in SeedSpeed.entries) {
            val timing = AnimationTiming(speed.hopSeconds)
            val display = BoardDisplayState()
            display.reset(start)
            val step = 0.01f
            val seconds = runToEnd(SowAnimator(display, result.events, { timing }, SilentSounds), step)
            val expected = expectedSeconds(result.events, timing)
            assertEquals("Speed $speed", expected, seconds, step * 2f)
        }
    }

    @Test
    fun theSameFinalBoardComesOutAtEverySpeed() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 3)
        for (speed in SeedSpeed.entries) {
            val display = BoardDisplayState()
            display.reset(start)
            runToEnd(SowAnimator(display, result.events, timingOf(speed), SilentSounds), 0.02f)
            assertTrue("Speed $speed", display.countsMatch(result.newState))
        }
    }

    @Test
    fun aNewSeedSpeedAppliesFromTheNextStep() {
        val start = GameState.initial(Player.ONE)
        val events = listOf<SowEvent>(
            SowEvent.PickUp(0, 2),
            SowEvent.Drop(1, 5, 1),
            SowEvent.Drop(2, 5, 0),
        )
        val display = BoardDisplayState()
        display.reset(start)
        var timing = AnimationTiming(SeedSpeed.NORMAL.hopSeconds)
        val animator = SowAnimator(display, events, { timing }, SilentSounds)
        animator.advance(0.3f)
        timing = AnimationTiming(SeedSpeed.FAST.hopSeconds)
        animator.advance(0.4f)
        assertFalse(animator.isFinished)
        animator.advance(0.19f)
        assertFalse(animator.isFinished)
        animator.advance(0.02f)
        assertTrue(animator.isFinished)
    }

    @Test
    fun nothingMovesWhenNoTimePasses() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 0)
        val display = BoardDisplayState()
        display.reset(start)
        val animator = SowAnimator(display, result.events, timingOf(SeedSpeed.NORMAL), SilentSounds)
        animator.advance(0.0f)
        animator.advance(0.0f)
        assertFalse(animator.isFinished)
        // The seeds of pit 0 are lifted at once, but not one has landed yet.
        assertEquals(listOf(0, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4), display.pits.toList())
        assertTrue(display.handActive)
        assertEquals(4, display.handCount)
    }

    @Test
    fun jumpingToTheEndPlaysNoSoundAndEndsOnTheEngineState() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 0)
        val display = BoardDisplayState()
        display.reset(start)
        val sounds = CountingSounds()
        val animator = SowAnimator(display, result.events, timingOf(SeedSpeed.NORMAL), sounds)
        animator.advance(0.5f)
        val soundsBefore = sounds.total
        animator.finishInstantly()
        assertTrue(animator.isFinished)
        assertEquals(soundsBefore, sounds.total)
        assertTrue(display.countsMatch(result.newState))
        assertTrue(display.isSettled)
    }

    @Test
    fun jumpingToTheEndEverywhereInTheTurnIsAlwaysConsistent() {
        val start = GameState.initial(Player.ONE)
        val result = engine.playTurn(start, 0)
        var jumpAt = 0f
        while (jumpAt < 6f) {
            val display = BoardDisplayState()
            display.reset(start)
            val sounds = CountingSounds()
            val animator = SowAnimator(display, result.events, timingOf(SeedSpeed.NORMAL), sounds)
            animator.advance(jumpAt)
            val soundsBefore = sounds.total
            animator.finishInstantly()
            assertEquals(soundsBefore, sounds.total)
            assertTrue("Jump at $jumpAt", display.countsMatch(result.newState))
            assertTrue(display.isSettled)
            jumpAt += 0.13f
        }
    }

    @Test
    fun theSafetyNetEventPutsTheSeedsInHandBackOnTheBoard() {
        val start = GameState.initial(Player.ONE)
        val events = listOf<SowEvent>(
            SowEvent.PickUp(0, 4),
            SowEvent.Drop(1, 5, 3),
            SowEvent.StepLimitReached,
        )
        val display = BoardDisplayState()
        display.reset(start)
        runToEnd(SowAnimator(display, events, timingOf(SeedSpeed.FAST), SilentSounds), 0.05f)
        assertEquals(listOf(0, 8, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4), display.pits.toList())
        assertEquals(48, seedsShown(display))
        assertTrue(display.isSettled)
    }

    @Test
    fun aSkippedTurnShowsTheBannerForThePlayerAndThenClearsIt() {
        val start = GameState.initial(Player.ONE)
        val events = listOf<SowEvent>(SowEvent.TurnSkipped(Player.TWO))
        val display = BoardDisplayState()
        display.reset(start)
        val sounds = CountingSounds()
        val animator = SowAnimator(display, events, timingOf(SeedSpeed.NORMAL), sounds)
        animator.advance(0.5f)
        assertEquals(Player.TWO, display.bannerPlayer)
        assertEquals(1, sounds.skips)
        animator.advance(1.1f)
        assertNull(display.bannerPlayer)
        assertTrue(animator.isFinished)
    }

    @Test
    fun theDisplayEqualsTheEngineAfterEveryTurnOfTwoHundredRandomGames() {
        val steps = floatArrayOf(0.05f, 0.2f, 0.5f, 0.016f)
        for (game in 0 until 200) {
            val random = Random(game)
            var state = GameState.initial(if (game % 2 == 0) Player.ONE else Player.TWO)
            val display = BoardDisplayState()
            display.reset(state)
            val speed = SeedSpeed.entries[game % SeedSpeed.entries.size]
            // The slowest step (0.016 s) is used on a few games only, to keep the test quick.
            val step = if (game % 25 == 3) steps[3] else steps[game % 3]
            var turns = 0
            var sawGameOver = false
            while (state.status == GameStatus.IN_PROGRESS) {
                val pit = randomLegalMove(state, random)
                val result = engine.playTurn(state, pit)
                val sounds = CountingSounds()
                val animator = SowAnimator(display, result.events, timingOf(speed), sounds)
                runToEnd(animator, step) {
                    val leftover = if (animator.gameOverReached) result.newState.leftoverInHand else 0
                    assertEquals(
                        "Game $game turn $turns: seeds shown",
                        GameState.TOTAL_SEEDS - leftover,
                        seedsShown(display),
                    )
                }
                assertTrue("Game $game turn $turns: counts", display.countsMatch(result.newState))
                assertTrue("Game $game turn $turns: settled", display.isSettled)
                assertEquals(result.events.count { it is SowEvent.Drop }, sounds.drops)
                assertEquals(result.events.count { it is SowEvent.PickUp }, sounds.pickUps)
                assertEquals(result.events.count { it is SowEvent.Capture }, sounds.captures)
                if (result.newState.status == GameStatus.FINISHED) {
                    assertTrue(animator.gameOverReached)
                    sawGameOver = true
                }
                state = result.newState
                turns += 1
                assertTrue("Game $game never ended", turns < 20_000)
            }
            assertTrue("Game $game must end with a game-over event", sawGameOver)
        }
    }
}
