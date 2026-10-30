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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.westly.twelvepits.R
import com.westly.twelvepits.core.audio.Sfx
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.model.AvatarId
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

/**
 * The result screen (upright). Shown once for each finished game: a trophy (or the computer's
 * portrait after a defeat), the title, the final score, the statistics, and the two buttons.
 */
@Composable
fun ResultScreen(
    result: GameResult,
    sound: SoundManager,
    onPlayAgain: () -> Unit,
    onMainMenu: () -> Unit,
) {
    val isDefeat = result.headline == ResultHeadline.DEFEAT

    // The game music has stopped. Play the result sound, then bring the menu music back.
    LaunchedEffect(result) {
        sound.playSfx(if (isDefeat) Sfx.DEFEAT else Sfx.VICTORY)
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
                        Hero(isDefeat = isDefeat)
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
                    GoldButton(text = stringResource(R.string.result_play_again), onClick = onPlayAgain)
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

/** The trophy with a soft emerald glow, or after a defeat the computer's portrait. */
@Composable
private fun Hero(isDefeat: Boolean) {
    if (isDefeat) {
        Image(
            painter = painterResource(AvatarId.LEOPARD.drawableRes),
            contentDescription = stringResource(AvatarId.LEOPARD.labelRes),
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
    Column(
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
