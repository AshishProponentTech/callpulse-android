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

// ---- devices (§21) ---------------------------------------------------------------------------

@Serializable
data class ConsentRequest(
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("consent_version") val consentVersion: String,
    @SerialName("accepted_at") val acceptedAt: String? = null,
)

@Serializable
data class HeartbeatRequest(
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("app_version_code") val appVersionCode: Int? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("sdk_int") val sdkInt: Int? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    @SerialName("call_log_permission") val callLogPermission: Boolean,
    @SerialName("phone_state_permission") val phoneStatePermission: Boolean,
    @SerialName("contacts_permission") val contactsPermission: Boolean,
    @SerialName("notification_permission") val notificationPermission: Boolean,
    @SerialName("whatsapp_assist_enabled") val whatsappAssistEnabled: Boolean = false,
    @SerialName("battery_optimization_ignored") val batteryOptimizationIgnored: Boolean? = null,
    @SerialName("last_scan_at") val lastScanAt: String? = null,
    @SerialName("last_call_captured_at") val lastCallCapturedAt: String? = null,
    @SerialName("pending_sync_count") val pendingSyncCount: Int? = null,
)

@Serializable
data class DeviceStateDto(
    @SerialName("device_uuid") val deviceUuid: String? = null,
    val revoked: Boolean = false,
    @SerialName("consent_version") val consentVersion: String? = null,
    @SerialName("consent_accepted_at") val consentAcceptedAt: String? = null,
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int = 1,
)

// ---- call session ingest (§21) ---------------------------------------------------------------

@Serializable
data class CallSessionPayload(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("project_id") val projectId: Long,
    @SerialName("task_id") val taskId: Long? = null,
    @SerialName("milestone_id") val milestoneId: Long? = null,
    @SerialName("phone_e164") val phoneE164: String? = null,
    @SerialName("phone_display") val phoneDisplay: String? = null,
    @SerialName("contact_name") val contactName: String? = null,
    val direction: String,
    val channel: String,
    @SerialName("sub_channel") val subChannel: String? = null,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("tz_offset_minutes") val tzOffsetMinutes: Int,
    @SerialName("duration_sec") val durationSec: Int,
    @SerialName("ring_sec") val ringSec: Int? = null,
    val answered: Boolean,
    @SerialName("is_personal") val isPersonal: Boolean = false,
    val confidence: String,
    val source: String,
    @SerialName("android_call_log_id") val androidCallLogId: String? = null,
    @SerialName("phone_account_id") val phoneAccountId: String? = null,
    val outcome: String? = null,
    val notes: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("captured_at") val capturedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

@Serializable
data class BatchRequest(val sessions: List<CallSessionPayload>)

/** §6.2.8 — HTTP 200 with a per-row status, so one bad row cannot lose the rest. */
@Serializable
data class BatchRowResult(
    @SerialName("client_uuid") val clientUuid: String,
    val status: String,
    val id: Long? = null,
    val errors: Map<String, List<String>>? = null,
)

@Serializable
data class BatchResponse(val results: List<BatchRowResult>)
