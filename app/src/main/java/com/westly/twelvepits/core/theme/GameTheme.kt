package com.westly.twelvepits.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Colours of the game screen (design reference twelve_pits_landscape_game_screen.html).

/** Gold of the in-game controls, the count badge border and the control icons (#E7C16A). */
val GameGold = Color(0xFFE7C16A)

/** Fill of the hidden controls: rgba(10, 6, 3, 0.78). */
val GameControlFill = Color(0xC70A0603)

/** Border of the hidden controls: gold at 70%. */
val GameControlBorder = GameGold.copy(alpha = 0.70f)

/** Count badge: #0c0703 at 92% opacity. */
val BadgeFill = Color(0xEB0C0703)

/** Count badge border: gold (#E7C16A). */
val BadgeBorder = GameGold

/** Count badge number: light gold (#F3D98B). */
val BadgeText = Color(0xFFF3D98B)

/** Soft dark shade behind the controls at the top of the screen. */
val TopShade = Color(0x8C000000)

/** Dark layer behind the pause menu and the game dialogs. */
val OverlayScrim = Color(0xC7070504)

/** Soft glow of a pit that is about to be picked up again (relay). */
val RelayGlow = GameGold

/** Emerald glow of a pit that became 4. */
val CaptureGlow = Emerald

/** Colour of the "+4" that floats up from a store. */
val PlusFloat = LightGold

/** Player name under a store: ivory, slightly soft. */
val StoreNameText = Ivory.copy(alpha = 0.92f)

/** Dark shadow that keeps the player name readable on the wood. */
val StoreNameShadow = Color(0xCC000000)

/** Shadow under the board on the table. */
val BoardShadow = Color(0xA6000000)

/** Sizes of the game screen. */
object GameDimens {
    /** A tap hits a pit inside at least this radius, even when the drawn ring is smaller. */
    val MinPitTapRadius = 24.dp

    val ControlSize = 44.dp

    /** The touch area of a control: 48dp, a little larger than the 44dp button that is drawn. */
    val ControlHitSize = 48.dp
    val ControlBorder = 1.dp
    val ControlGap = 12.dp
    val ControlEdgeMargin = 18.dp
    val ControlTopMargin = 12.dp

    val CueEdgeMargin = 10.dp
    val CueTopMargin = 8.dp
    const val CUE_ALPHA = 0.38f

    val TopShadeHeight = 76.dp

    val RingStroke = 2.dp
    val GlowStrokeWide = 10.dp
    val GlowStrokeNarrow = 6.dp
    val BadgeBorderWidth = 1.5.dp
    val BoardShadowOffset = 10.dp

    val PanelMaxWidth = 520.dp
    val PanelPadding = 20.dp
    val PanelCorner = 16.dp
    val PanelBorder = 1.5.dp
    val ScreenMargin = 16.dp
    val BannerCorner = 12.dp
    val BannerMargin = 12.dp
    val BannerPaddingH = 20.dp
    val BannerPaddingV = 10.dp
    const val BANNER_FILL_ALPHA = 0.92f
    val PanelGap = 14.dp
    val ButtonGap = 10.dp
    val SettingsRowMinHeight = 52.dp
}
