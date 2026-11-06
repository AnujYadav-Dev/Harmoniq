package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlin.math.absoluteValue

fun Modifier.swipeable(
    minimumDragAmount: Float = 50f,
    // falls back to minimumDragAmount
    minimumVerticalDragDistance: Dp? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
) = pointerInput(Unit) {
    val minimumVerticalDragAmount = minimumVerticalDragDistance?.toPx() ?: minimumDragAmount
    var offset = Offset.Zero
    detectDragGestures(
        onDrag = { pointer, dragAmount ->
            pointer.consume()
            offset += dragAmount
        },
        onDragEnd = {
            val xAbs = offset.x.absoluteValue
            val yAbs = offset.y.absoluteValue
            when {
                xAbs > minimumDragAmount && xAbs > yAbs -> when {
                    offset.x > 0 -> onSwipeRight?.invoke()
                    else -> onSwipeLeft?.invoke()
                }

                yAbs > xAbs && yAbs > minimumVerticalDragAmount -> when {
                    offset.y > 0 -> onSwipeDown?.invoke()
                    else -> onSwipeUp?.invoke()
                }
            }
            offset = Offset.Zero
        },
        onDragCancel = {
            offset = Offset.Zero
        }
    )
}
