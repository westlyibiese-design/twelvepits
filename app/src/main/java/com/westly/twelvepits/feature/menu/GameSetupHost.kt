package com.westly.twelvepits.feature.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.storage.SettingsStore

/**
 * The setup screen. START GAME goes straight to the game: a game in progress of the same kind (the same
 * computer level, or Two players) comes back by itself and a new one starts only when there is none.
 * There is no question about replacing a saved game; a player starts afresh in Settings.
 */
@Composable
fun GameSetupHost(
    kind: SetupKind,
    settings: SettingsStore,
    onBack: () -> Unit,
    onStartGame: (GameConfig) -> Unit,
) {
    // One tap only: a second tap while the game screen is opening does nothing.
    var opened by remember { mutableStateOf(false) }

    GameSetupScreen(
        kind = kind,
        settings = settings,
        onBack = onBack,
        onStart = { config ->
            if (!opened) {
                opened = true
                onStartGame(config)
            }
        },
    )
}
