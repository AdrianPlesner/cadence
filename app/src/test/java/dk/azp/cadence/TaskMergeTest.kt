package dk.azp.cadence

import dk.azp.cadence.data.db.ChangeEntity
import dk.azp.cadence.data.db.TaskEntity
import dk.azp.cadence.data.sync.Hlc
import dk.azp.cadence.data.sync.TaskMerge
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskMergeTest {

    private val merge = TaskMerge(Json { ignoreUnknownKeys = true })

    private fun change(time: Long, device: String, payload: String) =
        ChangeEntity(device, time, "group", Hlc.format(time, 0, device), "TASK", "task", payload)

    /** A change from a current version, which always writes the estimate. */
    private fun current(time: Long, name: String, estimate: Int?) =
        change(time, "new-device", """{"name":"$name","estimateMinutes":${estimate ?: "null"}}""")

    /** A change from 0.1.0, which knows nothing about estimates. */
    private fun old(time: Long, name: String) = change(time, "old-device", """{"name":"$name"}""")

    private fun applyAll(vararg changes: ChangeEntity): TaskEntity? =
        changes.fold(null as TaskEntity?) { task, change -> merge.merge(task, change) ?: task }

    @Test
    fun oldEditKeepsTheEstimateInEitherOrder() {
        val setEstimate = current(1, "Water plants", 20)
        val oldRename = old(2, "Water the plants")
        listOf(applyAll(setEstimate, oldRename), applyAll(oldRename, setEstimate)).forEach { task ->
            assertEquals("Water the plants", task?.name)
            assertEquals(20, task?.estimateMinutes)
        }
    }

    @Test
    fun newerEstimateWinsInEitherOrder() {
        val first = current(1, "Water plants", 20)
        val second = current(2, "Water plants", 30)
        assertEquals(30, applyAll(first, second)?.estimateMinutes)
        assertEquals(30, applyAll(second, first)?.estimateMinutes)
    }

    @Test
    fun clearingTheEstimateWinsOverAnOlderValue() {
        val set = current(1, "Water plants", 20)
        val clear = current(2, "Water plants", null)
        assertNull(applyAll(set, clear)?.estimateMinutes)
        assertNull(applyAll(clear, set)?.estimateMinutes)
    }

    @Test
    fun staleChangeIsIgnored() {
        val task = applyAll(current(2, "Water plants", 20))
        assertNull(merge.merge(task, old(1, "Old name")))
    }

    @Test
    fun backfillPicksTheNewestCarriedEstimatePerTask() {
        val changes = listOf(current(1, "Water plants", 20), old(3, "Water the plants"), current(2, "Water plants", 25))
        assertEquals(TaskMerge.EstimateWrite(25, Hlc.format(2, 0, "new-device")), merge.latestEstimates(changes)["task"])
        assertNull(merge.latestEstimates(listOf(old(1, "Water plants")))["task"])
    }
}
