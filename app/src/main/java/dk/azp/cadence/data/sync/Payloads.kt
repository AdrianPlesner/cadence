package dk.azp.cadence.data.sync

import kotlinx.serialization.Serializable

enum class EntityType { GROUP, DEVICE, CATEGORY, TASK, COMPLETION }

@Serializable
data class GroupPayload(val name: String)

@Serializable
data class DevicePayload(val name: String, val deleted: Boolean = false)

@Serializable
data class CategoryPayload(val name: String, val deleted: Boolean = false)

@Serializable
data class TaskPayload(
    val name: String,
    val cadenceDays: Int? = null,
    val categoryIds: List<String> = emptyList(),
    val notifyWhenDue: Boolean = false,
    val deleted: Boolean = false,
)

@Serializable
data class CompletionPayload(val taskId: String, val doneDate: Long, val deleted: Boolean = false)
