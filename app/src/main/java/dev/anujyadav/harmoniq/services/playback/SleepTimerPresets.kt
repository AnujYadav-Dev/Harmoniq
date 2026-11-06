package dev.anujyadav.harmoniq.services.playback

/** The sleep timer's ready-made durations, for the dialog and what starts one with a tap. */
object SleepTimerPresets {
    data class Preset(val hours: Long, val minutes: Long)

    val all = listOf(
        Preset(0L, 15L),
        Preset(0L, 30L),
        Preset(1L, 0L),
        Preset(2L, 0L),
        Preset(3L, 0L),
    )

    /** What a tap starts until a timer has been used. */
    const val DEFAULT_MINUTES = 30
}
