package com.westly.twelvepits.feature.game.multiplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
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

    /** Counts every new start and every close, so a delayed clean-up never cuts a newer search or connection. */
    private var generation = 0

    override var lastFailureCode: Int? = null
        private set

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
                else -> failConnection(ConnectFailure.ERROR, result.status.statusCode)
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
        generation += 1
        lastFailureCode = null
        advertise(local, retry = true)
    }

    private fun advertise(local: RemotePlayer, retry: Boolean) {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startAdvertising(EndpointNames.encode(local), SERVICE_ID, lifecycle, options)
            .addOnFailureListener { error ->
                val code = statusOf(error)
                if (retry && code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    // An earlier session is still being closed. Close it once more and start again.
                    client.stopAdvertising()
                    val token = generation
                    handler.postDelayed({ if (generation == token) advertise(local, retry = false) }, RETRY_DELAY_MS)
                } else {
                    reportStartFailure("startAdvertising", error, code)
                }
            }
    }

    override fun startDiscovery() {
        generation += 1
        lastFailureCode = null
        discover(retry = true)
    }

    private fun discover(retry: Boolean) {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
        client.startDiscovery(SERVICE_ID, discovery, options)
            .addOnFailureListener { error ->
                val code = statusOf(error)
                if (retry && code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                    client.stopDiscovery()
                    val token = generation
                    handler.postDelayed({ if (generation == token) discover(retry = false) }, RETRY_DELAY_MS)
                } else {
                    reportStartFailure("startDiscovery", error, code)
                }
            }
    }

    override fun connectTo(endpointId: String, local: RemotePlayer) {
        generation += 1
        lastFailureCode = null
        this.endpointId = endpointId
        // Looking is over as soon as a host is chosen.
        client.stopDiscovery()
        client.requestConnection(EndpointNames.encode(local), endpointId, lifecycle)
            .addOnFailureListener { error -> failConnection(ConnectFailure.ERROR, statusOf(error), error) }
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
        generation += 1
        val token = generation
        // A goodbye message may have been sent a moment ago: give it time to leave before the link is cut.
        handler.postDelayed(
            {
                if (id != null) client.disconnectFromEndpoint(id)
                // A new search or connection may have started during the wait: it must not be cut.
                if (generation == token) client.stopAllEndpoints()
            },
            CLOSE_DELAY_MS,
        )
    }

    private fun failConnection(failure: ConnectFailure, code: Int? = null, error: Throwable? = null) {
        if (code != null) {
            lastFailureCode = code
            Log.e(TAG, "The connection failed with status $code", error)
        }
        endpointId = null
        connected = false
        listener?.onConnectionFailed(failure)
    }

    /** The code of a failed call: the real status when Google's service gave one, else the general error. */
    private fun statusOf(error: Exception): Int =
        (error as? ApiException)?.statusCode ?: CommonStatusCodes.ERROR

    private fun reportStartFailure(step: String, error: Exception, code: Int) {
        // Logged as an error so it shows in the diagnostics, with the status code that names the problem.
        Log.e(TAG, "$step failed with status $code: ${error.message}", error)
        lastFailureCode = code
        listener?.onConnectionFailed(ConnectFailure.START_FAILED)
    }

    private companion object {
        /** Both phones use the same service id, so they only find each other. */
        const val SERVICE_ID = "com.westly.twelvepits"
        const val CLOSE_DELAY_MS = 400L
        const val RETRY_DELAY_MS = 500L
        const val TAG = "TwelvePits"
    }
}
