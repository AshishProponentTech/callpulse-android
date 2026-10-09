package com.proponenttech.callpulse.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Appendix B says the app must handle every row of the error table explicitly. These pin that
 * mapping, so a change to the envelope cannot silently collapse a specific failure into a
 * generic one.
 */
class ApiCallTest {

    private val apiCall = ApiCall(Json { ignoreUnknownKeys = true })

    private fun errorResponse(
        code: Int,
        body: String,
        headers: Headers = Headers.headersOf(),
    ): Response<String> = Response.error(
        body.toResponseBody("application/json".toMediaType()),
        okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("http://localhost/api/v1/test")
                .header("Authorization", "Bearer sent-token").build())
            .headers(headers)
            .build(),
    )

    @Test
    fun `success returns the body`() = runBlocking {
        val result = apiCall.execute { Response.success("ok") }
        assertEquals(ApiResult.Success("ok"), result)
    }

    @Test
    fun `401 maps to Unauthenticated`() = runBlocking {
        val result = apiCall.execute {
            errorResponse(401, """{"message":"Unauthenticated.","code":"unauthenticated"}""")
        }
        assertEquals(ApiResult.Unauthenticated, result)
    }

    @Test
    fun `403 device_revoked maps to DeviceRevoked, not a generic forbidden`() = runBlocking {
        val result = apiCall.execute {
            errorResponse(403, """{"message":"Revoked.","code":"device_revoked"}""")
        }
        assertEquals(ApiResult.DeviceRevoked, result)
    }

    @Test
    fun `403 forbidden is not treated as a revoked device`() = runBlocking {
        // A policy denial must not wipe the user's local data.
        val result = apiCall.execute {
            errorResponse(403, """{"message":"Nope.","code":"forbidden"}""")
        }
        assertTrue(result is ApiResult.HttpError)
    }

    @Test
    fun `409 consent_required maps to ConsentRequired`() = runBlocking {
        val result = apiCall.execute {
            errorResponse(409, """{"message":"Consent needed.","code":"consent_required"}""")
        }
        assertEquals(ApiResult.ConsentRequired, result)
    }

    @Test
    fun `426 maps to UpdateRequired`() = runBlocking {
        val result = apiCall.execute {
            errorResponse(426, """{"message":"Update.","code":"app_update_required"}""")
        }
        assertEquals(ApiResult.UpdateRequired, result)
    }

    @Test
    fun `422 carries the field errors through`() = runBlocking {
        val result = apiCall.execute {
            errorResponse(
                422,
                """{"message":"Invalid.","code":"validation_failed",""" +
                    """"errors":{"school_id":["The selected school is not accessible."]}}""",
            )
        }

        result as ApiResult.ValidationFailed
        assertEquals(
            "The selected school is not accessible.",
            result.errors["school_id"]?.first(),
        )
    }

    @Test
    fun `429 reads Retry-After`() = runBlocking {
        val result = apiCall.execute {
            errorResponse(
                429,
                """{"message":"Slow down.","code":"rate_limited"}""",
                Headers.headersOf("Retry-After", "42"),
            )
        }
        assertEquals(ApiResult.RateLimited(42L), result)
    }

    @Test
    fun `a non-JSON error body does not crash the caller`() = runBlocking {
        // A proxy returning an HTML 502 must surface as an HttpError, not an exception.
        val result = apiCall.execute { errorResponse(502, "<html>Bad Gateway</html>") }
        assertTrue(result is ApiResult.HttpError)
        assertEquals(502, (result as ApiResult.HttpError).code)
    }

    @Test
    fun `an IO failure is a retryable NetworkError`() = runBlocking {
        val result = apiCall.execute<String> { throw IOException("offline") }
        assertTrue(result is ApiResult.NetworkError)
    }

    @Test fun `listener receives sent token and device revocation`() = runBlocking {
        var sent: String? = null
        var revoked = false
        val listener = object : SessionListener {
            override fun onUnauthenticated(sentToken: String?) { sent = sentToken }
            override fun onDeviceRevoked() { revoked = true }
        }
        val call = ApiCall(Json, listener)
        call.execute { errorResponse(401, """{"code":"token_rotated"}""") }
        assertEquals("Bearer sent-token", sent)
        call.execute { errorResponse(403, """{"code":"device_revoked"}""") }
        assertTrue(revoked)
    }

    @Test fun `wrong password without errors map is readable`() = runBlocking {
        val result = apiCall.execute { errorResponse(422, """{"code":"auth_failed"}""") }
        result as ApiResult.ValidationFailed
        assertEquals("Email or password is incorrect.", result.message)
        assertTrue(result.errors.isEmpty())
    }
}
