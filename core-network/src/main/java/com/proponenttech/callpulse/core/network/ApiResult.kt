package com.proponenttech.callpulse.core.network

/**
 * Appendix B says the app must handle every row of its error table explicitly. Modelling those
 * as distinct types means the compiler asks for each one at the call site, instead of them all
 * collapsing into a single "network error" toast.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val value: T) : ApiResult<T>

    /** 401 — token missing or expired. Clear the token, keep unsynced drafts (§11.2). */
    data object Unauthenticated : ApiResult<Nothing>

    /** 403 device_revoked — the remote kill switch. Wipe local data and lock the app (§11.2). */
    data object DeviceRevoked : ApiResult<Nothing>

    /** 409 consent_required — capture stays disabled until consent is recorded (decision #14). */
    data object ConsentRequired : ApiResult<Nothing>

    /** 426 — below `min_supported_version_code`. Block the app until it updates (§18.4). */
    data object UpdateRequired : ApiResult<Nothing>

    /** 422 — field errors, surfaced to the user rather than retried forever (§15). */
    data class ValidationFailed(
        val message: String,
        val errors: Map<String, List<String>>,
    ) : ApiResult<Nothing>

    /** 429 — includes Retry-After. Back off rather than hammering. */
    data class RateLimited(val retryAfterSeconds: Long?) : ApiResult<Nothing>

    /** Any other HTTP failure. */
    data class HttpError(val code: Int, val message: String) : ApiResult<Nothing>

    /** No connection, DNS failure, timeout — retryable, unlike the cases above. */
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}

/**
 * Every non-Success case is an `ApiResult<Nothing>`, so the cast is safe — but the compiler
 * cannot see that through the `when`, hence the suppression on the function rather than inline
 * (an annotation after `return` parses as a label in Kotlin).
 */
@Suppress("UNCHECKED_CAST")
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    else -> this as ApiResult<R>
}
