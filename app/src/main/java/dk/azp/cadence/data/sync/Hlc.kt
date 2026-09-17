package dk.azp.cadence.data.sync

import kotlin.math.max

/**
 * Hybrid logical clock. Stamps are fixed-width strings, so plain string comparison orders them: physical time first,
 * then a counter for events within the same millisecond, then the device id as a deterministic tiebreaker.
 */
class Hlc(
    private val deviceId: String,
    initialPhysical: Long,
    initialCounter: Int,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val persist: (physical: Long, counter: Int) -> Unit = { _, _ -> },
) {

    private var physical = initialPhysical
    private var counter = initialCounter

    /** Issues a stamp for a local event. */
    @Synchronized
    fun next(): String {
        val now = wallClock()
        if (now > physical) {
            physical = now
            counter = 0
        } else {
            counter++
        }
        persist(physical, counter)
        return format(physical, counter, deviceId)
    }

    /** Advances the clock past a stamp received from another device so later local stamps sort after it. */
    @Synchronized
    fun observe(remote: String) {
        val remotePhysical = physicalOf(remote)
        val remoteCounter = counterOf(remote)
        val now = wallClock()
        when {
            now > physical && now > remotePhysical -> {
                physical = now
                counter = 0
            }
            remotePhysical > physical -> {
                physical = remotePhysical
                counter = remoteCounter + 1
            }
            physical > remotePhysical -> counter++
            else -> counter = max(counter, remoteCounter) + 1
        }
        persist(physical, counter)
    }

    companion object {
        fun format(physical: Long, counter: Int, deviceId: String): String =
            String.format("%013d-%06x-%s", physical, counter, deviceId)

        fun physicalOf(stamp: String): Long = stamp.substring(0, 13).toLong()

        fun counterOf(stamp: String): Int = stamp.substring(14, 20).toInt(16)

        fun isNewer(candidate: String, existing: String): Boolean = candidate > existing
    }
}
