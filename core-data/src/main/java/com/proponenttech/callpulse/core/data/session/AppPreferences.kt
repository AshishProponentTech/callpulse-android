package com.proponenttech.callpulse.core.data.session

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "callpulse_prefs")

/**
 * Non-sensitive app state (§11.4). Anything secret belongs in [SecureStore] instead.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * §11.4 — the sticky project, held on-device only. Changing it affects FUTURE calls only;
     * historical drafts are never rewritten.
     */
    val defaultProjectId: Flow<Long?> = context.dataStore.data
        .map { it[KEY_DEFAULT_PROJECT_ID]?.takeIf { id -> id > 0 } }

    val workdaySeconds: Flow<Int> = context.dataStore.data
        .map { it[KEY_WORKDAY_SECONDS] ?: DEFAULT_WORKDAY_SECONDS }

    val consentVersion: Flow<String?> = context.dataStore.data.map { it[KEY_CONSENT_VERSION] }

    val consentAcceptedAt: Flow<String?> = context.dataStore.data.map { it[KEY_CONSENT_ACCEPTED_AT] }

    val minSupportedVersionCode: Flow<Int> = context.dataStore.data
        .map { it[KEY_MIN_SUPPORTED_VERSION_CODE] ?: 1 }

    /**
     * The projects the user may log calls against, cached from /bootstrap so the review sheet
     * has something to show offline. Serialised rather than modelled in Room because it is
     * server-owned reference data, not a draft — Room is a draft + offline queue cache only (§10).
     */
    val cachedProjectsJson: Flow<String?> = context.dataStore.data.map { it[KEY_PROJECTS_JSON] }

    suspend fun cacheProjects(json: String) {
        context.dataStore.edit { it[KEY_PROJECTS_JSON] = json }
    }

    suspend fun setDefaultProjectId(projectId: Long?) {
        context.dataStore.edit { prefs ->
            if (projectId == null) prefs.remove(KEY_DEFAULT_PROJECT_ID)
            else prefs[KEY_DEFAULT_PROJECT_ID] = projectId
        }
    }

    /** Cached from GET /bootstrap so a cold start has config before the first network call. */
    suspend fun cacheBootstrap(
        workdaySeconds: Int,
        consentVersion: String?,
        consentAcceptedAt: String?,
        minSupportedVersionCode: Int,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WORKDAY_SECONDS] = workdaySeconds
            prefs[KEY_MIN_SUPPORTED_VERSION_CODE] = minSupportedVersionCode
            consentVersion?.let { prefs[KEY_CONSENT_VERSION] = it }
                ?: prefs.remove(KEY_CONSENT_VERSION)
            consentAcceptedAt?.let { prefs[KEY_CONSENT_ACCEPTED_AT] = it }
                ?: prefs.remove(KEY_CONSENT_ACCEPTED_AT)
        }
    }

    /** Cleared on logout. Not called on a plain 401 — see SecureStore.clearSession. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        const val DEFAULT_WORKDAY_SECONDS = 28800

        val KEY_DEFAULT_PROJECT_ID: Preferences.Key<Long> = longPreferencesKey("default_project_id")
        val KEY_WORKDAY_SECONDS: Preferences.Key<Int> = intPreferencesKey("workday_seconds")
        val KEY_CONSENT_VERSION: Preferences.Key<String> = stringPreferencesKey("consent_version")
        val KEY_CONSENT_ACCEPTED_AT: Preferences.Key<String> = stringPreferencesKey("consent_accepted_at")
        val KEY_MIN_SUPPORTED_VERSION_CODE: Preferences.Key<Int> =
            intPreferencesKey("min_supported_version_code")
        val KEY_PROJECTS_JSON: Preferences.Key<String> = stringPreferencesKey("projects_json")
    }
}
