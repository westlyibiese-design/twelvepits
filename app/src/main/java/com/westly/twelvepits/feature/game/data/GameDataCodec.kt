package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.StarterPolicy
import java.net.URLDecoder
import java.net.URLEncoder

/*
 * The three saved values are plain text made of `key=value` lines. Plain text needs no library, can be
 * read in a unit test and can never be half understood: if anything is wrong, decode returns null.
 */

private const val LINE_BREAK = "\n"
private const val CHARSET = "UTF-8"
private const val MAX_TEXT_LENGTH = 4096

/** Splits `key=value` lines. Returns null when a line has no key or the text is too long. */
private fun parseLines(text: String): Map<String, String>? {
    if (text.length > MAX_TEXT_LENGTH) return null
    val fields = HashMap<String, String>()
    for (line in text.split(LINE_BREAK)) {
        if (line.isEmpty()) continue
        val separator = line.indexOf('=')
        if (separator < 1) return null
        fields[line.substring(0, separator)] = line.substring(separator + 1)
    }
    return fields
}

/** The mid-game save. RULES.md section 10: anything that does not follow the rules is rejected. */
internal object SavedGameCodec {
    private const val KEY_MODE = "mode"
    private const val KEY_DIFFICULTY = "difficulty"
    private const val KEY_P1_NAME = "player1Name"
    private const val KEY_P1_AVATAR = "player1Avatar"
    private const val KEY_P2_NAME = "player2Name"
    private const val KEY_P2_AVATAR = "player2Avatar"
    private const val KEY_ELAPSED = "elapsedMs"
    private const val KEY_BEST_ONE = "bestCaptureOne"
    private const val KEY_BEST_TWO = "bestCaptureTwo"

    /** A name is at most 12 characters in the game; this wider limit only rejects absurd data. */
    private const val MAX_STORED_NAME_LENGTH = 40

    fun encode(save: SavedGame): String {
        val lines = ArrayList<String>()
        for ((key, value) in save.state.toSaveMap()) {
            val text = if (value is List<*>) value.joinToString(",") else value.toString()
            lines.add("$key=$text")
        }
        lines.add("$KEY_MODE=${save.config.mode.name}")
        lines.add("$KEY_DIFFICULTY=${save.config.difficulty.name}")
        lines.add("$KEY_P1_NAME=${URLEncoder.encode(save.config.player1Name, CHARSET)}")
        lines.add("$KEY_P1_AVATAR=${save.config.player1Avatar.name}")
        lines.add("$KEY_P2_NAME=${URLEncoder.encode(save.config.player2Name, CHARSET)}")
        lines.add("$KEY_P2_AVATAR=${save.config.player2Avatar.name}")
        lines.add("$KEY_ELAPSED=${save.elapsedMs}")
        lines.add("$KEY_BEST_ONE=${save.stats.bestCaptureOne}")
        lines.add("$KEY_BEST_TWO=${save.stats.bestCaptureTwo}")
        return lines.joinToString(LINE_BREAK)
    }

    /** Returns null for anything missing, unreadable, from a newer schema or not following the rules. */
    fun decode(text: String): SavedGame? {
        val fields = parseLines(text) ?: return null

        // Every value that cannot be read becomes null (or a null item), and GameState.fromSaveMap rejects that.
        val stateMap = HashMap<String, Any?>()
        stateMap[GameState.KEY_SCHEMA_VERSION] = fields[GameState.KEY_SCHEMA_VERSION]?.toIntOrNull()
        stateMap[GameState.KEY_PITS] = fields[GameState.KEY_PITS]?.split(",")?.map { it.toIntOrNull() }
        stateMap[GameState.KEY_STORE_ONE] = fields[GameState.KEY_STORE_ONE]?.toIntOrNull()
        stateMap[GameState.KEY_STORE_TWO] = fields[GameState.KEY_STORE_TWO]?.toIntOrNull()
        stateMap[GameState.KEY_CURRENT_PLAYER] = fields[GameState.KEY_CURRENT_PLAYER]
        stateMap[GameState.KEY_STARTING_PLAYER] = fields[GameState.KEY_STARTING_PLAYER]
        stateMap[GameState.KEY_CAPTURED_TOTAL] = fields[GameState.KEY_CAPTURED_TOTAL]?.toIntOrNull()
        stateMap[GameState.KEY_MOVE_NUMBER] = fields[GameState.KEY_MOVE_NUMBER]?.toIntOrNull()
        stateMap[GameState.KEY_STATUS] = fields[GameState.KEY_STATUS]
        stateMap[GameState.KEY_LEFTOVER_IN_HAND] = fields[GameState.KEY_LEFTOVER_IN_HAND]?.toIntOrNull()
        val state = GameState.fromSaveMap(stateMap) ?: return null
        // Only a game in progress is ever saved.
        if (state.status != GameStatus.IN_PROGRESS) return null

        val mode = GameMode.entries.firstOrNull { it.name == fields[KEY_MODE] } ?: return null
        val difficulty = Difficulty.entries.firstOrNull { it.name == fields[KEY_DIFFICULTY] } ?: return null
        val avatarOne = AvatarId.entries.firstOrNull { it.name == fields[KEY_P1_AVATAR] } ?: return null
        val avatarTwo = AvatarId.entries.firstOrNull { it.name == fields[KEY_P2_AVATAR] } ?: return null
        val nameOne = decodeName(fields[KEY_P1_NAME]) ?: return null
        val nameTwo = decodeName(fields[KEY_P2_NAME]) ?: return null

        val elapsed = fields[KEY_ELAPSED]?.toLongOrNull() ?: return null
        if (elapsed < 0L) return null
        val bestOne = fields[KEY_BEST_ONE]?.toIntOrNull() ?: return null
        val bestTwo = fields[KEY_BEST_TWO]?.toIntOrNull() ?: return null
        // A single turn captures whole groups of 4 seeds, and never more than the player's whole store.
        if (!isPossibleBest(bestOne, state.storeOne)) return null
        if (!isPossibleBest(bestTwo, state.storeTwo)) return null

        return SavedGame(
            config = GameConfig(
                mode = mode,
                difficulty = difficulty,
                player1Name = nameOne,
                player1Avatar = avatarOne,
                player2Name = nameTwo,
                player2Avatar = avatarTwo,
            ),
            state = state,
            elapsedMs = elapsed,
            stats = TurnStats(bestCaptureOne = bestOne, bestCaptureTwo = bestTwo),
        )
    }

