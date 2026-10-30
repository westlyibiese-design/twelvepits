package com.westly.twelvepits.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPolicyTest {
    private fun decide(
        settingsLoaded: Boolean = true,
        wanted: MusicTrack = MusicTrack.GAME,
        musicEnabled: Boolean = true,
        appVisible: Boolean = true,
        suppressed: Boolean = false,
        focusPaused: Boolean = false,
    ): MusicAction = MusicPolicy.decide(settingsLoaded, wanted, musicEnabled, appVisible, suppressed, focusPaused)

    @Test
    fun playsWhenEverythingAllowsIt() {
        assertEquals(MusicAction.PLAY, decide())
        assertEquals(MusicAction.PLAY, decide(wanted = MusicTrack.MENU))
    }

    @Test
    fun waitsUntilTheSettingsAreRead() {
        assertEquals(MusicAction.WAIT, decide(settingsLoaded = false))
        assertEquals(MusicAction.WAIT, decide(settingsLoaded = false, wanted = MusicTrack.NONE))
    }

    @Test
    fun stoppingTheMusicFadesOutAndReleasesThePlayer() {
        assertEquals(MusicAction.RELEASE_FADED, decide(wanted = MusicTrack.NONE))
        assertEquals(MusicAction.RELEASE_FADED, decide(wanted = MusicTrack.NONE, appVisible = false))
    }

    @Test
    fun switchingMusicOffFadesOutAndPauses() {
        assertEquals(MusicAction.PAUSE_FADED, decide(musicEnabled = false))
        assertEquals(MusicAction.PAUSE_FADED, decide(musicEnabled = false, appVisible = false))
    }

    @Test
    fun anAppInTheBackgroundIsPausedAtOnce() {
        assertEquals(MusicAction.PAUSE_NOW, decide(appVisible = false))
        assertEquals(MusicAction.PAUSE_NOW, decide(appVisible = false, focusPaused = true))
    }

    @Test
    fun unpluggedHeadphonesPauseAtOnce() {
        assertEquals(MusicAction.PAUSE_NOW, decide(suppressed = true))
        assertEquals(MusicAction.PAUSE_NOW, decide(suppressed = true, focusPaused = true))
    }

    @Test
    fun aPhoneCallPausesButKeepsWaitingForTheFocus() {
        assertEquals(MusicAction.PAUSE_KEEP_FOCUS, decide(focusPaused = true))
    }

    @Test
    fun otherAppsDuckTheMusicToThirtyPercent() {
        assertEquals(0.3f, MusicPolicy.duckTarget(focusDucked = true, resultSoundPlaying = false), 0f)
    }

    @Test
    fun theResultSoundDucksTheMusicToFortyPercent() {
        assertEquals(0.4f, MusicPolicy.duckTarget(focusDucked = false, resultSoundPlaying = true), 0f)
    }

    @Test
    fun nothingToDuckMeansFullVolumeAndTheQuieterCauseWinsWhenBothApply() {
        assertEquals(1f, MusicPolicy.duckTarget(focusDucked = false, resultSoundPlaying = false), 0f)
        assertEquals(0.3f, MusicPolicy.duckTarget(focusDucked = true, resultSoundPlaying = true), 0f)
    }

    @Test
    fun theVolumeIsTheChosenVolumeTimesDuckTimesFade() {
        assertEquals(0.7f * 0.4f * 0.5f, MusicPolicy.effectiveVolume(0.7f, 0.4f, 0.5f), 0.0001f)
        assertEquals(0f, MusicPolicy.effectiveVolume(0.7f, 1f, 0f), 0f)
    }

    @Test
    fun theVolumeStaysInsideZeroAndOne() {
        assertEquals(1f, MusicPolicy.effectiveVolume(3f, 1f, 1f), 0f)
        assertEquals(0f, MusicPolicy.effectiveVolume(-1f, 1f, 1f), 0f)
    }

    @Test
    fun aCrossFadeKeepsTheTwoTracksFromBeingLoudTogether() {
        // The old track falls while the new one rises, both over the same time: they always add up to one.
        var outgoing = 1f
        var incoming = 0f
        repeat(10) {
            outgoing = Ramp.step(outgoing, 0f, 50f, MusicPolicy.CROSSFADE_MS)
            incoming = Ramp.step(incoming, 1f, 50f, MusicPolicy.CROSSFADE_MS)
            assertEquals(1f, outgoing + incoming, 0.0001f)
        }
        assertEquals(0f, outgoing, 0.0001f)
        assertEquals(1f, incoming, 0.0001f)
    }
}
