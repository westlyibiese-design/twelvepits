package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.Player

/**
 * The rules of RULES.md sections 3 to 8 (including the endless relay rule of section 4).
 * Pure Kotlin, no Android imports.
 * `playTurn` and `simulate` both run the same private `execute`, so they can never disagree.
 */
class SowingEngine(
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
    private val logger: EngineLogger = NoOpEngineLogger,
) {
    init {
        require(maxSteps > 0) { "maxSteps must be positive" }
    }

    /** Plays one turn and returns the new state with the full event script. */
    fun playTurn(state: GameState, pit: Int): TurnResult = execute(state, pit, ArrayList<SowEvent>())

    /** Same logic as playTurn but records no events (fast path for the computer player). */
    fun simulate(state: GameState, pit: Int): GameState = execute(state, pit, null).newState

    private fun execute(state: GameState, startPit: Int, events: MutableList<SowEvent>?): TurnResult {
        checkMoveIsLegal(state, startPit)

        val mover = state.currentPlayer
        val board = IntArray(GameState.PIT_COUNT) { state.pits[it] }
        // Index 0 is Player ONE's store and index 1 is Player TWO's store (Player.ordinal).
        val stores = intArrayOf(state.storeOne, state.storeTwo)
        var capturedTotal = state.capturedTotal
        var capturedForMover = 0
        var capturedForOpponent = 0
        var steps = 0
        var outcome = TurnOutcome.TURN_ENDED
        var leftover = 0
        // Every (board, landing pit) seen at a relay in this turn. Seeing one again means an endless relay.
        val seenLandings = HashSet<List<Int>>()

        var hand = board[startPit]
        board[startPit] = 0
        events?.add(SowEvent.PickUp(startPit, hand))
        var current = startPit

        sowing@ while (true) {
            while (hand > 0) {
                if (steps >= maxSteps) {
                    // Safety net: keep the total at 48 by putting the seeds in hand back.
                    board[current] += hand
                    outcome = TurnOutcome.STEP_LIMIT
                    events?.add(SowEvent.StepLimitReached)
                    logger.error("Step limit of $maxSteps reached in one turn (start pit $startPit).")
                    break@sowing
                }
                current = (current + 1) % GameState.PIT_COUNT
                board[current] += 1
                hand -= 1
                steps += 1
                events?.add(SowEvent.Drop(current, board[current], hand))

                if (board[current] == GameState.CAPTURE_SIZE) {
                    // The pit's owner takes the 4 seeds, except when this is the LAST seed of the sowing:
                    // then the player who sowed takes them, even from the opponent's pit (RULES.md section 5).
                    val owner = if (hand == 0) mover else GameState.ownerOfPit(current)
                    stores[owner.ordinal] += GameState.CAPTURE_SIZE
                    board[current] = 0
                    capturedTotal += GameState.CAPTURE_SIZE
                    if (owner == mover) {
                        capturedForMover += GameState.CAPTURE_SIZE
                    } else {
                        capturedForOpponent += GameState.CAPTURE_SIZE
                    }
                    events?.add(SowEvent.Capture(current, owner, stores[owner.ordinal]))

                    if (capturedTotal >= GameState.CAPTURE_TARGET) {
                        leftover = hand
                        outcome = TurnOutcome.GAME_OVER
                        events?.add(SowEvent.GameOver(winnerOf(stores), stores[0], stores[1], leftover))
                        break@sowing
                    }
                    if (hand == 0) {
                        // The last seed made a capture: the turn ends.
                        break@sowing
                    }
                }
            }

            // The last seed has landed on `current` without a capture.
            if (board[current] == 1) {
                // The pit was empty: the turn ends.
                break
            }
            if (!seenLandings.add(landingKey(board, current))) {
                // Endless relay (RULES.md section 4): the same board has come back, so the turn ends.
                outcome = TurnOutcome.LOOP_STOPPED
                events?.add(SowEvent.LoopStopped)
                break
            }
            // The pit already had seeds: relay with the whole pit.
            events?.add(SowEvent.RelayPause(current))
            hand = board[current]
            board[current] = 0
            events?.add(SowEvent.PickUp(current, hand))
        }

        var nextPlayer = mover
        if (outcome != TurnOutcome.GAME_OVER) {
            val opponent = mover.opponent
            if (seedsOnSide(board, opponent) > 0) {
                nextPlayer = opponent
            } else if (seedsOnSide(board, mover) > 0) {
                // The opponent has no seeds: their turn is skipped and the mover plays again.
                events?.add(SowEvent.TurnSkipped(opponent))
            } else {
                // Impossible while 4 or more seeds stay on the board. Treated like the step limit.
                logger.error("Both sides are empty before the game ended (start pit $startPit).")
                if (outcome == TurnOutcome.TURN_ENDED) {
                    events?.add(SowEvent.StepLimitReached)
                    outcome = TurnOutcome.STEP_LIMIT
                }
            }
        }

        val newState = GameState(
            pits = board.toList(),
            storeOne = stores[0],
            storeTwo = stores[1],
            currentPlayer = nextPlayer,
            startingPlayer = state.startingPlayer,
            capturedTotal = capturedTotal,
            moveNumber = state.moveNumber + 1,
            status = if (outcome == TurnOutcome.GAME_OVER) GameStatus.FINISHED else GameStatus.IN_PROGRESS,
            leftoverInHand = leftover,
        )
        return TurnResult(
            newState = newState,
            events = events ?: emptyList(),
            capturedForMover = capturedForMover,
            capturedForOpponent = capturedForOpponent,
            outcome = outcome,
        )
    }

    private fun checkMoveIsLegal(state: GameState, pit: Int) {
        if (state.status != GameStatus.IN_PROGRESS) {
            throw IllegalMoveException("The game is already finished.")
        }
        if (state.pits.size != GameState.PIT_COUNT) {
            throw IllegalMoveException("The board must have ${GameState.PIT_COUNT} pits.")
        }
        if (pit < 0 || pit >= GameState.PIT_COUNT) {
            throw IllegalMoveException("Pit $pit does not exist.")
        }
        if (GameState.ownerOfPit(pit) != state.currentPlayer) {
            throw IllegalMoveException("Pit $pit belongs to the opponent.")
        }
        if (state.pits[pit] == 0) {
            throw IllegalMoveException("Pit $pit is empty.")
        }
    }

    private fun landingKey(board: IntArray, landingPit: Int): List<Int> {
        val key = ArrayList<Int>(board.size + 1)
        for (count in board) {
            key.add(count)
        }
        key.add(landingPit)
        return key
    }

    private fun seedsOnSide(board: IntArray, player: Player): Int {
        var total = 0
        for (pit in GameState.pitsOf(player)) {
            total += board[pit]
        }
        return total
    }

    // A draw is impossible (RULES.md section 7), so the larger store always decides.
    private fun winnerOf(stores: IntArray): Player = if (stores[0] > stores[1]) Player.ONE else Player.TWO

    companion object {
        const val DEFAULT_MAX_STEPS = 10_000
    }
}
