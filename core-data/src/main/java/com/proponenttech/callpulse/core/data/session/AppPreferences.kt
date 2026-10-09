package com.proponenttech.callpulse.core.data.session

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "callpulse_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    val consentVersion = context.dataStore.data.map { it[VERSION] }.distinctUntilChanged()
    val consentAcceptedAt = context.dataStore.data.map { it[ACCEPTED] }.distinctUntilChanged()
    val policyUrl = context.dataStore.data.map { it[POLICY] }.distinctUntilChanged()
    val minSupportedVersionCode = context.dataStore.data.map { it[MIN_VERSION] ?: 1 }.distinctUntilChanged()
    val cachedSchoolsJson = context.dataStore.data.map { it[SCHOOLS] }.distinctUntilChanged()
    val callLogWatermark = context.dataStore.data.map { it[WATERMARK] ?: 0L }.distinctUntilChanged()
    val lastScanAtMillis = context.dataStore.data.map { it[LAST_SCAN] ?: 0L }.distinctUntilChanged()

    suspend fun cacheSchools(json: String) { context.dataStore.edit { it[SCHOOLS] = json } }
    suspend fun setCallLogWatermark(millis: Long) {
        context.dataStore.edit { if (millis > (it[WATERMARK] ?: 0L)) it[WATERMARK] = millis }
    }
    suspend fun setLastScanAt(millis: Long) { context.dataStore.edit { it[LAST_SCAN] = millis } }
    suspend fun cacheBootstrap(consentVersion: String, consentAcceptedAt: String?, minSupportedVersionCode: Int, policyUrl: String) {
        context.dataStore.edit {
            it[VERSION] = consentVersion
            it[MIN_VERSION] = minSupportedVersionCode
            it[POLICY] = policyUrl
            if (consentAcceptedAt == null) it.remove(ACCEPTED) else it[ACCEPTED] = consentAcceptedAt
        }
    }
    suspend fun recordConsent(version: String, acceptedAt: String) {
        context.dataStore.edit { it[VERSION] = version; it[ACCEPTED] = acceptedAt }
    }
    suspend fun clearConsent() { context.dataStore.edit { it.remove(ACCEPTED) } }
    suspend fun clear() { context.dataStore.edit { it.clear() } }

    private companion object {
        val VERSION = stringPreferencesKey("consent_version")
        val ACCEPTED = stringPreferencesKey("consent_accepted_at")
        val POLICY = stringPreferencesKey("policy_url")
        val MIN_VERSION = intPreferencesKey("min_supported_version_code")
        val SCHOOLS = stringPreferencesKey("schools_json")
        val WATERMARK = longPreferencesKey("call_log_watermark")
        val LAST_SCAN = longPreferencesKey("last_scan_at")
    }
}
