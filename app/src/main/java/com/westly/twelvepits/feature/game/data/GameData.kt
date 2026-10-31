package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.StarterPolicy

/**
 * The biggest number of seeds each player put into their own store in one single turn.
 * It comes from `capturedForMover` of every finished turn and is saved with the game.
 */
data class TurnStats(
    val bestCaptureOne: Int = 0,
    val bestCaptureTwo: Int = 0,
) {
    fun bestCaptureOf(player: Player): Int = if (player == Player.ONE) bestCaptureOne else bestCaptureTwo

    /** The stats after [mover] played a turn in which [capturedForMover] seeds went into their own store. */
    fun afterTurn(mover: Player, capturedForMover: Int): TurnStats = when (mover) {
        Player.ONE -> copy(bestCaptureOne = maxOf(bestCaptureOne, capturedForMover))
        Player.TWO -> copy(bestCaptureTwo = maxOf(bestCaptureTwo, capturedForMover))
    }
}

/** Everything that is saved about one unfinished game (RULES.md section 10). */
data class SavedGame(
    val config: GameConfig,
    val state: GameState,
    /** Time on the game screen in milliseconds, without pauses and without the time in the background. */
    val elapsedMs: Long,
    val stats: TurnStats,
)

/** Games won by each side. `playerOne` is Player ONE (the human against the computer, "You" on two phones). */
data class Tally(
    val playerOne: Int = 0,
    val playerTwo: Int = 0,
) {
    fun plusWin(winner: Player): Tally =
        if (winner == Player.ONE) copy(playerOne = playerOne + 1) else copy(playerTwo = playerTwo + 1)
}

/** Games won per mode. Against the computer every difficulty is counted separately (RULES.md section 10). */
data class Scores(
    val vsComputer: Map<Difficulty, Tally> = Difficulty.entries.associateWith { Tally() },
    val twoPlayers: Tally = Tally(),
    val twoPhones: Tally = Tally(),
) {
    fun computerTally(difficulty: Difficulty): Tally = vsComputer[difficulty] ?: Tally()

    fun tally(mode: GameMode, difficulty: Difficulty): Tally = when (mode) {
        GameMode.VS_COMPUTER -> computerTally(difficulty)
        GameMode.TWO_PLAYERS_ONE_PHONE -> twoPlayers
        GameMode.TWO_PHONES -> twoPhones
    }

    /** One more finished game won by [winner]. */
    fun withWin(mode: GameMode, difficulty: Difficulty, winner: Player): Scores = when (mode) {
        GameMode.VS_COMPUTER ->
            copy(vsComputer = vsComputer + (difficulty to computerTally(difficulty).plusWin(winner)))
        GameMode.TWO_PLAYERS_ONE_PHONE -> copy(twoPlayers = twoPlayers.plusWin(winner))
        GameMode.TWO_PHONES -> copy(twoPhones = twoPhones.plusWin(winner))
    }
}

/**
 * The saved game, the scores and the starter record. They are stored together in one file so that a
 * finished game changes all three in one single step.
 */
data class GameData(
    val savedGame: SavedGame? = null,
    val scores: Scores = Scores(),
    val starters: StarterPolicy = StarterPolicy(),
) {
    /**
     * A game has just finished: count the win, move the starter record on (the next game of this mode
     * starts with the other player) and delete the saved game. Nothing else changes.
     */
    fun afterGameFinished(config: GameConfig, finalState: GameState): GameData = copy(
        savedGame = null,
        scores = scores.withWin(config.mode, config.difficulty, finishedWinner(finalState)),
        starters = starters.afterGameFinished(config.mode, finalState.startingPlayer),
    )

    /**
     * A two-phone game has just finished. The win is counted from this phone's point of view (Player ONE
     * of the tally is "You"). The saved game of the other modes is not touched. Only the host passes
     * [starterOfFinishedGame]: it moves the starter record on. The guest passes null and its record stays.
     */
    fun afterTwoPhonesGame(localWon: Boolean, starterOfFinishedGame: Player?): GameData = copy(
        scores = scores.withWin(
            GameMode.TWO_PHONES,
            Difficulty.NORMAL,
            if (localWon) Player.ONE else Player.TWO,
        ),
        starters = if (starterOfFinishedGame == null) {
            starters
        } else {
            starters.afterGameFinished(GameMode.TWO_PHONES, starterOfFinishedGame)
        },
    )

    /** Scores and the starter record start again. The saved game, the settings, names and avatars stay. */
    fun afterScoresReset(): GameData = copy(scores = Scores(), starters = starters.reset())
}

/** The player with the larger store. A draw is impossible (RULES.md section 7); Player ONE only breaks a broken tie. */
fun finishedWinner(state: GameState): Player =
    state.winner ?: if (state.storeOne >= state.storeTwo) Player.ONE else Player.TWO

/** How the game screen starts. */
enum class StartMode {
    /** A new game. The starting player comes from the starter record. */
    NEW,

    /** The Continue button: the saved game, or nothing if it cannot be loaded. */
    CONTINUE,

    /** The game screen was rebuilt after the app was closed by the system: the saved game if there is one, else a new game. */
    RESTORE,
    ;

    companion object {
        fun fromArgument(raw: String?): StartMode = entries.firstOrNull { it.name == raw } ?: NEW
    }
}

/** The position a game screen starts from. */
data class GameStart(
    val state: GameState,
    val stats: TurnStats,
    val elapsedMs: Long,
)
