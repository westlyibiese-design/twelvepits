package com.westly.twelvepits.feature.game.ui

/** Thrown when `board_geometry.json` is missing something or holds a value that cannot be used. */
class BoardGeometryException(message: String) : RuntimeException(message)

/**
 * Every position of the board as fractions of `board.jpg`, read once from `board_geometry.json`.
 * Immutable. Pure Kotlin. x fractions are of the board width, y fractions are of the board height,
 * and names ending in `W` are fractions of the board width.
 *
 * Slots 0 to 5 are the bottom row left to right, slots 6 to 11 are the top row right to left
 * (the same order as the pit numbers of RULES.md for perspective ONE).
 */
class BoardGeometry private constructor(
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val aspectRatio: Float,
    private val xs: FloatArray,
    private val ys: FloatArray,
    val pitOuterRingRadiusW: Float,
    val pitInnerBowlRadiusW: Float,
    val rightStoreCx: Float,
    val rightStoreCy: Float,
    val leftStoreCx: Float,
    val leftStoreCy: Float,
    val storeOuterRadiusW: Float,
    val storeBowlOpeningRadiusW: Float,
    val seedAspectWOverH: Float,
    val seedLengthW: Float,
    val pitBadgeDiameterW: Float,
    val pitBadgeMinDiameterDp: Float,
    val topRowPitBadgeCy: Float,
    val bottomRowPitBadgeCy: Float,
    val storeBadgeDiameterW: Float,
    val storeBadgeCy: Float,
    val maxSeedsPerPit: Int,
    val maxSeedsPerStore: Int,
) {
    fun slotCx(slot: Int): Float = xs[slot]

    fun slotCy(slot: Int): Float = ys[slot]

    /** The y of a pit's count badge: on the rim facing the middle gap of the board. */
    fun slotBadgeCy(slot: Int): Float = if (slot < SLOTS_PER_ROW) bottomRowPitBadgeCy else topRowPitBadgeCy

    fun storeCx(side: StoreSide): Float = if (side == StoreSide.RIGHT) rightStoreCx else leftStoreCx

    fun storeCy(side: StoreSide): Float = if (side == StoreSide.RIGHT) rightStoreCy else leftStoreCy

    companion object {
        const val SLOT_COUNT = 12
        const val SLOTS_PER_ROW = 6

        private const val KEY_BOTTOM = "player1_pits_0_to_5_bottom_row_left_to_right"
        private const val KEY_TOP = "player2_pits_6_to_11_top_row_right_to_left"

        /** Reads and checks the JSON text. Throws [BoardGeometryException] when anything is wrong. */
        fun parse(json: String): BoardGeometry {
            val root = try {
                JsonReader(json).readDocument()
            } catch (e: JsonFormatException) {
                throw BoardGeometryException("board_geometry.json is not valid JSON: ${e.message}")
            }
            val rootMap = asMap(root, "the file")

            val cx = FloatArray(SLOT_COUNT)
            val cy = FloatArray(SLOT_COUNT)
            readRow(rootMap, KEY_BOTTOM, 0, cx, cy)
            readRow(rootMap, KEY_TOP, SLOTS_PER_ROW, cx, cy)

            val image = asMap(rootMap["board_image"], "board_image")
            val pit = asMap(rootMap["pit"], "pit")
            val stores = asMap(rootMap["stores"], "stores")
            val rightStore = asMap(stores["player1_store_right"], "stores.player1_store_right")
            val leftStore = asMap(stores["player2_store_left"], "stores.player2_store_left")
            val seed = asMap(rootMap["seed"], "seed")
            val badges = asMap(rootMap["count_badges"], "count_badges")
            val caps = asMap(rootMap["visible_seed_caps"], "visible_seed_caps")

            val widthPx = positive(image, "width_px", "board_image")
            val heightPx = positive(image, "height_px", "board_image")
            return BoardGeometry(
                imageWidthPx = widthPx.toInt(),
                imageHeightPx = heightPx.toInt(),
                aspectRatio = positive(image, "aspect_ratio", "board_image"),
                xs = cx,
                ys = cy,
                pitOuterRingRadiusW = fraction(pit, "outer_ring_radius_w", "pit"),
                pitInnerBowlRadiusW = fraction(pit, "inner_bowl_radius_w", "pit"),
                rightStoreCx = fraction(rightStore, "cx", "stores.player1_store_right"),
                rightStoreCy = fraction(rightStore, "cy", "stores.player1_store_right"),
                leftStoreCx = fraction(leftStore, "cx", "stores.player2_store_left"),
                leftStoreCy = fraction(leftStore, "cy", "stores.player2_store_left"),
                storeOuterRadiusW = fraction(stores, "outer_radius_w", "stores"),
                storeBowlOpeningRadiusW = fraction(stores, "bowl_opening_radius_w", "stores"),
                seedAspectWOverH = positive(seed, "aspect_w_over_h", "seed"),
                seedLengthW = fraction(seed, "length_in_pit_w", "seed"),
                pitBadgeDiameterW = fraction(badges, "pit_badge_diameter_w", "count_badges"),
                pitBadgeMinDiameterDp = positive(badges, "min_diameter_dp", "count_badges"),
                topRowPitBadgeCy = fraction(badges, "top_row_pit_badge_cy", "count_badges"),
                bottomRowPitBadgeCy = fraction(badges, "bottom_row_pit_badge_cy", "count_badges"),
                storeBadgeDiameterW = fraction(badges, "store_badge_diameter_w", "count_badges"),
                storeBadgeCy = fraction(badges, "store_badge_cy", "count_badges"),
                maxSeedsPerPit = positive(caps, "per_pit", "visible_seed_caps").toInt(),
                maxSeedsPerStore = positive(caps, "per_store", "visible_seed_caps").toInt(),
            )
        }

        private fun readRow(root: Map<String, Any?>, key: String, firstSlot: Int, cx: FloatArray, cy: FloatArray) {
            val list = root[key] as? List<*>
                ?: throw BoardGeometryException("board_geometry.json is missing the list \"$key\".")
            if (list.size != SLOTS_PER_ROW) {
                throw BoardGeometryException("\"$key\" must hold $SLOTS_PER_ROW pits but holds ${list.size}.")
            }
            for (index in 0 until SLOTS_PER_ROW) {
                val entry = asMap(list[index], "$key[$index]")
                val pitNumber = positiveOrZero(entry, "pit", key).toInt()
                if (pitNumber != firstSlot + index) {
                    throw BoardGeometryException("\"$key\" entry $index must be pit ${firstSlot + index} but is $pitNumber.")
                }
                cx[firstSlot + index] = fraction(entry, "cx", "$key[$index]")
                cy[firstSlot + index] = fraction(entry, "cy", "$key[$index]")
            }
        }

        private fun asMap(value: Any?, where: String): Map<String, Any?> {
            val map = value as? Map<*, *>
                ?: throw BoardGeometryException("board_geometry.json is missing $where.")
            val result = LinkedHashMap<String, Any?>()
            for ((k, v) in map) {
                if (k is String) result[k] = v
            }
            return result
        }

        private fun number(map: Map<String, Any?>, key: String, where: String): Float {
            val value = map[key] as? Double
                ?: throw BoardGeometryException("board_geometry.json is missing the number \"$key\" in $where.")
            if (value.isNaN() || value.isInfinite()) {
                throw BoardGeometryException("\"$key\" in $where is not a usable number.")
            }
            return value.toFloat()
        }

        private fun positive(map: Map<String, Any?>, key: String, where: String): Float {
            val value = number(map, key, where)
            if (value <= 0f) throw BoardGeometryException("\"$key\" in $where must be above zero.")
            return value
        }

        private fun positiveOrZero(map: Map<String, Any?>, key: String, where: String): Float {
            val value = number(map, key, where)
            if (value < 0f) throw BoardGeometryException("\"$key\" in $where must not be negative.")
            return value
        }

        /** A fraction of the board: above zero and at most one. */
        private fun fraction(map: Map<String, Any?>, key: String, where: String): Float {
            val value = number(map, key, where)
            if (value <= 0f || value > 1f) {
                throw BoardGeometryException("\"$key\" in $where must be a fraction between 0 and 1 but is $value.")
            }
            return value
        }
    }
}
