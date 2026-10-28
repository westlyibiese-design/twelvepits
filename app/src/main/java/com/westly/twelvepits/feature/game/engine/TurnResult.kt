package com.westly.twelvepits.feature.game.engine

enum class TurnOutcome {
    TURN_ENDED,
    GAME_OVER,
    STEP_LIMIT,
}

/**
 * `capturedForMover` and `capturedForOpponent` are the seeds captured during this turn into
 * each side's store (the mover is the player who played the turn).
 */
data class TurnResult(
    val newState: GameState,
    val events: List<SowEvent>,
    val capturedForMover: Int,
    val capturedForOpponent: Int,
    val outcome: TurnOutcome,
)
