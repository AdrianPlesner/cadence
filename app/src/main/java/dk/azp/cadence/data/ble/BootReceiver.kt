package dk.azp.cadence.data.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** System BLE scans do not survive a reboot, so the scan is registered again once the device is up. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            BleWake.register(context)
        }
    }
}
