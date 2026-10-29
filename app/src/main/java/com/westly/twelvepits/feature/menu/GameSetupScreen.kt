package com.westly.twelvepits.feature.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.core.storage.SetupChoices
import com.westly.twelvepits.core.theme.ControlFill
import com.westly.twelvepits.core.theme.Gold
import com.westly.twelvepits.core.theme.GoldButton
import com.westly.twelvepits.core.theme.GoldLine
import com.westly.twelvepits.core.theme.GoldTextField
import com.westly.twelvepits.core.theme.ScreenScaffold
import com.westly.twelvepits.core.theme.SegmentedSelector
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.drawableRes
import com.westly.twelvepits.core.theme.labelRes
import com.westly.twelvepits.core.theme.tapClickable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun GameSetupScreen(
    kind: SetupKind,
    settings: SettingsStore,
    onBack: () -> Unit,
    onStart: (GameConfig) -> Unit,
) {
    val choices by produceState<SetupChoices?>(initialValue = null, settings) {
        value = settings.setupChoices.first()
    }
    ScreenScaffold(title = stringResource(R.string.setup_title), onBack = onBack) {
        val loaded = choices
        if (loaded != null) {
            when (kind) {
                SetupKind.COMPUTER -> ComputerSetupForm(loaded, settings, onStart)
                SetupKind.TWO_PLAYERS -> TwoPlayerSetupForm(loaded, settings, onStart)
            }
        }
    }
}

@Composable
private fun ColumnScope.ComputerSetupForm(
    choices: SetupChoices,
    settings: SettingsStore,
    onStart: (GameConfig) -> Unit,
) {
    val defaultName = stringResource(R.string.default_name_you)
    val computerName = stringResource(R.string.computer_name)
    var name by rememberSaveable { mutableStateOf(limitTypedName(choices.computerPlayerName ?: defaultName)) }
    var avatar by rememberSaveable { mutableStateOf(selectableOrDefault(choices.computerPlayerAvatar, AvatarId.KING)) }
    var difficulty by rememberSaveable { mutableStateOf(choices.difficulty) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FormLayout(
        startEnabled = !starting,
        onStart = {
            starting = true
            val chosenName = finalName(name, defaultName)
            val config = computerGameConfig(chosenName, avatar, difficulty, computerName)
            scope.launch {
                settings.saveComputerSetup(chosenName, avatar, difficulty)
                onStart(config)
                starting = false
            }
        },
    ) {
        FieldLabel(stringResource(R.string.setup_your_name))
        GoldTextField(value = name, onValueChange = { name = limitTypedName(it) })
        FieldLabel(stringResource(R.string.setup_avatar))
        AvatarPicker(current = avatar, taken = null, onSelect = { avatar = it })
        FieldLabel(stringResource(R.string.setup_difficulty))
        val options = listOf(
            stringResource(R.string.difficulty_easy),
            stringResource(R.string.difficulty_normal),
            stringResource(R.string.difficulty_hard),
        )
        SegmentedSelector(
            options = options,
            selectedIndex = Difficulty.entries.indexOf(difficulty),
            onSelected = { difficulty = Difficulty.entries[it] },
        )
    }
}

@Composable
private fun ColumnScope.TwoPlayerSetupForm(
    choices: SetupChoices,
    settings: SettingsStore,
    onStart: (GameConfig) -> Unit,
) {
    val defaultOne = stringResource(R.string.default_name_player1)
    val defaultTwo = stringResource(R.string.default_name_player2)
    val savedOne = selectableOrDefault(choices.player1Avatar, AvatarId.KING)
    val savedTwo = resolveAvatarClash(savedOne, selectableOrDefault(choices.player2Avatar, AvatarId.QUEEN))
    var nameOne by rememberSaveable { mutableStateOf(limitTypedName(choices.player1Name ?: defaultOne)) }
    var nameTwo by rememberSaveable { mutableStateOf(limitTypedName(choices.player2Name ?: defaultTwo)) }
    var avatarOne by rememberSaveable { mutableStateOf(savedOne) }
    var avatarTwo by rememberSaveable { mutableStateOf(savedTwo) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    FormLayout(
        startEnabled = !starting,
        onStart = {
            starting = true
            val finalOne = finalName(nameOne, defaultOne)
            val finalTwo = finalName(nameTwo, defaultTwo)
            val config = twoPlayerGameConfig(finalOne, avatarOne, finalTwo, avatarTwo)
            scope.launch {
                settings.saveTwoPlayerSetup(finalOne, avatarOne, finalTwo, avatarTwo)
                onStart(config)
                starting = false
            }
        },
    ) {
        FieldLabel(stringResource(R.string.setup_player1_name))
        GoldTextField(value = nameOne, onValueChange = { nameOne = limitTypedName(it) })
        AvatarPicker(current = avatarOne, taken = avatarTwo, onSelect = { avatarOne = it })
        FieldLabel(stringResource(R.string.setup_player2_name))
        GoldTextField(value = nameTwo, onValueChange = { nameTwo = limitTypedName(it) })
        AvatarPicker(current = avatarTwo, taken = avatarOne, onSelect = { avatarTwo = it })
    }
}

/** Scrolling fields with START GAME pinned underneath, so the keyboard never hides the button. */
@Composable
private fun ColumnScope.FormLayout(
    startEnabled: Boolean,
    onStart: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        fields()
    }
    GoldButton(
        text = stringResource(R.string.setup_start),
        onClick = { if (startEnabled) onStart() },
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    BasicText(
        text = text,
        style = TwelvePitsType.label,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Row of the three selectable avatars. The one [taken] by the other player is dimmed and cannot be chosen. */
@Composable
private fun AvatarPicker(
    current: AvatarId,
    taken: AvatarId?,
    onSelect: (AvatarId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (avatar in SelectableAvatars) {
            val isTaken = avatar == taken
            val isSelected = avatar == current
            val label = stringResource(avatar.labelRes)
            val description = if (isTaken) stringResource(R.string.avatar_taken, label) else label
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .alpha(if (isTaken) 0.35f else 1f)
                    .clip(shape)
                    .background(ControlFill)
                    .border(
                        BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Gold else GoldLine),
                        shape,
                    )
                    .semantics {
                        selected = isSelected
                        contentDescription = description
                    }
                    .tapClickable(enabled = !isTaken, role = Role.RadioButton) { onSelect(avatar) }
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(avatar.drawableRes),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                BasicText(
                    text = label,
                    style = TwelvePitsType.segment.copy(textAlign = TextAlign.Center),
                )
            }
        }
    }
}
