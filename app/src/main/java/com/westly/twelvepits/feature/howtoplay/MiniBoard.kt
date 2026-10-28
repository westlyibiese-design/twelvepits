package com.westly.twelvepits.feature.howtoplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.westly.twelvepits.core.theme.Emerald
import com.westly.twelvepits.core.theme.Gold
import com.westly.twelvepits.core.theme.GoldLine
import com.westly.twelvepits.core.theme.Ink
import com.westly.twelvepits.core.theme.Ivory
import com.westly.twelvepits.core.theme.PitFill
import com.westly.twelvepits.core.theme.Walnut

/** How a pit is marked on a mini board. */
enum class PitMark { NONE, ACTION, CAPTURE }

/** A mini board: two rows of six pits. [top] is the opponent's row, [bottom] is yours. */
data class MiniBoardSpec(
    val top: List<Int>,
    val bottom: List<Int>,
    val marks: Map<Int, PitMark> = emptyMap(),
)

private const val COLUMNS = 6

// Seed spots inside a pit as fractions of the pit radius: four corners, then the middle.
private val SeedSpots = listOf(
    Offset(-0.36f, -0.30f),
    Offset(0.36f, -0.30f),
    Offset(-0.36f, 0.30f),
    Offset(0.36f, 0.30f),
    Offset(0f, 0f),
)
private val SeedTurns = listOf(-28f, 24f, 18f, -20f, 8f)

/** Pit index 0 to 5 is the bottom row left to right, 6 to 11 the top row right to left (RULES.md section 1). */
@Composable
fun MiniBoard(spec: MiniBoardSpec, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(6f / 2.5f)) {
        val frameRadius = 14.dp.toPx()
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Walnut, Ink)),
            cornerRadius = CornerRadius(frameRadius),
        )
        drawRoundRect(
            color = GoldLine,
            cornerRadius = CornerRadius(frameRadius),
            style = Stroke(width = 1.5.dp.toPx()),
        )
        val cell = size.width / COLUMNS
        val pitRadius = cell * 0.40f
        val topY = size.height * 0.27f
        val bottomY = size.height * 0.73f
        for (column in 0 until COLUMNS) {
            val x = cell * (column + 0.5f)
            // Top row runs right to left, so top pit 6 is the rightmost one.
            val topIndex = 6 + (COLUMNS - 1 - column)
            drawPit(Offset(x, topY), pitRadius, spec.top[column], spec.marks[topIndex] ?: PitMark.NONE)
            drawPit(Offset(x, bottomY), pitRadius, spec.bottom[column], spec.marks[column] ?: PitMark.NONE)
        }
    }
}

private fun DrawScope.drawPit(center: Offset, radius: Float, seeds: Int, mark: PitMark) {
    drawCircle(color = PitFill, radius = radius, center = center)
    val ring = when (mark) {
        PitMark.ACTION -> Emerald
        PitMark.CAPTURE -> Gold
        PitMark.NONE -> GoldLine
    }
    drawCircle(
        color = ring,
        radius = radius,
        center = center,
        style = Stroke(width = (if (mark == PitMark.NONE) 1.5.dp else 2.5.dp).toPx()),
    )
    val shown = seeds.coerceIn(0, SeedSpots.size)
    for (i in 0 until shown) {
        val spot = SeedSpots[i]
        val seedCenter = Offset(center.x + spot.x * radius, center.y + spot.y * radius)
        rotate(degrees = SeedTurns[i], pivot = seedCenter) {
            drawOval(
                color = Ivory,
                topLeft = Offset(seedCenter.x - radius * 0.24f, seedCenter.y - radius * 0.14f),
                size = Size(radius * 0.48f, radius * 0.28f),
            )
        }
    }
}
