package com.westly.twelvepits.feature.game.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.westly.twelvepits.core.audio.Sfx
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.engine.GameStatus
import com.westly.twelvepits.feature.game.engine.IllegalMoveException
import com.westly.twelvepits.feature.game.engine.SowingEngine
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
    GAME_OVER,
}

/** The result of a finished game. A draw is impossible (RULES.md section 7). */
data class GameResult(
    val winner: Player,
    val winnerScore: Int,
    val loserScore: Int,
    val scoreOne: Int,
    val scoreTwo: Int,
)

/** Everything the screen needs to show that is not the board itself. */
data class GameUiState(
    val overlay: GameOverlay = GameOverlay.NONE,
    val controlsVisible: Boolean = false,
    val moveNumber: Int = 0,
    val bannerPlayer: Player? = null,
    val soundMuted: Boolean = false,
    val result: GameResult? = null,
)

/**
 * Holds the game: the engine state, the running animation and the open panels. It lives as long as
 * the game screen's navigation entry, so turning the phone never restarts the game or its animation.
 * Main thread only.
 */
class GameViewModel(
    val config: GameConfig,
    internal val settings: SettingsStore,
    internal val sound: SoundManager,
) : ViewModel() {
    private val engine = SowingEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Replaced in Phase 5: the starter alternates between games and is saved.
    private val startingPlayer: Player = Player.ONE
    private var engineState: GameState = GameState.initial(startingPlayer)

    /** What the board shows. The draw code reads it every frame. */
    val display = BoardDisplayState()

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

        override fun capture() = sound.playSfx(Sfx.CAPTURE)

        override fun turnSkipped() = sound.playSfx(Sfx.TURN_SKIPPED)
    }

    init {
        display.reset(engineState)
        showTurnGlow(engineState.currentPlayer)
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
    }

    // ---- Screen life ----

    /** The game screen opened: the game music replaces the menu music. */
    fun onScreenShown() {
        sound.startGameMusic()
    }

    /** The game screen was left for good (not a rotation): the menu music comes back. */
    fun onScreenClosed() {
        sound.startMenuMusic()
    }

    /** The app went to the background: finish the turn's movement at once, with no sound. */
    fun onAppBackground() {
        val running = animator ?: return
        running.finishInstantly()
        display.clearEffects()
        finishTurnAnimation()
        frameTick.longValue += 1
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
        if (animator != null) return
        if (state.status != GameStatus.IN_PROGRESS) return
        if (_uiState.value.overlay != GameOverlay.NONE) return
        if (pit !in 0 until GameState.PIT_COUNT) return
        if (GameState.ownerOfPit(pit) != state.currentPlayer || state.pits[pit] == 0) {
            invalidTap(pit)
            return
        }
        startTurn(state, pit)
    }

    private fun startTurn(state: GameState, pit: Int) {
        val result = try {
            engine.playTurn(state, pit)
        } catch (e: IllegalMoveException) {
            invalidTap(pit)
            return
        }
        engineState = result.newState
        display.pitGlowPlayer = null
        display.storeGlowPlayer = state.currentPlayer
        animator = SowAnimator(display, result.events, { currentTiming }, sounds)
        _uiState.update { it.copy(moveNumber = result.newState.moveNumber) }
        refreshNeedsFrames()
    }

    private fun invalidTap(pit: Int) {
        sound.playSfx(Sfx.INVALID_MOVE)
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
            val winner = state.winner ?: if (state.storeOne >= state.storeTwo) Player.ONE else Player.TWO
            val result = GameResult(
                winner = winner,
                winnerScore = state.storeOf(winner),
                loserScore = state.storeOf(winner.opponent),
                scoreOne = state.storeOne,
                scoreTwo = state.storeTwo,
            )
            _uiState.update { it.copy(result = result, moveNumber = state.moveNumber) }
            setOverlay(GameOverlay.GAME_OVER)
        } else {
            showTurnGlow(state.currentPlayer)
            _uiState.update { it.copy(moveNumber = state.moveNumber) }
        }
        refreshNeedsFrames()
    }

    private fun syncCountsToEngine() {
        val state = engineState
        for (pit in 0 until GameState.PIT_COUNT) {
            display.pits[pit] = state.pits[pit]
        }
        display.stores[Player.ONE.ordinal] = state.storeOne
        display.stores[Player.TWO.ordinal] = state.storeTwo
    }

    private fun showTurnGlow(player: Player) {
        display.pitGlowPlayer = player
        display.storeGlowPlayer = player
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

    /** Starts the game again with the same starting player and the same names. */
    fun confirmRestart() {
        animator = null
        engineState = GameState.initial(startingPlayer)
        display.reset(engineState)
        showTurnGlow(engineState.currentPlayer)
        _uiState.update { it.copy(bannerPlayer = null, moveNumber = 0, result = null) }
        frameTick.longValue += 1
        setOverlay(GameOverlay.NONE)
    }

    private fun setOverlay(overlay: GameOverlay) {
        _uiState.update { it.copy(overlay = overlay) }
        if (overlay != GameOverlay.NONE) hideControls()
        refreshNeedsFrames()
    }

    override fun onCleared() {
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

/** Builds the [GameViewModel] for one game. */
class GameViewModelFactory(
    private val config: GameConfig,
    private val settings: SettingsStore,
    private val sound: SoundManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameViewModel::class.java)) { "Unknown ViewModel class" }
        return GameViewModel(config, settings, sound) as T
    }
}
