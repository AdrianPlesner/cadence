package dk.azp.cadence.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query(
        """
        SELECT g.*,
            (SELECT COUNT(*) FROM tasks t WHERE t.groupId = g.id AND t.deleted = 0) AS taskCount,
            (SELECT COUNT(*) FROM devices d WHERE d.groupId = g.id AND d.deleted = 0) AS deviceCount
        FROM groups g ORDER BY g.name COLLATE NOCASE
        """
    )
    fun observeSummaries(): Flow<List<GroupSummary>>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun observe(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun get(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE kicked = 0")
    suspend fun getActive(): List<GroupEntity>

    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("UPDATE groups SET name = :name, updatedHlc = :hlc WHERE id = :id")
    suspend fun updateName(id: String, name: String, hlc: String)

    @Query("UPDATE groups SET kicked = :kicked WHERE id = :id")
    suspend fun setKicked(id: String, kicked: Boolean)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DeviceDao {

    @Query(
        """
        SELECT d.*, p.lastSyncedAt AS lastSyncedAt, p.lastError AS lastError
        FROM devices d LEFT JOIN peer_sync p ON p.groupId = d.groupId AND p.deviceId = d.id
        WHERE d.groupId = :groupId AND d.deleted = 0 ORDER BY d.name COLLATE NOCASE
        """
    )
    fun observeMembers(groupId: String): Flow<List<DeviceWithSync>>

    @Query("SELECT * FROM devices WHERE groupId = :groupId AND id = :deviceId")
    suspend fun get(groupId: String, deviceId: String): DeviceEntity?

    @Query("SELECT groupId FROM devices WHERE id = :deviceId AND deleted = 0")
    suspend fun groupIdsForMember(deviceId: String): List<String>

    @Query("SELECT id FROM devices WHERE groupId = :groupId AND deleted = 1")
    suspend fun kickedDeviceIds(groupId: String): List<String>

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE groupId = :groupId AND deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeForGroup(groupId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface TaskDao {

    @Query(
        """
        SELECT t.*, (SELECT MAX(c.doneDate) FROM completions c WHERE c.taskId = t.id AND c.deleted = 0) AS lastDone
        FROM tasks t WHERE t.groupId = :groupId AND t.deleted = 0 ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeForGroup(groupId: String): Flow<List<TaskWithLastDone>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query(
        """
        SELECT t.*, (SELECT MAX(c.doneDate) FROM completions c WHERE c.taskId = t.id AND c.deleted = 0) AS lastDone, g.name AS groupName
        FROM tasks t JOIN groups g ON g.id = t.groupId
        WHERE t.deleted = 0 AND t.notifyWhenDue = 1 AND t.cadenceDays IS NOT NULL AND g.kicked = 0
        """
    )
    suspend fun remindable(): List<ReminderTask>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface CompletionDao {

    @Query("SELECT * FROM completions WHERE taskId = :taskId AND deleted = 0 ORDER BY doneDate DESC, updatedHlc DESC")
    fun observeForTask(taskId: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE id = :id")
    suspend fun get(id: String): CompletionEntity?

    @Upsert
    suspend fun upsert(completion: CompletionEntity)

    @Query("DELETE FROM completions WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface ChangeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(change: ChangeEntity): Long

    @Query("SELECT COALESCE(MAX(seq), 0) FROM changes WHERE originDevice = :originDevice")
    suspend fun maxSeq(originDevice: String): Long

    @Query("SELECT originDevice, MAX(seq) AS seq FROM changes WHERE groupId = :groupId GROUP BY originDevice")
    suspend fun cursors(groupId: String): List<OriginCursor>

    @Query("SELECT * FROM changes WHERE groupId = :groupId AND originDevice = :originDevice AND seq > :afterSeq ORDER BY seq")
    suspend fun after(groupId: String, originDevice: String, afterSeq: Long): List<ChangeEntity>

    @Query("DELETE FROM changes WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}

@Dao
interface PeerSyncDao {

    @Query("SELECT * FROM peer_sync WHERE groupId = :groupId AND lastHost IS NOT NULL")
    suspend fun knownAddresses(groupId: String): List<PeerSyncEntity>

    @Query("SELECT * FROM peer_sync WHERE groupId = :groupId AND deviceId = :deviceId")
    suspend fun get(groupId: String, deviceId: String): PeerSyncEntity?

    @Upsert
    suspend fun upsert(peer: PeerSyncEntity)

    @Query("DELETE FROM peer_sync WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
