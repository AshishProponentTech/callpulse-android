package com.proponenttech.callpulse.core.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CrmMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CallPulseDatabase::class.java,
    )

    @Test fun migrationPreservesDraftsAndRebuildsUuidColumns() {
        helper.createDatabase("crm-migration", 2).use { db ->
            for ((id, status) in listOf("pending", "failed", "synced").withIndex()) {
                db.execSQL("""
                    INSERT INTO call_drafts
                    (id,clientUuid,serverId,projectId,taskId,milestoneId,phoneE164,direction,channel,
                    startedAtMillis,tzOffsetMinutes,durationSec,answered,isPersonal,confidence,source,
                    androidCallLogId,capturedAtMillis,syncStatus,syncAttempts)
                    VALUES (?, ?, 123, 9, 10, 11, '+919876543210','outbound','cellular',
                    1000,330,5,1,0,'high','call_log',?,7000,?,2)
                """.trimIndent(), arrayOf<Any>(id + 1, "uuid-$id", "log-$id", status))
            }
        }
        helper.runMigrationsAndValidate("crm-migration", 3, true, CallPulseDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT * FROM call_drafts ORDER BY id").use { cursor ->
                assertEquals(3, cursor.count)
                for ((id, status) in listOf("pending", "failed", "synced").withIndex()) {
                    assertTrue(cursor.moveToNext())
                    assertEquals("uuid-$id", cursor.getString(cursor.getColumnIndexOrThrow("clientUuid")))
                    assertEquals(status, cursor.getString(cursor.getColumnIndexOrThrow("syncStatus")))
                    assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("syncAttempts")))
                    for (column in listOf("serverId", "schoolId", "leadId", "leadName", "leadStatus")) {
                        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow(column)))
                    }
                    assertEquals("unreviewed", cursor.getString(cursor.getColumnIndexOrThrow("reviewState")))
                }
            }
            // UUID server IDs now fit; call-log uniqueness still prevents duplicate capture.
            db.execSQL("UPDATE call_drafts SET serverId='server-uuid' WHERE id=1")
            db.execSQL("INSERT INTO personal_numbers VALUES ('+919876543210', 1234)")
            db.query("SELECT COUNT(*) FROM personal_numbers").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test fun migrationOneThroughThreeStillWorks() {
        helper.createDatabase("crm-chain", 1).close()
        helper.runMigrationsAndValidate("crm-chain", 3, true,
            CallPulseDatabase.MIGRATION_1_2, CallPulseDatabase.MIGRATION_2_3).close()
    }
}
