package com.westly.twelvepits.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import androidx.annotation.RawRes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.westly.twelvepits.R
import com.westly.twelvepits.core.storage.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Every short sound effect, with the raw file it plays. */
enum class Sfx(@RawRes val resId: Int) {
    BUTTON_TAP(R.raw.sfx_button_tap),
    LAUNCH_SHIMMER(R.raw.sfx_launch_shimmer),
    SEEDS_PICKUP(R.raw.sfx_seeds_pickup),
    SEED_DROP_A(R.raw.sfx_seed_drop_a),
    SEED_DROP_B(R.raw.sfx_seed_drop_b),
    SEED_DROP_C(R.raw.sfx_seed_drop_c),
    CAPTURE(R.raw.sfx_capture),
    TURN_SKIPPED(R.raw.sfx_turn_skipped),
    INVALID_MOVE(R.raw.sfx_invalid_move),
    VICTORY(R.raw.sfx_victory),
    DEFEAT(R.raw.sfx_defeat),
}

private enum class MusicTrack(@RawRes val resId: Int?) {
    NONE(null),
    MENU(R.raw.music_menu_loop),
    GAME(R.raw.music_game_loop),
}

/**
 * Plays every sound of the game. Call it from the main thread only.
 *
 * Short effects use SoundPool (all preloaded, 10 parallel streams, low latency).
 * Music uses Media3 ExoPlayer with REPEAT_MODE_ONE: the player queues the same file again
 * before it ends, so the output never stops and restarts. MediaPlayer.setLooping can click
 * at the loop point on some phones.
 *
 * Music, music volume and sound effects are read live from [SettingsStore].
 * Music stops when the app goes to the background and comes back when it returns.
 */
class SoundManager(
    context: Context,
    settings: SettingsStore,
) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val poolIds = HashMap<Sfx, Int>()
    private val readyPoolIds = HashSet<Int>()
    private var finishedLoading = 0
    private val _soundsReady = MutableStateFlow(false)

    /** True when every sound effect has finished loading (or failed to load). */
    val soundsReady: StateFlow<Boolean> = _soundsReady.asStateFlow()

    private var player: ExoPlayer? = null
    private var loadedTrack = MusicTrack.NONE
    private var wantedTrack = MusicTrack.NONE
    private var inForeground = true
    private var settingsLoaded = false
    private var musicEnabled = SettingsStore.DEFAULT_MUSIC_ENABLED
    private var musicVolume = SettingsStore.DEFAULT_MUSIC_VOLUME
    private var soundEffectsEnabled = SettingsStore.DEFAULT_SOUND_EFFECTS_ENABLED

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) readyPoolIds.add(sampleId)
            finishedLoading += 1
            if (finishedLoading >= Sfx.entries.size) _soundsReady.value = true
        }
        for (sfx in Sfx.entries) {
            val id = runCatching { soundPool.load(appContext, sfx.resId, 1) }.getOrNull()
            if (id != null) {
                poolIds[sfx] = id
            } else {
                finishedLoading += 1
            }
        }
        if (finishedLoading >= Sfx.entries.size) _soundsReady.value = true

        scope.launch {
            settings.musicEnabled.collect {
                musicEnabled = it
                settingsLoaded = true
                applyMusic()
            }
        }
        scope.launch {
            settings.musicVolume.collect {
                musicVolume = it
                applyMusic()
            }
        }
        scope.launch {
            settings.soundEffectsEnabled.collect { soundEffectsEnabled = it }
        }
    }

    fun playSfx(sfx: Sfx) {
        if (!soundEffectsEnabled) return
        val id = poolIds[sfx] ?: return
        if (id !in readyPoolIds) return
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    /** One seed lands in a pit: picks one of the three drop sounds at random. */
    fun playSeedDrop() {
        val drops = listOf(Sfx.SEED_DROP_A, Sfx.SEED_DROP_B, Sfx.SEED_DROP_C)
        playSfx(drops[Random.nextInt(drops.size)])
    }

    fun startMenuMusic() {
        wantedTrack = MusicTrack.MENU
        applyMusic()
    }

    fun startGameMusic() {
        wantedTrack = MusicTrack.GAME
        applyMusic()
    }

    fun stopMusic() {
        wantedTrack = MusicTrack.NONE
        applyMusic()
    }

    fun onAppForeground() {
        inForeground = true
        applyMusic()
    }

    fun onAppBackground() {
        inForeground = false
        applyMusic()
    }

    /** Lets the volume slider move the music volume while it is being dragged. */
    fun previewMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0f, 1f)
        player?.volume = musicVolume
    }

    private fun applyMusic() {
        if (!settingsLoaded) return
        val shouldPlay = wantedTrack != MusicTrack.NONE && inForeground && musicEnabled
        if (!shouldPlay) {
            player?.playWhenReady = false
            if (wantedTrack == MusicTrack.NONE) {
                player?.stop()
                player?.clearMediaItems()
                loadedTrack = MusicTrack.NONE
            }
            return
        }
        val active = player ?: createPlayer() ?: return
        if (loadedTrack != wantedTrack) {
            val resId = wantedTrack.resId ?: return
            val uri = Uri.parse("android.resource://${appContext.packageName}/$resId")
            active.setMediaItem(MediaItem.fromUri(uri))
            active.prepare()
            loadedTrack = wantedTrack
        }
        active.volume = musicVolume
        active.playWhenReady = true
    }

    private fun createPlayer(): ExoPlayer? {
        val created = runCatching {
            ExoPlayer.Builder(appContext).build().also {
                it.repeatMode = Player.REPEAT_MODE_ONE
            }
        }.getOrNull()
        player = created
        return created
    }

    private companion object {
        const val MAX_STREAMS = 10
    }
}
