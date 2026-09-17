package dk.azp.cadence.data.sync

import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.db.GroupEntity
import dk.azp.cadence.data.db.PeerSyncEntity
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** Answers sync requests from peers. Listens on a random port that the caller advertises through discovery. */
class SyncServer(
    private val db: CadenceDatabase,
    private val identity: DeviceIdentity,
    private val engine: ChangeEngine,
    private val json: Json,
    private val readmitDevice: suspend (groupId: String, deviceId: String, name: String) -> Unit,
    private val onPeerSynced: (groupId: String, peerDeviceId: String) -> Unit,
) {

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    var port: Int = 0
        private set

    suspend fun start(): Int {
        if (server != null) {
            return port
        }
        val started = embeddedServer(CIO, port = 0) {
            routing {
                post("/groups/{groupId}/hello") { handleHello(call) }
                post("/groups/{groupId}/sync") { handleSync(call) }
            }
        }.start(wait = false)
        server = started
        port = started.engine.resolvedConnectors().first().port
        return port
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)
        server = null
        port = 0
    }

    private suspend fun handleHello(call: ApplicationCall) {
        val session = authenticate<HelloRequest>(call, allowKicked = true) ?: return
        val response = HelloResponse(identity.deviceId, identity.deviceName, System.currentTimeMillis(), engine.cursors(session.group.id))
        rememberPeer(session.group.id, session.request.deviceId, call.request.origin.remoteAddress, session.request.listenPort, synced = false)
        call.respondText(session.crypto.encrypt(json.encodeToString(response)))
    }

    private suspend fun handleSync(call: ApplicationCall) {
        val session = authenticate<SyncRequest>(call, allowKicked = true) ?: return
        val request = session.request
        val groupId = session.group.id
        val rejoin = request.changes.firstOrNull { it.isSelfIntroductionOf(request.deviceId) }
        if (session.callerKicked) {
            if (rejoin == null) {
                call.respond(HttpStatusCode.Forbidden)
                return
            }
            readmitDevice(groupId, request.deviceId, json.decodeFromString<DevicePayload>(rejoin.payload).name)
        }
        val kicked = db.deviceDao().kickedDeviceIds(groupId).toSet()
        engine.applyRemote(groupId, request.changes.filter { it.originDevice !in kicked })
        val missing = engine.changesSince(session.group.id, request.cursors)
        val response = SyncResponse(identity.deviceId, identity.deviceName, System.currentTimeMillis(), missing)
        rememberPeer(session.group.id, request.deviceId, call.request.origin.remoteAddress, request.listenPort, synced = true)
        call.respondText(session.crypto.encrypt(json.encodeToString(response)))
        onPeerSynced(session.group.id, request.deviceId)
    }

    /**
     * Decrypts the request with the group secret and, unless [allowKicked], rejects kicked members. Responds with an
     * error and returns null when the request must not be served.
     */
    private suspend inline fun <reified T : Any> authenticate(call: ApplicationCall, allowKicked: Boolean = false): Session<T>? {
        val groupId = call.parameters["groupId"]
        val group = groupId?.let { db.groupDao().get(it) }
        var session: Session<T>? = null
        if (group == null || group.kicked) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val crypto = GroupCrypto(group.secret)
            val decrypted = crypto.decrypt(call.receiveText())
            val request = decrypted?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
            if (request == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val callerId = callerIdOf(request)
                val sentAt = sentAtOf(request)
                val callerKicked = db.deviceDao().get(group.id, callerId)?.deleted == true
                when {
                    abs(System.currentTimeMillis() - sentAt) > MAX_CLOCK_SKEW_MILLIS -> call.respond(HttpStatusCode.Unauthorized)
                    callerKicked && !allowKicked -> call.respond(HttpStatusCode.Forbidden)
                    else -> session = Session(group, crypto, request, callerKicked)
                }
            }
        }
        return session
    }

    private fun callerIdOf(request: Any): String = when (request) {
        is HelloRequest -> request.deviceId
        is SyncRequest -> request.deviceId
        else -> throw IllegalArgumentException("Unsupported request ${request::class}")
    }

    private fun sentAtOf(request: Any): Long = when (request) {
        is HelloRequest -> request.sentAt
        is SyncRequest -> request.sentAt
        else -> throw IllegalArgumentException("Unsupported request ${request::class}")
    }

    private suspend fun rememberPeer(groupId: String, peerDeviceId: String, host: String, port: Int, synced: Boolean) {
        val existing = db.peerSyncDao().get(groupId, peerDeviceId)
        db.peerSyncDao().upsert(
            PeerSyncEntity(
                groupId = groupId,
                deviceId = peerDeviceId,
                lastSyncedAt = if (synced) System.currentTimeMillis() else existing?.lastSyncedAt,
                lastHost = host,
                lastPort = port,
                lastError = if (synced) null else existing?.lastError,
            )
        )
    }

    private fun ChangeDto.isSelfIntroductionOf(deviceId: String): Boolean =
        entityType == EntityType.DEVICE.name && entityId == deviceId && originDevice == deviceId

    private class Session<T>(val group: GroupEntity, val crypto: GroupCrypto, val request: T, val callerKicked: Boolean)

    private companion object {
        const val MAX_CLOCK_SKEW_MILLIS = 5 * 60 * 1000L
    }
}
