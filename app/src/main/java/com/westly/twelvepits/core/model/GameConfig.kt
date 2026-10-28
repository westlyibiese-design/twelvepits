package com.westly.twelvepits.core.model

data class GameConfig(
    val mode: GameMode,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val player1Name: String,
    val player1Avatar: AvatarId,
    val player2Name: String,
    val player2Avatar: AvatarId,
)
