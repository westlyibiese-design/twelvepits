package com.westly.twelvepits.feature.game.multiplayer

/**
 * Two fake phones joined by a fake link, for the tests. Nothing is delivered until [pump] is called, so a
 * test decides exactly when each message arrives. Messages arrive in the order they were sent.
 */
class FakeLink {
    val host = FakeTransport(this, isHost = true)
    val guest = FakeTransport(this, isHost = false)

    private val queue = ArrayDeque<() -> Unit>()
    private var found = false

    var established = false
    var closedLink = false

    /** While true every message is lost on the way. */
    var muted = false
    var hostPlayer: RemotePlayer? = null
    var guestPlayer: RemotePlayer? = null
    var guestSearching = false
    var authCode = "4821"

    /** Can change the bytes the host sends to the guest. */
    var tamperHostToGuest: ((ByteArray) -> ByteArray)? = null

    val sentByHost = ArrayList<ByteArray>()
    val sentByGuest = ArrayList<ByteArray>()

    fun pump() {
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
    }

    /** Pretends the connection was made, for tests that do not use the lobby. */
    fun establishDirect() {
        established = true
    }

    fun breakLink() {
        if (closedLink) return
        closedLink = true
        queue.addLast { host.receiver?.onDisconnected() }
        queue.addLast { guest.receiver?.onDisconnected() }
    }

    internal fun search() {
        val player = hostPlayer ?: return
        if (!guestSearching || found) return
        found = true
        queue.addLast { guest.receiver?.onHostFound(FoundHost("host-1", player)) }
    }

    internal fun initiate() {
        val hostSide = hostPlayer ?: return
        val guestSide = guestPlayer ?: return
        queue.addLast { host.receiver?.onConnectionInitiated(authCode, guestSide) }
        queue.addLast { guest.receiver?.onConnectionInitiated(authCode, hostSide) }
    }

    internal fun tryEstablish() {
        if (!host.accepted || !guest.accepted || established) return
        established = true
        queue.addLast { host.receiver?.onConnectionEstablished() }
        queue.addLast { guest.receiver?.onConnectionEstablished() }
    }

    internal fun rejectedBy(byHost: Boolean) {
        val other = if (byHost) guest else host
        queue.addLast { other.receiver?.onConnectionFailed(ConnectFailure.REFUSED) }
    }

    internal fun deliver(fromHost: Boolean, bytes: ByteArray) {
        if (!established || closedLink) return
        val payload: ByteArray
        if (fromHost) {
            sentByHost.add(bytes)
            payload = tamperHostToGuest?.invoke(bytes) ?: bytes
        } else {
            sentByGuest.add(bytes)
            payload = bytes
        }
        if (muted) return
        val target = if (fromHost) guest else host
        queue.addLast { target.receiver?.onMessage(payload) }
    }

    internal fun closedBy(byHost: Boolean) {
        if (!established || closedLink) return
        closedLink = true
        val other = if (byHost) guest else host
        queue.addLast { other.receiver?.onDisconnected() }
    }
}

class FakeTransport(private val link: FakeLink, private val isHost: Boolean) : GameTransport {
    var receiver: TransportListener? = null
    var accepted = false
    var rejected = false
    var closeCalls = 0

    override fun setListener(listener: TransportListener?) {
        this.receiver = listener
    }

    override fun startHosting(local: RemotePlayer) {
        link.hostPlayer = local
        link.search()
    }

    override fun startDiscovery() {
        link.guestSearching = true
        link.search()
    }

    override fun connectTo(endpointId: String, local: RemotePlayer) {
        link.guestPlayer = local
        link.initiate()
    }

    override fun acceptConnection() {
        accepted = true
        link.tryEstablish()
    }

    override fun rejectConnection() {
        rejected = true
        link.rejectedBy(isHost)
    }

    override fun send(bytes: ByteArray) {
        link.deliver(isHost, bytes)
    }

    override fun stopSearching() = Unit

    override fun close() {
        closeCalls += 1
        link.closedBy(isHost)
    }
}
