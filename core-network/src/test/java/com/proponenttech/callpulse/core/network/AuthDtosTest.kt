package com.proponenttech.callpulse.core.network

import com.proponenttech.callpulse.core.network.dto.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AuthDtosTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private fun bootstrap(min: String, latest: String) = """
        {"data":{"user":{"id":"user-uuid","name":"Staff","email":"staff@example.com"},
        "server_time":"2026-10-09T12:00:00Z","timezone":"Asia/Kolkata",
        "consent":{"version":"2026-10-09","policy_url":"http://localhost:8123/api/v1/mobile/policy","accepted_at":null},
        "app":{"min_supported_version_code":$min,"latest_version_code":$latest},
        "schools":[{"id":"school-uuid","name":"School"}]}}
    """.trimIndent()

    @Test fun integerVersionCodes() {
        val data = json.decodeFromString<ApiEnvelope<BootstrapResponse>>(bootstrap("1", "3")).data
        assertEquals(1, data.app.minSupportedVersionCode)
        assertEquals(3, data.app.latestVersionCode)
        assertEquals("school-uuid", data.schools.single().id)
        assertNull(data.consent.acceptedAt)
    }

    @Test fun stringVersionCodes() {
        val data = json.decodeFromString<ApiEnvelope<BootstrapResponse>>(bootstrap("\"1\"", "\"3\"")).data
        assertEquals(1, data.app.minSupportedVersionCode)
        assertEquals(3, data.app.latestVersionCode)
    }

    @Test fun batchResultsIncludeLeadAndNullUuid() {
        val data = json.decodeFromString<ApiEnvelope<BatchResponse>>("""
            {"data":{"results":[
            {"client_uuid":null,"status":"rejected","id":null,"lead":null,"code":"invalid_call"},
            {"client_uuid":"call-uuid","status":"accepted","id":"server-uuid",
             "lead":{"id":"lead-uuid","name":"Student","status":"new"}}
            ]}}
        """).data
        assertNull(data.results[0].clientUuid)
        assertEquals("invalid_call", data.results[0].code)
        assertEquals("Student", data.results[1].lead?.name)
    }

    @Test fun requiredFalseAndZeroFieldsAreSentWithoutEncodeDefaults() {
        val call = CallPayload("call", "school", null, "+919876543210", null,
            "missed", false, "2026-10-09T10:00:00Z", "2026-10-09T10:00:00Z", 0)
        val encoded = json.parseToJsonElement(json.encodeToString(BatchRequest(listOf(call))))
            .jsonObject["calls"]!!.jsonArray.single().jsonObject
        assertEquals(JsonPrimitive(false), encoded["answered"])
        assertEquals(JsonPrimitive(0), encoded["duration_sec"])
        val device = DeviceInfoDto("device", "1", 1, "13", 33, "Redmi", "Phone")
        assertFalse(json.encodeToString(device).contains("platform"))
        val permissions = PermissionsDto(false, false, false, false)
        val fields = json.parseToJsonElement(json.encodeToString(permissions)).jsonObject
        assertEquals(4, fields.size)
    }
}
