package dk.azp.cadence

import dk.azp.cadence.data.sync.TaskPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun clearedEstimateIsStillWritten() {
        val encoded = json.parseToJsonElement(json.encodeToString(TaskPayload("Water plants"))).jsonObject
        assertEquals(JsonNull, encoded["estimateMinutes"])
    }

    @Test
    fun payloadFromOlderVersionDecodesWithoutEstimate() {
        val old = """{"name":"Water plants","cadenceDays":3,"categoryIds":[],"notifyWhenDue":true}"""
        assertNull(json.decodeFromString<TaskPayload>(old).estimateMinutes)
    }
}
