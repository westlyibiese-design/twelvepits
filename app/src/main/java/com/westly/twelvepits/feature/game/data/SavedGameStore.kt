package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.StarterPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The saved games, the scores and the starter record (RULES.md sections 2 and 10). There is one saved
 * game for each computer level and one for Two players ([SaveSlot]).
 *
 * Every write goes through one queue and is done in the order it was asked for, by [scope] (the
 * application scope) and under [NonCancellable], so a write is never lost or overtaken because a
 * screen closed. A read that must see earlier writes goes through the same queue.
 * This class has no Android imports.
 */
class SavedGameStore(
    private val storage: RawGameStorage,
    private val scope: CoroutineScope,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (job in queue) {
                try {
                    withContext(NonCancellable) { job() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError("A game data write failed", e)
                }
            }
        }
    }

    /** Everything that is saved. A saved game that cannot be read shows up as null. */
    val data: Flow<GameData> = storage.raw.map { decode(it) }.distinctUntilChanged()

    /** The unfinished games, by slot. A slot without a game is missing. */
    val savedGames: Flow<Map<SaveSlot, SavedGame>> = data.map { it.savedGames }.distinctUntilChanged()

    val scores: Flow<Scores> = data.map { it.scores }.distinctUntilChanged()

    /**
     * Saves the position after a finished turn, in the slot of its own game. The other slots are not
     * touched. Returns at once; the write is queued.
     */
    fun saveGame(save: SavedGame) {
        enqueue { edit { it.withSavedGame(save) } }
    }

    /**
     * A game has finished. In ONE edit: the win is counted, the starter record moves on and the
     * saved game of that kind is deleted. Returns at once; the write is queued.
     */
    fun recordFinishedGame(config: GameConfig, finalState: GameState) {
        enqueue { edit { it.afterGameFinished(config, finalState) } }
    }

    /**
     * A two-phone game has finished. In ONE edit the win is counted for this phone and, on the host,
     * the starter record moves on. A saved game of another mode is not touched.
     */
    fun recordFinishedPhonesGame(localWon: Boolean, starterOfFinishedGame: Player?) {
        enqueue { edit { it.afterTwoPhonesGame(localWon, starterOfFinishedGame) } }
    }

    /** Deletes the saved game of the same kind as [config] and returns when it is gone. */
    suspend fun deleteSavedGame(config: GameConfig) {
        sequential { edit { it.withoutSavedGame(config) } }
    }

    /** Deletes every saved game (the player asked for it in Settings). Scores and settings stay. */
    suspend fun clearSavedGames() {
        sequential { edit { it.copy(savedGames = emptyMap()) } }
    }

    /** True when a valid saved game of the same kind as [config] exists. */
    suspend fun hasSavedGame(config: GameConfig): Boolean = sequential { readData().savedGameFor(config) != null }

    /** The saved game of the same kind as [config], or null. A damaged saved game is deleted. */
    suspend fun loadSavedGame(config: GameConfig): SavedGame? = sequential { readData().savedGameFor(config) }

    /** Deletes a damaged saved game, if there is one. Called when the app starts. */
    fun discardDamagedSave() {
        enqueue { readData() }
    }

    /** Clears the scores and the starter record, after the player confirmed. Saved games and settings stay. */
    suspend fun resetScores() {
        sequential { edit { it.afterScoresReset() } }
    }

    /** The position a game screen starts from, or null when [mode] is CONTINUE and the saved game cannot be loaded. */
    suspend fun prepareStart(config: GameConfig, mode: StartMode): GameStart? = sequential {
        val data = readData()
        val saved = data.savedGameFor(config)
        when (mode) {
            StartMode.NEW -> newGame(config.mode, data)
            StartMode.CONTINUE -> saved?.toStart()
            StartMode.RESTORE -> saved?.toStart() ?: newGame(config.mode, data)
        }
    }

    /** Who starts the next new game of [mode]. */
    suspend fun starterFor(mode: GameMode): Player = sequential { readData().starters.starterFor(mode) }

    private fun newGame(mode: GameMode, data: GameData): GameStart =
        GameStart(GameState.initial(data.starters.starterFor(mode)), TurnStats(), 0L)

    private fun SavedGame.toStart(): GameStart = GameStart(state, stats, elapsedMs)

    /**
     * Reads everything. A saved game that cannot be read is deleted on the spot, and a save written by
     * an older version is rewritten in the current format. The other slots are never lost.
     */
    private suspend fun readData(): GameData {
        val raw = storage.raw.first()
        val data = decode(raw)
        val rawSaves = raw.savedGame
        if (rawSaves != null) {
            val clean = SavedGamesCodec.encode(data.savedGames)
            if (clean != rawSaves) {
                storage.edit { it.copy(savedGame = clean) }
            }
        }
        return data
    }

    private suspend fun edit(change: (GameData) -> GameData) {
        storage.edit { raw -> encode(change(decode(raw))) }
    }

    private fun enqueue(job: suspend () -> Unit) {
        queue.trySend(job)
    }

    /** Runs [block] in the queue, after every earlier write, and returns its result. */
    private suspend fun <T> sequential(block: suspend () -> T): T {
        val done = CompletableDeferred<T>()
        enqueue {
            try {
                done.complete(block())
            } catch (e: Throwable) {
                done.completeExceptionally(e)
            }
        }
        return done.await()
    }

    private fun decode(raw: RawGameData): GameData = GameData(
        savedGames = raw.savedGame?.let { SavedGamesCodec.decode(it) } ?: emptyMap(),
        scores = raw.scores?.let { ScoresCodec.decode(it) } ?: Scores(),
        starters = raw.starters?.let { StarterCodec.decode(it) } ?: StarterPolicy(),
    )

    private fun encode(data: GameData): RawGameData = RawGameData(
        savedGame = SavedGamesCodec.encode(data.savedGames),
        scores = ScoresCodec.encode(data.scores),
        starters = StarterCodec.encode(data.starters),
    )
}
