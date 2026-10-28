package com.westly.twelvepits.core.model

enum class Player {
    ONE,
    TWO;

    val opponent: Player
        get() = if (this == ONE) TWO else ONE
}
