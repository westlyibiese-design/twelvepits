package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.StarterPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Games won per mode, and per difficulty against the computer. */
class ScoresTest {
    @Test
    fun everyoneStartsAtZero() {
        val scores = Scores()
        for (difficulty in Difficulty.entries) {
            assertEquals(Tally(0, 0), scores.computerTally(difficulty))
        }
        assertEquals(Tally(0, 0), scores.twoPlayers)
        assertEquals(Tally(0, 0), scores.twoPhones)
    }

    @Test
    fun aWinAgainstTheComputerCountsOnlyForThatDifficulty() {
        val scores = Scores()
            .withWin(GameMode.VS_COMPUTER, Difficulty.EASY, Player.ONE)
            .withWin(GameMode.VS_COMPUTER, Difficulty.EASY, Player.ONE)
            .withWin(GameMode.VS_COMPUTER, Difficulty.EASY, Player.TWO)
            .withWin(GameMode.VS_COMPUTER, Difficulty.HARD, Player.TWO)
        assertEquals(Tally(2, 1), scores.computerTally(Difficulty.EASY))
        assertEquals(Tally(0, 0), scores.computerTally(Difficulty.NORMAL))
        assertEquals(Tally(0, 1), scores.computerTally(Difficulty.HARD))
        assertEquals(Tally(0, 0), scores.twoPlayers)
        assertEquals(Tally(0, 0), scores.twoPhones)
    }

    @Test
    fun twoPlayersAndTwoPhonesAreCountedSeparatelyAndIgnoreTheDifficulty() {
        val scores = Scores()
            .withWin(GameMode.TWO_PLAYERS_ONE_PHONE, Difficulty.HARD, Player.TWO)
            .withWin(GameMode.TWO_PLAYERS_ONE_PHONE, Difficulty.EASY, Player.ONE)
            .withWin(GameMode.TWO_PHONES, Difficulty.NORMAL, Player.ONE)
        assertEquals(Tally(1, 1), scores.twoPlayers)
        assertEquals(Tally(1, 0), scores.twoPhones)
        for (difficulty in Difficulty.entries) {
            assertEquals(Tally(0, 0), scores.computerTally(difficulty))
        }
        assertEquals(Tally(1, 1), scores.tally(GameMode.TWO_PLAYERS_ONE_PHONE, Difficulty.EASY))
    }

    @Test
    fun scoresSurviveBeingWrittenAndReadAgain() {
        val scores = Scores()
            .withWin(GameMode.VS_COMPUTER, Difficulty.NORMAL, Player.ONE)
            .withWin(GameMode.VS_COMPUTER, Difficulty.HARD, Player.TWO)
            .withWin(GameMode.TWO_PLAYERS_ONE_PHONE, Difficulty.NORMAL, Player.TWO)
            .withWin(GameMode.TWO_PHONES, Difficulty.NORMAL, Player.ONE)
        assertEquals(scores, ScoresCodec.decode(ScoresCodec.encode(scores)))
    }

    @Test
    fun missingRowsCountAsZeroAndBadNumbersAreRefused() {
        assertEquals(Scores(), ScoresCodec.decode(""))
        assertEquals(Tally(3, 4), ScoresCodec.decode("twoPlayers=3,4")?.twoPlayers)
        assertNull(ScoresCodec.decode("twoPlayers=3"))
        assertNull(ScoresCodec.decode("twoPlayers=3,x"))
        assertNull(ScoresCodec.decode("twoPlayers=-1,2"))
        assertNull(ScoresCodec.decode("computer.EASY=1,2,3"))
        assertNull(ScoresCodec.decode("garbage"))
    }

    @Test
    fun theStarterRecordSurvivesBeingWrittenAndReadAgain() {
        val policy = StarterPolicy()
            .afterGameFinished(GameMode.VS_COMPUTER, Player.ONE)
            .afterGameFinished(GameMode.TWO_PHONES, Player.TWO)
        assertEquals(policy, StarterCodec.decode(StarterCodec.encode(policy)))
        assertEquals(Player.TWO, StarterCodec.decode(StarterCodec.encode(policy)).starterFor(GameMode.VS_COMPUTER))
        assertEquals(Player.ONE, StarterCodec.decode(StarterCodec.encode(policy)).starterFor(GameMode.TWO_PHONES))
    }

    @Test
    fun anUnreadableStarterRecordStartsWithPlayerOne() {
        assertEquals(StarterPolicy(), StarterCodec.decode("garbage"))
        assertEquals(StarterPolicy(), StarterCodec.decode(""))
        assertEquals(Player.ONE, StarterCodec.decode("VS_COMPUTER=THREE").starterFor(GameMode.VS_COMPUTER))
        assertEquals(Player.TWO, StarterCodec.decode("VS_COMPUTER=TWO\nTWO_PHONES=zzz").starterFor(GameMode.VS_COMPUTER))
    }
}
