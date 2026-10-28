package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player

enum class GameStatus {
    IN_PROGRESS,
    FINISHED,
}

/**
 * The whole position of a game. Immutable: the engine never changes a state,
 * it builds a new one. `pits` is indexed 0 to 11 as in RULES.md section 1:
 * Player ONE owns pits 0 to 5, Player TWO owns pits 6 to 11.
 */
data class GameState(
    val pits: List<Int>,
    val storeOne: Int,
    val storeTwo: Int,
    val currentPlayer: Player,
    val startingPlayer: Player,
    val capturedTotal: Int,
    val moveNumber: Int,
    val status: GameStatus,
    val leftoverInHand: Int = 0,
) {
    fun storeOf(player: Player): Int = if (player == Player.ONE) storeOne else storeTwo

    fun ownerOf(pit: Int): Player = ownerOfPit(pit)

    fun seedsOnSide(player: Player): Int {
        var total = 0
        for (pit in pitsOf(player)) {
            total += pits.getOrElse(pit) { 0 }
        }
        return total
    }

    /** The current player's non-empty pits. Empty when the game is finished. */
    fun legalMoves(): List<Int> {
        if (status != GameStatus.IN_PROGRESS) return emptyList()
        return pitsOf(currentPlayer).filter { pits.getOrElse(it) { 0 } > 0 }
    }

    /** The player with the larger store, only when the game is finished. */
    val winner: Player?
        get() {
            if (status != GameStatus.FINISHED) return null
            return when {
                storeOne > storeTwo -> Player.ONE
                storeTwo > storeOne -> Player.TWO
                else -> null
            }
        }

    /** True only when this state follows every rule of RULES.md and can be trusted. */
    fun isValid(): Boolean {
        if (pits.size != PIT_COUNT) return false
        if (pits.any { it < 0 }) return false
        if (storeOne < 0 || storeTwo < 0) return false
        if (storeOne % CAPTURE_SIZE != 0 || storeTwo % CAPTURE_SIZE != 0) return false
        if (moveNumber < 0 || leftoverInHand < 0) return false
        if (capturedTotal != storeOne + storeTwo) return false
        if (capturedTotal > CAPTURE_TARGET) return false
        val seedsEverywhere =
            pits.sumOf { it.toLong() } + storeOne.toLong() + storeTwo.toLong() + leftoverInHand.toLong()
        if (seedsEverywhere != TOTAL_SEEDS.toLong()) return false
        val shouldBeFinished = capturedTotal == CAPTURE_TARGET
        if ((status == GameStatus.FINISHED) != shouldBeFinished) return false
        if (status == GameStatus.IN_PROGRESS) {
            if (leftoverInHand != 0) return false
            if (seedsOnSide(currentPlayer) == 0) return false
        }
        return true
    }

    /** Plain Kotlin values only (Int, String, List of Int), ready to be stored by Phase 5. */
    fun toSaveMap(): Map<String, Any> = mapOf(
        KEY_SCHEMA_VERSION to SCHEMA_VERSION,
        KEY_PITS to pits.toList(),
        KEY_STORE_ONE to storeOne,
        KEY_STORE_TWO to storeTwo,
        KEY_CURRENT_PLAYER to currentPlayer.name,
        KEY_STARTING_PLAYER to startingPlayer.name,
        KEY_CAPTURED_TOTAL to capturedTotal,
        KEY_MOVE_NUMBER to moveNumber,
        KEY_STATUS to status.name,
        KEY_LEFTOVER_IN_HAND to leftoverInHand,
    )

    companion object {
        const val PIT_COUNT = 12
        const val PITS_PER_SIDE = 6
        const val SEEDS_PER_PIT = 4
        const val TOTAL_SEEDS = 48
        const val CAPTURE_SIZE = 4
        const val CAPTURE_TARGET = 44

        const val SCHEMA_VERSION = 1
        const val KEY_SCHEMA_VERSION = "schemaVersion"
        const val KEY_PITS = "pits"
        const val KEY_STORE_ONE = "storeOne"
        const val KEY_STORE_TWO = "storeTwo"
        const val KEY_CURRENT_PLAYER = "currentPlayer"
        const val KEY_STARTING_PLAYER = "startingPlayer"
        const val KEY_CAPTURED_TOTAL = "capturedTotal"
        const val KEY_MOVE_NUMBER = "moveNumber"
        const val KEY_STATUS = "status"
        const val KEY_LEFTOVER_IN_HAND = "leftoverInHand"

        fun initial(startingPlayer: Player): GameState = GameState(
            pits = List(PIT_COUNT) { SEEDS_PER_PIT },
            storeOne = 0,
            storeTwo = 0,
            currentPlayer = startingPlayer,
            startingPlayer = startingPlayer,
            capturedTotal = 0,
            moveNumber = 0,
            status = GameStatus.IN_PROGRESS,
            leftoverInHand = 0,
        )

        fun ownerOfPit(pit: Int): Player {
            require(pit in 0 until PIT_COUNT) { "Pit index out of range: $pit" }
            return if (pit < PITS_PER_SIDE) Player.ONE else Player.TWO
        }

        fun pitsOf(player: Player): IntRange =
            if (player == Player.ONE) 0 until PITS_PER_SIDE else PITS_PER_SIDE until PIT_COUNT

        /** Returns null when the map is incomplete, has wrong types, or does not follow the rules. */
        fun fromSaveMap(map: Map<String, Any?>): GameState? {
            if (intValue(map[KEY_SCHEMA_VERSION]) != SCHEMA_VERSION) return null
            val rawPits = map[KEY_PITS] as? List<*> ?: return null
            val pitValues = ArrayList<Int>(rawPits.size)
            for (raw in rawPits) {
                pitValues.add(intValue(raw) ?: return null)
            }
            val state = GameState(
                pits = pitValues,
                storeOne = intValue(map[KEY_STORE_ONE]) ?: return null,
                storeTwo = intValue(map[KEY_STORE_TWO]) ?: return null,
                currentPlayer = playerValue(map[KEY_CURRENT_PLAYER]) ?: return null,
                startingPlayer = playerValue(map[KEY_STARTING_PLAYER]) ?: return null,
                capturedTotal = intValue(map[KEY_CAPTURED_TOTAL]) ?: return null,
                moveNumber = intValue(map[KEY_MOVE_NUMBER]) ?: return null,
                status = statusValue(map[KEY_STATUS]) ?: return null,
                leftoverInHand = intValue(map[KEY_LEFTOVER_IN_HAND]) ?: return null,
            )
            return if (state.isValid()) state else null
        }

        private fun intValue(raw: Any?): Int? = when (raw) {
            is Int -> raw
            is Long ->
                if (raw >= Int.MIN_VALUE.toLong() && raw <= Int.MAX_VALUE.toLong()) raw.toInt() else null
            else -> null
        }

        private fun playerValue(raw: Any?): Player? = Player.entries.firstOrNull { it.name == raw }

        private fun statusValue(raw: Any?): GameStatus? = GameStatus.entries.firstOrNull { it.name == raw }
    }
}
