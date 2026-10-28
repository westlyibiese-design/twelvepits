package com.westly.twelvepits

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.westly.twelvepits.core.audio.Sfx
import com.westly.twelvepits.core.theme.LocalTapSound
import com.westly.twelvepits.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as TwelvePitsApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12 and up always shows a system splash. Ours is invisible (plain #070504, no icon, no animation)
        // and is removed at once, so the player goes straight to our own launch screen.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { false }
        splash.setOnExitAnimationListener { splashView -> splashView.remove() }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val sound = container.sound
        val playTapSound: () -> Unit = { sound.playSfx(Sfx.BUTTON_TAP) }
        setContent {
            CompositionLocalProvider(LocalTapSound provides playTapSound) {
                AppNavGraph(container = container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.sound.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        container.sound.onAppBackground()
    }
}
