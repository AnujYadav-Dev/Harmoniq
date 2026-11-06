package dev.anujyadav.harmoniq.widgets

import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.action.ActionModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import dev.anujyadav.harmoniq.services.playback.PlaybackCommand

/**
 * Matches what starts [command] in the foreground. The library's own matcher compares Intent
 * objects, which are never the same one.
 */
internal fun sends(command: PlaybackCommand) = GlanceNodeMatcher<MappedNode>("sends $command") { node ->
    val action = (node.value as MappedNode).emittable.modifier
        .foldIn<Action?>(null) { found, modifier -> found ?: (modifier as? ActionModifier)?.action }
    val intent = runCatching { action?.javaClass?.getMethod("getIntent")?.invoke(action) as? Intent }.getOrNull()
    val isForeground = runCatching {
        action?.javaClass?.getMethod("isForegroundService")?.invoke(action) == true
    }.getOrDefault(false)
    isForeground && PlaybackCommand.fromIntent(intent) == command
}
