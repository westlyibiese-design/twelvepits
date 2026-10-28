package com.westly.twelvepits.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.SeedSpeed
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "twelve_pits_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Names and avatars chosen on the game setup screen. A null name means nothing was saved yet. */
data class SetupChoices(
    val computerPlayerName: String?,
    val computerPlayerAvatar: AvatarId?,
    val difficulty: Difficulty,
    val player1Name: String?,
    val player1Avatar: AvatarId?,
    val player2Name: String?,
    val player2Avatar: AvatarId?,
)

/**
 * All saved settings, read once at start and exposed as Flows.
 * Phase 4 (the game screen) reads: [seedHopSeconds], [musicEnabled], [musicVolume], [soundEffectsEnabled].
 */
class SettingsStore(context: Context) {
    private val store: DataStore<Preferences> = context.applicationContext.settingsDataStore

    private val data: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val musicEnabled: Flow<Boolean> =
        data.map { it[MUSIC_ENABLED] ?: DEFAULT_MUSIC_ENABLED }.distinctUntilChanged()

    val musicVolume: Flow<Float> =
        data.map { (it[MUSIC_VOLUME] ?: DEFAULT_MUSIC_VOLUME).coerceIn(0f, 1f) }.distinctUntilChanged()

    val soundEffectsEnabled: Flow<Boolean> =
        data.map { it[SOUND_EFFECTS_ENABLED] ?: DEFAULT_SOUND_EFFECTS_ENABLED }.distinctUntilChanged()

    val vibrationEnabled: Flow<Boolean> =
        data.map { it[VIBRATION_ENABLED] ?: DEFAULT_VIBRATION_ENABLED }.distinctUntilChanged()

    /** Per-seed hop time in seconds: 0.65 (Slow), 0.40 (Normal) or 0.20 (Fast). */
    val seedHopSeconds: Flow<Float> =
        data.map { SeedSpeed.fromHopSeconds(it[SEED_HOP_SECONDS]).hopSeconds }.distinctUntilChanged()

    val setupChoices: Flow<SetupChoices> = data.map { prefs ->
        SetupChoices(
            computerPlayerName = prefs[VS_NAME],
            computerPlayerAvatar = parseAvatar(prefs[VS_AVATAR]),
            difficulty = parseDifficulty(prefs[DIFFICULTY]),
            player1Name = prefs[P1_NAME],
            player1Avatar = parseAvatar(prefs[P1_AVATAR]),
            player2Name = prefs[P2_NAME],
            player2Avatar = parseAvatar(prefs[P2_AVATAR]),
        )
    }

    /** Reads the saved file once so later reads are instant. Used by the launch screen. */
    suspend fun preload() {
        data.first()
    }

    suspend fun setMusicEnabled(value: Boolean) {
        update { it[MUSIC_ENABLED] = value }
    }

    suspend fun setMusicVolume(value: Float) {
        update { it[MUSIC_VOLUME] = value.coerceIn(0f, 1f) }
    }

    suspend fun setSoundEffectsEnabled(value: Boolean) {
        update { it[SOUND_EFFECTS_ENABLED] = value }
    }

    suspend fun setVibrationEnabled(value: Boolean) {
        update { it[VIBRATION_ENABLED] = value }
    }

    suspend fun setSeedSpeed(speed: SeedSpeed) {
        update { it[SEED_HOP_SECONDS] = speed.hopSeconds }
    }

    suspend fun saveComputerSetup(name: String, avatar: AvatarId, difficulty: Difficulty) {
        update {
            it[VS_NAME] = name
            it[VS_AVATAR] = avatar.name
            it[DIFFICULTY] = difficulty.name
        }
    }

    suspend fun saveTwoPlayerSetup(
        player1Name: String,
        player1Avatar: AvatarId,
        player2Name: String,
        player2Avatar: AvatarId,
    ) {
        update {
            it[P1_NAME] = player1Name
            it[P1_AVATAR] = player1Avatar.name
            it[P2_NAME] = player2Name
            it[P2_AVATAR] = player2Avatar.name
        }
    }

    /** Saves even if the screen that asked for it has already closed. */
    private suspend fun update(change: (MutablePreferences) -> Unit) {
        withContext(NonCancellable) {
            store.edit { change(it) }
        }
    }

    private fun parseAvatar(raw: String?): AvatarId? =
        AvatarId.entries.firstOrNull { it.name == raw }

    private fun parseDifficulty(raw: String?): Difficulty =
        Difficulty.entries.firstOrNull { it.name == raw } ?: DEFAULT_DIFFICULTY

    companion object {
        const val DEFAULT_MUSIC_ENABLED = true
        const val DEFAULT_MUSIC_VOLUME = 0.7f
        const val DEFAULT_SOUND_EFFECTS_ENABLED = true
        const val DEFAULT_VIBRATION_ENABLED = false
        val DEFAULT_DIFFICULTY = Difficulty.NORMAL

        private val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        private val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        private val SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        private val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        private val SEED_HOP_SECONDS = floatPreferencesKey("seed_hop_seconds")
        private val VS_NAME = stringPreferencesKey("setup_vs_name")
        private val VS_AVATAR = stringPreferencesKey("setup_vs_avatar")
        private val DIFFICULTY = stringPreferencesKey("setup_difficulty")
        private val P1_NAME = stringPreferencesKey("setup_p1_name")
        private val P1_AVATAR = stringPreferencesKey("setup_p1_avatar")
        private val P2_NAME = stringPreferencesKey("setup_p2_name")
        private val P2_AVATAR = stringPreferencesKey("setup_p2_avatar")
    }
}
