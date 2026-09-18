package dk.azp.cadence.data.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dk.azp.cadence.CadenceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives beacons from the system scan and starts a sync with the peer that sent one, if it is a known member. */
class BleWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BEACON_SEEN || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
            return
        }
        val results = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java) ?: return
        val payloads = results.mapNotNull { result ->
            result.scanRecord?.getManufacturerSpecificData(BeaconPayload.MANUFACTURER_ID)?.let(BeaconPayload::decode)
        }
        if (payloads.isEmpty()) {
            return
        }
        val container = (context.applicationContext as CadenceApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val members = container.database.deviceDao().memberDeviceIds().associateBy { BeaconPayload.deviceHash(it).toList() }
                payloads.distinct().forEach { payload ->
                    val peerDeviceId = members[payload.deviceHash.toList()]
                    if (peerDeviceId != null && peerDeviceId != container.identity.deviceId) {
                        Log.i(TAG, "Beacon from $peerDeviceId at ${payload.host}:${payload.port}")
                        enqueueSync(context, peerDeviceId, payload)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun enqueueSync(context: Context, peerDeviceId: String, payload: BeaconPayload) {
        val request = OneTimeWorkRequestBuilder<BleWakeWorker>()
            .setInputData(
                workDataOf(
                    BleWakeWorker.KEY_PEER_DEVICE_ID to peerDeviceId,
                    BleWakeWorker.KEY_HOST to payload.host,
                    BleWakeWorker.KEY_PORT to payload.port,
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME_PREFIX + peerDeviceId, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val ACTION_BEACON_SEEN = "dk.azp.cadence.BEACON_SEEN"
        private const val TAG = "BleWakeReceiver"
        private const val WORK_NAME_PREFIX = "ble-wake-"
    }
}
