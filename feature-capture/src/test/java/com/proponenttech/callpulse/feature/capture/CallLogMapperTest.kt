package com.proponenttech.callpulse.feature.capture

import android.provider.CallLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Appendix A, pinned. These constants are compile-time ints, so the mapping is testable without
 * a device — which matters, because getting a direction wrong silently corrupts every report
 * built on top of it.
 */
class CallLogMapperTest {

    @Test
    fun `incoming with talk time is inbound and answered`() {
        val mapped = CallLogMapper.mapType(CallLog.Calls.INCOMING_TYPE, durationSec = 400)
        assertEquals("inbound", mapped.direction)
        assertTrue(mapped.answered)
        assertEquals("high", mapped.confidence)
    }

    @Test
    fun `incoming with zero duration is not answered`() {
        val mapped = CallLogMapper.mapType(CallLog.Calls.INCOMING_TYPE, durationSec = 0)
        assertFalse(mapped.answered)
    }

    @Test
    fun `outgoing unanswered still logs as outbound with zero duration`() {
        // QA #3 — an unanswered outgoing call must still be captured, not dropped.
        val mapped = CallLogMapper.mapType(CallLog.Calls.OUTGOING_TYPE, durationSec = 0)
        assertEquals("outbound", mapped.direction)
        assertFalse(mapped.answered)
    }

    @Test
    fun `missed maps to missed with a no_answer outcome`() {
        val mapped = CallLogMapper.mapType(CallLog.Calls.MISSED_TYPE, durationSec = 0)
        assertEquals("missed", mapped.direction)
        assertFalse(mapped.answered)
        assertEquals("no_answer", mapped.outcome)
    }

    @Test
    fun `rejected maps to rejected`() {
        val mapped = CallLogMapper.mapType(CallLog.Calls.REJECTED_TYPE, durationSec = 0)
        assertEquals("rejected", mapped.direction)
        assertFalse(mapped.answered)
    }

    @Test
    fun `voicemail is inbound, unanswered, medium confidence`() {
        val mapped = CallLogMapper.mapType(CallLog.Calls.VOICEMAIL_TYPE, durationSec = 30)
        assertEquals("inbound", mapped.direction)
        assertFalse(mapped.answered)
        assertEquals("medium", mapped.confidence)
        assertEquals("other", mapped.outcome)
    }

    @Test
    fun `answered externally is low confidence and not answered here`() {
        // The call was picked up on another device, so this handset never had the audio.
        val mapped = CallLogMapper.mapType(CallLog.Calls.ANSWERED_EXTERNALLY_TYPE, durationSec = 120)
        assertEquals("inbound", mapped.direction)
        assertFalse(mapped.answered)
        assertEquals("low", mapped.confidence)
    }

    @Test
    fun `blocked calls are skipped entirely`() {
        assertTrue(CallLogMapper.shouldSkip(CallLog.Calls.BLOCKED_TYPE))
        assertFalse(CallLogMapper.shouldSkip(CallLog.Calls.INCOMING_TYPE))
    }

    @Test
    fun `an unrecognised type is flagged rather than guessed`() {
        // Appendix A insists the else branch is handled; silently mapping it to "inbound"
        // would invent data.
        val mapped = CallLogMapper.mapType(9999, durationSec = 10)
        assertEquals("unknown", mapped.direction)
        assertTrue(mapped.isUnknownType)
        assertEquals("low", mapped.confidence)
    }

    @Test
    fun `ended_at is start plus connected duration`() {
        val start = 1_700_000_000_000L
        assertEquals(start + 400_000L, CallLogMapper.endedAtMillis(start, 400))
    }

    @Test
    fun `a zero duration call ends when it started`() {
        val start = 1_700_000_000_000L
        assertEquals(start, CallLogMapper.endedAtMillis(start, 0))
    }
}
