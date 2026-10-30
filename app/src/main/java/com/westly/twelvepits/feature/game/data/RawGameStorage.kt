package com.westly.twelvepits.feature.game.data

import kotlinx.coroutines.flow.Flow

/**
 * The three saved values exactly as they are written to disk. A missing value is null.
 * They live in ONE file, so one [RawGameStorage.edit] changes any of them together.
 */
data class RawGameData(
    val savedGame: String? = null,
    val scores: String? = null,
    val starters: String? = null,
)

/** Where [RawGameData] is kept. The app uses a DataStore file; unit tests use a fake in memory. */
interface RawGameStorage {
    val raw: Flow<RawGameData>

    /** Reads, changes and writes everything in one step that no other write can split. */
    suspend fun edit(change: (RawGameData) -> RawGameData)
}
