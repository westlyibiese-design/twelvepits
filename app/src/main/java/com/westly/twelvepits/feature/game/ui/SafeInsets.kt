package com.westly.twelvepits.feature.game.ui

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import kotlin.math.max

/** The parts of the screen that the camera cut-out and the rounded corners take away, in pixels. */
internal data class SafeInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** A rounded corner of radius r hides about 0.29 r along each edge (1 - cos 45 degrees). */
private const val CORNER_SHARE = 0.29f

/** The cut-out and rounded-corner insets. The system bars are hidden on the game screen and do not count. */
@Composable
internal fun rememberSafeInsets(widthPx: Float, heightPx: Float): SafeInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val view = LocalView.current
    val cutout = WindowInsets.displayCutout
    val cutLeft = cutout.getLeft(density, direction).toFloat()
    val cutTop = cutout.getTop(density).toFloat()
    val cutRight = cutout.getRight(density, direction).toFloat()
    val cutBottom = cutout.getBottom(density).toFloat()
    val corners = remember(view, widthPx, heightPx) { roundedCornerInsets(view) }
    return SafeInsets(
        left = max(cutLeft, corners[0]),
        top = max(cutTop, corners[1]),
        right = max(cutRight, corners[2]),
        bottom = max(cutBottom, corners[3]),
    )
}

/** Left, top, right, bottom. All zero below Android 12, which cannot tell the corner sizes. */
private fun roundedCornerInsets(view: View): FloatArray {
    val result = FloatArray(4)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        readCorners(view, result)
    }
    return result
}

@RequiresApi(Build.VERSION_CODES.S)
private fun readCorners(view: View, result: FloatArray) {
    val insets = view.rootWindowInsets ?: return
    val topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
    val topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
    val bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
    val bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
    result[0] = max(topLeft, bottomLeft) * CORNER_SHARE
    result[1] = max(topLeft, topRight) * CORNER_SHARE
    result[2] = max(topRight, bottomRight) * CORNER_SHARE
    result[3] = max(bottomLeft, bottomRight) * CORNER_SHARE
}
