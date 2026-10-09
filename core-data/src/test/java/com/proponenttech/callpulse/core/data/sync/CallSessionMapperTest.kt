package com.proponenttech.callpulse.core.data.sync

import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.OffsetDateTime

class CallSessionMapperTest {
    private fun call() = CallSessionEntity(
        clientUuid = "call", schoolId = "school", phoneE164 = "+919876543210",
        phoneDisplay = "9876543210", contactName = null,
        direction = "outbound", channel = "cellular", startedAtMillis = 1000,
        endedAtMillis = 6000, tzOffsetMinutes = 330, durationSec = 5,
        answered = true, confidence = "high", source = "call_log", deviceUuid = "device",
        androidCallLogId = "42", phoneAccountId = null, capturedAtMillis = 7000,
    )
    @Test fun mapsUuidSchoolNumericCallLogAndTimestamps() {
        val payload = requireNotNull(CallSessionMapper.toPayload(call()))
        assertEquals("school", payload.schoolId)
        assertEquals(42L, payload.androidCallLogId)
        assertEquals(1000L, OffsetDateTime.parse(payload.startedAt).toInstant().toEpochMilli())
        assertEquals(6000L, OffsetDateTime.parse(payload.endedAt).toInstant().toEpochMilli())
    }
    @Test fun missedAndRejectedNeverSendAnsweredOrDuration() {
        for (direction in listOf("missed", "rejected")) {
            val payload = requireNotNull(CallSessionMapper.toPayload(call().copy(direction = direction)))
            assertFalse(payload.answered)
            assertEquals(0, payload.durationSec)
            assertEquals(payload.startedAt, payload.endedAt)
        }
    }
    @Test fun skipsPrivateUnknownAndPersonalNumbers() {
        assertNotNull(CallSessionMapper.skipReason(call().copy(phoneE164 = null)))
        assertNotNull(CallSessionMapper.skipReason(call().copy(direction = "unknown")))
        assertNotNull(CallSessionMapper.skipReason(call(), personalNumber = true))
        assertNull(CallSessionMapper.skipReason(call()))
        assertNull(CallSessionMapper.toPayload(call().copy(phoneE164 = null)))
    }
    @Test fun waitsForSchoolAndHandlesNonNumericCallLogId() {
        assertNull(CallSessionMapper.toPayload(call().copy(schoolId = null)))
        assertNull(CallSessionMapper.toPayload(call().copy(androidCallLogId = "invalid"))?.androidCallLogId)
    }
}
