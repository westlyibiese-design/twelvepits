package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HostValidationTest {

    @Test
    fun aGoodMoveIsAccepted() {
        val h = MatchHarness(Player.ONE)
        val gameId = h.host.gameId
        assertNull(h.host.validateMove(Player.ONE, gameId, 1, 2))
    }

    @Test
    fun aMoveOfAnotherGameIsRejected() {
        val h = MatchHarness(Player.ONE)
        assertEquals(RejectReason.WRONG_GAME, h.host.validateMove(Player.ONE, "other", 1, 2))
    }

    @Test
    fun aRepeatedOrSkippedSequenceNumberIsRejected() {
        val h = MatchHarness(Player.ONE)
        val gameId = h.host.gameId
        assertEquals(RejectReason.WRONG_SEQUENCE, h.host.validateMove(Player.ONE, gameId, 0, 2))
        assertEquals(RejectReason.WRONG_SEQUENCE, h.host.validateMove(Player.ONE, gameId, 2, 2))
        assertTrue(h.host.localMove(2))
        h.link.pump()
        // The same move again, now with the old number.
        assertEquals(RejectReason.WRONG_SEQUENCE, h.host.validateMove(Player.ONE, gameId, 1, 2))
    }

    @Test
    fun aMoveOutOfTurnIsRejected() {
        val h = MatchHarness(Player.ONE)
        assertEquals(RejectReason.NOT_YOUR_TURN, h.host.validateMove(Player.TWO, h.host.gameId, 1, 8))
    }

    @Test
    fun anOpponentPitAnEmptyPitAndAPitOutOfRangeAreRejected() {
        val h = MatchHarness(Player.ONE)
        val gameId = h.host.gameId
        assertEquals(RejectReason.ILLEGAL_PIT, h.host.validateMove(Player.ONE, gameId, 1, 8))
        assertEquals(RejectReason.ILLEGAL_PIT, h.host.validateMove(Player.ONE, gameId, 1, 12))
        assertEquals(RejectReason.ILLEGAL_PIT, h.host.validateMove(Player.ONE, gameId, 1, -1))
        assertTrue(h.host.localMove(0))
        h.link.pump()
        // Pit 0 is empty now, whoever plays next.
        val next = h.host.state.currentPlayer
        if (next == Player.ONE) {
            assertEquals(RejectReason.ILLEGAL_PIT, h.host.validateMove(Player.ONE, gameId, 2, 0))
        }
    }

    @Test
    fun noMoveIsAcceptedAfterTheGameIsOver() {
        val h = MatchHarness(Player.ONE)
        h.playToTheEnd(Random(5))
        assertEquals(GameStatus.FINISHED, h.host.state.status)
        assertEquals(
            RejectReason.GAME_OVER,
            h.host.validateMove(h.host.state.currentPlayer, h.host.gameId, h.host.state.moveNumber + 1, 0),
        )
        assertTrue(!h.host.canLocalMove)
        assertTrue(!h.guest.canLocalMove)
    }

    @Test
    fun aRejectedMoveChangesNothingAndTheGuestIsTold() {
        val h = MatchHarness(Player.ONE)
        val before = h.host.state
        // The guest plays out of turn (it is the host's turn): raw message, as a broken or hostile phone would send.
        h.link.guest.send(ProtocolCodec.encode(PhonesMessage.Move(h.host.gameId, 1, 8)))
        h.link.pump()
        assertEquals(before, h.host.state)
        assertEquals(before, h.guest.state)
        val last = ProtocolCodec.decode(h.link.sentByHost.last()) as DecodeResult.Ok
        val rejected = last.message as PhonesMessage.Rejected
        assertEquals("NOT_YOUR_TURN", rejected.reason)
        assertNull(h.host.endReason)
        assertNull(h.guest.endReason)
    }
}
