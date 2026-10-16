package dk.azp.cadence.data.db

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import dk.azp.cadence.data.sync.TaskMerge
import kotlinx.serialization.json.Json

/**
 * Runs after the version 1 to 2 migration. A device on 0.1.0 already stored changes carrying estimates from upgraded
 * peers, but could not apply them, so the estimates are rebuilt from the stored change log.
 */
class EstimateBackfill : AutoMigrationSpec {

    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        val changes = mutableListOf<ChangeEntity>()
        db.query("SELECT originDevice, seq, groupId, hlc, entityType, entityId, payload FROM changes WHERE entityType = 'TASK'").use { cursor ->
            while (cursor.moveToNext()) {
                changes += ChangeEntity(
                    originDevice = cursor.getString(0),
                    seq = cursor.getLong(1),
                    groupId = cursor.getString(2),
                    hlc = cursor.getString(3),
                    entityType = cursor.getString(4),
                    entityId = cursor.getString(5),
                    payload = cursor.getString(6),
                )
            }
        }
        TaskMerge(Json { ignoreUnknownKeys = true }).latestEstimates(changes).forEach { (taskId, estimate) ->
            db.execSQL("UPDATE tasks SET estimateMinutes = ?, estimateHlc = ? WHERE id = ?", arrayOf<Any?>(estimate.minutes, estimate.hlc, taskId))
        }
    }
}
