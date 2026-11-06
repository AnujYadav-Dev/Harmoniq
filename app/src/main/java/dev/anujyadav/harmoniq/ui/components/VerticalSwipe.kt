package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

object VerticalSwipeDefaults {
    val MinimumDragDistance = 72.dp
}

/**
 * Reacts to vertical swipes only. Children handle a gesture first, so anything that
 * already consumed the drag (scrolling lists, sliders) keeps working as usual.
 */
fun Modifier.verticalSwipeable(
    minimumDragDistance: Dp = VerticalSwipeDefaults.MinimumDragDistance,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
) = pointerInput(Unit) {
    val minimumDragAmount = minimumDragDistance.toPx()
    var offsetY = 0f
    detectVerticalDragGestures(
        onDragStart = { offsetY = 0f },
        onDragEnd = {
            if (offsetY.absoluteValue > minimumDragAmount) {
                if (offsetY > 0) onSwipeDown() else onSwipeUp()
            }
        },
        onVerticalDrag = { pointer, dragAmount ->
            pointer.consume()
            offsetY += dragAmount
        },
    )
}

/** Keeps an imprecise drag on a control, like a seek bar, from reaching [verticalSwipeable]. */
fun Modifier.blockVerticalSwipe() = pointerInput(Unit) {
    detectVerticalDragGestures { pointer, _ -> pointer.consume() }
}
