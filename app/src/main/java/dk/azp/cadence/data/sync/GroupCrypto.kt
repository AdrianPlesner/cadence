package dk.azp.cadence.data.sync

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts sync traffic with a key derived from the group secret. Anyone on the LAN without the secret can neither read
 * nor forge messages; possession of the secret is what makes a device a member.
 */
class GroupCrypto(secret: String) {

    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)), "AES")

    fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + cipherText)
    }

    /** Returns null when the message was not produced with this group's secret. */
    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH))
        String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }.getOrNull()

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private val random = SecureRandom()

        fun newSecret(): String {
            val bytes = ByteArray(32).also { random.nextBytes(it) }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
