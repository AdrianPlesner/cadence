package dk.azp.cadence.data.sync

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

object NetworkAddress {

    /** The IPv4 address other devices on the current network can reach this device on, if any. */
    fun localIpv4(context: Context): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return null
        val linkProperties = connectivity.getLinkProperties(network) ?: return null
        return linkProperties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }
}
