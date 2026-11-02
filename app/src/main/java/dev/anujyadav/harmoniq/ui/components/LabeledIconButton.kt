package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shows [label] as a tooltip when [content] is long-pressed, like Android does for
 * views with a tooltip text. Compose icon buttons do not do this on their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/**
 * An icon-only button that names its action, as a long-press tooltip and for
 * accessibility services. [content] is drawn after the icon (e.g. its dropdown menu).
 */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit = {},
) {
    WithTooltip(label) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = iconModifier,
                tint = tint.takeOrElse { LocalContentColor.current },
            )
            content()
        }
    }
}
