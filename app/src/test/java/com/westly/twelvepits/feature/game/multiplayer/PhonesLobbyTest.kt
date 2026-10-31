package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonesLobbyTest {

    private class TwoLobbies {
        val link = FakeLink()
        var now = 0L
        private var counter = 0
        val hostLobby = PhonesLobby(link.host, { now }, 7, { "game" + (++counter) })
        val guestLobby = PhonesLobby(link.guest, { now }, 7, { "unused" })
    }

    private val hostMe = RemotePlayer("Ada", AvatarId.KING)
    private val guestMe = RemotePlayer("Bola", AvatarId.KING)

    /** Both phones are at the code question. */
    private fun upToTheCode(p: TwoLobbies, starter: Player = Player.ONE) {
        p.hostLobby.host(hostMe, starter)
        p.guestLobby.join(guestMe)
        p.link.pump()
        p.guestLobby.choose(p.guestLobby.state.value.hosts.first())
        p.link.pump()
    }

    @Test
    fun theWholeWayFromHostAndJoinToTheFirstGame() {
        val p = TwoLobbies()
        p.hostLobby.host(hostMe, Player.ONE)
        assertEquals(LobbyStage.HOST_WAITING, p.hostLobby.state.value.stage)
        p.guestLobby.join(guestMe)
        assertEquals(LobbyStage.JOIN_SEARCHING, p.guestLobby.state.value.stage)
        p.link.pump()

        val hosts = p.guestLobby.state.value.hosts
        assertEquals(1, hosts.size)
        assertEquals("Ada", hosts[0].player.name)
        assertEquals(AvatarId.KING, hosts[0].player.avatar)

        p.guestLobby.choose(hosts[0])
        assertEquals(LobbyStage.REQUESTING, p.guestLobby.state.value.stage)
        p.link.pump()
        assertEquals(LobbyStage.CONFIRM, p.hostLobby.state.value.stage)
        assertEquals(LobbyStage.CONFIRM, p.guestLobby.state.value.stage)
        assertEquals("4821", p.hostLobby.state.value.authCode)
        assertEquals(p.hostLobby.state.value.authCode, p.guestLobby.state.value.authCode)
        assertEquals("Bola", p.hostLobby.state.value.remote?.name)
        assertEquals("Ada", p.guestLobby.state.value.remote?.name)

        // The game starts only when both said yes.
        p.hostLobby.confirmYes()
        p.link.pump()
        assertEquals(LobbyStage.CONFIRM, p.hostLobby.state.value.stage)
        assertTrue(p.hostLobby.state.value.localAccepted)
        assertEquals(LobbyStage.CONFIRM, p.guestLobby.state.value.stage)
        assertNull(p.hostLobby.controller)
        p.guestLobby.confirmYes()
        p.link.pump()

        assertEquals(LobbyStage.READY, p.hostLobby.state.value.stage)
        assertEquals(LobbyStage.READY, p.guestLobby.state.value.stage)
        val host = p.hostLobby.controller ?: error("No host controller")
        val guest = p.guestLobby.controller ?: error("No guest controller")
        assertEquals(Player.ONE, host.role)
        assertEquals(Player.TWO, guest.role)
        assertEquals(host.config, guest.config)
        assertEquals(host.initialState, guest.initialState)
        assertEquals(host.gameId, guest.gameId)
        assertEquals("Ada", host.config?.player1Name)
        assertEquals("Bola", host.config?.player2Name)
    }

    @Test
    fun twoPlayersWithTheSameAvatarGetDifferentOnes() {
        val p = TwoLobbies()
        upToTheCode(p)
        p.hostLobby.confirmYes()
        p.guestLobby.confirmYes()
        p.link.pump()
        val config = p.hostLobby.controller?.config ?: error("No game")
        assertEquals(AvatarId.KING, config.player1Avatar)
        assertNotEquals(config.player1Avatar, config.player2Avatar)
        assertEquals(config, p.guestLobby.controller?.config)
    }

    @Test
    fun theHostsStarterRecordDecidesWhoStarts() {
        for (starter in Player.entries) {
            val p = TwoLobbies()
            upToTheCode(p, starter)
            p.hostLobby.confirmYes()
            p.guestLobby.confirmYes()
            p.link.pump()
            assertEquals(starter, p.hostLobby.controller?.initialState?.startingPlayer)
            assertEquals(starter, p.guestLobby.controller?.initialState?.startingPlayer)
        }
    }

    @Test
    fun afterTheLobbyTheFirstMoveGoesThroughTheControllers() {
        val p = TwoLobbies()
        upToTheCode(p, Player.ONE)
        p.hostLobby.confirmYes()
        p.guestLobby.confirmYes()
        p.link.pump()
        val host = p.hostLobby.controller ?: error("No host controller")
        val guest = p.guestLobby.controller ?: error("No guest controller")
        assertTrue(host.localMove(2))
        p.link.pump()
        assertEquals(1, guest.state.moveNumber)
        assertEquals(host.state, guest.state)
    }

    @Test
    fun saying_noEndsItForBothAndTheOtherPhoneIsToldItWasRefused() {
        val p = TwoLobbies()
        upToTheCode(p)
        p.hostLobby.confirmNo()
        p.link.pump()
        assertEquals(LobbyStage.START, p.hostLobby.state.value.stage)
        assertEquals(LobbyStage.FAILED, p.guestLobby.state.value.stage)
        assertEquals(LobbyError.REFUSED, p.guestLobby.state.value.error)
        assertTrue(p.link.host.rejected)
        assertNull(p.hostLobby.controller)
        assertNull(p.guestLobby.controller)
    }

    @Test
    fun aVersionMismatchEndsTheConnection() {
        val p = TwoLobbies()
        p.link.tamperHostToGuest = {
            """{"v":2,"type":"Hello","appVersionCode":1,"name":"Ada","avatar":"KING"}""".toByteArray()
        }
        upToTheCode(p)
        p.hostLobby.confirmYes()
        p.guestLobby.confirmYes()
        p.link.pump()
        assertEquals(LobbyStage.FAILED, p.guestLobby.state.value.stage)
        assertEquals(LobbyError.VERSION_MISMATCH, p.guestLobby.state.value.error)
        assertNull(p.guestLobby.controller)
    }

    @Test
    fun aConnectionThatTakesSixtySecondsIsGivenUp() {
        val p = TwoLobbies()
        upToTheCode(p)
        p.now = 59_999
        p.guestLobby.tick()
        p.hostLobby.tick()
        assertEquals(LobbyStage.CONFIRM, p.guestLobby.state.value.stage)
        p.now = 60_000
        p.guestLobby.tick()
        p.hostLobby.tick()
        assertEquals(LobbyStage.FAILED, p.guestLobby.state.value.stage)
        assertEquals(LobbyError.TIMEOUT, p.guestLobby.state.value.error)
        assertEquals(LobbyError.TIMEOUT, p.hostLobby.state.value.error)
    }

    @Test
    fun waitingForAGuestOrLookingForAHostNeverTimesOut() {
        val p = TwoLobbies()
        p.hostLobby.host(hostMe, Player.ONE)
        p.guestLobby.join(guestMe)
        p.now = 10 * 60_000L
        p.hostLobby.tick()
        p.guestLobby.tick()
        assertEquals(LobbyStage.HOST_WAITING, p.hostLobby.state.value.stage)
        assertEquals(LobbyStage.JOIN_SEARCHING, p.guestLobby.state.value.stage)
    }

    @Test
    fun aLinkThatBreaksWhileConnectingIsReportedAsLost() {
        val p = TwoLobbies()
        upToTheCode(p)
        p.link.breakLink()
        p.link.pump()
        assertEquals(LobbyError.LOST, p.hostLobby.state.value.error)
        assertEquals(LobbyError.LOST, p.guestLobby.state.value.error)
    }

    @Test
    fun cancelGoesBackToTheStartAndClosesTheConnection() {
        val p = TwoLobbies()
        p.hostLobby.host(hostMe, Player.ONE)
        p.hostLobby.cancel()
        assertEquals(LobbyStage.START, p.hostLobby.state.value.stage)
        assertTrue(p.link.host.closeCalls >= 1)
        // The same lobby can start again.
        p.hostLobby.host(hostMe, Player.ONE)
        assertEquals(LobbyStage.HOST_WAITING, p.hostLobby.state.value.stage)
    }

    @Test
    fun aFailureIsShownOnceAndResetGoesBackToTheStart() {
        val p = TwoLobbies()
        p.hostLobby.host(hostMe, Player.ONE)
        p.hostLobby.onConnectionFailed(ConnectFailure.START_FAILED)
        assertEquals(LobbyStage.FAILED, p.hostLobby.state.value.stage)
        assertEquals(LobbyError.START_FAILED, p.hostLobby.state.value.error)
        p.hostLobby.reset()
        assertEquals(LobbyStage.START, p.hostLobby.state.value.stage)
        assertNull(p.hostLobby.state.value.error)
    }

    @Test
    fun theListOfHostsFollowsWhoAppearsAndWhoLeaves() {
        val p = TwoLobbies()
        p.guestLobby.join(guestMe)
        p.guestLobby.onHostFound(FoundHost("a", RemotePlayer("One", AvatarId.KING)))
        p.guestLobby.onHostFound(FoundHost("b", RemotePlayer("Two", AvatarId.QUEEN)))
        p.guestLobby.onHostFound(FoundHost("a", RemotePlayer("One again", AvatarId.KING)))
        assertEquals(listOf("Two", "One again"), p.guestLobby.state.value.hosts.map { it.player.name })
        p.guestLobby.onHostLost("b")
        assertEquals(listOf("One again"), p.guestLobby.state.value.hosts.map { it.player.name })
    }

    @Test
    fun hostsAreIgnoredOutsideTheSearch() {
        val p = TwoLobbies()
        p.hostLobby.host(hostMe, Player.ONE)
        p.hostLobby.onHostFound(FoundHost("a", RemotePlayer("One", AvatarId.KING)))
        assertTrue(p.hostLobby.state.value.hosts.isEmpty())
    }

    @Test
    fun aMessageBeforeAnyConnectionIsIgnored() {
        val p = TwoLobbies()
        p.hostLobby.host(hostMe, Player.ONE)
        p.hostLobby.onMessage(ProtocolCodec.encode(PhonesMessage.Hello(7, "Bola", AvatarId.QUEEN)))
        assertEquals(LobbyStage.HOST_WAITING, p.hostLobby.state.value.stage)
        assertNull(p.hostLobby.controller)
        assertFalse(p.hostLobby.state.value.localAccepted)
        assertNotNull(p.hostLobby.state.value)
    }
}
