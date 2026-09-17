package dk.azp.cadence.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GroupEntity::class,
        DeviceEntity::class,
        CategoryEntity::class,
        TaskEntity::class,
        CompletionEntity::class,
        ChangeEntity::class,
        PeerSyncEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CadenceDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao

    abstract fun deviceDao(): DeviceDao

    abstract fun categoryDao(): CategoryDao

    abstract fun taskDao(): TaskDao

    abstract fun completionDao(): CompletionDao

    abstract fun changeDao(): ChangeDao

    abstract fun peerSyncDao(): PeerSyncDao

    companion object {
        fun create(context: Context): CadenceDatabase =
            Room.databaseBuilder(context, CadenceDatabase::class.java, "cadence.db").build()
    }
}
