package dev.anujyadav.harmoniq.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anujyadav.harmoniq.ui.components.ScaffoldDialog
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.ui.theme.AppIcon
import dev.anujyadav.harmoniq.utils.AppStrings

private val TilePreviewSize = 40.dp
private val OptionPreviewSize = 52.dp
private val OptionWidth = 72.dp
private val SelectedBorderWidth = 2.dp
private val HairlineBorderWidth = 1.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppIconTile(context: ViewContext, icon: @Composable () -> Unit) {
    val settings = context.symphony.settings
    val selected by settings.appIcon.flow.collectAsState()
    var isOpen by remember { mutableStateOf(false) }

    Card(
        colors = SettingsTileDefaults.cardColors(),
        onClick = { isOpen = true },
    ) {
        ListItem(
            colors = SettingsTileDefaults.listItemColors(),
            leadingContent = { icon() },
            headlineContent = { Text(AppStrings.AppIcon) },
            supportingContent = { Text(selected.label) },
            trailingContent = { AppIconPreview(selected, TilePreviewSize) },
        )
    }

    if (isOpen) {
        AppIconDialog(
            selected = selected,
            onSelect = { option ->
                isOpen = false
                if (option != selected) {
                    // only saved here, see AppIcon.applyIfNeeded for when it's switched
                    settings.appIcon.setValue(option)
                    Toast.makeText(
                        context.symphony.applicationContext,
                        AppStrings.AppIconUpdating,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onDismissRequest = { isOpen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIconDialog(
    selected: AppIcon,
    onSelect: (AppIcon) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(AppStrings.AppIcon) },
        content = {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon.entries.forEach { option ->
                    AppIconOption(
                        option,
                        isSelected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(AppStrings.Cancel)
            }
        },
    )
}

@Composable
private fun AppIconOption(option: AppIcon, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(OptionWidth)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppIconPreview(
            option,
            OptionPreviewSize,
            // the hairline keeps the dark icon visible on a dark dialog
            border = if (isSelected) {
                BorderStroke(SelectedBorderWidth, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(HairlineBorderWidth, MaterialTheme.colorScheme.outlineVariant)
            },
        )
        Text(
            option.label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppIconPreview(
    option: AppIcon,
    size: androidx.compose.ui.unit.Dp,
    border: BorderStroke? = null,
) {
    AsyncImage(
        option.icon,
        option.label,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier),
    )
}
