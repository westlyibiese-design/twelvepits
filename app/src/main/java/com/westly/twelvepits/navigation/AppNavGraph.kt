package com.westly.twelvepits.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.westly.twelvepits.core.theme.GameDialog
import com.westly.twelvepits.feature.game.ui.GameScreen
import com.westly.twelvepits.feature.game.ui.GameViewModel
import com.westly.twelvepits.feature.game.ui.GameViewModelFactory
import com.westly.twelvepits.feature.game.ui.OrientationGuard
import com.westly.twelvepits.feature.howtoplay.HowToPlayScreen
import com.westly.twelvepits.feature.launch.LaunchScreen
import com.westly.twelvepits.feature.menu.GameSetupScreen
import com.westly.twelvepits.feature.menu.MainMenuScreen
import com.westly.twelvepits.feature.menu.ModeSelectScreen
import com.westly.twelvepits.feature.menu.SetupKind
import com.westly.twelvepits.feature.settings.SettingsScreen

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
                MainMenuScreen(
                    savedGames = container.savedGames,
                    // Replaced in Phase 5
                    onContinue = {},
                    onPlay = { navController.navigate(Routes.MODE_SELECT) },
                    onHowToPlay = { navController.navigate(Routes.HOW_TO_PLAY) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.MODE_SELECT) {
                ModeSelectScreen(
                    onBack = { navController.popBackStack() },
                    onPlayComputer = { navController.navigate(Routes.SETUP_COMPUTER) },
                    onTwoPlayers = { navController.navigate(Routes.SETUP_TWO_PLAYERS) },
                )
            }
            composable(Routes.SETUP_COMPUTER) {
                // Replaced in Phase 5: the computer opponent arrives with its own phase.
                var comingSoon by remember { mutableStateOf(false) }
                GameSetupScreen(
                    kind = SetupKind.COMPUTER,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                    onStart = { comingSoon = true },
                )
                if (comingSoon) {
                    GameDialog(
                        title = stringResource(R.string.mode_computer_title),
                        message = stringResource(R.string.coming_next_phase),
                        confirmText = stringResource(R.string.game_over_ok),
                        onConfirm = { comingSoon = false },
                        onDismiss = { comingSoon = false },
                    )
                }
            }
            composable(Routes.SETUP_TWO_PLAYERS) {
                GameSetupScreen(
                    kind = SetupKind.TWO_PLAYERS,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                    onStart = { config -> navController.navigate(Routes.game(config)) },
                )
            }
            composable(Routes.HOW_TO_PLAY) {
                HowToPlayScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = container.settings,
                    sound = container.sound,
                    onBack = { navController.popBackStack() },
                )
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
                ),
            ) { entry ->
                val config = Routes.configFrom(entry.arguments)
                if (config == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    // The game and its running animation live in this ViewModel, which belongs to
                    // this navigation entry: turning the phone does not restart anything.
                    val viewModel = remember(entry) {
                        ViewModelProvider(
                            entry,
                            GameViewModelFactory(config, container.settings, container.sound),
                        )[GameViewModel::class.java]
                    }
                    GameScreen(
                        viewModel = viewModel,
                        onGameFinished = { returnToMenu() },
                        onExit = returnToMenu,
                    )
                }
            }
        }
    }
}
