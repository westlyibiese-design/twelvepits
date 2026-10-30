package com.westly.twelvepits.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RawRes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.westly.twelvepits.R
import com.westly.twelvepits.core.storage.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val TAG = "TwelvePits"

/** Parallel sound effect streams. At Fast speed a seed lands every 0.20 s and a drop sound lasts about 0.13 s. */
private const val MAX_STREAMS = 12

private const val PRIORITY_DROP = 0
private const val PRIORITY_NORMAL = 1
private const val PRIORITY_IMPORTANT = 2

/** How often the fades are moved forward, in milliseconds. */
private const val TICK_MS = 16L

/** A pause longer than this (a frozen phone) does not make a fade jump. */
private const val MAX_TICK_MS = 100f

/** Tracks that are still fading out. More than this are cut at once. */
private const val MAX_FADING_VOICES = 3

/** Every short sound effect, with the raw file it plays. */
enum class Sfx(
    @RawRes val resId: Int,
    /** SoundPool keeps the higher priority when all streams are busy. */
    val priority: Int = PRIORITY_NORMAL,
    /** While this sound plays the music is ducked for this long (0 = not at all). */
    val duckMusicMs: Long = 0L,
) {
    BUTTON_TAP(R.raw.sfx_button_tap),
    LAUNCH_SHIMMER(R.raw.sfx_launch_shimmer),
    SEEDS_PICKUP(R.raw.sfx_seeds_pickup, PRIORITY_IMPORTANT),
    SEED_DROP_A(R.raw.sfx_seed_drop_a, PRIORITY_DROP),
    SEED_DROP_B(R.raw.sfx_seed_drop_b, PRIORITY_DROP),
    SEED_DROP_C(R.raw.sfx_seed_drop_c, PRIORITY_DROP),
    CAPTURE(R.raw.sfx_capture, PRIORITY_IMPORTANT),
    TURN_SKIPPED(R.raw.sfx_turn_skipped, PRIORITY_IMPORTANT),
    INVALID_MOVE(R.raw.sfx_invalid_move, PRIORITY_IMPORTANT),
    VICTORY(R.raw.sfx_victory, PRIORITY_IMPORTANT, duckMusicMs = 2500L),
    DEFEAT(R.raw.sfx_defeat, PRIORITY_IMPORTANT, duckMusicMs = 2400L),
}

private val SEED_DROPS = arrayOf(Sfx.SEED_DROP_A, Sfx.SEED_DROP_B, Sfx.SEED_DROP_C)

@RawRes
private fun rawResOf(track: MusicTrack): Int? = when (track) {
    MusicTrack.NONE -> null
    MusicTrack.MENU -> R.raw.music_menu_loop
    MusicTrack.GAME -> R.raw.music_game_loop
}

/** What a track does once its fade has reached silence. */
private enum class AfterSilent { NONE, PAUSE, RELEASE }

/** One music player with its own fade level (0 = silent, 1 = full). */
private class Voice(val track: MusicTrack, val player: ExoPlayer) {
    var level = 0f
        private set
    var target = 0f
        private set
    var afterSilent = AfterSilent.NONE
        private set
    private var rampMs = MusicPolicy.FADE_IN_MS

    val isAudible: Boolean get() = player.playWhenReady && level > 0f

    val isRamping: Boolean get() = level != target

    fun fadeTo(newTarget: Float, durationMs: Float, action: AfterSilent) {
        target = newTarget
        rampMs = durationMs
        afterSilent = action
    }

    /** Moves the level one step. True when the level is silent and an action is waiting for that. */
    fun step(elapsedMs: Float): Boolean {
        if (level != target) level = Ramp.step(level, target, elapsedMs, rampMs)
        return level == target && target == 0f && afterSilent != AfterSilent.NONE
    }

    fun start() {
        player.playWhenReady = true
    }

    /** Pauses at once. The next start fades in from silence. */
    fun pauseNow() {
        level = 0f
        target = 0f
        afterSilent = AfterSilent.NONE
        player.playWhenReady = false
    }

    fun applyVolume(chosenVolume: Float, duck: Float) {
        player.volume = MusicPolicy.effectiveVolume(chosenVolume, duck, level)
    }

    fun release() {
        try {
            player.release()
        } catch (e: RuntimeException) {
            Log.w(TAG, "A music player could not be released: ${e.javaClass.simpleName}")
        }
    }
}

