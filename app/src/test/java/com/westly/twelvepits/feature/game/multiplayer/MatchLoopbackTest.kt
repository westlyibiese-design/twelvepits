package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import com.westly.twelvepits.feature.game.ui.BoardMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchLoopbackTest {

    // ---- Whole games ------------------------------------------------------------------------

    @Test
    fun threeHundredGamesEndWithTheSamePositionOnBothPhones() {
        for (seed in 1..300) {
            val starter = if (seed % 2 == 0) Player.TWO else Player.ONE
            val h = MatchHarness(starter)
            h.playToTheEnd(Random(seed))
            assertEquals("Seed $seed", GameStatus.FINISHED, h.host.state.status)
            assertEquals("Seed $seed", h.host.state, h.guest.state)
            assertEquals("Seed $seed", starter, h.host.state.startingPlayer)
            assertEquals("Seed $seed", h.host.state.moveNumber, h.hostItems.size)
            assertEquals("Seed $seed", h.hostItems.size, h.guestItems.size)
            assertNull("Seed $seed", h.host.endReason)
            assertNull("Seed $seed", h.guest.endReason)
            // Both phones were given the same turns in the same order.
            for (index in h.hostItems.indices) {
                val a = h.hostItems[index] as MatchItem.Turn
                val b = h.guestItems[index] as MatchItem.Turn
                assertEquals("Seed $seed turn $index", a.mover, b.mover)
                assertEquals("Seed $seed turn $index", a.result.newState, b.result.newState)
                assertEquals("Seed $seed turn $index", a.result.events, b.result.events)
            }
        }
    }

    @Test
    fun bothPhonesAgreeWhoWon() {
        for (seed in 1..40) {
            val h = MatchHarness(Player.ONE)
            h.playToTheEnd(Random(seed))
            assertEquals(h.host.state.winner, h.guest.state.winner)
            assertNotNull(h.host.state.winner)
        }
    }

    @Test
    fun theGuestPlaysFromTheBottomAndEveryTapGoesToThePitOfTheGuest() {
        val h = MatchHarness(Player.TWO)
        val mapper = BoardMapper(h.guest.role)
        assertEquals(Player.TWO, h.guest.role)
        for (slot in 0 until GameState.PITS_PER_SIDE) {
            assertEquals(Player.TWO, GameState.ownerOfPit(mapper.pitOfSlot(slot)))
        }
        // The bottom-left pit of the guest's screen is the first pit of Player TWO.
        assertEquals(6, mapper.pitOfSlot(0))
        assertTrue(h.guest.localMove(mapper.pitOfSlot(0)))
        h.link.pump()
        assertEquals(1, h.host.state.moveNumber)
        assertEquals(h.host.state, h.guest.state)
    }

    // ---- Taps -------------------------------------------------------------------------------

    @Test
    fun tapsOutOfTurnAreIgnored() {
        val h = MatchHarness(Player.ONE)
        assertFalse(h.guest.canLocalMove)
        assertFalse(h.guest.localMove(8))
        h.link.pump()
        assertEquals(0, h.host.state.moveNumber)
        assertEquals(0, h.link.sentByGuest.size)
    }

    @Test
    fun theGuestCannotTapAgainWhileItsMoveIsOnItsWay() {
        val h = MatchHarness(Player.TWO)
        assertTrue(h.guest.localMove(6))
        assertFalse(h.guest.canLocalMove)
        assertFalse(h.guest.localMove(7))
        h.link.pump()
        assertEquals(1, h.host.state.moveNumber)
        assertEquals(1, h.guest.state.moveNumber)
    }

    @Test
    fun anOpponentPitAndAnEmptyPitDoNotSendAnything() {
        val h = MatchHarness(Player.TWO)
        assertFalse(h.guest.localMove(0))
        assertFalse(h.guest.localMove(12))
        assertFalse(h.guest.localMove(-1))
        assertEquals(0, h.link.sentByGuest.size)
    }

    // ---- Lost link, leaving, silence --------------------------------------------------------

    @Test
    fun cuttingTheLinkAtAnyMomentEndsBothPhonesCleanly() {
        for (seed in 1..60) {
            val random = Random(seed)
            val h = MatchHarness(Player.ONE)
            val cutAfter = random.nextInt(0, 30)
            var played = 0
            while (played < cutAfter && h.host.state.status == GameStatus.IN_PROGRESS) {
                h.playRandomMove(random)
                played += 1
            }
            h.link.breakLink()
            h.link.pump()
            assertEquals("Seed $seed", EndReason.CONNECTION_LOST, h.host.endReason)
            assertEquals("Seed $seed", EndReason.CONNECTION_LOST, h.guest.endReason)
            assertFalse(h.host.canLocalMove)
            assertFalse(h.guest.canLocalMove)
            assertFalse(h.host.localMove(0))
            assertFalse(h.guest.localMove(6))
            h.host.tick()
            h.guest.tick()
        }
    }

    @Test
    fun leavingTellsTheOtherPlayerOnce() {
        val h = MatchHarness(Player.ONE)
        h.guest.leave()
        h.guest.leave()
        h.link.pump()
        assertEquals(EndReason.LEFT_BY_ME, h.guest.endReason)
        assertEquals(EndReason.PEER_LEFT, h.host.endReason)
        val byes = h.link.sentByGuest.count {
            val decoded = ProtocolCodec.decode(it)
            decoded is DecodeResult.Ok && decoded.message is PhonesMessage.Bye
        }
        assertEquals(1, byes)
    }

    @Test
    fun theHostLeavingEndsTheGuestToo() {
        val h = MatchHarness(Player.ONE)
        h.host.leave()
        h.link.pump()
        assertEquals(EndReason.LEFT_BY_ME, h.host.endReason)
        assertEquals(EndReason.PEER_LEFT, h.guest.endReason)
    }

    @Test
    fun aQuietLinkKeepsBothPhonesAliveWithPings() {
        val h = MatchHarness(Player.ONE)
        repeat(120) {
            h.now += 1_000
            h.host.tick()
            h.guest.tick()
            h.link.pump()
        }
        assertNull(h.host.endReason)
        assertNull(h.guest.endReason)
        val pings = h.link.sentByHost.count {
            val decoded = ProtocolCodec.decode(it)
            decoded is DecodeResult.Ok && decoded.message is PhonesMessage.Ping
        }
        assertTrue("Pings: $pings", pings in 20..30)
    }

    @Test
    fun noMessageForFifteenSecondsEndsTheMatch() {
        val h = MatchHarness(Player.ONE)
        h.link.muted = true
        h.now = 14_999
        h.host.tick()
        assertNull(h.host.endReason)
        h.now = 15_000
        h.host.tick()
        assertEquals(EndReason.CONNECTION_LOST, h.host.endReason)
    }

    @Test
    fun aMessageFromTheOtherPhoneRestartsTheSilentTime() {
        val h = MatchHarness(Player.ONE)
        h.now = 10_000
        h.guest.tick()
        h.link.pump()
        h.now = 20_000
        h.host.tick()
        assertNull(h.host.endReason)
    }

    // ---- Bad messages -----------------------------------------------------------------------

    @Test
    fun brokenOversizedAndUnknownMessagesChangeNothing() {
        val h = MatchHarness(Player.ONE)
        h.host.localMove(1)
        h.link.pump()
        val hostBefore = h.host.state
        val guestBefore = h.guest.state
        val junk = listOf(
            ByteArray(0),
            "not json".toByteArray(),
            ByteArray(MAX_MESSAGE_BYTES + 10) { 'x'.code.toByte() },
            """{"v":1,"type":"Dance"}""".toByteArray(),
            """{"v":1,"type":"Move","gameId":"${h.host.gameId}","seq":3}""".toByteArray(),
        )
        for (bytes in junk) {
            h.host.onMessage(bytes)
            h.guest.onMessage(bytes)
        }
        assertEquals(hostBefore, h.host.state)
        assertEquals(guestBefore, h.guest.state)
        assertNull(h.host.endReason)
        assertNull(h.guest.endReason)
    }

    @Test
    fun aRepeatedTurnIsIgnored() {
        val h = MatchHarness(Player.ONE)
        h.host.localMove(1)
        h.link.pump()
        val applied = h.link.sentByHost.first {
            val decoded = ProtocolCodec.decode(it)
            decoded is DecodeResult.Ok && decoded.message is PhonesMessage.TurnApplied
        }
        val before = h.guest.state
        val itemsBefore = h.guestItems.size
        h.guest.onMessage(applied)
        assertEquals(before, h.guest.state)
        assertEquals(itemsBefore, h.guestItems.size)
        assertNull(h.guest.endReason)
    }

    @Test
    fun aTurnFromAnotherGameIsIgnored() {
        val h = MatchHarness(Player.ONE)
        val before = h.guest.state
        h.guest.onMessage(ProtocolCodec.encode(PhonesMessage.TurnApplied("elsewhere", 1, 1, Player.ONE, 5)))
        assertEquals(before, h.guest.state)
        assertNull(h.guest.endReason)
    }

    @Test
    fun aDifferentProtocolVersionMidGameEndsTheMatch() {
        val h = MatchHarness(Player.ONE)
        h.guest.onMessage("""{"v":9,"type":"Ping"}""".toByteArray())
        assertEquals(EndReason.VERSION_MISMATCH, h.guest.endReason)
    }

    // ---- Out of sync ------------------------------------------------------------------------

    private fun tamperTurnHashes(h: MatchHarness, tamperResync: Boolean = false, onlyFirst: Boolean = false) {
        var done = false
        h.link.tamperHostToGuest = { bytes ->
            val decoded = ProtocolCodec.decode(bytes)
            val message = (decoded as? DecodeResult.Ok)?.message
            if (message is PhonesMessage.TurnApplied && !(onlyFirst && done)) {
                done = true
                ProtocolCodec.encode(
                    PhonesMessage.TurnApplied(message.gameId, message.seq, message.pit, message.mover, message.stateHash + 1),
                )
            } else if (tamperResync && message is PhonesMessage.Resync) {
                ProtocolCodec.encode(PhonesMessage.Resync(message.gameId, message.state, message.stateHash + 1))
            } else {
                bytes
            }
        }
    }

    @Test
    fun oneBadHashIsRepairedByOneResync() {
        val h = MatchHarness(Player.ONE)
        tamperTurnHashes(h, onlyFirst = true)
        assertTrue(h.host.localMove(1))
        h.link.pump()
        assertEquals(h.host.state, h.guest.state)
        assertNull(h.host.endReason)
        assertNull(h.guest.endReason)
        assertTrue(h.guestItems.last() is MatchItem.Replace)
        // The game goes on normally afterwards.
        val random = Random(3)
        repeat(6) { if (h.host.state.status == GameStatus.IN_PROGRESS) h.playRandomMove(random) }
        assertEquals(h.host.state, h.guest.state)
        assertNull(h.guest.endReason)
    }

    @Test
    fun aSecondMismatchEndsTheGameForBothPlayers() {
        val h = MatchHarness(Player.ONE)
        tamperTurnHashes(h)
        val random = Random(4)
        var guard = 0
        while (h.guest.endReason == null && guard < 50) {
            h.playRandomMove(random)
            guard += 1
        }
        assertEquals(EndReason.OUT_OF_SYNC, h.guest.endReason)
        assertEquals(EndReason.OUT_OF_SYNC, h.host.endReason)
    }

    @Test
    fun aResyncWithAWrongHashEndsTheGame() {
        val h = MatchHarness(Player.ONE)
        tamperTurnHashes(h, tamperResync = true)
        h.host.localMove(1)
        h.link.pump()
        assertEquals(EndReason.OUT_OF_SYNC, h.guest.endReason)
        assertEquals(EndReason.OUT_OF_SYNC, h.host.endReason)
    }

    @Test
    fun theHostSendsTheWholePositionOnlyOncePerGame() {
        val h = MatchHarness(Player.ONE)
        val request = ProtocolCodec.encode(PhonesMessage.ResyncRequest(h.host.gameId, 0))
        h.host.onMessage(request)
        h.host.onMessage(request)
        h.link.pump()
        val resyncs = h.link.sentByHost.count {
            val decoded = ProtocolCodec.decode(it)
            decoded is DecodeResult.Ok && decoded.message is PhonesMessage.Resync
        }
        assertEquals(1, resyncs)
    }

    // ---- Play again -------------------------------------------------------------------------

    private fun finishedMatch(seed: Int, starter: Player = Player.ONE): MatchHarness {
        val h = MatchHarness(starter)
        h.playToTheEnd(Random(seed))
        return h
    }

    private fun assertNewGameStarted(h: MatchHarness, previousStarter: Player, expectedNumber: Int) {
        assertEquals(expectedNumber, h.host.gameNumber)
        assertEquals(expectedNumber, h.guest.gameNumber)
        assertEquals(h.host.gameId, h.guest.gameId)
        assertEquals(previousStarter.opponent, h.host.initialState?.startingPlayer)
        assertEquals(h.host.initialState, h.guest.initialState)
        assertEquals(RematchState.NONE, h.host.rematchState)
        assertEquals(RematchState.NONE, h.guest.rematchState)
        assertEquals(GameStatus.IN_PROGRESS, h.host.state.status)
        assertEquals(h.host.state, h.guest.state)
    }

    @Test
    fun theGuestAsksAndTheHostAccepts() {
        val h = finishedMatch(11)
        val firstId = h.host.gameId
        h.guest.requestRematch()
        h.link.pump()
        assertEquals(RematchState.MINE_PENDING, h.guest.rematchState)
        assertEquals(RematchState.THEIRS_PENDING, h.host.rematchState)
        h.host.acceptRematch()
        h.link.pump()
        assertNewGameStarted(h, Player.ONE, 2)
        assertNotEquals(firstId, h.host.gameId)
    }

    @Test
    fun theHostAsksAndTheGuestAccepts() {
        val h = finishedMatch(12)
        h.host.requestRematch()
        h.link.pump()
        assertEquals(RematchState.THEIRS_PENDING, h.guest.rematchState)
        h.guest.acceptRematch()
        h.link.pump()
        assertNewGameStarted(h, Player.ONE, 2)
    }

    @Test
    fun theStarterAlternatesGameAfterGame() {
        var h = finishedMatch(13, Player.TWO)
        h.guest.requestRematch()
        h.link.pump()
        h.host.acceptRematch()
        h.link.pump()
        assertEquals(Player.ONE, h.host.initialState?.startingPlayer)
        h.playToTheEnd(Random(14))
        h.host.requestRematch()
        h.link.pump()
        h.guest.acceptRematch()
        h.link.pump()
        assertEquals(Player.TWO, h.host.initialState?.startingPlayer)
        assertEquals(3, h.host.gameNumber)
        assertEquals(3, h.guest.gameNumber)
    }

    @Test
    fun theOtherPlayerSaysNo() {
        val h = finishedMatch(15)
        h.guest.requestRematch()
        h.link.pump()
        h.host.declineRematch()
        h.link.pump()
        assertEquals(RematchState.DECLINED, h.guest.rematchState)
        assertEquals(RematchState.NONE, h.host.rematchState)
        assertEquals(1, h.host.gameNumber)
        h.guest.dismissDeclined()
        assertEquals(RematchState.NONE, h.guest.rematchState)
        // Asking again is possible.
        h.guest.requestRematch()
        h.link.pump()
        assertEquals(RematchState.THEIRS_PENDING, h.host.rematchState)
    }

    @Test
    fun takingBackTheRequestClosesTheQuestionOnTheOtherPhone() {
        val h = finishedMatch(16)
        h.guest.requestRematch()
        h.link.pump()
        h.guest.cancelRematchRequest()
        h.link.pump()
        assertEquals(RematchState.NONE, h.guest.rematchState)
        assertEquals(RematchState.NONE, h.host.rematchState)
    }

    @Test
    fun bothAskingAtTheSameTimeStartsTheGame() {
        val h = finishedMatch(17)
        h.guest.requestRematch()
        h.host.requestRematch()
        h.link.pump()
        assertNewGameStarted(h, Player.ONE, 2)
    }

    @Test
    fun theOtherPlayerAskingFirstMakesMyAskAnAcceptance() {
        val h = finishedMatch(18)
        h.host.requestRematch()
        h.link.pump()
        h.guest.requestRematch()
        h.link.pump()
        assertNewGameStarted(h, Player.ONE, 2)
    }

    @Test
    fun noRematchBeforeTheGameIsOver() {
        val h = MatchHarness(Player.ONE)
        h.guest.requestRematch()
        h.host.requestRematch()
        h.link.pump()
        assertEquals(RematchState.NONE, h.guest.rematchState)
        assertEquals(RematchState.NONE, h.host.rematchState)
        assertEquals(1, h.host.gameNumber)
    }

    @Test
    fun aMoveOfTheOldGameIsRejectedAfterARematch() {
        val h = finishedMatch(19)
        val oldId = h.host.gameId
        h.guest.requestRematch()
        h.link.pump()
        h.host.acceptRematch()
        h.link.pump()
        assertEquals(
            RejectReason.WRONG_GAME,
            h.host.validateMove(h.host.state.currentPlayer, oldId, 1, h.host.state.legalMoves().first()),
        )
        // The new game plays normally to the end.
        h.playToTheEnd(Random(20))
        assertEquals(GameStatus.FINISHED, h.host.state.status)
        assertEquals(h.host.state, h.guest.state)
    }

    @Test
    fun noRematchAfterTheOtherPlayerLeft() {
        val h = finishedMatch(21)
        h.host.leave()
        h.link.pump()
        h.guest.requestRematch()
        assertEquals(RematchState.NONE, h.guest.rematchState)
        assertEquals(EndReason.PEER_LEFT, h.guest.endReason)
    }

    @Test
    fun aSnapshotIsPublishedAfterEveryChange() {
        val h = MatchHarness(Player.ONE)
        assertEquals(1, h.host.snapshot.value.gameNumber)
        assertEquals(1, h.guest.snapshot.value.gameNumber)
        h.playToTheEnd(Random(22))
        h.guest.requestRematch()
        h.link.pump()
        assertEquals(RematchState.MINE_PENDING, h.guest.snapshot.value.rematch)
        assertEquals(RematchState.THEIRS_PENDING, h.host.snapshot.value.rematch)
        h.host.leave()
        h.link.pump()
        assertEquals(EndReason.LEFT_BY_ME, h.host.snapshot.value.endReason)
        assertEquals(EndReason.PEER_LEFT, h.guest.snapshot.value.endReason)
    }

    @Test
    fun aScreenThatOpensLateStillGetsEveryTurnFromTheFirst() {
        val h = MatchHarness(Player.ONE)
        val random = Random(23)
        repeat(5) { h.playRandomMove(random) }
        val late = ArrayList<MatchItem>()
        h.guest.attachTurnSink { late.add(it) }
        assertEquals(5, late.size)
        h.playRandomMove(random)
        assertEquals(6, late.size)
        val engine = SowingEngine()
        var state = h.guest.initialState ?: error("No game")
        for (item in late) {
            val turn = item as MatchItem.Turn
            state = engine.playTurn(state, turn.result.events.filterIsInstance<com.westly.twelvepits.feature.game.engine.SowEvent.PickUp>().first().pit).newState
        }
        assertEquals(h.guest.state, state)
    }
}
