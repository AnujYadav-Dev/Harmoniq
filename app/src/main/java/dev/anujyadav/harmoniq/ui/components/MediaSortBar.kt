package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import androidx.compose.material.icons.filled.ViewColumn
import dev.anujyadav.harmoniq.utils.AppStrings

@Composable
fun <T : Enum<T>> MediaSortBar(
    context: ViewContext,
    reverse: Boolean,
    onReverseChange: (Boolean) -> Unit,
    sort: T,
    sorts: Map<T, (ViewContext) -> String>,
    onSortChange: (T) -> Unit,
    label: @Composable () -> Unit,
    onShufflePlay: (() -> Unit)? = null,
    onShowModifyLayout: (() -> Unit)? = null,
    // shows a grid/list switch when set
    layout: MediaLayout? = null,
    onLayoutChange: ((MediaLayout) -> Unit)? = null,
) {
    var showDropdown by remember { mutableStateOf(false) }
    val currentTextStyle = MaterialTheme.typography.bodySmall.run {
        copy(color = MaterialTheme.colorScheme.onSurface)
    }

    val iconButtonStyle = IconButtonDefaults.iconButtonColors(
        contentColor = currentTextStyle.color
    )
    val iconModifier = Modifier.size(20.dp)
    val textButtonStyle = ButtonDefaults.textButtonColors(
        contentColor = currentTextStyle.color
    )

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row {
            Spacer(modifier = Modifier.width(8.dp))
            LabeledIconButton(
                icon = when {
                    reverse -> Icons.Filled.ArrowDownward
                    else -> Icons.Filled.ArrowUpward
                },
                label = AppStrings.ReverseSortOrder,
                colors = iconButtonStyle,
                iconModifier = iconModifier,
                onClick = { onReverseChange(!reverse) },
            )
            Box {
                TextButton(
                    colors = textButtonStyle,
                    onClick = {
                        showDropdown = !showDropdown
                    }
                ) {
                    Text(sorts[sort]!!(context), style = currentTextStyle)
                }
                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    sorts.map {
                        val onClick = {
                            showDropdown = false
                            onSortChange(it.key)
                        }

                        DropdownMenuItem(
                            contentPadding = MenuDefaults.DropdownMenuItemContentPadding.run {
                                val horizontalPadding = calculateLeftPadding(LayoutDirection.Ltr)
                                PaddingValues(
                                    start = horizontalPadding.div(2),
                                    end = horizontalPadding.times(4),
                                )
                            },
                            leadingIcon = {
                                RadioButton(
                                    selected = it.key == sort,
                                    onClick = onClick,
                                )
                            },
                            text = {
                                Text(it.value(context))
                            },
                            onClick = onClick,
                        )
                    }
                }
            }
            if (layout != null && onLayoutChange != null) {
                MediaLayoutToggle(
                    layout = layout,
                    onLayoutChange = onLayoutChange,
                    colors = iconButtonStyle,
                    iconModifier = iconModifier,
                )
            }
            // columns only apply to the grid
            onShowModifyLayout?.takeIf { layout != MediaLayout.List }?.let {
                LabeledIconButton(
                    icon = Icons.Filled.ViewColumn,
                    label = AppStrings.GridColumns,
                    iconModifier = iconModifier,
                    onClick = it,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyle(currentTextStyle) {
                label()
            }
            onShufflePlay?.let {
                LabeledIconButton(
                    icon = Icons.Filled.Shuffle,
                    label = AppStrings.ShufflePlay,
                    modifier = Modifier.padding(4.dp, 0.dp),
                    colors = iconButtonStyle,
                    iconModifier = iconModifier,
                    onClick = it,
                )
            }
            if (onShufflePlay == null) {
                Spacer(modifier = Modifier.width(20.dp))
            }
        }
    }
}

