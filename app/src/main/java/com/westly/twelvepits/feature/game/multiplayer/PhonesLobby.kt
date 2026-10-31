package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.menu.resolveAvatarClash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LobbyStage {
    /** Nothing started: the name and avatar are chosen. */
    START,

    /** The host waits for a guest. */
    HOST_WAITING,

    /** The guest looks for hosts and shows the list. */
    JOIN_SEARCHING,

    /** The guest chose a host and waits for the code. */
    REQUESTING,

    /** Both phones show the same code and ask for a yes. */
    CONFIRM,

    /** Both said yes. The phones introduce themselves and the game starts. */
    CONNECTING,

    /** The game has started: the game screen can open. */
    READY,

    FAILED,
}

enum class LobbyError { START_FAILED, REFUSED, TIMEOUT, VERSION_MISMATCH, LOST, ERROR }

data class LobbyState(
    val stage: LobbyStage = LobbyStage.START,
    val hosts: List<FoundHost> = emptyList(),
    val authCode: String? = null,
    val remote: RemotePlayer? = null,
    val localAccepted: Boolean = false,
    val error: LobbyError? = null,
)

const val CONNECT_TIMEOUT_MS = 60_000L

/**
 * Everything before the first game: be found or look for a host, compare the code, say yes, introduce
 * the phones, and hand over to a [HostController] or a [GuestController]. Pure Kotlin.
 */
