package com.westly.twelvepits.feature.game.data

import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store with a fake file: saving, Continue, scores, the starter record and the atomic game end. */
class SavedGameStoreTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val storage = FakeRawGameStorage()
    private val errors = ArrayList<String>()
    private val store = SavedGameStore(storage, scope) { message, _ -> errors.add(message) }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---- Test 9: save and resume ----

    @Test
    fun test9_aSavedGameComesBackExactly() = runBlocking {
        val save = midGameSave(turns = 5)
        store.saveGame(save)
        assertTrue(store.hasSavedGame())
        assertEquals(save, store.loadSavedGame())
        assertEquals(save, store.savedGame.first())
    }

    @Test
    fun test9_noSavedGameMeansNoContinue() = runBlocking {
        assertFalse(store.hasSavedGame())
        assertNull(store.loadSavedGame())
        assertNull(store.savedGame.first())
    }

    @Test
    fun theNewestSaveReplacesTheOlderOne() = runBlocking {
        store.saveGame(midGameSave(turns = 3))
        val newer = midGameSave(turns = 6)
        store.saveGame(newer)
        assertEquals(newer, store.loadSavedGame())
    }

    @Test
    fun deletingTheSavedGameRemovesContinue() = runBlocking {
        store.saveGame(midGameSave())
        store.deleteSavedGame()
        assertFalse(store.hasSavedGame())
        assertNull(storage.value.savedGame)
    }

    @Test
    fun test9_whenTheGameEndsTheSaveIsGone() = runBlocking {
        val config = testConfig()
        store.saveGame(midGameSave(config = config))
        val (finished, _) = finishedGame(Player.ONE, 21)
        store.recordFinishedGame(config, finished)
        assertFalse(store.hasSavedGame())
    }

    // ---- Damaged saves ----

    @Test
    fun aDamagedSaveIsIgnoredAndDeletedWithoutACrash() = runBlocking {
        storage.overwriteSavedGame("this is not a game")
        assertNull(store.savedGame.first())
        assertNull(store.loadSavedGame())
        assertNull(storage.value.savedGame)
        assertFalse(store.hasSavedGame())
    }

    @Test
    fun aSaveThatDoesNotAddUpIsIgnoredAndDeleted() = runBlocking {
        val good = SavedGameCodec.encode(midGameSave())
        storage.overwriteSavedGame(good.replace("storeOne=", "storeOne=8"))
        assertNull(store.loadSavedGame())
        assertNull(storage.value.savedGame)
    }

    @Test
    fun aSaveFromANewerVersionIsIgnoredAndDeleted() = runBlocking {
        val good = SavedGameCodec.encode(midGameSave())
        storage.overwriteSavedGame(good.replace("schemaVersion=1", "schemaVersion=2"))
        assertNull(store.loadSavedGame())
        assertNull(storage.value.savedGame)
    }

    @Test
    fun discardingADamagedSaveAtStartLeavesAGoodSaveAlone() = runBlocking {
        val save = midGameSave()
        store.saveGame(save)
        store.discardDamagedSave()
        assertEquals(save, store.loadSavedGame())
        storage.overwriteSavedGame("broken")
        store.discardDamagedSave()
        assertNull(store.loadSavedGame())
        assertNull(storage.value.savedGame)
    }

    @Test
    fun aDamagedSaveDoesNotTouchTheScoresOrTheStarterRecord() = runBlocking {
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)
        val (finished, _) = finishedGame(Player.ONE, 8)
        store.recordFinishedGame(config, finished)
        storage.overwriteSavedGame("broken")
        assertNull(store.loadSavedGame())
        assertEquals(1, store.scores.first().twoPlayers.playerOne + store.scores.first().twoPlayers.playerTwo)
        assertEquals(Player.TWO, store.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE))
    }

    // ---- The game end is ONE atomic edit ----

    @Test
    fun theGameEndChangesScoresStarterAndSaveInOneSingleEdit() = runBlocking {
        val config = testConfig(GameMode.VS_COMPUTER, Difficulty.HARD)
        store.saveGame(midGameSave(config = config))
        val (finished, _) = finishedGame(Player.ONE, 33)
        val winner = finished.winner!!
        val editsBefore = storage.editCount
        store.recordFinishedGame(config, finished)
        store.hasSavedGame() // waits until the queued write is done
        assertEquals(1, storage.editCount - editsBefore)
        assertNull(storage.value.savedGame)
        val scores = store.scores.first()
        assertEquals(1, scores.computerTally(Difficulty.HARD).let { it.playerOne + it.playerTwo })
        assertEquals(1, if (winner == Player.ONE) scores.computerTally(Difficulty.HARD).playerOne else scores.computerTally(Difficulty.HARD).playerTwo)
        assertEquals(Player.TWO, store.starterFor(GameMode.VS_COMPUTER))
    }

    @Test
    fun ifTheAppClosesBeforeTheGameEndEditNothingIsCountedAndTheOldSaveStays() = runBlocking {
        val config = testConfig()
        val save = midGameSave(config = config)
        store.saveGame(save)
        // The final turn is played, but the app is closed before recordFinishedGame runs.
        assertEquals(save, store.loadSavedGame())
        assertEquals(Scores(), store.scores.first())
        assertEquals(Player.ONE, store.starterFor(GameMode.VS_COMPUTER))
    }

    @Test
    fun aGameEndIsCountedOnceAndOtherModesAreNotTouched() = runBlocking {
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)
        val (finished, _) = finishedGame(Player.ONE, 14)
        store.recordFinishedGame(config, finished)
        val scores = store.scores.first()
        assertEquals(1, scores.twoPlayers.playerOne + scores.twoPlayers.playerTwo)
        assertEquals(Tally(), scores.twoPhones)
        for (difficulty in Difficulty.entries) assertEquals(Tally(), scores.computerTally(difficulty))
        assertEquals(Player.ONE, store.starterFor(GameMode.VS_COMPUTER))
        assertEquals(Player.ONE, store.starterFor(GameMode.TWO_PHONES))
    }

    // ---- Test 8: alternating starter ----

    @Test
    fun test8_theStarterAlternatesEveryFinishedGameAndIsKeptAfterRestart() = runBlocking {
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)

        // Game 1: Player ONE starts.
        val start1 = store.prepareStart(config, StartMode.NEW)!!
        assertEquals(Player.ONE, start1.state.startingPlayer)
        assertEquals(Player.ONE, start1.state.currentPlayer)
        val (end1, _) = playTurns(start1.state, 100_000, 1)
        assertEquals(GameStatus.FINISHED, end1.status)
        store.recordFinishedGame(config, end1)

        // Game 2 (Play again): Player TWO starts.
        val start2 = store.prepareStart(config, StartMode.NEW)!!
        assertEquals(Player.TWO, start2.state.startingPlayer)
        assertEquals(Player.TWO, start2.state.currentPlayer)
        val (end2, _) = playTurns(start2.state, 100_000, 2)
        store.recordFinishedGame(config, end2)

        // The app is closed and opened again: a new store on the same file.
        val reopened = SavedGameStore(storage, scope)
        val start3 = reopened.prepareStart(config, StartMode.NEW)!!
        assertEquals(Player.ONE, start3.state.startingPlayer)
    }

    @Test
    fun test8_anUnfinishedGameNeverChangesTheStarter() = runBlocking {
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)
        val start = store.prepareStart(config, StartMode.NEW)!!
        val (middle, stats) = playTurns(start.state, 9, 4)
        store.saveGame(SavedGame(config, middle, 5000L, stats))
        // Leaving, replacing the saved game and starting over: the starter stays Player ONE.
        store.deleteSavedGame()
        assertEquals(Player.ONE, store.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE))
        assertEquals(Player.ONE, store.prepareStart(config, StartMode.NEW)!!.state.startingPlayer)
    }

    @Test
    fun test8_aResumedGameKeepsItsOwnStartingPlayer() = runBlocking {
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)
        // The record says Player ONE starts next, but the saved game was started by Player TWO.
        val save = midGameSave(config = config, starter = Player.TWO, turns = 4)
        store.saveGame(save)
        assertEquals(Player.ONE, store.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE))
        val start = store.prepareStart(config, StartMode.CONTINUE)!!
        assertEquals(Player.TWO, start.state.startingPlayer)
        assertEquals(save.state, start.state)
    }

    @Test
    fun test8_againstTheComputerTheComputerStartsGameTwo() = runBlocking {
        val config = testConfig(GameMode.VS_COMPUTER)
        val start1 = store.prepareStart(config, StartMode.NEW)!!
        assertEquals(Player.ONE, start1.state.startingPlayer)
        val (end1, _) = playTurns(start1.state, 100_000, 6)
        store.recordFinishedGame(config, end1)
        val start2 = store.prepareStart(config, StartMode.NEW)!!
        assertEquals(Player.TWO, start2.state.startingPlayer)
        assertEquals(Player.TWO, start2.state.currentPlayer)
    }

    @Test
    fun theStarterRecordIsKeptSeparatelyPerMode() = runBlocking {
        val (finished, _) = finishedGame(Player.ONE, 17)
        store.recordFinishedGame(testConfig(GameMode.VS_COMPUTER), finished)
        assertEquals(Player.TWO, store.starterFor(GameMode.VS_COMPUTER))
        assertEquals(Player.ONE, store.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE))
    }

    // ---- Reset scores ----

    @Test
    fun resetScoresClearsScoresAndStarterButKeepsTheSavedGame() = runBlocking {
        val config = testConfig(GameMode.TWO_PLAYERS_ONE_PHONE)
        val (finished, _) = finishedGame(Player.ONE, 12)
        store.recordFinishedGame(config, finished)
        val save = midGameSave(config = config)
        store.saveGame(save)
        assertEquals(Player.TWO, store.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE))

        store.resetScores()

        assertEquals(Scores(), store.scores.first())
        assertEquals(Player.ONE, store.starterFor(GameMode.TWO_PLAYERS_ONE_PHONE))
        assertEquals(save, store.loadSavedGame())
    }

    // ---- Starting the game screen ----

    @Test
    fun continueReturnsTheSavedPositionTheTimeAndTheBestCaptures() = runBlocking {
        val save = midGameSave(turns = 12, seed = 9, elapsedMs = 61_000L)
        store.saveGame(save)
        val start = store.prepareStart(save.config, StartMode.CONTINUE)!!
        assertEquals(save.state, start.state)
        assertEquals(save.stats, start.stats)
        assertEquals(61_000L, start.elapsedMs)
    }

    @Test
    fun continueWithoutAValidSaveGivesNull() = runBlocking {
        assertNull(store.prepareStart(testConfig(), StartMode.CONTINUE))
        storage.overwriteSavedGame("broken")
        assertNull(store.prepareStart(testConfig(), StartMode.CONTINUE))
    }

    @Test
    fun restoreUsesTheSaveIfThereIsOneAndOtherwiseStartsANewGame() = runBlocking {
        val config = testConfig()
        val fresh = store.prepareStart(config, StartMode.RESTORE)!!
        assertEquals(GameState.initial(Player.ONE), fresh.state)
        assertEquals(0L, fresh.elapsedMs)
        val save = midGameSave(config = config)
        store.saveGame(save)
        assertEquals(save.state, store.prepareStart(config, StartMode.RESTORE)!!.state)
    }

    @Test
    fun aNewGameStartsFromTheInitialPositionWithZeroTimeAndStats() = runBlocking {
        val start = store.prepareStart(testConfig(), StartMode.NEW)!!
        assertEquals(GameState.initial(Player.ONE), start.state)
        assertEquals(TurnStats(), start.stats)
        assertEquals(0L, start.elapsedMs)
    }

    @Test
    fun startModeReadsItsNameAndFallsBackToNew() {
        assertEquals(StartMode.CONTINUE, StartMode.fromArgument("CONTINUE"))
        assertEquals(StartMode.RESTORE, StartMode.fromArgument("RESTORE"))
        assertEquals(StartMode.NEW, StartMode.fromArgument("NEW"))
        assertEquals(StartMode.NEW, StartMode.fromArgument(""))
        assertEquals(StartMode.NEW, StartMode.fromArgument(null))
        assertEquals(StartMode.NEW, StartMode.fromArgument("garbage"))
    }

    // ---- Writes are done in order, and a failing write does not stop the next ones ----

    @Test
    fun writesAreDoneInTheOrderTheyWereAskedForEvenIfTheFirstIsSlow() = runBlocking {
        val slowStorage = object : RawGameStorage {
            private val inner = FakeRawGameStorage()
            private var calls = 0
            override val raw: Flow<RawGameData> = inner.raw
            override suspend fun edit(change: (RawGameData) -> RawGameData) {
                val number = ++calls
                if (number == 1) delay(60)
                inner.edit(change)
            }
        }
        val threaded = SavedGameStore(slowStorage, CoroutineScope(Job() + Dispatchers.Default))
        val first = midGameSave(turns = 2)
        val second = midGameSave(turns = 7)
        threaded.saveGame(first)
        threaded.saveGame(second)
        assertEquals(second, threaded.loadSavedGame())
    }

    @Test
    fun aFailedWriteIsReportedAndTheNextWriteStillWorks() = runBlocking {
        storage.failEdits = true
        store.saveGame(midGameSave(turns = 2))
        store.hasSavedGame()
        assertEquals(listOf("A game data write failed"), errors)
        storage.failEdits = false
        val good = midGameSave(turns = 4)
        store.saveGame(good)
        assertEquals(good, store.loadSavedGame())
        assertNotNull(storage.value.savedGame)
    }
}
