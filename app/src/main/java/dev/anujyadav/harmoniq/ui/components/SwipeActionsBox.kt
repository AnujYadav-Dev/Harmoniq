package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val onTrigger: () -> Unit,
)

object SwipeActionsBoxDefaults {
    // fraction of the row width that has to be swiped to trigger the action
    const val THRESHOLD_FRACTION = 0.35f
}

/**
 * Runs [swipeRight] / [swipeLeft] when the row is swiped past the threshold, then
 * snaps the row back instead of dismissing it. A `null` action disables that direction.
 */
@Composable
fun SwipeActionsBox(
    swipeRight: SwipeAction?,
    swipeLeft: SwipeAction?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled || (swipeRight == null && swipeLeft == null)) {
        content()
        return
    }

    // the state outlives recompositions, so always trigger the latest actions
    val latestSwipeRight by rememberUpdatedState(swipeRight)
    val latestSwipeLeft by rememberUpdatedState(swipeLeft)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> latestSwipeRight?.onTrigger?.invoke()
                SwipeToDismissBoxValue.EndToStart -> latestSwipeLeft?.onTrigger?.invoke()
                SwipeToDismissBoxValue.Settled -> {}
            }
            false
        },
        positionalThreshold = { it * SwipeActionsBoxDefaults.THRESHOLD_FRACTION },
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = swipeRight != null,
        enableDismissFromEndToStart = swipeLeft != null,
        backgroundContent = {
            val isSwipingRight = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> swipeRight
                SwipeToDismissBoxValue.EndToStart -> swipeLeft
                SwipeToDismissBoxValue.Settled -> null
            } ?: return@SwipeToDismissBox

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = MaterialTheme.colorScheme.onPrimaryContainer
                Icon(action.icon, null, tint = color)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
            }
        },
    ) {
        // opaque so the action underneath only shows beside the moving row
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}
