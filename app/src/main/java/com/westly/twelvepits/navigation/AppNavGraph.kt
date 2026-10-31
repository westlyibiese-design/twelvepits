package com.westly.twelvepits.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.westly.twelvepits.AppContainer
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.theme.GameDialog
import com.westly.twelvepits.feature.game.ui.GameScreen
import com.westly.twelvepits.feature.game.ui.GameViewModel
import com.westly.twelvepits.feature.game.ui.GameViewModelFactory
import com.westly.twelvepits.feature.game.ui.OrientationGuard
import com.westly.twelvepits.feature.howtoplay.HowToPlayScreen
import com.westly.twelvepits.feature.launch.LaunchScreen
import com.westly.twelvepits.feature.game.data.StartMode
import com.westly.twelvepits.feature.game.multiplayer.PhonesScreen
import com.westly.twelvepits.feature.menu.GameSetupHost
import com.westly.twelvepits.feature.menu.MainMenuScreen
import com.westly.twelvepits.feature.menu.ModeSelectScreen
import com.westly.twelvepits.feature.menu.SetupKind
import com.westly.twelvepits.feature.result.PhonesResultHost
import com.westly.twelvepits.feature.result.ResultScreen
import com.westly.twelvepits.feature.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val FADE_MS = 400

@Composable
fun AppNavGraph(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val expectLandscape = Routes.isGame(currentEntry?.destination?.route)

    // Back to the main menu from the game screen (Leave, Quit and the game-over dialog).
    val returnToMenu: () -> Unit = {
        // Two phones: the other phone is told and the connection is closed. Nothing happens in the other modes.
        container.closePhonesSession()
        navController.popBackStack(Routes.MENU, inclusive = false)
    }

    OrientationGuard(expectLandscape = expectLandscape) {
        NavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
            startDestination = Routes.LAUNCH,
            enterTransition = { fadeIn(tween(FADE_MS)) },
            exitTransition = { fadeOut(tween(FADE_MS)) },
            popEnterTransition = { fadeIn(tween(FADE_MS)) },
            popExitTransition = { fadeOut(tween(FADE_MS)) },
        ) {
            composable(Routes.LAUNCH) {
                LaunchScreen(
                    settings = container.settings,
                    sound = container.sound,
                    onFinished = {
                        navController.navigate(Routes.MENU) {
                            popUpTo(Routes.LAUNCH) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.MENU) {
                LaunchedEffect(Unit) { container.sound.startMenuMusic() }
                val scope = rememberCoroutineScope()
                var opening by remember { mutableStateOf(false) }
                var loadFailed by remember { mutableStateOf(false) }
                MainMenuScreen(
                    savedGames = container.savedGames,
                    onContinue = {
                        if (!opening) {
                            opening = true
                            scope.launch {
                                val saved = container.savedGames.loadSavedGame()
                                if (saved == null) {
                                    loadFailed = true
                                } else {
                                    navController.navigate(Routes.game(saved.config, StartMode.CONTINUE))
                                }
                                opening = false
                            }
                        }
                    },
                    onPlay = { navController.navigate(Routes.MODE_SELECT) },
                    onHowToPlay = { navController.navigate(Routes.HOW_TO_PLAY) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
                if (loadFailed) {
                    GameDialog(
                        title = stringResource(R.string.continue_failed_title),
                        message = stringResource(R.string.continue_failed_message),
                        confirmText = stringResource(R.string.dialog_ok),
                        onConfirm = { loadFailed = false },
                        onDismiss = { loadFailed = false },
                    )
                }
            }
            composable(Routes.MODE_SELECT) {
                ModeSelectScreen(
                    onBack = { navController.popBackStack() },
                    onPlayComputer = { navController.navigate(Routes.SETUP_COMPUTER) },
                    onTwoPlayers = { navController.navigate(Routes.SETUP_TWO_PLAYERS) },
                    onTwoPhones = { navController.navigate(Routes.PHONES) },
                )
            }
            composable(Routes.SETUP_COMPUTER) {
                GameSetupHost(
                    kind = SetupKind.COMPUTER,
                    settings = container.settings,
                    savedGames = container.savedGames,
                    onBack = { navController.popBackStack() },
                    onStartGame = { config -> navController.navigate(Routes.game(config)) },
                )
            }
            composable(Routes.SETUP_TWO_PLAYERS) {
                GameSetupHost(
                    kind = SetupKind.TWO_PLAYERS,
                    settings = container.settings,
                    savedGames = container.savedGames,
                    onBack = { navController.popBackStack() },
                    onStartGame = { config -> navController.navigate(Routes.game(config)) },
                )
            }
            composable(Routes.PHONES) {
                val session = remember { container.openPhonesSession() }
                PhonesScreen(
                    session = session,
                    settings = container.settings,
                    savedGames = container.savedGames,
                    onBack = {
                        container.closePhonesSession()
                        navController.popBackStack()
                    },
                    onGameReady = { config -> navController.navigate(Routes.game(config)) },
                )
            }
            composable(Routes.HOW_TO_PLAY) {
                HowToPlayScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = container.settings,
                    sound = container.sound,
                    savedGames = container.savedGames,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RESULT) {
                // The result is kept in memory only. After the app was closed there is none: back to the menu.
                val result = remember { container.pendingResult }
                if (result == null) {
                    LaunchedEffect(Unit) {
                        if (!navController.popBackStack(Routes.MENU, inclusive = false)) {
                            navController.navigate(Routes.MENU) {
                                popUpTo(Routes.RESULT) { inclusive = true }
                            }
                        }
                    }
                } else if (result.config.mode == GameMode.TWO_PHONES) {
                    // Two phones: the match decides what the buttons do. Without a match there is nothing to continue.
                    val match = container.phonesSession?.controller
                    val toMenu: () -> Unit = {
                        container.pendingResult = null
                        returnToMenu()
                    }
                    BackHandler(onBack = toMenu)
                    if (match == null) {
                        LaunchedEffect(Unit) { toMenu() }
                    } else {
                        PhonesResultHost(
                            result = result,
                            match = match,
                            sound = container.sound,
                            onNewGame = { config ->
                                container.pendingResult = null
                                navController.navigate(Routes.game(config)) {
                                    popUpTo(Routes.RESULT) { inclusive = true }
                                }
                            },
                            onMainMenu = toMenu,
                        )
                    }
                } else {
                    val toMenu: () -> Unit = {
                        container.pendingResult = null
                        returnToMenu()
                    }
                    BackHandler(onBack = toMenu)
                    ResultScreen(
                        result = result,
                        sound = container.sound,
                        onPlayAgain = {
                            container.pendingResult = null
                            // The starter was already moved on when the game finished.
                            navController.navigate(Routes.game(result.config)) {
                                popUpTo(Routes.RESULT) { inclusive = true }
                            }
                        },
                        onMainMenu = toMenu,
                    )
                }
            }
            composable(
                route = Routes.GAME_PATTERN,
                arguments = listOf(
                    navArgument(Routes.ARG_MODE) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_DIFFICULTY) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_P1) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_A1) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_P2) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_A2) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_START) { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val config = Routes.configFrom(entry.arguments)
                // Two phones: the match of the open session. Without it the game cannot go on.
                val phonesMatch = if (config?.mode == GameMode.TWO_PHONES) container.phonesSession?.controller else null
                if (config == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else if (config.mode == GameMode.TWO_PHONES && phonesMatch == null) {
                    LaunchedEffect(Unit) { returnToMenu() }
                } else {
                    // Saved with the screen's state: if the system closed the app and this screen is rebuilt,
                    // it must continue the saved game and never start a new one.
                    var started by rememberSaveable { mutableStateOf(false) }
                    val startMode = if (started) {
                        StartMode.RESTORE
                    } else {
                        StartMode.fromArgument(entry.arguments?.getString(Routes.ARG_START))
                    }
                    LaunchedEffect(Unit) { started = true }
                    // The game and its running animation live in this ViewModel, which belongs to
                    // this navigation entry: turning the phone does not restart anything.
                    val viewModel = remember(entry) {
                        ViewModelProvider(
                            entry,
                            GameViewModelFactory(
                                config,
                                startMode,
                                container.settings,
                                container.sound,
                                container.savedGames,
                                phonesMatch,
                            ),
                        )[GameViewModel::class.java]
                    }
                    GameScreen(
                        viewModel = viewModel,
                        onGameFinished = { result ->
                            // Only once: the result is shown and the game screen is removed.
                            if (Routes.isGame(navController.currentDestination?.route)) {
                                container.pendingResult = result
                                navController.navigate(Routes.RESULT) {
                                    popUpTo(Routes.GAME_PATTERN) { inclusive = true }
                                }
                            }
                        },
                        onExit = returnToMenu,
                    )
                }
            }
        }
    }
}
