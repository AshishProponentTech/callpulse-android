package com.proponenttech.callpulse.core.network

import com.proponenttech.callpulse.core.network.dto.ApiEnvelope
import com.proponenttech.callpulse.core.network.dto.BootstrapResponse
import com.proponenttech.callpulse.core.network.dto.LoginRequest
import com.proponenttech.callpulse.core.network.dto.BatchRequest
import com.proponenttech.callpulse.core.network.dto.BatchResponse
import com.proponenttech.callpulse.core.network.dto.ConsentRequest
import com.proponenttech.callpulse.core.network.dto.DeviceStateDto
import com.proponenttech.callpulse.core.network.dto.HeartbeatRequest
import com.proponenttech.callpulse.core.network.dto.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * The §21 contract. Only the endpoints the app currently uses are declared; the rest arrive with
 * their stages so an unimplemented call cannot be made by accident.
 *
 * Every method returns `Response<T>` rather than the bare body, because Appendix B requires the
 * app to branch on the HTTP status and `code` — a generic "something went wrong" for a 409
 * `consent_required` is a support ticket waiting to happen.
 */
interface CallPulseApi {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<LoginResponse>>

    @POST("auth/refresh")
    suspend fun refresh(): Response<ApiEnvelope<LoginResponse>>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("bootstrap")
    suspend fun bootstrap(
        @Query("device_uuid") deviceUuid: String,
    ): Response<ApiEnvelope<BootstrapResponse>>

    /** Decision #14 — consent is a stored, versioned record; ingest is refused without it. */
    @POST("devices/consent")
    suspend fun recordConsent(@Body body: ConsentRequest): Response<ApiEnvelope<DeviceStateDto>>

    /** §12.1 — how a manager sees a permission was turned off, without the app nagging. */
    @POST("devices/heartbeat")
    suspend fun heartbeat(@Body body: HeartbeatRequest): Response<ApiEnvelope<DeviceStateDto>>

    /** §6.2.8 — always HTTP 200 with a per-row status array. */
    @POST("call-sessions/batch")
    suspend fun ingestBatch(@Body body: BatchRequest): Response<ApiEnvelope<BatchResponse>>
}
