package com.proponenttech.callpulse.feature.capture

import android.provider.CallLog
import com.proponenttech.callpulse.core.common.PhoneNumbers

/**
 * Appendix A — the CallLog.Calls.TYPE to direction mapping, and §13.4's field semantics.
 *
 * This is deliberately a pure function with no Android dependencies beyond the framework
 * constants, so the whole mapping is unit-testable without a device.
 */
object CallLogMapper {

    /** Appendix A: BLOCKED_TYPE is skipped entirely and never logged. */
    fun shouldSkip(type: Int): Boolean = type == CallLog.Calls.BLOCKED_TYPE

    data class Mapped(
        val direction: String,
        val answered: Boolean,
        val confidence: String,
        val outcome: String?,
        /** True when the type was unrecognised — §13.4 wants a capture_gap telemetry event. */
        val isUnknownType: Boolean = false,
    )

    fun mapType(type: Int, durationSec: Int): Mapped = when (type) {
        CallLog.Calls.INCOMING_TYPE -> Mapped(
            direction = "inbound",
            answered = durationSec > 0,
            confidence = "high",
            outcome = null,
        )

        CallLog.Calls.OUTGOING_TYPE -> Mapped(
            // An unanswered outgoing call has duration 0 — it still happened and is still logged.
            direction = "outbound",
            answered = durationSec > 0,
            confidence = "high",
            outcome = null,
        )

        CallLog.Calls.MISSED_TYPE -> Mapped(
            direction = "missed",
            answered = false,
            confidence = "high",
            outcome = "no_answer",
        )

        CallLog.Calls.VOICEMAIL_TYPE -> Mapped(
            direction = "inbound",
            answered = false,
            confidence = "medium",
            outcome = "other",
        )

        CallLog.Calls.REJECTED_TYPE -> Mapped(
            direction = "rejected",
            answered = false,
            confidence = "high",
            outcome = null,
        )

        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> Mapped(
            // Answered on another device, so this handset never had the audio.
            direction = "inbound",
            answered = false,
            confidence = "low",
            outcome = null,
        )

        // Appendix A: always handle the else branch.
        else -> Mapped(
            direction = "unknown",
            answered = false,
            confidence = "low",
            outcome = null,
            isUnknownType = true,
        )
    }

    /**
     * §13.4 — `ended_at` is `started_at + duration_sec`. This is an APPROXIMATION: for outgoing
     * calls the platform does not separate the ringing period, so the ring time is folded into
     * neither field. Documented here rather than silently assumed.
     *
     * `ring_sec` is left null. It is generally not obtainable, and inventing it would corrupt
     * the analytics that depend on it.
     */
    fun endedAtMillis(startedAtMillis: Long, durationSec: Int): Long =
        startedAtMillis + (durationSec * 1000L)

    /**
     * §13.4 — a hidden or withheld number arrives as empty, "-1" or "private". Those must end up
     * as a null phone_e164 rather than garbage: a junk value would poison the phone_e164 index
     * and every later lookup.
     */
    fun toE164(raw: String?): String? = PhoneNumbers.toE164(raw)
}
