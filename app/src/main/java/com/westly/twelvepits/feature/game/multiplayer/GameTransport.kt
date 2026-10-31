package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.feature.menu.MAX_NAME_LENGTH
import com.westly.twelvepits.feature.menu.SelectableAvatars

/** A player as the other phone sees them while connecting. */
data class RemotePlayer(val name: String, val avatar: AvatarId)

/** A game another phone is hosting. */
data class FoundHost(val endpointId: String, val player: RemotePlayer)

enum class ConnectFailure { START_FAILED, REFUSED, ERROR }

/** What the transport tells the game logic. Every call comes on the main thread. */
interface TransportListener {
    fun onHostFound(host: FoundHost) {}
    fun onHostLost(endpointId: String) {}

    /** Both phones show [authCode] and ask their player to compare it. */
    fun onConnectionInitiated(authCode: String, remote: RemotePlayer) {}

    /** Both players said yes: messages can be sent now. */
    fun onConnectionEstablished() {}
    fun onConnectionFailed(failure: ConnectFailure) {}
    fun onMessage(bytes: ByteArray) {}
    fun onDisconnected() {}
}

/**
 * The only thing the game logic knows about the connection between the two phones.
 * [NearbyTransport] is the real one; the tests use a fake one.
 */
interface GameTransport {
    fun setListener(listener: TransportListener?)

    /** Be found by another phone (the host). */
    fun startHosting(local: RemotePlayer)

    /** Look for hosts (the guest). */
    fun startDiscovery()

    fun connectTo(endpointId: String, local: RemotePlayer)
    fun acceptConnection()
    fun rejectConnection()
    fun send(bytes: ByteArray)

    /** Stop being found and stop looking. An open connection stays. */
    fun stopSearching()

    /** Close the connection and stop everything. The transport can be used again afterwards. */
    fun close()
}

/** The name sent while connecting carries the avatar too: "KING|Westly". */
object EndpointNames {
    private const val SEPARATOR = '|'

    fun encode(player: RemotePlayer): String = player.avatar.name + SEPARATOR + player.name

    fun decode(raw: String?): RemotePlayer {
        val text = raw ?: ""
        val cut = text.indexOf(SEPARATOR)
        if (cut > 0) {
            val avatar = SelectableAvatars.firstOrNull { it.name == text.substring(0, cut) }
            val name = text.substring(cut + 1).trim().take(MAX_NAME_LENGTH)
            if (avatar != null && name.isNotEmpty()) return RemotePlayer(name, avatar)
        }
        return RemotePlayer(text.trim().take(MAX_NAME_LENGTH).ifEmpty { "?" }, AvatarId.KING)
    }
}
