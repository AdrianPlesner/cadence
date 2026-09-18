package dk.azp.cadence.data.ble

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dk.azp.cadence.CadenceApp
import dk.azp.cadence.data.sync.PeerDiscovery
import kotlin.time.Duration.Companion.seconds

/** Syncs with the peer whose beacon woke this device, keeping the engine up briefly so the peer can sync back too. */
class BleWakeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val peerDeviceId = inputData.getString(KEY_PEER_DEVICE_ID) ?: return Result.failure()
        val host = inputData.getString(KEY_HOST) ?: return Result.failure()
        val port = inputData.getInt(KEY_PORT, 0)
        val syncManager = (applicationContext as CadenceApp).container.syncManager
        val outcome = syncManager.runBackgroundWindow(WINDOW, PeerDiscovery.Peer(peerDeviceId, host, port))
        Log.i(TAG, "Bluetooth wake sync: $outcome")
        return Result.success()
    }

    companion object {
        const val KEY_PEER_DEVICE_ID = "peer_device_id"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        private const val TAG = "BleWakeWorker"
        private val WINDOW = 20.seconds
    }
}
