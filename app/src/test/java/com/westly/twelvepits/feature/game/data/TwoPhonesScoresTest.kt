package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.multiplayer.MatchHarness
import com.westly.twelvepits.feature.result.GameResult
import com.westly.twelvepits.feature.result.ResultHeadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

class TwoPhonesScoresTest {

    private val phonesConfig = GameConfig(
        mode = GameMode.TWO_PHONES,
        difficulty = Difficulty.NORMAL,
        player1Name = "Ada",
        player1Avatar = AvatarId.KING,
        player2Name = "Bola",
        player2Avatar = AvatarId.QUEEN,
    )

    @Test
    fun aWinIsCountedForThisPhoneAsYou() {
        val data = GameData().afterTwoPhonesGame(localWon = true, starterOfFinishedGame = Player.ONE)
        assertEquals(Tally(playerOne = 1, playerTwo = 0), data.scores.twoPhones)
        val lost = GameData().afterTwoPhonesGame(localWon = false, starterOfFinishedGame = Player.ONE)
        assertEquals(Tally(playerOne = 0, playerTwo = 1), lost.scores.twoPhones)
    }

    @Test
    fun theHostMovesTheStarterRecordOnAndTheGuestDoesNot() {
        val host = GameData().afterTwoPhonesGame(true, Player.ONE)
        assertEquals(Player.TWO, host.starters.starterFor(GameMode.TWO_PHONES))
        val guest = GameData().afterTwoPhonesGame(false, null)
        assertEquals(Player.ONE, guest.starters.starterFor(GameMode.TWO_PHONES))
    }

    @Test
    fun nothingElseChanges() {
        val saved = SavedGame(
            config = phonesConfig.copy(mode = GameMode.TWO_PLAYERS_ONE_PHONE),
            state = GameState.initial(Player.ONE),
            elapsedMs = 5L,
            stats = TurnStats(),
        )
        val before = GameData(savedGame = saved)
        val after = before.afterTwoPhonesGame(true, Player.ONE)
        assertEquals(saved, after.savedGame)
        assertEquals(before.scores.twoPlayers, after.scores.twoPlayers)
        assertEquals(before.scores.vsComputer, after.scores.vsComputer)
        assertEquals(before.starters.starterFor(GameMode.VS_COMPUTER), after.starters.starterFor(GameMode.VS_COMPUTER))
        assertEquals(
            before.starters.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE),
            after.starters.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE),
        )
    }

    @Test
    fun theTwoPhonesScoreSurvivesBeingStored() {
        val data = GameData().afterTwoPhonesGame(true, Player.ONE).afterTwoPhonesGame(false, Player.TWO)
        val back = ScoresCodec.decode(ScoresCodec.encode(data.scores))
        assertNotNull(back)
        assertEquals(Tally(1, 1), back?.twoPhones)
    }

    @Test
    fun aResetClearsTheTwoPhonesScoreAndStarter() {
        val data = GameData().afterTwoPhonesGame(true, Player.ONE).afterScoresReset()
        assertEquals(Tally(), data.scores.twoPhones)
        assertEquals(Player.ONE, data.starters.starterFor(GameMode.TWO_PHONES))
    }

    @Test
    fun theTitleIsVictoryOrDefeatFromEachPhonesOwnSide() {
        val finished = finishedState(1)
        val winner = finishedWinner(finished)
        val hostResult = GameResult.from(phonesConfig, finished, TurnStats(), 1000L, localPlayer = Player.ONE, matchGameNumber = 1)
        val guestResult = GameResult.from(phonesConfig, finished, TurnStats(), 1000L, localPlayer = Player.TWO, matchGameNumber = 1)
        assertEquals(if (winner == Player.ONE) ResultHeadline.VICTORY else ResultHeadline.DEFEAT, hostResult.headline)
        assertEquals(if (winner == Player.TWO) ResultHeadline.VICTORY else ResultHeadline.DEFEAT, guestResult.headline)
        assertEquals(winner, hostResult.winner)
        assertEquals(hostResult.winnerName, guestResult.winnerName)
        assertEquals(1, hostResult.matchGameNumber)
    }

    @Test
    fun aTwoPhoneResultWithoutALocalPlayerStillNamesTheWinner() {
        val result = GameResult.from(phonesConfig, finishedState(2), TurnStats(), 0L)
        assertEquals(ResultHeadline.WINNER_NAMED, result.headline)
    }

    @Test
    fun theOtherModesKeepTheirTitles() {
        val finished = finishedState(3)
        val computer = phonesConfig.copy(mode = GameMode.VS_COMPUTER)
        val result = GameResult.from(computer, finished, TurnStats(), 0L)
        assertEquals(
            if (finishedWinner(finished) == Player.ONE) ResultHeadline.VICTORY else ResultHeadline.DEFEAT,
            result.headline,
        )
        val twoPlayers = phonesConfig.copy(mode = GameMode.TWO_PLAYERS_ONE_PHONE)
        assertEquals(ResultHeadline.WINNER_NAMED, GameResult.from(twoPlayers, finished, TurnStats(), 0L).headline)
    }

    @Test
    fun hundredsOfMatchesKeepBothPhonesScoresMirrored() {
        var hostData = GameData()
        var guestData = GameData()
        for (seed in 1..300) {
            val starter = hostData.starters.starterFor(GameMode.TWO_PHONES)
            val h = MatchHarness(starter)
            h.playToTheEnd(Random(seed))
            val state = h.host.state
            val winner = finishedWinner(state)
            assertEquals(winner, finishedWinner(h.guest.state))
            hostData = hostData.afterTwoPhonesGame(winner == Player.ONE, state.startingPlayer)
            guestData = guestData.afterTwoPhonesGame(winner == Player.TWO, null)
        }
        // What one phone counts as its own wins is exactly what the other counts as the opponent's.
        assertEquals(hostData.scores.twoPhones.playerOne, guestData.scores.twoPhones.playerTwo)
        assertEquals(hostData.scores.twoPhones.playerTwo, guestData.scores.twoPhones.playerOne)
        assertEquals(300, hostData.scores.twoPhones.playerOne + hostData.scores.twoPhones.playerTwo)
        // The host's record alternates after every game (300 games: back to the first starter).
        assertEquals(Player.ONE, hostData.starters.starterFor(GameMode.TWO_PHONES))
        assertEquals(Player.ONE, guestData.starters.starterFor(GameMode.TWO_PHONES))
    }

    private fun finishedState(seed: Int): GameState {
        val h = MatchHarness(Player.ONE)
        h.playToTheEnd(Random(seed))
        return h.host.state
    }
}
