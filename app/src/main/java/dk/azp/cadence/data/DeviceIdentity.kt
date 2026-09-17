package dk.azp.cadence.data

import android.content.Context
import android.os.Build
import java.util.UUID

/** Stable identity of this installation plus the small bits of sync state that must survive process death. */
class DeviceIdentity(context: Context) {

    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null) ?: defaultDeviceName()
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    fun loadHlcPhysical(): Long = prefs.getLong(KEY_HLC_PHYSICAL, 0L)

    fun loadHlcCounter(): Int = prefs.getInt(KEY_HLC_COUNTER, 0)

    fun saveHlc(physical: Long, counter: Int) {
        prefs.edit().putLong(KEY_HLC_PHYSICAL, physical).putInt(KEY_HLC_COUNTER, counter).apply()
    }

    private fun defaultDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model".trim()
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_HLC_PHYSICAL = "hlc_physical"
        const val KEY_HLC_COUNTER = "hlc_counter"
    }
}
