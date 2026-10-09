package com.proponenttech.callpulse.core.data.session

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.proponenttech.callpulse.core.data.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SessionStateTest {
    @Test fun stale401KeepsRotatedSessionAndRevocationClearsDataWithoutDeletingKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureStore(context)
        val preferences = AppPreferences(context)
        val db = Room.inMemoryDatabaseBuilder(context, CallPulseDatabase::class.java).build()
        try {
            val state = SessionState(store, preferences, db)
            val key = store.databasePassphrase
            val device = store.deviceUuid
            state.saveSession("new-token", "2027-01-01T00:00:00Z", "user-uuid")
            state.onUnauthenticated("Bearer old-token")
            assertTrue(state.signedIn.value)
            assertEquals("new-token", store.token)
            state.onUnauthenticated("Bearer new-token")
            assertFalse(state.signedIn.value)
            assertNull(store.token)

            state.saveSession("another-token", "2027-01-01T00:00:00Z", "user-uuid")
            preferences.recordConsent("version", "accepted")
            db.personalNumberDao().insert(PersonalNumberEntity("+919876543210", 1L))
            withContext(Dispatchers.Main) { state.onDeviceRevoked() }
            state.awaitCleanup()
            assertFalse(state.signedIn.value)
            assertNull(preferences.consentAcceptedAt.first())
            assertFalse(db.personalNumberDao().contains("+919876543210"))
            assertEquals(key, store.databasePassphrase)
            assertEquals(device, store.deviceUuid)
        } finally {
            store.clearSession()
            preferences.clear()
            db.close()
        }
    }
}
