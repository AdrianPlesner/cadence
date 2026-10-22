package dk.azp.cadence

import dk.azp.cadence.data.db.ChangeEntity
import dk.azp.cadence.data.sync.TouchedTasks
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchedTasksTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun change(type: String, entityId: String, payload: String) = ChangeEntity("device", 1, "group", "hlc", type, entityId, payload)

    @Test
    fun taskEditTouchesThatTask() {
        assertEquals(setOf("water"), TouchedTasks.of(listOf(change("TASK", "water", """{"name":"Water plants"}""")), json))
    }

    @Test
    fun completionTouchesItsTask() {
        val done = change("COMPLETION", "completion-1", """{"taskId":"bins","doneDate":20383}""")
        assertEquals(setOf("bins"), TouchedTasks.of(listOf(done), json))
    }

    @Test
    fun otherChangesTouchNoTask() {
        val changes = listOf(
            change("GROUP", "group", """{"name":"Home"}"""),
            change("CATEGORY", "garden", """{"name":"Garden"}"""),
            change("DEVICE", "device", """{"name":"Phone"}"""),
        )
        assertEquals(emptySet<String>(), TouchedTasks.of(changes, json))
    }

    @Test
    fun batchCollectsEveryTouchedTaskOnce() {
        val changes = listOf(
            change("TASK", "water", """{"name":"Water plants"}"""),
            change("COMPLETION", "c1", """{"taskId":"water","doneDate":1}"""),
            change("COMPLETION", "c2", """{"taskId":"bins","doneDate":1}"""),
        )
        assertEquals(setOf("water", "bins"), TouchedTasks.of(changes, json))
    }

    @Test
    fun unreadableCompletionIsSkipped() {
        assertEquals(emptySet<String>(), TouchedTasks.of(listOf(change("COMPLETION", "c1", "not json")), json))
    }
}
