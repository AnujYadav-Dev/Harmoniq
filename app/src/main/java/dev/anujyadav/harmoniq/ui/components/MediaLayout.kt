package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anujyadav.harmoniq.utils.AppStrings

/**
 * How a collection of media items is laid out.
 */
enum class MediaLayout {
    Grid,
    List;

    val toggled get() = if (this == Grid) List else Grid
}

/**
 * Switches between [MediaLayout]s. The icon shows the layout it switches to.
 */
@Composable
fun MediaLayoutToggle(
    layout: MediaLayout,
    onLayoutChange: (MediaLayout) -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    val target = layout.toggled
    LabeledIconButton(
        icon = when (target) {
            MediaLayout.Grid -> Icons.Filled.GridView
            MediaLayout.List -> Icons.AutoMirrored.Filled.ViewList
        },
        label = when (target) {
            MediaLayout.Grid -> AppStrings.GridView
            MediaLayout.List -> AppStrings.ListView
        },
        modifier = modifier,
        iconModifier = iconModifier,
        colors = colors,
        onClick = { onLayoutChange(target) },
    )
}
