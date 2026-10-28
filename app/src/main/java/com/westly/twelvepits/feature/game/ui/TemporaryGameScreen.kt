package com.westly.twelvepits.feature.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.theme.ScreenScaffold
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.labelRes

// Replaced in Phase 4
/** Temporary screen after START GAME: shows the chosen configuration as text. There is no game code yet. */
@Composable
fun TemporaryGameScreen(
    config: GameConfig,
    onBack: () -> Unit,
) {
    val modeName = stringResource(
        when (config.mode) {
            GameMode.VS_COMPUTER -> R.string.mode_name_vs_computer
            GameMode.TWO_PLAYERS_ONE_PHONE -> R.string.mode_name_two_players
            GameMode.TWO_PHONES -> R.string.mode_name_two_phones
        },
    )
    ScreenScaffold(title = stringResource(R.string.temp_game_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(
                text = stringResource(R.string.temp_game_note),
                style = TwelvePitsType.bodyMuted,
            )
            BasicText(
                text = stringResource(R.string.temp_game_mode, modeName),
                style = TwelvePitsType.body,
            )
            if (config.mode == GameMode.VS_COMPUTER) {
                val difficultyName = stringResource(
                    when (config.difficulty) {
                        Difficulty.EASY -> R.string.difficulty_easy
                        Difficulty.NORMAL -> R.string.difficulty_normal
                        Difficulty.HARD -> R.string.difficulty_hard
                    },
                )
                BasicText(
                    text = stringResource(R.string.temp_game_difficulty, difficultyName),
                    style = TwelvePitsType.body,
                )
            }
            BasicText(
                text = stringResource(
                    R.string.temp_game_player1,
                    config.player1Name,
                    stringResource(config.player1Avatar.labelRes),
                ),
                style = TwelvePitsType.body,
            )
            BasicText(
                text = stringResource(
                    R.string.temp_game_player2,
                    config.player2Name,
                    stringResource(config.player2Avatar.labelRes),
                ),
                style = TwelvePitsType.body,
            )
        }
    }
}
