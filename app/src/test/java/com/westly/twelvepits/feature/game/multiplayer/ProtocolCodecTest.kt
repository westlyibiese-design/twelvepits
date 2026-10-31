package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.SowingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProtocolCodecTest {

    private fun roundTrip(message: PhonesMessage): PhonesMessage {
        val result = ProtocolCodec.decode(ProtocolCodec.encode(message))
        if (result !is DecodeResult.Ok) fail("Not decoded: $result")
        return (result as DecodeResult.Ok).message
    }

    private fun decodeText(text: String): DecodeResult = ProtocolCodec.decode(text.toByteArray(Charsets.UTF_8))

    private fun assertMalformed(text: String) {
        val result = decodeText(text)
        assertTrue("Expected Malformed for $text but got $result", result is DecodeResult.Malformed)
    }

    @Test
    fun helloSurvivesTheTrip() {
        val back = roundTrip(PhonesMessage.Hello(123, "Ada", AvatarId.WARRIOR)) as PhonesMessage.Hello
        assertEquals(123, back.appVersionCode)
        assertEquals("Ada", back.name)
        assertEquals(AvatarId.WARRIOR, back.avatar)
    }

    @Test
    fun gameStartSurvivesTheTripForBothStarters() {
        for (starter in Player.entries) {
            val sent = PhonesMessage.GameStart("abc12345", "Ada", AvatarId.KING, "Bola", AvatarId.QUEEN, starter)
            val back = roundTrip(sent) as PhonesMessage.GameStart
            assertEquals("abc12345", back.gameId)
            assertEquals("Ada", back.hostName)
            assertEquals(AvatarId.KING, back.hostAvatar)
            assertEquals("Bola", back.guestName)
            assertEquals(AvatarId.QUEEN, back.guestAvatar)
            assertEquals(starter, back.startingPlayer)
        }
    }

    @Test
    fun gameStartTextUsesHostAndGuest() {
        val text = ProtocolCodec.toJson(
            PhonesMessage.GameStart("g", "Ada", AvatarId.KING, "Bola", AvatarId.QUEEN, Player.TWO),
        )
        assertTrue(text, text.contains("\"startingPlayer\":\"GUEST\""))
        assertTrue(text, text.contains("\"v\":1"))
    }

    @Test
    fun moveAndTurnAppliedAndRejectedSurviveTheTrip() {
        val move = roundTrip(PhonesMessage.Move("g1", 7, 9)) as PhonesMessage.Move
        assertEquals("g1", move.gameId)
        assertEquals(7, move.seq)
        assertEquals(9, move.pit)

        val applied = roundTrip(PhonesMessage.TurnApplied("g1", 8, 3, Player.ONE, -123456789)) as PhonesMessage.TurnApplied
        assertEquals(8, applied.seq)
        assertEquals(3, applied.pit)
        assertEquals(Player.ONE, applied.mover)
        assertEquals(-123456789, applied.stateHash)

        val rejected = roundTrip(PhonesMessage.Rejected("g1", 8, "NOT_YOUR_TURN")) as PhonesMessage.Rejected
        assertEquals("NOT_YOUR_TURN", rejected.reason)
        assertEquals(8, rejected.seq)
    }

    @Test
    fun resyncCarriesTheWholePosition() {
        val engine = SowingEngine()
        var state = GameState.initial(Player.ONE)
        state = engine.playTurn(state, 2).newState
        state = engine.playTurn(state, state.legalMoves().first()).newState
        val back = roundTrip(PhonesMessage.Resync("g1", state, StateHash.of(state))) as PhonesMessage.Resync
        assertEquals(state, back.state)
        assertEquals(StateHash.of(state), back.stateHash)
    }

    @Test
    fun messagesWithoutFieldsSurviveTheTrip() {
        assertTrue(roundTrip(PhonesMessage.Ping) is PhonesMessage.Ping)
        assertTrue(roundTrip(PhonesMessage.Pong) is PhonesMessage.Pong)
        assertTrue(roundTrip(PhonesMessage.RematchRequest) is PhonesMessage.RematchRequest)
        assertTrue(roundTrip(PhonesMessage.RematchAccepted) is PhonesMessage.RematchAccepted)
        assertTrue(roundTrip(PhonesMessage.RematchDeclined) is PhonesMessage.RematchDeclined)
        assertTrue(roundTrip(PhonesMessage.ResyncRequest("g", 4)) is PhonesMessage.ResyncRequest)
        assertEquals(BYE_LEFT, (roundTrip(PhonesMessage.Bye(BYE_LEFT)) as PhonesMessage.Bye).reason)
        assertEquals(BYE_DESYNC, (roundTrip(PhonesMessage.Bye(BYE_DESYNC)) as PhonesMessage.Bye).reason)
    }

    @Test
    fun namesWithQuotesBackslashesNewlinesAndAccentsSurviveTheTrip() {
        for (name in listOf("A\"B", "A\\B", "A\nB", "Ọlá Ńkẹ", "日本語", "😀")) {
            val back = roundTrip(PhonesMessage.Hello(1, name, AvatarId.KING)) as PhonesMessage.Hello
            assertEquals(name, back.name)
        }
    }

    @Test
    fun anUnknownTypeIsIgnoredNotRejected() {
        val result = decodeText("""{"v":1,"type":"Dance","gameId":"x","seq":0}""")
        assertTrue(result is DecodeResult.Unknown)
    }

    @Test
    fun anotherVersionIsReportedBeforeAnythingElse() {
        val result = decodeText("""{"v":2,"type":"Dance"}""")
        assertTrue(result is DecodeResult.VersionMismatch)
        assertEquals(2, (result as DecodeResult.VersionMismatch).theirVersion)
        assertTrue(decodeText("""{"v":2,"type":"Hello","appVersionCode":1,"name":"A","avatar":"KING"}""") is DecodeResult.VersionMismatch)
    }

    @Test
    fun aMissingVersionIsMalformed() {
        assertMalformed("""{"type":"Ping"}""")
    }

    @Test
    fun brokenTextIsMalformedAndNeverThrows() {
        assertMalformed("")
        assertMalformed("not json")
        assertMalformed("[1,2,3]")
        assertMalformed("\"text\"")
        assertMalformed("{")
        assertMalformed("""{"v":1,"type":"Ping"""")
        assertMalformed("null")
        assertMalformed("[".repeat(3000))
        assertMalformed("""{"v":"1","type":"Ping"}""")
        assertMalformed("""{"v":1,"type":5}""")
        assertMalformed("""{"v":1,"type":"Ping","gameId":7}""")
        assertMalformed("""{"v":1,"type":"Ping","seq":"x"}""")
    }

    @Test
    fun randomBytesNeverThrow() {
        val random = kotlin.random.Random(99)
        repeat(300) {
            val bytes = ByteArray(random.nextInt(0, 300)) { random.nextInt(256).toByte() }
            ProtocolCodec.decode(bytes)
        }
    }

    @Test
    fun missingOrBadFieldsAreMalformed() {
        assertMalformed("""{"v":1,"type":"Move","gameId":"g","seq":1}""")
        assertMalformed("""{"v":1,"type":"Move","gameId":"g","seq":1,"pit":12}""")
        assertMalformed("""{"v":1,"type":"Move","gameId":"g","seq":1,"pit":-1}""")
        assertMalformed("""{"v":1,"type":"Move","gameId":"g","seq":1,"pit":1.5}""")
        assertMalformed("""{"v":1,"type":"Move","gameId":"g","seq":1,"pit":1e30}""")
        assertMalformed("""{"v":1,"type":"TurnApplied","gameId":"g","seq":1,"pit":1,"mover":"THREE","stateHash":5}""")
        assertMalformed("""{"v":1,"type":"TurnApplied","gameId":"g","seq":1,"pit":1,"mover":"ONE"}""")
        assertMalformed("""{"v":1,"type":"Hello","appVersionCode":1,"name":"   ","avatar":"KING"}""")
        assertMalformed("""{"v":1,"type":"Hello","appVersionCode":1,"name":"A","avatar":"LEOPARD"}""")
        assertMalformed("""{"v":1,"type":"Hello","appVersionCode":1,"name":"A","avatar":"DRAGON"}""")
        assertMalformed("""{"v":1,"type":"Hello","appVersionCode":1,"name":"ThisNameIsFarTooLong","avatar":"KING"}""")
        assertMalformed("""{"v":1,"type":"GameStart","gameId":"","hostName":"A","hostAvatar":"KING","guestName":"B","guestAvatar":"QUEEN","startingPlayer":"HOST"}""")
        assertMalformed("""{"v":1,"type":"GameStart","gameId":"g","hostName":"A","hostAvatar":"KING","guestName":"B","guestAvatar":"QUEEN","startingPlayer":"NOBODY"}""")
        assertMalformed("""{"v":1,"type":"Resync","gameId":"g","stateHash":1,"state":{"pits":[1]}}""")
        assertMalformed("""{"v":1,"type":"Resync","gameId":"g","stateHash":1,"state":5}""")
    }

    @Test
    fun aResyncWithAnImpossiblePositionIsMalformed() {
        val state = GameState.initial(Player.ONE)
        val good = ProtocolCodec.toJson(PhonesMessage.Resync("g", state, StateHash.of(state)))
        assertTrue(decodeText(good) is DecodeResult.Ok)
        // 49 seeds in the first pit: the position follows no rule, so it is refused.
        val bad = good.replace("\"pits\":[4,", "\"pits\":[5,")
        assertNotEquals(good, bad)
        assertMalformed(bad)
    }

    @Test
    fun aLongGameIdIsMalformed() {
        assertMalformed("""{"v":1,"type":"Ping","gameId":"${"x".repeat(65)}"}""")
    }

    @Test
    fun aMessageOverTheLimitIsTooLargeAndOneAtTheLimitIsReadNormally() {
        val big = ByteArray(MAX_MESSAGE_BYTES + 1) { 'a'.code.toByte() }
        assertTrue(ProtocolCodec.decode(big) is DecodeResult.TooLarge)
        val exact = ByteArray(MAX_MESSAGE_BYTES) { 'a'.code.toByte() }
        assertTrue(ProtocolCodec.decode(exact) is DecodeResult.Malformed)
    }

    @Test
    fun everyRealMessageIsFarBelowTheLimit() {
        val state = GameState.initial(Player.ONE)
        val biggest = ProtocolCodec.encode(PhonesMessage.Resync("12345678", state, StateHash.of(state)))
        assertTrue(biggest.size < 600)
    }

    @Test
    fun theHashIsTheSameForTheSamePositionAndDiffersWhenAnythingChanges() {
        val engine = SowingEngine()
        val start = GameState.initial(Player.ONE)
        assertEquals(StateHash.of(start), StateHash.of(GameState.initial(Player.ONE)))
        assertNotEquals(StateHash.of(start), StateHash.of(GameState.initial(Player.TWO)))
        val after = engine.playTurn(start, 0).newState
        assertNotEquals(StateHash.of(start), StateHash.of(after))
        assertNotEquals(StateHash.of(after), StateHash.of(after.copy(moveNumber = after.moveNumber + 1)))
    }
}
