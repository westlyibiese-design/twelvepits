package com.westly.twelvepits

import android.app.Application
import android.util.Log
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.storage.PreferencesRawGameStorage
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.feature.game.data.SavedGameStore
import com.westly.twelvepits.feature.game.multiplayer.PhonesSession
import com.westly.twelvepits.feature.result.GameResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the objects every screen shares. Created once with the process. */
class AppContainer(private val app: Application) {
    /** Lives as long as the app. Saving uses it, so a write is never lost when a screen closes. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore = SettingsStore(app)
    val sound: SoundManager = SoundManager(app, settings)

    /** The saved game, the scores and the starter record. */
    val savedGames: SavedGameStore = SavedGameStore(
        storage = PreferencesRawGameStorage(app),
        scope = applicationScope,
        onError = { message, error -> Log.e(TAG, message, error) },
    )

    /**
     * The result of the game that has just finished, for the result screen. It is kept in memory only,
     * so a result is never shown again after the app was closed. Main thread only.
     */
    var pendingResult: GameResult? = null

    /**
     * The Two phones session: the connection, the lobby and the match. It lives from the Two phones
     * screen until the players are back at the main menu. Main thread only.
     */
    var phonesSession: PhonesSession? = null
        private set

    /** The session of this visit to Two phones. Made on the first call. */
    fun openPhonesSession(): PhonesSession {
        val existing = phonesSession
        if (existing != null) return existing
        val created = PhonesSession(app)
        phonesSession = created
        return created
    }

    /** Tells the other phone, closes the connection and forgets the session. Safe to call when there is none. */
    fun closePhonesSession() {
        phonesSession?.dispose()
        phonesSession = null
    }

    init {
        // A saved game that cannot be read is deleted now, so it never shows a Continue button.
        savedGames.discardDamagedSave()
    }

    private companion object {
        const val TAG = "TwelvePits"
    }
}

class TwelvePitsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
