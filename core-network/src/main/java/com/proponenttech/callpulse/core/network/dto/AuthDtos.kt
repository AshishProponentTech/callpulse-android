package com.proponenttech.callpulse.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for §21. Field names must match the frozen contract exactly — this is the whole
 * agreement between the two repositories, so nothing here gets renamed for convenience.
 *
 * Every response is wrapped in `data` by the Laravel API Resources.
 */
@Serializable
data class ApiEnvelope<T>(
    val data: T,
)

@Serializable
data class DeviceInfoDto(
    @SerialName("device_uuid") val deviceUuid: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    @SerialName("app_version_code") val appVersionCode: Int,
    @SerialName("os_version") val osVersion: String,
    @SerialName("sdk_int") val sdkInt: Int,
    val manufacturer: String,
    val model: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val device: DeviceInfoDto,
)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String? = null,
    val roles: List<String?> = emptyList(),
)

@Serializable
data class ProjectDto(
    val id: Long,
    val name: String,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ConsentDto(
    val version: String? = null,
    @SerialName("policy_url") val policyUrl: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
)

@Serializable
data class AppVersionDto(
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int,
    @SerialName("latest_version_code") val latestVersionCode: Int,
)

@Serializable
data class FeatureFlagsDto(
    // Decision #2 — Phase 3. The server sends false and this build must never act on true.
    @SerialName("whatsapp_assist") val whatsappAssist: Boolean = false,
    @SerialName("manual_whatsapp_log") val manualWhatsappLog: Boolean = true,
)

@Serializable
data class BootstrapResponse(
    val user: UserDto,
    @SerialName("server_time") val serverTime: String,
    val timezone: String,
    @SerialName("workday_seconds") val workdaySeconds: Int,
    val consent: ConsentDto,
    val app: AppVersionDto,
    val features: FeatureFlagsDto,
    val projects: List<ProjectDto>,
)

/** Appendix B — every failure carries a stable `code` the app branches on. */
@Serializable
data class ApiErrorDto(
    val message: String? = null,
    val code: String? = null,
    val errors: Map<String, List<String>>? = null,
)
