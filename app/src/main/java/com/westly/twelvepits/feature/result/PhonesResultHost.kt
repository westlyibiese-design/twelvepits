package com.westly.twelvepits.feature.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.multiplayer.MatchController

/**
 * The result screen of a two-phone game. It follows the match: when the other player accepts the
 * rematch (or asks and this player accepts), a new game starts and [onNewGame] opens its screen.
 */
@Composable
fun PhonesResultHost(
    result: GameResult,
    match: MatchController,
    sound: SoundManager,
    onNewGame: (GameConfig) -> Unit,
    onMainMenu: () -> Unit,
) {
    val snapshot by match.snapshot.collectAsStateWithLifecycle()
    val opponentName = if (match.role == Player.ONE) result.config.player2Name else result.config.player1Name

    // A game number higher than the game that was just played means a rematch has started.
    LaunchedEffect(snapshot.gameNumber) {
        val next = match.config
        if (snapshot.gameNumber > result.matchGameNumber && next != null) onNewGame(next)
    }

    ResultScreen(
        result = result,
        sound = sound,
        onPlayAgain = match::requestRematch,
        onMainMenu = onMainMenu,
        phones = PhonesResultState(
            rematch = snapshot.rematch,
            endReason = snapshot.endReason,
            opponentName = opponentName,
            onCancelRequest = match::cancelRematchRequest,
            onAccept = match::acceptRematch,
            onDecline = match::declineRematch,
            onDismissDeclined = match::dismissDeclined,
        ),
    )
}
