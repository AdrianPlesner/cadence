package dk.azp.cadence.data.repo

import androidx.room.withTransaction
import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.db.DeviceWithSync
import dk.azp.cadence.data.db.GroupEntity
import dk.azp.cadence.data.db.GroupSummary
import dk.azp.cadence.data.sync.ChangeEngine
import dk.azp.cadence.data.sync.DevicePayload
import dk.azp.cadence.data.sync.EntityType
import dk.azp.cadence.data.sync.GroupCrypto
import dk.azp.cadence.data.sync.GroupPayload
import dk.azp.cadence.data.sync.Invite
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class GroupRepository(
    private val db: CadenceDatabase,
    private val identity: DeviceIdentity,
    private val engine: ChangeEngine,
) {

    fun observeSummaries(): Flow<List<GroupSummary>> = db.groupDao().observeSummaries()

    fun observeGroup(groupId: String): Flow<GroupEntity?> = db.groupDao().observe(groupId)

    fun observeMembers(groupId: String): Flow<List<DeviceWithSync>> = db.deviceDao().observeMembers(groupId)

    suspend fun getGroup(groupId: String): GroupEntity? = db.groupDao().get(groupId)

    suspend fun createGroup(name: String): String {
        val groupId = UUID.randomUUID().toString()
        db.groupDao().upsert(GroupEntity(groupId, name, GroupCrypto.newSecret(), kicked = false, updatedHlc = ""))
        engine.record(groupId, EntityType.GROUP, groupId, GroupPayload(name))
        engine.record(groupId, EntityType.DEVICE, identity.deviceId, DevicePayload(identity.deviceName))
        return groupId
    }

    /**
     * Creates the group locally from an invite and announces this device as a member. A previously kicked device is
     * readmitted by the peer it syncs with next, not by this local write. Idempotent for known groups.
     */
    suspend fun joinGroup(invite: Invite): GroupEntity {
        val existing = db.groupDao().get(invite.groupId)
        if (existing == null) {
            db.groupDao().upsert(GroupEntity(invite.groupId, invite.groupName, invite.secret, kicked = false, updatedHlc = ""))
        } else if (existing.kicked) {
            db.groupDao().setKicked(invite.groupId, false)
        }
        engine.record(invite.groupId, EntityType.DEVICE, identity.deviceId, DevicePayload(identity.deviceName))
        return checkNotNull(db.groupDao().get(invite.groupId))
    }

    /** Called by the sync server when a kicked device presents itself again with a fresh invite. */
    suspend fun readmitDevice(groupId: String, deviceId: String, name: String) {
        engine.record(groupId, EntityType.DEVICE, deviceId, DevicePayload(name))
    }

    suspend fun renameGroup(groupId: String, name: String) {
        engine.record(groupId, EntityType.GROUP, groupId, GroupPayload(name))
    }

    suspend fun kickDevice(groupId: String, deviceId: String) {
        val device = db.deviceDao().get(groupId, deviceId) ?: return
        engine.record(groupId, EntityType.DEVICE, deviceId, DevicePayload(device.name, deleted = true))
    }

    /** Renames this device in every group it belongs to. */
    suspend fun renameThisDevice(name: String) {
        identity.deviceName = name
        for (groupId in db.deviceDao().groupIdsForMember(identity.deviceId)) {
            engine.record(groupId, EntityType.DEVICE, identity.deviceId, DevicePayload(name))
        }
    }

    /** Forgets the group on this device only. Other members keep the data and still list this device until kicked. */
    suspend fun leaveGroup(groupId: String) {
        db.withTransaction {
            db.completionDao().deleteForGroup(groupId)
            db.taskDao().deleteForGroup(groupId)
            db.categoryDao().deleteForGroup(groupId)
            db.deviceDao().deleteForGroup(groupId)
            db.changeDao().deleteForGroup(groupId)
            db.peerSyncDao().deleteForGroup(groupId)
            db.groupDao().delete(groupId)
        }
    }

    fun inviteFor(group: GroupEntity, host: String?, port: Int): Invite =
        Invite(group.id, group.name, group.secret, identity.deviceId, host, port.takeIf { it > 0 })
}
