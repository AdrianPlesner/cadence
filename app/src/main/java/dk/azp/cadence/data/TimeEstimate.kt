package dk.azp.cadence.data

/** Reads and writes task time estimates, stored as whole minutes. */
object TimeEstimate {

    /** Longest estimate accepted, so a typo like "1000h" is rejected instead of stored. */
    const val MAX_MINUTES = 100 * 60

    private const val MINUTE_UNIT = "(?:m|min|mins|minute|minutes)"
    private const val HOUR_UNIT = "(?:h|hr|hrs|hour|hours)"
    private val clockPattern = Regex("""^(\d+):(\d{1,2})$""")
    private val hoursPattern = Regex("""^(\d+(?:\.\d+)?)\s*$HOUR_UNIT(?:\s*(\d+)\s*$MINUTE_UNIT?)?$""")
    private val minutesPattern = Regex("""^(\d+)\s*$MINUTE_UNIT?$""")

    /**
     * Accepts "20", "45m", "45 min", "2h", "1.5h", "1h 30m", "1h30", "1 h 30 min" and "1:30". Returns null for blank,
     * malformed, zero or over-long input.
     */
    fun parse(text: String): Int? {
        val input = text.trim().lowercase().replace(',', '.')
        val clock = clockPattern.matchEntire(input)
        val hours = hoursPattern.matchEntire(input)
        val minutes = minutesPattern.matchEntire(input)
        val parsed = when {
            clock != null -> clockMinutes(clock)
            hours != null -> (hours.groupValues[1].toDouble() * 60).toInt() + (hours.groupValues[2].toIntOrNull() ?: 0)
            minutes != null -> minutes.groupValues[1].toIntOrNull()
            else -> null
        }
        return parsed?.takeIf { it in 1..MAX_MINUTES }
    }

    /** "20 min", "1 h" or "1 h 30 min". The output is accepted by [parse]. */
    fun format(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "$rest min"
            rest == 0 -> "$hours h"
            else -> "$hours h $rest min"
        }
    }

    private fun clockMinutes(match: MatchResult): Int? {
        val minutes = match.groupValues[2].toInt()
        return if (minutes < 60) match.groupValues[1].toInt() * 60 + minutes else null
    }
}
