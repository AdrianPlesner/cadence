package dk.azp.cadence.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Formatting {

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

    fun date(date: LocalDate): String = date.format(dateFormatter)

    fun daysAgo(days: Long): String = when (days) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }

    fun dueIn(daysLeft: Long): String = when {
        daysLeft > 1 -> "Due in $daysLeft days"
        daysLeft == 1L -> "Due tomorrow"
        daysLeft == 0L -> "Due today"
        daysLeft == -1L -> "Overdue by 1 day"
        else -> "Overdue by ${-daysLeft} days"
    }

    fun relativeTime(epochMillis: Long?, now: Long = System.currentTimeMillis()): String {
        if (epochMillis == null) {
            return "never"
        }
        val minutes = (now - epochMillis) / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
        }
    }
}
