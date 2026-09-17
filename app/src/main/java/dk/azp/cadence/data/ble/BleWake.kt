package dk.azp.cadence.data.ble

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registers a system-level BLE scan for Cadence beacons. The scan outlives the app process: matching advertisements
 * are delivered to [BleWakeReceiver] even when the app is not running.
 */
object BleWake {

    private const val TAG = "BleWake"

    val requiredPermissions: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            emptyList()
        }

    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun hasPermissions(context: Context): Boolean =
        requiredPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Idempotent: re-registering with the same PendingIntent replaces the previous scan. Bluetooth calls go through the
     * Bluetooth service and can stall, so the work is kept off the calling thread.
     */
    fun register(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { registerBlocking(appContext) }
    }

    private fun registerBlocking(context: Context) {
        if (!isSupported(context) || !hasPermissions(context)) {
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setManufacturerData(BeaconPayload.MANUFACTURER_ID, BeaconPayload.MAGIC, BeaconPayload.MAGIC_MASK)
            .build()
        // First-match delivery wakes the app once per peer instead of on every advertisement; not every radio offloads it.
        val callbackType = if (adapter.isOffloadedFilteringSupported) {
            ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST
        } else {
            ScanSettings.CALLBACK_TYPE_ALL_MATCHES
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(callbackType)
            .build()
        runCatching {
            val result = scanner.startScan(listOf(filter), settings, pendingIntent(context))
            if (result != 0) {
                Log.w(TAG, "Background scan not started: $result")
            }
        }.onFailure { Log.w(TAG, "Could not register background scan", it) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BleWakeReceiver::class.java).setAction(BleWakeReceiver.ACTION_BEACON_SEEN)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }
}
