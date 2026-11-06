package dev.anujyadav.harmoniq.utils

import java.time.Duration

data class TimedContent(val pairs: List<Pair<Long, String>>) {
    val isSynced: Boolean get() = pairs.firstOrNull()?.first != pairs.lastOrNull()?.first

    companion object {
        // "\r\n" comes first: alternatives are tried in order, and "\r" alone would match it too
        val lrcLineSeparatorRegex = Regex("""\r\n|\n|\r""")
        val lrcLineFilterRegex = Regex("""^\[\s*(\d+):(\d+)\.(\d+)?\s*](.*)""")

        private const val MILLIS_DIGITS = 3

        // "[00:12.34]" is 12.340 s: the fraction is in hundredths here, in thousandths for ".345"
        internal fun fractionToMillis(fraction: String) =
            fraction.padEnd(MILLIS_DIGITS, '0').take(MILLIS_DIGITS).toLong()

        fun fromLyrics(content: String): TimedContent {
            var lastTime = 0L
            val pairs = content.split(lrcLineSeparatorRegex).map { x ->
                val match = lrcLineFilterRegex.matchEntire(x)
                val pair = when {
                    match != null -> Duration
                        .ofMinutes(match.groupValues[1].toLong())
                        .plusSeconds(match.groupValues[2].toLong())
                        .plusMillis(fractionToMillis(match.groupValues[3]))
                        .toMillis() to match.groupValues[4].trim()

                    else -> lastTime to x.trim()
                }
                lastTime = pair.first
                pair
            }
            return TimedContent(pairs)
        }
    }
}