package com.proponenttech.callpulse.core.data.device

import android.os.Build
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.session.AppPreferences
import com.proponenttech.callpulse.core.data.session.SecureStore
import com.proponenttech.callpulse.core.network.ApiCall
import com.proponenttech.callpulse.core.network.ApiConfig
import com.proponenttech.callpulse.core.network.ApiResult
import com.proponenttech.callpulse.core.network.CallPulseApi
import com.proponenttech.callpulse.core.network.dto.ConsentRequest
import com.proponenttech.callpulse.core.network.dto.DeviceStateDto
import com.proponenttech.callpulse.core.network.dto.HeartbeatRequest
import com.proponenttech.callpulse.core.network.map
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private val dao: CallSessionDao,
) {

    /**
     * Decision #14 — consent is a record the server can later prove, not just a screen the
     * employee tapped through. Capture stays disabled until this returns 2xx.
     */
    suspend fun recordConsent(consentVersion: String): ApiResult<DeviceStateDto> {
        val result = apiCall.execute {
            api.recordConsent(
                ConsentRequest(
                    deviceUuid = secureStore.deviceUuid,
                    consentVersion = consentVersion,
                    acceptedAt = isoNow(),
                ),
            )
        }.map { it.data }

        if (result is ApiResult.Success) {
            preferences.cacheBootstrap(
                workdaySeconds = preferences.workdaySeconds.first(),
                consentVersion = result.value.consentVersion,
                consentAcceptedAt = result.value.consentAcceptedAt ?: isoNow(),
                minSupportedVersionCode = result.value.minSupportedVersionCode,
            )
        }

        return result
    }

    /** §12.1 — permission health, so managers see a disabled permission without nagging in-app. */
    suspend fun sendHeartbeat(): ApiResult<DeviceStateDto> {
        val lastScan = preferences.lastScanAtMillis.first().takeIf { it > 0 }

        return apiCall.execute {
            api.heartbeat(
                HeartbeatRequest(
                    deviceUuid = secureStore.deviceUuid,
                    appVersion = config.appVersion,
                    appVersionCode = config.appVersionCode,
                    osVersion = Build.VERSION.RELEASE,
                    sdkInt = Build.VERSION.SDK_INT,
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    callLogPermission = permissions.callLog,
                    phoneStatePermission = permissions.phoneState,
                    contactsPermission = permissions.contacts,
                    notificationPermission = permissions.notifications,
                    whatsappAssistEnabled = false, // Decision #2 — Phase 3, never true here.
                    batteryOptimizationIgnored = permissions.batteryOptimizationIgnored,
                    lastScanAt = lastScan?.let { isoOf(it) },
                    pendingSyncCount = dao.pendingCount(),
                ),
            )
        }.map { it.data }.also { result ->
            if (result is ApiResult.Success && result.value.revoked) {
                Timber.w("Server reports this device is revoked")
            }
        }
    }

    private fun isoNow(): String = isoOf(System.currentTimeMillis())

    /** Decision #8 — ISO-8601 with offset, matching what the API speaks. */
    private fun isoOf(millis: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
        )
}
