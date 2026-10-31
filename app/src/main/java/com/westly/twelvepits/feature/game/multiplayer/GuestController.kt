package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.SowingEngine

/**
 * The guest (Player TWO) asks the host to play a move and plays every turn the host announces.
 * It checks every announced turn against the hash of the host's position.
 */
class GuestController(
    transport: GameTransport,
    clock: () -> Long,
    engine: SowingEngine = SowingEngine(),
) : MatchController(transport, Player.TWO, clock, engine) {

    /** The sequence number of my move that the host has not answered yet. */
    private var pendingSeq: Int? = null
    private var resyncUsed = false
    private var awaitingResync = false

    override val canLocalMove: Boolean
        get() = endReason == null && gameNumber > 0 && !awaitingResync && pendingSeq == null &&
            state.status == GameStatus.IN_PROGRESS && state.currentPlayer == Player.TWO

    override fun localMove(pit: Int): Boolean {
        if (!canLocalMove) return false
        if (pit !in 0 until GameState.PIT_COUNT) return false
        if (GameState.ownerOfPit(pit) != Player.TWO || state.pits[pit] == 0) return false
        val seq = state.moveNumber + 1
        pendingSeq = seq
        send(PhonesMessage.Move(gameId, seq, pit))
        return true
    }

    override fun handleGame(message: PhonesMessage) {
        when (message) {
            is PhonesMessage.GameStart -> onGameStart(message)
            is PhonesMessage.TurnApplied -> onTurnApplied(message)
            is PhonesMessage.Rejected -> {
                // Only the answer to my own open move frees the board for the next tap.
                if (message.gameId == gameId && message.seq == pendingSeq) pendingSeq = null
            }
            is PhonesMessage.Resync -> onResync(message)
            else -> Unit
        }
    }

    private fun onGameStart(start: PhonesMessage.GameStart) {
        // A new game can only start after the one before it is finished.
        if (gameNumber > 0 && state.status != GameStatus.FINISHED) return
        beginGame(
            start.gameId,
            buildConfig(start.hostName, start.hostAvatar, start.guestName, start.guestAvatar),
            start.startingPlayer,
        )
        pendingSeq = null
        resyncUsed = false
        awaitingResync = false
    }

    private fun onTurnApplied(applied: PhonesMessage.TurnApplied) {
        if (applied.gameId != gameId || awaitingResync || gameNumber == 0) return
        val expected = state.moveNumber + 1
        // An older turn is a repeat: ignore it. A turn from the future means a turn was lost.
        if (applied.seq < expected) return
        if (applied.seq > expected) {
            mismatch()
            return
        }
        if (state.status != GameStatus.IN_PROGRESS || state.currentPlayer != applied.mover) {
            mismatch()
            return
        }
        val result = playOrNull(state, applied.pit)
        if (result == null || StateHash.of(result.newState) != applied.stateHash) {
            mismatch()
            return
        }
        if (pendingSeq == applied.seq) pendingSeq = null
        commitTurn(result, applied.mover)
    }

    /** The first time: ask the host for the whole position. The second time: the game is over for both. */
    private fun mismatch() {
        if (resyncUsed) {
            end(EndReason.OUT_OF_SYNC, notifyPeer = true)
            return
        }
        resyncUsed = true
        awaitingResync = true
        send(PhonesMessage.ResyncRequest(gameId, state.moveNumber))
    }

    private fun onResync(resync: PhonesMessage.Resync) {
        if (!awaitingResync || resync.gameId != gameId) return
        if (!resync.state.isValid() || StateHash.of(resync.state) != resync.stateHash) {
            end(EndReason.OUT_OF_SYNC, notifyPeer = true)
            return
        }
        awaitingResync = false
        pendingSeq = null
        state = resync.state
        commit(MatchItem.Replace(resync.state))
    }
}
