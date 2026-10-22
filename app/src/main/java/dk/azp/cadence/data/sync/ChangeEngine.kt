package dk.azp.cadence.data.sync

import android.util.Log
import androidx.room.withTransaction
import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.db.CategoryEntity
import dk.azp.cadence.data.db.ChangeEntity
import dk.azp.cadence.data.db.CompletionEntity
import dk.azp.cadence.data.db.DeviceEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.math.max

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
    private val onChanged: (ChangeEffects) -> Unit = {},
) {

    /**
     * What a batch did that reminders care about: the tasks whose live state it changed, and the groups this device was
     * readmitted to.
     */
    data class ChangeEffects(val touchedTaskIds: Set<String>, val readmittedGroupIds: Set<String>)

    /** Groups this device was readmitted to during the batch being applied; only touched under [writeLock]. */
    private val readmittedGroups = mutableSetOf<String>()

    private val writeLock = Mutex()
    private val taskMerge = TaskMerge(json)

    suspend fun record(groupId: String, type: EntityType, entityId: String, payload: Any) {
        val effects = writeLock.withLock {
            readmittedGroups.clear()
            val change = db.withTransaction {
                val seq = max(identity.lastSeq, db.changeDao().maxSeq(identity.deviceId)) + 1
                identity.lastSeq = seq
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
                change
            }
            ChangeEffects(TouchedTasks.of(listOf(change), json), takeReadmittedGroups())
        }
        onChanged(effects)
    }

    /** Applies changes received from a peer. Returns how many were new to this device. */
    suspend fun applyRemote(groupId: String, changes: List<ChangeDto>): Int {
        var newCount = 0
        val effective = mutableListOf<ChangeEntity>()
        val effects = writeLock.withLock {
            readmittedGroups.clear()
            db.withTransaction {
                for (dto in changes) {
                    val change = dto.toEntity(groupId)
                    val inserted = db.changeDao().insert(change) != -1L
                    if (inserted) {
                        newCount++
                        // A change this version cannot interpret is still stored and relayed; it must not block the batch.
                        runCatching {
                            hlc.observe(change.hlc)
                            if (applyToLiveTables(change)) {
                                effective += change
                            }
                        }.onFailure { Log.w(TAG, "Could not apply ${change.entityType} ${change.entityId} from ${change.originDevice}", it) }
                    }
                }
            }
            ChangeEffects(TouchedTasks.of(effective, json), takeReadmittedGroups())
        }
        if (newCount > 0) {
            onChanged(effects)
        }
        return newCount
    }

    private fun takeReadmittedGroups(): Set<String> = readmittedGroups.toSet().also { readmittedGroups.clear() }

    suspend fun cursors(groupId: String): Map<String, Long> =
        db.changeDao().cursors(groupId).associate { it.originDevice to it.seq }

    /** Every change in the group that a peer with the given cursors has not seen yet. */
    suspend fun changesSince(groupId: String, peerCursors: Map<String, Long>): List<ChangeDto> =
        db.changeDao().cursors(groupId).flatMap { local ->
            db.changeDao().after(groupId, local.originDevice, peerCursors[local.originDevice] ?: 0L)
        }.map { it.toDto() }

    /** Returns whether the change altered the live tables; an older change that loses the merge does not. */
    private suspend fun applyToLiveTables(change: ChangeEntity): Boolean =
        when (EntityType.valueOf(change.entityType)) {
            EntityType.GROUP -> applyGroup(change)
            EntityType.DEVICE -> applyDevice(change)
            EntityType.CATEGORY -> applyCategory(change)
            EntityType.TASK -> applyTask(change)
            EntityType.COMPLETION -> applyCompletion(change)
        }

    private suspend fun applyGroup(change: ChangeEntity): Boolean {
        val existing = db.groupDao().get(change.entityId)
        val newer = existing != null && Hlc.isNewer(change.hlc, existing.updatedHlc)
        if (newer) {
            val payload = json.decodeFromString<GroupPayload>(change.payload)
            db.groupDao().updateName(change.entityId, payload.name, change.hlc)
        }
        return newer
    }

    /**
     * Name and membership merge as separate fields. A device's own writes never change its membership, so a kicked
     * device cannot readmit itself by renaming, and a kick is never lost to a concurrent self-edit.
     */
    private suspend fun applyDevice(change: ChangeEntity): Boolean {
        val payload = json.decodeFromString<DevicePayload>(change.payload)
        val existing = db.deviceDao().get(change.groupId, change.entityId)
        val selfWrite = change.originDevice == change.entityId
        val merged = if (existing == null) {
            DeviceEntity(change.entityId, change.groupId, payload.name, change.hlc, deleted = payload.deleted && !selfWrite, deletedHlc = change.hlc)
        } else {
            val nameIsNewer = Hlc.isNewer(change.hlc, existing.updatedHlc)
            val membershipIsNewer = !selfWrite && Hlc.isNewer(change.hlc, existing.deletedHlc)
            existing.copy(
                name = if (nameIsNewer) payload.name else existing.name,
                updatedHlc = if (nameIsNewer) change.hlc else existing.updatedHlc,
                deleted = if (membershipIsNewer) payload.deleted else existing.deleted,
                deletedHlc = if (membershipIsNewer) change.hlc else existing.deletedHlc,
            )
        }
        db.deviceDao().upsert(merged)
        if (change.entityId == identity.deviceId) {
            db.groupDao().setKicked(change.groupId, merged.deleted)
            if (existing?.deleted == true && !merged.deleted) {
                readmittedGroups += change.groupId
            }
        }
        return merged != existing
    }

    private suspend fun applyCategory(change: ChangeEntity): Boolean {
        val existing = db.categoryDao().get(change.entityId)
        val newer = existing == null || Hlc.isNewer(change.hlc, existing.updatedHlc)
        if (newer) {
            val payload = json.decodeFromString<CategoryPayload>(change.payload)
            db.categoryDao().upsert(CategoryEntity(change.entityId, change.groupId, payload.name, change.hlc, payload.deleted))
        }
        return newer
    }

    private suspend fun applyTask(change: ChangeEntity): Boolean {
        val merged = taskMerge.merge(db.taskDao().get(change.entityId), change)
        merged?.let { db.taskDao().upsert(it) }
        return merged != null
    }

    private suspend fun applyCompletion(change: ChangeEntity): Boolean {
        val existing = db.completionDao().get(change.entityId)
        val newer = existing == null || Hlc.isNewer(change.hlc, existing.updatedHlc)
        if (newer) {
            val payload = json.decodeFromString<CompletionPayload>(change.payload)
            db.completionDao().upsert(
                CompletionEntity(change.entityId, change.groupId, payload.taskId, payload.doneDate, change.hlc, payload.deleted)
            )
        }
        return newer
    }

    private companion object {
        const val TAG = "ChangeEngine"
    }

    private fun encode(type: EntityType, payload: Any): String = when (type) {
        EntityType.GROUP -> json.encodeToString(payload as GroupPayload)
        EntityType.DEVICE -> json.encodeToString(payload as DevicePayload)
        EntityType.CATEGORY -> json.encodeToString(payload as CategoryPayload)
        EntityType.TASK -> json.encodeToString(payload as TaskPayload)
        EntityType.COMPLETION -> json.encodeToString(payload as CompletionPayload)
    }
}
