package com.westly.twelvepits.feature.game.multiplayer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode
import com.westly.twelvepits.core.storage.SettingsStore
import com.westly.twelvepits.core.storage.SetupChoices
import com.westly.twelvepits.core.theme.ControlFill
import com.westly.twelvepits.core.theme.GameDialog
import com.westly.twelvepits.core.theme.Gold
import com.westly.twelvepits.core.theme.GoldButton
import com.westly.twelvepits.core.theme.GoldLine
import com.westly.twelvepits.core.theme.GoldTextField
import com.westly.twelvepits.core.theme.OutlinedGoldButton
import com.westly.twelvepits.core.theme.ScreenScaffold
import com.westly.twelvepits.core.theme.TwelvePitsType
import com.westly.twelvepits.core.theme.drawableRes
import com.westly.twelvepits.core.theme.tapClickable
import com.westly.twelvepits.feature.game.data.SavedGameStore
import com.westly.twelvepits.feature.menu.AvatarPicker
import com.westly.twelvepits.feature.menu.FieldLabel
import com.westly.twelvepits.feature.menu.finalName
import com.westly.twelvepits.feature.menu.limitTypedName
import com.westly.twelvepits.feature.menu.selectableOrDefault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class PhonesRole { HOST, JOIN }

private enum class PhonesDialog {
    NONE,
    SERVICES,
    PERMISSION_INFO,
    PERMISSION_DENIED,
    PERMISSION_BLOCKED,
    LOCATION_OFF,
}

/**
 * The Two phones screen (portrait): choose a name and an avatar, then host a game or join one. The same
 * screen shows every step until the game starts, then [onGameReady] opens the game screen.
 */
