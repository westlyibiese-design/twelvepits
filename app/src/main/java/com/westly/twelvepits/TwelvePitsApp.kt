package com.westly.twelvepits

import android.app.Application
import android.util.Log
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.storage.PreferencesRawGameStorage
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.feature.game.data.SavedGameStore
import com.westly.twelvepits.feature.result.GameResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the objects every screen shares. Created once with the process. */
class AppContainer(app: Application) {
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
