package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.IllegalMoveException
import com.westly.twelvepits.feature.game.engine.SowingEngine
import com.westly.twelvepits.feature.game.engine.TurnResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Why a two-phone match is over. */
enum class EndReason {
    /** This player left. */
    LEFT_BY_ME,

    /** The other player left (they sent a goodbye). */
    PEER_LEFT,

    /** Nothing was heard from the other phone for 15 seconds, or the link broke. */
    CONNECTION_LOST,

    /** The two phones could not agree on the position, even after the host sent the whole position. */
    OUT_OF_SYNC,

    /** The other phone runs another version of the game. */
    VERSION_MISMATCH,
}

/** The play-again question between two finished games. */
enum class RematchState {
    NONE,

    /** I asked and wait for an answer. */
    MINE_PENDING,

    /** The other player asked and waits for my answer. */
    THEIRS_PENDING,

    /** The other player said no. Shown once. */
    DECLINED,
}

/** Why the host does not accept a move. */
enum class RejectReason { WRONG_GAME, WRONG_SEQUENCE, NOT_YOUR_TURN, ILLEGAL_PIT, GAME_OVER }

/** One thing the game screen has to play, in order. */
sealed class MatchItem {
    /** A turn that was accepted. [mover] is the player who played it. */
    class Turn(val result: TurnResult, val mover: Player) : MatchItem()

    /** The host sent the whole position: the board is set to it without animation. */
    class Replace(val state: GameState) : MatchItem()
}

/** What the screens read. A new value is published after every change. */
data class MatchSnapshot(
    val gameNumber: Int = 0,
    val rematch: RematchState = RematchState.NONE,
    val endReason: EndReason? = null,
)

const val PING_INTERVAL_MS = 5_000L
const val PEER_TIMEOUT_MS = 15_000L

/**
 * What both phones have in common. Both run the same engine on every accepted turn and keep their own
 * copy of the position. The host decides which moves are accepted ([HostController]); the guest asks
 * ([GuestController]). Pure Kotlin: the connection comes in as a [GameTransport] and time as [clock].
 */
