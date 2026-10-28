package com.westly.twelvepits.feature.launch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import com.westly.twelvepits.core.theme.FlashCore
import com.westly.twelvepits.core.theme.FlashEdge
import com.westly.twelvepits.core.theme.FlashMid
import com.westly.twelvepits.core.theme.GlowCore
import com.westly.twelvepits.core.theme.GlowMid
import com.westly.twelvepits.core.theme.RayGold
import com.westly.twelvepits.core.theme.SeedBody
import com.westly.twelvepits.core.theme.SeedEdge
import com.westly.twelvepits.core.theme.SeedHighlight
import com.westly.twelvepits.core.theme.SeedShadow
import com.westly.twelvepits.core.theme.SheenWhite
import com.westly.twelvepits.core.theme.SparkGold
import com.westly.twelvepits.core.theme.SparkGreen
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val IMAGE_WIDTH = 841f
private const val IMAGE_HEIGHT = 1870f
private const val RING_CENTER_X = 420f
private const val RING_CENTER_Y = 895f
private const val RING_RADIUS = 247f
private const val RING_PITS = 11
private const val SEEDS_PER_PIT = 4


// Seed spots of a 2x2 cluster, in hundredths of the image width.
private val ClusterSpots = listOf(
    Offset(-1.9f, -1.3f),
    Offset(1.9f, -1.3f),
    Offset(-1.9f, 1.3f),
    Offset(1.9f, 1.3f),
)

/**
 * Where the 841x1870 emblem image lands on the screen when it is drawn cover-fit and centred.
 * Every effect is placed with this, so rays, glow and seeds stay on the emblem on any phone shape.
 */
class ImageSpace(width: Float, height: Float) {
    val scale: Float = max(width / IMAGE_WIDTH, height / IMAGE_HEIGHT)
    private val offsetX: Float = (width - IMAGE_WIDTH * scale) / 2f
    private val offsetY: Float = (height - IMAGE_HEIGHT * scale) / 2f

    fun x(imageX: Float): Float = offsetX + imageX * scale
    fun y(imageY: Float): Float = offsetY + imageY * scale

    /** Length in screen pixels of a share of the image width (0.01 is one "cqw"). */
    fun w(share: Float): Float = share * IMAGE_WIDTH * scale

    val center: Offset = Offset(x(RING_CENTER_X), y(RING_CENTER_Y))
}

/** Slowly turning gold rays behind the emblem. */
fun DrawScope.drawRays(space: ImageSpace, rotationDegrees: Float, opacity: Float) {
    val reach = space.w(0.75f)
    val brush = Brush.radialGradient(
        0.08f to Color.Transparent,
        0.16f to RayGold,
        0.56f to Color.Transparent,
        center = space.center,
        radius = reach,
    )
    val half = (2.0 * PI / 180.0).toFloat()
    val length = reach * 0.6f
    val wedge = Path().apply {
        moveTo(space.center.x, space.center.y)
        lineTo(
            space.center.x + length * sin(-half),
            space.center.y - length * cos(-half),
        )
        lineTo(
            space.center.x + length * sin(half),
            space.center.y - length * cos(half),
        )
        close()
    }
    for (ray in 0 until 24) {
        rotate(degrees = rotationDegrees + ray * 15f, pivot = space.center) {
            drawPath(path = wedge, brush = brush, alpha = opacity, blendMode = BlendMode.Screen)
        }
    }
}

/** Pulsing emerald glow at the emblem centre. [pulse] goes 0 to 1 and back. */
fun DrawScope.drawGlow(space: ImageSpace, pulse: Float, opacity: Float) {
    val radius = space.w(0.22f) * (0.95f + 0.09f * pulse)
    val brush = Brush.radialGradient(
        0f to GlowCore,
        0.42f to GlowMid,
        0.70f to Color.Transparent,
        center = space.center,
        radius = radius,
    )
    drawCircle(
        brush = brush,
        radius = radius,
        center = space.center,
        alpha = (0.35f + 0.35f * pulse) * opacity,
        blendMode = BlendMode.Screen,
    )
}

/** One soft band of light that sweeps across the emblem centre. [progress] goes 0 to 1. */
fun DrawScope.drawSheen(space: ImageSpace, progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val diameter = space.w(0.29f)
    val radius = diameter / 2f
    val circle = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                space.center.x - radius,
                space.center.y - radius,
                space.center.x + radius,
                space.center.y + radius,
            ),
        )
    }
    val bandWidth = diameter * 0.30f
    val left = space.center.x - radius + diameter * (-0.40f + 1.70f * progress)
    val top = space.center.y - radius * 1.4f
    val bottom = space.center.y + radius * 1.4f
    val lean = (bottom - top) * 0.32f
    val band = Path().apply {
        moveTo(left + lean, top)
        lineTo(left + lean + bandWidth, top)
        lineTo(left + bandWidth, bottom)
        lineTo(left, bottom)
        close()
    }
    clipPath(circle) {
        drawPath(
            path = band,
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to SheenWhite,
                1f to Color.Transparent,
                startX = left,
                endX = left + bandWidth + lean,
            ),
        )
    }
}

/**
 * The 44 seeds of the launch animation: 11 pits around the ring, 4 seeds each.
 * [dropStartMs] holds the time each seed started to drop, or a negative number if it has not appeared yet.
 */
