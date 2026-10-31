package com.westly.twelvepits.feature.game.multiplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.westly.twelvepits.R

/** The title of the message that ends a two-phone game before it is finished. */
@Composable
fun endReasonTitle(reason: EndReason?, opponentName: String): String = when (reason) {
    EndReason.PEER_LEFT -> stringResource(R.string.phones_end_title_peer_left, opponentName)
    EndReason.CONNECTION_LOST -> stringResource(R.string.phones_end_title_lost)
    EndReason.OUT_OF_SYNC -> stringResource(R.string.phones_end_title_sync)
    EndReason.VERSION_MISMATCH -> stringResource(R.string.phones_end_title_version)
    EndReason.LEFT_BY_ME, null -> stringResource(R.string.phones_end_title)
}

/** The second line of that message. */
@Composable
fun endReasonNote(reason: EndReason?): String = when (reason) {
    EndReason.VERSION_MISMATCH -> stringResource(R.string.phones_end_version)
    else -> stringResource(R.string.phones_end_note)
}

/** The sentence that tells why a two-phone match is over. */
@Composable
fun endReasonText(reason: EndReason?, opponentName: String): String = when (reason) {
    EndReason.PEER_LEFT -> stringResource(R.string.phones_end_peer_left, opponentName)
    EndReason.CONNECTION_LOST -> stringResource(R.string.phones_end_lost)
    EndReason.OUT_OF_SYNC -> stringResource(R.string.phones_end_sync)
    EndReason.VERSION_MISMATCH -> stringResource(R.string.phones_end_version)
    EndReason.LEFT_BY_ME, null -> stringResource(R.string.phones_end_you_left)
}
