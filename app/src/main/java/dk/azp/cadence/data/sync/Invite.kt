package dk.azp.cadence.data.sync

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Everything a device needs to join a group, shown as a QR code or pasted as text. The host address is a hint for the
 * first sync only; later syncs find the peer through LAN discovery.
 */
data class Invite(
    val groupId: String,
    val groupName: String,
    val secret: String,
    val hostDeviceId: String,
    val host: String?,
    val port: Int?,
) {

    fun encode(): String {
        val params = buildList {
            add("g" to groupId)
            add("n" to groupName)
            add("s" to secret)
            add("d" to hostDeviceId)
            host?.let { add("h" to it) }
            port?.let { add("p" to it.toString()) }
        }
        return PREFIX + params.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }

    companion object {
        private const val PREFIX = "cadence://join?"

        fun parse(text: String): Invite? = runCatching { parseStrict(text) }.getOrNull()

        private fun parseStrict(text: String): Invite? {
            val trimmed = text.trim()
            val query = if (trimmed.startsWith(PREFIX)) trimmed.removePrefix(PREFIX) else return null
            val params = query.split('&').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else part.substring(0, index) to URLDecoder.decode(part.substring(index + 1), "UTF-8")
            }.toMap()
            val groupId = params["g"]
            val groupName = params["n"]
            val secret = params["s"]
            val hostDeviceId = params["d"]
            return if (groupId != null && groupName != null && secret != null && hostDeviceId != null) {
                Invite(groupId, groupName, secret, hostDeviceId, params["h"], params["p"]?.toIntOrNull())
            } else {
                null
            }
        }
    }
}
