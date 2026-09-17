package dk.azp.cadence.data.ble

import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * What a device broadcasts over Bluetooth LE while its sync server is up: who it is and where to reach it on the LAN.
 * Fits in the manufacturer-data field of a legacy advertisement, so no scan response is needed.
 */
data class BeaconPayload(val deviceHash: ByteArray, val host: String, val port: Int) {

    fun encode(): ByteArray {
        val address = InetAddress.getByName(host).address
        require(address.size == 4) { "Beacon carries IPv4 only" }
        return ByteBuffer.allocate(SIZE)
            .put(MAGIC)
            .put(deviceHash)
            .put(address)
            .putShort(port.toShort())
            .array()
    }

    override fun equals(other: Any?): Boolean =
        other is BeaconPayload && deviceHash.contentEquals(other.deviceHash) && host == other.host && port == other.port

    override fun hashCode(): Int = 31 * (31 * deviceHash.contentHashCode() + host.hashCode()) + port

    companion object {
        /** Bluetooth SIG company id reserved for internal use; the magic prefix tells Cadence beacons from other users of it. */
        const val MANUFACTURER_ID = 0xFFFF
        val MAGIC: ByteArray = byteArrayOf('C'.code.toByte(), 'D'.code.toByte())
        val MAGIC_MASK: ByteArray = byteArrayOf(-1, -1)
        const val HASH_SIZE = 8
        private const val SIZE = 2 + HASH_SIZE + 4 + 2

        fun deviceHash(deviceId: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray(Charsets.UTF_8)).copyOf(HASH_SIZE)

        fun decode(bytes: ByteArray): BeaconPayload? {
            if (bytes.size != SIZE || !bytes.copyOf(MAGIC.size).contentEquals(MAGIC)) {
                return null
            }
            val buffer = ByteBuffer.wrap(bytes, MAGIC.size, SIZE - MAGIC.size)
            val hash = ByteArray(HASH_SIZE).also(buffer::get)
            val address = ByteArray(4).also(buffer::get)
            val port = buffer.short.toInt() and 0xFFFF
            return BeaconPayload(hash, InetAddress.getByAddress(address).hostAddress ?: return null, port)
        }
    }
}
