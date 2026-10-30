package com.westly.twelvepits.feature.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.core.theme.GameDialog
import com.westly.twelvepits.feature.game.data.SavedGameStore
import kotlinx.coroutines.launch

/**
 * The setup screen with the question that protects a saved game: if the player taps START GAME while a
 * saved game exists, they must choose Replace (the saved game is deleted at once) or Cancel (nothing changes).
 */
@Composable
fun GameSetupHost(
    kind: SetupKind,
    settings: SettingsStore,
    savedGames: SavedGameStore,
    onBack: () -> Unit,
    onStartGame: (GameConfig) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<GameConfig?>(null) }
    var busy by remember { mutableStateOf(false) }

    GameSetupScreen(
        kind = kind,
        settings = settings,
        onBack = onBack,
        onStart = { config ->
            if (!busy) {
                busy = true
                scope.launch {
                    if (savedGames.hasSavedGame()) {
                        pending = config
                    } else {
                        onStartGame(config)
                    }
                }
            }
        },
    )

    val waiting = pending
    if (waiting != null) {
        GameDialog(
            title = stringResource(R.string.replace_title),
            message = stringResource(R.string.replace_message),
            confirmText = stringResource(R.string.replace_confirm),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                scope.launch {
                    savedGames.deleteSavedGame()
                    pending = null
                    onStartGame(waiting)
                }
            },
            onDismiss = {
                pending = null
                busy = false
            },
        )
    }
}