fun DrawScope.drawLaunchSeeds(space: ImageSpace, dropStartMs: FloatArray, elapsedMs: Float, turns: FloatArray) {
    val seedWidth = space.w(0.039f)
    val seedHeight = space.w(0.023f)
    for (pit in 0 until RING_PITS) {
        val angle = (pit * 2.0 * PI / RING_PITS).toFloat()
        val pitX = RING_CENTER_X + RING_RADIUS * sin(angle)
        val pitY = RING_CENTER_Y - RING_RADIUS * cos(angle)
        for (slot in 0 until SEEDS_PER_PIT) {
            val index = pit * SEEDS_PER_PIT + slot
            val started = dropStartMs[index]
            if (started < 0f) continue
            val t = ((elapsedMs - started) / LaunchTimeline.SEED_DROP_MS).coerceIn(0f, 1f)
            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
            val spot = ClusterSpots[slot]
            val cx = space.x(pitX + spot.x * 8.41f)
            val cy = space.y(pitY + spot.y * 8.41f) - space.w(0.03f) * (1f - eased)
            val scale = 1.15f - 0.15f * eased
            val width = seedWidth * scale
            val height = seedHeight * scale
            rotate(degrees = turns[index] * eased, pivot = Offset(cx, cy)) {
                drawOval(
                    color = SeedShadow.copy(alpha = 0.6f * t),
                    topLeft = Offset(cx - width / 2f, cy - height / 2f + space.w(0.003f)),
                    size = Size(width, height),
                )
                drawOval(
                    brush = Brush.radialGradient(
                        0f to SeedHighlight,
                        0.55f to SeedBody,
                        1f to SeedEdge,
                        center = Offset(cx - width * 0.15f, cy - height * 0.2f),
                        radius = width * 0.62f,
                    ),
                    topLeft = Offset(cx - width / 2f, cy - height / 2f),
                    size = Size(width, height),
                    alpha = t,
                )
            }
        }
    }
}

/** Emerald flash that grows out of the emblem centre. [progress] goes 0 to 1. */
fun DrawScope.drawFlash(space: ImageSpace, progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val opacity = linear(progress, 0f to 0f, 0.4f to 0.85f, 0.7f to 0.6f, 1f to 0f)
    val grow = linear(progress, 0f to 0.1f, 0.4f to 0.8f, 0.7f to 1.8f, 1f to 2.2f)
    val radius = space.w(0.6f) * grow
    drawCircle(
        brush = Brush.radialGradient(
            0f to FlashCore,
            0.16f to FlashMid,
            0.38f to FlashEdge,
            0.66f to Color.Transparent,
            center = space.center,
            radius = radius,
        ),
        radius = radius,
        center = space.center,
        alpha = opacity,
    )
}

private fun linear(at: Float, vararg keys: Pair<Float, Float>): Float {
    for (i in 1 until keys.size) {
        val (t1, v1) = keys[i]
        if (at <= t1) {
            val (t0, v0) = keys[i - 1]
            val span = t1 - t0
            return if (span <= 0f) v1 else v0 + (v1 - v0) * ((at - t0) / span)
        }
    }
    return keys.last().second
}

/** Drifting gold and emerald sparkles that rise from the bottom of the screen. */
class Sparkles(
    private val count: Int,
    private val width: Float,
    private val height: Float,
    private val unit: Float,
    seed: Int,
) {
    private val random = Random(seed)
    private val x = FloatArray(count)
    private val y = FloatArray(count)
    private val radius = FloatArray(count)
    private val speed = FloatArray(count)
    private val phase = FloatArray(count)
    private val phaseSpeed = FloatArray(count)
    private val green = BooleanArray(count)

    init {
        for (i in 0 until count) place(i, startAnywhere = true)
    }

    private fun place(i: Int, startAnywhere: Boolean) {
        x[i] = random.nextFloat() * width
        y[i] = if (startAnywhere) random.nextFloat() * height else height + 10f * unit
        radius[i] = (random.nextFloat() * 1.2f + 0.5f) * unit
        speed[i] = (random.nextFloat() * 0.22f + 0.08f) * unit
        phase[i] = random.nextFloat() * (2f * PI.toFloat())
        phaseSpeed[i] = random.nextFloat() * 0.03f + 0.01f
        green[i] = random.nextFloat() < 0.15f
    }

    /** Moves every sparkle by [seconds]. The speeds were chosen per frame at 60 frames a second. */
    fun step(seconds: Float) {
        val frames = seconds * 60f
        for (i in 0 until count) {
            y[i] -= speed[i] * frames
            phase[i] += phaseSpeed[i] * frames
            x[i] += sin(phase[i]) * 0.25f * unit * frames
            if (y[i] < -10f * unit) place(i, startAnywhere = false)
        }
    }

    fun draw(scope: DrawScope, opacity: Float) {
        for (i in 0 until count) {
            val alpha = (0.12f + 0.4f * abs(sin(phase[i] * 1.2f))) * opacity
            scope.drawCircle(
                color = (if (green[i]) SparkGreen else SparkGold).copy(alpha = alpha),
                radius = radius[i],
                center = Offset(x[i], y[i]),
            )
        }
    }
}
