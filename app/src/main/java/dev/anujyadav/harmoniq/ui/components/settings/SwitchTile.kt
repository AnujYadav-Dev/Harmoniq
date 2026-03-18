package dev.anujyadav.harmoniq.ui.components.settings

import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSwitchTile(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    subtitle: (@Composable () -> Unit)? = null
) {
    Card(
        enabled = enabled,
        colors = SettingsTileDefaults.cardColors(),
        onClick = {
            onChange(!value)
        }
    ) {
        ListItem(
            colors = SettingsTileDefaults.listItemColors(),
            leadingContent = { icon() },
            headlineContent = { title() },
            supportingContent = subtitle,
            trailingContent = {
                Switch(
                    checked = value,
                    enabled = enabled,
                    onCheckedChange = {
                        onChange(!value)
                    }
                )
            }
        )
    }
}
