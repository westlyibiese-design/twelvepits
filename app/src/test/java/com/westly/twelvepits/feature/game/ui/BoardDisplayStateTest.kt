package com.westly.twelvepits.feature.game.ui

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardDisplayStateTest {
    @Test
    fun resetCopiesTheEngineStateAndClearsEverythingMoving() {
        val display = BoardDisplayState()
        display.handActive = true
        display.bannerPlayer = Player.ONE
        display.startShake(3)
        display.addFloater(Player.TWO)
        val state = GameState.initial(Player.TWO)
        display.reset(state)
        assertTrue(display.countsMatch(state))
        assertTrue(display.isSettled)
        assertFalse(display.hasEffects)
        assertEquals(4, display.pits[7])
    }

    @Test
    fun countsMatchNoticesAWrongPitOrStore() {
        val state = GameState.initial(Player.ONE)
        val display = BoardDisplayState()
        display.reset(state)
        display.pits[5] = 3
        assertFalse(display.countsMatch(state))
        display.pits[5] = 4
        display.stores[Player.TWO.ordinal] = 4
        assertFalse(display.countsMatch(state))
    }

    @Test
    fun effectsEndOnTheirOwn() {
        val display = BoardDisplayState()
        val timing = AnimationTiming.NORMAL
        display.addFloater(Player.ONE)
        display.startShake(2)
        assertTrue(display.hasEffects)
        display.tickEffects(0.2f, timing)
        assertTrue(display.hasEffects)
        display.tickEffects(1.5f, timing)
        assertFalse(display.hasEffects)
    }
}
