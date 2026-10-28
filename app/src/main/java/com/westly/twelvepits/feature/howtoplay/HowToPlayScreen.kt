package com.westly.twelvepits.feature.howtoplay

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.westly.twelvepits.R
import com.westly.twelvepits.core.theme.Gold
import com.westly.twelvepits.core.theme.ScreenScaffold
import com.westly.twelvepits.core.theme.TwelvePitsType

private data class HowToPage(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val board: MiniBoardSpec,
)

// Pit numbers follow RULES.md: 0 to 5 is the bottom row, 6 to 11 the top row.
private val Pages: List<HowToPage> = listOf(
    HowToPage(
        titleRes = R.string.howto_page_sow_title,
        bodyRes = R.string.howto_page_sow_body,
        board = MiniBoardSpec(
            top = listOf(1, 2, 3, 0, 4, 2),
            bottom = listOf(3, 4, 1, 2, 0, 3),
            marks = mapOf(0 to PitMark.ACTION),
        ),
    ),
    HowToPage(
        titleRes = R.string.howto_page_relay_title,
        bodyRes = R.string.howto_page_relay_body,
        board = MiniBoardSpec(
            top = listOf(2, 0, 3, 1, 1, 2),
            bottom = listOf(2, 3, 0, 1, 4, 2),
            marks = mapOf(1 to PitMark.ACTION, 2 to PitMark.NONE),
        ),
    ),
    HowToPage(
        titleRes = R.string.howto_page_capture_title,
        bodyRes = R.string.howto_page_capture_body,
        board = MiniBoardSpec(
            top = listOf(1, 0, 2, 5, 0, 1),
            bottom = listOf(3, 0, 4, 1, 2, 0),
            marks = mapOf(2 to PitMark.CAPTURE),
        ),
    ),
    HowToPage(
        titleRes = R.string.howto_page_win_title,
        bodyRes = R.string.howto_page_win_body,
        board = MiniBoardSpec(
            top = listOf(0, 0, 1, 0, 0, 0),
            bottom = listOf(0, 2, 0, 0, 1, 0),
        ),
    ),
)

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { Pages.size })
    ScreenScaffold(title = stringResource(R.string.howto_title), onBack = onBack) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            val page = Pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                MiniBoard(spec = page.board)
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(text = stringResource(page.titleRes), style = TwelvePitsType.sectionTitle)
                BasicText(text = stringResource(page.bodyRes), style = TwelvePitsType.bodyMuted)
            }
        }
        PageDots(
            count = Pages.size,
            current = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 8.dp),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.howto_page_indicator, current + 1, count)
    Row(
        modifier = modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until count) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Gold else Gold.copy(alpha = 0.28f)),
            )
        }
    }
}