@Composable
fun PhonesScreen(
    session: PhonesSession,
    settings: SettingsStore,
    savedGames: SavedGameStore,
    onBack: () -> Unit,
    onGameReady: (GameConfig) -> Unit,
) {
    val lobby = session.lobby
    val state by lobby.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var pendingRole by remember { mutableStateOf<PhonesRole?>(null) }
    var pendingName by remember { mutableStateOf("") }
    var pendingAvatar by remember { mutableStateOf(AvatarId.KING) }
    var dialog by remember { mutableStateOf(PhonesDialog.NONE) }

    val choices by produceState<SetupChoices?>(initialValue = null, settings) {
        value = settings.setupChoices.first()
    }

    BackHandler {
        if (state.stage == LobbyStage.START) onBack() else lobby.cancel()
    }
    // A phone that is stopped while it is looking or waiting stops looking.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val stage = lobby.state.value.stage
        if (stage != LobbyStage.START && stage != LobbyStage.READY) lobby.cancel()
    }
    LaunchedEffect(state.stage) {
        if (state.stage == LobbyStage.READY) {
            val config = lobby.controller?.config
            if (config != null) onGameReady(config)
        }
    }

    fun startLobby(role: PhonesRole) {
        scope.launch {
            val me = RemotePlayer(pendingName, pendingAvatar)
            settings.saveTwoPhonesSetup(pendingName, pendingAvatar)
            if (role == PhonesRole.HOST) {
                lobby.host(me, savedGames.starterFor(GameMode.TWO_PHONES))
            } else {
                lobby.join(me)
            }
        }
    }

    fun afterPermissions(role: PhonesRole) {
        if (PhonesPermissions.needsLocationSwitch(Build.VERSION.SDK_INT) && !isLocationOn(context)) {
            dialog = PhonesDialog.LOCATION_OFF
            return
        }
        startLobby(role)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val role = pendingRole
        val denied = result.filterValues { granted -> !granted }.keys
        if (denied.isEmpty()) {
            if (role != null) afterPermissions(role)
        } else {
            // When Android no longer shows the question, the only way on is the app's settings page.
            val blocked = activity != null &&
                denied.any { permission -> !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) }
            dialog = if (blocked) PhonesDialog.PERMISSION_BLOCKED else PhonesDialog.PERMISSION_DENIED
        }
    }

    fun missingPermissions(): List<String> =
        PhonesPermissions.required(Build.VERSION.SDK_INT).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun begin(role: PhonesRole) {
        pendingRole = role
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            dialog = PhonesDialog.SERVICES
            return
        }
        if (missingPermissions().isNotEmpty()) {
            dialog = PhonesDialog.PERMISSION_INFO
            return
        }
        afterPermissions(role)
    }

    ScreenScaffold(
        title = stringResource(R.string.phones_title),
        onBack = { if (state.stage == LobbyStage.START) onBack() else lobby.cancel() },
    ) {
        when (state.stage) {
            LobbyStage.START -> {
                val loaded = choices
                if (loaded != null) {
                    StartForm(
                        choices = loaded,
                        onHost = { name, avatar ->
                            pendingName = name
                            pendingAvatar = avatar
                            begin(PhonesRole.HOST)
                        },
                        onJoin = { name, avatar ->
                            pendingName = name
                            pendingAvatar = avatar
                            begin(PhonesRole.JOIN)
                        },
                    )
                }
            }
            LobbyStage.HOST_WAITING -> WaitingPanel(
                title = stringResource(R.string.phones_host_waiting_title),
                body = stringResource(R.string.phones_host_waiting_body, pendingName),
                onCancel = lobby::cancel,
            )
            LobbyStage.JOIN_SEARCHING -> JoinList(state = state, onChoose = lobby::choose, onCancel = lobby::cancel)
            LobbyStage.REQUESTING -> WaitingPanel(
                title = stringResource(R.string.phones_connecting, state.remote?.name.orEmpty()),
                body = null,
                onCancel = lobby::cancel,
            )
            LobbyStage.CONFIRM -> ConfirmPanel(state = state, lobby = lobby)
            LobbyStage.CONNECTING, LobbyStage.READY -> WaitingPanel(
                title = stringResource(R.string.phones_starting),
                body = null,
                onCancel = lobby::cancel,
            )
            LobbyStage.FAILED -> FailedPanel(error = state.error, onTryAgain = lobby::reset)
        }
    }

    when (dialog) {
        PhonesDialog.NONE -> Unit
        PhonesDialog.SERVICES -> GameDialog(
            title = stringResource(R.string.phones_unavailable_title),
            message = stringResource(R.string.phones_unavailable_message),
            confirmText = stringResource(R.string.dialog_ok),
            onConfirm = { dialog = PhonesDialog.NONE },
            onDismiss = { dialog = PhonesDialog.NONE },
        )
        PhonesDialog.PERMISSION_INFO -> GameDialog(
            title = stringResource(R.string.phones_permission_title),
            message = stringResource(
                if (PhonesPermissions.isLocationBased(Build.VERSION.SDK_INT)) {
                    R.string.phones_permission_message_location
                } else {
                    R.string.phones_permission_message
                },
            ),
            confirmText = stringResource(R.string.phones_continue),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                dialog = PhonesDialog.NONE
                permissionLauncher.launch(missingPermissions().toTypedArray())
            },
            onDismiss = { dialog = PhonesDialog.NONE },
        )
        PhonesDialog.PERMISSION_DENIED -> GameDialog(
            title = stringResource(R.string.phones_permission_title),
            message = stringResource(R.string.phones_permission_denied_message),
            confirmText = stringResource(R.string.phones_try_again),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                dialog = PhonesDialog.NONE
                pendingRole?.let { begin(it) }
            },
            onDismiss = { dialog = PhonesDialog.NONE },
        )
        PhonesDialog.PERMISSION_BLOCKED -> GameDialog(
            title = stringResource(R.string.phones_permission_title),
            message = stringResource(R.string.phones_permission_blocked_message),
            confirmText = stringResource(R.string.phones_open_settings),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                dialog = PhonesDialog.NONE
                openSettings(
                    context,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onDismiss = { dialog = PhonesDialog.NONE },
        )
        PhonesDialog.LOCATION_OFF -> GameDialog(
            title = stringResource(R.string.phones_location_title),
            message = stringResource(R.string.phones_location_message),
            confirmText = stringResource(R.string.phones_open_settings),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                dialog = PhonesDialog.NONE
                openSettings(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onDismiss = { dialog = PhonesDialog.NONE },
        )
    }
}

