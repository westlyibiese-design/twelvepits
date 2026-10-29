package com.westly.twelvepits.feature.game.ui

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A rectangle in pixels. */
class BoardRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    /** Pixel x for a fraction of the board width. */
    fun x(fraction: Float): Float = left + fraction * width

    /** Pixel y for a fraction of the board height. */
    fun y(fraction: Float): Float = top + fraction * height
}

/** Where the board is drawn (pure Kotlin, unit tested). */
object BoardLayoutMath {
    /** The board is about 96% of the safe width on normal phones. */
    const val WIDTH_OF_SAFE_AREA = 0.96f

    /** The board is never taller than about this share of the screen height (before the 0.96). */
    const val HEIGHT_OF_SCREEN = 0.62f

    /**
     * The board rectangle: centred in the safe area (screen minus cut-out and rounded corner insets),
     * never stretched, width = min(0.96 x safe width, 0.96 x 0.62 x screen height x aspect ratio).
     * It also fits a window that is taller than wide.
     */
    fun boardRect(
        screenWidth: Float,
        screenHeight: Float,
        safeLeft: Float,
        safeTop: Float,
        safeRight: Float,
        safeBottom: Float,
        aspectRatio: Float,
    ): BoardRect {
        val safeWidth = max(1f, screenWidth - safeLeft - safeRight)
        val safeHeight = max(1f, screenHeight - safeTop - safeBottom)
        val width = max(
            1f,
            min(WIDTH_OF_SAFE_AREA * safeWidth, WIDTH_OF_SAFE_AREA * HEIGHT_OF_SCREEN * screenHeight * aspectRatio),
        )
        val height = width / aspectRatio
        val centerX = safeLeft + safeWidth / 2f
        val centerY = safeTop + safeHeight / 2f
        return BoardRect(centerX - width / 2f, centerY - height / 2f, width, height)
    }
}

/** Finds which pit a tap hits (pure Kotlin, unit tested). */
object PitHitTester {
    /**
     * The slot (0 to 11) of the pit hit by a tap at ([x], [y]), or -1 when no pit is hit.
     * A tap hits a pit when it is inside a circle of radius max(outer ring radius, [minRadiusPx])
     * around the pit centre. When two circles overlap the nearest pit wins.
     */
    fun hitSlot(x: Float, y: Float, board: BoardRect, geometry: BoardGeometry, minRadiusPx: Float): Int {
        val radius = max(geometry.pitOuterRingRadiusW * board.width, minRadiusPx)
        val radiusSquared = radius * radius
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (slot in 0 until BoardGeometry.SLOT_COUNT) {
            val dx = x - board.x(geometry.slotCx(slot))
            val dy = y - board.y(geometry.slotCy(slot))
            val distance = dx * dx + dy * dy
            if (distance <= radiusSquared && distance < bestDistance) {
                best = slot
                bestDistance = distance
            }
        }
        return best
    }
}

/**
 * Stable seed slots: slot `i` of a pit or store always has the same place and angle, so adding or
 * removing a seed never moves the seeds that are already there. Offsets are fractions of the board width.
 */
object SeedLayout {
    private const val GOLDEN_ANGLE = 2.39996f

    /** Spread radius of a pit: 0.55 of the inner bowl radius. */
    const val PIT_SPREAD_OF_BOWL = 0.55f

    /** Spread radius of a store: 0.62 of the bowl opening radius. */
    const val STORE_SPREAD_OF_OPENING = 0.62f

    /** The key of the left store; the right store uses [RIGHT_STORE_KEY]. Pits use their slot + 1. */
    const val LEFT_STORE_KEY = 20
    const val RIGHT_STORE_KEY = 21

    fun pitKey(slot: Int): Int = slot + 1

    fun storeKey(side: StoreSide): Int = if (side == StoreSide.LEFT) LEFT_STORE_KEY else RIGHT_STORE_KEY

    /** Distance of slot [index] from the centre, as a fraction of the board width. */
    fun radius(index: Int, spreadRadius: Float, cap: Int): Float {
        val clamped = index.coerceIn(0, cap - 1)
        return spreadRadius * sqrt((clamped + 0.5f) / cap)
    }

    fun angleRadians(index: Int, key: Int): Float = index * GOLDEN_ANGLE + key * 1.7f

    fun offsetX(index: Int, key: Int, spreadRadius: Float, cap: Int): Float =
        radius(index, spreadRadius, cap) * cos(angleRadians(index.coerceIn(0, cap - 1), key))

    fun offsetY(index: Int, key: Int, spreadRadius: Float, cap: Int): Float =
        radius(index, spreadRadius, cap) * sin(angleRadians(index.coerceIn(0, cap - 1), key))

    /** Rotation of the upright seed picture, in degrees from 0 up to (not including) 180. */
    fun rotationDegrees(index: Int, key: Int): Float = ((index * 67 + key * 41) % 180).toFloat()
}
