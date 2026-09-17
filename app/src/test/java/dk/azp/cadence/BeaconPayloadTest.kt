package dk.azp.cadence

import dk.azp.cadence.data.ble.BeaconPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeaconPayloadTest {

    @Test
    fun roundTrips() {
        val payload = BeaconPayload(BeaconPayload.deviceHash("device-a"), "192.168.1.20", 43210)
        assertEquals(payload, BeaconPayload.decode(payload.encode()))
    }

    @Test
    fun highPortsSurvive() {
        val payload = BeaconPayload(BeaconPayload.deviceHash("device-a"), "10.0.0.1", 65000)
        assertEquals(65000, BeaconPayload.decode(payload.encode())?.port)
    }

    @Test
    fun rejectsForeignManufacturerData() {
        assertNull(BeaconPayload.decode(byteArrayOf(1, 2, 3)))
        assertNull(BeaconPayload.decode(ByteArray(16)))
    }

    @Test
    fun hashIsStableAndShort() {
        assertEquals(BeaconPayload.HASH_SIZE, BeaconPayload.deviceHash("x").size)
        assertEquals(BeaconPayload.deviceHash("x").toList(), BeaconPayload.deviceHash("x").toList())
    }
}
