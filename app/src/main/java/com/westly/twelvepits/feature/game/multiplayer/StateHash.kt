package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.feature.game.engine.GameState

/**
 * A short number that describes a whole position, the same on both phones. It uses FNV-1a over the
 * numbers of the position, never `hashCode()` (the hash of an enum is not the same in two processes).
 */
object StateHash {
    fun of(state: GameState): Int {
        var hash = 0x811C9DC5.toInt()
        fun mix(value: Int) {
            for (shift in 0..24 step 8) {
                hash = (hash xor ((value ushr shift) and 0xFF)) * 16777619
            }
        }
        for (pit in state.pits) mix(pit)
        mix(state.storeOne)
        mix(state.storeTwo)
        mix(state.currentPlayer.ordinal)
        mix(state.startingPlayer.ordinal)
        mix(state.capturedTotal)
        mix(state.moveNumber)
        mix(state.status.ordinal)
        mix(state.leftoverInHand)
        return hash
    }
}