/**
 * Plays every sound of the game and also the vibration. Call it from the main thread only.
 *
 * Short effects use SoundPool (all preloaded, 12 parallel streams, low latency, never any audio focus).
 * Music uses Media3 ExoPlayer with REPEAT_MODE_ONE, which queues the same file again before it ends, so
 * the output never stops and restarts. Each track has its own fade level. The music:
 * - asks for the audio focus when it starts and gives it back when it stops,
 * - pauses for a phone call or an assistant (and comes back), ducks to 30% when another app only wants
 *   the music quieter, and stays paused when another app takes the audio for good,
 * - pauses when headphones are unplugged, and when the app is not visible,
 * - fades in and out over 400 ms and cross-fades between the menu and the game track over 500 ms,
 * - follows the volume slider at once, and ducks to 40% while the victory or defeat sound plays.
 * Music, music volume, sound effects and vibration are read live from [SettingsStore].
 */
class SoundManager(
    context: Context,
    settings: SettingsStore,
) {
    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager: AudioManager? = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val haptics = Haptics(appContext)

    // ---- Sound effects ----

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

    /** The latest streams, so the Sound effects switch can cut them at once. A ring: the oldest is replaced. */
    private val activeStreams = IntArray(MAX_STREAMS)
    private var nextStreamSlot = 0

    // ---- Music ----

    private var current: Voice? = null
    private val fadingVoices = ArrayList<Voice>()
    private var wantedTrack = MusicTrack.NONE
    private var inForeground = true
    private var settingsLoaded = false

    /** Headphones were unplugged or another app took the audio for good: silent until the app comes back. */
    private var suppressed = false

    /** A phone call or an assistant has the audio for a moment: paused, waiting for the focus to return. */
    private var focusPaused = false
    private var hasFocus = false
    private var focusDucked = false
    private var resultSoundPlaying = false
    private var duck = 1f
    private var duckTarget = 1f
    private var tickJob: Job? = null
    private var resultDuckJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false

    private var musicEnabled = SettingsStore.DEFAULT_MUSIC_ENABLED
    private var musicVolume = SettingsStore.DEFAULT_MUSIC_VOLUME
    private var soundEffectsEnabled = SettingsStore.DEFAULT_SOUND_EFFECTS_ENABLED
    private var vibrationEnabled = SettingsStore.DEFAULT_VIBRATION_ENABLED

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                focusPaused = false
                focusDucked = false
                updateDuckTarget()
                applyMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasFocus = false
                focusPaused = true
                focusDucked = false
                updateDuckTarget()
                applyMusic()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusDucked = true
                updateDuckTarget()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                suppressed = true
                applyMusic()
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                suppressed = true
                applyMusic()
            }
        }
    }

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                readyPoolIds.add(sampleId)
            } else {
                Log.w(TAG, "A sound effect could not be loaded (status $status). It stays silent.")
            }
            finishedLoading += 1
            if (finishedLoading >= Sfx.entries.size) _soundsReady.value = true
        }
        for (sfx in Sfx.entries) {
            val id = try {
                soundPool.load(appContext, sfx.resId, 1)
            } catch (e: RuntimeException) {
                Log.w(TAG, "A sound effect could not be requested: ${e.javaClass.simpleName}")
                null
            }
            if (id != null) {
                poolIds[sfx] = id
            } else {
                finishedLoading += 1
            }
        }
        if (finishedLoading >= Sfx.entries.size) _soundsReady.value = true

        scope.launch {
            settings.musicEnabled.collect { enabled ->
                // Switching Music on again is a clear wish to hear it, so it ends any "stay silent".
                if (enabled && !musicEnabled) suppressed = false
                musicEnabled = enabled
                settingsLoaded = true
                applyMusic()
            }
        }
        scope.launch {
            settings.musicVolume.collect {
                musicVolume = it
                updateVolumes()
            }
        }
        scope.launch {
            settings.soundEffectsEnabled.collect { enabled ->
                soundEffectsEnabled = enabled
                if (!enabled) stopAllEffects()
            }
        }
        scope.launch {
            settings.vibrationEnabled.collect { vibrationEnabled = it }
        }
    }

    // ---- Public: effects and vibration ----

    fun playSfx(sfx: Sfx) {
        if (!soundEffectsEnabled) return
        val id = poolIds[sfx] ?: return
        if (id !in readyPoolIds) return
        val stream = try {
            soundPool.play(id, 1f, 1f, sfx.priority, 0, 1f)
        } catch (e: RuntimeException) {
            Log.w(TAG, "A sound effect could not be played: ${e.javaClass.simpleName}")
            0
        }
        if (stream == 0) return
        activeStreams[nextStreamSlot] = stream
        nextStreamSlot = (nextStreamSlot + 1) % activeStreams.size
        if (sfx.duckMusicMs > 0L) duckMusicFor(sfx.duckMusicMs)
    }

    /** One seed lands in a pit: picks one of the three drop sounds at random. */
    fun playSeedDrop() {
        playSfx(SEED_DROPS[Random.nextInt(SEED_DROPS.size)])
    }

    /** Vibrates, but only while the Vibration setting is on. */
    fun vibrate(effect: HapticEffect) {
        if (!vibrationEnabled) return
        haptics.play(effect)
    }

    // ---- Public: music ----

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

    /** The app is visible again: music that was silenced by headphones or another app may play again. */
    fun onAppForeground() {
        inForeground = true
        suppressed = false
        applyMusic()
    }

    fun onAppBackground() {
        inForeground = false
        applyMusic()
    }

    /** Lets the volume slider move the music volume while it is being dragged. */
    fun previewMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0f, 1f)
        updateVolumes()
    }

    // ---- Music: decisions ----

    private fun applyMusic() {
        when (
            MusicPolicy.decide(
                settingsLoaded = settingsLoaded,
                wanted = wantedTrack,
                musicEnabled = musicEnabled,
                appVisible = inForeground,
                suppressed = suppressed,
                focusPaused = focusPaused,
            )
        ) {
            MusicAction.WAIT -> Unit
            MusicAction.RELEASE_FADED -> silence(fade = true, release = true, keepFocus = false)
            MusicAction.PAUSE_FADED -> silence(fade = true, release = false, keepFocus = false)
            MusicAction.PAUSE_NOW -> silence(fade = false, release = false, keepFocus = false)
            MusicAction.PAUSE_KEEP_FOCUS -> silence(fade = false, release = false, keepFocus = true)
            MusicAction.PLAY -> play()
        }
    }

    private fun play() {
        if (!requestFocus()) return
        val track = wantedTrack
        val old = current
        if (old != null && old.track != track) {
            // A short cross-fade: the old track fades out while the new one fades in.
            old.fadeTo(0f, MusicPolicy.CROSSFADE_MS, AfterSilent.RELEASE)
            retire(old)
            current = null
        }
        val voice = current ?: createVoice(track)?.also { current = it } ?: return
        val fadeMs = if (fadingVoices.isEmpty()) MusicPolicy.FADE_IN_MS else MusicPolicy.CROSSFADE_MS
        voice.fadeTo(1f, fadeMs, AfterSilent.NONE)
        voice.start()
        registerNoisy()
        updateVolumes()
        startTicker()
    }

    private fun silence(fade: Boolean, release: Boolean, keepFocus: Boolean) {
        val voice = current
        if (voice != null) {
            if (fade && voice.isAudible) {
                voice.fadeTo(
                    0f,
                    MusicPolicy.FADE_OUT_MS,
                    if (release) AfterSilent.RELEASE else AfterSilent.PAUSE,
                )
            } else if (release) {
                voice.release()
                current = null
            } else {
                voice.pauseNow()
            }
        }
        if (!fade) releaseFadingVoices()
        if (!keepFocus) abandonFocus()
        unregisterNoisy()
        updateVolumes()
        startTicker()
    }

    private fun retire(voice: Voice) {
        fadingVoices.add(voice)
        while (fadingVoices.size > MAX_FADING_VOICES) {
            fadingVoices.removeAt(0).release()
        }
    }

    private fun releaseFadingVoices() {
        for (voice in fadingVoices) voice.release()
        fadingVoices.clear()
    }

    private fun createVoice(track: MusicTrack): Voice? {
        val resId = rawResOf(track) ?: return null
        val player = try {
            ExoPlayer.Builder(appContext)
                .setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setUsage(C.USAGE_GAME)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // The focus is handled here, so the volume and the fades stay under one control.
                    false,
                )
                .build()
        } catch (e: RuntimeException) {
            Log.w(TAG, "The music player could not be created: ${e.javaClass.simpleName}")
            return null
        }
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.volume = 0f
        val voice = Voice(track, player)
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "The music could not be played: ${error.errorCodeName}. It stays silent.")
                    // Not released inside the player's own callback.
                    mainHandler.post { dropVoice(voice) }
                }
            },
        )
        player.setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${appContext.packageName}/$resId")))
        player.prepare()
        return voice
    }

    private fun dropVoice(voice: Voice) {
        voice.release()
        if (current === voice) current = null
        fadingVoices.remove(voice)
    }

    // ---- Music: audio focus and headphones ----

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        val manager = audioManager ?: return true
        val request = focusRequest ?: buildFocusRequest().also { focusRequest = it }
        val result = try {
            manager.requestAudioFocus(request)
        } catch (e: RuntimeException) {
            Log.w(TAG, "The audio focus could not be requested: ${e.javaClass.simpleName}")
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        return when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                hasFocus = true
                focusPaused = false
                true
            }
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                // A call is going on: the system tells us when the focus is ours.
                focusPaused = true
                false
            }
            else -> false
        }
    }

    private fun buildFocusRequest(): AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusListener)
            .build()

    private fun abandonFocus() {
        val request = focusRequest
        if (request != null) {
            try {
                audioManager?.abandonAudioFocusRequest(request)
            } catch (e: RuntimeException) {
                Log.w(TAG, "The audio focus could not be given back: ${e.javaClass.simpleName}")
            }
        }
        focusRequest = null
        hasFocus = false
        focusPaused = false
        if (focusDucked) {
            focusDucked = false
            updateDuckTarget()
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        noisyRegistered = try {
            appContext.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "The headphone listener could not be started: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        noisyRegistered = false
        try {
            appContext.unregisterReceiver(noisyReceiver)
        } catch (e: RuntimeException) {
            Log.w(TAG, "The headphone listener could not be stopped: ${e.javaClass.simpleName}")
        }
    }

    // ---- Music: volume, ducking and fades ----

    private fun updateVolumes() {
        current?.applyVolume(musicVolume, duck)
        for (voice in fadingVoices) voice.applyVolume(musicVolume, duck)
    }

    private fun updateDuckTarget() {
        duckTarget = MusicPolicy.duckTarget(focusDucked, resultSoundPlaying)
        startTicker()
    }

    /** The music is quieter while the victory or defeat sound plays, then comes back. */
    private fun duckMusicFor(durationMs: Long) {
        resultSoundPlaying = true
        updateDuckTarget()
        resultDuckJob?.cancel()
        resultDuckJob = scope.launch {
            delay(durationMs)
            resultSoundPlaying = false
            updateDuckTarget()
        }
    }

    private fun isAnimating(): Boolean {
        val voice = current
        return duck != duckTarget ||
            fadingVoices.isNotEmpty() ||
            (voice != null && (voice.isRamping || voice.afterSilent != AfterSilent.NONE))
    }

    /** Runs only while something fades, so an idle game costs nothing. */
    private fun startTicker() {
        if (tickJob?.isActive == true || !isAnimating()) return
        var last = SystemClock.uptimeMillis()
        tickJob = scope.launch {
            while (isActive && isAnimating()) {
                delay(TICK_MS)
                val now = SystemClock.uptimeMillis()
                tick((now - last).toFloat().coerceAtMost(MAX_TICK_MS))
                last = now
            }
        }
    }

    private fun tick(elapsedMs: Float) {
        duck = Ramp.step(duck, duckTarget, elapsedMs, MusicPolicy.DUCK_RAMP_MS)
        val voice = current
        if (voice != null && voice.step(elapsedMs)) {
            when (voice.afterSilent) {
                AfterSilent.RELEASE -> {
                    voice.release()
                    current = null
                }
                AfterSilent.PAUSE -> voice.pauseNow()
                AfterSilent.NONE -> Unit
            }
        }
        val iterator = fadingVoices.iterator()
        while (iterator.hasNext()) {
            val fading = iterator.next()
            if (fading.step(elapsedMs)) {
                fading.release()
                iterator.remove()
            }
        }
        updateVolumes()
    }

    private fun stopAllEffects() {
        for (slot in activeStreams.indices) {
            val stream = activeStreams[slot]
            if (stream != 0) {
                try {
                    soundPool.stop(stream)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "A sound effect could not be stopped: ${e.javaClass.simpleName}")
                }
                activeStreams[slot] = 0
            }
        }
    }
}
