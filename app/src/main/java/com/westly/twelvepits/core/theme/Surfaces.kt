package com.westly.twelvepits.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.westly.twelvepits.R

/** Widest the content of any upright screen gets, so tablets and wide windows still look right. */
val MaxContentWidth = 480.dp

/**
 * Standard upright screen: plain wood background with a dark scrim, a back button and a title.
 * Content is padded by the system bars and the keyboard and centred up to [MaxContentWidth].
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        Image(
            painter = painterResource(R.drawable.menu_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().background(Scrim))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onClick = onBack, contentDescription = stringResource(R.string.back))
                    Spacer(modifier = Modifier.width(12.dp))
                    BasicText(
                        text = title,
                        style = TwelvePitsType.screenTitle,
                        maxLines = 2,
                    )
                }
                content()
            }
        }
    }
}

/** Card with a title, a subtitle, a leading icon and a trailing icon. A disabled card is greyed and does nothing. */
@Composable
fun MenuCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 84.dp)
        .alpha(if (enabled) 1f else 0.5f)
        .clip(shape)
        .background(ControlFill.copy(alpha = 0.88f))
        .border(BorderStroke(1.dp, GoldLine), shape)
    val clickable = if (enabled) {
        base.tapClickable(onClick = onClick)
    } else {
        base.semantics { disabled() }
    }
    Row(
        modifier = clickable.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(text = title, style = TwelvePitsType.cardTitle)
            BasicText(text = subtitle, style = TwelvePitsType.label)
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
}

/** Dialog in the game's look. Pass [dismissText] to show a second button. */
@Composable
fun GameDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Walnut)
                .border(BorderStroke(1.5.dp, Gold), shape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(text = title, style = TwelvePitsType.sectionTitle)
            BasicText(text = message, style = TwelvePitsType.body)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dismissText != null) {
                    OutlinedGoldButton(
                        text = dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedGoldButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
