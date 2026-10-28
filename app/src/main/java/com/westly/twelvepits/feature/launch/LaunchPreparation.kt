package com.westly.twelvepits.feature.launch

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import com.westly.twelvepits.R
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.storage.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val SOUNDS_TIMEOUT_MS = 2500L
private const val BOARD_GEOMETRY_FILE = "board_geometry.json"

/**
 * Does the real work behind the loading bar and reports each finished step.
 * A step that fails is still counted as finished: the game must never be stuck on the launch screen.
 */
suspend fun prepareApp(
    context: Context,
    settings: SettingsStore,
    sound: SoundManager,
    onStepDone: (PrepStep) -> Unit,
) {
    withContext(Dispatchers.IO) {
        runCatching { settings.preload() }
    }
    onStepDone(PrepStep.SETTINGS_READ)

    withContext(Dispatchers.IO) {
        runCatching {
            context.assets.open(BOARD_GEOMETRY_FILE).bufferedReader().use { JSONObject(it.readText()) }
        }
    }
    onStepDone(PrepStep.GEOMETRY_PARSED)

    withContext(Dispatchers.IO) {
        runCatching { ResourcesCompat.getFont(context, R.font.cinzel) }
    }
    onStepDone(PrepStep.FONTS_LOADED)

    withTimeoutOrNull(SOUNDS_TIMEOUT_MS) { sound.soundsReady.first { it } }
    onStepDone(PrepStep.SOUNDS_LOADED)
}
