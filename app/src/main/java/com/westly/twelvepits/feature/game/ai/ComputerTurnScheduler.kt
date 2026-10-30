package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.feature.game.engine.EngineLogger
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.NoOpEngineLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Runs the computer's turn: waits a moment so the human can follow the board, asks the
 * [ComputerPlayer] on a background thread, then hands over the chosen pit. It never hands over a
 * pit that is not a legal move: it logs an error and uses the first legal pit instead.
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
            pause(thinkMillis)
            val chosen = withContext(computeContext) { player.choosePit(state) }
            val legal = state.legalMoves()
            if (legal.isEmpty()) {
                logger.error("The computer has no legal pit to play.")
                job = null
                return@launch
            }
            val pit = if (chosen in legal) {
                chosen
            } else {
                logger.error("The computer chose pit $chosen, which is not a legal move. Using pit ${legal.first()}.")
                legal.first()
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

    companion object {
        /** The pause before the computer plays, so the human can follow the board (0.8 s). */
        const val THINK_MILLIS = 800L
    }
}
