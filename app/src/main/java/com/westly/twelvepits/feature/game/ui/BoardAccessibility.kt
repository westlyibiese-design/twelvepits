package com.westly.twelvepits.feature.game.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The board is drawn on a canvas, so a screen reader cannot see it. This layer puts one invisible
 * element over every pit and every store. Each one says what the player can see ("Pit 3, 5 seeds, your pit",
 * "Right store, 12 seeds") and a double tap on a pit plays it. The elements take no touches of their own,
 * so nothing changes for a player who does not use a screen reader.
 */
@Composable
internal fun BoardAccessibilityLayer(
    viewModel: GameViewModel,
    mapper: BoardMapper,
    board: BoardRect,
    geometry: BoardGeometry,
    minRadiusPx: Float,
) {
    val density = LocalDensity.current
    val resources = LocalContext.current.resources
    val sowAction = stringResource(R.string.a11y_sow_action)
    val pitRadius = max(geometry.pitOuterRingRadiusW * board.width, minRadiusPx)
    val pitSize = with(density) { (pitRadius * 2f).toDp() }
    val storeRadius = max(geometry.storeOuterRadiusW * board.width, minRadiusPx)
    val storeSize = with(density) { (storeRadius * 2f).toDp() }

    for (pit in 0 until GameState.PIT_COUNT) {
        val slot = mapper.slotOfPit(pit)
        val left = board.x(geometry.slotCx(slot)) - pitRadius
        val top = board.y(geometry.slotCy(slot)) - pitRadius
        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(pitSize)
                .semantics(mergeDescendants = true) {
                    contentDescription = describePit(resources, viewModel, pit)
                    onClick(label = sowAction) {
                        viewModel.onPitTapped(pit)
                        true
                    }
                },
        )
    }
    for (owner in Player.entries) {
        val side = mapper.storeSide(owner)
        val left = board.x(geometry.storeCx(side)) - storeRadius
        val top = board.y(geometry.storeCy(side)) - storeRadius
        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(storeSize)
                .semantics(mergeDescendants = true) {
                    contentDescription = describeStore(resources, viewModel, owner, side)
                },
        )
    }
}

/** What the board shows now. Reading the frame tick makes the description follow the animation. */
private fun shownPitCount(viewModel: GameViewModel, pit: Int): Int =
    if (viewModel.frameTick.longValue >= 0L) viewModel.display.pits[pit] else 0

private fun shownStoreCount(viewModel: GameViewModel, owner: Player): Int =
    if (viewModel.frameTick.longValue >= 0L) viewModel.display.stores[owner.ordinal] else 0

private fun seedsText(resources: Resources, count: Int): String =
    resources.getQuantityString(R.plurals.a11y_seed_count, count, count)

private fun describePit(resources: Resources, viewModel: GameViewModel, pit: Int): String {
    val config = viewModel.config
    val owner = GameState.ownerOfPit(pit)
    val ownerText = when {
        config.mode == GameMode.VS_COMPUTER && owner == Player.ONE -> resources.getString(R.string.a11y_owner_you)
        config.mode == GameMode.VS_COMPUTER -> resources.getString(R.string.a11y_owner_computer)
        owner == Player.ONE -> resources.getString(R.string.a11y_owner_named, config.player1Name)
        else -> resources.getString(R.string.a11y_owner_named, config.player2Name)
    }
    return resources.getString(R.string.a11y_pit, pit + 1, seedsText(resources, shownPitCount(viewModel, pit)), ownerText)
}

private fun describeStore(resources: Resources, viewModel: GameViewModel, owner: Player, side: StoreSide): String {
    val seeds = seedsText(resources, shownStoreCount(viewModel, owner))
    return resources.getString(
        if (side == StoreSide.RIGHT) R.string.a11y_store_right else R.string.a11y_store_left,
        seeds,
    )
}
