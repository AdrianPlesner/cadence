package dk.azp.cadence.data.sync

import dk.azp.cadence.data.db.ChangeEntity
import dk.azp.cadence.data.db.TaskEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Merges task changes. The estimate merges as its own field: it takes the value of the newest change that carries one,
 * so changes from versions without estimates never erase it, and every device ends up with the same value whatever
 * order changes arrive in.
 */
class TaskMerge(private val json: Json) {

    /** The estimate a change sets, or null when the change was made by a version without estimates. */
    data class EstimateWrite(val minutes: Int?, val hlc: String)

    /** Returns the task to store, or null when the change is older than everything it touches. */
    fun merge(existing: TaskEntity?, change: ChangeEntity): TaskEntity? {
        val payload = json.decodeFromString<TaskPayload>(change.payload)
        val estimate = estimateWrite(change)
        val rowIsNewer = existing == null || Hlc.isNewer(change.hlc, existing.updatedHlc)
        val estimateIsNewer = estimate != null && (existing == null || Hlc.isNewer(change.hlc, existing.estimateHlc))
        val base = when {
            rowIsNewer -> TaskEntity(
                id = change.entityId,
                groupId = change.groupId,
                name = payload.name,
                cadenceDays = payload.cadenceDays,
                categoryIds = payload.categoryIds.joinToString(","),
                notifyWhenDue = payload.notifyWhenDue,
                estimateMinutes = existing?.estimateMinutes,
                estimateHlc = existing?.estimateHlc ?: "",
                updatedHlc = change.hlc,
                deleted = payload.deleted,
            )
            estimateIsNewer -> existing
            else -> null
        }
        return if (estimate != null && estimateIsNewer) base?.copy(estimateMinutes = estimate.minutes, estimateHlc = estimate.hlc) else base
    }

    /** The newest estimate per task among [changes], for rebuilding estimates from a stored change log. */
    fun latestEstimates(changes: List<ChangeEntity>): Map<String, EstimateWrite> =
        changes
            .filter { it.entityType == EntityType.TASK.name }
            .mapNotNull { change -> runCatching { estimateWrite(change) }.getOrNull()?.let { change.entityId to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, writes) -> writes.maxBy { it.hlc } }

    private fun estimateWrite(change: ChangeEntity): EstimateWrite? =
        if (ESTIMATE_KEY in json.parseToJsonElement(change.payload).jsonObject) {
            EstimateWrite(json.decodeFromString<TaskPayload>(change.payload).estimateMinutes, change.hlc)
        } else {
            null
        }

    private companion object {
        const val ESTIMATE_KEY = "estimateMinutes"
    }
}
