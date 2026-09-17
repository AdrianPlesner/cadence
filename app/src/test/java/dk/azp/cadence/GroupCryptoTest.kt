package dk.azp.cadence

import dk.azp.cadence.data.sync.GroupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupCryptoTest {

    @Test
    fun decryptsWithTheSameSecret() {
        val crypto = GroupCrypto("secret")
        assertEquals("hello", crypto.decrypt(crypto.encrypt("hello")))
    }

    @Test
    fun rejectsOtherSecretsAndGarbage() {
        val message = GroupCrypto("secret").encrypt("hello")
        assertNull(GroupCrypto("other").decrypt(message))
        assertNull(GroupCrypto("secret").decrypt("not base64!"))
    }
}
