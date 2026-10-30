package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Test 9 at logic level: what is saved is exactly what comes back, and anything wrong is refused. */
class SavedGameCodecTest {
    private val goodText: String = SavedGameCodec.encode(midGameSave())

    /** The saved text with one line changed ([value] null removes the line). */
    private fun changed(key: String, value: String?): String =
        goodText.split("\n").mapNotNull { line ->
            if (line.substringBefore('=') == key) {
                if (value == null) null else "$key=$value"
            } else {
                line
            }
        }.joinToString("\n")

    private fun assertRefused(text: String) = assertNull(SavedGameCodec.decode(text))

    @Test
    fun saveAndRestoreGivesExactlyTheSameGame() {
        val save = midGameSave(turns = 5)
        assertEquals(5, save.state.moveNumber)
        assertEquals(save, SavedGameCodec.decode(SavedGameCodec.encode(save)))
    }

    @Test
    fun everythingTheSpecNamesIsRestored() {
        val config = GameConfig(
            mode = GameMode.TWO_PLAYERS_ONE_PHONE,
            difficulty = Difficulty.HARD,
            player1Name = "Åbé & Co = 100%",
            player1Avatar = AvatarId.WARRIOR,
            player2Name = "Line\nBreak",
            player2Avatar = AvatarId.QUEEN,
        )
        val save = midGameSave(config = config, turns = 14, seed = 11, starter = Player.TWO, elapsedMs = 987_654L)
        val back = checkNotNull(SavedGameCodec.decode(SavedGameCodec.encode(save)))
        assertEquals(save.state.pits, back.state.pits)
        assertEquals(save.state.storeOne, back.state.storeOne)
        assertEquals(save.state.storeTwo, back.state.storeTwo)
        assertEquals(save.state.currentPlayer, back.state.currentPlayer)
        assertEquals(Player.TWO, back.state.startingPlayer)
        assertEquals(save.state.moveNumber, back.state.moveNumber)
        assertEquals(GameMode.TWO_PLAYERS_ONE_PHONE, back.config.mode)
        assertEquals(Difficulty.HARD, back.config.difficulty)
        assertEquals("Åbé & Co = 100%", back.config.player1Name)
        assertEquals("Line\nBreak", back.config.player2Name)
        assertEquals(AvatarId.WARRIOR, back.config.player1Avatar)
        assertEquals(AvatarId.QUEEN, back.config.player2Avatar)
        assertEquals(987_654L, back.elapsedMs)
        assertEquals(save.stats, back.stats)
    }

    @Test
    fun manyDifferentGamesRoundTrip() {
        for (seed in 1..40) {
            val save = midGameSave(turns = 1 + seed, seed = seed, starter = if (seed % 2 == 0) Player.ONE else Player.TWO)
            assertEquals("seed $seed", save, SavedGameCodec.decode(SavedGameCodec.encode(save)))
        }
    }

    @Test
    fun theGoodTextIsAccepted() {
        assertNotNull(SavedGameCodec.decode(goodText))
    }

    @Test
    fun missingAnyFieldIsRefused() {
        for (line in goodText.split("\n")) {
            assertRefused(changed(line.substringBefore('='), null))
        }
    }

    @Test
    fun unreadableNumbersAreRefused() {
        for (key in listOf("schemaVersion", "storeOne", "storeTwo", "capturedTotal", "moveNumber", "leftoverInHand", "elapsedMs", "bestCaptureOne", "bestCaptureTwo")) {
            assertRefused(changed(key, "abc"))
            assertRefused(changed(key, ""))
        }
        assertRefused(changed("pits", "4,4,x,4,4,4,4,4,4,4,4,4"))
        assertRefused(changed("pits", ""))
    }

    @Test
    fun aSaveFromANewerSchemaIsRefused() {
        assertRefused(changed("schemaVersion", "2"))
        assertRefused(changed("schemaVersion", "0"))
    }

    @Test
    fun aBoardThatBreaksTheRulesIsRefused() {
        val pits = goodText.split("\n").first { it.startsWith("pits=") }.substringAfter('=').split(",").map { it.toInt() }
        assertRefused(changed("pits", pits.take(11).joinToString(",")))
        assertRefused(changed("pits", (pits + 1).joinToString(",")))
        assertRefused(changed("pits", pits.mapIndexed { i, p -> if (i == 0) p + 1 else p }.joinToString(",")))
        assertRefused(changed("pits", pits.mapIndexed { i, p -> if (i == 0) -1 else if (i == 1) p + p + 1 else p }.joinToString(",")))
    }

    @Test
    fun storesThatDoNotAddUpAreRefused() {
        val state = SavedGameCodec.decode(goodText)!!.state
        assertRefused(changed("storeOne", (state.storeOne + 4).toString()))
        assertRefused(changed("storeOne", (state.storeOne + 1).toString()))
        assertRefused(changed("storeTwo", "-4"))
        assertRefused(changed("capturedTotal", (state.capturedTotal + 4).toString()))
    }

    @Test
    fun aFinishedGameIsNeverAValidSave() {
        assertRefused(changed("status", "FINISHED"))
        assertRefused(changed("status", "PAUSED"))
        val (finished, stats) = finishedGame(Player.ONE, 5)
        val text = SavedGameCodec.encode(SavedGame(testConfig(), finished, 1000L, stats))
        assertRefused(text)
    }

    @Test
    fun aPlayerToMoveWithNoSeedsIsRefused() {
        val state = SavedGameCodec.decode(goodText)!!.state
        val empty = GameState.pitsOf(state.currentPlayer)
        val moved = state.pits.mapIndexed { i, p -> if (i in empty) 0 else p }
        val lost = state.pits.sum() - moved.sum()
        val other = GameState.pitsOf(state.currentPlayer.opponent).first()
        val fixed = moved.mapIndexed { i, p -> if (i == other) p + lost else p }
        assertRefused(changed("pits", fixed.joinToString(",")))
    }

    @Test
    fun seedsInHandInAGameInProgressAreRefused() {
        assertRefused(changed("leftoverInHand", "1"))
    }

    @Test
    fun unknownModeDifficultyPlayerOrAvatarIsRefused() {
        assertRefused(changed("mode", "THREE_PLAYERS"))
        assertRefused(changed("difficulty", "IMPOSSIBLE"))
        assertRefused(changed("currentPlayer", "THREE"))
        assertRefused(changed("startingPlayer", "nobody"))
        assertRefused(changed("player1Avatar", "DRAGON"))
        assertRefused(changed("player2Avatar", ""))
    }

    @Test
    fun badNamesAreRefused() {
        assertRefused(changed("player1Name", ""))
        assertRefused(changed("player1Name", "+++"))
        assertRefused(changed("player2Name", "%zz"))
        assertRefused(changed("player2Name", "x".repeat(41)))
    }

    @Test
    fun impossibleTimeAndBestCaptureAreRefused() {
        assertRefused(changed("elapsedMs", "-1"))
        assertRefused(changed("bestCaptureOne", "-4"))
        assertRefused(changed("bestCaptureTwo", "5"))
        assertRefused(changed("bestCaptureOne", "48"))
    }

    @Test
    fun garbageIsRefusedWithoutCrashing() {
        assertRefused("")
        assertRefused("hello")
        assertRefused("=")
        assertRefused("a\n=b")
        assertRefused("\u0000\u0001\u0002")
        assertRefused("x".repeat(10_000))
        assertRefused(goodText + "\n" + "y".repeat(5000))
    }
}