    private fun isPossibleBest(best: Int, store: Int): Boolean =
        best >= 0 && best % GameState.CAPTURE_SIZE == 0 && best <= store

    private fun decodeName(raw: String?): String? {
        if (raw == null) return null
        val name = try {
            URLDecoder.decode(raw, CHARSET)
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (name.isBlank() || name.length > MAX_STORED_NAME_LENGTH) return null
        return name
    }
}

/** Games won per mode: `computer.EASY=3,1` means Player ONE won 3 and Player TWO won 1. */
internal object ScoresCodec {
    private const val COMPUTER_PREFIX = "computer."
    private const val KEY_TWO_PLAYERS = "twoPlayers"
    private const val KEY_TWO_PHONES = "twoPhones"

    fun encode(scores: Scores): String {
        val lines = ArrayList<String>()
        for (difficulty in Difficulty.entries) {
            lines.add("$COMPUTER_PREFIX${difficulty.name}=${encodeTally(scores.computerTally(difficulty))}")
        }
        lines.add("$KEY_TWO_PLAYERS=${encodeTally(scores.twoPlayers)}")
        lines.add("$KEY_TWO_PHONES=${encodeTally(scores.twoPhones)}")
        return lines.joinToString(LINE_BREAK)
    }

    /** Returns null when a number cannot be read. A missing row counts as 0 to 0. */
    fun decode(text: String): Scores? {
        val fields = parseLines(text) ?: return null
        val computer = HashMap<Difficulty, Tally>()
        for (difficulty in Difficulty.entries) {
            val raw = fields["$COMPUTER_PREFIX${difficulty.name}"]
            computer[difficulty] = if (raw == null) Tally() else decodeTally(raw) ?: return null
        }
        val twoPlayers = fields[KEY_TWO_PLAYERS]?.let { decodeTally(it) ?: return null } ?: Tally()
        val twoPhones = fields[KEY_TWO_PHONES]?.let { decodeTally(it) ?: return null } ?: Tally()
        return Scores(vsComputer = computer, twoPlayers = twoPlayers, twoPhones = twoPhones)
    }

    private fun encodeTally(tally: Tally): String = "${tally.playerOne},${tally.playerTwo}"

    private fun decodeTally(raw: String): Tally? {
        val parts = raw.split(",")
        if (parts.size != 2) return null
        val one = parts[0].toIntOrNull() ?: return null
        val two = parts[1].toIntOrNull() ?: return null
        if (one < 0 || two < 0) return null
        return Tally(playerOne = one, playerTwo = two)
    }
}

/** Who starts the next new game of each mode: `VS_COMPUTER=TWO`. Unknown or missing modes start with Player ONE. */
internal object StarterCodec {
    fun encode(policy: StarterPolicy): String =
        GameMode.entries.joinToString(LINE_BREAK) { "${it.name}=${policy.starterFor(it).name}" }

    fun decode(text: String): StarterPolicy {
        val fields = parseLines(text) ?: return StarterPolicy()
        val starters = HashMap<GameMode, Player>()
        for (mode in GameMode.entries) {
            val player = Player.entries.firstOrNull { it.name == fields[mode.name] }
            if (player != null) starters[mode] = player
        }
        return StarterPolicy.fromMap(starters)
    }
}
