package com.westly.twelvepits.feature.game.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Tells the menu whether an unfinished game was saved. Phase 5 adds the real saving. */
interface SavedGameStore {
    fun hasSavedGame(): Flow<Boolean>
}

// Replaced in Phase 5
class NoSavedGameStore : SavedGameStore {
    override fun hasSavedGame(): Flow<Boolean> = flowOf(false)
}
