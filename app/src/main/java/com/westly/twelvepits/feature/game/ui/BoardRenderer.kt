package com.westly.twelvepits.feature.game.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.core.theme.BadgeBorder
import com.westly.twelvepits.core.theme.BadgeFill
import com.westly.twelvepits.core.theme.BadgeText
import com.westly.twelvepits.core.theme.BoardShadow
import com.westly.twelvepits.core.theme.BodyFont
import com.westly.twelvepits.core.theme.CaptureGlow
import com.westly.twelvepits.core.theme.GameDimens
import com.westly.twelvepits.core.theme.Ink
import com.westly.twelvepits.core.theme.PlusFloat
import com.westly.twelvepits.core.theme.RelayGlow
import com.westly.twelvepits.core.theme.StoreNameShadow
import com.westly.twelvepits.core.theme.StoreNameText
import com.westly.twelvepits.core.theme.SeedShadow
import com.westly.twelvepits.core.theme.TurnGlow
import com.westly.twelvepits.feature.game.engine.GameState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the table, the board, the seeds, the count badges, the glows and the moving seeds.
 * Everything that depends on the screen size is worked out once in the constructor, so the draw
 * function allocates nothing. Only the main thread uses it.
 */
internal class BoardRenderer(
    private val geometry: BoardGeometry,
    mapper: BoardMapper,
    private val board: BoardRect,
    private val boardImage: ImageBitmap,
    private val backgroundImage: ImageBitmap?,
    private val seedImage: ImageBitmap,
    private val screenWidth: Float,
    private val screenHeight: Float,
    density: Density,
    private val measurer: TextMeasurer,
    playerNames: List<String>,
    private val playerWins: List<Int> = emptyList(),
    private val winsLabel: String = "",
) {
    private val dp: Float = density.density
    private val boardW: Float = board.width

    private val seedLength: Float = geometry.seedLengthW * boardW
    private val seedWidth: Float = seedLength * geometry.seedAspectWOverH
    private val seedHalfW: Float = seedWidth / 2f
    private val seedHalfH: Float = seedLength / 2f
    private val seedShadowFilter = ColorFilter.tint(SeedShadow, BlendMode.SrcIn)
    private val shadowDx: Float = 1f * dp
    private val shadowDy: Float = 1.5f * dp

    private val pitCap: Int = geometry.maxSeedsPerPit
    private val storeCap: Int = geometry.maxSeedsPerStore
    private val pitSpread: Float = geometry.pitInnerBowlRadiusW * SeedLayout.PIT_SPREAD_OF_BOWL * boardW
    private val storeSpread: Float = geometry.storeBowlOpeningRadiusW * SeedLayout.STORE_SPREAD_OF_OPENING * boardW

    // Positions by engine pit (0 to 11) and by player (Player.ordinal), in screen pixels.
    private val pitX = FloatArray(GameState.PIT_COUNT)
    private val pitY = FloatArray(GameState.PIT_COUNT)
    private val pitBadgeY = FloatArray(GameState.PIT_COUNT)
    private val pitSeedX = FloatArray(GameState.PIT_COUNT * pitCap)
    private val pitSeedY = FloatArray(GameState.PIT_COUNT * pitCap)
    private val pitSeedRot = FloatArray(GameState.PIT_COUNT * pitCap)
    private val storeX = FloatArray(BoardDisplayState.PLAYER_COUNT)
    private val storeY = FloatArray(BoardDisplayState.PLAYER_COUNT)
    private val storeBadgeY = FloatArray(BoardDisplayState.PLAYER_COUNT)
    private val storeSeedX = FloatArray(BoardDisplayState.PLAYER_COUNT * storeCap)
    private val storeSeedY = FloatArray(BoardDisplayState.PLAYER_COUNT * storeCap)
    private val storeSeedRot = FloatArray(BoardDisplayState.PLAYER_COUNT * storeCap)

    // The small tilted group of seeds held in the hand.
    private val handOffsetX = FloatArray(HAND_SLOTS)
    private val handOffsetY = FloatArray(HAND_SLOTS)
    private val handRotation = FloatArray(HAND_SLOTS)
    private val handLiftPx: Float = HAND_LIFT_W * boardW
    private val hopArcPx: Float = HOP_ARC_W * boardW
    private val captureArcPx: Float = CAPTURE_ARC_W * boardW

    // Count badges.
    private val pitBadgeDiameter: Float = max(geometry.pitBadgeDiameterW * boardW, geometry.pitBadgeMinDiameterDp * dp)
    private val storeBadgeDiameter: Float = geometry.storeBadgeDiameterW * boardW
    private val handBadgeDiameter: Float = pitBadgeDiameter * HAND_BADGE_SCALE
    private val badgeBorder = Stroke(width = GameDimens.BadgeBorderWidth.value * dp)
    private val pitTextStyle = numberStyle(pitBadgeDiameter, density)
    private val storeTextStyle = numberStyle(storeBadgeDiameter, density)
    private val handTextStyle = numberStyle(handBadgeDiameter, density)
    private val plusTextStyle = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = with(density) { (storeBadgeDiameter * PLUS_TEXT_SCALE).toSp() },
        color = PlusFloat,
    )
    private val pitLayouts = arrayOfNulls<TextLayoutResult>(MAX_CACHED_NUMBER + 1)
    private val storeLayouts = arrayOfNulls<TextLayoutResult>(MAX_CACHED_NUMBER + 1)
    private val handLayouts = arrayOfNulls<TextLayoutResult>(MAX_CACHED_NUMBER + 1)
    private var plusLayout: TextLayoutResult? = null

    // Each player's name and score sit in the table margin at the end of the board, centred on that
    // player's own store, on the side of the board where that player's pits are. Indexed by Player.ordinal.
    private val nameMaxWidth: Int = (boardW * NAME_WIDTH_OF_BOARD).roundToInt().coerceAtLeast(1)
    private val nameLayouts = arrayOfNulls<TextLayoutResult>(BoardDisplayState.PLAYER_COUNT)
    private val scoreLayouts = arrayOfNulls<TextLayoutResult>(MAX_CACHED_NUMBER + 1)
    private val plateX = FloatArray(BoardDisplayState.PLAYER_COUNT)
    private val nameY = FloatArray(BoardDisplayState.PLAYER_COUNT)
    private val scoreY = FloatArray(BoardDisplayState.PLAYER_COUNT)
    private var scoreStyle: TextStyle = TextStyle.Default

    // Glow rings.
    private val pitRingRadius: Float = (geometry.pitOuterRingRadiusW + RING_MARGIN_W) * boardW
    private val storeRingRadius: Float = (geometry.storeOuterRadiusW + RING_MARGIN_W) * boardW
    private val ringStroke = Stroke(width = GameDimens.RingStroke.value * dp)
    private val glowStrokeWide = Stroke(width = GameDimens.GlowStrokeWide.value * dp)
    private val glowStrokeNarrow = Stroke(width = GameDimens.GlowStrokeNarrow.value * dp)
    private val shakeAmplitude: Float = SHAKE_W * boardW

    // Where the images are drawn.
    private val backgroundOffset: IntOffset
    private val backgroundSize: IntSize
    private val boardOffset = IntOffset(board.left.roundToInt(), board.top.roundToInt())
    private val boardSize = IntSize(board.width.roundToInt().coerceAtLeast(1), board.height.roundToInt().coerceAtLeast(1))
    private val shadowOffsetPx: Float = GameDimens.BoardShadowOffset.value * dp

    init {
        for (pit in 0 until GameState.PIT_COUNT) {
            val slot = mapper.slotOfPit(pit)
            pitX[pit] = board.x(geometry.slotCx(slot))
            pitY[pit] = board.y(geometry.slotCy(slot))
            pitBadgeY[pit] = board.y(geometry.slotBadgeCy(slot))
            val key = SeedLayout.pitKey(slot)
            for (i in 0 until pitCap) {
                val at = pit * pitCap + i
                pitSeedX[at] = SeedLayout.offsetX(i, key, pitSpread, pitCap)
                pitSeedY[at] = SeedLayout.offsetY(i, key, pitSpread, pitCap)
                pitSeedRot[at] = SeedLayout.rotationDegrees(i, key)
            }
        }
        for (owner in Player.entries) {
            val side = mapper.storeSide(owner)
            val o = owner.ordinal
            storeX[o] = board.x(geometry.storeCx(side))
            storeY[o] = board.y(geometry.storeCy(side))
            storeBadgeY[o] = board.y(geometry.storeBadgeCy)
            val key = SeedLayout.storeKey(side)
            for (i in 0 until storeCap) {
                val at = o * storeCap + i
                storeSeedX[at] = SeedLayout.offsetX(i, key, storeSpread, storeCap)
                storeSeedY[at] = SeedLayout.offsetY(i, key, storeSpread, storeCap)
                storeSeedRot[at] = SeedLayout.rotationDegrees(i, key)
            }
        }
        val handSpread = HAND_SPREAD_W * boardW
        for (i in 0 until HAND_SLOTS) {
            handOffsetX[i] = SeedLayout.offsetX(i, HAND_KEY, handSpread, HAND_SLOTS)
            handOffsetY[i] = SeedLayout.offsetY(i, HAND_KEY, handSpread, HAND_SLOTS)
            handRotation[i] = SeedLayout.rotationDegrees(i, HAND_KEY) + HAND_TILT_DEGREES
        }
        val boardBottom = board.top + board.height
        val boardCenterY = board.top + board.height / 2f
        // Both players use the same font sizes, kept inside the smaller of the two margins.
        val smallestBand = max(1f, min(board.top, screenHeight - boardBottom))
        val nameFontPx = max(min(NAME_FONT_W * boardW, smallestBand * NAME_BAND_SHARE), NAME_MIN_DP * dp)
        val scoreFontPx = max(min(SCORE_FONT_W * boardW, smallestBand * SCORE_BAND_SHARE), SCORE_MIN_DP * dp)
        val plateNameStyle = nameStyle(nameFontPx, density)
        scoreStyle = scoreTextStyle(scoreFontPx, density)
        val scoreHeight = measurer.measure("0", scoreStyle).size.height
        for (o in 0 until BoardDisplayState.PLAYER_COUNT) {
            // The plate goes in the margin on the side of the board where this player's pits are.
            val firstPit = o * GameState.PITS_PER_SIDE
            var pitRowY = 0f
            for (pit in firstPit until firstPit + GameState.PITS_PER_SIDE) pitRowY += pitY[pit]
            pitRowY /= GameState.PITS_PER_SIDE
            val above = pitRowY < boardCenterY
            val bandTop = if (above) 0f else boardBottom
            val bandHeight = max(1f, if (above) board.top else screenHeight - boardBottom)
            val name = playerNames.getOrNull(o)?.trim().orEmpty()
            val nameLayout = if (name.isEmpty()) {
                null
            } else {
                measurer.measure(
                    text = name,
                    style = plateNameStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = nameMaxWidth),
                )
            }
            nameLayouts[o] = nameLayout
            plateX[o] = storeX[o]
            val nameHeight = nameLayout?.size?.height ?: 0
            val gap = if (nameLayout == null) 0f else PLATE_GAP_DP * dp
            val plateHeight = nameHeight + gap + scoreHeight
            val top = (bandTop + (bandHeight - plateHeight) / 2f).coerceIn(0f, max(0f, screenHeight - plateHeight))
            nameY[o] = top
            scoreY[o] = top + nameHeight + gap
        }
        val image = backgroundImage
        if (image != null) {
            val scale = max(screenWidth / image.width, screenHeight / image.height)
            val w = (image.width * scale).roundToInt()
            val h = (image.height * scale).roundToInt()
            backgroundSize = IntSize(w, h)
            backgroundOffset = IntOffset(((screenWidth - w) / 2f).roundToInt(), ((screenHeight - h) / 2f).roundToInt())
        } else {
            backgroundSize = IntSize.Zero
            backgroundOffset = IntOffset.Zero
        }
    }

    /** Draws one frame. [pulse] is the gentle 0.7 to 1.0 pulse of the turn glow. */
    fun DrawScope.drawScene(display: BoardDisplayState, pulse: Float) {
        drawTable()
        drawPitSeeds(display)
        drawStoreSeeds(display)
        drawGlows(display, pulse)
        drawBadges(display)
        drawPlayerNames(display)
        drawCaptureFly(display)
        drawHand(display)
        drawHop(display)
        drawFloaters(display)
    }

    private fun DrawScope.drawTable() {
        drawRect(Ink)
        val background = backgroundImage
        if (background != null) {
            drawImage(
                image = background,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(background.width, background.height),
                dstOffset = backgroundOffset,
                dstSize = backgroundSize,
                filterQuality = FilterQuality.Medium,
            )
        }
        // Soft shadow under the board, built from a few stacked rounded rectangles.
        for (layer in 0 until SHADOW_LAYERS) {
            val grow = layer * 4f * dp
            drawRoundRect(
                color = BoardShadow.copy(alpha = BoardShadow.alpha / (SHADOW_LAYERS * 1.5f)),
                topLeft = Offset(board.left - grow, board.top - grow + shadowOffsetPx),
                size = Size(board.width + 2f * grow, board.height + 2f * grow),
                cornerRadius = CornerRadius(grow + 2f * dp),
            )
        }
        drawImage(
            image = boardImage,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(boardImage.width, boardImage.height),
            dstOffset = boardOffset,
            dstSize = boardSize,
            filterQuality = FilterQuality.Medium,
        )
    }

    private fun DrawScope.drawSeed(cx: Float, cy: Float, degrees: Float, alpha: Float) {
        rotate(degrees, Offset(cx + shadowDx, cy + shadowDy)) {
            drawImage(
                image = seedImage,
                topLeft = Offset(cx + shadowDx - seedHalfW, cy + shadowDy - seedHalfH),
                alpha = alpha,
                colorFilter = seedShadowFilter,
            )
        }
        rotate(degrees, Offset(cx, cy)) {
            drawImage(
                image = seedImage,
                topLeft = Offset(cx - seedHalfW, cy - seedHalfH),
                alpha = alpha,
            )
        }
    }

    private fun shakeOffset(display: BoardDisplayState, pit: Int): Float {
        if (display.shakePit != pit) return 0f
        val progress = display.shakeProgress.coerceIn(0f, 1f)
        return sin(progress * SHAKE_WAVES * TWO_PI) * (1f - progress) * shakeAmplitude
    }

    private fun DrawScope.drawPitSeeds(display: BoardDisplayState) {
        for (pit in 0 until GameState.PIT_COUNT) {
            val shown = min(display.pits[pit], pitCap)
            if (shown <= 0) continue
            val shake = shakeOffset(display, pit)
            val base = pit * pitCap
            for (i in 0 until shown) {
                drawSeed(pitX[pit] + pitSeedX[base + i] + shake, pitY[pit] + pitSeedY[base + i], pitSeedRot[base + i], 1f)
            }
        }
    }

    private fun DrawScope.drawStoreSeeds(display: BoardDisplayState) {
        for (o in 0 until BoardDisplayState.PLAYER_COUNT) {
            val shown = min(display.stores[o], storeCap)
            val base = o * storeCap
            for (i in 0 until shown) {
                drawSeed(storeX[o] + storeSeedX[base + i], storeY[o] + storeSeedY[base + i], storeSeedRot[base + i], 1f)
            }
        }
    }

    private fun DrawScope.drawGlows(display: BoardDisplayState, pulse: Float) {
        val relayPit = display.relayGlowPit
        if (relayPit in 0 until GameState.PIT_COUNT) {
            drawSoftGlow(pitX[relayPit], pitY[relayPit], pitRingRadius, RelayGlow, display.relayGlow)
        }
        val capturePit = display.captureGlowPit
        if (capturePit in 0 until GameState.PIT_COUNT) {
            drawSoftGlow(pitX[capturePit], pitY[capturePit], pitRingRadius, CaptureGlow, display.captureGlow)
        }
        val pitPlayer = display.pitGlowPlayer
        if (pitPlayer != null) {
            val first = if (pitPlayer == Player.ONE) 0 else GameState.PITS_PER_SIDE
            for (pit in first until first + GameState.PITS_PER_SIDE) {
                if (display.pits[pit] > 0) drawTurnRing(pitX[pit], pitY[pit], pitRingRadius, pulse)
            }
        }
        val storePlayer = display.storeGlowPlayer
        if (storePlayer != null) {
            drawTurnRing(storeX[storePlayer.ordinal], storeY[storePlayer.ordinal], storeRingRadius, pulse)
        }
    }

    /** A soft glow made of a few stacked circles (no gradient is created while drawing). */
    private fun DrawScope.drawSoftGlow(cx: Float, cy: Float, radius: Float, color: Color, strength: Float) {
        val s = strength.coerceIn(0f, 1f)
        val center = Offset(cx, cy)
        drawCircle(color.copy(alpha = 0.10f * s), radius * 1.25f, center)
        drawCircle(color.copy(alpha = 0.16f * s), radius * 1.00f, center)
        drawCircle(color.copy(alpha = 0.22f * s), radius * 0.75f, center)
    }

    private fun DrawScope.drawTurnRing(cx: Float, cy: Float, radius: Float, pulse: Float) {
        val center = Offset(cx, cy)
        drawCircle(TurnGlow.copy(alpha = 0.06f * pulse), radius, center)
        drawCircle(TurnGlow.copy(alpha = 0.10f * pulse), radius, center, style = glowStrokeWide)
        drawCircle(TurnGlow.copy(alpha = 0.18f * pulse), radius, center, style = glowStrokeNarrow)
        drawCircle(TurnGlow.copy(alpha = 0.75f + 0.25f * pulse), radius, center, style = ringStroke)
    }

    private fun DrawScope.drawBadges(display: BoardDisplayState) {
        for (pit in 0 until GameState.PIT_COUNT) {
            val shake = shakeOffset(display, pit)
            val count = display.pits[pit]
            drawBadge(pitX[pit] + shake, pitBadgeY[pit], pitBadgeDiameter, pitLayout(count), 1f)
        }
        for (o in 0 until BoardDisplayState.PLAYER_COUNT) {
            drawBadge(storeX[o], storeBadgeY[o], storeBadgeDiameter, storeLayout(display.stores[o]), 1f)
        }
    }

    /** Each player's name and score, centred on that player's store, in the table margin. */
    private fun DrawScope.drawPlayerNames(display: BoardDisplayState) {
        for (o in 0 until BoardDisplayState.PLAYER_COUNT) {
            val name = nameLayouts[o]
            if (name != null) {
                val x = (plateX[o] - name.size.width / 2f).coerceIn(0f, max(0f, screenWidth - name.size.width))
                drawText(textLayoutResult = name, topLeft = Offset(x, nameY[o]))
            }
            val score = scoreLayout(playerWins.getOrNull(o) ?: 0)
            val scoreX = (plateX[o] - score.size.width / 2f).coerceIn(0f, max(0f, screenWidth - score.size.width))
            drawText(textLayoutResult = score, topLeft = Offset(scoreX, scoreY[o]))
        }
    }

    private fun DrawScope.drawBadge(cx: Float, cy: Float, diameter: Float, text: TextLayoutResult, alpha: Float) {
        val center = Offset(cx, cy)
        val radius = diameter / 2f
        drawCircle(BadgeFill, radius, center, alpha = alpha)
        drawCircle(BadgeBorder, radius - badgeBorder.width / 2f, center, alpha = alpha, style = badgeBorder)
        drawText(
            textLayoutResult = text,
            topLeft = Offset(cx - text.size.width / 2f, cy - text.size.height / 2f),
            alpha = alpha,
        )
    }

    /** The 4 captured seeds flying from the pit into the owner's store. */
    private fun DrawScope.drawCaptureFly(display: BoardDisplayState) {
        if (!display.captureFlyActive) return
        val pit = display.captureFlyPit
        val owner = display.captureFlyOwner
        val t = ease(display.captureFlyProgress)
        val arc = captureArcPx * 4f * t * (1f - t)
        for (i in 0 until GameState.CAPTURE_SIZE) {
            val fromAt = pit * pitCap + i
            val toIndex = min(display.captureFlyFromStore + i, storeCap - 1)
            val toAt = owner * storeCap + toIndex
            val fromX = pitX[pit] + pitSeedX[fromAt]
            val fromY = pitY[pit] + pitSeedY[fromAt]
            val toX = storeX[owner] + storeSeedX[toAt]
            val toY = storeY[owner] + storeSeedY[toAt]
            drawSeed(
                lerp(fromX, toX, t),
                lerp(fromY, toY, t) - arc,
                lerp(pitSeedRot[fromAt], storeSeedRot[toAt], t),
                1f,
            )
        }
    }

    /** The small tilted group of seeds held just above a pit, with the number still in hand. */
    private fun DrawScope.drawHand(display: BoardDisplayState) {
        if (!display.handActive) return
        val fromPit = if (display.hopActive) display.hopFrom else display.handPit
        val toPit = if (display.hopActive) display.hopTo else display.handPit
        val t = if (display.hopActive) ease(display.hopProgress) else 0f
        val lift = handLiftPx * ease(display.handLift)
        val gx = lerp(pitX[fromPit], pitX[toPit], t)
        val gy = lerp(pitY[fromPit], pitY[toPit], t) - lift
        val inGroup = min(display.handCount - (if (display.hopActive) 1 else 0), HAND_SLOTS)
        for (i in 0 until inGroup) {
            drawSeed(gx + handOffsetX[i], gy + handOffsetY[i], handRotation[i], display.handAlpha)
        }
        if (display.handCount > 0) {
            drawBadge(
                gx + HAND_BADGE_DX_W * boardW,
                gy - HAND_BADGE_DY_W * boardW,
                handBadgeDiameter,
                handLayout(display.handCount),
                display.handAlpha,
            )
        }
    }

    /** The one seed that hops from the hand to the next pit along a small arc. */
    private fun DrawScope.drawHop(display: BoardDisplayState) {
        if (!display.hopActive) return
        val from = display.hopFrom
        val to = display.hopTo
        val t = ease(display.hopProgress)
        val startX = pitX[from]
        val startY = pitY[from] - handLiftPx * ease(display.handLift)
        val index = min(display.pits[to], pitCap - 1)
        val endAt = to * pitCap + index
        val endX = pitX[to] + pitSeedX[endAt]
        val endY = pitY[to] + pitSeedY[endAt]
        val arc = hopArcPx * 4f * display.hopProgress * (1f - display.hopProgress)
        drawSeed(
            lerp(startX, endX, t),
            lerp(startY, endY, t) - arc,
            lerp(handRotation[0], pitSeedRot[endAt], t),
            1f,
        )
    }

    private fun DrawScope.drawFloaters(display: BoardDisplayState) {
        for (i in 0 until BoardDisplayState.MAX_FLOATERS) {
            val progress = display.floaterProgress[i]
            if (progress == BoardDisplayState.UNUSED) continue
            val owner = display.floaterOwner[i]
            val layout = plusLayout ?: measurer.measure("+4", plusTextStyle).also { plusLayout = it }
            val rise = (PLUS_START_W + PLUS_RISE_W * progress) * boardW
            val alpha = (1f - progress * progress).coerceIn(0f, 1f)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(storeX[owner] - layout.size.width / 2f, storeY[owner] - rise - layout.size.height / 2f),
                alpha = alpha,
            )
        }
    }

    private fun pitLayout(count: Int): TextLayoutResult = cachedLayout(pitLayouts, count, pitTextStyle)

    private fun storeLayout(count: Int): TextLayoutResult = cachedLayout(storeLayouts, count, storeTextStyle)

    private fun handLayout(count: Int): TextLayoutResult = cachedLayout(handLayouts, count, handTextStyle)

    private fun scoreLayout(count: Int): TextLayoutResult {
        val text = "$winsLabel: $count"
        if (count < 0 || count > MAX_CACHED_NUMBER) return measurer.measure(text, scoreStyle)
        return scoreLayouts[count] ?: measurer.measure(text, scoreStyle).also { scoreLayouts[count] = it }
    }

    private fun cachedLayout(cache: Array<TextLayoutResult?>, count: Int, style: TextStyle): TextLayoutResult {
        if (count < 0 || count > MAX_CACHED_NUMBER) return measurer.measure(count.toString(), style)
        return cache[count] ?: measurer.measure(count.toString(), style).also { cache[count] = it }
    }

    private fun nameStyle(fontPx: Float, density: Density): TextStyle = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = with(density) { fontPx.toSp() },
        color = StoreNameText,
        textAlign = TextAlign.Center,
        shadow = Shadow(color = StoreNameShadow, offset = Offset(0f, 2f * dp), blurRadius = 4f * dp),
    )

    private fun scoreTextStyle(fontPx: Float, density: Density): TextStyle = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = with(density) { fontPx.toSp() },
        color = BadgeText,
        textAlign = TextAlign.Center,
        shadow = Shadow(color = StoreNameShadow, offset = Offset(0f, 2f * dp), blurRadius = 4f * dp),
    )

    private fun numberStyle(diameter: Float, density: Density): TextStyle = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = with(density) { (diameter * NUMBER_TEXT_SCALE).toSp() },
        color = BadgeText,
    )

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    /** Smooth start and end (smoothstep). */
    private fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private companion object {
        const val TWO_PI = 6.2831855f
        const val MAX_CACHED_NUMBER = 64

        // The badge number is about 0.536 of the badge diameter (0.0145 / 0.027 of the board width for pits).
        const val NUMBER_TEXT_SCALE = 0.536f
        const val PLUS_TEXT_SCALE = 0.70f
        // Player names and scores in the table margin: a share of the board width, kept inside the margin band.
        const val NAME_FONT_W = 0.022f
        const val NAME_BAND_SHARE = 0.28f
        const val NAME_MIN_DP = 10f
        const val NAME_WIDTH_OF_BOARD = 0.22f
        const val SCORE_FONT_W = 0.040f
        const val SCORE_BAND_SHARE = 0.40f
        const val SCORE_MIN_DP = 14f
        const val PLATE_GAP_DP = 2f

        const val HAND_SLOTS = 5
        const val HAND_KEY = 30
        const val HAND_TILT_DEGREES = 18f
        const val HAND_SPREAD_W = 0.012f
        const val HAND_LIFT_W = 0.045f
        const val HAND_BADGE_SCALE = 0.9f
        const val HAND_BADGE_DX_W = 0.022f
        const val HAND_BADGE_DY_W = 0.020f

        const val HOP_ARC_W = 0.030f
        const val CAPTURE_ARC_W = 0.050f
        const val RING_MARGIN_W = 0.0025f
        const val SHAKE_W = 0.004f
        const val SHAKE_WAVES = 3f
        const val SHADOW_LAYERS = 4

        const val PLUS_START_W = 0.020f
        const val PLUS_RISE_W = 0.070f
    }
}
