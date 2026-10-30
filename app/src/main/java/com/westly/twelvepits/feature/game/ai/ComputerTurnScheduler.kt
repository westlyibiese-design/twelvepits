package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.feature.game.engine.EngineLogger
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.NoOpEngineLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Runs the computer's turn. The [ComputerPlayer] thinks on a background thread while the scheduler
 * waits [thinkMillis], so the thinking time counts inside the wait: a quick player plays after
 * [thinkMillis], a slow one as soon as it has answered. It never hands over a pit that is not a legal
 * move: it logs an error and uses the first legal pit instead.
 *
 * [start] and [cancel] are called from the main thread. [onPitChosen] runs in [scope] (the main thread).
 * [pause] and [computeContext] can be replaced in unit tests.
 * Pure Kotlin, no Android imports.
 */
class ComputerTurnScheduler(
    private val player: ComputerPlayer,
    private val scope: CoroutineScope,
    private val onPitChosen: (state: GameState, pit: Int) -> Unit,
    private val logger: EngineLogger = NoOpEngineLogger,
    private val thinkMillis: Long = THINK_MILLIS,
    private val computeContext: CoroutineContext = Dispatchers.Default,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private var job: Job? = null

    /** True from [start] until the pit is handed over or [cancel] is called. */
    val isThinking: Boolean
        get() = job?.isActive == true

    /** Starts thinking about [state]. Does nothing if the computer is already thinking. */
    fun start(state: GameState) {
        if (isThinking) return
        job = scope.launch {
            val legal = state.legalMoves()
            if (legal.isEmpty()) {
                logger.error("The computer has no legal pit to play.")
                job = null
                return@launch
            }
            val chosen = think(state)
            val pit = when {
                chosen == null -> legal.first()
                chosen in legal -> chosen
                else -> {
                    logger.error("The computer chose pit $chosen, which is not a legal move. Using pit ${legal.first()}.")
                    legal.first()
                }
            }
            job = null
            onPitChosen(state, pit)
        }
    }

    /** Stops thinking. The next [start] begins again with the full waiting time. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** The pit the player chose, or null when the player failed (the first legal pit is used then). */
    private suspend fun think(state: GameState): Int? {
        try {
            return coroutineScope {
                val answer = async(computeContext) { player.choosePit(state) }
                pause(thinkMillis)
                answer.await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            logger.error("The computer player failed: ${failure.message}")
            return null
        }
    }

    companion object {
        /** The least time the computer takes before it plays, so the human can follow the board (0.8 s). */
        const val THINK_MILLIS = 800L
    }
}
