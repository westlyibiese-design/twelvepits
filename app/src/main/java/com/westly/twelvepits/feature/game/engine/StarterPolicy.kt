package com.westly.twelvepits.feature.game.engine

import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player

/**
 * Who starts the NEXT new game, kept separately for each game mode (RULES.md section 2).
 * Only a finished game changes it (afterGameFinished) and only a score reset puts it back (reset).
 * Restart, abandoning a game and starting over an unfinished game have no function here on
 * purpose: they never change the starter.
 */
data class StarterPolicy(
    val starters: Map<GameMode, Player> = GameMode.entries.associateWith { Player.ONE },
) {
    fun starterFor(mode: GameMode): Player = starters[mode] ?: Player.ONE

    fun afterGameFinished(mode: GameMode, starterOfFinishedGame: Player): StarterPolicy =
        StarterPolicy(toMap() + (mode to starterOfFinishedGame.opponent))

    fun reset(): StarterPolicy = StarterPolicy()

    /** Plain value that Phase 5 can store. */
    fun toMap(): Map<GameMode, Player> = GameMode.entries.associateWith { starterFor(it) }

    companion object {
        fun fromMap(map: Map<GameMode, Player>): StarterPolicy =
            StarterPolicy(GameMode.entries.associateWith { map[it] ?: Player.ONE })
    }
}
