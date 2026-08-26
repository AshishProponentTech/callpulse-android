package com.proponenttech.callpulse.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A captured call, held locally until the server accepts it.
 *
 * Room is a draft + offline queue cache only (§10) — never a second analytics warehouse. The
 * server is the source of truth; these rows exist so capture survives being offline.
 */
@Entity(
    tableName = "call_drafts",
    indices = [
        Index(value = ["clientUuid"], unique = true),
        /*
         * UNIQUE, not merely indexed. Check-then-insert loses to a race: the post-call scan and
         * the periodic reconcile can both read the same CallLog row before either has written
         * it, and both then insert. Observed in testing — three calls became six drafts.
         * SQLite permits many NULLs in a unique index, so manual entries are unaffected.
         */
        Index(value = ["androidCallLogId"], unique = true),
        Index(value = ["startedAtMillis"]),
        Index(value = ["syncStatus"]),
    ],
)
data class CallSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /**
     * §13.3 — generated once at draft creation and NEVER regenerated. It is the server's
     * idempotency key; regenerating on retry is the single most common cause of duplicate rows
     * in systems like this.
     */
    val clientUuid: String,

    /** Set once the server accepts the row, so later updates address the same record. */
    val serverId: Long? = null,

    val projectId: Long?,
    val taskId: Long? = null,
    val milestoneId: Long? = null,

    val phoneE164: String?,
    val phoneDisplay: String?,
    val contactName: String?,

    val direction: String,
    val channel: String,
    val subChannel: String? = null,

    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val tzOffsetMinutes: Int,

    val durationSec: Int,
    val ringSec: Int? = null,
    val answered: Boolean,
    val isPersonal: Boolean = false,

    val confidence: String,
    val source: String,

    val deviceUuid: String?,
    /** CallLog._ID — the primary local dedupe key (§13.3). */
    val androidCallLogId: String?,
    val phoneAccountId: String?,

    val outcome: String? = null,
    val notes: String? = null,
    val appVersion: String? = null,

    val capturedAtMillis: Long,
    val reviewedAtMillis: Long? = null,

    val syncStatus: String = SyncStatus.PENDING,
    /** §15 — a 422 is surfaced to the user rather than retried forever. */
    val syncError: String? = null,
    val syncAttempts: Int = 0,
)

/** §15 — per-row sync state. */
object SyncStatus {
    const val PENDING = "pending"
    const val SYNCING = "syncing"
    const val SYNCED = "synced"
    const val FAILED = "failed"
}
