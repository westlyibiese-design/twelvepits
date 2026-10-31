package com.westly.twelvepits.feature.game.multiplayer

import android.content.Context
import android.os.SystemClock
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * One visit to the Two phones mode: the connection, the lobby, and (after the game starts) the match.
 * It lives from the Two phones screen until the players are back at the main menu, so it survives the
 * move from the setup screen to the game screen and on to the result screen. Main thread only.
 */
class PhonesSession(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transport: GameTransport = NearbyTransport(context)

    val lobby: PhonesLobby = PhonesLobby(
        transport = transport,
        clock = { SystemClock.elapsedRealtime() },
        appVersionCode = readVersionCode(context),
        gameIds = { UUID.randomUUID().toString().take(8) },
    )

    /** The match, once the first game has started. */
    val controller: MatchController?
        get() = lobby.controller

    private var disposed = false

    init {
        scope.launch {
            while (true) {
                delay(TICK_MS)
                lobby.tick()
                lobby.controller?.tick()
            }
        }
    }

    /** Ends everything: the other phone is told, the connection is closed, the timer stops. */
    fun dispose() {
        if (disposed) return
        disposed = true
        lobby.controller?.leave()
        lobby.cancel()
        scope.cancel()
    }

    private fun readVersionCode(context: Context): Int =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info).toInt()
        }.getOrDefault(0)

    private companion object {
        const val TICK_MS = 1_000L
    }
}