abstract class MatchController(
    protected val transport: GameTransport,
    /** ONE for the host, TWO for the guest. */
    val role: Player,
    protected val clock: () -> Long,
    protected val engine: SowingEngine = SowingEngine(),
) : TransportListener {

    var gameId: String = ""
        private set

    var config: GameConfig? = null
        private set

    /** The position at the start of the current game. The turns of the game are played on it, in order. */
    var initialState: GameState? = null
        private set

    /** The position after the last accepted turn. */
    var state: GameState = GameState.initial(Player.ONE)
        protected set

    var gameNumber: Int = 0
        private set

    var rematchState: RematchState = RematchState.NONE
        private set

    var endReason: EndReason? = null
        private set

    /** Called when a game starts (the first one or a rematch). */
    var onGameStarted: (() -> Unit)? = null

    private val mutableSnapshot = MutableStateFlow(MatchSnapshot())
    val snapshot: StateFlow<MatchSnapshot> = mutableSnapshot

    private val log = ArrayList<MatchItem>()
    private var sink: ((MatchItem) -> Unit)? = null
    private var lastSentMs: Long = clock()
    private var lastReceivedMs: Long = clock()

    /** True when a tap on a pit may be sent or played now. */
    abstract val canLocalMove: Boolean

    /** The local player taps [pit]. True when the tap was accepted for playing or sending. */
    abstract fun localMove(pit: Int): Boolean

    protected abstract fun handleGame(message: PhonesMessage)

    /** The other player asked for a rematch while I had asked too. */
    protected open fun peerRequestedWhileMinePending() {}

    /** The other player accepted my rematch request. */
    protected open fun peerAcceptedMine() {}

    /** I accepted the rematch the other player asked for. */
    protected open fun acceptedByMe() {}

    /**
     * Gives every turn of the current game to [newSink], from the first one, and every later turn as
     * it is accepted. The game screen of the current game calls this once.
     */
    fun attachTurnSink(newSink: (MatchItem) -> Unit) {
        sink = newSink
        for (item in ArrayList(log)) newSink(item)
    }

    fun detachTurnSink(oldSink: (MatchItem) -> Unit) {
        if (sink === oldSink) sink = null
    }

    /** Called about once a second by the owner. Sends a ping when it is quiet and ends the match when the other phone is silent. */
    fun tick() {
        if (endReason != null) return
        val now = clock()
        if (now - lastReceivedMs >= PEER_TIMEOUT_MS) {
            end(EndReason.CONNECTION_LOST, notifyPeer = false)
            return
        }
        if (now - lastSentMs >= PING_INTERVAL_MS) send(PhonesMessage.Ping)
    }

    /** This player leaves. The other phone is told once. */
    fun leave() {
        end(EndReason.LEFT_BY_ME, notifyPeer = true)
    }

    override fun onMessage(bytes: ByteArray) {
        if (endReason != null) return
        lastReceivedMs = clock()
        when (val decoded = ProtocolCodec.decode(bytes)) {
            is DecodeResult.Ok -> handle(decoded.message)
            is DecodeResult.VersionMismatch -> end(EndReason.VERSION_MISMATCH, notifyPeer = false)
            DecodeResult.Unknown, DecodeResult.Malformed, DecodeResult.TooLarge -> Unit
        }
    }

    override fun onDisconnected() {
        end(EndReason.CONNECTION_LOST, notifyPeer = false)
    }

    private fun handle(message: PhonesMessage) {
        when (message) {
            PhonesMessage.Ping -> send(PhonesMessage.Pong)
            PhonesMessage.Pong -> Unit
            is PhonesMessage.Bye ->
                end(if (message.reason == BYE_DESYNC) EndReason.OUT_OF_SYNC else EndReason.PEER_LEFT, notifyPeer = false)
            PhonesMessage.RematchRequest -> onRematchRequest()
            PhonesMessage.RematchAccepted -> if (rematchState == RematchState.MINE_PENDING) peerAcceptedMine()
            PhonesMessage.RematchDeclined -> onRematchDeclined()
            else -> handleGame(message)
        }
    }

    // ---- Rematch -----------------------------------------------------------------------------

    /** PLAY AGAIN on the result screen. If the other player already asked, this accepts. */
    fun requestRematch() {
        if (endReason != null || gameNumber == 0 || state.status != GameStatus.FINISHED) return
        when (rematchState) {
            RematchState.THEIRS_PENDING -> acceptRematch()
            RematchState.MINE_PENDING -> Unit
            RematchState.NONE, RematchState.DECLINED -> {
                rematchState = RematchState.MINE_PENDING
                send(PhonesMessage.RematchRequest)
                publish()
            }
        }
    }

    /** Take back my own request. */
    fun cancelRematchRequest() {
        if (rematchState != RematchState.MINE_PENDING) return
        rematchState = RematchState.NONE
        send(PhonesMessage.RematchDeclined)
        publish()
    }

    fun acceptRematch() {
        if (endReason != null || rematchState != RematchState.THEIRS_PENDING) return
        rematchState = RematchState.NONE
        send(PhonesMessage.RematchAccepted)
        publish()
        acceptedByMe()
    }

    fun declineRematch() {
        if (rematchState != RematchState.THEIRS_PENDING) return
        rematchState = RematchState.NONE
        send(PhonesMessage.RematchDeclined)
        publish()
    }

    /** The "does not want to play again" message was read. */
    fun dismissDeclined() {
        if (rematchState != RematchState.DECLINED) return
        rematchState = RematchState.NONE
        publish()
    }

    private fun onRematchRequest() {
        if (gameNumber == 0 || state.status != GameStatus.FINISHED) return
        if (rematchState == RematchState.MINE_PENDING) {
            peerRequestedWhileMinePending()
        } else {
            rematchState = RematchState.THEIRS_PENDING
            publish()
        }
    }

    private fun onRematchDeclined() {
        when (rematchState) {
            RematchState.MINE_PENDING -> rematchState = RematchState.DECLINED
            RematchState.THEIRS_PENDING -> rematchState = RematchState.NONE
            else -> return
        }
        publish()
    }

    // ---- For the two controllers -------------------------------------------------------------

    protected fun beginGame(id: String, newConfig: GameConfig, starter: Player) {
        gameId = id
        config = newConfig
        val start = GameState.initial(starter)
        initialState = start
        state = start
        log.clear()
        gameNumber += 1
        rematchState = RematchState.NONE
        publish()
        onGameStarted?.invoke()
    }

    protected fun buildConfig(
        hostName: String,
        hostAvatar: AvatarId,
        guestName: String,
        guestAvatar: AvatarId,
    ): GameConfig =
        GameConfig(
            mode = GameMode.TWO_PHONES,
            difficulty = Difficulty.NORMAL,
            player1Name = hostName,
            player1Avatar = hostAvatar,
            player2Name = guestName,
            player2Avatar = guestAvatar,
        )

    protected fun commitTurn(result: TurnResult, mover: Player) {
        state = result.newState
        commit(MatchItem.Turn(result, mover))
    }

    protected fun commit(item: MatchItem) {
        log.add(item)
        sink?.invoke(item)
    }

    protected fun send(message: PhonesMessage) {
        if (endReason != null) return
        lastSentMs = clock()
        transport.send(ProtocolCodec.encode(message))
    }

    protected fun end(reason: EndReason, notifyPeer: Boolean) {
        if (endReason != null) return
        if (notifyPeer) {
            val bye = if (reason == EndReason.OUT_OF_SYNC) BYE_DESYNC else BYE_LEFT
            transport.send(ProtocolCodec.encode(PhonesMessage.Bye(bye)))
        }
        endReason = reason
        publish()
        transport.close()
    }

    protected fun publish() {
        mutableSnapshot.value = MatchSnapshot(gameNumber, rematchState, endReason)
    }

    /** Plays [pit] on [from] with the engine. Null when the engine refuses it. */
    protected fun playOrNull(from: GameState, pit: Int): TurnResult? =
        try {
            engine.playTurn(from, pit)
        } catch (e: IllegalMoveException) {
            null
        }
}
