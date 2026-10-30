package com.westly.twelvepits.feature.game.ui

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.westly.twelvepits.core.audio.HapticEffect
import com.westly.twelvepits.core.audio.Sfx
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.feature.game.ai.ComputerPlayer
import com.westly.twelvepits.feature.game.ai.ComputerPlayers
import com.westly.twelvepits.feature.game.ai.ComputerTurnScheduler
import com.westly.twelvepits.feature.game.data.GameStart
import com.westly.twelvepits.feature.game.data.PlayClock
import com.westly.twelvepits.feature.game.data.SavedGame
import com.westly.twelvepits.feature.game.data.SavedGameStore
import com.westly.twelvepits.feature.game.data.StartMode
import com.westly.twelvepits.feature.game.data.TurnStats
import com.westly.twelvepits.feature.game.engine.EngineLogger
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.IllegalMoveException
import com.westly.twelvepits.feature.game.engine.SowingEngine
import com.westly.twelvepits.feature.result.GameResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which panel is open on top of the board. */
enum class GameOverlay {
    NONE,
    LEAVE,
    PAUSE,
    SETTINGS,
    RESTART_CONFIRM,
    QUIT_CONFIRM,

    /** The game is over. The screen is about to show the result screen; nothing can be tapped. */
    GAME_OVER,
}

/** Everything the screen needs to show that is not the board itself. */
data class GameUiState(
    val overlay: GameOverlay = GameOverlay.NONE,
    val controlsVisible: Boolean = false,
    val moveNumber: Int = 0,
    val bannerPlayer: Player? = null,
    val soundMuted: Boolean = false,
    /** False until the starting position (new or saved) has been read. */
    val ready: Boolean = false,
    /** Continue was asked for but the saved game could not be loaded. The screen goes back to the menu. */
    val loadFailed: Boolean = false,
    /** Set once, when the game is over and its last animation has finished. */
    val result: GameResult? = null,
)

/**
 * Holds the game: the engine state, the running animation and the open panels. It lives as long as
 * the game screen's navigation entry, so turning the phone never restarts the game or its animation.
 * It also saves the game after every finished turn and plays the computer's turns. Main thread only.
 */
