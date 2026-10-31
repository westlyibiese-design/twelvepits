package com.westly.twelvepits.feature.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.westly.twelvepits.R
import com.westly.twelvepits.core.audio.HapticEffect
import com.westly.twelvepits.core.audio.Sfx
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.theme.GameDialog
import com.westly.twelvepits.feature.game.multiplayer.EndReason
import com.westly.twelvepits.feature.game.multiplayer.RematchState
import com.westly.twelvepits.feature.game.multiplayer.endReasonText
import com.westly.twelvepits.core.theme.GoldButton
import com.westly.twelvepits.core.theme.GoldLine
import com.westly.twelvepits.core.theme.Ink
import com.westly.twelvepits.core.theme.MaxContentWidth
import com.westly.twelvepits.core.theme.OutlinedGoldButton
import com.westly.twelvepits.core.theme.ResultCardFill
import com.westly.twelvepits.core.theme.ResultDimens
import com.westly.twelvepits.core.theme.ResultType
import com.westly.twelvepits.core.theme.Scrim
import com.westly.twelvepits.core.theme.TrophyGlowCenter
import com.westly.twelvepits.core.theme.TrophyGlowEdge
import com.westly.twelvepits.core.theme.drawableRes
import com.westly.twelvepits.core.theme.labelRes
import kotlinx.coroutines.delay
import com.westly.twelvepits.core.model.Player

/** Length of `sfx_victory` and `sfx_defeat`: the menu music comes back once the sound has played. */
private const val VICTORY_SOUND_MS = 2500L
private const val DEFEAT_SOUND_MS = 2400L

/** What the result screen needs on two phones: the play-again question between the two players. */
class PhonesResultState(
    val rematch: RematchState,
    /** Set when the other player left or the connection broke: there will be no rematch. */
    val endReason: EndReason?,
    val opponentName: String,
    val onCancelRequest: () -> Unit,
    val onAccept: () -> Unit,
    val onDecline: () -> Unit,
    val onDismissDeclined: () -> Unit,
)

/**
 * The result screen (upright). Shown once for each finished game: a trophy (or the computer's
 * portrait after a defeat), the title, the final score, the statistics, and the two buttons.
 * On two phones [phones] is set: PLAY AGAIN asks the other player, who can say yes or no.
 */
