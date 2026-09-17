package dk.azp.cadence.data.sync

import androidx.room.withTransaction
import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.ChangeEntity
import dk.azp.cadence.data.db.CompletionEntity
import dk.azp.cadence.data.db.DeviceEntity
import dk.azp.cadence.data.db.TaskEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * The single write path for synced data. Every local edit becomes a change-log row that is applied to the live tables,
 * and changes received from peers go through exactly the same application step. Rows merge last-writer-wins by HLC.
 */
class ChangeEngine(
    private val db: CadenceDatabase,
    private val identity: DeviceIdentity,
    private val hlc: Hlc,
    private val json: Json,
    /** Called after any batch of changes, local or remote, has been applied. */
    private val onChanged: () -> Unit = {},
) {

    private val writeLock = Mutex()

    suspend fun record(groupId: String, type: EntityType, entityId: String, payload: Any) {
        writeLock.withLock {
            db.withTransaction {
                val seq = db.changeDao().maxSeq(identity.deviceId) + 1
                val change = ChangeEntity(
                    originDevice = identity.deviceId,
                    seq = seq,
                    groupId = groupId,
                    hlc = hlc.next(),
                    entityType = type.name,
                    entityId = entityId,
                    payload = encode(type, payload),
                )
                db.changeDao().insert(change)
                applyToLiveTables(change)
            }
        }
        onChanged()
    }

    /** Applies changes received from a peer. Returns how many were new to this device. */
    suspend fun applyRemote(groupId: String, changes: List<ChangeDto>): Int {
        var applied = 0
        writeLock.withLock {
            db.withTransaction {
                for (dto in changes) {
                    val change = dto.toEntity(groupId)
                    val inserted = db.changeDao().insert(change) != -1L
                    if (inserted) {
                        hlc.observe(change.hlc)
                        applyToLiveTables(change)
                        applied++
                    }
                }
            }
        }
        if (applied > 0) {
            onChanged()
        }
        return applied
    }

    suspend fun cursors(groupId: String): Map<String, Long> =
        db.changeDao().cursors(groupId).associate { it.originDevice to it.seq }

    /** Every change in the group that a peer with the given cursors has not seen yet. */
    suspend fun changesSince(groupId: String, peerCursors: Map<String, Long>): List<ChangeDto> =
        db.changeDao().cursors(groupId).flatMap { local ->
            db.changeDao().after(groupId, local.originDevice, peerCursors[local.originDevice] ?: 0L)
        }.map { it.toDto() }

    private suspend fun applyToLiveTables(change: ChangeEntity) {
        when (EntityType.valueOf(change.entityType)) {
            EntityType.GROUP -> applyGroup(change)
            EntityType.DEVICE -> applyDevice(change)
            EntityType.CATEGORY -> applyCategory(change)
            EntityType.TASK -> applyTask(change)
            EntityType.COMPLETION -> applyCompletion(change)
        }
    }

    private suspend fun applyGroup(change: ChangeEntity) {
        val existing = db.groupDao().get(change.entityId) ?: return
        if (Hlc.isNewer(change.hlc, existing.updatedHlc)) {
            val payload = json.decodeFromString<GroupPayload>(change.payload)
            db.groupDao().updateName(change.entityId, payload.name, change.hlc)
        }
    }

    private suspend fun applyDevice(change: ChangeEntity) {
        val existing = db.deviceDao().get(change.groupId, change.entityId)
        if (existing == null || Hlc.isNewer(change.hlc, existing.updatedHlc)) {
            val payload = json.decodeFromString<DevicePayload>(change.payload)
            db.deviceDao().upsert(DeviceEntity(change.entityId, change.groupId, payload.name, change.hlc, payload.deleted))
            if (change.entityId == identity.deviceId) {
                db.groupDao().setKicked(change.groupId, payload.deleted)
            }
        }
    }

    private suspend fun applyCategory(change: ChangeEntity) {
        val existing = db.categoryDao().get(change.entityId)
        if (existing == null || Hlc.isNewer(change.hlc, existing.updatedHlc)) {
            val payload = json.decodeFromString<CategoryPayload>(change.payload)
            db.categoryDao().upsert(CategoryEntity(change.entityId, change.groupId, payload.name, change.hlc, payload.deleted))
        }
    }

    private suspend fun applyTask(change: ChangeEntity) {
        val existing = db.taskDao().get(change.entityId)
        if (existing == null || Hlc.isNewer(change.hlc, existing.updatedHlc)) {
            val payload = json.decodeFromString<TaskPayload>(change.payload)
            db.taskDao().upsert(
                TaskEntity(
                    id = change.entityId,
                    groupId = change.groupId,
                    name = payload.name,
                    cadenceDays = payload.cadenceDays,
                    categoryIds = payload.categoryIds.joinToString(","),
                    notifyWhenDue = payload.notifyWhenDue,
                    updatedHlc = change.hlc,
                    deleted = payload.deleted,
                )
            )
        }
    }

    private suspend fun applyCompletion(change: ChangeEntity) {
        val existing = db.completionDao().get(change.entityId)
        if (existing == null || Hlc.isNewer(change.hlc, existing.updatedHlc)) {
            val payload = json.decodeFromString<CompletionPayload>(change.payload)
            db.completionDao().upsert(
                CompletionEntity(change.entityId, change.groupId, payload.taskId, payload.doneDate, change.hlc, payload.deleted)
            )
        }
    }

    private fun encode(type: EntityType, payload: Any): String = when (type) {
        EntityType.GROUP -> json.encodeToString(payload as GroupPayload)
        EntityType.DEVICE -> json.encodeToString(payload as DevicePayload)
        EntityType.CATEGORY -> json.encodeToString(payload as CategoryPayload)
        EntityType.TASK -> json.encodeToString(payload as TaskPayload)
        EntityType.COMPLETION -> json.encodeToString(payload as CompletionPayload)
    }
}
