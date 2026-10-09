package com.proponenttech.callpulse.core.data.device

import android.os.Build
import com.proponenttech.callpulse.core.data.session.*
import com.proponenttech.callpulse.core.network.*
import com.proponenttech.callpulse.core.network.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val api: CallPulseApi,
    private val apiCall: ApiCall,
    private val secureStore: SecureStore,
    private val preferences: AppPreferences,
    private val permissions: PermissionState,
    private val config: ApiConfig,
) {
    suspend fun recordConsent(version: String): ApiResult<ConsentResponse> {
        val result = apiCall.execute {
            api.recordConsent(ConsentRequest(secureStore.deviceUuid, version))
        }.map { it.data }
        if (result is ApiResult.Success) preferences.recordConsent(version, result.value.acceptedAt)
        return result
    }

    suspend fun sendHeartbeat(): ApiResult<HeartbeatResponse> = apiCall.execute {
        api.heartbeat(HeartbeatRequest(
            deviceUuid = secureStore.deviceUuid,
            appVersion = config.appVersion,
            appVersionCode = config.appVersionCode,
            osVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            permissions = PermissionsDto(permissions.callLog, permissions.phoneState,
                permissions.notifications, permissions.batteryOptimizationIgnored),
        ))
    }.map { it.data }
}