@Composable
fun ResultScreen(
    result: GameResult,
    sound: SoundManager,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
    phones: PhonesResultState? = null,
) {
    val isDefeat = result.headline == ResultHeadline.DEFEAT

    // The game music has stopped. Play the result sound, then bring the menu music back.
    LaunchedEffect(result) {
        sound.playSfx(if (isDefeat) Sfx.DEFEAT else Sfx.VICTORY)
        sound.vibrate(if (isDefeat) HapticEffect.DEFEAT else HapticEffect.VICTORY)
        delay(if (isDefeat) DEFEAT_SOUND_MS else VICTORY_SOUND_MS)
        sound.startMenuMusic()
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink)) {
        Image(
            painter = painterResource(R.drawable.menu_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().background(Scrim))
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxSize()
                    .padding(horizontal = ResultDimens.ContentMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = maxHeight)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(
                            ResultDimens.BlockGap,
                            Alignment.CenterVertically,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Hero(isDefeat = isDefeat, defeatAvatar = defeatAvatarOf(result))
                        Headline(result)
                        ScoreCard(result)
                        StatsCard(result)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = ResultDimens.ButtonAreaPadding),
                    verticalArrangement = Arrangement.spacedBy(ResultDimens.ButtonGap),
                ) {
                    if (phones == null) {
                        GoldButton(text = stringResource(R.string.result_play_again), onClick = onPlayAgain)
                    } else {
                        PhonesButtons(phones = phones, onPlayAgain = onPlayAgain)
                    }
                    OutlinedGoldButton(
                        text = stringResource(R.string.result_main_menu),
                        onClick = onMainMenu,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** The portrait shown after a defeat: the computer's, or on two phones the winner's. */
private fun defeatAvatarOf(result: GameResult): AvatarId = when {
    result.config.mode != GameMode.TWO_PHONES -> AvatarId.LEOPARD
    result.winner == Player.ONE -> result.config.player1Avatar
    else -> result.config.player2Avatar
}

/** The buttons and questions of the play-again step between two phones. */
@Composable
private fun PhonesButtons(phones: PhonesResultState, onPlayAgain: () -> Unit) {
    if (phones.endReason != null) {
        // The other player is gone: there is nobody to play again with.
        BasicText(
            text = endReasonText(phones.endReason, phones.opponentName),
            style = ResultType.subline.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        return
    }
    when (phones.rematch) {
        RematchState.MINE_PENDING -> {
            BasicText(
                text = stringResource(R.string.phones_rematch_waiting, phones.opponentName),
                style = ResultType.subline.copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
            OutlinedGoldButton(
                text = stringResource(R.string.dialog_cancel),
                onClick = phones.onCancelRequest,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> GoldButton(text = stringResource(R.string.result_play_again), onClick = onPlayAgain)
    }
    if (phones.rematch == RematchState.THEIRS_PENDING) {
        GameDialog(
            title = stringResource(R.string.phones_rematch_request_title, phones.opponentName),
            message = stringResource(R.string.phones_rematch_request_message),
            confirmText = stringResource(R.string.phones_rematch_accept),
            dismissText = stringResource(R.string.phones_rematch_decline),
            onConfirm = phones.onAccept,
            onDismiss = phones.onDecline,
        )
    }
    if (phones.rematch == RematchState.DECLINED) {
        GameDialog(
            title = stringResource(R.string.phones_rematch_declined_title),
            message = stringResource(R.string.phones_rematch_declined, phones.opponentName),
            confirmText = stringResource(R.string.dialog_ok),
            onConfirm = phones.onDismissDeclined,
            onDismiss = phones.onDismissDeclined,
        )
    }
}

/** The trophy with a soft emerald glow, or after a defeat a portrait. */
@Composable
private fun Hero(isDefeat: Boolean, defeatAvatar: AvatarId) {
    if (isDefeat) {
        Image(
            painter = painterResource(defeatAvatar.drawableRes),
            contentDescription = stringResource(defeatAvatar.labelRes),
            modifier = Modifier.size(ResultDimens.HeroSize),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(ResultDimens.TrophyGlowSize)
                    .background(
                        Brush.radialGradient(listOf(TrophyGlowCenter, TrophyGlowEdge)),
                        CircleShape,
                    ),
            )
            Image(
                painter = painterResource(R.drawable.trophy),
                contentDescription = stringResource(R.string.result_trophy_description),
                modifier = Modifier.size(ResultDimens.HeroSize),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun Headline(result: GameResult) {
    val title = when (result.headline) {
        ResultHeadline.VICTORY -> stringResource(R.string.result_victory)
        ResultHeadline.DEFEAT -> stringResource(R.string.result_defeat)
        ResultHeadline.WINNER_NAMED -> stringResource(R.string.result_wins, result.winnerName)
    }
    val subline = when (result.headline) {
        ResultHeadline.WINNER_NAMED ->
            stringResource(R.string.result_seeds_captured, result.winnerScore)
        else -> stringResource(R.string.result_captured, result.winnerName, result.winnerScore)
    }
    // A live region: a screen reader announces the result as soon as it is shown.
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ResultDimens.TitleGap),
    ) {
        BasicText(text = title, style = ResultType.headline.copy(textAlign = TextAlign.Center))
        BasicText(text = subline, style = ResultType.subline.copy(textAlign = TextAlign.Center))
    }
}

@Composable
private fun ResultCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(ResultDimens.CardCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ResultCardFill)
            .border(BorderStroke(ResultDimens.CardBorder, GoldLine), shape)
            .padding(ResultDimens.CardPadding),
    ) {
        content()
    }
}

/** Both players with their portrait and name, and the final score "27 to 21". The winner's number is brighter. */
@Composable
private fun ScoreCard(result: GameResult) {
    ResultCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreColumn(
                name = result.config.player1Name,
                avatar = result.config.player1Avatar,
                score = result.scoreOne,
                isWinner = result.winner == Player.ONE,
                modifier = Modifier.weight(1f),
            )
            BasicText(
                text = stringResource(R.string.result_to),
                style = ResultType.scoreTo,
            )
            ScoreColumn(
                name = result.config.player2Name,
                avatar = result.config.player2Avatar,
                score = result.scoreTwo,
                isWinner = result.winner == Player.TWO,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScoreColumn(
    name: String,
    avatar: AvatarId,
    score: Int,
    isWinner: Boolean,
    modifier: Modifier = Modifier,
) {
    val numberStyle: TextStyle = if (isWinner) ResultType.scoreNumber else ResultType.scoreNumberDim
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ResultDimens.ScoreGap),
    ) {
        Image(
            painter = painterResource(avatar.drawableRes),
            contentDescription = null,
            modifier = Modifier.size(ResultDimens.AvatarSize),
            contentScale = ContentScale.Fit,
        )
        BasicText(
            text = name,
            style = ResultType.scoreName.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(text = score.toString(), style = numberStyle)
    }
}

/** Moves, the winner's best capture in one turn, and the play time as mm:ss. */
@Composable
private fun StatsCard(result: GameResult) {
    ResultCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            StatColumn(
                value = result.moves.toString(),
                label = stringResource(R.string.result_moves),
                modifier = Modifier.weight(1f),
            )
            StatColumn(
                value = result.bestCapture.toString(),
                label = stringResource(R.string.result_best_capture),
                modifier = Modifier.weight(1f),
            )
            StatColumn(
                value = formatPlayTime(result.elapsedMs),
                label = stringResource(R.string.result_time),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(text = value, style = ResultType.statValue.copy(textAlign = TextAlign.Center))
        BasicText(text = label, style = ResultType.statLabel.copy(textAlign = TextAlign.Center))
    }
}
