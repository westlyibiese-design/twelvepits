package com.westly.twelvepits.feature.game.ai

import com.westly.twelvepits.core.model.Difficulty
import kotlin.random.Random

/** Makes the computer player for a difficulty. */
object ComputerPlayers {
    fun forDifficulty(difficulty: Difficulty, random: Random = Random.Default): ComputerPlayer =
        when (difficulty) {
            Difficulty.EASY -> EasyPlayer(random)
            Difficulty.NORMAL -> NormalPlayer(random)
            Difficulty.HARD -> HardPlayer(random)
        }
}
