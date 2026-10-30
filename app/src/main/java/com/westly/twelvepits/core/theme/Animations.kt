package com.westly.twelvepits.core.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** False when the phone's "Remove animations" setting (animator duration scale 0) is on. */
fun animationsEnabled(context: Context): Boolean =
    try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    } catch (e: RuntimeException) {
        true
    }

/** The same value as a state that is read again every time the screen comes back to the front. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(animationsEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = animationsEnabled(context) }
    return enabled
}
