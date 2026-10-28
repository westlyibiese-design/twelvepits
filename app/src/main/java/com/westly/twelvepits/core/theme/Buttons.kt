package com.westly.twelvepits.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Called on every tap of a menu button. MainActivity supplies the real sound; the default does nothing. */
val LocalTapSound = staticCompositionLocalOf<() -> Unit> { {} }

val ButtonShape = RoundedCornerShape(12.dp)
private val MinTouch = 48.dp

/** Click handling shared by every button: tap sound plus a small pressed fade. */
@Composable
fun Modifier.tapClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val tapSound = LocalTapSound.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .graphicsLayer { alpha = if (pressed) 0.8f else 1f }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
        ) {
            tapSound()
            onClick()
        }
}

/** Large gold gradient button: the one main action of a screen. */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(ButtonShape)
            .background(Brush.verticalGradient(listOf(LightGold, DeepGold)))
            .border(BorderStroke(1.dp, LightGold), ButtonShape)
            .tapClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TwelvePitsType.primaryButton.copy(textAlign = TextAlign.Center),
        )
    }
}

/** Outlined secondary button: gold 1.5dp border, dark fill. */
@Composable
fun OutlinedGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = MinTouch)
            .clip(ButtonShape)
            .background(ControlFill)
            .border(BorderStroke(1.5.dp, Gold), ButtonShape)
            .tapClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TwelvePitsType.secondaryButton.copy(textAlign = TextAlign.Center),
        )
    }
}

/** Back button: 44dp square with 12dp corners, inside a 48dp touch area. */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(MinTouch)
            .semantics { this.contentDescription = contentDescription }
            .tapClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(ButtonShape)
                .background(ControlFill)
                .border(BorderStroke(1.dp, GoldLine), ButtonShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val w = size.width
                val h = size.height
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.65f, h * 0.15f)
                    lineTo(w * 0.30f, h * 0.50f)
                    lineTo(w * 0.65f, h * 0.85f)
                }
                drawPath(path, color = Gold, style = stroke)
            }
        }
    }
}
