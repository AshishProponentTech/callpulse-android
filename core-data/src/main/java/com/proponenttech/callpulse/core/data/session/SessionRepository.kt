package com.proponenttech.callpulse.core.data.session

import android.os.Build
import com.proponenttech.callpulse.core.network.ApiCall
import com.proponenttech.callpulse.core.network.ApiConfig
import com.proponenttech.callpulse.core.network.ApiResult
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.network.CallPulseApi
import com.proponenttech.callpulse.core.network.dto.BootstrapResponse
import com.proponenttech.callpulse.core.network.dto.DeviceInfoDto
import com.proponenttech.callpulse.core.network.dto.LoginRequest
import com.proponenttech.callpulse.core.network.dto.ProjectDto
import com.proponenttech.callpulse.core.network.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §11 — login, bootstrap and the token lifecycle.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: CallPulseApi,
    private val apiCall: ApiCall,
    private val secureStore: SecureStore,
    private val preferences: AppPreferences,
    private val config: ApiConfig,
    private val json: Json,
    private val dao: CallSessionDao,
) {

    val isLoggedIn: Boolean get() = secureStore.token != null

    val deviceUuid: String get() = secureStore.deviceUuid

    val cachedProjects: Flow<List<ProjectDto>> = preferences.cachedProjectsJson.map { raw ->
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString<List<ProjectDto>>(raw) }.getOrElse {
                Timber.w(it, "Cached projects could not be read; treating as empty")
                emptyList()
            }
        }
    }

    val defaultProjectId: Flow<Long?> = preferences.defaultProjectId

    suspend fun login(email: String, password: String): ApiResult<Unit> {
        val request = LoginRequest(
            email = email,
            password = password,
            device = currentDeviceInfo(),
        )

        return apiCall.execute { api.login(request) }.map { envelope ->
            val data = envelope.data
            secureStore.token = data.token
            secureStore.tokenExpiresAt = data.expiresAt
            secureStore.userId = data.user.id
        }
    }

    /**
     * §11.1 — one call after login: projects, workday seconds, consent version, feature flags
     * and the minimum supported app version, all cached for a cold start.
     */
    @Suppress("UNCHECKED_CAST") // Non-Success cases are ApiResult<Nothing>; see ApiResult.map.
    suspend fun bootstrap(): ApiResult<BootstrapResponse> {
        val result = apiCall.execute { api.bootstrap(secureStore.deviceUuid) }

        if (result is ApiResult.Success) {
            val data = result.value.data

            preferences.cacheBootstrap(
                workdaySeconds = data.workdaySeconds,
                consentVersion = data.consent.version,
                consentAcceptedAt = data.consent.acceptedAt,
                minSupportedVersionCode = data.app.minSupportedVersionCode,
            )
            preferences.cacheProjects(json.encodeToString(data.projects))

            /*
             * §11.4 — if the remembered project has disappeared from /projects/mine, drop it and
             * make the user choose again rather than silently logging calls to a project they
             * are no longer on.
             */
            val remembered = preferences.defaultProjectId.first()
            if (remembered != null && data.projects.none { it.id == remembered }) {
                Timber.i("Default project %d is no longer accessible; clearing it", remembered)
                preferences.setDefaultProjectId(null)
            }

            return ApiResult.Success(data)
        }

        return result as ApiResult<BootstrapResponse>
    }

    /**
     * Choosing a project also rescues any draft captured before one existed. Without this a call
     * made during setup could never sync — rule 7 forbids a call without a project, so it would
     * sit in the queue forever with no way out.
     */
    suspend fun setDefaultProject(projectId: Long?) {
        preferences.setDefaultProjectId(projectId)

        if (projectId != null) {
            val filled = dao.assignProjectToUnassigned(projectId)
            if (filled > 0) Timber.i("Assigned a project to %d previously stuck draft(s)", filled)
        }
    }

    suspend fun logout() {
        // Best effort: if the server is unreachable the local session still goes.
        runCatching { apiCall.execute { api.logout() } }
        secureStore.clearSession()
        preferences.clear()
    }

    /**
     * §11.2 — on 401 clear the token and force re-login, but do NOT clear the local database.
     * Unsynced drafts must survive and re-attach to the same user.
     */
    fun onUnauthenticated() {
        Timber.w("401 received - clearing the session, keeping local drafts")
        secureStore.clearSession()
    }

    /** §11.2 — on 403 device_revoked, wipe everything and lock the app. */
    fun onDeviceRevoked() {
        Timber.w("Device revoked - wiping local data")
        secureStore.wipeEverything()
    }

    private fun currentDeviceInfo() = DeviceInfoDto(
        deviceUuid = secureStore.deviceUuid,
        platform = "android",
        appVersion = config.appVersion,
        appVersionCode = config.appVersionCode,
        osVersion = Build.VERSION.RELEASE ?: "unknown",
        sdkInt = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER ?: "unknown",
        model = Build.MODEL ?: "unknown",
    )
}
