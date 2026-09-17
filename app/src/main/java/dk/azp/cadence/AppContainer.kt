package dk.azp.cadence

import android.content.Context
import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.repo.GroupRepository
import dk.azp.cadence.data.repo.TaskRepository
import dk.azp.cadence.data.sync.ChangeEngine
import dk.azp.cadence.data.sync.Hlc
import dk.azp.cadence.data.sync.SyncClient
import dk.azp.cadence.data.sync.SyncManager
import dk.azp.cadence.data.sync.SyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** Hand-wired object graph for the whole app. */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    val identity = DeviceIdentity(context)
    val database = CadenceDatabase.create(context)

    private val hlc = Hlc(identity.deviceId, identity.loadHlcPhysical(), identity.loadHlcCounter(), persist = identity::saveHlc)
    private val engine = ChangeEngine(database, identity, hlc, json)

    val groupRepository = GroupRepository(database, identity, engine)
    val taskRepository = TaskRepository(database, engine)

    private val syncServer = SyncServer(database, identity, engine, json) { _, _ -> }
    private val syncClient = SyncClient(identity, engine, json) { syncServer.port }

    val syncManager = SyncManager(context, appScope, database, identity, syncServer, syncClient)
}