class PhonesLobby(
    private val transport: GameTransport,
    private val clock: () -> Long,
    private val appVersionCode: Int,
    private val gameIds: () -> String,
) : TransportListener {

    private val mutableState = MutableStateFlow(LobbyState())
    val state: StateFlow<LobbyState> = mutableState

    /** Set when the first game has started. */
    var controller: MatchController? = null
        private set

    private var local: RemotePlayer? = null
    private var isHost = false
    private var starter = Player.ONE
    private var connectingSince = 0L
    private var connected = false
    private var helloSent = false
    private var helloReceived = false

    fun host(me: RemotePlayer, firstStarter: Player) {
        begin(me, host = true)
        starter = firstStarter
        mutableState.value = LobbyState(stage = LobbyStage.HOST_WAITING)
        transport.startHosting(me)
    }

    fun join(me: RemotePlayer) {
        begin(me, host = false)
        mutableState.value = LobbyState(stage = LobbyStage.JOIN_SEARCHING)
        transport.startDiscovery()
    }

    /** The guest taps a host in the list. */
    fun choose(host: FoundHost) {
        val me = local ?: return
        if (mutableState.value.stage != LobbyStage.JOIN_SEARCHING) return
        connectingSince = clock()
        mutableState.value = mutableState.value.copy(stage = LobbyStage.REQUESTING, remote = host.player)
        transport.connectTo(host.endpointId, me)
    }

    fun confirmYes() {
        val now = mutableState.value
        if (now.stage != LobbyStage.CONFIRM || now.localAccepted) return
        mutableState.value = now.copy(localAccepted = true)
        transport.acceptConnection()
    }

    fun confirmNo() {
        if (mutableState.value.stage != LobbyStage.CONFIRM) return
        transport.rejectConnection()
        cancel()
    }

    /** Back to the start: everything is closed. */
    fun cancel() {
        transport.close()
        controller = null
        forget()
        mutableState.value = LobbyState()
    }

    /** After a failure: back to the start screen. */
    fun reset() {
        cancel()
    }

    /** Called about once a second. A connection that takes more than 60 seconds is given up. */
    fun tick() {
        val stage = mutableState.value.stage
        val waiting = stage == LobbyStage.REQUESTING || stage == LobbyStage.CONFIRM || stage == LobbyStage.CONNECTING
        if (waiting && clock() - connectingSince >= CONNECT_TIMEOUT_MS) fail(LobbyError.TIMEOUT)
    }

    private fun begin(me: RemotePlayer, host: Boolean) {
        transport.close()
        controller = null
        forget()
        local = me
        isHost = host
        transport.setListener(this)
    }

    private fun forget() {
        connected = false
        helloSent = false
        helloReceived = false
        connectingSince = 0L
    }

    private fun fail(error: LobbyError) {
        transport.close()
        controller = null
        forget()
        mutableState.value = LobbyState(stage = LobbyStage.FAILED, error = error)
    }

    private fun inConnecting(): Boolean {
        val stage = mutableState.value.stage
        return stage == LobbyStage.REQUESTING || stage == LobbyStage.CONFIRM || stage == LobbyStage.CONNECTING
    }

    // ---- What the transport tells us ---------------------------------------------------------

    override fun onHostFound(host: FoundHost) {
        val now = mutableState.value
        if (now.stage != LobbyStage.JOIN_SEARCHING) return
        val others = now.hosts.filter { it.endpointId != host.endpointId }
        mutableState.value = now.copy(hosts = others + host)
    }

    override fun onHostLost(endpointId: String) {
        val now = mutableState.value
        if (now.stage != LobbyStage.JOIN_SEARCHING) return
        mutableState.value = now.copy(hosts = now.hosts.filter { it.endpointId != endpointId })
    }

    override fun onConnectionInitiated(authCode: String, remote: RemotePlayer) {
        val stage = mutableState.value.stage
        if (stage != LobbyStage.HOST_WAITING && stage != LobbyStage.REQUESTING) {
            transport.rejectConnection()
            return
        }
        connectingSince = clock()
        mutableState.value = mutableState.value.copy(
            stage = LobbyStage.CONFIRM,
            authCode = authCode,
            remote = remote,
            localAccepted = false,
        )
    }

    override fun onConnectionEstablished() {
        if (!inConnecting()) return
        connected = true
        val now = mutableState.value
        if (now.stage == LobbyStage.CONFIRM) mutableState.value = now.copy(stage = LobbyStage.CONNECTING)
        sendHello()
    }

    override fun onConnectionFailed(failure: ConnectFailure) {
        val stage = mutableState.value.stage
        if (stage == LobbyStage.START || stage == LobbyStage.READY || stage == LobbyStage.FAILED) return
        fail(
            when (failure) {
                ConnectFailure.START_FAILED -> LobbyError.START_FAILED
                ConnectFailure.REFUSED -> LobbyError.REFUSED
                ConnectFailure.ERROR -> LobbyError.ERROR
            },
        )
    }

    override fun onDisconnected() {
        if (inConnecting()) fail(LobbyError.LOST)
    }

    override fun onMessage(bytes: ByteArray) {
        if (!inConnecting()) return
        when (val decoded = ProtocolCodec.decode(bytes)) {
            is DecodeResult.VersionMismatch -> fail(LobbyError.VERSION_MISMATCH)
            is DecodeResult.Ok -> {
                val message = decoded.message
                if (message is PhonesMessage.Hello) onHello(message)
            }
            DecodeResult.Unknown, DecodeResult.Malformed, DecodeResult.TooLarge -> Unit
        }
    }

    private fun sendHello() {
        val me = local ?: return
        if (helloSent || !connected) return
        helloSent = true
        transport.send(ProtocolCodec.encode(PhonesMessage.Hello(appVersionCode, me.name, me.avatar)))
    }

    private fun onHello(hello: PhonesMessage.Hello) {
        if (helloReceived) return
        helloReceived = true
        val me = local ?: return
        // A message can only come over a connection that exists.
        connected = true
        val now = mutableState.value
        if (now.stage == LobbyStage.CONFIRM) mutableState.value = now.copy(stage = LobbyStage.CONNECTING)
        sendHello()
        if (isHost) {
            val guest = RemotePlayer(hello.name, resolveAvatarClash(me.avatar, hello.avatar))
            val hostController = HostController(transport, clock, me, guest, gameIds)
            attach(hostController)
            hostController.startFirstGame(starter)
        } else {
            attach(GuestController(transport, clock))
        }
    }

    private fun attach(newController: MatchController) {
        controller = newController
        newController.onGameStarted = {
            mutableState.value = mutableState.value.copy(stage = LobbyStage.READY)
        }
        transport.setListener(newController)
    }
}
