package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine

/**
 * The host (Player ONE) decides which moves are played. Every move, its own and the guest's, is
 * checked here, played with the engine, announced to the guest and then played on the host's own board.
 */
class HostController(
    transport: GameTransport,
    clock: () -> Long,
    private val host: RemotePlayer,
    /** The guest, with the avatar already chosen so it differs from the host's. */
    private val guest: RemotePlayer,
    private val gameIds: () -> String,
    engine: SowingEngine = SowingEngine(),
) : MatchController(transport, Player.ONE, clock, engine) {

    private var resyncsSent = 0

    override val canLocalMove: Boolean
        get() = endReason == null && gameNumber > 0 &&
            state.status == GameStatus.IN_PROGRESS && state.currentPlayer == Player.ONE

    /** The first game of the match. [starter] comes from the starter record of the Two phones mode. */
    fun startFirstGame(starter: Player) {
        startNewGame(starter)
    }

    private fun startNewGame(starter: Player) {
        val id = gameIds()
        beginGame(id, buildConfig(host.name, host.avatar, guest.name, guest.avatar), starter)
        resyncsSent = 0
        send(PhonesMessage.GameStart(id, host.name, host.avatar, guest.name, guest.avatar, starter))
    }

    /** Null when the move is allowed, otherwise the reason it is not. */
    fun validateMove(from: Player, moveGameId: String, seq: Int, pit: Int): RejectReason? {
        if (moveGameId != gameId) return RejectReason.WRONG_GAME
        if (state.status != GameStatus.IN_PROGRESS) return RejectReason.GAME_OVER
        if (seq != state.moveNumber + 1) return RejectReason.WRONG_SEQUENCE
        if (state.currentPlayer != from) return RejectReason.NOT_YOUR_TURN
        if (pit !in 0 until GameState.PIT_COUNT) return RejectReason.ILLEGAL_PIT
        if (GameState.ownerOfPit(pit) != from || state.pits[pit] == 0) return RejectReason.ILLEGAL_PIT
        return null
    }

    override fun localMove(pit: Int): Boolean {
        if (endReason != null || gameNumber == 0) return false
        val seq = state.moveNumber + 1
        if (validateMove(Player.ONE, gameId, seq, pit) != null) return false
        return playAndAnnounce(Player.ONE, pit, seq)
    }

    override fun handleGame(message: PhonesMessage) {
        when (message) {
            is PhonesMessage.Move -> onGuestMove(message)
            is PhonesMessage.ResyncRequest -> onResyncRequest(message)
            else -> Unit
        }
    }

    private fun onGuestMove(move: PhonesMessage.Move) {
        val reason = validateMove(Player.TWO, move.gameId, move.seq, move.pit)
        if (reason != null) {
            send(PhonesMessage.Rejected(move.gameId, move.seq, reason.name))
            return
        }
        playAndAnnounce(Player.TWO, move.pit, move.seq)
    }

    private fun playAndAnnounce(mover: Player, pit: Int, seq: Int): Boolean {
        val result = playOrNull(state, pit) ?: return false
        send(PhonesMessage.TurnApplied(gameId, seq, pit, mover, StateHash.of(result.newState)))
        commitTurn(result, mover)
        return true
    }

    /** The guest's position did not match: the whole position is sent, but only once per game. */
    private fun onResyncRequest(request: PhonesMessage.ResyncRequest) {
        if (request.gameId != gameId || resyncsSent >= 1) return
        resyncsSent += 1
        send(PhonesMessage.Resync(gameId, state, StateHash.of(state)))
    }

    // The host starts every rematch. The player who did not start the finished game starts the next one.
    override fun peerRequestedWhileMinePending() {
        send(PhonesMessage.RematchAccepted)
        startNewGame(state.startingPlayer.opponent)
    }

    override fun peerAcceptedMine() {
        startNewGame(state.startingPlayer.opponent)
    }

    override fun acceptedByMe() {
        startNewGame(state.startingPlayer.opponent)
    }
}
