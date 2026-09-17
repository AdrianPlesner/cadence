package dk.azp.cadence.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A group is the unit of sync: a set of devices that share a set of tasks.
 * [secret] and [kicked] are local-only; [name] is synced through the change log.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val secret: String,
    val kicked: Boolean,
    val updatedHlc: String,
)

/** Membership of a device in a group. A kicked device is a tombstoned row. */
@Entity(tableName = "devices", primaryKeys = ["groupId", "id"])
data class DeviceEntity(
    val id: String,
    val groupId: String,
    val name: String,
    val updatedHlc: String,
    val deleted: Boolean,
)

@Entity(tableName = "categories", indices = [Index("groupId")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val updatedHlc: String,
    val deleted: Boolean,
)

@Entity(tableName = "tasks", indices = [Index("groupId")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val name: String,
    val cadenceDays: Int?,
    /** Comma separated category ids; see [categoryIdList]. */
    val categoryIds: String,
    val updatedHlc: String,
    val deleted: Boolean,
) {
    fun categoryIdList(): List<String> = categoryIds.split(',').filter { it.isNotBlank() }
}

/** One occasion on which a task was carried out. [doneDate] is an epoch day. */
@Entity(tableName = "completions", indices = [Index("taskId"), Index("groupId")])
data class CompletionEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val taskId: String,
    val doneDate: Long,
    val updatedHlc: String,
    val deleted: Boolean,
)

/**
 * Append-only log of every change, local or received. Rows are identified by the device that made the change and that
 * device's own sequence number, so any device can relay any other device's changes.
 */
@Entity(tableName = "changes", primaryKeys = ["originDevice", "seq"], indices = [Index("groupId", "originDevice", "seq")])
data class ChangeEntity(
    val originDevice: String,
    val seq: Long,
    val groupId: String,
    val hlc: String,
    val entityType: String,
    val entityId: String,
    val payload: String,
)

/** Local-only record of the last exchange with a peer device for a group. */
@Entity(tableName = "peer_sync", primaryKeys = ["groupId", "deviceId"])
data class PeerSyncEntity(
    val groupId: String,
    val deviceId: String,
    val lastSyncedAt: Long?,
    val lastHost: String?,
    val lastPort: Int?,
    val lastError: String?,
)

data class GroupSummary(
    @Embedded val group: GroupEntity,
    val taskCount: Int,
    val deviceCount: Int,
)

data class TaskWithLastDone(
    @Embedded val task: TaskEntity,
    val lastDone: Long?,
)

data class DeviceWithSync(
    @Embedded val device: DeviceEntity,
    val lastSyncedAt: Long?,
    val lastError: String?,
)

data class OriginCursor(
    val originDevice: String,
    val seq: Long,
)
