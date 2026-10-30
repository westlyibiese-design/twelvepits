package com.westly.twelvepits.feature.game.ui

import androidx.compose.runtime.collectAsState
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.core.theme.GameDimens
import com.westly.twelvepits.core.theme.Ink
import com.westly.twelvepits.core.theme.OutlinedGoldButton
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.labelRes
import com.westly.twelvepits.feature.result.GameResult
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

private const val PULSE_MS = 1400
private const val PULSE_LOW = 0.7f

/**
 * The game screen. Landscape, full screen, nothing on it except the table and the board: the
 * controls appear after a tap on the table. The game itself lives in [viewModel].
 *
 * [onGameFinished] is called once, when the game is over and its last animation has finished.
 * [onExit] is called when the player leaves or quits to the menu.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onGameFinished: (GameResult) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    perspective: Player = Player.ONE,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    GameWindowEffect(activity, viewModel)
    GameFrameLoop(viewModel)
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onAppForeground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAppBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (activity != null) hideSystemBars(activity, view)
    }
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }

    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val assetsState by produceState<AssetsState>(AssetsState.Loading, context) {
        value = loadGameAssets(context)
    }
    val mapper = remember(perspective) { BoardMapper(perspective) }

    // The game is over: go to the result screen. (Back does the same while the screen is still closing.)
    LaunchedEffect(ui.result) {
        val finished = ui.result
        if (finished != null) onGameFinished(finished)
    }
    // Continue was asked for but the saved game could not be read.
    LaunchedEffect(ui.loadFailed) {
        if (ui.loadFailed) onExit()
    }

    BackHandler {
        val result = ui.result
        if (ui.overlay == GameOverlay.GAME_OVER) {
            if (result != null) onGameFinished(result)
        } else {
            viewModel.onBackPressed()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        when (val state = assetsState) {
            AssetsState.Loading -> Unit
            is AssetsState.Failed -> LoadErrorPanel(state, onExit)
            is AssetsState.Ready -> if (ui.ready) {
                GameBoardLayer(
                    viewModel = viewModel,
                    assets = state.assets,
                    mapper = mapper,
                    ui = ui,
                    onExit = onExit,
                )
            }
        }
    }
}

@Composable
private fun GameBoardLayer(
    viewModel: GameViewModel,
    assets: GameAssets,
    mapper: BoardMapper,
    ui: GameUiState,
    onExit: () -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val geometry = assets.geometry
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val insets = rememberSafeInsets(widthPx, heightPx)
        val board = remember(widthPx, heightPx, insets, geometry) {
            BoardLayoutMath.boardRect(
                widthPx, heightPx, insets.left, insets.top, insets.right, insets.bottom, geometry.aspectRatio,
            )
        }
        val seedLengthPx = geometry.seedLengthW * board.width
        val seedImage = remember(assets, seedLengthPx) {
            scaleSeed(
                assets.seedSource,
                (seedLengthPx * geometry.seedAspectWOverH).roundToInt(),
                seedLengthPx.roundToInt(),
            )
        }
        val playerNames = listOf(viewModel.config.player1Name, viewModel.config.player2Name)
        val savedData by viewModel.savedData.collectAsState(initial = null)
        val tally = savedData?.scores?.tally(viewModel.config.mode, viewModel.config.difficulty)
        val playerWins = listOf(tally?.playerOne ?: 0, tally?.playerTwo ?: 0)
        val winsLabel = stringResource(R.string.board_wins_label)
        val renderer = remember(assets, board, seedImage, mapper, widthPx, heightPx, density, measurer, playerNames, playerWins, winsLabel) {
            BoardRenderer(
                geometry = geometry,
                mapper = mapper,
                board = board,
                boardImage = assets.board,
                backgroundImage = assets.background,
                seedImage = seedImage,
                screenWidth = widthPx,
                screenHeight = heightPx,
                density = density,
                measurer = measurer,
                playerNames = playerNames,
                playerWins = playerWins,
                winsLabel = winsLabel,
            )
        }
        val minTapRadius = with(density) { GameDimens.MinPitTapRadius.toPx() }
        val pulseTransition = rememberInfiniteTransition(label = "turnPulse")
        val pulse by pulseTransition.animateFloat(
            initialValue = PULSE_LOW,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_MS, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "turnPulseValue",
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(board, geometry, mapper, viewModel) {
                    detectTapGestures { offset ->
                        val slot = PitHitTester.hitSlot(offset.x, offset.y, board, geometry, minTapRadius)
                        if (slot >= 0) {
                            viewModel.onPitTapped(mapper.pitOfSlot(slot))
                        } else {
                            viewModel.toggleControls()
                        }
                    }
                },
        ) {
            // Reading the tick makes the board draw again on every animation frame.
            val tick = viewModel.frameTick.longValue
            if (tick >= 0L) {
                with(renderer) { drawScene(viewModel.display, pulse) }
            }
        }

        val skippedName = ui.bannerPlayer?.let { playerName(viewModel, it) }
        TurnSkippedBanner(playerName = skippedName, insets = insets)
        GameControlsLayer(
            visible = ui.controlsVisible && ui.overlay == GameOverlay.NONE,
            soundMuted = ui.soundMuted,
            insets = insets,
            onShow = viewModel::showControls,
            onBack = viewModel::askLeave,
            onPause = viewModel::openPause,
            onSound = viewModel::toggleSound,
        )
        GameOverlayHost(viewModel = viewModel, ui = ui, onExit = onExit)
    }
}

/** Shows whichever panel is open: the pause menu, a question or the settings. */
@Composable
private fun GameOverlayHost(
    viewModel: GameViewModel,
    ui: GameUiState,
    onExit: () -> Unit,
) {
    // Nothing is saved before the first turn is played.
    val savedMessage = stringResource(
        if (ui.moveNumber >= 1) R.string.leave_message else R.string.leave_message_unsaved,
    )
    when (ui.overlay) {
        GameOverlay.NONE -> Unit
        GameOverlay.LEAVE -> ConfirmOverlay(
            title = stringResource(R.string.leave_title),
            message = savedMessage,
            dismissText = stringResource(R.string.leave_stay),
            confirmText = stringResource(R.string.leave_leave),
            onDismiss = viewModel::stay,
            onConfirm = onExit,
        )
        GameOverlay.PAUSE -> PauseOverlay(
            moveNumber = ui.moveNumber,
            difficulty = if (viewModel.config.mode == GameMode.VS_COMPUTER) {
                stringResource(viewModel.config.difficulty.labelRes)
            } else {
                null
            },
            onResume = viewModel::resume,
            onRestart = viewModel::askRestart,
            onSettings = viewModel::openSettings,
            // The game is already saved after every turn, so quitting needs no question once a turn was played.
            onQuit = { if (ui.moveNumber >= 1) onExit() else viewModel.askQuit() },
        )
        GameOverlay.SETTINGS -> SettingsOverlay(
            settings = viewModel.settings,
            sound = viewModel.sound,
            onDone = viewModel::backToPause,
        )
        GameOverlay.RESTART_CONFIRM -> ConfirmOverlay(
            title = stringResource(R.string.restart_title),
            message = stringResource(R.string.restart_message),
            dismissText = stringResource(R.string.dialog_cancel),
            confirmText = stringResource(R.string.pause_restart),
            onDismiss = viewModel::backToPause,
            onConfirm = viewModel::confirmRestart,
        )
        GameOverlay.QUIT_CONFIRM -> ConfirmOverlay(
            title = stringResource(R.string.quit_title),
            message = savedMessage,
            dismissText = stringResource(R.string.dialog_cancel),
            confirmText = stringResource(R.string.quit_confirm),
            onDismiss = viewModel::backToPause,
            onConfirm = onExit,
        )
        // The result screen takes over; nothing is drawn here and the board cannot be tapped.
        GameOverlay.GAME_OVER -> Unit
    }
}

