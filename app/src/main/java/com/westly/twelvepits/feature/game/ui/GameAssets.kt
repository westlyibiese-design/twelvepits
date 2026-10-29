package com.westly.twelvepits.feature.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.annotation.DrawableRes
import com.westly.twelvepits.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** What can go wrong while the board is loaded. The game screen shows a clear message for each. */
internal enum class AssetsFailure {
    GEOMETRY_MISSING,
    GEOMETRY_INVALID,
    IMAGE_MISSING,
}

/** The images and the geometry of the game screen, each decoded once. */
internal class GameAssets(
    /** The table. Optional: without it the table is plain dark. */
    val background: ImageBitmap?,
    val board: ImageBitmap,
    val seedSource: Bitmap,
    val geometry: BoardGeometry,
)

internal sealed interface AssetsState {
    data object Loading : AssetsState

    class Ready(val assets: GameAssets) : AssetsState

    class Failed(val failure: AssetsFailure, val detail: String) : AssetsState
}

private const val GEOMETRY_FILE = "board_geometry.json"

/** Reads board_geometry.json and decodes the three images off the main thread. Never throws. */
internal suspend fun loadGameAssets(context: Context): AssetsState = withContext(Dispatchers.IO) {
    val geometryText = try {
        context.assets.open(GEOMETRY_FILE).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        return@withContext AssetsState.Failed(AssetsFailure.GEOMETRY_MISSING, GEOMETRY_FILE)
    }
    val geometry = try {
        BoardGeometry.parse(geometryText)
    } catch (e: BoardGeometryException) {
        return@withContext AssetsState.Failed(AssetsFailure.GEOMETRY_INVALID, e.message ?: GEOMETRY_FILE)
    }
    val board = decode(context, R.drawable.board)
        ?: return@withContext AssetsState.Failed(AssetsFailure.IMAGE_MISSING, "board.jpg")
    val seed = decode(context, R.drawable.seed)
        ?: return@withContext AssetsState.Failed(AssetsFailure.IMAGE_MISSING, "seed.png")
    val background = decode(context, R.drawable.game_background)
    AssetsState.Ready(
        GameAssets(
            background = background?.asImageBitmap(),
            board = board.asImageBitmap(),
            seedSource = seed,
            geometry = geometry,
        ),
    )
}

private fun decode(context: Context, @DrawableRes id: Int): Bitmap? {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return try {
        BitmapFactory.decodeResource(context.resources, id, options)
    } catch (e: RuntimeException) {
        null
    }
}

/**
 * The seed picture scaled once to the size it is drawn at. It is halved step by step first so the
 * small seeds stay smooth instead of jagged.
 */
internal fun scaleSeed(source: Bitmap, targetWidth: Int, targetHeight: Int): ImageBitmap {
    val width = targetWidth.coerceAtLeast(1)
    val height = targetHeight.coerceAtLeast(1)
    var current = source
    var w = source.width
    var h = source.height
    while (w / 2 >= width && h / 2 >= height) {
        w /= 2
        h /= 2
        current = Bitmap.createScaledBitmap(current, w, h, true)
    }
    return Bitmap.createScaledBitmap(current, width, height, true).asImageBitmap()
}
