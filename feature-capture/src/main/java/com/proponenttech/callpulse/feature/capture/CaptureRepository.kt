package com.proponenttech.callpulse.feature.capture

import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import com.proponenttech.callpulse.core.data.db.SyncStatus
import com.proponenttech.callpulse.core.data.session.AppPreferences
import com.proponenttech.callpulse.core.data.session.SecureStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns call log rows into local drafts, applying the §13.3 dedupe rules.
 *
 * Every path into the database goes through here, so the dedupe cannot be bypassed by a new
 * caller added later.
 */
@Singleton
class CaptureRepository @Inject constructor(
    private val reader: CallLogReader,
    private val dao: CallSessionDao,
    private val preferences: AppPreferences,
    private val secureStore: SecureStore,
) {

    private val scanMutex = Mutex()

    data class ScanResult(
        val created: Int,
        val skipped: Int,
        val newestStartedAtMillis: Long?,
    )

    /**
     * §13.2 — one scan. Reads everything newer than the watermark, dedupes, persists, and
     * advances the watermark.
     *
     * On first run the watermark is seeded to now minus 24h so the employee immediately sees
     * something. Deliberately not months of history: the server rejects anything older than 90
     * days anyway (§6.2), and importing a year of personal calls would be a privacy problem in
     * its own right.
     */
    suspend fun scan(nowMillis: Long = System.currentTimeMillis()): ScanResult =
        // Four mechanisms can trigger a scan and several may fire at once — a call ending while
        // the app is foregrounded triggers both. Serialising them keeps the unique index from
        // having to be the last line of defence, and stops two scans doing the same work twice.
        scanMutex.withLock { scanLocked(nowMillis) }

    private suspend fun scanLocked(nowMillis: Long): ScanResult {
        val stored = preferences.callLogWatermark.first()
        val watermark = if (stored == 0L) {
            val backfillFrom = nowMillis - BACKFILL_WINDOW_MILLIS
            Timber.i("First run - backfilling the last 24h")
            backfillFrom
        } else {
            stored
        }

        val rows = reader.readSince(watermark)
        if (rows.isEmpty()) {
            preferences.setCallLogWatermark(watermark)
            preferences.setLastScanAt(nowMillis)
            return ScanResult(created = 0, skipped = 0, newestStartedAtMillis = null)
        }

        val defaultProjectId = preferences.defaultProjectId.first()
        var created = 0
        var skipped = 0
        var newest: Long? = null

        for (row in rows) {
            newest = maxOf(newest ?: 0L, row.dateMillis)

            if (CallLogMapper.shouldSkip(row.type)) {
                skipped++
                continue
            }

            if (isDuplicate(row)) {
                skipped++
                continue
            }

            val entity = toEntity(row, defaultProjectId, nowMillis)
            val id = dao.insert(entity)

            if (id > 0) created++ else skipped++
        }

        newest?.let { preferences.setCallLogWatermark(it) }
        preferences.setLastScanAt(nowMillis)

        Timber.i("Scan complete: %d created, %d skipped", created, skipped)
        return ScanResult(created, skipped, newest)
    }

    /**
     * §13.3, in priority order. The CallLog id is authoritative; the fuzzy window exists only to
     * guard the race where the PhoneState receiver and the periodic scan both find the same call
     * before either has written it.
     */
    private suspend fun isDuplicate(row: CallLogRow): Boolean {
        if (row.id.isNotEmpty() && dao.findByCallLogId(row.id) != null) return true

        val e164 = CallLogMapper.toE164(row.number) ?: return false
        val channel = "cellular"

        return dao.findNear(
            phoneE164 = e164,
            channel = channel,
            fromMillis = row.dateMillis - DEDUPE_WINDOW_MILLIS,
            toMillis = row.dateMillis + DEDUPE_WINDOW_MILLIS,
        ) != null
    }

    private fun toEntity(
        row: CallLogRow,
        defaultProjectId: Long?,
        nowMillis: Long,
    ): CallSessionEntity {
        val mapped = CallLogMapper.mapType(row.type, row.durationSec)

        if (mapped.isUnknownType) {
            // §13.4 wants a capture_gap telemetry event for this; the row is still kept.
            Timber.w("Unrecognised CallLog TYPE %d - logging as unknown", row.type)
        }

        val e164 = CallLogMapper.toE164(row.number)

        return CallSessionEntity(
            // Generated ONCE here and never again (§13.3).
            clientUuid = UUID.randomUUID().toString(),
            projectId = defaultProjectId,
            phoneE164 = e164,
            // Keep whatever the dialer showed even when it cannot be normalised.
            phoneDisplay = row.number,
            contactName = row.cachedName,
            direction = mapped.direction,
            channel = "cellular",
            startedAtMillis = row.dateMillis,
            endedAtMillis = CallLogMapper.endedAtMillis(row.dateMillis, row.durationSec),
            tzOffsetMinutes = TimeZone.getDefault().getOffset(row.dateMillis) / 60000,
            durationSec = row.durationSec,
            // §13.4 — not obtainable from the call log; never fabricated.
            ringSec = null,
            answered = mapped.answered,
            confidence = mapped.confidence,
            source = "call_log",
            deviceUuid = secureStore.deviceUuid,
            androidCallLogId = row.id.takeIf { it.isNotEmpty() },
            phoneAccountId = row.phoneAccountId,
            outcome = mapped.outcome,
            capturedAtMillis = nowMillis,
            syncStatus = SyncStatus.PENDING,
        )
    }

    private companion object {
        const val BACKFILL_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
        const val DEDUPE_WINDOW_MILLIS = 60 * 1000L
    }
}
