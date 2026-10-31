package com.westly.twelvepits.feature.game.multiplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * The only class that uses Google's Nearby Connections. It carries the bytes of [GameTransport] between
 * exactly two phones (the point-to-point strategy). All callbacks arrive on the main thread.
 */
class NearbyTransport(context: Context) : GameTransport {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: TransportListener? = null

    /** The phone on the other end, from the moment a connection starts until it is closed. */
    private var endpointId: String? = null
    private var connected = false

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            val current = endpointId
            if (current != null && current != id) {
                client.rejectConnection(id)
                return
            }
            endpointId = id
            client.stopAdvertising()
            client.stopDiscovery()
            listener?.onConnectionInitiated(info.authenticationDigits, EndpointNames.decode(info.endpointName))
        }

        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (id != endpointId) return
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connected = true
                    listener?.onConnectionEstablished()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> failConnection(ConnectFailure.REFUSED)
                else -> failConnection(ConnectFailure.ERROR)
            }
        }

        override fun onDisconnected(id: String) {
            if (id != endpointId) return
            val wasConnected = connected
            endpointId = null
            connected = false
            if (wasConnected) {
                listener?.onDisconnected()
            } else {
                listener?.onConnectionFailed(ConnectFailure.ERROR)
            }
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            listener?.onHostFound(FoundHost(id, EndpointNames.decode(info.endpointName)))
        }

        override fun onEndpointLost(id: String) {
            listener?.onHostLost(id)
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (id != endpointId) return
            val bytes = payload.asBytes() ?: return
            listener?.onMessage(bytes)
        }

        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {
            // Messages are small and arrive whole. There is nothing to follow.
        }
    }

    override fun setListener(listener: TransportListener?) {
        this.listener = listener
    }

    override fun startHosting(local: RemotePlayer) {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startAdvertising(EndpointNames.encode(local), SERVICE_ID, lifecycle, options)
            .addOnFailureListener { listener?.onConnectionFailed(ConnectFailure.START_FAILED) }
    }

    override fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startDiscovery(SERVICE_ID, discovery, options)
            .addOnFailureListener { listener?.onConnectionFailed(ConnectFailure.START_FAILED) }
    }

    override fun connectTo(endpointId: String, local: RemotePlayer) {
        this.endpointId = endpointId
        // Looking is over as soon as a host is chosen.
        client.stopDiscovery()
        client.requestConnection(EndpointNames.encode(local), endpointId, lifecycle)
            .addOnFailureListener { failConnection(ConnectFailure.ERROR) }
    }

    override fun acceptConnection() {
        val id = endpointId ?: return
        client.acceptConnection(id, payloads)
    }

    override fun rejectConnection() {
        val id = endpointId ?: return
        client.rejectConnection(id)
    }

    override fun send(bytes: ByteArray) {
        val id = endpointId ?: return
        client.sendPayload(id, Payload.fromBytes(bytes))
    }

    override fun stopSearching() {
        client.stopAdvertising()
        client.stopDiscovery()
    }

    override fun close() {
        val id = endpointId
        endpointId = null
        connected = false
        client.stopAdvertising()
        client.stopDiscovery()
        // A goodbye message may have been sent a moment ago: give it time to leave before the link is cut.
        handler.postDelayed(
            {
                if (id != null) client.disconnectFromEndpoint(id)
                client.stopAllEndpoints()
            },
            CLOSE_DELAY_MS,
        )
    }

    private fun failConnection(failure: ConnectFailure) {
        endpointId = null
        connected = false
        listener?.onConnectionFailed(failure)
    }

    private companion object {
        /** Both phones use the same service id, so they only find each other. */
        const val SERVICE_ID = "com.westly.twelvepits"
        const val CLOSE_DELAY_MS = 400L
    }
}
