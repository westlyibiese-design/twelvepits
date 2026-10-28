package com.westly.twelvepits.feature.game.engine

/** The engine has no Android imports, so errors go out through this interface. */
interface EngineLogger {
    fun error(message: String)
}

object NoOpEngineLogger : EngineLogger {
    override fun error(message: String) {
    }
}
