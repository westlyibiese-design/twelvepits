package com.westly.twelvepits.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.westly.twelvepits.core.theme.Ink
import kotlinx.coroutines.delay

/** After the window has the expected shape, the cover stays a moment so the new layout is fully drawn. */
private const val SETTLE_MS = 150L

/** If the window never takes the expected shape (a tablet that ignores the lock), the cover goes anyway. */
private const val GIVE_UP_MS = 1500L

/**
 * The game screen is landscape and every other screen is portrait. When the screen being shown changes
 * from one kind to the other, the window turns for a short moment. Until it has turned, this draws plain
 * #070504 over everything, so a stretched menu or a squeezed board is never visible.
 *
 * It does nothing at the start of the app and nothing when the activity is recreated by the turn itself.
 */
@Composable
fun OrientationGuard(
    expectLandscape: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight
        val matches = isLandscape == expectLandscape
        // The expectation the window last had the right shape for.
        var settledFor by remember { mutableStateOf(expectLandscape) }
        val pending = expectLandscape != settledFor
        LaunchedEffect(pending, matches, expectLandscape) {
            if (pending) {
                delay(if (matches) SETTLE_MS else GIVE_UP_MS)
                settledFor = expectLandscape
            }
        }
        content()
        if (pending) {
            Box(modifier = Modifier.fillMaxSize().background(Ink))
        }
    }
}
