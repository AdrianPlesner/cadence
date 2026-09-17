package dk.azp.cadence.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.util.ArrayDeque

/** Advertises this device's sync server over DNS-SD and reports other Cadence devices on the network. */
class PeerDiscovery(
    context: Context,
    private val ownDeviceId: String,
    private val onPeerFound: (Peer) -> Unit,
) {

    data class Peer(val deviceId: String, val host: String, val port: Int)

    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val multicastLock = context.applicationContext.getSystemService(WifiManager::class.java)
        ?.createMulticastLock("cadence-discovery")
        ?.apply { setReferenceCounted(false) }

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val ownServiceName = "cadence-" + ownDeviceId.take(8)

    fun start(listenPort: Int) {
        multicastLock?.acquire()
        register(listenPort)
        discover()
    }

    fun stop() {
        registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        registrationListener = null
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        synchronized(resolveQueue) {
            resolveQueue.clear()
            resolving = false
        }
        if (multicastLock?.isHeld == true) {
            multicastLock.release()
        }
    }

    /** Restarts discovery so peers that were already found are reported again. */
    fun rescan() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        discover()
    }

    private fun register(listenPort: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = ownServiceName
            serviceType = SERVICE_TYPE
            port = listenPort
            setAttribute(ATTRIBUTE_DEVICE_ID, ownDeviceId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Service registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun discover() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val isPeer = serviceInfo.serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.') && serviceInfo.serviceName != ownServiceName
                if (isPeer) {
                    enqueueResolve(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) {
            resolveQueue.add(info)
        }
        resolveNext()
    }

    /** NsdManager only resolves one service at a time, so resolutions are serialised through a queue. */
    private fun resolveNext() {
        val next = synchronized(resolveQueue) {
            if (resolving) null else resolveQueue.poll()?.also { resolving = true }
        } ?: return
        @Suppress("DEPRECATION")
        nsdManager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                finishResolve()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val deviceId = serviceInfo.attributes[ATTRIBUTE_DEVICE_ID]?.toString(Charsets.UTF_8)
                @Suppress("DEPRECATION")
                val host = serviceInfo.host?.hostAddress
                if (deviceId != null && host != null && deviceId != ownDeviceId) {
                    onPeerFound(Peer(deviceId, host, serviceInfo.port))
                }
                finishResolve()
            }
        })
    }

    private fun finishResolve() {
        synchronized(resolveQueue) {
            resolving = false
        }
        resolveNext()
    }

    private companion object {
        const val TAG = "PeerDiscovery"
        const val SERVICE_TYPE = "_cadence._tcp."
        const val ATTRIBUTE_DEVICE_ID = "id"
    }
}
