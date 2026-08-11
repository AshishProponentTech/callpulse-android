package com.proponenttech.callpulse.core.network

import com.proponenttech.callpulse.core.network.dto.ApiEnvelope
import com.proponenttech.callpulse.core.network.dto.BootstrapResponse
import com.proponenttech.callpulse.core.network.dto.LoginRequest
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
}
