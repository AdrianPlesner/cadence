package dk.azp.cadence.data.sync

import dk.azp.cadence.data.db.ChangeEntity
import kotlinx.serialization.json.Json

/** Which tasks a batch of changes concerns: an edited task itself, and the task a completion belongs to. */
object TouchedTasks {

    fun of(changes: List<ChangeEntity>, json: Json): Set<String> =
        changes.mapNotNull { change ->
            when (change.entityType) {
                EntityType.TASK.name -> change.entityId
                EntityType.COMPLETION.name -> runCatching { json.decodeFromString<CompletionPayload>(change.payload).taskId }.getOrNull()
                else -> null
            }
        }.toSet()
}
