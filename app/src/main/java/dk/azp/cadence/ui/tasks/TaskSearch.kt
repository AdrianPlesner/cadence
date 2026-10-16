package dk.azp.cadence.ui.tasks

import java.util.Locale

/** Matches tasks against a typed search: every word must appear in the task's name or in one of its categories. */
object TaskSearch {

    /** Any run of whitespace, including no-break spaces, which \s alone does not cover. */
    private val WHITESPACE = Regex("""[\s\p{Z}]+""")

    fun matches(row: TaskRow, query: String): Boolean {
        val words = query.lowercase(Locale.ROOT).split(WHITESPACE).filter { it.isNotEmpty() }
        val haystack = (listOf(row.task.name) + row.categoryNames).map { it.lowercase(Locale.ROOT) }
        return words.all { word -> haystack.any { it.contains(word) } }
    }
}