class GameViewModel(
    val config: GameConfig,
    private val startMode: StartMode,
    internal val settings: SettingsStore,
    internal val sound: SoundManager,
    private val savedGames: SavedGameStore,
    computer: ComputerPlayer?,
) : ViewModel() {
    private val engine = SowingEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The real position is read from the saved data right after creation (see loadStart).
    private var engineState: GameState = GameState.initial(Player.ONE)
    private var stats = TurnStats()
    private val clock = PlayClock(now = { SystemClock.elapsedRealtime() })
    private var inForeground = true
    private var finishRecorded = false

    private val scheduler: ComputerTurnScheduler? = computer?.let {
        ComputerTurnScheduler(
            player = it,
            scope = scope,
            onPitChosen = { state, pit -> onComputerChose(state, pit) },
            logger = object : EngineLogger {
                override fun error(message: String) {
                    Log.e(TAG, message)
                }
            },
        )
    }

    /** What the board shows. The draw code reads it every frame. */
    val display = BoardDisplayState()

    /** The saved data. The board reads the games each player has won from it. */
    val savedData: kotlinx.coroutines.flow.Flow<com.westly.twelvepits.feature.game.data.GameData>
        get() = savedGames.data

    /** Changes on every animation frame so the board is drawn again. */
    val frameTick = mutableLongStateOf(0L)

    /** True while something moves and the frame clock must run. */
    var needsFrames by mutableStateOf(false)
        private set

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var animator: SowAnimator? = null
    private var currentTiming = AnimationTiming.NORMAL
    private var lastFrameNanos = 0L
    private var hideControlsJob: Job? = null
    private var musicOn = SettingsStore.DEFAULT_MUSIC_ENABLED
    private var effectsOn = SettingsStore.DEFAULT_SOUND_EFFECTS_ENABLED

    private val sounds = object : AnimationSounds {
        override fun pickUp() = sound.playSfx(Sfx.SEEDS_PICKUP)

        override fun drop() = sound.playSeedDrop()

        override fun capture() {
            sound.playSfx(Sfx.CAPTURE)
            sound.vibrate(HapticEffect.CAPTURE)
        }

        override fun turnSkipped() {
            sound.playSfx(Sfx.TURN_SKIPPED)
            sound.vibrate(HapticEffect.TURN_SKIPPED)
        }
    }

    init {
        display.reset(engineState)
        scope.launch {
            settings.seedHopSeconds.collect { currentTiming = AnimationTiming(it) }
        }
        scope.launch {
            settings.musicEnabled.collect {
                musicOn = it
                publishSoundState()
            }
        }
        scope.launch {
            settings.soundEffectsEnabled.collect {
                effectsOn = it
                publishSoundState()
            }
        }
        loadStart()
    }

    /** Reads the starting position: a new game, or the saved game. */
    private fun loadStart() {
        scope.launch {
            val start = try {
                savedGames.prepareStart(config, startMode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "The starting position could not be read.", e)
                null
            }
            if (start == null) {
                _uiState.update { it.copy(loadFailed = true) }
            } else {
                applyStart(start)
            }
        }
    }

    private fun applyStart(start: GameStart) {
        engineState = start.state
        stats = start.stats
        clock.reset(start.elapsedMs)
        display.reset(engineState)
        updateTurnGlow()
        _uiState.update { it.copy(ready = true, moveNumber = engineState.moveNumber) }
        frameTick.longValue += 1
        syncClock()
        syncComputerTurn()
    }

    // ---- Screen life ----

    /** The game screen opened: the game music replaces the menu music. */
    fun onScreenShown() {
        sound.startGameMusic()
    }

    /**
     * The game screen was left for good (not a rotation). The menu music comes back, except after a
     * finished game: the result screen starts it again once its sound has played.
     */
    fun onScreenClosed() {
        if (_uiState.value.result == null) sound.startMenuMusic()
    }

    /** The screen is visible again: the clock and the computer's thinking continue. */
    fun onAppForeground() {
        inForeground = true
        syncClock()
        syncComputerTurn()
    }

    /**
     * The app went to the background or the screen is being left: finish the turn's movement at once
     * with no sound, stop the clock and the computer's thinking, and save the position.
     */
    fun onAppBackground() {
        inForeground = false
        val running = animator
        if (running != null) {
            running.finishInstantly()
            display.clearEffects()
            finishTurnAnimation()
            frameTick.longValue += 1
        }
        syncClock()
        syncComputerTurn()
        saveProgress()
    }

    /** The frame loop (re)started, so the time before it must not count. */
    fun onFrameLoopStarted() {
        lastFrameNanos = 0L
    }

    /** One animation frame. [frameTimeNanos] comes from the frame clock. */
    fun onFrame(frameTimeNanos: Long) {
        val last = lastFrameNanos
        lastFrameNanos = frameTimeNanos
        if (last == 0L) return
        var delta = (frameTimeNanos - last) / NANOS_PER_SECOND
        if (delta <= 0f) return
        if (delta > MAX_FRAME_SECONDS) delta = MAX_FRAME_SECONDS
        if (_uiState.value.overlay != GameOverlay.NONE) {
            refreshNeedsFrames()
            return
        }
        display.tickEffects(delta, currentTiming)
        val running = animator
        if (running != null) {
            running.advance(delta)
            mirrorBanner()
            if (running.isFinished) finishTurnAnimation()
        }
        frameTick.longValue += 1
        refreshNeedsFrames()
    }

    // ---- Playing ----

    /** The player tapped the pit with this engine number. */
    fun onPitTapped(pit: Int) {
        val state = engineState
        if (!_uiState.value.ready) return
        if (animator != null) return
        if (state.status != GameStatus.IN_PROGRESS) return
        if (_uiState.value.overlay != GameOverlay.NONE) return
        // Against the computer the human is Player ONE and cannot play during the computer's turn.
        if (isComputerTurn(state)) return
        if (pit !in 0 until GameState.PIT_COUNT) return
        if (GameState.ownerOfPit(pit) != state.currentPlayer || state.pits[pit] == 0) {
            invalidTap(pit)
            return
        }
        sound.vibrate(HapticEffect.PIT_TAP)
        startTurn(state, pit)
    }

    /** The computer's thinking time is over and it chose [pit] for [state]. */
    private fun onComputerChose(state: GameState, pit: Int) {
        if (engineState != state) return
        if (animator != null) return
        if (_uiState.value.overlay != GameOverlay.NONE) return
        if (!isComputerTurn(state)) return
        startTurn(state, pit)
    }

    private fun isComputerTurn(state: GameState): Boolean =
        scheduler != null && config.mode == GameMode.VS_COMPUTER && state.currentPlayer == Player.TWO

    private fun startTurn(state: GameState, pit: Int) {
        val result = try {
            engine.playTurn(state, pit)
        } catch (e: IllegalMoveException) {
            invalidTap(pit)
            return
        }
        engineState = result.newState
        stats = stats.afterTurn(state.currentPlayer, result.capturedForMover)
        display.pitGlowPlayer = null
        display.storeGlowPlayer = state.currentPlayer
        animator = SowAnimator(display, result.events, { currentTiming }, sounds)
        _uiState.update { it.copy(moveNumber = result.newState.moveNumber) }
        // The turn is computed: save its final result now, before the animation starts, so closing the
        // app during the animation leaves the finished turn saved. A finished game is recorded after its animation.
        saveProgress()
        syncComputerTurn()
        refreshNeedsFrames()
    }

    private fun invalidTap(pit: Int) {
        sound.playSfx(Sfx.INVALID_MOVE)
        sound.vibrate(HapticEffect.INVALID_MOVE)
        display.startShake(pit)
        frameTick.longValue += 1
        refreshNeedsFrames()
    }

    private fun finishTurnAnimation() {
        animator = null
        if (!display.countsMatch(engineState)) {
            Log.e(TAG, "The board and the engine disagree after a turn; showing the engine position.")
            syncCountsToEngine()
        }
        display.clearSequence()
        mirrorBanner()
        val state = engineState
        if (state.status == GameStatus.FINISHED) {
            display.pitGlowPlayer = null
            display.storeGlowPlayer = null
            completeGame(state)
        } else {
            updateTurnGlow()
            _uiState.update { it.copy(moveNumber = state.moveNumber) }
            syncComputerTurn()
        }
        refreshNeedsFrames()
    }

    /**
     * The game is over and its animation has finished. In ONE saved edit the win is counted, the
     * starter record moves on and the saved game is deleted. Then the result screen can be shown.
     */
    private fun completeGame(state: GameState) {
        clock.stop()
        if (!finishRecorded) {
            finishRecorded = true
            savedGames.recordFinishedGame(config, state)
            sound.stopMusic()
        }
        val result = GameResult.from(config, state, stats, clock.elapsedMs())
        _uiState.update { it.copy(result = result, moveNumber = state.moveNumber) }
        setOverlay(GameOverlay.GAME_OVER)
    }

    /** Saves the position of a game in progress with at least one turn played. */
    private fun saveProgress() {
        val state = engineState
        if (!_uiState.value.ready) return
        if (state.status != GameStatus.IN_PROGRESS || state.moveNumber <= 0) return
        savedGames.saveGame(SavedGame(config, state, clock.elapsedMs(), stats))
    }

    private fun syncCountsToEngine() {
        val state = engineState
        for (pit in 0 until GameState.PIT_COUNT) {
            display.pits[pit] = state.pits[pit]
        }
        display.stores[Player.ONE.ordinal] = state.storeOne
        display.stores[Player.TWO.ordinal] = state.storeTwo
    }

    /** The green ring is on the store of whoever plays; the pits glow only when a human can tap them. */
    private fun updateTurnGlow() {
        val state = engineState
        if (state.status != GameStatus.IN_PROGRESS) {
            display.pitGlowPlayer = null
            display.storeGlowPlayer = null
            return
        }
        display.pitGlowPlayer = if (isComputerTurn(state)) null else state.currentPlayer
        display.storeGlowPlayer = state.currentPlayer
    }

    private fun mirrorBanner() {
        val shown = display.bannerPlayer
        if (_uiState.value.bannerPlayer != shown) {
            _uiState.update { it.copy(bannerPlayer = shown) }
        }
    }

    private fun refreshNeedsFrames() {
        val blocked = _uiState.value.overlay != GameOverlay.NONE
        needsFrames = (animator != null || display.hasEffects) && !blocked
    }

    /** The play-time clock runs only while the board can be played: visible, no panel open, game in progress. */
    private fun syncClock() {
        val ui = _uiState.value
        val shouldRun = ui.ready && inForeground && ui.overlay == GameOverlay.NONE &&
            engineState.status == GameStatus.IN_PROGRESS
        if (shouldRun) clock.start() else clock.stop()
    }

    /** The computer thinks only on its own turn, while the board is visible, nothing moves and no panel is open. */
    private fun syncComputerTurn() {
        val computer = scheduler ?: return
        val state = engineState
        val ui = _uiState.value
        val wanted = ui.ready && inForeground && ui.overlay == GameOverlay.NONE && animator == null &&
            state.status == GameStatus.IN_PROGRESS && isComputerTurn(state)
        if (wanted) computer.start(state) else computer.cancel()
    }

    // ---- Controls ----

    fun toggleControls() {
        if (_uiState.value.controlsVisible) hideControls() else showControls()
    }

    fun showControls() {
        hideControlsJob?.cancel()
        _uiState.update { it.copy(controlsVisible = true) }
        hideControlsJob = scope.launch {
            delay(CONTROLS_VISIBLE_MS)
            _uiState.update { it.copy(controlsVisible = false) }
        }
    }

    fun hideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = null
        if (_uiState.value.controlsVisible) _uiState.update { it.copy(controlsVisible = false) }
    }

    /** One button for both music and sound effects: if either is on, both go off; if both are off, both go on. */
    fun toggleSound() {
        val turnOn = !(musicOn || effectsOn)
        scope.launch {
            settings.setMusicEnabled(turnOn)
            settings.setSoundEffectsEnabled(turnOn)
        }
    }

    private fun publishSoundState() {
        val muted = !musicOn && !effectsOn
        if (_uiState.value.soundMuted != muted) _uiState.update { it.copy(soundMuted = muted) }
    }

    // ---- Panels ----

    fun openPause() = setOverlay(GameOverlay.PAUSE)

    fun resume() = setOverlay(GameOverlay.NONE)

    fun openSettings() = setOverlay(GameOverlay.SETTINGS)

    fun backToPause() = setOverlay(GameOverlay.PAUSE)

    fun askRestart() = setOverlay(GameOverlay.RESTART_CONFIRM)

    fun askQuit() = setOverlay(GameOverlay.QUIT_CONFIRM)

    fun askLeave() = setOverlay(GameOverlay.LEAVE)

    fun stay() = setOverlay(GameOverlay.NONE)

    /** The system Back button or gesture. */
    fun onBackPressed() {
        when (_uiState.value.overlay) {
            GameOverlay.NONE -> askLeave()
            GameOverlay.LEAVE -> stay()
            GameOverlay.PAUSE -> resume()
            GameOverlay.SETTINGS, GameOverlay.RESTART_CONFIRM, GameOverlay.QUIT_CONFIRM -> backToPause()
            GameOverlay.GAME_OVER -> Unit
        }
    }

    /**
     * Starts the game again with the SAME starting player and the same players. The saved game is
     * deleted and the statistics and the time start again. A restart is not a finished game, so it is
     * not counted and does not change the starter record.
     */
    fun confirmRestart() {
        animator = null
        scheduler?.cancel()
        engineState = GameState.initial(engineState.startingPlayer)
        stats = TurnStats()
        clock.reset()
        scope.launch { savedGames.deleteSavedGame() }
        display.reset(engineState)
        updateTurnGlow()
        _uiState.update { it.copy(bannerPlayer = null, moveNumber = 0, result = null) }
        frameTick.longValue += 1
        setOverlay(GameOverlay.NONE)
    }

    private fun setOverlay(overlay: GameOverlay) {
        _uiState.update { it.copy(overlay = overlay) }
        if (overlay != GameOverlay.NONE) hideControls()
        syncClock()
        syncComputerTurn()
        refreshNeedsFrames()
    }

    override fun onCleared() {
        scheduler?.cancel()
        scope.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "TwelvePits"
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val MAX_FRAME_SECONDS = 0.1f
        const val CONTROLS_VISIBLE_MS = 3000L
    }
}

/** Builds the [GameViewModel] for one game. Against the computer it gets the computer player. */
class GameViewModelFactory(
    private val config: GameConfig,
    private val startMode: StartMode,
    private val settings: SettingsStore,
    private val sound: SoundManager,
    private val savedGames: SavedGameStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameViewModel::class.java)) { "Unknown ViewModel class" }
        // The difficulty comes from the config, so a game restored by Continue keeps its level.
        val computer: ComputerPlayer? =
            if (config.mode == GameMode.VS_COMPUTER) ComputerPlayers.forDifficulty(config.difficulty) else null
        return GameViewModel(config, startMode, settings, sound, savedGames, computer) as T
    }
}