@Composable
private fun ColumnScope.StartForm(
    choices: SetupChoices,
    onHost: (String, AvatarId) -> Unit,
    onJoin: (String, AvatarId) -> Unit,
) {
    val defaultName = stringResource(R.string.default_name_you)
    var name by rememberSaveable { mutableStateOf(limitTypedName(choices.phonesName ?: defaultName)) }
    var avatar by rememberSaveable { mutableStateOf(selectableOrDefault(choices.phonesAvatar, AvatarId.KING)) }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FieldLabel(stringResource(R.string.setup_your_name))
        GoldTextField(value = name, onValueChange = { name = limitTypedName(it) })
        FieldLabel(stringResource(R.string.setup_avatar))
        AvatarPicker(current = avatar, taken = null, onSelect = { avatar = it })
        BasicText(
            text = stringResource(R.string.phones_hint),
            style = TwelvePitsType.bodyMuted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GoldButton(
            text = stringResource(R.string.phones_host),
            onClick = { onHost(finalName(name, defaultName), avatar) },
        )
        OutlinedGoldButton(
            text = stringResource(R.string.phones_join),
            onClick = { onJoin(finalName(name, defaultName), avatar) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.WaitingPanel(title: String, body: String?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text = title, style = TwelvePitsType.sectionTitle.copy(textAlign = TextAlign.Center))
        if (body != null) {
            BasicText(text = body, style = TwelvePitsType.body.copy(textAlign = TextAlign.Center))
        }
    }
    OutlinedGoldButton(
        text = stringResource(R.string.dialog_cancel),
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun ColumnScope.JoinList(
    state: LobbyState,
    onChoose: (FoundHost) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(text = stringResource(R.string.phones_join_title), style = TwelvePitsType.sectionTitle)
        if (state.hosts.isEmpty()) {
            BasicText(text = stringResource(R.string.phones_join_empty), style = TwelvePitsType.bodyMuted)
        }
        for (host in state.hosts) {
            HostRow(host = host, onClick = { onChoose(host) })
        }
    }
    OutlinedGoldButton(
        text = stringResource(R.string.dialog_cancel),
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

@Composable
private fun HostRow(host: FoundHost, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(ControlFill)
            .border(BorderStroke(1.dp, GoldLine), shape)
            .tapClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(host.player.avatar.drawableRes),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.width(14.dp))
        BasicText(text = host.player.name, style = TwelvePitsType.cardTitle, maxLines = 1)
    }
}

@Composable
private fun ColumnScope.ConfirmPanel(state: LobbyState, lobby: PhonesLobby) {
    val remoteName = state.remote?.name.orEmpty()
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.phones_confirm_title),
            style = TwelvePitsType.sectionTitle.copy(textAlign = TextAlign.Center),
        )
        BasicText(
            text = state.authCode.orEmpty(),
            style = TwelvePitsType.screenTitle.copy(textAlign = TextAlign.Center, color = Gold),
        )
        BasicText(
            text = stringResource(R.string.phones_confirm_body, remoteName),
            style = TwelvePitsType.body.copy(textAlign = TextAlign.Center),
        )
        if (state.localAccepted) {
            BasicText(
                text = stringResource(R.string.phones_confirm_waiting, remoteName),
                style = TwelvePitsType.bodyMuted.copy(textAlign = TextAlign.Center),
            )
        }
    }
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.localAccepted) {
            GoldButton(text = stringResource(R.string.phones_confirm_yes), onClick = lobby::confirmYes)
            OutlinedGoldButton(
                text = stringResource(R.string.phones_confirm_no),
                onClick = lobby::confirmNo,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedGoldButton(
                text = stringResource(R.string.dialog_cancel),
                onClick = lobby::cancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ColumnScope.FailedPanel(error: LobbyError?, onTryAgain: () -> Unit) {
    val message = stringResource(
        when (error) {
            LobbyError.START_FAILED -> R.string.phones_error_start
            LobbyError.REFUSED -> R.string.phones_error_refused
            LobbyError.TIMEOUT -> R.string.phones_error_timeout
            LobbyError.VERSION_MISMATCH -> R.string.phones_end_version
            LobbyError.LOST -> R.string.phones_end_lost
            LobbyError.ERROR, null -> R.string.phones_error_generic
        },
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = stringResource(R.string.phones_error_title),
            style = TwelvePitsType.sectionTitle.copy(textAlign = TextAlign.Center),
        )
        BasicText(text = message, style = TwelvePitsType.body.copy(textAlign = TextAlign.Center))
    }
    GoldButton(
        text = stringResource(R.string.phones_try_again),
        onClick = onTryAgain,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

private fun isLocationOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
    return LocationManagerCompat.isLocationEnabled(manager)
}

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        // No settings page to open: the player can still open Settings by hand.
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
