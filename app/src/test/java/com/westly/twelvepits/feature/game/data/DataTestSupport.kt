package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.SowingEngine
import com.westly.twelvepits.feature.game.engine.randomLegalMove
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

/** A saved file kept in memory. [editCount] counts how many separate edits were made. */
internal class FakeRawGameStorage(initial: RawGameData = RawGameData()) : RawGameStorage {
    private val current = MutableStateFlow(initial)
    var editCount = 0
        private set
    var failEdits = false

    val value: RawGameData
        get() = current.value

    override val raw: Flow<RawGameData> = current

    override suspend fun edit(change: (RawGameData) -> RawGameData) {
        if (failEdits) throw java.io.IOException("disk full")
        editCount += 1
        current.value = change(current.value)
    }

    fun overwriteSavedGame(text: String?) {
        current.value = current.value.copy(savedGame = text)
    }
}

internal fun testConfig(
    mode: GameMode = GameMode.VS_COMPUTER,
    difficulty: Difficulty = Difficulty.NORMAL,
): GameConfig = GameConfig(
    mode = mode,
    difficulty = difficulty,
    player1Name = "Ada",
    player1Avatar = AvatarId.KING,
    player2Name = if (mode == GameMode.VS_COMPUTER) "Computer" else "Bola",
    player2Avatar = if (mode == GameMode.VS_COMPUTER) AvatarId.LEOPARD else AvatarId.QUEEN,
)

/**
 * The position after [turns] random turns, with the best captures of both players. With
 * [stopBeforeFinish] the last turn is left out if it would finish the game.
 */
internal fun playTurns(
    start: GameState,
    turns: Int,
    seed: Int,
    stopBeforeFinish: Boolean = false,
): Pair<GameState, TurnStats> {
    val engine = SowingEngine()
    val random = Random(seed)
    var state = start
    var stats = TurnStats()
    repeat(turns) {
        if (state.status == com.westly.twelvepits.feature.game.engine.GameStatus.IN_PROGRESS) {
            val mover = state.currentPlayer
            val result = engine.playTurn(state, randomLegalMove(state, random))
            if (stopBeforeFinish && result.newState.status == com.westly.twelvepits.feature.game.engine.GameStatus.FINISHED) {
                return state to stats
            }
            stats = stats.afterTurn(mover, result.capturedForMover)
            state = result.newState
        }
    }
    return state to stats
}

internal fun midGameSave(
    config: GameConfig = testConfig(),
    turns: Int = 5,
    seed: Int = 3,
    starter: Player = Player.ONE,
    elapsedMs: Long = 123_456L,
): SavedGame {
    val (state, stats) = playTurns(GameState.initial(starter), turns, seed, stopBeforeFinish = true)
    return SavedGame(config = config, state = state, elapsedMs = elapsedMs, stats = stats)
}

/** A finished game, played with random moves. */
internal fun finishedGame(starter: Player, seed: Int): Pair<GameState, TurnStats> =
    playTurns(GameState.initial(starter), 100_000, seed)
