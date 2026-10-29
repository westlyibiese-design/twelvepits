package com.westly.twelvepits.feature.game.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the real file that ships in the app (Gradle runs unit tests from the `app` folder). */
class BoardGeometryTest {
    private val json: String = File("src/main/assets/board_geometry.json").readText()

    private fun parse(text: String): BoardGeometry = BoardGeometry.parse(text)

    private fun broken(from: String, to: String): String {
        assertTrue("The test needs to find $from in the real file", json.contains(from))
        val changed = json.replace(from, to)
        assertNotEquals(json, changed)
        return changed
    }

    @Test
    fun theRealFileParses() {
        val geometry = parse(json)
        assertEquals(1567, geometry.imageWidthPx)
        assertEquals(415, geometry.imageHeightPx)
        assertEquals(3.7759f, geometry.aspectRatio, 0.0001f)
    }

    @Test
    fun pitPositionsAreReadInSlotOrder() {
        val geometry = parse(json)
        val expectedX = floatArrayOf(0.2393f, 0.3427f, 0.4461f, 0.5495f, 0.6535f, 0.7575f)
        for (index in 0 until 6) {
            assertEquals(expectedX[index], geometry.slotCx(index), 0.00001f)
            assertEquals(0.7277f, geometry.slotCy(index), 0.00001f)
            // The top row runs right to left: pit 6 is above pit 5.
            assertEquals(expectedX[5 - index], geometry.slotCx(6 + index), 0.00001f)
            assertEquals(0.2578f, geometry.slotCy(6 + index), 0.00001f)
        }
    }

    @Test
    fun storesRingsSeedsAndBadgesAreRead() {
        val geometry = parse(json)
        assertEquals(0.9068f, geometry.storeCx(StoreSide.RIGHT), 0.00001f)
        assertEquals(0.09f, geometry.storeCx(StoreSide.LEFT), 0.00001f)
        assertEquals(0.5157f, geometry.storeCy(StoreSide.RIGHT), 0.00001f)
        assertEquals(0.5157f, geometry.storeCy(StoreSide.LEFT), 0.00001f)
        assertEquals(0.044f, geometry.pitOuterRingRadiusW, 0.00001f)
        assertEquals(0.0383f, geometry.pitInnerBowlRadiusW, 0.00001f)
        assertEquals(0.0906f, geometry.storeOuterRadiusW, 0.00001f)
        assertEquals(0.0715f, geometry.storeBowlOpeningRadiusW, 0.00001f)
        assertEquals(0.5827f, geometry.seedAspectWOverH, 0.00001f)
        assertEquals(0.0319f, geometry.seedLengthW, 0.00001f)
        assertEquals(0.027f, geometry.pitBadgeDiameterW, 0.00001f)
        assertEquals(20f, geometry.pitBadgeMinDiameterDp, 0.00001f)
        assertEquals(0.4241f, geometry.topRowPitBadgeCy, 0.00001f)
        assertEquals(0.5614f, geometry.bottomRowPitBadgeCy, 0.00001f)
        assertEquals(0.045f, geometry.storeBadgeDiameterW, 0.00001f)
        assertEquals(0.7855f, geometry.storeBadgeCy, 0.00001f)
        assertEquals(14, geometry.maxSeedsPerPit)
        assertEquals(30, geometry.maxSeedsPerStore)
    }

    @Test
    fun badgesSitOnTheRimFacingTheMiddleGap() {
        val geometry = parse(json)
        assertEquals(0.5614f, geometry.slotBadgeCy(0), 0.00001f)
        assertEquals(0.4241f, geometry.slotBadgeCy(6), 0.00001f)
    }

    @Test
    fun aMissingKeyIsRejected() {
        assertThrows(BoardGeometryException::class.java) {
            parse(broken("\"visible_seed_caps\"", "\"visible_seed_capz\""))
        }
    }

    @Test
    fun aFractionOutsideTheBoardIsRejected() {
        assertThrows(BoardGeometryException::class.java) {
            parse(broken("\"inner_bowl_radius_w\": 0.0383", "\"inner_bowl_radius_w\": 1.7"))
        }
    }

    @Test
    fun aWrongPitNumberIsRejected() {
        assertThrows(BoardGeometryException::class.java) {
            parse(broken("\"pit\": 3,", "\"pit\": 9,"))
        }
    }

    @Test
    fun aCapOfZeroIsRejected() {
        assertThrows(BoardGeometryException::class.java) {
            parse(broken("\"per_pit\": 14", "\"per_pit\": 0"))
        }
    }

    @Test
    fun textThatIsNotJsonIsRejected() {
        assertThrows(BoardGeometryException::class.java) { parse("this is not json") }
        assertThrows(BoardGeometryException::class.java) { parse("") }
        assertThrows(BoardGeometryException::class.java) { parse("[1, 2, 3]") }
    }
}
