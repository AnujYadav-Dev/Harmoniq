package dev.anujyadav.harmoniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AlphabetIndex
import kotlin.math.ceil
import kotlinx.coroutines.launch

// narrower than the end padding of a row's trailing button, so rows keep their full width
private val BarWidth = 18.dp
private val SlotHeight = 14.dp
private val BubbleSize = 56.dp
private val LabelFontSize = 10.sp
private const val IDLE_LABEL_ALPHA = 0.55f

/**
 * The sections for a list as currently shown. [textOf] is the text the list is sorted by,
 * and null for sorts that aren't alphabetical, where jumping by letter would make no sense.
 */
@Composable
fun <T> rememberAlphabetIndex(
    context: ViewContext,
    items: List<T>,
    sortKey: Any?,
    minItems: Int = AlphabetIndex.MIN_ITEMS,
    textOf: ((T) -> String?)?,
): List<AlphabetIndex.Section> {
    val isEnabled by context.symphony.settings.alphabetIndexEnabled.flow.collectAsState()
    return remember(isEnabled, items, sortKey, minItems) {
        when {
            isEnabled && textOf != null -> AlphabetIndex.build(items, minItems, textOf)
            else -> emptyList()
        }
    }
}

/** Floats the bar over the edge of [content], with no background of its own. */
@Composable
fun AlphabetIndexedContent(
    sections: List<AlphabetIndex.Section>,
    onJumpToItem: suspend (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    var activeLabel by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (sections.isNotEmpty()) {
            AlphabetIndexBar(
                sections = sections,
                activeLabel = activeLabel,
                onActiveLabelChange = { activeLabel = it },
                onJumpToItem = onJumpToItem,
            )
        }
        activeLabel?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = BarWidth + 12.dp)
                    .size(BubbleSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun BoxScope.AlphabetIndexBar(
    sections: List<AlphabetIndex.Section>,
    activeLabel: String?,
    onActiveLabelChange: (String?) -> Unit,
    onJumpToItem: suspend (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestOnJumpToItem by rememberUpdatedState(onJumpToItem)
    val latestOnActiveLabelChange by rememberUpdatedState(onActiveLabelChange)

    BoxWithConstraints(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(BarWidth),
        contentAlignment = Alignment.Center,
    ) {
        // short screens (landscape, keyboard open) show every nth letter
        val maxSlots = (maxHeight / SlotHeight).toInt().coerceAtLeast(1)
        val step = ceil(sections.size.toFloat() / maxSlots).toInt().coerceAtLeast(1)
        val visibleSections = remember(sections, step) {
            sections.filterIndexed { index, _ -> index % step == 0 }
        }

        // only the letters take touches, the rest of the edge still reaches the list
        Column(
            modifier = Modifier
                // a drag along the edge shouldn't turn into the system back gesture
                .systemGestureExclusion()
                .pointerInput(visibleSections) {
                    val slotHeight = SlotHeight.toPx()
                    var selected: AlphabetIndex.Section? = null

                    fun select(y: Float) {
                        val slot = (y / slotHeight).toInt().coerceIn(0, visibleSections.lastIndex)
                        val section = visibleSections[slot]
                        if (section != selected) {
                            selected = section
                            latestOnActiveLabelChange(section.label)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { latestOnJumpToItem(section.itemIndex) }
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        select(down.position.y)
                        try {
                            do {
                                val change = awaitPointerEvent().changes
                                    .firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (change.pressed) {
                                    select(change.position.y)
                                }
                            } while (change.pressed)
                        } finally {
                            selected = null
                            latestOnActiveLabelChange(null)
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            visibleSections.forEach { section ->
                Box(
                    modifier = Modifier
                        .width(BarWidth)
                        .height(SlotHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        section.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = LabelFontSize),
                        color = when (section.label) {
                            activeLabel -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = IDLE_LABEL_ALPHA)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
