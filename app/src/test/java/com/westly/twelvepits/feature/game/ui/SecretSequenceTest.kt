package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.ai.ComputerPlayer
import com.westly.twelvepits.feature.game.ai.HardPlayer
import com.westly.twelvepits.feature.game.ai.NoLegalMoveException
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The hidden tap code, where the taps are on the real board, and the best-move hint. */
class SecretSequenceTest {
    private var clock = 0L
    private val sequence = SecretSequence(now = { clock })

    private val first = SecretTarget.TOP_SECOND
    private val second = SecretTarget.LEFT_STORE
    private val third = SecretTarget.RIGHT_STORE
    private val fourth = SecretTarget.TOP_FIFTH

    private fun tap(target: SecretTarget?, after: Long = 100L): Boolean {
        clock += after
        return sequence.onTap(target)
    }

    // ---- The code ----

    @Test
    fun theRightFourTapsInTimeActivateIt() {
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(third))
        assertTrue(tap(fourth))
    }

    @Test
    fun onlyThreeCorrectTapsDoNotActivateIt() {
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(third))
    }

    @Test
    fun theWrongOrderNeverActivatesIt() {
        val orders = listOf(
            listOf(second, first, third, fourth),
            listOf(first, third, second, fourth),
            listOf(fourth, third, second, first),
            listOf(first, second, fourth, third),
        )
        for (order in orders) {
            sequence.reset()
            var activated = false
            for (target in order) activated = tap(target) || activated
            assertFalse(order.toString(), activated)
        }
    }

    @Test
    fun aWrongTapResetsAndTheCodeMustStartAgainFromTheFirstPlace() {
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(null)) // somewhere else
        assertFalse(tap(third))
        assertFalse(tap(fourth))
        // Starting again from the first place works.
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(third))
        assertTrue(tap(fourth))
    }

    @Test
    fun aWrongPlaceInTheMiddleResets() {
        assertFalse(tap(first))
        assertFalse(tap(third)) // skipped the second place
        assertFalse(tap(fourth))
    }

    @Test
    fun tappingTheFirstPlaceTwiceIsJustANewStart() {
        assertFalse(tap(first))
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(third))
        assertTrue(tap(fourth))
    }

    @Test
    fun repeatedTapsOnOneNeverActivate() {
        repeat(20) { assertFalse(tap(first, after = 50L)) }
        repeat(20) { assertFalse(tap(second, after = 50L)) }
    }

    @Test
    fun takingMoreThanThreeSecondsResets() {
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(third))
        // The fourth tap comes more than 3 seconds after the first.
        assertFalse(tap(fourth, after = 3_000L))
    }

    @Test
    fun exactlyThreeSecondsStillCounts() {
        assertFalse(tap(first, after = 0L))
        assertFalse(tap(second, after = 1_000L))
        assertFalse(tap(third, after = 1_000L))
        assertTrue(tap(fourth, after = 1_000L))
    }

    @Test
    fun aSlowStartCanBeBeginAgainWithTheFirstPlace() {
        assertFalse(tap(first))
        assertFalse(tap(second, after = 5_000L)) // too late: forgotten
        assertFalse(tap(first, after = 5_000L))
        assertFalse(tap(second))
        assertFalse(tap(third))
        assertTrue(tap(fourth))
    }

    @Test
    fun resetForgetsEverythingEnteredSoFar() {
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(third))
        sequence.reset()
        assertFalse(tap(fourth))
    }

    @Test
    fun afterActivatingTheCodeStartsOver() {
        tap(first); tap(second); tap(third)
        assertTrue(tap(fourth))
        assertFalse(tap(fourth))
        assertFalse(tap(first))
        assertFalse(tap(second))
        assertFalse(tap(third))
        assertTrue(tap(fourth))
    }

    // ---- Where the taps are on the real board ----

    private val geometry = BoardGeometry.parse(File("src/main/assets/board_geometry.json").readText())
    private val board = BoardRect(0f, 0f, 2400f, 2400f / geometry.aspectRatio)
    private val minRadius = 24f

    private fun targetAt(fractionX: Float, fractionY: Float): SecretTarget? {
        val x = board.x(fractionX)
        val y = board.y(fractionY)
        val slot = PitHitTester.hitSlot(x, y, board, geometry, minRadius)
        val store = if (slot < 0) PitHitTester.hitStore(x, y, board, geometry) else null
        return SecretTargets.of(slot, store)
    }

    @Test
    fun theTwoTopPitsAreTheSecondAndFifthFromTheLeft() {
        val topSlotsLeftToRight = (6..11).sortedBy { geometry.slotCx(it) }
        assertEquals(SecretTargets.TOP_SECOND_SLOT, topSlotsLeftToRight[1])
        assertEquals(SecretTargets.TOP_FIFTH_SLOT, topSlotsLeftToRight[4])
    }

    @Test
    fun theTapsAtTheCentresOfTheFourPlacesAreRecognised() {
        assertEquals(first, targetAt(geometry.slotCx(SecretTargets.TOP_SECOND_SLOT), geometry.slotCy(SecretTargets.TOP_SECOND_SLOT)))
        assertEquals(second, targetAt(geometry.storeCx(StoreSide.LEFT), geometry.storeCy(StoreSide.LEFT)))
        assertEquals(third, targetAt(geometry.storeCx(StoreSide.RIGHT), geometry.storeCy(StoreSide.RIGHT)))
        assertEquals(fourth, targetAt(geometry.slotCx(SecretTargets.TOP_FIFTH_SLOT), geometry.slotCy(SecretTargets.TOP_FIFTH_SLOT)))
    }

    @Test
    fun otherPitsAreNotPartOfTheCode() {
        for (slot in 0 until BoardGeometry.SLOT_COUNT) {
            if (slot == SecretTargets.TOP_SECOND_SLOT || slot == SecretTargets.TOP_FIFTH_SLOT) continue
            assertNull("slot $slot", targetAt(geometry.slotCx(slot), geometry.slotCy(slot)))
        }
    }

    @Test
    fun aStoreIsSeenByTheCodeButIsNeverAPit() {
        for (side in StoreSide.entries) {
            val x = board.x(geometry.storeCx(side))
            val y = board.y(geometry.storeCy(side))
            assertEquals(-1, PitHitTester.hitSlot(x, y, board, geometry, minRadius))
            assertEquals(side, PitHitTester.hitStore(x, y, board, geometry))
        }
    }

    @Test
    fun aPitIsNotAStore() {
        for (slot in 0 until BoardGeometry.SLOT_COUNT) {
            val x = board.x(geometry.slotCx(slot))
            val y = board.y(geometry.slotCy(slot))
            assertNull("slot $slot", PitHitTester.hitStore(x, y, board, geometry))
        }
    }

    @Test
    fun theCodeWorksOnOtherScreenSizesBecauseItUsesTheBoardNotPixels() {
        for (width in listOf(1280f, 1920f, 2340f, 2520f)) {
            val scaled = BoardRect(40f, 25f, width, width / geometry.aspectRatio)
            val x = scaled.x(geometry.storeCx(StoreSide.LEFT))
            val y = scaled.y(geometry.storeCy(StoreSide.LEFT))
            assertEquals(StoreSide.LEFT, PitHitTester.hitStore(x, y, scaled, geometry))
        }
    }

    // ---- The best move ----

    private val engine = SowingEngine()
    private fun quick() = HardPlayer(maxDepth = 2, timeBudgetMillis = 50L)

    @Test
    fun theHintIsALegalPitOfThePlayerToMoveInTheActualPosition() = runBlocking {
        var state = GameState.initial(Player.ONE)
        var turns = 0
        while (state.status == GameStatus.IN_PROGRESS && turns < 12) {
            val pit = CheatHint.recommend(state, quick())
            assertNotNull(pit)
            assertTrue(pit in state.legalMoves())
            assertEquals(state.currentPlayer, state.ownerOf(pit!!))
            assertTrue(state.pits[pit] > 0)
            state = engine.playTurn(state, pit).newState
            turns += 1
        }
    }

    @Test
    fun theHintFollowsTheBoardAndNotAFixedPit() = runBlocking {
        // Only one pit can be played: that pit is the answer, whatever the opening looked like.
        val base = GameState.initial(Player.ONE)
        val pits = MutableList(GameState.PIT_COUNT) { 0 }
        pits[3] = 4
        pits[9] = 2
        val state = base.copy(pits = pits, storeOne = 20, storeTwo = 16, capturedTotal = 36, moveNumber = 7)
        assertEquals(3, CheatHint.recommend(state, quick()))
    }

    @Test
    fun noLegalMoveGivesNoHintAndNoCrash() = runBlocking {
        val base = GameState.initial(Player.ONE)
        val empty = base.copy(pits = List(GameState.PIT_COUNT) { 0 })
        assertNull(CheatHint.recommend(empty, quick()))
        val finished = base.copy(status = GameStatus.FINISHED)
        assertNull(CheatHint.recommend(finished, quick()))
        val refuses = object : ComputerPlayer {
            override suspend fun choosePit(state: GameState): Int = throw NoLegalMoveException("none")
        }
        assertNull(CheatHint.recommend(base, refuses))
    }

    @Test
    fun aPitThatIsNotLegalIsNeverRecommended() = runBlocking {
        val wrongSide = object : ComputerPlayer {
            override suspend fun choosePit(state: GameState): Int = 11
        }
        assertNull(CheatHint.recommend(GameState.initial(Player.ONE), wrongSide))
    }
}
