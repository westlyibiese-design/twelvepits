package com.westly.twelvepits.feature.menu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.theme.GoldButton
import com.westly.twelvepits.core.theme.Ink
import com.westly.twelvepits.core.theme.MaxContentWidth
import com.westly.twelvepits.core.theme.OutlinedGoldButton
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.labelRes
import com.westly.twelvepits.feature.game.data.SavedGame
import com.westly.twelvepits.feature.game.data.SavedGameStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REVEAL_START_DELAY_MS = 350L
private const val REVEAL_STEP_MS = 120L
private const val REVEAL_FADE_MS = 450

@Composable
fun MainMenuScreen(
    savedGames: SavedGameStore,
    onContinue: () -> Unit,
    onPlay: () -> Unit,
    onHowToPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    // Continue is shown only while a valid saved game exists.
    val savedGame by remember(savedGames) { savedGames.savedGame }
        .collectAsStateWithLifecycle(initialValue = null)

    // Controls fade in one after another once the cross-fade from the launch screen is under way.
    val reveal = remember { List(4) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        delay(REVEAL_START_DELAY_MS)
        reveal.forEachIndexed { index, fade ->
            launch {
                delay(index * REVEAL_STEP_MS)
                fade.animateTo(1f, tween(REVEAL_FADE_MS))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Ink)) {
        Image(
            painter = painterResource(R.drawable.menu_hero),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // Soft shade: dark at the top 28% and at the bottom 30%.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink.copy(alpha = 0.85f),
                        0.28f to Color.Transparent,
                        0.70f to Color.Transparent,
                        1f to Ink.copy(alpha = 0.90f),
                    ),
                ),
        )
        Image(
            painter = painterResource(R.drawable.title_logo),
            contentDescription = stringResource(R.string.launch_logo_description),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.06f)
                .fillMaxWidth(0.72f)
                .widthIn(max = MaxContentWidth * 0.72f),
            contentScale = ContentScale.FillWidth,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                // With the largest system font the buttons scroll instead of running over the logo.
                .heightIn(max = maxHeight * 0.72f)
                .verticalScroll(rememberScrollState())
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val saved = savedGame
            if (saved != null) {
                Column(
                    modifier = Modifier.alpha(reveal[0].value),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GoldButton(text = stringResource(R.string.menu_continue), onClick = onContinue)
                    BasicText(
                        text = continueSummary(saved),
                        style = TwelvePitsType.label.copy(textAlign = TextAlign.Center),
                    )
                }
            }
            GoldButton(
                text = stringResource(R.string.menu_play),
                onClick = onPlay,
                modifier = Modifier.alpha(reveal[1].value),
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).alpha(reveal[2].value),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedGoldButton(
                    text = stringResource(R.string.menu_how_to_play),
                    onClick = onHowToPlay,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                OutlinedGoldButton(
                    text = stringResource(R.string.menu_settings),
                    onClick = onSettings,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            BasicText(
                text = stringResource(R.string.publisher),
                style = TwelvePitsType.small.copy(textAlign = TextAlign.Center),
                modifier = Modifier.alpha(reveal[3].value).padding(top = 4.dp),
            )
        }
    }
}

/** The small line under Continue, for example "Against the computer · Normal · Move 14". */
@Composable
private fun continueSummary(saved: SavedGame): String {
    val mode = stringResource(saved.config.mode.labelRes)
    val move = saved.state.moveNumber
    return if (saved.config.mode == GameMode.VS_COMPUTER) {
        stringResource(
            R.string.continue_summary_computer,
            mode,
            stringResource(saved.config.difficulty.labelRes),
            move,
        )
    } else {
        stringResource(R.string.continue_summary_other, mode, move)
    }
}
