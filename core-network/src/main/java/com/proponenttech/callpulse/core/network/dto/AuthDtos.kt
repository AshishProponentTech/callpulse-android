package com.proponenttech.callpulse.core.network.dto

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

@Serializable data class ApiEnvelope<T>(val data: T)
@Serializable data class DeviceInfoDto(
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("app_version_code") val appVersionCode: Int,
    @SerialName("os_version") val osVersion: String,
    @SerialName("sdk_int") val sdkInt: Int,
    val manufacturer: String,
    val model: String,
)
@Serializable data class LoginRequest(val email: String, val password: String, val device: DeviceInfoDto)
@Serializable data class LoginResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
    val user: UserDto,
)
@Serializable data class UserDto(val id: String, val name: String, val email: String? = null)
@Serializable data class SchoolDto(val id: String, val name: String)
@Serializable data class ConsentDto(
    val version: String,
    @SerialName("policy_url") val policyUrl: String,
    @SerialName("accepted_at") val acceptedAt: String? = null,
)

/** Backend emits integers although the generated contract says strings. */
object VersionCodeSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("VersionCode", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.int else decoder.decodeInt()
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}
@Serializable data class AppVersionDto(
    @Serializable(with = VersionCodeSerializer::class)
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int,
    @Serializable(with = VersionCodeSerializer::class)
    @SerialName("latest_version_code") val latestVersionCode: Int,
)
@Serializable data class BootstrapResponse(
    val user: UserDto,
    @SerialName("server_time") val serverTime: String,
    val timezone: String,
    val consent: ConsentDto,
    val app: AppVersionDto,
    val schools: List<SchoolDto>,
)
@Serializable data class ApiErrorDto(
    val message: String? = null,
    val code: String? = null,
    val errors: Map<String, List<String>>? = null,
)
@Serializable data class ConsentRequest(
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("consent_version") val consentVersion: String,
)
@Serializable data class ConsentResponse(@SerialName("accepted_at") val acceptedAt: String)
@Serializable data class PermissionsDto(
    @SerialName("read_call_log") val readCallLog: Boolean,
    @SerialName("read_phone_state") val readPhoneState: Boolean,
    @SerialName("post_notifications") val postNotifications: Boolean,
    @SerialName("battery_optimization_exempt") val batteryOptimizationExempt: Boolean,
)
@Serializable data class HeartbeatRequest(
    @SerialName("device_uuid") val deviceUuid: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("app_version_code") val appVersionCode: Int? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("sdk_int") val sdkInt: Int? = null,
    val permissions: PermissionsDto? = null,
)
@Serializable data class HeartbeatResponse(@SerialName("server_time") val serverTime: String)
@Serializable data class CallPayload(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("school_id") val schoolId: String,
    @SerialName("android_call_log_id") val androidCallLogId: Long?,
    @SerialName("phone_e164") val phoneE164: String,
    @SerialName("contact_name") val contactName: String?,
    val direction: String,
    val answered: Boolean,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("duration_sec") val durationSec: Int,
)
@Serializable data class BatchRequest(val calls: List<CallPayload>)
@Serializable data class LeadRefDto(val id: String, val name: String, val status: String)
@Serializable data class BatchRowResult(
    @SerialName("client_uuid") val clientUuid: String? = null,
    val status: String,
    val id: String? = null,
    val lead: LeadRefDto? = null,
    val code: String? = null,
)
@Serializable data class BatchResponse(val results: List<BatchRowResult>)
