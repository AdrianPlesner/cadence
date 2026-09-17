package dk.azp.cadence.ui.tasks

import dk.azp.cadence.data.db.TaskEntity
import dk.azp.cadence.data.db.TaskWithLastDone
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A task as shown in lists: the stored entity plus everything derived from today's date. */
data class TaskRow(
    val task: TaskEntity,
    val categoryNames: List<String>,
    val lastDone: LocalDate?,
    val daysSince: Long?,
    val daysUntilDue: Long?,
) {

    val isOverdue: Boolean get() = daysUntilDue != null && daysUntilDue < 0

    companion object {
        fun from(row: TaskWithLastDone, categoryNames: Map<String, String>, today: LocalDate): TaskRow {
            val lastDone = row.lastDone?.let(LocalDate::ofEpochDay)
            val daysSince = lastDone?.let { ChronoUnit.DAYS.between(it, today) }
            val daysUntilDue = row.task.cadenceDays?.let { cadence -> daysSince?.let { cadence - it } }
            return TaskRow(
                task = row.task,
                categoryNames = row.task.categoryIdList().mapNotNull(categoryNames::get),
                lastDone = lastDone,
                daysSince = daysSince,
                daysUntilDue = daysUntilDue,
            )
        }

        /** Overdue first, then due soonest, then tasks without a cadence by longest since done, never-done tasks last. */
        val urgencyOrder: Comparator<TaskRow> = compareBy<TaskRow>(
            { it.daysUntilDue == null },
            { it.daysUntilDue ?: 0L },
            { it.daysSince == null },
            { -(it.daysSince ?: 0L) },
            { it.task.name.lowercase() },
        )
    }
}
