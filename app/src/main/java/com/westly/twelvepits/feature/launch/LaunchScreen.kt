package com.westly.twelvepits.feature.launch

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.twelvepits.R
import com.westly.twelvepits.core.audio.Sfx
import com.westly.twelvepits.core.audio.SoundManager
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.core.theme.Ink
import com.westly.twelvepits.core.theme.LaunchBarEnd
import com.westly.twelvepits.core.theme.LaunchBarStart
import com.westly.twelvepits.core.theme.LaunchBarTrack
import com.westly.twelvepits.core.theme.LaunchBarMiddle
import com.westly.twelvepits.core.theme.TwelvePitsType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val SPARKLE_COUNT = 24
private const val SHIMMER_WAIT_MS = 1500L
private const val NOT_STARTED = -1f

/**
 * The launch screen: emblem artwork, rays, glow, sheen, sparkles, 44 seeds dropping in time with a loading
 * bar that shows real preparation, a flash, then a cross-fade to the main menu (done by the navigation).
 * A tap after one second skips. With the phone's animations turned off it leaves at once.
 */
@Composable
fun LaunchScreen(
    settings: SettingsStore,
    sound: SoundManager,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val currentOnFinished by rememberUpdatedState(onFinished)
    val finished = remember { AtomicBoolean(false) }
    val finish: () -> Unit = {
        if (finished.compareAndSet(false, true)) currentOnFinished()
    }

    val animationsOff = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (animationsOff) {
        LaunchedEffect(Unit) { finish() }
        Box(modifier = Modifier.fillMaxSize().background(Ink))
        return
    }

    var elapsedMs by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    var doneSteps by remember { mutableStateOf(emptySet<PrepStep>()) }

    LaunchedEffect(Unit) {
        prepareApp(context, settings, sound) { step -> doneSteps = doneSteps + step }
    }
    LaunchedEffect(Unit) {
        // The shimmer plays when the emblem appears, as soon as the sound is loaded.
        withTimeoutOrNull(SHIMMER_WAIT_MS) { sound.soundsReady.first { it } }
        sound.playSfx(Sfx.LAUNCH_SHIMMER)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (elapsedMs >= LaunchTimeline.SKIP_AFTER_MS) finish()
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val space = remember(widthPx, heightPx) { ImageSpace(widthPx, heightPx) }
        val sparkles = remember(widthPx, heightPx, density) {
            Sparkles(SPARKLE_COUNT, widthPx, heightPx, density, seed = 11)
        }
        val dropStarts = remember { FloatArray(LaunchTimeline.SEED_COUNT) { NOT_STARTED } }
        val seedTurns = remember {
            FloatArray(LaunchTimeline.SEED_COUNT) { Random(it * 31 + 5).nextFloat() * 70f - 35f }
        }
        var flashStartMs by remember { mutableFloatStateOf(NOT_STARTED) }

        // One loop drives the whole animation: time, bar, seeds, sparkles, flash and the hand-over to the menu.
        LaunchedEffect(sparkles) {
            var startNanos = -1L
            var lastNanos = 0L
            var seedsShown = 0
            while (true) {
                val now = withFrameNanos { it }
                if (startNanos < 0L) {
                    startNanos = now
                    lastNanos = now
                }
                val ms = (now - startNanos) / 1_000_000f
                sparkles.step(((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f))
                lastNanos = now

                val shown = LaunchTimeline.shownProgress(ms, doneSteps)
                progress = shown
                val seedsNow = LaunchTimeline.seedsShown(shown)
                while (seedsShown < seedsNow) {
                    dropStarts[seedsShown] = ms
                    seedsShown += 1
                }
                if (flashStartMs < 0f && shown >= 0.999f) flashStartMs = ms
                elapsedMs = ms

                if (flashStartMs >= 0f) {
                    val flash = LaunchTimeline.flashProgress(ms, flashStartMs)
                    if (flash >= LaunchTimeline.NAVIGATE_AT_FLASH) finish()
                    if (flash >= 1f) break
                }
            }
        }

        // Emblem: enters over 1.4 s (starts bigger and dark, ends sharp), then breathes slowly.
        Image(
            painter = painterResource(R.drawable.splash_emblem),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val enter = (elapsedMs / LaunchTimeline.ENTER_MS).coerceIn(0f, 1f)
                    val settle = 1f - (1f - enter) * (1f - enter) * (1f - enter)
                    val breathe = if (enter >= 1f) {
                        0.015f * (0.5f - 0.5f * cos(2f * PI.toFloat() * elapsedMs / 6000f))
                    } else {
                        0f
                    }
                    val grow = 1.12f - 0.12f * settle + breathe
                    scaleX = grow
                    scaleY = grow
                    alpha = settle
                },
            contentScale = ContentScale.Crop,
        )

        // Rays, glow, sheen, seeds, sparkles and the final flash are all drawn here.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ms = elapsedMs
            val enter = (ms / LaunchTimeline.ENTER_MS).coerceIn(0f, 1f)
            val pulse = 0.5f - 0.5f * cos(2f * PI.toFloat() * ms / 4500f)
            drawRays(space, rotationDegrees = ms / 1000f * 3f, opacity = 0.2f * enter)
            drawGlow(space, pulse = pulse, opacity = enter)
            drawSheen(space, progress = ((ms - 1200f) / 1200f).coerceIn(0f, 1f))
            drawLaunchSeeds(space, dropStarts, ms, seedTurns)
            sparkles.draw(this, opacity = enter)
            if (flashStartMs >= 0f) {
                drawFlash(space, LaunchTimeline.flashProgress(ms, flashStartMs))
            }
        }

        // Logo: 72% of the width, rises in at about 0.7 s.
        val logoWidth = minOf(maxWidth * 0.72f, 520.dp)
        Image(
            painter = painterResource(R.drawable.title_logo),
            contentDescription = stringResource(R.string.launch_logo_description),
            modifier = Modifier
                .offset(x = (maxWidth - logoWidth) / 2f, y = maxHeight * 0.735f)
                .width(logoWidth)
                .graphicsLayer {
                    val rise = ((elapsedMs - LaunchTimeline.LOGO_START_MS) / LaunchTimeline.LOGO_RISE_MS)
                        .coerceIn(0f, 1f)
                    alpha = rise
                    translationY = (1f - rise) * widthPx * 0.015f
                },
            contentScale = ContentScale.FillWidth,
        )

        // Loading bar at about 89.5% of the height.
        val barWidth = minOf(maxWidth * 0.44f, 320.dp)
        val percent by remember { derivedStateOf { LaunchTimeline.percent(progress) } }
        val ready by remember { derivedStateOf { progress >= 0.999f } }
        Column(
            modifier = Modifier
                .offset(x = (maxWidth - barWidth) / 2f, y = maxHeight * 0.895f)
                .width(barWidth)
                .graphicsLayer {
                    alpha = ((elapsedMs - LaunchTimeline.LOADER_START_MS) / 600f).coerceIn(0f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LaunchBarTrack)
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(LaunchBarStart, LaunchBarMiddle, LaunchBarEnd),
                            ),
                            size = androidx.compose.ui.geometry.Size(size.width * progress, size.height),
                        )
                    },
            )
            BasicText(
                text = if (ready) {
                    stringResource(R.string.launch_ready)
                } else {
                    stringResource(R.string.launch_loading, percent)
                },
                style = TwelvePitsType.small.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        BasicText(
            text = stringResource(R.string.publisher),
            style = TwelvePitsType.small.copy(textAlign = TextAlign.Center),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 12.dp),
        )
    }
}
