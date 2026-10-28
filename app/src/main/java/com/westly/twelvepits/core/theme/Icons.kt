package com.westly.twelvepits.core.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private fun DrawScope.lineStroke(): Stroke =
    Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Chip icon: the computer opponent. */
@Composable
fun ChipIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(32.dp)) {
        val s = lineStroke()
        val w = size.width
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.25f, w * 0.25f),
            size = Size(w * 0.5f, w * 0.5f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = s,
        )
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.40f, w * 0.40f),
            size = Size(w * 0.20f, w * 0.20f),
            style = s,
        )
        for (f in listOf(0.38f, 0.62f)) {
            drawLine(tint, Offset(w * f, w * 0.08f), Offset(w * f, w * 0.25f), s.width, StrokeCap.Round)
            drawLine(tint, Offset(w * f, w * 0.75f), Offset(w * f, w * 0.92f), s.width, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.08f, w * f), Offset(w * 0.25f, w * f), s.width, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.75f, w * f), Offset(w * 0.92f, w * f), s.width, StrokeCap.Round)
        }
    }
}

/** Two people icon: two players on one phone. */
@Composable
fun PeopleIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(32.dp)) {
        val s = lineStroke()
        val w = size.width
        drawCircle(tint, radius = w * 0.11f, center = Offset(w * 0.38f, w * 0.30f), style = s)
        drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.72f, w * 0.30f), style = s)
        val body = Path().apply {
            moveTo(w * 0.12f, w * 0.80f)
            cubicTo(w * 0.12f, w * 0.55f, w * 0.64f, w * 0.55f, w * 0.64f, w * 0.80f)
        }
        drawPath(body, color = tint, style = s)
        val second = Path().apply {
            moveTo(w * 0.72f, w * 0.52f)
            cubicTo(w * 0.86f, w * 0.52f, w * 0.90f, w * 0.66f, w * 0.90f, w * 0.80f)
        }
        drawPath(second, color = tint, style = s)
    }
}

/** Globe icon: two phones. */
@Composable
fun GlobeIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(32.dp)) {
        val s = lineStroke()
        val w = size.width
        val c = Offset(w / 2f, w / 2f)
        drawCircle(tint, radius = w * 0.38f, center = c, style = s)
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.33f, w * 0.12f),
            size = Size(w * 0.34f, w * 0.76f),
            style = s,
        )
        drawLine(tint, Offset(w * 0.12f, w / 2f), Offset(w * 0.88f, w / 2f), s.width, StrokeCap.Round)
    }
}

/** Padlock icon: locked items. */
@Composable
fun LockIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val s = lineStroke()
        val w = size.width
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.20f, w * 0.46f),
            size = Size(w * 0.60f, w * 0.40f),
            cornerRadius = CornerRadius(w * 0.06f),
            style = s,
        )
        val shackle = Path().apply {
            moveTo(w * 0.32f, w * 0.46f)
            lineTo(w * 0.32f, w * 0.32f)
            cubicTo(w * 0.32f, w * 0.10f, w * 0.68f, w * 0.10f, w * 0.68f, w * 0.32f)
            lineTo(w * 0.68f, w * 0.46f)
        }
        drawPath(shackle, color = tint, style = s)
    }
}

/** Chevron pointing right: opens a card. */
@Composable
fun ChevronRightIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val s = lineStroke()
        val w = size.width
        val path = Path().apply {
            moveTo(w * 0.35f, w * 0.18f)
            lineTo(w * 0.68f, w * 0.50f)
            lineTo(w * 0.35f, w * 0.82f)
        }
        drawPath(path, color = tint, style = s)
    }
}
