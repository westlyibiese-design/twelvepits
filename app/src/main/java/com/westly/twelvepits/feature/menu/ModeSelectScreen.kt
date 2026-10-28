package com.westly.twelvepits.feature.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.westly.twelvepits.R
import com.westly.twelvepits.core.theme.ChevronRightIcon
import com.westly.twelvepits.core.theme.ChipIcon
import com.westly.twelvepits.core.theme.Gold
import com.westly.twelvepits.core.theme.GlobeIcon
import com.westly.twelvepits.core.theme.LockIcon
import com.westly.twelvepits.core.theme.MenuCard
import com.westly.twelvepits.core.theme.OffGrey
import com.westly.twelvepits.core.theme.PeopleIcon
import com.westly.twelvepits.core.theme.ScreenScaffold

@Composable
fun ModeSelectScreen(
    onBack: () -> Unit,
    onPlayComputer: () -> Unit,
    onTwoPlayers: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.mode_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MenuCard(
                title = stringResource(R.string.mode_computer_title),
                subtitle = stringResource(R.string.mode_computer_subtitle),
                onClick = onPlayComputer,
                leading = { ChipIcon(tint = Gold) },
                trailing = { ChevronRightIcon(tint = Gold) },
            )
            MenuCard(
                title = stringResource(R.string.mode_two_players_title),
                subtitle = stringResource(R.string.mode_two_players_subtitle),
                onClick = onTwoPlayers,
                leading = { PeopleIcon(tint = Gold) },
                trailing = { ChevronRightIcon(tint = Gold) },
            )
            // Phase 9 unlocks this card. Until then it is greyed out and does nothing.
            MenuCard(
                title = stringResource(R.string.mode_two_phones_title),
                subtitle = stringResource(R.string.mode_two_phones_subtitle),
                enabled = false,
                leading = { GlobeIcon(tint = OffGrey) },
                trailing = { LockIcon(tint = OffGrey) },
            )
        }
    }
}
