@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.westly.twelvepits.core.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.westly.twelvepits.R

/** Cinzel is bundled as one variable font; each weight picks its own point on the weight axis. */
val Cinzel: FontFamily = FontFamily(
    Font(
        R.font.cinzel,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.cinzel,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.cinzel,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Body text uses the system sans font. */
val BodyFont: FontFamily = FontFamily.SansSerif

object TwelvePitsType {
    val screenTitle = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.sp,
        color = LightGold,
    )
    val primaryButton = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 3.sp,
        color = OnGold,
    )
    val secondaryButton = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = 1.5.sp,
        color = LightGold,
    )
    val cardTitle = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = LightGold,
    )
    val sectionTitle = TextStyle(
        fontFamily = Cinzel,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 1.sp,
        color = Gold,
    )
    val body = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        color = Ivory,
    )
    val bodyMuted = body.copy(color = IvoryMuted)
    val label = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        letterSpacing = 0.5.sp,
        color = IvoryMuted,
    )
    val segment = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = Gold,
    )
    val small = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 1.sp,
        // 70% ivory keeps at least 4.5:1 contrast on the brightest part of the wood background.
        color = Ivory.copy(alpha = 0.70f),
    )
}
