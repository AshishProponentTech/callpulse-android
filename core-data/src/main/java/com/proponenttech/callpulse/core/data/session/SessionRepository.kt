package com.proponenttech.callpulse.core.data.session

import android.os.Build
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.network.*
import com.proponenttech.callpulse.core.network.dto.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val api: CallPulseApi,
    private val apiCall: ApiCall,
    private val secureStore: SecureStore,
    private val preferences: AppPreferences,
    private val config: ApiConfig,
    private val json: Json,
    private val dao: CallSessionDao,
    private val schoolStore: SchoolStore,
    val state: SessionState,
) {
    private val tokenMutex = Mutex()
    val isLoggedIn get() = secureStore.token != null
    val deviceUuid get() = secureStore.deviceUuid
    val selectedSchoolId = schoolStore.schoolId
    val cachedSchools = preferences.cachedSchoolsJson.map { raw ->
        raw?.let { runCatching { json.decodeFromString<List<SchoolDto>>(it) }.getOrNull() }.orEmpty()
    }

    suspend fun login(email: String, password: String): ApiResult<Unit> = tokenMutex.withLock {
        state.awaitCleanup()
        apiCall.execute { api.login(LoginRequest(email, password, currentDeviceInfo())) }.map {
            state.saveSession(it.data.token, it.data.expiresAt, it.data.user.id)
        }
    }

    /** Serialise rotation; another foreground/worker refresh rechecks the new expiry. */
    suspend fun refreshIfExpiring(): ApiResult<Unit> = tokenMutex.withLock {
        if (!isLoggedIn) return@withLock ApiResult.Unauthenticated
        val expiry = runCatching { OffsetDateTime.parse(secureStore.tokenExpiresAt).toInstant() }.getOrNull()
        if (expiry != null && expiry.isAfter(java.time.Instant.now().plusSeconds(30L * 86400))) {
            return@withLock ApiResult.Success(Unit)
        }
        val previousToken = secureStore.token
        apiCall.execute { api.refresh() }.map {
            // A revoke/logout during refresh must not resurrect the session.
            if (secureStore.token == previousToken) {
                state.saveSession(it.data.token, it.data.expiresAt, it.data.user.id)
            }
        }
    }

    suspend fun bootstrap(): ApiResult<BootstrapResponse> {
        val result = apiCall.execute { api.bootstrap(deviceUuid) }.map { it.data }
        if (result is ApiResult.Success) {
            val data = result.value
            preferences.cacheBootstrap(data.consent.version, data.consent.acceptedAt,
                data.app.minSupportedVersionCode, data.consent.policyUrl)
            preferences.cacheSchools(json.encodeToString(data.schools))
            val remembered = selectedSchoolId.value?.takeIf { id -> data.schools.any { it.id == id } }
            val school = remembered ?: data.schools.singleOrNull()?.id
            if (school == null) schoolStore.select(null) else setSchool(school)
        }
        return result
    }

    suspend fun setSchool(id: String) {
        schoolStore.select(id)
        dao.assignSchoolToUnassigned(id)
    }

    suspend fun logout() = tokenMutex.withLock {
        apiCall.execute { api.logout() }
        state.clear()
        preferences.clear()
        schoolStore.select(null)
    }

    private fun currentDeviceInfo() = DeviceInfoDto(
        deviceUuid, config.appVersion, config.appVersionCode,
        Build.VERSION.RELEASE ?: "unknown", Build.VERSION.SDK_INT,
        Build.MANUFACTURER ?: "unknown", Build.MODEL ?: "unknown",
    )
}
