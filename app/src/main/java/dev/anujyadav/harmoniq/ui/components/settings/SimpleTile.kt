package dev.anujyadav.harmoniq.ui.components.settings

import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSimpleTile(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Card(
        enabled = enabled,
        colors = SettingsTileDefaults.cardColors(),
        onClick = onClick
    ) {
        ListItem(
            colors = SettingsTileDefaults.listItemColors(enabled = enabled),
            leadingContent = { icon() },
            headlineContent = { title() },
            supportingContent = { subtitle?.let { it() } }
        )
    }
}
