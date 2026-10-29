package com.westly.twelvepits.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A control on the game screen: 44dp, gold on dark, 12dp corners. Plays the tap sound. */
@Composable
fun GameControlButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(GameDimens.ControlSize)
            .clip(ButtonShape)
            .background(GameControlFill)
            .border(BorderStroke(GameDimens.ControlBorder, GameControlBorder), ButtonShape)
            .semantics { this.contentDescription = contentDescription }
            .tapClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// The icons use the 24 x 24 drawing grid of the design reference.

private fun DrawScope.glyphStroke(widthOnGrid: Float): Stroke =
    Stroke(width = widthOnGrid * size.width / 24f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Arrow pointing left: back to the menu. */
@Composable
fun BackGlyph(modifier: Modifier = Modifier, glyphSize: Dp = 22.dp) {
    Canvas(modifier = modifier.size(glyphSize)) {
        val k = size.width / 24f
        val path = Path().apply {
            moveTo(15f * k, 5f * k)
            lineTo(8f * k, 12f * k)
            lineTo(15f * k, 19f * k)
        }
        drawPath(path, GameGold, style = glyphStroke(2f))
    }
}

/** Two vertical bars: pause. */
@Composable
fun PauseGlyph(modifier: Modifier = Modifier, glyphSize: Dp = 20.dp) {
    Canvas(modifier = modifier.size(glyphSize)) {
        val k = size.width / 24f
        val stroke = glyphStroke(2.4f)
        drawLine(GameGold, Offset(8f * k, 5f * k), Offset(8f * k, 19f * k), stroke.width, StrokeCap.Round)
        drawLine(GameGold, Offset(16f * k, 5f * k), Offset(16f * k, 19f * k), stroke.width, StrokeCap.Round)
    }
}

/** Speaker with waves, or a speaker with a cross when [muted]. */
@Composable
fun SoundGlyph(muted: Boolean, modifier: Modifier = Modifier, glyphSize: Dp = 22.dp) {
    Canvas(modifier = modifier.size(glyphSize)) {
        val k = size.width / 24f
        val stroke = glyphStroke(2f)
        val speaker = Path().apply {
            moveTo(4f * k, 9f * k)
            lineTo(4f * k, 15f * k)
            lineTo(8f * k, 15f * k)
            lineTo(13f * k, 19f * k)
            lineTo(13f * k, 5f * k)
            lineTo(8f * k, 9f * k)
            close()
        }
        drawPath(speaker, GameGold, style = stroke)
        if (muted) {
            drawLine(GameGold, Offset(16f * k, 9f * k), Offset(22f * k, 15f * k), stroke.width, StrokeCap.Round)
            drawLine(GameGold, Offset(22f * k, 9f * k), Offset(16f * k, 15f * k), stroke.width, StrokeCap.Round)
        } else {
            // Two arcs: radius 5 around (12.93, 12) and radius 8.5 around (12.98, 12), about 89 degrees each.
            drawArc(
                color = GameGold,
                startAngle = -44.4f,
                sweepAngle = 88.8f,
                useCenter = false,
                topLeft = Offset((12.93f - 5f) * k, (12f - 5f) * k),
                size = Size(10f * k, 10f * k),
                style = stroke,
            )
            drawArc(
                color = GameGold,
                startAngle = -44.9f,
                sweepAngle = 89.8f,
                useCenter = false,
                topLeft = Offset((12.98f - 8.5f) * k, (12f - 8.5f) * k),
                size = Size(17f * k, 17f * k),
                style = stroke,
            )
        }
    }
}
