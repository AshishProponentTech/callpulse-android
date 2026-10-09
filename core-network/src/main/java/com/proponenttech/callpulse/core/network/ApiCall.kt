package com.proponenttech.callpulse.core.network

import com.proponenttech.callpulse.core.network.dto.ApiErrorDto
import kotlinx.serialization.json.Json
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Maps an HTTP response onto [ApiResult], translating the Appendix B error table once so no
 * caller has to re-derive it.
 */
class ApiCall(private val json: Json, private val listener: SessionListener? = null) {

    suspend fun <T> execute(block: suspend () -> Response<T>): ApiResult<T> = try {
        block().toApiResult()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        // Genuinely retryable: no connection, DNS failure, timeout.
        ApiResult.NetworkError(e)
    } catch (e: Exception) {
        Timber.e(e, "Unexpected failure during an API call")
        ApiResult.NetworkError(e)
    }

    private fun <T> Response<T>.toApiResult(): ApiResult<T> {
        if (isSuccessful) {
            val body = body()
            return if (body != null) {
                ApiResult.Success(body)
            } else {
                @Suppress("UNCHECKED_CAST")
                ApiResult.Success(Unit as T)
            }
        }

        val error = parseError()

        return when {
            code() == 401 -> {
                listener?.onUnauthenticated(raw().request.header("Authorization"))
                ApiResult.Unauthenticated
            }
            code() == 403 && error?.code == "device_revoked" -> {
                listener?.onDeviceRevoked()
                ApiResult.DeviceRevoked
            }
            code() == 409 && error?.code == "consent_required" -> ApiResult.ConsentRequired
            code() == 426 -> ApiResult.UpdateRequired
            code() == 422 -> ApiResult.ValidationFailed(
                message = if (error?.code == "auth_failed") "Email or password is incorrect." else error?.message ?: "The server rejected the request.",
                errors = error?.errors.orEmpty(),
            )

            code() == 429 -> ApiResult.RateLimited(
                retryAfterSeconds = headers()["Retry-After"]?.toLongOrNull(),
            )

            else -> ApiResult.HttpError(
                code = code(),
                message = error?.message ?: "Request failed (${code()}).",
            )
        }
    }

    private fun Response<*>.parseError(): ApiErrorDto? = try {
        errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<ApiErrorDto>(it) }
    } catch (e: Exception) {
        // A non-JSON error body (a proxy's HTML 502, say) must not crash the caller.
        Timber.w(e, "Could not parse the error body")
        null
    }
}
