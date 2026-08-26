package com.proponenttech.callpulse.feature.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONLY thing in the app that reads CallLog (§13.1). One code path means one place to audit
 * for the privacy rules, and one place that can be wrong.
 */
@Singleton
class CallLogReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * §13.2 — reads every row NEWER than the watermark, oldest first.
     *
     * Deliberately not "the most recent row": that model silently drops calls whenever two
     * happen back to back, the device reboots, or the OEM kills the process. Ordering ascending
     * matters too, so the watermark advances monotonically even if the scan is interrupted.
     */
    fun readSince(watermarkMillis: Long, limit: Int = 200): List<CallLogRow> {
        if (!hasPermission()) {
            Timber.w("READ_CALL_LOG not granted - skipping scan")
            return emptyList()
        }

        val projection = buildList {
            add(CallLog.Calls._ID)
            add(CallLog.Calls.DATE)
            add(CallLog.Calls.DURATION)
            add(CallLog.Calls.TYPE)
            add(CallLog.Calls.NUMBER)
            // §12 — the app works fully without contacts; the name is a convenience only.
            if (hasContactsPermission()) add(CallLog.Calls.CACHED_NAME)
            add(CallLog.Calls.PHONE_ACCOUNT_ID)
        }.toTypedArray()

        val rows = mutableListOf<CallLogRow>()

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} > ?",
                arrayOf(watermarkMillis.toString()),
                /*
                 * Ordering only. A "LIMIT n" suffix here is rejected by the CallLog provider on
                 * modern Android with "Invalid token LIMIT" - it validates the sort order rather
                 * than passing it through to SQLite. The cap is applied while reading instead.
                 */
                "${CallLog.Calls.DATE} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val accountIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (cursor.moveToNext() && rows.size < limit) {
                    rows += CallLogRow(
                        id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else "",
                        dateMillis = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                        durationSec = if (durationIdx >= 0) cursor.getInt(durationIdx) else 0,
                        type = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1,
                        number = if (numberIdx >= 0) cursor.getString(numberIdx) else null,
                        cachedName = if (nameIdx >= 0) cursor.getString(nameIdx) else null,
                        phoneAccountId = if (accountIdx >= 0) cursor.getString(accountIdx) else null,
                    )
                }
            }
        } catch (e: SecurityException) {
            // The permission can be revoked between the check and the query.
            Timber.w(e, "CallLog query denied")
            return emptyList()
        } catch (e: Exception) {
            Timber.e(e, "CallLog query failed")
            return emptyList()
        }

        return rows
    }
}
