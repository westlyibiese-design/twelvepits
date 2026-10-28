package com.westly.twelvepits.feature.game.engine

/** Thrown for an empty pit, an opponent's pit, a pit that does not exist, or a finished game. */
class IllegalMoveException(message: String) : RuntimeException(message)
