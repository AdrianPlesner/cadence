package dk.azp.cadence

import dk.azp.cadence.data.db.TaskEntity
import dk.azp.cadence.ui.tasks.TaskRow
import dk.azp.cadence.ui.tasks.TaskSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSearchTest {

    private fun row(name: String, vararg categories: String) = TaskRow(
        task = TaskEntity("id", "group", name, null, "", false, null, "", "", false),
        categoryNames = categories.toList(),
        lastDone = null,
        daysSince = null,
        daysUntilDue = null,
    )

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(TaskSearch.matches(row("Water plants"), ""))
        assertTrue(TaskSearch.matches(row("Water plants"), "   "))
    }

    @Test
    fun matchesPartOfTheNameIgnoringCase() {
        assertTrue(TaskSearch.matches(row("Water plants"), "PLAN"))
        assertFalse(TaskSearch.matches(row("Water plants"), "bins"))
    }

    @Test
    fun matchesCategoryNames() {
        assertTrue(TaskSearch.matches(row("Water plants", "Garden"), "gard"))
    }

    @Test
    fun everyWordMustMatchSomewhere() {
        val task = row("Water plants", "Garden")
        assertTrue(TaskSearch.matches(task, "garden water"))
        assertFalse(TaskSearch.matches(task, "garden bins"))
    }

    @Test
    fun splitsOnAnyWhitespace() {
        val task = row("Water plants", "Garden")
        assertTrue(TaskSearch.matches(task, "garden\twater"))
        assertTrue(TaskSearch.matches(task, "garden\u00A0water"))
        assertFalse(TaskSearch.matches(task, "garden\u00A0bins"))
    }

    @Test
    fun matchesNonAsciiLetters() {
        assertTrue(TaskSearch.matches(row("Vask køleskab"), "KØLE"))
    }
}