private fun playerName(viewModel: GameViewModel, player: Player): String =
    if (player == Player.ONE) viewModel.config.player1Name else viewModel.config.player2Name

@Composable
private fun LoadErrorPanel(state: AssetsState.Failed, onExit: () -> Unit) {
    val message = stringResource(
        when (state.failure) {
            AssetsFailure.GEOMETRY_MISSING -> R.string.game_error_geometry_missing
            AssetsFailure.GEOMETRY_INVALID -> R.string.game_error_geometry_invalid
            AssetsFailure.IMAGE_MISSING -> R.string.game_error_image_missing
        },
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(GameDimens.ScreenMargin * 2),
        verticalArrangement = Arrangement.spacedBy(GameDimens.PanelGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.game_error_title),
            style = TwelvePitsType.screenTitle.copy(textAlign = TextAlign.Center),
        )
        BasicText(text = message, style = TwelvePitsType.body.copy(textAlign = TextAlign.Center))
        BasicText(
            text = stringResource(R.string.game_error_detail, state.detail),
            style = TwelvePitsType.small.copy(textAlign = TextAlign.Center),
        )
        OutlinedGoldButton(text = stringResource(R.string.back), onClick = onExit)
    }
}

/** Runs the frame clock only while something moves, so an idle board costs no battery. */
@Composable
private fun GameFrameLoop(viewModel: GameViewModel) {
    LaunchedEffect(viewModel) {
        snapshotFlow { viewModel.needsFrames }.collectLatest { needed ->
            if (needed) {
                viewModel.onFrameLoopStarted()
                while (true) {
                    val nanos = withFrameNanos { it }
                    viewModel.onFrame(nanos)
                }
            }
        }
    }
}

/**
 * Landscape, immersive (system bars hidden), screen kept on, camera cut-out used. Everything is put
 * back when the screen is left for good. A rotation or a restart of the activity does not put it back.
 */
@Composable
private fun GameWindowEffect(activity: Activity?, viewModel: GameViewModel) {
    val view = LocalView.current
    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            val window = activity.window
            val previousCutoutMode = readCutoutMode(window)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setCutoutMode(window, gameCutoutMode())
            hideSystemBars(activity, view)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                if (!activity.isChangingConfigurations) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    showSystemBars(activity, view)
                    setCutoutMode(window, previousCutoutMode)
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    viewModel.onScreenClosed()
                }
            }
        }
    }
}

private fun hideSystemBars(activity: Activity, view: View) {
    val controller = WindowInsetsControllerCompat(activity.window, view)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
}

private fun showSystemBars(activity: Activity, view: View) {
    WindowInsetsControllerCompat(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
}

private fun readCutoutMode(window: Window): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode else 0

private fun gameCutoutMode(): Int = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    else -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
}

private fun setCutoutMode(window: Window, mode: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val attributes = window.attributes
        attributes.layoutInDisplayCutoutMode = mode
        window.attributes = attributes
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
