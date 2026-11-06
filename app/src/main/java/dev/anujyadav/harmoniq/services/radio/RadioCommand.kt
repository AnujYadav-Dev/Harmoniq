package dev.anujyadav.harmoniq.services.radio

/**
 * What can be asked of Radio with no activity around, delivered by [RadioNotificationService].
 * Use [RadioNotificationService.start] from code that can run while the app is in foreground,
 * or a foreground service PendingIntent from a widget.
 */
enum class RadioCommand {
    /** Continues what was playing, restoring the saved queue if needed. */
    Resume;

    val action = "dev.anujyadav.harmoniq.radio.command.${name.lowercase()}"

    companion object {
        fun fromAction(action: String?) = entries.find { it.action == action }
    }
}
