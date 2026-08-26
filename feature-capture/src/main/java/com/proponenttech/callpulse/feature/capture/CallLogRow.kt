package com.proponenttech.callpulse.feature.capture

/** A raw row read from the system call log, before any interpretation. */
data class CallLogRow(
    val id: String,
    /** CallLog.Calls.DATE — epoch millis when the call started (placed or received). */
    val dateMillis: Long,
    /** CallLog.Calls.DURATION — CONNECTED talk seconds. Zero for missed/rejected/unanswered. */
    val durationSec: Int,
    /** CallLog.Calls.TYPE — see Appendix A. Read from the framework, never hardcoded. */
    val type: Int,
    val number: String?,
    val cachedName: String?,
    val phoneAccountId: String?,
)
