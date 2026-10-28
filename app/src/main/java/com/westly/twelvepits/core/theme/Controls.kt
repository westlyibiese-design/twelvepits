package com.westly.twelvepits.core.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Switch: emerald when on, grey when off. 48dp touch height. */
@Composable
fun GoldSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapSound = LocalTapSound.current
    val source = remember { MutableInteractionSource() }
    val thumbShift by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                role = Role.Switch,
            ) {
                tapSound()
                onCheckedChange(it)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(CircleShape)
                .background(if (checked) EmeraldDeep else ControlFill)
                .border(BorderStroke(1.5.dp, if (checked) Emerald else GoldLine), CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = 4.dp + thumbShift)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) Ivory else OffGrey),
            )
        }
    }
}

/** Gold slider from 0f to 1f. Tap or drag anywhere on the bar. 48dp touch height. */
@Composable
fun GoldSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
) {
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val thumbRadiusPx = with(LocalDensity.current) { 11.dp.toPx() }
    val trackHeightPx = with(LocalDensity.current) { 6.dp.toPx() }
    val shown = value.coerceIn(0f, 1f)

    fun fractionAt(x: Float): Float {
        val usable = (widthPx - 2f * thumbRadiusPx).coerceAtLeast(1f)
        return ((x - thumbRadiusPx) / usable).coerceIn(0f, 1f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnChange(fractionAt(offset.x))
                    currentOnFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { currentOnFinished() },
                    onDragCancel = { currentOnFinished() },
                ) { change, _ ->
                    change.consume()
                    currentOnChange(fractionAt(change.position.x))
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val centerY = size.height / 2f
            val startX = thumbRadiusPx
            val endX = size.width - thumbRadiusPx
            val thumbX = startX + (endX - startX) * shown
            drawLine(
                color = Gold.copy(alpha = 0.22f),
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Gold,
                start = Offset(startX, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round,
            )
            drawCircle(color = LightGold, radius = thumbRadiusPx, center = Offset(thumbX, centerY))
        }
    }
}

/** Segmented selector: the selected item is a gold gradient. Each item is at least 48dp high. */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ControlFill)
            .border(BorderStroke(1.dp, GoldLine), shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(
                        if (isSelected) {
                            Brush.verticalGradient(listOf(LightGold, DeepGold))
                        } else {
                            SolidColor(ControlFill)
                        },
                    )
                    .semantics { selected = isSelected }
                    .tapClickable(role = Role.RadioButton) { onSelected(index) }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = label,
                    style = TwelvePitsType.segment.copy(
                        color = if (isSelected) OnGold else Gold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/** Single-line text field in the same look as the other controls. */
@Composable
fun GoldTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TwelvePitsType.body.copy(fontSize = 18.sp),
        cursorBrush = SolidColor(Gold),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(ControlFill)
            .border(BorderStroke(1.dp, GoldLine), shape),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                innerTextField()
            }
        },
    )
}
