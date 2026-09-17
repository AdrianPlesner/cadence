package dk.azp.cadence.data.sync

import dk.azp.cadence.data.db.ChangeEntity
import kotlinx.serialization.Serializable

@Serializable
data class ChangeDto(
    val originDevice: String,
    val seq: Long,
    val hlc: String,
    val entityType: String,
    val entityId: String,
    val payload: String,
) {
    fun toEntity(groupId: String): ChangeEntity = ChangeEntity(originDevice, seq, groupId, hlc, entityType, entityId, payload)
}

fun ChangeEntity.toDto(): ChangeDto = ChangeDto(originDevice, seq, hlc, entityType, entityId, payload)

/** First leg of a sync: the caller introduces itself and asks what the peer already has. */
@Serializable
data class HelloRequest(
    val deviceId: String,
    val deviceName: String,
    val listenPort: Int,
    val sentAt: Long,
)

@Serializable
data class HelloResponse(
    val deviceId: String,
    val deviceName: String,
    val sentAt: Long,
    val cursors: Map<String, Long>,
)

/** Second leg: the caller pushes what the peer is missing and states its own cursors so the peer can answer in kind. */
@Serializable
data class SyncRequest(
    val deviceId: String,
    val deviceName: String,
    val listenPort: Int,
    val sentAt: Long,
    val cursors: Map<String, Long>,
    val changes: List<ChangeDto>,
)

@Serializable
data class SyncResponse(
    val deviceId: String,
    val deviceName: String,
    val sentAt: Long,
    val changes: List<ChangeDto>,
)
