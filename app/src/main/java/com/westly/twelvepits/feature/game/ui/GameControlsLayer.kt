package com.westly.twelvepits.feature.game.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.westly.twelvepits.R
import com.westly.twelvepits.core.theme.BackGlyph
import com.westly.twelvepits.core.theme.ControlFill
import com.westly.twelvepits.core.theme.GameControlButton
import com.westly.twelvepits.core.theme.GameDimens
import com.westly.twelvepits.core.theme.GoldLine
import com.westly.twelvepits.core.theme.PauseGlyph
import com.westly.twelvepits.core.theme.SoundGlyph
import com.westly.twelvepits.core.theme.TopShade
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.tapClickable
import kotlin.math.max

private const val CONTROLS_FADE_MS = 250
private const val BANNER_FADE_MS = 200

/**
 * The controls of the game screen: one faint pause icon in the top-right corner, and after a tap on
 * the table Back (top-left), Pause and Sound (top-right) on a soft top shade. Everything stays
 * inside the safe area. The layer itself never takes a tap: only the buttons do.
 */
@Composable
internal fun GameControlsLayer(
    visible: Boolean,
    soundMuted: Boolean,
    insets: SafeInsets,
    onShow: () -> Unit,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(CONTROLS_FADE_MS),
        label = "controlsFade",
    )
    fun margin(minimum: Dp, inset: Float): Dp = with(density) { max(minimum.toPx(), inset).toDp() }

    val left = margin(GameDimens.ControlEdgeMargin, insets.left)
    val right = margin(GameDimens.ControlEdgeMargin, insets.right)
    val top = margin(GameDimens.ControlTopMargin, insets.top)
    val cueRight = margin(GameDimens.CueEdgeMargin, insets.right)
    val cueTop = margin(GameDimens.CueTopMargin, insets.top)
    val cueDescription = stringResource(R.string.game_show_controls_description)
    val shadeHeight = GameDimens.TopShadeHeight + with(density) { insets.top.toDp() }

    Box(modifier = modifier.fillMaxSize()) {
        if (fade > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(shadeHeight)
                    .alpha(fade)
                    .background(Brush.verticalGradient(listOf(TopShade, Color.Transparent))),
            )
        }
        if (fade < 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = cueTop, end = cueRight)
                    .size(GameDimens.ControlSize)
                    .alpha(GameDimens.CUE_ALPHA * (1f - fade))
                    .semantics { contentDescription = cueDescription }
                    .tapClickable(enabled = !visible, onClick = onShow),
                contentAlignment = Alignment.Center,
            ) {
                PauseGlyph()
            }
        }
        if (fade > 0f) {
            GameControlButton(
                contentDescription = stringResource(R.string.game_back_description),
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = left, top = top)
                    .alpha(fade),
            ) {
                BackGlyph()
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = right, top = top)
                    .alpha(fade),
                horizontalArrangement = Arrangement.spacedBy(GameDimens.ControlGap),
            ) {
                GameControlButton(
                    contentDescription = stringResource(R.string.game_pause_description),
                    onClick = onPause,
                ) {
                    PauseGlyph()
                }
                GameControlButton(
                    contentDescription = stringResource(R.string.game_sound_description),
                    onClick = onSound,
                ) {
                    SoundGlyph(muted = soundMuted)
                }
            }
        }
    }
}

/** "<name> has no seeds. Turn skipped." at the top centre of the table. */
@Composable
internal fun TurnSkippedBanner(
    playerName: String?,
    insets: SafeInsets,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val lastName = remember { mutableStateOf("") }
    if (playerName != null) lastName.value = playerName
    val top = with(density) { max(GameDimens.BannerMargin.toPx(), insets.top).toDp() }
    val shape = RoundedCornerShape(GameDimens.BannerCorner)
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = playerName != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
            enter = fadeIn(tween(BANNER_FADE_MS)),
            exit = fadeOut(tween(BANNER_FADE_MS)),
        ) {
            BasicText(
                text = stringResource(R.string.game_turn_skipped, lastName.value),
                style = TwelvePitsType.body.copy(textAlign = TextAlign.Center),
                modifier = Modifier
                    .clip(shape)
                    .background(ControlFill.copy(alpha = GameDimens.BANNER_FILL_ALPHA))
                    .border(BorderStroke(GameDimens.ControlBorder, GoldLine), shape)
                    .padding(horizontal = GameDimens.BannerPaddingH, vertical = GameDimens.BannerPaddingV),
            )
        }
    }
}
