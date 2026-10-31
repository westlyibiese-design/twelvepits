package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine
import kotlin.random.Random

/** A host and a guest that already know each other and have started their first game. */
class MatchHarness(starter: Player = Player.ONE) {
    val link = FakeLink()
    var now = 0L
    private var counter = 0
    private val clock: () -> Long = { now }

    val host = HostController(
        transport = link.host,
        clock = clock,
        host = RemotePlayer("Ada", AvatarId.KING),
        guest = RemotePlayer("Bola", AvatarId.QUEEN),
        gameIds = { "game" + (++counter) },
    )
    val guest = GuestController(link.guest, clock)

    val hostItems = ArrayList<MatchItem>()
    val guestItems = ArrayList<MatchItem>()

    init {
        link.establishDirect()
        link.host.setListener(host)
        link.guest.setListener(guest)
        host.attachTurnSink { hostItems.add(it) }
        guest.attachTurnSink { guestItems.add(it) }
        host.startFirstGame(starter)
        link.pump()
    }

    /** The phone of whoever plays now. */
    fun mover(): MatchController = if (host.state.currentPlayer == Player.ONE) host else guest

    /** Plays one legal move chosen by [random] on the right phone and delivers everything. Returns the pit. */
    fun playRandomMove(random: Random): Int {
        val moves = host.state.legalMoves()
        val pit = moves[random.nextInt(moves.size)]
        check(mover().localMove(pit)) { "The phone of the player to move did not take the move" }
        link.pump()
        return pit
    }

    /** Plays until the game is over, and checks after every move that both phones hold the same position. */
    fun playToTheEnd(random: Random) {
        val engine = SowingEngine()
        var reference: GameState = host.initialState ?: error("No game")
        while (reference.status == GameStatus.IN_PROGRESS) {
            val pit = playRandomMove(random)
            reference = engine.playTurn(reference, pit).newState
            check(host.state == reference) { "The host differs from the engine" }
            check(guest.state == reference) { "The guest differs from the engine" }
            check(StateHash.of(host.state) == StateHash.of(guest.state)) { "The hashes differ" }
        }
    }
}
