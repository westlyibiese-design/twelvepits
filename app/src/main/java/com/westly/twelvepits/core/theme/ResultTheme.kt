package com.westly.twelvepits.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colours of the result screen (design reference screens/07_result.jpg).

/** The score of the player who did not win: a dimmer gold than [LightGold]. */
val ScoreDim = Gold.copy(alpha = 0.62f)

/** Soft emerald glow behind the trophy, strongest in the middle. */
val TrophyGlowCenter = Emerald.copy(alpha = 0.30f)

/** The edge of the trophy glow: the same colour, fully faded out. */
val TrophyGlowEdge = Emerald.copy(alpha = 0f)

/** Fill of the score card and the statistics card. */
val ResultCardFill = ControlFill.copy(alpha = 0.72f)

/** Sizes of the result screen. */
object ResultDimens {
    val ContentMargin = 24.dp
    val BlockGap = 20.dp
    val TitleGap = 6.dp
    val HeroSize = 150.dp
    val TrophyGlowSize = 220.dp
    val CardCorner = 12.dp
    val CardBorder = 1.dp
    val CardPadding = 16.dp
    val AvatarSize = 56.dp
    val ScoreGap = 4.dp
    val ButtonGap = 12.dp
    val ButtonAreaPadding = 16.dp
}

/** Text styles of the result screen. */
object ResultType {
    val headline = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = 3.sp,
        color = LightGold,
    )
    val subline = TwelvePitsType.body.copy(color = IvoryMuted)
    val scoreName = TwelvePitsType.label
    val scoreNumber = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = 1.sp,
        color = LightGold,
    )
    val scoreNumberDim = scoreNumber.copy(color = ScoreDim)
    val scoreTo = TwelvePitsType.label
    val statValue = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = Ivory,
    )
    val statLabel = TwelvePitsType.label
}
