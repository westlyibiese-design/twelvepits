package com.westly.twelvepits.feature.game.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.westly.twelvepits.R
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.core.theme.Gold
import com.westly.twelvepits.core.theme.GameDimens
import com.westly.twelvepits.core.theme.GoldButton
import com.westly.twelvepits.core.theme.OutlinedGoldButton
import com.westly.twelvepits.core.theme.OverlayScrim
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.Walnut
import com.westly.twelvepits.feature.settings.SettingsControls

/**
 * A panel over the board. It is drawn inside the screen (not as a separate window), so the system
 * bars stay hidden. It covers the whole screen and takes every tap, so nothing behind it can be tapped.
 */
@Composable
private fun OverlayPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(GameDimens.PanelCorner)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OverlayScrim)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(GameDimens.ScreenMargin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = modifier
                .widthIn(max = GameDimens.PanelMaxWidth)
                .fillMaxWidth()
                .clip(shape)
                .background(Walnut)
                .border(BorderStroke(GameDimens.PanelBorder, Gold), shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(GameDimens.PanelPadding),
        ) {
            content()
        }
    }
}

/** The pause menu: title and move counter on the left, the four buttons on the right (a landscape layout). */
@Composable
internal fun PauseOverlay(
    moveNumber: Int,
    difficulty: String?,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onQuit: () -> Unit,
) {
    OverlayPanel {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(GameDimens.PanelGap * 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(GameDimens.ButtonGap, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = stringResource(R.string.pause_title),
                    style = TwelvePitsType.screenTitle.copy(textAlign = TextAlign.Center),
                )
                BasicText(
                    text = stringResource(R.string.pause_move, moveNumber),
                    style = TwelvePitsType.bodyMuted.copy(textAlign = TextAlign.Center),
                )
                if (difficulty != null) {
                    BasicText(
                        text = difficulty,
                        style = TwelvePitsType.bodyMuted.copy(textAlign = TextAlign.Center),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(GameDimens.ButtonGap),
            ) {
                GoldButton(text = stringResource(R.string.pause_resume), onClick = onResume)
                OutlinedGoldButton(
                    text = stringResource(R.string.pause_restart),
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedGoldButton(
                    text = stringResource(R.string.pause_settings),
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedGoldButton(
                    text = stringResource(R.string.pause_quit),
                    onClick = onQuit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The same settings as the Settings screen (same controls, same saved values), in a landscape panel. */
@Composable
internal fun SettingsOverlay(
    settings: SettingsStore,
    sound: SoundManager,
    onDone: () -> Unit,
) {
    OverlayPanel {
        Column(verticalArrangement = Arrangement.spacedBy(GameDimens.PanelGap)) {
            BasicText(
                text = stringResource(R.string.settings_title),
                style = TwelvePitsType.screenTitle,
            )
            SettingsControls(
                settings = settings,
                sound = sound,
                rowMinHeight = GameDimens.SettingsRowMinHeight,
            )
            GoldButton(text = stringResource(R.string.game_settings_done), onClick = onDone)
        }
    }
}

/** A question with a left button (dismiss) and a right button (confirm). */
@Composable
internal fun ConfirmOverlay(
    title: String,
    message: String,
    dismissText: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayPanel {
        Column(verticalArrangement = Arrangement.spacedBy(GameDimens.PanelGap)) {
            BasicText(text = title, style = TwelvePitsType.sectionTitle)
            BasicText(text = message, style = TwelvePitsType.body)
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(GameDimens.PanelGap),
            ) {
                OutlinedGoldButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                OutlinedGoldButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}
