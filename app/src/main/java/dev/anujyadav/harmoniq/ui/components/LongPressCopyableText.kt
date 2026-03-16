package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.ActivityUtils

@Composable
fun LongPressCopyableText(context: ViewContext, text: String) {
    Text(
        text,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                ActivityUtils.copyToClipboardAndNotify(context.symphony, text)
            })
        }
    )
}
