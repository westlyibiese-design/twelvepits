package com.westly.twelvepits.navigation

import android.net.Uri
import android.os.Bundle
import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode

/** Route names of every screen built so far. */
object Routes {
    const val LAUNCH = "launch"
    const val MENU = "menu"
    const val MODE_SELECT = "mode_select"
    const val SETUP_COMPUTER = "setup_computer"
    const val SETUP_TWO_PLAYERS = "setup_two_players"
    const val HOW_TO_PLAY = "how_to_play"
    const val SETTINGS = "settings"

    /** The game screen (landscape). Every other route is portrait. */
    const val GAME_PATTERN =
        "game?mode={mode}&difficulty={difficulty}&p1={p1}&a1={a1}&p2={p2}&a2={a2}"

    const val ARG_MODE = "mode"
    const val ARG_DIFFICULTY = "difficulty"
    const val ARG_P1 = "p1"
    const val ARG_A1 = "a1"
    const val ARG_P2 = "p2"
    const val ARG_A2 = "a2"

    /** True for the game screen's route, the only landscape screen. */
    fun isGame(route: String?): Boolean = route != null && route.startsWith("game?")

    /** The game screen's route for a chosen configuration. Names are encoded so any text is safe. */
    fun game(config: GameConfig): String =
        "game?mode=${config.mode.name}" +
            "&difficulty=${config.difficulty.name}" +
            "&p1=${Uri.encode(config.player1Name)}&a1=${config.player1Avatar.name}" +
            "&p2=${Uri.encode(config.player2Name)}&a2=${config.player2Avatar.name}"

    /** Rebuilds the configuration from route arguments. Returns null when anything is missing or invalid. */
    fun configFrom(args: Bundle?): GameConfig? {
        if (args == null) return null
        val mode = GameMode.entries.firstOrNull { it.name == args.getString(ARG_MODE) } ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == args.getString(ARG_DIFFICULTY) } ?: return null
        val avatar1 = AvatarId.entries.firstOrNull { it.name == args.getString(ARG_A1) } ?: return null
        val avatar2 = AvatarId.entries.firstOrNull { it.name == args.getString(ARG_A2) } ?: return null
        val name1 = args.getString(ARG_P1) ?: return null
        val name2 = args.getString(ARG_P2) ?: return null
        return GameConfig(
            mode = mode,
            difficulty = difficulty,
            player1Name = name1,
            player1Avatar = avatar1,
            player2Name = name2,
            player2Avatar = avatar2,
        )
    }
}
