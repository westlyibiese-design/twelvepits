package com.westly.twelvepits.core.theme

import androidx.annotation.StringRes
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameMode

/** The name the player sees for a difficulty. */
@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.NORMAL -> R.string.difficulty_normal
        Difficulty.HARD -> R.string.difficulty_hard
    }

/** The name the player sees for a game mode. */
@get:StringRes
val GameMode.labelRes: Int
    get() = when (this) {
        GameMode.VS_COMPUTER -> R.string.mode_name_vs_computer
        GameMode.TWO_PLAYERS_ONE_PHONE -> R.string.mode_name_two_players
        GameMode.TWO_PHONES -> R.string.mode_name_two_phones
    }
