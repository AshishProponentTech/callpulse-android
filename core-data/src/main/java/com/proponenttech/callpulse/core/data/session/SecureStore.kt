package com.proponenttech.callpulse.core.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §11.3 — the token and the SQLCipher passphrase live in EncryptedSharedPreferences backed by
 * the Android Keystore. Never in DataStore plaintext, never in Room, never in a log.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_TOKEN) else putString(KEY_TOKEN, value)
        }.apply()

    var tokenExpiresAt: String?
        get() = prefs.getString(KEY_TOKEN_EXPIRES_AT, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_TOKEN_EXPIRES_AT) else putString(KEY_TOKEN_EXPIRES_AT, value)
        }.apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_USER_ID) else putString(KEY_USER_ID, value)
        }.apply()

    /**
     * §11.1 — generated once with UUID.randomUUID() on first launch and never regenerated.
     * Deliberately NOT ANDROID_ID, IMEI or any hardware identifier: those are either
     * unavailable on modern Android or are personal data we have no reason to hold.
     */
    val deviceUuid: String
        get() = prefs.getString(KEY_DEVICE_UUID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_UUID, it).apply()
        }

    /** SQLCipher passphrase — generated once, never leaves this file. */
    val databasePassphrase: String
        get() = prefs.getString(KEY_DB_PASSPHRASE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DB_PASSPHRASE, it).apply()
        }

    /**
     * §11.2 — on 401 the session is cleared but the local database is NOT touched, so unsynced
     * drafts survive re-login. The device_uuid and passphrase also survive: regenerating either
     * would orphan the server-side device record and make the encrypted database unreadable.
     */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .remove(KEY_USER_ID)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "callpulse_secure"
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        const val KEY_USER_ID = "user_uuid"
        const val KEY_DEVICE_UUID = "device_uuid"
        const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
