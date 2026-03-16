package dev.anujyadav.harmoniq.ui.components.settings

import android.net.Uri
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.ActivityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLinkTile(
    context: ViewContext,
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    url: String,
) {
    Card(
        colors = SettingsTileDefaults.cardColors(),
        onClick = {
            ActivityUtils.startBrowserActivity(context.activity, Uri.parse(url))
        }
    ) {
        ListItem(
            colors = SettingsTileDefaults.listItemColors(),
            leadingContent = { icon() },
            headlineContent = { title() },
            supportingContent = { Text(url) }
        )
    }
}
