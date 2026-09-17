package dk.azp.cadence

import dk.azp.cadence.data.sync.Hlc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlcTest {

    @Test
    fun stampsAreMonotonicWithinTheSameMillisecond() {
        val hlc = Hlc("device-a", 0L, 0, wallClock = { 1_000L })
        val first = hlc.next()
        val second = hlc.next()
        assertTrue(Hlc.isNewer(second, first))
        assertEquals(1_000L, Hlc.physicalOf(second))
        assertEquals(1, Hlc.counterOf(second))
    }

    @Test
    fun observingAFutureRemoteStampMovesLocalStampsPastIt() {
        val hlc = Hlc("device-a", 0L, 0, wallClock = { 1_000L })
        val remote = Hlc.format(5_000L, 3, "device-b")
        hlc.observe(remote)
        val local = hlc.next()
        assertTrue(Hlc.isNewer(local, remote))
        assertEquals(5_000L, Hlc.physicalOf(local))
    }

    @Test
    fun wallClockAheadOfEverythingResetsTheCounter() {
        var now = 1_000L
        val hlc = Hlc("device-a", 0L, 0, wallClock = { now })
        hlc.next()
        hlc.next()
        now = 2_000L
        val stamp = hlc.next()
        assertEquals(0, Hlc.counterOf(stamp))
    }

    @Test
    fun deviceIdBreaksTiesDeterministically() {
        val a = Hlc.format(1_000L, 0, "aaaa")
        val b = Hlc.format(1_000L, 0, "bbbb")
        assertTrue(Hlc.isNewer(b, a))
    }
}
