package dev.anujyadav.harmoniq.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.anujyadav.harmoniq.utils.AppStrings
import sh.calvin.reorderable.ReorderableCollectionItemScope

object ReorderableItemDefaults {
    @Composable
    fun containerColor(isDragging: Boolean) = when {
        isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> Color.Transparent
    }
}

@Composable
fun ReorderableCollectionItemScope.ReorderDragHandle(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDragStarted: () -> Unit = {},
    onDragStopped: () -> Unit = {},
) {
    IconButton(
        onClick = {},
        enabled = enabled,
        modifier = modifier.draggableHandle(
            enabled = enabled,
            onDragStarted = { onDragStarted() },
            onDragStopped = onDragStopped,
        ),
    ) {
        Icon(Icons.Filled.DragHandle, AppStrings.Reorder)
    }
}
