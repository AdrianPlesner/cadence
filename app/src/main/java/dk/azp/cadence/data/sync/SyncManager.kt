package dk.azp.cadence.data.sync

import android.content.Context
import android.util.Log
import dk.azp.cadence.data.DeviceIdentity
import dk.azp.cadence.data.ble.BleBeacon
import dk.azp.cadence.data.db.CadenceDatabase
import dk.azp.cadence.data.db.GroupEntity
import dk.azp.cadence.data.db.PeerSyncEntity
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Runs the sync server, finds peers on the LAN and exchanges changes with every peer that is a member of one of this
 * device's groups. The engine is on while at least one holder wants it: the foreground UI, or a background sync window.
 */
class SyncManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val db: CadenceDatabase,
    private val identity: DeviceIdentity,
    private val server: SyncServer,
    private val client: SyncClient,
) {

    data class Status(val running: Boolean = false, val activeSyncs: Int = 0, val lastMessage: String? = null)

    private val statusFlow = MutableStateFlow(Status())
    val status: StateFlow<Status> = statusFlow

    private val listenPortFlow = MutableStateFlow(0)
    val listenPort: StateFlow<Int> = listenPortFlow

    private var discovery: PeerDiscovery? = null
    private val beacon = BleBeacon(context, identity.deviceId)
    private val peerLocks = mutableMapOf<String, Mutex>()
    /** Who currently wants the engine running; guarded by [lifecycleLock], which also orders start and stop. */
    private val holders = mutableSetOf<String>()
    private val lifecycleLock = Mutex()

    /** Keeps the engine running until the same holder calls [release]. */
    fun acquire(holder: String) {
        scope.launch { acquireNow(holder) }
    }

    fun release(holder: String) {
        scope.launch { releaseNow(holder) }
    }

    /**
     * One background sync pass: brings the engine up, contacts [peer] if given and every peer address seen before,
     * leaves the engine running for [window] so discovered peers can be synced too, then hands the engine back.
     * Returns a short status line.
     */
    suspend fun runBackgroundWindow(window: Duration, peer: PeerDiscovery.Peer? = null): String {
        val started = acquireNow(HOLDER_BACKGROUND)
        return try {
            if (started) {
                if (peer != null) {
                    syncWithPeer(peer)
                }
                syncKnownPeers()
                delay(window)
                withTimeoutOrNull(window) { statusFlow.first { it.activeSyncs == 0 } }
                statusFlow.value.lastMessage ?: "No peers reachable"
            } else {
                "Sync engine could not start"
            }
        } finally {
            releaseNow(HOLDER_BACKGROUND)
        }
    }

    private suspend fun acquireNow(holder: String): Boolean = lifecycleLock.withLock {
        holders += holder
        val started = runCatching {
            if (discovery == null) {
                val port = server.start()
                listenPortFlow.value = port
                discovery = PeerDiscovery(context, identity.deviceId) { peer -> scope.launch { syncWithPeer(peer) } }.also { it.start(port) }
                beacon.start(localAddress(), port)
                statusFlow.update { it.copy(running = true) }
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not start sync", error)
            statusFlow.update { it.copy(lastMessage = "Sync could not start: ${error.message}") }
        }
        if (started.isSuccess && holder == HOLDER_FOREGROUND) {
            scope.launch { syncKnownPeers() }
        }
        started.isSuccess
    }

    private suspend fun releaseNow(holder: String) {
        lifecycleLock.withLock {
            holders -= holder
            if (holders.isEmpty()) {
                beacon.stop()
                discovery?.stop()
                discovery = null
                server.stop()
                listenPortFlow.value = 0
                statusFlow.update { it.copy(running = false) }
            }
        }
    }

    fun localAddress(): String? = NetworkAddress.localIpv4(context)

    /** Starts the beacon if the engine is already running, for use right after Bluetooth permissions were granted. */
    fun refreshBeacon() {
        scope.launch {
            lifecycleLock.withLock {
                if (discovery != null) {
                    beacon.start(localAddress(), listenPortFlow.value)
                }
            }
        }
    }

    /** Rescans the network and retries every peer address seen before. */
    fun syncNow() {
        discovery?.rescan()
        scope.launch { syncKnownPeers() }
    }

    /** First sync after joining, against the address embedded in the invite. Runs independently of the caller. */
    fun syncWithInviteHost(group: GroupEntity, invite: Invite) {
        val host = invite.host
        val port = invite.port
        if (host != null && port != null) {
            scope.launch { syncGroupWithPeer(group, invite.hostDeviceId, host, port) }
        }
    }

    private suspend fun syncKnownPeers() {
        val jobs = mutableListOf<Job>()
        val groups = runCatching { db.groupDao().getActive() }.getOrElse { emptyList() }
        for (group in groups) {
            for (peer in db.peerSyncDao().knownAddresses(group.id)) {
                val host = peer.lastHost
                val port = peer.lastPort
                if (host != null && port != null) {
                    jobs += scope.launch { syncGroupWithPeer(group, peer.deviceId, host, port) }
                }
            }
        }
        jobs.joinAll()
    }

    private suspend fun syncWithPeer(peer: PeerDiscovery.Peer) {
        val groupIds = db.deviceDao().groupIdsForMember(peer.deviceId).toSet()
        for (group in db.groupDao().getActive().filter { it.id in groupIds }) {
            syncGroupWithPeer(group, peer.deviceId, peer.host, peer.port)
        }
    }

    private suspend fun syncGroupWithPeer(group: GroupEntity, peerDeviceId: String, host: String, port: Int) {
        val lock = synchronized(peerLocks) { peerLocks.getOrPut("${group.id}/$peerDeviceId") { Mutex() } }
        lock.withLock {
            statusFlow.update { it.copy(activeSyncs = it.activeSyncs + 1) }
            val outcome = runCatching { client.sync(group, host, port) }
            val existing = db.peerSyncDao().get(group.id, peerDeviceId)
            outcome.onSuccess { result ->
                db.peerSyncDao().upsert(PeerSyncEntity(group.id, peerDeviceId, System.currentTimeMillis(), host, port, null))
                statusFlow.update {
                    it.copy(activeSyncs = it.activeSyncs - 1, lastMessage = "Synced with ${result.peerDeviceName}: sent ${result.sent}, received ${result.received}")
                }
            }.onFailure { error ->
                Log.w(TAG, "Sync with $peerDeviceId at $host:$port failed", error)
                if (error is SyncClient.PeerRejectedException && error.status == HttpStatusCode.Forbidden) {
                    db.groupDao().setKicked(group.id, true)
                }
                db.peerSyncDao().upsert(PeerSyncEntity(group.id, peerDeviceId, existing?.lastSyncedAt, host, port, error.message ?: error::class.simpleName))
                statusFlow.update { it.copy(activeSyncs = it.activeSyncs - 1, lastMessage = "Sync with $host failed: ${error.message}") }
            }
        }
    }

    companion object {
        const val HOLDER_FOREGROUND = "foreground"
        const val HOLDER_BACKGROUND = "background"
        private const val TAG = "SyncManager"
    }
}
