package com.proponenttech.callpulse.core.data.session

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class AppPreferencesTest {
    @Test fun scanWatermarkChangesDoNotRetriggerConsentObservers() = runBlocking {
        val prefs = AppPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        prefs.clear()
        val events = Channel<String?>(Channel.UNLIMITED)
        val observer = launch { prefs.consentAcceptedAt.collect { events.send(it) } }
        try {
            withTimeout(5000) {
                assertNull(events.receive())
                prefs.recordConsent("version", "accepted")
                assertEquals("accepted", events.receive())
                prefs.setCallLogWatermark(1000)
                prefs.setLastScanAt(2000)
                assertNull(withTimeoutOrNull(500) { events.receive() })
                prefs.clearConsent()
                // A real consent change must still reach the observer.
                assertNull(events.receive())
            }
        } finally {
            observer.cancelAndJoin()
            prefs.clear()
            events.close()
        }
    }
}
