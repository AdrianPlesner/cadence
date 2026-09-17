package dk.azp.cadence

import dk.azp.cadence.data.sync.Invite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteTest {

    @Test
    fun roundTripsAllFields() {
        val invite = Invite("g1", "Kitchen & garden", "s3cr3t/+=", "d1", "192.168.1.20", 43210)
        assertEquals(invite, Invite.parse(invite.encode()))
    }

    @Test
    fun roundTripsWithoutAddress() {
        val invite = Invite("g1", "Home", "secret", "d1", null, null)
        assertEquals(invite, Invite.parse(invite.encode()))
    }

    @Test
    fun rejectsForeignText() {
        assertNull(Invite.parse("https://example.com/?g=1"))
        assertNull(Invite.parse("cadence://join?g=1&n=x"))
    }
}
