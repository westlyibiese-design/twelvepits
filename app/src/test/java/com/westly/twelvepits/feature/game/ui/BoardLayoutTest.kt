package com.westly.twelvepits.feature.game.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class BoardLayoutTest {
    private val geometry: BoardGeometry = BoardGeometry.parse(File("src/main/assets/board_geometry.json").readText())
    private val aspect = geometry.aspectRatio

    private fun rect(width: Float, height: Float, left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) =
        BoardLayoutMath.boardRect(width, height, left, top, right, bottom, aspect)

    // ---- The board rectangle ----

    @Test
    fun aNormalPhoneGetsTheWholeWidth() {
        val board = rect(2400f, 1080f)
        assertEquals(2400f, board.width, 0.5f)
        assertEquals(board.width / aspect, board.height, 0.5f)
        assertEquals((2400f - board.width) / 2f, board.left, 0.5f)
        assertEquals((1080f - board.height) / 2f, board.top, 0.5f)
    }

    @Test
    fun theBoardIsNeverStretchedAndNeverTallerThanSixtyPercentOfTheScreen() {
        val screens = listOf(
            1920f to 1080f, // 16:9
            2340f to 1080f, // 19.5:9
            2400f to 1080f, // 20:9
            2520f to 1080f, // 21:9
            2560f to 1600f, // tablet
            1280f to 800f,
        )
        for ((w, h) in screens) {
            val board = rect(w, h)
            assertEquals("$w x $h", aspect, board.width / board.height, 0.001f)
            assertTrue("$w x $h", board.height <= 0.62f * 0.96f * h + 0.5f)
            assertTrue("$w x $h", board.width <= w + 0.5f)
            assertTrue("$w x $h", board.left >= 0f && board.top >= 0f)
        }
    }

    @Test
    fun theBoardIsCentredInTheSafeAreaNotInTheWholeScreen() {
        val board = rect(2400f, 1080f, left = 120f, top = 0f, right = 0f, bottom = 0f)
        val safeWidth = 2400f - 120f
        assertEquals(safeWidth, board.width, 0.5f)
        assertEquals(120f + safeWidth / 2f, board.left + board.width / 2f, 0.5f)
        assertTrue(board.left >= 120f - 0.01f)
    }

    @Test
    fun aWindowTallerThanWideStillShowsTheBoardCentredAndScaledToFit() {
        val board = rect(1080f, 2400f)
        assertEquals(1080f, board.width, 0.5f)
        assertEquals(aspect, board.width / board.height, 0.001f)
        assertEquals(1080f / 2f, board.left + board.width / 2f, 0.5f)
        assertEquals(2400f / 2f, board.top + board.height / 2f, 0.5f)
    }

    // ---- Hit testing ----

    @Test
    fun aTapOnAPitCentreHitsThatPit() {
        val board = rect(2400f, 1080f)
        for (slot in 0 until BoardGeometry.SLOT_COUNT) {
            val x = board.x(geometry.slotCx(slot))
            val y = board.y(geometry.slotCy(slot))
            assertEquals(slot, PitHitTester.hitSlot(x, y, board, geometry, 10f))
        }
    }

    @Test
    fun theHitCircleIsTheOuterRingRadiusAndNotLarger() {
        val board = rect(2400f, 1080f)
        val ring = geometry.pitOuterRingRadiusW * board.width
        val x = board.x(geometry.slotCx(2))
        val y = board.y(geometry.slotCy(2))
        assertEquals(2, PitHitTester.hitSlot(x + ring * 0.95f, y, board, geometry, 1f))
        assertEquals(-1, PitHitTester.hitSlot(x + ring * 1.05f, y, board, geometry, 1f))
    }

    @Test
    fun onASmallBoardTheTwentyFourDpMinimumRadiusApplies() {
        val board = rect(800f, 360f)
        val ring = geometry.pitOuterRingRadiusW * board.width
        val minimum = 62f
        assertTrue(minimum > ring)
        val x = board.x(geometry.slotCx(7))
        val y = board.y(geometry.slotCy(7))
        // Tap upwards, away from the other pits, so only the pit's own circle can be hit.
        assertEquals(7, PitHitTester.hitSlot(x, y - minimum * 0.95f, board, geometry, minimum))
        assertEquals(-1, PitHitTester.hitSlot(x, y - minimum * 1.05f, board, geometry, minimum))
    }

    @Test
    fun aTapOnTheTableOrOnAStoreHitsNoPit() {
        val board = rect(2400f, 1080f)
        assertEquals(-1, PitHitTester.hitSlot(5f, 5f, board, geometry, 10f))
        val storeX = board.x(geometry.storeCx(StoreSide.RIGHT))
        val storeY = board.y(geometry.storeCy(StoreSide.RIGHT))
        assertEquals(-1, PitHitTester.hitSlot(storeX, storeY, board, geometry, 10f))
        val leftX = board.x(geometry.storeCx(StoreSide.LEFT))
        assertEquals(-1, PitHitTester.hitSlot(leftX, storeY, board, geometry, 10f))
    }

    @Test
    fun whenTwoCirclesOverlapTheNearestPitWins() {
        val board = rect(2400f, 1080f)
        val huge = board.width // every pit circle overlaps its neighbours
        val x = board.x(geometry.slotCx(1)) + 20f
        val y = board.y(geometry.slotCy(1))
        assertEquals(1, PitHitTester.hitSlot(x, y, board, geometry, huge))
    }

    // ---- Seed slots ----

    /**
     * How far the oval seed reaches from the bowl centre: the distance of its centre plus the half
     * size of the oval in the direction away from the centre. The upright seed is turned clockwise
     * by its rotation, so its long axis points to (sin, -cos).
     */
    private fun reach(x: Float, y: Float, rotationDegrees: Float): Float {
        val halfLong = geometry.seedLengthW / 2f
        val halfShort = halfLong * geometry.seedAspectWOverH
        val distance = sqrt(x * x + y * y)
        if (distance < 0.000001f) return halfLong
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val axisX = Math.sin(radians).toFloat()
        val axisY = -Math.cos(radians).toFloat()
        val along = (x * axisX + y * axisY) / distance
        val across = 1f - along * along
        return distance + sqrt(halfLong * halfLong * along * along + halfShort * halfShort * across)
    }

    @Test
    fun everyPitSeedStaysInsideTheBowlForAllCaps() {
        val spread = geometry.pitInnerBowlRadiusW * SeedLayout.PIT_SPREAD_OF_BOWL
        val cap = geometry.maxSeedsPerPit
        for (slot in 0 until BoardGeometry.SLOT_COUNT) {
            val key = SeedLayout.pitKey(slot)
            for (i in 0 until cap) {
                val x = SeedLayout.offsetX(i, key, spread, cap)
                val y = SeedLayout.offsetY(i, key, spread, cap)
                val far = reach(x, y, SeedLayout.rotationDegrees(i, key))
                assertTrue("pit slot $slot seed $i reaches $far", far <= geometry.pitInnerBowlRadiusW)
            }
        }
    }

    @Test
    fun everyStoreSeedStaysInsideTheBowlOpeningForAllCaps() {
        val spread = geometry.storeBowlOpeningRadiusW * SeedLayout.STORE_SPREAD_OF_OPENING
        val cap = geometry.maxSeedsPerStore
        for (side in StoreSide.entries) {
            val key = SeedLayout.storeKey(side)
            for (i in 0 until cap) {
                val x = SeedLayout.offsetX(i, key, spread, cap)
                val y = SeedLayout.offsetY(i, key, spread, cap)
                val far = reach(x, y, SeedLayout.rotationDegrees(i, key))
                assertTrue("store $side seed $i reaches $far", far <= geometry.storeBowlOpeningRadiusW)
            }
        }
    }

    @Test
    fun aSlotNeverChangesWhenTheCountChanges() {
        val spread = geometry.pitInnerBowlRadiusW * SeedLayout.PIT_SPREAD_OF_BOWL
        val cap = geometry.maxSeedsPerPit
        for (i in 0 until cap) {
            val first = SeedLayout.offsetX(i, 5, spread, cap)
            val again = SeedLayout.offsetX(i, 5, spread, cap)
            assertEquals(first, again, 0f)
            assertEquals(SeedLayout.rotationDegrees(i, 5), SeedLayout.rotationDegrees(i, 5), 0f)
        }
    }

    @Test
    fun rotationsStayBelowOneHundredEightyDegrees() {
        for (key in 1..21) {
            for (i in 0 until 30) {
                val degrees = SeedLayout.rotationDegrees(i, key)
                assertTrue(degrees >= 0f && degrees < 180f)
            }
        }
    }

    @Test
    fun theKeysAreDifferentForEveryPitAndStore() {
        val keys = HashSet<Int>()
        for (slot in 0 until BoardGeometry.SLOT_COUNT) {
            assertTrue(keys.add(SeedLayout.pitKey(slot)))
        }
        assertTrue(keys.add(SeedLayout.storeKey(StoreSide.LEFT)))
        assertTrue(keys.add(SeedLayout.storeKey(StoreSide.RIGHT)))
        assertEquals(14, keys.size)
    }

    @Test
    fun theFirstSlotsFollowTheGoldenAngleFormula() {
        val spread = 0.02f
        val cap = 14
        val key = 3
        for (i in 0 until cap) {
            val radius = spread * sqrt((i + 0.5f) / cap)
            val angle = i * 2.39996f + key * 1.7f
            assertEquals(radius * kotlin.math.cos(angle), SeedLayout.offsetX(i, key, spread, cap), 0.00001f)
            assertEquals(radius * kotlin.math.sin(angle), SeedLayout.offsetY(i, key, spread, cap), 0.00001f)
            assertEquals(((i * 67 + key * 41) % 180).toFloat(), SeedLayout.rotationDegrees(i, key), 0f)
        }
    }
}
