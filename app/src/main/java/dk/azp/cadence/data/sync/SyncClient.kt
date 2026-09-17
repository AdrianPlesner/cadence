package dk.azp.cadence.data.sync

import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.db.GroupEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class SyncClient(
    private val identity: DeviceIdentity,
    private val engine: ChangeEngine,
    private val json: Json,
    private val listenPort: () -> Int,
) {

    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 4_000
            requestTimeoutMillis = 20_000
        }
    }

    class Result(val peerDeviceId: String, val peerDeviceName: String, val sent: Int, val received: Int)

    class PeerRejectedException(status: HttpStatusCode) : Exception("Peer answered ${status.value}")

    /** Runs a full two-leg exchange with the peer at host:port for one group. */
    suspend fun sync(group: GroupEntity, host: String, port: Int): Result {
        val crypto = GroupCrypto(group.secret)
        val base = "http://$host:$port/groups/${group.id}"
        val hello = HelloRequest(identity.deviceId, identity.deviceName, listenPort(), System.currentTimeMillis())
        val helloResponse = exchange<HelloResponse>("$base/hello", crypto, json.encodeToString(hello))
        val outgoing = engine.changesSince(group.id, helloResponse.cursors)
        val request = SyncRequest(
            deviceId = identity.deviceId,
            deviceName = identity.deviceName,
            listenPort = listenPort(),
            sentAt = System.currentTimeMillis(),
            cursors = engine.cursors(group.id),
            changes = outgoing,
        )
        val syncResponse = exchange<SyncResponse>("$base/sync", crypto, json.encodeToString(request))
        val received = engine.applyRemote(group.id, syncResponse.changes)
        return Result(syncResponse.deviceId, syncResponse.deviceName, outgoing.size, received)
    }

    private suspend inline fun <reified T> exchange(url: String, crypto: GroupCrypto, body: String): T {
        val response = http.post(url) { setBody(crypto.encrypt(body)) }
        if (!response.status.isSuccess()) {
            throw PeerRejectedException(response.status)
        }
        val plain = crypto.decrypt(response.bodyAsText()) ?: throw IllegalStateException("Peer response could not be decrypted")
        return json.decodeFromString(plain)
    }
}
