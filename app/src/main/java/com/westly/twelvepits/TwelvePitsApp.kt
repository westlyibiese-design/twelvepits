package com.westly.twelvepits

import android.app.Application
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.feature.game.data.NoSavedGameStore
import com.westly.twelvepits.feature.game.data.SavedGameStore

/** Holds the objects every screen shares. Created once with the process. */
class AppContainer(app: Application) {
    val settings: SettingsStore = SettingsStore(app)
    val sound: SoundManager = SoundManager(app, settings)
    val savedGames: SavedGameStore = NoSavedGameStore()
}

class TwelvePitsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
