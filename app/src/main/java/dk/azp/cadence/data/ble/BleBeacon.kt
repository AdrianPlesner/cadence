package dk.azp.cadence.data.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/** Advertises this device's sync endpoint over BLE so sleeping peers can be woken by the system's background scan. */
class BleBeacon(private val context: Context, private val deviceId: String) {

    private var advertiser: BluetoothLeAdvertiser? = null
    private val callback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "Advertising failed: $errorCode")
        }
    }

    fun start(host: String?, port: Int) {
        val ready = host != null && port > 0 && canAdvertise(context)
        if (!ready) {
            return
        }
        val leAdvertiser = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(BeaconPayload.MANUFACTURER_ID, BeaconPayload(BeaconPayload.deviceHash(deviceId), host!!, port).encode())
            .build()
        runCatching {
            stop()
            leAdvertiser.startAdvertising(settings, data, callback)
            advertiser = leAdvertiser
        }.onFailure { Log.w(TAG, "Could not start advertising", it) }
    }

    fun stop() {
        runCatching { advertiser?.stopAdvertising(callback) }
        advertiser = null
    }

    companion object {
        private const val TAG = "BleBeacon"

        fun canAdvertise(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }
}
