package com.westly.twelvepits.core.audio

/** The music that is wanted right now. [NONE] means silence. */
enum class MusicTrack { NONE, MENU, GAME }

/** What the music has to do after something changed. */
enum class MusicAction {
    /** The settings are not read yet: do nothing. */
    WAIT,

    /** Fade out, then throw the player away (the music was stopped on purpose). */
    RELEASE_FADED,

    /** Fade out, then pause (the Music setting was switched off). */
    PAUSE_FADED,

    /** Pause at once and give the audio focus back (app in the background, headphones unplugged). */
    PAUSE_NOW,

    /** Pause at once but keep waiting for the audio focus (a phone call or an assistant is talking). */
    PAUSE_KEEP_FOCUS,

    /** Play, with a fade in. */
    PLAY,
}

/** The rules and numbers of the music behaviour. Pure Kotlin, so it can be unit tested. */
object MusicPolicy {
    const val FADE_IN_MS = 400f
    const val FADE_OUT_MS = 400f
    const val CROSSFADE_MS = 500f

    /** How fast the ducking level moves. */
    const val DUCK_RAMP_MS = 150f

    /** Volume factor while another app asks the music to get quieter. */
    const val FOCUS_DUCK = 0.3f

    /** Volume factor while the victory or defeat sound plays. */
    const val RESULT_DUCK = 0.4f

    /** The first matching line wins, from the most to the least important. */
    fun decide(
        settingsLoaded: Boolean,
        wanted: MusicTrack,
        musicEnabled: Boolean,
        appVisible: Boolean,
        suppressed: Boolean,
        focusPaused: Boolean,
    ): MusicAction = when {
        !settingsLoaded -> MusicAction.WAIT
        wanted == MusicTrack.NONE -> MusicAction.RELEASE_FADED
        !musicEnabled -> MusicAction.PAUSE_FADED
        !appVisible -> MusicAction.PAUSE_NOW
        suppressed -> MusicAction.PAUSE_NOW
        focusPaused -> MusicAction.PAUSE_KEEP_FOCUS
        else -> MusicAction.PLAY
    }

    /** The ducking level the music moves toward. Both causes together use the quieter one. */
    fun duckTarget(focusDucked: Boolean, resultSoundPlaying: Boolean): Float =
        minOf(if (focusDucked) FOCUS_DUCK else 1f, if (resultSoundPlaying) RESULT_DUCK else 1f)

    /** The volume given to the player: the chosen volume times the ducking level times the fade level. */
    fun effectiveVolume(chosenVolume: Float, duck: Float, fade: Float): Float =
        (chosenVolume * duck * fade).coerceIn(0f, 1f)
}
