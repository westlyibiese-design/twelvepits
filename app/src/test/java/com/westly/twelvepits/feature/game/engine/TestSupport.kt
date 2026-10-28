package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player
import kotlin.random.Random

/** A position built by hand. The seed total is not forced to 48: the engine only needs a legal move. */
internal fun boardState(
    pits: List<Int>,
    current: Player = Player.ONE,
    storeOne: Int = 0,
    storeTwo: Int = 0,
): GameState = GameState(
    pits = pits,
    storeOne = storeOne,
    storeTwo = storeTwo,
    currentPlayer = current,
    startingPlayer = current,
    capturedTotal = storeOne + storeTwo,
    moveNumber = 0,
    status = GameStatus.IN_PROGRESS,
)

internal fun randomLegalMove(state: GameState, random: Random): Int {
    val legal = state.legalMoves()
    return legal[random.nextInt(legal.size)]
}

/** Plays random legal moves until the game is finished. Fails if it never finishes. */
internal fun playToEnd(engine: SowingEngine, start: GameState, seed: Int): GameState {
    val random = Random(seed)
    var state = start
    var turns = 0
    while (state.status == GameStatus.IN_PROGRESS) {
        state = engine.playTurn(state, randomLegalMove(state, random)).newState
        turns += 1
        check(turns < 100_000) { "Game did not finish. Position: $state" }
    }
    return state
}

internal class RecordingLogger : EngineLogger {
    val messages = ArrayList<String>()

    override fun error(message: String) {
        messages.add(message)
    }
}
