package com.westly.twelvepits.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.twelvepits.R
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.model.SeedSpeed
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.core.theme.GoldLine
import com.westly.twelvepits.core.theme.GoldSlider
import com.westly.twelvepits.core.theme.GoldSwitch
import com.westly.twelvepits.core.theme.ScreenScaffold
import com.westly.twelvepits.core.theme.SegmentedSelector
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.Cinzel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    sound: SoundManager,
    onBack: () -> Unit,
) {
    val musicOn by settings.musicEnabled
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_MUSIC_ENABLED)
    val savedVolume by settings.musicVolume
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_MUSIC_VOLUME)
    val effectsOn by settings.soundEffectsEnabled
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_SOUND_EFFECTS_ENABLED)
    val vibrationOn by settings.vibrationEnabled
        .collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_VIBRATION_ENABLED)
    val hopSeconds by settings.seedHopSeconds
        .collectAsStateWithLifecycle(initialValue = SeedSpeed.DEFAULT.hopSeconds)
    var draggedVolume by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            SettingRow(label = stringResource(R.string.settings_music)) {
                GoldSwitch(
                    checked = musicOn,
                    onCheckedChange = { scope.launch { settings.setMusicEnabled(it) } },
                )
            }
            Divider()
            SettingRow(label = stringResource(R.string.settings_music_volume), labelWeight = 0.4f) {
                GoldSlider(
                    value = draggedVolume ?: savedVolume,
                    description = stringResource(R.string.settings_music_volume),
                    onValueChange = {
                        draggedVolume = it
                        sound.previewMusicVolume(it)
                    },
                    onValueChangeFinished = {
                        val chosen = draggedVolume
                        if (chosen != null) {
                            scope.launch {
                                settings.setMusicVolume(chosen)
                                draggedVolume = null
                            }
                        }
                    },
                    modifier = Modifier.weight(0.6f),
                )
            }
            Divider()
            SettingRow(label = stringResource(R.string.settings_sound_effects)) {
                GoldSwitch(
                    checked = effectsOn,
                    onCheckedChange = { scope.launch { settings.setSoundEffectsEnabled(it) } },
                )
            }
            Divider()
            SettingRow(label = stringResource(R.string.settings_vibration)) {
                GoldSwitch(
                    checked = vibrationOn,
                    onCheckedChange = { scope.launch { settings.setVibrationEnabled(it) } },
                )
            }
            Divider()
            BasicText(
                text = stringResource(R.string.settings_seed_speed),
                style = TwelvePitsType.label,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            val speeds = SeedSpeed.entries
            SegmentedSelector(
                options = listOf(
                    stringResource(R.string.seed_speed_slow),
                    stringResource(R.string.seed_speed_normal),
                    stringResource(R.string.seed_speed_fast),
                ),
                selectedIndex = speeds.indexOf(SeedSpeed.fromHopSeconds(hopSeconds)),
                onSelected = { scope.launch { settings.setSeedSpeed(speeds[it]) } },
            )
        }
        Footer(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp))
    }
}

@Composable
private fun SettingRow(
    label: String,
    labelWeight: Float = 1f,
    control: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            text = label,
            style = TwelvePitsType.body,
            modifier = Modifier.weight(labelWeight),
        )
        control()
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GoldLine.copy(alpha = 0.5f)))
}

@Composable
private fun Footer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember { readVersionName(context) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BasicText(
            text = stringResource(R.string.settings_footer_title),
            style = TwelvePitsType.cardTitle.copy(
                fontFamily = Cinzel,
                textAlign = TextAlign.Center,
            ),
        )
        BasicText(
            text = if (version.isEmpty()) {
                stringResource(R.string.publisher)
            } else {
                stringResource(R.string.settings_footer_version, version)
            },
            style = TwelvePitsType.label.copy(textAlign = TextAlign.Center),
        )
    }
}

/** The real versionName of the installed build. Empty if the system cannot tell. */
private fun readVersionName(context: Context): String =
    runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
