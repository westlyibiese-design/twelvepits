package com.westly.twelvepits.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.westly.twelvepits.feature.game.data.RawGameData
import com.westly.twelvepits.feature.game.data.RawGameStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// A file that cannot be read is replaced by an empty one: the game starts fresh and never crashes.
private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "twelve_pits_game",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * One DataStore file that holds the saved game, the scores and the starter record together
 * (RULES.md section 10), so a finished game updates all three in one atomic edit.
 * The settings, names and avatars have their own file ([SettingsStore]) and are never touched here.
 */
class PreferencesRawGameStorage(context: Context) : RawGameStorage {
    private val store: DataStore<Preferences> = context.applicationContext.gameDataStore

    override val raw: Flow<RawGameData> = store.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> read(preferences) }

    override suspend fun edit(change: (RawGameData) -> RawGameData) {
        store.edit { preferences ->
            val next = change(read(preferences))
            preferences.write(SAVED_GAME, next.savedGame)
            preferences.write(SCORES, next.scores)
            preferences.write(STARTERS, next.starters)
        }
    }

    private fun read(preferences: Preferences): RawGameData = RawGameData(
        savedGame = preferences[SAVED_GAME],
        scores = preferences[SCORES],
        starters = preferences[STARTERS],
    )

    private fun MutablePreferences.write(key: Preferences.Key<String>, value: String?) {
        if (value == null) {
            remove(key)
        } else {
            this[key] = value
        }
    }

    private companion object {
        val SAVED_GAME = stringPreferencesKey("saved_game")
        val SCORES = stringPreferencesKey("scores")
        val STARTERS = stringPreferencesKey("starters")
    }
}
