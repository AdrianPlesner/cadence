package dk.azp.cadence

import dk.azp.cadence.data.TimeEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeEstimateTest {

    @Test
    fun parsesPlainMinutes() {
        assertEquals(20, TimeEstimate.parse("20"))
        assertEquals(45, TimeEstimate.parse("45m"))
        assertEquals(45, TimeEstimate.parse(" 45 min "))
        assertEquals(90, TimeEstimate.parse("90 minutes"))
    }

    @Test
    fun parsesHoursAndCombinations() {
        assertEquals(120, TimeEstimate.parse("2h"))
        assertEquals(90, TimeEstimate.parse("1.5h"))
        assertEquals(90, TimeEstimate.parse("1,5 hours"))
        assertEquals(90, TimeEstimate.parse("1h 30m"))
        assertEquals(90, TimeEstimate.parse("1h30"))
        assertEquals(90, TimeEstimate.parse("1 H 30 MIN"))
        assertEquals(90, TimeEstimate.parse("1:30"))
        assertEquals(5, TimeEstimate.parse("0:05"))
    }

    @Test
    fun rejectsBlankMalformedZeroAndHuge() {
        assertNull(TimeEstimate.parse(""))
        assertNull(TimeEstimate.parse("soon"))
        assertNull(TimeEstimate.parse("1:75"))
        assertNull(TimeEstimate.parse("0"))
        assertNull(TimeEstimate.parse("1000h"))
        assertNull(TimeEstimate.parse("-5"))
    }

    @Test
    fun formatsAndRoundTrips() {
        assertEquals("20 min", TimeEstimate.format(20))
        assertEquals("1 h", TimeEstimate.format(60))
        assertEquals("1 h 30 min", TimeEstimate.format(90))
        listOf(1, 20, 59, 60, 61, 90, 125, TimeEstimate.MAX_MINUTES).forEach { minutes ->
            assertEquals(minutes, TimeEstimate.parse(TimeEstimate.format(minutes)))
        }
    }
}
