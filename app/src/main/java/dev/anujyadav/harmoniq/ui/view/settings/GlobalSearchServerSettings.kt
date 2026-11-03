package dev.anujyadav.harmoniq.ui.view.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.BuildConfig
import dev.anujyadav.harmoniq.services.globalsearch.GlobalSearchMessages
import dev.anujyadav.harmoniq.services.globalsearch.api.ServerError
import dev.anujyadav.harmoniq.services.globalsearch.connection.ConnectionState
import dev.anujyadav.harmoniq.services.globalsearch.connection.ServerConnection
import dev.anujyadav.harmoniq.services.globalsearch.connection.ServerConnections
import dev.anujyadav.harmoniq.services.globalsearch.connection.ServerMode
import dev.anujyadav.harmoniq.ui.components.ScaffoldDialog
import dev.anujyadav.harmoniq.ui.components.settings.SettingsOptionTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSideHeading
import dev.anujyadav.harmoniq.ui.components.settings.SettingsSimpleTile
import dev.anujyadav.harmoniq.ui.components.settings.SettingsTextInputTile
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import kotlinx.coroutines.launch

/**
 * Settings → Global search → Server: the default Harmoniq server (configured from GitHub)
 * or the user's own server (URL + token typed in here, token stored encrypted).
 */
@Composable
fun GlobalSearchServerSettings(context: ViewContext, enabled: Boolean) {
    val connections = context.symphony.globalSearch.connections
    val mode by connections.mode.collectAsState()
    val state by connections.state.collectAsState()
    val remoteStatus by connections.remoteStatus.collectAsState()
    val customUrl by connections.customUrl.collectAsState()
    val hasCustomToken by connections.hasCustomToken.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var testResult by remember(mode, customUrl, hasCustomToken) { mutableStateOf<String?>(null) }

    SettingsSideHeading(AppStrings.Server)
    SettingsOptionTile(
        icon = { Icon(Icons.Filled.Dns, null) },
        title = { Text(AppStrings.Server) },
        value = mode,
        values = mapOf(ServerMode.Default to AppStrings.ServerHarmoniq, ServerMode.Custom to AppStrings.ServerCustom),
        captions = mapOf(ServerMode.Default to AppStrings.ServerHarmoniqHint, ServerMode.Custom to AppStrings.ServerCustomHint),
        enabled = enabled,
        onChange = { connections.setMode(it) },
    )
    HorizontalDivider()

    when (mode) {
        ServerMode.Default -> SettingsSimpleTile(
            icon = { Icon(Icons.Filled.Sync, null) },
            title = { Text(AppStrings.ServerConnection) },
            subtitle = { Text(defaultConnectionSummary(state, remoteStatus, connections.hasRemoteConfig)) },
            enabled = enabled && connections.hasRemoteConfig,
            onClick = { coroutineScope.launch { connections.refreshRemote(force = true) } },
        )

        ServerMode.Custom -> {
            SettingsTextInputTile(
                context,
                icon = { Icon(Icons.Filled.Link, null) },
                title = { Text(AppStrings.ServerUrl) },
                value = customUrl.ifEmpty { AppStrings.ServerUrlNotSet },
                onChange = { input ->
                    if (connections.isValidServerUrl(input)) {
                        connections.saveCustom(input, token = null)
                    } else {
                        val hint = if (BuildConfig.DEBUG) AppStrings.ServerUrlInvalidDebug else AppStrings.ServerUrlInvalid
                        Toast.makeText(context.activity, hint, Toast.LENGTH_LONG).show()
                    }
                },
            )
            HorizontalDivider()
            ServerTokenTile(
                hasToken = hasCustomToken,
                onSave = { connections.saveCustom(customUrl, it) },
            )
            HorizontalDivider()
            SettingsSimpleTile(
                icon = { Icon(Icons.Filled.NetworkCheck, null) },
                title = { Text(AppStrings.TestConnection) },
                subtitle = { Text(testResult ?: AppStrings.TestConnectionHint) },
                enabled = enabled && (state as? ConnectionState.Ready)?.connection != null,
                onClick = {
                    val connection = (state as? ConnectionState.Ready)?.connection ?: return@SettingsSimpleTile
                    testResult = AppStrings.TestingConnection
                    coroutineScope.launch {
                        testResult = connections.test(connection.baseUrl.toString(), connection.appToken).fold(
                            onSuccess = { AppStrings.ConnectedToServerX(it.serverVersion) },
                            onFailure = { err ->
                                (err as? ServerError)?.let { GlobalSearchMessages.forError(it) } ?: AppStrings.ServerUrlInvalid
                            },
                        )
                    }
                },
            )
        }
    }
}

private fun defaultConnectionSummary(
    state: ConnectionState,
    remoteStatus: ServerConnections.RemoteStatus,
    hasRemoteConfig: Boolean,
): String = when {
    state is ConnectionState.Disabled -> AppStrings.ServerPausedX(state.message ?: AppStrings.GlobalSearchPaused)
    state is ConnectionState.NotConfigured -> AppStrings.ServerNotConfigured
    remoteStatus.isLoading -> AppStrings.ServerStatusChecking
    (state as? ConnectionState.Ready)?.connection?.source == ServerConnection.Source.Remote -> {
        val updated = DateUtils.getRelativeTimeSpanString(remoteStatus.fetchedAt).toString()
        if (remoteStatus.lastError != null) "${AppStrings.CouldNotReachGitHub} ($updated)"
        else AppStrings.ServerConnectionFromGitHubX(updated)
    }

    hasRemoteConfig && remoteStatus.lastError != null -> "${AppStrings.ServerConnectionBuiltIn} · ${AppStrings.CouldNotReachGitHub}"
    else -> AppStrings.ServerConnectionBuiltIn
}

@Composable
private fun ServerTokenTile(hasToken: Boolean, onSave: (String) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    SettingsSimpleTile(
        icon = { Icon(Icons.Filled.Key, null) },
        title = { Text(AppStrings.ServerAccessToken) },
        subtitle = { Text(if (hasToken) AppStrings.ServerAccessTokenSaved else AppStrings.ServerAccessTokenNotSet) },
        onClick = { isOpen = true },
    )
    if (!isOpen) return

    // the saved token is never shown or prefilled; typing a new one replaces it
    var input by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }
    ScaffoldDialog(
        onDismissRequest = { isOpen = false },
        title = { Text(AppStrings.ServerAccessToken) },
        content = {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    value = input,
                    onValueChange = { input = it },
                    visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { isVisible = !isVisible }) {
                            Icon(
                                if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isVisible) AppStrings.HideToken else AppStrings.ShowToken,
                            )
                        }
                    },
                )
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        AppStrings.ServerAccessTokenHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = { isOpen = false }) { Text(AppStrings.Cancel) }
            TextButton(
                enabled = input.isNotBlank(),
                onClick = {
                    onSave(input)
                    isOpen = false
                },
            ) { Text(AppStrings.Done) }
        },
    )
}
