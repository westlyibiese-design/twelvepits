package com.westly.twelvepits.feature.result

import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.data.TurnStats
import com.westly.twelvepits.feature.game.data.finishedWinner
import com.westly.twelvepits.feature.game.engine.GameState
import java.util.Locale

/** Which title the result screen shows. A draw is impossible (RULES.md section 7), so there is no draw title. */
enum class ResultHeadline {
    /** The human beat the computer, or the player of this phone won a two-phone game. */
    VICTORY,

    /** The computer won, or the other player won a two-phone game. */
    DEFEAT,

    /** Two players on one phone: "<name> wins". */
    WINNER_NAMED,
}

/** Everything the result screen shows about one finished game. */
data class GameResult(
    val config: GameConfig,
    val winner: Player,
    val scoreOne: Int,
    val scoreTwo: Int,
    val moves: Int,
    /** The winner's biggest number of seeds captured in one single turn. */
    val bestCapture: Int,
    val elapsedMs: Long,
    /** On two phones: which player this phone is. The title is then Victory or Defeat from this phone's side. */
    val localPlayer: Player? = null,
    /** On two phones: the number of the game in the match, so the result screen can tell a rematch from this game. */
    val matchGameNumber: Int = 0,
) {
    val winnerScore: Int
        get() = if (winner == Player.ONE) scoreOne else scoreTwo

    val loserScore: Int
        get() = if (winner == Player.ONE) scoreTwo else scoreOne

    val winnerName: String
        get() = if (winner == Player.ONE) config.player1Name else config.player2Name

    val headline: ResultHeadline
        get() = when {
            config.mode == GameMode.TWO_PHONES && localPlayer != null ->
                if (winner == localPlayer) ResultHeadline.VICTORY else ResultHeadline.DEFEAT
            config.mode != GameMode.VS_COMPUTER -> ResultHeadline.WINNER_NAMED
            winner == Player.ONE -> ResultHeadline.VICTORY
            else -> ResultHeadline.DEFEAT
        }

    companion object {
        /** The result of a finished game. [finalState] must be finished; seeds left on the board are not counted. */
        fun from(
            config: GameConfig,
            finalState: GameState,
            stats: TurnStats,
            elapsedMs: Long,
            localPlayer: Player? = null,
            matchGameNumber: Int = 0,
        ): GameResult {
            val winner = finishedWinner(finalState)
            return GameResult(
                config = config,
                winner = winner,
                scoreOne = finalState.storeOne,
                scoreTwo = finalState.storeTwo,
                moves = finalState.moveNumber,
                bestCapture = stats.bestCaptureOf(winner),
                elapsedMs = elapsedMs,
                localPlayer = localPlayer,
                matchGameNumber = matchGameNumber,
            )
        }
    }
}

/** Play time as mm:ss, for example 06:42. Digits are always 0 to 9, whatever the phone's language. */
fun formatPlayTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
