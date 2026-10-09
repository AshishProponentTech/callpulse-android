package com.proponenttech.callpulse.core.data.session

import com.proponenttech.callpulse.core.data.db.CallPulseDatabase
import com.proponenttech.callpulse.core.network.SessionListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** No dependency on repositories or network clients: safe to bind into ApiCall. */
@Singleton
class SessionState @Inject constructor(
    private val store: SecureStore,
    private val preferences: AppPreferences,
    private val database: CallPulseDatabase,
) : SessionListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = MutableStateFlow(store.token != null)
    val signedIn = active.asStateFlow()
    private val message = MutableStateFlow<String?>(null)
    val reason = message.asStateFlow()
    @Volatile private var revocationCleanup: Job? = null

    @Synchronized
    fun saveSession(token: String, expiresAt: String, userId: String) {
        store.token = token
        store.tokenExpiresAt = expiresAt
        store.userId = userId
        message.value = null
        active.value = true
    }

    @Synchronized
    override fun onUnauthenticated(sentToken: String?) {
        val current = store.token ?: return
        // Ignore responses from requests sent before token rotation.
        if (sentToken != "Bearer $current") return
        clear("Your session expired. Sign in again.")
    }

    @Synchronized
    override fun onDeviceRevoked() {
        clear("Access for this device has been revoked. Contact your administrator.")
        if (revocationCleanup?.isActive != true) {
            revocationCleanup = scope.launch {
                preferences.clear()
                database.clearAllTables()
            }
        }
    }

    suspend fun awaitCleanup() { revocationCleanup?.join() }

    @Synchronized
    fun clear(reason: String? = null) {
        store.clearSession()
        message.value = reason
        active.value = false
    }
}
